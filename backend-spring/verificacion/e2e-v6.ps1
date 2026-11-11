# E2E de aislamiento por tenant sobre los contratos C1/C2 actuales.
# Conserva registro, precio/historia, prospeccion, revision, reenvio,
# reasignacion y fin del mandato; no usa backfill ni un tenant legacy fijo.
# Uso: Invoke-E2E.ps1 -Suite v6
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
. "$PSScriptRoot/lib-publicaciones-c6.ps1"
. "$PSScriptRoot/lib-financiacion-b0-test.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
$null = AsegurarPlanEmpresarialB0DePrueba -Organizacion $fixtureE2E.organizacion -Codigo $fixtureE2E.codigo
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
}



function ApiError($metodo,$ruta,$token,$cuerpo) {
 try{Api $metodo $ruta $token $cuerpo|Out-Null;return @{codigo=0}}
 catch{$r=$_.Exception.Response;if($null -eq $r){throw};return @{codigo=[int]$r.StatusCode}}
}

Write-Host "`n== 1. Identidad global y contexto seleccionado actual ==" -ForegroundColor Cyan
$agente=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$admin=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
$brokerDestino=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
foreach($actor in @($agente,$broker,$admin)){
 $sesion=Api GET '/sesion' $actor.token
 Check 'contexto real pertenece al tenant exacto del fixture' ($sesion.idOrganizacion -eq $fixtureE2E.organizacion -and $sesion.rol -eq $actor.rol) 'contexto'
}
$hoy=(Get-Date).ToString('yyyy-MM-dd');$finEncargo=(Get-Date).AddDays(90).ToString('yyyy-MM-dd');$sufijo=[guid]::NewGuid().ToString('N').Substring(0,10)

Write-Host "`n== 2. Cartera autorizada y frontera de gobierno ==" -ForegroundColor Cyan
$totalPrevio=(Api GET '/locales?pagina=1&tamano=10' $agente.token).totalRecords
Check 'organizacion nueva no hereda cartera semilla' ($totalPrevio -eq 0) 'cartera nueva'
Check 'TenantAdmin no recibe cartera por administrar' ((ApiError GET '/locales' $admin.token).codigo -eq 403) 'gobierno'
$propietario=(NuevoPropietarioC2DePrueba -Token $agente.token -Nombre 'Titular ficticio TEST V6').id

Write-Host "`n== 3. Alta actual y estampado de tenant ==" -ForegroundColor Cyan
# Montaje exclusivamente TEST aprobado: una mensualidad E/R en PEN, IGV N,
# vigencia explicita hoy a finEncargo. No es un default del producto.
$nuevoLocal=NuevoInmuebleConEncargo -Token $agente.token -Direccion 'Av. Multi-tenant TEST456' -IdPropietario $propietario `
 -Codigo ('LOC-V6'+$sufijo) -Metraje 150.5 -Rubro 'Cafeteria' -Importe 9200 -Moneda 'PEN' -Descripcion 'Alta multi-tenant TEST V6' -LocalIndependienteTest `
 -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' -Exclusividad $true -InicioEncargo $hoy -FinEncargo $finEncargo
$idLocal=$nuevoLocal.id
Check 'tipo y uso declarados se conservan' ((Invoke-ControlLocalE2ESql "select tipo_inmueble||'/'||uso from propiedad where id_propiedad=$idLocal and organizacion_id=$($fixtureE2E.organizacion)") -eq 'L/C') 'tipo/uso'
Check 'propiedad lleva la organizacion actual, no legacy1' ((Invoke-ControlLocalE2ESql "select organizacion_id from propiedad where id_propiedad=$idLocal") -eq "$($fixtureE2E.organizacion)") 'propiedad'
Check 'atributos tienen el mismo tenant que su propiedad' ((Invoke-ControlLocalE2ESql "select count(*) from atributo_propiedad where id_propiedad=$idLocal and organizacion_id<>$($fixtureE2E.organizacion)") -eq '0') 'atributos'
Check 'encargo pertenece al mismo recurso y tenant' ((Invoke-ControlLocalE2ESql "select count(*) from captacion where id_captacion=$($nuevoLocal.idEncargo) and id_propiedad=$idLocal and organizacion_id=$($fixtureE2E.organizacion)") -eq '1') 'encargo'
Check 'el alta incrementa solo su cartera en1' ((Api GET '/locales?pagina=1&tamano=10' $agente.token).totalRecords -eq ($totalPrevio+1)) 'total'

