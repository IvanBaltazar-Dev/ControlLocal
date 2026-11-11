# =====================================================================
# E2E de la vertical F3 Demanda contra el API v2.
#
# Recorre cliente -> requerimiento -> coincidencias -> oportunidad ->
# visita -> interaccion con las tres bandas de rol, y comprueba lo que un
# test unitario no puede: los mensajes del contrato Core vigente sobre la BD
# real, la auditoria de cada transicion y que todo nace con el tenant.
#
# Autoridad: docs/1INF50/BROX-diseno-software.md (RF028-RF030) y
# docs/1INF50/BROX-KAIROS-catalogo-operativo.md (CAT-E18/E20, D1-D4).
# C4: work/c4-c5-20261030/BROX-ESPECIFICACION-IMPLEMENTACION-C4-C5-2026-10-30.md, secciones1/4/10.
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f3-demanda
#             (compatible con Windows PowerShell 5.1 y con pwsh 7)
# =====================================================================
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) }
    catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
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

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_1'
Write-Host "`n== 1. Login de las tres bandas de rol ==" -ForegroundColor Cyan
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login agente' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login broker' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login admin' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

$propietarioE2E = NuevoPropietarioC2DePrueba -Token $agente.token -Nombre "Propietario TEST F3 $sufijo"
$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_2'
Write-Host "`n== 2. Prologo C2: Encargo aprobado mediante revision humana ==" -ForegroundColor Cyan
# La demanda necesita oferta: local -> prospeccion -> captacion aprobada.
# Registrar NO es encargar (V75): la propiedad nace sin encargo y se prospecta.
# Antes `POST /locales` hacia las dos cosas y abria ademas la prospeccion de
# rebote; ahora las dos son explicitas, que es lo que el embudo dice.
$local = NuevoInmuebleSinEncargo -LocalIndependienteTest -Token $agente.token -Direccion 'Av. Demanda 789' `
    -IdPropietario $propietarioE2E.id -Metraje 150.5 -Rubro 'Cafeteria' -Descripcion "Prospecto F3 $sufijo"
$idLocal = $local.id
$idProspeccion = NuevaProspeccion -Token $agente.token -IdPropiedad $idLocal `
    -Observaciones "Prospeccion F3 $sufijo"
$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'CONTACTAR_PROSPECCION'
Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null | Out-Null
$script:EtapaFixtureE2E = 'REUNION_PROSPECCION'
Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null | Out-Null
$script:EtapaFixtureE2E = 'PROPUESTA_PROSPECCION'
Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null | Out-Null
# La operacion se DECLARA. Hasta V75 captar la cableaba a ALQUILER y el importe
# lo copiaba del espejo de la propiedad; ahora los trae quien capta, porque es
# lo que el propietario acaba de aceptar.
$script:EtapaFixtureE2E = 'CAPTAR_PROSPECCION'
$captada = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{
    operacion = 'ALQUILER'; importe = 9200; moneda = 'PEN'; comisionPactada = 100
    tipoComision = 'E'; baseCalculo = 'R'; tratamientoIgv = 'N'; exclusividad = $true
    inicioEncargo = $hoy; finEncargo = $finEncargo
}
$idCaptacion = $captada.idCaptacion
$codigoCaptacion = $captada.captacionCodigo
$null = HabilitarEncargoC2DePrueba -IdEncargo $idCaptacion -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato'
$activa = Api GET "/captaciones/$idCaptacion" $agente.token $null
Check 'la captacion del prologo quedo ACTIVA' ($activa.estado -eq 'A') $activa.estado

# Segundo local: su prospeccion inicial queda EN PROCESO para poder
# colgarle una interaccion de contexto PROSPECCION mas abajo.
$local2 = NuevoInmuebleSinEncargo -LocalIndependienteTest -Token $agente.token -Direccion 'Jr. Segundo 321' `
    -IdPropietario $propietarioE2E.id -Metraje 120 -Rubro 'Cafeteria' -Descripcion "Prospecto F3B $sufijo"
$idProspeccion2 = NuevaProspeccion -Token $agente.token -IdPropiedad $local2.id `
    -Observaciones "Prospeccion F3B $sufijo"
Check 'la segunda prospeccion nace en P (en proceso)' `
    ((Api GET "/prospecciones?idLocal=$($local2.id)" $agente.token).items[0].estado -eq 'P') 'estado'

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_3'
Write-Host "`n== 3. Clientes de la cartera comercial autorizada ==" -ForegroundColor Cyan
$clientesPrevios = (Api GET '/clientes?pagina=1&tamano=10' $agente.token).totalRecords
$clienteA = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "4578$sufijo"
    nombre = "Cliente Demanda A $sufijo"; telefono = '987000111'
    correo = "demanda.a.$sufijo@demo.test"; rubroComercial = 'Cafeteria'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$idClienteA = $clienteA.id
Check 'POST /clientes crea el rol CLIENTE' ($null -ne $idClienteA) 'id'
Check 'el cliente nace en el tenant autenticado' `
    ((Invoke-ControlLocalE2ESql "select organizacion_id from detalle_cliente where id_persona_rol=$idClienteA") -eq $fixtureE2E.organizacion) 'organizacion_id'
Check 'idCliente del cable es un persona_rol de tipo CLIENTE' `
    ((Invoke-ControlLocalE2ESql "select tipo_rol from persona_rol where id_persona_rol=$idClienteA") -eq 'CLIENTE') 'tipo_rol'

