# Auditoría de profundidad inmobiliaria — 2026-08-20

> **Estado documental 2026-08-25:** este archivo conserva la medición original
> y las propuestas de cada corte. No sustituye al encargo vigente. Corte 4 está
> cerrado definitivamente en `795ffbf`; para Corte 5 la decisión que **gobierna**
> es `decision-estado-ocupacion-en-los-siete.md`, y `encargo-corte-5-terreno.md`
> es el encargo que la **ejecuta** —un `encargo-*` no gobierna—. La redacción
> anterior los presentaba a los dos como gobernantes; lo midió la tercera
> auditoría del 2026-08-25.

**Qué es:** la medición que pidió el corte adicional, hecha ANTES de tocar el
catálogo. Diez auditorías en paralelo: una por tipo de propiedad —leyendo
`catalogo_atributo` en la base viva y el motor de captura— y tres transversales
(coherencia de claves entre consumidores, frontera de autoridad del SPA, y el
modelo actual para unidades relacionadas).

**De sólo lectura.** No se modificó ningún fichero para producirla.

**Evidencia cruda:** las diez respuestas completas quedan en el journal del
workflow `wf_6c24292a-f7c`.

> **Qué de esto está decidido y qué no.** El ORDEN de los cortes sí: es la
> secuencia acordada el 2026-08-20 y §6 la fija. El CONTENIDO de cada corte —las
> claves concretas, sus rótulos, sus exigencias— sigue siendo una propuesta
> medida, no una decisión: se confirma corte a corte, al ejecutarlo.

> **El orden de los cortes se corrigió el 2026-08-20, y con él la numeración.**
> El plan se redactó poniendo el catálogo delante de la edición; es al revés,
> porque la corrupción del editor es daño activo y el vocabulario es expansión.
> La secuencia vigente es **0A contención → 0B catálogo → 0C sujeto → resto**, y
> la cadena de migraciones arranca en **V71** (V70 ya está ocupada por la
> publicación por encargo). Todo eso está en §6, que es la que manda; el resto
> del documento se dejó como se midió.

---
# PLAN DE CATÁLOGO — BROX / profundidad inmobiliaria

Verificado contra la base viva (`catalogo_atributo` = 19 filas, `catalogo_atributo_tipo`, `condicion_economica_captacion`, `captacion`) y contra el estado de migraciones (última aplicada entonces **V69**; hoy **V70**, y la siguiente libre es **V71**). Auditoría de sólo lectura: no se modificó ningún fichero.

---

## 1. ¿Hoy BROX describe bien cada tipo de activo?

**No. Ninguno de los siete.** El catálogo entero son **19 claves** para siete tipos de propiedad, y sólo **cuatro filas** están marcadas requeridas en toda la base: `metraje_total` (los 7 tipos), `dormitorios` (C y D) y `zonificacion` (T).

| Tipo | Claves publicadas | Obligatorias | Captar | Comparar | Publicar | Encontrar |
|---|---|---|---|---|---|---|
| **D** departamento | 10 | 2 | parcial | **no** | **no** | **no** |
| **C** casa | 11 | 2 | parcial | **no** | **no** | **no** |
| **T** terreno | 7 (2 sin sentido) | 2 | parcial | **no** | **no** | **no** |
| **L** local | 12 | 1 | parcial | **no** | **no** | parcial y sesgado |
| **O** oficina | 10 | 1 | parcial | **no** | **no** | **no** |
| **A** almacén | 12 | 1 | parcial | **no** | **no** | **no** |
| **X** otro | — | 1 | **sin auditar** | — | — | — |

La ficha mínima legítima de un departamento en Lima es hoy *"90 m², 3 dormitorios"*; la de un almacén, *"2 000 m² y un precio"* sin decir si son techados; la de un local, *"40 m² y S/ 3 000"* sin decir si está a pie de calle o dentro de una galería.

**Cuatro conclusiones transversales, y ninguna es de opinión:**

1. **El catálogo no sabe hablar.** `tipo_dato='LISTA'` existe pero **no hay dónde guardar sus opciones** — ni columna ni tabla — y `MotorDeCapturaImpl` pasa `opciones=null` para todo atributo de catálogo, con lo que `Pregunta.controlDe` cae en TEXTO. Se comprueba con `servicios_disponibles`, la única LISTA sembrada, que viaja hoy como texto libre. Faltan además `LISTA_MULTIPLE`, `FECHA`, importe con moneda, y `valor_maximo` (sólo hay `valor_minimo`: hoy entran 40 dormitorios y el piso 400).
2. **El ENCARGO no es sujeto de nada.** `catalogo_atributo_tipo` sólo se mapea contra `tipo_propiedad` y `atributo_propiedad` cuelga de `id_propiedad`: por construcción **todo atributo gobernado es un hecho de la propiedad**. La condición comercial son columnas fijas de `condicion_economica_captacion` (importe, moneda, comisión, IGV *de la comisión*). Por eso ~30 conceptos reales —garantía, plazo mínimo, IGV de la renta, mascotas aceptadas, se ofrece amoblado, disponible desde— **no tienen dónde escribirse**, y por eso `amoblado` guarda como hecho físico permanente algo que se negocia en cada alquiler.
3. **La identidad registral está en el sitio equivocado.** `partida_registral` existe una sola vez en la base: `condicion_compraventa.partida_registral`, colgada de **una solicitud de venta**. Se pide al final del embudo, sólo en venta, y se retipea cada vez. Un inmueble que nunca se puso en venta no tiene partida en ningún sitio.
4. **Lo que se captura no se puede editar, y editarlo corrompe.** El único editor del SPA es `local-form` (montado en `propiedades/:id/editar` para los siete tipos): no pregunta `dormitorios`, `banos`, `amoblado`, `area_terreno`, `pisos_edificacion` ni `servicios_disponibles`; `PUT /locales` rechaza cualquier tipo que no sea L u O; y al guardar **inventa `rubro_permitido`** (cae al primer valor de la lista) y **aplasta `uso` a `'C'`**. `PUT /propiedades/{id}` existe y está en la matriz, pero `PropiedadesService` no tiene ningún método que lo llame.
   > **Cerrado el 2026-08-22.** `local-form` se borró en el bloque 3d y el hueco quedó sin editor hasta el bloque **3f**: `/propiedades/:id/editar` llama a `PUT /propiedades/{id}` con **sólo lo tocado**, pinta las características desde `GET /captura/definicion` y no conoce ninguna clave. Desde aquí sí tiene sentido ampliar el catálogo: lo que se siembre se podrá corregir.

**Regla que atraviesa todo el plan:** un dato que no se sabe se declara FALTANTE. No se pone `ENTREGA_INMEDIATA` por defecto porque sea lo frecuente, ni `VIVIENDA` porque el tipo sea D, ni se deduce el acceso de que `nombreEdificioGaleria` venga lleno.

---

## 2. Capacidades que faltan en el catálogo (van ANTES de cualquier fila)

Sin esto, la mitad de la sección 3 no se puede representar y la otra mitad acabaría escrita en Angular, que es lo que el gate de D-A-1 rompe.

| # | Falta | Forma propuesta |
|---|---|---|
| C-1 | Vocabulario de una LISTA | Tabla `catalogo_atributo_opcion(id_catalogo_atributo, valor VARCHAR(40), rotulo VARCHAR(120), orden, activo)`, PK (id, valor). El trigger `exigir_atributo_gobernado` valida pertenencia. |
| C-2 | Multivalor | `tipo_dato='LISTA_MULTIPLE'` + tabla `atributo_propiedad_opcion(organizacion_id, id_propiedad, clave, valor)`. |
| C-3 | Fechas | `tipo_dato='FECHA'` + `atributo_propiedad.valor_fecha DATE`. |
| C-4 | Importes con moneda | `tipo_dato='IMPORTE'` + `atributo_propiedad.valor_moneda VARCHAR(3)` CHECK (PEN,USD), obligatoria cuando el tipo es IMPORTE. Cierra `cuota_mantenimiento`, hoy DECIMAL sin unidad ni moneda: un `350` guardado no se sabe si es soles o dólares. |
| C-5 | Techo numérico | `catalogo_atributo.valor_maximo NUMERIC(14,4)` + CHECK `valor_minimo <= valor_maximo`. |
| C-6 | **Tres niveles de exigencia, no dos** | `catalogo_atributo_tipo.requerido` (booleano) sólo sabe "bloquea el alta". Sustituir por `exigencia VARCHAR(3)` CHECK `('ALT','PUB','OPC')`: **ALT** bloquea el alta, **PUB** bloquea publicar, **OPC** no bloquea. *(Se implementó así en V72, con una precisión: `PUB` bloquea el caso de uso de publicación —crear el anuncio y pasarlo a `PUBLICADO`— y **no** toca `disponibilidad_comercial`. Ver §6 bis.)* Sin este campo, toda la columna «nivel» de este plan no tiene dónde vivir. |
| C-7 | Sujeto del atributo | `catalogo_atributo.sujeto VARCHAR(10)` CHECK `('PROPIEDAD','ENCARGO')`, default PROPIEDAD. Reutiliza opciones, rangos, DTO y motor de captura para el encargo (ver §3). |
| C-8 | Campos estructurales nuevos | Ensanchar `ck_catalogo_campo_estructural` de `(METRAJE, PISO)` a `(METRAJE, PISO, PARTIDA, OFICINA_REGISTRAL, INTERIOR, EDIFICIO)`. La partida **no** puede ser un ATRIBUTO: meter la identidad registral en `atributo_propiedad.valor_texto` es exactamente la degradación que la invariante prohíbe. |
| C-9 | El contrato tiene que publicarlo | `PreguntaCatalogoResponse` (`PropiedadesUniversalesController:164`) devuelve seis campos y ninguno es `opciones`. Añadir `opciones[{valor,rotulo}]`, `valorMinimo`, `valorMaximo`, `exigencia`, `ayuda`. Y `/captura/apertura` debe publicar las opciones **con rótulo**, no como lista de cadenas: hoy el SPA se inventa el texto en tres sitios distintos (`Local` / `Local comercial` / `LOCAL`). |

---

## 3. Lista consolidada de atributos — una clave, muchos tipos

Notación: **aplica_a** y **requerido_para** con los códigos de una letra. Nivel: **ALT** = bloquea el alta · **PUB** = bloquea publicar · **OPC** = recomendado. Autoridad **P** = propiedad, **E** = encargo (§4).

> **Antes de subir cualquier fila a `PUB`, leer §6 bis.** Bloquea de verdad: un
> 400 en el momento de anunciar, sobre toda propiedad que no tenga el dato. Las
> columnas «nivel» de aquí abajo son **propuestas**, no estado.

### 3.1 Correcciones sobre las 19 claves que YA existen (cero claves nuevas)

Esto es lo más barato del plan: filas en `catalogo_atributo_tipo`, cambios de `tipo_dato` y de rótulo.

| clave | qué cambia | aplica_a resultante | exigencia |
|---|---|---|---|
| `metraje_total` | Rótulo → **«Área techada»** + ayuda que fija la convención (área techada, la de la partida; la terraza NO se suma). Es la peor grieta de comparabilidad: un flat de 90 m² techados con 30 de terraza lo carga un agente como 120 y otro como 90, y el precio por m² deja de ser comparable entre dos fichas del propio BROX. | L,O,D,C,T,A,X | ALT (todos, ya) |
| `metraje_construido` | **Retirar de D** (para un departamento nombra el mismo hecho que `metraje_total`: dos claves, una verdad). Subir en A y C. | A,C,L,O | **PUB** en A, C |
| `area_terreno` | Subir en C y T. Una casa se tasa por el PAR (terreno, construida). ⚠️ **La mitad de `T` está DEROGADA por D-7 del titular (2026-08-25)**: en `T` no se sube a `PUB` — **se retira su aplicabilidad**, porque `metraje_total` es la superficie canónica de un terreno y dos claves para la misma verdad no comparan nada. La retirada va en la **subtanda 5B**, con migración de datos, no en 5A. Lo de `C` sigue en pie. | A,C~~,T~~ | **ALT** en C · ~~**PUB** en T~~ |
| `antiguedad_anios` | ~~**Quitar `aplica_todos`**. Un terreno eriazo no tiene antigüedad; preguntarlo hace dudar de si el sistema entiende lo que se registra.~~ ⚠️ **APLAZADO por D-5 del titular (2026-08-25)**: el Corte 5 **no toca `aplica_todos` de ninguna clave**. No es que la observación sea falsa —un terreno eriazo sigue sin antigüedad—, es que quitar el booleano a una clave que además tiene filas por tipo entra en la **doble autoridad de aplicabilidad**, deuda estructural anotada en §2.3 bis de `pendientes-brox.md` y que se resuelve antes de declarar el Core estable. Queda como **deuda registrada, no como plan vigente**. | L,O,D,C,A | PUB en C, L, O |
| `estacionamientos` | ~~**Quitar `aplica_todos`** (fuera de T).~~ ⚠️ **APLAZADO por D-5, igual que la fila de arriba** — misma razón y misma deuda (§2.3 bis de `pendientes-brox.md`); y aquí pesa además que `estacionamientos` es lado-hecho de un par deliberado y el guard de pares de `V78` **exime por predicado** a toda clave con `aplica_todos = true`. Valor mínimo 0 ya está bien: 0 = no tiene, nulo = no se sabe. | L,O,D,C,A | PUB en C, L, O, D |
| `banos` | **Añadir L, O, A** (hoy sólo C,D: un local se registra sin decir si tiene SS.HH., y sin ellos no hay ITSE ni licencia). ⚠️ **El estrechamiento DECIMAL → ENTERO + `medios_banos` NO va en el Corte 1**: `medios_banos` es una clave **nueva** y este corte declara «cero claves nuevas». Entra en el **Corte 3** con el bloque de baños/servicio, que es donde ya estaba previsto. Hasta entonces `banos` sigue DECIMAL y la ambigüedad del `2.5` se documenta en la ayuda del campo, no se resuelve a medias. | L,O,D,C,A | **PUB** en L,O,D,C,A |
| `cuota_mantenimiento` | **Añadir C y A** (condominios y parques logísticos). Cambiar a **IMPORTE** (con moneda). | D,L,O,C,A | PUB |
| `zonificacion` | **Añadir O** (una oficina en casa de zona residencial no obtiene licencia; es la omisión más cara del tipo). **TEXTO → LISTA** con el vocabulario de los planos de Lima. Hoy `C-2`, `c2` y `Comercio Zonal` son tres valores distintos y no agrupan nada. | A,C,L,T,O | ALT en T (ya) · **PUB** en L,O,A |
| `rubro_permitido` | **TEXTO → LISTA_MULTIPLE** cerrada. Y **una sola autoridad**: el matcher lo lee hoy de `detalle_local_comercial`, tabla que el alta universal no escribe nunca (§5, corte 1). | A,L,O | PUB |
| `pisos_edificacion` | **Añadir D y O.** «Piso 12 de 20» y «piso 12 de 12» son productos distintos. | C,D,O | PUB en D,O |
| `frente` | **Añadir C.** Una casa que se vende por su terreno se cotiza por frente. | A,L,T,C | PUB en T · OPC en C |
| `altura_libre` | Subir en A: dos naves de 1 000 m², una de 4 m y otra de 11 m, no son el mismo activo. | A,L | **PUB** en A |
| `piso` | Subir en D y O: sin piso, «Av. Larco 1234» y «Javier Prado 476» nombran un edificio, no una unidad. Rótulo → **«Piso de ingreso»** (en un dúplex nadie sabe hoy si escribir 8 o «8-9»). | D,L,O | **ALT** en D,O |
| `interiorUnidad` *(estructural)* | **Habilitar para A** (el stock logístico moderno se identifica por módulo). Exigir en D. | D,L,O,A | **ALT** en D · PUB en A |
| `nombreEdificioGaleria` *(estructural)* | **Habilitar para A** (condominio o parque logístico: es como se busca el activo y hoy acaba dentro de `direccion`). | D,L,O,A | PUB en A |
| `servicios_disponibles` | **Se retira en el Corte 5, no antes.** Para un terreno el estado no es sí/no: lo sustituyen `agua_desague` y `energia_electrica`, y debe resolverse en el encargo cómo se relaciona esa sustitución con `gas`, que ya existe desde `V81`. Cada estado que sobreviva debe distinguir «con factibilidad aprobada». Retirarla antes deja un agujero de captura. Se retira **en la misma tanda que deja operativos sus reemplazos**, migrando lo recuperable y declarando FALTANTE lo que no. **Resuelto por D-1 y D-2 del titular el 2026-08-25**: se ejecuta en la **subtanda 5A** (`V84`) y `gas` conserva su concepto ganando una opción. | ~~A,C,L,O,T,D,X~~ **sólo `T`** — medido el 2026-08-25 contra `controllocal_dev`: `catalogo_atributo_tipo` tiene **una** fila para la clave `19`, `T/OPC`. La enumeración de siete tipos de esta celda **nunca fue cierta**. | OPC |
| `apto_licencia_funcionamiento` | Se conserva como declaración, pero deja de estar solo: se acompaña de `certificado_itse`. | A,L,O | OPC |
| `dormitorios` | Sin cambios (ALT en C,D, correcto). | C,D | ALT |
| `amoblado` | Sin cambios como **hecho físico**. Su mitad comercial sale a `se_ofrece_amoblado` (§4). | C,D | OPC |
| `ambientes` | Sin cambios. Para oficina no describe nada (cuenta divisiones sin decir para qué sirven): se complementa con `salas_reunion`. | A,C,D,L,O | OPC |

