import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('reportes', ['ADMINISTRADOR']);
if (!app) throw new Error('La sesión no tiene permiso para Reportes Gerenciales.');

const $ = selector => document.querySelector(selector);
const numeroFormateado = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 });
const reportes = {
  pedidos: {
    ruta: 'pedidos-por-sucursal', titulo: 'Pedidos por sucursal',
    descripcion: 'Total de pedidos válidos y unidades de pan solicitadas por cada sucursal.',
    grafico: 'Panes solicitados por sucursal',
    columnas: [['Sucursal','sucursal','texto'],['Pedidos','totalPedidos','numero'],['Panes solicitados','totalPanes','numero']],
    categoria: item => item.sucursal, series: [['Panes solicitados', 'totalPanes']]
  },
  panes: {
    ruta: 'panes-mas-solicitados', titulo: 'Panes más solicitados',
    descripcion: 'Ranking de productos finales solicitados en pedidos válidos; no equivale a ventas monetarias.',
    grafico: 'Ranking por unidades solicitadas',
    columnas: [['Código','codigo','texto'],['Pan','producto','texto'],['Unidades solicitadas','totalSolicitado','numero'],['Pedidos','totalPedidos','numero']],
    categoria: item => item.producto, series: [['Unidades solicitadas', 'totalSolicitado']]
  },
  produccion: {
    ruta: 'produccion-diaria', titulo: 'Producción diaria',
    descripcion: 'Compara panes programados y fabricados según la fecha de producción.',
    grafico: 'Panes programados frente a panes producidos',
    columnas: [['Fecha','fecha','fecha'],['Producciones','totalProducciones','numero'],['Finalizadas','produccionesFinalizadas','numero'],['Panes programados','panesProgramados','numero'],['Panes producidos','panesProducidos','numero']],
    categoria: item => fechaVisible(item.fecha), series: [['Programados', 'panesProgramados'], ['Producidos','panesProducidos']]
  },
  despachos: {
    ruta: 'despachos-por-sucursal', titulo: 'Despachos por sucursal',
    descripcion: 'Despachos registrados, recepciones confirmadas y unidades enviadas.',
    grafico: 'Despachos realizados y recibidos',
    columnas: [['Sucursal','sucursal','texto'],['Despachos','totalDespachos','numero'],['Recibidos','despachosRecibidos','numero'],['Panes despachados','panesDespachados','numero']],
    categoria: item => item.sucursal, series: [['Despachados', 'totalDespachos'], ['Recibidos','despachosRecibidos']]
  },
  inventario: {
    ruta: 'inventario-bajo', titulo: 'Inventario bajo',
    descripcion: 'Productos activos que alcanzaron o están por debajo de su existencia mínima configurada.',
    grafico: 'Cobertura del mínimo de cada producto',
    columnas: [['Código','codigo','texto'],['Producto','producto','texto'],['Tipo','tipo','texto'],['Unidad','unidadMedida','texto'],['Existencia','existenciaActual','numero'],['Mínimo','existenciaMinima','numero'],['Faltante','cantidadFaltante','numero']],
    categoria: item => item.producto,
    series: [['Cobertura del mínimo', 'porcentajeMinimo']]
  }
};
const claves = Object.keys(reportes);
const tabs = [...document.querySelectorAll('[data-reporte]')];
let actual = 'pedidos';
let datos = {};
let errores = {};
let rangoAplicado = null;
let secuencia = 0;
let consultando = false;

