import { MODULOS, iniciarModulo } from './app-shell.js';

// La cabecera, permisos, navegación y logout se gestionan en un solo lugar.
const app = iniciarModulo('inicio', ['ADMINISTRADOR', 'PRODUCCION', 'SUCURSAL']);
if (!app) throw new Error('Se requiere una sesión autorizada para el Dashboard.');
const { sesion, rol } = app;
const usuario = sesion.usuario || {};
const ROL_ETIQUETA = { ADMINISTRADOR: 'Administrador', PRODUCCION: 'Producción', SUCURSAL: 'Sucursal' };
const modulosVisibles = MODULOS.filter(modulo => modulo.roles.includes(rol));

const saludo = document.querySelector('#saludo');
const descripcionRol = document.querySelector('#descripcion-rol');
const badgeRol = document.querySelector('#badge-rol');
const badgeSucursal = document.querySelector('#badge-sucursal');
const statsGrid = document.querySelector('#stats-grid');
const statsStatus = document.querySelector('#stats-status');

function nombreCorto() {
  const nombre = String(usuario.nombre || usuario.usuario || 'Colaborador').trim();
  return nombre.split(/\s+/)[0] || 'Colaborador';
}
function pintarIdentidad() {
  saludo.textContent = `¡Buen día, ${nombreCorto()}!`;
  const mensajes = {
    ADMINISTRADOR: 'Una vista general para coordinar tu panadería y sus sucursales.',
    PRODUCCION: 'Revisa la producción, los insumos y las operaciones de hoy.',
    SUCURSAL: 'Consulta tus pedidos y sigue los despachos de tu sucursal.'
  };
  descripcionRol.textContent = mensajes[rol];
  badgeRol.textContent = ROL_ETIQUETA[rol];
  if (rol === 'SUCURSAL' && usuario.sucursal) {
    badgeSucursal.textContent = String(usuario.sucursal);
    badgeSucursal.hidden = false;
  }
}

function pintarTarjetasModulos() {
  const panel = document.querySelector('#modules-grid');
  panel.replaceChildren();
  for (const modulo of modulosVisibles) {
    const boton = document.createElement('a');
    boton.className = 'module-card';
    boton.style.textDecoration = 'none';
    boton.href = new URL(`../pages/${modulo.pagina}`, import.meta.url).href;
    const simbolo = document.createElement('span');
    simbolo.className = 'module-card__icon';
    simbolo.textContent = modulo.icono;
    const texto = document.createElement('span');
    texto.className = 'module-card__content';
    const titulo = document.createElement('strong');
    titulo.textContent = modulo.etiqueta;
    const descripcion = document.createElement('span');
    descripcion.textContent = modulo.descripcion;
    texto.append(titulo, descripcion);
    const etiqueta = document.createElement('span');
    etiqueta.className = 'module-card__state';
    etiqueta.textContent = 'Abrir módulo →';
    boton.append(simbolo, texto, etiqueta);
    panel.append(boton);
  }
}

// El menú es orientativo: la autorización auténtica siempre se hace en Jersey.
function contar(lista, condicion = () => true) {
  return Array.isArray(lista) ? lista.filter(condicion).length : null;
}
function estadoEs(estado) { return (item) => String(item.estado || '').toUpperCase() === estado; }
function definirIndicadores(datos) {
  if (rol === 'ADMINISTRADOR') return [
    ['Productos registrados', contar(datos.productos), 'Catálogo total', '▣'],
    ['Pedidos registrados', contar(datos.pedidos), 'Todas las sucursales', '▧'],
    ['Producciones', contar(datos.producciones), 'Historial completo', '◈'],
    ['Despachos', contar(datos.despachos), 'Historial completo', '⇥']
  ];
  if (rol === 'PRODUCCION') return [
    ['Producciones pendientes', contar(datos.producciones, estadoEs('PENDIENTE')), 'Por preparar', '◈'],
    ['Pedidos enviados', contar(datos.pedidos, estadoEs('ENVIADO')), 'Por producir', '▧'],
    ['Productos con inventario bajo', contar(datos.inventario, (i) => String(i.estadoInventario || '').toUpperCase() === 'BAJO'), 'Según nivel mínimo', '▥'],
    ['Despachos', contar(datos.despachos), 'Historial completo', '⇥']
  ];
  return [
    ['Mis pedidos', contar(datos.pedidos), 'Solo tu sucursal', '▧'],
    ['En borrador', contar(datos.pedidos, estadoEs('BORRADOR')), 'Por enviar', '▤'],
    ['Pedidos enviados', contar(datos.pedidos, estadoEs('ENVIADO')), 'En espera', '◈'],
    ['Mis despachos', contar(datos.despachos), 'Solo tu sucursal', '⇥']
  ];
}
function pintarIndicadores(indicadores) {
  statsGrid.replaceChildren();
  for (const [titulo, valor, subtitulo, simbolo] of indicadores) {
    const card = document.createElement('article');
    card.className = 'stat-card';
    const arriba = document.createElement('div');
    arriba.className = 'stat-card__top';
    const label = document.createElement('span');
    label.textContent = titulo;
    const icono = document.createElement('span');
    icono.className = 'stat-card__icon';
    icono.textContent = simbolo;
    arriba.append(label, icono);
    const numero = document.createElement('strong');
    numero.textContent = valor === null ? '—' : String(valor);
    const descripcion = document.createElement('p');
    descripcion.textContent = valor === null ? 'No se pudo consultar' : subtitulo;
    card.append(arriba, numero, descripcion);
    statsGrid.append(card);
  }
}
let actualizando = false;
async function cargarIndicadores() {
  if (actualizando) return;
  actualizando = true;
  const actualizar = document.querySelector('#actualizar-resumen');
  actualizar.disabled = true;
  statsStatus.textContent = 'Actualizando la información…';
  const endpoints = rol === 'ADMINISTRADOR'
    ? { productos: 'productos', pedidos: 'pedidos', producciones: 'producciones', despachos: 'despachos' }
    : rol === 'PRODUCCION'
      ? { producciones: 'producciones', pedidos: 'pedidos', inventario: 'inventario', despachos: 'despachos' }
      : { pedidos: 'pedidos', despachos: 'despachos' };
  const entradas = Object.entries(endpoints);
  const resultados = await Promise.allSettled(entradas.map(([, ruta]) => app.api(ruta)));
  const datos = {};
  let fallos = 0;
  for (let i = 0; i < entradas.length; i++) {
    const [nombre] = entradas[i];
    const resultado = resultados[i];
    if (resultado.status === 'fulfilled') {
      datos[nombre] = resultado.value;
    } else {
      if (resultado.reason?.estado === 401) return;
      fallos++;
      datos[nombre] = null;
    }
  }
  pintarIndicadores(definirIndicadores(datos));
  statsStatus.textContent = fallos
    ? 'Algunos datos no están disponibles. Verifica Tomcat o actualiza para reintentar.'
    : 'Información consultada.';
  if (fallos) app.notificar('No se pudieron consultar todos los indicadores.', 'error');
  actualizar.disabled = false;
  actualizando = false;
}

document.querySelector('#actualizar-resumen').addEventListener('click', cargarIndicadores);

pintarIdentidad();
pintarTarjetasModulos();
cargarIndicadores();
