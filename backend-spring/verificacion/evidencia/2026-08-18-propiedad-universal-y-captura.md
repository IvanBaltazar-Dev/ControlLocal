# Evidencia · Propiedad Universal Operativa + Captura v1

**Fecha:** 2026-08-18
**Sigue a:** `2026-08-17-nucleo-universal-entidades-repositorios.md` (entidades y repositorios).
**Decisiones:** `decision-modelo-universal-propiedad-operacion.md` (D-E4-1) ·
`decision-motor-de-registro.md` (D-E4-2) · `decision-kairos-contrato-de-acciones.md` (D-K-1).

**Cierra los bloques 2 y 3** de la ruta a BROX 1.0.

---

## El recorrido que ahora pasa entero

```
 1. Inicio el registro de una propiedad.          POST /captura
 2. BROX sabe que es departamento + venta.        (lo anoto el borrador)
 3. Consulta el catalogo y decide que aplica.     catalogo_atributo por tipo
 4. Detecta lo que falta.                         titular, direccion, dormitorios…
 5. Persiste el borrador.                         borrador_captura CAP-00001
 6. Completo los datos.                           POST /captura (otro canal)
 7. BROX registra en UNA transaccion:             POST /captura/{id}/ejecutar
      propiedad · titulares · atributos
      operacion · precio · historico · evento
 8. La vuelvo a consultar.                        GET /propiedades/{id}
 9. La modifico.                                  PUT /propiedades/{id}
10. El historico anterior permanece.              U 180 000 → U 175 000
11. Repito el mismo comando.                      Idempotency-Key
12. BROX no duplica nada.                         reintento = true
```

Lo cubre `PropiedadUniversalIntegrationTest.borradorInterrumpidoYRetomado` de
punta a punta, contra PostgreSQL real.

---

## Lo que se cerró primero: los dos pendientes técnicos

### 1 · `SimulacroRecuperacionIntegrationTest` — aislado

Fallaba con *«La organizacion ya tiene un administrador operativo»* y no era un
fallo de código: la prueba **leía** el estado de la base compartida en vez de
construir el suyo. Una recuperación de emergencia solo tiene sentido en un
tenant sin gobierno, y la organización 1 lo tiene.

Ahora el simulacro **construye su precondición**: tenant propio
(`SIMULACRO-RECUPERACION`) con un `TENANT_ADMIN` activo y **sin factor MFA** —
membresía válida para el trigger de V44, pero no operativa, que es la definición
misma de la emergencia. Una aserción en el `@BeforeEach` deja constancia de que
la precondición se cumple antes de empezar.

**4/4, y repetible.** La limpieza va acotada a su tenant: el simulacro ya no
puede estropear el estado de otra organización ni depender de él.

### 2 · La operación inferida en silencio — retirada

`PrecioPropiedad.operacion` tenía `= OPERACION_ALQUILER` y `Captacion.motivoOperacion`
tenía `= "A"`. Mientras el sistema solo supo alquilar, eso era invisible; con la
venta dentro, **archiva un precio de venta en la serie de alquiler y ningún CHECK
puede notarlo** — 180 000 es un número perfectamente legal para una renta.

| Antes | Ahora |
|---|---|
| `PrecioPropiedad.operacion = "A"` | sin defecto; `setOperacion(null)` **lanza** |
| `Captacion.motivoOperacion = "A"` | sin defecto; el setter exige VENTA o ALQUILER |
| `textoO(…, "A")` en `CaptacionServiceImpl` | sin último recurso |
| `POST /locales/{id}/precios` suponía alquiler | resuelve del encargo vivo, o **rechaza** |

Los cuatro productores heredados ahora **declaran** su operación en el punto
donde escriben, con el motivo al lado. La diferencia no es cosmética: un defecto
en la entidad se aplicaba también al camino universal, donde la operación puede
perfectamente ser VENTA.

> `OperacionDelEncargo` es el sustituto: declarada gana, si no la deduce del
> **único** encargo vivo, y si hay dos —o ninguno— **declara que falta** en vez
> de suponer.

---

## Lo que se construyó

### Base — cinco migraciones

| | Qué cierra |
|---|---|
| **V54** | El **séptimo tipo**: `ck_propiedad_tipo` admitía seis y el catálogo de V48 ya preguntaba por siete. El catálogo sabía describir un almacén y la tabla no dejaba registrarlo. |
| **V55** | **Gobierno del catálogo**: una organización no puede borrar, retipar, apropiarse ni **sombrear** un atributo común de BROX. Lo suyo sí puede crearlo. |
| **V56** | `borrador_captura`: el estado transaccional de una captura incompleta. |
| **V57** | `comando_idempotente`: unicidad `(organizacion_id, idempotency_key)` + huella + resultado. |
| **V58** | **Un encargo vivo por (propiedad, OPERACIÓN)**, no uno por propiedad. |

