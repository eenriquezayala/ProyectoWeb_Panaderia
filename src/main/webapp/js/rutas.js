import { iniciarModulo } from './app-shell.js';

// La autorización definitiva se realiza en Jersey (FiltroJwt).
const app = iniciarModulo('rutas', ['ADMINISTRADOR']);
if (!app) throw new Error('Gestión de rutas requiere un administrador autenticado.');

const $ = selector => document.querySelector(selector);
const dialogRegistro = $('#dialog-registro');
const dialogVendedor = $('#dialog-vendedor');
const dialogVehiculo = $('#dialog-vehiculo');
let rutas = [];
let despachos = [];
let vehiculosDisponibles = [];
let rutaSeleccionada = null;
let cargando = false;
let guardando = false;

function enteroPositivo(valor) {
  const n = Number(valor);
  return Number.isSafeInteger(n) && n > 0 ? n : null;
}
function idRuta(r) { return enteroPositivo(r?.idDespachoOracle); }
function versionRuta(r) { return enteroPositivo(r?.version); }
function estadoRuta(r) { return String(r?.estado || '').trim().toUpperCase(); }
function crearCelda(tr, valor, clase = '') {
  const td = document.createElement('td');
  td.textContent = valor === undefined || valor === null || valor === '' ? '—' : String(valor);
  if (clase) td.className = clase;
  tr.append(td);
  return td;
}
function crearBoton(etiqueta, accion) {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'table-action';
  boton.textContent = etiqueta;
  boton.disabled = guardando;
  boton.addEventListener('click', accion);
  return boton;
}
function mostrarError(selector, mensaje) {
  const elemento = $(selector);
  elemento.textContent = String(mensaje || 'Ocurrió un problema.');
  elemento.hidden = false;
}
function esconderError(selector) {
  const elemento = $(selector);
  elemento.textContent = '';
  elemento.hidden = true;
}
function chip(estado) {
  const estilos = {
    PENDIENTE_ASIGNACION: 'waiting',
    PENDIENTE_VEHICULO: 'waiting',
    LISTA_PARA_ENTREGA: 'ready',
    ENTREGA_REPORTADA: 'done'
  };
  const span = document.createElement('span');
  span.className = `status-chip status-chip--${estilos[estado] || 'off'}`;
  span.textContent = estado ? estado.replaceAll('_', ' ') : 'SIN ESTADO';
  return span;
}
function pintar() {
  const tabla = $('#tabla-rutas');
  const filtro = $('#filtro-estado').value;
  const texto = $('#buscar').value.trim().toLowerCase();
  const ordenadas = [...rutas].sort((a, b) => (idRuta(b) || 0) - (idRuta(a) || 0));
  const visibles = ordenadas.filter(r => {
    if (filtro && estadoRuta(r) !== filtro) return false;
    const campos = [r.idRuta, r.idDespachoOracle, r.idVendedor, r.idVehiculo, r.sucursal, r.estado];
    return campos.some(valor => String(valor ?? '').toLowerCase().includes(texto));
  });
  tabla.replaceChildren();
  for (const ruta of visibles) {
    const tr = document.createElement('tr');
    const tdRuta = document.createElement('td');
    const titulo = document.createElement('strong');
    titulo.className = 'rutas-id';
    titulo.textContent = ruta.idRuta || `despacho_${idRuta(ruta) ?? '—'}`;
    const subtitulo = document.createElement('small');
    subtitulo.className = 'rutas-subline';
    subtitulo.textContent = `Despacho Oracle #${idRuta(ruta) ?? '—'}`;
    tdRuta.append(titulo, subtitulo);
    tr.append(tdRuta);
    crearCelda(tr, ruta.sucursal);
    crearCelda(tr, ruta.idVendedor == null ? 'Sin asignar' : `ID ${ruta.idVendedor}`);
    crearCelda(tr, ruta.idVehiculo || 'Sin asignar');
    const estadoTd = document.createElement('td');
    estadoTd.append(chip(estadoRuta(ruta)));
    tr.append(estadoTd);
    crearCelda(tr, ruta.version, 'num');
    const acciones = document.createElement('td');
    acciones.className = 'table-actions rutas-acciones';
    if (estadoRuta(ruta) === 'PENDIENTE_ASIGNACION') {
      acciones.append(crearBoton('Asignar vendedor', () => abrirVendedor(ruta)));
    } else if (estadoRuta(ruta) === 'PENDIENTE_VEHICULO') {
      acciones.append(crearBoton('Asignar vehículo', () => abrirVehiculo(ruta)));
    } else if (estadoRuta(ruta) === 'LISTA_PARA_ENTREGA') {
      crearCeldaFija(acciones, 'En proceso de entrega');
    } else if (estadoRuta(ruta) === 'ENTREGA_REPORTADA') {
      crearCeldaFija(acciones, 'Entrega informada');
    } else {
      crearCeldaFija(acciones, 'Sin acción disponible');
    }
    tr.append(acciones);
    tabla.append(tr);
  }
  if (!visibles.length) {
    const tr = document.createElement('tr');
    const td = crearCelda(tr, 'No hay rutas que coincidan con los filtros.', 'empty-table');
    td.colSpan = 7;
    tabla.append(tr);
  }
  $('#stat-total').textContent = String(rutas.length);
  $('#stat-pendientes').textContent = String(rutas.filter(r => estadoRuta(r) === 'PENDIENTE_ASIGNACION').length);
  $('#stat-vehiculos').textContent = String(rutas.filter(r => estadoRuta(r) === 'PENDIENTE_VEHICULO').length);
  $('#stat-entregadas').textContent = String(rutas.filter(r => estadoRuta(r) === 'ENTREGA_REPORTADA').length);
  $('#conteo-filtrado').textContent = `${visibles.length} de ${rutas.length} rutas`;
}
function crearCeldaFija(contenedor, texto) {
  const span = document.createElement('span');
  span.className = 'rutas-tag';
  span.textContent = texto;
  contenedor.append(span);
}
async function cargar() {
  if (cargando) return;
  cargando = true;
  $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando rutas en Firestore…';
  try {
    // La consulta de rutas puede no incluir sucursal ni número de pedido.
    // Completamos solo los datos visuales usando despachos de Oracle.
    const [datos, datosDespachos] = await Promise.all([
      app.api('rutas'),
      app.api('despachos').catch(() => null)
    ]);
    if (!Array.isArray(datos)) throw new Error('La API no devolvió una lista de rutas válida.');
    if (Array.isArray(datosDespachos)) despachos = datosDespachos;
    const indiceDespachos = new Map(despachos.map(d => [enteroPositivo(d.idDespacho), d]));
    rutas = datos.map(r => {
      const despacho = indiceDespachos.get(idRuta(r));
      return { ...r, sucursal: r.sucursal || despacho?.sucursal || null };
    });
    pintar();
    $('#estado-carga').textContent = 'Rutas actualizadas.';
  } catch (error) {
    $('#estado-carga').textContent = error.message || 'No fue posible consultar las rutas.';
    app.notificar('No se pudieron actualizar las rutas. Verifica el servidor.', 'error');
  } finally {
    cargando = false;
    $('#btn-recargar').disabled = false;
  }
}
async function abrirRegistro() {
  if (guardando) return;
  $('#form-registro').reset();
  esconderError('#error-registro');
  const select = $('#registro-despacho');
  select.replaceChildren(new Option('Consultando despachos…', ''));
  $('#guardar-registro').disabled = true;
  dialogRegistro.showModal();
  try {
    // Refrescar ambos listados para no ofrecer despachos que ya tengan ruta.
    const [datosDespachos, datosRutas] = await Promise.all([app.api('despachos'), app.api('rutas')]);
    if (!dialogRegistro.open) return;
    if (!Array.isArray(datosDespachos) || !Array.isArray(datosRutas)) {
      throw new Error('No se pudo obtener el catálogo de despachos y rutas.');
    }
    despachos = datosDespachos;
    rutas = datosRutas;
    pintar();
    const conRuta = new Set(rutas.map(r => idRuta(r)).filter(Boolean));
    const disponibles = despachos.filter(d => String(d.estado || '').trim().toUpperCase() === 'DESPACHADO'
      && enteroPositivo(d.idDespacho) && !conRuta.has(Number(d.idDespacho)));
    select.replaceChildren(new Option('Selecciona un despacho…', ''));
    for (const d of disponibles) {
      select.add(new Option(`Despacho #${d.idDespacho} · Pedido #${d.idPedido ?? '—'} · ${d.sucursal || 'Sucursal'}`, String(d.idDespacho)));
    }
    $('#guardar-registro').disabled = !disponibles.length;
    $('#ayuda-registro').textContent = disponibles.length
      ? `${disponibles.length} despacho(s) sin ruta. Se verificará nuevamente en el servidor antes de guardar.`
      : 'No hay despachos DESPACHADOS sin ruta. Primero registra un despacho desde el módulo Despachos.';
  } catch (error) {
    if (dialogRegistro.open) {
      select.replaceChildren(new Option('No se pudieron cargar los despachos', ''));
      mostrarError('#error-registro', error.message);
    }
  }
}
async function registrarRuta(evento) {
  evento.preventDefault();
  if (guardando) return;
  esconderError('#error-registro');
  const idDespachoOracle = enteroPositivo($('#registro-despacho').value);
  if (!idDespachoOracle) { mostrarError('#error-registro', 'Selecciona un despacho válido.'); return; }
  guardando = true;
  const boton = $('#guardar-registro');
  boton.disabled = true;
  boton.textContent = 'Registrando…';
  try {
    const resultado = await app.api('rutas', { metodo: 'POST', datos: { idDespachoOracle } });
    if (resultado?.exito !== true) throw new Error(resultado?.mensaje || 'El servidor no confirmó la creación de la ruta.');
    dialogRegistro.close();
    app.notificar(resultado.mensaje || `Ruta del despacho #${idDespachoOracle} registrada.`, 'exito');
  } catch (error) {
    mostrarError('#error-registro', `${error.message || 'No se pudo confirmar el registro.'} Revisa el listado actualizado antes de volver a intentar.`);
  } finally {
    guardando = false;
    boton.disabled = false;
    boton.textContent = 'Registrar ruta';
    await cargar();
  }
}
function rolUsuario(u) {
  const rol = u?.rol;
  if (typeof rol === 'string') return rol.trim().toUpperCase();
  if (rol && typeof rol.nombre === 'string') return rol.nombre.trim().toUpperCase();
  return String(u?.nombreRol || u?.rolNombre || '').trim().toUpperCase();
}
function idUsuario(u) { return enteroPositivo(u?.idUsuario ?? u?.id); }
async function abrirVendedor(ruta) {
  if (guardando) return;
  rutaSeleccionada = ruta;
  $('#form-vendedor').reset();
  esconderError('#error-vendedor');
  $('#vendedor-contexto').textContent = `Ruta ${ruta.idRuta} · sucursal ${ruta.sucursal || '—'} · versión ${ruta.version}. Solo se puede asignar una vez.`;
  const select = $('#vendedor-select');
  const boton = $('#guardar-vendedor');
  select.replaceChildren(new Option('Consultando vendedores…', ''));
  select.disabled = true;
  boton.disabled = true;
  dialogVendedor.showModal();
  try {
    const usuarios = await app.api('usuarios');
    if (!dialogVendedor.open) return;
    if (!Array.isArray(usuarios)) throw new Error('El catálogo de usuarios tiene un formato inesperado.');
    const vendedores = usuarios.filter(u => rolUsuario(u) === 'VENDEDOR_RUTA' && idUsuario(u));
    select.replaceChildren(new Option('Selecciona un vendedor…', ''));
    for (const u of vendedores) {
      const id = idUsuario(u);
      const etiqueta = u.nombre || u.usuario || 'Vendedor';
      select.add(new Option(`${etiqueta} · ID ${id} · ${u.sucursal || ''}`, String(id)));
    }
    if (!vendedores.length) {
      mostrarError('#error-vendedor', 'No hay vendedores registrados en el catálogo. Registra uno desde Usuarios.');
    }
    select.disabled = !vendedores.length;
  } catch (error) {
    if (dialogVendedor.open) {
      select.replaceChildren(new Option('No se pudieron cargar vendedores', ''));
      mostrarError('#error-vendedor', error.message || 'No fue posible consultar los vendedores.');
    }
  } finally {
    if (dialogVendedor.open) boton.disabled = !enteroPositivo(select.value) || select.disabled;
  }
}

