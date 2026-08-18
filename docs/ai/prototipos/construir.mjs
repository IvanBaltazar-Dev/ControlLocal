/* ====================================================================
   CONSTRUIR — inlina el núcleo canónico en los dos prototipos
   --------------------------------------------------------------------
   node docs/ai/prototipos/construir.mjs

   Los artefactos publicados tienen que ser autocontenidos (la CSP del
   visor bloquea cualquier host externo), así que el núcleo no se puede
   servir como archivo aparte: se copia dentro de los dos HTML. Este
   script es lo que garantiza que la copia sea LA MISMA en los dos, byte a
   byte, y que venga de un único archivo bajo control de versiones.

   Después de tocar `nucleo-brox.js`, ejecuta esto y vuelve a publicar.
   `pruebas-nucleo.js` falla si el bloque inlinado no coincide con la
   fuente, así que un olvido se ve enseguida.
   ==================================================================== */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DIR = dirname(fileURLToPath(import.meta.url));
const PROTOTIPOS = ["inicio.html", "indicadores.html"];
const ABRE = '<script data-nucleo="brox">';
const CIERRA = "</script>";
const AVISO = "/* GENERADO POR construir.mjs — NO EDITAR AQUI.\n" +
  "   Fuente única: docs/ai/prototipos/nucleo-brox.js */\n";

const nucleo = readFileSync(join(DIR, "nucleo-brox.js"), "utf8");
const bloque = ABRE + "\n" + AVISO + nucleo.trimEnd() + "\n" + CIERRA;

let cambios = 0;
for (const archivo of PROTOTIPOS) {
  const ruta = join(DIR, archivo);
  const antes = readFileSync(ruta, "utf8");
  let despues;

  const i = antes.indexOf(ABRE);
  if (i >= 0) {
    const j = antes.indexOf(CIERRA, i);
    if (j < 0) throw new Error(`${archivo}: el bloque del núcleo está sin cerrar`);
    despues = antes.slice(0, i) + bloque + antes.slice(j + CIERRA.length);
  } else {
    /* Primera vez: va justo antes del script de la pantalla, que es quien
       lo consume. Se busca el ÚLTIMO <script> del archivo para no colarlo
       dentro de las definiciones SVG. */
    const k = antes.lastIndexOf("<script>");
    if (k < 0) throw new Error(`${archivo}: no encuentro dónde insertar el núcleo`);
    despues = antes.slice(0, k) + bloque + "\n\n" + antes.slice(k);
  }

  if (despues !== antes) {
    writeFileSync(ruta, despues);
    cambios++;
    console.log(`  actualizado  ${archivo}`);
  } else {
    console.log(`  ya al día    ${archivo}`);
  }
}

const lineas = nucleo.split("\n").length;
console.log(`\nnúcleo: ${lineas} líneas · ${(nucleo.length / 1024).toFixed(1)} KB · ${cambios} archivo(s) tocado(s)`);
console.log("comprueba con: node docs/ai/prototipos/pruebas-nucleo.js");
