# Operación — respaldo, restauración y almacén

Todo lo de aquí usa herramientas estándar y gratuitas: `pg_dump`, `pg_restore`, Docker y
PowerShell. Ningún servicio de pago.

> **La regla que ordena esta carpeta:** el entregable **no es un archivo de respaldo**. Es una
> **restauración comprobada**. Un respaldo que nunca se restauró es una creencia, no una copia de
> seguridad.

El otro procedimiento de esta carpeta es
[`custodios-y-recuperacion-de-emergencia.md`](custodios-y-recuperacion-de-emergencia.md): qué se
hace el día que una organización se queda sin ningún administrador capaz de gobernarla. **Su
código existe** (V38, `recuperar-gobierno.ps1`, suite `s0-emergencia`) y viene **apagado por
defecto**; lo que falta es la activación —designar a los dos custodios reales y montar el canal
externo—. En `prod`, encenderla sin ambas cosas detiene el arranque. Mientras tanto, lo que
aplica es su sección 2: **mantener dos administradores operativos** para no necesitarlo.

## 1. Respaldar

```powershell
powershell -File backend-spring/operacion/respaldo.ps1
```

Deja tres archivos en `backend-spring/backups/`:

| Archivo | Qué es |
|---|---|
| `controllocal_dev_<fecha>.dump` | Respaldo completo en formato *custom*: esquema, datos **y** el historial de Flyway |
| `….dump.sha256` | Checksum, para detectar corrupción |
| `….dump.json` | Manifiesto: fecha, base, versión de PostgreSQL, migraciones aplicadas, tamaño, checksum y retención |

Admite `-BaseDatos`, `-RetencionDias` y `-Destino`.

**Dos trampas que el script ya evita y conviene no reintroducir:**

1. **Nunca** redirijas la salida binaria de `docker exec pg_dump` con `>` en PowerShell: reencoda
   el flujo y **corrompe el dump**. El archivo pesa casi igual y falla al restaurar. El script
   vuelca dentro del contenedor y trae el fichero con `docker cp`, que mueve bytes sin
   interpretarlos.
2. `flyway_schema_history.version` es **VARCHAR**: `max(version)` devuelve `'9'` teniendo aplicada
   la V27. Se ordena por `installed_rank`.

## 2. Restaurar y verificar

```powershell
powershell -File backend-spring/operacion/restaurar-verificar.ps1
```

Sin argumentos toma el respaldo más reciente. Recorre siete pasos sobre una base **nueva y
desechable**, y la elimina siempre —en un `finally`, aunque algo falle a mitad—. Al terminar
imprime cuántas comprobaciones pasaron sobre el total:

| Paso | Qué comprueba |
|---|---|
| 0 | El SHA-256 coincide y el archivo empieza por la firma `PGDMP` |
| 1–2 | Se crea una base vacía y `pg_restore` deja tablas |
| 3 | **Flyway reconoce el historial**: la tabla existe, hay migraciones aplicadas, ninguna fallida y todas conservan checksum |
| 4–5 | Las 11 tablas críticas existen; organización activa, credenciales, `persona_rol` sin huérfanos y **tenancy sin nulos** |
| 6 | El volumen del almacén existe y es legible, y se contrastan las claves contra su contenido |
| 7 | Éxito o fallo, con código de salida 0/1 |

Con `-ConservarBase` no la elimina, para poder inspeccionarla.

> **El paso 6 informa, no exige el 100 %.** Una base restaurada en otro entorno referencia
> binarios que ese entorno nunca tuvo. Lo que **sí** es un fallo es que el volumen no exista o no
> se pueda leer: eso significa que los archivos no son persistentes.

Para comprobar un respaldo suelto, compara su hash con el `.sha256` que lo acompaña. **Si no
coinciden, el archivo no sirve**: no lo restaures sobre nada.

## 3. Dónde viven las copias

| Concepto | Valor |
|---|---|
| Ubicación | `backend-spring/backups/`, **fuera** del volumen de PostgreSQL, y fuera de git |
| Retención | 14 días; se purga en cada ejecución de `respaldo.ps1` |
| Formato | `pg_dump -Fc`: comprimido y restaurable selectivamente |

**Un respaldo guardado en el mismo volumen que protege no es un respaldo.** Cuando exista
infraestructura productiva, el destino tiene que estar además en otra máquina.

## 4. Los binarios

Documentos y fotos viven en el volumen **`controllocal_almacen`**
(`backend-spring_controllocal_almacen` con el prefijo de Compose), montado en
`/var/lib/controllocal/almacen`. Antes de 2026-08-04 caían en la capa de escritura del contenedor
y un `--force-recreate` los borraba sin avisar.