### 3.2 Identidad y situación registral (autoridad PROPIEDAD)

**Sembradas el 2026-08-23 por `V79`.** Sólo las dos primeras son ESTRUCTURAL
—son identidad, no dependen del tipo—; las otras cuatro describen **situación**
y su aplicabilidad sí depende del tipo, así que son atributos gobernados.

> **La columna «nivel» de esta tabla es lo que se PROPUSO, no lo que se aplicó.**
> Las seis entraron **`OPC`**, sin excepción. `PUB` bloquea publicar (§6 bis), y
> estrenarlo con claves que acaban de nacer deja sin poder anunciarse a toda la
> cartera. La promoción está pendiente y es de otro corte; se conserva la
> propuesta porque es el destino previsto, no un error.

| clave | rótulo | tipo | opciones | aplica_a | destino | en V79 | nivel propuesto |
|---|---|---|---|---|---|---|---|
| `partida_registral` | Partida registral | TEXTO | — | L,O,D,C,T,A | ESTRUCTURAL | **OPC** | PUB en todos |
| `oficina_registral` | Oficina registral | LISTA | LIMA, CALLAO, HUAURA, CANETE, HUARAL, BARRANCA | L,O,D,C,T,A | ESTRUCTURAL | **OPC** | PUB (el número de partida se repite entre oficinas) |
| `area_segun_partida` | Área según partida | DECIMAL m² | — | C,T,A | ATRIBUTO | **OPC** | OPC (permite avisar de la discrepancia antes de pactar precio) |
| `independizado` | Unidad independizada | BOOLEANO | — | D,O,L,A | ATRIBUTO | **OPC** | PUB (sin independizar no hay crédito hipotecario ni contrato inscribible) |
| `cargas_gravamenes` | Cargas y gravámenes | LISTA_MULTIPLE | NINGUNA, HIPOTECA, EMBARGO, SERVIDUMBRE, COPROPIEDAD_SIN_DIVIDIR, SUCESION_PENDIENTE, LITIGIO | L,O,D,C,T,A | ATRIBUTO | **OPC** | PUB |
| `declaratoria_fabrica` | Fábrica declarada e inscrita | BOOLEANO | — | C,D | ATRIBUTO | **OPC** | PUB en C · OPC en D (el tercer piso sin declarar es el problema nº 1 de la casa limeña: el banco no financia) |

> **Y una pregunta que la promoción tendrá que contestar y hoy no se puede:**
> una partida bloquea publicar una **venta** y es mucho menos relevante en un
> **alquiler**. La aplicabilidad de una clave de la PROPIEDAD se declara por
> tipo (`catalogo_atributo_tipo`) y **no por operación** — expresar esa
> diferencia exige algo que el sujeto PROPIEDAD no tiene. Registrado, sin
> resolver.

### 3.3 Estado y condición del activo

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `estado_conservacion` | Estado de conservación | LISTA | ESTRENO, MUY_BUENO, BUENO, REGULAR, PARA_REMODELAR, PARA_DEMOLER | L,O,D,C,A | **PUB** en todos. `antiguedad_anios` no lo sustituye: 20 años remodelado y 20 años sin tocar son la misma fila hoy |
| `nivel_implementacion` | Nivel de implementación | LISTA | CASCO_OBRA_GRIS, PLANTA_LIBRE, IMPLEMENTADO_PARCIAL, IMPLEMENTADO_COMPLETO | L,O,A | **PUB**. Va aparte de la conservación: son dos hechos distintos y mueve la renta pedida entre 20 y 40 % |
| `etapa_entrega` | Etapa de entrega | LISTA | EN_PLANOS, EN_CONSTRUCCION, ENTREGA_INMEDIATA | D,O,L,A | **PUB** en D. Sin defecto: no se pone ENTREGA_INMEDIATA por ser lo frecuente |

### 3.4 Edificio y servicios comunes

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `ascensores` | Ascensores | ENTERO (min 0) | — | D,O,L | **PUB**. El vacío más caro: hoy un 5.º sin ascensor y uno con dos ascensores tienen ficha idéntica |
| `vigilancia` | Vigilancia y control de acceso | LISTA_MULTIPLE | NO_TIENE, PORTERO_DIURNO, CASETA_24H, CAMARAS_CCTV, CONTROL_DE_ACCESO, CERCO_PERIMETRICO | D,O,L,C,A | **PUB** |
| `areas_comunes` | Áreas comunes | LISTA_MULTIPLE | GIMNASIO, PISCINA, SUM, PARRILLAS, COWORKING, SALA_DE_NINOS, AZOTEA, LAVANDERIA_COMUN, JUEGOS_INFANTILES, SALA_DE_CINE | D,O,C | **PUB** en D (justifica la cuota) |
| `unidades_por_piso` | Unidades por piso | ENTERO (min 1) | — | D,O | OPC |
| `recepcion_edificio` | Recepción atendida | BOOLEANO | — | O | PUB |
| `horario_acceso_edificio` | Horario de acceso | LISTA | 24_7, LUN_VIE_OFICINA, LUN_SAB_OFICINA, OTRO | O,L,A | OPC (regla del edificio, descalifica call centers y cierres contables) |
| `fibra_optica` | Fibra óptica en el edificio | BOOLEANO | — | O,L | OPC |
| `en_condominio` | En condominio cerrado | BOOLEANO | — | C,A | OPC (sin él, la cuota de mantenimiento de una casa no tiene a qué referirse) |
| `restriccion_reglamento_interno` | Restricciones del reglamento interno | TEXTO | — | D,O,L | OPC (el reglamento puede prohibir lo que la zonificación permite) |
| `accesibilidad_movilidad_reducida` | Accesible para movilidad reducida | BOOLEANO | — | D,O,L | OPC |
| `certificacion_sostenible` | Certificación sostenible | LISTA | NINGUNA, LEED_CERTIFIED, LEED_SILVER, LEED_GOLD, LEED_PLATINUM, OTRA | O | OPC |

### 3.5 Instalaciones (el mismo concepto, un solo vocabulario para todos los tipos)

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `gas` | Suministro de gas | LISTA | SIN_RED_CERCANA, RED_EN_LA_VIA, INSTALADO, GLP_TANQUE_EXTERNO, GLP_BALONES | D,C,L,O,A,T | **PUB** en D,L · OPC resto. Calidda crece manzana a manzana: se pregunta, no se supone por distrito |
| `agua_desague` | Agua y desagüe | LISTA | CONECTADO, CON_FACTIBILIDAD_APROBADA, SIN_SERVICIO | T,A | **PUB** en T |
| `energia_electrica` | Energía eléctrica | LISTA | CONECTADO, CON_FACTIBILIDAD_APROBADA, SIN_SERVICIO | T | **PUB** (en la periferia se tiene luz y no desagüe, o al revés: un solo campo agregado esconde justo la combinación que importa) |
| `agua_caliente` | Agua caliente | LISTA | NO_TIENE, TERMA_ELECTRICA, TERMA_A_GAS, CENTRALIZADA, SOLAR | D,C | OPC |
| `suministro_electrico` | Tipo de suministro | LISTA | MONOFASICO_220, TRIFASICO_380, TRIFASICO_440, SUBESTACION_PROPIA | L,O,A | **PUB** en A. `carga_electrica_kw` dice cuánta potencia hay, no de qué clase; sin trifásica no hay cámara fría ni cargador de montacargas |
| `respaldo_electrico` | Respaldo eléctrico | LISTA | NO_TIENE, GRUPO_ELECTROGENO_AREAS_COMUNES, GRUPO_ELECTROGENO_TOTAL | D,O,L,A | OPC (en un edificio alto, un corte deja el piso 12 sin agua y sin ascensor) |
| `aire_acondicionado` | Aire acondicionado | LISTA | NINGUNO, SPLIT_EN_UNIDAD, CENTRAL_DEL_EDIFICIO, VRV_INDEPENDIENTE | O,L | **PUB** en O (en torre de fachada sellada no es confort, es habitabilidad; y el central viene con horario fijo y recargo) |
| `medidor_servicios` | Medidor de servicios | LISTA | INDEPENDIENTE, COMPARTIDO_PRORRATEO, SIN_MEDIDOR | L,O,A | OPC |
| `sistema_contra_incendios` | Sistema contra incendios | LISTA | NINGUNO, EXTINTORES, GABINETES, ROCIADORES, ROCIADORES_ESFR | A,L,O | OPC |
| `extraccion_humos` | Extracción de humos | LISTA | SIN_DUCTO, DUCTO_PROYECTADO, DUCTO_A_AZOTEA, CAMPANA_INSTALADA | L | OPC (habilita o descarta de golpe al segmento gastronómico) |

### 3.6 Distribución interior (vivienda)

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `tipologia` | Tipología | LISTA | MONOAMBIENTE, FLAT, DUPLEX, TRIPLEX, PENTHOUSE, LOFT | D | **PUB**. El concepto que más falta después del área: hoy un dúplex de 90 m² y un flat de 90 m² son la misma fila |
| `niveles_internos` | Niveles de la unidad | ENTERO (min 1) | — | D,L,O | OPC |
| `medios_banos` | Medios baños | ENTERO (min 0) | — | D,C | OPC — necesario para estrechar `banos` a ENTERO |
| `cuarto_servicio` | Cuartos de servicio | ENTERO (min 0) | — | D,C | OPC |
| `bano_servicio` | Baño de servicio | BOOLEANO | — | D,C | OPC (hoy suma a `banos` e infla la comparación) |
| `tipo_cocina` | Tipo de cocina | LISTA | CERRADA, ABIERTA_A_SALA, KITCHENETTE, BARRA | D | OPC |
| `lavanderia` | Lavandería | LISTA | INDEPENDIENTE, EN_COCINA, EN_TERRAZA, COMUN_DEL_EDIFICIO, NO_TIENE | D | OPC |
| `estudio` | Ambiente de estudio | BOOLEANO | — | D,C | OPC (hoy o se cuenta como dormitorio —falseando un campo obligatorio y de matching— o se pierde) |
| `vista` | Vista | LISTA | INTERIOR, EXTERIOR_A_CALLE, VISTA_A_PARQUE, VISTA_AL_MAR, VISTA_A_AREAS_COMUNES | D,O | **PUB** en D |
| `terraza` / `area_terraza` | Tiene terraza / Área de terraza | BOOLEANO / DECIMAL m² | — | D,C | OPC. **Dos claves a propósito**: la presencia se sabe en la visita, el metraje no; `area_terraza` vacía no significa que no haya terraza |
| `balcon` | Tiene balcón | BOOLEANO | — | D | OPC |
| `jardin` / `patio` / `area_jardin_patio` | Jardín / Patio / Área de uso exclusivo | BOOLEANO / BOOLEANO / DECIMAL m² | — | C,D | OPC. Jardín y patio no son lo mismo: quien busca jardín no debe visitar patios |
| `piscina` | Piscina | BOOLEANO | — | C | OPC |
| `depositos` / `deposito_area` | Depósitos / Área de depósito | ENTERO / DECIMAL m² | — | D,O | OPC (en Lima suele ir independizado junto con la cochera y forma parte del precio) |
| `tipo_estacionamiento` | Tipo de estacionamiento | LISTA | SIMPLE, DOBLE_LINEAL, DOBLE_PARALELO, MOTO | D,O,C | OPC (`estacionamientos = 2` no distingue dos cocheras de un doble lineal) |
| `estacionamiento_independizado` | Estacionamiento independizado | BOOLEANO | — | D,O,L | OPC — **provisional**: lo cierra de verdad §5 |
| `mascotas_reglamento` | El reglamento permite mascotas | BOOLEANO | — | **C,D** | OPC. Hecho del edificio, no del encargo (su gemelo comercial está en §4). **Corregido por medición al ejecutar `V80` (2026-08-24): aquí decía «D», y su condición `mascotas_aceptadas` se pacta en C y D — el hecho no puede llegar menos lejos que su condición** |

