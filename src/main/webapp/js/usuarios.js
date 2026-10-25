import { iniciarModulo } from './app-shell.js';
const app = iniciarModulo('usuarios', ['ADMINISTRADOR']);
if (!app) throw new Error('Se requiere una sesión de administrador');

const $ = selector => document.querySelector(selector);
const tbody = $('#tabla-usuarios');
const dialog = $('#dialog-usuario');
const form = $('#form-usuario');
let usuarios = [];
let roles = [];
let sucursales = [];
let cargando = false;

function celda(fila, valor, clase = '') {
  const el = document.createElement('td'); el.textContent = valor == null ? '—' : String(valor);
  if (clase) el.className = clase;
  fila.append(el); return el;
}
function pintar() {
  const buscar = $('#buscar').value.trim().toLocaleLowerCase('es');
  const rol = $('#filtro-rol').value;
  const estado = $('#filtro-estado').value;
  const filtrados = usuarios.filter(u => {
    const coincide = `${u.usuario||''} ${u.nombre||''} ${u.sucursal||''}`.toLocaleLowerCase('es').includes(buscar);
    return coincide && (!rol || String(u.rol||'').toUpperCase() === rol) && (!estado || String(u.estado) === estado);
  });
  tbody.replaceChildren();
  for (const u of filtrados) {
    const tr = document.createElement('tr');
    celda(tr, u.usuario || '—', 'strong-text');
    celda(tr, u.nombre || '—');
    celda(tr, u.rol || '—');
    celda(tr, u.sucursal || 'Sin sucursal');
    const td = document.createElement('td');
    const badge = document.createElement('span'); badge.className = `status-chip status-chip--${Number(u.estado)===1?'ok':'off'}`;
    badge.textContent = Number(u.estado)===1?'Activo':'Inactivo'; td.append(badge); tr.append(td);
    tbody.append(tr);
  }
  if (!filtrados.length) {
    const tr = document.createElement('tr'); const td = document.createElement('td');
    td.colSpan = 5; td.className = 'empty-table'; td.textContent = 'No hay usuarios que coincidan con la búsqueda.';
    tr.append(td); tbody.append(tr);
  }
  $('#total-usuarios').textContent = usuarios.length;
  $('#total-activos').textContent = usuarios.filter(u => Number(u.estado)===1).length;
  $('#total-sucursal').textContent = usuarios.filter(u => String(u.rol).toUpperCase()==='SUCURSAL').length;
  $('#total-produccion').textContent = usuarios.filter(u => String(u.rol).toUpperCase()==='PRODUCCION').length;
  $('#conteo-filtrado').textContent = `${filtrados.length} de ${usuarios.length} usuarios`;
}
async function cargar() {
  if (cargando) return;
  cargando = true; $('#btn-recargar').disabled = true;
  $('#estado-carga').textContent = 'Consultando usuarios en Oracle…';
  try {
    const datos = await app.api('usuarios');
    if (!Array.isArray(datos)) throw new Error('La API no devolvió una lista de usuarios.');
    usuarios = datos; pintar(); $('#estado-carga').textContent = 'Datos actualizados.';
  } catch (err) { $('#estado-carga').textContent = err.mensaje || err.message; app.notificar(err.mensaje || err.message, 'error'); }
  finally { cargando = false; $('#btn-recargar').disabled = false; }
}
function crearOpcion(valor, texto) {
  const opcion = document.createElement('option'); opcion.value = valor; opcion.textContent = texto; return opcion;
}
async function cargarCatalogos() {
  const resultado = await Promise.all([app.api('usuarios/roles'), app.api('usuarios/sucursales')]);
  if (!Array.isArray(resultado[0]) || !Array.isArray(resultado[1])) throw new Error('No se pudieron consultar roles y sucursales.');
  roles = resultado[0]; sucursales = resultado[1];
  const selectorRol = $('#nuevo-rol');
  selectorRol.replaceChildren(crearOpcion('', 'Seleccionar rol'));
  roles.forEach(r => selectorRol.append(crearOpcion(String(r.idRol), r.nombre)));
  const selectorSucursal = $('#nueva-sucursal');
  selectorSucursal.replaceChildren(crearOpcion('', 'Seleccionar sucursal'));
  sucursales.forEach(s => selectorSucursal.append(crearOpcion(String(s.idSucursal), s.nombre)));
}
function actualizarSucursal() {
  const rol = roles.find(r => String(r.idRol) === $('#nuevo-rol').value);
  const esSucursal = String(rol?.nombre || '').trim().toUpperCase() === 'SUCURSAL';
  $('#grupo-sucursal').hidden = !esSucursal;
  $('#nueva-sucursal').required = esSucursal;
  if (!esSucursal) $('#nueva-sucursal').value = '';
}
async function abrirNuevo() {
  form.reset(); $('#error-usuario').hidden = true;
  try {
    await cargarCatalogos(); actualizarSucursal(); dialog.showModal(); $('#nuevo-usuario').focus();
  } catch (error) { app.notificar(error.mensaje || error.message, 'error'); }
}
function errorFormulario(texto) { $('#error-usuario').textContent = texto; $('#error-usuario').hidden = false; }
async function guardar(e) {
  e.preventDefault(); $('#error-usuario').hidden = true;
  const usuario = $('#nuevo-usuario').value.trim();
  const nombre = $('#nuevo-nombre').value.trim();
  const contrasena = $('#nueva-clave').value;
  const rol = roles.find(r => String(r.idRol) === $('#nuevo-rol').value);
  const esSucursal = String(rol?.nombre || '').trim().toUpperCase() === 'SUCURSAL';
  const idSucursal = esSucursal ? Number($('#nueva-sucursal').value) : null;
  if (!usuario || !nombre || !rol || contrasena.length < 12 || (esSucursal && !idSucursal)) {
    errorFormulario('Completa todos los campos. La contraseña debe tener al menos 12 caracteres y el usuario SUCURSAL requiere una sucursal.'); return;
  }
  const btn = $('#guardar-usuario'); btn.disabled = true; btn.textContent = 'Creando…';
  try {
    const respuesta = await app.api('usuarios', {metodo:'POST', datos:{ usuario, contrasena, nombre, idRol: Number(rol.idRol), idSucursal }});
    dialog.close(); app.notificar(respuesta?.mensaje || 'Usuario creado correctamente.', 'exito');
    form.reset(); await cargar();
  } catch (err) { errorFormulario(err.mensaje || err.message); }
  finally { btn.disabled = false; btn.textContent = 'Crear usuario'; }
}
$('#buscar').addEventListener('input', pintar);
$('#filtro-rol').addEventListener('change', pintar);
$('#filtro-estado').addEventListener('change', pintar);
$('#btn-recargar').addEventListener('click', cargar);
$('#btn-nuevo').addEventListener('click', abrirNuevo);
$('#nuevo-rol').addEventListener('change', actualizarSucursal);
$('#cerrar-dialogo').addEventListener('click', () => dialog.close());
$('#cancelar-dialogo').addEventListener('click', () => dialog.close());
form.addEventListener('submit', guardar);
cargar();
