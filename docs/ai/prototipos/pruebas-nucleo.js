/* ====================================================================
   PRUEBAS DE INVARIANTES DEL NUCLEO
   --------------------------------------------------------------------
   node docs/ai/prototipos/pruebas-nucleo.js

   No comprueban que la pantalla se vea bien: comprueban que las dos
   pantallas NO PUEDAN contradecirse. Cada bloque corresponde a una de las
   incoherencias que se encontraron en los prototipos publicados, y esta
   escrito para que vuelva a fallar si alguien las reintroduce.

   Salida: 0 todo verde · 1 alguna invariante rota.
   ==================================================================== */

"use strict";

var fs = require("fs");
var path = require("path");
var N = require("./nucleo-brox.js");

var DIR = __dirname;
var fallos = [], hechas = 0, bloque = "";

function grupo(t) { bloque = t; console.log("\n" + t); }
function ok(nombre, cond, detalle) {
  hechas++;
  if (cond) { console.log("  ✓ " + nombre); return true; }
  console.log("  ✗ " + nombre + (detalle ? "  →  " + detalle : ""));
  fallos.push(bloque + " · " + nombre + (detalle ? " — " + detalle : ""));
  return false;
}
function igual(nombre, a, b) { return ok(nombre, a === b, "esperado " + JSON.stringify(b) + ", obtenido " + JSON.stringify(a)); }

var AMBITOS = [{ tipo: "AGENTE", id: "PER-1" }, { tipo: "EQUIPO" }];
var PERIODOS = N.PERIODOS.map(function (p) { return p.v; });
var ESCENARIOS = [{}, { arranque: true }, { sinMeta: true }];

/* ------------------------------------------------------------------
   1 · KPI — un solo cálculo para las dos pantallas
   ------------------------------------------------------------------ */

grupo("1 · KPI · el mismo objeto para Inicio y para Indicadores");

/* Las dos pantallas llaman a la MISMA funcion con el mismo ambito y el mismo
   periodo. Si alguien reintroduce un calculo propio en una de ellas, esta
   prueba no lo ve -- lo ve la del bloque 8, que lee los dos HTML. Aqui se
   comprueba que la funcion es determinista y que su contrato esta completo. */
var CAMPOS = ["actual", "metaPeriodo", "metaEsperadaAHoy", "proyeccionCierre",
  "porcentajeProyectado", "porcentajeMeta", "faltante", "estadoRitmo"];

AMBITOS.forEach(function (ambito) {
  var nombre = ambito.tipo === "EQUIPO" ? "equipo" : N.nom(ambito.id);
  PERIODOS.forEach(function (p) {
    var a = N.kpisDe(ambito, p);
    var b = N.kpisDe(ambito, p);
    ok("determinista · " + nombre + " · " + p,
      JSON.stringify(a.map(recorta)) === JSON.stringify(b.map(recorta)));
  });
  var k = N.kpisDe(ambito, N.PERIODO_INICIO);
  ok("contrato completo · " + nombre, k.every(function (x) {
    return CAMPOS.every(function (c) { return c in x; });
  }));
});
function recorta(k) {
  var o = {};
  CAMPOS.forEach(function (c) { o[c] = k[c]; });
  return o;
}

/* La comprobación literal del punto 16: para un mismo actor, KPI, periodo y
   fecha de referencia, las dos pantallas reciben el MISMO objeto. Se imita
   aquí el camino de cada una:
     Inicio        NB.kpisDe(ámbito del rol, NB.PERIODO_INICIO)
     Indicadores   NB.kpisDe(ambitoDe(id), periodo elegido)
   Con el periodo del Inicio («1m») los dos tienen que coincidir campo a campo. */
[["AGENTE", { tipo: "AGENTE", id: N.AGENTE_VISTA }], ["BROKER", { tipo: "EQUIPO" }]].forEach(function (par) {
  var rol = par[0], ambito = par[1];
  var desdeInicio = N.kpisDe(ambito, N.PERIODO_INICIO);
  var desdeIndicadores = N.kpisDe(ambito, "1m");
  desdeInicio.forEach(function (k, i) {
    var j = desdeIndicadores[i];
    ok("Inicio e Indicadores reciben lo mismo · " + rol + " · " + k.rotulo,
      CAMPOS.every(function (c) { return k[c] === j[c]; }),
      CAMPOS.filter(function (c) { return k[c] !== j[c]; })
        .map(function (c) { return c + ": " + k[c] + " ≠ " + j[c]; }).join(", "));
  });
});

