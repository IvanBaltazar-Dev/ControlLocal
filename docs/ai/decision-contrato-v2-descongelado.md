# CONTRATO V2 DESCONGELADO PARA EVOLUCIÓN

**Fecha: 2026-08-09.** Decisión del titular del producto. Sustituye a la regla de congelación que
gobernó todo el desarrollo desde F0.

---

## 1. Qué deja de ser cierto

Hasta hoy, el contrato REST de v2 era **byte-compatible con `backend-java`**: DTOs, cuerpos de
error, códigos de estado, mensajes exactos de 401/403/429 y formato del token HS256. Esa regla tenía
una razón concreta —permitir que los dos backends coexistieran durante el Strangler— y esa razón
**ya no existe**: la v1 nunca corrió en producción y nunca lo hará.

**El contrato antiguo deja de ser la autoridad.**

## 2. Qué puede cambiar desde ahora

DTOs, endpoints, nombres, estados, errores, permisos, modelos, flujos, contratos HTTP y
comportamientos heredados.

Con **dos condiciones que no son negociables**:

1. **Una razón funcional o arquitectónica.** No se cambia por gusto ni por simetría.
2. **El cambio viaja con sus tests.** Un cambio de contrato sin test que lo fije es una regresión
   esperando fecha.

## 3. La nueva regla de desarrollo

**Antes:**

```
v1  →  copiar comportamiento  →  mantener compatibilidad
```

**Desde ahora:**

```
necesidad del producto  →  regla de dominio  →  contrato v2  →  backend  →  frontend  →  tests
```

**OpenAPI y las pruebas son el contrato ejecutable.** Lo que no esté en uno de los dos, no es
contrato.

## 4. Corte lógico del legado

`backend-spring` + `frontend-angular` + PostgreSQL son **la única fuente de verdad funcional del
desarrollo**. En consecuencia:

- GlassFish no define comportamiento nuevo.
- Blazor no define UX nueva.
- MySQL no define el modelo futuro.
- **Un comportamiento extraño de la v1 no se replica automáticamente.**
- No se modifica código nuevo únicamente para conseguir igualdad con el legado.

> **Nota sobre dónde está el legado.** El árbol (`backend-java/`, `frontend-csharp/`, `database/`) se
> eliminó del working tree el 2026-08-08. Sigue **íntegro en el historial de git** y se consulta con
> `git checkout <commit> -- backend-java/`, que es el modo referencia que esta etapa pide. No hay
> que restaurarlo al árbol para consultarlo.

## 5. Lo que ya se descongeló, antes de esta decisión

El trabajo empezó el 2026-08-08 y está hecho y verificado. Se lista aquí porque es el precedente de
cómo se hace un descongelado: **cada cambio con su test reescrito para fijar la conducta correcta,
nunca ablandado**.

| Punto | Qué era | Qué es |
|---|---|---|
| **H-12** | `GET /documentos/contenido` **público sin autenticación** — por ahí se descargaban documentos de identidad. La "capability" era la ruta física, de 32 bits, sin caducidad y en el query string | Exige token. Verificado: 401 sin él, 200 con él |
| **3.5** | La alerta de captación **casi nunca se emitía**: `captar` construía la captación a mano, saltándose el alta que avisa. Y captar desde una prospección es el camino normal | Emite el mismo tipo y severidad que el otro camino |
| **3.6** | Cuatro números falsos: `100 %` fijo con base cero, *"visita realizada"* contando canceladas, `captacionesPendientes` duplicado, y el operativo cayendo a **todo el historial** si el periodo venía vacío | Los cuatro corregidos |
| **3.7** | La bandeja **cortaba en 10 y descartaba el resto sin dejar rastro**: se veía igual con 10 tareas que con 40 | Devuelve todas, con su orden por prioridad |
| **3.8** | Tres vías de subida por un bug del `SocketsHttpHandler` de .NET 10 | Una sola. Con las otras se fue un búfer que **no liberaba una carga abandonada hasta reiniciar** |

En los cinco, los tests que fallaron **nombraban el bug**
(`laBandejaCortaEn10YDescartaElRestoENSILENCIO`, `conVisitaRealizadaCuentaVisitasDeCualquierEstado`,
`laPrimeraFilaDelEmbudoLlevaCienAunConBaseCero`,
`sinProspeccionesEnLaVentanaElOperativoCaeATodasLasDelAlcance`, y un check de E2E llamado
*"captar NO avisa al broker… (hueco del cable)"*). Se reescribieron para fijar la conducta correcta.

## 6. Cómo se evoluciona a partir de aquí

**No** una refactorización masiva transversal. Módulo a módulo, y **se cierra uno antes de abrir el
siguiente**:

1. inventariar comportamiento heredado;
2. separar bug / deuda / regla válida;
3. decidir el comportamiento objetivo;
4. modificar dominio y contrato;
5. modificar backend;
6. modificar Angular;
7. actualizar tests;
8. ejecutar la regresión de la vertical;
9. ejecutar los gates transversales;
10. cerrar el módulo.

## 7. Lo que esta etapa NO es

No es una release, no es RC-1 y no autoriza apagar servidores, arquitectura productiva, alta
disponibilidad, varias instancias, backups externos definitivos, migración productiva, dominio,
DNS ni monitorización productiva. Todo eso sigue en la ruta a producción
(`plan-maestro-ruta-a-produccion.md`) y se retoma cuando se decida preparar el lanzamiento.

## 8. Referencia

La línea base contra la que se detectarán regresiones:

- Evidencia: `backend-spring/verificacion/evidencia/2026-08-09-baseline-v2-pre-descongelado.md`
- Tag: **`baseline-v2-pre-descongelado`**
