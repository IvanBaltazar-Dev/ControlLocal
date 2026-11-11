# =====================================================================
# E2E de CONTRASENAS Y RECUPERACION (Plan S0 seccion 4.2-seccion 4.5) - Bloque 4.
#
# Cierra los dos huecos mas basicos del diagnostico:
#   H-02  no existia NINGUNA forma de cambiar una contrasena;
#   H-08  no existia NINGUNA forma de recuperar el acceso salvo SQL.
#
# Lo que demuestra, y que es lo que hay que poder defender:
#   * el cambio exige la contrasena ACTUAL y mata todas las sesiones;
#   * la politica no exige mayuscula+digito+simbolo (fabricaria Clave2026!)
#     pero si longitud, y rechaza reutilizar;
#   * la recuperacion NO revela si la cuenta existe;
#   * el administrador NUNCA ve ni fija la contrasena de otra persona;
#   * otra recuperacion invalida el enlace anterior; canje/entrega positivos
#     conservan evidencia App separada, sin token administrativo ni temporal.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite s0-contrasenas
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
}

# Codigo + cuerpo de una llamada que se espera que falle.
#
# Gotcha de PS 5.1, y no es teorico: segun por donde venga el error el cuerpo
# esta en ErrorDetails.Message o hay que leerlo del stream (con la posicion al
# inicio, porque puede venir ya consumido). Se prueban LOS DOS. Con uno solo,
# la mitad de las comprobaciones de mensaje comparan contra $null y fallan sin
# que el producto tenga nada mal - que es exactamente lo que paso en la primera
# corrida de esta suite.
function ApiError($metodo, $ruta, $token, $cuerpo) {
    try {
        $r = Api $metodo $ruta $token $cuerpo
        return [pscustomobject]@{ codigo = 200; error = $null; codigoError = $null; datos = $r }
    } catch {
        $respuesta = $_.Exception.Response
        if($null -eq $respuesta){throw}
        $texto = LeerErrorHttpDeFixture $_
        $json = $null
        if (-not [string]::IsNullOrWhiteSpace($texto)) { try{$json = $texto | ConvertFrom-Json}catch{$json=$null} }
        return [pscustomobject]@{
            codigo      = [int]$respuesta.StatusCode.value__
            error       = if ($json.error -is [string]) { $json.error } else { 'Respuesta HTTP sin mensaje JSON legible.' }
            codigoError = if ($json) { $json.codigo } else { $null }
            datos       = $null
        }
    }
}

function CodigoDeGet($ruta, $token) {
    try { Api GET $ruta $token $null | Out-Null; return 200 }
    catch { return [int]$_.Exception.Response.StatusCode.value__ }
}

function Login($correo, $contrasena, $rol) {
    Connect-ControlLocalE2E -BaseUrl $base -Correo $correo -Contrasena $contrasena -Organizacion $fixtureE2E.organizacion -Rol $rol
}
# Devuelve 204 sin cuerpo: Invoke-RestMethod no sirve para leer el codigo.
function PostSinCuerpo($ruta, $token, $cuerpo) {
    $parametros = @{
        Method = 'POST'; Uri = "$base$ruta"; TimeoutSec = 30; UseBasicParsing = $true
        Headers = @{}
    }
    if ($token) { $parametros.Headers['Authorization'] = "Bearer $token" }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 6)
        $parametros['ContentType'] = 'application/json'
    }
    try { return [int](Invoke-WebRequest @parametros).StatusCode }
    catch { return [int]$_.Exception.Response.StatusCode.value__ }
}

$CLAVE_BUENA   = 'palanca verde de julio'
$CLAVE_TERCERA = 'escalera de piedra gris'
$CLAVE_OTRA    = 'ventana azul con lluvia'

Write-Host "`n== 1. El cambio exige la contrasena ACTUAL ==" -ForegroundColor Cyan
$agente = Login $fixtureE2E.actores.vmora.correo 'Agente2026' 'AGENTE'
Check 'login del agente con la clave del fixture' ($agente.token.Length -gt 40) 'token'

$cuentaPropia = Api GET '/cuenta' $agente.token $null
$sesionParalela = Login $fixtureE2E.actores.vmora.correo 'Agente2026' 'AGENTE'
$malActual = ApiError POST '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = 'la-que-no-es'; contrasenaNueva = $CLAVE_BUENA }
# Sin esto, una sesion robada se quedaria con la cuenta para siempre.
Check 'con la contrasena actual incorrecta responde 400' ($malActual.codigo -eq 400) "http=$($malActual.codigo)"
Check 'y lo dice sin ambiguedad' ($malActual.error -eq 'La contrasena actual es incorrecta.') $malActual.error

Write-Host "`n== 2. La politica: longitud si, teatro de simbolos no ==" -ForegroundColor Cyan
$corta = ApiError POST '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = 'corta1' }
Check 'rechaza por debajo del minimo' ($corta.codigo -eq 400) "http=$($corta.codigo)"
Check 'y el mensaje dice cuantos caracteres hacen falta' ($corta.error -like '*12 caracteres*') $corta.error

