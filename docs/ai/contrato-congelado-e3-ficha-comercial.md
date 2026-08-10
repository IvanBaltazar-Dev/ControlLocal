# Contrato congelado E3 — ficha comercial

> **El "congelado" del título es histórico.** El contrato se descongeló el
> 2026-08-09 (`decision-contrato-v2-descongelado.md`): DTOs, endpoints, estados y
> errores pueden cambiar con razón funcional y con sus pruebas.
>
> Este documento **describe el comportamiento vigente** y se sigue actualizando
> —no es historia—, pero la autoridad son **las pruebas y OpenAPI**, no este
> texto. Si discrepan, manda la suite.

**Estado:** CONGELADO, CORTADO Y VERIFICADO (2026-07-29)  
**Fuente de verdad:** `backend-java/controllocal-rest/.../ClientesRest.java`,
`PropietariosRest.java` y `FichaComercialSupport.java`.

## 1. Alcance del corte

E3 porta los cuatro GET que completan la paridad de `/clientes` y
`/propietarios`:

| Metodo | Ruta |
|---|---|
| GET | `/clientes/{id}/ficha-comercial` |
| GET | `/clientes/{id}/ficha-comercial/{section}` |
| GET | `/propietarios/{id}/ficha-comercial` |
| GET | `/propietarios/{id}/ficha-comercial/{section}` |

No crea tablas ni necesita una migracion Flyway: agrega una lectura
transversal sobre V4, V5, V7 y V8.

## 2. Parametros y paginacion

- La ficha completa acepta `page_size` y `tamano`; `page_size` gana.
- Una seccion acepta `page`/`pagina` y `page_size`/`tamano`; los nombres en
  ingles ganan.
- Pagina por defecto: `1`.
- Tamano por defecto: `8`.
- Pagina menor que uno se normaliza a `1`.
- Tamano efectivo: entre `1` y `8`, aunque el parametro indique mas.
- La paginacion se aplica despues de ordenar todas las filas visibles.

## 3. Secciones

Cliente, en este orden:

1. `requerimientos`
2. `propiedades`
3. `oportunidades`
4. `interacciones`
5. `visitas`
6. `solicitudes`
7. `cierres`
8. `agentes`

Propietario, en este orden:

1. `locales`
2. `prospecciones`
3. `captaciones`
4. `oportunidades`
5. `solicitudes`
6. `cierres`
7. `agentes`

Una seccion desconocida responde 400 con uno de estos textos exactos:

- `Seccion de ficha de cliente no valida.`
- `Seccion de ficha de propietario no valida.`

## 4. Carga inicial parcial

La ficha de cliente trae la primera pagina de `requerimientos`. Las otras
secciones son marcadores pendientes:

```json
{"section":"propiedades","totalRecords":-1,"page":0,"pageSize":8,"items":[]}
```

La ficha de propietario trae la primera pagina de `locales`; para
`prospecciones` y `captaciones` calcula el total pero deja `items` vacio. Las
otras secciones usan el marcador pendiente anterior.

La cabecera de propietario conserva la rareza legacy: `cantidadLocales` es
`0` dentro de esta respuesta, aunque la seccion `locales` tenga registros.

## 5. Respuestas

### ClienteFichaResponse

```text
cliente, requerimientoActivo, ctaRuta, sections
```

- `requerimientoActivo` es verdadero si existe al menos un requerimiento
  `ACTIVO`.
- `ctaRuta` es `/oportunidad-form?clienteId={id}` solo para AGENTE con
  requerimiento activo; en cualquier otro caso es cadena vacia.

### PropietarioFichaResponse

```text
propietario, sections
```

### FichaSectionResponse

```text
section, totalRecords, page, pageSize, items
```

### FichaRowResponse

```text
id, codigo, proceso, titulo, subtitulo, local, distrito,
cliente, clienteId, propietario, propietarioId, agente,
estado, fecha, ruta, icono, tono, fechaOrden
```

Los nulos descriptivos se normalizan normalmente a `"-"`; una ruta ausente
es `""`. `fechaOrden` se conserva en JSON aunque su finalidad principal sea
ordenar.

## 6. Orden, deduplicacion y privacidad

- Orden: `fechaOrden DESC`, luego `proceso`, luego `codigo`.
- `propiedades`, `locales` y `agentes` deduplican por id y conservan el primer
  hito encontrado por el orden de armado legacy.
- ADMIN ve toda la historia de su organizacion.
- AGENTE puede abrir cualquier cliente o propietario del tenant, pero las
  secciones solo muestran registros de los que es responsable. El nombre de
  agente se reemplaza por `"-"` y la seccion `agentes` queda vacia.
- BROKER solo puede abrir una ficha si hay historia visible de sus agentes
  supervisados o de una captacion que revisa. Dentro ve solo esa historia.
- Un id de otro tenant se comporta como inexistente (404).

Los requerimientos del cliente no pertenecen a un agente: una vez que el
broker tiene acceso a la ficha, puede verlos completos.

## 7. Filas y vocabulario

Los procesos posibles son `Requerimiento`, `Propiedad`, `Prospeccion`,
`Captacion`, `Oportunidad`, `Interaccion`, `Visita`, `Solicitud`, `Cierre` y
`Agente`.

El texto de estados, canales y resultados usa las descripciones legacy, no
los codigos persistidos. Las rutas mantienen el formato relativo sin `/`
inicial (`cliente-detail/{id}`, `local-detail/{id}`,
`captacion-detail/{codigo}`, etc.).

## 8. Gate de corte

- [x] Tests de comportamiento del service: **12/12**.
- [x] Reactor completo verde: **344/344**.
- [x] E2E dedicado contra PostgreSQL/Docker: **60/60**.
- [x] Las 11 secciones, aliases de paginacion y marcadores parciales quedan
      verificados.
- [x] Roles, privacidad e aislamiento de organizacion quedan verificados.

El corte no necesita V11: lee las tablas ya aplicadas por V4, V5, V7 y V8.
`verificacion/e2e-ficha-comercial.ps1` deja registros E3 identificables en
`BROX_LEGACY`; el fixture de la segunda organizacion se elimina en la misma
corrida.
