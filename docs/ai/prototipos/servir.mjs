/* Servidor estático mínimo para abrir los prototipos en el navegador.
   node docs/ai/prototipos/servir.mjs   →   http://localhost:4310/inicio.html
   Solo sirve esta carpeta y solo .html/.js: no es un servidor de verdad ni
   pretende serlo. Los prototipos publicados no lo necesitan -- son un solo
   archivo autocontenido --; esto es para revisarlos antes de publicar. */

import { createServer } from "node:http";
import { readFileSync, existsSync } from "node:fs";
import { dirname, join, extname, basename } from "node:path";
import { fileURLToPath } from "node:url";

const DIR = dirname(fileURLToPath(import.meta.url));
const PUERTO = 4310;
const TIPOS = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8" };

createServer((req, res) => {
  const nombre = basename(decodeURIComponent((req.url || "/").split("?")[0])) || "inicio.html";
  const archivo = nombre === "" || nombre === "/" ? "inicio.html" : nombre;
  const ruta = join(DIR, archivo);
  const ext = extname(ruta);

  if (!TIPOS[ext] || !existsSync(ruta)) {
    res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
    res.end("No está: " + archivo + "\nPrueba /inicio.html o /indicadores.html");
    return;
  }
  /* Los prototipos son un `<body>` suelto: el visor de artefactos les pone
     el esqueleto. Aquí se lo ponemos igual para que se vean como se verán. */
  let cuerpo = readFileSync(ruta);
  if (ext === ".html") {
    cuerpo = '<!doctype html><html><head><meta charset="utf8">' +
      '<meta name="viewport" content="width=device-width,initial-scale=1">' +
      '<style>:root{color-scheme:light}body{margin:0;padding:0;font:14px -apple-system,BlinkMacSystemFont,sans-serif;background:#faf9f5;color:#141413}img{max-width:100%}</style>' +
      "</head><body>\n" + cuerpo.toString("utf8") + "\n</body></html>";
  }
  res.writeHead(200, { "content-type": TIPOS[ext], "cache-control": "no-store" });
  res.end(cuerpo);
}).listen(PUERTO, () => {
  console.log("prototipos en http://localhost:" + PUERTO + "/inicio.html");
  console.log("                http://localhost:" + PUERTO + "/indicadores.html");
});
