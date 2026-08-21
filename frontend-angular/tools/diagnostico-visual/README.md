# Huella de estilo calculado

Instrumento para responder a **una** pregunta durante un refactor de estilos:
*¿ha cambiado algo de lo que se ve?*

Vuelca el `getComputedStyle` de cada elemento de una pantalla, en varios estados, y compara dos
ejecuciones. Sirve cuando partes un componente, mueves reglas de sitio o tocas la encapsulación —
donde el build compila, los tests pasan y aun así la pantalla no es la misma.

## Esto NO es un gate, y no debe convertirse en uno

No está en la suite y no debería estarlo:

- **Mide cosas que no son deterministas.** El ancho, el alto y los márgenes `auto` los decide la
  medida del texto, y esa varía entre ejecuciones según haya cargado ya la fuente. Un control —el
  mismo código dos veces— mueve esas propiedades igual que un cambio real. `comparar.js` las excluye
  por eso; una prueba permanente que las mirara sería intermitente.
- **Es caro.** Tres montajes completos del Inicio y ~15 000 valores por ejecución, para responder a
  una pregunta que solo se hace durante un refactor.
- **Su salida hay que interpretarla.** Distinguir ruido de señal exige mirar el control. Un gate que
  hay que interpretar cada vez no es un gate.

Lo que sí se convierte en prueba permanente es **el hallazgo concreto**, en pequeño y determinista.
El primer uso encontró que una `@keyframes` había dejado de corresponder al cruzar una frontera de
componente; lo que quedó en la suite son dos `it` en `dashboard.spec.ts` que comprueban que toda
animación del Radar apunta a una `@keyframes` viva. La huella encuentra; la suite retiene.

## Cómo se usa

1. Pega `bloque-huella.ts` al final de `src/app/features/dashboard/dashboard.spec.ts`. Usa lo que ese
   fichero ya declara (`TAREA`, `HALLAZGO`, `carga`, `sesion`, `Dashboard`, los servicios), así que no
   necesita imports nuevos. Ajusta los estados que monta si la pantalla que te interesa no es el
   Inicio.
2. Ejecuta la suite y quédate con las líneas marcadas:

   ```bash
   CHROME_BIN="$USERPROFILE/.cache/puppeteer/chrome/win64-150.0.7871.24/chrome-win64/chrome.exe" \
     npx ng test --watch=false --browsers=EdgeHeadlessCI 2>&1 \
     | sed 's/\x1b\[[0-9;]*m//g' | grep -a '##H##' | sed 's/.*##H## //' > antes.txt
   ```

3. Aplica el refactor —o cambia de commit— y repite en `despues.txt`.
4. Compara:

   ```bash
   node comparar.js antes.txt despues.txt
   ```

**Haz siempre el control.** Antes de creerte una diferencia, ejecuta dos veces el **mismo** código y
compara esas dos salidas. Si el control no da cero, lo que estás viendo es ruido del entorno y no del
cambio.

Trabaja en un `git worktree` aparte, no en el árbol vivo: hay que ir y volver entre dos commits, y el
procedimiento deja el árbol a medias si algo se interrumpe. `node_modules` se puede enlazar con
`mklink /J` en vez de reinstalar.

## Cómo leer la salida

`comparar.js` imprime un bloque por elemento cuyo estilo calculado difiere, con la propiedad y los dos
valores. Al final, el recuento.

Dos diferencias son **esperables** al partir un componente y no significan que algo se haya roto:

- **La etiqueta cambia** donde un `<div>` pasa a ser el anfitrión de un componente
  (`<div class="reco">` → `<cl-radar-resolver class="reco">`). La clave de comparación ignora el
  nombre de etiqueta a propósito.
- **`animation-name` cambia de hash.** Angular le antepone el identificador del componente que
  declara la `@keyframes`, y ese identificador depende del contenido de la hoja. `comparar.js`
  normaliza a `PREFIJADA:nombre`. Lo que **sí** es un fallo es `SIN-PREFIJO:nombre`: significa que la
  regla usa una animación declarada en otro componente, no resuelve, y no corre.

## Mantenimiento

Ninguno programado. `tools/` está fuera de los `tsconfig`, así que `bloque-huella.ts` no se compila ni
se typechequea mientras vive aquí: si `dashboard.spec.ts` cambia sus fixtures, esto se entera al
pegarlo, no antes. Es aceptable para lo que es — se toca cada varios meses, en un refactor, y lo
primero que haces al usarlo es ejecutarlo.
