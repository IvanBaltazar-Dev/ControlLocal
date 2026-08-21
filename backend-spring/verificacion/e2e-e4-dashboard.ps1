# =====================================================================
# E2E de E4 (dashboard, indicadores y seguimiento comercial) contra el API v2.
#
# Como son AGREGADOS sobre una BD con seed, el script no compara valores
# absolutos: toma una foto ANTES, crea un fixture identificable y comprueba los
# DELTAS mas los invariantes de forma (donut exclusivo, embudo, series, techo de
# paginacion, alcance por rol y aislamiento de tenant).
#
# Contrato: docs/ai/contrato-congelado-e4-dashboard-indicadores-seguimiento.md
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite e4-dashboard
#             (compatible con Windows PowerShell 5.1 y con pwsh 7)
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

function ParametrosApi($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $parametros = @{
        Method = $metodo
        Uri = "$base$ruta"
        Headers = $headers
        TimeoutSec = 60
    }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 6)
        $parametros['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) {
        $parametros['ContentType'] = 'application/json'
    }
    $parametros
}

function Api($metodo, $ruta, $token, $cuerpo) {
    $parametros = ParametrosApi $metodo $ruta $token $cuerpo
    Invoke-RestMethod @parametros
}

function ApiError($metodo, $ruta, $token, $cuerpo) {
    try {
        Api $metodo $ruta $token $cuerpo | Out-Null
        return @{ codigo = 0; error = '(la llamada no fallo)' }
    } catch {
        $respuesta = $PSItem.Exception.Response
        if ($null -eq $respuesta) { return @{ codigo = -1; error = $PSItem.Exception.Message } }
        return @{ codigo = [int]$respuesta.StatusCode; error = '' }
    }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

function Etapa($resumen, $nombre) {
    ($resumen.etapas | Where-Object { $_.nombre -eq $nombre }).valor
}

function Salud($resumen, $nombre) {
    ($resumen.captacionesSalud | Where-Object { $_.nombre -eq $nombre }).valor
}

function Fila($pagina, $proceso) {
    $pagina.items | Where-Object { $_.proceso -eq $proceso } | Select-Object -First 1
}

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$finEncargo = $hoyFecha.AddDays(90).ToString('yyyy-MM-dd')
$vigenciaOferta = $hoyFecha.AddDays(15).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login y actores ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$otroAgente = Api POST '/auth/login' $null @{ usuario = 'ltorres'; contrasena = 'Agente2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del agente de otro equipo' ($otroAgente.rol -eq 'AGENTE') $otroAgente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del administrador' ($admin.rol -eq 'ADMIN') $admin.rol

Write-Host "`n== 2. Los tres recursos abren para los tres roles ==" -ForegroundColor Cyan
$antesAgente = Api GET '/indicadores/resumen?periodo=7d' $agente.token $null
$antesBroker = Api GET '/indicadores/resumen?periodo=7d' $broker.token $null
$antesAdmin = Api GET '/indicadores/resumen?periodo=7d' $admin.token $null
Check 'el ambito del ADMIN es global' ($antesAdmin.ambito -eq 'Reportes globales') $antesAdmin.ambito
Check 'el ambito del BROKER es de equipo' ($antesBroker.ambito -eq 'Reportes de equipo') $antesBroker.ambito
Check 'el ambito del AGENTE es personal' ($antesAgente.ambito -eq 'Mi actividad') $antesAgente.ambito
Check 'ningun rol recibe 403 en /indicadores/resumen (el cable no lleva gate)' `
    ($null -ne $antesAgente.ambito -and $null -ne $antesBroker.ambito) 'sin gate de rol'

$avanceAntes = Api GET '/indicadores/avance' $agente.token $null
Check 'el ambito del avance del AGENTE es personal' `
    ($avanceAntes.ambito -eq 'Mi avance comercial') $avanceAntes.ambito
Check 'el ambito del avance del ADMIN es global' `
    ((Api GET '/indicadores/avance' $admin.token $null).ambito -eq 'Avance comercial global') 'ambito'
Check 'el ambito del avance del BROKER es de equipo' `
    ((Api GET '/indicadores/avance' $broker.token $null).ambito -eq 'Avance comercial del equipo') 'ambito'

Write-Host "`n== 3. Periodo: ventanas, etiquetas y sinonimos ==" -ForegroundColor Cyan
$siete = Api GET '/indicadores/resumen?periodo=7d' $admin.token $null
$quince = Api GET '/indicadores/resumen?periodo=15' $admin.token $null
$mes = Api GET '/indicadores/resumen?periodo=mes' $admin.token $null
$seisMeses = Api GET '/indicadores/resumen' $admin.token $null
$basura = Api GET '/indicadores/resumen?periodo=no-existe' $admin.token $null
Check 'periodo 7d da 7 cubos diarios' ($siete.mesesEtiquetas.Count -eq 7) $siete.mesesEtiquetas.Count
Check 'periodo 15 (sinonimo) da 15 cubos' ($quince.mesesEtiquetas.Count -eq 15) $quince.mesesEtiquetas.Count
Check 'periodo mes (sinonimo de 1m) da 30 cubos' ($mes.mesesEtiquetas.Count -eq 30) $mes.mesesEtiquetas.Count
Check 'la etiqueta diaria es dd/MM' `
    ($siete.mesesEtiquetas[-1] -eq $hoyFecha.ToString('dd/MM')) $siete.mesesEtiquetas[-1]
Check 'sin periodo cae en 6 meses con serie mensual' `
    ($seisMeses.mesesEtiquetas.Count -le 7 -and $seisMeses.mesesEtiquetas[-1] -match '^[A-Z][a-z]{2} \d{2}$') `
    "$($seisMeses.mesesEtiquetas.Count) / $($seisMeses.mesesEtiquetas[-1])"
Check 'un periodo desconocido cae en el mismo 6 meses' `
    ($basura.mesesEtiquetas.Count -eq $seisMeses.mesesEtiquetas.Count) `
    "$($basura.mesesEtiquetas.Count) vs $($seisMeses.mesesEtiquetas.Count)"
Check 'las tres series comparten longitud con las etiquetas' `
    ($siete.cierresPorMes.Count -eq 7 -and $siete.captacionesPorPeriodo.Count -eq 7 `
        -and $siete.conversionPorPeriodo.Count -eq 7) `
    "cierres=$($siete.cierresPorMes.Count) caps=$($siete.captacionesPorPeriodo.Count) conv=$($siete.conversionPorPeriodo.Count)"
Check 'ninguna conversion por periodo supera 100' `
    (($siete.conversionPorPeriodo + $seisMeses.conversionPorPeriodo | Where-Object { $_ -gt 100 }).Count -eq 0) `
    'cohorte'
# E2.0 (2026-08-10): conversionPropia es el UNICO numerico nulable del resumen.
# Sin captaciones en el periodo no hay tasa que calcular, y decir 0 hacia
# indistinguible ese caso del de haber trabajado doce y no cerrar ninguna. Cuando
# viene, sigue acotada a 100.
Check 'conversionPropia, cuando existe, nunca supera 100' `
    (($null -eq $siete.conversionPropia -or $siete.conversionPropia -le 100) -and `
     ($null -eq $seisMeses.conversionPropia -or $seisMeses.conversionPropia -le 100)) `
    "$($siete.conversionPropia) / $($seisMeses.conversionPropia)"
Check 'sin captaciones en el periodo la conversion viaja nula, no cero' `
    ($siete.captacionesTotales -gt 0 -or $null -eq $siete.conversionPropia) `
    "caps=$($siete.captacionesTotales) conv=$($siete.conversionPropia)"
Check 'con captaciones en el periodo la conversion viaja como numero' `
    ($seisMeses.captacionesTotales -eq 0 -or $null -ne $seisMeses.conversionPropia) `
    "caps=$($seisMeses.captacionesTotales) conv=$($seisMeses.conversionPropia)"

Write-Host "`n== 4. Forma congelada del resumen ==" -ForegroundColor Cyan
Check 'el donut trae las 5 etapas con sus nombres exactos' `
    ((($siete.etapas | ForEach-Object { $_.nombre }) -join '|') -eq `
        'Captacion activa|Clientes interesados|Con solicitud|En evaluacion|Alquilada') `
    (($siete.etapas | ForEach-Object { $_.nombre }) -join '|')
Check 'la salud trae los 4 cubos con sus nombres exactos' `
    ((($siete.captacionesSalud | ForEach-Object { $_.nombre }) -join '|') -eq `
        'Activas|Por revisar|Observadas|Bloqueadas/cerradas') `
    (($siete.captacionesSalud | ForEach-Object { $_.nombre }) -join '|')
Check 'el embudo trae los 4 tramos con sus nombres exactos' `
    ((($siete.embudo | ForEach-Object { $_.etapa }) -join '|') -eq `
        'Oportunidades activas|Con visita realizada|Con solicitud creada|Cerradas exitosas') `
    (($siete.embudo | ForEach-Object { $_.etapa }) -join '|')
# Descongelado 2026-08-08. Antes este check exigia 100 FIJO en el primer tramo,
# incluso con base cero; ahora el porcentaje sigue a la base, asi que se
# comprueba la relacion y no la constante.
Check 'el primer tramo del embudo vale 100 solo si hay base' `
    (($siete.embudo[0].valor -gt 0 -and $siete.embudo[0].porcentaje -eq 100) -or `
     ($siete.embudo[0].valor -eq 0 -and $siete.embudo[0].porcentaje -eq 0)) `
    "valor=$($siete.embudo[0].valor) pct=$($siete.embudo[0].porcentaje)"
# `captacionesPendientes` se retiro del contrato: duplicaba captacionesPorRevisar.
Check 'captacionesPendientes ya no viaja en la respuesta' `
    ($null -eq $siete.captacionesPendientes) `
    "$($siete.captacionesPendientes)"
Check 'el operativo trae sus 6 campos' `
    ($null -ne $siete.operativo.recontactosVencidos -and $null -ne $siete.operativo.recontactosAlDia `
        -and $null -ne $siete.operativo.diasPromedioSinSeguimiento `
        -and $null -ne $siete.operativo.visitasPendientes `
        -and $null -ne $siete.operativo.solicitudesSinCierre `
        -and $null -ne $siete.operativo.conversionProspeccionCaptacion) 'operativo'

# --- E1: el hecho ya interpretado (R-07) ------------------------------------
# El backend clasifica; Angular pinta. Si estas cuatro caen, la pantalla habria
# tenido que volver a decidir por su cuenta que numero duele, que es justo la
# duplicacion que E1 vino a cerrar.
$conceptos = @('SOLICITUD_POR_EVALUAR', 'RECONTACTO_VENCIDO', 'CAPTACION_POR_REVISAR',
    'SOLICITUD_APROBADA_SIN_CIERRE', 'DEMORA_DE_SEGUIMIENTO', 'VISITA_PENDIENTE',
    'CIERRE_REGISTRADO', 'COBERTURA_DE_AGENTES')
$senales = @($siete.senales)
Check 'las senales traen los 8 conceptos, incluidos los que estan en cero' `
    ((($senales | ForEach-Object { $_.concepto }) -join '|') -eq ($conceptos -join '|')) `
    (($senales | ForEach-Object { $_.concepto }) -join '|')
Check 'las senales llegan ordenadas por la prioridad del dominio' `
    ((($senales | ForEach-Object { $_.prioridad }) -join ',') -eq `
        ((($senales | ForEach-Object { $_.prioridad }) | Sort-Object) -join ',')) `
    (($senales | ForEach-Object { $_.prioridad }) -join ',')
$niveles = @($senales | ForEach-Object { $_.nivelAtencion } | Sort-Object -Unique)
Check 'cada senal viaja con un nivel del vocabulario acordado' `
    (@($niveles | Where-Object {
        $_ -notin @('ALTO', 'MEDIO', 'INFORMATIVO', 'SIN_PENDIENTES')
    }).Count -eq 0) ($niveles -join '|')
# Un informativo en cero NO es "todo al dia": es cero. La diferencia importa
# porque decide el color y el usuario la lee como buena noticia.
$visitas = $senales | Where-Object { $_.concepto -eq 'VISITA_PENDIENTE' }
Check 'un concepto informativo nunca baja a SIN_PENDIENTES' `
    ($visitas.nivelAtencion -eq 'INFORMATIVO' -and -not $visitas.requiereAtencion) `
    "$($visitas.nivelAtencion)/$($visitas.requiereAtencion)"
$vencidos = $senales | Where-Object { $_.concepto -eq 'RECONTACTO_VENCIDO' }
Check 'la senal de recontacto repite el numero del operativo, ya clasificado' `
    ($vencidos.valor -eq $siete.operativo.recontactosVencidos `
        -and $vencidos.nivelAtencion -eq $(if ($vencidos.valor -gt 0) { 'ALTO' } else { 'SIN_PENDIENTES' })) `
    "valor=$($vencidos.valor) nivel=$($vencidos.nivelAtencion)"

# E2.1: la cabecera del tablero abre con "N cosas necesitan tu atencion". Ese N
# lo suma el dominio y NO se puede derivar sumando las senales en el cliente:
# DEMORA_DE_SEGUIMIENTO vale dias, no cosas.
$cosasPendientes = 0
foreach ($s in $senales) {
    if ($s.requiereAtencion -and $s.concepto -ne 'DEMORA_DE_SEGUIMIENTO') {
        $cosasPendientes += $s.valor
    }
}
Check 'pendientesDeAtencion suma las senales que cuentan cosas' `
    ($siete.pendientesDeAtencion -eq $cosasPendientes) `
    "backend=$($siete.pendientesDeAtencion) recalculado=$cosasPendientes"
$demora = $senales | Where-Object { $_.concepto -eq 'DEMORA_DE_SEGUIMIENTO' }
Check 'los dias de atraso no se cuelan entre las cosas pendientes' `
    ($demora.valor -eq 0 -or $siete.pendientesDeAtencion -lt ($cosasPendientes + $demora.valor)) `
    "atraso=$($demora.valor) pendientes=$($siete.pendientesDeAtencion)"

Check 'el desempeno corta en 8 filas' ($siete.desempeno.Count -le 8) $siete.desempeno.Count
Check 'el desempeno del ADMIN compara brokers, no agentes' `
    ($antesAdmin.brokersActivos -ge 1) "brokersActivos=$($antesAdmin.brokersActivos)"
Check 'el AGENTE cuenta 1 agente activo y 0 brokers' `
    ($antesAgente.agentesActivos -eq 1 -and $antesAgente.brokersActivos -eq 0) `
    "agentes=$($antesAgente.agentesActivos) brokers=$($antesAgente.brokersActivos)"
Check 'el BROKER cuenta 1 broker activo y su equipo' `
    ($antesBroker.brokersActivos -eq 1 -and $antesBroker.agentesActivos -ge 1) `
    "brokers=$($antesBroker.brokersActivos) agentes=$($antesBroker.agentesActivos)"

Write-Host "`n== 5. Fixture identificable de E4 ==" -ForegroundColor Cyan
$seguimientoAntes = Api GET '/seguimiento-comercial' $agente.token $null
$propietario = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "85$sufijo"
    nombre = "Propietario E4 $sufijo"; telefono = '987400001'
    correo = "propietario.e4.$sufijo@test.local"; consentimientoUsoDato = $true; estado = 'A'
}
$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "86$sufijo"
    nombre = "Cliente E4 $sufijo"; telefono = '987400002'
    correo = "cliente.e4.$sufijo@test.local"; rubroComercial = 'Retail E4'
    consentimientoContacto = $true; consentimientoUsoDato = $true; estado = 'A'
}
$local = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-E4-$sufijo"; direccion = "Av. Indicadores E4 $sufijo"
    distrito = 'Barranco'; metraje = 150; precioReferencial = 8100; monedaReferencial = 'PEN'
    rubroPermitido = 'Retail E4'; idPropietario = $propietario.id; estadoPublicacion = 'P'
}
$captacionPendiente = Api POST '/captaciones' $agente.token @{
    codigoCaptacion = "CAP-P-E4-$sufijo"; fechaCaptacion = $hoy; fechaInicioVigencia = $hoy
    fechaFinVigencia = $finEncargo
    comisionPactada = 100; observaciones = "Fixture pendiente E4 $sufijo"
    idLocal = $local.id; motivoOperacion = 'A'; urgencia = 1; exclusividad = $false
}
$idLocal = [long]$local.id
$idCliente = [long]$cliente.id
$idAgente = [long]$agente.idDominio
$idCaptacionPendiente = [long]$captacionPendiente.id
Check 'se crea el propietario E4' ($propietario.id -gt 0) "id=$($propietario.id)"
Check 'se crea el local E4 en Barranco' ($idLocal -gt 0) "id=$idLocal"
Check 'la captacion nace PENDIENTE de revision' ($captacionPendiente.estado -eq 'P') `
    $captacionPendiente.estado

# Segundo local + captacion ACTIVA: la que llevara oportunidad, visita y solicitud.
$local2 = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-E4B-$sufijo"; direccion = "Av. Avance E4 $sufijo"
    distrito = 'Barranco'; metraje = 210; precioReferencial = 9900; monedaReferencial = 'PEN'
    rubroPermitido = 'Retail E4'; idPropietario = $propietario.id; estadoPublicacion = 'P'
}
$captacion = Api POST '/captaciones' $agente.token @{
    codigoCaptacion = "CAP-A-E4-$sufijo"; fechaCaptacion = $hoy; fechaInicioVigencia = $hoy
    fechaFinVigencia = $finEncargo
    comisionPactada = 100; observaciones = "Fixture activo E4 $sufijo"
    idLocal = $local2.id; motivoOperacion = 'A'; urgencia = 3; exclusividad = $true
}
$captacion = Api POST "/captaciones/$($captacion.id)/decision" $broker.token @{
    accion = 'A'; observacion = 'Aprobada para verificar E4.'
}
$idCaptacion = [long]$captacion.id
$idLocal2 = [long]$local2.id
Check 'se activa la captacion del avance' ($captacion.estado -eq 'A') $captacion.estado

Sql @"
insert into prospeccion (
    organizacion_id, codigo_prospeccion, fecha_registro, estado,
    fecha_contacto, fecha_recontacto, observaciones, id_propiedad, id_rol_agente
)
select id_organizacion, 'PRO-E4-$sufijo', now(), 'S',
       date '$hoy', date '$hoy' - 11, 'Prospeccion vencida E4 $sufijo',
       $idLocal, $idAgente
from organizacion where codigo='BROX_LEGACY';

-- La oportunidad nace ABIERTA (A), no en S: la solicitud se registra por el
-- API (POST /solicitudes) y es ESE alta el que la mueve a S. Sembrarla ya en
-- S dejaba la puerta cerrada para la cascada contractual real.
insert into oportunidad_comercial (
    organizacion_id, codigo_oportunidad, estado, id_rol_cliente,
    id_captacion, id_rol_agente, observaciones, fecha_registro,
    fecha_actualizacion_estado
)
select id_organizacion, 'OP-E4-$sufijo', 'A', $idCliente,
       $idCaptacion, $idAgente, 'Oportunidad E4 $sufijo', now(), now()
from organizacion where codigo='BROX_LEGACY';
"@ | Out-Null

$idOportunidad = [long](Sql "select id_oportunidad from oportunidad_comercial where codigo_oportunidad='OP-E4-$sufijo'")
$idProspeccion = [long](Sql "select id_prospeccion from prospeccion where codigo_prospeccion='PRO-E4-$sufijo'")

Sql @"
insert into interaccion_comercial (
    organizacion_id, contexto, id_oportunidad, id_rol_agente,
    canal_contacto, resultado, observaciones, fecha_hora
)
select id_organizacion, 'OPORTUNIDAD', $idOportunidad, $idAgente,
       'W', 'INTERESADO', 'Interaccion E4 $sufijo', now()
from organizacion where codigo='BROX_LEGACY';

insert into visita (
    organizacion_id, id_oportunidad, id_rol_agente, fecha_visita,
    hora_visita, estado, observaciones, resultado, nivel_interes
)
select id_organizacion, $idOportunidad, $idAgente, date '$hoy',
       time '11:00', 'R', 'Visita E4 $sufijo', 'INTERESADO', 5
from organizacion where codigo='BROX_LEGACY';
"@ | Out-Null

# La solicitud YA NO se siembra por SQL: entra por el API con su propio codigo.
# El alta la deja REGISTRADA (G) y mueve la oportunidad a S; el reenvio la pone
# EN EVALUACION (E), que es el estado que miran el donut y solicitudesPorEvaluar.
# Ese camino es tambien el que llena el snapshot economico (fecha de inicio,
# plazo en meses y moneda) sin el cual POST /contratos no puede formalizar.
$solicitud = Api POST '/solicitudes' $agente.token @{
    codigoSolicitud = "SOL-E4-$sufijo"; idOportunidad = $idOportunidad
    montoPropuesto = 9500; moneda = 'PEN'; plazoMeses = 18; fechaInicio = $hoy
    formaPago = 'TRANSFERENCIA'; fechaVigenciaOferta = $vigenciaOferta
    observaciones = "Solicitud E4 $sufijo"
}
$idSolicitud = [long]$solicitud.id
$solicitudEnEvaluacion = Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null
Check 'la solicitud del fixture llega a EN EVALUACION por el camino real' `
    ($solicitudEnEvaluacion.estado -eq 'E') $solicitudEnEvaluacion.estado
Check 'el alta de la solicitud dejo la oportunidad en S (efecto lateral del cable)' `
    ((Api GET "/oportunidades/$idOportunidad" $agente.token).estado -eq 'S') 'estado oportunidad'
Check 'el fixture cubre prospeccion, oportunidad, visita, interaccion y solicitud' `
    ($idOportunidad -gt 0 -and $idProspeccion -gt 0 -and $idSolicitud -gt 0) `
    "op=$idOportunidad pro=$idProspeccion sol=$idSolicitud"

try {

Write-Host "`n== 6. Deltas del resumen del agente ==" -ForegroundColor Cyan
$despues = Api GET '/indicadores/resumen?periodo=7d' $agente.token $null
Check 'captacionesTotales sube 2 (las dos nacidas hoy)' `
    ($despues.captacionesTotales - $antesAgente.captacionesTotales -eq 2) `
    "$($antesAgente.captacionesTotales) -> $($despues.captacionesTotales)"
Check 'captacionesActivas sube 1' `
    ($despues.captacionesActivas - $antesAgente.captacionesActivas -eq 1) `
    "$($antesAgente.captacionesActivas) -> $($despues.captacionesActivas)"
Check 'captacionesPorRevisar sube 1' `
    ($despues.captacionesPorRevisar - $antesAgente.captacionesPorRevisar -eq 1) `
    "$($antesAgente.captacionesPorRevisar) -> $($despues.captacionesPorRevisar)"
Check 'propiedadesEquipo sube 1 (solo la ACTIVA cuenta)' `
    ($despues.propiedadesEquipo - $antesAgente.propiedadesEquipo -eq 1) `
    "$($antesAgente.propiedadesEquipo) -> $($despues.propiedadesEquipo)"
Check 'oportunidadesActivas sube 1 (estado S cuenta como activa)' `
    ($despues.oportunidadesActivas - $antesAgente.oportunidadesActivas -eq 1) `
    "$($antesAgente.oportunidadesActivas) -> $($despues.oportunidadesActivas)"
Check 'solicitudesPorEvaluar sube 1 (estado E)' `
    ($despues.solicitudesPorEvaluar - $antesAgente.solicitudesPorEvaluar -eq 1) `
    "$($antesAgente.solicitudesPorEvaluar) -> $($despues.solicitudesPorEvaluar)"
Check 'visitas del periodo sube 1' ($despues.visitas - $antesAgente.visitas -eq 1) `
    "$($antesAgente.visitas) -> $($despues.visitas)"
Check 'interacciones del periodo sube 1' `
    ($despues.interacciones - $antesAgente.interacciones -eq 1) `
    "$($antesAgente.interacciones) -> $($despues.interacciones)"
Check 'salud Activas sube 1 y Por revisar sube 1 en la ventana' `
    ((Salud $despues 'Activas') - (Salud $antesAgente 'Activas') -eq 1 `
        -and (Salud $despues 'Por revisar') - (Salud $antesAgente 'Por revisar') -eq 1) `
    "activas=$(Salud $despues 'Activas') porRevisar=$(Salud $despues 'Por revisar')"
Check 'la captacion con solicitud E cae en "En evaluacion" del donut' `
    ((Etapa $despues 'En evaluacion') - (Etapa $antesAgente 'En evaluacion') -eq 1) `
    "$(Etapa $antesAgente 'En evaluacion') -> $(Etapa $despues 'En evaluacion')"
Check 'la captacion PENDIENTE no entra en ninguna etapa del donut' `
    ((Etapa $despues 'Captacion activa') -eq (Etapa $antesAgente 'Captacion activa')) `
    "$(Etapa $antesAgente 'Captacion activa') -> $(Etapa $despues 'Captacion activa')"
Check 'el embudo sube su base y su tramo con visita' `
    ($despues.embudo[0].valor - $siete.embudo[0].valor -ge 1 `
        -and $despues.embudo[1].valor -ge 1) `
    "base=$($despues.embudo[0].valor) conVisita=$($despues.embudo[1].valor)"
Check 'el embudo cuenta la oportunidad S como "con solicitud creada"' `
    ($despues.embudo[2].valor -ge 1) $despues.embudo[2].valor
# Aqui el DELTA no sirve, y es del cable: la fuente del operativo cae a TODAS
# las prospecciones del alcance cuando la ventana no tuvo ninguna. La foto
# previa venia de ese fallback (la prospeccion sembrada, 31 dias de atraso) y
# la posterior ya viene de la ventana (solo la del fixture), asi que las dos
# valen 1 y restarlas no mide nada. Se comprueba el valor resultante: con la
# ventana ocupada por el fixture, el promedio de atraso ES el suyo.
Check 'el recontacto atrasado 11 dias cuenta como vencido' `
    ($despues.operativo.recontactosVencidos -ge 1 `
        -and $despues.operativo.diasPromedioSinSeguimiento -eq 11) `
    "vencidos=$($despues.operativo.recontactosVencidos) atraso=$($despues.operativo.diasPromedioSinSeguimiento)"
Check 'la solicitud en E no cuenta como "sin cierre" (solo la aprobada)' `
    ($despues.operativo.solicitudesSinCierre -eq $antesAgente.operativo.solicitudesSinCierre) `
    "$($antesAgente.operativo.solicitudesSinCierre) -> $($despues.operativo.solicitudesSinCierre)"
Check 'el agente aparece en su propio desempeno con sus captaciones' `
    (@($despues.desempeno | Where-Object { $_.captaciones -ge 2 }).Count -ge 1) `
    ($despues.desempeno | ConvertTo-Json -Compress)

Write-Host "`n== 7. El alcance de indicadores es SOLO por agente ==" -ForegroundColor Cyan
$otro = Api GET '/indicadores/resumen?periodo=7d' $otroAgente.token $null
Check 'el agente de otro equipo no ve las captaciones del fixture' `
    ($otro.captacionesTotales -eq 0 -or $otro.captacionesActivas -lt $despues.captacionesActivas) `
    "otro=$($otro.captacionesActivas) propio=$($despues.captacionesActivas)"
