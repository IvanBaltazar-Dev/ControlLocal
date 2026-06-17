# ControlLocal — Guía de despliegue para demo en laboratorio

Esta guía asume que vas a levantar el proyecto **desde cero** en una máquina del
laboratorio (no la tuya), con acceso al RDS de la universidad ya activo.

Tiempo estimado de setup: **15 minutos**.

---

## 1. Pre-requisitos en la máquina del laboratorio

Verifica que estén instalados (abriendo Terminal):

```bash
java --version          # >= 21 (probado con 26)
mvn --version           # >= 3.9
dotnet --version        # >= 8 (probado con 10)
```

Más manualmente:

- **IntelliJ IDEA Ultimate** (no Community, porque despliega en GlassFish).
- **GlassFish 8.x** descargado y descomprimido en alguna ruta accesible
  (por ejemplo `~/glassfish8`).
- **MySQL Workbench** para correr los scripts del seed contra RDS.

---

## 2. Clonar el repositorio

```bash
cd ~/Desktop
git clone https://github.com/IvanBaltazar-Dev/ControlLocal.git
cd ControlLocal
git checkout feature/control-local-core-update   # rama de la entrega
```

---

## 3. Crear los archivos de configuración (NO están en el repo)

El `.gitignore` excluye los archivos con credenciales. Hay tres plantillas
`.example` que tienes que copiar y completar con los valores reales.

```bash
# 1) Credenciales de la base de datos RDS
cp backend-java/controllocal-db-manager/src/main/resources/db.properties.example \
   backend-java/controllocal-db-manager/src/main/resources/db.properties

# 2) Secreto del JWT y CORS del API
cp backend-java/controllocal-rest/src/main/resources/api.properties.example \
   backend-java/controllocal-rest/src/main/resources/api.properties

# 3) Configuración del frontend (URL del backend)
cp frontend-csharp/ControlLocal.Web/appsettings.example.json \
   frontend-csharp/ControlLocal.Web/appsettings.Development.json
```

Luego **edita los dos primeros** y reemplaza los placeholders con los valores
reales. (El tercero ya viene listo, no necesita cambios.)

### `db.properties` — qué llenar

| Clave | Qué poner |
| --- | --- |
| `db.host` | endpoint de la instancia RDS (`prog3-labs-1inf30....rds.amazonaws.com`) |
| `db.port` | `3306` |
| `db.name` | `controllocal` |
| `db.user` | `admin` |
| `db.password` | la password del RDS (consultar con el equipo) |
| `db.ssl` | `true` |

### `api.properties` — qué llenar

| Clave | Qué poner |
| --- | --- |
| `api.environment` | `development` |
| `api.cors.origin` | `http://localhost:5232` |
| `api.token.secret` | un string aleatorio de al menos 32 caracteres (puedes generarlo con `openssl rand -base64 48`) |

---

## 4. Cargar / verificar la base de datos en RDS

Si la BD ya tiene datos del laboratorio anterior, **salta este paso**.
Si la BD está vacía o quieres recargarla desde cero, abre MySQL Workbench
conectado al RDS y corre **en este orden**:

1. `database/00_recreate_database_controllocal.sql` (DROP + CREATE)
2. `database/01_create_schema_controllocal.sql`
3. `database/02_seed_base_data.sql`
4. `database/03_seed_demo_data.sql`

Verifica con esta query rápida:

```sql
USE controllocal;
SELECT
    (SELECT COUNT(*) FROM usuario_interno)        AS usuarios,
    (SELECT COUNT(*) FROM broker)                 AS brokers,
    (SELECT COUNT(*) FROM agente_inmobiliario)    AS agentes,
    (SELECT COUNT(*) FROM broker_agente WHERE estado='A') AS asignaciones,
    (SELECT COUNT(*) FROM captacion)              AS captaciones,
    (SELECT COUNT(*) FROM oportunidad_comercial)  AS oportunidades,
    (SELECT COUNT(*) FROM solicitud_alquiler)     AS solicitudes;
```

Valores esperados (mínimo): usuarios ≥ 5, brokers ≥ 3, agentes ≥ 4,
asignaciones ≥ 4, captaciones ≥ 10, oportunidades ≥ 8, solicitudes ≥ 3.

Si `solicitudes = 0`, corre `database/04_garantia_solicitudes_demo.sql` para
crear las 3 solicitudes demo de forma idempotente.

---

## 5. Compilar el backend

Abre **IntelliJ IDEA** y haz `File → Open` apuntando al folder
`ControlLocal/backend-java`. Espera a que IntelliJ termine de indexar.

