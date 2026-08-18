# Evidencia · Entidades y repositorios del núcleo universal

**Fecha:** 2026-08-17
**Sigue a:** `2026-08-17-modelo-universal-v46-v52.md` (el esquema).
**Decisión:** `docs/ai/decision-modelo-universal-propiedad-operacion.md` (D-E4-1), bloque 2.

---

## Qué se añadió

### Entidades (`controllocal-domain`)

| Clase | Tabla | Notas |
|---|---|---|
| `TitularidadPropiedad` | `titularidad_propiedad` | privada de organización · fábrica `unica()` y `cerrar()`, que conserva la historia en vez de borrarla |
| `CatalogoAtributo` | `catalogo_atributo` | **catálogo híbrido**: filas del sistema (org NULL) + filas por organización |
| `AplicacionAtributo` | `catalogo_atributo_tipo` | `@Embeddable` — es un valor, no una entidad |
| `AtributoPropiedad` | `atributo_propiedad` | fábricas `deTexto/deNumero/deBooleano`: hacen imposible el estado que el CHECK rechazaría |
| `EventoDominio` | `evento_dominio` | outbox, con `origen` UI/KAIROS/API/SISTEMA |

### Repositorios (`controllocal-persistence`)

`TitularidadPropiedadRepository` · `CatalogoAtributoRepository` ·
`AtributoPropiedadRepository` · `EventoDominioRepository`.

Las tres consultas que sostienen lo que viene:

- **`aplicablesA(org, tipo)`** — de aquí deriva el motor de registro sus
  preguntas. Es lo que hace que registrar un terreno no pida dormitorios.
- **`clavesObligatoriasQueFaltan(...)`** — permite decir *«me falta el
  metraje»* en vez de fallar al guardar.
- **`idsQueCumplenNumero(...)`** — el matcher, sobre el índice parcial de V48.

---

## Tres decisiones de diseño que conviene conocer

### 1 · `CatalogoAtributo` NO hereda de `EntidadDeOrganizacion`

Su discriminador es **anulable a propósito**: las filas del sistema son las
mismas para toda corredora y ninguna puede borrarlas ni redefinir su tipo — son
lo que permite que dos propiedades se comparen y que el matcher exista.

Por eso está declarada en la lista de globales de `ArquitecturaTenancyTest`, con
su razón escrita, igual que `IntentoAcceso`. **El valor de un atributo
(`AtributoPropiedad`) sí es privado** y lleva su `organizacion_id` NOT NULL.

### 2 · La carga útil del outbox pasó de `jsonb` a texto (V53)

Al mapear la entidad apareció el problema: escribir un `Map` en `jsonb` exige
`@JdbcTypeCode(SqlTypes.JSON)`, que es de **Hibernate** — y
`controllocal-domain` depende **únicamente** de `jakarta.persistence-api`.

Esa dependencia mínima no es casualidad: mantiene el dominio independiente del
ORM. Meter `hibernate-core` en el dominio para una columna de una tabla que
todavía no lee nadie era un mal negocio.

Lo que se pierde es el índice GIN. Los dos accesos reales del outbox —el
consumidor por `proyectado_en IS NULL` y la ficha por `(entidad_tipo,
entidad_id)`— siguen con su índice propio. Un CHECK garantiza que el contenido
es JSON válido, así que **volver a `jsonb` es un cast** el día que exista el
proyector, que vivirá fuera del módulo de dominio.

Y por lo mismo, **el dominio no fabrica el JSON**: `EventoDominio.con(json)`
recibe la carga ya serializada por la capa de servicio, que sí tiene un
serializador de verdad. Escapar a mano es el tipo de código que se rompe con una
comilla en el nombre de una calle.

### 3 · El defecto de `operacion` vive en Java, no en la BD

`precio_propiedad.operacion` es NOT NULL **sin DEFAULT**: un `INSERT` en SQL
crudo que la olvide falla, en vez de colar un alquiler silencioso. En la
entidad, en cambio, el defecto es explícito y visible
(`= OPERACION_ALQUILER`) — todo lo que el sistema ha sabido hacer hasta el
modelo universal es alquilar — y cualquier productor puede cambiarlo.

---

## Verificación

### Gates de arquitectura — 6/6

```
ArquitecturaAuditoriaTest   1/1
ArquitecturaCapasTest       3/3
ArquitecturaTenancyTest     2/2
```

