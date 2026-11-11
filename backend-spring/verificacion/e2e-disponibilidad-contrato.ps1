# Ciclo contrato -> disponibilidad del inmueble (Bloque 7.3.2).
#
# LA REGLA QUE VERIFICA: terminar un contrato NO devuelve el local al mercado.
# Finalizar y rescindir lo dejan ALQUILADO con una tarea de revision, y solo una
# decision humana explicita -esta operacion- lo devuelve (D) o lo retira (T).
#
# El caso que mas importa es la RENOVACION: el contrato anterior queda en R y el
# sucesor nace vivo, asi que revisar el anterior tiene que fallar. Sin eso
# existiria una ventana en la que un local ocupado figura disponible.
#
# ASCII puro y sin BOM: PowerShell 5.1 lee un .ps1 sin BOM como ANSI y un solo
# caracter acentuado rompe el parseo del script entero.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
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
    $parametros = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 45 }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 8)
        $parametros['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) {
        $parametros['ContentType'] = 'application/json'
    }
    $parametros
}

function Api($metodo, $ruta, $token, $cuerpo) {
    # El splatting exige una VARIABLE: `@(expresion)[0]` no es splatting, es
    # indexar un array, y PowerShell lo pasa como un unico argumento posicional.
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
        $codigo = [int]$respuesta.StatusCode
        $contenido = LeerErrorHttpDeFixture $PSItem
        $mensaje = 'Respuesta HTTP sin mensaje JSON legible.'
        try { $jsonError = $contenido | ConvertFrom-Json; if($jsonError.error -is [string]){$mensaje=$jsonError.error} } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$ayer = $hoyFecha.AddDays(-1).ToString('yyyy-MM-dd')
$fechaInicio = $hoyFecha.AddDays(15).ToString('yyyy-MM-dd')
$fechaFin = $hoyFecha.AddDays(380).ToString('yyyy-MM-dd')
$fechaFinEncargo = $hoyFecha.AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Actores ==" -ForegroundColor Cyan
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$brokerAjeno = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del broker de otro equipo' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol

# Monta propietario, cliente, local, captacion aprobada, oportunidad, visita y
# solicitud APROBADA. Devuelve los ids que el escenario necesita despues.
function MontarOperacion($etiqueta) {
    $m = "$etiqueta$sufijo"
    $propietario = Api POST '/propietarios' $agente.token @{
        tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = ("6" + (Get-Random -Minimum 1000000 -Maximum 9999999))
        nombre = "Propietario $m"; telefono = '987700001'
        correo = "prop.$m@test.local"; consentimientoUsoDato = $true; estado = 'A'
    }
    $cliente = Api POST '/clientes' $agente.token @{
        tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = ("7" + (Get-Random -Minimum 1000000 -Maximum 9999999))
        nombre = "Cliente $m"; telefono = '987700002'; correo = "cli.$m@test.local"
        rubroComercial = 'Retail'; consentimientoContacto = $true; consentimientoUsoDato = $true; estado = 'A'
    }
    # `POST /locales` se retiro en el Corte 0A: el alta universal crea el
    # inmueble y abre su encargo en el mismo acto.
    $local = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token -Direccion "Av. $m 100" `
        -Distrito 'Lince' -IdPropietario $propietario.id -Metraje 80 `
        -Importe 2000 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1.00 `
        -TratamientoIgv 'N' -InicioEncargo $hoy -FinEncargo $fechaFinEncargo `
        -Descripcion "Captacion $m"
    $null = HabilitarEncargoC2DePrueba -IdEncargo $($local.idEncargo) -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
        -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
        -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato' -EjecucionComision @{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER'}}
    $captacion = Api GET "/captaciones/$($local.idEncargo)" $agente.token $null
    $oportunidad = NuevaOportunidadC4DePrueba -IdEncargo ([long]$captacion.id) -IdCliente $cliente.id -Token $agente.token `
        -EvidenciaInteres 'Manifestacion expresa FICTICIA de interes del caso TEST E2E C4.' -Observaciones "Oportunidad $m"
    $idOportunidad = [long]$oportunidad.id
    # C4: disponibilidad y contrato tienen sus actos propios; no registrar
    # una comunicacion ficticia para habilitar la visita o el contrato.
    $visita = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $ayer -Hora '10:00' -Token $agente.token -Observaciones "Visita $m"
    ResultadoVisitaC4DePrueba -IdVisita $($visita.id) -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $hoy -Desenlace @{
        resultado = 'INTERESADO'; observaciones = "Interes $m"; nivelInteres = 5
        objecionPrincipal = 'O'; opinionPrecio = 'J'; proximaAccion = 'S'
    } | Out-Null
    $solicitud = Api POST '/solicitudes' $agente.token @{
        codigoSolicitud = "SOL-$m"; fechaRegistro = $hoy; idOportunidad = $idOportunidad
        montoPropuesto = 2000; moneda = 'PEN'; plazoMeses = 12; fechaInicio = $fechaInicio
        formaPago = 'TRANSFERENCIA'; mesesGarantia = 2; mesesAdelanto = 1
        fechaVigenciaOferta = $hoyFecha.AddDays(10).ToString('yyyy-MM-dd'); observaciones = "Solicitud $m"
    }
    Api POST "/solicitudes/$([long]$solicitud.id)/reenviar" $agente.token $null | Out-Null
    Api POST '/evaluaciones' $broker.token @{
        tipoEvaluacion = 'F'; resultado = 'A'; idSolicitud = [long]$solicitud.id
        observaciones = "Aprobada $m"
    } | Out-Null
    [pscustomobject]@{
        Marca = $m; IdLocal = [long]$local.id; IdCaptacion = [long]$captacion.id
        IdSolicitud = [long]$solicitud.id; IdOportunidad = $idOportunidad
    }
}

function DisponibilidadDe($idLocal) {
    Invoke-ControlLocalE2ESql "select disponibilidad_comercial from propiedad where id_propiedad=$idLocal"
}

Write-Host "`n== 2. Contrato P: existir no saca el local del mercado ==" -ForegroundColor Cyan
$a = MontarOperacion 'DISPA'
Check 'el local nace DISPONIBLE' ((DisponibilidadDe $a.IdLocal) -eq 'D') (DisponibilidadDe $a.IdLocal)
$borrador=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$a.IdSolicitud;incidencias="Borrador $($a.Marca)"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
$idBorrador = [long]$borrador.id
Check 'el contrato nace EN PROCESO' ($borrador.estadoContrato -eq 'P') $borrador.estadoContrato
# Un borrador NO equivale a Reservado: reservar es otra causa de negocio.
Check 'un contrato P no cambia la disponibilidad' ((DisponibilidadDe $a.IdLocal) -eq 'D') (DisponibilidadDe $a.IdLocal)