### 3.7 Comercial y logístico

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `tipo_acceso` | Tipo de acceso | LISTA | A_PIE_DE_CALLE, ESQUINA_A_CALLE, GALERIA_INTERIOR, PASAJE_COMERCIAL, CENTRO_COMERCIAL, INTERIOR_DE_EDIFICIO, MERCADO | L | **`PUB`** (corregido). Se sembró `ALT` en `V81` y **`V82` lo bajó a `PUB` el mismo día**, por decisión del titular: en este modelo `ALT` bloquea **también el alta**, y un local se tiene que poder **registrar** sin haberlo visitado. Sigue **impidiendo publicar**, que es lo que se quería: sin él, 40 m² a S/ 3 000 son caros a pie de calle en Miraflores y absurdos en el interior de Mesa Redonda |
| `en_esquina` | Está en esquina | BOOLEANO | — | L,O,A | OPC (ortogonal a `tipo_acceso`) |
| `clase_edificio` | Clase de edificio | LISTA | A_PLUS, A, B, C, NO_APLICA | O | **PUB**. Es como el mercado de oficinas de Lima se segmenta a sí mismo; sin ella, un precio por m² mezcla dos mercados |
| `metraje_arrendable` | Metraje arrendable | DECIMAL m² | — | O,L,A | **PUB** en O. La renta se cotiza en USD/m²/mes sobre área arrendable; hoy ni el denominador es homogéneo entre fichas |
| `banos_comunes_piso` | Baños comunes en el piso | BOOLEANO | — | O | PUB (un `banos = 0` se lee como defecto cuando puede ser el estándar del edificio) |
| `posiciones_trabajo` | Posiciones de trabajo | ENTERO | — | O | OPC (la demanda se formula en personas, no en metros) |
| `salas_reunion` | Salas de reunión | ENTERO | — | O | OPC |
| `aforo_itse` | Aforo autorizado (ITSE) | ENTERO personas | — | L,O,A | **PUB** (es el techo del negocio del inquilino) |
| `certificado_itse` | Certificado ITSE | LISTA | VIGENTE, VENCIDO, EN_TRAMITE, NO_TIENE | L,O,A | **PUB** (el hecho verificable detrás de `apto_licencia_funcionamiento`, que hoy es un booleano sin procedencia) |
| `area_libre` | Área libre / patio de maniobras | DECIMAL m² | — | A | **PUB** (la resta `area_terreno − techada` no vale: `area_terreno` es opcional) |
| `profundidad_patio_maniobras` | Profundidad de patio | DECIMAL m | — | A | PUB (un T3S3 necesita 30-35 m para girar; 400 m² largos y angostos no equivalen a 400 m² cuadrados) |
| `acceso_vehiculo_maximo` | Vehículo máximo que ingresa | LISTA | CAMIONETA, CAMION_2_EJES, CAMION_3_EJES, TRAILER_T3S3, CONTENEDOR_40_PIES | A,T,L | **PUB** en A. Parte el mercado en dos |
| `muelles_carga` / `tipo_muelle` | Muelles / Tipo de muelle | ENTERO / LISTA | SIN_MUELLE, A_NIVEL_DE_PISO, ANDEN_ELEVADO, ANDEN_CON_NIVELADOR, MIXTO | A | **PUB**. Contar muelles sin decir el tipo deja la cifra sin significado |
| `puertas_ingreso` / `ancho_puerta_ingreso` / `alto_puerta_ingreso` | Puertas de ingreso / Ancho / Alto | ENTERO / DECIMAL m / DECIMAL m | — | A | PUB |
| `capacidad_portante_piso` | Capacidad portante del piso | DECIMAL t/m² | — | A | **PUB**. El dato que más veces rompe una operación ya negociada |
| `tipo_piso` | Tipo de piso | LISTA | CONCRETO_PULIDO, CONCRETO_ENDURECIDO, LOSA_SIN_TRATAR, AFIRMADO, TIERRA | A | OPC |
| `luz_entre_columnas` | Luz entre columnas | TEXTO (m) | — | A | OPC |
| `posiciones_pallet` | Capacidad en posiciones pallet | ENTERO | — | A | OPC |
| `area_oficinas` | Área de oficinas administrativas | DECIMAL m² | — | A | PUB (sin separarla, esos metros se cotizan como almacén) |
| `condicion_almacenamiento` | Condición de almacenamiento | LISTA | SECO, REFRIGERADO, CONGELADO, MATERIALES_PELIGROSOS, DEPOSITO_TEMPORAL_ADUANERO | A | OPC (submercados que casi no se cruzan) |
| `balanza_camionera` | Balanza camionera | BOOLEANO | — | A | OPC |
| `estacionamientos_camiones` | Estacionamiento de camiones | ENTERO | — | A | OPC (mezclarlos con `estacionamientos` hace el número inútil para ambos casos) |
| `via_de_acceso` | Vía principal de acceso | TEXTO | — | A,T | OPC («Panamericana Sur km 32» vale más que el distrito) |

### 3.8 Terreno y parámetros urbanísticos

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `condicion_terreno` | Condición del terreno | LISTA | URBANO_HABILITADO, EN_PROCESO_DE_HABILITACION, RUSTICO_ERIAZO, ZONA_INFORMAL_SIN_HABILITAR | T | ~~**ALT**~~ → **`PUB`. DEROGADA EN SU EJE DE EXIGENCIA por D-3 del titular, 2026-08-25** (`encargo-corte-5-terreno.md` §2). El argumento de esta celda —*«500 m² habilitados en Surco y 500 m² rústicos en Pachacámac son hoy la misma fila; el agente lo sabe en la puerta»*— se conserva porque sostiene **que la clave debe existir**, y eso sigue en pie. Lo que el titular rechaza es la segunda mitad: *«BROX debe poder registrar un terreno aunque todavía no se conozca o confirme su condición. No asumir que URBANO_HABILITADO / EN_PROCESO / RUSTICO puede determinarse visualmente.»* `ALT` bloquea **dos** puertas —el alta y la publicación—; `PUB` sólo la segunda. Era la única `ALT` que esta auditoría proponía para el Corte 5. **Va en la subtanda 5B**, no en 5A |
| `situacion_registral` | Situación registral | LISTA | INSCRITO_EN_SUNARP, EN_SANEAMIENTO, NO_INSCRITO_SOLO_POSESION | T,C | **PUB** |
| `fondo` | Fondo | DECIMAL m | — | T,C | **PUB** en T. Un aviso de terreno se escribe «10 × 20»; 200 m² de 8×25 y de 20×10 sirven para cosas distintas |
| `posicion_en_manzana` | Posición en la manzana | LISTA | UN_FRENTE, DOS_FRENTES, TRES_FRENTES, CUATRO_FRENTES, ESQUINA | T,C | **PUB** |
| `topografia` | Topografía | LISTA | PLANO, PENDIENTE_LEVE, PENDIENTE_PRONUNCIADA, BAJO_NIVEL_DE_VIA, ACCIDENTADO | T | OPC |
| `altura_normativa_pisos` | Altura normativa | ENTERO pisos | — | T,C | **PUB** en T. La zonificación sola no lo dice: depende de la vía |
| `coeficiente_edificacion` | Coeficiente de edificación | DECIMAL | — | T | OPC (× área = área vendible; con eso una inmobiliaria decide en dos minutos) |
| `area_libre_minima` | Área libre mínima | DECIMAL % | — | T | OPC |
| `retiro_municipal` | Retiro municipal | DECIMAL m | — | T | OPC |
| `usos_compatibles` | Usos compatibles | TEXTO | — | T | OPC (línea distinta de la zonificación en el certificado de parámetros) |
| `certificado_parametros_vigente` | Certificado de parámetros | BOOLEANO | — | T | OPC (distingue lo que dijo el propietario de lo certificado) |
| `lote_minimo_normativo` | Lote mínimo normativo | DECIMAL m² | — | T | OPC |
| `tipo_via_acceso` | Tipo de vía del frente | LISTA | AVENIDA, CALLE_O_JIRON, PASAJE, CARRETERA, TROCHA_O_SIN_VIA | T,L,A | **PUB** en T. Avenida o pasaje es el doble de precio para el mismo metraje |
| `estado_via` | Estado de la vía | LISTA | ASFALTADA, AFIRMADA, SIN_AFIRMAR | T,A | OPC |
| ~~`estado_ocupacion`~~ **fila DEROGADA EN LOS CUATRO EJES** — ver debajo | ~~Estado de ocupación~~ | ~~LISTA~~ | ~~LIBRE_Y_DESOCUPADO, CON_EDIFICACION_A_DEMOLER, OCUPADO_POR_TERCEROS, EN_USO_POR_EL_PROPIETARIO~~ | ~~T,C~~ | ~~**PUB** en T~~ |
| `estado_ocupacion` **(vigente)** | Estado de ocupación | LISTA | DESOCUPADO, OCUPADO_POR_EL_PROPIETARIO, OCUPADO_POR_INQUILINO, OCUPADO_POR_TERCEROS_SIN_TITULO | **A,C,D,L,O,T,X** (`aplica_todos = false`) | **OPC** en los siete |
| `edificacion_existente` | Edificación existente | DECIMAL m² | — | T | OPC (declarar 0 no es lo mismo que no saberlo) |
| `cercado` | Cercado o amurallado | BOOLEANO | — | T | OPC |
| `restriccion_arqueologica` | Restricción arqueológica (CIRA) | LISTA | NO_APLICA, CIRA_OBTENIDO, EN_TRAMITE, REQUERIDO_NO_INICIADO | T | OPC (no se infiere del distrito: depende del polígono) |
| `zona_de_riesgo` | Zona de riesgo declarada | BOOLEANO | — | T,C | OPC |
| `manzana_lote` | Manzana y lote | TEXTO (ESTRUCTURAL → `interiorUnidad`) | — | T | **PUB**. Hoy se escribe dentro de `direccion` y dos agentes captan el mismo terreno con dos direcciones distintas |
| `latitud` / `longitud` *(ya existen, estructurales)* | — | — | — | T | **PUB** en T: un terreno suele no tener dirección útil |
| `torre_bloque` | Torre o bloque | TEXTO | — | D | OPC (el 501 existe en la Torre A y en la B; la mayoría del stock limeño no tiene torres, por eso no bloquea). **Se ejecutó en el Corte 3 (`V80`), no en el 5: está redactada en esta sección por arrastre, pero su `aplica_a` es `D` y un corte se define por tipo, no por número de sección** |

> **Sobre `estado_ocupacion`, la fila tachada:** la **derogó
> [D-C5-1](decision-estado-ocupacion-en-los-siete.md)**, resuelta por CONTROL el
> **2026-08-24** por encargo del titular, y lo hizo **en los cuatro ejes** —
> vocabulario, aplicabilidad, `aplica_todos` y exigencia:
>
> 1. **Vocabulario**: `CON_EDIFICACION_A_DEMOLER` no es un estado de ocupación
>    sino de la edificación, y su sitio es `edificacion_existente`; los otros tres
>    se renombran para que signifiquen lo mismo en un departamento que en un
>    terreno.
> 2. **Aplicabilidad**: **los siete**, no `T,C`. La condición
>    `entrega_desocupado` ya se pacta en los siete desde `V77`, y el guard 2.2 de
>    `V78` abortaría la migración si el hecho llegara menos lejos.
> 3. **`aplica_todos = false`**, con filas explícitas.
> 4. **Exigencia `OPC`**, no `PUB` en `T`. El argumento original de D-C5-1 §5
>    caducó por medición el 2026-08-25 (la señal `PUB` de la PROPIEDAD **sí**
>    existe); la conclusión se mantiene **por D-1 del titular**, no por aquel
>    argumento.
>
> Se ejecuta en la **subtanda 5A** del Corte 5 (`V84`).

**Totales:** ~20 correcciones sin clave nueva y **~85 claves nuevas de PROPIEDAD**, de las cuales **ninguna queda en ALT**: las dos que lo eran —`tipo_acceso` en L y `condicion_terreno` en T— están derogadas en su eje de exigencia (tabla de abajo), y todas las demás reparten entre PUB y OPC. Toda la exigencia `ALT` que este documento propone cae sobre **claves que ya existían** (§3.1, «cero claves nuevas»): `area_terreno` en C, `piso` en D y O, e `interiorUnidad` en D.

> **Este total ha estado mal dos veces, y las dos por contar sobre sus propias
> filas sin recorrerlas.** Decía «4 en ALT», se corrigió a «1 en ALT
> (`area_terreno` en C, §3.1)» el **2026-08-25** — pero ese 1 quedó colgando de
> «~85 claves nuevas», y `area_terreno` **no es una clave nueva**: es una de las
> 19 que ya existen. Corregido otra vez el mismo día, tras la segunda vuelta de
> auditoría: el total y su ejemplo hablan ahora del mismo conjunto. Las tres
> filas que se caen no se borran: cada una se nombra con lo que la derogó —y la
> tercera, con que nunca fue `ALT`—, porque el argumento de cada celda sigue
> sosteniendo **que la clave debe existir**, y lo derogado es sólo su eje de
> exigencia.
>
> | clave | decía | dice su propia fila | quién lo derogó |
> |---|---|---|---|
> | `tipo_acceso` en **L** | ALT | **PUB** (§3.7, fila `tipo_acceso`) | `V82`, decisión del titular el **2026-08-24**, el mismo día que `V81` la sembró `ALT`: `ALT` bloquea **también el alta** y un local se tiene que poder registrar sin haberlo visitado. Sigue impidiendo publicar |
> | `condicion_terreno` en **T** | ALT | **PUB** (§3.8, fila `condicion_terreno`) | **D-3 del titular, 2026-08-25** (`encargo-corte-5-terreno.md` §2): BROX debe poder registrar un terreno aunque su condición no se conozca todavía. Va en la subtanda **5B** |
> | `metraje_construido` en **A** | ALT | **PUB en A y C** (§3.1, fila `metraje_construido`) | nadie: **nunca fue ALT**. El total lo contaba mal ya al escribirse — §3.1 dice PUB desde la primera versión |
>
> Medido contra `controllocal_dev` el 2026-08-25, el catálogo **aplicado** lleva
> `ALT` sólo donde ya lo tenía antes de esta auditoría (`metraje_total` en los
> siete, `dormitorios` en C y D, `zonificacion` en T): **ninguna de las claves
> nuevas de este documento ha entrado `ALT`**. `area_terreno` en C es propuesta
> de 5B, todavía no aplicada.

