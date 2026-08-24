# La identidad registral pertenece a la propiedad · Corte 2 · V79

**Cerrado el 2026-08-23.** Migración `V79__la_identidad_registral_de_la_propiedad.sql`.
Encargo congelado: `docs/ai/encargo-corte-2-identidad-registral.md`, con su
**enmienda posterior al precheck** al final del mismo documento.

---

## El hueco

La partida registral existía en **un solo sitio de toda la base**:

```
condicion_compraventa.partida_registral      0 filas
```

Colgada de una solicitud de venta. Un inmueble que nunca se puso en venta **no
tenía partida en ninguna parte**, así que el broker no podía verificar titular ni
cargas antes de firmar un encargo de alquiler — que es la operación más
frecuente de la cartera (16 de 19 encargos en `dev`).

No es un dato que se pacte: la partida es lo que el inmueble **es** ante el
registro. Sobrevive al encargo y no cambia porque se vuelva a alquilar. Por la
regla de `V73` —*si al firmar el siguiente encargo el dato puede cambiar sin que
la propiedad haya cambiado, es del ENCARGO*— es del sujeto **PROPIEDAD**, y las
dos claves de identidad son además **ESTRUCTURAL**: no dependen del tipo y
participan en la identidad del activo, que es el criterio de D-E4-3.

---

## El precheck paró el corte, y tenía razón

Antes de tocar un archivo, el CONSTRUCTOR devolvió
`STOP — DECISIÓN REQUERIDA POR CONTROL`. El encargo congelado pedía sembrar
cinco de las seis claves como **`PUB`**, apoyándose en esta afirmación:

> «`Exigencia.PUB` existe […] pero el contrato del DTO dice explícitamente que
> *no es un error*: la ficha avisa, nadie rechaza. **No hay un solo `throw`
> colgado de ahí**.»

**Es falso**, y lo siguiente está medido contra el código vivo:

| Hecho | Dónde |
|---|---|
| `exigirPublicable(...)` termina en `throw new ReglaNegocioException("Todavia no se puede publicar: …")` | `PublicacionServiceImpl.java:186-214` |
| Se alcanza al crear el anuncio de un encargo **y** al pasarlo a `PUBLICADO` | `PublicacionServiceImpl.java:94` · `:326` |
| La lista que lo alimenta filtra por ALT **y** PUB | `AtributosGobernados.faltantesDePropiedadParaPublicar` |
| `ReglaNegocioException` → **HTTP 400** | `ManejadorErroresApi.java:45` |

El comentario del DTO que el encargo citaba dice, entero: *«`atributosQueFaltan`
no es un error: es lo que permite a la ficha avisar de que **no se puede publicar
todavía**»*. Se leyó la primera mitad.

Y hay un segundo hecho que cambia la conclusión: **no existe ninguna superficie
del cable que reporte una PUB de la PROPIEDAD**. `PropiedadResponse.atributosQueFaltan`
lleva sólo ALT (`esRequeridoPara` → `bloqueaAlta()`); `EncargoFicha.faltanParaPublicar`
lleva ALT+PUB pero **sólo del ENCARGO**. El único consumidor de la lista de
propiedad es el `throw`.

**Lo que se evitó, medido:**

- **26 de 26** propiedades reales de `controllocal_dev` pasan hoy el gate de
  publicación. Con la tabla congelada, las 26 dejaban de poder anunciarse.
- **Dos de las cinco** suites del gate de cierre publican y habrían caído en el
  400: `e2e-f4-solicitud.ps1:144` y `e2e-estabilizacion-alquiler.ps1:136`, las
  dos sobre un LOCAL creado con `metraje_total` y `rubro_permitido` y nada más.
- Ponerlas en verde habría exigido rellenar identidad registral en el fixture:
  **alterar un flujo comercial y modificar pruebas**, las dos prohibidas por el
  propio encargo.

**CONTROL resolvió:** la semántica de `PUB` no se toca —bloquea publicar, y está
bien que lo haga—, y **las seis capacidades de V79 entran `OPC`**. La promoción
queda documentada como destino, no implementada.

---

## Preflight — las dos bases antes de V79

Medido el 2026-08-23 contra PostgreSQL real, las dos en `V78`.

