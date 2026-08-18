# E2.2 — La pelota y el orden (2026-08-18)

**Qué cierra:** `DEPENDE_DE_MI` + `lado`/`paso` + **una sola política de despacho
con los seis criterios** de D-E2-1 §3. Tres de las siete interpretaciones que la
auditoría del SPA marcaba como deuda vuelven al backend.

**Qué NO abre:** E2.3. Ni hallazgos, ni Radar, ni interpretación adicional.

---

## 1. Lo que traía hoy, medido antes de tocar nada

`FichaTarea` publicaba `tipo, entidadTipo, entidadId, entidadCodigo,
rutaResolver, descripcion, estado, prioridad, fechaProgramada, diasSinAccion,
fechaVencimiento`.

**Faltaban los tres canónicos.** Y el orden estaba repartido en tres sitios que
no se conocían:

| Dónde | Qué ordenaba |
|---|---|
| `TareaServiceImpl.ORDEN_BANDEJA` | prioridad, luego días sin acción — **dos** criterios |
| `dashboard.ts:724` | `a.prioridad - b.prioridad \|\| b.valor - a.valor` — **otros dos** |
| D-E2-1 §3 | **seis** criterios |

Tres opiniones sobre lo mismo. Un orden que discrepa no falla: enseña las cosas
en un orden distinto del que el dominio decidió, y nadie lo nota.

---

## 2. Lo que cambió

### Tres datos nuevos, y **ninguna columna**

`dependeDeMi`, `lado` y `paso` se **derivan** del tipo del asunto y del tipo de
su entidad, que ya estaban guardados. Persistirlos sería guardar la respuesta a
una pregunta que el tipo ya contesta, y el día que cambiara la regla habría que
reescribir el histórico para que las tareas viejas dijeran lo nuevo.

| Clase | Qué declara |
|---|---|
| `LadoDeLaOperacion` | las dos cadenas: OFERTA (3 pasos) y DEMANDA (4), que no comparten ninguno |
| `NaturalezaDelAsunto` | por tipo: si depende del agente, si es ocasión, si desbloquea |

### El hallazgo que hubo que medir

**De los siete disparadores, uno no depende del agente.**
`POST /contratos/{id}/comision/cobro` y `/comision/movimientos` son **BROKER** en
la matriz operación-rol. La tarea «comisión lista para cobro» llevaba tiempo en
la bandeja del agente sin que pudiera resolverla.

No se dedujo del nombre: se comprobó contra `docs/ai/matriz-operacion-rol.md`,
que está vigilada por su propio test. Sigue en la cola —saber que el dinero
espera importa— pero deja de ocupar uno de los cinco puestos del foco.

### Una sola política, con nombre

`PoliticaDeDespacho`. Los pesos son los del prototipo aprobado
(`docs/ai/prototipos/nucleo-brox.js` §13), copiados con sus valores: cambiarlos
«de paso» al portarlos habría movido el orden que el diseño ya validó a ojo.

```
1 · depende de mí      filtro previo: lo que espera a otro no compite
2 · ventana temporal   max(0, 26 - margen*2)   ·  vencido pesa igual que hoy
3 · ocasión            +30                     ·  puede superar un vencimiento lejano
4 · desbloqueo         +30
5 · antigüedad         min(12, días)           ·  con tope, para que no mande sola
6 · estabilidad        empate → orden anterior ·  y sin pasado, el id
```

**No recorta a cinco.** Devuelve la colección entera ordenada; el tope lo aplica
la pantalla. Recortar aquí escondería asuntos sin decirlo — el fallo que D-F7-2
dejó documentado.

---

## 3. Verificación

```
backend  855 pruebas · 0 fallos · 0 SKIPPED
Angular  565 / 565
```

### Los seis criterios, uno a uno

`PoliticaDeDespachoTest` — 17 casos. Cada uno parte de **dos asuntos
equivalentes** y mueve **un solo hecho**, así que si ese criterio dejara de
pesar, cae ese test y solo ese. Un test que fijara el orden de una lista
concreta pasaría a verde con cualquier comparador que diera la casualidad de
producirlo.