```powershell
docker run --rm -v backend-spring_controllocal_almacen:/a alpine:3.20 find /a -type f
```

| Operación | ¿Sobreviven? |
|---|---|
| `docker restart controllocal-api-v2` | **Sí** |
| `docker compose up -d --force-recreate api` | **Sí** (verificado) |
| `docker compose down` + `up -d` | **Sí** (verificado) |
| `docker compose down -v` | **NO** — borra los volúmenes. Es deliberado; evítalo salvo que quieras empezar de cero |

## 5. Si se pierde el servidor

1. Levantar Docker y `docker compose up -d postgres` en la máquina nueva.
2. Traer el último respaldo válido y comprobar su checksum. Si no coincide, usar el anterior.
3. Restaurar: `docker cp` el dump al contenedor, crear la base y `pg_restore --no-owner
   --no-privileges`.
4. **Verificar antes de abrir el servicio**: `restaurar-verificar.ps1 -Archivo <archivo>.dump`.
5. Restaurar los binarios en el volumen. ⚠️ **Hoy no tienen copia propia**: `pg_dump` guarda las
   *claves*, no los archivos. Una pérdida del servidor recupera la base **pero no los documentos**.
6. Levantar la API y comprobar `GET /controllocal/Api/salud`.
7. **Rotar el secreto JWT** si hay cualquier sospecha de compromiso. Ya no hay convivencia que
   coordinar: es un cambio de configuración.

## 6. Automatizar

```powershell
schtasks /create /tn "ControlLocal respaldo diario" /tr "powershell -File D:\init\ControlLocal\backend-spring\operacion\respaldo.ps1" /sc daily /st 03:00
```

**Y programar también la verificación**, semanal. Un respaldo que nunca se restaura no está
probado.

## 7. Mover el almacén a un bucket

Son **dos** operaciones y la primera es de solo lectura. Hazla siempre antes: si hay referencias
rotas, conviene saberlo **antes** de tocar nada.

```
--controllocal.almacen.migracion.modo=conciliar   # no escribe nada; cruza claves contra el almacén
--controllocal.almacen.migracion.modo=migrar      # copia lo que falte y verifica releyendo
```

La invocación completa —con el jar, el volumen montado `:ro` y las variables del destino— está en
el bloque de ejemplo de `MigracionAlmacen`.

| Código de salida | Significa |
|---|---|
| `0` | Sin averías: cada fila con binario lo tiene en el destino |
| `2` | Hay referencias rotas o fallos de copia — **no apagues el origen** |
| `1` | La herramienta no llegó a arrancar; no es un resultado del informe |

**Monta el origen de solo lectura** (`:ro`): la herramienta nunca escribe en él, y el montaje lo
garantiza en vez de prometerlo. Tampoco lo borra —los archivos se quedan hasta que el corte esté
verificado, porque el único plan de vuelta atrás que funciona es que los datos viejos sigan ahí— y
es idempotente, así que repetirla tras un corte de red reescribe los mismos bytes en el mismo sitio.

**Verifica lo que copia**, releyendo del destino. Lo que no se puede verificar cuenta como *fallo*,
no como copiado: un informe que dice «500 copiados» escondiendo uno truncado es peor que no tener
informe, porque autoriza a apagar el origen.

El informe separa a propósito dos hallazgos que piden cosas distintas: las **referencias rotas**
—una fila que apunta a un binario ausente— **no las arregla migrar**, y los **huérfanos** no se
migran, porque copiarlos sería llevarse la basura a la casa nueva.

> **La lista de columnas es explícita, no deducida.** Son tres y **se llaman distinto**:
> `persona.foto_clave`, `foto_propiedad.clave` y `documento_solicitud.ruta_archivo` —esta última
> **no es una ruta**, es una clave pese al nombre—. Y hay dos que *parecen* de la familia y no lo
> son: `evento_seguridad.clave_valor_hash` y `comision_movimiento.clave_idempotencia`. Buscar
> «clave» en el esquema y migrar lo que salga rompe la auditoría. Una columna nueva que guarde una
> clave se registra en `InventarioDeClaves`.

## 8. Lo que esto todavía no cubre

| Pendiente | Por qué importa |
|---|---|
| **Copia de los binarios** | `pg_dump` guarda las claves, no los archivos: la restauración deja la base íntegra y los documentos ausentes |
| **Copia fuera de la máquina** | Con el servidor perdido se pierde también el respaldo |
| **Cifrado en reposo** | El dump contiene datos personales en claro |
| **Alerta cuando el respaldo falla** | Hoy el fallo solo se ve en el código de salida; sin monitorización, un backup roto hace semanas parece uno que funciona |

Los cuatro pertenecen a la arquitectura productiva, que no está decidida todavía.
