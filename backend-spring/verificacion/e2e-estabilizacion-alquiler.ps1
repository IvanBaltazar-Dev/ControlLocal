# Verificacion transversal y aislada del dominio economico de alquiler.
# No es una vertical nueva ni una suite acumulativa: se ejecuta solo mediante
# Invoke-E2E.ps1, sobre un PostgreSQL efimero recreado por Flyway.
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
$marca = "STAB$sufijo"
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$fechaVisita = $hoyFecha.AddDays(-1).ToString('yyyy-MM-dd')
$fechaInicio = $hoyFecha.AddDays(15).ToString('yyyy-MM-dd')
$fechaFinEncargo = $hoyFecha.AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Contexto efimero y actores ==" -ForegroundColor Cyan
Check 'la base lleva identificador exclusivo de corrida' `
    ($e2e.Database -match '^controllocal_e2e_' -and $e2e.Database -ne 'controllocal') $e2e.Database
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol

Write-Host "`n== 2. Condicion economica explicita y moneda obligatoria ==" -ForegroundColor Cyan
$propietario = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "91$sufijo"
    nombre = "Propietario $marca"; telefono = '987510001'
    correo = "propietario.$marca@test.local"; consentimientoUsoDato = $true; estado = 'A'
}
$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "92$sufijo"
    nombre = "Cliente $marca"; telefono = '987510002'
    correo = "cliente.$marca@test.local"; rubroComercial = 'Retail'
    consentimientoContacto = $true; consentimientoUsoDato = $true; estado = 'A'
}
$local = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-$marca"; direccion = "Av. $marca 100"
    distrito = 'Miraflores'; metraje = 120; precioReferencial = 7200
    monedaReferencial = 'PEN'; rubroPermitido = 'Retail'; idPropietario = $propietario.id
    estado = 'D'; estadoPublicacion = 'P'
}
$captacionBase = @{
    codigoCaptacion = "CAP-$marca"; fechaCaptacion = $hoy; fechaInicioVigencia = $hoy
    fechaFinVigencia = $fechaFinEncargo
    observaciones = "Estabilizacion economica $marca"; idLocal = $local.id
    idAgente = $agente.idDominio; motivoOperacion = 'A'; urgencia = 3; exclusividad = $true
    tipoOperacion = 'A'; importeReferencia = 7200; monedaReferencia = 'PEN'
    tipoComision = 'E'; baseCalculo = 'R'; valorComision = 1.00
    monedaComision = 'PEN'; tratamientoIgv = 'N'
}
$captacionImposible = $captacionBase.Clone()
$captacionImposible['codigoCaptacion'] = "CAP-BAD-$marca"
$captacionImposible['tipoComision'] = 'F'
$captacionImposible['baseCalculo'] = 'R'
$rechazoApi = ApiError POST '/captaciones' $agente.token $captacionImposible
Check 'el backend rechaza una combinacion tipo/base invalida' `
    ($rechazoApi.codigo -eq 400 -and $rechazoApi.error -match 'tipo y base') `
    "codigo=$($rechazoApi.codigo) error=$($rechazoApi.error)"

