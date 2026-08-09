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
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$agenteAjeno = Api POST '/auth/login' $null @{ usuario = 'ltorres'; contrasena = 'Agente2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
$brokerAjeno = Api POST '/auth/login' $null @{ usuario = 'psoto'; contrasena = 'Broker2026' }
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login agente responsable (AGE-001)' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login agente de otro equipo (AGE-003)' ($agenteAjeno.rol -eq 'AGENTE') $agenteAjeno.rol
Check 'login broker supervisor (BRK-001)' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login broker de otro equipo (BRK-002)' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login admin' ($admin.rol -eq 'ADMIN') $admin.rol

Write-Host "`n== 2. Prologo F2 + F3: oferta, captacion ACTIVA y oportunidad ==" -ForegroundColor Cyan
$local = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-F4$sufijo"; direccion = 'Av. Cierre 456'; distrito = 'Miraflores'
    metraje = 140; precioReferencial = 8000; monedaReferencial = 'PEN'; rubroPermitido = 'Cafeteria'
    idPropietario = 43; estadoPublicacion = 'P'
}
$idLocal = $local.id
$idProspeccion = (Api GET "/prospecciones?idLocal=$idLocal" $agente.token).items[0].id
Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null | Out-Null
# comisionPactada es un PORCENTAJE: 5 % de la renta propuesta.
$captada = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{ comisionPactada = 5 }
$idCaptacion = $captada.idCaptacion
Api PUT "/captaciones/$idCaptacion" $agente.token @{
    fechaCaptacion = $hoy; fechaInicioVigencia = $hoy; fechaFinVigencia = $finEncargo
    comisionPactada = 5; idLocal = $idLocal; motivoOperacion = 'A'
    urgencia = 3; exclusividad = $true
} | Out-Null
Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'A'; observacion = 'Conforme' } | Out-Null
Check 'la captacion del prologo quedo ACTIVA' `
    ((Sql "select estado from captacion where id_captacion=$idCaptacion") -eq 'A') 'estado captacion'

$idPublicacion = (Api POST "/locales/$idLocal/publicaciones" $agente.token @{
    canal = 'URBANIA'; estado = 'P'; rentaPublicada = 8500; moneda = 'PEN'
    urlPublicacion = "https://demo.test/f4-$sufijo"
}).id
Check 'el local tiene una publicacion PUBLICADA (para el efecto 6)' ($null -ne $idPublicacion) 'publicacion'

$cliente = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "4890$sufijo"
    nombre = "Cliente Cierre $sufijo"; telefono = '987444555'
    correo = "cierre.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$idCliente = $cliente.id
$oportunidad = Api POST '/oportunidades' $agente.token @{
    idCliente = $idCliente; idCaptacion = $idCaptacion
    observaciones = 'Interesado tras la visita.'
}
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
    ((Sql "select organizacion_id from solicitud_alquiler where id_solicitud=$idSolicitud") -eq '1') 'organizacion_id'
# Convencion de Transiciones: iniciar() NO audita (nacer no es transicionar).
Check 'nacer no deja fila en historial_estado' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='SOLICITUD_ALQUILER' and id_entidad=$idSolicitud") -eq '0') 'historial'

# Efecto lateral invisible en el REST: la oportunidad pasa a S. En la v2 va
# por Transiciones, asi que -a diferencia de la v1- SI deja auditoria (MEJ-01).
Check 'el alta movio la oportunidad a S (solicitud creada)' `
    ((Api GET "/oportunidades/$idOportunidad" $agente.token).estado -eq 'S') 'estado oportunidad'
Check 'y esa transicion SI se audito (la v1 no lo hacia)' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad and estado_nuevo='S'") -eq '1') 'historial oportunidad'

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
    ((Sql "select count(*) from pg_indexes where tablename='solicitud_alquiler' and indexdef like '%id_oportunidad%' and indexdef like 'CREATE UNIQUE%'") -ge '1') 'unico'

# NOTA: el otro caso de unicidad —proponer un codigoSolicitud que ya existe,
# que responde 409 como el cable— NO se puede probar aqui: a esta altura la
# unica oportunidad del guion ya esta en S, asi que la precondicion "ABIERTA"
# corta antes de llegar al codigo. Haria falta una segunda oportunidad ABIERTA
# (y por tanto un segundo local con captacion ACTIVA, por el unico parcial).
# Queda cubierto por SolicitudServiceImplTest.unCodigoPropuestoRepetidoEsCONFLICTONoBadRequest.

