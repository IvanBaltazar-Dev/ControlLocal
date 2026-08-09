# Contrato de `LocalForm` en Angular

**Cerrado el 2026-07-30.** Este documento fija el patrón reutilizable para formularios de
alta/edición del SPA y las decisiones que no deben redescubrirse en cada pantalla.

## Rutas y autorización

| Ruta Angular | Operación REST | Roles |
|---|---|---|
| `/locales/nuevo` | `POST /locales` | AGENTE |
| `/locales/:id/editar` | `GET /locales/{id}` + `PUT /locales/{id}` | AGENTE |

El listado `/locales` sigue visible para todos. Los botones de alta/edición y ambas rutas se
restringen a AGENTE; el backend conserva la autorización definitiva. En edición, el service
acepta locales que el agente **prospectó o captó**.

## Cable congelado

`LocalRequest` conserva sin aliases los campos del backend:

`codigoLocal`, `direccion`, `distrito`, `metraje`, `precioReferencial`, `rubroPermitido`,
`descripcion`, `idPropietario`, `estado`, `tipoInmueble`, `uso`, `ambientes`,
`antiguedadAnios`, `zonaUrbanizacion`, `geoLat`, `geoLong`, `estadoPublicacion`, `frente`,
`zonificacion`, `aptoLicenciaFuncionamiento`, `cargaElectricaKw`,
`numeroEstacionamientos` y `cuotaMantenimiento`.

- El alta genera `codigoLocal`, fuerza `uso='C'` y nace con `estadoPublicacion='B'`.
- El backend crea la prospección inicial; el frontend no duplica esa llamada.
- La edición conserva `codigoLocal`, `idPropietario` y `estadoPublicacion`.
- Texto opcional vacío viaja como `null`, respetando la omisión de nulos del contrato de
  respuesta sin alterar el cuerpo de entrada.

## Propietarios y rendimiento

El selector pide `GET /propietarios?pagina=1&tamano=50`, muestra búsqueda local sobre lo ya
cargado y ofrece “Cargar más” mientras `items.length < totalRecords`. No descarga el catálogo
entero ni establece un techo silencioso. Si el propietario de un local editado no está en la
primera página, se resuelve con `GET /propietarios/{id}`.

La carga inicial usa `Promise.all` para traer página y local en paralelo, `OnPush` para el
componente y `takeUntilDestroyed` para cerrar la suscripción de progreso al destruir la vista.

## Validaciones y alcance de la pantalla

- Obligatorios: propietario, dirección, distrito, metraje mayor que cero, precio no negativo y
  rubro.
- Latitud `[-90, 90]`, longitud `[-180, 180]`; ambientes y estacionamientos son enteros.
- El propietario queda bloqueado al editar, igual que en el Blazor.
- `LocalForm` no sube fotos: esa responsabilidad vive en `FichaPropiedad`.
- No se muestran acciones hacia prospección/captación mientras esas pantallas Angular no estén
  migradas; así no se crean rutas rotas.

## Verificación

- 4 pruebas de componente: alta, edición inmutable, formulario inválido y código UTC.
- 6 pruebas adicionales de servicios y autorización por operación.
- Suite Angular completa: **57/57**.
- Build de producción: correcto.
