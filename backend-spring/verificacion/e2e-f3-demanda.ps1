# =====================================================================
# E2E de la vertical F3 Demanda contra el API v2.
#
# Recorre cliente -> requerimiento -> coincidencias -> oportunidad ->
# visita -> interaccion con las tres bandas de rol, y comprueba lo que un
# test unitario no puede: los mensajes del contrato congelado sobre la BD
# real, la auditoria de cada transicion y que todo nace con el tenant.
#
# Contrato: docs/ai/contrato-congelado-f3-demanda.md
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f3-demanda
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

$sufijo = Get-Random -Minimum 1000 -Maximum 9999
$hoy = (Get-Date).ToString('yyyy-MM-dd')
# Las visitas van RELATIVAS a hoy. Estaban escritas con fechas literales de
# agosto de 2026 y el guion caducaba solo: al pasar el dia 20, la "agenda de
# proximas" dejaba de incluir una visita que el propio guion acababa de
# programar. No se vio antes porque la suite no arrancaba -su prologo llamaba a
# `POST /locales`, retirado en el Corte 0A-.
$enDias = { param($d) (Get-Date).AddDays($d).ToString('yyyy-MM-dd') }
$visitaProxima = & $enDias 5
$visitaReprogramada = & $enDias 9
$visitaOtra = & $enDias 7
$finEncargo = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login de las tres bandas de rol ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login broker' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login admin' ($admin.rol -eq 'ADMIN') $admin.rol

Write-Host "`n== 2. Prologo F2: cartera con una captacion ACTIVA ==" -ForegroundColor Cyan
# La demanda necesita oferta: local -> prospeccion -> captacion aprobada.
# Registrar NO es encargar (V75): la propiedad nace sin encargo y se prospecta.
# Antes `POST /locales` hacia las dos cosas y abria ademas la prospeccion de
# rebote; ahora las dos son explicitas, que es lo que el embudo dice.
$local = NuevoInmuebleSinEncargo -Token $agente.token -Direccion 'Av. Demanda 789' `
    -IdPropietario 43 -Metraje 150.5 -Rubro 'Cafeteria' -Descripcion "Prospecto F3 $sufijo"
$idLocal = $local.id
$idProspeccion = NuevaProspeccion -Token $agente.token -IdPropiedad $idLocal `
    -Observaciones "Prospeccion F3 $sufijo"
Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null | Out-Null
Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null | Out-Null
# La operacion se DECLARA. Hasta V75 captar la cableaba a ALQUILER y el importe
# lo copiaba del espejo de la propiedad; ahora los trae quien capta, porque es
# lo que el propietario acaba de aceptar.
$captada = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{
    operacion = 'ALQUILER'; importe = 9200; moneda = 'PEN'; comisionPactada = 100
}
$idCaptacion = $captada.idCaptacion
$codigoCaptacion = $captada.captacionCodigo
Api PUT "/captaciones/$idCaptacion" $agente.token @{
    fechaCaptacion = $hoy; fechaInicioVigencia = $hoy; fechaFinVigencia = $finEncargo
    comisionPactada = 100; idLocal = $idLocal; motivoOperacion = 'A'
    urgencia = 3; exclusividad = $true
} | Out-Null
$activa = Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'A'; observacion = 'Conforme' }
Check 'la captacion del prologo quedo ACTIVA' ($activa.estado -eq 'A') $activa.estado

# Segundo local: su prospeccion inicial queda EN PROCESO para poder
# colgarle una interaccion de contexto PROSPECCION mas abajo.
$local2 = NuevoInmuebleSinEncargo -Token $agente.token -Direccion 'Jr. Segundo 321' `
    -IdPropietario 43 -Metraje 120 -Rubro 'Cafeteria' -Descripcion "Prospecto F3B $sufijo"
$idProspeccion2 = NuevaProspeccion -Token $agente.token -IdPropiedad $local2.id `
    -Observaciones "Prospeccion F3B $sufijo"
Check 'la segunda prospeccion nace en P (en proceso)' `
    ((Api GET "/prospecciones?idLocal=$($local2.id)" $agente.token).items[0].estado -eq 'P') 'estado'

Write-Host "`n== 3. Clientes (catalogo COMPARTIDO del tenant) ==" -ForegroundColor Cyan
$clientesPrevios = (Api GET '/clientes?pagina=1&tamano=10' $agente.token).totalRecords
$clienteA = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "4578$sufijo"
    nombre = "Cliente Demanda A $sufijo"; telefono = '987000111'
    correo = "demanda.a.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$idClienteA = $clienteA.id
