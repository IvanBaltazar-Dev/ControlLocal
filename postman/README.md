# Pruebas Postman — ControlLocal API

Colección lista para importar en Postman (o ejecutar con [Newman](https://github.com/postmanlabs/newman))
que cubre **todos los endpoints REST** disponibles hasta el momento y los ordena siguiendo el
**flujo de negocio real**.

## Archivos

| Archivo | Qué es |
| --- | --- |
| `ControlLocal.postman_collection.json` | La colección: 68 peticiones organizadas por paso del flujo. |
| `ControlLocal.local.postman_environment.json` | Entorno local: `baseUrl`, usuario y contraseña por defecto. |

## Cómo usar en Postman

1. **Import** → arrastra los dos `.json`.
2. Arriba a la derecha selecciona el entorno **"ControlLocal - Local"**.
3. Asegúrate de que el backend esté arriba en `http://localhost:8080/controllocal/Api`.
4. Opción A — manual: abre cualquier carpeta, ejecuta primero su `🔑 Iniciar sesión` y luego las peticiones.
   El **token se guarda solo** y viaja como `Authorization: Bearer` en el resto.
5. Opción B — automática: botón derecho sobre la colección → **Run collection**. De arriba hacia abajo
   recorre el camino feliz completo.

## Orden = flujo de negocio

```
00 · Salud (público)
01 · Catálogos y captación (AGENTE)      propietario, cliente, local y captación (PENDIENTE_REVISION)
02 · Revisión de captación (BROKER)      el broker APRUEBA  → la captación pasa a ACTIVA
03 · Gestión comercial (AGENTE)          oportunidad, visita, solicitud, prospecciones, alertas
04 · Evaluación de solicitud (BROKER)    evaluar solicitud, administrar captación
```

> El orden importa: una oportunidad solo se puede crear si la captación está **ACTIVA**, y eso ocurre
> únicamente después de que el broker la aprueba (paso 02). Por eso los logins se alternan entre roles.

## Credenciales demo (seed `database/02_seed_base_data.sql`)

| Rol | Usuario | Contraseña |
| --- | --- | --- |
| Admin | `admin@controllocal.test` | `Admin2026` |
| Broker | `rsalas` / `psoto` | `Broker2026` |
| Agente | `vmora` / `jruiz` / `ltorres` / `creyes` | `Agente2026` |

Para probar con otro usuario, cambia `usuario` / `contrasena` en el entorno y usa el login genérico,
o edita el login de la carpeta correspondiente.

## Peticiones marcadas `(smoke)`

Algunas transiciones dependen del estado del dato (p. ej. cancelar una visita que ya se realizó, o
cerrar una captación). Sus pruebas no exigen un código exacto: verifican que la API **respondió sin
error de autenticación (401) ni de servidor (5xx)**, es decir, que la petición está bien construida y
el backend la entendió. Un `400`/`403`/`404` de regla de negocio ahí es un resultado válido.

## Ejecutar con Newman (CLI)

```bash
npx newman run postman/ControlLocal.postman_collection.json \
  -e postman/ControlLocal.local.postman_environment.json
```

> Nota: el login está limitado a 5 intentos por minuto por IP. La colección hace 4 logins (uno por
> cambio de rol), así que entra holgada; evita re-ejecutarla muchas veces seguidas en menos de un minuto.
