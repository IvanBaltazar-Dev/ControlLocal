# =====================================================================
# E2E del bloque PERSONAS (E1): /propietarios, /agentes, /brokers,
# /asignaciones y /perfil.
#
# Lo que de verdad viene a probar —y que ningun test unitario puede— son:
# * las DOS consultas nativas de PropiedadRepository, que en la v2 sustituyen
#   al escaneo en memoria de la v1:
#   * contarLocalesEnSeguimiento -> el campo cantidadLocales del cable
#   * idsPropietarioDelBroker    -> el alcance del BROKER sobre el catalogo
# * la paginacion y los contadores SQL de brokers/agentes;
# * el Party-Role completo y la supervision inicial al registrar;
# * la reasignacion transaccional y su evento historico de V10;
# * el aislamiento real entre dos organizaciones a traves de /brokers.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite personas
# Ojo: el login admite 10 intentos por minuto; espera entre corridas.
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

# Sufijo para que el guion sea re-ejecutable: documento y correo son UNICOS
# por organizacion, asi que un valor fijo fallaria en la segunda corrida.
$sufijo = (Get-Date).ToString('HHmmss')
$idCorrida = (Get-Date).ToString('yyyyMMddHHmmssfff')

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'login agente' ($agente.rol -eq 'AGENTE') $agente.rol
$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
Check 'login broker' ($broker.rol -eq 'BROKER') $broker.rol
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login admin' ($admin.rol -eq 'ADMIN') $admin.rol

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
    ((Sql "select tipo_rol from persona_rol where id_persona_rol=$idNuevo") -eq 'PROPIETARIO') 'tipo_rol'
Check 'y nace con el tenant estampado' `
    ((Sql "select count(*) from persona_rol where id_persona_rol=$idNuevo and organizacion_id is not null") -eq '1') 'tenant'
Check 'el rol nace VIGENTE' `
    ((Sql "select count(*) from persona_rol where id_persona_rol=$idNuevo and vigencia_hasta is null") -eq '1') 'vigencia'

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

Write-Host "`n== 4. Lectura y catalogo compartido ==" -ForegroundColor Cyan
$ficha = Api GET "/propietarios/$idNuevo" $agente.token
Check 'GET {id} resuelve la ficha' ($ficha.id -eq $idNuevo) $ficha.id
Check 'con los datos de la persona' ($ficha.nombre -eq 'Ana Ruiz Vega') $ficha.nombre
Check 'y su fecha de creacion' ($null -ne $ficha.fechaCreacion) 'fechaCreacion'

$listaAgente = Api GET '/propietarios?pagina=1&tamano=100' $agente.token
$listaAdmin  = Api GET '/propietarios?pagina=1&tamano=100' $admin.token
Check 'el catalogo es COMPARTIDO: agente y admin ven el mismo universo' `
    ($listaAgente.totalRecords -eq $listaAdmin.totalRecords) "$($listaAgente.totalRecords) vs $($listaAdmin.totalRecords)"
Check 'y el total cuadra con la BD' `
    ($listaAgente.totalRecords -eq [int](Sql "select count(*) from persona_rol where tipo_rol='PROPIETARIO' and vigencia_hasta is null")) `
    "api=$($listaAgente.totalRecords)"
Check 'el recien creado sale primero (orden: ultimo creado)' `
    ($listaAgente.items[0].id -eq $idNuevo) $listaAgente.items[0].id

$inexistente = ApiError GET '/propietarios/999999' $agente.token
Check 'un propietario inexistente responde 404' ($inexistente.codigo -eq 404) "codigo=$($inexistente.codigo)"

Write-Host "`n== 5. contarLocalesEnSeguimiento (SQL nativo, UNION de 2 ramas) ==" -ForegroundColor Cyan
# El propietario recien creado no tiene locales: la consulta debe devolverlo
# sin fila y el service caer a 0. Es la rama "sin resultados" del nativo.
Check 'un propietario sin locales cuenta 0' `
    ((Api GET "/propietarios/$idNuevo" $agente.token).cantidadLocales -eq 0) 'contador'

# Un propietario del seed CON locales: el contador tiene que cuadrar con el
# UNION hecho a mano en SQL. Si el nativo esta mal, aqui se cae.
$conLocales = Sql @"
select p.id_rol_propietario
from propiedad p
where exists (select 1 from captacion c where c.id_propiedad = p.id_propiedad)
   or exists (select 1 from prospeccion pr where pr.id_propiedad = p.id_propiedad)
group by p.id_rol_propietario
order by count(distinct p.id_propiedad) desc
limit 1
"@
Check 'el seed tiene algun propietario con locales en seguimiento' `
    ($conLocales -match '^\d+$') "id=$conLocales"

