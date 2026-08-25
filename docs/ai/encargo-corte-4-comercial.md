# Encargo — Corte 4 · Comercial (L, O, A) — `V81`

> **HISTÓRICO — CERRADO DEFINITIVAMENTE.** El encargo se ejecutó con `V81` y
> `V82`; el bloque completo, incluido 4.P, quedó cerrado en `795ffbf`.

**Congelado por CONTROL el 2026-08-24**, con decisión de producto tomada por el
titular (§3).

**BASE_SHA:** `c083bc0` — rama `feat/modelo-universal-y-autoridad-del-dato`,
Corte 3 cerrado y aprobado, Flyway hasta `V80`.

Corte anterior: **Corte 3 · Vivienda (`V80`)**, cerrado el 2026-08-24. No se
reabre.

---

## 1. Qué entra

Lo que la auditoría dejó para los tipos **comerciales** —local (`L`), oficina
(`O`), almacén/industrial (`A`)—, más lo que el Corte 3 excluyó por no ser
vivienda. **39 claves nuevas**, migración única
**`V81__el_activo_comercial_descrito.sql`**.

`orden`: **560 … 940**, de diez en diez, en el orden de las tablas de §4.
(El Corte 3 acabó en 550; medir y confirmar antes de escribir.)

Sujeto `PROPIEDAD`, `destino = 'ATRIBUTO'`, `del_sistema = true`,
`organizacion_id = NULL`, `aplica_todos = false`, `familia = NULL`, sin valores
por defecto, ninguna `ESTRUCTURAL`.

---

## 2. El corte hereda las instalaciones de vivienda, y se acepta

§3.5 mezcla tipos: `gas` aplica a **D,C,L,O,A,T** y `agua_caliente` a **D,C** —
vivienda. El Corte 3 las excluyó expresamente y **su encargo está congelado**.

**No se reabre el Corte 3.** La consecuencia se acepta y se escribe: **el Corte 4
termina también las instalaciones de la vivienda.** Queda registrado como
consecuencia, no como hueco silencioso.

Lo mismo con `gas`, `acceso_vehiculo_maximo` y `via_de_acceso`, que tocan `T`: su
tipo principal es comercial, y **un corte se define por el tipo que lo motiva**,
no por qué otros tipos arrastre su aplicabilidad. Los parámetros urbanísticos del
terreno siguen en el Corte 5.

---

## 3. LA EXIGENCIA — decisión del titular, tomada con el efecto medido

> **`tipo_acceso` nace `ALT` en `L`. Las otras 38 nacen `OPC`.**

### El efecto, medido y aceptado antes de decidir

`ALT` **impide publicar**, igual que `PUB`:
`AtributoPropiedadRepository.clavesQueImpidenPublicar:100` filtra
`a.exigencia in ('ALT', 'PUB')` — por diseño desde `V72`. No es «avisar»: es
HTTP 400 al anunciar.

Medido contra `controllocal_dev` el 2026-08-24:

```
L: 21 propiedades · 21 sin tipo_acceso (la clave no existe todavia)
```

→ **Al aplicar `V81`, las 26 propiedades publicables pasan a 5.** Los 21 locales
quedan fuera del mercado hasta que alguien los visite y registre el dato. Se
desbloquean **uno a uno**, según se vean.

**El titular lo decidió sabiendo esto, y se le planteó dos veces**: la primera con
una descripción equivocada de CONTROL —que `ALT` sólo avisaba—, corregida con la
medición antes de congelar nada. La decisión se mantuvo con la información
correcta.

### Por qué es defendible, y por qué no se rodea

`tipo_acceso` es el único dato de este corte que **el agente tiene delante cuando
capta**: está de pie en el local. No se deduce del distrito ni del metraje. Y sin
él, 40 m² a S/ 3 000 son caros a pie de calle en Miraflores y absurdos en el
interior de Mesa Redonda: el precio por m² mezcla dos mercados distintos.

Hoy hay **10 filas ALT** en todo el sistema y **ninguna exige salir a mirar**
(`metraje_total` en los siete tipos, `dormitorios` en C y D, `zonificacion` en T).
`tipo_acceso` es la primera. Eso es lo que hace la decisión real, y por eso la
tomó el titular.

### Lo que NO se hace para aliviarlo

- **No se rellena `tipo_acceso` en los 21 locales.** Ni por inferencia, ni por el
  caso frecuente, ni con `A_PIE_DE_CALLE` «porque casi siempre lo es». Un dato
  que no se sabe se declara FALTANTE. Es la regla no negociable del North Star y
  el motivo de que el bloqueo sea aceptable: **desbloquea el hecho verificado, no
  el relleno.**
