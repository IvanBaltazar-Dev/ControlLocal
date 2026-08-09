# =====================================================================
# E2E de V37 — segundo factor TOTP, elevación y el invariante de
# administrador OPERATIVO (Bloque 6, D-S0-22…D-S0-37).
#
# Diseño: docs/ai/plan-s0-6-mfa-y-break-glass.md §18.
#
# El escenario 16 (simulacro de recuperación de emergencia) NO está aquí:
# pertenece a V38, que no se implementa.
#
# La suite tarda MINUTOS y casi todo es espera de reloj: un TOTP se acepta una
# sola vez, así que cada prueba encadenada tiene que dejar pasar su paso de 30 s.
# Es el precio del anti-replay y no se recorta.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite s0-mfa
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

function Api($metodo, $ruta, $token, $cuerpo, $cabeceras) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    if ($cabeceras) { $cabeceras.GetEnumerator() | ForEach-Object { $headers[$_.Key] = $_.Value } }
    $parametros = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 6)
        $parametros['ContentType'] = 'application/json'
    }
    Invoke-RestMethod @parametros
}

function CodigoDe($metodo, $ruta, $token, $cuerpo, $cabeceras) {
    try { Api $metodo $ruta $token $cuerpo $cabeceras | Out-Null; return 200 }
    catch { return [int]$_.Exception.Response.StatusCode.value__ }
}

function ErrorDe($metodo, $ruta, $token, $cuerpo, $cabeceras) {
    try { Api $metodo $ruta $token $cuerpo $cabeceras | Out-Null; return '(no fallo)' }
    catch {
        try {
            $lector = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            return ($lector.ReadToEnd() | ConvertFrom-Json).error
        } catch { return '(sin cuerpo)' }
    }
}

# Estado Y cuerpo de UNA sola peticion. Hace falta porque preguntar por el
# `codigo` con una llamada aparte gastaria un desafio y una unidad del limite
# por cada comprobacion, y acabaria disparando el bloqueo que otra seccion mide.
function RespuestaDe($metodo, $ruta, $token, $cuerpo, $cabeceras) {
    try {
        Api $metodo $ruta $token $cuerpo $cabeceras | Out-Null
        return @{ http = 200; codigo = $null; error = $null }
    } catch {
        $http = [int]$_.Exception.Response.StatusCode.value__
        $leido = $null
        try {
            $lector = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            $leido = ($lector.ReadToEnd() | ConvertFrom-Json)
        } catch { }
        return @{ http = $http; codigo = $leido.codigo; error = $leido.error }
    }
}