Write-Host "`n== 4. Lectura y alcance de /solicitudes (§7: por AGENTE) ==" -ForegroundColor Cyan
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
Check 'el ADMIN ve todo el tenant' `
    ((Api GET "/solicitudes?idOportunidad=$idOportunidad" $admin.token).totalRecords -eq 1) 'alcance admin'

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
# La clave cuelga de `tenant/{organizacionId}/{codigo}` desde la preparacion
# para S3 (2026-08-05): el prefijo por tenant es donde se apoyan las politicas
# de aislamiento del bucket, y se aplico ANTES de mover binarios para no tener
# que moverlos dos veces. Se comprueba la FORMA, no el id concreto: fijar
# `tenant/1/` ataria la suite al orden de siembra del fixture.
Check 'la clave del almacen cuelga de tenant/{org}/{codigo de la solicitud}' `
    ($doc1.rutaArchivo -match "^tenant/\d+/$([Regex]::Escape($solicitud.codigoSolicitud))/") $doc1.rutaArchivo
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

# El tipo se valida al REGISTRAR (el vocabulario vive en el service, la web no
# ve el dominio), asi que el intento con tipo 'Z' llego a subir su binario y
# hubo que borrarlo. El invariante: tantos binarios en el almacen como
# documentos en el expediente.
# Ojo con PowerShell 5.1: hay que asignar ANTES de envolver con @(); un
# @(Api ...) directo cuenta el resultado de la funcion como un solo objeto.
$expediente = Api GET "/solicitudes/$idSolicitud/documentos" $agente.token
$documentos = @($expediente).Count
# La carpeta se DERIVA de la clave que devolvio el API (tenant/{org}/{codigo})
# en vez de reconstruirse a mano. Reconstruirla fue justo lo que rompio esta
# comprobacion al llegar el prefijo por tenant: seguia mirando la ruta vieja,
# contaba 0 binarios y acusaba de huerfanos a documentos que estaban bien.
$carpetaAlmacen = (($doc1.rutaArchivo -split '/') | Select-Object -First 3) -join '/'
$binarios = [int](docker exec $e2e.ApiContainer sh -c "ls -1 /almacen-e2e/$carpetaAlmacen 2>/dev/null | wc -l")
Check 'un alta rechazada no deja huerfanos en el almacen' `
    ($binarios -eq $documentos) "binarios=$binarios documentos=$documentos"

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
    ((Sql "select count(*) from historial_estado where entidad_tipo='DOCUMENTO_SOLICITUD' and id_entidad=$($doc4.id)") -eq '1') 'historial documento'

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

Write-Host "`n== 9. POST /contratos: la cascada de SIETE efectos (§6) ==" -ForegroundColor Cyan
$sinSolicitud = ApiError POST '/contratos' $agente.token @{ }
Check 'sin idSolicitud responde el mensaje del cable' `
    ($sinSolicitud.error -eq 'Selecciona la solicitud aprobada que se va a alquilar.') $sinSolicitud.error
$estadoMalo = ApiError POST '/contratos' $agente.token @{ idSolicitud = $idSolicitud; estadoContrato = 'X' }
Check 'un estado que no existe en el enum' ($estadoMalo.error -eq 'Estado de contrato invalido.') $estadoMalo.error
$estadoNoCierre = ApiError POST '/contratos' $agente.token @{ idSolicitud = $idSolicitud; estadoContrato = 'P' }
Check 'un estado valido que no es de cierre' `
    ($estadoNoCierre.error -eq 'El cierre solo admite los estados Firmado o Vigente.') $estadoNoCierre.error
$futura = ApiError POST '/contratos' $agente.token @{
    idSolicitud = $idSolicitud; fechaCierre = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
}
Check 'la fecha de cierre no puede ser futura' ($futura.error -eq 'La fecha de cierre no puede ser futura.') $futura.error
$cierreAjeno = ApiError POST '/contratos' $agenteAjeno.token @{ idSolicitud = $idSolicitud }
Check 'el contrato SI exige que la solicitud sea del agente (403)' ($cierreAjeno.codigo -eq 403) "codigo=$($cierreAjeno.codigo)"
$brokerCierra = ApiError POST '/contratos' $broker.token @{ idSolicitud = $idSolicitud }
Check 'el BROKER no cierra (403): el cierre lo registra el agente' ($brokerCierra.codigo -eq 403) "codigo=$($brokerCierra.codigo)"