- **No se le da valor por defecto** en la migración.
- **No se toca `exigirPublicable`** ni se le añade excepción para propiedades
  anteriores a `V81`.
- **Las otras 38 no suben.** Las catorce `PUB` que propone la auditoría siguen
  siendo propuesta: hoy el sistema tendría su primera `PUB` y **`PUB` no informa
  de nada** —no hay superficie del cable que reporte una PUB de la PROPIEDAD—.
  Esa promoción es un corte propio.

### Consecuencia operativa que el corte DEBE dejar escrita

La evidencia tiene que decir, con nombre y código, **cuáles son los 21 locales
que dejan de publicarse**, para que exista la lista de trabajo de campo. Un corte
que saca inventario del mercado sin decir cuál lo saca a ciegas.

---

## 4. Las 39 claves

Fuente: `auditoria-profundidad-inmobiliaria.md` §3.3, §3.4, §3.5 y §3.7. Cada
aplicabilidad se **contrasta contra la base antes de escribirla**; si la medición
contradice la tabla, manda la medición y se corrige el documento.

### 4.1 · §3.3 — el hecho que cierra un par huérfano

| # | clave | rótulo | tipo | aplica_a | vocabulario |
|---|---|---|---|---|---|
| 1 | `nivel_implementacion` | Nivel de implementación | LISTA | **A,L,O** | CASCO_OBRA_GRIS · PLANTA_LIBRE · IMPLEMENTADO_PARCIAL · IMPLEMENTADO_COMPLETO |

Es el **hecho** cuya condición `se_entrega_implementado` existe desde `V77`.
Medido: la condición se pacta en **A, L y O**. El guard 2.2 de `V78` **ignora
`tipo_operacion`** y compara sólo el conjunto de `tipo_propiedad`, así que el
hecho debe cubrir **A, L y O** o `V81` falla en su propia migración. La auditoría
dice «L,O,A»: **coincide**, aquí no hay corrección que hacer.

Va aparte de `estado_conservacion` (Corte 3) a propósito: son dos hechos
distintos.

### 4.2 · §3.4 — lo que el Corte 3 dejó fuera

| # | clave | rótulo | tipo | aplica_a | vocabulario / rango |
|---|---|---|---|---|---|
| 2 | `recepcion_edificio` | Recepción atendida | BOOLEANO | O | — |
| 3 | `horario_acceso_edificio` | Horario de acceso | LISTA | A,L,O | H24_7 · LUN_VIE_OFICINA · LUN_SAB_OFICINA · OTRO |
| 4 | `fibra_optica` | Fibra óptica en el edificio | BOOLEANO | L,O | — |
| 5 | `certificacion_sostenible` | Certificación sostenible | LISTA | O | NINGUNA · LEED_CERTIFIED · LEED_SILVER · LEED_GOLD · LEED_PLATINUM · OTRA |

> **`24_7` no vale como código**: los valores son `UPPER_SNAKE` empezando por
> letra (`^[A-Z][A-Z0-9_]*$`). Por eso **`H24_7`**, con su rótulo «24/7».

`horario_acceso_edificio` es regla del edificio, no del encargo: descalifica call
centers y cierres contables.

### 4.3 · §3.5 — instalaciones

| # | clave | rótulo | tipo | aplica_a | vocabulario |
|---|---|---|---|---|---|
| 6 | `gas` | Suministro de gas | LISTA | A,C,D,L,O,T | SIN_RED_CERCANA · RED_EN_LA_VIA · INSTALADO · GLP_TANQUE_EXTERNO · GLP_BALONES |
| 7 | `agua_caliente` | Agua caliente | LISTA | C,D | NO_TIENE · TERMA_ELECTRICA · TERMA_A_GAS · CENTRALIZADA · SOLAR |
| 8 | `suministro_electrico` | Tipo de suministro | LISTA | A,L,O | MONOFASICO_220 · TRIFASICO_380 · TRIFASICO_440 · SUBESTACION_PROPIA |
| 9 | `respaldo_electrico` | Respaldo eléctrico | LISTA | A,D,L,O | NO_TIENE · GRUPO_ELECTROGENO_AREAS_COMUNES · GRUPO_ELECTROGENO_TOTAL |
| 10 | `aire_acondicionado` | Aire acondicionado | LISTA | L,O | NINGUNO · SPLIT_EN_UNIDAD · CENTRAL_DEL_EDIFICIO · VRV_INDEPENDIENTE |
| 11 | `medidor_servicios` | Medidor de servicios | LISTA | A,L,O | INDEPENDIENTE · COMPARTIDO_PRORRATEO · SIN_MEDIDOR |
| 12 | `sistema_contra_incendios` | Sistema contra incendios | LISTA | A,L,O | NINGUNO · EXTINTORES · GABINETES · ROCIADORES · ROCIADORES_ESFR |
| 13 | `extraccion_humos` | Extracción de humos | LISTA | L | SIN_DUCTO · DUCTO_PROYECTADO · DUCTO_A_AZOTEA · CAMPANA_INSTALADA |