# Cuerpo TAL CUAL viaja, sin pasar por la deserializacion de PowerShell. Hace
# falta para contar elementos de un array: `Invoke-RestMethod` no siempre los
# entrega enumerados y el recuento sale 1.
function TextoDe($metodo, $ruta, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    (Invoke-WebRequest -Method $metodo -Uri "$base$ruta" -Headers $headers `
        -TimeoutSec 30 -UseBasicParsing).Content
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

# --- TOTP en PowerShell -----------------------------------------------
# Hace falta generar codigos de verdad: probar el segundo factor con un
# codigo inventado solo probaria que rechaza basura.

function Base32Decode($texto) {
    $alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $bits = 0; $valor = 0
    $salida = New-Object System.Collections.Generic.List[byte]
    foreach ($c in $texto.ToUpper().ToCharArray()) {
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

function PasoActual { [long][math]::Floor(([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) / 30) }

function TotpDe($secretoBase32, $paso) {
    $clave = Base32Decode $secretoBase32
    $contador = [BitConverter]::GetBytes([int64]$paso)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($contador) }
    $hmac = New-Object System.Security.Cryptography.HMACSHA1
    $hmac.Key = $clave
    $hash = $hmac.ComputeHash($contador)
    $desplazamiento = $hash[$hash.Length - 1] -band 0x0F
    $binario = ((($hash[$desplazamiento] -band 0x7F) -shl 24) -bor
                (($hash[$desplazamiento + 1] -band 0xFF) -shl 16) -bor
                (($hash[$desplazamiento + 2] -band 0xFF) -shl 8) -bor
                 ($hash[$desplazamiento + 3] -band 0xFF))
    return ($binario % 1000000).ToString('000000')
}

function CodigoAhora($secreto) { TotpDe $secreto (PasoActual) }

# --- Higiene de respuestas ---------------------------------------------
# Se buscan secretos por su FORMA, no por el nombre del campo. Buscar la
# palabra "contrasena" marcaba como fuga el campo `debeCambiarContrasena`, y
# "elevacion" marcaba el tipo de evento ELEVACION_EMITIDA: dos falsos positivos
# que solo enseñan que la prueba estaba mal escrita. Lo que no puede aparecer
# es el ASPECTO de un secreto.
function SinSecretos($json) {
    if ([string]::IsNullOrEmpty($json)) { return $true }
    # Secreto TOTP en Base32 (>=32 caracteres del alfabeto A-Z2-7).
    if ($json -cmatch '[A-Z2-7]{32,}') { return $false }
    # Codigo de respaldo: cinco grupos de cuatro separados por guion.
    if ($json -cmatch '[0-9A-Z]{4}(-[0-9A-Z]{4}){4}') { return $false }
    # Hash de contrasena o de codigo (formato de PasswordHasher).
    if ($json -match '(?i)pbkdf2') { return $false }
    return $true
}

# Esperar al paso siguiente es INEVITABLE, y por diseño: un codigo vale una sola
# vez y el proximo no existe hasta que el reloj avanza. La alternativa —admitir
# dos veces el mismo paso— es justo el defecto que estas pruebas cierran, asi
# que aqui se espera en vez de rebajar la proteccion.
function EsperarPasoNuevo {
    $desde = PasoActual
    while ((PasoActual) -eq $desde) { Start-Sleep -Milliseconds 500 }
}

function EsperarHastaPaso($objetivo) {
    while ((PasoActual) -lt $objetivo) { Start-Sleep -Milliseconds 500 }
}

function DesafioDe($usuario, $clave) {
    (Invoke-WebRequest -Method POST -Uri "$base/auth/mfa/desafio" -TimeoutSec 30 -UseBasicParsing `
        -ContentType 'application/json' `
        -Body (@{ usuario = $usuario; contrasena = $clave } | ConvertTo-Json)).Content | ConvertFrom-Json
}

# =====================================================================
Write-Host "`n== 0. Punto de partida: el administrador debe enrolar ==" -ForegroundColor Cyan

# V37 marca a todo TENANT_ADMIN vigente. No hay ventana en la que este sin
# MFA y sin obligacion de ponerselo.
$marcados = [int](Sql "SELECT count(*) FROM credencial_usuario WHERE debe_enrolar_mfa")
Check 'V37 obliga a enrolar a los administradores' ($marcados -eq 1) "marcados=$marcados"
$exigido = Sql "SELECT mfa_gobierno_exigido FROM organizacion WHERE id_organizacion=1"
Check 'la organizacion AUN no exige MFA de gobierno (arranque seguro)' ($exigido -eq 'f') "exigido=$exigido"

$admin = Api POST '/auth/login' $null @{ usuario = 'admin@controllocal.test'; contrasena = 'Admin2026' }
Check 'el administrador SI puede entrar con la sesion capada' ($admin.token.Length -gt 40) 'token'

Write-Host "`n== 1. La sesion capada solo alcanza lo imprescindible ==" -ForegroundColor Cyan
Check 'GET /perfil pasa'      ((CodigoDe GET '/perfil' $admin.token $null $null) -eq 200) 'perfil'
Check 'GET /perfil/mfa pasa'  ((CodigoDe GET '/perfil/mfa' $admin.token $null $null) -eq 200) 'estado'
$codigo403 = CodigoDe GET '/agentes?pagina=1&tamano=1' $admin.token $null $null
Check 'administrar agentes NO pasa' ($codigo403 -eq 403) "http=$codigo403"
Check 'administrar miembros NO pasa' `
    ((CodigoDe GET '/asignaciones/agentes' $admin.token $null $null) -eq 403) 'asignaciones'
Check 'clientes NO pasa' ((CodigoDe GET '/clientes?pagina=1&tamano=1' $admin.token $null $null) -eq 403) 'clientes'
Check 'el logout SI pasa (no se encierra a nadie)' `
    ((CodigoDe POST '/auth/logout' $admin.token @{} $null) -eq 200) 'logout'

Write-Host "`n== 2. Enrolamiento ==" -ForegroundColor Cyan
# El logout de arriba sello `sesiones_invalidas_desde`, y el `iat` del token
# tiene precision de SEGUNDO: reentrar dentro del mismo segundo daria un token
# que nace invalidado. Es el borde conocido y aceptado de D-S0-12 — falla del
# lado seguro—, y aqui hay que esperarlo en vez de tropezar con el.
Start-Sleep -Milliseconds 1500
$admin = Api POST '/auth/login' $null @{ usuario = 'admin@controllocal.test'; contrasena = 'Admin2026' }
$enrolamiento = Api POST '/perfil/mfa' $admin.token @{} $null
Check 'el enrolamiento devuelve secreto y otpauth' `
    ($enrolamiento.secreto.Length -ge 32 -and $enrolamiento.uri -like 'otpauth://totp/*') "uri=$($enrolamiento.uri)"
Check 'la URI declara SHA1/6/30 (interoperable)' `
    ($enrolamiento.uri -like '*algorithm=SHA1*' -and $enrolamiento.uri -like '*digits=6*' `
        -and $enrolamiento.uri -like '*period=30*') "uri=$($enrolamiento.uri)"

$secreto = $enrolamiento.secreto
$estado = Api GET '/perfil/mfa' $admin.token $null $null
Check 'sin confirmar, el factor NO esta activo' (-not $estado.activo) "activo=$($estado.activo)"

$malCodigo = RespuestaDe POST '/perfil/mfa/confirmar' $admin.token @{ codigo = '000000' } $null
Check 'confirmar con codigo invalido no activa' `
    ($malCodigo.error -eq 'El codigo no es valido.') "error=$($malCodigo.error)"
Check 'y el rechazo viaja con codigo estable, no solo con texto' `
    ($malCodigo.codigo -eq 'MFA_CODIGO_INVALIDO') "codigo=$($malCodigo.codigo)"

# Se confirma al PRINCIPIO de un paso para que el replay de la seccion 3 caiga
# dentro de la misma ventana: si no, podria pasar por el cambio de paso y no
# por el sellado, que es lo que se quiere probar.
EsperarPasoNuevo
$pasoConfirmacion = PasoActual
$codigoConfirmacion = TotpDe $secreto $pasoConfirmacion
$codigos = Api POST '/perfil/mfa/confirmar' $admin.token @{ codigo = $codigoConfirmacion } $null
Check 'confirmar devuelve 8 codigos de respaldo' ($codigos.codigos.Count -eq 8) "n=$($codigos.codigos.Count)"
Check 'los codigos llevan identificador + secreto' `
    ($codigos.codigos[0] -match '^[0-9A-Z]{4}(-[0-9A-Z]{4}){4}$') "codigo=$($codigos.codigos[0])"

Check 'confirmar INVALIDA las sesiones vivas' `
    ((CodigoDe GET '/perfil' $admin.token $null $null) -eq 401) 'la sesion que enrolo tambien cae'
$exigido = Sql "SELECT mfa_gobierno_exigido FROM organizacion WHERE id_organizacion=1"
Check 'la organizacion cruza a MFA de gobierno' ($exigido -eq 't') "exigido=$exigido"

# Validar y consumir son el MISMO acto tambien al activar el factor. Antes no
# lo eran: confirmar comprobaba el codigo y no lo sellaba —`consumirPaso` solo
# mira factores ACTIVO y aqui todavia era PENDIENTE—, asi que el primer codigo
# seguia sirviendo para entrar durante el resto de su ventana.
$sellado = [long](Sql @"
SELECT coalesce(f.ultimo_paso,0) FROM factor_autenticacion f
  JOIN credencial_usuario cu ON cu.id_persona_rol = f.id_credencial
 WHERE cu.nombre_usuario = 'admin@controllocal.test' AND f.estado = 'A'
"@)
Check 'confirmar SELLA el paso que uso' ($sellado -eq $pasoConfirmacion) `
    "ultimo_paso=$sellado paso=$pasoConfirmacion"

Write-Host "`n== 3. El login pasa a dos pasos ==" -ForegroundColor Cyan
$http = CodigoDe POST '/auth/login' $null @{ usuario = 'admin@controllocal.test'; contrasena = 'Admin2026' } $null
Check 'con MFA activo, /auth/login responde 401' ($http -eq 401) "http=$http"

$respuesta = Invoke-WebRequest -Method POST -Uri "$base/auth/mfa/desafio" -TimeoutSec 30 -UseBasicParsing `
    -ContentType 'application/json' `
    -Body (@{ usuario = 'admin@controllocal.test'; contrasena = 'Admin2026' } | ConvertTo-Json)
$desafio = ($respuesta.Content | ConvertFrom-Json)
Check '/auth/mfa/desafio responde 202 con desafio' `
    ($respuesta.StatusCode -eq 202 -and $desafio.desafio) "http=$($respuesta.StatusCode)"
Check 'el 202 NO trae token de sesion' ($null -eq $desafio.token) 'un desafio no autoriza nada'

# EL ESCENARIO DEL DEFECTO. El codigo con el que se acaba de confirmar el
# enrolamiento NO puede volver a usarse para entrar, aunque su ventana siga
# abierta. Es ademas el codigo mas expuesto de todos: acaba de estar en
# pantalla, junto al QR y a los codigos de respaldo.
$replay = RespuestaDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $desafio.desafio; codigo = $codigoConfirmacion } $null
Check 'el codigo del enrolamiento NO sirve luego para autenticar' `
    ($replay.http -ne 200) "http=$($replay.http)"
Check 'y se rechaza como REUTILIZADO, no como invalido' `
    ($replay.codigo -eq 'MFA_CODIGO_REUTILIZADO') "codigo=$($replay.codigo)"

# Y con el paso siguiente SI entra: lo que cuesta la proteccion es esperar un
# paso, no quedarse fuera.
EsperarHastaPaso ($pasoConfirmacion + 1)
$desafio = DesafioDe 'admin@controllocal.test' 'Admin2026'
$sesion = Api POST '/auth/mfa/verificar' $null @{ desafio = $desafio.desafio; codigo = (CodigoAhora $secreto) } $null
Check 'con el paso temporal siguiente la autenticacion SI pasa' `
    ($sesion.token.Length -gt 40) 'sesion'
Check 'verificar devuelve el LoginResponse congelado' `
    ($sesion.token.Length -gt 40 -and $sesion.rol -eq 'ADMIN') "rol=$($sesion.rol)"
$adminToken = $sesion.token
Check 'la sesion ya NO esta capada' ((CodigoDe GET '/agentes?pagina=1&tamano=1' $adminToken $null $null) -eq 200) 'agentes'

# Los otros dos codigos del contrato, sobre el mismo camino.
$yaCanjeado = RespuestaDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $desafio.desafio; codigo = (CodigoAhora $secreto) } $null
Check 'un desafio ya canjeado se distingue por codigo' `
    ($yaCanjeado.codigo -eq 'MFA_DESAFIO_CONSUMIDO') "codigo=$($yaCanjeado.codigo)"
$inexistente = RespuestaDe POST '/auth/mfa/verificar' $null `
    @{ desafio = 'no-existe'; codigo = '000000' } $null
Check 'un desafio inexistente se distingue por codigo' `
    ($inexistente.codigo -eq 'MFA_DESAFIO_INVALIDO') "codigo=$($inexistente.codigo)"

# Un usuario SIN MFA usa el MISMO camino y recibe 200 + sesion.
$sinMfa = Invoke-WebRequest -Method POST -Uri "$base/auth/mfa/desafio" -TimeoutSec 30 -UseBasicParsing `
    -ContentType 'application/json' -Body (@{ usuario = 'rsalas'; contrasena = 'Broker2026' } | ConvertTo-Json)
Check 'sin MFA el mismo endpoint responde 200 + sesion' `
    ($sinMfa.StatusCode -eq 200 -and ($sinMfa.Content | ConvertFrom-Json).token) "http=$($sinMfa.StatusCode)"
$brokerToken = ($sinMfa.Content | ConvertFrom-Json).token

Write-Host "`n== 4. Anti-replay ATOMICO (D-S0-31) ==" -ForegroundColor Cyan
# Hay que empezar en un paso LIMPIO: el login de arriba ya consumio el paso
# actual, y reutilizarlo probaria el replay contra el codigo equivocado.
EsperarPasoNuevo
$paso = PasoActual
$codigoFijo = TotpDe $secreto $paso

$d1 = (Invoke-WebRequest -Method POST -Uri "$base/auth/mfa/desafio" -TimeoutSec 30 -UseBasicParsing `
    -ContentType 'application/json' -Body (@{ usuario='admin@controllocal.test'; contrasena='Admin2026' } | ConvertTo-Json)).Content | ConvertFrom-Json
$primera = CodigoDe POST '/auth/mfa/verificar' $null @{ desafio = $d1.desafio; codigo = $codigoFijo } $null
$d2 = (Invoke-WebRequest -Method POST -Uri "$base/auth/mfa/desafio" -TimeoutSec 30 -UseBasicParsing `
    -ContentType 'application/json' -Body (@{ usuario='admin@controllocal.test'; contrasena='Admin2026' } | ConvertTo-Json)).Content | ConvertFrom-Json
$segunda = CodigoDe POST '/auth/mfa/verificar' $null @{ desafio = $d2.desafio; codigo = $codigoFijo } $null

Check 'el mismo codigo entra UNA vez' ($primera -eq 200) "http=$primera"
Check 'y el REPLAY del mismo codigo falla' ($segunda -ne 200) `
    "http=$segunda — sin UPDATE condicional, el codigo valdria toda su ventana"

$ultimoPaso = [long](Sql "SELECT coalesce(ultimo_paso,0) FROM factor_autenticacion WHERE estado='A'")
Check 'el paso consumido queda sellado' ($ultimoPaso -ge $paso) "ultimo_paso=$ultimoPaso paso=$paso"

Write-Host "`n== 5. Solo paso actual y anterior; el futuro NO (D-S0-36) ==" -ForegroundColor Cyan
# Se prueban contra el factor de un BROKER recien enrolado, para no gastar
# los pasos del administrador ni chocar con su ultimo_paso.
$brokerSesion = Api POST '/auth/mfa/desafio' $null @{ usuario='rsalas'; contrasena='Broker2026' } $null
$brokerToken = $brokerSesion.token
$enrolBroker = Api POST '/perfil/mfa' $brokerToken @{} $null
$secretoBroker = $enrolBroker.secreto
EsperarPasoNuevo
$pasoBroker = PasoActual
Api POST '/perfil/mfa/confirmar' $brokerToken @{ codigo = (TotpDe $secretoBroker $pasoBroker) } $null | Out-Null

$dFuturo = DesafioDe 'rsalas' 'Broker2026'
$httpFuturo = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dFuturo.desafio; codigo = (TotpDe $secretoBroker ((PasoActual) + 1)) } $null
Check 'el paso FUTURO (t+1) se rechaza' ($httpFuturo -ne 200) "http=$httpFuturo"

$dViejo = DesafioDe 'rsalas' 'Broker2026'
$httpViejo = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dViejo.desafio; codigo = (TotpDe $secretoBroker ((PasoActual) - 2)) } $null
Check 'dos pasos atras (t-2) se rechaza' ($httpViejo -ne 200) "http=$httpViejo"

# El sellado del enrolamiento alcanza tambien a esta prueba: con `ultimo_paso`
# en el paso de la confirmacion, "el anterior" solo vuelve a ser aceptable
# cuando han pasado DOS. Es la consecuencia buscada, no un estorbo — el
# anti-replay no distingue de donde venia el sello.
EsperarHastaPaso ($pasoBroker + 2)
$dAnterior = DesafioDe 'rsalas' 'Broker2026'
$httpAnterior = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dAnterior.desafio; codigo = (TotpDe $secretoBroker ((PasoActual) - 1)) } $null
Check 'el paso anterior (t-1) SI vale: el cliente atrasado es el caso real' `
    ($httpAnterior -eq 200) "http=$httpAnterior"

Write-Host "`n== 6. Limite por desafio y acumulado por cuenta (D-S0-32) ==" -ForegroundColor Cyan
$dLimite = DesafioDe 'rsalas' 'Broker2026'
for ($i = 1; $i -le 5; $i++) {
    CodigoDe POST '/auth/mfa/verificar' $null @{ desafio = $dLimite.desafio; codigo = '000000' } $null | Out-Null
}
$traLimite = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dLimite.desafio; codigo = (CodigoAhora $secretoBroker) } $null
Check 'al sexto intento el desafio muere, aunque el codigo sea bueno' ($traLimite -ne 200) "http=$traLimite"

