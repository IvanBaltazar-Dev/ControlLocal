# Verificacion HTTP del ciclo economico de la comision por movimientos.
#
# POR QUE EXISTE. POST /contratos/{id}/comision/movimientos es el unico de los
# tres gates de comision que ninguna suite ejercitaba: f4-solicitud cubre
# asignar y cobrar, estabilizacion-alquiler cubre el KPI y la anulacion, y el
# endpoint que mueve el dinero de verdad -cobros parciales, pagos al agente,
# reversiones y ajustes- no tenia ni un check sobre el cable.
#
# Lo que fija: que P/R/C se DERIVAN del saldo de comision_movimiento y no los
# elige quien llama, que los topes economicos se aplican sobre el saldo real, y
# que el estado A solo llega por decision expresa.
#
# Numeros del escenario, todos en PEN para que la aritmetica se lea:
#   bruta 1000  =  renta 1000 x 1.00 mensualidades (tipoComision E, base R)
#   parte del agente 400  ->  parte de la empresa 600
#
# ASCII puro y sin BOM a proposito: PowerShell 5.1 lee un .ps1 sin BOM como
# ANSI y un solo caracter acentuado -aunque este dentro de un comentario- rompe
# el parseo del script entero con un error que senala una linea inocente.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0
$fail = 0

function Check($nombre, $condicion, $detalle) {
    if ($condicion) {
        $script:ok++
        Write-Host "  OK   $nombre" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red
    }
}

function ParametrosApi($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $parametros = @{
        Method = $metodo
        Uri = "$base$ruta"
        Headers = $headers
        TimeoutSec = 45
    }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 8)
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

# Igual que Api pero con la cabecera de idempotencia que manda el SPA nuevo.
function ApiIdem($metodo, $ruta, $token, $cuerpo, $clave) {
    $parametros = ParametrosApi $metodo $ruta $token $cuerpo
    $parametros.Headers['Idempotency-Key'] = $clave
    Invoke-RestMethod @parametros
}