`gas` no se supone por distrito: Calidda crece manzana a manzana. `suministro_electrico`
no lo sustituye `carga_electrica_kw` —cuánta potencia hay, no de qué clase—.
`extraccion_humos` habilita o descarta de golpe al segmento gastronómico.

**`agua_desague` y `energia_electrica` NO entran**: son los reemplazos de
`servicios_disponibles` y nacen en el **Corte 5**, con la retirada de ésta y con
la guarda «ninguna LISTA sin vocabulario» extendida a PROPIEDAD. Separarlas antes
deja un agujero de captura.

### 4.4 · §3.7 — comercial y logístico

| # | clave | rótulo | tipo | unidad | aplica_a | vocabulario / rango |
|---|---|---|---|---|---|---|
| 14 | **`tipo_acceso`** | Tipo de acceso | LISTA | — | **L → `ALT`** | A_PIE_DE_CALLE · ESQUINA_A_CALLE · GALERIA_INTERIOR · PASAJE_COMERCIAL · CENTRO_COMERCIAL · INTERIOR_DE_EDIFICIO · MERCADO |
| 15 | `en_esquina` | Está en esquina | BOOLEANO | — | A,L,O | — |
| 16 | `clase_edificio` | Clase de edificio | LISTA | — | O | A_PLUS · A · B · C · NO_APLICA |
| 17 | `metraje_arrendable` | Metraje arrendable | DECIMAL | m² | A,L,O | `valor_minimo = 0` |
| 18 | `banos_comunes_piso` | Baños comunes en el piso | BOOLEANO | — | O | — |
| 19 | `posiciones_trabajo` | Posiciones de trabajo | ENTERO | — | O | `valor_minimo = 0` |
| 20 | `salas_reunion` | Salas de reunión | ENTERO | — | O | `valor_minimo = 0` |
| 21 | `aforo_itse` | Aforo autorizado (ITSE) | ENTERO | personas | A,L,O | `valor_minimo = 0` |
| 22 | `certificado_itse` | Certificado ITSE | LISTA | — | A,L,O | VIGENTE · VENCIDO · EN_TRAMITE · NO_TIENE |
| 23 | `area_libre` | Área libre / patio de maniobras | DECIMAL | m² | A | `valor_minimo = 0` |
| 24 | `profundidad_patio_maniobras` | Profundidad de patio | DECIMAL | m | A | `valor_minimo = 0` |
| 25 | `acceso_vehiculo_maximo` | Vehículo máximo que ingresa | LISTA | — | A,L,T | CAMIONETA · CAMION_2_EJES · CAMION_3_EJES · TRAILER_T3S3 · CONTENEDOR_40_PIES |
| 26 | `muelles_carga` | Muelles de carga | ENTERO | — | A | `valor_minimo = 0` |
| 27 | `tipo_muelle` | Tipo de muelle | LISTA | — | A | SIN_MUELLE · A_NIVEL_DE_PISO · ANDEN_ELEVADO · ANDEN_CON_NIVELADOR · MIXTO |
| 28 | `puertas_ingreso` | Puertas de ingreso | ENTERO | — | A | `valor_minimo = 0` |
| 29 | `ancho_puerta_ingreso` | Ancho de puerta | DECIMAL | m | A | `valor_minimo = 0` |
| 30 | `alto_puerta_ingreso` | Alto de puerta | DECIMAL | m | A | `valor_minimo = 0` |
| 31 | `capacidad_portante_piso` | Capacidad portante del piso | DECIMAL | t/m² | A | `valor_minimo = 0` |
| 32 | `tipo_piso` | Tipo de piso | LISTA | — | A | CONCRETO_PULIDO · CONCRETO_ENDURECIDO · LOSA_SIN_TRATAR · AFIRMADO · TIERRA |
| 33 | `luz_entre_columnas` | Luz entre columnas | TEXTO | m | A | `longitud_maxima = 40` |
| 34 | `posiciones_pallet` | Capacidad en posiciones pallet | ENTERO | — | A | `valor_minimo = 0` |
| 35 | `area_oficinas` | Área de oficinas administrativas | DECIMAL | m² | A | `valor_minimo = 0` |
| 36 | `condicion_almacenamiento` | Condición de almacenamiento | LISTA | — | A | SECO · REFRIGERADO · CONGELADO · MATERIALES_PELIGROSOS · DEPOSITO_TEMPORAL_ADUANERO |
| 37 | `balanza_camionera` | Balanza camionera | BOOLEANO | — | A | — |
| 38 | `estacionamientos_camiones` | Estacionamiento de camiones | ENTERO | — | A | `valor_minimo = 0` |
| 39 | `via_de_acceso` | Vía principal de acceso | TEXTO | — | A,T | `longitud_maxima = 120` |

