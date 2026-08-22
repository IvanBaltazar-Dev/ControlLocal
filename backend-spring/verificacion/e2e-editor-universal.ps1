# Verificacion HTTP del editor universal: PUT /propiedades/{id} por el cable.
#
# POR QUE EXISTE. El gate de conservacion (ConservacionDeLaEdicionIntegrationTest)
# demuestra la regla de bloques llamando al SERVICIO, y los specs del editor
# demuestran que el SPA construye un cuerpo con solo lo tocado. Entre los dos
# quedaba sin recorrer justo el tramo que une las dos cosas: un JSON parcial
# entrando por HTTP, deserializado por Jackson, aplicado contra PostgreSQL y
# releido por GET. Esta suite recorre ese tramo para LOS SIETE TIPOS.
#
# LO QUE FIJA, por tipo:
#   abrir -> modificar UNA cosa -> guardar -> releer  ==  todo lo demas identico
# para la ubicacion, una caracteristica, el importe del encargo de VENTA (con
# el de ALQUILER intacto y el historico de la venta creciendo en UN hito), lo
# pactado en el alquiler (por idEncargo) y el borrado explicito. Y que un valor
# en blanco se rechaza: vaciar no es borrar.
#
# EL FIXTURE SALE DEL CONTRATO. Este guion no lleva una tabla "tipo -> campos":
# pide GET /captura/definicion para cada tipo y rellena lo que el Core declara,
# por control. Si el catalogo crece, la suite crece con el sin tocar una linea.
#
# ASCII puro y sin BOM: PS 5.1 lee un .ps1 sin BOM como ANSI y un solo caracter
# acentuado -aunque este en un comentario- rompe el parseo entero.
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
    $parametros = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 45 }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 8)
        $parametros['ContentType'] = 'application/json'
    } elseif ($metodo -in @('POST', 'PUT', 'PATCH')) {
        $parametros['ContentType'] = 'application/json'
    }
    $parametros
}

# Si el API rechaza, el error que sube lleva el CUERPO de la respuesta: sin el,
# un 400 se lee como "WebException" y hay que repetir la corrida entera para
# saber que dijo el Core.
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

# El PUT tal como lo manda el editor: con su Idempotency-Key.
function Editar($id, $token, $cuerpo) {
    $parametros = ParametrosApi 'PUT' "/propiedades/$id" $token $cuerpo
    $parametros.Headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
    Invoke-RestMethod @parametros
}

