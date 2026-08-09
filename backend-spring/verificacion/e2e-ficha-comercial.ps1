# =====================================================================
# E2E de E3 ficha comercial contra el API v2.
#
# Verifica los cuatro GET congelados, la carga inicial parcial, las once
# secciones distintas, aliases y limites de paginacion, privacidad por rol,
# alcance del broker, aislamiento multitenant y metodos no permitidos.
#
# Contrato: docs/ai/contrato-congelado-e3-ficha-comercial.md
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite ficha-comercial
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
        TimeoutSec = 30
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
        if ($null -eq $respuesta) {
            return @{ codigo = -1; error = $PSItem.Exception.Message }
        }
        $codigo = [int]$respuesta.StatusCode
        $texto = ''
        try {
            $lector = New-Object IO.StreamReader($respuesta.GetResponseStream())
            $texto = $lector.ReadToEnd()
            $lector.Close()
        } catch { $texto = '' }
        $mensaje = $texto
        try { $mensaje = ($texto | ConvertFrom-Json).error } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

function NombreSecciones($sections) {
    ($sections.psobject.Properties.Name -join ',')
}

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$fechaCaptacion = $hoyFecha.AddDays(-20).ToString('yyyy-MM-dd')
$fechaVisita = $hoyFecha.AddDays(-2).ToString('yyyy-MM-dd')
$fechaInicio = $hoyFecha.AddDays(30).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login y actores ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{
    usuario = 'vmora'; contrasena = 'Agente2026'
}
$otroAgente = Api POST '/auth/login' $null @{
    usuario = 'ltorres'; contrasena = 'Agente2026'
}
$broker = Api POST '/auth/login' $null @{
    usuario = 'rsalas'; contrasena = 'Broker2026'
}
$brokerAjeno = Api POST '/auth/login' $null @{
    usuario = 'psoto'; contrasena = 'Broker2026'
}
# V37: la sesion del TENANT_ADMIN nace CAPADA hasta enrolar su segundo factor.
# El helper vive en e2e-context.ps1 y lo comparten todas las suites que actuan
# como administrador.
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login del agente responsable' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del agente ajeno' ($otroAgente.rol -eq 'AGENTE') $otroAgente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del broker ajeno' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login del administrador' ($admin.rol -eq 'ADMIN') $admin.rol

Write-Host "`n== 2. Fixture transversal identificable de E3 ==" -ForegroundColor Cyan
$propietario = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'
    tipoDocumento = 'D'
    numeroDocumento = "83$sufijo"
    nombre = "Propietario E3 $sufijo"
    telefono = '987300001'
    correo = "propietario.e3.$sufijo@test.local"
    consentimientoUsoDato = $true
    estado = 'A'
}
$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'
    tipoDocumento = 'D'
    numeroDocumento = "84$sufijo"
    nombre = "Cliente E3 $sufijo"
    telefono = '987300002'
    correo = "cliente.e3.$sufijo@test.local"
    rubroComercial = 'Retail E3'
    consentimientoContacto = $true
    consentimientoUsoDato = $true
    estado = 'A'
}
$local = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-E3-$sufijo"
    direccion = "Av. Ficha E3 $sufijo"
    distrito = 'Miraflores'
    metraje = 140
    precioReferencial = 7200
    monedaReferencial = 'PEN'
    rubroPermitido = 'Retail E3'
    idPropietario = $propietario.id
    estadoPublicacion = 'P'
}
$captacion = Api POST '/captaciones' $agente.token @{
    codigoCaptacion = "CAP-E3-$sufijo"
    fechaCaptacion = $fechaCaptacion
    fechaInicioVigencia = $fechaCaptacion
    fechaFinVigencia = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')
    comisionPactada = 100
    observaciones = "Fixture ficha E3 $sufijo"
    idLocal = $local.id
    motivoOperacion = 'A'
    urgencia = 2
    exclusividad = $true
}
$captacion = Api POST "/captaciones/$($captacion.id)/decision" $broker.token @{
    accion = 'A'; observacion = 'Aprobada para verificar la ficha E3.'
}

Check 'se crea el propietario E3' ($propietario.id -gt 0) "id=$($propietario.id)"
Check 'se crea el cliente E3' ($cliente.id -gt 0) "id=$($cliente.id)"
Check 'se crea el local E3' ($local.id -gt 0) "id=$($local.id)"
Check 'se activa la captacion E3' ($captacion.estado -eq 'A') $captacion.estado

