# El editor universal · bloque 3f

**Cerrado el 2026-08-22.** Sin migración: el Core ya tenía el contrato. Lo que
faltaba era la puerta.

---

## El hueco

`PUT /propiedades/{id}` existía desde el bloque 2, estaba en la matriz y tenía
su gate de conservación (47 casos por los siete tipos). Y **ninguna pantalla lo
llamaba**: `local-form` —el editor que rechazaba cinco tipos, inventaba el rubro
y aplastaba el uso— se borró en el bloque 3d, y el hueco quedó sin editor. Se
podía capturar en universal y no corregir. La auditoría de profundidad lo
señaló como la puerta que tenía que existir **antes** de ampliar el catálogo:
cada clave nueva sin forma segura de corregirla es un error de captura
congelado.

---

## Lo que se construyó

### Una ruta, siete tipos

`/propiedades/:id/editar` (`features/propiedad-editor/`), sólo AGENTE, sobre
`PUT /propiedades/{id}`. No revive `local-form`, no hay un editor por tipo, no
se vuelve a `/locales`. Arranca leyendo:

- `GET /propiedades/{id}` — el estado real completo;
- `GET /captura/definicion?tipoPropiedad=…&operaciones=…` — qué características
  pertenecen a este tipo, con las operaciones **vivas**. El mismo plan que usó
  el alta.

Vocabularios y rótulos vienen del Core. Angular no decide que un departamento
lleva dormitorios ni que un terreno lleva zonificación: pinta lo que recibe.

### Cuatro bloques independientes

| Bloque | Qué edita | Por dónde viaja |
|---|---|---|
| Propiedad y ubicación | dirección, distrito y los campos estructurales que el comando admite; descripción | `ubicacion{}`, `descripcion` |
| Características | las del catálogo para este tipo | `atributos[]` + `atributosABorrar[]` |
| Titulares | cuotas, representante, quiénes | `titulares[]` — **el conjunto completo, sólo si se tocó** |
| Gestión comercial | **un bloque por `idEncargo`** | `operaciones[]` (importe, moneda, exclusividad, fechas) + `condiciones[{idEncargo}]` (lo pactado) |

`ENC-0016 · Venta` y `ENC-0032 · Alquiler` son dos bloques, y lo serían aunque
hubiera otro de venta: el histórico pertenece al encargo. **La operación de un
encargo no se cambia desde aquí** —eso reescribiría su historia—; el cambio de
intención es cerrar uno y abrir otro. Los cerrados se ven y no se editan.

### La regla del cuerpo

El editor **no construye un `ComandoEdicion` completo**. Lleva la cuenta de lo
tocado, bloque a bloque, y `cambios()` produce un cuerpo que contiene eso y
nada más:

```
toca la ubicación      →  { ubicacion: { distrito } }
toca características   →  { atributos: [...] } / { atributosABorrar: [...] }
toca ENC-0016          →  { operaciones: [{ operacion: VENTA, importe, moneda }] }
toca los titulares     →  { titulares: [...conjunto completo...] }
```

Y las tres cosas que no se confunden, porque confundirlas es exactamente cómo
el editor anterior corrompía:

```
no sé el valor     ≠  inventar un defecto   → un selector sin elegir es ''
no toqué el valor  ≠  mandarlo vacío        → un campo vaciado NO viaja
quiero eliminarlo  =  intención explícita    → «Quitar», y viaja en atributosABorrar
```

Un campo que se vacía vuelve a «no tocado». Retirar un valor es otra acción,
con nombre, y el Core rechaza `''` con 400 si alguien intentara lo contrario.

### El renderizador pasa a ser compartido — y estaba desfasado

El alta pintaba cada campo con un `ng-template` propio. Al necesitarlo el
editor, se extrajo a **`shared/campo-gobernado`** (`<cl-campo-gobernado>`), y
al hacerlo apareció una deriva: desde el Corte 0B el Core publica `opciones`
como `{valor, rotulo}`, y `captura.service.ts` seguía tipándolas como
`string[]` con el alta pintando `{{ opcion }}`. **El tipo TS reflejaba el
contrato viejo.** Con un solo renderizador el contrato se corrige una vez, y
los `opciones` del listado también pasan a usar el rótulo del Core.

---

## Lo que el cable no transporta, y el editor no ofrece

- **`IMPORTE` y `SELECTOR_MULTIPLE`**: `AtributoRequest` lleva `(clave, valor)`
  —sin moneda ni lista—. Hoy ninguna clave del sistema es de esos tipos; el
  editor las pinta **en sólo lectura** con su motivo, para que nadie crea que el
  dato no existe.
- **Código, uso, tipo, estado del registro**: no son del PUT. Se ven, no se
  tocan.