ok("los cuatro nombres canónicos, letra por letra",
  JSON.stringify(N.kpisDe(AMBITOS[0], "1m").map(function (k) { return k.rotulo; })) ===
  JSON.stringify(["Propietarios contactados", "Propiedades captadas", "Solicitudes ingresadas", "Contratos firmados"]));

/* Las tres correcciones pedidas explícitamente. */
grupo("1.1 · KPI · el fixture corregido");
var ag = N.kpisDe({ tipo: "AGENTE", id: "PER-1" }, "1m");
var eq = N.kpisDe({ tipo: "EQUIPO" }, "1m");
function porClave(l, c) { return l.filter(function (k) { return k.clave === c; })[0]; }

var cap = porClave(ag, "captacion");
igual("agente · locales captados · actual", cap.actual, 13);
igual("agente · locales captados · meta", cap.metaPeriodo, 15);
igual("agente · locales captados · metaEsperadaAHoy", cap.metaEsperadaAHoy, 13);
igual("agente · locales captados · proyección", cap.proyeccionCierre, 15);
igual("agente · locales captados · estado", cap.estadoRitmo, N.RITMO.EN_RITMO);

var conAg = porClave(ag, "contrato");
igual("agente · contratos · actual/meta", conAg.actual + "/" + conAg.metaPeriodo, "4/5");
ok("agente · contratos · proyección ≈ 4,6", conAg.porcentajeProyectado === 92, "porcentaje " + conAg.porcentajeProyectado);
igual("agente · contratos · estado", conAg.estadoRitmo, N.RITMO.ATENCION);

var conEq = porClave(eq, "contrato");
igual("broker · contratos · actual/meta", conEq.actual + "/" + conEq.metaPeriodo, "17/20");
ok("broker · contratos · proyección ≈ 19,6", conEq.porcentajeProyectado === 98, "porcentaje " + conEq.porcentajeProyectado);
igual("broker · contratos · estado", conEq.estadoRitmo, N.RITMO.ATENCION);

grupo("1.2 · KPI · vocabulario de ritmo sin contaminar");
var VOCAB = [N.RITMO.EN_RITMO, N.RITMO.ATENCION, N.RITMO.FUERA_DE_RITMO, N.RITMO.SIN_BASE];
AMBITOS.forEach(function (ambito) {
  PERIODOS.forEach(function (p) {
    ESCENARIOS.forEach(function (esc) {
      var mal = N.kpisDe(ambito, p, esc).filter(function (k) { return VOCAB.indexOf(k.estadoRitmo) === -1; });
      ok("estadoRitmo del vocabulario · " + ambito.tipo + "/" + p + "/" + JSON.stringify(esc),
        mal.length === 0, mal.map(function (k) { return k.clave + "=" + k.estadoRitmo; }).join(", "));
    });
  });
});
ok("«alto/medio/bueno» ya no son estados de ritmo",
  VOCAB.indexOf("alto") === -1 && VOCAB.indexOf("medio") === -1 && VOCAB.indexOf("bueno") === -1);

grupo("1.3 · KPI · la meta del equipo es la suma de las metas de sus agentes");
N.KPIS.definiciones.forEach(function (d) {
  var suma = N.AGENTES.reduce(function (t, id) { return t + N.KPIS.metas[id][d.clave]; }, 0);
  igual(d.rotulo, porClave(eq, d.clave).metaPeriodo, suma);
});

grupo("1.4 · KPI · el actual del equipo es la suma de sus agentes");
N.KPIS.definiciones.forEach(function (d) {
  var suma = N.AGENTES.reduce(function (t, id) {
    return t + porClave(N.kpisDe({ tipo: "AGENTE", id: id }, "1m"), d.clave).actual;
  }, 0);
  igual(d.rotulo, porClave(eq, d.clave).actual, suma);
});

/* ------------------------------------------------------------------
   2 · EMBUDO — la evaluación de solicitud es una etapa real
   ------------------------------------------------------------------ */