Write-Host "`n== 3. Anular desde P: no hay nada que liberar ==" -ForegroundColor Cyan
Api POST "/contratos/$idBorrador/anular" $broker.token @{ motivo = 'El cliente desistio' } | Out-Null
Check 'el contrato queda ANULADO' `
    ((Invoke-ControlLocalE2ESql "select estado_contrato from contrato_alquiler where id_contrato_alquiler=$idBorrador") -eq 'A') 'estado'
Check 'la disponibilidad original queda intacta' ((DisponibilidadDe $a.IdLocal) -eq 'D') (DisponibilidadDe $a.IdLocal)
$revisionInnecesaria = ApiError POST "/contratos/$idBorrador/revision-disponibilidad" $broker.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'No hacia falta'
}
Check 'revisar un contrato que nunca ocupo el local se rechaza con 400' `
    ($revisionInnecesaria.codigo -eq 400 -and $revisionInnecesaria.error -match 'no esta alquilado') `
    "codigo=$($revisionInnecesaria.codigo) error=$($revisionInnecesaria.error)"

Write-Host "`n== 4. Firmar y activar: el local queda ALQUILADO y sigue asi ==" -ForegroundColor Cyan
$b = MontarOperacion 'DISPB'
$contratoB=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$b.IdSolicitud;incidencias="Borrador $($b.Marca)"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
$idB = [long]$contratoB.id
$formalizado=FormalizarAlquilerC5DePrueba -IdEncargo $b.IdCaptacion -IdOportunidad $b.IdOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 2000 -Moneda 'PEN' -Inicio $contratoB.fechaInicioContrato -Fin $contratoB.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $idB
Check 'firmar ejecuta el cierre comercial: el local queda ALQUILADO' `
    ((DisponibilidadDe $b.IdLocal) -eq 'A') (DisponibilidadDe $b.IdLocal)
Api POST "/contratos/$idB/activar" $agente.token @{ motivo = 'Entrega realizada' } | Out-Null
Check 'activar D->V no vuelve a tocar la disponibilidad' `
    ((DisponibilidadDe $b.IdLocal) -eq 'A') (DisponibilidadDe $b.IdLocal)

