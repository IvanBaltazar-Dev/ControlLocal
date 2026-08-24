> # ⛔ ESTE DOCUMENTO NO GOBIERNA
>
> **Residuo del incidente del 2026-08-24.** CONTROL sobrescribio este fichero
> **sin leer antes su contenido anterior**, que se ha perdido: nunca estuvo en el
> indice de git, asi que no hay objeto que recuperar. **Lo que sigue abajo NO es
> el contenido original**, y no se afirma que lo sea.
>
> **El encargo que gobierna el Corte 3 es
> [`encargo-corte-3-vivienda-reconstruido.md`](encargo-corte-3-vivienda-reconstruido.md)**,
> re-derivado desde cero contra la auditoria, el mapa, los pendientes y la
> medicion viva del 2026-08-24, con la verificacion fila por fila en su §1.
>
> Se conserva sin borrar porque su enmienda posterior al precheck documenta un
> hallazgo real (el censo M2 del gate rojo desde V77), que el encargo reconstruido
> recoge y verifica en su §4.

---

# Encargo — Corte 3 · Vivienda: D y C (`V80`)

**Congelado por CONTROL el 2026-08-24.**
**BASE_SHA:** `099a72332c621b20ad7a96f427f3e4369108877b`
(rama `feat/modelo-universal-y-autoridad-del-dato`, árbol limpio, `origin` en el
mismo commit, migraciones aplicadas hasta `V79`).

Corte anterior: **Corte 2 · Identidad registral (`V79`)**, cerrado el 2026-08-23,
aprobado por auditoría y publicado. No se reabre.

---

## Por qué este corte y no otro

Medido contra `mapa-ejecucion-brox.md`, `auditoria-profundidad-inmobiliaria.md`
§6, `pendientes-brox.md` §2 y las dos bases vivas:

| Candidato | Veredicto |
|---|---|
| **Corte 1 (resto)** · profundidad de las 19 claves | ⛔ Sigue **aplazado**. Pide decidir exigencias y vocabularios sobre evidencia contaminada: sus cifras salen de `controllocal_repositorios`, que **es `TEST_DB_URL`**. El corpus real de `controllocal_dev` son **26 propiedades** — medido hoy: 21 L · 2 O · 1 C · 1 D · 1 T. |
| **Corte 3 · Vivienda (D, C)** | ✅ **ENTRA.** Es el siguiente del plan, está **enteramente especificado** en §3.3, §3.4 y §3.6 de la auditoría —claves, rótulos, tipos, vocabularios y aplicabilidad— y **no depende del corpus**: son claves **nuevas** sobre un mecanismo que desde `V72` sabe declarar vocabulario y desde `V73` sabe de quién es cada dato. |
| Cortes 4–7 | ⬜ Van detrás, por tipo. El 7 además está vetado hasta que el dato exista. |
| Bloque 1b (SPA), producción, multi-tenancy, rotación del JWT | ⬜ Otra naturaleza. El mapa prohíbe expresamente mezclarlo con el modelo inmobiliario. |

Contra el North Star: *«propiedades suficientemente completas»* es literalmente
lo que la primera etapa debe demostrar, y el corte **sólo añade**. No retira
ninguna captura, que es la regla no negociable de su §7.

---

## La exigencia, congelada antes de empezar

**Todas las claves nuevas entran `OPC`. Sin excepción. Ninguna `PUB`, ninguna
`ALT`.**

No es prudencia: es lo que la enmienda de CONTROL al Corte 2 ya resolvió y midió.

- `PUB` **bloquea publicar** con un `throw` → HTTP 400
  (`PublicacionServiceImpl.java:186-214`, alcanzado en `:94` y `:326`;
  `ManejadorErroresApi.java:45`).
- **No existe ninguna superficie del cable que reporte una PUB de la PROPIEDAD.**
  `PropiedadResponse.atributosQueFaltan` lleva sólo ALT. El único consumidor de la
  lista de propiedad es el `throw`.
- Hoy **ninguna** clave del sistema tiene exigencia `PUB` — tampoco las seis de
  `V79`, que entraron `OPC` a propósito.