$brokerDespues = Api GET '/indicadores/resumen?periodo=7d' $broker.token $null
Check 'el broker supervisor ve las captaciones de su agente' `
    ($brokerDespues.captacionesTotales - $antesBroker.captacionesTotales -eq 2) `
    "$($antesBroker.captacionesTotales) -> $($brokerDespues.captacionesTotales)"
$adminDespues = Api GET '/indicadores/resumen?periodo=7d' $admin.token $null
Check 'el admin ve el tenant completo' `
    ($adminDespues.captacionesTotales -ge $brokerDespues.captacionesTotales) `
    "admin=$($adminDespues.captacionesTotales) broker=$($brokerDespues.captacionesTotales)"

Write-Host "`n== 8. Avance comercial (RF-017) ==" -ForegroundColor Cyan
$avance = Api GET '/indicadores/avance' $agente.token $null
$filaAvance = $avance.detalle | Where-Object { $_.codigoCaptacion -eq "CAP-A-E4-$sufijo" }
Check 'la captacion ACTIVA aparece en el avance' ($null -ne $filaAvance) "CAP-A-E4-$sufijo"
Check 'la captacion PENDIENTE no aparece en el avance' `
    (($avance.detalle | Where-Object { $_.codigoCaptacion -eq "CAP-P-E4-$sufijo" }).Count -eq 0) `
    "CAP-P-E4-$sufijo"
