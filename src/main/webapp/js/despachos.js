import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('despachos', ['ADMINISTRADOR', 'PRODUCCION', 'SUCURSAL']);
if (!app) throw new Error('No hay una sesión autorizada para Despachos.');

const $ = selector => document.querySelector(selector);
const esOperativo = app.rol === 'ADMINISTRADOR' || app.rol === 'PRODUCCION';
const tabla = $('#tabla-despachos');
const dialogNuevo = $('#dialog-nuevo');
const dialogDetalle = $('#dialog-detalle');
const dialogConfirmar = $('#dialog-confirmar');
const formato = new Intl.NumberFormat('es-GT', {maximumFractionDigits: 3});
let despachos = [];
let despachoActual = null;
let despachoPorRecibir = null;
let cargando = false;
let guardando = false;

function numero(valor) { return formato.format(Number(valor) || 0); }
function idDespacho(d) { return Number(d?.idDespacho || 0); }
function estadoDespacho(d) { return String(d?.estado || '').trim().toUpperCase(); }
function fechaVisible(valor) {
  if (!valor) return '—';
  const m = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/);
  return m ? `${m[3]}/${m[2]}/${m[1]}${m[4] ? ' · ' + m[4] + ':' + m[5] : ''}` : String(valor);
}
function crearCelda(tr, valor, clase = '') {
  const td = document.createElement('td');
  td.textContent = valor == null ? '—' : String(valor);
  if (clase) td.className = clase;
  tr.append(td);
  return td;
}
function crearBoton(texto, accion) {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'table-action';
  boton.textContent = texto;
  boton.addEventListener('click', accion);
  return boton;
}
function chip(estado) {
  const span = document.createElement('span');
  const estilos = {DESPACHADO: 'done', RECIBIDO: 'received'};
  span.className = `status-chip status-chip--${estilos[estado] || 'off'}`;
  span.textContent = estado.replaceAll('_', ' ') || 'DESCONOCIDO';
  return span;
}
function mostrarError(selector, mensaje) {
  $(selector).textContent = mensaje;
  $(selector).hidden = false;
}
function esconderError(selector) { $(selector).hidden = true; }
function configurarPermisos() {
  $('#btn-nuevo').hidden = !esOperativo;
  $('#aviso-sucursal').hidden = esOperativo;
  $('#alcance-datos').textContent = app.rol === 'SUCURSAL' ? 'SOLO TU SUCURSAL' : 'TODAS LAS SUCURSALES';
  $('#stat-total-etiqueta').textContent = app.rol === 'SUCURSAL' ? 'Mis despachos' : 'Despachos registrados';
  if (!esOperativo) {
    $('#descripcion-listado').textContent = 'Consulta únicamente los despachos de tu sucursal y revisa los panes enviados. La recepción se confirma desde Administración o Producción.';
  }
}
function pintar() {
  const busqueda = $('#buscar').value.trim().toLocaleLowerCase('es');
  const estado = $('#filtro-estado').value;
  const visibles = despachos.filter(d => {
    const e = estadoDespacho(d);
    return (!estado || estado === e)
      && `${d.idDespacho} ${d.idPedido} ${d.sucursal || ''} ${d.fechaDespacho || ''} ${d.observaciones || ''}`
        .toLocaleLowerCase('es').includes(busqueda);
  });
  tabla.replaceChildren();
  for (const d of visibles) {
    const tr = document.createElement('tr');
    crearCelda(tr, `#${idDespacho(d)}`, 'pedido-id');
    crearCelda(tr, `#${d.idPedido || '—'}`);
    crearCelda(tr, d.sucursal || '—', 'strong-text');
    crearCelda(tr, fechaVisible(d.fechaDespacho));
    const tdEstado = document.createElement('td');
    tdEstado.append(chip(estadoDespacho(d)));
    tr.append(tdEstado);
    crearCelda(tr, d.observaciones?.trim() || 'Sin observaciones', 'despacho-observacion');
    const acciones = document.createElement('td');
    acciones.className = 'table-actions';
    acciones.append(crearBoton('Ver detalle', () => abrirDetalle(d)));
    if (esOperativo && estadoDespacho(d) === 'DESPACHADO') {
      acciones.append(crearBoton('Marcar recibido', () => prepararRecepcion(d)));
    }
    tr.append(acciones);
    tabla.append(tr);
  }
  if (!visibles.length) {
    const tr = document.createElement('tr');
    const td = crearCelda(tr, 'No hay despachos que coincidan con los filtros.', 'empty-table');
    td.colSpan = 7; tabla.append(tr);
  }
  $('#stat-total').textContent = numero(despachos.length);
  $('#stat-despachados').textContent = numero(despachos.filter(d => estadoDespacho(d) === 'DESPACHADO').length);
  $('#stat-recibidos').textContent = numero(despachos.filter(d => estadoDespacho(d) === 'RECIBIDO').length);
  $('#stat-sucursales').textContent = numero(new Set(despachos.map(d => d.sucursal).filter(Boolean)).size);
  $('#conteo-filtrado').textContent = `${visibles.length} de ${despachos.length} despachos`;
}
async function cargar() {
  if (cargando) return;
  cargando = true;
  $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando despachos de Oracle…';
  try {
    const datos = await app.api('despachos');
    if (!Array.isArray(datos)) throw new Error('La API no devolvió el listado de despachos esperado.');
    despachos = datos;
    pintar();
    $('#estado-carga').textContent = 'Despachos actualizados.';
  } catch (error) {
    $('#estado-carga').textContent = error.message || 'No fue posible cargar despachos.';
    app.notificar(error.message || 'No fue posible consultar despachos.', 'error');
  } finally {
    cargando = false;
    $('#btn-recargar').disabled = false;
  }
}
async function abrirNuevo() {
  if (!esOperativo || guardando) return;
  $('#form-nuevo').reset();
  esconderError('#error-nuevo');
  const select = $('#nuevo-pedido');
  select.replaceChildren(new Option('Consultando pedidos LISTOS…', ''));
  $('#guardar-nuevo').disabled = true;
  $('#ayuda-pedido').textContent = 'Buscando pedidos listos para despacho…';
  dialogNuevo.showModal();
  try {
    // La consulta de permisos se valida nuevamente en Java al registrar.
    const [pedidos, despachosActuales] = await Promise.all([app.api('pedidos'), app.api('despachos')]);
    if (!dialogNuevo.open) return;
    if (!Array.isArray(pedidos) || !Array.isArray(despachosActuales)) {
      throw new Error('No se pudo obtener el catálogo de pedidos disponibles.');
    }
    const despachados = new Set(despachosActuales.map(d => Number(d.idPedido)));
    const listos = pedidos.filter(p => String(p.estado || '').trim().toUpperCase() === 'LISTO'
      && !despachados.has(Number(p.idPedido)) && Number(p.idPedido) > 0);
    select.replaceChildren(new Option('Selecciona un pedido LISTO…', ''));
    for (const p of listos) {
      const texto = `Pedido #${p.idPedido} · ${p.sucursal || 'Sucursal'} · requerido ${fechaVisible(p.fechaRequerida)}`;
      select.add(new Option(texto, String(p.idPedido)));
    }
    $('#guardar-nuevo').disabled = listos.length === 0;
    $('#ayuda-pedido').textContent = listos.length
      ? `${listos.length} pedido(s) LISTOS sin despacho. Solo se registrará el pedido seleccionado.`
      : 'No hay pedidos LISTOS sin despacho. Primero finaliza una producción desde el módulo Producción.';
  } catch (error) {
    if (dialogNuevo.open) {
      select.replaceChildren(new Option('No se pudieron cargar pedidos', ''));
      $('#ayuda-pedido').textContent = 'Inténtalo de nuevo después de actualizar.';
      mostrarError('#error-nuevo', error.message || 'No fue posible consultar los pedidos.');
    }
  }
}
async function guardarNuevo(evento) {
  evento.preventDefault();
  if (!esOperativo || guardando) return;
  esconderError('#error-nuevo');
  const idPedido = Number($('#nuevo-pedido').value);
  if (!Number.isSafeInteger(idPedido) || idPedido <= 0) {
    mostrarError('#error-nuevo', 'Selecciona un pedido LISTO válido.'); return;
  }
  guardando = true;
  const boton = $('#guardar-nuevo');
  boton.disabled = true; boton.textContent = 'Registrando…';
  try {
    // No enviar idUsuario; el backend lo obtiene del JWT.
    const datos = {idPedido, observaciones: $('#nuevo-observaciones').value.trim()};
    const r = await app.api('despachos', {metodo: 'POST', datos});
    if (!r?.exito || Number(r.id) <= 0) {
      throw new Error(r?.mensaje || 'El servidor no confirmó el despacho.');
    }
    dialogNuevo.close();
    app.notificar(r.mensaje || `Despacho #${r.id} registrado.`, 'exito');
    await cargar();
    const nuevo = despachos.find(d => idDespacho(d) === Number(r.id));
    if (nuevo) await abrirDetalle(nuevo);
  } catch (error) {
    if (dialogNuevo.open) mostrarError('#error-nuevo', error.message || 'No fue posible registrar el despacho.');
    else app.notificar(error.message || 'No fue posible actualizar despachos.', 'error');
  } finally {
    guardando = false;
    boton.disabled = false; boton.textContent = 'Registrar despacho';
  }
}
async function abrirDetalle(d) {
  despachoActual = d;
  const id = idDespacho(d);
  $('#titulo-detalle').textContent = `Despacho #${id}`;
  $('#detalle-pedido').textContent = `#${d.idPedido}`;
  $('#detalle-sucursal').textContent = d.sucursal || '—';
  $('#detalle-estado').textContent = estadoDespacho(d).replaceAll('_', ' ');
  $('#detalle-observaciones').textContent = d.observaciones?.trim() || 'Sin observaciones.';
  $('#tabla-detalle').replaceChildren();
  $('#detalle-total').textContent = '0';
  $('#detalle-carga').textContent = 'Consultando panes enviados…';
  $('#detalle-recibir').hidden = !(esOperativo && estadoDespacho(d) === 'DESPACHADO');
  $('#detalle-recibir').disabled = true;
  if (!dialogDetalle.open) dialogDetalle.showModal();
  try {
    const detalles = await app.api(`despachos/${id}/detalle`);
    if (!dialogDetalle.open || idDespacho(despachoActual) !== id) return;
    if (!Array.isArray(detalles)) throw new Error('La API no devolvió un detalle válido.');
    let total = 0;
    for (const item of detalles) {
      const tr = document.createElement('tr');
      crearCelda(tr, item.producto || '—', 'strong-text');
      crearCelda(tr, numero(item.cantidad), 'num');
      total += Number(item.cantidad) || 0;
      $('#tabla-detalle').append(tr);
    }
    if (!detalles.length) {
      const tr = document.createElement('tr');
      const td = crearCelda(tr, 'Este despacho no contiene productos.', 'empty-table');
      td.colSpan = 2; $('#tabla-detalle').append(tr);
    }
    $('#detalle-total').textContent = numero(total);
    $('#detalle-carga').textContent = `${detalles.length} tipo(s) de pan despachados.`;
    $('#detalle-recibir').disabled = total <= 0;
  } catch (error) {
    $('#detalle-carga').textContent = error.message || 'No fue posible consultar el detalle.';
    $('#detalle-recibir').disabled = true;
  }
}
function prepararRecepcion(d) {
  if (!esOperativo || guardando || estadoDespacho(d) !== 'DESPACHADO') return;
  despachoPorRecibir = d;
  $('#titulo-confirmar').textContent = `Confirmar recepción #${idDespacho(d)}`;
  $('#mensaje-confirmar').textContent = `¿Confirmas que la sucursal ${d.sucursal || 'correspondiente'} recibió el despacho #${idDespacho(d)} (pedido #${d.idPedido})? Esta acción cambia el estado a RECIBIDO.`;
  esconderError('#error-confirmar');
  dialogConfirmar.showModal();
}
async function confirmarRecepcion() {
  if (!esOperativo || guardando || !despachoPorRecibir) return;
  guardando = true;
  const boton = $('#aceptar-confirmar');
  boton.disabled = true; boton.textContent = 'Confirmando…';
  const id = idDespacho(despachoPorRecibir);
  try {
    const r = await app.api(`despachos/${id}/recibir`, {metodo: 'PUT'});
    if (!r?.exito) throw new Error(r?.mensaje || 'El servidor no confirmó la recepción.');
    dialogConfirmar.close();
    despachoPorRecibir = null;
    if (dialogDetalle.open) dialogDetalle.close();
    app.notificar(r.mensaje || `Despacho #${id} recibido.`, 'exito');
    await cargar();
  } catch (error) {
    mostrarError('#error-confirmar', error.message || 'No fue posible confirmar la recepción.');
  } finally {
    guardando = false;
    boton.disabled = false; boton.textContent = 'Sí, confirmar recepción';
  }
}

configurarPermisos();
$('#btn-recargar').addEventListener('click', cargar);
$('#buscar').addEventListener('input', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-nuevo').addEventListener('click', abrirNuevo);
$('#form-nuevo').addEventListener('submit', guardarNuevo);
$('#cerrar-nuevo').addEventListener('click', () => dialogNuevo.close());
$('#cancelar-nuevo').addEventListener('click', () => dialogNuevo.close());
$('#cerrar-detalle').addEventListener('click', () => dialogDetalle.close());
$('#cerrar-detalle-pie').addEventListener('click', () => dialogDetalle.close());
$('#detalle-recibir').addEventListener('click', () => {
  if (despachoActual) prepararRecepcion(despachoActual);
});
$('#cancelar-confirmar').addEventListener('click', () => dialogConfirmar.close());
$('#aceptar-confirmar').addEventListener('click', confirmarRecepcion);
void cargar();