function EditarError($id, $token, $cuerpo) {
    try {
        Editar $id $token $cuerpo | Out-Null
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

. "$PSScriptRoot/lib-alta-inmueble.ps1"

# ---------------------------------------------------------------------
# El retrato: toda la ficha, plana y con nombre, para decir QUE se movio.
# Mismo criterio que el gate de conservacion: comparar dos objetos dice "no
# son iguales"; comparar dos mapas dice que campo.
# ---------------------------------------------------------------------
# Por VALOR y no por escala, igual que el gate de conservacion: el PUT devuelve
# la ficha con el importe recien puesto (`260000`) y el GET la relee de la
# base (`260000.00`, numeric(14,2)). Son el mismo importe, y una diferencia de
# escala no es una perdida de dato.
function Norm($v) {
    $s = "$v"
    if ($s -match '^-?\d+(\.\d+)?$') {
        $cultura = [Globalization.CultureInfo]::InvariantCulture
        return ([decimal]::Parse($s, $cultura)).ToString('0.############', $cultura)
    }
    $s
}

function Retrato($f) {
    $r = @{}
    foreach ($c in 'codigo', 'tipoPropiedad', 'uso', 'descripcion', 'estadoRegistro', 'disponibilidadComercial') {
        $r["prop.$c"] = "$($f.$c)"
    }
    if ($f.ubicacion) {
        foreach ($p in $f.ubicacion.PSObject.Properties) { $r["ubicacion.$($p.Name)"] = "$($p.Value)" }
    }
    foreach ($a in @($f.atributos)) { if ($a) { $r["atributo.$($a.clave)"] = Norm $a.valor } }
    foreach ($t in @($f.titulares)) { if ($t) { $r["titular.$($t.idPropietario)"] = "$(Norm $t.cuota)|$($t.representante)" } }
    foreach ($e in @($f.encargos)) {
        if (-not $e) { continue }
        $p = "encargo.$($e.idEncargo)."
        foreach ($c in 'operacion', 'estado', 'vivo', 'importe', 'moneda', 'exclusividad', 'inicio', 'fin', 'idAgente') {
            $r["$($p)$c"] = Norm $e.$c
        }
        $r["$($p)hitos"] = (@($e.historico) | ForEach-Object { "$($_.hito) $($_.moneda) $(Norm $_.monto) $($_.fecha)" }) -join ' | '
        foreach ($c in @($e.condiciones)) { if ($c) { $r["$($p)cond.$($c.clave)"] = Norm $c.valor } }
    }
    $r
}

function Diferencias($a, $b, $permitidos) {
    $claves = @(@($a.Keys) + @($b.Keys) | Sort-Object -Unique)
    $dif = @()
    foreach ($k in $claves) {
        if ($permitidos -contains $k) { continue }
        $va = '<ausente>'; if ($a.ContainsKey($k)) { $va = $a[$k] }
        $vb = '<ausente>'; if ($b.ContainsKey($k)) { $vb = $b[$k] }
        if ($va -ne $vb) { $dif += "$k : '$va' -> '$vb'" }
    }
    ,$dif
}

function Encargo($f, $operacion) {
    @($f.encargos) | Where-Object { $_.operacion -eq $operacion -and $_.vivo } | Select-Object -First 1
}

# ---------------------------------------------------------------------
# Valores por CONTROL, nunca por clave: el guion no sabe que es `dormitorios`.
# ---------------------------------------------------------------------
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

function ClaveBase($clave) { $i = $clave.IndexOf(':'); if ($i -lt 0) { $clave } else { $clave.Substring(0, $i) } }

# Los campos de la UBICACION tal como los nombra el cable (UbicacionRequest).
# El motor los pregunta dentro de `delTipo` cuando son del tipo -el interior y
# el edificio, en L/O/D-, pero en el cuerpo del alta y del PUT viajan en
# `ubicacion`, no en `atributos`: el enrutador de atributos solo conoce el
# catalogo. Es la misma lista que usa el editor, y por la misma razon.
$camposUbicacion = @('direccion', 'distrito', 'zonaUrbanizacion', 'latitud', 'longitud',
                     'interiorUnidad', 'piso', 'referenciaInterna', 'nombreEdificioGaleria')
function EsDeUbicacion($p) { $camposUbicacion -contains $p.clave }

$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$marca = "EDU$sufijo"

Write-Host "`n== 1. Contexto efimero y actores ==" -ForegroundColor Cyan
Check 'la base lleva identificador exclusivo de corrida' `
    ($e2e.Database -match '^controllocal_e2e_' -and $e2e.Database -ne 'controllocal') $e2e.Database
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'login del agente' ($agente.rol -eq 'AGENTE') $agente.rol
$dueno = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "73$sufijo"
    nombre = "Titular $marca"; telefono = '987620001'
    correo = "titular.$marca@test.local"; consentimientoUsoDato = $true; estado = 'A'
}
$socio = Api POST '/propietarios' $agente.token @{
    tipoPersona = 'N'; tipoDocumento = 'D'; numeroDocumento = "74$sufijo"
    nombre = "Socio $marca"; telefono = '987620002'
    correo = "socio.$marca@test.local"; consentimientoUsoDato = $true; estado = 'A'
}

$tipos = @('LOCAL', 'OFICINA', 'DEPARTAMENTO', 'CASA', 'TERRENO', 'ALMACEN', 'OTRO')
$indice = 0
foreach ($tipo in $tipos) {
    $indice++
    Write-Host "`n== 2.$indice $tipo ==" -ForegroundColor Cyan

    # El plan de preguntas de ESTE tipo con las dos operaciones: lo mismo que
    # pide el alta y lo mismo que pide el editor.
    $def = Api GET "/captura/definicion?intencion=REGISTRAR_PROPIEDAD&tipoPropiedad=$tipo&operaciones=VENTA,ALQUILER" $agente.token $null
    $delTipo = @(@($def.delTipo) | Where-Object { $_ -and (EsEditable $_) })

    $atributos = @()
    $ubicacion = @{ direccion = "Calle $marca $indice"; distrito = 'Miraflores'; zonaUrbanizacion = 'Original' }
    $semilla = 0
    foreach ($p in $delTipo) {
        $semilla++
        $valor = ValorPara $p $semilla
        if (EsDeUbicacion $p) { $ubicacion[$p.clave] = $valor } else { $atributos += @{ clave = $p.clave; valor = $valor } }
    }
    $delCatalogo = @($delTipo | Where-Object { -not (EsDeUbicacion $_) })

    $alta = Api POST '/propiedades' $agente.token @{
        tipoPropiedad = $tipo
        descripcion   = "Editor universal $marca $tipo"
        ubicacion     = $ubicacion
        titulares     = @(@{ idPropietario = $dueno.id; representante = $true })
        atributos     = $atributos
        operaciones   = @(
            @{ operacion = 'VENTA';    importe = 250000; moneda = 'USD'; exclusividad = $true },
            @{ operacion = 'ALQUILER'; importe = 3500;   moneda = 'PEN'; exclusividad = $false }
        )
    }
    $id = [long]$alta.idPropiedad
    $antes = Api GET "/propiedades/$id" $agente.token $null
    Check "$tipo nace con sus dos encargos vivos" (@(@($antes.encargos) | Where-Object { $_.vivo }).Count -eq 2) "$(@($antes.encargos).Count) encargos"
    Check "$tipo conserva lo que el contrato pidio" (@($antes.atributos).Count -ge $delCatalogo.Count) "$(@($antes.atributos).Count) de $($delCatalogo.Count)"

    # --- a) la ubicacion: un campo, y todo lo demas igual
    $despues = Editar $id $agente.token @{ ubicacion = @{ zonaUrbanizacion = "Editada $marca" } }
    $dif = Diferencias (Retrato $antes) (Retrato $despues) @('ubicacion.zonaUrbanizacion')
    Check "$tipo cambiar la zona conserva todo lo demas" ($dif.Count -eq 0) ($dif -join '; ')
    Check "$tipo y la zona cambio de verdad" ($despues.ubicacion.zonaUrbanizacion -eq "Editada $marca") "$($despues.ubicacion.zonaUrbanizacion)"
    $antes = $despues

    # --- b) una caracteristica del catalogo: la primera editable y no obligatoria
    $x = @($delCatalogo | Where-Object { -not $_.obligatoria -and $_.control -in @('ENTERO', 'DECIMAL', 'TEXTO') }) | Select-Object -First 1
    if ($x) {
        $nuevo = ValorPara $x 40
        $despues = Editar $id $agente.token @{ atributos = @(@{ clave = $x.clave; valor = $nuevo }) }
        $dif = Diferencias (Retrato $antes) (Retrato $despues) @("atributo.$($x.clave)")
        Check "$tipo cambiar '$($x.clave)' no toca ubicacion, titulares ni encargos" ($dif.Count -eq 0) ($dif -join '; ')
        $leido = @($despues.atributos | Where-Object { $_.clave -eq $x.clave }) | Select-Object -First 1
        Check "$tipo y '$($x.clave)' vale lo escrito" ("$($leido.valor)" -eq "$nuevo") "$($leido.valor) <> $nuevo"
        $antes = $despues

        # --- c) vaciar NO es borrar: se rechaza, y nada cambia
        $rechazo = EditarError $id $agente.token @{ atributos = @(@{ clave = $x.clave; valor = '' }) }
        Check "$tipo un valor en blanco se rechaza con 400" ($rechazo.codigo -eq 400) "codigo=$($rechazo.codigo) $($rechazo.error)"
        $tras = Api GET "/propiedades/$id" $agente.token $null
        $dif = Diferencias (Retrato $antes) (Retrato $tras) @()
        Check "$tipo y el rechazo no dejo rastro" ($dif.Count -eq 0) ($dif -join '; ')

        # --- d) borrar es una intencion declarada
        $despues = Editar $id $agente.token @{ atributosABorrar = @($x.clave) }
        $dif = Diferencias (Retrato $antes) (Retrato $despues) @("atributo.$($x.clave)")
        Check "$tipo retirar '$($x.clave)' lo retira y no toca nada mas" (($dif.Count -eq 0) -and -not (Retrato $despues).ContainsKey("atributo.$($x.clave)")) ($dif -join '; ')
        $antes = $despues
    } else {
        Write-Host "  --   $tipo no declara ninguna caracteristica opcional editable: b/c/d no aplican" -ForegroundColor Yellow
    }

    # --- e) el importe de la VENTA: solo la venta, y su historico CRECE
    $venta = Encargo $antes 'VENTA'
    $alquiler = Encargo $antes 'ALQUILER'
    $despues = Editar $id $agente.token @{ operaciones = @(@{ operacion = 'VENTA'; importe = 260000; moneda = 'USD' }) }
    $dif = Diferencias (Retrato $antes) (Retrato $despues) @("encargo.$($venta.idEncargo).importe", "encargo.$($venta.idEncargo).hitos")
    Check "$tipo cambiar el precio de venta deja el alquiler identico" ($dif.Count -eq 0) ($dif -join '; ')
    $ventaDespues = Encargo $despues 'VENTA'
    Check "$tipo el historico de la venta gana UN hito autorizado" `
        ((@($ventaDespues.historico).Count -eq (@($venta.historico).Count + 1)) -and (@($ventaDespues.historico)[-1].hito -eq 'U') -and ([double]@($ventaDespues.historico)[-1].monto -eq 260000)) `
        "antes=$(@($venta.historico).Count) despues=$(@($ventaDespues.historico).Count) ultimo=$(@($ventaDespues.historico)[-1].hito) $(@($ventaDespues.historico)[-1].monto)"
    Check "$tipo y los hitos anteriores siguen delante, intactos" `
        (((@($venta.historico) | ForEach-Object { "$($_.hito) $($_.monto)" }) -join '|') -eq ((@($ventaDespues.historico) | Select-Object -First (@($venta.historico).Count) | ForEach-Object { "$($_.hito) $($_.monto)" }) -join '|')) 'el historico se reescribio'
    $antes = $despues

    # --- f) lo pactado en el ALQUILER viaja por idEncargo, sin calificar
    $bloqueAlquiler = @($def.deLaOperacion | Where-Object { $_.operacion -eq 'ALQUILER' }) | Select-Object -First 1
    $k = $null
    if ($bloqueAlquiler) {
        $k = @($bloqueAlquiler.preguntas | Where-Object { (ClaveBase $_.clave) -notin @('importe', 'moneda', 'exclusividad') -and $_.control -in @('ENTERO', 'DECIMAL', 'INTERRUPTOR', 'TEXTO') }) | Select-Object -First 1
    }
    if ($k) {
        $claveK = ClaveBase $k.clave
        $valorK = ValorPara $k 2
        $despues = Editar $id $agente.token @{ condiciones = @(@{ idEncargo = $alquiler.idEncargo; atributos = @(@{ clave = $claveK; valor = $valorK }) }) }
        $dif = Diferencias (Retrato $antes) (Retrato $despues) @("encargo.$($alquiler.idEncargo).cond.$claveK")
        Check "$tipo pactar '$claveK' en el alquiler no toca la venta ni la propiedad" ($dif.Count -eq 0) ($dif -join '; ')
        $condicion = @((Encargo $despues 'ALQUILER').condiciones | Where-Object { $_.clave -eq $claveK }) | Select-Object -First 1
        Check "$tipo y quedo escrito en SU encargo" ("$($condicion.valor)" -eq "$valorK") "$($condicion.valor) <> $valorK"
        Check "$tipo y no aparecio en la venta" (-not (@((Encargo $despues 'VENTA').condiciones | Where-Object { $_.clave -eq $claveK }).Count -gt 0)) 'la venta tiene la condicion del alquiler'
        $antes = $despues
    } else {
        Write-Host "  --   $tipo no declara condiciones pactables de alquiler: f no aplica" -ForegroundColor Yellow
    }

    # --- f bis) lo pactado en la VENTA, que hasta V77 no tenia vocabulario.
    #
    # Es la mitad del modelo que estaba entera sin sembrar: un encargo de venta
    # no podia decir si se entrega desocupado ni si acepta credito. Se elige la
    # condicion desde el CONTRATO, igual que las demas.
    $bloqueVenta = @($def.deLaOperacion | Where-Object { $_.operacion -eq 'VENTA' }) | Select-Object -First 1
    $kv = $null
    if ($bloqueVenta) {
        $kv = @($bloqueVenta.preguntas | Where-Object { (ClaveBase $_.clave) -notin @('importe', 'moneda', 'exclusividad') -and $_.control -in @('ENTERO', 'DECIMAL', 'INTERRUPTOR', 'TEXTO', 'SELECTOR') }) | Select-Object -First 1
    }
    if ($kv) {
        $claveV = ClaveBase $kv.clave
        $valorV = ValorPara $kv 3
        $despues = Editar $id $agente.token @{ condiciones = @(@{ idEncargo = $venta.idEncargo; atributos = @(@{ clave = $claveV; valor = $valorV }) }) }
        $dif = Diferencias (Retrato $antes) (Retrato $despues) @("encargo.$($venta.idEncargo).cond.$claveV")
        Check "$tipo pactar '$claveV' en la VENTA no toca el alquiler ni la propiedad" ($dif.Count -eq 0) ($dif -join '; ')
        $condV = @((Encargo $despues 'VENTA').condiciones | Where-Object { $_.clave -eq $claveV }) | Select-Object -First 1
        Check "$tipo y quedo escrito en el encargo de VENTA" ("$(Norm $condV.valor)" -eq "$(Norm $valorV)") "$($condV.valor) <> $valorV"
        Check "$tipo y no aparecio en el alquiler" (-not (@((Encargo $despues 'ALQUILER').condiciones | Where-Object { $_.clave -eq $claveV }).Count -gt 0)) 'el alquiler tiene la condicion de la venta'
        $antes = $despues
    } else {
        Write-Host "  --   $tipo no declara condiciones pactables de venta: f bis no aplica" -ForegroundColor Yellow
    }

    # --- f ter) los dos tipos por los que se ensancho el cable en V77.
    #
    # `AtributoRequest` llevaba (clave, valor) y no sabia transportar una moneda
    # ni una lista, asi que un IMPORTE y un LISTA_MULTIPLE eran inescribibles.
    # Se ejercitan POR EL CABLE porque es ahi donde estaba el estrechamiento: la
    # prueba de integracion llama al servicio, que siempre supo hacerlo.
    if ($bloqueAlquiler) {
        $kimp = @($bloqueAlquiler.preguntas | Where-Object { $_.control -eq 'IMPORTE' }) | Select-Object -First 1
        if ($kimp) {
            $claveI = ClaveBase $kimp.clave
            $despues = Editar $id $agente.token @{ condiciones = @(@{ idEncargo = $alquiler.idEncargo; atributos = @(@{ clave = $claveI; valor = '250'; moneda = 'PEN' }) }) }
            $condI = @((Encargo $despues 'ALQUILER').condiciones | Where-Object { $_.clave -eq $claveI }) | Select-Object -First 1
            Check "$tipo un IMPORTE viaja con su moneda y vuelve cruda" `
                (($condI.moneda -eq 'PEN') -and ("$($condI.valor)" -match '250')) `
                "moneda=$($condI.moneda) valor=$($condI.valor)"
            $dif = Diferencias (Retrato $antes) (Retrato $despues) @("encargo.$($alquiler.idEncargo).cond.$claveI")
            Check "$tipo y no toco nada mas" ($dif.Count -eq 0) ($dif -join '; ')
            $antes = $despues
        }

        $kmul = @($bloqueAlquiler.preguntas | Where-Object { $_.control -eq 'SELECTOR_MULTIPLE' }) | Select-Object -First 1
        if ($kmul) {
            $claveM = ClaveBase $kmul.clave
            $elegidos = @(@($kmul.opciones)[0].valor)
            if (@($kmul.opciones).Count -gt 1) { $elegidos += @($kmul.opciones)[1].valor }
            # `valores` y NUNCA `valor`: un multivalor que llegue tambien como
            # escalar lo rechaza el Core -- sus valores van en su tabla.
            $despues = Editar $id $agente.token @{ condiciones = @(@{ idEncargo = $alquiler.idEncargo; atributos = @(@{ clave = $claveM; valores = $elegidos }) }) }
            $condM = @((Encargo $despues 'ALQUILER').condiciones | Where-Object { $_.clave -eq $claveM }) | Select-Object -First 1
            # Se compara como CONJUNTO, no como secuencia. Un multivalor es un
            # conjunto -- quien lo recibe sustituye -- y el Core lo devuelve en
            # orden estable (`order by valor`), no en el orden en que llego. Que
            # el mismo conjunto se lea siempre igual es lo que hace comparables
            # dos retratos; exigir el orden de envio seria afirmar una semantica
            # que la lista no tiene.
            Check "$tipo un multivalor vuelve como lista cruda, no como texto con comas" `
                (((@($condM.valores) | Sort-Object) -join '|') -eq (($elegidos | Sort-Object) -join '|')) `
                "valores=$(@($condM.valores) -join '|') esperado=$($elegidos -join '|')"
            # Al LEER, `valor` trae el texto ya compuesto -- "Cocina, Lavadora" --
            # y `valores` la lista cruda: la misma verdad, una vez para pintar y
            # otra para poder corregirla. Lo que se comprueba aqui es que la
            # cruda esta, que es la que el editor necesita.
            Check "$tipo y ademas del texto compuesto vienen los valores crudos" `
                (@($condM.valores).Count -eq $elegidos.Count) `
                "crudos=$(@($condM.valores).Count) esperados=$($elegidos.Count)"

            # Y al ESCRIBIR, la regla es la contraria: un multivalor NO puede
            # llegar tambien como escalar. Ahi si son dos formas del mismo dato
            # y el Core no elige entre ellas -- avisa.
            $ambos = EditarError $id $agente.token @{ condiciones = @(@{ idEncargo = $alquiler.idEncargo; atributos = @(@{ clave = $claveM; valor = 'COCINA'; valores = $elegidos }) }) }
            Check "$tipo mandar un multivalor como lista Y como escalar se rechaza" `
                ($ambos.codigo -eq 400) "codigo=$($ambos.codigo) $($ambos.error)"
            $antes = $despues
        }
    }

    # --- g) un bloque de una operacion que NO esta viva se rechaza: no se crea nada
    if ($tipo -eq 'OTRO') {
        $rechazo = EditarError $id $agente.token @{ operaciones = @(@{ operacion = 'VENTA'; importe = 1; moneda = 'USD'; exclusividad = $true }, @{ operacion = 'VENTA'; importe = 2; moneda = 'USD' }) }
        Check 'dos bloques de la misma operacion se rechazan' ($rechazo.codigo -eq 400) "codigo=$($rechazo.codigo)"
    }
}

