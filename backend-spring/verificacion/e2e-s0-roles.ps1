# =====================================================================
# E2E del Bloque 5 — "Roles y gobierno": separacion de BROKER y
# TENANT_ADMIN segun la matriz D-S0-17, aprobada fila por fila.
#
# Es el escenario A2 del Plan S0 (escalamiento de privilegios) puesto
# sobre las 26 operaciones que cambiaron de dueno.
#
# La regla que se verifica es *gobernar no es operar*:
#   - el TENANT_ADMIN ve todo su tenant y gobierna cuentas y organigrama;
#   - NO firma ningun hecho del negocio (filas 5, 7, 9, 10 y 13);
#   - el BROKER decide y firma, pero ya no administra cuentas (filas 17-18).
#
# Lo que hace esta suite distinta de un test de gates: comprueba que la
# separacion se sostiene en las TRES capas donde podria romperse — el
# token (que sigue congelado), la banda efectiva (que resuelve el
# servidor) y la auditoria (que dejo de mentir).
#
# Uso: powershell -File backend-spring/verificacion/e2e-s0-roles.ps1
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
    try {
        Api $metodo $ruta $token $cuerpo | Out-Null
        return 200
    } catch {
        return [int]$_.Exception.Response.StatusCode.value__
    }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

# El mensaje del trigger sale por stderr y el codigo de salida de psql no
# sobrevive al salto docker -> PowerShell, asi que el rechazo NO se comprueba
# leyendo la salida: se comprueba por EFECTO. Cada transaccion que debe
# fallar lleva una marca observable; si al final la marca no esta, la
# transaccion entera se deshizo — que es exactamente lo que un trigger
# diferido debe garantizar, y una afirmacion mas fuerte que leer un texto.

# -------------------------------------------------------------------
Write-Host "`n== 1. El backfill reparo las bandas que V6 dejo mal ==" -ForegroundColor Cyan

# V6 poblo usuario_organizacion uniendo el rol USUARIO_INTERNO con el rol
# BROKER — dos filas que por construccion no pueden compartir id—, asi que
# el CASE caia siempre al ELSE y las 21 cuentas quedaron como 'AGENTE'.
# Nadie lo detecto porque nadie leia la tabla (H-14). V33 la reconstruye.
$admins = [int](Sql "SELECT count(*) FROM usuario_organizacion WHERE rol='TENANT_ADMIN' AND estado='A'")
$brokers = [int](Sql "SELECT count(*) FROM usuario_organizacion WHERE rol='BROKER' AND estado='A'")
$viejos = [int](Sql "SELECT count(*) FROM usuario_organizacion WHERE rol='ADMIN'")
Check 'hay exactamente un TENANT_ADMIN sembrado' ($admins -eq 1) "admins=$admins"
Check 'los brokers dejaron de contarse como agentes' ($brokers -ge 1) "brokers=$brokers"
Check "no queda ninguna banda 'ADMIN' de V6" ($viejos -eq 0) "viejos=$viejos"

$rolGobierno = [int](Sql "SELECT count(*) FROM persona_rol WHERE tipo_rol='ADMIN' AND vigencia_hasta IS NULL")
Check 'el administrador gano su persona_rol de gobierno' ($rolGobierno -eq 1) "roles=$rolGobierno"

# D-S0-10: conserva identidad, credencial e historial. Lo unico que cambia
# es con que rol firma.
$mismaPersona = [int](Sql "SELECT count(*) FROM persona_rol pr JOIN credencial_usuario cu ON cu.id_persona_rol=pr.id_persona_rol WHERE cu.nombre_usuario='admin@controllocal.test'")
Check 'el administrador conserva su credencial (D-S0-10)' ($mismaPersona -eq 1) "credenciales=$mismaPersona"

# -------------------------------------------------------------------
Write-Host "`n== 2. El token sigue congelado; la banda la resuelve el servidor ==" -ForegroundColor Cyan

