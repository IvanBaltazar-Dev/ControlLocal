# =====================================================================
# E2E del patron definitivo de listados sobre GET /locales.
#
# Crea 1.005 locales identificables en una organizacion nueva de TEST y 7 homonimos
# en una segunda organizacion. Verifica paginacion, filtros, resumen, orden
# estable, aislamiento y pagina vacia contra API + PostgreSQL reales.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite locales-listado
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
$ok = 0; $fail = 0
$prefijo = "LPR$(Get-Random -Minimum 10000 -Maximum 99999)"
$codigoOrganizacion = "PRUEBA_$prefijo"
$idOrganizacionOtra = $null

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



try {
    Write-Host "`n== 1. Fixture de mas de 1.000 filas y segundo tenant ==" -ForegroundColor Cyan
    $operador = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
    $propietarioPrincipal = NuevoPropietarioC2DePrueba -Token $operador.token -Nombre 'Propietario TEST de listado1005'
    $idPropietario = $propietarioPrincipal.id
    $fixtureOtro = Initialize-ControlLocalE2EActors -Etiqueta 'contrario'
    $operadorOtro = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureOtro.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureOtro.organizacion -Rol 'AGENTE'
    $propietarioOtro = NuevoPropietarioC2DePrueba -Token $operadorOtro.token -Nombre 'Propietario TEST de listado extranjero7'
    $idOrganizacionOtra = $fixtureOtro.organizacion
    $idPropietarioOtro = $propietarioOtro.id
    # El contrato D/N/I ya no es una columna: V15-V17 partieron propiedad.estado
    # en estado_registro (A/I) + disponibilidad_comercial (D/R/A/T). El fixture
    # inserta el PAR que PropiedadRepository.ESTADO_LEGADO vuelve a leer como
    # D/N/I. Ojo: 'N' no es un valor legal de disponibilidad_comercial
    # (ck_propiedad_disponibilidad solo admite D/R/A/T) - un local activo pero
    # no disponible se guarda por su causa: aqui ALQUILADO ('A').
    # Reparto: 800 D + 150 N + 55 I = 1.005.
    Invoke-ControlLocalE2ESql @"
insert into propiedad
    (codigo,direccion,distrito,metraje,precio_referencial,moneda_referencial,descripcion,
     estado_registro,disponibilidad_comercial,tipo_inmueble,uso,id_rol_propietario,organizacion_id)
select '$prefijo-' || lpad(g::text,4,'0'),
       'Avenida rendimiento $prefijo ' || g,
       'Lima', 80 + g, 4000 + g, 'PEN', 'Fixture paginado',
       case when g <= 950 then 'A' else 'I' end,
       case when g <= 800 then 'D' when g <= 950 then 'A' else 'T' end,
       'L','C',$idPropietario,$($fixtureE2E.organizacion)
from generate_series(1,1005) g
"@ | Out-Null
    Invoke-ControlLocalE2ESql @"
insert into atributo_propiedad (organizacion_id,id_propiedad,clave,valor_texto)
select $($fixtureE2E.organizacion),id_propiedad,'rubro_permitido','Prueba rendimiento'
from propiedad where organizacion_id=$($fixtureE2E.organizacion) and codigo like '$prefijo-%';
insert into atributo_propiedad (organizacion_id,id_propiedad,clave,valor_booleano)
select $($fixtureE2E.organizacion),id_propiedad,'apto_licencia_funcionamiento',true
from propiedad where organizacion_id=$($fixtureE2E.organizacion) and codigo like '$prefijo-%'
"@ | Out-Null

    Invoke-ControlLocalE2ESql @"
insert into propiedad
    (codigo,direccion,distrito,metraje,precio_referencial,moneda_referencial,descripcion,
     estado_registro,disponibilidad_comercial,tipo_inmueble,uso,id_rol_propietario,organizacion_id)
select '$prefijo-' || lpad(g::text,4,'0'),
       'Avenida rendimiento $prefijo ' || g,
       'Lima', 80 + g, 4000 + g, 'PEN', 'Segundo tenant', 'A', 'D',
       'L','C',$idPropietarioOtro,$idOrganizacionOtra
from generate_series(1,7) g
"@ | Out-Null
    Invoke-ControlLocalE2ESql @"
insert into atributo_propiedad (organizacion_id,id_propiedad,clave,valor_texto)
select $idOrganizacionOtra,id_propiedad,'rubro_permitido','Prueba otro tenant'
from propiedad where organizacion_id=$idOrganizacionOtra and codigo like '$prefijo-%';
insert into atributo_propiedad (organizacion_id,id_propiedad,clave,valor_booleano)
select $idOrganizacionOtra,id_propiedad,'apto_licencia_funcionamiento',true
from propiedad where organizacion_id=$idOrganizacionOtra and codigo like '$prefijo-%'
"@ | Out-Null

    # Relaciones nuevas y explicitas de TEST: el tenant solo no da lectura C1.
    # Son preparaciones P sin decision ni pacto, no Encargos habilitados.
    Invoke-ControlLocalE2ESql @"
insert into captacion(codigo_captacion,fecha_captacion,estado,motivo_operacion,id_propiedad,id_rol_agente,organizacion_id)
select 'CTX-LIST-'||id_propiedad,current_date,'P','A',id_propiedad,
 case when organizacion_id=$($fixtureE2E.organizacion) then $($operador.idDominio) else $($operadorOtro.idDominio) end,organizacion_id
from propiedad where organizacion_id in ($($fixtureE2E.organizacion),$idOrganizacionOtra) and codigo like '$prefijo-%';
"@ | Out-Null
    Check 'fixture del tenant principal tiene 1.005 locales' ((Invoke-ControlLocalE2ESql "select count(*) from propiedad where organizacion_id=$($fixtureE2E.organizacion) and codigo like '$prefijo-%'") -eq '1005') 'conteo BD'
    Check 'fixture homonimo del segundo tenant tiene 7 locales' ((Invoke-ControlLocalE2ESql "select count(*) from propiedad where organizacion_id=$idOrganizacionOtra and codigo like '$prefijo-%'") -eq '7') 'conteo BD'

    Write-Host "`n== 2. Pagina, filtro y orden estables ==" -ForegroundColor Cyan
    $pagina1 = Api GET "/locales?page=1&tamano=20&texto=$prefijo" $operador.token $null
    Check 'el conteo excluye las 7 filas del otro tenant' ($pagina1.totalRecords -eq 1005) "total=$($pagina1.totalRecords)"
    Check 'solo descarga la pagina visible' (@($pagina1.items).Count -eq 20) "items=$(@($pagina1.items).Count)"
    Check 'conserva page/pageSize del contrato' ($pagina1.page -eq 1 -and $pagina1.pageSize -eq 20) "$($pagina1.page)/$($pagina1.pageSize)"

    $ids = @($pagina1.items | ForEach-Object { [long]$PSItem.id })
    $ordenados = @($ids | Sort-Object)
    Check 'el orden por id es total y determinista' (($ids -join ',') -eq ($ordenados -join ',')) ($ids -join ',')

    $ultima = Api GET "/locales?page=51&tamano=20&texto=$prefijo" $operador.token $null
    Check 'la pagina 51 contiene las 5 filas restantes' (@($ultima.items).Count -eq 5) "items=$(@($ultima.items).Count)"
    Check 'el total no depende de la pagina' ($ultima.totalRecords -eq 1005) "total=$($ultima.totalRecords)"

    $vacia = Api GET "/locales?page=999&tamano=20&texto=$prefijo" $operador.token $null
    Check 'una pagina fuera de rango es vacia, no un error' (@($vacia.items).Count -eq 0 -and $vacia.totalRecords -eq 1005) "items=$(@($vacia.items).Count), total=$($vacia.totalRecords)"

    Write-Host "`n== 3. Estado y KPI calculados en PostgreSQL ==" -ForegroundColor Cyan
    $noDisponibles = Api GET "/locales?page=1&tamano=20&texto=$prefijo&estado=N" $operador.token $null
    Check 'el filtro de estado cuenta todas las coincidencias' ($noDisponibles.totalRecords -eq 150) "total=$($noDisponibles.totalRecords)"
    Check 'todas las filas de la pagina respetan estado=N' (@($noDisponibles.items | Where-Object { $PSItem.estado -ne 'N' }).Count -eq 0) 'estado inesperado'

    $resumen = Api GET "/locales/resumen?texto=$prefijo" $operador.token $null
    Check 'resumen total completo' ($resumen.total -eq 1005) "total=$($resumen.total)"
    Check 'resumen disponibles' ($resumen.disponibles -eq 800) "D=$($resumen.disponibles)"
    Check 'resumen no disponibles' ($resumen.noDisponibles -eq 150) "N=$($resumen.noDisponibles)"
    Check 'resumen inactivos' ($resumen.inactivos -eq 55) "I=$($resumen.inactivos)"
    Check 'las partes del KPI suman el total' (($resumen.disponibles + $resumen.noDisponibles + $resumen.inactivos) -eq $resumen.total) 'suma'

    Write-Host "`n== 4. Indices del camino caliente ==" -ForegroundColor Cyan
    # El indice por estado ya no es el de V11: al eliminar la columna `estado`,
    # V17 se llevo ix_propiedad_org_estado_id con ella. V22 lo recrea sobre
    # estado_registro (+ id para el orden, + disponibilidad_comercial al final).
    $indices = Invoke-ControlLocalE2ESql "select indexname from pg_indexes where schemaname='public' and indexname in ('ix_propiedad_org_estado_registro_id_disp','ix_propiedad_org_id','ix_propiedad_codigo_trgm','ix_propiedad_direccion_trgm','ix_propiedad_distrito_trgm','ix_persona_nombre_trgm') order by indexname"
    Check 'los 6 indices del listado siguen instalados (V11 + V22)' (@($indices -split "`n" | Where-Object { $PSItem }).Count -eq 6) $indices
}
finally {
    # Conservar originales/historia hasta el teardown de la base exclusiva.
    $cierre = Assert-ControlLocalE2EContext
    Check 'el runner conserva la frontera del fixture aislado' ($cierre.Database -eq $e2e.Database) 'teardown'
}
Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
