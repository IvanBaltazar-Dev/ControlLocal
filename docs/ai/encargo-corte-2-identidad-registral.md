# Encargo — Corte 2 · Identidad registral (V79)

**Alcance congelado el 2026-08-23** por la sesión de CONTROL, medido contra el
repositorio y las dos bases vivas. Quien implemente **no re-audita**: lo medido
está aquí abajo con su cifra.

---

## Precondición bloqueante — ⛔ **INCUMPLIDA**

**Publicar la rama antes de la primera modificación funcional de V79.**

**Son 48 commits sin publicar, no 44.** Las 44 —y las 43 de
`pendientes-brox.md` §0.1— se contaron contra `main` **local**, que está 4
commits por delante de `origin/main` **sin divergencia** (`origin/main...main`
= `0 4`). Contra lo que existe de verdad en GitHub, `origin/main..HEAD` = **48**.

**Estado: no publicada.** `git ls-remote --heads origin` devuelve únicamente
`refs/heads/main` en `2832a9b`. El intento de *push* desde la sesión de CONTROL
se colgó y, forzado a fallar rápido, dio la causa:

```
fatal: could not read Username for 'https://github.com': terminal prompts disabled
```

`credential.helper = manager` y el Credential Manager no puede abrir su diálogo
en una sesión no interactiva; `gh` no está instalado. **CONTROL no introduce
credenciales.** La publicación la ejecuta el titular desde una terminal
interactiva:

```
git push -u origin feat/modelo-universal-y-autoridad-del-dato
```

Y se da por cumplida sólo cuando las cuatro comprobaciones den:

| Comprobación | Cómo |
|---|---|
| existe *upstream* | `git rev-parse --abbrev-ref "@{u}"` |
| SHA remoto = HEAD local | `git rev-parse HEAD` contra `git ls-remote origin <rama>` → `a7ff895…` |
| los 48 commits son recuperables | `git rev-list --count origin/main..origin/<rama>` → **48** |
| `main` intacto | `git ls-remote --heads origin` → `refs/heads/main` sigue en `2832a9b` |

**No hacer *merge* a `main`.** La rotación del JWT (§0.2) y qué pasa con
`origin/main` (§0.3) son trabajo aparte y **no bloquean este corte**.

> Comprobado antes de intentar el *push*: no queda rastreado ningún
> `api.properties`, `db.properties` ni `appsettings.json`, y los tres ficheros
> que marcó la búsqueda de secretos son falsos positivos (una asignación de
> variable en el script de cierre; los dos Java sólo nombran `password` como
> parámetro). La rama no empeora la exposición ya publicada en `2832a9b`.

---

## Por qué este corte y no el que decía el mapa

El mapa ponía quinto el «Corte 1 (resto)». Se invierte, y la razón es medida:

1. **El Corte 1 (resto) no se puede decidir hoy.** Pide exigencias y vocabularios
   —decisiones de negocio— y la evidencia para calibrarlas está contaminada: los
   números de impacto («406 baños», «584 zonificaciones», «1 048 departamentos»)
   salen de `controllocal_repositorios`, que **es `TEST_DB_URL`**, la base
   autorizada de integración, con 726 claves `zz_*` de residuo contra 45 del
   sistema. En un día pasaron de 406 a 525 baños sin que nadie cargara nada.
   El corpus real es **26 propiedades** y **2 valores** en esas cinco claves.
   Queda aplazado por insuficiencia de corpus real, no por falta de trabajo.

2. **`PUB` no bloquea nada, y eso hace barato estrenarlo.** Ver la semántica
   congelada más abajo.

3. **Este corte enciende el instrumento que hace contestable al Corte 1.** Al
   marcar PUB, el faltante para publicar empieza a reportar sobre propiedades
   reales. Esa es la medición que hoy no existe.

Y tiene valor propio: la partida registral existe **en un solo sitio de toda la
base**, `condicion_compraventa.partida_registral`, **con 0 filas**. Un inmueble
que nunca se puso en venta no tiene partida en ninguna parte, así que el broker
no puede verificar titular y cargas antes de firmar el encargo.

