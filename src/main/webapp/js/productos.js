import { iniciarModulo } from './app-shell.js';
const app = iniciarModulo('productos', ['ADMINISTRADOR','PRODUCCION','SUCURSAL']);
if (!app) throw new Error('Se requiere una sesión autorizada');

const admin = app.rol === 'ADMINISTRADOR';
const $ = selector => document.querySelector(selector);
const tbody = $('#tabla-productos');
const dialog = $('#dialog-producto');
const form = $('#form-producto');
let productos = [];
let editando = null;
let cargando = false;

$('#btn-nuevo').hidden = !admin;
$('#col-acciones').hidden = !admin;
$('#modo-consulta').hidden = admin;

const numero = n => new Intl.NumberFormat('es-GT',{maximumFractionDigits:3}).format(Number(n)||0);
function textoCelda(fila, contenido, clase = '') {
  const celda = document.createElement('td');
  celda.textContent = contenido == null ? '—' : String(contenido);
  if (clase) celda.className = clase;
  fila.append(celda);
  return celda;
}
function etiqueta(texto, estilo) {
  const el = document.createElement('span'); el.className = `status-chip status-chip--${estilo}`;
  el.textContent = texto; return el;
}
function pintar() {
  const buscar = $('#buscar').value.trim().toLocaleLowerCase('es');
  const tipo = $('#filtro-tipo').value;
  const estado = $('#filtro-estado').value;
  const filtrados = productos.filter(p => {
    const coincideTexto = `${p.codigo || ''} ${p.nombre || ''}`.toLocaleLowerCase('es').includes(buscar);
    return coincideTexto && (!tipo || p.tipo === tipo) && (!estado || String(p.estado) === estado);
  });
  tbody.replaceChildren();
  for (const p of filtrados) {
    const fila = document.createElement('tr');
    const producto = document.createElement('td');
    const titulo = document.createElement('strong'); titulo.textContent = p.nombre || 'Sin nombre';
    const codigo = document.createElement('small'); codigo.textContent = p.codigo || 'Sin código';
    producto.append(titulo, codigo); fila.append(producto);
    textoCelda(fila, p.tipo === 'FINAL' ? 'Pan terminado' : 'Ingrediente');
    textoCelda(fila, p.unidadMedida || '—');
    textoCelda(fila, numero(p.existencia), 'num');
    textoCelda(fila, numero(p.existenciaMinima), 'num');
    const estadoCelda = document.createElement('td');
    estadoCelda.append(etiqueta(Number(p.estado) === 1 ? 'Activo' : 'Inactivo', Number(p.estado) === 1 ? 'ok' : 'off'));
    fila.append(estadoCelda);
    if (admin) {
      const acciones = document.createElement('td');
      acciones.className = 'table-actions';
      const editar = document.createElement('button');
      editar.type = 'button'; editar.className = 'table-action'; editar.textContent = 'Editar';
      editar.addEventListener('click', () => abrirEditar(p));
      const alternar = document.createElement('button');
      alternar.type = 'button'; alternar.className = `table-action ${Number(p.estado)===1?'table-action--danger':''}`;
      alternar.textContent = Number(p.estado) === 1 ? 'Desactivar' : 'Activar';
      alternar.addEventListener('click', () => cambiarEstado(p));
      acciones.append(editar, alternar); fila.append(acciones);
    }
    tbody.append(fila);
  }
  if (!filtrados.length) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = admin ? 7 : 6; td.className = 'empty-table';
    td.textContent = 'No hay productos que coincidan con la búsqueda.';
    tr.append(td); tbody.append(tr);
  }
  $('#conteo-filtrado').textContent = `${filtrados.length} de ${productos.length} productos`;
  $('#total-productos').textContent = productos.length;
  $('#total-finales').textContent = productos.filter(p => p.tipo === 'FINAL').length;
  $('#total-insumos').textContent = productos.filter(p => p.tipo === 'INSUMO').length;
  $('#total-activos').textContent = productos.filter(p => Number(p.estado) === 1).length;
}
async function cargar() {
  if (cargando) return;
  cargando = true; $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando productos';
  try {
    const resultado = await app.api('productos');
    if (!Array.isArray(resultado)) throw new Error('La API no devolvió una lista de productos.');
    productos = resultado;
    pintar(); $('#estado-carga').textContent = 'Datos actualizados';
  } catch (e) { $('#estado-carga').textContent = e.mensaje || e.message; app.notificar(e.mensaje || e.message, 'error'); }
  finally { cargando = false; $('#btn-recargar').disabled = false; }
}
function mostrarUnidadLibre() {
  const custom = $('#producto-unidad').value === 'OTRA';
  $('#grupo-unidad-personalizada').hidden = !custom;
  $('#producto-unidad-personalizada').required = custom;
}
function errorFormulario(mensaje) { $('#error-producto').textContent = mensaje; $('#error-producto').hidden = false; }
function abrirNuevo() {
  if (!admin) return;
  editando = null; form.reset(); $('#error-producto').hidden = true;
  $('#titulo-dialogo').textContent = 'Nuevo producto';
  $('#guardar-producto').textContent = 'Guardar producto';
  $('#grupo-tipo').hidden = false;
  mostrarUnidadLibre(); dialog.showModal(); $('#producto-codigo').focus();
}
function abrirEditar(p) {
  if (!admin) return;
  editando = p; form.reset(); $('#error-producto').hidden = true;
  $('#titulo-dialogo').textContent = `Editar producto #${p.idProducto}`;
  $('#guardar-producto').textContent = 'Guardar cambios';
  $('#grupo-tipo').hidden = true;
  $('#producto-codigo').value = p.codigo || '';
  $('#producto-nombre').value = p.nombre || '';
  $('#producto-minima').value = Number(p.existenciaMinima) || 0;
  const unidades = Array.from($('#producto-unidad').options).map(o => o.value);
  if (unidades.includes(p.unidadMedida)) { $('#producto-unidad').value = p.unidadMedida; }
  else { $('#producto-unidad').value = 'OTRA'; $('#producto-unidad-personalizada').value = p.unidadMedida || ''; }
  mostrarUnidadLibre(); dialog.showModal(); $('#producto-codigo').focus();
}
async function guardar(e) {
  e.preventDefault(); if (!admin) return;
  $('#error-producto').hidden = true;
  const codigo = $('#producto-codigo').value.trim().toUpperCase();
  const nombre = $('#producto-nombre').value.trim();
  const unidadMedida = ($('#producto-unidad').value === 'OTRA'
    ? $('#producto-unidad-personalizada').value : $('#producto-unidad').value).trim().toUpperCase();
  const existenciaMinima = Number($('#producto-minima').value);
  if (!codigo || !nombre || !unidadMedida || $('#producto-minima').value === '' || !Number.isFinite(existenciaMinima) || existenciaMinima < 0) {
    errorFormulario('Completa código, nombre, unidad y una existencia mínima válida.'); return;
  }
  const datos = { codigo, nombre, unidadMedida, existenciaMinima };
  if (!editando) datos.tipo = $('#producto-tipo').value;
  const boton = $('#guardar-producto'); boton.disabled = true; boton.textContent = 'Guardando…';
  try {
    const ruta = editando ? `productos/${editando.idProducto}` : 'productos';
    const resultado = await app.api(ruta, { metodo: editando ? 'PUT' : 'POST', datos });
    dialog.close(); app.notificar(resultado?.mensaje || 'Producto guardado correctamente.', 'exito'); await cargar();
  } catch (err) { errorFormulario(err.mensaje || err.message); }
  finally { boton.disabled = false; boton.textContent = editando ? 'Guardar cambios' : 'Guardar producto'; }
}

