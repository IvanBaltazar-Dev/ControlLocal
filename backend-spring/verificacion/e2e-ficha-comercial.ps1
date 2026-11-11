# =====================================================================
# E2E de E3 ficha comercial contra el API v2.
#
# Verifica los cuatro GET de Core, la carga inicial parcial, las once
# secciones distintas, aliases y limites de paginacion, privacidad por rol,
# alcance del broker, aislamiento multitenant y metodos no permitidos.
#
# Autoridad: docs/1INF50/BROX-diseno-software.md y
# docs/1INF50/BROX-KAIROS-catalogo-operativo.md (ambito y campos autorizados).
# C4: work/c4-c5-20261030/BROX-ESPECIFICACION-IMPLEMENTACION-C4-C5-2026-10-30.md, secciones1/4/10.
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite ficha-comercial
#             (compatible con Windows PowerShell 5.1 y con pwsh 7)
# =====================================================================
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
        $texto = LeerErrorHttpDeFixture $PSItem
        $mensaje = 'Respuesta HTTP sin mensaje JSON legible.'
        try { $jsonError = $texto | ConvertFrom-Json; if($jsonError.error -is [string]){$mensaje=$jsonError.error} } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
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
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$otroAgente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.ltorres.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$brokerAjeno = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
# V37: la sesion del TENANT_ADMIN nace CAPADA hasta enrolar su segundo factor.
# El helper vive en e2e-context.ps1 y lo comparten todas las suites que actuan
# como administrador.
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login del agente responsable' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del agente ajeno' ($otroAgente.rol -eq 'AGENTE') $otroAgente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del broker ajeno' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login del administrador' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

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
$local = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token -Direccion "Av. Ficha E3 $sufijo" -Codigo "LOC-E3-$sufijo" `
    -IdPropietario $propietario.id -Metraje 140 -Rubro 'Retail E3' `
    -Importe 7200 -Moneda 'PEN' -TipoComision 'P' -BaseCalculo 'R' -ValorComision 1.00 `
    -TratamientoIgv 'N' -InicioEncargo $fechaCaptacion `
    -FinEncargo (Get-Date).AddDays(90).ToString('yyyy-MM-dd') `
    -Descripcion "Fixture ficha E3 $sufijo"
$null = HabilitarEncargoC2DePrueba -IdEncargo $($local.idEncargo) -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato' -EjecucionComision @{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER'}}
# El anuncio, explicito: `POST /locales` lo creaba de rebote con
# `estadoPublicacion` y el alta universal no publica. La cascada del contrato
# tiene que encontrar un anuncio VIVO para poder cerrarlo, que es parte de lo
# que esta suite comprueba.
PublicarAnuncioC6DePrueba -IdEncargo $local.idEncargo -Token $agente.token `
    -Canal 'URBANIA' -Importe 7200 -Moneda 'PEN' -Titulo "Anuncio E3 $sufijo" | Out-Null
$captacion = Api GET "/captaciones/$($local.idEncargo)" $agente.token $null

Check 'se crea el propietario E3' ($propietario.id -gt 0) "id=$($propietario.id)"
Check 'se crea el cliente E3' ($cliente.id -gt 0) "id=$($cliente.id)"
Check 'se crea el local E3' ($local.id -gt 0) "id=$($local.id)"
Check 'se activa la captacion E3' ($captacion.estado -eq 'A') $captacion.estado

$idPropietario = [long]$propietario.id
$idCliente = [long]$cliente.id
$idCaptacion = [long]$captacion.id
$idLocal = [long]$local.id
$idAgente = [long]$agente.idDominio

# Diez demandas propias de TEST por Core, con interesado y responsable reales
# del fixture. El presupuesto mensual total es una declaracion del caso TEST.
for ($n = 1; $n -le 10; $n++) {
    $estadoDemanda = 'A'
    $null = Invoke-ComandoC2DePrueba POST '/requerimientos' $agente.token @{
        interesado = @{ tipo = 'CLIENTE'; idCliente = $idCliente }
        operacion = 'ALQUILER'; tiposInmueble = @('LOCAL_COMERCIAL'); tipoInmueble = 'LOCAL_COMERCIAL'
        rubro = ('Rubro E3 ' + $n); presupuestoMin = (3000 + $n); presupuestoMax = (9000 + $n)
        moneda = 'PEN'; conceptoPresupuesto = 'TOTAL_MENSUAL_OBLIGATORIO'; periodoPresupuesto = 'MENSUAL'
        metrajeMin = (70 + $n); metrajeMax = (180 + $n); estado = $estadoDemanda
        observaciones = "Demanda paginada TEST E3 $sufijo"; finalidad = 'Gestion inmobiliaria TEST explicita'
    } ("e3-demanda-$sufijo-$n")
}

Invoke-ControlLocalE2ESql @"
insert into prospeccion (
    organizacion_id, codigo_prospeccion, fecha_registro, estado,
    resultado_propuesta, fecha_contacto, fecha_reunion, fecha_propuesta,
    observaciones, id_propiedad, id_rol_agente, id_captacion
)
select id_organizacion, 'PRO-E3-$sufijo', now() - interval '21 days', 'T',
       'A', date '$fechaCaptacion', date '$fechaCaptacion' + 2,
       date '$fechaCaptacion' + 4, 'Prospeccion E3 $sufijo',
       $idLocal, $idAgente, $idCaptacion
