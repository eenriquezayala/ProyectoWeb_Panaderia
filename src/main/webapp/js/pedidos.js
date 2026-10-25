import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('pedidos', ['ADMINISTRADOR', 'PRODUCCION', 'SUCURSAL']);
if (!app) throw new Error('No hay una sesión autorizada para Pedidos.');

const $ = selector => document.querySelector(selector);
const puedeEditar = app.rol !== 'PRODUCCION';
const esAdmin = app.rol === 'ADMINISTRADOR';
const dialogNuevo = $('#dialog-nuevo');
const dialogDetalle = $('#dialog-detalle');
const dialogAgregar = $('#dialog-agregar');
const dialogEnviar = $('#dialog-enviar');
const tbody = $('#tabla-pedidos');
const numero = valor => new Intl.NumberFormat('es-GT').format(Number(valor) || 0);
let pedidos = [];
let productosFinales = null;
let sucursales = null;
let pedidoActual = null;
let paraEnviar = null;
let cargando = false;
let guardando = false;

function fechaISOlocal(fecha) {
  const dia = new Date(fecha);
  const yyyy = dia.getFullYear();
  const mm = String(dia.getMonth() + 1).padStart(2, '0');
  const dd = String(dia.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}
function fechaVisible(valor) {
  if (!valor) return '—';
  const texto = String(valor);
  const coincidencia = texto.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (coincidencia) return `${coincidencia[3]}/${coincidencia[2]}/${coincidencia[1]}`;
  return texto;
}
function celda(tr, valor, clase = '') {
  const td = document.createElement('td');
  td.textContent = valor == null ? '—' : String(valor);
  if (clase) td.className = clase;
  tr.append(td);
  return td;
}
function botonAccion(texto, click, clase = '') {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = `table-action${clase ? ' ' + clase : ''}`;
  b.textContent = texto;
  b.addEventListener('click', click);
  return b;
}
function chip(estado) {
  const estilos = {
    BORRADOR: 'draft', ENVIADO: 'sent', EN_PROCESO: 'working',
    LISTO: 'ready', DESPACHADO: 'done', RECIBIDO: 'received'
  };
  const el = document.createElement('span');
  el.className = `status-chip status-chip--${estilos[estado] || 'off'}`;
  el.textContent = (estado || 'DESCONOCIDO').replaceAll('_', ' ');
  return el;
}
function ponerError(id, mensaje) {
  const el = $(id);
  el.textContent = mensaje;
  el.hidden = false;
}
function ocultarError(id) { $(id).hidden = true; }
function esBorrador(p) { return String(p?.estado || '').toUpperCase() === 'BORRADOR'; }
function soloLectura() {
  $('#btn-nuevo').hidden = !puedeEditar;
  $('#aviso-lectura').hidden = puedeEditar;
  $('#alcance-datos').textContent = app.rol === 'SUCURSAL' ? 'SOLO TU SUCURSAL'
    : esAdmin ? 'TODAS LAS SUCURSALES' : 'CONSULTA GENERAL';
  $('#etiqueta-total').textContent = app.rol === 'SUCURSAL' ? 'Pedidos de tu sucursal' : 'Pedidos registrados';
  if (app.rol === 'PRODUCCION') $('#descripcion-listado').textContent = 'Consulta solicitudes y cantidades programadas de las sucursales. Las ediciones no están disponibles para tu rol.';
}
function pintar() {
  const busqueda = $('#buscar').value.trim().toLocaleLowerCase('es');
  const estado = $('#filtro-estado').value;
  const lista = pedidos.filter(p => {
    const coincide = `${p.idPedido} ${p.sucursal || ''} ${p.observaciones || ''} ${p.fechaRequerida || ''}`.toLocaleLowerCase('es').includes(busqueda);
    return coincide && (!estado || String(p.estado).toUpperCase() === estado);
  });
  tbody.replaceChildren();
  for (const p of lista) {
    const tr = document.createElement('tr');
    celda(tr, `#${p.idPedido}`, 'pedido-id');
    celda(tr, p.sucursal || '—');
    const tdFecha = celda(tr, fechaVisible(p.fechaRequerida));
    const small = document.createElement('small');
    small.textContent = `Creado: ${fechaVisible(p.fechaPedido)}`;
    tdFecha.append(small);
    const tdEstado = document.createElement('td');
    tdEstado.append(chip(String(p.estado || '').toUpperCase()));
    tr.append(tdEstado);
    const observaciones = String(p.observaciones || '').trim();
    const tdObs = celda(tr, observaciones || 'Sin observaciones');
    tdObs.title = observaciones;
    const acciones = document.createElement('td');
    acciones.className = 'table-actions pedido-acciones';
    acciones.append(botonAccion('Ver detalle', () => abrirDetalle(p)));
    if (puedeEditar && esBorrador(p)) {
      acciones.append(botonAccion('＋ Pan', () => abrirAgregar(p)));
      acciones.append(botonAccion('Enviar', () => abrirConfirmacion(p)));
    }
    tr.append(acciones);
    tbody.append(tr);
  }
  if (!lista.length) {
    const tr = document.createElement('tr');
    const td = celda(tr, 'No hay pedidos que coincidan con los filtros.', 'empty-table');
    td.colSpan = 6;
    tbody.append(tr);
  }
  $('#stat-total').textContent = numero(pedidos.length);
  $('#stat-borrador').textContent = numero(pedidos.filter(p => p.estado === 'BORRADOR').length);
  $('#stat-enviado').textContent = numero(pedidos.filter(p => p.estado === 'ENVIADO').length);
  $('#stat-listos').textContent = numero(pedidos.filter(p => ['LISTO','DESPACHADO','RECIBIDO'].includes(p.estado)).length);
  $('#conteo-filtrado').textContent = `${lista.length} de ${pedidos.length} pedidos`;
}
async function cargar() {
  if (cargando) return;
  cargando = true;
  $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando pedidos...';
  try {
    const respuesta = await app.api('pedidos');
    if (!Array.isArray(respuesta)) throw new Error('La API no devolvió una lista de pedidos.');
    pedidos = respuesta;
    pintar();
    $('#estado-carga').textContent = 'Pedidos actualizados.';
  } catch (e) {
    $('#estado-carga').textContent = e.message || 'No se pudieron consultar los pedidos.';
    app.notificar(e.message || 'Error al cargar pedidos.', 'error');
  } finally {
    cargando = false;
    $('#btn-recargar').disabled = false;
  }
}
async function obtenerSucursales() {
  if (sucursales) return sucursales;
  const data = await app.api('usuarios/sucursales');
  if (!Array.isArray(data)) throw new Error('No fue posible cargar las sucursales.');
  sucursales = data;
  return data;
}
async function obtenerPanes() {
  if (productosFinales) return productosFinales;
  const data = await app.api('productos?tipo=FINAL');
  if (!Array.isArray(data)) throw new Error('No fue posible cargar los panes finales.');
  productosFinales = data.filter(p => p.tipo === 'FINAL' && Number(p.estado) === 1);
  return productosFinales;
}
async function abrirNuevo() {
  if (!puedeEditar) return;
  $('#form-nuevo').reset();
  ocultarError('#error-nuevo');
  const minFecha = fechaISOlocal(new Date());
  const manana = new Date(); manana.setDate(manana.getDate() + 1);
  $('#nuevo-fecha').min = minFecha;
  $('#nuevo-fecha').value = fechaISOlocal(manana);
  $('#campo-sucursal').hidden = !esAdmin;
  $('#nota-sucursal').textContent = esAdmin
    ? 'Como administrador, selecciona la sucursal que realizará el pedido. El responsable será tu usuario'
    : `El pedido se asociará automáticamente a tu sucursal (${app.sesion.usuario.sucursal || 'asignada en Oracle'}).`;
  dialogNuevo.showModal();
  if (!esAdmin) return;
  const select = $('#nuevo-sucursal');
  select.replaceChildren(new Option('Cargando sucursales…', ''));
  try {
    const lista = await obtenerSucursales();
    if (!dialogNuevo.open) return;
    select.replaceChildren(new Option('Seleccionar sucursal…', ''));
    for (const s of lista) select.add(new Option(s.nombre, String(s.idSucursal)));
  } catch (e) { ponerError('#error-nuevo', e.message || 'Error al consultar sucursales.'); }
}
async function guardarNuevo(ev) {
  ev.preventDefault();
  if (!puedeEditar || guardando) return;
  ocultarError('#error-nuevo');
  const fecha = $('#nuevo-fecha').value;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha) || fecha < fechaISOlocal(new Date())) {
    ponerError('#error-nuevo', 'Selecciona una fecha requerida válida, de hoy en adelante.'); return;
  }
  const datos = {fechaRequerida: fecha, observaciones: $('#nuevo-observaciones').value.trim()};
  if (esAdmin) {
    const idSucursal = Number($('#nuevo-sucursal').value);
    if (!Number.isSafeInteger(idSucursal) || idSucursal <= 0) { ponerError('#error-nuevo', 'Selecciona una sucursal.'); return; }
    datos.idSucursal = idSucursal;
  }
  guardando = true;
  const btn = $('#guardar-nuevo'); btn.disabled = true; btn.textContent = 'Creando…';
  try {
    const respuesta = await app.api('pedidos', {metodo:'POST', datos});
    if (!respuesta?.exito || !(Number(respuesta.id) > 0)) throw new Error(respuesta?.mensaje || 'El servidor no devolvió un pedido válido.');
    dialogNuevo.close();
    app.notificar(respuesta.mensaje || `Pedido #${respuesta.id} creado correctamente.`, 'exito');
    await cargar();
    const p = pedidos.find(item => Number(item.idPedido) === Number(respuesta.id));
    if (p) await abrirAgregar(p);
  } catch (e) { ponerError('#error-nuevo', e.message || 'No se pudo crear el pedido.'); if (!dialogNuevo.open) app.notificar(e.message || 'Error al actualizar pedidos.', 'error'); }
  finally { guardando = false; btn.disabled = false; btn.textContent = 'Crear pedido'; }
}
function pintarDetalleFilas(detalles) {
  const tbodyDetalle = $('#tabla-detalle');
  tbodyDetalle.replaceChildren();
  let total = 0;
  for (const d of detalles) {
    const tr = document.createElement('tr');
    celda(tr, d.producto || '—', 'strong-text');
    celda(tr, numero(d.cantidadManana), 'num');
    celda(tr, numero(d.cantidadResto), 'num');
    celda(tr, numero(d.cantidadTarde), 'num');
    const sumado = Number(d.total ?? (Number(d.cantidadManana || 0) + Number(d.cantidadResto || 0) + Number(d.cantidadTarde || 0)));
    total += sumado;
    celda(tr, numero(sumado), 'num');
    tbodyDetalle.append(tr);
  }
  if (!detalles.length) {
    const tr = document.createElement('tr');
    const td = celda(tr, 'Todavía no se han agregado panes a este pedido.', 'empty-table');
    td.colSpan = 5; tbodyDetalle.append(tr);
  }
  $('#detalle-total').textContent = numero(total);
  $('#detalle-carga').textContent = `${detalles.length} producto(s) registrados.`;
  $('#btn-detalle-enviar').disabled = total <= 0;
  $('#btn-detalle-enviar').title = total <= 0 ? 'Debes agregar al menos un pan antes de enviar.' : '';
}
async function abrirDetalle(p) {
  pedidoActual = p;
  $('#titulo-detalle').textContent = `Pedido #${p.idPedido}`;
  $('#descripcion-detalle').textContent = 'Distribución de panes solicitados por turno.';
  $('#detalle-sucursal').textContent = p.sucursal || '—';
  $('#detalle-fecha').textContent = fechaVisible(p.fechaRequerida);
  $('#detalle-estado').textContent = p.estado || '—';
  $('#detalle-observaciones').textContent = p.observaciones ? `Observaciones: ${p.observaciones}` : 'Sin observaciones.';
  $('#detalle-carga').textContent = 'Consultando detalle…';
  $('#tabla-detalle').replaceChildren();
  $('#detalle-total').textContent = '0';
  const editable = puedeEditar && esBorrador(p);
  $('#btn-detalle-agregar').hidden = !editable;
  $('#btn-detalle-enviar').hidden = !editable;
  $('#btn-detalle-enviar').disabled = true;
  dialogDetalle.showModal();
  try {
    const datos = await app.api(`pedidos/${p.idPedido}/detalle`);
    if (!Array.isArray(datos)) throw new Error('El servidor devolvió un detalle no válido.');
    if (dialogDetalle.open && pedidoActual?.idPedido === p.idPedido) pintarDetalleFilas(datos);
  } catch (e) {
    $('#detalle-carga').textContent = e.message || 'Error al consultar el detalle.';
    app.notificar(e.message || 'Error al consultar detalle.', 'error');
  }
}
async function abrirAgregar(p) {
  if (!puedeEditar || !esBorrador(p)) return;
  pedidoActual = p;
  if (dialogDetalle.open) dialogDetalle.close();
  $('#form-agregar').reset();
  ocultarError('#error-agregar');
  $('#subtitulo-agregar').textContent = `Pedido #${p.idPedido} · ${p.sucursal || ''}. Indica cantidades enteras de cada turno.`;
  ['manana','resto','tarde'].forEach(x => $(`#agregar-${x}`).value = '0');
  actualizarTotalPan();
  const select = $('#agregar-producto');
  select.replaceChildren(new Option('Cargando panes…', ''));
  dialogAgregar.showModal();
  try {
    const panes = await obtenerPanes();
    if (!dialogAgregar.open) return;
    select.replaceChildren(new Option('Seleccionar pan…', ''));
    for (const pan of panes) select.add(new Option(`${pan.codigo || ''} · ${pan.nombre}`, String(pan.idProducto)));
    if (!panes.length) ponerError('#error-agregar', 'No hay panes finales activos en el catálogo.');
  } catch (e) { ponerError('#error-agregar', e.message || 'No se pudo consultar Productos.'); }
}
function cantidadesPan() {
  return ['manana','resto','tarde'].map(x => {
    const valor = $(`#agregar-${x}`).value.trim();
    return valor === '' ? NaN : Number(valor);
  });
}
function actualizarTotalPan() {
  const valores = cantidadesPan();
  const valido = valores.every(n => Number.isSafeInteger(n) && n >= 0);
  $('#total-agregar').textContent = valido ? numero(valores.reduce((a,b) => a+b,0)) : '—';
}
async function guardarPan(ev) {
  ev.preventDefault();
  if (guardando || !pedidoActual || !puedeEditar) return;
  ocultarError('#error-agregar');
  const idProducto = Number($('#agregar-producto').value);
  const [manana, resto, tarde] = cantidadesPan();
  if (!Number.isSafeInteger(idProducto) || idProducto <= 0) { ponerError('#error-agregar','Selecciona un pan terminado.'); return; }
  if (![manana,resto,tarde].every(v => Number.isSafeInteger(v) && v >= 0) || manana+resto+tarde <= 0) {
    ponerError('#error-agregar','Escribe cantidades enteras no negativas y solicita al menos un pan.'); return;
  }
  guardando = true;
  const btn = $('#guardar-agregar'); btn.disabled = true; btn.textContent = 'Agregando…';
  const p = pedidoActual;
  try {
    const respuesta = await app.api(`pedidos/${p.idPedido}/productos`, {
      metodo:'POST', datos:{idProducto, manana, resto, tarde}
    });
    dialogAgregar.close();
    app.notificar(respuesta?.mensaje || 'Pan agregado al pedido.', 'exito');
    await cargar();
    const actualizado = pedidos.find(x => Number(x.idPedido) === Number(p.idPedido)) || p;
    await abrirDetalle(actualizado);
  } catch (e) { ponerError('#error-agregar', e.message || 'No se pudo agregar el pan.'); }
  finally { guardando = false; btn.disabled = false; btn.textContent = 'Agregar pan'; }
}
function abrirConfirmacion(p) {
  if (!puedeEditar || !esBorrador(p) || dialogEnviar.open) return;
  paraEnviar = p;
  if (dialogDetalle.open) dialogDetalle.close();
  $('#mensaje-enviar').textContent = `¿Deseas enviar el pedido #${p.idPedido} de ${p.sucursal || 'la sucursal'}? Después de enviarlo no se podrán agregar ni editar panes. El pedido debe contener al menos un producto.`;
  dialogEnviar.showModal();
}
async function confirmarEnvio() {
  if (!paraEnviar || guardando) return;
  const p = paraEnviar;
  guardando = true;
  const btn = $('#confirmar-enviar'); btn.disabled = true; btn.textContent = 'Enviando…';
  try {
    const respuesta = await app.api(`pedidos/${p.idPedido}/enviar`, {metodo:'PUT'});
    dialogEnviar.close();
    paraEnviar = null;
    app.notificar(respuesta?.mensaje || `Pedido #${p.idPedido} enviado correctamente.`, 'exito');
    await cargar();
  } catch (e) { dialogEnviar.close(); app.notificar(e.message || 'No se pudo enviar el pedido.', 'error'); }
  finally { guardando = false; btn.disabled = false; btn.textContent = 'Sí, enviar pedido'; }
}