Check 'POST /clientes crea el rol CLIENTE' ($null -ne $idClienteA) 'id'
Check 'el cliente nace en el tenant de legado' `
    ((Sql "select organizacion_id from detalle_cliente where id_persona_rol=$idClienteA") -eq '1') 'organizacion_id'
Check 'idCliente del cable es un persona_rol de tipo CLIENTE' `
    ((Sql "select tipo_rol from persona_rol where id_persona_rol=$idClienteA") -eq 'CLIENTE') 'tipo_rol'

$clienteB = Api POST '/clientes' $agente.token @{
    tipoPersona = 'J'; tipoDocumento = 'R'; numeroDocumento = "2055120$sufijo"
    nombre = "Retail Demanda B $sufijo"; telefono = '014000222'
    correo = "demanda.b.$sufijo@demo.test"; rubroComercial = 'Retail'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$idClienteB = $clienteB.id

$totalClientes = (Api GET '/clientes?pagina=1&tamano=10' $agente.token).totalRecords
Check 'el catalogo crecio en 2' ($totalClientes -eq $clientesPrevios + 2) "$totalClientes vs $clientesPrevios"
# El catalogo es compartido para ADMIN y AGENTE; el BROKER es la excepcion
# (solo los clientes de su equipo), no al reves.
Check 'el ADMIN ve el mismo catalogo compartido' `
    ((Api GET '/clientes?pagina=1&tamano=10' $admin.token).totalRecords -eq $totalClientes) 'catalogo compartido'
Check 'el BROKER va acotado a su equipo (unico rol con alcance)' `
    ((Api GET '/clientes?pagina=1&tamano=10' $broker.token).totalRecords -le $totalClientes) 'alcance broker'

$fichaCliente = Api GET "/clientes/$idClienteA" $agente.token
Check 'GET /clientes/{id} devuelve la ficha' ($fichaCliente.nombre -eq "Cliente Demanda A $sufijo") $fichaCliente.nombre

$editado = Api PUT "/clientes/$idClienteA" $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "4578$sufijo"
    nombre = "Cliente Demanda A $sufijo"; telefono = '987000999'
    correo = "demanda.a.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
Check 'PUT /clientes/{id} actualiza' ($editado.telefono -eq '987000999') $editado.telefono

$sinPermiso = ApiError POST '/clientes' $broker.token @{ nombre = 'X'; tipoPersona = 'N' }
Check 'el BROKER no registra clientes (403)' ($sinPermiso.codigo -eq 403) "codigo=$($sinPermiso.codigo)"

# --- Filtros aditivos + resumen de la bandeja (extension 2026-08-02) ---
# La pantalla Angular dejo de descargar la cartera y filtrar en memoria. Los
# cuatro filtros son OPCIONALES: omitidos, la respuesta es la del cable
# congelado, y eso es lo primero que se comprueba (ya lo hizo $totalClientes).
# El texto busca en nombre, documento y rubro, que viven en DOS tablas: se
# resuelve por conjunto de candidatos, asi que aqui se prueba cada rama.
$porNombre = Api GET "/clientes?texto=Retail Demanda B $sufijo" $agente.token
Check 'el texto encuentra por nombre o razon social' `
    ($porNombre.totalRecords -eq 1 -and $porNombre.items[0].id -eq $idClienteB) `
    "total=$($porNombre.totalRecords)"
$porDocumento = Api GET "/clientes?texto=2055120$sufijo" $agente.token
Check 'el texto encuentra por numero de documento' `
    ($porDocumento.totalRecords -eq 1 -and $porDocumento.items[0].id -eq $idClienteB) `
    "total=$($porDocumento.totalRecords)"
# La rama del rubro vive en detalle_cliente: es la que un OR entre tablas no
# podia indexar y la que justifica el UNION.
$porRubro = Api GET '/clientes?texto=Cafeteria' $agente.token
Check 'el texto encuentra por RUBRO (la rama de la otra tabla)' `
    (@($porRubro.items | Where-Object { $_.id -eq $idClienteA }).Count -eq 1) `
    "total=$($porRubro.totalRecords)"
Check 'un texto sin coincidencias devuelve vacio, no la lista entera' `
    ((Api GET '/clientes?texto=ZZZNOEXISTE' $agente.token).totalRecords -eq 0) 'sin coincidencias'

$juridicas = Api GET '/clientes?tipoPersona=J' $agente.token
Check 'el filtro de tipo de persona recorta' `
    (@($juridicas.items | Where-Object { $_.tipoPersona -ne 'J' }).Count -eq 0 `
        -and $juridicas.totalRecords -lt $totalClientes) "total=$($juridicas.totalRecords)"
Check 'un tipo de persona inexistente devuelve vacio, no lo ignora' `
    ((Api GET '/clientes?tipoPersona=X' $agente.token).totalRecords -eq 0) 'tipo invalido'
Check 'el filtro exacto de rubro recorta' `
    (@((Api GET '/clientes?rubro=Cafeteria' $agente.token).items `
        | Where-Object { $_.rubroComercial -ne 'Cafeteria' }).Count -eq 0) 'rubro exacto'

