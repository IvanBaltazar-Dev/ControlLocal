/* ====================================================================
   MOTOR DE CAPTURA — la máquina de preguntas
   --------------------------------------------------------------------
   node docs/ai/modelo/motor-captura.js       (demuestra tres altas)

   Una sola implementación para dos caras:

     + Registrar (Angular)  →  presenta la pregunta que el motor devuelve
     KAIROS                 →  extrae respuestas del lenguaje natural y
                               las mete por la MISMA puerta

   La regla que lo hace posible: **el motor no tiene un guion por tipo**.
   El plan de preguntas se DERIVA de `modelo-universal.js` — del catálogo
   de atributos, de a qué tipos aplica cada uno y de cuáles son
   obligatorios. Añadir un tipo de propiedad no añade un formulario:
   añade filas al catálogo.

   Esto es una maqueta ejecutable del contrato, no la implementación: en
   producción vive en el backend (`service/captura`), porque Angular
   presenta y KAIROS conversa, pero ninguno de los dos decide qué se
   pregunta.
   ==================================================================== */

"use strict";

const M = require("./modelo-universal.js");

/* ------------------------------------------------------------------
   1 · Qué se puede registrar
   ------------------------------------------------------------------ */

const INTENCIONES = {
  PROPIEDAD: { rotulo: "Propiedad", queCrea: ["Propiedad", "Titularidad", "Encargo", "CondicionEconomica"] },
  PROPIETARIO: { rotulo: "Propietario", queCrea: ["Persona", "PersonaRol"] },
  CLIENTE: { rotulo: "Cliente", queCrea: ["Persona", "PersonaRol", "Requerimiento"] },
  ACTIVIDAD: { rotulo: "Actividad", queCrea: ["InteraccionComercial"] },
};

/* Un paso es una PREGUNTA con todo lo que la pantalla necesita para
   pintarla y KAIROS para entenderla. Nada más. */
function paso(id, pregunta, tipo, extra) {
  return Object.assign({ id, pregunta, tipo, obligatorio: true }, extra || {});
}

/* ------------------------------------------------------------------
   2 · El plan, derivado del modelo
   ------------------------------------------------------------------ */

/* Los tres primeros pasos son fijos porque son los que ORDENAN el resto:
   sin operación y sin tipo no se sabe qué preguntar después. */
function planBase() {
  return [
    paso("operacion", "¿Qué quieres hacer con la propiedad?", "OPCION", {
      opciones: [
        { valor: "ALQUILER", rotulo: "Alquilar" },
        { valor: "VENTA", rotulo: "Vender" },
        { valor: "AMBAS", rotulo: "Las dos cosas" },
      ],
      porQue: "Decide el precio, el expediente de cierre y la comisión.",
    }),
    paso("tipoPropiedad", "¿Qué tipo de propiedad es?", "OPCION", {
      opciones: Object.entries(M.TIPO_PROPIEDAD).map(([v, t]) => ({ valor: v, rotulo: t.rotulo })),
      porQue: "Decide qué características tiene sentido pedir.",
    }),
    paso("propietario", "¿De quién es?", "BUSQUEDA", {
      busca: "PROPIETARIO",
      porQue: "Antes de pedir datos nuevos se busca si ya está registrado.",
      permiteCrear: true,
    }),
  ];
}

/* A partir de aquí, TODO sale del catálogo. Esta función es la que
   demuestra que no hay siete formularios. */
function pasosDeAtributos(tipo) {
  const aplica = (a) => a.aplica === "TODOS" || a.aplica.indexOf(tipo) !== -1;
  const requerido = (a) => a.requerido || (a.requeridoPara || []).indexOf(tipo) !== -1;
  /* La CAPTURA sólo pregunta lo ACTIVO, igual que en el Core: `aplicablesA`
     filtra `activo` y el trigger `exigir_atributo_gobernado` lo exige otra vez.
     Una clave retirada sigue en el catálogo porque su valor se sigue LEYENDO,
     pero volver a preguntarla sería reabrir la puerta que `V84` cerró. */
  return M.ATRIBUTOS.filter((a) => !a.retirado).filter(aplica).map((a) =>
    paso("attr:" + a.clave, preguntaDe(a), tipoDePregunta(a), {
      obligatorio: requerido(a),
      unidad: a.unidad,
      porQue: requerido(a) ? "Sin esto la ficha no se puede publicar." : null,
    }));
}

/* El rótulo se deriva de la clave; en producción vendrá del catálogo con
   su texto, pero la forma es la misma: NO se escribe una vez por tipo. */
function preguntaDe(a) {
  const t = a.clave.replace(/_/g, " ");
  const texto = t.charAt(0).toUpperCase() + t.slice(1);
  return "¿" + texto + "?" + (a.unidad ? " (" + a.unidad + ")" : "");
}
function tipoDePregunta(a) {
  return { ENTERO: "NUMERO", DECIMAL: "NUMERO", BOOLEANO: "SI_NO", LISTA: "MULTIPLE" }[a.tipo] || "TEXTO";
}