### V58 es el hallazgo de la tanda

V50 añadió `uq_captacion_viva_por_operacion` y dio por admitida la venta y el
alquiler simultáneos. **Pero dejó en pie el índice heredado de la v1:**

```sql
uq_captacion_activa_por_local  UNIQUE (organizacion_id, id_propiedad) WHERE estado = 'A'
```

Ese índice no distingue operación. Con los dos encargos PENDIENTES todo
funcionaba —y así se verificó V50, con sus 47 comprobaciones sobre encargos en
`P`—; en cuanto el broker aprobaba el segundo, la base lo rechazaba. **El modelo
universal se rompía justo en el paso que lo hace útil.**

El índice de V50 es estrictamente más fuerte por operación: impide dos vivas en
cualquier combinación de P, O y A. Lo único que el viejo añadía era prohibir
cruzar operaciones, que es exactamente lo que hay que permitir. La guarda de
Java se estrechó igual, y su mensaje enseña la alternativa.

### Dominio

`OperacionInmobiliaria` (VENTA/ALQUILER, congelado) · `BorradorCaptura` ·
`ComandoIdempotente` · `PrecioPropiedad.hito(...)` como única forma prevista de
construir un hito completo.

**El enum rechaza con explicación, no con «valor inválido»:** `AMBAS` responde
que se representa con dos encargos independientes; `COMPRA` responde que es una
perspectiva y no una operación —comprar es VENTA vista desde el cliente— y que
el lado lo dice el rol. Un mensaje que dice *por qué* evita el ticket siguiente.

### Servicio

| Pieza | Qué resuelve |
|---|---|
| `PropiedadUniversalService` | alta, lectura y edición por el modelo nuevo |
| `service/captura/MotorDeCaptura` | qué se sabe, qué falta, qué se pregunta ahora |
| `GuionRegistroPropiedad` | lo **estructural** de la intención; lo demás sale del catálogo |
| `AtributosGobernados` | convierte y valida contra el catálogo, y calcula lo que falta |
| `ComandosIdempotentes` | un comando se ejecuta una vez aunque llegue dos |
| `OperacionDelEncargo` | de qué operación es un importe, o por qué no se sabe |
| `Documentos` | el JSON de las tres columnas de texto, con Jackson y no a mano |

### Cable — 9 operaciones nuevas

`POST /propiedades` · `GET /propiedades/{id}` · `PUT /propiedades/{id}` ·
`GET /propiedades/catalogo/{tipo}` · `POST /captura` · `GET /captura` ·
`GET /captura/{id}` · `POST /captura/{id}/ejecutar` · `DELETE /captura/{id}`

Con sus filas en `matriz-operacion-rol.md`, que es lo que el gate exige.
Dos cabeceras cruzan el bloque: `Idempotency-Key` y `X-Origen`
(UI · KAIROS · API · SISTEMA).

---

## Tres decisiones que conviene conocer

### 1 · `/propiedades` es un recurso nuevo, no más verbos en `/locales`

`/locales` es el alta de la v1: un local comercial en alquiler, un propietario,
el precio en una columna. Estirarlo habría dejado un recurso con **dos
comportamientos según qué campos llegaran**, y con las 57 pantallas actuales
colgando del comportamiento viejo. Los dos escriben en las mismas tablas.

### 2 · Tres columnas viejas se siguen escribiendo, como proyección

`propiedad.id_rol_propietario`, `precio_referencial` y `moneda_referencial` son
NOT NULL y las lee todo el cable actual. El alta universal las escribe derivadas
de la fuente nueva: el **representante** de la titularidad, y el importe del
encargo de **alquiler** si lo hay —la columna se llama «renta referencial» en
media docena de sitios y una venta ahí haría que los listados mostraran 180 000
donde esperan una mensualidad—. Quitarlas es una tanda propia.

### 3 · Avanzar y ejecutar están separados

`POST /captura` anota y no escribe nada del negocio; `POST /captura/{id}/ejecutar`
corre el caso de uso. Un canal conversacional necesita poder **confirmar antes
de escribir**, y con una sola llamada no habría momento para confirmar.

---

## Verificación

### `PropiedadUniversalIntegrationTest` — 24/24

