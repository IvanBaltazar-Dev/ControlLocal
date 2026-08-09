# Evidencia de pruebas — BLOQUES 0, 1 y 2

**BLOQUES 1 y 2 (persistencia, respaldos, perfiles y arranque): 2026-08-04.**
**BLOQUE 0 (autorización de datos personales, D-27): 2026-08-05 — §4 bis.**

Corrida real en la máquina de desarrollo. Cada bloque incluye el comando y su salida literal.
Lo que **no** se pudo comprobar está dicho como tal al final (§6).

- **Entorno**: Windows 11, Docker 29.6.2, PostgreSQL 17.10 en contenedor, JDK 21.0.11 (Temurin),
  Windows PowerShell 5.1.
- **Contenedores**: `controllocal-postgres-v2`, `controllocal-api-v2`.

---

## 1. El archivo sobrevive a la recreación del contenedor

**Criterio de cierre del encargo:** *"un archivo sobrevive a la recreación de la API"*.

### 1.1 Subida por el API real (no copiando a mano en el volumen)

```
POST /locales/1/fotos   (agente vmora, base64, PNG de 67 bytes)
→ {"idFoto":1,"clave":"locales/1/14d5a398-prueba-persistencia.png",
   "nombre":"prueba-persistencia.png","proveedor":"DISCO"}
```

### 1.2 El binario está en el volumen, no en la capa del contenedor

```
$ docker run --rm -v backend-spring_controllocal_almacen:/a alpine:3.20 find /a -type f
/a/locales/1/14d5a398-prueba-persistencia.png
```

### 1.3 Recreación forzada del contenedor del API

```
$ docker compose up -d --force-recreate api
 Container controllocal-api-v2 Started
$ docker inspect -f '{{.Id}}' controllocal-api-v2 | cut -c1-12
0e50074e2c6c          ← contenedor NUEVO

$ curl -o r.png -w "http=%{http_code} bytes=%{size_download} tipo=%{content_type}" \
    ".../documentos/contenido?clave=locales/1/14d5a398-prueba-persistencia.png"
http=200 bytes=67 tipo=image/png

$ head -c 8 r.png | xxd
00000000: 8950 4e47 0d0a 1a0a    .PNG....
```

### 1.4 Ciclo completo `down` + `up` (más duro que un reinicio)

```
$ docker compose down          # SIN -v
 Container controllocal-postgres-v2 Removed
 Network backend-spring_default Removed
$ docker compose up -d
 Container controllocal-api-v2 Started

$ curl -o r2.png -w "http=%{http_code} bytes=%{size_download}" ".../documentos/contenido?clave=..."
http=200 bytes=67
$ cmp r.png r2.png
IDENTICO byte a byte al leido antes
```

**Resultado: ✅** El archivo sobrevive a la recreación del contenedor **y** al ciclo completo de
`down`/`up`, y se recupera **byte a byte idéntico**.

> **Qué cambió para que esto funcione**: `ALMACEN_DIR` pasa a ser una ruta absoluta
> (`/var/lib/controllocal/almacen`) respaldada por el volumen con nombre `controllocal_almacen`.
> Antes caía en `./almacen-dev`, **relativo al directorio de trabajo del contenedor**, es decir en
> su capa de escritura: cualquier recreación lo borraba.

> **El artefacto se retiró el 2026-08-06.** Al recrear la base de desarrollo tras cerrar el Bloque 5
> desapareció la fila `foto_propiedad` que lo referenciaba, y el binario quedó huérfano en el
> volumen. Se eliminó dejando esta constancia: **la evidencia de esta sección son los comandos y sus
> salidas literales, no el archivo**. El `curl` de §1.3 y §1.4 ya no reproduce porque la clave no
> existe; para repetir la prueba hay que volver a subir una foto por el API —que es justamente lo
> que hace §1.1— y seguir desde ahí.
>
> Tras el borrado, el volumen quedó **sin ningún archivo**. Siguen existiendo **cuatro referencias
> del seed de V8** (`documento_solicitud.ruta_archivo` → `SOL-260715103000/*.pdf`) cuyos binarios
> nunca se subieron: son el problema inverso —referencias colgando, no archivos huérfanos— y **no
> se tocan**, porque corregirlas obligaría a editar una migración aplicada.

---

## 2. Respaldo automático de PostgreSQL

