# Auditoría de profundidad inmobiliaria — 2026-08-20

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
| C-6 | **Tres niveles de exigencia, no dos** | `catalogo_atributo_tipo.requerido` (booleano) sólo sabe "bloquea el alta". Sustituir por `exigencia VARCHAR(3)` CHECK `('ALT','PUB','OPC')`: **ALT** bloquea el alta, **PUB** bloquea publicar/`disponibilidad_comercial='D'`, **OPC** no bloquea. Sin este campo, toda la columna «nivel» de este plan no tiene dónde vivir. |
| C-7 | Sujeto del atributo | `catalogo_atributo.sujeto VARCHAR(10)` CHECK `('PROPIEDAD','ENCARGO')`, default PROPIEDAD. Reutiliza opciones, rangos, DTO y motor de captura para el encargo (ver §3). |
| C-8 | Campos estructurales nuevos | Ensanchar `ck_catalogo_campo_estructural` de `(METRAJE, PISO)` a `(METRAJE, PISO, PARTIDA, OFICINA_REGISTRAL, INTERIOR, EDIFICIO)`. La partida **no** puede ser un ATRIBUTO: meter la identidad registral en `atributo_propiedad.valor_texto` es exactamente la degradación que la invariante prohíbe. |
| C-9 | El contrato tiene que publicarlo | `PreguntaCatalogoResponse` (`PropiedadesUniversalesController:164`) devuelve seis campos y ninguno es `opciones`. Añadir `opciones[{valor,rotulo}]`, `valorMinimo`, `valorMaximo`, `exigencia`, `ayuda`. Y `/captura/apertura` debe publicar las opciones **con rótulo**, no como lista de cadenas: hoy el SPA se inventa el texto en tres sitios distintos (`Local` / `Local comercial` / `LOCAL`). |

---

## 3. Lista consolidada de atributos — una clave, muchos tipos

Notación: **aplica_a** y **requerido_para** con los códigos de una letra. Nivel: **ALT** = bloquea el alta · **PUB** = bloquea publicar · **OPC** = recomendado. Autoridad **P** = propiedad, **E** = encargo (§4).

### 3.1 Correcciones sobre las 19 claves que YA existen (cero claves nuevas)

Esto es lo más barato del plan: filas en `catalogo_atributo_tipo`, cambios de `tipo_dato` y de rótulo.

