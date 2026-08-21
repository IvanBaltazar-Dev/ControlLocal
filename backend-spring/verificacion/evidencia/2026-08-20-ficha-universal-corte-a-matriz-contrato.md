# Corte A · La matriz «dato requerido → campo del read model»

**Fecha:** 2026-08-20
**Qué mide:** si `GET /propiedades/{id}` (`FichaPropiedadUniversal`) basta para
construir la ficha universal **sin que Angular deduzca, traduzca ni reconstruya
nada**.
**Fuentes leídas:** `PropiedadUniversalService.java`,
`PropiedadUniversalServiceImpl.java` (`ficha()`, `fichaDeEncargo()`),
`PropiedadUniversalDtos.java`, `PropiedadesUniversalesController.java`,
`CaptacionRepository.encargosVivosDe`, `AtributosGobernados`,
`OperacionInmobiliaria`, `EstadosDominio`.

**Veredicto: el núcleo físico está completo; el comercial llega a medias y la
actividad no existe.** No se toca Angular hasta cerrar los huecos de abajo.

---

## 1. La cosa física

| Dato requerido | Campo del read model | |
|---|---|---|
| Código | `codigo` | OK |
| Descripción | `descripcion` | OK |
| Dirección, distrito, zona, piso, interior, edificio, coordenadas | `ubicacion.*` | OK |
| Metraje | `atributos[]` → `metraje_total` | OK |
| Características | `atributos[]` con `clave`, `rotulo`, `tipoDato`, `unidad`, `valor` | OK |
| Titulares | `titulares[]` con `nombre`, `cuota`, `representante`, `desde` | OK |
| Tipo — **valor** | `tipoPropiedad` | **HUECO** |
| Tipo — **rótulo** | — | **HUECO** |
| Uso — rótulo | `uso` (código `C`/`V`/`I`/`M`) | **HUECO** |
| Estado de registro — rótulo | `estadoRegistro` (código) | **HUECO** |
| Disponibilidad — rótulo | `disponibilidadComercial` (código) | **HUECO** |
| Lo que falta para publicar | `atributosQueFaltan` (claves) | **HUECO** |

### Sobre `metraje_total`: **no hay duplicado, y conviene dejarlo escrito**

`FichaPropiedadUniversal` **no tiene** campo `metraje`. El metraje viaja una sola
vez, entre `atributos[]`, con la clave lógica `metraje_total` — que es
exactamente lo que D-E4-3 prometió: la autoridad física se movió a un campo
canónico del agregado y el contrato lógico no. `ficha()` lo relee con
`EscritorEstructural.leer` y lo devuelve entre los atributos, con su rótulo.

Quien sí lleva un `metraje` suelto es `FilaPropiedad` — **otro DTO, el del
listado**, donde existe para ordenar y filtrar en SQL. No se cruzan.

Conclusión: **la ficha no necesita ninguna excepción del tipo
`if (atributo.clave !== 'metraje_total')`**. Si alguna vez aparece una, es la
señal de que alguien añadió el campo suelto a la ficha.

### El hueco del tipo, que es el más grave de este bloque

`ficha()` devuelve `propiedad.getTipoInmueble()` **en crudo**: `"L"`. El listado,
en cambio, devuelve las dos formas —`AtributosGobernados.nombreDelTipo` → `LOCAL`
y `rotuloDelTipo` → «Local comercial»—. Es decir: **dos lecturas del mismo
concepto publican tres representaciones distintas**, y la ficha publica
justamente la de almacenamiento.

Con eso, pintar «Local comercial» en la ficha sólo se puede hacer traduciendo en
Angular, que es la matriz «tipo → texto» que `FronteraDeAutoridadEnElSpaTest`
rompe el build por tener. El rótulo ya está escrito en Core; sólo hay que
publicarlo.

---

## 2. La gestión comercial, encargo a encargo

| Dato requerido | Campo del read model | |
|---|---|---|
| Identidad del encargo | `idEncargo` | OK |
| Código | `codigo` | OK |
| Operación — valor | `operacion` (`VENTA` / `ALQUILER`) | OK |
| Importe y moneda | `importe`, `moneda` | OK |
| Vigencia | `inicio`, `fin` | OK |
| Histórico económico propio | `historico[]`, filtrado por operación | OK |
| Estado — valor | `estado` | OK |
| Operación — rótulo | — | **HUECO** |
| **Rótulo del importe** | — | **HUECO** |
| Estado — rótulo | — | **HUECO** |
| Agente responsable | — | **HUECO** |
| Exclusividad | `EncargoFicha.exclusividad` | **HUECO en el DTO** |
| **Encargos cerrados** | — | **HUECO** |