```
$ powershell -File backend-spring/operacion/respaldo.ps1

=== Respaldo de PostgreSQL v2 ===
Contenedor : controllocal-postgres-v2
Base       : controllocal_dev
Destino    : D:\init\ControlLocal\backend-spring\backups

-> pg_dump (formato custom, incluye esquema, datos e historial Flyway)...

[OK] Respaldo generado
  Archivo   : controllocal_dev_20260804_233013.dump
  Tamano    : 233.9 KB
  Flyway    : 27 migraciones, maxima 27
  PostgreSQL: 17.10
  SHA-256   : D6A177B07C2C837A01752B0834818B72ABA62B5E7BCCFB5B743E74A347F987CA

-> Retencion: nada que retirar (limite 14 dias)
Respaldos conservados: 2
Duracion: 3.9 s
===== EXIT: 0 =====
```

**Resultado: ✅** Incluye esquema, datos, historial de Flyway, fecha, nombre de base, checksum y
política de retención, y el destino está fuera del volumen de PostgreSQL.

> **Defecto encontrado y corregido durante esta corrida**: la primera ejecución informó
> *"maxima 9"* teniendo aplicada la **V27**. `flyway_schema_history.version` es **VARCHAR**, así que
> `max(version)` compara como texto. Corregido ordenando por `installed_rank`. Es exactamente el
> tipo de error que un manifiesto sin verificar habría arrastrado hasta el día de una restauración.

---

## 3. Restauración automatizada y verificada

```
$ powershell -File backend-spring/operacion/restaurar-verificar.ps1

=== Restauracion verificada de PostgreSQL v2 ===
Respaldo : controllocal_dev_20260804_233013.dump
Base test: controllocal_restauracion_20260804233022

-- 0. Integridad del archivo --
  [OK]    El SHA-256 coincide con el registrado al generarlo
  [OK]    El archivo es un dump custom de PostgreSQL -- firma leida: 'PGDMP'
-- 1. Base vacia --
  [OK]    Se creo la base de verificacion
  [OK]    La base nace vacia -- 0 tablas
-- 2. Restauracion --
  [OK]    La restauracion dejo tablas en la base -- 44 tablas (pg_restore salio 0)
-- 3. Historial de Flyway --
  [OK]    Existe flyway_schema_history
  [OK]    El historial trae migraciones aplicadas -- 27 migraciones, maxima 27
  [OK]    Ninguna migracion quedo marcada como fallida -- fallidas: 0
  [OK]    Todas las migraciones SQL conservan su checksum -- sin checksum: 0
-- 4. Tablas criticas --
  [OK]    organizacion 1 · persona 26 · persona_rol 47 · credencial_usuario 21
  [OK]    propiedad 2 · captacion 1 · oportunidad_comercial 1 · solicitud_alquiler 1
  [OK]    contrato_alquiler 0 · comision_liquidacion 0 · historial_estado 0
-- 5. Consultas minimas --
  [OK]    Hay al menos una organizacion activa -- 1 activas
  [OK]    Hay credenciales restauradas -- 21 credenciales
  [OK]    Ningun persona_rol quedo sin su persona -- huerfanos: 0
  [OK]    Toda persona conserva su organizacion (tenancy) -- sin tenant: 0
-- 6. Documentos referenciados --
  [OK]    Existe el volumen del almacen 'backend-spring_controllocal_almacen'
  [OK]    El almacen es accesible y se pudo listar -- 1 archivos en el volumen
  [INFO]  Binarios referenciados: 5; presentes: 1; ausentes: 4
-- Limpieza --
  Base de verificacion eliminada: controllocal_restauracion_20260804233022

RESULTADO: EXITO  (26/26 comprobaciones)
Duracion: 20.4 s
===== EXIT: 0 =====
```

**Resultado: ✅ 26/26.** La base restaurada es una base **nueva**, Flyway reconoce las 27
migraciones con sus checksums, las 11 tablas críticas están y la integridad referencial y de tenancy
se sostienen.

> **Hallazgo real que destapó la comprobación 6**: la base referencia **5 binarios y solo 1 existe**.
> Los 4 ausentes son documentos del expediente `SOL-260715103000` sembrados por V8: las **filas**
> están, los **archivos** nunca existieron. No es un fallo de la restauración —es dato de semilla—,
> pero es justo la clase de hueco que solo aparece cuando alguien comprueba de verdad los binarios
> referenciados, y anticipa el pendiente de §6: **los binarios no tienen copia de seguridad propia**.