$contrato = Api POST '/contratos' $agente.token @{
    idSolicitud = $idSolicitud; fechaCierre = $hoy; estadoContrato = 'V'
    incidencias = 'Firma en oficina.'
}
$idContrato = $contrato.id
Check 'POST /contratos responde 201 y el contrato queda VIGENTE' ($contrato.estadoContrato -eq 'V') $contrato.estadoContrato
Check 'las condiciones se LEEN de la solicitud, no se copian' `
    ($contrato.rentaMensual -eq 8000 -and $contrato.plazoContratoMeses -eq 24) `
    "$($contrato.rentaMensual)/$($contrato.plazoContratoMeses)"
Check 'la fecha fin se deriva (inicio + plazo)' ($contrato.fechaFinContrato -eq '2028-09-01') $contrato.fechaFinContrato

# --- Los siete efectos, uno por uno ---
Check 'efecto 2: la comision nace P con la bruta (5% de 8000 = 400)' `
    ($contrato.comisionEstado -eq 'P' -and $contrato.comisionGenerada -eq 400) `
    "$($contrato.comisionEstado)/$($contrato.comisionGenerada)"
Check 'efecto 2: renta y comision conservan PEN sin conversion' `
    ($contrato.moneda -eq 'PEN' -and $contrato.monedaComision -eq 'PEN') `
    "$($contrato.moneda)|$($contrato.monedaComision)"
Check 'efecto 3: la oportunidad se cerro EXITOSA (F)' `
    ((Sql "select estado from oportunidad_comercial where id_oportunidad=$idOportunidad") -eq 'F') 'oportunidad'
Check 'efecto 4: la solicitud quedo CERRADA (C)' `
    ((Sql "select estado from solicitud_alquiler where id_solicitud=$idSolicitud") -eq 'C') 'solicitud'
Check 'efecto 5: la captacion se cerro con fecha y motivo de alquiler' `
    ((Sql "select estado||'|'||fecha_cierre||'|'||motivo_cierre from captacion where id_captacion=$idCaptacion") -eq "C|$hoy|A") 'captacion'
Check 'efecto 6: el local conserva registro A y disponibilidad A (alquilado)' `
    ((Sql "select estado_registro||'|'||disponibilidad_comercial from propiedad where id_propiedad=$idLocal") -eq 'A|A') 'propiedad'
Check 'efecto 6: se registro el precio con hito C (cerrado real)' `
    ((Sql "select count(*) from precio_propiedad where id_propiedad=$idLocal and hito='C' and monto=8000") -eq '1') 'precio'
Check 'efecto 6: las publicaciones se dieron de baja' `
    ((Sql "select estado from publicacion where id_publicacion=$idPublicacion") -eq 'C') 'publicacion'

# La v1 movia estos cuatro estados a mano y NO auditaba ninguno (MEJ-01).
$auditoria = Sql @"
select count(*) from historial_estado
 where (entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad and estado_nuevo='F')
    or (entidad_tipo='SOLICITUD_ALQUILER' and id_entidad=$idSolicitud and estado_nuevo='C')
    or (entidad_tipo='CAPTACION' and id_entidad=$idCaptacion and estado_nuevo='C')
    or (entidad_tipo='DISPONIBILIDAD_PROPIEDAD' and id_entidad=$idLocal and estado_nuevo='A')
"@
Check 'la cascada dejo CUATRO filas en historial_estado (MEJ-01)' ($auditoria -eq '4') "filas=$auditoria"
Check 'el contrato NACE con iniciar(): no suma fila propia' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='CONTRATO_ALQUILER' and id_entidad=$idContrato") -eq '0') 'historial contrato'

$segundoContrato = ApiError POST '/contratos' $agente.token @{ idSolicitud = $idSolicitud }
Check 'una oportunidad no admite un segundo contrato' `
    ($segundoContrato.error -eq 'Solo se puede registrar el alquiler de una solicitud aprobada.') $segundoContrato.error

Write-Host "`n== 10. Lectura de contratos y comision (§5) ==" -ForegroundColor Cyan
$porOportunidad = Api GET "/contratos/oportunidad/$idOportunidad" $agente.token
Check 'GET /contratos/oportunidad/{id} resuelve el contrato' ($porOportunidad.id -eq $idContrato) $porOportunidad.id
$paginaContratos = Api GET '/contratos' $agente.token
Check 'el tamano por defecto de /contratos es 100, no 10' ($paginaContratos.pageSize -eq 100) $paginaContratos.pageSize
Check 'el BROKER alcanza por CAPTACION supervisada' `
    (@((Api GET '/contratos' $broker.token).items | Where-Object { $_.id -eq $idContrato }).Count -eq 1) 'alcance broker'
Check 'el BROKER de otro equipo no lo ve' `
    (@((Api GET '/contratos' $brokerAjeno.token).items | Where-Object { $_.id -eq $idContrato }).Count -eq 0) 'alcance broker ajeno'

