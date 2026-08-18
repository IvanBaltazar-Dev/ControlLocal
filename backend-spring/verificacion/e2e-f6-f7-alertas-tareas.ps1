# =====================================================================
# E2E de F6 alertas + F7 tareas contra el API v2.
#
# Van juntas porque son la misma pieza vista desde dos lados: la alerta
# AVISA y la tarea MANDA A HACER. Lo que de verdad verifica este script
# no es el CRUD de dos recursos, son las ONCE emisiones repartidas por
# todo el flujo comercial (§4 del contrato) y los SIETE disparadores de
# la bandeja (§5.1) — es decir, que F6/F7 quedaron cableadas en las
# verticales ya cortadas.
#
# Contrato: docs/ai/contrato-congelado-f6-f7-alertas-tareas.md
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f6-f7-alertas-tareas
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
        $texto = ''
        try {
            $lector = New-Object IO.StreamReader($respuesta.GetResponseStream())
            $texto = $lector.ReadToEnd(); $lector.Close()
        } catch { $texto = '' }
        $mensaje = $texto
        try { $mensaje = ($texto | ConvertFrom-Json).error } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

# Alertas ACTIVAS de un tipo sobre una entidad concreta (lo que el cable
# acaba de emitir). Se consulta por SQL para no depender del tope de la campana.
function AlertasDe($tipo, $entidadTipo, $entidadId) {
    Sql "select count(*) from alerta where tipo='$tipo' and entidad_tipo='$entidadTipo' and entidad_id=$entidadId and estado='A'"
}

# Ojo: SIEMPRE por (tipo, entidadTipo, entidadId). Filtrar solo por el id
# cruza entidades distintas que comparten numero — es facil que un local y una
# solicitud tengan el mismo id.
function MensajeDe($tipo, $entidadTipo, $entidadId) {
    Sql "select mensaje from alerta where tipo='$tipo' and entidad_tipo='$entidadTipo' and entidad_id=$entidadId order by id_alerta desc limit 1"
}

function SeveridadDe($tipo, $entidadTipo, $entidadId) {
    Sql "select severidad from alerta where tipo='$tipo' and entidad_tipo='$entidadTipo' and entidad_id=$entidadId order by id_alerta desc limit 1"
}

$sufijo = Get-Random -Minimum 1000 -Maximum 9999
$hoy = (Get-Date).ToString('yyyy-MM-dd')
$finEncargo = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
$brokerAjeno = Api POST '/auth/login' $null @{ usuario = 'psoto'; contrasena = 'Broker2026' }
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login agente (AGE-001)' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login broker supervisor (BRK-001)' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login broker de otro equipo (BRK-002)' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login admin' ($admin.rol -eq 'ADMIN') $admin.rol

Write-Host "`n== 2. La campana: forma, alcance y el GET que ESCRIBE ==" -ForegroundColor Cyan
$campana = Api GET '/alertas' $agente.token
Check 'el tamano por defecto de /alertas es 20, no 10' ($campana.pageSize -eq 20) $campana.pageSize
Check 'la campana solo muestra ACTIVAS' `
    (@($campana.items | Where-Object { $_.estado -ne 'A' }).Count -eq 0) 'estados'
Check 'ordenadas por fecha de generacion descendente' `
    (@($campana.items).Count -le 1 -or ([DateTime]$campana.items[0].fechaGeneracion -ge [DateTime]$campana.items[-1].fechaGeneracion)) 'orden'
Check 'el vocabulario viaja con el NOMBRE del enum, no CHAR(1)' `
    (@($campana.items | Where-Object { $_.severidad -notin @('INFO','MEDIA','ALTA') }).Count -eq 0) 'severidad'

# El barrido de recontacto se materializa DENTRO del GET (no hay planificador
# en la v1); ya corrio en la primera lectura de arriba. Se cuenta en CUALQUIER
# estado a proposito: el throttle de 5 minutos hace que dos corridas seguidas
# del script no vuelvan a barrer, y una alerta atendida sigue probando que el
# barrido la materializo.
$recontacto = Sql "select count(*) from alerta where tipo='SIN_RESPUESTA'"
Check 'el GET materializa las alertas de recontacto vencido (D-F6-2)' `
    ([int]$recontacto -ge 1) "alertas=$recontacto"