Check 'la fila del avance trae direccion, distrito y estado comercial' `
    ($filaAvance.direccion -eq "Av. Avance E4 $sufijo" -and $filaAvance.distrito -eq 'Barranco' `
        -and $filaAvance.estadoComercial -eq 'Activa') `
    "$($filaAvance.direccion) / $($filaAvance.distrito) / $($filaAvance.estadoComercial)"
Check 'la fila cuenta 1 oportunidad, con visita y con solicitud' `
    ($filaAvance.oportunidadesTotales -eq 1 -and $filaAvance.oportunidadesConVisita -eq 1 `
        -and $filaAvance.oportunidadesConSolicitud -eq 1) `
    "tot=$($filaAvance.oportunidadesTotales) vis=$($filaAvance.oportunidadesConVisita) sol=$($filaAvance.oportunidadesConSolicitud)"
Check 'la fila separa visitas concretadas de programadas' `
    ($filaAvance.visitasConcretadas -eq 1 -and $filaAvance.visitasProgramadas -eq 0) `
    "concretadas=$($filaAvance.visitasConcretadas) programadas=$($filaAvance.visitasProgramadas)"
Check 'la fila cuenta 1 interesado, 1 interaccion y 1 solicitud' `
    ($filaAvance.interesados -eq 1 -and $filaAvance.interacciones -eq 1 `
        -and $filaAvance.solicitudesRecibidas -eq 1) `
    "int=$($filaAvance.interesados) itc=$($filaAvance.interacciones) sol=$($filaAvance.solicitudesRecibidas)"
Check 'las tasas de la fila son 100 y no superan 100' `
    ($filaAvance.tasaOportVisita -eq 100 -and $filaAvance.tasaOportSolicitud -eq 100) `
    "vis=$($filaAvance.tasaOportVisita) sol=$($filaAvance.tasaOportSolicitud)"
Check 'sin motivo de no continuidad la columna viaja vacia' `
    ($filaAvance.motivoNoContinuidad -eq '') "'$($filaAvance.motivoNoContinuidad)'"
