// BROX · Ficha V08 · Formateador único de cifras, importes, áreas y fechas.
// Los datos llegan sin formato; sólo este módulo los convierte en texto. Dos configuraciones:
//  - «es-PE» (propuesta): agrupación con coma y decimales con punto, como CLDR es-PE
//    (175,000 · 108.78). Meses de es-PE («setiembre»).
//  - «v07»: la convención que escribía V07 (175.000 · 108,78).
// La elección es DECISIÓN DEL TITULAR; el formateador es determinista y no depende de la
// versión de ICU del navegador, para que Core (C6) pueda reproducirlo.
(function (raiz, fabrica) {
  if (typeof module === 'object' && module.exports) module.exports = fabrica();
  else raiz.BROX_FORMATO = fabrica();
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  var NBSP = ' ';
  var CONFIG = {
    'es-PE': {
      id: 'es-PE', nombre: 'es-PE · coma de miles y punto decimal', grupo: ',', decimal: '.',
      meses: ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre'],
      mesesCortos: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic']
    },
    v07: {
      id: 'v07', nombre: 'Convención V07 · punto de miles y coma decimal', grupo: '.', decimal: ',',
      meses: ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'],
      mesesCortos: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic']
    }
  };
  var PREDETERMINADA = 'es-PE';
  // Símbolo de presentación por moneda ISO. El sol se escribe «S/»; el dólar conserva su código
  // para no confundirse con «$», que en las fuentes no identifica una moneda.
  var MONEDA = { PEN: 'S/', USD: 'USD' };

  function crear(id) {
    var c = CONFIG[id] || CONFIG[PREDETERMINADA];

    // Agrupa desde cuatro cifras (1,000), igual en ambas convenciones.
    function numero(v, decimales) {
      if (typeof v !== 'number' || !isFinite(v)) throw new TypeError('número esperado');
      var d = typeof decimales === 'number' ? decimales : (Number.isInteger(v) ? 0 : (Math.abs(Math.round(v * 10) - v * 10) < 1e-9 ? 1 : 2));
      var fijo = Math.abs(v).toFixed(d), partes = fijo.split('.');
      var entero = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, c.grupo);
      return (v < 0 ? '−' : '') + entero + (partes[1] ? c.decimal + partes[1] : '');
    }
    // Un importe conserva céntimos cuando los tiene; si es entero no se añaden «.00».
    function importe(x) {
      if (!x || MONEDA[x.moneda] === undefined) throw new TypeError('importe con moneda ISO esperado');
      var dec = Math.round(x.importe * 100) % 100 === 0 ? 0 : 2;
      return MONEDA[x.moneda] + NBSP + numero(x.importe, dec);
    }
    function partesImporte(x) { var dec = Math.round(x.importe * 100) % 100 === 0 ? 0 : 2; return { simbolo: MONEDA[x.moneda], cifra: numero(x.importe, dec) }; }
    function conUnidad(v, unidad, decimales) { return numero(v, decimales) + (unidad ? NBSP + unidad : ''); }
    function fecha(iso) { var p = String(iso).split('-').map(Number); return { a: p[0], m: p[1], d: p[2] }; }
    function fechaLarga(iso) { var f = fecha(iso); return f.d + ' de ' + c.meses[f.m - 1] + ' de ' + f.a; }
    function fechaCorta(iso) { var f = fecha(iso); return f.d + NBSP + c.mesesCortos[f.m - 1] + NBSP + f.a; }
    function fechaMono(iso) { var f = fecha(iso); return String(f.d).padStart(2, '0') + ' ' + c.mesesCortos[f.m - 1].toUpperCase() + ' ' + f.a; }
    // Piso de una unidad (TEXTO en Core): 1 → «1.er», 2 → «2.º», 3 → «3.er»; un código no
    // numérico («S1») se presenta tal como llega.
    function ordinalPiso(texto) {
      var t = String(texto).trim();
      if (!/^\d+$/.test(t)) return t;
      var n = Number(t);
      return n + (n === 1 || n === 3 ? '.er' : '.º');
    }
    // Nivel de una unidad (1.5.0; revisión entregada por el titular el 20 de octubre: «S1 Nivel» obligaba a interpretar una
    // abreviatura): «S1» es el primer sótano y se escribe «Sótano 1»; un número es un piso («2.º piso»); lo demás, tal como llega.
    function nivel(texto) {
      var t = String(texto).trim(), s = /^S(\d{1,2})$/i.exec(t);
      if (s) return 'Sótano' + NBSP + Number(s[1]);
      return /^\d+$/.test(t) ? ordinalPiso(t) + NBSP + 'piso' : t;
    }
    function plural(n, uno, varios) { return n === 1 ? uno : varios; }
    return { id: c.id, nombre: c.nombre, numero: numero, importe: importe, partesImporte: partesImporte, conUnidad: conUnidad, fechaLarga: fechaLarga, fechaCorta: fechaCorta, fechaMono: fechaMono, ordinalPiso: ordinalPiso, nivel: nivel, plural: plural, NBSP: NBSP };
  }

  return { crear: crear, configuraciones: Object.keys(CONFIG), predeterminada: PREDETERMINADA, MONEDA: MONEDA };
});