---

## La semántica de `PUB`, congelada para este corte

> **`PUB` = dato requerido para considerar completa la información de
> publicación, y debe aparecer en el faltante para publicar
> (`atributosQueFaltan` / `faltanParaPublicar`). V79 NO introduce un rechazo
> duro de la publicación.**

Esto no es una interpretación: es lo medido. `Exigencia.PUB` existe,
`impidePublicar()` devuelve cierto para ALT y PUB, y los dos repositorios
calculan el faltante con `exigencia in ('ALT','PUB')` — pero el contrato del DTO
dice **explícitamente** que *no es un error*: la ficha avisa, nadie rechaza. No
hay un solo `throw` colgado de ahí. Y hoy no existe **ninguna** fila con
exigencia PUB: 10 ALT + 59 OPC.

**Prohibido en V79:** introducir un `throw` nuevo, un bloqueo de publicación o un
cambio de flujo para hacer coincidir el código con una descripción documental
antigua. Si un documento gobernante afirma que «PUB bloquea», **el documento está
equivocado y se corrige**; el código no se dobla para darle la razón.

---

## Qué entra — migración **V79**, única

### Dos columnas estructurales

`propiedad.partida_registral` y `propiedad.oficina_registral`, destino
**ESTRUCTURAL**. Autoridad declarada en el catálogo como hizo D-E4-3: el
contrato lógico expone la clave, el consumidor no sabe dónde vive.

### Cuatro claves de catálogo

| clave | tipo | aplica_a | exigencia |
|---|---|---|---|
| `independizado` | BOOLEANO | D, O, L, A | **PUB** |
| `cargas_gravamenes` | LISTA_MULTIPLE | L, O, D, C, T, A | **PUB** |
| `area_segun_partida` | DECIMAL m² | C, T, A | OPC |
| `declaratoria_fabrica` | BOOLEANO | C, D | **PUB en C**, OPC en D |

`partida_registral` y `oficina_registral`: **PUB en los seis tipos**.

> `declaratoria_fabrica` lleva exigencia **distinta según el tipo**. Es legítimo:
> `catalogo_atributo_tipo` la guarda por fila. No se uniformice.

### Vocabularios

- `oficina_registral`: LIMA, CALLAO, HUAURA, CANETE, HUARAL, BARRANCA.
- `cargas_gravamenes`: NINGUNA, HIPOTECA, EMBARGO, SERVIDUMBRE,
  COPROPIEDAD_SIN_DIVIDIR, SUCESION_PENDIENTE, LITIGIO.

### Una resignificación

`condicion_compraventa.partida_registral` pasa a ser **la partida vigente en esa
venta, copia fechada de la del activo** — no su único domicilio. Hoy tiene **0
filas**, así que la migración de datos es vacía; lo que no puede faltar es
dejarlo escrito (comentario de columna y documento), porque el próximo que la
lea sin eso la volverá a tratar como origen.

---

## Alcance documental autorizado del CONSTRUCTOR

CONTROL **no** modifica los documentos gobernantes. Estas dos correcciones
quedan autorizadas dentro de este corte, y sólo estas:

**1. `mapa-ejecucion-brox.md` — actualización mínima.** Debe pasar a indicar:

- **Corte 2 · Identidad registral = siguiente**;
- **Corte 1 (resto) = aplazado por insuficiencia de corpus real**;
- la **razón de la inversión**: la evidencia del Corte 1 salía de la base de
  pruebas, el Corte 2 no depende de esa medición y estrena el instrumento que
  hace contestable al Corte 1.

**Sin reordenar el resto del roadmap.** Nada más de ese documento se toca.

**2. Corregir donde se afirme que «PUB bloquea».** Registrar la contradicción
entre la semántica medida y lo que digan los documentos gobernantes, y corregir
**el documento**, nunca el código. Candidatos conocidos: `pendientes-brox.md`
§2.1 («en `dev` prácticamente ningún local volvería a ser publicable») y
`auditoria-profundidad-inmobiliaria.md` donde repita la idea.

