# Cierre de la migración — entorno listo para pruebas manuales (2026-08-09)

Segunda corrida del día. La primera produjo `baseline-v2-pre-descongelado` (commit `10f985e`);
esta **no repite la batería E2E** —el árbol no cambió— y se concentra en lo que aquella no
cubría: **el entorno de desarrollo tal y como lo va a usar una persona**.

La distinción importa. Las 22 suites E2E levantan `docker-compose.e2e.yml` con una base
efímera que crean y destruyen. La base `controllocal_dev` y el SPA en el 4200 son **otro
camino**, y ahí es donde apareció lo único que bloqueaba de verdad.

---

## 1. Qué se ejecutó

| Capa | Resultado |
|---|---|
| Reactor backend completo | **BUILD SUCCESS** |
| Tests de integración contra PostgreSQL real | **37 ejecutados, 0 saltados, 0 fallos** |
| Suite Angular | **529 / 529** |
| Suites E2E | **no re-ejecutadas** — decisión explícita, ver §2 |
| Flujos de negocio de punta a punta | verificados contra el entorno dev (§4) |

Los 37 de integración, uno a uno: `BusquedaLocalesIntegrationTest` 12,
`InvariantesComisionIntegrationTest` 6, `OcupacionInmuebleIntegrationTest` 5,
`PadronDeGobiernoIntegrationTest` 5, `RepositorioEstadosIntegrationTest` 4,
`SimulacroRecuperacionIntegrationTest` 4, `VocabularioPersistidoIntegrationTest` 1.

> **Método**: un `mvn ... | tail -120` truncó la primera comprobación y solo dejó ver 3 de los
> 7. Se repitió volcando el log completo a fichero. El mismo error de método —dar por
> ejecutado lo que no se vio— es el que este proyecto ya pagó con V31/V37/V38.

## 2. Por qué no se repitieron las 22 suites E2E

Se corrieron **hoy**, sobre **este mismo commit**, con el árbol limpio y etiquetado, y
salieron 22/22. Volver a correrlas cuesta horas de máquina y habría re-derivado el mismo
resultado sobre el mismo código. La evidencia de `2026-08-09-baseline-v2-pre-descongelado.md`
sigue siendo válida para `10f985e`.

**Cuándo deja de valer**: al primer cambio de código. La evolución módulo a módulo del
contrato descongelado exige su propia corrida.

---

## 3. Lo único que bloqueaba: el admin no podía entrar

`admin@controllocal.test` respondía **401** en `POST /auth/login` en la base de desarrollo.

**No es un bug.** Es D-S0-22 funcionando: una cuenta con segundo factor **no entra por el
camino que no lo pide**, y `/auth/login` devuelve credenciales inválidas a propósito. El SPA
usa `POST /auth/mfa/desafio`, que responde 200 sin factor y 202 + desafío con él.

El problema real es el **estado de la base de dev**, no el código:

| | |
|---|---|
| `factor_autenticacion` id 2, credencial 1 | TOTP **activo** desde el 2026-08-06 08:11 |
| `codigo_respaldo_mfa` | **0 filas** — no hay códigos de rescate |
| `secreto_cifrado` | cifrado con la clave del API; nadie tiene el TOTP |
| `DELETE /mfa` (`revocarPropio`) | exige **sesión iniciada** — que es lo que falta |

Residuo de una prueba de enrolamiento. **Ninguna migración lo siembra**: V37 crea
`organizacion.mfa_gobierno_exigido` con `DEFAULT FALSE`, y el flag se encendió solo al
enrolar. O sea que **factor y flag son el mismo residuo**.

### Por qué revertirlo exige tocar las dos cosas a la vez

`exigir_administrador_operativo()` (V44) impide que la organización se quede sin un
TENANT_ADMIN **con MFA activo** cuando `mfa_gobierno_exigido` es cierto. Revocar solo el
factor lo rechaza —y hace bien—. El trigger es `DEFERRABLE INITIALLY DEFERRED`, así que se
evalúa al COMMIT: bajando el flag y revocando el factor **en la misma transacción**, al
cerrar solo se exige un TENANT_ADMIN activo, que existe.

**Pendiente de ejecución por el titular**: la escritura la bloqueó el clasificador de
permisos, y no se forzó.

```sql
UPDATE organizacion SET mfa_gobierno_exigido = FALSE WHERE id_organizacion = 1;
UPDATE factor_autenticacion SET estado = 'R', revocado_en = now()
 WHERE id_factor = 2 AND id_credencial = 1;
```

Deja la cuenta como la siembra V3 y como la asumen las 22 suites. Después se puede
re-enrolar MFA desde la propia app. **Solo afecta a `controllocal_dev`**; `prod` tiene su
propio validador de arranque.

---

## 4. Flujos verificados de punta a punta

Contra el entorno dev, **por la API real** (ningún INSERT directo), y comprobados después en
el SPA con los dos roles. El recorrido quedó como script re-ejecutable en
`verificacion/generar-datos-demo.sh`, con las cuatro trampas del cable documentadas en su
cabecera. **No es una suite E2E**: escribe, no verifica, y ninguna suite depende de él.