La columna «nivel» de §3.3, §3.4 y §3.6 de la auditoría es **una propuesta, no un
estado**. La promoción a PUB es un corte propio, con su medición, y **no es
éste**.

Los dos `ALT` nuevos que propone la auditoría (`tipo_acceso` en L,
`condicion_terreno` en T) son de los cortes 4 y 5. **Este corte no cambia la
exigencia de ninguna clave existente y no retira del mercado ninguna
propiedad.**

---

## Qué entra — migración **`V80__la_vivienda_descrita_de_verdad.sql`**, única

**Sujeto `PROPIEDAD`, `destino = 'ATRIBUTO'`, `del_sistema = true`,
`organizacion_id = NULL`, `aplica_todos = false`, `exigencia = 'OPC'`,
`requerido = false`, `familia = NULL`.** Ninguna `ESTRUCTURAL`, ninguna con valor
por defecto.

`orden`: continúa donde paró el catálogo de la PROPIEDAD — el máximo actual
medido es **250** (`cargas_gravamenes`). Empezar en **260** y avanzar de diez en
diez, en el orden de la tabla.

### §3.3 · Estado y condición del activo

| # | clave | rótulo | tipo | unidad | aplica_a | vocabulario |
|---|---|---|---|---|---|---|
| 1 | `estado_conservacion` | Estado de conservación | LISTA | — | A,C,D,L,O | ESTRENO · MUY_BUENO · BUENO · REGULAR · PARA_REMODELAR · PARA_DEMOLER |
| 2 | `etapa_entrega` | Etapa de entrega | LISTA | — | A,D,L,O | EN_PLANOS · EN_CONSTRUCCION · ENTREGA_INMEDIATA |

`estado_conservacion` **no lo sustituye `antiguedad_anios`**: veinte años
remodelado y veinte años sin tocar son hoy la misma fila.
`etapa_entrega` **sin defecto**: no se pone `ENTREGA_INMEDIATA` por ser lo
frecuente.

### §3.4 · Edificio y servicios comunes — la parte que toca vivienda

| # | clave | rótulo | tipo | unidad | aplica_a | vocabulario / rango |
|---|---|---|---|---|---|---|
| 3 | `ascensores` | Ascensores | ENTERO | — | D,L,O | `valor_minimo = 0` |
| 4 | `vigilancia` | Vigilancia y control de acceso | LISTA_MULTIPLE | — | A,C,D,L,O | NO_TIENE · PORTERO_DIURNO · CASETA_24H · CAMARAS_CCTV · CONTROL_DE_ACCESO · CERCO_PERIMETRICO |
| 5 | `areas_comunes` | Áreas comunes | LISTA_MULTIPLE | — | C,D,O | GIMNASIO · PISCINA · SUM · PARRILLAS · COWORKING · SALA_DE_NINOS · AZOTEA · LAVANDERIA_COMUN · JUEGOS_INFANTILES · SALA_DE_CINE |
| 6 | `unidades_por_piso` | Unidades por piso | ENTERO | — | D,O | `valor_minimo = 1` |
| 7 | `en_condominio` | En condominio cerrado | BOOLEANO | — | A,C | — |
| 8 | `restriccion_reglamento_interno` | Restricciones del reglamento interno | TEXTO | — | D,L,O | `longitud_maxima = 500` |
| 9 | `accesibilidad_movilidad_reducida` | Accesible para movilidad reducida | BOOLEANO | — | D,L,O | — |

Se quedan **fuera** de §3.4 y van al Corte 4, por ser exclusivas de O/L/A:
`recepcion_edificio`, `horario_acceso_edificio`, `fibra_optica`,
`certificacion_sostenible`.

### §3.6 · Distribución interior (vivienda)