grupo("2 · Embudo · la cadena no puede crecer y no se salta etapas");

AMBITOS.forEach(function (ambito) {
  PERIODOS.forEach(function (p) {
    var e = N.embudos(ambito, p);
    var c = e.contadores;
    var etq = ambito.tipo + "/" + p;
    ok("demanda decreciente · " + etq,
      c.oportunidades >= c.visitas && c.visitas >= c.solicitud &&
      c.solicitud >= c.aprobadas && c.aprobadas >= c.contrato,
      [c.oportunidades, c.visitas, c.solicitud, c.aprobadas, c.contrato].join(" ≥ "));
    ok("oferta decreciente · " + etq,
      c.prospectos >= c.prospeccion && c.prospeccion >= c.captacion && c.captacion >= c.publicados,
      [c.prospectos, c.prospeccion, c.captacion, c.publicados].join(" ≥ "));
    ok("cada salto encadena con el siguiente · " + etq,
      e.demanda.every(function (s, i) { return i === 0 || s.n === e.demanda[i - 1].avanzo; }) &&
      e.oferta.every(function (s, i) { return i === 0 || s.n === e.oferta[i - 1].avanzo; }));
  });
});

var dem = N.embudos({ tipo: "EQUIPO" }, "1m").demanda;
ok("el embudo de demanda tiene cuatro saltos, no tres", dem.length === 4, "tiene " + dem.length);
ok("existe el salto Solicitud → Aprobada",
  dem.some(function (s) { return s.de === "Solicitud" && s.a === "Aprobada"; }));
ok("ingresadas y aprobadas NO son el mismo número",
  N.embudos({ tipo: "EQUIPO" }, "1m").contadores.solicitud !== N.embudos({ tipo: "EQUIPO" }, "1m").contadores.aprobadas,
  "ambas valen " + N.embudos({ tipo: "EQUIPO" }, "1m").contadores.solicitud);
N.AGENTES.forEach(function (id) {
  var c = N.PRODUCCION[id];
  ok("aprobadas ≥ contratos · " + N.nom(id), c.aprobadas >= c.contrato, c.aprobadas + " < " + c.contrato);
  ok("ingresadas ≥ aprobadas · " + N.nom(id), c.solicitud >= c.aprobadas, c.solicitud + " < " + c.aprobadas);
});

grupo("2.1 · Embudo · el origen de un KPI es el paso anterior del embudo");
AMBITOS.forEach(function (ambito) {
  var k = N.kpisDe(ambito, "1m"), e = N.embudos(ambito, "1m");
  var mapa = { prospeccion: e.oferta[0], captacion: e.oferta[1], solicitud: e.demanda[1], contrato: e.demanda[3] };
  Object.keys(mapa).forEach(function (clave) {
    var kk = porClave(k, clave), s = mapa[clave];
    ok(ambito.tipo + " · " + kk.rotulo + " sale del embudo",
      kk.origen.de === s.n && kk.actual === s.avanzo,
      "KPI " + kk.origen.de + "→" + kk.actual + " vs embudo " + s.n + "→" + s.avanzo);
  });
});

/* ------------------------------------------------------------------
   3 · EQUIPO — el pulso es una agregación, no un texto
   ------------------------------------------------------------------ */

grupo("3 · Equipo · pulso y excepciones salen del mismo GROUP BY");

PERIODOS.forEach(function (p) {
  var pulso = N.pulsoEquipo(p);
  var suma = pulso.grupos.reduce(function (t, g) { return t + g.n; }, 0);
  igual("totalAgentes = suma de estados · " + p, suma, pulso.total);
  igual("totalAgentes = agentes del equipo · " + p, pulso.total, N.AGENTES.length);

  var exc = N.excepcionesEquipo(p);
  var fuera = pulso.por[N.RITMO.EN_RITMO].length;
  igual("excepciones = agentes que no están en ritmo · " + p, exc.length, pulso.total - fuera);
  ok("ninguna excepción está EN_RITMO · " + p,
    exc.every(function (e) { return e.estadoRitmo !== N.RITMO.EN_RITMO; }),
    exc.filter(function (e) { return e.estadoRitmo === N.RITMO.EN_RITMO; }).map(function (e) { return e.nombre; }).join(", "));
  ok("cada excepción nombra su brecha · " + p,
    exc.every(function (e) { return e.brecha && e.brecha.rotulo; }));
  ok("ningún agente aparece dos veces · " + p,
    new Set(exc.map(function (e) { return e.agente; })).size === exc.length);
});

