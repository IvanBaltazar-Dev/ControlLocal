# =====================================================================
# E2E de E2 reportes periodicos al propietario contra el API v2.
#
# Verifica el recurso anidado completo (lista, preview y alta), los valores
# derivados desde actividad real, los gates por rol/equipo/tenant y el efecto
# observable sobre la cadencia de 15 dias de /tareas.
#
# Autoridad: docs/1INF50/BROX-diseno-software.md (RF078/RF079) y
# docs/1INF50/BROX-KAIROS-catalogo-operativo.md (CAT-E31).
# C4: work/c4-c5-20261030/BROX-ESPECIFICACION-IMPLEMENTACION-C4-C5-2026-10-30.md, secciones1/4/10.
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite reportes-propietario
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
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
        $texto = LeerErrorHttpDeFixture $PSItem
        $mensaje = 'Respuesta HTTP sin mensaje JSON legible.'
        try { $jsonError = $texto | ConvertFrom-Json; if($jsonError.error -is [string]){$mensaje=$jsonError.error} } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
}

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$hoyFecha = (Get-Date).Date
$hoy = $hoyFecha.ToString('yyyy-MM-dd')
$desdeFecha = $hoyFecha.AddDays(-1)
$desde = $desdeFecha.ToString('yyyy-MM-dd')
$fuera = $hoyFecha.AddDays(-2).ToString('yyyy-MM-dd')
$fechaCaptacion = $hoyFecha.AddDays(-20).ToString('yyyy-MM-dd')

Write-Host "`n== 1. Login y actores ==" -ForegroundColor Cyan
$agente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$otroAgente = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.ltorres.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$broker = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$brokerAjeno = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.psoto.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
# V37: la sesion del TENANT_ADMIN nace CAPADA hasta enrolar su segundo factor.
# El helper vive en e2e-context.ps1 y lo comparten todas las suites que actuan
# como administrador.
$admin = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
Check 'login del agente propietario de la captacion' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'login del agente de otro equipo' ($otroAgente.rol -eq 'AGENTE') $otroAgente.rol
Check 'login del broker supervisor' ($broker.rol -eq 'BROKER') $broker.rol
Check 'login del broker ajeno' ($brokerAjeno.rol -eq 'BROKER') $brokerAjeno.rol
Check 'login del administrador' ($admin.rol -eq 'TENANT_ADMIN') $admin.rol

Write-Host "`n== 2. Fixture E2 aislado y actividad real ==" -ForegroundColor Cyan
$propietarioE2E = NuevoPropietarioC2DePrueba -Token $agente.token -Nombre "Propietario TEST E2 $sufijo"
$idPropietario = [long]$propietarioE2E.id

$local = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token -Direccion "Av. Reporte E2 $sufijo" -Codigo "LOC-E2-$sufijo" `
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
Invoke-ControlLocalE2ESql "update captacion set fecha_captacion = date '$fechaCaptacion' where id_captacion=$idCaptacion" | Out-Null
$captacion = Api GET "/captaciones/$idCaptacion" $agente.token
Check 'se crea un local aislado para E2' ($local.id -gt 0) "local=$($local.id)"
Check 'se crea una captacion propia con reloj vencido' `
    ($idCaptacion -gt 0 -and $captacion.idAgente -eq $agente.idDominio) `
    "captacion=$idCaptacion agente=$($captacion.idAgente)"

$null = HabilitarEncargoC2DePrueba -IdEncargo $idCaptacion -TokenResponsable $agente.token -TokenDecisor $broker.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato'

$aprobada = Api GET "/captaciones/$idCaptacion" $agente.token $null
Check 'el broker supervisor activa la captacion E2' ($aprobada.estado -eq 'A') $aprobada.estado

$clienteDeReporte = Api POST '/clientes' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'P'; numeroDocumento = ('E2-' + [guid]::NewGuid().ToString('N').Substring(0,20))
    nombre = "Cliente TEST reporte E2 $sufijo"; consentimientoUsoDato = $true; consentimientoContacto = $true
}
$oportunidadDeReporte = NuevaOportunidadC4DePrueba -IdEncargo $idCaptacion -IdCliente $clienteDeReporte.id `
    -Token $agente.token -Codigo "OP-E2-$sufijo" -Observaciones "Fixture de resumen TEST E2 $sufijo" `
    -EvidenciaInteres 'Manifestacion FICTICIA explicita de interes del cliente TEST de reporte.'
$idOportunidad = [long]$oportunidadDeReporte.id

# Tres comunicaciones declaradas de TEST, con la fecha real del caso y
# resultado desconocido. No se convierten DOCS_SOLICITADOS/INTERESADO.
$directa = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'ENCARGO'; referencia = $idCaptacion }) `
    -Resumen "Consulta directa E2 $sufijo" -Fuente "Fixture TEST E2 $sufijo" -Fecha $desde
