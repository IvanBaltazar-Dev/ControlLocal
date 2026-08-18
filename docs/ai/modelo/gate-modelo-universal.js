/* ====================================================================
   GATE DEL MODELO UNIVERSAL
   --------------------------------------------------------------------
   node docs/ai/modelo/gate-modelo-universal.js

   No comprueba que el modelo sea bonito: comprueba que **representa los
   ocho casos sin una sola excepción especial**, que es la condición que
   el plan pone para congelarlo.

   Tres bloques:

     A · REPRESENTABILIDAD — cada caso se instancia con las entidades
         declaradas. Si un caso necesitara un campo que no existe, una
         tabla nueva o una regla «solo para este tipo», falla.

     B · INVARIANTES — las reglas que el contrato declara se comprueban
         contra las instancias, no contra la intención.

     C · ANCLAJE EN EL REPOSITORIO — todo lo que el contrato dice que YA
         EXISTE tiene que aparecer de verdad en las migraciones Flyway.
         Es lo que impide que el documento se vuelva una lista de deseos.

   Salida: 0 el modelo se puede congelar · 1 todavía no.
   ==================================================================== */

"use strict";

const fs = require("fs");
const path = require("path");
const M = require("./modelo-universal.js");

const MIGRACIONES = path.join(__dirname, "../../../backend-spring/controllocal-app/src/main/resources/db/migration");
const DOMINIO = path.join(__dirname, "../../../backend-spring/controllocal-domain/src/main/java/com/controllocal/domain");

let fallos = [], hechas = 0, bloque = "";
function grupo(t) { bloque = t; console.log("\n" + t); }
function ok(nombre, cond, detalle) {
  hechas++;
  if (cond) { console.log("  ✓ " + nombre); return true; }
  console.log("  ✗ " + nombre + (detalle ? "  →  " + detalle : ""));
  fallos.push(bloque + " · " + nombre + (detalle ? " — " + detalle : ""));
  return false;
}

const catalogo = {};
M.ATRIBUTOS.forEach((a) => { catalogo[a.clave] = a; });

function aplicaA(attr, tipo) {
  return attr.aplica === "TODOS" || attr.aplica.indexOf(tipo) !== -1;
}

/* ==================================================================
   A · REPRESENTABILIDAD
   ================================================================== */

grupo("A · Los ocho casos se representan con las entidades declaradas");

/* Instancia un caso usando SOLO lo que el contrato declara. Si hiciera falta
   cualquier otra cosa, esta función no tendría dónde ponerla — y eso es
   justamente lo que se está comprobando. */
function instanciar(caso) {
  const filas = { Propiedad: [], Titularidad: [], Encargo: [], CondicionEconomica: [], AtributoPropiedad: [], HistoricoEconomico: [] };
  const idProp = "P:" + caso.id;

  filas.Propiedad.push({ id: idProp, tipoPropiedad: caso.propiedad.tipo });

  caso.titulares.forEach((t, i) => {
    filas.Titularidad.push({ idPropiedad: idProp, idRolPropietario: "R:" + caso.id + ":" + i, cuota: t.cuota, esRepresentante: t.representante, vigenteHasta: null });
  });

  Object.entries(caso.propiedad.atributos).forEach(([clave, valor]) => {
    filas.AtributoPropiedad.push({ idPropiedad: idProp, clave, valor });
  });

  caso.encargos.forEach((e, i) => {
    const idEnc = "E:" + caso.id + ":" + i;
    filas.Encargo.push({ id: idEnc, idPropiedad: idProp, operacion: e.operacion, vigente: e.vigente, idCondicionEconomica: "C:" + idEnc });
    filas.CondicionEconomica.push({ id: "C:" + idEnc, tipoOperacion: e.operacion, importeReferencia: e.importe, monedaReferencia: e.moneda });
    filas.HistoricoEconomico.push({ idPropiedad: idProp, idEncargo: idEnc, operacion: e.operacion, hito: "U", monto: e.importe, moneda: e.moneda });
  });

  return filas;
}

const instancias = {};
M.CASOS.forEach((caso) => {
  const f = instanciar(caso);
  instancias[caso.id] = f;
  ok(caso.nombre + " · se instancia", f.Propiedad.length === 1 && f.Encargo.length === caso.encargos.length);
});

