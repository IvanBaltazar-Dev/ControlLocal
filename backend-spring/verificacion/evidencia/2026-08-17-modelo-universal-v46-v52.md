# Evidencia · Modelo universal Propiedad × Operación (V46–V52)

**Fecha:** 2026-08-17
**Decisión que implementa:** `docs/ai/decision-modelo-universal-propiedad-operacion.md` (D-E4-1), bloque 2 de la ruta a BROX 1.0.
**Alcance:** las seis migraciones del plan (M0–M5) más el outbox de eventos.

---

## Qué se aplicó

| Migración | Qué hace | Filas tocadas |
|---|---|---|
| **V46** `ubicacion_postgis` | extensión PostGIS + `propiedad.ubicacion geography(Point,4326)` + dos índices GiST + trigger de sincronía en los dos sentidos | 0 propiedades con coordenadas hoy |
| **V47** `titularidad_propiedad` | tabla de titularidad con cuota, representante y vigencia + backfill | **21 titularidades** creadas, 0 propiedades sin titular |
| **V48** `atributos_gobernados` | `catalogo_atributo` + `catalogo_atributo_tipo` + `atributo_propiedad` + semilla + backfill | 19 atributos del sistema, **91 valores migrados** |
| **V49** `historico_economico_por_encargo` | `precio_propiedad.id_captacion` + `operacion` + backfill | **14 hitos** atados a su encargo; 8 sin encargo quedan como alquiler |
| **V50** `encargo_operacion_explicita` | quita el `DEFAULT` de `motivo_operacion`, índice único de un encargo vivo por operación, coherencia con la condición económica | **13 encargos** verificados |
| **V51** `expediente_alquiler_y_compraventa` | `solicitud_alquiler.tipo` + `condicion_compraventa` + `tipo_documento_requerido.tipo_propiedad` | **8 documentos** de compraventa en catálogo |
| **V52** `evento_dominio_outbox` | outbox transaccional con `origen` (UI/KAIROS/API/SISTEMA) | tabla nueva |

**Ninguna migración borra nada.** `precio_referencial`, `moneda_referencial`,
`id_rol_propietario`, `disponibilidad_comercial` y `detalle_local_comercial`
siguen en su sitio: las entidades las mapean y quitarlas rompería el arranque.
Su retirada es un cambio de código, módulo a módulo.

---

## Cómo se verificó

### 1 · Ensayo sobre una copia, no sobre la base de desarrollo

```
pg_dump -Fc controllocal_dev  →  controllocal_ensayo  →  las 7 migraciones
```

**El ensayo encontró dos errores antes de que llegaran a ninguna parte:**

| Error | Qué era | Cómo se corrigió |
|---|---|---|
| `data type bigint has no default operator class for access method "gist"` | el índice compuesto `(organizacion_id, ubicacion)` no se puede crear sin `btree_gist` | se añade `CREATE EXTENSION btree_gist` en V46 |
| `El atributo "rubro_permitido" no aplica a una propiedad de tipo O` | el catálogo decía que el rubro es de local y almacén; **la base tiene dos oficinas con rubro relleno** | se añade `O` al catálogo, en la migración **y** en `docs/ai/modelo/modelo-universal.js` |

> El segundo no es un bug de la migración: es el dato corrigiendo al diseño.
> `detalle_local_comercial` es obligatoria para tipo **L y O**, y el modelo
> escrito a mano se había quedado con L.

### 2 · Gate de invariantes: 47 comprobaciones

```bash
docker cp backend-spring/verificacion/gate-modelo-universal.sql controllocal-postgres-v2:/tmp/
docker exec controllocal-postgres-v2 \
    psql -U controllocal -d controllocal_dev -q -f /tmp/gate-modelo-universal.sql
```

```
 en verde | en rojo | total
----------+---------+-------
       47 |       0 |    47
```

Todo dentro de una transacción que termina en `ROLLBACK`: se puede correr contra
desarrollo sin ensuciarlo.

**No solo comprueba que las cosas existan: intenta romperlas.** 13 de las 47 son
«esto tiene que ser rechazado», y si la base lo acepta, la prueba falla:

- cuotas de titularidad que no suman 100
- dos representantes vigentes sobre la misma propiedad
- una clave de atributo que no está en el catálogo
- un atributo que no aplica al tipo de propiedad (`dormitorios` en un local)
- el valor en la columna equivocada (`carga_electrica_kw` como texto)
- un hito de precio con operación distinta a la de su encargo
- **dos encargos vivos de la misma operación sobre una propiedad**
- condiciones de compraventa colgadas de un expediente de alquiler

**Y la prueba que decide el modelo:**

```
  32  M4 admite venta y alquiler vivos sobre la misma propiedad     OK
  33  M4 rechaza dos encargos vivos de la misma operacion           OK
```

Las tres comprobaciones que ocurren dentro de un `SAVEPOINT` se capturan con
`\gset` antes del `ROLLBACK TO`: si no, se perderían con él — y son justo las
tres que prueban que el modelo funciona.

### 3 · Aplicación real y arranque

```
Flyway: Successfully applied 7 migrations to schema "public", now at version v52
Hibernate: Initialized JPA EntityManagerFactory for persistence unit 'default'
```

`ddl-auto: validate` pasó: **ninguna entidad quedó desalineada con el esquema.**

Y la API responde con todo aplicado:

| Endpoint | |
|---|---|
| `POST /auth/login` (agente) | **200** |
| `GET /locales` | **200** |
| `GET /captaciones` | **200** |
| `GET /dashboard` | **200** |
| `GET /indicadores/resumen` | **200** |
| `GET /solicitudes` | **200** |

---

## Cambio de infraestructura

`backend-spring/docker-compose.yml` pasa de `postgres:17-alpine` a
**`postgis/postgis:17-3.5-alpine`**. Está construida sobre la anterior, así que
el volumen `controllocal_pg_data` se montó sin migración: mismo major, mismo
formato de datadir. Comprobado tras recrear el contenedor — **21 propiedades y
45 migraciones intactas** antes de aplicar nada.

> **Producción va a necesitar PostGIS.** Es la única dependencia nueva de
> infraestructura de todo el bloque.

---

## Respaldo

`pg_dump -Fc` de `controllocal_dev` **antes** de tocar nada, en el scratchpad de
la sesión (327 KB). El procedimiento formal de respaldo sigue siendo
`backend-spring/operacion/respaldo.ps1`.

---

## Lo que queda pendiente, y no es esquema

Las migraciones dejan la base lista; **el código todavía no usa lo nuevo**:

- Ninguna entidad JPA mapea `titularidad_propiedad`, `atributo_propiedad` ni
  `evento_dominio`.
- El alta de propiedad sigue escribiendo `id_rol_propietario` y
  `precio_referencial` en `propiedad`.
- Nadie escribe en el outbox todavía.
- El motor de captura (D-E4-2) no existe como servicio.

Eso es el trabajo del bloque 3 y se hace **módulo a módulo**, como manda
`decision-contrato-v2-descongelado.md` §6.

---

## Reejecución

Las siete migraciones son idempotentes en su parte de datos (`NOT EXISTS`,
`ON CONFLICT DO NOTHING`, `IF NOT EXISTS`). El gate se puede correr tantas veces
como haga falta: siempre termina en `ROLLBACK`.