$clienteB = Api POST '/clientes' $agente.token @{
    tipoPersona = 'J'; tipoDocumento = 'R'; numeroDocumento = "2055120$sufijo"
    nombre = "Retail Demanda B $sufijo"; telefono = '014000222'
    correo = "demanda.b.$sufijo@demo.test"; rubroComercial = 'Retail'
    consentimientoContacto = $true; consentimientoUsoDato = $true
}
$idClienteB = $clienteB.id

$totalClientes = (Api GET '/clientes?pagina=1&tamano=10' $agente.token).totalRecords
Check 'el catalogo crecio en 2' ($totalClientes -eq $clientesPrevios + 2) "$totalClientes vs $clientesPrevios"
# La lectura comercial conserva AGENTE/BROKER; gobierno tiene su propia puerta.
Check 'gobernar no concede el catalogo comercial de clientes' `
    ((ApiError GET '/clientes?pagina=1&tamano=10' $admin.token $null).codigo -eq 403) 'gobierno'
Check 'el BROKER consulta su equipo autorizado' `
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

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_4'
Write-Host "`n== 4. Demanda propia con contexto, version e importes explicitos ==" -ForegroundColor Cyan
$datosDemanda = @{
    interesado = @{ tipo = 'CLIENTE'; idCliente = $idClienteA }
    operacion = 'ALQUILER'; tiposInmueble = @('L')
    rubro = 'Cafeteria'; presupuestoMin = 3000; presupuestoMax = 12000; moneda = 'PEN'
    conceptoPresupuesto = 'TOTAL_MENSUAL_OBLIGATORIO'; periodoPresupuesto = 'MENSUAL'
    metrajeMin = 80; metrajeMax = 200; estado = 'A'; observaciones = 'Busqueda TEST de local a pie de calle.'
    distritos = @('Miraflores', 'San Isidro'); finalidad = 'Gestion inmobiliaria TEST explicita'
}
$referenciaDuplicada=$datosDemanda.Clone();$referenciaDuplicada.idCliente=$idClienteA
$antesReferenciaDuplicada=[long](Invoke-ControlLocalE2ESql "select count(*) from requerimiento_cliente where organizacion_id=$($fixtureE2E.organizacion)")
$rechazoReferencia=Invoke-ComandoC2DePrueba POST '/requerimientos' $agente.token $referenciaDuplicada "f3-demanda-doble-ref-$sufijo" -EsperarError
Check 'dos fuentes de interesado se rechazan sin crear Demanda' `
    ($rechazoReferencia.codigo -eq 400 -and [long](Invoke-ControlLocalE2ESql "select count(*) from requerimiento_cliente where organizacion_id=$($fixtureE2E.organizacion)") -eq $antesReferenciaDuplicada) 'referencia unica'
$requerimiento = Invoke-ComandoC2DePrueba POST '/requerimientos' $agente.token $datosDemanda "f3-demanda-$sufijo"
$idRequerimiento = [long]$requerimiento.id
Check 'Core registra Demanda activa con presupuesto y moneda declarados' `
    ($requerimiento.estado -eq 'A' -and $requerimiento.presupuestoMax -eq 12000 `
        -and $requerimiento.moneda -eq 'PEN' -and $requerimiento.conceptoPresupuesto -eq 'TOTAL_MENSUAL_OBLIGATORIO') 'Demanda'
Check 'la Demanda pertenece al tenant autenticado' `
    ([long](Invoke-ControlLocalE2ESql "select organizacion_id from requerimiento_cliente where id_requerimiento=$idRequerimiento") -eq $fixtureE2E.organizacion) 'tenant'
Check 'los dos distritos declarados quedan enlazados' `
    ([int](Invoke-ControlLocalE2ESql "select count(*) from requerimiento_distrito where id_requerimiento=$idRequerimiento") -eq 2) 'distritos'
$porCliente = @(Api GET "/requerimientos/cliente/$idClienteA" $agente.token $null)
Check 'la ficha del cliente consulta sus Demandas autorizadas' `
    (@($porCliente | Where-Object { $_.id -eq $idRequerimiento }).Count -eq 1) 'Demanda concreta'
$datosEdicion = $datosDemanda.Clone(); $datosEdicion.presupuestoMax = 15000; $datosEdicion.versionEsperada = $requerimiento.version
$actualizado = Invoke-ComandoC2DePrueba PUT "/requerimientos/$idRequerimiento" $agente.token $datosEdicion "f3-demanda-editar-$sufijo"
Check 'editar conserva concepto y aumenta solo el maximo declarado' `
    ($actualizado.presupuestoMax -eq 15000 -and $actualizado.presupuestoMin -eq 3000 `
        -and $actualizado.moneda -eq 'PEN' -and $actualizado.conceptoPresupuesto -eq 'TOTAL_MENSUAL_OBLIGATORIO') 'presupuesto'
$pausaRechazada = Invoke-ComandoC2DePrueba PUT "/requerimientos/$idRequerimiento" $agente.token @{
    versionEsperada = $actualizado.version; estado = 'P'
} "f3-demanda-pausa-no-contractual-$sufijo" -EsperarError
$vigente = Api GET "/requerimientos/$idRequerimiento" $agente.token $null
Check 'un estado generico no retira Demanda ni altera version o presupuesto' `
    ($pausaRechazada.codigo -eq 400 -and $vigente.estado -eq 'A' -and $vigente.version -eq $actualizado.version `
        -and $vigente.presupuestoMax -eq 15000) 'RF030 requiere actuacion'