function ApiIdemError($metodo, $ruta, $token, $cuerpo, $clave) {
    try {
        ApiIdem $metodo $ruta $token $cuerpo $clave | Out-Null
        return @{ codigo = 0; error = '(la llamada no fallo)' }
    } catch {
        $respuesta = $PSItem.Exception.Response
        if ($null -eq $respuesta) {
            return @{ codigo = -1; error = $PSItem.Exception.Message }
        }
        $codigo = [int]$respuesta.StatusCode
        $contenido = ''
        try {
            $lector = New-Object IO.StreamReader($respuesta.GetResponseStream())
            $contenido = $lector.ReadToEnd()
            $lector.Close()
        } catch { $contenido = '' }
        $mensaje = $contenido
        try { $mensaje = ($contenido | ConvertFrom-Json).error } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}

function ApiError($metodo, $ruta, $token, $cuerpo) {
    try {
        Api $metodo $ruta $token $cuerpo | Out-Null
        return @{ codigo = 0; error = '(la llamada no fallo)' }
    } catch {
        $respuesta = $PSItem.Exception.Response
        if ($null -eq $respuesta) {
            return @{ codigo = -1; error = $PSItem.Exception.Message }
        }
        $codigo = [int]$respuesta.StatusCode
        $contenido = ''
        try {
            $lector = New-Object IO.StreamReader($respuesta.GetResponseStream())
            $contenido = $lector.ReadToEnd()
            $lector.Close()
        } catch { $contenido = '' }
        $mensaje = $contenido
        try { $mensaje = ($contenido | ConvertFrom-Json).error } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -v ON_ERROR_STOP=1 -U controllocal `
        -d $e2e.Database -t -A -c $consulta) -join "`n"
}

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$marca = "MOV$sufijo"
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$ayer = $hoyFecha.AddDays(-1).ToString('yyyy-MM-dd')
# +2 dias, no +1. El API corre en UTC dentro del contenedor y este script usa la
# hora LOCAL (UTC-5): a partir de las 19:00 locales, "manana local" ya es HOY en
# el servidor, la fecha deja de ser futura y el rechazo no se produce. Con dos
# dias el caso es inequivoco en cualquier huso y a cualquier hora.
$manana = $hoyFecha.AddDays(2).ToString('yyyy-MM-dd')
$fechaInicio = $hoyFecha.AddDays(15).ToString('yyyy-MM-dd')
$fechaFinEncargo = $hoyFecha.AddDays(90).ToString('yyyy-MM-dd')

# Cuerpo base de un movimiento valido; cada caso clona y cambia lo suyo.
function Movimiento($tipo, $monto) {
    @{ tipo = $tipo; monto = $monto; moneda = 'PEN'; fecha = $hoy
       formaPago = 'TRANSFERENCIA'; observacion = "Movimiento $marca" }
}

Write-Host "`n== 1. Contexto efimero y actores ==" -ForegroundColor Cyan
Check 'la base lleva identificador exclusivo de corrida' `
    ($e2e.Database -match '^controllocal_e2e_' -and $e2e.Database -ne 'controllocal') $e2e.Database
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol

Write-Host "`n== 2. Montaje: contrato cerrado con comision bruta de 1000 PEN ==" -ForegroundColor Cyan
$propietario = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "71$sufijo"
    nombre = "Propietario $marca"; telefono = '987610001'
    correo = "propietario.$marca@test.local"; consentimientoUsoDato = $true; estado = 'A'
}
$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "72$sufijo"
    nombre = "Cliente $marca"; telefono = '987610002'
    correo = "cliente.$marca@test.local"; rubroComercial = 'Retail'
    consentimientoContacto = $true; consentimientoUsoDato = $true; estado = 'A'
}
# Alta y encargo en un solo acto: `POST /locales` se retiro en el Corte 0A y
# `POST /propiedades` abre el encargo con la propiedad, porque un importe sin
# operacion declarada no dice si es precio de venta o renta.
$local = NuevoInmuebleConEncargo -Token $agente.token -Direccion "Av. $marca 200" `
    -IdPropietario $propietario.id -Importe 1000 -Moneda 'PEN' `
    -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1.00 -TratamientoIgv 'N' `
    -InicioEncargo $hoy -FinEncargo $fechaFinEncargo -Descripcion "Captacion $marca"
$captacion = Api POST "/captaciones/$($local.idEncargo)/decision" $broker.token @{
    accion = 'A'; observacion = "Captacion aprobada $marca"
}
$idCaptacion = [long]$captacion.id
$oportunidad = Api POST '/oportunidades' $agente.token @{
    idCliente = $cliente.id; idCaptacion = $idCaptacion; observaciones = "Oportunidad $marca"
}
$idOportunidad = [long]$oportunidad.id
Api POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad; canalContacto = 'W'; resultado = 'INTERESADO'
    observaciones = "Interaccion $marca"
} | Out-Null
$visita = Api POST '/visitas' $agente.token @{
    idOportunidad = $idOportunidad; fechaVisita = $ayer; horaVisita = '10:00'
    observaciones = "Visita $marca"
}
Api PATCH "/visitas/$($visita.id)/realizar" $agente.token $null | Out-Null
Api PATCH "/visitas/$($visita.id)/resultado" $agente.token @{
    resultado = 'INTERESADO'; observaciones = "Interes confirmado $marca"
    nivelInteres = 5; objecionPrincipal = 'O'; opinionPrecio = 'J'; proximaAccion = 'S'
} | Out-Null
$solicitud = Api POST '/solicitudes' $agente.token @{
    codigoSolicitud = "SOL-$marca"; fechaRegistro = $hoy; idOportunidad = $idOportunidad
    montoPropuesto = 1000; moneda = 'PEN'; plazoMeses = 12; fechaInicio = $fechaInicio
    formaPago = 'TRANSFERENCIA'; mesesGarantia = 2; mesesAdelanto = 1
    fechaVigenciaOferta = $hoyFecha.AddDays(10).ToString('yyyy-MM-dd')
    observaciones = "Solicitud $marca"
}
$idSolicitud = [long]$solicitud.id
Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null | Out-Null
Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; idSolicitud = $idSolicitud
    observaciones = "Solicitud aprobada $marca"
} | Out-Null
$contrato = Api POST '/contratos' $agente.token @{
    idSolicitud = $idSolicitud; fechaCierre = $hoy; estadoContrato = 'V'
    incidencias = "Contrato $marca"
}
$idContrato = [long]$contrato.id
Check 'la comision nace PENDIENTE con la bruta completa' `
    ($contrato.comisionEstado -eq 'P' -and $contrato.comisionGenerada -eq 1000) `
    "estado=$($contrato.comisionEstado) bruta=$($contrato.comisionGenerada)"
Check 'nace sin caja: nada cobrado y todo el bruto por cobrar' `
    ($contrato.montoCobrado -eq 0 -and $contrato.saldoCobro -eq 1000) `
    "cobrado=$($contrato.montoCobrado) saldo=$($contrato.saldoCobro)"
Check 'nace sin reparto: el broker no ha decidido todavia' `
    ($null -eq $contrato.montoAgente) "montoAgente=$($contrato.montoAgente)"

