# E2E Personas C1: propietario comercial, cuenta global y pertenencia
# aceptada, directorios de gobierno, perfil propio y supervision trazada.
# Datos nuevos ficticios; las identidades no se copian de cuentas semillas.
# Uso: Invoke-E2E.ps1 -Suite personas
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



# Sufijo para que el guion sea re-ejecutable: documento y correo son UNICOS
# por organizacion, asi que un valor fijo fallaria en la segunda corrida.
$sufijo = (Get-Date).ToString('HHmmss')
$idCorrida = (Get-Date).ToString('yyyyMMddHHmmssfff')
$hoy=(Get-Date).ToString('yyyy-MM-dd')
$finEncargo=(Get-Date).AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
Check 'login agente' ($agente.rol -eq 'AGENTE') $agente.rol
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
Check 'login broker' ($broker.rol -eq 'BROKER') $broker.rol
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login admin' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

Write-Host "`n== 2. Alta de propietario (el AGENTE es quien registra) ==" -ForegroundColor Cyan
$brokerRegistra = ApiError POST '/propietarios' $broker.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "80$sufijo"; nombre = 'Prueba Broker'
}
Check 'el BROKER no puede registrar (403)' ($brokerRegistra.codigo -eq 403) "codigo=$($brokerRegistra.codigo)"

# `consentimientoUsoDato` es OBLIGATORIO en el alta desde D-27: sin la
# autorizacion no se persiste ni la persona (divergencia deliberada de la v1,
# que la creaba igual con el booleano en false). Sin este campo, el alta
# responde 400 y la suite entera se cae aqui.
$nuevo = Api POST '/propietarios' $agente.token @{
    tipoPersona           = 'N'
    tipoDocumento         = 'D'
    numeroDocumento       = "80$sufijo"
    nombre                = 'Ana Ruiz Vega'
    telefono              = '999888777'
    correo                = "ana.$sufijo@correo.test"
    consentimientoUsoDato = $true
}
$idNuevo = $nuevo.id
Check 'el alta responde 201 con id' ($idNuevo -gt 0) "id=$idNuevo"
Check 'nace ACTIVO sin mandar estado' ($nuevo.estado -eq 'A') $nuevo.estado
Check 'el contador arranca en 0' ($nuevo.cantidadLocales -eq 0) $nuevo.cantidadLocales
Check 'el id del cable es el persona_rol del rol PROPIETARIO' `
    ((Invoke-ControlLocalE2ESql "select tipo_rol from persona_rol where id_persona_rol=$idNuevo") -eq 'PROPIETARIO') 'tipo_rol'
Check 'y nace con el tenant estampado' `
    ((Invoke-ControlLocalE2ESql "select count(*) from persona_rol where id_persona_rol=$idNuevo and organizacion_id is not null") -eq '1') 'tenant'
Check 'el rol nace VIGENTE' `
    ((Invoke-ControlLocalE2ESql "select count(*) from persona_rol where id_persona_rol=$idNuevo and vigencia_hasta is null") -eq '1') 'vigencia'

Write-Host "`n== 3. Validaciones (mensajes del cable) ==" -ForegroundColor Cyan
# Sin cuerpo no hay Content-Type, y eso es un 415 ANTES de entrar al metodo,
# igual que el @Consumes(JSON) de la v1 (JAX-RS asume octet-stream cuando falta
# la cabecera). El mensaje "Los datos del propietario son obligatorios." queda
# para un POST con Content-Type y cuerpo nulo, que este cliente no sabe emitir;
# lo cubre PropietarioServiceImplTest.sinDatosRespondeElMensajeV1.
$sinCuerpo = ApiError POST '/propietarios' $agente.token $null
Check 'sin Content-Type el recurso responde 415, no 500' ($sinCuerpo.codigo -eq 415) "codigo=$($sinCuerpo.codigo)"
$tipoMal = ApiError POST '/propietarios' $agente.token @{
    tipoPersona = 'X'; tipoDocumento = 'D'; numeroDocumento = '12345678'; nombre = 'X'
}
Check 'tipo de persona invalido' `
    ($tipoMal.error -eq 'Valor invalido para tipo de persona: X') $tipoMal.error
$dniCorto = ApiError POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = '1234567'; nombre = 'X'
}
Check 'el DNI exige 8 digitos' ($dniCorto.error -eq 'El DNI debe tener 8 digitos.') $dniCorto.error
$rucCorto = ApiError POST '/propietarios' $agente.token @{
    tipoPersona = 'J'; tipoDocumento = 'R'; numeroDocumento = '2010012345'; nombre = 'X'
}
Check 'el RUC exige 11 digitos' ($rucCorto.error -eq 'El RUC debe tener 11 digitos.') $rucCorto.error
$sinNombre = ApiError POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "90$sufijo"; nombre = '  '
}
Check 'el nombre es obligatorio' `
    ($sinNombre.error -eq 'El nombre o razon social es obligatorio.') $sinNombre.error

# El documento es UNICO por organizacion: repetirlo es 409, no 400 (misma
# regla que el codigo de solicitud; el guardian es el indice).
# Lleva la autorizacion a proposito: sin ella el alta se caeria antes con un
# 400 de D-27 y esta comprobacion pasaria a medir otra cosa.
$repetido = ApiError POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "80$sufijo"; nombre = 'Duplicada'
    consentimientoUsoDato = $true
}
Check 'un documento repetido responde 409, no 400' ($repetido.codigo -eq 409) "codigo=$($repetido.codigo)"