grupo("3.1 · Equipo · la concentración sale de la distribución de cartera");
var conc = N.concentracionCartera();
var dist = N.carteraPorAgente().filter(function (x) { return x.agente; });
igual("el agente nombrado es el primero de la distribución", conc.agente, dist[0].agente);
igual("su cartera es la de la distribución", conc.cartera, dist[0].cartera);
igual("el total es la suma de la distribución", conc.total, N.carteraTotalEquipo());
igual("el porcentaje es cartera/total", conc.porcentaje, Math.round(conc.cartera / conc.total * 100));
ok("nadie tiene más cartera que el agente nombrado",
  dist.every(function (x) { return x.cartera <= conc.cartera; }));
ok("la distribución por etapa suma el total de la cartera",
  N.CARTERA_EQUIPO.etapa.reduce(function (t, x) { return t + x[1]; }, 0) === N.carteraTotalEquipo(),
  N.CARTERA_EQUIPO.etapa.reduce(function (t, x) { return t + x[1]; }, 0) + " ≠ " + N.carteraTotalEquipo());
ok("la distribución por distrito suma el total de la cartera",
  N.CARTERA_EQUIPO.distrito.reduce(function (t, x) { return t + x[1]; }, 0) === N.carteraTotalEquipo());
/* Y cada agente reparte exactamente su cartera: es lo que permite abrir uno
   desde el broker sin que los totales dejen de cuadrar. */
N.AGENTES.concat(["SIN_ASIGNAR"]).forEach(function (id) {
  var g = N.GESTION[id];
  var nombre = id === "SIN_ASIGNAR" ? "Sin asignar" : N.nom(id);
  ["etapa", "distrito"].forEach(function (eje) {
    ok(nombre + " reparte su cartera por " + eje,
      !!g[eje] && g[eje].reduce(function (t, x) { return t + x[1]; }, 0) === g.cartera,
      g[eje] ? g[eje].reduce(function (t, x) { return t + x[1]; }, 0) + " ≠ " + g.cartera : "no tiene " + eje);
  });
});

/* El hallazgo del broker habla de conversión, no de concentración: si vuelven
   a ser el mismo número con dos significados, se nota aquí. */
grupo("3.2 · Equipo · el hallazgo nombra su concepto");
var hall = N.hallazgoDe("BROKER");
ok("el porcentaje del hallazgo va con su concepto",
  /% de las visitas llegan a solicitud/.test(hall.c), hall.c);
igual("y es el del embudo del equipo",
  Number(/([0-9]+) % de las visitas/.exec(hall.c)[1]),
  N.embudos({ tipo: "EQUIPO" }, "1m").demanda[1].porcentaje);
ok("el agente del hallazgo es el peor del reparto de conversión",
  hall.c.indexOf(N.conversionPorAgente("solicitud").filter(function (x) { return x.fiable; })[0].nombre) !== -1);

/* ------------------------------------------------------------------
   4 · ASUNTOS — identidad estable
   ------------------------------------------------------------------ */

grupo("4 · Asuntos · identidad, estado y pelota");

var todos = N.ASUNTOS.concat([N.IRRUPCIONES.AGENTE.asunto, N.IRRUPCIONES.BROKER.asunto]);
ok("ningún asuntoId repetido",
  new Set(todos.map(function (a) { return a.asuntoId; })).size === todos.length);
ok("ningún id de trabajo repetido",
  new Set(todos.map(function (a) { return a.id; })).size === todos.length);
ok("todos declaran entidadTipo",
  todos.every(function (a) { return !!a.entidadTipo; }));
ok("todos declaran lado OFERTA o DEMANDA",
  todos.every(function (a) { return a.lado === "OFERTA" || a.lado === "DEMANDA"; }));
ok("el paso existe en la cadena de su lado",
  todos.every(function (a) { return N.LADO[a.lado].pasos[a.paso] !== undefined; }));

