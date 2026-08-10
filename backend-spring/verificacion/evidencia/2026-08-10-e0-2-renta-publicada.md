# E0.2 — hito `P` de renta publicada

**Fecha:** 2026-08-10
**Etapa:** E0 · Histórico económico
**Estado:** CERRADO — gate verde

---

## Qué se cerró

`Publicacion.rentaPublicada` mutaba en su sitio y el valor anterior se perdía. El
hito `U` **no** cubre esto: ese es el precio que el propietario autoriza en
privado, y la elasticidad se mide contra **lo que el mercado vio**.

### Auditoría de productores

| Productor | Qué hace |
|---|---|
| `crear` (96) | alta de publicación con renta |
| `actualizar` (118-120) | edición — y la **moneda cambia sin condición** (121) |
| `sincronizar` (185) | propaga el precio referencial del local |

El dato que decidió el diseño: **`sincronizar` se llama desde el alta del local
(`LocalComercialServiceImpl:250`) y desde TODA edición (`:318`)**. Sin
deduplicación, cambiar el metraje de un local escribiría un hito de renta.

---

## Dos decisiones

**`P` solo si la publicación está PUBLICADA.** Un borrador no lo ve nadie;
anotar su renta como "publicada" metería en la serie precios que nunca
existieron para el mercado. Por eso `cambiarEstado` también pasa por el
productor: el instante en que un borrador se publica **es** la primera vez que
esa renta se ve.

**Deduplica por importe Y moneda, con `compareTo` y no `equals`.** `5200` y
`5200.00` son el mismo precio; `equals` de `BigDecimal` los distingue por escala,
y con él cada sincronización habría duplicado el hito.

### Limitación conocida y aceptada

El histórico cuelga de la **propiedad**, no de la publicación. Con varias
publicaciones a rentas distintas por canal, cada cambio queda registrado
—ninguno es falso— pero la serie no puede atribuirlo a un portal. **No se promete
elasticidad por canal** hasta modelarlo.

---

## Pruebas

**Service** — `PublicacionServiceImplTest`, 8 nuevos (30/30 con
`LocalComercialServiceImplTest`): publicar deja hito · borrador no · publicar un
borrador sí · sincronizar sin cambio económico no duplica · renta nueva sí ·
cambio de moneda solo también cuenta · borrador sin publicación previa no escribe
nada · editar un anuncio publicado deja hito.

**Integración** — `HistoricoPrecioIntegrationTest`, 3 contra PostgreSQL real.
Cubre solo lo que un mock no puede:

1. **El CHECK de la tabla contra `PrecioPropiedad.HITOS`, en ambas direcciones.**
   Existe por el incidente de V40: se estrechó un CHECK y un productor siguió
   escribiendo un valor que ya no cabía; ni javac ni Hibernate leen un CHECK, así
   que reventó en runtime. El productor de `P` depende exactamente de eso.
2. **El desempate por `id` cuando la fecha empata.** La deduplicación pide el
   *último* `P`, y varios hitos del mismo día son normales (una edición de precio
   y su propagación caen ambas hoy). Solo el SQL decide ese orden.
3. **La escala de `numeric(12,2)`.** Se escribe `5200`, vuelve `5200.00`.

**Gate** — `verificacion/Verificar-Cierre.ps1`: `CIERRE VERDE`, exit 0.

Los **8** de integración ejecutados (0 skipped), comprobado sobre la salida:
`BusquedaLocales` 12 · `InvariantesComision` 6 · `OcupacionInmueble` 5 ·
`PadronDeGobierno` 5 · `RepositorioEstados` 4 · `SimulacroRecuperacion` 4 ·
`HistoricoPrecio` **3** · `VocabularioPersistido` 1 · `GateDeCierre` 3.

E2E: `comision-movimientos` 65 · `disponibilidad-contrato` 41 · `f4-solicitud`
125 · `estabilizacion-alquiler` 18 → **249 comprobaciones, 0 fallas**.

---

## Dos fallos del primer intento, y por qué importan

**`GateDeCierreTest` rechazó el build.** El inventario de tests de integración
vive **dentro de un test**: añadir uno rompe la compilación hasta declararlo ahí.
Yo había actualizado `Verificar-Cierre.ps1` pero no el inventario, y el gate no
dejó pasar la guarda a medias. Funcionó como está diseñado.

**El test de escala leía desde la caché de JPA, no desde Postgres.** Con
`@Transactional` + `saveAndFlush`, `findFirst…` devuelve la misma instancia
recién guardada: el test nunca fue a la base, que era su única razón de existir.
Se corrigió con `flush()` + `clear()` explícito y el porqué escrito al lado.

Anotado porque es la misma familia de error dos veces en la misma tanda —una
aserción tautológica y una lectura desde caché—: **un test puede estar verde sin
poder fallar por la razón correcta**. Argumento a favor de los invariantes
ejecutables de E4: la revisión humana atenta no basta.

---

## Cambios de guarda

- `Verificar-Cierre.ps1` y `GateDeCierreTest` incluyen ya
  `HistoricoPrecioIntegrationTest`: a partir de ahora el gate **exige que se haya
  ejecutado**, no solo que no falle.

---

## Estado de E0

| | | |
|---|---|---|
| 0.1 | `U` inicial + backfill (16 rescatados) | CERRADO |
| 0.2 | hito `P` de renta publicada | **CERRADO** |
| 0.3 | decisión funcional del productor `O` | pendiente — es decisión, no código |
