# Registrar no es encargar · V75

**Cerrado el 2026-08-21.** Migración `V75__una_propiedad_puede_no_estar_encargada.sql`.

Microcorte de convergencia. **No reabre el Corte 0C** (`6d0abfe` sigue siendo
válido): cierra la contradicción que su corrida de cierre destapó.

---

## La contradicción

El modelo tenía congelado que la Propiedad es la cosa física y que la operación
pertenece al Encargo (D-E4-1). Pero el alta **exigía al menos una operación**,
así que toda propiedad nacía con un encargo vivo. Y el embudo dice lo contrario:

```
propietario  →  PROSPECCIÓN  →  ENCARGO  →  PUBLICACIÓN
                (existe para conseguir el encargo)
```

Si la prospección existe para conseguir el encargo, **el encargo no puede tener
que existir antes de prospectar**. Al retirar `POST /locales` en el Corte 0A
—que registraba el inmueble *y* abría una prospección *y* creaba un anuncio— no
quedó ninguna entrada para una propiedad que sólo se está prospectando, y
`uq_captacion_viva_por_operacion` rechazaba el encargo que `captar` intentaba
crear encima del que el alta ya había abierto.

Las otras dos salidas se descartaron y por qué:

- **«`captar` completa el encargo pendiente»** haría que el encargo exista antes
  de que el propietario haya encargado nada: convierte una intención del agente
  en una relación comercial.
- **«el inmueble no entra hasta captarlo»** rompe el flujo que BROX ya tenía e
  impide acumular identidad, ubicación, titularidad, duplicados e interacciones
  sobre el inmueble mientras se intenta captarlo.

---

## La distinción que congela

> **Propiedad registrada ≠ propiedad comercialmente encargada.**

Una propiedad sin encargos está en el registro maestro y puede prospectarse. Lo
que **no** es: ofrecida. No tiene precio autorizado, no tiene histórico
económico `U`, no se publica, y no dice estar «disponible» porque nada afirme lo
contrario.

Y no se deduce al revés: **propiedad sin encargo ≠ prospección**. Son dos cosas
que apuntan a la propiedad, no un estado suyo.

---

## Lo que cambia

| | Antes | Ahora |
|---|---|---|
| `POST /propiedades` | exigía ≥1 operación | acepta **0, 1 o 2** |
| con 0 operaciones | imposible | propiedad, ubicación, titularidad, atributos y evento. **Cero** captación, condición económica, hito `U`, anuncio y disponibilidad |
| con 1–2 operaciones | — | idéntico a antes, y la propiedad entra en oferta |
| `precio_referencial` / `moneda_referencial` | NOT NULL, proyectados del encargo | **anulables**: sin encargo no hay precio autorizado |
| `disponibilidad_comercial` | NOT NULL, `'D'` estampado en el alta | **anulable**: la oferta la abre el ENCARGO, y queda en `historial_estado` |
| `captar` | operación **fija a ALQUILER**, importe copiado del espejo del inmueble | `operacion`, `importe` y `moneda` **explícitos**; tipo y base de comisión los implica la operación |
| segundo encargo vivo de la misma operación | violación de integridad cruda de PostgreSQL | regla de negocio con su mensaje, y dice que **la otra operación sí se puede** |
| guion de captura | `operaciones` **obligatoria** e incondicional | se pregunta y **ordena**, pero no bloquea |
| `POST /locales/{id}/precios` sin encargos | hito huérfano que la ficha esconde y `/locales/{id}/precios` enseña | rechazado: el precio autorizado pertenece a un encargo |

`uq_captacion_viva_por_operacion` **se conserva tal cual**. Antes estorbaba
—toda propiedad nacía con encargo vivo, así que captar chocaba siempre—; ahora
vuelve a defender lo que decía.

**NULL y no un código nuevo.** Se pensó en un quinto valor `NO_OFRECIDA` y se
descartó: `DISPONIBILIDAD_PROPIEDAD` es una máquina de estados con transiciones,
rótulos y filtros, y «todavía no ha entrado en la máquina» no es un estado de la
máquina, es su ausencia.

---

## Verificación