$resumenClientes = Api GET '/clientes/resumen' $agente.token
Check 'el resumen cuenta el mismo conjunto que la lista' `
    ($resumenClientes.total -eq $totalClientes) `
    "resumen=$($resumenClientes.total) lista=$totalClientes"
Check 'activos + inactivos suman el total (no es una cuarta consulta)' `
    ($resumenClientes.activos + $resumenClientes.inactivos -eq $resumenClientes.total) `
    "$($resumenClientes.activos)+$($resumenClientes.inactivos)"
Check 'el resumen trae los rubros para el selector data-driven' `
    ($resumenClientes.rubros -contains 'Cafeteria' -and $resumenClientes.rubros -contains 'Retail') `
    ($resumenClientes.rubros -join ',')
$resumenFiltrado = Api GET '/clientes?texto=Cafeteria' $agente.token
Check 'el resumen aplica los mismos filtros que la lista' `
    ((Api GET '/clientes/resumen?texto=Cafeteria' $agente.token).total -eq $resumenFiltrado.totalRecords) `
    'resumen filtrado'

# La baja es LOGICA y el estado la distingue; reactivar es el PUT con estado A.
Api DELETE "/clientes/$idClienteB" $agente.token $null | Out-Null
Check 'la baja logica saca al cliente del cubo de activos' `
    ((@((Api GET '/clientes?estado=A' $agente.token).items `
        | Where-Object { $_.id -eq $idClienteB })).Count -eq 0) `
    'sigue apareciendo como activo'
Check 'y lo deja en el cubo de inactivos' `
    (@((Api GET '/clientes?estado=I' $agente.token).items | Where-Object { $_.id -eq $idClienteB }).Count -eq 1) `
    'inactivo'
Api PUT "/clientes/$idClienteB" $agente.token @{
    nombre = "Retail Demanda B $sufijo"; telefono = '014000222'
    correo = "demanda.b.$sufijo@demo.test"; rubroComercial = 'Retail'
    consentimientoContacto = $true; consentimientoUsoDato = $true; estado = 'A'
} | Out-Null
Check 'reactivar es el PUT con estado A, no un endpoint nuevo' `
    (@((Api GET '/clientes?estado=A' $agente.token).items | Where-Object { $_.id -eq $idClienteB }).Count -eq 1) `
    'reactivado'

Write-Host "`n== 4. Requerimientos (perfil de busqueda) ==" -ForegroundColor Cyan
$requerimiento = Api POST '/requerimientos' $agente.token @{
    idCliente = $idClienteA; rubro = 'Cafeteria'; tipoInmueble = 'LOCAL_COMERCIAL'
    rentaMin = 3000; rentaMax = 12000; moneda = 'PEN'; metrajeMin = 80; metrajeMax = 200
    estado = 'A'; observaciones = 'Local a pie de calle.'
    distritos = @('Miraflores', 'San Isidro')
}
$idRequerimiento = $requerimiento.id
Check 'POST /requerimientos crea el perfil A (activo)' ($requerimiento.estado -eq 'A') $requerimiento.estado
Check 'tipoInmueble viaja con el NOMBRE del enum (no CHAR(1))' `
    ($requerimiento.tipoInmueble -eq 'LOCAL_COMERCIAL') $requerimiento.tipoInmueble
Check 'el requerimiento nace con tenant' `
    ((Sql "select organizacion_id from requerimiento_cliente where id_requerimiento=$idRequerimiento") -eq '1') 'organizacion_id'