if ($conLocales -match '^\d+$') {
    $esperadoAdmin = [int](Sql @"
select count(*) from (
    select p.id_propiedad from propiedad p join captacion c on c.id_propiedad = p.id_propiedad
    where p.id_rol_propietario = $conLocales
    union
    select p.id_propiedad from propiedad p join prospeccion pr on pr.id_propiedad = p.id_propiedad
    where p.id_rol_propietario = $conLocales
) t
"@)
    $fichaAdmin = Api GET "/propietarios/$conLocales" $admin.token
    Check 'ADMIN: cantidadLocales cuadra con el UNION sin filtro de rol' `
        ($fichaAdmin.cantidadLocales -eq $esperadoAdmin) "api=$($fichaAdmin.cantidadLocales) sql=$esperadoAdmin"
    Check 'y no duplica el local que tiene captacion Y prospeccion (count distinct)' `
        ($fichaAdmin.cantidadLocales -le [int](Sql "select count(*) from propiedad where id_rol_propietario=$conLocales")) `
        "contador=$($fichaAdmin.cantidadLocales)"

    # El mismo propietario visto por el AGENTE: el contador se acota a SUS
    # captaciones y prospecciones, asi que puede ser menor. Nunca mayor.
    $fichaAgente = Api GET "/propietarios/$conLocales" $agente.token
    Check 'AGENTE: el contador es el suyo y nunca supera al del ADMIN' `
        ($fichaAgente.cantidadLocales -le $fichaAdmin.cantidadLocales) `
        "agente=$($fichaAgente.cantidadLocales) admin=$($fichaAdmin.cantidadLocales)"

    $esperadoAgente = [int](Sql @"
select count(*) from (
    select p.id_propiedad from propiedad p join captacion c on c.id_propiedad = p.id_propiedad
    where p.id_rol_propietario = $conLocales and c.id_rol_agente = $($agente.idDominio)
    union
    select p.id_propiedad from propiedad p join prospeccion pr on pr.id_propiedad = p.id_propiedad
    where p.id_rol_propietario = $conLocales and pr.id_rol_agente = $($agente.idDominio)
) t
"@)
    Check 'AGENTE: y cuadra con el UNION filtrado por su rol' `
        ($fichaAgente.cantidadLocales -eq $esperadoAgente) "api=$($fichaAgente.cantidadLocales) sql=$esperadoAgente"
}

Write-Host "`n== 6. idsPropietarioDelBroker (SQL nativo, alcance del BROKER) ==" -ForegroundColor Cyan
$listaBroker = Api GET '/propietarios?pagina=1&tamano=100' $broker.token
Check 'el BROKER SI queda acotado (ve menos que el catalogo)' `
    ($listaBroker.totalRecords -le $listaAdmin.totalRecords) `
    "broker=$($listaBroker.totalRecords) admin=$($listaAdmin.totalRecords)"

$esperadoBroker = [int](Sql @"
select count(*) from (
    select distinct p.id_rol_propietario
    from propiedad p join captacion c on c.id_propiedad = p.id_propiedad
    where c.id_rol_agente in (select id_rol_agente from supervision_agente
                              where id_rol_broker = $($broker.idDominio) and fecha_fin is null)
       or c.id_rol_broker_revisor = $($broker.idDominio)
    union
    select distinct p.id_rol_propietario
    from propiedad p join prospeccion pr on pr.id_propiedad = p.id_propiedad
    where pr.id_rol_agente in (select id_rol_agente from supervision_agente
                               where id_rol_broker = $($broker.idDominio) and fecha_fin is null)
) t
"@)
Check 'y su total cuadra con el UNION de equipo + captaciones que revisa' `
    ($listaBroker.totalRecords -eq $esperadoBroker) "api=$($listaBroker.totalRecords) sql=$esperadoBroker"

# El propietario recien creado no cuelga de ninguna propiedad, asi que el
# broker NO debe alcanzarlo: 403, que es lo que distingue "existe pero no es
# tuyo" de "no existe" (404).
$fueraDeAlcance = ApiError GET "/propietarios/$idNuevo" $broker.token
Check 'un propietario fuera de su alcance responde 403, no 404' `
    ($fueraDeAlcance.codigo -eq 403) "codigo=$($fueraDeAlcance.codigo)"

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
    ((Sql "select p.estado from persona p join persona_rol r on r.id_persona=p.id_persona where r.id_persona_rol=$idNuevo") -eq 'I') 'estado'
Check 'y el rol sigue existiendo (baja logica, no fisica)' `
    ((Sql "select count(*) from persona_rol where id_persona_rol=$idNuevo") -eq '1') 'rol'

$bajaRepetida = ApiError DELETE '/propietarios/999999' $agente.token $null
Check 'dar de baja uno inexistente responde 404' ($bajaRepetida.codigo -eq 404) "codigo=$($bajaRepetida.codigo)"

Write-Host "`n== 9. Brokers: lectura, alta Party-Role y actualizacion ==" -ForegroundColor Cyan
$listaBrokersAgente = Api GET '/brokers?pagina=1&tamano=100' $agente.token
$totalBrokersBd = [int](Sql @"
select count(*)
from detalle_broker
where organizacion_id = (select id_organizacion from organizacion where codigo='BROX_LEGACY')
"@)
Check 'cualquier usuario autenticado puede listar brokers' `
    ($listaBrokersAgente.totalRecords -eq $totalBrokersBd) `
    "api=$($listaBrokersAgente.totalRecords) sql=$totalBrokersBd"

$fichaBroker = Api GET "/brokers/$($broker.idDominio)" $agente.token
Check 'GET broker expone el usuario y el contador de equipo' `
    ($fichaBroker.id -eq $broker.idDominio -and $fichaBroker.usuario -eq 'rsalas' `
        -and $fichaBroker.agentesACargo -ge 0) `
    "id=$($fichaBroker.id) usuario=$($fichaBroker.usuario)"

$altaBrokerProhibida = ApiError POST '/brokers' $broker.token @{
    nombre = 'Broker prohibido'; usuario = "prohibido_$sufijo"; contrasena = 'Broker2026'
}
Check 'solo ADMIN puede registrar brokers (403)' `
    ($altaBrokerProhibida.codigo -eq 403) "codigo=$($altaBrokerProhibida.codigo)"

# §2.5 del Plan S0: una organizacion puede tener los administradores que
# necesite — con uno solo, un olvido de contrasena es una caida de gobierno
# (H-04). La regla anterior ("Solo debe existir un broker administrador") se
# retiro. Lo unico que sigue siendo unico es el BOOLEANO heredado que lee
# GlassFish, asi que el segundo administrador gobierna por su membresia y no
# carga la marca.
$segundoAdmin = Api POST '/brokers' $admin.token @{
    nombre = 'Otro administrador'; usuario = "admin2_$sufijo"; contrasena = 'Broker2026'
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "62$sufijo"
    esAdministrador = $true
}
Check 'ya se admite un segundo administrador (D-31)' `
    ($segundoAdmin.id -gt 0) "id=$($segundoAdmin.id)"
Check 'y NO carga la marca heredada, que sigue siendo unica' `
    (-not $segundoAdmin.esAdministrador) "esAdministrador=$($segundoAdmin.esAdministrador)"
Check 'gobierna por su membresia, no por el booleano' `
    ([int](Sql "select count(*) from usuario_organizacion where rol='TENANT_ADMIN' and estado='A'") -ge 2) `
    'membresias TENANT_ADMIN'

$nuevoBroker = Api POST '/brokers' $admin.token @{
    nombre = 'Broker E1 Temporal'
    tipoPersona = 'N'
    tipoDocumento = 'D'
    numeroDocumento = "63$sufijo"
    telefono = '955000111'
    correo = "broker.e1.$idCorrida@correo.test"
    usuario = "brk_e1_$idCorrida"
    contrasena = 'BrokerE12026'
    zona = 'Lima Centro'
    codigoBroker = "BRK-E1-$sufijo"
    estado = 'I'
    esAdministrador = $false
}
$idNuevoBroker = $nuevoBroker.id
Check 'ADMIN registra broker y obtiene el id del rol BROKER' `
    ($idNuevoBroker -gt 0 -and $nuevoBroker.codigoBroker -eq "BRK-E1-$sufijo") `
    "id=$idNuevoBroker codigo=$($nuevoBroker.codigoBroker)"
Check 'el POST ignora estado y nace ACTIVO' `
    ($nuevoBroker.estadoAdministrativo -eq 'A') $nuevoBroker.estadoAdministrativo
Check 'el alta crea PERSONA + USUARIO_INTERNO + BROKER en el mismo tenant' `
    ((Sql @"
select count(*)
from persona_rol operativo
join persona_rol usuario on usuario.id_persona=operativo.id_persona
where operativo.id_persona_rol=$idNuevoBroker
  and operativo.tipo_rol='BROKER'
  and usuario.tipo_rol='USUARIO_INTERNO'
  and operativo.organizacion_id=usuario.organizacion_id
"@) -eq '1') 'Party-Role'
Check 'la credencial y el detalle comparten la organizacion del rol' `
    ((Sql @"
select count(*)
from detalle_broker db
join persona_rol rb on rb.id_persona_rol=db.id_persona_rol
join persona_rol ru on ru.id_persona=rb.id_persona and ru.tipo_rol='USUARIO_INTERNO'
join credencial_usuario cu on cu.id_persona_rol=ru.id_persona_rol
where db.id_persona_rol=$idNuevoBroker
  and db.organizacion_id=rb.organizacion_id
  and cu.organizacion_id=rb.organizacion_id
"@) -eq '1') 'tenant de credencial/detalle'

$equipoNuevoBroker = @(Api GET "/brokers/$idNuevoBroker/agentes" $admin.token)
Check 'un broker nuevo empieza sin agentes' `
    (@($equipoNuevoBroker | Where-Object { $null -ne $_.id }).Count -eq 0) `
    "items=$(@($equipoNuevoBroker | Where-Object { $null -ne $_.id }).Count)"

$brokerEditado = Api PUT "/brokers/$idNuevoBroker" $admin.token @{
    nombre = 'Broker E1 Actualizado'
    telefono = '955000222'
    correo = "broker.e1.editado.$idCorrida@correo.test"
    zona = 'Lima Norte'
    estado = 'A'
    numeroDocumento = '00000000'
    usuario = 'usuario_ignorado'
    codigoBroker = 'BRK-IGNORADO'
    esAdministrador = $true
}
Check 'PUT broker reemplaza solo los campos editables' `
    ($brokerEditado.nombre -eq 'Broker E1 Actualizado' `
        -and $brokerEditado.telefono -eq '955000222' `
        -and $brokerEditado.zona -eq 'Lima Norte') `
    "$($brokerEditado.nombre) / $($brokerEditado.telefono) / $($brokerEditado.zona)"
Check 'PUT broker ignora documento, usuario, codigo y bandera ADMIN' `
    ($brokerEditado.numeroDocumento -eq "63$sufijo" `
        -and $brokerEditado.usuario -eq "brk_e1_$idCorrida" `
        -and $brokerEditado.codigoBroker -eq "BRK-E1-$sufijo" `
        -and -not $brokerEditado.esAdministrador) `
    "$($brokerEditado.numeroDocumento) / $($brokerEditado.usuario) / $($brokerEditado.codigoBroker)"

Write-Host "`n== 10. Agentes: alcance, alta, contadores y actualizacion ==" -ForegroundColor Cyan
$agenteNoLista = ApiError GET '/agentes' $agente.token
Check 'un AGENTE no puede consumir /agentes (403)' `
    ($agenteNoLista.codigo -eq 403) "codigo=$($agenteNoLista.codigo)"

$listaAgentesAdmin = Api GET '/agentes?pagina=1&tamano=100' $admin.token
$listaAgentesBroker = Api GET '/agentes?pagina=1&tamano=100' $broker.token
$totalAgentesBd = [int](Sql @"
select count(*)
from detalle_agente
where organizacion_id = (select id_organizacion from organizacion where codigo='BROX_LEGACY')
"@)
Check 'ADMIN ve el catalogo de agentes de su organizacion' `
    ($listaAgentesAdmin.totalRecords -eq $totalAgentesBd) `
    "api=$($listaAgentesAdmin.totalRecords) sql=$totalAgentesBd"
Check 'BROKER ve solo su equipo activo' `
    ($listaAgentesBroker.totalRecords -eq [int](Sql @"
select count(*)
from supervision_agente
where organizacion_id=(select id_organizacion from organizacion where codigo='BROX_LEGACY')
  and id_rol_broker=$($broker.idDominio)
  and fecha_fin is null
"@)) "api=$($listaAgentesBroker.totalRecords)"

# D-S0-17 fila 17, invertida: el alta de agentes paso a ser GOBIERNO del
# tenant (D-S0-18, "un broker no crea cuentas"). Antes la hacia el broker y el
# administrador estaba expresamente excluido; ahora es al reves, y el broker
# supervisor viaja en la peticion porque quien gobierna no supervisa a nadie de
# quien deducirlo.
$altaAgentePorBroker = ApiError POST '/agentes' $broker.token @{
    nombre = 'Agente desde broker'; usuario = "age_broker_$sufijo"; contrasena = 'Agente2026'
}
Check 'el BROKER ya no registra agentes (fila 17)' `
    ($altaAgentePorBroker.codigo -eq 403) "codigo=$($altaAgentePorBroker.codigo)"

$adminSinSupervisor = ApiError POST '/agentes' $admin.token @{
    nombre = 'Agente sin supervisor'; usuario = "age_admin_$sufijo"; contrasena = 'Agente2026'
}
Check 'el alta exige el broker supervisor explicito' `
    ($adminSinSupervisor.error -eq 'Debe indicar el broker que supervisara al agente.') `
    $adminSinSupervisor.error

$nuevoAgente = Api POST '/agentes' $admin.token @{
    idBrokerSupervisor = $broker.idDominio
    nombre = 'Agente E1 Temporal'
    tipoPersona = 'N'
    tipoDocumento = 'D'
    numeroDocumento = "64$sufijo"
    telefono = '966000111'
    correo = "agente.e1.$idCorrida@correo.test"
    usuario = "age_e1_$idCorrida"
    contrasena = 'AgenteE12026'
    zona = 'Lima Sur'
    codigoAgente = "AGE-E1-$sufijo"
    estado = 'A'
    estadoOperativo = 'D'
}
$idNuevoAgente = $nuevoAgente.id
Check 'el TENANT_ADMIN registra un agente con contadores iniciales en cero' `
    ($idNuevoAgente -gt 0 -and $nuevoAgente.captacionesActivas -eq 0 `
        -and $nuevoAgente.operacionesActivas -eq 0) `
    "id=$idNuevoAgente captaciones=$($nuevoAgente.captacionesActivas) operaciones=$($nuevoAgente.operacionesActivas)"
Check 'el alta crea PERSONA + USUARIO_INTERNO + AGENTE' `
    ((Sql @"
select count(*)
from persona_rol operativo
join persona_rol usuario on usuario.id_persona=operativo.id_persona
where operativo.id_persona_rol=$idNuevoAgente
  and operativo.tipo_rol='AGENTE'
  and usuario.tipo_rol='USUARIO_INTERNO'
  and operativo.organizacion_id=usuario.organizacion_id
"@) -eq '1') 'Party-Role'
Check 'y una supervision vigente con el motivo inicial congelado' `
    ((Sql @"
select count(*)
from supervision_agente
where id_rol_agente=$idNuevoAgente
  and id_rol_broker=$($broker.idDominio)
  and fecha_fin is null
  and motivo='Asignacion inicial por registro de agente.'
"@) -eq '1') 'supervision'

$loginNuevoAgente = Api POST '/auth/login' $null @{
    usuario = "age_e1_$idCorrida"; contrasena = 'AgenteE12026'
}
Check 'la credencial creada autentica con rol AGENTE' `
    ($loginNuevoAgente.rol -eq 'AGENTE' -and $loginNuevoAgente.idDominio -eq $idNuevoAgente) `
    "$($loginNuevoAgente.rol) / $($loginNuevoAgente.idDominio)"

$brokerAlterno = Api POST '/auth/login' $null @{ usuario = 'psoto'; contrasena = 'Broker2026' }
Check 'login del broker alterno para probar alcance de escritura' `
    ($brokerAlterno.rol -eq 'BROKER') $brokerAlterno.rol
$fueraDeEquipo = ApiError PUT "/agentes/$idNuevoAgente" $brokerAlterno.token @{
    nombre = 'No debe cambiar'
}
# D-S0-17 fila 18: editar la ficha de un agente paso a ser gobierno del tenant.
# La comprobacion de supervision que protegia al broker de tocar equipos ajenos
# se sustituyo por una mas fuerte —el broker no edita NINGUN agente, ni el
# suyo—, asi que este caso ya no es una regla de negocio sino un 403.
Check 'un broker no modifica agentes, ni de otro equipo ni del suyo (fila 18)' `
    ($fueraDeEquipo.codigo -eq 403) "codigo=$($fueraDeEquipo.codigo)"

$agenteEditado = Api PUT "/agentes/$idNuevoAgente" $admin.token @{
    nombre = 'Agente E1 Actualizado'
    telefono = '966000222'
    correo = "agente.e1.editado.$idCorrida@correo.test"
    zona = 'Callao'
    estado = 'A'
    estadoOperativo = 'D'
    numeroDocumento = '00000000'
    usuario = 'usuario_ignorado'
    codigoAgente = 'AGE-IGNORADO'
}
Check 'PUT agente reemplaza los campos editables' `
    ($agenteEditado.nombre -eq 'Agente E1 Actualizado' `
        -and $agenteEditado.telefono -eq '966000222' `
        -and $agenteEditado.zona -eq 'Callao') `
    "$($agenteEditado.nombre) / $($agenteEditado.telefono) / $($agenteEditado.zona)"
Check 'PUT agente ignora documento, usuario y codigo' `
    ($agenteEditado.numeroDocumento -eq "64$sufijo" `
        -and $agenteEditado.usuario -eq "age_e1_$idCorrida" `
        -and $agenteEditado.codigoAgente -eq "AGE-E1-$sufijo") `
    "$($agenteEditado.numeroDocumento) / $($agenteEditado.usuario) / $($agenteEditado.codigoAgente)"
Check 'el PUT conserva la rareza del cable: contadores en cero' `
    ($agenteEditado.captacionesActivas -eq 0 -and $agenteEditado.operacionesActivas -eq 0) `
    "$($agenteEditado.captacionesActivas) / $($agenteEditado.operacionesActivas)"

$listaAgentesAdmin = Api GET '/agentes?pagina=1&tamano=100' $admin.token
$filaVmora = @($listaAgentesAdmin.items | Where-Object { $_.usuario -eq 'vmora' })[0]
Check 'el seed contiene a vmora para contrastar contadores' `
    ($null -ne $filaVmora) 'vmora no aparece'
if ($null -ne $filaVmora) {
    $capsVmora = [int](Sql "select count(*) from captacion where id_rol_agente=$($filaVmora.id) and estado in ('P','O','A')")
    $opsVmora = [int](Sql "select count(*) from oportunidad_comercial where id_rol_agente=$($filaVmora.id) and estado in ('A','S')")
    Check 'captacionesActivas cuadra con PENDIENTE/OBSERVADA/ACTIVA' `
        ($filaVmora.captacionesActivas -eq $capsVmora) `
        "api=$($filaVmora.captacionesActivas) sql=$capsVmora"
    Check 'operacionesActivas cuadra con ABIERTA/SOLICITUD_CREADA' `
        ($filaVmora.operacionesActivas -eq $opsVmora) `
        "api=$($filaVmora.operacionesActivas) sql=$opsVmora"
}
$equipoRsalas = @(Api GET "/brokers/$($broker.idDominio)/agentes" $admin.token)
Check '/brokers/{id}/agentes incluye el alta del equipo' `
    (@($equipoRsalas | Where-Object { $_.id -eq $idNuevoAgente }).Count -eq 1) `
    "agente=$idNuevoAgente"

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
    ((Sql @"
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
# El destino se pide por el id del ROL DE BROKER. Desde el Bloque 5 el
# `idDominio` del administrador es su rol de GOBIERNO, no uno de broker, asi
# que hay que tomar el del `detalle_broker` que conserva (D-S0-10) para seguir
# ejercitando esta regla en vez de chocar antes con "Broker no encontrado".
$rolBrokerDelAdmin = [int](Sql @"
select db.id_persona_rol
from detalle_broker db
where db.es_administrador
  and db.organizacion_id=(select id_organizacion from organizacion where codigo='BROX_LEGACY')
"@)
$destinoAdmin = ApiError POST '/asignaciones/reasignar' $admin.token @{
    idAgente = $idNuevoAgente
    idBrokerDestino = $rolBrokerDelAdmin
    motivo = 'Destino invalido'
}
Check 'el administrador no se usa como supervisor destino' `
    ($destinoAdmin.error -eq `
        'El broker administrador no requiere asignacion de agentes para supervisar.') `
    $destinoAdmin.error

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
    ((Sql @"
select count(*)
from supervision_agente
where id_rol_agente=$idNuevoAgente
  and id_rol_broker=$($broker.idDominio)
  and fecha_fin is not null
"@) -eq '1') 'supervision anterior'
Check 'queda exactamente una supervision activa con el destino' `
    ((Sql @"
select count(*)
from supervision_agente
where id_rol_agente=$idNuevoAgente
  and id_rol_broker=$idNuevoBroker
  and fecha_fin is null
"@) -eq '1') 'supervision vigente'
Check 'V10 persiste un evento inmutable con tenant, autor y motivo' `
    ((Sql @"
select count(*)
from reasignacion_agente_broker
where id_reasignacion=$($reasignado.id)
  and id_rol_agente=$idNuevoAgente
  and id_rol_broker_anterior=$($broker.idDominio)
  and id_rol_broker_nuevo=$idNuevoBroker
  and id_rol_broker_administrador is null
  and id_persona_actor=$($admin.idUsuario)
  and tipo_rol_actor='TENANT_ADMIN'
  and motivo='$motivoReasignacion'
  and fecha_cambio is not null
  and organizacion_id=(select id_organizacion from organizacion where codigo='BROX_LEGACY')
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

Write-Host "`n== 13. Tenancy: invariantes y aislamiento por API ==" -ForegroundColor Cyan
Check 'sin token responde 401' ((ApiError GET '/propietarios' $null).codigo -eq 401) 'sin token'
Check 'cero personas con organizacion_id NULL' `
    ((Sql "select count(*) from persona where organizacion_id is null") -eq '0') 'tenant persona'
Check 'cero roles con organizacion_id NULL' `
    ((Sql "select count(*) from persona_rol where organizacion_id is null") -eq '0') 'tenant rol'

# Fixture minimo de otra corredora. El hash se copia del admin seed para
# demostrar que, aun con una clave valida, el login queda cerrado al tenant
# de legado mientras D-20 mantenga la convivencia con GlassFish.
$codigoOrg2 = "E1_$idCorrida"
$usuarioOrg2 = "e1org_$idCorrida"
$org2Creada = $false
try {
    $idOrg2 = [long](Sql @"
with nueva_org as (
    insert into organizacion (codigo, nombre)
    values ('$codigoOrg2', 'Organizacion temporal E1')
    returning id_organizacion
), nueva_persona as (
    insert into persona (
        tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, telefono, correo, estado, organizacion_id
    )
    select 'N', 'D', '79$sufijo', 'Admin temporal E1',
           '977000111', 'admin.org2.$idCorrida@correo.test', 'A', id_organizacion
    from nueva_org
    returning id_persona, organizacion_id
), rol_usuario as (
    insert into persona_rol (id_persona, tipo_rol, vigencia_desde, organizacion_id)
    select id_persona, 'USUARIO_INTERNO', current_date, organizacion_id
    from nueva_persona
    returning id_persona_rol, id_persona, organizacion_id
), credencial as (
    insert into credencial_usuario (
        id_persona_rol, tipo_rol, nombre_usuario, contrasena_hash,
        estado_administrativo, organizacion_id
    )
    select id_persona_rol, 'USUARIO_INTERNO', '$usuarioOrg2',
           (select contrasena_hash from credencial_usuario
            where nombre_usuario='admin@controllocal.test' limit 1),
           'A', organizacion_id
    from rol_usuario
), rol_broker as (
    insert into persona_rol (id_persona, tipo_rol, vigencia_desde, organizacion_id)
    select id_persona, 'BROKER', current_date, organizacion_id
    from nueva_persona
    returning id_persona_rol, id_persona, organizacion_id
), detalle as (
    insert into detalle_broker (
        id_persona_rol, tipo_rol, codigo_broker, zona,
        fecha_designacion, es_administrador, organizacion_id
    )
    select id_persona_rol, 'BROKER', 'BRK-001', 'Org2',
           current_date, true, organizacion_id
    from rol_broker
), membresia as (
    insert into usuario_organizacion (
        organizacion_id, id_usuario, rol, nombre_visible, estado, id_persona
    )
    -- 'ADMIN' era la banda de V6 y V33 la retiro del vocabulario
    -- (ck_usuario_org_rol). Ademas satisface el invariante de V34: una
    -- organizacion con cuentas activas necesita gobierno.
    select organizacion_id, id_persona_rol, 'TENANT_ADMIN',
           'Admin temporal E1', 'A', id_persona
    from rol_usuario
)
select id_organizacion from nueva_org
"@)
    $org2Creada = $true
    Check 'se crea el fixture de una segunda organizacion' ($idOrg2 -gt 0) "org=$idOrg2"

    $idBrokerOrg2 = [long](Sql @"
select id_persona_rol
from detalle_broker
where organizacion_id=$idOrg2
"@)
    $loginOrg2 = ApiError POST '/auth/login' $null @{
        usuario = $usuarioOrg2; contrasena = 'Admin2026'
    }
    Check 'el login rechaza credenciales de un tenant no habilitado' `
        ($loginOrg2.codigo -eq 401 -and $loginOrg2.error -eq 'Credenciales invalidas.') `
        "codigo=$($loginOrg2.codigo) error=$($loginOrg2.error)"
    $brokersLegadoConOrg2 = Api GET '/brokers?pagina=1&tamano=100' $admin.token
    $totalBrokersLegado = [int](Sql @"
select count(*)
from detalle_broker
where organizacion_id=(select id_organizacion from organizacion where codigo='BROX_LEGACY')
"@)
    Check '/brokers no mezcla la segunda organizacion en el tenant legado' `
        ($brokersLegadoConOrg2.totalRecords -eq $totalBrokersLegado `
            -and @($brokersLegadoConOrg2.items | Where-Object {
                $_.numeroDocumento -eq "79$sufijo"
            }).Count -eq 0) `
        "api=$($brokersLegadoConOrg2.totalRecords) sql=$totalBrokersLegado"
    Check 'el ADMIN legado no puede resolver el broker de la otra organizacion' `
        ((ApiError GET "/brokers/$idBrokerOrg2" $admin.token).codigo -eq 404) `
        "brokerOrg2=$idBrokerOrg2"
} finally {
    if ($org2Creada) {
        Sql @"
delete from usuario_organizacion
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from detalle_broker
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from credencial_usuario
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from persona_rol
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from persona
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from organizacion where codigo='$codigoOrg2';
"@ | Out-Null
    }
}
Check 'el fixture de segunda organizacion se retira al terminar' `
    ((Sql "select count(*) from organizacion where codigo='$codigoOrg2'") -eq '0') 'cleanup'

# =====================================================================
# Extensiones aditivas de Personas (2026-08-03): ficha del agente,
# busqueda y cubos. Ninguna existe en la v1, asi que aqui no se compara
# contra el cable congelado: se comprueba que sean COHERENTES entre si y
# que respeten el alcance.
# =====================================================================

Write-Host "`n== Ficha del agente (GET /agentes/{id}) ==" -ForegroundColor Cyan

$catalogoAgentes = Api GET '/agentes?pagina=1&tamano=50' $admin.token
$agentePrueba = $catalogoAgentes.items | Where-Object { $_.codigoAgente } | Select-Object -First 1
Check 'el catalogo de agentes trae al menos uno para la ficha' `
    ($null -ne $agentePrueba) 'sin agentes en el seed'

$ficha = Api GET "/agentes/$($agentePrueba.id)" $admin.token
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
    ([int]$ficha.cierres -eq [int](Sql "select count(*) from contrato_alquiler where id_rol_agente_cierre=$($agentePrueba.id)")) `
    "ficha=$($ficha.cierres)"

$fichaInexistente = ApiError GET '/agentes/99999999' $admin.token
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
$propPrueba = $catalogoProp.items | Select-Object -First 1
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