| clave | qué cambia | aplica_a resultante | exigencia |
|---|---|---|---|
| `metraje_total` | Rótulo → **«Área techada»** + ayuda que fija la convención (área techada, la de la partida; la terraza NO se suma). Es la peor grieta de comparabilidad: un flat de 90 m² techados con 30 de terraza lo carga un agente como 120 y otro como 90, y el precio por m² deja de ser comparable entre dos fichas del propio BROX. | L,O,D,C,T,A,X | ALT (todos, ya) |
| `metraje_construido` | **Retirar de D** (para un departamento nombra el mismo hecho que `metraje_total`: dos claves, una verdad). Subir en A y C. | A,C,L,O | **PUB** en A, C |
| `area_terreno` | Subir en C y T. Una casa se tasa por el PAR (terreno, construida). | A,C,T | **ALT** en C · **PUB** en T |
| `antiguedad_anios` | **Quitar `aplica_todos`**. Un terreno eriazo no tiene antigüedad; preguntarlo hace dudar de si el sistema entiende lo que se registra. | L,O,D,C,A | PUB en C, L, O |
| `estacionamientos` | **Quitar `aplica_todos`** (fuera de T). Valor mínimo 0 ya está bien: 0 = no tiene, nulo = no se sabe. | L,O,D,C,A | PUB en C, L, O, D |
| `banos` | **Añadir L, O, A** (hoy sólo C,D: un local se registra sin decir si tiene SS.HH., y sin ellos no hay ITSE ni licencia). Estrechar **DECIMAL → ENTERO** y sacar el medio baño a `medios_banos`: hoy `2.5` es una convención no publicada en ninguna parte y un agente escribe 2.5 donde otro escribe 3. | L,O,D,C,A | **PUB** en L,O,D,C,A |
| `cuota_mantenimiento` | **Añadir C y A** (condominios y parques logísticos). Cambiar a **IMPORTE** (con moneda). | D,L,O,C,A | PUB |
| `zonificacion` | **Añadir O** (una oficina en casa de zona residencial no obtiene licencia; es la omisión más cara del tipo). **TEXTO → LISTA** con el vocabulario de los planos de Lima. Hoy `C-2`, `c2` y `Comercio Zonal` son tres valores distintos y no agrupan nada. | A,C,L,T,O | ALT en T (ya) · **PUB** en L,O,A |
| `rubro_permitido` | **TEXTO → LISTA_MULTIPLE** cerrada. Y **una sola autoridad**: el matcher lo lee hoy de `detalle_local_comercial`, tabla que el alta universal no escribe nunca (§5, corte 1). | A,L,O | PUB |
| `pisos_edificacion` | **Añadir D y O.** «Piso 12 de 20» y «piso 12 de 12» son productos distintos. | C,D,O | PUB en D,O |
| `frente` | **Añadir C.** Una casa que se vende por su terreno se cotiza por frente. | A,L,T,C | PUB en T · OPC en C |
| `altura_libre` | Subir en A: dos naves de 1 000 m², una de 4 m y otra de 11 m, no son el mismo activo. | A,L | **PUB** en A |
| `piso` | Subir en D y O: sin piso, «Av. Larco 1234» y «Javier Prado 476» nombran un edificio, no una unidad. Rótulo → **«Piso de ingreso»** (en un dúplex nadie sabe hoy si escribir 8 o «8-9»). | D,L,O | **ALT** en D,O |
| `interiorUnidad` *(estructural)* | **Habilitar para A** (el stock logístico moderno se identifica por módulo). Exigir en D. | D,L,O,A | **ALT** en D · PUB en A |
| `nombreEdificioGaleria` *(estructural)* | **Habilitar para A** (condominio o parque logístico: es como se busca el activo y hoy acaba dentro de `direccion`). | D,L,O,A | PUB en A |
| `servicios_disponibles` | **Retirar.** Para un terreno el estado no es sí/no: se sustituye por `agua_desague`, `energia_electrica` y `gas`, cada uno con su tercer estado «con factibilidad aprobada», que es justo el que decide si el lote es desarrollable. | — | — |
| `apto_licencia_funcionamiento` | Se conserva como declaración, pero deja de estar solo: se acompaña de `certificado_itse`. | A,L,O | OPC |
| `dormitorios` | Sin cambios (ALT en C,D, correcto). | C,D | ALT |
| `amoblado` | Sin cambios como **hecho físico**. Su mitad comercial sale a `se_ofrece_amoblado` (§4). | C,D | OPC |
| `ambientes` | Sin cambios. Para oficina no describe nada (cuenta divisiones sin decir para qué sirven): se complementa con `salas_reunion`. | A,C,D,L,O | OPC |

### 3.2 Identidad y situación registral (autoridad PROPIEDAD, destino ESTRUCTURAL)

| clave | rótulo | tipo | opciones | aplica_a | requerido | nivel |
|---|---|---|---|---|---|---|
| `partida_registral` | Partida registral | TEXTO | — | L,O,D,C,T,A | — | **PUB** en todos |
| `oficina_registral` | Oficina registral | LISTA | LIMA, CALLAO, HUAURA, CANETE, HUARAL, BARRANCA | L,O,D,C,T,A | — | PUB (el número de partida se repite entre oficinas) |
| `area_segun_partida` | Área según partida | DECIMAL m² | — | C,T,A | — | OPC (permite avisar de la discrepancia antes de pactar precio) |
| `independizado` | Unidad independizada | BOOLEANO | — | D,O,L,A | — | **PUB** (sin independizar no hay crédito hipotecario ni contrato inscribible) |
| `cargas_gravamenes` | Cargas y gravámenes | LISTA_MULTIPLE | NINGUNA, HIPOTECA, EMBARGO, SERVIDUMBRE, COPROPIEDAD_SIN_DIVIDIR, SUCESION_PENDIENTE, LITIGIO | L,O,D,C,T,A | — | PUB |
| `declaratoria_fabrica` | Fábrica declarada e inscrita | BOOLEANO | — | C,D | — | **PUB** en C (el tercer piso sin declarar es el problema nº 1 de la casa limeña: el banco no financia) |

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
| `mascotas_reglamento` | El reglamento permite mascotas | BOOLEANO | — | D | OPC. Hecho del edificio, no del encargo (su gemelo comercial está en §4) |

