# Guía manual de pruebas en Postman — ControlLocal API

Sigue los pasos **en orden, de arriba hacia abajo**. Cada uno indica método, URL, cuerpo y el
código que debes ver. Si los haces en secuencia verás `200` y `201` todo el camino.

> Verificado de extremo a extremo contra el backend local: la secuencia completa devuelve 200/201.

---

## Antes de empezar

- **URL base:** `http://localhost:8080/controllocal/Api`
- En Postman, para las peticiones con cuerpo: pestaña **Body → raw → JSON**.
- Para las peticiones privadas: pestaña **Authorization → Type: Bearer Token** y pega ahí el `token`.
- El backend debe estar arriba (prueba el paso 1).

### Credenciales (seed `02_seed_base_data.sql`)
| Rol | Usuario | Contraseña |
|---|---|---|
| Agente | `vmora` | `Agente2026` |
| Broker | `rsalas` | `Broker2026` |
| Admin | `admin@controllocal.test` | `Admin2026` |

### IDs que irás anotando (los devuelve cada POST en el campo `id`)
| Variable | De qué paso sale |
|---|---|
| idPropietario | paso 3 |
| idCliente | paso 7 |
| idLocal | paso 11 |
| idCaptacion | paso 15 |
| idOportunidad | paso 25 |
| idVisita | paso 28 |
| idSolicitud / codigoSolicitud | paso 34 |
| idProspeccion | paso 38 |

Donde veas `{idX}` reemplázalo por el número que anotaste. Donde veas `{token}` usa el último token
de login (cámbialo cada vez que cambias de rol).

---

## 🟢 Bloque A — Salud y sesión de AGENTE

### Paso 1 — Salud (sin token)
- **GET** `http://localhost:8080/controllocal/Api/salud`
- **Espera: 200** → `{"estado":"ok",...}`

### Paso 2 — Login Agente → copia el `token`
- **POST** `http://localhost:8080/controllocal/Api/auth/login`
- Body:
```json
{ "usuario": "vmora", "contrasena": "Agente2026" }
```
- **Espera: 200**. Copia el campo `token` y úsalo como Bearer en los pasos 3–19.

---

## 🟢 Bloque B — Propietarios

### Paso 3 — Crear propietario → anota `idPropietario`
- **POST** `…/Api/propietarios`  · Bearer `{token}`
```json
{
  "tipoPersona": "N",
  "tipoDocumento": "D",
  "numeroDocumento": "70123456",
  "nombre": "Propietario Demo",
  "telefono": "987654321",
  "correo": "prop.demo1@controllocal.test",
  "consentimientoUsoDato": true,
  "estado": "A"
}
```
- **Espera: 201**. (Si dice documento duplicado, cambia `numeroDocumento` y el `correo`.)

### Paso 4 — Listar propietarios
- **GET** `…/Api/propietarios?pagina=1&tamano=10` · Bearer → **200**

### Paso 5 — Obtener propietario
- **GET** `…/Api/propietarios/{idPropietario}` · Bearer → **200**

### Paso 6 — Actualizar propietario
- **PUT** `…/Api/propietarios/{idPropietario}` · Bearer
```json
{
  "tipoPersona": "N",
  "tipoDocumento": "D",
  "numeroDocumento": "70123456",
  "nombre": "Propietario Demo (editado)",
  "telefono": "900000000",
  "correo": "prop.demo1@controllocal.test",
  "consentimientoUsoDato": true,
  "estado": "A"
}
```
- **Espera: 200**

---

## 🟢 Bloque C — Clientes

### Paso 7 — Crear cliente → anota `idCliente`
- **POST** `…/Api/clientes` · Bearer
```json
{
  "tipoPersona": "N",
  "tipoDocumento": "D",
  "numeroDocumento": "70654321",
  "nombre": "Cliente Demo",
  "telefono": "912345678",
  "correo": "cli.demo1@controllocal.test",
  "rubroComercial": "Cafeteria",
  "consentimientoContacto": true,
  "consentimientoUsoDato": true,
  "estado": "A"
}
```
- **Espera: 201**