---

## Reglas que ya existen y no se rodean

- **Ningún valor por defecto.** La ausencia significa «todavía no se sabe»
  (regla del 3g). Ni siquiera `cargas_gravamenes = NINGUNA`: «ninguna carga» es
  una afirmación verificada, no el estado inicial.
- **`tg_catalogo_sistema_inmutable`** no estorba aquí —son claves nuevas—, y no
  se toca.
- **Guard de pares hecho/condición** (V77 ampliado, V78 completado): comprobar
  que ninguna de las seis es mitad de un par; si lo fuera, nace cubriendo la
  aplicabilidad de su condición o el gate lo dirá.
- **Ninguna clave nueva sin fila en `docs/ai/matriz-operacion-rol.md`** si
  aparece un endpoint. `PUT /propiedades/{id}` ya existe, así que probablemente
  no aparezca ninguno.

---

## Qué NO entra, y por qué

- **Cablear el bloqueo real de publicación.** Congelado arriba: PUB es aviso.
- **Extender a PROPIEDAD la guarda «ninguna LISTA sin vocabulario».** Parece la
  ocasión —`oficina_registral` es una LISTA de sujeto PROPIEDAD y nace con
  vocabulario— pero **rompería el arranque**: `servicios_disponibles` es una
  LISTA muda de sujeto PROPIEDAD y sus reemplazos nacen en el Corte 5. La guarda
  va con ellos, no aquí.
- **Corte 1 (resto).** Aplazado. **No decidir ahora** exigencias de `banos`,
  `zonificacion`, `pisos_edificacion` ni `frente`, y **no inventar vocabulario**
  para `servicios_disponibles`.
- **Las cuatro conversiones de tipo** bloqueadas por la invariante deliberada.
- **UI nueva.** El alta y el editor derivan del catálogo por
  `cl-campo-gobernado`: las seis claves deben aparecer solas. **Que aparezcan
  sin tocar Angular es una de las pruebas del corte**, no un supuesto.
- UI (§4), multi-tenancy y RLS (§5), E3, KAIROS, Cortes 3–7.

---

## Cierre — qué se exige para dar el corte por cerrado

- Gate del modelo universal y los gates de V77/V78, en verde.
- **E2E**: una captación de **alquiler** registra partida y oficina (hoy es
  imposible), y una solicitud de venta hereda la partida vigente como copia
  fechada.
- **La prueba del instrumento**: el faltante para publicar reporta las PUB
  nuevas sobre las 26 propiedades reales. Ese número es el insumo del Corte 1.
- **La prueba de que Angular no se tocó**: las seis claves se pintan solas.
- **Una sola corrida**: `verificacion/Verificar-Cierre.ps1` con `TEST_DB_URL`,
  más el build de producción de Angular. Sin nada más compilando.
- Evidencia en `backend-spring/verificacion/evidencia/`.

---

## Lo que este corte deja abierto a propósito

| | |
|---|---|
| Rotar el JWT y decidir qué pasa con `origin/main` | §0.2 y §0.3 de `pendientes-brox.md` |
| **El tipo `X` (OTRO) sigue quedándose atrás** | ninguna de las seis claves lo incluye: se queda en **3** claves aplicables. Sigue sin auditar |
| El ciclo largo de verificación | el gate corre **5 de 23** suites; sin periodicidad acordada |
| Tres tenants de prueba en `controllocal_dev` | `E2E-UNIVERSAL-A`, `E2E-UNIVERSAL-B`, `SIMULACRO-RECUPERACION` |
| `atributo_encargo` tiene **0 filas** | las 26 condiciones de V77 nunca se han escrito. El editor **sí** sabe escribirlas (`condiciones: [{ idEncargo, atributos }]`): es falta de uso, no hueco de construcción |

---

# ENMIENDA POSTERIOR AL PRECHECK — CONTROL — 2026-08-23

