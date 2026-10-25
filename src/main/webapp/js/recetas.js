import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('recetas', ['ADMINISTRADOR', 'PRODUCCION']);
if (!app) throw new Error('La sesión no permite consultar recetas.');

const $ = selector => document.querySelector(selector);
const tbody = $('#tabla-recetas');
const dialogReceta = $('#dialog-receta');
const dialogDetalle = $('#dialog-detalle');
const dialogIngrediente = $('#dialog-ingrediente');
const formatoNumero = new Intl.NumberFormat('es-GT', {maximumFractionDigits: 3});
const numero = v => formatoNumero.format(Number(v) || 0);
let recetas = [];
let productos = [];
let conteos = new Map();
let recetaActual = null;
let cargando = false;
let guardando = false;

function celda(tr, valor, clase = '') {
  const td = document.createElement('td');
  td.textContent = valor === null || valor === undefined ? '—' : String(valor);
  if (clase) td.className = clase;
  tr.append(td);
  return td;
}
function badge(texto, estilo = 'off') {
  const el = document.createElement('span');
  el.className = `status-chip status-chip--${estilo}`;
  el.textContent = texto;
  return el;
}
function errorFormulario(selector, msg) {
  const el = $(selector); el.textContent = msg; el.hidden = false;
}
function recId(receta) { return Number(receta.idReceta || receta.id || 0); }
function nombreReceta(r) { return r.receta || r.nombre || `Receta #${recId(r)}`; }
function nombreProducto(r) { return r.producto || r.nombreProducto || 'Pan sin nombre'; }
function detalleTexto(item) { return item.insumo || item.nombre || item.producto || 'Insumo'; }
function pintar() {
  const texto = $('#buscar').value.trim().toLocaleLowerCase('es');
  const estado = $('#filtro-completa').value;
  const lista = recetas.filter(r => {
    const coincide = `${nombreReceta(r)} ${nombreProducto(r)} ${r.descripcion || ''}`.toLocaleLowerCase('es').includes(texto);
    const cantidad = conteos.get(recId(r));
    return coincide && (!estado || (estado === 'con' ? cantidad > 0 : cantidad === 0));
  });
  tbody.replaceChildren();
  for (const r of lista) {
    const tr = document.createElement('tr');
    const tdReceta = celda(tr, nombreReceta(r), 'strong-text');
    if (r.descripcion) {
      const descripcion = document.createElement('small');
      descripcion.textContent = r.descripcion;
      tdReceta.append(descripcion);
    }
    celda(tr, nombreProducto(r));
    celda(tr, numero(r.rendimiento), 'num');
    const tdEstado = document.createElement('td');
    const cantidad = conteos.get(recId(r));
    tdEstado.append(cantidad === undefined ? badge('Sin comprobar') : cantidad > 0 ? badge(`${cantidad} ingrediente${cantidad === 1 ? '' : 's'}`, 'ok') : badge('Sin ingredientes'));
    tr.append(tdEstado);
    const tdAcciones = document.createElement('td'); tdAcciones.className = 'table-actions';
    const ver = document.createElement('button'); ver.type = 'button'; ver.className = 'table-action'; ver.textContent = 'Ver detalle';
    ver.addEventListener('click', () => abrirDetalle(r));
    tdAcciones.append(ver); tr.append(tdAcciones); tbody.append(tr);
  }
  if (!lista.length) {
    const tr = document.createElement('tr');
    const td = celda(tr, 'No hay recetas que coincidan con los filtros.', 'empty-table');
    td.colSpan = 5; tbody.append(tr);
  }
  $('#total-recetas').textContent = recetas.length;
  $('#recetas-completas').textContent = recetas.filter(r => conteos.get(recId(r)) > 0).length;
  $('#recetas-vacias').textContent = recetas.filter(r => conteos.get(recId(r)) === 0).length;
  $('#total-insumos').textContent = productos.filter(p => p.tipo === 'INSUMO' && Number(p.estado) === 1).length;
  $('#conteo-filtrado').textContent = `${lista.length} de ${recetas.length} recetas`;
}
async function cargar() {
  if (cargando) return;
  cargando = true;
  $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando recetas y catálogo de productos…';
  try {
    const [respuestaRecetas, respuestaProductos] = await Promise.all([
      app.api('recetas'), app.api('productos')
    ]);
    if (!Array.isArray(respuestaRecetas) || !Array.isArray(respuestaProductos)) {
      throw new Error('El servidor no devolvió un listado válido.');
    }
    recetas = respuestaRecetas;
    productos = respuestaProductos;
    conteos = new Map();
    // Consultar en lotes: evitar decenas de conexiones simultáneas a Oracle.
    let fallos = 0;
    for (let inicio = 0; inicio < recetas.length; inicio += 6) {
      const lote = recetas.slice(inicio, inicio + 6);
      const resultados = await Promise.allSettled(lote.map(r => app.api(`recetas/${recId(r)}/detalle`)));
      resultados.forEach((resultado, index) => {
        if (resultado.status === 'fulfilled' && Array.isArray(resultado.value)) {
          conteos.set(recId(lote[index]), resultado.value.length);
        } else {
          fallos++;
        }
      });
    }
    pintar();
    $('#estado-carga').textContent = fallos ? `Recetas cargadas; no se pudieron comprobar ${fallos} detalles.` : 'Recetas actualizadas.';
  } catch (err) {
    $('#estado-carga').textContent = err.message || 'No fue posible consultar las recetas.';
    app.notificar(err.message || 'Error de conexión', 'error');
  } finally {
    cargando = false;
    $('#btn-recargar').disabled = false;
  }
}
function llenarSelect(select, lista, placeholder) {
  select.replaceChildren();
  const opcion = new Option(placeholder, ''); select.add(opcion);
  for (const elemento of lista) select.add(new Option(elemento.etiqueta, String(elemento.id)));
}
function abrirCrear() {
  $('#form-receta').reset(); $('#error-receta').hidden = true;
  const finales = productos.filter(p => p.tipo === 'FINAL' && Number(p.estado) === 1);
  llenarSelect($('#receta-producto'), finales.map(p => ({id: p.idProducto, etiqueta: `${p.codigo || ''} · ${p.nombre}`})), 'Seleccionar pan…');
  if (!finales.length) {
    app.notificar('No hay panes activos disponibles. Registra un producto FINAL primero.', 'error');
    return;
  }
  dialogReceta.showModal();
  $('#receta-producto').focus();
}
async function guardarReceta(evento) {
  evento.preventDefault();
  if (guardando) return;
  $('#error-receta').hidden = true;
  const idProducto = Number($('#receta-producto').value);
  const nombre = $('#receta-nombre').value.trim();
  const rendimiento = Number($('#receta-rendimiento').value);
  const descripcion = $('#receta-descripcion').value.trim();
  if (!idProducto || !nombre || !Number.isSafeInteger(rendimiento) || rendimiento <= 0) {
    errorFormulario('#error-receta', 'Selecciona un pan, escribe el nombre y utiliza un rendimiento entero mayor que cero.'); return;
  }
  guardando = true;
  const btn = $('#guardar-receta'); btn.disabled = true; btn.textContent = 'Creando…';
  try {
    const resultado = await app.api('recetas', {metodo: 'POST', datos: {idProducto, nombre, rendimiento, descripcion}});
    dialogReceta.close();
    app.notificar(resultado?.mensaje || 'Receta creada. Ahora agrega sus ingredientes.', 'exito');
    await cargar();
    const nueva = recetas.find(r => recId(r) === Number(resultado?.id));
    if (nueva) await abrirDetalle(nueva);
  } catch (err) {
    errorFormulario('#error-receta', err.message || 'No fue posible crear la receta.');
  } finally {
    guardando = false; btn.disabled = false; btn.textContent = 'Crear receta';
  }
}
async function abrirDetalle(receta) {
  recetaActual = receta;
  $('#titulo-detalle').textContent = nombreReceta(receta);
  $('#detalle-descripcion').textContent = receta.descripcion || 'Receta sin observaciones.';
  $('#detalle-producto').textContent = nombreProducto(receta);
  $('#detalle-rendimiento').textContent = `${numero(receta.rendimiento)} panes`;
  $('#detalle-estado').textContent = 'Consultando ingredientes…';
  $('#tabla-ingredientes').replaceChildren();
  dialogDetalle.showModal();
  try {
    const lista = await app.api(`recetas/${recId(receta)}/detalle`);
    // El usuario pudo abrir otra receta mientras llegaba la respuesta.
    if (!dialogDetalle.open || recId(recetaActual || {}) !== recId(receta)) return;
    if (!Array.isArray(lista)) throw new Error('El servidor no devolvió el detalle esperado.');
    const body = $('#tabla-ingredientes');
    for (const item of lista) {
      const tr = document.createElement('tr');
      celda(tr, detalleTexto(item), 'strong-text');
      celda(tr, numero(item.cantidad), 'num');
      celda(tr, item.unidadMedida || '—');
      body.append(tr);
    }
    if (!lista.length) {
      const tr = document.createElement('tr');
      const td = celda(tr, 'Todavía no hay ingredientes. Agrégalos para completar la receta.', 'empty-table');
      td.colSpan = 3; body.append(tr);
    }
    conteos.set(recId(receta), lista.length);
    pintar();
    $('#detalle-estado').textContent = `${lista.length} ingrediente(s) por cada rendimiento de la receta.`;
  } catch (err) {
    $('#detalle-estado').textContent = err.message || 'No fue posible obtener los ingredientes.';
    app.notificar(err.message || 'No fue posible consultar el detalle.', 'error');
  }
}
function abrirIngrediente() {
  if (!recetaActual) return;
  const insumos = productos.filter(p => p.tipo === 'INSUMO' && Number(p.estado) === 1);
  if (!insumos.length) { app.notificar('No hay ingredientes activos en el catálogo de Productos.', 'error'); return; }
  $('#form-ingrediente').reset(); $('#error-ingrediente').hidden = true;
  $('#subtitulo-ingrediente').textContent = `Receta: ${nombreReceta(recetaActual)} · Rendimiento: ${numero(recetaActual.rendimiento)} panes.`;
  llenarSelect($('#ingrediente-insumo'), insumos.map(p => ({id: p.idProducto, etiqueta: `${p.nombre} (${p.unidadMedida || 'unidad'})`})), 'Seleccionar insumo…');
  dialogDetalle.close(); dialogIngrediente.showModal();
}
async function guardarIngrediente(evento) {
  evento.preventDefault(); if (guardando || !recetaActual) return;
  $('#error-ingrediente').hidden = true;
  const idInsumo = Number($('#ingrediente-insumo').value);
  const cantidad = Number($('#ingrediente-cantidad').value);
  if (!idInsumo || $('#ingrediente-cantidad').value === '' || !Number.isFinite(cantidad) || cantidad <= 0) {
    errorFormulario('#error-ingrediente', 'Selecciona un insumo e ingresa una cantidad mayor que cero.'); return;
  }
  guardando = true;
  const btn = $('#guardar-ingrediente'); btn.disabled = true; btn.textContent = 'Agregando…';
  const receta = recetaActual;
  try {
    const resultado = await app.api(`recetas/${recId(receta)}/ingredientes`, {metodo:'POST', datos:{idInsumo, cantidad}});
    dialogIngrediente.close();
    app.notificar(resultado?.mensaje || 'Ingrediente agregado correctamente.', 'exito');
    await abrirDetalle(receta);
  } catch (err) { errorFormulario('#error-ingrediente', err.message || 'No fue posible agregar el ingrediente.'); }
  finally { guardando = false; btn.disabled = false; btn.textContent = 'Agregar ingrediente'; }
}

$('#buscar').addEventListener('input', pintar);
$('#filtro-completa').addEventListener('change', pintar);
$('#btn-recargar').addEventListener('click', cargar);
$('#btn-nuevo').addEventListener('click', abrirCrear);
$('#form-receta').addEventListener('submit', guardarReceta);
$('#form-ingrediente').addEventListener('submit', guardarIngrediente);
$('#cerrar-receta').addEventListener('click', () => dialogReceta.close());
$('#cancelar-receta').addEventListener('click', () => dialogReceta.close());
$('#cerrar-detalle').addEventListener('click', () => dialogDetalle.close());
$('#cerrar-detalle-pie').addEventListener('click', () => dialogDetalle.close());
$('#btn-agregar-ingrediente').addEventListener('click', abrirIngrediente);
$('#cerrar-ingrediente').addEventListener('click', () => dialogIngrediente.close());
$('#cancelar-ingrediente').addEventListener('click', () => { dialogIngrediente.close(); if (recetaActual) abrirDetalle(recetaActual); });
cargar();