$porOportunidad = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'OPORTUNIDAD'; referencia = $idOportunidad }) `
    -Resumen "Consulta de oportunidad E2 $sufijo" -Fuente "Fixture TEST E2 $sufijo" -Fecha $hoy
$anterior = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'ENCARGO'; referencia = $idCaptacion }) `
    -Resumen "Fuera del periodo E2 $sufijo" -Fuente "Fixture TEST E2 $sufijo" -Fecha $fuera
$comunicacionDirecta = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $directa -Clave "e2-directa-$sufijo"
$comunicacionOportunidad = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $porOportunidad -Clave "e2-oportunidad-$sufijo"
$comunicacionAnterior = Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $agente.token -Datos $anterior -Clave "e2-anterior-$sufijo"
Check 'tres comunicaciones tienen tres identidades y sus fechas declaradas' `
    (@($comunicacionDirecta.resultado.id, $comunicacionOportunidad.resultado.id, $comunicacionAnterior.resultado.id | Select-Object -Unique).Count -eq 3 `
        -and $comunicacionDirecta.resultado.fechaHecho -eq $desde -and $comunicacionAnterior.resultado.fechaHecho -eq $fuera) `
    'identidades y fechas'

$visitaDesde = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $desde -Hora '15:00' -Token $agente.token -Observaciones "Visita realizada TEST E2 $sufijo"
$null = ResultadoVisitaC4DePrueba -IdVisita $visitaDesde.id -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $desde -Desenlace @{ resultado = 'INTERESADO' }
$visitaPendiente = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $hoy -Hora '17:00' -Token $agente.token -Observaciones "Visita no realizada todavia TEST E2 $sufijo"
$visitaAnterior = NuevaVisitaC4DePrueba -IdOportunidad $idOportunidad -Fecha $fuera -Hora '11:00' -Token $agente.token -Observaciones "Visita fuera del periodo TEST E2 $sufijo"
$null = ResultadoVisitaC4DePrueba -IdVisita $visitaAnterior.id -Token $agente.token -Ocurrencia 'REALIZADA' -FechaHecho $fuera -Desenlace @{ resultado = 'INTERESADO' }

Invoke-ControlLocalE2ESql @"
insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'P', 'Precio E2 uno',
       $($agente.idDominio), timestamptz '$desde 13:00:00-05'
from organizacion where codigo='$($fixtureE2E.codigo)';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'P', 'Precio E2 dos',
       $($agente.idDominio), timestamptz '$hoy 13:00:00-05'
from organizacion where codigo='$($fixtureE2E.codigo)';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'U', 'Ubicacion E2',
       $($agente.idDominio), timestamptz '$hoy 14:00:00-05'
from organizacion where codigo='$($fixtureE2E.codigo)';

insert into motivo_no_continuidad (
    organizacion_id, id_oportunidad, razon_principal, observaciones,
    id_rol_agente, fecha_registro
)
select id_organizacion, $idOportunidad, 'C', 'Fuera del periodo E2',
       $($agente.idDominio), timestamptz '$fuera 14:00:00-05'
from organizacion where codigo='$($fixtureE2E.codigo)';
"@ | Out-Null
Check 'se prepara una oportunidad aislada para los agregados' ($idOportunidad -gt 0) "op=$idOportunidad"

Write-Host "`n== 3. Lista vacia, alcance y errores de lectura ==" -ForegroundColor Cyan
$listaInicial = Api GET "/captaciones/$idCaptacion/reportes-propietario" $agente.token
Check 'la captacion nueva empieza sin reportes' ($listaInicial.Count -eq 0) "items=$($listaInicial.Count)"
Check 'el broker supervisor puede listar' `
    ((Api GET "/captaciones/$idCaptacion/reportes-propietario" $broker.token).Count -eq 0) 'broker'
Check 'gobernar no permite listar el reporte comercial' `
    ((ApiError GET "/captaciones/$idCaptacion/reportes-propietario" $admin.token).codigo -eq 403) 'gobierno'
Check 'un agente ajeno recibe 403' `
    ((ApiError GET "/captaciones/$idCaptacion/reportes-propietario" $otroAgente.token).codigo -eq 403) `
    'agente ajeno'
Check 'un broker ajeno recibe 403' `
    ((ApiError GET "/captaciones/$idCaptacion/reportes-propietario" $brokerAjeno.token).codigo -eq 403) `
    'broker ajeno'
