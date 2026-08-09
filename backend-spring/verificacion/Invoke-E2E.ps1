# Crea un PostgreSQL y un API efimeros POR SUITE, la ejecuta y garantiza su
# eliminacion. No reutiliza la base `controllocal` de desarrollo.
#
# Por que un entorno por suite y no uno por invocacion (cambio del 2026-08-03):
# las suites de busqueda cargan 100.000 filas por tabla. Mientras el entorno era
# uno solo para varias suites, cada una tenia que retirar su banco a mano con
# `delete ... like` para no contaminar a la siguiente, y eso costaba MAS de ocho
# minutos —mas que su propia fase de medicion— para borrar filas que iban a
# morir con la base unos segundos despues. Con un entorno por suite el
# aislamiento lo da la base efimera, la limpieza es tirarla entera y las suites
# ya no borran nada. El coste que se paga a cambio es el arranque del entorno de
# la segunda suite en adelante; una invocacion de una sola suite —el caso
# normal— no paga nada.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        'locales-listado', 'locales-busqueda', 'demanda-busqueda', 'solicitudes-busqueda',
        'sonda-transporte',
        'v6', 'f3-demanda', 'f4-solicitud',
        'f6-f7-alertas-tareas', 'personas', 'reportes-propietario',
        'ficha-comercial', 'e4-dashboard', 'estabilizacion-alquiler', 'comision-movimientos',
        'disponibilidad-contrato',
        'v6-dos-organizaciones',
        's0-sesiones', 's0-bloqueo', 's0-contrasenas', 's0-roles', 's0-mfa',
        's0-emergencia'
    )]
    [string[]] $Suite
)

$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '..\docker-compose.e2e.yml'
$jar = Join-Path $PSScriptRoot '..\controllocal-app\target\controllocal-app-2.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw "No existe el jar del API: $jar. Ejecuta mvn install antes de la suite."
}