| # | clave | rótulo | tipo | unidad | aplica_a | vocabulario / rango |
|---|---|---|---|---|---|---|
| 10 | `tipologia` | Tipología | LISTA | — | D | MONOAMBIENTE · FLAT · DUPLEX · TRIPLEX · PENTHOUSE · LOFT |
| 11 | `niveles_internos` | Niveles de la unidad | ENTERO | — | D,L,O | `valor_minimo = 1` |
| 12 | `medios_banos` | Medios baños | ENTERO | — | C,D | `valor_minimo = 0` |
| 13 | `cuarto_servicio` | Cuartos de servicio | ENTERO | — | C,D | `valor_minimo = 0` |
| 14 | `bano_servicio` | Baño de servicio | BOOLEANO | — | C,D | — |
| 15 | `tipo_cocina` | Tipo de cocina | LISTA | — | D | CERRADA · ABIERTA_A_SALA · KITCHENETTE · BARRA |
| 16 | `lavanderia` | Lavandería | LISTA | — | D | INDEPENDIENTE · EN_COCINA · EN_TERRAZA · COMUN_DEL_EDIFICIO · NO_TIENE |
| 17 | `estudio` | Ambiente de estudio | BOOLEANO | — | C,D | — |
| 18 | `vista` | Vista | LISTA | — | D,O | INTERIOR · EXTERIOR_A_CALLE · VISTA_A_PARQUE · VISTA_AL_MAR · VISTA_A_AREAS_COMUNES |
| 19 | `terraza` | Tiene terraza | BOOLEANO | — | C,D | — |
| 20 | `area_terraza` | Área de terraza | DECIMAL | m² | C,D | `valor_minimo = 0` |
| 21 | `balcon` | Tiene balcón | BOOLEANO | — | D | — |
| 22 | `jardin` | Tiene jardín | BOOLEANO | — | C,D | — |
| 23 | `patio` | Tiene patio | BOOLEANO | — | C,D | — |
| 24 | `area_jardin_patio` | Área de uso exclusivo | DECIMAL | m² | C,D | `valor_minimo = 0` |
| 25 | `piscina` | Piscina | BOOLEANO | — | C | — |
| 26 | `depositos` | Depósitos | ENTERO | — | D,O | `valor_minimo = 0` |
| 27 | `deposito_area` | Área de depósito | DECIMAL | m² | D,O | `valor_minimo = 0` |
| 28 | `tipo_estacionamiento` | Tipo de estacionamiento | LISTA | — | C,D,O | SIMPLE · DOBLE_LINEAL · DOBLE_PARALELO · MOTO |
| 29 | `torre_bloque` | Torre o bloque | TEXTO | — | D | `longitud_maxima = 40` |
| 30 | `mascotas_reglamento` | El reglamento permite mascotas | BOOLEANO | — | **C,D** | — · **ver abajo** |

**`terraza` / `area_terraza` y `jardin` / `patio` / `area_jardin_patio` son claves
separadas a propósito**: la presencia se sabe en la visita, el metraje no. Un
`area_terraza` vacía **no** significa que no haya terraza. Jardín y patio no son
lo mismo — quien busca jardín no debe visitar patios.

`estudio` existe porque hoy o se cuenta como dormitorio —falseando un campo `ALT`
y de *matching*— o se pierde.

### La clave 30 es mitad de un par, y su aplicabilidad está medida

`mascotas_reglamento` es el **hecho** cuya **condición** `mascotas_aceptadas` ya
existe desde `V74` (`V74:70`, aplicabilidad en `V74:110-116`). El guard 2.2 de
`V78` exige que el hecho **no llegue menos lejos que su condición**, y hay un
espejo en Java que corre en cada corrida de integración:
`SujetoDelDatoIntegrationTest` (`PARES_DELIBERADOS` en `:108-117`, aserciones en
`:735` y `:1073`) — que además lleva un **noveno par**,
`uso` / `uso_admitido_por_titular`, ausente de las listas SQL. Medido hoy contra
`controllocal_dev`:

```
mascotas_aceptadas   BOOLEANO   sujeto=ENCARGO   catalogo_atributo_operacion: CA:OPC  DA:OPC
```

→ **`mascotas_reglamento` nace con `catalogo_atributo_tipo` en C y D.** La
auditoría §3.6 dice «D»; la medición dice **C y D**, y manda la medición. Si
naciera sólo en D, `V80` **falla en su propia migración**, que es lo correcto.

Los otros tres hechos huérfanos **no entran** (medidos hoy, y ninguno es
vivienda): `se_entrega_implementado` → A,L,O (venta/alquiler) = Corte 4;
`entrega_desocupado` → los siete en VENTA = Corte 5;
`acepta_venta_fraccionada` → T en VENTA = Corte 5.

