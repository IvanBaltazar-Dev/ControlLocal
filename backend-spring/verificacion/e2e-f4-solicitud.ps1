# =====================================================================
# E2E de la vertical F4 (solicitud -> documentos -> evaluacion ->
# contrato -> comision) contra el API v2.
#
# Es la vertical que CIERRA el ciclo: el ultimo bloque comprueba, fila a
# fila, la cascada de siete efectos del cierre. Cubre lo que un test de
# service no puede: los gates de rol, los codigos HTTP, la forma exacta
# del JSON y las tres vias de subida de binarios.
#
# Contrato: docs/ai/contrato-congelado-f4-solicitud.md
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f4-solicitud
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

# Cuerpo binario crudo (octet-stream), la segunda via de subida.
function ApiBinario($ruta, $token, $bytes) {
    Invoke-RestMethod -Method POST -Uri "$base$ruta" -Headers @{ Authorization = "Bearer $token" } `
        -Body $bytes -ContentType 'application/octet-stream' -TimeoutSec 30
}

# Variante de ApiBinario que espera error. Hace falta desde que la subida por
# octet-stream es la UNICA via: las validaciones que antes se probaban por el
# camino JSON ahora tienen que probarse por este.
# El cuerpo del error se lee del STREAM de la respuesta, igual que ApiError.
# En PowerShell 5.1 `$PSItem.ErrorDetails.Message` llega VACIO para los fallos
# de Invoke-RestMethod, asi que la primera version de esta funcion daba cuatro
# checks en rojo con el mensaje en blanco: parecia que el backend habia dejado
# de validar, y lo que fallaba era el lector.
function ApiBinarioError($ruta, $token, $bytes) {
    try {
        ApiBinario $ruta $token $bytes | Out-Null
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

# Ejecuta esperando un error y devuelve @{ codigo; error } del cuerpo
# {"error": ...} congelado. codigo 0 = la llamada NO fallo.
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



function Base64De($texto) {
    [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($texto))
}

$sufijo = Get-Random -Minimum 1000 -Maximum 9999
$hoy = (Get-Date).ToString('yyyy-MM-dd')
$finEncargo = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login de los cinco actores ==" -ForegroundColor Cyan
# vmora (AGE-001) lo supervisa rsalas (BRK-001); ltorres (AGE-003) lo
# supervisa psoto (BRK-002). Ese cruce es lo que prueba las dos reglas de
# alcance y el "el broker no supervisa al agente responsable".
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$agenteAjeno = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.ltorres.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$brokerAjeno = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login agente responsable (AGE-001)' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login agente de otro equipo (AGE-003)' ($agenteAjeno.rol -eq 'AGENTE') $agenteAjeno.rol
Check 'login broker supervisor (BRK-001)' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login broker de otro equipo (BRK-002)' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login admin' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

$propietarioFixture = NuevoPropietarioC2DePrueba -Token $agente.token -Nombre ('Propietario TEST F4 '+$sufijo)
Write-Host "`n== 2. Prologo F2 + F3: oferta, captacion ACTIVA y oportunidad ==" -ForegroundColor Cyan
# Registrar NO es encargar (V75): el inmueble entra en el registro maestro y se
# prospecta; el encargo nace al captar, cuando el propietario acepta.
$local = NuevoInmuebleSinEncargo -LocalIndependienteTest -Token $agente.token -Direccion 'Av. Cierre 456' `
    -IdPropietario $propietarioFixture.id -Metraje 140 -Rubro 'Cafeteria' -Descripcion "Prospecto F4 $sufijo"
$idLocal = $local.id
$idProspeccion = NuevaProspeccion -Token $agente.token -IdPropiedad $idLocal `
    -Observaciones "Prospeccion F4 $sufijo"
Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null | Out-Null
# Este puerto de prospección declara el porcentaje 5; Core conserva 0.05
# como fracción del pacto. No elegir unidades por magnitud ni aplicar dos veces.
$captada = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{
    operacion = 'ALQUILER'; importe = 8000; moneda = 'PEN'; comisionPactada = 5
    tipoComision = 'P'; baseCalculo = 'R'; tratamientoIgv = 'N'; exclusividad = $true
    inicioEncargo = $hoy; finEncargo = $finEncargo
}
$idCaptacion = $captada.idCaptacion
$pactoCaptado=Api GET "/encargos/$idCaptacion" $agente.token $null
Check 'el porcentaje declarado 5 conserva la fracción 0.05 en el pacto' ($pactoCaptado.condiciones.valorComision -eq 0.05) $pactoCaptado.condiciones.valorComision
$null = HabilitarEncargoC2DePrueba -IdEncargo $idCaptacion -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato' -EjecucionComision @{devengo=@{hito='CONTRATO_ALQUILER'};exigibilidad=@{hito='CONTRATO_ALQUILER'}}
Check 'la captacion del prologo quedo ACTIVA' `
    ((Invoke-ControlLocalE2ESql "select estado from captacion where id_captacion=$idCaptacion") -eq 'A') 'estado captacion'

# El anuncio cuelga del ENCARGO desde V70: `/locales/{id}/publicaciones` devolvia
# las series de venta y alquiler juntas sin poder decir cual publicaba que.
$idPublicacion = (PublicarAnuncioC6DePrueba -IdEncargo $idCaptacion -Token $agente.token `
    -Canal 'URBANIA' -Importe 8500 -Moneda 'PEN' -Titulo "Anuncio F4 TEST $sufijo" -Url "https://demo.test/f4-$sufijo").id
Check 'el local tiene una publicacion PUBLICADA (para el efecto 6)' ($null -ne $idPublicacion) 'publicacion'

$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "4890$sufijo"
    nombre = "Cliente Cierre $sufijo"; telefono = '987444555'
    correo = "cierre.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$idCliente = $cliente.id
$oportunidad = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $idCliente -Token $agente.token `
    -EvidenciaInteres 'Interes directo declarado EXCLUSIVAMENTE TEST F4, no inferido de una comunicacion.' `
    -Observaciones 'Caso ficticio de solicitud F4.'
$idOportunidad = $oportunidad.id
Check 'la oportunidad del prologo nace ABIERTA' ($oportunidad.estado -eq 'A') $oportunidad.estado