Write-Host "`n== 3. El gate es del BROKER, sin agente y sin admin ==" -ForegroundColor Cyan
$comoAgente = ApiError POST "/contratos/$idContrato/comision/movimientos" $agente.token (Movimiento 'C' 100)
Check 'el agente no registra movimientos (403)' ($comoAgente.codigo -eq 403) "codigo=$($comoAgente.codigo)"
$sinToken = ApiError POST "/contratos/$idContrato/comision/movimientos" $null (Movimiento 'C' 100)
Check 'sin sesion no se registra nada (401)' ($sinToken.codigo -eq 401) "codigo=$($sinToken.codigo)"
$contratoInexistente = ApiError POST '/contratos/99999999/comision/movimientos' $broker.token (Movimiento 'C' 100)
Check 'un contrato inexistente responde 404' ($contratoInexistente.codigo -eq 404) `
    "codigo=$($contratoInexistente.codigo)"

Write-Host "`n== 4. Validaciones del cuerpo ==" -ForegroundColor Cyan
$tipoMalo = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'X' 100)
Check 'un tipo de movimiento desconocido se rechaza' `
    ($tipoMalo.codigo -eq 400 -and $tipoMalo.error -eq 'Tipo de movimiento de comision invalido.') `
    "codigo=$($tipoMalo.codigo) error=$($tipoMalo.error)"
$montoCero = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 0)
Check 'un movimiento de importe cero se rechaza' `
    ($montoCero.codigo -eq 400 -and $montoCero.error -eq 'El monto del movimiento debe ser mayor que cero.') `
    "codigo=$($montoCero.codigo) error=$($montoCero.error)"
$montoNegativo = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' -50)
Check 'un movimiento de importe negativo se rechaza' `
    ($montoNegativo.codigo -eq 400 -and $montoNegativo.error -eq 'El monto del movimiento debe ser mayor que cero.') `
    "codigo=$($montoNegativo.codigo) error=$($montoNegativo.error)"
$otraMoneda = Movimiento 'C' 100
$otraMoneda['moneda'] = 'USD'
$monedaMala = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token $otraMoneda
Check 'la moneda del movimiento no puede diferir de la liquidacion' `
    ($monedaMala.codigo -eq 400 -and $monedaMala.error -eq 'La moneda del movimiento debe coincidir con la liquidacion.') `
    "codigo=$($monedaMala.codigo) error=$($monedaMala.error)"
$fechaFutura = Movimiento 'C' 100
$fechaFutura['fecha'] = $manana
$futura = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token $fechaFutura
Check 'un movimiento con fecha futura se rechaza' `
    ($futura.codigo -eq 400 -and $futura.error -match 'no puede ser futura') `
    "codigo=$($futura.codigo) error=$($futura.error)"
$pagoSinReparto = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'P' 100)
Check 'no se paga al agente antes de que el broker reparta' `
    ($pagoSinReparto.codigo -eq 400 -and $pagoSinReparto.error -eq 'Asigna primero la parte del agente.') `
    "codigo=$($pagoSinReparto.codigo) error=$($pagoSinReparto.error)"
Check 'ninguna validacion dejo rastro en comision_movimiento' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato") -eq '0') `
    'se persistio un movimiento rechazado'

Write-Host "`n== 5. Reparto y primer cobro parcial ==" -ForegroundColor Cyan
$repartida = Api POST "/contratos/$idContrato/comision/asignar" $broker.token @{ montoAgente = 400 }
Check 'el broker reparte 400 al agente y 600 quedan para la empresa' `
    ($repartida.montoAgente -eq 400 -and $repartida.montoEmpresa -eq 600) `
    "agente=$($repartida.montoAgente) empresa=$($repartida.montoEmpresa)"