---

## 4. Lo que es del ENCARGO y no de la propiedad

> ### Estado desde el Corte 0C — 2026-08-21
>
> **La infraestructura que esta sección proponía ya está implementada** por
> `V73__el_sujeto_del_dato.sql` y `V74__las_primeras_condiciones_del_encargo.sql`.
> Ya existen:
>
> - `catalogo_atributo.sujeto`
> - `catalogo_atributo_operacion` — aplicabilidad por **(tipo × operación)**
> - `atributo_encargo` — almacenamiento propio, con FK compuesta de tenant
> - la separación física entre atributos de PROPIEDAD y de ENCARGO
> - los guards que impiden escribir un atributo en el sujeto equivocado
>   (`tg_atributo_gobernado` y `tg_atributo_de_encargo`)
>
> **Por tanto esta sección ya no propone la arquitectura.** Su contenido
> vigente es:
>
> 1. la clasificación PROPIEDAD / ENCARGO;
> 2. la lista de condiciones comerciales del encargo;
> 3. sus tipos, opciones y aplicabilidad;
> 4. el criterio rector:
>
> > **Un hecho del inmueble sobrevive al encargo; una condición negociada
> > muere con él.**
>
> Lo que quedaba pendiente era **la siembra**, y la cierra `V77` — ver
> «El inventario, condición por condición» al final de esta sección.

Antes del Corte 0C el encargo era `captacion` a secas (con `motivo_operacion` A/V y una `condicion_economica_captacion` que sólo modela importe, moneda y **comisión** — `tratamiento_igv` ahí se refiere a la comisión, no a la renta), y nada de lo de abajo cabía en ninguna parte. La forma que se construyó es la que sigue, y se deja escrita porque explica **por qué** es así:
- `catalogo_atributo.sujeto = 'ENCARGO'` (§2, C-7).
- Tabla de aplicabilidad propia: **`catalogo_atributo_operacion(id_catalogo_atributo, tipo_propiedad VARCHAR(1), tipo_operacion VARCHAR(1), exigencia VARCHAR(3))`**, con **PK `(id_catalogo_atributo, tipo_propiedad, tipo_operacion)`** y FK `ON DELETE CASCADE` a `catalogo_atributo` — calcada de `catalogo_atributo_tipo` (V48). La aplicabilidad del encargo es por **(tipo, operación)**, no sólo por tipo: `partida_registral` bloquea una VENTA y es irrelevante en un ALQUILER; `garantia_meses` es al revés. Esto cierra el límite de modelo que las auditorías de C y O dejaron a la vista y que hoy obliga a degradar a OPC cosas que en venta son PUB.
- Valores en **`atributo_encargo(organizacion_id, id_captacion, clave, valor_texto, valor_numero, valor_booleano, valor_fecha, valor_moneda)`**, PK (id_captacion, clave), **FK compuesta** `(organizacion_id, id_captacion)` → `uq_captacion_org` (el gate del discriminador de tenant lo exige).

**La regla que gobierna el reparto:** un hecho del inmueble sobrevive al encargo; una condición negociada muere con él. Si al firmar el siguiente alquiler el dato puede cambiar sin que la propiedad haya cambiado, es del ENCARGO. Cada par se declara **separado**, no se reutiliza el campo físico:

| Par | PROPIEDAD (hecho) | ENCARGO (condición) |
|---|---|---|
| Muebles | `amoblado` | `se_ofrece_amoblado` |
| Mascotas | `mascotas_reglamento` (lo que permite el reglamento) | `acepta_mascotas` (lo que quiere el propietario **en este alquiler**) |
| Mantenimiento | `cuota_mantenimiento` (lo que cobra la junta) + `base_mantenimiento` | `mantenimiento_a_cargo_de` (quién lo paga aquí) |
| Cocheras | `estacionamientos` | `estacionamientos_incluidos` + `precio_estacionamiento_adicional` |
| Implementación | `nivel_implementacion` | `se_entrega_implementado` + `meses_gracia_implementacion` |
| Rubro | `rubro_permitido` (zonificación y reglamento) | `rubros_excluidos_por_titular` (lo que el dueño rechaza) |
| Ocupación | `estado_ocupacion` | `entrega_desocupado` |
| Divisibilidad | `lote_minimo_normativo` | `acepta_venta_fraccionada` / `arrendamiento_parcial` |
| Racks | (parte de `nivel_implementacion`) | `racks_incluidos` |

**Catálogo del ENCARGO — condiciones de ALQUILER (`tipo_operacion='A'`)**

| clave | rótulo | tipo | opciones / unidad | aplica_a | nivel |
|---|---|---|---|---|---|
| `disponible_desde` | Disponible desde | **FECHA** | — | todos | **PUB**. `disponibilidad_comercial` es un estado (D/R/A/T), no una fecha: no distingue entrega inmediata de ocupado hasta marzo |
| `garantia_meses` | Meses de garantía | ENTERO | meses, min 0 | todos | **PUB** |
| `adelanto_meses` | Meses de adelanto | ENTERO | meses, min 0 | todos | **PUB**. Aparte de la garantía: garantía + adelanto es cuánto dinero necesita el inquilino el día de la firma, y el motivo más frecuente de caída después de la visita |
| `plazo_minimo_meses` | Plazo mínimo | ENTERO | meses | todos | **PUB** en L,O,A (24-60 meses en comercial: descalifica a quien necesita 12) |
| `igv_arrendamiento` | IGV sobre la renta | LISTA | GRAVADO_18, NO_GRAVADO, POR_DEFINIR | todos | **PUB** en L,O,A. «S/ 5 000» con IGV y «S/ 5 000 más IGV» son S/ 900 al mes de diferencia |
| `modalidad_precio` | Modalidad del precio | LISTA | MENSUAL_TOTAL, POR_M2_AL_MES | A,L,O | **PUB** en A. El agente que oye «seis dólares el metro» escribe 6 en un campo que el sistema lee como renta mensual: error de tres órdenes de magnitud que ninguna validación detecta hoy |
| `mantenimiento_a_cargo_de` | Mantenimiento a cargo de | LISTA | PROPIETARIO, INQUILINO | D,O,L,C,A | **PUB** |
| `estacionamientos_incluidos` | Cocheras incluidas | ENTERO | min 0 | D,O,L,C,A | **PUB** |
| `precio_estacionamiento_adicional` | Precio por cochera adicional | IMPORTE | /mes | D,O,L | PUB |
| `acepta_mascotas` | Acepta mascotas | BOOLEANO | — | D,C | **PUB** |
| `se_ofrece_amoblado` | Se ofrece amoblado | BOOLEANO | — | D,C | **PUB** |
| `equipamiento_incluido` | Equipamiento incluido | LISTA_MULTIPLE | REFRIGERADORA, COCINA, LAVADORA, SECADORA, AIRE_ACONDICIONADO, MUEBLES_SALA, MUEBLES_DORMITORIO, CORTINAS, TELEVISOR | D,C | OPC. Hoy se negocia por WhatsApp y no queda escrito: ahí nacen las disputas al entregar |
| `uso_admitido_por_titular` | Uso admitido por el propietario | LISTA | VIVIENDA, VIVIENDA_Y_OFICINA, COMERCIAL | C,D | OPC (distinto de `uso`, que dice lo que la zonificación permite) |
| `se_entrega_implementado` | Se entrega implementado | BOOLEANO | — | L,O,A | OPC |
| `meses_gracia_implementacion` | Meses de gracia | ENTERO | meses | L,O,A | OPC. Sin registrarla, el histórico miente sobre la renta efectiva del primer año |
| `respaldo_exigido` | Respaldo exigido al inquilino | LISTA | SOLO_DEPOSITO, AVAL_PERSONAL, AVAL_CON_INMUEBLE, CARTA_FIANZA_BANCARIA | L,O,A | OPC (filtra la demanda antes de la visita y define qué documentos pedirá la evaluación) |
| `rubros_excluidos_por_titular` | Rubros que el propietario no acepta | TEXTO | — | L,O,A | OPC |
| `racks_incluidos` | Se entregan los racks | BOOLEANO | — | A | OPC |
| `arrendamiento_parcial` / `area_minima_arrendable` | Acepta arrendar por partes / Área mínima | BOOLEANO / DECIMAL m² | — | A,L,O | OPC |

**Catálogo del ENCARGO — condiciones de VENTA (`tipo_operacion='V'`)**

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `entrega_desocupado` | Se entrega desocupado | BOOLEANO | — | todos | **PUB** |
| `apto_credito_hipotecario` | Acepta crédito hipotecario | BOOLEANO | — | todos | **PUB**. Aunque la unidad esté saneada, un comprador financiado alarga el cierre 45-60 días y hay vendedores que sólo quieren contado |
| `acepta_financiamiento_directo` | Acepta financiamiento del propietario | BOOLEANO | — | T,C,D | **PUB** en T (es lo que hace que el aviso funcione en la periferia) |
| `acepta_permuta` | Acepta permuta | BOOLEANO | — | todos | OPC |
| `acepta_venta_fraccionada` | Acepta vender por partes | BOOLEANO | — | T | OPC |
| `acepta_aporte_a_proyecto` | Acepta aportar el terreno a un proyecto | BOOLEANO | — | T | OPC (canje terreno por metros: la salida habitual para lotes bien zonificados) |

### El inventario, condición por condición — V77

Las tablas de arriba son **la lista**, no la implementación. Antes de sembrar,
cada condición se pasó por la pregunta que decide el sujeto:

> ¿Describe **cómo ES** el inmueble, o **cómo se acordó comercializarlo** en
> este encargo? Si al abrir otro encargo sobre la misma propiedad el dato puede
> cambiar sin que la propiedad haya cambiado, es del ENCARGO.

Las 26 la pasan como ENCARGO. Lo que la revisión sí cambió son cuatro cosas, y
se dicen porque el documento decía otra:

| Qué decía §4 | Qué quedó, y por qué |
|---|---|
| `acepta_mascotas` | **`mascotas_aceptadas`** — es la clave que V74 sembró y ya tiene aplicabilidad. Renombrarla sería una migración sin ninguna ganancia; el catálogo es la autoridad y el documento se corrige |
| `se_ofrece_amoblado` aplica a D,C | **D, C y O** — una oficina amoblada es un producto real y se anuncia como tal. Lo decidió V74 con esa razón escrita |
| `igv_arrendamiento` con opción `POR_DEFINIR` | **Sin `POR_DEFINIR`.** La ausencia del valor ya significa «todavía no se sabe». Con la opción habría **dos formas de decir lo mismo**, que es la clase de duplicidad que este plan lleva cortes enteros retirando. Quedan `GRAVADO_18` y `NO_GRAVADO` |
| `precio_estacionamiento_adicional` (IMPORTE) y `equipamiento_incluido` (LISTA_MULTIPLE) | Se siembran, **y obligan a ensanchar el cable**: `AtributoRequest` llevaba `(clave, valor)` y no sabía transportar una moneda ni una lista. El servicio ya lo modelaba (`ValorAtributo(clave, valor, moneda, valores)`); era el DTO web el que iba estrecho. Sin eso, dos condiciones quedarían sembradas y mudas |

**Ninguna entra como obligatoria.** Las 26 son `OPC`, por la misma razón que
V74 escribió: que una garantía sea imprescindible para publicar un alquiler
puede ser cierto, pero **es una decisión del negocio que nadie ha tomado**, y
tomarla dentro de la migración que introduce la clave dejaría fichas ya
publicadas incompletas de golpe. Subir una a PUB es una línea de SQL el día que
se decida. Los niveles que las tablas de arriba proponen quedan como **lo que se
propuso**, no como lo que se sembró.

#### Los pares semánticos, todos

El par no es una curiosidad de `amoblado`: es el patrón. Un guard de V77 los
recorre **todos** y rompe la migración si dos de un par acaban en el mismo
sujeto.

| Hecho de la PROPIEDAD | Condición del ENCARGO | Estado del lado PROPIEDAD |
|---|---|---|
| `amoblado` | `se_ofrece_amoblado` | existe |
| `cuota_mantenimiento` | `mantenimiento_a_cargo_de` | existe |
| `estacionamientos` | `estacionamientos_incluidos` | existe |
| `rubro_permitido` | `rubros_excluidos_por_titular` | existe |
| `uso` *(columna, no atributo)* | `uso_admitido_por_titular` | existe |
| `mascotas_reglamento` | `mascotas_aceptadas` | ✅ **cubierto** — `V80`, en **C y D** |
| `nivel_implementacion` | `se_entrega_implementado` | ✅ **cubierto** — `V81` (Corte 4, 2026-08-24), en **A, L y O** |
| `estado_ocupacion` | `entrega_desocupado` | ✅ **cubierto** — `V84` (Corte 5 · 5A, 2026-08-25), **OPC en los siete** tipos, que son exactamente aquellos en los que la condición se pacta desde `V77` |
| `lote_minimo_normativo` | `acepta_venta_fraccionada` | **falta** — Corte 5 |

Que falte el lado PROPIEDAD **no impide sembrar el lado ENCARGO**: la condición
es cierta por sí sola —el propietario acepta o no acepta vender por partes— y
esperar al hecho estructural dejaría el encargo mudo por algo que no le
pertenece. Lo que el guard impide es lo contrario: que cuando llegue el hecho,
alguien lo meta en el sujeto de su condición.

**Y la existencia de un dato de PROPIEDAD nunca responde por su condición.** Que
el inmueble tenga muebles no dice si este alquiler los incluye; que la junta
cobre mantenimiento no dice quién lo paga aquí. Son preguntas distintas con
respuestas distintas, y por eso son claves distintas.

#### La segunda mitad de la regla del par — `V78`, 2026-08-22

Vivir en sujetos distintos **no basta**. Falta una condición que nadie estaba
mirando:

> **El hecho tiene que llegar donde llega su condición.** Si la condición aplica
> a un tipo de propiedad donde el hecho no aplica, en ese tipo el pacto es *la
> única casilla* donde cabe el hecho — y la separación queda escrita en el
> catálogo y deshecha en la práctica.

Así se abrieron tres huecos sin que ninguna migración hiciera nada raro: `V74`
amplió `se_ofrece_amoblado` a **OFICINA** («una oficina amoblada es un producto
real») sin ampliar `amoblado`, y `V77` llevó `mantenimiento_a_cargo_de` a
**ALMACÉN** y **CASA** sin ampliar `cuota_mantenimiento`. `V78` los cierra y deja
la comprobación puesta: `SujetoDelDatoIntegrationTest` la recorre sobre el
catálogo real, y los pares se declaran **una sola vez** en la constante
`PARES_DELIBERADOS` para que las dos comprobaciones —misma-jaula-no y
llega-igual-de-lejos— no puedan mirar listas distintas.

