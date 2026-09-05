# D-4P-1 · El modelo de procedencia — alternativas medidas y cobertura

**Aprobado por CONTROL el 2026-08-25.** Es la fase 2 de
`encargo-4p-procedencia-del-dato-gobernado.md`, **aprobada y congelada** para que
la implementación no la rehaga.

---

## 0. Los datos que deciden

Medidos en `controllocal_dev`:

```
uq_atributo_propiedad_clave  UNIQUE (id_propiedad, clave)    ← una fila por clave
uq_atributo_encargo_clave    UNIQUE (id_captacion, clave)    ← idem en ENCARGO
historial_estado.estado_anterior / estado_nuevo : varchar(1)
historial_estado : NO tiene columna `clave`; direcciona por id_entidad bigint
claves ESTRUCTURAL = 4  ·  con metraje = 26 de 26 propiedades
escriben por SQL directo: 6 suites E2E + gate-modelo-universal.sql
atributo_propiedad = 76 filas · fecha_actualizacion NULL en TODAS
atributo_encargo = 0 · las dos tablas de multivalor = 0
```

---

## 1. Las tres alternativas × las 12 invariantes

| # | invariante | **A1** columnas | **A2** `historial_estado` | **A3** tabla propia |
|---|---|---|---|---|
| 1 | escritura deja procedencia (PROP+ENC) | **IMPOSIBILITA** — 4 claves `ESTRUCTURAL` **no crean fila**; `metraje_total` la tienen **26/26** | **IMPOSIBILITA** — sin columna `clave`, no puede nombrar `(sujeto, agregado, clave)` | **sostiene**, en la frontera del servicio (§3) |
| 2 | misma transacción | sostiene | sostiene | **sostiene** |
| 3 | editar no destruye lo anterior | **IMPOSIBILITA** — `uq_…_clave` **rechaza** la 2.ª fila; el `UPDATE` pisa | **IMPOSIBILITA** — `varchar(1)` no admite `A_PIE_DE_CALLE` (14), decimal, fecha ni conjunto | **sostiene** |
| 4 | borrar no destruye historia | **IMPOSIBILITA** — el borrado es físico: se va la fila **y su columna** | **IMPOSIBILITA** — no puede guardar el valor retirado | **sostiene** |
| 5 | `INFERIDO` con modelo/versión/confianza | sostiene (CHECK) | **IMPOSIBILITA** — sin columnas de modelo/versión/confianza; serializarlo en `motivo varchar(300)` crea **dos verdades** | **sostiene** |
| 6 | legado sin procedencia inventada | sostiene | sostiene | **sostiene** |
| 7 | `LOC-D001`/`LOC-0002` con fuente | **debilita** — se guarda y la 1.ª edición la borra (por 3) | **IMPOSIBILITA** (por 3) | **sostiene** |
| 8 | Web y KAIROS misma semántica | sostiene (es del cable) | sostiene | **sostiene** |
| 9 | Core deriva actor/canal | sostiene | sostiene | **sostiene** |
| 10 | ningún valor actual se pierde | sostiene | sostiene | **sostiene** |
| 11 | bloqueados 19 · sin `P` nuevo | sostiene | sostiene | **sostiene** |
| 12 | `evento_dominio` sigue outbox | sostiene | **debilita** — mezcla «origen de una afirmación» con «estado de una entidad» | **sostiene** |
| + | multivalor conserva **el conjunto anterior** | **IMPOSIBILITA** — `borrarDe(ancla)` + re-`save`; la hija cuelga del ancla vigente | **IMPOSIBILITA** — `varchar(1)` | **sostiene** |

**A1 muere por cinco imposibilidades medidas.** **A2 muere por forma**: extender su
FK de `entidad_tipo` es barato, pero eso resuelve *el nombre*, no que **el valor no
quepa** ni que **no se pueda direccionar por clave**.

**Elegida: A3.**

---

## 2. La forma