### 3.7 Comercial y logístico

| clave | rótulo | tipo | opciones | aplica_a | nivel |
|---|---|---|---|---|---|
| `tipo_acceso` | Tipo de acceso | LISTA | A_PIE_DE_CALLE, ESQUINA_A_CALLE, GALERIA_INTERIOR, PASAJE_COMERCIAL, CENTRO_COMERCIAL, INTERIOR_DE_EDIFICIO, MERCADO | L | **ALT**. Único obligatorio nuevo de L: sin él, 40 m² a S/ 3 000 son caros a pie de calle en Miraflores y absurdos en el interior de Mesa Redonda. El agente está delante del local |
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
| `condicion_terreno` | Condición del terreno | LISTA | URBANO_HABILITADO, EN_PROCESO_DE_HABILITACION, RUSTICO_ERIAZO, ZONA_INFORMAL_SIN_HABILITAR | T | **ALT**. 500 m² habilitados en Surco y 500 m² rústicos en Pachacámac son hoy la misma fila; el agente lo sabe en la puerta |
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
| `estado_ocupacion` | Estado de ocupación | LISTA | LIBRE_Y_DESOCUPADO, CON_EDIFICACION_A_DEMOLER, OCUPADO_POR_TERCEROS, EN_USO_POR_EL_PROPIETARIO | T,C | **PUB** en T |
| `edificacion_existente` | Edificación existente | DECIMAL m² | — | T | OPC (declarar 0 no es lo mismo que no saberlo) |
| `cercado` | Cercado o amurallado | BOOLEANO | — | T | OPC |
| `restriccion_arqueologica` | Restricción arqueológica (CIRA) | LISTA | NO_APLICA, CIRA_OBTENIDO, EN_TRAMITE, REQUERIDO_NO_INICIADO | T | OPC (no se infiere del distrito: depende del polígono) |
| `zona_de_riesgo` | Zona de riesgo declarada | BOOLEANO | — | T,C | OPC |
| `manzana_lote` | Manzana y lote | TEXTO (ESTRUCTURAL → `interiorUnidad`) | — | T | **PUB**. Hoy se escribe dentro de `direccion` y dos agentes captan el mismo terreno con dos direcciones distintas |
| `latitud` / `longitud` *(ya existen, estructurales)* | — | — | — | T | **PUB** en T: un terreno suele no tener dirección útil |
| `torre_bloque` | Torre o bloque | TEXTO | — | D | OPC (el 501 existe en la Torre A y en la B; la mayoría del stock limeño no tiene torres, por eso no bloquea) |

**Totales:** ~20 correcciones sin clave nueva, **~85 claves nuevas de PROPIEDAD**, de las cuales **4 en ALT** (`tipo_acceso` en L, `condicion_terreno` en T, `metraje_construido` en A vía §3.1, `area_terreno` en C vía §3.1) más las tres elevaciones de claves existentes (`piso` en D/O, `interiorUnidad` en D). El resto reparte entre PUB y OPC.

---

## 4. Lo que es del ENCARGO y no de la propiedad

Hoy el encargo es `captacion` (con `motivo_operacion` A/V y una `condicion_economica_captacion` que sólo modela importe, moneda y **comisión** — `tratamiento_igv` ahí se refiere a la comisión, no a la renta). Nada de lo de abajo cabe en ninguna parte.

**Forma propuesta**, reutilizando el catálogo:
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

### **Corte 1 — Filas gratis: las 19 claves que ya existen** · el mejor coste/valor del plan