### Paso 8 — Listar clientes
- **GET** `…/Api/clientes?pagina=1&tamano=10` · Bearer → **200**

### Paso 9 — Obtener cliente
- **GET** `…/Api/clientes/{idCliente}` · Bearer → **200**

### Paso 10 — Actualizar cliente
- **PUT** `…/Api/clientes/{idCliente}` · Bearer
```json
{
  "tipoPersona": "N",
  "tipoDocumento": "D",
  "numeroDocumento": "70654321",
  "nombre": "Cliente Demo (editado)",
  "telefono": "911111111",
  "correo": "cli.demo1@controllocal.test",
  "rubroComercial": "Restaurante",
  "consentimientoContacto": true,
  "consentimientoUsoDato": true,
  "estado": "A"
}
```
- **Espera: 200**

---

## 🟢 Bloque D — Local y captación

### Paso 11 — Crear local → anota `idLocal`
- **POST** `…/Api/locales` · Bearer
```json
{
  "codigoLocal": "CL-DEMO-01",
  "direccion": "Av. Postman 123",
  "distrito": "Miraflores",
  "metraje": 120.5,
  "precioReferencial": 3500,
  "rubroPermitido": "Comercial",
  "descripcion": "Local de prueba",
  "idPropietario": {idPropietario},
  "estado": "D",
  "tipoInmueble": "L",
  "uso": "C",
  "ambientes": 3,
  "estadoPublicacion": "B"
}
```
- **Espera: 201**. (Si `codigoLocal` está duplicado, cámbialo, p. ej. `CL-DEMO-02`.)

### Paso 12 — Mis locales (del agente)
- **GET** `…/Api/locales/mis-locales?pagina=1&tamano=10` · Bearer → **200**

### Paso 13 — Listar locales
- **GET** `…/Api/locales?pagina=1&tamano=10` · Bearer → **200**

### Paso 14 — Obtener local
- **GET** `…/Api/locales/{idLocal}` · Bearer → **200**

### Paso 15 — Crear captación → anota `idCaptacion`
- **POST** `…/Api/captaciones` · Bearer
```json
{
  "codigoCaptacion": "CAP-DEMO-01",
  "fechaCaptacion": "2026-06-17",
  "fechaInicioVigencia": "2026-06-17",
  "fechaFinVigencia": "2026-12-17",
  "comisionPactada": 5.0,
  "observaciones": "Captacion de prueba",
  "idLocal": {idLocal},
  "motivoOperacion": "A",
  "urgencia": 2,
  "exclusividad": true
}
```
- **Espera: 201**. Nace en estado `PENDIENTE_REVISION`.

### Paso 16 — Listar captaciones
- **GET** `…/Api/captaciones?pagina=1&tamano=10` · Bearer → **200**

### Paso 17 — Obtener captación
- **GET** `…/Api/captaciones/{idCaptacion}` · Bearer → **200**

### Paso 18 — Actualizar captación (mientras está pendiente)
- **PUT** `…/Api/captaciones/{idCaptacion}` · Bearer
```json
{
  "fechaCaptacion": "2026-06-17",
  "fechaInicioVigencia": "2026-06-17",
  "fechaFinVigencia": "2026-12-31",
  "comisionPactada": 6.0,
  "observaciones": "Captacion editada",
  "idLocal": {idLocal},
  "motivoOperacion": "A",
  "urgencia": 3,
  "exclusividad": true
}
```
- **Espera: 200**

