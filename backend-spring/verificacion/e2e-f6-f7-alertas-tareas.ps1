# E2E C1/C2/R27 y obligaciones de campana/tareas C7.
# Montajes actuales; avisos C7 y cierre economico C5 siguen sin certificarse
# por parser ni por estas migraciones de consumidores.
# Uso: Invoke-E2E.ps1 -Suite f6-f7-alertas-tareas
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

# Subida de documentos. Desde el 2026-08-08 es la UNICA via: las de JSON con
# base64 y por trozos se borraron al descongelar el contrato (existian por un
# bug del cliente .NET del Blazor, que ya no existe).
function ApiBinario($ruta, $token, $bytes) {
    Invoke-RestMethod -Method POST -Uri "$base$ruta" -Headers @{ Authorization = "Bearer $token" } `
        -Body $bytes -ContentType 'application/octet-stream' -TimeoutSec 30
}

function ApiError($metodo, $ruta, $token, $cuerpo) {
    try {
        Api $metodo $ruta $token $cuerpo | Out-Null
        return @{ codigo = 0; error = '(la llamada no fallo)' }
    } catch {
        $respuesta = $PSItem.Exception.Response
        if ($null -eq $respuesta) { return @{ codigo = -1; error = $PSItem.Exception.Message } }
        $codigo = [int]$respuesta.StatusCode
        $texto = LeerErrorHttpDeFixture $PSItem
        $mensaje = 'Respuesta HTTP sin mensaje JSON legible.'
        try { $jsonError = $texto | ConvertFrom-Json; if($jsonError.error -is [string]){$mensaje=$jsonError.error} } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}



# Alertas ACTIVAS de un tipo sobre una entidad concreta (lo que el cable
# acaba de emitir). Se consulta por SQL para no depender del tope de la campana.
function AlertasDe($tipo, $entidadTipo, $entidadId) {
    Invoke-ControlLocalE2ESql "select count(*) from alerta where organizacion_id=$($fixtureE2E.organizacion) and tipo='$tipo' and entidad_tipo='$entidadTipo' and entidad_id=$entidadId and estado='A'"
}

# Ojo: SIEMPRE por (tipo, entidadTipo, entidadId). Filtrar solo por el id
# cruza entidades distintas que comparten numero - es facil que un local y una
# solicitud tengan el mismo id.
function MensajeDe($tipo, $entidadTipo, $entidadId) {
    Invoke-ControlLocalE2ESql "select mensaje from alerta where organizacion_id=$($fixtureE2E.organizacion) and tipo='$tipo' and entidad_tipo='$entidadTipo' and entidad_id=$entidadId order by id_alerta desc limit 1"
}

function SeveridadDe($tipo, $entidadTipo, $entidadId) {
    Invoke-ControlLocalE2ESql "select severidad from alerta where organizacion_id=$($fixtureE2E.organizacion) and tipo='$tipo' and entidad_tipo='$entidadTipo' and entidad_id=$entidadId order by id_alerta desc limit 1"
}

$sufijo = Get-Random -Minimum 1000 -Maximum 9999
$hoy = (Get-Date).ToString('yyyy-MM-dd')
$finEncargo = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$brokerAjeno = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login agente (AGE-001)' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login broker supervisor (BRK-001)' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login broker de otro equipo (BRK-002)' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login admin' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

Write-Host "`n== R27. Intento real TEST y fecha elegida expresamente ==" -ForegroundColor Cyan
$propietario=(NuevoPropietarioC2DePrueba -Token $agente.token -Nombre 'Titular TEST F6').id
$localR27=NuevoInmuebleSinEncargo -Token $agente.token -Direccion 'Jr. R27 TEST 100' -IdPropietario $propietario -LocalIndependienteTest
$prospR27=Api POST '/prospecciones' $agente.token @{
 idLocal=$localR27.id;observaciones='Prospeccion TEST para recontacto.'
 destinatarioDeclarado=@{tipo='PROPIETARIO';nombre='Propietario declarado TEST R27';telefono='955222333';correo='r27-test@example.invalid';fuente='Declaracion explicita del escenario TEST.'}
}
$revisionesR27=@(Api GET "/prospecciones/$($prospR27.id)/seguimientos" $agente.token)
if($revisionesR27.Count -ne 1 -or $null -eq $revisionesR27[0].contactoDeclarado){throw 'El productor no entrego el destinatario declarado unico del caso TEST.'}
$destinoR27=$revisionesR27[0].contactoDeclarado
$llamada=NuevaInteraccionC4DePrueba -Vinculos @(@{tipo='PROSPECCION';referencia=$prospR27.id}) `
 -Resumen 'Llamada telefonica saliente TEST no respondida.' -Fuente 'Hecho explicito del escenario TEST F6 R27.' -Fecha $hoy `
 -Tipo 'LLAMADA' -Canal 'TELEFONO' -Resultado 'SIN_RESPUESTA' `
 -Destinatario @{referenciaDeclarada=$destinoR27.referenciaDeclarada} `
 -Eventos @(@{referencia=('TEST-'+[guid]::NewGuid().ToString('N'));evento='LLAMADA_SALIENTE';momento=@{fecha=$hoy}})
$null=Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $llamada -Clave ('f6-r27-'+[guid]::NewGuid().ToString('N'))
$revisionesR27=@(Api GET "/prospecciones/$($prospR27.id)/seguimientos" $agente.token)
if($revisionesR27.Count -ne 1){throw 'La llamada debe conservar el destinatario/finalidad exactos de R27.'}
$revisionR27=$revisionesR27[0]
$planR27=Invoke-ComandoC2DePrueba POST "/prospecciones/$($prospR27.id)/seguimientos/$($revisionR27.id)/planificacion" $agente.token @{
 versionEsperada=$revisionR27.version;origen='INTENCION_PROFESIONAL';fecha=$hoy;detalle='Recontactar hoy por eleccion explicita del agente TEST.'
} ('f6-r27-plan-'+[guid]::NewGuid().ToString('N'))
$revisionR27=$planR27.resultado
Check 'planificar conserva identidad y usa fecha declarada' ($revisionR27.id -eq $revisionesR27[0].id -and $revisionR27.proximaFecha -eq $hoy) 'R27'
Write-Host "`n== 2. La campana: forma, alcance y el GET que ESCRIBE ==" -ForegroundColor Cyan
$campana = Api GET '/alertas' $agente.token
Check 'el tamano por defecto de /alertas es 20, no 10' ($campana.pageSize -eq 20) $campana.pageSize
Check 'la campana solo muestra ACTIVAS' `
    (@($campana.items | Where-Object { $_.estado -ne 'A' }).Count -eq 0) 'estados'
Check 'ordenadas por fecha de generacion descendente' `
    (@($campana.items).Count -le 1 -or ([DateTime]$campana.items[0].fechaGeneracion -ge [DateTime]$campana.items[-1].fechaGeneracion)) 'orden'
Check 'el vocabulario viaja con el NOMBRE del enum, no CHAR(1)' `
    (@($campana.items | Where-Object { $_.severidad -notin @('INFO','MEDIA','ALTA') }).Count -eq 0) 'severidad'

$recontacto=Invoke-ControlLocalE2ESql "select count(*) from alerta where organizacion_id=$($fixtureE2E.organizacion) and tipo='SIN_RESPUESTA' and entidad_tipo='PROSPECCION' and entidad_id=$($prospR27.id)"
Check 'la fecha R27 elegida materializa un aviso sin inventar siete dias' ($recontacto -eq '1') 'R27'
Api GET '/alertas' $agente.token|Out-Null
$recontacto2=Invoke-ControlLocalE2ESql "select count(*) from alerta where organizacion_id=$($fixtureE2E.organizacion) and tipo='SIN_RESPUESTA' and entidad_tipo='PROSPECCION' and entidad_id=$($prospR27.id)"
Check 'misma causa no duplica aviso al releer' ($recontacto2 -eq $recontacto) 'idempotencia'
Check 'supervisor ve la revision autorizada de su equipo' (@((Api GET '/alertas' $broker.token).items|Where-Object{$_.entidadTipo -eq 'PROSPECCION' -and $_.entidadId -eq $prospR27.id}).Count -eq 1) 'supervisor'
Check 'otro broker no ve la revision del equipo ajeno' (@((Api GET '/alertas' $brokerAjeno.token).items|Where-Object{$_.entidadTipo -eq 'PROSPECCION' -and $_.entidadId -eq $prospR27.id}).Count -eq 0) 'equipo ajeno'
Check 'gobierno no obtiene la campana comercial' ((ApiError GET '/alertas' $admin.token).codigo -eq 403) 'gobierno'
Write-Host "`n== 3. Atender: los DOS verbos y el false que no es error ==" -ForegroundColor Cyan
$activas = Api GET '/alertas' $agente.token
if (@($activas.items).Count -gt 0) {
    $coincidentes=@($activas.items|Where-Object{$_.entidadTipo -eq 'PROSPECCION' -and $_.entidadId -eq $prospR27.id});if($coincidentes.Count -ne 1){throw 'No hay aviso R27 exacto que atender.'};$idAlerta=$coincidentes[0].id
    $primera = Api POST "/alertas/$idAlerta/atender" $agente.token $null
    Check 'POST atender responde {"atendida": true}' ($primera.atendida -eq $true) $primera.atendida
    $segunda = Api PATCH "/alertas/$idAlerta/atender" $agente.token $null
    Check 'PATCH es el MISMO endpoint y la 2a vez responde false (D-F6-6)' `
        ($segunda.atendida -eq $false) $segunda.atendida
    Check 'atender sella la fecha de resolucion' `
        ((Invoke-ControlLocalE2ESql "select fecha_resolucion is not null from alerta where id_alerta=$idAlerta") -eq 't') 'fecha_resolucion'
    Check 'y la saca de la campana' `
        (@((Api GET '/alertas' $agente.token).items | Where-Object { $_.id -eq $idAlerta }).Count -eq 0) 'sigue activa'
}
$inexistente = ApiError POST '/alertas/99999999/atender' $agente.token $null
Check 'una alerta inexistente responde 404 (no 403: no confirma que exista)' `
    ($inexistente.codigo -eq 404) "codigo=$($inexistente.codigo)"

Write-Host "`n== 4. Prologo: cartera nueva, emitiendo alertas por el camino ==" -ForegroundColor Cyan
# Registrar NO es encargar (V75): la propiedad nace sin encargo y se prospecta.
$local = NuevoInmuebleSinEncargo -Token $agente.token -Direccion 'Av. Alerta 100' `
    -IdPropietario $propietario -Metraje 130 -LocalIndependienteTest -Rubro 'Cafeteria' -Descripcion "Prospecto F6 $sufijo"
$idLocal = $local.id

# ---------------------------------------------------------------------
# AQUI HABIA CUATRO COMPROBACIONES, Y LO QUE VERIFICABAN YA NO EXISTE.
#
# Cambiar el metraje o el rubro por `PUT /locales/{id}` emitia la alerta
# "Modificacion comercial sensible" -con el tipo SOLICITUD_EVALUADA y la
# entidad INMUEBLE, dos bugs congelados: D-F6-4 y D-F6-5-. El emisor vivia en
# `LocalComercialService.actualizar`, que el Corte 0A retiro junto con su
# endpoint. `PUT /propiedades/{id}` no lo repuso.
#
# No se reescriben aqui porque no hay a donde: la capacidad se perdio en el
# backend, y devolverla tal cual seria reintroducir los dos bugs a proposito.
# Queda anotada como deuda en la evidencia del corte; reponerla es decidir
# antes con que tipo y con que entidad avisa, que es una decision de producto.
# ---------------------------------------------------------------------

$idProspeccion = NuevaProspeccion -Token $agente.token -IdPropiedad $idLocal `
    -Observaciones "Prospeccion F6 $sufijo"
Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null | Out-Null
$captada = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{
    operacion = 'ALQUILER'; importe = 7000; moneda = 'PEN'; comisionPactada = 5
    tipoComision = 'P'; baseCalculo = 'R'; tratamientoIgv = 'N'; exclusividad = $true
    inicioEncargo = $hoy; finEncargo = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')
}
$idCaptacion = $captada.idCaptacion
# NB01: el borrador privado no notifica; la presentacion real da la entrada
# de revision. La entrega de todos los avisos C7 no se acredita por este montaje.
Check 'captar un borrador no genera un aviso de presentacion inexistente' ((AlertasDe 'CAPTACION_CREADA' 'CAPTACION' $idCaptacion) -eq '0') 'NB01'
$local2=NuevoInmuebleConEncargo -Token $agente.token -Direccion 'Jr. Aviso TEST 200' -IdPropietario $propietario -Metraje 90 -Importe 5000 -Moneda 'PEN' -LocalIndependienteTest
Check 'registro fisico y borrador no se confunden con presentacion a decision' ((AlertasDe 'CAPTACION_CREADA' 'CAPTACION' $local2.idEncargo) -eq '0') 'borrador'
$preparada=PrepararPeticionC2DePrueba -IdEncargo $idCaptacion -TokenResponsable $agente.token -MonedaComision 'PEN' -Exclusividad $true `
 -HitoDevengo 'CONTRATO_ALQUILER' -ParteObligada 'PROPIETARIO' -Beneficiario 'ORGANIZACION' -Exigibilidad 'AL_DEVENGO' -FechaFirma $hoy -EjecucionComision @{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER'}}
$pendientesBroker=Api GET '/captaciones/pendientes?pagina=1&tamano=100' $broker.token
Check 'la presentacion real tiene entrada de revision del broker' (@($pendientesBroker.items|Where-Object{$_.id -eq $idCaptacion}).Count -eq 1) 'peticion'
$encargoAObservar=Api GET "/encargos/$idCaptacion" $broker.token
$propuestaObservacion=Invoke-ComandoC2DePrueba POST "/encargos/peticiones/$($preparada.peticion.id)/decision" $broker.token @{
 actuacion='OBSERVAR';versionPeticion=$preparada.peticion.version;versionEncargo=$encargoAObservar.version;observaciones='Falta la ficha TEST'
} ('f6-observar-'+[guid]::NewGuid().ToString('N'))
$observada=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $propuestaObservacion
Check 'observacion conserva la peticion y su estado explicito' ($observada.id -eq $preparada.peticion.id -and $observada.estado -eq 'OBSERVADA') 'peticion'
# Obligacion de aviso C7 conservada; no se crea por SQL para hacerla verde.
Check 'F6 pendienteC7: observar notifica al agente con severidad MEDIA' ((SeveridadDe 'CAPTACION_REVISADA' 'CAPTACION' $idCaptacion) -eq 'MEDIA') 'avisoC7'
Check 'F6 pendienteC7: el aviso conserva el motivo' ((MensajeDe 'CAPTACION_REVISADA' 'CAPTACION' $idCaptacion) -like '*Falta la ficha TEST*') 'motivo'
$encargoObservado=Api GET "/encargos/$idCaptacion" $agente.token
$reenviada=Invoke-ComandoC2DePrueba POST "/encargos/peticiones/$($observada.id)/reenviar" $agente.token @{
 versionPeticion=$observada.version;presentacion=@{
  documento=$preparada.documento.id;versionEsperada=$encargoObservado.version;partes=$preparada.partes
  firmas=$observada.firma.firmas;respuestaObservaciones='Respuesta explicita del escenario TEST; se mantiene el instrumento presentado.'
 }
} ('f6-reenviar-'+[guid]::NewGuid().ToString('N'))
Check 'reenviar mantiene identidad y crea una nueva revision de presentacion' ($reenviada.id -eq $observada.id -and $reenviada.estado -eq 'PENDIENTE' -and $reenviada.firma.revisionPresentacion -ne $observada.firma.revisionPresentacion) 'reenviar'
$aprobada=AprobarPeticionC2DePrueba -IdEncargo $idCaptacion -IdPeticion $reenviada.id -Documento $preparada.documento -TokenDecisor $broker.token
Check 'F6 pendienteC7: aprobar notifica con severidad INFO' ((SeveridadDe 'CAPTACION_REVISADA' 'CAPTACION' $idCaptacion) -eq 'INFO') 'avisoC7'
$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "5120$sufijo"
    nombre = "Cliente Alerta $sufijo"; telefono = '987222333'
    correo = "alerta.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$oportunidad=NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $cliente.id -Token $agente.token -EvidenciaInteres 'Interes explicito FICTICIO TEST F6; no inferido de comunicacion.'
$idOportunidad = $oportunidad.id

Write-Host "`n== 5. Las emisiones de F4 (solicitud, documentos, evaluacion) ==" -ForegroundColor Cyan
$solicitud = Api POST '/solicitudes' $agente.token @{
    idOportunidad = $idOportunidad; montoPropuesto = 7000; moneda = 'PEN'; plazoMeses = 12
    fechaInicio = '2026-09-01'; formaPago = 'TRANSFERENCIA'; mesesGarantia = 1; mesesAdelanto = 1
}
$idSolicitud = $solicitud.id
$pdf = [Text.Encoding]::UTF8.GetBytes('%PDF-1.4 prueba')
$doc = ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=I&nombreArchivo=dni.pdf" `
    $agente.token $pdf
Check 'subir documento con la solicitud REGISTRADA NO avisa (aun no la evaluan)' `
    ((AlertasDe 'SOLICITUD_DOCUMENTO' 'SOLICITUD_ALQUILER' $idSolicitud) -eq '0') 'alerta'

Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null | Out-Null
Check 'F4: reenviar a evaluacion avisa al broker (SOLICITUD_REENVIADA)' `
    ((AlertasDe 'SOLICITUD_REENVIADA' 'SOLICITUD_ALQUILER' $idSolicitud) -eq '1') 'alerta'

ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=R&nombreArchivo=ruc.pdf" `
    $agente.token $pdf | Out-Null
Check 'ahora SI avisa: el expediente cambio mientras lo evaluaban' `
    ((AlertasDe 'SOLICITUD_DOCUMENTO' 'SOLICITUD_ALQUILER' $idSolicitud) -eq '1') 'alerta'
Check 'y el mensaje nombra el archivo' `
    ((MensajeDe 'SOLICITUD_DOCUMENTO' 'SOLICITUD_ALQUILER' $idSolicitud) -like '*"ruc.pdf"*mientras esta en evaluacion.') 'mensaje'

Api PATCH "/solicitudes/$idSolicitud/documentos/$($doc.id)/revisar" $broker.token @{
    resultado = 'O'; observaciones = 'Ilegible'
} | Out-Null
Check 'F4: observar un documento avisa al agente' `
    ((AlertasDe 'SOLICITUD_DOCUMENTO_REVISADO' 'SOLICITUD_ALQUILER' $idSolicitud) -eq '1') 'alerta'
Check 'el mensaje nombra el TIPO del documento, no el archivo' `
    ((MensajeDe 'SOLICITUD_DOCUMENTO_REVISADO' 'SOLICITUD_ALQUILER' $idSolicitud) -like '*"Documento de identidad"*: Ilegible') 'mensaje'
Api PATCH "/solicitudes/$idSolicitud/documentos/$($doc.id)/revisar" $broker.token @{ resultado = 'C' } | Out-Null
Check 'conformar NO avisa (no hay nada que subsanar)' `
    ((AlertasDe 'SOLICITUD_DOCUMENTO_REVISADO' 'SOLICITUD_ALQUILER' $idSolicitud) -eq '1') 'no duplica'

Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'O'; observaciones = 'Sube el sustento.'; idSolicitud = $idSolicitud
} | Out-Null
Check 'F4: evaluar OBSERVADA avisa con severidad MEDIA' `
    ((SeveridadDe 'SOLICITUD_EVALUADA' 'SOLICITUD_ALQUILER' $idSolicitud) -eq 'MEDIA') 'severidad'
Check 'y el mensaje lleva la DESCRIPCION del resultado, no su codigo' `
    ((MensajeDe 'SOLICITUD_EVALUADA' 'SOLICITUD_ALQUILER' $idSolicitud) -like '*evaluada con resultado Observada.') 'mensaje'

Write-Host "`n== 6. La bandeja del agente (F7) ==" -ForegroundColor Cyan
$bandeja = Api GET '/tareas' $agente.token
# Lista pelada: los elementos son tareas, no hay envoltorio con items/totalRecords.
Check 'GET /tareas devuelve una LISTA PELADA, sin sobre de paginacion' `
    (@($bandeja).Count -eq 0 -or ($null -ne $bandeja[0].tipo -and $null -eq $bandeja[0].items)) 'forma'
# Listado sin tope artificial: la pagina conserva el total autorizado.
$subir = @($bandeja | Where-Object { $_.entidadTipo -eq 'SOLICITUD_ALQUILER' -and $_.entidadId -eq $idSolicitud })
Check 'disparador 4: la solicitud OBSERVADA pide SUBIR_DOCUMENTOS' `
    ($subir.Count -eq 1 -and $subir[0].tipo -eq 'SUBIR_DOCUMENTOS') "tipo=$($subir[0].tipo)"
Check 'con prioridad ALTA' ($subir[0].prioridad -eq 'ALTA') $subir[0].prioridad
Check 'su Resolver enruta por CODIGO de solicitud, no por id' `
    ($subir[0].rutaResolver -eq "solicitud-detail/$($solicitud.codigoSolicitud)") $subir[0].rutaResolver
Check 'la bandeja sale ordenada: ALTA antes que MEDIA' `
    (@($bandeja).Count -le 1 -or $bandeja[0].prioridad -eq 'ALTA') 'orden'

$recontactoTarea=@($bandeja|Where-Object{$_.tipo -eq 'RECONTACTO' -and $_.entidadTipo -eq 'PROSPECCION' -and $_.entidadId -eq $prospR27.id})
Check 'Tarea consume la misma revision vigente de R27' ($recontactoTarea.Count -eq 1 -and $recontactoTarea[0].fechaVencimiento -eq $revisionR27.proximaFecha) 'fecha fuente'
$antes = @(Api GET '/tareas' $agente.token).Count
$despues = @(Api GET '/tareas' $agente.token).Count
Check 'el reconcile es IDEMPOTENTE: releer no duplica' ($antes -eq $despues) "$antes vs $despues"

$idTarea = $subir[0].id
Api POST "/tareas/$idTarea/cancelar" $agente.token $null | Out-Null
$trasCancelar = @(Api GET '/tareas' $agente.token | Where-Object { $_.id -eq $idTarea })
Check 'cancelar la saca de la bandeja (soft-cancel, no borra)' ($trasCancelar.Count -eq 0) 'sigue'
Check 'la fila sigue en la BD como A (cancelada)' `
    ((Invoke-ControlLocalE2ESql "select estado from tarea where id_tarea=$idTarea") -eq 'A') 'estado'
$revive = @(Api GET '/tareas' $agente.token | Where-Object {
    $_.entidadTipo -eq 'SOLICITUD_ALQUILER' -and $_.entidadId -eq $idSolicitud })
Check 'y NO revive aunque el disparador siga vigente (seccion 5.2, trampa 1)' ($revive.Count -eq 0) 'revivio'

$pendientes = Api GET '/tareas/pendientes' $agente.token
Check '/tareas/pendientes SI lleva sobre, con tamano por defecto 5' ($pendientes.pageSize -eq 5) $pendientes.pageSize
Check 'el BROKER no entra a la bandeja (403)' `
    ((ApiError GET '/tareas' $broker.token $null).codigo -eq 403) 'broker'
Check 'el ADMIN tampoco: es el unico recurso sin acceso de admin' `
    ((ApiError GET '/tareas' $admin.token $null).codigo -eq 403) 'admin'
Check 'cancelar una tarea ajena no se permite' `
    ((ApiError POST "/tareas/$idTarea/cancelar" $broker.token $null).codigo -eq 403) 'cancelar ajena'

Write-Host "`n== 7. El cierre: efecto 7 de la cascada de F4 ==" -ForegroundColor Cyan
Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null | Out-Null
Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; observaciones = 'Conforme.'; idSolicitud = $idSolicitud
} | Out-Null
Check 'evaluar APROBADA avisa con severidad INFO' `
    ((SeveridadDe 'SOLICITUD_EVALUADA' 'SOLICITUD_ALQUILER' $idSolicitud) -eq 'INFO') 'severidad'

# Con la solicitud aprobada aparece el disparador 2 (seguimiento del cierre).
$bandejaAprobada = Api GET '/tareas' $agente.token
Check 'disparador 2: una solicitud APROBADA pide SEGUIMIENTO del cierre' `
    (@($bandejaAprobada | Where-Object { $_.tipo -eq 'SEGUIMIENTO' -and $_.entidadTipo -eq 'SOLICITUD_ALQUILER' -and $_.entidadId -eq $idSolicitud }).Count -eq 1) 'seguimiento'

$preparado=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$idSolicitud;incidencias='Preparación TEST de alertas'} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
$formalizado=FormalizarAlquilerC5DePrueba -IdEncargo $idCaptacion -IdOportunidad $idOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta $preparado.rentaMensual -Moneda $preparado.moneda -Inicio $preparado.fechaInicioContrato -Fin $preparado.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $preparado.id
$contrato=$formalizado.contrato
$idContrato = $contrato.id
Check 'efecto 7: el cierre avisa al broker (OPORTUNIDAD_CERRADA)' `
    ((AlertasDe 'OPORTUNIDAD_CERRADA' 'OPORTUNIDAD' $idOportunidad) -eq '1') 'alerta'
Check 'efecto 7: y da por hechas las tareas abiertas de las 4 entidades' `
    ((Invoke-ControlLocalE2ESql "select count(*) from tarea where estado in ('P','E') and ((entidad_tipo='OPORTUNIDAD' and entidad_id=$idOportunidad) or (entidad_tipo='SOLICITUD_ALQUILER' and entidad_id=$idSolicitud) or (entidad_tipo='CAPTACION' and entidad_id=$idCaptacion) or (entidad_tipo='INMUEBLE' and entidad_id=$idLocal))") -eq '0') 'tareas abiertas'

