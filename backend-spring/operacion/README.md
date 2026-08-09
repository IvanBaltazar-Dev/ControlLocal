# Operación — respaldo, restauración y persistencia

> **Otro procedimiento vive en esta carpeta:**
> [custodios-y-recuperacion-de-emergencia.md](custodios-y-recuperacion-de-emergencia.md) — quiénes
> son los dos custodios y qué se hace el día que una organización se queda sin ningún administrador
> capaz de gobernar. **Su parte de código (V38) no está construida**, así que hoy lo que aplica de
> ese documento es la sección 2: mantener **dos** administradores operativos para no necesitarlo.

Guía corta para el BLOQUE 1 del [plan maestro](../../docs/ai/plan-maestro-ruta-a-produccion.md):
**persistencia y recuperación operativa**. Todo lo de aquí usa herramientas estándar y gratuitas
(`pg_dump`, `pg_restore`, Docker y PowerShell); no hay servicios de pago ni dependencias externas.

> **La regla que ordena este bloque:** el entregable **no es un archivo de backup**. El entregable
> es una **restauración comprobada**. Un respaldo que nunca se restauró es una creencia, no una
> copia de seguridad.

---

## 1. Respaldo manual

```powershell
powershell -File backend-spring/operacion/respaldo.ps1
```

Genera en `backend-spring/backups/` tres archivos:

| Archivo | Qué es |
|---|---|
| `controllocal_dev_<fecha>.dump` | Respaldo completo en formato **custom** de `pg_dump`: esquema, datos **y** el historial de Flyway |
| `…​.dump.sha256` | Checksum para detectar corrupción |
| `…​.dump.json` | Manifiesto: fecha, base, versión de PostgreSQL, migraciones aplicadas y su máxima, tamaño, checksum y retención |

Parámetros útiles:

```powershell
powershell -File backend-spring/operacion/respaldo.ps1 -BaseDatos controllocal_dev -RetencionDias 30
powershell -File backend-spring/operacion/respaldo.ps1 -Destino D:\respaldos\controllocal
```

**Dos trampas que el script ya evita, y conviene no reintroducir:**

1. **Nunca** redirigir la salida binaria de `docker exec pg_dump` con `>` en PowerShell: reencoda el
   flujo y **corrompe el dump**. El archivo pesa parecido y falla al restaurar. El script vuelca
   dentro del contenedor y trae el archivo con `docker cp`, que mueve bytes sin interpretarlos.
2. `flyway_schema_history.version` es **VARCHAR**: `max(version)` devuelve `'9'` teniendo aplicada
   la **V27**. Se ordena por `installed_rank`.

---

## 2. Restauración verificada

```powershell
powershell -File backend-spring/operacion/restaurar-verificar.ps1
```

Sin argumentos toma el respaldo **más reciente**. Ejecuta **26 comprobaciones** sobre una base
**nueva y desechable**, y la elimina siempre (bloque `finally`, aunque algo falle a mitad):

| Paso | Qué comprueba |
|---|---|
| 0 | El SHA-256 coincide y el archivo empieza por la firma `PGDMP` |
| 1 | Se crea una base **vacía** (0 tablas) |
| 2 | `pg_restore` deja tablas |
| 3 | **Flyway reconoce el historial**: existe la tabla, hay migraciones aplicadas, ninguna fallida y todas conservan checksum |
| 4 | Las **11 tablas críticas** existen, con su conteo de filas |
| 5 | Consultas mínimas: organización activa, credenciales, `persona_rol` sin huérfanos y **tenancy sin nulos** |
| 6 | **Documentos referenciados**: el volumen del almacén existe, es legible, y se contrastan las claves de `foto_propiedad` y `documento_solicitud` contra su contenido |
| 7 | Resultado de **éxito o fallo**, con código de salida 0/1 |

Para inspeccionar la base restaurada en vez de eliminarla:

```powershell
powershell -File backend-spring/operacion/restaurar-verificar.ps1 -ConservarBase
```

> **El paso 6 informa, no exige el 100 %.** Una base restaurada en otro entorno referencia binarios
> que ese entorno nunca tuvo. Lo que sí es un fallo es que el volumen **no exista o no se pueda
> leer**: eso significa que los archivos no son persistentes.

---

## 3. Verificar la integridad de un respaldo suelto

