# Pendientes de BROX — inventario completo

**Qué responde:** todo lo que queda por hacer, medido contra el repositorio y la
base de datos reales, no contra lo que los documentos dicen que falta.

**Actualizado el 2026-08-25**, después de publicar el cierre definitivo del
Corte 4.
Estado del árbol: rama `feat/modelo-universal-y-autoridad-del-dato`, commit
`795ffbf16384853b3e2c220895d4ac5ff6d01d06`, migraciones hasta **V83**.

> **Corregido el 2026-08-23 con el Corte 2 (`V79`)**, y sólo en lo que dejó de
> ser cierto: la rama **ya está publicada** (§0.1 y §10), la identidad registral
> **ya existe** (§2.5), las suites de integración son **20** y no 22, y las
> cifras de impacto de §2.1 **salían de `TEST_DB_URL`**, no del mercado. Lo
> demás se deja como se midió.

> **Estado actual del cierre:** Corte 4 cerrado definitivamente, auditoría final
> limpia. Cartera **medida el 2026-08-25, antes de `V84`: 7 publicables y 19
> bloqueadas de 26** — es un **registro fechado**; la cifra con autoridad
> después de la subtanda 5A sale de **su evidencia de cierre**, y no se sustituye
> aquí por una medición que nadie ha hecho. I0 está en curso y **el Corte 5 está
> ABIERTO**: el titular lo congeló el 2026-08-25 con D-1…D-7 y la **subtanda 5A**
> está en ejecución (`encargo-corte-5-terreno.md`).

**Cómo se hizo:** recorriendo `docs/ai/` (66 documentos), `backend-spring/`,
`frontend-angular/src/`, `backend-spring/verificacion/` y las dos bases
PostgreSQL vivas. Cada fila que dice «abierto» se comprobó; las que los
documentos daban por pendientes y **ya estaban hechas** están marcadas como
tales y se corrigieron en su documento de origen.

**Este documento no sustituye a nadie.** `mapa-ejecucion-brox.md` sigue siendo
la portada («dónde estamos») y
`checklist-captura-moat-e-inteligencia-inmobiliaria.md` los requisitos de cierre
de la etapa en curso. Esto es el **inventario transversal**: lo que ninguno de
los dos recoge entero porque está repartido en quince documentos.

---

## 0. Lo primero, porque no es técnico y bloquea todo lo demás

| # | Pendiente | Medido | Por qué importa |
|---|---|---|---|
| **0.1** | ~~**43 commits sin publicar.**~~ ✅ **RESUELTO el 2026-08-25**: la rama está publicada con el SHA final de Corte 4 | `git rev-parse @{u}` y `git ls-remote` → `795ffbf16384853b3e2c220895d4ac5ff6d01d06`; `origin/main..origin/<rama>` = **90** | La medición anterior era de 48 commits contra el remoto anterior. La rama actual está sincronizada; el cierre final añadió los dos commits documentales de la auditoría. |
| **0.2** | **Rotar el secreto JWT y las credenciales RDS publicadas** en `2832a9b` | `origin/main` = `2832a9b` | El commit que las publicó **es la cabeza de `main` en GitHub**. Y `backend-spring` reutiliza ese mismo secreto de firma. Sin GlassFish al que preservar compatibilidad, rotarlo es un cambio de configuración |
| **0.3** | **Decidir qué pasa con `origin/main`** | — | La historia pública se quedó en la v1. O se fusiona lo nuevo, o se declara que `main` no representa el producto |

> **0.1 y 0.2 están relacionados y el orden importa**: publicar la rama sin
> rotar antes no empeora nada (el secreto ya está publicado desde `2832a9b`),
> pero rotar y no publicar deja la rotación también en un solo disco.

---

## 1. Lo que bloquea la etapa en curso — E3 · Negociación

E3 es la única etapa 🟡 SIGUIENTE y está **bloqueada por tres decisiones de E0**
que hay que tomar **antes** de escribir la primera fila de oferta, porque las
tres cambian el dato que se persiste (`decision-hito-oferta-de-demanda.md` §«Dos
cuestiones abiertas»).

| # | Cuestión | Propuesta que ya existe en el documento | Estado |
|---|---|---|---|
| **1.1** | ¿Contra qué precio pedido se congela el *snapshot* de la oferta: `U` (lo autorizado, privado) o `P` (lo que el mercado vio)? | último `P` vigente, con caída a `U` si no hay anuncio, **dejando constancia de cuál se usó** | ⬜ sin decidir |
| **1.2** | ¿Dónde vive `O` sin mezclar dos negociaciones del mismo inmueble? `PrecioPropiedad` cuelga de la **propiedad**, y con dos interesados la serie interleava dos mesas distintas | referencia a la **oportunidad** | ⬜ sin decidir |
| **1.3** | Dónde consta lo declarado (menor) | — | ⬜ sin decidir |

**Además, y es nuevo desde que se escribió esa decisión:** el histórico
económico tampoco sabe **de qué encargo** es. Con venta y alquiler vivos a la
vez sobre la misma propiedad, las dos series se mezclan. Está anotado en
`decision-modelo-universal-propiedad-operacion.md` §2 como una de las seis cosas
que faltaban de verdad, y sigue abierto.

---

## 2. Profundidad inmobiliaria — lo que queda de los cortes de catálogo

Fuente: `auditoria-profundidad-inmobiliaria.md`. La cadena real de migraciones y
lo que ocupó cada una está en su §6. **`V84` ya está aplicada** — Corte 5 ·
subtanda 5A, 2026-08-25, pendiente de auditoría de CONTROL —; `V83` corresponde
al microcorte 4.P de procedencia. La siguiente migración de catálogo será `V85`,
y no se abre hasta que 5A esté auditada (D-4).

### 2.1 Corte 1 · mitad de PROFUNDIDAD ⬜ — APLAZADO

V78 cerró la mitad de **sujeto** (¿de quién es cada clave?). Queda la mitad de
**profundidad** (¿a qué tipos aplica y con qué exigencia?), que está medida e
inerte y sólo espera decisiones de negocio:

| Cambio | Medido | Qué falta decidir |
|---|---|---|
| `banos` → L, O, A | 406 valores, todos en C y D; cero en L, O, A | la exigencia |
| `zonificacion` → O | 584 valores en A, C, L, T; las 72 oficinas sin ninguno | ídem |
| `pisos_edificacion` → D, O | la clave más estrecha: **una sola fila**, en C | ídem |
| `frente` → C | 475 valores en A, L, T; ninguna casa | ídem |
| `interiorUnidad` / `nombreEdificioGaleria` → A | no son claves de catálogo: dos `Set.of` en `GuionRegistroPropiedad`, sin migración | ídem |

> **El flip a PUB es el cambio de mayor impacto operativo del corte** — y las
> dos frases con que se describía estaban mal las dos. Corregidas el
> 2026-08-23:
>
> 1. **Las cifras no eran del mercado.** «1 048 departamentos», «781 D / 407 L»
>    y «806 D / 139 C» salen de `controllocal_repositorios`, que **es
>    `TEST_DB_URL`**: la base donde cometen las 20 suites de integración, con
>    **2 871 propiedades** y **757 claves `zz_*`** de residuo. El corpus real es
>    `controllocal_dev`: **26 propiedades** —1 C, 1 D, 21 L, 2 O, 1 T— y 74
>    valores escritos. En `dev` no hay 1 048 departamentos: hay **uno**.
> 2. **«Prácticamente ningún local volvería a ser publicable» se queda corto, y
>    no es una figura**: `PUB` cuelga de `exigirPublicable`, que **lanza** y sale
>    como **HTTP 400** (`PublicacionServiceImpl:186`, `ManejadorErroresApi:45`).
>    Hoy las 26 propiedades reales pasan el gate; con esas claves en PUB
>    dejarían de poder anunciarse las 26. ~~Y **PUB no informa de nada**: no
>    existe ninguna superficie del cable que reporte una PUB de la
>    PROPIEDAD.~~ **Esa segunda frase caducó el mismo 2026-08-24**, en el commit
>    `35cf09c` (16:51): `PropiedadResponse.faltanParaPublicar` sale de
>    `clavesQueImpidenPublicar`, que filtra `exigencia in ('ALT','PUB')` sobre
>    sujeto **PROPIEDAD**, y Angular lo pinta en `propiedad-detail.html`. **PUB
>    sí informa.** Lo que sigue en pie de la advertencia es lo primero: el flip
>    **bloquea**, y ése es el coste que hay que escalonar o aceptar. La deuda
>    está marcada SALDADA más abajo en §2.5 bis; ésta era su otra mitad y decía
>    lo contrario. Corregido en el preflight del Corte 5, **2026-08-25**.
>
> **O se escalona, o se acepta y se dice.** ~~Hoy **ninguna** clave del sistema
> tiene exigencia PUB~~ — cierto hasta `V82` (2026-08-24), que subió
> **`tipo_acceso` a `PUB` en `L`**. Hoy el catálogo del sistema tiene **una**
> `PUB`; las seis que sembró `V79` sí entraron OPC a propósito. Corregido el
> **2026-08-25**.