$idPropietario = [long]$propietario.id
$idCliente = [long]$cliente.id
$idCaptacion = [long]$captacion.id
$idLocal = [long]$local.id
$idAgente = [long]$agente.idDominio

Sql @"
insert into requerimiento_cliente (
    organizacion_id, id_rol_cliente, rubro, tipo_inmueble,
    renta_min, renta_max, moneda, metraje_min, metraje_max,
    estado, observaciones, fecha_creacion, fecha_actualizacion
)
select o.id_organizacion, $idCliente, 'Rubro E3 ' || n, 'LOCAL_COMERCIAL',
       3000 + n, 9000 + n, 'PEN', 70 + n, 180 + n,
       case when n=1 then 'A' else 'P' end,
       'Requerimiento paginado E3 $sufijo',
       now() - (n || ' days')::interval,
       now() - (n || ' days')::interval
from organizacion o cross join generate_series(1, 10) n
where o.codigo='BROX_LEGACY';

insert into prospeccion (
    organizacion_id, codigo_prospeccion, fecha_registro, estado,
    resultado_propuesta, fecha_contacto, fecha_reunion, fecha_propuesta,
    observaciones, id_propiedad, id_rol_agente, id_captacion
)
select id_organizacion, 'PRO-E3-$sufijo', now() - interval '21 days', 'T',
       'A', date '$fechaCaptacion', date '$fechaCaptacion' + 2,
       date '$fechaCaptacion' + 4, 'Prospeccion E3 $sufijo',
       $idLocal, $idAgente, $idCaptacion
from organizacion where codigo='BROX_LEGACY';
"@ | Out-Null

$idProspeccion = [long](Sql "select id_prospeccion from prospeccion where organizacion_id=(select id_organizacion from organizacion where codigo='BROX_LEGACY') and codigo_prospeccion='PRO-E3-$sufijo'")

$oportunidad = Api POST '/oportunidades' $agente.token @{
    idCliente = $idCliente; idCaptacion = $idCaptacion
    observaciones = "Oportunidad E3 $sufijo"
}
$idOportunidad = [long]$oportunidad.id

Api POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad; canalContacto = 'W'; resultado = 'INTERESADO'
    observaciones = "Interaccion E3 $sufijo"
} | Out-Null

$visita = Api POST '/visitas' $agente.token @{
    idOportunidad = $idOportunidad; fechaVisita = $fechaVisita; horaVisita = '10:30'
    observaciones = "Visita E3 $sufijo"
}
Api PATCH "/visitas/$($visita.id)/realizar" $agente.token $null | Out-Null
Api PATCH "/visitas/$($visita.id)/resultado" $agente.token @{
    resultado = 'INTERESADO'; observaciones = "Interes confirmado E3 $sufijo"
    nivelInteres = 5; objecionPrincipal = 'O'; opinionPrecio = 'J'; proximaAccion = 'S'
} | Out-Null

$solicitud = Api POST '/solicitudes' $agente.token @{
    codigoSolicitud = "SOL-E3-$sufijo"; fechaRegistro = $fechaVisita
    idOportunidad = $idOportunidad; montoPropuesto = 7100; moneda = 'PEN'; plazoMeses = 24
    fechaInicio = $fechaInicio; formaPago = 'TRANSFERENCIA'; mesesGarantia = 2; mesesAdelanto = 1
    fechaVigenciaOferta = $hoyFecha.AddDays(20).ToString('yyyy-MM-dd')
    observaciones = "Solicitud E3 $sufijo"
}
$idSolicitud = [long]$solicitud.id
Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null | Out-Null
Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; idSolicitud = $idSolicitud
    observaciones = "Expediente E3 aprobado $sufijo"
} | Out-Null

$contrato = Api POST '/contratos' $agente.token @{
    idSolicitud = $idSolicitud; fechaCierre = $hoy; estadoContrato = 'V'
    incidencias = "Contrato vigente E3 $sufijo"
}
$idContrato = [long]$contrato.id

$conteosFixture = Sql @"
select
  (select count(*) from requerimiento_cliente where id_rol_cliente=$idCliente) || '|' ||
  (select count(*) from prospeccion where id_prospeccion=$idProspeccion) || '|' ||
  (select count(*) from oportunidad_comercial where id_oportunidad=$idOportunidad) || '|' ||
  (select count(*) from solicitud_alquiler where id_solicitud=$idSolicitud) || '|' ||
  (select count(*) from contrato_alquiler where id_contrato_alquiler=$idContrato) || '|' ||
  (select count(*) from comision_liquidacion where id_contrato_alquiler=$idContrato)
