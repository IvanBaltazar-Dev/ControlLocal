# =====================================================================
# GATE DE BUSQUEDA POR CONJUNTO DE CANDIDATOS de las tres bandejas de
# Demanda (F3): /oportunidades, /visitas e /interacciones.
#
# Es el equivalente de e2e-locales-busqueda.ps1 para F3, y existe por la misma
# razon: las tres nacieron resolviendo el texto libre con un OR que cruzaba
# tablas -lo que la seccion 5 de contrato-listados-paginados.md prohibe- y se
# reescribieron por ramas + UNION. Este gate mide si esa reescritura cumple, y
# comprueba ademas que la semantica no cambio.
#
# Que verifica, en este orden:
#   1. Semantica: cada rama casa lo suyo, sin duplicados, y conteo = pagina.
#   2. El KPI del resumen sale del MISMO conjunto que la lista.
#   3. Rendimiento por HTTP sobre 100.000 filas, juzgado por los TRES criterios
#      de la seccion 5: discriminante (< 1.000 ms), no discriminante (RC-003 y su
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
. "$PSScriptRoot/lib-interacciones-c4.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
$ok = 0; $fail = 0

# Volumen del banco. 100.000 es el numero del requisito; se puede bajar para
# iterar en local, pero el gate se firma con 100.000.
$VOLUMEN = if ($env:CONTROLLOCAL_E2E_DEMANDA) { [int]$env:CONTROLLOCAL_E2E_DEMANDA } else { 100000 }
# PRIMO a proposito. El contexto de la interaccion cicla con modulo 4 y el
# cliente se elige con modulo $CLIENTES: si compartieran factor, ciertos
# clientes no caerian NUNCA en ciertos contextos y ramas enteras quedarian sin
# datos sin que nada lo delatara. Con 200 pasaba exactamente eso -el cliente
# testigo no aparecia jamas en una interaccion de contexto CLIENTE-.
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @p) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
}