---

## 4. Perfiles y arranque fallido

### 4.1 Perfiles

`application.yml` conserva solo lo común; lo que cambia por entorno vive en
`application-dev.yml`, `application-test.yml` y `application-prod.yml`.

| Perfil | Base | Locations de Flyway | Secreto | Swagger |
|---|---|---|---|---|
| `dev` (por defecto) | `controllocal_dev` local | `migration` + `migration-dev` | fallback permitido, **con WARN** | activo |
| `test` | `TEST_DB_URL`, sin defecto útil | `migration` + `migration-dev` | fallback permitido | activo |
| `prod` | `${DB_URL}` **obligatoria** | `migration` + **`migration-prod`** | **obligatorio** | **apagado** |

Verificado en la corrida: el contenedor arranca con `SPRING_PROFILES_ACTIVE=dev` y responde
`GET /salud` → `{"estado":"ok"}`.

### 4.2 Arranque fallido — comprobado **de verdad**, no solo con tests

```
$ java -jar controllocal-app/target/controllocal-app-2.0.0-SNAPSHOT.jar --spring.profiles.active=prod

java.lang.IllegalStateException: El perfil 'prod' esta activo pero faltan 6 variable(s) de entorno obligatoria(s):
  - DB_URL  -> URL JDBC de PostgreSQL (jdbc:postgresql://host:5432/base)
  - DB_USER  -> usuario de la base de datos
  - DB_PASSWORD  -> contrasena de la base de datos (no la del compose de desarrollo)
  - API_TOKEN_SECRET  -> secreto de firma del JWT, >= 32 caracteres (openssl rand -base64 48)
  - CORS_ORIGENES  -> origen exacto del SPA, sin comodines ni localhost
  - ALMACEN_DIR  -> ruta absoluta del almacen de binarios sobre un volumen persistente

El arranque se detiene a proposito (D-S0-20). En 'dev' estas variables tienen valores por
defecto; en 'prod' no, para que un despliegue mal configurado no levante en silencio.
	at com.controllocal.app.arranque.ComprobacionVariablesObligatorias...
```

**Resultado: ✅** El proceso **muere**, y el mensaje nombra **las seis** variables con su propósito.

> **Defecto de diseño encontrado y corregido durante esta corrida.** La primera versión dejaba la
> comprobación para cuando el contexto ya estaba montado, y entonces el primer bean en reventar era
> el DataSource: el operador veía `'url' must start with "jdbc"`, que es cierto pero **no dice que
> falta `DB_URL`**. Se añadió `ComprobacionVariablesObligatorias`, un
> `ApplicationListener<ApplicationEnvironmentPreparedEvent>` registrado en `META-INF/spring.factories`
> que corre **antes de que exista ningún bean**. Un arranque que falla sin nombrar la variable acaba
> resolviéndose desactivando la comprobación, que es justo lo que D-S0-2 quiere evitar.

### 4.3 Las nueve reglas del validador

```
$ mvn -pl controllocal-app test -Dtest=ValidadorConfiguracionSeguridadTest
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0

$ mvn -pl controllocal-app test -Dtest=ArranqueProduccionTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Nueve reglas, cada una con su test, y todas nombran la variable a corregir:

| Regla | Hallazgo que cierra |
|---|---|
| `API_TOKEN_SECRET` ausente, corto o igual al de desarrollo | **H-01 (crítica)** |
| `CORS_ORIGENES` vacío, con `*` o con `localhost` | superficie de desarrollo abierta |
| Swagger / `/v3/api-docs` accesibles | H-13 |
| `DB_PASSWORD` = `controllocal` | H-17 |
| `DB_URL` apuntando a `localhost` | valores de desarrollo en producción |
| `ALMACEN_DIR` ausente, relativo o no escribible | **pérdida silenciosa de documentos** |
| Credenciales con hash del seed | **H-03 (crítica)** |
| Contraseñas compartidas entre cuentas | H-03 ampliado |
| Organización sin administrador activo | H-04 |

El test `elMensajeDeFalloLosEnumeraTodos` fija que el mensaje enumere **los nueve a la vez**: si
solo dijera el primero, arreglar un despliegue costaría nueve intentos.

> **Corrección durante la corrida**: mi primera versión del test esperaba 8 hallazgos y el validador
> reportó 9. El validador tenía razón; el test estaba mal contado.

---

## 4 bis. BLOQUE 0 — Autorización de datos personales (D-27, 2026-08-05)

### 4bis.1 V28 aplicada, reutilizando las estructuras de V6

```
$ psql -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 2"
28|autorizacion datos personales|t
27|atribucion historica cierre|t

