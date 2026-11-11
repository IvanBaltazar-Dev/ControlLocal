// BROX · Ficha V08 · Validador ligero del contrato de datos «brox-ficha-datos» 1.x.
// Funciona en el navegador (global BROX_VALIDAR_FICHA) y en Node (require). Sin dependencias.
// Comprueba forma, tipos del catálogo Core, aplicabilidad por tipo, frontera de privacidad y
// esenciales. No decide permisos: Core es la autoridad; esto evita que una salida mal formada
// llegue al renderer. Ver CONTRATO-DATOS-FICHA-V08.md.
(function (raiz, fabrica) {
  if (typeof module === 'object' && module.exports) module.exports = fabrica(require('./catalogo-core.js'), require('./perfiles.js'));
  else raiz.BROX_VALIDAR_FICHA = fabrica(raiz.BROX_CATALOGO_CORE, raiz.BROX_PERFILES_V08);
})(typeof globalThis !== 'undefined' ? globalThis : this, function (CATALOGO, PERFILES) {
  'use strict';
  var VERSION_MAYOR = 1;
  var NATURALEZAS_PIEZA = ['FUENTE_REAL', 'DEMOSTRACION'];
  var SALIDAS = ['REVISION_PRIVADA', 'DIFUSION_AUTORIZADA'];
  var NATURALEZAS_DATO = ['DECLARADO', 'OBSERVADO', 'INFERIDO'];
  var ORIGENES_FOTO = ['ORIGINAL_FUENTE', 'PROPIA_PROFESIONAL', 'ESCENA_ILUSTRATIVA', 'DERIVADA_IA'];
  var AUTORIZACIONES_FOTO = ['SALIDA_AUTORIZADA', 'REVISION_PRIVADA', 'NO_AUTORIZADA'];
  // 1.6.0 (instrucción del titular del 20 de octubre): todo encargo tiene un responsable, un agente independiente o un
  // agente de una empresa; una empresa firma con el agente que atiende. ORGANIZACION sola ya no es una condición válida.
  var CONDICIONES_PROFESIONAL = ['INDEPENDIENTE', 'AGENTE_DE_ORGANIZACION'];
  var TIPOS_CONTACTO = ['WHATSAPP', 'TELEFONO', 'CORREO', 'WEB'];
  var HITOS_PRECIO = { P: 'Precio publicado', U: 'Precio autorizado' };
  var OPERACIONES = ['VENTA', 'ALQUILER'];
  // Encargo seleccionado (CAT-E17; REVISION Web Free, paso 3): VENCIDO no viaja, se calcula con vigenteHasta.
  var ESTADOS_ENCARGO = ['EN_PREPARACION', 'APROBADO', 'FINALIZADO', 'RESCINDIDO'];
  // Pieza desactualizada (RF-024; AC-171): por una versión nueva o por un cambio en sus orígenes.
  var CAUSAS_DESACTUALIZACION = ['NUEVA_VERSION', 'ORIGEN_MODIFICADO'];
  // Medio del tiempo referencial a un lugar público (1.3.0; H-30).
  var MODOS_TIEMPO = ['A_PIE', 'EN_AUTO', 'EN_TRANSPORTE_PUBLICO'];
  // Precisión de la ubicación autorizada para la salida (1.5.0; H-20 y H-33): el inmueble mismo, sólo su edificio (el número
  // interior queda reservado) o una zona aproximada por decisión de difusión, con su radio.
  var PRECISIONES = ['EXACTA', 'EDIFICIO', 'APROXIMADA'];
  // C6 aprobado01/11: procedencia explícita desde contrato1.13; linaje/modelo/permisos siguen en Core.
  var CAMPOS_DERIVADA = ['derivadaDe', 'vinculoOriginal', 'tipoProceso', 'mejoraIA'];
  // CAT18/19, contrato1.14: Core transmite el concepto humano; no es texto libre ni conversión.
  var ROTULOS_AREA = ['Área total', 'Área construida', 'Área de terreno', 'Área arrendable', 'Área del estacionamiento', 'Área del objeto ofrecido'];
  // Términos de otros vocabularios de estado: se traducen al contrato, no se aceptan como estado (§2.4).
  var EQUIVALENCIAS = {
    CONOCIDO: 'un valor conocido viaja como VIGENTE con su procedencia',
    INFORMADA: 'un valor conocido viaja como VIGENTE con su procedencia',
    POR_CONFIRMAR: 'lo desconocido se declara FALTANTE',
    DESCONOCIDO: 'lo desconocido se declara FALTANTE',
    DESCONOCIDA: 'lo desconocido se declara FALTANTE',
    PENDIENTE_PREGUNTA: 'lo desconocido se declara FALTANTE',
    AUSENCIA_DECLARADA: 'una ausencia declarada viaja como VIGENTE con su valor (false, 0 o la opción del catálogo) y su procedencia',
    NO_APLICABLE: 'lo que no aplica no se envía',
    HISTORICO: 'HISTORICO no viaja en la salida'
  };
  // Nombres que nunca viajan en la salida comercial (CAT-E17/E43; RN-003; CIERRE §2; CAT:1080, CAT:1513):
  // titulares, documentos de identidad, partida, firmas, dirección, expediente, precio reservado,
  // archivos del expediente y metadatos EXIF (CAT-R14).
  var PRIVADOS = ['titular', 'titulares', 'propietario', 'propietarios', 'dni', 'documento', 'numeroDocumento', 'numero_documento', 'tipoDocumento', 'partida', 'partida_registral', 'partidaRegistral', 'firma', 'firmas', 'firmaDigital', 'direccion', 'direccionExacta', 'referenciaInterna', 'referencia_interna', 'interiorUnidad', 'interior_unidad', 'geo_lat', 'geo_long', 'telefonoPropietario', 'correoPropietario', 'expediente', 'precioReservado', 'precio_reservado', 'reservado', 'documentos', 'adjuntos', 'exif'];
  var FECHA = /^\d{4}-\d{2}-\d{2}$/;
  var CORREO = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
  var TELEFONO = /(\+?51[\s.-]?)?\b9\d{2}[\s.-]?\d{3}[\s.-]?\d{3}\b/;
  // Contactos que se enlazan (1.4.0): número internacional (E.164: «+», código de país y de 8 a 15 cifras), correo y web.
  var INTERNACIONAL = /^\+[1-9](?:[\s.-]?\d){7,14}$/;
  var CORREO_SOLO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  var WEB_SOLO = /^(https:\/\/)?[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+(\/[^\s]*)?$/;
  var IMPORTE_EN_TEXTO = /(US\$|USD|S\/\.?|\$)\s?\d/;
  var RELLENO = /por confirmar|por definir|sin dato|^\s*(pendiente|desconocid[oa]|n\/?a|s\/d|-+|\?+)\s*$/i;
  // Ocho o más dígitos seguidos en un texto libre: posible DNI o número de partida (1.1.0).
  var DIGITOS_LARGOS = /\b\d{8,}\b/;
  // La sección documental no certifica de forma genérica (CIERRE §2): sin «en regla» (1.1.0).
  var EN_REGLA = /en regla/i;

  function esObjeto(x) { return x !== null && typeof x === 'object' && !Array.isArray(x); }
  function fechaValida(s) { if (typeof s !== 'string' || !FECHA.test(s)) return false; var d = new Date(s + 'T00:00:00Z'); return !isNaN(d) && d.toISOString().slice(0, 10) === s; }

  function Informe() { this.errores = []; this.avisos = []; }
  Informe.prototype.error = function (ruta, mensaje) { this.errores.push({ ruta: ruta, mensaje: mensaje }); };
  Informe.prototype.aviso = function (ruta, mensaje) { this.avisos.push({ ruta: ruta, mensaje: mensaje }); };

  function definicionDe(ref) {
    if (/^H-\d\d$/.test(ref)) { var h = PERFILES.huecos[ref]; return h ? { tipoDato: h.tipoDato, unidad: h.unidad, hueco: true } : null; }
    return CATALOGO.propiedad[ref] || CATALOGO.encargo[ref] || null;
  }

  function procedenciaDe(datos, p) {
    if (typeof p === 'string') return (datos.procedencias || {})[p] || null;
    return esObjeto(p) ? p : null;
  }

  function presente(x) { return x !== undefined && x !== null; }

  // Procedencia de un valor (modelo 4P; D-4P-1; V83). Desde 1.2.0: `naturaleza` es opcional y nunca se
  // deduce (ausente = no consta); la fecha es exigible: la del hecho (`observadoEn`, si se sabe) o la del
  // registro (`registradoEn`, que Core siempre conoce); `evidenciaRef` sólo cuando hay evidencia.
  function comprobarProcedencia(inf, ruta, p) {
    if (!esObjeto(p)) { inf.error(ruta, 'procedencia ausente o no resuelta'); return; }
    if (presente(p.naturaleza) && NATURALEZAS_DATO.indexOf(p.naturaleza) < 0) inf.error(ruta + '.naturaleza', 'DECLARADO, OBSERVADO o INFERIDO; si no consta, se omite (D-4P-1)');
    if (presente(p.observadoEn) && !fechaValida(p.observadoEn)) inf.error(ruta + '.observadoEn', 'fecha ISO AAAA-MM-DD');
    if (presente(p.registradoEn) && !fechaValida(p.registradoEn)) inf.error(ruta + '.registradoEn', 'fecha ISO AAAA-MM-DD');
    if (!presente(p.observadoEn) && !presente(p.registradoEn)) inf.error(ruta, 'procedencia sin fecha: observadoEn (fecha del hecho) o registradoEn (fecha de registro)');
    if (fechaValida(p.observadoEn) && fechaValida(p.registradoEn) && p.observadoEn > p.registradoEn) inf.error(ruta + '.observadoEn', 'el hecho no puede observarse después de registrarse');
    if (presente(p.evidenciaRef) && (typeof p.evidenciaRef !== 'string' || !p.evidenciaRef.trim())) inf.error(ruta + '.evidenciaRef', 'texto no vacío; sin evidencia, se omite');
    if (p.naturaleza === 'INFERIDO' && !(typeof p.confianza === 'number' && p.confianza >= 0 && p.confianza <= 1)) inf.error(ruta + '.confianza', 'un dato INFERIDO exige confianza entre 0 y 1');
  }

  // Resuelve la procedencia de un dato: por identificador del registro o en línea. La que viene en línea
  // se comprueba con las mismas reglas que las del registro (1.2.0).
  function resolverProcedencia(inf, datos, ruta, ref) {
    if (typeof ref === 'string') { var p = (datos.procedencias || {})[ref]; if (!p) inf.error(ruta, 'procedencia «' + ref + '» no registrada en datos.procedencias'); return p || null; }
    if (esObjeto(ref)) { comprobarProcedencia(inf, ruta, ref); return ref; }
    inf.error(ruta, 'procedencia ausente');
    return null;
  }

  function comprobarValor(inf, ruta, def, valor, opciones) {
    var t = def.tipoDato;
    if (t === 'ENTERO') { if (!(typeof valor === 'number' && Number.isInteger(valor) && valor >= 0)) inf.error(ruta, 'ENTERO no negativo esperado; los datos llegan sin formato'); }
    else if (t === 'DECIMAL') { if (!(typeof valor === 'number' && isFinite(valor) && valor >= 0)) inf.error(ruta, 'DECIMAL finito no negativo esperado; los datos llegan sin formato'); }
    else if (t === 'BOOLEANO') { if (typeof valor !== 'boolean') inf.error(ruta, 'BOOLEANO esperado'); }
    else if (t === 'TEXTO') { if (typeof valor !== 'string' || !valor.trim()) inf.error(ruta, 'TEXTO no vacío esperado'); }
    else if (t === 'FECHA') { if (!fechaValida(valor)) inf.error(ruta, 'FECHA ISO AAAA-MM-DD esperada'); }
    else if (t === 'LISTA') {
      if (typeof valor !== 'string') inf.error(ruta, 'LISTA: valor de opción esperado');
      else if (opciones && opciones.length && opciones.indexOf(valor) < 0) inf.error(ruta, 'LISTA: «' + valor + '» no es una opción del catálogo');
    } else if (t === 'LISTA_MULTIPLE') {
      if (!Array.isArray(valor) || !valor.length) inf.error(ruta, 'LISTA_MULTIPLE: arreglo de opciones esperado');
      else valor.forEach(function (v) { if (opciones && opciones.length && opciones.indexOf(v) < 0) inf.error(ruta, 'LISTA_MULTIPLE: «' + v + '» no es una opción del catálogo'); });
    } else if (t === 'IMPORTE') { comprobarImporte(inf, ruta, valor); }
    else if (t === 'OBJETO') { if (!esObjeto(valor)) inf.error(ruta, 'objeto esperado'); }
  }

  function comprobarImporte(inf, ruta, v) {
    if (!esObjeto(v)) { inf.error(ruta, 'IMPORTE {importe, moneda} esperado'); return; }
    if (!(typeof v.importe === 'number' && isFinite(v.importe) && v.importe >= 0)) inf.error(ruta + '.importe', 'importe numérico sin formato esperado');
    if (CATALOGO.monedas.indexOf(v.moneda) < 0) inf.error(ruta + '.moneda', 'moneda ISO PEN o USD; nunca un símbolo ni una moneda deducida');
    if (v.periodo !== undefined && ['MENSUAL'].indexOf(v.periodo) < 0) inf.error(ruta + '.periodo', 'periodo admitido: MENSUAL');
  }

  // Un dato gobernado: {estado:'VIGENTE', valor, procedencia, ...} o {estado:'FALTANTE', motivo?}.
  function comprobarDato(inf, datos, ruta, d, def) {
    if (!esObjeto(d)) { inf.error(ruta, 'dato {estado, …} esperado'); return; }
    if (d.rotulo !== undefined) {
      if (!(Number(String(datos.versionContrato).split('.')[1]) >= 14)) inf.error(ruta + '.rotulo', 'rótulo de superficie exige contrato1.14 o posterior');
      if (d.estado !== 'VIGENTE' || !def || def.unidad !== 'm²' || !/^inmueble\.(atributos\.|fueraDeCatalogo\[)/.test(ruta)) inf.error(ruta + '.rotulo', 'sólo un área vigente admite rótulo de superficie');
      if (ROTULOS_AREA.indexOf(d.rotulo) < 0) inf.error(ruta + '.rotulo', 'rótulo de superficie del vocabulario cerrado CAT18/19; no texto libre');
    }
    if (d.estado === 'FALTANTE') {
      if ('valor' in d && d.valor !== null) inf.error(ruta, 'un dato FALTANTE no lleva valor');
      if (d.motivo !== undefined && typeof d.motivo !== 'string') inf.error(ruta + '.motivo', 'texto esperado');
      if (d.incertidumbre !== undefined) inf.error(ruta + '.incertidumbre', 'una incertidumbre se declara sobre un valor vigente; lo desconocido ya es FALTANTE');
      return;
    }
    if (d.estado !== 'VIGENTE') { inf.error(ruta + '.estado', 'estado VIGENTE o FALTANTE' + (EQUIVALENCIAS[d.estado] ? ': ' + EQUIVALENCIAS[d.estado] : '')); return; }
    // Un texto de relleno no es un valor: lo desconocido se declara FALTANTE (1.0.1).
    if (typeof d.valor === 'string' && RELLENO.test(d.valor)) inf.error(ruta + '.valor', 'texto de relleno («' + d.valor + '»): un dato desconocido se declara FALTANTE');
    if (def) comprobarValor(inf, ruta + '.valor', def, d.valor, def.opciones);
    if (d.decimales !== undefined && !(Number.isInteger(d.decimales) && d.decimales >= 0 && d.decimales <= 3)) inf.error(ruta + '.decimales', 'entero 0–3');
    if (d.unidad !== undefined && def && def.unidad && d.unidad !== def.unidad) inf.error(ruta + '.unidad', 'la unidad del catálogo es «' + def.unidad + '»');
    // Un valor vigente sin naturaleza es un valor conocido: se presenta con normalidad (1.2.0).
    var p = resolverProcedencia(inf, datos, ruta + '.procedencia', d.procedencia);
    if (p && p.naturaleza === 'INFERIDO') inf.aviso(ruta, 'dato INFERIDO: no se presenta como hecho en la pieza');
    // Notas para el profesional (1.5.0): nunca entran en la pieza. `observacion` va a «Qué debe confirmarse»; `incertidumbre`
    // la declara la proyección cuando el alcance de un valor no está claro y deja la pieza en vista previa hasta resolverlo.
    if (d.observacion !== undefined && (typeof d.observacion !== 'string' || !d.observacion.trim())) inf.error(ruta + '.observacion', 'texto no vacío: una nota para el profesional que no entra en la pieza');
    if (d.incertidumbre !== undefined && (typeof d.incertidumbre !== 'string' || !d.incertidumbre.trim() || d.incertidumbre.length > 160)) inf.error(ruta + '.incertidumbre', 'frase de hasta 160 caracteres que completa «Falta confirmar …»');
  }
  // Imagen autorizada para la salida (logotipo, 1.4.0; fotografía de quien atiende, 1.5.0): local, con sus medidas, su
  // texto alternativo y la autorización expresa, como los contactos.
  function comprobarImagenAutorizada(inf, ruta, img, que) {
    if (!esObjeto(img)) { inf.error(ruta, 'objeto { src, ancho, alto, alt, autorizadoParaSalida }'); return; }
    comprobarSrc(inf, ruta + '.src', img.src);
    if (!(typeof img.ancho === 'number' && img.ancho > 0 && typeof img.alto === 'number' && img.alto > 0)) inf.error(ruta, 'ancho y alto del original, en píxeles');
    if (typeof img.alt !== 'string' || !img.alt.trim()) inf.error(ruta + '.alt', que);
    if (img.autorizadoParaSalida !== true) inf.error(ruta, 'sólo viaja con autorizadoParaSalida: true, la autorización para esta salida (CAT-E17; CAT:765)');
  }

  // Campos técnicos que no son texto libre: huellas, rutas y referencias de evidencia.
  var TECNICOS = ['sha256', 'src', 'claro', 'oscuro', 'evidenciaRef', 'fuente', 'id', 'portada'];
  function recorrerPrivados(inf, nodo, ruta, permitidos) {
    if (Array.isArray(nodo)) { nodo.forEach(function (x, i) { recorrerPrivados(inf, x, ruta + '[' + i + ']', permitidos); }); return; }
    if (!esObjeto(nodo)) {
      var campo = ruta.replace(/\[\d+\]$/, '').split('.').pop();
      if (typeof nodo === 'string' && TECNICOS.indexOf(campo) < 0 && permitidos.textoLibre(ruta)) {
        if (CORREO.test(nodo) && !permitidos.contacto(nodo)) inf.error(ruta, 'correo en texto libre: sólo viajan contactos autorizados en marcaYContacto');
        if (TELEFONO.test(nodo) && !permitidos.contacto(nodo)) inf.error(ruta, 'teléfono en texto libre: sólo viajan contactos autorizados en marcaYContacto');
        else if (DIGITOS_LARGOS.test(nodo) && !permitidos.contacto(nodo)) inf.error(ruta, 'ocho o más dígitos seguidos en texto libre: posible DNI o partida; no viaja en la salida (RN-003)');
      }
      return;
    }
    Object.keys(nodo).forEach(function (k) {
      if (PRIVADOS.indexOf(k) >= 0) inf.error(ruta + '.' + k, 'dato privado: no entra en el contrato de salida');
      if ((k === 'latitud' || k === 'longitud') && ruta.indexOf('puntoAutorizado') < 0) inf.error(ruta + '.' + k, 'coordenadas sólo dentro de ubicacion.puntoAutorizado');
      if ((k === 'telefono' || k === 'correo') && ruta.indexOf('marcaYContacto') < 0) inf.error(ruta + '.' + k, 'contacto fuera de marcaYContacto');
      recorrerPrivados(inf, nodo[k], ruta + '.' + k, permitidos);
    });
  }

  function validar(datos) {
    var inf = new Informe();
    if (!esObjeto(datos)) { inf.error('datos', 'objeto esperado'); return cerrar(inf, datos); }
    if (datos.contrato !== 'brox-ficha-datos') inf.error('contrato', 'debe ser «brox-ficha-datos»');
    var mayor = typeof datos.versionContrato === 'string' ? Number(datos.versionContrato.split('.')[0]) : NaN;
    if (mayor !== VERSION_MAYOR) inf.error('versionContrato', 'versión mayor ' + VERSION_MAYOR + ' esperada (recibida: ' + datos.versionContrato + ')');

    // Procedencias registradas.
    var procs = datos.procedencias || {};
    if (!esObjeto(procs)) inf.error('procedencias', 'objeto id → procedencia esperado');
    else Object.keys(procs).forEach(function (id) { comprobarProcedencia(inf, 'procedencias.' + id, procs[id]); });

    // Pieza (CAT-E17 PiezaComercial).
    var pz = datos.pieza;
    if (!esObjeto(pz)) inf.error('pieza', 'objeto requerido');
    else {
      if (typeof pz.id !== 'string' || !pz.id) inf.error('pieza.id', 'identificador requerido');
      if (NATURALEZAS_PIEZA.indexOf(pz.naturaleza) < 0) inf.error('pieza.naturaleza', 'FUENTE_REAL o DEMOSTRACION');
      if (SALIDAS.indexOf(pz.salida) < 0) inf.error('pieza.salida', 'REVISION_PRIVADA o DIFUSION_AUTORIZADA');
      if (pz.plantilla !== 'BROX-FICHA' || pz.versionPlantilla !== 'V08') inf.error('pieza.plantilla', 'plantilla BROX-FICHA versión V08');
      if (!(Number.isInteger(pz.versionContenido) && pz.versionContenido >= 1)) inf.error('pieza.versionContenido', 'entero ≥ 1');
      if (!fechaValida(pz.fechaCorte)) inf.error('pieza.fechaCorte', 'fecha ISO de la proyección requerida');
      if (!Array.isArray(pz.versionesOrigen)) inf.error('pieza.versionesOrigen', 'arreglo de fuentes y versiones requerido');
      if (pz.naturaleza === 'DEMOSTRACION' && pz.salida === 'DIFUSION_AUTORIZADA') inf.error('pieza.salida', 'una demostración no se autoriza para difusión');
      // 1.7.0: el enlace seguro de la ficha digital, si la app lo entrega (CAT-E43: se abre por posesión, caduca y se revoca).
      // La plantilla no crea enlaces; sólo lo muestra («Ver las N fotos», «Ver la ficha completa»). https y sin espacios.
      var en = pz.enlaceDigital;
      if (en !== undefined && en !== null) {
        if (!esObjeto(en) || typeof en.url !== 'string' || !/^https:\/\/[^\s"<>]+$/.test(en.url) || en.url.length > 300) inf.error('pieza.enlaceDigital.url', 'enlace https de hasta 300 caracteres, sin espacios');
        else if (en.vigenteHasta !== undefined && !fechaValida(en.vigenteHasta)) inf.error('pieza.enlaceDigital.vigenteHasta', 'fecha ISO');
      }
      // Desactualización (RF-024; AC-171): la indica Core; la pieza la muestra y no se difunde (1.1.0).
      var ds = pz.desactualizada;
      if (ds !== undefined && ds !== null) {
        if (!esObjeto(ds) || !fechaValida(ds.desde) || CAUSAS_DESACTUALIZACION.indexOf(ds.causa) < 0) inf.error('pieza.desactualizada', 'desde (fecha ISO) y causa ' + CAUSAS_DESACTUALIZACION.join(' u ') + ' requeridos');
        else {
          if (fechaValida(pz.fechaCorte) && ds.desde < pz.fechaCorte) inf.error('pieza.desactualizada.desde', 'no puede ser anterior a la fecha de corte de la pieza');
          if (ds.versionVigente !== undefined && !(Number.isInteger(ds.versionVigente) && ds.versionVigente > pz.versionContenido)) inf.error('pieza.desactualizada.versionVigente', 'entero mayor que versionContenido');
          if (ds.causa === 'NUEVA_VERSION' && ds.versionVigente === undefined) inf.error('pieza.desactualizada.versionVigente', 'una versión nueva indica su número (AC-171)');
        }
      }
    }
    var demostracion = esObjeto(pz) && pz.naturaleza === 'DEMOSTRACION';

    // Inmueble.
    var inm = datos.inmueble, tipo = null;
    if (!esObjeto(inm)) inf.error('inmueble', 'objeto requerido');
    else {
      comprobarDato(inf, datos, 'inmueble.tipo', inm.tipo, { tipoDato: 'LISTA', opciones: Object.keys(CATALOGO.tipos) });
      if (esObjeto(inm.tipo) && inm.tipo.estado === 'VIGENTE') tipo = inm.tipo.valor;
      var u = inm.ubicacion;
      if (!esObjeto(u)) inf.error('inmueble.ubicacion', 'objeto requerido');
      else {
        comprobarDato(inf, datos, 'inmueble.ubicacion.distrito', u.distrito, { tipoDato: 'TEXTO' });
        comprobarDato(inf, datos, 'inmueble.ubicacion.zonaUrbanizacion', u.zonaUrbanizacion, { tipoDato: 'TEXTO' });
        // Ubicación autorizada para la salida (1.5.0; revisión entregada por el titular el 20 de octubre: la ficha debe permitir
        // reconocer dónde está el inmueble). La dirección (H-33) y el punto (H-20) los autoriza Core para esta salida, por
        // ejemplo al formalizar el encargo, y llevan la misma precisión: el inmueble (EXACTA), sólo su edificio (EDIFICIO) o,
        // por decisión de difusión, una zona aproximada con su radio (sólo el punto). La exclusividad no es esa autorización.
        var pa = u.puntoAutorizado, precisionPunto = null;
        if (pa !== null && pa !== undefined) {
          var rp = 'inmueble.ubicacion.puntoAutorizado';
          if (!esObjeto(pa) || typeof pa.latitud !== 'number' || typeof pa.longitud !== 'number' || !fechaValida(pa.autorizadoEl) || typeof pa.autorizadoPor !== 'string' || !pa.autorizadoPor.trim())
            inf.error(rp, 'punto sólo con latitud, longitud, precision, autorizadoPor y autorizadoEl (H-20)');
          else {
            if (Math.abs(pa.latitud) > 90 || Math.abs(pa.longitud) > 180) inf.error(rp, 'latitud entre −90 y 90 y longitud entre −180 y 180');
            if (PRECISIONES.indexOf(pa.precision) < 0) inf.error(rp + '.precision', PRECISIONES.join(' · ') + ' (1.5.0): la pieza dibuja el punto con esa precisión');
            else precisionPunto = pa.precision;
            if (pa.precision === 'APROXIMADA' && !(Number.isInteger(pa.radioMetros) && pa.radioMetros >= 50 && pa.radioMetros <= 2000)) inf.error(rp + '.radioMetros', 'una ubicación aproximada declara su radio: entero de 50 a 2000 metros');
            if (pa.precision !== 'APROXIMADA' && pa.radioMetros !== undefined) inf.error(rp + '.radioMetros', 'sólo una ubicación APROXIMADA lleva radio');
          }
        }
        var da = u.direccionAutorizada;
        if (da !== undefined && da !== null) {
          var rd = 'inmueble.ubicacion.direccionAutorizada';
          comprobarDato(inf, datos, rd, da, { tipoDato: 'TEXTO' });
          if (esObjeto(da) && da.estado === 'VIGENTE') {
            if (['EXACTA', 'EDIFICIO'].indexOf(da.precision) < 0) inf.error(rd + '.precision', 'EXACTA (el inmueble) o EDIFICIO (sólo el edificio; el número interior queda reservado)');
            if (!fechaValida(da.autorizadoEl) || typeof da.autorizadoPor !== 'string' || !da.autorizadoPor.trim()) inf.error(rd, 'autorizadoPor y autorizadoEl requeridos: la dirección sólo sale autorizada para esta salida (H-33)');
            if (precisionPunto && precisionPunto !== da.precision) inf.error(rd + '.precision', 'la dirección y el punto llevan la misma precisión (dirección ' + da.precision + ', punto ' + precisionPunto + ')');
          }
        }
        if ((precisionPunto === 'EXACTA' || precisionPunto === 'EDIFICIO') && !(esObjeto(da) && da.estado === 'VIGENTE'))
          inf.error('inmueble.ubicacion.puntoAutorizado', 'un punto ' + precisionPunto + ' va con su dirección autorizada (direccionAutorizada, H-33): dirección, marcador y enlace dicen lo mismo');
      }
      // Atributos del catálogo Core.
      var atr = inm.atributos || {};
      if (!esObjeto(atr)) inf.error('inmueble.atributos', 'objeto clave → dato esperado');
      else Object.keys(atr).forEach(function (clave) {
        var def = CATALOGO.propiedad[clave], ruta = 'inmueble.atributos.' + clave;
        if (!def) { inf.error(ruta, 'clave inexistente en el catálogo Core; un dato fuera del catálogo va en fueraDeCatalogo con su hueco'); return; }
        if (!def.activa) inf.error(ruta, 'clave RETIRADA en el catálogo');
        if (tipo && !def.aplicabilidad[tipo]) inf.error(ruta, 'la clave no aplica al tipo ' + tipo + ' según el catálogo');
        comprobarDato(inf, datos, ruta, atr[clave], def);
      });
      if (tipo) Object.keys(CATALOGO.propiedad).forEach(function (clave) {
        var def = CATALOGO.propiedad[clave], ex = def.aplicabilidad[tipo];
        if (!def.activa || !ex || ex === 'OPC') return;
        var d = atr[clave];
        if (!d || d.estado !== 'VIGENTE') inf.aviso('inmueble.atributos.' + clave, ex === 'ALT' ? 'Core exige este dato para confirmar el registro (ALT)' : 'Core exige este dato para publicar (PUB)');
      });
      // Fuera de catálogo: huecos documentados.
      var vistos = {};
      (inm.fueraDeCatalogo || []).forEach(function (h, i) {
        var ruta = 'inmueble.fueraDeCatalogo[' + i + ']';
        if (!esObjeto(h) || !PERFILES.huecos[h.hueco]) { inf.error(ruta + '.hueco', 'identificador de hueco H-xx registrado esperado'); return; }
        if (vistos[h.hueco]) inf.error(ruta, 'hueco repetido'); vistos[h.hueco] = true;
        comprobarDato(inf, datos, ruta, h, definicionDe(h.hueco));
      });
      // Entorno (H-22).
      if (inm.entorno !== null && inm.entorno !== undefined) {
        var en = inm.entorno, ruta = 'inmueble.entorno';
        if (!esObjeto(en) || !esObjeto(en.mapa) || !esObjeto(en.mapa.variantes)) inf.error(ruta, 'mapa con variantes requerido');
        // «cuadrado» (1.5.0) es opcional: la portada digital sin fotografías lo usa en lugar de la galería.
        else ['ancho', 'compacto', 'cuadrado'].forEach(function (v) {
          var x = en.mapa.variantes[v], rv = ruta + '.mapa.variantes.' + v;
          if (v === 'cuadrado' && x === undefined) return;
          if (!esObjeto(x) || typeof x.claro !== 'string' || typeof x.oscuro !== 'string') inf.error(rv, 'claro y oscuro requeridos');
          else {
            [x.claro, x.oscuro].forEach(function (src) { comprobarSrc(inf, rv, src); });
            // Encuadre del mapa (1.5.0): sus bordes en grados, para que la pieza dibuje encima el punto autorizado. El archivo
            // del mapa nunca trae marcado el inmueble.
            var q = x.encuadre;
            if (q !== undefined && !(esObjeto(q) && [q.oeste, q.sur, q.este, q.norte].every(function (n) { return typeof n === 'number' && isFinite(n); }) && q.oeste < q.este && q.sur < q.norte)) inf.error(rv + '.encuadre', 'oeste, sur, este y norte en grados, con oeste < este y sur < norte');
            else if (q && esObjeto(pa) && typeof pa.latitud === 'number' && precisionPunto && (pa.longitud < q.oeste || pa.longitud > q.este || pa.latitud < q.sur || pa.latitud > q.norte)) inf.aviso(rv + '.encuadre', 'el punto autorizado queda fuera de este mapa: no se marca');
            else if (!q && precisionPunto) inf.aviso(rv, 'el mapa no trae su encuadre: el punto autorizado no se marca en él');
          }
        });
        if (en.atribucionEnlace !== undefined && !(typeof en.atribucionEnlace === 'string' && /^https:\/\/[^\s]+$/.test(en.atribucionEnlace))) inf.error(ruta + '.atribucionEnlace', 'dirección https de la licencia o de los créditos del mapa');
        var conTiempo = false;
        if (!Array.isArray(en.referencias)) inf.error(ruta + '.referencias', 'arreglo requerido');
        else en.referencias.forEach(function (r, i) {
          if (!esObjeto(r) || !/^\d{2}$/.test(r.numero) || !r.nombre || !r.categoria || !fechaValida(r.verificadoEl)) { inf.error(ruta + '.referencias[' + i + ']', 'numero 2 dígitos, nombre, categoria y verificadoEl requeridos'); return; }
          // Tiempo referencial (1.3.0; hueco H-30): minutos enteros y medio, desde la ubicación registrada.
          if (r.tiempo === undefined) return;
          conTiempo = true;
          var rt = ruta + '.referencias[' + i + '].tiempo';
          if (!esObjeto(r.tiempo) || !Number.isInteger(r.tiempo.minutos) || r.tiempo.minutos < 1 || r.tiempo.minutos > 180) inf.error(rt + '.minutos', 'entero de 1 a 180 minutos');
          else if (MODOS_TIEMPO.indexOf(r.tiempo.modo) < 0) inf.error(rt + '.modo', 'medio ' + MODOS_TIEMPO.join(' · '));
        });
        if (conTiempo) {
          var ts = en.tiempos;
          if (!esObjeto(ts) || ts.desde !== 'UBICACION_REGISTRADA' || !fechaValida(ts.calculadoEl)) inf.error(ruta + '.tiempos', 'los tiempos referenciales declaran desde: UBICACION_REGISTRADA y calculadoEl');
          else resolverProcedencia(inf, datos, ruta + '.tiempos.procedencia', ts.procedencia);
          // 1.5.0: varios tiempos juntos acotan la ubicación (H-30). Sólo se muestran si la salida autoriza el inmueble o su
          // edificio; si no, la ficha no los escribe, para no revelar por otra vía lo que la salida reserva.
          if (precisionPunto !== 'EXACTA' && precisionPunto !== 'EDIFICIO') inf.aviso(ruta + '.tiempos', 'sin una ubicación autorizada del inmueble o de su edificio, la ficha no escribe los tiempos: juntos acotan la ubicación');
        }
        if (en.distancias !== undefined) inf.error(ruta + '.distancias', 'la ficha no publica distancias');
        if (typeof en.atribucion !== 'string' || !fechaValida(en.fecha)) inf.error(ruta, 'atribucion y fecha requeridas');
        resolverProcedencia(inf, datos, ruta + '.procedencia', en.procedencia);
        if (en.marcaPropiedad) inf.error(ruta + '.marcaPropiedad', 'el mapa nunca marca la propiedad');
      }
      // La cuota de mantenimiento de Core es DECIMAL sin moneda; su importe completo viaja en H-18
      // y debe coincidir con la cifra de Core.
      var h18 = (inm.fueraDeCatalogo || []).filter(function (h) { return h && h.hueco === 'H-18' && h.estado === 'VIGENTE'; })[0];
      var cuota = atr && atr.cuota_mantenimiento;
      if (h18 && vigente(cuota) && esObjeto(h18.valor) && h18.valor.importe !== cuota.valor) inf.error('inmueble.fueraDeCatalogo.H-18', 'el importe no coincide con cuota_mantenimiento');
      if (h18 && !vigente(cuota)) inf.error('inmueble.fueraDeCatalogo.H-18', 'H-18 completa cuota_mantenimiento; sin la cifra de Core no viaja');
      // Fotografías.
      var ids = {};
      (inm.fotos || []).forEach(function (f, i) {
        var ruta = 'inmueble.fotos[' + i + ']';
        if (!esObjeto(f)) { inf.error(ruta, 'objeto esperado'); return; }
        if (typeof f.id !== 'string' || !f.id) inf.error(ruta + '.id', 'identificador requerido');
        else if (ids[f.id]) inf.error(ruta + '.id', 'identificador repetido'); else ids[f.id] = true;
        comprobarSrc(inf, ruta + '.src', f.src);
        if (!(Number.isInteger(f.ancho) && f.ancho > 0 && Number.isInteger(f.alto) && f.alto > 0)) inf.error(ruta, 'ancho y alto en píxeles requeridos');
        if (typeof f.ambiente !== 'string' || !f.ambiente) inf.error(ruta + '.ambiente', 'ambiente requerido (H-19)');
        if (typeof f.rotulo !== 'string' || !f.rotulo) inf.error(ruta + '.rotulo', 'rótulo requerido');
        if (ORIGENES_FOTO.indexOf(f.origen) < 0) inf.error(ruta + '.origen', ORIGENES_FOTO.join(', '));
        if (AUTORIZACIONES_FOTO.indexOf(f.autorizacion) < 0) inf.error(ruta + '.autorizacion', AUTORIZACIONES_FOTO.join(', '));
        if (f.autorizacion === 'NO_AUTORIZADA') inf.error(ruta + '.autorizacion', 'una fotografía no autorizada no viaja en la salida');
        if (f.origen === 'ESCENA_ILUSTRATIVA' && !demostracion) inf.error(ruta + '.origen', 'una escena ilustrativa sólo cabe en una demostración');
        if (esObjeto(pz) && pz.salida === 'DIFUSION_AUTORIZADA' && f.autorizacion !== 'SALIDA_AUTORIZADA') inf.error(ruta + '.autorizacion', 'la difusión exige SALIDA_AUTORIZADA');
        // Copia pública sin EXIF (CAT-R14; H-29): el original y sus metadatos se quedan en Core (1.1.0).
        if (f.exifDepurado !== undefined && typeof f.exifDepurado !== 'boolean') inf.error(ruta + '.exifDepurado', 'booleano');
        if (esObjeto(pz) && pz.salida === 'DIFUSION_AUTORIZADA' && f.exifDepurado !== true) inf.error(ruta + '.exifDepurado', 'la difusión exige una copia sin EXIF (CAT-R14)');
        var admiteDerivada = Number(String(datos.versionContrato).split('.')[1]) >= 13;
        if (f.origen === 'DERIVADA_IA' && (!admiteDerivada || f.mejoraIA !== true))
          inf.error(ruta + '.origen', 'DERIVADA_IA exige contrato1.13 o posterior y mejoraIA=true; Core acredita autorización, fidelidad y selección');
        CAMPOS_DERIVADA.forEach(function (k) {
          if (k === 'mejoraIA' && admiteDerivada && f.origen === 'DERIVADA_IA' && f.mejoraIA === true) return;
          if (f[k] !== undefined) inf.error(ruta + '.' + k, 'el linaje privado no viaja; mejoraIA sólo acompaña DERIVADA_IA autorizada desde contrato1.13');
        });
        if (f.encuadre !== undefined && !(esObjeto(f.encuadre) && Number.isInteger(f.encuadre.izquierda) && Number.isInteger(f.encuadre.derecha))) inf.error(ruta + '.encuadre', 'izquierda y derecha en píxeles');
        resolverProcedencia(inf, datos, ruta + '.procedencia', f.procedencia);
      });
      // Documentación (H-23).
      var doc = inm.documentacion;
      if (doc !== undefined && doc !== null) {
        if (!esObjeto(doc) || ['VIGENTE', 'FALTANTE'].indexOf(doc.estado) < 0) inf.error('inmueble.documentacion', 'estado VIGENTE o FALTANTE');
        else if (doc.estado === 'VIGENTE') (doc.elementos || []).forEach(function (e, i) {
          if (!esObjeto(e) || !e.rotulo || !e.texto) { inf.error('inmueble.documentacion.elementos[' + i + ']', 'rotulo y texto requeridos'); return; }
          resolverProcedencia(inf, datos, 'inmueble.documentacion.elementos[' + i + '].procedencia', e.procedencia);
          if (EN_REGLA.test(e.rotulo + ' ' + e.texto)) inf.error('inmueble.documentacion.elementos[' + i + ']', '«en regla» es una certificación genérica: se escribe el hecho revisado con su fuente y fecha (CIERRE-PLANTILLAS §2)');
        });
      }
      // Uso y construcción (H-24).
      var uc = inm.usoYConstruccion;
      if (uc !== undefined && uc !== null) {
        var r = 'inmueble.usoYConstruccion';
        if (!esObjeto(uc)) inf.error(r, 'objeto esperado');
        else {
          if (['DOCUMENTO_APLICABLE', 'EJEMPLO_FICTICIO'].indexOf(uc.naturaleza) < 0) inf.error(r + '.naturaleza', 'DOCUMENTO_APLICABLE o EJEMPLO_FICTICIO');
          if (uc.naturaleza === 'EJEMPLO_FICTICIO' && !demostracion) inf.error(r + '.naturaleza', 'un ejemplo ficticio sólo cabe en una demostración');
          if (['REVISADO_VIGENTE', 'PENDIENTE', 'VENCIDO', 'EN_CONFLICTO'].indexOf(uc.revision) < 0) inf.error(r + '.revision', 'estado de revisión no reconocido');
          if (typeof uc.aplicaAlPredio !== 'boolean') inf.error(r + '.aplicaAlPredio', 'booleano requerido');
          if (uc.alturaMaximaMetros !== undefined && !(typeof uc.alturaMaximaMetros === 'number' && isFinite(uc.alturaMaximaMetros) && uc.alturaMaximaMetros > 0)) inf.error(r + '.alturaMaximaMetros', 'número positivo; nunca se convierte en pisos');
          if (!uc.fuente || !fechaValida(uc.fechaFuente) || !fechaValida(uc.revisadoEl)) inf.error(r, 'fuente, fechaFuente y revisadoEl requeridos');
        }
      }
    }

    // Encargo.
    var enc = datos.encargo;
    if (!esObjeto(enc)) inf.error('encargo', 'objeto requerido');
    else {
      // Encargo seleccionado (CAT-E17): referencia, versión, estado y vigencia. Sin encargo (id null)
      // la pieza sólo puede ser una vista previa privada (1.1.0).
      if (enc.id !== null && enc.id !== undefined && (typeof enc.id !== 'string' || !enc.id)) inf.error('encargo.id', 'referencia del encargo (texto) o null');
      if (typeof enc.id === 'string' && enc.id) {
        if (!(Number.isInteger(enc.version) && enc.version >= 1)) inf.error('encargo.version', 'entero ≥ 1: versión del encargo proyectada');
        if (enc.estado === 'VENCIDO') inf.error('encargo.estado', 'VENCIDO se calcula: Core envía APROBADO y vigenteHasta');
        else if (ESTADOS_ENCARGO.indexOf(enc.estado) < 0) inf.error('encargo.estado', ESTADOS_ENCARGO.join(', '));
      } else ['version', 'estado', 'vigenteHasta'].forEach(function (k) { if (enc[k] !== undefined && enc[k] !== null) inf.error('encargo.' + k, 'sin encargo seleccionado (id null) no viaja ' + k); });
      if (enc.vigenteHasta !== undefined && enc.vigenteHasta !== null && !fechaValida(enc.vigenteHasta)) inf.error('encargo.vigenteHasta', 'fecha ISO AAAA-MM-DD o null');
      if (esObjeto(pz) && pz.encargo !== undefined && (pz.encargo || null) !== (enc.id || null)) inf.error('pieza.encargo', 'debe coincidir con encargo.id (CAT-E17)');
      comprobarDato(inf, datos, 'encargo.operacion', enc.operacion, { tipoDato: 'LISTA', opciones: OPERACIONES });
      var op = esObjeto(enc.operacion) && enc.operacion.estado === 'VIGENTE' ? CATALOGO.operaciones[enc.operacion.valor] : null;
      var pr = enc.precio;
      if (!esObjeto(pr)) inf.error('encargo.precio', 'objeto {importe, moneda, hito} requerido');
      else {
        comprobarDato(inf, datos, 'encargo.precio.importe', pr.importe, { tipoDato: 'DECIMAL' });
        comprobarDato(inf, datos, 'encargo.precio.moneda', pr.moneda, { tipoDato: 'LISTA', opciones: CATALOGO.monedas });
        if (esObjeto(pr.moneda) && pr.moneda.estado === 'FALTANTE' && pr.moneda.enFuente !== undefined && typeof pr.moneda.enFuente !== 'string') inf.error('encargo.precio.moneda.enFuente', 'texto literal de la fuente');
        if (!HITOS_PRECIO[pr.hito]) inf.error('encargo.precio.hito', 'P (publicado) o U (autorizado), según precio_propiedad.hito');
        if (pr.periodo !== undefined && pr.periodo !== 'MENSUAL') inf.error('encargo.precio.periodo', 'sólo MENSUAL');
        if (op === 'A' && pr.periodo !== 'MENSUAL') inf.error('encargo.precio.periodo', 'un alquiler declara periodo MENSUAL (la renta de Core es mensual)');
        if (op === 'V' && pr.periodo !== undefined) inf.error('encargo.precio.periodo', 'una venta no lleva periodo');
        // Sujeto del precio (CAT-R05; hueco H-26): qué comprende la cifra cuando no es sólo el inmueble descrito.
        if (pr.sujeto !== undefined) comprobarDato(inf, datos, 'encargo.precio.sujeto', pr.sujeto, definicionDe('H-26'));
      }
      var cond = enc.condiciones || {};
      Object.keys(cond).forEach(function (clave) {
        var def = CATALOGO.encargo[clave], ruta = 'encargo.condiciones.' + clave;
        if (!def) { inf.error(ruta, 'clave ENCARGO inexistente (V74/V77)'); return; }
        if (op && tipo && (def.operaciones[op] || []).indexOf(tipo) < 0) inf.error(ruta, 'no aplica a ' + enc.operacion.valor + ' de tipo ' + tipo + ' según V74/V77');
        comprobarDato(inf, datos, ruta, cond[clave], def);
      });
    }

    // Marca y contacto (CAT-E17; H-21).
    var mc = datos.marcaYContacto, contactos = [];
    if (!esObjeto(mc)) inf.error('marcaYContacto', 'objeto requerido (FALTANTE si no hay identidad autorizada)');
    else if (mc.estado === 'VIGENTE') {
      if (mc.condicion === 'ORGANIZACION') inf.error('marcaYContacto.condicion', 'todo encargo tiene un responsable (1.6.0): un agente independiente (INDEPENDIENTE) o un agente de una empresa (AGENTE_DE_ORGANIZACION, con su organizacion); una empresa firma con el agente que atiende');
      else if (CONDICIONES_PROFESIONAL.indexOf(mc.condicion) < 0) inf.error('marcaYContacto.condicion', CONDICIONES_PROFESIONAL.join(', '));
      if (typeof mc.nombre !== 'string' || !mc.nombre) inf.error('marcaYContacto.nombre', 'nombre visible requerido');
      if (mc.condicion === 'AGENTE_DE_ORGANIZACION' && !mc.organizacion) inf.error('marcaYContacto.organizacion', 'requerida para un agente de organización');
      if (['REAL', 'DEMOSTRACION'].indexOf(mc.naturaleza) < 0) inf.error('marcaYContacto.naturaleza', 'REAL o DEMOSTRACION');
      if (mc.naturaleza === 'DEMOSTRACION' && !demostracion) inf.error('marcaYContacto.naturaleza', 'identidad de demostración en una pieza real');
      if (mc.registro !== undefined && !(esObjeto(mc.registro) && mc.registro.referencia && fechaValida(mc.registro.verificadoEl))) inf.error('marcaYContacto.registro', 'referencia y verificadoEl requeridos (CAT:383)');
      // Nombre de presentación (1.5.0): el que la persona eligió para presentarse («María Fernanda Villanueva»); si no
      // viaja, la ficha escribe el nombre completo y deja que salte de línea.
      if (mc.nombrePresentacion !== undefined && (typeof mc.nombrePresentacion !== 'string' || !mc.nombrePresentacion.trim())) inf.error('marcaYContacto.nombrePresentacion', 'texto no vacío');
      var principales = 0;
      (mc.contactos || []).forEach(function (c, i) {
        var r = 'marcaYContacto.contactos[' + i + ']';
        if (!esObjeto(c) || TIPOS_CONTACTO.indexOf(c.tipo) < 0 || typeof c.valor !== 'string') inf.error(r, 'tipo y valor requeridos');
        else if (c.autorizadoParaSalida !== true) inf.error(r, 'sólo viajan contactos autorizados para la salida (CAT:765, CAT:1499)');
        else {
          // Contacto principal (1.5.0): el que la persona configuró para la acción «Coordinar una visita»; uno como máximo.
          if (c.principal !== undefined && typeof c.principal !== 'boolean') inf.error(r + '.principal', 'booleano');
          if (c.principal === true && ++principales > 1) inf.error(r + '.principal', 'un solo contacto principal');
          contactos.push(c.valor.replace(/\s/g, ''));
          // 1.4.0: cada contacto es un enlace en pantalla y en el PDF. Lo que no se puede enlazar se escribe sin enlace
          // y se avisa; no es un error, para que los datos 1.3.x sigan siendo válidos.
          if (c.tipo === 'WHATSAPP' && !INTERNACIONAL.test(c.valor)) inf.aviso(r, 'WhatsApp sin código de país (+51 …): se escribe sin enlace, porque el chat directo pide el número internacional (CAT-E43)');
          if (c.tipo === 'CORREO' && !CORREO_SOLO.test(c.valor)) inf.aviso(r, 'no es una dirección de correo: se escribe sin enlace');
          if (c.tipo === 'WEB' && !WEB_SOLO.test(c.valor)) inf.aviso(r, 'no es una dirección web: se escribe sin enlace');
        }
      });
      // Logotipo de la organización o del profesional (1.4.0; hueco H-31): opcional, local y autorizado para esta
      // salida, como los contactos. Sin logotipo, el nombre lleva la firma.
      if (mc.logo !== undefined && mc.logo !== null) comprobarImagenAutorizada(inf, 'marcaYContacto.logo', mc.logo, 'el nombre que muestra el logotipo, para quien no lo ve');
      // Fotografía de quien atiende (1.5.0; hueco H-32): opcional, de la persona que atiende y autorizada por ella para esta
      // salida. Una organización sin agente no lleva fotografía: la firma es su marca.
      if (mc.retrato !== undefined && mc.retrato !== null) {
        comprobarImagenAutorizada(inf, 'marcaYContacto.retrato', mc.retrato, 'quién aparece en la fotografía, para quien no la ve');
        if (mc.condicion === 'ORGANIZACION') inf.error('marcaYContacto.retrato', 'una organización sin agente firma con su marca; la fotografía es de la persona que atiende');
      }
    } else if (mc.estado !== 'FALTANTE') inf.error('marcaYContacto.estado', 'VIGENTE o FALTANTE');

    // Presentación (parte 3).
    var pr3 = datos.presentacion;
    if (!esObjeto(pr3)) inf.error('presentacion', 'objeto requerido');
    else {
      var perfil = PERFILES.perfiles[pr3.perfil];
      if (!perfil) inf.error('presentacion.perfil', 'perfil desconocido; ver perfiles.js');
      else if (tipo && perfil.tipo !== tipo) inf.error('presentacion.perfil', 'el perfil «' + perfil.id + '» es de tipo ' + perfil.tipo + ' y el inmueble es ' + tipo);
      var fotosIds = ((inm && inm.fotos) || []).map(function (f) { return f.id; });
      if (pr3.portada !== null && pr3.portada !== undefined && fotosIds.indexOf(pr3.portada) < 0) inf.error('presentacion.portada', 'la portada debe ser una fotografía del contrato');
      var enGrupo = {};
      (pr3.grupos || []).forEach(function (g, i) {
        if (!esObjeto(g) || !g.nombre || !Array.isArray(g.fotos)) { inf.error('presentacion.grupos[' + i + ']', 'nombre y fotos requeridos'); return; }
        g.fotos.forEach(function (id) {
          if (fotosIds.indexOf(id) < 0) inf.error('presentacion.grupos[' + i + ']', 'fotografía desconocida ' + id);
          if (enGrupo[id]) inf.error('presentacion.grupos[' + i + ']', 'fotografía en dos grupos ' + id); enGrupo[id] = true;
        });
      });
      fotosIds.forEach(function (id) { if (!enGrupo[id]) inf.aviso('presentacion.grupos', 'fotografía ' + id + ' fuera de los grupos: se agrega al final'); });
      if (perfil && Array.isArray(pr3.destacados)) {
        var elegibles = perfil.caracteristicas.concat(perfil.rasgos).map(function (x) { return x.ref; }).concat(perfil.esquina ? [perfil.esquina] : []);
        pr3.destacados.forEach(function (ref) { if (elegibles.indexOf(ref) < 0) inf.error('presentacion.destacados', '«' + ref + '» no es una característica del perfil'); });
        if (pr3.destacados.length > 4) inf.aviso('presentacion.destacados', 'se muestran hasta cuatro destacados adicionales');
      }
      if (pr3.descripcion !== null && pr3.descripcion !== undefined) {
        if (!esObjeto(pr3.descripcion) || typeof pr3.descripcion.texto !== 'string') inf.error('presentacion.descripcion', 'texto requerido');
        else {
          resolverProcedencia(inf, datos, 'presentacion.descripcion.procedencia', pr3.descripcion.procedencia);
          if (IMPORTE_EN_TEXTO.test(pr3.descripcion.texto)) inf.aviso('presentacion.descripcion', 'la descripción menciona un importe: el precio vive en encargo.precio');
          if (/por confirmar|por definir/i.test(pr3.descripcion.texto)) inf.aviso('presentacion.descripcion', 'la descripción dice «por confirmar»: lo desconocido va a «Qué debe confirmarse»');
          if (EN_REGLA.test(pr3.descripcion.texto)) inf.error('presentacion.descripcion', '«en regla» es una certificación genérica que la pieza no hace (CIERRE-PLANTILLAS §2)');
        }
      }
      if (pr3.titulo !== undefined && (typeof pr3.titulo !== 'string' || !pr3.titulo.trim())) inf.error('presentacion.titulo', 'texto no vacío');
    }

    // Frontera de privacidad sobre todo el árbol.
    recorrerPrivados(inf, datos, 'datos', {
      textoLibre: function (ruta) { return ruta.indexOf('marcaYContacto.contactos') < 0; },
      contacto: function (txt) { var t = txt.replace(/\s/g, ''); return contactos.some(function (c) { return t.indexOf(c) >= 0; }); }
    });
    return cerrar(inf, datos);
  }

  function comprobarSrc(inf, ruta, src) {
    if (typeof src !== 'string' || !src) { inf.error(ruta, 'ruta requerida'); return; }
    if (/^(https?:)?\/\//i.test(src)) inf.error(ruta, 'sin red: la ruta es relativa, blob: o data:, nunca un servidor externo');
    else if (/^[a-z][a-z0-9+.-]*:/i.test(src) && !/^(data:image\/|blob:)/i.test(src)) inf.error(ruta, 'esquema no admitido');
  }

  function vigente(d) { return esObjeto(d) && d.estado === 'VIGENTE'; }

  // Esenciales de la pieza: operación, tipo, zona y precio con moneda. Difusión exige además
  // identidad autorizada y los datos PUB del catálogo (Core no publicaría sin ellos).
  // Un contacto que la ficha puede enlazar (las mismas reglas que enlaceContacto de ficha.js; 1.4.0).
  function enlazable(c) {
    var v = typeof c.valor === 'string' ? c.valor.trim() : '';
    if (c.tipo === 'WHATSAPP') return INTERNACIONAL.test(v);
    if (c.tipo === 'TELEFONO') return /\d{6,}/.test(v.replace(/\D/g, ''));
    if (c.tipo === 'CORREO') return CORREO_SOLO.test(v);
    if (c.tipo === 'WEB') return WEB_SOLO.test(v);
    return false;
  }
  function esenciales(datos) {
    var faltan = [];
    var enc = (datos && datos.encargo) || {}, inm = (datos && datos.inmueble) || {}, u = inm.ubicacion || {}, pr = enc.precio || {};
    if (!vigente(enc.operacion)) faltan.push({ ref: 'encargo.operacion', rotulo: 'Operación', detalle: 'Falta indicar si el encargo es de venta o de alquiler.' });
    if (!vigente(inm.tipo)) faltan.push({ ref: 'inmueble.tipo', rotulo: 'Tipo de inmueble', detalle: 'Falta el tipo de inmueble.' });
    if (!vigente(u.distrito)) faltan.push({ ref: 'inmueble.ubicacion.distrito', rotulo: 'Distrito de la zona', detalle: 'Falta el distrito de la zona aproximada.' });
    if (!vigente(pr.importe)) faltan.push({ ref: 'encargo.precio.importe', rotulo: 'Precio', detalle: 'Falta el importe del precio.' });
    if (!vigente(pr.moneda)) {
      var enFuente = esObjeto(pr.moneda) && pr.moneda.enFuente;
      faltan.push({ ref: 'encargo.precio.moneda', rotulo: 'Moneda del precio', detalle: enFuente ? 'La fuente escribe «' + enFuente + '» junto al precio; falta confirmar si es USD o PEN.' : 'Falta la moneda del precio (USD o PEN).' });
    }
    // Base del alquiler (CAT-R05): donde Core admite alquiler total o por m² (modalidad_precio), sin ella
    // la cifra es ambigua y no se presenta como precio completo (1.1.0).
    var defModalidad = CATALOGO.encargo.modalidad_precio;
    if (vigente(enc.operacion) && enc.operacion.valor === 'ALQUILER' && vigente(inm.tipo) && defModalidad && (defModalidad.operaciones.A || []).indexOf(inm.tipo.valor) >= 0 && !vigente((enc.condiciones || {}).modalidad_precio))
      faltan.push({ ref: 'encargo.condiciones.modalidad_precio', rotulo: 'Base del alquiler', detalle: 'Falta indicar si el alquiler es total al mes o por m² al mes.' });
    var difusion = datos && datos.pieza && datos.pieza.salida === 'DIFUSION_AUTORIZADA';
    // 1.6.0 (instrucción del titular del 20 de octubre): todo encargo tiene un responsable —un agente independiente o un
    // agente de una empresa— y una forma de contactarlo que se pueda enlazar. Sin ellos la pieza queda en BORRADOR; antes
    // sólo la difusión exigía la identidad.
    var mc = datos && datos.marcaYContacto;
    if (!vigente(mc)) faltan.push({ ref: 'marcaYContacto', rotulo: 'Responsable del encargo', detalle: 'Falta el responsable del encargo, un agente independiente o un agente de una empresa, autorizado para esta salida y con su forma de contacto.' });
    else if (!(mc.contactos || []).some(function (c) { return esObjeto(c) && c.autorizadoParaSalida === true && enlazable(c); }))
      faltan.push({ ref: 'marcaYContacto.contactos', rotulo: 'Contacto del responsable', detalle: 'Falta una forma de contactar al responsable que se pueda enlazar: WhatsApp o teléfono con su número completo, correo o web.' });
    if (difusion && vigente(inm.tipo)) {
      var atr = inm.atributos || {};
      Object.keys(CATALOGO.propiedad).forEach(function (clave) {
        var def = CATALOGO.propiedad[clave];
        if (def.activa && def.aplicabilidad[inm.tipo.valor] === 'PUB' && !vigente(atr[clave])) faltan.push({ ref: 'inmueble.atributos.' + clave, rotulo: clave, detalle: 'Core exige este dato para publicar (PUB).' });
      });
    }
    return { estado: faltan.length ? 'BORRADOR' : 'LISTA', faltan: faltan };
  }

  // Estado de la pieza (RF-080; CAT-E17): VISTA_PREVIA o LISTA_PARA_DIFUSION. Lo decide Core al
  // «Obtener para difusión», que revalida permiso, vigencia y PUB (salida DIFUSION_AUTORIZADA). Esta
  // guarda sólo puede bajar la pieza a VISTA_PREVIA, nunca subirla, y compara con la fecha de corte
  // para que el resultado sea reproducible. Cada motivo lleva su código y los datos para redactarlo.
  function estadoPieza(datos) {
    var motivos = [];
    var pz = (datos && datos.pieza) || {}, enc = (datos && datos.encargo) || {};
    if (pz.naturaleza === 'DEMOSTRACION') motivos.push({ codigo: 'DEMOSTRACION', detalle: 'Una demostración no se difunde.' });
    else {
      if (pz.salida !== 'DIFUSION_AUTORIZADA') motivos.push({ codigo: 'SALIDA_PRIVADA', detalle: 'La difusión se obtiene aparte y vuelve a comprobar permiso, vigencia y requisitos de publicación.' });
      if (!(typeof enc.id === 'string' && enc.id)) motivos.push({ codigo: 'SIN_ENCARGO', detalle: 'No hay un encargo seleccionado; la difusión exige un encargo aprobado y vigente.' });
      else if (enc.estado !== 'APROBADO') motivos.push({ codigo: 'ENCARGO_NO_APROBADO', estadoEncargo: enc.estado, detalle: 'El encargo no está aprobado; la difusión exige un encargo aprobado y vigente.' });
      else if (!fechaValida(enc.vigenteHasta)) motivos.push({ codigo: 'VIGENCIA_FALTANTE', detalle: 'Falta la fecha de término del encargo; sin vigencia comprobada la pieza no se difunde.' });
      else if (fechaValida(pz.fechaCorte) && enc.vigenteHasta < pz.fechaCorte) motivos.push({ codigo: 'ENCARGO_VENCIDO', fecha: enc.vigenteHasta, detalle: 'El encargo venció antes de la fecha de corte de la pieza.' });
    }
    if (esObjeto(pz.desactualizada)) motivos.push({ codigo: 'DESACTUALIZADA', fecha: pz.desactualizada.desde, causa: pz.desactualizada.causa, versionVigente: pz.desactualizada.versionVigente, detalle: 'Esta versión de la pieza está desactualizada.' });
    if (esenciales(datos).estado === 'BORRADOR') motivos.push({ codigo: 'ESENCIALES', detalle: 'Faltan datos esenciales.' });
    // 1.3.1 (instrucción del titular del 18 de octubre): la pieza ya no escribe lo que falta. Una incertidumbre
    // material de los casos acordados (contrato §7.2) no queda a la vista del lector, así que la pieza se conserva
    // como vista previa hasta completarla; el profesional la ve en «Qué debe confirmarse» antes de difundir.
    var inc = incertidumbres(datos);
    if (inc.length) motivos.push({ codigo: 'INCERTIDUMBRE_MATERIAL', faltan: inc, detalle: 'Falta confirmar ' + enumerar(inc.map(function (x) { return x.que; })) + '.' });
    return { estadoPieza: motivos.length ? 'VISTA_PREVIA' : 'LISTA_PARA_DIFUSION', motivos: motivos };
  }

  // Casos de incertidumbre material del contrato §7.2 (D-C), sólo los que los datos declaran: qué comprende el
  // precio o una condición del perfil con «siFalta» que llega FALTANTE, una cuota de mantenimiento sin moneda ni
  // período (H-18) y una revisión urbanística no vigente, en conflicto o sin aplicación acreditada. No es un
  // clasificador de materialidad: son los casos acordados.
  function incertidumbres(datos) {
    var inc = [], enc = (datos && datos.encargo) || {}, inm = (datos && datos.inmueble) || {}, pr = enc.precio || {};
    var perfil = PERFILES.perfiles[((datos && datos.presentacion) || {}).perfil];
    var huecos = {}; (inm.fueraDeCatalogo || []).forEach(function (h) { if (esObjeto(h)) huecos[h.hueco] = h; });
    var dato = function (ref) { return /^H-\d\d$/.test(ref) ? huecos[ref] : (CATALOGO.encargo[ref] ? (enc.condiciones || {})[ref] : (inm.atributos || {})[ref]); };
    var faltante = function (d) { return esObjeto(d) && d.estado === 'FALTANTE'; };
    if (faltante(pr.sujeto)) inc.push({ ref: 'encargo.precio.sujeto', que: 'qué comprende el precio' });
    if (perfil) perfil.condiciones.forEach(function (s) {
      if (s.siFalta && faltante(dato(s.ref))) inc.push({ ref: s.ref, que: s.siFalta });
      if (s.formato === 'mantenimiento' && vigente(dato('cuota_mantenimiento')) && !vigente(dato('H-18'))) inc.push({ ref: 'H-18', que: 'la moneda y el período del mantenimiento' });
    });
    var u = inm.usoYConstruccion;
    if (esObjeto(u) && !(u.revision === 'REVISADO_VIGENTE' && u.aplicaAlPredio === true && u.fuente && u.fechaFuente && u.revisadoEl)) inc.push({ ref: 'inmueble.usoYConstruccion', que: 'los parámetros urbanísticos' });
    // 1.5.0 (revisión entregada por el titular el 20 de octubre: en Cieneguilla no queda claro cómo entra el bungalow en los
    // 400 m² y en los conteos; quitar la nota no basta): la proyección declara la incertidumbre sobre el alcance de un valor
    // y la pieza queda en vista previa hasta resolverla. La plantilla no la deduce: sólo la recoge.
    var declaradas = function (grupo, prefijo) { Object.keys(grupo || {}).forEach(function (k) { var d = grupo[k]; if (vigente(d) && typeof d.incertidumbre === 'string' && d.incertidumbre.trim()) inc.push({ ref: prefijo + k, que: d.incertidumbre.trim() }); }); };
    declaradas(inm.atributos, ''); declaradas(huecos, ''); declaradas(enc.condiciones, '');
    if (vigente(pr.importe) && typeof pr.importe.incertidumbre === 'string') inc.push({ ref: 'encargo.precio.importe', que: pr.importe.incertidumbre.trim() });
    return inc;
  }
  function enumerar(l) { return l.length < 2 ? l.join('') : l.slice(0, -1).join(', ') + ' y ' + l[l.length - 1]; }

  function cerrar(inf, datos) {
    var es = inf.errores.length ? null : esenciales(datos), ep = inf.errores.length ? null : estadoPieza(datos);
    return { valido: inf.errores.length === 0, errores: inf.errores, avisos: inf.avisos, estado: es ? es.estado : null, esencialesFaltantes: es ? es.faltan : [], estadoPieza: ep ? ep.estadoPieza : null, motivosVistaPrevia: ep ? ep.motivos : [] };
  }

  return { version: '1.14.0', PRECISIONES: PRECISIONES, INTERNACIONAL: INTERNACIONAL, CORREO_SOLO: CORREO_SOLO, WEB_SOLO: WEB_SOLO, incertidumbres: incertidumbres, validar: validar, esenciales: esenciales, estadoPieza: estadoPieza, definicionDe: definicionDe, procedenciaDe: procedenciaDe, HITOS_PRECIO: HITOS_PRECIO, ESTADOS_ENCARGO: ESTADOS_ENCARGO };
});