var estados = ["ACTIVO", "FUERA_DEL_FOCO", "RESUELTO"];
ok("el estado es uno del vocabulario",
  todos.every(function (a) { return estados.indexOf(a.estado) !== -1; }));

/* La invariante que fallaba: un asunto no puede estar en el foco y fuera. */
["AGENTE", "BROKER"].forEach(function (rol) {
  var activos = N.asuntosActivos(rol).map(function (a) { return a.asuntoId; });
  var fuera = N.asuntosDe(rol).filter(function (a) { return a.estado === "FUERA_DEL_FOCO"; })
    .map(function (a) { return a.asuntoId; });
  var cruce = activos.filter(function (x) { return fuera.indexOf(x) !== -1; });
  ok("ningún asuntoId está ACTIVO y FUERA_DEL_FOCO · " + rol, cruce.length === 0, cruce.join(", "));
  ok("nada del foco espera a un tercero · " + rol,
    N.asuntosActivos(rol).every(function (a) { return a.dependeDeMi; }));
});

grupo("4.1 · Asuntos · Av. Brasil 900 y la regla de la pelota");
var brasil = N.asuntoPorId("brasil");
var pro = N.PROSPECCIONES["PRO-0022"];
ok("la prospección tiene desenlace", !!pro.desenlace && !!pro.fechaDesenlace);
igual("y apunta a la captación que la sucede", pro.captacion, "CAP-0034");
igual("el asunto de la prospección está fuera del foco", brasil.estado, "FUERA_DEL_FOCO");
ok("y no depende del agente", brasil.dependeDeMi === false);
ok("no compite en el foco del agente",
  N.asuntosActivos("AGENTE").every(function (a) { return a.id !== "brasil"; }));
ok("la captación sí está en el foco del broker",
  N.asuntosActivos("BROKER").some(function (a) { return a.entidadId === "CAP-0034"; }));
ok("la trazabilidad se conserva", !!N.v(brasil.salida) && N.v(brasil.salida).indexOf("captación") !== -1);

grupo("4.2 · Asuntos · Petit Thouars: dos asuntos, un inmueble");
var mismos = todos.filter(function (a) { return a.propiedad === "PROP-259" && a.visto === "AGENTE"; });
ok("el inmueble tiene más de un asunto del agente", mismos.length >= 2, "tiene " + mismos.length);
ok("con entidades distintas",
  new Set(mismos.map(function (a) { return a.entidadTipo + ":" + a.entidadId; })).size === mismos.length);
var salido = N.asuntoPorId("solicitud-petit"), vivo = N.asuntoPorId("petit");
igual("el que sale del foco es la solicitud", salido.entidadId, "SOL-0114");
igual("y está FUERA_DEL_FOCO", salido.estado, "FUERA_DEL_FOCO");
igual("el que sigue vivo es la captación", vivo.entidadId, "CAP-0009");
igual("y está ACTIVO", vivo.estado, "ACTIVO");
var cambio = N.cambiosDe("AGENTE").filter(function (c) { return /sale de tu foco/.test(c.detalle || ""); })[0];
ok("«sale de tu foco» existe y aclara que el otro asunto sigue",
  !!cambio && /otro asunto/.test(cambio.detalle), cambio ? cambio.detalle : "no hay aviso");

grupo("4.3 · Asuntos · Arenales es OFERTA");
var are = N.asuntoPorId("arenales");
igual("lado", are.lado, "OFERTA");
igual("actor del lado", N.LADO[are.lado].actor, "PROPIETARIO");
igual("entidad", are.entidadTipo, "CAPTACION");
ok("destino coherente con el lado", /propietarios/.test(are.destino.r), are.destino.r);
ok("su expediente habla del propietario",
  N.expedienteDe(are).some(function (r) { return /Propietari/.test(r[0]); }));

/* ------------------------------------------------------------------
   5 · AGENDA — un evento, un id, una hora
   ------------------------------------------------------------------ */

grupo("5 · Agenda · la visita de mañana a las 10:30 es un único evento");

var diezYmedia = Object.keys(N.VISITAS).map(function (k) { return N.VISITAS[k]; })
  .filter(function (v) { return N.diasHasta(v.cuando) === 1; });