**Qué es esto.** El CONSTRUCTOR detuvo el corte antes de tocar nada y devolvió
`STOP — DECISIÓN REQUERIDA POR CONTROL`. CONTROL resolvió. Esta enmienda
**sustituye únicamente los puntos contradictorios** del encargo de arriba; todo
lo demás sigue vigente tal como está escrito, y el original **no se reescribe**
para que se pueda leer qué se creyó y qué resultó ser.

## Lo que el precheck midió, y corrige al encargo original

El apartado «La semántica de `PUB`, congelada para este corte» afirmaba que **no
hay un solo `throw` colgado de PUB**. Es falso, y la medición del CONSTRUCTOR lo
prueba:

| Evidencia | Dónde |
|---|---|
| `exigirPublicable(...)` termina en `throw new ReglaNegocioException("Todavia no se puede publicar: …")` | `PublicacionServiceImpl.java:186-214` |
| Se alcanza desde **dos** caminos: crear anuncio de un encargo, y pasar un anuncio a `PUBLICADO` | `PublicacionServiceImpl.java:94` y `:326` |
| La lista que alimenta ese `throw` filtra por ALT **y** PUB | `AtributosGobernados.faltantesDePropiedadParaPublicar` |
| `ReglaNegocioException` → **HTTP 400** | `ManejadorErroresApi.java:45` |

Y el comentario del DTO que el encargo citaba como prueba dice, entero:
*«`atributosQueFaltan` no es un error: es lo que permite a la ficha avisar de que
**no se puede publicar todavía**»*. Se leyó la primera mitad.

Hay un segundo hecho medido que cambia la conclusión: **no existe ninguna
superficie del cable que reporte una PUB de la PROPIEDAD**.
`PropiedadResponse.atributosQueFaltan` lleva **sólo ALT** (`esRequeridoPara` →
`bloqueaAlta()`), y `EncargoFicha.faltanParaPublicar` lleva ALT+PUB pero **sólo
del ENCARGO**. El único consumidor de la lista de PROPIEDAD es el `throw`.

De modo que, hoy, marcar PUB una clave de la PROPIEDAD hace **exactamente una
cosa**: rechazar la publicación con un 400. No informa de nada.

## El daño que se evitó, medido

- **26 de 26 propiedades reales de `controllocal_dev` pasan hoy el gate** de
  publicación (sólo hay 10 filas ALT y las 26 las cumplen). Con la tabla
  congelada del encargo original, **las 26 dejaban de ser publicables**.
- **Dos de las cinco suites del gate de cierre publican** y habrían caído en el
  400: `e2e-f4-solicitud.ps1:144` y `e2e-estabilizacion-alquiler.ps1:136`, las
  dos sobre un LOCAL creado con `metraje_total` y `rubro_permitido` y nada más.
- Ponerlas en verde habría exigido rellenar identidad registral en el fixture:
  **alterar un flujo comercial y modificar pruebas**, las dos prohibidas por el
  propio encargo.

## Lo que gobierna a partir de aquí

### 1. La semántica de la exigencia no se toca

```
ALT   bloquea el alta
PUB   bloquea la publicacion
OPC   no bloquea
```

**`PUB` sí bloquea publicar, y está bien que lo haga.** No se modifica
`PublicacionServiceImpl.exigirPublicable` y **no se crea una segunda semántica de
PUB**. Lo que estaba equivocado era el encargo, no el producto.

### 2. Las seis capacidades de V79 entran **OPC**, sin excepción

`partida_registral` · `oficina_registral` · `independizado` ·
`cargas_gravamenes` · `area_segun_partida` · `declaratoria_fabrica`.

**Ninguna se siembra PUB en V79.** La clasificación futura queda **sólo
documentada**, y no se implementa ahora:

| clave | destino futuro |
|---|---|
| `partida_registral` | futura PUB |
| `oficina_registral` | futura PUB |
| `independizado` | futura PUB donde corresponda |
| `cargas_gravamenes` | futura PUB |
| `area_segun_partida` | **OPC**, también en el futuro |
| `declaratoria_fabrica` | futura PUB en C · OPC en D |