Write-Host "`n== 4. Directorio comercial autorizado ==" -ForegroundColor Cyan
$ficha=Api GET "/propietarios/$idNuevo" $agente.token
Check 'GET propietario devuelve identidad y creacion conocidas' ($ficha.id -eq $idNuevo -and $ficha.nombre -eq 'Ana Ruiz Vega' -and $null -ne $ficha.fechaCreacion) 'ficha'
$listaAgente=Api GET '/propietarios?pagina=1&tamano=100' $agente.token
Check 'el directorio propio coincide con filas de su organizacion' ($listaAgente.totalRecords -eq [int](Invoke-ControlLocalE2ESql "select count(*) from persona_rol where organizacion_id=$($fixtureE2E.organizacion) and tipo_rol='PROPIETARIO' and vigencia_hasta is null")) 'total'
Check 'el propietario nuevo sale primero' ($listaAgente.items[0].id -eq $idNuevo) 'orden'
Check 'gobernar no concede directorio de propietarios' ((ApiError GET '/propietarios' $admin.token).codigo -eq 403) 'gobierno'
Check 'propietario inexistente devuelve404' ((ApiError GET '/propietarios/99999999' $agente.token).codigo -eq 404) 'inexistente'
Check 'broker no obtiene al propietario sin relacion comercial propia' ((ApiError GET "/propietarios/$idNuevo" $broker.token).codigo -eq 403) 'alcance'

Write-Host "`n== 5. Seguimiento distinct por propiedad, sin datos semilla ==" -ForegroundColor Cyan
Check 'propietario sin recursos cuenta0' ((Api GET "/propietarios/$idNuevo" $agente.token).cantidadLocales -eq 0) 'contador'
$conLocales=(NuevoPropietarioC2DePrueba -Token $agente.token -Nombre 'Titular TEST conteo Personas').id
$localConteo=NuevoInmuebleSinEncargo -Token $agente.token -IdPropietario $conLocales -Direccion 'Jr. Conteo TEST 101' -Metraje 90 -LocalIndependienteTest
$prospConteo=NuevaProspeccion -Token $agente.token -IdPropiedad $localConteo.id -Observaciones 'Hecho TEST para el contador de seguimiento.'
foreach($paso in @('contactar','reunion','propuesta')){Api POST "/prospecciones/$prospConteo/$paso" $agente.token $null|Out-Null}
$captadaConteo=Api POST "/prospecciones/$prospConteo/captar" $agente.token @{
 operacion='ALQUILER';importe=2500;moneda='PEN';comisionPactada=5;tipoComision='P';baseCalculo='R';tratamientoIgv='N'
 fechaInicioVigencia=$hoy;fechaFinVigencia=$finEncargo;exclusividad=$true
}
$contadorAgente=Api GET "/propietarios/$conLocales" $agente.token
Check 'Captacion y Prospeccion del mismo inmueble cuentan una vez' ($contadorAgente.cantidadLocales -eq 1) 'distinct'
Check 'ambas relaciones son reales y pertenecen al fixture' ((Invoke-ControlLocalE2ESql "select (select count(*) from captacion where id_captacion=$($captadaConteo.idCaptacion) and organizacion_id=$($fixtureE2E.organizacion))+(select count(*) from prospeccion where id_prospeccion=$prospConteo and organizacion_id=$($fixtureE2E.organizacion))") -eq '2') 'dos fuentes'

Write-Host "`n== 6. Alcance del broker sobre sus recursos ==" -ForegroundColor Cyan
$listaBroker=Api GET '/propietarios?pagina=1&tamano=100' $broker.token
Check 'el broker alcanza al propietario de su equipo' (@($listaBroker.items|Where-Object{$_.id -eq $conLocales}).Count -eq 1) 'equipo'
Check 'y no al propietario sin relacion comercial' (@($listaBroker.items|Where-Object{$_.id -eq $idNuevo}).Count -eq 0) 'sin relacion'
Check 'contador del equipo tambien es distinct' ((Api GET "/propietarios/$conLocales" $broker.token).cantidadLocales -eq 1) 'contador'
Write-Host "`n== 7. Actualizacion ==" -ForegroundColor Cyan
$brokerEdita = ApiError PUT "/propietarios/$idNuevo" $broker.token @{ nombre = 'X' }
Check 'el BROKER no puede editar (403)' ($brokerEdita.codigo -eq 403) "codigo=$($brokerEdita.codigo)"

$editado = Api PUT "/propietarios/$idNuevo" $agente.token @{
    nombre   = 'Ana Ruiz Vega de Torres'
    telefono = '111222333'
    correo   = "ana.nueva.$sufijo@correo.test"
}
Check 'el PUT reemplaza nombre, telefono y correo' `
    ($editado.nombre -eq 'Ana Ruiz Vega de Torres' -and $editado.telefono -eq '111222333') $editado.nombre
Check 'y NO toca el documento ni el tipo de persona' `
    ($editado.numeroDocumento -eq "80$sufijo" -and $editado.tipoPersona -eq 'N') $editado.numeroDocumento
Check 'el PUT responde el contador en 0 (el cable no lo recalcula)' `
    ($editado.cantidadLocales -eq 0) $editado.cantidadLocales

$putSinNombre = ApiError PUT "/propietarios/$idNuevo" $agente.token @{ telefono = '000' }
Check 'el PUT revalida la persona entera (nombre obligatorio)' `
    ($putSinNombre.error -eq 'El nombre o razon social es obligatorio.') $putSinNombre.error