Check 'los distritos N:M quedaron enlazados' `
    ((Sql "select count(*) from requerimiento_distrito where id_requerimiento=$idRequerimiento") -eq '2') 'requerimiento_distrito'

$porCliente = Api GET "/requerimientos/cliente/$idClienteA" $agente.token
Check 'GET /requerimientos/cliente/{id} lista los del cliente' (@($porCliente).Count -ge 1) "items=$(@($porCliente).Count)"

$actualizado = Api PUT "/requerimientos/$idRequerimiento" $agente.token @{
    idCliente = $idClienteA; rubro = 'Cafeteria'; tipoInmueble = 'LOCAL_COMERCIAL'
    rentaMin = 3000; rentaMax = 15000; moneda = 'PEN'; metrajeMin = 80; metrajeMax = 200
    estado = 'A'; observaciones = 'Amplia el tope de renta.'
    distritos = @('Miraflores', 'San Isidro')
}
Check 'PUT /requerimientos/{id} actualiza la renta' ($actualizado.rentaMax -eq 15000) $actualizado.rentaMax

$pausado = Api POST "/requerimientos/$idRequerimiento/estado" $agente.token @{ estado = 'P' }
Check 'POST /{id}/estado pausa el requerimiento' ($pausado.estado -eq 'P') $pausado.estado
$reactivado = Api POST "/requerimientos/$idRequerimiento/estado" $agente.token @{ estado = 'A' }
Check 'y lo reactiva (solo A alimenta el matching)' ($reactivado.estado -eq 'A') $reactivado.estado

Write-Host "`n== 5. Coincidencias: las tres entradas del matching ==" -ForegroundColor Cyan
# OJO con el id del sobre: en cliente -> propiedades el `id` es el de la
# CAPTACION (la oferta viaja por su captacion), no el de la propiedad.
$desdeCliente = Api GET "/clientes/$idClienteA/coincidencias" $agente.token
Check 'cliente -> propiedades responde el sobre completo' `
    ($null -ne $desdeCliente.origen -and $null -ne $desdeCliente.items) 'origen/items'
Check 'el default de pagina es 6 (§7)' ($desdeCliente.pageSize -eq 6) $desdeCliente.pageSize
$nuestro = @($desdeCliente.items | Where-Object { $PSItem.captacionId -eq $idCaptacion })
Check 'el local recien captado casa con el requerimiento' ($nuestro.Count -eq 1) "items=$($desdeCliente.items.Count)"
if ($nuestro.Count -eq 1) {
    Check 'los 5 criterios aplicables cumplen -> puntaje 100' ($nuestro[0].puntaje -eq 100) $nuestro[0].puntaje
    Check 'viajan las razones legibles (cumple)' (@($nuestro[0].cumple).Count -ge 1) ($nuestro[0].cumple -join '|')
    Check 'la coincidencia es accionable (proponerRuta)' `
        ($nuestro[0].proponerRuta -like "*captacionId=$idCaptacion*") $nuestro[0].proponerRuta
}

$topeado = Api GET "/clientes/$idClienteA/coincidencias?page_size=50" $agente.token
Check 'page_size se topa en 24' ($topeado.pageSize -le 24) $topeado.pageSize

# Regla de "vista personal" (idsClientesDelActor): para un no-ADMIN la
# demanda propia son los clientes que YA tienen oportunidad del equipo. El
# cliente recien creado todavia no la tiene, asi que el AGENTE no lo ve
# como coincidencia de la captacion, pero el ADMIN si (va sin restriccion).
$desdeCaptacionId = Api GET "/captaciones/$idCaptacion/coincidencias" $agente.token
Check 'captacion -> clientes responde por id' ($null -ne $desdeCaptacionId.items) 'items'
$desdeCaptacionCodigo = Api GET "/captaciones/$codigoCaptacion/coincidencias" $agente.token
Check 'la misma ruta acepta el CODIGO (cable v1)' `
    ($desdeCaptacionCodigo.total -eq $desdeCaptacionId.total) "$($desdeCaptacionCodigo.total) vs $($desdeCaptacionId.total)"
Check 'sin oportunidad previa el AGENTE aun no ve al cliente nuevo' `
    (@($desdeCaptacionId.items | Where-Object { $PSItem.clienteId -eq $idClienteA }).Count -eq 0) `
    "items=$($desdeCaptacionId.items.Count)"
$captacionAdmin = Api GET "/captaciones/$idCaptacion/coincidencias" $admin.token
$clienteParaAdmin = @($captacionAdmin.items | Where-Object { $PSItem.clienteId -eq $idClienteA })
Check 'el ADMIN si lo ve (su matching va sin vista personal)' ($clienteParaAdmin.Count -eq 1) "items=$($captacionAdmin.items.Count)"
if ($clienteParaAdmin.Count -eq 1) {
    Check 'y la coincidencia de captacion es accionable' `
        ($clienteParaAdmin[0].proponerRuta -like "*clienteId=$idClienteA*") $clienteParaAdmin[0].proponerRuta
}

