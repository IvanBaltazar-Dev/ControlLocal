# Verificacion transversal y aislada del dominio economico de alquiler.
# No es una vertical nueva ni una suite acumulativa: se ejecuta solo mediante
# Invoke-E2E.ps1, sobre un PostgreSQL efimero recreado por Flyway.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
. "$PSScriptRoot/lib-publicaciones-c6.ps1"
. "$PSScriptRoot/lib-financiacion-b0-test.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
$null = AsegurarPlanEmpresarialB0DePrueba -Organizacion $fixtureE2E.organizacion -Codigo $fixtureE2E.codigo
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
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
        $contenido = LeerErrorHttpDeFixture $PSItem
        $mensaje = 'Respuesta HTTP sin mensaje JSON legible.'
        try { $jsonError = $contenido | ConvertFrom-Json; if($jsonError.error -is [string]){$mensaje=$jsonError.error} } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
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
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
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
# El alta universal crea el inmueble y abre su encargo: `POST /locales` se
# retiro en el Corte 0A y el importe no viaja sin su operacion.
$local = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token -Direccion "Av. $marca 100" `
    -Distrito 'Miraflores' -IdPropietario $propietario.id -Metraje 120 `
    -Importe 7200 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1.00 `
    -TratamientoIgv 'N' -InicioEncargo $hoy -FinEncargo $fechaFinEncargo `
    -Descripcion "Estabilizacion economica $marca"
