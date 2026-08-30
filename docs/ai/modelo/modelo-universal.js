/* ====================================================================
   MODELO UNIVERSAL BROX — Propiedad × Operación
   --------------------------------------------------------------------
   El contrato congelado, escrito como DATO para poder comprobarlo.
   `gate-modelo-universal.js` lo lee, instancia los ocho casos del gate y
   falla si alguno necesita una excepción especial.

   LA TESIS DEL MODELO, EN UNA FRASE

     Una propiedad NO tiene operación. Un titular ENCARGA una operación
     sobre una propiedad, y ese encargo es el que lleva la operación, la
     condición económica, el plazo, la comisión y su propio histórico.

   De ahí sale todo lo demás sin casos especiales:

     · «en venta y alquiler a la vez» son DOS encargos vivos, no un
       booleano ni una tercera operación `AMBAS`;
     · el precio de venta y la renta no se mezclan porque cuelgan de
       encargos distintos;
     · el expediente de cierre se elige por la operación del encargo, no
       por el tipo de propiedad;
     · un cambio de intención (dejo de alquilar, ahora vendo) cierra un
       encargo y abre otro, y la historia de los dos se conserva.

   ESTADO DE CADA PIEZA

     EXISTE      ya está en el esquema y se conserva tal cual
     AMPLIA      existe y se le añaden columnas
     NUEVA       no existe
     RENOMBRA    existe con otro nombre y pasa a llamarse como aquí
   ==================================================================== */

"use strict";

/* ------------------------------------------------------------------
   0 · Vocabularios
   ------------------------------------------------------------------ */

const OPERACION = { VENTA: "VENTA", ALQUILER: "ALQUILER" };

/* Siete tipos, y `OTRO` para no bloquear un alta por falta de catálogo.
   Los códigos de una letra son los que ya viajan en `propiedad.tipo_inmueble`. */
const TIPO_PROPIEDAD = {
  LOCAL_COMERCIAL: { codigo: "L", rotulo: "Local comercial" },
  OFICINA: { codigo: "O", rotulo: "Oficina" },
  DEPARTAMENTO: { codigo: "D", rotulo: "Departamento" },
  CASA: { codigo: "C", rotulo: "Casa" },
  TERRENO: { codigo: "T", rotulo: "Terreno" },
  ALMACEN: { codigo: "A", rotulo: "Almacén / depósito" },
  OTRO: { codigo: "X", rotulo: "Otro" },
};

/* La operación NO nace de la propiedad: un terreno se vende y se alquila,
   una casa también. Esta tabla existe solo para ORDENAR las preguntas del
   motor de registro, nunca para prohibir. */
const OPERACION_HABITUAL = {
  LOCAL_COMERCIAL: [OPERACION.ALQUILER, OPERACION.VENTA],
  OFICINA: [OPERACION.ALQUILER, OPERACION.VENTA],
  DEPARTAMENTO: [OPERACION.VENTA, OPERACION.ALQUILER],
  CASA: [OPERACION.VENTA, OPERACION.ALQUILER],
  TERRENO: [OPERACION.VENTA, OPERACION.ALQUILER],
  ALMACEN: [OPERACION.ALQUILER, OPERACION.VENTA],
  OTRO: [OPERACION.VENTA, OPERACION.ALQUILER],
};

/* Qué expediente abre el cierre. Se elige por OPERACIÓN, nunca por tipo de
   propiedad: es lo que evita meter una compraventa dentro de una solicitud
   de alquiler. */
const EXPEDIENTE_DE = {
  [OPERACION.ALQUILER]: "EXPEDIENTE_ALQUILER",
  [OPERACION.VENTA]: "EXPEDIENTE_COMPRAVENTA",
};

/* ------------------------------------------------------------------
   1 · Atributos gobernados
   El error a evitar es un EAV libre: si cualquiera puede inventar una
   clave, ni la búsqueda ni el matcher pueden comparar dos propiedades.
   Cada atributo declara TIPO, UNIDAD y a qué tipos de propiedad aplica; el
   catálogo es por organización pero los de aquí son del sistema y no se
   pueden borrar.

   ESTA LISTA NO ES EL CATÁLOGO ENTERO, y confundirlos ha costado caro. La
   autoridad del catálogo es `catalogo_atributo` en el Core; esto nació como el
   subconjunto que los ocho casos del gate necesitan para instanciarse, más lo
   que después haya hecho falta declarar para no contradecirlo. Medido
   contra `controllocal_dev` el **2026-08-29, después de `V85`**: el Core lleva
   **115** claves de sujeto PROPIEDAD (110 atributos activos + 1 retirada + 4
   estructurales) y aquí se declaran **40**. La diferencia NO es deuda de este
   corte —viene de los Cortes 2, 3 y 4, que ampliaron el Core sin pasar por
   aquí— y está registrada como **N18** en `pendientes-brox.md`.

   (Estas dos cifras decían **97** y **22**, medidas el 2026-08-25. Caducaron con
   `V85` y **sobrevivieron a la ronda que corrigió su gemela** en
   `pendientes-brox.md`: se arregló allí y se dejó viva aquí. Es la razón por la
   que ahora toda cifra de una evidencia se vuelve a medir contra el SHA que se
   firma, y no contra el anterior.)

   Lo que sí es obligación de este archivo: **no contradecir al Core**. Una
   clave declarada aquí tiene que decir lo mismo que dice el catálogo, porque
   el gate la lee como si fuera el contrato. Por eso `retirado` existe: una
   clave que el Core retiró no se borra —su valor se sigue leyendo— pero
   tampoco se sigue anunciando como viva.
   ------------------------------------------------------------------ */

