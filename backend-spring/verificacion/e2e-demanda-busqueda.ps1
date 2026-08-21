# =====================================================================
# GATE DE BUSQUEDA POR CONJUNTO DE CANDIDATOS de las tres bandejas de
# Demanda (F3): /oportunidades, /visitas e /interacciones.
#
# Es el equivalente de e2e-locales-busqueda.ps1 para F3, y existe por la misma
# razon: las tres nacieron resolviendo el texto libre con un OR que cruzaba
# tablas —lo que la §5 de contrato-listados-paginados.md prohibe— y se
# reescribieron por ramas + UNION. Este gate mide si esa reescritura cumple, y
# comprueba ademas que la semantica no cambio.
#
# Que verifica, en este orden:
#   1. Semantica: cada rama casa lo suyo, sin duplicados, y conteo = pagina.
#   2. El KPI del resumen sale del MISMO conjunto que la lista.
#   3. Rendimiento por HTTP sobre 100.000 filas, juzgado por los TRES criterios
#      de la §5: discriminante (< 1.000 ms), no discriminante (RC-003 y su
#      referencia sin texto como evidencia) y paginacion profunda (RC-003).
#   4. PLANES: cada rama entra por su indice y NINGUNA de las tres tablas
#      grandes se recorre entera (que es lo que delataria un OR reintroducido).
#   5. Guarda estatica: el metodo JPQL de listado no vuelve a recibir el texto.
#
# Objetivo de la busqueda DISCRIMINANTE: p95 < 1000 ms y peor en
# regimen < 2000 ms. Limite absoluto RC-003: < 3000 ms.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite demanda-busqueda
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0; $fail = 0

# Volumen del banco. 100.000 es el numero del requisito; se puede bajar para
# iterar en local, pero el gate se firma con 100.000.
$VOLUMEN = if ($env:CONTROLLOCAL_E2E_DEMANDA) { [int]$env:CONTROLLOCAL_E2E_DEMANDA } else { 100000 }
# PRIMO a proposito. El contexto de la interaccion cicla con modulo 4 y el
# cliente se elige con modulo $CLIENTES: si compartieran factor, ciertos
# clientes no caerian NUNCA en ciertos contextos y ramas enteras quedarian sin
# datos sin que nada lo delatara. Con 200 pasaba exactamente eso —el cliente
# testigo no aparecia jamas en una interaccion de contexto CLIENTE—.
$CLIENTES = 199
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

# p50/p95/peor de N llamadas reales. La PRIMERA se informa aparte (columna
# Frio) y no entra en el percentil: paga el JIT del camino de consulta, la
# cache de planes vacia y las paginas fuera del buffer. Mismo criterio que el
# gate de locales, y por la misma razon: los umbrales vigilan el REGIMEN.
#
# `$termino` es el texto libre del escenario y viaja aparte para poder
# clasificarlo despues. La §5 define DISCRIMINANTE por lo que acota el TERMINO,
# y `Casa` mide el efecto CONJUNTO de todos los filtros: en cuanto hay otro
# filtro activo dejan de ser lo mismo (ver §8).
function Medir($etiqueta, $ruta, $token, $termino = '', $repeticiones = 20) {
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
        # Las tres primeras letras de la etiqueta son la bandeja (OPO/VIS/INT).
        # El modulo forma parte de la clave del termino: 'HITO' no casa lo mismo
        # en oportunidades que en visitas.
        Modulo = $etiqueta.Substring(0, 3)
        Termino = $termino
        p50 = [math]::Round($o[[int]($o.Count * 0.5)])
        p95 = [math]::Round($o[[int]($o.Count * 0.95) - 1])
        Peor = [math]::Round($o[-1])
        Frio = [math]::Round($frio)
        Casa = (ConvertFrom-Json $script:ultimo).totalRecords
    }
}

function Plan($consulta) { Sql "explain (analyze, buffers) $consulta" }

# Token con renovacion por CADUCIDAD, no por escenario.
#
# Dos limites reales chocan aqui y la corrida completa los toca los dos: el JWT
# caduca antes de que terminen las ~500 llamadas (la primera version del gate
# murio con "Token invalido o expirado."), pero renovarlo en cada escenario
# agota el limite de 10 logins por minuto de /auth/login y el gate se cae con
# un 429 —el rate limiting del contrato congelado funcionando como debe—.
# Se renueva solo cuando quedan menos de 60 s de vida.
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

Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
$script:token = $admin.token
$vidaToken = if ($admin.expiraEnSegundos) { [int]$admin.expiraEnSegundos } else { 900 }
$script:tokenExpira = (Get-Date).AddSeconds([math]::Max(60, $vidaToken - 60))
$token = $script:token
Check 'login del administrador' ($admin.rol -eq 'ADMIN') $admin.rol