$demandaParaFin=Invoke-ComandoC2DePrueba POST '/requerimientos' $agente.token $datosDemanda "f3-demanda-fin-$sufijo"
$propuestaFin=Api POST "/requerimientos/$($demandaParaFin.id)/fin/propuestas" $agente.token @{
    versionEsperada=$demandaParaFin.version;tipo='RETIRADA';fechaDelHecho=$hoy;motivo='Retiro expreso del requerimiento ficticio TEST separado.'
}
$finDemanda=ConfirmarActuacionDePrueba -Token $agente.token -Propuesta $propuestaFin
$retirada=Api GET "/requerimientos/$($demandaParaFin.id)" $agente.token $null
$historiaDemanda=Api GET "/requerimientos/$($demandaParaFin.id)?version=$($demandaParaFin.version)" $agente.token $null
Check 'fin exacto conserva identidad fecha motivo y presupuesto del antecedente' `
    ($finDemanda.idDemanda -eq $demandaParaFin.id -and $finDemanda.tipo -eq 'RETIRADA' -and $finDemanda.fechaDelHecho -eq $hoy `
        -and $retirada.estado -eq 'R' -and $retirada.presupuestoMax -eq 12000 `
        -and $historiaDemanda.estado -eq 'A' -and $historiaDemanda.version -eq $demandaParaFin.version) 'RF030 e historia'

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_5'
Write-Host "`n== 5. Matching por origen/version, categorias y valores G/H/S/U/W ==" -ForegroundColor Cyan
# D2 exige hechos propios de disponibilidad, distintos de aprobar el Encargo
# o registrar la Demanda. Este escenario declara ambos disponibles HOY como
# datos FICTICIOS TEST, con las versiones de recurso y confirmacion leidas de Core.
foreach ($recursoD2 in @(@{ tipo='ENCARGO'; id=$idCaptacion }, @{ tipo='DEMANDA'; id=$idRequerimiento })) {
    $vistaD2 = Api GET "/disponibilidad/$($recursoD2.tipo)/$($recursoD2.id)" $agente.token $null
    if ($null -eq $vistaD2.versionRecurso -or $null -eq $vistaD2.versionConfirmacion `
        -or $vistaD2.clave.tipo -ne $recursoD2.tipo -or $vistaD2.clave.idRecurso -ne $recursoD2.id) {
        throw 'Core no devolvio el recurso y las versiones D2 requeridas por el fixture.'
    }
    $datosD2 = @{
        tipo=$recursoD2.tipo; idRecurso=$recursoD2.id
        versionRecursoObservada=$vistaD2.versionRecurso; versionConfirmacionEsperada=$vistaD2.versionConfirmacion
        disponible=$true; fechaHecho=$null; fechaSinHora=$hoy
        fuente='Disponibilidad declarada del escenario FICTICIO TEST F3.'
        evidenciaRef="TEST-F3-$sufijo-$($recursoD2.tipo)-$($recursoD2.id)"
    }
    $confirmacionD2 = Invoke-ComandoC2DePrueba POST '/disponibilidad/confirmaciones' $agente.token $datosD2 `
        ('e2e-f3-d2-' + [guid]::NewGuid().ToString('N'))
    $actualD2 = Api GET "/disponibilidad/$($recursoD2.tipo)/$($recursoD2.id)" $agente.token $null
    Check "D2 registra disponibilidad explicita TEST de $($recursoD2.tipo) sin cambiar version del recurso" `
        ($confirmacionD2.aplicacion -eq 'APLICADA' -and $confirmacionD2.registrado.disponible -eq $true `
            -and $confirmacionD2.registrado.fechaSinHora -eq $hoy -and $null -eq $confirmacionD2.registrado.fechaHecho `
            -and $actualD2.vigente.id -eq $confirmacionD2.registrado.id `
            -and $actualD2.versionRecurso -eq $vistaD2.versionRecurso `
            -and $actualD2.versionConfirmacion -eq $confirmacionD2.registrado.version) 'hecho contextual D2, no estado contractual'
}
$vigente = Api GET "/requerimientos/$idRequerimiento" $agente.token $null

function MostrarDiagnosticoMatchingF3($Respuesta, [string]$Categoria, $IdEncargo) {
    # Solo magnitudes y codigos; no referencias, datosPermitidos ni contenido privado.
    $candidatos = @($Respuesta.grupos | ForEach-Object { $_.candidatos } | Where-Object { $null -ne $_ })
    $propios = @($candidatos | Where-Object { $_.datosPermitidos.idEncargo -eq $IdEncargo } | ForEach-Object {
        [ordered]@{ categoria=$_.categoria; g=$_.g; h=$_.h; causas=@($_.razones | ForEach-Object { $_.causa } | Where-Object { $null -ne $_ } | Select-Object -Unique) }
    })
    Write-Host ('E2E_MATCHING_DIAG ' + ([ordered]@{ categoriaSolicitada=$Categoria; total=$Respuesta.total; candidatos=$candidatos.Count; propios=$propios } | ConvertTo-Json -Depth 6 -Compress))
}
$baseDemanda = "/coincidencias?origen=DEMANDA&idOrigen=$idRequerimiento&versionOrigen=$($vigente.version)&tipoVersion=DEMANDA&modalidad=COMERCIAL&capas=L0"
$desdeDemanda = Api GET ($baseDemanda + '&categoria=OBLIGATORIAS_PENDIENTES') $agente.token $null
$confirmadas = Api GET ($baseDemanda + '&categoria=COMERCIAL_CONFIRMADA&tamano=50') $agente.token $null
$candidatosPropios = @($desdeDemanda.grupos | ForEach-Object { $_.candidatos })
Check 'el Encargo propio se encuentra sin convertir renta en total mensual completo' `
    ($candidatosPropios.Count -eq 1 -and $candidatosPropios[0].h -eq 0) 'obligatorias pendientes por evidencia insuficiente'
if (-not ($candidatosPropios.Count -eq 1 -and $candidatosPropios[0].h -eq 0)) {
    MostrarDiagnosticoMatchingF3 $desdeDemanda 'OBLIGATORIAS_PENDIENTES' $idCaptacion
    MostrarDiagnosticoMatchingF3 $confirmadas 'COMERCIAL_CONFIRMADA' $idCaptacion
    foreach ($categoriaDiagnostico in @('HABILITACION_PENDIENTE','NO_HABILITADA','INCOMPATIBLE')) {
        $diagnostico = Api GET ($baseDemanda + '&categoria=' + $categoriaDiagnostico) $agente.token $null
        MostrarDiagnosticoMatchingF3 $diagnostico $categoriaDiagnostico $idCaptacion
    }
}
Check 'la falta del total mensual no se rescata como coincidencia confirmada' `
    ($confirmadas.total -eq 0 -and @($confirmadas.grupos|ForEach-Object{$_.candidatos}).Count -eq 0) 'D1/D3: sin rescate por score'
Check 'la seleccion conserva la Demanda exacta y el alcance solicitado' `
    ($desdeDemanda.seleccion.idOrigen -eq $idRequerimiento -and $desdeDemanda.seleccion.versionOrigen -eq $vigente.version `
        -and $desdeDemanda.seleccion.tipoVersion -eq 'DEMANDA' -and $desdeDemanda.versionRegla) 'seleccion explicita'
Check 'el tamano por defecto es veinte y el explicito conserva cincuenta' `
    ($desdeDemanda.seleccion.tamano -eq 20 -and $confirmadas.seleccion.tamano -eq 50) 'D4'
foreach ($grupo in @($desdeDemanda.grupos)) {
    Check 'el grupo conserva version y referencia de la Demanda' `
        ($grupo.versionDemanda -eq $vigente.version -and $grupo.referenciaDemanda) 'grupo'
    foreach ($c in @($grupo.candidatos)) {
        Check 'lo pendiente no se promueve a coincidencia comercial confirmada' `
            ($c.categoria -eq 'OBLIGATORIAS_PENDIENTES' -and $c.g -in @(0,1,2) -and $c.h -in @(0,1,2)) 'categoria y logica trivalente'
        Check 'S/U/W conservan sus magnitudes sin convertirse a un porcentaje' `
            ($c.s -ge 0 -and $c.u -ge 0 -and $c.w -ge ($c.s + $c.u) -and $null -eq $c.puntaje) 'S/U/W'
        $razonPresupuesto=@($c.razones|Where-Object{$_.clave -eq 'base:presupuesto'})
        Check 'el presupuesto conserva su causa propia sin sustituir total mensual por renta' `
            ($razonPresupuesto.Count -eq 1 -and $razonPresupuesto[0].resultado -eq 0 -and $razonPresupuesto[0].causa -eq 'DATO_FALTANTE') 'D3: total no acreditado'
    }
}
$encargoMatching = Api GET "/encargos/$idCaptacion" $agente.token $null
$baseEncargo = "/coincidencias?origen=ENCARGO&idOrigen=$idCaptacion&versionOrigen=$($encargoMatching.version)&tipoVersion=ENCARGO&modalidad=COMERCIAL&capas=L0&categoria=OBLIGATORIAS_PENDIENTES"
$desdeEncargo = Api GET $baseEncargo $agente.token $null
Check 'el origen Encargo conserva id y version propios sin elegir un Cliente' `
    ($desdeEncargo.seleccion.idOrigen -eq $idCaptacion -and $desdeEncargo.seleccion.tipoVersion -eq 'ENCARGO') 'Encargo'
$fisicoProspeccion = Api GET "/propiedades/$($local2.id)" $agente.token $null
$baseProspeccion = "/coincidencias?origen=PROSPECCION&idOrigen=$idProspeccion2&versionOrigen=$($fisicoProspeccion.versionFisica)&tipoVersion=FISICA&modalidad=PRELIMINAR&capas=L0&categoria=POTENCIAL"
$desdeProspeccion = Api GET $baseProspeccion $agente.token $null
Check 'la prospeccion sin Encargo conserva modalidad preliminar y version fisica' `
    ($desdeProspeccion.seleccion.modalidad -eq 'PRELIMINAR' -and $desdeProspeccion.seleccion.tipoVersion -eq 'FISICA') 'preliminar'
Check 'gobernar no concede la consulta de coincidencias comerciales' `
    ((ApiError GET $baseEncargo $admin.token $null).codigo -eq 403) 'gobierno'
$sinVersion = ApiError GET "/coincidencias?origen=DEMANDA&idOrigen=$idRequerimiento&tipoVersion=DEMANDA&modalidad=COMERCIAL&capas=L0&categoria=COMERCIAL_CONFIRMADA" $agente.token $null
Check 'una consulta no supone la version de origen ausente' ($sinVersion.codigo -eq 400) 'version requerida'
$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_6'
Write-Host "`n== 6. Oportunidad comercial (el hub) ==" -ForegroundColor Cyan
$oportunidad = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $idClienteA -Token $agente.token `
    -EvidenciaInteres 'Manifestacion expresa FICTICIA de interes del caso TEST E2E C4.' -Observaciones 'Interesada tras el matching de cartera.'
$idOportunidad = $oportunidad.id
Check 'POST /oportunidades nace ABIERTA' ($oportunidad.estado -eq 'A') $oportunidad.estado
Check 'el correlativo sigue el formato OP-*' ($oportunidad.codigoOportunidad -match '^OP-') $oportunidad.codigoOportunidad
Check 'la oportunidad nace con tenant' `
    ((Invoke-ControlLocalE2ESql "select organizacion_id from oportunidad_comercial where id_oportunidad=$idOportunidad") -eq $fixtureE2E.organizacion) 'organizacion_id'
# Convencion de Transiciones: iniciar() fija el estado de nacimiento y NO
# escribe historial (no hay transicion); solo aplicar() audita. Igual que en
# el E2E de F2, donde prospeccion y captacion cuentan solo sus aplicar().
Check 'el alta NO escribe historial (nacer no es transicionar)' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad") -eq '0') 'historial'

$encargoObservado = Api GET "/encargos/$idCaptacion" $agente.token $null
$propuestaDuplicada = Api POST '/oportunidades/propuestas' $agente.token @{
    idEncargo = $idCaptacion; versionEncargo = $encargoObservado.version
    interesado = @{ tipo = 'CLIENTE'; idCliente = $idClienteA }; origen = 'DIRECTO'
    evidenciaInteres = @{ descripcion = 'Manifestacion TEST explicita repetida sobre el mismo par.' }
}
$repetida = ConfirmarActuacionDePrueba -Token $agente.token -Propuesta $propuestaDuplicada -EsperarRechazo
Check 'no se duplica la oportunidad abierta del par cliente/captacion' `
    ($repetida.estado -eq 'RECHAZADO' -and $repetida.motivo -eq 'CONTENIDO_CAMBIADO') $repetida.motivo

$sinCliente = ApiError POST '/oportunidades/propuestas' $agente.token @{
    idEncargo = $idCaptacion; versionEncargo = $encargoObservado.version; origen = 'DIRECTO'
    evidenciaInteres = @{ descripcion = 'Intento TEST sin interesado identificado.' }
}
Check 'sin interesado se rechaza sin inventar un rol Cliente' ($sinCliente.codigo -eq 400) 'interesado ausente'

$ficha = Api GET "/oportunidades/$idOportunidad" $agente.token
Check 'GET /oportunidades/{id} resuelve cliente y captacion' `
    ($null -ne $ficha.clienteNombre -and $null -ne $ficha.codigoCaptacion) "$($ficha.clienteNombre)/$($ficha.codigoCaptacion)"

$filtrada = Api GET "/oportunidades?idCliente=$idClienteA&pagina=1&tamano=10" $agente.token
Check 'el filtro por cliente devuelve la nuestra' `
    (@($filtrada.items | Where-Object { $PSItem.id -eq $idOportunidad }).Count -eq 1) "total=$($filtrada.totalRecords)"

$cierre = ApiError POST "/oportunidades/$idOportunidad/cierre-exitoso" $agente.token $null
Check 'no existe una puerta que finja cierre exitoso de la oportunidad' ($cierre.codigo -in @(404,405)) "codigo=$($cierre.codigo)"

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_7'
Write-Host "`n== 7. Visita: agenda y ocurrencia declarada por sus casos propios ==" -ForegroundColor Cyan
$visita = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $visitaProxima -Hora '16:00' -Token $agente.token -Observaciones 'Primera visita coordinada.'
$idVisita = $visita.id
Check 'POST /visitas programa en P' ($visita.estado -eq 'P') $visita.estado
Check 'la visita nace con tenant' `
    ((Invoke-ControlLocalE2ESql "select organizacion_id from visita where id_visita=$idVisita") -eq $fixtureE2E.organizacion) 'organizacion_id'

$reciboReprogramacion = Invoke-ComandoC2DePrueba PUT "/visitas/$idVisita/programacion" $agente.token @{
    idVisita = $idVisita; versionEsperada = $visita.version; fechaVisita = $visitaReprogramada
    horaVisita = '10:30'; motivo = 'Nueva fecha explicita TEST de la visita.'
} "f3-reprogramar-$sufijo"
$reprogramada = $reciboReprogramacion.resultado
Check 'la reprogramacion AUTO conserva la visita y su estado G' `
    ($reprogramada.id -eq $idVisita -and $reprogramada.estado -eq 'G') $reprogramada.estado
Check 'la nueva fecha quedo guardada' ($reprogramada.fechaVisita -match $visitaReprogramada) $reprogramada.fechaVisita

$proximas = Api GET '/visitas/proximas?tamano=8' $agente.token
Check 'la agenda proximas incluye la visita viva' `
    (@($proximas.items | Where-Object { $PSItem.id -eq $idVisita }).Count -eq 1) "total=$($proximas.totalRecords)"
# El calendario se pregunta por el mes de la visita, no por un mes fijo.
$fechaRe = [datetime]::ParseExact($visitaReprogramada, 'yyyy-MM-dd', $null)
$mes = Api GET "/visitas/mes?anio=$($fechaRe.Year)&mes=$($fechaRe.Month)" $agente.token
Check 'el calendario del mes la trae' `
    (@($mes.items | Where-Object { $PSItem.id -eq $idVisita }).Count -eq 1) "items=$($mes.items.Count)"

$sinDesenlace = Invoke-ComandoC2DePrueba POST "/visitas/$idVisita/resultado" $agente.token @{
    versionEsperada = $reprogramada.version; resultado = 'INTERESADO'; fechaHecho = $hoy
} "f3-ocurrencia-ausente-$sufijo" -EsperarError
Check 'un resultado no inventa la ocurrencia realizada o no realizada' `
    ($sinDesenlace.codigo -eq 400) 'ocurrencia ausente'

$desenlace = ResultadoVisitaC4DePrueba -IdVisita $idVisita -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $hoy -Desenlace @{
    resultado = 'INTERESADO'; observaciones = 'Le gusto el frente.'
    nivelInteres = 4; objecionPrincipal = 'P'; opinionPrecio = 'A'; proximaAccion = 'O'
}
Check 'el desenlace guarda el resultado' ($desenlace.resultado -eq 'INTERESADO') $desenlace.resultado
Check 'la ocurrencia declarada coloca la visita en R' ($desenlace.estado -eq 'R') $desenlace.estado
Check 'y el nivel de interes' ($desenlace.nivelInteres -eq 4) $desenlace.nivelInteres
Check 'la oportunidad sigue ABIERTA tras un desenlace positivo' `
    ((Api GET "/oportunidades/$idOportunidad" $agente.token).estado -eq 'A') 'estado'
Check 'las 2 transiciones de visita quedaron auditadas (G y R)' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='VISITA' and id_entidad=$idVisita") -eq '2') 'historial'
Check 'y ninguna fila de auditoria quedo sin tenant' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='VISITA' and id_entidad=$idVisita and organizacion_id is null") -eq '0') 'organizacion_id'

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_8'
Write-Host "`n== 8. C4: una comunicacion, vinculos expresos y sin avance comercial implicito ==" -ForegroundColor Cyan
$definicion = Api GET '/interacciones/definicion' $agente.token $null
Check 'Core publica una definicion versionada de comunicacion' `
    ($definicion.version -and $definicion.tipos.Count -eq 3 -and $definicion.canales.Count -eq 8 `
        -and $definicion.eventos.Count -eq 6 -and $definicion.resultados.Count -eq 5) 'diccionario Core'
$datosOportunidad = NuevaInteraccionC4DePrueba `
    -Vinculos @(@{ tipo = 'OPORTUNIDAD'; referencia = $idOportunidad }, @{ tipo = 'CLIENTE'; referencia = $idClienteA }) `
    -Resumen 'Comunicacion TEST de la oportunidad y del cliente, sin resultado conocido.' `
    -Fuente "Fixture TEST F3 $sufijo" -Fecha $hoy
$reciboOportunidad = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token `
    -Datos $datosOportunidad -Clave "f3-oportunidad-$sufijo"
$iOportunidad = $reciboOportunidad.resultado
Check 'el hecho conserva sus dos vinculos sin crear dos comunicaciones' `
    (@($iOportunidad.vinculos | Where-Object { $_.tipo -eq 'OPORTUNIDAD' -and $_.referencia -eq $idOportunidad }).Count -eq 1 `
        -and @($iOportunidad.vinculos | Where-Object { $_.tipo -eq 'CLIENTE' -and $_.referencia -eq $idClienteA }).Count -eq 1) `
    "id=$($iOportunidad.id)"
$datosCliente = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'CLIENTE'; referencia = $idClienteA }) `
    -Resumen 'Comunicacion TEST parcial del cliente.' -Fuente "Fixture TEST F3 $sufijo"