# Hash PBKDF2 en el formato de PasswordHasher: pbkdf2$<iter>$<salB64>$<hashB64>,
# HMAC-SHA256, 100.000 iteraciones, sal de 16 bytes y clave de 32.
#
# Existe para que los custodios de la suite de emergencia se generen EN LA
# CORRIDA y mueran con su base efimera. Un par (secreto, hash) versionado seria
# un custodio real con el secreto publicado (D-S0-53).
function New-HashPbkdf2 {
    param([Parameter(Mandatory = $true)][string] $Secreto)
    $sal = New-Object byte[] 16
    ([Security.Cryptography.RandomNumberGenerator]::Create()).GetBytes($sal)
    $derivador = New-Object Security.Cryptography.Rfc2898DeriveBytes(
        $Secreto, $sal, 100000, [Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        return 'pbkdf2$100000$' + [Convert]::ToBase64String($sal) + '$' +
               [Convert]::ToBase64String($derivador.GetBytes(32))
    } finally { $derivador.Dispose() }
}

function New-SecretoAleatorio {
    $material = New-Object byte[] 16
    ([Security.Cryptography.RandomNumberGenerator]::Create()).GetBytes($material)
    return ([Convert]::ToBase64String($material)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Get-FreeTcpPort {
    $listener = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        $listener.LocalEndpoint.Port
    } finally {
        $listener.Stop()
    }
}

function Invoke-SuiteAislada {
    param([Parameter(Mandatory = $true)][string] $Nombre)

    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss')
    $random = Get-Random -Minimum 1000 -Maximum 9999
    $runId = "$stamp-$random"
    $dbRunId = $runId.Replace('-', '_')
    $project = "controllocal-e2e-$runId"

    $env:CONTROLLOCAL_E2E_RUN_ID = $runId
    $env:CONTROLLOCAL_E2E_DB = "controllocal_e2e_$dbRunId"
    $env:CONTROLLOCAL_E2E_API_PORT = [string](Get-FreeTcpPort)
    $env:CONTROLLOCAL_E2E_BASE_URL = "http://localhost:$($env:CONTROLLOCAL_E2E_API_PORT)/controllocal/Api"
    $env:CONTROLLOCAL_E2E_POSTGRES_CONTAINER = "controllocal-postgres-e2e-$runId"
    $env:CONTROLLOCAL_E2E_API_CONTAINER = "controllocal-api-e2e-$runId"

    # Bloqueo por cuenta e IP (D-S0-21): la suite que lo prueba necesita
    # umbrales BAJOS para poder provocarlo; el resto los necesita altos para no
    # bloquearse a si misma. El contador solo cuenta fallos, asi que los logins
    # correctos de las demas suites no consumen cupo de todas formas.
    if ($Nombre -eq 's0-bloqueo') {
        $env:CONTROLLOCAL_E2E_MAX_FALLOS_CUENTA = '3'
        $env:CONTROLLOCAL_E2E_MAX_FALLOS_IP = '50'
    } else {
        $env:CONTROLLOCAL_E2E_MAX_FALLOS_CUENTA = '100'
        $env:CONTROLLOCAL_E2E_MAX_FALLOS_IP = '200'
    }

    # Recuperacion de emergencia: encendida SOLO en su suite, y con custodios
    # generados aqui mismo. Los secretos viajan a la suite por variable de
    # entorno y desaparecen con el proceso; los hashes, al contenedor.
    if ($Nombre -eq 's0-emergencia') {
        $env:CONTROLLOCAL_E2E_RECUPERACION = 'true'
        $env:CONTROLLOCAL_E2E_CUSTODIO_A_ID = 'custodio-e2e-a'
        $env:CONTROLLOCAL_E2E_CUSTODIO_B_ID = 'custodio-e2e-b'
        $env:CONTROLLOCAL_E2E_CUSTODIO_A_SECRETO = New-SecretoAleatorio
        $env:CONTROLLOCAL_E2E_CUSTODIO_B_SECRETO = New-SecretoAleatorio
        $env:CONTROLLOCAL_E2E_CUSTODIO_A_HASH = New-HashPbkdf2 $env:CONTROLLOCAL_E2E_CUSTODIO_A_SECRETO
        $env:CONTROLLOCAL_E2E_CUSTODIO_B_HASH = New-HashPbkdf2 $env:CONTROLLOCAL_E2E_CUSTODIO_B_SECRETO
    } else {
        $env:CONTROLLOCAL_E2E_RECUPERACION = 'false'
    }

    $entornoIntentado = $false
    $fallo = $null
    try {
        Write-Host "`n== Entorno E2E aislado $runId ==" -ForegroundColor Cyan
        Write-Host "Base: $($env:CONTROLLOCAL_E2E_DB)"
        $entornoIntentado = $true
        docker compose -p $project -f $compose up -d --wait
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo levantar el entorno E2E aislado.' }

        $salud = "$($env:CONTROLLOCAL_E2E_BASE_URL)/salud"
        $disponible = $false

        # Espera POR RELOJ, no por numero de intentos.
        #
        # Antes era `for ($intento = 1; $intento -le 240; ...)` con medio segundo
        # de pausa, y eso tiene dos problemas. El primero es que la ventana real
        # no era fija: si la conexion se rechaza al instante el bucle dura ~120 s,
        # pero si cada intento agota sus 3 s de timeout dura hasta 840 s. El
        # segundo es que 120 s se quedan cortos: el arranque en frio (Flyway +
        # Hibernate) ronda los 63 s con la maquina en reposo y se pasa de largo
        # cuando esta cargada.
        #
        # Costo una linea base entera el 2026-08-09: seis suites —v6, personas,
        # ficha-comercial, f4-solicitud, solicitudes-busqueda y f6-f7— murieron
        # SIN EJECUTAR UN SOLO CHECK despues de una hora de suites encadenadas,
        # y las seis estaban verdes ese mismo dia. Un arnes que se rinde antes
        # de que el servicio arranque no mide el producto, mide la maquina.
        #
        # Esperar mas NO puede ocultar un defecto funcional: si el API no
        # arranca, ninguna suite pasa igualmente.
        $limite = (Get-Date).AddSeconds(360)
        $arranque = Get-Date
        while ((Get-Date) -lt $limite) {
            try {
                $respuesta = Invoke-RestMethod -Method GET -Uri $salud -TimeoutSec 5
                if ($respuesta) { $disponible = $true; break }
            } catch {
                Start-Sleep -Milliseconds 500
            }
        }
        if (-not $disponible) {
            $esperado = [int]((Get-Date) - $arranque).TotalSeconds
            throw "El API E2E no quedo disponible en $salud tras $esperado s."
        }

        Write-Host "`n== Suite $Nombre / corrida $runId ==" -ForegroundColor Cyan
        if ($Nombre -eq 'v6-dos-organizaciones') {
            $sql = Join-Path $PSScriptRoot "$Nombre.sql"
            if (-not (Test-Path -LiteralPath $sql)) { throw "No existe la suite $sql." }
            Get-Content -LiteralPath $sql -Raw | docker exec -i $env:CONTROLLOCAL_E2E_POSTGRES_CONTAINER `
                psql -U controllocal -d $env:CONTROLLOCAL_E2E_DB -v ON_ERROR_STOP=1
            if ($LASTEXITCODE -ne 0) { throw "La suite $Nombre termino con codigo $LASTEXITCODE." }
        } else {
            $script = Join-Path $PSScriptRoot "e2e-$Nombre.ps1"
            if (-not (Test-Path -LiteralPath $script)) { throw "No existe la suite $script." }
            & powershell -NoProfile -ExecutionPolicy Bypass -File $script
            if ($LASTEXITCODE -ne 0) { throw "La suite $Nombre termino con codigo $LASTEXITCODE." }
        }
    } catch {
        $fallo = $_
        if ($entornoIntentado) {
            Write-Host "`n== Log del API E2E antes de limpiar ==" -ForegroundColor Yellow
            docker logs $env:CONTROLLOCAL_E2E_API_CONTAINER --tail 200 2>&1
        }
    } finally {
        # La limpieza es ESTO: se elimina la base exclusiva de la corrida con su
        # contenedor (PostgreSQL vive en tmpfs, asi que el almacen muere con el),
        # la red y cualquier volumen del proyecto. Ninguna suite borra filas.
        if ($entornoIntentado) {
            $cronometro = [Diagnostics.Stopwatch]::StartNew()
            docker compose -p $project -f $compose down -v --remove-orphans
            $cronometro.Stop()
            if ($LASTEXITCODE -ne 0 -and $null -eq $fallo) {
                $fallo = [Exception]'La limpieza del entorno E2E fallo.'
            }
            Write-Host ("Base {0} y su entorno eliminados en {1:n1} s." -f `
                $env:CONTROLLOCAL_E2E_DB, $cronometro.Elapsed.TotalSeconds) -ForegroundColor DarkGray
        }

        $contenedores = docker ps -a --filter "label=com.docker.compose.project=$project" --format '{{.Names}}'
        $volumenes = docker volume ls --filter "label=com.docker.compose.project=$project" --format '{{.Name}}'
        $redes = docker network ls --filter "label=com.docker.compose.project=$project" --format '{{.Name}}'
        $residuos = @($contenedores) + @($volumenes) + @($redes) | Where-Object { $_ }
        if ($residuos.Count -gt 0) {
            if ($null -eq $fallo) {
                $fallo = [Exception]"Quedaron recursos E2E residuales: $($residuos -join ', ')"
            }
        } else {
            Write-Host "Entorno $runId eliminado: sin contenedores, volumenes ni red residuales." -ForegroundColor Green
        }

        Remove-Item Env:CONTROLLOCAL_E2E_RUN_ID -ErrorAction SilentlyContinue
        Remove-Item Env:CONTROLLOCAL_E2E_DB -ErrorAction SilentlyContinue
        Remove-Item Env:CONTROLLOCAL_E2E_API_PORT -ErrorAction SilentlyContinue
        Remove-Item Env:CONTROLLOCAL_E2E_BASE_URL -ErrorAction SilentlyContinue
        Remove-Item Env:CONTROLLOCAL_E2E_POSTGRES_CONTAINER -ErrorAction SilentlyContinue
        Remove-Item Env:CONTROLLOCAL_E2E_API_CONTAINER -ErrorAction SilentlyContinue
        # Los secretos de custodio mueren con la corrida, igual que su base.
        foreach ($variable in 'CONTROLLOCAL_E2E_RECUPERACION',
                              'CONTROLLOCAL_E2E_CUSTODIO_A_ID', 'CONTROLLOCAL_E2E_CUSTODIO_B_ID',
                              'CONTROLLOCAL_E2E_CUSTODIO_A_HASH', 'CONTROLLOCAL_E2E_CUSTODIO_B_HASH',
                              'CONTROLLOCAL_E2E_CUSTODIO_A_SECRETO', 'CONTROLLOCAL_E2E_CUSTODIO_B_SECRETO') {
            Remove-Item "Env:$variable" -ErrorAction SilentlyContinue
        }
    }

    if ($null -ne $fallo) { throw $fallo }
}

# Una suite que falla no impide limpiar el entorno de las siguientes: cada una
# tiene el suyo y se destruye en su propio `finally`. El primer fallo se
# propaga al terminar todas.
$fallos = @()
foreach ($nombre in $Suite) {
    try { Invoke-SuiteAislada -Nombre $nombre }
    catch { $fallos += $_; Write-Host "`n== Suite $nombre FALLO ==" -ForegroundColor Red }
}
if ($fallos.Count -gt 0) { throw $fallos[0] }
