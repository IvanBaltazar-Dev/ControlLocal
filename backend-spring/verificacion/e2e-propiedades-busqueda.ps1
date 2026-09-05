# =====================================================================
# E2E de la BUSQUEDA del LISTADO UNIVERSAL sobre volumen: GET /propiedades.
#
# POR QUE EXISTE, Y POR QUE NO BASTA CON `locales-busqueda`. Los dos recursos
# leen la MISMA tabla y NO comparten el camino de texto:
#
#   /locales      -> reescritura por CONJUNTO DE CANDIDATOS (RC-003): una rama
#                    por tabla, cada una con su indice trigrama, unidas por
#                    UNION. Ahi entra tambien el rubro.
#   /propiedades  -> un unico OR de cuatro `like` que cruza `propiedad`,
#                    `persona_rol` y `persona`, mas dos EXISTS sobre
#                    `captacion` para el filtro de operacion, y `order by id
#                    desc`. Es el predicado que la cabecera de `RAMAS_TEXTO`
#                    documenta como Seq Scan; la reescritura NUNCA llego aqui.
#
# Asi que un p95 verde en `/locales` no dice nada del listado universal. Esta
# suite es la primera linea base de `/propiedades` sobre 100.000 filas.
#
# COMO SE LEEN SUS NUMEROS. Solo hay UN limite contractual, y es el de RC-003:
# ninguna busqueda puede pasar de 3.000 ms. Eso, y los fallos funcionales, son
# lo unico que pone la suite en rojo. p50, p95, peor caso, frio y sobrecoste de
# pagina profunda se IMPRIMEN como diagnostico y no bloquean: no existe todavia
# una linea base del listado universal contra la que juzgarlos, y copiar aqui
# el 30 % relativo de `/locales` seria heredar el umbral de otro camino de
# consulta -uno que si esta optimizado- y llamarlo medida de este.
#
# EL BANCO CARGA ENCARGOS, y eso no es adorno. `/propiedades` es un listado DE
# ENCARGOS: sin `captacion`, los dos EXISTS cortan de inmediato, la segunda
# consulta de la pagina no llega a tener nada que hidratar y el p95 mediria el
# camino vacio. Un tercio de las propiedades lleva venta, un tercio alquiler y
# un sexto las dos.
#
# ANTES DE FIRMAR UNA MEDIDA: correr `sonda-transporte`. El proxy de puertos de
# Docker Desktop ha falseado ya dos gates de latencia en esta maquina, y una
# compilacion de Angular en paralelo tumba cualquier suite de busqueda solo por
# tiempos.
#
# ASCII puro y sin BOM: PS 5.1 lee un .ps1 sin BOM como ANSI y un solo caracter
# acentuado -aunque este en un comentario- rompe el parseo entero.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite propiedades-busqueda
# Volumen: 100.000 por defecto; `CONTROLLOCAL_E2E_PROPIEDADES` lo baja para iterar.
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0
$fail = 0

# Variable PROPIA, no la de `/locales`: las dos suites cargan bancos distintos
# y bajar una no debe bajar la otra sin querer.
$VOLUMEN = if ($env:CONTROLLOCAL_E2E_PROPIEDADES) { [int]$env:CONTROLLOCAL_E2E_PROPIEDADES } else { 100000 }
# El unico limite que pone en rojo. RC-003.
$RC003 = 3000
# Diagnostico, no gate. Se imprimen y se comentan; no deciden nada todavia.
$P95_REFERENCIA = 1000
$PEOR_REFERENCIA = 2000

function Check($nombre, $condicion, $detalle) {
    if ($condicion) {
        $script:ok++
        Write-Host "  OK   $nombre" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red
    }
}

function Nota($texto) { Write-Host "  --   $texto" -ForegroundColor DarkGray }

function Api($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $p = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 120 }
    if ($null -ne $cuerpo) {
        $p['Body'] = ($cuerpo | ConvertTo-Json -Depth 6)
        $p['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) {
        $p['ContentType'] = 'application/json'
    }
    Invoke-RestMethod @p
}