- **Cerrar un encargo o cambiarle la operación**: otro caso de uso, otro rol.

---

## Verificación

```
backend   717 + 48 + 364 · 0 skipped · 21/21 suites de integración (CIERRE VERDE)
angular   665/665 (+19 del editor, +1 de la ficha)
build     producción sin errores; propiedad-editor.scss 4,11 kB (techo 16 kB)
E2E       cierre por defecto 125 OK / 0
          editor-universal  114 OK / 0  ← nueva, los siete tipos por HTTP
```

### Los siete gates que se exigieron antes del commit

| # | Gate | Dónde |
|---|---|---|
| 1 | **Conservación × 7 tipos**: abrir → modificar una sola cosa → guardar → releer = idéntico | `ConservacionDeLaEdicionIntegrationTest` (47) + **`e2e-editor-universal`** por el cable HTTP, fixture **derivado del contrato** |
| 2 | **Venta + alquiler simultáneos**: editar sólo el importe de venta; el alquiler idéntico con su histórico | conservación (`cambiarUnEncargoNoContaminaAlOtro`) + E2E por tipo + spec del editor (`operaciones` sólo VENTA) |
| 3 | **Histórico**: cambiar el importe **añade** el hito, no reemplaza | conservación gana la afirmación explícita `hitos(después) = hitos(antes) + U 330000`; E2E: «gana UN hito autorizado» y «los anteriores siguen delante» |
| 4 | **Características**: modificar una no altera ubicación, titulares ni encargos | conservación (`guardarUnBloqueNoTocaNingunOtro`) + E2E + spec |
| 5 | **Borrado explícito**: borrar retira; no tocar conserva; vaciar se rechaza | conservación + E2E («un valor en blanco se rechaza con 400», «el rechazo no dejó rastro») + spec |
| 6 | **Frontera Angular**: ninguna matriz fija `tipo → campos`, ni incondicional | `FronteraDeAutoridadEnElSpaTest` **ampliado**: `OTRO` entra en la lista, una clave de objeto sin comillas cuenta igual que un literal, `@case ('CASA')` y la comparación Yoda también caen |
| 7 | **Ningún default inventado**: uso, operación, rubro, listas | spec («un selector sin elegir queda en blanco», «lo que el cable no transporta se ve y no se edita»); E2E: `uso` y `codigo` en el retrato sin moverse |

Además: `EdicionRequestJsonTest` (web) cubre el tramo que el gate de
conservación no recorría — ausente llega `null`, `[]` llega lista vacía, y son
dos cosas distintas que el servicio trata distinto.

`PropiedadComoActivoDeDatoIntegrationTest` no se tocó y sigue 18/18: el editor
no abre nada, no publica nada y no inventa encargos.

---

## Lo que se encontró al hacerlo

**Las claves calificadas.** La definición publica lo del encargo como
`garantia_meses:ALQUILER`; la ficha y el PUT usan la clave **desnuda** atada al
`idEncargo`. El editor casa las dos formas por el separador `:`, que el Core
garantiza que ninguna clave del catálogo lleva.

**`interiorUnidad` y `nombreEdificioGaleria` llegan en `delTipo`** —son
estructurales de L/O/D y el motor los pregunta allí—, pero en el cuerpo del
alta y del PUT van en `ubicacion`, no en `atributos`: el enrutador de
atributos sólo conoce el catálogo y responde 400. El editor ya lo resolvía
—decide el hueco del cable por el nombre del campo, no por la sección en que
llegó la pregunta—; el fixture de la E2E no, y es donde salió.

**El PUT devuelve `260000` y el GET `260000.00`.** La respuesta del PUT
serializa el importe recién puesto sin escala; la relectura trae la escala de
`numeric(14,2)`. Es el mismo importe y el gate Java ya comparaba por valor; la
E2E hace lo mismo. Se anota porque un consumidor que comparara texto lo leería
como cambio.

**`titulares: []` no es no-op.** El conjunto vacío cierra todas las
titularidades — legítimo desde V76, pero es una intención. Por eso el editor
sólo manda el bloque si se tocó, y lo manda entero.

---

## Lo que NO se hizo a propósito

No se añadió ninguna clave. Nada de dúplex, mascotas, ascensores, partida
registral ni áreas comunes: eso es la profundidad inmobiliaria, y empieza
**ahora** que existe una forma segura de corregir lo que se siembre.

**Deuda que queda dicha:** la comisión se escribe en el alta y no se lee en la
ficha (`tipoComision`, `baseCalculo`, `valorComision`, `tratamientoIgv` no
viajan en `EncargoResponse`), así que el editor no puede mostrarla ni
editarla — y `actualizarEncargo` las ignora, así que tampoco se pierden. Es
la misma clase de defecto que ya se pagó con `exclusividad`.
