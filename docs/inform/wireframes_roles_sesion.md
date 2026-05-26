# ControlLocal - Wireframes, roles y sesion

Este documento alinea los wireframes con lo que el backend actual soporta y con las reglas de negocio esperadas para Broker y Agente Inmobiliario.

## Estado actual del programa

El proyecto actual es principalmente backend Java por capas:

- `controllocal-model`: entidades de dominio y enums.
- `controllocal-dao`: persistencia JDBC.
- `controllocal-bl`: reglas de negocio.
- `controllocal-app`: entrada minima por consola.

No hay todavia controladores web, vistas HTML, frontend, login real, manejo de cookies, tokens, sesiones HTTP ni Spring Security. Por eso los wireframes pueden representar el producto objetivo, pero el programa actual todavia no puede ejecutar una interfaz con permisos de pantalla o mantener una sesion activa desde navegador.

## Logo

El logo existente esta en:

```text
docs/commercial/logo/logo_v1.png
```

Debe integrarse en todos los wireframes en estos puntos:

- Login: logo centrado arriba del formulario.
- Layout interno: logo pequeno en la esquina superior izquierda del sidebar o header.
- Pantalla de carga/splash: logo centrado con nombre ControlLocal.
- Documentos o reportes exportables: logo en cabecera.

## Pantallas faltantes recomendadas

### Autenticacion

- Login.
- Cerrar sesion.
- Cambio de contrasena desde perfil, como mejora posterior.
- Mantener sesion activa, como mejora futura cuando exista autenticacion web.

No es obligatorio incluir "Recuperar contrasena" en el MVP porque ControlLocal
es un sistema interno y los usuarios internos son administrados por el broker
administrador. Para la primera version, la recuperacion puede resolverse como
restablecimiento de contrasena desde Gestion de usuarios internos.

### Panel Broker

- Dashboard de supervision.
- Registro y administracion de sus agentes inmobiliarios, si es broker normal.
- Bandeja de captaciones pendientes de revision segun alcance.
- Detalle de captacion.
- Aprobar captacion.
- Observar captacion.
- Rechazar captacion.
- Reasignar captacion a otro agente.
- Cerrar captacion activa.
- Evaluar solicitud de alquiler.
- Gestion de brokers normales, solo si es broker administrador.
- Reasignacion o intervencion de agentes entre brokers, solo si es broker administrador.
- Reportes y trazabilidad.

### Panel Agente Inmobiliario

- Dashboard operativo.
- Registro de propietarios.
- Registro de locales comerciales.
- Registro de captaciones.
- Correccion de captaciones observadas.
- Registro de clientes interesados.
- Creacion de oportunidades comerciales.
- Registro de interacciones.
- Programacion y actualizacion de visitas.
- Registro de solicitud de alquiler.
- Carga de documentos de solicitud.
- Registro de motivo de no continuidad.

### Pantallas compartidas

- Perfil de usuario.
- Notificaciones.
- Historial de actividad.
- Buscador global.
- Vista de detalle de oportunidad comercial.

## Uso de showModal

Para wireframes frontend, `showModal()` conviene en formularios cortos o decisiones puntuales. No conviene para flujos largos con muchas secciones.

Usar modal para:

- Registro rapido de usuario interno.
- Confirmar aprobar, observar o rechazar captacion.
- Reasignar captacion.
- Cerrar captacion.
- Registrar interaccion breve.
- Registrar motivo de no continuidad.
- Confirmar cierre de sesion.

Usar pantalla completa o pagina dedicada para:

- Registro completo de local comercial.
- Registro completo de captacion.
- Registro completo de cliente interesado si incluye datos extensos.
- Solicitud de alquiler.
- Evaluacion de solicitud.
- Detalle de oportunidad comercial.

## Matriz correcta de permisos