$conUsuario = ApiError POST '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = ('xx'+$cuentaPropia.nombreUsuario+'yy') }
Check 'rechaza la que contiene el nombre de usuario' ($conUsuario.codigo -eq 400) "http=$($conUsuario.codigo)"

$comun = ApiError POST '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = 'administrador' }
Check 'rechaza las de la lista de claves comunes' ($comun.codigo -eq 400) "http=$($comun.codigo)"

# Una frase larga en minusculas SI vale: la longitud es lo que aporta
# entropia, y exigir simbolos fabrica justo el patron del seed.
$aceptada = PostSinCuerpo '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = $CLAVE_BUENA }
Check 'una frase larga en minusculas SI se acepta (204)' ($aceptada -eq 204) "http=$aceptada"

Write-Host "`n== 3. Cambiarla mata TODAS las sesiones, incluida la que llamo ==" -ForegroundColor Cyan
$viejo = CodigoDeGet '/cuenta' $agente.token
# Si la sesion sobreviviera al cambio, el cambio no serviria de nada frente
# a una sesion robada: es justo el escenario que motiva la operacion.
Check 'el token anterior al cambio responde 401' ($viejo -eq 401) "http=$viejo"

Check 'el cambio revoca tambien el otro dispositivo sin esperar al segundo siguiente' `
    ((CodigoDeGet '/cuenta' $sesionParalela.token) -eq 401) 'segunda sesion'
$viejaClave = ApiError POST '/auth/login' $null @{ correo = $fixtureE2E.actores.vmora.correo; contrasena = 'Agente2026' }
Check 'la contrasena vieja ya no entra' ($viejaClave.codigo -eq 401) "http=$($viejaClave.codigo)"

$agente = Login $fixtureE2E.actores.vmora.correo $CLAVE_BUENA 'AGENTE'
Check 'la contrasena nueva SI entra' ($agente.token.Length -gt 40) 'token'
Check 'y su token sirve' ((CodigoDeGet '/cuenta' $agente.token) -eq 200) 'get'

Write-Host "`n== 4. No se puede volver a una contrasena reciente ==" -ForegroundColor Cyan
# Se cambia una segunda vez para que la ANTERIOR quede en el historial y se
# pueda intentar volver a ella. Ojo: no vale intentar volver a la del fixture
# ('Agente2026'), porque tiene 10 caracteres y la cortaria la regla de
# longitud ANTES de llegar a la de reutilizacion - la prueba pasaria en verde
# sin haber ejercitado el historial.
$segundoCambio = PostSinCuerpo '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = $CLAVE_BUENA; contrasenaNueva = $CLAVE_TERCERA }
Check 'un segundo cambio tambien entra (204)' ($segundoCambio -eq 204) "http=$segundoCambio"
$agente = Login $fixtureE2E.actores.vmora.correo $CLAVE_TERCERA 'AGENTE'

$reutilizada = ApiError POST '/cuenta/contrasena' $agente.token `
    @{ contrasenaActual = $CLAVE_TERCERA; contrasenaNueva = $CLAVE_BUENA }
# El patron real: "cambiala" -> se vuelve a poner la de siempre.
Check 'rechaza reutilizar una de las ultimas' ($reutilizada.codigo -eq 400) "http=$($reutilizada.codigo)"
Check 'y lo dice nombrando el historial' ($reutilizada.error -like '*ultimas*') $reutilizada.error

$historial = [int](Invoke-ControlLocalE2ESql "SELECT count(*) FROM credencial_password WHERE id_cuenta_acceso=$($fixtureE2E.actores.vmora.cuenta)")
Check 'el historial guarda el hash abandonado' ($historial -ge 1) "n=$historial"
$enClaro = [int](Invoke-ControlLocalE2ESql "SELECT count(*) FROM credencial_password WHERE id_cuenta_acceso=$($fixtureE2E.actores.vmora.cuenta) AND contrasena_hash NOT LIKE 'pbkdf2%'")
Check 'el historial guarda HASHES, no contrasenas' ($enClaro -eq 0) "n=$enClaro"

Write-Host "`n== 5. La recuperacion no revela el padron de cuentas ==" -ForegroundColor Cyan
$inexistente = PostSinCuerpo '/auth/recuperacion' $null @{ correo = 'no-existe-jamas-7c1b@example.invalid' }
$existente = PostSinCuerpo '/auth/recuperacion' $null @{ correo = $fixtureE2E.actores.vmora.correo }
Check 'un usuario inexistente responde 202' ($inexistente -eq 202) "http=$inexistente"
Check 'un usuario real responde 202 TAMBIEN' ($existente -eq 202) "http=$existente"
$vacio = PostSinCuerpo '/auth/recuperacion' $null @{ }
Check 'y un cuerpo vacio tambien: 202' ($vacio -eq 202) "http=$vacio"

$emitidos = [int](Invoke-ControlLocalE2ESql "SELECT count(*) FROM token_acceso WHERE tipo='RECUPERACION'")
# Solo la del usuario real emite token; las otras dos solo dejan evento. Lo
# que NO puede pasar es que el llamador note la diferencia.
Check 'solo la cuenta real genero token' ($emitidos -eq 1) "n=$emitidos"

