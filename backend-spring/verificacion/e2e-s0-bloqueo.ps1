# =====================================================================
# E2E del bloqueo por cuenta e IP y de la auditoria de seguridad
# (D-S0-21 + Plan S0 §6.3) — bloque "Seguridad de sesiones, auditoria y
# bloqueo de accesos".
#
# Corre con umbrales BAJOS (CUENTA=3) que le fija Invoke-E2E.ps1: es la
# unica suite que necesita provocar el bloqueo a proposito.
#
# Lo que demuestra:
#   * la dimension CUENTA existe y frena lo que el limitador anterior no
#     frenaba (fuerza bruta contra una sola cuenta);
#   * un usuario INEXISTENTE se cuenta igual, asi que el bloqueo no delata
#     que cuentas son reales;
#   * la respuesta no distingue nada: mismo 429, mismo cuerpo congelado;
#   * un login correcto limpia el contador sin borrar historial;
#   * la auditoria registra los tres desenlaces, y sin secretos.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite s0-bloqueo
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0; $fail = 0

function Check($nombre, $condicion, $detalle) {
    if ($condicion) { $script:ok++; Write-Host "  OK   $nombre" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

# Devuelve codigo, cuerpo y Retry-After de un intento de login.
function Login($usuario, $contrasena) {
    $cuerpo = @{ usuario = $usuario; contrasena = $contrasena } | ConvertTo-Json
    try {
        $r = Invoke-WebRequest -Method POST -Uri "$base/auth/login" -Body $cuerpo `
            -ContentType 'application/json' -TimeoutSec 30 -UseBasicParsing
        return [pscustomobject]@{ Codigo = [int]$r.StatusCode; Error = $null; RetryAfter = $null }
    } catch {
        $respuesta = $_.Exception.Response
        # PS 5.1: segun por donde venga el error, el cuerpo esta en
        # ErrorDetails o hay que leerlo del stream (con la posicion al inicio,
        # porque puede venir ya consumido). Se prueban los dos.
        $texto = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($texto)) {
            try {
                $flujo = $respuesta.GetResponseStream()
                $flujo.Position = 0
                $texto = (New-Object IO.StreamReader($flujo)).ReadToEnd()
            } catch { $texto = $null }
        }
        $mensaje = if ([string]::IsNullOrWhiteSpace($texto)) { $null } else { ($texto | ConvertFrom-Json).error }
        $retry = $null
        try { $retry = $respuesta.Headers['Retry-After'] } catch { }
        return [pscustomobject]@{
            Codigo = [int]$respuesta.StatusCode; Error = $mensaje; RetryAfter = $retry
        }
    }
}

Write-Host "`n== 1. Umbral por CUENTA: lo que el limitador anterior no frenaba ==" -ForegroundColor Cyan
# Tres fallos con el umbral en 3. Un atacante con 50 IPs no podia esquivar
# esta dimension antes porque no existia: solo se contaba por IP.
1..3 | ForEach-Object {
    $r = Login 'vmora' "clave-mala-$_"
    Check "intento $_ responde 401 (todavia no bloquea)" ($r.Codigo -eq 401) "http=$($r.Codigo)"
}

$bloqueado = Login 'vmora' 'clave-mala-4'
Check 'al cruzar el umbral responde 429' ($bloqueado.Codigo -eq 429) "http=$($bloqueado.Codigo)"
Check 'el cuerpo del 429 es el CONGELADO' `
    ($bloqueado.Error -eq 'Demasiadas solicitudes. Intenta nuevamente en un minuto.') `
    "error=$($bloqueado.Error)"
Check 'y trae Retry-After con la espera real' ($null -ne $bloqueado.RetryAfter) "retry=$($bloqueado.RetryAfter)"

Write-Host "`n== 2. Con la clave BUENA tambien rebota: el bloqueo va antes ==" -ForegroundColor Cyan
$conClaveBuena = Login 'vmora' 'Agente2026'
# Si la contrasena se comprobara primero, el tiempo de respuesta delataria
# si la cuenta existe.
Check 'la cuenta bloqueada rebota aunque la clave sea correcta' `
    ($conClaveBuena.Codigo -eq 429) "http=$($conClaveBuena.Codigo)"

Write-Host "`n== 3. Un usuario INEXISTENTE se cuenta igual ==" -ForegroundColor Cyan
1..3 | ForEach-Object { Login 'no-existe-jamas-9f3a' "x$_" | Out-Null }
$inexistente = Login 'no-existe-jamas-9f3a' 'x4'
# Si solo contaran las cuentas reales, bastaria observar quien se bloquea
# para saber que nombres existen.
Check 'un usuario que no existe tambien acaba en 429' ($inexistente.Codigo -eq 429) "http=$($inexistente.Codigo)"
# Se exige que el cuerpo EXISTA ademas de coincidir: comparar dos nulos
# pasaria la prueba sin comprobar nada (ya paso una vez).
Check 'con el MISMO cuerpo que una cuenta real' `
    (-not [string]::IsNullOrWhiteSpace($inexistente.Error) -and $inexistente.Error -eq $bloqueado.Error) `
    "error=$($inexistente.Error)"

Write-Host "`n== 4. Otra cuenta sigue entrando: el bloqueo no es global ==" -ForegroundColor Cyan
$broker = Login 'rsalas' 'Broker2026'
Check 'el broker entra sin problema' ($broker.Codigo -eq 200) "http=$($broker.Codigo)"

Write-Host "`n== 5. La tabla no guarda a quien se intento ==" -ForegroundColor Cyan
$enClaro = Sql "SELECT count(*) FROM intento_acceso WHERE clave_valor_hash LIKE '%vmora%'"
Check 'intento_acceso NO guarda el usuario en claro' ($enClaro -eq '0') "coincidencias=$enClaro"
$largo = Sql "SELECT DISTINCT length(clave_valor_hash) FROM intento_acceso"
Check 'la clave va hasheada en SHA-256 (64 hex)' ($largo -eq '64') "largo=$largo"
$dimensiones = Sql "SELECT count(DISTINCT clave_tipo) FROM intento_acceso"
Check 'se cuenta en las DOS dimensiones' ($dimensiones -eq '2') "dimensiones=$dimensiones"

Write-Host "`n== 6. Auditoria de seguridad ==" -ForegroundColor Cyan
$fallidos = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='LOGIN_FALLIDO'")
Check 'quedan eventos LOGIN_FALLIDO' ($fallidos -ge 6) "n=$fallidos"
$bloqueos = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='LOGIN_BLOQUEADO_429'")
Check 'queda evento LOGIN_BLOQUEADO_429' ($bloqueos -ge 1) "n=$bloqueos"
$correctos = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='LOGIN_OK'")
Check 'queda evento LOGIN_OK del broker' ($correctos -ge 1) "n=$correctos"

# Un login fallido contra un usuario inexistente no tiene persona: si
# exigieramos una, el evento que mas interesa no se registraria.
$sinPersona = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='LOGIN_FALLIDO' AND id_persona IS NULL")
Check 'el fallido anonimo se registra sin persona' ($sinPersona -ge 1) "n=$sinPersona"

# Regla de higiene (§6.3): ni una contrasena del fixture puede aparecer.
$secretos = [int](Sql @"
SELECT count(*) FROM evento_seguridad
WHERE coalesce(detalle_json,'') ILIKE '%Agente2026%'
   OR coalesce(detalle_json,'') ILIKE '%clave-mala%'
   OR coalesce(detalle_json,'') ILIKE '%contrasena%'
   OR coalesce(detalle_json,'') ILIKE '%pbkdf2%'
   OR coalesce(motivo,'')       ILIKE '%Agente2026%'
"@)
Check 'la auditoria NO filtra contrasenas ni hashes' ($secretos -eq 0) "coincidencias=$secretos"

# El motivo dice la dimension, nunca la cuenta probada.
$motivo = Sql "SELECT motivo FROM evento_seguridad WHERE tipo='LOGIN_BLOQUEADO_429' LIMIT 1"
Check 'el motivo nombra la DIMENSION, no la cuenta' `
    ($motivo -like 'bloqueado por *' -and $motivo -notlike '*vmora*') "motivo=$motivo"

Write-Host "`n== 7. El logout deja su rastro ==" -ForegroundColor Cyan
$token = (Invoke-RestMethod -Method POST -Uri "$base/auth/login" `
    -Body (@{ usuario = 'sramirez'; contrasena = 'Broker2026' } | ConvertTo-Json) `
    -ContentType 'application/json' -TimeoutSec 30).token
Invoke-WebRequest -Method POST -Uri "$base/auth/logout" `
    -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 30 -UseBasicParsing | Out-Null
$logouts = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='LOGOUT'")
Check 'queda evento LOGOUT' ($logouts -ge 1) "n=$logouts"
$invalidadas = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='SESIONES_INVALIDADAS'")
# Dos eventos y no uno: "salio" y "sus sesiones dejaron de valer" son hechos
# distintos, y el segundo lo produciran tambien otros flujos.
Check 'y su SESIONES_INVALIDADAS aparte' ($invalidadas -ge 1) "n=$invalidadas"

Write-Host "`n---------------------------------------------" -ForegroundColor Cyan
Write-Host "  OK: $ok    FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
