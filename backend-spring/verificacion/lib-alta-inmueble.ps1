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

    $alta = Api POST '/propiedades' $Token @{
        tipoPropiedad = $TipoPropiedad
        uso           = $Uso
        descripcion   = $Descripcion
        ubicacion     = $ubicacion
        titulares     = @(@{ idPropietario = $IdPropietario; representante = $true })
        atributos     = $listaAtributos
        operaciones   = @($bloque)
    }

    [pscustomobject]@{
        id        = [long] $alta.idPropiedad
        codigo    = $alta.codigo
        idEncargo = [long] $alta.idsEncargos[0]
    }
}