$iCliente = (Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token `
    -Datos $datosCliente -Clave "f3-cliente-$sufijo").resultado
Check 'lo desconocido no se convierte en canal, resultado ni fecha' `
    ($null -eq $iCliente.datos.canal -and $null -eq $iCliente.datos.resultado -and $null -eq $iCliente.fechaHecho) 'parcialidad'
$datosEncargo = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'ENCARGO'; referencia = $idCaptacion }) `
    -Resumen 'Comunicacion TEST del Encargo, sin una decision de negocio.' -Fuente "Fixture TEST F3 $sufijo" -Fecha $hoy
$iCaptacion = (Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token `
    -Datos $datosEncargo -Clave "f3-encargo-$sufijo").resultado
$estadoPrevio = Invoke-ControlLocalE2ESql "select estado from prospeccion where id_prospeccion=$idProspeccion2"
$auditoriaPrevia = [int](Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion2")
$datosProspeccion = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'PROSPECCION'; referencia = $idProspeccion2 }) `
    -Resumen 'Comunicacion TEST previa al Encargo; proxima gestion declarada, no realizada.' `
    -Fuente "Fixture TEST F3 $sufijo" -Fecha $hoy `
    -Proxima @{ origen = 'INTENCION_PROFESIONAL'; fecha = $visitaOtra; detalle = 'Volver a consultar al propietario TEST.' }
$iProspeccion = (Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token `
    -Datos $datosProspeccion -Clave "f3-prospeccion-$sufijo").resultado