### Una ayuda que sí se puede corregir

`banos` sigue **DECIMAL** y este corte **no la estrecha**. Lo que sí hace es
**publicar su convención en la `ayuda`**: qué significa `2.5`. Es la precondición
documental del estrechamiento futuro —«hay que escribirla antes de aplicarla, no
después»— y `proteger_catalogo_del_sistema()` **no bloquea `UPDATE` de `ayuda`**
sobre una clave del sistema (sólo `clave`, `tipo_dato`, `del_sistema` y
`organizacion_id`; `V55:113-130`).

La ayuda debe decir el hecho, no la metáfora: un baño completo cuenta 1, un medio
baño (sin ducha) cuenta 0.5, y a partir de este corte el medio baño se registra
además en `medios_banos`.

---

## Cómo se escribe — patrones ya establecidos, no se inventan

Copiar la mecánica de `V79__la_identidad_registral_de_la_propiedad.sql`:

1. `INSERT INTO catalogo_atributo (...)` con las 16 columnas explícitas, **ASCII
   en el INSERT** y un bloque `UPDATE` posterior que repone acentos en `rotulo`,
   `ayuda` y `unidad` (`m2` → `m²`) — `V79:149-168`.
2. `INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
   requerido, exigencia) SELECT ... CROSS JOIN (VALUES ...)`, un bloque por
   conjunto de aplicabilidad. **`requerido` se escribe siempre**, y es espejo
   exacto de `exigencia = 'ALT'` — aquí, `false` en todas. El guard 2.4 de `V78`
   lo comprueba **en todo el catálogo**, no sólo en las filas nuevas.
3. `INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo,
   orden)` con `orden` denso 1..N, códigos `UPPER_SNAKE` ASCII y rótulos legibles;
   acentos por `UPDATE` posterior.
4. **`catalogo_atributo_operacion` no se toca.** Es la tabla del sujeto ENCARGO, y
   escribir en ella desde una clave de PROPIEDAD rompe el guard 2.5 de `V78`.
5. Cerrar con **un `DO $$ ... END $$;` de aserciones** al estilo de `V79:389-544`,
   que compruebe invariantes del estado resultante y no cuentas escritas a mano.
   Como mínimo, sobre el array de claves nuevas:
   - todas presentes y `activo`;
   - ninguna con `aplica_todos = false` y cero filas de aplicabilidad;
   - ninguna con filas en `catalogo_atributo_operacion`;
   - **toda LISTA/LISTA_MULTIPLE nueva con vocabulario** (la guarda global de
     `V77` sólo mira `sujeto='ENCARGO'`, así que aquí va acotada a estas claves);
   - **toda fila nueva `exigencia = 'OPC'`**, y **cero filas `PUB` en todo el
     catálogo del sistema**;
   - `destino = 'ATRIBUTO'` y `campo_estructural IS NULL` en todas;
   - cero valores materializados en `atributo_propiedad` para las claves nuevas;
   - el par `mascotas_reglamento` / `mascotas_aceptadas` cubierto.

---

## Reglas que ya existen y no se rodean

- **Ningún valor por defecto.** La ausencia significa «todavía no se sabe». Ni
  siquiera `vigilancia = NO_TIENE`: «no tiene vigilancia» es una afirmación
  verificada, no el estado inicial.
- **Nada inferido, nada rellenado retroactivamente.** Las 26 propiedades reales se
  quedan con sus atributos FALTANTES. No se deduce `FLAT` de que el tipo sea D.
- **`tg_catalogo_sistema_inmutable` y `tg_catalogo_no_sombrea` no se tocan.** No
  estorban: son claves nuevas con `organizacion_id = NULL`.
- **Los cuatro gates del build**: capas (`app → web → service → persistence →
  domain`), transiciones por `service/soporte/Transiciones`, discriminador de
  tenant, y la matriz operación→rol. Este corte **no añade endpoints**; si
  apareciera uno, lleva su fila en `docs/ai/matriz-operacion-rol.md`.
