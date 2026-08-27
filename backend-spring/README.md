# BROX Core — la API

Spring Boot 3.5 sobre PostgreSQL/PostGIS. Reactor Maven de cinco módulos con las capas
blindadas por ArchUnit: **`app → web → service → persistence → domain`**, y ninguna flecha
al revés.

| Módulo | Qué contiene |
|---|---|
| `controllocal-domain` | Entidades JPA, enums y contratos del dominio |
| `controllocal-persistence` | Repositorios Spring Data + paquete `query` (SQL nativo con alcance) |
| `controllocal-service` | Casos de uso `@Transactional`, reglas y excepciones de negocio |
| `controllocal-web` | `@RestController`, DTOs, Spring Security/JWT y OpenAPI |
| `controllocal-app` | Arranque (fat jar), `application-*.yml`, migraciones Flyway y tests de arquitectura |

## Arrancar

```bash
mvn -f backend-spring/pom.xml clean install
```

```bash
docker compose -f backend-spring/docker-compose.yml up -d
```

Base URL `http://localhost:8090/controllocal/Api`; Swagger en `/swagger-ui.html`. Públicos:
`GET /salud` y el login. La base `controllocal_dev` escucha en `localhost:5433` y Flyway la
migra al arrancar.

Almacenamiento S3-compatible (MinIO) opcional, detrás de un perfil de Compose:

```bash
docker compose -f backend-spring/docker-compose.yml --profile s3 up -d minio minio-init
```

### Cuatro cosas que cuestan tiempo si no se saben

- **`mvn -pl controllocal-app install` SIN `clean` no reempaqueta el fat jar.** Maven dice
  `BUILD SUCCESS` e `Installing …app.jar`, pero el fichero conserva su fecha *y su tamaño*: el
  contenedor sigue sirviendo el código anterior y un endpoint recién escrito responde 404. La
  señal es el tamaño del jar. Usa siempre `clean install` en ese módulo.
- **`mvn -pl X` sin `-am` compila contra el jar *instalado* de sus dependencias**, no contra
  tus fuentes editadas. De ahí los «cannot find symbol» de un método que acabas de escribir.
- **Tras tocar una migración hay que reempaquetar** —Flyway lee el classpath, no el árbol de
  fuentes— y reiniciar el contenedor con `docker restart controllocal-api-v2`.
- **Los servidores Java no arrancan desde sesiones de agente en esta máquina**: las JVM
  descendientes del harness no pueden crear el pipe loopback del selector NIO. Corre la API en
  Docker o desde IntelliJ.

## Lo que no se rompe

Cuatro puertas rompen el build, y están así a propósito:

| Puerta | Qué exige |
|---|---|
| Capas estrictas | `app → web → service → persistence → domain` |
| `service/soporte/Transiciones` | **Único** punto que muta el estado de un `Transicionable` y emite `historial_estado` |
| Discriminador de tenant | Toda entidad privada hereda `EntidadDeOrganizacion` (`organizacion_id` NOT NULL, sin DEFAULT) |
| Matriz operación→rol | [`MatrizOperacionRolTest`](controllocal-app/src/test/java/com/controllocal/arquitectura/MatrizOperacionRolTest.java) contrasta la matriz contra los controladores: **un endpoint nuevo necesita su fila** — método, ruta, roles y dónde se decide el alcance |

Y tres puntos de escritura únicos que **no se rodean**:

- **`EventosSeguridad`** es el único que escribe `evento_seguridad`, append-only y con una
  transacción por evento: un login fallido queda registrado aunque la operación que lo provocó
  termine lanzando.
- **`ContrasenaServiceImpl.fijarContrasena`** es el único sitio donde cambia una contraseña. Son
  cinco efectos que no pueden desparejarse: política, archivo del hash saliente, hash nuevo con
  fecha, descapar la cuenta e invalidar todas las sesiones.
- **`FiltroAutenticacionJwt`** comprueba *por petición* si la sesión fue revocada y si está capada
  por contraseña temporal. Sin caché: un TTL de 30 s dejaría viva una sesión ya revocada, que es
  justo el fallo que la pieza cierra.

> **Nadie fija la contraseña de otra persona.** Ni el administrador, ni el broker. El titular la
> cambia sabiendo la anterior, o la define canjeando un token de un solo uso. La temporal que
> genera el sistema es la única excepción y nace capada. No añadas un endpoint donde alguien
> elija la clave de otro.

## La corrida de cierre