/* Un bloque económico POR OPERACIÓN: es lo que hace que «las dos cosas»
   no necesite ningún caso especial — se repite el mismo bloque. */
function pasosEconomicos(operacion) {
  const esVenta = operacion === "VENTA";
  return [
    paso("precio:" + operacion, esVenta ? "¿En cuánto se vende?" : "¿Cuánto es la renta mensual?", "DINERO", {
      porQue: "El importe lleva su moneda y su base; nunca se interpreta por magnitud.",
    }),
    paso("comision:" + operacion, esVenta ? "¿Qué comisión por la venta?" : "¿Qué comisión por el alquiler?", "COMISION", {
      obligatorio: false,
      porQue: "Si no se declara, se usa la política de la organización.",
    }),
    paso("exclusividad:" + operacion, "¿El encargo es exclusivo?", "SI_NO", { obligatorio: false }),
    paso("plazo:" + operacion, "¿Hasta cuándo vale el encargo?", "FECHA", { obligatorio: false }),
  ];
}

function planCompleto(respuestas) {
  const plan = planBase();
  const tipo = respuestas.tipoPropiedad;
  const op = respuestas.operacion;
  if (!tipo || !op) return plan;

  plan.push(paso("direccion", "¿Dónde está?", "DIRECCION", {
    porQue: "La ubicación decide con qué requerimientos puede casar.",
  }));
  plan.push(...pasosDeAtributos(tipo));
  (op === "AMBAS" ? ["VENTA", "ALQUILER"] : [op]).forEach((o) => plan.push(...pasosEconomicos(o)));
  return plan;
}

/* ------------------------------------------------------------------
   3 · La sesión de captura
   Lo que ve el consumidor: «dame la siguiente pregunta» y «toma esta
   respuesta». Da igual si la respuesta viene de un clic o de una frase.
   ------------------------------------------------------------------ */

function abrirSesion(intencion, contexto) {
  if (!INTENCIONES[intencion]) throw new Error("intención desconocida: " + intencion);
  return {
    intencion,
    /* El contexto es lo que el motor YA SABE y por tanto no pregunta:
       desde qué pantalla se abrió, qué propietario estaba abierto, qué
       dijo KAIROS en la frase inicial. */
    respuestas: Object.assign({}, contexto || {}),
    confirmada: false,
  };
}

function siguiente(sesion) {
  const plan = planCompleto(sesion.respuestas);
  for (const p of plan) {
    if (p.id in sesion.respuestas) continue;
    if (!p.obligatorio && sesion.respuestas["__saltar_opcionales"]) continue;
    return p;
  }
  return null;
}

function responder(sesion, idPaso, valor) {
  sesion.respuestas[idPaso] = valor;
  return siguiente(sesion);
}

/* Lo que falta para poder confirmar. Es lo que permite a KAIROS decir
   «me falta el metraje» en vez de fallar al guardar. */
function loQueFalta(sesion) {
  return planCompleto(sesion.respuestas)
    .filter((p) => p.obligatorio && !(p.id in sesion.respuestas))
    .map((p) => p.id);
}

/* El resumen que se enseña ANTES de crear nada. Vale igual para la
   pantalla de confirmación y para que KAIROS lo lea en voz alta. */
function resumen(sesion) {
  const r = sesion.respuestas;
  const ops = r.operacion === "AMBAS" ? ["VENTA", "ALQUILER"] : [r.operacion];
  return {
    intencion: sesion.intencion,
    propiedad: {
      tipo: r.tipoPropiedad,
      direccion: r.direccion,
      /* Un opcional sin responder NO es un atributo: se salta, no se guarda
         vacío. Guardar nulos llenaría la tabla de filas que no dicen nada y
         el matcher no sabría distinguir «no aplica» de «no lo sé». */
      atributos: Object.keys(r).filter((k) => k.startsWith("attr:") && r[k] !== null && r[k] !== undefined)
        .reduce((o, k) => (o[k.slice(5)] = r[k], o), {}),
    },
    titular: r.propietario,
    encargos: ops.filter(Boolean).map((o) => ({
      operacion: o,
      importe: r["precio:" + o],
      exclusividad: r["exclusividad:" + o] || false,
    })),
    falta: loQueFalta(sesion),
    listoParaConfirmar: loQueFalta(sesion).length === 0,
  };
}

/* Confirmar es lo único que ESCRIBE. Antes de esto no existe nada, ni
   borradores a medias: es lo que hace seguro que KAIROS conduzca. */
function confirmar(sesion) {
  if (!resumen(sesion).listoParaConfirmar) {
    return { creado: null, error: "faltan datos obligatorios", falta: loQueFalta(sesion) };
  }
  sesion.confirmada = true;
  const r = resumen(sesion);
  return {
    creado: {
      Propiedad: 1,
      Titularidad: 1,
      Encargo: r.encargos.length,
      CondicionEconomica: r.encargos.length,
      AtributoPropiedad: Object.keys(r.propiedad.atributos).length,
    },
    resumen: r,
  };
}