| Criterio | Qué demuestra |
|---|---|
| 1 | lo que espera al broker va detrás **aunque tenga todo a favor** |
| 1 | …y sigue en la lista: no compite, no desaparece |
| 2 | menos margen adelanta · lo vencido no pesa menos que lo de hoy · sin plazo ≠ vence hoy |
| 3 | la ocasión adelanta a un vencimiento a 10 días, **pero no a lo que vence hoy** |
| 4 | desbloquear sube |
| 5 | esperar gana turno · **con tope**: un asunto de hace un año no tapa lo que vence hoy |
| 6 | el orden previo manda en los empates · lo nuevo entra detrás · sin pasado el desempate es determinista · **y la estabilidad no congela un cambio real** |

### Un test viejo que cambió de expectativa, y está bien que cambiara

`laBandejaDevuelveTODASlasTareasAbiertas` afirmaba que con 40 tareas la primera
era **la más rezagada** (48 días). Ya no: el tope de 12 días hace que 37 de ellas
empaten, y con empate manda la estabilidad. Es exactamente el criterio 5
haciendo su trabajo — si la antigüedad no tuviera tope, lo más viejo copaba el
foco para siempre y nada nuevo entraba nunca.

### El gate

`PoliticaDeOrdenUnicaTest` impide que el SPA vuelva a ordenar por los campos con
los que se decide la urgencia (`prioridad`, `diasSinAccion`, `nivelAtencion`,
`fechaVencimiento`, `dependeDeMi`).

**No prohíbe `.sort(`**: ordenar por nombre o por una columna que el usuario
elige no es despacho. Prohibir el verbo entero sería tan inservible como no
prohibir nada, porque el primer caso legítimo obligaría a quitarlo.

Comprobado que muerde: inyectando `a.prioridad - b.prioridad` en `dashboard.ts`
falla con fichero, línea y motivo.

---

## 4. La comprobación visual

`GET /tareas` como el agente `vmora`, 41 asuntos:

```
pos | depende | lado    | paso        | vence      | dias | tipo
  1 | true    | DEMANDA | OPORTUNIDAD |            |    9 | PROPONER_OPORTUNIDAD   <- ocasión (+30)
 ...
 23 | true    | OFERTA  | PROSPECCION | 2026-07-01 |   48 | RECONTACTO             <- vencido, antigüedad topada
 24 | true    | DEMANDA | VISITA      | 2026-07-30 |   19 | VISITA
 ...
 41 | false   | DEMANDA | CONTRATO    |            |   10 | SEGUIMIENTO            <- lo cobra el BROKER
```

Las ocasiones primero, y **el único asunto que no depende del agente, último**.

### Alterar evidencia real mueve el asunto correcto

En `/dashboard`, sin tocar una línea de TypeScript:

```
UPDATE tarea SET fecha_programada = fecha_programada - interval '20 days'
 WHERE id_tarea = 16;      -- REQ-6
```

```
antes                                después
1. REQ-12 · 9 días                   1. REQ-6  · 29 días   <- subió del 5.º al 1.º
2. REQ-8  · 9 días                   2. REQ-12 · 9 días
3. REQ-1  · 9 días                   3. REQ-8  · 9 días
4. REQ-10 · 9 días                   4. REQ-1  · 9 días
5. REQ-6  · 9 días                   5. REQ-10 · 9 días
```

Evidencia restaurada después; REQ-6 volvió a su quinto puesto.

---

## 5. Lo que queda dicho, no arreglado

**`prioridad` sigue viajando pero ya no ordena.** El filtro de la bandeja la usa
y por eso se conserva, pero en pantalla se ven asuntos `MEDIA` por encima de
`ALTA` cuando la política así lo decide — una coincidencia de cartera es MEDIA y
gana por ser ocasión.

No es un error: es la política mandando sobre una etiqueta que se calculó con
otro criterio. Si llega a leerse como incoherente, **la solución es derivar
`prioridad` de la política**, no devolver `prioridad` al comparador. Se anota
para que la próxima persona no elija lo segundo por ser más corto.
