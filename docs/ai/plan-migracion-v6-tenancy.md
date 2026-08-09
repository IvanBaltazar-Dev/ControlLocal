# Plan de migración V6 — Núcleo multi-tenant (tenant único de legado)

> Ejecuta D-16/D-18/D-20/D-22/D-24/D-25/D-26 de `arquitectura-multitenancy-colaboracion.md`.
> **Alcance deliberadamente acotado**: solo la infraestructura transversal de tenancy, operando aún como
> **tenant único** (organización de legado). Sin RLS activo, sin selección de organización, sin segundo
> tenant real, sin token nuevo, sin colaboración, sin KAIROS, sin F3, sin cambio de plataforma de BD.
> Meta: que F3 nazca sobre una frontera organizacional correcta desde su primera fila.

## 1. Clasificación de tablas (criterio #10 del gate)

**GLOBAL (catálogo compartido, SIN `organizacion_id`):**

| Tabla | Por qué global |
|---|---|
| `entidad_tipo` | Catálogo maestro del vocabulario polimórfico; los códigos son universales |
| `distrito` | Geografía compartida (Miraflores es Miraflores para toda corredora) |

**TENANT-SCOPED (llevan `organizacion_id`, 14 tablas):**

| Grupo | Tablas |
|---|---|
| Identidad | `persona`, `persona_rol`, `credencial_usuario`, `detalle_broker`, `detalle_agente`, `supervision_agente` |
| Auditoría | `historial_estado` (audita entidades de un tenant) |
| Oferta | `propiedad`, `detalle_local_comercial`, `foto_propiedad`, `precio_propiedad`, `publicacion` |
| Proceso | `captacion`, `prospeccion`, `reasignacion_captacion` |

> `credencial_usuario` es tenant-scoped **solo transitoriamente**: en el corte de GlassFish se separa en
> `usuario` (cuenta global, D-22) + `usuario_organizacion` (D-26). En V6 se queda bajo el tenant de legado.

## 2. Unicidades globales → por-organización (criterio #3)

| Tabla | Constraint hoy (global) | V6 (por-org) |
|---|---|---|
| `persona` | `uq_persona_documento (tipo_documento, numero_documento)` | `(organizacion_id, tipo_documento, numero_documento)` |
| `persona` | `uq_persona_correo (correo)` | `(organizacion_id, correo)` |
| `credencial_usuario` | `nombre_usuario UNIQUE` | `(organizacion_id, nombre_usuario)` |
| `detalle_broker` | `codigo_broker UNIQUE` | `(organizacion_id, codigo_broker)` |
| `detalle_broker` | `uq_broker_admin_unico WHERE es_administrador` (un admin GLOBAL) | `(organizacion_id) WHERE es_administrador` (un admin **por org**) |
| `detalle_agente` | `codigo_agente UNIQUE` | `(organizacion_id, codigo_agente)` |
| `propiedad` | `codigo UNIQUE` | `(organizacion_id, codigo)` |
| `captacion` | `codigo_captacion UNIQUE` | `(organizacion_id, codigo_captacion)` |
| `captacion` | `uq_captacion_activa_por_local (id_propiedad) WHERE estado='A'` | `(organizacion_id, id_propiedad) WHERE estado='A'` |
| `prospeccion` | `codigo_prospeccion UNIQUE` | `(organizacion_id, codigo_prospeccion)` |

`distrito.nombre UNIQUE` y `entidad_tipo.codigo PK` **se mantienen globales**.

## 3. Protección de relaciones (criterio #4) — FK compuestas con tenant

Para que un bug de consulta no pueda cruzar tenants, las FK entre tablas privadas incorporan
`organizacion_id`. Requiere un `UNIQUE (organizacion_id, <pk>)` en la tabla referenciada. Ejemplo:

```sql
-- persona gana: UNIQUE (organizacion_id, id_persona)
-- persona_rol: la FK a persona pasa a compuesta
ALTER TABLE persona_rol
  ADD CONSTRAINT fk_persona_rol_persona_org
  FOREIGN KEY (organizacion_id, id_persona) REFERENCES persona (organizacion_id, id_persona);
```

Se aplica a las FK "hijas" clave: `persona_rol→persona`, `propiedad→persona_rol(propietario)`,
`captacion/prospeccion→propiedad` y `→detalle_agente`, `detalle_*→persona_rol`,
`foto/precio/publicacion/detalle_local→propiedad`, `reasignacion→captacion`.

## 4. Orden seguro de aplicación (V6.1–V6.6)

1. **V6.1** — `organizacion` + registro `BROX_LEGACY` ("Organización de legado"; **no** usar `SIVAN`
   como nombre de tenant: SIVAN es la empresa dueña de la plataforma).
2. **V6.2** — `organizacion_id` **NULLABLE** en cada tabla privada → `UPDATE` de backfill al tenant de
   legado → `SET NOT NULL`. (Nunca NOT NULL de golpe.)
3. **V6.3** — reescribir unicidades globales → por-org (§2).
4. **V6.4** — `UNIQUE (organizacion_id, pk)` + FK compuestas con tenant (§3).
5. **V6.5** — `usuario_organizacion` (D-26) + compatibilidad GlassFish: la org activa la fija el backend
   (`= BROX_LEGACY`), **sin `DEFAULT` permanente en la BD** (un default ocultaría código que aún no pasa
   el tenant). Toda escritura nueva provee la organización explícitamente.
6. **V6.6** — modelo de consentimiento D-25 (`finalidad_tratamiento`, `aviso_privacidad_version`,
   `autorizacion_tratamiento_evento`, `evidencia_autorizacion`) — contrato listo; los flujos de KAIROS
   llegan después.