La regla **no** se le exige a un par cuyo lado PROPIEDAD todavía no existe: a un
hecho que no ha nacido no se le pide cobertura. ~~Los cuatro que faltan
(`mascotas_reglamento`, `nivel_implementacion`, `estado_ocupacion`,
`lote_minimo_normativo`)~~ **Quedan DOS** —`estado_ocupacion` y
`lote_minimo_normativo`, los dos del Corte 5—: `V80` cerró
`mascotas_reglamento` (Corte 3) y `V81` cerró `nivel_implementacion` (Corte 4),
ambos el 2026-08-24. Corregido el **2026-08-25**. Siguen con su corte asignado
arriba, y el día que lleguen tendrán que nacer cubriendo a su condición o el gate
lo dirá.

> Y hay una exención que esta sección no menciona: el bucle del guard lleva
> `AND NOT h.aplica_todos`, así que **ningún par cuyo hecho tenga
> `aplica_todos = true` se comprueba** — hoy, `estacionamientos`. Está anotado
> como deuda estructural en §2.3 bis de `pendientes-brox.md`.

#### La ausencia no es un «no»

Ninguna de las 26 tiene valor por defecto, y eso se sostiene en los tres sitios:

- **la base** no pone `DEFAULT` en ninguna;
- **el Core** no rellena: una condición que nadie declaró simplemente no está en
  `condiciones[{idEncargo}]`;
- **la pantalla** tampoco: un `BOOLEANO` sin declarar se pinta **«Sin declarar»**
  y no como una casilla vacía, que se lee igual que un «no».

```
ausencia de se_ofrece_amoblado      ≠  se ofrece sin muebles
ausencia de mascotas_aceptadas      ≠  no acepta mascotas
ausencia de mantenimiento_a_cargo_de ≠  lo paga el propietario
ausencia de entrega_desocupado      ≠  se entrega ocupado
```

Es lo que permitirá a KAIROS **preguntar sólo lo que falta** en lugar de heredar
supuestos que nadie dijo.

---

## 5. Unidades relacionadas — diseño mínimo, genérico

**El problema, medido.** Una cochera hoy es el escalar `2.0000` en `atributo_propiedad.valor_numero` bajo la clave `estacionamientos` (ENTERO, `aplica_todos=true`, min 0). No tiene identidad, ni titular, ni partida, ni precio, ni disponibilidad, ni fotos, ni histórico. Y **no existe ninguna relación propiedad↔propiedad**: de los 24 FK que apuntan a `propiedad` en 11 tablas, ninguna tiene dos; `Propiedad.java` tiene un solo `@ManyToOne` (a `persona_rol`); no hay auto-FK, ni `id_propiedad_matriz`, ni nada equivalente en V1…V69.

**Invariante que hay que sostener:** *una unidad con partida propia es una propiedad, nunca un atributo.* Un depósito independizado, un almacén anexo y una cochera con partida son el mismo caso; el diseño no puede llamarse `cochera_departamento`.

**Diseño:**

```
unidad_relacionada
  organizacion_id            NOT NULL
  id_propiedad_principal     NOT NULL   -- FK compuesta (organizacion_id, id) → uq_propiedad_org
  id_propiedad_relacionada   NOT NULL   -- misma FK compuesta
  tipo_relacion              VARCHAR(1) NOT NULL
  vigente_desde              DATE NOT NULL
  vigente_hasta              DATE NULL
```

- **Dos FK a `propiedad`**, nunca una tabla por par de tipos: *qué* es la unidad ya lo dice `propiedad.tipo_inmueble`; la relación sólo dice **cómo** se relacionan. Así sirve igual a cochera, depósito, almacén anexo y azotea.
- `tipo_relacion` con **códigos unitarios** y CHECK cerrado (V40 estrechó tres columnas `estado` justo por esta invariante): **`'A'`** accesoria (misma partida, no viaja sola), **`'I'`** independiente (partida propia, se comercializa aparte), **`'C'`** vinculada por reglamento interno.
- **`organizacion_id` NOT NULL + FK compuestas** copiando `fk_captacion_propiedad_org`: lo exige el gate del discriminador de tenant y sin eso una relación cruzaría dos corredoras.
- **Guardas en la base** (constraint/trigger; el mensaje va en el servicio, como en V47/V48): no auto-relación; único parcial sobre el par mientras `vigente_hasta IS NULL`; y la regla que **encarna** la invariante — una relación `'I'` exige que la unidad relacionada exista como fila propia en `propiedad` con su `partida_registral` (§3.2) y pueda llevar su propio encargo.
- **Códigos de tipo nuevos** en `ck_propiedad_tipo` (hoy L,O,D,C,T,A,X): **`'E'`** estacionamiento y **`'B'`** depósito/bodega. Ni `'A'` (es ALMACÉN, con `altura_libre` y `carga_electrica_kw` que no describen una cochera) ni `'X'` (`AtributosGobernados` ya avisa del error). **V54 es la receta completa**: CHECK + filas en `catalogo_atributo_tipo` + `tipo_documento_requerido`, y Angular no se toca. Aviso de V54: `'A'` significa ALMACÉN en `tipo_inmueble` y ALQUILADO en `disponibilidad_comercial` — quien añada letras mira las dos columnas.
- **El contador residual se queda**, y significa otra cosa: `estacionamientos` pasa a ser *"cuántos espacios vienen con esto"* (descriptivo, legítimo) y aplica sólo a los tipos con edificación (L,O,D,C,A; no T). Los estacionamientos con partida se mueven a filas de `propiedad` + relación `'I'`.
- **El SPA no puede ramificar.** `PropiedadResponse` (`PropiedadUniversalDtos.java:331`) gana `unidadesRelacionadas[]` con el rótulo **ya traducido por el Core** (estilo `AtributosGobernados.rotuloDelTipo`), y `GuionRegistroPropiedad` gana su paso. Un `if (tipo === 'D') mostrarCocheras()` en Angular tiene que romper el build.
- **Aviso antes de tocar:** `FronteraDeAutoridadEnElSpaTest` fija en `CLAVES_QUE_NO_SE_MOVIERON` el nombre `numeroEstacionamientos` y comprueba que **sigue** existiendo en el SPA (línea 78). Resignificarlo rompe ese test **a propósito**: es una decisión de producto, no un defecto a arreglar de paso.
- **Precedente exacto**: V60 declara autoridad → V61 consolida → V62 retira la columna espejo. Ese mismo trío ya se le aplicó una vez a `numero_estacionamientos`.

---

## 6. Orden de ejecución por cortes

Un corte se cierra con **gate + tests + evidencia**. La verificación de cierre es `verificacion/Verificar-Cierre.ps1` con `TEST_DB_URL`, nunca `mvn clean install`. Todo endpoint nuevo necesita su fila en `docs/ai/matriz-operacion-rol.md` o `MatrizOperacionRolTest` tumba la compilación.

> **Este orden se corrigió el 2026-08-20, después de escribir el plan.** La
> primera versión ponía el catálogo delante de la edición. Es el orden
> equivocado: **contención antes que expansión del vocabulario**. Mientras el
> único editor pueda rechazar cinco tipos, inventar `rubro_permitido` y aplastar
> `uso` a `'C'`, cada día que pasa corrompe inmuebles — y añadir capacidades
> inmobiliarias encima es ampliar la superficie de lo que se puede perder.
>
> La numeración de migraciones se rehízo entera con él. **V70 está ocupada** por
> la publicación por encargo, así que la cadena arranca en **V71**.

---

### **Corte 0A — Contener la corrupción de edición** · va primero, y no es negociable

Antes de añadir **una sola** capacidad inmobiliaria nueva tiene que existir un
gate de conservación:

```
leer → abrir el editor → NO modificar ese dato → guardar → releer  ==  idéntico
```

Y no vale probarlo con un departamento feliz. Cubre **los siete códigos**
`L, O, D, C, T, A, X`, propiedad **y** encargos, y varios tipos de valor
gobernado — texto, entero, decimal, booleano y el que hoy es LISTA. Es la misma
forma de ida y vuelta que cerró D-E4-3 (`crear → leer → editar otra cosa →
releer`), y allí ya demostró encontrar pérdidas que ninguna prueba de
persistencia aislada ve.

Aquí entra también **la puerta única de edición**.

| | |
|---|---|
| **Migración** | **V71**: retirar `detalle_local_comercial` con el patrón V60→V61→V62 (consolidar `rubro_permitido`, `apto_licencia_funcionamiento`, `carga_electrica_kw` en `atributo_propiedad` y **borrar la tabla espejo**). |
| **Código** | Editor **universal** en el SPA que consume `PUT /propiedades/{id}` — el endpoint existe, está en la matriz, y `PropiedadesService` no lo llama desde ninguna parte. Retirar `local-form`, `catalogos-local.ts` (42 distritos y 25 rubros duplicados, con la fuente ya publicada en `GET propiedades/filtros`), `core/rubros.ts` (copia byte a byte) y el `uso: 'C'` literal de `locales.service.ts:61` + `LocalComercialServiceImpl:267,363`. `CoincidenciaCartera.rubroPermitido()` y `CoincidenciaServiceImpl.filaPropiedad()` dejan de leer `getDetalleLocal()`. El listado universal `/propiedades` busca rubro por `atributo_propiedad`. `codigos.ts`: retirar `TIPO_INMUEBLE`, `USO_INMUEBLE` y consumir `tipoRotulo` / `usoRotulo`, que el contrato ya publica. |
| **Invariantes** | Lo que la interfaz **no recibió o no modificó** conserva exactamente su semántica anterior. `uso` no se inventa. `rubro_permitido` no aparece porque Angular lo suponga. Ninguna matriz fija de campos en Angular. |
| **Prueba** | El gate de conservación de arriba, por los siete tipos. Más: añadir **los códigos unitarios `L,O,D,C,T,A,X`** a `TIPOS_DE_PROPIEDAD` en `FronteraDeAutoridadEnElSpaTest` y una tercera comprobación para la matriz *incondicional* — hoy los tres gates no ven `local-form` porque no ramifica: pinta la matriz entera sin condición. Con eso el build **falla hoy mismo**, que es lo que se quiere. |
| **Por qué primero** | Es lo único de este plan que está causando daño ahora. Y es coherente con la frontera ya congelada: BROX Core decide qué es verdad; BROX Web representa y ejecuta el caso de uso (D-A-1). |

---

### **Corte 0B — El catálogo aprende a hablar** · bloqueante, sin ninguna clave nueva

Sólo cuando 0A esté verde.

| | |
|---|---|
| **Migración** | **V72**: tabla `catalogo_atributo_opcion`; `atributo_propiedad_opcion`; columnas `valor_fecha`, `valor_moneda` en `atributo_propiedad`; `valor_maximo` en `catalogo_atributo`; ensanchar `ck_catalogo_atributo_tipo_dato` a `LISTA_MULTIPLE, FECHA, IMPORTE`; ensanchar `ck_catalogo_campo_estructural`; sustituir `catalogo_atributo_tipo.requerido` por `exigencia VARCHAR(3)` (`ALT`/`PUB`/`OPC`); extender el trigger `exigir_atributo_gobernado` a pertenencia de opción, rango min/max y moneda obligatoria en IMPORTE. |
| **Código** | `MotorDeCapturaImpl` deja de pasar `opciones=null`; `Pregunta.controlDe` gana LISTA_MULTIPLE / FECHA / IMPORTE; `PreguntaCatalogo` y `PreguntaCatalogoResponse` ganan `opciones[{valor,rotulo}]`, `valorMinimo`, `valorMaximo`, `exigencia`, `ayuda`; `/captura/apertura` y `/captura/definicion` publican opciones **con rótulo**. |
| **Prueba** | Test que recorre `catalogo_atributo` y falla si una LISTA no tiene opciones. E2E que verifica que `servicios_disponibles` llega con `control='LISTA'` y valores. Test que verifica que ninguna clave de tipo IMPORTE se guarda sin moneda. |
| **Evidencia** | La única LISTA sembrada hoy deja de viajar como texto libre. |
| **Hereda de V71** | Dos capacidades que el catálogo **todavía no sabe expresar**, y que quedaron a la vista al retirar `detalle_local_comercial`. No se resolvieron allí porque fingir que el catálogo ya las tiene habría sido inventar; se resuelven aquí. **`longitudMaxima`** es de las dos la que bloquea: el rubro perdió su `VARCHAR(120)` y ninguna clave puede hoy declarar cuánto mide su valor — 0B no está completo sin ella. La otra es la **exigencia del rubro**: el `NOT NULL` de la tabla por tipo decía «si hay local, hay rubro», y el catálogo lo declara `requerido = false` desde V48; con `exigencia ALT/PUB/OPC` ya existe dónde decidirlo a propósito en vez de heredarlo. |
| **Por qué antes de sembrar** | Hoy una `LISTA` no tiene vocabulario persistente y el motor acaba tratándola como texto libre. Sembrar decenas de campos antes de resolverlo trasladaría el catálogo a Angular — que es justo lo que el gate de D-A-1 rompe. |

---

### **Corte 0C — El ENCARGO como sujeto gobernado**