Write-Host "`n== 4. Precio en el Encargo e historia append-only ==" -ForegroundColor Cyan
$ficha=Api GET "/locales/$idLocal" $agente.token
Check 'ficha resuelve el inmueble y titular del caso' ($ficha.codigoLocal -eq ('LOC-V6'+$sufijo) -and $null -ne $ficha.propietarioNombre) 'ficha'
$encargo=Api GET "/encargos/$($nuevoLocal.idEncargo)" $agente.token
Check 'preparacion conserva precio inicial con moneda y operacion propias' ($encargo.estado -eq 'EN_PREPARACION' -and $encargo.operacion -eq 'ALQUILER' -and $encargo.condiciones.precioPedido -eq 9200 -and $encargo.condiciones.monedaPrecio -eq 'PEN') 'condicion inicial'
Check 'el pacto TEST de alquiler declara mensualidad y vigencia completas' ($encargo.condiciones.tipoComision -eq 'E' -and $encargo.condiciones.baseCalculo -eq 'R' -and $encargo.condiciones.valorComision -eq 1 -and $encargo.condiciones.tratamientoIgv -eq 'N' -and $encargo.desde -eq $hoy -and $encargo.hasta -eq $finEncargo) 'pacto explicito, no inferido'
$idCondicionInicial=[long](Invoke-ControlLocalE2ESql "select id_condicion_economica from captacion where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo) and id_propiedad=$idLocal")
$pacto=$encargo.condiciones;$pacto.precioPedido=9900
$encargoEditado=Invoke-ComandoC2DePrueba PUT "/encargos/$($nuevoLocal.idEncargo)/condiciones" $agente.token @{
 versionEsperada=$encargo.version;condiciones=$pacto;motivo='Precio de salida explicito TEST, aun en preparacion.'
} ('v6-precio-'+[guid]::NewGuid().ToString('N'))
Check 'precio editado procede del pacto actual' ((Api GET "/locales/$idLocal" $agente.token).precioReferencial -eq 9900) 'precio'
$idCondicionActual=[long](Invoke-ControlLocalE2ESql "select id_condicion_economica from captacion where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo)")
Check 'editar conserva la condicion anterior y una nueva version economica' `
 ($idCondicionActual -ne $idCondicionInicial -and $encargoEditado.version -gt $encargo.version -and $encargoEditado.condiciones.precioPedido -eq 9900 -and $encargoEditado.condiciones.monedaPrecio -eq 'PEN' -and
  (Invoke-ControlLocalE2ESql "select count(*) from condicion_economica_captacion where organizacion_id=$($fixtureE2E.organizacion) and id_condicion_economica=$idCondicionInicial and importe_referencia=9200 and moneda_referencia='PEN' and tipo_operacion='A'") -eq '1') '9200/PEN conservado; actual9900/PEN'
Check 'preparar o editar no acredita precio autorizado U' ((Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo) and hito='U'") -eq '0') 'sin autorizacion ficticia'
$preciosAntesO=Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo)"
$ofertasAntesO=Invoke-ControlLocalE2ESql "select count(*) from oferta where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo)"
$ofertadoManual=Invoke-ComandoC2DePrueba POST "/locales/$idLocal/precios" $agente.token @{hito='O';moneda='PEN';monto=9500;operacion='ALQUILER'} ('v6-ofertado-prohibido-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'OFERTADO manual se rechaza porque requiere una negociacion real' ($ofertadoManual.codigo -eq 400 -and $ofertadoManual.error -match 'OFERTADO.*no se registra a mano.*mesa de negociacion') 'puerta de productor'
$trasRechazoO=Api GET "/encargos/$($nuevoLocal.idEncargo)" $agente.token
Check 'el rechazo no crea oferta ni precio ni cambia el Encargo' `
 ($trasRechazoO.version -eq $encargoEditado.version -and $trasRechazoO.condiciones.precioPedido -eq 9900 -and
  (Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo)") -eq $preciosAntesO -and
  (Invoke-ControlLocalE2ESql "select count(*) from oferta where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo)") -eq $ofertasAntesO) 'sin hecho comercial inventado'