### Paso 19 — Actualizar el local (ya ligado a tu captación)
- **PUT** `…/Api/locales/{idLocal}` · Bearer
```json
{
  "codigoLocal": "CL-DEMO-01",
  "direccion": "Av. Postman 123 (editado)",
  "distrito": "Miraflores",
  "metraje": 130,
  "precioReferencial": 3800,
  "rubroPermitido": "Comercial",
  "descripcion": "Editado",
  "idPropietario": {idPropietario},
  "estado": "D",
  "tipoInmueble": "L",
  "uso": "C",
  "estadoPublicacion": "P"
}
```
- **Espera: 200**.
- ⚠️ Si lo intentas **antes** del paso 15 da **400** ("este local no pertenece a tus captaciones"): un
  agente solo edita locales ligados a una de sus captaciones. Por eso va aquí.

---

## 🟢 Bloque E — El BROKER aprueba (cambia de sesión)

### Paso 20 — Login Broker → copia el nuevo `token`
- **POST** `…/Api/auth/login`
```json
{ "usuario": "rsalas", "contrasena": "Broker2026" }
```
- **Espera: 200**. Usa este token (Broker) en los pasos 21–23.

### Paso 21 — Captaciones pendientes de revisión
- **GET** `…/Api/captaciones/pendientes?pagina=1&tamano=10` · Bearer(broker) → **200**

### Paso 22 — Aprobar la captación  ➜ pasa a ACTIVA
- **POST** `…/Api/captaciones/{idCaptacion}/decision` · Bearer(broker)
```json
{ "accion": "APROBAR", "observacion": "Aprobada en demo" }
```
- **Espera: 200**.
- 💡 Este paso es **obligatorio antes de crear la oportunidad** (el paso 25 exige captación ACTIVA).
- ⏳ En este entorno la BD remota es lenta; esta petición puede tardar ~30–90s. Espera, no la canceles.

### Paso 23 — Listar agentes (vista del broker)
- **GET** `…/Api/agentes?pagina=1&tamano=50` · Bearer(broker) → **200**

---

## 🟢 Bloque F — El AGENTE gestiona (vuelve a sesión de agente)

### Paso 24 — Login Agente otra vez → copia el `token`
- **POST** `…/Api/auth/login`
```json
{ "usuario": "vmora", "contrasena": "Agente2026" }
```
- **Espera: 200**. Usa este token (Agente) de aquí en adelante.

### Paso 25 — Crear oportunidad → anota `idOportunidad`
- **POST** `…/Api/oportunidades` · Bearer
```json
{
  "codigoOportunidad": "",
  "idCliente": {idCliente},
  "idCaptacion": {idCaptacion},
  "observaciones": "Oportunidad de prueba"
}
```
- **Espera: 201**. (Si da 400 "La captacion debe estar ACTIVA", te faltó el paso 22.)

### Paso 26 — Listar oportunidades
- **GET** `…/Api/oportunidades?pagina=1&tamano=10` · Bearer → **200**

### Paso 27 — Obtener oportunidad
- **GET** `…/Api/oportunidades/{idOportunidad}` · Bearer → **200**

### Paso 28 — Programar visita → anota `idVisita`
- **POST** `…/Api/visitas` · Bearer
```json
{
  "idOportunidad": {idOportunidad},
  "fechaVisita": "2026-06-20",
  "horaVisita": "10:30:00",
  "observaciones": "Visita de prueba"
}
```
- **Espera: 201**

### Paso 29 — Listar visitas
- **GET** `…/Api/visitas?pagina=1&tamano=10` · Bearer → **200**

### Paso 30 — Obtener visita
- **GET** `…/Api/visitas/{idVisita}` · Bearer → **200**

### Paso 31 — Reprogramar visita
- **PATCH** `…/Api/visitas/{idVisita}/reprogramar` · Bearer
```json
{ "fechaVisita": "2026-06-22", "horaVisita": "15:00:00" }
```
- **Espera: 200**

### Paso 32 — Marcar realizada
- **PATCH** `…/Api/visitas/{idVisita}/realizar` · Bearer · (sin body) → **200**

### Paso 33 — Registrar resultado (SEGUIMIENTO)
- **PATCH** `…/Api/visitas/{idVisita}/resultado` · Bearer
```json
{ "resultado": "S", "observaciones": "Cliente pide seguimiento", "nivelInteres": 4 }
```
- **Espera: 200**.
- ⚠️ Debe ir **después** de "realizar" (paso 32): solo una visita REALIZADA admite resultado.

