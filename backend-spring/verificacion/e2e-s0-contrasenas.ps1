# =====================================================================
# E2E de CONTRASENAS Y RECUPERACION (Plan S0 §4.2-§4.5) — Bloque 4.
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
#   * el token sirve UNA vez y emitir otro mata el anterior;
#   * la contrasena temporal deja la sesion CAPADA de verdad.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite s0-contrasenas
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

# Codigo + cuerpo de una llamada que se espera que falle.
#
# Gotcha de PS 5.1, y no es teorico: segun por donde venga el error el cuerpo
# esta en ErrorDetails.Message o hay que leerlo del stream (con la posicion al
# inicio, porque puede venir ya consumido). Se prueban LOS DOS. Con uno solo,
# la mitad de las comprobaciones de mensaje comparan contra $null y fallan sin
# que el producto tenga nada mal — que es exactamente lo que paso en la primera
# corrida de esta suite.
function ApiError($metodo, $ruta, $token, $cuerpo) {
    try {
        $r = Api $metodo $ruta $token $cuerpo
        return [pscustomobject]@{ codigo = 200; error = $null; codigoError = $null; datos = $r }
    } catch {
        $respuesta = $_.Exception.Response
        $texto = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($texto)) {
            try {
                $flujo = $respuesta.GetResponseStream()
                $flujo.Position = 0
                $texto = (New-Object IO.StreamReader($flujo)).ReadToEnd()
            } catch { $texto = $null }
        }
        $json = $null
        if (-not [string]::IsNullOrWhiteSpace($texto)) { $json = $texto | ConvertFrom-Json }
        return [pscustomobject]@{
            codigo      = [int]$respuesta.StatusCode.value__
            error       = if ($json) { $json.error } else { $null }
            codigoError = if ($json) { $json.codigo } else { $null }
            datos       = $null
        }
    }
}

function CodigoDeGet($ruta, $token) {
    try { Api GET $ruta $token $null | Out-Null; return 200 }
    catch { return [int]$_.Exception.Response.StatusCode.value__ }
}