"@
Check 'el fixture cubre demanda, oferta, cierre y liquidacion' `
    ($conteosFixture -eq '10|1|1|1|1|1') $conteosFixture
$cascadaE3 = Sql @"
select p.disponibilidad_comercial || '|' || c.estado || '|' ||
       (select count(*) from publicacion pub where pub.id_propiedad=p.id_propiedad and pub.estado='C')
from propiedad p join captacion c on c.id_propiedad=p.id_propiedad
where p.id_propiedad=$idLocal and c.id_captacion=$idCaptacion
"@
Check 'POST /contratos ejecuta la cascada E3 (local A alquilado, captacion C, publicacion C)' `
    ($cascadaE3 -eq 'A|C|1') $cascadaE3

Write-Host "`n== 3. Ficha completa del cliente y carga parcial ==" -ForegroundColor Cyan
$fichaClienteAgente = Api GET "/clientes/$idCliente/ficha-comercial?page_size=3&tamano=1" $agente.token $null
$fichaClienteAdmin = Api GET "/clientes/$idCliente/ficha-comercial" $admin.token $null
$fichaClienteBroker = Api GET "/clientes/$idCliente/ficha-comercial" $broker.token $null
$ordenCliente = NombreSecciones $fichaClienteAgente.sections

Check 'la cabecera identifica al cliente' `
    ($fichaClienteAgente.cliente.id -eq $idCliente -and $fichaClienteAgente.cliente.nombre -eq "Cliente E3 $sufijo") `
    "$($fichaClienteAgente.cliente.id)|$($fichaClienteAgente.cliente.nombre)"
Check 'detecta requerimiento activo' $fichaClienteAgente.requerimientoActivo $fichaClienteAgente.requerimientoActivo
Check 'solo el agente recibe el CTA congelado' `
    ($fichaClienteAgente.ctaRuta -eq "/oportunidad-form?clienteId=$idCliente" -and
     $fichaClienteAdmin.ctaRuta -eq '' -and $fichaClienteBroker.ctaRuta -eq '') `
    "$($fichaClienteAgente.ctaRuta)|$($fichaClienteAdmin.ctaRuta)|$($fichaClienteBroker.ctaRuta)"
Check 'las ocho secciones conservan orden' `
    ($ordenCliente -eq 'requerimientos,propiedades,oportunidades,interacciones,visitas,solicitudes,cierres,agentes') `
    $ordenCliente
Check 'page_size gana a tamano en la ficha completa' `
    ($fichaClienteAgente.sections.requerimientos.pageSize -eq 3 -and
     $fichaClienteAgente.sections.requerimientos.items.Count -eq 3 -and
     $fichaClienteAgente.sections.requerimientos.totalRecords -eq 10) `
    ($fichaClienteAgente.sections.requerimientos | ConvertTo-Json -Compress)
Check 'las otras secciones del cliente quedan pendientes' `
    ($fichaClienteAgente.sections.propiedades.totalRecords -eq -1 -and
     $fichaClienteAgente.sections.propiedades.page -eq 0 -and
     $fichaClienteAgente.sections.propiedades.items.Count -eq 0) `
    ($fichaClienteAgente.sections.propiedades | ConvertTo-Json -Compress)

Write-Host "`n== 4. Once secciones distintas y paginacion ==" -ForegroundColor Cyan
$seccionesCliente = @('requerimientos', 'propiedades', 'oportunidades', 'interacciones',
    'visitas', 'solicitudes', 'cierres', 'agentes')
foreach ($seccion in $seccionesCliente) {
    $respuesta = Api GET "/clientes/$idCliente/ficha-comercial/$seccion" $admin.token $null
    Check "cliente/$seccion responde con filas" `
        ($respuesta.section -eq $seccion -and $respuesta.totalRecords -ge 1 -and $respuesta.items.Count -ge 1) `
        "$($respuesta.section)|$($respuesta.totalRecords)|$($respuesta.items.Count)"
}