module.exports = { INTENCIONES, abrirSesion, siguiente, responder, resumen, confirmar, loQueFalta, planCompleto };

/* ------------------------------------------------------------------
   4 · Demostración
   ------------------------------------------------------------------ */

if (require.main === module) {
  const guiones = [
    {
      titulo: "Local comercial en alquiler — por clics",
      intencion: "PROPIEDAD",
      respuestas: {
        operacion: "ALQUILER", tipoPropiedad: "LOCAL_COMERCIAL", propietario: "Sr. Aliaga",
        direccion: "Jr. Ica 118, Breña", "attr:metraje_total": 85, "attr:rubro_permitido": "gastronómico",
        "precio:ALQUILER": "USD 2,900",
      },
    },
    {
      titulo: "Departamento en venta — la frase de KAIROS",
      frase: "Registra un departamento en Miraflores. La señora Torres lo quiere vender en US$ 180 mil.",
      intencion: "PROPIEDAD",
      /* Lo que KAIROS extrae de la frase entra como CONTEXTO: son
         respuestas ya dadas, no un camino distinto. */
      contexto: {
        operacion: "VENTA", tipoPropiedad: "DEPARTAMENTO",
        propietario: "Sra. Torres", direccion: "Miraflores", "precio:VENTA": "USD 180,000",
      },
      respuestas: { "attr:metraje_total": 92, "attr:dormitorios": 3 },
    },
    {
      titulo: "Terreno en venta y alquiler a la vez",
      intencion: "PROPIEDAD",
      respuestas: {
        operacion: "AMBAS", tipoPropiedad: "TERRENO", propietario: "Inversiones Lume",
        direccion: "Av. Brasil 900", "attr:metraje_total": 1200, "attr:zonificacion": "RDM",
        "precio:VENTA": "USD 540,000", "precio:ALQUILER": "USD 6,800",
      },
    },
  ];

  for (const g of guiones) {
    console.log("\n" + "═".repeat(66));
    console.log("  " + g.titulo);
    if (g.frase) console.log("  KAIROS: «" + g.frase + "»");
    console.log("═".repeat(66));

    const s = abrirSesion(g.intencion, g.contexto);
    if (g.contexto) {
      console.log("  ya sabe: " + Object.keys(g.contexto).join(", "));
      console.log("  → no vuelve a preguntar nada de eso\n");
    }
    let preguntas = 0, p;
    while ((p = siguiente(s))) {
      const valor = g.respuestas[p.id] !== undefined ? g.respuestas[p.id]
        : (g.contexto && g.contexto[p.id] !== undefined ? g.contexto[p.id] : null);
      if (valor === null && !p.obligatorio) { s.respuestas[p.id] = null; continue; }
      if (valor === null) { console.log("  ⤷ FALTA: " + p.pregunta); break; }
      preguntas++;
      console.log("  " + String(preguntas).padStart(2) + ". " + p.pregunta.padEnd(46) + " → " + valor);
      responder(s, p.id, valor);
    }
    const r = resumen(s);
    console.log("\n  preguntas hechas: " + preguntas + " · listo: " + (r.listoParaConfirmar ? "sí" : "no, falta " + r.falta.join(", ")));
    const c = confirmar(s);
    if (c.creado) {
      console.log("  al confirmar crea: " +
        Object.entries(c.creado).map(([k, n]) => n + " " + k).join(" · "));
    }
  }

  /* La comprobación que importa: nadie pregunta lo que no aplica. */
  console.log("\n" + "═".repeat(66));
  console.log("  Preguntas por tipo — derivadas del catálogo, no escritas");
  console.log("═".repeat(66));
  Object.keys(M.TIPO_PROPIEDAD).forEach((tipo) => {
    const plan = planCompleto({ operacion: "VENTA", tipoPropiedad: tipo });
    const attrs = plan.filter((p) => p.id.startsWith("attr:"));
    const obl = attrs.filter((p) => p.obligatorio).length;
    console.log("  " + tipo.padEnd(18) + String(attrs.length).padStart(2) + " características (" + obl + " obligatorias)   " +
      attrs.slice(0, 4).map((p) => p.id.slice(5)).join(", ") + (attrs.length > 4 ? "…" : ""));
  });
  const conDormitorios = Object.keys(M.TIPO_PROPIEDAD).filter((t) =>
    planCompleto({ operacion: "VENTA", tipoPropiedad: t }).some((p) => p.id === "attr:dormitorios"));
  console.log("\n  «dormitorios» solo se pregunta en: " + conDormitorios.join(", "));
  const conCarga = Object.keys(M.TIPO_PROPIEDAD).filter((t) =>
    planCompleto({ operacion: "VENTA", tipoPropiedad: t }).some((p) => p.id === "attr:carga_electrica_kw"));
  console.log("  «carga eléctrica» solo en:        " + conCarga.join(", "));
}