### 2.2 Las conversiones de tipo, bloqueadas por una invariante deliberada

`tg_catalogo_sistema_inmutable` **prohíbe cambiar el `tipo_dato` de una clave
del sistema** («los valores ya escritos dejarían de significar lo mismo»). No es
un obstáculo a rodear: es la garantía. Las cuatro necesitan **otra** vía —clave
nueva + migración de datos + retirada de la vieja—, y cada una tiene además su
propio bloqueo de dato:

| Conversión | Bloqueo adicional | Dónde va |
|---|---|---|
| `cuota_mantenimiento` DECIMAL → **IMPORTE** | 784 filas con `valor_moneda` NULL al 100 %, y **ninguna fuente de la que deducirla**: `moneda_referencial` es la moneda de una renta, no la de un gasto de junta; el mismo importe 350 aparece bajo PEN (237 veces) y USD (56); el encargo vivo tiene 74 casos con monedas en conflicto | cuando la moneda se **declare** |
| `rubro_permitido` TEXTO → **LISTA_MULTIPLE** | 22 valores libres distintos, varios no mapeables con certeza; y cambia el almacén (los valores pasan a `atributo_propiedad_opcion`) | Corte 3/4, con vocabulario reconciliado |
| `zonificacion` TEXTO → **LISTA** | los 584 valores mapearían al 100 %; **el problema es el vocabulario nuevo**: derivarlo de lo observado daría 4 opciones y Lima tiene decenas (CV, CM, CE, RDB, I1–I4, OU, ZRP, ZTE…) | cuando salga de los planos de zonificación |
| `banos` DECIMAL → **ENTERO** + `medios_banos` | ~~`medios_banos` es clave nueva~~ ✅ **nació en `V80`** (Corte 3, 2026-08-24), y con él **la convención de `banos` ya está publicada en su `ayuda`** — un baño completo cuenta 1, un medio baño sin ducha cuenta 0.5. Era la precondición que faltaba: la migración de datos es determinista (379 de 406 valores son `.5` exactos) pero descansaba en una convención que nadie había escrito. **El estrechamiento en sí sigue pendiente** | corte propio, ya **desbloqueado** |

### 2.3 `servicios_disponibles` — la última LISTA muda · ✅ **RETIRADA en `V84`** (2026-08-25)

> **Esta sección estaba escrita en presente y dejó de ser cierta el mismo día en
> que se implementó 5A.** Se reescribe en pasado y se conserva el diagnóstico,
> porque explica *por qué* la clave sobrevivió muda durante cuatro cortes.

**Lo que era** (hasta `V84`): `tipo_dato='LISTA'` con **cero opciones
sembradas**. El trigger sólo valida pertenencia *si la clave tiene vocabulario*,
así que aceptaba cualquier cadena; y `MotorDeCaptura.controlDe` deriva el control
de si hay opciones, así que la dibujaba como **texto libre**. El dato entraba y no
comparaba con nada.

**Lo que se hizo** (`V84`, subtanda 5A; D-1 y D-2 del titular, 2026-08-25):

- **No se le inventó vocabulario** ni se le cambió el tipo. Se **retiró**:
  `activo = false`, nunca `DELETE`. La clave sigue existiendo, su fila de
  aplicabilidad `T/OPC` también, y **sus valores se conservan y se siguen
  leyendo** — incluidos rótulo y tipo, que la lectura resuelve aunque la clave
  esté inactiva. Lo que se cerró es la **escritura**.
- `agua_desague` nació LISTA con `CONECTADO` / `CON_FACTIBILIDAD_APROBADA` /
  `SIN_SERVICIO`, **`PUB` en `T`** y `OPC` en `A`.
- `energia_electrica` nació con el mismo vocabulario, **`PUB` en `T`**.
- `gas` **conservó su concepto, su clave, su tipo y su aplicabilidad** —no se
  migró ni se reinterpretó— y sólo **ganó** la opción `CON_FACTIBILIDAD_APROBADA`
  junto a la que ya tenía, `RED_EN_LA_VIA`. Son cosas distintas: red en la vía es
  infraestructura física; factibilidad aprobada es un documento de la
  concesionaria. **No se extendió `gas` a `X`**.
- El reparto del legado fue **después** de que existieran los reemplazos, con
  linaje de procedencia (`V83`). **No se tradujo nada**: las dos cadenas medidas
  —322 filas en la base de pruebas, 0 en `dev`— son **ambiguas**, porque ninguna
  dice si el servicio está conectado o sólo tiene factibilidad aprobada, que es
  justo la distinción que las claves nuevas existen para capturar. `SIN_SERVICIO`
  **no** es la traducción de «no lo mencionó». Y una cadena que el acta no
  inventaríe **detiene la migración nombrándola**, en vez de contarse en silencio.
- ✅ **Y su guarda gemela ya está puesta**: la comprobación «ninguna LISTA/
  LISTA_MULTIPLE **activa** sin vocabulario», que `V77` escribió sólo para
  `sujeto = 'ENCARGO'` —por eso esta clave sobrevivió muda—, se extendió a la
  PROPIEDAD en la misma migración y **después** de la retirada, o habría abortado
  contra su propia clave (comprobado: `EXIT=3` al invertir los bloques 6 y 7).

### 2.3 bis DEUDA ESTRUCTURAL · **doble autoridad de aplicabilidad** — bloquea declarar el Core estable

**Anotada el 2026-08-25** por exigencia de **D-5** (el titular decidió *no*
tocarlo en el Corte 5 · 5A, y a cambio dejarlo escrito). **Debe resolverse antes
de declarar CORE FOUNDATION STABLE**, es decir, antes de que el catálogo del Core
se dé por estable para Web y KAIROS.

**El hecho.** Tres claves —no dos, como se venía diciendo— tienen **las dos
autoridades a la vez**: `aplica_todos = true` **y** filas por tipo en
`catalogo_atributo_tipo`.

| clave | `aplica_todos` | filas por tipo | exigencia de esas filas |
|---|---|---|---|
| `antiguedad_anios` | `true` | los 7 | OPC en los 7 |
| `estacionamientos` | `true` | los 7 | OPC en los 7 |
| `metraje_total` | `true` | los 7 | **ALT** en los 7 |

**Quién gana, medido en el código, y por qué importa que no sea el mismo:**

| pregunta | quién responde | autoridad efectiva |
|---|---|---|
| ¿se **pregunta** en este tipo? | `CatalogoAtributoRepository.aplicablesA` | **el booleano** (`c.aplicaTodos = true OR exists(fila)`) |
| ¿se puede **escribir**? | el mismo camino, vía `exigir_atributo_gobernado` | **el booleano** |
| ¿con qué **exigencia**? | `AtributoPropiedadRepository.clavesObligatoriasQueFaltan` | **las filas** — `join c.aplicaciones a`, INNER |
| ¿**bloquea publicar**? | `clavesQueImpidenPublicar` | **las filas** — mismo INNER JOIN |