Write-Host "`n== 8. Baja logica ==" -ForegroundColor Cyan
$brokerBorra = ApiError DELETE "/propietarios/$idNuevo" $broker.token $null
Check 'el BROKER no puede dar de baja (403)' ($brokerBorra.codigo -eq 403) "codigo=$($brokerBorra.codigo)"

Api DELETE "/propietarios/$idNuevo" $agente.token $null | Out-Null
Check 'la baja deja la PERSONA inactiva, no borra fila' `
    ((Invoke-ControlLocalE2ESql "select p.estado from persona p join persona_rol r on r.id_persona=p.id_persona where r.id_persona_rol=$idNuevo") -eq 'I') 'estado'
Check 'y el rol sigue existiendo (baja logica, no fisica)' `
    ((Invoke-ControlLocalE2ESql "select count(*) from persona_rol where id_persona_rol=$idNuevo") -eq '1') 'rol'

$bajaRepetida = ApiError DELETE '/propietarios/999999' $agente.token $null
Check 'dar de baja uno inexistente responde 404' ($bajaRepetida.codigo -eq 404) "codigo=$($bajaRepetida.codigo)"

function IngresarProfesionalDePrueba($Nombre,$Rol,$Codigo,$Supervisor,[bool]$Administrar,$Documento) {
 $uid=[guid]::NewGuid().ToString('N')
 $cuenta=NuevaCuentaVerificadaE2E -Usuario ('e2e-'+$uid.Substring(0,20)) -Correo ('e2e-'+$uid+'@example.invalid') -Contrasena 'ProfesionalTEST2026' -Nombre $Nombre
 $invitacion=Invoke-ComandoC2DePrueba POST '/membresias/invitaciones' $admin.token @{
  correoCuenta=$cuenta.correo;persona=@{nombre=$Nombre;tipoPersona='N';tipoDocumento='P';numeroDocumento=$Documento;telefono='966000111';correoLaboral=('laboral-'+$uid+'@example.invalid')}
  rolPrincipal=$Rol;administrar=$Administrar;idBrokerSupervisor=$Supervisor;codigoInterno=$Codigo;zona='Lima Sur';estadoOperativo='D'
  venceEn=[DateTime]::UtcNow.AddDays(1).ToString('o');motivo='Invitacion explicita del escenario TEST Personas.'
 } ('e2e-invitar-'+$uid)
 $identidad=Api POST '/auth/mfa/desafio' $null @{correo=$cuenta.correo;contrasena='ProfesionalTEST2026'}
 if(-not $identidad.token){throw 'La cuenta nueva no obtuvo sesion personal; no se fabrica contexto.'}
 $aceptada=Invoke-ComandoC2DePrueba POST "/membresias/invitaciones/$($invitacion.referencia)/aceptar" $identidad.token @{} ('e2e-aceptar-'+$uid)
 return [pscustomobject]@{cuenta=$cuenta;pertenencia=$aceptada}
}

Write-Host "`n== 9. Directorio y pertenencia por invitacion aceptada ==" -ForegroundColor Cyan
$listaBrokersAgente=Api GET '/brokers?pagina=1&tamano=100' $agente.token
Check 'el directorio de brokers se limita a la organizacion' ($listaBrokersAgente.totalRecords -eq [int](Invoke-ControlLocalE2ESql "select count(*) from detalle_broker where organizacion_id=$($fixtureE2E.organizacion)")) 'brokers'
$fichaBroker=Api GET "/brokers/$($broker.idDominio)" $agente.token
$cuentaBroker=Api GET '/cuenta' $broker.token
Check 'la referencia de cuenta del broker es la actual' ($fichaBroker.id -eq $broker.idDominio -and $fichaBroker.usuario -eq $cuentaBroker.nombreUsuario) 'cuenta'
foreach($ruta in @('/brokers','/agentes')){
 foreach($tok in @($admin.token,$broker.token,$agente.token)){Check "el alta antigua $ruta esta retirada" ((ApiError POST $ruta $tok @{}).codigo -in @(404,405)) 'ruta sustituida'}
}
$segundoGobierno=IngresarProfesionalDePrueba 'Segundo gobierno TEST' 'BROKER' ('E1G-'+$sufijo) $null $true ('E1G'+$sufijo)
Check 'segunda atribucion de gobierno aceptada sin crear otra identidad local' ($segundoGobierno.pertenencia.administrar -eq $true -and $segundoGobierno.pertenencia.idOrganizacion -eq $fixtureE2E.organizacion) 'gobierno'
$ingresoBroker=IngresarProfesionalDePrueba 'Broker E1 Temporal' 'BROKER' ('E1B-'+$sufijo) $null $false ('E1B'+$sufijo)
$idNuevoBroker=[long]$ingresoBroker.pertenencia.idRolOperativo
$nuevoBroker=Api GET "/brokers/$idNuevoBroker" $admin.token
Check 'invitacion aceptada crea rol y detalle BROKER locales' ($idNuevoBroker -gt 0 -and $nuevoBroker.codigoBroker -eq ('E1B-'+$sufijo) -and $nuevoBroker.estadoAdministrativo -eq 'A') 'broker'
Check 'cuenta global y pertenencia son entidades separadas' ((Invoke-ControlLocalE2ESql "select count(*) from usuario_organizacion where id_usuario=$($ingresoBroker.cuenta.cuenta) and id_persona=$($ingresoBroker.pertenencia.idPersona) and organizacion_id=$($fixtureE2E.organizacion) and estado='A'") -eq '1') 'cuenta/pertenencia'
Check 'broker nuevo no tiene equipo' (@(Api GET "/brokers/$idNuevoBroker/agentes" $admin.token).Count -eq 0) 'equipo'
$brokerEditado=Api PUT "/brokers/$idNuevoBroker" $admin.token @{
 nombre='Broker E1 Actualizado';telefono='955000222';correoLaboral=('broker.editado.'+$sufijo+'@example.invalid');zona='Lima Norte'
 numeroDocumento='00000000';usuario='usuario_ignorado';codigoBroker='IGNORADO';esAdministrador=$true
}
Check 'edicion profesional actualiza nombre/telefono/zona' ($brokerEditado.nombre -eq 'Broker E1 Actualizado' -and $brokerEditado.telefono -eq '955000222' -and $brokerEditado.zona -eq 'Lima Norte') 'edicion'
Check 'no cambia documento, cuenta, codigo ni gobierno' ($brokerEditado.numeroDocumento -eq ('E1B'+$sufijo) -and $brokerEditado.usuario -eq $ingresoBroker.cuenta.usuario -and $brokerEditado.codigoBroker -eq ('E1B-'+$sufijo) -and -not $brokerEditado.esAdministrador) 'identidad'