# ESPERADO ya pertenece a la puerta manual; no equivale a OFERTADO/autorizado.
$deducido=Api POST "/locales/$idLocal/precios" $agente.token @{hito='E';moneda='PEN';monto=9600}
Check 'la referencia esperada deduce la unica operacion y conserva moneda y tenant' `
 ($deducido.operacion -eq 'ALQUILER' -and $deducido.hito -eq 'E' -and $deducido.moneda -eq 'PEN' -and $deducido.monto -eq 9600 -and
  (Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where id_precio=$($deducido.id) and organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo) and operacion='A'") -eq '1') 'referencia, no oferta'
$null=HabilitarEncargoC2DePrueba -IdEncargo $nuevoLocal.idEncargo -TokenResponsable $agente.token -TokenDecisor $broker.token -MonedaComision 'PEN' -Exclusividad $true `
 -HitoDevengo 'CONTRATO_ALQUILER' -ParteObligada 'PROPIETARIO' -Beneficiario 'ORGANIZACION' -Exigibilidad 'AL_DEVENGO' -FechaFirma $hoy
$serieU=Invoke-ControlLocalE2ESql "select string_agg(monto::int::text||'/'||moneda||'/'||operacion,'>' order by id_precio) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($nuevoLocal.idEncargo) and hito='U'"
Check 'la habilitacion real crea solo U9900 del alquiler autorizado en PEN' ($serieU -eq '9900/PEN/A') 'hecho autorizado'
$publicacion=PublicarAnuncioC6DePrueba -IdEncargo $nuevoLocal.idEncargo -Token $agente.token -Canal 'URBANIA' -Importe 9900 -Moneda 'PEN' -Titulo 'Anuncio V6 TEST'
Check 'publicacion habilitada nace en su tenant' ($publicacion.estado -eq 'P' -and (Invoke-ControlLocalE2ESql "select organizacion_id from publicacion where id_publicacion=$($publicacion.id)") -eq "$($fixtureE2E.organizacion)") 'publicacion'

Write-Host "`n== 5. Prospeccion declarada y transiciones trazadas ==" -ForegroundColor Cyan
$idProspeccion=NuevaProspeccion -Token $agente.token -IdPropiedad $idLocal -Observaciones 'Prospeccion de venta TEST; no altera el alquiler.'
$inicial=Api GET "/prospecciones/$idProspeccion" $agente.token
Check 'prospecto empieza en P y referencia su inmueble' ($inicial.estado -eq 'P' -and $inicial.localId -eq $idLocal) 'prospeccion'
$antesHistoria=[int](Invoke-ControlLocalE2ESql "select count(*) from historial_estado where organizacion_id=$($fixtureE2E.organizacion) and entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion")
foreach($paso in @('contactar','reunion','propuesta')){$p=Api POST "/prospecciones/$idProspeccion/$paso" $agente.token $null}
Check 'propuesta pendiente no acredita aceptacion ni Encargo' ($p.estado -eq 'S' -and $p.resultadoPropuesta -eq 'P' -and $null -eq $p.idCaptacion) 'propuesta'
Check 'tres transiciones conservan auditoria del tenant' ([int](Invoke-ControlLocalE2ESql "select count(*) from historial_estado where organizacion_id=$($fixtureE2E.organizacion) and entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion") -eq ($antesHistoria+3)) 'historia'