**La consecuencia, escrita sin rodeos:** una clave con `aplica_todos = true` y
**sin fila** para un tipo se pregunta en ese tipo y acepta valor, pero **su
exigencia es inalcanzable ahí**: ninguna de las dos consultas de bloqueo la ve,
porque las dos entran por INNER JOIN sobre las filas. Sería una `ALT` que no
obliga y una `PUB` que no impide — un bloqueo declarado que no existe.

**Y el guard de pares de `V78` (2.2) las excluye por predicado**: su condición
lleva `AND NOT h.aplica_todos`, así que ninguna de las tres participa nunca en la
comprobación «el hecho no llega menos lejos que su condición». `estacionamientos`
es una de las tres **y** es lado-hecho de un par deliberado
(`estacionamientos_incluidos`, pactado en `A,C,D,L,O` de alquiler): **ese par
nunca entra en el bucle**. Hoy pasaría igual —el hecho tiene fila en los siete—,
pero **está exento, no verificado**: el día que se le quite una fila, el guard
callará.

**Por qué hoy no se ve ninguna incoherencia, y por qué eso no es tranquilizador:**
las tres tienen fila para **los siete** tipos, así que las dos autoridades
coinciden. Y donde podrían discrepar, el defecto de la columna `exigencia`
también sería el mismo valor. **Coinciden por casualidad, no por construcción.**
El día que alguien añada un tipo, retire una fila o siembre una clave nueva con
`aplica_todos = true` y filas parciales, la incoherencia aparece **y no hay gate
que la vea**.

**Lo que la resolución tiene que decidir** (no se decide aquí): o el booleano
desaparece y la aplicabilidad es siempre explícita, o el booleano pasa a ser la
única autoridad y las filas se derivan, o se prohíbe por gate la combinación de
ambos. Las tres son cambios de contrato del Core, y por eso no caben dentro de un
corte de profundidad.

### 2.3 ter DEUDA · el rechazo de una clave retirada llega al cliente como «duplicado»

**Anotada el 2026-08-25**, encontrada auditando la conservación de 5A. **No entra
en 5A**: es preexistente y no se arregla en el sitio, sino en el mapeo global de
errores.

Un `PUT /propiedades/{id}` con una clave **retirada** muere en
`exigir_atributo_gobernado` con **`SQLSTATE 23503`** (`foreign_key_violation`), no
`23514`. Corregido el 2026-08-25 tras la segunda auditoría (N5) y **medido** en
`pg_proc`: una clave retirada no se encuentra —la consulta del trigger lleva
`AND c.activo = true`—, así que sale por la rama `IF NOT FOUND` con
`RAISE … USING ERRCODE = 'foreign_key_violation'`. Un valor fuera de vocabulario u
otra violación del mismo trigger sí salen con `23514`. Medido contra
`controllocal_dev`:

```
ERROR:  23503: El atributo "servicios_disponibles" no esta en el catalogo
CONTEXT:  PL/pgSQL function exigir_atributo_gobernado() line 16 at RAISE
```

El desenlace es el mismo por las dos ramas, y es el que importa aquí:
Hibernate lo envuelve en `DataIntegrityViolationException` y
`ManejadorErroresApi.unicidadViolada` lo traduce a **409 «Ya existe un registro
con esos datos: un dato único está duplicado.»**

Eso no es lo que pasó. No hay nada duplicado: hay una clave que ya no se captura,
o un valor fuera de vocabulario. El cliente recibe un diagnóstico **falso**, y el
agente que lo lee corrige lo que no está roto.

- **Ruta**: `backend-spring/controllocal-web/src/main/java/com/controllocal/web/http/ManejadorErroresApi.java`
  (líneas 102-110 y `mensajeDuplicado`, 152-164).
- **Por qué no se toca aquí**: el handler está registrado para **toda**
  `DataIntegrityViolationException` de la API — personas, documentos, correos,
  comisiones—, así que separar «violación de UNIQUE» de «violación de CHECK o de
  trigger» cambia el código de estado y el texto de **todos** los recursos, y hay
  contratos y pruebas colgando de ellos. Es un corte propio, con su medición de
  qué endpoints devuelven hoy 409 por esta vía.
- **Lo que sí está probado en 5A**: que el rechazo **existe** —una clave retirada
  no admite valores nuevos y el valor no se escribe—, en
  `OcupacionYServiciosIntegrationTest.serviciosDisponiblesQuedoRetiradaYNoBorrada`.
  Lo que falta es que el cliente sepa **por qué**.

### 2.3 quater DEUDAS que dejó la segunda auditoría de 5A (2026-08-25)

Cinco cosas medidas, **ninguna es regresión de este corte** salvo donde se diga, y
**ninguna se corrige aquí**: cada una tiene alcance propio. Se anotan con ruta y
condición de disparo para que no haya que volver a descubrirlas.

| # | Qué es | Ruta | Por qué no se toca ahora |
|---|---|---|---|
| **N3** | **La quinta superficie de la retirada: una clave ESTRUCTURAL retirada pierde su valor en la ficha.** `armar` inyecta el valor de una estructural sólo si su definición está en el mapa filtrado por `activo`, y `definicionesParaLeer` se aplica **después**, así que no la recupera. La columna canónica sigue llena; la ficha deja de mostrarla | `controllocal-service/…/soporte/LectorPorAutoridad.java:287-292`, contra `…/soporte/AtributosGobernados.java` (`definicionesParaLeer`) | **Hoy no se retira ninguna ESTRUCTURAL, así que no hay dato perdido.** Se dispara **el día que se retire una**. Arreglarlo es un cambio con su propio alcance —el lector tendría que preguntar por las estructurales retiradas *antes* de armar— y merece corte propio. El javadoc ya dice el límite exacto en vez de afirmar sin matiz que «retirar la pregunta no puede degradar la respuesta» |
| **N7** | **`CatalogoAtributoRepository.paraLeer` no filtra `sujeto` ni acota a una fila.** Misma debilidad que `porClave`: si una clave existiera con el mismo nombre en los dos sujetos, la lectura podría completar la definición del sujeto equivocado | `controllocal-persistence/…/repositorio/CatalogoAtributoRepository.java` (`paraLeer`) | **Hoy inocuo: 0 claves duplicadas entre sujetos, medido.** Es la misma corrección que `porClave` y se hace con ella, no antes |
| **N8** | **La comprobación 91 del gate se satisface con cualquier rastro anterior**, sin correlacionar el rastro con el **valor vigente**: un valor repartido, luego pisado por otro sin linaje, seguiría contando como declarado | `backend-spring/verificacion/gate-modelo-universal.sql`, comprobación 91 | **Hueco preexistente de 4.P, no regresión de 5A.** Correlacionar rastro y valor vigente es una capacidad del linaje —no de este corte— y afecta a todas las comprobaciones de la familia 76-80, no sólo a la 91 |
| **N9** | **`UnSoloLectorPorSujetoTest` no ve la dependencia `AtributosDeEncargo → AtributosGobernados`** que introdujo 5A (`completarRetiradas`, estático y compartido), porque el gate está escrito contra **entidades de dominio**. Cualquier método **no neutro** que se añada a `AtributosGobernados` quedará alcanzable desde el ENCARGO sin que ningún gate lo note | `controllocal-app/src/test/java/…/UnSoloLectorPorSujetoTest.java` | Ampliar el gate a dependencias **entre soportes de servicio** cambia lo que el gate significa y puede volverse ruidoso; es una decisión de arquitectura de pruebas, no un arreglo de 5A. Mientras tanto, la regla se sostiene por revisión: lo que se comparte es la consulta al catálogo, que **no tiene sujeto** |
| **78** | **La comprobación 78** (`4P después del cutover ninguna columna estructural sin linaje`) está **roja de base en cualquier copia de `controllocal_repositorios`**: **54** propiedades con `piso` sin linaje, creadas por pruebas **anteriores a 4.P**. Medido el 2026-08-25 desglosando el `CROSS JOIN` por campo: las 54 son de `PISO`, ninguna de los otros tres campos canónicos | la base `controllocal_repositorios` | **No bloquea el cierre** — `Verificar-Cierre.ps1` corre el gate contra `controllocal_dev`, donde está verde. Pero **envenena cualquier medición futura** que corra el gate sobre esa base y lea el exit-code: un rojo de residuo se confunde con un rojo de defecto. Quien mida ahí tiene que descontarla explícitamente, como hizo la evidencia de 5A |
| **V84:408** | **Un comentario de `V84` sigue afirmando que «un fixture las escribe en cada corrida»**, que es falso desde este mismo corte (N2). Es la única copia de esa frase que **no** se corrigió | `controllocal-app/src/main/resources/db/migration/V84__la_ocupacion_y_los_servicios_con_vocabulario.sql:408` | **`V84` está aplicada en las dos bases**: editar el fichero invalida el checksum y obliga a repetir el ciclo entero de H1. La regla del repositorio es *never edit an applied migration*, y una migración aplicada es además **evidencia fechada** de por qué se hizo lo que se hizo. La frase **no gobierna ninguna ejecución** —el gate y las suites, que sí deciden verde o rojo, están corregidos—, así que se declara falsa aquí en vez de reescribir el registro. Si algún día `V84` hay que tocarla por una razón funcional, esta línea se arregla en el mismo viaje |