| | `controllocal_dev` | `controllocal_repositorios` |
|---|---|---|
| qué es | **corpus operativo** | **`TEST_DB_URL`** — infraestructura sintética de integración |
| propiedades | **26** (1 C · 1 D · 21 L · 2 O · 1 T) | 2 871 (7 tipos) |
| claves de catálogo | 45, todas del sistema | 802, de las cuales **757 son `zz_*`** de residuo de las suites |
| valores escritos | 74 en `atributo_propiedad`, 0 multivalor, 0 de encargo | 13 777 · 176 · 437 |
| exigencias del sistema | 10 ALT · 59 OPC · **0 PUB** | 10 ALT · 59 OPC · **0 PUB** |
| `condicion_compraventa` | **0 filas** | **0 filas** |
| columnas registrales en `propiedad` | ninguna | ninguna |
| las 6 claves de V79 | no existen | no existen |

> **Sólo la primera describe mercado.** Las cifras de impacto que circulaban en
> `auditoria-profundidad-inmobiliaria.md` —«406 baños», «1 048 departamentos»—
> salen de la segunda. En `dev` no hay 1 048 departamentos: hay **uno**. Esa
> distinción es la razón por la que el Corte 1 (resto) queda aplazado y este
> corte se adelantó: la identidad registral se puede modelar **sin inferir nada**.

---

## Qué entró — `V79`, migración única

### Esquema

```
propiedad
  + partida_registral  VARCHAR(40)  NULL
  + oficina_registral  VARCHAR(40)  NULL

catalogo_atributo
  ~ ck_catalogo_campo_estructural
      antes:   ('METRAJE','PISO')
      despues: ('METRAJE','PISO','PARTIDA_REGISTRAL','OFICINA_REGISTRAL')

  + funcion exigir_vocabulario_estructural()
  + trigger tg_vocabulario_estructural  BEFORE INSERT OR UPDATE ON propiedad

condicion_compraventa.partida_registral
  ~ COMMENT: pasa a declararse COPIA del episodio, no autoridad
```

### Catálogo

| clave | tipo | destino | aplica_a | exigencia |
|---|---|---|---|---|
| `partida_registral` | TEXTO (máx. 40) | **ESTRUCTURAL** `PARTIDA_REGISTRAL` | L,O,D,C,T,A | OPC |
| `oficina_registral` | LISTA | **ESTRUCTURAL** `OFICINA_REGISTRAL` | L,O,D,C,T,A | OPC |
| `independizado` | BOOLEANO | ATRIBUTO | D,O,L,A | OPC |
| `declaratoria_fabrica` | BOOLEANO | ATRIBUTO | C,D | OPC |
| `area_segun_partida` | DECIMAL m² | ATRIBUTO | C,T,A | OPC |
| `cargas_gravamenes` | LISTA_MULTIPLE | ATRIBUTO | L,O,D,C,T,A | OPC |

**27 filas de aplicabilidad · 13 opciones de vocabulario · 0 en PUB · 0 valores
materializados.** Ninguna incluye el tipo `X` (OTRO), que sigue con tres claves
aplicables y sin auditar.

Vocabularios, con `catalogo_atributo_opcion` como **única** autoridad:

```
oficina_registral   LIMA · CALLAO · HUAURA · CANETE · HUARAL · BARRANCA
cargas_gravamenes   NINGUNA · HIPOTECA · EMBARGO · SERVIDUMBRE
                    COPROPIEDAD_SIN_DIVIDIR · SUCESION_PENDIENTE · LITIGIO
```

### La guarda que faltaba

`oficina_registral` es la **primera LISTA cuya autoridad es un campo canónico**,
y ahí la comprobación de vocabulario de `V72` no llegaba: vive dentro de
`exigir_atributo_gobernado`, que es un trigger de `atributo_propiedad`, tabla por
la que un valor estructural **no pasa**. La capa Java tampoco lo comprobaba —
`ConversionDeValores` acota tipo, rango y longitud, y nunca pertenencia.

Sin la guarda, `oficina_registral = 'MADRID'` habría entrado sin que nada la
parase. V79 la cierra por los dos lados y **sin duplicar el vocabulario**:

- `ConversionDeValores.exigirDelVocabulario(definicion, valor)` — lee
  `opcionesVigentes()`, que son las filas del catálogo. Da el mensaje.
- `tg_vocabulario_estructural` — recorre las claves `ESTRUCTURAL` de tipo lista
  **con vocabulario sembrado** y compara contra `catalogo_atributo_opcion`. Es la
  garantía.

**No se generalizó**, y eso era la trampa del corte: la guarda mira sólo claves
`ESTRUCTURAL`. `servicios_disponibles` es una LISTA de la PROPIEDAD, muda a
propósito, y sigue comportándose exactamente igual — con su caso que lo
demuestra.