| | |
|---|---|
| **Migración** | **V73**: `catalogo_atributo.sujeto VARCHAR(10)` CHECK `('PROPIEDAD','ENCARGO')`; **`catalogo_atributo_operacion(id_catalogo_atributo BIGINT REFERENCES catalogo_atributo ON DELETE CASCADE, tipo_propiedad VARCHAR(1), tipo_operacion VARCHAR(1), exigencia VARCHAR(3))`, PK `(id_catalogo_atributo, tipo_propiedad, tipo_operacion)`** — la misma forma exacta que `catalogo_atributo_tipo` en V48, y por la misma razón: una fila de aplicabilidad **no tiene identidad propia**, es una fila *de un atributo*; un `id` autónomo dejaría la tabla sin decir a qué atributo clasifica. La FK va por **`id_catalogo_atributo`, no por `clave`**: `clave` sólo es única por organización (`uq_catalogo_atributo_clave` sobre `COALESCE(organizacion_id,0), clave`), y una FK compuesta con `organizacion_id` NULL —que es como se declaran los atributos del sistema— no verificaría nada. `tipo_operacion` toma el vocabulario de `captacion.motivo_operacion` (`'A'`, `'V'`). Y `atributo_encargo` con FK **real** compuesta a `uq_captacion_org`; trigger análogo a `exigir_atributo_gobernado`. |
| **Código** | `MotorDeCaptura` gana el paso de encargo; `AtributosGobernados` traduce por (tipo, operación); el DTO de captación publica los atributos del encargo; los tres pares del §4 quedan separados en el guion. |
| **Prueba** | La invariante de enrutamiento, **entera y comprobable precisamente porque la FK existe**: `sujeto='PROPIEDAD'` → aplicabilidad en `catalogo_atributo_tipo` → persiste en `atributo_propiedad` o en su campo estructural; `sujeto='ENCARGO'` → aplicabilidad en `catalogo_atributo_operacion` → persiste en `atributo_encargo`; **nunca en las dos**. Un test recorre el catálogo y falla si una clave tiene filas de aplicabilidad del sujeto que no le corresponde, y —cuando `aplica_todos = false`, que es el caso en que la tabla manda— si no tiene ninguna en la suya. Test de tenant: `atributo_encargo` lleva discriminador. |
| **Siembra** | Las ~25 claves de §4. **Aquí es donde `amoblado` deja de mentir.** |
| **Regla que congela** | Toda clave gobernada declara **exactamente un vocabulario, exactamente un sujeto y exactamente una autoridad** — y el sujeto **no** se resuelve con una FK polimórfica. Ver D-E4-3 §9. |

---

### **Corte 1 — Las 19 claves que ya existen** · migración **V78**

> **La numeración cambió, y conviene saber por qué.** Este corte se planificó
> para `V77`; ese número lo ocupó el **lenguaje completo del ENCARGO** (26
> condiciones, 3g del mapa), que tenía que ir delante para no empujar a resolver
> condiciones comerciales metiendo campos en `atributo_propiedad`. El Corte 1
> arranca en **`V78`**.

> **Ejecutado el 2026-08-22 · `V78__el_hecho_llega_donde_llega_su_condicion.sql`.**
> El corte se abrió con una pregunta distinta a la que este documento traía —
> *¿cada una de las 19 describe un hecho del inmueble, o estaba bloqueada
> porque confundíamos un hecho con una condición del encargo?*— y la respuesta
> se midió, no se opinó:
>
> - **14 son hecho puro** y estaban bien colocadas; **2 son mitad de un par**
>   (`estacionamientos`, `rubro_permitido`) con su gemela comercial ya
>   separada y del mismo alcance; **3 tenían un problema de modelo**
>   (`amoblado`, `cuota_mantenimiento`, `servicios_disponibles`).
>   **Ninguna era una condición disfrazada**: 0C, `V74` y `V77` ya habían
>   sacado todas las condiciones del sujeto PROPIEDAD.
> - Lo que sí apareció, y este documento no había mirado, es la **cobertura del
>   par**: un hecho y su condición pueden vivir en sujetos distintos y aun así
>   el hecho llegar **menos lejos**. Donde eso pasa, en ese tipo el pacto es la
>   única casilla donde cabe el hecho. La consulta sobre los ocho pares dio
>   **tres huecos** — `amoblado`/O, `cuota_mantenimiento`/A y /C — y `V78` los
>   cierra con tres filas OPC (cero valores afectados en las dos bases).
> - Las **conversiones de tipo siguen bloqueadas**, y ahora se sabe por qué
>   invariante: `tg_catalogo_sistema_inmutable` prohíbe cambiar el `tipo_dato`
>   de una clave del sistema. Eso afecta a `cuota_mantenimiento`→IMPORTE,
>   `rubro_permitido`→LISTA_MULTIPLE, `zonificacion`→LISTA y `banos`→ENTERO,
>   además de los motivos de dato que este documento ya medía.
> - **`servicios_disponibles` es un hecho de la PROPIEDAD y está bien
>   colocado**; lo que le falta es vocabulario. Declarada LISTA y sin una sola
>   opción sembrada, el trigger no valida nada y `MotorDeCaptura.controlDe` la
>   degrada a TEXTO. Es deuda de catálogo, no de sujeto, y se resuelve en el
>   corte que evolucione el catálogo — no antes, y sin inventarle un
>   vocabulario.
> - **Las ampliaciones de aplicabilidad por profundidad** que este corte
>   planificaba (`banos`→L,O,A · `zonificacion`→O · `pisos_edificacion`→D,O ·
>   `frente`→C · `interiorUnidad`/`nombreEdificioGaleria`→A) **no entraron en
>   `V78`**: no las justifica la pregunta del sujeto, y mezclarlas habría hecho
>   imposible decir qué corrigió qué. Siguen medidas e inertes aquí.
>
> Evidencia: `verificacion/evidencia/2026-08-22-corte-1-el-hecho-y-su-condicion.md`.

> **Preflight ejecutado el 2026-08-22.** Antes de escribir una línea de la
> migración se midieron todos los valores existentes de las 19 claves en las dos
> bases —`controllocal_dev` (26 propiedades, cartera de uso real) y
> `controllocal_repositorios` (1 915, la de los tests de integración)— y cada
> transformación se clasificó **reversible / reconciliable / bloqueada**. Lo que
> sigue ya no es un plan: es lo que el dato permite.
>
> El resultado corrige el encabezado que este corte tenía. **No es «filas
> gratis»**: cuatro de sus cambios están bloqueados por un trigger que el
> documento no mencionaba, dos más por el dato, y uno por una contradicción
> interna del propio plan.

#### Lo que V77 SÍ hace: ampliar aplicabilidad, y **como OPC**

Insertar filas en `catalogo_atributo_tipo` es lo único del corte que no toca ni
reinterpreta ningún valor: esa tabla no tiene trigger, y en los cinco casos **no
existe hoy un solo valor de esa clave en el tipo que se añade**.

| Cambio | Medido |
|---|---|
| **`banos` a L, O, A** | Sus 406 valores están en C y D. Cero en L, O y A. |
| **`cuota_mantenimiento` a C, A** | Sus 625 valores están en D, L y O. Cero en C y A. |
| **`zonificacion` a O** | Sus 584 valores están en A, C, L y T. Las 72 oficinas no tienen ninguno. |
| **`pisos_edificacion` a D, O** | Es la clave más estrecha del catálogo: hoy tiene **una sola fila**, C. Ningún departamento ni oficina tiene valor. |
| **`frente` a C** | Sus 475 valores están en A, L y T. Ninguna casa tiene. |
| **`interiorUnidad` / `nombreEdificioGaleria` habilitados para A** | **No son claves de catálogo**: son columnas estructurales y su restricción a L,O,D vive en `GuionRegistroPropiedad.ESTRUCTURALES_POR_TIPO`. Habilitarlas son dos `Set.of` — sin migración. Y el escritor ya las acepta sin mirar el tipo: los 83 almacenes de la base de pruebas **ya las tienen rellenas** aunque el guion no se las pregunte nunca. |

**«Como OPC» no es un detalle de redacción.** `exigencia` es `NOT NULL` sin
defecto: hay que elegirla en el mismo INSERT. Insertadas como OPC son inertes;
insertadas como PUB —que es lo que §3.1 declara como estado resultante— dejan
**139 casas y 83 almacenes sin poder publicarse de golpe**, ninguno con valor. Y
hay que escribir también `requerido`, que es su columna espejo y hoy es coherente
al 100 %.

**Dos de esas preguntas nacen sin su referente**, y conviene saberlo al
formularlas: la cuota de mantenimiento de una casa necesita `en_condominio` (§3.4
lo dice con estas palabras: «sin él, la cuota de mantenimiento de una casa no
tiene a qué referirse») y la de un almacén necesita `base_mantenimiento` (§4).
Las dos son claves **nuevas**, así que en V77 la pregunta existe y su referente
no. Es aceptable —un número sin referente sigue siendo mejor que ningún número—
pero se declara, no se descubre después.

#### El rótulo de `metraje_total`: el cambio que parecía gratis y no lo es

Renombrar a «Área techada» **no toca ninguna fila a nivel SQL, y reinterpreta
todas**. Tres cosas que la primera lectura no vio:

1. **El rótulo es la instrucción al agente sobre qué número teclear**
   (`propiedad-form.html`), y ese número va a `propiedad.metraje`, columna
   `NOT NULL` que desde V61 es **la autoridad única** —V61 borró la copia de
   `atributo_propiedad` y dejó una guarda que revienta si vuelve—. Todo lo
   capturado después de V77 entra bajo la convención nueva y **queda mezclado en
   la misma columna** con lo anterior, sin ninguna marca que los separe. Revertir
   el rótulo más tarde no re-anota esas filas: ahí está la irreversibilidad.
2. **KAIROS usa el rótulo como desambiguador.** `InterpreteDeterminista` arma su
   índice de palabras con el rótulo **vivo**, y su propio javadoc dice por qué no
   usa la unidad: «tres atributos comparten m², y "120 m²" no dice cuál de los
   tres es». Esos tres son `metraje_total`, `metraje_construido` y `area_terreno`
   — y el renombrado hace que el primero colisione justo con aquello de lo que
   tiene que distinguirse.
3. **`BandaDeMetraje`** trocea la cartera en bandas comerciales sobre
   `propiedad.metraje`, y los cortes se calibraron sobre «total».

El precedente que se citaba **no aplica**: V68 arregló tildes y V69 unidades;
ninguna cambió el *significado* de un rótulo. El precedente real apunta al revés
— V61, ante metrajes divergentes, **abortó** con «elegir cuál gana es una
decisión de negocio: resuélvelas antes de migrar».

**Sigue siendo reconciliable, y la reconciliación no inventa nada**: donde hay
`metraje_construido` el metraje se autodesambigua (es el total); donde no lo hay
—1 129 de 1 932 filas medidas— la convención es **desconocida y se declara así**,
no se asume techada. Pero hoy no existe ninguna columna que registre bajo qué
convención se capturó cada metraje, **así que esa marca viaja en V77 junto al
rótulo o el rótulo no viaja**. Con el rótulo solo, la mezcla es irrecuperable.

#### Lo que V77 NO hace, y dónde va

| Cambio | Por qué se detiene | Dónde entra |
|---|---|---|
| **`banos` DECIMAL → ENTERO + `medios_banos`** | `medios_banos` es una **clave nueva**, y este corte declara cero claves nuevas: es una contradicción interna. Además `tg_catalogo_sistema_inmutable` **prohíbe cambiar el `tipo_dato` de una clave del sistema** («los valores ya escritos dejarían de significar lo mismo»), y 379 de 406 valores son `.5`. | **Corte 3**, con el bloque de baños/servicio, donde `medios_banos` ya estaba previsto. |
| **`servicios_disponibles` retirada** | Sus reemplazos (`agua_desague`, `energia_electrica` y la semántica que se resuelva para `gas`) se cierran en el **Corte 5**. `gas` ya existe desde `V81`; no se puede tratar como clave nueva sin decidir cómo se conserva su vocabulario y sus valores. Retirarla ahora deja cuatro cortes en los que BROX **deja de capturar un hecho que hoy captura**. Y mecánicamente tampoco se puede: el trigger bloquea el DELETE de una clave del sistema — la única salida legal es `activo = false`. | **Corte 5**, en la misma tanda que deja operativos los reemplazos, migrando lo recuperable y declarando FALTANTE lo que no. |
| **`cuota_mantenimiento` → IMPORTE** | **625 filas, `valor_moneda` NULL en el 100 %.** Y no hay de dónde sacarla: `propiedad.moneda_referencial` es la moneda de una renta o de un precio de venta, no la de un gasto mensual de junta — el mismo importe 350 aparece bajo PEN (237 veces) y bajo USD (56), y 280 sólo bajo USD. La tercera fuente, el encargo vivo, tiene **74 casos con monedas en conflicto** y 115 sin encargo. Rellenarla sería inventar. | Cuando la moneda se **declare**. Hasta entonces el importe sigue viajando como número: retirar el número perdería el único dato que sí existe. |
| **`rubro_permitido` → LISTA_MULTIPLE** | Es la conversión con más **dato real de uso** detrás: 22 valores libres distintos tecleados en `dev` («Restaurante / cafetería», «Bar restaurante», «Bodega» vs «Depósito comercial» vs «Logística»…), varios **no mapeables con certeza** — decidirlos es normalizar por parecido. Y cambia el **almacén**, no sólo el vocabulario: LISTA_MULTIPLE exige que los valores vivan en `atributo_propiedad_opcion`, y las 488 filas actuales tienen `valor_texto`. | Cuando exista el vocabulario reconciliado **con su tabla de opciones sembrada** y la migración mueva los 488 valores. Los que no mapeen se declaran FALTANTES, no se aproximan. |
| **`zonificacion` → LISTA** | Los 584 valores mapearían al 100 % (`CZ`, `RDM`, `RDA`, `I2`), así que el dato viejo no es el problema: **el nuevo sí**. Un vocabulario derivado de lo observado tendría 4 opciones, y la zonificación real de Lima tiene decenas (CV, CM, CE, RDB, I1-I4, OU, ZRP, ZTE…). Con la lista cerrada a lo medido, el agente que está delante de un local en CV **no tiene dónde poner lo que ve**. | Cuando el vocabulario salga de **los planos de zonificación**, no de estas 584 filas. La conversión es fácil; la lista es el trabajo. |
| **`metraje_construido` fuera de D** | La premisa —«para un departamento nombra el mismo hecho que `metraje_total`»— **no se sostiene con este mismo documento**: §3.1 renombra `metraje_total` a «Área techada» en los **siete** tipos, así que si en D nombra lo mismo, también en A, C, L y O — y ahí el plan lo conserva y lo **sube** a PUB. No hay criterio que separe D. (El cruce de datos no decide nada: los 803 valores son cinco constantes de fixture repetidas.) | Cuando el plan diga qué hecho es cada uno **en cada tipo**. Retirarlo antes huérfana 267 filas sin reemplazo. |
| **Exigencia declarada de `interiorUnidad` / `nombreEdificioGaleria`** | «ALT en D» y «PUB en A» **no tienen dónde escribirse**: son estructurales, la `Pregunta` del guion no lleva campo de exigencia y las dos están en la lista de opcionales. | Cuando el guion sepa declarar exigencia sobre un campo estructural. |

#### Lo que hay que decidir a propósito antes de escribir la migración

