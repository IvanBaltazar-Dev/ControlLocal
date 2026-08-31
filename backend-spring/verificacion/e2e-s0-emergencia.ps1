# =====================================================================
# E2E del SIMULACRO de recuperacion de emergencia (V38, seccion 18.16 del diseno).
#
# SOLO ASCII, como el resto de las suites: PowerShell 5.1 lee un .ps1 sin BOM
# como ANSI, y un guion largo o una enne en un comentario bastan para romper el
# analisis del script entero con un error que senala una linea que esta bien.
#
# Recorre el escenario entero POR EL CABLE: el ultimo TENANT_ADMIN pierde su
# telefono y no tiene codigos de respaldo -> dos custodios aprueban por
# separado -> una sola aprobacion NO habilita -> se aplica la revocacion -> el
# administrador VUELVE A ENROLAR EL MISMO -> la concesion se cierra sola -> y se
# comprueba que nunca leyo un dato comercial, no creo ninguna persona, no fijo
# ninguna contrasena y no dejo cuenta ni rol nuevos.
#
# DOS COSAS QUE HACEN DISTINTA A ESTA SUITE
#
# 1. El conector de gestion escucha en 127.0.0.1 DENTRO del contenedor y el
#    puerto NO se publica. No es una limitacion de la prueba: es el control que
#    se esta probando. Por eso se llama con `docker exec`, que es la unica via
#    que existe, y por eso hay una comprobacion de que la misma ruta responde
#    404 por el puerto publico.
#
# 2. Los secretos de custodio los genera `Invoke-E2E.ps1` en esta corrida y
#    mueren con ella (D-S0-53). No hay ninguno versionado: un par (secreto,
#    hash) en el repositorio seria un custodio real con el secreto publicado.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite s0-emergencia
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

function CodigoDe($metodo, $ruta, $token, $cuerpo) {
    try { Api $metodo $ruta $token $cuerpo | Out-Null; return 200 }
    catch { return [int]$_.Exception.Response.StatusCode.value__ }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

# --- El conector local, desde DENTRO del contenedor ---------------------
# `wget` de BusyBox: es lo que hay en la imagen, y llega al loopback interno.
$gestion = 'http://127.0.0.1:8091/controllocal/Api/gestion/recuperacion'

function Gestion($ruta, $cuerpoJson, $cabecera) {
    # EL CUERPO VIAJA EN BASE64, y no es por gusto: PowerShell 5.1 destroza las
    # comillas dobles al pasar argumentos a un ejecutable nativo, asi que un
    # JSON entregado con --post-data llega al contenedor sin comillas y el API
    # responde 400 por cuerpo ilegible. Costo dos corridas confundir eso con un
    # fallo del servicio. En base64 no hay ninguna comilla que cruzar.
    $orden = 'wget -q -O -'
    if ($cabecera) { $orden += " --header '$cabecera'" }
    if ($null -ne $cuerpoJson) {
        $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($cuerpoJson))
        $orden = "echo $b64 | base64 -d > /tmp/cuerpo.json && $orden" +
                 " --header 'Content-Type: application/json' --post-file /tmp/cuerpo.json"
    }
    $orden += " '$gestion$ruta'"
    $salida = & docker exec $e2e.ApiContainer sh -c $orden
    if ($LASTEXITCODE -ne 0) { throw "La llamada de gestion fallo ($ruta)." }
    return ($salida -join '') | ConvertFrom-Json
}

function GestionFalla($ruta, $cuerpoJson, $cabecera) {
    try { Gestion $ruta $cuerpoJson $cabecera | Out-Null; return $false } catch { return $true }
}

