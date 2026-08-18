# Prototipos de BROX · fuente canónica

Las dos maquetas de alta fidelidad de E2, con **una sola fuente de datos**.

| Archivo | Qué es |
|---|---|
| `nucleo-brox.js` | **La fuente canónica.** Entidades, política, KPI, embudos, asuntos y agenda. Todo hecho existe aquí una vez. |
| `inicio.html` | D-E2-1 · Inicio, foco y resolución. Proyecta el núcleo. |
| `indicadores.html` | D-E2-2 · Indicadores comerciales. Proyecta el mismo núcleo. |
| `pruebas-nucleo.js` | Las invariantes que impiden que las dos pantallas se contradigan. |
| `construir.mjs` | Inlina el núcleo en los dos HTML (byte a byte el mismo bloque). |
| `servir.mjs` | Servidor estático para verlos antes de publicar. |

## El ciclo

```bash
node docs/ai/prototipos/construir.mjs && node docs/ai/prototipos/pruebas-nucleo.js
```

1. Se edita **`nucleo-brox.js`** — nunca los datos dentro de un HTML.
2. `construir.mjs` copia el núcleo dentro de los dos prototipos. Hace falta
   porque un artefacto publicado tiene que ser autocontenido: la CSP del visor
   bloquea cualquier host externo, así que el núcleo no puede servirse aparte.
3. `pruebas-nucleo.js` comprueba las invariantes **y** que el bloque inlinado
   coincide con la fuente: si te olvidas del paso 2, falla.

Para verlos en el navegador:

```bash
node docs/ai/prototipos/servir.mjs
```

→ `http://localhost:4310/inicio.html` y `/indicadores.html`. También está como
`prototipos` en `.claude/launch.json`.

## Las cinco reglas que sostienen esto

1. **Ninguna duración se escribe.** Se guardan fechas y se derivan los días
   contra `FECHA_REF`. Es lo que hacía que la misma espera saliera 11 en una
   caja y 12 en la de al lado.
2. **El ritmo se calcula una vez**, en `ritmoDe`. Las dos pantallas reciben el
   mismo objeto —`actual`, `metaPeriodo`, `metaEsperadaAHoy`,
   `proyeccionCierre`, `porcentajeProyectado`, `estadoRitmo`— y solo eligen con
   qué color lo dibujan.
3. **El vocabulario de ritmo no se mezcla con el de severidad.**
   `EN_RITMO · ATENCION · FUERA_DE_RITMO · SIN_BASE` describen un KPI;
   `ALTA · MEDIA` describen un asunto de la cola. `alto`/`medio`/`bueno`
   significaban las dos cosas y por ahí se coló la contradicción.
4. **Las cadenas suman.** La meta del equipo es la suma de las de sus agentes;
   el embudo sale de los mismos contadores que los KPI. `visitas ≥ solicitudes
   ≥ aprobadas ≥ contratos` se cumple por construcción, no por una regla de
   presentación.
5. **Cada asunto tiene identidad** (`asuntoId` + `entidadTipo` + `entidadId`).
   Una dirección no identifica nada: un inmueble participa en varios procesos a
   la vez, y por eso «sale de tu foco» nombra un asunto, no una calle.

## Criterio de aceptación

> Si cambias una sola fecha, visita, captación, solicitud, contrato o meta en
> `nucleo-brox.js`, todas las representaciones afectadas de las dos pantallas
> cambian coherentemente **sin editar ningún otro dato a mano**.

Las pruebas lo vigilan: mueven `FECHA_REF` un día y comprueban que todas las
esperas avanzan y todas las ventanas se acortan.

## Y en producción

El núcleo es una maqueta, no una capa de la aplicación. Su equivalente real es
el backend: la salida canónica de KPI y la regla de que `estadoRitmo` se decide
en el dominio están en la **Tanda 5** de
[`estado-backend-para-el-inicio.md`](../estado-backend-para-el-inicio.md).
Angular no reclasifica: dibuja.
