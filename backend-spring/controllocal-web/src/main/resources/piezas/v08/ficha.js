// BROX · Ficha comercial V08 · renderer y protocolo de la entrada embebible.
// Recibe { tipo:'brox-ficha', version:'V08', datos, opciones } por postMessage, valida el contrato
// «brox-ficha-datos» 1.x, dibuja la lectura digital y las hojas A4 estáticas y responde
// { tipo:'brox-ficha-lista', alto, faltantes, estadoPieza, … }. Ver CONTRATO-DATOS-FICHA-V08.md §8.
// La pieza no publica, no comparte y no registra comunicación: imprimir o exportar no es difundir.
(function () {
  'use strict';
  var CAT = window.BROX_CATALOGO_CORE, PERF = window.BROX_PERFILES_V08, VAL = window.BROX_VALIDAR_FICHA, FMT = window.BROX_FORMATO, TEMAS = window.BROX_TEMAS_V08;
  var VERSION = 'V08';
  var $ = function (id) { return document.getElementById(id); };
  var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); };
  var pad2 = function (n) { return String(n).padStart(2, '0'); };

  // Iconos de trazo recto, como el sprite de la landing (guía visual: trazo 1,6, puntas cuadradas, en tinta).
  var TRAZOS = {
    piso: 'M3 20h5v-4h4v-4h4V8h5', cama: 'M2 18V8m20 10V8M2 14h20M5 14V8h6v6m0-3h8v3M2 18v3m20-3v3', bano: 'M3 11h18v4l-2 5H5l-2-5v-4M5 11V5h4v2M6 20v2M18 20v2',
    auto: 'M4 15V9l2-5h12l2 5v6M4 10h16M2 15h20v4H2v-4m3 4v3m14-3v3M5 13h2m10 0h2', ascensor: 'M3 3h18v18H3V3M8 16V8m-3 3 3-3 3 3m5-3v8m-3-3 3 3 3-3',
    reloj: 'M3 3h18v18H3zM12 7v5l3 3', jardin: 'M12 21V10M12 14 7 10V5l5 4 5-4v5l-5 4M5 21h14', terraza: 'M3 10h18M5 10v11M19 10v11M3 21h18M9 14v7M15 14v7M8 10V4h8v6',
    vista: 'M2 12 7 6h10l5 6-5 6H7zM10 10h4v4h-4z', casa: 'M2 11 12 3l10 8M5 9v12h14V9M9 21v-7h6v7', frente: 'M3 21 8 3m8 0 5 18M12 3v4m0 3v4m0 3v4',
    altura: 'M12 3v18M7 8l5-5 5 5M7 16l5 5 5-5', puerta: 'M4 22V3h16v19M7 22V6h10v16m-3-9h1', oficina: 'M3 22V3h18v19M7 7h2m6 0h2M7 11h2m6 0h2M7 15h2m6 0h2M10 22v-4h4v4',
    red: 'M2 9l10-6 10 6M5 12l7-4 7 4M8 15l4-2 4 2M12 18v2', area: 'M3 8V3h5m8 0h5v5m0 8v5h-5m-8 0H3v-5M7 7h10v10H7V7', terreno: 'M2 20 8 10l5 6 4-12 5 16H2',
    agua: 'M12 3l6 10-2 6H8l-2-6z', energia: 'M13 2 5 14h6l-1 8 8-12h-6z', documento: 'M5 2h10l4 4v16H5V2m10 0v5h4M8 11h8m-8 4h8m-8 4h5', carga: 'M2 6h20v14H2V6m6 0v14m8-14v14M2 11h20',
    esquina: 'M3 21V3h18M8 21V8h13M3 13h5M13 3v5', piscina: 'M3 3v12m7-12v12M3 7h7M3 11h7M2 19l4-2 4 2 4-2 4 2 4-2', via: 'M5 21 9 3m6 0 4 18M12 4v3m0 4v3m0 4v3',
    pin: 'M12 21.5 5.5 12V3.5h13V12zM9.5 7.5h5v4h-5z', ampliar: 'M7 17 17 7M9 7h8v8', flecha: 'M4 12h15M13 6l6 6-6 6', atras: 'M20 12H5M11 6l-6 6 6 6',
    correo: 'M3 5.5h18v13H3zM3.5 6l8.5 7 8.5-7', web: 'M3 4h18v16H3zM3 9h18M6 6.5h2', telefono: 'M7 2.5h10v19H7zM10.5 18.5h3', alerta: 'M12 3 2 21h20zM12 9.5v5.5m0 2.5v1',
    check: 'M4 12.5 9.5 18 20 6', menos: 'M5 12h14', punto: 'M9 9h6v6H9z', fotos: 'M3 5h18v14H3zM3 15l5-5 4 4 3-3 6 6M15 8.5h2',
    camara: 'M3 7h4l2-3h6l2 3h4v13H3zM12 10.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 1 1 0-7', precio: 'M3 3h8l10 10-8 8L3 11zM7.5 7.5h1.5v1.5H7.5z',
    consulta: 'M3 4h18v12h-9l-5 4v-4H3zM7 8.5h10M7 12h6', persona: 'M12 3.5a4 4 0 1 1 0 8 4 4 0 1 1 0-8M4.5 21v-1.5a6 6 0 0 1 6-6h3a6 6 0 0 1 6 6V21'
  };
  var ico = function (n) { return '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="' + (TRAZOS[n] || TRAZOS.area) + '"/></svg>'; };
  var SINGULAR = { Dormitorios: 'Dormitorio', Ascensores: 'Ascensor', Ambientes: 'Ambiente', 'Baños': 'Baño', 'Baños completos': 'Baño completo', 'Muelles de carga': 'Muelle de carga', 'Unidades descritas': 'Unidad descrita', Habitaciones: 'Habitación', Estacionamientos: 'Estacionamiento', Unidades: 'Unidad', Cocheras: 'Cochera', Pisos: 'Piso', 'Pisos de la casa': 'Piso' };
  var ROTULOS_CLAVE = { metraje_total: 'Área total declarada', metraje_construido: 'Área construida', area_terreno: 'Área de terreno', dormitorios: 'Dormitorios', banos: 'Baños', ascensores: 'Ascensores', cuota_mantenimiento: 'Cuota de mantenimiento', en_condominio: 'Régimen de condominio', rubro_permitido: 'Rubros permitidos', apto_licencia_funcionamiento: 'Licencia de funcionamiento', topografia: 'Topografía', agua_desague: 'Agua y desagüe', energia_electrica: 'Energía eléctrica', edificacion_existente: 'Construcción existente', capacidad_portante_piso: 'Carga admisible del piso', suministro_electrico: 'Suministro eléctrico', estado_ocupacion: 'Ocupación', igv_arrendamiento: 'IGV del alquiler', zonificacion: 'Zonificación', tipo_acceso: 'Tipo de acceso', condicion_terreno: 'Habilitación del terreno' };

  var actual = null, anfitrion = null, ultimoAlto = 0, listaEnviada = false;

  // ------------------------------------------------------------------ utilidades del contrato
  function vig(d) { return !!d && typeof d === 'object' && d.estado === 'VIGENTE'; }
  function procedencia(datos, d) { return d ? VAL.procedenciaDe(datos, d.procedencia) : null; }
  function inferido(datos, d) { var p = procedencia(datos, d); return !!p && p.naturaleza === 'INFERIDO'; }
  function rotuloOpcion(ref, valor) { var m = PERF.rotulosOpciones[ref] || {}; return m[valor] || String(valor).toLowerCase().replace(/_/g, ' ').replace(/^./, function (c) { return c.toUpperCase(); }); }
  function rotuloRef(ref, perfil) {
    var specs = [].concat(perfil.areas, perfil.caracteristicas, perfil.rasgos, perfil.condiciones, perfil.documentos);
    for (var i = 0; i < specs.length; i++) if (specs[i].ref === ref) return specs[i].rotulo;
    if (/^H-\d\d$/.test(ref)) return PERF.huecos[ref].nombre;
    return ROTULOS_CLAVE[ref] || ref.replace(/_/g, ' ').replace(/^./, function (c) { return c.toUpperCase(); });
  }
  function singularSi(n, rotulo) { return n === 1 && SINGULAR[rotulo] ? SINGULAR[rotulo] : rotulo; }
  function capitalizar(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : s; }
  // Contactos (1.4.0): cada uno es un enlace que la persona pulsa, en pantalla y en el PDF. WhatsApp abre el chat
  // directo con el número internacional (CAT-E43; wa.me sólo admite cifras, sin «+»); teléfono, correo y web, su
  // esquema. Lo que no se puede enlazar se escribe sin enlace (validar.js lo avisa). La pieza no envía nada.
  function textoContacto(c) { return c.tipo === 'WHATSAPP' ? 'WhatsApp ' + c.valor : c.tipo === 'WEB' ? c.valor.replace(/^https:\/\//, '').replace(/\/$/, '') : c.valor; }
  function enlaceContacto(c) {
    var v = String(c.valor).trim();
    if (c.tipo === 'WHATSAPP') return VAL.INTERNACIONAL.test(v) ? 'https://wa.me/' + v.replace(/\D/g, '') : null;
    if (c.tipo === 'TELEFONO') return /\d{6,}/.test(v.replace(/\D/g, '')) ? 'tel:' + v.replace(/[^\d+]/g, '') : null;
    if (c.tipo === 'CORREO') return VAL.CORREO_SOLO.test(v) ? 'mailto:' + v : null;
    if (c.tipo === 'WEB') return VAL.WEB_SOLO.test(v) ? 'https://' + v.replace(/^https:\/\//, '') : null;
    return null;
  }
  // La acción de la pieza (1.4.0; 1.5.0): «Coordinar una visita», una sola, hacia un solo destino que comparten el botón,
  // el QR de la impresión y el texto que acompaña a la imagen vertical. Primero el contacto que la persona marcó como
  // principal; si no marcó ninguno, WhatsApp (con el mensaje escrito), teléfono, correo y, si sólo hay web, la web. Un
  // teléfono no prueba que tenga WhatsApp: el chat sólo sale de un contacto WHATSAPP. Sin contactos, no hay acción.
  var ORDEN_ACCION = ['WHATSAPP', 'TELEFONO', 'CORREO', 'WEB'];
  var CANAL = { WHATSAPP: 'WhatsApp', TELEFONO: 'Teléfono', CORREO: 'Correo', WEB: 'Web' };
  var VERBO = { WHATSAPP: 'escribir por WhatsApp a', TELEFONO: 'llamar a', CORREO: 'escribir un correo a', WEB: 'abrir la web de' };
  function accionDe(identidad, titulo, direccion) {
    if (!identidad) return null;
    var enlazables = identidad.contactos.filter(function (c) { return c.href; });
    if (!enlazables.length) return null;
    var c = enlazables.filter(function (x) { return x.principal; })[0] || enlazables.slice().sort(function (a, b) { return ORDEN_ACCION.indexOf(a.tipo) - ORDEN_ACCION.indexOf(b.tipo); })[0];
    var asunto = titulo + (direccion ? ', ' + direccion : '');
    var href = c.tipo === 'WHATSAPP' ? c.href + '?text=' + encodeURIComponent('Hola, quisiera coordinar una visita: ' + asunto + '.')
      : c.tipo === 'CORREO' ? c.href + '?subject=' + encodeURIComponent('Visita: ' + asunto) : c.href;
    return { tipo: c.tipo === 'WHATSAPP' ? 'whatsapp' : 'tinta', texto: 'Coordinar una visita', canal: CANAL[c.tipo], verbo: VERBO[c.tipo], contacto: c, href: href };
  }
  // Respaldo de un hecho documental (1.4.0; 1.5.0). Sale sólo de su procedencia, sin deducir nada, y dice qué se hizo
  // (revisión entregada por el titular el 20 de octubre: «Observado con evidencia» era ambiguo): un documento revisado (lo
  // observó una persona y hay evidencia), una revisión (observado sin evidencia), una declaración con su documento o una
  // declaración. Sin naturaleza no se escribe nada: «Registrado» se confundía con la inscripción en registros públicos.
  function respaldoDe(p, fmt) {
    if (!p || !p.naturaleza || p.naturaleza === 'INFERIDO') return null;
    var tipo = p.naturaleza === 'OBSERVADO' ? (p.evidenciaRef ? 'Documento revisado' : 'Revisado') : (p.evidenciaRef ? 'Declarado con documento' : 'Declarado');
    var fecha = p.observadoEn || p.registradoEn;
    return { tipo: tipo, fecha: fecha || null, texto: tipo + (fecha ? ' el ' + fmt.fechaCorta(fecha) : '') };
  }
  // Ubicación autorizada (1.5.0; revisión del 20 de octubre: la ficha debe permitir reconocer dónde está el inmueble). La
  // dirección y el punto llegan autorizados para esta salida con la misma precisión; sin ellos, la zona es aproximada.
  // «Ver ubicación» abre el punto en Google Maps (Maps URLs): en el teléfono, en la aplicación instalada.
  function enlaceMapa(p) { return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(p.latitud + ',' + p.longitud); }
  // Posición del punto sobre una variante del mapa, en porcentaje de su ancho y de su alto (la proyección es lineal en grados).
  function sobreMapa(punto, variante) {
    var q = variante && variante.encuadre;
    if (!punto || !q) return null;
    var x = (punto.longitud - q.oeste) / (q.este - q.oeste) * 100, y = (q.norte - punto.latitud) / (q.norte - q.sur) * 100;
    if (x < 0 || x > 100 || y < 0 || y > 100) return null;
    var r = null;
    if (punto.precision === 'APROXIMADA') r = punto.radioMetros / ((q.este - q.oeste) * 111320 * Math.cos(punto.latitud * Math.PI / 180)) * 100;
    return { x: Math.round(x * 100) / 100, y: Math.round(y * 100) / 100, r: r === null ? null : Math.round(r * 100) / 100 };
  }

  // Nombre del tipo en el título compuesto (P5): «Departamento de 3 dormitorios en …».
  var TIPO_EN_TITULO = { departamento: 'Departamento', casa: 'Casa', casa_campo: 'Casa de campo', casa_playa: 'Casa de playa', local: 'Local comercial', oficina: 'Oficina', terreno: 'Terreno', almacen: 'Almacén', estacionamiento: 'Estacionamiento', edificio: 'Edificio', otro: 'Inmueble' };
  // Subgrupos del capítulo 03 (P7), en este orden; las áreas van primero y «Uso y construcción» al final.
  var GRUPOS_CARAC = ['distribucion', 'edificio', 'cochera', 'dimensiones', 'servicios', 'terreno', 'exteriores'];
  function tituloGrupoCarac(id, perfil) {
    switch (id) {
      case 'distribucion': return 'Distribución';
      case 'edificio': return perfil.tipo === 'C' || perfil.tipo === 'A' ? 'Construcción' : 'Edificio';
      case 'cochera': return ['oficina', 'edificio', 'otro', 'estacionamiento'].indexOf(perfil.id) >= 0 ? 'Estacionamiento' : 'Cochera';
      case 'dimensiones': return 'Dimensiones';
      case 'servicios': return 'Servicios';
      case 'terreno': return 'Terreno';
      default: return 'Exteriores y equipamiento';
    }
  }

  // Motivos de una vista previa (validar.js decide; aquí sólo se redactan con fechas legibles). Cada
  // frase dice la causa y la acción en 25 palabras como máximo: se usan en la franja y en el panel.
  var ESTADO_ENCARGO_TEXTO = { FINALIZADO: 'finalizado', RESCINDIDO: 'rescindido' };
  var MOTIVOS_DEL_ENCARGO = ['SIN_ENCARGO', 'ENCARGO_NO_APROBADO', 'VIGENCIA_FALTANTE', 'ENCARGO_VENCIDO'];
  function textoMotivo(mo, fmt) {
    switch (mo.codigo) {
      case 'DEMOSTRACION': return 'Una demostración no se difunde.';
      case 'SALIDA_PRIVADA': return 'Para difundirla, usa «Obtener para difusión»: vuelve a comprobar permiso, vigencia y requisitos de publicación.';
      case 'SIN_ENCARGO': return 'Para difundirla, vincúlala a un encargo aprobado y vigente.';
      case 'ENCARGO_NO_APROBADO': return ESTADO_ENCARGO_TEXTO[mo.estadoEncargo]
        ? 'El encargo está ' + ESTADO_ENCARGO_TEXTO[mo.estadoEncargo] + '; esta ficha ya no se difunde.'
        : 'El encargo está en preparación; para difundirla debe estar aprobado.';
      case 'VIGENCIA_FALTANTE': return 'Falta la fecha de término del encargo; complétala para difundirla.';
      case 'ENCARGO_VENCIDO': return 'El encargo venció el ' + fmt.fechaLarga(mo.fecha) + '; sin un encargo vigente no se difunde.';
      case 'DESACTUALIZADA': return mo.causa === 'NUEVA_VERSION'
        ? 'Desde el ' + fmt.fechaLarga(mo.fecha) + ' rige la versión ' + mo.versionVigente + '.'
        : 'Los datos de origen cambiaron el ' + fmt.fechaLarga(mo.fecha) + '; esta versión no los refleja' + (mo.versionVigente ? ' y rige la versión ' + mo.versionVigente : '') + '.';
      case 'ESENCIALES': return 'Faltan datos esenciales.';
      // 1.3.1: lo que falta ya no se escribe en la pieza; la franja de la pantalla dice qué completar.
      case 'INCERTIDUMBRE_MATERIAL': return 'Para difundirla, confirma ' + mo.faltan.map(function (x) { return x.que; }).reduce(function (t, x, i, l) { return t + (i ? (i === l.length - 1 ? ' y ' : ', ') : '') + x; }, '') + '.';
    }
    return mo.detalle || '';
  }
  // Franja de borrador: el esencial que falta y qué hacer, en una frase (P1).
  var FALTA_ESENCIAL = {
    'encargo.operacion': 'Falta indicar si es venta o alquiler. Complétalo antes de difundir.',
    'inmueble.tipo': 'Falta el tipo de inmueble. Complétalo antes de difundir.',
    'inmueble.ubicacion.distrito': 'Falta el distrito de la zona. Complétalo antes de difundir.',
    'encargo.precio.importe': 'Falta el precio. Complétalo antes de difundir.',
    'encargo.condiciones.modalidad_precio': 'Falta la base del alquiler: total o por m² al mes. Complétala antes de difundir.',
    'marcaYContacto': 'Falta el responsable del encargo: un agente independiente o un agente de una empresa, con su forma de contacto. Complétalo antes de difundir.',
    'marcaYContacto.contactos': 'Falta la forma de contactar al responsable: WhatsApp o teléfono con su número completo, correo o web. Complétala antes de difundir.'
  };
  function textoBorrador(faltan, datos) {
    if (faltan.length > 1) {
      // 1.6.0: la moneda conserva lo que escribe la fuente también cuando faltan varios esenciales.
      var enF = datos.encargo.precio.moneda && datos.encargo.precio.moneda.enFuente;
      var lista = faltan.map(function (f) { return f.rotulo.toLowerCase() + (f.ref === 'encargo.precio.moneda' && enF ? ' (la fuente escribe «' + enF + '»)' : ''); });
      return 'Faltan ' + faltan.length + ' datos esenciales: ' + lista.slice(0, -1).join(', ') + ' y ' + lista[lista.length - 1] + '. Complétalos antes de difundir.';
    }
    var f = faltan[0];
    if (f.ref === 'encargo.precio.moneda') {
      var enFuente = datos.encargo.precio.moneda && datos.encargo.precio.moneda.enFuente;
      return enFuente ? 'Falta la moneda del precio: la fuente escribe «' + enFuente + '». Confirma si es USD o PEN antes de difundir.' : 'Falta la moneda del precio (USD o PEN). Complétala antes de difundir.';
    }
    return FALTA_ESENCIAL[f.ref] || 'Falta un dato que se exige para publicar: ' + f.rotulo.toLowerCase() + '. Complétalo antes de difundir.';
  }

  // ------------------------------------------------------------------ modelo de la pieza
  function construirModelo(datos, op) {
    var fmt = FMT.crear(op.regional);
    var pz = datos.pieza, inm = datos.inmueble, enc = datos.encargo, pr = enc.precio, pres = datos.presentacion;
    var perfil = PERF.perfiles[pres.perfil];
    var demo = pz.naturaleza === 'DEMOSTRACION';
    var huecos = {}; (inm.fueraDeCatalogo || []).forEach(function (h) { huecos[h.hueco] = h; });
    var atr = inm.atributos || {}, cond = enc.condiciones || {};
    var dato = function (ref) { return /^H-\d\d$/.test(ref) ? huecos[ref] : (CAT.encargo[ref] ? cond[ref] : atr[ref]); };
    var presentable = function (d) { return vig(d) && !inferido(datos, d); };
    var valor = function (ref) { var d = dato(ref); return presentable(d) ? d.valor : undefined; };
    var faltantes = [], vistos = {};
    var falta = function (ref, rotulo, motivo, detalle, capitulo, esencial) { if (vistos[ref]) return; vistos[ref] = true; faltantes.push({ ref: ref, rotulo: rotulo, esencial: !!esencial, motivo: motivo, detalle: detalle, capitulo: capitulo }); };
    var capituloDe = function (ref) {
      if (/^(encargo\.precio|encargo\.operacion)/.test(ref)) return '02';
      if (/ubicacion|entorno/.test(ref)) return '01';
      if (/documentacion/.test(ref)) return '04';
      if (/fotos/.test(ref)) return '05';
      if (/^inmueble\.tipo/.test(ref)) return 'portada';
      if (/marcaYContacto/.test(ref)) return 'identidad';
      return '03';
    };

    // Esenciales (una sola implementación: validar.js)
    var es = VAL.esenciales(datos);
    es.faltan.forEach(function (f) { falta(f.ref, f.rotulo, 'ESENCIAL', f.detalle, capituloDe(f.ref), true); });

    // Identidad (parte 2). 1.4.0: la firma comercial tiene jerarquía propia. 1.5.0 (revisiones entregadas por el titular
    // el 20 de octubre: la ficha se presenta como una pieza comercial del agente o de la empresa, en una firma horizontal):
    // a la izquierda, la empresa (su logotipo autorizado, H-31, o su nombre comercial); a la derecha, quien atiende, con su
    // nombre y, junto a él, su fotografía si existe (H-32) y su registro si viaja. Sin rótulos fijos como «Agente a cargo»:
    // el nombre junto a la foto ya dice quién atiende. Un independiente sin logotipo ocupa el lugar principal, a la
    // izquierda; una organización sin agente firma sólo con su marca. La identidad completa va una vez, al comienzo; el pie
    // de cada hoja sólo nombra a quien atiende y cómo escribirle. El nombre de presentación, si la persona lo eligió, manda.
    var mc = datos.marcaYContacto, identidad = null;
    if (vig(mc)) {
      var esAgente = mc.condicion === 'AGENTE_DE_ORGANIZACION', esOrg = mc.condicion === 'ORGANIZACION';
      var nombre = typeof mc.nombrePresentacion === 'string' && mc.nombrePresentacion.trim() ? mc.nombrePresentacion.trim() : mc.nombre;
      var logoAut = mc.logo && mc.logo.autorizadoParaSalida === true ? mc.logo : null;
      identidad = {
        condicion: mc.condicion,
        logo: logoAut,
        // La empresa (grupo de la izquierda): logotipo o nombre comercial. Un independiente sólo la tiene si firma con logotipo.
        empresa: esAgente || esOrg ? { logo: logoAut, nombre: esAgente ? mc.organizacion : nombre } : logoAut ? { logo: logoAut, nombre: nombre } : null,
        // Quien atiende (grupo de la derecha; a la izquierda si no hay empresa): el agente o el independiente.
        persona: esOrg ? null : nombre,
        retrato: !esOrg && mc.retrato && mc.retrato.autorizadoParaSalida === true ? mc.retrato : null,
        registro: mc.registro ? 'Registro ' + mc.registro.referencia : null,
        // En el pie: quien atiende (la empresa ya está en la firma inicial).
        contactoNombre: nombre, contactoEmpresa: esAgente ? mc.organizacion : null,
        contactos: (mc.contactos || []).filter(function (c) { return c.autorizadoParaSalida === true; }).map(function (c) { return { tipo: c.tipo, valor: c.valor, texto: textoContacto(c), href: enlaceContacto(c), principal: c.principal === true }; })
      };
    } else falta('marcaYContacto', 'Responsable del encargo', 'FALTANTE', (mc && mc.motivo) || 'Sin responsable autorizado para esta salida.', 'identidad');
    // «Pregunta al profesional…» sólo tiene sentido si la pieza trae un contacto autorizado (P2).
    var conContacto = !!(identidad && identidad.contactos.length);

    // Operación, tipo, zona, título
    var operacion = presentable(enc.operacion) ? enc.operacion.valor : null;
    var zona = [inm.ubicacion.zonaUrbanizacion, inm.ubicacion.distrito].filter(presentable).map(function (d) { return d.valor; });
    // Título que dice qué es (P5; decisión 4): tipo y zona, en 65 caracteres como máximo. Un título editorial del
    // profesional manda; sin él, se compone sólo con datos conocidos. 1.5.0 (revisión del 20 de octubre): sin los
    // dormitorios, que ya se leen en las cifras clave; el título corto deja espacio a la firma y a la dirección.
    var zonaU = presentable(inm.ubicacion.zonaUrbanizacion) ? inm.ubicacion.zonaUrbanizacion.valor : null;
    var distrito = presentable(inm.ubicacion.distrito) ? inm.ubicacion.distrito.valor : null;
    var tipoTitulo = TIPO_EN_TITULO[perfil.id] || perfil.rotulo;
    var titulo = pres.titulo, lineaZona = zona.join(', '), compuesto = !pres.titulo;
    if (compuesto) {
      var lugar = zonaU || distrito;
      titulo = tipoTitulo + (lugar ? ' en ' + lugar : '');
      if (titulo.length > 65 && distrito) { lugar = distrito; titulo = tipoTitulo + ' en ' + distrito; }
      // Debajo, sólo la parte de la zona que el título no dice: el distrito cuando el título nombra la zona y la
      // zona cuando, por largo, el título se quedó con el distrito.
      lineaZona = zona.filter(function (z) { return z !== lugar; }).join(', ');
    }
    // Ubicación autorizada (1.5.0; H-33 y H-20). La dirección y el punto comparten precisión: el inmueble (EXACTA), su
    // edificio (EDIFICIO, el número interior queda reservado) o una zona aproximada por decisión de difusión. Sin ellos,
    // la ficha muestra la zona como aproximada y la dirección pendiente queda para el profesional.
    var ub = inm.ubicacion, dirA = presentable(ub.direccionAutorizada) && ['EXACTA', 'EDIFICIO'].indexOf(ub.direccionAutorizada.precision) >= 0 ? ub.direccionAutorizada : null;
    var pa = ub.puntoAutorizado && VAL.PRECISIONES.indexOf(ub.puntoAutorizado.precision) >= 0 ? ub.puntoAutorizado : null;
    var precision = dirA ? dirA.precision : 'APROXIMADA';
    var ubicacion = {
      precision: precision, direccion: dirA ? dirA.valor : null, zona: zona.join(', '), punto: pa,
      etiqueta: precision === 'EDIFICIO' ? 'Ubicación del edificio' : precision === 'APROXIMADA' ? 'Ubicación aproximada' : null,
      enlace: pa && pa.precision !== 'APROXIMADA' && pa.precision === precision ? enlaceMapa(pa) : null
    };
    if (ub.direccionAutorizada && ub.direccionAutorizada.estado === 'FALTANTE') falta('inmueble.ubicacion.direccionAutorizada', 'Dirección del inmueble', 'FALTANTE', ub.direccionAutorizada.motivo || 'Falta la dirección autorizada para esta salida; la ficha muestra la zona aproximada.', '01');
    // Bajo el título: la dirección, si está autorizada, y la parte de la zona que el título no dice.
    var lineaUbicacion = [ubicacion.direccion].concat(lineaZona ? [lineaZona] : []).filter(Boolean).join(', ');
    var nombreOperacion = operacion === 'VENTA' ? 'Venta' : operacion === 'ALQUILER' ? 'Alquiler' : null;
    var antetitulo = compuesto ? (operacion === 'VENTA' ? 'En venta' : operacion === 'ALQUILER' ? 'En alquiler' : '') : [nombreOperacion, perfil.rotulo].filter(Boolean).join(' · ');

    // Precio (parte 1 · encargo)
    var precio = null, procPrecio = procedencia(datos, pr.importe);
    var rotuloPrecio = operacion === 'ALQUILER' ? 'Alquiler' : operacion === 'VENTA' ? 'Precio de venta' : 'Precio';
    // Nota del precio: su naturaleza y su fecha, sin términos internos (P2). Sin encargo en Core, el precio
    // viene del anuncio de origen.
    var notaPrecio = function () {
      if (demo) return '';
      // 1.4.0: la fecha corta de la guía visual («3 oct 2026»); de qué anuncio sale lo dice el pie, una sola vez.
      var f = procPrecio && procPrecio.observadoEn ? fmt.fechaCorta(procPrecio.observadoEn) : null;
      var cuando = f ? ' el ' + f : '';
      if (pr.hito === 'U') return 'Precio autorizado' + cuando + '.';
      return 'Precio publicado' + cuando + '.';
    };
    // Base del alquiler (modalidad_precio): por m² se escribe en la cifra; sin base, la cifra no se imprime. En el
    // texto, «Alquiler · S/ 6,500 al mes»: alquiler al mes (CAT 11.6; contrato §3.3).
    var porM2 = valor('modalidad_precio') === 'POR_M2_AL_MES';
    var baseFalta = es.faltan.some(function (f) { return f.ref === 'encargo.condiciones.modalidad_precio'; });
    var periodo = pr.periodo === 'MENSUAL' ? (porM2 ? 'por m² al mes' : 'al mes') : null;
    if (presentable(pr.importe) && presentable(pr.moneda) && !baseFalta) {
      var partes = fmt.partesImporte({ importe: pr.importe.valor, moneda: pr.moneda.valor });
      precio = { rotulo: rotuloPrecio, simbolo: partes.simbolo, cifra: partes.cifra, periodo: periodo, nota: notaPrecio(), texto: fmt.importe({ importe: pr.importe.valor, moneda: pr.moneda.valor }) + (periodo ? ' ' + periodo : '') };
    } else if (op.porConfirmarEnPieza && presentable(pr.importe)) {
      var enFuente = pr.moneda && pr.moneda.enFuente, conMoneda = presentable(pr.moneda), notas = [];
      if (!conMoneda) notas.push('Moneda por confirmar' + (enFuente ? ': la fuente escribe «' + enFuente + '»' : ''));
      if (baseFalta) notas.push('Base por confirmar: alquiler total al mes o por m²');
      var p2 = conMoneda ? fmt.partesImporte({ importe: pr.importe.valor, moneda: pr.moneda.valor }) : null;
      precio = { rotulo: rotuloPrecio, simbolo: p2 ? p2.simbolo : '', cifra: p2 ? p2.cifra : fmt.numero(pr.importe.valor), periodo: null, pendiente: true, nota: notas.join('. ') + '.', texto: p2 ? fmt.importe({ importe: pr.importe.valor, moneda: pr.moneda.valor }) : fmt.numero(pr.importe.valor) };
    }

    // Fotografías incluidas según la salida, en el orden de los grupos
    var admitidas = pz.salida === 'DIFUSION_AUTORIZADA' ? ['SALIDA_AUTORIZADA'] : ['SALIDA_AUTORIZADA', 'REVISION_PRIVADA'];
    var porId = {}; (inm.fotos || []).forEach(function (f) { if (admitidas.indexOf(f.autorizacion) >= 0) porId[f.id] = f; });
    var grupos = [], usadas = {};
    (pres.grupos || []).forEach(function (g) { var fs = g.fotos.map(function (id) { return porId[id]; }).filter(Boolean); fs.forEach(function (f) { usadas[f.id] = true; }); if (fs.length) grupos.push({ nombre: g.nombre, fotos: fs }); });
    var sueltas = (inm.fotos || []).filter(function (f) { return porId[f.id] && !usadas[f.id]; });
    if (sueltas.length) grupos.push({ nombre: 'Otras fotografías', fotos: sueltas });
    var fotos = []; grupos.forEach(function (g, i) { g.indice = i; g.fotos.forEach(function (f) { f._grupo = i; fotos.push(f); }); });
    var portada = porId[pres.portada] || fotos[0] || null;
    var hayEscenas = fotos.some(function (f) { return f.origen === 'ESCENA_ILUSTRATIVA'; });
    if (!fotos.length) falta('inmueble.fotos', 'Fotografías', 'NO_INCLUIDO', 'Sin fotografías autorizadas para esta salida; la portada se compone sin imagen.', '05');
    (inm.fotos || []).forEach(function (f) { if (f.observacion) falta('inmueble.fotos.' + f.id, 'Fotografía «' + f.rotulo + '»', 'OBSERVACION', f.observacion, '05'); });

    // Áreas
    var areas = [];
    perfil.areas.forEach(function (s) {
      var d = dato(s.ref);
      if (presentable(d) && typeof d.valor === 'number') { var pa = procedencia(datos, d); areas.push({ ref: s.ref, rotulo: d.rotulo || s.rotulo, cifra: fmt.numero(d.valor, d.decimales), num: d.valor, unidad: 'm²', def: d.definicion || '', fecha: pa && pa.observadoEn ? pa.observadoEn : null }); }
    });

    // Características (formatos compuestos para baños y cochera)
    var caracteristicas = [];
    var sinUnidad = function (s) {
      if (s.ref === 'ascensores' || s.ref === 'H-17') return 'Sin ascensor';
      if (s.formato === 'cochera') return perfil.id === 'oficina' || perfil.id === 'edificio' || perfil.id === 'otro' ? 'Sin estacionamiento' : 'Sin cochera';
      return 'Sin ' + s.rotulo.toLowerCase();
    };
    perfil.caracteristicas.forEach(function (s) {
      var d = dato(s.ref); if (!presentable(d)) return;
      var v = d.valor, c = { ref: s.ref, icono: s.icono, rotulo: s.rotulo, valor: '', unidad: '', nota: '', texto: false, grupo: s.grupo || 'exteriores' };
      // Una ausencia declarada (cero) es una condición material y se escribe llana: «Sin ascensor».
      if (v === 0 && (s.formato === 'entero' || s.formato === 'cochera')) { c.valor = sinUnidad(s); c.texto = true; c.ausencia = true; caracteristicas.push(c); return; }
      switch (s.formato) {
        case 'banos': {
          var medios = valor('medios_banos'); if (typeof medios !== 'number') medios = Math.round((v % 1) * 2);
          var completos = v - medios * 0.5, totalBanos = completos + medios; c.cuenta = totalBanos;
          // Instrucción del titular (18 oct): el total grande y, debajo, «2 baños completos + 1 medio baño» (1.4.0). El total
          // cuenta los medios baños, así que el desglose hace falta para leerlo y va con la cifra también en la
          // portada A4 (§7.5: «si la nota es necesaria para interpretar la cifra, se conserva junto a ella»).
          if (medios > 0) { c.valor = fmt.numero(totalBanos); c.rotulo = singularSi(totalBanos, 'Baños'); c.notaNecesaria = true; c.nota = (completos === 1 ? '1 baño completo' : fmt.numero(completos) + ' baños completos') + ' + ' + (medios === 1 ? '1 medio baño' : fmt.numero(medios) + ' medios baños'); }
          else { c.valor = fmt.numero(completos); c.rotulo = singularSi(completos, 'Baños completos'); }
          break;
        }
        case 'cochera': {
          c.valor = fmt.numero(v); c.rotulo = v === 1 ? 'Cochera' : 'Cocheras';
          if (perfil.id === 'oficina' || perfil.id === 'edificio' || perfil.id === 'otro') c.rotulo = v === 1 ? 'Estacionamiento' : 'Estacionamientos';
          var notas = [], cap = valor('H-03'), areaC = dato('H-04'), techada = valor('H-06'), disp = valor('H-07'), tipoE = valor('tipo_estacionamiento');
          if (typeof cap === 'number') notas.push('para ' + cap + (cap === 1 ? ' auto' : ' autos'));
          if (presentable(areaC)) notas.push(fmt.numero(areaC.valor, areaC.decimales) + FMT.crear(op.regional).NBSP + 'm²');
          if (techada === true) notas.push('techada');
          if (disp) notas.push('«' + disp.toLowerCase() + '» según la fuente');
          if (tipoE) notas.push('tipo ' + rotuloOpcion('tipo_estacionamiento', tipoE).toLowerCase());
          c.nota = capitalizar(notas.join(' · '));
          break;
        }
        case 'piso': c.valor = fmt.ordinalPiso(v); break;
        case 'nivel': c.valor = fmt.nivel(v); c.texto = !/^\d/.test(c.valor) && c.valor.length > 5; break;
        case 'pisos': c.valor = fmt.numero(v); c.rotulo = singularSi(v, s.rotulo); break;
        case 'metros': c.valor = fmt.numero(v, d.decimales); c.unidad = 'm'; break;
        case 'autos': c.valor = fmt.numero(v); c.unidad = v === 1 ? 'auto' : 'autos'; break;
        case 'anios': c.valor = fmt.numero(v); c.unidad = v === 1 ? 'año' : 'años'; break;
        case 'decimal': c.valor = fmt.numero(v, d.decimales); c.unidad = (CAT.propiedad[s.ref] || {}).unidad || ''; break;
        case 'lista': c.valor = rotuloOpcion(s.ref, v); c.texto = true; break;
        case 'texto': c.valor = String(v); c.texto = String(v).length > 5; break;
        default: c.valor = fmt.numero(v); c.rotulo = singularSi(v, s.rotulo);
      }
      c.num = typeof v === 'number' ? v : null; c.formato = s.formato;
      if (c.cuenta === undefined && (s.formato === 'entero' || s.formato === 'cochera' || s.formato === 'pisos') && typeof v === 'number') c.cuenta = v;
      caracteristicas.push(c);
    });
    // Piso y ascensor van juntos (P2; V06 §3 «4.º piso, sin ascensor»): el ascensor es la nota del piso.
    var celdaPiso = caracteristicas.filter(function (c) { return c.ref === 'piso'; })[0];
    var celdaAscensor = caracteristicas.filter(function (c) { return c.ref === 'ascensores' || c.ref === 'H-17'; })[0];
    if (celdaPiso && celdaAscensor) {
      var nAscensores = valor(celdaAscensor.ref);
      celdaPiso.nota = nAscensores === 0 ? 'Sin ascensor' : nAscensores === 1 ? '1 ascensor' : fmt.numero(nAscensores) + ' ascensores';
      caracteristicas.splice(caracteristicas.indexOf(celdaAscensor), 1);
    }
    // 1.5.0 (revisión del 20 de octubre: unidad y capacidad van juntas): en un estacionamiento, la capacidad es la nota de
    // la unidad («1 unidad · Para 1 auto»), como el ascensor lo es del piso. Una unidad para dos autos no son dos unidades.
    var celdaUnidad = perfil.id === 'estacionamiento' ? caracteristicas.filter(function (c) { return c.ref === 'estacionamientos' && !c.ausencia; })[0] : null;
    var celdaCapacidad = caracteristicas.filter(function (c) { return c.ref === 'H-03'; })[0];
    if (celdaUnidad && celdaCapacidad) {
      var autos = valor('H-03');
      celdaUnidad.nota = 'Para ' + fmt.numero(autos) + (autos === 1 ? ' auto' : ' autos');
      celdaUnidad.notaNecesaria = true;
      caracteristicas.splice(caracteristicas.indexOf(celdaCapacidad), 1);
    }
    // 1.5.0 (revisión del 20 de octubre: «2 estacionamientos» sin más, junto a «Incluye 1 estacionamiento» y a un
    // estacionamiento adicional con precio, obligaba a reconstruir el alcance): en un alquiler, la cifra dice cuántos
    // incluye la renta, con el dato del encargo; si no consta, la cifra queda sola y no se supone nada.
    var celdaEst = operacion === 'ALQUILER' ? caracteristicas.filter(function (c) { return c.ref === 'estacionamientos' && !c.ausencia; })[0] : null;
    var incluidos = cond.estacionamientos_incluidos;
    if (celdaEst && presentable(incluidos) && typeof incluidos.valor === 'number' && typeof valor('estacionamientos') === 'number') {
      var nInc = incluidos.valor, nTot = valor('estacionamientos');
      celdaEst.nota = nInc === 0 ? 'Ninguno incluido en el alquiler' : nInc >= nTot ? (nTot === 1 ? 'Incluido en el alquiler' : 'Incluidos en el alquiler') : fmt.numero(nInc) + (nInc === 1 ? ' incluido' : ' incluidos') + ' en el alquiler';
      celdaEst.notaNecesaria = true;
    }

    // Rasgos (positivos accesorios: el negativo no se muestra) y destacados: el selector local admite
    // hasta cuatro adicionales (V06 §2.6); no limita qué datos muestra el capítulo 03.
    var manual = (pres.destacados || []).slice(0, 4);
    var rasgos = [];
    perfil.rasgos.forEach(function (s) {
      // La esquina ya se escribe en 01 Zona; no se repite entre las características.
      if (s.ref === perfil.esquina) return;
      var d = dato(s.ref); if (!presentable(d)) return;
      var g = s.grupo || 'exteriores';
      if (d.valor === true) rasgos.push({ ref: s.ref, icono: s.icono, texto: s.rotulo, destacado: manual.indexOf(s.ref) >= 0, grupo: g });
      else if (typeof d.valor === 'string' && s.formato === 'lista') rasgos.push({ ref: s.ref, icono: s.icono, texto: rotuloOpcion(s.ref, d.valor), destacado: manual.indexOf(s.ref) >= 0, grupo: g });
    });
    rasgos.sort(function (a, b) { return Number(b.destacado) - Number(a.destacado); });

    // Zona: vía y posición (frases completas)
    var hechosZona = [];
    // 1.4.0 (revisión del 18 de octubre): el tipo de vía es un hecho de la zona, en neutro; el azul queda para lo que
    // distingue al inmueble, como la esquina.
    if (perfil.via) { var tv = valor(perfil.via); if (tv) hechosZona.push({ icono: 'via', texto: rotuloOpcion(perfil.via, tv), neutro: true }); }
    if (perfil.esquina) {
      var es2 = valor(perfil.esquina);
      if (es2 === true) hechosZona.push({ icono: 'esquina', texto: 'En esquina' });
      else if (typeof es2 === 'string') hechosZona.push({ icono: 'esquina', texto: rotuloOpcion(perfil.esquina, es2) });
    }
    var entorno = inm.entorno || null;
    if (!entorno) falta('inmueble.entorno', 'Mapa del entorno', 'NO_INCLUIDO', 'Sin referencias públicas corroboradas para esta zona; la ficha muestra la zona sin mapa.', '01');

    // Destacados del profesional (hasta cuatro, V06 §2.6): van en la portada, bajo las cifras clave.
    var destacadosPortada = rasgos.filter(function (r) { return r.destacado; }).map(function (r) { return { texto: r.texto, icono: r.icono }; });
    if (perfil.esquina && manual.indexOf(perfil.esquina) >= 0) {
      var hechoEsquina = hechosZona.filter(function (h) { return h.icono === 'esquina'; })[0];
      if (hechoEsquina) destacadosPortada.push({ texto: hechoEsquina.texto, icono: 'esquina' });
    }

    // Precio y condiciones por grupos (P4): qué comprende el precio, costos mensuales y condiciones de la
    // operación, en ese orden. Las desfavorables quedan en su grupo con frase completa.
    var filas = { precio: [], costos: [], condiciones: [] }, mantenimiento = null;
    // Un componente de lo que comprende el precio o de un costo que llega FALTANTE es una incertidumbre material
    // (CAT-R05; contrato §3.3 y §7.2): no se imprime un importe incompleto ni un total. Desde 1.3.1 tampoco se
    // escribe que falta: queda en «Qué debe confirmarse» y la pieza no se difunde hasta completarlo.
    perfil.condiciones.forEach(function (s) {
      // 1.4.0 (revisión del 18 de octubre: «El precio comprende → Alquiler total al mes» era circular): la base del
      // alquiler ya está en la cifra, «al mes» o «por m² al mes»; no se repite como fila.
      if (s.ref === 'modalidad_precio') return;
      var g = filas[s.grupo] ? s.grupo : 'condiciones';
      if (s.formato === 'mantenimiento') {
        var cuota = dato('cuota_mantenimiento'), h18 = dato('H-18');
        if (presentable(cuota) && presentable(h18)) { mantenimiento = fmt.importe({ importe: h18.valor.importe, moneda: h18.valor.moneda }) + (h18.valor.periodo === 'MENSUAL' ? ' al mes' : ''); filas[g].push({ ref: 'cuota_mantenimiento', rotulo: s.rotulo, valor: mantenimiento }); }
        else if (presentable(cuota)) {
          falta('H-18', 'Moneda de la cuota de mantenimiento', 'FALTANTE', 'Core registra ' + fmt.numero(cuota.valor) + ' sin moneda ni período; un importe sin moneda no se publica.', '02');
        }
        return;
      }
      var d = dato(s.ref);
      if (d && d.estado === 'FALTANTE' && s.siFalta) return;
      if (!presentable(d)) return;
      // 1.5.0 (revisión del 20 de octubre: el alquiler no explicaba el estacionamiento ni los cargos adicionales): lo que
      // incluye la renta, como frase, y lo que se paga además, con su moneda y su período.
      if (s.formato === 'incluidos') { filas[g].push({ ref: s.ref, frase: d.valor === 0 ? 'No incluye estacionamiento' : 'Incluye ' + fmt.numero(d.valor) + (d.valor === 1 ? ' estacionamiento' : ' estacionamientos'), signo: d.valor > 0, incluidos: d.valor }); return; }
      if (s.formato === 'importe') { filas[g].push({ ref: s.ref, rotulo: s.rotulo, valor: fmt.importe(d.valor) + (d.valor.periodo === 'MENSUAL' ? ' al mes' : '') }); return; }
      if (s.formato === 'rasgo') { if (d.valor === true) filas[g].push({ ref: s.ref, frase: s.texto, signo: true }); else if (d.valor === false && s.no) filas[g].push({ ref: s.ref, frase: s.no, signo: false }); }
      else if (s.formato === 'lista') filas[g].push(s.frase ? { ref: s.ref, frase: rotuloOpcion(s.ref, d.valor) } : { ref: s.ref, rotulo: s.rotulo, valor: rotuloOpcion(s.ref, d.valor), texto: true });
      else if (s.formato === 'meses') filas[g].push({ ref: s.ref, rotulo: s.rotulo, valor: String(d.valor), unidad: d.valor === 1 ? 'mes' : 'meses', icono: 'reloj' });
      else filas[g].push({ ref: s.ref, rotulo: s.rotulo, valor: String(d.valor), texto: String(d.valor).length > 8 });
    });
    // Sujeto del precio (H-26): qué comprende la cifra, primero en su grupo.
    if (pr.sujeto) {
      if (presentable(pr.sujeto)) filas.precio.unshift({ ref: 'encargo.precio.sujeto', frase: String(pr.sujeto.valor) });
      else if (pr.sujeto.estado === 'FALTANTE') {
        falta('encargo.precio.sujeto', 'Qué comprende el precio', 'FALTANTE', pr.sujeto.motivo || 'Falta indicar qué comprende el precio.', '02');
      }
      else if (inferido(datos, pr.sujeto)) falta('encargo.precio.sujeto', 'Qué comprende el precio', 'INFERIDO', 'El valor es una inferencia y no se presenta como hecho.', '02');
    }
    var tituloCondiciones = operacion === 'VENTA' ? 'Condiciones de venta' : operacion === 'ALQUILER' ? 'Condiciones del alquiler' : 'Condiciones';
    var gruposPrecio = [{ id: 'precio', titulo: 'El precio comprende', filas: filas.precio }, { id: 'costos', titulo: 'Costos mensuales', filas: filas.costos }, { id: 'condiciones', titulo: tituloCondiciones, filas: filas.condiciones }]
      .filter(function (g) { return g.filas.length; });
    // Junto al precio de la portada (1.4.0; proximidad: lo que decide va junto). 1.5.0 (revisión del 20 de octubre: el
    // bloque mezclaba pagos y condiciones del contrato con la misma jerarquía, y el estacionamiento se leía en tres
    // lugares): dentro del mismo bloque, separados por espacio y sin tarjetas nuevas, primero lo que el precio incluye,
    // después lo que se paga cada mes además del precio (el mantenimiento dice quién lo paga) y, al final, lo que se
    // exige al firmar un alquiler: garantía, adelanto y plazo. No se calcula un total mensual. Debajo, las condiciones con
    // su sentido, a favor y también las que no lo son, hasta cuatro. El 02 las tiene todas.
    var extrasPrecio = [], pagaMant = valor('mantenimiento_a_cargo_de');
    filas.precio.forEach(function (f) { if (f.incluidos !== undefined) extrasPrecio.push({ grupo: 'incluye', ref: f.ref, frase: f.frase, signo: f.signo }); });
    var notaMant = pagaMant === 'INQUILINO' ? 'a cargo del inquilino' : pagaMant === 'PROPIETARIO' ? 'a cargo del propietario' : '';
    if (mantenimiento) extrasPrecio.push({ grupo: 'pago', ref: 'cuota_mantenimiento', rotulo: 'Mantenimiento', nota: notaMant, notaRef: notaMant ? 'mantenimiento_a_cargo_de' : null, valor: mantenimiento });
    filas.costos.forEach(function (f) { if (f.ref === 'precio_estacionamiento_adicional') extrasPrecio.push({ grupo: 'pago', ref: f.ref, rotulo: f.rotulo, valor: f.valor }); });
    // El IGV del alquiler es parte de lo que se paga cada mes: va con los pagos, como frase.
    filas.costos.concat(filas.condiciones).forEach(function (f) { if (f.ref === 'igv_arrendamiento' && f.frase) extrasPrecio.push({ grupo: 'pago', ref: f.ref, frase: f.frase }); });
    if (operacion === 'ALQUILER') filas.condiciones.forEach(function (f) { if (f.unidad === 'mes' || f.unidad === 'meses') extrasPrecio.push({ grupo: 'contrato', ref: f.ref, rotulo: f.rotulo, valor: f.valor + fmt.NBSP + f.unidad }); });
    var condicionesClave = [];
    gruposPrecio.forEach(function (g) { g.filas.forEach(function (f) { if (typeof f.signo === 'boolean' && f.frase && f.incluidos === undefined) condicionesClave.push(f); }); });
    condicionesClave = condicionesClave.slice(0, 4);
    // Lo que el bloque del precio de la portada ya presenta (1.5.0): el A4 no lo repite en el 02.
    var enCaja = extrasPrecio.map(function (x) { return x.ref; }).concat(extrasPrecio.map(function (x) { return x.notaRef; }), condicionesClave.map(function (f) { return f.ref; })).filter(Boolean);

    // Documentación (capítulo 04): cada hecho una vez, como enunciado, y una sola fecha para el grupo: la más
    // frecuente; un hecho con otra fecha la lleva consigo (P3).
    var documentos = [];
    perfil.documentos.forEach(function (s) {
      var d = dato(s.ref); if (!presentable(d)) return;
      var p = procedencia(datos, d);
      var texto = typeof d.valor === 'boolean' ? (d.valor ? s.si : s.no) : [].concat(d.valor).map(function (x) { return rotuloOpcion(s.ref, x); }).join(', ');
      if (texto) documentos.push({ ref: s.ref, texto: texto, signo: typeof d.valor === 'boolean' ? d.valor : null, respaldo: respaldoDe(p, fmt) });
    });
    var doc = inm.documentacion;
    if (vig(doc)) (doc.elementos || []).forEach(function (e) { var p = procedencia(datos, e); documentos.push({ ref: 'inmueble.documentacion', texto: e.texto, respaldo: respaldoDe(p, fmt) }); });
    else if (doc && doc.estado === 'FALTANTE') falta('inmueble.documentacion', 'Resumen documental', 'FALTANTE', doc.motivo || 'Sin resumen documental autorizado.', '04');
    // 1.4.0: el respaldo más frecuente (tipo y fecha) se escribe una vez para el grupo y cada hecho con otro respaldo
    // lleva el suyo (P3, ahora con el tipo de respaldo además de la fecha). Un hecho sin respaldo no lleva nada.
    var cuentaResp = {}, ordenResp = []; documentos.forEach(function (x) { if (!x.respaldo) return; var k = x.respaldo.texto; if (!cuentaResp[k]) ordenResp.push(k); cuentaResp[k] = (cuentaResp[k] || 0) + 1; });
    var respaldoDocs = documentos.length && documentos.every(function (x) { return x.respaldo; }) ? ordenResp.slice().sort(function (a, b) { return cuentaResp[b] - cuentaResp[a] || ordenResp.indexOf(a) - ordenResp.indexOf(b); })[0] || null : null;
    documentos.forEach(function (x) { x.respaldoPropio = x.respaldo && x.respaldo.texto !== respaldoDocs ? x.respaldo.texto : ''; });

    // Uso y construcción (guardas de V07: revisión vigente, aplicable, con fuente y condiciones)
    var uso = null, u = inm.usoYConstruccion;
    if (u) {
      var valido = u.revision === 'REVISADO_VIGENTE' && u.aplicaAlPredio === true && u.fuente && u.fechaFuente && u.revisadoEl && (u.naturaleza !== 'EJEMPLO_FICTICIO' || demo);
      var hayCond = typeof u.condiciones === 'string' && u.condiciones.trim().length > 0;
      if (valido) {
        var hechos = [];
        var zon = valor('zonificacion'), alt = valor('altura_normativa_pisos');
        if (zon) hechos.push({ rotulo: 'Zonificación', valor: zon });
        if (Number.isInteger(alt) && alt > 0 && hayCond) hechos.push({ rotulo: 'Altura máxima según la fuente', valor: alt + (alt === 1 ? ' piso' : ' pisos'), pisos: alt });
        if (typeof u.alturaMaximaMetros === 'number' && hayCond) hechos.push({ rotulo: 'Altura máxima en metros', valor: fmt.numero(u.alturaMaximaMetros) + FMT.crear(op.regional).NBSP + 'm' });
        if (hechos.length) uso = { hechos: hechos, condiciones: u.condiciones || '', fuente: u.fuente + ' · Fuente: ' + fmt.fechaCorta(u.fechaFuente) + ' · Revisión: ' + fmt.fechaCorta(u.revisadoEl) + (u.naturaleza === 'EJEMPLO_FICTICIO' ? ' · Ejemplo ficticio' : '') };
      } else {
        // Revisión no vigente, en conflicto o sin aplicación acreditada: una incertidumbre material (contrato §7.2).
        // Desde 1.3.1 no se escribe en la pieza: queda en «Qué debe confirmarse» y la pieza no se difunde hasta
        // completarla (validar.js, INCERTIDUMBRE_MATERIAL). No se muestra ninguna altura ni zonificación.
        var causaUso = u.aplicaAlPredio !== true ? 'No consta que los parámetros urbanísticos revisados se apliquen a este predio.'
          : u.revision === 'EN_CONFLICTO' ? 'Las fuentes de los parámetros urbanísticos no coinciden.'
          : u.revision === 'VENCIDO' ? 'La revisión de los parámetros urbanísticos venció.'
          : u.revision === 'PENDIENTE' ? 'La revisión de los parámetros urbanísticos está pendiente.'
          : 'La revisión de los parámetros urbanísticos no indica su fuente o su fecha.';
        falta('inmueble.usoYConstruccion', 'Parámetros urbanísticos', 'FALTANTE', causaUso + ' No se muestra una altura máxima.', '03');
      }
    }

    // Cifras clave de la portada (P5): orden fijo por tipo, sólo lo que consta y hasta cinco; la misma medición
    // no se repite, y dos áreas de distinta definición se muestran aunque sus valores coincidan. Una ausencia
    // declarada se escribe como frase («Sin cochera»).
    var buscarCifra = function (ref) {
      if (ref === '@area') return areas[0] ? { ref: areas[0].ref, valor: areas[0].cifra, unidad: areas[0].unidad, rotulo: areas[0].rotulo, area: true, icono: 'area' } : null;
      if (ref === '@via') { var hv = hechosZona.filter(function (x) { return x.icono === 'via'; })[0]; return hv ? { ref: perfil.via, valor: hv.texto, frase: true, icono: 'via' } : null; }
      if (ref === '@zonificacion') { var hz = uso && uso.hechos && uso.hechos.filter(function (x) { return x.rotulo === 'Zonificación'; })[0]; return hz ? { ref: 'zonificacion', valor: hz.valor, rotulo: 'Zonificación', texto: true, icono: 'documento' } : null; }
      var ar = areas.filter(function (x) { return x.ref === ref; })[0];
      if (ar) return { ref: ar.ref, valor: ar.cifra, unidad: ar.unidad, rotulo: ar.rotulo, area: true, icono: 'area' };
      var c = caracteristicas.filter(function (x) { return x.ref === ref; })[0];
      if (!c) return null;
      // 1.5.0: en un estacionamiento, la cifra de la unidad se rotula «Estacionamiento» y lleva su capacidad debajo.
      var rotuloCifra = c.ref === 'estacionamientos' && perfil.id === 'estacionamiento' ? singularSi(c.num, 'Estacionamientos') : c.rotulo;
      return c.ausencia ? { ref: c.ref, valor: c.valor, frase: true, icono: c.icono } : { ref: c.ref, valor: c.valor, unidad: c.unidad, rotulo: rotuloCifra, nota: c.nota, notaNecesaria: !!c.notaNecesaria, texto: c.texto, icono: c.icono, pisos: c.formato === 'pisos' && typeof c.num === 'number' && c.num > 0 && c.num <= 12 ? c.num : 0 };
    };
    var cifras = [];
    (perfil.cifras || []).forEach(function (t) {
      var opciones = t.split('|'), hallada = null;
      for (var i = 0; i < opciones.length && !hallada; i++) hallada = buscarCifra(opciones[i]);
      if (!hallada || cifras.length >= 6) return;
      if (cifras.some(function (x) { return x.ref === hallada.ref; })) return;
      cifras.push(hallada);
    });

    // Capítulo 03 por subgrupos (P7): áreas primero, después los grupos del tipo en orden fijo y, al final, uso y
    // construcción. Cada grupo es una lista de una columna: rótulo y valor, o una frase completa.
    var gruposCarac = [];
    if (areas.length) {
      // 1.5.0: sin la fecha de las áreas (el pie ya no fecha los datos; la fecha de captura sigue en Core).
      gruposCarac.push({ id: 'areas', titulo: 'Áreas', nota: '', filas: areas.map(function (x) { return { ref: x.ref, rotulo: x.rotulo, valor: x.cifra, unidad: x.unidad, nota: x.def, num: x.num, formato: 'area', icono: 'area' }; }) });
    }
    GRUPOS_CARAC.forEach(function (id) {
      // Dentro del grupo «Cochera» o «Estacionamiento», el número de plazas se rotula «Unidades» (P7).
      var filasG = caracteristicas.filter(function (c) { return c.grupo === id; }).map(function (c) { return c.ausencia ? { ref: c.ref, frase: c.valor, icono: c.icono } : { ref: c.ref, rotulo: c.ref === 'estacionamientos' && id === 'cochera' ? singularSi(c.num, 'Unidades') : c.rotulo, valor: c.valor, unidad: c.unidad, nota: c.nota, notaNecesaria: !!c.notaNecesaria, texto: c.texto, icono: c.icono, num: c.num, cuenta: c.cuenta, formato: c.formato }; })
        .concat(rasgos.filter(function (r) { return r.grupo === id; }).map(function (r) { return { ref: r.ref, frase: r.texto, destacado: r.destacado, icono: r.icono, rasgo: true }; }));
      if (filasG.length) gruposCarac.push({ id: id, titulo: tituloGrupoCarac(id, perfil), filas: filasG });
    });

    // Datos declarados FALTANTE e inferidos (todo el contrato, sin duplicar)
    var revisar = function (ref, d) {
      if (!d) return;
      if (d.estado === 'FALTANTE') falta(ref, rotuloRef(ref, perfil), 'FALTANTE', d.motivo || 'No consta en la fuente.', capituloDe(ref));
      else if (vig(d) && inferido(datos, d)) falta(ref, rotuloRef(ref, perfil), 'INFERIDO', 'El valor es una inferencia y no se presenta como hecho.', capituloDe(ref));
    };
    Object.keys(atr).forEach(function (k) { revisar(k, atr[k]); });
    Object.keys(huecos).forEach(function (k) { revisar(k, huecos[k]); });
    Object.keys(cond).forEach(function (k) { revisar(k, cond[k]); });
    // Exigencias de Core (ALT confirma el registro; PUB permite publicar)
    var tipo = vig(inm.tipo) ? inm.tipo.valor : null;
    if (tipo) Object.keys(CAT.propiedad).forEach(function (k) {
      var def = CAT.propiedad[k], ex = def.aplicabilidad[tipo];
      if (def.activa && (ex === 'ALT' || ex === 'PUB') && !vig(atr[k])) falta(k, rotuloRef(k, perfil), ex, ex === 'ALT' ? 'Core exige este dato para confirmar el registro.' : 'Core exige este dato para publicar.', capituloDe(k));
    });

    // Notas para el profesional (1.5.0): nunca entran en la pieza. Una observación sobre un valor va a «Qué debe
    // confirmarse»; una incertidumbre declarada deja además la pieza en vista previa (validar.js, incertidumbres()).
    var notas = function (grupo) { Object.keys(grupo || {}).forEach(function (k) { var d = grupo[k]; if (!vig(d)) return; if (d.observacion) falta(k + '.observacion', rotuloRef(k, perfil), 'OBSERVACION', d.observacion, capituloDe(k)); if (d.incertidumbre) falta(k + '.incertidumbre', rotuloRef(k, perfil), 'FALTANTE', 'Falta confirmar ' + d.incertidumbre + '. Hasta confirmarlo, la ficha no se difunde.', capituloDe(k)); }); };
    notas(atr); notas(huecos); notas(cond);

    // Estado de la pieza (RF-080; CAT-E17): VISTA_PREVIA o LISTA_PARA_DIFUSION. validar.js decide y sólo puede bajar lo
    // que Core autorizó. 1.5.0 (revisión del 20 de octubre: «el sistema explica el estado y las pendientes; la salida
    // comercial presenta la oferta»): el estado ya no se escribe en la pieza ni en el PDF; viaja al anfitrión en la
    // respuesta, con su aviso redactado, y el anfitrión lo muestra fuera de la pieza. Quitar el rótulo no cambia la
    // evaluación: «Obtener para difusión» la repite en Core antes de entregar el archivo.
    var ep = VAL.estadoPieza(datos);
    var motivos = ep.motivos.map(function (mo) { return { codigo: mo.codigo, detalle: textoMotivo(mo, fmt) }; });
    // El aviso del estado más grave (borrador, versión desactualizada, demostración y vista previa privada; P1), redactado
    // para el anfitrión, que lo muestra fuera de la pieza (1.5.0). Los demás motivos van a «Qué debe confirmarse».
    var delEncargo = demo ? null : ep.motivos.filter(function (mo) { return MOTIVOS_DEL_ENCARGO.indexOf(mo.codigo) >= 0; })[0];
    var privada = !demo && ep.motivos.some(function (mo) { return mo.codigo === 'SALIDA_PRIVADA'; });
    var incierta = demo ? null : ep.motivos.filter(function (mo) { return mo.codigo === 'INCERTIDUMBRE_MATERIAL'; })[0];
    var desactualizada = ep.motivos.filter(function (mo) { return mo.codigo === 'DESACTUALIZADA'; })[0];
    var faltanLlanos = es.faltan.map(function (f) { return { ref: f.ref, rotulo: /^inmueble\.atributos\./.test(f.ref) ? rotuloRef(f.ref.split('.').pop(), perfil) : f.rotulo }; });
    var avisoEstado = null;
    if (es.estado === 'BORRADOR') avisoEstado = { clase: 'borrador', titulo: 'Borrador · no difundir.', texto: textoBorrador(faltanLlanos, datos) };
    else if (desactualizada) avisoEstado = { clase: 'desactualizada', titulo: 'Versión desactualizada · no difundir.', texto: textoMotivo(desactualizada, fmt) };
    else if (demo) avisoEstado = { clase: 'demo', titulo: 'Demostración · no difundir.', texto: hayEscenas ? 'Datos ficticios y escenas ilustrativas de BROX; no describen un inmueble.' : 'Datos ficticios para revisar la composición; no describen un inmueble.' };
    else if (delEncargo || privada || incierta) avisoEstado = { clase: 'privada', titulo: 'Vista previa privada · no difundir.', texto: textoMotivo(delEncargo || (privada ? { codigo: 'SALIDA_PRIVADA' } : incierta), fmt) };
    if (delEncargo) {
      var dondeEncargo = { SIN_ENCARGO: ['encargo', 'Encargo seleccionado'], ENCARGO_NO_APROBADO: ['encargo.estado', 'Aprobación del encargo'], VIGENCIA_FALTANTE: ['encargo.vigenteHasta', 'Vigencia del encargo'], ENCARGO_VENCIDO: ['encargo.vigenteHasta', 'Vigencia del encargo'] }[delEncargo.codigo];
      falta(dondeEncargo[0], dondeEncargo[1], 'DIFUSION', textoMotivo(delEncargo, fmt), 'salida');
    }
    if (desactualizada) falta('pieza.desactualizada', 'Versión de la ficha', 'DIFUSION', textoMotivo(desactualizada, fmt) + ' Una versión desactualizada no se difunde.', 'salida');

    // 1.5.0 (revisión del 20 de octubre): el pie ya no dice de dónde salen los datos ni la versión; la fuente, la fecha de
    // captura y la versión siguen en Core y en la trazabilidad de la entrega (metadatos del HTML exportado). La pieza sólo
    // conserva lo que cambia cómo se lee la oferta: la fecha del precio y la de los parámetros urbanísticos. Una
    // demostración lo dice una vez, al pie, para que una hoja suelta no pase por una oferta real.
    var notaDemo = demo ? 'Datos de demostración: no describen un inmueble.' : '';

    // Espacios de foto reservados (1.2.3; instrucción del titular): desde 1.5.0 sólo en la vista de preparación
    // (opciones.preparacion), para que el profesional vea dónde va cada foto. La salida comercial no lleva recuadros vacíos
    // (revisión del 20 de octubre): sin fotografías, la portada se recompone.
    var reservas = op.preparacion && !fotos.length ? (perfil.reservasFoto || []).slice() : [];

    faltantes.sort(function (a, b) { return Number(b.esencial) - Number(a.esencial); });
    // Tiempos a pie o en auto (H-30) sólo con la ubicación autorizada del inmueble o de su edificio: varios tiempos juntos
    // acotan la ubicación, y la ficha no debe revelar por otra vía lo que la salida reserva (1.5.0).
    var conTiemposAut = !!(entorno && entorno.referencias.some(function (r) { return r.tiempo; }) && (ubicacion.precision === 'EXACTA' || ubicacion.precision === 'EDIFICIO'));
    return {
      reservas: reservas,
      datos: datos, op: op, fmt: fmt, perfil: perfil, demo: demo, estado: es.estado, estadoPieza: ep.estadoPieza, motivos: motivos, avisoEstado: avisoEstado, identidad: identidad, conContacto: conContacto,
      accion: accionDe(identidad, titulo, ubicacion.direccion),
      extrasPrecio: extrasPrecio, condicionesClave: condicionesClave, enCaja: enCaja, respaldoDocs: respaldoDocs,
      antetitulo: antetitulo, titulo: titulo, zona: zona, lineaZona: lineaZona, lineaUbicacion: lineaUbicacion, ubicacion: ubicacion, conTiempos: conTiemposAut,
      precio: precio, fotos: fotos, grupos: grupos, portada: portada, hayEscenas: hayEscenas,
      areas: areas, caracteristicas: caracteristicas, rasgos: rasgos, cifras: cifras, destacados: destacadosPortada, gruposCarac: gruposCarac, hechosZona: hechosZona, entorno: entorno,
      gruposPrecio: gruposPrecio, mantenimiento: mantenimiento, operacion: operacion, documentos: documentos, uso: uso, descripcion: pres.descripcion && pres.descripcion.texto ? pres.descripcion.texto : '',
      faltantes: faltantes, notaDemo: notaDemo, rotuloPrecio: rotuloPrecio,
      // 1.7.0: el enlace seguro de la ficha digital, si la app lo entrega (pieza.enlaceDigital.url, sólo https). Lo usan
      // «Ver las N fotos» en el PDF y «Ver la ficha completa» en la vista para teléfono. La plantilla no crea enlaces.
      enlaceFicha: pz.enlaceDigital && typeof pz.enlaceDigital.url === 'string' && /^https:\/\/[^\s"<>]+$/.test(pz.enlaceDigital.url) ? pz.enlaceDigital.url : null,
      // Trazabilidad (1.5.0): fuera del pie comercial; viaja en los metadatos del HTML exportado y en la respuesta.
      traza: { pieza: pz.id, version: pz.versionContenido, plantilla: pz.plantilla + ' ' + pz.versionPlantilla, contrato: datos.versionContrato, fechaCorte: pz.fechaCorte, fuente: (pz.versionesOrigen || []).map(function (o) { return o.fuente + ' (' + o.fecha + ')'; }).join('; ') }
    };
  }

  // ------------------------------------------------------------------ piezas de marcado compartidas
  // 1.4.0 (revisión entregada por el titular el 18 de octubre y guía visual de BROX Web): la firma comercial con
  // jerarquía propia, el contacto con enlace y una sola acción, la X de BROX arriba y «Ficha generada con BROX» abajo,
  // los capítulos completos a todo el ancho, la letra A4 desde 10 pt y la paleta de la guía.
  var ESCENA = 'Escena ilustrativa · no corresponde a un inmueble';
  // Rótulo de capítulo (guía visual: rótulo de sección): número en mono, nombre en versalitas y un filete de 1 px. En
  // la hoja, el capítulo que sigue en otra hoja lo dice: «continúa».
  function rotulo(no, nombre, sigue) { return '<p class="capitulo-rotulo"><span class="capitulo-nombre">' + esc(nombre) + '</span>' + (sigue ? '<span class="capitulo-sigue">continúa</span>' : '') + '<span class="capitulo-filete" aria-hidden="true"></span></p>'; }
  function imgFoto(f, clase) { return '<img' + (clase ? ' class="' + clase + '"' : '') + ' src="' + esc(f.src) + '" alt="' + esc(f.rotulo) + '" width="' + f.ancho + '" height="' + f.alto + '" decoding="async"' + (f.encuadre ? ' data-enc="' + [f.encuadre.izquierda, f.encuadre.derecha, f.ancho].join(',') + '"' : '') + '>'; }
  // La X de BROX (marca/brox-x-*.svg: los trazos y la fotografía del logotipo oficial, sin las letras), arriba y fuera de
  // la firma comercial: en la línea de la operación de la portada y en la cabecera de las demás hojas. Abajo, la firma de
  // BROX: «Inteligencia inmobiliaria con» y el logotipo completo, cuya palabra BROX completa la frase (1.5.0; revisión
  // entregada por el titular el 20 de octubre; antes, «Ficha generada con»). Sin punto final, sin recuadro.
  function marcaX() { return '<span class="marca-x"><img class="marca-tinta" src="marca/brox-x-ink.svg" alt="BROX" width="348" height="251"><img class="marca-clara" src="marca/brox-x-light.svg" alt="" width="348" height="251"></span>'; }
  // 1.10.0 (instrucción del titular del 26 de octubre: «Inteligencia inmobiliaria con BROX» al centro, «con otra letra o
  // algo más dinámico»): al centro, en la letra de los títulos. 1.12.0 («no debe tener colores debe ser al centro de color
  // negro y el logo»): todo en tinta, con el logotipo; un solo texto.
  function firmaBrox() { return '<span class="brox"><span class="brox-lema">Inteligencia inmobiliaria con</span><img class="marca-tinta" src="marca/brox-wordmark-ink.svg" alt="BROX" width="1094" height="250"><img class="marca-clara" src="marca/brox-wordmark-light.svg" alt="" width="1094" height="250"></span>'; }
  // Logotipo de WhatsApp (1.10.0; instrucción del titular del 26 de octubre: «con su logo real»): el globo con el auricular
  // oficial, en un solo trazo lleno, el mismo que usa la guía de BROX Web; ficha.css lo pinta en el verde de WhatsApp. 1.11.0
  // («no dupliques el logo de whatsapp por todo lado»): va una sola vez, junto al número, sin medalla ni píldora.
  var WA_LOGO = 'M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413z';
  function icoWhatsapp() { return '<svg class="ico ico-wa" viewBox="0 0 24 24" aria-hidden="true"><path d="' + WA_LOGO + '"/></svg>'; }
  // Texto de un contacto: un correo largo se parte, si hace falta, antes de la arroba, nunca a mitad de una palabra.
  function textoEnlace(c) { return c.tipo === 'CORREO' && c.texto.indexOf('@') > 0 ? esc(c.texto.slice(0, c.texto.indexOf('@'))) + '<wbr>' + esc(c.texto.slice(c.texto.indexOf('@'))) : esc(c.texto); }
  function icoContacto(tipo) { return tipo === 'WHATSAPP' ? icoWhatsapp() : ico(tipo === 'CORREO' ? 'correo' : tipo === 'WEB' ? 'web' : 'telefono'); }
  // Un enlace de la pieza se abre fuera de ella: en pantalla, en otra pestaña; en el PDF, Chrome lo conserva como
  // anotación de enlace (https:, tel:, mailto:).
  function abrirFuera(enHoja) { return enHoja ? '' : ' target="_blank" rel="noopener noreferrer"'; }
  // Firma inicial (1.4.0; 1.5.0, revisiones entregadas por el titular el 20 de octubre). Una banda horizontal con dos
  // grupos en el mismo eje: la empresa a la izquierda y, a la derecha, quien atiende con su fotografía al lado del nombre
  // (rectangular, pequeña, sin marcos ni efectos), para que foto y nombre se lean como una unidad. Va una sola vez, al
  // comienzo: en la portada A4, en la cabecera de la lectura digital y en la imagen vertical. La X de BROX no va en la
  // firma: va en la línea de la operación («En venta»), fuera de la identidad comercial. Sin identidad autorizada no se
  // dibuja nada y el anfitrión dice qué falta; sólo la vista de preparación reserva su lugar.
  function imagenMarcada(img, clase) { return '<span class="' + clase + '"><img src="' + esc(img.src) + '" alt="' + esc(img.alt) + '" width="' + Math.round(img.ancho) + '" height="' + Math.round(img.alto) + '"></span>'; }
  function firma(m, contexto) {
    var i = m.identidad;
    if (!i) return m.op.preparacion ? firmaReservada() : '';
    var e = i.empresa, registroEmpresa = !i.persona && i.registro ? '<span class="firma-meta">' + esc(i.registro) + '</span>' : '';
    var empresa = e ? '<div class="firma-empresa">' + (e.logo ? imagenMarcada(e.logo, 'firma-logo') : '<span class="firma-nombre">' + esc(e.nombre) + '</span>') + registroEmpresa + '</div>' : '';
    var persona = i.persona ? '<div class="firma-persona"><span class="firma-textos"><span class="firma-quien">' + esc(i.persona) + '</span>' + (i.registro ? '<span class="firma-meta">' + esc(i.registro) + '</span>' : '') + '</span>'
      + (i.retrato ? imagenMarcada(i.retrato, 'firma-retrato') : m.op.preparacion ? retratoReservado() : '') + '</div>' : '';
    return '<div class="firma' + (e ? '' : ' sin-empresa') + (i.persona ? '' : ' sin-persona') + (i.retrato ? ' con-retrato' : '') + '">' + empresa + persona + '</div>';
  }
  // Un contacto con su enlace (1.4.0): en pantalla se abre fuera de la pieza; en el PDF, Chrome lo conserva.
  function contactoEnlace(c, enHoja) {
    var dentro = icoContacto(c.tipo) + '<span>' + (c.tipo === 'WHATSAPP' ? '<span class="contacto-tipo">WhatsApp</span> ' + esc(c.valor) : textoEnlace(c)) + '</span>';
    return c.href ? '<a class="contacto" data-tipo="' + c.tipo + '" href="' + esc(c.href) + '"' + abrirFuera(enHoja) + '>' + dentro + '</a>' : '<span class="contacto" data-tipo="' + c.tipo + '">' + dentro + '</span>';
  }
  // Canales del pie (1.5.0: menos canales a la vez): el de la acción primero y, si cabe en la misma línea que el nombre,
  // uno más, con el correo antes que el teléfono, WhatsApp o la web. Un nombre largo se queda con su canal principal: el
  // segundo canal no debe apretar el primero (revisión del 20 de octubre).
  var ORDEN_SEGUNDO = ['CORREO', 'TELEFONO', 'WHATSAPP', 'WEB'], LINEA_PIE = 48;
  function canalesPie(m, max) {
    var i = m.identidad, principal = m.accion && m.accion.contacto;
    var orden = i.contactos.slice().sort(function (a, b) { return Number(b === principal) - Number(a === principal) || ORDEN_SEGUNDO.indexOf(a.tipo) - ORDEN_SEGUNDO.indexOf(b.tipo); }).slice(0, max);
    if (orden.length > 1 && (i.contactoNombre + orden[0].texto + orden[1].texto).length > LINEA_PIE) orden = orden.slice(0, 1);
    return orden;
  }
  // Contacto al pie de cada hoja (1.5.0; revisiones del 20 de octubre: una hoja se reenvía sola y debe decir a quién
  // escribir, sin repetir la tarjeta de la empresa): a la izquierda, quien atiende y su canal principal; a la derecha,
  // «Coordinar una visita» hacia ese mismo contacto. En el A4 para imprimir no hay botón: la portada lleva el QR.
  function contactoPie(m, enHoja, conAccion) {
    var i = m.identidad; if (!i) return m.op.preparacion ? pieContactoReservado() : '';
    var canales = canalesPie(m, 2), accion = conAccion && m.accion ? accionMarcado(m.accion, enHoja) : '';
    return '<div class="pie-contacto' + (accion ? ' con-accion' : '') + '"><div class="pie-datos"><p class="pie-quien"><b>' + esc(i.contactoNombre) + '</b></p>'
      + (canales.length ? '<ul class="pie-canales">' + canales.map(function (c) { return '<li>' + contactoEnlace(c, enHoja) + '</li>'; }).join('') + '</ul>' : '') + '</div>' + accion + '</div>';
  }
  // Cierre «Contacto» de la lectura digital: quien atiende, todos sus canales y la acción, en una banda (1.5.0: sin volver
  // a presentar la firma completa).
  function contactoMarcado(m) {
    var i = m.identidad;
    if (!i) return m.op.preparacion ? '<section class="contacto-banda reservado" aria-label="Contacto"><div class="contacto-quien"><p class="contacto-nombre">Agente a cargo</p><p class="contacto-meta">Empresa, si la hay</p></div><ul class="contacto-lista"><li>' + canalReservado() + '</li></ul>' + accionReservada() + '</section>' : '';
    var lista = i.contactos.length ? '<ul class="contacto-lista">' + i.contactos.map(function (c) { return '<li>' + contactoEnlace(c, false) + '</li>'; }).join('') + '</ul>' : '';
    return '<section class="contacto-banda' + (m.accion ? '' : ' sin-accion') + (lista ? '' : ' sin-lista') + '" aria-label="Contacto"><div class="contacto-quien"><p class="contacto-nombre">' + esc(i.contactoNombre) + '</p>' + (i.contactoEmpresa ? '<p class="contacto-meta">' + esc(i.contactoEmpresa) + '</p>' : '') + '</div>' + lista + (m.accion ? accionMarcado(m.accion, false) : '') + '</section>';
  }
  // Botón de la acción (1.6.0; 1.8.0, el del tema). 1.11.0 (revisión del titular del 26 de octubre a la 1.10.0: «no debe
  // quedar el fondo de icono de whatsapp blanco», «no dupliques el logo de whatsapp»): el texto y la flecha de la guía, sin
  // el icono del canal, que ya está junto al número; el canal sigue en su nombre accesible.
  function accionMarcado(a, enHoja) {
    return '<a class="accion" data-canal="' + esc(a.contacto.tipo) + '" href="' + esc(a.href) + '"' + abrirFuera(enHoja) + ' aria-label="' + esc(a.texto + ' por ' + a.canal.toLowerCase()) + '"><span>' + esc(a.texto) + '</span>' + ico('flecha') + '</a>';
  }
  // Código QR de la acción (1.5.0; impresión): el mismo destino que el botón, con el mensaje escrito. Grafito sobre
  // blanco, módulos cuadrados, sin logotipo dentro y con la zona libre de cuatro módulos (DENSO WAVE). El codificador es
  // la biblioteca de Project Nayuki (qrcodegen.js, MIT), local: la pieza no pide nada a la red.
  function qrSvg(texto) {
    var Q = window.qrcodegen; if (!Q || !texto) return '';
    var qr = Q.QrCode.encodeText(texto, Q.QrCode.Ecc.MEDIUM), n = qr.size, b = 4, t = n + 2 * b, d = '';
    for (var y = 0; y < n; y++) for (var x = 0; x < n; x++) {
      if (!qr.getModule(x, y) || (x > 0 && qr.getModule(x - 1, y))) continue;
      var w = 1; while (x + w < n && qr.getModule(x + w, y)) w++;
      d += 'M' + (x + b) + ' ' + (y + b) + 'h' + w + 'v1h-' + w + 'z';
    }
    return '<svg class="qr" viewBox="0 0 ' + t + ' ' + t + '" role="img" aria-label="Código QR para coordinar una visita" shape-rendering="crispEdges" data-modulos="' + n + '"><rect width="' + t + '" height="' + t + '" fill="#FFFFFF"/><path d="' + d + '" fill="#111214"/></svg>';
  }
  // QR en pantalla (1.7.0; instrucción del titular del 20 de octubre: «el QR tiene sentido en la web para que escaneen
  // desde una pantalla»): junto al botón de la ficha digital, sólo en pantallas grandes con puntero (lo decide el CSS),
  // para seguir en el teléfono. El mismo destino que el botón. En el teléfono, en la vista para teléfono y en el PDF para
  // compartir no hay QR: ahí se toca o se pulsa el botón.
  function qrPantalla(m) {
    var a = m.accion, i = m.identidad, svg = a ? qrSvg(a.href) : '';
    return svg ? '<div class="qr-pantalla" title="' + esc('Escanéalo con tu teléfono para ' + a.verbo + ' ' + (a.verbo === 'abrir la web de' && i.contactoEmpresa ? i.contactoEmpresa : i.contactoNombre)) + '">' + svg + '<p>O escanéalo con tu teléfono</p></div>' : '';
  }
  // Banda de la portada para imprimir (1.5.0): el QR de 35 mm con la acción, la instrucción y el contacto. Sin acción no
  // hay QR: queda el contacto breve, y nunca un QR genérico o hacia BROX.
  function pieQR(m) {
    var a = m.accion, i = m.identidad, svg = a ? qrSvg(a.href) : '';
    if (!svg) return contactoPie(m, true, true);
    var canales = canalesPie(m, 2);
    return '<div class="pie-qr">' + svg + '<div class="pie-qr-texto"><p class="pie-qr-accion">' + esc(a.texto) + '</p><p class="pie-qr-instruccion">Escanea el código con la cámara del teléfono para ' + esc(a.verbo) + ' <b>' + esc(a.verbo === 'abrir la web de' && i.contactoEmpresa ? i.contactoEmpresa : i.contactoNombre) + '</b>.</p>'
      + (canales.length ? '<ul class="pie-canales">' + canales.map(function (c) { return '<li>' + contactoEnlace(c, true) + '</li>'; }).join('') + '</ul>' : '') + '</div></div>';
  }
  // 1.3.1 (instrucción del titular): la ficha no escribe lo que no incluye. Sin precio, el lugar del precio invita a
  // consultar; sin documentación, una sola línea (1.4.0: lo demás que falta se omite, sin repetir la invitación).
  var CONSULTAR = 'Consultar con el agente a cargo';
  // El precio de la portada (guía visual, «dinero previsto»): marfil con filete oro de 2 px, rótulo, importe en tinta y
  // su nota; debajo, lo que se paga además o se exige en un alquiler, como pares rótulo · valor.
  function precioMarcado(m, clase, sinExtras) {
    var p = m.precio, extras = sinExtras ? [] : m.extrasPrecio;
    var cuerpo = p ? '<p class="precio-cifra">' + (p.simbolo ? '<small>' + esc(p.simbolo) + '</small>' : '') + '<span>' + esc(p.cifra) + '</span>' + (p.periodo ? '<span class="periodo">' + esc(p.periodo) + '</span>' : '') + '</p>' + (p.nota ? '<p class="precio-nota">' + esc(p.nota) + '</p>' : '')
      : '<p class="precio-consulta">' + CONSULTAR + '</p>';
    // 1.5.0: lo que incluye, lo que se paga cada mes y lo que se exige al firmar, en tres grupos separados por espacio.
    var de = function (g) { return extras.filter(function (x) { return x.grupo === g; }); };
    var incluye = de('incluye'), pagos = de('pago'), contrato = de('contrato');
    var ref = function (r) { return r ? ' data-ref="' + esc(r) + '"' : ''; };
    var lista = function (xs, g) { return xs.length ? '<dl class="precio-extra precio-' + g + '">' + xs.map(function (x) { return x.frase ? '<div class="frase"' + ref(x.ref) + '><dt>' + esc(x.frase) + '</dt></div>' : '<div' + ref(x.ref) + '><dt>' + esc(x.rotulo) + (x.nota ? '<small' + ref(x.notaRef) + '>' + esc(x.nota) + '</small>' : '') + '</dt><dd>' + esc(x.valor) + '</dd></div>'; }).join('') + '</dl>' : ''; };
    var grupos = (incluye.length ? '<ul class="precio-incluye">' + incluye.map(function (x) { return '<li class="' + (x.signo ? 'si' : 'no') + '"' + ref(x.ref) + '>' + ico(x.signo ? 'check' : 'menos') + '<span>' + esc(x.frase) + '</span></li>'; }).join('') + '</ul>' : '') + lista(pagos, 'pago') + lista(contrato, 'contrato');
    return '<div class="' + clase + (p ? '' : ' consulta') + (p && p.pendiente ? ' pendiente' : '') + '"><p class="precio-rotulo">' + esc(p ? p.rotulo : m.rotuloPrecio) + '</p>' + cuerpo
      + (grupos ? '<div class="precio-grupos">' + grupos + '</div>' : '') + '</div>';
  }
  // Condiciones junto al precio: a favor en hoja; las que no lo son, en neutro y con su frase completa.
  function condicionesMarcado(m, clase) {
    if (!m.condicionesClave.length) return '';
    return '<ul class="' + clase + '" aria-label="Condiciones">' + m.condicionesClave.map(function (f) { return '<li class="' + (f.signo ? 'si' : 'no') + '" data-ref="' + esc(f.ref) + '">' + ico(f.signo ? 'check' : 'menos') + '<span>' + esc(f.frase) + '</span></li>'; }).join('') + '</ul>';
  }
  function piezaConsulta(id, que) { return '<section class="pieza pieza-consulta" data-pieza="' + esc(id) + '" data-k="3"><p class="consulta-fila">' + ico('consulta') + '<span><b>' + esc(que) + ':</b> ' + CONSULTAR.charAt(0).toLowerCase() + CONSULTAR.slice(1) + '</span></p></section>'; }
  function cifraConUnidad(valor, unidad) { return esc(valor) + (unidad ? '<span class="unidad">' + esc(unidad) + '</span>' : ''); }
  // Cifras clave junto al título y al precio (P5), cada una con su icono en tinta (guía visual: el icono acompaña): el
  // valor y debajo qué es; una ausencia, como frase. En la hoja las notas quedan en el detalle, salvo la que hace falta
  // para leer la cifra (el desglose de los baños). Un número de pisos lleva su pictograma.
  // 1.3.1: las cifras se reparten en filas sin huecos de una rejilla de seis columnas, de «capacidad» cifras por fila;
  // la que trae desglose ocupa el doble si eso no suma filas ni huecos, y una cifra sola en su fila se lee en una línea.
  function cifrasMarcado(m, clase, sinNotas, capacidad) {
    if (!m.cifras.length) return '';
    var celda = function (c, extra) {
      var figura = c.pisos ? '<span class="cifra-ico pisos">' + pisosPicto(c.pisos) + '</span>' : c.icono ? '<span class="cifra-ico">' + ico(c.icono) + '</span>' : '';
      var contenido = figura + '<b>' + cifraConUnidad(c.valor, c.unidad) + '</b>' + (c.rotulo ? '<span>' + esc(c.rotulo) + '</span>' : '') + (c.nota && (!sinNotas || c.notaNecesaria) ? '<span class="nota">' + esc(c.nota) + '</span>' : '');
      // En pantalla, cada cifra lleva a su capítulo: la vía a 01 y lo demás a 03 (interacción; el A4 no enlaza).
      var destino = c.icono === 'via' ? 'zona' : 'areas', clases = [c.frase || c.texto ? 'texto' : ''].concat(extra || []).filter(Boolean);
      return '<li data-ref="' + esc(c.ref) + '"' + (clases.length ? ' class="' + clases.join(' ') + '"' : '') + '>' + (sinNotas ? contenido : '<a class="cifra-enlace" href="#cap-' + destino + '" data-ir="' + destino + '">' + contenido + '</a>') + '</li>';
    };
    if (!capacidad) return '<ul class="' + clase + '" aria-label="Cifras clave">' + m.cifras.map(function (c) { return celda(c, c.notaNecesaria ? ['ancha'] : []); }).join('') + '</ul>';
    var plan = filasCifras(m.cifras, capacidad), unidad = 6 / capacidad, items = [];
    plan.filas.forEach(function (fila) {
      var spans = fila.map(function (i) { return plan.w[i] * unidad; }), resto = 6 - spans.reduce(function (t, x) { return t + x; }, 0);
      var ancha = fila.map(function (i) { return plan.w[i]; }).indexOf(2);
      if (ancha >= 0) spans[ancha] += resto; else for (var k = 0; resto > 0; k = (k + 1) % fila.length) { spans[k]++; resto--; }
      fila.forEach(function (i, k) { items.push(celda(m.cifras[i], ['s' + spans[k], plan.w[i] === 2 ? 'ancha' : '', fila.length === 1 ? 'sola' : ''])); });
    });
    return '<ul class="' + clase + ' en-filas" aria-label="Cifras clave">' + items.join('') + '</ul>';
  }
  // 1.4.0 (revisión del 18 de octubre: en Los Jardines y Cieneguilla el desglose de los baños ocupaba dos y tres
  // líneas): primero, todas las cifras con desglose en una celda ancha, donde se lee en una línea, aunque eso sume una
  // fila; después, menos filas; después, menos huecos.
  function filasCifras(cifras, capacidad) {
    var anchas = [], mejor = null; cifras.forEach(function (c, i) { if (c.notaNecesaria) anchas.push(i); });
    for (var mask = 0; mask < (1 << anchas.length); mask++) {
      var w = cifras.map(function (c, i) { var j = anchas.indexOf(i); return j >= 0 && (mask >> j) & 1 ? 2 : 1; });
      var filas = empacarCifras(w, capacidad);
      var huecos = filas.reduce(function (t, f) { return t + capacidad - f.reduce(function (u, i) { return u + w[i]; }, 0); }, 0);
      var clave = [-w.filter(function (x) { return x === 2; }).length, filas.length, huecos];
      if (!mejor || clave[0] < mejor.clave[0] || (clave[0] === mejor.clave[0] && (clave[1] < mejor.clave[1] || (clave[1] === mejor.clave[1] && clave[2] < mejor.clave[2])))) mejor = { clave: clave, filas: filas, w: w };
    }
    return mejor;
  }
  // En orden; si la siguiente no cabe en la fila, entra la primera de las que siguen que sí cabe.
  function empacarCifras(w, capacidad) {
    var pend = w.map(function (x, i) { return i; }), filas = [];
    while (pend.length) {
      var fila = [], usado = 0;
      for (var k = 0; k < pend.length && usado < capacidad;) { var i = pend[k]; if (usado + w[i] <= capacidad) { fila.push(i); usado += w[i]; pend.splice(k, 1); } else k++; }
      filas.push(fila);
    }
    return filas;
  }
  // Destacados del profesional (V06 §2.6): lo que distingue al inmueble, en zafiro, con su forma aprobada (tinte, tesela
  // clara, icono azul y letra de cuerpo).
  function destacadosMarcado(m, clase) { return m.destacados.length ? '<ul class="' + clase + '" aria-label="Destacados">' + m.destacados.map(function (d) { return '<li>' + ico(d.icono || 'punto') + '<span>' + esc(d.texto) + '</span></li>'; }).join('') + '</ul>' : ''; }
  // 1.4.0 (revisión del 18 de octubre): la descripción habla del inmueble, con el rótulo «Sobre el inmueble».
  function descripcionMarcado(m, clase) { return m.descripcion ? '<div class="' + clase + '"><p class="lead-rotulo">Sobre el inmueble</p><p class="lead-texto">' + esc(m.descripcion) + '</p></div>' : ''; }
  // Espacios reservados (1.7.0; instrucción del titular del 20 de octubre: «en todos los PDF quiero ver los placeholders
  // donde saldrán» y, en los casos reales, «el espacio para el perfil de la persona a cargo»). Sólo con
  // opciones.preparacion: la salida comercial no lleva recuadros vacíos. Cada espacio tiene la forma de lo que irá ahí y
  // lo nombra en una línea, sin avisos ni números de orden dentro de la pieza: la firma con el logotipo, quien atiende y su
  // foto; el contacto con su botón; cada foto con su ambiente; el mapa.
  function reservaEspacio(nombre, clase, detalle, icono) {
    return '<div class="reserva-espacio' + (clase ? ' ' + clase : '') + '" role="img" aria-label="' + esc('Espacio para ' + nombre.charAt(0).toLowerCase() + nombre.slice(1) + (detalle ? ': ' + detalle.charAt(0).toLowerCase() + detalle.slice(1) : '')) + '"><span class="reserva-espacio-texto">' + ico(icono || 'camara') + '<b>' + esc(nombre) + '</b>' + (detalle ? '<span>' + esc(detalle) + '</span>' : '') + '</span></div>';
  }
  function retratoReservado() { return '<span class="firma-retrato reserva-retrato" role="img" aria-label="Espacio para la foto de quien atiende">' + ico('persona') + '</span>'; }
  function firmaReservada() {
    return '<div class="firma firma-reservada"><div class="firma-empresa"><span class="reserva-logo" role="img" aria-label="Espacio para el logotipo de la empresa">Logotipo</span></div>'
      + '<div class="firma-persona"><span class="firma-textos"><span class="firma-quien">Agente a cargo</span><span class="firma-meta">Nombre y registro</span></span>' + retratoReservado() + '</div></div>';
  }
  function accionReservada() { return '<span class="accion-reservada" role="img" aria-label="Espacio para el botón Coordinar una visita"><span>Coordinar una visita</span>' + ico('flecha') + '</span>'; }
  // 1.10.0 (instrucción del titular del 26 de octubre: «"WhatsApp o teléfono" igual debe estar con su logo real… con los
  // colores de whatsapp»): el lugar del canal lleva el logotipo de WhatsApp y sus colores; 1.11.0, sin píldora.
  function canalReservado() { return '<span class="contacto" data-tipo="WHATSAPP">' + icoWhatsapp() + '<span>WhatsApp o teléfono</span></span>'; }
  function quienReservado() { return '<p class="k-quien reserva-dato"><b>Agente a cargo</b><span class="contacto-wa">' + icoWhatsapp() + '<span>WhatsApp o teléfono</span></span></p>'; }
  // 1.11.0: en la vista para teléfono, el canal de la acción va bajo el nombre (el botón ya no lleva su icono): WhatsApp con
  // su logotipo y el número; otro canal, su texto.
  function canalKairos(a) { return !a ? '' : a.contacto.tipo === 'WHATSAPP' ? '<span class="contacto-wa" title="WhatsApp">' + icoWhatsapp() + '<span>' + esc(a.contacto.valor) + '</span></span>' : '<span class="k-canal">' + icoContacto(a.contacto.tipo) + '<span>' + esc(a.contacto.texto) + '</span></span>'; }
  function pieContactoReservado() {
    return '<div class="pie-contacto con-accion reservado"><div class="pie-datos"><p class="pie-quien"><b>Agente a cargo</b></p><ul class="pie-canales"><li>' + canalReservado() + '</li></ul></div>' + accionReservada() + '</div>';
  }
  function reservasKairos(m) { return '<div class="k-fotos">' + reservaEspacio('Foto de portada', 'k-reserva', m.reservas[0]) + '</div>'; }

  // ------------------------------------------------------------------ tablero (1.2.3; 1.4.0)
  // Cada capítulo es un tablero de piezas (una por grupo) que se reparten el ancho; las celdas de cada pieza crecen
  // hasta completar su fila. Desde 1.4.0 cada pieza es un panel de la guía visual: borde de 1 px, banda de título en
  // papel claro y cuerpo blanco; el icono va en tinta. Una celda es un dato (icono, valor y, pegado debajo, qué es) o
  // un hecho (marca y frase): hoja si está a favor, zafiro si distingue al inmueble y neutro si es una condición, una
  // ausencia declarada o un hecho de la zona.
  function pisosPicto(n) { return '<span class="picto-pisos" aria-hidden="true">' + new Array(Math.min(n, 12) + 1).join('<i></i>') + '</span>'; }
  function celdaMarcado(f) {
    var ref = f.ref ? ' data-ref="' + esc(f.ref) + '"' : '';
    if (f.frase) {
      var tipo = f.signo === true ? ' si' : f.signo === false || f.ausencia ? ' no' : f.rasgo ? ' rasgo' : ' neutro';
      var marca = '<span class="celda-ico">' + ico(f.signo === true ? 'check' : f.signo === false ? 'menos' : (f.icono || 'punto')) + '</span>';
      return '<div class="celda hecho' + tipo + (f.frase.length > 34 ? ' largo' : '') + (f.destacado ? ' destacado' : '') + '"' + ref + '>' + marca + '<p class="celda-frase">' + esc(f.frase) + (f.respaldo ? '<span class="celda-respaldo">' + esc(f.respaldo) + '</span>' : '') + '</p></div>';
    }
    var figura = f.pisos ? '<span class="celda-ico pisos">' + pisosPicto(f.pisos) + '</span>' : f.icono ? '<span class="celda-ico">' + ico(f.icono) + '</span>' : '';
    return '<div class="celda' + (f.texto ? ' texto' : '') + (f.ancha ? ' ancha' : '') + '"' + ref + '>' + figura + '<p class="celda-valor">' + cifraConUnidad(f.valor, f.unidad) + '</p><p class="celda-rotulo">' + esc(f.rotulo) + '</p>' + (f.nota ? '<p class="celda-nota">' + esc(f.nota) + '</p>' : '') + '</div>';
  }
  // Peso de una pieza en su fila: sus celdas (una frase o un texto largo, un valor con unidad de más de once caracteres
  // y una celda ancha cuentan doble), de uno a cuatro; y nunca menos que su título (más de doce letras, dos unidades),
  // para que el título y la cifra no se partan en una pieza angosta (1.4.0).
  function peso(celdas, titulo) {
    var k = 0;
    (celdas || []).forEach(function (f) { k += (f.frase && f.frase.length > 34) || (f.texto && String(f.valor).length > 18) || (!f.frase && String(f.valor || '').length + String(f.unidad || '').length > 11) || f.ancha ? 2 : 1; });
    return Math.max(titulo && titulo.length > 12 ? 2 : 1, Math.min(4, k));
  }
  function piezaMarcado(o) {
    var k = o.k || peso(o.celdas, o.titulo);
    return '<section class="pieza' + (o.clase ? ' ' + o.clase : '') + '" data-pieza="' + esc(o.id) + '" data-k="' + k + '"' + (o.tono ? ' data-tono="' + o.tono + '"' : '') + (o.ref ? ' data-ref="' + esc(o.ref) + '"' : '') + '>'
      + (o.titulo ? '<p class="pieza-titulo">' + esc(o.titulo) + '</p>' : '') + (o.nota ? '<p class="pieza-nota">' + esc(o.nota) + '</p>' : '')
      + (o.celdas && o.celdas.length ? '<div class="celdas">' + o.celdas.map(celdaMarcado).join('') + '</div>' : '') + (o.extra || '') + '</section>';
  }
  // Filas del tablero: las piezas, en su orden, se reparten en filas de hasta C unidades (una por celda) con el menor
  // hueco posible y sin dejar sola en su fila una pieza de una o dos celdas. En pantallas angostas las filas se deshacen.
  function filasTablero(ks, C) {
    var n = ks.length, mejor = [0], corte = [];
    for (var j = 1; j <= n; j++) {
      mejor[j] = Infinity;
      for (var i = j - 1, u = 0; i >= 0; i--) {
        u += ks[i]; if (u > C && j - i > 1) break;
        var costo = Math.pow(Math.max(0, C - u), 2) + (j - i === 1 ? (ks[i] === 1 ? 10 : ks[i] === 2 ? 2 : 0) : 0);
        if (mejor[i] + costo < mejor[j]) { mejor[j] = mejor[i] + costo; corte[j] = i; }
      }
    }
    var filas = []; for (var f = n; f > 0; f = corte[f]) filas.unshift([corte[f], f]);
    return filas;
  }
  function pesos(piezas) { return piezas.map(function (h) { var k = /data-k="(\d+)"/.exec(h); return k ? Number(k[1]) : 1; }); }
  function tablero(piezas, clase) {
    piezas = piezas.filter(Boolean);
    return '<div class="tablero' + (clase ? ' ' + clase : '') + '">' + filasTablero(pesos(piezas), 5).map(function (r) { return '<div class="fila">' + piezas.slice(r[0], r[1]).join('') + '</div>'; }).join('') + '</div>';
  }
  // En la hoja, cada fila del tablero es un bloque de la paginación (§7.5): a todo el ancho y de cinco unidades.
  function filasHoja(piezas) {
    piezas = piezas.filter(Boolean);
    return filasTablero(pesos(piezas), 5).map(function (r) { return '<div class="tablero"><div class="fila">' + piezas.slice(r[0], r[1]).join('') + '</div></div>'; });
  }
  // 03: un subgrupo (P7) es una pieza; sus filas, celdas.
  function piezaGrupo(g) {
    return piezaMarcado({ id: g.id, titulo: g.titulo, nota: g.nota, celdas: g.filas.map(function (f) {
      if (f.frase) return { ref: f.ref, frase: f.frase, icono: f.icono, destacado: f.destacado, rasgo: f.rasgo, ausencia: !f.rasgo };
      return { ref: f.ref, rotulo: f.rotulo, valor: f.valor, unidad: f.unidad, nota: f.nota, ancha: !!f.notaNecesaria, texto: f.texto, icono: f.icono, pisos: f.formato === 'pisos' && typeof f.num === 'number' && f.num > 0 && f.num <= 12 ? f.num : 0 };
    }) });
  }
  // Uso y construcción: la altura permitida con el pictograma de sus pisos, la zonificación y, debajo, condiciones y
  // fuente. En neutro desde 1.4.0: el azul queda para lo que distingue al inmueble.
  function piezaUso(u) {
    if (!u) return '';
    var celdas = u.hechos.map(function (h) { return { rotulo: h.rotulo, valor: h.valor, texto: !/^\d/.test(h.valor), icono: h.rotulo === 'Zonificación' ? 'documento' : 'altura', pisos: h.pisos || 0 }; });
    var extra = '<div class="uso-texto">' + (u.condiciones ? '<p class="uso-condiciones">' + esc(u.condiciones) + '</p>' : '') + '<p class="uso-fuente">' + esc(u.fuente) + '</p></div>';
    return piezaMarcado({ id: 'uso', titulo: 'Uso y construcción', ref: 'inmueble.usoYConstruccion', celdas: celdas, extra: extra, k: 4 });
  }
  // 02 (1.4.0; revisión del 18 de octubre: «Precio y condiciones» repite el precio, porque una hoja se comparte sola):
  // el precio en su pieza, con su nota, y a su lado lo que comprende, los costos mensuales y las condiciones. Lo que
  // falta no se rellena: sin precio, «Consultar con el agente a cargo»; sin grupos, el precio queda solo y respira.
  function piezaPrecio(m) {
    var p = m.precio;
    if (!p) return '<section class="pieza pieza-precio consulta" data-pieza="precio" data-k="2" data-ref="encargo.precio.importe"><p class="pieza-titulo">' + esc(m.rotuloPrecio) + '</p><div class="precio-bloque"><p class="precio-valor">' + CONSULTAR + '</p></div></section>';
    return '<section class="pieza pieza-precio" data-pieza="precio" data-k="2" data-ref="encargo.precio.importe"><p class="pieza-titulo">' + esc(p.rotulo) + '</p><div class="precio-bloque"><p class="precio-valor">' + esc(p.texto) + '</p>' + (p.nota ? '<p class="pieza-nota">' + esc(p.nota) + '</p>' : '') + '</div></section>';
  }
  function piezasPrecio(m) { return [piezaPrecio(m)].concat(m.gruposPrecio.map(piezaGrupoPrecio)); }
  function piezaGrupoPrecio(g) {
    return piezaMarcado({ id: g.id, titulo: g.titulo, celdas: g.filas.map(function (f) {
      return f.frase ? { ref: f.ref, frase: f.frase, signo: f.signo } : { ref: f.ref, rotulo: f.rotulo, valor: f.valor, unidad: f.unidad, texto: f.texto, icono: f.icono || (f.ref === 'cuota_mantenimiento' ? 'casa' : f.ref === 'precio_estacionamiento_adicional' ? 'auto' : null) };
    }) });
  }
  // 04: cada hecho documental una vez, como enunciado, con su respaldo (1.4.0): el más frecuente arriba, una vez; el
  // que tiene otro, junto a su hecho. 1.5.0: sin información publicable, el capítulo no se dibuja (capitulos()).
  function piezasDocs(m) {
    return [piezaMarcado({ id: 'documentacion', tono: 'hoja', nota: m.respaldoDocs ? m.respaldoDocs + '.' : '', k: 4, celdas: m.documentos.map(function (d) { return { ref: d.ref, frase: d.texto, signo: d.signo, icono: 'documento', respaldo: d.respaldoPropio }; }) })];
  }
  // 01 Ubicación (1.5.0; revisión entregada por el titular el 20 de octubre: la ficha debe permitir reconocer dónde está
  // el inmueble). Un solo encabezado y, debajo, la dirección autorizada con la zona, o la zona rotulada una vez como
  // «Ubicación aproximada»; la del edificio dice que el mapa marca el edificio. «Ver ubicación» sólo con un punto del
  // inmueble o de su edificio. A su lado, la vía (neutra) y la esquina (rasgo) como hechos.
  // 1.6.0: con el mapa en la portada, Ubicación va en la misma hoja que las cifras y los destacados, y no repite la vía ni
  // la esquina que ya se leen ahí (omitir: iconos de los hechos de la zona que la portada ya muestra).
  function piezaUbicacion(m, enHoja, omitir) {
    var u = m.ubicacion, hechos = m.hechosZona.filter(function (x) { return (omitir || []).indexOf(x.icono) < 0; }).map(function (x) { return { frase: x.texto, icono: x.icono, rasgo: !x.neutro }; });
    var lugar = u.direccion ? '<p class="ubicacion-direccion">' + esc(u.direccion) + '</p>' + (u.zona ? '<p class="ubicacion-zona">' + esc(u.zona) + '</p>' : '')
      : '<p class="ubicacion-direccion">' + esc(u.zona) + '</p>';
    var enlace = u.enlace ? '<a class="enlace-ver" href="' + esc(u.enlace) + '"' + abrirFuera(enHoja) + '>Ver ubicación' + ico('ampliar') + '</a>' : '';
    return '<section class="pieza pieza-ubicacion' + (hechos.length ? '' : ' sola') + '" data-pieza="ubicacion" data-precision="' + u.precision + '" data-k="' + (hechos.length ? 4 : 3) + '"><div class="ubicacion-tarjeta">' + ico('pin') + '<div class="ubicacion-textos">'
      + (u.etiqueta ? '<p class="ubicacion-etiqueta">' + esc(u.etiqueta) + '</p>' : '') + lugar + enlace + '</div></div>'
      + (hechos.length ? '<div class="celdas">' + hechos.map(celdaMarcado).join('') + '</div>' : '') + '</section>';
  }
  // El mapa con el inmueble marcado encima (1.5.0): el archivo sólo trae calles y lugares públicos; la ficha pone el
  // punto autorizado con el encuadre de la variante. Un punto del inmueble o de su edificio es un marcador de grafito; una
  // ubicación aproximada, un círculo de su radio, sin marcador. El inmueble no se confunde con un lugar público.
  function marcaMapa(m, variante) {
    var u = m.ubicacion, p = u.punto && (u.precision !== 'APROXIMADA' || u.punto.precision === 'APROXIMADA') ? sobreMapa(u.punto, variante) : null;
    if (!p) return '';
    if (p.r !== null) return '<span class="mapa-zona" data-x="' + p.x + '" data-y="' + p.y + '" data-r="' + p.r + '" aria-hidden="true"></span>';
    return '<span class="mapa-marca" data-x="' + p.x + '" data-y="' + p.y + '" aria-hidden="true"><span class="mapa-marca-caja">' + ico(u.precision === 'EDIFICIO' ? 'oficina' : 'casa') + '</span></span>';
  }
  function altMapa(m) {
    var u = m.ubicacion, base = m.entorno.mapa.alt || 'Mapa del entorno';
    var marca = u.punto && u.precision !== 'APROXIMADA' ? (u.precision === 'EDIFICIO' ? ' El marcador señala el edificio.' : ' El marcador señala el inmueble.') : u.punto ? ' El círculo señala la zona aproximada.' : '';
    return base.replace(/([^.])$/, '$1.') + marca;
  }
  // Referencias públicas (H-22): número, nombre y categoría y, con la ubicación autorizada, el tiempo con su modo
  // (H-30): «Estación Ejemplo · 5 min a pie». Nunca un tiempo calculado en línea recta: viaja hecho desde Core.
  var MODO_TIEMPO = { A_PIE: 'a pie', EN_AUTO: 'en auto', EN_TRANSPORTE_PUBLICO: 'en transporte público' };
  function tiempoLugar(m, r) { return m.conTiempos && r.tiempo ? { minutos: r.tiempo.minutos + ' min', modo: MODO_TIEMPO[r.tiempo.modo] || '' } : null; }
  function notaTiempos(m) {
    if (!m.conTiempos) return '';
    var auto = m.entorno.referencias.some(function (r) { return r.tiempo && r.tiempo.modo !== 'A_PIE'; });
    return 'Tiempos aproximados desde el inmueble' + (auto ? '; en auto o en transporte, según el tráfico' : '') + '.';
  }
  // Atribución del mapa, breve y legible, junto al mapa (OSMF: el aviso y la dirección de la licencia, escritos).
  // 1.7.0: en la imagen para compartir, el crédito va como texto: una imagen no conserva enlaces.
  function atribucionMapa(m, enHoja, sinEnlace) {
    var e = m.entorno, t = esc(e.atribucion);
    return '<p class="mapa-atribucion">' + (e.atribucionEnlace && !sinEnlace ? '<a href="' + esc(e.atribucionEnlace) + '"' + abrirFuera(enHoja) + '>' + t + '</a>' : t) + '</p>';
  }
  // Capítulos de la pieza, numerados en orden (1.4.0): un capítulo sin contenido se omite y los siguientes suben un
  // número. 1.5.0: también la documentación sin nada publicable (revisión del 20 de octubre: con el contacto en cada hoja,
  // «Documentación: consultar con el agente a cargo» no aportaba); el precio siempre está, porque invita a consultar.
  function capitulos(m) {
    var lista = [{ id: 'zona', nombre: 'Ubicación' }, { id: 'precio', nombre: 'Precio y condiciones' }];
    if (m.gruposCarac.length || m.uso) lista.push({ id: 'areas', nombre: 'Áreas y características' });
    if (m.documentos.length) lista.push({ id: 'documentacion', nombre: 'Documentación' });
    if (m.fotos.length || m.reservas.length) lista.push({ id: 'recorrido', nombre: 'Recorrido visual' });
    return lista;
  }
  function conContactoCap(m) { return !!m.identidad || m.op.preparacion; }
  // Sin fotografías, el mapa con el inmueble es la imagen de la portada sólo donde la ubicación es el argumento principal
  // (perfiles.js, portadaSinFotos: terreno y local). En los demás, la portada se reorganiza con lo que hay y el mapa
  // acompaña a «Ubicación» con su lista de lugares (1.5.0; revisión del 20 de octubre: en una oficina, el mapa dominaba
  // la portada y dirigía la lectura al entorno, no al espacio).
  function mapaHeroe(m) { return !m.portada && !m.reservas.length && !!m.entorno && m.perfil.portadaSinFotos === 'mapa'; }

  // ------------------------------------------------------------------ lectura digital
  function renderDigital(m) {
    var h = [], caps = capitulos(m), porId = {};
    caps.forEach(function (c) { porId[c.id] = c; });
    // 1.5.0: sin franja de estado ni rótulos internos; el anfitrión dice el estado fuera de la pieza.
    var f = firma(m, 'digital');
    if (f) h.push('<header class="identidad"><div class="envoltura">' + f + '</div></header>');
    h.push(portadaDigital(m));
    h.push('<nav class="subnav" aria-label="Capítulos de la ficha"><div class="envoltura"><ol>' + caps.map(function (c) { return '<li><a href="#cap-' + c.id + '" data-cap="' + c.id + '">' + esc(c.nombre) + '</a></li>'; }).join('')
      + (conContactoCap(m) ? '<li><a href="#cap-contacto" data-cap="contacto">Contacto</a></li>' : '') + '</ol>'
      + (m.precio && !m.precio.pendiente ? '<p class="subnav-precio"><span>' + esc(m.precio.rotulo) + '</span>' + esc(m.precio.texto) + '</p>' : '') + '</div><span class="subnav-progreso" aria-hidden="true"></span></nav>');
    caps.forEach(function (c) {
      if (c.id === 'zona') h.push(capZona(m, c.no));
      else if (c.id === 'precio') h.push(capPrecio(m, c.no));
      else if (c.id === 'areas') h.push(capAreas(m, c.no));
      else if (c.id === 'documentacion') h.push(capDocs(m, c.no));
      else if (c.id === 'recorrido') h.push(capRecorrido(m, c.no));
    });
    if (conContactoCap(m)) h.push('<section class="capitulo capitulo-contacto" id="cap-contacto" data-cap="contacto"><div class="envoltura">' + rotulo(null, 'Contacto') + contactoMarcado(m) + '</div></section>');
    // Pie (1.5.0): «Ficha generada con BROX» y, en una demostración, que sus datos son ficticios; sin versión ni fuente.
    h.push('<footer class="pie"><div class="envoltura">' + (m.notaDemo ? '<p class="pie-fuente">' + esc(m.notaDemo) + '</p>' : '<span></span>') + '<div class="pie-brox">' + firmaBrox() + '</div></div></footer>');
    return '<div class="digital">' + h.join('') + '</div>';
  }
  // Portada (P5; NN/g: las cifras junto a la galería). A la izquierda, la galería; a la derecha, en un panel: qué es y
  // dónde, el precio con lo que se paga además y sus condiciones, las cifras clave, lo que distingue al inmueble, la
  // descripción y, al pie del panel, la acción con quién atiende (guía visual: la tarjeta termina en su botón).
  function resumenMarcado(m) {
    var i = m.identidad, conVisual = m.portada || m.reservas.length || mapaHeroe(m);
    // 1.5.0: dos grupos (lo que es, dónde y cuánto mide | cuánto cuesta y en qué condiciones); sin fotos, son las dos
    // columnas de la portada; con galería, el panel es una sola columna y CSS ordena precio, condiciones y cifras.
    return '<div class="resumen"><div class="resumen-izq">'
      + '<div class="resumen-cabeza"><div class="resumen-operacion">' + (m.antetitulo ? '<p class="antetitulo"><i aria-hidden="true"></i>' + esc(m.antetitulo) + '</p>' : '<span></span>') + marcaX() + '</div><h1 class="titulo">' + esc(m.titulo) + '</h1>' + (m.lineaUbicacion ? '<p class="zona-linea">' + ico('pin') + '<span>' + esc(m.lineaUbicacion) + '</span></p>' : '') + '</div>'
      + cifrasMarcado(m, 'cifras-clave', false, conVisual ? 2 : 0) + destacadosMarcado(m, 'destacados') + '</div>'
      + '<div class="resumen-der">' + precioMarcado(m, 'precio-portada') + condicionesMarcado(m, 'condiciones-clave') + '</div>' + descripcionMarcado(m, 'lead')
      + (m.accion ? '<div class="resumen-accion">' + accionMarcado(m.accion, false) + '<p class="resumen-quien"><b>' + esc(i.contactoNombre) + '</b>' + (i.contactoEmpresa ? ' · ' + esc(i.contactoEmpresa) : '') + '</p>' + qrPantalla(m) + '</div>'
        : !i && m.op.preparacion ? '<div class="resumen-accion reservado">' + accionReservada() + '<p class="resumen-quien"><b>Agente a cargo</b></p></div>' : '')
      + '</div>';
  }
  // Fotos de la galería de portada: una por cada otro ambiente y, si faltan, las siguientes; hasta cuatro.
  function fotosLaterales(m, max) {
    var p = m.portada, otros = [];
    m.grupos.forEach(function (g) { if (g.fotos[0] !== p) otros.push(g.fotos[0]); });
    m.fotos.forEach(function (f) { if (f !== p && otros.indexOf(f) < 0) otros.push(f); });
    return otros.slice(0, max);
  }
  function galeriaPortada(m) {
    var p = m.portada, n = m.fotos.length, lat = fotosLaterales(m, 4);
    if (lat.length < 2) lat = [];
    var resto = n - 1 - lat.length;
    var controles = '<div class="escenario-cabeza">' + (m.hayEscenas ? '<span class="chip cristal aviso-escena"><i aria-hidden="true"></i>' + esc(ESCENA) + '</span>' : '') + '</div>'
      + '<div class="escenario-pie"><span class="chip cristal" id="portada-ambiente"><i aria-hidden="true"></i><span>' + esc(p.rotulo) + '</span></span>'
      + '<span class="escenario-controles">' + (n > 1 ? '<button type="button" class="boton-cuadro cristal" data-paso="-1" aria-label="Fotografía anterior">' + ico('atras') + '</button><span class="contador cristal" id="portada-contador" role="status">' + (m.fotos.indexOf(p) + 1) + ' / ' + n + '</span><button type="button" class="boton-cuadro cristal" data-paso="1" aria-label="Fotografía siguiente">' + ico('flecha') + '</button>' : '')
      + '<button type="button" class="boton-linea cristal ver-original" data-abrir-actual><span>Ampliar foto</span>' + ico('ampliar') + '</button></span></div>';
    // 1.5.0 (revisión del 20 de octubre): cada foto lleva su propio rótulo, el de lo que muestra, no el nombre del grupo.
    var laterales = lat.length ? '<div class="laterales" data-lat="' + lat.length + '">' + lat.map(function (f, i) {
      var nombre = '<span class="lateral-rotulo cristal">' + esc(f.rotulo) + '</span>';
      if (i === lat.length - 1 && resto > 0) return '<a class="lateral lateral-todas" href="#cap-recorrido" data-ir="recorrido"><span class="lateral-marco">' + imgFoto(f) + '</span><span class="lateral-mas">' + ico('fotos') + '<span>Ver las ' + n + ' fotos</span></span></a>';
      return '<button type="button" class="lateral" data-foto="' + esc(f.id) + '" aria-label="Ver ' + esc(f.rotulo) + ' en la portada"><span class="lateral-marco">' + imgFoto(f) + '</span>' + nombre + '</button>';
    }).join('') + '</div>' : '';
    var tira = m.grupos.length > 1 ? '<nav class="tira" aria-label="Ambientes de la propiedad">' + m.grupos.map(function (gr, i) { return '<button type="button" data-grupo="' + i + '" aria-pressed="' + (i === p._grupo) + '"><span class="tira-nombre">' + esc(gr.nombre) + '</span><span class="tira-cuenta">' + gr.fotos.length + '</span></button>'; }).join('') + '</nav>' : '';
    return '<div class="galeria-portada"><div class="escenario marco" id="portada-escenario"><div class="escenario-foto" id="portada-foto">' + imgFoto(p) + '</div>' + controles + '</div>' + laterales + tira + '</div>';
  }
  function reservasPortada(m) {
    var r = m.reservas, lat = r.slice(1, 5);
    return '<div class="galeria-portada reservas">' + reservaEspacio('Foto de portada', 'escenario', r[0])
      + (lat.length ? '<div class="laterales" data-lat="' + lat.length + '">' + lat.map(function (nombre) { return reservaEspacio(nombre, 'lateral'); }).join('') + '</div>' : '') + '</div>';
  }
  // Portada digital (1.5.0): con fotos, la galería; sin fotos, el mapa con el inmueble marcado ocupa su lugar (revisión
  // del 20 de octubre: una ficha con pocas fotos mantiene la misma calidad); sin fotos ni mapa, el panel a todo el
  // ancho; los espacios reservados, sólo en la vista de preparación.
  function portadaDigital(m) {
    if (m.portada) return '<section class="portada con-fotos" aria-label="Portada"><div class="envoltura portada-rejilla">' + galeriaPortada(m) + resumenMarcado(m) + '</div></section>';
    if (m.reservas.length) return '<section class="portada con-reservas" aria-label="Portada"><div class="envoltura portada-rejilla">' + reservasPortada(m) + resumenMarcado(m) + '</div></section>';
    if (mapaHeroe(m)) return '<section class="portada con-mapa" aria-label="Portada"><div class="envoltura portada-rejilla"><div class="galeria-portada">' + figuraMapa(m, 'portada') + '</div>' + resumenMarcado(m) + '</div></section>';
    return '<section class="portada sin-foto" aria-label="Portada"><div class="envoltura portada-rejilla">' + resumenMarcado(m) + '</div></section>';
  }
  // El mapa de la lectura digital: la variante ancha en escritorio y la compacta en el teléfono, claras u oscuras según el
  // tema, con el inmueble marcado encima en la capa de cada variante; debajo, la atribución.
  function figuraMapa(m, donde) {
    var v = m.entorno.mapa.variantes, grande = donde === 'portada' && v.cuadrado ? v.cuadrado : v.ancho;
    var picture = function (tema, alt) { return '<picture class="mapa-' + tema + '"><source media="(max-width: 759px)" srcset="' + esc(v.compacto[tema]) + '"><img src="' + esc(grande[tema]) + '" alt="' + alt + '" width="' + grande.ancho + '" height="' + grande.alto + '"></picture>'; };
    var capas = '<span class="mapa-capa capa-ancho">' + marcaMapa(m, grande) + '</span><span class="mapa-capa capa-compacto">' + marcaMapa(m, v.compacto) + '</span>';
    return '<figure class="figura-mapa' + (donde === 'portada' ? ' en-portada' : '') + '"><div class="marco"><button type="button" class="mapa-boton" data-abrir-mapa aria-label="Ampliar el mapa">'
      + '<span class="mapa-lienzo">' + picture('claro', esc(altMapa(m))) + picture('oscuro', '') + capas + '</span>'
      + '<span class="boton-linea cristal mapa-ampliar"><span>Ampliar mapa</span>' + ico('ampliar') + '</span></button></div>'
      + '<figcaption class="mapa-pie">' + atribucionMapa(m, false) + (donde === 'portada' && m.ubicacion.enlace ? '<a class="enlace-ver" href="' + esc(m.ubicacion.enlace) + '" target="_blank" rel="noopener noreferrer">Ver ubicación' + ico('ampliar') + '</a>' : '') + '</figcaption></figure>';
  }
  function leyendaMarcado(m, clase) {
    return '<ol class="' + clase + '">' + m.entorno.referencias.map(function (r) {
      var t = tiempoLugar(m, r);
      return '<li><span class="leyenda-no">' + esc(r.numero) + '</span><span class="leyenda-texto"><b>' + esc(r.nombre) + '</b><span>' + esc(r.categoria) + '</span></span>' + (t ? '<span class="leyenda-tiempo"><b>' + esc(t.minutos) + '</b><span>' + esc(t.modo) + '</span></span>' : '') + '</li>';
    }).join('') + '</ol>';
  }
  // 01 Ubicación (1.5.0): un encabezado, la dirección o la zona aproximada con su enlace y, con mapa, el mapa con el
  // inmueble y los lugares públicos con su tiempo. Sin el párrafo que explicaba cómo leer los números ni la fecha del mapa.
  function capZona(m, no) {
    var cuerpo;
    if (m.entorno) cuerpo = '<div class="zona-con-mapa">' + figuraMapa(m, 'capitulo') + '<div class="zona-lado">' + piezaUbicacion(m, false) + leyendaMarcado(m, 'leyenda') + (notaTiempos(m) ? '<p class="mapa-tiempos">' + esc(notaTiempos(m)) + '</p>' : '') + '</div></div>';
    else if (m.op.preparacion) cuerpo = '<div class="zona-con-mapa">' + reservaEspacio('Mapa del entorno', 'figura-mapa reserva-mapa', 'Con la ubicación autorizada', 'pin') + '<div class="zona-lado">' + piezaUbicacion(m, false) + '</div></div>';
    else cuerpo = tablero([piezaUbicacion(m, false)]);
    return '<section class="capitulo" id="cap-zona" data-cap="zona"><div class="envoltura">' + rotulo(no, 'Ubicación') + cuerpo + '</div></section>';
  }
  function capPrecio(m, no) { return '<section class="capitulo" id="cap-precio" data-cap="precio"><div class="envoltura">' + rotulo(no, 'Precio y condiciones') + tablero(piezasPrecio(m)) + '</div></section>'; }
  // 03 por subgrupos (P7): áreas, los grupos del tipo y uso y construcción, como piezas que llenan el ancho.
  function capAreas(m, no) {
    var piezas = m.gruposCarac.map(piezaGrupo); if (m.uso) piezas.push(piezaUso(m.uso));
    return '<section class="capitulo" id="cap-areas" data-cap="areas"><div class="envoltura">' + rotulo(no, 'Áreas y características') + tablero(piezas) + '</div></section>';
  }
  function capDocs(m, no) { return '<section class="capitulo" id="cap-documentacion" data-cap="documentacion"><div class="envoltura">' + rotulo(no, 'Documentación') + tablero(piezasDocs(m)) + '</div></section>'; }
  // Recorrido visual: todas las fotografías, por ambiente (instrucción del titular). Cada ambiente compone sus fotos en
  // una rejilla de doce columnas sin huecos: una grande y las demás alrededor, según cuántas sean.
  function disposicion(n) {
    var c = { 1: [[8, 2]], 2: [[6, 2], [6, 2]], 3: [[4, 2], [4, 2], [4, 2]], 4: [[4, 2], [4, 2], [4, 1], [4, 1]], 5: [[6, 2], [3, 1], [3, 1], [3, 1], [3, 1]] };
    if (c[n]) return c[n];
    if (n % 5 === 1) return c[3].concat(c[3], disposicion(n - 6).length ? disposicion(n - 6) : []);
    var r = []; for (var i = 0; i < Math.floor(n / 5); i++) r = r.concat(c[5]);
    return n % 5 ? r.concat(c[n % 5]) : r;
  }
  function capRecorrido(m, no) {
    var cabeza = rotulo(no, 'Recorrido visual') + '<div class="cap-cabeza"><h2 class="cap-titulo">Ambientes de la propiedad</h2>';
    if (!m.fotos.length) {
      return '<section class="capitulo" id="cap-recorrido" data-cap="recorrido"><div class="envoltura">' + cabeza + '</div><ul class="reservas-lista">' + m.reservas.map(function (nombre) { return '<li>' + reservaEspacio(nombre) + '</li>'; }).join('') + '</ul></div></section>';
    }
    // 1.5.0: sin el párrafo que explicaba los grupos; sólo, si las hay, que son escenas ilustrativas.
    var entrada = m.hayEscenas ? ESCENA + '.' : '';
    return '<section class="capitulo" id="cap-recorrido" data-cap="recorrido"><div class="envoltura">' + cabeza + (entrada ? '<p class="cap-entrada">' + esc(entrada) + '</p>' : '') + '</div>'
      + m.grupos.map(function (g, i) {
        var d = disposicion(g.fotos.length), unica = g.fotos.length === 1;
        var info = '<div class="ambiente-cabeza"><h3>' + esc(g.nombre) + '</h3><span class="ambiente-cuenta">' + g.fotos.length + (g.fotos.length === 1 ? ' fotografía' : ' fotografías') + '</span><button type="button" class="ver-ambiente" data-ver-grupo="' + i + '">Ver en pantalla completa' + ico('ampliar') + '</button></div>';
        return '<article class="ambiente' + (unica ? ' unica' : '') + (i % 2 ? ' espejo' : '') + '" id="ambiente-' + (i + 1) + '">' + info + '<div class="ambiente-fotos">'
          + g.fotos.map(function (f, n) { return '<button type="button" class="foto" data-foto="' + esc(f.id) + '" data-c="' + d[n][0] + '" data-r="' + d[n][1] + '" aria-label="Ver ' + esc(f.rotulo) + '"><span class="foto-marco">' + imgFoto(f) + '</span><span class="foto-rotulo cristal">' + esc(f.rotulo) + (f.origen === 'ESCENA_ILUSTRATIVA' ? '<em>' + esc(ESCENA) + '</em>' : '') + '</span></button>'; }).join('') + '</div></article>';
      }).join('') + '</div></section>';
  }

  // ------------------------------------------------------------------ hojas A4 (estáticas)
  // Muchas fotos (1.7.0; pregunta del titular del 20 de octubre: «un caso donde son muchas imágenes, cómo lo vas a
  // manejar»): la galería del PDF lleva hasta 18 fotos además de la portada (tres hojas de seis, en el orden de los
  // ambientes). Si hay más y la app entrega el enlace de la ficha digital, la última hoja termina con «Ver las N fotos»
  // hacia ella; sin enlace van todas: nada se pierde. Un PDF que se comparte por WhatsApp no debe pesar decenas de megas.
  var GALERIA_MAX = 18;
  function fotosGaleria(m) {
    var todas = []; m.grupos.forEach(function (g) { g.fotos.forEach(function (f) { if (f !== m.portada) todas.push(f); }); });
    return m.enlaceFicha && todas.length > GALERIA_MAX ? todas.slice(0, GALERIA_MAX) : todas;
  }
  // 1.7.0: las fotos elegidas llenan hojas de seis en el orden de los ambientes; un ambiente puede seguir en la hoja
  // siguiente y cada foto dice su ambiente. Antes, un ambiente que no cabía entero empezaba hoja nueva y dejaba hojas de
  // dos o tres fotos.
  function paginasGaleria(m) {
    if (!m.op.galeria) return [];
    var items = [], elegidas = fotosGaleria(m), paginas = [];
    m.grupos.forEach(function (g) { g.fotos.forEach(function (f) { if (f !== m.portada && elegidas.indexOf(f) >= 0) items.push({ foto: f, grupo: g.nombre }); }); });
    for (var i = 0; i < items.length; i += 6) paginas.push(items.slice(i, i + 6));
    return paginas;
  }

  // Cabeceras (1.5.0; revisiones entregadas por el titular el 20 de octubre): la portada abre con la banda de la firma
  // (empresa y quien atiende) y una línea fina; la X de BROX va debajo, en la línea de la operación. Las demás hojas
  // empiezan con el título breve del inmueble y la X, sin volver a presentar al profesional.
  function cabezaPortada(m) { var f = firma(m, 'hoja'); return f ? '<header class="hoja-cabeza">' + f + '</header>' : ''; }
  function cabezaDetalle(m) { return '<header class="hoja-cabeza detalle"><p class="hoja-detalle-titulo">' + esc(m.titulo) + '</p>' + marcaX() + '</header>'; }
  // Pie de cada hoja (1.5.0), en dos niveles bajo una sola línea fina: arriba, quien atiende con su canal principal y
  // «Coordinar una visita» (en el A4 para imprimir, la portada lleva el QR y las demás hojas sólo el contacto); abajo, la
  // firma de BROX, la nota de una demostración y la paginación mínima («1/2»), sólo si hay más de una hoja. Sin versión
  // ni fecha técnica, que quedan en la trazabilidad.
  function pieHoja(m, n, total, portada) {
    var imprimir = m.op.a4 === 'imprimir', conQR = portada && imprimir && m.accion && window.qrcodegen;
    return '<footer class="hoja-pie' + (conQR ? ' con-qr' : '') + (m.identidad || m.op.preparacion ? '' : ' sin-contacto') + '">' + (conQR ? pieQR(m) : contactoPie(m, true, !imprimir))
      + '<div class="pie-marca">' + firmaBrox() + (m.notaDemo ? '<span class="pie-demo">' + esc(m.notaDemo) + '</span>' : '') + (total > 1 ? '<span class="pie-pagina">' + n + '/' + total + '</span>' : '') + '</div></footer>';
  }
  // Galería de la portada A4: la foto de portada grande y, a su lado, una de cada otro ambiente (hasta dos), cada una
  // con su propio rótulo (1.5.0). Sin fotografías, sólo la vista de preparación dibuja los espacios reservados.
  function galeriaHoja(m) {
    var p = m.portada;
    if (p) {
      var lado = fotosLaterales(m, 2);
      return '<div class="hoja-galeria-portada' + (lado.length ? '' : ' sola') + '"><figure class="hoja-foto">' + imgFoto(p) + (p.origen === 'ESCENA_ILUSTRATIVA' ? '<figcaption><i aria-hidden="true"></i>' + esc(ESCENA) + '</figcaption>' : '') + '</figure>'
        + (lado.length ? '<div class="hoja-fotos-lado">' + lado.map(function (f, k) {
          var todas = k === lado.length - 1 && m.enlaceFicha && !m.op.galeria && m.fotos.length > lado.length + 1;
          return '<figure class="hoja-foto-lado">' + imgFoto(f) + '<figcaption>' + esc(f.rotulo) + '</figcaption>' + (todas ? '<a class="hoja-ver-fotos" href="' + esc(m.enlaceFicha) + '">' + ico('fotos') + '<span>Ver las ' + m.fotos.length + ' fotos</span></a>' : '') + '</figure>';
        }).join('') + '</div>' : '') + '</div>';
    }
    if (!m.reservas.length) return '';
    var r = m.reservas;
    return '<div class="hoja-galeria-portada reservas">' + reservaEspacio('Foto de portada', 'hoja-foto', r[0])
      + (r.length > 1 ? '<div class="hoja-fotos-lado">' + r.slice(1, 3).map(function (nombre) { return reservaEspacio(nombre, 'hoja-foto-lado'); }).join('') + '</div>' : '') + '</div>';
  }
  // El mapa en la hoja: la variante compacta, con el inmueble marcado encima, y la atribución breve debajo.
  function mapaHoja(m, clase, sinEnlace) {
    var v = m.entorno.mapa.variantes.compacto;
    return '<figure class="' + clase + '"><span class="mapa-lienzo"><img src="' + esc(v.claro) + '" alt="' + esc(altMapa(m)) + '" width="' + v.ancho + '" height="' + v.alto + '"><span class="mapa-capa">' + marcaMapa(m, v) + '</span></span>' + atribucionMapa(m, true, sinEnlace) + '</figure>';
  }
  // 01 en la hoja (1.5.0): el mapa con el inmueble y, a su lado, la dirección con su enlace y los lugares públicos con su
  // tiempo. Si el mapa ya es la imagen de la portada (sin fotografías), el 01 no lo repite: dirección y lugares.
  function zonaHoja(m, mapaEnPortada) {
    var lugares = m.entorno ? leyendaMarcado(m, 'hoja-leyenda') + (notaTiempos(m) ? '<p class="hoja-nota">' + esc(notaTiempos(m)) + '</p>' : '') : '';
    if (!m.entorno) return m.op.preparacion ? '<div class="hoja-zona">' + reservaEspacio('Mapa del entorno', 'hoja-mapa', 'Con la ubicación autorizada', 'pin') + '<div class="hoja-zona-lado">' + tablero([piezaUbicacion(m, true)]) + '</div></div>' : tablero([piezaUbicacion(m, true)]);
    var enPortada = m.cifras.some(function (c) { return c.icono === 'via'; }) ? ['via'] : [];
    if (m.destacados.some(function (d) { return d.icono === 'esquina'; })) enPortada.push('esquina');
    if (mapaEnPortada) return '<div class="hoja-zona solo-lado">' + tablero([piezaUbicacion(m, true, enPortada)]) + '<div class="hoja-zona-lugares">' + lugares + '</div></div>';
    return '<div class="hoja-zona">' + mapaHoja(m, 'hoja-mapa') + '<div class="hoja-zona-lado">' + tablero([piezaUbicacion(m, true)]) + lugares + '</div></div>';
  }
  // Bloques del detalle A4 (§7.5; 1.4.0): cada capítulo va a todo el ancho y cada fila de su tablero es un bloque que
  // no se parte. El primer bloque de un capítulo lleva su rótulo; uno que abre hoja sin ser el primero, el rótulo con
  // «continúa». La lectura es una sola columna: un capítulo no salta de columna (revisión del 18 de octubre).
  function bloquesA4(m, caps, op, mapaEnPortada) {
    var b = [];
    var agregar = function (c, filas, soloPrecio) { filas.forEach(function (html, k) { b.push({ id: c.id + '-' + k, cap: c.id, no: c.no, nombre: c.nombre, primero: k === 0, html: html, soloPrecio: !!soloPrecio && k === 0 }); }); };
    if (op.descripcionEnDetalle && m.descripcion) b.push({ id: 'descripcion', cap: 'descripcion', primero: true, sinRotulo: true, html: descripcionMarcado(m, 'hoja-lead hoja-lead-detalle') });
    caps.forEach(function (c) {
      if (c.id === 'zona') agregar(c, [zonaHoja(m, mapaEnPortada)]);
      // 1.5.0: si la primera fila del 02 es sólo el precio y hay grupos, esa fila no va en la portada, donde el precio ya
      // está al lado (ajustarA4): el 02 empieza en la hoja siguiente, con su precio, y nunca queda un rótulo sin contenido.
      // 1.5.0 (revisión del 20 de octubre: el 02 repetía lo que el bloque del precio ya dice): en el A4, el bloque del precio
      // de la portada es la versión completa de lo que incluye, se paga y se exige; el 02 sólo agrega lo que no está ahí y,
      // si no queda nada, no se dibuja. Si la portada tuvo que ceder esas filas, el 02 va completo.
      else if (c.id === 'precio') {
        var completa = !op.sinExtrasEnPortada && !op.sinCondicionesEnPortada;
        var gruposA4 = completa ? m.gruposPrecio.map(function (g) { return { id: g.id, titulo: g.titulo, filas: g.filas.filter(function (f) { return m.enCaja.indexOf(f.ref) < 0; }) }; }).filter(function (g) { return g.filas.length; }) : m.gruposPrecio;
        if (!gruposA4.length && completa) return;
        var pp = [piezaPrecio(m)].concat(gruposA4.map(piezaGrupoPrecio)), rangos = filasTablero(pesos(pp), 5);
        agregar(c, filasHoja(pp), gruposA4.length > 0 && rangos.length > 1 && rangos[0][1] - rangos[0][0] === 1);
      }
      else if (c.id === 'areas') { var pz = m.gruposCarac.map(piezaGrupo); if (m.uso) pz.push(piezaUso(m.uso)); agregar(c, filasHoja(pz)); }
      else if (c.id === 'documentacion') agregar(c, filasHoja(piezasDocs(m)));
    });
    return b;
  }
  // A4: hoja 1, portada (firma, título y dirección; la galería o, sin fotografías, el mapa con el inmueble; las cifras a la
  // izquierda y el precio con sus condiciones a la derecha (1.12.0: sin fotos, el precio y las cifras al lado del mapa);
  // los destacados y la descripción a todo el ancho y el pie con el contacto y su acción); después, las hojas de detalle, con el título breve arriba y el contacto breve al pie, porque una hoja
  // se comparte sola; al final, la galería opcional. Sin fotografías, el detalle empieza en la portada y una ficha corta
  // cabe en una sola hoja (1.5.0: el número de hojas responde al contenido).
  function renderA4(m, plan) {
    var op = (plan && plan.op) || {}, mapaEnPortada = mapaHeroe(m);
    var caps = capitulos(m).filter(function (c) { return !(op.sinPrecioEnDetalle && c.id === 'precio'); });
    var bloques = bloquesA4(m, caps, op, mapaEnPortada), porId = {};
    bloques.forEach(function (x) { porId[x.id] = x; });
    // Sin plan (primer dibujo, antes de medir) todos los bloques van en una hoja de detalle; ajustarA4 mide y reparte.
    if (!plan) plan = { op: op, portada: [], detalle: [bloques.map(function (x) { return x.id; })] };
    var bloqueHtml = function (id, i) {
      var x = porId[id], etiqueta = x.sinRotulo ? '' : x.primero ? rotulo(x.no, x.nombre) : (i === 0 ? rotulo(x.no, x.nombre, true) : '');
      return '<section class="bloque-a4" data-bloque="' + x.id + '" data-cap="' + x.cap + '"' + (x.primero || x.sinRotulo ? '' : ' data-continua=""') + (x.soloPrecio ? ' data-solo-precio=""' : '') + '>' + etiqueta + x.html + '</section>';
    };
    var flujo = function (ids) { return '<div class="hoja-flujo"><div class="hoja-col">' + ids.map(bloqueHtml).join('') + '</div></div>'; };
    var galeria = paginasGaleria(m), total = 1 + plan.detalle.length + galeria.length, h = [];
    var flujoEnPortada = !m.portada && !m.reservas.length;
    var antetitulo = '<p class="antetitulo">' + (m.antetitulo ? '<i aria-hidden="true"></i>' + esc(m.antetitulo) : '') + '</p>';
    var derecha = precioMarcado(m, 'hoja-precio', op.sinExtrasEnPortada) + (op.sinCondicionesEnPortada ? '' : condicionesMarcado(m, 'hoja-condiciones'));
    var descripcion = op.descripcionEnDetalle ? '' : descripcionMarcado(m, 'hoja-lead'), visual, resumen;
    if (mapaEnPortada) {
      // Sin fotografías (1.5.0): el mapa con el inmueble y, a su lado, el precio con lo que decide; debajo, las cifras a todo
      // el ancho, los destacados y la descripción.
      // 1.12.0 (revisión del titular: el precio «ocupa espacio de más»): al lado del mapa, el precio con sus condiciones
      // y, debajo, las cifras de dos en dos, de modo que la columna llena el alto del mapa sin estirar el precio; debajo
      // del mapa, los destacados y la descripción.
      var bajoMapa = destacadosMarcado(m, 'hoja-destacados') + descripcion;
      visual = '<div class="hoja-mapa-fila">' + mapaHoja(m, 'hoja-mapa-portada') + '<div class="hoja-resumen-b">' + derecha + cifrasMarcado(m, 'hoja-cifras', true, 2) + '</div></div>';
      resumen = bajoMapa ? '<div class="hoja-resumen solo-cifras">' + bajoMapa + '</div>' : '';
    } else {
      // 1.12.0 (revisión del titular: «reorganizar los elementos para que se vea mejor»): las cifras a la izquierda y el
      // precio con sus condiciones a la derecha, de alto parejo; los destacados, en su fila, a todo el ancho.
      var izquierda = cifrasMarcado(m, 'hoja-cifras', true, 3);
      visual = galeriaHoja(m);
      resumen = '<div class="hoja-resumen' + (izquierda ? '' : ' solo-precio') + '">' + (izquierda ? '<div class="hoja-resumen-a">' + izquierda + '</div>' : '') + '<div class="hoja-resumen-b">' + derecha + '</div>' + destacadosMarcado(m, 'hoja-destacados') + descripcion + '</div>';
    }
    var clasePortada = m.portada ? '' : m.reservas.length ? ' con-reservas' : mapaEnPortada ? ' con-mapa' : ' sin-foto', compacta = op.compacto ? ' compacta' : '';
    h.push('<article class="hoja portada-a4' + clasePortada + compacta + (m.op.a4 === 'imprimir' ? ' para-imprimir' : '') + '" data-pagina="1">' + cabezaPortada(m)
      + '<div class="hoja-titulo">' + antetitulo + marcaX() + '</div><h1>' + esc(m.titulo) + '</h1>' + (m.lineaUbicacion ? '<p class="zona-linea">' + ico('pin') + '<span>' + esc(m.lineaUbicacion) + '</span></p>' : '')
      + visual + resumen + (flujoEnPortada ? flujo(plan.portada) : '') + pieHoja(m, 1, total, true) + '</article>');
    plan.detalle.forEach(function (ids, i) {
      var n = i + 2;
      h.push('<article class="hoja hoja-detalle' + compacta + '" data-pagina="' + n + '">' + cabezaDetalle(m) + flujo(ids) + pieHoja(m, n, total, false) + '</article>');
    });
    galeria.forEach(function (items, i) {
      var n = i + 2 + plan.detalle.length;
      var nombres = []; items.forEach(function (it) { if (nombres.indexOf(it.grupo) < 0) nombres.push(it.grupo); });
      // 1.5.0: sin el párrafo que explicaba las fotos; sólo, si las hay, que algunas conservan las marcas de su origen.
      h.push('<article class="hoja hoja-galeria" data-pagina="' + n + '">' + cabezaDetalle(m) + '<div class="hoja-galeria-cabeza">' + rotulo((caps.filter(function (c) { return c.id === 'recorrido'; })[0] || {}).no, 'Recorrido visual') + '<h2>' + esc(nombres.join(' · ')) + '</h2></div><div class="rejilla' + (items.length === 1 ? ' una' : items.length === 2 ? ' dos' : '') + '">'
        + items.map(function (it) { var f = it.foto; return '<figure><span class="foto-celda"><span class="foto-recorte" data-ar="' + ((f.ancho - (f.encuadre ? f.encuadre.izquierda + f.encuadre.derecha : 0)) / f.alto).toFixed(4) + '">' + imgFoto(f) + '</span></span><figcaption><b>' + esc(it.grupo) + '</b>' + (f.rotulo !== it.grupo || f.origen === 'ESCENA_ILUSTRATIVA' ? '<span>' + (f.rotulo !== it.grupo ? esc(f.rotulo) : '') + (f.origen === 'ESCENA_ILUSTRATIVA' ? (f.rotulo !== it.grupo ? ' · ' : '') + esc(ESCENA) : '') + '</span>' : '') + '</figcaption></figure>'; }).join('')
        + '</div>' + (items.some(function (it) { return it.foto.marcasDeOrigen; }) ? '<p class="hoja-galeria-nota">Las fotografías conservan las marcas de su origen.</p>' : '')
        + (i === galeria.length - 1 && fotosGaleria(m).length + 1 < m.fotos.length ? '<a class="hoja-ver-todas" href="' + esc(m.enlaceFicha) + '">' + ico('fotos') + '<span>Ver las ' + m.fotos.length + ' fotos</span>' + ico('ampliar') + '</a>' : '') + pieHoja(m, n, total, false) + '</article>');
    });
    return { html: '<div class="a4">' + h.join('') + '</div>', paginas: total, plan: plan };
  }
  // ------------------------------------------------------------------ imagen vertical (1.5.0)
  // Resumen para compartir por WhatsApp (revisión entregada por el titular el 20 de octubre): una composición propia de
  // 390 px de ancho, no un A4 reducido, con letra de 16 px para leer y 14 px como mínimo. Orden: la firma compacta, qué es
  // y dónde, la foto principal (o, sin fotos, el mapa con el inmueble), el precio con lo que decide, las cifras clave, lo
  // que distingue al inmueble, el contacto y la marca BROX. Una imagen no conserva enlaces: «Coordinar una visita» se lee
  // como invitación, con el canal escrito, y el enlace utilizable va en el texto que la acompaña (textoAcompanante).
  function renderVertical(m, op) {
    op = op || {};
    var i = m.identidad, p = m.portada, a = m.accion;
    var f = firma(m, 'vertical'), cabeza = f ? '<header class="v-cabeza">' + f + '</header>' : '';
    var titulo = '<div class="v-titulo"><div class="v-operacion">' + (m.antetitulo ? '<p class="antetitulo"><i aria-hidden="true"></i>' + esc(m.antetitulo) + '</p>' : '<span></span>') + marcaX() + '</div><h1>' + esc(m.titulo) + '</h1>' + (m.lineaUbicacion ? '<p class="zona-linea">' + ico('pin') + '<span>' + esc(m.lineaUbicacion) + '</span></p>' : '') + '</div>';
    var visual = op.sinVisual ? '' : p ? '<figure class="v-foto">' + imgFoto(p) + (p.origen === 'ESCENA_ILUSTRATIVA' ? '<figcaption><i aria-hidden="true"></i>' + esc(ESCENA) + '</figcaption>' : '') + '</figure>'
      : m.reservas.length ? reservaEspacio('Foto de portada', 'v-foto', m.reservas[0]) : mapaHeroe(m) ? mapaHoja(m, 'v-mapa', true) : '';
    // Lo esencial: el precio con hasta tres de lo que incluye, se paga además o se exige, hasta dos condiciones (también las desfavorables, que
    // cambian la decisión), hasta cuatro cifras y dos destacados. El A4 conserva todo lo demás.
    var resumido = Object.assign({}, m, { extrasPrecio: m.extrasPrecio.slice(0, 3), condicionesClave: m.condicionesClave.filter(function (c) { return !c.signo; }).concat(m.condicionesClave.filter(function (c) { return c.signo; })).slice(0, 2), cifras: m.cifras.slice(0, op.cifras || 4), destacados: m.destacados.slice(0, 2) });
    // El contacto: la invitación con el canal escrito; quien atiende ya está en la firma de arriba y no se repite.
    // 1.11.0: el botón con su flecha y, debajo, el canal con su icono (WhatsApp, con su logotipo y sus colores, una vez).
    var contacto = i && a ? '<footer class="v-contacto"><p class="v-accion"><b>' + esc(a.texto) + '</b>' + ico('flecha') + '</p><p class="v-canal" data-tipo="' + a.contacto.tipo + '">' + icoContacto(a.contacto.tipo) + '<span>' + (a.contacto.tipo === 'WHATSAPP' ? '<span class="contacto-tipo">WhatsApp</span> ' + esc(a.contacto.valor) : esc(a.contacto.texto)) + '</span></p></footer>' : '';
    return '<div class="vertical"><article class="vertical-pieza' + (op.fotoBaja ? ' foto-baja' : '') + '">' + cabeza + titulo + visual
      + '<div class="v-resumen">' + precioMarcado(resumido, 'v-precio') + condicionesMarcado(resumido, 'v-condiciones') + cifrasMarcado(resumido, 'v-cifras', true, 0) + (op.sinDestacados ? '' : destacadosMarcado(resumido, 'v-destacados')) + '</div>'
      + contacto + '<p class="v-brox">' + firmaBrox() + (m.notaDemo ? '<span class="pie-demo">' + esc(m.notaDemo) + '</span>' : '') + '</p></article></div>';
  }
  // ------------------------------------------------------------------ vista para teléfono (1.7.0)
  // Para mostrarse dentro de KAIROS, la aplicación de teléfono de BROX (instrucción del titular del 20 de octubre: «otra
  // vista para que se muestre en KAIROS, diseñada para una interfaz de teléfono»). Sigue las reglas de las tarjetas de
  // KAIROS (catálogo operativo, 11.9): se agrupa por lo que se decide, un toque revela lo demás y la acción va junto al
  // inmueble. Arriba, lo decisivo con la composición de la imagen para compartir: las fotos en una tira que se desliza, qué
  // es y dónde, el precio con lo que se paga además, las condiciones, las cifras y lo que distingue al inmueble; debajo,
  // cada capítulo se abre con un toque; al pie, fija, la acción con quién atiende. La pieza no dice dónde se muestra.
  var TIRA_MAX = 8;
  function mapaKairos(m) {
    var v = m.entorno.mapa.variantes.compacto;
    return '<figure class="k-mapa"><span class="mapa-lienzo"><img class="mapa-claro" src="' + esc(v.claro) + '" alt="' + esc(altMapa(m)) + '" width="' + v.ancho + '" height="' + v.alto + '"><img class="mapa-oscuro" src="' + esc(v.oscuro) + '" alt="" width="' + v.ancho + '" height="' + v.alto + '"><span class="mapa-capa">' + marcaMapa(m, v) + '</span></span>' + atribucionMapa(m, false) + '</figure>';
  }
  function seccionKairos(nombre, pista, cuerpo) {
    return '<details class="k-seccion"><summary><span class="k-seccion-nombre">' + esc(nombre) + '</span>' + (pista ? '<span class="k-seccion-pista">' + esc(pista) + '</span>' : '') + ico('flecha') + '</summary><div class="k-seccion-cuerpo">' + cuerpo + '</div></details>';
  }
  function renderKairos(m) {
    var i = m.identidad, a = m.accion, f = firma(m, 'kairos'), n = m.fotos.length;
    var cabeza = f ? '<header class="k-cabeza">' + f + '</header>' : '';
    // Las fotos: las primeras en la tira; la última abre el visor con todas (muchas fotos no se cargan de una vez).
    var tira = m.fotos.slice(0, TIRA_MAX), visual = '';
    if (n) visual = '<div class="k-fotos"><ol class="k-tira" aria-label="Fotografías">' + tira.map(function (x, k) {
      var mas = k === tira.length - 1 && n > tira.length;
      return '<li><button type="button" class="k-foto' + (mas ? ' k-foto-mas' : '') + '" data-k-foto="' + esc(x.id) + '" aria-label="' + esc(mas ? 'Ver las ' + n + ' fotos' : 'Ampliar ' + x.rotulo) + '">' + imgFoto(x) + (mas ? '<span class="k-foto-todas">' + ico('fotos') + '<span>Ver las ' + n + ' fotos</span></span>' : '<span class="k-foto-rotulo">' + esc(x.rotulo) + '</span>') + '</button></li>';
    }).join('') + '</ol>' + (n > 1 ? '<p class="k-fotos-cuenta">' + n + ' fotos</p>' : '') + (m.hayEscenas ? '<p class="k-escena"><i aria-hidden="true"></i>' + esc(ESCENA) + '</p>' : '') + '</div>';
    else if (m.reservas.length) visual = reservasKairos(m);
    else if (mapaHeroe(m)) visual = '<div class="k-visual-mapa">' + mapaKairos(m) + '</div>';
    var titulo = '<div class="v-titulo"><div class="v-operacion">' + (m.antetitulo ? '<p class="antetitulo"><i aria-hidden="true"></i>' + esc(m.antetitulo) + '</p>' : '<span></span>') + marcaX() + '</div><h1>' + esc(m.titulo) + '</h1>' + (m.lineaUbicacion ? '<p class="zona-linea">' + ico('pin') + '<span>' + esc(m.lineaUbicacion) + '</span></p>' : '') + '</div>';
    var resumen = '<section class="k-resumen">' + titulo + precioMarcado(m, 'v-precio') + condicionesMarcado(m, 'v-condiciones') + cifrasMarcado(m, 'v-cifras', true, 0) + destacadosMarcado(m, 'v-destacados') + descripcionMarcado(m, 'k-lead') + '</section>';
    // Cada capítulo, cerrado, con su nombre y lo que resume; un toque lo abre.
    var secciones = [];
    var cuerpoZona = (m.entorno && !mapaHeroe(m) ? mapaKairos(m) : !m.entorno && m.op.preparacion ? reservaEspacio('Mapa del entorno', 'k-mapa', 'Con la ubicación autorizada', 'pin') : '') + tablero([piezaUbicacion(m, false)]) + (m.entorno ? leyendaMarcado(m, 'leyenda') + (notaTiempos(m) ? '<p class="mapa-tiempos">' + esc(notaTiempos(m)) + '</p>' : '') : '');
    secciones.push(seccionKairos('Ubicación', m.ubicacion.zona || m.lineaUbicacion, cuerpoZona));
    var precioResto = m.gruposPrecio.map(function (g) { return { id: g.id, titulo: g.titulo, filas: g.filas.filter(function (f) { return m.enCaja.indexOf(f.ref) < 0; }) }; }).filter(function (g) { return g.filas.length; });
    if (precioResto.length) secciones.push(seccionKairos('Precio y condiciones', '', tablero(precioResto.map(piezaGrupoPrecio))));
    // El detalle completo de cada grupo, como en el A4 (el detalle conserva la información; 1.4.0).
    var caracResto = m.gruposCarac.map(piezaGrupo).concat(m.uso ? [piezaUso(m.uso)] : []);
    if (caracResto.length) secciones.push(seccionKairos('Áreas y características', '', tablero(caracResto)));
    if (m.documentos.length) secciones.push(seccionKairos('Documentación', m.documentos.length === 1 ? '1 dato con respaldo' : m.documentos.length + ' datos con respaldo', tablero(piezasDocs(m))));
    var completa = m.enlaceFicha ? '<a class="k-completa" href="' + esc(m.enlaceFicha) + '" target="_blank" rel="noopener noreferrer">Ver la ficha completa' + ico('ampliar') + '</a>' : '';
    // Al pie, fija: quien atiende con el canal de la acción (1.11.0) y la acción. Sin identidad, sólo la vista de preparación
    // reserva su lugar.
    var contacto = i ? '<footer class="k-contacto"><p class="k-quien"><b>' + esc(i.contactoNombre) + '</b>' + canalKairos(a) + '</p>' + (a ? accionMarcado(a, false) : '') + '</footer>'
      : m.op.preparacion ? '<footer class="k-contacto">' + quienReservado() + accionReservada() + '</footer>' : '';
    return '<div class="kairos"><article class="k-ficha">' + cabeza + visual + resumen + (secciones.length ? '<div class="k-secciones">' + secciones.join('') + '</div>' : '') + completa
      + '<p class="k-brox v-brox">' + firmaBrox() + (m.notaDemo ? '<span class="pie-demo">' + esc(m.notaDemo) + '</span>' : '') + '</p>' + contacto + '</article></div>';
  }
  // Ajuste de la imagen vertical (1.5.0): con las fuentes cargadas, si pasa de 900 px de alto (390 × 900, cerca de la
  // proporción de un teléfono) se baja primero la foto, después se quitan los destacados y, al final, la cuarta cifra. El
  // precio, las condiciones y el contacto no se quitan y la letra no se reduce: si aún no cabe, la imagen es más alta.
  var ALTO_VERTICAL = 900;
  function ajustarVertical(m) {
    var raiz = document.querySelector('.vertical'); if (!raiz) return null;
    document.body.classList.add('midiendo-vertical');
    try {
      var pasos = [{}, { fotoBaja: true }, { fotoBaja: true, sinDestacados: true }, { fotoBaja: true, sinDestacados: true, cifras: 3 }], op = pasos[0];
      for (var k = 0; k < pasos.length; k++) {
        op = pasos[k];
        var nuevo = renderVertical(m, op); raiz.outerHTML = nuevo; raiz = document.querySelector('.vertical'); aplicarCssom(raiz);
        if (raiz.querySelector('.vertical-pieza').getBoundingClientRect().height <= ALTO_VERTICAL) break;
      }
      var pieza = raiz.querySelector('.vertical-pieza');
      return { ancho: Math.round(pieza.getBoundingClientRect().width), alto: Math.ceil(pieza.getBoundingClientRect().height), ajuste: op };
    } finally { document.body.classList.remove('midiendo-vertical'); }
  }
  // Texto que acompaña a la imagen en el mensaje (1.5.0): qué es, dónde, el precio y las cifras principales, y el
  // contacto con su enlace resuelto (el mismo destino que el botón y el QR). WhatsApp enlaza solo un número o un correo
  // escritos; el chat directo va como enlace wa.me.
  function textoAcompanante(m) {
    var lineas = [(m.antetitulo ? m.antetitulo + ': ' : '') + m.titulo];
    if (m.lineaUbicacion) lineas.push(m.lineaUbicacion);
    var cifras = m.cifras.filter(function (c) { return !c.frase && !c.texto && (c.area || !c.unidad); }).slice(0, 3).map(function (c) { return c.area ? c.valor + ' ' + c.unidad : c.valor + ' ' + (c.rotulo || '').toLowerCase(); });
    lineas.push([m.precio && !m.precio.pendiente ? m.precio.texto : m.rotuloPrecio + ': consultar'].concat(cifras).join(' · '));
    var a = m.accion, i = m.identidad;
    if (a) {
      var con = i.contactoNombre + (i.contactoEmpresa ? ' (' + i.contactoEmpresa + ')' : '');
      if (a.contacto.tipo === 'WHATSAPP') lineas.push('Coordina una visita con ' + con + ' por WhatsApp: ' + a.href);
      else if (a.contacto.tipo === 'TELEFONO') lineas.push('Coordina una visita con ' + con + ' al ' + a.contacto.valor + '.');
      else if (a.contacto.tipo === 'CORREO') lineas.push('Coordina una visita con ' + con + ' por correo: ' + a.contacto.valor);
      else lineas.push('Coordina una visita con ' + con + ': ' + a.href);
    }
    if (m.notaDemo) lineas.push(m.notaDemo);
    return lineas.join('\n');
  }
  function exportarVertical(base) {
    var v = document.querySelector('.vertical'); if (!v || !actual) return null;
    var titulo = (actual.modelo.estadoPieza === 'VISTA_PREVIA' ? 'Vista previa · ' : '') + actual.modelo.titulo + ' · BROX';
    return '<!doctype html>\n<html lang="es-PE">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=390,initial-scale=1">\n'
      + '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src \'self\' data: blob:; style-src \'self\' \'unsafe-inline\'; font-src \'self\'; base-uri \'self\'; form-action \'none\'">\n'
      + metadatosTraza(actual.modelo, 'vertical') + (base ? '<base href="' + esc(base) + '">\n' : '') + '<title>' + esc(titulo) + '</title>\n<link rel="stylesheet" href="ficha.css">\n</head>\n<body data-vista="vertical" data-tema="' + esc(actual.opciones.tema) + '" class="estatica' + (datosTema(actual.opciones.tema).pantallaOscura ? ' tema-oscuro' : '') + (datosTema(actual.opciones.tema).banda ? ' con-banda' : '') + '">\n'
      + v.outerHTML + '\n</body>\n</html>\n';
  }
  // Trazabilidad de la entrega (1.5.0): la pieza, su versión, la plantilla, el contrato, la fecha de corte, el estado y el
  // formato viajan en metadatos del documento exportado, fuera del pie comercial (revisión del 20 de octubre).
  function metadatosTraza(m, formato) {
    var t = m.traza;
    return '<meta name="brox-pieza" content="' + esc(t.pieza) + '">\n<meta name="brox-version-contenido" content="' + esc(t.version) + '">\n<meta name="brox-plantilla" content="' + esc(t.plantilla) + '">\n<meta name="brox-contrato" content="' + esc(t.contrato) + '">\n<meta name="brox-fecha-corte" content="' + esc(t.fechaCorte) + '">\n<meta name="brox-estado-pieza" content="' + esc(m.estadoPieza) + '">\n<meta name="brox-formato" content="' + esc(formato) + '">\n';
  }

  // Una hoja desborda si el pie queda fuera o si su flujo de bloques no alcanza (el flujo recorta, no estira).
  function desbordeHoja(hoja) {
    var pie = hoja.querySelector('.hoja-pie'); if (!pie) return 0;
    var caja = hoja.getBoundingClientRect(), pad = parseFloat(getComputedStyle(hoja).paddingBottom) || 0, flujo = 0;
    hoja.querySelectorAll('.hoja-col').forEach(function (f) { flujo = Math.max(flujo, f.scrollHeight - f.clientHeight); });
    return Math.max(Math.round(pie.getBoundingClientRect().bottom - (caja.bottom - pad)), hoja.scrollHeight - hoja.clientHeight > 1 ? 1 : 0, flujo > 1 ? Math.round(flujo) : 0);
  }
  // Reparto (§7.5; 1.4.0): con las fuentes cargadas, primero la portada: si no alcanza, la descripción pasa al detalle y,
  // si aún falta, la portada deja al 02 las condiciones y los costos que el 02 ya muestra completos (nada se pierde). Después
  // se mide una vez el alto de cada bloque y el espacio de cada hoja, y los bloques se reparten en orden, hoja por hoja,
  // sin partir ninguno. El HTML exportado conserva el resultado, sin scripts.
  function ajustarA4(m) {
    var a4 = document.querySelector('.a4'), resultado = { paginas: actual.paginasA4, desbordes: [] };
    if (!a4) return resultado;
    document.body.classList.add('midiendo-a4');
    try {
      var op = {}, rehacer = function (plan) { var r = renderA4(m, plan); a4.outerHTML = r.html; a4 = document.querySelector('.a4'); aplicarCssom(a4); return r; };
      var cabeDetalle = function () { var d = a4.querySelector('.hoja-detalle .hoja-col'); return d ? d.clientHeight : 0; };
      ['descripcionEnDetalle', 'sinCondicionesEnPortada', 'sinExtrasEnPortada'].forEach(function (paso) {
        var portada = a4.querySelector('.portada-a4');
        if (!portada || desbordeHoja(portada) <= 0) return;
        var util = paso === 'descripcionEnDetalle' ? !!m.descripcion : paso === 'sinCondicionesEnPortada' ? m.condicionesClave.length > 0 : m.extrasPrecio.length > 0;
        if (!util) return;
        op[paso] = true; rehacer({ op: op, portada: [], detalle: [bloquesA4(m, capitulos(m), op).map(function (x) { return x.id; })] });
      });
      // Mide los bloques con todos en una hoja de detalle y los reparte en orden, hoja por hoja.
      var repartir = function () {
        rehacer({ op: op, portada: [], detalle: [bloquesA4(m, capitulos(m), op).map(function (x) { return x.id; })] });
        var detalle = a4.querySelector('.hoja-detalle .hoja-col'), portadaCol = a4.querySelector('.portada-a4 .hoja-col');
        var bloques = detalle ? Array.prototype.map.call(detalle.children, function (b) { return { id: b.getAttribute('data-bloque'), h: b.getBoundingClientRect().height, continua: b.hasAttribute('data-continua'), soloPrecio: b.hasAttribute('data-solo-precio') }; }) : [];
        var hueco = detalle ? parseFloat(getComputedStyle(detalle).rowGap) || 0 : 0;
        // Alto del rótulo «continúa» que lleva un bloque de continuación cuando abre hoja.
        var altoRotulo = 0;
        if (detalle) { var prueba = document.createElement('section'); prueba.className = 'bloque-a4'; prueba.innerHTML = rotulo('03', 'Áreas y características', true) + '<div></div>'; detalle.appendChild(prueba); altoRotulo = prueba.getBoundingClientRect().height; detalle.removeChild(prueba); }
        var dispDetalle = cabeDetalle(), dispPortada = portadaCol ? portadaCol.clientHeight : 0;
        // Hojas en orden: la portada sin fotografía (si la hay) y, después, las de detalle que hagan falta.
        var plan = { op: JSON.parse(JSON.stringify(op)), portada: [], detalle: [] }, hojas = portadaCol ? [{ ids: plan.portada, disp: dispPortada, portada: true }] : [], k = 0, usado = 0;
        var siguiente = function () { k++; usado = 0; if (k >= hojas.length) { var ids = []; plan.detalle.push(ids); hojas.push({ ids: ids, disp: dispDetalle }); } };
        if (!hojas.length) { k = -1; siguiente(); }
        bloques.forEach(function (b) {
          var hj = hojas[k], alto = usado + (hj.ids.length ? hueco : 0) + b.h + (!hj.ids.length && b.continua ? altoRotulo : 0);
          // En la portada, un bloque que no cabe pasa al detalle aunque sea el primero; en una hoja de detalle vacía, entra.
          // La fila del 02 que sólo repetiría el precio de la portada empieza en la hoja siguiente (1.5.0).
          if (((hj.ids.length || hj.portada) && alto > hj.disp + 0.5) || (hj.portada && b.soloPrecio)) { siguiente(); hj = hojas[k]; alto = b.h + (b.continua ? altoRotulo : 0); }
          hj.ids.push(b.id); usado = alto;
        });
        // 1.5.0: una última hoja que sólo trae la continuación de un capítulo, con poco contenido, recibe el capítulo entero
        // si cabe: la hoja se lee sola y no empieza con un resto («continúa»).
        var alto = function (id) { return (bloques.filter(function (b) { return b.id === id; })[0] || { h: 0 }).h; };
        var ult = hojas[hojas.length - 1], prev = hojas[hojas.length - 2];
        if (ult && prev && ult.ids.length && usado / ult.disp < 0.35) {
          var primero = bloques.filter(function (b) { return b.id === ult.ids[0]; })[0], cap = primero && primero.continua ? ult.ids[0].replace(/-\d+$/, '') : null;
          var mover = cap ? prev.ids.filter(function (id) { return id.replace(/-\d+$/, '') === cap; }) : [];
          var total = mover.concat(ult.ids).reduce(function (t, id, i) { return t + alto(id) + (i ? hueco : 0); }, 0);
          if (mover.length && mover.length < prev.ids.length && total <= ult.disp + 0.5) {
            mover.forEach(function (id) { prev.ids.splice(prev.ids.indexOf(id), 1); });
            Array.prototype.unshift.apply(ult.ids, mover); usado = total;
          }
        }
        // Una hoja de detalle sin bloques (la portada lo absorbió todo) no se dibuja.
        plan.detalle = plan.detalle.filter(function (ids) { return ids.length; });
        return { plan: plan, usoUltima: hojas.length ? usado / hojas[hojas.length - 1].disp : 1 };
      };
      var reparto = repartir();
      // 1.5.0 (revisión del 20 de octubre: los números del mapa necesitan su significado en la misma hoja): con el mapa como
      // imagen de la portada, «Ubicación» y su lista de lugares van en esa hoja. Si no caben, la portada cede las condiciones
      // y después los costos junto al precio, que el capítulo de precio conserva completos.
      if (mapaHeroe(m)) ['sinCondicionesEnPortada', 'sinExtrasEnPortada'].forEach(function (paso) {
        if (reparto.plan.portada.indexOf('zona-0') >= 0 || op[paso]) return;
        if (paso === 'sinCondicionesEnPortada' ? m.condicionesClave.length > 0 : m.extrasPrecio.length > 0) { op[paso] = true; reparto = repartir(); }
      });
      // 1.5.0 (revisión del 20 de octubre: no producir una hoja por obligación): si la última hoja de detalle quedaría
      // casi vacía (menos de un 35 % de su alto), se prueba el detalle compacto, con menos aire y la misma letra; se queda
      // sólo si ahorra una hoja.
      if (reparto.plan.detalle.length && reparto.usoUltima < 0.35) {
        op.compacto = true;
        var compacto = repartir();
        if (compacto.plan.detalle.length < reparto.plan.detalle.length) reparto = compacto;
        else { op.compacto = false; reparto = repartir(); }
      }
      // 1.5.0: si el 02 sólo repite el precio (no hay grupos) y quedó en la portada, junto al precio de la portada, el A4 no
      // lo repite en la misma hoja; los capítulos siguientes suben un número.
      if (!m.gruposPrecio.length && reparto.plan.portada.indexOf('precio-0') >= 0) {
        reparto.plan.op.sinPrecioEnDetalle = true;
        reparto.plan.portada = reparto.plan.portada.filter(function (id) { return id !== 'precio-0'; });
      }
      var r = rehacer(reparto.plan); resultado.paginas = r.paginas;
      a4.querySelectorAll('.hoja').forEach(function (hoja, i) { var d = desbordeHoja(hoja); if (d > 0) resultado.desbordes.push({ hoja: i + 1, px: d }); });
    } finally { document.body.classList.remove('midiendo-a4'); }
    return resultado;
  }

  // ------------------------------------------------------------------ montaje e interacción
  function aplicarCssom(raiz) {
    raiz.querySelectorAll('img[data-enc]').forEach(function (img) {
      var p = img.dataset.enc.split(',').map(Number), util = p[2] - p[0] - p[1];
      if (util > 0) { img.style.setProperty('--enc-w', (p[2] / util * 100).toFixed(4) + '%'); img.style.setProperty('--enc-x', (-p[0] / util * 100).toFixed(4) + '%'); }
    });
    raiz.querySelectorAll('[data-k]').forEach(function (el) { el.style.setProperty('--k', el.dataset.k); });
    raiz.querySelectorAll('[data-lat]').forEach(function (el) { el.style.setProperty('--lat', el.dataset.lat); });
    raiz.querySelectorAll('[data-c]').forEach(function (el) { el.style.setProperty('--c', el.dataset.c); el.style.setProperty('--r', el.dataset.r); });
    raiz.querySelectorAll('[data-ar]').forEach(function (el) { el.style.setProperty('--ar', el.dataset.ar); });
    // El punto autorizado sobre el mapa (1.5.0): posición y radio en porcentaje de la variante.
    raiz.querySelectorAll('[data-x]').forEach(function (el) { el.style.setProperty('--x', el.dataset.x); el.style.setProperty('--y', el.dataset.y); if (el.dataset.r) el.style.setProperty('--radio', el.dataset.r); });
  }

  var visor = { fotos: [], indice: 0, grupo: null };
  function abrirFoto(id, grupo) {
    var m = actual.modelo; visor.fotos = m.fotos; visor.grupo = grupo == null ? null : grupo;
    visor.indice = Math.max(0, m.fotos.findIndex(function (f) { return f.id === id; }));
    pintarVisor();
    var d = $('visor'); d.dataset.modo = 'foto'; if (!d.open) d.showModal();
  }
  function enAlcance() { return visor.grupo == null ? visor.fotos : visor.fotos.filter(function (f) { return f._grupo === visor.grupo; }); }
  function pintarVisor() {
    var m = actual.modelo, f = visor.fotos[visor.indice], g = m.grupos[f._grupo];
    $('visor-antetitulo').textContent = 'Explorar · ' + m.titulo;
    $('visor-titulo').textContent = g.nombre + ' · ' + (visor.indice + 1) + ' / ' + visor.fotos.length;
    $('visor-imagen').src = f.src; $('visor-imagen').alt = f.rotulo; $('visor-capa').innerHTML = '';
    $('visor-rotulo').textContent = f.rotulo;
    $('visor-fuente').textContent = f.origen === 'ESCENA_ILUSTRATIVA' ? ESCENA + '.' : f.marcasDeOrigen ? 'Fotografía original completa, con las marcas de su origen.' : 'Fotografía original completa.';
    $('visor-ambientes').innerHTML = '<button type="button" data-v-grupo="todos" aria-pressed="' + (visor.grupo == null) + '">Toda la propiedad</button>' + m.grupos.map(function (gr, i) { return '<button type="button" data-v-grupo="' + i + '" aria-pressed="' + (visor.grupo === i) + '">' + esc(gr.nombre) + '</button>'; }).join('');
    $('visor-miniaturas').innerHTML = enAlcance().map(function (x) { return '<button type="button" data-v-foto="' + esc(x.id) + '" aria-pressed="' + (x === f) + '" aria-label="Ver ' + esc(x.rotulo) + '"><img src="' + esc(x.src) + '" alt=""></button>'; }).join('');
  }
  function pasoVisor(n) { var alc = enAlcance(), f = visor.fotos[visor.indice], i = alc.indexOf(f), sig = alc[(i + n + alc.length) % alc.length]; visor.indice = visor.fotos.indexOf(sig); pintarVisor(); }
  function abrirMapa() {
    var m = actual.modelo, e = m.entorno; if (!e) return;
    var d = $('visor'), u = m.ubicacion; d.dataset.modo = 'mapa';
    $('visor-antetitulo').textContent = 'Ubicación' + (u.etiqueta ? ' · ' + u.etiqueta.toLowerCase().replace(/^ubicación /, '') : '');
    $('visor-titulo').textContent = u.direccion ? u.direccion + (u.zona ? ', ' + u.zona : '') : u.zona;
    $('visor-imagen').src = document.body.classList.contains('tema-oscuro') ? e.mapa.variantes.ancho.oscuro : e.mapa.variantes.ancho.claro; $('visor-imagen').alt = altMapa(m);
    $('visor-capa').innerHTML = marcaMapa(m, e.mapa.variantes.ancho); aplicarCssom($('visor-capa'));
    $('visor-lugares').innerHTML = e.referencias.map(function (r) { var t = tiempoLugar(m, r); return '<li><span class="leyenda-no">' + esc(r.numero) + '</span>' + esc(r.nombre) + ' · ' + esc(r.categoria) + (t ? ' · ' + esc(t.minutos + ' ' + t.modo) : '') + '</li>'; }).join('');
    $('visor-rotulo').textContent = u.etiqueta || u.direccion || u.zona;
    $('visor-fuente').textContent = e.atribucion + '.' + (notaTiempos(m) ? ' ' + notaTiempos(m) : '');
    if (!d.open) d.showModal();
  }
  // La foto del escenario de portada: la tira marca su ambiente y la miniatura que la muestra queda señalada.
  function cambiarPortada(m, f) {
    var cont = $('portada-foto'); if (!cont) return;
    cont.innerHTML = imgFoto(f); aplicarCssom(cont);
    var img = cont.querySelector('img'); if (!actual.opciones.movimiento || actual.opciones.movimiento !== 'reducido') { img.classList.add('entrando'); requestAnimationFrame(function () { requestAnimationFrame(function () { img.classList.remove('entrando'); }); }); }
    $('portada-ambiente').querySelector('span').textContent = f.rotulo;
    var contador = $('portada-contador'); if (contador) contador.textContent = (m.fotos.indexOf(f) + 1) + ' / ' + m.fotos.length;
    document.querySelectorAll('.tira [data-grupo]').forEach(function (b) { b.setAttribute('aria-pressed', String(Number(b.dataset.grupo) === f._grupo)); });
    document.querySelectorAll('.laterales .lateral[data-foto]').forEach(function (b) { b.setAttribute('aria-current', String(b.dataset.foto === f.id)); });
    actual.portadaActual = f;
  }
  function irA(id) { var s = $('cap-' + id); if (s) s.scrollIntoView({ behavior: actual.opciones.movimiento === 'reducido' ? 'auto' : 'smooth', block: 'start' }); }
  function interacciones(m) {
    var raiz = $('raiz');
    actual.portadaActual = m.portada;
    var porId = {}; m.fotos.forEach(function (f) { porId[f.id] = f; });
    raiz.querySelectorAll('[data-paso]').forEach(function (b) { b.addEventListener('click', function () { var i = m.fotos.indexOf(actual.portadaActual); cambiarPortada(m, m.fotos[(i + Number(b.dataset.paso) + m.fotos.length) % m.fotos.length]); }); });
    raiz.querySelectorAll('.tira [data-grupo]').forEach(function (b) { b.addEventListener('click', function () { cambiarPortada(m, m.grupos[Number(b.dataset.grupo)].fotos[0]); }); });
    raiz.querySelectorAll('.laterales .lateral[data-foto]').forEach(function (b) { b.addEventListener('click', function () { cambiarPortada(m, porId[b.dataset.foto]); }); });
    raiz.querySelectorAll('[data-ir]').forEach(function (a) { a.addEventListener('click', function (e) { e.preventDefault(); irA(a.dataset.ir); }); });
    raiz.querySelectorAll('[data-abrir-actual]').forEach(function (b) { b.addEventListener('click', function () { abrirFoto(actual.portadaActual.id, null); }); });
    raiz.querySelectorAll('[data-abrir-mapa]').forEach(function (b) { b.addEventListener('click', abrirMapa); });
    raiz.querySelectorAll('[data-k-foto]').forEach(function (b) { b.addEventListener('click', function () { abrirFoto(b.dataset.kFoto, null); }); });
    raiz.querySelectorAll('[data-ver-grupo]').forEach(function (b) { b.addEventListener('click', function () { var g = Number(b.dataset.verGrupo); abrirFoto(m.grupos[g].fotos[0].id, g); }); });
    raiz.querySelectorAll('.ambiente [data-foto]').forEach(function (b) { b.addEventListener('click', function () { var f = porId[b.dataset.foto]; abrirFoto(f.id, f._grupo); }); });
    // capítulos visibles: revelado, subnavegación con el capítulo activo y el avance de la lectura
    var enlaces = raiz.querySelectorAll('.subnav a[data-cap]'), progreso = raiz.querySelector('.subnav-progreso');
    var secciones = Array.prototype.slice.call(raiz.querySelectorAll('.capitulo[data-cap]'));
    var marcar = function (cap) {
      enlaces.forEach(function (a) {
        var si = a.dataset.cap === cap, lista = a.parentElement.parentElement;
        a.setAttribute('aria-current', String(si));
        if (si && lista.scrollWidth > lista.clientWidth) lista.scrollTo({ left: Math.max(0, a.parentElement.offsetLeft - 24), behavior: 'auto' });
      });
    };
    // Activo = el último capítulo cuyo inicio ya pasó bajo la subnavegación; al final del documento, el último.
    var fijadoHasta = 0;
    var calcular = function () {
      var total = document.documentElement.scrollHeight - window.innerHeight;
      if (progreso) progreso.style.setProperty('--p', total > 0 ? Math.min(1, Math.max(0, window.scrollY / total)).toFixed(4) : '0');
      if (!secciones.length || performance.now() < fijadoHasta) return;
      var linea = (raiz.querySelector('.subnav') || { offsetHeight: 56 }).offsetHeight + window.innerHeight * 0.15, actualCap = null;
      secciones.forEach(function (s) { if (s.getBoundingClientRect().top <= linea) actualCap = s.dataset.cap; });
      if (window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2) actualCap = secciones[secciones.length - 1].dataset.cap;
      marcar(actualCap);
    };
    var pendiente = false;
    window.addEventListener('scroll', function () { if (pendiente) return; pendiente = true; requestAnimationFrame(function () { pendiente = false; calcular(); }); }, { passive: true });
    // al elegir un capítulo, ése queda activo mientras termina el desplazamiento
    enlaces.forEach(function (a) { a.addEventListener('click', function () { fijadoHasta = performance.now() + 900; marcar(a.dataset.cap); }); });
    calcular();
    if ('IntersectionObserver' in window) {
      var revelar = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('visto'); revelar.unobserve(e.target); } }); }, { threshold: 0.08 });
      secciones.forEach(function (s) { revelar.observe(s); });
    } else secciones.forEach(function (s) { s.classList.add('visto'); });
  }
  document.addEventListener('DOMContentLoaded', function () {
    $('visor-cerrar').addEventListener('click', function () { $('visor').close(); });
    $('visor-anterior').addEventListener('click', function () { pasoVisor(-1); });
    $('visor-siguiente').addEventListener('click', function () { pasoVisor(1); });
    $('visor-ambientes').addEventListener('click', function (e) { var b = e.target.closest('[data-v-grupo]'); if (!b) return; visor.grupo = b.dataset.vGrupo === 'todos' ? null : Number(b.dataset.vGrupo); var alc = enAlcance(); visor.indice = visor.fotos.indexOf(alc[0]); pintarVisor(); });
    $('visor-miniaturas').addEventListener('click', function (e) { var b = e.target.closest('[data-v-foto]'); if (!b) return; visor.indice = visor.fotos.findIndex(function (f) { return f.id === b.dataset.vFoto; }); pintarVisor(); });
    document.addEventListener('keydown', function (e) {
      var d = $('visor'); if (!d.open) return;
      if (d.dataset.modo === 'mapa') { if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); $('visor-lienzo').scrollBy({ left: e.key === 'ArrowLeft' ? -240 : 240 }); } return; }
      if (e.key === 'ArrowLeft') pasoVisor(-1); if (e.key === 'ArrowRight') pasoVisor(1);
    });
  });

  function esperarImagenes(raiz, ms) {
    var imgs = Array.prototype.slice.call(raiz.querySelectorAll('img'));
    var promesas = imgs.map(function (img) { return img.complete ? Promise.resolve() : new Promise(function (r) { img.addEventListener('load', r, { once: true }); img.addEventListener('error', r, { once: true }); }); });
    var tope = new Promise(function (r) { setTimeout(r, ms); });
    return Promise.race([Promise.all(promesas), tope]).then(function () { return document.fonts ? document.fonts.ready : null; });
  }
  function medirAlto() { return Math.ceil(document.documentElement.scrollHeight); }
  function enviar(msg) {
    var destino = anfitrion ? anfitrion.fuente : (window.parent !== window ? window.parent : null);
    if (!destino) return;
    try { destino.postMessage(msg, anfitrion && anfitrion.origen ? anfitrion.origen : '*'); } catch (e) { /* el anfitrión cerró la ventana */ }
  }
  // Opciones (§6). 1.5.0: la vista vertical para compartir, la variante A4 para imprimir (con el QR en la portada) y la
  // vista de preparación, que dibuja los espacios reservados. Son opciones de presentación, no datos del inmueble.
  // Temas de color (1.7.0; 1.8.0; temas.js): un id del catálogo o uno de sus alias («claro», «oscuro» y los id retirados en
  // 1.8.0); lo demás, el predeterminado. Cada tema trae su banda y su botón (ficha.css, bloque de temas).
  function temaDe(t) { var id = TEMAS && TEMAS.alias[t] || t; return TEMAS && TEMAS.temas.some(function (x) { return x.id === id; }) ? id : TEMAS ? TEMAS.predeterminado : 'marfil'; }
  function datosTema(id) { return (TEMAS && TEMAS.temas.filter(function (x) { return x.id === id; })[0]) || { pantallaOscura: false, banda: false }; }
  function normalizarOpciones(o) {
    o = o || {};
    return { vista: ['a4', 'vertical', 'kairos'].indexOf(o.vista) >= 0 ? o.vista : 'digital', a4: o.a4 === 'imprimir' ? 'imprimir' : 'compartir', preparacion: o.preparacion === true, tema: temaDe(o.tema), galeria: o.galeria === true, regional: FMT.configuraciones.indexOf(o.regional) >= 0 ? o.regional : FMT.predeterminada, porConfirmarEnPieza: o.porConfirmarEnPieza === true, movimiento: o.movimiento === 'reducido' ? 'reducido' : 'sistema' };
  }

  function dibujar(datos, opcionesEntrada) {
    var r = VAL.validar(datos);
    if (!r.valido) { $('raiz').innerHTML = '<section class="capitulo"><div class="envoltura">' + rotulo(null, 'Datos no válidos') + '<ul class="condiciones">' + r.errores.map(function (e) { return '<li><span class="c-rotulo">' + esc(e.ruta) + '</span><span class="c-valor">' + esc(e.mensaje) + '</span></li>'; }).join('') + '</ul></div></section>'; enviar({ tipo: 'brox-ficha-error', version: VERSION, errores: r.errores }); return; }
    var op = normalizarOpciones(opcionesEntrada), m = construirModelo(datos, op);
    actual = { datos: datos, opciones: op, modelo: m, avisos: r.avisos };
    document.body.dataset.vista = op.vista;
    document.body.dataset.tema = op.tema;
    document.body.classList.toggle('tema-oscuro', datosTema(op.tema).pantallaOscura);
    document.body.classList.toggle('con-banda', datosTema(op.tema).banda);
    document.body.classList.toggle('mov-reducido', op.movimiento === 'reducido');
    document.documentElement.lang = 'es-PE';
    var a4 = renderA4(m);
    actual.paginasA4 = a4.paginas;
    // 1.7.0: la vista para teléfono se dibuja sólo cuando se pide (la ficha digital, el A4 y la imagen se dibujan siempre:
    // el A4 da el número de hojas y la imagen su medida).
    var raiz = $('raiz'); raiz.innerHTML = renderDigital(m) + a4.html + renderVertical(m) + (op.vista === 'kairos' ? renderKairos(m) : '');
    aplicarCssom(raiz);
    interacciones(m);
    // 1.5.0: el estado no va en la pieza, pero el título del documento (y, con él, el del PDF y el nombre del archivo al
    // guardarlo) dice «Vista previa» mientras la pieza no esté lista para difusión.
    document.title = (m.estadoPieza === 'VISTA_PREVIA' ? 'Vista previa · ' : '') + m.titulo + ' · BROX';
    listaEnviada = false;
    var avisos = r.avisos.slice();
    esperarImagenes(raiz, 9000).then(function () {
      // Con las fuentes cargadas, la portada A4 se ajusta una vez (§7.5) y se esperan sus imágenes nuevas.
      var ajuste = ajustarA4(m);
      actual.paginasA4 = ajuste.paginas;
      ajuste.desbordes.forEach(function (d) { avisos.push({ ruta: 'a4.hoja' + d.hoja, mensaje: 'La hoja ' + d.hoja + ' no alcanza: ' + d.px + ' px de contenido quedan fuera.' }); });
      actual.vertical = ajustarVertical(m);
      return esperarImagenes(raiz, 9000);
    }).then(function () {
      ultimoAlto = medirAlto(); listaEnviada = true;
      // 1.5.0: la respuesta trae además el aviso del estado redactado (para mostrarlo fuera de la pieza), la acción con su
      // destino (el mismo del botón, del QR y del texto que acompaña a la imagen vertical) y la medida de esa imagen.
      var accion = m.accion ? { canal: m.accion.canal, href: m.accion.href } : null;
      enviar({ tipo: 'brox-ficha-lista', version: VERSION, idPieza: datos.pieza.id, versionContenido: datos.pieza.versionContenido, alto: ultimoAlto, faltantes: m.faltantes, estado: m.estado, estadoPieza: m.estadoPieza, motivosVistaPrevia: m.motivos, avisoEstado: m.avisoEstado, accion: accion, hitoPrecio: datos.encargo.precio.hito, naturaleza: datos.pieza.naturaleza, salida: datos.pieza.salida, vista: op.vista, a4: op.a4, paginasA4: actual.paginasA4, vertical: actual.vertical, avisos: avisos });
      window.BROX_FICHA_ESTADO = { lista: true, idPieza: datos.pieza.id, estado: m.estado, estadoPieza: m.estadoPieza, motivosVistaPrevia: m.motivos, avisoEstado: m.avisoEstado, accion: accion, faltantes: m.faltantes, paginasA4: actual.paginasA4, vertical: actual.vertical, alto: ultimoAlto };
    });
  }

  function exportarA4(base) {
    var a4 = document.querySelector('.a4'); if (!a4) return null;
    var titulo = actual ? (actual.modelo.estadoPieza === 'VISTA_PREVIA' ? 'Vista previa · ' : '') + actual.modelo.titulo + ' · BROX' : 'Ficha A4 · BROX';
    return '<!doctype html>\n<html lang="es-PE">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1">\n'
      + '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src \'self\' data: blob:; style-src \'self\' \'unsafe-inline\'; font-src \'self\'; base-uri \'self\'; form-action \'none\'">\n'
      + (actual ? metadatosTraza(actual.modelo, 'a4-' + actual.opciones.a4) : '') + (base ? '<base href="' + esc(base) + '">\n' : '') + '<title>' + esc(titulo) + '</title>\n<link rel="stylesheet" href="ficha.css">\n</head>\n<body data-vista="a4" data-tema="' + esc(actual ? actual.opciones.tema : 'marfil') + '" class="estatica' + (actual && datosTema(actual.opciones.tema).banda ? ' con-banda' : '') + '">\n'
      + a4.outerHTML + '\n</body>\n</html>\n';
  }

  if (window.ResizeObserver) new ResizeObserver(function () {
    if (!listaEnviada) return;
    var alto = medirAlto(); if (Math.abs(alto - ultimoAlto) < 2) return;
    ultimoAlto = alto; enviar({ tipo: 'brox-ficha-alto', version: VERSION, alto: alto });
  }).observe(document.documentElement);

  window.addEventListener('message', function (e) {
    if (e.source !== window.parent) return; // sólo la ventana madre (o la propia, si no hay madre)
    var msg = e.data; if (!msg || typeof msg !== 'object' || typeof msg.tipo !== 'string') return;
    if (['brox-ficha', 'brox-ficha-imprimir', 'brox-ficha-exportar-a4', 'brox-ficha-exportar-vertical'].indexOf(msg.tipo) < 0) return;
    anfitrion = { fuente: e.source, origen: e.origin && e.origin !== 'null' && e.origin !== 'file://' ? e.origin : '*' };
    if (msg.tipo === 'brox-ficha') {
      if (msg.version !== VERSION) { enviar({ tipo: 'brox-ficha-error', version: VERSION, errores: [{ ruta: 'version', mensaje: 'Se esperaba la versión V08' }] }); return; }
      dibujar(msg.datos, msg.opciones);
    } else if (msg.tipo === 'brox-ficha-imprimir') { if (actual) window.print(); }
    else if (msg.tipo === 'brox-ficha-exportar-a4') enviar({ tipo: 'brox-ficha-a4', version: VERSION, a4: actual ? actual.opciones.a4 : null, html: exportarA4(typeof msg.base === 'string' ? msg.base : null) });
    // 1.5.0: la imagen vertical como documento estático (el anfitrión o Core la rasterizan) y el texto que la acompaña.
    else if (msg.tipo === 'brox-ficha-exportar-vertical') enviar({ tipo: 'brox-ficha-vertical', version: VERSION, html: exportarVertical(typeof msg.base === 'string' ? msg.base : null), ancho: actual && actual.vertical ? actual.vertical.ancho : null, alto: actual && actual.vertical ? actual.vertical.alto : null, texto: actual ? textoAcompanante(actual.modelo) : null, enlace: actual && actual.modelo.accion ? actual.modelo.accion.href : null });
  });
  window.BROX_FICHA_V08 = { version: VERSION, exportarA4: exportarA4, exportarVertical: exportarVertical };
  document.addEventListener('DOMContentLoaded', function () { if (window.parent !== window) window.parent.postMessage({ tipo: 'brox-ficha-preparada', version: VERSION, contratos: ['1'] }, '*'); });
})();