Check 'repartir no mueve caja ni cambia el estado' `
    ($repartida.comisionEstado -eq 'P' -and $repartida.montoCobrado -eq 0) `
    "estado=$($repartida.comisionEstado) cobrado=$($repartida.montoCobrado)"

$parcial = Api POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 300)
Check 'un cobro parcial deriva el estado a PARCIAL' ($parcial.comisionEstado -eq 'R') $parcial.comisionEstado
Check 'el saldo baja exactamente lo cobrado' `
    ($parcial.montoCobrado -eq 300 -and $parcial.saldoCobro -eq 700) `
    "cobrado=$($parcial.montoCobrado) saldo=$($parcial.saldoCobro)"

Write-Host "`n== 6. Idempotencia: la clave distingue el reintento del segundo abono ==" -ForegroundColor Cyan
# Deduplicar por (tipo, monto, moneda, fecha) seria perder dinero: dos abonos
# de 300 el mismo dia son legitimos. La identidad la aporta el cliente con una
# clave por operacion, y el guardian real es el indice unico parcial
# `uq_movimiento_idempotencia`.
$claveAbono = [guid]::NewGuid().ToString()
$primerAbono = ApiIdem POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 300) $claveAbono
Check 'el segundo abono con clave nueva suma' `
    ($primerAbono.montoCobrado -eq 600 -and $primerAbono.saldoCobro -eq 400) `
    "cobrado=$($primerAbono.montoCobrado) saldo=$($primerAbono.saldoCobro)"

# (1) mismo Idempotency-Key reenviado -> una sola fila
$cobradoAntes = $primerAbono.montoCobrado
$reintento = ApiIdem POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 300) $claveAbono
Check 'reenviar la MISMA clave devuelve el resultado original, no cobra otra vez' `
    ($reintento.montoCobrado -eq $cobradoAntes -and $reintento.saldoCobro -eq 400) `
    "cobrado=$($reintento.montoCobrado) (antes $cobradoAntes)"
Check 'y no deja una segunda fila' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato and m.clave_idempotencia='$claveAbono'") -eq '1') `
    'el reintento duplico la fila'

# (3) misma clave con payload distinto -> conflicto
$conflicto = ApiIdemError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 50) $claveAbono
Check 'la misma clave con otro comando es 409, no un exito silencioso' `
    ($conflicto.codigo -eq 409 -and $conflicto.error -match 'clave de idempotencia') `
    "codigo=$($conflicto.codigo) error=$($conflicto.error)"
Check 'el conflicto no altero la caja' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato and m.tipo='C'") -eq '2') `
    'el 409 dejo una fila'

# (7) el KPI antes/despues demuestra que el reintento no duplico dinero
$filtroKpi = "texto=$marca&distrito=Barranco"
$kpiTrasReintento = Api GET "/contratos/resumen?$filtroKpi" $broker.token $null
$cobradoKpi = @($kpiTrasReintento.montosCobrados | Where-Object { $_.moneda -eq 'PEN' })
Check 'el KPI de cobrado refleja 600, no 900: el reintento no genero dinero' `
    ($cobradoKpi.Count -eq 1 -and $cobradoKpi[0].monto -eq 600) `
    ($kpiTrasReintento.montosCobrados | ConvertTo-Json -Compress)