**Es el único test que COMETE de verdad**, en dos tenants propios
(`E2E-UNIVERSAL-A` y `-B`) que limpia antes de cada corrida. No es comodidad:
cuatro de las invariantes solo existen en el COMMIT —el constraint trigger
diferido de las cuotas, la idempotencia entre comandos, el rollback de un alta
fallida y los dos encargos simultáneos—. El **segundo** tenant no es decorativo:
sin él, «el aislamiento funciona» es una afirmación sin prueba.

| Escenario | Demuestra |
|---|---|
| Local + alquiler + un titular | compatibilidad con el negocio existente |
| Departamento + venta | el hito queda en la serie `V`, no en la de alquiler |
| Casa + alquiler | el uso se deduce del tipo: una casa es vivienda |
| Terreno + venta | pide zonificación y **no** pide dormitorios |
| Titulares 60/40 | copropiedad, con un solo representante |
| Cuotas que suman 90 | rechazo **antes** de escribir, diciendo cuánto suman |
| Venta **y** alquiler a la vez | dos encargos, dos precios, **dos históricos** |
| Dos veces la misma operación | rechazo |
| `AMBAS` y `COMPRA` | rechazo con la explicación, no con «valor inválido» |
| Atributo no aplicable al tipo | rechazo nombrando el tipo |
| Atributo obligatorio ausente | se declara faltante, con su nombre |
| Clave inventada | rechazo |
| Tenant redefine `dormitorios` | la base lo impide, y borrarlo o retiparlo también |
| Tenant añade `vista_al_mar` | se admite, se usa, y el vecino no lo ve |
| Tenant B lee/edita lo de A | **404**, no 403 |
| Misma clave de idempotencia | una sola propiedad, mismo resultado, `reintento = true` |
| Clave reutilizada con otro contenido | rechazo |
| Fallo al final del alta | rollback completo: ni propiedad, ni titular, ni evento |
| Crear → leer → editar → leer | round-trip por las estructuras nuevas |
| Cambiar el precio | `U 180 000` → `U 175 000`, append-only |
| Editar titulares | la titularidad anterior se **cierra**, no se borra |
| Origen KAIROS | `evento_dominio.origen = KAIROS`, con su actor |
| Borrador interrumpido y retomado | el recorrido completo de 6 pasos |
| Ejecutar a medias | rechazo con la lista de lo que falta |
| Valor inválido | se rechaza **al anotarlo**, no al final |

### Reactor completo

```
controllocal-service     631 pruebas
controllocal-web          43
controllocal-app         142   (incluidas las 10 de integracion)
                        ----
                         816   0 fallos · 0 errores · 0 omitidas
```

**Sin errores conocidos arrastrados.** El único que quedaba —el simulacro— se
cerró en esta misma tanda.

### Gates de arquitectura — 13/13

Capas · tenancy · auditoría · política única · matriz operación→rol · inventario
de integración. Dos de ellos **encontraron trabajo real**:

- **`PoliticaUnicaTest`** vio un `plusMonths(6)` escrito a mano en el alta
  universal y exigió `PoliticaComercial.finDelEncargo(inicio)`. Tenía razón: era
  una segunda copia de la duración del encargo, y las copias divergen en
  silencio.
- **`MatrizOperacionRolTest`** no dejó entrar las 9 operaciones nuevas sin su
  fila.

### Gate del contrato (nodo) — 165/165 (eran 160)

```bash
node docs/ai/modelo/gate-modelo-universal.js
```

Una comprobación **falló y tenía razón a medias**: pinchaba la validación de la
operación *en línea* dentro de `Captacion.java`
(`!"A".equals(...) && !"V".equals(...)`), y esa línea ya no existe — se mudó a
`OperacionInmobiliaria`, que además rechaza el nulo. El gate pasa a comprobar la
**intención** en vez de una implementación concreta, y de paso añade cuatro
comprobaciones que antes no podía hacer: que el vocabulario esté congelado en
dos valores, que `AMBAS` y `COMPRA` se rechacen **con explicación**, y que ni el
encargo ni el hito de precio tengan valor por defecto.

### Gate SQL del modelo — 54/54 (eran 47)

```bash
docker cp verificacion/gate-modelo-universal.sql controllocal-postgres-v2:/tmp/gate.sql
docker exec controllocal-postgres-v2 psql -U controllocal -d controllocal_dev -q -f /tmp/gate.sql
#  en verde: 54 · en rojo: 0
```

Las siete nuevas cubren lo que esta tanda añadió, incluidas las dos que
existen **por el fallo que se encontró**:

```
GOB   una organizacion no puede sombrear una clave del sistema
GOB   un atributo del sistema es inmutable en clave y tipo
GOB   ninguna fila de tenant sombrea hoy una clave comun
TIPO  propiedad admite los SIETE tipos, incluido el almacen
OP    el indice viejo de una activa por local ya NO bloquea
OP    la invariante vigente es un encargo vivo por operacion
IDEM  un comando no se puede repetir dentro de una organizacion
```

### `ddl-auto: validate` y Flyway

```
Successfully applied 58 migrations to schema "public", now at version v58
Started ControlLocalApplication
```

Ninguna de las tres entidades nuevas quedó desalineada. **Las 58 aplican sobre
una base vacía**, no solo de forma incremental: es lo que hace cada suite E2E,
que levanta su PostgreSQL propio en `tmpfs`.

### Corrida de cierre — `Verificar-Cierre.ps1`

```
== 2. Reactor completo contra PostgreSQL real ==
   controllocal-service   631      controllocal-web    43
   controllocal-app       142      ------------------------
                                   816   0 fallos · 0 errores · 0 omitidas

== 3. Los tests de integracion se EJECUTARON, no se saltaron ==
   OK   los 10, comprobados en la salida del reactor

== 4. Suites E2E del cierre ==
   comision-movimientos      65 OK / 0 fallas
   disponibilidad-contrato   41 OK / 0 fallas
   f4-solicitud             125 OK / 0 fallas
   estabilizacion-alquiler   18 OK / 0 fallas

== CIERRE VERDE ==
```

**0 fallos · 0 errores · 0 regresiones conocidas.** No se traslada ningún error
al siguiente corte: el único que arrastraba el repositorio —el simulacro de
recuperación— se cerró en esta misma tanda.

---

## Lo que encontró el E2E: tres fallos que el reactor no podía ver

Hicieron falta **cuatro corridas de cierre**. Las tres primeras pasaron el
reactor entero —816 pruebas, los 10 tests de integración incluidos— y murieron
en las suites E2E, cada una por un fallo distinto y los tres reales.

**La causa común de que ninguno se viera antes:** los tests de servicio simulan
el repositorio, así que un campo obligatorio que nadie rellena **nunca llega a
PostgreSQL**; y los tests de humo son GET. Una columna NOT NULL sin escritor solo
se destapa cuando alguien escribe de verdad por el cable.

### Fallo 2 · `captacion.motivo_operacion` desde una prospección

`POST /prospecciones/{id}/captar` —**el camino normal para captar**— construye la
`Captacion` a mano en vez de pasar por `CaptacionServiceImpl.registrar`, y
dependía del defecto `= "A"` de la entidad. Al retirarlo dejó de escribirse:
captar desde una prospección fallaba entero.

Corregido tomando la operación de **su propia condición económica**, que la
declara tres líneas más arriba: son la misma declaración, y
`tg_captacion_operacion_coherente` (V50) existe precisamente porque dos sitios
divergen. Con su prueba de regresión en `ProspeccionServiceImplTest`.

> La lección, anotada: al retirar un valor por defecto hay que barrer **todos**
> los sitios de construcción, no solo el servicio que lleva el nombre de la
> entidad. Son tres para `Captacion`, cinco para `PrecioPropiedad` y uno para
> `SolicitudAlquiler`.

### Fallo 3 · el barrido de tenancy no conocía el catálogo híbrido

`f4-solicitud` y otras tres suites cuentan las filas con `organizacion_id NULL`
**en toda la base** y exigen cero. Con V48 hay 19 legítimas: los atributos
comunes de BROX, que llevan el discriminador NULL **a propósito** — son las
mismas para toda corredora y son lo que permite que dos propiedades se comparen.
Si llevaran tenant, el vocabulario dejaría de ser común y el matcher entre
organizaciones no podría existir.

La aserción se estrechó en las cuatro suites excluyendo `catalogo_atributo`, con
la razón escrita al lado. **Es la misma excepción que `ArquitecturaTenancyTest`
ya declaraba** desde que se creó la entidad; el barrido en SQL simplemente no se
había enterado. La invariante sigue viva para las otras 47 tablas.

### Fallo 1 · `solicitud_alquiler.tipo`

La primera corrida de cierre pasó el reactor entero y **murió en la primera
suite E2E**:

```
ERROR: null value in column "tipo" of relation "solicitud_alquiler"
       violates not-null constraint
```

**V51 añadió `solicitud_alquiler.tipo` NOT NULL y la entidad no lo mapeaba**, así
que *ningún expediente se podía crear*. Es exactamente el mismo fallo que tuvo
`precio_propiedad.operacion` con V49, descrito en la evidencia anterior — y
entró **dos veces** porque nada lo vigilaba.