Write-Host "`n== 6. Venta separada, peticion observada y reenvio de la misma identidad ==" -ForegroundColor Cyan
$captada=Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{
 operacion='VENTA';importe=350000;moneda='USD';comisionPactada=3;tipoComision='P';baseCalculo='V';tratamientoIgv='N'
 inicioEncargo=$hoy;finEncargo=$finEncargo;exclusividad=$true
}
$idCaptacion=$captada.idCaptacion
Check 'nuevo Encargo de venta no sustituye el alquiler' ($captada.estado -eq 'T' -and $idCaptacion -ne $nuevoLocal.idEncargo) 'identidad'
$pactoVenta=Api GET "/encargos/$idCaptacion" $agente.token
Check 'la venta conserva USD y su3porciento declarado con vigencia explicita' ($pactoVenta.operacion -eq 'VENTA' -and $pactoVenta.condiciones.monedaPrecio -eq 'USD' -and $pactoVenta.condiciones.tipoComision -eq 'P' -and $pactoVenta.condiciones.baseCalculo -eq 'V' -and $pactoVenta.condiciones.valorComision -eq 0.03 -and $pactoVenta.desde -eq $hoy -and $pactoVenta.hasta -eq $finEncargo) 'fuente P/V/3pct, no mensualidad'
$preciosAntesAmbiguedad=Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_propiedad=$idLocal"
$ambigua=Invoke-ComandoC2DePrueba POST "/locales/$idLocal/precios" $agente.token @{hito='E';moneda='PEN';monto=9700} ('v6-operacion-ambigua-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'precio sin operacion rechaza especificamente venta y alquiler simultaneos' `
 ($ambigua.codigo -eq 400 -and $ambigua.error -match 'venta y de alquiler a la vez.*declara.*VENTA o ALQUILER' -and
  (Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_propiedad=$idLocal") -eq $preciosAntesAmbiguedad) 'ambiguedad real sin insertar precio'
$preparada=PrepararPeticionC2DePrueba -IdEncargo $idCaptacion -TokenResponsable $agente.token -MonedaComision 'USD' -Exclusividad $true `
 -HitoDevengo 'ESCRITURA' -ParteObligada 'PROPIETARIO' -Beneficiario 'ORGANIZACION' -Exigibilidad 'AL_DEVENGO' -FechaFirma $hoy
$enc=Api GET "/encargos/$idCaptacion" $broker.token
$negativa=ApiError POST "/encargos/peticiones/$($preparada.peticion.id)/decision" $broker.token @{
 actuacion='OBSERVAR';versionPeticion=$preparada.peticion.version;versionEncargo=$enc.version;observaciones=''
}
Check 'observar exige motivo, no un cambio sin contenido' ($negativa.codigo -in @(400,409)) 'motivo'
$propuesta=Api POST "/encargos/peticiones/$($preparada.peticion.id)/decision" $broker.token @{
 actuacion='OBSERVAR';versionPeticion=$preparada.peticion.version;versionEncargo=$enc.version;observaciones='Precisar descripcion TEST del instrumento.'
}
$observada=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $propuesta
Check 'observar no cambia EN_PREPARACION ni sustituye peticion' ($observada.estado -eq 'OBSERVADA' -and $observada.id -eq $preparada.peticion.id -and (Api GET "/encargos/$idCaptacion" $agente.token).estado -eq 'EN_PREPARACION') 'observacion'
$enc=Api GET "/encargos/$idCaptacion" $agente.token
$reenviada=Invoke-ComandoC2DePrueba POST "/encargos/peticiones/$($observada.id)/reenviar" $agente.token @{
 versionPeticion=$observada.version;presentacion=@{documento=$preparada.documento.id;versionEsperada=$enc.version;partes=$preparada.partes;firmas=$observada.firma.firmas;respuestaObservaciones='Se precisa la descripcion TEST sin cambiar el instrumento firmado.'}
} ('v6-reenviar-'+[guid]::NewGuid().ToString('N'))
Check 'reenvio conserva la peticion y versiona su constancia' ($reenviada.id -eq $observada.id -and $reenviada.estado -eq 'PENDIENTE' -and $reenviada.firma.revisionPresentacion -ne $observada.firma.revisionPresentacion) 'reenvio'
Check 'ruta antigua que mutaba operacion esta retirada' ((ApiError PUT "/captaciones/$idCaptacion" $agente.token @{motivoOperacion='A';tipoOperacion='A'}).codigo -in @(404,405)) 'mutacion retirada'
Check 'operacion real sigue siendo VENTA' ((Api GET "/encargos/$idCaptacion" $agente.token).operacion -eq 'VENTA') 'operacion'
$aprobada=AprobarPeticionC2DePrueba -IdEncargo $idCaptacion -IdPeticion $reenviada.id -Documento $preparada.documento -TokenDecisor $broker.token
Check 'aprobacion humana conserva tenant y documento originales' ($aprobada.estado -eq 'APROBADA' -and $aprobada.id -eq $preparada.peticion.id) 'aprobacion'

Write-Host "`n== 7. Reasignacion gobernada y fin de ese mandato, no cierre economico ==" -ForegroundColor Cyan
$enc=Api GET "/encargos/$idCaptacion" $broker.token
$agenteDestino=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.ltorres.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$sesionDestino=Api GET '/sesion' $agenteDestino.token
if($sesionDestino.rol -ne 'AGENTE' -or $sesionDestino.idOrganizacion -ne $fixtureE2E.organizacion -or $sesionDestino.idDominio -le 0){throw 'El destino TEST requiere su contexto operativo real.'}
$idAgenteDestino=[long]$sesionDestino.idDominio
# El instrumento aprobado nombra al profesional. Gobierno no altera esa firma.
$reasignacionesAntesFirmado=Invoke-ControlLocalE2ESql "select count(*) from reasignacion_captacion where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$idCaptacion"
$condicionesFirmadas=$enc.condiciones|ConvertTo-Json -Depth 20 -Compress
$candidatosFirmados=@{codigo=0;error=''}
try {Api GET "/captaciones/$idCaptacion/reasignacion/candidatos" $admin.token | Out-Null}
catch {
 if($null -eq $_.Exception.Response){throw}
 $candidatosFirmados=@{codigo=[int]$_.Exception.Response.StatusCode;error=(LeerErrorHttpDeFixture $_)}
}
Check 'el selector no ofrece sustituir al profesional del instrumento firmado' ($candidatosFirmados.codigo -eq 400 -and $candidatosFirmados.error -match 'corresponde adenda') 'firma protegida'
$traspasoFirmado=Invoke-ComandoC2DePrueba POST "/captaciones/$idCaptacion/reasignar" $admin.token @{
 idAgenteNuevo=$idAgenteDestino;idAgenteObservado=$enc.responsable;versionEsperada=$enc.version;motivo='Intento TEST de sustituir profesional firmado sin adenda.'
} ('v6-firma-protegida-'+[guid]::NewGuid().ToString('N')) -EsperarError
$firmadoTrasIntento=Api GET "/encargos/$idCaptacion" $broker.token
Check 'el cambio firmado sin adenda se rechaza sin modificar responsable o pacto' `
 ($traspasoFirmado.codigo -eq 400 -and $traspasoFirmado.error -match 'corresponde adenda' -and $firmadoTrasIntento.responsable -eq $enc.responsable -and $firmadoTrasIntento.version -eq $enc.version -and
  $firmadoTrasIntento.documentoActual -eq $enc.documentoActual -and ($firmadoTrasIntento.condiciones|ConvertTo-Json -Depth 20 -Compress) -eq $condicionesFirmadas -and
  (Invoke-ControlLocalE2ESql "select count(*) from reasignacion_captacion where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$idCaptacion") -eq $reasignacionesAntesFirmado) 'sin efecto sobre lo firmado'
# Otro episodio TEST no firmado demuestra el cambio operativo permitido;
# no se atribuye a este positivo cobertura de una adenda de profesional.
$preparacionReasignable=NuevoInmuebleConEncargo -Token $agente.token -Direccion ('Direccion TEST de preparacion reasignable '+$sufijo) -IdPropietario $propietario `
 -Codigo ('LOC-V6-R'+$sufijo) -Metraje 150.5 -Rubro 'Cafeteria' -Operacion 'VENTA' -Importe 350000 -Moneda 'USD' -LocalIndependienteTest
$idReasignable=$preparacionReasignable.idEncargo
$encReasignable=Api GET "/encargos/$idReasignable" $broker.token
Check 'la reasignacion positiva opera sobre una preparacion no firmada distinta' ($idReasignable -ne $idCaptacion -and $encReasignable.estado -eq 'EN_PREPARACION' -and $null -eq $encReasignable.documentoActual) 'ambito del caso positivo'
$candidatos=Api GET "/captaciones/$idReasignable/reasignacion/candidatos" $admin.token
Check 'candidato exacto de otro equipo es visible por gobierno autorizado' (@($candidatos.items|Where-Object{$_.idAgente -eq $idAgenteDestino}).Count -eq 1) 'candidato'
$traspaso=Invoke-ComandoC2DePrueba POST "/captaciones/$idReasignable/reasignar" $admin.token @{
 idAgenteNuevo=$idAgenteDestino;idAgenteObservado=$encReasignable.responsable;versionEsperada=$encReasignable.version;motivo='Cruce autorizado entre equipos de la preparacion no firmada TEST V6.'
} ('v6-traspaso-'+[guid]::NewGuid().ToString('N'))
Check 'reasignar cambia responsable y conserva EN_PREPARACION' ($traspaso.id -eq $idReasignable -and $traspaso.idAgente -eq $idAgenteDestino -and $traspaso.estado -eq 'EN_PREPARACION') 'traspaso operativo'
Check 'evento conserva atribucion de gobierno y tenant' ((Invoke-ControlLocalE2ESql "select count(*) from reasignacion_captacion where id_reasignacion=$($traspaso.idReasignacion) and id_captacion=$idReasignable and organizacion_id=$($fixtureE2E.organizacion) and tipo_rol_actor='TENANT_ADMIN'") -eq '1') 'auditoria'
Check 'gobierno no obtiene condiciones comerciales tras reasignar' ((ApiError GET "/encargos/$idReasignable" $admin.token).codigo -eq 403) 'sin ampliacion'
$encDestino=Api GET "/encargos/$idReasignable" $brokerDestino.token
$fin=Invoke-ComandoC2DePrueba POST "/encargos/$idReasignable/fin" $brokerDestino.token @{
 tipoFin='FINALIZACION';fechaEfectiva=$hoy;motivo='Fin explicito del mandato ficticio TEST; no venta ni contrato simulado.';versionEsperada=$encDestino.version;evidencias=@()
} ('v6-fin-'+[guid]::NewGuid().ToString('N'))
Check 'solo termina la preparacion reasignada; venta y alquiler firmados se conservan' ($fin.id -eq $idReasignable -and $fin.estado -eq 'FINALIZADO' -and
 (Api GET "/encargos/$idCaptacion" $agente.token).estado -eq 'APROBADO' -and (Api GET "/encargos/$($nuevoLocal.idEncargo)" $agente.token).estado -eq 'APROBADO') 'fin aislado'
Check 'finalizar la preparacion no registra un cierre economico' ((Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$idReasignable and hito='C'") -eq '0') 'sin precio de cierre'

Write-Host "`n== 8. Segunda organizacion real de TEST, sin filtro global ficticio ==" -ForegroundColor Cyan
$otra=Initialize-ControlLocalE2EActors -Etiqueta 'v6otro'
$otroAgente=Connect-ControlLocalE2E -BaseUrl $base -Correo $otra.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $otra.organizacion -Rol 'AGENTE'
Check 'otro tenant no lista el inmueble del primero' (@((Api GET '/locales?pagina=1&tamano=100' $otroAgente.token).items|Where-Object{$_.id -eq $idLocal}).Count -eq 0) 'lista'
Check 'referencia de inmueble ajeno no se resuelve' ((ApiError GET "/locales/$idLocal" $otroAgente.token).codigo -in @(403,404)) 'directa'
Check 'ninguna fila de historia de estos recursos pierde tenant' ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where organizacion_id is null and ((entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion) or (entidad_tipo='CAPTACION' and id_entidad in ($idCaptacion,$($nuevoLocal.idEncargo))))") -eq '0') 'historia'
# No backfill global ni all-tables NULL: cuenta/catalogos globales y tablas
# privadas tienen autoridades distintas. Se verifican los recursos creados.
Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if($fail -eq 0){'Green'}else{'Red'})
if($fail -gt 0){exit 1}