### 2.4 Los hechos que faltan de un par deliberado — queda **uno**

El guard de pares vigila que un hecho y su condición no compartan sujeto, y V78
añadió que el hecho no llegue menos lejos que su condición. Eran cuatro; **`V80`
cerró el primero, `V81` el segundo y `V84` el tercero**, y queda **uno** —de la
subtanda 5B—, donde el pacto sigue siendo el único sitio donde cabe la verdad
física:

| Hecho que falta | Su condición, que ya existe | Corte |
|---|---|---|
| ~~`mascotas_reglamento`~~ ✅ **HECHO 2026-08-24 · `V80`**, y nació en **C y D** | `mascotas_aceptadas` | ~~3~~ |
| ~~`nivel_implementacion`~~ ✅ **HECHO 2026-08-24 · `V81`**, en **A, L y O** | `se_entrega_implementado` | ~~4~~ |
| ~~`estado_ocupacion`~~ ✅ **HECHO 2026-08-25 · `V84`**, en **los siete** — que es exactamente donde se pacta su condición | `entrega_desocupado` | ~~5A~~ |
| `lote_minimo_normativo` | `acepta_venta_fraccionada` | 5B |

> Que falte el lado PROPIEDAD **no impedía sembrar el lado ENCARGO** —la
> condición es cierta por sí sola— y por eso se hizo. Lo que hay que recordar al
> construirlos: **tienen que nacer cubriendo la aplicabilidad de su condición**,
> o el gate de V78 lo dirá.

> **Y lo dijo.** El plan de la auditoría daba `mascotas_reglamento` como «D».
> Medido antes de sembrar: su condición se pacta en `C/A` **y** `D/A`, así que
> nacer sólo en D habría roto `V80` en su propia guarda — se comprobó
> simulándolo, y la rompe. Nació en **C y D**. La lección para los tres que
> quedan es literal: **la aplicabilidad del hecho no se elige, se lee de la de su
> condición.**

### 2.5 Cortes 2 a 7 ⬜

| Corte | Qué trae | Migración |
|---|---|---|
| ~~**2 · Identidad registral**~~ ✅ **HECHO 2026-08-23** | `partida_registral` y `oficina_registral` como **estructurales**, más `independizado`, `cargas_gravamenes`, `area_segun_partida`, `declaratoria_fabrica` — **las seis OPC**, ninguna PUB. Se adelantó al resto del Corte 1 porque era un hueco estructural que se podía modelar **sin inferir nada del corpus contaminado**. Lo que queda fuera y sigue abierto: la promoción OPC→PUB, y el *snapshot* fechado de `condicion_compraventa.partida_registral`, que nace con el expediente de compraventa (bloque 6) | **V79** ✅ |
| ~~**3 · Vivienda (D, C)**~~ ✅ **HECHO 2026-08-24** | **30 claves** OPC —tipología, conservación, etapa de entrega, ascensores, vigilancia, áreas comunes, vista, bloque de baños/servicio, áreas exteriores, depósitos, torre— con 9 vocabularios (49 opciones) y 68 filas de aplicabilidad. Ninguna ALT, ninguna PUB. **Heredado a medias a propósito**: `medios_banos` nació y la convención de `banos` ya está publicada, pero **el estrechamiento sigue pendiente** (§2.2). Fuera: la promoción OPC→PUB, `familia` para agrupar un formulario que pasa de 25 a 55 campos (va con el corte del SPA), y `estacionamiento_independizado`, que el Corte 6 sustituye con `unidad_relacionada`. Un commit previo sin migración (`3.a`) arregló el gate `.sql`, **rojo desde `V77` y nunca ejecutado** | **V80** ✅ |
| ~~**4 · Comercial (L, O, A)**~~ ✅ **HECHO DEFINITIVAMENTE 2026-08-25** | **39 claves** con 18 vocabularios (83 opciones) y 71 filas de aplicabilidad. `V81`/`V82`, los siete pasos de conciliación y 4.P quedaron cerrados con auditoría final limpia en `795ffbf`. Cartera **medida el 2026-08-25, antes de `V84`: 7 publicables y 19 bloqueadas de 26** — también esto es un registro fechado, no un estado permanente: 5A estrena dos `PUB` en `T` y la cifra con autoridad sale de **su evidencia de cierre**. Las cifras `5/26` y `21 bloqueadas` son registros fechados de los pasos anteriores. | **V81/V82/V83** ✅ |
| **5 · Terreno y ocupación transversal** — 🟡 **ABIERTO 2026-08-25 · subtanda 5A en ejecución** | Congelado por el titular con **D-1…D-7**. **5A (`V84`)**: `estado_ocupacion` LISTA · **OPC en los siete**; `agua_desague` (**PUB en T**, OPC en A) y `energia_electrica` (**PUB en T**) nacen **con** vocabulario; `gas` gana `CON_FACTIBILIDAD_APROBADA` sin cambiar de concepto; `servicios_disponibles` → `activo = false` (**nunca `DELETE`**) tras repartir lo recuperable; y se extiende la guarda «ninguna LISTA activa de PROPIEDAD sin vocabulario». **5B** (no se abre hasta auditar 5A, D-4): parámetros urbanísticos, `condicion_terreno` —**`PUB`, no `ALT`**, por **D-3**—, `situacion_registral`, `fondo`, `tipo_via_acceso`, `lote_minimo_normativo`, `edificacion_existente` y la retirada de `area_terreno` en `T` (D-7). `manzana_lote` queda fuera del corte (D-6) | **V84** 🟡 |
| **6 · Unidades relacionadas** | una unidad con partida propia **es una Propiedad relacionada**, no un escalar dentro de un EAV: `unidad_relacionada`, códigos `E`/`B`, `unidadesRelacionadas[]` | — |
| **7 · Demanda y matcher** | unificar el vocabulario de tipo (hoy ALMACÉN y `DEPOSITO_ALMACEN` —el mismo concepto— se declaran no comparables), permitir que un requerimiento pida atributos gobernados, y **arreglar el sesgo**: un dato faltante hace que el criterio NO APLIQUE sin castigar el puntaje, así que **la propiedad peor capturada obtiene mejor puntaje** | — |

### 2.5 bis Deudas de base que el Corte 4 pagó

- **D-BASE-4 · `area_minima_arrendable.unidad` estaba en `m2`, sin acento.**
  Olvido de `V77`, que sí aplicó el `UPDATE` de acentos a sus hermanas.
  **Pagada en `V81`**: era la última clave del catálogo con `unidad = 'm2'` y
  ahora quedan **cero**. No se tocó ningún valor escrito — es el rótulo de la
  unidad, no el dato.
- **D-BASE4-1 · el gate de ida y vuelta no crecía con el catálogo.**
  `ConservacionDeLaEdicionIntegrationTest` prometía en su javadoc «la carga más
  ancha que el catálogo permite **hoy**» y llevaba listas escritas a mano.
  Medido antes de `V81`: **L 13 de 24, O 10 de 28, A 12 de 22** — treinta y una
  claves sin probar, siete de ellas sembradas por `V80` para el local. **Pagada
  en `V81`**: los seis casos llevan ahora todo lo que su tipo admite
  (L 40 · O 47 · A 50 · D 46 · C 35 · T 14) y **un test nuevo lo comprueba contra
  el catálogo**, así que la promesa dejó de depender de que alguien se acordara.