- **El inventario de las 20 clases de integración está escrito a mano en DOS
  sitios** y `GateDeCierreTest:163` comprueba que coinciden: la lista de
  `arquitectura/GateDeCierreTest.java:76-111` y la de
  `verificacion/Verificar-Cierre.ps1:35-76`. Si el corte añade un fichero de test
  de integración nuevo, va **en los dos** o el gate rompe.
- **Flyway:** `V80` es la siguiente libre. **Jamás se edita una migración
  aplicada.** Tras tocar migraciones: `mvn -pl controllocal-app **clean** install
  -DskipTests` (sin `clean` el jar conserva su tamaño y su código viejo) y
  reiniciar `controllocal-api-v2`.

---

## Qué NO entra, y por qué

- **Ninguna promoción a `PUB` ni a `ALT`**, incluidas las seis de `V79`.
- **El estrechamiento `banos` DECIMAL → ENTERO.**
  `tg_catalogo_sistema_inmutable` lo prohíbe por diseño; exige clave nueva +
  migración de datos + retirada de la vieja, y eso es un corte propio.
  `medios_banos` **sí** nace aquí: es exactamente lo que lo habilita.
- **Las otras tres conversiones de tipo** (`cuota_mantenimiento` → IMPORTE,
  `rubro_permitido` → LISTA_MULTIPLE, `zonificacion` → LISTA): misma invariante, y
  cada una con su propio bloqueo de dato.
- **El resto del Corte 1** — ampliar aplicabilidad de `banos`, `zonificacion`,
  `pisos_edificacion` o `frente`. Sigue aplazado por insuficiencia de corpus.
- **`servicios_disponibles`** y la extensión a PROPIEDAD de la guarda «ninguna
  LISTA sin vocabulario»: sus reemplazos nacen en el **Corte 5** y la guarda va
  con ellos. Retirarla antes deja un agujero de captura — lo que el North Star
  prohíbe.
- **`estacionamiento_independizado`**, que §3.6 marca **provisional** y que §5
  cierra de verdad con `unidad_relacionada` (Corte 6). Sembrar hoy una clave que
  ya sabemos que será sustituida es crear deuda de retirada a cambio de nada.
- **`agua_caliente`** y el resto de §3.5 (instalaciones): son del **Corte 4**.
- **UI nueva.** El alta y el editor derivan del catálogo por `cl-campo-gobernado`.
  Las 30 claves deben aparecer **solas**. Que aparezcan sin tocar Angular es una
  **prueba** del corte, no un supuesto. Y `FronteraDeAutoridadEnElSpaTest` rompe
  el build si el SPA escribe una clave o ramifica por tipo.
- **`familia`.** Las 25 claves de PROPIEDAD la tienen `NULL`; las 30 nuevas nacen
  igual. Agrupar un formulario que pasa de 25 a 55 campos es una decisión de
  presentación y de vocabulario, y va con el corte del SPA. **Queda registrado
  como consecuencia del corte, no como hueco silencioso.**
- Multi-tenancy, RLS, producción, rotación del JWT, E3, KAIROS, tipo `X`, PDF.

---

## Alcance documental autorizado

Sólo estas dos, y nada más:

1. **`mapa-ejecucion-brox.md`** — marcar `Corte 3 · Vivienda (D, C)` como el corte
   en curso / cerrado, con su migración, sin reordenar el resto del roadmap.
2. **`auditoria-profundidad-inmobiliaria.md` §6** — actualizar la tabla de «lo que
   realmente se aplicó» con `V80`, y **corregir la aplicabilidad de
   `mascotas_reglamento` de «D» a «C,D»**, que es un error del plan que la
   medición descubre. Se corrige el documento, nunca el código.

`pendientes-brox.md` §2.4 se anota sólo en la fila de `mascotas_reglamento`.

---

## Cierre — qué se exige para dar el corte por cerrado

- Gate del modelo universal (`node docs/ai/modelo/gate-modelo-universal.js` y el
  `.sql` contra `controllocal_dev`) y los gates de `V77`/`V78`/`V79`, en verde.
- **Una vivienda (D) y una casa (C) se registran y se editan con las claves
  nuevas, ida y vuelta idéntica** — la regla de conservación del 0A: leer → abrir
  el editor → no tocar ese dato → guardar → releer = idéntico.