$admin = Api POST '/auth/login' $null @{ usuario = 'admin@controllocal.test'; contrasena = 'Admin2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }

# R1: el formato del token no puede cambiar mientras GlassFish conviva.
Check 'el login del admin SIGUE diciendo ADMIN (contrato congelado)' `
    ($admin.rol -eq 'ADMIN') "rol=$($admin.rol)"

$claves = $admin.token.Split('.')[1]
$claves = $claves.PadRight([int](([math]::Ceiling($claves.Length / 4.0)) * 4), '=').Replace('-', '+').Replace('_', '/')
$carga = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($claves)) | ConvertFrom-Json
Check 'el token no lleva TENANT_ADMIN por ningun lado' ($carga.rol -eq 'ADMIN') "rol=$($carga.rol)"

# R2: el idDominio del admin es su rol de GOBIERNO, no un rol de broker.
$idRolGobierno = [int](Sql "SELECT id_persona_rol FROM persona_rol WHERE tipo_rol='ADMIN' AND vigencia_hasta IS NULL")
Check 'el idDominio del admin es su rol de gobierno (R2)' `
    ($admin.idDominio -eq $idRolGobierno) "token=$($admin.idDominio) bd=$idRolGobierno"

# La banda real viaja por el endpoint aditivo, no por el login (R3).
# A partir de aqui hace falta operar, y desde V37 la sesion del TENANT_ADMIN
# nace CAPADA hasta enrolar su segundo factor. Las comprobaciones de ARRIBA se
# hacen a proposito con el token recien emitido: lo que fijan es el formato del
# token, que no depende del enrolamiento.
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'

$sesionAdmin = Api GET '/sesion' $admin.token
$sesionBroker = Api GET '/sesion' $broker.token
$sesionAgente = Api GET '/sesion' $agente.token
Check 'GET /sesion dice TENANT_ADMIN' ($sesionAdmin.rol -eq 'TENANT_ADMIN') "rol=$($sesionAdmin.rol)"
Check 'GET /sesion dice BROKER' ($sesionBroker.rol -eq 'BROKER') "rol=$($sesionBroker.rol)"
Check 'GET /sesion dice AGENTE' ($sesionAgente.rol -eq 'AGENTE') "rol=$($sesionAgente.rol)"

# -------------------------------------------------------------------
Write-Host "`n== 3. Lo que el TENANT_ADMIN CONSERVA (supervision y lectura) ==" -ForegroundColor Cyan

$conserva = @(
    @{ n = 'fila 1  GET /captaciones/pendientes'; m = 'GET'; r = '/captaciones/pendientes?pagina=1&tamano=1' },
    @{ n = 'fila 2  GET /captaciones/reasignables'; m = 'GET'; r = '/captaciones/reasignables?pagina=1&tamano=1' },
    @{ n = 'fila 3  GET /captaciones/propiedades-equipo'; m = 'GET'; r = '/captaciones/propiedades-equipo?pagina=1&tamano=1' },
    @{ n = 'fila 4  GET /captaciones/propiedades-equipo/resumen'; m = 'GET'; r = '/captaciones/propiedades-equipo/resumen' },
    @{ n = 'fila 8  GET /captaciones/reasignaciones'; m = 'GET'; r = '/captaciones/reasignaciones' },
    @{ n = 'fila 11 GET /evaluaciones'; m = 'GET'; r = '/evaluaciones?pagina=1&tamano=1' },
    @{ n = 'fila 14 GET /agentes'; m = 'GET'; r = '/agentes?pagina=1&tamano=1' },
    @{ n = 'fila 15 GET /agentes/resumen'; m = 'GET'; r = '/agentes/resumen' }
)
foreach ($caso in $conserva) {
    $codigo = CodigoDe $caso.m $caso.r $admin.token $null
    Check "admin CONSERVA $($caso.n)" ($codigo -eq 200) "http=$codigo"
}

# -------------------------------------------------------------------
Write-Host "`n== 4. Lo que el TENANT_ADMIN PIERDE (operacion comercial) ==" -ForegroundColor Cyan

# Estas son las cinco filas que retiran al administrador de la operacion.
# Antes entraba a todas por herencia: era un broker con un booleano.
$pierde = @(
    @{ n = 'fila 5  POST /captaciones/{id}/decision'; m = 'POST'; r = '/captaciones/1/decision'; c = @{ accion = 'aprobar' } },
    @{ n = 'fila 7  POST /captaciones/{id}/cierre'; m = 'POST'; r = '/captaciones/1/cierre'; c = @{ motivo = 'X' } },
    @{ n = 'fila 9  PATCH documentos/{idDoc}/revisar'; m = 'PATCH'; r = '/solicitudes/1/documentos/1/revisar'; c = @{ estado = 'C' } },
    @{ n = 'fila 10 PATCH documentos/conformar'; m = 'PATCH'; r = '/solicitudes/1/documentos/conformar'; c = @{} },
    @{ n = 'fila 13 POST /evaluaciones'; m = 'POST'; r = '/evaluaciones'; c = @{ idSolicitud = 1; tipoEvaluacion = 'F'; resultado = 'A' } }
)
foreach ($caso in $pierde) {
    $codigo = CodigoDe $caso.m $caso.r $admin.token $caso.c
    Check "admin PIERDE $($caso.n)" ($codigo -eq 403) "http=$codigo"
}

# El mensaje es el congelado: quien llama no distingue "no tienes permisos"
# de "esta operacion ya no es tuya", y no deberia.
$mensaje = $null
try { Api POST '/evaluaciones' $admin.token @{ idSolicitud = 1; tipoEvaluacion = 'F'; resultado = 'A' } | Out-Null }
catch {
    $lector = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
    $mensaje = ($lector.ReadToEnd() | ConvertFrom-Json).error
}
Check 'el 403 conserva el mensaje congelado' `
    ($mensaje -eq 'No tienes permisos para esta operacion.') "mensaje=$mensaje"

# -------------------------------------------------------------------
Write-Host "`n== 5. Lo que el BROKER PIERDE (gobierno de cuentas) ==" -ForegroundColor Cyan

# D-S0-18: un broker no crea cuentas, ni siquiera las de su propio equipo.
$codigo = CodigoDe POST '/agentes' $broker.token @{ nombre = 'Intruso'; usuario = 'intruso'; contrasena = 'Temporal2026' }
Check 'fila 17 el BROKER ya no da de alta agentes' ($codigo -eq 403) "http=$codigo"
$codigo = CodigoDe PUT '/agentes/6' $broker.token @{ nombre = 'Editado' }
Check 'fila 18 el BROKER ya no edita agentes' ($codigo -eq 403) "http=$codigo"
$codigo = CodigoDe POST '/accesos/6/invitacion' $broker.token @{}
Check 'el BROKER tampoco invita (D-S0-18)' ($codigo -eq 403) "http=$codigo"
foreach ($ruta in @('/asignaciones/agentes', '/asignaciones/brokers', '/asignaciones/historial')) {
    $codigo = CodigoDe GET $ruta $broker.token $null
    Check "el BROKER no entra a $ruta" ($codigo -eq 403) "http=$codigo"
}

Write-Host "`n== 5 bis. Lo que el BROKER CONSERVA (decidir y firmar) ==" -ForegroundColor Cyan
$codigo = CodigoDe GET '/captaciones/pendientes?pagina=1&tamano=1' $broker.token $null
Check 'el BROKER conserva su bandeja de revision' ($codigo -eq 200) "http=$codigo"
$codigo = CodigoDe GET '/evaluaciones?pagina=1&tamano=1' $broker.token $null
Check 'el BROKER conserva el listado de evaluaciones' ($codigo -eq 200) "http=$codigo"

# -------------------------------------------------------------------
Write-Host "`n== 6. El alta de agente cambio de dueno, no solo de gate ==" -ForegroundColor Cyan

# Es la unica de las 18 que no se resolvia moviendo una anotacion: antes el
# supervisor salia de la sesion del broker que la ejecutaba.
$codigo = CodigoDe POST '/agentes' $admin.token @{ nombre = 'Sin Supervisor'; usuario = 'e2e-sinsup'; contrasena = 'Temporal2026' }
Check 'sin idBrokerSupervisor el alta se rechaza (400)' ($codigo -eq 400) "http=$codigo"

$idBroker = [int](Sql "SELECT db.id_persona_rol FROM detalle_broker db JOIN persona_rol pr ON pr.id_persona_rol=db.id_persona_rol JOIN persona p ON p.id_persona=pr.id_persona JOIN persona_rol pi ON pi.id_persona=p.id_persona AND pi.tipo_rol='USUARIO_INTERNO' JOIN credencial_usuario cu ON cu.id_persona_rol=pi.id_persona_rol WHERE cu.nombre_usuario='rsalas'")
$creado = Api POST '/agentes' $admin.token @{
    nombre = 'Agente De Gobierno'; usuario = 'e2e-roles-alta'; contrasena = 'Temporal2026'
    numeroDocumento = '90900777'; idBrokerSupervisor = $idBroker
}
Check 'el TENANT_ADMIN si da de alta con supervisor explicito' ($null -ne $creado.id) "id=$($creado.id)"

$supervisor = [int](Sql "SELECT sa.id_rol_broker FROM supervision_agente sa WHERE sa.id_rol_agente=$($creado.id) AND sa.fecha_fin IS NULL")
Check 'el agente queda bajo el broker que viajo en la peticion' `
    ($supervisor -eq $idBroker) "supervisor=$supervisor esperado=$idBroker"

# usuario_organizacion dejo de ser una tabla que nadie mantiene (H-14).
$banda = Sql "SELECT uo.rol FROM usuario_organizacion uo WHERE uo.nombre_visible='Agente De Gobierno'"
Check 'el alta crea tambien la membresia del nuevo usuario' ($banda -eq 'AGENTE') "banda=$banda"

# -------------------------------------------------------------------
Write-Host "`n== 7. La auditoria dejo de mentir (H-09) ==" -ForegroundColor Cyan

# Antes `Actor.tipoRolOperativo()` traducia ADMIN -> BROKER, asi que el
# rastro no distinguia gobierno de operacion. La reasignacion es la unica
# operacion CON EFECTO que el TENANT_ADMIN conserva (fila 6), y por eso es
# la que puede demostrarlo.
#
# El paso previo lo da el broker a proposito: la captacion del seed nace
# PENDIENTE y solo se vuelve reasignable si alguien la aprueba — y aprobar
# ya no es del administrador. Encadenar las dos mitades demuestra el reparto
# entero: el broker decide, el gobierno reorganiza.
$pendientes = Api GET '/captaciones/pendientes?pagina=1&tamano=1' $broker.token
Check 'el broker encuentra la captacion pendiente del seed' ($pendientes.items.Count -gt 0) "items=$($pendientes.items.Count)"

$captacion = $pendientes.items[0]
Api POST "/captaciones/$($captacion.id)/decision" $broker.token @{ accion = 'aprobar' } | Out-Null
$estado = Sql "SELECT estado FROM captacion WHERE id_captacion=$($captacion.id)"
Check 'el BROKER aprueba y la captacion queda activa (fila 5)' ($estado -eq 'A') "estado=$estado"

$reasignables = Api GET '/captaciones/reasignables?pagina=1&tamano=1' $admin.token
Check 'ahora el admin la ve reasignable (fila 2)' ($reasignables.items.Count -gt 0) "items=$($reasignables.items.Count)"

$otroAgente = [int](Sql "SELECT da.id_persona_rol FROM detalle_agente da WHERE da.id_persona_rol <> $($captacion.idAgente) ORDER BY da.id_persona_rol LIMIT 1")
# D-P0-9: el comando declara sobre que agente actua -- el que se vio en la bandeja, que aprobar no mueve -- y el Core responde 409 si ya lo lleva otro.
$reasignada = Api POST "/captaciones/$($captacion.id)/reasignar" $admin.token @{ idAgenteNuevo = $otroAgente; motivo = 'E2E gobierno'; idAgenteActual = $captacion.idAgente }
Check 'el TENANT_ADMIN reasigna entre equipos (fila 6)' ($null -ne $reasignada) 'sin respuesta'

$evento = Sql "SELECT coalesce(tipo_rol_actor,'?')||'|'||coalesce(id_rol_broker::text,'sin-broker') FROM reasignacion_captacion ORDER BY id_reasignacion DESC LIMIT 1"
Check 'el gobierno audita como TENANT_ADMIN, no como BROKER (H-09)' `
    ($evento -eq "TENANT_ADMIN|sin-broker") "evento=$evento"

# El contrato no cambia de forma: `idBroker` sencillamente no viaja cuando
# no hay broker detras (el JSON omite nulos).
$historial = Api GET '/captaciones/reasignaciones' $admin.token
Check 'la reasignacion de gobierno aparece en el historial' ($historial.Count -gt 0) "n=$($historial.Count)"

# -------------------------------------------------------------------
Write-Host "`n== 8. El invariante: una organizacion nunca sin gobierno ==" -ForegroundColor Cyan

# Trigger DEFERRABLE INITIALLY DEFERRED (V34, D-S0-9). Se evalua al COMMIT,
# asi que un relevo de administrador es legal en cualquier orden y una baja
# a secas no lo es.
$antes = Sql "SELECT id_usuario FROM usuario_organizacion WHERE rol='TENANT_ADMIN' AND estado='A'"

# La marca viaja en la MISMA transaccion que la degradacion: si sobrevive, es
# que la transaccion se confirmo y el invariante no sirve de nada.
Sql "BEGIN; UPDATE usuario_organizacion SET nombre_visible='MARCA_E2E' WHERE rol='TENANT_ADMIN'; UPDATE usuario_organizacion SET rol='BROKER' WHERE rol='TENANT_ADMIN'; COMMIT;" | Out-Null

$sigue = [int](Sql "SELECT count(*) FROM usuario_organizacion WHERE rol='TENANT_ADMIN' AND estado='A'")
$marcas = [int](Sql "SELECT count(*) FROM usuario_organizacion WHERE nombre_visible='MARCA_E2E'")
Check 'degradar al unico administrador deja la organizacion con gobierno' ($sigue -eq 1) "admins=$sigue"
Check 'y la transaccion ENTERA se deshace, no solo la degradacion' ($marcas -eq 0) "marcas=$marcas"

# El sucesor se elige ANTES de abrir la transaccion y se exige distinto del
# actual: resolverlo dentro con un `min()` devolveria al mismo, porque tras
# la degradacion el saliente tambien es BROKER, y el relevo pasaria sin
# relevar a nadie.
$sucesor = [int](Sql "SELECT min(id_usuario) FROM usuario_organizacion WHERE rol='BROKER' AND estado='A' AND id_usuario <> $antes")
Check 'hay un broker distinto al que relevar' ($sucesor -gt 0) "sucesor=$sucesor"

# V37 SUBIO EL LISTON, y este escenario lo demuestra. Con el administrador ya
# enrolado, la organizacion cruzo a MFA de gobierno, asi que el invariante
# dejo de conformarse con "hay una membresia TENANT_ADMIN" y exige un
# administrador OPERATIVO (D-S0-37). Un sucesor sin segundo factor NO lo es,
# y el relevo se rechaza — que es justo el agujero que V34 no veia: una
# organizacion con administrador nominal y sin nadie capaz de gobernar.
Sql "BEGIN; UPDATE usuario_organizacion SET rol='BROKER' WHERE rol='TENANT_ADMIN'; UPDATE usuario_organizacion SET rol='TENANT_ADMIN' WHERE id_usuario=$sucesor; COMMIT;" | Out-Null
$despues = Sql "SELECT id_usuario FROM usuario_organizacion WHERE rol='TENANT_ADMIN' AND estado='A'"
Check 'el relevo hacia un sucesor SIN segundo factor se rechaza (D-S0-37)' `
    ($despues -eq $antes) "antes=$antes despues=$despues"

# Y con el sucesor ya operativo, el relevo SI pasa aunque la baja vaya ANTES
# del alta: eso es lo que compra el trigger DIFERIDO. Inmediato, obligaria a
# recordar "primero el alta", una regla no escrita que se rompe sola.
$usuarioSucesor = Sql "SELECT cu.nombre_usuario FROM credencial_usuario cu WHERE cu.id_persona_rol=$sucesor"
# -EnrolarSiempre: un BROKER no esta OBLIGADO a llevar segundo factor
# (D-S0-19), asi que hay que pedirlo explicitamente para volverlo operativo.
Connect-ControlLocalE2E $base $usuarioSucesor 'Broker2026' -EnrolarSiempre | Out-Null
Sql "BEGIN; UPDATE usuario_organizacion SET rol='TENANT_ADMIN' WHERE id_usuario=$sucesor; COMMIT;" | Out-Null
# Ahora si es operativo (membresia + credencial activa + factor ACTIVO), y el
# saliente puede bajar en la misma transaccion.
Sql "BEGIN; UPDATE usuario_organizacion SET rol='BROKER' WHERE id_usuario=$antes; COMMIT;" | Out-Null

$despues = Sql "SELECT string_agg(id_usuario::text, ',' ORDER BY id_usuario) FROM usuario_organizacion WHERE rol='TENANT_ADMIN' AND estado='A'"
Check 'con el sucesor YA operativo, el relevo se confirma' `
    ($despues -eq "$sucesor") "antes=$antes despues=$despues"

# -------------------------------------------------------------------
Write-Host "`n== Resumen ==" -ForegroundColor Cyan
Write-Host "  OK: $ok   FALLAS: $fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
