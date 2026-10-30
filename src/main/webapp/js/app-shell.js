import { apiFetch, URL_DASHBOARD } from './api.js';
import { obtenerSesion, borrarSesion, irALogin, normalizarRol } from './auth.js';

export const MODULOS = [
  { id: 'productos', etiqueta: 'Productos', icono: '▣', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION','SUCURSAL'], pagina: 'productos.html', descripcion: 'Catálogo de panes e insumos.' },
  { id: 'usuarios', etiqueta: 'Usuarios', icono: '♙', grupos: 'Administración', roles: ['ADMINISTRADOR'], pagina: 'usuarios.html', descripcion: 'Cuentas, perfiles y sucursales.' },
  { id: 'recetas', etiqueta: 'Recetas', icono: '▤', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION'], pagina: 'recetas.html', descripcion: 'Ingredientes y rendimientos.' },
  { id: 'inventario', etiqueta: 'Inventario', icono: '▥', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION'], pagina: 'inventario.html', descripcion: 'Existencias, entradas y ajustes.' },
  { id: 'pedidos', etiqueta: 'Pedidos', icono: '▧', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION','SUCURSAL'], pagina: 'pedidos.html', descripcion: 'Solicitudes de las sucursales.' },
  { id: 'producciones', etiqueta: 'Producción', icono: '◈', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION'], pagina: 'producciones.html', descripcion: 'Programación y fabricación.' },
  { id: 'plan', etiqueta: 'Planificación de insumos', icono: '◫', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION'], pagina: 'planificacion.html', descripcion: 'Necesidades y faltantes.' },
  { id: 'despachos', etiqueta: 'Despachos', icono: '⇥', grupos: 'Operación', roles: ['ADMINISTRADOR','PRODUCCION','SUCURSAL'], pagina: 'despachos.html', descripcion: 'Envíos y recepción de pedidos.' },
  { id: 'rutas', etiqueta: 'Gestión de rutas', icono: '↗', grupos: 'Operación', roles: ['ADMINISTRADOR'], pagina: 'rutas.html', descripcion: 'Rutas, vendedores y vehículos de entrega.' },
  { id: 'reportes', etiqueta: 'Reportes', icono: '▦', grupos: 'Administración', roles: ['ADMINISTRADOR'], pagina: 'reportes.html', descripcion: 'Indicadores y reportes gerenciales.' }
];

export function iniciarModulo(moduloActual, rolesAutorizados) {
  const sesion = obtenerSesion();
  if (!sesion) { irALogin('expirada'); return null; }
  const rol = normalizarRol(sesion.usuario?.rol);
  if (!rolesAutorizados.includes(rol)) {
    window.location.replace(URL_DASHBOARD);
    return null;
  }
  const token = sesion.token;
  const menu = document.querySelector('#main-navigation');
  const mensaje = document.querySelector('#toast');
  let avisoTemporizador;
  function notificar(texto, tipo = 'info') {
    clearTimeout(avisoTemporizador);
    mensaje.textContent = texto;
    mensaje.className = `toast toast--${tipo}`;
    mensaje.hidden = false;
    avisoTemporizador = setTimeout(() => { mensaje.hidden = true; }, 4300);
  }
  const btnInicio = crearEnlace('⌂', 'Inicio', new URL('pages/dashboard.html', new URL('../', import.meta.url)).href, moduloActual === 'inicio');
  menu.replaceChildren(btnInicio);
  for (const grupo of ['Operación', 'Administración']) {
    const elementos = MODULOS.filter(m => m.grupos === grupo && m.roles.includes(rol));
    if (!elementos.length) continue;
    const titulo = document.createElement('div');
    titulo.className = 'sidebar__section-label sidebar__section-label--sub';
    titulo.textContent = grupo.toUpperCase();
    menu.append(titulo);
    for (const modulo of elementos) {
      if (modulo.pagina) {
        const link = crearEnlace(modulo.icono, modulo.etiqueta, new URL(`../pages/${modulo.pagina}`, import.meta.url).href, moduloActual === modulo.id);
        menu.append(link);
      } else {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'nav-item nav-item--pending';
        const icono = document.createElement('span');
        icono.className = 'nav-item__icon'; icono.textContent = modulo.icono;
        const etiqueta = document.createElement('span');
        etiqueta.className = 'nav-item__label'; etiqueta.textContent = modulo.etiqueta;
        const pendiente = document.createElement('span');
        pendiente.className = 'nav-item__soon'; pendiente.textContent = '·';
        boton.append(icono, etiqueta, pendiente);
        boton.addEventListener('click', () => { notificar(`El módulo ${modulo.etiqueta} estará disponible en una siguiente fase.`); cerrarMovil(); });
        menu.append(boton);
      }
    }
  }

  const nombre = String(sesion.usuario?.nombre || sesion.usuario?.usuario || 'SC');
  document.querySelector('#fecha-actual').textContent = new Intl.DateTimeFormat('es-GT', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  const nombreUsuario = document.querySelector('#usuario-actual');
  if (nombreUsuario) nombreUsuario.textContent = `${nombre} · ${rol === 'ADMINISTRADOR' ? 'Administrador' : rol === 'PRODUCCION' ? 'Producción' : 'Sucursal'}`;

  const sidebar = document.querySelector('#sidebar');
  const overlay = document.querySelector('#sidebar-overlay');
  const menuToggle = document.querySelector('#menu-toggle');
  function cerrarMovil() {
    sidebar.classList.remove('sidebar--open');
    document.body.classList.remove('menu-is-open');
    overlay.hidden = true;
    menuToggle.setAttribute('aria-expanded', 'false');
    menuToggle.setAttribute('aria-label', 'Abrir menú');
  }
  menuToggle.addEventListener('click', () => {
    const abierto = sidebar.classList.toggle('sidebar--open');
    document.body.classList.toggle('menu-is-open', abierto);
    overlay.hidden = !abierto;
    menuToggle.setAttribute('aria-expanded', String(abierto));
    menuToggle.setAttribute('aria-label', abierto ? 'Cerrar menú' : 'Abrir menú');
  });
  overlay.addEventListener('click', cerrarMovil);
  menu.addEventListener('click', ev => { if (ev.target.closest('a')) cerrarMovil(); });
  document.addEventListener('keydown', ev => { if (ev.key === 'Escape') cerrarMovil(); });
  window.addEventListener('resize', () => { if (window.innerWidth > 900) cerrarMovil(); });
  const logout = document.querySelector('#logout-sidebar');
  logout.addEventListener('click', async () => {
    logout.disabled = true;
    logout.textContent = 'Cerrando sesión…';
    try {
      const resultado = await apiFetch('usuarios/logout', { metodo: 'POST', token });
      if (resultado?.exito === false) throw new Error(resultado.mensaje || 'No se confirmó el cierre de sesión.');
      borrarSesion(); irALogin('logout');
    } catch (err) {
      if (err.estado === 401) { borrarSesion(); irALogin('expirada'); return; }
      notificar('No se pudo confirmar el cierre de sesión. Inténtalo nuevamente.', 'error');
      logout.disabled = false;
      logout.textContent = '↪ Cerrar sesión';
    }
  });

  async function api(ruta, opciones = {}) {
    try { return await apiFetch(ruta, { ...opciones, token }); }
    catch (error) {
      if (error.estado === 401) { borrarSesion(); irALogin('expirada'); }
      throw error;
    }
  }
  return { sesion, rol, token, api, notificar };
}

function crearEnlace(simbolo, etiqueta, url, activo) {
  const a = document.createElement('a');
  a.href = url;
  a.className = `nav-item nav-item--link${activo ? ' nav-item--active' : ''}`;
  if (activo) a.setAttribute('aria-current','page');
  const icono = document.createElement('span'); icono.className = 'nav-item__icon'; icono.textContent = simbolo;
  const txt = document.createElement('span'); txt.className = 'nav-item__label'; txt.textContent = etiqueta;
  a.append(icono, txt);
  return a;
}
