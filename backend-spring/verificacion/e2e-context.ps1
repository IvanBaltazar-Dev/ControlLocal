# Contexto obligatorio para cualquier suite E2E que escriba.
# Una ejecucion directa, o que apunte al API/BD de desarrollo, falla antes de
# hacer el primer login o INSERT.

function Assert-ControlLocalE2EContext {
    $runId = $env:CONTROLLOCAL_E2E_RUN_ID
    $database = $env:CONTROLLOCAL_E2E_DB
    $baseUrl = $env:CONTROLLOCAL_E2E_BASE_URL
    $postgresContainer = $env:CONTROLLOCAL_E2E_POSTGRES_CONTAINER
    $apiContainer = $env:CONTROLLOCAL_E2E_API_CONTAINER

    if ([string]::IsNullOrWhiteSpace($runId) -or $runId -notmatch '^[a-z0-9-]+$') {
        throw 'Suite E2E bloqueada: ejecutala mediante verificacion/Invoke-E2E.ps1.'
    }
    if ($database -notmatch '^controllocal_e2e_[a-z0-9_]+$' -or $database -eq 'controllocal') {
        throw "Suite E2E bloqueada: la base '$database' no es una base exclusiva de prueba."
    }
    if ($baseUrl -notmatch '^http://localhost:\d+/controllocal/Api$' -or
        $baseUrl -eq 'http://localhost:8090/controllocal/Api') {
        throw "Suite E2E bloqueada: el API '$baseUrl' corresponde a desarrollo o no es valido."
    }
    if ($postgresContainer -notmatch '^controllocal-postgres-e2e-' -or
        $apiContainer -notmatch '^controllocal-api-e2e-') {
        throw 'Suite E2E bloqueada: los contenedores no son efimeros ni llevan el identificador de corrida.'
    }

    [pscustomobject]@{
        RunId = $runId
        Database = $database
        BaseUrl = $baseUrl
        PostgresContainer = $postgresContainer
        ApiContainer = $apiContainer
    }
}

# =====================================================================
# TOTP y enrolamiento — compartidos desde V37 (Bloque 6)
#
# POR QUE ESTO VIVE AQUI Y NO EN UNA SUITE. V37 marca `debe_enrolar_mfa`
# a todo TENANT_ADMIN, asi que su sesion nace CAPADA: solo alcanza el
# perfil, el enrolamiento y el logout. Cualquier suite que actue como
# administrador —y son casi todas— tiene que enrolar antes de operar.
#
# No se relaja la regla en el perfil de pruebas a proposito: hacerlo
# dejaria sin verificar justo lo que el bloque viene a garantizar, y las
# suites pasarian a probar un sistema que no es el que se despliega.
# =====================================================================

function ConvertFrom-Base32Rfc4648 {
    param([Parameter(Mandatory = $true)][string] $Texto)
    $alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $bits = 0; $valor = 0
    $salida = New-Object System.Collections.Generic.List[byte]
    foreach ($c in $Texto.ToUpper().ToCharArray()) {
        $indice = $alfabeto.IndexOf($c)
        if ($indice -lt 0) { continue }
        $valor = ($valor -shl 5) -bor $indice
        $bits += 5
        if ($bits -ge 8) {
            $salida.Add([byte](($valor -shr ($bits - 8)) -band 0xFF))
            $bits -= 8
        }
    }
    return $salida.ToArray()
}

function Get-PasoTotp { [long][math]::Floor(([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) / 30) }

function Get-CodigoTotp {
    param(
        [Parameter(Mandatory = $true)][string] $SecretoBase32,
        [long] $Paso = -1
    )
    if ($Paso -lt 0) { $Paso = Get-PasoTotp }
    $hmac = New-Object System.Security.Cryptography.HMACSHA1
    $hmac.Key = ConvertFrom-Base32Rfc4648 $SecretoBase32
    $contador = [BitConverter]::GetBytes([int64]$Paso)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($contador) }
    $hash = $hmac.ComputeHash($contador)
    $d = $hash[$hash.Length - 1] -band 0x0F
    $binario = ((($hash[$d] -band 0x7F) -shl 24) -bor (($hash[$d + 1] -band 0xFF) -shl 16) -bor
                (($hash[$d + 2] -band 0xFF) -shl 8) -bor ($hash[$d + 3] -band 0xFF))
    return ($binario % 1000000).ToString('000000')
}

<#
.SYNOPSIS
Deja una cuenta lista para operar: entra y, si su sesion esta capada por
`debe_enrolar_mfa`, enrola el segundo factor y vuelve a entrar por los dos
pasos.

.OUTPUTS
Un objeto con `Token` (el de sesion) y `Secreto` (el TOTP, por si la suite
necesita mas codigos).
#>
function Connect-ControlLocalE2E {
    param(
        [Parameter(Mandatory = $true)][string] $BaseUrl,
        [Parameter(Mandatory = $true)][string] $Usuario,
        [Parameter(Mandatory = $true)][string] $Contrasena,
        # Enrola aunque la cuenta no este OBLIGADA. Para BROKER y AGENTE el
        # segundo factor es posible pero no exigido (D-S0-19), asi que una
        # suite que necesite volverlos "operativos" tiene que pedirlo.
        [switch] $EnrolarSiempre
    )
    $cuerpo = @{ usuario = $Usuario; contrasena = $Contrasena } | ConvertTo-Json
    $sesion = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/login" -TimeoutSec 30 `
        -ContentType 'application/json' -Body $cuerpo

    $estado = Invoke-RestMethod -Method GET -Uri "$BaseUrl/perfil/mfa" -TimeoutSec 30 `
        -Headers @{ Authorization = "Bearer $($sesion.token)" }
    if (-not $estado.debeEnrolar -and -not $EnrolarSiempre) {
        return ($sesion | Add-Member -NotePropertyName Secreto -NotePropertyValue $null -PassThru)
    }

    $enrolamiento = Invoke-RestMethod -Method POST -Uri "$BaseUrl/perfil/mfa" -TimeoutSec 30 `
        -ContentType 'application/json' -Body '{}' `
        -Headers @{ Authorization = "Bearer $($sesion.token)" }
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/perfil/mfa/confirmar" -TimeoutSec 30 `
        -ContentType 'application/json' `
        -Body (@{ codigo = (Get-CodigoTotp $enrolamiento.secreto) } | ConvertTo-Json) `
        -Headers @{ Authorization = "Bearer $($sesion.token)" } | Out-Null

    # Confirmar INVALIDA las sesiones vivas —nacieron sin segundo factor—, asi
    # que hay que volver a entrar; y ahora por los dos pasos.
    $desafio = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/mfa/desafio" -TimeoutSec 30 `
        -ContentType 'application/json' -Body $cuerpo
    # El paso actual quedo consumido al confirmar (anti-replay, D-S0-31):
    # hay que esperar al siguiente.
    $desde = Get-PasoTotp
    while ((Get-PasoTotp) -eq $desde) { Start-Sleep -Milliseconds 500 }
    $conFactor = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/mfa/verificar" -TimeoutSec 30 `
        -ContentType 'application/json' `
        -Body (@{ desafio = $desafio.desafio; codigo = (Get-CodigoTotp $enrolamiento.secreto) } | ConvertTo-Json)

    # Se devuelve el LoginResponse COMPLETO —no solo el token— para que las
    # suites que ya miraban `.rol` o `.idUsuario` no tengan que cambiar nada mas.
    return ($conFactor | Add-Member -NotePropertyName Secreto `
        -NotePropertyValue $enrolamiento.secreto -PassThru)
}