$canjeMalo = ApiError POST '/auth/recuperacion/canje' $null `
    @{ token = 'token-inventado-que-no-existe'; contrasenaNueva = $CLAVE_OTRA }
Check 'un token inventado responde 400' ($canjeMalo.codigo -eq 400) "http=$($canjeMalo.codigo)"
# Caducado, usado, reemplazado o inventado dan EL MISMO error: distinguirlos
# diria al atacante si acerto el token pero llego tarde.
Check 'con un mensaje que no dice cual de los cuatro motivos fue' `
    ($canjeMalo.error -eq 'El enlace no es valido o ya fue utilizado.') $canjeMalo.error

Write-Host "`n== 6. Cuenta propia: no reinvitacion ni contrasena temporal del gobierno ==" -ForegroundColor Cyan
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
foreach($actor in @($agente,$broker,$admin)) {
    $retirada = ApiError POST "/accesos/$($fixtureE2E.actores.vmora.persona)/invitacion" $actor.token @{ motivo='VIA_RETIRADA_TEST' }
    Check ('la via retirada no da credenciales ajenas a '+$actor.rol) ($retirada.codigo -in @(404,405)) 'retiro'
}
$cuentaDespues = Api GET '/cuenta' $agente.token $null
Check 'la cuenta propia conserva correo e identidad tras los intentos ajenos' `
    ($cuentaDespues.idCuenta -eq $cuentaPropia.idCuenta -and $cuentaDespues.correoPropio -eq $cuentaPropia.correoPropio) 'cuenta propia'

Write-Host "`n== 7. Recuperacion privada: hash, proposito y una emision vigente ==" -ForegroundColor Cyan
$otraEmision = PostSinCuerpo '/auth/recuperacion' $null @{ correo=$fixtureE2E.actores.vmora.correo }
Check 'otra solicitud conserva el acuse uniforme202' ($otraEmision -eq 202) 'acuse'
$tokens = Invoke-ControlLocalE2ESql @"
select count(*)||'|'||count(*) filter(where usado_en is null and invalidado_en is null)||'|'||
 count(*) filter(where hash_token !~ '^[0-9a-f]{64}$')||'|'||
 count(*) filter(where correo_objetivo <> '$($fixtureE2E.actores.vmora.correo)')
from token_acceso where id_cuenta_acceso=$($fixtureE2E.actores.vmora.cuenta) and tipo='RECUPERACION';
"@
$partes = $tokens.Trim().Split('|')
Check 'la nueva emision invalida la anterior para esta cuenta/proposito' `
    ([int]$partes[0] -ge 2 -and [int]$partes[1] -eq 1) 'tokens vigentes'
Check 'se conserva hash y destinatario propio sin exponerlos' `
    ([int]$partes[2] -eq 0 -and [int]$partes[3] -eq 0) 'hash/destino'
$sinEnlace = ApiError POST '/auth/recuperacion/canje' $null @{ token='NO_ES_UN_ENLACE_VALIDO';contrasenaNueva=$CLAVE_OTRA }
Check 'un token no emitido no permite restaurar la cuenta' ($sinEnlace.codigo -eq 400) 'canje negativo'
Check 'el canje rechazado no revoca una sesion correcta' ((CodigoDeGet '/cuenta' $agente.token) -eq 200) 'sin efecto'

Write-Host "`n== 8. Auditoria de cuenta, sin secretos ==" -ForegroundColor Cyan
foreach($tipo in @('PASSWORD_CAMBIADA','RECUPERACION_EMITIDA','SESIONES_INVALIDADAS')) {
    $n = [int](Invoke-ControlLocalE2ESql "select count(*) from evento_seguridad where id_cuenta_acceso=$($fixtureE2E.actores.vmora.cuenta) and tipo='$tipo'")
    Check ('queda evento propio '+$tipo) ($n -ge 1) 'auditoria'
}
$secretos = [int](Invoke-ControlLocalE2ESql @"
select count(*) from evento_seguridad where id_cuenta_acceso=$($fixtureE2E.actores.vmora.cuenta) and
 (coalesce(detalle_json,'') like '%$CLAVE_BUENA%' or coalesce(detalle_json,'') like '%$CLAVE_TERCERA%'
  or coalesce(detalle_json,'') ilike '%pbkdf2%' or coalesce(motivo,'') like '%$CLAVE_TERCERA%');
"@)
Check 'la auditoria no publica contrasenas ni hashes' ($secretos -eq 0) 'higiene'

# Limite EXPLICITO, no un Check verde: NotificadorFueraDeBanda no entrega
# enlaces. Canje positivo/30min/CAS: CuentaPersonalIntegrationTest y pruebas
# de EnlacesDeCuenta. Antes del piloto sigue pendiente transporte privado.
# No se inyecta token, no se habilita endpoint ni se simula correo externo.
Write-Host 'E2E_LIMITE recuperacion positiva y entrega privada: evidencia App separada; transporte E2E pendiente.'
Write-Host "`n---------------------------------------------" -ForegroundColor Cyan
Write-Host "  OK: $ok    FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