# Señal temprana: la prospeccion sin captacion responde, pero NO es
# accionable — proponerRuta viaja VACIA (cadena vacia, no null).
$desdeProspeccion = Api GET "/prospecciones/$idProspeccion2/coincidencias" $admin.token
Check 'prospeccion -> clientes responde (señal temprana)' ($desdeProspeccion.items.Count -ge 1) "items=$($desdeProspeccion.items.Count)"
Check 'sin captacion la prospeccion NO es accionable' `
    (@($desdeProspeccion.items | Where-Object { -not [string]::IsNullOrEmpty($PSItem.proponerRuta) }).Count -eq 0) 'proponerRuta'

Write-Host "`n== 6. Oportunidad comercial (el hub) ==" -ForegroundColor Cyan
$oportunidad = Api POST '/oportunidades' $agente.token @{
    idCliente = $idClienteA; idCaptacion = $idCaptacion
    observaciones = 'Interesada tras el matching de cartera.'
}
$idOportunidad = $oportunidad.id
Check 'POST /oportunidades nace ABIERTA' ($oportunidad.estado -eq 'A') $oportunidad.estado
Check 'el correlativo sigue el formato OP-*' ($oportunidad.codigoOportunidad -match '^OP-') $oportunidad.codigoOportunidad
Check 'la oportunidad nace con tenant' `
    ((Sql "select organizacion_id from oportunidad_comercial where id_oportunidad=$idOportunidad") -eq '1') 'organizacion_id'
# Convencion de Transiciones: iniciar() fija el estado de nacimiento y NO
# escribe historial (no hay transicion); solo aplicar() audita. Igual que en
# el E2E de F2, donde prospeccion y captacion cuentan solo sus aplicar().
Check 'el alta NO escribe historial (nacer no es transicionar)' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad") -eq '0') 'historial'

$repetida = ApiError POST '/oportunidades' $agente.token @{ idCliente = $idClienteA; idCaptacion = $idCaptacion }
Check 'no se duplica la oportunidad abierta del par cliente/captacion' `
    ($repetida.error -eq 'Ya existe una oportunidad abierta para el cliente y captacion.') $repetida.error

$sinCliente = ApiError POST '/oportunidades' $agente.token @{ idCaptacion = $idCaptacion }
Check 'sin cliente responde el mensaje del cable' ($sinCliente.error -eq 'Selecciona un cliente interesado.') $sinCliente.error

$ficha = Api GET "/oportunidades/$idOportunidad" $agente.token
Check 'GET /oportunidades/{id} resuelve cliente y captacion' `
    ($null -ne $ficha.clienteNombre -and $null -ne $ficha.codigoCaptacion) "$($ficha.clienteNombre)/$($ficha.codigoCaptacion)"

$filtrada = Api GET "/oportunidades?idCliente=$idClienteA&pagina=1&tamano=10" $agente.token
Check 'el filtro por cliente devuelve la nuestra' `
    (@($filtrada.items | Where-Object { $PSItem.id -eq $idOportunidad }).Count -eq 1) "total=$($filtrada.totalRecords)"

$cierre = ApiError POST "/oportunidades/$idOportunidad/cierre-exitoso" $agente.token $null
Check 'cierre-exitoso SIEMPRE responde 400 (cable real)' ($cierre.codigo -eq 400) "codigo=$($cierre.codigo)"