### Por qué `ddl-auto: validate` no lo ve

Es la parte que sorprende: `validate` comprueba que las columnas que la entidad
**mapea** existan en la tabla. No comprueba lo contrario — que la entidad mapee
todas las que la tabla **exige**. Una columna obligatoria y desconocida para JPA
arranca perfectamente y revienta en el primer INSERT. Y como las pruebas de humo
son GET, el fallo viaja hasta que alguien escribe de verdad.

### Lo corregido

- `SolicitudAlquiler.tipo` mapeado, con `derivarTipoDe(operacionDelEncargo)` en
  vez de un setter libre: **el tipo no se elige**, lo determina el encargo del
  que cuelga la oportunidad, y `tg_expediente_tipo_del_encargo` (V51) lo exige
  en la base. El día que una oportunidad venga de un encargo de VENTA, el
  expediente nacerá de compraventa sin tocar nada.
- **Gate nuevo:** `NucleoUniversalIntegrationTest.todaColumnaObligatoriaEstaMapeada`
  cruza `information_schema.columns` con las fuentes del dominio y falla si
  alguna columna NOT NULL sin DEFAULT no está mapeada. Sigue la herencia de
  `@MappedSuperclass` — sin eso marcaría 48 tablas por `organizacion_id` y solo
  estaría diciendo que no sabe leer una herencia.

> **Es el argumento de por qué el cierre incluye E2E y no solo el reactor.** Los
> 10 tests de integración pasaron con los dos fallos dentro: ninguno crea un
> expediente ni capta desde una prospección. Sólo una escritura real por el
> cable los destapó — y son exactamente los dos caminos por los que pasa el
> negocio todos los días.

---

## Dos cosas que costaron una corrida cada una

### A · Una variable PL/pgSQL que se llamaba como su columna

V55 declaraba `del_sistema bigint` y luego hacía
`SELECT count(*) … WHERE del_sistema`. PostgreSQL aborta con *«column reference
"del_sistema" is ambiguous»* y **tumba la migración entera**. Corregido y anotado
en el propio fichero: las variables de los bloques `DO` llevan nombres que
ninguna columna usa.

> Flyway lo dejó limpio: PostgreSQL tiene DDL transaccional, así que la
> migración fallida se deshizo sin dejar fila en `flyway_schema_history`.

### B · `estado_operativo` de un agente no es `'A'`

El fixture creaba el agente con `estado_operativo = 'A'` y el CHECK admite
`D`/`L`/`N`. Dos columnas de una letra en la misma base con vocabularios
distintos: el `'A'` de `estado` no es el de `estado_operativo`, igual que el
`'A'` de ALMACÉN en `tipo_inmueble` no es el de ALQUILADO en
`disponibilidad_comercial`.

Y una lección de fixture: la búsqueda de «¿ya existe el agente?» iba contra
`persona_rol`, pero cada sentencia de `JdbcTemplate` confirma por su cuenta —una
corrida que creara el rol y fallara al crear el detalle dejaba un rol huérfano
que **todas** las corridas siguientes daban por bueno. Ahora busca contra
`detalle_agente`.

---

## Infraestructura

`docker-compose.e2e.yml` pasa a `postgis/postgis:17-3.5-alpine`. Aquí la
dependencia **no es opcional**: V46 hace `CREATE EXTENSION postgis` sobre una
base vacía, así que sin la extensión Flyway aborta en la primera migración del
modelo universal y la suite entera muere antes de arrancar.

---

## Lo que queda listo para la siguiente tanda

**A · Requerimiento universal.** El espejo de la oferta. `OperacionInmobiliaria`
ya es el vocabulario común: un requerimiento de VENTA y un encargo de VENTA se
cruzan sin traducir nada.

**B · KAIROS funcional.** El adaptador tiene contra qué hablar:
`POST /captura` para comprender y completar, `GET /captura` para retomar,
`POST /captura/{id}/ejecutar` para invocar el mismo caso de uso que la pantalla,
`X-Origen: KAIROS` para la trazabilidad y `Idempotency-Key` para reintentar sin
duplicar. **KAIROS no tendrá lógica inmobiliaria propia.**

## Lo que NO se construyó, a propósito

Matcher v2, negociación E3, compraventa completa, Neo4j, WhatsApp, LLM, voz,
embeddings, memoria vectorial, LangGraph y automatizaciones autónomas. Todo eso
depende de este spine, y ahora existe.