Write-Host "`n== 6b. Carrera real: dos peticiones simultaneas con la misma clave ==" -ForegroundColor Cyan
# El indice unico es lo que corta la carrera; la lectura previa del service
# solo resuelve el caso normal. Se lanzan de verdad en paralelo.
$claveCarrera = [guid]::NewGuid().ToString()
$cuerpoCarrera = (Movimiento 'C' 100) | ConvertTo-Json -Depth 8
$guion = {
    param($uri, $token, $clave, $cuerpo)
    try {
        Invoke-RestMethod -Method POST -Uri $uri -TimeoutSec 45 -ContentType 'application/json' `
            -Headers @{ Authorization = "Bearer $token"; 'Idempotency-Key' = $clave } -Body $cuerpo | Out-Null
        return 'OK'
    } catch {
        return "ERR:$([int]$PSItem.Exception.Response.StatusCode)"
    }
}
$uriCarrera = "$base/contratos/$idContrato/comision/movimientos"
$trabajos = 1..2 | ForEach-Object {
    Start-Job -ScriptBlock $guion -ArgumentList $uriCarrera, $broker.token, $claveCarrera, $cuerpoCarrera
}
$resultadosCarrera = $trabajos | Wait-Job -Timeout 90 | Receive-Job
$trabajos | Remove-Job -Force
$filasCarrera = [int](Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato and m.clave_idempotencia='$claveCarrera'")
Check 'dos peticiones simultaneas con la misma clave dejan UNA sola fila' `
    ($filasCarrera -eq 1) "filas=$filasCarrera respuestas=$($resultadosCarrera -join ',')"
$trasCarrera = Api GET "/contratos/oportunidad/$idOportunidad" $broker.token $null
Check 'y la caja solo subio 100, no 200' `
    ($trasCarrera.montoCobrado -eq 700) "cobrado=$($trasCarrera.montoCobrado)"

# (4) dos operaciones legitimas iguales con claves distintas -> dos filas
Write-Host "`n== 6c. Dos abonos legitimos identicos con claves distintas ==" -ForegroundColor Cyan
$claveA = [guid]::NewGuid().ToString()
$claveB = [guid]::NewGuid().ToString()
ApiIdem POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 50) $claveA | Out-Null
$segundoLegitimo = ApiIdem POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 50) $claveB
Check 'dos comandos distintos con el mismo contenido SI suman los dos' `
    ($segundoLegitimo.montoCobrado -eq 800) "cobrado=$($segundoLegitimo.montoCobrado)"
Check 'y dejan dos filas, una por clave' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato and m.clave_idempotencia in ('$claveA','$claveB')") -eq '2') `
    'no se registraron los dos abonos'
Check 'un movimiento sin cabecera sigue admitiendose (contrato legado)' `
    ((Api POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 50)).montoCobrado -eq 850) `
    'la cabecera dejo de ser opcional'

Write-Host "`n== 7. Topes economicos sobre el saldo real ==" -ForegroundColor Cyan
# Saldo en este punto: 1000 - 850 = 150.
$excedeCobro = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 500)
Check 'no se cobra mas que el saldo pendiente' `
    ($excedeCobro.codigo -eq 400 -and $excedeCobro.error -eq 'El cobro no puede superar el saldo pendiente.') `
    "codigo=$($excedeCobro.codigo) error=$($excedeCobro.error)"
$excedeReversion = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'R' 900)
Check 'una reversion no puede devolver mas de lo cobrado' `
    ($excedeReversion.codigo -eq 400 -and $excedeReversion.error -eq 'La reversion no puede superar lo cobrado.') `
    "codigo=$($excedeReversion.codigo) error=$($excedeReversion.error)"

$revertido = Api POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'R' 250)
Check 'una reversion valida devuelve saldo al pendiente' `
    ($revertido.montoCobrado -eq 600 -and $revertido.saldoCobro -eq 400) `
    "cobrado=$($revertido.montoCobrado) saldo=$($revertido.saldoCobro)"
Check 'cobrado mas saldo sigue siendo la bruta' `
    (($revertido.montoCobrado + $revertido.saldoCobro) -eq 1000) `
    "$($revertido.montoCobrado)+$($revertido.saldoCobro)"

Write-Host "`n== 8. Pago al agente: pata independiente del cobro ==" -ForegroundColor Cyan
$pagado = Api POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'P' 400)
Check 'el pago al agente agota su parte sin tocar el cobro' `
    ($pagado.montoPagadoAgente -eq 400 -and $pagado.saldoPagoAgente -eq 0) `
    "pagado=$($pagado.montoPagadoAgente) saldo=$($pagado.saldoPagoAgente)"
Check 'pagar al agente no altera lo cobrado por la corredora' `
    ($pagado.montoCobrado -eq 600 -and $pagado.comisionEstado -eq 'R') `
    "cobrado=$($pagado.montoCobrado) estado=$($pagado.comisionEstado)"
$excedePago = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'P' 1)
Check 'no se paga al agente mas que su parte asignada' `
    ($excedePago.codigo -eq 400 -and $excedePago.error -eq 'El pago al agente no puede superar su saldo pendiente.') `
    "codigo=$($excedePago.codigo) error=$($excedePago.error)"