# `-q` no es cosmetico: sin el, psql imprime tambien la etiqueta del comando
# ("INSERT 0 1") en la salida, y un `insert ... returning id` acaba devolviendo
# "2`nINSERT 0 1", que revienta el primer `[long]` que lo lea.
function Sql($consulta) {
    $salida = (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database `
        -v ON_ERROR_STOP=1 -q -t -A -c $consulta) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "SQL fallo con codigo $LASTEXITCODE : $consulta" }
    return $salida
}

function Texto($valor) { [uri]::EscapeDataString($valor) }

# p50/p95/peor de N llamadas reales.
#
# La PRIMERA llamada de cada escenario se mide y se informa aparte, en `Frio`,
# pero NO entra en el percentil: en frio se paga el JIT del camino de consulta,
# la cache de planes vacia y las paginas que aun no estan en el buffer. Es el
# mismo criterio -y el mismo codigo- que `locales-busqueda`, para que las dos
# lineas base se puedan poner una al lado de la otra.
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
        p95 = [math]::Round($o[[math]::Max(0, [int]($o.Count * 0.95) - 1)])
        Peor = [math]::Round($o[-1])
        Frio = [math]::Round($frio)
        Casa = (ConvertFrom-Json $script:ultimo).totalRecords
    }
}

Write-Host "`n== 1. Contexto efimero y login ==" -ForegroundColor Cyan
Check 'la base lleva identificador exclusivo de corrida' `
    ($e2e.Database -match '^controllocal_e2e_' -and $e2e.Database -ne 'controllocal') $e2e.Database
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
$token = $agente.token

# ---------------------------------------------------------------------
Write-Host "`n== 2. Banco de $VOLUMEN propiedades con sus encargos ==" -ForegroundColor Cyan
# Determinista por aritmetica modular sobre `g`, nunca por azar: dos corridas
# cargan exactamente el mismo banco y los asserts se escriben como fracciones
# de $VOLUMEN, asi que bajar el volumen para iterar no rompe ninguno.
#
# Cartera SESGADA a proposito -la mayoria no disponible, los disponibles en
# minoria-, que es la forma de una corredora madura y el peor caso del filtro.
#
# Testigos:
#   g % 5000 = 0  -> ' HITO' en la direccion   (VOLUMEN/5000 filas)
#   g % 7        -> reparte los SIETE tipos    (VOLUMEN/7 de cada uno)
#   g % 3 = 0    -> encargo de VENTA
#   g % 3 = 1    -> encargo de ALQUILER
#   g % 6 = 0    -> ademas de la venta, tambien alquiler  (las dos vivas)
$cronometro = [Diagnostics.Stopwatch]::StartNew()
$idPropietario = [long](Sql "select min(id_persona_rol) from persona_rol where tipo_rol = 'PROPIETARIO' and organizacion_id = 1")
# `captacion.id_rol_agente` apunta a `detalle_agente`, NO a `persona_rol`: un
# `min(id_persona_rol) where tipo_rol='AGENTE'` puede dar un rol sin detalle.
$idAgente = [long](Sql "select min(id_persona_rol) from detalle_agente where organizacion_id = 1")
Check 'el banco tiene propietario y agente de la organizacion' `
    (($idPropietario -gt 0) -and ($idAgente -gt 0)) "propietario=$idPropietario agente=$idAgente"

Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, precio_referencial,
                       moneda_referencial, estado_registro, disponibilidad_comercial,
                       tipo_inmueble, uso, id_rol_propietario, organizacion_id)
select 'PROP-' || lpad(g::text, 7, '0'),
       'Avenida universal ' || g || case when g % 5000 = 0 then ' HITO' else '' end,
       (array['Miraflores','Lima','Barranco','Surco','Ate','San Isidro'])[1 + g % 6],
       60 + (g % 400), 3000 + (g % 9000), 'PEN',
       case when g % 10 = 0 then 'I' else 'A' end,
       case when g % 10 = 0 then 'T' when g % 200 = 1 then 'D' else 'A' end,
       (array['L','O','D','C','T','A','X'])[1 + g % 7],
       case when (array['L','O','D','C','T','A','X'])[1 + g % 7] in ('D','C') then 'V' else 'C' end,
       $idPropietario, 1
from generate_series(1, $VOLUMEN) g
"@ | Out-Null

# Las condiciones economicas van en UN insert y se enlazan despues por codigo:
# `ck_condicion_tipo_base` es una tripleta acoplada (P + R + misma moneda), y
# escribirla una sola vez evita repetirla en cada rama.
Sql @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, motivo_operacion, exclusividad, id_propiedad, id_rol_agente,
                       organizacion_id)
select 'CAPV-' || lpad(g::text, 7, '0'), current_date, current_date, current_date + 180,
       'P', 'V', true, p.id_propiedad, $idAgente, 1
  from generate_series(1, $VOLUMEN) g
  join propiedad p on p.codigo = 'PROP-' || lpad(g::text, 7, '0') and p.organizacion_id = 1
 where g % 3 = 0
"@ | Out-Null
Sql @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, motivo_operacion, exclusividad, id_propiedad, id_rol_agente,
                       organizacion_id)
select 'CAPA-' || lpad(g::text, 7, '0'), current_date, current_date, current_date + 180,
       'P', 'A', false, p.id_propiedad, $idAgente, 1
  from generate_series(1, $VOLUMEN) g
  join propiedad p on p.codigo = 'PROP-' || lpad(g::text, 7, '0') and p.organizacion_id = 1
 where g % 3 = 1 or g % 6 = 0
"@ | Out-Null
# Encargos CERRADOS, y colocados donde se pueden ver: sobre propiedades de
# `g % 3 = 2`, que son justo las que NO tienen ningun encargo vivo. Si el
# listado contara un encargo cerrado, esas filas aparecerian "en venta" y el
# total del filtro subiria en una cantidad medible. Puestos sobre una propiedad
# que ya tiene la venta viva no demostrarian nada.
Sql @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, motivo_operacion, id_propiedad, id_rol_agente, organizacion_id,
                       fecha_cierre, motivo_cierre)
select 'CAPC-' || lpad(g::text, 7, '0'), current_date, current_date, current_date + 180,
       'C', 'V', p.id_propiedad, $idAgente, 1, current_date, 'M'
  from generate_series(1, $VOLUMEN) g
  join propiedad p on p.codigo = 'PROP-' || lpad(g::text, 7, '0') and p.organizacion_id = 1
 where g % 3 = 2 and g % 100 = 2
"@ | Out-Null

# El importe solo lo lleva una fraccion: la condicion economica es la parte cara
# del encargo -`ck_condicion_tipo_base` es una tripleta acoplada: P + R + la
# misma moneda- y lo que se prueba con ella es que el importe VIAJA, no cuantas
# veces cabe en la tabla.
#
# El enlace va por el IMPORTE, que codifica el mismo `g` que el codigo del
# encargo. Emparejarlos por `row_number()` los ataria al orden de dos consultas
# distintas y bastaria una fila de la semilla con `tipo_operacion='V'` para
# desplazar el emparejamiento entero sin que nada avisara.
Sql @"
insert into condicion_economica_captacion (organizacion_id, tipo_operacion, importe_referencia,
                                           moneda_referencia, tipo_comision, base_calculo,
                                           valor_comision, moneda_comision, tratamiento_igv)
select 1, 'V', 200000 + g, 'USD', 'P', 'R', 3, 'USD', 'I'
  from generate_series(1, $VOLUMEN) g
 where g % 300 = 0
"@ | Out-Null
Sql @"
update captacion c
   set id_condicion_economica = ce.id_condicion_economica
  from condicion_economica_captacion ce
 where c.organizacion_id = 1
   and c.codigo_captacion like 'CAPV-%'
   and ce.organizacion_id = 1
   and ce.tipo_operacion = 'V'
   and ce.valor_comision = 3
   and ce.moneda_comision = 'USD'
   and ce.importe_referencia = 200000 + cast(substring(c.codigo_captacion from 6) as integer)
"@ | Out-Null

# `analyze` sobre TODO lo que toca la consulta. `captacion` y su condicion no
# aparecen en el camino de `/locales`, y sin estadisticas el plan es el del
# catalogo vacio: el p95 medido seria el de otro plan, no el del producto.
foreach ($tabla in 'propiedad', 'captacion', 'condicion_economica_captacion', 'persona', 'persona_rol') {
    Sql "analyze $tabla" | Out-Null
}
$cronometro.Stop()
$cargadas = [int](Sql "select count(*) from propiedad where organizacion_id = 1 and codigo like 'PROP-%'")
$encargosVivos = [int](Sql "select count(*) from captacion where organizacion_id = 1 and estado in ('P','O','A') and codigo_captacion like 'CAP%'")
Check "el banco tiene $VOLUMEN propiedades" ($cargadas -eq $VOLUMEN) "cargadas=$cargadas"
Nota ("carga completa en {0:n1} s ({1} encargos vivos)" -f $cronometro.Elapsed.TotalSeconds, $encargosVivos)

# ---------------------------------------------------------------------
# LA COMPOSICION DEL BANCO SE MIDE Y SE IMPRIME, no se afirma en un comentario.
#
# Un banco de 100.000 filas todas `tipo_inmueble='L'` y sin encargos mediria el
# camino heredado con nombre nuevo: los dos EXISTS cortarian de inmediato, la
# hidratacion de encargos no tendria nada que hidratar y el reparto por tipo
# nunca ejercitaria el filtro universal. El benchmark tiene que poder ROMPER las
# ramas universales, y para eso hay que mostrar que las contiene.
Write-Host "`n-- composicion del banco --" -ForegroundColor DarkGray
# El `group by` va por la COLUMNA de la subconsulta, no por el ordinal del
# select: `group by 1` apuntaria al primer item, que ya lleva el `count(*)`
# dentro, y PostgreSQL rechaza una funcion de agregado en el GROUP BY.
$porTipo = Sql @"
select tipo || ' ' || n from (
  select case p.tipo_inmueble
           when 'L' then 'LOCAL' when 'O' then 'OFICINA' when 'D' then 'DEPARTAMENTO'
           when 'C' then 'CASA'  when 'T' then 'TERRENO' when 'A' then 'ALMACEN'
           else 'OTRO' end as tipo, count(*) as n
    from propiedad p
   where p.organizacion_id = 1 and p.codigo like 'PROP-%'
   group by p.tipo_inmueble
) t order by tipo
"@
Write-Host ("     tipos: " + (($porTipo -split "`n") -join ' | ')) -ForegroundColor DarkGray
$tiposDistintos = [int](Sql "select count(distinct tipo_inmueble) from propiedad where organizacion_id = 1 and codigo like 'PROP-%'")
Check 'el banco contiene los SIETE tipos, no solo locales' ($tiposDistintos -eq 7) "tipos=$tiposDistintos"

$porEncargo = Sql @"
select caso || ' ' || count(*) from (
  select case
    when v.id_propiedad is not null and a.id_propiedad is not null then '4-VENTA+ALQUILER'
    when v.id_propiedad is not null then '2-VENTA'
    when a.id_propiedad is not null then '3-ALQUILER'
    else '1-SIN-ENCARGO' end as caso
    from propiedad p
    left join (select distinct id_propiedad from captacion
                where organizacion_id = 1 and motivo_operacion = 'V' and estado in ('P','O','A')) v
           on v.id_propiedad = p.id_propiedad
    left join (select distinct id_propiedad from captacion
                where organizacion_id = 1 and motivo_operacion = 'A' and estado in ('P','O','A')) a
           on a.id_propiedad = p.id_propiedad
   where p.organizacion_id = 1 and p.codigo like 'PROP-%'
) t group by caso order by caso
"@
Write-Host ("  encargos: " + (($porEncargo -split "`n") -join ' | ')) -ForegroundColor DarkGray
$casosDistintos = @(($porEncargo -split "`n") | Where-Object { $_ }).Count
Check 'el banco contiene los CUATRO casos de encargo' ($casosDistintos -eq 4) "casos=$casosDistintos"
# Y que ningun caso sea testimonial: con una sola fila por caso el filtro de
# operacion no llegaria a costar nada y el numero no diria nada.
$minimoPorCaso = [int](Sql @"
select min(n) from (
  select count(*) as n from propiedad p
    left join (select distinct id_propiedad from captacion
                where organizacion_id = 1 and motivo_operacion = 'V' and estado in ('P','O','A')) v
           on v.id_propiedad = p.id_propiedad
    left join (select distinct id_propiedad from captacion
                where organizacion_id = 1 and motivo_operacion = 'A' and estado in ('P','O','A')) a
           on a.id_propiedad = p.id_propiedad
   where p.organizacion_id = 1 and p.codigo like 'PROP-%'
   group by (v.id_propiedad is not null), (a.id_propiedad is not null)
) t
"@)
Check 'y ninguno de los cuatro es testimonial (>= 5% del banco)' `
    ($minimoPorCaso -ge [int][math]::Floor($VOLUMEN / 20)) "el mas chico tiene $minimoPorCaso"

# ---------------------------------------------------------------------
Write-Host "`n== 3. Semantica sobre volumen ==" -ForegroundColor Cyan
$hito = Api GET "/propiedades?page=1&page_size=100&texto=HITO" $token $null
$esperadasHito = [int][math]::Floor($VOLUMEN / 5000)
$enPrimeraPagina = [math]::Min($esperadasHito, 100)
Check "el testigo HITO casa solo con los suyos ($esperadasHito)" `
    ($hito.totalRecords -eq $esperadasHito) "total=$($hito.totalRecords)"
Check 'y la pagina trae tantas filas como caben del total' `
    (@($hito.items).Count -eq $enPrimeraPagina) "items=$(@($hito.items).Count)"
Check 'todas las filas del testigo llevan el testigo' `
    ((@($hito.items | Where-Object { "$($_.direccion)" -notmatch 'HITO' })).Count -eq 0) 'alguna sin HITO'
Check 'sin repetidos' `
    ((@($hito.items | ForEach-Object { $_.id }) | Sort-Object -Unique).Count -eq $enPrimeraPagina) 'hay duplicados'

$completo = Api GET '/propiedades?page=1&page_size=1' $token $null
Check 'el total sin filtro es el banco entero mas lo que trae la semilla' `
    ($completo.totalRecords -ge $VOLUMEN) "total=$($completo.totalRecords)"
Check 'el tope de 100 se aplica tambien sobre volumen' `
    ((Api GET '/propiedades?page=1&page_size=1000' $token $null).pageSize -eq 100) 'pageSize'

$ids = @((Api GET '/propiedades?page=1&page_size=100' $token $null).items | ForEach-Object { [long]$_.id })
Check 'el orden id DESC se mantiene sobre volumen' `
    ((@($ids) -join ',') -eq (@($ids | Sort-Object -Descending) -join ',')) 'orden roto'

# Los siete tipos, cada uno con su septima parte. Es la comprobacion que
# `/locales` no puede hacer: alli la fila no dice de que tipo es.
foreach ($tipo in 'LOCAL', 'OFICINA', 'DEPARTAMENTO', 'CASA', 'TERRENO', 'ALMACEN', 'OTRO') {
    $r = Api GET "/propiedades?page=1&page_size=1&tipoPropiedad=$tipo" $token $null
    Check "tipoPropiedad=$tipo acota a su parte del banco" `
        (($r.totalRecords -gt 0) -and ($r.totalRecords -lt $VOLUMEN)) "total=$($r.totalRecords)"
}
$textoBanco = Texto 'Avenida universal'
$sumaTipos = 0
foreach ($tipo in 'LOCAL', 'OFICINA', 'DEPARTAMENTO', 'CASA', 'TERRENO', 'ALMACEN', 'OTRO') {
    $sumaTipos += (Api GET "/propiedades?page=1&page_size=1&tipoPropiedad=$tipo&texto=$textoBanco" $token $null).totalRecords
}
Check 'los siete tipos reparten el banco entero sin solaparse' `
    ($sumaTipos -eq $VOLUMEN) "suma=$sumaTipos de $VOLUMEN"

# Encargos: los tres filtros de operacion sobre el banco.
$esperadasVenta = [int][math]::Floor($VOLUMEN / 3)
$esperadasLasDos = [int][math]::Floor($VOLUMEN / 6)
# Acotados al BANCO por su texto: la semilla trae sus propios encargos y sumar
# los suyos convertiria una cuenta exacta en una aproximacion.
$conVenta = Api GET "/propiedades?page=1&page_size=1&operaciones=VENTA&texto=$textoBanco" $token $null
$conAlquiler = Api GET "/propiedades?page=1&page_size=1&operaciones=ALQUILER&texto=$textoBanco" $token $null
$conLasDos = Api GET "/propiedades?page=1&page_size=100&operaciones=VENTA,ALQUILER&texto=$textoBanco" $token $null
Check 'operaciones=VENTA trae solo las que tienen venta viva' `
    ($conVenta.totalRecords -eq $esperadasVenta) "total=$($conVenta.totalRecords) esperado=$esperadasVenta"
Check 'operaciones=VENTA,ALQUILER es la interseccion, no la union' `
    ($conLasDos.totalRecords -eq $esperadasLasDos) `
    "V=$($conVenta.totalRecords) A=$($conAlquiler.totalRecords) VA=$($conLasDos.totalRecords) esperado=$esperadasLasDos"
Check 'y cada fila de "las dos" trae de verdad sus dos encargos, con VENTA primero' `
    ((@($conLasDos.items | Where-Object {
        (@($_.encargos).Count -ne 2) -or (@($_.encargos)[0].operacion -ne 'VENTA') -or
        (@($_.encargos)[1].operacion -ne 'ALQUILER') })).Count -eq 0) 'alguna fila no cumple'
# El encargo cerrado no puede sostener un filtro de operacion: las `CAPC-` estan
# puestas sobre propiedades sin ninguna venta viva, asi que cada una que contara
# seria un falso positivo visible en el total de arriba.
$cerradas = [int](Sql "select count(*) from captacion where organizacion_id = 1 and estado = 'C'")
Check "el banco tiene encargos cerrados que ignorar ($cerradas)" ($cerradas -gt 0) "$cerradas"
$conCerrado = [long](Sql @"
select p.id_propiedad from propiedad p
  join captacion c on c.id_propiedad = p.id_propiedad and c.estado = 'C'
 where p.organizacion_id = 1 and p.codigo like 'PROP-%'
   and c.codigo_captacion like 'CAPC-%'
 order by p.id_propiedad limit 1
"@)
$filaCerrada = @((Api GET "/propiedades?page=1&page_size=1&texto=$(Texto (Sql "select codigo from propiedad where id_propiedad = $conCerrado"))" $token $null).items)[0]
Check 'una propiedad cuyo unico encargo esta cerrado sale con la lista vacia' `
    ((@($filaCerrada.PSObject.Properties.Name) -contains 'encargos') -and (@($filaCerrada.encargos).Count -eq 0)) `
    "encargos=$(@($filaCerrada.encargos).Count)"

$conImporte = @($conLasDos.items | Where-Object {
    @($_.encargos | Where-Object { $null -ne $_.importe }).Count -gt 0 })
Nota "filas de la pagina con al menos un importe: $(@($conImporte).Count) de $(@($conLasDos.items).Count)"

# El rubro es rama CANONICA del motor, tambien sobre volumen. El testigo va a
# los tres tipos que lo admiten -`exigir_atributo_gobernado` solo deja escribir
# `rubro_permitido` en A, L y O-, uno de cada. Con `(array['L','O','D','C','T',
# 'A','X'])[1 + g % 7]`: 'L' cae en g%7=0 (g=7), 'O' en g%7=1 (g=1) y 'A' en
# g%7=5 (g=5). Los codigos se eligen POR ESA CUENTA y el Check de abajo la
# comprueba: si el reparto cambiara, el testigo no se colocaria en silencio.
$rubroTestigo = "Boutique universal $VOLUMEN"
Sql @"
insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
select 1, p.id_propiedad, 'rubro_permitido', '$rubroTestigo'
  from propiedad p
 where p.organizacion_id = 1
   and p.codigo in ('PROP-0000007', 'PROP-0000001', 'PROP-0000005')
on conflict (id_propiedad, clave) do update set valor_texto = excluded.valor_texto
"@ | Out-Null
$rubroCargado = [int](Sql "select count(*) from atributo_propiedad where organizacion_id = 1 and clave = 'rubro_permitido' and valor_texto = '$rubroTestigo'")
Check 'el testigo de rubro existe en la base sobre los tres tipos que lo admiten' `
    ($rubroCargado -eq 3) "filas=$rubroCargado"
$porRubro = Api GET "/propiedades?page=1&page_size=10&texto=$(Texto $rubroTestigo)" $token $null
Check 'el listado universal SI busca por rubro tambien sobre volumen' `
    ($porRubro.totalRecords -eq 3) "total=$($porRubro.totalRecords)"
Check 'y son ALMACEN, LOCAL y OFICINA, los tres a los que aplica' `
    (((@($porRubro.items | ForEach-Object { $_.tipoPropiedad }) | Sort-Object) -join ',') -eq 'ALMACEN,LOCAL,OFICINA') `
    ((@($porRubro.items | ForEach-Object { $_.tipoPropiedad }) | Sort-Object) -join ',')
# Control negativo sobre volumen: los cuatro tipos a los que el rubro no aplica
# no ganan candidatos por esa rama por mucho banco que haya detras.
foreach ($sinRubro in 'CASA', 'DEPARTAMENTO', 'TERRENO', 'OTRO') {
    $r = Api GET "/propiedades?page=1&page_size=1&texto=$(Texto $rubroTestigo)&tipoPropiedad=$sinRubro" $token $null
    Check "$sinRubro no gana candidatos por la rama del rubro" ($r.totalRecords -eq 0) "total=$($r.totalRecords)"
}

# ---------------------------------------------------------------------
Write-Host "`n== 4. Aislamiento de tenant sobre volumen ==" -ForegroundColor Cyan
# El vecino carga el MISMO testigo textual. Si el discriminador faltara en el
# conteo -que es una consulta distinta de la de la pagina-, el total subiria.
$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$idOrganizacionOtra = [long](Sql "insert into organizacion (codigo, nombre) values ('VECINA-$sufijo', 'Corredora vecina $sufijo') returning id_organizacion")
$idPersonaOtra = [long](Sql @"
insert into persona (tipo_persona, tipo_documento, numero_documento, nombres_o_razon_social,
                     estado, organizacion_id)
values ('N', 'D', '79$sufijo', 'Titular vecino', 'A', $idOrganizacionOtra)
returning id_persona
"@)
$idPropietarioOtro = [long](Sql @"
insert into persona_rol (id_persona, tipo_rol, organizacion_id)
values ($idPersonaOtra, 'PROPIETARIO', $idOrganizacionOtra)
returning id_persona_rol
"@)
$totalAntes = (Api GET "/propiedades?page=1&page_size=1&texto=HITO" $token $null).totalRecords
Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, estado_registro,
                       disponibilidad_comercial, tipo_inmueble, uso,
                       id_rol_propietario, organizacion_id)
select 'PROP-' || lpad(g::text, 7, '0'),
       'Avenida universal ' || g || ' HITO', 'Miraflores', 90, 'A', 'D',
       'L', 'C', $idPropietarioOtro, $idOrganizacionOtra
from generate_series(1, 50) g
"@ | Out-Null
Sql 'analyze propiedad' | Out-Null
$totalDespues = (Api GET "/propiedades?page=1&page_size=100&texto=HITO" $token $null)
Check 'las 50 del vecino no entran en el total del testigo' `
    ($totalDespues.totalRecords -eq $totalAntes) "antes=$totalAntes despues=$($totalDespues.totalRecords)"
$salidaVecinas = Sql "select id_propiedad from propiedad where organizacion_id = $idOrganizacionOtra"
$vecinas = @(($salidaVecinas -split "`n") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$enPagina = @(@($totalDespues.items | ForEach-Object { "$($_.id)" }) | Where-Object { $vecinas -contains $_ })
Check 'ni una sola fila del vecino aparece en la pagina' (@($enPagina).Count -eq 0) ($enPagina -join ',')
# El vecino repite el mismo `codigo` que esta corredora -`uq_propiedad_codigo`
# es (organizacion_id, codigo)-, asi que buscar por codigo es la prueba mas
# dura: dos filas distintas responden al mismo texto y solo una es de quien
# pregunta.
$porCodigo = Api GET "/propiedades?page=1&page_size=100&texto=$(Texto 'PROP-0000001')" $token $null
Check 'un codigo homonimo entre corredoras devuelve solo el propio' `
    ((@($porCodigo.items).Count -eq 1) -and
     (@($vecinas | Where-Object { $_ -eq "$(@($porCodigo.items)[0].id)" })).Count -eq 0) `
    "items=$(@($porCodigo.items).Count)"

# ---------------------------------------------------------------------
Write-Host "`n== 4 bis. El plan: que indices usa el motor ==" -ForegroundColor Cyan
# POR QUE ESTA AQUI. La normalizacion se hizo por una razon medible -que el
# listado universal use los indices trigrama que RC-003 dejo puestos, en vez de
# barrer la tabla- y una latencia verde no demuestra por si sola que eso pase:
# con 100.000 filas en cache un Seq Scan tambien entra en el limite.
#
# LO QUE ESTE SQL ES, Y LO QUE NO ES. Es una TRANSCRIPCION de la forma que
# compone `MotorBusquedaInmobiliaria` para el criterio del listado universal,
# escrita aqui para poder pedirle el plan a PostgreSQL. No es una segunda
# definicion de la busqueda: nadie la ejecuta en produccion, y lo que se afirma
# no es su resultado sino que la ESTRATEGIA -ramas indexables unidas- alcanza
# los indices. El gate que impide una segunda busqueda de verdad es
# `UnSoloMotorDeBusquedaTest`, sobre el codigo de produccion.
$sqlPlan = @"
explain (analyze, buffers)
select x.id from (
  select c.id as id from (
    select p.id_propiedad as id from propiedad p
     where p.organizacion_id = 1 and lower(p.direccion) like lower('%avenida universal 4242%')
    union
    select a.id_propiedad as id from atributo_propiedad a
      join propiedad p on p.id_propiedad = a.id_propiedad
     where a.organizacion_id = 1 and p.organizacion_id = 1
       and a.clave = 'rubro_permitido'
       and lower(a.valor_texto) like lower('%avenida universal 4242%')
    union
    select p.id_propiedad as id from propiedad p
      left join persona_rol rp on rp.id_persona_rol = p.id_rol_propietario
      left join persona per on per.id_persona = rp.id_persona
     where p.organizacion_id = 1
       and lower(per.nombres_o_razon_social) like lower('%avenida universal 4242%')
  ) c
  join propiedad p on p.id_propiedad = c.id
 where p.organizacion_id = 1
) x order by x.id desc limit 20 offset 0
"@
$plan = Sql $sqlPlan
Write-Host $plan -ForegroundColor DarkGray
# Lo que se exige: que alguna rama del texto llegue por un indice trigrama. Si
# el plan cae entero a Seq Scan, la normalizacion no sirvio para lo que se hizo
# -y el numero de latencia, verde o no, estaria midiendo otra cosa-.
Check 'el plan del conjunto de candidatos alcanza un indice trigrama' `
    ($plan -match 'ix_propiedad_(codigo|direccion|distrito)_trgm|ix_persona_nombre_trgm') `
    'ninguna rama uso indice trigrama'
Check 'y no barre la tabla entera para encontrar el texto' `
    ($plan -notmatch 'Seq Scan on propiedad') 'hay Seq Scan sobre propiedad'

# ---------------------------------------------------------------------
Write-Host "`n== 5. Rendimiento sobre $VOLUMEN filas ==" -ForegroundColor Cyan
Nota 'RC-003 (< 3000 ms) es lo unico que bloquea. El resto es diagnostico.'
# El nombre del propietario sale de la BASE, no de una constante: el escenario
# "texto por propietario" tiene que recorrer de verdad la rama que cruza a
# `persona`, y un nombre inventado mediria el camino sin coincidencias.
$nombrePropietario = Sql @"
select per.nombres_o_razon_social from persona per
  join persona_rol pr on pr.id_persona = per.id_persona
 where pr.id_persona_rol = $idPropietario
"@
$textoPropietario = @("$nombrePropietario".Trim() -split '\s+')[0]
Check 'el propietario del banco tiene nombre por el que buscar' `
    ($textoPropietario.Length -ge 3) "nombre='$nombrePropietario'"
$porPropietario = Api GET "/propiedades?page=1&page_size=1&texto=$(Texto $textoPropietario)" $token $null
Check 'buscar por el nombre del propietario alcanza el banco entero' `
    ($porPropietario.totalRecords -ge $VOLUMEN) "total=$($porPropietario.totalRecords)"
$paginaProfunda = [math]::Max(1, [math]::Floor($VOLUMEN / 100))
$medidas = @(
    (Medir 'listado sin filtro, pagina 1'        '/propiedades?page=1&page_size=20' $token),
    (Medir 'listado sin filtro, pagina profunda' "/propiedades?page=$paginaProfunda&page_size=20" $token),
    (Medir 'texto raro (pocas filas)'            '/propiedades?page=1&page_size=20&texto=HITO' $token),
    (Medir 'texto comun (barre la cartera)'      '/propiedades?page=1&page_size=20&texto=Avenida' $token),
    (Medir 'texto por propietario'               "/propiedades?page=1&page_size=20&texto=$(Texto $textoPropietario)" $token),
    (Medir 'tipo + distrito'                     '/propiedades?page=1&page_size=20&tipoPropiedad=CASA&distrito=Surco' $token),
    (Medir 'las dos operaciones vivas'           '/propiedades?page=1&page_size=20&operaciones=VENTA,ALQUILER' $token),
    (Medir 'texto comun + las dos operaciones'   '/propiedades?page=1&page_size=20&texto=Avenida&operaciones=VENTA,ALQUILER' $token)
)
$medidas | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

foreach ($m in $medidas) {
    Check "RC-003 en '$($m.Escenario)': peor caso < $RC003 ms" ($m.Peor -lt $RC003) "peor=$($m.Peor) ms"
    # La llamada en frio tambien la vive un usuario: no se le perdona RC-003.
    Check "RC-003 en '$($m.Escenario)': la llamada en frio < $RC003 ms" ($m.Frio -lt $RC003) "frio=$($m.Frio) ms"
}

Write-Host "`n-- diagnostico (no bloquea) --" -ForegroundColor DarkGray
foreach ($m in $medidas) {
    $aviso = @()
    if ($m.p95 -gt $P95_REFERENCIA) { $aviso += "p95 $($m.p95) > $P95_REFERENCIA" }
    if ($m.Peor -gt $PEOR_REFERENCIA) { $aviso += "peor $($m.Peor) > $PEOR_REFERENCIA" }
    if ($aviso.Count) { Nota "$($m.Escenario): $($aviso -join '; ')" }
}
$superficial = @($medidas | Where-Object { $_.Escenario -eq 'listado sin filtro, pagina 1' })[0]
$profunda = @($medidas | Where-Object { $_.Escenario -eq 'listado sin filtro, pagina profunda' })[0]
$sobrecoste = if ($superficial.p50 -gt 0) {
    [math]::Round((($profunda.p50 - $superficial.p50) / $superficial.p50) * 100)
} else { 0 }
Nota "sobrecoste de la pagina $paginaProfunda frente a la 1: $sobrecoste % (p50 $($superficial.p50) -> $($profunda.p50) ms)"

Write-Host "`n== 6. La base es la efimera de la corrida ==" -ForegroundColor Cyan
# La limpieza es tirar la base entera con su contenedor; ninguna suite borra
# filas. Lo unico que hay que comprobar es que la base era la correcta.
Check 'el banco se cargo en la base efimera' `
    ((Sql 'select current_database()') -eq $e2e.Database) (Sql 'select current_database()')

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