- **Los vocabularios llegan por las dos puertas** —alta y editor— con las mismas
  opciones. Es D-A-1: el Core publica la definición y los dos consumidores la
  reciben idéntica.
- **Angular no se toca**: las 30 claves se pintan solas.
- **Las 26 propiedades reales siguen siendo publicables.** Ninguna PUB nueva, y
  esto se comprueba, no se supone.
- **Una sola corrida de cierre**: `verificacion/Verificar-Cierre.ps1` con
  `TEST_DB_URL` (que demuestra que las de integración se ejecutaron de verdad),
  **más el build de producción de Angular** —`ng test` no comprueba los
  presupuestos— y **sin nada más compilando en la máquina**.
- Evidencia en `backend-spring/verificacion/evidencia/2026-08-24-corte-3-vivienda.md`.

---

## Lo que este corte deja abierto a propósito

| | |
|---|---|
| La promoción `OPC → PUB`, para las seis de `V79` y las 30 de `V80` | Un corte propio, con su medición sobre corpus real |
| El estrechamiento de `banos` y las otras tres conversiones | `pendientes-brox.md` §2.2 |
| El agrupamiento del formulario (`familia`) con 55 campos | Va con el corte del SPA |
| Los tres hechos huérfanos restantes | Cortes 4 y 5 |
| El tipo `X` (OTRO) sigue quedándose atrás | Ninguna de las 30 lo incluye. Sigue sin auditar |
| Rotar el JWT y decidir qué pasa con `origin/main` | `pendientes-brox.md` §0.2 y §0.3 |
| El ciclo largo de verificación | El gate corre 5 de 23 suites, sin periodicidad acordada |

---

## Protocolo

Si el precheck contradice este encargo, **devolver
`STOP — DECISIÓN REQUERIDA POR CONTROL`** antes de tocar un archivo, con la
medición que lo contradice. Ocurrió en `V79` y tenía razón.

---

# ENMIENDA POSTERIOR AL PRECHECK — CONTROL — 2026-08-24

**Qué es esto.** El CONSTRUCTOR paró antes de escribir un solo archivo y devolvió
`STOP — DECISIÓN REQUERIDA POR CONTROL`. Tenía razón por segunda vez seguida.
Esta enmienda **sustituye el criterio de cierre** del encargo de arriba y **añade
un commit previo**; todo lo demás sigue vigente sin cambios.

## El hallazgo, verificado por CONTROL

El criterio de cierre exigía el gate `.sql` **en verde**, y el gate `.sql` está
**rojo en `BASE_SHA`**, antes de tocar nada:

```
16  M2 el catalogo del sistema tiene 25 atributos     FALLO
```

`backend-spring/verificacion/gate-modelo-universal.sql:200-201`:

```sql
SELECT pg_temp.comprobar('M2 el catalogo del sistema tiene 25 atributos',
    (SELECT count(*) = 25 FROM catalogo_atributo WHERE del_sistema));
```

Medido hoy contra `controllocal_dev`: **51** — 25 de PROPIEDAD y 26 de ENCARGO.
La coincidencia de que PROPIEDAD valga exactamente 25 es lo que hace que el
número siga «pareciendo» correcto al leerlo.

**Desde cuándo:** el censo se actualizó por última vez en `a07a594` (V76), de 19 a
25. `V77` sembró veinte condiciones del ENCARGO y lo dejó rojo; `V79` añadió seis
más. **Sobrevivió a tres cortes cerrados y auditados** porque
`Verificar-Cierre.ps1` **no ejecuta este `.sql`**: sólo corre si alguien se
acuerda de invocarlo a mano.

## Lo que CONTROL resuelve, y por qué

**El censo deja de ser un censo, y no se arregla escribiendo `= 81`.**