$normalizada = Api GET "/clientes/$idCliente/ficha-comercial/requerimientos?page=0&pagina=2&page_size=99&tamano=1" $admin.token $null
$aliasEspanol = Api GET "/clientes/$idCliente/ficha-comercial/requerimientos?pagina=2&tamano=1" $admin.token $null
Check 'page y page_size ganan y se limitan a 1..8' `
    ($normalizada.page -eq 1 -and $normalizada.pageSize -eq 8 -and $normalizada.items.Count -eq 8) `
    "$($normalizada.page)|$($normalizada.pageSize)|$($normalizada.items.Count)"
Check 'pagina y tamano funcionan como aliases' `
    ($aliasEspanol.page -eq 2 -and $aliasEspanol.pageSize -eq 1 -and $aliasEspanol.items.Count -eq 1) `
    "$($aliasEspanol.page)|$($aliasEspanol.pageSize)|$($aliasEspanol.items.Count)"

$seccionesPropietario = @('locales', 'prospecciones', 'captaciones', 'oportunidades',
    'solicitudes', 'cierres', 'agentes')
foreach ($seccion in $seccionesPropietario) {
    $respuesta = Api GET "/propietarios/$idPropietario/ficha-comercial/$seccion" $admin.token $null
    Check "propietario/$seccion responde con filas" `
        ($respuesta.section -eq $seccion -and $respuesta.totalRecords -ge 1 -and $respuesta.items.Count -ge 1) `
        "$($respuesta.section)|$($respuesta.totalRecords)|$($respuesta.items.Count)"
}

Write-Host "`n== 5. Ficha completa del propietario y carga parcial ==" -ForegroundColor Cyan
$fichaPropietario = Api GET "/propietarios/$idPropietario/ficha-comercial?page_size=3&tamano=1" $agente.token $null
$ordenPropietario = NombreSecciones $fichaPropietario.sections
Check 'la cabecera identifica al propietario' `
    ($fichaPropietario.propietario.id -eq $idPropietario -and
     $fichaPropietario.propietario.nombre -eq "Propietario E3 $sufijo") `
    "$($fichaPropietario.propietario.id)|$($fichaPropietario.propietario.nombre)"
Check 'cantidadLocales conserva el cero legacy' ($fichaPropietario.propietario.cantidadLocales -eq 0) $fichaPropietario.propietario.cantidadLocales
Check 'las siete secciones conservan orden' `
    ($ordenPropietario -eq 'locales,prospecciones,captaciones,oportunidades,solicitudes,cierres,agentes') `
    $ordenPropietario
Check 'locales carga la primera pagina' `
    ($fichaPropietario.sections.locales.totalRecords -eq 1 -and
     $fichaPropietario.sections.locales.items.Count -eq 1 -and
     $fichaPropietario.sections.locales.pageSize -eq 3) `
    ($fichaPropietario.sections.locales | ConvertTo-Json -Compress)
Check 'prospecciones resume total sin cargar items' `
    ($fichaPropietario.sections.prospecciones.totalRecords -ge 1 -and
     $fichaPropietario.sections.prospecciones.page -eq 1 -and
     $fichaPropietario.sections.prospecciones.items.Count -eq 0) `
    ($fichaPropietario.sections.prospecciones | ConvertTo-Json -Compress)
Check 'captaciones resume total sin cargar items' `
    ($fichaPropietario.sections.captaciones.totalRecords -eq 1 -and
     $fichaPropietario.sections.captaciones.items.Count -eq 0) `
    ($fichaPropietario.sections.captaciones | ConvertTo-Json -Compress)
Check 'cierres queda como marcador pendiente' `
    ($fichaPropietario.sections.cierres.totalRecords -eq -1 -and
     $fichaPropietario.sections.cierres.page -eq 0) `
    ($fichaPropietario.sections.cierres | ConvertTo-Json -Compress)

Write-Host "`n== 6. Privacidad y alcance por rol ==" -ForegroundColor Cyan
$oportunidadPropia = Api GET "/clientes/$idCliente/ficha-comercial/oportunidades" $agente.token $null
$agentesPropios = Api GET "/clientes/$idCliente/ficha-comercial/agentes" $agente.token $null
$requerimientosAjenos = Api GET "/clientes/$idCliente/ficha-comercial/requerimientos" $otroAgente.token $null
$oportunidadesAjenas = Api GET "/clientes/$idCliente/ficha-comercial/oportunidades" $otroAgente.token $null
$agentesAjenos = Api GET "/clientes/$idCliente/ficha-comercial/agentes" $otroAgente.token $null
$oportunidadesBroker = Api GET "/clientes/$idCliente/ficha-comercial/oportunidades" $broker.token $null
$agentesBroker = Api GET "/clientes/$idCliente/ficha-comercial/agentes" $broker.token $null