### El rótulo del importe

`OperacionInmobiliaria.nombreDelImporte()` **ya existe** y devuelve «precio de
venta» / «renta mensual». `rotuloDeLaCondicion()` también. Ninguno de los dos
viaja por el cable. Sin ellos, la única forma de que la ficha no llame «renta» a
un precio de venta es un ternario en Angular sobre `operacion === 'VENTA'` — y
eso es semántica inmobiliaria en el SPA.

### La exclusividad se calcula, se devuelve, y se pierde en la última capa

`EncargoFicha` la lleva (con un comentario que cuenta que ya se perdió una vez y
un formulario la borraba al guardar). `EncargoResponse.desde()` **no la copia**.
Sale del servicio y no llega al cliente.

### El hueco que rompe el caso 4: la ficha sólo ve los encargos VIVOS

`ficha()` llama a `captaciones.encargosVivosDe(...)`, cuyo `where` es
`estado in ('P','O','A')`. Los cerrados (`C`), rechazados (`R`) y vencidos (`V`)
**no aparecen** — y con ellos desaparece su histórico económico completo.

Para un **listado** eso es correcto: la cartera enseña lo que está vivo. Para una
**ficha** no lo es: una propiedad con

```
alquiler 2024 — cerrado
alquiler 2025 — cerrado
alquiler 2026 — vigente
```

hoy enseñaría **un** bloque y dos series económicas se perderían de la vista sin
decir que existen. La ficha tiene que traer los encargos **todos**, marcando
cuáles siguen vivos.

Esto además es lo que hace verificable la regla de identidad: **el bloque es
`idEncargo`, nunca `operacion`**. Con sólo un encargo de venta y otro de
alquiler, un `groupBy(operacion)` incorrecto pasa las pruebas. Con tres encargos
de alquiler, no.

---

## 3. La actividad

| Dato requerido | Campo del read model | |
|---|---|---|
| Oportunidades | — | **NO EXISTE** |
| Visitas | — | **NO EXISTE** |
| Interacciones | — | **NO EXISTE** |
| Expedientes | — | **NO EXISTE** |
| Contratos | — | **NO EXISTE** |

`FichaPropiedadUniversal` no tiene actividad, y **no hay ningún endpoint que la
devuelva por propiedad**. Lo que hay son cinco listados que filtran cada uno por
su clave:

| Recurso | Filtro disponible | Distancia a la propiedad |
|---|---|---|
| `GET /oportunidades` | `idCaptacion` | 1 salto |
| `GET /solicitudes` | `idCaptacion` | 1 salto |
| `GET /interacciones` | `contexto=CAPTACION` + `idCaptacion` | 1 salto |
| `GET /visitas` | `idOportunidad` | 2 saltos — **una llamada por oportunidad** |
| `GET /contratos` | ni `idCaptacion` ni `idPropiedad` | **inalcanzable** |

Resolverlo desde el SPA obligaría a: una llamada por encargo × tres recursos,
más una por oportunidad para las visitas, más un barrido de contratos filtrado a
mano. Y sobre todo obligaría a escribir en Angular la regla «la actividad de una
propiedad es la unión de la de sus encargos», que es dominio.

**Conclusión: la actividad se cierra en BROX Core**, con la procedencia
(`idEncargo`) puesta por el productor en cada elemento.

---

## 4. Lo que decide este corte

| # | Hueco | Cierre |
|---|---|---|
| 1 | `tipoPropiedad` publica el código de almacenamiento | publicar el **nombre** (`LOCAL`) y añadir `tipoRotulo` |
| 2 | `uso`, `estadoRegistro`, `disponibilidadComercial` sin rótulo | publicar su rótulo junto al código |
| 3 | `atributosQueFaltan` son claves desnudas | publicar clave **y** rótulo |
| 4 | el encargo no dice cómo se llama su importe | publicar `importeRotulo` desde `nombreDelImporte()` |
| 5 | el encargo no dice su operación ni su estado en palabras | publicar `operacionRotulo` y `estadoRotulo` |
| 6 | el encargo no dice quién lo lleva | publicar `agenteNombre` (+ `idAgente`) |
| 7 | la exclusividad se pierde en el DTO | copiarla en `EncargoResponse` |
| 8 | la ficha esconde los encargos cerrados | traerlos todos, con `vivo` marcado |
| 9 | no hay actividad | `actividad` en la ficha, con `idEncargo` en cada elemento |