### 2.5 ter Lo que el Corte 4 dejó abierto — la decisión ya está tomada, quedan dos deudas

- ~~**DECISIÓN PENDIENTE DEL TITULAR · `ALT` tampoco deja registrar**~~ ✅
  **RESUELTA 2026-08-24 · `V82`.** El titular decidió que `tipo_acceso` quede en
  **`PUB`**: un local sin ese dato **se registra, se edita, se conserva y sirve
  para inteligencia — pero no se publica**. No hizo falta tocar Java: `V72` ya
  había construido ese nivel (`Exigencia.bloqueaAlta()` mira sólo `ALT`). **La
  publicabilidad no se movió** —siguen 5 de 26 y las mismas 21 bloqueadas—, que
  es la prueba de que el cambio hizo lo suyo y sólo eso. Evidencia:
  `verificacion/evidencia/2026-08-24-correccion-tipo-acceso-pub.md`.

  El diagnóstico que llevó a la decisión, tal como se midió: `exigirObligatorios` corre en
  `PropiedadUniversalServiceImpl.registrar:231` y **`editar:437` no lo llama**:
  las 21 propiedades ya existentes se siguen editando y **ninguna se ve
  afectada**, pero **desde el 2026-08-24 no se da de alta un local nuevo sin
  `tipo_acceso`**. No es un comportamiento nuevo —`metraje_total`, `dormitorios`
  y `zonificacion` ya lo tenían— y el Corte 4 **no relajó nada** para esquivarlo.
  El titular consintió sobre «no se puede anunciar»; sobre esto **no se le
  preguntó**. Roza `V75`/`V76` (registrar no es encargar; BROX conoce inmuebles
  que no gestiona), pero **el hueco es más estrecho de lo que parece**: hoy hay
  **cero** propiedades con `origen_incorporacion = OBSERVACION`, y `tipo_acceso`
  —a pie de calle, esquina, galería, pasaje— es justo lo que se ve desde la
  calle. Lo bloqueado no es el inmueble avistado, sino **el reportado por
  teléfono o desde un anuncio, sin nadie delante**. Separar «obligatorio para
  publicar» de «obligatorio para registrar» es **un corte propio**; rebajar la
  exigencia, no. Medido en §9 de
  `verificacion/evidencia/2026-08-24-corte-4-comercial.md`.
- **El test de completitud es ahora una obligación permanente, y crece.** Desde
  `V81`, **todo** corte de profundidad debe extender los siete casos de
  `ConservacionDeLaEdicionIntegrationTest` o el build rompe. Es exactamente lo
  que se quería, pero el fixture lleva ya **220** valores escritos a mano.
  Cuando el catálogo se acerque a 200 claves habrá que decidir si sigue a mano o
  **se deriva de `/captura/definicion`**, como ya hace `e2e-editor-universal`.
  Anotarlo ahora es más barato que descubrirlo en el Corte 7.
- ~~**DEUDA NUEVA DE `V82` · el bloqueo es real y NADIE lo anuncia**~~ ✅
  **SALDADA 2026-08-24**, en el corte corto que siguió a `V82` y **sin migración**.
  La PROPIEDAD reporta ahora su propia deuda en
  `PropiedadResponse.faltanParaPublicar`, con el mismo nombre con que el ENCARGO
  reporta la suya, y sale del **mismo método de dominio que decide el rechazo**
  (`faltantesDePropiedadParaPublicar`), no de una segunda matriz. Y la capacidad
  `publicacionGestionable.permitida` **dejó de contradecirla**: se deriva de las
  dos listas que la ficha ya calcula. Medido sobre las 26: **21 bloqueadas, 21
  con causa visible, 8 encargos vivos con `permitida = false`, y cero casos de
  `permitida = true` con faltantes conocidos**. Evidencia:
  `verificacion/evidencia/2026-08-24-senal-pub-visible.md`.

  El diagnóstico original, tal como se midió el 2026-08-24 **y antes de que el
  corte de la señal lo resolviera** — su presente ya no es el de hoy:

  > Al pasar a `PUB`, `tipo_acceso` **desapareció de `atributosQueFaltan`** (que
  > sólo lleva `ALT`) y **no aparece en `faltanParaPublicar`**, que es del sujeto
  > ENCARGO y por el guard 2.5 de `V78` **no puede llevar una clave de la
  > PROPIEDAD**. El encargo de la corrección predijo «7 avisan / 14 no»; **lo
  > medido es 21 → 0**. La barrera sigue en pie —publicar devuelve 400— pero
  > **ninguna superficie de lectura lo dice**. Hoy **no existe ningún sitio donde
  > una clave `PUB` de la PROPIEDAD se reporte**: construirlo es **un corte
  > propio**. El hueco queda fijado en
  > `CatalogoQueHablaIntegrationTest.elBloqueoNoSeAnunciaEnNingunaSuperficie`,
  > que se pondrá **rojo** el día que alguien lo construya — y esa es la señal.
- **El suelo del gate `.sql` sigue en 51 con 120 claves sembradas.** Es el
  límite honesto que su propio comentario declara desde `e8cfaa4`, y no es
  defecto de este corte — pero el margen ya es de **69 retiradas** antes de que
  salte. Quien vigila de verdad es la invariante de aplicabilidad, no el número.
- ~~**DEUDA NUEVA · la cuarta puerta de exposición no pregunta por la deuda de
  catálogo**~~ ✅ **SALDADA 2026-08-24 · y eran CINCO, no cuatro.** El preflight
  del microcorte encontró una quinta que no estaba en ningún inventario:
  **`sincronizar(idPropiedad, …)`** creaba la publicación, la dejaba en
  `PUBLICADO` y escribía el hito `P` **sin preguntar por una sola clave del
  catálogo** — residuo del formulario de la v1, borrada el 2026-08-08. **Las dos
  tenían cero consumidores de producción y ninguna estaba expuesta**, así que
  **se retiraron** en lugar de hacerlas delegar: una vía que delega sigue
  existiendo y puede desincronizarse en el próximo cambio; una que no existe no
  puede eludir nada. Quedan `crearEnEncargo` (crea) y `cambiarEstado` (publica),
  las dos con `exigirPublicable`, y `actualizar`, que **no toca el estado**. Lo
  fija `PuertasDePublicacionTest`, y se comprobó que muerde reintroduciendo la
  vía. Evidencia:
  `verificacion/evidencia/2026-08-24-puertas-de-publicacion.md`.

  El diagnóstico original, tal como se escribió:

  > `PublicacionServiceImpl.crear(idPropiedad, datos, actor)`
  > (`:135-147`) **no llama a `exigirPublicable`**: sólo comprueba
  > `exigirAlgunEncargo`. Las otras tres sí lo hacen — `crearEnEncargo:94` y
  > `cambiarEstado:326`, esta última en la transición a `PUBLICADO`, que es la que
  > expone al mercado.
  > **Hoy no es explotable y por eso no fue defecto del corte**: esa ruta **no está
  > expuesta** —el único endpoint de creación es
  > `POST /encargos/{idEncargo}/publicaciones`, que va por `crearEnEncargo`—,
  > verificado en los controladores y en la matriz.
  > Pero la cadena que el corte acaba de establecer —**regla → `faltanParaPublicar`
  > → `permitida` → acción visible**— tiene esa cuarta puerta sin guardar **en el
  > código**. El día que alguien la exponga, `permitida` diría `false` y la
  > publicación funcionaría igual. Se anota **ahora que la coherencia es
  > explícita**, no cuando alguien la abra.