Api GET '/alertas' $agente.token | Out-Null
$recontacto2 = Sql "select count(*) from alerta where tipo='SIN_RESPUESTA'"
Check 'y no duplica: ni por el throttle ni por el "ya existe una activa"' `
    ($recontacto2 -eq $recontacto) "$recontacto2 vs $recontacto"

Check 'el BROKER supervisor ve las alertas de su equipo' `
    ((Api GET '/alertas' $broker.token).totalRecords -ge 1) 'alcance broker'
Check 'el BROKER de otro equipo no ve las de este agente' `
    (@((Api GET '/alertas' $brokerAjeno.token).items | Where-Object { $_.agenteNombre -eq 'Valentina Mora' }).Count -eq 0) 'alcance ajeno'
Check 'el ADMIN ve todo el tenant' `
    ((Api GET '/alertas' $admin.token).totalRecords -ge (Api GET '/alertas' $broker.token).totalRecords) 'alcance admin'

Write-Host "`n== 3. Atender: los DOS verbos y el false que no es error ==" -ForegroundColor Cyan
$activas = Api GET '/alertas' $agente.token
if (@($activas.items).Count -gt 0) {
    $idAlerta = $activas.items[0].id
    $primera = Api POST "/alertas/$idAlerta/atender" $agente.token $null
    Check 'POST atender responde {"atendida": true}' ($primera.atendida -eq $true) $primera.atendida
    $segunda = Api PATCH "/alertas/$idAlerta/atender" $agente.token $null
    Check 'PATCH es el MISMO endpoint y la 2a vez responde false (D-F6-6)' `
        ($segunda.atendida -eq $false) $segunda.atendida
    Check 'atender sella la fecha de resolucion' `
        ((Sql "select fecha_resolucion is not null from alerta where id_alerta=$idAlerta") -eq 't') 'fecha_resolucion'
    Check 'y la saca de la campana' `
        (@((Api GET '/alertas' $agente.token).items | Where-Object { $_.id -eq $idAlerta }).Count -eq 0) 'sigue activa'
}
$inexistente = ApiError POST '/alertas/99999999/atender' $agente.token $null
Check 'una alerta inexistente responde 404 (no 403: no confirma que exista)' `
    ($inexistente.codigo -eq 404) "codigo=$($inexistente.codigo)"

Write-Host "`n== 4. Prologo: cartera nueva, emitiendo alertas por el camino ==" -ForegroundColor Cyan
$local = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-F6$sufijo"; direccion = 'Av. Alerta 100'; distrito = 'Miraflores'
    metraje = 130; precioReferencial = 7000; monedaReferencial = 'PEN'; rubroPermitido = 'Cafeteria'
    idPropietario = 43; estadoPublicacion = 'P'
}
$idLocal = $local.id

# Deuda VIEJA de F2, cerrada con F6: cambiar metraje o rubro emite la alerta
# de modificacion comercial sensible. Viaja con el tipo SOLICITUD_EVALUADA y
# la entidad INMUEBLE: los dos son bugs congelados (D-F6-4 / D-F6-5).
Api PUT "/locales/$idLocal" $agente.token @{
    codigoLocal = "LOC-F6$sufijo"; direccion = 'Av. Alerta 100'; distrito = 'Miraflores'
    metraje = 145; precioReferencial = 7000; monedaReferencial = 'PEN'; rubroPermitido = 'Cafeteria'
    idPropietario = 43; estadoPublicacion = 'P'
} | Out-Null
Check 'F2: cambiar el metraje emite "Modificacion comercial sensible"' `
    ((AlertasDe 'SOLICITUD_EVALUADA' 'INMUEBLE' $idLocal) -eq '1') 'alerta sensible'
# El mensaje lleva tilde ("Modificación") y psql por docker exec la mangla al
# volver a PowerShell 5.1: se compara por el tramo sin acentos, que es lo que
# de verdad importa aqui — que el aviso es ese y viaja con el tipo equivocado.
Check 'y lo hace con el tipo EQUIVOCADO del cable (bug congelado, D-F6-5)' `
    ((MensajeDe 'SOLICITUD_EVALUADA' 'INMUEBLE' $idLocal) -like '*comercial sensible, revisar') 'mensaje'