Write-Host "`n== 3. Titulares: el conjunto completo, solo si se toca ==" -ForegroundColor Cyan
# Sobre la ultima propiedad creada. Una copropiedad con cuotas: el PUT manda el
# conjunto entero y el Core concilia.
$antes = Api GET "/propiedades/$id" $agente.token $null
$despues = Editar $id $agente.token @{ titulares = @(
    @{ idPropietario = $dueno.id; cuota = 60; representante = $true },
    @{ idPropietario = $socio.id; cuota = 40; representante = $false }
) }
$dif = @(Diferencias (Retrato $antes) (Retrato $despues) @() | Where-Object { $_ -notlike 'titular.*' })
Check 'cambiar los titulares no toca ubicacion, atributos ni encargos' ($dif.Count -eq 0) ($dif -join '; ')
Check 'quedan dos titulares vigentes con sus cuotas' `
    ((@($despues.titulares).Count -eq 2) -and (@($despues.titulares | Where-Object { $_.idPropietario -eq $socio.id -and [double]$_.cuota -eq 40 }).Count -eq 1)) `
    "$(@($despues.titulares).Count) titulares"
$sinTocar = Editar $id $agente.token @{ descripcion = "Sin tocar titulares $marca" }
Check 'editar otra cosa despues deja los dos titulares como estaban' (@($sinTocar.titulares).Count -eq 2) "$(@($sinTocar.titulares).Count) titulares"

Write-Host "`n== 4. Lo que el editor no ofrece, el cable tampoco admite ==" -ForegroundColor Cyan
$rechazo = EditarError $id $agente.token @{ atributos = @(@{ clave = 'zzz_clave_inexistente'; valor = '1' }) }
Check 'una clave fuera del catalogo se rechaza' ($rechazo.codigo -eq 400) "codigo=$($rechazo.codigo)"
$rechazo = EditarError $id $agente.token @{ atributosABorrar = @('direccion') }
Check 'la direccion no se puede retirar' ($rechazo.codigo -eq 400) "codigo=$($rechazo.codigo)"
$vacio = Editar $id $agente.token @{}
$dif = Diferencias (Retrato $sinTocar) (Retrato $vacio) @()
Check 'un cuerpo vacio no toca nada' ($dif.Count -eq 0) ($dif -join '; ')

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