grupo("A.1 · Ningún caso necesita una excepción especial");
M.CASOS.forEach((caso) => {
  /* Una excepción sería: una tabla fuera del contrato, una columna que solo
     aplica a este tipo fuera del catálogo, o una regla nombrada por tipo. */
  const usadas = Object.keys(instancias[caso.id]).filter((k) => instancias[caso.id][k].length);
  const fuera = usadas.filter((k) => !M.ENTIDADES[k]);
  ok(caso.nombre + " · solo usa entidades del contrato", fuera.length === 0, fuera.join(", "));

  const desconocidos = Object.keys(caso.propiedad.atributos).filter((c) => !catalogo[c]);
  ok(caso.nombre + " · sin atributos fuera del catálogo", desconocidos.length === 0, desconocidos.join(", "));

  const noAplican = Object.keys(caso.propiedad.atributos).filter((c) => !aplicaA(catalogo[c], caso.propiedad.tipo));
  ok(caso.nombre + " · sus atributos aplican a su tipo", noAplican.length === 0, noAplican.join(", "));
});

grupo("A.2 · Los atributos requeridos de cada tipo están cubiertos");
M.CASOS.forEach((caso) => {
  const faltan = M.ATRIBUTOS
    .filter((a) => a.requerido || (a.requeridoPara || []).indexOf(caso.propiedad.tipo) !== -1)
    .filter((a) => aplicaA(a, caso.propiedad.tipo))
    .map((a) => a.clave)
    .filter((c) => !(c in caso.propiedad.atributos));
  ok(caso.nombre + " · no le falta ningún atributo obligatorio", faltan.length === 0, faltan.join(", "));
});

/* ==================================================================
   B · INVARIANTES
   ================================================================== */

grupo("B · La operación vive en el encargo, no en la propiedad");

ok("la entidad Propiedad no declara operación",
  !("operacion" in M.ENTIDADES.Propiedad.campos));
ok("la entidad Propiedad no declara precio",
  !Object.keys(M.ENTIDADES.Propiedad.campos).some((c) => /precio|renta|importe/i.test(c)));
ok("la entidad Encargo sí declara operación",
  "operacion" in M.ENTIDADES.Encargo.campos);
ok("la condición económica cuelga del encargo",
  "idCondicionEconomica" in M.ENTIDADES.Encargo.campos);
ok("el histórico económico sabe de qué encargo es",
  "idEncargo" in M.ENTIDADES.HistoricoEconomico.campos);
ok("no existe una operación «AMBAS»",
  Object.keys(M.OPERACION).length === 2 && !("AMBAS" in M.OPERACION),
  Object.keys(M.OPERACION).join(","));

grupo("B.1 · Venta y alquiler simultáneos, sin caso especial");
const simult = instancias["venta-y-alquiler"];
const vivos = simult.Encargo.filter((e) => e.vigente);
ok("hay dos encargos vigentes", vivos.length === 2, String(vivos.length));
ok("con operaciones distintas", new Set(vivos.map((e) => e.operacion)).size === 2);
ok("sobre UNA sola propiedad", new Set(simult.Encargo.map((e) => e.idPropiedad)).size === 1);
ok("cada uno con su condición económica",
  simult.CondicionEconomica.length === 2 &&
  new Set(simult.CondicionEconomica.map((c) => c.importeReferencia)).size === 2);
ok("y con su propia serie de precios",
  new Set(simult.HistoricoEconomico.map((h) => h.operacion)).size === 2);

grupo("B.2 · No hay dos encargos vigentes de la MISMA operación");
M.CASOS.forEach((caso) => {
  const porOperacion = {};
  instancias[caso.id].Encargo.filter((e) => e.vigente)
    .forEach((e) => { porOperacion[e.operacion] = (porOperacion[e.operacion] || 0) + 1; });
  const repetida = Object.entries(porOperacion).filter(([, n]) => n > 1);
  ok(caso.nombre, repetida.length === 0, repetida.map(([o, n]) => o + "×" + n).join(", "));
});

grupo("B.3 · Titularidad");
M.CASOS.forEach((caso) => {
  const t = instancias[caso.id].Titularidad.filter((x) => x.vigenteHasta === null);
  const suma = t.reduce((s, x) => s + x.cuota, 0);
  const repr = t.filter((x) => x.esRepresentante).length;
  ok(caso.nombre + " · las cuotas suman 100", suma === 100, String(suma));
  ok(caso.nombre + " · exactamente un representante", repr === 1, String(repr));
});
ok("la copropiedad tiene tres titulares", instancias["copropiedad"].Titularidad.length === 3);

grupo("B.4 · Coherencia de la condición económica con su encargo");
M.CASOS.forEach((caso) => {
  const f = instancias[caso.id];
  const malas = f.Encargo.filter((e) => {
    const c = f.CondicionEconomica.find((x) => x.id === e.idCondicionEconomica);
    return !c || c.tipoOperacion !== e.operacion;
  });
  ok(caso.nombre, malas.length === 0, malas.map((e) => e.id).join(", "));
});