Write-Host "`n== 9. El ajuste NO es un comando monetario ==" -ForegroundColor Cyan
# El tipo 'A' nacio en V15 como una cuarta letra del CHECK y nunca tuvo regla:
# nada define que saldo modifica, con que signo, contra que tope, como afecta a
# P/R/C, como se revierte ni como entra en KPI. Tampoco existe en la v1. Se
# retira del comando; el CHECK de la base sigue admitiendolo para no perder
# filas historicas si las hubiera.
$ajuste = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'A' 50)
Check 'el ajuste se rechaza con 400 y un motivo explicito' `
    ($ajuste.codigo -eq 400 -and $ajuste.error -match 'no es una operacion monetaria') `
    "codigo=$($ajuste.codigo) error=$($ajuste.error)"
Check 'y no deja ninguna fila de tipo A' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato and m.tipo='A'") -eq '0') `
    'se persistio un ajuste'
Check 'el rechazo no toco ningun saldo' `
    ((Api GET "/contratos/oportunidad/$idOportunidad" $broker.token $null).montoCobrado -eq 600) `
    'el ajuste movio caja'

Write-Host "`n== 10. El estado COBRADA lo produce el saldo, no una eleccion ==" -ForegroundColor Cyan
$cerrada = Api POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 400)
Check 'agotar el saldo deriva el estado a COBRADA' ($cerrada.comisionEstado -eq 'C') $cerrada.comisionEstado
Check 'lo cobrado iguala la bruta y no queda saldo' `
    ($cerrada.montoCobrado -eq 1000 -and $cerrada.saldoCobro -eq 0) `
    "cobrado=$($cerrada.montoCobrado) saldo=$($cerrada.saldoCobro)"
$tras = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'C' 10)
Check 'una comision cobrada no admite mas cobros' `
    ($tras.codigo -eq 400 -and $tras.error -eq 'El cobro no puede superar el saldo pendiente.') `
    "codigo=$($tras.codigo) error=$($tras.error)"

# HALLAZGO ABIERTO: revertir sobre una comision ya COBRADA se rechaza, pero con
# el mensaje de la maquina de estados en vez de una regla de negocio. Significa
# ademas que un cobro registrado por error ya no se puede deshacer.
$revertirCobrada = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento 'R' 100)
Check 'revertir una comision cobrada se rechaza (con el mensaje de la maquina)' `
    ($revertirCobrada.codigo -eq 400 -and $revertirCobrada.error -match 'C -> R') `
    "codigo=$($revertirCobrada.codigo) error=$($revertirCobrada.error)"
Check 'ese rechazo revierte tambien el movimiento: no queda fila R de mas' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato and m.tipo='R'") -eq '1') `
    'la transaccion no revirtio el movimiento'

Write-Host "`n== 11. Evidencia persistida y auditoria ==" -ForegroundColor Cyan
$filas = Sql @"
select m.tipo || ':' || m.monto::numeric(14,0) || ':' || m.rol_usuario
  from comision_movimiento m
  join comision_liquidacion l on l.id_comision_liquidacion = m.id_comision_liquidacion
 where l.id_contrato_alquiler = $idContrato
 order by m.id_comision_movimiento
"@
$esperadas = @('C:300:BROKER', 'C:300:BROKER', 'C:100:BROKER', 'C:50:BROKER',
               'C:50:BROKER', 'C:50:BROKER', 'R:250:BROKER', 'P:400:BROKER',
               'C:400:BROKER') -join "`n"
Check 'los nueve movimientos quedan en orden, con importe y rol del actor' `
    ($filas -eq $esperadas) "obtenido=[$filas]"
Check 'ni el reintento, ni la carrera, ni el 409, ni el ajuste dejaron fila de mas' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato") -eq '9') `
    'sobran o faltan movimientos'
Check 'todos heredan el tenant de la liquidacion' `
    ((Sql "select count(distinct m.organizacion_id) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato") -eq '1') `
    'tenant inconsistente'
# historial_estado usa id_entidad (tarea usa entidad_id): comprobado contra el
# DDL vigente antes de escribir esta consulta.
$transiciones = Sql @"
select string_agg(coalesce(h.estado_anterior,'-') || '>' || h.estado_nuevo, ',' order by h.id_historial)
  from historial_estado h
 where h.entidad_tipo = 'COMISION_LIQUIDACION'
   and h.id_entidad = (select id_comision_liquidacion from comision_liquidacion
                        where id_contrato_alquiler = $idContrato)
