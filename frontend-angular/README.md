# BROX Web — la SPA

Angular 20 con componentes *standalone* y signals. Consume la API en
`http://localhost:8090/controllocal/Api`.

```bash
npm --prefix frontend-angular start
```

Arranca en `http://localhost:4200`. Necesita la API levantada
([`backend-spring/README.md`](../backend-spring/README.md)) y se entra con las credenciales del
seed.

## Cómo está organizada

| Carpeta | Qué contiene |
|---|---|
| `core/` | Autenticación y guardas, clientes HTTP (`core/api`), formato, política comercial y reglas transversales |
| `features/` | Una carpeta por pantalla, cargada de forma diferida desde `app.routes.ts` |
| `layout/` | El *shell*: navegación por rol, campana de alertas |
| `shared/` | Piezas reutilizables: filtros, KPI, gráficos, paginación, visor de documentos |

**El dashboard es la home (`/`)**, no una pantalla más, y la bandeja de tareas vive dentro. Por eso
no existen entradas de menú separadas para «Inicio», «Dashboard» o «Mis tareas».

## Probar

```bash
CHROME_BIN="$USERPROFILE/.cache/puppeteer/chrome/win64-150.0.7871.24/chrome-win64/chrome.exe" npx ng test --watch=false --browsers=EdgeHeadlessCI
```

**Sin `CHROME_BIN`, `ng test` se cuelga en silencio**: no hay Chrome ni Edge instalados en esta
máquina, así que Karma espera para siempre un navegador que nunca conecta, sin error, sin timeout y
sin proceso. Y el lanzador se llama `EdgeHeadlessCI` —es el único que registra `karma.conf.js`—: un
nombre distinto falla con `Cannot load browser`.

## Cuatro trampas que ya costaron tiempo

- **`ng test` verde no significa que compile.** Los specs se compilan con la configuración de
  *desarrollo*, que no lleva `budgets`; el tope de estilo por componente (16 kB) solo se aplica en
  `ng build --configuration production`. Así viajó una build rota durante cuatro commits con la
  suite en verde. **Si tocas una hoja de estilos o `angular.json`, corre también la build de
  producción.** El porqué del tope está en
  [`docs/ai/decision-presupuesto-de-estilos-de-componente.md`](../docs/ai/decision-presupuesto-de-estilos-de-componente.md).
- **Angular acota los nombres de `@keyframes` por componente.** Una regla que use una animación
  declarada en la hoja de *otro* componente sale con el nombre pelado, no encaja con nada y **la
  animación simplemente no corre**: sin error de compilación, sin aviso, y `animation-name` sigue
  leyéndose como un nombre. Si mueves una regla con `animation`, **mueve o duplica su `@keyframes`
  con ella**.
- **Un campo nulo no viaja.** Jackson está configurado `NON_NULL`, así que en TypeScript llega como
  `undefined`, no como `null`: `x === null` lo pasa por alto —y por ahí salió a pantalla un «19 de
  undefined»—. Declara los campos anulables como opcionales (`campo?: number | null`) y compara con
  `== null`.
- **No compiles mientras corre una suite E2E.** Las de búsqueda afirman latencias en la misma
  máquina y un `ng build` en paralelo las tumba solo por tiempos.

## Cómo se decide la interfaz

Las pantallas de inteligencia no se improvisan: su diseño está decidido y escrito.

| Documento | Qué gobierna |
|---|---|
| [`decision-inicio-foco-y-resolucion.md`](../docs/ai/decision-inicio-foco-y-resolucion.md) | El Inicio: el foco, el Radar y cómo se resuelve un asunto |
| [`decision-indicadores-comerciales.md`](../docs/ai/decision-indicadores-comerciales.md) | Qué mide cada indicador y cómo se dibuja |
| [`decision-lenguaje-natural-de-negocio.md`](../docs/ai/decision-lenguaje-natural-de-negocio.md) | Cómo se escribe el texto: el hecho con su cifra y su fecha, sin metáforas |
| [`matriz-operacion-rol.md`](../docs/ai/matriz-operacion-rol.md) | Quién puede llamar a qué. Léela antes de construir cualquier pantalla con roles |

Dos reglas que se rompen sin querer:

- **El frontend no reclasifica: dibuja.** Los umbrales y la interpretación viven en el dominio y
  llegan ya resueltos en el cable. Si necesitas un número interpretado, el sitio es el backend.
- **Ninguna pantalla lleva «Exportar PDF»** (D-F5-1). Lo que se exporta es CSV, que es dato y no
  maquetación.

Hay además dos maquetas de alta fidelidad con una única fuente de datos en
[`docs/ai/prototipos/`](../docs/ai/prototipos/), y un instrumento para comparar el estilo calculado
antes y después de un refactor en [`tools/diagnostico-visual/`](tools/diagnostico-visual/).