Write-Host "`n== 5. Finalizar: sigue ALQUILADO y aparece la revision ==" -ForegroundColor Cyan
Api POST "/contratos/$idB/finalizar" $agente.token @{ motivo = 'Plazo cumplido' } | Out-Null
Check 'finalizar NO devuelve el local al mercado' `
    ((DisponibilidadDe $b.IdLocal) -eq 'A') (DisponibilidadDe $b.IdLocal)
Check 'y crea la tarea de revision atada a ESE contrato' `
    ((Invoke-ControlLocalE2ESql "select count(*) from tarea where tipo='REVISION_INMUEBLE' and id_contrato_origen=$idB and estado in ('P','E')") -eq '1') `
    'tarea de revision'

Write-Host "`n== 6. La revision es del BROKER y valida su cuerpo ==" -ForegroundColor Cyan
$comoAgente = ApiError POST "/contratos/$idB/revision-disponibilidad" $agente.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'Local en buen estado'
}
Check 'el agente no revisa (403)' ($comoAgente.codigo -eq 403) "codigo=$($comoAgente.codigo)"
$ajeno = ApiError POST "/contratos/$idB/revision-disponibilidad" $brokerAjeno.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'Intento de otro tenant'
}
Check 'un broker de otro equipo no revisa: fuera de su alcance por captacion (403)' ($ajeno.codigo -eq 403) "codigo=$($ajeno.codigo)"
$sinMotivo = ApiError POST "/contratos/$idB/revision-disponibilidad" $broker.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = '   '
}
Check 'el motivo es obligatorio' `
    ($sinMotivo.codigo -eq 400 -and $sinMotivo.error -match 'motivo') `
    "codigo=$($sinMotivo.codigo) error=$($sinMotivo.error)"
$resultadoMalo = ApiError POST "/contratos/$idB/revision-disponibilidad" $broker.token @{
    resultado = 'RESERVAR'; motivo = 'Reservado no entra aqui'
}
Check 'RESERVAR no es un resultado posible de la revision' `
    ($resultadoMalo.codigo -eq 400 -and $resultadoMalo.error -match 'VOLVER_AL_MERCADO') `
    "codigo=$($resultadoMalo.codigo) error=$($resultadoMalo.error)"

Write-Host "`n== 7. VOLVER_AL_MERCADO: A -> D ==" -ForegroundColor Cyan
$revision = Api POST "/contratos/$idB/revision-disponibilidad" $broker.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'Local entregado conforme'
}
Check 'la revision devuelve el local al mercado' ((DisponibilidadDe $b.IdLocal) -eq 'D') (DisponibilidadDe $b.IdLocal)
Check 'la respuesta declara el trayecto A->D' `
    ($revision.disponibilidadAnterior -eq 'A' -and $revision.disponibilidadNueva -eq 'D' -and -not $revision.repetida) `
    ($revision | ConvertTo-Json -Compress)
Check 'y cierra la tarea de revision de ESE contrato' `
    ((Invoke-ControlLocalE2ESql "select count(*) from tarea where id_contrato_origen=$idB and estado in ('P','E')") -eq '0') 'tarea abierta'
Check 'el historial guarda la transicion con actor, rol y motivo' `
    ((Invoke-ControlLocalE2ESql "select tipo_rol_actor||'|'||(id_actor is not null)::text||'|'||estado_anterior||'>'||estado_nuevo||'|'||(motivo like '%entregado conforme%')::text from historial_estado where entidad_tipo='DISPONIBILIDAD_PROPIEDAD' and id_entidad=$($b.IdLocal) order by id_historial desc limit 1") -eq 'BROKER|true|A>D|true') `
    (Invoke-ControlLocalE2ESql "select tipo_rol_actor||'|'||estado_anterior||'>'||estado_nuevo from historial_estado where entidad_tipo='DISPONIBILIDAD_PROPIEDAD' and id_entidad=$($b.IdLocal) order by id_historial desc limit 1")

Write-Host "`n== 8. La historia comercial NO revive ==" -ForegroundColor Cyan
Check 'la captacion sigue CERRADA' `
    ((Invoke-ControlLocalE2ESql "select estado from captacion where id_captacion=$($b.IdCaptacion)") -eq 'C') 'captacion reabierta'
Check 'las publicaciones siguen dadas de baja' `
    ((Invoke-ControlLocalE2ESql "select count(*) from publicacion where id_propiedad=$($b.IdLocal) and estado<>'C'") -eq '0') 'publicacion reabierta'