Tabla propia **append-only**, direccionada por
**`(organizacion_id, sujeto, id_agregado, clave)`** — **nunca** por
`id_atributo_propiedad`. Ése es el punto que mete las cinco superficies en **un
solo mecanismo**.

Contiene: el valor (espejo de `atributo_propiedad`:
`valor_texto/numero/booleano/fecha/moneda`) · las **nueve** dimensiones de
`Procedencia` · `naturaleza` · `observado_en` · `evidencia_ref` · `confianza` ·
actor/rol/`registrado_en` · **`verbo`** (`ALTA | EDICION | RETIRADA`).

**El conjunto de un multivalor** va en una **tabla hija espejo** de
`atributo_propiedad_opcion`, para guardar **el conjunto entero, no la diferencia**.
Medido: `atributo_encargo_opcion_pkey (id_atributo_encargo, valor)` — conjunto
**sin orden y sin duplicados**, así que conservar el conjunto **es** conservar el
dato completo.

**Inmutabilidad**: trigger `BEFORE UPDATE OR DELETE` que lanza — patrón ya
existente en `observacion_mercado` (`tg_observacion_append_only`, `V76`). **El
trigger no escribe: sólo impide.** Escribe el servicio, en la misma transacción.

---

## 3. Las cinco superficies, demostradas

| # | superficie | cómo queda |
|---|---|---|
| **1** | PROPIEDAD escalar | `tipo_acceso` de `A_PIE_DE_CALLE` a `ESQUINA_A_CALLE`: la fila vigente se actualiza, el rastro gana una **segunda** fila (`verbo=EDICION`) y **la primera sigue ahí**. Lo que A1 no puede por `uq_…_clave` |
| **2** | PROPIEDAD **multivalor** | `vigilancia` de `{CASETA_24H, CAMARAS_CCTV}` a `{CAMARAS_CCTV}`: una fila de rastro con **el conjunto de dos**, entero |
| **3** | PROPIEDAD **retirada** | la fila vigente **desaparece**; el rastro gana `verbo=RETIRADA` **con el valor que se quitó**, actor, canal y fecha. La clave queda **con linaje y sin fila vigente** — imposible si colgara del `id` de la fila |
| **4** | **ENCARGO** | idéntico en las tres formas, **desde el primer commit**: `sujeto='ENCARGO'`, `id_agregado=id_captacion`. Cierra de paso que hoy **pactar no emite ni evento de operación** |
| **5** | **`ESTRUCTURAL`** | `metraje_total` escribe `propiedad.metraje` y **no crea fila**. Como el rastro se indexa **por clave**, deja linaje igual que los demás. Es la superficie que **decide la forma**, y la que A1 y A2 pierden entera |

---

## 3 bis. LA FRONTERA DE GARANTÍA — el *cutover* de 4.P

**Decisión del titular, 2026-08-25.** Es lo que convierte «tenemos linaje» en algo
comprobable en vez de aspiracional:

```
ANTES del cutover   → puede existir legado SIN linaje. No es defecto.
DESPUÉS del cutover → una escritura gobernada SIN linaje ES UN DEFECTO.
```

La fecha de entrada en vigor es la de `V83`. **El gate tiene que poder decir de
qué lado cae cada fila**, y por eso la frontera se define **explícitamente** en el
modelo, no se deja al criterio de quien consulte.

### 3 bis.1 · La primera modificación de una clave `ESTRUCTURAL`

Requisito propio, y es donde este corte se puede hacer mal:

Cuando tras el *cutover* se modifique por primera vez `metraje_total`, `piso`,
`partida_registral` u `oficina_registral`, el linaje debe **preservar las dos
cosas**:

- **el valor anterior que el Core encontró** en la columna, y
- **el valor nuevo**, con su procedencia completa.

**Y al anterior NO se le atribuye procedencia histórica.** No se le pone canal, ni
actor, ni fecha de nacimiento, ni naturaleza: **no consta**. Lo único que se
afirma de él es lo que sí es cierto — *«en el momento de esta edición, el Core
encontró este valor»* —, que es una **constatación del estado hallado**, no una
génesis.