# EL PUNTO CLAVE: pedir desafios nuevos NO reinicia el contador de la cuenta.
for ($ronda = 1; $ronda -le 2; $ronda++) {
    $dRonda = DesafioDe 'rsalas' 'Broker2026'
    for ($i = 1; $i -le 5; $i++) {
        CodigoDe POST '/auth/mfa/verificar' $null @{ desafio = $dRonda.desafio; codigo = '000000' } $null | Out-Null
    }
}
$fallosCuenta = [int](Sql "SELECT count(*) FROM intento_acceso WHERE clave_tipo='MFA_CUENTA' AND exito=false")
Check 'los fallos se acumulan POR CUENTA entre desafios distintos' ($fallosCuenta -ge 10) "fallos=$fallosCuenta"

$dTrasEspera = DesafioDe 'rsalas' 'Broker2026'
$espera = RespuestaDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dTrasEspera.desafio; codigo = (CodigoAhora $secretoBroker) } $null
Check 'con >=10 fallos acumulados se exige espera, aun con codigo bueno' `
    ($espera.error -like '*Demasiados intentos*') "error=$($espera.error)"
Check 'y la espera NO es un bloqueo indefinido' ($espera.error -like '*minutos*') "error=$($espera.error)"
Check 'el limite tambien se distingue por codigo' `
    ($espera.codigo -eq 'MFA_LIMITE_INTENTOS') "codigo=$($espera.codigo)"