```powershell
Get-FileHash backend-spring/backups/<archivo>.dump -Algorithm SHA256
Get-Content  backend-spring/backups/<archivo>.dump.sha256
Get-Content  backend-spring/backups/<archivo>.dump.json | ConvertFrom-Json
```

Si los dos hashes no coinciden, **el archivo no sirve**: no intente restaurarlo sobre nada.

---

## 4. Dónde viven las copias y cuánto duran

| Concepto | Valor |
|---|---|
| Ubicación por defecto | `backend-spring/backups/` (**fuera** del volumen de PostgreSQL) |
| Retención por defecto | **14 días**; se purga en cada ejecución de `respaldo.ps1` |
| Qué se purga | El `.dump` y sus dos acompañantes (`.sha256`, `.json`) |
| Formato | `pg_dump -Fc` (custom): comprimido y restaurable selectivamente |

**Regla:** un respaldo guardado en el mismo volumen que protege no es un respaldo. Cuando exista
infraestructura productiva, el destino debe estar además **en otra máquina** (BLOQUE 9: *backups
externos*).

`.gitignore` cubre `backend-spring/backups/`: los dumps **no se versionan**.

---

## 5. Persistencia de los binarios

Los documentos y las fotos viven en el volumen con nombre **`controllocal_almacen`**
(`backend-spring_controllocal_almacen` una vez que Compose le antepone el proyecto), montado en
`/var/lib/controllocal/almacen` dentro del contenedor del API.

**Antes de 2026-08-04 esto no existía**: el almacén caía en `./almacen-dev`, relativo al directorio
de trabajo del contenedor, es decir **en su capa de escritura**. Un `docker compose up
--force-recreate` borraba fotos y documentos sin avisar.

Comprobar el contenido:

```powershell
docker run --rm -v backend-spring_controllocal_almacen:/a alpine:3.20 find /a -type f
```

| Operación | ¿Sobreviven los archivos? |
|---|---|
| `docker restart controllocal-api-v2` | **Sí** |
| `docker compose up -d --force-recreate api` | **Sí** (verificado) |
| `docker compose down` + `up -d` | **Sí** (verificado) |
| `docker compose down -v` | **NO** — borra los volúmenes. Es deliberado y por eso hay que evitarlo salvo que se quiera empezar de cero |

---

## 6. Qué hacer si se pierde el servidor

1. **Levantar la infraestructura** en la máquina nueva: Docker y `docker compose up -d postgres`.
2. **Traer el último respaldo válido** y comprobar su checksum (§3). Si no coincide, use el anterior.
3. **Restaurar sobre la base productiva**:
   ```powershell
   docker cp <archivo>.dump controllocal-postgres-v2:/tmp/r.dump
   docker exec controllocal-postgres-v2 psql -U controllocal -d postgres -c "CREATE DATABASE controllocal_dev"
   docker exec controllocal-postgres-v2 pg_restore -U controllocal -d controllocal_dev --no-owner --no-privileges /tmp/r.dump
   ```
4. **Verificar antes de abrir el servicio**: `restaurar-verificar.ps1 -Archivo <archivo>.dump`.
5. **Restaurar los binarios** en el volumen `controllocal_almacen`. ⚠️ **Hoy los binarios no tienen
   copia propia**: `pg_dump` guarda las *claves*, no los archivos. Mientras no exista esa copia, una
   pérdida del servidor **recupera la base pero no los documentos** — es el pendiente inmediato de
   este bloque (§8).
6. **Levantar el API** y comprobar `GET /controllocal/Api/salud`.
7. **Rotar el secreto JWT** si hay cualquier sospecha de que la máquina perdida estuviera
   comprometida (Plan S0 §1.3: la rotación es **coordinada** mientras viva GlassFish).

---

## 7. Automatizar la copia

Programar `respaldo.ps1` con el Programador de tareas de Windows:

```powershell
schtasks /create /tn "ControlLocal respaldo diario" /tr "powershell -File D:\init\ControlLocal\backend-spring\operacion\respaldo.ps1" /sc daily /st 03:00
```

**Y programar también la verificación**, semanal: un respaldo que nunca se restaura no está probado.

```powershell
schtasks /create /tn "ControlLocal verificacion semanal" /tr "powershell -File D:\init\ControlLocal\backend-spring\operacion\restaurar-verificar.ps1" /sc weekly /d SUN /st 04:00
```

---