try {

Write-Host "`n== 2. Banco de $VOLUMEN filas por tabla ==" -ForegroundColor Cyan
# Testigos: uno de cada 4.999 lleva la marca, de modo que el termino selectivo
# casa con ~20 filas sobre 100.000.
#
# 4.999 es PRIMO, y por la misma razon que $CLIENTES: el contexto de la
# interaccion cicla con modulo 4, y con un paso de 5.000 —multiplo de 4— TODOS
# los testigos caian en el mismo contexto. Las ramas de captacion y prospeccion
# se quedaban sin un solo testigo y el gate lo reporto como si el producto
# fallara. Con un paso primo, los testigos recorren los cuatro contextos.
#   HITO     -> direccion de la propiedad   (rama propiedad)
#   HITOCAP  -> codigo de la captacion      (rama captacion)
#   HITOPRO  -> codigo de la prospeccion    (rama prospeccion)
#   HITOOP   -> codigo de la oportunidad    (rama oportunidad)
#   HITOOBS  -> observaciones               (rama interaccion)
#   HITOCLI  -> nombre del cliente          (rama persona)   ~ VOLUMEN/CLIENTES
$agente = [int](Sql "select min(id_persona_rol) from persona_rol where tipo_rol='AGENTE'")
$propietario = [int](Sql "select min(id_persona_rol) from persona_rol where tipo_rol='PROPIETARIO'")
Check 'el seed aporta agente y propietario para colgar el banco' `
    ($agente -gt 0 -and $propietario -gt 0) "agente=$agente propietario=$propietario"

Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, precio_referencial,
                       moneda_referencial, estado_registro, disponibilidad_comercial,
                       id_rol_propietario, organizacion_id)
select 'PERFD-' || lpad(g::text, 7, '0'),
       'Avenida demanda ' || g || case when g % 4999 = 0 then ' HITO' else '' end,
       (array['Miraflores','Lima','Barranco','Surco','Ate','San Isidro'])[1 + g % 6],
       60 + (g % 400), 3000 + (g % 9000), 'PEN', 'A',
       case when g % 200 = 1 then 'D' else 'A' end,
       $propietario, 1
from generate_series(1, $VOLUMEN) g
"@ | Out-Null

Sql @"
insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
select 1, p.id_propiedad, 'rubro_permitido', 'Carga demanda'
  from propiedad p where p.organizacion_id = 1 and p.codigo like 'PERFD-%';
insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_booleano)
select 1, p.id_propiedad, 'apto_licencia_funcionamiento', true
  from propiedad p where p.organizacion_id = 1 and p.codigo like 'PERFD-%'
"@ | Out-Null

# Las captaciones del banco nacen VENCIDAS, no activas, y es deliberado: una
# ACTIVA exige exclusividad y condicion economica completas
# (ck_captacion_activa_completa, V17) y una CERRADA exige fecha y motivo de
# cierre (ck_captacion_cierre) — serian 100.000 filas mas de una tabla que
# NINGUNA de las tres busquedas mira. El estado de la captacion no participa en
# el conjunto de candidatos, asi que el banco es igual de exigente y no inventa
# datos economicos falsos.
Sql @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, id_propiedad, id_rol_agente, organizacion_id)
select 'CAPD-' || lpad(row_number() over (order by p.id_propiedad)::text, 7, '0')
       || case when row_number() over (order by p.id_propiedad) % 4999 = 0 then '-HITOCAP' else '' end,
       current_date, current_date, current_date + 180, 'V', p.id_propiedad, $agente, 1
  from propiedad p where p.organizacion_id = 1 and p.codigo like 'PERFD-%'
"@ | Out-Null

Sql @"
insert into prospeccion (codigo_prospeccion, estado, id_propiedad, id_rol_agente, organizacion_id)
select 'PROD-' || lpad(row_number() over (order by p.id_propiedad)::text, 7, '0')
       || case when row_number() over (order by p.id_propiedad) % 4999 = 0 then '-HITOPRO' else '' end,
       'P', p.id_propiedad, $agente, 1
  from propiedad p where p.organizacion_id = 1 and p.codigo like 'PERFD-%'
"@ | Out-Null

# Clientes del banco: uno de cada CLIENTES lleva el testigo del nombre, asi que
# la rama de persona casa con ~VOLUMEN/CLIENTES oportunidades.
Sql @"
insert into persona (tipo_persona, tipo_documento, numero_documento, nombres_o_razon_social,
                     telefono, correo, estado, consentimiento_uso_dato, organizacion_id)
select 'N', 'D', lpad((70000000 + g)::text, 8, '0'),
       case when g = 1 then 'Cliente Testigo HITOCLI' else 'Cliente Demanda ' || g end,
       '9' || lpad(g::text, 8, '0'), 'demanda' || g || '@perf.test', 'A', true, 1
from generate_series(1, $CLIENTES) g
"@ | Out-Null
Sql @"
insert into persona_rol (id_persona, tipo_rol, organizacion_id)
select p.id_persona, 'CLIENTE', 1 from persona p where p.correo like 'demanda%@perf.test'
"@ | Out-Null
Sql @"
insert into detalle_cliente (id_persona_rol, organizacion_id, rubro_comercial, consentimiento_contacto)
select r.id_persona_rol, 1, 'Carga demanda', true
  from persona_rol r join persona p on p.id_persona = r.id_persona
 where r.tipo_rol = 'CLIENTE' and p.correo like 'demanda%@perf.test'
"@ | Out-Null

# Oportunidades: una por captacion, cliente en round-robin sobre el banco de
# clientes y los cinco estados repartidos.
Sql @"
insert into oportunidad_comercial (organizacion_id, codigo_oportunidad, estado, id_rol_cliente,
                                   id_captacion, id_rol_agente, observaciones, fecha_registro)
select 1,
       'OPD-' || lpad(n::text, 7, '0') || case when n % 4999 = 0 then '-HITOOP' else '' end,
       (array['A','S','N','F','X'])[1 + n % 5],
       cli.id_persona_rol, c.id_captacion, $agente,
       'Carga demanda ' || n, now() - (n || ' minutes')::interval
  from (select c.id_captacion, row_number() over (order by c.id_captacion) as n
          from captacion c where c.organizacion_id = 1 and c.codigo_captacion like 'CAPD-%') c
  -- Los clientes se numeran UNA vez y se emparejan por hash. Con un `join
  -- lateral ... offset N limit 1` el reparto costaba 100.000 recorridos de la
  -- tabla de clientes y la carga del banco se iba a varios minutos.
  join (select r.id_persona_rol, (row_number() over (order by r.id_persona_rol) - 1) as k
          from persona_rol r join persona p on p.id_persona = r.id_persona
         where r.tipo_rol = 'CLIENTE' and p.correo like 'demanda%@perf.test') cli
    on cli.k = c.n % $CLIENTES
"@ | Out-Null

Sql @"
insert into visita (organizacion_id, id_oportunidad, id_rol_agente, fecha_visita, hora_visita,
                    estado, observaciones)
select 1, o.id_oportunidad, $agente, current_date + ((n % 60)::int), time '10:00',
       (array['P','G','R','N','C'])[1 + n % 5], 'Visita de carga ' || n
  from (select o.id_oportunidad, row_number() over (order by o.id_oportunidad) as n
          from oportunidad_comercial o
         where o.organizacion_id = 1 and o.codigo_oportunidad like 'OPD-%') o
"@ | Out-Null

# Interacciones: los cuatro contextos repartidos, cada uno colgando SOLO de su
# entidad (lo exige el CHECK polimorfico de V7).
Sql @"
insert into interaccion_comercial (organizacion_id, contexto, id_oportunidad, id_prospeccion,
                                   id_captacion, id_rol_cliente, id_rol_agente, canal_contacto,
                                   resultado, observaciones, fecha_hora)
select 1,
       (array['OPORTUNIDAD','CAPTACION','CLIENTE','PROSPECCION'])[1 + n % 4],
       case when n % 4 = 0 then o.id_oportunidad end,
       case when n % 4 = 3 then pro.id_prospeccion end,
       case when n % 4 = 1 then o.id_captacion end,
       case when n % 4 = 2 then o.id_rol_cliente end,
       $agente,
       (array['L','W','E','P','R','T','O'])[1 + n % 7],
       case when n % 4 = 0 then 'INTERESADO' when n % 4 = 1 then 'DOCS_SOLICITADOS'
            when n % 4 = 2 then 'SEGUIMIENTO' else 'CONTACTADO' end,
       'Contacto de carga ' || n || case when n % 4999 = 0 then ' HITOOBS' else '' end,
       now() - (n || ' minutes')::interval
  from (select o.id_oportunidad, o.id_captacion, o.id_rol_cliente,
               row_number() over (order by o.id_oportunidad) as n
          from oportunidad_comercial o
         where o.organizacion_id = 1 and o.codigo_oportunidad like 'OPD-%') o
  -- Mismo motivo que arriba: prospecciones numeradas una vez y emparejadas
  -- por hash, no un lateral con OFFSET por cada una de las 100.000 filas.
  join (select p.id_prospeccion, (row_number() over (order by p.id_prospeccion) - 1) as k
          from prospeccion p
         where p.organizacion_id = 1 and p.codigo_prospeccion like 'PROD-%'
         limit 4999) pro on pro.k = o.n % 4999
"@ | Out-Null

foreach ($t in @('propiedad','captacion','prospeccion','persona','persona_rol','detalle_cliente',
                 'oportunidad_comercial','visita','interaccion_comercial')) {
    Sql "analyze $t" | Out-Null
}

$nOp = [int](Sql "select count(*) from oportunidad_comercial where codigo_oportunidad like 'OPD-%'")
$nVi = [int](Sql "select count(*) from visita v join oportunidad_comercial o on o.id_oportunidad=v.id_oportunidad where o.codigo_oportunidad like 'OPD-%'")
$nIn = [int](Sql "select count(*) from interaccion_comercial where observaciones like 'Contacto de carga%'")
Check "el banco tiene $VOLUMEN oportunidades"  ($nOp -eq $VOLUMEN) "cargadas=$nOp"
Check "el banco tiene $VOLUMEN visitas"        ($nVi -eq $VOLUMEN) "cargadas=$nVi"
Check "el banco tiene $VOLUMEN interacciones"  ($nIn -eq $VOLUMEN) "cargadas=$nIn"

# [int] en PowerShell REDONDEA, no trunca: con un banco reducido daba 1 testigo
# esperado donde no habia ninguno. Floor es lo que corresponde al `% 5000`.
$testigos = [math]::Floor($VOLUMEN / 5000)

Write-Host "`n== 3. Semantica: cada rama casa lo suyo y no duplica ==" -ForegroundColor Cyan
$opHito    = Api GET "/oportunidades?pagina=1&tamano=100&query=HITO"     $token $null
$opHitoOp  = Api GET "/oportunidades?pagina=1&tamano=100&query=HITOOP"   $token $null
$opHitoCap = Api GET "/oportunidades?pagina=1&tamano=100&query=HITOCAP"  $token $null
$opCliente = Api GET "/oportunidades?pagina=1&tamano=100&query=HITOCLI"  $token $null
$opNada    = Api GET "/oportunidades?pagina=1&tamano=10&query=ZZZNOEXISTE" $token $null
# 'HITO' es prefijo de los otros testigos: casa la direccion (VOLUMEN/5000) mas
# las filas cuyo codigo lleva HITOOP o HITOCAP. Lo que importa es que el UNION
# NO las cuente dos veces, que es justo lo que se comprueba abajo.
Check 'rama direccion: el testigo HITO casa y no duplica' `
    ($opHito.totalRecords -ge $testigos -and
     ($opHito.items | Select-Object -ExpandProperty id -Unique).Count -eq $opHito.items.Count) `
    "total=$($opHito.totalRecords)"
Check "rama codigo de oportunidad: HITOOP casa $testigos" ($opHitoOp.totalRecords -eq $testigos) "total=$($opHitoOp.totalRecords)"
Check "rama codigo de captacion: HITOCAP casa $testigos"  ($opHitoCap.totalRecords -eq $testigos) "total=$($opHitoCap.totalRecords)"
# El reparto round-robin no da un cociente exacto, asi que lo que se comprueba
# es que la rama casa Y que todo lo que devuelve es del cliente testigo.
Check 'rama nombre del cliente: HITOCLI casa solo sus oportunidades' `
    ($opCliente.totalRecords -gt 0 -and
     @($opCliente.items | Where-Object { $_.clienteNombre -notlike '*HITOCLI*' }).Count -eq 0) `
    "total=$($opCliente.totalRecords)"
Check 'un texto sin coincidencias devuelve vacio y total 0' `
    ($opNada.totalRecords -eq 0 -and $opNada.items.Count -eq 0) "total=$($opNada.totalRecords)"

$viHito = Api GET "/visitas?pagina=1&tamano=100&query=HITO"    $token $null
$viDist = Api GET "/visitas?pagina=1&tamano=10&query=Barranco" $token $null
Check 'visitas, rama direccion: el testigo casa y no duplica' `
    ($viHito.totalRecords -ge $testigos -and
     ($viHito.items | Select-Object -ExpandProperty id -Unique).Count -eq $viHito.items.Count) `
    "total=$($viHito.totalRecords)"
Check 'visitas, rama distrito: casa el sexto de la cartera' `
    ($viDist.totalRecords -gt 0) "total=$($viDist.totalRecords)"

$inObs = Api GET "/interacciones?pagina=1&tamano=100&q=HITOOBS" $token $null
$inCap = Api GET "/interacciones?pagina=1&tamano=100&q=HITOCAP" $token $null
$inPro = Api GET "/interacciones?pagina=1&tamano=100&q=HITOPRO" $token $null
$inCli = Api GET "/interacciones?pagina=1&tamano=100&q=HITOCLI" $token $null
Check 'interacciones, rama observaciones: HITOOBS casa los suyos' `
    ($inObs.totalRecords -gt 0 -and
     ($inObs.items | Select-Object -ExpandProperty id -Unique).Count -eq $inObs.items.Count) `
    "total=$($inObs.totalRecords)"
Check 'interacciones, rama codigo de captacion' ($inCap.totalRecords -gt 0) "total=$($inCap.totalRecords)"
Check 'interacciones, rama codigo de prospeccion' ($inPro.totalRecords -gt 0) "total=$($inPro.totalRecords)"
Check 'interacciones, rama nombre del cliente' ($inCli.totalRecords -gt 0) "total=$($inCli.totalRecords)"

Write-Host "`n== 4. Conteo y pagina miran el mismo conjunto ==" -ForegroundColor Cyan
function IdsDe($pagina) { @($pagina.items | ForEach-Object { $_.id }) }
$q1 = Api GET "/oportunidades?pagina=1&tamano=40&query=HITOOP" $token $null
$q2 = Api GET "/oportunidades?pagina=2&tamano=40&query=HITOOP" $token $null
$q3 = Api GET "/oportunidades?pagina=3&tamano=40&query=HITOOP" $token $null
$ids = (IdsDe $q1) + (IdsDe $q2) + (IdsDe $q3)
Check 'paginar el conjunto entero devuelve exactamente el total' `
    ($ids.Count -eq $q1.totalRecords) "$($ids.Count) vs $($q1.totalRecords)"
Check 'las paginas no repiten filas' `
    (($ids | Select-Object -Unique).Count -eq $ids.Count) 'ids repetidos'
Check 'el orden es estable y descendente entre paginas' `
    ((($ids | Sort-Object -Descending) -join ',') -eq ($ids -join ',')) 'orden'
$vacia = Api GET "/oportunidades?pagina=9&tamano=40&query=HITOOP" $token $null
Check 'la pagina posterior a la ultima viaja vacia y conserva el total' `
    ($vacia.items.Count -eq 0 -and $vacia.totalRecords -eq $q1.totalRecords) `
    "items=$($vacia.items.Count) total=$($vacia.totalRecords)"

Write-Host "`n== 5. El KPI sale del MISMO conjunto que la lista ==" -ForegroundColor Cyan
$resOp = Api GET "/oportunidades/resumen?query=HITOOP" $token $null
$sumaOp = $resOp.abiertas + $resOp.conSolicitud + $resOp.noContinuan + $resOp.exitosas + $resOp.noFavorables
Check 'resumen de oportunidades: el total cuadra con la lista' `
    ($resOp.total -eq $q1.totalRecords) "resumen=$($resOp.total) lista=$($q1.totalRecords)"
Check 'resumen de oportunidades: los cubos suman el total' ($sumaOp -eq $resOp.total) "$sumaOp vs $($resOp.total)"
$opAbiertas = Api GET "/oportunidades?pagina=1&tamano=1&query=HITOOP&estado=A" $token $null
Check 'el cubo de abiertas coincide con el filtro de la lista' `
    ($opAbiertas.totalRecords -eq $resOp.abiertas) "lista=$($opAbiertas.totalRecords) resumen=$($resOp.abiertas)"

$resVi = Api GET "/visitas/resumen?query=HITO" $token $null
$sumaVi = $resVi.programadas + $resVi.reprogramadas + $resVi.realizadas + $resVi.noRealizadas + $resVi.canceladas
Check 'resumen de visitas: el total cuadra con la lista' `
    ($resVi.total -eq $viHito.totalRecords) "resumen=$($resVi.total) lista=$($viHito.totalRecords)"
Check 'resumen de visitas: los cubos suman el total' ($sumaVi -eq $resVi.total) "$sumaVi vs $($resVi.total)"
$viProg = Api GET "/visitas?pagina=1&tamano=1&query=HITO&estado=P" $token $null
Check 'el cubo de programadas coincide con el filtro de la lista' `
    ($viProg.totalRecords -eq $resVi.programadas) "lista=$($viProg.totalRecords) resumen=$($resVi.programadas)"

Write-Host "`n== 6. Rendimiento del texto libre ==" -ForegroundColor Cyan
$ultimaOp = [math]::Ceiling($VOLUMEN / 10)
$medidas = @(
    (Medir 'OPO casa con TODO'          "/oportunidades?pagina=1&tamano=10&query=Avenida" $token 'Avenida'),
    (Medir 'OPO casa TODO - profunda'   "/oportunidades?pagina=$ultimaOp&tamano=10&query=Avenida" $token 'Avenida'),
    (Medir 'OPO medianam. selectivo'    "/oportunidades?pagina=1&tamano=10&query=OPD-00001" $token 'OPD-00001'),
    (Medir 'OPO ~20 coincidencias'      "/oportunidades?pagina=1&tamano=10&query=HITOOP" $token 'HITOOP'),
    (Medir 'OPO por cliente'            "/oportunidades?pagina=1&tamano=10&query=HITOCLI" $token 'HITOCLI'),
    (Medir 'OPO sin coincidencias'      "/oportunidades?pagina=1&tamano=10&query=ZZZNOEXISTE" $token 'ZZZNOEXISTE'),
    (Medir 'OPO texto + estado'         "/oportunidades?pagina=1&tamano=10&query=Avenida&estado=A" $token 'Avenida'),
    (Medir 'VIS casa con TODO'          "/visitas?pagina=1&tamano=10&query=Avenida" $token 'Avenida'),
    (Medir 'VIS casa TODO - profunda'   "/visitas?pagina=$ultimaOp&tamano=10&query=Avenida" $token 'Avenida'),
    (Medir 'VIS por distrito'           "/visitas?pagina=1&tamano=10&query=Barranco" $token 'Barranco'),
    (Medir 'VIS ~20 coincidencias'      "/visitas?pagina=1&tamano=10&query=HITO" $token 'HITO'),
    (Medir 'VIS texto + estado'         "/visitas?pagina=1&tamano=10&query=Avenida&estado=P" $token 'Avenida'),
    (Medir 'INT casa con TODO'          "/interacciones?pagina=1&tamano=10&q=Contacto" $token 'Contacto'),
    (Medir 'INT casa TODO - profunda'   "/interacciones?pagina=$ultimaOp&tamano=10&q=Contacto" $token 'Contacto'),
    (Medir 'INT ~20 coincidencias'      "/interacciones?pagina=1&tamano=10&q=HITOOBS" $token 'HITOOBS'),
    (Medir 'INT por captacion'          "/interacciones?pagina=1&tamano=10&q=HITOCAP" $token 'HITOCAP'),
    (Medir 'INT por cliente'            "/interacciones?pagina=1&tamano=10&q=HITOCLI" $token 'HITOCLI'),
    (Medir 'INT texto + canal'          "/interacciones?pagina=1&tamano=10&q=Contacto&canal=L" $token 'Contacto')
)
$medidas | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$frio = ($medidas | Measure-Object Frio -Maximum).Maximum

Write-Host "`n== 7. Sin texto: la referencia de cada bandeja ==" -ForegroundColor Cyan
$sinTexto = @(
    (Medir 'OPO sin texto - pagina 1'  "/oportunidades?pagina=1&tamano=10" $token),
    (Medir 'OPO sin texto - profunda'  "/oportunidades?pagina=$ultimaOp&tamano=10" $token),
    (Medir 'VIS sin texto - pagina 1'  "/visitas?pagina=1&tamano=10" $token),
    (Medir 'VIS sin texto - profunda'  "/visitas?pagina=$ultimaOp&tamano=10" $token),
    (Medir 'INT sin texto - pagina 1'  "/interacciones?pagina=1&tamano=10" $token),
    (Medir 'INT sin texto - profunda'  "/interacciones?pagina=$ultimaOp&tamano=10" $token)
)
$sinTexto | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

# ---------------------------------------------------------------------
# LOS TRES CRITERIOS (§5, decision del 2026-08-02).
#
# El objetivo unico de "p95 <= 1000 para todo texto libre" era demasiado
# grueso: metia en el mismo saco una busqueda y un listado sin filtro. Cada
# escenario se juzga ahora por el criterio que le toca.
#
# CORREGIDO (2026-08-03): un termino es NO DISCRIMINANTE cuando **el termino**
# casa con casi todo el banco, y eso NO es lo que devuelve `Casa` en cuanto hay
# otro filtro activo — ahi `Casa` mide el efecto conjunto de todos los filtros.
# Los tres escenarios `texto + X` de este gate usaban terminos que casan con el
# 100 % del banco ('Avenida', 'Contacto') y quedaban clasificados como
# DISCRIMINANTES solo porque el segundo filtro bajaba el total: se les exigia
# el objetivo de una busqueda a lo que en realidad es un listado sin filtro.
# Pasaban por poco margen, asi que el error no se veia; en F4 el mismo defecto
# produjo un falso rojo (`texto=Calle&estado=PENDIENTES`, 28.572 filas de las
# 100.000 que casa su texto).
#
# Ahora cada escenario declara su termino y se clasifica por la cardinalidad de
# ESE TERMINO AISLADO, medida por el propio gate. La clave lleva el modulo
# delante porque el mismo termino no casa lo mismo en cada bandeja.
# ---------------------------------------------------------------------
$UMBRAL_NO_DISCRIMINANTE = [math]::Floor($VOLUMEN * 0.9)

# Cardinalidad de cada termino POR SI SOLO: se toma del escenario que lo usa sin
# ningun otro filtro (los que no llevan '+' en la etiqueta).
$casaDelTermino = @{}
foreach ($m in $medidas | Where-Object { $_.Escenario -notlike '*+*' }) {
    $clave = "$($m.Modulo)|$($m.Termino)"
    if (-not $casaDelTermino.ContainsKey($clave)) { $casaDelTermino[$clave] = $m.Casa }
}
# Si un termino no tuviera su medida aislada, el gate lo DICE en vez de
# clasificarlo a ciegas: es la unica forma de que anadir un escenario
# `texto + X` con un termino nuevo no reintroduzca el defecto en silencio.
foreach ($m in $medidas) {
    Check "$($m.Escenario): el termino '$($m.Termino)' tiene su medida aislada" `
        ($casaDelTermino.ContainsKey("$($m.Modulo)|$($m.Termino)")) `
        "falta un escenario que mida '$($m.Termino)' en $($m.Modulo) sin otros filtros"
}
function EsNoDiscriminante($m) {
    $clave = "$($m.Modulo)|$($m.Termino)"
    $casaDelTermino.ContainsKey($clave) -and $casaDelTermino[$clave] -ge $UMBRAL_NO_DISCRIMINANTE
}

$noDiscriminantes = $medidas | Where-Object { EsNoDiscriminante $_ }
$discriminantes = $medidas | Where-Object {
    -not (EsNoDiscriminante $_) -and $_.Escenario -notlike '*profunda*' }
$profundas = $medidas | Where-Object { $_.Escenario -like '*profunda*' }

Write-Host "`n== 8. Criterio 1: busqueda DISCRIMINANTE ==" -ForegroundColor Cyan
Write-Host "   ($($discriminantes.Count) escenarios; el termino acota de verdad el conjunto)"
$p95Disc = ($discriminantes | Measure-Object p95 -Maximum).Maximum
$peorDisc = ($discriminantes | Measure-Object Peor -Maximum).Maximum
Check "p95 de la busqueda discriminante < $P95_OBJETIVO ms" ($p95Disc -lt $P95_OBJETIVO) `
    "p95=$p95Disc ms en '$(($discriminantes | Sort-Object p95 -Descending | Select-Object -First 1).Escenario)'"
Check "peor observado de la busqueda discriminante < $PEOR_OBJETIVO ms" `
    ($peorDisc -lt $PEOR_OBJETIVO) "peor=$peorDisc ms"

Write-Host "`n== 9. Criterio 2: busqueda NO DISCRIMINANTE ==" -ForegroundColor Cyan
Write-Host "   ($($noDiscriminantes.Count) escenarios con >= $UMBRAL_NO_DISCRIMINANTE coincidencias)"
# Aqui NO aplica el objetivo de 1.000 ms: el termino casa con todo, asi que
# funcionalmente es listar sin filtro y el Seq Scan es el plan CORRECTO.
#
# Lo que aplica es RC-003. La comparacion contra la misma pagina sin texto se
# REGISTRA, nunca se usa como umbral, y se registran LAS DOS PROFUNDIDADES:
# cambia de signo segun la pagina —en la profunda el texto sale mejor porque
# evita el OFFSET, en la pagina 1 sale peor porque el UNION construye y
# deduplica un conjunto que el listado llano no toca—. Un gate que mirara solo
# una de las dos daria por bueno el patron por el motivo equivocado.
$evidencia = foreach ($m in $noDiscriminantes) {
    $bandeja = $m.Modulo
    $cual = if ($m.Escenario -like '*profunda*') { 'profunda' } else { 'pagina 1' }
    $ref = ($sinTexto | Where-Object { $_.Escenario -eq "$bandeja sin texto - $cual" }).p95
    Check "$($m.Escenario): bajo RC-003 (< $RC003 ms)" ($m.p95 -lt $RC003) "p95=$($m.p95) ms"
    [pscustomobject]@{
        Modulo      = $bandeja
        Profundidad = $cual
        ConTexto    = $m.p95
        SinTexto    = $ref
        Razon       = if ($ref -gt 0) { [math]::Round($m.p95 / $ref, 2) } else { 0 }
        Lectura     = if ($ref -gt 0 -and $m.p95 -le $ref) { 'la busqueda cuesta MENOS' } else { 'la busqueda cuesta MAS' }
    }
}
Write-Host "`n   Evidencia (NO es umbral): las dos profundidades de cada modulo" -ForegroundColor DarkGray
$evidencia | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
# Que las dos profundidades esten registradas es parte del criterio: sin eso,
# la comparacion aislada no puede darlo por cumplido.
foreach ($b in ($evidencia | Select-Object -ExpandProperty Modulo -Unique)) {
    $profs = @($evidencia | Where-Object { $_.Modulo -eq $b } |
        Select-Object -ExpandProperty Profundidad -Unique)
    Check "$b : el criterio 2 registra las dos profundidades" `
        ($profs.Count -eq 2) "registradas: $($profs -join ', ')"
}

Write-Host "`n== 10. Criterio 3: paginacion PROFUNDA ==" -ForegroundColor Cyan
# Fuera del gate de busqueda y solo bajo RC-003: el coste lo pone el OFFSET
# recorriendo las entradas de indice que salta, no la busqueda. La palanca es
# la paginacion por cursor/keyset, NO una proyeccion materializada.
# @() en las dos partes: con UN solo escenario profundo PowerShell trata el
# resultado como PSObject y el `+` intenta sumar objetos en vez de concatenar.
$peorProfunda = ((@($profundas) + @($sinTexto | Where-Object { $_.Escenario -like '*profunda*' })) |
    Measure-Object Peor -Maximum).Maximum
Check "la paginacion profunda se mantiene bajo RC-003 (< $RC003 ms)" `
    ($peorProfunda -lt $RC003) "peor=$peorProfunda ms"
Write-Host "        pendiente registrado: sustituir OFFSET por paginacion por cursor/keyset." -ForegroundColor DarkGray

Check "la llamada en frio respeta RC-003 (< $RC003 ms)" ($frio -lt $RC003) "frio=$frio ms"

Write-Host "`n== 11. Planes: cada rama entra por su indice, ninguna tabla se recorre entera ==" -ForegroundColor Cyan
# Las medidas de arriba pasan por el API, que fuerza el PLAN PERSONALIZADO
# dentro de su transaccion (PlanDeConsulta). Aqui se explica a mano, asi que
# hay que pedir lo mismo o se leeria el plan generico y las conclusiones serian
# las contrarias.
Sql "set plan_cache_mode = 'force_custom_plan'" | Out-Null
# El termino selectivo es el que delata un OR reintroducido: con ramas, cada
# una entra por su trigrama; con OR, la tabla grande cae a Seq Scan.
$planOp = Plan @"
select count(*) from (
  select o.id_oportunidad as id from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
   where o.organizacion_id = 1 and lower(o.codigo_oportunidad) like lower('%HITOOP%')
  union
  select o.id_oportunidad from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
   where o.organizacion_id = 1 and lower(cap.codigo_captacion) like lower('%HITOOP%')
  union
  select o.id_oportunidad from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
   where o.organizacion_id = 1 and lower(prop.direccion) like lower('%HITOOP%')
  union
  select o.id_oportunidad from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
    join persona_rol prCli on prCli.id_persona_rol = o.id_rol_cliente
    join persona perCli on perCli.id_persona = prCli.id_persona
   where o.organizacion_id = 1 and lower(perCli.nombres_o_razon_social) like lower('%HITOOP%')
) c
"@
Write-Host $planOp
# Se exige el indice de las ramas cuya tabla es GRANDE. La rama de `persona` no
# entra en la exigencia a proposito: el banco tiene ~200 clientes y ahi un Seq
# Scan es la eleccion CORRECTA del planificador, no un sintoma. Lo que delata un
# OR reintroducido es que se recorra entera una de las tablas de 100.000.
Check 'plan de oportunidades: cada rama grande entra por su trigrama' `
    ($planOp -match 'ix_oportunidad_codigo_trgm' -and $planOp -match 'ix_captacion_codigo_trgm' `
     -and $planOp -match 'ix_propiedad_direccion_trgm') `
    'falta algun indice de rama'
Check 'plan de oportunidades: sin Seq Scan de las tablas grandes' `
    ($planOp -notmatch 'Seq Scan on oportunidad_comercial' -and
     $planOp -notmatch 'Seq Scan on captacion' -and $planOp -notmatch 'Seq Scan on propiedad') 'Seq Scan'

$planIn = Plan @"
select count(*) from (
  select i.id_interaccion as id from interaccion_comercial i
   where i.organizacion_id = 1 and lower(i.observaciones) like lower('%HITOOBS%')
  union
  select i.id_interaccion from interaccion_comercial i
    join prospeccion pro on pro.id_prospeccion = i.id_prospeccion
   where i.organizacion_id = 1 and lower(pro.codigo_prospeccion) like lower('%HITOOBS%')
  union
  select i.id_interaccion from interaccion_comercial i
    join captacion cap on cap.id_captacion = i.id_captacion
   where i.organizacion_id = 1 and lower(cap.codigo_captacion) like lower('%HITOOBS%')
  union
  select i.id_interaccion from interaccion_comercial i
    join persona_rol prCli on prCli.id_persona_rol = i.id_rol_cliente
    join persona perCli on perCli.id_persona = prCli.id_persona
   where i.organizacion_id = 1 and lower(perCli.nombres_o_razon_social) like lower('%HITOOBS%')
) c
"@
Write-Host $planIn
Check 'plan de interacciones: entra por los trigramas de observaciones y codigos' `
    ($planIn -match 'ix_interaccion_observaciones_trgm' -and $planIn -match 'ix_captacion_codigo_trgm' `
     -and $planIn -match 'ix_prospeccion_codigo_trgm') 'falta algun indice de rama'