soloLectura();
$('#buscar').addEventListener('input', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-recargar').addEventListener('click', cargar);
$('#btn-nuevo').addEventListener('click', abrirNuevo);
$('#form-nuevo').addEventListener('submit', guardarNuevo);
$('#cerrar-nuevo').addEventListener('click', () => dialogNuevo.close());
$('#cancelar-nuevo').addEventListener('click', () => dialogNuevo.close());
$('#form-agregar').addEventListener('submit', guardarPan);
$('#cerrar-agregar').addEventListener('click', () => dialogAgregar.close());
$('#cancelar-agregar').addEventListener('click', () => dialogAgregar.close());
['manana','resto','tarde'].forEach(x => $(`#agregar-${x}`).addEventListener('input', actualizarTotalPan));
$('#cerrar-detalle').addEventListener('click', () => dialogDetalle.close());
$('#cerrar-detalle-pie').addEventListener('click', () => dialogDetalle.close());
$('#btn-detalle-agregar').addEventListener('click', () => pedidoActual && abrirAgregar(pedidoActual));
$('#btn-detalle-enviar').addEventListener('click', () => pedidoActual && abrirConfirmacion(pedidoActual));
$('#cancelar-enviar').addEventListener('click', () => { dialogEnviar.close(); paraEnviar = null; });
$('#confirmar-enviar').addEventListener('click', confirmarEnvio);
cargar();