$comision=LeerComisionC5DePrueba $formalizado $broker.token
$asignacion=Api POST '/comisiones/repartos/propuestas' $broker.token @{comision=$comision.id;versionEsperada=$comision.version;montoAgente=200;observacion='Reparto TEST 200'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $asignacion
Check 'F4: asignar la comision avisa al agente (COMISION_ASIGNADA)' `
    ((AlertasDe 'COMISION_ASIGNADA' 'CONTRATO_ALQUILER' $idContrato) -eq '1') 'alerta'
Check 'el aviso NO expone el monto neto (regla de privacidad del cable)' `
    ((MensajeDe 'COMISION_ASIGNADA' 'CONTRATO_ALQUILER' $idContrato) -notlike '*200*') 'mensaje'
$comision=LeerComisionC5DePrueba $formalizado $broker.token
$asignacion=Api POST '/comisiones/repartos/propuestas' $broker.token @{comision=$comision.id;versionEsperada=$comision.version;montoAgente=250;observacion='Reparto TEST 250'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $asignacion
Check 'un reajuste NO repite el aviso: solo la PRIMERA asignacion' `
    ((AlertasDe 'COMISION_ASIGNADA' 'CONTRATO_ALQUILER' $idContrato) -eq '1') 'duplicado'

$comision=LeerComisionC5DePrueba $formalizado $broker.token
$cobro=PrepararCobroC5DePrueba -Contexto $formalizado -Token $broker.token -TokenEvidencia $agente.token -Importe $comision.calculo.cuantia.importe -Moneda $comision.calculo.cuantia.moneda -Fecha $hoy -Referencia 'RECEPCION-EXTERNA-TEST-ALERTAS'
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $cobro
Check 'F4: cobrar avisa al agente (COMISION_COBRADA)' `
    ((AlertasDe 'COMISION_COBRADA' 'CONTRATO_ALQUILER' $idContrato) -eq '1') 'alerta'
Check 'F2: cerrar la captacion en la cascada NO usa el aviso de cierre manual' `
    ((AlertasDe 'CAPTACION_CERRADA' 'CAPTACION' $idCaptacion) -eq '0') 'captacion cerrada'

Write-Host "`n== 8. Contrato de errores y tenancy ==" -ForegroundColor Cyan
Check 'sin token, la campana responde 401' `
    ((ApiError GET '/alertas' $null $null).codigo -eq 401) 'sin token'
Check 'alertas y tareas nacen con tenant' `
    ((Invoke-ControlLocalE2ESql "select (select count(*) from alerta where organizacion_id is null)+(select count(*) from tarea where organizacion_id is null)") -eq '0') 'tenant'
Check 'ninguna alerta atendida se queda sin fecha de resolucion' `
    ((Invoke-ControlLocalE2ESql "select count(*) from alerta where estado<>'A' and fecha_resolucion is null") -eq '0') 'ck_alerta_resolucion'
Check 'ninguna tarea completada se queda sin fecha' `
    ((Invoke-ControlLocalE2ESql "select count(*) from tarea where estado='C' and fecha_completada is null") -eq '0') 'ck_tarea_completada'
Check 'no hay dos tareas ABIERTAS de la misma entidad y agente' `
    ((Invoke-ControlLocalE2ESql "select (select count(*) from (select organizacion_id,id_rol_agente,entidad_tipo,entidad_id from tarea where estado in ('P','E') and not(tipo='RECONTACTO' and marca_causa is not null) group by 1,2,3,4 having count(*)>1) n)+(select count(*) from (select organizacion_id,id_seguimiento_prospeccion from tarea where estado in ('P','E') and tipo='RECONTACTO' and marca_causa is not null group by 1,2 having count(*)>1) r)") -eq '0') 'dedup'

# No escanear catalogos globales como si todas las tablas fueran privadas.
Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
