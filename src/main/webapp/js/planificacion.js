import { iniciarModulo } from './app-shell.js';

const app = iniciarModulo('plan', ['ADMINISTRADOR', 'PRODUCCION']);
if (!app) throw new Error('No hay una sesión autorizada para Planificación.');

const $ = selector => document.querySelector(selector);
const formato = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 });
const dialogConfirmar = $('#dialog-confirmar');
let producciones = [];
let planActual = null;
let consultando = false;
let guardando = false;
const idInicial = Number(new URLSearchParams(window.location.search).get('id'));

function numero(valor) { return formato.format(Number(valor) || 0); }
function idProd(p) { return Number(p?.idProduccion || 0); }
function estadoProd(p) { return String(p?.estado || '').toUpperCase(); }
function fechaCorta(valor) {
  if (!valor) return '—';
  const m = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : String(valor);
}
function celda(tr, valor, clase='') {
  const td = document.createElement('td');
  td.textContent = valor === null || valor === undefined ? '—' : String(valor);
  if (clase) td.className = clase;
  tr.append(td); return td;
}
function limpiarPlan() {
  planActual = null;
  $('#resultado-plan').hidden = true;
  $('#btn-iniciar').disabled = true;
}
function pintarPlan(plan) {
  planActual = plan;
  const insumos = Array.isArray(plan.insumos) ? plan.insumos : [];
  const faltantes = insumos.filter(i => Number(i.cantidadFaltante) > 0 || String(i.estadoInsumo || '').toUpperCase() !== 'SUFICIENTE').length;
  const sinReceta = Number(plan.productosSinRecetaCompleta) || 0;
  $('#stat-id').textContent = `#${plan.idProduccion}`;
  $('#stat-insumos').textContent = numero(insumos.length);
  $('#stat-faltantes').textContent = numero(faltantes);
  $('#stat-sin-receta').textContent = numero(sinReceta);
  const listo = plan.puedeIniciar === true && faltantes === 0 && sinReceta === 0 && insumos.length > 0;
  const estado = $('#estado-plan');
  estado.className = `status-chip status-chip--${listo ? 'ok' : 'off'}`;
  estado.textContent = listo ? 'INSUMOS SUFICIENTES' : 'REQUIERE ATENCIÓN';
  const mensaje = $('#mensaje-plan');
  mensaje.className = `plan-banner plan-banner--${listo ? 'ok' : 'warn'}`;
  mensaje.textContent = plan.mensaje || (listo ? 'La producción tiene los insumos necesarios.' : 'No se puede iniciar la producción todavía.');
  $('#btn-iniciar').disabled = !listo;
  const tabla = $('#tabla-insumos'); tabla.replaceChildren();
  for (const item of insumos) {
    const tr = document.createElement('tr');
    const nombre = celda(tr, item.nombre || item.codigo || 'Insumo sin nombre', 'strong-text');
    const codigo = document.createElement('small'); codigo.textContent = item.codigo || `#${item.idInsumo || '—'}`; nombre.append(codigo);
    celda(tr, item.unidadMedida || '—');
    celda(tr, numero(item.cantidadNecesaria), 'num');
    celda(tr, numero(item.existenciaActual), 'num');
    const faltante = celda(tr, numero(item.cantidadFaltante), 'num');
    if (Number(item.cantidadFaltante) > 0) faltante.classList.add('plan-faltante');
    const resultado = document.createElement('td');
    const texto = String(item.estadoInsumo || '').toUpperCase();
    const chip = document.createElement('span'); chip.className = `status-chip status-chip--${texto === 'SUFICIENTE' ? 'ok' : 'off'}`;
    chip.textContent = texto === 'SUFICIENTE' ? 'SUFICIENTE' : 'FALTANTE'; resultado.append(chip); tr.append(resultado);
    tabla.append(tr);
  }
  if (!insumos.length) {
    const tr = document.createElement('tr');
    const td = celda(tr, sinReceta ? 'Hay panes sin receta completa. Agrega los ingredientes de las recetas antes de consultar los faltantes.' : 'Esta producción no tiene insumos calculados.', 'empty-table');
    td.colSpan = 6; tabla.append(tr);
  }
  $('#resultado-plan').hidden = false;
}
async function cargarOpciones(idPreferido = null) {
  $('#btn-recargar').disabled = true;
  $('#estado-seleccion').textContent = 'Consultando producciones pendientes…';
  try {
    const datos = await app.api('producciones');
    if (!Array.isArray(datos)) throw new Error('La API no devolvió un listado válido.');
    producciones = datos.filter(p => estadoProd(p) === 'PENDIENTE');
    const select = $('#selector-produccion');
    const actual = idPreferido || Number(select.value) || idInicial;
    select.replaceChildren(new Option('Selecciona una producción…', ''));
    for (const p of producciones) select.add(new Option(`Producción #${idProd(p)} · ${fechaCorta(p.fechaProduccion)}`, String(idProd(p))));
    const elegida = producciones.find(p => idProd(p) === actual);
    if (elegida) select.value = String(actual);
    $('#estado-seleccion').textContent = producciones.length
      ? `${producciones.length} producción(es) pendientes disponibles.`
      : 'No hay producciones PENDIENTES. Genera una desde el módulo Producción después de enviar pedidos.';
    limpiarPlan();
    if (elegida) await consultar();
  } catch (e) {
    $('#estado-seleccion').textContent = e.message || 'No se pudieron cargar las producciones.';
    app.notificar(e.message || 'Error de conexión', 'error');
    limpiarPlan();
  } finally { $('#btn-recargar').disabled = false; }
}
async function consultar() {
  if (consultando) return;
  const id = Number($('#selector-produccion').value);
  if (!Number.isSafeInteger(id) || id <= 0) {
    limpiarPlan(); app.notificar('Selecciona primero una producción pendiente.', 'error'); return;
  }
  consultando = true;
  $('#btn-consultar').disabled = true;
  $('#estado-seleccion').textContent = `Calculando insumos de la producción #${id}…`;
  limpiarPlan();
  try {
    const plan = await app.api(`producciones/${id}/plan-insumos`);
    // Ignorar resultados antiguos cuando el usuario seleccionó otra producción.
    if (Number($('#selector-produccion').value) !== id) return;
    if (!plan || Number(plan.idProduccion) !== id) throw new Error('La API devolvió una planificación inesperada.');
    pintarPlan(plan);
    $('#estado-seleccion').textContent = `Plan de producción #${id} actualizado.`;
  } catch (e) {
    $('#estado-seleccion').textContent = e.message || 'Error al consultar el plan.';
    app.notificar(e.message || 'Error al consultar planificación.', 'error');
  } finally { consultando = false; $('#btn-consultar').disabled = false; }
}
function abrirConfirmacion() {
  if (!planActual?.puedeIniciar || guardando) return;
  $('#error-confirmar').hidden = true;
  $('#titulo-confirmar').textContent = `Iniciar producción #${planActual.idProduccion}`;
  $('#mensaje-confirmar').textContent = 'El servidor volverá a verificar existencias y recetas. Si todo sigue disponible, descontará ingredientes e iniciará la producción. ¿Continuar?';
  dialogConfirmar.showModal();
}
async function confirmar() {
  if (!planActual?.puedeIniciar || guardando) return;
  const id = Number(planActual.idProduccion);
  guardando = true;
  $('#aceptar-confirmar').disabled = true;
  $('#aceptar-confirmar').textContent = 'Iniciando…';
  $('#error-confirmar').hidden = true;
  try {
    const respuesta = await app.api(`producciones/${id}/iniciar`, { metodo:'PUT' }); // Body omitido; responsable JWT
    if (!respuesta?.exito) throw new Error(respuesta?.mensaje || 'La operación no fue confirmada por Oracle.');
    dialogConfirmar.close();
    app.notificar(respuesta.mensaje || `Producción #${id} iniciada correctamente.`, 'exito');
    await cargarOpciones(); // no debe aparecer en pendientes una vez iniciada
  } catch (e) {
    $('#error-confirmar').textContent = e.message || 'No fue posible iniciar la producción.';
    $('#error-confirmar').hidden = false;
  } finally {
    guardando = false;
    $('#aceptar-confirmar').disabled = false;
    $('#aceptar-confirmar').textContent = 'Sí, iniciar';
  }
}
$('#selector-produccion').addEventListener('change', limpiarPlan);
$('#btn-consultar').addEventListener('click', consultar);
$('#btn-recargar').addEventListener('click', () => cargarOpciones());
$('#btn-iniciar').addEventListener('click', abrirConfirmacion);
$('#cancelar-confirmar').addEventListener('click', () => dialogConfirmar.close());
$('#aceptar-confirmar').addEventListener('click', confirmar);
cargarOpciones();