## 5. Trabajo de código acoplado (no es solo SQL)

Como `organizacion_id` es NOT NULL sin DEFAULT, la app debe proveerlo o los INSERT fallan:

- **Entidades JPA**: añadir `organizacion_id` (o `@ManyToOne Organizacion`) a las 14 privadas + nuevas
  entidades `Organizacion`, `UsuarioOrganizacion` y las 4 de consentimiento.
- **`Actor`** gana `idOrganizacion` (constante = legado mientras sea tenant único); lo resuelve el
  backend, **nunca el cliente** (D-20).
- **`Alcances`** filtra por `organizacion_id` **antes** que por rol.
- **Capa service**: al crear cualquier entidad privada, fijar la organización del `Actor`.
- **Aislamiento V6 = discriminador + filtro en app** (D-24); RLS se activa al habilitar multi-tenant real.

## 6. Gate de aceptación (V6 terminada cuando TODO es verdad)

| # | Criterio | Estado | Cómo se comprobó |
|---|---|---|---|
| 1 | Todos los registros V1–V5 pertenecen a la organización de legado | ✔ | Backfill de V6.2; las 16 tablas privadas dan 0 filas con `organizacion_id IS NULL` |
| 2 | Ninguna entidad privada acepta `organizacion_id = NULL` | ✔ | `NOT NULL` sin DEFAULT en BD + `ArquitecturaTenancyTest` (ArchUnit) + guarda `@PrePersist` |
| 3 | Unicidades comerciales acotadas por organización | ✔ | V6.3 + `v6-dos-organizaciones.sql` (6 códigos repetidos entre tenants) |
| 4 | Las relaciones críticas impiden cruces de tenant | ✔ | FK compuestas de V6.4; el script rechaza una captación sobre el local de otro tenant |
| 5 | GlassFish y el frontend funcionan **sin cambiar el token** | ✔ | E2E: el JWT sigue con 3 partes HS256 y **sin** claim de organización |
| 6 | Las altas nuevas reciben el tenant desde el backend | ✔ | E2E: cada fila creada (propiedad, detalle, publicación, precio, prospección, captación, reasignación, auditoría) nace en `BROX_LEGACY` |
| 7 | Prueba de **dos organizaciones técnicas** con códigos repetidos | ✔ | `verificacion/v6-dos-organizaciones.sql` (5/5 comprobaciones) |
| 8 | Rollback probado sobre una copia de la base | ✔ | `verificacion/v6-rollback.sql` sobre `controllocal_rollback_test`: vuelve a la forma V5 con los datos intactos |
| 9 | El E2E del flujo actual (F2) sigue pasando | ✔ | `verificacion/e2e-v6.ps1` — 46/46 |
| 10 | Este documento indica qué tablas son globales y cuáles tenant-scoped (§1) | ✔ | §1 |

> Las dos organizaciones de prueba **no** activan multi-tenancy comercial; solo demuestran que el esquema
> lo soporta. El script corre dentro de una transacción que termina en `ROLLBACK`, así que no ensucia la
> base de desarrollo.

## 7. Estado de ejecución

**V6 COMPLETA y verificada (2026-07-27).** Migración aplicada en la BD de desarrollo (Flyway v6), API
arrancando con `ddl-auto: validate` en verde y los 10 criterios del gate cumplidos.

- [x] Diseño y clasificación (este documento)
- [x] **V6 migración SQL** (`V6__nucleo_multitenant.sql`, V6.1–V6.6)
- [x] Entidades JPA: `@MappedSuperclass EntidadDeOrganizacion` en las 15 privadas + `Organizacion`,
      `UsuarioOrganizacion` y las 4 de consentimiento
- [x] `Actor` gana `idOrganizacion`; lo resuelve `OrganizacionService` y lo ata al request
      `FiltroAutenticacionJwt` vía el principal `SesionDeRequest` (el token NO cambia)
- [x] `Alcances` filtra por organización antes que por rol; el tenant entra en el WHERE de todas las
      consultas de propiedad/captación/prospección/supervisión
- [x] Correlativos `PRO-####` / `CAP-####` por organización (antes contaban global)
- [x] Compilación + unit + ArchUnit verdes: **57 tests** (`mvn clean install`)
- [x] V6 aplicada en Docker + jar repackaged + arranque verificado
- [x] Gate #7 (dos organizaciones), #9 (E2E F2, 46 checks) y #8 (rollback sobre copia)

### Qué sigue (fuera del alcance de V6, por diseño)

- **F3 Demanda** (cliente, oportunidad, interacción, visita, requerimientos/matching): nace ya sobre la
  frontera organizacional, que era el objetivo de V6.
- **RLS**: hoy el aislamiento es discriminador + filtro en la app (D-24). Se activa al habilitar
  multi-tenant real, después del corte de GlassFish.
- **Lecturas hijas de un agregado** (fotos, precios y publicaciones de un local) se alcanzan solo por el
  id del padre, que sí va filtrado por tenant; sus consultas no repiten el filtro. Cuando se active RLS
  quedan cubiertas a nivel de fila.
- **Login por organización**: sigue global a propósito (V6 no introduce selección de organización). Al
  descongelar el token, la organización saldrá de `usuario_organizacion` sin tocar a los llamadores.

> **Nota**: V6 y el código son **un cambio acoplado**. `organizacion_id` es NOT NULL sin DEFAULT (§4,
> V6.5), así que aplicar la SQL sin las entidades/servicios hace fallar todo INSERT.

> **Docker Desktop debe estar levantado** para aplicar la migración y correr el E2E: en esta máquina las
> JVM del harness no pueden con el loopback NIO, así que el API solo corre en contenedor. La compilación
> Maven sí funciona sin Docker.
