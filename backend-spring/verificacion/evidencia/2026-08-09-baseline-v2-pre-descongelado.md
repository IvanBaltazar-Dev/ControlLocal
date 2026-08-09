# Línea base BROX v2 — pre-descongelado (2026-08-09)

**No es una release ni un candidato a producción.** Es la fotografía verificable contra la que se
detectarán regresiones cuando empiece la evolución módulo a módulo.

---

## 1. Qué se ejecutó

| Capa | Resultado |
|---|---|
| Reactor backend completo | **verde** |
| Tests de integración contra PostgreSQL real | **37 EJECUTADOS, 0 saltados, 0 fallos** |
| Cuatro gates estructurales | verdes |
| Suite Angular | **529 / 529** |
| Suites E2E funcionales | **22 / 22 verdes** |
| Residuos de entornos E2E | **cero** |

### Los 37 de integración, uno a uno

Llevaban **toda la sesión saltándose en silencio** (`@EnabledIfEnvironmentVariable(TEST_DB_URL)`).
Esta es la primera corrida en la que se ejecutan de verdad:

| Test | Tests | Saltados |
|---|---|---|
| `BusquedaLocalesIntegrationTest` | 12 | 0 |
| `InvariantesComisionIntegrationTest` | 6 | 0 |
| `OcupacionInmuebleIntegrationTest` | 5 | 0 |
| `PadronDeGobiernoIntegrationTest` | 5 | 0 |
| `RepositorioEstadosIntegrationTest` | 4 | 0 |
| `SimulacroRecuperacionIntegrationTest` | 4 | 0 |
| `VocabularioPersistidoIntegrationTest` | 1 | 0 |

> **Por qué esto importa más que el resto de la tabla.** Ya costó caro una vez: V31, V37 y V38
> introdujeron tres columnas `estado` con la palabra completa —rompiendo el invariante de código
> unitario que `RepositorioEstadosIntegrationTest` existe para proteger— y **el build siguió verde
> durante todo el bloque de seguridad** porque ese gate nunca llegó a ejecutarse. Lo arregló V40.
> Su residuo apareció hoy: el check de `s0-mfa` que comparaba `'A'` contra `'ACTIVO'`.

### Gates estructurales

`ArquitecturaCapasTest` (app → web → service → persistence → domain), `ArquitecturaAuditoriaTest`
(toda transición pasa por `Transiciones`), `MatrizOperacionRolTest` (166 operaciones declaradas
== `@PreAuthorize` real, en los dos sentidos) y `EstadosSinProductorTest`. Más `GateDeCierreTest`,
que impide que una corrida declarada de cierre omita los tests de PostgreSQL.

### Las 22 suites E2E

| Suite | Checks | | Suite | Checks |
|---|---|---|---|---|
| `personas` | 126 | | `comision-movimientos` | 65 |
| `f4-solicitud` | 125 | | `ficha-comercial` | 61 |
| `e4-dashboard` | 120 | | `s0-contrasenas` | 59 |
| `f3-demanda` | 103 | | `reportes-propietario` | 50 |
| `s0-mfa` | 89 | | `s0-roles` | 48 |
| `demanda-busqueda` | 69 | | `solicitudes-busqueda` | 48 |
| `f6-f7-alertas-tareas` | 68 | | `v6` | 46 |
| `s0-emergencia` | 30 | | `disponibilidad-contrato` | 41 |
| `s0-bloqueo` | 21 | | `locales-listado` | 18 |
| `estabilizacion-alquiler` | 18 | | `s0-sesiones` | 11 |

Más `v6-dos-organizaciones` (valida con bloques SQL, sin marcador de checks) y `sonda-transporte`
(es una sonda: su veredicto fue *"sin pausas del entorno: se puede medir rendimiento"*).

---

## 2. Fallos y su clasificación

**Cero de categoría 1 (regresión funcional).** Ninguna.

| Suite | Categoría | Qué pasó | Desenlace |
|---|---|---|---|
| `ficha-comercial`, `f4-solicitud`, `f6-f7-alertas-tareas`, `solicitudes-busqueda` | **3 — arnés** | Murieron en el arranque del API **sin ejecutar un solo check** | Corregido el arnés; las cuatro pasan |
| `demanda-busqueda` | **4 — rendimiento** | 64 OK / 5 FALLAS tras 899 s | No reprodujo: **69/69** en 622 s |
| `personas`, `v6` | **3 — arnés** | Mismo modo de fallo | Pasan: 126/126 y 46/46 |
| `s0-mfa` | **2 — test obsoleto** | Check comparaba `'A'` contra `'ACTIVO'` tras V40 | Corregido; 89/89 |
| `locales-busqueda` | **4 — rendimiento** | Ver §3 | Deuda registrada |

### La causa raíz del arnés