from organizacion where codigo='$($fixtureE2E.codigo)';
"@ | Out-Null

$idProspeccion = [long](Invoke-ControlLocalE2ESql "select id_prospeccion from prospeccion where organizacion_id=(select id_organizacion from organizacion where codigo='$($fixtureE2E.codigo)') and codigo_prospeccion='PRO-E3-$sufijo'")

$oportunidad = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $idCliente -Token $agente.token `
    -EvidenciaInteres 'Manifestacion expresa FICTICIA de interes del caso TEST E2E C4.' -Observaciones "Oportunidad E3 $sufijo"
$idOportunidad = [long]$oportunidad.id

# Hecho de prueba parcial explicito: no inventa tipo, canal ni interes.
# Se conserva para comprobar la seccion de comunicaciones de esta ficha.
$datosComunicacion = NuevaInteraccionC4DePrueba `
    -Vinculos @(@{ tipo = 'OPORTUNIDAD'; referencia = $idOportunidad }, @{ tipo = 'CLIENTE'; referencia = $idCliente }) `
    -Resumen "Comunicacion E3 $sufijo" -Fuente "Fixture TEST E3 $sufijo" -Fecha $hoy
$comunicacion = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token `
    -Datos $datosComunicacion -Clave "e3-comunicacion-$sufijo"
Check 'el fixture de ficha conserva una identidad comunicativa real' `
    ($comunicacion.resultado.id -gt 0 -and $null -eq $comunicacion.resultado.datos.resultado) `
    "id=$($comunicacion.resultado.id)"

$visita = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $fechaVisita -Hora '10:30' -Token $agente.token -Observaciones "Visita E3 $sufijo"
ResultadoVisitaC4DePrueba -IdVisita $($visita.id) -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $hoy -Desenlace @{
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

$preparado=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$idSolicitud;incidencias="Preparación TEST E3 $sufijo"} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
Check 'preparar no acredita firma ni fecha de cierre' ($preparado.estadoContrato -eq 'P' -and $null -eq $preparado.fechaCierre) 'preparación'
$formalizado=FormalizarAlquilerC5DePrueba -IdEncargo $idCaptacion -IdOportunidad $idOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 7100 -Moneda 'PEN' -Inicio $preparado.fechaInicioContrato -Fin $preparado.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $preparado.id
$contrato=$formalizado.contrato
$idContrato = [long]$contrato.id

$conteosFixture = Invoke-ControlLocalE2ESql @"
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
$cascadaE3 = Invoke-ControlLocalE2ESql @"
select p.disponibilidad_comercial || '|' || c.estado || '|' ||
       (select count(*) from publicacion pub where pub.id_propiedad=p.id_propiedad and pub.estado='C')
from propiedad p join captacion c on c.id_propiedad=p.id_propiedad
where p.id_propiedad=$idLocal and c.id_captacion=$idCaptacion
"@
Check 'POST /contratos ejecuta la cascada E3 (local A alquilado, captacion C, publicacion C)' `
    ($cascadaE3 -eq 'A|C|1') $cascadaE3

Write-Host "`n== 3. Ficha completa del cliente y carga parcial ==" -ForegroundColor Cyan
$fichaClienteAgente = Api GET "/clientes/$idCliente/ficha-comercial?page_size=3&tamano=1" $agente.token $null
$fichaClienteAdmin = ApiError GET "/clientes/$idCliente/ficha-comercial" $admin.token $null
$fichaClienteBroker = Api GET "/clientes/$idCliente/ficha-comercial" $broker.token $null
$ordenCliente = NombreSecciones $fichaClienteAgente.sections

Check 'la cabecera identifica al cliente' `
    ($fichaClienteAgente.cliente.id -eq $idCliente -and $fichaClienteAgente.cliente.nombre -eq "Cliente E3 $sufijo") `
    "$($fichaClienteAgente.cliente.id)|$($fichaClienteAgente.cliente.nombre)"
Check 'detecta requerimiento activo' $fichaClienteAgente.requerimientoActivo $fichaClienteAgente.requerimientoActivo
Check 'solo el agente recibe el CTA congelado' `
    ($fichaClienteAgente.ctaRuta -eq "/oportunidad-form?clienteId=$idCliente" -and
     $fichaClienteAdmin.codigo -eq 403 -and $fichaClienteBroker.ctaRuta -eq '') `
    "$($fichaClienteAgente.ctaRuta)|$($fichaClienteAdmin.codigo)|$($fichaClienteBroker.ctaRuta)"
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
    $tokenSeccion = if ($seccion -eq 'agentes') { $broker.token } else { $agente.token }
    $respuesta = Api GET "/clientes/$idCliente/ficha-comercial/$seccion" $tokenSeccion $null
    Check "cliente/$seccion responde con filas" `
        ($respuesta.section -eq $seccion -and $respuesta.totalRecords -ge 1 -and $respuesta.items.Count -ge 1) `
        "$($respuesta.section)|$($respuesta.totalRecords)|$($respuesta.items.Count)"
}

