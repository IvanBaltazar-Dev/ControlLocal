# Corrida de CIERRE: reactor completo contra PostgreSQL real y despues las
# suites E2E, en el orden acordado.
#
# POR QUE EXISTE. `mvn clean install` a secas NO es un gate de cierre: los seis
# tests de integracion llevan `@EnabledIfEnvironmentVariable(TEST_DB_URL)` y sin
# esa variable JUnit los salta EN SILENCIO, con el build en verde. Asi entraron
# V31, V37 y V38 con tres columnas `estado` en palabra completa rompiendo el
# invariante de codigo unitario, sin que nada lo dijera durante todo un bloque.
#
# Este script hace tres cosas que `mvn` solo no hace:
#   1. exige TEST_DB_URL antes de compilar nada;
#   2. exporta CONTROLLOCAL_CIERRE=1, que activa GateDeCierreTest dentro del
#      propio reactor -por si alguien lanza mvn a mano-;
#   3. comprueba en la salida que los seis tests de integracion se EJECUTARON,
#      no que "no fallaron";
#   4. corre `gate-modelo-universal.sql` contra la base real (Corte 3.a). Ese
#      fichero comprueba invariantes que ningun test de Java puede ver -- las que
#      solo se prueban intentando romperlas contra Postgres -- y hasta hoy solo
#      corria si alguien se acordaba. No se acordo nadie: llevaba en rojo desde
#      V77 y sobrevivio a tres cortes cerrados y auditados. Un gate que depende
#      de la memoria no es un gate, y esa es la misma leccion por la que existe
#      este script.
[CmdletBinding()]
param(
    [string] $UrlBaseDatos = $env:TEST_DB_URL,
    [string] $Usuario = 'controllocal',
    [string] $Clave = 'controllocal',
    # El gate .sql se corre contra la base de desarrollo, que es el corpus real y
    # es para la que esta escrito (elige propiedad, titular y encargo vivos). No
    # la ensucia: todo ocurre dentro de una transaccion que termina en ROLLBACK.
    [string] $ContenedorPostgres = 'controllocal-postgres-v2',
    [string] $BaseDelGate = 'controllocal_dev',
    # Suites E2E del cierre, en orden. Las economicas van despues de la de
    # movimientos porque comparten el ciclo de comision.
    # `editor-universal` entra en el cierre por defecto desde el bloque 3f: es
    # el gate de conservacion POR EL CABLE para los siete tipos, y un corte de
    # profundidad que rompa la edicion tiene que caer aqui, no en produccion.
    [string[]] $Suites = @('comision-movimientos', 'disponibilidad-contrato', 'f4-solicitud',
                          'estabilizacion-alquiler', 'editor-universal')
)

$ErrorActionPreference = 'Stop'
$raiz = Join-Path $PSScriptRoot '..'
# Tiene que coincidir con el inventario de GateDeCierreTest: ese test rompe el
# build si aparece una prueba de integracion nueva y no se declara, y esta lista
# es la que comprueba que ademas se EJECUTO.
$integracion = @(
    # D-E4-3: una sola autoridad persistente por clave publicada. FALTABA en
    # esta lista hasta el 2026-08-19 -GateDeCierreTest si la inventariaba, pero
    # el script no comprobaba que se hubiera ejecutado-, y era justo la prueba
    # que el 18 de agosto escribio 162 propiedades en controllocal_dev. Ahora
    # GateDeCierreTest compara las dos listas, asi que no pueden volver a
    # separarse.
    'AutoridadDelDatoIntegrationTest',
    'BusquedaLocalesIntegrationTest',
    # Corte 0B: los tres tipos nuevos, el vocabulario y la exigencia PUB.
    'CatalogoQueHablaIntegrationTest',
    # Corte 0A: la ida y vuelta de la edicion por los siete tipos.
    'ConservacionDeLaEdicionIntegrationTest',
    'ConvergenciaCampanaColaIntegrationTest',
    # D-E2-5 / E2.5: el broker tiene sus propios asuntos, y no comparte ni un id
    # con la bandeja del agente.
    'FocoDelBrokerIntegrationTest',
    'HistoricoPrecioIntegrationTest',
    # Cierre de E2.5: un broker con equipo puede entrar y su sesion resuelve a SU
    # rol, para que mirar su pantalla no dependa de la suerte.
    'IdentidadDelBrokerIntegrationTest',
    # D-E2-1 seccion 10 / E2.4: la capa de interpretacion del Inicio -- como esta,
    # el expediente de cuatro renglones y la lectura que los sintetiza.
    'InterpretacionDelInicioIntegrationTest',
    'InvariantesComisionIntegrationTest',
    # D-E4-1: las piezas del nucleo universal (titularidad, atributos, outbox).
    'NucleoUniversalIntegrationTest',
    'OcupacionInmuebleIntegrationTest',
    'PadronDeGobiernoIntegrationTest',
    # 4.P: la procedencia del DATO. Los ocho casos de reconstruccion -- alta,
    # edicion, borrado, multivalor, estructural legado, naturalezas mezcladas,
    # inferencia incompleta y encargo.
    'ProcedenciaDelValorIntegrationTest',
    # D-E4-1 / D-E4-2: los escenarios de aceptacion de la propiedad universal y
    # la captura. Es el unico que COMETE de verdad, en tenants propios.
    # La Propiedad como activo de dato: conocida, con procedencia y observaciones.
    'PropiedadComoActivoDeDatoIntegrationTest',
    # Convergencia del 0C: una propiedad puede existir sin estar encargada.
    'PropiedadSinEncargoIntegrationTest',
    'PropiedadUniversalIntegrationTest',
    'RepositorioEstadosIntegrationTest',
    'SimulacroRecuperacionIntegrationTest',
    # Corte 0C: el sujeto del dato, y que ningun encargo contamine a otro.
    'SujetoDelDatoIntegrationTest',
    'VocabularioPersistidoIntegrationTest'
)