async function asignarVendedor(evento) {
  evento.preventDefault();
  if (guardando || !rutaSeleccionada) return;
  esconderError('#error-vendedor');
  const ruta = rutaSeleccionada;
  const idVendedor = enteroPositivo($('#vendedor-select').value);
  if (!idVendedor) { mostrarError('#error-vendedor', 'Selecciona un vendedor válido del catálogo.'); return; }
  const id = idRuta(ruta);
  const versionEsperada = versionRuta(ruta);
  if (!id || !versionEsperada || estadoRuta(ruta) !== 'PENDIENTE_ASIGNACION') {
    mostrarError('#error-vendedor', 'La ruta no permite esta asignación. Actualiza la tabla.'); return;
  }
  guardando = true;
  const boton = $('#guardar-vendedor');
  boton.disabled = true;
  boton.textContent = 'Asignando…';
  try {
    const resultado = await app.api(`rutas/${id}/asignar-vendedor`, {
      metodo: 'PUT', datos: { idVendedor, versionEsperada }
    });
    if (resultado?.exito !== true) throw new Error(resultado?.mensaje || 'El servidor no confirmó la asignación.');
    dialogVendedor.close();
    rutaSeleccionada = null;
    app.notificar(resultado.mensaje || `Vendedor ${idVendedor} asignado. Se intentó enviar el aviso FCM.`, 'exito');
  } catch (error) {
    mostrarError('#error-vendedor', `${error.message || 'No se pudo confirmar la asignación.'} Consulta el estado de la ruta antes de intentar otra vez; el servidor podría haber completado la operación.`);
  } finally {
    guardando = false;
    boton.disabled = false;
    boton.textContent = 'Asignar y notificar';
    await cargar();
  }
}
// La versión se toma exclusivamente del catálogo recibido desde el backend.
function sincronizarVehiculoSeleccionado() {
  const id = $('#vehiculo-id').value;
  const vehiculo = vehiculosDisponibles.find(v => v.idVehiculo === id);
  const version = enteroPositivo(vehiculo?.version);
  $('#vehiculo-version').value = version ? String(version) : '';
  $('#guardar-vehiculo').disabled = guardando || !id || !version;
}

