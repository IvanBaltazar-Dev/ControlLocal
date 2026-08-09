# =====================================================================
# E2E de la BUSQUEDA POR CONJUNTO DE CANDIDATOS del listado de locales.
#
# Es el gate de rendimiento de RC-003 para el texto libre, y el unico que puede
# medirlo: sobre 100.000 locales reales, por HTTP, con el API y PostgreSQL de
# verdad. Comprueba ademas que la reescritura conserva la semantica (conteo =
# pagina, sin duplicados, aislamiento por organizacion) y que el rubro entra.
#
# Objetivo interno: p95 del texto libre <= 1000 ms y peor observado < 2000 ms.
# Limite absoluto RC-003: < 3000 ms.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite locales-busqueda
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0; $fail = 0

# Volumen del banco. 100.000 es el numero del requisito; se puede bajar para
# iterar en local, pero el gate se firma con 100.000.
$VOLUMEN = if ($env:CONTROLLOCAL_E2E_LOCALES) { [int]$env:CONTROLLOCAL_E2E_LOCALES } else { 100000 }
$P95_OBJETIVO = 1000
$PEOR_OBJETIVO = 2000
$RC003 = 3000

function Check($nombre, $condicion, $detalle) {
    if ($condicion) { $script:ok++; Write-Host "  OK   $nombre" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red }
}