- **Límite de cobertura, dicho en vez de presentado como total.** El barrido
  empírico del corte sólo ejercita **la rama de la PROPIEDAD**: las **112** filas
  de `catalogo_atributo_operacion` son **todas OPC**, así que hoy **ningún
  encargo puede tener un faltante ALT/PUB propio** y el escenario «el ENCARGO
  bloquea» **no es reproducible contra la cartera real**. Queda probado por
  construcción y por el test de integración, no por los datos. Cuando el Corte 5
  o el 7 siembren la primera condición ALT/PUB del ENCARGO, **ese escenario pasa
  a ser medible y hay que medirlo**.

### 2.5 quinquies La conciliación de los anuncios vivos — hecha, y lo que dejó medido

**2026-08-25.** `V82` bloqueó 21 locales, pero **cuatro seguían `PUBLICADO`**:
anuncios creados antes de que `tipo_acceso` existiera, que ninguna migración
cerró —correctamente, porque nadie lo autorizó—. Así que «los 21 salen del
mercado» **no era literalmente cierto**: cuatro seguían expuestos, uno de ellos
una **venta de USD 315 000**.

**Resueltos uno por uno, no en bloque.** En ninguno existía evidencia explícita y
trazable de cómo se entra al local, así que **no se escribió `tipo_acceso` en
ninguno** y los cuatro anuncios se **cerraron** por el mecanismo de dominio
(`cambiarEstado` → `C`, que no pasa por `exigirPublicable` porque **retirar del
mercado nunca puede estar bloqueado porque falte un dato**).

Medido después: **0** publicaciones `PUBLICADO` con faltantes `PUB` · **4/4**
explicados · **0** datos inventados · **0** pérdida histórica (12 publicaciones
antes y después, fechas de publicación e importes intactos) · **0** hitos `P`
artificiales (3 → 3; el de `PROP-0022` sigue siendo el registro original). Todo
remedido por auditoría independiente. Evidencia:
`verificacion/evidencia/2026-08-24-conciliacion-anuncios-bloqueados.md`.

**Lo que hace válido el cero**: el barrido demostró **encontrar cuando hay** —
halló el acceso explícito en `LOC-0001`, `LOC-0002` y `LOC-D001`. Un cero sin
control positivo no habría probado nada.

#### ✅ RESUELTO — los dos accesos documentados, conservados (2026-08-25, `df05903`)

**Esto ya no está abierto.** El titular decidió conservar los dos que eran
inequívocos, y se hizo:

```
LOC-D001  tipo_acceso = A_PIE_DE_CALLE     2026-08-25 02:56:02
LOC-0002  tipo_acceso = GALERIA_INTERIOR   2026-08-25 02:56:26
locales: 21 total · 2 con acceso · 19 bloqueados
```

**`LOC-0001` sigue sin valor**, por la razón de la tabla de abajo. Evidencia:
`verificacion/evidencia/2026-08-25-dos-accesos-documentados.md`.

> **La publicabilidad del catálogo pasó de `5 de 26` a `7 de 26`**, y las
> bloqueadas de **21 a 19** — medido, no restado. Cualquier cifra de «21
> bloqueadas» **en éste y en otros documentos** es un registro fechado de su
> microcorte, no el estado de hoy — incluidas **las dos que están más arriba en
> esta misma sección**, ya tachadas y con su evidencia.
>
> **Y `7 de 26` es, a su vez, un registro fechado del 2026-08-25 anterior a
> `V84`.** La subtanda 5A estrena dos `PUB` en `T`; el único terreno de la
> cartera, `PROP-0024`, pasa de publicable a bloqueado, que es el efecto buscado
> de `PUB`. La cifra con autoridad después de 5A la fija **su evidencia de
> cierre**, y ninguna cifra nueva se escribe aquí sin haberla medido.

El análisis que llevó a la decisión, que se conserva porque fija el listón:

De los **17** locales bloqueados que quedaban entonces, **tres llevaban acceso en
su descripción**, pero con el mismo listón sólo **dos** eran inequívocos:

| propiedad | texto | lectura |
|---|---|---|
| `LOC-D001` | «Local **a pie de calle** con vitrina» | **inequívoco** → `A_PIE_DE_CALLE` |
| `LOC-0002` | «Local **en galeria** del centro» | **inequívoco**, un punto por debajo: en Lima «galería» nombra a veces lo que el vocabulario clasificaría `CENTRO_COMERCIAL` |
| `LOC-0001` | «**en esquina**, primera linea de avenida» · zona «**Centro comercial** de Miraflores» | **NO inequívoco**: el mismo registro sostiene dos opciones **excluyentes** (`ESQUINA_A_CALLE` y `CENTRO_COMERCIAL`) y `tipo_acceso` es de valor único. Elegir sería la inferencia prohibida |

Ninguno tenía anuncio vivo, así que ninguno estaba en el alcance de la
conciliación. **Conservar un hecho que ya consta no es inventarlo**, así que los
dos primeros **se conservaron sin visitar**; `LOC-0001` cayó del lado de las
cuatro —hay señal, y se contradice consigo misma— y **sigue esperando visita**.

### 2.5 quater Enriquecimiento de Propiedad — capacidad de producto, capa calidad/moat

**Decidido por el titular el 2026-08-24**, al aceptar la consecuencia de `V82`.

**La regla, primero, porque cierra una vía que parecía cómoda:**
**`atributosQueFaltan` NO debe usarse como mecanismo genérico de
enriquecimiento.** Es la lista de lo que **bloquea el alta** —sólo `ALT`— y nada
más. Colgar de ahí «lo que le falta de profundidad a un inmueble» volvería a
mezclar dos preguntas que `V72` separó a propósito, y acabaría empujando claves a
`ALT` para que se vean, que es exactamente el movimiento que el Corte 4 tuvo que
deshacer.

**Lo que queda registrado como capacidad pendiente**, distinta y con su propio
sitio: **profundidad / enriquecimiento de la Propiedad**, especialmente para
inmuebles con **`origen_incorporacion = OBSERVACION`**. Pertenece a la capa de
**calidad / moat**, no al motor de exigencias.

**Sus límites, explícitos:**

- **No cambia `ALT`/`PUB`/`OPC`** de ninguna clave.
- **No entró en `V82`** ni podía: `V82` fue una fila y dos columnas.
- No se implementa reutilizando `atributosQueFaltan`.

**Qué la separa de la deuda de `V82` de arriba**, porque se tocan y no son lo
mismo: aquélla era **deuda de publicación** —una clave `PUB` que bloqueaba y que
ninguna superficie reportaba; **su arreglo ya se hizo** el 2026-08-24 (`35cf09c`,
§2.5 bis): las `PUB` faltantes se exponen junto a las `ALT` **sin fundir las dos
listas**, en `PropiedadResponse.faltanParaPublicar`—. Ésta es **deuda de conocimiento**: qué
le falta por saber a un inmueble que nadie ha encargado todavía. La primera tiene
21 casos concretos hoy; la segunda no tiene ninguno todavía —hay **cero**
propiedades con origen `OBSERVACION`— y nace para cuando los haya.

**Los 14 locales sin encargo vivo no reciben señal artificial**: no tienen deuda
de publicación, y fabricarles una sería inventar una obligación que nadie pactó.

### 2.6 Y una decisión que el Corte 1 dejó explícitamente sin tomar

**El tipo `X` (OTRO) se está quedando sin preguntas.** Hoy tiene exactamente
tres claves aplicables y las tres son de `aplica_todos`; quitar dos lo dejaría
con `metraje_total`. El plan dice «no abrir X, auditarlo antes de decidir si
sigue existiendo», y ningún corte lo ha auditado. **Que sea una decisión, no un
efecto colateral.**

---

## 3. Modelo universal — lo que `decision-modelo-universal-propiedad-operacion.md` daba por faltante

De las seis cosas que §2 de esa decisión declaraba pendientes:

| | Estado hoy |
|---|---|
| Multi-titular (`propiedad.id_rol_propietario` 1:1 `NOT NULL`) | ✅ resuelto — `titularidad_propiedad`, y la columna admite NULL desde V76 |
| Atributos gobernados (era una tabla por subtipo) | ✅ resuelto — `detalle_local_comercial` retirada en V71 |
| **El histórico económico no sabe de qué encargo es** | ⬜ **abierto** — bloquea E3 (§1) |
| **El requerimiento habla alquiler comercial** (`renta_min/max`, `rubro` obligatorio, un solo `tipo_inmueble`) | ⬜ **abierto** — Corte 7 |
| **La compraventa no tiene expediente** | ⬜ **abierto** — bloque 6 del mapa |
| PostGIS y outbox de eventos | ✅ resuelto — `propiedad.ubicacion geography(Point,4326)` y el outbox existen |