En el panel **Maven** (derecha) → click derecho sobre `ControlLocal` (raíz)
→ **Lifecycle → install**.

Espera el mensaje `BUILD SUCCESS`. Esto compila los 6 módulos
(`model`, `dao`, `db-manager`, `bl`, `app`, `rest`) e instala el WAR
final en `backend-java/controllocal-rest/target/controllocal.war`.

---

## 6. Configurar GlassFish en IntelliJ

1. `File → Project Structure → SDKs` → agrega Java 21+ si no está.
2. `IntelliJ Preferences → Build, Execution, Deployment → Application Servers`
   → `+` → **GlassFish Server** → apunta al folder donde descomprimiste
   GlassFish 8.x (por ejemplo `~/glassfish8/glassfish`).
3. Crea una Run Configuration de GlassFish:
   - `Run → Edit Configurations → + → GlassFish Server → Local`.
   - **Server tab**: Application server = el GlassFish recién agregado.
   - **Deployment tab**: `+` → **External Source** → apunta al archivo
     `backend-java/controllocal-rest/target/controllocal.war` generado en
     el paso anterior.
   - **Application context** = `controllocal` (debería autocompletarse).
4. Click en el botón verde de Play. Espera al mensaje
   `Artifact controllocal: Artifact is deployed successfully` en el log.

> **Nota**: gracias a que ahora `db.properties` y `api.properties` se
> empaquetan en el WAR, **no necesitas agregar nada en "VM options"**.

---

## 7. Levantar el frontend

En otra terminal (o desde IntelliJ Rider / VS Code):

```bash
cd frontend-csharp/ControlLocal.Web
dotnet run
```

Espera el mensaje `Now listening on: http://localhost:5232`.

---

## 8. Smoke test antes de presentar

1. Abre `http://localhost:8080/controllocal/Api/salud` en el navegador.
   Debe responder con un JSON `{"estado":"ok",...}`.
2. Abre `http://localhost:5232` en el navegador.
3. Login con `rsalas` / `Broker2026`.
4. Si entras al dashboard, todo está OK.

Si te sale "Credenciales incorrectas":
- Verifica que el archivo `db.properties` existe y tiene la password correcta.
- Verifica en el log de GlassFish que no haya excepciones de conexión.
- Verifica que el RDS sea alcanzable: `mysql -h <host> -u admin -p` desde
  Terminal.

---

## 9. Usuarios disponibles para la demo

| Usuario | Password | Rol | Pantalla con datos |
| --- | --- | --- | --- |
| `admin@controllocal.test` | `Admin2026` | Admin general | Vista global |
| `rsalas` | `Broker2026` | Broker (BRK-001) | Mis agentes, Operaciones del equipo, Solicitudes por evaluar |
| `psoto` | `Broker2026` | Broker (BRK-002) | Captaciones por revisar |
| `vmora` | `Agente2026` | Agente (AGE-001 de rsalas) | Captaciones propias, Solicitudes propias |
| `ltorres` | `Agente2026` | Agente (AGE-003 de psoto) | Captaciones propias |

**Guion sugerido (3 minutos):**

1. Login como `rsalas` → mostrar Mis agentes, Operaciones del equipo,
   Solicitudes por evaluar. Mencionar: *"solo veo mi equipo"*.
2. Cerrar sesión, entrar como `psoto` → mostrar Captaciones por revisar
   (CAP-DEMO-004 Pendiente, CAP-DEMO-005 Observada). Mencionar:
   *"otro broker ve su propia bandeja"*.
3. Cerrar sesión, entrar como `vmora` → mostrar Captaciones (las suyas) y
   Solicitudes de alquiler (la suya). Mencionar:
   *"cada agente solo ve sus propios expedientes"*.

Esto demuestra: autenticación con JWT, autorización por rol, filtrado
multi-tenancy por broker y datos reales desde RDS.

---

## 10. Si algo falla en vivo durante la demo

| Síntoma | Solución rápida |
| --- | --- |
| Pantalla "Solicitudes por evaluar" vacía | Corre `database/04_garantia_solicitudes_demo.sql` en Workbench |
| "Credenciales incorrectas" en el login | Faltan los `db.properties` o `api.properties`. Crearlos según paso 3 |
| Dashboard de equipo no carga | Lo del dashboard es mock (no es parte de la entrega). Pasa directo a las otras pantallas |
| GlassFish dice "Address already in use" | Otra instancia de GlassFish quedó zombie. Abre Activity Monitor → Force Quit el proceso `java` que consume más RAM |
| Backend devuelve 401 en todo | El JWT secret cambió entre el momento del login y el request. Vuelve a loguearte |
