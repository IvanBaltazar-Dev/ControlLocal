# =====================================================================
# E2E del LISTADO UNIVERSAL: GET /propiedades por el cable.
#
# POR QUE EXISTE. `GET /locales` tiene dos suites -`locales-listado` y
# `locales-busqueda`- y `GET /propiedades` no tenia ninguna: ni una sola
# llamada con parametros de consulta en toda la carpeta (medido el 2026-09-02
# sobre las 23 suites que ya existian, con `/locales?` como control positivo
# del barrido). Lo unico que lo cubria eran pruebas de INTEGRACION que
# llaman al SERVICIO -`PropiedadUniversalIntegrationTest`-, asi que el tramo
# controlador-Jackson-cable no lo recorria nadie: ni los alias `page`/`pagina`,
# ni el tope de 100, ni el sobre `PageResponse`, ni que la fila que viaja sea
# `FilaPropiedad` y no `LocalListado`.
#
# Y no es el mismo listado con otro nombre. `/locales` publica UNA fila por
# local con `precio_referencial` dentro y sin decir de que operacion es;
# `/propiedades` publica una fila por propiedad -de los SIETE tipos- con SUS
# encargos vivos dentro, que pueden ser dos y no se pueden sumar ni comparar.
# Probar uno no prueba el otro.
#
# LO QUE FIJA:
#   1. La proyeccion es `FilaPropiedad`: lleva `tipoPropiedad` + `tipoRotulo` +
#      `encargos[]`, y NO lleva ninguno de los campos que solo existen en
#      `LocalListado` (`precioReferencial`, `rubroPermitido`, `codigoLocal`...).
#   2. Los siete tipos entran, con su nombre de valor y su rotulo.
#   3. Los cuatro casos de encargos: cero, venta, alquiler y ambos -y con
#      ambos, VENTA antes que ALQUILER-. Un encargo CERRADO no aparece.
#   4. `operaciones=VENTA,ALQUILER` es "tiene las dos", no "tiene alguna".
#   5. El texto busca codigo, direccion, distrito, propietario Y RUBRO: las
#      cinco ramas son del motor comun, ninguna es de un recurso. Con su control
#      negativo, porque el rubro solo APLICA a ALMACEN, LOCAL y OFICINA.
#   6. El orden universal es `id DESC`.
#   7. El tamano por defecto es 20 y el maximo 100, recortado en silencio.
#   8. La frontera de tenant: la cartera del vecino no se ve ni se cuenta, y la
#      base rechaza un encargo que cruce la frontera.
#
# LO QUE NO TOCA: `/locales`, sus dos suites, ni la corrida de cierre.
#
# ASCII puro y sin BOM: PS 5.1 lee un .ps1 sin BOM como ANSI y un solo caracter
# acentuado -aunque este en un comentario- rompe el parseo entero.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite propiedades-listado
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0
$fail = 0

function Check($nombre, $condicion, $detalle) {
    if ($condicion) {
        $script:ok++
        Write-Host "  OK   $nombre" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red
    }
}

function ParametrosApi($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $p = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 60 }
    if ($null -ne $cuerpo) {
        $p['Body'] = ($cuerpo | ConvertTo-Json -Depth 8)
        $p['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) {
        $p['ContentType'] = 'application/json'
    }
    return $p
}

# El error que sube lleva el CUERPO de la respuesta: sin el, un 400 se lee como
# "WebException" y hay que repetir la corrida entera para saber que dijo el Core.
function Api($metodo, $ruta, $token, $cuerpo) {
    $parametros = ParametrosApi $metodo $ruta $token $cuerpo
    try {
        Invoke-RestMethod @parametros
    } catch {
        $respuesta = $PSItem.Exception.Response
        $detalle = $PSItem.Exception.Message
        if ($null -ne $respuesta) {
            try {
                $lector = New-Object IO.StreamReader($respuesta.GetResponseStream())
                $detalle = "HTTP $([int]$respuesta.StatusCode): $($lector.ReadToEnd())"
                $lector.Close()
            } catch { }
        }
        throw "$metodo $ruta -> $detalle"
    }
}