$normalizada = Api GET "/clientes/$idCliente/ficha-comercial/requerimientos?page=0&pagina=2&page_size=99&tamano=1" $agente.token $null
$aliasEspanol = Api GET "/clientes/$idCliente/ficha-comercial/requerimientos?pagina=2&tamano=1" $agente.token $null
Check 'page y page_size ganan y se limitan a 1..8' `
    ($normalizada.page -eq 1 -and $normalizada.pageSize -eq 8 -and $normalizada.items.Count -eq 8) `
    "$($normalizada.page)|$($normalizada.pageSize)|$($normalizada.items.Count)"
Check 'pagina y tamano funcionan como aliases' `
    ($aliasEspanol.page -eq 2 -and $aliasEspanol.pageSize -eq 1 -and $aliasEspanol.items.Count -eq 1) `
    "$($aliasEspanol.page)|$($aliasEspanol.pageSize)|$($aliasEspanol.items.Count)"

$seccionesPropietario = @('locales', 'prospecciones', 'captaciones', 'oportunidades',
    'solicitudes', 'cierres', 'agentes')
foreach ($seccion in $seccionesPropietario) {
    $tokenSeccion = if ($seccion -eq 'agentes') { $broker.token } else { $agente.token }
    $respuesta = Api GET "/propietarios/$idPropietario/ficha-comercial/$seccion" $tokenSeccion $null
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
Check 'otro agente no recibe Demandas del responsable ajeno' `
    ($requerimientosAjenos.totalRecords -eq 0) $requerimientosAjenos.totalRecords
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
$seccionClienteInvalida = ApiError GET "/clientes/$idCliente/ficha-comercial/desconocida" $agente.token $null
$seccionPropietarioInvalida = ApiError GET "/propietarios/$idPropietario/ficha-comercial/desconocida" $agente.token $null
$clienteSinToken = ApiError GET "/clientes/$idCliente/ficha-comercial" $null $null
$propietarioSinToken = ApiError GET "/propietarios/$idPropietario/ficha-comercial" $null $null
$clienteInexistente = ApiError GET '/clientes/999999999/ficha-comercial' $agente.token $null
$propietarioInexistente = ApiError GET '/propietarios/999999999/ficha-comercial' $agente.token $null
$postNoPermitido = ApiError POST "/clientes/$idCliente/ficha-comercial" $agente.token @{}
$putNoPermitido = ApiError PUT "/propietarios/$idPropietario/ficha-comercial" $agente.token @{}

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
$otroTenant = Initialize-ControlLocalE2EActors -Etiqueta 'contrario'
$actorOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.vmora.correo `
    -Contrasena 'Agente2026' -Organizacion $otroTenant.organizacion -Rol 'AGENTE'
$propietarioOtro = NuevoPropietarioC2DePrueba -Token $actorOtroTenant.token -Nombre 'Propietario ficticio del tenant contrario E3'
$clienteOtro = Api POST '/clientes' $actorOtroTenant.token @{
    tipoPersona = 'N'; tipoDocumento = 'P'; numeroDocumento = ('E3-' + [guid]::NewGuid().ToString('N').Substring(0,20))
    nombre = 'Cliente ficticio del tenant contrario E3'; consentimientoContacto = $true; consentimientoUsoDato = $true
}
Check 'el propio actor extranjero puede leer su contacto de TEST' `
    ((Api GET "/clientes/$($clienteOtro.id)" $actorOtroTenant.token $null).id -eq $clienteOtro.id) 'positivo extranjero'
$clienteOtroTenant = ApiError GET "/clientes/$($clienteOtro.id)/ficha-comercial" $agente.token $null
$propietarioOtroTenant = ApiError GET "/propietarios/$($propietarioOtro.id)/ficha-comercial" $agente.token $null
Check 'el cliente de otro tenant no se divulga por poseer su id' ($clienteOtroTenant.codigo -eq 404) 'tenant'
Check 'el propietario de otro tenant no se divulga por poseer su id' ($propietarioOtroTenant.codigo -eq 404) 'tenant'
# Se conservan originales hasta el teardown de la base EXCLUSIVA del runner.
$contextoFinal = Assert-ControlLocalE2EContext
Check 'ambos fixtures pertenecen a la base exclusiva de esta corrida' ($contextoFinal.Database -eq $e2e.Database) 'teardown'
$persistenciaE3 = Invoke-ControlLocalE2ESql @"
select
  (select count(*) from persona where correo='propietario.e3.$sufijo@test.local') || '|' ||
  (select count(*) from persona where correo='cliente.e3.$sufijo@test.local') || '|' ||
  (select count(*) from propiedad where codigo='LOC-E3-$sufijo') || '|' ||
  (select count(*) from captacion where id_captacion=$idCaptacion)
"@
Check 'los registros TEST E3 quedan identificables hasta el teardown de su base exclusiva' ($persistenciaE3 -eq '1|1|1|1') $persistenciaE3

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
