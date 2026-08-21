# =====================================================================
# E2E de E2 reportes periodicos al propietario contra el API v2.
#
# Verifica el recurso anidado completo (lista, preview y alta), los valores
# derivados desde actividad real, los gates por rol/equipo/tenant y el efecto
# observable sobre la cadencia de 15 dias de /tareas.
#
# Contrato: docs/ai/contrato-congelado-e2-reportes-propietario.md
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite reportes-propietario
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
        # Cuerpo JSON ausente: evita que Windows PowerShell mande el POST como
        # form-urlencoded y permite que @RequestBody(required=false) reciba null.
        $parametros['ContentType'] = 'application/json'
    }
    $parametros
}

function Api($metodo, $ruta, $token, $cuerpo) {
    $parametros = ParametrosApi $metodo $ruta $token $cuerpo
    Invoke-RestMethod @parametros
}

function ApiConEstado($metodo, $ruta, $token, $cuerpo) {
    $parametros = ParametrosApi $metodo $ruta $token $cuerpo
    $parametros['UseBasicParsing'] = $true
    $respuesta = Invoke-WebRequest @parametros
    [pscustomobject]@{
        codigo = [int]$respuesta.StatusCode
        cuerpo = ($respuesta.Content | ConvertFrom-Json)
    }
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

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$desdeFecha = $hoyFecha.AddDays(-1)
$desde = $desdeFecha.ToString('yyyy-MM-dd')
$fuera = $hoyFecha.AddDays(-2).ToString('yyyy-MM-dd')
$fechaCaptacion = $hoyFecha.AddDays(-20).ToString('yyyy-MM-dd')

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
Check 'login del agente propietario de la captacion' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del agente de otro equipo' ($otroAgente.rol -eq 'AGENTE') $otroAgente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del broker ajeno' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login del administrador' ($admin.rol -eq 'ADMIN') $admin.rol

Write-Host "`n== 2. Fixture E2 aislado y actividad real ==" -ForegroundColor Cyan
$idPropietario = [long](Sql @"
select pr.id_persona_rol
from persona_rol pr
where pr.organizacion_id=(
        select id_organizacion from organizacion where codigo='BROX_LEGACY')
  and pr.tipo_rol='PROPIETARIO'
  and pr.vigencia_hasta is null
order by pr.id_persona_rol
limit 1
"@)

$local = NuevoInmuebleConEncargo -Token $agente.token -Direccion "Av. Reporte E2 $sufijo" -Codigo "LOC-E2-$sufijo" `
    -IdPropietario $idPropietario -Metraje 125 -Rubro 'Comercio E2' `
    -Importe 6800 -Moneda 'PEN' -TipoComision 'P' -BaseCalculo 'R' -ValorComision 1.00 `
    -TratamientoIgv 'N' -InicioEncargo $fechaCaptacion `
    -FinEncargo (Get-Date).AddDays(90).ToString('yyyy-MM-dd') `
    -Descripcion "Fixture reportes E2 $sufijo"
$idCaptacion = [long]$local.idEncargo
# El reloj de F7 cuenta desde la FECHA DE CAPTACION, y el alta la pone hoy: es
# la fecha en que el encargo se abrio de verdad. Esta suite necesita un encargo
# de hace veinte dias para que la tarea de reporte ya este vencida, asi que se
# retrasa aqui -en el fixture y a la vista- en vez de pedirle al caso de uso que
# acepte una fecha de captacion inventada.
Sql "update captacion set fecha_captacion = date '$fechaCaptacion' where id_captacion=$idCaptacion" | Out-Null
$captacion = Api GET "/captaciones/$idCaptacion" $agente.token
Check 'se crea un local aislado para E2' ($local.id -gt 0) "local=$($local.id)"
Check 'se crea una captacion propia con reloj vencido' `
    ($idCaptacion -gt 0 -and $captacion.idAgente -eq $agente.idDominio) `
    "captacion=$idCaptacion agente=$($captacion.idAgente)"

$aprobada = Api POST "/captaciones/$idCaptacion/decision" $broker.token @{
    accion = 'A'; observacion = 'Aprobada para verificar E2.'
}
Check 'el broker supervisor activa la captacion E2' ($aprobada.estado -eq 'A') $aprobada.estado

$idOportunidad = [long](Sql @"
with nueva_oportunidad as (
    insert into oportunidad_comercial (
        organizacion_id, codigo_oportunidad, estado, id_rol_cliente,
        id_captacion, id_rol_agente, observaciones,
        fecha_registro, fecha_primera_consulta
    )
    select o.id_organizacion, 'OP-E2-$sufijo', 'A', dc.id_persona_rol,
           $idCaptacion, $($agente.idDominio), 'Fixture de resumen E2 $sufijo',
           timestamptz '$desde 09:00:00-05', timestamptz '$desde 09:00:00-05'
    from organizacion o
    join detalle_cliente dc on dc.organizacion_id=o.id_organizacion
    where o.codigo='BROX_LEGACY'
    order by dc.id_persona_rol
    limit 1
    returning id_oportunidad
)
select id_oportunidad from nueva_oportunidad
"@)

Sql @"
insert into interaccion_comercial (
    organizacion_id, contexto, id_captacion, id_rol_agente,
    canal_contacto, resultado, observaciones, fecha_hora
)
select id_organizacion, 'CAPTACION', $idCaptacion, $($agente.idDominio),
       'E', 'DOCS_SOLICITADOS', 'Consulta directa E2 $sufijo',
       timestamptz '$desde 12:00:00-05'
from organizacion where codigo='BROX_LEGACY';

insert into interaccion_comercial (
    organizacion_id, contexto, id_oportunidad, id_rol_agente,
    canal_contacto, resultado, observaciones, fecha_hora
)
select id_organizacion, 'OPORTUNIDAD', $idOportunidad, $($agente.idDominio),
       'W', 'INTERESADO', 'Consulta de oportunidad E2 $sufijo',
       timestamptz '$hoy 12:00:00-05'
from organizacion where codigo='BROX_LEGACY';

insert into interaccion_comercial (
    organizacion_id, contexto, id_captacion, id_rol_agente,
    canal_contacto, resultado, observaciones, fecha_hora
)
select id_organizacion, 'CAPTACION', $idCaptacion, $($agente.idDominio),
       'L', 'DOCS_SOLICITADOS', 'Fuera del periodo E2 $sufijo',
       timestamptz '$fuera 12:00:00-05'
from organizacion where codigo='BROX_LEGACY';

insert into visita (
    organizacion_id, id_oportunidad, id_rol_agente, fecha_visita,
    hora_visita, estado, resultado, observaciones
)
select id_organizacion, $idOportunidad, $($agente.idDominio), date '$desde',
       time '15:00', 'R', 'INTERESADO', 'Visita realizada E2 $sufijo'
from organizacion where codigo='BROX_LEGACY';

insert into visita (
    organizacion_id, id_oportunidad, id_rol_agente, fecha_visita,
    hora_visita, estado, observaciones
)
select id_organizacion, $idOportunidad, $($agente.idDominio), date '$hoy',
       time '17:00', 'P', 'Visita no realizada todavia E2 $sufijo'
from organizacion where codigo='BROX_LEGACY';

insert into visita (
    organizacion_id, id_oportunidad, id_rol_agente, fecha_visita,
    hora_visita, estado, resultado, observaciones
)
select id_organizacion, $idOportunidad, $($agente.idDominio), date '$fuera',
       time '11:00', 'R', 'INTERESADO', 'Visita fuera del periodo E2 $sufijo'
from organizacion where codigo='BROX_LEGACY';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'P', 'Precio E2 uno',
       $($agente.idDominio), timestamptz '$desde 13:00:00-05'
from organizacion where codigo='BROX_LEGACY';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'P', 'Precio E2 dos',
       $($agente.idDominio), timestamptz '$hoy 13:00:00-05'
from organizacion where codigo='BROX_LEGACY';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'U', 'Ubicacion E2',
       $($agente.idDominio), timestamptz '$hoy 14:00:00-05'
from organizacion where codigo='BROX_LEGACY';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'C', 'Fuera del periodo E2',
       $($agente.idDominio), timestamptz '$fuera 14:00:00-05'
from organizacion where codigo='BROX_LEGACY';
"@ | Out-Null
Check 'se prepara una oportunidad aislada para los agregados' ($idOportunidad -gt 0) "op=$idOportunidad"

Write-Host "`n== 3. Lista vacia, alcance y errores de lectura ==" -ForegroundColor Cyan
$listaInicial = Api GET "/captaciones/$idCaptacion/reportes-propietario" $agente.token
Check 'la captacion nueva empieza sin reportes' ($listaInicial.Count -eq 0) "items=$($listaInicial.Count)"
Check 'el broker supervisor puede listar' `
    ((Api GET "/captaciones/$idCaptacion/reportes-propietario" $broker.token).Count -eq 0) 'broker'
Check 'el administrador puede listar' `
    ((Api GET "/captaciones/$idCaptacion/reportes-propietario" $admin.token).Count -eq 0) 'admin'
Check 'un agente ajeno recibe 403' `
    ((ApiError GET "/captaciones/$idCaptacion/reportes-propietario" $otroAgente.token).codigo -eq 403) `
    'agente ajeno'
Check 'un broker ajeno recibe 403' `
    ((ApiError GET "/captaciones/$idCaptacion/reportes-propietario" $brokerAjeno.token).codigo -eq 403) `
    'broker ajeno'
Check 'una captacion inexistente responde 404' `
    ((ApiError GET '/captaciones/999999999/reportes-propietario' $admin.token).codigo -eq 404) `
    'inexistente'
Check 'sin token responde 401' `
    ((ApiError GET "/captaciones/$idCaptacion/reportes-propietario" $null).codigo -eq 401) `
    'sin token'
Check 'PUT no existe y conserva 405' `
    ((ApiError PUT "/captaciones/$idCaptacion/reportes-propietario" $agente.token @{}).codigo -eq 405) `
    'PUT'
Check 'DELETE no existe y conserva 405' `
    ((ApiError DELETE "/captaciones/$idCaptacion/reportes-propietario" $agente.token $null).codigo -eq 405) `
    'DELETE'

Write-Host "`n== 4. Preview derivado, inclusivo y visible por rol ==" -ForegroundColor Cyan
$rutaPreview = "/captaciones/$idCaptacion/reportes-propietario/preview?desde=$desde&hasta=$hoy"
$preview = Api GET $rutaPreview $agente.token
Check 'preview cuenta interacciones directas y de oportunidades' `
    ($preview.consultas -eq 2) "consultas=$($preview.consultas)"
Check 'preview solo cuenta visitas REALIZADAS del periodo' `
    ($preview.visitas -eq 1) "visitas=$($preview.visitas)"
Check 'preview agrupa y ordena objeciones por frecuencia' `
    ($preview.objeciones -eq 'Precio (2), Ubicacion (1)') $preview.objeciones
Check 'el rango incluye ambos extremos y excluye la actividad anterior' `
    ($preview.consultas -eq 2 -and $preview.visitas -eq 1) `
    "$($preview.consultas)/$($preview.visitas)"
Check 'el broker supervisor obtiene el mismo preview' `
    ((Api GET $rutaPreview $broker.token).objeciones -eq $preview.objeciones) 'broker'
Check 'el administrador obtiene el mismo preview' `
    ((Api GET $rutaPreview $admin.token).consultas -eq 2) 'admin'
$previewAbierto = Api GET `
    "/captaciones/$idCaptacion/reportes-propietario/preview" $agente.token
Check 'sin limites, el preview conserva el rango abierto del cable' `
    ($previewAbierto.consultas -ge 3 `
        -and $previewAbierto.visitas -ge 2 `
        -and $previewAbierto.objeciones -like '*Condiciones del contrato (1)*') `
    "$($previewAbierto.consultas)/$($previewAbierto.visitas)/$($previewAbierto.objeciones)"
$fechaInvalida = ApiError GET `
    "/captaciones/$idCaptacion/reportes-propietario/preview?desde=no-es-fecha" `
    $agente.token
Check 'una fecha de query invalida responde 400 con el valor recibido' `
    ($fechaInvalida.codigo -eq 400 -and $fechaInvalida.error -eq 'Fecha no valida: no-es-fecha') `
    "codigo=$($fechaInvalida.codigo) error=$($fechaInvalida.error)"

Write-Host "`n== 5. Integracion previa con la tarea periodica F7 ==" -ForegroundColor Cyan
Api GET '/tareas' $agente.token | Out-Null
$tareaAntes = Sql @"
select estado
from tarea
where organizacion_id=(
        select id_organizacion from organizacion where codigo='BROX_LEGACY')
  and tipo='REPORTE_PROPIETARIO'
  and entidad_tipo='CAPTACION'
  and entidad_id=$idCaptacion
order by id_tarea desc
limit 1
"@
Check 'sin reporte, F7 materializa la tarea vencida de 15 dias' `
    ($tareaAntes -eq 'P') "estado=$tareaAntes"

Write-Host "`n== 6. Gates y validaciones del alta ==" -ForegroundColor Cyan
$cuerpoValido = @{
    periodoInicio = $desde
    periodoFin = $hoy
    consultasReportadas = 999
    visitasReportadas = 888
    objecionesFrecuentes = 'Texto manual que debe ignorarse'
    ajustesRecomendados = 'Revisar el precio de salida.'
    canalEnvio = $null
}
$sinCuerpo = ApiError POST `
    "/captaciones/$idCaptacion/reportes-propietario" $agente.token $null
Check 'el cuerpo es obligatorio con el mensaje congelado' `
    ($sinCuerpo.codigo -eq 400 `
        -and $sinCuerpo.error -eq 'Los datos del reporte son obligatorios.') `
    "codigo=$($sinCuerpo.codigo) error=$($sinCuerpo.error)"
$periodoInvalido = ApiError POST `
    "/captaciones/$idCaptacion/reportes-propietario" $agente.token @{
        periodoInicio = $hoy; periodoFin = $desde; canalEnvio = 'E'
    }
Check 'el fin anterior al inicio responde 400 con el mensaje congelado' `
    ($periodoInvalido.codigo -eq 400 `
        -and $periodoInvalido.error -eq 'El fin del periodo no puede ser anterior al inicio.') `
    "codigo=$($periodoInvalido.codigo) error=$($periodoInvalido.error)"
$canalInvalido = ApiError POST `
    "/captaciones/$idCaptacion/reportes-propietario" $agente.token @{
        periodoInicio = $desde; periodoFin = $hoy; canalEnvio = 'X'
    }
Check 'un canal desconocido responde 400' `
    ($canalInvalido.codigo -eq 400) `
    "codigo=$($canalInvalido.codigo) error=$($canalInvalido.error)"
Check 'el broker no puede registrar reportes' `
    ((ApiError POST "/captaciones/$idCaptacion/reportes-propietario" `
        $broker.token $cuerpoValido).codigo -eq 403) 'broker'
Check 'el administrador no puede registrar reportes' `
    ((ApiError POST "/captaciones/$idCaptacion/reportes-propietario" `
        $admin.token $cuerpoValido).codigo -eq 403) 'admin'
Check 'un agente ajeno no puede registrar reportes' `
    ((ApiError POST "/captaciones/$idCaptacion/reportes-propietario" `
        $otroAgente.token $cuerpoValido).codigo -eq 403) 'agente ajeno'

Write-Host "`n== 7. Alta autoritativa y persistencia ==" -ForegroundColor Cyan
$alta = ApiConEstado POST `
    "/captaciones/$idCaptacion/reportes-propietario" $agente.token $cuerpoValido
$reporte = $alta.cuerpo
Check 'POST responde exactamente 201' ($alta.codigo -eq 201) "codigo=$($alta.codigo)"
Check 'el 201 identifica captacion y agente por rol operativo' `
    ($reporte.idCaptacion -eq $idCaptacion `
        -and $reporte.idAgente -eq $agente.idDominio) `
    "$($reporte.idCaptacion)/$($reporte.idAgente)"
Check 'el servidor ignora los contadores manipulados del cliente' `
    ($reporte.consultasReportadas -eq 2 -and $reporte.visitasReportadas -eq 1) `
    "$($reporte.consultasReportadas)/$($reporte.visitasReportadas)"
Check 'el servidor ignora las objeciones manuales del cliente' `
    ($reporte.objecionesFrecuentes -eq 'Precio (2), Ubicacion (1)') `
    $reporte.objecionesFrecuentes
Check 'los ajustes recomendados siguen siendo manuales' `
    ($reporte.ajustesRecomendados -eq 'Revisar el precio de salida.') `
    $reporte.ajustesRecomendados
Check 'canal nulo aplica EMAIL por defecto' ($reporte.canalEnvio -eq 'E') $reporte.canalEnvio
Check 'fechaReporte es hoy y fechaCreacion viaja en el 201' `
    ($reporte.fechaReporte -eq $hoy -and $null -ne $reporte.fechaCreacion) `
    "$($reporte.fechaReporte)/$($reporte.fechaCreacion)"
$filaGuardada = Sql @"
select consultas_reportadas || '|' || visitas_reportadas || '|' ||
       objeciones_frecuentes || '|' || canal_envio
from reporte_propietario
where id_reporte_propietario=$($reporte.id)
  and organizacion_id=(
      select id_organizacion from organizacion where codigo='BROX_LEGACY')
"@
Check 'la fila PostgreSQL conserva los derivados y el tenant' `
    ($filaGuardada -eq '2|1|Precio (2), Ubicacion (1)|E') $filaGuardada

Write-Host "`n== 8. Listado, canales y reinicio de F7 ==" -ForegroundColor Cyan
$lista = Api GET "/captaciones/$idCaptacion/reportes-propietario" $agente.token
Check 'el listado incluye el reporte creado' `
    (@($lista | Where-Object { $_.id -eq $reporte.id }).Count -eq 1) `
    "items=$($lista.Count)"
Check 'el listado es una lista pelada, no un sobre paginado' `
    ($null -eq $lista.totalRecords) 'totalRecords'

$canalesAceptados = $true
foreach ($canal in @('L', 'W', 'P', 'R', 'T', 'O')) {
    $creadoCanal = Api POST `
        "/captaciones/$idCaptacion/reportes-propietario" $agente.token @{
            periodoInicio = $desde
            periodoFin = $hoy
            ajustesRecomendados = "Canal $canal E2 $sufijo"
            canalEnvio = $canal
        }
    if ($creadoCanal.canalEnvio -ne $canal) { $canalesAceptados = $false }
}
Check 'los siete codigos de canal del cable son aceptados' $canalesAceptados 'canales'

$listaCompleta = Api GET "/captaciones/$idCaptacion/reportes-propietario" $agente.token
$ordenada = $true
for ($i = 1; $i -lt $listaCompleta.Count; $i++) {
    if ([DateTime]$listaCompleta[$i - 1].fechaReporte `
        -lt [DateTime]$listaCompleta[$i].fechaReporte) {
        $ordenada = $false
    }
}
Check 'el listado conserva fechaReporte descendente' $ordenada 'orden'

Api GET '/tareas' $agente.token | Out-Null
$tareaDespues = Sql @"
select estado
from tarea
where organizacion_id=(
        select id_organizacion from organizacion where codigo='BROX_LEGACY')
  and tipo='REPORTE_PROPIETARIO'
  and entidad_tipo='CAPTACION'
  and entidad_id=$idCaptacion
order by id_tarea desc
limit 1
"@
Check 'registrar el reporte reinicia la cadencia y completa la tarea F7' `
    ($tareaDespues -eq 'C') "estado=$tareaDespues"
$ultimoReporte = Sql @"
select max(fecha_reporte)
from reporte_propietario
where organizacion_id=(
        select id_organizacion from organizacion where codigo='BROX_LEGACY')
  and id_captacion=$idCaptacion
"@
Check 'F7 puede leer el ultimo reporte de la captacion' `
    ($ultimoReporte -eq $hoy) "fecha=$ultimoReporte"

Write-Host "`n== 9. Aislamiento real de tenant y cleanup temporal ==" -ForegroundColor Cyan
$codigoOrg2 = "E2_$sufijo"
$org2Creada = $false
try {
    $idCaptacionOrg2 = [long](Sql @"
with nueva_org as (
    insert into organizacion (codigo, nombre)
    values ('$codigoOrg2', 'Organizacion temporal E2')
    returning id_organizacion
), persona_propietario as (
    insert into persona (
        tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, estado, organizacion_id
    )
    select 'N', 'D', '81$sufijo', 'Propietario temporal E2', 'A', id_organizacion
    from nueva_org
    returning id_persona, organizacion_id
), rol_propietario as (
    insert into persona_rol (
        id_persona, tipo_rol, vigencia_desde, organizacion_id
    )
    select id_persona, 'PROPIETARIO', current_date, organizacion_id
    from persona_propietario
    returning id_persona_rol, organizacion_id
), propiedad_org2 as (
    insert into propiedad (
        codigo, direccion, distrito, metraje, precio_referencial, moneda_referencial,
        estado_registro, disponibilidad_comercial, tipo_inmueble, uso, id_rol_propietario,
        tipo_rol_propietario, organizacion_id
    )
    select 'LOC-E2-ORG2', 'Direccion temporal E2', 'Miraflores',
           100, 5000, 'PEN', 'A', 'D', 'L', 'C', id_persona_rol,
           'PROPIETARIO', organizacion_id
    from rol_propietario
    returning id_propiedad, organizacion_id
), atributo_local as (
    insert into atributo_propiedad (
        organizacion_id, id_propiedad, clave, valor_texto
    )
    select organizacion_id, id_propiedad, 'rubro_permitido', 'Temporal E2' from propiedad_org2
), persona_agente as (
    insert into persona (
        tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, estado, organizacion_id
    )
    select 'N', 'D', '82$sufijo', 'Agente temporal E2', 'A', id_organizacion
    from nueva_org
    returning id_persona, organizacion_id
), rol_agente as (
    insert into persona_rol (
        id_persona, tipo_rol, vigencia_desde, organizacion_id
    )
    select id_persona, 'AGENTE', current_date, organizacion_id
    from persona_agente
    returning id_persona_rol, organizacion_id
), detalle_agente_org2 as (
    insert into detalle_agente (
        id_persona_rol, tipo_rol, codigo_agente, zona_asignada,
        fecha_ingreso, estado_operativo, organizacion_id
    )
    select id_persona_rol, 'AGENTE', 'AGE-E2-ORG2', 'Org2',
           current_date, 'D', organizacion_id
    from rol_agente
    returning id_persona_rol, organizacion_id
), captacion_org2 as (
    insert into captacion (
        codigo_captacion, fecha_captacion,
        fecha_inicio_encargo, fecha_fin_encargo,
        observaciones, estado, id_propiedad, id_rol_agente,
        motivo_operacion, organizacion_id
    )
    select 'CAP-E2-ORG2', current_date,
           current_date, current_date + 180,
           'Captacion temporal E2', 'P', p.id_propiedad, a.id_persona_rol,
           'A', p.organizacion_id
    from propiedad_org2 p, detalle_agente_org2 a
    returning id_captacion, id_rol_agente, organizacion_id
), reporte_org2 as (
    insert into reporte_propietario (
        organizacion_id, id_captacion, id_rol_agente,
        fecha_reporte, consultas_reportadas, visitas_reportadas, canal_envio
    )
    select organizacion_id, id_captacion, id_rol_agente,
           current_date, 0, 0, 'E'
    from captacion_org2
)
select id_captacion from captacion_org2
"@)
    $org2Creada = $true
    Check 'se crea el fixture E2 de una segunda organizacion' `
        ($idCaptacionOrg2 -gt 0) "captacion=$idCaptacionOrg2"
    $ajenaTenant = ApiError GET `
        "/captaciones/$idCaptacionOrg2/reportes-propietario" $admin.token
    Check 'el ADMIN legado recibe 404 para una captacion de otro tenant' `
        ($ajenaTenant.codigo -eq 404) `
        "codigo=$($ajenaTenant.codigo) captacion=$idCaptacionOrg2"
} finally {
    if ($org2Creada) {
        Sql @"
delete from reporte_propietario
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from captacion
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from atributo_propiedad
where id_propiedad in (
    select id_propiedad from propiedad
    where organizacion_id=(
        select id_organizacion from organizacion where codigo='$codigoOrg2'));
delete from propiedad
where organizacion_id=(select id_organizacion from organizacion where codigo='$codigoOrg2');
delete from detalle_agente
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
    ((Sql "select count(*) from organizacion where codigo='$codigoOrg2'") -eq '0') `
    'cleanup'

Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