$adminSinPermiso = ApiError POST "/contratos/$idContrato/comision/asignar" $admin.token @{ montoAgente = 100 }
Check 'los gates de comision son del BROKER SIN admin (403)' ($adminSinPermiso.codigo -eq 403) "codigo=$($adminSinPermiso.codigo)"
$sinMonto = ApiError POST "/contratos/$idContrato/comision/asignar" $broker.token @{ }
Check 'asignar exige el monto del agente' ($sinMonto.error -eq 'Indica el monto del agente.') $sinMonto.error
$ajenaAsigna = ApiError POST "/contratos/$idContrato/comision/asignar" $brokerAjeno.token @{ montoAgente = 100 }
Check 'un broker que no supervisa la captacion recibe 403' ($ajenaAsigna.codigo -eq 403) "codigo=$($ajenaAsigna.codigo)"

$asignada = Api POST "/contratos/$idContrato/comision/asignar" $broker.token @{ montoAgente = 250 }
Check 'asignar fija el monto del agente' ($asignada.montoAgente -eq 250) $asignada.montoAgente
Check 'y el de la empresa se calcula solo (400 - 250 = 150)' ($asignada.montoEmpresa -eq 150) $asignada.montoEmpresa

$vistaAgente = Api GET "/contratos/oportunidad/$idOportunidad" $agente.token
Check 'el AGENTE no ve el reparto (Jackson non_null: ni viajan)' `
    ($null -eq $vistaAgente.montoAgente -and $null -eq $vistaAgente.montoEmpresa) 'montos ocultos'
Check 'pero SI ve la comision bruta y su estado' `
    ($vistaAgente.comisionGenerada -eq 400 -and $vistaAgente.comisionEstado -eq 'P') 'comision bruta'
Check 'el ADMIN si ve el reparto (solo lectura)' `
    ((Api GET "/contratos/oportunidad/$idOportunidad" $admin.token).montoAgente -eq 250) 'admin ve neto'

$sinEstado = ApiError POST "/contratos/$idContrato/comision/cobro" $broker.token @{ }
Check 'el cobro exige el estado' `
    ($sinEstado.error -eq 'Indica el estado del cobro (Cobrada o Anulada).') $sinEstado.error
$estadoCobroMalo = ApiError POST "/contratos/$idContrato/comision/cobro" $broker.token @{ estado = 'P' }
Check 'el cobro solo admite Cobrada o Anulada' `
    ($estadoCobroMalo.error -eq 'El cobro solo admite los estados Cobrada o Anulada.') $estadoCobroMalo.error

$cobrada = Api POST "/contratos/$idContrato/comision/cobro" $broker.token @{
    estado = 'C'; fechaCobro = $hoy; formaPago = 'TRANSFERENCIA'
}
Check 'el cobro deja la comision C' ($cobrada.comisionEstado -eq 'C') $cobrada.comisionEstado
Check 'el estado viaja como codigo y la forma de pago conserva su catalogo' `
    ($cobrada.formaPago -eq 'TRANSFERENCIA') $cobrada.formaPago
Check 'el cobro se audito en la comision' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='COMISION_LIQUIDACION' and estado_nuevo='C'") -ge '1') 'historial comision'

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
    ((Sql "select (select organizacion_id from contrato_alquiler where id_contrato_alquiler=$idContrato)||'|'||(select organizacion_id from comision_liquidacion where id_contrato_alquiler=$idContrato)||'|'||(select count(distinct organizacion_id) from documento_solicitud where id_solicitud=$idSolicitud)") -eq '1|1|1') 'tenant'

Write-Host "`n== 10. Ciclo juridico del contrato (Bloque 7) ==" -ForegroundColor Cyan