---

## 4. Interfaz — lo que queda del corte de UI

Fuente: `auditoria-ui-brox.md` (medición del 2026-08-17) y
`auditoria-residuos-semanticos.md`, **re-medidos hoy**.

### 4.1 Ya hecho, y el documento no lo decía

- `estadoRitmo` y la clasificación por asunto (E2.6), `DEPENDE_DE_MI` (E2.2) y
  la cola del broker (E2.5): tres de los cinco `BACKEND_FALTANTE`.
- El literal `Panel` del cascarón y la pantalla «Catálogos del sistema»:
  retirados.
- El menú: Locales→Propiedades, Dashboard→Inicio.
- `local-form` y `local-detail`: **borrados**; el alta y el editor universales
  los sustituyen y salen del catálogo.

### 4.2 Abierto, medido hoy

| # | Pendiente | Medida de hoy |
|---|---|---|
| **4.1** | **`BroxPageHeader` no existe**: cada pantalla se pinta su propia miga de pan | **50** ficheros con `class="miga"` |
| **4.2** | **«ControlLocal» sigue visible al usuario** | **6** plantillas (login, recuperar-acceso, cambiar-contrasena, enrolar-mfa, agente-form, broker-form) + `<title>ControllocalWeb</title>` en `index.html` |
| **4.3** | **«Cierres exitosos» no se renombró a «Contratos»** | `propiedades-alquiladas.html`, título y miga |
| **4.4** | **`locales.service.ts` sigue vivo** y con él el modelo plano de L/O | lo usan 4 pantallas: `ficha-propiedad`, `captacion-detail`, `captacion-form`, `captacion-review` |
| **4.5** | **`ficha-propiedad`** (la de `captaciones/:codigo/ficha`) lee el modelo heredado, no el universal | `import ... from '../../core/api/locales.service'` |
| **4.6** | **`GET /inicio` compuesto** no existe: el Inicio se arma con varias llamadas | no hay `InicioController` |
| **4.7** | **Capacidades por sesión** no existen | sin `capacidades` en `web/` |
| **4.8** | Las dos uniones: **Interacciones** dentro del expediente · **Reportes** como pestaña de Indicadores | mantienen el menú en 15/17/19 en vez de 13/15/16 |
| **4.9** | Unificar tabla de bandeja, filtros, badges, timeline, KPI y tokens; `agente-form`+`broker-form`; las 4 parejas duplicadas | 13 veredictos `UNIFICAR` |
| **4.10** | Rediseñar estado activo del menú, iconografía, **móvil (drawer)** | 5 veredictos `REDISEÑAR` |
| **4.11** | Subtítulo del login («Gestión comercial de locales») y patrón de progreso por pasos en el resto de altas | deuda menor |

### 4.3 Y lo que la auditoría de UI declaró **no auditado**

Sigue sin auditarse, y conviene no confundirlo con «está bien»:

- **Accesibilidad** — ni contraste real, ni foco, ni lectores de pantalla.
- **Rendimiento percibido** — no se ha medido pintado ni tamaño de *bundle*.
- **Glosario de textos** — el vocabulario funcional único **no está escrito**.
- **Las pantallas de seguridad** (`seguridad`, `enrolar-mfa`, `perfil`) —
  inventariadas, flujo sin revisar.

---

## 5. Multi-tenancy — el diseño está cerrado, la ejecución no

`arquitectura-multitenancy-colaboracion.md` §12 declara **todas las preguntas
resueltas** (D-18 a D-26). Lo que queda es implementación:

| # | Pendiente | Medido hoy |
|---|---|---|
| **5.1** | **RLS no está activado en ninguna tabla.** D-24 dice activarlo **antes del segundo tenant** | `select count(*) from pg_class where relrowsecurity` → **0** |
| **5.2** | `BROX_LEGACY` sigue siendo el único tenant real | 4 organizaciones: `BROX_LEGACY` (26 propiedades) + 3 de prueba |
| **5.3** | Separar cuenta de acceso de persona (D-22): `cuenta_acceso` global | `usuario_organizacion` existe; `cuenta_acceso` **no** |
| **5.4** | `canal_whatsapp` (D-21) y la bóveda de identidad de red (D-18) | no existen |
| **5.5** | F0/Locales/F2 se construyeron sin contexto de organización → añadir el contexto explícito de D-20 | — |

> **Y un residuo:** en `controllocal_dev` viven tres tenants de prueba
> (`E2E-UNIVERSAL-A`, `E2E-UNIVERSAL-B`, `SIMULACRO-RECUPERACION`) con 6
> `persona_rol` y cero propiedades. `AislamientoDePruebasTest` impide que eso se
> repita, pero lo que ya entró sigue ahí.

---

## 6. Producción — nada se despliega en público antes de esto

### 6.1 Configuración e identidad

| # | Pendiente |
|---|---|
| **6.1** | Rotar JWT y credenciales (§0.2) |
| **6.2** | Separar los *seeds* de desarrollo de los datos reales. `ValidadorConfiguracionSeguridad` **se niega a arrancar `prod`** mientras alguna credencial sembrada siga viva — está bien que así sea, pero significa que hoy `prod` no arranca |
| **6.3** | *Bootstrap* inicial de una organización real (hoy sólo existe la de legado) |
| **6.4** | Configuración fuera de `localhost`, imagen productiva, TLS/proxy |

### 6.2 Respaldo y almacén (bloque 9)

De `backend-spring/operacion/README.md` §8, y ninguno bloquea a S0:

| # | Pendiente | Por qué importa |
|---|---|---|
| **6.5** | **Copia de los binarios del almacén** | `pg_dump` guarda las claves, no los archivos: una restauración deja la base íntegra y **los documentos ausentes** |
| **6.6** | **Copia fuera de la máquina** | hoy el destino por defecto es el mismo disco |
| **6.7** | **Cifrado del respaldo en reposo** | el *dump* lleva datos personales en claro |
| **6.8** | **Alerta cuando el respaldo falla** | un *backup* roto desde hace semanas parece uno que funciona |
| **6.9** | **Elegir el servidor S3** y migrar los binarios antes de cambiar el proveedor por defecto | los archivos viven en el volumen `controllocal_almacen`, no en ningún *bucket*: cambiar primero muestra un almacén vacío. La herramienta (`MigracionAlmacen`, modos `conciliar`/`migrar`) ya existe |

---

## 7. Verificación — el gate de cierre cubre 5 de 23 suites

Existen **23 suites E2E** (más `e2e-context.ps1`, que es soporte). La corrida de
cierre ejecuta **cinco**:

```
en el gate:  comision-movimientos · disponibilidad-contrato · f4-solicitud
             estabilizacion-alquiler · editor-universal

fuera:       demanda-busqueda · e4-dashboard · f3-demanda · f6-f7-alertas-tareas
             ficha-comercial · locales-busqueda · locales-listado · personas
             reportes-propietario · solicitudes-busqueda · sonda-transporte · v6
             s0-bloqueo · s0-contrasenas · s0-emergencia · s0-mfa · s0-roles
             s0-sesiones
```

**Qué significa:** una regresión en personas, demanda, indicadores, alertas o en
**cualquiera de las seis suites de seguridad** no la detecta la corrida de
cierre. No es un descuido gratuito —las tres suites de búsqueda miden p95 en
esta misma máquina y son frágiles frente a una compilación en paralelo—, pero
**hoy no hay ninguna corrida que las pase todas**, ni una periodicidad acordada
para hacerlo.

Pendiente: decidir un **ciclo largo** (todas las suites, sin nada más corriendo)
y con qué frecuencia se ejecuta.

### 7.1 DEUDA · nada compara el checksum aplicado con el del classpath

**Anotada el 2026-08-25**, tras el hallazgo H1 de la auditoría de 5A.