Write-Host "`n== 7. Codigos de respaldo ==" -ForegroundColor Cyan
$dRespaldo = DesafioDe 'admin@controllocal.test' 'Admin2026'
$conRespaldo = Api POST '/auth/mfa/verificar' $null `
    @{ desafio = $dRespaldo.desafio; codigo = $codigos.codigos[0] } $null
Check 'un codigo de respaldo deja entrar' ($conRespaldo.token.Length -gt 40) 'sesion'
$adminToken = $conRespaldo.token

$estado = Api GET '/perfil/mfa' $adminToken $null $null
Check 'usar un codigo NO desactiva el MFA' ($estado.activo) "activo=$($estado.activo)"
Check 'y queda uno menos' ($estado.codigosDisponibles -eq 7) "quedan=$($estado.codigosDisponibles)"

$dRepetido = DesafioDe 'admin@controllocal.test' 'Admin2026'
$httpRepetido = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dRepetido.desafio; codigo = $codigos.codigos[0] } $null
Check 'el mismo codigo de respaldo NO vale dos veces' ($httpRepetido -ne 200) "http=$httpRepetido"

$dInventado = DesafioDe 'admin@controllocal.test' 'Admin2026'
$httpInventado = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dInventado.desafio; codigo = 'ZZZZ-1111-2222-3333-4444' } $null
Check 'un identificador inventado falla (localiza CERO filas)' ($httpInventado -ne 200) "http=$httpInventado"

Write-Host "`n== 8. Regenerar y revocar exigen reautenticacion (D-S0-34) ==" -ForegroundColor Cyan
$errSinCodigo = ErrorDe POST '/perfil/mfa/codigos' $adminToken @{ contrasena = 'Admin2026' } $null
Check 'regenerar SIN codigo se rechaza: la sesion abierta no basta' `
    ($errSinCodigo -eq 'El codigo no es valido.') "error=$errSinCodigo"
$errSinClave = ErrorDe POST '/perfil/mfa/codigos' $adminToken @{ codigo = (CodigoAhora $secreto) } $null
Check 'regenerar SIN contrasena se rechaza' ($errSinClave -like '*contrasena*') "error=$errSinClave"

EsperarPasoNuevo   # el anterior quedo consumido por el anti-replay
$nuevos = Api POST '/perfil/mfa/codigos' $adminToken @{ contrasena = 'Admin2026'; codigo = (CodigoAhora $secreto) } $null
Check 'regenerar devuelve 8 codigos nuevos' ($nuevos.codigos.Count -eq 8) "n=$($nuevos.codigos.Count)"
$dViejoCodigo = DesafioDe 'admin@controllocal.test' 'Admin2026'
$httpViejoCodigo = CodigoDe POST '/auth/mfa/verificar' $null `
    @{ desafio = $dViejoCodigo.desafio; codigo = $codigos.codigos[1] } $null