Los nueve son de BROX Core. Ninguno se arregla en Angular, y ocho de los nueve
consisten en **publicar algo que el dominio ya sabe decir** y hoy se queda
dentro.

---

## 5. Las dos fichas que ya existen, y cuál es la canónica

| Pantalla | Ruta | Alcance real |
|---|---|---|
| `features/local-detail` | `/propiedades/:id` | la **propiedad**, leída por `GET /locales/{id}` (modelo heredado: un propietario, un precio, ninguna operación) |
| `features/ficha-propiedad` | `/captaciones/:codigo/ficha` | **un encargo**: galería, condiciones pactadas, responsables |

En el modelo universal un encargo **es** una captación, así que
`ficha-propiedad` no es otra ficha de la propiedad: es la ficha de **uno de sus
encargos** — es decir, exactamente el bloque 2 de la ficha universal, aislado y
sin la propiedad alrededor.

**Canónica: la ficha de la propiedad.** `local-detail` se sustituye por
`propiedad-detail` (el nombre que ya fijó `auditoria-residuos-semanticos.md`
§4.3), y `ficha-propiedad` queda como pantalla de un encargo hasta que su única
pieza propia —la galería de fotos, que en la base es **de la propiedad**
(`foto_propiedad`) y no del encargo— se absorba. **No se crea una tercera
implementación de detalle.**

## 6. Huérfanos, demostrados y no supuestos

```
features/locales/       0 rutas · 0 imports · 0 referencias externas  → se borra
features/local-detail/  1 ruta (/propiedades/:id) · 0 imports          → lo sustituye propiedad-detail
```

`local-detail` y `LocalDetail` aparecen en otros ficheros **sólo como cadena del
cable** (`'local-detail': (id) => ['/propiedades', id]` en `cliente-detail` y
`propietario-detail`, que traducen la `ruta` que emite el backend) y en
comentarios. No son importaciones y no se rompen al sustituir la pantalla.

---

# Cortes B–E · Lo que se hizo, y lo que quedó fuera

## Corte B — el read model universal, cerrado

Los nueve huecos del §4, cerrados en BROX Core. Ocho consistían en **publicar
algo que el dominio ya sabía decir** y se quedaba dentro:

| Hueco | Cierre | Dónde |
|---|---|---|
| 1 | `tipoPropiedad` ahora es el **nombre** (`LOCAL`) + `tipoRotulo` | `ficha()` |
| 2 | `usoRotulo`, `estadoRegistroRotulo`, `disponibilidadRotulo` | `AtributosGobernados.rotuloDelUso` + `descripcion()` de los enums |
| 3 | `atributosQueFaltan` pasa de `List<String>` a `{clave, rotulo}` | `ficha()` |
| 4 | `importeRotulo` — «precio de venta» / «renta mensual» | `OperacionInmobiliaria.nombreDelImporte()`, que ya existía |
| 5 | `operacionRotulo` y `estadoRotulo` del encargo | `fichaDeEncargo()` |
| 6 | `idAgente` + `agenteNombre` del encargo | `fichaDeEncargo()` |
| 7 | `exclusividad` deja de perderse en el DTO | `EncargoResponse.desde` |
| 8 | la ficha trae **todos** los encargos, con `vivo` marcado | `CaptacionRepository.encargosDe` + `Captacion.esVivo` |
| 9 | `actividad` con `idEncargo` en cada hecho | `ActividadDeLaPropiedad` (nuevo) |

Y uno más que apareció al pintar: **`hitoRotulo`** («Autorizado», «Publicado»),
porque el histórico enseñaba la letra.

**Dos correcciones de fondo, no cosméticas:**

- **El histórico se filtra por ENCARGO, no por operación.** Filtrar por
  operación funciona mientras haya un encargo de cada; con tres alquileres
  sucesivos le daría al de 2026 los precios de 2024. `precio_propiedad`
  ya guardaba `id_captacion`; no se estaba usando.
- **La ficha ya no llama a `encargosVivosDe`.** Un encargo cerrado es el único
  sitio donde vive su serie económica.