const ATRIBUTOS = [
  /* comunes a todo */
  { clave: "metraje_total", tipo: "DECIMAL", unidad: "m2", aplica: "TODOS", requerido: true },
  { clave: "antiguedad_anios", tipo: "ENTERO", unidad: "años", aplica: "TODOS" },
  { clave: "estacionamientos", tipo: "ENTERO", aplica: "TODOS" },

  /* construido */
  { clave: "metraje_construido", tipo: "DECIMAL", unidad: "m2", aplica: ["LOCAL_COMERCIAL", "OFICINA", "DEPARTAMENTO", "CASA", "ALMACEN"] },
  { clave: "ambientes", tipo: "ENTERO", aplica: ["LOCAL_COMERCIAL", "OFICINA", "DEPARTAMENTO", "CASA", "ALMACEN"] },
  { clave: "piso", tipo: "TEXTO", aplica: ["LOCAL_COMERCIAL", "OFICINA", "DEPARTAMENTO"] },
  { clave: "cuota_mantenimiento", tipo: "DECIMAL", unidad: "moneda", aplica: ["LOCAL_COMERCIAL", "OFICINA", "DEPARTAMENTO"] },

  /* vivienda */
  { clave: "dormitorios", tipo: "ENTERO", aplica: ["DEPARTAMENTO", "CASA"], requeridoPara: ["DEPARTAMENTO", "CASA"] },
  { clave: "banos", tipo: "DECIMAL", aplica: ["DEPARTAMENTO", "CASA"] },
  { clave: "amoblado", tipo: "BOOLEANO", aplica: ["DEPARTAMENTO", "CASA"] },
  { clave: "pisos_edificacion", tipo: "ENTERO", aplica: ["CASA"] },

  /* comercial e industrial */
  { clave: "frente", tipo: "DECIMAL", unidad: "m", aplica: ["LOCAL_COMERCIAL", "TERRENO", "ALMACEN"] },
  { clave: "carga_electrica_kw", tipo: "DECIMAL", unidad: "kW", aplica: ["LOCAL_COMERCIAL", "OFICINA", "ALMACEN"] },
  { clave: "altura_libre", tipo: "DECIMAL", unidad: "m", aplica: ["ALMACEN", "LOCAL_COMERCIAL"] },
  { clave: "apto_licencia_funcionamiento", tipo: "BOOLEANO", aplica: ["LOCAL_COMERCIAL", "OFICINA", "ALMACEN"] },
  /* La oficina lleva rubro: `detalle_local_comercial` es obligatoria para L
     **y O**, y las dos oficinas de la base lo tienen relleno. Lo descubrió el
     ensayo de V48, no el diseño. */
  { clave: "rubro_permitido", tipo: "TEXTO", aplica: ["LOCAL_COMERCIAL", "OFICINA", "ALMACEN"] },

  /* suelo */
  { clave: "zonificacion", tipo: "TEXTO", aplica: ["TERRENO", "LOCAL_COMERCIAL", "ALMACEN", "CASA"], requeridoPara: ["TERRENO"] },
  /* `area_terreno` PIERDE `TERRENO` en el Corte 5 · 5B (`V85`), por D-7 del
     titular. Para un terreno nombraba la MISMA superficie que `metraje_total`
     —que es la canónica: columna `propiedad.metraje`, NOT NULL y ALT en los
     siete—, y dos claves para una verdad no comparan nada: un agente escribía
     500 en una, otro en la otra, y el buscador no podía ordenar por superficie.

     En CASA y ALMACÉN **sí** dice algo distinto y por eso se queda: una casa se
     tasa por el par (terreno, construida) y una nave tiene patio además de
     techo. La retirada es de la APLICABILIDAD, no de la clave.

     El dato existente no se perdió: los valores sobre terrenos que coincidían
     con su `metraje_total` se retiraron dejando linaje (`RETIRADA`), y los que
     NO coincidían se CONSERVAN sin tocar y se cuentan — no se sabe cuál de las
     dos superficies es la correcta, y elegir una sería inventar. */
  { clave: "area_terreno", tipo: "DECIMAL", unidad: "m²", aplica: ["CASA", "ALMACEN"],
    retiradaDe: ["TERRENO"], retiradaPor: "V85",
    nota: "en un TERRENO duplicaba metraje_total, que es la superficie canónica (D-7)" },

  /* servicios del suelo — Corte 5 · 5A (`V84`)

     `servicios_disponibles` era una LISTA **sin una sola opción sembrada**, es
     decir texto libre disfrazado de vocabulario: «Agua, luz y desagüe» y «agua
     y desagüe» son la misma fila para cualquier comparador. Y ninguna de las
     dos dice lo único que hace falta saber en un terreno —si el servicio está
     CONECTADO o sólo tiene FACTIBILIDAD APROBADA—, así que su legado quedó
     AMBIGUO y permanece FALTANTE: no se tradujo nada.

     Se queda escrita aquí, retirada, y no se borra: sus valores se conservan y
     la ficha los sigue leyendo con su rótulo y su tipo. Lo que se cerró es la
     escritura. */
  { clave: "servicios_disponibles", tipo: "LISTA", aplica: ["TERRENO"],
    retirado: true, retiradaPor: "V84",
    sustituidaPor: ["agua_desague", "energia_electrica"],
    nota: "activo = false en el Core: no se pregunta ni se admite valor nuevo; se sigue leyendo" },

  { clave: "agua_desague", tipo: "LISTA", aplica: ["TERRENO", "ALMACEN"],
    exigencia: { TERRENO: "PUB", ALMACEN: "OPC" },
    opciones: ["CONECTADO", "CON_FACTIBILIDAD_APROBADA", "SIN_SERVICIO"] },

  { clave: "energia_electrica", tipo: "LISTA", aplica: ["TERRENO"],
    exigencia: { TERRENO: "PUB" },
    opciones: ["CONECTADO", "CON_FACTIBILIDAD_APROBADA", "SIN_SERVICIO"] },

  /* ocupación — Corte 5 · 5A (`V84`), por D-C5-1 y D-1 del titular

     Aplica a LOS SIETE tipos y aun así `aplica_todos = false` en el Core, con
     una fila explícita por tipo. Por eso se escribe la lista y no "TODOS": son
     dos cosas distintas en el esquema, y esa doble autoridad de aplicabilidad
     (D-5) sigue sin gate — está registrada en `pendientes-brox.md`. */
  { clave: "estado_ocupacion", tipo: "LISTA",
    aplica: ["LOCAL_COMERCIAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO"],
    aplicaTodos: false,
    exigencia: { LOCAL_COMERCIAL: "OPC", OFICINA: "OPC", DEPARTAMENTO: "OPC", CASA: "OPC",
                 TERRENO: "OPC", ALMACEN: "OPC", OTRO: "OPC" },
    opciones: ["DESOCUPADO", "OCUPADO_POR_EL_PROPIETARIO", "OCUPADO_POR_INQUILINO",
               "OCUPADO_POR_TERCEROS_SIN_TITULO"] },

  /* ================================================================
     El suelo y sus parámetros urbanísticos — Corte 5 · 5B (`V85`)

     Hasta aquí un TERRENO se describía con DIECISÉIS características —medidas
     contra `controllocal_dev` el 2026-08-29— y **ninguna hablaba del suelo como
     suelo**. Las dieciséis, y la suma cuadra: **cinco** medidas o identidad que
     valen para cualquier activo, **cuatro** registrales, **tres** de servicios
     —`gas`, `agua_desague` y `energia_electrica`; `servicios_disponibles` ya
     estaba retirada desde `V84`—, el acceso de vehículo, que **no** es un
     servicio, la vía como texto libre, la ocupación, y una `area_terreno` que
     repite la superficie que ya está en `metraje_total`.
     Dos lotes de 500 m² en el mismo distrito eran la misma ficha aunque uno
     fuera urbano habilitado con ocho pisos de altura normativa y el otro un
     eriazo con CIRA pendiente y una trocha delante.

     LA EXIGENCIA: **una sola `PUB`**, `condicion_terreno` (D-3 del titular), y
     las otras diecisiete `OPC`. La columna «nivel» de
     `auditoria-profundidad-inmobiliaria.md` §3.8 propone cinco `PUB` más: es
     PROPUESTA, no autoridad, y D-1 dice que no se eleva nada más en este corte.
     El precedente está medido: en el Corte 4 la auditoría proponía `PUB` para
     ocho claves y **las ocho nacieron `OPC`**.

     `tipo_via_acceso` CONVIVE con `via_de_acceso` —que sigue viva desde `V81`—:
     la segunda dice CUÁL es la vía, ésta de qué CLASE es. No es la duplicidad
     de `area_terreno`.
     ================================================================ */

  /* qué ES este suelo */
  { clave: "condicion_terreno", tipo: "LISTA", aplica: ["TERRENO"],
    exigencia: { TERRENO: "PUB" },
    opciones: ["URBANO_HABILITADO", "EN_PROCESO_DE_HABILITACION", "RUSTICO_ERIAZO",
               "ZONA_INFORMAL_SIN_HABILITAR"],
    nota: "PUB y no ALT (D-3): BROX debe poder REGISTRAR un terreno cuya condición no se confirmó" },
  { clave: "situacion_registral", tipo: "LISTA", aplica: ["TERRENO", "CASA"],
    opciones: ["INSCRITO_EN_SUNARP", "EN_SANEAMIENTO", "NO_INSCRITO_SOLO_POSESION"] },
  { clave: "zona_de_riesgo", tipo: "BOOLEANO", aplica: ["TERRENO", "CASA"] },
  { clave: "restriccion_arqueologica", tipo: "LISTA", aplica: ["TERRENO"],
    opciones: ["NO_APLICA", "CIRA_OBTENIDO", "EN_TRAMITE", "REQUERIDO_NO_INICIADO"] },

  /* cómo ES: forma, relieve y vía */
  { clave: "tipo_via_acceso", tipo: "LISTA", aplica: ["TERRENO", "LOCAL_COMERCIAL", "ALMACEN"],
    opciones: ["AVENIDA", "CALLE_O_JIRON", "PASAJE", "CARRETERA", "TROCHA_O_SIN_VIA"] },
  { clave: "estado_via", tipo: "LISTA", aplica: ["TERRENO", "ALMACEN"],
    opciones: ["ASFALTADA", "AFIRMADA", "SIN_AFIRMAR"] },
  { clave: "fondo", tipo: "DECIMAL", unidad: "m", aplica: ["TERRENO", "CASA"] },
  { clave: "posicion_en_manzana", tipo: "LISTA", aplica: ["TERRENO", "CASA"],
    opciones: ["UN_FRENTE", "DOS_FRENTES", "TRES_FRENTES", "CUATRO_FRENTES", "ESQUINA"] },
  { clave: "topografia", tipo: "LISTA", aplica: ["TERRENO"],
    opciones: ["PLANO", "PENDIENTE_LEVE", "PENDIENTE_PRONUNCIADA", "BAJO_NIVEL_DE_VIA",
               "ACCIDENTADO"] },

  /* qué hay encima */
  { clave: "edificacion_existente", tipo: "DECIMAL", unidad: "m²", aplica: ["TERRENO"],
    nota: "DECIMAL y no BOOLEANO: declarar 0 es una medida —está vacío— y dejarlo en blanco es «no consta»" },
  { clave: "cercado", tipo: "BOOLEANO", aplica: ["TERRENO"] },

  /* qué deja hacer la norma */
  { clave: "certificado_parametros_vigente", tipo: "BOOLEANO", aplica: ["TERRENO"] },
  { clave: "altura_normativa_pisos", tipo: "ENTERO", unidad: "pisos", aplica: ["TERRENO", "CASA"] },
  { clave: "coeficiente_edificacion", tipo: "DECIMAL", aplica: ["TERRENO"],
    nota: "× área del lote = área vendible; con eso una inmobiliaria decide en dos minutos" },
  { clave: "area_libre_minima", tipo: "DECIMAL", unidad: "%", aplica: ["TERRENO"],
    nota: "en POR CIENTO del lote: 30, no 0,30" },
  { clave: "retiro_municipal", tipo: "DECIMAL", unidad: "m", aplica: ["TERRENO"] },
  { clave: "lote_minimo_normativo", tipo: "DECIMAL", unidad: "m²", aplica: ["TERRENO"],
    nota: "es el HECHO del par con `acepta_venta_fraccionada`, que es condición del ENCARGO (V77/V78)" },
  { clave: "usos_compatibles", tipo: "TEXTO", aplica: ["TERRENO"] },
];

