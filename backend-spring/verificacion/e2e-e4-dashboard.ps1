# =====================================================================
# E2E de E4 (dashboard, indicadores y seguimiento comercial) contra el API v2.
#
# Como son AGREGADOS sobre una BD con seed, el script no compara valores
# absolutos: toma una foto ANTES, crea un fixture identificable y comprueba los
# DELTAS mas los invariantes de forma (donut exclusivo, embudo, series, techo de
# paginacion, alcance por rol y aislamiento de tenant).
#
# Autoridad: docs/1INF50/BROX-KAIROS-catalogo-operativo.md (CAT-E29/E31)
# y docs/1INF50/BROX_KAIROS_directriz_clientes_independientes.md, seccion5.3.
# C4: work/c4-c5-20261030/BROX-ESPECIFICACION-IMPLEMENTACION-C4-C5-2026-10-30.md, secciones4/10.
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite e4-dashboard
#             (compatible con Windows PowerShell 5.1 y con pwsh 7)
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
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
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$otroAgente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.ltorres.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del agente de otro equipo' ($otroAgente.rol -eq 'AGENTE') $otroAgente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del administrador' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

Write-Host "`n== 2. Operacion comercial y gobierno separados ==" -ForegroundColor Cyan
$antesAgente = Api GET '/indicadores/resumen?periodo=7d' $agente.token $null
$antesBroker = Api GET '/indicadores/resumen?periodo=7d' $broker.token $null
$antesAdmin = ApiError GET '/indicadores/resumen?periodo=7d' $admin.token $null
Check 'gobierno no obtiene indicadores comerciales' ($antesAdmin.codigo -eq 403) "codigo=$($antesAdmin.codigo)"
# El rotulo cambio a proposito: "Reportes de equipo" se leia como el titulo de
# una seccion, no como el alcance de lo que se esta mirando
# (`IndicadorServiceImpl:805`, con su prueba unitaria). Este guion llevaba el
# texto viejo y nadie lo noto porque la suite no arrancaba.
Check 'el ambito del BROKER es de equipo' ($antesBroker.ambito -eq 'Mi equipo') $antesBroker.ambito
Check 'el ambito del AGENTE es personal' ($antesAgente.ambito -eq 'Mi actividad') $antesAgente.ambito
Check 'agente y broker reciben su propio alcance comercial' `
    ($null -ne $antesAgente.ambito -and $null -ne $antesBroker.ambito) 'sin gate de rol'

$avanceAntes = Api GET '/indicadores/avance' $agente.token $null
Check 'el ambito del avance del AGENTE es personal' `
    ($avanceAntes.ambito -eq 'Mi avance comercial') $avanceAntes.ambito
Check 'gobierno no obtiene avance comercial' `
    ((ApiError GET '/indicadores/avance' $admin.token $null).codigo -eq 403) 'gobierno'
Check 'el ambito del avance del BROKER es de equipo' `
    ((Api GET '/indicadores/avance' $broker.token $null).ambito -eq 'Avance comercial del equipo') 'ambito'

