# Fixture C2 por las puertas vigentes de Core, para los guiones E2E.
# Abrir el registro fisico y una preparacion no acredita aceptacion ni habilita
# el Encargo. HabilitarEncargoC2DePrueba simula EXPRESAMENTE las partes TEST,
# conserva documento/evidencia/version y obtiene la decision real de Core.
#
# e2e-context dota de fuente comun estas funciones; Api sigue siendo el
# transporte HTTP del guion. Este fixture no sustituye reglas de Core.

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
        # Acceso conocido del escenario TEST; null conserva el desconocimiento.
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
        [string] $Piso = $null,
        [switch] $LocalIndependienteTest
    )

    $listaAtributos = @()
    if ($null -ne $Metraje) { $listaAtributos += @{ clave = 'metraje_total'; valor = "$Metraje" } }
    if ($Rubro) { $listaAtributos += @{ clave = 'rubro_permitido'; valor = $Rubro } }
    if ($Atributos) { foreach ($extra in $Atributos) { $listaAtributos += $extra } }

    # Dato conocido del escenario TEST. TipoAcceso null permite probar
    # preparaciones incompletas; la suficiencia la decide Core.
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
    if ($alta.idPropiedad -le 0 -or @($alta.idsEncargos).Count -ne 1) {
        throw 'El alta C2 no devolvio una propiedad y exactamente una preparacion de Encargo.'
    }
    if ($LocalIndependienteTest) {
        if ($TipoPropiedad -ne 'LOCAL' -or $Uso -ne 'C' -or $Piso -or $InteriorUnidad) {
            throw 'El caso TEST de local independiente no se aplica a otro tipo, uso o unidad de edificio.'
        }
        DeclararLocalIndependienteC2DePrueba -IdPropiedad $alta.idPropiedad -Token $Token -Metraje $Metraje -Operacion $Operacion
    }

    [pscustomobject]@{
        id        = [long] $alta.idPropiedad
        codigo    = $alta.codigo
        idEncargo = [long] $alta.idsEncargos[0]
    }
}

function DeclararLocalIndependienteC2DePrueba {
    param([long] $IdPropiedad, [string] $Token, $Metraje, $Operacion)
    # Hechos elegidos EXPRESAMENTE por el llamante TEST con el switch, no
    # deducidos de una direccion ni propuestos como defecto del producto.
    $script:FuenteFixtureE2E = 'lib-alta-inmueble.ps1'
    $script:EtapaFixtureE2E = 'LEER_FISICO_PARA_DECLARACION'
    $actual = Api GET "/propiedades/$IdPropiedad" $Token $null
    if([string]::IsNullOrWhiteSpace($actual.ubicacion.direccion)){throw 'El caso TEST requiere identificar su objeto con la direccion real ya registrada.'}
    $fuente = 'Declaracion expresa TEST E2E: local independiente sin potencial urbanistico ofrecido.'
    $atributos = @(
        @{ clave = 'contexto.unidadDeEdificio'; naturaleza = 'DECLARADO'; evidenciaRef = $fuente; captura = @{ estado = 'NO_APLICABLE' } },
        @{ clave = 'contexto.potencialOCambioUso'; naturaleza = 'DECLARADO'; evidenciaRef = $fuente; captura = @{ estado = 'NO_APLICABLE' } }
    )
    if ($null -ne $Metraje) {
        $atributos += @{ clave = 'metraje_total'; valor = ([decimal]$Metraje).ToString([Globalization.CultureInfo]::InvariantCulture)
            naturaleza = 'DECLARADO'; evidenciaRef = 'Area total declarada por el fixture TEST E2E.'
            captura = @{ estado = 'CONOCIDO'; concepto = 'AREA_TOTAL' } }
    }
    $script:EtapaFixtureE2E = 'DECLARAR_CONTEXTO_FISICO'
    $null = Api PUT "/propiedades/$IdPropiedad" $Token @{
        versionEsperada = $actual.versionFisica; atributos = $atributos
        contextoCaptura = @{ tipoPropiedad = 'L'; usoOfrecido = 'C'; objetoOfrecido = $actual.ubicacion.direccion
            operacion = $Operacion; aplicabilidad = @{ unidadDeEdificio = 'NO_APLICA'; potencialOCambioUso = 'NO_APLICA' }; prestacionesOfrecidas = @() }
    }
}