```
backend  342/342 · 0 skipped · 20/20 suites de integración ejecutadas
angular  645/645
kairos    35/35

E2E      comision-movimientos     65 OK / 0
         disponibilidad-contrato  41 OK / 0
         estabilizacion-alquiler  18 OK / 0
         f4-solicitud            125 OK / 0
         f3-demanda              103 OK / 0
         f6-f7-alertas-tareas     64 OK / 0
         ficha-comercial          61 OK / 0
         reportes-propietario     50 OK / 0
         v6                       50 OK / 0
         e4-dashboard            129 OK / 0
```

**Los diez guiones E2E vuelven a correr.** Estaban muertos desde el Corte 0A —el
primero llamaba a `POST /locales` y contestaba 405—, así que llevaban semanas sin
verificar nada. Al revivirlos salió también la herrumbre acumulada, que se
arregla aquí y se dice:

- `e2e-f3-demanda` programaba visitas con **fechas literales de agosto de 2026** y
  caducaba solo: pasado el día 20, la «agenda de próximas» dejaba de incluir una
  visita que el propio guion acababa de programar. Ahora son relativas a hoy.
- `e2e-e4-dashboard` afirmaba que el ámbito del broker es «Reportes de equipo», y
  el rótulo cambió a propósito a «Mi equipo» —se leía como el título de una
  sección, no como el alcance de lo que se mira—.
- Tres limpiezas de fixture no soltaban las colecciones hijas que el alta
  universal sí escribe: el hito `U` y el anuncio cuelgan del **encargo** (V49,
  V70) y la **titularidad** de la propiedad. Sin eso la FK abortaba la
  transacción entera y la limpieza no borraba **nada**: el residuo salía `2|2|1`
  y el fallo aparecía lejos de su causa.

Y una lección de forma: `e2e-e4-dashboard` **elige** los códigos de sus encargos
porque media docena de comprobaciones busca las cinco etapas por
`CAP-…-E4-<sufijo>`. El alta universal los numera ella, así que ahí la propiedad
nace **sin encargo** y el encargo se abre con `POST /captaciones`, que sí acepta
el código. La migración mecánica había colapsado las dos llamadas en una y se
llevó por delante la identidad del fixture.

`PropiedadSinEncargoIntegrationTest` — la prueba que congela la decisión, en el
orden en que el usuario la describió:

| Comportamiento | Qué demuestra |
|---|---|
| `operaciones: []` crea la propiedad y nada más | 0 captaciones, 0 condiciones, 0 hitos, 0 anuncios |
| sin encargo no hay precio autorizado | precio y moneda en NULL, no en cero |
| sin encargo no dice estar disponible | disponibilidad NULL; el **registro** sigue activo |
| sin encargo no se puede publicar | y el mensaje dice que hay que captarla |
| la ficha se lee y no inventa encargos | ni historia económica |
| con una operación, el alta no cambia | encargo, hito `U`, precio proyectado y disponibilidad `'D'` |
| prospectar no abre encargo | la prospección es la intención del agente |
| captar con operación explícita | encargo, condición e importe **el pactado**, no el del registro |
| captar sin operación | rechazado, nombrando las dos |
| captar sin importe | rechazado: no hay precio del que tirar |
| segundo encargo vivo de la misma operación | rechazado, con el código del que ya existe |
| la otra operación sí | dos episodios, dos economías |
| **invariante** con encargo vivo | ninguna propiedad captada se queda sin declarar disponibilidad |

`ConservacionDeLaEdicionIntegrationTest` gana el escenario que faltaba —**una
propiedad prospectada, con cero encargos**, por los siete tipos—: guardar no la
cambia y **no le inventa un encargo**; editar la descripción no la pone en
oferta. El bloque de encargo se queda fuera a propósito: pedir un cambio de
operación sobre una propiedad que no tiene ninguna no es «no tocar nada».

---

## Lo que se encontró al hacerlo

Seis barridos en paralelo sobre el repositorio, más un crítico de completitud.
Lo que ninguno de los tres primeros habría encontrado solo:

**El alta se detenía en TRES puertas encadenadas, no una.** El rechazo explícito,
el `.get(0)` **eager** de `operacionDeReferencia` —`orElse` evalúa su argumento
siempre, también cuando el filtro ya encontró uno— y las dos columnas NOT NULL.
Levantar sólo la primera cambia un 400 legible por un 500.

