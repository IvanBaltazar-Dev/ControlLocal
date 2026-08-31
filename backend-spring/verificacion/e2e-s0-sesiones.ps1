# =====================================================================
# E2E de la invalidacion de SESIONES (D-S0-12) — bloque "Seguridad de
# sesiones, auditoria y bloqueo de accesos".
#
# Nada que ver con la autorizacion de datos personales (D-27), que es una
# constancia del alta y NO tiene flujo de revocacion. Aqui "revocar" es
# siempre "invalidar sesiones de usuario".
#
# Lo que demuestra, que es justo lo que antes no se podia demostrar:
# un token FIRMADO y NO EXPIRADO deja de servir en cuanto la cuenta cierra
# sesion, sin tocar el formato del token (que sigue congelado).
#
# Uso: powershell -File backend-spring/verificacion/e2e-s0-sesiones.ps1
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

function Api($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $parametros = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 6)
        $parametros['ContentType'] = 'application/json'
    }
    Invoke-RestMethod @parametros
}

# Codigo HTTP de una llamada que se espera que falle.
function CodigoDe($metodo, $ruta, $token) {
    try {
        Api $metodo $ruta $token $null | Out-Null
        return 200
    } catch {
        return [int]$_.Exception.Response.StatusCode.value__
    }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

Write-Host "`n== 1. Login y token util ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'login del agente devuelve token' ($agente.token.Length -gt 40) 'token'
$claves = $agente.token.Split('.')[1]
$claves = $claves.PadRight([int](([math]::Ceiling($claves.Length / 4.0)) * 4), '=').Replace('-', '+').Replace('_', '/')
$carga = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($claves)) | ConvertFrom-Json
# `iat` es la pieza sobre la que se apoya toda la invalidacion: si el token
# dejara de llevarlo, esto deja de funcionar en silencio.
Check 'el token lleva iat (base de D-S0-12)' ($null -ne $carga.iat) "iat=$($carga.iat)"
Check 'el token sigue SIN llevar la organizacion (contrato congelado)' ($null -eq $carga.idOrganizacion) 'gate'

$antes = CodigoDe GET '/clientes?pagina=1&tamano=1' $agente.token
Check 'con el token recien emitido, GET /clientes responde 200' ($antes -eq 200) "http=$antes"

Write-Host "`n== 2. Logout con efecto en servidor ==" -ForegroundColor Cyan
$respuesta = $null
try {
    $respuesta = Invoke-WebRequest -Method POST -Uri "$base/auth/logout" `
        -Headers @{ Authorization = "Bearer $($agente.token)" } -TimeoutSec 30 -UseBasicParsing
} catch {
    $respuesta = $_.Exception.Response
}
Check 'POST /auth/logout responde 204' ([int]$respuesta.StatusCode -eq 204) "http=$([int]$respuesta.StatusCode)"

$marca = Sql "SELECT sesiones_invalidas_desde IS NOT NULL FROM credencial_usuario WHERE nombre_usuario = 'vmora'"
Check 'la credencial quedo sellada en la BD' ($marca -eq 't') "valor=$marca"

Write-Host "`n== 3. El MISMO token ya no vale ==" -ForegroundColor Cyan
$despues = CodigoDe GET '/clientes?pagina=1&tamano=1' $agente.token
# Bien firmado y sin expirar, pero muerto: eso es exactamente lo que antes
# no ocurria — cerrar sesion era solo borrar localStorage.
Check 'el token anterior al logout responde 401' ($despues -eq 401) "http=$despues"

# Gotcha de PS 5.1: tras un error de Invoke-RestMethod el stream de la
# respuesta ya viene consumido, asi que GetResponseStream() devuelve vacio.
# El cuerpo esta en $_.ErrorDetails.Message.
$mensaje = ''
try { Api GET '/clientes?pagina=1&tamano=1' $agente.token $null | Out-Null } catch {
    $cuerpo = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($cuerpo)) {
        $flujo = $_.Exception.Response.GetResponseStream()
        $flujo.Position = 0
        $cuerpo = (New-Object IO.StreamReader($flujo)).ReadToEnd()
    }
    $mensaje = ($cuerpo | ConvertFrom-Json).error
}
# Mismo mensaje congelado que un token corrupto: no se le dice al cliente
# POR QUE dejo de valer.
Check 'el mensaje es el congelado y no revela el motivo' `
    ($mensaje -eq 'Token invalido o expirado.') "mensaje=$mensaje"

Write-Host "`n== 4. Volver a entrar funciona ==" -ForegroundColor Cyan
Start-Sleep -Seconds 1   # `iat` tiene precision de segundo (borde documentado).
$nuevo = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$conNuevo = CodigoDe GET '/clientes?pagina=1&tamano=1' $nuevo.token
Check 'el token nuevo (emitido despues) SI vale' ($conNuevo -eq 200) "http=$conNuevo"
$viejoSigueMuerto = CodigoDe GET '/clientes?pagina=1&tamano=1' $agente.token
Check 'y el viejo sigue muerto' ($viejoSigueMuerto -eq 401) "http=$viejoSigueMuerto"

Write-Host "`n== 5. El logout no toca a otras cuentas ==" -ForegroundColor Cyan
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
$brokerOk = CodigoDe GET '/clientes?pagina=1&tamano=1' $broker.token
Check 'la sesion del broker sigue intacta' ($brokerOk -eq 200) "http=$brokerOk"

Write-Host "`n---------------------------------------------" -ForegroundColor Cyan
Write-Host "  OK: $ok    FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