Write-Host "`n== 3. POST /solicitudes: alta, precondiciones y efecto lateral ==" -ForegroundColor Cyan
$sinDatos = ApiError POST '/solicitudes' $agente.token @{ montoPropuesto = 8000 }
Check 'sin idOportunidad responde el mensaje del cable' `
    ($sinDatos.error -eq 'Los datos de la solicitud son obligatorios.') $sinDatos.error

$formaMala = ApiError POST '/solicitudes' $agente.token @{
    idOportunidad = $idOportunidad; montoPropuesto = 8000; moneda = 'PEN'; formaPago = 'BITCOIN'
}
Check 'forma de pago invalida da el mensaje del cable' `
    ($formaMala.error -eq 'Valor invalido para forma de pago: BITCOIN') $formaMala.error

$brokerRegistra = ApiError POST '/solicitudes' $broker.token @{ idOportunidad = $idOportunidad; montoPropuesto = 8000 }
Check 'el BROKER no registra solicitudes (403)' ($brokerRegistra.codigo -eq 403) "codigo=$($brokerRegistra.codigo)"

$solicitud = Api POST '/solicitudes' $agente.token @{
    idOportunidad = $idOportunidad; montoPropuesto = 8000; moneda = 'PEN'; plazoMeses = 24
    fechaInicio = '2026-09-01'; formaPago = 'TRANSFERENCIA'
    mesesGarantia = 2; mesesAdelanto = 1; fechaVigenciaOferta = '2026-08-31'
    observaciones = 'Oferta formal del interesado.'
}
$idSolicitud = $solicitud.id
Check 'POST /solicitudes responde 201 y nace REGISTRADA (G)' ($solicitud.estado -eq 'G') $solicitud.estado
Check 'el codigo autogenerado sigue el formato SOL-yyMMddHHmmss' `
    ($solicitud.codigoSolicitud -match '^SOL-\d{12}$') $solicitud.codigoSolicitud
Check 'plazoTentativo se DERIVA de plazoMeses ("24 meses")' `
    ($solicitud.plazoTentativo -eq '24 meses') $solicitud.plazoTentativo
Check 'el checklist arranca en 0/6' `
    ($solicitud.documentosEntregados -eq 0 -and $solicitud.documentosRequeridos -eq 6) `
    "$($solicitud.documentosEntregados)/$($solicitud.documentosRequeridos)"
Check 'la solicitud nace con tenant' `
    ((Invoke-ControlLocalE2ESql "select organizacion_id from solicitud_alquiler where id_solicitud=$idSolicitud") -eq "$($fixtureE2E.organizacion)") 'organizacion_id'
# Convencion de Transiciones: iniciar() NO audita (nacer no es transicionar).
Check 'nacer no deja fila en historial_estado' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='SOLICITUD_ALQUILER' and id_entidad=$idSolicitud") -eq '0') 'historial'

# Efecto lateral invisible en el REST: la oportunidad pasa a S. En la v2 va
# por Transiciones, asi que -a diferencia de la v1- SI deja auditoria (MEJ-01).
Check 'el alta movio la oportunidad a S (solicitud creada)' `
    ((Api GET "/oportunidades/$idOportunidad" $agente.token).estado -eq 'S') 'estado oportunidad'
Check 'y esa transicion SI se audito (la v1 no lo hacia)' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad and estado_nuevo='S'") -eq '1') 'historial oportunidad'

# Una oportunidad admite UNA sola solicitud, pero el mensaje que sale NO es el
# del duplicado: el alta ya movio la oportunidad a S, y la precondicion
# "ABIERTA" se comprueba ANTES que la existencia. Es el orden del cable v1
# (BusinessValidations primero, unico de la BD despues). Fue esta corrida la
# que demostro que el camino del duplicado por oportunidad es INALCANZABLE, y
# por eso su comprobacion se retiro del service (2026-07-29): era codigo muerto
# y ademas respondia 400 donde el cable responde 409. Lo protege el unico.
$duplicada = ApiError POST '/solicitudes' $agente.token @{ idOportunidad = $idOportunidad; montoPropuesto = 8000; moneda = 'PEN' }
Check 'una oportunidad admite UNA sola solicitud (corta por la precondicion)' `
    ($duplicada.error -eq 'La oportunidad comercial debe estar ABIERTA.') $duplicada.error
Check 'y la BD lo respalda con el unico por oportunidad' `
    ((Invoke-ControlLocalE2ESql "select count(*) from pg_indexes where tablename='solicitud_alquiler' and indexdef like '%id_oportunidad%' and indexdef like 'CREATE UNIQUE%'") -ge '1') 'unico'

# NOTA: el otro caso de unicidad -proponer un codigoSolicitud que ya existe,
# que responde 409 como el cable- NO se puede probar aqui: a esta altura la
# unica oportunidad del guion ya esta en S, asi que la precondicion "ABIERTA"
# corta antes de llegar al codigo. Haria falta una segunda oportunidad ABIERTA
# (y por tanto un segundo local con captacion ACTIVA, por el unico parcial).
# Queda cubierto por SolicitudServiceImplTest.unCodigoPropuestoRepetidoEsCONFLICTONoBadRequest.

Write-Host "`n== 4. Lectura y alcance de /solicitudes (seccion 7: por AGENTE) ==" -ForegroundColor Cyan
$porCodigo = Api GET "/solicitudes/codigo/$($solicitud.codigoSolicitud)" $agente.token
Check 'GET /solicitudes/codigo/{codigo} resuelve la ficha' ($porCodigo.id -eq $idSolicitud) $porCodigo.id
$listado = Api GET "/solicitudes?idOportunidad=$idOportunidad" $agente.token
Check 'el filtro idOportunidad acota a una' ($listado.totalRecords -eq 1) $listado.totalRecords
Check 'la ficha resuelve el contexto (cliente, captacion, local)' `
    ($listado.items[0].clienteNombre -eq "Cliente Cierre $sufijo" -and $listado.items[0].direccionLocal -eq 'Av. Cierre 456') `
    "$($listado.items[0].clienteNombre) / $($listado.items[0].direccionLocal)"

Check 'el BROKER supervisor la alcanza (por agente supervisado)' `
    ((Api GET "/solicitudes?idOportunidad=$idOportunidad" $broker.token).totalRecords -eq 1) 'alcance broker'
Check 'el BROKER de otro equipo NO la ve (lista vacia, no 403)' `
    ((Api GET "/solicitudes?idOportunidad=$idOportunidad" $brokerAjeno.token).totalRecords -eq 0) 'alcance broker ajeno'
