# E2.6 — Contraste, pie y metas · cierre de E2

**Fecha:** 2026-08-19
**Alcance:** la última subtanda de E2 y, con ella, el cierre de la etapa.
**Diagnóstico previo:** `docs/ai/diagnostico-e2-6-contraste-medias-y-metas.md`

---

## 1. Lo primero: se midió antes de picar

El bloque entero se midió contra PostgreSQL real y la API viva **antes de
escribir la primera consulta**. La medición no confirmó el plan: lo cambió.

| Lo que se creía | Lo que había |
|---|---|
| El contraste de renta necesita una consulta | **No hay muestra en ninguna celda.** La mejor combinación zona × metraje tenía **4** propiedades; catorce de las diecisiete tenían **una** |
| Las medias propias necesitan afinarse | **No son calculables por falta del hecho**: 0 visitas realizadas, 0 interacciones colgadas de una prospección, y los 4 contratos cierran **un día antes** de su oportunidad |
| Las metas están en alguna parte | **Cero productores** en base, backend, SPA y seed |
| El periodo sirve | Es una **ventana móvil**, y `metaEsperadaAHoy` sobre una ventana móvil es tautológica |
| Los cuatro KPI tienen un nombre | Tenían **dos juegos distintos** en los dos documentos que gobiernan |

Y dos hallazgos que no eran de E2.6 y hubo que cerrar primero (§2 y §3).

---

## 2. Una prueba de integración podía escribir en la base de desarrollo

### Lo que pasó

El 18 y el 19 de agosto la suite corrió con `TEST_DB_URL` apuntando a
`controllocal_dev`. Nada lo impedía: cada prueba leía la variable por su cuenta.

```
162 propiedades · 120 captaciones · 184 hitos de precio
471 atributos   · 120 titularidades ·  42 prospecciones
242 eventos de dominio (el outbox ENTERO era residuo)
```

**El daño era de evidencia.** La cabecera del Inicio decía *«125 cosas necesitan
tu atención»* —120 eran captaciones de prueba— y la única celda con muestra del
contraste pasaba a tener 42 filas a 7000 y 21 a 7500: parece 63 observaciones y
son dos valores repetidos por un caso de prueba.

### La causa, cerrada

`BaseDeDatosDePruebas` valida la url desde `@DynamicPropertySource`, antes de que
Flyway migre y mucho antes de que nada escriba. Deniega por defecto.

```
$ TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_dev \
  mvn -pl controllocal-app test -Dtest=HistoricoPrecioIntegrationTest

java.lang.IllegalStateException: Las pruebas de integracion escriben, borran y
migran: no pueden correr contra 'controllocal_dev'.
  Bases autorizadas: controllocal_repositorios
  Corrige TEST_DB_URL, por ejemplo:
    jdbc:postgresql://localhost:5433/controllocal_repositorios
  (El 2026-08-18 esta guarda no existia y la suite dejo 162 propiedades
  de prueba en controllocal_dev.)

Tests run: 3, Failures: 0, Errors: 3
```

Comprobado que **no escribió nada**: 183 propiedades antes, 183 después.
Y que la base correcta sigue pasando: `Tests run: 3, Failures: 0, Errors: 0`.

`AislamientoDePruebasTest` (10 comprobaciones) rompe el build si una prueba la
rodea leyendo la variable o registrando su propio origen de datos.

### El residuo, retirado por su huella y no por su fecha

Una fecha no prueba el origen. La dirección sí, porque es literal del código de
prueba y **ninguna propiedad legítima la usa**:

| Huella | Filas | Origen |
|---|---|---|
| `Av. Autoridad %` | 118 | `AutoridadDelDatoIntegrationTest:95` |
| `Av. Ida y Vuelta AUT-%` | 42 | `AutoridadDelDatoIntegrationTest:477,606` |
| `Av. Doble Fuente 100` | 1 | ejercicio manual contra la API de dev |
| `Av. Pardo 1234` | 1 | ejercicio manual contra la API de dev |
| | **162** | |