**La actividad se resuelve por los ids de los encargos ya leídos**, no por la
propiedad: así la procedencia sale de la consulta en vez de reconstruirse
después. Cinco consultas, todas `id in (...)`, ninguna recortada.

## Corte D — las pruebas que rompen un diseño malo

**Backend** (`PropiedadUniversalIntegrationTest`, PostgreSQL real): **43/43**.
Seis nuevas, y la que importa es `variosEncargosHistoricosDeLaMismaOperacion`:
tres alquileres sucesivos → tres bloques, tres históricos, y ninguno ve la cifra
de otro. Venta + alquiler **no** detecta un `groupBy(operacion)`; esto sí.

**Frontend** (`propiedad-detail.spec.ts`): **662/662** en toda la suite. Los
cuatro fixtures pedidos —un encargo, venta + alquiler (PROP-0022), copropiedad
con cuotas y representante, y tres encargos históricos de la misma operación—
más: el metraje una sola vez, ningún precio en el bloque de la propiedad, la
procedencia por hecho, el filtro por encargo, y «—» donde falta un dato en vez
de un cero.

## Corte C — la ruta

```
/propiedades/:id  →  features/propiedad-detail  →  GET /propiedades/{id}
```

`GET /locales/{id}` ya no participa: `propiedad-detail` no inyecta
`LocalesService` y hay un spec que lo afirma.

`features/propiedad-detail/` son **dos** componentes, no uno grande:
`propiedad-detail` (las tres zonas) y `bloque-encargo` (un encargo). El bloque
es uno solo — el mismo pinta el vigente y el cerrado— para que las dos copias no
empiecen a separarse en el primer cambio.

## Corte E — el legado, borrado con prueba

```
features/locales/       0 rutas · 0 imports · 0 referencias  → BORRADO
```

Las menciones restantes a «Locales» en el SPA son el **tipo inmobiliario**
legítimo (menú, rótulos de propietarios, títulos), no el sinónimo de propiedad.

### Lo que NO se cerró, y por qué

**`features/local-detail/` sigue en el árbol, sin ruta.** No se borra todavía, y
la razón no es prudencia: es que dentro vive `editor-publicacion`, **la única
pantalla del SPA que gestiona publicaciones** — crear, editar, publicar, pausar
y cerrar un anuncio. Borrar la carpeta borraría esa capacidad sin que nadie lo
pidiera.

Al reapuntar `/propiedades/:id` a la ficha universal, **esa pantalla ha quedado
sin entrada de menú**. Es un efecto real de esta tanda y se anota aquí en vez de
dejarlo pasar.

No se resuelve trayéndola tal cual a la ficha nueva, porque cuelga de
`GET /locales/{id}/publicaciones` y eso volvería a meter el modelo heredado en
la ruta canónica — justo el criterio de fracaso que esta tanda tenía que evitar.

**Cerrarlo es su propia tanda**, y tiene una pregunta de modelo delante: una
publicación anuncia un precio y una operación, así que **pertenece al encargo, no
a la propiedad** (`PrecioPropiedad.HITO_PUBLICADO` ya lo dice: «la renta que el
mercado ve, la escribe la publicación»). Mientras la publicación siga colgando
de la propiedad en la v1, portarla es mover el problema de sitio.

Secuencia propuesta: publicación → encargo en el modelo → subrecurso de
`/propiedades/{id}/encargos/{idEncargo}/publicaciones` → sección en la ficha →
**entonces** borrar `features/local-detail/`.

---

# Los dos niveles de lectura: encargo e inmueble

Un añadido posterior al Corte B, y no cosmético — es una distinción de
arquitectura:

```
idEncargo    la identidad técnica de UN episodio comercial
idPropiedad  la continuidad histórica del inmueble
```

Los bloques de encargo sirven para **auditar y negociar**: este importe, este
agente, esta serie, este expediente. Pero no contestan las preguntas que hacen
que esto sea memoria inmobiliaria y no un CRM de operaciones vivas:

> ¿A cuánto se alquiló la última vez? · ¿En cuánto se intentó vender en 2023? ·
> ¿Cuántas veces estuvo en alquiler? · ¿Cuál fue el último precio de cierre?

`FichaPropiedadUniversal.historia` las responde, y **sin fusionar nada**:

| | |
|---|---|
| `porOperacion[]` | cuántas veces, desde cuándo, si sigue vigente, último pedido y último cierre |
| `linea[]` | todos los movimientos del inmueble en orden, atravesando encargos |

