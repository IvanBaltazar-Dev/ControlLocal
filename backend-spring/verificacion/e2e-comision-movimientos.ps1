# C5 HTTP real: pacto, hito, cobro, gestión y privacidad. Todo dato/evidencia es TEST.
# Sustituye el productor antiguo C/P/R y las marcas de estado que fingían dinero.
$ErrorActionPreference='Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e=Assert-ControlLocalE2EContext
$base=$e2e.BaseUrl
$fixtureE2E=Initialize-ControlLocalE2EActors
$ok=0;$fail=0
function Check($nombre,$condicion,$detalle){
    if($condicion){$script:ok++;Write-Host "  OK   $nombre" -ForegroundColor Green}
    else{$script:fail++;Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red}
}
function Api($metodo,$ruta,$token,$cuerpo){
    $headers=@{};if($token){$headers.Authorization="Bearer $token"}
    $p=@{Method=$metodo;Uri="$base$ruta";Headers=$headers;TimeoutSec=90}
    if($null -ne $cuerpo){$p.Body=$cuerpo|ConvertTo-Json -Depth 24;$p.ContentType='application/json'}
    try{Write-ControlLocalE2EHttpResult (Invoke-RestMethod @p)}
    catch{MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo;throw}
}
function ApiError($metodo,$ruta,$token,$cuerpo){
    try{Api $metodo $ruta $token $cuerpo|Out-Null;return @{codigo=0;error='No falló'}}
    catch{
        if($null -eq $_.Exception.Response){throw}
        return @{codigo=[int]$_.Exception.Response.StatusCode;error=(LeerErrorHttpDeFixture $_)}
    }
}
function IngresoPen($f){
    $importes=@($f.cobro.ingresosRecibidos|Where-Object {$_.moneda -eq 'PEN'})
    if($importes.Count -eq 0){return [decimal]0}
    if($importes.Count -ne 1 -or $null -eq $importes[0].importe){throw 'Fondos sin moneda/importe único conocido.'}
    return [decimal]$importes[0].importe
}
$script:FuenteFixtureE2E='e2e-comision-movimientos.ps1'
$script:EtapaFixtureE2E='C5_IDENTIDAD'
$hoyFecha=(Get-Date).Date;$hoy=$hoyFecha.ToString('yyyy-MM-dd')
$fin=$hoyFecha.AddMonths(12).ToString('yyyy-MM-dd')
$marca='C5-'+[guid]::NewGuid().ToString('N').Substring(0,10)
$sufijo=[string](Get-Random -Minimum 1000000 -Maximum 9999999)
$agente=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$gobierno=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'base propia identificada' ($e2e.Database -match '^controllocal_e2e_' -and $e2e.Database -ne 'controllocal') $e2e.Database
$propietario=Api POST '/propietarios' $agente.token @{tipoPersona='N';tipoDocumento='D';numeroDocumento="7$sufijo";nombre="Propietario TEST $marca";telefono='987610001';correo="$marca.prop@example.invalid";consentimientoUsoDato=$true;estado='A'}
$cliente=Api POST '/clientes' $agente.token @{tipoPersona='N';tipoDocumento='D';numeroDocumento="8$sufijo";nombre="Cliente TEST $marca";telefono='987610002';correo="$marca.cli@example.invalid";rubroComercial='Retail';consentimientoContacto=$true;consentimientoUsoDato=$true;estado='A'}
$local=NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token -Direccion "Av. TEST $marca 200" -IdPropietario $propietario.id -Importe 1000 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' -InicioEncargo $hoy -FinEncargo $fin
$ejecucion=@{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER';fechaExpresa=$null;cantidad=$null;unidadExpresa=$null;computoExpreso=$null}}
$null=HabilitarEncargoC2DePrueba -IdEncargo $local.idEncargo -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'CONTRATO_ALQUILER' -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'Al contrato de alquiler confirmado TEST' -EjecucionComision $ejecucion
$mesa=NuevaOportunidadC4DePrueba -IdEncargo $local.idEncargo -IdCliente $cliente.id -Token $agente.token -EvidenciaInteres 'Interés explícito ficticio TEST C5'
$script:EtapaFixtureE2E='C5_HITO_REAL'
$ctx=FormalizarAlquilerC5DePrueba -IdEncargo $local.idEncargo -IdOportunidad $mesa.id -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 1000 -Moneda 'PEN' -Inicio $hoy -Fin $fin -FechaFirma $hoy
$f=LeerComisionC5DePrueba $ctx $broker.token
Check 'hito confirmado produce contrato; no crea Solicitud ficticia' ($ctx.contrato.id -gt 0 -and $null -eq $ctx.contrato.idSolicitud) 'contrato/suficiencia'
Check 'regla E usa renta contractual, conocida en PEN' ($f.calculo.cuantia.importe -eq 1000 -and $f.calculo.cuantia.moneda -eq 'PEN') 'base/moneda'
Check 'devengo acreditado separado de recepción' ($f.devengo.estado -eq 'DEVENGADO' -and (IngresoPen $f) -eq 0 -and $f.cobro.estado -eq 'SIN_COBROS') 'devengo/cobro'
Check 'pago único no crea tramos sintéticos' (@($f.tramos).Count -eq 0) 'tramos'
$ruta="/comisiones?encargo=$($ctx.pacto.encargo)&revisionPacto=$($ctx.pacto.revisionAprobatoria)&contexto=$($ctx.expediente.id)"
Check 'gobernar no concede consulta financiera' ((ApiError GET $ruta $gobierno.token $null).codigo -eq 403) 'TenantAdmin'
$otroBanco=Initialize-ControlLocalE2EActors -Etiqueta 'otro'
$otroBroker=Connect-ControlLocalE2E -BaseUrl $base -Correo $otroBanco.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $otroBanco.organizacion -Rol 'BROKER' -EnrolarSiempre
Check 'otra organización no obtiene comisión' ((ApiError GET $ruta $otroBroker.token $null).codigo -in @(403,404)) 'aislamiento'
$asignacion=Api POST '/comisiones/repartos/propuestas' $broker.token @{comision=$f.id;versionEsperada=$f.version;montoAgente=400;observacion='Reparto elegido TEST; no es pago ni requisito de cobro'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $asignacion
$privada=LeerComisionC5DePrueba $ctx $broker.token
$reducida=LeerComisionC5DePrueba $ctx $agente.token
Check 'broker conserva reparto; agente no recibe netos internos' ($privada.reparto.agente -eq 400 -and $null -eq $reducida.reparto.agente -and $null -eq $reducida.reparto.empresa -and $null -eq $reducida.reparto.evaluacionBase) 'permiso por campo'
$script:EtapaFixtureE2E='C5_COBRO_PARCIAL'
$paquete=PrepararCobroC5DePrueba -Contexto $ctx -Token $broker.token -TokenEvidencia $agente.token -Importe 300 -Moneda 'PEN' -Fecha $hoy -Referencia "$marca-C1" -ConDatos
$propuesta=$paquete.propuesta
Check 'preparar cobro no registra fondos' ((IngresoPen (LeerComisionC5DePrueba $ctx $broker.token)) -eq 0) 'preparación'
$acuse=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $propuesta -ConAcuse
$repetida=Api POST "/actuaciones/$($propuesta.referencia)/confirmacion" $broker.token $acuse.confirmacion
Check 'reintento real recupera mismo movimiento' ($repetida.estado -eq 'EXITO' -and $repetida.resultado.movimiento -eq $acuse.resultado.movimiento) 'idempotencia'
$f=LeerComisionC5DePrueba $ctx $broker.token
Check 'parcial conserva saldo sin duplicar recepción ni tramos' ((IngresoPen $f) -eq 300 -and $f.cobro.saldoInformativoSobreCalculo.importe -eq 700 -and @($f.tramos).Count -eq 0) 'parcial'
$sabotaje=@{};foreach($k in $paquete.datos.Keys){$sabotaje[$k]=$paquete.datos[$k]};$sabotaje.tipo='P'
$tipoViejo=ApiError POST '/comisiones/movimientos/propuestas' $broker.token $sabotaje
Check 'selector P retirado se rechaza antes de ejecutar el cuerpo' ($tipoViejo.codigo -eq 400) 'binding estricto'
$sinMotivo=ApiError POST '/comisiones/conclusiones/propuestas' $broker.token @{comision=$f.id;versionEsperada=$f.version;motivo=' '}
Check 'concluir exige motivo' ($sinMotivo.codigo -eq 400) 'motivo'
$reprogramacion=Api POST '/comisiones/seguimientos/propuestas' $broker.token @{comision=$f.id;versionEsperada=$f.version;proximaRevision=$hoyFecha.AddDays(2).ToString('yyyy-MM-dd');motivo='Fecha acordada TEST'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $reprogramacion
$f=LeerComisionC5DePrueba $ctx $broker.token
Check 'seguimiento conserva ingresos y saldo' ((IngresoPen $f) -eq 300 -and $f.cobro.saldoInformativoSobreCalculo.importe -eq 700 -and -not $f.gestion.concluida) 'seguimiento'
$conclusion=Api POST '/comisiones/conclusiones/propuestas' $broker.token @{comision=$f.id;versionEsperada=$f.version;motivo='Gestión concluida con lo recibido TEST; sin condonación'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $conclusion
$f=LeerComisionC5DePrueba $ctx $broker.token
Check 'concluir gestión no condona ni cambia pacto/recibido' ($f.gestion.concluida -and (IngresoPen $f) -eq 300 -and $f.cobro.saldoInformativoSobreCalculo.importe -eq 700 -and $f.pacto.revisionAprobatoria -eq $ctx.pacto.revisionAprobatoria) 'conclusión'
$otra=PrepararCobroC5DePrueba -Contexto $ctx -Token $broker.token -TokenEvidencia $agente.token -Importe 700 -Moneda 'PEN' -Fecha $hoy -Referencia "$marca-C2"
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $otra
$f=LeerComisionC5DePrueba $ctx $broker.token
Check 'otro cobro real no reabre gestión automáticamente' ($f.gestion.concluida -and (IngresoPen $f) -eq 1000 -and $f.cobro.saldoInformativoSobreCalculo.importe -eq 0) 'cobro posterior'
$movimientosReales=[long](Invoke-ControlLocalE2ESql "select count(*) from comision_movimiento where id_comision_liquidacion=$($f.id)")
Check 'dos depósitos siguen siendo dos movimientos del mismo pacto único' ($movimientosReales -eq 2 -and @($f.tramos).Count -eq 0) 'movimientos'
$resumen=Api GET '/contratos/resumen' $broker.token $null
$ingresos=@($resumen.ingresosRealmenteRecibidos|Where-Object {$_.moneda -eq 'PEN'})
Check 'resumen financiero conserva ingresos realmente recibidos' ($ingresos.Count -eq 1 -and $ingresos[0].monto -eq 1000) 'resumen'
$vieja=ApiError POST "/contratos/$($ctx.contrato.id)/comision/movimientos" $broker.token @{tipo='C';monto=1}
Check 'ruta genérica retirada no escribe movimientos' ($vieja.codigo -in @(404,405)) 'retiro'
Write-Host "== Resultado C5 HTTP: $ok OK / $fail FALLAS =="
if($fail -gt 0){exit 1}