async function abrirVehiculo(ruta) {
  if (guardando) return;
  rutaSeleccionada = ruta;
  vehiculosDisponibles = [];
  $('#form-vehiculo').reset();
  esconderError('#error-vehiculo');
  $('#vehiculo-contexto').textContent = `Ruta ${ruta.idRuta} · vendedor ID ${ruta.idVendedor ?? '—'} · versión ${ruta.version}`;
  const select = $('#vehiculo-id');
  select.replaceChildren(new Option('Consultando vehículos…', ''));
  select.disabled = true;
  $('#guardar-vehiculo').disabled = true;
  dialogVehiculo.showModal();
  try {
    // El servidor lee Firestore mediante Firebase Admin SDK; no exponemos credenciales al navegador.
    const datos = await app.api('rutas/vehiculos-disponibles');
    if (!dialogVehiculo.open) return;
    if (!Array.isArray(datos)) throw new Error('La API devolvió un catálogo de vehículos inválido.');
    vehiculosDisponibles = datos.filter(v =>
      typeof v?.idVehiculo === 'string' && v.idVehiculo.length > 0 &&
      enteroPositivo(v.version)
    );
    select.replaceChildren(new Option('Selecciona un vehículo…', ''));
    for (const v of vehiculosDisponibles) {
      const etiqueta = `${v.nombre || v.idVehiculo} · ${v.placa || 'Sin placa'} · ${v.idVehiculo}`;
      select.add(new Option(etiqueta, v.idVehiculo));
    }
    if (!vehiculosDisponibles.length) {
      mostrarError('#error-vehiculo', 'No hay vehículos activos y disponibles para asignar.');
    }
    select.disabled = !vehiculosDisponibles.length;
    sincronizarVehiculoSeleccionado();
  } catch (error) {
    if (dialogVehiculo.open) {
      select.replaceChildren(new Option('No se pudieron cargar vehículos', ''));
      mostrarError('#error-vehiculo', error.message || 'No fue posible consultar los vehículos.');
    }
  }
}