### El fallo que la guarda genérica encontró antes de existir

`EscritorEstructural` tiene el mismo concepto escrito en **cuatro** `switch`
—`aplicar`, `vaciar`, `sabeEscribir`, `leerValor`— y nada obligaba a que las
cuatro listas coincidieran:

```
leerValor(...)                                  -> default -> null
ValoresGobernados.Constructor.con(clave, null)  -> descarta la clave
```

Un `case` de escritura sin su lectura **guarda el dato en su columna y lo hace
desaparecer del contrato**, y ninguna prueba de ida y vuelta lo nota porque la
clave sencillamente no está en la respuesta.

`CadenaEstructuralCompletaTest` lo cierra de forma genérica: descubre los
conceptos por reflexión sobre las constantes `CAMPO_*` de `CatalogoAtributo` —así
un concepto nuevo entra en el gate sólo con declararlo, sin una segunda lista que
mantener— y exige de cada uno la ida, la vuelta y una decisión explícita sobre
qué significa vaciarlo.

**Se comprobó que muerde**, retirando el `case` de lectura de
`PARTIDA_REGISTRAL`:

```
CadenaEstructuralCompletaTest.loEscritoSeLee  FAILURE
  La cadena estructural esta rota en:
    PARTIDA_REGISTRAL: se escribio "120.50" y `leerValor` devolvio null,
    asi que el dato queda guardado donde nadie lo lee
```

La otra mitad —que ninguna **fila** del catálogo declare un concepto que el
código no conoce— vive en `AutoridadDelDatoIntegrationTest`, porque el catálogo
es dato y un tenant puede escribir en él.

---

## Lo que NO entró, y por qué

| | |
|---|---|
| **Ninguna promoción a `PUB`** | Bloquea publicar. Es una decisión de negocio con su propio corte |
| **El *snapshot* A→B de compraventa** | `condicion_compraventa.partida_registral` tiene 0 filas y su escritor nace con el expediente de compraventa (bloque 6). V79 sólo deja escrito, en el comentario de la columna, que dejó de ser la autoridad. Simularlo con SQL habría sido probar la simulación |
| **La guarda «ninguna LISTA de PROPIEDAD sin vocabulario»** | Rompería `servicios_disponibles`, que es muda a propósito hasta el Corte 5 |
| **Aplicabilidad registral por operación** | Una partida importa mucho más en una venta que en un alquiler, y hoy una clave de PROPIEDAD declara aplicabilidad **por tipo**, no por operación. Registrado, sin resolver |
| **Familia temática** | Las otras 19 claves de PROPIEDAD tienen `familia = NULL`. Estrenar la primera aquí habría cambiado cómo agrupa el alta, y nadie lo pidió |
| **Cualquier cambio en Angular** | `git diff --stat frontend-angular/` = **vacío**. Es una de las pruebas del corte, no un supuesto |

---

## Verificación

**Una corrida**, `verificacion/Verificar-Cierre.ps1` con `TEST_DB_URL`, más Angular
y el build de producción aparte para no compilar en paralelo con las suites.

```
Reactor con PostgreSQL real       720 + 48 + 393     0 fallos · 0 SKIPPED
Integracion comprobada ejecutada  20 / 20
E2E del cierre                    65 + 41 + 125 + 18 + 147 = 396 OK · 0 fallas
Angular                           668 / 668 SUCCESS
Build de produccion               verde (exit 0)
git diff --check                  limpio
```

> El build de producción emite tres **avisos** de presupuesto de estilos
> —`radar-resolver.scss` 4,40 kB, `shell.scss` 4,47 kB,
> `propiedad-editor.scss` 4,11 kB, sobre un umbral de aviso de 4 kB—. Son
> anteriores a este corte y no lo rompen: el techo que **falla** es 16 kB
> (`decision-presupuesto-de-estilos-de-componente.md`), y V79 no toca ninguna
> hoja de estilo. Se dicen para que nadie los cuente como nuevos.

Contra el baseline que CONTROL reportó antes del corte —`717 + 48 + 374`, 20/20,
396 casos E2E—:

| | antes | después | qué entró |
|---|---|---|---|
| `controllocal-service` | 717 | **720** | `CadenaEstructuralCompletaTest` (3) |
| `controllocal-persistence` | 48 | 48 | — |
| `controllocal-app` | 374 | **393** | `AutoridadDelDatoIntegrationTest` 11→**19** · `CatalogoQueHablaIntegrationTest` 20→**31** |
| suites de integración | 20 | **20** | ninguna nueva, a propósito: los casos de V79 viven en las dos suites cuyo tema ya era el suyo |
| E2E | 396 | **396** | ninguna nueva. La E2E del *snapshot* A→B se retiró del criterio de cierre por la enmienda |

### La migración, aplicada dos veces sobre un V78 real

| Base | Antes | Después |
|---|---|---|
| `controllocal_repositorios` (`TEST_DB_URL`) | V78 | **V79**, aplicada en la corrida de cierre |
| `controllocal_dev` (corpus real) | V78 | **V79**, `2026-08-24 05:01:49` UTC |

Y una vez más desde cero: cada suite E2E levanta un PostgreSQL efímero y corre
la cadena Flyway completa, así que `V79` se aplicó también **sobre base vacía**
cinco veces, sin residuo (`sin contenedores, volumenes ni red residuales`).

### El corpus real, antes y después

Lo que importa de esta tabla es la columna de la derecha: **nada cambió salvo lo
que se añadió**.

| | antes | después |
|---|---|---|
| propiedades | 26 | **26** |
| propiedades con partida declarada | — | **0** |
| propiedades con oficina declarada | — | **0** |
| claves del sistema | 45 | **51** |
| de sujeto PROPIEDAD · ENCARGO | 19 · 26 | **25 · 26** |
| conceptos ESTRUCTURAL | 2 | **4** |
| exigencias del sistema | 10 ALT · 59 OPC · **0 PUB** | 10 ALT · 86 OPC · **0 PUB** |
| valores de las seis claves nuevas | — | **0** |
| `condicion_compraventa` | 0 filas | **0 filas**, ahora con comentario |
| `servicios_disponibles` | LISTA · ATRIBUTO · 0 opciones | **idéntica** |

> **Los ceros son el resultado, no la falta de él.** V79 no inventó una partida,
> ni una oficina, ni un `independizado = false`, ni un `cargas_gravamenes =
> NINGUNA`. La ausencia sigue significando «todavía no se sabe».

En `controllocal_repositorios` sí hay valores escritos después —4 partidas, 6
oficinas, 1 valor gobernado, y 324 propiedades más— porque **son los que
escribieron las pruebas**. Esa base comete: es su función, y es la razón por la
que sus cifras no describen mercado.

### Angular no se tocó, y es una prueba

```
git diff --stat frontend-angular/     (vacio)
```

Las seis claves llegan al alta y al editor **por el contrato**, no por código:
`AutoridadDelDatoIntegrationTest.lasSeisLleganAlAltaSinTocarNingunaInterfaz`
comprueba que el motor de captura publica cada una en un tipo al que aplica, con
su tipo de dato y —en la oficina— su vocabulario. `cl-campo-gobernado` ramifica
por `control`, nunca por la clave, y `FronteraDeAutoridadEnElSpaTest` rompe el
build si alguien lo intenta.

`PublicacionServiceImpl` **tampoco aparece en el diff**: la semántica de `PUB` no
se tocó.

### Un fallo real durante la verificación, y qué enseñó

La primera corrida definitiva **abortó**, y la abortó una guarda de V79:

```
ERROR: V79: se escribieron 3 valores de claves que acaban de nacer.
```

Causa: para reaplicar la migración corregida se revirtió V79 de la base de
pruebas a mano, y **borrar la clave del catálogo no borra sus valores** —
`atributo_propiedad` referencia por `clave`, no por FK, así que las tres filas de
`cargas_gravamenes` escritas por las pruebas sobrevivieron a la reversión.

No era un defecto del corte: era la guarda haciendo su trabajo sobre un estado
inconsistente. En una base V78 legítima esas filas **no pueden existir** —
`exigir_atributo_gobernado` rechaza una clave que no está en el catálogo—, así
que la única forma de verlas es exactamente la que se vio.

---

## Lo que este corte deja abierto

| | |
|---|---|
| La promoción `OPC → PUB` de las cinco que la tienen prevista | y, con ella, la pregunta de si debe distinguir venta de alquiler |
| El *snapshot* fechado de la partida en una compraventa | bloque 6 |
| Rotar el secreto JWT | `pendientes-brox.md` §0.2 — sigue publicado en `2832a9b` |
| El tipo `X` (OTRO) | sigue con 3 claves aplicables y sin auditar |
| El ciclo largo de verificación | el gate corre 5 de 23 suites E2E |
