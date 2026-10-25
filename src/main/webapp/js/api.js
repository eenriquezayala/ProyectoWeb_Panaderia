// La ruta se obtiene desde este archivo: funciona aunque el contexto WAR cambie.
// Ejemplo: /Proyecto_PanaderiaSanCarlos/webapi/
const raizAplicacion = new URL('../', import.meta.url);
export const URL_LOGIN = new URL('index.html', raizAplicacion).href;
export const URL_DASHBOARD = new URL('pages/dashboard.html', raizAplicacion).href;
const BASE_API = new URL('webapi/', raizAplicacion);

export class ApiError extends Error {
  constructor(mensaje, estado = 0) {
    super(mensaje);
    this.name = 'ApiError';
    this.estado = estado;
  }
}

export async function apiFetch(ruta, { metodo = 'GET', token = null, datos } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (datos !== undefined) headers['Content-Type'] = 'application/json';

  const controlador = new AbortController();
  const esperaMaxima = setTimeout(() => controlador.abort(), 15000);
  let respuesta;
  try {
    respuesta = await fetch(new URL(ruta.replace(/^\/+/, ''), BASE_API), {
      method: metodo,
      headers,
      body: datos === undefined ? undefined : JSON.stringify(datos),
      cache: 'no-store',
      signal: controlador.signal
    });
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new ApiError('El servidor tardó demasiado en responder. Inténtalo de nuevo.');
    }
    throw new ApiError('No fue posible conectar con el servidor. Verifica que Tomcat esté iniciado.');
  } finally {
    clearTimeout(esperaMaxima);
  }

  const tipo = respuesta.headers.get('content-type') || '';
  let contenido = null;
  if (tipo.includes('application/json') || tipo.includes('+json')) {
    try { contenido = await respuesta.json(); } catch (_) {
      if (respuesta.ok && respuesta.status !== 204) {
        throw new ApiError('El servidor respondió con JSON inválido. Intenta de nuevo.');
      }
    }
  } else if (respuesta.status === 204) {
    contenido = null;
  } else {
    const texto = await respuesta.text();
    contenido = texto || null;
  }
  if (!respuesta.ok) {
    const mensaje = contenido && typeof contenido === 'object' && typeof contenido.mensaje === 'string'
      ? contenido.mensaje : `Error al realizar la operación (HTTP ${respuesta.status}).`;
    throw new ApiError(mensaje, respuesta.status);
  }
  return contenido;
}