Check 'una comunicacion no cambia la etapa de prospeccion' `
    ((Invoke-ControlLocalE2ESql "select estado from prospeccion where id_prospeccion=$idProspeccion2") -eq $estadoPrevio) 'estado conservado'
Check 'no se inventa una transicion comercial en su auditoria' `
    ([int](Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion2") -eq $auditoriaPrevia) 'historia'
Check 'el hecho pertenece al tenant autenticado' `
    ([long](Invoke-ControlLocalE2ESql "select organizacion_id from interaccion_comercial where id_interaccion=$($iOportunidad.id)") -eq $fixtureE2E.organizacion) 'tenant'
$replay = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $datosOportunidad -Clave "f3-oportunidad-$sufijo"
Check 'reintentar la misma captura devuelve identidad y version originales' `
    ($replay.resultado.id -eq $iOportunidad.id -and $replay.resultado.version -eq $iOportunidad.version `
        -and $replay.clave -eq $reciboOportunidad.clave) 'replay original'
$cambiada = $datosOportunidad.Clone(); $cambiada.resumen = 'Otro contenido TEST con la misma clave.'
$conflicto = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $cambiada -Clave "f3-oportunidad-$sufijo" -EsperarError
Check 'misma clave y otro contenido produce conflicto' ($conflicto.codigo -eq 409) "codigo=$($conflicto.codigo)"
$antesInvalidas = [int](Invoke-ControlLocalE2ESql "select count(*) from interaccion_comercial where organizacion_id=$($fixtureE2E.organizacion)")
$incompatible = $datosCliente.Clone(); $incompatible.tipo = 'LLAMADA'; $incompatible.canal = 'CORREO_ELECTRONICO'
$rechazada = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $incompatible -Clave "f3-invalida-$sufijo" -EsperarError
Check 'tipo y canal incompatibles se rechazan antes de guardar' ($rechazada.codigo -eq 400) "codigo=$($rechazada.codigo)"
Check 'la combinacion invalida no escribe una comunicacion parcial' `
    ([int](Invoke-ControlLocalE2ESql "select count(*) from interaccion_comercial where organizacion_id=$($fixtureE2E.organizacion)") -eq $antesInvalidas) 'sin escritura'
$porOportunidad = Api GET "/interacciones?contexto=OPORTUNIDAD&referencia=$idOportunidad&pagina=1&tamano=20" $agente.token $null
$porCliente = Api GET "/interacciones?contexto=CLIENTE&referencia=$idClienteA&pagina=1&tamano=20" $agente.token $null
Check 'las dos vistas autorizadas conservan la misma identidad' `
    (@($porOportunidad.items | Where-Object { $_.id -eq $iOportunidad.id }).Count -eq 1 `
        -and @($porCliente.items | Where-Object { $_.id -eq $iOportunidad.id }).Count -eq 1) 'dos vistas, un hecho'
$porEncargo = Api GET "/interacciones?contexto=ENCARGO&referencia=$idCaptacion&pagina=1&tamano=100" $agente.token $null
Check 'el contexto de Encargo incluye la comunicacion directa y la de su oportunidad' `
    (@($porEncargo.items | Where-Object { $_.id -in @($iCaptacion.id, $iOportunidad.id) }).Count -eq 2) 'origenes autorizados'
Check 'gobernar no permite listar comunicaciones privadas' `
    ((ApiError GET '/interacciones?pagina=1&tamano=10' $admin.token $null).codigo -eq 403) 'gobierno'

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_9'
Write-Host "`n== 9. Alcance por rol: DOS reglas distintas de broker ==" -ForegroundColor Cyan
# Oportunidades/visitas conservan su contexto de Encargo. Interaccion exige
# autor y TODOS sus vinculos/campos autorizados antes de paginar o contar.
$oportunidadesBroker = Api GET '/oportunidades?pagina=1&tamano=50' $broker.token
Check 'el BROKER alcanza la oportunidad por la captacion de su equipo' `
    (@($oportunidadesBroker.items | Where-Object { $PSItem.id -eq $idOportunidad }).Count -eq 1) "total=$($oportunidadesBroker.totalRecords)"

$visitasBroker = Api GET '/visitas?pagina=1&tamano=50' $broker.token
Check 'el BROKER alcanza la visita por la misma regla' `
    (@($visitasBroker.items | Where-Object { $PSItem.id -eq $idVisita }).Count -eq 1) "total=$($visitasBroker.totalRecords)"

$interaccionesBroker = Api GET '/interacciones?pagina=1&tamano=100' $broker.token
Check 'el BROKER alcanza la interaccion con todos sus vinculos autorizados' `
    (@($interaccionesBroker.items | Where-Object { $PSItem.id -eq $iOportunidad.id }).Count -eq 1) "total=$($interaccionesBroker.totalRecords)"

$brokerProgramando = Invoke-ComandoC2DePrueba POST '/visitas' $broker.token @{
    idOportunidad = $idOportunidad; versionContexto = (Api GET "/oportunidades/$idOportunidad" $agente.token $null).version
    fechaVisita = $hoy; horaVisita = '09:00'
} "f3-broker-sin-permiso-$sufijo" -EsperarError
Check 'el BROKER no programa visitas (403)' ($brokerProgramando.codigo -eq 403) "codigo=$($brokerProgramando.codigo)"

$adminVe = ApiError GET '/oportunidades?pagina=1&tamano=50' $admin.token $null
Check 'gobernar no concede la cartera de oportunidades' ($adminVe.codigo -eq 403) 'gobierno'

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_10'
Write-Host "`n== 10. Desenlace de no continuidad: cierra la oportunidad ==" -ForegroundColor Cyan
$oportunidadB = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $idClienteB -Token $agente.token `
    -EvidenciaInteres 'Manifestacion expresa FICTICIA de interes del caso TEST E2E C4.' -Observaciones 'Segundo interesado.'
$visitaB = NuevaVisitaC4DePrueba -IdOportunidad $oportunidadB.id -Fecha $visitaOtra -Hora '11:00' -Token $agente.token
$desenlaceB = ResultadoVisitaC4DePrueba -IdVisita $($visitaB.id) -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $hoy -Desenlace @{
    resultado = 'NO_INTERESADO'; observaciones = 'El precio excede su tope.'
}
Check 'el desenlace de no continuidad se guarda' ($desenlaceB.resultado -eq 'NO_INTERESADO') $desenlaceB.resultado
$sinCierreImplicito = Api GET "/oportunidades/$($oportunidadB.id)" $agente.token
Check 'la visita no sustituye el acto de no continuidad' ($sinCierreImplicito.estado -eq 'A') $sinCierreImplicito.estado
$cerrada = NoContinuidadC4DePrueba -IdOportunidad $oportunidadB.id -Token $agente.token `
    -Razon 'P' -Observaciones 'El interesado TEST no continua por precio.' -FechaHecho $hoy
Check 'el acto explicito de no continuidad cierra en N' ($cerrada.estado -eq 'N') $cerrada.estado
Check 'quedo el motivo tipificado, no solo el estado' `
    ((Invoke-ControlLocalE2ESql "select count(*) from motivo_no_continuidad where id_oportunidad=$($oportunidadB.id)") -eq '1') 'motivo_no_continuidad'

$versionVisita = (Api GET "/visitas/$idVisita" $agente.token $null).version
$opinionesSinVisita = Invoke-ComandoC2DePrueba POST "/visitas/$idVisita/resultado" $agente.token @{
    versionEsperada = $versionVisita; ocurrencia = 'NO_REALIZADA'; fechaHecho = $hoy
    resultado = 'No se realizo la visita TEST'; nivelInteres = 3; motivoRectificacion = 'Caso negativo TEST de opinion sin ocurrencia realizada.'
} "f3-opiniones-sin-visita-$sufijo" -EsperarError
Check 'una visita NO_REALIZADA no admite opiniones ni nivel de interes inventados' `
    ($opinionesSinVisita.codigo -eq 400) "codigo=$($opinionesSinVisita.codigo)"

# Cierre manual por no continuidad sobre la oportunidad A.
$noContinuidad = NoContinuidadC4DePrueba -IdOportunidad $idOportunidad -Token $agente.token `
    -Razon 'U' -Observaciones 'El interesado TEST prefiere otra zona.' -FechaHecho $hoy
Check 'la propuesta revisada de no continuidad cierra en N' ($noContinuidad.estado -eq 'N') $noContinuidad.estado
Check 'la transicion de cierre quedo auditada (A -> N)' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$idOportunidad") -eq '1') 'historial'
Check 'el acto explicito de no continuidad se audito en la oportunidad B' `
    ((Invoke-ControlLocalE2ESql "select count(*) from historial_estado where entidad_tipo='OPORTUNIDAD' and id_entidad=$($oportunidadB.id)") -eq '1') 'historial'

$script:FuenteFixtureE2E = 'e2e-f3-demanda.ps1'
$script:EtapaFixtureE2E = 'F3_FASE_11'
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

# C1 mantiene entidades globales legitimadas. Se comprueba el tenant exacto
# de CADA fila privada creada por este caso, no un NULL global indiscriminado.
$filasPrivadas=Invoke-ControlLocalE2ESql @"
select
 (select count(*) from propiedad where id_propiedad in ($idLocal,$($local2.id)) and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from captacion where id_captacion=$idCaptacion and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from prospeccion where id_prospeccion in ($idProspeccion,$idProspeccion2) and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from persona_rol where id_persona_rol in ($idClienteA,$idClienteB) and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from requerimiento_cliente where id_requerimiento in ($idRequerimiento,$($demandaParaFin.id)) and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from oportunidad_comercial where id_oportunidad in ($idOportunidad,$($oportunidadB.id)) and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from visita where id_visita in ($idVisita,$($visitaB.id)) and organizacion_id=$($fixtureE2E.organizacion))+
 (select count(*) from interaccion_comercial where id_interaccion in ($($iOportunidad.id),$($iCliente.id),$($iCaptacion.id),$($iProspeccion.id)) and organizacion_id=$($fixtureE2E.organizacion));
"@
Check 'las17filas privadas del flujo tienen el tenant exacto del actor' ($filasPrivadas -eq '17') 'aislamiento de recursos creados'
Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
