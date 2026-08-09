# =====================================================================
# GATE DE BUSQUEDA POR CONJUNTO DE CANDIDATOS de la bandeja de
# SOLICITUDES (F4): GET /solicitudes y GET /solicitudes/resumen.
#
# Es el equivalente de e2e-demanda-busqueda.ps1 para F4 y existe por la misma
# razon: la bandeja estrena texto libre y la §5 de
# contrato-listados-paginados.md exige medir el patron —ramas + UNION— sobre el
# banco de 100.000 antes de dar la pantalla por cerrada.
#
# Esta bandeja tiene CINCO ramas, una mas que ninguna anterior:
#   codigo de la solicitud · codigo de la oportunidad · direccion y distrito de
#   la propiedad · nombre del cliente · nombre del agente.
#
# Que verifica, en este orden:
#   1. Semantica: cada rama casa lo suyo, sin duplicados, y conteo = pagina.
#   2. El KPI del resumen sale del MISMO conjunto que la lista, y el cubo
#      PENDIENTES (E+O) coincide con listar por ese mismo cubo.
#   3. Rendimiento por HTTP sobre 100.000 filas, con los TRES criterios de la
#      §5: discriminante (< 1.000 ms), no discriminante (RC-003, con su
#      referencia sin texto como evidencia) y paginacion profunda (RC-003).
#   4. PLANES: cada rama entra por su indice y NINGUNA tabla grande se recorre
#      entera, mas el contraste del OR prohibido sobre el mismo banco.
#   5. Guarda estatica: el metodo JPQL de listado no recibe el texto.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite solicitudes-busqueda
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0; $fail = 0