`api-e2e` **no tiene healthcheck** en `docker-compose.e2e.yml`, así que `docker compose up -d --wait`
vuelve en cuanto el contenedor *corre*, no cuando está listo. Toda la espera recaía en un bucle de
**240 intentos × 500 ms ≈ 120 s**.

El arranque en frío (Flyway + Hibernate) ronda los **63 s con la máquina en reposo** y se pasa de
largo cuando está cargada. Peor: la ventana no era fija — si la conexión se rechazaba al instante
duraba 120 s, y si cada intento agotaba sus 3 s de timeout, hasta 840 s.

Se cambió a **espera por reloj (360 s)**. Esperar más no puede ocultar un defecto funcional: si el
API no arranca, ninguna suite pasa igual.

---

## 3. Deuda de rendimiento — `locales-busqueda`

**Queda fuera de la línea base funcional a propósito**, y no se toca ningún umbral.

El escenario que falla es **uno de los ocho**: `texto=Lima` (`medianamente selectivo`), con p50 339 ms
y **p95 1088 ms** contra un techo de 1000. Los otros siete pasan — incluido `texto=Avenida`, que
devuelve **100.000 filas en 496 ms**, seis veces más datos y la mitad de tiempo.

### Lo que descartó el `EXPLAIN ANALYZE`

- **No faltan índices.** Los trigram existen y el planificador los usa:
  `ix_propiedad_codigo_trgm`, `ix_propiedad_direccion_trgm`, `ix_propiedad_distrito_trgm` y
  `ix_detalle_local_rubro_trgm`.
- **No es el filtro de estado.** Con él 52 ms, sin él 46 ms. Misma estrategia. (Hipótesis propia,
  descartada por la medición.)
- **El Seq Scan de 245 ms no se reproduce**: apareció en la primera ejecución tras el `ANALYZE` y no
  volvió en cinco intentos.

### Lo que sí mide

Diez ejecuciones de la consulta real, todo en caché (`shared hit`, cero lecturas a disco):

```
n=10   min=45 ms   mediana=66 ms   max=120 ms
```

| | |
|---|---|
| Consulta en base | 45–120 ms |
| HTTP p50 del gate | 339 ms |
| HTTP p95 del gate | **1088 ms** |

**La base aporta una novena parte del p95.** Los ~950 ms restantes ocurren fuera de PostgreSQL.

### Sospechoso, con precedente en el repo

El **proxy de puertos de Docker Desktop**. `docker-compose.e2e.yml` ya lo documenta con medidas
propias: *"4 parones de ~2.070 ms en 900 llamadas"* renovando conexión, mitigado con
`SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1`. Y `diagnostico-pico-rc003-gate-f3.md` recoge un falso
positivo de 3,3 s por la misma causa.

**Pendiente**: medir en una máquina en reposo. Si el p95 baja de 1000 ms, el gate está bien y falla
el entorno de medición. **No subir el umbral** — es el compromiso de RC-003.

---

## 4. Deudas conocidas anteriores (categoría 6)

Ninguna se abrió hoy y ninguna bloquea:

- **Rotación de credenciales**: el commit `2832a9b` publicó en GitHub el secreto de firma JWT y las
  credenciales RDS. Sacadas del índice y cubiertas por `.gitignore`, pero **el historial las
  conserva** y solo la rotación cierra la exposición. Es acción del titular.
- **Recuperación de acceso sin transporte** (D-S0-11): el endpoint existe y emite el token, pero no
  hay canal configurado, así que **no llega a nadie**. El camino que funciona es la invitación.
- **Break-glass sin activar** (D-S0-53): falta designar los dos custodios reales y el canal externo.
  Sin ellos `prod` no arranca con la bandera encendida, a propósito.
- **Reportes PDF fuera de alcance** (D-F5-1): los 5 endpoints Jasper de la v1 no se portaron y no hay
  tecnología de reemplazo elegida.
- **`locales-busqueda`**: §3.

---

## 5. Notas de método que costaron tiempo

- **Tres formatos de marcador conviven** en las suites: `===== n OK / m FALLAS =====`,
  `OK: n  FALLAS: m` y `== Resultado: n OK, m fallas ==`. Un script que solo busque uno da por
  "sin resultado" suites que están verdes.
- **PowerShell 5.1 escribe con `>` en UTF-16LE**, así que `grep` y `ripgrep` no ven nada dentro de
  esos logs. Hay que leerlos con `Select-String`.
- **`personas` falló dos veces con `TEST_DB_URL` y `CONTROLLOCAL_CIERRE` heredados** de la corrida de
  cierre y pasó al quitarlos. La correlación es fuerte pero **el mecanismo no está confirmado**:
  ningún script E2E lee esas variables. `Verificar-Cierre.ps1` las exporta y después corre las
  suites en el mismo proceso; su lista por defecto son cuatro suites que no se ven afectadas.
  Queda anotado como sospecha, no como causa.