**`mvn clean install` NO es un gate.** Los tests de integración llevan
`@EnabledIfEnvironmentVariable(TEST_DB_URL)` y sin esa variable JUnit **los salta en silencio**
con el build en verde. Así entraron tres columnas `estado` en palabra completa, rompiendo el
invariante de código unitario, durante un bloque entero.

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_repositorios CONTROLLOCAL_CIERRE=1 mvn -f backend-spring/pom.xml clean install
```

`TEST_DB_URL` habilita los tests de integración contra PostgreSQL real; `CONTROLLOCAL_CIERRE=1`
convierte el salto silencioso en un fallo dentro del propio reactor, y de eso responde
[`GateDeCierreTest`](controllocal-app/src/test/java/com/controllocal/arquitectura/GateDeCierreTest.java),
que además **inventaría los tests de integración**: si alguien añade uno, la prueba obliga a
declararlo ahí en vez de dejarlo saltando en silencio para siempre.

## El modelo, en cuatro reglas

1. **La operación vive en el ENCARGO, no en la propiedad, y nunca se infiere.** `VENTA` y
   `ALQUILER` son los dos únicos valores; `AMBAS` y `COMPRA` se rechazan con la explicación. Una
   propiedad disponible para las dos cosas lleva **dos encargos independientes**, cada uno con su
   precio, su vigencia y su histórico. La invariante es *un encargo vivo por (propiedad, operación)*.
2. **No hay valores por defecto donde el defecto mentiría.** Ni `precio_propiedad.operacion` ni
   `captacion.motivo_operacion` los tienen, ni en la base ni en la entidad. Quien escribe declara;
   si no lo sabe, el error dice que falta. Un defecto a alquiler archivaría precios de venta en la
   serie equivocada y **ningún CHECK podría notarlo**.
3. **`/captura` es el motor de preguntas y lo comparten la SPA y KAIROS**: qué se sabe, qué falta,
   qué se pregunta ahora. La lista de campos de cada tipo **sale del catálogo**, no del cliente:
   añadir un atributo es una fila, no un despliegue.
4. **Los estados persisten como código unitario**, y el enum lo deriva
   [`EstadosDominio`](controllocal-domain/src/main/java/com/controllocal/domain/comun/EstadosDominio.java),
   que es donde vive la correspondencia completa entre código almacenado y estado.

**El esquema lo posee Flyway** (`controllocal-app/src/main/resources/db/migration/`); Hibernate
solo `validate`. **Una migración aplicada no se edita nunca.** Dos cosas que `validate` no ve:

- **Una columna obligatoria sin mapear.** `ddl-auto: validate` comprueba que lo mapeado exista, no
  que exista lo obligatorio; de eso se encarga `todaColumnaObligatoriaEstaMapeada`.
- **El cuerpo de una función PL/pgSQL.** Una conversión de vocabulario tiene que llegar también
  ahí: V40 estrechó tres columnas `estado` y dejó una función comparando contra el valor viejo, lo
  que tumbó todo el enrolamiento MFA. Ni javac ni Hibernate leen un `prosrc`.

## Perfiles y configuración

`dev` (por defecto), `test` y `prod`. La conexión, las locations de Flyway, el secreto JWT y
`ALMACEN_DIR` viven en `application-{perfil}.yml`, **no** en `application.yml`.

**`prod` no tiene valores por defecto**: una variable ausente detiene el arranque nombrándola.
Después, `ValidadorConfiguracionSeguridad` recorre la configuración y bloquea el arranque ante
cualquier ajuste inseguro —un secreto de firma corto, la contraseña del compose de desarrollo, las
credenciales del MinIO publicado, cualquier cuenta del seed todavía viva—. **Cada mensaje nombra la
variable que hay que corregir**, a propósito: un arranque que falla diciendo «configuración
inválida» se acaba resolviendo desactivando la comprobación.

Los binarios tienen dos proveedores tras una sola frontera (`AlmacenDocumentos`):
`ALMACEN_PROVEEDOR=DISCO`, el defecto, o `S3` genérico (MinIO, SeaweedFS, Garage, Ceph RGW o AWS;
el servidor productivo aún no está elegido). **No cambies el defecto a S3 antes de migrar los
binarios**: hoy viven en el volumen `controllocal_almacen`, no en un bucket. El respaldo, la
restauración y la migración entre proveedores se operan con herramienta interna que no forma
parte de este repositorio; la frontera que hay que respetar sí está aquí, en
[`AlmacenDocumentos`](controllocal-web/src/main/java/com/controllocal/web/almacen/AlmacenDocumentos.java).

## Lo que no está aquí

Este README explica cómo trabajar sobre el backend. Dos cosas que se buscan aquí y no están:

- **El contrato ejecutable son OpenAPI y las pruebas**, no un documento. Levanta la API y abre
  `/swagger-ui.html`; lo que no esté ahí o en un test, no es contrato.
- **La documentación de decisiones, el mapa de etapas y la evidencia de cada corrida son
  internos** y no se publican en este repositorio.

> **El contrato REST se descongeló el 2026-08-09.** DTOs, endpoints, estados, errores y flujos
> pueden cambiar con dos condiciones: una razón funcional o arquitectónica, y **que el cambio
> llegue con sus pruebas**. La regla de trabajo es
> *necesidad → regla de dominio → contrato → backend → frontend → pruebas*.