Sustituye a la tabla «Cuatro claves de catálogo» y a la línea
«`partida_registral` y `oficina_registral`: PUB en los seis tipos».

### 3. El Corte 2 sigue delante del Corte 1, por otra razón

La justificación original —«enciende el instrumento que hace contestable al
Corte 1»— **queda retirada**: con las seis en OPC no se enciende ningún
instrumento, y construir uno sería trabajo que nadie autorizó.

**La razón vigente es otra y se sostiene sola:** la identidad registral es un
hueco estructural demostrado —la partida existe hoy en un único sitio de toda la
base, `condicion_compraventa.partida_registral`, con **0 filas**— y **se puede
modelar correctamente sin inferir nada del corpus contaminado de
`TEST_DB_URL`**. El Corte 1 (resto) no puede decirse lo mismo, y por eso sigue
aplazado.

**Prohibido dentro de V79:** construir Moat Health, un endpoint nuevo o una
superficie nueva de faltantes para rescatar la justificación anterior.

### 4. `oficina_registral`

Se mantiene **PROPIEDAD · ESTRUCTURAL · LISTA · vocabulario gobernado por el
catálogo**. Un valor fuera del vocabulario **debe rechazarse**.

La única autoridad de opciones es **`catalogo_atributo_opcion`**. Queda prohibido
duplicar `LIMA`/`CALLAO`/… en un enum Java, un `Set` escrito a mano, un CHECK SQL
enumerativo paralelo o en Angular.

Se construye **únicamente la guarda técnica mínima** para que un valor
estructural consulte el vocabulario gobernado, y **no se generaliza de forma que
rompa `servicios_disponibles`** —que sigue siendo una LISTA de PROPIEDAD sin
vocabulario, y sus reemplazos son del Corte 5.

### 5. `condicion_compraventa.partida_registral`

V79 **no** crea escritor nuevo, **no** crea fecha nueva, **no** implementa el
expediente de compraventa, **no** hace backfill y **no** la usa como autoridad
actual. La autoridad actual pasa a ser la Propiedad; la columna histórica se
preserva y se documenta.

**Se elimina del criterio de cierre la E2E del snapshot A→B**, y no se simula esa
funcionalidad con SQL para conseguir verde. Sustituye a la prueba obligatoria
nº 9 del encargo original y al segundo punto de «E2E» del apartado de cierre.

### 6. La cadena estructural, completa y vigilada

`partida_registral` y `oficina_registral` deben cubrir **simétricamente**:

```
catalogo / campo estructural -> escritor -> vaciado -> lector
                             -> soporte de escritura -> DTO / caso de uso
```

Y **debe existir una protección que falle si un campo ESTRUCTURAL puede
escribirse y no leerse**, genérica si la arquitectura lo permite. El hallazgo del
AUDITOR sobre `EscritorEstructural.leerValor` es crítico: su `default` devuelve
`null`, y `ValoresGobernados.Constructor.con` descarta un `null` **en silencio**,
así que un `case` de escritura sin su lectura simétrica guarda el dato donde
nadie lo lee y **no falla nada**.

### 7. El baseline de integración

La cifra canónica es **20 suites de integración**, no 22. `GateDeCierreTest`
inventaría 20 y `Verificar-Cierre.ps1` comprueba esas 20. **No se tocan los tests
para llegar a 22.**

## Lo que esta enmienda deja registrado para el futuro, sin resolver

**Si la futura PUB de la identidad registral debe afectar a VENTA y no a
ALQUILER.** Hoy la aplicabilidad de una clave de PROPIEDAD se declara por tipo
(`catalogo_atributo_tipo`) y no por operación; expresar «la partida bloquea
publicar una venta y es irrelevante en un alquiler» exigiría algo que el sujeto
PROPIEDAD no tiene. **Queda registrado. No se resuelve en V79.**