**`muelles_carga` y `tipo_muelle` son dos claves a propósito**: contar muelles sin
decir el tipo deja la cifra sin significado.
`area_oficinas` va aparte porque sin separarla esos metros se cotizan como
almacén. `estacionamientos_camiones` no se mezcla con `estacionamientos`:
juntarlos hace el número inútil para los dos casos.
`via_de_acceso` es TEXTO y no LISTA porque «Panamericana Sur km 32» no tiene
vocabulario cerrado.

### 4.5 · `certificado_itse` **no retira** `apto_licencia_funcionamiento`

`apto_licencia_funcionamiento` existe hoy (BOOLEANO, A/L/O, OPC) y la auditoría
describe `certificado_itse` como «el hecho verificable detrás» de ese booleano
sin procedencia. **Las dos conviven en este corte.** Retirar la vieja exige
migración de datos y es un corte propio: el North Star prohíbe retirar una
captura antes de que su reemplazo exista y esté poblado. Queda anotado.

---

## 5. Lo que la exigencia obliga a cambiar en el cierre

**El criterio «las 26 propiedades siguen publicables» YA NO APLICA** y sustituirlo
es parte del corte, no un efecto colateral:

- **5 de 26 publicables**, y las **21** bloqueadas lo están **por
  `tipo_acceso` y sólo por `tipo_acceso`**. Que ninguna otra clave nueva aparezca
  como bloqueante se comprueba, no se supone.
- La evidencia lleva **la lista de los 21 locales con su código**, que es la lista
  de trabajo de campo.
- **Las suites E2E que publican un `L` van a romperse.** Sus fixtures tendrán que
  registrar `tipo_acceso` al crear el local. **Eso es corrección de fixture, no
  relajación de la regla**: lo que no vale es quitar el ALT ni sembrar el valor en
  las propiedades reales. Revisar las 5 del gate y también las de fuera que se
  toquen.
- `PropiedadResponse.atributosQueFaltan` lleva ALT, así que **`tipo_acceso` sí
  informa** en la ficha — a diferencia de una PUB. Comprobar que aparece.

---

## 6. Cómo se escribe

Igual que `V79` y `V80`, sin inventar mecánica:

1. `INSERT INTO catalogo_atributo` con columnas explícitas, **ASCII en el
   INSERT**, y `UPDATE` posterior que repone acentos en `rotulo`, `ayuda` y
   `unidad` (`m2` → `m²`, `t/m2` → `t/m²`).
2. `INSERT INTO catalogo_atributo_tipo (…, requerido, exigencia)` por conjunto de
   aplicabilidad. **`requerido` es espejo exacto de `exigencia = 'ALT'`**: `true`
   sólo en la fila `tipo_acceso`/`L`, `false` en las demás. El guard 2.4 de `V78`
   lo mira en **todo** el catálogo.
3. `INSERT INTO catalogo_atributo_opcion` con `orden` denso `1..N`, códigos
   `UPPER_SNAKE` ASCII que **empiecen por letra**, rótulos legibles, acentos por
   `UPDATE`.
4. **`catalogo_atributo_operacion` no se toca** (guard 2.5).
5. Cerrar con un `DO $$ … END $$;` de aserciones sobre el array de las 39:
   todas presentes y activas; ninguna sin aplicabilidad; ninguna en
   `catalogo_atributo_operacion`; **toda LISTA/LISTA_MULTIPLE con vocabulario**;
   **exactamente una fila `ALT`** en las nuevas (`tipo_acceso`/`L`) y su
   `requerido = true`; **cero `PUB` en todo el catálogo del sistema**;
   `destino='ATRIBUTO'` y `campo_estructural IS NULL`; cero valores
   materializados; y el par `nivel_implementacion` / `se_entrega_implementado`
   cubierto en A, L y O.

Además, **y es la única corrección de dato del corte**:

6. `UPDATE catalogo_atributo SET unidad = 'm²' WHERE clave = 'area_minima_arrendable'`
   — se le olvidó a `V77` (D-BASE-4). Es clave del ENCARGO y comercial, así que
   éste es su sitio. `proteger_catalogo_del_sistema()` no bloquea el `UPDATE` de
   `unidad`.

---

## 7. Deuda de verificación que este corte tiene que pagar

Medida en el Corte 3 y anotada en
`evidencia/2026-08-24-corte-3-estado-base-medido.md`:

- **`ConservacionDeLaEdicionIntegrationTest` no crece con el catálogo**: sus casos
  por tipo son listas escritas a mano. El Corte 3 extendió D y C. **Este corte
  extiende L, O y A**, o lo que siembre queda fuera del gate de ida y vuelta.
- **`e2e-editor-universal.ps1:162` excluye `SELECTOR_MULTIPLE` del fixture.** Este
  corte no siembra ninguna LISTA_MULTIPLE, así que **no bloquea** — pero si
  apareciera una, se demuestra a mano o se levanta la exclusión.
- **El barrido con `grep -iF` no funciona en esta máquina** (aborta con SIGABRT,
  sin stderr). Cualquier barrido lleva **control positivo**, o se usa `rg`.
  Está en `CLAUDE.md`.

---

## 8. Qué NO entra

- **Ninguna `PUB`.** Las catorce que propone la auditoría siguen siendo propuesta.
- **Ningún otro `ALT`** que `tipo_acceso`/`L`.
- **`agua_desague`, `energia_electrica`** y la retirada de `servicios_disponibles`
  → Corte 5.
- **`estado_ocupacion`** → Corte 5, **y viene con un error de plan ya medido**: su
  condición `entrega_desocupado` está sembrada en los **siete** tipos y la
  auditoría §3.8 lo planea para T,C. Sembrado así, la migración lanza. Anotado en
  §5 del borrador y en `pendientes-brox.md`.
- **`lote_minimo_normativo`** (T) → Corte 5. **`unidad_relacionada`** → Corte 6.
- **Las conversiones de tipo** (`cuota_mantenimiento`, `rubro_permitido`,
  `zonificacion`, `banos`): cada una con su bloqueo de dato.
- **El resto del Corte 1**: aplazado por corpus.
- **UI nueva.** Las 39 se pintan solas por `cl-campo-gobernado`. Que aparezcan sin
  tocar Angular es **prueba** del corte.
- **`familia`**: sigue `NULL`. El formulario pasa de 55 a 94 campos; agruparlo es
  decisión de presentación y va con el corte del SPA. **Registrado, no
  silenciado.**
- **Rellenar `tipo_acceso` en los 21 locales.** Ver §3.
- Multi-tenancy, RLS, producción, rotación del JWT, E3, KAIROS, tipo `X`, PDF.

---

## 9. Alcance documental autorizado

1. `mapa-ejecucion-brox.md` — Corte 4 en curso / cerrado, con `V81`.
2. `auditoria-profundidad-inmobiliaria.md` §6 — lo realmente aplicado, más
   cualquier corrección que la medición imponga.
3. `pendientes-brox.md` — §2.4 (`nivel_implementacion` cubierto), §2.5,
   y la fila de `area_minima_arrendable`.
4. Evidencia: `verificacion/evidencia/2026-08-24-corte-4-comercial.md`.
5. `docs/ai/borrador-corte-4-comercial.md` — marcarlo superado por este encargo.

---

## 10. Cierre

- Gate `.sql` **en verde con las 39 sembradas**, ejecutado **dentro** de
  `Verificar-Cierre.ps1` (ya lo hace desde `3.a`).
- Una `L`, una `O` y una `A` se registran y se editan con las claves nuevas,
  **ida y vuelta idéntica**.
- Vocabularios idénticos por las dos puertas (alta y editor) — D-A-1.
- **Angular no se toca.**
- **5 de 26 publicables, y las 21 bloqueadas sólo por `tipo_acceso`** — con la
  lista de códigos en la evidencia.
- Una sola corrida de cierre con `TEST_DB_URL`, más el build de producción de
  Angular, sin nada más compilando.

---

## 11. Protocolo

Si el precheck contradice este encargo, **devolver `STOP — DECISIÓN REQUERIDA POR
CONTROL`** antes de tocar un archivo, con la medición que lo contradice. Ha
ocurrido en `V79`, en `3.a` y en la propia exigencia de este corte —donde CONTROL
describió mal el efecto de `ALT` y la medición lo corrigió antes de congelar—.

Al terminar:

```
LISTO PARA AUDITORÍA
BASE_SHA=<sha de c083bc0>
CANDIDATE_SHA=<sha>
```