1. **Quitar `aplica_todos` no basta, y por sí solo no hace nada.** V72 materializó las 7 filas de tipo de `antiguedad_anios` y `estacionamientos`, y tanto Java como el trigger evalúan `aplica_todos OR EXISTS(fila de tipo)`: bajar la bandera sin borrar la fila deja TERRENO preguntándolo igual. **Es un no-op.**
2. **Y al borrarlas quedan huérfanas 166 filas de cada una** (83 en T y 83 en X). No se borran solas: la ficha las sigue pintando pero **con la clave cruda** —`antiguedad_anios` sin unidad en vez de «Antigüedad»— y **cualquier re-guardado de esa propiedad revienta**. Hay que decidir si se borran, se archivan o se dejan.
3. **El tipo X (OTRO) se queda con una sola pregunta.** Hoy tiene exactamente tres claves aplicables y son las tres de `aplica_todos`. Quitando dos, a X le queda `metraje_total`. §7.8 dice «no abrir X, auditarlo antes de decidir si sigue existiendo» — y este corte lo reduce de refilón. Que sea una decisión, no un efecto colateral.
4. **Los flips a PUB son el cambio de mayor impacto operativo del corte, y no estaba cuantificado.** Hoy **ninguna** de las 19 claves del sistema tiene exigencia PUB. Medido lo que faltaría: `pisos_edificacion` en 1 048 de 1 048 departamentos y 72 de 72 oficinas; `banos` en 781 D, 407 L, 72 O, 83 A; `cuota_mantenimiento` en 806 D, 139 C, 97 L, 83 A. En `dev`, **prácticamente ningún local volvería a ser publicable**. Lo ya publicado no se retira —el gate es de entrada— pero republicar sí se bloquea. O se escalona, o se acepta y se dice.
5. **`requerido` es columna espejo de `exigencia`** y hoy son coherentes al 100 % (0 filas divergentes). V77 tiene que escribir las dos, o rompe la coherencia que V72 dejó establecida.
6. **El gate del Corte 0A cae con esto.** `ConservacionDeLaEdicionIntegrationTest` usa literalmente lo que este corte invalida: DEPARTAMENTO con `banos = 2.5`, cuotas sin moneda, TERRENO con `servicios_disponibles` y con antigüedad y estacionamientos, y el caso OTRO compuesto de las tres claves `aplica_todos`. Reescribir esos fixtures es parte del corte, y hay que reconocer que el gate deja de cubrir los siete tipos con los mismos datos con que se cerró 0A.

#### Qué tan verificado está esto

Cada medición pasó por un segundo agente con la carga de la prueba invertida
—«encuentra una fila, un valor o un consumidor que se pierda»—, y **tres
veredictos cambiaron la conclusión de partida**: el rótulo de `metraje_total`
dejó de ser el cambio inocuo (§ arriba), añadir `cuota_mantenimiento` a C y A
dejó de ser inerte (la exigencia hay que elegirla, y el referente no existe), y
la justificación de retirar `metraje_construido` de D resultó estar apoyada en un
fixture, no en la cartera. Ocho verificaciones no llegaron a correr; las que
quedaron sin segundo par de ojos son las de menor riesgo —`frente`,
`pisos_edificacion`, `interiorUnidad`, `nombreEdificioGaleria`, `zonificacion` a
O— y su clasificación descansa en **una sola medición**. Se dice para que quien
ejecute V77 sepa cuáles volver a mirar.

#### El gate de V77

No basta con comprobar filas del catálogo. **Una corrección del catálogo que no llegue idéntica a los consumidores no está cerrada.** El gate recorre los **siete tipos** y demuestra, para cada uno:

- **qué pregunta el alta** y **qué exige** para poder registrar (ALT);
- **qué exige para publicar** (PUB) y qué mensaje da cuando falta;
- **qué dejó de preguntar**, y que lo que dejó de preguntarse **sigue siendo legible** donde ya estaba escrito;
- que un valor existente sobrevive a **`GET` → `PUT` de otra cosa → `GET`** — el gate de conservación del 0A, ahora con el catálogo nuevo;
- que **BROX Web y KAIROS reciben la misma definición del Core**: mismo guion, mismos rótulos, misma exigencia, mismo vocabulario. Es la frontera D-A-1 y el principio del North Star de que los dos consumen el mismo núcleo.

#### La instrucción, para quien entre a V77

> Antes de modificar el catálogo, **mide** todos los valores existentes de las 19
> claves y clasifica cada transformación como reversible, reconciliable o
> bloqueada. **No inventes moneda, no normalices texto por aproximación, no
> retires un dato antes de que exista su reemplazo y no introduzcas claves nuevas
> en este corte.** El objetivo de V77 es mejorar la semántica de las 19 claves
> existentes **conservando íntegramente el conocimiento ya acumulado**. Después
> verifica los siete tipos, alta/edición/publicación, Web y KAIROS, y cierre
> completo.

La medición ya está hecha y es lo que hay arriba; lo que queda es volver a
correrla contra la cartera del momento —habrá crecido— y comprobar que ninguna
clasificación empeoró.

#### Valor

Sigue siendo de los mejores coste/valor del plan, pero por lo que de verdad
hace: **cinco tipos ganan preguntas que hoy no tienen** —baños en local, oficina
y almacén; mantenimiento en casa y almacén; zonificación en oficina; pisos de
edificación en departamento y oficina; frente en casa— y el almacén pasa a poder
identificarse por módulo y por nombre de parque. Sin una sola clave nueva y **sin
perder un solo dato**.

Lo que **no** entrega, y conviene no prometerlo: el metraje no deja de ser
ambiguo por renombrarlo. Deja de serlo el día que cada fila diga bajo qué
convención se capturó — y ese día empieza en V77 sólo si la marca viaja con el
rótulo.

---

### **Corte 2 — Identidad registral** · §3.2 — ✅ **EJECUTADO 2026-08-23 · `V79`**

> **Se adelantó al resto del Corte 1**, y la razón es medida: la identidad
> registral es un **hueco estructural demostrado** —la partida existía en un
> único sitio de toda la base, `condicion_compraventa.partida_registral`, con
> **0 filas**— y se puede modelar sin inferir nada. El resto del Corte 1 pide
> exigencias y vocabularios, y su evidencia sale de `controllocal_repositorios`,
> que **es `TEST_DB_URL`**: infraestructura sintética de integración, no corpus
> de mercado. Ver §6 bis.

- **Migración V79**: `propiedad.partida_registral`, `propiedad.oficina_registral` (destino ESTRUCTURAL, gracias a C-8) + las claves `independizado`, `cargas_gravamenes`, `area_segun_partida`, `declaratoria_fabrica`. `condicion_compraventa.partida_registral` pasa a ser lo que debió ser siempre: **la partida vigente en esa venta, copia fechada de la del activo**, no su único domicilio.
- **Las seis entraron `OPC`, ninguna `PUB`**, y eso corrige lo que decía la tabla de §3.2. La razón está en §6 bis: `PUB` **bloquea publicar** con un 400, así que estrenarlo aquí habría dejado sin poder anunciarse a las 26 propiedades reales de `controllocal_dev` —las 26 pasan hoy el gate— y habría tumbado dos de las cinco suites del cierre. La promoción es una línea de SQL el día que el negocio la decida.
- **Prueba**: `AutoridadDelDatoIntegrationTest` (la cadena estructural entera: escritor, vaciado, lector, conservación y edición explícita) y `CatalogoQueHablaIntegrationTest` (vocabulario de la oficina por las dos puertas, multivalor de cargas, ausencia ≠ respuesta, y que publicar sigue funcionando). Más `CadenaEstructuralCompletaTest`, el gate genérico que rompe el build si un concepto estructural se puede escribir y no leer.
- **Lo que NO entró**: la E2E del *snapshot* A→B de compraventa. `condicion_compraventa.partida_registral` tiene 0 filas y su escritor nace con el expediente de compraventa (bloque 6); V79 sólo deja escrito, en el comentario de la columna y aquí, que dejó de ser la autoridad. Simularlo con SQL para tener una prueba verde habría sido probar la simulación.
- **Valor**: el broker verifica titular y cargas **antes** de firmar el encargo, no al cerrar — y también en un encargo de **alquiler**, donde antes la partida no tenía dónde vivir.

---

### **Corte 3 — Vivienda: D y C** · §3.3, §3.6, §3.4 (parte) — ✅ **EJECUTADO 2026-08-24 · `V80`**
Siembra + opciones + E2E de alta/edición/publicación por tipo. Aquí entran `tipologia`, `estado_conservacion`, `ascensores`, `vigilancia`, `areas_comunes`, `vista`, el bloque de baños/servicio y las áreas exteriores.

- **Dos commits.** `3.a` **sin migración**: arregla el censo `M2` de
  `verificacion/gate-modelo-universal.sql` —`count(*) = 25` sobre el catálogo del
  sistema, **rojo desde `V77`**, superviviente de tres cortes cerrados porque
  `Verificar-Cierre.ps1` no lo ejecutaba— y mete el gate en la corrida de cierre.
  `3.b` = **`V80`**: **30 claves**, 9 vocabularios con 49 opciones, 68 filas de
  aplicabilidad.
- **Las 30 entraron `OPC`, ninguna `ALT`, ninguna `PUB`**, y eso corrige la
  columna «nivel» de §3.3, §3.4 y §3.6, que era una **propuesta y no un estado**.
  Misma razón que en el Corte 2 (§6 bis): `PUB` **bloquea publicar** con un 400.
  El catálogo del sistema sigue con **cero `PUB`**, y se comprueba en la propia
  migración. *(Cierto al aplicarse `V80`, el 2026-08-24. **`V82`, del mismo día,
  subió `tipo_acceso` a `PUB` en `L`**: desde entonces el catálogo tiene una.
  Anotado el 2026-08-25; el párrafo se conserva porque describe lo que `V80`
  encontró y comprobó.)*
- **`mascotas_reglamento` entra en `C` y `D`, no sólo en `D`** — **corrección de
  §3.6 por medición**. Su condición `mascotas_aceptadas` se pacta en
  `catalogo_atributo_operacion` como `C/A/OPC` y `D/A/OPC`, y el guard 2.2 de
  `V78` exige que el hecho no llegue menos lejos que su condición. Si hubiera
  nacido sólo en `D`, `V80` habría fallado en su propia guarda — se comprobó
  simulándolo. **Manda la medición: se corrige el documento, nunca el código.**
- **`torre_bloque` se ejecuta en este corte, no en el 5** — **anotación sobre
  §3.8**. Está redactada dentro de *Terreno y parámetros urbanísticos* por
  arrastre de redacción, pero su `aplica_a` es `D` y su justificación es de
  vivienda. Un corte se define **por tipo**, no por número de sección.
- **Angular no se tocó.** Las 30 claves se pintan solas por `cl-campo-gobernado`,
  y que aparezcan sin tocar el SPA es una **prueba** del corte.
- **Evidencia**: `verificacion/evidencia/2026-08-24-el-censo-que-se-rompia-al-avanzar.md`
  y `verificacion/evidencia/2026-08-24-corte-3-vivienda.md`.

**Heredado del Corte 1, y ejecutado a medias a propósito:** `medios_banos` **nace
aquí**, y con él la **convención de `banos` queda publicada en su `ayuda`** — un
baño completo cuenta 1, un medio baño (sin ducha) cuenta 0.5. Era la precondición
que faltaba: la migración de datos es determinista (los 379 valores fraccionarios
medidos son `.5` exactos, sin un solo caso raro) pero descansaba en una
**convención no publicada**, y hay que escribirla antes de aplicarla, no después.
**El estrechamiento a ENTERO sigue sin hacerse**: `tg_catalogo_sistema_inmutable`
lo prohíbe por diseño y exige clave nueva + migración de datos + retirada de la
vieja. Es un corte propio.

### **Corte 4 — Comercial: L, O, A** · §3.7, §3.5 — ✅ **EJECUTADO 2026-08-24 · `V81`**
`tipo_acceso` (el único ALT nuevo de L), `clase_edificio`, `nivel_implementacion`, `metraje_arrendable`, `aforo_itse`, `certificado_itse`, el bloque logístico completo y las instalaciones.

- **39 claves**, 18 vocabularios con 83 opciones, 71 filas de aplicabilidad
  (A 28 · L 16 · O 19 · C 2 · D 3 · T 3). `orden` 560…940.
- **`tipo_acceso` entró `ALT` en `L` y `V82` lo corrigió a `PUB` el mismo día** — ver la nota al final de este bloque. Fue la única exigencia no-OPC del corte. Decisión del
  titular con el efecto medido: **`ALT` impide publicar igual que `PUB`**
  (`clavesQueImpidenPublicar` filtra `exigencia in ('ALT','PUB')`), así que al
  aplicar `V81` **las 26 propiedades publicables pasaron a 5** y los 21 locales
  quedaron fuera del mercado hasta que se visiten. Es el resultado esperado. Las
  otras 38 entraron `OPC`, y **las catorce `PUB` que propone esta auditoría
  siguen siendo propuesta**. ~~El catálogo del sistema conserva **cero
  `PUB`**.~~ **FALSO desde `V82`**, del mismo 2026-08-24, que es precisamente el
  cambio que este párrafo describe dos líneas más arriba: `tipo_acceso` **es
  `PUB` en `L`**, y es la única. Corregido el **2026-08-25**; medido contra
  `controllocal_dev`: `catalogo_atributo_tipo` tiene **una** fila con
  `exigencia = 'PUB'`.
- ~~**A diferencia de una `PUB`, esta `ALT` informa**: `atributosQueFaltan` se
  alimenta de `exigencia = 'ALT'`, y la ficha de cada local bloqueado dice que le
  falta «Tipo de acceso». El bloqueo viaja con la instrucción de cómo
  quitarlo.~~ **FALSO, y lo era ya al escribirse.** `V82` sacó `tipo_acceso` de
  `ALT`, y lo que `V82` midió es lo contrario: al pasar a `PUB` la clave
  **desapareció de `atributosQueFaltan`** —que sólo lleva `ALT`— y la ficha dejó
  de decir que faltaba «Tipo de acceso». Ese fue el hueco que la señal `PUB`
  visible vino a tapar el mismo día (`35cf09c`): hoy la clave se reporta, pero en
  `faltanParaPublicar`, **no** en `atributosQueFaltan`, y no por ser `ALT` sino
  por ser `PUB`. Corregido el **2026-08-25**.
- **Este corte termina también las instalaciones de la vivienda** (`gas` en
  A,C,D,L,O,T y `agua_caliente` en C,D): §3.5 mezcla tipos, el Corte 3 las
  excluyó y su encargo está congelado. Se acepta como consecuencia y se escribe,
  en vez de reabrirlo. Igual con `acceso_vehiculo_maximo` y `via_de_acceso`, que
  alcanzan a T: **un corte se define por el tipo que lo motiva**, no por lo que
  arrastre su aplicabilidad. Los parámetros urbanísticos siguen en el Corte 5.