"@
Check 'solo se auditan los cambios REALES de estado: P>R y R>C' `
    ($transiciones -eq 'P>R,R>C') "transiciones=[$transiciones]"

Write-Host "`n== 12. El KPI mira el universo filtrado, no la pagina ==" -ForegroundColor Cyan
$filtro = "texto=$marca&distrito=Barranco"
$resumen = Api GET "/contratos/resumen?$filtro" $broker.token $null
$generadaPEN = @($resumen.comisionesGeneradas | Where-Object { $_.moneda -eq 'PEN' })
$cobradaPEN = @($resumen.montosCobrados | Where-Object { $_.moneda -eq 'PEN' })
$pagadaPEN = @($resumen.montosPagadosAgente | Where-Object { $_.moneda -eq 'PEN' })
Check 'el KPI de generado sigue siendo la bruta' `
    ($generadaPEN.Count -eq 1 -and $generadaPEN[0].monto -eq 1000) `
    ($resumen.comisionesGeneradas | ConvertTo-Json -Compress)
Check 'el KPI de cobrado sale de los movimientos: cobros menos reversiones' `
    ($cobradaPEN.Count -eq 1 -and $cobradaPEN[0].monto -eq 1000) `
    ($resumen.montosCobrados | ConvertTo-Json -Compress)
Check 'el KPI de pagado al agente sale de los movimientos P' `
    ($pagadaPEN.Count -eq 1 -and $pagadaPEN[0].monto -eq 400) `
    ($resumen.montosPagadosAgente | ConvertTo-Json -Compress)
$tabla = Api GET "/contratos?$filtro&pagina=1&tamano=10" $broker.token $null
Check 'tabla y resumen parten del mismo conjunto de candidatos' `
    ($tabla.totalRecords -eq 1 -and $resumen.cierres -eq $tabla.totalRecords) `
    "tabla=$($tabla.totalRecords) resumen=$($resumen.cierres)"

Write-Host "`n== 13. Una comision anulada no admite ningun movimiento ==" -ForegroundColor Cyan
# La comision quedo COBRADA y desde ahi ya no se puede anular por el cable
# -que es justo lo que protege el ciclo-, asi que el estado A se fuerza en la
# base para poder ejercitar la guarda del endpoint. Es una manipulacion
# deliberada del fixture, no un camino de negocio.
Sql "update comision_liquidacion set estado='A' where id_contrato_alquiler=$idContrato" | Out-Null
foreach ($tipo in @('C', 'P', 'A', 'R')) {
    $enAnulada = ApiError POST "/contratos/$idContrato/comision/movimientos" $broker.token (Movimiento $tipo 10)
    Check "una comision anulada rechaza el movimiento $tipo" `
        ($enAnulada.codigo -eq 400 -and $enAnulada.error -eq 'Una comision anulada no admite movimientos.') `
        "codigo=$($enAnulada.codigo) error=$($enAnulada.error)"
}
Check 'la anulacion no borro la evidencia economica previa' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato") -eq '9') `
    'se perdieron movimientos'

Write-Host "`n== 14. Comision bruta CERO: respuesta controlada, nunca 500 ==" -ForegroundColor Cyan
# Una captacion sin comision es legal (`valor_comision = 0` con motivo expreso,
# `ck_condicion_sin_comision`) y produce una liquidacion de bruto 0. Cobrarla
# emitia un movimiento de importe 0 que `ck_movimiento_monto CHECK (monto > 0)`
# rechazaba, y eso salia por el cable como un 500. El constraint tiene razon:
# un movimiento de cero no es evidencia de nada. La decision se toma en negocio.
$marca0 = "CERO$sufijo"
$propietario0 = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "73$sufijo"
    nombre = "Propietario $marca0"; telefono = '987610003'
    correo = "propietario.$marca0@test.local"; consentimientoUsoDato = $true; estado = 'A'
}
$cliente0 = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "74$sufijo"
    nombre = "Cliente $marca0"; telefono = '987610004'
    correo = "cliente.$marca0@test.local"; rubroComercial = 'Retail'
    consentimientoContacto = $true; consentimientoUsoDato = $true; estado = 'A'
}
$local0 = NuevoInmuebleConEncargo -Token $agente.token -Direccion "Av. $marca0 300" `
    -Distrito 'Surco' -IdPropietario $propietario0.id -Metraje 60 `
    -Importe 900 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 0 `
    -TratamientoIgv 'N' -InicioEncargo $hoy -FinEncargo $fechaFinEncargo `
    -Descripcion "Sin comision $marca0"
