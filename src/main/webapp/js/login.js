import { apiFetch, URL_DASHBOARD } from './api.js';
import { guardarSesion, obtenerSesion } from './auth.js';

const formulario = document.querySelector('#login-form');
const usuario = document.querySelector('#usuario');
const contrasena = document.querySelector('#contrasena');
const boton = document.querySelector('#login-submit');
const textoBoton = document.querySelector('#login-submit-text');
const aviso = document.querySelector('#login-notice');
const toggle = document.querySelector('#toggle-password');


// Si ya hay una sesión, el dashboard validará el token contra la API.
if (obtenerSesion()) window.location.replace(URL_DASHBOARD);

const motivo = new URLSearchParams(window.location.search).get('motivo');
if (motivo === 'logout') mostrarAviso('La sesión se cerró correctamente.', 'exito');
if (motivo === 'expirada') mostrarAviso('Tu sesión terminó. Inicia sesión nuevamente.', 'info');

function mostrarAviso(mensaje, estilo = 'error') {
  aviso.textContent = mensaje;
  aviso.className = `notice notice--${estilo}`;
  aviso.hidden = false;
}
function limpiarAviso() { aviso.hidden = true; }

function cargando(estaCargando) {
  boton.disabled = estaCargando;
  textoBoton.textContent = estaCargando ? 'Verificando acceso…' : 'Ingresar al sistema';
  usuario.readOnly = estaCargando;
  contrasena.readOnly = estaCargando;
}

toggle.addEventListener('click', () => {
  const visible = contrasena.type === 'text';
  contrasena.type = visible ? 'password' : 'text';
  toggle.textContent = visible ? 'Mostrar' : 'Ocultar';
  toggle.setAttribute('aria-label', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
  toggle.setAttribute('aria-pressed', String(!visible));
});

formulario.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  limpiarAviso();
  const nombre = usuario.value.trim();
  const clave = contrasena.value;
  if (!nombre || !clave) {
    mostrarAviso('Ingresa tu usuario y tu contraseña.');
    (!nombre ? usuario : contrasena).focus();
    return;
  }
  cargando(true);
  try {
    const respuesta = await apiFetch('usuarios/login', {
      metodo: 'POST', datos: { usuario: nombre, contrasena: clave }
    });
    guardarSesion(respuesta);
    window.location.replace(URL_DASHBOARD);
  } catch (error) {
    mostrarAviso(error.mensaje || error.message || 'No fue posible iniciar sesión.');
    cargando(false);
  }
});