- **`certificado_itse` NO retira `apto_licencia_funcionamiento`.** Conviven: el
  North Star prohíbe retirar una captura antes de que su reemplazo exista **y esté
  poblado**, y la retirada exige migración de datos. Es un corte propio.
- **Códigos `UPPER_SNAKE` que empiezan por letra** — de ahí `H24_7` y no `24_7`,
  con rótulo «24/7». La migración lo comprueba con un regex sobre las 83 opciones.
- **`area_minima_arrendable` recuperó el acento de `m²`** (D-BASE-4), olvido de
  `V77`. Era la última clave del catálogo con `unidad = 'm2'`: ahora quedan cero.
- **Evidencia**: `verificacion/evidencia/2026-08-24-corte-4-comercial.md`.

### **Corte 5 — Terreno y ocupación transversal** · §3.8

> **🟡 EN CURSO. La subtanda 5A está APLICADA (`V84`, 2026-08-25)**: nacen
> `estado_ocupacion` (OPC en los siete), `agua_desague` y `energia_electrica`
> (PUB en `T`), `gas` gana `CON_FACTIBILIDAD_APROBADA` sin cambiar de concepto y
> `servicios_disponibles` queda `activo = false` conservando sus valores. Lo que
> sigue abajo es **el plan del corte**, y la parte de 5A ya no es futuro: se lee
> como lo que se hizo. **5B no está abierta** (D-4). Evidencia:
> `verificacion/evidencia/2026-08-25-corte-5a-ocupacion-y-servicios.md`.

Parámetros urbanísticos, servicios con su tercer estado, vía y ocupación. Cierra
la duplicidad `metraje_total`/`area_terreno` para T. `estado_ocupacion` no queda
limitado a T y C: su condición `entrega_desocupado` se pacta en los siete tipos,
por lo que el hecho debe cubrir A, C, D, L, O, T y X.

**Hereda del Corte 1:** aquí nacen `agua_desague` y `energia_electrica`, y se
resuelve la semántica de reemplazo de `servicios_disponibles` junto con `gas`,
que ya existe desde `V81`. **Sólo entonces** `servicios_disponibles` pasa a
`activo = false` (no se borra: el trigger del catálogo del sistema no lo permite,
y además borrarla perdería lo escrito). La migración reparte el literal de cada
fila entre los conceptos vigentes cuando sea recuperable y declara FALTANTE lo
que no lo sea. El orden importa: primero existen los reemplazos, después se
retira el reemplazado.

### **Corte 6 — Unidades relacionadas** · §5
Una unidad con partida propia **es una Propiedad relacionada**, con identidad, titularidad, partida, histórico y eventualmente encargo propios — no un escalar dentro de un EAV. Migración con `unidad_relacionada`, códigos `'E'` y `'B'`, `unidadesRelacionadas[]` en el DTO, paso en el guion, y la resignificación deliberada de `numeroEstacionamientos` en `FronteraDeAutoridadEnElSpaTest`.

### **Corte 7 — Demanda y matcher**
Unificar el vocabulario de tipo (hoy la demanda usa `LOCAL_COMERCIAL, OFICINA, DEPOSITO_ALMACEN, STAND_MODULO, TERRENO_COMERCIAL, OTRO` y `mapTipo` sólo traduce tres, con lo que ALMACÉN y DEPOSITO_ALMACEN —el mismo concepto— se declaran no comparables y el criterio pasa a NO_APLICA). Permitir que un requerimiento pida atributos gobernados. Y **arreglar el sesgo**: hoy un dato faltante hace que el criterio NO APLIQUE sin castigar el puntaje, así que **la propiedad peor capturada obtiene mejor puntaje** al reducir el denominador.

---

### La cadena de migraciones, de un vistazo

**Lo que se planificó** (se deja como se escribió, para que se vea cuánto se
desvió la ejecución de la previsión):

```
V70  ✅ APLICADA   la publicacion pertenece al encargo
V71     0A         retirar detalle_local_comercial (puerta unica de edicion)
V72     0B         capacidades del catalogo: opciones, multivalor, fecha, importe, exigencia
V73     0C         sujeto del dato: atributo_encargo
V74     1          las 19 claves que ya existen, bien declaradas
V75     2          identidad registral
```

**Lo que realmente se aplicó** (actualizado 2026-08-25). Cada corte costó más de una
migración, y aparecieron dos que el plan no tenía:

```
V70  ✅  la publicacion pertenece al encargo
V71  ✅  0A  retirar detalle_local_comercial
V72  ✅  0B  el catalogo aprende a hablar
V73  ✅  0C  el sujeto del dato
V74  ✅  0C  las primeras condiciones del encargo (6, para probar el mecanismo)
V75  ✅      convergencia: una propiedad puede no estar encargada
V76  ✅  0D  la propiedad como activo de dato       <- no estaba en el plan
V77  ✅  0E  el lenguaje completo del ENCARGO (26)  <- no estaba en el plan
V78  ✅  1   el hecho llega donde llega su condicion (mitad de SUJETO)
V79  ✅  2   la identidad registral de la propiedad  <- se adelanto al resto del 1
V80  ✅  3   la vivienda descrita de verdad (30 claves D y C)
V81  ✅  4   el activo comercial descrito (39 claves L, O, A + gas y agua caliente)
V82  ✅  4   tipo_acceso impide publicar, no registrar (ALT -> PUB)
V83  ✅  4.P procedencia granular del dato gobernado
          1   mitad de PROFUNDIDAD  ⬜  <- sigue APLAZADA, sin migracion asignada
V84  🟡  5A  la ocupacion y los servicios con vocabulario  <- aplicada; 5B sin abrir
```

> **El Corte 4 estrena la primera `ALT` de campo del sistema.** Las diez que
> había (`metraje_total`, `dormitorios`, `zonificacion`) se responden desde el
> escritorio; `tipo_acceso` exige estar de pie en el local. Su precio, medido y
> aceptado antes de decidirlo: **21 de 26 propiedades dejan de ser publicables**
> hasta que alguien las visite.

> **Y `V82` la corrigió el mismo día, sin tocar esa consecuencia.** El cierre del
> Corte 4 midió que `ALT` **no sólo impide publicar: impide registrar**
> (`Exigencia.bloqueaAlta()`). Eso chocaba con `V75`/`V76` —registrar no es
> encargar, y BROX conoce inmuebles que no gestiona—, así que el titular bajó la
> clave a **`PUB`**, el nivel que `V72` ya tenía previsto. **La publicabilidad no
> se movió: siguen 5 de 26 y las mismas 21 bloqueadas.** Lo que se recuperó es
> poder **registrar** un local sin haberlo visitado.

> **El Corte 3 costó dos commits y una sola migración.** El primero (`3.a`) no
> toca el esquema: arregla el gate `.sql` que llevaba rojo desde `V77` y lo mete
> en la corrida de cierre. Que hiciera falta un commit entero para eso es el dato
> que importa — **el gate sobrevivió tres cortes cerrados y auditados sin que
> nadie lo ejecutara**, y `V80` habría cerrado en verde sobre un gate roto.

---

## 6 bis. Dos cosas que este plan daba por ciertas y no lo eran

**Medidas el 2026-08-23, antes de escribir `V79`.** No se corrigen los apartados
de arriba uno a uno —se dejan como se escribieron— pero nada de lo que sigue se
puede volver a suponer.

### `PUB` **sí** bloquea publicar, y con un 400

La columna «nivel» de §3 reparte `PUB` con generosidad porque se leyó como un
aviso. No lo es:

| Hecho | Dónde |
|---|---|
| `exigirPublicable(...)` termina en `throw new ReglaNegocioException("Todavia no se puede publicar: …")` | `PublicacionServiceImpl.java:186-214` |
| Se alcanza al crear el anuncio de un encargo **y** al pasarlo a `PUBLICADO` | `PublicacionServiceImpl.java:94` y `:326` |
| La lista que lo alimenta filtra por ALT **y** PUB | `AtributosGobernados.faltantesDePropiedadParaPublicar` |
| `ReglaNegocioException` → **HTTP 400** | `ManejadorErroresApi.java:45` |

~~Y hay una segunda mitad que importa igual: **no existe ninguna superficie del
cable que reporte una PUB de la PROPIEDAD**. `PropiedadResponse.atributosQueFaltan`
lleva sólo ALT; `EncargoFicha.faltanParaPublicar` lleva ALT+PUB pero sólo del
ENCARGO. Hoy, marcar PUB una clave de la propiedad hace **exactamente una cosa**:
rechazar la publicación.~~

> **CADUCADO el 2026-08-24 a las 16:51**, en el commit `35cf09c`, y anotado aquí
> el **2026-08-25**. Esa segunda mitad **era cierta cuando se escribió y dejó de
> serlo el mismo día**: `PropiedadResponse` ganó su propio
> `faltanParaPublicar`, alimentado por
> `AtributoPropiedadRepository.clavesQueImpidenPublicar`, que filtra
> `exigencia in ('ALT','PUB')` **sobre las aplicaciones por tipo del sujeto
> PROPIEDAD**; Angular lo pinta en `propiedad-detail.html`, y
> `CatalogoQueHablaIntegrationTest` lo fija exigiendo que `zonificacion` aparezca
> ahí. **Hoy, marcar PUB una clave de la propiedad hace dos cosas: rechaza la
> publicación y lo dice.** Evidencia:
> `verificacion/evidencia/2026-08-24-senal-pub-visible.md`.
>
> Lo que **no** ha cambiado, y es lo que sigue gobernando: `atributosQueFaltan`
> sigue llevando **sólo ALT**, así que una `PUB` **no** aparece ahí. Las dos
> listas no se funden.

**Consecuencia práctica para los cortes 3 a 7:** cada `PUB` de las tablas de §3
es una decisión que retira del mercado a toda propiedad que no tenga ese dato.
Se toma por corte, con su medición, o no se toma.

### Los números de impacto de §1 salen de la base de pruebas

`controllocal_repositorios` **es `TEST_DB_URL`**: la base que las 20 suites de
integración usan y en la que cometen. El 2026-08-22 tenía **2 871 propiedades**
y **802 claves de catálogo, de las cuales 757 son `zz_*`** sembradas por los
propios tests. Sus cifras describen el residuo de las corridas, no el mercado.

El **corpus real** es `controllocal_dev`: **26 propiedades** (1 C, 1 D, 21 L,
2 O, 1 T), 45 claves del sistema y 74 valores escritos. Cualquier frase de este
documento del tipo «406 baños» o «1 048 departamentos» hay que leerla contra
esa distinción antes de usarla para decidir una exigencia.

## 7. Lo que NO haría ahora

1. **No sembrar ninguna clave antes del Corte 0B**, ni tocar el editor antes de tener el gate de conservación del **0A**. Una LISTA sin tabla de opciones obliga a Angular a inventar el vocabulario y rompe D-A-1; y ampliar lo que se puede editar antes de garantizar que editar no destruye es ampliar la superficie de la pérdida. Por eso este plan tiene dos cortes técnicos delante, y en ese orden.
2. **No añadir `disponible_desde` como TEXTO «provisional».** Es la propuesta que aparece en cuatro auditorías y es un parche: no filtraría «disponible este mes» ni ordenaría. El tipo FECHA cuesta una columna.
3. **No crear un tipo de propiedad «cochera» antes de la relación genérica.** Sin `unidad_relacionada`, una cochera dada de alta suelta queda huérfana en la cartera — peor que el contador actual.
4. **No tocar el matcher hasta el Corte 7.** Cambiar el puntaje mientras la mitad de la cartera está sin datos produce rankings que nadie puede interpretar. Primero el dato, después el criterio.
5. **No rellenar retroactivamente lo que hoy está vacío.** Las cuatro propiedades existentes se quedan con sus atributos FALTANTES. Inferir `VIVIENDA` de que el tipo sea D, o `ENTREGA_INMEDIATA` porque es lo frecuente, es exactamente lo que la ayuda de `uso` («si no se declara, se deduce del tipo») hace mal hoy.
6. **No estrechar `piso` a numérico ni añadir `piso_numero`.** `TEXTO` está ahí porque L y O admiten «Sótano» y «Mezzanine». Que «del piso 5 hacia arriba» no se resuelva en SQL para D es real, pero se arregla estrechando el tipo **por tipo de propiedad** o separando la clave — decisión de modelo, no un atributo más. Un `piso_numero` al lado repetiría el defecto que V67 acaba de cerrar.
7. **No convertir `metraje_total` en dos claves por tipo.** Se resuelve con rótulo + ayuda («área techada») y con `area_terreno` obligatoria en C y PUB en T. Partir la clave rompería `campo_estructural=METRAJE`, que es de los pocos sitios donde la autoridad ya está limpia.
8. **No abrir el tipo `X` (OTRO).** No hay auditoría de ese tipo — llegaron seis, no siete — y hoy `metraje_total` es su único requerido. Auditarlo antes de decidir si sigue existiendo.
9. **No añadir defectos de campo en las entidades.** Ningún `DEFAULT` en las columnas nuevas más allá de lo estructural; el defecto legítimo se escribe donde se usa y con su porqué.
10. **No abrir el PDF ni la publicación en portales.** Fuera de alcance por D-F5-1, y el Corte 6 aún no habría cerrado el dato que un aviso necesita.

---

**Ficheros que este plan toca (referencia para quien lo ejecute):**
`D:/init/ControlLocal/backend-spring/controllocal-app/src/main/resources/db/migration/` (desde V71) · `.../controllocal-service/src/main/java/com/controllocal/service/soporte/AtributosGobernados.java` · `.../service/captura/GuionRegistroPropiedad.java` · `.../service/impl/MotorDeCapturaImpl.java` · `.../service/impl/PropiedadUniversalServiceImpl.java` · `.../service/impl/LocalComercialServiceImpl.java` (se retira) · `.../service/soporte/CoincidenciaCartera.java` · `.../controllocal-web/src/main/java/com/controllocal/web/dto/PropiedadUniversalDtos.java` · `.../web/controlador/PropiedadesUniversalesController.java` · `.../controllocal-domain/src/main/java/com/controllocal/domain/inmueble/CatalogoAtributo.java` · `.../controllocal-app/src/test/java/com/controllocal/arquitectura/FronteraDeAutoridadEnElSpaTest.java` · `D:/init/ControlLocal/frontend-angular/src/app/features/local-form/` (se retira) · `.../src/app/core/api/codigos.ts` · `.../src/app/features/propiedad-detail/` · `D:/init/ControlLocal/docs/ai/matriz-operacion-rol.md`