igual("solo hay una visita mañana", diezYmedia.length, 1);
igual("y es VIS-0044", diezYmedia[0].id, "VIS-0044");
igual("en Av. Larco 780", N.dir(diezYmedia[0].propiedad), "Av. Larco 780");
igual("a las 10:30", N.fhora(diezYmedia[0].cuando), "10:30");

var larco = N.asuntoPorId("larco");
igual("el asunto del foco apunta a esa visita", larco.entidadId, "VIS-0044");
var evAgenda = N.agendaDe("AGENTE").filter(function (e) { return e.entidadId === "VIS-0044"; });
igual("la agenda la lleva una sola vez", evAgenda.length, 1);
igual("y la enlaza con su asunto", evAgenda[0].asunto.AGENTE, "larco");
ok("el «día cubierto» del agente nombra esa misma visita",
  N.actorDe("AGENTE").cubierto.siguiente.indexOf("Av. Larco 780") !== -1,
  N.actorDe("AGENTE").cubierto.siguiente);
ok("el «día cubierto» del broker también",
  N.actorDe("BROKER").cubierto.siguiente.indexOf("Av. Larco 780") !== -1,
  N.actorDe("BROKER").cubierto.siguiente);
ok("Petit Thouars tiene su propia fecha, distinta",
  N.fhora(N.vis("VIS-0051").cuando) !== "10:30" || N.diasHasta(N.vis("VIS-0051").cuando) !== 1);

grupo("5.1 · Agenda · cada evento apunta a una entidad que existe");
var TABLAS = { VISITA: N.VISITAS, CAPTACION: N.CAPTACIONES, SOLICITUD: N.SOLICITUDES, CONTRATO: N.CONTRATOS, OPORTUNIDAD: N.OPORTUNIDADES, PROSPECCION: N.PROSPECCIONES };
["AGENTE", "BROKER"].forEach(function (rol) {
  N.agendaDe(rol).forEach(function (e) {
    ok(rol + " · " + e.id + " apunta a " + e.entidadTipo + " " + e.entidadId,
      !!(TABLAS[e.entidadTipo] && TABLAS[e.entidadTipo][e.entidadId]));
  });
});
todos.forEach(function (a) {
  if (!a.entidadId) return;
  ok("asunto " + a.asuntoId + " apunta a " + a.entidadTipo + " " + a.entidadId,
    !!(TABLAS[a.entidadTipo] ? TABLAS[a.entidadTipo][a.entidadId] : (a.entidadTipo === "CLIENTE" ? N.PERSONAS[a.entidadId] : true)));
});
ok("toda propiedad referida existe",
  todos.every(function (a) { return !a.propiedad || !!N.PROPIEDADES[a.propiedad]; }));

grupo("5.2 · Agenda · un inmueble alquilado no tiene captación en revisión");
var porPropiedad = {};
Object.keys(N.CAPTACIONES).forEach(function (k) {
  var c = N.CAPTACIONES[k];
  (porPropiedad[c.propiedad] = porPropiedad[c.propiedad] || []).push(c);
});
Object.keys(porPropiedad).forEach(function (p) {
  var vivas = porPropiedad[p].filter(function (c) { return c.estado !== "CERRADA"; });
  ok("una sola captación viva en " + N.dir(p), vivas.length <= 1,
    vivas.map(function (c) { return c.id + "(" + c.estado + ")"; }).join(", "));
});
Object.keys(N.CONTRATOS).forEach(function (k) {
  var c = N.CONTRATOS[k];
  var vivas = (porPropiedad[c.propiedad] || []).filter(function (x) { return x.estado !== "CERRADA"; });
  ok("el inmueble con contrato firmado no tiene captación abierta · " + N.dir(c.propiedad),
    vivas.length === 0, vivas.map(function (x) { return x.id; }).join(", "));
});

/* ------------------------------------------------------------------
   6 · FECHAS — ninguna duración escrita a mano
   ------------------------------------------------------------------ */

grupo("6 · Fechas · toda duración se deriva de FECHA_REF");

/* Si `FECHA_REF` avanza un día, toda duración tiene que moverse con ella.
   Es la prueba que hace imposible volver a los desfases 11/12 y 6/7. */