$captacion = Api POST '/captaciones' $agente.token $captacionBase
$captacion = Api POST "/captaciones/$($captacion.id)/decision" $broker.token @{
    accion = 'A'; observacion = "Porcentaje confirmado $marca"
}
$idCaptacion = [long]$captacion.id
$preferenciaErrores = $ErrorActionPreference
try {
    # El stderr de PostgreSQL es el resultado esperado de esta comprobacion.
    # PowerShell 5.1 no debe convertirlo en una excepcion antes de afirmar el
    # codigo de salida y comprobar que el valor original quedo intacto.
    $ErrorActionPreference = 'Continue'
    $salidaSqlInvalido = docker exec $e2e.PostgresContainer psql -v ON_ERROR_STOP=1 `
        -U controllocal -d $e2e.Database -t -A `
        -c "update condicion_economica_captacion set base_calculo='V' where id_condicion_economica=(select id_condicion_economica from captacion where id_captacion=$idCaptacion)" 2>&1
    $codigoSqlInvalido = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $preferenciaErrores
}
Check 'la base de datos tambien rechaza la combinacion tipo/base invalida' ($codigoSqlInvalido -ne 0) ($salidaSqlInvalido -join ' ')
Check 'el rechazo SQL conserva E/R/1.00 y su moneda' `
    ((Sql "select tipo_comision||'|'||base_calculo||'|'||valor_comision||'|'||moneda_comision from condicion_economica_captacion where id_condicion_economica=(select id_condicion_economica from captacion where id_captacion=$idCaptacion)") -eq 'E|R|1.0000|PEN') 'condicion alterada'

Write-Host "`n== 3. Cascada completa mediante POST /contratos ==" -ForegroundColor Cyan
$oportunidad = Api POST '/oportunidades' $agente.token @{
    idCliente = $cliente.id; idCaptacion = $idCaptacion; observaciones = "Oportunidad $marca"
}
$idOportunidad = [long]$oportunidad.id
Api POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad; canalContacto = 'W'; resultado = 'INTERESADO'
    observaciones = "Interaccion $marca"
} | Out-Null
$visita = Api POST '/visitas' $agente.token @{
    idOportunidad = $idOportunidad; fechaVisita = $fechaVisita; horaVisita = '11:00'
    observaciones = "Visita $marca"
}
Api PATCH "/visitas/$($visita.id)/realizar" $agente.token $null | Out-Null
Api PATCH "/visitas/$($visita.id)/resultado" $agente.token @{
    resultado = 'INTERESADO'; observaciones = "Interes confirmado $marca"
    nivelInteres = 5; objecionPrincipal = 'O'; opinionPrecio = 'J'; proximaAccion = 'S'
} | Out-Null
$solicitud = Api POST '/solicitudes' $agente.token @{
    codigoSolicitud = "SOL-$marca"; fechaRegistro = $hoy; idOportunidad = $idOportunidad
    montoPropuesto = 7000; moneda = 'PEN'; plazoMeses = 24; fechaInicio = $fechaInicio
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

Check '100 % genera exactamente una renta mensual' `
    ($contrato.rentaMensual -eq 7000 -and $contrato.comisionGenerada -eq 7000) `
    "renta=$($contrato.rentaMensual) comision=$($contrato.comisionGenerada)"
Check 'renta y comision conservan PEN' `
    ($contrato.moneda -eq 'PEN' -and $contrato.monedaComision -eq 'PEN') `
    "$($contrato.moneda)|$($contrato.monedaComision)"
Check 'el contrato conserva el estado juridico Vigente' ($contrato.estadoContrato -eq 'V') $contrato.estadoContrato
$cascada = Sql @"
select p.estado_registro || '|' || p.disponibilidad_comercial || '|' ||
       c.estado || '|' || c.motivo_cierre || '|' || o.estado || '|' || s.estado || '|' ||
       co.estado_contrato || '|' || cl.estado || '|' || cl.moneda || '|' ||
       (select count(*) from publicacion pub where pub.id_propiedad=p.id_propiedad and pub.estado='C') || '|' ||
       (select count(*) from precio_propiedad pr where pr.id_propiedad=p.id_propiedad and pr.hito='C' and pr.moneda='PEN' and pr.monto=7000)
  from propiedad p
  join captacion c on c.id_propiedad=p.id_propiedad and c.id_captacion=$idCaptacion
  join oportunidad_comercial o on o.id_captacion=c.id_captacion and o.id_oportunidad=$idOportunidad
  join solicitud_alquiler s on s.id_oportunidad=o.id_oportunidad and s.id_solicitud=$idSolicitud
  join contrato_alquiler co on co.id_solicitud=s.id_solicitud and co.id_contrato_alquiler=$idContrato
  left join comision_liquidacion cl on cl.id_contrato_alquiler=co.id_contrato_alquiler
 where p.id_propiedad=$($local.id)
"@
Check 'la cascada alquila el local y cierra captacion por alquiler, oportunidad, solicitud y publicacion' `
    ($cascada -eq 'A|A|C|A|F|C|V|P|PEN|1|1') $cascada

Write-Host "`n== 4. KPI, filtros y contratos sin liquidacion ==" -ForegroundColor Cyan
$idAgente = [long]$agente.idDominio
$filtro = "texto=$marca&distrito=Miraflores&idAgente=$idAgente"
$tabla = Api GET "/contratos?$filtro&pagina=1&tamano=10" $broker.token $null
$resumen = Api GET "/contratos/resumen?$filtro" $broker.token $null
$importePEN = @($resumen.comisionesGeneradas | Where-Object { $_.moneda -eq 'PEN' })
Check 'tabla y resumen aplican exactamente los mismos filtros' `
    ($tabla.totalRecords -eq 1 -and $resumen.cierres -eq $tabla.totalRecords) `
    "tabla=$($tabla.totalRecords) resumen=$($resumen.cierres)"
Check 'el KPI suma la comision vigente por moneda' `
    ($importePEN.Count -eq 1 -and $importePEN[0].monto -eq 7000) `
    ($resumen.comisionesGeneradas | ConvertTo-Json -Compress)
Check 'sin anomalía la comprobacion sin liquidacion es cero' ($resumen.sinLiquidacion -eq 0) $resumen.sinLiquidacion

Api POST "/contratos/$idContrato/comision/cobro" $broker.token @{ estado = 'A' } | Out-Null
$resumenAnulado = Api GET "/contratos/resumen?$filtro" $broker.token $null
Check 'Comision generada excluye liquidaciones anuladas' `
    (@($resumenAnulado.comisionesGeneradas).Count -eq 0) `
    ($resumenAnulado.comisionesGeneradas | ConvertTo-Json -Compress)
Check 'anular el cobro no cambia el estado juridico del contrato' `
    ((Api GET "/contratos/oportunidad/$idOportunidad" $agente.token $null).estadoContrato -eq 'V') 'contrato alterado'

Sql "delete from comision_liquidacion where id_contrato_alquiler=$idContrato" | Out-Null
$resumenSinLiquidacion = Api GET "/contratos/resumen?$filtro" $broker.token $null
$tablaSinLiquidacion = Api GET "/contratos?$filtro&pagina=1&tamano=10" $broker.token $null
Check 'un contrato sin liquidacion sigue en la tabla' ($tablaSinLiquidacion.totalRecords -eq 1) $tablaSinLiquidacion.totalRecords
Check 'el resumen detecta el contrato sin liquidacion' ($resumenSinLiquidacion.sinLiquidacion -eq 1) $resumenSinLiquidacion.sinLiquidacion
Check 'tabla y resumen siguen iguales tras la anomalía' `
    ($tablaSinLiquidacion.totalRecords -eq $resumenSinLiquidacion.cierres) `
    "$($tablaSinLiquidacion.totalRecords)|$($resumenSinLiquidacion.cierres)"

Write-Host "`n== Resultado: $ok OK, $fail fallas ==" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
