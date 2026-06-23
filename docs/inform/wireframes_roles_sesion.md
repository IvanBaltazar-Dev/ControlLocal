# Wireframes, Roles Y Sesion

Este documento alinea la interfaz objetivo con el estado actual del proyecto. Sirve para revisar pantallas, permisos y comportamiento de sesion sin tener que leer todo el codigo.

## Estado Actual Del Proyecto

ControlLocal ya cuenta con:

- Backend Java por capas: `model`, `db-manager`, `dao`, `bl`, `rest`.
- API Jakarta REST bajo `/controllocal/Api`.
- Autenticacion con `POST /auth/login`.
- Token JWT firmado con HS256.
- Filtro `JwtAuthFilter` para proteger endpoints privados.
- Frontend Blazor en `frontend-csharp/ControlLocal.Web`.
- Menu lateral por rol en `Services/Navigation.cs`.

La sesion actual no implementa `remember me`. El token dura 30 minutos y debe enviarse como:

```text
Authorization: Bearer <token>
```

## Roles Del Producto

| Rol | Enfoque | Sustento |
| --- | --- | --- |
| Broker administrador | Gobierno global | Administra brokers, audita y puede intervenir reasignaciones. |
| Broker supervisor | Control de equipo | Revisa captaciones, evalua solicitudes y gestiona agentes propios. |
| Agente inmobiliario | Operacion diaria | Registra propietarios, locales, captaciones, clientes, oportunidades, visitas y solicitudes. |

Regla conceptual: el agente genera la operacion; el broker la supervisa. Esta separacion sostiene la trazabilidad del sistema.

## Navegacion Esperada Por Rol

### Broker Administrador

- Dashboard global.
- Reportes globales.
- Brokers.
- Reasignar agentes.
- Catalogos del sistema.

### Broker Supervisor

- Dashboard de equipo.
- Reportes de equipo.
- Mis agentes.
- Reasignar captaciones.
- Captaciones por revisar.
- Solicitudes por evaluar.
- Operaciones del equipo.

### Agente Inmobiliario

- Dashboard.
- Clientes interesados.
- Propietarios.
- Locales comerciales.
- Captaciones.
- Oportunidades comerciales.
- Interacciones comerciales.
- Visitas.
- Solicitudes de alquiler.

## Matriz De Permisos

| Modulo / accion | Admin | Broker | Agente |
| --- | --- | --- | --- |
| Ver dashboard global | Si | No | No |
| Ver dashboard de equipo | Si | Si, solo equipo | No |
| Ver dashboard operativo | No | No | Si |
| Registrar brokers | Si | No | No |
| Registrar agentes | No | Si, agentes propios | No |
| Reasignar agentes entre brokers | Si | No | No |
| Registrar propietarios | No | No | Si |
| Registrar locales comerciales | No | No | Si |
| Registrar captaciones | No | No | Si |
| Corregir captaciones observadas | No | No | Si |
| Revisar captaciones | Si, global | Si, equipo | No |
| Aprobar, observar o rechazar captaciones | Si, global | Si, equipo | No |
| Reasignar captaciones | Si, global | Si, equipo | No |
| Cerrar captaciones | Si, global | Si, equipo | No |
| Registrar clientes interesados | No | No | Si |
| Crear oportunidades | No | No | Si |
| Registrar interacciones | No | No | Si |
| Programar visitas | No | No | Si |
| Registrar resultado de visita | No | No | Si |
| Registrar solicitud de alquiler | No | No | Si |
| Cargar documentos | No | No | Si |
| Evaluar solicitudes | Si, global | Si, equipo | No |
| Ver reportes globales | Si | No | No |
| Ver reportes de equipo | Si | Si, equipo | No |
| Ver reportes propios | No | No | Si |

## Sustento De Pantallas