**La moneda tenía dos candados.** `ck_propiedad_moneda_referencial` lleva el
`IS NOT NULL` **dentro** de la expresión, así que `DROP NOT NULL` a secas habría
seguido fallando, con 23514 en vez de 23502: el mismo bloqueo con otro número.

**KAIROS guardaba la operación bajo una clave que nadie leía.**
`Vocabulario.OPERACION = "operacion"`, en singular; la clave que publica BROX es
`operaciones`. No se notaba porque el alta la exigía y KAIROS chocaba antes. En
cuanto dejó de ser obligatoria, el borrador habría quedado LISTO con la
operación en la clave equivocada: el usuario diciendo «en venta» y la propiedad
naciendo con cero encargos. **La frase entendida, guardada, y sin llegar.**

**BROX Web no podía ejercer la decisión.** `propiedad-form` bloqueaba el plan
hasta tener operación, así que el endpoint aceptaría `operaciones: []` y desde
la única puerta humana no habría forma de mandarlo. Y `captacion-form` no tenía
un solo campo editable de importe: lo espejaba del inmueble, que ahora puede
llegar vacío. Los dos arreglados; el importe pasa a escribirse, que además es lo
correcto —dos encargos de la misma propiedad pueden pactar cifras distintas—.

**`captar` no podía expresar una venta** aunque se le diera la operación: el
tipo de comisión (`E`) y la base (`R`) estaban cableados, y para venta la
validación exige `P+V` o `F+N`. Una comisión de venta calculada sobre «renta
mensual» trata un precio de venta como si fuera un alquiler.

**El filtro `estado=N` no devolvía lo que su propio KPI contaba.** En SQL
`NULL <> 'D'` es UNKNOWN, así que la propiedad sin encargo salía como `'N'` en
el listado —vía el `else`— y no aparecía al filtrar por `'N'`. El mismo agujero
por partida doble: JPQL y nativa.

**«Entra al mercado» no dejaba rastro.** El primer encargo pone la propiedad en
oferta, y eso es el hecho comercial más importante de su expediente. Ahora va
por `Transiciones`, así que escribe en `historial_estado` como todas las demás.

---

## Deuda registrada, no resuelta

**La alerta «Modificación comercial sensible» se quedó sin emisor.** Vivía en
`LocalComercialService.actualizar`, que el Corte 0A retiró con su endpoint;
`PUT /propiedades/{id}` no la repuso. Las cuatro comprobaciones de
`e2e-f6-f7-alertas-tareas` se retiran **diciéndolo**, no en silencio: devolverla
tal cual sería reintroducir a propósito sus dos bugs congelados (viajaba con el
tipo `SOLICITUD_EVALUADA` y la entidad `INMUEBLE`, D-F6-4 y D-F6-5). Reponerla
es decidir antes con qué tipo y qué entidad avisa.

**Una propiedad sólo registrada no tiene agente dueño.**
`LocalComercialServiceImpl.exigirPertenencia` reconoce «tengo una captación» o
«tengo una prospección», no «yo la registré», así que su propio autor no puede
darla de baja hasta prospectarla. El flujo previsto es registrar y prospectar
seguido, así que el hueco es estrecho — pero es un hueco.

**Su propietario queda fuera del alcance del broker.**
`idsPropietarioDelBroker` y `contarLocalesEnSeguimiento` llegan a la propiedad
por captación **o** por prospección; una sólo registrada no entra por ninguna.
Es una decisión de alcance que hay que tomar a propósito.

**`captacion-form` sigue fijando `motivoOperacion: 'A'`.** Desde esa pantalla
sólo se abren encargos de alquiler. El rótulo del importe ya está preparado para
seguir a la operación cuando la pantalla sepa declararla.

**`CatalogoProductoresTest` no resuelve los nombres contra el código**: sólo mide
la longitud del texto de evidencia. El catálogo canónico puede nombrar a un
productor que no existe con el build en verde — es como las dos filas corregidas
aquí llevaban tiempo mintiendo.

**`gate-modelo-universal.sql` exige `count(*) = 21 FROM propiedad`**, una cifra
escrita a mano que cualquier alta nueva en la base de desarrollo rompe.