grupo("B.5 · El expediente de cierre lo elige la OPERACIÓN");
ok("alquiler abre expediente de alquiler", M.EXPEDIENTE_DE.ALQUILER === "EXPEDIENTE_ALQUILER");
ok("venta abre expediente de compraventa", M.EXPEDIENTE_DE.VENTA === "EXPEDIENTE_COMPRAVENTA");
ok("hay un expediente por operación y solo dos operaciones",
  Object.keys(M.EXPEDIENTE_DE).length === Object.keys(M.OPERACION).length);
ok("ningún tipo de propiedad aparece en la elección del expediente",
  !Object.keys(M.EXPEDIENTE_DE).some((k) => k in M.TIPO_PROPIEDAD));

grupo("B.6 · El cambio de intención conserva la historia");
const cambio = instancias["cambio-de-intencion"];
ok("queda el encargo cerrado", cambio.Encargo.some((e) => !e.vigente));
ok("y el nuevo vigente", cambio.Encargo.some((e) => e.vigente));
ok("las dos series de precio siguen separadas",
  new Set(cambio.HistoricoEconomico.map((h) => h.idEncargo)).size === 2);

grupo("B.7 · La demanda habla el mismo idioma que la oferta");
M.CASOS_DEMANDA.forEach((r) => {
  const fuera = r.criterios.map((c) => c.clave).filter((c) => !catalogo[c]);
  ok(r.nombre + " · sus criterios están en el catálogo", fuera.length === 0, fuera.join(", "));
  ok(r.nombre + " · declara operación", !!r.operacionBuscada);
  ok(r.nombre + " · el tipo buscado es una lista", Array.isArray(r.tiposPropiedad));
});

grupo("B.8 · Un match se puede decidir con lo declarado");
/* No se implementa el matcher: se comprueba que la información necesaria
   existe. Un indispensable incumplido descarta; el resto puntúa. */
function casa(req, caso) {
  const opEncargo = req.operacionBuscada === "COMPRA" ? "VENTA" : "ALQUILER";
  const encargo = caso.encargos.find((e) => e.vigente && e.operacion === opEncargo);
  if (!encargo) return { casa: false, motivo: "sin encargo vigente de esa operación" };
  if (req.tiposPropiedad.indexOf(caso.propiedad.tipo) === -1) return { casa: false, motivo: "otro tipo" };
  if (encargo.moneda !== req.moneda) return { casa: false, motivo: "otra moneda" };
  if (encargo.importe < req.presupuesto[0] || encargo.importe > req.presupuesto[1]) return { casa: false, motivo: "fuera de presupuesto" };
  const incumple = req.criterios.filter((c) => {
    if (c.peso !== "INDISPENSABLE") return false;
    const v = caso.propiedad.atributos[c.clave];
    if (v === undefined) return true;
    return c.operador === "MAYOR_IGUAL" ? !(v >= c.valor)
      : c.operador === "MENOR_IGUAL" ? !(v <= c.valor)
      : c.operador === "IGUAL" ? v !== c.valor : false;
  });
  if (incumple.length) return { casa: false, motivo: "incumple " + incumple.map((c) => c.clave).join(", ") };
  return { casa: true };
}
M.CASOS_DEMANDA.forEach((req) => {
  (req.deberiaCasarCon || []).forEach((idCaso) => {
    const caso = M.CASOS.find((c) => c.id === idCaso);
    const r = casa(req, caso);
    ok(req.nombre + " ↔ " + caso.nombre, r.casa, r.motivo);
  });
  /* Y no casa con lo que no debe: una compra no puede casar con un alquiler. */
  const cruzados = M.CASOS.filter((c) => (req.deberiaCasarCon || []).indexOf(c.id) === -1 && casa(req, c).casa);
  ok(req.nombre + " · no casa con lo que no debe", cruzados.length === 0, cruzados.map((c) => c.id).join(", "));
});

/* ==================================================================
   C · ANCLAJE EN EL REPOSITORIO
   ================================================================== */

grupo("C · Lo que el contrato dice que YA EXISTE, existe de verdad");

const sql = fs.existsSync(MIGRACIONES)
  ? fs.readdirSync(MIGRACIONES).filter((f) => f.endsWith(".sql"))
      .map((f) => fs.readFileSync(path.join(MIGRACIONES, f), "utf8")).join("\n")
  : "";
ok("se encuentran las migraciones Flyway", sql.length > 0, MIGRACIONES);