- **Migración V74**: todo el §3.1. Filas en `catalogo_atributo_tipo` (`banos` en L,O,A; `cuota_mantenimiento` en C,A; `zonificacion` en O; `pisos_edificacion` en D,O; `frente` en C), flips de exigencia, cambios de `tipo_dato` (`zonificacion` y `rubro_permitido` a LISTA/LISTA_MULTIPLE con sus opciones, `cuota_mantenimiento` a IMPORTE, `banos` a ENTERO), quitar `aplica_todos` de `antiguedad_anios` y `estacionamientos`, rótulo de `metraje_total`, habilitar `interiorUnidad`/`nombreEdificioGaleria` para A, retirar `servicios_disponibles`, retirar `metraje_construido` de D.
- **Código**: `GuionRegistroPropiedad` deja de restringir `interiorUnidad`/`nombreEdificioGaleria` a L,O,D. Migrar el dato existente de `banos` DECIMAL a `banos` ENTERO + `medios_banos` **sin inferir**: `2.5` → 2 completos + 1 medio es la única lectura documentada, y lo que no encaje se declara FALTANTE.
- **Prueba**: E2E que comprueba que un TERRENO ya no pregunta antigüedad ni estacionamientos, y que un LOCAL pregunta baños.
- **Valor**: sin una sola clave nueva desaparecen dos preguntas sin sentido, la zonificación pasa a filtrable, el mantenimiento deja de ser un número sin moneda y el local deja de registrarse sin SS.HH.

---

### **Corte 2 — Identidad registral** · §3.2

- **Migración V75**: `propiedad.partida_registral`, `propiedad.oficina_registral` (destino ESTRUCTURAL, gracias a C-8) + las claves `independizado`, `cargas_gravamenes`, `area_segun_partida`, `declaratoria_fabrica`. `condicion_compraventa.partida_registral` pasa a ser lo que debió ser siempre: **la partida vigente en esa venta, copia fechada de la del activo**, no su único domicilio.
- **Prueba**: E2E que verifica que una captación de **alquiler** puede registrar la partida (hoy es imposible) y que la solicitud de venta la hereda.
- **Valor**: el broker verifica titular y cargas en SUNARP **antes** de firmar el encargo, no al cerrar.

---

### **Corte 3 — Vivienda: D y C** · §3.3, §3.6, §3.4 (parte)
Siembra + opciones + E2E de alta/edición/publicación por tipo. Aquí entran `tipologia`, `estado_conservacion`, `ascensores`, `vigilancia`, `areas_comunes`, `vista`, el bloque de baños/servicio y las áreas exteriores.

### **Corte 4 — Comercial: L, O, A** · §3.7, §3.5
`tipo_acceso` (el único ALT nuevo de L), `clase_edificio`, `nivel_implementacion`, `metraje_arrendable`, `aforo_itse`, `certificado_itse`, el bloque logístico completo y las instalaciones.

### **Corte 5 — Terreno: T** · §3.8
Parámetros urbanísticos, servicios con su tercer estado, vía y ocupación. Cierra la duplicidad `metraje_total`/`area_terreno` para T.

### **Corte 6 — Unidades relacionadas** · §5
Una unidad con partida propia **es una Propiedad relacionada**, con identidad, titularidad, partida, histórico y eventualmente encargo propios — no un escalar dentro de un EAV. Migración con `unidad_relacionada`, códigos `'E'` y `'B'`, `unidadesRelacionadas[]` en el DTO, paso en el guion, y la resignificación deliberada de `numeroEstacionamientos` en `FronteraDeAutoridadEnElSpaTest`.

### **Corte 7 — Demanda y matcher**
Unificar el vocabulario de tipo (hoy la demanda usa `LOCAL_COMERCIAL, OFICINA, DEPOSITO_ALMACEN, STAND_MODULO, TERRENO_COMERCIAL, OTRO` y `mapTipo` sólo traduce tres, con lo que ALMACÉN y DEPOSITO_ALMACEN —el mismo concepto— se declaran no comparables y el criterio pasa a NO_APLICA). Permitir que un requerimiento pida atributos gobernados. Y **arreglar el sesgo**: hoy un dato faltante hace que el criterio NO APLIQUE sin castigar el puntaje, así que **la propiedad peor capturada obtiene mejor puntaje** al reducir el denominador.

---

### La cadena de migraciones, de un vistazo

```
V70  ✅ APLICADA   la publicacion pertenece al encargo
V71     0A         retirar detalle_local_comercial (puerta unica de edicion)
V72     0B         capacidades del catalogo: opciones, multivalor, fecha, importe, exigencia
V73     0C         sujeto del dato: atributo_encargo
V74     1          las 19 claves que ya existen, bien declaradas
V75     2          identidad registral
```

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