**Cada cifra arrastra su `idEncargo` y su `codigoEncargo`**, incluso dentro del
dato agregado. De «la última renta fueron 2 400» siempre se puede volver al
episodio que lo dice. Esa es la diferencia entre agregar para leer y mezclar.

Se calcula **sin una sola consulta más**: es otra lectura de lo que `ficha()` ya
había leído.

### Lo pedido y lo cerrado son dos números, y no se sustituyen

`ultimoPedido` sale de los hitos `U` (autorizado) y `P` (publicado).
`ultimoCierre` sale sólo de `C`. Cuando no hay cierre, el campo llega **`null` y
la ficha escribe «Sin cierre registrado»** — nunca el precio pedido.

Ese respaldo, que parece amable, convierte «lo que pedíamos» en «lo que vale», y
es exactamente la cifra que después se cita en una negociación.

**En datos reales se ve por qué importa.** `LOC-0001` tiene:

```
E esperado   PEN 8 500   2025-02-01
C cerrado    PEN 5 480   2026-08-09
```

La ficha dice «Último pedido: —» y «Último cierre: PEN 5 480». No toma el 8 500
como precio pedido —`E` es lo que el propietario **esperaba**, no lo que se
autorizó a pedir— ni lo toma del importe del encargo. El 8 500 no se pierde: está
en «Todos los movimientos», rotulado «Esperado», que es lo que de verdad es.

### Pruebas

Cuatro backend (`laHistoriaCuentaLosEpisodiosSinFusionarlos`,
`elCierreNoSeConfundeConLoPedido`, `laHistoriaSeparaLasDosOperaciones`,
`sinHistoriaNoHayNull`) → **47/47** en `PropiedadUniversalIntegrationTest`,
**231/231** en el reactor completo.

Cuatro frontend, incluida «los dos niveles conviven: la historia no reemplaza a
los bloques de encargo» → **660/660**.

---

# Lo que enseñó pintarla contra datos reales

Cuatro defectos que sólo aparecen cuando el dato sale a pantalla. Los cuatro,
corregidos:

| Se veía | Era | Dónde se arregló |
|---|---|---|
| `450 moneda` | `cuota_mantenimiento.unidad = 'moneda'`, que no es una unidad sino un importe cuya moneda depende de la propiedad | **V69**, en el catálogo |
| `120 m2` | el metro cuadrado se escribe `m²`, y arreglarlo en el cliente sería otra tabla de traducción | **V69**, en el catálogo |
| `APTO PARA LICENCIA: true` | un `BOOLEANO` pintado con el valor del cable | la ficha, leyendo `tipoDato` — representar la respuesta, no decidir la pregunta |
| `Ofrece PEN 5480.00` | el importe iba **concatenado dentro de la frase** en el backend, así que no había forma de agrupar millares | el hecho lleva ahora `monto` y `moneda` como **valores**; la pantalla los formatea |

Y uno de contenido: con **todos** los encargos cerrados, «Gestión comercial»
quedaba con el título y nada debajo —se lee como un fallo de carga—. Ahora lo
dice: «Ningún encargo vigente: hoy no está ni en venta ni en alquiler».

---

# La publicación pertenece al ENCARGO (V70)

La deuda que quedó abierta arriba está cerrada. **No como un cambio de URL: como
la relación real del modelo.**

```
Propiedad → Encargo → Publicación
```

## Lo que la medición encontró antes de tocar código

| | |
|---|---|
| `publicacion.id_captacion` | **no existía**: la publicación colgaba sólo de `id_propiedad` |
| `renta_publicada` | el importe se llamaba «renta» — falso en cuanto se publica una venta, y el nombre viajaba hasta la pantalla |
| el hito `P` del histórico | se escribía **suponiendo `ALQUILER`** y **sin encargo** |
| consumidores del endpoint heredado | **no era huérfano**: `oportunidad-form` lo usaba |
| alcance por tenant | `findById` desnudo en `actualizar` y `cambiarEstado`; las consultas por inmueble no filtraban por organización |

**El código ya había anunciado este cambio.** En `PublicacionServiceImpl` estaba
escrito, junto al `setOperacion(ALQUILER)` fijo:

> «la publicacion de una venta llegara con el encargo de venta y su propio
> importe, y entonces esta linea dejara de ser una constante»

### El defecto que ese hito huérfano causaba, y que nadie veía

