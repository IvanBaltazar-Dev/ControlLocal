# D-E4-3 — Una sola autoridad por dato (2026-08-18)

**Qué cierra:** los pasos 1 a 10 de `docs/ai/decision-autoridad-de-cada-dato.md`.
Siete conceptos vivían **a la vez** como columna de `propiedad` y como fila de
`atributo_propiedad`, con uno solo sincronizado. Ya no.

**Qué cierra también:** el paso 11. El SPA **no necesitó ningún cambio** — sigue
consumiendo `LocalRequest`/`LocalResponse` con los mismos nombres y los mismos
tipos —, que es justo la señal de que el contrato lógico no se movió.

---

## 1. Qué se ejecutó

```bash
export JAVA_HOME=".../jdk-21.0.11.10-hotspot"
export TEST_DB_URL="jdbc:postgresql://localhost:5433/controllocal_dev"
export TEST_DB_USER=controllocal
export TEST_DB_PASSWORD=controllocal
export CONTROLLOCAL_CIERRE=1
mvn -o -f backend-spring/pom.xml test
```

| Capa | Resultado |
|---|---|
| `controllocal-service` (unitarias) | **631 / 631** |
| `controllocal-web` + arquitectura | **43 / 43** |
| `controllocal-app` (incl. integración y gates) | **161 / 161** |
| **Total** | **835 · 0 fallos · 0 errores · 0 SKIPPED** |

El **0 skipped** es la mitad de la evidencia. Sin `TEST_DB_URL` los 37 tests de
integración se saltan **en silencio** y Maven termina en verde: así fue como
V31/V37/V38 metieron tres columnas `estado` de palabra completa con el build
verde durante un bloque de seguridad entero.

---

## 2. Estado del esquema, consultado al servidor

```
flyway_schema_history      62 | retirar columnas espejo | t

select count(*) from information_schema.columns
 where table_name='propiedad'
   and column_name in ('ambientes','frente','zonificacion','cuota_mantenimiento',
                       'numero_estacionamientos','antiguedad_anios');
--> 0

select clave, valor_minimo from catalogo_atributo where valor_minimo is not null;
--> ambientes            1.0000
    antiguedad_anios     0.0000
    cuota_mantenimiento  0.0000
    estacionamientos     0.0000
    frente               0.0000
```

Y el arranque real: `ControlLocalApplication` levanta con `ddl-auto: validate`
contra ese esquema, que es la prueba de que la entidad y la migración dicen lo
mismo.

---

## 3. La búsqueda del paso 8, que es lo que autorizó el DROP

```
getters/setters de las seis en código de producción ....... 0
las seis en JPQL o SQL nativo ............................. 0
las seis en scripts de verificación u operación ........... 0
referencias restantes ..... nombres de campo de LocalRequest/LocalResponse
                            (contrato lógico: se queda)
```

### Lo que devolvió y no estaba en el plan

| Hallazgo | Por qué importaba |
|---|---|
| `LocalComercialServiceImpl:534-542` seguía **escribiendo** las seis columnas | El paso 4 cortó la fuga sólo en el camino universal. `/locales` escribía columna y leía columna: una isla coherente consigo misma. Migrar sólo su lector habría convertido **cada PUT en pérdida silenciosa**. Lector y escritor entraron en el mismo cambio. |
| `CoincidenciaCartera.evalFrente` leía `propiedad.getFrente()` | Tercer lector, y el más callado: leer una columna vacía no falla, convierte el criterio en «no aplica» y **mueve el puntaje sin avisar**. Tres llamadores, uno dentro de dos bucles anidados. |
| El `DROP COLUMN` se llevaba **cuatro CHECK de rango** de V4 | `atributo_propiedad` valida el **tipo** del valor, no su rango. V62 los muda a `catalogo_atributo.valor_minimo` **antes** del DROP: el invariante no deja de existir en ningún momento. |

---

## 4. El round-trip, que es lo que encontró el fallo

`crear → leer → editar OTRA cosa → releer`. Encontró que al mover `metraje` a su
campo canónico se movió el escritor y no el lector: el dato seguía guardándose y
**dejó de poder leerse por el API**.