function Login($usuario, $contrasena) {
    Api POST '/auth/login' $null @{ usuario = $usuario; contrasena = $contrasena }
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
$agente = Login 'vmora' 'Agente2026'
Check 'login del agente con la clave del fixture' ($agente.token.Length -gt 40) 'token'

$malActual = ApiError POST '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = 'la-que-no-es'; contrasenaNueva = $CLAVE_BUENA }
# Sin esto, una sesion robada se quedaria con la cuenta para siempre.
Check 'con la contrasena actual incorrecta responde 400' ($malActual.codigo -eq 400) "http=$($malActual.codigo)"
Check 'y lo dice sin ambiguedad' ($malActual.error -eq 'La contrasena actual es incorrecta.') $malActual.error

Write-Host "`n== 2. La politica: longitud si, teatro de simbolos no ==" -ForegroundColor Cyan
$corta = ApiError POST '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = 'corta1' }
Check 'rechaza por debajo del minimo' ($corta.codigo -eq 400) "http=$($corta.codigo)"
Check 'y el mensaje dice cuantos caracteres hacen falta' ($corta.error -like '*12 caracteres*') $corta.error

$conUsuario = ApiError POST '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = 'xxxvmoraxxxyyy' }
Check 'rechaza la que contiene el nombre de usuario' ($conUsuario.codigo -eq 400) "http=$($conUsuario.codigo)"

$comun = ApiError POST '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = 'controllocal' }
Check 'rechaza las de la lista de claves comunes' ($comun.codigo -eq 400) "http=$($comun.codigo)"

# Una frase larga en minusculas SI vale: la longitud es lo que aporta
# entropia, y exigir simbolos fabrica justo el patron del seed.
$aceptada = PostSinCuerpo '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = 'Agente2026'; contrasenaNueva = $CLAVE_BUENA }
Check 'una frase larga en minusculas SI se acepta (204)' ($aceptada -eq 204) "http=$aceptada"

Write-Host "`n== 3. Cambiarla mata TODAS las sesiones, incluida la que llamo ==" -ForegroundColor Cyan
$viejo = CodigoDeGet '/clientes?pagina=1&tamano=1' $agente.token
# Si la sesion sobreviviera al cambio, el cambio no serviria de nada frente
# a una sesion robada: es justo el escenario que motiva la operacion.
Check 'el token anterior al cambio responde 401' ($viejo -eq 401) "http=$viejo"

$viejaClave = ApiError POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'la contrasena vieja ya no entra' ($viejaClave.codigo -eq 401) "http=$($viejaClave.codigo)"

Start-Sleep -Seconds 1   # `iat` tiene precision de segundo (borde documentado).
$agente = Login 'vmora' $CLAVE_BUENA
Check 'la contrasena nueva SI entra' ($agente.token.Length -gt 40) 'token'
Check 'y su token sirve' ((CodigoDeGet '/clientes?pagina=1&tamano=1' $agente.token) -eq 200) 'get'

Write-Host "`n== 4. No se puede volver a una contrasena reciente ==" -ForegroundColor Cyan
# Se cambia una segunda vez para que la ANTERIOR quede en el historial y se
# pueda intentar volver a ella. Ojo: no vale intentar volver a la del fixture
# ('Agente2026'), porque tiene 10 caracteres y la cortaria la regla de
# longitud ANTES de llegar a la de reutilizacion — la prueba pasaria en verde
# sin haber ejercitado el historial.
$segundoCambio = PostSinCuerpo '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = $CLAVE_BUENA; contrasenaNueva = $CLAVE_TERCERA }
Check 'un segundo cambio tambien entra (204)' ($segundoCambio -eq 204) "http=$segundoCambio"
Start-Sleep -Seconds 1
$agente = Login 'vmora' $CLAVE_TERCERA

$reutilizada = ApiError POST '/perfil/contrasena' $agente.token `
    @{ contrasenaActual = $CLAVE_TERCERA; contrasenaNueva = $CLAVE_BUENA }
# El patron real: "cambiala" -> se vuelve a poner la de siempre.
Check 'rechaza reutilizar una de las ultimas' ($reutilizada.codigo -eq 400) "http=$($reutilizada.codigo)"
Check 'y lo dice nombrando el historial' ($reutilizada.error -like '*ultimas*') $reutilizada.error

$historial = [int](Sql "SELECT count(*) FROM credencial_password")
Check 'el historial guarda el hash abandonado' ($historial -ge 1) "n=$historial"
$enClaro = [int](Sql "SELECT count(*) FROM credencial_password WHERE contrasena_hash NOT LIKE 'pbkdf2%'")
Check 'el historial guarda HASHES, no contrasenas' ($enClaro -eq 0) "n=$enClaro"

Write-Host "`n== 5. La recuperacion no revela el padron de cuentas ==" -ForegroundColor Cyan
$inexistente = PostSinCuerpo '/auth/recuperacion' $null @{ usuario = 'no-existe-jamas-7c1b' }
$existente = PostSinCuerpo '/auth/recuperacion' $null @{ usuario = 'vmora' }
Check 'un usuario inexistente responde 202' ($inexistente -eq 202) "http=$inexistente"
Check 'un usuario real responde 202 TAMBIEN' ($existente -eq 202) "http=$existente"
$vacio = PostSinCuerpo '/auth/recuperacion' $null @{ }
Check 'y un cuerpo vacio tambien: 202' ($vacio -eq 202) "http=$vacio"

$emitidos = [int](Sql "SELECT count(*) FROM token_acceso WHERE tipo='RECUPERACION'")
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

Write-Host "`n== 6. Invitar es GOBIERNO: ni el agente ni el broker pueden ==" -ForegroundColor Cyan
$broker = Login 'rsalas' 'Broker2026'
# V37: el TENANT_ADMIN entra con la sesion CAPADA hasta enrolar su segundo
# factor; el helper lo enrola y devuelve la sesion ya operativa.
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
$idPersonaAgente = [int](Sql "SELECT r.id_persona FROM credencial_usuario c JOIN persona_rol r ON r.id_persona_rol = c.id_persona_rol WHERE c.nombre_usuario = 'vmora'")
Check 'se resuelve la persona del agente' ($idPersonaAgente -gt 0) "id=$idPersonaAgente"

$porAgente = ApiError POST "/accesos/$idPersonaAgente/invitacion" $agente.token @{ motivo = 'prueba' }
Check 'un AGENTE no puede invitar (403)' ($porAgente.codigo -eq 403) "http=$($porAgente.codigo)"
# D-S0-18: un broker no invita ni a su propio equipo. Invitar, activar y
# suspender son gobierno del tenant, no supervision.
$porBroker = ApiError POST "/accesos/$idPersonaAgente/invitacion" $broker.token @{ motivo = 'prueba' }
Check 'un BROKER tampoco, ni siquiera a su equipo (403)' ($porBroker.codigo -eq 403) "http=$($porBroker.codigo)"

$invitacion = Api POST "/accesos/$idPersonaAgente/invitacion" $admin.token @{ motivo = 'alta de equipo' }
Check 'el ADMIN si puede y recibe el token' ($invitacion.token.Length -gt 20) "token=$($invitacion.token)"
Check 'con su caducidad' ($null -ne $invitacion.expiraEn) 'expiraEn'
# Sin transporte configurado (D-S0-11) el token lo entrega quien lo pidio.
Check 'y avisando de que NO se entrego al titular' ($invitacion.entregadoAlTitular -eq $false) "$($invitacion.entregadoAlTitular)"

Write-Host "`n== 7. En la base vive el HASH, nunca el token ==" -ForegroundColor Cyan
$tokenEnClaro = [int](Sql "SELECT count(*) FROM token_acceso WHERE hash_token = '$($invitacion.token)'")
Check 'el token NO esta guardado en claro' ($tokenEnClaro -eq 0) "n=$tokenEnClaro"
$largo = Sql "SELECT DISTINCT length(hash_token) FROM token_acceso"
Check 'se guarda su SHA-256 (64 hex)' ($largo -eq '64') "largo=$largo"

Write-Host "`n== 8. Emitir otro mata el anterior ==" -ForegroundColor Cyan
$segunda = Api POST "/accesos/$idPersonaAgente/invitacion" $admin.token @{ motivo = 'se perdio el primero' }
$primeraMuerta = ApiError POST '/auth/recuperacion/canje' $null `
    @{ token = $invitacion.token; contrasenaNueva = $CLAVE_OTRA }
# Un token viejo filtrado no puede seguir sirviendo despues de emitir el bueno.
Check 'la invitacion anterior deja de valer' ($primeraMuerta.codigo -eq 400) "http=$($primeraMuerta.codigo)"
$vivos = [int](Sql "SELECT count(*) FROM token_acceso WHERE usado_en IS NULL AND invalidado_en IS NULL")
Check 'nunca hay dos tokens vivos para la misma credencial' ($vivos -le 2) "n=$vivos"

Write-Host "`n== 9. El canje sirve UNA vez y lo define el titular ==" -ForegroundColor Cyan
$canje = PostSinCuerpo '/auth/recuperacion/canje' $null `
    @{ token = $segunda.token; contrasenaNueva = $CLAVE_OTRA }
Check 'el canje responde 204' ($canje -eq 204) "http=$canje"
Start-Sleep -Seconds 1
$conNueva = Login 'vmora' $CLAVE_OTRA
Check 'la contrasena que definio el titular entra' ($conNueva.token.Length -gt 40) 'token'

$repetido = ApiError POST '/auth/recuperacion/canje' $null `
    @{ token = $segunda.token; contrasenaNueva = 'otra frase larga distinta' }
Check 'el MISMO token ya no sirve una segunda vez' ($repetido.codigo -eq 400) "http=$($repetido.codigo)"
$usados = [int](Sql "SELECT count(*) FROM token_acceso WHERE usado_en IS NOT NULL")
Check 'y quedo sellado como usado' ($usados -ge 1) "n=$usados"

Write-Host "`n== 10. Alcance: otra organizacion es 404, no 403 ==" -ForegroundColor Cyan
$ajena = ApiError POST '/accesos/999999/invitacion' $admin.token @{ motivo = 'x' }
# Un 403 confirmaria que esa persona existe en algun sitio.
Check 'una persona que no es del tenant responde 404' ($ajena.codigo -eq 404) "http=$($ajena.codigo)"

$aSiMismo = ApiError POST "/accesos/$([int](Sql "SELECT r.id_persona FROM credencial_usuario c JOIN persona_rol r ON r.id_persona_rol = c.id_persona_rol WHERE c.nombre_usuario = 'admin@controllocal.test'"))/invitacion" $admin.token @{ motivo = 'x' }
# Emitirse un token a uno mismo saltaria la comprobacion de contrasena actual:
# seria una puerta trasera para una sesion robada de administrador.
Check 'el administrador no puede emitirse un token a si mismo' ($aSiMismo.codigo -eq 400) "http=$($aSiMismo.codigo)"

Write-Host "`n== 11. Contrasena temporal: la genera el SISTEMA y capa la sesion ==" -ForegroundColor Cyan
$idPersonaBroker = [int](Sql "SELECT r.id_persona FROM credencial_usuario c JOIN persona_rol r ON r.id_persona_rol = c.id_persona_rol WHERE c.nombre_usuario = 'sramirez'")
$temporal = Api POST "/accesos/$idPersonaBroker/contrasena-temporal" $admin.token @{ motivo = 'perdio el acceso' }
Check 'el ADMIN obtiene una temporal' ($temporal.contrasenaTemporal.Length -ge 12) "largo=$($temporal.contrasenaTemporal.Length)"
Check 'que trae el usuario al que pertenece' ($temporal.usuario -eq 'sramirez') $temporal.usuario
Check 'y avisa de que hay que cambiarla' ($temporal.debeCambiarla -eq $true) "$($temporal.debeCambiarla)"
# Sin I/l/1/O/0: la clave se dicta por telefono o se copia de una pantalla.
# `-cnotmatch` y no `-notmatch`: el operador por defecto de PowerShell IGNORA
# mayusculas, asi que '[Il1O0]' tambien casaba con la 'o' y la 'i' minusculas
# —que si estan en el alfabeto, porque solo son ambiguas frente a caracteres
# que ya se retiraron— y la comprobacion fallaba sin que hubiera nada mal.
Check 'sin caracteres que se confundan al dictarla' `
    ($temporal.contrasenaTemporal -cnotmatch '[Il1O0]') $temporal.contrasenaTemporal

$marcada = Sql "SELECT debe_cambiar_contrasena FROM credencial_usuario WHERE nombre_usuario='sramirez'"
Check 'la credencial queda marcada en la BD' ($marcada -eq 't') "valor=$marcada"

Start-Sleep -Seconds 1
$capado = Login 'sramirez' $temporal.contrasenaTemporal
Check 'la temporal permite entrar' ($capado.token.Length -gt 40) 'token'

$bloqueada = ApiError GET '/clientes?pagina=1&tamano=1' $capado.token $null
Check 'pero cualquier operacion responde 403' ($bloqueada.codigo -eq 403) "http=$($bloqueada.codigo)"
# El SPA distingue por el CODIGO, no por el texto: el texto es traducible.
Check 'con un codigo que el SPA puede distinguir' `
    ($bloqueada.codigoError -eq 'CAMBIO_CONTRASENA_REQUERIDO') "codigo=$($bloqueada.codigoError)"

# Sin estas dos, la pantalla de cambio obligatorio no podria ni pintarse.
Check 'GET /perfil SI pasa con la sesion capada' ((CodigoDeGet '/perfil' $capado.token) -eq 200) 'perfil'
$cambio = PostSinCuerpo '/perfil/contrasena' $capado.token `
    @{ contrasenaActual = $temporal.contrasenaTemporal; contrasenaNueva = 'la mia de verdad ya' }
Check 'y el cambio de contrasena tambien (204)' ($cambio -eq 204) "http=$cambio"

$descapada = Sql "SELECT debe_cambiar_contrasena FROM credencial_usuario WHERE nombre_usuario='sramirez'"
Check 'cambiarla descapa la cuenta' ($descapada -eq 'f') "valor=$descapada"
Start-Sleep -Seconds 1
$libre = Login 'sramirez' 'la mia de verdad ya'
Check 'y ya opera con normalidad' ((CodigoDeGet '/clientes?pagina=1&tamano=1' $libre.token) -eq 200) 'get'

Write-Host "`n== 12. Auditoria: queda todo, y sin un solo secreto ==" -ForegroundColor Cyan
foreach ($tipo in @('PASSWORD_CAMBIADA', 'RECUPERACION_EMITIDA', 'INVITACION_EMITIDA',
                    'INVITACION_CANJEADA', 'PASSWORD_RESTABLECIDA', 'SESIONES_INVALIDADAS')) {
    $n = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='$tipo'")
    Check "queda evento $tipo" ($n -ge 1) "n=$n"
}

# La regla de higiene de §6.3, aplicada a lo que este bloque introduce: ni
# contrasenas, ni tokens, ni hashes pueden aparecer en la auditoria.
$secretos = [int](Sql @"
SELECT count(*) FROM evento_seguridad
WHERE coalesce(detalle_json,'') LIKE '%$($segunda.token)%'
   OR coalesce(detalle_json,'') LIKE '%$($temporal.contrasenaTemporal)%'
   OR coalesce(detalle_json,'') ILIKE '%pbkdf2%'
   OR coalesce(motivo,'')       LIKE '%$($temporal.contrasenaTemporal)%'
"@)
Check 'la auditoria NO filtra el token ni la temporal' ($secretos -eq 0) "coincidencias=$secretos"

# El evento de invitacion tiene que decir SOBRE QUIEN se actuo: sin eso, una
# auditoria de accesos no responde la unica pregunta que se hace despues.
$conObjetivo = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE tipo='INVITACION_EMITIDA' AND id_objetivo IS NOT NULL")
Check 'la invitacion registra a quien se le dio acceso' ($conObjetivo -ge 1) "n=$conObjetivo"

Write-Host "`n---------------------------------------------" -ForegroundColor Cyan
Write-Host "  OK: $ok    FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