Check 'regenerar invalida TODOS los anteriores' ($httpViejoCodigo -ne 200) "http=$httpViejoCodigo"
Check 'regenerar NO invalida sesiones' ((CodigoDe GET '/perfil' $adminToken $null $null) -eq 200) 'sigue dentro'

Write-Host "`n== 9. Reglas de revocacion por rol ==" -ForegroundColor Cyan
$idPersonaBroker = [int](Sql "SELECT pr.id_persona FROM persona_rol pr JOIN credencial_usuario cu ON cu.id_persona_rol=pr.id_persona_rol WHERE cu.nombre_usuario='rsalas'")
$brokerSesion = Api POST '/auth/mfa/desafio' $null @{ usuario='vmora'; contrasena='Agente2026' } $null
$agenteToken = $brokerSesion.token

Check 'un AGENTE no revoca el factor de nadie' `
    ((CodigoDe DELETE "/accesos/$idPersonaBroker/mfa" $agenteToken @{ motivo='x' } $null) -eq 403) 'agente'

EsperarPasoNuevo
$elevacion = Api POST '/perfil/elevacion' $adminToken @{ contrasena = 'Admin2026'; codigo = (CodigoAhora $secreto) } $null
Check 'la elevacion se emite con contrasena + codigo' ($elevacion.token.Length -gt 20) 'token'

$errSinElevacion = ErrorDe DELETE "/accesos/$idPersonaBroker/mfa" $adminToken @{ motivo='prueba' } $null
Check 'revocar SIN elevacion se rechaza' ($errSinElevacion -like '*reautenticarse*') "error=$errSinElevacion"

$idPersonaAdmin = 1
$errPropio = ErrorDe DELETE "/accesos/$idPersonaAdmin/mfa" $adminToken @{ motivo='prueba' } `
    @{ 'X-Elevacion' = $elevacion.token }