Write-Host "`n== 7. Visita: maquina de estados por PATCH ==" -ForegroundColor Cyan
$visita = Api POST '/visitas' $agente.token @{
    idOportunidad = $idOportunidad; fechaVisita = $visitaProxima; horaVisita = '16:00'
    observaciones = 'Primera visita coordinada.'
}
$idVisita = $visita.id
Check 'POST /visitas programa en P' ($visita.estado -eq 'P') $visita.estado
Check 'la visita nace con tenant' `
    ((Sql "select organizacion_id from visita where id_visita=$idVisita") -eq '1') 'organizacion_id'

$reprogramada = Api PATCH "/visitas/$idVisita/reprogramar" $agente.token @{ fechaVisita = $visitaReprogramada; horaVisita = '10:30' }
Check 'PATCH reprogramar -> G' ($reprogramada.estado -eq 'G') $reprogramada.estado
Check 'la nueva fecha quedo guardada' ($reprogramada.fechaVisita -match $visitaReprogramada) $reprogramada.fechaVisita

$proximas = Api GET '/visitas/proximas?tamano=8' $agente.token
Check 'la agenda proximas incluye la visita viva' `
    (@($proximas.items | Where-Object { $PSItem.id -eq $idVisita }).Count -eq 1) "total=$($proximas.totalRecords)"
# El calendario se pregunta por el mes de la visita, no por un mes fijo.
$fechaRe = [datetime]::ParseExact($visitaReprogramada, 'yyyy-MM-dd', $null)
$mes = Api GET "/visitas/mes?anio=$($fechaRe.Year)&mes=$($fechaRe.Month)" $agente.token
Check 'el calendario del mes la trae' `
    (@($mes.items | Where-Object { $PSItem.id -eq $idVisita }).Count -eq 1) "items=$($mes.items.Count)"

$sinDesenlace = ApiError PATCH "/visitas/$idVisita/resultado" $agente.token @{ resultado = 'INTERESADO' }
Check 'no se registra desenlace antes de realizarla' `
    ($sinDesenlace.error -eq 'Solo una visita realizada y sin resultado admite registrar el desenlace.') $sinDesenlace.error

$realizada = Api PATCH "/visitas/$idVisita/realizar" $agente.token $null
Check 'PATCH realizar -> R' ($realizada.estado -eq 'R') $realizada.estado

$desenlace = Api PATCH "/visitas/$idVisita/resultado" $agente.token @{
    resultado = 'INTERESADO'; observaciones = 'Le gusto el frente.'
    nivelInteres = 4; objecionPrincipal = 'P'; opinionPrecio = 'A'; proximaAccion = 'O'
}
Check 'el desenlace guarda el resultado' ($desenlace.resultado -eq 'INTERESADO') $desenlace.resultado
Check 'y el nivel de interes' ($desenlace.nivelInteres -eq 4) $desenlace.nivelInteres
Check 'la oportunidad sigue ABIERTA tras un desenlace positivo' `
    ((Api GET "/oportunidades/$idOportunidad" $agente.token).estado -eq 'A') 'estado'
Check 'las 2 transiciones de visita quedaron auditadas (G y R)' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='VISITA' and id_entidad=$idVisita") -eq '2') 'historial'
Check 'y ninguna fila de auditoria quedo sin tenant' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='VISITA' and id_entidad=$idVisita and organizacion_id is null") -eq '0') 'organizacion_id'

Write-Host "`n== 8. Interacciones: bitacora POLIMORFICA ==" -ForegroundColor Cyan
$iOportunidad = Api POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad; canalContacto = 'W'; resultado = 'NEGOCIANDO'
    observaciones = 'Negocia el plazo del contrato.'
}
Check 'contexto OPORTUNIDAD se infiere del id' ($iOportunidad.contexto -eq 'OPORTUNIDAD') $iOportunidad.contexto

$iCliente = Api POST '/interacciones' $agente.token @{
    idCliente = $idClienteA; canalContacto = 'L'; resultado = 'REQUIERE_OPCIONES'
    observaciones = 'Pide mas alternativas en San Isidro.'
}
Check 'contexto CLIENTE se infiere del id' ($iCliente.contexto -eq 'CLIENTE') $iCliente.contexto

$iCaptacion = Api POST '/interacciones' $agente.token @{
    idCaptacion = $idCaptacion; canalContacto = 'E'; resultado = 'DOCS_SOLICITADOS'
    observaciones = 'Se piden los documentos del propietario.'
}
Check 'contexto CAPTACION se infiere del id' ($iCaptacion.contexto -eq 'CAPTACION') $iCaptacion.contexto

