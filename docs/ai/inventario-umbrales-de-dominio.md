# E1.1B · Inventario de umbrales — antes de centralizar nada

**Fecha:** 2026-08-10
**Etapa:** E1 · Instrumentación y políticas
**Regla que lo motiva:** R-07 — ningún componente Angular decide qué significa
"estancado", "por vencer", "riesgo" o "caliente".

Este documento es el **paso 1**: inventariar y clasificar. La centralización va
después, y solo sobre lo clasificado como **regla de negocio**.

---

## Criterio de clasificación

| Clase | Definición | Dónde debe vivir |
|---|---|---|
| **Hecho** | se deriva del dato sin decidir nada (`vence en 18 días`) | donde sea; no es un umbral |
| **Regla de negocio** | decide qué significa un número (`>7 días = riesgo`) | política de dominio nombrada |
| **Presentación** | tamaño de página, longitud de recorte, ancho de gráfico | donde está, correcto |

---

## Hallazgo 1 — `DIAS_RECONTACTO = 7` está **cuadruplicado**

Es el umbral más importante del sistema —define cuándo un seguimiento pasa a
estar vencido— y hoy existen **cuatro copias independientes**:

| Copia | Ubicación |
|---|---|
| 1 | `TareaServiceImpl:57` — dispara la tarea de recontacto |
| 2 | `IndicadorServiceImpl:83` — decide `recontactosVencidos` |
| 3 | `AlertaServiceImpl:30` — emite la alerta |
| 4 | `dashboard.ts:589` — pinta el KPI en ámbar (`> 7`) |

El propio código sabe que están acopladas: `IndicadorServiceImpl:82` dice
*"Mismo umbral que la bandeja de F7, a proposito: los dos numeros deben
cuadrar"*. Es decir, **la coherencia depende hoy de un comentario y de la
disciplina de quien edite**. Cambiar la política de recontacto exige tocar cuatro
archivos en dos lenguajes, y nada rompe si uno se queda atrás: la bandeja diría
una cosa y el indicador otra, en silencio.

Es exactamente el patrón que ya costó caro con V40 (un CHECK y un productor
divergiendo sin que nada avisara).

---

## Hallazgo 2 — En Angular hay **un solo** umbral numérico de negocio

Menos de lo temido. Es este:

```ts
// dashboard.ts:589
tono: op.diasPromedioSinSeguimiento > 7 ? 'ambar' : 'azul'
```

Y es, precisamente, la cuarta copia del 7.

---

## Hallazgo 3 — La **jerarquía de severidad** sí vive en el componente

Ocho ternarios en `dashboard.ts` deciden qué duele más:

| Línea | Decisión |
|---|---|
| 455 | `captacionesPorRevisar > 0` → **ámbar** |
| 461 | `solicitudesPorEvaluar > 0` → **rojo** |
| 528, 556, 583 | `recontactosVencidos > 0` → **rojo** |
| 534, 562, 601 | `solicitudesSinCierre > 0` → **ámbar** |

El `> 0` no es un umbral configurable —"si hay alguno, atención" es un hecho—,
pero **decidir que una solicitud sin evaluar es roja y una captación por revisar
es ámbar SÍ es criterio de negocio**: dice qué se atiende primero. Está repartido
en ocho sitios sin nombre, y en E2 va a multiplicarse cuando el triaje clasifique
cinco activos.

**Propuesta:** no centralizar el `> 0`, sino la **severidad por concepto**
(`SOLICITUD_SIN_EVALUAR = CRITICO`, `CAPTACION_POR_REVISAR = ATENCION`…), que es
lo que de verdad se está decidiendo.

---

## Hallazgo 4 — Una validación que solo existe en el frontend

```ts
// reasignaciones-captacion.ts:248 y :265
if (motivo.length < 10) { … }
```

**El backend no la tiene.** Comprobado: no hay ninguna comprobación de longitud
mínima de motivo en el servicio de reasignación. La API acepta un motivo de tres
caracteres; solo la UI lo impide.

No es un umbral de presentación mal colocado: es una **regla de negocio que se
puede saltar llamando al API**. Va a la política, y además al servicio.

---