Check 'la oportunidad sigue finalizada exitosa' `
    ((Invoke-ControlLocalE2ESql "select estado from oportunidad_comercial where id_oportunidad=$($b.IdOportunidad)") -eq 'F') 'oportunidad alterada'
Check 'la solicitud sigue cerrada' `
    ((Invoke-ControlLocalE2ESql "select estado from solicitud_alquiler where id_solicitud=$($b.IdSolicitud)") -eq 'C') 'solicitud alterada'
$comision=LeerComisionC5DePrueba $formalizado $broker.token
Check 'la revisión no fabrica dinero ni altera la base de comisión' `
    ($comision.calculo.cuantia.importe -eq 2000 -and $comision.cobro.estado -eq 'SIN_COBROS') 'comisión/cobro'

Write-Host "`n== 9. Idempotencia y conflicto de la revision ==" -ForegroundColor Cyan
$repetida = Api POST "/contratos/$idB/revision-disponibilidad" $broker.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'Local entregado conforme'
}
Check 'repetir la MISMA revision es idempotente, no un 500' `
    ($repetida.repetida -eq $true -and $repetida.id -eq $revision.id) ($repetida | ConvertTo-Json -Compress)
Check 'y no crea una segunda fila' `
    ((Invoke-ControlLocalE2ESql "select count(*) from revision_disponibilidad where id_contrato_alquiler=$idB") -eq '1') 'filas de revision'
$contradictoria = ApiError POST "/contratos/$idB/revision-disponibilidad" $broker.token @{
    resultado = 'RETIRAR_DEL_MERCADO'; motivo = 'Cambio de idea'
}
Check 'una revision contradictoria es 409, no una violacion UNIQUE' `
    ($contradictoria.codigo -eq 409 -and $contradictoria.error -match 'resultado distinto') `
    "codigo=$($contradictoria.codigo) error=$($contradictoria.error)"
Check 'y la disponibilidad no cambio' ((DisponibilidadDe $b.IdLocal) -eq 'D') (DisponibilidadDe $b.IdLocal)

Write-Host "`n== 10. Rescindir + RETIRAR_DEL_MERCADO: A -> T ==" -ForegroundColor Cyan
$c = MontarOperacion 'DISPC'
$contratoC=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$c.IdSolicitud;incidencias="Borrador $($c.Marca)"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
$idC = [long]$contratoC.id
$formalizadoC=FormalizarAlquilerC5DePrueba -IdEncargo $c.IdCaptacion -IdOportunidad $c.IdOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 2000 -Moneda 'PEN' -Inicio $contratoC.fechaInicioContrato -Fin $contratoC.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $idC
Api POST "/contratos/$idC/activar" $agente.token @{ motivo = 'Entrega' } | Out-Null
Api POST "/contratos/$idC/rescindir" $broker.token @{ motivo = 'Incumplimiento del inquilino' } | Out-Null
Check 'rescindir tampoco libera el local' ((DisponibilidadDe $c.IdLocal) -eq 'A') (DisponibilidadDe $c.IdLocal)
$retiro = Api POST "/contratos/$idC/revision-disponibilidad" $broker.token @{
    resultado = 'RETIRAR_DEL_MERCADO'; motivo = 'El propietario lo destina a uso propio'
}
Check 'la revision retira el local del mercado' ((DisponibilidadDe $c.IdLocal) -eq 'T') (DisponibilidadDe $c.IdLocal)
Check 'la respuesta declara el trayecto A->T' `
    ($retiro.disponibilidadNueva -eq 'T' -and $retiro.resultado -eq 'RETIRAR_DEL_MERCADO') `
    ($retiro | ConvertTo-Json -Compress)

