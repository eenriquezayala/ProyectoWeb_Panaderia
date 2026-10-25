import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('inventario', ['ADMINISTRADOR', 'PRODUCCION']);
if (!app) throw new Error('La sesión no permite consultar inventario.');

const $ = selector => document.querySelector(selector);
const tbody = $('#tabla-inventario');
const dialogMovimiento = $('#dialog-movimiento');
const dialogAjuste = $('#dialog-confirmar-ajuste');
const dialogHistorial = $('#dialog-historial');
const numero = valor => new Intl.NumberFormat('es-GT', {maximumFractionDigits: 3}).format(Number(valor) || 0);
let inventario = [];
let operacion = 'entrada';
let cargando = false;
let guardando = false;
let confirmarResolver;

function celda(tr, contenido, clase = '') {
  const td = document.createElement('td'); td.textContent = contenido == null ? '—' : String(contenido);
  if (clase) td.className = clase;
  tr.append(td); return td;
}
function estadoBadge(texto, estilo) {
  const chip = document.createElement('span'); chip.className = `status-chip status-chip--${estilo}`;
  chip.textContent = texto; return chip;
}
function productoActivo() {
  return inventario.find(i => Number(i.idProducto) === Number($('#movimiento-producto').value));
}
function pintar() {
  const buscar = $('#buscar').value.trim().toLocaleLowerCase('es');
  const tipo = $('#filtro-tipo').value;
  const estado = $('#filtro-estado').value;
  const visibles = inventario.filter(p => {
    const coincide = `${p.codigo || ''} ${p.nombre || ''}`.toLocaleLowerCase('es').includes(buscar);
    return coincide && (!tipo || p.tipo === tipo) && (!estado || String(p.estadoInventario) === estado);
  });
  tbody.replaceChildren();
  for (const p of visibles) {
    const tr = document.createElement('tr');
    const tdProducto = celda(tr, p.nombre || 'Sin nombre', 'strong-text');
    const codigo = document.createElement('small'); codigo.textContent = p.codigo || ''; tdProducto.append(codigo);
    celda(tr, p.tipo === 'FINAL' ? 'Pan terminado' : 'Ingrediente');
    celda(tr, numero(p.existencia), 'num');
    celda(tr, numero(p.existenciaMinima), 'num');
    celda(tr, p.unidadMedida || '—');
    const estadoTd = document.createElement('td');
    const bajo = p.estadoInventario === 'BAJO';
    estadoTd.append(estadoBadge(bajo ? 'Existencia baja' : 'Normal', bajo ? 'off' : 'ok'));
    tr.append(estadoTd);
    const tdAcciones = document.createElement('td'); tdAcciones.className = 'table-actions';
    const btnHistorial = document.createElement('button'); btnHistorial.type = 'button'; btnHistorial.className = 'table-action'; btnHistorial.textContent = 'Historial';
    btnHistorial.addEventListener('click', () => mostrarHistorial(p));
    const btnEntrada = document.createElement('button'); btnEntrada.type = 'button'; btnEntrada.className = 'table-action'; btnEntrada.textContent = 'Entrada';
    btnEntrada.addEventListener('click', () => abrirMovimiento('entrada', p.idProducto));
    const btnAjuste = document.createElement('button'); btnAjuste.type = 'button'; btnAjuste.className = 'table-action table-action--danger'; btnAjuste.textContent = 'Ajustar';
    btnAjuste.addEventListener('click', () => abrirMovimiento('ajuste', p.idProducto));
    tdAcciones.append(btnHistorial, btnEntrada, btnAjuste);
    tr.append(tdAcciones); tbody.append(tr);
  }
  if (!visibles.length) {
    const tr = document.createElement('tr'); const td = celda(tr, 'No hay productos que coincidan con los filtros.', 'empty-table');
    td.colSpan = 7; tbody.append(tr);
  }
  $('#total-productos').textContent = inventario.length;
  $('#total-insumos').textContent = inventario.filter(i => i.tipo === 'INSUMO').length;
  $('#total-finales').textContent = inventario.filter(i => i.tipo === 'FINAL').length;
  $('#total-bajos').textContent = inventario.filter(i => i.estadoInventario === 'BAJO').length;
  $('#conteo-filtrado').textContent = `${visibles.length} de ${inventario.length} productos`;
}
async function cargar() {
  if (cargando) return;
  cargando = true;
  $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando inventario';
  try {
    const resultado = await app.api('inventario');
    if (!Array.isArray(resultado)) throw new Error('La API no devolvió una lista de inventario.');
    inventario = resultado;
    pintar();
    $('#estado-carga').textContent = 'Existencias actualizadas.';
  } catch (err) {
    $('#estado-carga').textContent = err.message || 'No se pudo consultar el inventario.';
    app.notificar(err.message || 'No se pudo conectar con el servidor.', 'error');
  } finally { cargando = false; $('#btn-recargar').disabled = false; }
}
function actualizarPrevia() {
  const p = productoActivo();
  $('#existencia-previa').textContent = p ? numero(p.existencia) : '—';
  $('#unidad-previa').textContent = p?.unidadMedida || '—';
}
function abrirMovimiento(modo, idSeleccionado = null) {
  operacion = modo;
  $('#form-movimiento').reset();
  $('#error-movimiento').hidden = true;
  $('#titulo-movimiento').textContent = modo === 'entrada' ? 'Registrar entrada' : 'Ajustar existencia';
  $('#etiqueta-cantidad').textContent = modo === 'entrada' ? 'Cantidad a ingresar *' : 'Nueva existencia total *';
  $('#movimiento-cantidad').min = modo === 'entrada' ? '0.0001' : '0';
  $('#movimiento-cantidad').placeholder = modo === 'entrada' ? 'Ej. 5' : 'Ej. 24.5';
  $('#guardar-movimiento').textContent = modo === 'entrada' ? 'Registrar entrada' : 'Revisar ajuste';
  $('#subtitulo-movimiento').textContent = modo === 'entrada'
    ? 'Una entrada suma existencias. El usuario responsable se obtiene del JWT.'
    : 'El ajuste fija la existencia TOTAL, no suma ni resta esa cifra. Confirma antes de guardar.';
  const select = $('#movimiento-producto');
  select.replaceChildren(); select.add(new Option('Seleccionar producto…', ''));
  for (const p of inventario) select.add(new Option(`${p.codigo || ''} · ${p.nombre} (${p.unidadMedida || 'unidad'})`, String(p.idProducto)));
  if (idSeleccionado != null) select.value = String(idSeleccionado);
  actualizarPrevia();
  if (!inventario.length) { app.notificar('No hay productos activos para registrar movimientos.', 'error'); return; }
  dialogMovimiento.showModal();
  select.focus();
}
function confirmarAjuste(p, nuevaExistencia) {
  const diferencia = nuevaExistencia - Number(p.existencia);
  $('#mensaje-confirmar-ajuste').textContent = `Vas a establecer la existencia de «${p.nombre}» en ${numero(nuevaExistencia)} ${p.unidadMedida || ''}. Actual: ${numero(p.existencia)}. Variación: ${diferencia > 0 ? '+' : ''}${numero(diferencia)}. ¿Confirmas?`;
  return new Promise(resolve => {
    confirmarResolver = resolve;
    dialogAjuste.showModal();
  });
}
function cerrarConfirmacion(aceptado) {
  if (!dialogAjuste.open) return;
  dialogAjuste.close();
  if (confirmarResolver) { const f = confirmarResolver; confirmarResolver = null; f(aceptado); }
}
async function guardarMovimiento(evento) {
  evento.preventDefault(); if (guardando) return;
  $('#error-movimiento').hidden = true;
  const p = productoActivo();
  const texto = $('#movimiento-cantidad').value.trim();
  const cantidad = Number(texto);
  if (!p || !texto || !Number.isFinite(cantidad) || (operacion === 'entrada' ? cantidad <= 0 : cantidad < 0)) {
    mostrarError('Elige un producto e ingresa una cantidad válida. Para ajustes se permite cero.'); return;
  }
  if (operacion === 'ajuste' && cantidad === Number(p.existencia)) {
    mostrarError('La nueva existencia es igual a la actual. No hay cambios para registrar.'); return;
  }
  if (operacion === 'ajuste') {
    // Cerramos el formulario antes de confirmar para evitar diálogos superpuestos.
    dialogMovimiento.close();
    const aceptar = await confirmarAjuste(p, cantidad);
    if (!aceptar) { dialogMovimiento.showModal(); return; }
  }
  guardando = true;
  const btn = $('#guardar-movimiento'); btn.disabled = true; btn.textContent = 'Guardando…';
  try {
    const esEntrada = operacion === 'entrada';
    const resultado = await app.api(esEntrada ? 'inventario/entradas' : 'inventario/ajustes', {
      metodo: esEntrada ? 'POST' : 'PUT',
      datos: esEntrada ? {idProducto: Number(p.idProducto), cantidad} : {idProducto: Number(p.idProducto), nuevaExistencia: cantidad}
    });
    if (dialogMovimiento.open) dialogMovimiento.close();
    app.notificar(resultado?.mensaje || 'Movimiento guardado correctamente.', 'exito');
    await cargar();
  } catch (err) {
    if (!dialogMovimiento.open) dialogMovimiento.showModal();
    mostrarError(err.message || 'No fue posible registrar el movimiento.');
  } finally {
    guardando = false;
    btn.disabled = false;
    btn.textContent = operacion === 'entrada' ? 'Registrar entrada' : 'Revisar ajuste';
  }
}
function mostrarError(mensaje) { const el = $('#error-movimiento'); el.textContent = mensaje; el.hidden = false; }
function fechaLocal(valor) {
  if (!valor) return '—';
  const fecha = new Date(valor);
  return Number.isNaN(fecha.getTime()) ? String(valor) : new Intl.DateTimeFormat('es-GT', {dateStyle:'short', timeStyle:'short'}).format(fecha);
}
async function mostrarHistorial(p) {
  $('#titulo-historial').textContent = `Movimientos: ${p.nombre}`;
  $('#historial-descripcion').textContent = `Producto ${p.codigo || p.idProducto} · Existencia actual ${numero(p.existencia)} ${p.unidadMedida || ''}`;
  $('#historial-estado').textContent = 'Consultando movimientos en Oracle…';
  $('#tabla-movimientos').replaceChildren();
  dialogHistorial.showModal();
  try {
    const movimientos = await app.api(`inventario/${p.idProducto}/movimientos`);
    if (!Array.isArray(movimientos)) throw new Error('Respuesta de movimientos inválida.');
    const body = $('#tabla-movimientos');
    for (const m of movimientos) {
      const tr = document.createElement('tr');
      celda(tr, fechaLocal(m.fecha));
      celda(tr, String(m.tipo || '—').replaceAll('_',' '));
      celda(tr, numero(m.cantidad), 'num');
      celda(tr, numero(m.saldoAnterior), 'num');
      celda(tr, numero(m.saldoNuevo), 'num');
      body.append(tr);
    }
    if (!movimientos.length) {
      const tr = document.createElement('tr'); const td = celda(tr, 'Aún no hay movimientos registrados para este producto.', 'empty-table');
      td.colSpan = 5; body.append(tr);
    }
    $('#historial-estado').textContent = `${movimientos.length} movimiento(s) registrados.`;
  } catch (err) { $('#historial-estado').textContent = err.message || 'No fue posible cargar el historial.'; app.notificar(err.message || 'Error al consultar movimientos.', 'error'); }
}

$('#buscar').addEventListener('input', pintar);
$('#filtro-tipo').addEventListener('change', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-recargar').addEventListener('click', cargar);
$('#btn-nuevo').addEventListener('click', () => abrirMovimiento('entrada'));
$('#movimiento-producto').addEventListener('change', actualizarPrevia);
$('#form-movimiento').addEventListener('submit', guardarMovimiento);
$('#cerrar-movimiento').addEventListener('click', () => dialogMovimiento.close());
$('#cancelar-movimiento').addEventListener('click', () => dialogMovimiento.close());
$('#confirmar-ajuste').addEventListener('click', () => cerrarConfirmacion(true));
$('#cancelar-ajuste').addEventListener('click', () => cerrarConfirmacion(false));
dialogAjuste.addEventListener('cancel', e => { e.preventDefault(); cerrarConfirmacion(false); });
$('#cerrar-historial').addEventListener('click', () => dialogHistorial.close());
$('#cerrar-historial-pie').addEventListener('click', () => dialogHistorial.close());
cargar();