Check 'nadie se revoca a si mismo por esta via' ($errPropio -like '*tu perfil*') "error=$errPropio"

$errSinMotivo = ErrorDe DELETE "/accesos/$idPersonaBroker/mfa" $adminToken @{} `
    @{ 'X-Elevacion' = $elevacion.token }
Check 'el motivo es obligatorio' ($errSinMotivo -like '*motivo*') "error=$errSinMotivo"

$httpRevoca = CodigoDe DELETE "/accesos/$idPersonaBroker/mfa" $adminToken @{ motivo='telefono perdido' } `
    @{ 'X-Elevacion' = $elevacion.token }
Check 'el TENANT_ADMIN SI revoca el factor de un companero' ($httpRevoca -eq 200) "http=$httpRevoca"

$obligado = Sql "SELECT debe_enrolar_mfa FROM credencial_usuario WHERE nombre_usuario='rsalas'"
Check 'el afectado queda obligado a enrolar' ($obligado -eq 't') "obligado=$obligado"
$revocados = [int](Sql "SELECT count(*) FROM factor_autenticacion f JOIN credencial_usuario cu ON cu.id_persona_rol=f.id_credencial WHERE cu.nombre_usuario='rsalas' AND f.estado='R'")
Check 'el factor queda REVOCADO, no borrado' ($revocados -eq 1) "revocados=$revocados"

$httpElevacionUsada = CodigoDe DELETE "/accesos/$idPersonaBroker/mfa" $adminToken @{ motivo='otra vez' } `
    @{ 'X-Elevacion' = $elevacion.token }
Check 'la elevacion NO vale dos veces' ($httpElevacionUsada -ne 200) "http=$httpElevacionUsada"

Write-Host "`n== 10. Invariante de administrador OPERATIVO (D-S0-37) ==" -ForegroundColor Cyan
EsperarPasoNuevo
$elevacion2 = Api POST '/perfil/elevacion' $adminToken @{ contrasena = 'Admin2026'; codigo = (CodigoAhora $secreto) } $null
$errUltimo = ErrorDe DELETE "/accesos/$idPersonaAdmin/mfa" $adminToken @{ motivo='x' } `
    @{ 'X-Elevacion' = $elevacion2.token }
Check 'no se puede revocar el factor del UNICO administrador operativo' `
    ($errUltimo -like '*tu perfil*' -or $errUltimo -like '*operativo*') "error=$errUltimo"

EsperarPasoNuevo
$errPropioUltimo = ErrorDe DELETE '/perfil/mfa' $adminToken `
    @{ contrasena = 'Admin2026'; codigo = (CodigoAhora $secreto) } $null
Check 'ni el propio titular puede, si es el ultimo operativo' `
    ($errPropioUltimo -like '*administrador operativo*') "error=$errPropioUltimo"

$sigue = Sql "SELECT estado FROM factor_autenticacion f JOIN credencial_usuario cu ON cu.id_persona_rol=f.id_credencial WHERE cu.nombre_usuario='admin@controllocal.test' AND f.estado='A'"
Check 'y el factor del administrador sigue ACTIVO' ($sigue -eq 'A') "estado=$sigue"

Write-Host "`n== 11. token_acceso: el tipo es obligatorio (D-S0-23) ==" -ForegroundColor Cyan
$tipos = Sql "SELECT string_agg(DISTINCT tipo, ',' ORDER BY tipo) FROM token_acceso"
Check 'conviven desafios y elevaciones en la misma tabla' `
    ($tipos -like '*DESAFIO_MFA*' -and $tipos -like '*ELEVACION*') "tipos=$tipos"

# Un desafio NO puede canjearse como recuperacion de contrasena.
$dCruzado = DesafioDe 'admin@controllocal.test' 'Admin2026'
$errCruce = ErrorDe POST '/auth/recuperacion/canje' $null `
    @{ token = $dCruzado.desafio; contrasena = 'OtraClave2026' } $null