Write-Host "`n== 10. Agentes: gobierno local y alcance de equipo ==" -ForegroundColor Cyan
Check 'agente no lista el directorio de colegas' ((ApiError GET '/agentes' $agente.token).codigo -eq 403) 'agente'
$sinSupervisor=ApiError POST '/membresias/invitaciones' $admin.token @{
 correoCuenta='supervisor-ausente@example.invalid';persona=@{nombre='Agente sin supervisor TEST';tipoPersona='N';tipoDocumento='P';numeroDocumento=('SIN'+$sufijo)}
 rolPrincipal='AGENTE';administrar=$false;codigoInterno=('E1S-'+$sufijo);venceEn=[DateTime]::UtcNow.AddDays(1).ToString('o');motivo='Caso negativo TEST.'
}
Check 'invitacion AGENTE sin supervisor no se admite' ($sinSupervisor.codigo -in @(400,409)) 'supervisor'
$ingresoAgente=IngresarProfesionalDePrueba 'Agente E1 Temporal' 'AGENTE' ('E1A-'+$sufijo) $broker.idDominio $false ('E1A'+$sufijo)
$idNuevoAgente=[long]$ingresoAgente.pertenencia.idRolOperativo
$nuevoAgente=Api GET "/agentes/$idNuevoAgente/perfil" $admin.token
Check 'gobierno recibe perfil local y omite contadores comerciales' ($nuevoAgente.id -eq $idNuevoAgente -and $null -eq $nuevoAgente.captacionesActivas -and $null -eq $nuevoAgente.operacionesActivas) 'perfil minimo'
Check 'supervision inicial es unica y vigente con el destino elegido' ((Invoke-ControlLocalE2ESql "select count(*) from supervision_agente where organizacion_id=$($fixtureE2E.organizacion) and id_rol_agente=$idNuevoAgente and id_rol_broker=$($broker.idDominio) and fecha_fin is null") -eq '1') 'supervision'
$loginNuevoAgente=Connect-ControlLocalE2E -BaseUrl $base -Correo $ingresoAgente.cuenta.correo -Contrasena 'ProfesionalTEST2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
Check 'el titular accede por correo propio verificado y contexto actual' ($loginNuevoAgente.rol -eq 'AGENTE' -and $loginNuevoAgente.idDominio -eq $idNuevoAgente) 'ingreso'
$brokerAlterno=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
foreach($tok in @($broker.token,$brokerAlterno.token)){Check 'broker no edita fichas de agentes de ningun equipo' ((ApiError PUT "/agentes/$idNuevoAgente" $tok @{nombre='No cambiar'}).codigo -eq 403) 'gobierno exclusivo'}
$agenteEditado=Api PUT "/agentes/$idNuevoAgente" $admin.token @{
 nombre='Agente E1 Actualizado';telefono='966000222';correoLaboral=('agente.e1.editado.'+$idCorrida+'@correo.test');zona='Callao';estadoOperativo='D'
 numeroDocumento='00000000';usuario='ignorado';codigoAgente='IGNORADO'
}
Check 'edicion local conserva identidad y cambia los datos profesionales' ($agenteEditado.nombre -eq 'Agente E1 Actualizado' -and $agenteEditado.telefono -eq '966000222' -and $agenteEditado.zona -eq 'Callao' -and $agenteEditado.numeroDocumento -eq ('E1A'+$sufijo) -and $agenteEditado.usuario -eq $ingresoAgente.cuenta.usuario -and $agenteEditado.codigoAgente -eq ('E1A-'+$sufijo)) 'datos'
Check 'edicion de gobierno tampoco expone contadores comerciales' ($null -eq $agenteEditado.captacionesActivas -and $null -eq $agenteEditado.operacionesActivas) 'minimizacion'
$listaAgentesAdmin=Api GET '/agentes?pagina=1&tamano=100' $admin.token
$listaAgentesBroker=Api GET '/agentes?pagina=1&tamano=100' $broker.token
Check 'directorio de gobierno contiene solo su organizacion' ($listaAgentesAdmin.totalRecords -eq [int](Invoke-ControlLocalE2ESql "select count(*) from detalle_agente where organizacion_id=$($fixtureE2E.organizacion)")) 'total'
Check 'directorio del broker contiene solo supervision vigente' ($listaAgentesBroker.totalRecords -eq [int](Invoke-ControlLocalE2ESql "select count(*) from supervision_agente where organizacion_id=$($fixtureE2E.organizacion) and id_rol_broker=$($broker.idDominio) and fecha_fin is null")) 'equipo'
$fichaContada=Api GET "/agentes/$($agente.idDominio)" $broker.token
Check 'contador de captaciones se contrasta sobre el recurso real del caso' ($fichaContada.agente.captacionesActivas -eq 1) 'captacion TEST'
$equipoRsalas=@(Api GET "/brokers/$($broker.idDominio)/agentes" $admin.token)
Check 'nuevo integrante aparece en el equipo elegido' (@($equipoRsalas|Where-Object{$_.id -eq $idNuevoAgente}).Count -eq 1) 'equipo'
Write-Host "`n== 11. Perfil: telefono y foto ==" -ForegroundColor Cyan
$perfilInicial = Api GET '/perfil' $loginNuevoAgente.token
Check 'GET /perfil devuelve la persona autenticada' `
    ($perfilInicial.nombre -eq 'Agente E1 Actualizado' `
        -and $perfilInicial.correo -eq "agente.e1.editado.$idCorrida@correo.test") `
    "$($perfilInicial.nombre) / $($perfilInicial.correo)"

$telefonoInvalido = ApiError PATCH '/perfil' $loginNuevoAgente.token @{ telefono = '12-3' }
Check 'PATCH conserva el mensaje de telefono invalido' `
    ($telefonoInvalido.error -eq 'Ingresa un telefono valido de entre 6 y 15 digitos.') `
    $telefonoInvalido.error
$perfilEditado = Api PATCH '/perfil' $loginNuevoAgente.token @{ telefono = '  +51 987 654 321  ' }
Check 'PATCH recorta y persiste solo el telefono' `
    ($perfilEditado.telefono -eq '+51 987 654 321' `
        -and $perfilEditado.nombre -eq 'Agente E1 Actualizado') `
    "$($perfilEditado.telefono) / $($perfilEditado.nombre)"

$extensionInvalida = ApiError POST '/perfil/foto' $loginNuevoAgente.token @{
    nombreArchivo = 'perfil.gif'; contenidoBase64 = 'YQ=='
}
Check 'la foto rechaza extensiones fuera de PNG/JPG' `
    ($extensionInvalida.error -eq 'Solo se permiten imagenes PNG o JPG.') $extensionInvalida.error
$base64Invalido = ApiError POST '/perfil/foto' $loginNuevoAgente.token @{
    nombreArchivo = 'perfil.png'; contenidoBase64 = '%%%no-es-base64%%%'
}
Check 'la foto rechaza base64 invalido' `
    ($base64Invalido.error -eq 'El contenido de la imagen (base64) es invalido.') $base64Invalido.error
$contenidoFoto = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('fixture-e1-png'))
$foto = Api POST '/perfil/foto' $loginNuevoAgente.token @{
    nombreArchivo = "perfil-$sufijo.png"; contenidoBase64 = $contenidoFoto
}
Check 'la foto valida devuelve una clave opaca' `
    (-not [string]::IsNullOrWhiteSpace($foto.clave)) 'clave vacia'
Check 'la clave queda persistida en persona.foto_clave' `
    ((Invoke-ControlLocalE2ESql @"
select p.foto_clave
from persona p
join persona_rol r on r.id_persona=p.id_persona
where r.id_persona_rol=$idNuevoAgente
"@) -eq $foto.clave) "api=$($foto.clave)"
$perfilConFoto = Api GET '/perfil' $loginNuevoAgente.token
Check 'GET /perfil refleja telefono y foto actualizados' `
    ($perfilConFoto.telefono -eq '+51 987 654 321' `
        -and $perfilConFoto.fotoClave -eq $foto.clave) `
    "$($perfilConFoto.telefono) / $($perfilConFoto.fotoClave)"

Write-Host "`n== 12. Asignaciones: autorizacion, transaccion e historial V10 ==" -ForegroundColor Cyan
Check 'un BROKER no puede listar asignaciones (403)' `
    ((ApiError GET '/asignaciones/agentes' $broker.token).codigo -eq 403) 'broker'
Check 'un AGENTE no puede listar asignaciones (403)' `
    ((ApiError GET '/asignaciones/brokers' $agente.token).codigo -eq 403) 'agente'

$asignables = @(Api GET '/asignaciones/agentes' $admin.token)
$supervisores = @(Api GET '/asignaciones/brokers' $admin.token)
Check 'ADMIN lista agentes con su broker actual' `
    (@($asignables | Where-Object {
        $_.idAgente -eq $idNuevoAgente -and $_.brokerActual -eq $broker.nombre
    }).Count -eq 1) "agente=$idNuevoAgente"
Check 'ADMIN lista brokers y el nuevo empieza con cero agentes' `
    (@($supervisores | Where-Object {
        $_.idBroker -eq $idNuevoBroker -and $_.agentesACargo -eq 0
    }).Count -eq 1) "broker=$idNuevoBroker"

$reasignacionSinDatos = ApiError POST '/asignaciones/reasignar' $admin.token @{
    motivo = 'Faltan ids'
}
Check 'reasignar exige agente y broker destino' `
    ($reasignacionSinDatos.error -eq 'El agente y el broker destino son obligatorios.') `
    $reasignacionSinDatos.error
# Gobierno y supervision son atribuciones separadas; no se reutiliza el booleano legacy.
$motivoReasignacion = "Rebalanceo E1 $idCorrida"
$reasignado = Api POST '/asignaciones/reasignar' $admin.token @{
    idAgente = $idNuevoAgente
    idBrokerDestino = $idNuevoBroker
    motivo = $motivoReasignacion
}
# V36: `idBrokerAdministrador` viaja VACIO. Administrar dejo de ser una
# variedad de broker, asi que el autor no cabe en una columna que apunta a
# `detalle_broker`; el JSON omite nulos y el resto del sobre no cambia.
Check 'la respuesta identifica agente, origen y destino' `
    ($reasignado.idAgente -eq $idNuevoAgente `
        -and $reasignado.idBrokerAnterior -eq $broker.idDominio `
        -and $reasignado.idBrokerNuevo -eq $idNuevoBroker) `
    "$($reasignado.idAgente) / $($reasignado.idBrokerAnterior) / $($reasignado.idBrokerNuevo)"
Check 'y ya no atribuye la reasignacion a un rol de broker' `
    ($null -eq $reasignado.idBrokerAdministrador) `
    "idBrokerAdministrador=$($reasignado.idBrokerAdministrador)"
Check 'la supervision anterior queda cerrada' `
    ((Invoke-ControlLocalE2ESql @"
select count(*)
from supervision_agente
where id_rol_agente=$idNuevoAgente
  and id_rol_broker=$($broker.idDominio)
  and fecha_fin is not null
"@) -eq '1') 'supervision anterior'
Check 'queda exactamente una supervision activa con el destino' `
    ((Invoke-ControlLocalE2ESql @"
select count(*)
from supervision_agente
where id_rol_agente=$idNuevoAgente
  and id_rol_broker=$idNuevoBroker
  and fecha_fin is null
"@) -eq '1') 'supervision vigente'
Check 'V10 persiste un evento inmutable con tenant, autor y motivo' `
    ((Invoke-ControlLocalE2ESql @"
select count(*)
from reasignacion_agente_broker
where id_reasignacion=$($reasignado.id)
  and id_rol_agente=$idNuevoAgente
  and id_rol_broker_anterior=$($broker.idDominio)
  and id_rol_broker_nuevo=$idNuevoBroker
  and id_rol_broker_administrador is null
  and id_persona_actor=$($fixtureE2E.actores.admin.persona)
  and tipo_rol_actor='TENANT_ADMIN'
  and motivo='$motivoReasignacion'
  and fecha_cambio is not null
  and organizacion_id=(select id_organizacion from organizacion where codigo='$($fixtureE2E.codigo)')
"@) -eq '1') "evento=$($reasignado.id)"

$historial = @(Api GET '/asignaciones/historial' $admin.token)
Check 'el historial devuelve primero el evento recien creado' `
    ($historial.Count -gt 0 -and $historial[0].id -eq $reasignado.id `
        -and $historial[0].motivo -eq $motivoReasignacion) `
    "primero=$($historial[0].id)"
$mismoDestino = ApiError POST '/asignaciones/reasignar' $admin.token @{
    idAgente = $idNuevoAgente
    idBrokerDestino = $idNuevoBroker
    motivo = 'No debe duplicarse'
}
Check 'reasignar al supervisor actual conserva el mensaje congelado' `
    ($mismoDestino.error -eq 'El agente ya esta asignado a ese broker supervisor.') `
    $mismoDestino.error
$equipoDestino = @(Api GET "/brokers/$idNuevoBroker/agentes" $admin.token)
Check 'el equipo del broker destino refleja la reasignacion' `
    (@($equipoDestino | Where-Object { $_.id -eq $idNuevoAgente }).Count -eq 1) `
    "agente=$idNuevoAgente"
$supervisoresDespues = @(Api GET '/asignaciones/brokers' $admin.token)
Check 'el contador del broker destino sube a uno' `
    (@($supervisoresDespues | Where-Object {
        $_.idBroker -eq $idNuevoBroker -and $_.agentesACargo -eq 1
    }).Count -eq 1) "broker=$idNuevoBroker"

Write-Host "`n== 13. Aislamiento con segunda organizacion ficticia ==" -ForegroundColor Cyan
Check 'sin sesion devuelve401' ((ApiError GET '/propietarios' $null).codigo -eq 401) 'sin sesion'
$otra=Initialize-ControlLocalE2EActors -Etiqueta 'personas2'
$otraAgente=Connect-ControlLocalE2E -BaseUrl $base -Correo $otra.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $otra.organizacion -Rol 'AGENTE'
$propietarioAjeno=(NuevoPropietarioC2DePrueba -Token $otraAgente.token -Nombre 'Titular TEST otro tenant').id
Check 'el directorio no mezcla al propietario ajeno' (@((Api GET '/propietarios?pagina=1&tamano=100' $agente.token).items|Where-Object{$_.id -eq $propietarioAjeno}).Count -eq 0) 'tenant'
Check 'la referencia ajena no se resuelve por el actor local' ((ApiError GET "/propietarios/$propietarioAjeno" $agente.token).codigo -in @(403,404)) 'referencia ajena'
Check 'gobierno no resuelve el broker de otra organizacion' ((ApiError GET "/brokers/$($otra.actores.rsalas.rol)" $admin.token).codigo -eq 404) 'directorio local'
Check 'filas nuevas de Persona/Rol conservan tenant exacto' ((Invoke-ControlLocalE2ESql "select count(*) from persona_rol r join persona p on p.id_persona=r.id_persona where r.id_persona_rol in ($idNuevoAgente,$idNuevoBroker) and r.organizacion_id=$($fixtureE2E.organizacion) and p.organizacion_id=r.organizacion_id") -eq '2') 'tenancy'
# La identidad global persiste aunque la organizacion no este operativa: se
# prueba ausencia de contexto, no se cambia la contrasena ni se copia un hash.
Invoke-ControlLocalE2ESql "update organizacion set estado='I' where id_organizacion=$($otra.organizacion)"|Out-Null
try{
 $identidadInactiva=Api POST '/auth/mfa/desafio' $null @{correo=$otra.actores.ltorres.correo;contrasena='Agente2026'}
 $contextosInactivos=@(Api GET '/sesion/contextos' $identidadInactiva.token)
 Check 'organizacion inactiva no ofrece contexto operativo a una cuenta valida' (@($contextosInactivos|Where-Object{$_.idOrganizacion -eq $otra.organizacion}).Count -eq 0) 'contextos'
}finally{Invoke-ControlLocalE2ESql "update organizacion set estado='A' where id_organizacion=$($otra.organizacion)"|Out-Null}
# Se retira el entorno exclusivo completo por el runner: no DELETE manual de
# membresias, credenciales, supervision o historia para fingir limpieza.
Write-Host "`n== Ficha del agente (GET /agentes/{id}) ==" -ForegroundColor Cyan

$catalogoAgentes = Api GET '/agentes?pagina=1&tamano=50' $admin.token
$agentePrueba = $catalogoAgentes.items | Where-Object { $_.id -eq $agente.idDominio }
Check 'el catalogo de agentes trae al menos uno para la ficha' `
    ($null -ne $agentePrueba) 'sin agentes en el seed'

$ficha = Api GET "/agentes/$($agentePrueba.id)" $broker.token
Check 'la ficha responde el mismo agente que el catalogo' `
    ($ficha.agente.id -eq $agentePrueba.id) "ficha=$($ficha.agente.id)"
Check 'la ficha trae la supervision vigente con nombre de broker' `
    ($null -ne $ficha.supervision -and -not [string]::IsNullOrWhiteSpace($ficha.supervision.brokerNombre)) `
    "supervision=$($ficha.supervision | ConvertTo-Json -Compress)"
Check 'la ficha reparte las captaciones por estado, no un total suelto' `
    ($null -ne $ficha.captaciones) 'sin bloque de captaciones'
Check 'la ficha separa las SEIS magnitudes de comision' `
    ($null -ne $ficha.comisiones.generada -and $null -ne $ficha.comisiones.cobrada `
        -and $null -ne $ficha.comisiones.pendienteCobro -and $null -ne $ficha.comisiones.asignadaAgente `
        -and $null -ne $ficha.comisiones.pagadaAgente -and $null -ne $ficha.comisiones.pendientePagoAgente) `
    'faltan magnitudes'

# Coherencia del dinero: pendiente = generada - cobrada, por moneda y nunca
# negativo. Es la invariante que hace util separar las tres magnitudes.
$monedasIncoherentes = @()
foreach ($generada in @($ficha.comisiones.generada)) {
    $cobrada = @($ficha.comisiones.cobrada | Where-Object { $_.moneda -eq $generada.moneda })
    $pendiente = @($ficha.comisiones.pendienteCobro | Where-Object { $_.moneda -eq $generada.moneda })
    $montoCobrado = if ($cobrada.Count) { [decimal]$cobrada[0].monto } else { 0 }
    $montoPendiente = if ($pendiente.Count) { [decimal]$pendiente[0].monto } else { 0 }
    $esperado = [Math]::Max([decimal]$generada.monto - $montoCobrado, 0)
    if ($montoPendiente -ne $esperado) { $monedasIncoherentes += $generada.moneda }
}
Check 'pendiente de cobro = generada - cobrada, por moneda y sin negativos' `
    ($monedasIncoherentes.Count -eq 0) "monedas incoherentes: $($monedasIncoherentes -join ', ')"

Check 'los cierres de la ficha salen de la atribucion historica (V27)' `
    ([int]$ficha.cierres -eq [int](Invoke-ControlLocalE2ESql "select count(*) from contrato_alquiler where organizacion_id=$($fixtureE2E.organizacion) and id_rol_agente_cierre=$($agentePrueba.id)")) `
    "ficha=$($ficha.cierres)"

$fichaInexistente = ApiError GET '/agentes/99999999' $broker.token
Check 'la ficha de un agente inexistente responde 404' `
    ($fichaInexistente.codigo -eq 404) "codigo=$($fichaInexistente.codigo)"

$fichaAgente = ApiError GET "/agentes/$($agentePrueba.id)" $agente.token
Check 'el AGENTE no entra al recurso de agentes (403)' `
    ($fichaAgente.codigo -eq 403) "codigo=$($fichaAgente.codigo)"

Write-Host "`n== Busqueda y cubos del catalogo ==" -ForegroundColor Cyan

# El texto lo resuelve la BASE: se busca por un termino del propio agente y el
# conjunto tiene que reducirse sin dejar de contenerlo.
$termino = $agentePrueba.codigoAgente
$filtrados = Api GET "/agentes?pagina=1&tamano=50&texto=$termino" $admin.token
Check 'el texto filtra en el servidor y encuentra al agente buscado' `
    (@($filtrados.items | Where-Object { $_.id -eq $agentePrueba.id }).Count -eq 1) `
    "total=$($filtrados.totalRecords)"
Check 'el texto ACOTA el conjunto (no devuelve el catalogo entero)' `
    ($filtrados.totalRecords -le $catalogoAgentes.totalRecords) `
    "filtrado=$($filtrados.totalRecords) catalogo=$($catalogoAgentes.totalRecords)"

$sinCoincidencias = Api GET '/agentes?pagina=1&tamano=50&texto=ZZZNOEXISTE' $admin.token
Check 'un texto sin coincidencias devuelve vacio y total 0' `
    ($sinCoincidencias.totalRecords -eq 0 -and @($sinCoincidencias.items).Count -eq 0) `
    "total=$($sinCoincidencias.totalRecords)"

$resumenAgentes = Api GET '/agentes/resumen' $admin.token
Check 'el resumen de agentes cuadra con el total del listado' `
    ($resumenAgentes.total -eq $catalogoAgentes.totalRecords) `
    "resumen=$($resumenAgentes.total) listado=$($catalogoAgentes.totalRecords)"
Check 'los cubos administrativos suman el total' `
    (($resumenAgentes.activos + $resumenAgentes.inactivos) -eq $resumenAgentes.total) `
    "A=$($resumenAgentes.activos) I=$($resumenAgentes.inactivos)"
Check 'los cubos operativos suman el total' `
    (($resumenAgentes.disponibles + $resumenAgentes.ocupados + $resumenAgentes.vacaciones `
        + $resumenAgentes.suspendidos) -eq $resumenAgentes.total) `
    "D=$($resumenAgentes.disponibles) O=$($resumenAgentes.ocupados)"

$activos = Api GET '/agentes?pagina=1&tamano=50&estado=A' $admin.token
Check 'el cubo de activos coincide con el filtro de la lista' `
    ($activos.totalRecords -eq $resumenAgentes.activos) `
    "lista=$($activos.totalRecords) cubo=$($resumenAgentes.activos)"

$resumenFiltrado = Api GET "/agentes/resumen?texto=$termino" $admin.token
Check 'el resumen respeta el mismo texto que la lista' `
    ($resumenFiltrado.total -eq $filtrados.totalRecords) `
    "resumen=$($resumenFiltrado.total) lista=$($filtrados.totalRecords)"

# El BROKER ve MENOS agentes que el ADMIN: solo los que supervisa.
$catalogoBroker = Api GET '/agentes?pagina=1&tamano=50' $broker.token
Check 'el BROKER solo alcanza a los agentes que supervisa' `
    ($catalogoBroker.totalRecords -le $catalogoAgentes.totalRecords) `
    "broker=$($catalogoBroker.totalRecords) admin=$($catalogoAgentes.totalRecords)"

Write-Host "`n== Busqueda y cubos de propietarios ==" -ForegroundColor Cyan

$catalogoProp = Api GET '/propietarios?pagina=1&tamano=50' $agente.token
$propPrueba = $catalogoProp.items | Where-Object { $_.id -eq $conLocales }
$propFiltrados = Api GET "/propietarios?pagina=1&tamano=50&texto=$($propPrueba.numeroDocumento)" $agente.token
Check 'el texto de propietarios filtra en el servidor por documento' `
    (@($propFiltrados.items | Where-Object { $_.id -eq $propPrueba.id }).Count -eq 1) `
    "total=$($propFiltrados.totalRecords)"

$propSinCoincidencias = Api GET '/propietarios?pagina=1&tamano=50&texto=ZZZNOEXISTE' $agente.token
Check 'propietarios: un texto sin coincidencias devuelve vacio' `
    ($propSinCoincidencias.totalRecords -eq 0) "total=$($propSinCoincidencias.totalRecords)"

$resumenProp = Api GET '/propietarios/resumen' $agente.token
Check 'el resumen de propietarios cuadra con el listado' `
    ($resumenProp.total -eq $catalogoProp.totalRecords) `
    "resumen=$($resumenProp.total) listado=$($catalogoProp.totalRecords)"
Check 'los cubos de propietarios suman el total' `
    (($resumenProp.activos + $resumenProp.inactivos) -eq $resumenProp.total) `
    "A=$($resumenProp.activos) I=$($resumenProp.inactivos)"

# El listado SIN filtros tiene que responder exactamente lo de antes: es lo que
# hace que estos parametros sean aditivos y no un cambio de contrato.
$sinFiltros = Api GET '/propietarios?pagina=1&tamano=10' $agente.token
Check 'omitir los filtros conserva el cable congelado (orden id desc)' `
    (@($sinFiltros.items).Count -eq 0 -or $sinFiltros.items[0].id -ge $sinFiltros.items[-1].id) `
    'el orden dejo de ser descendente'

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