## 7 bis. Conciliar y migrar los binarios (Bloque 8)

Mover el almacén de disco a un bucket son **dos** operaciones, y la primera es de solo lectura.
Hágala siempre antes: si hay referencias rotas, conviene saberlo **antes** de tocar nada.

```powershell
# 1) CONCILIAR — no escribe nada. Cruza las claves de PostgreSQL con el almacén.
docker run --rm --network backend-spring_default `
  -e SPRING_PROFILES_ACTIVE=dev -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... `
  -e ALMACEN_PROVEEDOR=S3 -e ALMACEN_S3_ENDPOINT=... -e ALMACEN_S3_BUCKET=... `
  -e ALMACEN_DIR=/var/lib/controllocal/almacen `
  -v controllocal_almacen:/var/lib/controllocal/almacen:ro `
  -v ...\controllocal-app-2.0.0-SNAPSHOT.jar:/app/app.jar:ro `
  eclipse-temurin:21-jre-alpine java -jar /app/app.jar `
  --spring.main.web-application-type=none `
  --controllocal.almacen.migracion.modo=conciliar

# 2) MIGRAR — copia lo que falte y verifica releyendo. Cambie solo el `modo`.
```

**Códigos de salida**, pensados para encadenar:

| Código | Significa |
|---|---|
| `0` | Sin averías: cada fila con binario lo tiene en el destino |
| `2` | Hay referencias rotas o fallos de copia — **no apague el origen** |
| `1` | La herramienta no llegó a arrancar (no es un resultado del informe) |

Cuatro propiedades que conviene conocer antes de usarla:

- **Monte el origen de solo lectura** (`:ro`), como en el ejemplo. La herramienta nunca escribe en
  él, y el montaje lo garantiza en vez de prometerlo.
- **No borra el origen.** Terminar la migración no apaga el disco: los archivos se quedan hasta que
  el corte esté verificado, porque el único plan de vuelta atrás que funciona es que los datos
  viejos sigan ahí. Vaciarlo es una decisión aparte y posterior.
- **Es idempotente.** Escribe en la clave exacta y salta lo que ya está. Repetirla tras un corte de
  red reescribe los mismos bytes en el mismo sitio en vez de duplicar objetos.
- **Verifica lo que copia**: relee del destino y compara. Lo que no se puede verificar cuenta como
  *fallo*, no como copiado — un informe que dice "500 copiados" escondiendo uno truncado es peor
  que no tener informe, porque autoriza a apagar el origen.

Dos hallazgos que el informe separa a propósito, porque piden cosas distintas:

- **Referencias rotas** — una fila que apunta a un binario que no está. El usuario ve un hueco donde
  debería estar su DNI. **Migrar no las arregla**: lo que se perdió no vuelve por cambiar de almacén.
- **Huérfanos** — binarios que ya no referencia nadie. **No se migran**, a propósito: copiarlos sería
  llevarse la basura a la casa nueva. Si contienen datos personales, además, deberían borrarse.

> **La lista de columnas es explícita, no deducida.** Son tres y **se llaman distinto**:
> `persona.foto_clave`, `foto_propiedad.clave` y `documento_solicitud.ruta_archivo` — esta última
> **no es una ruta**, es la clave del almacén pese al nombre. Y hay dos que *parecen* de la familia y
> no lo son: `evento_seguridad.clave_valor_hash` y `comision_movimiento.clave_idempotencia`. Buscar
> "clave" en el esquema y migrar lo que salga rompe la auditoría. Si añade una columna nueva que
> guarde una clave, regístrela en `InventarioDeClaves` — el informe dice cuántas miró para que se
> note.

## 8. Lo que este bloque todavía **no** cubre

| Pendiente | Por qué importa |
|---|---|
| **Copia de los binarios del almacén** | `pg_dump` guarda las claves, no los archivos. Una restauración deja la base íntegra y los documentos ausentes |
| **Copia fuera de la máquina** | Hoy el destino por defecto es el mismo disco. Con el servidor perdido, se pierde también el respaldo |
| **Cifrado del respaldo en reposo** | El dump contiene datos personales en claro |
| **Alerta cuando el respaldo falla** | Hoy el fallo solo se ve en el código de salida; sin monitorización, un backup que lleva semanas roto parece uno que funciona |

Los cuatro pertenecen al **BLOQUE 9 — arquitectura productiva**, y ninguno bloquea a S0.