$captacion0 = Api POST "/captaciones/$($local0.idEncargo)/decision" $broker.token @{
    accion = 'A'; observacion = "Sin comision aprobada $marca0"
}
$oportunidad0 = Api POST '/oportunidades' $agente.token @{
    idCliente = $cliente0.id; idCaptacion = [long]$captacion0.id; observaciones = "Oportunidad $marca0"
}
$idOportunidad0 = [long]$oportunidad0.id
Api POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad0; canalContacto = 'W'; resultado = 'INTERESADO'
    observaciones = "Interaccion $marca0"
} | Out-Null
$visita0 = Api POST '/visitas' $agente.token @{
    idOportunidad = $idOportunidad0; fechaVisita = $ayer; horaVisita = '12:00'
    observaciones = "Visita $marca0"
}
Api PATCH "/visitas/$($visita0.id)/realizar" $agente.token $null | Out-Null
Api PATCH "/visitas/$($visita0.id)/resultado" $agente.token @{
    resultado = 'INTERESADO'; observaciones = "Interes $marca0"
    nivelInteres = 5; objecionPrincipal = 'O'; opinionPrecio = 'J'; proximaAccion = 'S'
} | Out-Null
$solicitud0 = Api POST '/solicitudes' $agente.token @{
    codigoSolicitud = "SOL-$marca0"; fechaRegistro = $hoy; idOportunidad = $idOportunidad0
    montoPropuesto = 900; moneda = 'PEN'; plazoMeses = 12; fechaInicio = $fechaInicio
    formaPago = 'TRANSFERENCIA'; mesesGarantia = 2; mesesAdelanto = 1
    fechaVigenciaOferta = $hoyFecha.AddDays(10).ToString('yyyy-MM-dd')
    observaciones = "Solicitud $marca0"
}
Api POST "/solicitudes/$([long]$solicitud0.id)/reenviar" $agente.token $null | Out-Null
Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; idSolicitud = [long]$solicitud0.id
    observaciones = "Aprobada $marca0"
} | Out-Null
$contrato0 = Api POST '/contratos' $agente.token @{
    idSolicitud = [long]$solicitud0.id; fechaCierre = $hoy; estadoContrato = 'V'
    incidencias = "Contrato $marca0"
}
$idContrato0 = [long]$contrato0.id
Check 'una captacion sin comision genera una liquidacion de bruto 0' `
    ($contrato0.comisionGenerada -eq 0 -and $contrato0.comisionEstado -eq 'P') `
    "bruta=$($contrato0.comisionGenerada) estado=$($contrato0.comisionEstado)"

Api POST "/contratos/$idContrato0/comision/asignar" $broker.token @{ montoAgente = 0 } | Out-Null
$movimientosAntes = [int](Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato0")
$cobroCero = Api POST "/contratos/$idContrato0/comision/cobro" $broker.token @{
    estado = 'C'; fechaCobro = $hoy; formaPago = 'TRANSFERENCIA'
}
Check 'cobrar una comision de bruto 0 responde 200, no 500' `
    ($cobroCero.comisionEstado -eq 'C') "estado=$($cobroCero.comisionEstado)"
Check 'y no escribe ningun movimiento de importe cero' `
    ([int](Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato0") -eq $movimientosAntes) `
    'se creo un movimiento'
Check 'los saldos quedan a cero y cuadran' `
    ($cobroCero.montoCobrado -eq 0 -and $cobroCero.saldoCobro -eq 0) `
    "cobrado=$($cobroCero.montoCobrado) saldo=$($cobroCero.saldoCobro)"
Check 'la transaccion no dejo filas parciales: cero movimientos para esa liquidacion' `
    ((Sql "select count(*) from comision_movimiento m join comision_liquidacion l on l.id_comision_liquidacion=m.id_comision_liquidacion where l.id_contrato_alquiler=$idContrato0") -eq '0') `
    'quedaron filas'
Check 'el historial si registra la transicion P>C' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='COMISION_LIQUIDACION' and estado_nuevo='C' and id_entidad=(select id_comision_liquidacion from comision_liquidacion where id_contrato_alquiler=$idContrato0)") -eq '1') `
    'no se audito el cierre'

Write-Host "`n== Resultado: $ok OK, $fail fallas ==" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