| Test | Qué impide |
|---|---|
| `elLectorEnrutaComoElEscritor` | que un cambio de autoridad saque un valor de la respuesta |
| `localesIdaYVuelta` | crear → ficha → listado → **editar sólo el precio** → releer los seis |
| `vaciarUnGobernadoLoRetira` | que un campo vaciado se quede con el valor viejo pegado |
| `elRangoDeV4SobrevivioAlDrop` | que el DROP se lleve un invariante sin reemplazo |
| `laDeudaDeColumnasEspejoNoCrece` | que vuelva una séptima columna espejo |

---

## 5. Coste, medido

| Camino | Antes | Ahora |
|---|---|---|
| `GET /locales` | 3 consultas por página | **4** |
| `GET /locales/{id}` | 3 | **4** |
| `/mis-locales` | 3 por página | **4** |
| matcher cartera→cliente | 1 por candidata | **1 para toda la cartera**, antes del bucle |

Ninguno pasó a N+1. `metraje` se queda en la proyección del listado: es el único
estructural y un listado tiene que poder ordenar por él en SQL, antes del
`LIMIT`.

---

## 6. Un fallo ajeno que estas corridas destaparon

`NucleoUniversalIntegrationTest.elOutboxGuardaYSeConsume` comprobaba «recién
escrito, está pendiente» mirando la ventana de los **50 más antiguos**. Como la
ventana va ordenada por id ascendente —un consumidor drena el outbox en orden—
el evento nuevo es siempre el último de la cola: el test pasaba sólo mientras la
tabla tuviera menos de 50 filas, y al llegar a **52 pendientes** empezó a fallar
por su propio éxito.

Corregido para preguntar por **su** id y **a la tabla**: `marcarProyectados` es
un update masivo de JPQL que va directo a la base y no refresca la entidad ya
cargada, así que `findById` habría devuelto el objeto viejo. La ventana se sigue
comprobando, pero por lo que de verdad garantiza: un tope y un orden.

---

## 7. Paso 11 — Angular

La búsqueda dirigida sobre `frontend-angular/src` (6 categorías: consumidores,
parsing especial, defaults, aplicabilidad por tipo, «vacío = no modificar», y
tests que congelaran la representación) devolvió **cero trabajo**:

```
vocabulario de almacenamiento en el SPA ................... 0
  (atributo_propiedad, catalogo_atributo, campo_estructural,
   valor_numero, valor_texto)
parsing especial o fallback vieja/nueva representación .... 0
validaciones de aplicabilidad por tipo de propiedad ....... 0
formularios que traten vacío como "no modificar" .......... 0
tests que congelen la representación anterior ............. 0
```

Las 8 apariciones de las seis claves son legítimas: el DTO tipado
(`frente: number | null`), las plantillas de presentación y los controles del
formulario. **El SPA nunca supo dónde vivían esos valores, y por eso mover la
autoridad no le llegó.**

### El cable, comprobado contra la API viva

```
GET /locales/1303   (agente vmora, token real)

  "metraje":120.00   "ambientes":4        "antiguedadAnios":12
  "frente":6.5       "zonificacion":"CZ"  "numeroEstacionamientos":2
  "cuotaMantenimiento":350
```

Mismos nombres, mismos tipos. `metraje` conserva su escala porque su autoridad
sigue siendo una columna `NUMERIC(10,2)`; `cuotaMantenimiento` sale `350` y no
`350.00` porque su autoridad es ahora `valor_numero NUMERIC(14,4)`, compartido
por todas las claves — publicar el crudo daría `350.0000`, que es la escala del
**almacenamiento**, justo lo que el cliente no debe ver.

### El gate nuevo

`FronteraDeAutoridadEnElSpaTest`, en `controllocal-app` junto a los otros gates
estructurales:

| Test | Qué impide |
|---|---|
| `elSpaNoSabeDondeSeGuardaNada` | que un nombre de tabla o columna llegue a un `.ts` o `.html` |
| `elContratoLogicoNoSeMovio` | que las seis **desaparezcan** del contrato del SPA (sería el contrato lógico moviéndose) |

Comprobado que muerde: inyectando `atributo_propiedad` en `locales.service.ts`
falla nombrando fichero, línea y motivo.

### Deuda declarada, no cerrada

Cuatro mínimos viven dos veces: en `catalogo_atributo.valor_minimo` (que creó
V62) y en los validadores de `local-form`. No es conocimiento de almacenamiento
—no bloquea el cierre— pero sí es una mini-autoridad paralela. Se cierra cuando
la aplicabilidad, el rango, la obligatoriedad y la unidad viajen desde el
catálogo como contrato: eso es la normalización del alta de propiedades, no esta
tanda.