Check 'la oportunidad en S no cuenta como abierta' ($filaAvance.oportunidadesAbiertas -eq 0) `
    $filaAvance.oportunidadesAbiertas
Check 'el agregado del avance cuadra con el detalle' `
    ($avance.propiedades -eq $avance.detalle.Count `
        -and $avance.oportunidadesTotales -ge $filaAvance.oportunidadesTotales) `
    "props=$($avance.propiedades) detalle=$($avance.detalle.Count)"

Write-Host "`n== 9. Dashboard ==" -ForegroundColor Cyan
$dashAgente = Api GET '/dashboard' $agente.token $null
$dashBroker = Api GET '/dashboard' $broker.token $null
$dashAdmin = Api GET '/dashboard?tamano=3' $admin.token $null
Check 'el dashboard reusa el mismo resumen de indicadores' `
    ($dashAgente.indicadores.ambito -eq 'Mi actividad') $dashAgente.indicadores.ambito
Check 'el tamano por defecto de la bandeja es 5' ($dashAgente.bandeja.pageSize -eq 5) `
    $dashAgente.bandeja.pageSize
Check 'la bandeja arranca en la pagina 1' ($dashAgente.bandeja.page -eq 1) $dashAgente.bandeja.page
Check 'la bandeja del agente lleva a lo sumo 5 items y no mas que su total' `
    ($dashAgente.bandeja.items.Count -le 5 `
        -and $dashAgente.bandeja.items.Count -le $dashAgente.bandeja.totalRecords) `
    "items=$($dashAgente.bandeja.items.Count) total=$($dashAgente.bandeja.totalRecords)"
Check 'el BROKER recibe bandeja VACIA, no 403' `
    ($dashBroker.bandeja.items.Count -eq 0 -and $dashBroker.bandeja.totalRecords -eq 0) `
    "items=$($dashBroker.bandeja.items.Count) total=$($dashBroker.bandeja.totalRecords)"
Check 'el ADMIN recibe bandeja vacia con el pageSize que pidio' `
    ($dashAdmin.bandeja.items.Count -eq 0 -and $dashAdmin.bandeja.pageSize -eq 3) `
    "items=$($dashAdmin.bandeja.items.Count) pageSize=$($dashAdmin.bandeja.pageSize)"
Check 'el dashboard respeta el periodo' `
    ((Api GET '/dashboard?periodo=7d' $admin.token $null).indicadores.mesesEtiquetas.Count -eq 7) `
    'periodo'

Write-Host "`n== 10. Seguimiento comercial: las cinco etapas ==" -ForegroundColor Cyan
$seg = Api GET "/seguimiento-comercial?q=E4-$sufijo&page_size=8" $agente.token $null
$segTodo = Api GET '/seguimiento-comercial' $agente.token $null
Check 'la busqueda libre encuentra las 5 filas del fixture por codigo' `
    ($seg.totalRecords -eq 5) "total=$($seg.totalRecords)"
$filaProspeccion = Fila $seg 'Prospeccion'
$filaCaptacion = $seg.items | Where-Object { $_.codigo -eq "CAP-P-E4-$sufijo" }
$filaOportunidad = Fila $seg 'Oportunidad'
$filaSolicitud = Fila $seg 'Solicitud'
Check 'la prospeccion trae icono store, tono blue y su ruta' `
    ($filaProspeccion.icono -eq 'store' -and $filaProspeccion.tono -eq 'blue' `
        -and $filaProspeccion.ruta -eq "prospeccion-detail/$idProspeccion") `
    "$($filaProspeccion.icono)/$($filaProspeccion.tono)/$($filaProspeccion.ruta)"
Check 'la captacion trae icono pin y ruta por codigo' `
    ($filaCaptacion.icono -eq 'pin' -and $filaCaptacion.ruta -eq "captacion-detail/CAP-P-E4-$sufijo") `
    "$($filaCaptacion.icono)/$($filaCaptacion.ruta)"
Check 'solo la captacion PENDIENTE trae rutaRevision' `
    ($filaCaptacion.rutaRevision -eq "captacion-review/CAP-P-E4-$sufijo") `
    $filaCaptacion.rutaRevision
Check 'la captacion ACTIVA no trae rutaRevision' `
    ((($seg.items | Where-Object { $_.codigo -eq "CAP-A-E4-$sufijo" }).rutaRevision) -eq '') 'vacia'
Check 'la oportunidad trae icono target, tono info y su cliente' `
    ($filaOportunidad.icono -eq 'target' -and $filaOportunidad.tono -eq 'info' `
        -and $filaOportunidad.cliente -eq "Cliente E4 $sufijo" `
        -and $filaOportunidad.clienteId -eq $idCliente) `
    "$($filaOportunidad.icono)/$($filaOportunidad.tono)/$($filaOportunidad.cliente)"
Check 'la solicitud trae icono fileText, monto plano y ruta de evaluacion' `
    ($filaSolicitud.icono -eq 'fileText' -and $filaSolicitud.monto -eq '9500.00' `
        -and $filaSolicitud.rutaRevision -eq "evaluacion/SOL-E4-$sufijo") `
    "$($filaSolicitud.icono)/$($filaSolicitud.monto)/$($filaSolicitud.rutaRevision)"
Check 'prospeccion y captacion mandan cliente "-" y monto vacio' `
    ($filaProspeccion.cliente -eq '-' -and $filaProspeccion.monto -eq '' `
        -and $filaCaptacion.cliente -eq '-') `
    "$($filaProspeccion.cliente)/$($filaProspeccion.monto)"
Check 'el propietario del local se resuelve en la fila de oportunidad (mapa por local)' `
    ($filaOportunidad.propietario -eq "Propietario E4 $sufijo" `
        -and $filaOportunidad.propietarioId -eq [long]$propietario.id) `
    "$($filaOportunidad.propietario)/$($filaOportunidad.propietarioId)"
Check 'la vigencia de la captacion usa el formato legible del cable' `
    ($filaCaptacion.ultimoHito -match '^(Vigente hasta|Captada el) \d{2} [A-Z][a-z]{2} \d{4}$') `
    $filaCaptacion.ultimoHito

Write-Host "`n== 11. Seguimiento: filtros, aliases, conteos y opciones ==" -ForegroundColor Cyan
$segCaptaciones = Api GET "/seguimiento-comercial?q=E4-$sufijo&tipo=Captacion" $agente.token $null
$segAlias = Api GET "/seguimiento-comercial?q__contains=E4-$sufijo&process__eq=Captacion" $agente.token $null
Check 'el filtro de proceso recorta items pero no counts' `
    ($segCaptaciones.items.Count -eq 2 -and $segCaptaciones.counts.todos -eq $seg.counts.todos) `
    "items=$($segCaptaciones.items.Count) counts=$($segCaptaciones.counts.todos)"
Check 'los aliases en ingles dan el mismo resultado que los cortos' `
    ($segAlias.totalRecords -eq $segCaptaciones.totalRecords `
        -and $segAlias.counts.todos -eq $segCaptaciones.counts.todos) `
    "$($segAlias.totalRecords) vs $($segCaptaciones.totalRecords)"
Check 'los counts desglosan las cinco etapas del fixture' `
    ($seg.counts.prospeccion -ge 1 -and $seg.counts.captacion -eq 2 `
        -and $seg.counts.oportunidad -ge 1 -and $seg.counts.solicitud -ge 1) `
    ($seg.counts | ConvertTo-Json -Compress)
$segDistrito = Api GET "/seguimiento-comercial?district__eq=Barranco&q=E4-$sufijo" $agente.token $null
$segOtroDistrito = Api GET "/seguimiento-comercial?district__eq=Chorrillos&q=E4-$sufijo" $agente.token $null
Check 'el filtro por distrito conserva las filas del fixture y recorta las demas' `
    ($segDistrito.counts.todos -eq $seg.counts.todos -and $segOtroDistrito.counts.todos -eq 0) `
    "barranco=$($segDistrito.counts.todos) chorrillos=$($segOtroDistrito.counts.todos)"
Check 'las options se calculan SIN filtros (siguen ofreciendo todo el alcance)' `
    ($segCaptaciones.options.agentes.Count -eq $segTodo.options.agentes.Count `
        -and $segCaptaciones.options.estados.Count -eq $segTodo.options.estados.Count) `
    "filtrado=$($segCaptaciones.options.agentes.Count) todo=$($segTodo.options.agentes.Count)"
Check 'las options no incluyen el relleno "-"' `
    (($segTodo.options.agentes + $segTodo.options.propietarios + $segTodo.options.estados `
        + $segTodo.options.distritos | Where-Object { $_ -eq '-' }).Count -eq 0) 'sin guiones'
Check 'las options traen Barranco y el propietario del fixture' `
    ($segTodo.options.distritos -contains 'Barranco' `
        -and $segTodo.options.propietarios -contains "Propietario E4 $sufijo") `
    ($segTodo.options.distritos -join ',')
$segGrande = Api GET '/seguimiento-comercial?page_size=50' $agente.token $null
Check 'el tamano de pagina tiene techo 8, no solo defecto' `
    ($segGrande.pageSize -eq 8 -and $segGrande.items.Count -le 8) `
    "pageSize=$($segGrande.pageSize) items=$($segGrande.items.Count)"
Check 'page gana a pagina y una pagina fuera de rango devuelve vacio' `
    ((Api GET '/seguimiento-comercial?page=9999' $agente.token $null).items.Count -eq 0) 'pagina alta'
Check 'una pagina menor que 1 se normaliza a 1' `
    ((Api GET '/seguimiento-comercial?page=-4' $agente.token $null).page -eq 1) 'normalizada'
Check 'el total del seguimiento crece con el fixture' `
    ($segTodo.counts.todos -gt $seguimientoAntes.counts.todos) `
    "$($seguimientoAntes.counts.todos) -> $($segTodo.counts.todos)"

Write-Host "`n== 12. Seguimiento: alcance por rol ==" -ForegroundColor Cyan
$segOtro = Api GET "/seguimiento-comercial?q=E4-$sufijo" $otroAgente.token $null
$segBroker = Api GET "/seguimiento-comercial?q=E4-$sufijo" $broker.token $null
$segAdmin = Api GET "/seguimiento-comercial?q=E4-$sufijo" $admin.token $null
Check 'el agente de otro equipo no ve ninguna fila del fixture' `
    ($segOtro.counts.todos -eq 0) "total=$($segOtro.counts.todos)"
Check 'el broker supervisor ve las filas de su agente' `
    ($segBroker.counts.todos -eq $seg.counts.todos) `
    "broker=$($segBroker.counts.todos) agente=$($seg.counts.todos)"
Check 'el admin ve al menos lo que ve el broker' `
    ($segAdmin.counts.todos -ge $segBroker.counts.todos) `
    "admin=$($segAdmin.counts.todos) broker=$($segBroker.counts.todos)"

Write-Host "`n== 13. El cierre entra al donut y a los cierres ==" -ForegroundColor Cyan
# El cierre pasa por la CASCADA CONTRACTUAL REAL (F4 section 6), no por SQL: el
# broker aprueba la solicitud y el agente registra el alquiler. Insertar el
# contrato a mano ya no es legal —ck_contrato_formalizado_completo exige el
# snapshot (inicio, fin, renta y moneda) para D/V— y ademas mentia: dejaba la
# captacion ABIERTA y el local publicado, que no es el mundo que produce el
# cierre. Los siete efectos se comprueban en e2e-f4-solicitud; aqui solo se
# verifica que E4 lee lo que la cascada dejo.
$aprobacion = Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; observaciones = 'Expediente conforme E4.'
    idSolicitud = $idSolicitud
}
Check 'el broker aprueba la solicitud del fixture' ($aprobacion.resultado -eq 'A') $aprobacion.resultado
$contrato = Api POST '/contratos' $agente.token @{
    idSolicitud = $idSolicitud; fechaCierre = $hoy; estadoContrato = 'V'
    incidencias = "Contrato E4 $sufijo"
}
Check 'POST /contratos formaliza el cierre VIGENTE con su snapshot' `
    ($contrato.estadoContrato -eq 'V' -and $contrato.rentaMensual -eq 9500 `
        -and $contrato.plazoContratoMeses -eq 18 -and $contrato.fechaInicioContrato -eq $hoy) `
    "$($contrato.estadoContrato)/$($contrato.rentaMensual)/$($contrato.plazoContratoMeses)"
Check 'la cascada cerro solicitud (C), oportunidad (F) y captacion (C)' `
    ((Sql "select s.estado||'|'||o.estado||'|'||c.estado
             from solicitud_alquiler s
             join oportunidad_comercial o on o.id_oportunidad=s.id_oportunidad
             join captacion c on c.id_captacion=o.id_captacion
            where s.id_solicitud=$idSolicitud") -eq 'C|F|C') 'cascada'

$conCierre = Api GET '/indicadores/resumen?periodo=7d' $agente.token $null
Check 'cierres del periodo sube 1' ($conCierre.cierres - $despues.cierres -eq 1) `
    "$($despues.cierres) -> $($conCierre.cierres)"
Check 'cierresCohorte sube 1 (la captacion nacio en la ventana)' `
    ($conCierre.cierresCohorte - $despues.cierresCohorte -eq 1) `
    "$($despues.cierresCohorte) -> $($conCierre.cierresCohorte)"
Check 'la captacion pasa de "En evaluacion" a "Alquilada" sin duplicarse' `
    ((Etapa $conCierre 'Alquilada') - (Etapa $despues 'Alquilada') -eq 1 `
        -and (Etapa $conCierre 'En evaluacion') - (Etapa $despues 'En evaluacion') -eq -1) `
    "alquilada=$(Etapa $conCierre 'Alquilada') evaluacion=$(Etapa $conCierre 'En evaluacion')"
Check 'el embudo cuenta la oportunidad F como cerrada exitosa' `
    ($conCierre.embudo[3].valor -ge 1) $conCierre.embudo[3].valor
Check 'el desempeno del agente registra su cierre' `
    (@($conCierre.desempeno | Where-Object { $_.cierres -ge 1 }).Count -ge 1) `
    ($conCierre.desempeno | ConvertTo-Json -Compress)
$segCierre = Api GET "/seguimiento-comercial?q=E4-$sufijo&tipo=Cierre" $agente.token $null
$filaCierre = Fila $segCierre 'Cierre'
Check 'el cierre aparece en el seguimiento con icono checkCircle y tono green' `
    ($filaCierre.icono -eq 'checkCircle' -and $filaCierre.tono -eq 'green') `
    "$($filaCierre.icono)/$($filaCierre.tono)"
Check 'el cierre se arma desde su solicitud: codigo de oportunidad, monto y ruta' `
    ($filaCierre.codigo -eq "OP-E4-$sufijo" -and $filaCierre.monto -eq '9500.00' `
        -and $filaCierre.ruta -eq "solicitud-detail/SOL-E4-$sufijo") `
    "$($filaCierre.codigo)/$($filaCierre.monto)/$($filaCierre.ruta)"
Check 'el estado del cierre es la descripcion del contrato' ($filaCierre.estado -eq 'Vigente') `
    $filaCierre.estado
Check 'el cierre trae la fecha de cierre como ultimo hito' ($filaCierre.ultimoHito -eq $hoy) `
    $filaCierre.ultimoHito
Check 'el agente de otro equipo no ve el cierre' `
    ((Api GET "/seguimiento-comercial?q=E4-$sufijo&tipo=Cierre" $otroAgente.token $null).counts.cierre -eq 0) `
    'aislado'

Write-Host "`n== 14. Aislamiento de tenant ==" -ForegroundColor Cyan
$codigoOrg2 = "E4_$sufijo"
$org2Creada = $false
# La linea base del ADMIN se re-toma AQUI, ya con el cierre aplicado: la
# cascada real cerro la captacion del fixture, asi que la foto de la seccion 7
# ($adminDespues) dejo de servir para "otro tenant no me mueve los numeros".
$adminAntesOrg2 = Api GET '/indicadores/resumen?periodo=7d' $admin.token $null
try {
    $idCaptacionOrg2 = [long](Sql @"
with nueva_org as (
    insert into organizacion (codigo, nombre)
    values ('$codigoOrg2', 'Organizacion temporal E4')
    returning id_organizacion
), persona_propietario as (
    insert into persona (
        tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, estado, organizacion_id
    )
    select 'N', 'D', '87$sufijo', 'Propietario temporal E4', 'A', id_organizacion
    from nueva_org
    returning id_persona, organizacion_id
), rol_propietario as (
    insert into persona_rol (id_persona, tipo_rol, vigencia_desde, organizacion_id)
    select id_persona, 'PROPIETARIO', current_date, organizacion_id
    from persona_propietario
    returning id_persona_rol, organizacion_id
), propiedad_org2 as (
    insert into propiedad (
        codigo, direccion, distrito, metraje, precio_referencial, moneda_referencial,
        estado_registro, disponibilidad_comercial, tipo_inmueble, uso, id_rol_propietario,
        tipo_rol_propietario, organizacion_id
    )
    select 'LOC-E4-ORG2', 'Direccion temporal E4', 'Ate',
           100, 5000, 'PEN', 'A', 'D', 'L', 'C', id_persona_rol, 'PROPIETARIO', organizacion_id
    from rol_propietario
    returning id_propiedad, organizacion_id
), atributo_local as (
    insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
    select organizacion_id, id_propiedad, 'rubro_permitido', 'Temporal E4' from propiedad_org2
), persona_agente as (
    insert into persona (
        tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, estado, organizacion_id
    )
    select 'N', 'D', '88$sufijo', 'Agente temporal E4', 'A', id_organizacion
    from nueva_org
    returning id_persona, organizacion_id
), rol_agente as (
    insert into persona_rol (id_persona, tipo_rol, vigencia_desde, organizacion_id)
    select id_persona, 'AGENTE', current_date, organizacion_id
    from persona_agente
    returning id_persona_rol, organizacion_id
), detalle_agente_org2 as (
    insert into detalle_agente (
        id_persona_rol, tipo_rol, codigo_agente, zona_asignada,
        fecha_ingreso, estado_operativo, organizacion_id
    )
    select id_persona_rol, 'AGENTE', 'AGE-E4-ORG2', 'Org2',
           current_date, 'D', organizacion_id
    from rol_agente
    returning id_persona_rol, organizacion_id
), captacion_org2 as (
    insert into captacion (
        codigo_captacion, fecha_captacion,
        fecha_inicio_encargo, fecha_fin_encargo,
        observaciones, estado, id_propiedad, id_rol_agente,
        motivo_operacion, organizacion_id
    )
    select 'CAP-E4-ORG2', current_date,
           current_date, current_date + 180,
           'Captacion temporal E4', 'P', p.id_propiedad, a.id_persona_rol,
           'A', p.organizacion_id
    from propiedad_org2 p, detalle_agente_org2 a
    returning id_captacion
)
select id_captacion from captacion_org2
"@)
    $org2Creada = $true
    Check 'se crea el fixture E4 de una segunda organizacion' ($idCaptacionOrg2 -gt 0) `
        "captacion=$idCaptacionOrg2"
    $adminConOrg2 = Api GET '/indicadores/resumen?periodo=7d' $admin.token $null
    Check 'la captacion de otro tenant no altera los indicadores del ADMIN legado' `
        ($adminConOrg2.captacionesActivas -eq $adminAntesOrg2.captacionesActivas) `
        "$($adminAntesOrg2.captacionesActivas) -> $($adminConOrg2.captacionesActivas)"
    $segOrg2 = Api GET '/seguimiento-comercial?q=E4-ORG2' $admin.token $null
    Check 'el seguimiento del ADMIN legado no ve filas de otro tenant' `
        ($segOrg2.counts.todos -eq 0) "total=$($segOrg2.counts.todos)"
    $avanceOrg2 = Api GET '/indicadores/avance' $admin.token $null
    Check 'el avance del ADMIN legado no ve propiedades de otro tenant' `
        (($avanceOrg2.detalle | Where-Object { $_.codigoCaptacion -eq 'CAP-E4-ORG2' }).Count -eq 0) `
        'CAP-E4-ORG2'
    Check 'el distrito exclusivo del otro tenant no aparece en las options' `
        (-not ((Api GET '/seguimiento-comercial' $admin.token $null).options.distritos -contains 'Ate')) `
        'Ate'
} finally {
    if ($org2Creada) {
        Sql @"
delete from captacion
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from atributo_propiedad
where id_propiedad in (
    select id_propiedad from propiedad
    where organizacion_id=(
        select id_organizacion from organizacion where codigo='$codigoOrg2'));
delete from propiedad
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from detalle_agente
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from persona_rol
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from persona
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from organizacion where codigo='$codigoOrg2';
"@ | Out-Null
    }
}
Check 'el fixture de segunda organizacion se retira al terminar' `
    ((Sql "select count(*) from organizacion where codigo='$codigoOrg2'") -eq '0') 'cleanup'

Write-Host "`n== 15. Sin token no hay indicadores ==" -ForegroundColor Cyan
Check 'GET /indicadores/resumen sin token responde 401' `
    ((ApiError GET '/indicadores/resumen' $null).codigo -eq 401) 'sin token'
Check 'GET /dashboard sin token responde 401' `
    ((ApiError GET '/dashboard' $null).codigo -eq 401) 'sin token'
Check 'GET /seguimiento-comercial sin token responde 401' `
    ((ApiError GET '/seguimiento-comercial' $null).codigo -eq 401) 'sin token'

} finally {
    Write-Host "`n== Limpieza del fixture E4 ==" -ForegroundColor Cyan
    Sql @"
-- El cierre real cuelga liquidacion de comision del contrato y evaluacion de
-- la solicitud: ninguna de las dos cae por cascada, asi que se retiran antes
-- que su padre. Se localizan por subconsulta (no por el id del contrato) para
-- que la limpieza siga funcionando si el script murio antes del cierre.
delete from comision_movimiento where id_comision_liquidacion in (
    select id_comision_liquidacion from comision_liquidacion
     where id_contrato_alquiler in (
        select id_contrato_alquiler from contrato_alquiler
         where incidencias like '%E4 $sufijo%'));
delete from comision_liquidacion where id_contrato_alquiler in (
    select id_contrato_alquiler from contrato_alquiler
     where incidencias like '%E4 $sufijo%');
delete from historial_estado where entidad_tipo='CONTRATO_ALQUILER' and id_entidad in (
    select id_contrato_alquiler from contrato_alquiler where incidencias like '%E4 $sufijo%');
delete from contrato_alquiler where incidencias like '%E4 $sufijo%';
delete from evaluacion_solicitud where id_solicitud=$idSolicitud;
delete from historial_estado where entidad_tipo='SOLICITUD_ALQUILER' and id_entidad=$idSolicitud;
delete from solicitud_alquiler where codigo_solicitud='SOL-E4-$sufijo';
delete from visita where observaciones like '%E4 $sufijo%';
delete from interaccion_comercial where observaciones like '%E4 $sufijo%';
delete from motivo_no_continuidad where id_oportunidad=$idOportunidad;
delete from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad;
delete from oportunidad_comercial where id_oportunidad=$idOportunidad;
-- La bandeja y la campana se reconcilian al leer /dashboard, asi que el fixture
-- deja tareas y alertas derivadas: se retiran por entidad, no por texto.
delete from tarea where
    (entidad_tipo='CAPTACION' and entidad_id in ($idCaptacion, $idCaptacionPendiente))
 or (entidad_tipo='PROSPECCION' and entidad_id=$idProspeccion)
 or (entidad_tipo='OPORTUNIDAD' and entidad_id=$idOportunidad)
 or (entidad_tipo in ('SOLICITUD', 'SOLICITUD_ALQUILER') and entidad_id=$idSolicitud)
 or (entidad_tipo in ('PROPIEDAD', 'INMUEBLE') and entidad_id in ($idLocal, $idLocal2));
delete from alerta where
    (entidad_tipo='CAPTACION' and entidad_id in ($idCaptacion, $idCaptacionPendiente))
 or (entidad_tipo='PROSPECCION' and entidad_id=$idProspeccion)
 or (entidad_tipo='OPORTUNIDAD' and entidad_id=$idOportunidad)
 or (entidad_tipo in ('SOLICITUD', 'SOLICITUD_ALQUILER') and entidad_id=$idSolicitud)
 or (entidad_tipo in ('PROPIEDAD', 'INMUEBLE') and entidad_id in ($idLocal, $idLocal2));
delete from prospeccion where codigo_prospeccion='PRO-E4-$sufijo'
   or id_propiedad in ($idLocal, $idLocal2);
delete from reasignacion_captacion where id_captacion in ($idCaptacion, $idCaptacionPendiente);
delete from historial_estado where entidad_tipo='CAPTACION'
  and id_entidad in ($idCaptacion, $idCaptacionPendiente);
delete from captacion where id_captacion in ($idCaptacion, $idCaptacionPendiente);
-- DISPONIBILIDAD_PROPIEDAD la escribe el cierre al sacar el local del mercado:
-- es la cuarta fila auditada de la cascada (MEJ-01), y no la dejaba el SQL.
delete from historial_estado
 where entidad_tipo in ('PROPIEDAD', 'INMUEBLE', 'DISPONIBILIDAD_PROPIEDAD')
   and id_entidad in ($idLocal, $idLocal2);
-- Crear el local con estadoPublicacion='P' deja una publicacion, y esas tres
-- colecciones hijas cuelgan del local: sin borrarlas la FK bloquea el propiedad.
delete from publicacion where id_propiedad in ($idLocal, $idLocal2);
delete from foto_propiedad where id_propiedad in ($idLocal, $idLocal2);
delete from precio_propiedad where id_propiedad in ($idLocal, $idLocal2);
delete from atributo_propiedad where id_propiedad in ($idLocal, $idLocal2);
delete from propiedad where id_propiedad in ($idLocal, $idLocal2);
delete from detalle_cliente where id_persona_rol=$idCliente;
delete from persona_rol where id_persona_rol in ($idCliente, $($propietario.id));
-- Desde D-27 (V28) el alta de cliente y de propietario es transaccional y deja
-- ademas su constancia de autorizacion. Sin retirarla, el `delete from persona`
-- choca contra fk_autorizacion_persona_org, aborta la transaccion entera y la
-- limpieza no borra NADA: el residuo sale 2|2|1 y el fallo aparece lejos de su
-- causa. Va antes que la persona, como cualquier otra hija.
delete from autorizacion_tratamiento_evento where id_persona in (
    select id_persona from persona
     where nombres_o_razon_social in ('Propietario E4 $sufijo', 'Cliente E4 $sufijo'));
delete from persona where nombres_o_razon_social in (
    'Propietario E4 $sufijo', 'Cliente E4 $sufijo');
"@ | Out-Null
    $residuo = Sql @"
select
  (select count(*) from captacion where codigo_captacion like 'CAP-%-E4-$sufijo') || '|' ||
  (select count(*) from propiedad where codigo like 'LOC-E4%-$sufijo') || '|' ||
  (select count(*) from oportunidad_comercial where codigo_oportunidad='OP-E4-$sufijo')
"@
    Check 'el fixture E4 se retira por completo' ($residuo -eq '0|0|0') $residuo
}

Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