$ajeno = ApiError GET "/solicitudes/$idSolicitud" $agenteAjeno.token $null
Check 'un agente de otro equipo recibe 403 en la ficha' ($ajeno.codigo -eq 403) "codigo=$($ajeno.codigo)"
Check 'TenantAdmin no consulta solicitudes comerciales' `
    ((ApiError GET "/solicitudes?idOportunidad=$idOportunidad" $admin.token $null).codigo -eq 403) 'gobierno no opera'

Write-Host "`n== 5. Documentos: la UNICA via de subida ==" -ForegroundColor Cyan
# Descongelado 2026-08-08: de las tres vias que convivian quedo solo
# octet-stream. Las otras dos (JSON con base64 y por trozos) existian por un bug
# del cliente .NET del Blazor, que ya no existe.
$contenidoPdf = '%PDF-1.4 documento de prueba'
$bytesPdf = [Text.Encoding]::UTF8.GetByteCount($contenidoPdf)
$pdf = Base64De $contenidoPdf

$doc1 = ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=I&nombreArchivo=dni.pdf" `
    $agente.token ([Text.Encoding]::UTF8.GetBytes($contenidoPdf))
Check 'la subida responde 201 con el documento REGISTRADO/Pendiente' `
    ($doc1.estado -eq 'R' -and $doc1.resultadoRevision -eq 'P') "$($doc1.estado)/$($doc1.resultadoRevision)"
# A04 entrega sólo referencia UUID Core. La clave de proveedor no sale al cliente.
$referenciaDoc=[guid]::Empty
Check 'el documento entrega referencia UUID opaca, sin clave de proveedor' `
    ([guid]::TryParse([string]$doc1.rutaArchivo,[ref]$referenciaDoc) -and $referenciaDoc -ne [guid]::Empty) 'referencia Core'
Check 'el tipo se traduce a su nombre de catalogo' `
    ($doc1.tipoDocumento -eq 'I' -and $doc1.tipoNombre) "$($doc1.tipoDocumento)/$($doc1.tipoNombre)"
# fecha_entrega la fija el caso de uso: la respuesta del POST DEBE traerla
# (con insertable=false y DEFAULT now() viajaba vacia).
Check 'el POST devuelve fechaEntrega (no depende del DEFAULT de la BD)' `
    ($null -ne $doc1.fechaEntrega) 'fechaEntrega'
# H-12 cerrado el 2026-08-08: este endpoint era PUBLICO y aqui se comprobaba
# que servia el binario SIN token. Ahora se comprueban las dos caras.
$urlContenido = "$base/documentos/contenido?clave=$([Uri]::EscapeDataString($doc1.rutaArchivo))"
$conToken = Invoke-WebRequest -Uri $urlContenido -Headers @{ Authorization = "Bearer $($agente.token)" } `
    -UseBasicParsing -TimeoutSec 20
Check 'el binario se sirve CON token' ($conToken.StatusCode -eq 200) "codigo=$($conToken.StatusCode)"
$sinToken = -1
try {
    Invoke-WebRequest -Uri $urlContenido -UseBasicParsing -TimeoutSec 20 | Out-Null
} catch {
    if ($PSItem.Exception.Response) { $sinToken = [int]$PSItem.Exception.Response.StatusCode }
}
Check 'H-12: sin token responde 401, ya no entrega el binario' ($sinToken -eq 401) "codigo=$sinToken"

$doc2 = ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=R&nombreArchivo=ficha-ruc.pdf" `
    $agente.token ([Text.Encoding]::UTF8.GetBytes('%PDF-1.4 ficha ruc'))
Check 'una segunda subida usa el mismo camino' ($doc2.tipoDocumento -eq 'R') $doc2.tipoDocumento
# El tercer tipo lo subia la via por trozos; ahora va por la misma que las
# otras dos. El checklist de mas abajo cuenta TIPOS, asi que hacen falta tres.
$doc3 = ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=V&nombreArchivo=vigencia.pdf" `
    $agente.token ([Text.Encoding]::UTF8.GetBytes($contenidoPdf))
Check 'tercera subida, tercer tipo' ($doc3.tipoDocumento -eq 'V') $doc3.tipoDocumento

# Las tres vias que NO sobreviven: la de handoff local nunca se porto (D-F4-1,
# leia del disco del servidor) y las de base64 y trozos se borraron al
# descongelar el contrato.
foreach ($muerta in @('local', 'chunk', '')) {
    $ruta = if ($muerta) { "/solicitudes/$idSolicitud/documentos/$muerta" } else { "/solicitudes/$idSolicitud/documentos" }
    $ida = ApiError POST $ruta $agente.token @{ tipoDocumento = 'I'; nombreArchivo = 'x.pdf' }
    $etiqueta = if ($muerta) { $muerta } else { 'base64 (POST /documentos)' }
    Check "la via '$etiqueta' ya NO existe" `
        ($ida.codigo -eq 404 -or $ida.codigo -eq 405) "codigo=$($ida.codigo)"
}

Write-Host "`n== 6. Documentos: validaciones y checklist X/6 ==" -ForegroundColor Cyan
# Las validaciones se comprueban por la via que queda. Las tres que solo
# existian en el camino JSON (base64 corrupto, cuerpo vacio y Content-Type
# ausente) se fueron con el: no hay cuerpo JSON que malformar.
$ctxValidacion = Assert-ControlLocalE2EContext
$orgValidacion = [long]$fixtureE2E.organizacion
if($orgValidacion -le 0 -or $fixtureE2E.codigo -cnotmatch '^E2E-[a-f0-9]{20}$' -or
        $ctxValidacion.ApiContainer -cne $e2e.ApiContainer -or $ctxValidacion.Database -cne $e2e.Database -or
        (Invoke-ControlLocalE2ESql "select count(*) from organizacion where id_organizacion=$orgValidacion and codigo='$($fixtureE2E.codigo)' and modalidad='EMPRESARIAL'") -ne '1') {
    throw 'El control de binarios requiere la organizacion TEST de esta corrida.'
}
$directorioTenant = '/almacen-e2e/tenant/'+$orgValidacion
$contarBinariosTenant = {
    $preferenciaPrevia = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $clavesPrivadas = @(& docker exec $e2e.ApiContainer find $directorioTenant -type f 2>&1)
        $codigoConteo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $preferenciaPrevia }
    if($codigoConteo -ne 0){throw 'No se pudo comprobar el almacen TEST; no acreditar ausencia por un error tecnico.'}
    return @($clavesPrivadas | Where-Object {-not [string]::IsNullOrWhiteSpace([string]$_)}).Count
}
$binariosAntesRechazos = & $contarBinariosTenant
$cuerpo = [Text.Encoding]::UTF8.GetBytes($contenidoPdf)
$extMala = ApiBinarioError "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=I&nombreArchivo=virus.exe" `
    $agente.token $cuerpo
Check 'extension no permitida' ($extMala.error -eq 'Tipo de archivo no permitido (.exe).') $extMala.error
$tipoMalo = ApiBinarioError "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=Z&nombreArchivo=x.pdf" `
    $agente.token $cuerpo
