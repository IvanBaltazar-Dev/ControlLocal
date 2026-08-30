# Alta de un inmueble con su encargo, para los guiones E2E.
#
# POR QUE EXISTE. El Corte 0A (V71) retiro `POST /locales` y `PUT /locales/{id}`:
# eran la puerta del modelo viejo -un formulario que solo sabia describir locales
# comerciales- y quedaron sustituidos por `POST /propiedades`, que da de alta
# cualquiera de los siete tipos. Diez guiones E2E seguian llamando a la puerta
# retirada y contestaba 405; la corrida de cierre lo destapo en el Corte 0C.
#
# LO QUE CAMBIA, Y NO ES SOLO LA RUTA. `POST /propiedades` da de alta el inmueble
# Y ABRE SUS ENCARGOS en el mismo acto, porque una propiedad sin operacion
# declarada no dice si su importe es un precio de venta o una renta. Asi que
# esto sustituye a DOS llamadas del guion viejo:
#
#     POST /locales      + POST /captaciones     ->   POST /propiedades
#
# y devuelve las dos identidades que el guion necesita despues: `id` (el
# inmueble) e `idEncargo` (la relacion comercial, la que va a `decision`).
#
# Se dot-sourcea desde cada guion, asi que puede llamar a la funcion `Api` que
# cada uno define: PowerShell resuelve el nombre en el momento de la llamada.
#
# ASCII puro, como todos los .ps1 de esta carpeta: PS 5.1 lee un fichero sin BOM
# como ANSI y un solo caracter acentuado -aunque este en un comentario- rompe el
# parseo entero con un error que senala una linea sana.

function NuevoInmuebleConEncargo {
    param(
        [Parameter(Mandatory = $true)] [string] $Token,
        [Parameter(Mandatory = $true)] [string] $Direccion,
        [Parameter(Mandatory = $true)] $IdPropietario,
        # El codigo del inmueble. Vacio = lo genera el Core (PROP-####). Los
        # guiones que buscan por codigo -o que lo afirman en un Check- pasan el
        # suyo, igual que hacian con `codigoLocal`.
        [string] $Codigo = $null,
        [string] $Distrito = 'Barranco',
        [string] $TipoPropiedad = 'LOCAL',
        [string] $Uso = 'C',
        [string] $Descripcion = 'Alta de guion E2E',
        [string] $Operacion = 'ALQUILER',
        $Importe = 1000,
        [string] $Moneda = 'PEN',
        # El rubro y el metraje viajan como atributos GOBERNADOS. Desde V71 no
        # hay tabla espejo: `rubro_permitido` vive en `atributo_propiedad` y
        # `metraje_total` en el campo canonico, y de eso se encarga el enrutador
        # de autoridad. El guion no tiene que saber cual es cual.
        $Metraje = 90,
        [string] $Rubro = 'Retail',
        # `tipo_acceso` es ALT en LOCAL desde V81, y ALT impide publicar. Vacio
        # = no se registra, que es lo que necesita una suite que quiera probar
        # justamente el bloqueo.
        [string] $TipoAcceso = 'A_PIE_DE_CALLE',
        $Atributos = $null,
        $TipoComision = $null,
        $BaseCalculo = $null,
        $ValorComision = $null,
        $TratamientoIgv = $null,
        $Exclusividad = $true,
        $InicioEncargo = $null,
        $FinEncargo = $null,
        [string] $ZonaUrbanizacion = $null,
        [string] $InteriorUnidad = $null,
        [string] $Piso = $null
    )

    $listaAtributos = @()
    if ($null -ne $Metraje) { $listaAtributos += @{ clave = 'metraje_total'; valor = "$Metraje" } }
    if ($Rubro) { $listaAtributos += @{ clave = 'rubro_permitido'; valor = $Rubro } }
    if ($Atributos) { foreach ($extra in $Atributos) { $listaAtributos += $extra } }

    # V81 hizo `tipo_acceso` ALT en LOCAL, y en este proyecto ALT NO es un aviso:
    # `clavesQueImpidenPublicar` filtra `exigencia in ('ALT','PUB')`, asi que un
    # local sin este dato queda registrado pero NO publicable, y toda suite que
    # publique se cae con 400. El fixture registra el dato, que es lo que haria un
    # agente que ha estado de pie en el local.
    #
    # LO QUE NO SE HACE AQUI, y es la parte que importa: no se baja la exigencia a
    # OPC, y no se cambia el tipo del inmueble a OFICINA para esquivarla. Lo
    # primero relaja la regla que el titular decidio; lo segundo cambia lo que la
    # suite dice estar probando. Arreglar el fixture es registrar el dato.
    $traeAcceso = $false
    if ($Atributos) {
        foreach ($extra in $Atributos) { if ($extra.clave -eq 'tipo_acceso') { $traeAcceso = $true } }
    }
    if ($TipoPropiedad -eq 'LOCAL' -and $TipoAcceso -and -not $traeAcceso) {
        $listaAtributos += @{ clave = 'tipo_acceso'; valor = $TipoAcceso }
    }

    $bloque = @{ operacion = $Operacion; importe = $Importe; moneda = $Moneda }
    if ($null -ne $Exclusividad)   { $bloque['exclusividad']   = $Exclusividad }
    if ($TipoComision)             { $bloque['tipoComision']   = $TipoComision }
    if ($BaseCalculo)              { $bloque['baseCalculo']    = $BaseCalculo }
    if ($null -ne $ValorComision)  { $bloque['valorComision']  = $ValorComision }
    if ($TratamientoIgv)           { $bloque['tratamientoIgv'] = $TratamientoIgv }
    if ($InicioEncargo)            { $bloque['inicioEncargo']  = $InicioEncargo }
    if ($FinEncargo)               { $bloque['finEncargo']     = $FinEncargo }

    $ubicacion = @{ direccion = $Direccion; distrito = $Distrito }
    if ($ZonaUrbanizacion) { $ubicacion['zonaUrbanizacion'] = $ZonaUrbanizacion }
    if ($InteriorUnidad)   { $ubicacion['interiorUnidad']   = $InteriorUnidad }
    if ($Piso)             { $ubicacion['piso']             = $Piso }

    $cuerpo = @{
        tipoPropiedad = $TipoPropiedad
        uso           = $Uso
        descripcion   = $Descripcion
        ubicacion     = $ubicacion
        titulares     = @(@{ idPropietario = $IdPropietario; representante = $true })
        atributos     = $listaAtributos
        operaciones   = @($bloque)
    }
    if ($Codigo) { $cuerpo['codigo'] = $Codigo }
    $alta = Api POST '/propiedades' $Token $cuerpo

    [pscustomobject]@{
        id        = [long] $alta.idPropiedad
        codigo    = $alta.codigo
        idEncargo = [long] $alta.idsEncargos[0]
    }
}