**Comprobación inversa:** cero propiedades anteriores al 18 llevan ninguna de las
cuatro huellas. El criterio selecciona exactamente 162 filas y ni una más.

> **La atribución inicial estaba equivocada** y conviene dejarlo escrito: se
> culpó a `PropiedadUniversalIntegrationTest`, que construye sus propios tenants
> (orgs 38 y 39) y no tocó la organización 1. Quien escribió en la cartera fue
> `AutoridadDelDatoIntegrationTest`, que registra por el caso de uso real y por
> eso recibió códigos `PROP-####` **indistinguibles de los de producción**.

V64 aborta si del residuo cuelga trabajo comercial. No colgaba nada: 0 contratos,
0 oportunidades, 0 interacciones, 0 reasignaciones, 0 reportes.

```
propiedad             183 -> 21     evento_dominio        242 -> 0
captacion             133 -> 13     oportunidad_comercial   8 -> 8
precio_propiedad      206 -> 22     solicitud_alquiler      6 -> 6
atributo_propiedad    541 -> 70     visita                  8 -> 8
titularidad           141 -> 21     contrato_alquiler       4 -> 4
prospeccion            63 -> 21

pendientesDeAtencion  125 -> 5
```

---

## 3. El hito `P` nunca se había ejercitado

Cero filas con **cinco publicaciones vivas**. El productor existe desde E0.2 y
las publicaciones eran anteriores.

**Sin backfill inventado** —nadie puede demostrar qué renta vio el mercado en el
pasado— pero sí verificado hacia adelante, con el caso real:

```
POST /locales/8/publicaciones  {rentaPublicada: 6200, moneda: PEN, estado: P}
  -> 201, y exactamente UN hito P

PUT  .../publicaciones/11      {rentaPublicada: 6200}   (el mismo importe)
  -> 200, y NO duplica

PUT  .../publicaciones/11      {rentaPublicada: 5800}   (cambio real)
  -> 200, y añade el segundo

 id_precio | id_propiedad | hito | monto |   fecha
-----------+--------------+------+-------+------------
       820 |            8 | P    |  6200 | 2026-08-19
       821 |            8 | P    |  5800 | 2026-08-19
```

---

## 4. Las dos decisiones bloqueantes

### 4.1 Los cuatro nombres canónicos

D-E2-2 §1 decía *Prospección efectiva / Captaciones activadas / Solicitudes
generadas*; D-E2-1 §6.2 y la maqueta decían otra cosa, y había una comprobación
que exigía los cuatro «letra por letra». **El gate no se podía escribir: pedía
dos verdades.**

Gana el hecho de negocio, y «Locales» pasa a «Propiedades»:

| Código | Nombre | Evento que cuenta |
|---|---|---|
| `C` | Propietarios contactados | `prospeccion.fecha_contacto` dentro del mes |
| `P` | Propiedades captadas | la **transición a ACTIVA** de `historial_estado` |
| `S` | Solicitudes ingresadas | `solicitud_alquiler.fecha_registro` |
| `F` | Contratos firmados | `contrato_alquiler.fecha_cierre` |

**Dos de los cuatro se habrían medido mal:**

- *Propietarios contactados* por estado perdería los descartados, y los tres
  descartados de la base **sí** habían sido contactados.
- *Propiedades captadas* por estado da **5**; por evento da **9**, porque cuatro
  ya cerraron en contrato.

### 4.2 El periodo pasa a ser un mes de calendario

`metaEsperadaAHoy = meta × transcurridos ÷ días` es tautológica en una ventana
móvil: los transcurridos son siempre los totales, así que lo esperado a hoy sería
siempre la meta entera y el semáforo diría rojo todos los días menos el último.

**Dos semánticas, dos parámetros.** `periodo` sigue siendo la ventana móvil de
las series; `mes` (`AAAA-MM`, por defecto el actual) es el del ritmo. El cable
publica el periodo entero:

```json
"periodo": { "codigo": "2026-08", "desde": "2026-08-01", "hasta": "2026-08-31",
             "diasTranscurridos": 19, "diasTotales": 31, "enCurso": true }
```

---

## 5. Las metas, el ritmo y el cierre determinista

### Metas (V65)

Mensual y por agente. **La del equipo no se guarda: es la suma**, así que no
puede contradecir a sus sumandos. Si falta la de alguno, el ritmo se declara
`COBERTURA_INCOMPLETA` y **no se compara contra una meta parcial**, que daría
siempre una brecha a favor.

La tabla **nace vacía**: los números de la maqueta no son el objetivo de nadie.

### Ritmo

Baja de `docs/ai/prototipos/*.html` —donde estaba duplicado entre sus dos
archivos— a `Ritmo`, y sus cinco umbrales a `PoliticaComercial`.

### «Puede cerrarse este mes»

Determinista: **aprobadas, sin contrato y con la oferta vigente**. Ni oportunidad
ni visita entran. El importe sale de `monto_propuesto` y conserva su moneda.

Con la evidencia de hoy: **cero operaciones**. La única solicitud viva estaba en
revisión y además con la oferta vencida el día 15. La maqueta enseñaba «3
operaciones · US$ 9,300»; eran constantes.

---

## 6. El contraste: se implementa la degradación, no un rango falso

Un rango nace solo con **10 propiedades distintas** en la misma zona, tramo y
moneda, con **una observación válida por propiedad** (el hito `P` más reciente,
para que una republicada no pese cinco veces). Manda `P` y **no hay sustituto**:
`U` es lo que el propietario autoriza pedir, no lo que el mercado ve.

Por debajo: `SIN_REFERENCIA_INTERNA_SUFICIENTE` **conservando la N** —«4
propiedades» informa y «sin datos» no dice si falta poco o todo—. Sin zona o sin
metraje: `SIN_GRUPO_COMPARABLE`, que no es lo mismo.

Verificado en el cable:

```json
"contraste": { "forma": "NINGUNA", "motivo": "SIN_OBSERVACIONES",
               "zona": "Miraflores", "banda": "100 a 200 m2", "observaciones": 0 }
```

**Las tres medias propias degradan por separado.** Propuestas por visita cuenta
visitas **realizadas** (hoy 0). Días hasta contrato **descarta la cronología
imposible** en vez de promediarla, y se mide desde la solicitud (4 casos, menos
que la muestra mínima de 5). El recontacto real descarta los intervalos de cero
días: dos apuntes del mismo día son un apunte doble.

Ninguna cifra sale de fuera de la organización, y ningún texto puede decir
«sector», «mercado» ni «industria».

---

## 7. La maqueta deja de parecer fuente ejecutable

El bloque `POLITICA` de `nucleo-brox.js` se llamaba *«los umbrales, en un solo
sitio (E1)»* y era falso por partida doble: estaba también en los dos HTML y **ya
divergía**.

| Umbral | Maqueta | Backend |
|---|---|---|
| reporte al propietario | **10 días** | **15 días** |
| renta sin ajustar | **60 días** | **45 días** (y estaba suelto en el código) |

Pasa a `POLITICA_ESPEJO`, con la advertencia delante, y **`PoliticaUnicaTest`
compara cada valor con su `Regla`**. Probado que el gate rompe el build al
reponer el 10 histórico:

```
La maqueta y la politica dicen cosas distintas.
  [reporteAlPropietarioDias: la maqueta dice 10 y la politica 15]
```

El 45 que estaba suelto en `InterpreteDeLaBandeja` —con un comentario que lo
llamaba «plazo de recontacto», que son 7— sube a la política. Y los cortes de
banda (50/100/200) también: deciden **contra quién** se compara una renta. El
gate lo descubrió solo, al chocar el 200 con el tope de comisión.

---

## 8. La pantalla