Check 'tipo de documento invalido' ($tipoMalo.error -eq 'Tipo de documento invalido: Z') $tipoMalo.error
$sinTipo = ApiBinarioError "/solicitudes/$idSolicitud/documentos/archivo?nombreArchivo=x.pdf" `
    $agente.token $cuerpo
Check 'tipo obligatorio' ($sinTipo.error -eq 'El tipo de documento es obligatorio.') $sinTipo.error
$sinNombre = ApiBinarioError "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=I" `
    $agente.token $cuerpo
Check 'nombre de archivo obligatorio' ($sinNombre.error -eq 'El nombre del archivo es obligatorio.') $sinNombre.error

# El preflight Core rechaza tipo Z antes del PUT. La clave del journal C6
# pertenece a tenant/org/salidas/operacion/intento; no codifica la solicitud.
# Comparar todo el tenant conserva los archivos C2 legitimos y demuestra que
# los cuatro rechazos no agregan binarios, sin asumir una carpeta por expediente.
$binariosDespuesRechazos = & $contarBinariosTenant
# Ojo con PowerShell 5.1: hay que asignar ANTES de envolver con @(); un
# @(Api ...) directo cuenta el resultado de la funcion como un solo objeto.
$expediente = Api GET "/solicitudes/$idSolicitud/documentos" $agente.token
$documentos = @($expediente).Count
# Claves exclusivamente de DB, ligadas a solicitud y organizacion TEST. No se
# imprimen ni se aceptan claves de proveedor desde la respuesta del cliente.
$documentosInternos = (Invoke-ControlLocalE2ESql "select coalesce(json_agg(json_build_object('id',id_documento,'clave',ruta_archivo) order by id_documento),'[]'::json) from documento_solicitud where organizacion_id=$orgValidacion and id_solicitud=$idSolicitud") | ConvertFrom-Json
$binariosDocumentados = 0
foreach($documentoInterno in $documentosInternos) {
    if([long]$documentoInterno.id -notin @([long]$doc1.id,[long]$doc2.id,[long]$doc3.id) -or
            $documentoInterno.clave -cnotmatch "^tenant/$orgValidacion/salidas/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[1-9][0-9]*\.pdf$") {
        throw 'La referencia interna no corresponde a los documentos PDF TEST registrados.'
    }
    $preferenciaPrevia = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $salidaPrivada = @(& docker exec $e2e.ApiContainer sh -c 'test -f $1 && test -s $1' 'fixture' ('/almacen-e2e/'+$documentoInterno.clave) 2>&1)
        $codigoArchivo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $preferenciaPrevia }
    if($codigoArchivo -ne 0){throw 'Un documento del expediente TEST no conserva un binario disponible.'}
    $binariosDocumentados++
}
Check 'un alta rechazada no deja huerfanos en el almacen' `
    ($binariosDespuesRechazos -eq $binariosAntesRechazos -and $documentos -eq 3 -and $binariosDocumentados -eq $documentos) `
    "antes=$binariosAntesRechazos despues=$binariosDespuesRechazos documentos=$documentos comprobados=$binariosDocumentados"

# El checklist cuenta SEIS tipos: PODER_REPRESENTACION (P) y OTRO (O) no suman.
ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=O&nombreArchivo=anexo.pdf" `
    $agente.token $cuerpo | Out-Null
Check 'el tipo OTRO no suma al checklist (sigue 3/6)' `
    ((Api GET "/solicitudes/$idSolicitud" $agente.token).documentosEntregados -eq 3) 'checklist'
$doc4 = ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=E&nombreArchivo=sustento.pdf" `
    $agente.token $cuerpo
ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=G&nombreArchivo=garantia.pdf" `
    $agente.token $cuerpo | Out-Null
ApiBinario "/solicitudes/$idSolicitud/documentos/archivo?tipoDocumento=D&nombreArchivo=declaracion.pdf" `
    $agente.token $cuerpo | Out-Null
Check 'con los seis tipos entregados el checklist marca 6/6' `
    ((Api GET "/solicitudes/$idSolicitud" $agente.token).documentosEntregados -eq 6) 'checklist'

Write-Host "`n== 7. Revision de documentos (BROKER/ADMIN) ==" -ForegroundColor Cyan
$sinResultado = ApiError PATCH "/solicitudes/$idSolicitud/documentos/$($doc1.id)/revisar" $broker.token @{ observaciones = 'x' }
Check 'el resultado de la revision es obligatorio' `
    ($sinResultado.error -eq 'El resultado de la revision es obligatorio.') $sinResultado.error
$sinObservacion = ApiError PATCH "/solicitudes/$idSolicitud/documentos/$($doc1.id)/revisar" $broker.token @{ resultado = 'O' }
Check 'observar exige la observacion (MEJ-03)' `
    ($sinObservacion.error -eq 'La observacion del documento es obligatoria.') $sinObservacion.error
$agenteRevisa = ApiError PATCH "/solicitudes/$idSolicitud/documentos/$($doc1.id)/revisar" $agente.token @{ resultado = 'C' }
Check 'el AGENTE no revisa documentos (403)' ($agenteRevisa.codigo -eq 403) "codigo=$($agenteRevisa.codigo)"