Check 'el agente ve solo su oportunidad y su nombre se oculta' `
    ($oportunidadPropia.totalRecords -eq 1 -and $oportunidadPropia.items[0].agente -eq '-') `
    "$($oportunidadPropia.totalRecords)|$($oportunidadPropia.items[0].agente)"
Check 'la seccion agentes queda vacia para AGENTE' `
    ($agentesPropios.totalRecords -eq 0 -and $agentesPropios.items.Count -eq 0) `
    "$($agentesPropios.totalRecords)|$($agentesPropios.items.Count)"
Check 'otro agente puede abrir la ficha y ve requerimientos sin dueno' `
    ($requerimientosAjenos.totalRecords -eq 10) $requerimientosAjenos.totalRecords
Check 'otro agente no ve historia ajena ni agentes' `
    ($oportunidadesAjenas.totalRecords -eq 0 -and $agentesAjenos.totalRecords -eq 0) `
    "$($oportunidadesAjenas.totalRecords)|$($agentesAjenos.totalRecords)"
Check 'el broker supervisor ve la historia de su equipo' `
    ($oportunidadesBroker.totalRecords -eq 1 -and $agentesBroker.totalRecords -eq 1) `
    "$($oportunidadesBroker.totalRecords)|$($agentesBroker.totalRecords)"

$otroAgentePropietario = Api GET "/propietarios/$idPropietario/ficha-comercial" $otroAgente.token $null
Check 'otro agente puede abrir propietario pero no ve locales ajenos' `
    ($otroAgentePropietario.sections.locales.totalRecords -eq 0) `
    $otroAgentePropietario.sections.locales.totalRecords

$clienteBrokerAjeno = ApiError GET "/clientes/$idCliente/ficha-comercial" $brokerAjeno.token $null
$propietarioBrokerAjeno = ApiError GET "/propietarios/$idPropietario/ficha-comercial" $brokerAjeno.token $null
Check 'broker sin historia visible recibe 403 en cliente' ($clienteBrokerAjeno.codigo -eq 403) "$($clienteBrokerAjeno.codigo)|$($clienteBrokerAjeno.error)"
Check 'broker sin historia visible recibe 403 en propietario' ($propietarioBrokerAjeno.codigo -eq 403) "$($propietarioBrokerAjeno.codigo)|$($propietarioBrokerAjeno.error)"

Write-Host "`n== 7. Errores congelados y superficie solo lectura ==" -ForegroundColor Cyan
$seccionClienteInvalida = ApiError GET "/clientes/$idCliente/ficha-comercial/desconocida" $admin.token $null
$seccionPropietarioInvalida = ApiError GET "/propietarios/$idPropietario/ficha-comercial/desconocida" $admin.token $null
$clienteSinToken = ApiError GET "/clientes/$idCliente/ficha-comercial" $null $null
$propietarioSinToken = ApiError GET "/propietarios/$idPropietario/ficha-comercial" $null $null
$clienteInexistente = ApiError GET '/clientes/999999999/ficha-comercial' $admin.token $null
$propietarioInexistente = ApiError GET '/propietarios/999999999/ficha-comercial' $admin.token $null
$postNoPermitido = ApiError POST "/clientes/$idCliente/ficha-comercial" $admin.token @{}
$putNoPermitido = ApiError PUT "/propietarios/$idPropietario/ficha-comercial" $admin.token @{}

Check 'seccion invalida de cliente conserva 400 y texto exacto' `
    ($seccionClienteInvalida.codigo -eq 400 -and $seccionClienteInvalida.error -eq 'Seccion de ficha de cliente no valida.') `
    "$($seccionClienteInvalida.codigo)|$($seccionClienteInvalida.error)"
Check 'seccion invalida de propietario conserva 400 y texto exacto' `
    ($seccionPropietarioInvalida.codigo -eq 400 -and $seccionPropietarioInvalida.error -eq 'Seccion de ficha de propietario no valida.') `
    "$($seccionPropietarioInvalida.codigo)|$($seccionPropietarioInvalida.error)"