### Paso 34 — Crear solicitud → anota `idSolicitud` y `codigoSolicitud`
- **POST** `…/Api/solicitudes` · Bearer
```json
{
  "codigoSolicitud": "",
  "fechaRegistro": "2026-06-17",
  "montoPropuesto": 3400,
  "plazoTentativo": "12 meses",
  "observaciones": "Solicitud de prueba",
  "fechaVigenciaOferta": "2026-07-17",
  "idOportunidad": {idOportunidad}
}
```
- **Espera: 201**. El `codigoSolicitud` se autogenera; cópialo para el paso 37.

### Paso 35 — Listar solicitudes
- **GET** `…/Api/solicitudes?pagina=1&tamano=10` · Bearer → **200**

### Paso 36 — Obtener solicitud
- **GET** `…/Api/solicitudes/{idSolicitud}` · Bearer → **200**

### Paso 37 — Obtener por código
- **GET** `…/Api/solicitudes/codigo/{codigoSolicitud}` · Bearer → **200**

### Paso 38 — Crear prospección → anota `idProspeccion`
- **POST** `…/Api/prospecciones` · Bearer
```json
{ "idLocal": {idLocal}, "observaciones": "Prospeccion de prueba" }
```
- **Espera: 201**

### Paso 39 — Listar prospecciones
- **GET** `…/Api/prospecciones?pagina=1&tamano=10` · Bearer → **200**

### Paso 40 — Prospecciones por recontactar
- **GET** `…/Api/prospecciones/recontactar?dias=15&pagina=1&tamano=10` · Bearer → **200**

### Paso 41 — Obtener prospección
- **GET** `…/Api/prospecciones/{idProspeccion}` · Bearer → **200**

### Paso 42 — Listar alertas
- **GET** `…/Api/alertas?pagina=1&tamano=20` · Bearer → **200**

---

## 🟢 Bloque G — El BROKER evalúa (cambia de sesión)

### Paso 43 — Login Broker → copia el `token`
- **POST** `…/Api/auth/login`
```json
{ "usuario": "rsalas", "contrasena": "Broker2026" }
```
- **Espera: 200**

### Paso 44 — Listar evaluaciones
- **GET** `…/Api/evaluaciones?pagina=1&tamano=10` · Bearer(broker) → **200**

### Paso 45 — Registrar evaluación
- **POST** `…/Api/evaluaciones` · Bearer(broker)
```json
{
  "tipoEvaluacion": "P",
  "resultado": "A",
  "observaciones": "Evaluacion de prueba",
  "idSolicitud": {idSolicitud}
}
```
- **Espera: 201** ✅ (fin del flujo: el agente captó, el broker aprobó, se gestionó y se evaluó).

### Paso 46 — Listar alertas (broker)
- **GET** `…/Api/alertas?pagina=1&tamano=20` · Bearer(broker) → **200**

---

## Notas rápidas
- **401 "Token requerido / invalido":** falta el Bearer o expiró (dura 30 min). Repite el login.
- **400 con un mensaje claro:** es una regla de negocio (orden o estado), no un error de Postman.
- **Re-ejecutar desde cero:** cambia `numeroDocumento`, `codigoLocal` y `codigoCaptacion` por valores
  nuevos (los anteriores ya existen y darían duplicado).
- **Login limitado a 5 intentos por minuto** por IP; esta guía hace 4, así que vas holgado.
- Códigos usados en los cuerpos: tipoDocumento `D`=DNI · tipoPersona `N`=Natural · estado `A`=Activo ·
  local `D`=Disponible · tipoInmueble `L`=Local · uso `C`=Comercial · publicación `B`=Borrador
  `P`=Publicado · operación `A`=Alquiler · resultado visita `S`=Seguimiento · evaluación tipo `P`=Preliminar
  resultado `A`=Aprobada.