function num(valor) {
  const n = Number(valor);
  return Number.isFinite(n) ? n : 0;
}
function numTxt(valor) { return numeroFormateado.format(num(valor)); }
function fechaVisible(valor) {
  const match = String(valor || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : (valor || '—');
}
function fechaISO(fecha) {
  const año = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${año}-${mes}-${dia}`;
}
function mesActual() {
  const hoy = new Date();
  $('#fecha-desde').value = fechaISO(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
  $('#fecha-hasta').value = fechaISO(new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0));
}
function mostrarErrorFechas(texto) { $('#fechas-error').textContent = texto; $('#fechas-error').hidden = false; }
function validarFechas() {
  const desde = $('#fecha-desde').value;
  const hasta = $('#fecha-hasta').value;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(desde) || !/^\d{4}-\d{2}-\d{2}$/.test(hasta)) {
    mostrarErrorFechas('Selecciona las dos fechas para consultar los reportes.'); return null;
  }
  if (desde > hasta) { mostrarErrorFechas('La fecha Desde no puede ser posterior a Hasta.'); return null; }
  $('#fechas-error').hidden = true;
  return { desde, hasta };
}
function total(filas, clave) {
  return Array.isArray(filas) ? filas.reduce((suma, item) => suma + num(item[clave]), 0) : null;
}
function valorKpi(selector, n) { $(selector).textContent = n === null ? '—' : numTxt(n); }
function pintarKPIs() {
  valorKpi('#kpi-pedidos', total(datos.pedidos, 'totalPedidos'));
  valorKpi('#kpi-solicitados', total(datos.pedidos, 'totalPanes'));
  valorKpi('#kpi-producidos', total(datos.produccion, 'panesProducidos'));
  valorKpi('#kpi-despachos', total(datos.despachos, 'totalDespachos'));
  valorKpi('#kpi-inventario', Array.isArray(datos.inventario) ? datos.inventario.length : null);
}
function textoCelda(valor, tipo) {
  if (tipo === 'numero') return numTxt(valor);
  if (tipo === 'fecha') return fechaVisible(valor);
  return valor == null || valor === '' ? '—' : String(valor);
}
function pintarTabla(config, filas) {
  const head = $('#tabla-encabezado');
  const cuerpo = $('#tabla-reporte');
  head.replaceChildren(); cuerpo.replaceChildren();
  for (const [texto,,tipo] of config.columnas) {
    const th = document.createElement('th'); th.textContent = texto;
    if (tipo === 'numero') th.className = 'num';
    head.append(th);
  }
  if (!filas.length) {
    const tr = document.createElement('tr'); const td = document.createElement('td');
    td.colSpan = config.columnas.length; td.className = 'empty-table';
    td.textContent = 'No hay información registrada para esta consulta.';
    tr.append(td); cuerpo.append(tr);
  } else {
    for (const item of filas) {
      const tr = document.createElement('tr');
      for (const [,campo,tipo] of config.columnas) {
        const td = document.createElement('td');
        td.textContent = textoCelda(item[campo], tipo);
        if (tipo === 'numero') td.className = 'num';
        tr.append(td);
      }
      cuerpo.append(tr);
    }
  }
  $('#reporte-filas').textContent = `${filas.length} ${filas.length === 1 ? 'registro' : 'registros'}`;
  $('#pie-reporte').textContent = `${filas.length} filas obtenidas · ${actual === 'inventario' ? 'Estado actual' : 'Período ' + fechaVisible(rangoAplicado.desde) + ' al ' + fechaVisible(rangoAplicado.hasta)}`;
}
function crearBarra(fila, etiqueta, campo, maximo, indiceSerie, esInventario) {
  const valor = esInventario ? Math.min(100, Math.max(0, 100 * num(fila.existenciaActual) / Math.max(0.001, num(fila.existenciaMinima)))) : Math.max(0, num(fila[campo]));
  const porcentaje = esInventario ? valor : (maximo > 0 ? (valor / maximo) * 100 : 0);
  const linea = document.createElement('div'); linea.className = 'report-chart__bar-line';
  const nombre = document.createElement('span'); nombre.className = 'report-chart__bar-name'; nombre.textContent = etiqueta;
  const pista = document.createElement('div'); pista.className = 'report-chart__track';
  const barra = document.createElement('div');
  barra.className = `report-chart__fill ${indiceSerie === 1 ? 'report-chart__fill--light' : ''} ${esInventario && valor < 100 ? 'report-chart__fill--warn' : ''}`;
  barra.style.width = `${Math.min(100, Math.max(0, porcentaje))}%`;
  pista.append(barra);
  const cantidad = document.createElement('span'); cantidad.className = 'report-chart__bar-value';
  cantidad.textContent = esInventario ? `${numTxt(valor)} %` : numTxt(valor);
  linea.append(nombre, pista, cantidad);
  return linea;
}
function pintarGrafico(config, filas) {
  const contenedor = $('#grafico-contenedor');
  const grafico = $('#grafico-barras');
  const leyenda = $('#grafico-leyenda');
  grafico.replaceChildren(); leyenda.replaceChildren();
  const comparables = filas.filter(item => config.series.some(([, campo]) => num(item[campo]) > 0) || actual === 'inventario');
  const mostradas = comparables.slice(0, 12);
  if (!mostradas.length) { contenedor.hidden = true; return; }
  contenedor.hidden = false;
  $('#grafico-titulo').textContent = config.grafico;
  $('#grafico-nota').textContent = comparables.length > 12
    ? `Se muestran 12 de ${comparables.length} filas con valores; la tabla contiene todas.`
    : 'Visualización de los registros con cantidades disponibles.';
  const esInventario = actual === 'inventario';
  const maximo = esInventario ? 100 : Math.max(1, ...mostradas.flatMap(item => config.series.map(([,campo]) => num(item[campo]))));
  for (const item of mostradas) {
    const grupo = document.createElement('div'); grupo.className = 'report-chart__group';
    const nombre = document.createElement('strong'); nombre.className = 'report-chart__group-label'; nombre.textContent = config.categoria(item) || '—';
    grupo.append(nombre);
    config.series.forEach(([etiqueta,campo],indice) => grupo.append(crearBarra(item, etiqueta, campo, maximo, indice, esInventario)));
    grafico.append(grupo);
  }
  if (!esInventario && config.series.length > 1) {
    for (const [indice, [etiqueta]] of config.series.entries()) {
      const item = document.createElement('span'); item.className = 'report-legend__item';
      const marca = document.createElement('i'); marca.className = `report-legend__mark ${indice ? 'report-legend__mark--light' : ''}`;
      item.append(marca, document.createTextNode(etiqueta)); leyenda.append(item);
    }
  } else if (esInventario) {
    leyenda.textContent = 'Porcentaje de existencia respecto al mínimo de cada producto. Los valores al 100 % están justo en el mínimo.';
  }
}
function pintarError(error) {
  $('#grafico-contenedor').hidden = true;
  $('#tabla-encabezado').replaceChildren();
  $('#tabla-reporte').replaceChildren();
  $('#estado-reportes').textContent = `No se pudo cargar el reporte: ${error}`;
  $('#estado-reportes').classList.add('report-error');
  $('#reporte-filas').textContent = 'Sin datos';
  $('#pie-reporte').textContent = 'Vuelve a intentarlo con el botón Actualizar.';
}
function pintarReporte() {
  const config = reportes[actual];
  $('#reporte-titulo').textContent = config.titulo;
  $('#reporte-descripcion').textContent = config.descripcion;
  $('#reporte-alcance').textContent = actual === 'inventario'
    ? 'Estado actual'
    : (rangoAplicado ? `${fechaVisible(rangoAplicado.desde)} — ${fechaVisible(rangoAplicado.hasta)}` : 'Sin período');
  $('#grafico-contenedor').hidden = true;
  if (consultando) {
    $('#estado-reportes').textContent = 'Consultando Oracle…';
    $('#estado-reportes').classList.remove('report-error');
    return;
  }
  if (errores[actual]) { pintarError(errores[actual]); return; }
  if (!Array.isArray(datos[actual])) { pintarError('No hay respuesta del servidor.'); return; }
  const filas = datos[actual];
  $('#estado-reportes').classList.remove('report-error');
  $('#estado-reportes').textContent = filas.length ? 'Información actualizada.' : 'La consulta fue correcta, pero no hay registros para mostrar.';
  pintarGrafico(config, filas);
  pintarTabla(config, filas);
}
function seleccionarReporte(clave, enfocar = false) {
  if (!reportes[clave]) return;
  actual = clave;
  for (const tab of tabs) {
    const seleccionado = tab.dataset.reporte === clave;
    tab.setAttribute('aria-selected', String(seleccionado));
    tab.tabIndex = seleccionado ? 0 : -1;
    if (seleccionado && enfocar) tab.focus();
  }
  $('#report-contenido').setAttribute('aria-labelledby', `tab-${clave}`);
  pintarReporte();
}
async function cargarTodo() {
  const fechas = validarFechas();
  if (!fechas) return;
  const seq = ++secuencia;
  consultando = true;
  rangoAplicado = fechas;
  datos = {}; errores = {};
  pintarKPIs(); pintarReporte();
  $('#btn-aplicar').disabled = true;
  $('#btn-actualizar').disabled = true;
  const entradas = claves.map(clave => [clave, reportes[clave]]);
  const resultados = await Promise.allSettled(entradas.map(([,config]) => {
    const qs = config === reportes.inventario ? '' : `?desde=${encodeURIComponent(fechas.desde)}&hasta=${encodeURIComponent(fechas.hasta)}`;
    return app.api(`reportes/${config.ruta}${qs}`);
  }));
  if (seq !== secuencia) return;
  let fallos = 0;
  for (let i = 0; i < entradas.length; i++) {
    const clave = entradas[i][0]; const resultado = resultados[i];
    if (resultado.status === 'fulfilled' && Array.isArray(resultado.value)) datos[clave] = resultado.value;
    else {
      fallos++;
      errores[clave] = resultado.status === 'rejected' ? resultado.reason?.message || 'Error desconocido.' : 'Formato de respuesta inesperado.';
    }
  }
  consultando = false;
  $('#btn-aplicar').disabled = false;
  $('#btn-actualizar').disabled = false;
  pintarKPIs(); pintarReporte();
  if (fallos) app.notificar(`No se pudieron consultar ${fallos} de los 5 reportes. Revisa el servidor.`, 'error');
}

for (const tab of tabs) {
  tab.addEventListener('click', () => seleccionarReporte(tab.dataset.reporte));
  tab.addEventListener('keydown', evento => {
    if (!['ArrowLeft','ArrowRight','Home','End'].includes(evento.key)) return;
    evento.preventDefault();
    const indice = claves.indexOf(tab.dataset.reporte);
    const siguiente = evento.key === 'Home' ? 0 : evento.key === 'End' ? claves.length - 1
      : (indice + (evento.key === 'ArrowRight' ? 1 : -1) + claves.length) % claves.length;
    seleccionarReporte(claves[siguiente], true);
  });
}
$('#form-fechas').addEventListener('submit', evento => { evento.preventDefault(); if (!consultando) cargarTodo(); });
$('#btn-actualizar').addEventListener('click', () => { if (!consultando) cargarTodo(); });
$('#btn-mes').addEventListener('click', () => { mesActual(); if (!consultando) cargarTodo(); });
mesActual();
seleccionarReporte('pedidos');
cargarTodo();