Check 'la ficha de cliente exige token' ($clienteSinToken.codigo -eq 401) $clienteSinToken.codigo
Check 'la ficha de propietario exige token' ($propietarioSinToken.codigo -eq 401) $propietarioSinToken.codigo
Check 'cliente inexistente responde 404' ($clienteInexistente.codigo -eq 404) "$($clienteInexistente.codigo)|$($clienteInexistente.error)"
Check 'propietario inexistente responde 404' ($propietarioInexistente.codigo -eq 404) "$($propietarioInexistente.codigo)|$($propietarioInexistente.error)"
Check 'POST sobre ficha de cliente responde 405' ($postNoPermitido.codigo -eq 405) $postNoPermitido.codigo
Check 'PUT sobre ficha de propietario responde 405' ($putNoPermitido.codigo -eq 405) $putNoPermitido.codigo

Write-Host "`n== 8. Aislamiento de organizacion y limpieza temporal ==" -ForegroundColor Cyan
$codigoOrg = "E3_ORG_$sufijo"
$idsOtroTenant = $null
try {
    $idsOtroTenant = Sql @"
with org as (
    insert into organizacion (codigo, nombre)
    values ('$codigoOrg', 'Organizacion temporal E3 $sufijo')
    returning id_organizacion
), persona_propietario as (
    insert into persona (
        organizacion_id, tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, correo, estado, consentimiento_uso_dato
    )
    select id_organizacion, 'N', 'D', '91$sufijo',
           'Propietario otro tenant E3', 'prop.otro.e3.$sufijo@test.local', 'A', true
    from org returning id_persona, organizacion_id
), rol_propietario as (
    insert into persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
    select organizacion_id, id_persona, 'PROPIETARIO', current_date
    from persona_propietario returning id_persona_rol
), persona_cliente as (
    insert into persona (
        organizacion_id, tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, correo, estado, consentimiento_uso_dato
    )
    select id_organizacion, 'N', 'D', '92$sufijo',
           'Cliente otro tenant E3', 'cli.otro.e3.$sufijo@test.local', 'A', true
    from org returning id_persona, organizacion_id
), rol_cliente as (
    insert into persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
    select organizacion_id, id_persona, 'CLIENTE', current_date
    from persona_cliente returning id_persona_rol, organizacion_id
), detalle as (
    insert into detalle_cliente (
        id_persona_rol, organizacion_id, rubro_comercial,
        consentimiento_contacto
    )
    select id_persona_rol, organizacion_id, 'Otro tenant E3', true
    from rol_cliente returning id_persona_rol
)
select (select id_persona_rol from rol_propietario) || '|' ||
       (select id_persona_rol from detalle)
"@
    $partes = $idsOtroTenant.Split('|')
    $idPropietarioOtro = [long]$partes[0]
    $idClienteOtro = [long]$partes[1]

    $clienteOtroTenant = ApiError GET "/clientes/$idClienteOtro/ficha-comercial" $admin.token $null
    $propietarioOtroTenant = ApiError GET "/propietarios/$idPropietarioOtro/ficha-comercial" $admin.token $null
    Check 'cliente de otro tenant se comporta como inexistente' `
        ($clienteOtroTenant.codigo -eq 404) "$($clienteOtroTenant.codigo)|$($clienteOtroTenant.error)"
    Check 'propietario de otro tenant se comporta como inexistente' `
        ($propietarioOtroTenant.codigo -eq 404) "$($propietarioOtroTenant.codigo)|$($propietarioOtroTenant.error)"
} finally {
    Sql @"
delete from detalle_cliente
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg');
delete from persona_rol
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg');
delete from persona
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg');
delete from organizacion where codigo='$codigoOrg';
"@ | Out-Null
}
$organizacionTemporal = Sql "select count(*) from organizacion where codigo='$codigoOrg'"
Check 'el fixture temporal del segundo tenant queda eliminado' ($organizacionTemporal -eq '0') $organizacionTemporal

$persistenciaE3 = Sql @"
select
  (select count(*) from persona where correo='propietario.e3.$sufijo@test.local') || '|' ||
  (select count(*) from persona where correo='cliente.e3.$sufijo@test.local') || '|' ||
  (select count(*) from propiedad where codigo='LOC-E3-$sufijo') || '|' ||
  (select count(*) from captacion where codigo_captacion='CAP-E3-$sufijo')
"@
Check 'los registros E3 quedan identificables en desarrollo' ($persistenciaE3 -eq '1|1|1|1') $persistenciaE3

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