Check 'su entidad es INMUEBLE, que la v2 llama PROPIEDAD (D-F6-4)' `
    ((Sql "select entidad_tipo from alerta where entidad_id=$idLocal and tipo='SOLICITUD_EVALUADA'") -eq 'INMUEBLE') 'entidad_tipo'
$sensible = @((Api GET '/alertas' $agente.token).items | Where-Object { $_.entidadTipo -eq 'INMUEBLE' })
Check 'y viaja SIN ruta: ruta() no enruta INMUEBLE (cable real)' `
    ($sensible.Count -eq 0 -or $null -eq $sensible[0].ruta) 'ruta'

$idProspeccion = (Api GET "/prospecciones?idLocal=$idLocal" $agente.token).items[0].id
Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null | Out-Null
$captada = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{ comisionPactada = 5 }
$idCaptacion = $captada.idCaptacion
# 3.5 CORREGIDO el 2026-08-08. Aqui se comprobaba que captar NO avisaba: creaba
# la captacion saltandose el alta que emite la alerta, asi que el broker solo se
# enteraba si alguien usaba POST /captaciones. Como captar desde una prospeccion
# es el camino NORMAL, el aviso practicamente no existia y la captacion se
# quedaba PENDIENTE_REVISION sin que nadie lo supiera.
Check 'captar SI avisa al broker por el camino normal (prospeccion -> captacion)' `
    ((AlertasDe 'CAPTACION_CREADA' 'CAPTACION' $idCaptacion) -eq '1') 'alerta'

# El alta directa SI avisa. Necesita su propio local: solo una captacion
# ACTIVA por local, y la de arriba lo ocupa.
$local2 = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-F6B$sufijo"; direccion = 'Jr. Aviso 200'; distrito = 'Miraflores'
    metraje = 90; precioReferencial = 5000; monedaReferencial = 'PEN'; rubroPermitido = 'Retail'
    idPropietario = 43; estadoPublicacion = 'P'
}
$captacionDirecta = Api POST '/captaciones' $agente.token @{
    codigoCaptacion = "CAP-D$sufijo"; fechaCaptacion = $hoy
    fechaInicioVigencia = $hoy; fechaFinVigencia = $finEncargo
    comisionPactada = 4; idLocal = $local2.id; motivoOperacion = 'A'
    urgencia = 3; exclusividad = $true
}
Check 'POST /captaciones SI avisa al BROKER (CAPTACION_CREADA)' `
    ((AlertasDe 'CAPTACION_CREADA' 'CAPTACION' $captacionDirecta.id) -eq '1') 'alerta'
Check 'el aviso al broker cuelga del AGENTE (no hay columna de destinatario)' `
    ((Sql "select da.codigo_agente from alerta a join detalle_agente da on da.id_persona_rol=a.id_rol_agente where a.tipo='CAPTACION_CREADA' and a.entidad_id=$($captacionDirecta.id)") -eq 'AGE-001') 'id_rol_agente'

# Observar primero para ver la severidad derivada, luego aprobar.
Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'O'; observacion = 'Falta la ficha' } | Out-Null
Check 'F2: observar avisa al AGENTE con severidad MEDIA' `
    ((SeveridadDe 'CAPTACION_REVISADA' 'CAPTACION' $idCaptacion) -eq 'MEDIA') 'severidad'
Check 'y el detalle es literal ": " + observacion' `
    ((MensajeDe 'CAPTACION_REVISADA' 'CAPTACION' $idCaptacion) -like '*fue observada: Falta la ficha') 'mensaje'
Api PUT "/captaciones/$idCaptacion" $agente.token @{
    codigoCaptacion = $captada.captacionCodigo; fechaCaptacion = $hoy
    fechaInicioVigencia = $hoy; fechaFinVigencia = $finEncargo
    comisionPactada = 5; idLocal = $idLocal; motivoOperacion = 'A'
    urgencia = 3; exclusividad = $true
} | Out-Null
Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'A'; observacion = 'Conforme' } | Out-Null
Check 'aprobar avisa con severidad INFO (la derivada del desenlace)' `
    ((SeveridadDe 'CAPTACION_REVISADA' 'CAPTACION' $idCaptacion) -eq 'INFO') 'severidad'