Write-Host "`n== 3. Periodo: ventanas, etiquetas y sinonimos ==" -ForegroundColor Cyan
$siete = Api GET '/indicadores/resumen?periodo=7d' $broker.token $null
$quince = Api GET '/indicadores/resumen?periodo=15' $broker.token $null
$mes = Api GET '/indicadores/resumen?periodo=mes' $broker.token $null
$seisMeses = Api GET '/indicadores/resumen' $broker.token $null
$basura = Api GET '/indicadores/resumen?periodo=no-existe' $broker.token $null
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
        -and $null -eq $siete.operativo.diasPromedioSinSeguimiento `
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
# La demora no evaluable no es cero ni una cantidad de tareas.
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
Check 'la demora no evaluable se conserva explicita y no suma pendientes' `
    ($null -eq $demora.valor -and $demora.estadoCalculo -eq 'NO_EVALUABLE' `
        -and $demora.causa -and -not $demora.requiereAtencion) 'demora no evaluable'

Check 'el desempeno corta en 8 filas' ($siete.desempeno.Count -le 8) $siete.desempeno.Count
Check 'la denegacion de gobierno no entrega desempeno comercial' `
    ($antesAdmin.codigo -eq 403 -and $null -eq $antesAdmin.desempeno) 'gobierno'
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
# Dos preparaciones reales C2. Los codigos son los que devuelve Core; el
# marcador de busqueda se declara en el codigo/direccion de los inmuebles TEST.
$local = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token `
    -Direccion "Av. Indicadores E4-$sufijo" -IdPropietario $propietario.id -Codigo "LOC-E4-$sufijo" `
    -Distrito 'Barranco' -Metraje 150 -Rubro 'Retail E4' -Importe 8100 -Moneda 'PEN' `
    -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' -Exclusividad $false `
    -InicioEncargo $hoy -FinEncargo $finEncargo -Descripcion "Preparacion TEST E4-$sufijo"
$captacionPendiente = Api GET "/captaciones/$($local.idEncargo)" $agente.token $null
$codigoPendiente = $captacionPendiente.codigoCaptacion
$idLocal = [long]$local.id
$idCliente = [long]$cliente.id
$idAgente = [long]$agente.idDominio
$idCaptacionPendiente = [long]$local.idEncargo
Check 'propiedad y preparacion C2 conservan sus identidades' ($idLocal -gt 0 -and $idCaptacionPendiente -gt 0) 'preparacion'
Check 'abrir preparacion no acredita revision aprobatoria' ($captacionPendiente.estado -eq 'P') $captacionPendiente.estado
$local2 = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token `
    -Direccion "Av. Avance E4-$sufijo" -IdPropietario $propietario.id -Codigo "LOC-E4B-$sufijo" `
    -Distrito 'Barranco' -Metraje 210 -Rubro 'Retail E4' -Importe 9900 -Moneda 'PEN' `
    -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' -Exclusividad $true `
    -InicioEncargo $hoy -FinEncargo $finEncargo -Descripcion "Encargo habilitado TEST E4-$sufijo"
$idCaptacion = [long]$local2.idEncargo
$null = HabilitarEncargoC2DePrueba -IdEncargo $idCaptacion -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato' -EjecucionComision @{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER'}}
$captacion = Api GET "/captaciones/$idCaptacion" $agente.token $null
$codigoActiva = $captacion.codigoCaptacion
$idLocal2 = [long]$local2.id
Check 'la revision competente C2 habilita el Encargo' ($captacion.estado -eq 'A') $captacion.estado

# R27 requiere una Prospeccion SIN Encargo propio. Se crea por su productor,
# no sembrando fechas legacy ni un contador equivalente por SQL.
$localProspeccion = NuevoInmuebleSinEncargo -LocalIndependienteTest -Token $agente.token `
    -Direccion "Prospeccion TEST E4-$sufijo" -IdPropietario $propietario.id -Codigo "LOC-E4P-$sufijo" `
    -Distrito 'Barranco' -Metraje 80 -Rubro 'Retail E4'
$idProspeccion = NuevaProspeccion -Token $agente.token -IdPropiedad $localProspeccion.id -Observaciones "Seguimiento TEST E4-$sufijo"
$revisiones = @(Api GET "/prospecciones/$idProspeccion/seguimientos" $agente.token $null)
if ($revisiones.Count -ne 1) { throw 'El fixture R27 necesita su revision contextual real, no una fila fabricada.' }
$fechaDeclarada = $hoyFecha.AddDays(-11).ToString('yyyy-MM-dd')
$planR27 = Invoke-ComandoC2DePrueba POST "/prospecciones/$idProspeccion/seguimientos/$($revisiones[0].id)/planificacion" $agente.token @{
    versionEsperada = $revisiones[0].version; origen = 'INTENCION_PROFESIONAL'; fecha = $fechaDeclarada
    detalle = 'Fecha explicita vencida de planificacion TEST; no prueba una llamada.'
} "e4-plan-r27-$sufijo"
$oportunidadDeAvance = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $idCliente -Token $agente.token `
    -Codigo "OP-E4-$sufijo" -Observaciones "Oportunidad TEST E4-$sufijo" `
    -EvidenciaInteres 'Manifestacion FICTICIA explicita de interes del cliente TEST del avance.'