Flyway lee las migraciones **del classpath**, no del árbol de fuentes. Si se
edita una migración y se reinicia el contenedor **sin `clean install`**, el jar
conserva la versión anterior: la base se queda con el checksum de un fichero que
ya no existe, y **nada avisa** — hasta el siguiente arranque con el jar bueno,
que muere con `Migration checksum mismatch for migration version N`. Pasó
exactamente eso con `V84` (evidencia
`2026-08-25-corte-5a-ocupacion-y-servicios.md` §10).

Hoy lo único que lo evita es **disciplina de runbook**. Lo que lo cerraría: una
comprobación de arranque —o una prueba del gate— que lea el checksum resuelto de
cada migración del classpath y lo compare con `flyway_schema_history`, fallando
con el nombre de la versión desalineada. **No entra en un corte de catálogo**:
toca el arranque de la aplicación, no el modelo.

---

## 8. Fuera de alcance declarado — que no vuelva a proponerse

No son pendientes: son cosas **decididas como fuera**, y conviene tenerlas juntas
para que nadie las reabra por descuido.

| Qué | Dónde se decidió |
|---|---|
| **Informes PDF** (los 5 endpoints Jasper de la v1) — no se portan y no hay tecnología elegida. **No añadir botones «Exportar PDF»** | D-F5-1, `decision-reportes-pdf-fuera-de-alcance.md` |
| Matcher v2, negociación E3, compraventa completa, Neo4j, WhatsApp, LLM, voz, *embeddings*, memoria vectorial, LangGraph y automatizaciones autónomas de KAIROS | checklist, cierre de los bloques 2 y 3 |
| Configuración de la política comercial **por organización** — declarada y sin implementar a propósito | `inventario-umbrales-de-dominio.md` |
| El mapeo estado → tono duplicado en diez pantallas | ídem |

---

## 9. Estado documental — qué se corrigió hoy y qué queda por vigilar

### 9.1 Eliminado

| Documento | Por qué |
|---|---|
| `contrato-local-form.md` | Describía `LocalForm` y las rutas `/locales/nuevo`, `/locales/:id/editar`, **borrados** en los bloques 3d y 3f. Se presentaba como «el patrón reutilizable para formularios de alta/edición del SPA», que hoy es `propiedad-form` + `propiedad-editor` + `cl-campo-gobernado`: no estaba desactualizado, estaba **enseñando lo contrario**. Cero enlaces entrantes. Queda en git |

### 9.2 Anotados hoy (siguen siendo útiles, pero ya no dicen la verdad presente)

| Documento | Anotación |
|---|---|
| `modelo-herencia-y-generalizacion.md` | **HISTÓRICO**: lee el esquema MySQL y las clases de `backend-java/`, borrados. Se conserva por su punto B, que es el germen del modelo universal |
| `estado-backend-para-el-inicio.md` | **CUMPLIDO**: E2 cerró el 2026-08-19. Su único hueco vivo (`GET /inicio`) pasa a §4.6 de aquí |
| `traspaso-inicio-a-angular.md` | **CUMPLIDO** |
| `encargo-sesion-kairos.md` | **NO EJECUTADO, y a propósito.** Da por vigente el estado del bloque 3 (2026-08-18); desde entonces entraron V71…V78. **Reescribirlo antes de usarlo** |
| `auditoria-residuos-semanticos.md` | **PARCIALMENTE RESUELTA**, con el estado real de sus cinco puntos |
| `auditoria-ui-brox.md` | Tres de sus cinco `BACKEND_FALTANTE` ya existen. Y aviso de colisión: sus «Corte 1/2» son de UI, no los del catálogo |
| `mapa-pantalla-dominio-backend.md` | Medido el 2026-08-17 y no re-medido: varias filas `DERIVADO_FRONTEND` ya no lo están |
| Los 8 `contrato-congelado-*.md` | Citaban rutas `backend-java/...` como «fuente de verdad» y esa carpeta no existe desde el 2026-08-08 |
| `auditoria-profundidad-inmobiliaria.md` | Su cadena de migraciones previstas (V71…V75) se quedó corta: la real llega a V78. Se añadió la tabla de lo realmente aplicado y se corrigió «Corte 2 · migración V78» → **V79** |

### 9.3 Trampas de numeración que conviene no olvidar

Hay **tres** planes que usan las mismas letras y números para cosas distintas:

| Numeración | De qué habla | Dónde |
|---|---|---|
| **E0…E9** | etapas de captura del *moat* (E0 histórico económico … E9 certificación) | `mapa-ejecucion-brox.md`, checklist |
| **E1…E5** | etapas de la **migración** (personas, reportes, ficha, dashboard, corte) | docs marcados HISTÓRICO |
| **Corte 0A…7** | cortes del **catálogo** inmobiliario | `auditoria-profundidad-inmobiliaria.md` |
| **Corte 1, 2** | cortes de **UI** | `auditoria-ui-brox.md` |

Y los **bloques 2…9** de la ruta a BROX 1.0, que son otra cosa más.

### 9.4 Lo que sigue vigente y no se ha tocado

El orden vigente sale **solo de tres sitios**, y son los mismos que nombra
`mapa-ejecucion-brox.md` § «Qué gobierna, y qué no»:

| Documento | Responde |
|---|---|
| `mapa-ejecucion-brox.md` | dónde estamos |
| `checklist-captura-moat-e-inteligencia-inmobiliaria.md` | qué falta para cerrar la etapa |
| `decision-*.md` (D-E…) | decisiones funcionales concretas |

> **Esta lista decía SIETE, y se ha devuelto a tres** (auditoría del 2026-08-25,
> N1). Enumeraba además `north-star-brox.md`,
> `auditoria-profundidad-inmobiliaria.md`, `matriz-operacion-rol.md` e
> `i0-industrializacion-brox.md` —este último **añadido por el propio Corte 5**,
> medido con `git diff 795ffbf 1b1cc0b`—. Ampliar la lista de lo que **gobierna**
> es un **cambio de autoridad documental**: contradice `CLAUDE.md` («only three
> documents govern») y **nadie lo decidió**; se coló dentro de cortes de
> catálogo, en tres sitios distintos. Queda **como decisión pendiente del
> titular**, no resuelta por vía de los hechos. El mapa lleva este mismo aviso,
> con las mismas palabras, para que los tres sitios digan lo mismo.
>
> Que no gobiernen no significa que no se lean: `north-star-brox.md` es el marco
> contra el que se mide un avance, `auditoria-profundidad-inmobiliaria.md` es la
> fuente de los cortes de catálogo, `matriz-operacion-rol.md` es referencia
> autoritativa de quién llama a qué —y además está vigilada por un test— e
> `i0-industrializacion-brox.md` es el protocolo de ejecución en curso. Lo que se
> discute es si **mandan**, y eso lo decide el titular.

El `encargo-corte-5-terreno.md` prepara el siguiente corte, pero no lo abre. Los
documentos con banner HISTÓRICO de la era de la migración se conservan tal cual:
explican el **porqué**, y CLAUDE.md ya avisa de que no gobiernan.

---

## 10. Si sólo se puede hacer una cosa

~~**Publicar la rama.**~~ ✅ **Hecho el 2026-08-25.** La rama
`feat/modelo-universal-y-autoridad-del-dato` y su remoto coinciden en
`795ffbf`. El árbol está limpio y el cierre definitivo de Corte 4 ya está
publicado.

~~**La siguiente acción de la ruta es I0:** terminar la ordenación documental y
dejar congelado el encargo de Corte 5 antes de abrir una migración `V84`.~~

> **Superado el mismo 2026-08-25.** El titular congeló el encargo del Corte 5
> (D-1…D-7) e I0 dejó de bloquearlo: **la subtanda 5A está implementada y `V84`
> aplicada**, pendiente de la auditoría de CONTROL. I0 sigue en curso en
> paralelo (`i0-industrializacion-brox.md`).

La rotación del secreto JWT (§0.2) sigue siendo una prioridad de producción,
pero no es el siguiente corte de catálogo ni se mezcla con esta preparación.