> La diferencia con E1 es exactamente ésta: E1 fechaba el nacimiento del valor;
> esto sólo constata qué había cuando se le pasó por encima. Lo primero no consta;
> lo segundo lo presencia la propia operación.

## 3 ter. Génesis selectiva — ni backfill, ni ausencia por norma

**No se generaliza en ninguna de las dos direcciones.**

- **No** «backfill de todo lo que se pueda inventar».
- **No** «ausencia para todo el legado por comodidad».

**Sí** génesis para el histórico **cuya procedencia sea realmente demostrable**.
El caso candidato son las **70 filas de `V48`** — pero **sólo si el inventario
prueba de verdad su fecha y su canal**, no si simplemente lo parece. **Si la
prueba no se sostiene al implementarlo, esas filas se quedan sin génesis y se
dice.**

Y en toda génesis que se escriba: **`naturaleza` ausente**. La procedencia
**operacional** puede ser demostrable; **cómo se conoció el hecho, casi nunca lo
es**.

## 4. Los cuatro casos de KAIROS

Entran por el **mismo `ValorAtributo`** que usa el SPA —`MotorDeCapturaImpl:819/854`
ya converge ahí—, así que es **una** semántica, no dos.

| conversación | `naturaleza` | exige |
|---|---|---|
| «el propietario me dijo que acepta mascotas» | `DECLARADO` | — |
| «veo en la foto que parece tener balcón» | `INFERIDO` | **modelo · versión · confianza**, por CHECK |
| «durante la visita comprobé que tiene ascensor» | `OBSERVADO` | — |
| **no permite saberlo** | **ausente** | **no se fuerza** |

---

## 5. Las dos reglas ejecutables, y cómo se prueban

- **El Core jamás deduce `naturaleza`** → test que escribe el mismo valor **por
  canal SPA y por canal API, con actores distintos**, y exige `naturaleza`
  **ausente en ambos**. Falla si alguien la deriva del canal, del actor o del
  *endpoint*.
- **Una operación, naturalezas distintas** → un `PUT` con `tipo_acceso=OBSERVADO`,
  `zonificacion=DECLARADO` y `vigilancia` **sin naturaleza**, verificando que
  **cada valor conserva la suya**. Es el caso que abrió 4.P.

---

## 6. Lo que NINGÚN test cubre — dicho, no disimulado

1. **Invariante 1 no se sostiene a nivel de esquema.** 6 suites y el gate escriben
   por SQL directo, así que la procedencia **no puede ser `NOT NULL`**. Se sostiene
   **en la frontera del servicio**, con un test de arquitectura — y **ese test no
   verá un escritor privado nuevo**, mismo límite ya declarado en
   `PuertasDePublicacionTest`.
2. **Invariante 6** se comprueba como «cero filas de génesis con `naturaleza` no
   nula», pero **no puede probar que la génesis sea cierta**: descansa en la
   coincidencia medida entre `fecha_creacion` y la fecha de `V48`.
3. **Invariante 8** se prueba en el Core; **que KAIROS lo consuma igual no tiene
   suite propia** hoy.

---

## 7. Coste

**`V83`** (confirmado libre): 2 tablas, 1 trigger, 1 función y la siembra de
génesis. ~2 dominio · 2 persistencia · 3 servicio · 1 impl (la plomería) · 1 DTO
web · +2 tests. **Cero suites E2E que ajustar** —precisamente porque la
procedencia no es obligatoria en el esquema— y **cero ficheros de Angular**.

La plomería, cuantificada: `Procedencia` nace en `registrar:205` y `editar:445` y
**hoy no llega a ninguno de los siete escritores**. Hay que bajarla por
`aplicarValores`, `aplicarValoresDeEdicion`, `escribirMultivalor`,
`escribirMultivalorDeEncargo`, `retirarValores`, `aplicarCondiciones(De)` y el
enrutado `ESTRUCTURAL`. Y **`retirar` debe devolver el valor que borró** — hoy
devuelve `boolean`.
