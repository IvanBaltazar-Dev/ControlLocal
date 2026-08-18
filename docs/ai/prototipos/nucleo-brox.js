/* ====================================================================
   NUCLEO BROX — FUENTE CANONICA DE LOS PROTOTIPOS
   --------------------------------------------------------------------
   Un hecho puede aparecer en cinco componentes, pero existe UNA SOLA VEZ
   aqui. Inicio (D-E2-1) e Indicadores (D-E2-2) no son dos universos: son
   dos proyecciones de este estado.

   REGLAS QUE ESTE ARCHIVO HACE CUMPLIR

   1 · NINGUNA DURACION SE ESCRIBE. Se guardan fechas y se derivan los dias
       con `diasDesde` / `diasHasta` contra `FECHA_REF`. Es lo que hacia que
       la misma espera saliera 11 en una caja y 12 en la de al lado.

   2 · EL RITMO SE CALCULA UNA VEZ, en `ritmoDe`. Las dos pantallas reciben
       el mismo objeto: actual, metaPeriodo, metaEsperadaAHoy,
       proyeccionCierre, porcentajeProyectado, porcentajeMeta, faltante y
       estadoRitmo. Ninguna pantalla reclasifica.

   3 · EL VOCABULARIO DE RITMO NO SE MEZCLA CON EL DE SEVERIDAD.
         ritmo      EN_RITMO · ATENCION · FUERA_DE_RITMO · SIN_BASE
         severidad  ALTA · MEDIA          (de un asunto de la cola)
         naturaleza RIESGO · OPORTUNIDAD  (si el asunto es problema o ocasion)
       `alto`/`medio`/`bueno` ya no significan ritmo en ninguna parte.

   4 · LAS CADENAS SUMAN. La meta del equipo es la suma de las metas de sus
       agentes (D-E2-2 §5); el KPI del equipo, la suma de los actuales. El
       embudo se deriva de los mismos contadores por agente, asi que
       `visitas >= solicitudes >= aprobadas >= contratos` se cumple por
       construccion y no por una regla de presentacion.

   5 · LA EVALUACION DE SOLICITUD ES UNA ETAPA REAL. `solicitudes
       ingresadas` y `solicitudes aprobadas` son dos numeros distintos: la
       perdida entre ambos es el trabajo del broker y no se esconde.

   6 · CADA ASUNTO TIENE IDENTIDAD (`asuntoId` + `entidadTipo` + `entidadId`).
       Una direccion no es un identificador: un inmueble participa en varios
       procesos a la vez, y por eso «sale de tu foco» nombra un asunto, no
       una calle.

   Si cambias una fecha, una visita, una captacion, una solicitud, un
   contrato o una meta AQUI, las dos pantallas cambian solas.
   ==================================================================== */

