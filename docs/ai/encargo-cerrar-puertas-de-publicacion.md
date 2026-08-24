# Encargo — Microcorte · ningún camino de creación elude la publicabilidad

**Congelado por CONTROL el 2026-08-24**, por decisión del titular tras cerrar el
corte de la señal `PUB`.

**BASE_SHA:** `7655295` — rama `feat/modelo-universal-y-autoridad-del-dato`,
árbol limpio, dev en `V82`.

**Cierre de coherencia, no funcionalidad nueva. El Corte 5 no se abre aquí.**

---

## 1. Objetivo

> Al terminar, **no debe existir ningún camino de creación de publicación que
> pueda eludir las mismas reglas de publicabilidad del Core.**

---

## 2. PREFLIGHT — medido por CONTROL. **Y son CINCO vías, no cuatro.**

El encargo del titular habla de «las cuatro vías». **Medido, hay una quinta**, y
tiene exactamente el mismo defecto que la cuarta.

| # | vía | consumidores en `src/main` | ¿expuesta? | ¿pasa por `exigirPublicable`? |
|---|---|---|---|---|
| 1 | `crearEnEncargo(idEncargo, …)` | controlador | **SÍ** — `POST /encargos/{id}/publicaciones` | **sí** (`:94`) |
| 2 | `cambiarEstado(idPublicacion, …)` | controlador | **SÍ** | **sí** (`:326`, transición a `PUBLICADO`) |
| 3 | `actualizar(idPublicacion, …)` | controlador | SÍ | no crea — edita |
| 4 | **`crear(idPropiedad, …)`** | **CERO** | **NO** | **NO** ← la puerta del Auditor |
| 5 | **`sincronizar(idPropiedad, …)`** | **CERO** | **NO** | **NO** ← **no inventariada** |

**Cómo se midió** (`rg` sobre `**/src/main/**/*.java`, con control positivo):

- `publicaciones.crear(` / `.crear(idPropiedad` → **ningún acierto en `src/main`**.
  Único consumidor: **un test**, `PropiedadSinEncargoIntegrationTest:161`.
- `sincronizar(` → **sólo** `PublicacionService` y `PublicacionServiceImpl`
  (su declaración y su definición). **Ningún llamador de producción.** Sus
  consumidores son tests de `PublicacionServiceImplTest`.
- **Control positivo**: `LocalComercialServiceImpl` **sí** inyecta
  `PublicacionService` (campo `:87`, constructor `:112`) — la dependencia existe
  y el barrido la ve— **pero no llama a ninguno de los dos**. Su javadoc
  (`PublicacionServiceImpl:414`) todavía afirma que *«`LocalComercialServiceImpl`
  llama a `sincronizar` en TODA actualizacion»*: **eso ya no es cierto** y hay que
  corregirlo.

### 2.1 Por qué la quinta es igual de grave, o más

`sincronizar:356-380` **construye una `Publicacion` nueva** cuando no existe
(`:362-371`), le pone el estado que le pasen —**incluido `PUBLICADO`**— y llama a
`registrarImportePublicado`, que **escribe un hito `P` en la serie de precios**.
Todo ello **sin preguntar por una sola clave del catálogo**.

Es residuo del formulario de la v1 —lo dice su propio comentario `:351-354`— y la
v1 se borró el 2026-08-08.

---

## 3. Decisión de implementación

La regla del titular: *si tiene cero consumidores, preferir retirarlo si no forma
parte de ningún contrato necesario.*

**Las dos tienen cero consumidores de producción. Se retiran las dos**, de la
interfaz y de la implementación.

**Retirar es aquí mejor que delegar, y por una razón concreta:** una vía que
delega sigue existiendo y sigue pudiendo desincronizarse en el próximo cambio. Una
vía que no existe no puede eludir nada. El objetivo dice «ningún camino»; la forma
más barata de garantizarlo es que no haya camino.

### 3.1 Qué pasa con sus pruebas

