// sessionStorage conserva la sesión al recargar, pero no al cerrar la pestaña.
// Es una solución simple para desarrollo; no protege contra XSS.
import { URL_LOGIN } from './api.js';
const CLAVE_SESION = 'psc_sesion_v1';

export function guardarSesion(respuesta) {
  if (!respuesta || respuesta.exito !== true || !respuesta.token || !respuesta.usuario) {
    throw new Error('El servidor no devolvió una sesión válida.');
  }
  const duracion = Number(respuesta.expiraEnSegundos) || 3600;
  const sesion = {
    token: respuesta.token,
    usuario: respuesta.usuario,
    expiraEn: Date.now() + duracion * 1000
  };
  sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  return sesion;
}

export function obtenerSesion() {
  try {
    const texto = sessionStorage.getItem(CLAVE_SESION);
    if (!texto) return null;
    const sesion = JSON.parse(texto);
    if (!sesion || typeof sesion.token !== 'string' || !sesion.usuario) {
      borrarSesion(); return null;
    }
    if (Date.now() >= Number(sesion.expiraEn || 0)) {
      borrarSesion(); return null;
    }
    return sesion;
  } catch (_) {
    borrarSesion(); return null;
  }
}

export function borrarSesion() {
  sessionStorage.removeItem(CLAVE_SESION);
}

export function irALogin(motivo = '') {
  const url = new URL(URL_LOGIN);
  if (motivo) url.searchParams.set('motivo', motivo);
  window.location.replace(url.href);
}

export function normalizarRol(rol) {
  return String(rol || '').trim().toUpperCase();
}