| Modulo / accion | Broker administrador | Broker no administrador | Agente inmobiliario |
| --- | --- | --- | --- |
| Ver dashboard de supervision | Si, global | Si, solo agentes supervisados | No |
| Registrar usuarios internos | Si | No | No |
| Registrar brokers | Si | No | No |
| Registrar agentes | No, salvo intervencion administrativa | Si, agentes propios | No |
| Asignar o reasignar agentes entre brokers | Si | No | No |
| Registrar propietarios | No | No | Si |
| Registrar locales comerciales | No | No | Si |
| Registrar captaciones | No | No | Si |
| Corregir captaciones observadas | No | No | Si |
| Registrar clientes interesados | No | No | Si |
| Crear oportunidades comerciales | No | No | Si |
| Registrar interacciones | No | No | Si |
| Programar visitas | No | No | Si |
| Registrar solicitud de alquiler | No | No | Si |
| Cargar documentos de solicitud | No | No | Si |
| Revisar captaciones | Si, global | Si, solo agentes supervisados | No |
| Aprobar / observar / rechazar captaciones | Si, global | Si, solo agentes supervisados | No |
| Reasignar captaciones | Si, global | Si, solo agentes supervisados | No |
| Cerrar captaciones | Si, global | Si, solo agentes supervisados | No |
| Evaluar solicitudes | Si, global | Si, solo agentes supervisados | No |
| Ver reportes globales | Si | No | No |
| Ver reportes de equipo | Si | Si, solo agentes supervisados | No |
| Ver reportes propios | No | No | Si |

Regla clave: el broker administrador administra brokers, audita el proceso global y puede intervenir reasignando agentes cuando sea necesario, pero no debe registrar captaciones, locales ni clientes. El broker normal registra y administra sus propios agentes inmobiliarios; desde esa alta se crea la supervision activa en `broker_agente`. El broker normal solo puede revisar, reasignar, cerrar y evaluar operaciones de los agentes bajo su responsabilidad. Las operaciones comerciales de registro pertenecen al agente inmobiliario.

## Vinculaciones correctas del flujo

El flujo objetivo debe quedar asi:

1. Agente registra propietario.
2. Agente registra local comercial vinculado al propietario.
3. Agente registra captacion vinculada al local y al agente responsable.
4. Broker administrador o broker supervisor del agente revisa captacion.
5. Si el broker observa, el agente corrige y reenvia.
6. Si el broker rechaza, la captacion termina.
7. Si el broker aprueba, la captacion queda activa.
8. Agente registra cliente interesado.
9. Agente crea oportunidad comercial vinculando cliente + captacion activa + agente.
10. Agente registra interacciones y visitas sobre la oportunidad.
11. Si el cliente continua, agente registra solicitud de alquiler.
12. Agente carga documentos.
13. Broker administrador o broker supervisor del agente evalua solicitud.
14. Si el cliente no continua, agente registra motivo de no continuidad y se cierra la oportunidad.

## Mantener sesion activa

El programa actual no soporta todavia "mantener sesion activa" porque no existe una capa de autenticacion ni gestion de sesion.

Para soportarlo se necesita agregar:

- Endpoint de login.
- Hash real de contrasenas.
- Sesion HTTP o token JWT.
- Opcion `rememberMe`, solo si se decide implementar "mantener sesion activa".
- Cookie persistente segura o refresh token.
- Fecha de expiracion.
- Endpoint de logout.
- Invalidacion de sesion/token.
- Middleware o filtro que valide permisos por rol en cada endpoint.
- Restablecimiento de contrasena administrado por broker administrador, antes que recuperacion publica por correo.

### Recomendacion tecnica

Cuando se agregue frontend/API, usar Spring Boot + Spring Security. Para "mantener sesion activa", la opcion mas simple es cookie HTTP-only con remember-me. Si se separa frontend y backend, usar access token corto + refresh token persistente.

Reglas minimas:

- Sin `rememberMe`: sesion corta, por ejemplo 30 minutos de inactividad.
- Con `rememberMe`: persistencia de 7 a 30 dias, revocable en logout.
- Cookie `HttpOnly`, `Secure` en produccion y `SameSite=Lax` o `Strict`.
- No guardar contrasena ni hashes en localStorage.

## Ajustes que deben reflejarse en el wireframe

- Ocultar del menu del broker las opciones: `Registrar captacion`, `Registrar local`, `Registrar cliente`.
- Mostrar esas opciones solo en el menu del agente.
- Separar el menu del broker en supervision, revision, reasignacion, evaluacion y reportes.
- Separar el menu del agente en registros, oportunidades, visitas, solicitudes y documentos.
- Agregar logo en login y layout interno.
- Cambiar formularios largos a pantallas dedicadas.
- Usar modales solo para acciones breves o confirmaciones.
- No agregar checkbox "Mantener sesion activa" al MVP; queda como mejora futura.
- Agregar vista de perfil con accion "Cerrar sesion en este dispositivo".
- No agregar "Recuperar contrasena" como flujo publico en el MVP; usar restablecimiento por broker administrador.