(function (raiz) {
  "use strict";

  /* ------------------------------------------------------------------
     0 · TIEMPO
     El "hoy" del prototipo. Lunes 10 de agosto de 2026, 8:30.
     ------------------------------------------------------------------ */

  var FECHA_REF = "2026-08-10T08:30";

  var MES_CORTO = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  var DIA_SEMANA = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];

  /* Se construye en UTC a proposito: sin zona horaria, `diasDesde` no
     depende de donde se abra el prototipo. */
  function aFecha(f) {
    if (f instanceof Date) return f;
    var m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?$/.exec(String(f));
    if (!m) throw new Error("fecha no reconocida: " + f);
    return new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0)));
  }

  var DIA_MS = 86400000;

  /* Dias de calendario, no fracciones: entre el 3 y el 10 de agosto hay 7
     dias aunque las horas no coincidan. */
  function soloDia(f) {
    var d = aFecha(f);
    return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  }
  function diasEntre(desde, hasta) {
    return Math.round((soloDia(hasta) - soloDia(desde)) / DIA_MS);
  }
  function diasDesde(fecha, ref) { return diasEntre(fecha, ref || FECHA_REF); }
  function diasHasta(fecha, ref) { return diasEntre(ref || FECHA_REF, fecha); }

  function sumarDias(fecha, n) {
    return new Date(soloDia(fecha) + n * DIA_MS);
  }

  /* "6 ago" */
  function fdia(f) {
    var d = aFecha(f);
    return d.getUTCDate() + " " + MES_CORTO[d.getUTCMonth()];
  }
  /* "6 de agosto" */
  function flargo(f) {
    var d = aFecha(f);
    var nombre = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
      "agosto", "septiembre", "octubre", "noviembre", "diciembre"][d.getUTCMonth()];
    return d.getUTCDate() + " de " + nombre;
  }
  function fhora(f) {
    var d = aFecha(f);
    return ("0" + d.getUTCHours()).slice(-2) + ":" + ("0" + d.getUTCMinutes()).slice(-2);
  }
  /* Como se nombra un dia cercano: hoy, mañana, o su fecha. */
  function fcuando(f, ref) {
    var n = diasHasta(f, ref);
    if (n === 0) return "Hoy";
    if (n === 1) return "Mañana";
    if (n === -1) return "Ayer";
    return fdia(f);
  }
  /* Hace cuanto, dicho como se dice en una oficina. */
  function fhace(f, ref) {
    var d = aFecha(f), r = aFecha(ref || FECHA_REF);
    var n = diasEntre(f, r);
    if (n === 0) {
      var min = Math.max(1, Math.round((r - d) / 60000));
      if (min < 60) return "hace " + min + " min";
      return "hace " + Math.round(min / 60) + " h";
    }
    if (n === 1) return "ayer";
    return "hace " + n + " días";
  }
  function fcabecera(ref) {
    var d = aFecha(ref || FECHA_REF);
    var s = DIA_SEMANA[new Date(soloDia(d)).getUTCDay()];
    return s.charAt(0).toUpperCase() + s.slice(1) + " " + d.getUTCDate() + " " +
      ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
        "septiembre", "octubre", "noviembre", "diciembre"][d.getUTCMonth()] + " · " + fhora(d);
  }
  function plural(n, uno, varios) { return n + " " + (n === 1 ? uno : varios); }
  function dinero(n) { return "US$ " + n.toLocaleString("en-US"); }
  function pc(a, b) { return b ? Math.round(a / b * 100) : 0; }

  /* ------------------------------------------------------------------
     1 · POLITICA — los umbrales, en un solo sitio (E1)
     ------------------------------------------------------------------ */

  var POLITICA = {
    recontactoDias: 7,          /* plazo para volver a contactar */
    reporteAlPropietarioDias: 10,
    plazoCobroComisionDias: 15,
    tiempoPublicadoUmbralDias: 90,
    rentaSinAjustarDias: 60,
    /* Ritmo */
    llega: 1.0,                 /* proyeccion/meta a partir de la cual es EN_RITMO */
    cerca: 0.85,                /* por debajo de esto, FUERA_DE_RITMO */
    arranquePc: 0.15,           /* mientras el periodo lleve menos, el tope es ATENCION */
    volumenMinimo: 3,           /* por debajo no hay cadencia diaria que medir */
    muestraMinima: 5            /* por debajo no hay conversion que concluir */
  };

  var RITMO = { EN_RITMO: "EN_RITMO", ATENCION: "ATENCION", FUERA_DE_RITMO: "FUERA_DE_RITMO", SIN_BASE: "SIN_BASE" };
  var VOZ_RITMO = {
    EN_RITMO: "En ritmo", ATENCION: "Atención",
    FUERA_DE_RITMO: "Fuera de ritmo", SIN_BASE: "Sin base suficiente"
  };

  /* ------------------------------------------------------------------
     2 · PERSONAS
     ------------------------------------------------------------------ */

  var PERSONAS = {
    "PER-1": { id: "PER-1", nombre: "Valentina Mora", ini: "VM", papel: "AGENTE", rotulo: "Agente" },
    "PER-2": { id: "PER-2", nombre: "Rodrigo Salas", ini: "RS", papel: "BROKER", rotulo: "Broker supervisor" },
    "PER-3": { id: "PER-3", nombre: "Diego Rojas", ini: "DR", papel: "AGENTE", rotulo: "Agente" },
    "PER-4": { id: "PER-4", nombre: "Andrea Ruiz", ini: "AR", papel: "AGENTE", rotulo: "Agente" },
    "PER-5": { id: "PER-5", nombre: "Carlos León", ini: "CL", papel: "AGENTE", rotulo: "Agente" },
    "PER-6": { id: "PER-6", nombre: "Luis Torres", ini: "LT", papel: "AGENTE", rotulo: "Agente" },
    "PER-7": { id: "PER-7", nombre: "Paula Vega", ini: "PV", papel: "AGENTE", rotulo: "Agente" },
    "PER-8": { id: "PER-8", nombre: "Iván Reyes", ini: "IR", papel: "AGENTE", rotulo: "Agente" },
    "PER-9": { id: "PER-9", nombre: "Sofía Lara", ini: "SL", papel: "AGENTE", rotulo: "Agente" },

    /* Propietarios */
    "PRP-1": { id: "PRP-1", nombre: "Sr. Aliaga", papel: "PROPIETARIO" },
    "PRP-2": { id: "PRP-2", nombre: "Sr. Ramírez", papel: "PROPIETARIO" },
    "PRP-3": { id: "PRP-3", nombre: "Sra. Peña", papel: "PROPIETARIO" },
    "PRP-4": { id: "PRP-4", nombre: "Inversiones Lume", papel: "PROPIETARIO" },
    "PRP-5": { id: "PRP-5", nombre: "Sr. Quiroz", papel: "PROPIETARIO" },
    "PRP-6": { id: "PRP-6", nombre: "Sr. Bermúdez", papel: "PROPIETARIO" },
    "PRP-7": { id: "PRP-7", nombre: "Sra. Ordoñez", papel: "PROPIETARIO" },
    "PRP-8": { id: "PRP-8", nombre: "Sra. Del Águila", papel: "PROPIETARIO" },
    "PRP-9": { id: "PRP-9", nombre: "Inmobiliaria Sáenz", papel: "PROPIETARIO" },
    "PRP-10": { id: "PRP-10", nombre: "Sr. Villena", papel: "PROPIETARIO" },
    "PRP-11": { id: "PRP-11", nombre: "Sra. Iturbe", papel: "PROPIETARIO" },
    "PRP-12": { id: "PRP-12", nombre: "Sr. Cavero", papel: "PROPIETARIO" },
    "PRP-13": { id: "PRP-13", nombre: "Sra. Zegarra", papel: "PROPIETARIO" },
    "PRP-14": { id: "PRP-14", nombre: "Sr. Palomino", papel: "PROPIETARIO" },

    /* Clientes e interesados */
    "CLI-1": { id: "CLI-1", nombre: "Grupo Andina", papel: "CLIENTE" },
    "CLI-2": { id: "CLI-2", nombre: "Textiles Sur", papel: "CLIENTE" },
    "CLI-3": { id: "CLI-3", nombre: "Farmacias del Sur", papel: "CLIENTE" },
    "CLI-4": { id: "CLI-4", nombre: "Comercial Nova", papel: "CLIENTE" },
    "CLI-5": { id: "CLI-5", nombre: "Distribuidora Ríos", papel: "CLIENTE" },
    "CLI-6": { id: "CLI-6", nombre: "María Torres", papel: "CLIENTE" },
    "CLI-7": { id: "CLI-7", nombre: "Carlos Mejía", papel: "CLIENTE" },
    "CLI-8": { id: "CLI-8", nombre: "Comercial Andes", papel: "CLIENTE" },
    "CLI-9": { id: "CLI-9", nombre: "Comercial Vela", papel: "CLIENTE" },
    "CLI-10": { id: "CLI-10", nombre: "Textil Aurora", papel: "CLIENTE" }
  };

  function per(id) { return PERSONAS[id]; }
  function nom(id) { return PERSONAS[id].nombre; }

  var AGENTES = ["PER-1", "PER-3", "PER-4", "PER-5", "PER-6", "PER-7", "PER-8", "PER-9"];
  var AGENTE_VISTA = "PER-1";   /* el agente cuya pantalla se enseña */
  var BROKER_VISTA = "PER-2";

  /* ------------------------------------------------------------------
     3 · PROPIEDADES
     `rango` es el rango real de renta de su zona y metraje: sale del
     historico economico de la casa (E0), nunca de estadisticas del sector.
     ------------------------------------------------------------------ */

  var PROPIEDADES = {
    "PROP-118": { id: "PROP-118", direccion: "Jr. Ica 118", distrito: "Breña", m2: 85, propietario: "PRP-1",
      renta: 2900, rentaDesde: null, serieRenta: null, rango: [2100, 3600], rangoDe: "Breña" },
    "PROP-1840": { id: "PROP-1840", direccion: "Av. Arequipa 1840", distrito: "Miraflores", m2: 140, propietario: "PRP-2",
      renta: 4500, rentaDesde: "2026-06-18", serieRenta: [4800, 4800, 4800, 4500, 4500, 4500, 4500],
      rango: [3200, 5100], rangoDe: "Miraflores" },
    "PROP-259": { id: "PROP-259", direccion: "Petit Thouars 259", distrito: "Lince", m2: 120, propietario: "PRP-6",
      renta: 4200, rentaDesde: "2026-06-09", serieRenta: [4500, 4500, 4500, 4200, 4200, 4200, 4200],
      rango: [3400, 4800], rangoDe: "Lince" },
    "PROP-380": { id: "PROP-380", direccion: "Arenales 380", distrito: "Lince", m2: 95, propietario: "PRP-3",
      renta: 3100, rentaDesde: "2026-03-04", serieRenta: null, rango: [2900, 4100], rangoDe: "Lince" },
    "PROP-900": { id: "PROP-900", direccion: "Av. Brasil 900", distrito: "Jesús María", m2: 210, propietario: "PRP-4",
      renta: 6800, rentaDesde: "2026-08-06", serieRenta: null, rango: [4800, 7200], rangoDe: "Jesús María para 210 m²" },
    "PROP-240": { id: "PROP-240", direccion: "Jr. Cusco 240", distrito: "Cercado", m2: 64, propietario: "PRP-7",
      renta: 2400, rentaDesde: "2026-07-29", serieRenta: null, rango: [2200, 3400], rangoDe: "Cercado" },
    "PROP-1120": { id: "PROP-1120", direccion: "Av. Huaylas 1120", distrito: "Chorrillos", m2: 90, propietario: "PRP-5",
      renta: 2800, rentaDesde: "2026-07-11", serieRenta: [2800, 2800, 2800, 2800, 2800, 2800, 2800],
      rango: [2200, 3200], rangoDe: "Chorrillos" },
    "PROP-780": { id: "PROP-780", direccion: "Av. Larco 780", distrito: "Miraflores", m2: 110, propietario: "PRP-8",
      renta: 3600, rentaDesde: "2026-05-20", serieRenta: null, rango: [3200, 5100], rangoDe: "Miraflores" },
    "PROP-1200": { id: "PROP-1200", direccion: "Av. Salaverry 1200", distrito: "Jesús María", m2: 130, propietario: "PRP-9",
      renta: 5100, rentaDesde: "2026-07-26", serieRenta: null, rango: [3900, 5400], rangoDe: "Jesús María" },
    "PROP-455": { id: "PROP-455", direccion: "Jr. Tacna 455", distrito: "Cercado", m2: 88, propietario: "PRP-10",
      renta: 3400, rentaDesde: "2026-06-30", serieRenta: null, rango: [2600, 3900], rangoDe: "Cercado" },
    "PROP-410": { id: "PROP-410", direccion: "Jr. Canevaro 410", distrito: "Lince", m2: 70, propietario: "PRP-11",
      renta: 2700, rentaDesde: "2026-08-10", serieRenta: null, rango: [2400, 3300], rangoDe: "Lince" },
    "PROP-COL": { id: "PROP-COL", direccion: "Av. Colonial 780", distrito: "Cercado", m2: 150, propietario: "PRP-12",
      renta: 2600, rentaDesde: "2026-05-02", serieRenta: null, rango: [2200, 3400], rangoDe: "Cercado" },
    /* Cartera de Diego Rojas: sostiene la agenda propia del broker. */
    "PROP-CAM": { id: "PROP-CAM", direccion: "Av. Camino Real 145", distrito: "San Isidro", m2: 160, propietario: "PRP-13",
      renta: 5800, rentaDesde: null, serieRenta: null, rango: [4600, 7400], rangoDe: "San Isidro" },
    "PROP-PAR": { id: "PROP-PAR", direccion: "Jr. Paruro 620", distrito: "Cercado", m2: 105, propietario: "PRP-14",
      renta: 3200, rentaDesde: "2026-06-15", serieRenta: null, rango: [2600, 3900], rangoDe: "Cercado" }
  };
  function prop(id) { return PROPIEDADES[id]; }
  function dir(id) { return PROPIEDADES[id].direccion; }

  /* ------------------------------------------------------------------
     4 · PROCESOS DE LA OFERTA
     ------------------------------------------------------------------ */

  /* Una prospeccion con `desenlace` ya no compite: dejo de estar viva el dia
     que se cerro. `CAPTADO` apunta a la captacion que la sucede. */
  var PROSPECCIONES = {
    "PRO-0022": { id: "PRO-0022", propiedad: "PROP-900", agente: "PER-1",
      abierta: "2026-07-18", propuestaEnviada: "2026-08-01", intentos: 2,
      desenlace: "CAPTADO", fechaDesenlace: "2026-08-06", captacion: "CAP-0034",
      rentaEstimada: 6800 }
  };

  /* `plazoDias` manda: el vencimiento se calcula, nunca se escribe. */
  var CAPTACIONES = {
    "CAP-0031": { id: "CAP-0031", propiedad: "PROP-118", agente: "PER-1",
      registrada: "2026-08-02", firmaEncargo: "2026-08-02", plazoDias: 180, exclusivo: false,
      estado: "OBSERVADA", fechaRevision: "2026-08-06", observaciones: 2, observacionesResueltas: 1,
      fechaResolucionUltima: "2026-08-08", documentos: [2, 3], publicada: null,
      ultimoReporte: null, rentaPropuesta: 2900 },
    "CAP-0022": { id: "CAP-0022", propiedad: "PROP-1840", agente: "PER-1",
      registrada: "2026-02-23", firmaEncargo: "2026-02-23", plazoDias: 180, exclusivo: true,
      estado: "ACTIVA", publicada: "2026-02-26", ultimoReporte: "2026-07-28",
      visitas: 4, propuestas: 0, ultimaVisita: "2026-08-06",
      ultimoContactoPropietario: "2026-08-09", rentaPropuesta: 4500 },
    "CAP-0009": { id: "CAP-0009", propiedad: "PROP-259", agente: "PER-1",
      registrada: "2026-06-09", firmaEncargo: "2026-06-09", plazoDias: 183, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-06-11", ultimoReporte: "2026-08-01",
      visitas: 7, propuestas: 0, ultimaVisita: "2026-08-09", rentaPropuesta: 4200,
      /* La racha de visitas empieza aqui: sin esta fecha, «7 visitas en 14
         días» seria un 14 escrito a mano. */
      visitasDesde: "2026-07-27",
      objeciones: { precio: 4, metraje: 1, de: 7 } },
    "CAP-0028": { id: "CAP-0028", propiedad: "PROP-380", agente: "PER-1",
      registrada: "2026-03-04", firmaEncargo: "2026-03-04", plazoDias: 179, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-03-06", ultimoReporte: "2026-07-30",
      visitas: 2, propuestas: 1, propuestasRechazadas: 1, ultimaVisita: "2026-07-12",
      ultimoContactoPropietario: "2026-07-30", rentaPropuesta: 3100 },
    "CAP-0034": { id: "CAP-0034", propiedad: "PROP-900", agente: "PER-1",
      registrada: "2026-08-06", firmaEncargo: null, plazoDias: 180, exclusivo: false,
      estado: "EN_REVISION", documentos: [3, 3], publicada: null, ultimoReporte: null,
      rentaPropuesta: 6800, prospeccion: "PRO-0022" },
    "CAP-0036": { id: "CAP-0036", propiedad: "PROP-240", agente: "PER-1",
      registrada: "2026-07-25", firmaEncargo: "2026-07-25", plazoDias: 184, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-07-29", ultimoReporte: "2026-07-29",
      vistasFicha: 18, visitas: 0, ficha: [3, 5], fichaFalta: "fotos ni potencia eléctrica",
      rentaPropuesta: 2400 },
    "CAP-0041": { id: "CAP-0041", propiedad: "PROP-1120", agente: "PER-1",
      registrada: "2026-07-08", firmaEncargo: "2026-07-08", plazoDias: 184, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-07-11", ultimoReporte: "2026-07-19",
      vistasFicha: 18, visitas: 0, rentaPropuesta: 2800 },
    "CAP-0037": { id: "CAP-0037", propiedad: "PROP-410", agente: "PER-1",
      registrada: "2026-08-07", firmaEncargo: "2026-08-07", plazoDias: 180, exclusivo: false,
      estado: "ACTIVA", aprobada: "2026-08-10T05:30", publicada: "2026-08-10T05:30",
      ultimoReporte: "2026-08-10", rentaPropuesta: 2700 },
    "CAP-0044": { id: "CAP-0044", propiedad: "PROP-780", agente: "PER-1",
      registrada: "2026-05-20", firmaEncargo: "2026-05-20", plazoDias: 184, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-05-23", ultimoReporte: "2026-08-02",
      visitas: 1, propuestas: 0, rentaPropuesta: 3600 },
    "CAP-0051": { id: "CAP-0051", propiedad: "PROP-1200", agente: "PER-1",
      registrada: "2026-07-26", firmaEncargo: "2026-07-26", plazoDias: 184, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-07-28", ultimoReporte: "2026-07-28",
      visitas: 2, propuestas: 0, rentaPropuesta: 5100 },
    "CAP-0055": { id: "CAP-0055", propiedad: "PROP-455", agente: "PER-1",
      registrada: "2026-06-30", firmaEncargo: "2026-06-30", plazoDias: 184, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-07-02", ultimoReporte: "2026-07-20",
      visitas: 2, propuestas: 1, rentaPropuesta: 3400 },
    "CAP-0231": { id: "CAP-0231", propiedad: "PROP-CAM", agente: "PER-3",
      registrada: "2026-08-08", firmaEncargo: null, plazoDias: 180, exclusivo: false,
      estado: "EN_REVISION", documentos: [3, 3], publicada: null, ultimoReporte: null,
      rentaPropuesta: 5800 },
    "CAP-0219": { id: "CAP-0219", propiedad: "PROP-PAR", agente: "PER-3",
      registrada: "2026-06-15", firmaEncargo: "2026-06-15", plazoDias: 184, exclusivo: false,
      estado: "ACTIVA", publicada: "2026-06-18", ultimoReporte: "2026-07-30",
      visitas: 3, propuestas: 1, rentaPropuesta: 3200 },
    /* Contrato firmado: la captacion de Av. Colonial 780 esta cerrada. */
    "CAP-0140": { id: "CAP-0140", propiedad: "PROP-COL", agente: "PER-1",
      registrada: "2026-04-28", firmaEncargo: "2026-04-28", plazoDias: 184, exclusivo: false,
      estado: "CERRADA", publicada: "2026-05-02", ultimoReporte: "2026-07-24",
      visitas: 5, propuestas: 1, rentaPropuesta: 2600 }
  };
  function cap(id) { return CAPTACIONES[id]; }
  function venceEncargo(c) {
    return c.firmaEncargo ? sumarDias(c.firmaEncargo, c.plazoDias) : null;
  }
  function consumidoEncargo(c) {
    if (!c.firmaEncargo) return null;
    return { transcurridos: diasDesde(c.firmaEncargo), previsto: c.plazoDias };
  }

  /* ------------------------------------------------------------------
     5 · PROCESOS DE LA DEMANDA
     ------------------------------------------------------------------ */

  var OPORTUNIDADES = {
    "OPO-0098": { id: "OPO-0098", propiedad: "PROP-780", cliente: "CLI-2", agente: "PER-1",
      abierta: "2026-07-22", rentaPedida: 3600, presupuesto: [3000, 4200],
      contactos: 3, visitas: 1, propuestas: 0 },
    "OPO-0103": { id: "OPO-0103", propiedad: "PROP-1200", cliente: "CLI-4", agente: "PER-1",
      abierta: "2026-07-26", rentaPedida: 5100, presupuesto: [3900, 5400],
      contactos: 2, visitas: 2, propuestas: 0, condicionesPedidas: "2026-08-08",
      decideEnSemanas: 2 },
    "OPO-0087": { id: "OPO-0087", propiedad: "PROP-259", cliente: "CLI-7", agente: "PER-1",
      abierta: "2026-01-14", cerrada: "2026-03-20", motivoCierre: "precio",
      reabierta: "2026-08-10T08:26", presupuesto: [3200, 4500],
      contactos: 4, visitas: 3, propuestas: 1 },
    "OPO-0111": { id: "OPO-0111", propiedad: null, cliente: "CLI-1", agente: "PER-1",
      abierta: "2026-08-10T07:20", rubro: "gastronómico", presupuesto: [2800, 4000],
      m2: [80, 130], zona: "Miraflores", primerRequerimiento: true },
    "OPO-0064": { id: "OPO-0064", propiedad: null, cliente: "CLI-3", agente: "PER-1",
      abierta: "2026-05-12", presupuesto: [2500, 4000], zonas: ["Lince", "Jesús María"],
      requerimientosAbiertos: 3, contactos: 6, visitas: 2, propuestas: 0,
      ultimoContacto: "2026-08-03" }
  };
  function opo(id) { return OPORTUNIDADES[id]; }

  /* La coincidencia de cartera: el motor del hallazgo (ya existe en el
     backend como `CoincidenciaCartera`). Criterios comprobados y el que
     falta, no un porcentaje suelto. */
  var COINCIDENCIAS = {
    "COI-1": { id: "COI-1", requerimiento: "OPO-0111", propiedades: ["PROP-780", "PROP-410"],
      criterios: ["ubicación", "metraje", "rubro", "renta"], falta: "potencia eléctrica",
      comprobados: 4, total: 5, detectada: "2026-08-10T07:22" }
  };

  var VISITAS = {
    /* LA visita de mañana a las 10:30. Un solo evento, un solo id. */
    "VIS-0044": { id: "VIS-0044", propiedad: "PROP-780", oportunidad: "OPO-0098", cliente: "CLI-2",
      agente: "PER-1", cuando: "2026-08-11T10:30", programada: "2026-08-05",
      estado: "PROGRAMADA", confirmada: false },
    "VIS-0051": { id: "VIS-0051", propiedad: "PROP-259", oportunidad: null, cliente: null,
      agente: "PER-1", cuando: "2026-08-28T16:00", programada: "2026-08-04",
      estado: "PROGRAMADA", confirmada: true },
    "VIS-0039": { id: "VIS-0039", propiedad: "PROP-259", cliente: "CLI-6", agente: "PER-1",
      cuando: "2026-08-09T11:00", estado: "REALIZADA", resultado: "SIN_PROPUESTA", objecion: "precio" }
  };
  function vis(id) { return VISITAS[id]; }

  var SOLICITUDES = {
    "SOL-0114": { id: "SOL-0114", propiedad: "PROP-259", interesado: "CLI-6", agente: "PER-1",
      creada: "2026-07-28", renta: 4200, plazoMeses: 36,
      documentos: [4, 4], completa: "2026-08-10T07:53", verificada: "2026-08-10T07:53",
      observaciones: 0, entraBandejaBroker: "2026-08-08", estado: "POR_EVALUAR",
      fechaComprometida: "2026-08-13", visitasPrevias: 2 },
    "SOL-0121": { id: "SOL-0121", propiedad: "PROP-455", interesado: "CLI-5", agente: "PER-1",
      creada: "2026-07-28", renta: 3400, plazoMeses: 24,
      documentos: [3, 4], ultimoDocumento: "2026-08-04", falta: "estados financieros", falta1: "estado financiero",
      estado: "EN_PREPARACION", visitasPrevias: 2 },
    "SOL-0108": { id: "SOL-0108", propiedad: "PROP-240", interesado: "CLI-8", agente: "PER-1",
      creada: "2026-07-20", renta: 2400, plazoMeses: 24,
      documentos: [4, 4], estado: "APROBADA", aprobada: "2026-08-04",
      contrato: null, comisionPrevista: 1150 },
    "SOL-0082": { id: "SOL-0082", propiedad: "PROP-PAR", interesado: "CLI-10", agente: "PER-3",
      creada: "2026-07-30", renta: 3200, plazoMeses: 24,
      documentos: [4, 4], completa: "2026-08-09", observaciones: 0, estado: "POR_EVALUAR",
      entraBandejaBroker: "2026-08-09" }
  };
  function sol(id) { return SOLICITUDES[id]; }

  var CONTRATOS = {
    "CON-0071": { id: "CON-0071", propiedad: "PROP-COL", solicitud: null, cliente: "CLI-9",
      agente: "PER-1", firmado: "2026-07-24", plazoMeses: 24, renta: 2600,
      comision: { devengada: 2340, cobrada: 0, fechaPagoComprometida: null } }
  };
  function con(id) { return CONTRATOS[id]; }

  /* ------------------------------------------------------------------
     6 · PRODUCCION POR AGENTE
     De aqui salen LOS CUATRO KPI y LOS DOS EMBUDOS. El equipo es la suma:
     no hay un total escrito aparte que pueda desmentir a sus partes
     (D-E2-2 §5).
     ------------------------------------------------------------------ */

  var KPIS = {
    /* Los cuatro nombres canonicos, letra por letra en las dos pantallas. */
    definiciones: [
      { clave: "prospeccion", rotulo: "Propietarios contactados", u: "propietarios", u1: "propietario",
        origen: { campo: "prospectos", que: "prospecciones", verbo: "llegan a contacto" } },
      { clave: "captacion", rotulo: "Locales captados", u: "locales", u1: "local",
        origen: { campo: "prospeccion", que: "contactos", verbo: "entran a cartera" } },
      { clave: "solicitud", rotulo: "Solicitudes ingresadas", u: "solicitudes", u1: "solicitud",
        origen: { campo: "visitas", que: "visitas", verbo: "acaban en solicitud" } },
      { clave: "contrato", rotulo: "Contratos firmados", u: "contratos", u1: "contrato",
        origen: { campo: "aprobadas", que: "solicitudes aprobadas", verbo: "llegan a contrato" } }
    ],
    /* Metas vigentes del mes por agente. La del equipo es su suma. */
    metas: {
      "PER-1": { prospeccion: 24, captacion: 15, solicitud: 8, contrato: 5 },
      "PER-3": { prospeccion: 24, captacion: 8, solicitud: 4, contrato: 3 },
      "PER-4": { prospeccion: 22, captacion: 7, solicitud: 4, contrato: 3 },
      "PER-5": { prospeccion: 22, captacion: 7, solicitud: 4, contrato: 3 },
      "PER-6": { prospeccion: 24, captacion: 7, solicitud: 3, contrato: 2 },
      "PER-7": { prospeccion: 20, captacion: 5, solicitud: 2, contrato: 2 },
      "PER-8": { prospeccion: 14, captacion: 4, solicitud: 2, contrato: 1 },
      "PER-9": { prospeccion: 10, captacion: 3, solicitud: 1, contrato: 1 }
    }
  };

  /* Contadores del periodo base (mes en curso, dia 26 de 30).
     `prospectos` y `visitas` no son KPI: son los DENOMINADORES del embudo, y
     por eso viven al lado -- un KPI y su origen no pueden salir de dos
     sitios distintos. `aprobadas` es la etapa que faltaba. */
  var PRODUCCION = {
    "PER-1": { prospectos: 31, prospeccion: 22, captacion: 13, publicados: 11,
      oportunidades: 24, visitas: 16, solicitud: 6, aprobadas: 5, contrato: 4 },
    "PER-3": { prospectos: 30, prospeccion: 23, captacion: 8, publicados: 7,
      oportunidades: 13, visitas: 9, solicitud: 5, aprobadas: 4, contrato: 3 },
    "PER-4": { prospectos: 28, prospeccion: 21, captacion: 7, publicados: 6,
      oportunidades: 12, visitas: 8, solicitud: 4, aprobadas: 3, contrato: 2 },
    "PER-5": { prospectos: 27, prospeccion: 20, captacion: 7, publicados: 6,
      oportunidades: 9, visitas: 6, solicitud: 3, aprobadas: 3, contrato: 3 },
    "PER-6": { prospectos: 24, prospeccion: 8, captacion: 5, publicados: 4,
      oportunidades: 24, visitas: 17, solicitud: 2, aprobadas: 2, contrato: 2 },
    "PER-7": { prospectos: 23, prospeccion: 20, captacion: 5, publicados: 4,
      oportunidades: 6, visitas: 4, solicitud: 2, aprobadas: 2, contrato: 2 },
    "PER-8": { prospectos: 20, prospeccion: 18, captacion: 4, publicados: 4,
      oportunidades: 4, visitas: 2, solicitud: 1, aprobadas: 1, contrato: 1 },
    "PER-9": { prospectos: 15, prospeccion: 14, captacion: 2, publicados: 2,
      oportunidades: 4, visitas: 1, solicitud: 1, aprobadas: 0, contrato: 0 }
  };

  /* Estado de gestion por agente: lo que alimenta cartera, lectura y
     supervision. Todo conteo, ninguna duracion. */
  var GESTION = {
    "PER-1": { cartera: 13, recontactos: 11, recontactosVencidos: 2, sinMovimiento90: 1,
      captacionesSinObservacion: 11, captacionesRevisadas: 13,
      visitasConResultado: 12, encargosPorVencer: 3, encargosSinExclusividad: 1,
      diasPublicadoMedia: 68, localesSobreUmbral: 1, rentaSinAjustar: 2,
      fichasIncompletas: 3, rentaEnDolares: 0.62,
      etapa: [["Activa", 5], ["Interesados", 3], ["Solicitud", 2], ["Evaluación", 1], ["Alquilada", 2]],
      distrito: [["Miraflores", 5], ["San Isidro", 3], ["Surco", 2], ["Lince", 2], ["Barranco", 1]] },
    "PER-3": { cartera: 5, recontactos: 5, recontactosVencidos: 0, sinMovimiento90: 1,
      captacionesSinObservacion: 7, captacionesRevisadas: 8, visitasConResultado: 7,
      encargosPorVencer: 1, encargosSinExclusividad: 0, diasPublicadoMedia: 70,
      localesSobreUmbral: 1, rentaSinAjustar: 1, fichasIncompletas: 1, rentaEnDolares: 0.55,
      etapa: [["Activa", 2], ["Interesados", 2], ["Solicitud", 1], ["Evaluación", 0], ["Alquilada", 0]],
      distrito: [["Miraflores", 2], ["San Isidro", 1], ["Surco", 1], ["Lince", 1], ["Barranco", 0]] },
    "PER-4": { cartera: 4, recontactos: 4, recontactosVencidos: 0, sinMovimiento90: 0,
      captacionesSinObservacion: 6, captacionesRevisadas: 7, visitasConResultado: 6,
      encargosPorVencer: 1, encargosSinExclusividad: 1, diasPublicadoMedia: 66,
      localesSobreUmbral: 0, rentaSinAjustar: 1, fichasIncompletas: 0, rentaEnDolares: 0.6,
      etapa: [["Activa", 2], ["Interesados", 1], ["Solicitud", 0], ["Evaluación", 1], ["Alquilada", 0]],
      distrito: [["Miraflores", 1], ["San Isidro", 1], ["Surco", 1], ["Lince", 0], ["Barranco", 1]] },
    "PER-5": { cartera: 3, recontactos: 4, recontactosVencidos: 0, sinMovimiento90: 0,
      captacionesSinObservacion: 6, captacionesRevisadas: 7, visitasConResultado: 5,
      encargosPorVencer: 0, encargosSinExclusividad: 0, diasPublicadoMedia: 64,
      localesSobreUmbral: 0, rentaSinAjustar: 1, fichasIncompletas: 1, rentaEnDolares: 0.5,
      etapa: [["Activa", 1], ["Interesados", 1], ["Solicitud", 1], ["Evaluación", 0], ["Alquilada", 0]],
      distrito: [["Miraflores", 1], ["San Isidro", 1], ["Surco", 0], ["Lince", 0], ["Barranco", 1]] },
    "PER-6": { cartera: 20, recontactos: 8, recontactosVencidos: 5, sinMovimiento90: 2,
      captacionesSinObservacion: 4, captacionesRevisadas: 5, visitasConResultado: 12,
      encargosPorVencer: 2, encargosSinExclusividad: 1, diasPublicadoMedia: 82,
      localesSobreUmbral: 2, rentaSinAjustar: 2, fichasIncompletas: 2, rentaEnDolares: 0.58,
      etapa: [["Activa", 14], ["Interesados", 4], ["Solicitud", 1], ["Evaluación", 0], ["Alquilada", 1]],
      distrito: [["Miraflores", 6], ["San Isidro", 5], ["Surco", 3], ["Lince", 3], ["Barranco", 3]] },
    "PER-7": { cartera: 2, recontactos: 3, recontactosVencidos: 0, sinMovimiento90: 0,
      captacionesSinObservacion: 4, captacionesRevisadas: 5, visitasConResultado: 3,
      encargosPorVencer: 0, encargosSinExclusividad: 0, diasPublicadoMedia: 60,
      localesSobreUmbral: 0, rentaSinAjustar: 0, fichasIncompletas: 0, rentaEnDolares: 0.62,
      etapa: [["Activa", 1], ["Interesados", 1], ["Solicitud", 0], ["Evaluación", 0], ["Alquilada", 0]],
      distrito: [["Miraflores", 1], ["San Isidro", 1], ["Surco", 0], ["Lince", 0], ["Barranco", 0]] },
    "PER-8": { cartera: 1, recontactos: 2, recontactosVencidos: 0, sinMovimiento90: 0,
      captacionesSinObservacion: 3, captacionesRevisadas: 4, visitasConResultado: 2,
      encargosPorVencer: 0, encargosSinExclusividad: 0, diasPublicadoMedia: 58,
      localesSobreUmbral: 0, rentaSinAjustar: 0, fichasIncompletas: 0, rentaEnDolares: 0.55,
      etapa: [["Activa", 1], ["Interesados", 0], ["Solicitud", 0], ["Evaluación", 0], ["Alquilada", 0]],
      distrito: [["Miraflores", 0], ["San Isidro", 0], ["Surco", 1], ["Lince", 0], ["Barranco", 0]] },
    "PER-9": { cartera: 1, recontactos: 2, recontactosVencidos: 0, sinMovimiento90: 0,
      captacionesSinObservacion: 2, captacionesRevisadas: 2, visitasConResultado: 1,
      encargosPorVencer: 0, encargosSinExclusividad: 0, diasPublicadoMedia: 55,
      localesSobreUmbral: 0, rentaSinAjustar: 0, fichasIncompletas: 0, rentaEnDolares: 0.6,
      etapa: [["Activa", 0], ["Interesados", 0], ["Solicitud", 0], ["Evaluación", 0], ["Alquilada", 1]],
      distrito: [["Miraflores", 0], ["San Isidro", 1], ["Surco", 0], ["Lince", 0], ["Barranco", 0]] },
    /* Los locales sin agente asignado son cartera del equipo y de nadie. */
    "SIN_ASIGNAR": { cartera: 5,
      etapa: [["Activa", 2], ["Interesados", 1], ["Solicitud", 1], ["Evaluación", 1], ["Alquilada", 0]],
      distrito: [["Miraflores", 2], ["San Isidro", 1], ["Surco", 1], ["Lince", 1], ["Barranco", 0]] }
  };

  /* La cartera del equipo NO se escribe: se suma la de sus agentes más la de
     los no asignados. Así «Cartera · Agente» y «Cartera · Etapa» cuentan
     siempre lo mismo, que es lo que dejó de cuadrar en el prototipo anterior. */
  function carteraEquipoPor(eje) {
    var acum = {}, orden = [];
    Object.keys(GESTION).forEach(function (k) {
      (GESTION[k][eje] || []).forEach(function (par) {
        if (!(par[0] in acum)) { acum[par[0]] = 0; orden.push(par[0]); }
        acum[par[0]] += par[1];
      });
    });
    return orden.map(function (n) { return [n, acum[n]]; });
  }
  var CARTERA_EQUIPO = {
    get etapa() { return carteraEquipoPor("etapa"); },
    get distrito() { return carteraEquipoPor("distrito"); }
  };

  /* Seis tramos de historia por KPI, para las curvas. El ultimo es el actual
     y se comprueba contra PRODUCCION. */
  var SERIES = {
    "PER-1": { prospeccion: [14, 17, 16, 20, 19, 22], captacion: [9, 11, 10, 13, 11, 13],
      solicitud: [4, 5, 5, 7, 6, 6], contrato: [2, 3, 3, 5, 4, 4] },
    EQUIPO: { prospeccion: [96, 112, 108, 134, 128, 146], captacion: [34, 40, 38, 47, 44, 51],
      solicitud: [15, 18, 17, 22, 21, 24], contrato: [12, 15, 14, 18, 17, 17] }
  };

  /* ------------------------------------------------------------------
     7 · PERIODOS
     El filtro elige el PERIODO EN CURSO, no una ventana hacia atras: es la
     unica lectura con la que la meta significa algo.
     ------------------------------------------------------------------ */

  var PERIODO_BASE_TRANSCURRIDOS = 26;

  var PERIODOS = [
    { v: "7d", e: "7 días", dias: 7, transcurridos: 3, f: 7 / 30, cada: "por semana", ant: "a la semana anterior", curso: "semana en curso", rot: "6 semanas", etq: ["6 jul", "13 jul", "20 jul", "27 jul", "3 ago", "10 ago"] },
    { v: "1m", e: "1 mes", dias: 30, transcurridos: 26, f: 1, cada: "al mes", ant: "al mes anterior", curso: "mes en curso", rot: "6 meses", etq: ["Mar", "Abr", "May", "Jun", "Jul", "Ago"] },
    { v: "3m", e: "3 meses", dias: 92, transcurridos: 87, f: 3, cada: "por trimestre", ant: "al trimestre anterior", curso: "trimestre en curso", rot: "6 trimestres", etq: ["2T 25", "3T 25", "4T 25", "1T 26", "2T 26", "3T 26"] },
    { v: "6m", e: "6 meses", dias: 184, transcurridos: 148, f: 6, cada: "por semestre", ant: "al semestre anterior", curso: "semestre en curso", rot: "6 semestres", etq: ["1S 24", "2S 24", "1S 25", "2S 25", "1S 26", "2S 26"] },
    { v: "1y", e: "1 año", dias: 365, transcurridos: 223, f: 12, cada: "al año", ant: "al año anterior", curso: "año en curso", rot: "6 años", etq: ["2021", "2022", "2023", "2024", "2025", "2026"] }
  ];
  var PERIODO_INICIO = "1m";   /* el pie del Inicio siempre habla del mes en curso */

  function periodoDe(v, opciones) {
    var p = null;
    for (var i = 0; i < PERIODOS.length; i++) if (PERIODOS[i].v === v) p = PERIODOS[i];
    if (!p) p = PERIODOS[1];
    var transcurridos = p.transcurridos;
    /* Un arranque flojo no fija cifras: mueve el momento del periodo. */
    if (opciones && opciones.arranque) transcurridos = Math.max(1, Math.round(p.dias * 0.1));
    return {
      v: p.v, e: p.e, dias: p.dias, transcurridos: transcurridos, f: p.f,
      cada: p.cada, ant: p.ant, curso: p.curso, rot: p.rot, etq: p.etq,
      /* factores con los que se proyecta el fixture base a este periodo */
      fReal: transcurridos / PERIODO_BASE_TRANSCURRIDOS,
      fMeta: p.f,
      flojo: opciones && opciones.arranque ? 0.35 : 1
    };
  }

  /* ------------------------------------------------------------------
     8 · EL RITMO — se calcula UNA VEZ, aqui
     El criterio es PROYECCION AL CIERRE: si mantengo este ritmo, ¿en cuanto
     acabo? No "cuanto llevo de la meta" ni "cuanto llevo de lo esperado".
     ------------------------------------------------------------------ */

  function ritmoDe(actual, metaPeriodo, periodo) {
    var t = periodo.transcurridos, d = periodo.dias, restantes = d - t;
    var base = {
      actual: actual, metaPeriodo: metaPeriodo || 0,
      metaEsperadaAHoy: null, proyeccionCierre: actual,
      porcentajeProyectado: null, porcentajeMeta: null,
      faltante: null, sinCadencia: false, arranque: false,
      estadoRitmo: RITMO.SIN_BASE, voz: VOZ_RITMO.SIN_BASE
    };

    if (!metaPeriodo) return base;

    base.faltante = Math.max(0, metaPeriodo - actual);
    base.porcentajeMeta = pc(actual, metaPeriodo);

    /* No todos los dias se firma un contrato: con meta menor que el volumen
       minimo, repartirla por dias inventa una cadencia que el negocio no
       tiene. Solo se mira si ya se cumplio y cuanto periodo queda. */
    if (metaPeriodo < POLITICA.volumenMinimo) {
      base.sinCadencia = true;
      base.proyeccionCierre = actual;
      base.estadoRitmo = actual >= metaPeriodo ? RITMO.EN_RITMO
        : (restantes / d >= 0.25 ? RITMO.ATENCION : RITMO.FUERA_DE_RITMO);
      base.voz = VOZ_RITMO[base.estadoRitmo];
      return base;
    }

    base.metaEsperadaAHoy = Math.round(metaPeriodo * t / d);
    var proyeccion = actual + (actual / t) * restantes;
    /* Se trunca: no vas a firmar 0,6 contratos. */
    base.proyeccionCierre = Math.floor(proyeccion);
    base.porcentajeProyectado = Math.round(proyeccion / metaPeriodo * 100);

    var cumplimiento = proyeccion / metaPeriodo;
    var e = cumplimiento >= POLITICA.llega ? RITMO.EN_RITMO
      : (cumplimiento >= POLITICA.cerca ? RITMO.ATENCION : RITMO.FUERA_DE_RITMO);
    /* Dos guardas: sin recorrido no se proyecta nada, y a una unidad de la
       meta nunca es rojo. */
    base.arranque = t / d < POLITICA.arranquePc;
    if (e === RITMO.FUERA_DE_RITMO && base.arranque) e = RITMO.ATENCION;
    if (e === RITMO.FUERA_DE_RITMO && metaPeriodo - actual <= 1) e = RITMO.ATENCION;
    base.estadoRitmo = e;
    base.voz = VOZ_RITMO[e];
    return base;
  }

  /* ------------------------------------------------------------------
     9 · LOS KPI — la unica puerta
     `ambito` es {tipo:"AGENTE", id} o {tipo:"EQUIPO"}. Las dos pantallas
     llaman aqui con el mismo ambito y el mismo periodo, y por eso no
     pueden discrepar.
     ------------------------------------------------------------------ */

  function agentesDe(ambito) {
    return ambito.tipo === "EQUIPO" ? AGENTES.slice() : [ambito.id];
  }

  function suma(ids, tabla, campo) {
    var t = 0;
    ids.forEach(function (id) { t += (tabla[id] && tabla[id][campo]) || 0; });
    return t;
  }

  function escala(n, factor, flojo) {
    return Math.round(n * factor * (flojo === undefined ? 1 : flojo));
  }

  /* Contadores del ambito ya proyectados al periodo. Un solo sitio: el
     embudo y los KPI leen de aqui, asi que el 22 de "contactados" es
     literalmente el mismo 22 del KPI. */
  function contadores(ambito, periodo) {
    var ids = agentesDe(ambito), c = {};
    ["prospectos", "prospeccion", "captacion", "publicados",
      "oportunidades", "visitas", "solicitud", "aprobadas", "contrato"].forEach(function (k) {
      c[k] = escala(suma(ids, PRODUCCION, k), periodo.fReal, periodo.flojo);
    });
    /* El redondeo podria invertir una cadena: un paso nunca supera al
       anterior. Se corrige aqui y no en la pantalla. */
    ["prospectos>prospeccion", "prospeccion>captacion", "captacion>publicados",
      "oportunidades>visitas", "visitas>solicitud", "solicitud>aprobadas",
      "aprobadas>contrato"].forEach(function (par) {
      var p = par.split(">");
      if (c[p[1]] > c[p[0]]) c[p[1]] = c[p[0]];
    });
    return c;
  }

  function metaDe(ambito, clave, periodo, opciones) {
    if (opciones && opciones.sinMeta && clave === "solicitud") return 0;
    var ids = agentesDe(ambito);
    var m = 0;
    ids.forEach(function (id) { m += KPIS.metas[id][clave]; });
    return m ? Math.max(1, Math.round(m * periodo.fMeta)) : 0;
  }

  /* El objeto que reciben LAS DOS pantallas, sin diferencias. */
  function kpisDe(ambito, periodoV, opciones) {
    var periodo = periodoDe(periodoV, opciones);
    var c = contadores(ambito, periodo);
    var esEquipo = ambito.tipo === "EQUIPO";
    return KPIS.definiciones.map(function (d) {
      var actual = c[d.clave];
      var meta = metaDe(ambito, d.clave, periodo, opciones);
      var r = ritmoDe(actual, meta, periodo);
      var de = c[d.origen.campo];
      r.clave = d.clave;
      r.rotulo = d.rotulo;
      r.u = d.u;
      r.u1 = d.u1;
      r.periodo = periodo;
      r.origen = { de: de, que: d.origen.que, verbo: d.origen.verbo };
      /* Toda conversion muestra su N, y con muestra corta no concluye. */
      r.conversion = de
        ? { de: de, a: actual, porcentaje: pc(actual, de), fiable: de >= POLITICA.muestraMinima }
        : null;
      r.serie = (esEquipo ? SERIES.EQUIPO : SERIES[ambito.id] || SERIES.EQUIPO)[d.clave];
      return r;
    });
  }

  function kpiDe(ambito, clave, periodoV, opciones) {
    var l = kpisDe(ambito, periodoV, opciones);
    for (var i = 0; i < l.length; i++) if (l[i].clave === clave) return l[i];
    return null;
  }

  /* ------------------------------------------------------------------
     10 · EMBUDOS
     Se derivan de los mismos contadores. La etapa de evaluacion existe: la
     perdida entre ingresadas y aprobadas es el trabajo del broker.
     ------------------------------------------------------------------ */

  function embudos(ambito, periodoV, opciones) {
    var periodo = periodoDe(periodoV, opciones);
    var c = contadores(ambito, periodo);
    var salto = function (de, a, x, y) { return { de: de, a: a, n: x, avanzo: y, perdidos: x - y, porcentaje: pc(y, x), fiable: x >= POLITICA.muestraMinima }; };
    return {
      oferta: [
        salto("Prospecto", "Contactado", c.prospectos, c.prospeccion),
        salto("Contactado", "Captación", c.prospeccion, c.captacion),
        salto("Captación", "Publicación", c.captacion, c.publicados)
      ],
      demanda: [
        salto("Oportunidad", "Visita", c.oportunidades, c.visitas),
        salto("Visita", "Solicitud", c.visitas, c.solicitud),
        salto("Solicitud", "Aprobada", c.solicitud, c.aprobadas),
        salto("Aprobada", "Contrato", c.aprobadas, c.contrato)
      ],
      contadores: c
    };
  }

  /* Conversion visita→solicitud por agente: lo que revela que una media del
     equipo esconde a un agente. Sale de PRODUCCION, no de una lista aparte. */
  function conversionPorAgente(clave) {
    var de = { solicitud: "visitas", captacion: "prospeccion", prospeccion: "prospectos", contrato: "aprobadas" }[clave || "solicitud"];
    var k = clave || "solicitud";
    return AGENTES.map(function (id) {
      var p = PRODUCCION[id];
      return { agente: id, nombre: nom(id), de: p[de], a: p[k],
        porcentaje: pc(p[k], p[de]), fiable: p[de] >= POLITICA.muestraMinima };
    }).sort(function (x, y) { return x.porcentaje - y.porcentaje; });
  }

  /* ------------------------------------------------------------------
     11 · EL EQUIPO — pulso, excepciones y concentracion, todo derivado
     ------------------------------------------------------------------ */

  /* El ritmo de un agente es el de su produccion agregada contra su meta
     agregada; su brecha principal es su peor KPI. Es la tabla de D-E2-2 §6.2:
     Ritmo + Principal brecha. */
  function ritmoDeAgente(id, periodoV, opciones) {
    var periodo = periodoDe(periodoV, opciones);
    var ambito = { tipo: "AGENTE", id: id };
    var kpis = kpisDe(ambito, periodoV, opciones);
    var actual = 0, meta = 0;
    kpis.forEach(function (k) { actual += k.actual; meta += k.metaPeriodo; });
    var r = ritmoDe(actual, meta, periodo);
    var orden = { FUERA_DE_RITMO: 0, ATENCION: 1, SIN_BASE: 2, EN_RITMO: 3 };
    var peor = kpis.slice().sort(function (x, y) {
      var d = orden[x.estadoRitmo] - orden[y.estadoRitmo];
      if (d !== 0) return d;
      return (x.porcentajeProyectado || 999) - (y.porcentajeProyectado || 999);
    })[0];
    return {
      agente: id, nombre: nom(id), ini: per(id).ini,
      estadoRitmo: r.estadoRitmo, voz: r.voz, actual: actual, metaPeriodo: meta,
      proyeccionCierre: r.proyeccionCierre, porcentajeProyectado: r.porcentajeProyectado,
      brecha: peor, kpis: kpis,
      recontactosVencidos: GESTION[id].recontactosVencidos
    };
  }

  /* GROUP BY estadoRitmo. Por construccion:
     totalAgentes = enRitmo + atencion + fueraDeRitmo + sinBase. */
  function pulsoEquipo(periodoV, opciones) {
    var por = {}, orden = [RITMO.EN_RITMO, RITMO.ATENCION, RITMO.FUERA_DE_RITMO, RITMO.SIN_BASE];
    orden.forEach(function (k) { por[k] = []; });
    AGENTES.forEach(function (id) {
      var r = ritmoDeAgente(id, periodoV, opciones);
      por[r.estadoRitmo].push(r);
    });
    return {
      total: AGENTES.length,
      grupos: orden.map(function (k) { return { estadoRitmo: k, voz: VOZ_RITMO[k], agentes: por[k], n: por[k].length }; })
        .filter(function (g) { return g.n > 0; }),
      por: por
    };
  }

  /* Gestion por excepcion: SOLO los agentes que el pulso no cuenta como
     EN_RITMO. Nunca un ranking, y nunca una fila que no salga de aqui. */
  function excepcionesEquipo(periodoV, opciones) {
    var p = pulsoEquipo(periodoV, opciones);
    return p.por[RITMO.FUERA_DE_RITMO].concat(p.por[RITMO.ATENCION], p.por[RITMO.SIN_BASE]);
  }

  function carteraPorAgente() {
    var l = AGENTES.map(function (id) { return { agente: id, nombre: nom(id), cartera: GESTION[id].cartera }; });
    l.push({ agente: null, nombre: "Sin asignar", cartera: GESTION.SIN_ASIGNAR.cartera });
    return l.sort(function (a, b) { return b.cartera - a.cartera; });
  }
  function carteraTotalEquipo() {
    return carteraPorAgente().reduce(function (t, x) { return t + x.cartera; }, 0);
  }

  /* La concentracion sale de la MISMA distribucion que dibuja «Cartera ·
     Agente». El agente que se nombra es, necesariamente, el primero de esa
     distribucion. */
  function concentracionCartera() {
    var l = carteraPorAgente().filter(function (x) { return x.agente; });
    var total = carteraTotalEquipo();
    var mayor = l[0];
    return {
      agente: mayor.agente, nombre: mayor.nombre, cartera: mayor.cartera,
      total: total, porcentaje: pc(mayor.cartera, total),
      segundo: l[1], reparto: Math.round(total / l.length)
    };
  }

  function equipoSuma(campo) { return suma(AGENTES, GESTION, campo); }

  /* ------------------------------------------------------------------
     12 · ASUNTOS
     Identidad estable: `asuntoId` + `entidadTipo` + `entidadId`. Una
     direccion NO identifica un asunto: un inmueble participa en varios
     procesos a la vez.
       estado    ACTIVO | FUERA_DEL_FOCO | RESUELTO
       dependeDeMi  primer filtro del despacho: solo compite lo que puedo
                    resolver yo ahora
     ------------------------------------------------------------------ */

  var LADO = {
    OFERTA: { clave: "OFERTA", actor: "PROPIETARIO", rotulo: "Propietario", pasos: ["Prospección", "Captación", "Publicación"] },
    DEMANDA: { clave: "DEMANDA", actor: "CLIENTE", rotulo: "Cliente", pasos: ["Oportunidad", "Visita", "Solicitud", "Contrato"] }
  };

  var SEVERIDAD = { ALTA: "ALTA", MEDIA: "MEDIA" };
  var NATURALEZA = { RIESGO: "RIESGO", OPORTUNIDAD: "OPORTUNIDAD" };

  /* Cada asunto declara sus FECHAS; `ventana` y `espera` son derivadas y se
     resuelven al leerlas -- si se calcularan al cargar, un `limite` que
     depende de otra entidad (el vencimiento de un encargo) se congelaria. */
  function A(o) {
    Object.defineProperty(o, "espera", { enumerable: true, get: function () {
      return o.desde ? diasDesde(v(o.desde)) : 0;
    } });
    Object.defineProperty(o, "ventana", { enumerable: true, get: function () {
      var l = v(o.limite);
      return l === undefined || l === null ? null : diasHasta(l);
    } });
    if (o.estado === undefined) o.estado = "ACTIVO";
    if (o.dependeDeMi === undefined) o.dependeDeMi = true;
    if (o.naturaleza === undefined) o.naturaleza = NATURALEZA.RIESGO;
    return o;
  }

  var ASUNTOS = [

    /* ---------------- AGENTE (PER-1) ---------------- */

    A({ asuntoId: "AS-A-01", id: "ica", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CAPTACION", entidadId: "CAP-0031", propiedad: "PROP-118",
      lado: "OFERTA", paso: 1, proc: "captacion",
      severidad: SEVERIDAD.ALTA, grupo: "expediente",
      desde: "2026-08-06", limite: null, desbloquea: true,
      titulo: function () { return dir("PROP-118"); },
      hecho: function () { return "Falta la partida registral · <b>bloquea la publicación</b>"; },
      identidad: function () { var p = prop("PROP-118"); return [nom(p.propietario), p.distrito, p.m2 + " m²"]; },
      reco: "Adjuntar la partida registral.",
      para: "La captación vuelve a revisión del broker y el local puede publicarse.",
      accion: { tipo: "archivo", et: "Seleccionar partida registral", sub: "PDF · máximo 10 MB",
        ok: "Partida adjuntada", luego: "La captación volvió a revisión del broker." },
      motivo: "bloquea una publicación.",
      destino: { n: "Ficha comercial de la captación", r: "/captaciones/CAP-0031/ficha" } }),

    A({ asuntoId: "AS-A-02", id: "arequipa", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CAPTACION", entidadId: "CAP-0022", propiedad: "PROP-1840",
      lado: "OFERTA", paso: 1, proc: "captacion",
      severidad: SEVERIDAD.ALTA, grupo: "fecha", naturaleza: NATURALEZA.OPORTUNIDAD,
      desde: "2026-08-09", limite: function () { return venceEncargo(cap("CAP-0022")); },
      desbloquea: false, ocasion: true,
      titulo: function () { return dir("PROP-1840"); },
      hecho: function () {
        var c = cap("CAP-0022");
        return "<b>Vence en " + plural(diasHasta(venceEncargo(c)), "día", "días") + "</b> · el propietario " +
          (diasDesde(c.ultimoContactoPropietario) === 1 ? "respondió ayer" : "respondió " + fhace(c.ultimoContactoPropietario));
      },
      identidad: function () {
        var c = cap("CAP-0022"), p = prop("PROP-1840");
        return [nom(p.propietario), dinero(c.rentaPropuesta), "vence " + fdia(venceEncargo(c))];
      },
      reco: "Programar la conversación con el propietario.",
      para: "Llegar al vencimiento con la renovación decidida, no negociándola ese día.",
      accion: { tipo: "fecha", et: "Programar", ok: "Conversación programada", luego: "Queda en la agenda del propietario." },
      motivo: function () { return "el encargo vence en " + plural(diasHasta(venceEncargo(cap("CAP-0022"))), "día", "días") + "."; },
      destino: { n: "Ficha comercial de la captación", r: "/captaciones/CAP-0022/ficha" } }),

    A({ asuntoId: "AS-A-03", id: "andina", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "OPORTUNIDAD", entidadId: "OPO-0111", propiedad: null,
      lado: "DEMANDA", paso: 0, proc: "oportunidad",
      severidad: SEVERIDAD.MEDIA, naturaleza: NATURALEZA.OPORTUNIDAD, grupo: "seguimiento",
      desde: "2026-08-10", limite: null, desbloquea: false, ocasion: true,
      titulo: function () { return "Cafetería · " + opo("OPO-0111").zona; },
      hecho: function () {
        var co = COINCIDENCIAS["COI-1"];
        return co.propiedades.length + " locales vuelven a encajar · falta confirmar " + co.falta.split(" ")[0];
      },
      identidad: function () {
        var o = opo("OPO-0111");
        return [nom(o.cliente), o.zona, o.m2[0] + "–" + o.m2[1] + " m²"];
      },
      reco: function () { return "Confirmar la potencia eléctrica de los " + COINCIDENCIAS["COI-1"].propiedades.length + " locales."; },
      para: "Presentar las dos opciones completas en la misma llamada.",
      accion: { tipo: "dato", et: "Potencia registrada",
        opciones: ["15 kW o más", "Menos de 15 kW", "No se puede confirmar hoy"],
        ok: "Dato registrado", luego: "Los dos locales pasan a propuesta." },
      motivo: function () { return "la coincidencia es de " + fhace(COINCIDENCIAS["COI-1"].detectada) + "."; },
      destino: { n: "Bitácora del cliente", r: "/clientes/18/contacto" } }),

    A({ asuntoId: "AS-A-04", id: "larco", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "VISITA", entidadId: "VIS-0044", propiedad: "PROP-780",
      lado: "DEMANDA", paso: 1, proc: "visita",
      severidad: SEVERIDAD.MEDIA, grupo: "fecha",
      desde: "2026-08-05", limite: "2026-08-11", desbloquea: false,
      titulo: function () { return dir("PROP-780"); },
      hecho: function () {
        var v = vis("VIS-0044");
        return "Visita " + fcuando(v.cuando).toLowerCase() + " " + fhora(v.cuando) + " · el interesado no ha confirmado";
      },
      identidad: function () {
        var v = vis("VIS-0044"), p = prop("PROP-780");
        return [nom(v.cliente), p.distrito, dinero(p.renta)];
      },
      reco: function () { return "Confirmar la visita con " + nom(vis("VIS-0044").cliente) + "."; },
      para: function () { return "No perder el horario reservado para " + fcuando(vis("VIS-0044").cuando).toLowerCase() + "."; },
      accion: { tipo: "registro", et: "Registrar contacto",
        opciones: ["Confirma la visita", "Pide otro horario", "No contesta"],
        ok: "Contacto registrado", luego: "La visita queda confirmada." },
      motivo: function () { return "la visita es " + fcuando(vis("VIS-0044").cuando).toLowerCase() + "."; },
      destino: { n: "Detalle de la oportunidad", r: "/oportunidades/98" } }),

    A({ asuntoId: "AS-A-05", id: "sur", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CLIENTE", entidadId: "CLI-3", propiedad: null,
      lado: "DEMANDA", paso: 0, proc: "cliente",
      severidad: SEVERIDAD.MEDIA, grupo: "seguimiento",
      desde: "2026-08-03",
      limite: function () { return sumarDias(opo("OPO-0064").ultimoContacto, POLITICA.recontactoDias); },
      desbloquea: false,
      titulo: function () { return nom("CLI-3"); },
      hecho: function () { return "El plazo de recontacto <b>se cumple hoy</b>"; },
      identidad: function () {
        var o = opo("OPO-0064");
        return [o.zonas.join(" y "), plural(o.requerimientosAbiertos, "requerimiento", "requerimientos")];
      },
      reco: function () { return "Llamar hoy a " + nom("CLI-3") + "."; },
      para: "Mantener el seguimiento dentro del plazo de la política comercial.",
      accion: { tipo: "registro", et: "Registrar llamada",
        opciones: ["Sigue buscando", "Pospone la búsqueda", "No contesta"],
        ok: "Llamada registrada", luego: "El recontacto queda al día." },
      motivo: "el plazo vence hoy.",
      destino: { n: "Bitácora del cliente", r: "/clientes/31/contacto" } }),

    /* Su expediente habla de encargo, propietaria y renta, y su destino es la
       ficha del propietario: es OFERTA. Estaba clasificado como cliente. */
    A({ asuntoId: "AS-A-06", id: "arenales", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CAPTACION", entidadId: "CAP-0028", propiedad: "PROP-380",
      lado: "OFERTA", paso: 1, proc: "captacion",
      severidad: SEVERIDAD.MEDIA, grupo: "seguimiento",
      desde: "2026-07-30",
      limite: function () { return sumarDias(cap("CAP-0028").ultimoContactoPropietario, POLITICA.recontactoDias); },
      desbloquea: false,
      titulo: function () { return dir("PROP-380"); },
      hecho: function () {
        var lim = sumarDias(cap("CAP-0028").ultimoContactoPropietario, POLITICA.recontactoDias);
        return "Recontacto vencido " + fhace(lim);
      },
      identidad: function () {
        var p = prop("PROP-380");
        return [nom(p.propietario), p.distrito, dinero(p.renta)];
      },
      reco: function () { return "Llamar a " + nom("PRP-3") + "."; },
      para: "Retomar el seguimiento, que está fuera de plazo.",
      accion: { tipo: "registro", et: "Registrar llamada",
        opciones: ["Mantiene el encargo", "Quiere revisar la renta", "No contesta"],
        ok: "Llamada registrada", luego: "El recontacto queda al día." },
      motivo: "el recontacto está vencido.",
      destino: { n: "Ficha del propietario", r: "/propietarios/12" } }),

    A({ asuntoId: "AS-A-07", id: "petit", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CAPTACION", entidadId: "CAP-0009", propiedad: "PROP-259",
      lado: "OFERTA", paso: 2, proc: "publicacion",
      severidad: SEVERIDAD.MEDIA, grupo: "seguimiento",
      desde: "2026-08-09", limite: null, desbloquea: false,
      titulo: function () { return dir("PROP-259"); },
      hecho: function () {
        var c = cap("CAP-0009");
        return c.visitas + " visitas sin propuesta · " + c.objeciones.precio + " mencionan el precio";
      },
      identidad: function () {
        var p = prop("PROP-259");
        return [p.distrito, p.m2 + " m²", dinero(p.renta)];
      },
      reco: "Preparar la conversación de precio con el propietario.",
      para: "Que decida con las cifras delante si ajusta la renta o la mantiene.",
      accion: { tipo: "expediente", et: "Abrir el expediente",
        nota: function () { return "Necesita las cifras de las " + cap("CAP-0009").visitas + " visitas y el histórico de renta."; } },
      motivo: function () { return cap("CAP-0009").visitas + " visitas sin propuesta."; },
      destino: { n: "Ficha comercial de la captación", r: "/captaciones/CAP-0009/ficha" } }),

    A({ asuntoId: "AS-A-08", id: "salaverry", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "OPORTUNIDAD", entidadId: "OPO-0103", propiedad: "PROP-1200",
      lado: "DEMANDA", paso: 0, proc: "oportunidad",
      severidad: SEVERIDAD.MEDIA, grupo: "expediente",
      desde: "2026-08-08", limite: null, desbloquea: false,
      titulo: function () { return dir("PROP-1200"); },
      hecho: function () { return "Pidió las condiciones " + fhace(opo("OPO-0103").condicionesPedidas); },
      identidad: function () {
        var o = opo("OPO-0103"), p = prop("PROP-1200");
        return [nom(o.cliente), p.distrito, dinero(o.rentaPedida)];
      },
      reco: "Enviar las condiciones de arrendamiento.",
      para: "Responder lo que el interesado pidió por escrito.",
      accion: { tipo: "expediente", et: "Abrir la oportunidad", nota: "Las condiciones se arman sobre el expediente." },
      motivo: "el interesado espera condiciones.",
      destino: { n: "Detalle de la oportunidad", r: "/oportunidades/103" } }),

    A({ asuntoId: "AS-A-09", id: "tacna", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "SOLICITUD", entidadId: "SOL-0121", propiedad: "PROP-455",
      lado: "DEMANDA", paso: 2, proc: "solicitud",
      severidad: SEVERIDAD.MEDIA, grupo: "expediente",
      desde: "2026-08-04", limite: null, desbloquea: false,
      titulo: function () { return dir("PROP-455"); },
      hecho: function () {
        var s = sol("SOL-0121");
        return "Falta " + (s.documentos[1] - s.documentos[0]) + " de " + s.documentos[1] + " documentos del expediente";
      },
      identidad: function () {
        var s = sol("SOL-0121"), p = prop("PROP-455");
        return [nom(s.interesado), p.distrito, dinero(s.renta)];
      },
      reco: function () { return "Pedir los estados financieros a " + nom(sol("SOL-0121").interesado) + "."; },
      para: "Completar el expediente para que el broker pueda evaluarlo.",
      accion: { tipo: "registro", et: "Registrar solicitud del documento",
        opciones: ["Lo envía hoy", "Lo envía esta semana", "No contesta"],
        ok: "Registrado", luego: "Queda anotado el compromiso de entrega." },
      motivo: "falta un documento.",
      destino: { n: "Documentos de la solicitud", r: "/solicitudes/SOL-0121/documentos" } }),

    A({ asuntoId: "AS-A-10", id: "cusco", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CAPTACION", entidadId: "CAP-0036", propiedad: "PROP-240",
      lado: "OFERTA", paso: 2, proc: "publicacion",
      severidad: SEVERIDAD.MEDIA, grupo: "expediente",
      desde: "2026-07-29", limite: null, desbloquea: false,
      titulo: function () { return dir("PROP-240"); },
      hecho: function () { return "Publicado sin fotos ni potencia"; },
      identidad: function () {
        var p = prop("PROP-240");
        return [p.distrito, p.m2 + " m²", dinero(p.renta)];
      },
      reco: "Completar la ficha del local.",
      para: "Que el local entre en las búsquedas donde hoy no aparece.",
      accion: { tipo: "expediente", et: "Abrir la ficha", nota: "Fotos y datos técnicos se cargan en la ficha del local." },
      motivo: "quedó fuera de un cruce.",
      destino: { n: "Ficha del local", r: "/locales/140" } }),

    A({ asuntoId: "AS-A-11", id: "huaylas", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "CAPTACION", entidadId: "CAP-0041", propiedad: "PROP-1120",
      lado: "OFERTA", paso: 2, proc: "publicacion",
      severidad: SEVERIDAD.MEDIA, grupo: "seguimiento",
      desde: "2026-07-11", limite: null, desbloquea: false,
      titulo: function () { return dir("PROP-1120"); },
      hecho: function () {
        return "Un mes publicado y ninguna visita";
      },
      identidad: function () {
        var p = prop("PROP-1120");
        return [nom(p.propietario), p.distrito, p.m2 + " m²"];
      },
      reco: "Revisar la renta con el propietario.",
      para: "Entender por qué el local no recibe visitas.",
      accion: { tipo: "expediente", et: "Abrir el local", nota: "La revisión de renta se hace sobre el histórico del inmueble." },
      motivo: "un mes sin visitas.",
      destino: { n: "Ficha del local", r: "/locales/132" } }),

    /* FUERA DEL FOCO — la prospeccion de Av. Brasil 900 se cerro con
       captacion el 6 ago. No se borra: deja de competir. */
    A({ asuntoId: "AS-A-12", id: "brasil", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "PROSPECCION", entidadId: "PRO-0022", propiedad: "PROP-900",
      lado: "OFERTA", paso: 0, proc: "prospeccion",
      severidad: SEVERIDAD.MEDIA, grupo: "seguimiento",
      desde: "2026-08-01", limite: null, desbloquea: false,
      estado: "FUERA_DEL_FOCO", dependeDeMi: false,
      salida: function () {
        var p = PROSPECCIONES["PRO-0022"];
        return "La prospección se cerró con captación el " + fdia(p.fechaDesenlace) +
          " · " + p.captacion + " espera decisión del broker.";
      },
      titulo: function () { return dir("PROP-900"); },
      hecho: function () { return "Cerrada con captación · ahora decide el broker"; },
      identidad: function () {
        var p = prop("PROP-900");
        return [nom(p.propietario), p.distrito, p.m2 + " m²"];
      },
      reco: "Nada que hacer aquí.",
      para: "La captación está en revisión del broker.",
      accion: { tipo: "expediente", et: "Abrir la captación", nota: "La decisión es del broker." },
      motivo: "cerrada con captación.",
      destino: { n: "Ficha comercial de la captación", r: "/captaciones/CAP-0034/ficha" } }),

    /* FUERA DEL FOCO — la solicitud de Petit Thouars quedo completa y paso al
       broker. Es ESTE asunto el que «sale de tu foco», no el inmueble: el
       AS-A-07 del mismo local sigue vivo y con otra causa. */
    A({ asuntoId: "AS-A-13", id: "solicitud-petit", visto: "AGENTE", dueno: "PER-1",
      entidadTipo: "SOLICITUD", entidadId: "SOL-0114", propiedad: "PROP-259",
      lado: "DEMANDA", paso: 2, proc: "solicitud",
      severidad: SEVERIDAD.MEDIA, grupo: "expediente",
      desde: "2026-07-28", limite: null, desbloquea: false,
      estado: "FUERA_DEL_FOCO", dependeDeMi: false,
      salida: function () {
        return "Expediente completo " + fhace(sol("SOL-0114").completa) + " · ahora decide el broker.";
      },
      titulo: function () { return "Solicitud de " + nom(sol("SOL-0114").interesado); },
      hecho: function () { return "Expediente completo · <b>ahora decide el broker</b>"; },
      identidad: function () {
        var s = sol("SOL-0114"), p = prop("PROP-259");
        return [nom(s.interesado), dir("PROP-259"), dinero(s.renta)];
      },
      reco: "Nada que hacer aquí.",
      para: "La evaluación es del broker.",
      accion: { tipo: "expediente", et: "Abrir la solicitud", nota: "La evaluación de solicitud es del broker." },
      motivo: "ya decide el broker.",
      destino: { n: "Detalle de la solicitud", r: "/solicitudes/SOL-0114" } }),

    /* ---------------- BROKER (PER-2) ---------------- */

    A({ asuntoId: "AS-B-01", id: "sol", visto: "BROKER", dueno: "PER-2",
      entidadTipo: "SOLICITUD", entidadId: "SOL-0114", propiedad: "PROP-259",
      lado: "DEMANDA", paso: 2, proc: "solicitud",
      severidad: SEVERIDAD.ALTA, grupo: "expediente",
      desde: "2026-08-08", limite: function () { return sol("SOL-0114").fechaComprometida; },
      desbloquea: true,
      titulo: function () { return "Solicitud de " + nom(sol("SOL-0114").interesado); },
      hecho: function () {
        return "Expediente completo " + fhace(sol("SOL-0114").completa) + " · <b>solo falta tu decisión</b>";
      },
      identidad: function () {
        var s = sol("SOL-0114"), p = prop("PROP-259");
        return [dir("PROP-259"), p.distrito, dinero(s.renta)];
      },
      reco: "Evaluar la solicitud y responder.",
      para: "La operación no avanza hasta que decidas. Si la apruebas, pasa a contrato.",
      accion: { tipo: "expediente", et: "Abrir la evaluación",
        nota: "La evaluación de solicitud es una operación completa, no un control rápido." },
      motivo: "solo falta tu decisión.",
      destino: { n: "Evaluación de la solicitud", r: "/solicitudes/SOL-0114/evaluar" } }),

    A({ asuntoId: "AS-B-02", id: "brasil-b", visto: "BROKER", dueno: "PER-2",
      entidadTipo: "CAPTACION", entidadId: "CAP-0034", propiedad: "PROP-900",
      lado: "OFERTA", paso: 1, proc: "captacion",
      severidad: SEVERIDAD.ALTA, grupo: "expediente",
      desde: "2026-08-06", limite: null, desbloquea: true,
      titulo: function () { return dir("PROP-900"); },
      hecho: function () {
        var c = cap("CAP-0034");
        return plural(diasDesde(c.registrada), "día", "días") + " esperando decisión · <b>bloquea a " +
          nom(c.agente).split(" ")[0] + "</b>";
      },
      identidad: function () {
        var c = cap("CAP-0034"), p = prop("PROP-900");
        return [nom(c.agente), p.distrito, p.m2 + " m²", dinero(c.rentaPropuesta)];
      },
      reco: "Revisar la captación y decidir.",
      para: "Sin tu decisión el local no se publica y la agente no puede abrir oportunidades.",
      accion: { tipo: "expediente", et: "Abrir la captación",
        nota: "La decisión sobre una captación es del broker y se registra en su expediente." },
      motivo: "bloquea a una agente.",
      destino: { n: "Revisión de la captación", r: "/captaciones/CAP-0034/revisar" } }),

    /* La concentracion NO se escribe: se lee de la distribucion de cartera. */
    A({ asuntoId: "AS-B-03", id: "carga", visto: "BROKER", dueno: "PER-2",
      entidadTipo: "AGENTE", entidadId: null, propiedad: null,
      lado: "OFERTA", paso: 0, proc: "prospeccion",
      severidad: SEVERIDAD.MEDIA, grupo: "seguimiento",
      desde: "2026-08-04", limite: null, desbloquea: false,
      titulo: function () { return "Carga de " + concentracionCartera().nombre; },
      hecho: function () {
        var c = concentracionCartera();
        return "Concentra el <b>" + c.porcentaje + " %</b> de la cartera del equipo";
      },
      identidad: function () {
        var c = concentracionCartera();
        return ["Equipo", AGENTES.length + " agentes", c.total + " locales en cartera"];
      },
      reco: function () { return "Revisar el reparto de cartera con " + concentracionCartera().nombre.split(" ")[0] + "."; },
      para: "Que la concentración no se convierta en seguimientos vencidos.",
      accion: { tipo: "registro", et: "Registrar acuerdo",
        opciones: ["Se redistribuyen 6 locales", "Mantiene la carga", "Se revisa la próxima semana"],
        ok: "Acuerdo registrado", luego: "Queda anotado en el seguimiento del equipo." },
      motivo: function () { var c = concentracionCartera(); return "un agente concentra el " + c.porcentaje + " %."; },
      destino: { n: "Mi equipo", r: "/mi-equipo" } }),

    A({ asuntoId: "AS-B-04", id: "arequipa-b", visto: "BROKER", dueno: "PER-2",
      entidadTipo: "CAPTACION", entidadId: "CAP-0022", propiedad: "PROP-1840",
      lado: "OFERTA", paso: 1, proc: "captacion",
      severidad: SEVERIDAD.ALTA, grupo: "fecha",
      desde: "2026-08-09", limite: function () { return venceEncargo(cap("CAP-0022")); },
      desbloquea: false,
      titulo: function () { return dir("PROP-1840"); },
      hecho: function () {
        var c = cap("CAP-0022");
        return "<b>Vence en " + plural(diasHasta(venceEncargo(c)), "día", "días") + "</b> · el reporte lo envía la agente";
      },
      identidad: function () {
        var c = cap("CAP-0022");
        return [nom(c.agente), c.exclusivo ? "encargo exclusivo" : "encargo", "vence " + fdia(venceEncargo(c))];
      },
      reco: function () { return "Acordar con " + nom(cap("CAP-0022").agente).split(" ")[0] + " la actualización al propietario."; },
      para: "Que el reporte salga antes de la fecha, no después.",
      accion: { tipo: "fecha", et: "Programar", ok: "Acuerdo programado", luego: "Queda en la agenda con la agente." },
      motivo: function () { return "el encargo vence en " + plural(diasHasta(venceEncargo(cap("CAP-0022"))), "día", "días") + "."; },
      destino: { n: "Ficha comercial de la captación", r: "/captaciones/CAP-0022/ficha" } }),

    A({ asuntoId: "AS-B-05", id: "comision", visto: "BROKER", dueno: "PER-2",
      entidadTipo: "CONTRATO", entidadId: "CON-0071", propiedad: "PROP-COL",
      lado: "DEMANDA", paso: 3, proc: "comision",
      severidad: SEVERIDAD.MEDIA, grupo: "expediente",
      desde: "2026-07-24", limite: null, desbloquea: false,
      titulo: function () { return "Comisión de " + dir("PROP-COL"); },
      hecho: function () {
        var c = con("CON-0071");
        return plural(diasDesde(c.firmado), "día", "días") + " desde la firma · el plazo habitual son " +
          POLITICA.plazoCobroComisionDias;
      },
      identidad: function () {
        var c = con("CON-0071");
        return [nom(c.cliente), dinero(c.comision.devengada), "sin cobrar"];
      },
      reco: "Confirmar la fecha de pago con el cliente.",
      para: "Poner la comisión en el próximo ciclo de cobro.",
      accion: { tipo: "fecha", et: "Registrar fecha de pago", ok: "Fecha registrada", luego: "La comisión entra en el ciclo de cobro." },
      motivo: "pasó el plazo de cobro.",
      destino: { n: "Comisiones", r: "/comisiones" } })
  ];

  /* Irrupciones: lo urgente pregunta, no se cuela. */
  var IRRUPCIONES = {
    AGENTE: {
      texto: function () { return nom("CLI-7") + " respondió sobre " + dir("PROP-259") + "."; },
      asunto: A({ asuntoId: "AS-A-90", id: "mejia", visto: "AGENTE", dueno: "PER-1",
        entidadTipo: "OPORTUNIDAD", entidadId: "OPO-0087", propiedad: "PROP-259",
        lado: "DEMANDA", paso: 0, proc: "oportunidad",
        severidad: SEVERIDAD.MEDIA, naturaleza: NATURALEZA.OPORTUNIDAD, grupo: "seguimiento",
        desde: "2026-08-10", limite: "2026-08-10", desbloquea: false, ocasion: true,
        titulo: function () { return nom("CLI-7") + " · " + dir("PROP-259"); },
        hecho: function () {
          var o = opo("OPO-0087");
          return "Respondió " + fhace(o.reabierta) + " tras " +
            Math.round(diasDesde(o.cerrada) / 30) + " meses";
        },
        identidad: function () { return [prop("PROP-259").distrito, "reabierta por la bajada de precio"]; },
        reco: "Llamarlo ahora.",
        para: "Aprovechar que acaba de escribir para saber si sigue buscando.",
        accion: { tipo: "registro", et: "Registrar llamada",
          opciones: ["Sigue buscando", "Ya no busca", "No contesta"],
          ok: "Llamada registrada", luego: "La oportunidad queda actualizada." },
        motivo: "acaba de responder.",
        destino: { n: "Detalle de la oportunidad", r: "/oportunidades/87" } })
    },
    BROKER: {
      texto: function () {
        return "Una solicitud aprobada lleva " + plural(diasDesde(sol("SOL-0108").aprobada), "día", "días") + " sin contrato.";
      },
      asunto: A({ asuntoId: "AS-B-90", id: "urgente-b", visto: "BROKER", dueno: "PER-2",
        entidadTipo: "SOLICITUD", entidadId: "SOL-0108", propiedad: "PROP-240",
        lado: "DEMANDA", paso: 2, proc: "solicitud",
        severidad: SEVERIDAD.ALTA, grupo: "expediente",
        desde: "2026-08-04", limite: "2026-08-10", desbloquea: true,
        titulo: function () { return "Solicitud aprobada sin contrato"; },
        hecho: function () {
          return "Aprobada " + fhace(sol("SOL-0108").aprobada) + " · el contrato no se ha generado";
        },
        identidad: function () { return [dir("PROP-240"), nom(sol("SOL-0108").interesado)]; },
        reco: "Revisar por qué el contrato no se generó.",
        para: "Una solicitud aprobada sin contrato bloquea la comisión.",
        accion: { tipo: "expediente", et: "Abrir la solicitud", nota: "El contrato se genera desde el expediente de la solicitud." },
        motivo: "aprobada y sin contrato.",
        destino: { n: "Detalle de la solicitud", r: "/solicitudes/SOL-0108" } })
    }
  };

  /* `v(x)` resuelve un campo que puede ser valor o funcion: los textos con
     fechas tienen que evaluarse contra FECHA_REF, no congelarse al cargar. */
  function v(x) { return typeof x === "function" ? x() : x; }

  function asuntosDe(rol, opciones) {
    var o = opciones || {};
    return ASUNTOS.filter(function (a) {
      if (a.visto !== rol) return false;
      if (o.soloEstos && o.soloEstos.indexOf(a.id) === -1) return false;
      return true;
    });
  }
  function asuntosActivos(rol, opciones) {
    return asuntosDe(rol, opciones).filter(function (a) {
      return a.estado === "ACTIVO" && a.dependeDeMi;
    });
  }
  function asuntoPorId(id) {
    for (var i = 0; i < ASUNTOS.length; i++) if (ASUNTOS[i].id === id) return ASUNTOS[i];
    if (IRRUPCIONES.AGENTE.asunto.id === id) return IRRUPCIONES.AGENTE.asunto;
    if (IRRUPCIONES.BROKER.asunto.id === id) return IRRUPCIONES.BROKER.asunto;
    return null;
  }

  /* ------------------------------------------------------------------
     13 · POLITICA DE DESPACHO — determinista, nunca visible como score
       1 · solo compite lo que depende de mi y esta activo
       2 · ventana temporal: menos margen, mas peso
       3 · ventana de oportunidad: algo acaba de moverse
       4 · desbloqueo: resolverlo permite continuar un proceso
       5 · antiguedad accionable con tope
       6 · estabilidad: ante empate se conserva el orden anterior
     ------------------------------------------------------------------ */

  var MAX_FOCO = 5;

  function pesoAsunto(a, ajuste) {
    var ventana = ajuste && ajuste.ventana !== undefined ? ajuste.ventana : a.ventana;
    var p = 0;
    if (ventana !== null && ventana !== undefined) p += Math.max(0, 26 - ventana * 2);
    if (a.ocasion) p += 30;
    if (a.desbloquea) p += 30;
    p += Math.min(12, a.espera);
    return p;
  }

  function despacho(lista, ordenPrevio, ajustes) {
    var prev = ordenPrevio || [];
    return lista.slice().sort(function (x, y) {
      var d = pesoAsunto(y, ajustes && ajustes[y.id]) - pesoAsunto(x, ajustes && ajustes[x.id]);
      if (d !== 0) return d;
      var ix = prev.indexOf(x.id), iy = prev.indexOf(y.id);
      return (ix === -1 ? 99 : ix) - (iy === -1 ? 99 : iy);
    });
  }

  /* Los tres primeros del foco son las acciones que Indicadores propone: la
     misma politica, la misma lista, los mismos nombres. */
  function accionesDe(rol, opciones, n) {
    return despacho(asuntosActivos(rol, opciones)).slice(0, n || 3).map(function (a) {
      return {
        asuntoId: a.asuntoId, id: a.id, titulo: v(a.titulo),
        prioridad: a.severidad === SEVERIDAD.ALTA ? "Alta" : "Media",
        detalle: v(a.hecho).replace(/<\/?b>/g, ""),
        entidad: a.entidadTipo + (a.entidadId ? " " + a.entidadId : "")
      };
    });
  }

  /* ------------------------------------------------------------------
     14 · AGENDA — un evento, un id, una hora
     Todo lo que tiene fecha vive aqui. Si un evento ya es un asunto del
     foco, el Radar deja de repetirlo como fecha suelta y enlaza a su numero
     (regla del hogar unico, D-E2-1 §11).
     ------------------------------------------------------------------ */

  var AGENDA = [
    { id: "EV-01", tipo: "VISITA", rotulo: "Visita", cuando: function () { return vis("VIS-0044").cuando; },
      entidadTipo: "VISITA", entidadId: "VIS-0044", proc: "visita",
      detalle: function () { var v_ = vis("VIS-0044"); return dir(v_.propiedad) + " · " + fhora(v_.cuando); },
      quien: "PER-1", ve: ["AGENTE", "BROKER"], asunto: { AGENTE: "larco" } },

    { id: "EV-02", tipo: "REPORTE", rotulo: "Reporte", cuando: "2026-08-12",
      entidadTipo: "CAPTACION", entidadId: "CAP-0028", proc: "captacion",
      detalle: function () { return dir("PROP-380"); },
      quien: "PER-1", ve: ["AGENTE"], asunto: { AGENTE: "arenales" } },

    { id: "EV-03", tipo: "DECISION", rotulo: "Decisión", cuando: function () { return sol("SOL-0114").fechaComprometida; },
      entidadTipo: "SOLICITUD", entidadId: "SOL-0114", proc: "solicitud",
      detalle: function () { return dir("PROP-259") + " · la toma el broker"; },
      quien: "PER-2", ve: ["AGENTE"], asunto: {} },

    { id: "EV-04", tipo: "DECISION", rotulo: "Decisión", cuando: function () { return sol("SOL-0114").fechaComprometida; },
      entidadTipo: "SOLICITUD", entidadId: "SOL-0114", proc: "solicitud",
      detalle: function () { return dir("PROP-259") + " · comprometida con la interesada"; },
      quien: "PER-2", ve: ["BROKER"], asunto: { BROKER: "sol" } },

    { id: "EV-05", tipo: "VENCIMIENTO", rotulo: "Vence", cuando: function () { return venceEncargo(cap("CAP-0022")); },
      entidadTipo: "CAPTACION", entidadId: "CAP-0022", proc: "captacion",
      detalle: function () { return dir("PROP-1840"); },
      quien: "PER-1", ve: ["AGENTE", "BROKER"], asunto: { AGENTE: "arequipa", BROKER: "arequipa-b" } },

    { id: "EV-06", tipo: "VISITA", rotulo: "Visita", cuando: function () { return vis("VIS-0051").cuando; },
      entidadTipo: "VISITA", entidadId: "VIS-0051", proc: "visita",
      detalle: function () { var v_ = vis("VIS-0051"); return dir(v_.propiedad) + " · " + fhora(v_.cuando); },
      quien: "PER-1", ve: ["AGENTE"], asunto: {} },

    { id: "EV-07", tipo: "REVISION", rotulo: "Revisión", cuando: "2026-08-12",
      entidadTipo: "CAPTACION", entidadId: "CAP-0231", proc: "captacion",
      detalle: function () { return dir("PROP-CAM") + " · captación de " + nom("PER-3"); },
      quien: "PER-2", ve: ["BROKER"], asunto: {} },

    { id: "EV-08", tipo: "VENCIMIENTO", rotulo: "Vence", cuando: function () { return venceEncargo(cap("CAP-0028")); },
      entidadTipo: "CAPTACION", entidadId: "CAP-0028", proc: "captacion",
      detalle: function () { return dir("PROP-380") + " · " + nom("PER-1"); },
      quien: "PER-1", ve: ["BROKER"], asunto: {} },

    { id: "EV-09", tipo: "DECISION", rotulo: "Decisión", cuando: "2026-08-11",
      entidadTipo: "SOLICITUD", entidadId: "SOL-0082", proc: "solicitud",
      detalle: function () { return dir("PROP-PAR") + " · vence tu plazo"; },
      quien: "PER-2", ve: ["BROKER"], asunto: {} }
  ];

  function agendaDe(rol) {
    return AGENDA.filter(function (e) { return e.ve.indexOf(rol) !== -1; })
      .map(function (e) {
        return {
          id: e.id, tipo: e.tipo, rotulo: e.rotulo, proc: e.proc,
          cuando: v(e.cuando), etiqueta: fcuando(v(e.cuando)),
          detalle: v(e.detalle), entidadTipo: e.entidadTipo, entidadId: e.entidadId,
          asunto: e.asunto || {}
        };
      })
      .sort(function (a, b) { return soloDia(a.cuando) - soloDia(b.cuando); });
  }
  /* El proximo evento, para «día cubierto» y para el `proximo` de un asunto. */
  function proximoEvento(rol, filtro) {
    var l = agendaDe(rol).filter(function (e) { return diasHasta(e.cuando) >= 0 && (!filtro || filtro(e)); });
    return l.length ? l[0] : null;
  }
  function proximoDeAsunto(a) {
    var l = agendaDe(a.visto).filter(function (e) {
      return diasHasta(e.cuando) >= 0 &&
        ((e.entidadId && e.entidadId === a.entidadId) ||
         (e.asunto && e.asunto[a.visto] === a.id));
    });
    if (!l.length) return null;
    var e = l[0];
    return e.etiqueta + " · " + e.rotulo.toLowerCase() + " · " + e.detalle;
  }

  /* ------------------------------------------------------------------
     15 · QUE CAMBIO — movimientos que alteran de quien depende algo
     ------------------------------------------------------------------ */

  var CAMBIOS = [
    { cuando: function () { return sol("SOL-0114").completa; }, ve: ["AGENTE", "BROKER"],
      que: function () { return nom(sol("SOL-0114").interesado) + " entregó los documentos que faltaban."; },
      detalle: {
        /* Nombra el ASUNTO que se mueve, no la direccion: el inmueble tiene
           otro asunto vivo y decir «sale de tu cola» a secas se contradice. */
        AGENTE: function () {
          return "La solicitud de " + dir("PROP-259") + " <b>sale de tu foco</b>: ahora decide el broker. " +
            "El precio del local sigue en tu foco, que es otro asunto.";
        },
        BROKER: function () { return dir("PROP-259") + " · <b>encabeza tu lista</b> desde entonces."; }
      } },
    { cuando: function () { return cap("CAP-0037").aprobada; }, ve: ["AGENTE", "BROKER"],
      que: {
        AGENTE: function () { return "El broker aprobó tu captación de " + dir("PROP-410") + "."; },
        BROKER: function () { return dir("PROP-410") + " quedó publicado."; }
      },
      detalle: {
        AGENTE: function () { return "El local ya está publicado."; },
        BROKER: function () { return "Captación de " + nom(cap("CAP-0037").agente) + "."; }
      } },
    { cuando: function () { return PROSPECCIONES["PRO-0022"].fechaDesenlace; }, ve: ["AGENTE"],
      que: function () { return "La prospección de " + dir("PROP-900") + " se cerró con captación."; },
      detalle: { AGENTE: function () { return "Sale de tu foco: " + PROSPECCIONES["PRO-0022"].captacion + " espera decisión del broker."; } } },
    { cuando: function () { return vis("VIS-0039").cuando; }, ve: ["AGENTE"],
      que: function () { return "Se hizo la visita en " + dir("PROP-259") + "."; },
      detalle: { AGENTE: function () { return "La " + cap("CAP-0009").visitas + ".ª; ninguna llegó a propuesta."; } } },
    { cuando: function () { return cap("CAP-0022").ultimoContactoPropietario; }, ve: ["AGENTE"],
      que: function () { return nom(prop("PROP-1840").propietario) + ", de " + dir("PROP-1840") + ", devolvió la llamada."; },
      detalle: { AGENTE: function () { return "Está disponible esta semana."; } } },
    { cuando: "2026-08-09", ve: ["BROKER"],
      que: function () { return nom("PER-3") + " registró su tercera captación del mes."; },
      detalle: {} },
    { cuando: "2026-08-09", ve: ["BROKER"],
      que: function () { return nom("PER-6") + " no registró actividad."; },
      detalle: { BROKER: function () { return "Segundo día seguido."; } } }
  ];

  function cambiosDe(rol) {
    return CAMBIOS.filter(function (c) { return c.ve.indexOf(rol) !== -1; })
      .map(function (c) {
        var cuando = v(c.cuando);
        return {
          cuando: cuando, etiqueta: fhace(cuando).replace(/^hace /, ""),
          que: v(typeof c.que === "object" ? c.que[rol] : c.que),
          detalle: c.detalle && c.detalle[rol] ? v(c.detalle[rol]) : null
        };
      })
      .sort(function (a, b) { return aFecha(b.cuando) - aFecha(a.cuando); });
  }

  /* ------------------------------------------------------------------
     16 · HALLAZGOS — el mismo motor, otra salida
     ------------------------------------------------------------------ */

  function hallazgoDe(rol) {
    if (rol === "AGENTE") {
      var co = COINCIDENCIAS["COI-1"], o = opo(co.requerimiento);
      return {
        q: co.propiedades.length + " locales vuelven a encajar con Cafetería · " + o.zona + ".",
        c: "Coinciden " + co.criterios.slice(0, -1).join(", ") + " y " + co.criterios[co.criterios.length - 1] +
          ". <b>Falta confirmar la " + co.falta + ".</b>",
        asunto: { AGENTE: "andina" }
      };
    }
    /* El hallazgo del broker no es una coincidencia de cartera: es la
       DISPERSION que la media del equipo esconde. Y nombra su concepto: un
       porcentaje suelto no es una identidad. */
    var kpi = kpiDe({ tipo: "EQUIPO" }, "solicitud", PERIODO_INICIO);
    var l = conversionPorAgente("solicitud").filter(function (x) { return x.fiable; });
    var peor = l[0], resto = l.slice(1);
    var min = Math.min.apply(null, resto.map(function (x) { return x.porcentaje; }));
    var max = Math.max.apply(null, resto.map(function (x) { return x.porcentaje; }));
    return {
      q: "El cuello del equipo está concentrado en un agente.",
      c: "<b>" + kpi.conversion.porcentaje + " % de las visitas llegan a solicitud</b> en el equipo. " +
        peor.nombre + " va al " + peor.porcentaje + " %; el resto, entre " + min + " % y " + max + " %.",
      asunto: { BROKER: "carga" }
    };
  }

  /* ------------------------------------------------------------------
     17 · «PUEDE CERRARSE ESTE MES»
     Renta mensual de las operaciones que pueden firmarse en el periodo.
     Sigue sin ser una metrica de D-E2-2 (§13) y se marca como tal.
     ------------------------------------------------------------------ */

  var CIERRE_POSIBLE = {
    AGENTE: { operaciones: 3, serie: [4.2, 5.1, 4.8, 6.4, 7.9, 9.3] },
    BROKER: { operaciones: 11, esperanDecision: 3, serie: [28, 31, 29, 36, 38, 41.2] }
  };
  function puedeCerrarse(rol) {
    var c = CIERRE_POSIBLE[rol];
    var valor = rol === "AGENTE" ? 9300 : 41200;
    return {
      rotulo: "Puede cerrarse este mes",
      valor: dinero(valor),
      detalle: rol === "AGENTE"
        ? plural(c.operaciones, "operación", "operaciones") + " · renta mensual"
        : plural(c.operaciones, "operación", "operaciones") + " · <b>" + c.esperanDecision + " esperan tu decisión</b>",
      serie: c.serie
    };
  }

  /* ------------------------------------------------------------------
     17 bis · EL EXPEDIENTE, CÓMO ESTÁ Y LA LECTURA
     Tres capas de interpretacion, y ninguna escribe un numero de dias: todas
     llaman a `diasDesde` / `diasHasta`. Formatos:
       fila      [rotulo, valor, estado|null, grafico|null, contraste|null]
       estado    "bien" | "ojo" | "mal"   (ausente = historial, sin color)
       grafico   {cuenta:[hechos,total]} · {plazo:{transcurridos,previsto}} · {serie:[...]}
       contraste {r:[min,max,valor], t} · {d:"...", t:"..."}
     ------------------------------------------------------------------ */

  function marca(txt) { return "<mark>" + txt + "</mark>"; }
  function sinCambios(p) { return plural(diasDesde(p.rentaDesde), "día", "días") + " sin cambios"; }
  function rango(p) { return { r: [p.rango[0], p.rango[1], p.renta], t: "del rango de " + p.rangoDe + " · " + p.rango[0].toLocaleString("en-US") + "–" + p.rango[1].toLocaleString("en-US") }; }

  var EXPEDIENTES = {

    ica: function () {
      var c = cap("CAP-0031"), p = prop("PROP-118");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · 6 meses · vence el " + fdia(venceEncargo(c)),
          null, { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(c.rentaPropuesta) + " propuesta · " + marca("aún sin publicar"), "ojo", null, rango(p)],
        ["Revisión", "Devuelta el " + fdia(c.fechaRevision) + " · " + c.observaciones + " observaciones, " +
          marca(c.observacionesResueltas + " resuelta"), "bien", { cuenta: [c.observacionesResueltas, c.observaciones] }],
        ["Propietario", nom(p.propietario) + " · " + marca("sin reportes enviados"), "ojo"]
      ];
    },

    arequipa: function () {
      var c = cap("CAP-0022"), p = prop("PROP-1840");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · exclusivo · " + c.plazoDias + " días",
          "mal", { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(p.renta) + " desde el " + fdia(p.rentaDesde) + " · " + marca(sinCambios(p)),
          "ojo", { serie: p.serieRenta }, rango(p)],
        ["Actividad", c.visitas + " visitas · " + marca(c.propuestas + " propuestas") + " · última el " + fdia(c.ultimaVisita),
          "mal", null, { d: c.propuestas + " de " + c.visitas, t: "tu media es 1 propuesta cada 3 visitas" }],
        ["Propietario", nom(p.propietario) + " · " + marca(plural(diasDesde(c.ultimoReporte), "día", "días") + " sin reporte"), "ojo"]
      ];
    },

    andina: function () {
      var o = opo("OPO-0111"), co = COINCIDENCIAS["COI-1"];
      return [
        ["Requerimiento", "Registrado " + marca(fcuando(o.abierta).toLowerCase() + " " + fhora(o.abierta)) + " · rubro " + o.rubro, "bien"],
        ["Presupuesto", "Hasta " + dinero(o.presupuesto[1]) + " · " + marca(o.m2[0] + "–" + o.m2[1] + " m² en " + o.zona),
          "bien", null, { d: "12 locales", t: "de tu cartera entran en ese presupuesto y esa zona" }],
        ["Coincidencias", co.propiedades.length + " locales · " + marca("falta comprobar la " + co.falta),
          "ojo", { cuenta: [co.comprobados, co.total] }],
        ["Cliente", nom(o.cliente) + " · " + marca("primer requerimiento"), "bien"]
      ];
    },

    larco: function () {
      var o = opo("OPO-0098"), p = prop("PROP-780");
      return [
        ["Oportunidad", "Abierta el <b>" + fdia(o.abierta) + "</b> · " + marca(plural(diasDesde(o.abierta), "día", "días") + " en curso"), "ojo"],
        ["Renta", dinero(o.rentaPedida) + " · " + marca("dentro de su rango"), "bien", null,
          { r: [o.presupuesto[0], o.presupuesto[1], o.rentaPedida], t: "del presupuesto de " + nom(o.cliente) + " · " + o.presupuesto[0].toLocaleString("en-US") + "–" + o.presupuesto[1].toLocaleString("en-US") }],
        ["Actividad", o.visitas + " visita · " + marca("sin propuesta en " + plural(diasDesde(o.abierta), "día", "días")), "ojo"],
        ["Cliente", nom(o.cliente) + " · " + o.contactos + " contactos · " + marca("responde siempre"), "bien"]
      ];
    },

    sur: function () {
      var o = opo("OPO-0064");
      return [
        ["Cliente", "Alta el <b>" + fdia(o.abierta) + "</b> · " + marca(plural(o.requerimientosAbiertos, "requerimiento abierto", "requerimientos abiertos")),
          "bien", null, { d: "5 clientes", t: "tuyos tienen más de un requerimiento abierto; este tiene tres" }],
        ["Presupuesto", dinero(o.presupuesto[0]) + "–" + o.presupuesto[1].toLocaleString("en-US") + " · " + marca("dos zonas donde tienes cartera"), "bien"],
        ["Actividad", o.contactos + " contactos · " + o.visitas + " visitas · " + marca(o.propuestas + " propuestas"),
          "mal", null, { d: o.contactos + " contactos", t: "tu media hasta la primera propuesta son 4" }],
        ["Último contacto", fdia(o.ultimoContacto) + " · revisaron dos zonas", "ojo",
          { plazo: { transcurridos: diasDesde(o.ultimoContacto), previsto: POLITICA.recontactoDias } }]
      ];
    },

    arenales: function () {
      var c = cap("CAP-0028"), p = prop("PROP-380");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · vence el " + fdia(venceEncargo(c)),
          "ojo", { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(p.renta) + " desde el alta · " + marca("nunca se ajustó"), "ojo", null, rango(p)],
        ["Actividad", c.visitas + " visitas · " + marca(c.propuestasRechazadas + " propuesta rechazada"), "mal"],
        ["Propietaria", nom(p.propietario) + " · " + marca("último contacto el " + fdia(c.ultimoContactoPropietario)),
          "mal", null, { d: plural(diasDesde(c.ultimoContactoPropietario), "día", "días"),
            t: "el plazo de recontacto de tu organización son " + POLITICA.recontactoDias }]
      ];
    },

    petit: function () {
      var c = cap("CAP-0009"), p = prop("PROP-259");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · 6 meses · vence el " + fdia(venceEncargo(c)),
          null, { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(p.renta) + " desde el " + fdia(p.rentaDesde) + " · " + marca(sinCambios(p)),
          "ojo", { serie: p.serieRenta }, rango(p)],
        ["Actividad", marca(c.visitas + " visitas") + " en " + plural(diasDesde(cap("CAP-0009").visitasDesde), "día", "días") + " · " + c.propuestas + " propuestas", "bien"],
        ["Objeciones", "Precio en " + c.objeciones.precio + " de " + c.objeciones.de + " visitas · metraje en " + c.objeciones.metraje,
          "mal", { cuenta: [c.objeciones.precio, c.objeciones.de] },
          { d: pc(c.objeciones.precio, c.objeciones.de) + " %", t: "en tu cartera la objeción de precio aparece en el 22 %" }]
      ];
    },

    salaverry: function () {
      var o = opo("OPO-0103"), p = prop("PROP-1200");
      return [
        ["Oportunidad", "Abierta el <b>" + fdia(o.abierta) + "</b> · " + plural(diasDesde(o.abierta), "día", "días") + " en curso"],
        ["Renta", dinero(o.rentaPedida) + " pedida · " + marca("sin contraoferta"), "ojo", null, rango(p)],
        ["Actividad", o.visitas + " visitas · " + marca("condiciones pedidas el " + fdia(o.condicionesPedidas)), "bien"],
        ["Cliente", nom(o.cliente) + " · " + marca("decide en " + o.decideEnSemanas + " semanas"), "ojo"]
      ];
    },

    tacna: function () {
      var s = sol("SOL-0121"), p = prop("PROP-455");
      return [
        ["Solicitud", "Creada el <b>" + fdia(s.creada) + "</b> · " + plural(diasDesde(s.creada), "día", "días") + " en curso"],
        ["Renta", dinero(s.renta) + " · " + marca("contrato a " + s.plazoMeses + " meses"), "bien", null, rango(p)],
        ["Expediente", "Último documento el " + marca(fdia(s.ultimoDocumento)) + " · " +
          plural(diasDesde(s.ultimoDocumento), "día", "días") + " sin avanzar", "ojo",
          { cuenta: s.documentos },
          { d: plural(diasDesde(s.ultimoDocumento), "día", "días"), t: "tus solicitudes completan el expediente en 4 de media" }],
        ["Interesado", nom(s.interesado) + " · " + marca(s.visitasPrevias + " visitas previas"), "bien"]
      ];
    },

    brasil: function () {
      var pr = PROSPECCIONES["PRO-0022"], c = cap("CAP-0034"), p = prop("PROP-900");
      return [
        ["Prospección", "Abierta el <b>" + fdia(pr.abierta) + "</b> · " + marca("cerrada el " + fdia(pr.fechaDesenlace) + " con captación"), "bien"],
        ["Renta", dinero(c.rentaPropuesta) + " propuesta · " + marca("el mayor de tu cartera"), "bien", null, rango(p)],
        ["Actividad", pr.intentos + " intentos · propuesta enviada el " + fdia(pr.propuestaEnviada), null],
        ["Propietario", nom(p.propietario) + " · " + marca("aceptó el encargo"), "bien"]
      ];
    },

    cusco: function () {
      var c = cap("CAP-0036"), p = prop("PROP-240");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · vence el " + fdia(venceEncargo(c)),
          null, { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(p.renta) + " publicada el " + fdia(c.publicada) + " · " + marca("bajo el rango"), "bien", null, rango(p)],
        ["Actividad", c.vistasFicha + " vistas de la ficha · " + marca(c.visitas + " visitas"), "mal", null,
          { d: "0 %", t: "tus fichas completas convierten el 11 % de las vistas en visita" }],
        ["Ficha", marca("Sin " + c.fichaFalta), "mal", { cuenta: c.ficha }]
      ];
    },

    huaylas: function () {
      var c = cap("CAP-0041"), p = prop("PROP-1120");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · vence el " + fdia(venceEncargo(c)),
          null, { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(p.renta) + " desde el " + fdia(p.rentaDesde) + " · " + marca(sinCambios(p)),
          "ojo", { serie: p.serieRenta }, rango(p)],
        ["Actividad", c.vistasFicha + " vistas · " + marca(c.visitas + " visitas") + " en " + plural(diasDesde(c.publicada), "día", "días"), "mal"],
        ["Propietario", nom(p.propietario) + " · " + marca(plural(diasDesde(c.ultimoReporte), "día", "días") + " sin reporte"), "ojo"]
      ];
    },

    mejia: function () {
      var o = opo("OPO-0087"), p = prop("PROP-259");
      return [
        ["Oportunidad", "Abierta el <b>" + fdia(o.abierta) + "</b> · cerrada el " + fdia(o.cerrada) + " por " + o.motivoCierre],
        ["Presupuesto", "Hasta " + dinero(o.presupuesto[1]) + " · " + marca("el local está en " + p.renta.toLocaleString("en-US")),
          "bien", null, { r: [o.presupuesto[0], o.presupuesto[1], p.renta],
            t: "de su presupuesto: le quedan " + dinero(o.presupuesto[1] - p.renta) + " de margen" }],
        ["Actividad", o.visitas + " visitas en su día · " + marca("llegó a propuesta"), "bien"],
        ["Cliente", nom(o.cliente) + " · " + marca(Math.round(diasDesde(o.cerrada) / 30) + " meses sin contacto"), "ojo"]
      ];
    },

    "solicitud-petit": function () { return EXPEDIENTES.sol(); },

    sol: function () {
      var s = sol("SOL-0114"), p = prop("PROP-259");
      return [
        ["Solicitud", "Creada el <b>" + fdia(s.creada) + "</b> · " + plural(diasDesde(s.creada), "día", "días") + " en curso"],
        ["Renta", dinero(s.renta) + " · " + marca("contrato a " + s.plazoMeses + " meses"), "bien", null,
          { d: s.plazoMeses + " meses", t: "la media de tus contratos firmados son 24" }],
        ["Expediente", "Verificado " + fhace(s.verificada) + " · " + marca("sin observaciones"), "bien", { cuenta: s.documentos }],
        ["Interesada", nom(s.interesado) + " · " + s.visitasPrevias + " visitas · " +
          marca(plural(diasDesde(s.entraBandejaBroker), "día", "días") + " esperando"), "ojo"]
      ];
    },

    "brasil-b": function () {
      var c = cap("CAP-0034"), p = prop("PROP-900");
      return [
        ["Captación", "Registrada el <b>" + fdia(c.registrada) + "</b> por " + nom(c.agente)],
        ["Renta", dinero(c.rentaPropuesta) + " propuesta · " + marca("la captación más alta del mes"), "bien", null, rango(p)],
        ["Expediente", "Completa desde el " + fdia(c.registrada) + " · " +
          marca(plural(diasDesde(c.registrada), "día", "días") + " en tu cola"), "ojo", { cuenta: c.documentos },
          { d: plural(diasDesde(c.registrada), "día", "días"), t: "tu media para decidir una captación es 1,5" }],
        ["Agente", nom(c.agente) + " · " + marca(PRODUCCION[c.agente].captacion + " captaciones este mes"), "bien"]
      ];
    },

    carga: function () {
      var co = concentracionCartera(), g = GESTION[co.agente];
      return [
        ["Equipo", AGENTES.length + " agentes · <b>" + co.total + " locales</b> en cartera"],
        ["Concentración", co.nombre + ": " + co.cartera + " · el siguiente: " + co.segundo.cartera,
          "mal", { cuenta: [co.cartera, co.total] },
          { d: co.porcentaje + " %", t: "del equipo en un agente; repartido parejo serían " + co.reparto + " cada uno" }],
        ["Actividad", plural(g.recontactosVencidos, "seguimiento vencido", "seguimientos vencidos") + " · " +
          marca(pc(g.recontactosVencidos, equipoSuma("recontactosVencidos")) + " % de los del equipo"), "mal"],
        ["Origen", "Reasignación de la cartera de Breña · " + marca(fhace("2026-08-04")), "ojo"]
      ];
    },

    "arequipa-b": function () {
      var c = cap("CAP-0022"), p = prop("PROP-1840");
      return [
        ["Encargo", "Firmado el <b>" + fdia(c.firmaEncargo) + "</b> · exclusivo · " + c.plazoDias + " días",
          "mal", { plazo: consumidoEncargo(c) }],
        ["Renta", dinero(p.renta) + " · " + marca(sinCambios(p)), "ojo", { serie: p.serieRenta }, rango(p)],
        ["Actividad", c.visitas + " visitas · " + marca(c.propuestas + " propuestas"), "mal"],
        ["Agente", nom(c.agente) + " · último reporte el " + fdia(c.ultimoReporte), "ojo", null,
          { d: plural(diasDesde(c.ultimoReporte), "día", "días"),
            t: "el compromiso de reporte al propietario es cada " + POLITICA.reporteAlPropietarioDias }]
      ];
    },

    comision: function () {
      var c = con("CON-0071");
      return [
        ["Contrato", "Firmado el <b>" + fdia(c.firmado) + "</b> · " + c.plazoMeses + " meses de plazo"],
        ["Comisión", dinero(c.comision.devengada) + " devengados · " + marca(dinero(c.comision.cobrada) + " cobrados"), "mal"],
        /* Ni «17/15» ni un porcentaje: la frase dice lo transcurrido, lo
           previsto y el sobrepaso, que es lo único que se puede accionar. */
        ["Plazo", plural(diasDesde(c.firmado), "día transcurrido", "días transcurridos") + " · " +
          marca("plazo previsto " + POLITICA.plazoCobroComisionDias + " días"), "ojo",
          { plazo: { transcurridos: diasDesde(c.firmado), previsto: POLITICA.plazoCobroComisionDias } },
          { d: "+" + (diasDesde(c.firmado) - POLITICA.plazoCobroComisionDias) + " días", t: "sobre el plazo habitual de tu organización" }],
        ["Cliente", nom(c.cliente) + " · " + marca("sin fecha de pago comprometida"), "mal"]
      ];
    },

    "urgente-b": function () {
      var s = sol("SOL-0108"), p = prop("PROP-240");
      return [
        ["Solicitud", "Aprobada el <b>" + fdia(s.aprobada) + "</b> · " + marca("a la espera de contrato"), "mal"],
        ["Renta", dinero(s.renta) + " · " + marca("contrato a " + s.plazoMeses + " meses"), "bien", null, rango(p)],
        ["Contrato", marca("Sin generar") + " · " + plural(diasDesde(s.aprobada), "día", "días") + " desde la aprobación",
          "mal", null, { d: plural(diasDesde(s.aprobada), "día", "días"), t: "tus solicitudes aprobadas pasan a contrato en 2 de media" }],
        ["Comisión", dinero(s.comisionPrevista) + " · " + marca("no devengada todavía"), "mal"]
      ];
    }
  };

  /* CÓMO ESTÁ · hasta tres hechos, cada uno con su estado. El estado lo
     decide el dominio: hecho (resuelto), falta (depende de alguien), plazo
     (corre el tiempo), freno (consecuencia) y dato (contexto). */
  var COMO_ESTA = {
    ica: function () {
      var c = cap("CAP-0031");
      return { avance: [c.observacionesResueltas, c.observaciones, "observaciones resueltas"],
        f: [["hecho", "El metraje ya está corregido"],
            ["falta", "Falta subir la partida registral"],
            ["freno", "Hasta que llegue, el local no se puede publicar"]] };
    },
    arequipa: function () {
      var c = cap("CAP-0022");
      return { f: [["hecho", "El propietario devolvió la llamada " + fhace(c.ultimoContactoPropietario)],
            ["plazo", "El encargo vence en " + plural(diasHasta(venceEncargo(c)), "día", "días")],
            ["falta", "No hay conversación programada"]] };
    },
    andina: function () {
      var co = COINCIDENCIAS["COI-1"];
      return { avance: [co.comprobados, co.total, "criterios comprobados"],
        f: [["hecho", "Ubicación, metraje, rubro y renta coinciden"],
            ["falta", "Falta comprobar la " + co.falta + " de los dos locales"],
            ["dato", "El requerimiento entró " + fhace(opo("OPO-0111").abierta)]] };
    },
    larco: function () {
      var v_ = vis("VIS-0044");
      return { f: [["plazo", "La visita es " + fcuando(v_.cuando).toLowerCase() + " a las " + fhora(v_.cuando)],
            ["falta", "El interesado todavía no confirma"],
            ["dato", "Se coordinó " + fhace(v_.programada)]] };
    },
    sur: function () {
      var o = opo("OPO-0064");
      return { f: [["plazo", "Hoy se cumple el plazo para volver a llamar"],
            ["hecho", "Es el cliente con más requerimientos abiertos: " + o.requerimientosAbiertos],
            ["dato", "Último contacto: " + flargo(o.ultimoContacto)]] };
    },
    arenales: function () {
      var c = cap("CAP-0028");
      var lim = sumarDias(c.ultimoContactoPropietario, POLITICA.recontactoDias);
      return { f: [["plazo", "El plazo para volver a llamar venció " + fhace(lim)],
            ["hecho", "Los " + (c.visitas + c.propuestas) + " contactos anteriores los atendió"],
            ["dato", plural(diasDesde(c.ultimoContactoPropietario), "día", "días") + " sin hablar; el plazo son " + POLITICA.recontactoDias]] };
    },
    petit: function () {
      var c = cap("CAP-0009"), p = prop("PROP-259");
      return { f: [["hecho", c.visitas + " visitas en los últimos " + plural(diasDesde(cap("CAP-0009").visitasDesde), "día", "días")],
            ["falta", "Ninguna llegó a propuesta"],
            ["dato", "La renta no cambia desde el " + flargo(p.rentaDesde)]] };
    },
    salaverry: function () {
      var o = opo("OPO-0103");
      return { f: [["hecho", o.visitas + " visitas hechas"],
            ["falta", "Pidió las condiciones por escrito y sigue esperando"],
            ["dato", "Han pasado " + plural(diasDesde(o.condicionesPedidas), "día", "días") + " desde entonces"]] };
    },
    tacna: function () {
      var s = sol("SOL-0121");
      return { avance: [s.documentos[0], s.documentos[1], "documentos cargados"],
        f: [["hecho", "Ya están el DNI, la vigencia de poder y el RUC"],
            ["falta", "Falta el " + s.falta1],
            ["freno", "Sin él la solicitud no pasa a evaluación"]] };
    },
    brasil: function () {
      var pr = PROSPECCIONES["PRO-0022"];
      return { f: [["hecho", "La propuesta de encargo se envió el " + flargo(pr.propuestaEnviada)],
            ["hecho", "El propietario aceptó el " + flargo(pr.fechaDesenlace)],
            ["dato", "La captación " + pr.captacion + " espera decisión del broker"]] };
    },
    cusco: function () {
      var c = cap("CAP-0036");
      return { avance: [c.ficha[0], c.ficha[1], "datos de la ficha completos"],
        f: [["hecho", "El local está publicado desde hace " + plural(diasDesde(c.publicada), "día", "días")],
            ["falta", "No tiene " + c.fichaFalta + " registrada"],
            ["freno", "Por eso hoy quedó fuera de un requerimiento"]] };
    },
    huaylas: function () {
      var c = cap("CAP-0041"), p = prop("PROP-1120");
      return { f: [["hecho", c.vistasFicha + " personas vieron la ficha en " + plural(diasDesde(c.publicada), "día", "días")],
            ["falta", "Ninguna pidió visita"],
            ["dato", "Renta publicada: " + dinero(p.renta)]] };
    },
    mejia: function () {
      var o = opo("OPO-0087"), p = prop("PROP-259");
      return { f: [["hecho", "Respondió " + fhace(o.reabierta) + ", tras " + Math.round(diasDesde(o.cerrada) / 30) + " meses"],
            ["hecho", "El precio del local bajó a " + dinero(p.renta)],
            ["falta", "Falta confirmar si sigue buscando"]] };
    },
    "solicitud-petit": function () {
      var s = sol("SOL-0114");
      return { avance: [s.documentos[0], s.documentos[1], "documentos verificados"],
        f: [["hecho", "El expediente quedó completo " + fhace(s.completa)],
            ["hecho", "Este asunto salió de tu foco"],
            ["dato", "La decisión es del broker"]] };
    },
    sol: function () {
      var s = sol("SOL-0114");
      return { avance: [s.documentos[0], s.documentos[1], "documentos verificados"],
        f: [["hecho", "Los " + s.documentos[1] + " documentos están verificados"],
            ["hecho", "El expediente quedó completo " + fhace(s.completa)],
            ["freno", "Nadie puede seguir hasta que decidas"]] };
    },
    "brasil-b": function () {
      var c = cap("CAP-0034");
      return { avance: [c.documentos[0], c.documentos[1], "documentos adjuntos"],
        f: [["hecho", "La captación llegó completa el " + flargo(c.registrada)],
            ["falta", "Lleva " + plural(diasDesde(c.registrada), "día", "días") + " esperando tu decisión"],
            ["freno", "El local no se publica y " + nom(c.agente).split(" ")[0] + " no puede abrir oportunidades"]] };
    },
    carga: function () {
      var co = concentracionCartera(), g = GESTION[co.agente];
      return { f: [["falta", co.nombre + " lleva " + co.cartera + " de los " + co.total + " locales de la cartera del equipo"],
            ["plazo", g.recontactosVencidos + " de sus seguimientos están vencidos"],
            ["dato", "El siguiente agente con más cartera lleva " + co.segundo.cartera]] };
    },
    "arequipa-b": function () {
      var c = cap("CAP-0022");
      return { f: [["plazo", "El encargo vence en " + plural(diasHasta(venceEncargo(c)), "día", "días")],
            ["falta", "El propietario lleva " + plural(diasDesde(c.ultimoReporte), "día", "días") + " sin recibir un reporte"],
            ["dato", "El reporte lo envía " + nom(c.agente).split(" ")[0] + ", no tú"]] };
    },
    comision: function () {
      var c = con("CON-0071");
      return { f: [["hecho", "El contrato se firmó el " + flargo(c.firmado)],
            ["falta", dinero(c.comision.devengada) + " devengados y nada cobrado en " + plural(diasDesde(c.firmado), "día", "días")],
            ["dato", "Lo habitual son " + POLITICA.plazoCobroComisionDias + " días"]] };
    },
    "urgente-b": function () {
      var s = sol("SOL-0108");
      return { f: [["hecho", "La solicitud está aprobada desde el " + flargo(s.aprobada)],
            ["freno", "El contrato todavía no se genera"],
            ["falta", "Hasta que exista, la comisión no se devenga"]] };
    }
  };

  /* La lectura: una frase que sintetiza el expediente sin recitarlo. */
  var LECTURAS = {
    ica: function () {
      var c = cap("CAP-0031");
      return "Encargo de hace " + plural(diasDesde(c.firmaEncargo), "día", "días") +
        ", renta todavía sin publicar y el propietario aún no ha recibido ningún reporte.";
    },
    arequipa: function () {
      var c = cap("CAP-0022"), p = prop("PROP-1840");
      return "Exclusiva de " + c.plazoDias + " días casi agotada -- quedan " + plural(diasHasta(venceEncargo(c)), "día", "días") +
        " --, " + c.visitas + " visitas sin propuesta y la renta sin moverse desde " +
        flargo(p.rentaDesde).split(" de ")[1] + ".";
    },
    andina: function () {
      var co = COINCIDENCIAS["COI-1"];
      return "Cliente nuevo, requerimiento de " + fhace(opo("OPO-0111").abierta) + " y " +
        co.propiedades.length + " locales que ya casi encajan.";
    },
    larco: function () {
      var o = opo("OPO-0098");
      return "Oportunidad de " + flargo(o.abierta).split(" de ")[1] + " con una sola visita; la renta está dentro de lo que el cliente puede pagar.";
    },
    sur: function () {
      var o = opo("OPO-0064");
      return "Cliente de " + flargo(o.abierta).split(" de ")[1] + " con " + o.requerimientosAbiertos +
        " requerimientos vivos, " + o.contactos + " contactos y ninguna propuesta presentada.";
    },
    arenales: function () {
      var c = cap("CAP-0028");
      return plural(Math.round(diasDesde(c.firmaEncargo) / 30), "mes", "meses") + " de encargo, una propuesta rechazada y " +
        plural(diasDesde(c.ultimoContactoPropietario), "día", "días") + " sin hablar con la propietaria.";
    },
    petit: function () {
      var c = cap("CAP-0009");
      return "El local se enseña mucho y no cierra: " + c.visitas + " visitas en " +
        plural(diasDesde(cap("CAP-0009").visitasDesde), "día", "días") + " y el precio como objeción en " + c.objeciones.precio + ".";
    },
    salaverry: function () {
      var o = opo("OPO-0103");
      return "El cliente pidió condiciones por escrito " + fhace(o.condicionesPedidas) + " y decide en " +
        o.decideEnSemanas + " semanas; no hay contraoferta.";
    },
    tacna: function () {
      var s = sol("SOL-0121");
      return "Solicitud del " + fdia(s.creada) + " con " + s.documentos[0] + " de " + s.documentos[1] +
        " documentos y " + plural(diasDesde(s.ultimoDocumento), "día", "días") + " sin avanzar.";
    },
    brasil: function () {
      var pr = PROSPECCIONES["PRO-0022"];
      return "Propuesta de encargo enviada el " + flargo(pr.propuestaEnviada) + " y aceptada el " +
        flargo(pr.fechaDesenlace) + ": la prospección terminó y ahora el turno es del broker.";
    },
    cusco: function () {
      var c = cap("CAP-0036");
      return "Publicado hace " + plural(diasDesde(c.publicada), "día", "días") + " con la ficha a medias: " +
        c.vistasFicha + " vistas y ninguna visita.";
    },
    huaylas: function () {
      var c = cap("CAP-0041"), p = prop("PROP-1120");
      return plural(diasDesde(c.publicada), "día", "días") + " publicado a " + dinero(p.renta) +
        " sin tocar el precio: " + c.vistasFicha + " vistas y ninguna visita pedida.";
    },
    mejia: function () {
      var o = opo("OPO-0087"), p = prop("PROP-259");
      return "Oportunidad cerrada el " + fdia(o.cerrada) + " por precio; el local bajó a " + dinero(p.renta) +
        " y entra en su presupuesto.";
    },
    "solicitud-petit": function () {
      var s = sol("SOL-0114");
      return "Expediente completo y verificado sin observaciones: este asunto ya no depende de ti.";
    },
    sol: function () {
      var s = sol("SOL-0114");
      return "Expediente completo y verificado sin observaciones; la interesada lleva " +
        plural(diasDesde(s.entraBandejaBroker), "día", "días") + " esperando respuesta.";
    },
    "brasil-b": function () {
      var c = cap("CAP-0034");
      return "Captación completa desde el " + flargo(c.registrada) + ", de una agente con " +
        PRODUCCION[c.agente].captacion + " captaciones este mes, parada en tu cola.";
    },
    carga: function () {
      var co = concentracionCartera(), g = GESTION[co.agente];
      return "Un agente concentra " + co.cartera + " de los " + co.total +
        " locales del equipo y acumula " + g.recontactosVencidos + " de los " +
        equipoSuma("recontactosVencidos") + " seguimientos vencidos.";
    },
    "arequipa-b": function () {
      var c = cap("CAP-0022");
      return "Exclusiva casi agotada y " + plural(diasDesde(c.ultimoReporte), "día", "días") +
        " sin reporte al propietario; el encargo lo lleva " + nom(c.agente).split(" ")[0] + ".";
    },
    comision: function () {
      var c = con("CON-0071");
      return "Contrato firmado el " + flargo(c.firmado) + ", " + dinero(c.comision.devengada) +
        " devengados y nada cobrado " + plural(diasDesde(c.firmado) - POLITICA.plazoCobroComisionDias, "día", "días") +
        " por encima del plazo.";
    },
    "urgente-b": function () {
      var s = sol("SOL-0108");
      return "Solicitud aprobada hace " + plural(diasDesde(s.aprobada), "día", "días") +
        " sin contrato generado; la comisión no se devenga hasta que exista.";
    }
  };

  function expedienteDe(a) { return (EXPEDIENTES[a.id] || function () { return []; })(); }
  function comoEstaDe(a) { return (COMO_ESTA[a.id] || function () { return null; })(); }
  function lecturaDe(a) { return (LECTURAS[a.id] || function () { return null; })(); }

  /* ------------------------------------------------------------------
     17 ter · ACTORES — quién mira la pantalla
     Lo que cambia por rol es el ALCANCE y la VOZ, no las definiciones.
     ------------------------------------------------------------------ */

  var ACCESOS = {
    /* Lo que el AGENTE empieza desde cero. */
    AGENTE: [
      { n: "Nueva prospección", i: "i-mapa", proc: "prospeccion", r: "/locales/nuevo" },
      { n: "Nueva captación", i: "i-firma", proc: "captacion", r: "/captaciones/nueva" },
      { n: "Programar visita", i: "i-cal", proc: "visita", r: "/visitas/nueva" },
      { n: "Reporte al propietario", i: "i-graf", proc: "publicacion", r: "/reportes" }
    ],
    /* El BROKER no crea: revisa, decide y reparte. */
    BROKER: [
      { n: "Revisar captaciones", i: "i-firma", proc: "captacion", r: "/captaciones/pendientes" },
      { n: "Evaluar solicitudes", i: "i-doc", proc: "solicitud", r: "/solicitudes/revisar" },
      { n: "Seguimiento del equipo", i: "i-pulso", proc: "cliente", r: "/seguimiento-comercial" },
      { n: "Reasignar cartera", i: "i-persona", proc: "oportunidad", r: "/captaciones/reasignaciones" }
    ]
  };

  function actorDe(rol) {
    var p = per(rol === "AGENTE" ? AGENTE_VISTA : BROKER_VISTA);
    var pulso = pulsoEquipo(PERIODO_INICIO);
    var enRitmo = pulso.por[RITMO.EN_RITMO].length;
    var esperanDecision = asuntosActivos("BROKER").filter(function (a) { return a.desbloquea; }).length;
    var carteraAg = GESTION[AGENTE_VISTA];

    /* Los conteos del «día cubierto» salen de las mismas entidades, no de
       una lista aparte: si se anade una solicitud, el numero cambia solo. */
    var misSolicitudes = Object.keys(SOLICITUDES).filter(function (k) {
      return SOLICITUDES[k].agente === AGENTE_VISTA && SOLICITUDES[k].estado !== "CERRADA";
    }).length;
    var misEncargos = Object.keys(CAPTACIONES).filter(function (k) {
      return CAPTACIONES[k].agente === AGENTE_VISTA && CAPTACIONES[k].estado === "ACTIVA";
    }).length;

    if (rol === "AGENTE") {
      var sig = proximoEvento("AGENTE");
      return {
        id: p.id, n: p.nombre, ini: p.ini, r: p.rotulo,
        saludo: "Buenos días, " + p.nombre.split(" ")[0],
        zona: "Qué atender ahora",
        vigila: "Vigilando <b>" + plural(carteraAg.cartera, "local", "locales") + "</b> · " +
          plural(cambiosDe("AGENTE").filter(function (c) { return diasDesde(c.cuando) === 0; }).length, "movimiento", "movimientos") + " hoy",
        accesos: ACCESOS.AGENTE,
        ambito: "Tu gestión",
        cubierto: {
          f: "Cerraste tu lista.",
          sub: "No queda nada que dependa de ti. Lo que sigue abierto está esperando a otros, y el Radar avisa en cuanto vuelva a tu campo.",
          logro: "Es el <b>cuarto día</b> que la cierras este mes. Hoy lo han conseguido <b>" +
            enRitmo + " de los " + AGENTES.length + " agentes</b> del equipo.",
          revisado: [
            [String(carteraAg.cartera), "locales revisados"],
            [String(misEncargos), "encargos vigentes"],
            [String(misSolicitudes), "solicitudes en curso"],
            ["0", "vencidas o sin seguimiento"]
          ],
          siguiente: sig
            ? fcuando(sig.cuando) + " a las <b>" + fhora(sig.cuando) + "</b>, " + sig.rotulo.toLowerCase() +
              " en " + sig.detalle.split(" · ")[0] + ". Nada antes."
            : "Nada programado por delante."
        }
      };
    }

    var sigB = proximoEvento("BROKER", function (e) { return e.tipo === "VISITA"; }) || proximoEvento("BROKER");
    return {
      id: p.id, n: p.nombre, ini: p.ini, r: p.rotulo,
      saludo: "Buenos días, " + p.nombre.split(" ")[0],
      zona: "Qué supervisar ahora",
      vigila: "Vigilando <b>" + carteraTotalEquipo() + " locales</b> de " + AGENTES.length + " agentes",
      accesos: ACCESOS.BROKER,
      ambito: "Tu equipo",
      cubierto: {
        f: "Tu bandeja está limpia.",
        sub: "Ningún expediente espera tu decisión. Tus agentes pueden seguir sin ti, que es de lo que se trata.",
        logro: "Es la <b>segunda vez este mes</b>. Los otros días cerraste con <b>" +
          plural(esperanDecision, "decisión", "decisiones") + "</b> pendientes de media.",
        revisado: [
          [String(carteraTotalEquipo()), "locales del equipo"],
          [String(AGENTES.length), "agentes activos"],
          [String(enRitmo), "en ritmo"],
          ["0", "decisiones pendientes"]
        ],
        siguiente: sigB
          ? fcuando(sigB.cuando) + " a las <b>" + fhora(sigB.cuando) + "</b>, <b>" +
            nom(vis(sigB.entidadId) ? vis(sigB.entidadId).agente : "PER-1") + "</b> visita " +
            sigB.detalle.split(" · ")[0] + ". Nada tuyo antes."
          : "Nada programado por delante."
      }
    };
  }

  /* ------------------------------------------------------------------
     18 · SALIDA
     ------------------------------------------------------------------ */

  var API = {
    FECHA_REF: FECHA_REF,
    POLITICA: POLITICA, RITMO: RITMO, VOZ_RITMO: VOZ_RITMO,
    SEVERIDAD: SEVERIDAD, NATURALEZA: NATURALEZA, LADO: LADO,
    PERSONAS: PERSONAS, AGENTES: AGENTES, AGENTE_VISTA: AGENTE_VISTA, BROKER_VISTA: BROKER_VISTA,
    PROPIEDADES: PROPIEDADES, PROSPECCIONES: PROSPECCIONES, CAPTACIONES: CAPTACIONES,
    OPORTUNIDADES: OPORTUNIDADES, COINCIDENCIAS: COINCIDENCIAS, VISITAS: VISITAS,
    SOLICITUDES: SOLICITUDES, CONTRATOS: CONTRATOS,
    KPIS: KPIS, PRODUCCION: PRODUCCION, GESTION: GESTION, SERIES: SERIES,
    CARTERA_EQUIPO: CARTERA_EQUIPO, PERIODOS: PERIODOS, PERIODO_INICIO: PERIODO_INICIO,
    ASUNTOS: ASUNTOS, IRRUPCIONES: IRRUPCIONES, AGENDA: AGENDA, CAMBIOS: CAMBIOS,
    MAX_FOCO: MAX_FOCO,

    /* tiempo */
    aFecha: aFecha, diasDesde: diasDesde, diasHasta: diasHasta, diasEntre: diasEntre,
    sumarDias: sumarDias, fdia: fdia, flargo: flargo, fhora: fhora, fcuando: fcuando,
    fhace: fhace, fcabecera: fcabecera, plural: plural, dinero: dinero, pc: pc, v: v,

    /* consultas */
    per: per, nom: nom, prop: prop, dir: dir, cap: cap, opo: opo, vis: vis,
    sol: sol, con: con, venceEncargo: venceEncargo, consumidoEncargo: consumidoEncargo,

    /* derivaciones */
    periodoDe: periodoDe, ritmoDe: ritmoDe, kpisDe: kpisDe, kpiDe: kpiDe,
    contadores: contadores, embudos: embudos, conversionPorAgente: conversionPorAgente,
    ritmoDeAgente: ritmoDeAgente, pulsoEquipo: pulsoEquipo, excepcionesEquipo: excepcionesEquipo,
    carteraPorAgente: carteraPorAgente, carteraTotalEquipo: carteraTotalEquipo,
    concentracionCartera: concentracionCartera, equipoSuma: equipoSuma,
    asuntosDe: asuntosDe, asuntosActivos: asuntosActivos, asuntoPorId: asuntoPorId,
    despacho: despacho, pesoAsunto: pesoAsunto, accionesDe: accionesDe,
    agendaDe: agendaDe, proximoEvento: proximoEvento, proximoDeAsunto: proximoDeAsunto,
    cambiosDe: cambiosDe, hallazgoDe: hallazgoDe, puedeCerrarse: puedeCerrarse,
    expedienteDe: expedienteDe, comoEstaDe: comoEstaDe, lecturaDe: lecturaDe,
    ACCESOS: ACCESOS, actorDe: actorDe
  };

  raiz.NUCLEO_BROX = API;
  if (typeof module !== "undefined" && module.exports) module.exports = API;
})(typeof globalThis !== "undefined" ? globalThis : this);