**Pie del Inicio** — los cuatro KPI con su meta y su ritmo, la cifra en juego y
`calculado hace X`. El enlace es la franja entera.

```
AGENTE   Propietarios contactados  19 de 24   A 5 de la meta · hoy deberías ir por 15
BROKER   Propietarios contactados  19 de 66   A 47 de la meta · el equipo va por detrás
         PULSO DEL EQUIPO  1 en ritmo · 0 requieren atención · 3 fuera de ritmo
```

**Indicadores** — los cuatro círculos con arco, marca del ritmo esperado, la
lectura, la variación y **qué hecho cuenta cada uno**.

La redacción vive en `core/rendimiento.ts`, **el mismo módulo para las dos
pantallas**: por construcción no pueden contradecirse.

**Dos fallos encontrados en el navegador, no en la teoría:**

1. El cable **omite los campos nulos**, así que llegaban como `undefined` y el
   broker leía «19 de undefined».
2. Cinco tokens de color que no existen (`--cl-verde`, `--cl-ambar`, `--cl-rojo`,
   `--cl-fondo-2`, `--cl-tinta-3`): las barras salían transparentes.

Y uno más: el `DatePipe` escribía «August 2026» en una pantalla entera en
español, porque el `LOCALE_ID` de la aplicación sigue siendo `en-US`.

---

## 9. Los gates

| Gate | Qué rompe |
|---|---|
| `AislamientoDePruebasTest` | una prueba de integración que pueda escribir en la base de desarrollo |
| `PoliticaUnicaTest` (ampliado) | la maqueta divergiendo de la política, y los cuatro nombres canónicos escritos fuera del dominio |
| `GateDeCierreTest` (ampliado) | una prueba de integración que el script de cierre no compruebe |
| `MatrizOperacionRolTest` | los dos endpoints nuevos sin su fila |

### 9 bis. El barrido encontró un agujero en el propio gate de cierre

Al repasar E2.0–E2.6 salió esto, y merece quedar escrito porque es el mismo
defecto que el gate existe para evitar, un nivel más arriba:

`GateDeCierreTest` inventariaba **catorce** pruebas de integración y
`Verificar-Cierre.ps1` comprobaba que se hubieran ejecutado **trece**. La que
faltaba era `AutoridadDelDatoIntegrationTest` — **precisamente la que el 18 de
agosto escribió 162 propiedades en la base de desarrollo**. La corrida de cierre
nunca probó que se hubiera ejecutado: un verde que no significaba lo que parecía.

No se arregla añadiendo la línea que faltaba, porque dos listas mantenidas a mano
vuelven a divergir. `GateDeCierreTest` compara ahora las dos, y se comprobó que
rompe el build al quitar la línea:

```
Verificar-Cierre.ps1 no comprueba que estas pruebas se hayan ejecutado, asi que
una corrida de cierre podria darlas por buenas sin haberlas corrido.
  expected: <[]> but was: <[AutoridadDelDatoIntegrationTest]>
```

### 9 ter. Y una deuda anotada, no escondida

En el Radar, un asunto de tipo `PROSPECCION` viaja **sin expediente** (0
renglones) mientras que los de `VISITA` traen los cuatro. No es una regresión:
los cuatro renglones se construyen desde el inmueble y una prospección todavía no
tiene uno, así que devolver vacío es más honesto que inventarlos. Pero D-E2-1
§10.3 dice que los cuatro son «los mismos para todo asunto», así que hay una
tensión real entre el diseño y lo que se puede construir. Queda anotada para
decidirla, no resuelta a escondidas.

---

## 10. Cómo reproducir

```bash
# El cierre completo
powershell -File backend-spring/verificacion/Verificar-Cierre.ps1

# La guarda, probada por el lado que importa
TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_dev \
  mvn -pl controllocal-app test -Dtest=HistoricoPrecioIntegrationTest   # debe FALLAR

# El prototipo
node docs/ai/prototipos/construir.mjs && node docs/ai/prototipos/pruebas-nucleo.js
```