Write-Host "`n== 11. Renovacion: nunca una ventana falsa de disponibilidad ==" -ForegroundColor Cyan
$d = MontarOperacion 'DISPD'
$contratoD=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$d.IdSolicitud;incidencias="Borrador $($d.Marca)"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
$idD = [long]$contratoD.id
$formalizadoD=FormalizarAlquilerC5DePrueba -IdEncargo $d.IdCaptacion -IdOportunidad $d.IdOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 2000 -Moneda 'PEN' -Inicio $contratoD.fechaInicioContrato -Fin $contratoD.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $idD
Api POST "/contratos/$idD/activar" $agente.token @{ motivo = 'Entrega' } | Out-Null
$sucesor = Invoke-ComandoC2DePrueba POST "/contratos/$idD/renovar" $agente.token @{
    fechaInicioContrato = $hoyFecha.AddDays(400).ToString('yyyy-MM-dd')
    fechaFinContrato = $hoyFecha.AddDays(760).ToString('yyyy-MM-dd')
    rentaContractual = 2200; moneda = 'PEN'; motivo = 'Renovacion anual'
} ('e2e-renovacion-'+[guid]::NewGuid().ToString('N'))
$idSucesor = [long]$sucesor.id
Check 'preparar renovación no acredita firma ni termina al anterior' ($sucesor.estadoContrato -eq 'P' -and (Invoke-ControlLocalE2ESql "select estado_contrato from contrato_alquiler where id_contrato_alquiler=$idD") -eq 'V') 'preparación de sucesor'
$renovada=FormalizarAlquilerC5DePrueba -IdEncargo $d.IdCaptacion -IdOportunidad $d.IdOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 2200 -Moneda 'PEN' -Inicio $sucesor.fechaInicioContrato -Fin $sucesor.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $idSucesor
Check 'el contrato anterior queda RENOVADO' `
    ((Invoke-ControlLocalE2ESql "select estado_contrato from contrato_alquiler where id_contrato_alquiler=$idD") -eq 'R') 'anterior'
Check 'el sucesor nace vivo' `
    ((Invoke-ControlLocalE2ESql "select estado_contrato from contrato_alquiler where id_contrato_alquiler=$idSucesor") -in @('D','V')) 'sucesor'
Check 'el local permanece ALQUILADO durante toda la renovacion' `
    ((DisponibilidadDe $d.IdLocal) -eq 'A') (DisponibilidadDe $d.IdLocal)
Check 'nunca hay dos contratos vivos sobre la misma propiedad' `
    ((Invoke-ControlLocalE2ESql "select count(*) from contrato_alquiler where id_propiedad=$($d.IdLocal) and estado_contrato in ('D','V')") -eq '1') `
    'dos contratos vivos'
# Un contrato RENOVADO no admite revision: hay continuidad contractual.
$revisarRenovado = ApiError POST "/contratos/$idD/revision-disponibilidad" $broker.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'Intento de liberar con sucesor vivo'
}
Check 'revisar el contrato RENOVADO se rechaza' `
    ($revisarRenovado.codigo -eq 400) "codigo=$($revisarRenovado.codigo) error=$($revisarRenovado.error)"
# Y aunque el anterior llegara a un estado revisable, el sucesor vivo lo impide.
Invoke-ControlLocalE2ESql "update contrato_alquiler set estado_contrato='F' where id_contrato_alquiler=$idD" | Out-Null
$conSucesorVivo = ApiError POST "/contratos/$idD/revision-disponibilidad" $broker.token @{
    resultado = 'VOLVER_AL_MERCADO'; motivo = 'Intento con sucesor vigente'
}
Check 'con un sucesor vivo no se puede liberar el local' `
    ($conSucesorVivo.codigo -eq 400 -and $conSucesorVivo.error -match 'ocupado por otro contrato') `
    "codigo=$($conSucesorVivo.codigo) error=$($conSucesorVivo.error)"
Check 'y el local sigue ALQUILADO' ((DisponibilidadDe $d.IdLocal) -eq 'A') (DisponibilidadDe $d.IdLocal)
Check 'no quedo ninguna revision registrada para ese contrato' `
    ((Invoke-ControlLocalE2ESql "select count(*) from revision_disponibilidad where id_contrato_alquiler=$idD") -eq '0') 'revision fantasma'

Write-Host "`n== Resultado: $ok OK, $fail fallas ==" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
