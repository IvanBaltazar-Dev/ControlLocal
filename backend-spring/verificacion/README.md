# Verificación E2E

Scripts que comprueban lo que un test unitario no puede: el comportamiento de la API contra
PostgreSQL real, por el cable.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f4-solicitud
```

`Invoke-E2E.ps1` crea **por suite** una base vacía con identificador de corrida, deja que Flyway la
reconstruya, levanta la API en un puerto aleatorio y elimina en un `finally` contenedores, redes,
volúmenes y la propia base. Antes de ejecutar hace falta el jar: `mvn install`.

> **Prohibido lanzar los `e2e-*.ps1` o los `.sql` directamente contra la base de desarrollo.** Cada
> script lleva una guarda que falla antes del primer login o del primer INSERT. Ni
> `controllocal_dev` ni la API de `:8090` participan.

**La limpieza es tirar la base entera, no borrar filas.** Las suites de búsqueda cargan 100.000
filas por tabla; retirarlas con `delete … like` costaba el **61 % del reloj** de una corrida —más
que todas sus comprobaciones juntas— para vaciar tablas que iban a morir con el contenedor unos
segundos después. El `compose down -v` que lo hace de verdad tarda entre 1,6 y 3,4 s.

Un entorno por suite arregla además el problema de encadenarlas: el limitador de peticiones es
parte del contrato, así que a partir de la tercera los logins recibían **429** y la corrida moría
sin un solo check en rojo. Con una API por suite el contador arranca de cero.

## Las suites

| Suite | Qué fija |
|---|---|
| `v6` | Flujo de captación completo sobre el núcleo multi-tenant y su aislamiento |
| `f3-demanda` | Cliente, requerimiento, oportunidad, visita, interacción y *matching* |
| `f4-solicitud` | El ciclo que cierra el negocio: solicitud → documentos → evaluación → contrato |
| `f6-f7-alertas-tareas` | La campana de alertas y la bandeja de tareas del agente |
| `personas` | Alta y ficha de agentes, brokers y propietarios; supervisión y reasignación |
| `reportes-propietario` | Reporte de avance por encargo, periodo y organización |
| `ficha-comercial` | Las lecturas transversales de cliente y propietario |
| `e4-dashboard` | Dashboard, indicadores y seguimiento comercial |
| `estabilizacion-alquiler` | Contrato económico transversal: moneda, vigencias, cascada del cierre y KPI |
| `comision-movimientos` | El dinero de verdad: cobros parciales, pagos, reversiones y ajustes. Los estados **se derivan del saldo**, no los elige quien llama |
| `disponibilidad-contrato` | Terminar un contrato **no** devuelve el inmueble al mercado. El caso que más importa es la renovación |
| `editor-universal` | `PUT /propiedades/{id}` con un JSON parcial, para **los siete tipos**: tocar una cosa deja todo lo demás idéntico |
| `locales-listado` · `locales-busqueda` | Bandejas y texto libre sobre miles de filas, con sus índices |
| `demanda-busqueda` · `solicitudes-busqueda` | Los gates de latencia de las bandejas de F3 y F4 sobre 100.000 filas |
| `sonda-transporte` | Si el transporte permite medir rendimiento ahora mismo |
| `s0-sesiones` | Una sesión se puede matar sin tocar el token |
| `s0-bloqueo` | Bloqueo por cuenta e IP, y su auditoría |
| `s0-contrasenas` | Política, cambio, recuperación y que nada público revele si una cuenta existe |
| `s0-roles` | Gobernar no es operar: la banda efectiva y las 26 operaciones regateadas |
| `s0-mfa` | TOTP, elevación y el invariante de administrador **operativo**. Tarda minutos y casi todo es esperar el paso de 30 s: el anti-replay no se recorta |
| `s0-emergencia` | El simulacro completo: dos custodios aprueban por separado, una sola aprobación no habilita |
| `v6-dos-organizaciones` | Que un tenant no vea al otro |

`gate-modelo-universal.sql` comprueba invariantes que ningún test de Java puede ver —las que solo
se prueban intentando romperlas contra Postgres—. Lo corre la corrida de cierre; **no depende de
que alguien se acuerde**, que es exactamente por lo que estuvo en rojo desde V77 y sobrevivió a
tres cortes auditados.

**El recuento no se escribe aquí**: lo imprime la propia corrida, y una cifra a mano envejece a
mentira sin que nada avise. Lo que sí se dice es que **pasa verde contra las dos bases**,
`controllocal_dev` y `controllocal_repositorios`, desde D0 (2026-08-30). Antes de ese saneamiento
sólo pasaba contra `dev`: la base de pruebas arrastraba residuo de corridas anteriores a 4.P y un
rojo de residuo se confunde con un rojo de defecto.

`sanear-residuo-de-pruebas.sql` **no es una migración y no toca el esquema**: repara datos de
`controllocal_repositorios` que ninguna escritura de hoy puede producir y que hacían mentir a dos
comprobaciones del gate. Mide antes, actúa, vuelve a medir **con los mismos predicados del gate** y
**termina en rojo si queda alguna infractora**. Es idempotente y sobre `controllocal_dev` es un
no-op. El porqué de cada línea está en su cabecera y en
`evidencia/2026-08-30-d0-saneamiento-post-5b.md`.

```powershell
Get-Content backend-spring/verificacion/sanear-residuo-de-pruebas.sql -Raw |
    docker exec -i controllocal-postgres-v2 psql -U controllocal -d controllocal_repositorios -v ON_ERROR_STOP=1
```

## La corrida de cierre

```powershell
powershell -File backend-spring/verificacion/Verificar-Cierre.ps1
```

Existe porque **`mvn clean install` a secas no es un gate**: los tests de integración llevan
`@EnabledIfEnvironmentVariable(TEST_DB_URL)` y sin esa variable JUnit los salta **en silencio**,
con el build en verde. El script exige `TEST_DB_URL`, exporta `CONTROLLOCAL_CIERRE=1` para que el
gate se active también dentro del reactor, **comprueba en la salida que los tests de integración se
ejecutaron** —no que no fallaron— y corre el gate SQL contra la base real.

## Tres trampas de PowerShell 5.1

Las tres costaron corridas enteras, y ninguna da un error que apunte a la causa.

- **Los scripts son ASCII puro y sin BOM.** PowerShell 5.1 lee un `.ps1` sin BOM como ANSI: un solo
  guion largo o una `ñ` **dentro de un comentario** rompe el análisis del fichero entero, con un
  error que señala una línea perfectamente sana.
- **No invoques las suites con `2>&1` ni `2>$null`.** El progreso de `docker compose` va por stderr
  y esas redirecciones lo convierten en error terminante: el entorno muere antes de empezar. Para
  capturar, usa `Start-Transcript`.
- **`-File` no admite listas.** PowerShell entrega la coma como un único string. Si necesitas varias
  suites: `powershell -Command "& '…\Invoke-E2E.ps1' -Suite a,b"`.

Y una de medición: **no compiles el frontend mientras corre una suite**. Las de búsqueda afirman
p95 y peor caso en la misma máquina, así que un `ng build` en paralelo las tumba solo por tiempos.
Antes de culpar a un cambio, medir: dos rojos históricos de latencia resultaron ser contención de
la máquina y un proxy de puertos de Docker Desktop, no el producto.

## La evidencia

Cada corrida deja su registro en [`evidencia/`](evidencia/), con fecha en el nombre. Es el sitio
donde mirar qué pasó de verdad en un cierre, en lugar de fiarse de un número copiado a un
documento.