Los hitos `P` nacían sin `id_captacion`. La ficha filtra el histórico **por
encargo**, así que **existían en la base y no aparecían en ninguna parte** — ni
en el histórico del encargo ni en la historia comercial. Verificado antes de
migrar: `select hito, count(id_captacion) …` daba `P → 0 de 2`.

## Lo que se hizo

**V70** — `id_captacion` (nullable), `renta_publicada` → `importe_publicado`,
índice por encargo, y **dos backfills demostrables**: sólo se rellena cuando el
encargo candidato es **único**; con cero o con varios se deja `NULL`, porque
elegir sería inventar de cuál era. Se aplicó y resolvió el encargo de todas las
publicaciones existentes.

**El API canónico**, `EncargosController`:

```
GET    /encargos/{idEncargo}/publicaciones
POST   /encargos/{idEncargo}/publicaciones
PUT    /encargos/{idEncargo}/publicaciones/{idPublicacion}
POST   /encargos/{idEncargo}/publicaciones/{idPublicacion}/estado
```

Reutiliza los casos de uso: lo que cambia es por dónde se entra y qué se
comprueba al entrar — que el encargo sea del tenant (404 si no) y que **esté
vigente** para poder publicar. *No se publica lo que ya no se ofrece*, y esa
regla vive en el servicio, no en el botón.

**Los cuatro endpoints heredados se retiraron**, no se dejaron como
compatibilidad: `oportunidad-form` —el último consumidor— pregunta ahora por su
encargo, que además es lo que de verdad quería (el anuncio del que salió una
oportunidad es el de **su** encargo, no cualquiera de la propiedad).

**El editor salió de `features/local-detail/`** a `shared/publicaciones/`, y con
`local-detail` sin ninguna pieza propia, **se borró entero**.

**En la ficha, la entrada vive dentro de cada bloque de encargo.** No hay un
«Publicar propiedad» global: con venta y alquiler a la vez no diría qué se
publica. Y si se puede o no lo dice el Core en `publicacionGestionable`
`{permitida, motivo}` — la pantalla no escribe `estado === 'A'`.

## Verificado contra datos reales, no sólo en verde

En `/propiedades/3259` (PROP-0022, venta ENC-0014 + alquiler ENC-0015), se
publicó la **venta** a USD 315 000 desde la pantalla:

```
Venta     ENC-0014   URBANIA USD 315,000 Publicada
                     histórico: Autorizado 320,000 · Publicado 315,000
Alquiler  ENC-0015   sin anuncios
                     histórico: Autorizado 4,800
```

El anuncio y su hito cayeron **sólo** en la venta, con operación VENTA. Antes de
V70 ese hito habría entrado como ALQUILER y huérfano: habría ensuciado la serie
del alquiler y no se habría visto.

## Un defecto global que apareció al medir la pantalla

`styles.scss` tenía, sin acotar:

```scss
@media (max-width: 860px) { .fecha { display: none; } }
```

Es la fecha de la cabecera del Inicio, pero `.fecha` es un nombre demasiado
natural: **escondía la fecha de cada hito del histórico y de cada hecho de la
actividad, en toda la aplicación**, por debajo de 860px. Acotada a
`.lienzo .fecha`. Se detectó midiendo la geometría del DOM, no leyendo el texto:
el `getBoundingClientRect` de la fecha daba ancho 0.

## Cierre

```
723 + 43 + 241 pruebas backend · 0 fallos · 0 SKIPPED
653 pruebas Angular · 0 fallos
```

Incluye cinco pruebas de integración nuevas —venta y alquiler publican por
separado, dos alquileres sucesivos conservan cada uno sus anuncios, el encargo
cerrado no se publica, la capacidad la decide el Core, un encargo ajeno es 404— y
un gate nuevo, `FichaUniversalNoVuelveAlModeloViejoTest`, que rompe el build si
la ficha vuelve a `/locales/{id}`, importa `local-detail`, deja de pintar
`tipoRotulo` o agrupa encargos y anuncios por operación en vez de por
`idEncargo`.

> **El gate se estrenó cazándose a sí mismo**: su primera versión marcaba como
> infracción el `track episodio.operacion` de la historia comercial —donde
> agrupar por operación es exactamente lo correcto— y una mención a
> `local-detail` **en prosa**. Las dos reglas se afinaron; la lección quedó
> escrita en el propio test.