$observado = Api PATCH "/solicitudes/$idSolicitud/documentos/$($doc4.id)/revisar" $broker.token @{
    resultado = 'O'; observaciones = 'La boleta esta ilegible.'
}
Check 'observar deja el documento OBSERVADO' ($observado.estado -eq 'O') $observado.estado
Check 'un documento OBSERVADO deja de contar (5/6)' `
    ((Api GET "/solicitudes/$idSolicitud" $agente.token).documentosEntregados -eq 5) 'checklist'
Check 'la revision se audito' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='DOCUMENTO_SOLICITUD' and id_entidad=$($doc4.id)") -eq '1') 'historial documento'

# D-F4-5 CERRADA (decision de equipo, 2026-07-29). En el cable v1 revisar un
# documento suelto solo exigia el ROL, asi que un broker podia tocar el
# expediente de otro equipo; conformar en bloque y evaluar si comprobaban el
# alcance. Se cerro el hueco ANTES del corte: donde la v1 respondia 200, la v2
# responde 403. Es una divergencia deliberada y acotada del contrato congelado.
$ajenoRevisa = ApiError PATCH "/solicitudes/$idSolicitud/documentos/$($doc1.id)/revisar" $brokerAjeno.token @{ resultado = 'C' }
Check 'D-F4-5 cerrada: un broker de OTRO equipo ya NO puede revisar (403)' `
    ($ajenoRevisa.codigo -eq 403) "codigo=$($ajenoRevisa.codigo)"
# ...igual que conformar en bloque, que ya lo exigia en la v1.
$ajenoConforma = ApiError PATCH "/solicitudes/$idSolicitud/documentos/conformar" $brokerAjeno.token $null
Check 'D-F4-5: conformar en bloque tambien exige alcance (403)' ($ajenoConforma.codigo -eq 403) "codigo=$($ajenoConforma.codigo)"

$conformados = Api PATCH "/solicitudes/$idSolicitud/documentos/conformar" $broker.token $null
Check 'conformar devuelve el expediente COMPLETO' (@($conformados).Count -eq 7) "items=$(@($conformados).Count)"
Check 'conformar respeta al OBSERVADO (hallazgo deliberado del broker)' `
    ((@($conformados) | Where-Object { $_.id -eq $doc4.id }).estado -eq 'O') 'observado intacto'
Check 'y deja VALIDADO todo lo que estaba pendiente' `
    ((@($conformados) | Where-Object { $_.estado -eq 'V' }).Count -eq 6) 'validados'

Write-Host "`n== 8. Reenvio a evaluacion y decision del broker ==" -ForegroundColor Cyan
$reenviada = Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null
Check 'reenviar deja la solicitud EN REVISION (E)' ($reenviada.estado -eq 'E') $reenviada.estado
$reenvioMalo = ApiError POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null
Check 'no se reenvia desde EN REVISION' `
    ($reenvioMalo.error -eq 'Solo una solicitud registrada u observada puede enviarse a evaluacion.') $reenvioMalo.error

$agenteEvalua = ApiError GET '/evaluaciones' $agente.token $null
Check 'el AGENTE no entra a /evaluaciones (403)' ($agenteEvalua.codigo -eq 403) "codigo=$($agenteEvalua.codigo)"

# El tipo se IGNORA como valor pero el cable lo exige PRESENTE y valido, y su
# parseo va ANTES que el del resultado: con los dos mal, gana el del tipo.
$tipoVacio = ApiError POST '/evaluaciones' $broker.token @{ resultado = 'A'; idSolicitud = $idSolicitud }
Check 'tipoEvaluacion ausente es 400 (se ignora su valor, no su presencia)' `
    ($tipoVacio.error -like 'Valor invalido para tipo de evaluacion*') $tipoVacio.error
$ambosMal = ApiError POST '/evaluaciones' $broker.token @{ tipoEvaluacion = 'X'; resultado = 'Z'; idSolicitud = $idSolicitud }
Check 'con tipo y resultado invalidos gana el mensaje del TIPO' `
    ($ambosMal.error -eq 'Valor invalido para tipo de evaluacion: X') $ambosMal.error
$sinSupervision = ApiError POST '/evaluaciones' $brokerAjeno.token @{
    tipoEvaluacion = 'F'; resultado = 'A'; idSolicitud = $idSolicitud
}
Check 'la evaluacion SI exige supervision (a diferencia de revisar)' `
    ($sinSupervision.error -eq 'El broker no supervisa al agente responsable de esta solicitud.') $sinSupervision.error

# OBSERVADA -> tipo OBSERVACION (no consume la unica FINAL) y devuelve la
# solicitud a OBSERVADA, que es justo desde donde el agente puede reenviar.
$observacion = Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'O'; observaciones = 'Falta subsanar el sustento.'
    idSolicitud = $idSolicitud
}
Check 'el tipo se DERIVA del resultado: OBSERVADA => OBSERVACION' `
    ($observacion.tipoEvaluacion -eq 'O') $observacion.tipoEvaluacion
Check 'la evaluacion movio la solicitud a OBSERVADA' `
    ((Api GET "/solicitudes/$idSolicitud" $agente.token).estado -eq 'O') 'estado solicitud'
$historial = Api GET "/solicitudes/$idSolicitud/evaluaciones" $agente.token
Check 'el historial por solicitud SI lo ve el agente dueno' (@($historial).Count -eq 1) "items=$(@($historial).Count)"

Api POST "/solicitudes/$idSolicitud/reenviar" $agente.token $null | Out-Null
$aprobacion = Api POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'P'; resultado = 'A'; observaciones = 'Expediente conforme.'
    idSolicitud = $idSolicitud
}
Check 'APROBADA => tipo FINAL (se pisa el "P" que mando el cliente)' `
    ($aprobacion.tipoEvaluacion -eq 'F') $aprobacion.tipoEvaluacion
Check 'la solicitud quedo APROBADA' `
    ((Api GET "/solicitudes/$idSolicitud" $agente.token).estado -eq 'A') 'estado solicitud'
$segundaFinal = ApiError POST '/evaluaciones' $broker.token @{
    tipoEvaluacion = 'F'; resultado = 'R'; idSolicitud = $idSolicitud
}
Check 'solo cabe UNA evaluacion final por solicitud' `
    ($segundaFinal.error -eq 'Solo puede existir una evaluacion final por solicitud.') $segundaFinal.error
Check 'GET /evaluaciones pagina en SQL y devuelve las del broker' `
    ((Api GET '/evaluaciones?pagina=1&tamano=5' $broker.token).items.Count -le 5) 'paginacion'