# El contrato de la seccion 9 quedo VIGENTE. Sobre el se prueba lo que el
# ciclo tiene que impedir y quien puede decidirlo.

# --- La transicion ilegal NO se persiste -------------------------------
# Anular es dejar sin efecto algo que nunca lo tuvo. Un contrato VIGENTE ya
# produjo efectos y se cobro comision por el: lo que lo termina es rescindir.
# Anularlo borraria ese alquiler de la historia.
$anularVigente = ApiError POST "/contratos/$idContrato/anular" $broker.token @{ motivo = 'prueba' }
Check 'anular un contrato VIGENTE se rechaza' ($anularVigente.codigo -eq 400) `
    "http=$($anularVigente.codigo) $($anularVigente.error)"
Check 'y el rechazo dice de que estado a cual' `
    ($anularVigente.error -like '*V*' -and $anularVigente.error -like '*A*') "error=$($anularVigente.error)"
Check 'el contrato sigue VIGENTE en la base' `
    ((Sql "select estado_contrato from contrato_alquiler where id_contrato_alquiler=$idContrato") -eq 'V') 'estado'
Check 'y el intento ilegal no dejo historial' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='CONTRATO_ALQUILER' and id_entidad=$idContrato and estado_nuevo='A'") -eq '0') 'historial'

# --- Quien decide, y quien no ------------------------------------------
# «El broker decide; el agente registra». Rescindir corta un alquiler en curso
# y arrastra consecuencias economicas: no es el registro de un hecho consumado.
$rescindeAgente = ApiError POST "/contratos/$idContrato/rescindir" $agente.token @{ motivo = 'prueba' }
Check 'un AGENTE no rescinde: 403' ($rescindeAgente.codigo -eq 403) "http=$($rescindeAgente.codigo)"
$anulaAgente = ApiError POST "/contratos/$idContrato/anular" $agente.token @{ motivo = 'prueba' }
Check 'un AGENTE tampoco anula: 403' ($anulaAgente.codigo -eq 403) "http=$($anulaAgente.codigo)"

$tareasAntes = [int](Sql "select count(*) from tarea where entidad_tipo='INMUEBLE' and entidad_id=$idLocal")
$rescision = Api POST "/contratos/$idContrato/rescindir" $broker.token @{ motivo = 'acuerdo entre partes' }
Check 'el BROKER SI rescinde' ($rescision.estadoContrato -eq 'S') "estado=$($rescision.estadoContrato)"

# --- La disponibilidad NO se toca sola ---------------------------------
# Terminar juridicamente un contrato no demuestra que el local este vacio,
# entregado ni apto para volver al mercado. El ciclo juridico y el comercial
# son dos cosas, y la segunda la decide una persona.
Check 'el inmueble NO se libera solo: sigue ALQUILADO' `
    ((Sql "select disponibilidad_comercial from propiedad where id_propiedad=$idLocal") -eq 'A') 'disponibilidad'
$tareasDespues = [int](Sql "select count(*) from tarea where entidad_tipo='INMUEBLE' and entidad_id=$idLocal")
Check 'pero queda una tarea de revision del inmueble' ($tareasDespues -gt $tareasAntes) `
    "antes=$tareasAntes despues=$tareasDespues"

# --- Repetir no es un no-op --------------------------------------------
# `Transiciones` ignora en silencio una transicion al mismo estado —da
# idempotencia al resto de entidades— y eso hacia que rescindir dos veces
# respondiera 200 sin cambiar nada, como si hubiera funcionado.
$segunda = ApiError POST "/contratos/$idContrato/rescindir" $broker.token @{ motivo = 'otra vez' }
Check 'rescindir dos veces se rechaza, no se ignora' ($segunda.codigo -eq 400) `
    "http=$($segunda.codigo) $($segunda.error)"

# Y desde un terminal no se sale.
$reactivar = ApiError POST "/contratos/$idContrato/activar" $agente.token @{ motivo = 'reabrir' }
Check 'un contrato rescindido no se reactiva' ($reactivar.codigo -eq 400) "http=$($reactivar.codigo)"

$huerfanas = Sql @"
select coalesce(sum(nulos),0) from (
  select (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I where organizacion_id is null', table_name), false, true, '')))[1]::text::int as nulos
  from information_schema.columns where column_name='organizacion_id' and table_schema='public') t
"@
Check 'cero filas con organizacion_id NULL en toda la BD' ($huerfanas -eq '0') "nulos=$huerfanas"

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
