import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('producciones', ['ADMINISTRADOR', 'PRODUCCION']);
if (!app) throw new Error('No hay una sesión autorizada para Producción.');

const $ = selector => document.querySelector(selector);
const tabla = $('#tabla-producciones');
const modalNuevo = $('#dialog-nueva');
const modalDetalle = $('#dialog-detalle');
const modalConfirmar = $('#dialog-confirmar');
const formato = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 });
let producciones = [];
let produccionActual = null;
let accionPendiente = null;
let cargando = false;
let guardando = false;

function numero(valor) { return formato.format(Number(valor) || 0); }
function fechaCorta(valor) {
  if (!valor) return '—';
  const coinc = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return coinc ? `${coinc[3]}/${coinc[2]}/${coinc[1]}` : String(valor);
}
function fechaHoy() {
  const f = new Date();
  return `${f.getFullYear()}-${String(f.getMonth()+1).padStart(2,'0')}-${String(f.getDate()).padStart(2,'0')}`;
}
function estadoProd(p) { return String(p?.estado || '').trim().toUpperCase(); }
function idProd(p) { return Number(p?.idProduccion || 0); }
function celda(tr, texto, clase='') {
  const td = document.createElement('td');
  td.textContent = texto ?? '—';
  if (clase) td.className = clase;
  tr.append(td);
  return td;
}
function crearBoton(titulo, accion, clase='') {
  const boton = document.createElement('button');
  boton.type = 'button'; boton.className = `table-action ${clase}`;
  boton.textContent = titulo; boton.addEventListener('click', accion);
  return boton;
}
function chip(estado) {
  const el = document.createElement('span');
  el.className = `status-chip status-chip--${({PENDIENTE:'draft',EN_PROCESO:'working',FINALIZADA:'ready'})[estado] || 'off'}`;
  el.textContent = estado.replaceAll('_', ' ') || 'DESCONOCIDO';
  return el;
}
function irAlPlan(id) {
  const url = new URL('planificacion.html', window.location.href);
  url.searchParams.set('id', String(id));
  window.location.assign(url.href);
}
function mostrarError(selector, mensaje) {
  $(selector).textContent = mensaje;
  $(selector).hidden = false;
}
function pintar() {
  const busqueda = $('#buscar').value.trim().toLocaleLowerCase('es');
  const filtroEstado = $('#filtro-estado').value;
  const lista = producciones.filter(p => {
    const estado = estadoProd(p);
    return (!filtroEstado || estado === filtroEstado)
      && `${idProd(p)} ${p.fechaProduccion || ''} ${estado}`.toLocaleLowerCase('es').includes(busqueda);
  });
  tabla.replaceChildren();
  for (const p of lista) {
    const id = idProd(p); const estado = estadoProd(p);
    const tr = document.createElement('tr');
    celda(tr, `#${id}`, 'pedido-id');
    celda(tr, fechaCorta(p.fechaProduccion));
    const tdEstado = document.createElement('td'); tdEstado.append(chip(estado)); tr.append(tdEstado);
    celda(tr, fechaCorta(p.fechaInicio));
    celda(tr, fechaCorta(p.fechaFin));
    const tdAcciones = document.createElement('td'); tdAcciones.className = 'table-actions';
    tdAcciones.append(crearBoton('Ver detalle', () => abrirDetalle(p)));
    if (estado === 'PENDIENTE') {
      tdAcciones.append(crearBoton('Ver insumos', () => irAlPlan(id)));
      tdAcciones.append(crearBoton('Iniciar', () => prepararAccion(p)));
    } else if (estado === 'EN_PROCESO') {
      tdAcciones.append(crearBoton('Finalizar', () => prepararAccion(p)));
    }
    tr.append(tdAcciones); tabla.append(tr);
  }
  if (!lista.length) {
    const tr = document.createElement('tr'); const td = celda(tr, 'No hay producciones que coincidan con los filtros.', 'empty-table');
    td.colSpan = 6; tabla.append(tr);
  }
  $('#stat-total').textContent = numero(producciones.length);
  $('#stat-pendientes').textContent = numero(producciones.filter(p => estadoProd(p) === 'PENDIENTE').length);
  $('#stat-proceso').textContent = numero(producciones.filter(p => estadoProd(p) === 'EN_PROCESO').length);
  $('#stat-finalizadas').textContent = numero(producciones.filter(p => estadoProd(p) === 'FINALIZADA').length);
  $('#conteo-filtrado').textContent = `${lista.length} de ${producciones.length} producciones`;
}
async function cargar() {
  if (cargando) return;
  cargando = true;
  $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando producciones…';
  try {
    const datos = await app.api('producciones');
    if (!Array.isArray(datos)) throw new Error('La API no devolvió el listado esperado.');
    producciones = datos;
    pintar();
    $('#estado-carga').textContent = 'Producciones actualizadas.';
  } catch (e) {
    $('#estado-carga').textContent = e.message || 'No fue posible cargar las producciones.';
    app.notificar(e.message || 'No fue posible cargar producciones.', 'error');
  } finally {
    cargando = false; $('#btn-recargar').disabled = false;
  }
}
async function abrirNueva() {
  $('#form-nueva').reset(); $('#error-nueva').hidden = true;
  $('#fecha-produccion').value = fechaHoy();
  $('#fechas-sugeridas').textContent = 'Consultando fechas de pedidos enviados…';
  modalNuevo.showModal();
  try {
    const pedidos = await app.api('pedidos');
    if (!modalNuevo.open) return;
    const fechas = [...new Set((Array.isArray(pedidos) ? pedidos : [])
      .filter(p => String(p.estado).toUpperCase() === 'ENVIADO')
      .map(p => String(p.fechaRequerida || '').slice(0,10))
      .filter(f => /^\d{4}-\d{2}-\d{2}$/.test(f)))].sort();
    if (fechas.length) {
      $('#fecha-produccion').value = fechas[0];
      $('#fechas-sugeridas').textContent = `Fechas con pedidos ENVIADOS: ${fechas.map(fechaCorta).join(', ')}. Al generar, Oracle determina qué pedidos están disponibles.`;
    } else {
      $('#fechas-sugeridas').textContent = 'No aparecen pedidos ENVIADOS; crea y envía uno antes de generar una producción.';
    }
  } catch (e) {
    $('#fechas-sugeridas').textContent = 'No se pudieron cargar fechas sugeridas. Puedes ingresar manualmente la fecha requerida de un pedido ENVIADO.';
  }
}
async function guardarNueva(evento) {
  evento.preventDefault();
  if (guardando) return;
  $('#error-nueva').hidden = true;
  const fecha = $('#fecha-produccion').value;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
    mostrarError('#error-nueva', 'Selecciona una fecha válida (yyyy-MM-dd).'); return;
  }
  guardando = true;
  $('#guardar-nueva').disabled = true; $('#guardar-nueva').textContent = 'Generando…';
  try {
    const r = await app.api('producciones', { metodo:'POST', datos: { fecha } });
    if (!r?.exito || !(Number(r.id) > 0)) throw new Error(r?.mensaje || 'El servidor no devolvió una producción válida.');
    modalNuevo.close();
    app.notificar(r.mensaje || `Producción #${r.id} generada.`, 'exito');
    await cargar();
    const produccion = producciones.find(p => idProd(p) === Number(r.id));
    if (produccion) await abrirDetalle(produccion);
  } catch (e) {
    mostrarError('#error-nueva', e.message || 'No se pudo generar la producción.');
  } finally {
    guardando = false;
    $('#guardar-nueva').disabled = false; $('#guardar-nueva').textContent = 'Generar producción';
  }
}
async function abrirDetalle(p) {
  produccionActual = p;
  const id = idProd(p), estado = estadoProd(p);
  $('#titulo-detalle').textContent = `Producción #${id}`;
  $('#detalle-fecha').textContent = fechaCorta(p.fechaProduccion);
  $('#detalle-estado').textContent = estado.replaceAll('_',' ');
  $('#detalle-carga').textContent = 'Consultando panes programados…';
  $('#tabla-detalle').replaceChildren();
  $('#detalle-programado').textContent = '0'; $('#detalle-producido').textContent = '0';
  $('#detalle-plan').hidden = estado !== 'PENDIENTE';
  const botonAccion = $('#detalle-accion');
  botonAccion.hidden = estado === 'FINALIZADA';
  botonAccion.textContent = estado === 'PENDIENTE' ? 'Iniciar producción' : 'Finalizar producción';
  modalDetalle.showModal();
  try {
    const detalles = await app.api(`producciones/${id}/detalle`);
    if (!Array.isArray(detalles)) throw new Error('La API no devolvió un detalle válido.');
    let programado = 0, producido = 0;
    for (const d of detalles) {
      const tr = document.createElement('tr');
      celda(tr, d.producto || '—', 'strong-text');
      celda(tr, numero(d.cantidadProgramada), 'num');
      celda(tr, numero(d.cantidadProducida), 'num');
      programado += Number(d.cantidadProgramada) || 0;
      producido += Number(d.cantidadProducida) || 0;
      $('#tabla-detalle').append(tr);
    }
    if (!detalles.length) {
      const tr = document.createElement('tr'); const td = celda(tr, 'Sin productos programados.', 'empty-table');
      td.colSpan = 3; $('#tabla-detalle').append(tr);
    }
    $('#detalle-programado').textContent = numero(programado);
    $('#detalle-producido').textContent = numero(producido);
    $('#detalle-carga').textContent = `${detalles.length} tipo(s) de pan consultados.`;
    botonAccion.disabled = !detalles.length;
  } catch (e) {
    $('#detalle-carga').textContent = e.message || 'No se pudo consultar el detalle.';
    botonAccion.disabled = true;
  }
}
async function prepararAccion(p) {
  if (guardando) return;
  const id = idProd(p), estado = estadoProd(p);
  if (estado !== 'PENDIENTE' && estado !== 'EN_PROCESO') return;
  if (estado === 'PENDIENTE') {
    try {
      const plan = await app.api(`producciones/${id}/plan-insumos`);
      if (!plan?.puedeIniciar) {
        app.notificar(plan?.mensaje || 'La producción no tiene los insumos suficientes. Revisa la planificación.', 'error');
        irAlPlan(id);
        return;
      }
    } catch (e) { app.notificar(e.message || 'No fue posible validar los insumos.', 'error'); return; }
  }
  produccionActual = p;
  accionPendiente = estado === 'PENDIENTE' ? 'iniciar' : 'finalizar';
  $('#error-confirmar').hidden = true;
  $('#titulo-confirmar').textContent = `${accionPendiente === 'iniciar' ? 'Iniciar' : 'Finalizar'} producción #${id}`;
  $('#mensaje-confirmar').textContent = accionPendiente === 'iniciar'
    ? 'El sistema volverá a comprobar las recetas y descontará ingredientes del inventario. ¿Deseas continuar?'
    : 'Se registrarán los panes fabricados en inventario y los pedidos relacionados pasarán a LISTO. ¿Confirmas que terminó la fabricación?';
  if (modalDetalle.open) modalDetalle.close();
  modalConfirmar.showModal();
}
async function confirmarAccion() {
  if (!produccionActual || !accionPendiente || guardando) return;
  const id = idProd(produccionActual), accion = accionPendiente;
  guardando = true;
  $('#error-confirmar').hidden = true;
  $('#aceptar-confirmar').disabled = true;
  $('#aceptar-confirmar').textContent = 'Procesando…';
  try {
    const r = await app.api(`producciones/${id}/${accion}`, { metodo:'PUT' }); // sin Body; usuario del JWT
    if (!r?.exito) throw new Error(r?.mensaje || 'Oracle no confirmó la operación.');
    modalConfirmar.close();
    app.notificar(r.mensaje || `Producción #${id} actualizada.`, 'exito');
    produccionActual = null; accionPendiente = null;
    await cargar();
  } catch (e) {
    mostrarError('#error-confirmar', e.message || 'No fue posible actualizar la producción.');
  } finally {
    guardando = false;
    $('#aceptar-confirmar').disabled = false;
    $('#aceptar-confirmar').textContent = 'Confirmar';
  }
}
$('#buscar').addEventListener('input', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-recargar').addEventListener('click', cargar);
$('#btn-nuevo').addEventListener('click', abrirNueva);
$('#form-nueva').addEventListener('submit', guardarNueva);
$('#cerrar-nueva').addEventListener('click', () => modalNuevo.close());
$('#cancelar-nueva').addEventListener('click', () => modalNuevo.close());
$('#cerrar-detalle').addEventListener('click', () => modalDetalle.close());
$('#cerrar-detalle-pie').addEventListener('click', () => modalDetalle.close());
$('#detalle-plan').addEventListener('click', () => { if (produccionActual) irAlPlan(idProd(produccionActual)); });
$('#detalle-accion').addEventListener('click', () => { if (produccionActual) prepararAccion(produccionActual); });
$('#cancelar-confirmar').addEventListener('click', () => modalConfirmar.close());
$('#aceptar-confirmar').addEventListener('click', confirmarAccion);
cargar();