/* ------------------------------------------------------------------
   2 · Las entidades del contrato
   `campos` lista solo lo que decide el modelo; el resto (auditoría,
   organizacion_id, fechas) lo pone `EntidadDeOrganizacion` como en todo el
   backend. `invariantes` es lo que el gate comprueba.
   ------------------------------------------------------------------ */

const ENTIDADES = {

  /* ---------- Propiedad: la cosa física, sin intención comercial ---------- */
  Propiedad: {
    estado: "AMPLIA",
    tabla: "propiedad",
    clave: "id_propiedad",
    porQue:
      "Ya generaliza desde V4: tipo_inmueble ∈ {L,O,D,C,T,X} y uso ∈ {C,V,I,M}. " +
      "No se crea nada; se le quita lo que no le pertenece.",
    campos: {
      codigo: { estado: "EXISTE" },
      direccion: { estado: "EXISTE" },
      tipoPropiedad: { estado: "EXISTE", columna: "tipo_inmueble", nota: "se añade el código A (almacén)" },
      uso: { estado: "EXISTE" },
      estadoRegistro: { estado: "EXISTE", nota: "A/I — registro maestro, no disponibilidad" },
      ubicacion: { estado: "AMPLIA", columna: "ubicacion", nota: "geography(Point,4326); geo_lat/geo_long se conservan y se derivan" },
      idDistrito: { estado: "EXISTE" },
    },
    /* Lo que hoy está en `propiedad` y deja de pertenecerle. La clave es la
       COLUMNA real, para que el gate pueda comprobar que sigue ahí: si una
       desapareciera por otro camino, el contrato estaría desfasado. */
    seVan: {
      precio_referencial: "al encargo — un inmueble en venta y alquiler tiene DOS precios",
      moneda_referencial: "al encargo, por lo mismo",
      id_rol_propietario: "a Titularidad — una propiedad puede tener más de un titular",
      disponibilidad_comercial: "se DERIVA de los encargos vivos y de los expedientes en curso",
      detalle_local_comercial: "a AtributoPropiedad — una tabla por subtipo no escala a siete tipos",
    },
    invariantes: [
      // "toda propiedad tiene al menos un titular vigente" — DEROGADA EN V76.
      // Una Propiedad representa un inmueble CONOCIDO por BROX, no
      // necesariamente una oferta GESTIONADA por BROX: se puede conocer un
      // departamento anunciado a 180 000 USD sin saber quién es el dueño, y
      // obligar a declararlo obligaría a inventarlo. La exigencia se mudó al
      // ENCARGO, que es donde sigue siendo cierta.
      "un encargo vivo exige al menos un titular vigente de su propiedad",
      "la propiedad no declara operación",
      "la propiedad no declara precio",
      "la propiedad declara cómo llegó a conocerse",
    ],
  },

  /* ---------- Titularidad: quién es dueño, y en qué parte ---------- */
  Titularidad: {
    estado: "NUEVA",
    tabla: "titularidad_propiedad",
    clave: "id_titularidad",
    porQue:
      "Hoy propiedad.id_rol_propietario es 1:1 NOT NULL: una propiedad = un dueño. " +
      "Una copropiedad, una sucesión o una sociedad conyugal no caben.",
    campos: {
      idPropiedad: { estado: "NUEVA" },
      idRolPropietario: { estado: "NUEVA", nota: "persona_rol tipo PROPIETARIO — el Party-Role ya existe" },
      cuota: { estado: "NUEVA", nota: "porcentaje; 100 cuando hay un solo titular" },
      esRepresentante: { estado: "NUEVA", nota: "con quién se habla; exactamente uno por propiedad" },
      vigenteDesde: { estado: "NUEVA" },
      vigenteHasta: { estado: "NUEVA", nota: "NULL = vigente; una venta cierra unas y abre otras" },
    },
    invariantes: [
      "las cuotas vigentes de una propiedad suman 100",
      "exactamente un titular vigente es representante",
      "el histórico de titularidad no se borra: se cierra con vigenteHasta",
    ],
  },

  /* ---------- Encargo: la intención comercial. AQUÍ vive la operación ---------- */
  Encargo: {
    estado: "RENOMBRA",
    tabla: "captacion",
    clave: "id_captacion",
    porQue:
      "`captacion` YA es el encargo y YA lleva la operación: motivo_operacion ∈ {A,V}, " +
      "validado en el setter de la entidad desde antes de este documento. " +
      "El contrato no la inventa: la nombra y la hace obligatoria.",
    campos: {
      codigo: { estado: "EXISTE", columna: "codigo_captacion" },
      idPropiedad: { estado: "EXISTE" },
      operacion: { estado: "AMPLIA", columna: "motivo_operacion", nota: "A→ALQUILER, V→VENTA; deja de tener DEFAULT" },
      idCondicionEconomica: { estado: "EXISTE" },
      fechaInicioEncargo: { estado: "EXISTE" },
      fechaFinEncargo: { estado: "EXISTE" },
      exclusividad: { estado: "EXISTE" },
      estado: { estado: "EXISTE", nota: "P/O/R/A/C/V — la máquina no cambia" },
      idRolAgente: { estado: "EXISTE" },
      idRolBrokerRevisor: { estado: "EXISTE" },
    },
    invariantes: [
      "un encargo tiene exactamente una operación",
      "no puede haber dos encargos VIGENTES de la misma operación sobre la misma propiedad",
      "sí puede haber dos encargos vigentes de operaciones distintas sobre la misma propiedad",
      "la condición económica pertenece al encargo, no a la propiedad",
    ],
  },

  /* ---------- Condición económica: el número, con su unidad ---------- */
  CondicionEconomica: {
    estado: "EXISTE",
    tabla: "condicion_economica_captacion",
    clave: "id_condicion_economica",
    porQue:
      "Existe desde V15 y ya es correcta: tipo_operacion, importe, moneda, tipo y base " +
      "de comisión, IGV. «Ninguna capa puede inferir su significado por magnitud» — " +
      "eso es exactamente lo que el modelo universal necesita.",
    campos: {
      tipoOperacion: { estado: "EXISTE", nota: "debe coincidir con la operación de su encargo" },
      importeReferencia: { estado: "EXISTE" },
      monedaReferencia: { estado: "EXISTE" },
      tipoComision: { estado: "EXISTE" },
      baseCalculo: { estado: "EXISTE" },
      valorComision: { estado: "EXISTE" },
      tratamientoIgv: { estado: "EXISTE" },
    },
    invariantes: [
      "el importe nunca se interpreta por magnitud: lleva unidad y base",
      "tipoOperacion es igual a la operación del encargo que la posee",
    ],
  },

  /* ---------- Histórico económico: por encargo, no por propiedad ---------- */
  HistoricoEconomico: {
    estado: "AMPLIA",
    tabla: "precio_propiedad",
    clave: "id_precio",
    porQue:
      "E0 lo cerró y funciona (hitos U/P/O, backfill V45). Pero cuelga de la PROPIEDAD: " +
      "con venta y alquiler simultáneos, las dos series se mezclarían en una sola línea.",
    campos: {
      idPropiedad: { estado: "EXISTE" },
      idEncargo: { estado: "NUEVA", nota: "NULL solo para los hitos históricos anteriores a la migración" },
      operacion: { estado: "NUEVA", nota: "denormalizada para poder leer la serie sin join" },
      hito: { estado: "EXISTE", nota: "U autorizado · P publicado · O oferta" },
      monto: { estado: "EXISTE" },
      moneda: { estado: "EXISTE" },
      fecha: { estado: "EXISTE" },
    },
    invariantes: [
      "una serie de precios pertenece a una operación",
      "el histórico no se sobrescribe nunca",
    ],
  },

  /* ---------- Atributos: gobernados, no libres ---------- */
  AtributoPropiedad: {
    estado: "NUEVA",
    tabla: "atributo_propiedad",
    clave: "id_atributo_propiedad",
    porQue:
      "Hoy los atributos del subtipo viven en detalle_local_comercial: una tabla por tipo. " +
      "Con siete tipos serían siete tablas y siete formularios — justo lo que no queremos.",
    campos: {
      idPropiedad: { estado: "NUEVA" },
      clave: { estado: "NUEVA", nota: "del catálogo CatalogoAtributo; no admite claves libres" },
      valorTexto: { estado: "NUEVA" },
      valorNumero: { estado: "NUEVA" },
      valorBooleano: { estado: "NUEVA" },
    },
    invariantes: [
      "la clave existe en el catálogo",
      "el atributo aplica al tipo de la propiedad",
      "el valor está en la columna que corresponde a su tipo declarado",
    ],
  },

  CatalogoAtributo: {
    estado: "NUEVA",
    tabla: "catalogo_atributo",
    clave: "clave",
    porQue: "Es lo que separa un modelo dinámico de un saco de claves sueltas.",
    campos: {
      clave: { estado: "NUEVA" },
      tipoDato: { estado: "NUEVA", nota: "TEXTO · ENTERO · DECIMAL · BOOLEANO · LISTA" },
      unidad: { estado: "NUEVA" },
      aplicaA: { estado: "NUEVA", nota: "TODOS o la lista de tipos de propiedad" },
      requeridoPara: { estado: "NUEVA", nota: "tipos en los que el alta no se puede cerrar sin él" },
      delSistema: { estado: "NUEVA", nota: "los de este archivo; una organización no los borra" },
    },
    invariantes: ["dos organizaciones no comparten claves propias con el mismo nombre y distinto tipo"],
  },

  /* ---------- Publicación ---------- */
  Publicacion: {
    estado: "AMPLIA",
    tabla: "publicacion",
    clave: "id_publicacion",
    porQue: "Existe. Lo que falta es que sepa de qué encargo publica.",
    campos: {
      idPropiedad: { estado: "EXISTE" },
      idEncargo: { estado: "NUEVA", nota: "se publica un encargo, no una propiedad" },
    },
    invariantes: ["no se publica una propiedad sin encargo vigente"],
  },

  /* ---------- Demanda: el espejo ---------- */
  Requerimiento: {
    estado: "AMPLIA",
    tabla: "requerimiento_cliente",
    clave: "id_requerimiento",
    porQue:
      "Existe, pero habla alquiler comercial: renta_min/renta_max y rubro obligatorio. " +
      "Para que el matcher compare, el requerimiento tiene que hablar el MISMO idioma " +
      "que la propiedad — mismos atributos, misma unidad.",
    campos: {
      idRolCliente: { estado: "EXISTE" },
      operacionBuscada: { estado: "NUEVA", nota: "COMPRA o ALQUILER — el espejo de la operación del encargo" },
      tiposPropiedad: { estado: "AMPLIA", columna: "tipo_inmueble", nota: "pasa a ser lista: «casa o departamento»" },
      presupuestoMin: { estado: "RENOMBRA", columna: "renta_min", nota: "deja de llamarse renta" },
      presupuestoMax: { estado: "RENOMBRA", columna: "renta_max" },
      moneda: { estado: "EXISTE" },
      zonas: { estado: "EXISTE", relacion: "requerimiento_distrito", nota: "N:M, ya existe" },
      radioBusqueda: { estado: "NUEVA", nota: "para buscar por cercanía y no solo por distrito" },
      rubro: { estado: "AMPLIA", nota: "deja de ser obligatorio: un departamento no tiene rubro" },
      criterios: { estado: "NUEVA", nota: "lista de CriterioRequerimiento" },
    },
    invariantes: [
      "el requerimiento declara su operación",
      "sus criterios usan claves del mismo catálogo que los atributos de la propiedad",
    ],
  },

  CriterioRequerimiento: {
    estado: "NUEVA",
    tabla: "criterio_requerimiento",
    clave: "id_criterio",
    porQue:
      "«Indispensable» y «deseable» no son lo mismo, y el matcher necesita saberlo: " +
      "un indispensable que no se cumple DESCARTA; un deseable solo puntúa.",
    campos: {
      idRequerimiento: { estado: "NUEVA" },
      clave: { estado: "NUEVA", nota: "del mismo CatalogoAtributo" },
      operador: { estado: "NUEVA", nota: "IGUAL · MAYOR_IGUAL · MENOR_IGUAL · ENTRE · CONTIENE" },
      valor: { estado: "NUEVA" },
      peso: { estado: "NUEVA", nota: "INDISPENSABLE o DESEABLE" },
    },
    invariantes: [
      "un indispensable incumplido descarta la propiedad",
      "un deseable incumplido solo baja el puntaje",
    ],
  },

  /* ---------- Cierre: dos expedientes, una maquinaria ---------- */
  Expediente: {
    estado: "RENOMBRA",
    tabla: "solicitud_alquiler",
    clave: "id_expediente",
    porQue:
      "La compraventa NO cabe dentro de SolicitudAlquiler: tiene arras, minuta, escritura " +
      "y bloqueo registral, y no tiene garantía ni adelanto. Pero el 80 % de la maquinaria " +
      "—documentos, revisión, evaluación, comisión— es la misma. Un expediente con TIPO.",
    campos: {
      codigo: { estado: "EXISTE" },
      tipo: { estado: "NUEVA", nota: "ALQUILER o COMPRAVENTA, derivado de la operación del encargo" },
      idOportunidad: { estado: "EXISTE" },
      idRolAgente: { estado: "EXISTE" },
      importePropuesto: { estado: "RENOMBRA", columna: "monto_propuesto", nota: "renta mensual o precio total, según el tipo" },
      estado: { estado: "EXISTE" },
      condicionesAlquiler: {
        estado: "EXISTE",
        columnas: ["plazo_contrato_meses", "fecha_inicio_contrato", "forma_pago", "meses_garantia", "meses_adelanto"],
        nota: "solo si tipo=ALQUILER",
      },
      condicionesCompraventa: { estado: "NUEVA", nota: "arras, forma de pago, financiación — solo si tipo=COMPRAVENTA" },
    },
    invariantes: [
      "el tipo del expediente se deriva de la operación del encargo, no se elige a mano",
      "un expediente de alquiler no lleva condiciones de compraventa y viceversa",
      "los documentos exigidos salen de (tipo, tipoPropiedad), no de una lista fija",
    ],
  },

  DocumentoRequerido: {
    estado: "AMPLIA",
    tabla: "tipo_documento_requerido",
    clave: "id_tipo_documento",
    porQue: "Ya existe Y ya discrimina por tipo_operacion. Solo le falta el tipo de propiedad.",
    campos: {
      tipoOperacion: { estado: "EXISTE" },
      tipoPropiedad: { estado: "NUEVA", nota: "NULL = aplica a todos" },
      obligatorio: { estado: "EXISTE" },
    },
    invariantes: ["los documentos de un expediente se derivan, nunca se escriben en la pantalla"],
  },

  /* ---------- Trazabilidad para proyecciones futuras ---------- */
  EventoDominio: {
    estado: "NUEVA",
    tabla: "evento_dominio",
    clave: "id_evento",
    porQue:
      "Neo4j no entra como dependencia, pero los datos tienen que poder proyectarse. " +
      "Un outbox transaccional cuesta poco AHORA y es imposible reconstruir DESPUÉS.",
    campos: {
      tipo: { estado: "NUEVA", nota: "PROPIEDAD_REGISTRADA, ENCARGO_ABIERTO, OFERTA_PRESENTADA…" },
      entidadTipo: { estado: "NUEVA" },
      entidadId: { estado: "NUEVA" },
      ocurridoEn: { estado: "NUEVA" },
      cargaUtil: { estado: "NUEVA", nota: "jsonb con los ids de las entidades relacionadas" },
      proyectadoEn: { estado: "NUEVA", nota: "NULL hasta que un consumidor lo procesa" },
    },
    invariantes: [
      "el evento se escribe en la MISMA transacción que el hecho",
      "PostgreSQL sigue siendo la verdad; el grafo es una proyección reconstruible",
    ],
  },
};