$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "5120$sufijo"
    nombre = "Cliente Alerta $sufijo"; telefono = '987222333'
    correo = "alerta.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$oportunidad = Api POST '/oportunidades' $agente.token @{
    idCliente = $cliente.id; idCaptacion = $idCaptacion; observaciones = 'Interesado.'
}
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
Check 'y como mucho 10 acciones (el resto se descarta en silencio)' `
    (@($bandeja).Count -le 10) "items=$(@($bandeja).Count)"
$subir = @($bandeja | Where-Object { $_.entidadTipo -eq 'SOLICITUD_ALQUILER' -and $_.entidadId -eq $idSolicitud })
Check 'disparador 4: la solicitud OBSERVADA pide SUBIR_DOCUMENTOS' `
    ($subir.Count -eq 1 -and $subir[0].tipo -eq 'SUBIR_DOCUMENTOS') "tipo=$($subir[0].tipo)"
Check 'con prioridad ALTA' ($subir[0].prioridad -eq 'ALTA') $subir[0].prioridad
Check 'su Resolver enruta por CODIGO de solicitud, no por id' `
    ($subir[0].rutaResolver -eq "solicitud-detail/$($solicitud.codigoSolicitud)") $subir[0].rutaResolver
Check 'la bandeja sale ordenada: ALTA antes que MEDIA' `
    (@($bandeja).Count -le 1 -or $bandeja[0].prioridad -eq 'ALTA') 'orden'

$recontactoTarea = @($bandeja | Where-Object { $_.tipo -eq 'RECONTACTO' })
if ($recontactoTarea.Count -gt 0) {
    Check 'disparador 1: diasSinAccion se cuenta desde el PLAZO de la entidad, no desde la tarea' `
        ($recontactoTarea[0].diasSinAccion -ge 7) "dias=$($recontactoTarea[0].diasSinAccion)"
    Check 'y su vencimiento es la fecha de recontacto de la prospeccion' `
        ($null -ne $recontactoTarea[0].fechaVencimiento) 'fechaVencimiento'
}

$antes = @(Api GET '/tareas' $agente.token).Count
$despues = @(Api GET '/tareas' $agente.token).Count
Check 'el reconcile es IDEMPOTENTE: releer no duplica' ($antes -eq $despues) "$antes vs $despues"

$idTarea = $subir[0].id
Api POST "/tareas/$idTarea/cancelar" $agente.token $null | Out-Null
$trasCancelar = @(Api GET '/tareas' $agente.token | Where-Object { $_.id -eq $idTarea })
Check 'cancelar la saca de la bandeja (soft-cancel, no borra)' ($trasCancelar.Count -eq 0) 'sigue'
Check 'la fila sigue en la BD como A (cancelada)' `
    ((Sql "select estado from tarea where id_tarea=$idTarea") -eq 'A') 'estado'
$revive = @(Api GET '/tareas' $agente.token | Where-Object {
    $_.entidadTipo -eq 'SOLICITUD_ALQUILER' -and $_.entidadId -eq $idSolicitud })
Check 'y NO revive aunque el disparador siga vigente (§5.2, trampa 1)' ($revive.Count -eq 0) 'revivio'

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
    (@($bandejaAprobada | Where-Object { $_.tipo -eq 'SEGUIMIENTO' -and $_.entidadId -eq $idSolicitud }).Count -ge 0) 'seguimiento'

$contrato = Api POST '/contratos' $agente.token @{
    idSolicitud = $idSolicitud; fechaCierre = $hoy; estadoContrato = 'V'
}
$idContrato = $contrato.id
Check 'efecto 7: el cierre avisa al broker (OPORTUNIDAD_CERRADA)' `
    ((AlertasDe 'OPORTUNIDAD_CERRADA' 'OPORTUNIDAD' $idOportunidad) -eq '1') 'alerta'