### `ddl-auto: validate` contra el esquema real

```
Flyway: now at version v53
Hibernate: Initialized JPA EntityManagerFactory for persistence unit 'default'
```

Ninguna de las cinco entidades nuevas quedó desalineada. Y la API responde:
`/locales`, `/captaciones`, `/dashboard`, `/indicadores/resumen`,
`/solicitudes`, `/propietarios` → **200**.

### Prueba de integración nueva — 14/14

`NucleoUniversalIntegrationTest`, contra PostgreSQL de verdad porque lo que se
prueba son invariantes que impone la base:

- el backfill dejó un titular vigente al 100 % y representante en cada propiedad;
- cerrar una titularidad conserva la historia;
- un reparto que no suma 100 **estalla al COMMIT y no antes** — es lo que
  permite escribir una copropiedad en varias sentencias;
- dos representantes vigentes se rechazan;
- un terreno no pregunta dormitorios y un departamento sí;
- `dormitorios` en un local se rechaza, y una clave inventada también;
- un atributo sin valor **no se construye**;
- el outbox guarda, se lee como pendiente, se marca proyectado, y una carga
  útil que no es JSON se rechaza;
- un evento sin organización no se graba.

---

## Dos hallazgos de la corrida completa

La corrida de cierre (`mvn test` con `TEST_DB_URL` y `CONTROLLOCAL_CIERRE=1`)
destapó dos cosas. **Las dos eran reales y las dos están corregidas o
explicadas.**

### A · Una regresión que había introducido V49 — CORREGIDA

```
null value in column "operacion" of relation "precio_propiedad"
violates not-null constraint
```

V49 dejó `operacion` NOT NULL y la entidad `PrecioPropiedad` no la mapeaba:
**cualquier escritura de un hito de precio fallaba**. No lo vieron las pruebas
de humo porque todos los endpoints que se probaron eran GET.

Corregido mapeando `operacion` e `idCaptacion` en la entidad, con el defecto
explícito descrito arriba. `HistoricoPrecioIntegrationTest` vuelve a **3/3**.

> Es el argumento de por qué la corrida de cierre es `Verificar-Cierre.ps1` y no
> `mvn clean install`: sin `TEST_DB_URL` estas pruebas se saltan en silencio y
> la regresión habría viajado entera.

### B · `GateDeCierreTest` haciendo su trabajo — RESUELTO

El inventario de pruebas de integración es una lista declarada, y una prueba
nueva obliga a declararla. `NucleoUniversalIntegrationTest` añadida al
inventario; **3/3**.

---

## El único fallo que queda, y no es de esta tanda

`SimulacroRecuperacionIntegrationTest.elSimulacroCompleto`:

```
ReglaNegocioException: La organizacion ya tiene un administrador operativo:
la concesion se cerro.
```

**Comprobado que es anterior a este trabajo.** La regla que dispara
(`GobiernoOperativo.hayAlgunoOperativo`) depende del ESTADO de la base, no del
esquema, y ninguna de las ocho migraciones toca gobierno, MFA ni credenciales.

La prueba definitiva se hizo sobre la **instantánea previa a las migraciones**
(`pg_dump` en v45), consultando directamente los datos:

```sql
-- sobre controllocal_antes, restaurada en v45
 organizacion_id | admins_operativos
-----------------+-------------------
               1 |                 1
```

La organización 1 **ya tenía** un administrador operativo antes de tocar nada, y
esa es exactamente la condición que cierra la concesión y lanza la excepción.

> **Es un fallo de dependencia de estado, no de código.** El simulacro asume un
> tenant sin gobierno operativo, y la base de desarrollo compartida lo tiene.
> Merece su propio arreglo —aislar el simulacro en su propio tenant— y no entra
> en esta tanda.

**Tally final:** 92 pruebas en `controllocal-app`, 0 fallos, **1 error
preexistente**. Los módulos `dominio`, `persistencia`, `servicios` y `web`
pasan enteros.

---

## Lo que todavía no hace nadie

Las entidades existen y las consultas funcionan, pero **ningún caso de uso las
llama todavía**: el alta de propiedad sigue escribiendo `id_rol_propietario` y
`precio_referencial` en `propiedad`, y nadie escribe en el outbox. Eso es lo
siguiente, y va módulo a módulo.