function NuevoPropietarioC2DePrueba {
    param([Parameter(Mandatory = $true)][string] $Token, [Parameter(Mandatory = $true)][string] $Nombre)
    $null = Assert-ControlLocalE2EContext
    $ref = [guid]::NewGuid().ToString('N')
    Api POST '/propietarios' $Token @{
        tipoPersona = 'N'; tipoDocumento = 'P'; numeroDocumento = ('E2E-' + $ref.Substring(0, 20))
        nombre = $Nombre; telefono = '900000001'; correo = ('e2e-propietario-' + $ref + '@example.invalid')
        consentimientoUsoDato = $true; estado = 'A'
    }
}

function Invoke-ComandoC2DePrueba {
    param([string] $Metodo, [string] $Ruta, [string] $Token, $Datos, [string] $Clave, [switch] $EsperarError)
    $null = Assert-ControlLocalE2EContext
    if (-not $Clave) { throw 'El comando de fixture C2 requiere su clave explicita.' }
    try {
        $respuesta = Invoke-RestMethod -Method $Metodo -Uri "$base$Ruta" -TimeoutSec 90 -ContentType 'application/json' `
            -Headers @{ Authorization = "Bearer $Token"; 'Idempotency-Key' = $Clave; 'X-Origen' = 'API' } `
            -Body ($Datos | ConvertTo-Json -Depth 30 -Compress)
        if ($EsperarError) { return @{ codigo = 0; error = 'La peticion no fue rechazada.' } }
        return $respuesta
    } catch {
        if (-not $EsperarError -or $null -eq $_.Exception.Response) { MostrarErrorApiDeFixture $_ $Metodo $Ruta $Token $Datos; throw }
        return @{ codigo = [int]$_.Exception.Response.StatusCode; error = (LeerErrorHttpDeFixture $_) }
    }
}

function LeerArchivoPrivadoC2DePrueba {
    param([string]$Token,[guid]$Referencia,[string]$Destino)
    $null=Assert-ControlLocalE2EContext
    if($Referencia -eq [guid]::Empty){throw 'La lectura privada requiere referencia Core real.'}
    $temporal=[string]::IsNullOrWhiteSpace($Destino)
    if($temporal){$Destino=[IO.Path]::GetTempFileName()}
    $script:FuenteFixtureE2E='lib-alta-inmueble.ps1'
    $script:EtapaFixtureE2E='C2_ENTREGAR_BINARIO_PRIVADO'
    try {
        $r=Invoke-WebRequest -UseBasicParsing -Method GET -Uri "$base/documentos/contenido?clave=$Referencia" `
            -Headers @{Authorization="Bearer $Token"} -OutFile $Destino -PassThru -TimeoutSec 90
        if([int]$r.StatusCode -ne 200 -or -not (Test-Path -LiteralPath $Destino) -or (Get-Item -LiteralPath $Destino).Length -le 0){
            throw 'La entrega privada no produjo el instrumento; no se declara revisado.'
        }
        return [pscustomobject]@{bytes=(Get-Item -LiteralPath $Destino).Length;huella=(Get-FileHash -LiteralPath $Destino -Algorithm SHA256).Hash.ToLowerInvariant();tipo=[string]$r.Headers['Content-Type']}
    } catch {MostrarErrorApiDeFixture $_ 'GET' '/documentos/contenido' $Token $null;throw
    } finally {if($temporal -and (Test-Path -LiteralPath $Destino)){Remove-Item -LiteralPath $Destino -Force}}
}

function CrearPdfManuscritoC2DePrueba {
    param([string]$Token,$Documento,[object[]]$Partes)
    $ctx=Assert-ControlLocalE2EContext
    $java=$env:CONTROLLOCAL_E2E_JAVA
    $cp=$env:CONTROLLOCAL_E2E_FIRMA_CLASSPATH
    if([string]::IsNullOrWhiteSpace($java) -or -not [IO.Path]::IsPathRooted($java) -or -not (Test-Path -LiteralPath $java -PathType Leaf) -or [string]::IsNullOrWhiteSpace($cp)){
        throw 'Falta el productor PDF TEST preparado/verificado por Root; no se inventa un instrumento ni se renombra TXT.'
    }
    if($Partes.Count -lt 1){throw 'El instrumento TEST requiere las partes exactas ya documentadas.'}
    $carpeta=Join-Path ([IO.Path]::GetTempPath()) ('brox-pdf-test-'+$ctx.RunId+'-'+[guid]::NewGuid().ToString('N'))
    $null=[IO.Directory]::CreateDirectory($carpeta)
    $original=Join-Path $carpeta 'original-core.pdf'
    $salida=Join-Path $carpeta 'instrumento-test.pdf'
    try {
        $meta=LeerArchivoPrivadoC2DePrueba -Token $Token -Referencia $Documento.referencia -Destino $original
        if($meta.tipo -notlike 'application/pdf*'){throw 'Core no entrego el PDF original para el caso TEST.'}
        $argumentos=@('-cp',$cp,'com.controllocal.integracion.soporte.DocumentoManuscritoDePrueba',$original,$salida)
        for($i=1;$i -le $Partes.Count;$i++){$argumentos+=('Firmante TEST '+$i)}
        $preferenciaPrevia=$ErrorActionPreference
        try {$ErrorActionPreference='Continue';$resultado=& $java @argumentos 2>&1;$codigo=$LASTEXITCODE}
        finally {$ErrorActionPreference=$preferenciaPrevia}
        if($codigo -ne 0 -or -not (Test-Path -LiteralPath $salida -PathType Leaf) -or ($resultado -join "`n") -notmatch '(?m)^PDF_MANUSCRITO_TEST_OK bytes=\d+\s*$'){
            throw 'Fallo el productor PDF TEST unico; no hay fallback ni firma acreditada.'
        }
        $bytes=[IO.File]::ReadAllBytes($salida)
        if($bytes.Length -lt 5 -or [Text.Encoding]::ASCII.GetString($bytes,0,5) -ne '%PDF-' -or (Get-FileHash -LiteralPath $salida).Hash.ToLowerInvariant() -eq $meta.huella){
            throw 'El productor no conservo un PDF TEST transformado valido.'
        }
        Write-Host 'E2E_PDF_TEST original Core conservado y pagina de trazos FICTICIOS anadida por el productor unico.'
        return ,$bytes
    } finally {
        foreach($p in @($original,$salida)){if(Test-Path -LiteralPath $p){Remove-Item -LiteralPath $p -Force}}
        if(Test-Path -LiteralPath $carpeta){[IO.Directory]::Delete($carpeta,$false)}
    }
}