# Volumen del banco. 100.000 es el numero del requisito; se puede bajar para
# iterar en local, pero el gate se firma con 100.000.
$VOLUMEN = if ($env:CONTROLLOCAL_E2E_SOLICITUDES) { [int]$env:CONTROLLOCAL_E2E_SOLICITUDES } else { 100000 }
# PRIMO, por la misma razon que en el gate de F3: el estado de la solicitud
# cicla con modulo 7 y el cliente se elige con modulo $CLIENTES. Con factores
# comunes, ciertos clientes no caerian nunca en ciertos estados y ramas enteras
# quedarian sin datos sin que nada lo delatara.
$CLIENTES = 199
$PASO_TESTIGO = 4999
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
    $p = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 180 }
    if ($null -ne $cuerpo) {
        $p['Body'] = ($cuerpo | ConvertTo-Json -Depth 6); $p['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) { $p['ContentType'] = 'application/json' }
    Invoke-RestMethod @p
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

function Plan($consulta) { Sql "explain (analyze, buffers) $consulta" }

# Token con renovacion por CADUCIDAD, no por escenario: el JWT caduca antes de
# terminar las llamadas, pero renovarlo en cada escenario agota el limite de 10
# logins por minuto de /auth/login (429 del contrato congelado).
$script:token = $null
$script:tokenExpira = [datetime]::MinValue
function Token {
    if ((Get-Date) -lt $script:tokenExpira) { return $script:token }
    $r = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
    $script:token = $r.token
    $vida = if ($r.expiraEnSegundos) { [int]$r.expiraEnSegundos } else { 900 }
    $script:tokenExpira = (Get-Date).AddSeconds([math]::Max(60, $vida - 60))
    $script:token
}

# p50/p95/peor de N llamadas reales. La PRIMERA se informa aparte (columna
# Frio) y no entra en el percentil: paga el JIT del camino de consulta, la
# cache de planes vacia y las paginas fuera del buffer.
#
# `$termino` es el texto libre del escenario, y viaja aparte para poder
# clasificarlo despues: la §5 define DISCRIMINANTE por lo que acota el TERMINO,
# no por cuantas filas devuelve la peticion entera. Cuando hay otro filtro en
# juego, `Casa` mide el efecto conjunto y no sirve para clasificar (ver §8).
function Medir($etiqueta, $ruta, $termino = '', $repeticiones = 20) {
    $token = Token
    $llamada = {
        $t = Measure-Command {
            $r = Invoke-WebRequest -Uri "$base$ruta" -Headers @{ Authorization = "Bearer $token" } `
                                   -UseBasicParsing -TimeoutSec 180
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
        Termino = $termino
        p50 = [math]::Round($o[[int]($o.Count * 0.5)])
        p95 = [math]::Round($o[[int]($o.Count * 0.95) - 1])
        Peor = [math]::Round($o[-1])
        Frio = [math]::Round($frio)
        Casa = (ConvertFrom-Json $script:ultimo).totalRecords
    }
}

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
$script:token = $admin.token
$vidaToken = if ($admin.expiraEnSegundos) { [int]$admin.expiraEnSegundos } else { 900 }
$script:tokenExpira = (Get-Date).AddSeconds([math]::Max(60, $vidaToken - 60))
$token = $script:token
Check 'login del administrador' ($admin.rol -eq 'ADMIN') $admin.rol

try {

Write-Host "`n== 2. Banco de $VOLUMEN solicitudes ==" -ForegroundColor Cyan
# Testigos: uno de cada $PASO_TESTIGO lleva la marca, de modo que el termino
# selectivo casa con ~20 filas sobre 100.000.
#   HITO      -> direccion de la propiedad   (rama propiedad)
#   HITOOP    -> codigo de la oportunidad    (rama oportunidad)
#   HITOSOL   -> codigo de la solicitud      (rama solicitud)
#   HITOCLI   -> nombre del cliente          (rama persona del cliente)
#   HITOAGE   -> nombre del agente           (rama persona del agente)
$agente = [int](Sql "select min(id_persona_rol) from persona_rol where tipo_rol='AGENTE'")
$propietario = [int](Sql "select min(id_persona_rol) from persona_rol where tipo_rol='PROPIETARIO'")
Check 'el seed aporta agente y propietario para colgar el banco' `
    ($agente -gt 0 -and $propietario -gt 0) "agente=$agente propietario=$propietario"

# Un SEGUNDO agente con el testigo en el nombre: sin el, la rama del agente
# casaria con todo el banco o con nada, y no probaria nada.
Sql @"
insert into persona (tipo_persona, tipo_documento, numero_documento, nombres_o_razon_social,
                     telefono, correo, estado, consentimiento_uso_dato, organizacion_id)
values ('N', 'D', '77000001', 'Agente Testigo HITOAGE', '999000001',
        'agente.testigo@perfsol.test', 'A', true, 1)
"@ | Out-Null
Sql @"
insert into persona_rol (id_persona, tipo_rol, organizacion_id)
select id_persona, 'AGENTE', 1 from persona where correo = 'agente.testigo@perfsol.test'
"@ | Out-Null
Sql @"
insert into detalle_agente (id_persona_rol, codigo_agente, fecha_ingreso, estado_operativo, organizacion_id)
select r.id_persona_rol, 'AGE-PERFSOL', current_date, 'D', 1
  from persona_rol r join persona p on p.id_persona = r.id_persona
 where p.correo = 'agente.testigo@perfsol.test'
"@ | Out-Null
$agenteTestigo = [int](Sql @"
select r.id_persona_rol from persona_rol r join persona p on p.id_persona = r.id_persona
 where p.correo = 'agente.testigo@perfsol.test'
"@)
Check 'el agente testigo del banco existe' ($agenteTestigo -gt 0) "id=$agenteTestigo"

Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, precio_referencial,
                       moneda_referencial, estado_registro, disponibilidad_comercial,
                       id_rol_propietario, organizacion_id)
select 'PERFS-' || lpad(g::text, 7, '0'),
       'Calle solicitud ' || g || case when g % $PASO_TESTIGO = 0 then ' HITO' else '' end,
       (array['Miraflores','Lima','Barranco','Surco','Ate','San Isidro'])[1 + g % 6],
       60 + (g % 400), 3000 + (g % 9000), 'PEN', 'A', 'A',
       $propietario, 1
from generate_series(1, $VOLUMEN) g
"@ | Out-Null

Sql @"
insert into detalle_local_comercial (id_propiedad, rubro_permitido, apto_licencia_funcionamiento, organizacion_id)
select p.id_propiedad, 'Carga solicitudes', true, 1
  from propiedad p where p.organizacion_id = 1 and p.codigo like 'PERFS-%'
"@ | Out-Null

# Las captaciones del banco nacen VENCIDAS, no activas: una ACTIVA exige
# exclusividad y condicion economica completas (ck_captacion_activa_completa,
# V17) y el estado de la captacion NO participa en el conjunto de candidatos de
# esta bandeja. Igual criterio que en el gate de F3.
Sql @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, id_propiedad, id_rol_agente, organizacion_id)
select 'CAPS-' || lpad(row_number() over (order by p.id_propiedad)::text, 7, '0'),
       current_date, current_date, current_date + 180, 'V', p.id_propiedad, $agente, 1
  from propiedad p where p.organizacion_id = 1 and p.codigo like 'PERFS-%'
"@ | Out-Null

Sql @"
insert into persona (tipo_persona, tipo_documento, numero_documento, nombres_o_razon_social,
                     telefono, correo, estado, consentimiento_uso_dato, organizacion_id)
select 'N', 'D', lpad((78000000 + g)::text, 8, '0'),
       case when g = 1 then 'Cliente Testigo HITOCLI' else 'Cliente Solicitud ' || g end,
       '9' || lpad((10000000 + g)::text, 8, '0'), 'solicitud' || g || '@perfsol.test', 'A', true, 1
from generate_series(1, $CLIENTES) g
"@ | Out-Null
Sql @"
insert into persona_rol (id_persona, tipo_rol, organizacion_id)
select p.id_persona, 'CLIENTE', 1 from persona p where p.correo like 'solicitud%@perfsol.test'
"@ | Out-Null
Sql @"
insert into detalle_cliente (id_persona_rol, organizacion_id, rubro_comercial, consentimiento_contacto)
select r.id_persona_rol, 1, 'Carga solicitudes', true
  from persona_rol r join persona p on p.id_persona = r.id_persona
 where r.tipo_rol = 'CLIENTE' and p.correo like 'solicitud%@perfsol.test'
"@ | Out-Null

# Oportunidades: una por captacion, cliente emparejado por hash (no un lateral
# con OFFSET, que costaba 100.000 recorridos del banco de clientes).
Sql @"
insert into oportunidad_comercial (organizacion_id, codigo_oportunidad, estado, id_rol_cliente,
                                   id_captacion, id_rol_agente, observaciones, fecha_registro)
select 1,
       'OPS-' || lpad(n::text, 7, '0') || case when n % $PASO_TESTIGO = 0 then '-HITOOP' else '' end,
       'S', cli.id_persona_rol, c.id_captacion, $agente,
       'Carga solicitudes ' || n, now() - (n || ' minutes')::interval
  from (select c.id_captacion, row_number() over (order by c.id_captacion) as n
          from captacion c where c.organizacion_id = 1 and c.codigo_captacion like 'CAPS-%') c
  join (select r.id_persona_rol, (row_number() over (order by r.id_persona_rol) - 1) as k
          from persona_rol r join persona p on p.id_persona = r.id_persona
         where r.tipo_rol = 'CLIENTE' and p.correo like 'solicitud%@perfsol.test') cli
    on cli.k = c.n % $CLIENTES
"@ | Out-Null

# Solicitudes: una por oportunidad (uq_solicitud_oportunidad), los siete
# estados repartidos y una de cada 500 a nombre del agente testigo, para que la
# rama del agente case con ~200 filas sobre 100.000.
#
# El codigo cabe justo en VARCHAR(20): 'SOLS-' + 7 digitos + '-HITOSOL'.
Sql @"
insert into solicitud_alquiler (organizacion_id, codigo_solicitud, fecha_registro, monto_propuesto,
                                moneda, estado, id_oportunidad, id_rol_agente, plazo_contrato_meses,
                                observaciones)
select 1,
       'SOLS-' || lpad(n::text, 7, '0') || case when n % $PASO_TESTIGO = 0 then '-HITOSOL' else '' end,
       -- El cast a int es obligatorio: n viene de row_number() y es bigint, y
       -- PostgreSQL no define la resta date menos bigint. Sin el, el INSERT
       -- falla y el banco queda vacio.
       current_date - (n % 300)::int, 2000 + (n % 8000), 'PEN',
       (array['G','E','O','A','R','D','C'])[1 + n % 7],
       o.id_oportunidad,
       case when n % 500 = 0 then $agenteTestigo else $agente end,
       12 + (n % 24), 'Carga solicitudes ' || n
  from (select o.id_oportunidad, row_number() over (order by o.id_oportunidad) as n
          from oportunidad_comercial o
         where o.organizacion_id = 1 and o.codigo_oportunidad like 'OPS-%') o
"@ | Out-Null

foreach ($t in @('propiedad','captacion','persona','persona_rol','detalle_cliente','detalle_agente',
                 'oportunidad_comercial','solicitud_alquiler')) {
    Sql "analyze $t" | Out-Null
}

$nSol = [int](Sql "select count(*) from solicitud_alquiler where codigo_solicitud like 'SOLS-%'")
Check "el banco tiene $VOLUMEN solicitudes" ($nSol -eq $VOLUMEN) "cargadas=$nSol"

# [int] en PowerShell REDONDEA, no trunca: con un banco reducido daria 1
# testigo esperado donde no hay ninguno. Floor es lo que corresponde al modulo.
$testigos = [math]::Floor($VOLUMEN / $PASO_TESTIGO)

Write-Host "`n== 3. Semantica: cada rama casa lo suyo y no duplica ==" -ForegroundColor Cyan
$solHito    = Api GET "/solicitudes?pagina=1&tamano=100&texto=HITO"     $token $null
$solCodigo  = Api GET "/solicitudes?pagina=1&tamano=100&texto=HITOSOL"  $token $null
$solOp      = Api GET "/solicitudes?pagina=1&tamano=100&texto=HITOOP"   $token $null
$solCliente = Api GET "/solicitudes?pagina=1&tamano=100&texto=HITOCLI"  $token $null
$solAgente  = Api GET "/solicitudes?pagina=1&tamano=100&texto=HITOAGE"  $token $null
$solNada    = Api GET "/solicitudes?pagina=1&tamano=10&texto=ZZZNOEXISTE" $token $null
# 'HITO' es prefijo de los demas testigos: casa la direccion mas las filas cuyo
# codigo lleva HITOSOL o HITOOP. Lo que importa es que el UNION no las cuente
# dos veces.
Check 'rama direccion: el testigo HITO casa y no duplica' `
    ($solHito.totalRecords -ge $testigos -and
     ($solHito.items | Select-Object -ExpandProperty id -Unique).Count -eq $solHito.items.Count) `
    "total=$($solHito.totalRecords)"
Check "rama codigo de solicitud: HITOSOL casa $testigos" `
    ($solCodigo.totalRecords -eq $testigos) "total=$($solCodigo.totalRecords)"
Check "rama codigo de oportunidad: HITOOP casa $testigos" `
    ($solOp.totalRecords -eq $testigos) "total=$($solOp.totalRecords)"
Check 'rama nombre del cliente: HITOCLI casa solo las suyas' `
    ($solCliente.totalRecords -gt 0 -and
     @($solCliente.items | Where-Object { $_.clienteNombre -notlike '*HITOCLI*' }).Count -eq 0) `
    "total=$($solCliente.totalRecords)"
Check 'rama nombre del agente: HITOAGE casa solo las suyas' `
    ($solAgente.totalRecords -gt 0 -and
     @($solAgente.items | Where-Object { $_.agenteNombre -notlike '*HITOAGE*' }).Count -eq 0) `
    "total=$($solAgente.totalRecords)"
Check 'un texto sin coincidencias devuelve vacio y total 0' `
    ($solNada.totalRecords -eq 0 -and $solNada.items.Count -eq 0) "total=$($solNada.totalRecords)"

# El distrito viaja en la misma rama que la direccion: buscar por distrito
# tiene que casar aunque el filtro `distrito` no se use.
$solDistrito = Api GET "/solicitudes?pagina=1&tamano=10&texto=Barranco" $token $null
Check 'rama direccion: el distrito tambien es buscable' `
    ($solDistrito.totalRecords -gt 0) "total=$($solDistrito.totalRecords)"

Write-Host "`n== 4. Conteo y pagina miran el mismo conjunto ==" -ForegroundColor Cyan
function IdsDe($pagina) { @($pagina.items | ForEach-Object { $_.id }) }
$p1 = Api GET "/solicitudes?pagina=1&tamano=8&texto=HITOSOL" $token $null
$p2 = Api GET "/solicitudes?pagina=2&tamano=8&texto=HITOSOL" $token $null
$p3 = Api GET "/solicitudes?pagina=3&tamano=8&texto=HITOSOL" $token $null
$ids = (IdsDe $p1) + (IdsDe $p2) + (IdsDe $p3)
Check 'paginar el conjunto entero devuelve exactamente el total' `
    ($ids.Count -eq $p1.totalRecords) "$($ids.Count) vs $($p1.totalRecords)"
Check 'las paginas no repiten filas' `
    (($ids | Select-Object -Unique).Count -eq $ids.Count) 'ids repetidos'
Check 'el orden es estable y descendente entre paginas' `
    ((($ids | Sort-Object -Descending) -join ',') -eq ($ids -join ',')) 'orden'
$vacia = Api GET "/solicitudes?pagina=20&tamano=8&texto=HITOSOL" $token $null
Check 'la pagina posterior a la ultima viaja vacia y conserva el total' `
    ($vacia.items.Count -eq 0 -and $vacia.totalRecords -eq $p1.totalRecords) `
    "items=$($vacia.items.Count) total=$($vacia.totalRecords)"

Write-Host "`n== 5. El KPI sale del MISMO conjunto que la lista ==" -ForegroundColor Cyan
$resumen = Api GET "/solicitudes/resumen?texto=HITOSOL" $token $null
$suma = $resumen.registradas + $resumen.enRevision + $resumen.observadas + $resumen.aprobadas +
        $resumen.rechazadas + $resumen.desistidas + $resumen.cerradas
Check 'el total del resumen cuadra con la lista' `
    ($resumen.total -eq $p1.totalRecords) "resumen=$($resumen.total) lista=$($p1.totalRecords)"
Check 'los siete cubos suman el total' ($suma -eq $resumen.total) "$suma vs $($resumen.total)"
$aprobadas = Api GET "/solicitudes?pagina=1&tamano=1&texto=HITOSOL&estado=A" $token $null
Check 'el cubo de aprobadas coincide con el filtro de la lista' `
    ($aprobadas.totalRecords -eq $resumen.aprobadas) `
    "lista=$($aprobadas.totalRecords) resumen=$($resumen.aprobadas)"

# PENDIENTES no es un estado: es E + O resuelto en la base, y tiene que dar
# exactamente lo mismo que sumar los dos cubos del resumen.
$pendientes = Api GET "/solicitudes?pagina=1&tamano=1&texto=HITOSOL&estado=PENDIENTES" $token $null
Check 'el cubo PENDIENTES es exactamente en revision + observadas' `
    ($pendientes.totalRecords -eq ($resumen.enRevision + $resumen.observadas) -and
     $pendientes.totalRecords -eq $resumen.pendientes) `
    "lista=$($pendientes.totalRecords) resumen=$($resumen.pendientes)"
$pendientesMinuscula = Api GET "/solicitudes?pagina=1&tamano=1&texto=HITOSOL&estado=pendientes" $token $null
Check 'el cubo se normaliza a mayusculas' `
    ($pendientesMinuscula.totalRecords -eq $pendientes.totalRecords) `
    "minuscula=$($pendientesMinuscula.totalRecords)"

# El resumen NO acepta estado, distrito ni agente: son lo que devuelve.
$resumenConEstado = Api GET "/solicitudes/resumen?texto=HITOSOL&estado=A&distrito=Ate&idAgente=1" $token $null
Check 'el resumen ignora estado, distrito y agente' `
    ($resumenConEstado.total -eq $resumen.total) `
    "con filtros=$($resumenConEstado.total) sin filtros=$($resumen.total)"
Check 'el resumen ofrece los distritos y los agentes del alcance' `
    ($resumenConEstado.distritos.Count -gt 0 -and $resumenConEstado.agentes.Count -gt 0) `
    "distritos=$($resumenConEstado.distritos.Count) agentes=$($resumenConEstado.agentes.Count)"

# Los filtros son ADITIVOS: omitidos, el cable responde como la v1.
$sinFiltros = Api GET "/solicitudes?pagina=1&tamano=10" $token $null
$idsSinFiltros = (IdsDe $sinFiltros) -join ','
$idsOrdenados = ((IdsDe $sinFiltros) | Sort-Object -Descending) -join ','
Check 'sin filtros conserva el orden congelado por id descendente' `
    ($idsSinFiltros -eq $idsOrdenados) "orden=$idsSinFiltros"

Write-Host "`n== 6. Rendimiento del texto libre ==" -ForegroundColor Cyan
$ultima = [math]::Ceiling($VOLUMEN / 10)
$medidas = @(
    (Medir 'SOL casa con TODO'        "/solicitudes?pagina=1&tamano=10&texto=Calle" 'Calle'),
    (Medir 'SOL casa TODO - profunda' "/solicitudes?pagina=$ultima&tamano=10&texto=Calle" 'Calle'),
    (Medir 'SOL medianam. selectivo'  "/solicitudes?pagina=1&tamano=10&texto=SOLS-00001" 'SOLS-00001'),
    (Medir 'SOL ~20 por codigo'       "/solicitudes?pagina=1&tamano=10&texto=HITOSOL" 'HITOSOL'),
    (Medir 'SOL ~20 por operacion'    "/solicitudes?pagina=1&tamano=10&texto=HITOOP" 'HITOOP'),
    (Medir 'SOL por cliente'          "/solicitudes?pagina=1&tamano=10&texto=HITOCLI" 'HITOCLI'),
    (Medir 'SOL por agente'           "/solicitudes?pagina=1&tamano=10&texto=HITOAGE" 'HITOAGE'),
    (Medir 'SOL sin coincidencias'    "/solicitudes?pagina=1&tamano=10&texto=ZZZNOEXISTE" 'ZZZNOEXISTE'),
    (Medir 'SOL texto + estado'       "/solicitudes?pagina=1&tamano=10&texto=Calle&estado=A" 'Calle'),
    (Medir 'SOL texto + PENDIENTES'   "/solicitudes?pagina=1&tamano=10&texto=Calle&estado=PENDIENTES" 'Calle'),
    (Medir 'SOL resumen con texto'    "/solicitudes/resumen?texto=HITOSOL" 'HITOSOL')
)
$medidas | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$frio = ($medidas | Measure-Object Frio -Maximum).Maximum

Write-Host "`n== 7. Sin texto: la referencia de la bandeja ==" -ForegroundColor Cyan
$sinTexto = @(
    (Medir 'SOL sin texto - pagina 1' "/solicitudes?pagina=1&tamano=10"),
    (Medir 'SOL sin texto - profunda' "/solicitudes?pagina=$ultima&tamano=10")
)
$sinTexto | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

# ---------------------------------------------------------------------
# LOS TRES CRITERIOS (§5, decision del 2026-08-02). Cada escenario se juzga por
# el suyo: meter una busqueda y un listado sin filtro en el mismo saco fue el
# error que el gate de F3 corrigio.
#
# QUE es DISCRIMINANTE se decide por lo que acota el TERMINO, no por cuantas
# filas devuelve la peticion entera. El gate de F3 usaba el `totalRecords` de la
# respuesta como proxy, y ahi coincidia porque sus escenarios con otro filtro
# median rapido igualmente; aqui NO coincide: `texto=Calle&estado=PENDIENTES`
# casa 28.572 filas —por debajo del umbral— pero su texto casa con las 100.000,
# asi que el trabajo que mide es el de construir el conjunto entero. Juzgarlo
# como busqueda discriminante seria exigirle 1.000 ms a un listado sin filtro.
#
# Por eso cada escenario declara su termino y la discriminancia se lee de la
# medida de ESE MISMO termino sin otros filtros, que el propio gate ya toma.
# ---------------------------------------------------------------------
$UMBRAL_NO_DISCRIMINANTE = [math]::Floor($VOLUMEN * 0.9)
$paginados = $medidas | Where-Object { $_.Escenario -notlike '*resumen*' }

# Cuanto casa cada termino POR SI SOLO: se toma del escenario que lo usa sin
# ningun otro filtro. Si un termino no tuviera esa medida, el gate lo dice en
# vez de clasificarlo a ciegas.
$casaDelTermino = @{}
foreach ($m in $paginados | Where-Object { $_.Escenario -notlike '*+*' }) {
    if (-not $casaDelTermino.ContainsKey($m.Termino)) { $casaDelTermino[$m.Termino] = $m.Casa }
}
foreach ($m in $paginados) {
    Check "el termino '$($m.Termino)' tiene su medida sin otros filtros" `
        ($casaDelTermino.ContainsKey($m.Termino)) "escenario '$($m.Escenario)'"
}
function EsNoDiscriminante($m) {
    $casaDelTermino.ContainsKey($m.Termino) -and
        $casaDelTermino[$m.Termino] -ge $UMBRAL_NO_DISCRIMINANTE
}

$noDiscriminantes = $paginados | Where-Object { EsNoDiscriminante $_ }
$discriminantes = $paginados | Where-Object {
    -not (EsNoDiscriminante $_) -and $_.Escenario -notlike '*profunda*' }
$profundas = $paginados | Where-Object { $_.Escenario -like '*profunda*' }

Write-Host "`n== 8. Criterio 1: busqueda DISCRIMINANTE ==" -ForegroundColor Cyan
Write-Host "   ($($discriminantes.Count) escenarios; el termino acota de verdad el conjunto)"
$p95Disc = ($discriminantes | Measure-Object p95 -Maximum).Maximum
$peorDisc = ($discriminantes | Measure-Object Peor -Maximum).Maximum
Check "p95 de la busqueda discriminante < $P95_OBJETIVO ms" ($p95Disc -lt $P95_OBJETIVO) `
    "p95=$p95Disc ms en '$(($discriminantes | Sort-Object p95 -Descending | Select-Object -First 1).Escenario)'"
Check "peor observado de la busqueda discriminante < $PEOR_OBJETIVO ms" `
    ($peorDisc -lt $PEOR_OBJETIVO) "peor=$peorDisc ms"
# El resumen NO se juzga con el objetivo de 1.000 ms, y la razon es de diseno,
# no de conveniencia: la peticion agrupa TRES consultas y solo una es la
# busqueda. Los cubos si cuentan sobre el conjunto de candidatos —y eso ya lo
# vigila el criterio 1 a traves de la lista, que comparte ese conjunto—, pero
# `distritosDisponibles` y `agentesDisponibles` recorren el ALCANCE COMPLETO a
# proposito: ofrecen las opciones del alcance, no las del resultado filtrado,
# igual que en /clientes/resumen y /visitas/resumen. Su coste es el de listar
# sin filtro, asi que el limite que les aplica sin ambiguedad es RC-003.
#
# El objetivo de 1.000 ms se sigue informando —la corrida de FIRMA lo midio en
# 444 ms, maquina en reposo— para que una regresion de verdad se vea, pero no
# tumba el gate: hacerlo convertiria cualquier corrida con la maquina ocupada en
# un falso negativo (1.381 ms con una build de Angular en paralelo; 694 ms con
# el entorno de desarrollo levantado). La cifra canonica es la de la firma.
$resumenMedida = $medidas | Where-Object { $_.Escenario -like '*resumen*' }
Check "el resumen con texto se mantiene bajo RC-003 (< $RC003 ms)" `
    ($resumenMedida.p95 -lt $RC003) "p95=$($resumenMedida.p95) ms"
$objetivoResumen = if ($resumenMedida.p95 -lt $P95_OBJETIVO) { 'cumple' } else { 'NO cumple' }
Write-Host ("        informativo: el resumen $objetivoResumen el objetivo de $P95_OBJETIVO ms " +
            "(p95=$($resumenMedida.p95) ms); dos de sus tres consultas recorren el alcance entero.") `
    -ForegroundColor DarkGray

Write-Host "`n== 9. Criterio 2: busqueda NO DISCRIMINANTE ==" -ForegroundColor Cyan
Write-Host "   ($($noDiscriminantes.Count) escenarios con >= $UMBRAL_NO_DISCRIMINANTE coincidencias)"
# Aqui NO aplica el objetivo de 1.000 ms: el termino casa con todo, asi que
# funcionalmente es listar sin filtro y el Seq Scan es el plan CORRECTO. Lo que
# aplica es RC-003; la comparacion con la misma pagina sin texto se REGISTRA en
# las dos profundidades, nunca se usa como umbral.
$evidencia = foreach ($m in $noDiscriminantes) {
    $cual = if ($m.Escenario -like '*profunda*') { 'profunda' } else { 'pagina 1' }
    $ref = ($sinTexto | Where-Object { $_.Escenario -eq "SOL sin texto - $cual" }).p95
    Check "$($m.Escenario): bajo RC-003 (< $RC003 ms)" ($m.p95 -lt $RC003) "p95=$($m.p95) ms"
    [pscustomobject]@{
        Profundidad = $cual
        ConTexto    = $m.p95
        SinTexto    = $ref
        Razon       = if ($ref -gt 0) { [math]::Round($m.p95 / $ref, 2) } else { 0 }
        Lectura     = if ($ref -gt 0 -and $m.p95 -le $ref) { 'la busqueda cuesta MENOS' } else { 'la busqueda cuesta MAS' }
    }
}
Write-Host "`n   Evidencia (NO es umbral): las dos profundidades" -ForegroundColor DarkGray
$evidencia | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$profs = @($evidencia | Select-Object -ExpandProperty Profundidad -Unique)
Check 'el criterio 2 registra las dos profundidades' ($profs.Count -eq 2) `
    "registradas: $($profs -join ', ')"

Write-Host "`n== 10. Criterio 3: paginacion PROFUNDA ==" -ForegroundColor Cyan
# Fuera del gate de busqueda y solo bajo RC-003: el coste lo pone el OFFSET
# recorriendo las entradas de indice que salta, no la busqueda. La palanca es
# la paginacion por cursor/keyset, NO una proyeccion materializada.
# @() en las dos partes: con UN solo escenario profundo, PowerShell trata el
# resultado como PSObject y el `+` intenta sumar objetos en vez de concatenar.
$peorProfunda = ((@($profundas) + @($sinTexto | Where-Object { $_.Escenario -like '*profunda*' })) |
    Measure-Object Peor -Maximum).Maximum
Check "la paginacion profunda se mantiene bajo RC-003 (< $RC003 ms)" `
    ($peorProfunda -lt $RC003) "peor=$peorProfunda ms"
Write-Host "        pendiente registrado: sustituir OFFSET por paginacion por cursor/keyset." -ForegroundColor DarkGray

Check "la llamada en frio respeta RC-003 (< $RC003 ms)" ($frio -lt $RC003) "frio=$frio ms"

Write-Host "`n== 11. Planes: cada rama entra por su indice, ninguna tabla se recorre entera ==" -ForegroundColor Cyan
# Las medidas de arriba pasan por el API, que fuerza el PLAN PERSONALIZADO
# dentro de su transaccion (PlanDeConsulta). Aqui se explica a mano, asi que hay
# que pedir lo mismo o se leeria el plan generico y las conclusiones serian las
# contrarias.
Sql "set plan_cache_mode = 'force_custom_plan'" | Out-Null
$planSol = Plan @"
select count(*) from (
  select s.id_solicitud as id from solicitud_alquiler s
    join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
    join captacion cap on cap.id_captacion = op.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
   where s.organizacion_id = 1 and lower(s.codigo_solicitud) like lower('%HITOSOL%')
  union
  select s.id_solicitud from solicitud_alquiler s
    join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
    join captacion cap on cap.id_captacion = op.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
   where s.organizacion_id = 1 and lower(op.codigo_oportunidad) like lower('%HITOSOL%')
  union
  select s.id_solicitud from solicitud_alquiler s
    join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
    join captacion cap on cap.id_captacion = op.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
   where s.organizacion_id = 1 and (lower(prop.direccion) like lower('%HITOSOL%')
                                    or lower(prop.distrito) like lower('%HITOSOL%'))
  union
  select s.id_solicitud from solicitud_alquiler s
    join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
    join captacion cap on cap.id_captacion = op.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
    join persona_rol prCli on prCli.id_persona_rol = op.id_rol_cliente
    join persona perCli on perCli.id_persona = prCli.id_persona
   where s.organizacion_id = 1 and lower(perCli.nombres_o_razon_social) like lower('%HITOSOL%')
  union
  select s.id_solicitud from solicitud_alquiler s
    join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
    join captacion cap on cap.id_captacion = op.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
    join persona_rol prAg on prAg.id_persona_rol = s.id_rol_agente
    join persona perAg on perAg.id_persona = prAg.id_persona
   where s.organizacion_id = 1 and lower(perAg.nombres_o_razon_social) like lower('%HITOSOL%')
) c
"@
Write-Host $planSol
# Se exige el indice de las ramas cuya tabla es GRANDE. La rama de `persona` no
# entra en la exigencia a proposito: el banco tiene ~200 clientes y ahi un Seq
# Scan es la eleccion CORRECTA del planificador, no un sintoma.
Check 'plan de solicitudes: cada rama grande entra por su trigrama' `
    ($planSol -match 'ix_solicitud_codigo_trgm' -and $planSol -match 'ix_oportunidad_codigo_trgm' `
     -and $planSol -match 'ix_propiedad_direccion_trgm') `
    'falta algun indice de rama'
Check 'plan de solicitudes: sin Seq Scan de las tablas grandes' `
    ($planSol -notmatch 'Seq Scan on solicitud_alquiler' -and
     $planSol -notmatch 'Seq Scan on oportunidad_comercial' -and
     $planSol -notmatch 'Seq Scan on propiedad') 'Seq Scan'

# Contraste explicito: el OR cruzado que la §5 prohibe, sobre el MISMO banco.
$planOr = Plan @"
select count(*) from solicitud_alquiler s
  join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
  join captacion cap on cap.id_captacion = op.id_captacion
  join propiedad prop on prop.id_propiedad = cap.id_propiedad
  join persona_rol prCli on prCli.id_persona_rol = op.id_rol_cliente
  join persona perCli on perCli.id_persona = prCli.id_persona
  join persona_rol prAg on prAg.id_persona_rol = s.id_rol_agente
  join persona perAg on perAg.id_persona = prAg.id_persona
 where s.organizacion_id = 1
   and (lower(s.codigo_solicitud) like lower('%HITOSOL%')
        or lower(op.codigo_oportunidad) like lower('%HITOSOL%')
        or lower(prop.direccion) like lower('%HITOSOL%')
        or lower(perCli.nombres_o_razon_social) like lower('%HITOSOL%')
        or lower(perAg.nombres_o_razon_social) like lower('%HITOSOL%'))
"@
Write-Host "`n-- contraste: el OR cruzado sobre el mismo banco --"
Write-Host $planOr
Check 'el OR cruzado SI cae a Seq Scan (justifica la reescritura)' `
    ($planOr -match 'Seq Scan') 'el contraste no reprodujo el Seq Scan'

Write-Host "`n== 12. Guarda estatica: el listado ya no recibe el texto ==" -ForegroundColor Cyan
# Si alguien devuelve el texto al metodo JPQL de listado, vuelve el OR cruzado
# sin que ninguna medida lo note (los tiempos solo empeoran con volumen real).
$repo = Join-Path $PSScriptRoot '..\controllocal-persistence\src\main\java\com\controllocal\persistence\repositorio\SolicitudAlquilerRepository.java'
$fuente = Get-Content -LiteralPath $repo -Raw
$firma = [regex]::Match($fuente, 'Page<SolicitudAlquiler> buscar\((?s).*?Pageable pageable\);')
Check 'SolicitudAlquilerRepository: el listado JPQL no recibe texto libre' `
    ($firma.Success -and $firma.Value -notmatch '@Param\("(query|q|texto)"\)') `
    'la firma volvio a llevar el texto'

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