## Inventario completo

### Reglas de negocio — a centralizar

| Regla | Valor | Hoy en | Unidad |
|---|---|---|---|
| Recontacto vencido | **7** | ×4 (ver hallazgo 1) | días |
| Visita próxima | **3** | `TareaServiceImpl:58` | días |
| Cadencia de reporte al propietario | **15** | `TareaServiceImpl:60` | días |
| Puntaje mínimo para proponer oportunidad | **60** | `TareaServiceImpl:62` | puntos |
| Duración por defecto del encargo | **6** | `ProspeccionServiceImpl:52` | meses |
| Comisión máxima admitida | **200** | `CondicionesEconomicas:13` | % |
| Longitud mínima del motivo de reasignación | **10** | solo `reasignaciones-captacion.ts` | caracteres |
| Severidad por concepto | rojo/ámbar | 8 ternarios en `dashboard.ts` | — |

### Topes de consulta — **no** son reglas de negocio

`TOPE_DESEMPENO=8`, `ULTIMOS_CIERRES=8`, `MAX_PROXIMAS=8`, `TOPE_BARRIDO=500`,
`MAXIMO_POR_PAGINA=100`, `MAX_PAGE_SIZE=24`, `MAXIMO_EXPORTACION=1000`.

Acotan el tamaño de una respuesta, no el significado de un dato. Se quedan donde
están; conviene que sigan siendo constantes nombradas y no literales.

### Política de seguridad — ya centralizada, **no tocar**

`PoliticaContrasenas` (largo 12, historial 5, temporal 14), `Totp` (6 dígitos,
30 s, 1 paso atrás), `BloqueoAccesos` (20 fallos), `BloqueoMfa` (umbral 5),
`PasswordHasher` (100 000 iteraciones), `CifradoSecretos`.

Ya cumplen el espíritu de R-07 en su propio dominio: nombre estable, un solo
sitio, con su porqué escrito. **Este trabajo no las toca.**

### Presentación — correcto donde está

`POR_PAGINA=10` (×6 pantallas), `CANDIDATOS=20` (×3 formularios), `TAMANO=20`
(campana), `ANCHO=640` (gráfico), `MAX_DIGITOS=15`, `DURACION_AVISO_MS=4000`,
`total() > 9 ? '9+'`, los `slice(0,2)` de iniciales.

### Mapeo estado → tono: deuda distinta

Diez pantallas traducen un código de estado a un tono
(`codigo === 'O' ? 'aviso' : ''`). **No son umbrales** —el dominio ya clasificó—
pero están duplicados con criterios que no siempre coinciden. Es deuda de
consistencia visual, no de política. Se anota; no entra en E1.

---

## Forma propuesta para la política

Un único sitio en `service/soporte`, con lo que R-07 y el plan exigen por regla:
**nombre estable, descripción funcional, valor vigente, unidad, alcance
(`GLOBAL` ahora, `ORGANIZACION` después) y versión**.

Y dos consecuencias que conviene aceptar juntas:

1. **La política se expone al frontend.** Angular no puede dejar de conocer el 7
   sin que alguien se lo diga: o el backend devuelve el hecho ya clasificado
   (`seguimientoEnRiesgo: true`), o devuelve la política y el front la aplica. La
   primera opción es la que cumple R-07 de verdad; la segunda solo mueve el
   número de sitio.
2. **Un test por regla, por nombre.** Es lo que impide que vuelva a haber cuatro
   copias: si alguien reintroduce un literal, el test lo señala.

---

## Lo que NO haría en E1

Convertir esto en configuración por organización. El plan dice preparar la
estructura, no implementarla. Con una sola organización real, la configuración
multi-tenant es complejidad sin usuario — y el alcance ya está previsto en el
propio contrato de la política.

---

# Cierre — qué se hizo (2026-08-10)

`PoliticaComercial` (`service/soporte/`) es ahora el único sitio donde se decide
qué significa un número. Siete reglas, cada una con nombre estable, significado,
valor, unidad, alcance y versión:

| Nombre | Valor | Antes vivía en |
|---|---|---|
| `recontacto.dias` | 7 días | **5 copias** (ver abajo) |
| `visita.dias-de-aviso` | 3 días | `TareaServiceImpl` |
| `reporte-propietario.dias` | 15 días | `TareaServiceImpl` |
| `coincidencia.puntaje-minimo` | 60 puntos | `TareaServiceImpl` |
| `encargo.meses-por-defecto` | 6 meses | `ProspeccionServiceImpl` + `captacion-form.ts` |
| `comision.porcentaje-maximo` | 200 % | `CondicionesEconomicas` |
| `reasignacion.caracteres-minimos-del-motivo` | 10 caracteres | **solo el formulario de Angular** |

## Eran cinco copias del 7, no cuatro

El inventario buscó las que se **usaban**. El gate encontró una quinta:
`Prospeccion.DIAS_RECONTACTO = 7`, pública, con javadoc y **muerta** — nadie la
leía. Es la peor clase de copia: la que alguien reutiliza de buena fe seis meses
después porque parece la definición oficial. Retirada.

## La validación del motivo ya no depende del formulario

`PoliticaComercial.exigirMotivoDeReasignacion` la aplican los **dos** puntos de
reasignación (`CaptacionServiceImpl` y `AsignacionServiceImpl`). La API dejó de
aceptar un motivo de tres caracteres. El formulario sigue avisando antes de
enviar —es mejor experiencia— pero ya no es lo único que protege la regla.

## La severidad por concepto se nombró, y con ella el orden

`Concepto` + `NivelAtencion` sustituyen a los ocho ternarios de `dashboard.ts`.
El hallazgo al centralizarlos: **las escalas de los dos roles se contradecían**
—los recontactos vencidos eran lo primero para el administrador y lo cuarto para
el broker—. Ahora hay un solo orden, y sale del dominio.

El `> 7` desapareció de Angular sin moverse de sitio: el backend emite
`senales[]` con `valor`, `nivelAtencion`, `requiereAtencion` y `prioridad`; la
pantalla elige el color y el rótulo. Es la opción 1 de las dos que planteaba este
documento, que es la que cumple R-07 de verdad.

## Lo que impide la sexta copia

`PoliticaUnicaTest` (gate del módulo `app`) recorre el código fuente del backend
**y del SPA** buscando las formas en que estas reglas se re-escriben a mano
—`minusDays(7)`, `plusMonths(6)`, `< 10`, las constantes retiradas por nombre— y
falla nombrando archivo, línea y la alternativa. Ignora comentarios a propósito:
este repositorio los usa para explicar las reglas y el gate se dispararía con su
propia documentación.

No persigue el número suelto: un 7 puede ser cualquier cosa. Persigue el **7
aplicado como plazo**.

## Espejo en el SPA, declarado

Dos valores quedan también en `frontend-angular/src/app/core/politica-comercial.ts`
porque el formulario los necesita **antes** de enviar: el mínimo del motivo y los
meses del encargo. Están documentados como espejo, el backend vuelve a aplicar
las dos reglas, y `PoliticaComercialTest` recuerda por su nombre el archivo a
tocar si cambian.

## Lo que se anotó y no entró

El **mapeo estado → tono** de diez pantallas sigue igual: no son umbrales —el
dominio ya clasificó— sino deuda de consistencia visual. Y la configuración por
organización sigue sin implementarse, como decía la sección anterior.

### Hallazgo nuevo, fuera del alcance de E1

`dashboard.ts` tiene un respaldo heredado del Blazor que **no es un umbral pero
sí una decisión de negocio en la pantalla**:

```ts
i.conversionPropia > 0 ? i.conversionPropia : (i.desempeno[0]?.conversion ?? 0)
```

Si la conversión por cohorte del periodo es 0, la home muestra en su lugar **la
del primero de la tabla de desempeño**. Para un agente sin cohorte eso significa
ver la cifra del que más cerró presentada como propia.

**Resuelto en E2.0** (2026-08-10): `conversionPropia` viaja **nula** cuando no
hay cohorte —sin captaciones en el periodo no se convirtió nada *porque no había
nada que convertir*— y la pantalla lo dice en vez de pintar un número ajeno. Es
el único numérico nulable del resumen, y está así a propósito.