function AdjuntarArchivoC2DePrueba {
    param([long] $Encargo, $Documento, [string] $Finalidad, [string] $Nombre, $Contenido, [string] $Token, [string]$TipoContenido)
    $null = Assert-ControlLocalE2EContext
    if(-not $TipoContenido){throw 'El escenario TEST declara el tipo real del archivo.'}
    if($Contenido -is [byte[]]){$bytesArchivo=$Contenido}elseif($Contenido -is [string]){$bytesArchivo=[Text.Encoding]::UTF8.GetBytes($Contenido)}else{throw 'El archivo TEST requiere bytes reales o texto declarado.'}
    $script:FuenteFixtureE2E='lib-alta-inmueble.ps1'
    $script:EtapaFixtureE2E='C2_ARCHIVO_'+$Finalidad
    $actual = Api GET "/encargos/$Encargo" $Token $null
    Add-Type -AssemblyName System.Net.Http
    $clienteHttp = New-Object Net.Http.HttpClient
    $formulario = New-Object Net.Http.MultipartFormDataContent
    try {
        $clienteHttp.DefaultRequestHeaders.Authorization = New-Object Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        $clienteHttp.DefaultRequestHeaders.Add('Idempotency-Key', ('e2e-c2-carga-' + [guid]::NewGuid().ToString('N')))
        $clienteHttp.DefaultRequestHeaders.Add('X-Origen', 'API')
        $formulario.Add((New-Object Net.Http.StringContent([string]$Encargo)), 'encargo')
        if ($null -ne $Documento) { $formulario.Add((New-Object Net.Http.StringContent([string]$Documento)), 'documento') }
        $formulario.Add((New-Object Net.Http.StringContent($Finalidad)), 'finalidad')
        $formulario.Add((New-Object Net.Http.StringContent([string]$actual.version)), 'versionDestino')
        $binario = New-Object Net.Http.ByteArrayContent(,$bytesArchivo)
        $binario.Headers.ContentType = New-Object Net.Http.Headers.MediaTypeHeaderValue($TipoContenido)
        $formulario.Add($binario, 'archivo', $Nombre)
        $respuesta = $clienteHttp.PostAsync("$base/encargos/archivos/cargas", $formulario).GetAwaiter().GetResult()
        try {
            $texto = $respuesta.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $respuesta.IsSuccessStatusCode) {
                $falloHttp=[pscustomobject]@{Exception=[pscustomobject]@{Response=[pscustomobject]@{StatusCode=$respuesta.StatusCode}};ErrorDetails=[pscustomobject]@{Message=$texto}}
                MostrarErrorApiDeFixture $falloHttp 'POST' '/encargos/archivos/cargas' $Token $null
                throw "La carga C2 fue rechazada (HTTP $([int]$respuesta.StatusCode)); no se inventa evidencia."
            }
            $carga = $texto | ConvertFrom-Json
        } finally { $respuesta.Dispose() }
    } finally { $formulario.Dispose(); $clienteHttp.Dispose() }
    Invoke-ComandoC2DePrueba POST '/encargos/archivos' $Token @{
        referenciaOrigen = $carga.referencia; encargo = $Encargo; documento = $Documento
        finalidad = $Finalidad; versionDestino = $actual.version
    } ('e2e-c2-archivo-' + [guid]::NewGuid().ToString('N'))
}