if (sql) {
  Object.entries(M.ENTIDADES).forEach(([nombre, e]) => {
    if (e.estado === "NUEVA") return;
    const existe = new RegExp("CREATE TABLE (IF NOT EXISTS )?" + e.tabla + "\\b", "i").test(sql);
    ok(nombre + " → tabla `" + e.tabla + "` (" + e.estado + ")", existe);
  });

  grupo("C.1 · Las columnas declaradas como EXISTE están en el esquema");
  Object.entries(M.ENTIDADES).forEach(([nombre, e]) => {
    if (e.estado === "NUEVA") return;
    Object.entries(e.campos).forEach(([campo, def]) => {
      if (def.estado !== "EXISTE") return;
      /* Un campo del contrato puede ser una columna, VARIAS columnas o una
         relación N:M. Las tres formas se comprueban igual: el nombre tiene
         que aparecer en alguna migración. */
      const objetivos = def.relacion ? [def.relacion]
        : def.columnas ? def.columnas
        : [def.columna || campo.replace(/[A-Z]/g, (c) => "_" + c.toLowerCase())];
      const faltan = objetivos.filter((o) => !new RegExp("\\b" + o + "\\b").test(sql));
      ok(nombre + "." + campo + " → " + objetivos.map((o) => "`" + o + "`").join(" "),
        faltan.length === 0, "no aparece: " + faltan.join(", "));
    });
  });
}

grupo("C.2 · La operación está validada en el dominio, y sin defecto");
const captacion = fs.existsSync(path.join(DOMINIO, "comercial/Captacion.java"))
  ? fs.readFileSync(path.join(DOMINIO, "comercial/Captacion.java"), "utf8") : "";
ok("Captacion.java existe", captacion.length > 0);

/* Hasta el 2026-08-18 esto comprobaba la validación EN LÍNEA
   (`!"A".equals(...) && !"V".equals(...)`). La validación no desapareció: se
   mudó a `OperacionInmobiliaria`, que además rechaza el nulo y explica por qué
   COMPRA y AMBAS no existen. El gate pasa a comprobar la INTENCIÓN —que el
   encargo no acepte cualquier cosa y que no tenga defecto— en vez de una
   implementación concreta, que es lo que lo hacía frágil. */
ok("y delega la validación en `OperacionInmobiliaria`",
  /OperacionInmobiliaria\.desde\(motivoOperacion\)/.test(captacion),
  "el encargo tiene que rechazar cualquier operación que no sea VENTA o ALQUILER");
ok("sin valor por defecto: un olvido ya no se convierte en alquiler",
  /private String motivoOperacion;/.test(captacion),
  "`= \"A\"` volvió: eso archiva encargos de venta como alquiler en silencio");

const operacion = fs.existsSync(path.join(DOMINIO, "inmueble/OperacionInmobiliaria.java"))
  ? fs.readFileSync(path.join(DOMINIO, "inmueble/OperacionInmobiliaria.java"), "utf8") : "";
ok("OperacionInmobiliaria existe y congela el vocabulario en DOS valores",
  /VENTA\("V"\)/.test(operacion) && /ALQUILER\("A"\)/.test(operacion),
  "la operación es VENTA o ALQUILER; la perspectiva la da el rol");
ok("y rechaza AMBAS explicando que son DOS encargos",
  /COMBINADAS/.test(operacion) && /DOS encargos/.test(operacion),
  "un valor combinado obliga a decidir a mano qué pasa con el alquiler al vender");
ok("y rechaza COMPRA explicando que es una perspectiva",
  /PERSPECTIVAS/.test(operacion) && /perspectiva/.test(operacion),
  "comprar es VENTA vista desde el cliente");

const precio = fs.existsSync(path.join(DOMINIO, "inmueble/PrecioPropiedad.java"))
  ? fs.readFileSync(path.join(DOMINIO, "inmueble/PrecioPropiedad.java"), "utf8") : "";
ok("y el hito de precio tampoco supone la operación",
  /private String operacion;/.test(precio),
  "un precio de venta guardado en la serie de alquiler no lo detecta ningún CHECK");

grupo("C.3 · Lo que el contrato dice que hay que quitar, hoy está");
/* Si una de estas desapareciera por otro camino, el contrato estaría
   desfasado y la migración planificada sobraría. Se comprueba contra el
   ESQUEMA, que es donde vive la verdad. */
Object.keys(M.ENTIDADES.Propiedad.seVan).forEach((columna) => {
  ok("`" + columna + "` sigue en el esquema (y por eso hay que migrarla)",
    new RegExp("\\b" + columna + "\\b").test(sql),
    "ya no está: revisa el contrato, puede haber quedado obsoleto");
});

/* ================================================================== */

console.log("\n" + "─".repeat(64));
if (fallos.length) {
  console.log(fallos.length + " de " + hechas + " comprobaciones han fallado.\n");
  fallos.forEach((f) => console.log("  · " + f));
  console.log("\nEL MODELO NO SE PUEDE CONGELAR TODAVÍA.");
  process.exit(1);
}
console.log(hechas + " comprobaciones, todas verdes.");
console.log("Los ocho casos se representan sin excepciones: el modelo se puede congelar.");
process.exit(0);