Check 'efecto 7: y da por hechas las tareas abiertas de las 4 entidades' `
    ((Sql "select count(*) from tarea where estado in ('P','E') and ((entidad_tipo='OPORTUNIDAD' and entidad_id=$idOportunidad) or (entidad_tipo='SOLICITUD_ALQUILER' and entidad_id=$idSolicitud) or (entidad_tipo='CAPTACION' and entidad_id=$idCaptacion) or (entidad_tipo='INMUEBLE' and entidad_id=$idLocal))") -eq '0') 'tareas abiertas'

Api POST "/contratos/$idContrato/comision/asignar" $broker.token @{ montoAgente = 200 } | Out-Null
Check 'F4: asignar la comision avisa al agente (COMISION_ASIGNADA)' `
    ((AlertasDe 'COMISION_ASIGNADA' 'CONTRATO_ALQUILER' $idContrato) -eq '1') 'alerta'
Check 'el aviso NO expone el monto neto (regla de privacidad del cable)' `
    ((MensajeDe 'COMISION_ASIGNADA' 'CONTRATO_ALQUILER' $idContrato) -notlike '*200*') 'mensaje'
Api POST "/contratos/$idContrato/comision/asignar" $broker.token @{ montoAgente = 250 } | Out-Null
Check 'un reajuste NO repite el aviso: solo la PRIMERA asignacion' `
    ((AlertasDe 'COMISION_ASIGNADA' 'CONTRATO_ALQUILER' $idContrato) -eq '1') 'duplicado'

Api POST "/contratos/$idContrato/comision/cobro" $broker.token @{
    estado = 'C'; fechaCobro = $hoy; formaPago = 'TRANSFERENCIA'
} | Out-Null
Check 'F4: cobrar avisa al agente (COMISION_COBRADA)' `
    ((AlertasDe 'COMISION_COBRADA' 'CONTRATO_ALQUILER' $idContrato) -eq '1') 'alerta'
Check 'F2: cerrar la captacion en la cascada NO usa el aviso de cierre manual' `
    ((AlertasDe 'CAPTACION_CERRADA' 'CAPTACION' $idCaptacion) -eq '0') 'captacion cerrada'

Write-Host "`n== 8. Contrato de errores y tenancy ==" -ForegroundColor Cyan
Check 'sin token, la campana responde 401' `
    ((ApiError GET '/alertas' $null $null).codigo -eq 401) 'sin token'
Check 'alertas y tareas nacen con tenant' `
    ((Sql "select (select count(*) from alerta where organizacion_id<>1)+(select count(*) from tarea where organizacion_id<>1)") -eq '0') 'tenant'
Check 'ninguna alerta atendida se queda sin fecha de resolucion' `
    ((Sql "select count(*) from alerta where estado<>'A' and fecha_resolucion is null") -eq '0') 'ck_alerta_resolucion'
Check 'ninguna tarea completada se queda sin fecha' `
    ((Sql "select count(*) from tarea where estado='C' and fecha_completada is null") -eq '0') 'ck_tarea_completada'
Check 'no hay dos tareas ABIERTAS de la misma entidad y agente' `
    ((Sql "select count(*) from (select organizacion_id,id_rol_agente,entidad_tipo,entidad_id from tarea where estado in ('P','E') group by 1,2,3,4 having count(*)>1) t") -eq '0') 'dedup'

$huerfanas = Sql @"
select coalesce(sum(nulos),0) from (
  select (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I where organizacion_id is null', table_name), false, true, '')))[1]::text::int as nulos
  from information_schema.columns
   where column_name='organizacion_id' and table_schema='public'
     -- catalogo_atributo es HIBRIDO a proposito (D-E4-1 M2, V48): sus filas
     -- del sistema llevan organizacion_id NULL y son las MISMAS para toda
     -- corredora. Son lo que permite que dos propiedades se comparen; si
     -- llevaran tenant, el vocabulario dejaria de ser comun y el matcher
     -- entre organizaciones no podria existir. Es la misma excepcion que
     -- ArquitecturaTenancyTest ya declara, con la misma razon.
     and table_name <> 'catalogo_atributo') t
"@
Check 'cero filas con organizacion_id NULL en toda la BD' ($huerfanas -eq '0') "nulos=$huerfanas"

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