$encargoAntes=Api GET "/encargos/$($local.idEncargo)" $agente.token $null
$imposible=$encargoAntes.condiciones
$imposible.tipoComision='F';$imposible.baseCalculo='R'
$rechazoApi=Invoke-ComandoC2DePrueba PUT "/encargos/$($local.idEncargo)/condiciones" $agente.token @{versionEsperada=$encargoAntes.version;condiciones=$imposible;motivo='Combinación inválida TEST F/R'} ('e2e-condicion-invalida-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'el backend rechaza una combinacion tipo/base invalida' `
    ($rechazoApi.codigo -eq 400) `
    "codigo=$($rechazoApi.codigo) error=$($rechazoApi.error)"

# La condición inválida se prueba en la puerta PUT vigente; no se reabre
# el alta POST /captaciones retirada ni se crea otro Encargo.
$null = HabilitarEncargoC2DePrueba -IdEncargo $($local.idEncargo) -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato' -EjecucionComision @{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER'}}
$captacion = Api GET "/captaciones/$($local.idEncargo)" $agente.token $null
# El anuncio, explicito. `POST /locales` lo creaba de rebote con
# `estadoPublicacion = 'P'`; el alta universal no publica -lo dice su propia
# documentacion- porque publicar es una decision, no un efecto secundario de
# registrar. La cascada del contrato tiene que encontrar un anuncio VIVO para
# poder cerrarlo, que es justo lo que esta suite comprueba.
$anuncio = PublicarAnuncioC6DePrueba -IdEncargo $local.idEncargo -Token $agente.token `
    -Canal 'URBANIA' -Importe 7200 -Moneda 'PEN' -Titulo "Anuncio $marca"
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
    ((Invoke-ControlLocalE2ESql "select tipo_comision||'|'||base_calculo||'|'||valor_comision||'|'||moneda_comision from condicion_economica_captacion where id_condicion_economica=(select id_condicion_economica from captacion where id_captacion=$idCaptacion)") -eq 'E|R|1.0000|PEN') 'condicion alterada'

Write-Host "`n== 3. Cascada completa mediante POST /contratos ==" -ForegroundColor Cyan
$oportunidad = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $cliente.id -Token $agente.token `
    -EvidenciaInteres 'Manifestacion expresa FICTICIA de interes del caso TEST E2E C4.' -Observaciones "Oportunidad $marca"
$idOportunidad = [long]$oportunidad.id
# C4: el contrato economico se monta por sus casos propios, sin atribuir
# interes comercial ni un cambio de etapa a una comunicacion ficticia.
$visita = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $fechaVisita -Hora '11:00' -Token $agente.token -Observaciones "Visita $marca"
ResultadoVisitaC4DePrueba -IdVisita $($visita.id) -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $hoy -Desenlace @{
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
$preparado=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$idSolicitud;incidencias="Preparación TEST $marca"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
Check 'preparar no firma ni cobra' ($preparado.estadoContrato -eq 'P' -and $null -eq $preparado.fechaCierre) 'preparación'
$formalizado=FormalizarAlquilerC5DePrueba -IdEncargo $idCaptacion -IdOportunidad $idOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 7000 -Moneda 'PEN' -Inicio $preparado.fechaInicioContrato -Fin $preparado.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $preparado.id
$contrato=$formalizado.contrato
$comision=LeerComisionC5DePrueba $formalizado $broker.token
$idContrato = [long]$contrato.id

Check 'una mensualidad E genera exactamente la renta contractual' `
    ($contrato.rentaMensual -eq 7000 -and $comision.calculo.cuantia.importe -eq 7000) `
    "renta=$($contrato.rentaMensual) comision=$($comision.calculo.cuantia.importe)"
Check 'renta y comision conservan PEN' `
    ($contrato.moneda -eq 'PEN' -and $comision.calculo.cuantia.moneda -eq 'PEN') `
    "$($contrato.moneda)|$($comision.calculo.cuantia.moneda)"
Check 'contrato firmado conserva su fecha y estado efectivo' ($null -ne $contrato.fechaCierre -and $contrato.estadoContrato -in @('D','V')) $contrato.estadoContrato
$cascada = Invoke-ControlLocalE2ESql @"
select p.estado_registro || '|' || p.disponibilidad_comercial || '|' ||
       c.estado || '|' || c.motivo_cierre || '|' || o.estado || '|' || s.estado || '|' ||
       co.estado_contrato || '|' || coalesce(cl.estado,'SIN_MAQUINA') || '|' || cl.moneda || '|' ||
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
    ($cascada -eq "A|A|C|A|F|C|$($contrato.estadoContrato)|SIN_MAQUINA|PEN|1|1") $cascada

Write-Host "`n== 4. KPI, filtros y contratos sin liquidacion ==" -ForegroundColor Cyan
$idAgente = [long]$agente.idDominio
$filtro = "texto=$marca&distrito=Miraflores&idAgente=$idAgente"
$tabla = Api GET "/contratos?$filtro&pagina=1&tamano=10" $broker.token $null
$resumen = Api GET "/contratos/resumen?$filtro" $broker.token $null
$importePEN = @($resumen.calculado | Where-Object { $_.moneda -eq 'PEN' })
Check 'tabla y resumen aplican exactamente los mismos filtros' `
    ($tabla.totalRecords -eq 1 -and $resumen.cierres -eq $tabla.totalRecords) `
    "tabla=$($tabla.totalRecords) resumen=$($resumen.cierres)"
Check 'el KPI suma la comision vigente por moneda' `
    ($importePEN.Count -eq 1 -and $importePEN[0].monto -eq 7000) `
    ($resumen.calculado | ConvertTo-Json -Compress)
Check 'la base final tiene evaluación suficiente' ($resumen.sinEvaluacion -eq 0) $resumen.sinEvaluacion

# La cobertura sin evaluación se prueba con historia ctxNULL en PG166, sin borrar
# un registro actual append-only para fabricar una ausencia.
$concluir=Api POST '/comisiones/conclusiones/propuestas' $broker.token @{comision=$comision.id;versionEsperada=$comision.version;motivo='Gestión concluida TEST sin modificar pacto'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $concluir
$posterior=Api GET "/contratos/resumen?$filtro" $broker.token $null
Check 'concluir gestión no elimina cálculo ni fabrica ingreso' `
    (@($posterior.calculado|Where-Object{$_.moneda -eq 'PEN' -and $_.monto -eq 7000}).Count -eq 1 -and @($posterior.ingresosRealmenteRecibidos).Count -eq 0) 'gestión/ingreso'
Check 'concluir no elimina contrato ni sus antecedentes' `
    ((Api GET "/contratos/oportunidad/$idOportunidad" $broker.token $null).id -eq $idContrato) 'historia'

Write-Host "`n== Resultado: $ok OK, $fail fallas ==" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