async function asignarVehiculo(evento) {
  evento.preventDefault();
  if (guardando || !rutaSeleccionada) return;
  esconderError('#error-vehiculo');
  const ruta = rutaSeleccionada;
  const idVehiculo = $('#vehiculo-id').value.trim();
  if (!idVehiculo || !vehiculosDisponibles.some(v => v.idVehiculo === idVehiculo) || idVehiculo.length > 120 || idVehiculo.includes('/')) {
    mostrarError('#error-vehiculo', 'Selecciona un vehículo válido del catálogo.'); return;
  }
  const id = idRuta(ruta);
  const versionRutaEsperada = versionRuta(ruta);
  const versionVehiculoEsperada = enteroPositivo($('#vehiculo-version').value);
  if (!id || !versionRutaEsperada || estadoRuta(ruta) !== 'PENDIENTE_VEHICULO') {
    mostrarError('#error-vehiculo', 'La ruta no permite asignar un vehículo. Actualiza la tabla.'); return;
  }
  if (!versionVehiculoEsperada) {
    mostrarError('#error-vehiculo', 'No se pudo determinar la versión actual del vehículo. Vuelve a abrir la asignación.');
    return;
  }
  guardando = true;
  const boton = $('#guardar-vehiculo');
  boton.disabled = true;
  boton.textContent = 'Asignando…';
  try {
    // El backend exige las dos versiones para prevenir asignaciones sobre datos desactualizados.
    const resultado = await app.api(`rutas/${id}/asignar-vehiculo`, {
      metodo: 'PUT',
      datos: { idVehiculo, versionRutaEsperada, versionVehiculoEsperada }
    });
    if (resultado?.exito !== true) throw new Error(resultado?.mensaje || 'El servidor no confirmó el vehículo.');
    dialogVehiculo.close();
    rutaSeleccionada = null;
    app.notificar(resultado.mensaje || `Vehículo ${idVehiculo} asignado a despacho #${id}.`, 'exito');
  } catch (error) {
    mostrarError('#error-vehiculo', `${error.message || 'No se pudo confirmar la asignación.'} Cierra y vuelve a abrir la ventana para consultar versiones actualizadas antes de reintentar.`);
  } finally {
    guardando = false;
    boton.disabled = false;
    boton.textContent = 'Confirmar vehículo';
    await cargar();
  }
}

$('#btn-recargar').addEventListener('click', cargar);
$('#buscar').addEventListener('input', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-nuevo').addEventListener('click', abrirRegistro);
$('#form-registro').addEventListener('submit', registrarRuta);
$('#form-vendedor').addEventListener('submit', asignarVendedor);
$('#vendedor-select').addEventListener('change', () => {
  $('#guardar-vendedor').disabled = guardando || !enteroPositivo($('#vendedor-select').value);
});
$('#form-vehiculo').addEventListener('submit', asignarVehiculo);
$('#vehiculo-id').addEventListener('change', sincronizarVehiculoSeleccionado);
for (const [dialogo, nombre] of [
  [dialogRegistro, 'registro'], [dialogVendedor, 'vendedor'], [dialogVehiculo, 'vehiculo']
]) {
  $(`#cerrar-${nombre}`).addEventListener('click', () => { if (!guardando) dialogo.close(); });
  $(`#cancelar-${nombre}`).addEventListener('click', () => { if (!guardando) dialogo.close(); });
  dialogo.addEventListener('cancel', evento => { if (guardando) evento.preventDefault(); });
}
void cargar();