Check 'una captacion inexistente responde 404' `
    ((ApiError GET '/captaciones/999999999/reportes-propietario' $agente.token).codigo -eq 404) `
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
Check 'gobernar no permite el preview comercial' `
    ((ApiError GET $rutaPreview $admin.token).codigo -eq 403) 'gobierno'
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
$tareaAntes = Invoke-ControlLocalE2ESql @"
select estado
from tarea
where organizacion_id=(
        select id_organizacion from organizacion where codigo='$($fixtureE2E.codigo)')
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
$filaGuardada = Invoke-ControlLocalE2ESql @"
select consultas_reportadas || '|' || visitas_reportadas || '|' ||
       objeciones_frecuentes || '|' || canal_envio
from reporte_propietario
where id_reporte_propietario=$($reporte.id)
  and organizacion_id=(
      select id_organizacion from organizacion where codigo='$($fixtureE2E.codigo)')
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
$tareaDespues = Invoke-ControlLocalE2ESql @"
select estado
from tarea
where organizacion_id=(
        select id_organizacion from organizacion where codigo='$($fixtureE2E.codigo)')
  and tipo='REPORTE_PROPIETARIO'
  and entidad_tipo='CAPTACION'
  and entidad_id=$idCaptacion
order by id_tarea desc
limit 1
"@
Check 'registrar el reporte reinicia la cadencia y completa la tarea F7' `
    ($tareaDespues -eq 'C') "estado=$tareaDespues"
$ultimoReporte = Invoke-ControlLocalE2ESql @"
select max(fecha_reporte)
from reporte_propietario
where organizacion_id=(
        select id_organizacion from organizacion where codigo='$($fixtureE2E.codigo)')
  and id_captacion=$idCaptacion
"@
Check 'F7 puede leer el ultimo reporte de la captacion' `
    ($ultimoReporte -eq $hoy) "fecha=$ultimoReporte"

Write-Host "`n== 9. Aislamiento real de tenant y cleanup temporal ==" -ForegroundColor Cyan
$otroTenant = Initialize-ControlLocalE2EActors -Etiqueta 'contrario'
$gobiernoOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $otroTenant.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
$agenteOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $otroTenant.organizacion -Rol 'AGENTE'
$brokerOtroTenant = Connect-ControlLocalE2E -BaseUrl $base -Correo $otroTenant.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $otroTenant.organizacion -Rol 'BROKER' -EnrolarSiempre
$propietarioOtroTenant = NuevoPropietarioC2DePrueba -Token $agenteOtroTenant.token -Nombre 'Propietario ficticio del otro tenant E2'
$localOtroTenant = NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agenteOtroTenant.token -IdPropietario $propietarioOtroTenant.id `
    -Direccion 'Predio ficticio E2-ORG2' -Distrito 'Ate' -Metraje 100 -Rubro 'Retail TEST' `
    -Importe 1000 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' `
    -InicioEncargo $hoy -FinEncargo (Get-Date).AddDays(90).ToString('yyyy-MM-dd') -Exclusividad $true
$habilitadoOtro = HabilitarEncargoC2DePrueba -IdEncargo $localOtroTenant.idEncargo -TokenResponsable $agenteOtroTenant.token -TokenDecisor $brokerOtroTenant.token -FechaFirma $hoy `
    -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' `
    -ParteObligada 'Propietario TEST extranjero' -Beneficiario 'Agencia TEST extranjera' -Exigibilidad 'A la firma del contrato'
$reporteOtro = Api POST "/captaciones/$($localOtroTenant.idEncargo)/reportes-propietario" $agenteOtroTenant.token @{
    periodoInicio = $hoy; periodoFin = $hoy; canalEnvio = 'E'; ajustesRecomendados = 'Reporte ficticio TEST de otro tenant.'
}
Check 'el propio agente extranjero consulta su reporte persistido' `
    (@(Api GET "/captaciones/$($localOtroTenant.idEncargo)/reportes-propietario" $agenteOtroTenant.token $null).Count -eq 1) 'positivo extranjero'
$ajenaTenant = ApiError GET "/captaciones/$($localOtroTenant.idEncargo)/reportes-propietario" $agente.token $null
Check 'poseer el id del Encargo extranjero no divulga su reporte' ($ajenaTenant.codigo -eq 404) 'tenant'
# No borrar el reporte/historia para limpiar: el runner retira la base propia.
$contextoFinal = Assert-ControlLocalE2EContext
Check 'el teardown conserva la frontera de la base exclusiva' ($contextoFinal.Database -eq $e2e.Database) 'teardown'
Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