var antes = capturaDuraciones();
var refOriginal = N.FECHA_REF;
N.FECHA_REF = "2026-08-11T08:30";
/* diasDesde/diasHasta usan el FECHA_REF del módulo, así que se le pasa
   explícitamente para no depender de que la constante sea mutable. */
var despues = capturaDuraciones("2026-08-11T08:30");
N.FECHA_REF = refOriginal;

function capturaDuraciones(ref) {
  return todos.map(function (a) {
    return {
      id: a.id,
      espera: a.desde ? N.diasDesde(N.v(a.desde), ref) : null,
      ventana: N.v(a.limite) === null || N.v(a.limite) === undefined ? null : N.diasHasta(N.v(a.limite), ref)
    };
  });
}
var movidos = 0;
antes.forEach(function (x, i) {
  if (x.espera !== null && despues[i].espera === x.espera + 1) movidos++;
});
ok("las esperas avanzan con la fecha de referencia", movidos === antes.filter(function (x) { return x.espera !== null; }).length,
  movidos + " de " + antes.filter(function (x) { return x.espera !== null; }).length);
var ventanas = antes.filter(function (x) { return x.ventana !== null; });
ok("las ventanas se acortan con la fecha de referencia",
  ventanas.every(function (x, i) {
    var d = despues.filter(function (y) { return y.id === x.id; })[0];
    return d.ventana === x.ventana - 1;
  }));

/* Ningún registro del fixture guarda un número de días: solo fechas. */
var fuente = fs.readFileSync(path.join(DIR, "nucleo-brox.js"), "utf8");
var zonaDatos = fuente.slice(fuente.indexOf("var PERSONAS"), fuente.indexOf("17 bis"));
var sospechosos = (zonaDatos.match(/\b(dias|días)(Transcurridos|SinContacto|SinReporte|Espera|Desde)\b\s*:/g) || []);
ok("ninguna entidad guarda un contador de días", sospechosos.length === 0, sospechosos.join(", "));
ok("los plazos se guardan como plazo, no como consumo",
  /plazoDias:/.test(zonaDatos) && !/consumidos:/.test(zonaDatos));

grupo("6.1 · Fechas · el vencimiento de un encargo se calcula");
Object.keys(N.CAPTACIONES).forEach(function (k) {
  var c = N.CAPTACIONES[k];
  if (!c.firmaEncargo) return;
  var v = N.venceEncargo(c);
  igual(k + " · firma + plazo = vencimiento",
    N.diasEntre(c.firmaEncargo, v), c.plazoDias);
});
igual("CAP-0022 vence el 22 ago", N.fdia(N.venceEncargo(N.cap("CAP-0022"))), "22 ago");
igual("y lleva 168 de 180 días consumidos",
  N.consumidoEncargo(N.cap("CAP-0022")).transcurridos + "/" + N.cap("CAP-0022").plazoDias, "168/180");

grupo("6.2 · Fechas · la ventana consumida admite sobrepaso");
var plazoCom = N.expedienteDe(N.asuntoPorId("comision")).filter(function (r) { return r[0] === "Plazo"; })[0];
ok("el gráfico del plazo declara transcurridos y previsto",
  plazoCom[3] && plazoCom[3].plazo && "transcurridos" in plazoCom[3].plazo && "previsto" in plazoCom[3].plazo);
ok("y aquí los transcurridos superan lo previsto",
  plazoCom[3].plazo.transcurridos > plazoCom[3].plazo.previsto,
  JSON.stringify(plazoCom[3].plazo));

/* ------------------------------------------------------------------
   7 · PERSONAS Y TEXTOS
   ------------------------------------------------------------------ */

grupo("7 · Personas · un nombre por persona");
var nombres = Object.keys(N.PERSONAS).map(function (k) { return N.PERSONAS[k].nombre; });
ok("ningún nombre repetido", new Set(nombres).size === nombres.length);
var diegos = nombres.filter(function (n) { return /^Diego /.test(n); });
igual("un solo Diego", diegos.length, 1);
igual("y se llama Diego Rojas", diegos[0], "Diego Rojas");

