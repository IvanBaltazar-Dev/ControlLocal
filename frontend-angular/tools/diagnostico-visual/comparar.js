const fs = require('fs');
// El ancho, el alto y los margenes `auto` los decide la MEDIDA DEL TEXTO, y
// esa varia entre ejecuciones segun haya cargado ya la fuente: el control
// (mismo codigo dos veces) las mueve igual. Se excluyen.
const RUIDO = new Set(['width', 'height', 'margin-left', 'margin-right']);
// El nombre de la animacion lleva el id del componente que la declara. Lo que
// importa no es el hash sino DOS cosas: que sea la misma animacion, y que
// venga prefijada (sin prefijo no resuelve y la regla se queda muda).
function normaliza(prop, valor) {
  if (prop !== 'animation-name') return valor;
  return valor
    .split(',')
    .map((v) => {
      const t = v.trim();
      if (t === 'none') return 'none';
      const m = t.match(/^_ngcontent-[a-z-]+-c\d+_(.+)$/);
      return m ? `PREFIJADA:${m[1]}` : `SIN-PREFIJO:${t}`;
    })
    .join(',');
}
function leer(f) {
  const m = new Map();
  for (const linea of fs.readFileSync(f, 'utf8').split(/\r?\n/)) {
    if (!linea.trim()) continue;
    const p = linea.split('|').map((x) => x.trim());
    if (p[1] === 'TOTAL') continue;
    const props = Object.fromEntries(
      p[4].split(';').map((x) => { const i = x.indexOf('='); return [x.slice(0, i), x.slice(i + 1)]; })
        .filter(([k]) => !RUIDO.has(k))
        .map(([k, v]) => [k, normaliza(k, v)]),
    );
    m.set(`${p[0]}:${String(p[1]).padStart(4, '0')}`, { clases: p[2], tag: p[3], props });
  }
  return m;
}
const [fa, fd] = process.argv.slice(2);
const a = leer(fa), d = leer(fd);
let diferencias = 0;
for (const [k, va] of a) {
  const vd = d.get(k);
  if (!vd) { console.log(`FALTA ${k}`); diferencias++; continue; }
  const cambios = Object.keys(va.props).filter((p) => va.props[p] !== vd.props[p]);
  if (cambios.length) {
    diferencias++;
    console.log(`  ${k} [${va.clases}] <${va.tag}>`);
    cambios.forEach((p) => console.log(`      ${p}: ${va.props[p]}  ->  ${vd.props[p]}`));
  }
}
console.log(`\n>>> ${fa} vs ${fd}: ${diferencias} elementos con estilo distinto (de ${a.size})`);