function PrepararPeticionC2DePrueba {
    param(
        [Parameter(Mandatory = $true)][long] $IdEncargo,
        [Parameter(Mandatory = $true)][string] $TokenResponsable,
        [Parameter(Mandatory = $true)][string] $MonedaComision,
        [Parameter(Mandatory = $true)][bool] $Exclusividad,
        [Parameter(Mandatory = $true)][string] $HitoDevengo,
        [Parameter(Mandatory = $true)][string] $ParteObligada,
        [Parameter(Mandatory = $true)][string] $Beneficiario,
        [Parameter(Mandatory = $true)][string] $Exigibilidad,
        [string] $FechaFirma,
        $EjecucionComision = $null
    )
    $ctx = Assert-ControlLocalE2EContext
    if(-not $FechaFirma){throw 'El caso TEST declara su fecha de firma; no se completa una fecha desconocida.'}
    $script:FuenteFixtureE2E='lib-alta-inmueble.ps1'
    $script:EtapaFixtureE2E='C2_LEER_PACTO'
    # Puerto equivalente a EncargosDePrueba.presentar/habilitarExistente.
    # Son hechos/evidencias FICTICIOS de TEST; no acreditan aceptacion externa.
    $ficha = Api GET "/encargos/$IdEncargo" $TokenResponsable $null
    $pacto = $ficha.condiciones
    if ($null -eq $pacto -or $null -eq $pacto.precioPedido -or -not $pacto.monedaPrecio `
            -or -not $pacto.tipoComision -or $null -eq $pacto.valorComision) {
        throw 'El fixture debe declarar su precio, moneda y regla de comision; no se completan por magnitud o intuicion.'
    }
    # Mismos importes/tipo/base elegidos por el caso; no adaptar porcentajes.
    $pacto | Add-Member -NotePropertyName hitoDevengo -NotePropertyValue $HitoDevengo -Force
    $pacto | Add-Member -NotePropertyName parteObligada -NotePropertyValue $ParteObligada -Force
    $pacto | Add-Member -NotePropertyName beneficiario -NotePropertyValue $Beneficiario -Force
    $pacto | Add-Member -NotePropertyName exigibilidad -NotePropertyValue $Exigibilidad -Force
    $pacto | Add-Member -NotePropertyName monedaComision -NotePropertyValue $MonedaComision -Force
    $pacto | Add-Member -NotePropertyName procedencia -NotePropertyValue 'Declaracion explicita TEST E2E C2' -Force
    if($null -ne $EjecucionComision){$pacto | Add-Member -NotePropertyName ejecucion -NotePropertyValue $EjecucionComision -Force}
    $script:EtapaFixtureE2E='C2_COMPLETAR_PACTO'
    $ficha = Invoke-ComandoC2DePrueba PUT "/encargos/$IdEncargo/condiciones" $TokenResponsable @{
        versionEsperada = $ficha.version; condiciones = $pacto; motivo = 'Completar pacto explicito del fixture TEST C2.'
    } ('e2e-c2-pacto-' + [guid]::NewGuid().ToString('N'))
    $titulo = AdjuntarArchivoC2DePrueba $IdEncargo $null 'TITULARIDAD_ENCARGO' 'titularidad-test.txt' `
        "Declaracion explicita de titularidad de las partes ficticias TEST E2E del inmueble $($ficha.idPropiedad)." $TokenResponsable 'text/plain'
    $sesionResponsable = Invoke-RestMethod -Method GET -Uri "$base/sesion" -TimeoutSec 30 -Headers @{ Authorization = "Bearer $TokenResponsable" }
    $partesJson = Invoke-ControlLocalE2ESql @"
select coalesce(json_agg(json_build_object('titularidad',t.id_titularidad,'persona',r.id_persona,'cuota',t.cuota,
 'evidenciaTitularidad','$($titulo.referencia)','nombreTitularDeclarado',p.nombres_o_razon_social) order by t.id_titularidad),'[]'::json)
from titularidad_propiedad t join persona_rol r on r.id_persona_rol=t.id_rol_propietario and r.organizacion_id=t.organizacion_id
join persona p on p.id_persona=r.id_persona and p.organizacion_id=r.organizacion_id
where t.organizacion_id=$($sesionResponsable.idOrganizacion) and t.id_propiedad=$($ficha.idPropiedad) and t.vigente_hasta is null;
"@
    $partesLeidas = $partesJson | ConvertFrom-Json
    $partes = @($partesLeidas | ForEach-Object { $_ })
    if ($partes.Count -eq 0) { throw 'El fixture C2 requiere titulares expresos; no se fabrican partes para habilitar.' }
    foreach($parte in $partes) {
        if($null -eq $parte.titularidad -or $null -eq $parte.persona -or [string]::IsNullOrWhiteSpace($parte.nombreTitularDeclarado)) {
            throw 'La parte real del fixture no tiene identidad/nombre documentados; no se completan con un rotulo de relleno.'
        }
    }
    $ficha = Api GET "/encargos/$IdEncargo" $TokenResponsable $null
    $script:EtapaFixtureE2E='C2_PREPARAR_DOCUMENTO'
    $documento = Invoke-ComandoC2DePrueba POST '/encargos/documentos' $TokenResponsable @{
        encargo = $IdEncargo; versionEncargo = $ficha.version; idPropiedad = $ficha.idPropiedad
        partes = $partes; operacion = $ficha.operacion; unidades = @(); condiciones = $pacto
        desde = $ficha.desde; hasta = $ficha.hasta; exclusividad = $Exclusividad
    } ('e2e-c2-documento-' + [guid]::NewGuid().ToString('N'))
    if (@($documento.faltantes).Count -gt 0) { throw ('El fixture no es suficiente para habilitar Encargo: ' + ($documento.faltantes -join ', ')) }
    $pdfTest=CrearPdfManuscritoC2DePrueba -Token $TokenResponsable -Documento $documento -Partes $partes
    $aceptacion=AdjuntarArchivoC2DePrueba $IdEncargo $documento.id 'ACEPTACION_ENCARGO' 'instrumento-manuscrito-TEST.pdf' $pdfTest $TokenResponsable 'application/pdf'
    $firmas=@(foreach($parte in $partes){
        @{firmante=@{titularidad=$parte.titularidad;persona=$parte.persona};fechaFirma=$FechaFirma;documentoFirmado=$aceptacion.referencia
          descripcion='Instrumento del escenario TEST con trazos manuscritos FICTICIOS; no acredita firma externa real.'}
    })
    $ficha=Api GET "/encargos/$IdEncargo" $TokenResponsable $null
    $script:EtapaFixtureE2E='C2_PRESENTAR_FIRMAS'
    $peticion=Invoke-ComandoC2DePrueba POST "/encargos/$IdEncargo/peticiones" $TokenResponsable @{
        documento=$documento.id;versionEsperada=$ficha.version;partes=$partes;firmas=$firmas
    } ('e2e-c2-presentar-'+[guid]::NewGuid().ToString('N'))
    return [pscustomobject]@{encargo=$ficha;documento=$documento;partes=$partes;peticion=$peticion}
}

function AprobarPeticionC2DePrueba {
    param(
        [Parameter(Mandatory = $true)][long] $IdEncargo,
        [Parameter(Mandatory = $true)][long] $IdPeticion,
        [Parameter(Mandatory = $true)] $Documento,
        [Parameter(Mandatory = $true)][string] $TokenDecisor
    )
    $peticion=Api GET "/encargos/peticiones/$IdPeticion" $TokenDecisor $null
    if($null -eq $peticion.firma){throw 'No hay constancia de firma consultable para revisar; no se declara revisada.'}
    if($peticion.firma.documento -ne $Documento.id){throw 'El documento consultado no es el de la peticion exacta que se revisa.'}
    $null=LeerArchivoPrivadoC2DePrueba -Token $TokenDecisor -Referencia $Documento.referencia
    foreach($archivo in $peticion.firma.archivos){
        $leido=LeerArchivoPrivadoC2DePrueba -Token $TokenDecisor -Referencia $archivo.referencia
        if($leido.huella -ne $archivo.huella){throw 'El instrumento entregado no coincide con su referencia/version/huella actual.'}
    }
    $revision=Api GET "/encargos/$IdEncargo" $TokenDecisor $null
    # Declaracion de revision del operador ficticio, vinculada a la propuesta
    # exacta. Nunca es efecto: decide la confirmacion humana canonica unica.
    $script:EtapaFixtureE2E='C2_PREPARAR_DECISION_HUMANA'
    $propuesta=Invoke-ComandoC2DePrueba POST "/encargos/peticiones/$($peticion.id)/decision" $TokenDecisor @{
        actuacion='APROBAR';versionPeticion=$peticion.version;versionEncargo=$revision.version
        observaciones='Revision competente del instrumento FICTICIO del escenario TEST C2.'
        revisionFirma=@{revisionPresentacion=$peticion.firma.revisionPresentacion;huellaManifestacion=$peticion.firma.huellaManifestacion;documentoYFirmantesRevisados=$true}
    } ('e2e-c2-decidir-'+[guid]::NewGuid().ToString('N'))
    $resuelta=ConfirmarActuacionDePrueba -Token $TokenDecisor -Propuesta $propuesta
    if($resuelta.id -ne $peticion.id -or $resuelta.estado -ne 'APROBADA'){throw 'La confirmacion no aprobo la peticion exacta del fixture.'}
    return $resuelta
}

function HabilitarEncargoC2DePrueba {
    param(
        [Parameter(Mandatory = $true)][long] $IdEncargo,
        [Parameter(Mandatory = $true)][string] $TokenResponsable,
        [Parameter(Mandatory = $true)][string] $TokenDecisor,
        [Parameter(Mandatory = $true)][string] $MonedaComision,
        [Parameter(Mandatory = $true)][bool] $Exclusividad,
        [Parameter(Mandatory = $true)][string] $HitoDevengo,
        [Parameter(Mandatory = $true)][string] $ParteObligada,
        [Parameter(Mandatory = $true)][string] $Beneficiario,
        [Parameter(Mandatory = $true)][string] $Exigibilidad,
        [string] $FechaFirma,
        $EjecucionComision = $null
    )
    $datos=@{};foreach($k in $PSBoundParameters.Keys){if($k -ne 'TokenDecisor'){$datos[$k]=$PSBoundParameters[$k]}}
    $preparada=PrepararPeticionC2DePrueba @datos
    $peticion=AprobarPeticionC2DePrueba -IdEncargo $IdEncargo -IdPeticion $preparada.peticion.id -Documento $preparada.documento -TokenDecisor $TokenDecisor
    $habilitado=Api GET "/encargos/$IdEncargo" $TokenResponsable $null
    if(-not $habilitado.vigencia.habilitado){throw 'Core no habilito el Encargo; no se suplanta estado por SQL.'}
    Write-Host ("E2E_ENCARGO id=$IdEncargo version=$($habilitado.version) documento=$($preparada.documento.id) peticion=$($peticion.id) estado=$($habilitado.estado)")
    return $habilitado
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
        $Atributos = $null,
        [switch] $LocalIndependienteTest
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
    $script:FuenteFixtureE2E = 'lib-alta-inmueble.ps1'
    $script:EtapaFixtureE2E = 'REGISTRAR_FISICO_SIN_ENCARGO'
    $alta = Api POST '/propiedades' $Token $cuerpo

    if ($LocalIndependienteTest) {
        if ($TipoPropiedad -ne 'LOCAL' -or $Uso -ne 'C') { throw 'El caso TEST independiente requiere un local comercial declarado.' }
        DeclararLocalIndependienteC2DePrueba -IdPropiedad $alta.idPropiedad -Token $Token -Metraje $Metraje -Operacion $null
    }

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
    $script:FuenteFixtureE2E = 'lib-alta-inmueble.ps1'
    $script:EtapaFixtureE2E = 'REGISTRAR_PROSPECCION'
    $p = Api POST '/prospecciones' $Token @{
        idLocal = $IdPropiedad; observaciones = $Observaciones
    }
    return [long] $p.id
}