# Una interaccion de PROSPECCION mueve el embudo del propietario Y lo audita
# (la v1 lo hacia a mano y sin registrar).
$auditoriaPrevia = [int](Sql "select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion2")
$iProspeccion = Api POST '/interacciones' $agente.token @{
    idProspeccion = $idProspeccion2; canalContacto = 'P'; resultado = 'REUNION_AGENDADA'
    observaciones = 'Reunion pactada con el propietario.'
}
Check 'contexto PROSPECCION se infiere del id' ($iProspeccion.contexto -eq 'PROSPECCION') $iProspeccion.contexto
Check 'la interaccion de prospeccion movio el embudo a R' `
    ((Sql "select estado from prospeccion where id_prospeccion=$idProspeccion2") -eq 'R') 'estado prospeccion'
Check 'y esa transicion quedo auditada (mejora sobre la v1)' `
    ([int](Sql "select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion2") -eq $auditoriaPrevia + 1) 'historial'

Check 'toda interaccion nace con tenant' `
    ((Sql "select organizacion_id from interaccion_comercial where id_interaccion=$($iOportunidad.id)") -eq '1') 'organizacion_id'

# --- Validaciones del cable ---
$resultadoAjeno = ApiError POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad; canalContacto = 'W'; resultado = 'DOCS_SOLICITADOS'
}
Check 'la allow-list por contexto rechaza el resultado ajeno' `
    ($resultadoAjeno.error -eq 'Resultado no valido para OPORTUNIDAD: DOCS_SOLICITADOS') $resultadoAjeno.error

$sinCanal = ApiError POST '/interacciones' $agente.token @{ idOportunidad = $idOportunidad; resultado = 'INTERESADO' }
Check 'el canal de contacto es obligatorio' ($sinCanal.error -eq 'El canal de contacto es obligatorio.') $sinCanal.error

$canalMalo = ApiError POST '/interacciones' $agente.token @{
    idOportunidad = $idOportunidad; canalContacto = 'Z'; resultado = 'INTERESADO'
}
Check 'canal invalido nombra el codigo' ($canalMalo.error -eq 'Canal de contacto invalido: Z') $canalMalo.error

# REGRESION: el id de la entidad se exige ANTES que el agente. Si algun dia
# vuelve "Agente no encontrado para interaccion.", se rompio el orden del cable.
$sinEntidad = ApiError POST '/interacciones' $agente.token @{ canalContacto = 'W'; resultado = 'INTERESADO' }
Check 'sin ids exige la oportunidad (orden de validacion del cable)' `
    ($sinEntidad.error -eq 'La oportunidad de la interaccion es obligatoria.') $sinEntidad.error

$dosFiltros = ApiError GET "/interacciones?idOportunidad=$idOportunidad&idCliente=$idClienteA" $agente.token $null
Check 'solo se filtra por UNA entidad a la vez' `
    ($dosFiltros.error -eq 'Filtra por una sola entidad de interaccion.') $dosFiltros.error

$grupoPropietario = Api GET '/interacciones?grupo=PROPIETARIO&pagina=1&tamano=50' $agente.token
$idsPropietario = @($grupoPropietario.items | ForEach-Object { $PSItem.contexto })
Check 'grupo=PROPIETARIO solo trae PROSPECCION/CAPTACION' `
    (@($idsPropietario | Where-Object { $PSItem -notin @('PROSPECCION', 'CAPTACION') }).Count -eq 0) `
    ($idsPropietario -join ',')

$porOportunidad = Api GET "/interacciones?idOportunidad=$idOportunidad&pagina=1&tamano=20" $agente.token
Check 'el filtro por oportunidad devuelve la suya' `
    (@($porOportunidad.items | Where-Object { $PSItem.id -eq $iOportunidad.id }).Count -eq 1) "total=$($porOportunidad.totalRecords)"

Write-Host "`n== 9. Alcance por rol: DOS reglas distintas de broker ==" -ForegroundColor Cyan
# Oportunidades y visitas alcanzan por la CAPTACION del equipo;
# interacciones, por AGENTE SUPERVISADO. No se unifican.
$oportunidadesBroker = Api GET '/oportunidades?pagina=1&tamano=50' $broker.token
Check 'el BROKER alcanza la oportunidad por la captacion de su equipo' `
    (@($oportunidadesBroker.items | Where-Object { $PSItem.id -eq $idOportunidad }).Count -eq 1) "total=$($oportunidadesBroker.totalRecords)"

$visitasBroker = Api GET '/visitas?pagina=1&tamano=50' $broker.token
Check 'el BROKER alcanza la visita por la misma regla' `
    (@($visitasBroker.items | Where-Object { $PSItem.id -eq $idVisita }).Count -eq 1) "total=$($visitasBroker.totalRecords)"