/* ------------------------------------------------------------------
   3 · El gate: los ocho casos que el modelo tiene que representar
   SIN una sola excepción especial.
   ------------------------------------------------------------------ */

const CASOS = [
  {
    id: "local-alquiler",
    nombre: "Local comercial en alquiler",
    porQue: "Es el caso que el sistema ya hace hoy: no puede romperse.",
    propiedad: { tipo: "LOCAL_COMERCIAL", atributos: { metraje_total: 85, frente: 6, carga_electrica_kw: 12, rubro_permitido: "gastronómico" } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [{ operacion: "ALQUILER", importe: 2900, moneda: "USD", vigente: true }],
  },
  {
    id: "departamento-venta",
    nombre: "Departamento en venta",
    porQue: "El caso que hoy NO cabe: no hay venta y el precio es «renta».",
    propiedad: { tipo: "DEPARTAMENTO", atributos: { metraje_total: 92, dormitorios: 3, banos: 2, piso: "7" } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [{ operacion: "VENTA", importe: 180000, moneda: "USD", vigente: true }],
  },
  {
    id: "casa-alquiler",
    nombre: "Casa en alquiler",
    porQue: "Vivienda en alquiler: mezcla atributos de vivienda con operación de alquiler.",
    propiedad: { tipo: "CASA", atributos: { metraje_total: 210, area_terreno: 300, dormitorios: 4, pisos_edificacion: 2 } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [{ operacion: "ALQUILER", importe: 4200, moneda: "PEN", vigente: true }],
  },
  {
    id: "terreno-venta",
    nombre: "Terreno en venta",
    porQue: "El extremo: sin construcción, sin ambientes, sin antigüedad útil.",
    /* Este caso llevaba `metraje_total: 1200` **y** `area_terreno: 1200` — el
       mismo número escrito dos veces, que es exactamente la duplicidad que D-7
       retira en `V85`. Al quitarle `TERRENO` a `area_terreno`, este gate se puso
       rojo por su cuenta y nombró el atributo: hizo su trabajo. Se quita el
       duplicado, no la superficie: `metraje_total` es la canónica.

       Y LLEVA LAS TRES `PUB` DEL TERRENO, no sólo la de 5B. La primera versión
       de esta corrección añadió `condicion_terreno` y se quedó ahí, dejando el
       caso **declarándose publicable mientras su propio modelo lo bloqueaba**:
       este mismo fichero declara `agua_desague` y `energia_electrica` como `PUB`
       en TERRENO desde 5A, así que sin ellas el terreno no se puede anunciar.

       No lo cazó nadie, y es el punto: `gate-modelo-universal.js` **no conoce el
       eje `PUB`/`OPC`/`ALT`** — barrido el 2026-08-29, `PUB` y `exigencia` dan
       CERO apariciones en ese fichero, con control positivo (`requeridoPara`
       da 1, `clave` da 7). Sólo sabe comprobar `requerido`, que es el eje del
       ALTA. Es la deuda **N20** de `pendientes-brox.md`, y **no se cierra aquí**:
       escribir ese gate exige antes decidir contra qué compara —el catálogo
       completo o este subconjunto—, que es la pregunta abierta de N18. */
    propiedad: { tipo: "TERRENO", atributos: { metraje_total: 1200, zonificacion: "RDM", frente: 24, condicion_terreno: "URBANO_HABILITADO", agua_desague: "CONECTADO", energia_electrica: "CONECTADO" } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [{ operacion: "VENTA", importe: 540000, moneda: "USD", vigente: true }],
  },
  {
    id: "venta-y-alquiler",
    nombre: "Una propiedad en venta Y en alquiler a la vez",
    porQue:
      "El caso que decide el modelo. Si la operación viviera en la propiedad haría falta " +
      "un booleano, una tercera operación AMBAS o una fila duplicada. Con la operación en " +
      "el encargo son dos encargos, y cada uno lleva su precio y su comisión.",
    propiedad: { tipo: "OFICINA", atributos: { metraje_total: 140, ambientes: 6, carga_electrica_kw: 20 } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [
      { operacion: "VENTA", importe: 320000, moneda: "USD", vigente: true },
      { operacion: "ALQUILER", importe: 3400, moneda: "USD", vigente: true },
    ],
  },
  {
    id: "copropiedad",
    nombre: "Más de un propietario",
    porQue: "Hoy imposible: propiedad.id_rol_propietario es 1:1 NOT NULL.",
    propiedad: { tipo: "CASA", atributos: { metraje_total: 180, dormitorios: 3 } },
    titulares: [
      { cuota: 50, representante: true },
      { cuota: 30, representante: false },
      { cuota: 20, representante: false },
    ],
    encargos: [{ operacion: "VENTA", importe: 260000, moneda: "USD", vigente: true }],
  },
  {
    id: "cambio-de-intencion",
    nombre: "Deja de alquilarse y pasa a venderse",
    porQue:
      "Comprueba que la historia se conserva: el encargo de alquiler se cierra, el de venta " +
      "se abre, y las dos series de precio siguen siendo legibles por separado.",
    propiedad: { tipo: "DEPARTAMENTO", atributos: { metraje_total: 78, dormitorios: 2 } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [
      { operacion: "ALQUILER", importe: 2200, moneda: "USD", vigente: false },
      { operacion: "VENTA", importe: 145000, moneda: "USD", vigente: true },
    ],
  },
  {
    id: "almacen-alquiler",
    nombre: "Almacén en alquiler",
    porQue: "Tipo nuevo: comprueba que añadir uno no toca el modelo, solo el catálogo.",
    propiedad: { tipo: "ALMACEN", atributos: { metraje_total: 800, altura_libre: 8, carga_electrica_kw: 60 } },
    titulares: [{ cuota: 100, representante: true }],
    encargos: [{ operacion: "ALQUILER", importe: 7500, moneda: "USD", vigente: true }],
  },
];

/* Requerimientos del lado demanda: el espejo, y con los mismos atributos. */
const CASOS_DEMANDA = [
  {
    id: "busca-departamento-compra",
    nombre: "Busca departamento para comprar",
    operacionBuscada: "COMPRA",
    tiposPropiedad: ["DEPARTAMENTO"],
    presupuesto: [150000, 200000],
    moneda: "USD",
    criterios: [
      { clave: "dormitorios", operador: "MAYOR_IGUAL", valor: 3, peso: "INDISPENSABLE" },
      { clave: "estacionamientos", operador: "MAYOR_IGUAL", valor: 1, peso: "DESEABLE" },
    ],
    deberiaCasarCon: ["departamento-venta"],
  },
  {
    id: "busca-local-alquiler",
    nombre: "Busca local para alquilar (gastronómico)",
    operacionBuscada: "ALQUILER",
    tiposPropiedad: ["LOCAL_COMERCIAL"],
    presupuesto: [2000, 3500],
    moneda: "USD",
    criterios: [
      { clave: "carga_electrica_kw", operador: "MAYOR_IGUAL", valor: 10, peso: "INDISPENSABLE" },
      { clave: "frente", operador: "MAYOR_IGUAL", valor: 5, peso: "DESEABLE" },
    ],
    deberiaCasarCon: ["local-alquiler"],
  },
  {
    id: "busca-vivienda-cualquiera",
    nombre: "Busca casa o departamento para alquilar",
    porQue: "Comprueba que el tipo buscado es una LISTA, no un valor.",
    operacionBuscada: "ALQUILER",
    tiposPropiedad: ["CASA", "DEPARTAMENTO"],
    presupuesto: [3000, 5000],
    moneda: "PEN",
    criterios: [{ clave: "dormitorios", operador: "MAYOR_IGUAL", valor: 3, peso: "INDISPENSABLE" }],
    deberiaCasarCon: ["casa-alquiler"],
  },
];

module.exports = {
  OPERACION, TIPO_PROPIEDAD, OPERACION_HABITUAL, EXPEDIENTE_DE,
  ATRIBUTOS, ENTIDADES, CASOS, CASOS_DEMANDA,
};