# Para lo que DEBE fallar: devuelve el codigo y el mensaje en vez de reventar.
function ApiError($metodo, $ruta, $token, $cuerpo) {
    $parametros = ParametrosApi $metodo $ruta $token $cuerpo
    try {
        Invoke-RestMethod @parametros | Out-Null
        return @{ codigo = 0; error = '(la llamada no fallo)' }
    } catch {
        $respuesta = $PSItem.Exception.Response
        if ($null -eq $respuesta) { return @{ codigo = -1; error = $PSItem.Exception.Message } }
        $codigo = [int]$respuesta.StatusCode
        $contenido = ''
        try {
            $lector = New-Object IO.StreamReader($respuesta.GetResponseStream())
            $contenido = $lector.ReadToEnd()
            $lector.Close()
        } catch { $contenido = '' }
        $mensaje = $contenido
        try { $mensaje = ($contenido | ConvertFrom-Json).error } catch { }
        return @{ codigo = $codigo; error = $mensaje }
    }
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

# Un SQL que se espera que la BASE rechace. `docker exec` escribe el error en
# stderr y PS 5.1 lo convierte en ErrorRecord: con $ErrorActionPreference='Stop'
# eso aborta el guion aunque el rechazo sea justo lo que se busca.
function SqlDebeFallar($consulta) {
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database `
            -v ON_ERROR_STOP=1 -t -A -c $consulta 2>&1 | ForEach-Object { $_.ToString() }) -join ' '
        return [pscustomobject]@{ Codigo = $LASTEXITCODE; Salida = $salida }
    } finally { $ErrorActionPreference = $previo }
}

function Listar($token, $consulta) { Api GET "/propiedades$consulta" $token $null }
# SIN la coma de envoltura. `,@(...)` parece la forma prudente de devolver un
# array de un elemento, pero lo que hace es devolver un array DENTRO de otro: al
# usarlo directamente en una tuberia -`@(IdsDe $p) | Sort-Object`- el procesador
# desenrolla una capa y `Sort-Object` recibe UN objeto, asi que ordena una lista
# de uno y la devuelve intacta. Medido: con la coma, `(@(IdsDe $p) | Sort-Object)
# -join ','` sobre 9 y 3 devuelve "9,3"; sin ella, "3,9". Los consumidores
# envuelven con `@()`, que ya garantiza el array.
function IdsDe($pagina) { @(@($pagina.items) | ForEach-Object { [long]$_.id }) }
function Fila($pagina, $codigo) { @(@($pagina.items) | Where-Object { $_.codigo -eq $codigo })[0] }
function Texto($valor) { [uri]::EscapeDataString($valor) }

# ---------------------------------------------------------------------
# El alta sale del CONTRATO, no de una tabla "tipo -> campos" escrita aqui: se
# pide `GET /captura/definicion` para cada tipo y se rellena lo que el Core
# declara, por control. Si el catalogo crece, el fixture crece con el. Es el
# mismo criterio que usa `editor-universal`, y por la misma razon.
# ---------------------------------------------------------------------
$camposUbicacion = @('direccion', 'distrito', 'zonaUrbanizacion', 'latitud', 'longitud',
                     'interiorUnidad', 'piso', 'referenciaInterna', 'nombreEdificioGaleria')
function EsDeUbicacion($p) { $camposUbicacion -contains $p.clave }
function EsEditable($p) { $p.control -notin @('IMPORTE', 'SELECTOR_MULTIPLE', 'TITULARES') }

function ValorPara($p, $semilla) {
    $r = $p.restricciones
    switch ($p.control) {
        'SELECTOR'    { return "$(@($p.opciones)[0].valor)" }
        'INTERRUPTOR' { return 'true' }
        'FECHA'       { return (Get-Date).ToString('yyyy-MM-dd') }
        'ENTERO'      {
            $v = 1 + $semilla
            if ($r -and $null -ne $r.minimo) { $v = [int]$r.minimo + $semilla }
            if ($r -and $null -ne $r.maximo -and $v -gt [int]$r.maximo) { $v = [int]$r.maximo }
            return "$v"
        }
        'DECIMAL'     {
            $v = 10.5 + $semilla
            if ($r -and $null -ne $r.minimo) { $v = [double]$r.minimo + 10 + $semilla }
            if ($r -and $null -ne $r.maximo -and $v -gt [double]$r.maximo) { $v = [double]$r.maximo }
            return "$v"
        }
        'MONEDA'      { return "$(100 + $semilla)" }
        default       {
            $t = "Valor $semilla"
            if ($r -and $r.longitudMaxima) { $t = $t.Substring(0, [Math]::Min($t.Length, [int]$r.longitudMaxima)) }
            return $t
        }
    }
}

function AltaPropiedad($token, $tipo, $codigo, $direccion, $distrito, $idPropietario, $operaciones) {
    # La definicion no depende de que operaciones se vayan a abrir: `delTipo`
    # describe la COSA. Se pide con las dos para no dejar fuera una pregunta.
    $def = Api GET ("/captura/definicion?intencion=REGISTRAR_PROPIEDAD&tipoPropiedad=$tipo" +
                    "&operaciones=VENTA,ALQUILER") $token $null
    $delTipo = @(@($def.delTipo) | Where-Object { $_ -and (EsEditable $_) })
    $atributos = @()
    $ubicacion = @{ direccion = $direccion; distrito = $distrito }
    $semilla = 0
    foreach ($p in $delTipo) {
        $semilla++
        if (EsDeUbicacion $p) {
            if (-not $ubicacion.ContainsKey($p.clave)) { $ubicacion[$p.clave] = (ValorPara $p $semilla) }
        } else {
            $atributos += @{ clave = $p.clave; valor = (ValorPara $p $semilla) }
        }
    }
    $bloques = @()
    foreach ($operacion in @($operaciones)) {
        if (-not $operacion) { continue }
        $bloques += @{
            operacion    = $operacion
            importe      = $(if ($operacion -eq 'VENTA') { 250000 } else { 3500 })
            moneda       = $(if ($operacion -eq 'VENTA') { 'USD' } else { 'PEN' })
            exclusividad = $true
        }
    }
    $alta = Api POST '/propiedades' $token @{
        codigo        = $codigo
        tipoPropiedad = $tipo
        descripcion   = "Fixture del listado universal $codigo"
        ubicacion     = $ubicacion
        titulares     = @(@{ idPropietario = $idPropietario; representante = $true })
        atributos     = $atributos
        operaciones   = $bloques
    }
    return [pscustomobject]@{ id = [long]$alta.idPropiedad; codigo = $alta.codigo; tipo = $tipo }
}

# =====================================================================
$marca = "PRL$(Get-Random -Minimum 100000 -Maximum 999999)"
$distritoFixture = "Distrito $marca"
$rubroTestigo = "Rubro $marca"

Write-Host "`n== 1. Contexto efimero y actores ==" -ForegroundColor Cyan
Check 'la base lleva identificador exclusivo de corrida' `
    ($e2e.Database -match '^controllocal_e2e_' -and $e2e.Database -ne 'controllocal') $e2e.Database
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
$token = $agente.token

$dueno = Api POST '/propietarios' $token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "71$($marca.Substring(3))"
    nombre = "Titular $marca"; telefono = '987630001'
    correo = "titular.$marca@test.local"; consentimientoUsoDato = $true; estado = 'A'
}
Check 'el propietario del fixture existe' ($dueno.id -gt 0) "id=$($dueno.id)"

# ---------------------------------------------------------------------
Write-Host "`n== 2. Los siete tipos, con sus cuatro casos de encargo ==" -ForegroundColor Cyan
# Reparto deliberado: los cuatro casos de encargo caen sobre tipos distintos,
# de modo que ninguna comprobacion pueda pasar por casualidad del tipo LOCAL,
# que es el unico que existia antes del modelo universal.
$plan = @(
    @{ tipo = 'LOCAL';        rotulo = '^Local comercial$'; ops = @('VENTA', 'ALQUILER') },
    @{ tipo = 'OFICINA';      rotulo = '^Oficina$';         ops = @('VENTA') },
    @{ tipo = 'DEPARTAMENTO'; rotulo = '^Departamento$';    ops = @('ALQUILER') },
    @{ tipo = 'CASA';         rotulo = '^Casa$';            ops = @() },
    @{ tipo = 'TERRENO';      rotulo = '^Terreno$';         ops = @('VENTA') },
    # El rotulo de ALMACEN es el unico de los siete que lleva tilde, y eso lo
    # convierte en el unico que este guion no puede afirmar letra a letra: el
    # fichero es ASCII puro, y ademas `Invoke-RestMethod` de PS 5.1 decodifica
    # el cuerpo como Latin-1 cuando el `Content-Type` no declara `charset` -y el
    # de este API es `application/json` a secas, comprobado el 2026-09-02-, asi
    # que los dos bytes UTF-8 de la vocal acentuada llegan como DOS caracteres.
    # No es un defecto del producto -JSON es UTF-8 por especificacion y el SPA
    # lo decodifica bien-, es una limitacion de ESTE cliente: por eso el patron
    # admite una o dos posiciones y sigue exigiendo que el rotulo exista,
    # empiece por Almac y acabe en n.
    @{ tipo = 'ALMACEN';      rotulo = '^Almac.{1,2}n$';    ops = @('ALQUILER') },
    @{ tipo = 'OTRO';         rotulo = '^Otro$';            ops = @('VENTA', 'ALQUILER') }
)

$creadas = @{}
$indice = 0
foreach ($caso in $plan) {
    $indice++
    $codigo = "$marca-T$indice"
    $creadas[$caso.tipo] = AltaPropiedad $token $caso.tipo $codigo `
        "Avenida $marca numero $indice" $distritoFixture $dueno.id $caso.ops
}
Check 'los siete tipos se dieron de alta' (@($creadas.Keys).Count -eq 7) "$(@($creadas.Keys).Count) tipos"

# El rubro va a los TRES tipos que lo admiten -ALMACEN, LOCAL y OFICINA-, que
# es exactamente donde `exigir_atributo_gobernado` lo deja escribir. No es un
# detalle del fixture: `rubro_permitido` es atributo gobernado de PROPIEDAD
# desde V71, asi que su rama de busqueda es canonica y los DOS listados tienen
# que encontrarlo. Los otros cuatro tipos son el control negativo.
foreach ($conRubro in 'ALMACEN', 'LOCAL', 'OFICINA') {
    Sql ("insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto) " +
         "values (1, $($creadas[$conRubro].id), 'rubro_permitido', '$rubroTestigo') " +
         "on conflict (id_propiedad, clave) do update set valor_texto = excluded.valor_texto") | Out-Null
}

$universo = Listar $token "?texto=$(Texto $marca)&page_size=100"
Check 'el fixture completo se alcanza con una sola busqueda' `
    ($universo.totalRecords -eq 7) "totalRecords=$($universo.totalRecords)"

# ---------------------------------------------------------------------
Write-Host "`n== 3. La proyeccion es FilaPropiedad, no LocalListado ==" -ForegroundColor Cyan
$fila = Fila $universo $creadas['LOCAL'].codigo
$campos = @($fila.PSObject.Properties.Name)
foreach ($c in 'id', 'codigo', 'tipoPropiedad', 'tipoRotulo', 'uso', 'direccion', 'distrito',
                'metraje', 'estado', 'titulares', 'encargos', 'fechaRegistro') {
    Check "la fila publica '$c'" ($campos -contains $c) ($campos -join ',')
}
# Lo que NO puede viajar: son los campos de `LocalListado`, la proyeccion del
# listado heredado. Que aparecieran significaria que el recurso universal se
# esta sirviendo con la fila vieja -un precio suelto sin operacion-, que es
# exactamente lo que D-E4-1 vino a quitar.
foreach ($c in 'precioReferencial', 'monedaReferencial', 'rubroPermitido', 'codigoLocal',
                'tipoInmueble', 'descripcion', 'zonaUrbanizacion', 'geoLat', 'geoLong', 'idDistrito') {
    Check "la fila NO arrastra '$c' de LocalListado" (-not ($campos -contains $c)) ($campos -join ',')
}
Check 'titulares viaja como numero' ([int]$fila.titulares -eq 1) "titulares=$($fila.titulares)"
Check 'uso viaja con vocabulario legal' ("$($fila.uso)" -match '^[CVIM]$') "uso=$($fila.uso)"
Check 'el estado de la propiedad es D, N o I' ("$($fila.estado)" -match '^[DNI]$') "estado=$($fila.estado)"
Check 'la fila trae el propietario que la representa' `
    ("$($fila.propietarioNombre)" -eq "Titular $marca") "$($fila.propietarioNombre)"
# `idPropietario` es el id del ROL, no el de la persona: es lo que apunta
# `propiedad.id_rol_propietario`, y es lo que devuelve `POST /propietarios`.
# El relleno de la seccion 10 se apoya en esa igualdad para su FK.
Check 'idPropietario es el id del rol que devolvio el alta' `
    ([long]$fila.idPropietario -eq [long]$dueno.id) "$($fila.idPropietario) <> $($dueno.id)"

foreach ($caso in $plan) {
    $f = Fila $universo $creadas[$caso.tipo].codigo
    Check "$($caso.tipo) viaja con su nombre de valor" ($f.tipoPropiedad -eq $caso.tipo) "$($f.tipoPropiedad)"
    Check "$($caso.tipo) viaja con su rotulo ya escrito" ("$($f.tipoRotulo)" -match $caso.rotulo) "$($f.tipoRotulo)"
}

# ---------------------------------------------------------------------
Write-Host "`n== 4. Los encargos de cada fila ==" -ForegroundColor Cyan
$ambos = Fila $universo $creadas['LOCAL'].codigo
$soloVenta = Fila $universo $creadas['OFICINA'].codigo
$soloAlquiler = Fila $universo $creadas['DEPARTAMENTO'].codigo
$sinEncargo = Fila $universo $creadas['CASA'].codigo

Check 'una propiedad con las dos operaciones trae DOS encargos, no dos filas' `
    (@($ambos.encargos).Count -eq 2) "$(@($ambos.encargos).Count)"
Check 'y VENTA precede a ALQUILER dentro de la fila' `
    ((@($ambos.encargos)[0].operacion -eq 'VENTA') -and (@($ambos.encargos)[1].operacion -eq 'ALQUILER')) `
    ((@($ambos.encargos) | ForEach-Object { $_.operacion }) -join ',')
Check 'cada encargo lleva SU importe y SU moneda' `
    (([double](@($ambos.encargos)[0].importe) -eq 250000) -and (@($ambos.encargos)[0].moneda -eq 'USD') -and
     ([double](@($ambos.encargos)[1].importe) -eq 3500) -and (@($ambos.encargos)[1].moneda -eq 'PEN')) `
    ((@($ambos.encargos) | ForEach-Object { "$($_.operacion) $($_.moneda) $($_.importe)" }) -join ' | ')
Check 'el estado del encargo viaja como codigo vivo' `
    ((@($ambos.encargos) | Where-Object { "$($_.estado)" -match '^[POA]$' }).Count -eq 2) `
    ((@($ambos.encargos) | ForEach-Object { $_.estado }) -join ',')
Check 'solo venta trae un encargo de VENTA' `
    ((@($soloVenta.encargos).Count -eq 1) -and (@($soloVenta.encargos)[0].operacion -eq 'VENTA')) `
    "$(@($soloVenta.encargos).Count)"
Check 'solo alquiler trae un encargo de ALQUILER' `
    ((@($soloAlquiler.encargos).Count -eq 1) -and (@($soloAlquiler.encargos)[0].operacion -eq 'ALQUILER')) `
    "$(@($soloAlquiler.encargos).Count)"
# `encargos: []` SI viaja: Jackson NON_NULL omite nulos, no colecciones vacias.
# Que el campo desapareciera obligaria al cliente a distinguir "sin encargos"
# de "no me lo mandaron", que son cosas distintas.
Check 'una propiedad sin encargar trae la lista VACIA, no ausente' `
    ((@($sinEncargo.PSObject.Properties.Name) -contains 'encargos') -and (@($sinEncargo.encargos).Count -eq 0)) `
    "$(@($sinEncargo.encargos).Count)"

# ---------------------------------------------------------------------
Write-Host "`n== 5. El filtro por operacion es EXISTS, no una columna ==" -ForegroundColor Cyan
$conVenta = Listar $token "?texto=$(Texto $marca)&page_size=100&operaciones=VENTA"
$conAlquiler = Listar $token "?texto=$(Texto $marca)&page_size=100&operaciones=ALQUILER"
$conLasDos = Listar $token "?texto=$(Texto $marca)&page_size=100&operaciones=VENTA,ALQUILER"
Check 'operaciones=VENTA trae las cuatro que tienen venta viva' `
    ($conVenta.totalRecords -eq 4) "total=$($conVenta.totalRecords)"
Check 'operaciones=ALQUILER trae las cuatro que tienen alquiler vivo' `
    ($conAlquiler.totalRecords -eq 4) "total=$($conAlquiler.totalRecords)"
Check 'operaciones=VENTA,ALQUILER significa TIENE LAS DOS, no tiene alguna' `
    ($conLasDos.totalRecords -eq 2) "total=$($conLasDos.totalRecords)"
$salenLasDos = (@(IdsDe $conLasDos) | Sort-Object) -join ','
$esperadasLasDos = (@($creadas['LOCAL'].id, $creadas['OTRO'].id) | Sort-Object) -join ','
Check 'y las dos que salen son justo LOCAL y OTRO' `
    ($salenLasDos -eq $esperadasLasDos) "$salenLasDos <> $esperadasLasDos"
$porCodigo = Listar $token "?texto=$(Texto $marca)&page_size=100&operaciones=V,A"
Check 'el filtro admite tambien los codigos de una letra' `
    ($porCodigo.totalRecords -eq 2) "total=$($porCodigo.totalRecords)"

foreach ($invalido in 'AMBAS', 'VENTA,VENTA', 'COMPRA', ',') {
    $r = ApiError GET "/propiedades?operaciones=$(Texto $invalido)" $token $null
    Check "operaciones='$invalido' se rechaza con 400" ($r.codigo -eq 400) "codigo=$($r.codigo) $($r.error)"
}

# ---------------------------------------------------------------------
Write-Host "`n== 6. Un encargo CERRADO deja de contar ==" -ForegroundColor Cyan
# Se cierra por SQL a proposito: cerrar por el cable es otra puerta y otra
# suite. Lo que aqui se prueba es la definicion de VIVO del listado -estado en
# (P,O,A)-, no el camino que lleva a cerrarlo.
Sql ("update captacion set estado = 'C', fecha_cierre = current_date, motivo_cierre = 'M' " +
     "where organizacion_id = 1 and id_propiedad = $($creadas['OTRO'].id) and motivo_operacion = 'V'") | Out-Null
$tras = Listar $token "?texto=$(Texto $creadas['OTRO'].codigo)&page_size=10"
$filaOtro = Fila $tras $creadas['OTRO'].codigo
Check 'el encargo cerrado desaparece de la fila' `
    ((@($filaOtro.encargos).Count -eq 1) -and (@($filaOtro.encargos)[0].operacion -eq 'ALQUILER')) `
    ((@($filaOtro.encargos) | ForEach-Object { $_.operacion }) -join ',')
$trasDos = Listar $token "?texto=$(Texto $marca)&page_size=100&operaciones=VENTA,ALQUILER"
Check 'y la propiedad deja de tener "las dos vivas"' `
    (($trasDos.totalRecords -eq 1) -and (@(IdsDe $trasDos)[0] -eq $creadas['LOCAL'].id)) `
    "total=$($trasDos.totalRecords)"
$trasVenta = Listar $token "?texto=$(Texto $marca)&page_size=100&operaciones=VENTA"
Check 'un encargo cerrado no mantiene la propiedad "en venta"' `
    ($trasVenta.totalRecords -eq 3) "total=$($trasVenta.totalRecords)"

# ---------------------------------------------------------------------
Write-Host "`n== 7. El filtro por tipo ==" -ForegroundColor Cyan
foreach ($caso in $plan) {
    $porTipo = Listar $token "?texto=$(Texto $marca)&page_size=100&tipoPropiedad=$($caso.tipo)"
    Check "tipoPropiedad=$($caso.tipo) trae exactamente la suya" `
        (($porTipo.totalRecords -eq 1) -and (@(IdsDe $porTipo)[0] -eq $creadas[$caso.tipo].id)) `
        "total=$($porTipo.totalRecords)"
}
$porLetra = Listar $token "?texto=$(Texto $marca)&page_size=100&tipoPropiedad=T"
Check 'el tipo admite tambien el codigo de una letra' `
    (($porLetra.totalRecords -eq 1) -and (@(IdsDe $porLetra)[0] -eq $creadas['TERRENO'].id)) `
    "total=$($porLetra.totalRecords)"
$r = ApiError GET "/propiedades?tipoPropiedad=CHALET" $token $null
Check 'un tipo desconocido se rechaza con 400' ($r.codigo -eq 400) "codigo=$($r.codigo) $($r.error)"
$r = ApiError GET "/propiedades?tipoPropiedad=$(Texto 'LOCAL,CASA')" $token $null
Check 'el tipo NO es una lista: dos tipos separados por coma es 400' `
    ($r.codigo -eq 400) "codigo=$($r.codigo) $($r.error)"

# ---------------------------------------------------------------------
Write-Host "`n== 8. El texto mira cuatro campos, y el rubro no es uno ==" -ForegroundColor Cyan
$porCodigoTexto = Listar $token "?texto=$(Texto $creadas['CASA'].codigo)&page_size=10"
Check 'el texto casa por CODIGO' `
    (($porCodigoTexto.totalRecords -eq 1) -and (@(IdsDe $porCodigoTexto)[0] -eq $creadas['CASA'].id)) `
    "total=$($porCodigoTexto.totalRecords)"
$porDireccion = Listar $token "?texto=$(Texto "Avenida $marca numero 4")&page_size=10"
Check 'el texto casa por DIRECCION' `
    (($porDireccion.totalRecords -eq 1) -and (@(IdsDe $porDireccion)[0] -eq $creadas['CASA'].id)) `
    "total=$($porDireccion.totalRecords)"
$porDistrito = Listar $token "?texto=$(Texto $distritoFixture)&page_size=100"
Check 'el texto casa por DISTRITO' ($porDistrito.totalRecords -eq 7) "total=$($porDistrito.totalRecords)"
$porPropietario = Listar $token "?texto=$(Texto "Titular $marca")&page_size=100"
Check 'el texto casa por PROPIETARIO' ($porPropietario.totalRecords -eq 7) "total=$($porPropietario.totalRecords)"
# EL RUBRO ES RAMA CANONICA, no una particularidad de `/locales` (C0-a).
# `rubro_permitido` vive en `atributo_propiedad` como atributo gobernado de
# PROPIEDAD desde V71: buscarlo es buscar la cartera, y los DOS listados tienen
# que encontrar lo mismo. Hubo una version de este corte en que el universal
# salia sin esa rama; el efecto era que un almacen buscado por su rubro quedaba
# invisible justo en el listado que el producto usa de verdad.
$porRubro = Listar $token "?texto=$(Texto $rubroTestigo)&page_size=100"
Check 'el texto del listado universal SI mira el rubro' `
    ($porRubro.totalRecords -eq 3) "total=$($porRubro.totalRecords)"
Check 'y trae los TRES tipos a los que el rubro aplica (A, L, O)' `
    (((@($porRubro.items | ForEach-Object { $_.tipoPropiedad }) | Sort-Object) -join ',') -eq 'ALMACEN,LOCAL,OFICINA') `
    ((@($porRubro.items | ForEach-Object { $_.tipoPropiedad }) | Sort-Object) -join ',')
$porRubroEnLocales = Api GET "/locales?page=1&tamano=10&texto=$(Texto $rubroTestigo)" $token $null
Check 'los dos listados encuentran EXACTAMENTE lo mismo por rubro' `
    ($porRubroEnLocales.totalRecords -eq $porRubro.totalRecords) `
    "locales=$($porRubroEnLocales.totalRecords) universal=$($porRubro.totalRecords)"
# Control negativo. Que la rama sea canonica no puede significar que una
# propiedad a la que el rubro NO le aplica gane un dato ni aparezca por ella.
# Se comprueba por los dos lados: no sale en la busqueda, y la BASE rechaza
# escribirle un rubro -que es lo que garantiza que el caso no exista.
foreach ($sinRubro in 'CASA', 'DEPARTAMENTO', 'TERRENO', 'OTRO') {
    $r = Listar $token "?texto=$(Texto $rubroTestigo)&page_size=100&tipoPropiedad=$sinRubro"
    Check "$sinRubro no aparece por una rama que no le aplica" `
        ($r.totalRecords -eq 0) "total=$($r.totalRecords)"
}
$rechazo = SqlDebeFallar ("insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto) " +
    "values (1, $($creadas['TERRENO'].id), 'rubro_permitido', '$rubroTestigo')")
Check 'la base rechaza un rubro sobre un tipo que no lo admite' `
    ($rechazo.Codigo -ne 0) "codigo=$($rechazo.Codigo) $($rechazo.Salida)"
$sinDato = [int](Sql "select count(*) from atributo_propiedad where id_propiedad = $($creadas['TERRENO'].id) and clave = 'rubro_permitido'")
Check 'y el terreno sigue sin ese dato: nada se invento' ($sinDato -eq 0) "filas=$sinDato"
$mayusculas = Listar $token "?texto=$(Texto ($marca.ToLower()))&page_size=100"
Check 'el texto no distingue mayusculas' ($mayusculas.totalRecords -eq 7) "total=$($mayusculas.totalRecords)"
# Cruce: la misma propiedad casa por codigo y por direccion a la vez. Una fila,
# no dos -el listado universal filtra con un OR sobre una sola consulta, no con
# el UNION de ramas que usa `/locales`, pero el invariante que importa al que
# lee es el mismo-.
$cruce = Listar $token "?texto=$(Texto $marca)&page_size=100"
Check 'una fila que casa por varios campos aparece UNA vez' `
    ((@(IdsDe $cruce) | Sort-Object -Unique).Count -eq @(IdsDe $cruce).Count) `
    (@(IdsDe $cruce) -join ',')

# ---------------------------------------------------------------------
Write-Host "`n== 9. El orden universal es id DESC ==" -ForegroundColor Cyan
$ids = @(IdsDe $universo)
$descendente = @($ids | Sort-Object -Descending)
Check 'la pagina llega ordenada por id descendente' `
    (($ids -join ',') -eq ($descendente -join ',')) ($ids -join ',')
Check 'lo ultimo creado va primero' ($ids[0] -eq $creadas['OTRO'].id) "$($ids[0])"

# ---------------------------------------------------------------------
Write-Host "`n== 10. Paginacion: 20 por defecto, 100 de tope ==" -ForegroundColor Cyan
# Banco de relleno por SQL: 250 filas mas con la misma marca. Por API costaria
# 250 altas para probar un recorte de pagina, y lo que se prueba aqui no es el
# alta. Sin atributos: `exigir_atributo_gobernado` cobra 3-5 consultas por fila
# y ninguno de ellos participa en este listado.
$RELLENO = 250
Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, estado_registro,
                       disponibilidad_comercial, tipo_inmueble, uso,
                       id_rol_propietario, organizacion_id)
select '$marca-R' || lpad(g::text, 4, '0'),
       'Avenida $marca relleno ' || g,
       '$distritoFixture',
       50 + g,
       case when g % 25 = 0 then 'I' else 'A' end,
       case when g % 25 = 0 then 'T' when g % 3 = 0 then 'D' else 'A' end,
       (array['L','O','D','C','T','A','X'])[1 + g % 7],
       'C',
       $($dueno.id),
       1
from generate_series(1, $RELLENO) g
"@ | Out-Null
$total = 7 + $RELLENO
$completo = Listar $token "?texto=$(Texto $marca)&page_size=100"
Check "el universo del fixture son $total filas" ($completo.totalRecords -eq $total) "total=$($completo.totalRecords)"

$defecto = Listar $token "?texto=$(Texto $marca)"
Check 'sin tamano, la pagina es de 20' `
    (($defecto.pageSize -eq 20) -and (@($defecto.items).Count -eq 20)) `
    "pageSize=$($defecto.pageSize) items=$(@($defecto.items).Count)"
Check 'y el total no depende del tamano de la pagina' ($defecto.totalRecords -eq $total) "$($defecto.totalRecords)"

$tope = Listar $token "?texto=$(Texto $marca)&page_size=1000"
Check 'un tamano por encima del tope se recorta a 100, sin error' `
    (($tope.pageSize -eq 100) -and (@($tope.items).Count -eq 100)) `
    "pageSize=$($tope.pageSize) items=$(@($tope.items).Count)"
$cero = Listar $token "?texto=$(Texto $marca)&page_size=0&page=0"
Check 'una pagina 0 y un tamano 0 se elevan a 1, sin error' `
    (($cero.page -eq 1) -and ($cero.pageSize -eq 1) -and (@($cero.items).Count -eq 1)) `
    "page=$($cero.page) pageSize=$($cero.pageSize) items=$(@($cero.items).Count)"

$alias = Listar $token "?texto=$(Texto $marca)&pagina=2&tamano=100"
$aliasIngles = Listar $token "?texto=$(Texto $marca)&page=2&page_size=100"
Check 'los alias castellanos y los ingleses dan la misma pagina' `
    ((@(IdsDe $alias) -join ',') -eq (@(IdsDe $aliasIngles) -join ',')) 'paginas distintas'

$vistos = @()
$paginas = [math]::Ceiling($total / 100)
for ($p = 1; $p -le $paginas; $p++) {
    $vistos += @(IdsDe (Listar $token "?texto=$(Texto $marca)&page=$p&page_size=100"))
}
Check 'recorriendo las paginas se ve el total exacto' (@($vistos).Count -eq $total) "$(@($vistos).Count)"
Check 'y ninguna fila aparece en dos paginas' `
    ((@($vistos) | Sort-Object -Unique).Count -eq $total) "$((@($vistos) | Sort-Object -Unique).Count)"
Check 'el recorrido completo sigue en orden descendente' `
    ((@($vistos) -join ',') -eq (@($vistos | Sort-Object -Descending) -join ',')) 'orden roto entre paginas'

# ---------------------------------------------------------------------
Write-Host "`n== 11. El filtro por estado y por distrito ==" -ForegroundColor Cyan
$inactivas = Listar $token "?texto=$(Texto $marca)&page_size=100&estado=I"
$esperadasI = [int]($RELLENO / 25)
Check "estado=I trae las $esperadasI inactivas del relleno" `
    ($inactivas.totalRecords -eq $esperadasI) "total=$($inactivas.totalRecords)"
Check 'y todas vienen marcadas como inactivas' `
    ((@($inactivas.items | Where-Object { $_.estado -ne 'I' })).Count -eq 0) 'alguna no es I'
$disponibles = Listar $token "?texto=$(Texto $marca)&page_size=100&estado=D"
Check 'estado=D solo trae disponibles' `
    (($disponibles.totalRecords -gt 0) -and
     (@($disponibles.items | Where-Object { $_.estado -ne 'D' })).Count -eq 0) `
    "total=$($disponibles.totalRecords)"
$noDisponibles = Listar $token "?texto=$(Texto $marca)&page_size=100&estado=N"
Check 'D, N e I reparten el universo sin solaparse' `
    (($disponibles.totalRecords + $noDisponibles.totalRecords + $inactivas.totalRecords) -eq $total) `
    "D=$($disponibles.totalRecords) N=$($noDisponibles.totalRecords) I=$($inactivas.totalRecords)"
$porDistritoFiltro = Listar $token "?page_size=100&distrito=$(Texto $distritoFixture)"
Check 'el filtro por distrito acota a la cartera del fixture' `
    ($porDistritoFiltro.totalRecords -eq $total) "total=$($porDistritoFiltro.totalRecords)"
$opciones = Api GET '/propiedades/filtros' $token $null
Check 'el distrito del fixture se ofrece en el filtro' `
    (@($opciones.distritos) -contains $distritoFixture) "$(@($opciones.distritos).Count) distritos"

# ---------------------------------------------------------------------
Write-Host "`n== 12. La frontera de tenant ==" -ForegroundColor Cyan
# La cartera del vecino se construye con la MISMA marca, el MISMO distrito y un
# propietario del MISMO nombre: si el listado filtrara por texto antes que por
# tenant, o se dejara el discriminador en el conteo, estas filas saldrian.
$codigoOrganizacion = "OTRA-$marca"
$idOrganizacionOtra = [long](Sql "insert into organizacion (codigo, nombre) values ('$codigoOrganizacion', 'Corredora vecina $marca') returning id_organizacion")
$idPersonaOtra = [long](Sql @"
insert into persona (tipo_persona, tipo_documento, numero_documento, nombres_o_razon_social,
                     estado, organizacion_id)
values ('N', 'D', '72$($marca.Substring(3))', 'Titular $marca', 'A', $idOrganizacionOtra)
returning id_persona
"@)
$idPropietarioOtro = [long](Sql @"
insert into persona_rol (id_persona, tipo_rol, organizacion_id)
values ($idPersonaOtra, 'PROPIETARIO', $idOrganizacionOtra)
returning id_persona_rol
"@)
$idAgenteOtro = [long](Sql @"
insert into persona_rol (id_persona, tipo_rol, organizacion_id)
values ($idPersonaOtra, 'AGENTE', $idOrganizacionOtra)
returning id_persona_rol
"@)
Sql @"
insert into detalle_agente (id_persona_rol, codigo_agente, fecha_ingreso, organizacion_id)
values ($idAgenteOtro, 'AGE-$($marca.Substring(3))', current_date, $idOrganizacionOtra)
"@ | Out-Null
$salidaVecinas = Sql @"
insert into propiedad (codigo, direccion, distrito, metraje, estado_registro,
                       disponibilidad_comercial, tipo_inmueble, uso,
                       id_rol_propietario, organizacion_id)
select '$marca-V' || g, 'Avenida $marca vecina ' || g, '$distritoFixture', 70 + g,
       'A', 'D', (array['L','D','T'])[g], 'C', $idPropietarioOtro, $idOrganizacionOtra
from generate_series(1, 3) g
returning id_propiedad
"@
$idsOtras = @(($salidaVecinas -split "`n") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
Check 'la corredora vecina tiene sus tres propiedades' (@($idsOtras).Count -eq 3) "$(@($idsOtras).Count)"
Sql @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, motivo_operacion, id_propiedad, id_rol_agente, organizacion_id)
select 'CAPV-' || p.id_propiedad, current_date, current_date, current_date + 180,
       'P', 'V', p.id_propiedad, $idAgenteOtro, $idOrganizacionOtra
  from propiedad p where p.organizacion_id = $idOrganizacionOtra
"@ | Out-Null

$trasVecina = Listar $token "?texto=$(Texto $marca)&page_size=100"
Check 'la cartera del vecino no entra en el total' `
    ($trasVecina.totalRecords -eq $total) "total=$($trasVecina.totalRecords) (esperado $total)"
$idsPropios = @{}
foreach ($id in @(IdsDe $trasVecina)) { $idsPropios["$id"] = $true }
Check 'ni una sola fila del vecino aparece en la pagina' `
    ((@($idsOtras | Where-Object { $idsPropios.ContainsKey("$_") })).Count -eq 0) `
    ($idsOtras -join ',')
foreach ($consulta in "?texto=$(Texto $distritoFixture)&page_size=100",
                      "?distrito=$(Texto $distritoFixture)&page_size=100",
                      "?texto=$(Texto $marca)&page_size=100&operaciones=VENTA",
                      "?texto=$(Texto $marca)&page_size=100&estado=D") {
    $r = Listar $token $consulta
    $cruzadas = @(@(IdsDe $r) | Where-Object { $idsOtras -contains "$_" })
    Check "la frontera aguanta con '$consulta'" (@($cruzadas).Count -eq 0) ($cruzadas -join ',')
}
# Y por si el dia de manana alguien construyera la consulta sin el
# discriminador: la BASE tampoco deja cruzarla. `fk_captacion_propiedad_org` es
# una FK COMPUESTA (organizacion_id, id_propiedad), asi que un encargo del
# vecino sobre una propiedad de esta corredora no llega a existir.
$sabotaje = SqlDebeFallar @"
insert into captacion (codigo_captacion, fecha_captacion, fecha_inicio_encargo, fecha_fin_encargo,
                       estado, motivo_operacion, id_propiedad, id_rol_agente, organizacion_id)
values ('CAPX-$($marca.Substring(3))', current_date, current_date, current_date + 180,
        'P', 'V', $($creadas['CASA'].id), $idAgenteOtro, $idOrganizacionOtra)
"@
Check 'la base rechaza un encargo que cruza la frontera de tenant' `
    ($sabotaje.Codigo -ne 0) "codigo=$($sabotaje.Codigo) $($sabotaje.Salida)"

# ---------------------------------------------------------------------
Write-Host "`n== 13. La validacion comun de los dos listados ==" -ForegroundColor Cyan
# Los dos recursos comparten tenant, texto, estado y paginacion, asi que
# comparten tambien lo que pasa cuando el filtro esta mal escrito. Antes de la
# normalizacion los dos tenian el MISMO agujero por separado: un estado
# desconocido no era un error, era una pagina vacia -que le dice al cliente "no
# hay nada" cuando lo que pasa es que la pregunta no se entendio-.
# OJO con la interpolacion: tiene que ser "${ruta}?..." y no "$ruta?...". En
# PowerShell el `?` SI puede formar parte de un nombre de variable, asi que
# `"$ruta?estado=ACTIVO"` se lee como la variable `$ruta?estado` -inexistente,
# luego cadena vacia- y la URL queda en "<base>=ACTIVO". No da error: da un 404
# perfectamente creible que parece del producto. Costo una corrida el 2026-09-02.
foreach ($ruta in '/propiedades', '/locales') {
    $r = ApiError GET "${ruta}?estado=ACTIVO" $token $null
    Check "$ruta con un estado fuera del vocabulario responde 400" `
        ($r.codigo -eq 400) "codigo=$($r.codigo) $($r.error)"
    Check "$ruta dice cual es el vocabulario, no solo que esta mal" `
        ("$($r.error)" -match 'D.*N.*I') "$($r.error)"
    # Un parametro tipado con basura tambien es del cliente. Antes respondia 500
    # y el cuerpo publicaba el mensaje interno de la conversion.
    $t = ApiError GET "${ruta}?page=abc" $token $null
    Check "$ruta con page no numerico responde 400" ($t.codigo -eq 400) "codigo=$($t.codigo) $($t.error)"
    Check "$ruta no filtra el detalle interno en el error" `
        ("$($t.error)" -notmatch 'Detalle:|Exception|java\.') "$($t.error)"
    # Y el vocabulario no distingue mayusculas: lo que no admite es otra palabra.
    $minuscula = Api GET "${ruta}?estado=d&page_size=1" $token $null
    Check "$ruta acepta el estado en minusculas" ($minuscula.pageSize -eq 1) "pageSize=$($minuscula.pageSize)"
}
# Lo que NO cambia: un numero fuera de rango se acota, no se rechaza. Es lo que
# los dos recursos llevan haciendo desde que existen, y es distinto de una
# palabra que no pertenece a ningun vocabulario.
$acotada = Listar $token '?page=0&page_size=0'
Check 'una pagina fuera de rango se sigue acotando, no rechazando' `
    (($acotada.page -eq 1) -and ($acotada.pageSize -eq 1)) `
    "page=$($acotada.page) pageSize=$($acotada.pageSize)"

# ---------------------------------------------------------------------
Write-Host "`n== 14. Paridad con /locales sobre el MISMO inmueble ==" -ForegroundColor Cyan
# Los dos listados leen la misma tabla. Desde la normalizacion tambien usan el
# mismo motor, asi que sobre un inmueble que existe en los dos universos las
# dimensiones COMUNES tienen que responder igual. Las que no son comunes se
# afirman como diferencia deliberada, no se disimulan.
$localCodigo = $creadas['LOCAL'].codigo
$localId = $creadas['LOCAL'].id
# La pertenencia se comprueba RECORRIENDO las paginas, no mirando la primera.
# Los dos recursos ordenan al reves, asi que sobre un universo mayor que una
# pagina "esta en la pagina 1" no es la misma pregunta en los dos y compararlo
# mediria el orden, no la busqueda. Costo tres rojos el 2026-09-02.
function ContieneUniversal($token, $texto, $id, $total) {
    $paginas = [math]::Max(1, [math]::Ceiling($total / 100))
    for ($p = 1; $p -le $paginas; $p++) {
        if (@(IdsDe (Listar $token "?texto=$(Texto $texto)&page=$p&page_size=100")) -contains $id) { return $true }
    }
    return $false
}
function ContieneLocales($token, $texto, $id, $total) {
    $paginas = [math]::Max(1, [math]::Ceiling($total / 100))
    for ($p = 1; $p -le $paginas; $p++) {
        $r = Api GET "/locales?page=$p&tamano=100&texto=$(Texto $texto)" $token $null
        if (@(@($r.items) | ForEach-Object { [long]$_.id }) -contains $id) { return $true }
    }
    return $false
}
foreach ($dimension in @(
    @{ nombre = 'codigo';      texto = $localCodigo },
    @{ nombre = 'direccion';   texto = "Avenida $marca numero 1" },
    @{ nombre = 'distrito';    texto = $distritoFixture },
    @{ nombre = 'propietario'; texto = "Titular $marca" })) {
    $u = Listar $token "?texto=$(Texto $dimension.texto)&page_size=1"
    $l = Api GET "/locales?page=1&tamano=1&texto=$(Texto $dimension.texto)" $token $null
    Check "y por $($dimension.nombre) los dos cuentan lo mismo" `
        ($u.totalRecords -eq $l.totalRecords) `
        "universal=$($u.totalRecords) locales=$($l.totalRecords)"
    $enUniversal = ContieneUniversal $token $dimension.texto $localId $u.totalRecords
    $enLocales = ContieneLocales $token $dimension.texto $localId $l.totalRecords
    Check "buscar por $($dimension.nombre) encuentra el inmueble en LOS DOS listados" `
        ($enUniversal -and $enLocales) "universal=$enUniversal locales=$enLocales"
}
# Tenant: la misma frontera para los dos.
$vecinaUniversal = Listar $token "?texto=$(Texto "Avenida $marca vecina")&page_size=100"
$vecinaLocales = Api GET "/locales?page=1&tamano=100&texto=$(Texto "Avenida $marca vecina")" $token $null
Check 'la cartera del vecino no la ve ninguno de los dos' `
    (($vecinaUniversal.totalRecords -eq 0) -and ($vecinaLocales.totalRecords -eq 0)) `
    "universal=$($vecinaUniversal.totalRecords) locales=$($vecinaLocales.totalRecords)"
# Paginacion y conteo: mismo universo, mismo total, y el total no depende del
# tamano de pagina en ninguno de los dos.
$uChica = Listar $token "?texto=$(Texto $marca)&page_size=5"
$lChica = Api GET "/locales?page=1&tamano=5&texto=$(Texto $marca)" $token $null
Check 'los dos pagina de 5 en 5 sin mover el total' `
    ((@($uChica.items).Count -eq 5) -and (@($lChica.items).Count -eq 5) -and
     ($uChica.totalRecords -eq $completo.totalRecords) -and
     ($lChica.totalRecords -eq $completo.totalRecords)) `
    "u=$($uChica.totalRecords) l=$($lChica.totalRecords) esperado=$($completo.totalRecords)"

# El RUBRO ya no esta en esta lista: dejo de ser una diferencia el 2026-09-02.
# Su paridad se afirma arriba, con los dos listados devolviendo lo mismo.
Write-Host "  -- diferencias deliberadas --" -ForegroundColor DarkGray
# 1. El ORDEN es el contrario, y cada uno publica el suyo.
$uOrden = @(IdsDe (Listar $token "?texto=$(Texto $marca)&page_size=100"))
$lOrden = @(@((Api GET "/locales?page=1&tamano=100&texto=$(Texto $marca)" $token $null).items) |
            ForEach-Object { [long]$_.id })
Check 'el universal ordena id DESC y /locales id ASC, cada uno el suyo' `
    ((($uOrden -join ',') -eq (@($uOrden | Sort-Object -Descending) -join ',')) -and
     (($lOrden -join ',') -eq (@($lOrden | Sort-Object) -join ','))) `
    'algun orden no es el declarado'
# 2. Los filtros propios del modelo universal no existen en /locales: alli el
#    tipo no se publica ni se filtra, y las operaciones viven en los encargos.
#    Lo que se afirma es la asimetria: en el universal el parametro RECORTA; en
#    el heredado no existe, asi que la respuesta es la misma con y sin el.
$conTipo = Listar $token "?texto=$(Texto $marca)&page_size=100&tipoPropiedad=TERRENO"
$sinTipo = Listar $token "?texto=$(Texto $marca)&page_size=1"
Check 'en el universal el tipo recorta de verdad' `
    (($conTipo.totalRecords -gt 0) -and ($conTipo.totalRecords -lt $sinTipo.totalRecords)) `
    "conTipo=$($conTipo.totalRecords) sinTipo=$($sinTipo.totalRecords)"
Check 'y todas las filas que devuelve son de ese tipo' `
    ((@($conTipo.items | Where-Object { $_.tipoPropiedad -ne 'TERRENO' })).Count -eq 0) 'algun tipo distinto'
$localesConTipo = Api GET "/locales?page=1&tamano=1&texto=$(Texto $marca)&tipoPropiedad=TERRENO" $token $null
$localesSinTipo = Api GET "/locales?page=1&tamano=1&texto=$(Texto $marca)" $token $null
Check 'en /locales el parametro de tipo no existe y no cambia nada' `
    ($localesConTipo.totalRecords -eq $localesSinTipo.totalRecords) `
    "con=$($localesConTipo.totalRecords) sin=$($localesSinTipo.totalRecords)"

# ---------------------------------------------------------------------
Write-Host "`n== 15. Sin sesion no hay cartera ==" -ForegroundColor Cyan
$sinToken = ApiError GET '/propiedades' $null $null
Check 'sin token el listado responde 401' ($sinToken.codigo -eq 401) "codigo=$($sinToken.codigo)"

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