$interaccionesBroker = Api GET '/interacciones?pagina=1&tamano=100' $broker.token
Check 'el BROKER alcanza la interaccion por agente supervisado' `
    (@($interaccionesBroker.items | Where-Object { $PSItem.id -eq $iOportunidad.id }).Count -eq 1) "total=$($interaccionesBroker.totalRecords)"

$brokerProgramando = ApiError POST '/visitas' $broker.token @{
    idOportunidad = $idOportunidad; fechaVisita = '2026-09-01'; horaVisita = '09:00'
}
Check 'el BROKER no programa visitas (403)' ($brokerProgramando.codigo -eq 403) "codigo=$($brokerProgramando.codigo)"

$adminVe = Api GET '/oportunidades?pagina=1&tamano=50' $admin.token
Check 'el ADMIN gobierna todo su tenant' `
    (@($adminVe.items | Where-Object { $PSItem.id -eq $idOportunidad }).Count -eq 1) "total=$($adminVe.totalRecords)"

Write-Host "`n== 10. Desenlace de no continuidad: cierra la oportunidad ==" -ForegroundColor Cyan
$oportunidadB = Api POST '/oportunidades' $agente.token @{
    idCliente = $idClienteB; idCaptacion = $idCaptacion; observaciones = 'Segundo interesado.'
}
$visitaB = Api POST '/visitas' $agente.token @{
    idOportunidad = $oportunidadB.id; fechaVisita = $visitaOtra; horaVisita = '11:00'
}
Api PATCH "/visitas/$($visitaB.id)/realizar" $agente.token $null | Out-Null
$desenlaceB = Api PATCH "/visitas/$($visitaB.id)/resultado" $agente.token @{
    resultado = 'NO_INTERESADO'; observaciones = 'El precio excede su tope.'; razonNoContinuidad = 'P'
}
Check 'el desenlace de no continuidad se guarda' ($desenlaceB.resultado -eq 'NO_INTERESADO') $desenlaceB.resultado
$cerrada = Api GET "/oportunidades/$($oportunidadB.id)" $agente.token
Check 'la visita cerro la oportunidad en N' ($cerrada.estado -eq 'N') $cerrada.estado
Check 'quedo el motivo tipificado, no solo el estado' `
    ((Sql "select count(*) from motivo_no_continuidad where id_oportunidad=$($oportunidadB.id)") -eq '1') 'motivo_no_continuidad'

$nivelConNoContinuidad = ApiError PATCH "/visitas/$idVisita/resultado" $agente.token @{
    resultado = 'DESCARTADO'; nivelInteres = 3
}
Check 'no se acepta nivel de interes con resultado de no continuidad' `
    ($nivelConNoContinuidad.codigo -eq 400) "codigo=$($nivelConNoContinuidad.codigo)"

# Cierre manual por no continuidad sobre la oportunidad A.
$noContinuidad = Api POST "/oportunidades/$idOportunidad/no-continuidad" $agente.token @{
    razon = 'U'; observaciones = 'Prefiere otra zona.'
}
Check 'POST /{id}/no-continuidad cierra en N' ($noContinuidad.estado -eq 'N') $noContinuidad.estado
Check 'la transicion de cierre quedo auditada (A -> N)' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad") -eq '1') 'historial'
Check 'el cierre por visita tambien se audito en la oportunidad B' `
    ((Sql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$($oportunidadB.id)") -eq '1') 'historial'

Write-Host "`n== 11. Contrato de errores y tenancy ==" -ForegroundColor Cyan
# REGRESION: el metodo equivocado es 405, no 500 (la v1 conserva el estado
# de WebApplicationException en su mapper).
$metodoMalo = ApiError GET '/requerimientos' $agente.token $null
Check 'metodo no soportado responde 405, no 500' ($metodoMalo.codigo -eq 405) "codigo=$($metodoMalo.codigo)"
Check 'y el cuerpo sigue siendo {"error": ...}' ($metodoMalo.error -like 'HTTP 405*') $metodoMalo.error

$sinToken = ApiError GET '/oportunidades' $null $null
Check 'sin token responde 401' ($sinToken.codigo -eq 401) "codigo=$($sinToken.codigo)"

$inexistente = ApiError GET '/oportunidades/99999999' $agente.token $null
Check 'una oportunidad inexistente responde 404' ($inexistente.codigo -eq 404) "codigo=$($inexistente.codigo)"

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