La justificación escrita en `gate-modelo-universal.sql:196-199` —*«el catálogo del
sistema es una constante del producto, no cartera que crece con el uso»*— era
cierta cuando el catálogo estaba congelado. **El bloque 3e entero es un programa
cuyo propósito explícito es hacer crecer ese catálogo, corte a corte**
(V74 → V77 → V79 → V80 → cortes 4-7). Con eso, el número mide el avance del
roadmap y no una invariante: se pone rojo **cada vez que el producto avanza según
lo planeado**, que es exactamente el modo de fallo que el propio fichero ya
diagnosticó dos secciones más abajo, al convertir dos cifras hermanas en suelos
(`:516-520`, corregido en V76): *«Un gate que se rompe al usar el producto deja de
leerse»*. Aquí se rompió al **construir** el producto, y en efecto dejó de
leerse durante tres cortes.

Escribir `= 81` dentro de `V80` está **prohibido y además sería peor**: mete la
deuda de `V77` y `V79` dentro de una migración de siembra, la vuelve inatribuible,
y repite la trampa en los cortes 4 a 7.

## Qué se hace — el corte pasa a tener DOS commits

### `3.a` · «el censo que se rompía al avanzar» — va **primero**, y **no lleva migración**

Autorizado y acotado a esto:

1. **`backend-spring/verificacion/gate-modelo-universal.sql`** — la comprobación
   M2 del censo se sustituye por dos, y el conjunto queda **más fuerte, no más
   laxo**:
   - un **suelo** (`count(*) >= 51`), que caza lo único que un número puede cazar:
     que alguien **retire** una clave del sistema;
   - la invariante que sí importa y **hoy no existe**: **ninguna clave del sistema
     activa sin aplicabilidad** — `aplica_todos = false` con cero filas en
     `catalogo_atributo_tipo` si su sujeto es PROPIEDAD, o cero en
     `catalogo_atributo_operacion` si es ENCARGO. Ésa se rompe cuando alguien
     siembra mal, y **no** se rompe cuando el producto avanza.
   - El comentario `:196-199` se reescribe: desde cuándo estuvo rojo, por qué el
     argumento original caducó, y por qué esto no es relajar el gate.
2. **`backend-spring/verificacion/Verificar-Cierre.ps1`** — **el gate `.sql` entra
   en la corrida de cierre**, como paso propio antes del reactor, abortando si
   sale rojo. Un gate que sólo corre si alguien se acuerda no es un gate; es la
   misma lección por la que este script existe (los 37 tests que JUnit saltaba en
   silencio).
3. Commit propio, `fix:`, con evidencia en
   `backend-spring/verificacion/evidencia/2026-08-24-el-censo-que-se-rompia-al-avanzar.md`
   que deje escrito el desfase por migración (V77 +20, V79 +6, V80 +30).

**No entra nada más en `3.a`.** No se toca ninguna otra comprobación del `.sql`,
ni ninguna suite, ni ningún test.

### `3.b` · `V80`, exactamente como está congelado arriba

Sin cambios. Después de `3.a`, y con el gate ya ejecutándose de verdad.

## El criterio de cierre, corregido

Donde el encargo decía «gate del modelo universal en verde», ahora dice:

- **el `.sql` en verde de verdad, con las 30 claves de `V80` ya sembradas** — no
  «sin regresiones respecto a `BASE_SHA`». Con `3.a` hecho, el verde es
  alcanzable y significa algo.
- y `Verificar-Cierre.ps1` **ejecuta ese gate** como parte de la corrida.

El resto del cierre (las 20 de integración comprobadas como ejecutadas, las 5
suites E2E, el build de producción de Angular, la evidencia) no cambia.

## Lo que esta enmienda deja registrado, sin resolver

- **Los cortes 4 a 7 volverán a mover el censo.** Con el suelo y la invariante de
  aplicabilidad, no vuelve a hacer falta tocarlo: es lo que se compra aquí.
- **El gate `.sql` corre contra `controllocal_dev`**, no contra `TEST_DB_URL`.
  Sigue siendo así, y sigue significando que mide el corpus real.
- **Cuántas comprobaciones del `.sql` estaban rojas y nadie miraba** además de
  ésta: el CONSTRUCTOR midió una sola (`16`). Queda dicho que la corrida completa
  de `3.a` es la primera que lo verifica de verdad.

Al terminar, devolver:

```
LISTO PARA AUDITORÍA
BASE_SHA=099a72332c621b20ad7a96f427f3e4369108877b
CANDIDATE_SHA=<sha>
```