Check 'un DESAFIO_MFA no se canjea como recuperacion de contrasena' `
    ($errCruce -like '*no es valido*') "error=$errCruce"

$vivosPorTipo = Sql "SELECT max(c) FROM (SELECT count(*) c FROM token_acceso WHERE usado_en IS NULL AND invalidado_en IS NULL GROUP BY id_credencial, tipo) x"
Check 'el unico parcial es por (credencial, tipo)' ($vivosPorTipo -eq '1') "max=$vivosPorTipo"

Write-Host "`n== 11b. Reautenticacion reforzada: lo que NO cambia al fallar ==" -ForegroundColor Cyan
# El punto no es que rechace —eso ya se comprueba en la seccion 8— sino que un
# intento fallido no deje nada a medias: ni regenera codigos, ni toca el factor.
$codigosAntes = [int](Sql @"
SELECT count(*) FROM codigo_respaldo_mfa k
  JOIN factor_autenticacion f ON f.id_factor = k.id_factor
  JOIN credencial_usuario cu ON cu.id_persona_rol = f.id_credencial
 WHERE cu.nombre_usuario = 'admin@controllocal.test' AND k.usado_en IS NULL
"@)
EsperarPasoNuevo
CodigoDe POST '/perfil/mfa/codigos' $adminToken `
    @{ contrasena = 'ClaveEquivocada1'; codigo = (CodigoAhora $secreto) } $null | Out-Null
$codigosTrasClaveMala = [int](Sql @"
SELECT count(*) FROM codigo_respaldo_mfa k
  JOIN factor_autenticacion f ON f.id_factor = k.id_factor
  JOIN credencial_usuario cu ON cu.id_persona_rol = f.id_credencial
 WHERE cu.nombre_usuario = 'admin@controllocal.test' AND k.usado_en IS NULL
"@)
Check 'con la contrasena mal, regenerar NO toca los codigos' `
    ($codigosTrasClaveMala -eq $codigosAntes) "antes=$codigosAntes ahora=$codigosTrasClaveMala"

CodigoDe POST '/perfil/mfa/codigos' $adminToken @{ contrasena = 'Admin2026'; codigo = '000000' } $null | Out-Null
$estadoTrasCodigoMalo = Sql @"
SELECT f.estado FROM factor_autenticacion f
  JOIN credencial_usuario cu ON cu.id_persona_rol = f.id_credencial
 WHERE cu.nombre_usuario = 'admin@controllocal.test' AND f.estado = 'A'
"@
# El codigo unitario, no la palabra: V40 estrecho `factor_autenticacion.estado`
# a un caracter y la consulta de arriba ya filtra por 'A', pero la comparacion
# se quedo en 'ACTIVO'. Quedo comparando 'A' contra 'ACTIVO' —siempre falso— y
# nadie lo vio porque esta suite no se volvio a correr entera desde V40.
Check 'con el codigo mal, el factor sigue ACTIVO e intacto' `
    ($estadoTrasCodigoMalo -eq 'A') "estado=$estadoTrasCodigoMalo"

Write-Host "`n== 11c. Reemplazar el factor propio (D-S0-34) ==" -ForegroundColor Cyan
# Se prueba con un AGENTE: el invariante de administrador operativo impide que
# el unico TENANT_ADMIN se revoque el suyo, y eso ya se comprueba en la 10.
$agente = Api POST '/auth/mfa/desafio' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' } $null
$agenteToken = $agente.token
$enrolAgente = Api POST '/perfil/mfa' $agenteToken @{} $null
$secretoAgente = $enrolAgente.secreto
EsperarPasoNuevo
$pasoAgente = PasoActual
Api POST '/perfil/mfa/confirmar' $agenteToken @{ codigo = (TotpDe $secretoAgente $pasoAgente) } $null | Out-Null

Start-Sleep -Milliseconds 1500   # `iat` tiene precision de segundo (D-S0-12)
$agente = Api POST '/auth/mfa/desafio' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' } $null
EsperarHastaPaso ($pasoAgente + 1)
$sesionAgente = Api POST '/auth/mfa/verificar' $null `
    @{ desafio = $agente.desafio; codigo = (CodigoAhora $secretoAgente) } $null
$agenteToken = $sesionAgente.token

EsperarPasoNuevo
$httpReemplazo = CodigoDe DELETE '/perfil/mfa' $agenteToken `
    @{ contrasena = 'Agente2026'; codigo = (CodigoAhora $secretoAgente) } $null
Check 'reemplazar el factor propio se acepta' ($httpReemplazo -eq 200) "http=$httpReemplazo"
Check 'y la sesion que lo pidio muere en el acto' `
    ((CodigoDe GET '/perfil' $agenteToken $null $null) -eq 401) 'sesiones invalidadas'
$obligadoAgente = Sql "SELECT debe_enrolar_mfa FROM credencial_usuario WHERE nombre_usuario='vmora'"
Check 'la cuenta queda obligada a enrolar de nuevo' ($obligadoAgente -eq 't') "obligado=$obligadoAgente"
$factorAgente = Sql @"
SELECT f.estado FROM factor_autenticacion f
  JOIN credencial_usuario cu ON cu.id_persona_rol = f.id_credencial
 WHERE cu.nombre_usuario = 'vmora' ORDER BY f.id_factor DESC LIMIT 1
"@
Check 'el factor queda REVOCADO, no borrado' ($factorAgente -eq 'R') "estado=$factorAgente"

# Y al volver a entrar, la sesion nace CAPADA: no hay forma de acabar operando
# sin haber vuelto a enrolar.
Start-Sleep -Milliseconds 1500
$reentrada = Api POST '/auth/mfa/desafio' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' } $null
Check 'sin factor, el login vuelve a ser de un solo paso' ($reentrada.token.Length -gt 40) 'sesion'
Check 'y esa sesion SI alcanza el enrolamiento' `
    ((CodigoDe GET '/perfil/mfa' $reentrada.token $null $null) -eq 200) 'perfil/mfa'
Check 'pero NO alcanza a operar: queda capada' `
    ((CodigoDe GET '/clientes?pagina=1&tamano=1' $reentrada.token $null $null) -eq 403) 'capada'

Write-Host "`n== 11d. Nivel 2: la cuenta afectada queda capada ==" -ForegroundColor Cyan
# `rsalas` perdio su factor en la seccion 9 por revocacion del TENANT_ADMIN.
Start-Sleep -Milliseconds 1500
$afectado = Api POST '/auth/mfa/desafio' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' } $null
Check 'el afectado entra, pero con la sesion capada' `
    ((CodigoDe GET '/perfil/mfa' $afectado.token $null $null) -eq 200) 'perfil/mfa'
Check 'y NO puede administrar miembros' `
    ((CodigoDe GET '/agentes?pagina=1&tamano=1' $afectado.token $null $null) -eq 403) 'agentes'

Write-Host "`n== 11e. Gobierno de accesos (padron y aviso) ==" -ForegroundColor Cyan
# Se usan cuentas SIN MFA y sin capar (`sramirez`, `rgomez`): con una sesion
# capada o muerta el 403 llegaria por el capado o el 401 por la revocacion, y
# la prueba no diria nada sobre el gate de rol, que es lo que se quiere medir.
$otroBroker = (Api POST '/auth/mfa/desafio' $null @{ usuario = 'sramirez'; contrasena = 'Broker2026' } $null).token
$otroAgente = (Api POST '/auth/mfa/desafio' $null @{ usuario = 'rgomez'; contrasena = 'Agente2026' } $null).token

# Se cuenta sobre el JSON CRUDO, no sobre los objetos que devuelve
# Invoke-RestMethod: en PowerShell 5.1 un array de objetos no siempre llega
# enumerado al llamador, y contar ahi daba 1 para veintiuna cuentas — un fallo
# de la prueba que costo tres corridas confundir con un fallo de la consulta.
# Lo que importa es lo que viaja por el cable, asi que se mide eso.
$cuentasJson = TextoDe GET '/accesos' $adminToken
$nCuentas = ([regex]::Matches($cuentasJson, '"idPersona"')).Count
$credencialesEnBase = [int](Sql "SELECT count(*) FROM credencial_usuario WHERE organizacion_id = 1")
Check 'el padron devuelve TODAS las cuentas del tenant' ($nCuentas -eq $credencialesEnBase) `
    "api=$nCuentas base=$credencialesEnBase"
$cuentas = @(Api GET '/accesos' $adminToken $null $null)
Check 'y trae los DOS identificadores (persona y rol)' `
    ($null -ne $cuentas[0].idPersona -and $null -ne $cuentas[0].idRol) 'ids'
Check 'el padron NO expone secretos ni hashes' (SinSecretos ($cuentas | ConvertTo-Json -Depth 4)) 'higiene'
Check 'un BROKER no ve el padron' ((CodigoDe GET '/accesos' $otroBroker $null $null) -eq 403) 'broker'
Check 'un AGENTE tampoco' ((CodigoDe GET '/accesos' $otroAgente $null $null) -eq 403) 'agente'

$avisos = Api GET '/seguridad/avisos?pagina=1&tamano=50' $adminToken $null $null
$revocaciones = @($avisos.items | Where-Object { $_.tipo -eq 'MFA_REVOCADO' })
Check 'el aviso de gobierno registra la revocacion ajena' ($revocaciones.Count -ge 1) `
    "n=$(@($avisos.items).Count)"
Check 'y trae el motivo escrito por quien revoco' `
    (@($avisos.items | Where-Object { $_.motivo -like '*telefono perdido*' }).Count -ge 1) 'motivo'
Check 'el aviso NO expone secretos ni hashes' (SinSecretos ($avisos | ConvertTo-Json -Depth 5)) 'higiene'
Check 'un BROKER no lee el aviso de gobierno' `
    ((CodigoDe GET '/seguridad/avisos' $otroBroker $null $null) -eq 403) 'broker'

# Otro tenant responde 404, no 403: un 403 confirmaria que esa persona existe.
Sql "INSERT INTO organizacion (id_organizacion, codigo, nombre) VALUES (9001, 'E2E_OTRO', 'Otra corredora') ON CONFLICT DO NOTHING" | Out-Null
Sql @"
INSERT INTO persona (id_persona, tipo_persona, tipo_documento, numero_documento,
                     nombres_o_razon_social, organizacion_id)
VALUES (9001, 'N', 'D', '99887766', 'Ajena De Otro Tenant', 9001) ON CONFLICT DO NOTHING
"@ | Out-Null
EsperarPasoNuevo
$elevacionAjena = Api POST '/perfil/elevacion' $adminToken `
    @{ contrasena = 'Admin2026'; codigo = (CodigoAhora $secreto) } $null
$httpOtroTenant = CodigoDe DELETE '/accesos/9001/mfa' $adminToken @{ motivo = 'prueba de frontera' } `
    @{ 'X-Elevacion' = $elevacionAjena.token }
Check 'una persona de OTRO tenant responde 404, no 403' ($httpOtroTenant -eq 404) "http=$httpOtroTenant"

Write-Host "`n== 12. Higiene: ningun secreto en la auditoria ==" -ForegroundColor Cyan
$sucios = [int](Sql "SELECT count(*) FROM evento_seguridad WHERE detalle_json::text ~* '(secreto|codigo|desafio|elevacion|hash)'")
Check 'ningun evento lleva secretos ni codigos' ($sucios -eq 0) "sucios=$sucios"
$emitidos = Sql "SELECT string_agg(DISTINCT tipo, ',' ORDER BY tipo) FROM evento_seguridad WHERE tipo LIKE 'MFA%' OR tipo LIKE 'ELEVACION%'"
Check 'se auditan los hechos de MFA' `
    ($emitidos -like '*MFA_ACTIVADO*' -and $emitidos -like '*MFA_OK*' -and $emitidos -like '*ELEVACION_EMITIDA*') `
    "tipos=$emitidos"

# ---------------------------------------------------------------------
Write-Host "`n== Resumen ==" -ForegroundColor Cyan
Write-Host "  OK: $ok   FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