$idOportunidad = [long]$oportunidadDeAvance.id

$visitaDeAvance = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $hoy -Hora '11:00' -Token $agente.token -Observaciones "Visita TEST E4 $sufijo"
$null = ResultadoVisitaC4DePrueba -IdVisita $visitaDeAvance.id -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $hoy `
    -Desenlace @{ resultado = 'INTERESADO'; nivelInteres = 5 }

# Comunicacion real de TEST con fecha efectiva declarada, no fecha de registro
# convertida a contacto ni resultado comercial INTERESADO.
$datosComunicacion = NuevaInteraccionC4DePrueba `
    -Vinculos @(@{ tipo = 'OPORTUNIDAD'; referencia = $idOportunidad }) `
    -Resumen "Comunicacion E4 $sufijo" -Fuente "Fixture TEST E4 $sufijo" -Fecha $hoy
$comunicacion = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token `
    -Datos $datosComunicacion -Clave "e4-comunicacion-$sufijo"
Check 'el hecho comunicativo conserva fecha efectiva y no inventa resultado' `
    ($comunicacion.resultado.fechaHecho -eq $hoy -and $null -eq $comunicacion.resultado.datos.resultado) `
    "id=$($comunicacion.resultado.id)"

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
# La fecha del plan se consulta al productor R27. No hay reloj de siete dias
# ni promedio de demora inventado a partir de fecha_contacto/fecha_registro.
Check 'el plan R27 vencido cuenta sin inventar una demora media' `
    ($despues.operativo.recontactosVencidos -ge 1 -and $null -eq $despues.operativo.diasPromedioSinSeguimiento) 'R27 y demora no evaluable'
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
$adminDespues = ApiError GET '/indicadores/resumen?periodo=7d' $admin.token $null
Check 'el fixture no confiere cartera por gobernar' ($adminDespues.codigo -eq 403) 'gobierno'

Write-Host "`n== 8. Avance comercial (RF-017) ==" -ForegroundColor Cyan
$avance = Api GET '/indicadores/avance' $agente.token $null
$filaAvance = $avance.detalle | Where-Object { $_.codigoCaptacion -eq $codigoActiva }
Check 'la captacion ACTIVA aparece en el avance' ($null -ne $filaAvance) $codigoActiva
Check 'la captacion PENDIENTE no aparece en el avance' `
    (($avance.detalle | Where-Object { $_.codigoCaptacion -eq $codigoPendiente }).Count -eq 0) `
    $codigoPendiente