# ---------------------------------------------------------------------
# La propiedad que TODAVIA NADIE HA ENCARGADO (V75).
#
# Registrar no es encargar: una propiedad puede existir en el registro maestro
# mientras se intenta captarla, y el encargo nace cuando el propietario acepta.
# Es lo que necesitan los guiones que arrancan de una prospeccion -f3, f4,
# f6-f7-: con un encargo ya abierto, `captar` chocaba contra
# `uq_captacion_viva_por_operacion`.
#
# Devuelve solo `id` y `codigo`: no hay `idEncargo` que devolver, y ese es el
# punto.
# ---------------------------------------------------------------------
function NuevoInmuebleSinEncargo {
    param(
        [Parameter(Mandatory = $true)] [string] $Token,
        [Parameter(Mandatory = $true)] [string] $Direccion,
        [Parameter(Mandatory = $true)] $IdPropietario,
        [string] $Codigo = $null,
        [string] $Distrito = 'Miraflores',
        [string] $TipoPropiedad = 'LOCAL',
        [string] $Uso = 'C',
        [string] $Descripcion = 'Registrada para prospectar',
        $Metraje = 90,
        [string] $Rubro = 'Retail',
        # `tipo_acceso` es ALT en LOCAL desde V81, y ALT impide publicar. Vacio
        # = no se registra, que es lo que necesita una suite que quiera probar
        # justamente el bloqueo.
        [string] $TipoAcceso = 'A_PIE_DE_CALLE',
        $Atributos = $null
    )

    $listaAtributos = @()
    if ($null -ne $Metraje) { $listaAtributos += @{ clave = 'metraje_total'; valor = "$Metraje" } }
    if ($Rubro) { $listaAtributos += @{ clave = 'rubro_permitido'; valor = $Rubro } }
    if ($Atributos) { foreach ($extra in $Atributos) { $listaAtributos += $extra } }

    # V81 hizo `tipo_acceso` ALT en LOCAL, y en este proyecto ALT NO es un aviso:
    # `clavesQueImpidenPublicar` filtra `exigencia in ('ALT','PUB')`, asi que un
    # local sin este dato queda registrado pero NO publicable, y toda suite que
    # publique se cae con 400. El fixture registra el dato, que es lo que haria un
    # agente que ha estado de pie en el local.
    #
    # LO QUE NO SE HACE AQUI, y es la parte que importa: no se baja la exigencia a
    # OPC, y no se cambia el tipo del inmueble a OFICINA para esquivarla. Lo
    # primero relaja la regla que el titular decidio; lo segundo cambia lo que la
    # suite dice estar probando. Arreglar el fixture es registrar el dato.
    $traeAcceso = $false
    if ($Atributos) {
        foreach ($extra in $Atributos) { if ($extra.clave -eq 'tipo_acceso') { $traeAcceso = $true } }
    }
    if ($TipoPropiedad -eq 'LOCAL' -and $TipoAcceso -and -not $traeAcceso) {
        $listaAtributos += @{ clave = 'tipo_acceso'; valor = $TipoAcceso }
    }

    $cuerpo = @{
        tipoPropiedad = $TipoPropiedad
        uso           = $Uso
        descripcion   = $Descripcion
        ubicacion     = @{ direccion = $Direccion; distrito = $Distrito }
        titulares     = @(@{ idPropietario = $IdPropietario; representante = $true })
        atributos     = $listaAtributos
        # Cero operaciones. La propiedad queda registrada y NO ofrecida.
        operaciones   = @()
    }
    if ($Codigo) { $cuerpo['codigo'] = $Codigo }
    $alta = Api POST '/propiedades' $Token $cuerpo

    [pscustomobject]@{
        id     = [long] $alta.idPropiedad
        codigo = $alta.codigo
    }
}

# La prospeccion, explicita. `POST /locales` la abria de rebote y los guiones se
# apoyaban en ese efecto lateral (`GET /prospecciones?idLocal` -> items[0]); el
# alta universal no crea prospecciones y lo dice en su propia documentacion.
function NuevaProspeccion {
    param(
        [Parameter(Mandatory = $true)] [string] $Token,
        [Parameter(Mandatory = $true)] $IdPropiedad,
        [string] $Observaciones = 'Prospeccion de guion E2E'
    )
    $p = Api POST '/prospecciones' $Token @{
        idLocal = $IdPropiedad; observaciones = $Observaciones
    }
    return [long] $p.id
}