function confirmar(texto) {
  const d = $('#dialog-confirmar'); $('#mensaje-confirmar').textContent = texto;
  return new Promise(resolve => {
    const si = $('#btn-si-confirmar'); const no = $('#btn-no-confirmar');
    function fin(valor) { si.removeEventListener('click', aceptar); no.removeEventListener('click', cancelar); d.removeEventListener('cancel', cancelar); d.close(); resolve(valor); }
    function aceptar() { fin(true); }
    function cancelar(e) { if (e) e.preventDefault(); fin(false); }
    si.addEventListener('click', aceptar); no.addEventListener('click', cancelar); d.addEventListener('cancel', cancelar); d.showModal();
  });
}
async function cambiarEstado(p) {
  if (!admin) return;
  const activar = Number(p.estado) !== 1;
  const ok = await confirmar(`¿Deseas ${activar ? 'activar' : 'desactivar'} el producto «${p.nombre}»? No se eliminará de Oracle.`);
  if (!ok) return;
  try {
    const resultado = await app.api(`productos/${p.idProducto}/estado`, { metodo: 'PUT', datos: {estado: activar ? 1 : 0} });
    app.notificar(resultado?.mensaje || 'Estado actualizado.', 'exito'); await cargar();
  } catch (e) { app.notificar(e.mensaje || e.message, 'error'); }
}

$('#buscar').addEventListener('input', pintar);
$('#filtro-tipo').addEventListener('change', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-recargar').addEventListener('click', cargar);
$('#btn-nuevo').addEventListener('click', abrirNuevo);
$('#cerrar-dialogo').addEventListener('click', () => dialog.close());
$('#cancelar-dialogo').addEventListener('click', () => dialog.close());
$('#producto-unidad').addEventListener('change', mostrarUnidadLibre);
form.addEventListener('submit', guardar);
cargar();
