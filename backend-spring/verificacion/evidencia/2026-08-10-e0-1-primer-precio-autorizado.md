# E0.1 — primer precio autorizado (`U`) y rescate del histórico

**Fecha:** 2026-08-10
**Etapa:** E0 · Histórico económico
**Estado:** CERRADO — gate verde

---

## Qué se cerró

El alta de una propiedad **no dejaba ningún hito de precio**. El único productor
automático era la edición (`LocalComercialServiceImpl.actualizar`), y esa graba el
hito con el precio **nuevo**, nunca con el anterior.

Consecuencia: la primera edición de un local borraba para siempre su **precio de
salida** — el número contra el que se mide cuánto cedió el propietario hasta el
cierre, y que ninguna otra tabla conserva.

Dos cambios, en la misma tanda:

1. **Hacia adelante** — el alta graba su primer hito `U` (autorizado) con el monto
   y la moneda del alta, en la misma transacción y **antes** de sincronizar la
   publicación (el orden de la serie es autorizado → publicado).
2. **Hacia atrás** — `V45` rescata el primer `U` de las propiedades que todavía no
   habían sido editadas.

### Por qué el rescate es una deducción y no una estimación

`actualizar` escribe un hito `U` **siempre** que cambia precio o moneda. Por tanto:

> propiedad con **cero hitos** ⇒ su precio nunca se editó ⇒ `precio_referencial`
> vigente **es** el de salida

No se adivina un valor histórico: se rescata uno que seguía intacto porque nadie
lo había pisado. Era una ventana que se cerraba sola.

---

## Conciliación del backfill (`controllocal_dev`)

| | |
|---|---|
| Propiedades con precio | 21 |
| Con histórico **antes** | 5 |
| **Candidatos** | **16** |
| **Insertadas** | **16** |
| Candidatos restantes | **0** |
| Propiedades sin moneda | 0 (el `COALESCE` a PEN no se usó) |

La conciliación **vive dentro de la migración**: `V45` cuenta candidatos, inserta,
compara y lanza `RAISE EXCEPTION` si no cuadran. No depende de que alguien mire un
log después. No disparó.

**Idempotencia**, reejecutando el cuerpo del INSERT sobre la base ya migrada:

```
BEGIN
INSERT 0 0
ROLLBACK
```

**Fecha del hito rescatado** = `fecha_registro::date` de la propiedad, no `now()`:

```
3 | U | PEN 4500.00 | hito=2026-08-09 | alta=2026-08-09
4 | U | PEN 3800.00 | hito=2026-08-09 | alta=2026-08-09
5 | U | PEN 5200.00 | hito=2026-08-09 | alta=2026-08-09
```

---

## Pruebas

**Service** — `LocalComercialServiceImplTest`: **22/22**.

El test nuevo (`elAltaDejaElPrimerHitoAutorizadoYLaEdicionNoLoPisa`) hace las **dos**
operaciones a propósito: lo que se protege no es que el alta escriba, es que la
edición posterior **no borre**. Comprueba que tras editar 8500 → 9000 quedan dos
hitos y el de salida sigue siendo 8500.

**Corrida de cierre** — `verificacion/Verificar-Cierre.ps1`, `CIERRE VERDE`, exit 0.

Los 7 de integración **se ejecutaron**, comprobado sobre la salida y no sobre el
veredicto del script (0 skipped en todos):

| Suite | Tests |
|---|---|
| `BusquedaLocalesIntegrationTest` | 12 |
| `InvariantesComisionIntegrationTest` | 6 |
| `OcupacionInmuebleIntegrationTest` | 5 |
| `PadronDeGobiernoIntegrationTest` | 5 |
| `RepositorioEstadosIntegrationTest` | 4 |
| `SimulacroRecuperacionIntegrationTest` | 4 |
| `VocabularioPersistidoIntegrationTest` | 1 |
| `GateDeCierre` | 3 |

E2E, las cuatro suites: `comision-movimientos` 65 · `disponibilidad-contrato` 41 ·
`f4-solicitud` **125** · `estabilizacion-alquiler` 18 → **249 comprobaciones, 0 fallas**.

`f4-solicitud` reporta en otro formato (`===== 125 OK / 0 FALLAS =====`) que el resto
(`== Resultado: N OK ==`). Contar solo por el segundo formato hace creer que corrieron
tres suites de cuatro.

---

## Notas para quien siga

- **`mvn -q` oculta el resultado de los tests**, y el aviso de auto-attach de Mockito
  llega por stderr: PowerShell lo envuelve como `NativeCommandError` y parece un fallo
  con exit 0. Correr sin `-q` y mirar `Tests run:`.
- En PowerShell hay que **entrecomillar los `-D`** (`"-Dtest=X"`): sin comillas parte
  en el punto y Maven responde `Unknown lifecycle phase ".failIfNoSpecifiedTests"`.
- Durante esta tanda el API de desarrollo estaba recibiendo POSTs de local con código
  duplicado (`uq_propiedad_codigo`, 23505) desde hilos HTTP ajenos a esta sesión. No
  afecta a la migración —Flyway no interviene ahí— pero ensucia el log del contenedor.

---

## Lo que sigue en E0

- **0.2** · hito `P` de renta publicada. `PublicacionServiceImpl` escribe
  `rentaPublicada` en dos sitios sin histórico; y `registrar` ya llama a
  `publicaciones.sincronizar(...)` con el precio del alta, que es donde nace el
  primer `P`.
  **Limitación aceptada:** `P` cuelga de propiedad, no de publicación. Si un local
  se publica a rentas distintas por canal, la serie no lo distingue. No se resuelve
  ahora; no se promete elasticidad por canal.
- **0.3** · decisión funcional del productor `O`. Es decisión, no código.