function Abortar($mensaje) {
    Write-Host "`nCIERRE ABORTADO: $mensaje" -ForegroundColor Red
    exit 1
}

Write-Host "== 1. Requisitos de la corrida de cierre ==" -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($UrlBaseDatos)) {
    Abortar @'
falta TEST_DB_URL.

Sin ella los seis tests de integracion se saltan en silencio y el verde no
significa nada: es exactamente asi como se colaron las tres columnas de estado
que rompieron el invariante unitario.

  $env:TEST_DB_URL = "jdbc:postgresql://localhost:5433/controllocal_repositorios"
'@
}
if ($UrlBaseDatos -notlike 'jdbc:postgresql:*') {
    Abortar "TEST_DB_URL tiene que apuntar a PostgreSQL real: $UrlBaseDatos"
}
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    Abortar 'falta JAVA_HOME. El reactor compila con release 21 y necesita un JDK 21 o superior.'
}
Write-Host "  OK   TEST_DB_URL = $UrlBaseDatos"
Write-Host "  OK   JAVA_HOME   = $env:JAVA_HOME"

$env:TEST_DB_URL = $UrlBaseDatos
$env:TEST_DB_USER = $Usuario
$env:TEST_DB_PASSWORD = $Clave
# Activa GateDeCierreTest: dentro del reactor, la ausencia de TEST_DB_URL deja
# de ser un salto silencioso y pasa a ser un fallo.
$env:CONTROLLOCAL_CIERRE = '1'

Write-Host "`n== 2. Gate del modelo universal contra la base real ==" -ForegroundColor Cyan
# Va ANTES del reactor a proposito: es la comprobacion mas barata de la corrida
# -- segundos contra los minutos de `mvn clean install` -- y la que mas veces ha
# tenido razon. Si el esquema o la siembra estan mal, saberlo antes de compilar.
$gate = Join-Path $PSScriptRoot 'gate-modelo-universal.sql'
if (-not (Test-Path $gate)) {
    Abortar "no se encuentra el gate del modelo universal en $gate"
}
# psql necesita el fichero DENTRO del contenedor: se copia y se ejecuta con -f.
# Pasarlo por stdin no sirve -- el gate usa \gset y \o, que son del cliente.
& docker cp $gate "${ContenedorPostgres}:/tmp/gate-modelo-universal.sql"
if ($LASTEXITCODE -ne 0) {
    Abortar @"
no se pudo copiar el gate al contenedor '$ContenedorPostgres'.

El gate del modelo universal es parte del cierre, asi que no correrlo NO es una
opcion: levanta la base y repite.

  docker compose -f backend-spring/docker-compose.yml up -d
"@
}
& docker exec $ContenedorPostgres psql -U $Usuario -d $BaseDelGate -v ON_ERROR_STOP=1 -f /tmp/gate-modelo-universal.sql
if ($LASTEXITCODE -ne 0) {
    Abortar "el gate del modelo universal salio en rojo (codigo $LASTEXITCODE). El detalle esta arriba: cada comprobacion dice OK o FALLO."
}
Write-Host "  OK   gate del modelo universal en verde sobre $BaseDelGate"

Write-Host "`n== 3. Reactor completo contra PostgreSQL real ==" -ForegroundColor Cyan
$salida = Join-Path ([IO.Path]::GetTempPath()) 'controllocal-cierre-reactor.log'
# Sin 2>&1: en PowerShell 5.1 esa redireccion convierte el stderr de un nativo
# en error terminante. Se captura con Tee-Object, que respeta los flujos.
& mvn -f (Join-Path $raiz 'pom.xml') clean install | Tee-Object -FilePath $salida
$codigoMaven = $LASTEXITCODE
if ($codigoMaven -ne 0) {
    Abortar "el reactor fallo (codigo $codigoMaven). Detalle en $salida"
}

Write-Host "`n== 4. Los tests de integracion se EJECUTARON, no se saltaron ==" -ForegroundColor Cyan
$texto = Get-Content -Raw $salida
$ausentes = @()
foreach ($nombre in $integracion) {
    if ($texto -notmatch [regex]::Escape("Tests run:") -or
        $texto -notmatch ("(?m)Tests run:.*-- in .*" + [regex]::Escape($nombre))) {
        $ausentes += $nombre
    } else {
        Write-Host "  OK   $nombre ejecutado"
    }
}
if ($ausentes.Count -gt 0) {
    Abortar ("estos tests de integracion NO aparecen ejecutados: " + ($ausentes -join ', ') +
             ". El reactor termino verde sin comprobarlos.")
}

Write-Host "`n== 5. Suites E2E del cierre ==" -ForegroundColor Cyan
foreach ($suite in $Suites) {
    Write-Host "`n-- suite $suite --" -ForegroundColor Yellow
    & powershell -File (Join-Path $PSScriptRoot 'Invoke-E2E.ps1') -Suite $suite
    if ($LASTEXITCODE -ne 0) {
        Abortar "la suite E2E '$suite' fallo (codigo $LASTEXITCODE)."
    }
}

Write-Host "`n== CIERRE VERDE ==" -ForegroundColor Green
Write-Host "Gate del modelo universal + reactor con PostgreSQL real + $($Suites.Count) suites E2E." -ForegroundColor Green
Write-Host "Log del reactor: $salida"
exit 0