- **`PropiedadSinEncargoIntegrationTest:161`** («una propiedad sin encargo no se
  puede publicar») usa `crear`. Al retirarlo, **esa regla pasa a ser
  estructural**: no queda ningún método que acepte publicar sin encargo. **No
  borres la intención**: deja constancia —renombra o reescribe el test para que
  afirme lo que ahora es cierto, o documéntalo donde corresponda—. Lo que **no**
  vale es que la garantía desaparezca en silencio.
- **`PublicacionServiceImplTest`** tiene varios casos que ejercitan
  `sincronizar` para probar la **deduplicación del hito `P`** y la moneda. Ese
  comportamiento **sigue vivo** —`registrarImportePublicado` lo usan
  `crearEnEncargo` y `cambiarEstado`—, así que **repunta esos tests a una vía que
  siga existiendo**. No los borres: lo que protegen es real.

### 3.2 Si al medirlo aparece un consumidor legítimo

Entonces **no se retira: se hace que recorra la misma validación canónica**
—llamando a `exigirPublicable`, **no** copiándolo—. Y me lo dices antes de
hacerlo.

---

## 4. Prohibido

- **No copiar `exigirPublicable`.** Si alguna vía debe validar, **llama** al
  método existente.
- **No crear otra consulta.**
- **No** *hardcodear* `ALT`/`PUB` en ninguna capa.
- **No tocar Angular.**
- **No** modificar `V81`/`V82`. **Este corte no lleva migración.**
- **No abrir el Corte 5 de paso.**
- No cambiar exigencias, no *backfill*.

---

## 5. Prueba obligatoria

**Recorrer cada camino de creación que quede** con una propiedad **bloqueada por
catálogo** (un `LOCAL` sin `tipo_acceso` sirve: hay 21 en dev):

> **Ningún camino puede crear la publicación saltándose el bloqueo.**

Y con una propiedad **completa**: **el camino canónico sigue funcionando.**

Fija además una **prueba de arquitectura** que impida la reaparición: que
**ninguna vía pública de `PublicacionService` que cree o publique un anuncio
pueda hacerlo sin pasar por la validación canónica**. Si la de tipo ArchUnit no
alcanza —las constantes y las llamadas internas no siempre son visibles—, vale
un test que recorra la superficie del servicio; **dilo si no llega, en vez de
fingir cobertura**.

---

## 6. Lo que el Auditor buscará

Está avisado de buscar: **un consumidor oculto** del método viejo · **una
sobrecarga que siga sin guarda** · **una delegación que valide un sujeto pero no
el otro** · **una ruta indirecta capaz de publicar saltándose
`exigirPublicable`**.

**La quinta vía la encontré yo en el preflight, no él.** Asume que puede haber
una sexta: barre con **control positivo** o con `rg` —`grep -iF` aborta en esta
máquina sin escribir en stderr— y **mira también las escrituras directas al
repositorio** (`publicaciones.save(...)`), no sólo las llamadas al servicio.

---

## 7. Alcance documental

1. `docs/ai/mapa-ejecucion-brox.md` — el microcorte y su cierre.
2. `docs/ai/pendientes-brox.md` — §2.5 ter: la deuda de la cuarta puerta **queda
   saldada**, y se dice que eran **cinco**.
3. Evidencia:
   `backend-spring/verificacion/evidencia/2026-08-24-puertas-de-publicacion.md`.
4. **Corrige el javadoc mentiroso** de `PublicacionServiceImpl:414`.

Sin fila nueva en la matriz: no hay endpoint nuevo. **Si retirar un método de la
interfaz cambiara algún contrato REST, para y pregunta** — no debería, porque
ninguno está expuesto.

---

## 8. Cierre

Gate `.sql` verde **dentro** de `Verificar-Cierre.ps1` · la prueba de §5 · el test
de arquitectura · una sola corrida con `TEST_DB_URL` · **`ng test` y
`ng build --configuration production` igualmente**, para demostrar que Angular no
se rompió aunque no se toque · **commit único** · sin push.

## 9. Protocolo

`STOP — DECISIÓN REQUERIDA POR CONTROL` si el preflight te contradice, antes de
tocar un archivo.

```
LISTO PARA AUDITORÍA
BASE_SHA=7655295
CANDIDATE_SHA=<sha>
```
