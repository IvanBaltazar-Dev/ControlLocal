# Encargo · Sesión paralela: KAIROS funcional (bloque 4)

**Para:** una sesión de Claude Code trabajando **en paralelo** sobre este mismo
repositorio, mientras otra sesión hace el SPA.

**Abierto:** 2026-08-18. **Bloque 4** de la ruta a BROX 1.0
(`docs/ai/mapa-ejecucion-brox.md`).

---

## Las dos reglas de la sesión paralela

Se listan primero porque romperlas cuesta una corrida entera.

1. **No toques `frontend-angular/`.** Ni un fichero. El SPA lo lleva la otra
   sesión: sidebar, tokens, `core/auth/acceso.ts` y las 57 pantallas. Si KAIROS
   necesita algo del cable que el SPA también consume, **anótalo y dilo**; no lo
   cambies en los dos sitios.

2. **No corras `Verificar-Cierre.ps1` ni ninguna suite E2E sin avisar.** Las
   suites de búsqueda (`locales-busqueda`, `demanda-busqueda`,
   `solicitudes-busqueda`) miden p95 y peor caso **en esta misma máquina**, y un
   `ng build` de la otra sesión las tumba por tiempo — pasó el 2026-08-06 y
   costó una regresión completa. Para tu trabajo diario usa
   `mvn -o test -pl controllocal-service` y las pruebas de integración con
   `TEST_DB_URL`.

Además: `docs/ai/matriz-operacion-rol.md` es un fichero que **las dos sesiones**
tocan (cada endpoint nuevo exige su fila). Añade las tuyas al final de su
sección y no reordenes las existentes, para que el merge sea trivial.

---

## Qué ya existe, y es lo que NO hay que volver a construir

El bloque 3 cerró el 2026-08-18 con el motor de captura y el alta universal
funcionando contra PostgreSQL real. **KAIROS no tiene lógica inmobiliaria
propia**: es un adaptador sobre lo que ya hay.

| Pieza | Dónde | Qué hace |
|---|---|---|
| `MotorDeCaptura` | `service/captura/` | qué se sabe, qué falta, qué se pregunta ahora |
| `PropiedadUniversalService` | `service/` | alta, lectura y edición universales, todo o nada |
| `BorradorCaptura` | `domain/captura/`, V56 | el estado transaccional de una captura a medias |
| `ComandoIdempotente` | `domain/auditoria/`, V57 | un reintento no duplica |
| `EventoDominio` | `domain/auditoria/`, V52 | traza con `origen` UI/KAIROS/API/SISTEMA |
| `CatalogoAtributo` | `domain/inmueble/`, V48 | de aquí salen las preguntas de cada tipo |

Endpoints ya cortados y probados:

```
POST   /captura                 avanzar (crea o continúa un borrador)
GET    /captura                 lo que hay a medias en el tenant
GET    /captura/{id}            estado de una captura
POST   /captura/{id}/ejecutar   ejecuta el caso de uso
DELETE /captura/{id}            descartar

POST   /propiedades             alta universal
GET    /propiedades/{id}        ficha por el modelo universal
PUT    /propiedades/{id}        edición parcial
```

Cabeceras que ya se respetan: **`X-Origen: KAIROS`** (viaja hasta
`evento_dominio.origen`) e **`Idempotency-Key`**.

**Compruébalo antes de escribir nada.** Con la pila levantada
(`docker compose -f backend-spring/docker-compose.yml up -d postgres api`):

```bash
curl -s -X POST http://127.0.0.1:8090/controllocal/Api/captura \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'X-Origen: KAIROS' \
  -d '{"intencion":"REGISTRAR_PROPIEDAD","datos":{"tipoPropiedad":"DEPARTAMENTO","operacion":"VENTA","distrito":"Miraflores","importe":"180000","moneda":"USD"}}'
```

Responde con `faltante: ["titulares","direccion","metraje_total","dormitorios"]`
y la siguiente pregunta. **Ese es el contrato sobre el que se construye.**

---

## Qué hay que entregar

Un adaptador que convierta lenguaje natural en llamadas a los casos de uso que
ya existen. Seis acciones, y ninguna con reglas propias:

```
Consultar propiedad        Registrar propietario
Registrar propiedad        Continuar borrador
Consultar cliente          Registrar interacción
```

El ciclo es siempre el mismo:

```
comprender
   ↓
consultar el estado de captura      (GET /captura/{id})
   ↓
proponer / completar datos          (POST /captura)
   ↓
invocar el MISMO caso de uso        (POST /captura/{id}/ejecutar)
   ↓
devolver el resultado
```

Lo que gobierna qué puede hacer, con qué permisos y qué se confirma antes de
ejecutar está congelado en **`docs/ai/decision-kairos-contrato-de-acciones.md`
(D-K-1)**. Léelo entero antes de diseñar; no lo re-decidas.

---

## Cuatro cosas que no son negociables

1. **KAIROS no escribe en la base.** Ni una sentencia. Todo pasa por los casos
   de uso, que son los que tienen la transacción, las invariantes y el evento de
   dominio. Si algo no se puede hacer por el caso de uso, la respuesta es
   ampliar el caso de uso, no rodearlo.

2. **El actor sigue siendo una persona.** KAIROS es un `origen`, no un usuario.
   La sesión, el rol y el alcance salen del token como en cualquier otra
   petición; `evento_dominio.origen = 'KAIROS'` es lo que permite responder
   después «quién decidió esto».

3. **Un dato que no se sabe se declara faltante.** No se rellena con el caso
   frecuente, y esto vale doble para un modelo de lenguaje, que es una máquina
   de producir valores plausibles. La operación (`VENTA` / `ALQUILER`) es el
   caso extremo: nunca se infiere, y `OperacionInmobiliaria.desde(null)` ya
   falla con el mensaje correcto.

4. **Nada de LLM todavía si no hace falta para el corte.** El bloque 4 es el
   adaptador y su contrato. Voz, embeddings, memoria vectorial, LangGraph y
   WhatsApp son bloques posteriores y **dependen de este**; abrirlos ahora
   obliga a rehacerlos.

---

## Cómo se cierra

- Fila en `docs/ai/matriz-operacion-rol.md` por cada endpoint nuevo, o
  `MatrizOperacionRolTest` rompe el build.
- Pruebas de integración con `TEST_DB_URL`, declaradas en el inventario de
  `GateDeCierreTest` — si no, JUnit las salta **en silencio** y el reactor
  termina verde sin haberlas corrido.
- Un escenario que demuestre el recorrido conversacional entero contra
  PostgreSQL real: empieza KAIROS, se interrumpe, continúa, ejecuta, y repetir
  el comando no duplica.
- Evidencia en `backend-spring/verificacion/evidencia/`.
- **La corrida de cierre completa la coordinamos**, por la regla 2.

---

## Contexto que conviene leer, en este orden

1. `docs/ai/mapa-ejecucion-brox.md` — dónde estamos.
2. `docs/ai/decision-kairos-contrato-de-acciones.md` (D-K-1) — qué puede hacer.
3. `docs/ai/decision-motor-de-registro.md` (D-E4-2) — el motor que vas a usar.
4. `backend-spring/verificacion/evidencia/2026-08-18-propiedad-universal-y-captura.md`
   — qué se construyó y qué se probó.
5. `CLAUDE.md` — las trampas de esta máquina (JDK 21, API solo en Docker,
   PowerShell 5.1, `mvn -pl` sin `-am`).