grupo("7.1 · Textos · ningún código técnico a la vista, salvo donde toca");
todos.forEach(function (a) {
  var texto = [N.v(a.titulo), N.v(a.hecho), N.v(a.reco), N.v(a.para)].join(" ");
  ok("sin código en la fila ni en la recomendación · " + a.id,
    !/\b(CAP|SOL|PRO|OPO|VIS|CON|PER|PROP)-\d+/.test(texto), texto);
});

grupo("7.2 · Textos · ningún porcentaje suelto en la lectura del equipo");
["AGENTE", "BROKER"].forEach(function (rol) {
  var h = N.hallazgoDe(rol);
  var sueltos = (h.c.match(/(\d+) %(?! de| del)/g) || []).filter(function (m) {
    /* «12 %» detrás de un nombre propio es la comparación, no una identidad */
    return h.c.indexOf("va al " + m) === -1 && h.c.indexOf("entre " + m) === -1 && h.c.indexOf("y " + m) === -1;
  });
  ok("hallazgo de " + rol + " sin porcentaje huérfano", sueltos.length === 0, sueltos.join(", "));
});

/* ------------------------------------------------------------------
   8 · LOS DOS HTML — que no vuelvan a calcular por su cuenta
   ------------------------------------------------------------------ */

grupo("8 · Prototipos · una sola fuente, ningún cálculo propio");

var HTML = ["inicio.html", "indicadores.html"];
var leidos = {};
HTML.forEach(function (f) {
  var ruta = path.join(DIR, f);
  if (!fs.existsSync(ruta)) { ok(f + " existe", false, "no está"); return; }
  leidos[f] = fs.readFileSync(ruta, "utf8");
  ok(f + " existe", true);
});

if (Object.keys(leidos).length === HTML.length) {
  var nucleoInline = /<script data-nucleo="brox">([\s\S]*?)<\/script>/;
  var bloques = HTML.map(function (f) {
    var m = nucleoInline.exec(leidos[f]);
    return m ? m[1] : null;
  });
  ok("los dos llevan el núcleo inlinado", bloques.every(Boolean));
  if (bloques.every(Boolean)) {
    ok("y es byte a byte el mismo bloque", bloques[0] === bloques[1],
      "difieren en " + Math.abs(bloques[0].length - bloques[1].length) + " caracteres");
    /* El bloque lleva delante el aviso de «generado»; lo demás tiene que ser
       la fuente tal cual, o el build se quedó atrás. */
    var sinAviso = bloques[0].replace(/^\s*\/\* GENERADO POR construir\.mjs[\s\S]*?\*\/\s*/, "");
    ok("y coincide con nucleo-brox.js",
      sinAviso.trim() === fuente.trim(),
      "el build no está al día: ejecuta node docs/ai/prototipos/construir.mjs");
    ok("y avisa de que está generado", /GENERADO POR construir\.mjs/.test(bloques[0]));
  }

  HTML.forEach(function (f) {
    /* Fuera del bloque generado, nadie recalcula el ritmo ni pinta a mano. */
    var cuerpo = leidos[f].replace(nucleoInline, "");
    ok(f + " · no reclasifica el ritmo",
      !/(cumplimiento|proyectado)\s*>=?\s*(1\.0|0\.85|UMBRAL)/.test(cuerpo));
    ok(f + " · no define umbrales de ritmo",
      !/UMBRAL\s*=\s*\{[^}]*llega/.test(cuerpo));
    ok(f + " · no escribe estados de ritmo a mano",
      !/\br:\s*"(alto|medio|bueno)"/.test(cuerpo));
    ok(f + " · no escribe metaEsperadaAHoy a mano",
      !/\be:\s*\d+\s*,\s*r:/.test(cuerpo));
    ok(f + " · no lleva duraciones cocinadas",
      !/"(hace )?\d+ (días|dias) (sin|desde|esperando)/.test(cuerpo));
    ok(f + " · usa el núcleo", /NUCLEO_BROX/.test(cuerpo));
  });
}

/* ------------------------------------------------------------------ */

console.log("\n" + "─".repeat(64));
if (fallos.length) {
  console.log(fallos.length + " de " + hechas + " comprobaciones han fallado:\n");
  fallos.forEach(function (f) { console.log("  · " + f); });
  process.exit(1);
}
console.log(hechas + " comprobaciones, todas verdes.");
process.exit(0);