function Api($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $p = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 120 }
    if ($null -ne $cuerpo) {
        $p['Body'] = ($cuerpo | ConvertTo-Json -Depth 6); $p['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) { $p['ContentType'] = 'application/json' }
    Invoke-RestMethod @p
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

# p50/p95/peor de N llamadas reales.
#
# La PRIMERA llamada de cada escenario se mide y se informa aparte, en la
# columna `Frio`, pero NO entra en el percentil. No es maquillaje: en frio se
# paga el JIT del camino de consulta, la cache de planes vacia y las paginas que
# aun no estan en el buffer, y eso se ve igual con 30.000 filas que con 100.000
# —de hecho salio peor con 30.000, que es la prueba de que no depende del
# volumen—. Los umbrales vigilan el REGIMEN, que es lo que vive un usuario a
# partir de la segunda busqueda; el frio queda a la vista para que nadie lo
# descubra en produccion.
function Medir($etiqueta, $ruta, $token, $repeticiones = 20) {
    $llamada = {
        $t = Measure-Command {
            $r = Invoke-WebRequest -Uri "$base$ruta" -Headers @{ Authorization = "Bearer $token" } `
                                   -UseBasicParsing -TimeoutSec 120
            $script:ultimo = $r.Content
        }
        $t.TotalMilliseconds
    }
    $frio = & $llamada
    $ms = @()
    for ($i = 0; $i -lt $repeticiones; $i++) { $ms += & $llamada }
    $o = $ms | Sort-Object
    [pscustomobject]@{
        Escenario = $etiqueta
        p50 = [math]::Round($o[[int]($o.Count * 0.5)])
        p95 = [math]::Round($o[[int]($o.Count * 0.95) - 1])
        Peor = [math]::Round($o[-1])
        Frio = [math]::Round($frio)
        Casa = (ConvertFrom-Json $script:ultimo).totalRecords
    }
}

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
$token = $admin.token
Check 'login del administrador' ($admin.rol -eq 'ADMIN') $admin.rol

try {

Write-Host "`n== 2. Banco de $VOLUMEN locales ==" -ForegroundColor Cyan
# Cartera SESGADA a proposito: la mayoria alquilada y los disponibles en
# minoria, que es la forma de una corredora madura y el peor caso del filtro.
# Un local de cada 5.000 lleva el testigo HITO en la direccion (texto de ~20
# coincidencias) y uno de cada 1.000 un rubro propio.
Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, precio_referencial,
                       moneda_referencial, estado_registro, disponibilidad_comercial,
                       id_rol_propietario, organizacion_id)
select 'PERF-' || lpad(g::text, 7, '0'),
       'Avenida carga ' || g || case when g % 5000 = 0 then ' HITO' else '' end,
       (array['Miraflores','Lima','Barranco','Surco','Ate','San Isidro'])[1 + g % 6],
       60 + (g % 400), 3000 + (g % 9000), 'PEN',
       case when g % 10 = 0 then 'I' else 'A' end,
       case when g % 10 = 0 then 'T' when g % 200 = 1 then 'D' else 'A' end,
       (select min(id_persona_rol) from persona_rol where tipo_rol='PROPIETARIO' and organizacion_id=1),
       1
from generate_series(1, $VOLUMEN) g
"@ | Out-Null
Sql @"
insert into detalle_local_comercial (id_propiedad, rubro_permitido, apto_licencia_funcionamiento, organizacion_id)
select p.id_propiedad,
       case when p.id_propiedad % 1000 = 0 then 'Boutique de autor' else 'Carga RC-003' end,
       true, 1
  from propiedad p
 where p.organizacion_id = 1 and p.codigo like 'PERF-%'
"@ | Out-Null
Sql 'analyze propiedad' | Out-Null
Sql 'analyze detalle_local_comercial' | Out-Null
Sql 'analyze persona' | Out-Null
$cargados = [int](Sql "select count(*) from propiedad where codigo like 'PERF-%'")
Check "el banco tiene $VOLUMEN locales" ($cargados -eq $VOLUMEN) "cargados=$cargados"

Write-Host "`n== 3. Semantica: el conjunto de candidatos devuelve lo que debe ==" -ForegroundColor Cyan
$porRubro = Api GET '/locales?page=1&tamano=10&texto=Boutique' $token $null
Check 'el RUBRO entra en la busqueda (ampliacion de 2026-08-01)' `
    ($porRubro.totalRecords -eq [int]($VOLUMEN / 1000)) "total=$($porRubro.totalRecords)"
Check 'las filas del rubro traen el rubro buscado' `
    (@($porRubro.items | Where-Object { $_.rubroPermitido -ne 'Boutique de autor' }).Count -eq 0) 'rubro'

$hito = Api GET '/locales?page=1&tamano=100&texto=HITO' $token $null
Check 'el testigo HITO casa solo con los suyos' `
    ($hito.totalRecords -eq [int]($VOLUMEN / 5000)) "total=$($hito.totalRecords)"
Check 'sin duplicados: tantos ids distintos como filas' `
    (($hito.items | Select-Object -ExpandProperty id -Unique).Count -eq $hito.items.Count) 'ids'

$sinNada = Api GET '/locales?page=1&tamano=10&texto=ZZZNOEXISTE' $token $null
Check 'un texto sin coincidencias devuelve vacio y total 0' `
    ($sinNada.totalRecords -eq 0 -and $sinNada.items.Count -eq 0) "total=$($sinNada.totalRecords)"

# El local que casa por codigo, direccion, distrito, rubro y propietario a la
# vez: el UNION lo tiene que dar UNA sola vez.
Sql @"
update propiedad set codigo='PERF-CRUCE-1', direccion='Avenida CRUCE', distrito='CRUCE'
 where id_propiedad = (select min(id_propiedad) from propiedad where codigo like 'PERF-%');
update detalle_local_comercial set rubro_permitido='Rubro CRUCE'
 where id_propiedad = (select min(id_propiedad) from propiedad where codigo like 'PERF-%');
"@ | Out-Null
$cruce = Api GET '/locales?page=1&tamano=10&texto=CRUCE' $token $null
Check 'casar por varias ramas no duplica la fila' ($cruce.totalRecords -eq 1) "total=$($cruce.totalRecords)"

Write-Host "`n== 4. El conteo y la pagina miran el mismo conjunto ==" -ForegroundColor Cyan
# Se pagina de 40 en 40 sobre las 100 del rubro: tres paginas de verdad
# (40+40+20) y una cuarta vacia. OJO con PowerShell: `@($pagina.items.id)` sobre
# una pagina VACIA no da un array vacio sino uno con un $null dentro, y eso
# inflaba el conteo en uno. Por eso se proyecta con ForEach-Object.
function IdsDe($pagina) { @($pagina.items | ForEach-Object { $_.id }) }
$p1 = Api GET '/locales?page=1&tamano=40&texto=Boutique' $token $null
$p2 = Api GET '/locales?page=2&tamano=40&texto=Boutique' $token $null
$p3 = Api GET '/locales?page=3&tamano=40&texto=Boutique' $token $null
$p4 = Api GET '/locales?page=4&tamano=40&texto=Boutique' $token $null
$idsPaginados = (IdsDe $p1) + (IdsDe $p2) + (IdsDe $p3) + (IdsDe $p4)
Check 'paginar el conjunto entero devuelve exactamente el total' `
    ($idsPaginados.Count -eq $p1.totalRecords) "$($idsPaginados.Count) vs $($p1.totalRecords)"
Check 'la pagina siguiente a la ultima viaja vacia y conserva el total' `
    ($p4.items.Count -eq 0 -and $p4.totalRecords -eq $p1.totalRecords) `
    "items=$($p4.items.Count) total=$($p4.totalRecords)"
Check 'las paginas no repiten filas' `
    (($idsPaginados | Select-Object -Unique).Count -eq $idsPaginados.Count) 'ids repetidos'
Check 'el orden es por id y es estable entre paginas' `
    ((($idsPaginados | Sort-Object) -join ',') -eq ($idsPaginados -join ',')) `
    'las paginas no salen en orden de id'

$resumen = Api GET '/locales/resumen?texto=Boutique' $token $null
Check 'el KPI del resumen suma exactamente el total de la lista' `
    ($resumen.total -eq $p1.totalRecords) "resumen=$($resumen.total) lista=$($p1.totalRecords)"
$conEstado = Api GET '/locales?page=1&tamano=10&texto=Boutique&estado=D' $token $null
Check 'el desglose del resumen coincide con el filtro de la lista' `
    ($conEstado.totalRecords -eq $resumen.disponibles) `
    "lista=$($conEstado.totalRecords) resumen=$($resumen.disponibles)"

Write-Host "`n== 5. Rendimiento del texto libre (RC-003) ==" -ForegroundColor Cyan
$ultima = [math]::Ceiling($VOLUMEN / 10)
$medidas = @(
    (Medir 'casa con TODO'            "/locales?page=1&tamano=10&texto=Avenida" $token),
    (Medir 'casa con TODO - profunda' "/locales?page=$ultima&tamano=10&texto=Avenida" $token),
    (Medir 'medianamente selectivo'   "/locales?page=1&tamano=10&texto=Lima" $token),
    (Medir '~20 coincidencias'        "/locales?page=1&tamano=10&texto=HITO" $token),
    (Medir 'sin coincidencias'        "/locales?page=1&tamano=10&texto=ZZZNOEXISTE" $token),
    (Medir 'por rubro'                "/locales?page=1&tamano=10&texto=Boutique" $token),
    (Medir 'por propietario'          "/locales?page=1&tamano=10&texto=Pacifico" $token),
    (Medir 'texto + estado'           "/locales?page=1&tamano=10&texto=Avenida&estado=D" $token)
)
$medidas | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$peor = ($medidas | Measure-Object Peor -Maximum).Maximum
$frio = ($medidas | Measure-Object Frio -Maximum).Maximum

# El objetivo interno de p95 se le exige a las busquedas de PAGINA 1, que es lo
# que hace un usuario. La pagina profunda se juzga aparte y contra su propia
# referencia: alli el coste lo pone el OFFSET —recorrer 99.990 entradas de
# indice—, no la busqueda, y se mide comparandola con la MISMA pagina sin texto.
# Materializar una proyeccion de busqueda no cambiaria ese numero.
$paginaUno = $medidas | Where-Object { $_.Escenario -notlike '*profunda*' }
$profunda = $medidas | Where-Object { $_.Escenario -like '*profunda*' }
$p95 = ($paginaUno | Measure-Object p95 -Maximum).Maximum
Check "p95 del texto libre en pagina 1 <= $P95_OBJETIVO ms" ($p95 -le $P95_OBJETIVO) "p95=$p95 ms"
Check "peor observado en regimen < $PEOR_OBJETIVO ms" ($peor -lt $PEOR_OBJETIVO) "peor=$peor ms"
Check "limite absoluto RC-003 < $RC003 ms" ($peor -lt $RC003) "peor=$peor ms"
# La primera llamada no marca el objetivo interno, pero si RC-003: un usuario
# que abre la pantalla recien desplegada tambien tiene que quedar por debajo.
Check "la llamada en frio tambien respeta RC-003 (< $RC003 ms)" ($frio -lt $RC003) "frio=$frio ms"

Write-Host "`n== 6. Sin texto no se degrada ==" -ForegroundColor Cyan
$sinTexto = @(
    (Medir 'sin texto - pagina 1'        "/locales?page=1&tamano=10" $token),
    (Medir 'sin texto - pagina profunda' "/locales?page=$ultima&tamano=10" $token)
)
$sinTexto | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
Check 'el listado sin texto sigue por debajo del objetivo' `
    ((($sinTexto | Measure-Object Peor -Maximum).Maximum) -lt $PEOR_OBJETIVO) `
    "peor=$(($sinTexto | Measure-Object Peor -Maximum).Maximum) ms"

# El veredicto sobre la pagina profunda: lo que se vigila es cuanto AÑADE la
# busqueda sobre el coste que el OFFSET ya cobra sin ella. Si algun dia esa
# pagina baja de un segundo sera por cambiar OFFSET por paginacion por clave,
# no por tocar la busqueda.
$baseProfunda = ($sinTexto | Where-Object { $_.Escenario -like '*profunda*' }).p95
$textoProfunda = ($profunda | Measure-Object p95 -Maximum).Maximum
$sobrecoste = if ($baseProfunda -gt 0) { [math]::Round(($textoProfunda / $baseProfunda - 1) * 100) } else { 0 }
Check 'en la pagina profunda la busqueda no anade mas del 30% sobre el OFFSET' `
    ($sobrecoste -le 30) "sin texto=$baseProfunda ms, con texto=$textoProfunda ms (+$sobrecoste%)"

} finally {
    # La limpieza es el ENTORNO, no las filas: la base de la corrida es
    # exclusiva (`controllocal_e2e_<run_id>`, PostgreSQL sobre tmpfs) y
    # `Invoke-E2E.ps1` la destruye con su contenedor. Borrar el banco a mano
    # antes de tirar la base entera era trabajo puro; ver la nota larga en
    # `e2e-demanda-busqueda.ps1`, donde se midio.
    Write-Host "`n== Limpieza: la base exclusiva de la corrida se elimina entera ==" -ForegroundColor Cyan
    $baseActual = (Sql 'select current_database()').Trim()
    Check 'el banco vive en la base efimera de la corrida (se elimina entera)' `
        ($baseActual -eq $e2e.Database -and $baseActual -like 'controllocal_e2e_*') `
        "la suite apunta a '$baseActual', que no es la base efimera de la corrida"
}

Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