Check 'GET /evaluaciones/{id} resuelve la ficha' `
    ((Api GET "/evaluaciones/$($aprobacion.id)" $broker.token).idSolicitud -eq $idSolicitud) 'ficha'

Write-Host "`n== 9. POST /contratos: la cascada de SIETE efectos (seccion 6) ==" -ForegroundColor Cyan
$sinSolicitud=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{} ('sin-solicitud-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'preparar requiere identificar una solicitud autorizada' ($sinSolicitud.codigo -eq 400) $sinSolicitud.codigo
$rutaRetirada=ApiError POST '/contratos' $agente.token @{idSolicitud=$idSolicitud;estadoContrato='V';fechaCierre=$hoy}
Check 'la antigua creación de firma directa está retirada' ($rutaRetirada.codigo -in @(404,405)) $rutaRetirada.codigo
# Las fechas de hito se prueban en Formalizacion/C5; ya no existe un selector de
# estado/fecha de cierre dentro de la preparación de contrato.
$cierreAjeno=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agenteAjeno.token @{idSolicitud=$idSolicitud} ('preparacion-ajena-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'el contrato SI exige que la solicitud sea del agente (403)' ($cierreAjeno.codigo -eq 403) "codigo=$($cierreAjeno.codigo)"
$brokerCierra=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $broker.token @{idSolicitud=$idSolicitud} ('preparacion-broker-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'el broker no prepara por el agente; confirma el hito competente' ($brokerCierra.codigo -eq 403) "codigo=$($brokerCierra.codigo)"

$preparado=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$idSolicitud;incidencias='Preparación documental TEST.'} ('e2e-preparacion-'+[guid]::NewGuid().ToString('N'))
Check 'la preparación no acredita firma ni dinero' ($preparado.estadoContrato -eq 'P' -and $null -eq $preparado.fechaCierre) 'preparación'
$formalizado=FormalizarAlquilerC5DePrueba -IdEncargo $idCaptacion -IdOportunidad $idOportunidad -TokenResponsable $agente.token -TokenDecisor $broker.token -Renta 8000 -Moneda 'PEN' -Inicio $preparado.fechaInicioContrato -Fin $preparado.fechaFinContrato -FechaFirma $hoy -ContratoPreparado $preparado.id
$contrato=$formalizado.contrato
$comision=LeerComisionC5DePrueba $formalizado $broker.token
$idContrato = $contrato.id
Check 'el hito confirmado produce el contrato firmado de su fuente' ($contrato.estadoContrato -eq 'D' -and $contrato.id -eq $preparado.id) $contrato.estadoContrato
Check 'las condiciones se LEEN de la solicitud, no se copian' `
    ($contrato.rentaMensual -eq 8000 -and $contrato.plazoContratoMeses -eq 24) `
    "$($contrato.rentaMensual)/$($contrato.plazoContratoMeses)"
Check 'la fecha fin se deriva (inicio + plazo)' ($contrato.fechaFinContrato -eq '2028-09-01') $contrato.fechaFinContrato

# --- Los siete efectos, uno por uno ---
Check 'efecto 2: 0.05 de 8000 = 400, devengado sin recepción inventada' `
    ($comision.calculo.cuantia.importe -eq 400 -and $comision.devengo.estado -eq 'DEVENGADO' -and $comision.cobro.estado -eq 'SIN_COBROS') 'dimensiones separadas'
Check 'efecto 2: renta y comision conservan PEN sin conversion' `
    ($contrato.moneda -eq 'PEN' -and $comision.calculo.cuantia.moneda -eq 'PEN') `
    "$($contrato.moneda)|$($comision.calculo.cuantia.moneda)"
Check 'efecto 3: la oportunidad se cerro EXITOSA (F)' `
    ((Invoke-ControlLocalE2ESql "select estado from oportunidad_comercial where id_oportunidad=$idOportunidad") -eq 'F') 'oportunidad'
Check 'efecto 4: la solicitud quedo CERRADA (C)' `
    ((Invoke-ControlLocalE2ESql "select estado from solicitud_alquiler where id_solicitud=$idSolicitud") -eq 'C') 'solicitud'
Check 'efecto 5: la captacion se cerro con fecha y motivo de alquiler' `
    ((Invoke-ControlLocalE2ESql "select estado||'|'||fecha_cierre||'|'||motivo_cierre from captacion where id_captacion=$idCaptacion") -eq "C|$hoy|A") 'captacion'
Check 'efecto 6: el local conserva registro A y disponibilidad A (alquilado)' `
    ((Invoke-ControlLocalE2ESql "select estado_registro||'|'||disponibilidad_comercial from propiedad where id_propiedad=$idLocal") -eq 'A|A') 'propiedad'
Check 'efecto 6: se registro el precio con hito C (cerrado real)' `
    ((Invoke-ControlLocalE2ESql "select count(*) from precio_propiedad where id_propiedad=$idLocal and hito='C' and monto=8000") -eq '1') 'precio'
Check 'efecto 6: las publicaciones se dieron de baja' `
    ((Invoke-ControlLocalE2ESql "select estado from publicacion where id_publicacion=$idPublicacion") -eq 'C') 'publicacion'

# La v1 movia estos cuatro estados a mano y NO auditaba ninguno (MEJ-01).
$auditoria = Invoke-ControlLocalE2ESql @"
select count(*) from historial_estado
 where (entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad and estado_nuevo='F')
    or (entidad_tipo='SOLICITUD_ALQUILER' and id_entidad=$idSolicitud and estado_nuevo='C')
    or (entidad_tipo='CAPTACION' and id_entidad=$idCaptacion and estado_nuevo='C')
    or (entidad_tipo='DISPONIBILIDAD_PROPIEDAD' and id_entidad=$idLocal and estado_nuevo='A')
"@
Check 'la cascada dejo CUATRO filas en historial_estado (MEJ-01)' ($auditoria -eq '4') "filas=$auditoria"
Check 'el hito conserva transición desde la preparación' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='CONTRATO_ALQUILER' and id_entidad=$idContrato and estado_anterior='P'") -eq '1') 'historial contrato'

$segundoContrato=Invoke-ComandoC2DePrueba POST '/contratos/en-proceso' $agente.token @{idSolicitud=$idSolicitud} ('segunda-preparacion-'+[guid]::NewGuid().ToString('N')) -EsperarError
Check 'una oportunidad no admite un segundo contrato' `
    ($segundoContrato.codigo -in @(400,409)) $segundoContrato.error

Write-Host "`n== 10. Lectura de contratos y comision (seccion 5) ==" -ForegroundColor Cyan
$porOportunidad = Api GET "/contratos/oportunidad/$idOportunidad" $agente.token
Check 'GET /contratos/oportunidad/{id} resuelve el contrato' ($porOportunidad.id -eq $idContrato) $porOportunidad.id
$paginaContratos = Api GET '/contratos' $agente.token
Check 'el tamano por defecto de /contratos es 100, no 10' ($paginaContratos.pageSize -eq 100) $paginaContratos.pageSize
Check 'el BROKER alcanza por CAPTACION supervisada' `
    (@((Api GET '/contratos' $broker.token).items | Where-Object { $_.id -eq $idContrato }).Count -eq 1) 'alcance broker'
Check 'el BROKER de otro equipo no lo ve' `
    (@((Api GET '/contratos' $brokerAjeno.token).items | Where-Object { $_.id -eq $idContrato }).Count -eq 0) 'alcance broker ajeno'

$comision=LeerComisionC5DePrueba $formalizado $broker.token
$adminSinPermiso=ApiError POST '/comisiones/repartos/propuestas' $admin.token @{comision=$comision.id;versionEsperada=$comision.version;montoAgente=100}
Check 'gobierno no decide reparto comercial' ($adminSinPermiso.codigo -eq 403) "codigo=$($adminSinPermiso.codigo)"
$sinMonto=ApiError POST '/comisiones/repartos/propuestas' $broker.token @{comision=$comision.id;versionEsperada=$comision.version}
Check 'asignar exige monto conocido' ($sinMonto.codigo -eq 400) $sinMonto.error
$ajenaAsigna=ApiError POST '/comisiones/repartos/propuestas' $brokerAjeno.token @{comision=$comision.id;versionEsperada=$comision.version;montoAgente=100}
Check 'un broker que no supervisa la captacion recibe 403' ($ajenaAsigna.codigo -eq 403) "codigo=$($ajenaAsigna.codigo)"

$propuesta=Api POST '/comisiones/repartos/propuestas' $broker.token @{comision=$comision.id;versionEsperada=$comision.version;montoAgente=250;observacion='Reparto explícito TEST'}
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $propuesta
$asignada=LeerComisionC5DePrueba $formalizado $broker.token
Check 'asignar fija el monto del agente' ($asignada.reparto.agente -eq 250) $asignada.reparto.agente
Check 'y la parte empresa conserva su base (400 - 250 = 150)' ($asignada.reparto.empresa -eq 150) $asignada.reparto.empresa

$vistaAgente=LeerComisionC5DePrueba $formalizado $agente.token
Check 'el agente no recibe netos internos del reparto' ($null -eq $vistaAgente.reparto.agente -and $null -eq $vistaAgente.reparto.empresa) 'montos ocultos'
Check 'conserva cálculo bruto y devengo/cobro diferenciados' ($vistaAgente.calculo.cuantia.importe -eq 400 -and $vistaAgente.devengo.estado -eq 'DEVENGADO' -and $vistaAgente.cobro.estado -eq 'SIN_COBROS') 'magnitudes'
Check 'TenantAdmin no obtiene la ficha comercial del contrato' ((ApiError GET "/contratos/oportunidad/$idOportunidad" $admin.token $null).codigo -eq 403) 'gobierno no opera'

$sinDatos=ApiError POST '/comisiones/movimientos/propuestas' $broker.token @{}
Check 'cobrar requiere importe, partes, fecha y evidencia' ($sinDatos.codigo -eq 400) $sinDatos.error
$selectorRetirado=ApiError POST '/comisiones/movimientos/propuestas' $broker.token @{estado='C'}
Check 'una marca de estado no registra dinero' ($selectorRetirado.codigo -eq 400) $selectorRetirado.error
$cobro=PrepararCobroC5DePrueba -Contexto $formalizado -Token $broker.token -TokenEvidencia $agente.token -Importe 400 -Moneda 'PEN' -Fecha $hoy -Referencia 'COBRO-F4-TEST'
$null=ConfirmarActuacionDePrueba -Token $broker.token -Propuesta $cobro
$cobrada=LeerComisionC5DePrueba $formalizado $broker.token
Check 'cobro verificado acredita recibido sin cambiar el pacto' ($cobrada.cobro.estado -eq 'COBRADO' -and @($cobrada.cobro.ingresosRecibidos|Where-Object{$_.moneda -eq 'PEN' -and $_.importe -eq 400}).Count -eq 1) 'cobro'
Check 'movimiento conserva forma y fecha efectiva' ((Invoke-ControlLocalE2ESql "select count(*) from comision_movimiento where id_comision_liquidacion=$($cobrada.id) and tipo='C' and fecha='$hoy' and forma_pago='TRANSFERENCIA'") -eq '1') 'movimiento'
Check 'el cobro conserva su identidad de comisión y antecedente' ($cobrada.id -eq $comision.id -and $cobrada.pacto.revisionAprobatoria -eq $formalizado.pacto.revisionAprobatoria) 'historia'

Write-Host "`n== 11. Contrato de errores y tenancy ==" -ForegroundColor Cyan
$sinToken = ApiError GET '/solicitudes' $null $null
Check 'sin token responde 401' ($sinToken.codigo -eq 401) "codigo=$($sinToken.codigo)"
$inexistente = ApiError GET '/solicitudes/99999999' $agente.token $null
Check 'una solicitud inexistente responde 404' ($inexistente.codigo -eq 404) "codigo=$($inexistente.codigo)"
$contratoInexistente = ApiError GET '/contratos/oportunidad/99999999' $agente.token $null
Check 'un contrato inexistente responde 404' ($contratoInexistente.codigo -eq 404) "codigo=$($contratoInexistente.codigo)"
$metodoMalo = ApiError DELETE "/solicitudes/$idSolicitud" $agente.token $null
Check 'el metodo equivocado es 405, no 500' ($metodoMalo.codigo -eq 405) "codigo=$($metodoMalo.codigo)"

Check 'contrato, comision y documentos nacen con tenant' `
    ((Invoke-ControlLocalE2ESql "select (select organizacion_id from contrato_alquiler where id_contrato_alquiler=$idContrato)||'|'||(select organizacion_id from comision_liquidacion where id_contrato_alquiler=$idContrato)||'|'||(select count(distinct organizacion_id) from documento_solicitud where id_solicitud=$idSolicitud)") -eq "$($fixtureE2E.organizacion)|$($fixtureE2E.organizacion)|1") 'tenant'

Write-Host "`n== 10. Ciclo juridico del contrato (Bloque 7) ==" -ForegroundColor Cyan

# El contrato de la seccion 9 quedo VIGENTE. Sobre el se prueba lo que el
# ciclo tiene que impedir y quien puede decidirlo.

# --- La transicion ilegal NO se persiste -------------------------------
# Firmar conserva D. El caso siguiente requiere una activación expresa, no
# una mutación automática del estado por confirmar el hito.
$activado = Api POST "/contratos/$idContrato/activar" $agente.token @{ motivo = 'Inicio contractual acreditado TEST' }
Check 'activar expresamente lleva el contrato firmado a VIGENTE' ($activado.estadoContrato -eq 'V') $activado.estadoContrato
# Anular es dejar sin efecto algo que nunca lo tuvo. Un contrato VIGENTE ya
# produjo efectos y se cobro comision por el: lo que lo termina es rescindir.
# Anularlo borraria ese alquiler de la historia.
$anularVigente = ApiError POST "/contratos/$idContrato/anular" $broker.token @{ motivo = 'prueba' }
Check 'anular un contrato VIGENTE se rechaza' ($anularVigente.codigo -eq 400) `
    "http=$($anularVigente.codigo) $($anularVigente.error)"
Check 'y el rechazo dice de que estado a cual' `
    ($anularVigente.error -like '*V*' -and $anularVigente.error -like '*A*') "error=$($anularVigente.error)"
Check 'el contrato sigue VIGENTE en la base' `
    ((Invoke-ControlLocalE2ESql "select estado_contrato from contrato_alquiler where id_contrato_alquiler=$idContrato") -eq 'V') 'estado'
Check 'y el intento ilegal no dejo historial' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='CONTRATO_ALQUILER' and id_entidad=$idContrato and estado_nuevo='A'") -eq '0') 'historial'

# --- Quien decide, y quien no ------------------------------------------
# "El broker decide; el agente registra". Rescindir corta un alquiler en curso
# y arrastra consecuencias economicas: no es el registro de un hecho consumado.
$rescindeAgente = ApiError POST "/contratos/$idContrato/rescindir" $agente.token @{ motivo = 'prueba' }
Check 'un AGENTE no rescinde: 403' ($rescindeAgente.codigo -eq 403) "http=$($rescindeAgente.codigo)"
$anulaAgente = ApiError POST "/contratos/$idContrato/anular" $agente.token @{ motivo = 'prueba' }
Check 'un AGENTE tampoco anula: 403' ($anulaAgente.codigo -eq 403) "http=$($anulaAgente.codigo)"

$tareasAntes = [int](Invoke-ControlLocalE2ESql "select count(*) from tarea where entidad_tipo='INMUEBLE' and entidad_id=$idLocal")
$rescision = Api POST "/contratos/$idContrato/rescindir" $broker.token @{ motivo = 'acuerdo entre partes' }
Check 'el BROKER SI rescinde' ($rescision.estadoContrato -eq 'S') "estado=$($rescision.estadoContrato)"

# --- La disponibilidad NO se toca sola ---------------------------------
# Terminar juridicamente un contrato no demuestra que el local este vacio,
# entregado ni apto para volver al mercado. El ciclo juridico y el comercial
# son dos cosas, y la segunda la decide una persona.
Check 'el inmueble NO se libera solo: sigue ALQUILADO' `
    ((Invoke-ControlLocalE2ESql "select disponibilidad_comercial from propiedad where id_propiedad=$idLocal") -eq 'A') 'disponibilidad'
$tareasDespues = [int](Invoke-ControlLocalE2ESql "select count(*) from tarea where entidad_tipo='INMUEBLE' and entidad_id=$idLocal")
Check 'pero queda una tarea de revision del inmueble' ($tareasDespues -gt $tareasAntes) `
    "antes=$tareasAntes despues=$tareasDespues"

# --- Repetir no es un no-op --------------------------------------------
# `Transiciones` ignora en silencio una transicion al mismo estado -da
# idempotencia al resto de entidades- y eso hacia que rescindir dos veces
# respondiera 200 sin cambiar nada, como si hubiera funcionado.
$segunda = ApiError POST "/contratos/$idContrato/rescindir" $broker.token @{ motivo = 'otra vez' }
Check 'rescindir dos veces se rechaza, no se ignora' ($segunda.codigo -eq 400) `
    "http=$($segunda.codigo) $($segunda.error)"

# Y desde un terminal no se sale.
$reactivar = ApiError POST "/contratos/$idContrato/activar" $agente.token @{ motivo = 'reabrir' }
Check 'un contrato rescindido no se reactiva' ($reactivar.codigo -eq 400) "http=$($reactivar.codigo)"

$huerfanas = Invoke-ControlLocalE2ESql @"
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
     -- C1/ArquitecturaTenancyTest: cuenta/auditoría/correlación previas al
     -- contexto no son cartera. Son las cuatro tablas híbridas clasificadas.
     and table_name not in ('catalogo_atributo','evento_seguridad','intento_acceso','vinculo_interaccion_segura')) t
"@
# Cuenta y MFA generan auditoría previa al contexto. V100 obliga que esos
# eventos no lleven Persona/Rol comercial; no son expedientes de organización.
Check 'la auditoria previa al contexto conserva su CHECK validado' `
    ((Invoke-ControlLocalE2ESql "select count(*) from pg_constraint where conrelid='evento_seguridad'::regclass and conname='ck_evento_ambito' and convalidated") -eq '1') 'guarda de ambito'
Check 'ningun evento sin organizacion acredita una Persona o Rol comercial' `
    ((Invoke-ControlLocalE2ESql "select count(*) from evento_seguridad where organizacion_id is null and (id_persona is not null or rol_efectivo is not null)") -eq '0') 'cuenta y contexto separados'
Check 'cero filas comerciales con organizacion_id NULL' ($huerfanas -eq '0') "nulos=$huerfanas"
if($huerfanas -ne '0') {
    $diagnostico=Invoke-ControlLocalE2ESql @"
with n as (
 select table_name,(xpath('/row/n/text()',query_to_xml(format('select count(*) as n from %I where organizacion_id is null',table_name),false,true,'')))[1]::text::int as n
 from information_schema.columns where table_schema='public' and column_name='organizacion_id'
)
select table_name||':'||n from n where n>0 order by table_name
"@
    Write-Host "E2E_TENANT_DIAGNOSTICO tablas/conteos: $diagnostico"
}

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
