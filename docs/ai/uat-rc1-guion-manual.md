# UAT RC-1 — guion de prueba manual

**Para qué es.** Dejar de mirar el motor y **conducir el software**. 697 pruebas
verdes dicen que las piezas funcionan; esto dice si BROX se puede usar. Son
cosas distintas y solo la segunda se descubre usándolo.

**Regla mientras se ejecuta:** no se mira el código salvo que algo falle. Se usa
BROX como si fuéramos la inmobiliaria.

## Cómo levantarlo

```bash
docker compose -f backend-spring/docker-compose.yml up -d
```

```bash
npm --prefix frontend-angular start
```

API en `http://localhost:8090/controllocal/Api`, SPA en `http://localhost:4200`.

Credenciales sembradas: `admin@controllocal.test`/`Admin2026`,
`rsalas`…`sramirez`/`Broker2026`, `vmora`…`rgomez`/`Agente2026`.

> El administrador nace **obligado a enrolar MFA**: su sesión sale capada y solo
> alcanza perfil, enrolamiento y logout. No es un fallo, es el diseño (V37).

## Cómo anotar

Una línea por hallazgo, sin arreglar nada sobre la marcha:

```
[pantalla] qué hacía · qué esperaba · qué pasó · bloquea sí/no
```

Separar **defecto** (no hace lo que debe) de **fricción** (hace lo que debe pero
cuesta). Las dos importan; se priorizan distinto.

---

## 1. Entrada y sesión

| # | Caso | Qué mirar |
|---|---|---|
| 1.1 | Login de agente, broker y admin | Cada uno aterriza donde le corresponde |
| 1.2 | Enrolar MFA del admin | El QR/secreto se ve, el código entra, la sesión deja de estar capada |
| 1.3 | Login con contraseña mala | Mensaje comprensible, no un volcado técnico |
| 1.4 | Cambiar la propia contraseña | Pide la anterior; la nueva funciona; la vieja deja de funcionar |
| 1.5 | Recuperar acceso | El enlace/token llega y se puede canjear una sola vez |
| 1.6 | Logout | Volver atrás en el navegador **no** devuelve a la sesión |
| 1.7 | Dejar la sesión caducar | No hay bucle login↔dashboard |

## 2. Personas y autorización de datos

| # | Caso | Qué mirar |
|---|---|---|
| 2.1 | Alta de propietario | **Sin la casilla de autorización no hay alta** |
| 2.2 | Alta de cliente | Idem, y la constancia se ve después en la ficha |
| 2.3 | DNI/RUC inválido | Se rechaza antes de guardar, con un mensaje que dice qué corregir |
| 2.4 | Documento duplicado | No crea un segundo registro |
| 2.5 | Desactivar y reactivar | El estado se refleja en listado y ficha |

## 3. Local comercial

| # | Caso | Qué mirar |
|---|---|---|
| 3.1 | Alta de local con fotos | Las fotos suben y la portada se ve en el listado |
| 3.2 | Buscar por dirección, distrito, propietario | Resultados coherentes y **rápidos** |
| 3.3 | Filtros y KPI clicables | El contador cuadra con la tabla que muestra |
| 3.4 | Editar y desactivar | El local sale del mercado |

## 4. Ciclo comercial completo — el recorrido que importa

Hacerlo **entero y seguido**, con un local nuevo:

```
prospección → contacto → propuesta → captación → aprobación del broker
→ oportunidad → interacción → visita → resultado de visita
→ solicitud → documentos → evaluación → contrato → comisión
```

| # | Caso | Qué mirar |
|---|---|---|
| 4.1 | Prospección y propuesta | El embudo avanza y la fecha de recontacto aparece |
| 4.2 | Captación con condición económica | Pregunta «¿a cuánto equivale la comisión?» y la ficha lo explica en palabras |
| 4.3 | Broker aprueba / observa / rechaza | Las tres rutas y sus avisos |
| 4.4 | Oportunidad, interacción y visita | La visita se programa, se realiza y se registra su resultado |
| 4.5 | Solicitud y documentos | Checklist X/Y real; el broker observa un documento y el agente lo repone |
| 4.6 | Evaluación | Aprobar mueve la solicitud a APROBADA; rechazar la cierra |
| 4.7 | Contrato | Se crea, el local queda **ALQUILADO**, la oportunidad cierra exitosa |
| 4.8 | Comisión | Nace PENDIENTE; el broker reparte; cobro parcial; cobro total |
| 4.9 | **Doble clic en registrar movimiento** | **Un solo cobro.** Es lo que acabamos de construir |
| 4.10 | Finalizar el contrato | El local **sigue alquilado** y aparece la tarea de revisión |
| 4.11 | Resolver la revisión | Volver al mercado (`D`) o retirar (`T`); la captación **no** revive |

## 5. Permisos y alcance

| # | Caso | Qué mirar |
|---|---|---|
| 5.1 | Agente intenta rescindir | 403 con mensaje, o el botón no está |
| 5.2 | Agente ve solo lo suyo | Listados acotados a su alcance |
| 5.3 | Broker ve su equipo | Y no el de otro broker |
| 5.4 | Admin no opera comercialmente | Gobierna, no firma hechos de negocio |
| 5.5 | Entrar por URL a algo ajeno | 404, no un 500 ni datos de otro |

## 6. Bandejas, dashboard y navegación

| # | Caso | Qué mirar |
|---|---|---|
| 6.1 | Dashboard del agente | Bandeja de tareas dentro, con disparadores reales |
| 6.2 | Alertas y campana | Lo que anuncia existe y el enlace lleva a su sitio |
| 6.3 | Recorrer todo el menú | **Ningún callejón sin salida** |
| 6.4 | Recargar en cada pantalla (F5) | No se rompe ni pierde el contexto |
| 6.5 | Volver atrás del navegador | Comportamiento razonable |

## 7. Calidad de la experiencia

Esto no lo detecta ninguna prueba automática:

- ¿Los mensajes de error dicen **qué corregir** o solo que algo falló?
- ¿Hay pantallas que tardan de forma perceptible?
- ¿Los formularios largos pierden lo escrito al fallar la validación?
- ¿Se entiende en qué estado está cada cosa sin preguntarle a nadie?
- ¿Algún botón promete algo que no ocurre?

## Cierre de la UAT

Al terminar, clasificar cada hallazgo:

| | |
|---|---|
| **Bloqueante** | Impide operar. Se arregla antes de seguir con `V/D/X` |
| **Serio** | Hay forma de rodearlo. Entra en la cola con prioridad |
| **Fricción** | Funciona pero incomoda. Se agrupa por pantalla |
| **Fuera de alcance** | Los PDF de Jasper (D-F5-1) y todo lo marcado `RESERVADO_*` |

> **No es un hallazgo**: que falten `Captación V`, `Solicitud D` y
> `Oportunidad X`. Están **identificados y declarados** en
> `catalogo-productores-canonico.md`, con un gate que impide que se disfracen de
> funcionalidad existente. Se retoman después de esta línea base.