# Cualquier ruta del API del producto, pedida POR EL CONECTOR DE GESTION. La
# redireccion va DENTRO del contenedor: en PowerShell 5.1, redirigir la salida
# de un ejecutable nativo convierte su stderr en un error terminante.
function ProductoPorGestionFalla($ruta) {
    & docker exec $e2e.ApiContainer sh -c `
        "wget -q -O - 'http://127.0.0.1:8091/controllocal/Api$ruta' >/dev/null 2>&1"
    return $LASTEXITCODE -ne 0
}

# --- TOTP, para que el administrador vuelva a enrolar --------------------
function Base32Decode($texto) {
    $alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $bits = 0; $valor = 0
    $salida = New-Object System.Collections.Generic.List[byte]
    foreach ($c in $texto.ToUpper().ToCharArray()) {
        $indice = $alfabeto.IndexOf($c)
        if ($indice -lt 0) { continue }
        $valor = ($valor -shl 5) -bor $indice
        $bits += 5
        if ($bits -ge 8) { $salida.Add([byte](($valor -shr ($bits - 8)) -band 0xFF)); $bits -= 8 }
    }
    return $salida.ToArray()
}

function PasoActual { [long][math]::Floor(([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) / 30) }

function TotpDe($secretoBase32, $paso) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA1
    $hmac.Key = Base32Decode $secretoBase32
    $contador = [BitConverter]::GetBytes([int64]$paso)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($contador) }
    $hash = $hmac.ComputeHash($contador)
    $d = $hash[$hash.Length - 1] -band 0x0F
    $binario = ((($hash[$d] -band 0x7F) -shl 24) -bor (($hash[$d + 1] -band 0xFF) -shl 16) -bor
                (($hash[$d + 2] -band 0xFF) -shl 8) -bor ($hash[$d + 3] -band 0xFF))
    return ($binario % 1000000).ToString('000000')
}

function EsperarPasoNuevo {
    $desde = PasoActual
    while ((PasoActual) -eq $desde) { Start-Sleep -Milliseconds 500 }
}

$custodioA = $env:CONTROLLOCAL_E2E_CUSTODIO_A_ID
$custodioB = $env:CONTROLLOCAL_E2E_CUSTODIO_B_ID
$secretoA = $env:CONTROLLOCAL_E2E_CUSTODIO_A_SECRETO
$secretoB = $env:CONTROLLOCAL_E2E_CUSTODIO_B_SECRETO
if (-not $secretoA -or -not $secretoB) {
    throw 'Faltan los secretos de custodio de la corrida. Se generan en Invoke-E2E.ps1.'
}

# =====================================================================
Write-Host "`n== 0. La superficie NO existe por el puerto publico ==" -ForegroundColor Cyan

# Lo primero, porque es la propiedad que sostiene todo lo demas: si esta ruta
# fuera alcanzable desde fuera, "hace falta acceso al servidor" se convertiria
# en "hace falta acertar dos secretos".
$httpPublico = CodigoDe POST '/gestion/recuperacion' $null @{ operador = 'x' }
Check 'la ruta de gestion responde 404 por el puerto publico' ($httpPublico -eq 404) "http=$httpPublico"

# Y por el conector de gestion no se atiende el API del producto. Se prueba con
# `/salud`, que es publico y siempre responde por el puerto normal: si por aqui
# tambien respondiera, el puerto de gestion seria una copia del producto SIN
# autenticacion, que es lo contrario de lo que se busca.
Check 'el conector de gestion NO sirve el API del producto' `
    (ProductoPorGestionFalla '/salud') 'el filtro corta en los dos sentidos'

Write-Host "`n== 0b. La instalacion esta preparada ==" -ForegroundColor Cyan
# Se comprueba ANTES de emitir: si los custodios no llegaron a la configuracion,
# todo lo de abajo fallaria con un 400 opaco y habria que adivinar la causa.
$preparacion = Gestion '/estado' $null $null
Check 'la recuperacion esta habilitada' ($preparacion.habilitada -eq $true) 'bandera'
Check 'y hay DOS custodios configurados' ($preparacion.custodiosConfigurados -eq $true) `
    "custodios=$($preparacion.custodiosConfigurados) - revisa RECUPERACION_CUSTODIO_*_ID/HASH en el contenedor"
if (-not $preparacion.custodiosConfigurados) {
    docker exec $e2e.ApiContainer sh -c 'env | grep -c RECUPERACION_CUSTODIO'
    throw 'Sin custodios configurados no tiene sentido seguir: el simulacro probaria otra cosa.'
}

Write-Host "`n== 1. Punto de partida: la organizacion SIN gobierno operativo ==" -ForegroundColor Cyan
# V37 marca a todo TENANT_ADMIN como "debe enrolar", asi que una base recien
# migrada no tiene ningun administrador operativo. Es exactamente el callejon
# sin salida que la concesion viene a resolver: nadie puede revocarle el factor
# a nadie porque no hay quien lo haga.
$operativos = [int](Sql @"
SELECT count(*) FROM usuario_organizacion uo
  JOIN credencial_usuario cu ON cu.id_persona_rol = uo.id_usuario
 WHERE uo.organizacion_id = 1 AND uo.estado = 'A' AND uo.rol = 'TENANT_ADMIN'
   AND cu.estado_administrativo = 'A'
   AND NOT cu.debe_cambiar_contrasena AND NOT cu.debe_enrolar_mfa
"@)
Check 'no hay ningun administrador operativo' ($operativos -eq 0) "operativos=$operativos"

$idPersonaAdmin = [int](Sql @"
SELECT pr.id_persona FROM persona_rol pr
  JOIN credencial_usuario cu ON cu.id_persona_rol = pr.id_persona_rol
 WHERE cu.nombre_usuario = 'admin@controllocal.test'
"@)

$personasAntes = [int](Sql "SELECT count(*) FROM persona")
$cuentasAntes = [int](Sql "SELECT count(*) FROM credencial_usuario")
$rolesAntes = [int](Sql "SELECT count(*) FROM persona_rol")
$hashAntes = Sql "SELECT contrasena_hash FROM credencial_usuario WHERE nombre_usuario='admin@controllocal.test'"

Write-Host "`n== 2. Emision y doble aprobacion ==" -ForegroundColor Cyan
$emision = Gestion '' (@{
    idOrganizacion = 1; idPersonaObjetivo = $idPersonaAdmin
    operador = 'operador-e2e'; motivo = 'simulacro: telefono perdido y sin codigos'
} | ConvertTo-Json -Compress)
$idConcesion = $emision.idConcesion
Check 'la concesion nace PENDIENTE' ($emision.estado -eq 'PENDIENTE') "estado=$($emision.estado)"

# EL PUNTO: con UNA aprobacion no autoriza absolutamente nada.
$primera = Gestion "/$idConcesion/aprobaciones" (@{ custodio = $custodioA; secreto = $secretoA } | ConvertTo-Json -Compress)
Check 'con una sola aprobacion sigue PENDIENTE' ($primera.estado -eq 'PENDIENTE') "estado=$($primera.estado)"
Check 'y la primera aprobacion NO entrega ninguna concesion' ($null -eq $primera.concesion) 'sin secreto'

# El mismo custodio no puede cubrir las dos partes: lo impide el UNIQUE.
Check 'el mismo custodio no aprueba dos veces' `
    (GestionFalla "/$idConcesion/aprobaciones" (@{ custodio = $custodioA; secreto = $secretoA } | ConvertTo-Json -Compress) $null) `
    'una sola mano no basta'

# Un secreto equivocado no aprueba, y deja evento.
Check 'un secreto equivocado no aprueba' `
    (GestionFalla "/$idConcesion/aprobaciones" (@{ custodio = $custodioB; secreto = 'no-es' } | ConvertTo-Json -Compress) $null) `
    'aprobacion invalida'

$segunda = Gestion "/$idConcesion/aprobaciones" (@{ custodio = $custodioB; secreto = $secretoB } | ConvertTo-Json -Compress)
Check 'la segunda aprobacion la deja VIGENTE' ($segunda.estado -eq 'VIGENTE') "estado=$($segunda.estado)"
Check 'y entrega el secreto de la concesion' ($segunda.concesion.Length -gt 30) 'secreto'
$concesion = $segunda.concesion

$identidades = Sql "SELECT operador || '|' || custodio_a || '|' || custodio_b FROM concesion_recuperacion WHERE id_concesion=$idConcesion"
Check 'la fila conserva las TRES identidades' `
    ($identidades -eq "operador-e2e|$custodioA|$custodioB") "identidades=$identidades"

Write-Host "`n== 3. Las acciones: una vez cada una ==" -ForegroundColor Cyan
$revocacion = Gestion '/acciones/REVOCAR_MFA' '{}' "X-Concesion: $concesion"
Check 'REVOCAR_MFA se aplica' ($revocacion.tipo -eq 'REVOCAR_MFA') "tipo=$($revocacion.tipo)"
Check 'y descuenta capacidad' ($revocacion.accionesRestantes -eq 2) "restantes=$($revocacion.accionesRestantes)"

Check 'la MISMA accion no se repite' `
    (GestionFalla '/acciones/REVOCAR_MFA' '{}' "X-Concesion: $concesion") `
    'sin el UNIQUE por tipo, max_acciones=3 dejaria repetirla'

Check 'una accion inventada no existe' `
    (GestionFalla '/acciones/LEER_CLIENTES' '{}' "X-Concesion: $concesion") `
    'solo hay tres, y ninguna lee datos del negocio'

Check 'una concesion inventada no vale' `
    (GestionFalla '/acciones/REACTIVAR_CUENTA' '{}' 'X-Concesion: inventada') 'secreto falso'

Write-Host "`n== 4. El titular vuelve a enrolar EL MISMO ==" -ForegroundColor Cyan
# La concesion revoca; nunca configura el factor de nadie.
$admin = Api POST '/auth/login' $null @{ usuario = 'admin@controllocal.test'; contrasena = 'Admin2026' }
Check 'el administrador entra con la sesion capada' ($admin.token.Length -gt 40) 'token'
$enrolamiento = Api POST '/perfil/mfa' $admin.token @{}
EsperarPasoNuevo
$paso = PasoActual
Api POST '/perfil/mfa/confirmar' $admin.token @{ codigo = (TotpDe $enrolamiento.secreto $paso) } | Out-Null

$operativosDespues = [int](Sql @"
SELECT count(*) FROM usuario_organizacion uo
  JOIN credencial_usuario cu ON cu.id_persona_rol = uo.id_usuario
  JOIN factor_autenticacion f ON f.id_credencial = cu.id_persona_rol AND f.estado = 'A'
 WHERE uo.organizacion_id = 1 AND uo.estado = 'A' AND uo.rol = 'TENANT_ADMIN'
   AND cu.estado_administrativo = 'A'
   AND NOT cu.debe_cambiar_contrasena AND NOT cu.debe_enrolar_mfa
"@)
Check 'la organizacion vuelve a tener gobierno operativo' ($operativosDespues -ge 1) "operativos=$operativosDespues"

Write-Host "`n== 5. La concesion se cierra sola ==" -ForegroundColor Cyan
# Con gobierno de vuelta, la concesion ya no puede obrar: el intento la cierra.
Check 'con el gobierno restablecido, la concesion ya no aplica nada' `
    (GestionFalla '/acciones/REACTIVAR_CUENTA' '{}' "X-Concesion: $concesion") 'cerrada'
$estadoFinal = Sql "SELECT estado || '|' || coalesce(cierre_motivo,'-') FROM concesion_recuperacion WHERE id_concesion=$idConcesion"
Check 'y queda CERRADA por gobierno restablecido' `
    ($estadoFinal -eq 'C|GOBIERNO_RESTABLECIDO') "estado=$estadoFinal"

Write-Host "`n== 6. Lo que la concesion NO hizo ==" -ForegroundColor Cyan
Check 'no creo ninguna persona' ([int](Sql "SELECT count(*) FROM persona") -eq $personasAntes) 'personas'
Check 'no creo ninguna cuenta' ([int](Sql "SELECT count(*) FROM credencial_usuario") -eq $cuentasAntes) 'cuentas'
Check 'no dejo ningun rol nuevo' ([int](Sql "SELECT count(*) FROM persona_rol") -eq $rolesAntes) 'roles'
$hashDespues = Sql "SELECT contrasena_hash FROM credencial_usuario WHERE nombre_usuario='admin@controllocal.test'"
Check 'NUNCA fijo la contrasena de nadie' ($hashDespues -eq $hashAntes) 'hash intacto'

$acciones = Sql "SELECT count(*) FROM accion_recuperacion WHERE id_concesion=$idConcesion"
Check 'solo consta la accion que se aplico' ($acciones -eq '1') "acciones=$acciones"

Write-Host "`n== 7. Higiene de la auditoria ==" -ForegroundColor Cyan
$eventos = Sql "SELECT string_agg(DISTINCT tipo, ',' ORDER BY tipo) FROM evento_seguridad WHERE tipo LIKE 'RECUPERACION%' OR tipo LIKE 'CUSTODIO%'"
Check 'se auditan emision, aplicacion y aprobacion fallida' `
    ($eventos -like '*RECUPERACION_EMERGENCIA_EMITIDA*' -and $eventos -like '*RECUPERACION_EMERGENCIA_APLICADA*' `
        -and $eventos -like '*CUSTODIO_APROBACION_FALLIDA*') "tipos=$eventos"

$sucios = [int](Sql @"
SELECT count(*) FROM evento_seguridad
 WHERE coalesce(detalle_json,'') || coalesce(motivo,'') ~* '(pbkdf2|secreto|hash|contrasena)'
"@)
Check 'ningun evento lleva secretos ni hashes' ($sucios -eq 0) "sucios=$sucios"

$secretoEnBase = [int](Sql "SELECT count(*) FROM concesion_recuperacion WHERE hash_secreto = '$concesion'")
Check 'la base guarda el HASH del secreto, no el secreto' ($secretoEnBase -eq 0) 'hash, no secreto'

# ---------------------------------------------------------------------
Write-Host "`n== Resumen ==" -ForegroundColor Cyan
Write-Host "  OK: $ok   FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