| Pantalla | Por que existe | Rol principal |
| --- | --- | --- |
| Login | Abre una sesion autenticada y entrega token JWT. | Todos |
| Dashboard | Resume trabajo pendiente y estado comercial. | Todos, con distinta vista |
| Propietarios | Permite registrar titulares de locales. | Agente |
| Locales comerciales | Guarda informacion fisica, legal y comercial del inmueble. | Agente |
| Captaciones | Formaliza que un local puede ser comercializado. | Agente |
| Bandeja de captaciones | Permite control del broker antes de activar una captacion. | Broker |
| Clientes interesados | Registra demanda potencial. | Agente |
| Oportunidades | Une cliente y captacion para seguimiento trazable. | Agente / Broker lectura |
| Interacciones | Documenta contactos y respuestas. | Agente |
| Visitas | Agenda y registra reaccion del cliente frente al local. | Agente |
| Solicitudes | Formaliza condiciones propuestas por el cliente. | Agente |
| Documentos | Guarda evidencia para evaluar la solicitud. | Agente |
| Evaluacion | Registra decision del broker. | Broker |
| Reportes | Supervisa desempeno y trazabilidad. | Broker / Admin |
| Perfil | Muestra identidad activa y permite cerrar sesion. | Todos |

## Uso De Modales

Usar modal para decisiones breves:

- Confirmar aprobacion, observacion o rechazo.
- Reasignar captacion.
- Cerrar captacion.
- Registrar una interaccion corta.
- Confirmar cierre de sesion.
- Atender o descartar alerta.

Usar pagina dedicada para flujos largos:

- Registro de local comercial.
- Registro de captacion.
- Registro de cliente interesado con datos completos.
- Solicitud de alquiler.
- Carga documental.
- Evaluacion de solicitud.
- Detalle de oportunidad.

## Sesion Y Seguridad

### Comportamiento Actual

- Login: `POST /controllocal/Api/auth/login`.
- Roles devueltos al frontend: `ADMIN`, `BROKER`, `AGENTE`.
- Duracion del token: 30 minutos.
- Endpoints publicos: `/Api/salud` y `/Api/auth/login`.
- Endpoints privados: requieren encabezado `Authorization`.
- Algunas rutas de decision de captacion exigen rol `BROKER` o `ADMIN`.

### Mantener Sesion Activa

No forma parte del MVP actual. Para implementarlo despues se necesita:

- Refresh token o cookie persistente HTTP-only.
- Expiracion mas larga y revocable.
- Logout que invalide sesion persistente.
- Registro de dispositivos o sesiones si se requiere auditoria.

No se recomienda guardar tokens largos ni contrasenas en `localStorage`.

## Ajustes Que Deben Reflejar Los Wireframes

- El broker no debe ver accesos de registro operativo como propietario, local o cliente.
- El agente no debe ver bandejas de aprobacion ni evaluacion.
- El admin debe ver administracion y reportes globales, no formularios operativos.
- Las pantallas de detalle deben mostrar historial y estado actual.
- Todo cambio de estado debe pedir motivo u observacion cuando sea una decision humana.
- El logo debe aparecer en login, sidebar y reportes exportables.
- Formularios largos deben ser paginas con secciones, no modales.
- El flujo de no continuidad debe estar cerca de visitas e interacciones, porque normalmente nace ahi.

## Logo Y Material Visual

Logos disponibles:

```text
docs/commercial/logo/logo_v1.png
docs/commercial/logo/logo_v2.png
```

Uso recomendado:

- Login: logo centrado sobre el formulario.
- Layout interno: logo pequeno en sidebar.
- Reportes: logo en cabecera.
- Pantalla de carga: logo con nombre ControlLocal.

## Checklist De Revision De Wireframes

- Cada pantalla tiene un rol propietario claro.
- Cada boton de decision registra motivo cuando corresponde.
- Los estados visibles coinciden con los enums documentados.
- La navegacion no muestra acciones fuera del rol.
- El usuario siempre puede volver al dashboard.
- Los formularios largos tienen guardado claro y validaciones visibles.
- La sesion expirada lleva a login sin perder contexto de forma confusa.