Check 'la fila del avance trae direccion, distrito y estado comercial' `
    ($filaAvance.direccion -eq "Av. Avance E4-$sufijo" -and $filaAvance.distrito -eq 'Barranco' `
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
$dashAdmin = ApiError GET '/dashboard?tamano=3' $admin.token $null
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
Check 'gobierno no recibe dashboard comercial como bandeja vacia' `
    ($dashAdmin.codigo -eq 403) 'gobierno'
Check 'el dashboard respeta el periodo' `
    ((Api GET '/dashboard?periodo=7d' $agente.token $null).indicadores.mesesEtiquetas.Count -eq 7) `
    'periodo'

Write-Host "`n== 10. Seguimiento comercial: las cinco etapas ==" -ForegroundColor Cyan
$seg = Api GET "/seguimiento-comercial?q=E4-$sufijo&page_size=8" $agente.token $null
$segTodo = Api GET '/seguimiento-comercial' $agente.token $null
Check 'la busqueda libre encuentra las 5 filas del fixture por codigo' `
    ($seg.totalRecords -eq 5) "total=$($seg.totalRecords)"
$filaProspeccion = Fila $seg 'Prospeccion'
$filaCaptacion = $seg.items | Where-Object { $_.codigo -eq $codigoPendiente }
$filaOportunidad = Fila $seg 'Oportunidad'
$filaSolicitud = Fila $seg 'Solicitud'
Check 'la prospeccion trae icono store, tono blue y su ruta' `
    ($filaProspeccion.icono -eq 'store' -and $filaProspeccion.tono -eq 'blue' `
        -and $filaProspeccion.ruta -eq "prospeccion-detail/$idProspeccion") `
    "$($filaProspeccion.icono)/$($filaProspeccion.tono)/$($filaProspeccion.ruta)"
Check 'la captacion trae icono pin y ruta por codigo' `
    ($filaCaptacion.icono -eq 'pin' -and $filaCaptacion.ruta -eq "captacion-detail/$codigoPendiente") `
    "$($filaCaptacion.icono)/$($filaCaptacion.ruta)"
Check 'solo la captacion PENDIENTE trae rutaRevision' `
    ($filaCaptacion.rutaRevision -eq "captacion-review/$codigoPendiente") `
    $filaCaptacion.rutaRevision
Check 'la captacion ACTIVA no trae rutaRevision' `
    ((($seg.items | Where-Object { $_.codigo -eq $codigoActiva }).rutaRevision) -eq '') 'vacia'
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
$segAdmin = ApiError GET "/seguimiento-comercial?q=E4-$sufijo" $admin.token $null
Check 'el agente de otro equipo no ve ninguna fila del fixture' `
    ($segOtro.counts.todos -eq 0) "total=$($segOtro.counts.todos)"
Check 'el broker supervisor ve las filas de su agente' `
    ($segBroker.counts.todos -eq $seg.counts.todos) `
    "broker=$($segBroker.counts.todos) agente=$($seg.counts.todos)"
Check 'gobierno no obtiene seguimiento ni contadores de cartera' `
    ($segAdmin.codigo -eq 403 -and $null -eq $segAdmin.counts) 'gobierno'

Write-Host "`n== 13. El cierre entra al donut y a los cierres ==" -ForegroundColor Cyan
# El cierre pasa por la CASCADA CONTRACTUAL REAL (F4 section 6), no por SQL: el
# broker aprueba la solicitud y el agente registra el alquiler. Insertar el
# contrato a mano ya no es legal - ck_contrato_formalizado_completo exige el
# snapshot (inicio, fin, renta y moneda) para D/V - y ademas mentia: dejaba la
# captacion ABIERTA y el local publicado, que no es el mundo que produce el
# cierre. Los siete efectos se comprueban en e2e-f4-solicitud; aqui solo se
# verifica que E4 lee lo que la cascada dejo.
$aprobacion = Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; observaciones = 'Expediente conforme E4.'
    idSolicitud = $idSolicitud
}
Check 'el broker aprueba la solicitud del fixture' ($aprobacion.resultado -eq 'A') $aprobacion.resultado
$preparado=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$idSolicitud;incidencias="Preparación E4 TEST $sufijo"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
Check 'preparar conserva la ausencia de firma/cierre' ($preparado.estadoContrato -eq 'P' -and $null -eq $preparado.fechaCierre) 'preparación'
$formalizado=FormalizarAlquilerC5DePrueba -IdEncargo $idCaptacion -IdOportunidad $idOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 9500 -Moneda 'PEN' -Inicio $preparado.fechaInicioContrato -Fin $preparado.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $preparado.id
$contrato=$formalizado.contrato
Check 'el hito contractual real produce su contrato firmado' `
    ($contrato.estadoContrato -eq 'D' -and $contrato.rentaMensual -eq 9500 `
        -and $contrato.plazoContratoMeses -eq 18 -and $contrato.fechaInicioContrato -eq $hoy) `
    "$($contrato.estadoContrato)/$($contrato.rentaMensual)/$($contrato.plazoContratoMeses)"
Check 'la cascada cerro solicitud (C), oportunidad (F) y captacion (C)' `
    ((Invoke-ControlLocalE2ESql "select s.estado||'|'||o.estado||'|'||c.estado
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
Check 'el estado del cierre es la descripcion del contrato' ($filaCierre.estado -eq 'Firmado') `
    $filaCierre.estado
Check 'el cierre trae la fecha de cierre como ultimo hito' ($filaCierre.ultimoHito -eq $hoy) `
    $filaCierre.ultimoHito
Check 'el agente de otro equipo no ve el cierre' `
    ((Api GET "/seguimiento-comercial?q=E4-$sufijo&tipo=Cierre" $otroAgente.token $null).counts.cierre -eq 0) `
    'aislado'

Write-Host "`n== 14. Aislamiento de tenant ==" -ForegroundColor Cyan
$brokerAntesOrg2 = Api GET '/indicadores/resumen?periodo=7d' $broker.token $null
$otroTenant = Initialize-ControlLocalE2EActors -Etiqueta 'contrario'
$gobiernoOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $otroTenant.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
$agenteOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $otroTenant.organizacion -Rol 'AGENTE'
$brokerOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $otroTenant.organizacion -Rol 'BROKER' -EnrolarSiempre
$propietarioOtroTenant = NuevoPropietarioC2DePrueba -Token $agenteOtroTenant.token -Nombre 'Propietario ficticio del otro tenant E4'
$localOtroTenant = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agenteOtroTenant.token -IdPropietario $propietarioOtroTenant.id `
    -Direccion 'Predio ficticio E4-ORG2' -Distrito 'Ate' -Codigo "LOC-E4-ORG2-$sufijo" -Metraje 100 -Rubro 'Retail TEST' `
    -Importe 1000 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' `
    -InicioEncargo $hoy -FinEncargo $finEncargo -Exclusividad $true
$habilitadoOtro = HabilitarEncargoC2DePrueba -IdEncargo $localOtroTenant.idEncargo -TokenResponsable $agenteOtroTenant.token -TokenDecisor $brokerOtroTenant.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST extranjero' -Beneficiario 'Agencia TEST extranjera' -Exigibilidad 'A la firma del contrato'
$positivoOtro = Api GET '/indicadores/resumen?periodo=7d' $agenteOtroTenant.token $null
Check 'el positivo extranjero existe y esta realmente habilitado por Core' `
    ($habilitadoOtro.vigencia.habilitado -and $positivoOtro.captacionesActivas -eq 1) 'positivo extranjero'
$brokerConOrg2 = Api GET '/indicadores/resumen?periodo=7d' $broker.token $null
Check 'otro tenant no cambia los indicadores del broker propio' `
    ($brokerConOrg2.captacionesActivas -eq $brokerAntesOrg2.captacionesActivas) 'aislamiento'
$segOrg2 = Api GET '/seguimiento-comercial?q=E4-ORG2' $broker.token $null
Check 'el broker no obtiene filas comerciales del otro tenant' ($segOrg2.counts.todos -eq 0) 'aislamiento'
$avanceOrg2 = Api GET '/indicadores/avance' $broker.token $null
Check 'el avance no incluye el Encargo extranjero' `
    (@($avanceOrg2.detalle | Where-Object { $_.idCaptacion -eq $localOtroTenant.idEncargo }).Count -eq 0) 'aislamiento'
Check 'las opciones no divulgan el distrito exclusivo del otro tenant' `
    (-not ((Api GET '/seguimiento-comercial' $broker.token $null).options.distritos -contains 'Ate')) 'aislamiento'
# El historial extranjero permanece intacto hasta el teardown del runner.
Write-Host "`n== 15. Sin token no hay indicadores ==" -ForegroundColor Cyan
Check 'GET /indicadores/resumen sin token responde 401' `
    ((ApiError GET '/indicadores/resumen' $null).codigo -eq 401) 'sin token'
Check 'GET /dashboard sin token responde 401' `
    ((ApiError GET '/dashboard' $null).codigo -eq 401) 'sin token'
Check 'GET /seguimiento-comercial sin token responde 401' `
    ((ApiError GET '/seguimiento-comercial' $null).codigo -eq 401) 'sin token'

} finally {
    # La historia, recibos y documentos canonicos no se borran por SQL para
    # limpiar el fixture. Invoke-E2E elimina su base/contenedor EXCLUSIVOS.
    $ctxFinal = Assert-ControlLocalE2EContext
    Check 'el teardown pertenece a la base exclusiva de esta corrida' `
        ($ctxFinal.Database -eq $e2e.Database -and $ctxFinal.RunId -eq $e2e.RunId) 'contexto E2E'
    Write-Host 'E2E_CONSERVACION historia y recibos preservados hasta el teardown unitario del runner.'
}
Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