# p50/p95/peor de N llamadas reales. La PRIMERA se informa aparte (columna
# Frio) y no entra en el percentil: paga el JIT del camino de consulta, la
# cache de planes vacia y las paginas fuera del buffer. Mismo criterio que el
# gate de locales, y por la misma razon: los umbrales vigilan el REGIMEN.
#
# `$termino` es el texto libre del escenario y viaja aparte para poder
# clasificarlo despues. La seccion 5 define DISCRIMINANTE por lo que acota el TERMINO,
# y `Casa` mide el efecto CONJUNTO de todos los filtros: en cuanto hay otro
# filtro activo dejan de ser lo mismo (ver seccion 8).
function Medir($etiqueta, $ruta, $termino = '', $repeticiones = 20) {
    $llamada = {
        # Refresh outside the measured endpoint, before EACH request. A long
        # scenario must not keep a token that expired halfway through its loop.
        $token = Token
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
    $medida = [pscustomobject]@{
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
    Write-Host ('E2E_MEDICION ' + ([ordered]@{ etiqueta=$medida.Escenario; p50=$medida.p50; p95=$medida.p95; Peor=$medida.Peor; Frio=$medida.Frio; Casa=$medida.Casa } | ConvertTo-Json -Compress))
    return $medida
}

function Plan($consulta) { Invoke-ControlLocalE2ESql "begin; set local plan_cache_mode='force_custom_plan'; explain (analyze, buffers) $consulta; rollback;" }

# Token con renovacion por CADUCIDAD, no por escenario.
#
# Dos limites reales chocan aqui y la corrida completa los toca los dos: el JWT
# caduca antes de que terminen las ~500 llamadas (la primera version del gate
# murio con "Token invalido o expirado."), pero renovarlo en cada escenario
# agota el limite de 10 logins por minuto de /auth/login y el gate se cae con
# un 429 -el rate limiting del contrato congelado funcionando como debe-.
# Se renueva solo cuando quedan menos de60s de la caducidad acreditada.
$script:token = $null
$script:tokenExpira = [datetime]::MinValue
function ConservarSesionDeBanco($Sesion) {
    if(-not $Sesion.token -or -not $Sesion.expiraEn -or $Sesion.expiraEnSegundos -le 0) {
        throw 'La sesion Core no acredita token y caducidad; no se fabrica una vigencia de900segundos.'
    }
    $script:token=$Sesion.token
    $script:tokenExpira=([DateTimeOffset]::Parse([string]$Sesion.expiraEn)).UtcDateTime.AddSeconds(-60)
}
function Token {
    if ([DateTime]::UtcNow -lt $script:tokenExpira) { return $script:token }
    $r = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
    ConservarSesionDeBanco $r
    $script:token
}

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_1'
Write-Host "`n== 1. Login ==" -ForegroundColor Cyan
$sesionBanco = Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
ConservarSesionDeBanco $sesionBanco
$token = $script:token
Check 'login del agente del banco autorizado' ($sesionBanco.rol -eq 'AGENTE') $sesionBanco.rol

try {

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_2'
Write-Host "`n== 2. Banco de $VOLUMEN filas por tabla ==" -ForegroundColor Cyan
# Testigos: uno de cada 4.999 lleva la marca, de modo que el termino selectivo
# casa con ~20 filas sobre 100.000.
#
# 4.999 es PRIMO, y por la misma razon que $CLIENTES: el contexto de la
# interaccion cicla con modulo 4, y con un paso de 5.000 -multiplo de 4- TODOS
# los testigos caian en el mismo contexto. Las ramas de captacion y prospeccion
# se quedaban sin un solo testigo y el gate lo reporto como si el producto
# fallara. Con un paso primo, los testigos recorren los cuatro contextos.
#   HITO     -> direccion de la propiedad   (rama propiedad)
#   HITOCAP  -> codigo de la captacion      (rama captacion)
#   HITOPRO  -> codigo de la prospeccion    (rama prospeccion)
#   HITOOP   -> codigo de la oportunidad    (rama oportunidad)
#   HITOOBS  -> observaciones               (rama interaccion)
#   HITOCLI  -> nombre del cliente          (rama persona)   ~ VOLUMEN/CLIENTES
$agente = [long]$sesionBanco.idDominio
$propietarioDelBanco = NuevoPropietarioC2DePrueba -Token $token -Nombre 'Propietario TEST del banco de carga'
$propietario = [long]$propietarioDelBanco.id
Check 'el banco usa actor autenticado y propietario registrado por Core' `
    ($agente -gt 0 -and $propietario -gt 0) "agente=$agente propietario=$propietario"
Invoke-ControlLocalE2ESql @"
insert into propiedad (codigo, direccion, distrito, metraje, precio_referencial,
                       moneda_referencial, estado_registro, disponibilidad_comercial,
                       id_rol_propietario, organizacion_id)
select 'PERFD-' || lpad(g::text, 7, '0'),
       'Avenida demanda ' || g || case when g % $PASO_TESTIGO = 0 then ' HITO' else '' end,
       (array['Miraflores','Lima','Barranco','Surco','Ate','San Isidro'])[1 + g % 6],
       60 + (g % 400), 3000 + (g % 9000), 'PEN', 'A',
       case when g % 200 = 1 then 'D' else 'A' end,
       $propietario, $($fixtureE2E.organizacion)
from generate_series(1, $VOLUMEN) g
"@ | Out-Null

Invoke-ControlLocalE2ESql @"
insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
select $($fixtureE2E.organizacion), p.id_propiedad, 'rubro_permitido', 'Carga demanda'
  from propiedad p where p.organizacion_id = $($fixtureE2E.organizacion) and p.codigo like 'PERFD-%';
insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_booleano)
select $($fixtureE2E.organizacion), p.id_propiedad, 'apto_licencia_funcionamiento', true
  from propiedad p where p.organizacion_id = $($fixtureE2E.organizacion) and p.codigo like 'PERFD-%'
"@ | Out-Null

# Preparaciones TEST P asignadas al actor real: permiten esta lectura C1.
# No son mandatos aprobados ni acreditan pactos/hitos de las operaciones C5.
Invoke-ControlLocalE2ESql @"
-- `motivo_operacion` se declara. V50 (2026-08-18) le quito el DEFAULT 'A' que
-- traia de V5: con venta en el modelo, suponer alquiler archiva un precio de
-- venta en la serie equivocada. Este banco es de ALQUILER a proposito -- lo que
-- las tres busquedas miden es la demanda de renta.
-- Son preparaciones TEST sin decision ni pacto habilitado; V104 retiro V.
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, motivo_operacion, id_propiedad, id_rol_agente, organizacion_id)
select 'CAPD-' || lpad(row_number() over (order by p.id_propiedad)::text, 7, '0')
       || case when row_number() over (order by p.id_propiedad) % $PASO_TESTIGO = 0 then '-HITOCAP' else '' end,
       current_date, current_date, current_date + 180, 'P', 'A', p.id_propiedad, $agente, $($fixtureE2E.organizacion)
  from propiedad p where p.organizacion_id = $($fixtureE2E.organizacion) and p.codigo like 'PERFD-%'
"@ | Out-Null

Invoke-ControlLocalE2ESql @"
insert into prospeccion (codigo_prospeccion, estado, id_propiedad, id_rol_agente, organizacion_id)
select 'PROD-' || lpad(row_number() over (order by p.id_propiedad)::text, 7, '0')
       || case when row_number() over (order by p.id_propiedad) % $PASO_TESTIGO = 0 then '-HITOPRO' else '' end,
       'P', p.id_propiedad, $agente, $($fixtureE2E.organizacion)
  from propiedad p where p.organizacion_id = $($fixtureE2E.organizacion) and p.codigo like 'PERFD-%'
"@ | Out-Null

# Clientes del banco: uno de cada CLIENTES lleva el testigo del nombre, asi que
# la rama de persona casa con ~VOLUMEN/CLIENTES oportunidades.
# Los 199 clientes son recursos TEST creados por Core, con consentimiento
# explicito; no se fabrican permisos RF003 por insertar Persona/Cliente.
for ($g = 1; $g -le $CLIENTES; $g++) {
    $token=Token
    $nombreCliente = if ($g -eq 1) { 'Cliente Testigo HITOCLI' } else { 'Cliente Demanda ' + $g }
    $null = Api POST '/clientes' $token @{
        tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = ([string](70000000 + $g))
        nombre = $nombreCliente; telefono = ('9' + $g.ToString('D8')); correo = ('demanda' + $g + '@perf.test')
        rubroComercial = 'Carga demanda'; consentimientoUsoDato = $true; consentimientoContacto = $true
    }
}
# Oportunidades: una por captacion, cliente en round-robin sobre el banco de
# clientes y los cinco estados repartidos.
Invoke-ControlLocalE2ESql @"
insert into oportunidad_comercial (organizacion_id, codigo_oportunidad, estado, id_rol_cliente,
                                   id_captacion, id_rol_agente, observaciones, fecha_registro)
select $($fixtureE2E.organizacion),
       'OPD-' || lpad(n::text, 7, '0') || case when n % $PASO_TESTIGO = 0 then '-HITOOP' else '' end,
       (array['A','S','N','F','X'])[1 + n % 5],
       cli.id_persona_rol, c.id_captacion, $agente,
       'Carga demanda ' || n, now() - (n || ' minutes')::interval
  from (select c.id_captacion, row_number() over (order by c.id_captacion) as n
          from captacion c where c.organizacion_id = $($fixtureE2E.organizacion) and c.codigo_captacion like 'CAPD-%') c
  -- Los clientes se numeran UNA vez y se emparejan por hash. Con un `join
  -- lateral ... offset N limit 1` el reparto costaba 100.000 recorridos de la
  -- tabla de clientes y la carga del banco se iba a varios minutos.
  join (select r.id_persona_rol, (row_number() over (order by r.id_persona_rol) - 1) as k
          from persona_rol r join persona p on p.id_persona = r.id_persona
         where r.organizacion_id=$($fixtureE2E.organizacion) and p.organizacion_id=r.organizacion_id and r.tipo_rol = 'CLIENTE' and p.correo like 'demanda%@perf.test') cli
    on cli.k = c.n % $CLIENTES
"@ | Out-Null

Invoke-ControlLocalE2ESql @"
insert into visita (organizacion_id, id_oportunidad, id_rol_agente, fecha_visita, hora_visita,
                    estado, observaciones)
select $($fixtureE2E.organizacion), o.id_oportunidad, $agente, current_date + ((n % 60)::int), time '10:00',
       (array['P','G','R','N','C'])[1 + n % 5], 'Visita de carga ' || n
  from (select o.id_oportunidad, row_number() over (order by o.id_oportunidad) as n
          from oportunidad_comercial o
         where o.organizacion_id = $($fixtureE2E.organizacion) and o.codigo_oportunidad like 'OPD-%') o
"@ | Out-Null

# C4: el banco comparte SOLO el contexto de una plantilla autorizada creada
# por Core. No distribuye su permiso a los recursos artificiales de otras tablas.
# Los 100.000 hechos TEST tienen identidad/version/historia/vinculos propios;
# este bulkfixture no se declara como 100.000 comandos AUTO ejecutados.
$clientePlantilla = Api POST '/clientes' $token @{
    tipoPersona = 'N'; tipoDocumento = 'P'; numeroDocumento = ('C4-' + [guid]::NewGuid().ToString('N').Substring(0,20))
    nombre = 'Cliente TEST exclusivo de la plantilla C4'; consentimientoUsoDato = $true; consentimientoContacto = $true
}
$fechaComunicacion = Invoke-ControlLocalE2ESql "select to_char(clock_timestamp() at time zone 'America/Lima','YYYY-MM-DD')"
$datosPlantilla = NuevaInteraccionC4DePrueba -Vinculos @(@{ tipo = 'CLIENTE'; referencia = $clientePlantilla.id }) `
    -Resumen 'Plantilla TEST de carga C4' -Fuente 'Bulkfixture TEST C4, no actividad real' -Fecha $fechaComunicacion `
    -Tipo 'LLAMADA' -Canal 'TELEFONO' `
    -Destinatario @{ nombre = 'Destinatario TEST'; tipoDeclarado = 'PERSONA' } `
    -Interlocutor @{ nombre = 'Interlocutor TEST'; tipoDeclarado = 'PERSONA' } `
    -Declaraciones @(@{ texto = 'Declaracion de prueba de la plantilla'; fuente = 'Fixture TEST C4' })
$plantilla = (Invoke-InteraccionC4DePrueba -BaseUrl $base -Token $token -Datos $datosPlantilla -Clave ('perf-c4-' + [guid]::NewGuid().ToString('N'))).resultado
$idPlantilla = [long]$plantilla.id
Invoke-ControlLocalE2ESql @"
begin;
create temporary table comunicaciones_e2e(id bigint,n bigint,contenido jsonb) on commit drop;
insert into comunicaciones_e2e
select nextval(pg_get_serial_sequence('interaccion_comercial','id_interaccion')),g,
 jsonb_set(jsonb_set(jsonb_set(jsonb_set(i.contexto_declarado_json::jsonb,
 '{datos,resumen}',to_jsonb('Contacto de carga '||g||case when g%$PASO_TESTIGO=0 then ' HITOOBS' else '' end)),
 '{datos,destinatario,nombre}',to_jsonb('Destinatario TEST'||case when g%$PASO_TESTIGO=0 then ' HITODEST' else '' end)),
 '{datos,interlocutor,nombre}',to_jsonb('Interlocutor TEST'||case when g%$PASO_TESTIGO=0 then ' HITOINTER' else '' end)),
 '{datos,declaraciones,0,texto}',to_jsonb('Declaracion TEST'||case when g%$PASO_TESTIGO=0 then ' HITODECL' else '' end))
from generate_series(1,$VOLUMEN) g cross join interaccion_comercial i
where i.organizacion_id=$($fixtureE2E.organizacion) and i.id_interaccion=$idPlantilla;
insert into interaccion_comercial(id_interaccion,organizacion_id,id_rol_agente,version,fecha_hecho,registrado_en,id_rol_autor,
 autorizacion_json,contexto_declarado_json,version_diccionario,procedencia_json)
select n.id,i.organizacion_id,i.id_rol_agente,i.version,i.fecha_hecho,i.registrado_en,i.id_rol_autor,
 i.autorizacion_json,n.contenido::text,i.version_diccionario,i.procedencia_json
from comunicaciones_e2e n cross join interaccion_comercial i where i.id_interaccion=$idPlantilla and i.organizacion_id=$($fixtureE2E.organizacion);
insert into interaccion_vinculo(organizacion_id,id_interaccion,tipo,id_propiedad,id_persona,id_prospeccion,id_captacion,id_oportunidad,id_rol_cliente,version_referencia,creado_en)
select v.organizacion_id,n.id,v.tipo,v.id_propiedad,v.id_persona,v.id_prospeccion,v.id_captacion,v.id_oportunidad,v.id_rol_cliente,v.version_referencia,v.creado_en
from comunicaciones_e2e n cross join interaccion_vinculo v where v.organizacion_id=$($fixtureE2E.organizacion) and v.id_interaccion=$idPlantilla and v.retirado_en is null;
insert into interaccion_version(organizacion_id,id_interaccion,version,id_rol_actor,registrado_en,operacion,motivo,snapshot_json,procedencia_json)
select h.organizacion_id,n.id,h.version,h.id_rol_actor,h.registrado_en,h.operacion,'Bulkfixture TEST C4',
 jsonb_set(h.snapshot_json::jsonb,'{ficha}',(h.snapshot_json::jsonb->'ficha')||jsonb_build_object('id',n.id,'datos',n.contenido->'datos'))::text,h.procedencia_json
from comunicaciones_e2e n cross join interaccion_version h where h.organizacion_id=$($fixtureE2E.organizacion) and h.id_interaccion=$idPlantilla and h.version=$($plantilla.version);
commit;
"@ | Out-Null
foreach ($t in @('propiedad','captacion','prospeccion','persona','persona_rol','detalle_cliente',
                 'oportunidad_comercial','visita','interaccion_comercial')) {
    Invoke-ControlLocalE2ESql "analyze $t" | Out-Null
}

$nOp = [int](Invoke-ControlLocalE2ESql "select count(*) from oportunidad_comercial where organizacion_id=$($fixtureE2E.organizacion) and codigo_oportunidad like 'OPD-%'")
$nVi = [int](Invoke-ControlLocalE2ESql "select count(*) from visita v join oportunidad_comercial o on o.id_oportunidad=v.id_oportunidad where o.organizacion_id=$($fixtureE2E.organizacion) and o.codigo_oportunidad like 'OPD-%'")
$nIn = [int](Invoke-ControlLocalE2ESql "select count(*) from interaccion_comercial where organizacion_id=$($fixtureE2E.organizacion) and contexto_declarado_json::jsonb#>>'{datos,resumen}' like 'Contacto de carga%'")
Check "el banco tiene $VOLUMEN oportunidades"  ($nOp -eq $VOLUMEN) "cargadas=$nOp"
Check "el banco tiene $VOLUMEN visitas"        ($nVi -eq $VOLUMEN) "cargadas=$nVi"
Check "el banco tiene $VOLUMEN interacciones"  ($nIn -eq $VOLUMEN) "cargadas=$nIn"

# [int] en PowerShell REDONDEA, no trunca: con un banco reducido daba 1 testigo
# esperado donde no habia ninguno. Floor usa el mismo paso4999 de los testigos.
$testigos = [math]::Floor($VOLUMEN / $PASO_TESTIGO)

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_3'
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
$inCap = Api GET "/interacciones?pagina=1&tamano=100&q=HITODEST" $token $null
$inPro = Api GET "/interacciones?pagina=1&tamano=100&q=HITOINTER" $token $null
$inCli = Api GET "/interacciones?pagina=1&tamano=100&q=HITODECL" $token $null
Check 'interacciones, rama observaciones: HITOOBS casa los suyos' `
    ($inObs.totalRecords -gt 0 -and
     ($inObs.items | Select-Object -ExpandProperty id -Unique).Count -eq $inObs.items.Count) `
    "total=$($inObs.totalRecords)"
Check 'interacciones, rama destinatario declarado' ($inCap.totalRecords -gt 0) "total=$($inCap.totalRecords)"
Check 'interacciones, rama interlocutor declarado' ($inPro.totalRecords -gt 0) "total=$($inPro.totalRecords)"
Check 'interacciones, rama declaracion' ($inCli.totalRecords -gt 0) "total=$($inCli.totalRecords)"

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_4'
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

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_5'
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

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_6'
Write-Host "`n== 6. Rendimiento del texto libre ==" -ForegroundColor Cyan
$ultimaOp = [math]::Ceiling($VOLUMEN / 10)
$medidas = @(
    (Medir 'OPO casa con TODO'          "/oportunidades?pagina=1&tamano=10&query=Avenida" 'Avenida'),
    (Medir 'OPO casa TODO - profunda'   "/oportunidades?pagina=$ultimaOp&tamano=10&query=Avenida" 'Avenida'),
    (Medir 'OPO medianam. selectivo'    "/oportunidades?pagina=1&tamano=10&query=OPD-00001" 'OPD-00001'),
    (Medir 'OPO ~20 coincidencias'      "/oportunidades?pagina=1&tamano=10&query=HITOOP" 'HITOOP'),
    (Medir 'OPO por cliente'            "/oportunidades?pagina=1&tamano=10&query=HITOCLI" 'HITOCLI'),
    (Medir 'OPO sin coincidencias'      "/oportunidades?pagina=1&tamano=10&query=ZZZNOEXISTE" 'ZZZNOEXISTE'),
    (Medir 'OPO texto + estado'         "/oportunidades?pagina=1&tamano=10&query=Avenida&estado=A" 'Avenida'),
    (Medir 'VIS casa con TODO'          "/visitas?pagina=1&tamano=10&query=Avenida" 'Avenida'),
    (Medir 'VIS casa TODO - profunda'   "/visitas?pagina=$ultimaOp&tamano=10&query=Avenida" 'Avenida'),
    (Medir 'VIS por distrito'           "/visitas?pagina=1&tamano=10&query=Barranco" 'Barranco'),
    (Medir 'VIS ~20 coincidencias'      "/visitas?pagina=1&tamano=10&query=HITO" 'HITO'),
    (Medir 'VIS texto + estado'         "/visitas?pagina=1&tamano=10&query=Avenida&estado=P" 'Avenida'),
    (Medir 'INT casa con TODO'          "/interacciones?pagina=1&tamano=10&q=Contacto" 'Contacto'),
    (Medir 'INT casa TODO - profunda'   "/interacciones?pagina=$ultimaOp&tamano=10&q=Contacto" 'Contacto'),
    (Medir 'INT ~20 coincidencias'      "/interacciones?pagina=1&tamano=10&q=HITOOBS" 'HITOOBS'),
    (Medir 'INT por destinatario'          "/interacciones?pagina=1&tamano=10&q=HITODEST" 'HITODEST'),
    (Medir 'INT por declaracion'            "/interacciones?pagina=1&tamano=10&q=HITODECL" 'HITODECL'),
    (Medir 'INT texto + canal'          "/interacciones?pagina=1&tamano=10&q=Contacto&canal=TELEFONO" 'Contacto')
)
$medidas | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$frio = ($medidas | Measure-Object Frio -Maximum).Maximum

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_7'
Write-Host "`n== 7. Sin texto: la referencia de cada bandeja ==" -ForegroundColor Cyan
$sinTexto = @(
    (Medir 'OPO sin texto - pagina 1'  "/oportunidades?pagina=1&tamano=10"),
    (Medir 'OPO sin texto - profunda'  "/oportunidades?pagina=$ultimaOp&tamano=10"),
    (Medir 'VIS sin texto - pagina 1'  "/visitas?pagina=1&tamano=10"),
    (Medir 'VIS sin texto - profunda'  "/visitas?pagina=$ultimaOp&tamano=10"),
    (Medir 'INT sin texto - pagina 1'  "/interacciones?pagina=1&tamano=10"),
    (Medir 'INT sin texto - profunda'  "/interacciones?pagina=$ultimaOp&tamano=10")
)
$sinTexto | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

# ---------------------------------------------------------------------
# LOS TRES CRITERIOS (seccion 5, decision del 2026-08-02).
#
# El objetivo unico de "p95 <= 1000 para todo texto libre" era demasiado
# grueso: metia en el mismo saco una busqueda y un listado sin filtro. Cada
# escenario se juzga ahora por el criterio que le toca.
#
# CORREGIDO (2026-08-03): un termino es NO DISCRIMINANTE cuando **el termino**
# casa con casi todo el banco, y eso NO es lo que devuelve `Casa` en cuanto hay
# otro filtro activo - ahi `Casa` mide el efecto conjunto de todos los filtros.
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

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_8'
Write-Host "`n== 8. Criterio 1: busqueda DISCRIMINANTE ==" -ForegroundColor Cyan
Write-Host "   ($($discriminantes.Count) escenarios; el termino acota de verdad el conjunto)"
$p95Disc = ($discriminantes | Measure-Object p95 -Maximum).Maximum
$peorDisc = ($discriminantes | Measure-Object Peor -Maximum).Maximum
Check "p95 de la busqueda discriminante < $P95_OBJETIVO ms" ($p95Disc -lt $P95_OBJETIVO) `
    "p95=$p95Disc ms en '$(($discriminantes | Sort-Object p95 -Descending | Select-Object -First 1).Escenario)'"
Check "peor observado de la busqueda discriminante < $PEOR_OBJETIVO ms" `
    ($peorDisc -lt $PEOR_OBJETIVO) "peor=$peorDisc ms"

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_9'
Write-Host "`n== 9. Criterio 2: busqueda NO DISCRIMINANTE ==" -ForegroundColor Cyan
Write-Host "   ($($noDiscriminantes.Count) escenarios con >= $UMBRAL_NO_DISCRIMINANTE coincidencias)"
# Aqui NO aplica el objetivo de 1.000 ms: el termino casa con todo, asi que
# funcionalmente es listar sin filtro y el Seq Scan es el plan CORRECTO.
#
# Lo que aplica es RC-003. La comparacion contra la misma pagina sin texto se
# REGISTRA, nunca se usa como umbral, y se registran LAS DOS PROFUNDIDADES:
# cambia de signo segun la pagina -en la profunda el texto sale mejor porque
# evita el OFFSET, en la pagina 1 sale peor porque el UNION construye y
# deduplica un conjunto que el listado llano no toca-. Un gate que mirara solo
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

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_10'
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

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_11'
Write-Host "`n== 11. Planes: cada rama entra por su indice, ninguna tabla se recorre entera ==" -ForegroundColor Cyan
# Las medidas de arriba pasan por el API, que fuerza el PLAN PERSONALIZADO
# dentro de su transaccion (PlanDeConsulta). Aqui se explica a mano, asi que
# hay que pedir lo mismo o se leeria el plan generico y las conclusiones serian
# las contrarias.
# Plan applies SET LOCAL and EXPLAIN in the same transaction/psql session.
# El termino selectivo es el que delata un OR reintroducido: con ramas, cada
# una entra por su trigrama; con OR, la tabla grande cae a Seq Scan.
$planOp = Plan @"
select count(*) from (
  select o.id_oportunidad as id from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
   where o.organizacion_id = $($fixtureE2E.organizacion) and lower(o.codigo_oportunidad) like lower('%HITOOP%')
  union
  select o.id_oportunidad from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
   where o.organizacion_id = $($fixtureE2E.organizacion) and lower(cap.codigo_captacion) like lower('%HITOOP%')
  union
  select o.id_oportunidad from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
    join propiedad prop on prop.id_propiedad = cap.id_propiedad
   where o.organizacion_id = $($fixtureE2E.organizacion) and lower(prop.direccion) like lower('%HITOOP%')
  union
  select o.id_oportunidad from oportunidad_comercial o
    join captacion cap on cap.id_captacion = o.id_captacion
    join persona_rol prCli on prCli.id_persona_rol = o.id_rol_cliente
    join persona perCli on perCli.id_persona = prCli.id_persona
   where o.organizacion_id = $($fixtureE2E.organizacion) and lower(perCli.nombres_o_razon_social) like lower('%HITOOP%')
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

# Plan de la consulta REAL del nuevo lector, no del UNION de entidades legado.
# Todos los IDs del bulkfixture comparten el contexto autorizado de la plantilla;
# el permiso y el conteo se contrastan ademas por HTTP sobre este mismo banco.
$lecturasRuta = Join-Path $PSScriptRoot '..\controllocal-persistence\src\main\java\com\controllocal\persistence\repositorio\LecturasInteraccionRepository.java'
$lecturasFuente = Get-Content -LiteralPath $lecturasRuta -Raw
$fragmento = [regex]::Match($lecturasFuente, '(?s)private\s+static\s+final\s+String\s+BUSQUEDA\s*=\s*"""(.*?)"""\s*;')
if (-not $fragmento.Success) { throw 'No se localizo BUSQUEDA del lector vigente para medir su plan real.' }
# El lector enlaza q.toLowerCase(Locale.ROOT), no el termino original.
$patronPlan = '%' + 'HITOOBS'.ToLowerInvariant() + '%'
$idsPermitidos = Invoke-ControlLocalE2ESql "select string_agg(id_interaccion::text,',' order by id_interaccion) from interaccion_comercial where organizacion_id=$($fixtureE2E.organizacion) and (contexto_declarado_json::jsonb#>>'{datos,resumen}') like 'Contacto de carga%'"
if ($idsPermitidos -notmatch '^[0-9]+(,[0-9]+)*$') { throw 'El plan requiere los IDs positivos exactos del banco autorizado.' }
$enlacesPlan = [ordered]@{
    org = [string]([long]$fixtureE2E.organizacion); ids = "ARRAY[$idsPermitidos]::bigint[]"
    tipo = 'NULL'; resultado = 'NULL'; canal = 'NULL'; texto = ("'" + $patronPlan + "'")
    limite = '10'; offset = '0'
}
$consultaPlan = $fragmento.Groups[1].Value
foreach ($nombreEnlace in $enlacesPlan.Keys) {
    $consultaPlan = [regex]::Replace($consultaPlan, ('(?<!:):' + [regex]::Escape($nombreEnlace) + '\b'), $enlacesPlan[$nombreEnlace])
}
if ($consultaPlan -match '(?<!:):[a-zA-Z][a-zA-Z0-9_]*') { throw 'BUSQUEDA contiene un parametro que el fixture no ha enlazado.' }
# BUSQUEDA devuelve pagina y total; contar sus filas daria10, no los20 testigos.
$consultaConteoPlan = 'select min(total) from (' + $consultaPlan + ') resultado_plan'
$cuentaPlan = [long](Invoke-ControlLocalE2ESql $consultaConteoPlan)
Check "el patron del plan de interacciones encuentra los $testigos testigos antes de EXPLAIN" `
    ($cuentaPlan -eq $testigos) "count=$cuentaPlan esperado=$testigos"
$planIn = Plan $consultaPlan
Write-Host $planIn
Check 'plan de interacciones: usa acceso indexado en el lector vigente' `
    ($planIn -match 'ix_interaccion_texto_busqueda_trgm' -and $planIn -match 'Index Scan|Index Only Scan|Bitmap Index Scan') 'sin acceso GIN del texto vigente'
Check 'plan de interacciones: no recorre entera la tabla grande' `
    ($planIn -notmatch 'Seq Scan on interaccion_comercial') 'Seq Scan de interaccion_comercial'
# Contraste explicito: el OR cruzado que la seccion 5 prohibe, medido sobre el MISMO
# banco. Se ejecuta solo para dejar el numero en el informe.
$planOr = Plan @"
select count(*) from oportunidad_comercial o
  join captacion cap on cap.id_captacion = o.id_captacion
  join propiedad prop on prop.id_propiedad = cap.id_propiedad
  join persona_rol prCli on prCli.id_persona_rol = o.id_rol_cliente
  join persona perCli on perCli.id_persona = prCli.id_persona
 where o.organizacion_id = $($fixtureE2E.organizacion)
   and (lower(o.codigo_oportunidad) like lower('%HITOOP%')
        or lower(cap.codigo_captacion) like lower('%HITOOP%')
        or lower(prop.direccion) like lower('%HITOOP%')
        or lower(perCli.nombres_o_razon_social) like lower('%HITOOP%'))
"@
Write-Host "`n-- contraste: el OR cruzado sobre el mismo banco --"
Write-Host $planOr
Check 'el OR cruzado SI cae a Seq Scan (justifica la reescritura)' `
    ($planOr -match 'Seq Scan') 'el contraste no reprodujo el Seq Scan'

$script:FuenteFixtureE2E = 'e2e-demanda-busqueda.ps1'
$script:EtapaFixtureE2E = 'DEMANDA_BUSQUEDA_FASE_12'
Write-Host "`n== 12. Guarda estatica: el listado ya no recibe el texto ==" -ForegroundColor Cyan
# Si alguien devuelve el texto al metodo JPQL de listado, vuelve el OR cruzado
# sin que ninguna medida lo note (los tiempos solo empeoran con volumen real).
$repos = Join-Path $PSScriptRoot '..\controllocal-persistence\src\main\java\com\controllocal\persistence\repositorio'
foreach ($r in @(
    @{ Archivo = 'OportunidadComercialRepository.java'; Metodo = 'Page<OportunidadComercial> buscar' },
    @{ Archivo = 'VisitaRepository.java';               Metodo = 'Page<Visita> buscar' })) {
    $texto = Get-Content -LiteralPath (Join-Path $repos $r.Archivo) -Raw
    $firma = [regex]::Match($texto, [regex]::Escape($r.Metodo) + '\((?s).*?Pageable pageable\);')
    Check "$($r.Archivo): el listado JPQL no recibe texto libre" `
        ($firma.Success -and $firma.Value -notmatch '@Param\("(query|q|texto)"\)') 'la firma volvio a llevar el texto'
}

# El listado C4 autoriza antes del texto y del conteo. No se exige resucitar
# un Page<Entidad> eliminado ni se introduce texto privado en la cabecera JPQL.
$servicioC4 = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\controllocal-service\src\main\java\com\controllocal\service\impl\InteraccionServiceImpl.java') -Raw
Check 'el buscador C4 recibe la poblacion permitida por Core' `
    ($servicioC4 -match 'var p=poblacion\(actor\)' -and $servicioC4 -match 'lecturas.buscar\(actor.idOrganizacion\(\),ids,') 'poblacion autorizada'
Check 'el texto C4 busca unicamente dentro de las identidades permitidas' `
    ($fragmento.Groups[1].Value -match 'i\.organizacion_id\s*=\s*cast\(:org\s+AS\s+bigint\)' `
        -and $fragmento.Groups[1].Value -match 'i\.id_interaccion\s*=\s*ANY\(cast\(:ids\s+AS\s+bigint\[\]\)\)' `
        -and $lecturasFuente -match '\.addValue\("ids",array\(permitidas\)\)' `
        -and $fragmento.Value -notmatch 'join\s+(?:public\.)?(captacion|prospeccion|persona)\b') 'lector C4 acotado por organizacion e IDs nativos permitidos'
} finally {
    # La limpieza NO es borrar filas: es tirar la base entera.
    #
    # Aqui habia nueve `delete ... like` sin indice, en una sola transaccion,
    # sobre un banco de 100.000 filas por tabla. Medido en la corrida de firma
    # del 2026-08-03 (`20260803093503-7523`): **1.014 s - 16 min 54 s- de los
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
    $baseActual = (Invoke-ControlLocalE2ESql 'select current_database()').Trim()
    Check 'el banco vive en la base efimera de la corrida (se elimina entera)' `
        ($baseActual -eq $e2e.Database -and $baseActual -like 'controllocal_e2e_*') `
        "la suite apunta a '$baseActual', que no es la base efimera de la corrida"
}

Write-Host "`n===== $ok OK / $fail FALLAS =====" `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