$ psql -c "SELECT codigo, estado, requiere_consentimiento, permite_revocacion FROM finalidad_tratamiento"
ANALITICA_AGREGADA   |I|t|t
MEJORA_MODELOS       |I|t|t
OPERACION_SERVICIO   |A|t|t     ← la UNICA activa, cubre los cinco ambitos
PROSPECCION_COMERCIAL|I|t|t
RED_COLABORATIVA     |I|t|t

$ psql -c "SELECT version, cambio_material, vigente_hasta IS NULL AS vigente FROM aviso_privacidad_version"
1.0|f|t
```

**Cero tablas nuevas.** V28 solo ajusta el catálogo, añade dos columnas
(`registrada_por`, `motivo_revocacion`), una bandera (`cambio_material`) y siembra el aviso 1.0.

### 4bis.2 Sin autorización no queda **ningún** dato identificable

```
$ POST /clientes  {... "consentimientoUsoDato": false, "canalAutorizacion": "PRESENCIAL"}
{"error":"Sin la autorizacion de la persona para el registro y uso de sus datos no se puede
completar el alta."}
http=400

$ psql -c "SELECT count(*) FROM persona WHERE numero_documento = '85908109'"
0        ← la transaccion revirtio: no hay persona, ni marcada ni sin marcar
```

### 4bis.3 Con autorización, el backend rellena todo lo demás

```
$ POST /clientes  {... "consentimientoUsoDato": true, "canalAutorizacion": "WHATSAPP"}
{"id":248, ... }
http=201

$ psql -c "SELECT a.finalidad_codigo, a.evento, a.base_juridica, a.version_aviso,
           a.registrada_por, e.canal FROM autorizacion_tratamiento_evento a
           JOIN evidencia_autorizacion e ON e.id_evidencia = a.id_evidencia ..."
OPERACION_SERVICIO|OTORGADO|CONSENTIMIENTO|1.0|28|WHATSAPP
```

El agente eligió **una sola cosa** (el canal). Finalidad, evento, base jurídica, versión del aviso,
quién lo registró y la fecha los puso el servidor.

### 4bis.4 Página pública, leída **sin sesión**

```
$ curl http://localhost:8090/controllocal/Api/aviso-privacidad     (SIN token)
{"version":"1.0","vigenteDesde":"2026-08-05T05:33:58Z","cambioMaterial":false,
 "contenido":"Aviso de privacidad BROX v1.0. Finalidades: (1) gestion comercial..."}
```

Y la pantalla, verificada en navegador en `http://localhost:4200/privacidad` **sin iniciar sesión**:
rinde las cinco secciones (finalidades, conservación, seguridad, derechos, canales), muestra
*"Versión 1.0 · vigente desde 05 ago. 2026"* y cita el texto registrado.

### 4bis.5 Pruebas

```
$ mvn -pl controllocal-service test -Dtest=AutorizacionesTest
Tests run: 18, Failures: 0   → alta autorizada (3) · rechazo sin persistencia (4)
                               versionado del aviso (5) · revocación (6)

$ ng test --watch=false --browsers=EdgeHeadlessCI
TOTAL: 473 SUCCESS           → 469 anteriores + 4 de la sección de autorización
```

Las 4 de pantalla fijan lo que no se puede volver a introducir: **una sola casilla** (no dos), el
consentimiento de contacto **derivado**, el enlace al aviso presente, el canal como único
desplegable, que sin la casilla **no se llama al backend**, y que **en edición no se vuelve a
pedir**.

### 4bis.6 Corrección del 2026-08-05: propietario y página corporativa

**Dos cambios, y una decisión que cierra una puerta.**

**1. El alta de propietario estaba rota y ya no lo está.** El backend exigía la autorización en las
dos altas, pero la sección solo se había añadido al formulario de cliente. Verificado contra el API
real:

```
$ POST /propietarios  {... "consentimientoUsoDato": false}
{"error":"Sin la autorizacion de la persona ... no se puede completar el alta."}  http=400

$ POST /propietarios  {... "consentimientoUsoDato": true, "canalAutorizacion": "LLAMADA"}
{"id":249, ...}                                                                   http=201

$ psql → OPERACION_SERVICIO | OTORGADO | 1.0 | LLAMADA
```

La sección es **la misma que en cliente, palabra por palabra**: casilla, canal y enlace. En edición
no aparece.

**2. La página pública pasa a ser la versión corporativa aprobada.** Se retiraron los detalles
técnicos (mención de PBKDF2), las instrucciones extensas de eliminación, el **texto interno que se
guarda como evidencia** y las referencias a funcionalidades futuras. Comprobado en navegador, sin
sesión:

```
version    : "Versión 1.0 · Vigente desde el 5 de agosto de 2026"
correo     : "mailto:sivansolutionsgo@gmail.com"
copyright  : "© 2026 BROX. Gestión responsable de la información."
botones    : 0
secciones  : Nuestro compromiso · Para qué utilizamos la información ·
             Protección de la información · Conservación · Actualizaciones del aviso ·
             Consultas y atención sobre privacidad
```

De la llamada al API solo se toman **versión y fecha de vigencia**; el contenido es el corporativo.
Si el API no responde, la página **abre igual** con la versión publicada y **sin banner de error**.

**3. No hay autoservicio de revocación**: ni pantalla, ni botón (`botones: 0`), ni endpoint público.
Las solicitudes llegan al correo oficial y se atienden administrativamente. Lo que **sí** se sigue
registrando internamente: versionado del aviso, `cambio_material`, actor, fecha, canal y tenant.

**Comprobaciones focalizadas** (alcance acotado a propósito: no se corrió el reactor, ni la suite
Angular completa, ni los E2E — el cambio no toca componentes transversales):

```
$ ng test --include='**/propietario-form/*.spec.ts' --include='**/privacidad/*.spec.ts'
TOTAL: 10 SUCCESS      → 5 del formulario de propietario + 5 de la página

$ ng build
Application bundle generation complete. [35.062 s]
```

Las 5 de la página fijan lo retirado, que es lo que tiende a volver: **no** publicar el texto de
evidencia, **no** ofrecer revocación, **no** mencionar detalles técnicos, y abrir igual sin backend.

> **Gotcha de esta máquina**: exportar `CHROME_BIN` hace que Karma intente arrancar **también**
> ChromeHeadless; cuando ese lanzador se rinde, tumba el servidor y Edge se desconecta a mitad
> (`TOTAL: 0 SUCCESS` sin ningún test fallido). `karma.conf.js` ya detecta Edge solo:
> **correr sin `CHROME_BIN`**.

### 4bis.7 Corrección del 2026-08-05 (tarde): **fuera el canal de autorización**

> Esto **supersede** el desplegable que aparece en 4bis.2, 4bis.3 y 4bis.6: aquellos bloques son el
> registro de lo que se verificó ese día, no la descripción del API de hoy. El `canalAutorizacion`
> que se ve en esos `POST` **ya no existe en el request**.

**Lo que cambia.** El agente elegía entre seis opciones para describir la pantalla en la que ya
estaba: fricción sin información. El canal **se sigue registrando** —`evidencia_autorizacion.canal`
es `NOT NULL`— pero lo sella el backend con el valor técnico `FORMULARIO_BROX`. La sección del
formulario pasa a tener **dos elementos**: la casilla y el enlace al aviso.

**Cero migraciones.** `canal` es `VARCHAR(20)` sin `CHECK`, así que el valor entra tal cual y la
estructura queda preparada para el día en que existan otros caminos de entrada (WhatsApp, portal del
titular), cada uno sellando el suyo.

Retirado del cable: `ClienteRequest.canalAutorizacion` y `PropietarioRequest.canalAutorizacion`, los
dos **aditivos**, nunca parte de la v1. Un cliente que todavía los mande no rompe: Jackson ignora lo
que no conoce (`FAIL_ON_UNKNOWN_PROPERTIES` está desactivado por defecto en Spring Boot).

```
$ mvn -f backend-spring/pom.xml clean install
[INFO] BUILD SUCCESS   → servicios 448 | web 5 | aplicacion 45 (16 skipped: integración con BD viva)
                         los cuatro gates verdes, matriz operación→rol incluida

$ ng test --watch=false
TOTAL: 483 SUCCESS

$ ng build
Application bundle generation complete. [25.707 s]
```

`AutorizacionesTest` queda en **17** (3 alta · 3 rechazo · 5 versionado · 6 revocación): se fueron
las dos que validaban canal vacío y canal inventado —ya no hay canal que validar— y entró una que
fija que **la casilla es el único motivo por el que el alta puede caerse**. La que comprobaba la
evidencia ahora afirma que el canal grabado es `FORMULARIO_BROX` **sin que nadie lo haya elegido**.
En las dos suites de pantalla, la aserción "hay un `select` de canal" se invirtió: ahora fija que
**no** lo haya.

### 4bis.8 Cierre del Bloque 0: la constancia en las dos fichas (2026-08-05)

**Endpoints nuevos, no campos nuevos.** `GET /clientes/{id}/autorizacion` y
`GET /propietarios/{id}/autorizacion`. Ampliar `ClienteResponse` habría separado del cable v1 una
respuesta congelada; un endpoint aditivo no toca nada. Los dos llevan **su fila en la matriz** —el
gate no admite endpoint sin fila— y el **mismo alcance** que `GET /{id}`, resuelto por el mismo
`cargarConAcceso`.

La consulta va por **`persona.id`, no por el id del rol**: la autorización la dio la persona una vez
y cubre todos sus roles.

```
$ curl http://localhost:8090/controllocal/Api/salud                       -> 200
$ curl http://localhost:8090/controllocal/Api/clientes/1/autorizacion     -> 401  (sin token)
```

Lo que la ficha muestra: **estado, fecha y hora, quién la registró** y la **versión del aviso solo
cuando difiere de la vigente** — que es la lectura operativa de *"solo si aporta valor"*: si
coinciden es ruido; si no, dice que esa persona autorizó contra un aviso anterior. **El canal no se
muestra**, igual que ya no se pregunta.

Dos conductas fijadas con test porque son las que se pierden al retocar:

- **`SIN_REGISTRO` ≠ "no autorizó".** Las personas anteriores a D-27 no tienen evento, y la ficha lo
  dice con esas palabras en vez de insinuar una negativa que nunca ocurrió.
- **Un fallo del endpoint no tumba la ficha comercial** y **no se pinta como "sin autorización"**.

### 4bis.9 Preparación del almacenamiento para S3 (sin MinIO, sin migrar nada)

Alcance deliberadamente corto: **solo el formato de la clave**. Las claves nuevas cuelgan de
`tenant/{organizacionId}/` vía `AlmacenDocumentos.carpetaDeTenant`, en los tres puntos de subida
(fotos de local, foto de perfil, documentos del expediente). Se hace **ahora y no al migrar** porque
cambiar el formato *después* de mover los binarios obligaría a moverlos dos veces.

Lo que **no** se ha tocado, a propósito: no hay MinIO, no existe `AlmacenS3`, no se ha migrado ni un
binario, el proveedor sigue siendo `DISCO` y `GET /documentos/contenido` sigue público — su cierre
es del bloque de corte y seguridad de documentos.

Verificado por `AlmacenDiscoTest` (7): prefijo presente, dos organizaciones sin prefijo compartido,
ciclo subir/leer/eliminar, **clave antigua sin prefijo que se sigue leyendo** (no hay migración),
archivo inexistente, anti path-traversal y proveedor `DISCO`.

```
$ mvn -f backend-spring/pom.xml clean install
[INFO] BUILD SUCCESS   → servicios 454 | web 12 | aplicacion 45 (16 skipped: integración con BD viva)
                         los cuatro gates verdes; la matriz pasa con las 2 filas nuevas (152 ops)

$ ng test --watch=false
TOTAL: 494 SUCCESS     → 483 anteriores + 5 (ficha de cliente) + 6 (ficha de propietario)

$ ng build
Application bundle generation complete. [27.299 s]
```

> **No verificado en navegador**: la ficha exige sesión iniciada y no introduzco contraseñas. Lo que
> sí ejercita la plantilla real es la suite de componente, que monta el componente y afirma los
> textos exactos ("Autorización registrada", el nombre, la fecha en `es-PE`, ausencia de "Canal").

### 4bis.10 Lo que este bloque deja pendiente

| Pendiente | Consecuencia hoy |
|---|---|
| ~~Fichas de cliente y propietario~~ | ✅ **cerrado** el 2026-08-05 (§4bis.8) |
| Revocación administrativa | `Autorizaciones.revocar` existe y está probado, pero **por decisión no se expone**: las solicitudes llegan al correo oficial y hoy se aplican por SQL. Falta el procedimiento operativo escrito |

---

## 4 ter. Seguridad de sesiones — V29, invalidación y logout real (D-S0-12, 2026-08-05)

> **Vocabulario.** El bloque se llamaba *"Revocación, auditoría y bloqueo"* y se renombró a
> **"Seguridad de sesiones, auditoría y bloqueo de accesos"**. Aquí «revocar» significa **invalidar
> sesiones de usuario**, nunca la autorización de datos personales — que queda cerrada como
> constancia única del alta y **sin flujo de revocación**.

### 4ter.1 El hallazgo que abarata la pieza

El JWT **ya lleva `iat`, y lo emiten los dos backends**. Basta comparar ese instante contra una
marca por credencial para matar todas las sesiones vivas de una cuenta **sin tocar el formato del
token**, que sigue congelado mientras GlassFish conviva.

```
$ psql -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"
29|invalidacion sesiones|t

$ psql -c "\d credencial_usuario"  (extracto)
sesiones_invalidas_desde | timestamp with time zone | nullable
```

**Alcance estrecho a propósito.** El Plan S0 metía en V29 también la expansión de identidad
(`persona_rol` con `ADMIN`, `usuario_organizacion` con `TENANT_ADMIN`/`PLATFORM_ADMIN`), que
pertenece al bloque de roles y gobierno y está **bloqueado por D-S0-17**. Escribirlo aquí habría
atado este bloque a una decisión que no está tomada. Las columnas de contraseñas van con su bloque.

### 4ter.2 Verificado contra PostgreSQL real

```
$ powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite s0-sesiones
  OK   el token lleva iat (base de D-S0-12)
  OK   el token sigue SIN llevar la organizacion (contrato congelado)
  OK   con el token recien emitido, GET /clientes responde 200
  OK   POST /auth/logout responde 204
  OK   la credencial quedo sellada en la BD
  OK   el token anterior al logout responde 401
  OK   el mensaje es el congelado y no revela el motivo
  OK   el token nuevo (emitido despues) SI vale
  OK   y el viejo sigue muerto
  OK   la sesion del broker sigue intacta
  OK: 11    FALLAS: 0
```

Lo que esa corrida demuestra y antes no se podía demostrar: **un token bien firmado y sin expirar
deja de servir** en cuanto la cuenta cierra sesión. Hasta ahora "cerrar sesión" era
`localStorage.removeItem` y el token seguía siendo válido hasta caducar.

### 4ter.3 Decisiones de esta entrega

| Decisión | Por qué |
|---|---|
| **Sin caché de la marca** | El Plan S0 admitía 30–60 s para ahorrar una lectura por request. Se descarta: abriría una ventana en la que una sesión revocada sigue viva, que es justo el fallo que la pieza cierra. Es una proyección de una columna sobre claves primarias; la caché es la palanca si la sonda de transporte la señala, no el punto de partida |
| **El logout cierra TODAS las sesiones de la cuenta** | Sesiones individuales exigirían un `jti` que no cabe en el token congelado. El SPA lo dice en el `title` del botón en vez de prometer otra cosa |
| **`cerrarSesion()` no llama al servidor** | Es también el camino del 401 del interceptor: pedir el logout con un token ya inválido devolvería otro 401 y realimentaría el mismo cierre. La salida deliberada usa `salir()` |
| **El aviso al servidor es best-effort** | Si la red falla, se limpia igual. Dejar al usuario atrapado porque el servidor no contestó es peor que un token que caduca solo en 30 min |
| **Un token sin `iat` se lee como `EPOCH`** | No se exige el claim, para no romper el SSO si el otro backend lo omitiera; ausente = "emitido hace siempre", así que cualquier invalidación lo mata. Falla del lado seguro |

**Borde conocido y aceptado:** `iat` tiene precisión de segundo, así que un login que ocurra
**dentro del mismo segundo** que un logout nace invalidado. Falla del lado seguro y con tokens de 30
minutos no tiene consecuencia práctica.

**Gotcha de PS 5.1 anotado en el script:** tras un error de `Invoke-RestMethod` el stream de la
respuesta viene consumido y `GetResponseStream()` devuelve vacío — el cuerpo está en
`$_.ErrorDetails.Message`. Costó un falso negativo en la primera corrida.

```
$ mvn -f backend-spring/pom.xml clean install
[INFO] BUILD SUCCESS   → servicios 454 | web 23 | aplicacion 45 (16 skipped)
                         matriz operación→rol verde con la fila de /auth/logout (153 ops)

$ ng test --watch=false
TOTAL: 497 SUCCESS
```

### 4ter.4 Lo que falta del bloque

| Pieza | Estado |
|---|---|
| V30 — `evento_seguridad` append-only, ~20 tipos, test de higiene | ⬜ siguiente |
| Bloqueo por **cuenta e IP** sobre `intento_acceso` (D-S0-21) | ⬜ |
| Regresión de los 13 E2E con umbrales de perfil `test` | ⬜ — el propio plan avisa de que el bloqueo por cuenta puede tumbarlos |

---

## 5. Regresión del reactor

```
$ mvn clean install
[INFO] ControlLocal v2 - dominio ......... SUCCESS
[INFO] ControlLocal v2 - persistencia .... SUCCESS
[INFO] ControlLocal v2 - servicios ....... SUCCESS
[INFO] ControlLocal v2 - web ............. SUCCESS
[INFO] ControlLocal v2 - aplicacion ...... SUCCESS
[INFO] BUILD SUCCESS

TOTAL reactor -> Tests: 499 | Failures: 0 | Errors: 0 | Skipped: 16
```

De esas 499, **35 son nuevas**: 15 de `ValidadorConfiguracionSeguridadTest`, 2 de
`ArranqueProduccionTest` y 18 de `AutorizacionesTest`.

> **Ojo al contar**: las clases con `@Nested` reportan `tests="0"` en su `.txt` y en el atributo
> del XML, asi que sumar esa linea **subestima el total**. El numero fiable sale de contar los
> elementos `<testcase>`:
> `grep -rhc "<testcase" */target/surefire-reports/TEST-*.xml`.

Los **4 gates estructurales** siguen verdes: capas (3), auditoría de transiciones (1), tenancy (2) y
**matriz operación→rol (4)**. `TokenServiceTest` **5/5** tras el cambio de `TokenService`
(que ganó `usandoFallbackDeDesarrollo()` y `esFallbackDeDesarrollo(...)`, sin tocar el formato del
token ni el contrato).

**Los 16 saltados son los tests de integración sin `TEST_DB_URL`**, y se saltan **en silencio** por
diseño (`@EnabledIfEnvironmentVariable`). Se declara aquí a propósito: un `mvn test` verde sin esa
variable **no ha verificado ninguna consulta SQL nativa**. Es lo primero que debe configurar la CI
del BLOQUE 9.

---

## 6. Lo que esta corrida **no** demuestra

Dicho explícitamente para que nadie lo dé por cubierto:

| No verificado | Por qué |
|---|---|
| **Que un `prod` con variables presentes pero INSEGURAS no arranque** | Se probó que falta de variables detiene el arranque (§4.2) y que las nueve reglas del validador funcionan (§4.3), pero **no se levantó un contexto `prod` contra una base real** con, por ejemplo, los hashes del seed puestos. Eso es el escenario **A8** completo y pertenece a `e2e-s0-seguridad.ps1` |
| **Copia de seguridad de los binarios** | `pg_dump` guarda las claves, no los archivos. Una restauración deja la base íntegra y **los documentos ausentes** |
| **Respaldo fuera de la máquina** | El destino por defecto es el mismo disco |
| **Los 13 scripts de `verificacion/`** | No se ejecutaron en esta tanda. El riesgo es bajo —`docker-compose.e2e.yml` inyecta `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ORIGENES` y `ALMACEN_DIR` como variables, que es exactamente el patrón que el perfil `dev` sigue leyendo, y el contenedor de desarrollo arrancó así— pero la regresión completa exige correrlos |
| **El perfil `test` no lo usa nadie todavía** | `application-test.yml` existe y está listo, pero `docker-compose.e2e.yml` **no declara `SPRING_PROFILES_ACTIVE`**, así que las suites siguen corriendo en `dev`. Cambiarlo es una línea, pero **relaja el límite de login de 10 a 100 por minuto**, y eso altera la regla operativa de "esperar un minuto entre corridas". Es una decisión de quien opera los E2E, no un descuido |
| **La suite Angular** | No se tocó el SPA |
| **Restauración sobre una máquina distinta** | La prueba corrió contra el mismo contenedor de PostgreSQL |