Check 'plan de interacciones: sin Seq Scan de las tablas grandes' `
    ($planIn -notmatch 'Seq Scan on interaccion_comercial' -and
     $planIn -notmatch 'Seq Scan on prospeccion' -and $planIn -notmatch 'Seq Scan on captacion') 'Seq Scan'

# Contraste explicito: el OR cruzado que la §5 prohibe, medido sobre el MISMO
# banco. Se ejecuta solo para dejar el numero en el informe.
$planOr = Plan @"
select count(*) from oportunidad_comercial o
  join captacion cap on cap.id_captacion = o.id_captacion
  join propiedad prop on prop.id_propiedad = cap.id_propiedad
  join persona_rol prCli on prCli.id_persona_rol = o.id_rol_cliente
  join persona perCli on perCli.id_persona = prCli.id_persona
 where o.organizacion_id = 1
   and (lower(o.codigo_oportunidad) like lower('%HITOOP%')
        or lower(cap.codigo_captacion) like lower('%HITOOP%')
        or lower(prop.direccion) like lower('%HITOOP%')
        or lower(perCli.nombres_o_razon_social) like lower('%HITOOP%'))
"@
Write-Host "`n-- contraste: el OR cruzado sobre el mismo banco --"
Write-Host $planOr
Check 'el OR cruzado SI cae a Seq Scan (justifica la reescritura)' `
    ($planOr -match 'Seq Scan') 'el contraste no reprodujo el Seq Scan'

Write-Host "`n== 12. Guarda estatica: el listado ya no recibe el texto ==" -ForegroundColor Cyan
# Si alguien devuelve el texto al metodo JPQL de listado, vuelve el OR cruzado
# sin que ninguna medida lo note (los tiempos solo empeoran con volumen real).
$repos = Join-Path $PSScriptRoot '..\controllocal-persistence\src\main\java\com\controllocal\persistence\repositorio'
foreach ($r in @(
    @{ Archivo = 'OportunidadComercialRepository.java'; Metodo = 'Page<OportunidadComercial> buscar' },
    @{ Archivo = 'VisitaRepository.java';               Metodo = 'Page<Visita> buscar' },
    @{ Archivo = 'InteraccionComercialRepository.java'; Metodo = 'Page<InteraccionComercial> buscar' })) {
    $texto = Get-Content -LiteralPath (Join-Path $repos $r.Archivo) -Raw
    $firma = [regex]::Match($texto, [regex]::Escape($r.Metodo) + '\((?s).*?Pageable pageable\);')
    Check "$($r.Archivo): el listado JPQL no recibe texto libre" `
        ($firma.Success -and $firma.Value -notmatch '@Param\("(query|q|texto)"\)') 'la firma volvio a llevar el texto'
}

} finally {
    # La limpieza NO es borrar filas: es tirar la base entera.
    #
    # Aqui habia nueve `delete ... like` sin indice, en una sola transaccion,
    # sobre un banco de 100.000 filas por tabla. Medido en la corrida de firma
    # del 2026-08-03 (`20260803093503-7523`): **1.014 s — 16 min 54 s— de los
    # 1.657 s de la corrida entera. El 61 % del gate era borrar filas**, mas que
    # sus doce secciones de comprobacion juntas. Y era trabajo inutil de
    # principio a fin: la base de la corrida es EXCLUSIVA
    # (`controllocal_e2e_<run_id>`, PostgreSQL sobre tmpfs) y `Invoke-E2E.ps1`
    # la destruye con su contenedor **en 4 s**. Borrar fila a fila lo que va a
    # morir entero no comprueba nada.
    #
    # Lo unico que hay que garantizar para poder tirarla entera es que la base
    # sea de verdad la efimera de esta corrida y no una compartida. Eso es lo
    # que comprueba el check, y cuesta milisegundos.
    Write-Host "`n== Limpieza: la base exclusiva de la corrida se elimina entera ==" -ForegroundColor Cyan
    $baseActual = (Sql 'select current_database()').Trim()
    Check 'el banco vive en la base efimera de la corrida (se elimina entera)' `
        ($baseActual -eq $e2e.Database -and $baseActual -like 'controllocal_e2e_*') `
        "la suite apunta a '$baseActual', que no es la base efimera de la corrida"
}

Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