`propietario → local → prospección → captación → decisión del broker → publicación →
cliente → requerimiento → oportunidad → visita → interacción → solicitud → expediente
documental → conformidad → reenvío a revisión → evaluación → contrato → comisión`

La **cascada de cierre funciona**: al registrar el contrato, tres captaciones pasaron a
Cerrada y tres oportunidades a Finalizada exitosa sin intervención, y tres locales quedaron
No disponible.

### Dos cosas que costaron tiempo y conviene no volver a descubrir

1. **`captar` crea la captación como borrador con `exclusividad` en NULL**, y
   `validarActivacion` la exige. El broker **no puede aprobar** hasta que el agente complete
   el encargo con `PUT /captaciones/{id}`. No es un defecto: es el orden que ya sigue
   `e2e-f4-solicitud.ps1`. Sin ese PUT, la decisión responde *"La captacion no puede
   activarse sin condicion economica, moneda, exclusividad, propietario, agente y local."*
2. **`POST /prospecciones/{id}/captar` devuelve la prospección, no la captación**: el id de
   la captación viaja en **`idCaptacion`**, no en `id`. Leer `id` da el de la prospección y
   las llamadas siguientes fallan con *"Captacion no encontrado"* sobre ids que existen pero
   son de otra entidad.
3. **La solicitud nace `G` (Registrada) y el broker evalúa desde `E`**. El puente es
   `POST /solicitudes/{id}/reenviar`; sin él, `G -> A` es transición no permitida.
4. **La comisión pide campos concretos**: `asignar` exige `montoAgente`, y `cobro` exige
   `estado` (`C` o `A`) — no basta con mandar observaciones.

### Datos disponibles ahora en `controllocal_dev`

| Entidad | Filas | | Entidad | Filas |
|---|---|---|---|---|
| propiedad | 21 | | solicitud | 6 |
| persona | 45 | | documento | 35 |
| prospección | 21 | | evaluación | 5 |
| captación | 13 | | contrato | 3 |
| oportunidad | 8 | | alerta | 48 |
| visita | 8 | | interacción | 20 |

Repartidas por estado a propósito, para que los filtros y KPI tengan de dónde tirar:
captaciones 6 activas / 3 cerradas / 2 observadas / 2 rechazadas; solicitudes 3 cerradas /
1 en revisión / 1 observada / 1 rechazada; oportunidades 3 abiertas / 3 finalizadas / 2 con
solicitud; comisiones 2 cobradas y 1 pendiente.

---

## 5. Deudas registradas, ninguna bloqueante

Se dejan para después, como se acordó: primero usar la app, y que las mejoras salgan del uso.

- **Rotación de credenciales** — el commit `2832a9b` publicó el secreto JWT y las credenciales
  RDS en GitHub. El historial las conserva; solo la rotación cierra la exposición. Acción del
  titular. *(Anterior, sigue abierta.)*
- **`locales-busqueda`** — un escenario de ocho (`texto=Lima`) con p95 1088 ms contra un techo
  de 1000. La base aporta 45–120 ms; los ~950 restantes son ajenos a PostgreSQL y apuntan al
  proxy de puertos de Docker Desktop. Falta medir en máquina en reposo. **No subir el umbral.**
- **Recuperación de acceso sin transporte** (D-S0-11) — el endpoint emite el token pero no hay
  canal, así que no llega a nadie. El camino que funciona es la invitación.
- **Break-glass sin activar** (D-S0-53) — faltan los dos custodios reales y el canal externo.
- **Reportes PDF fuera de alcance** (D-F5-1) — sin tecnología de reemplazo elegida.
- **`GET /documentos/contenido` sigue siendo público** — superficie a retirar; el SPA ya no se
  apoya en ella (usa blob autenticado).
- **MinIO encendido sin usarse** — el contenedor corre aunque `ALMACEN_PROVEEDOR` sigue en
  `DISCO`. No molesta, pero es superficie innecesaria: se apaga con
  `docker compose -f backend-spring/docker-compose.yml stop minio`.
- **Duplicados al generar datos** — dos oportunidades y dos solicitudes chocaron contra una
  restricción de unicidad al reutilizar la misma captación. Es comportamiento correcto del
  dominio, anotado solo para que no sorprenda si se relanza el generador.

---

## 6. Cómo queda el entorno

| Servicio | Puerto | Estado |
|---|---|---|
| PostgreSQL v2 (`controllocal_dev`) | 5433 | arriba, migraciones al día (V44) |
| API Spring (perfil `dev`) | 8090 | arriba, `/salud` 200 |
| SPA Angular | 4200 | arriba, login y pantallas verificados |

Credenciales: `rsalas`…`sramirez` / `Broker2026`, `vmora`…`rgomez` / `Agente2026`.
`admin@controllocal.test` / `Admin2026` **tras aplicar el SQL del §3**.

> La primera petición al API tras un rato inactivo tarda bastante (inicialización perezosa
> del `DispatcherServlet` más el JIT). No es un cuelgue: la segunda va a velocidad normal.
