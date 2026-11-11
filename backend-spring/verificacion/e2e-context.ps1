# Contexto obligatorio para cualquier suite E2E que escriba.
# Una ejecucion directa, o que apunte al API/BD de desarrollo, falla antes de
# hacer el primer login o INSERT.

function Assert-ControlLocalE2EContext {
    $runId = $env:CONTROLLOCAL_E2E_RUN_ID
    $database = $env:CONTROLLOCAL_E2E_DB
    $baseUrl = $env:CONTROLLOCAL_E2E_BASE_URL
    $postgresContainer = $env:CONTROLLOCAL_E2E_POSTGRES_CONTAINER
    $apiContainer = $env:CONTROLLOCAL_E2E_API_CONTAINER

    if ([string]::IsNullOrWhiteSpace($runId) -or $runId -notmatch '^[a-z0-9-]+$') {
        throw 'Suite E2E bloqueada: ejecutala mediante verificacion/Invoke-E2E.ps1.'
    }
    if ($database -notmatch '^controllocal_e2e_[a-z0-9_]+$' -or $database -eq 'controllocal') {
        throw "Suite E2E bloqueada: la base '$database' no es una base exclusiva de prueba."
    }
    if ($baseUrl -notmatch '^http://localhost:\d+/controllocal/Api$' -or
        $baseUrl -eq 'http://localhost:8090/controllocal/Api') {
        throw "Suite E2E bloqueada: el API '$baseUrl' corresponde a desarrollo o no es valido."
    }
    if ($postgresContainer -notmatch '^controllocal-postgres-e2e-' -or
        $apiContainer -notmatch '^controllocal-api-e2e-') {
        throw 'Suite E2E bloqueada: los contenedores no son efimeros ni llevan el identificador de corrida.'
    }

    [pscustomobject]@{
        RunId = $runId
        Database = $database
        BaseUrl = $baseUrl
        PostgresContainer = $postgresContainer
        ApiContainer = $apiContainer
    }
}

# =====================================================================
# TOTP y enrolamiento — compartidos desde V37 (Bloque 6)
#
# POR QUE ESTO VIVE AQUI Y NO EN UNA SUITE. V37 marca `debe_enrolar_mfa`
# a todo TENANT_ADMIN, asi que su sesion nace CAPADA: solo alcanza el
# perfil, el enrolamiento y el logout. Cualquier suite que actue como
# administrador —y son casi todas— tiene que enrolar antes de operar.
#
# No se relaja la regla en el perfil de pruebas a proposito: hacerlo
# dejaria sin verificar justo lo que el bloque viene a garantizar, y las
# suites pasarian a probar un sistema que no es el que se despliega.
# =====================================================================

function ConvertFrom-Base32Rfc4648 {
    param([Parameter(Mandatory = $true)][string] $Texto)
    $alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $bits = 0; $valor = 0
    $salida = New-Object System.Collections.Generic.List[byte]
    foreach ($c in $Texto.ToUpper().ToCharArray()) {
        $indice = $alfabeto.IndexOf($c)
        if ($indice -lt 0) { continue }
        $valor = ($valor -shl 5) -bor $indice
        $bits += 5
        if ($bits -ge 8) {
            $salida.Add([byte](($valor -shr ($bits - 8)) -band 0xFF))
            $bits -= 8
        }
    }
    return $salida.ToArray()
}

function Get-PasoTotp { [long][math]::Floor(([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) / 30) }

function Get-CodigoTotp {
    param(
        [Parameter(Mandatory = $true)][string] $SecretoBase32,
        [long] $Paso = -1
    )
    if ($Paso -lt 0) { $Paso = Get-PasoTotp }
    $hmac = New-Object System.Security.Cryptography.HMACSHA1
    $hmac.Key = ConvertFrom-Base32Rfc4648 $SecretoBase32
    $contador = [BitConverter]::GetBytes([int64]$Paso)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($contador) }
    $hash = $hmac.ComputeHash($contador)
    $d = $hash[$hash.Length - 1] -band 0x0F
    $binario = ((($hash[$d] -band 0x7F) -shl 24) -bor (($hash[$d + 1] -band 0xFF) -shl 16) -bor
                (($hash[$d + 2] -band 0xFF) -shl 8) -bor ($hash[$d + 3] -band 0xFF))
    return ($binario % 1000000).ToString('000000')
}

<#
.SYNOPSIS
Deja una cuenta lista para operar: entra y, si su sesion esta capada por
`debe_enrolar_mfa`, enrola el segundo factor y vuelve a entrar por los dos
pasos.

.OUTPUTS
La sesion contextual actual. El factor queda privado en el proceso del
fixture; no se devuelve ni se incluye en evidencia.
#>

function Invoke-ControlLocalE2ESql {
    param([Parameter(Mandatory = $true)][string] $Consulta)
    $ctx = Assert-ControlLocalE2EContext
    # Por stdin: los JSON y las comillas no se reinterpretan como argumentos
    # nativos de Windows PowerShell 5.1. Nunca escribir contra desarrollo.
    $preferenciaPrevia = $ErrorActionPreference
    try {
        # Capturar stderr antes de informar: un error de alta de cuenta no
        # debe publicar la fila con su hash. El exit no-cero sigue fatal.
        $ErrorActionPreference = 'Continue'
        $salida = $Consulta | docker exec -i $ctx.PostgresContainer psql -X -q -t -A `
            -U controllocal -d $ctx.Database -v ON_ERROR_STOP=1 2>&1
        $codigoSalida = $LASTEXITCODE
    } finally { $ErrorActionPreference = $preferenciaPrevia }
    if ($codigoSalida -ne 0) { throw 'Fallo el SQL de fixture E2E; no continuar con datos parciales ni publicar filas/credenciales.' }
    return ($salida -join "`n")
}

function LeerErrorHttpDeFixture($fallo) {
    # Private transport body: callers parse it, never print it. A non-seekable
    # response is read once and cached on the same error, not in global state.
    $excepcion=$fallo.Exception
    if($excepcion.Data -and $excepcion.Data.Contains('BROX_E2E_CUERPO_ERROR')) {
        return [string]$excepcion.Data['BROX_E2E_CUERPO_ERROR']
    }
    if($fallo.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($fallo.ErrorDetails.Message)) {
        $texto=[string]$fallo.ErrorDetails.Message
        if($null -ne $excepcion.Data){$excepcion.Data['BROX_E2E_CUERPO_ERROR']=$texto}
        return $texto
    }
    $texto='';$flujo=$null;$lector=$null;$posicion=$null
    try {
        if($null -eq $excepcion.Response){return ''}
        $flujo=$excepcion.Response.GetResponseStream()
        if($null -eq $flujo){return ''}
        if($flujo.CanSeek){$posicion=$flujo.Position;$null=$flujo.Seek(0,[IO.SeekOrigin]::Begin)}
        $lector=[IO.StreamReader]::new($flujo,[Text.Encoding]::UTF8,$true,1024,$true)
        $texto=$lector.ReadToEnd()
    } catch { return '' }
    finally {
        if($lector){$lector.Dispose()}
        if($flujo -and $null -ne $posicion){try{$null=$flujo.Seek($posicion,[IO.SeekOrigin]::Begin)}catch{}}
    }
    if(-not [string]::IsNullOrWhiteSpace($texto)) {
        if($null -ne $excepcion.Data){$excepcion.Data['BROX_E2E_CUERPO_ERROR']=$texto}
        # Do not install the private body as ErrorDetails: PowerShell would
        # print it on an uncaught throw. Exception.Data is only a local cache.
    }
    return $texto
}

function MostrarErrorApiDeFixture($fallo, $metodo, $ruta, $token, $cuerpo) {
    $privados = New-Object 'Collections.Generic.List[string]'
    if($token){$privados.Add($token)}
    $sensibles = @('nombre','nombreTitularDeclarado','nombreRepresentanteDeclarado','nombreProfesionalDeclarado','telefono','correo','correoLaboral','correoPropio','correoCuenta','numeroDocumento','direccion','observaciones','descripcion','resumen','motivo','fuente','evidenciaRef','objetoOfrecido','contrasena','contrasenaActual','contrasenaNueva','pruebaRevision','eventoHumano','token','desafio','secreto')
    $recorrer = {
        param($dato)
        if($null -eq $dato -or $dato -is [string] -or $dato -is [ValueType]){return}
        if($dato -is [Collections.IDictionary]){
            foreach($clave in $dato.Keys){$v=$dato[$clave];if($clave -in $sensibles -and $v -is [string]){$privados.Add($v)}else{& $recorrer $v}}
        } elseif($dato -is [Collections.IEnumerable]){foreach($v in $dato){& $recorrer $v}}
        else {foreach($p in $dato.PSObject.Properties){if($p.Name -in $sensibles -and $p.Value -is [string]){$privados.Add($p.Value)}else{& $recorrer $p.Value}}}
    }
    & $recorrer $cuerpo
    $codigoHttp = if($fallo.Exception.Response){[int]$fallo.Exception.Response.StatusCode}else{0}
    $json = $null
    $textoError=LeerErrorHttpDeFixture $fallo
    if($textoError){try{$json=$textoError|ConvertFrom-Json}catch{$json=$null}}
    $mensaje = $null
    foreach($campo in @('mensaje','message','error')){if($json -and $json.$campo -is [string] -and -not [string]::IsNullOrWhiteSpace($json.$campo)){$mensaje=$json.$campo;break}}
    if(-not $mensaje){$mensaje=if($codigoHttp -eq 0){$fallo.Exception.Message}else{'Core rechazo la peticion sin mensaje JSON legible.'}}
    foreach($v in $privados){if($v){$mensaje=$mensaje.Replace($v,'[DATO_PROTEGIDO]')}}
    $mensaje=[regex]::Replace($mensaje,'eyJ[A-Za-z0-9_.-]+','[TOKEN_PROTEGIDO]')
    $mensaje=[regex]::Replace($mensaje,'(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b','[REFERENCIA_PROTEGIDA]')
    $mensaje=[regex]::Replace($mensaje,'(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43,}(?![A-Za-z0-9_-])','[REFERENCIA_PROTEGIDA]')
    $mensaje=[regex]::Replace($mensaje,'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}','[CORREO_PROTEGIDO]')
    if($mensaje.Length -gt 700){$mensaje=$mensaje.Substring(0,700)}
    $rutaSegura=($ruta -split '\?',2)[0]
    $rutaSegura=[regex]::Replace($rutaSegura,'(?i)[0-9a-f]{8}-[0-9a-f-]{27,}|[A-Za-z0-9_-]{43,}','[REFERENCIA_PROTEGIDA]')
    $codigoCore=if($json.codigo){[string]$json.codigo}elseif($json.codigoError){[string]$json.codigoError}else{$null}
    if($codigoCore -and $codigoCore -notmatch '^[A-Z0-9_/-]{1,80}$'){$codigoCore='CODIGO_NO_PUBLICABLE'}
    Write-Host ('E2E_API_ERROR '+(@{fuente=$script:FuenteFixtureE2E;etapa=$script:EtapaFixtureE2E;metodo=$metodo;ruta=$rutaSegura;http=$codigoHttp;codigoCore=$codigoCore;mensajeCore=$mensaje}|ConvertTo-Json -Compress))
    # PowerShell prints ErrorDetails when an error escapes the script. Keep
    # the original body in the private cache for ApiError, and only expose
    # the same sanitized diagnostic in that final automatic formatter.
    if($fallo -is [Management.Automation.ErrorRecord]) {
        $fallo.ErrorDetails=[Management.Automation.ErrorDetails]::new("HTTP $codigoHttp $metodo $rutaSegura - $mensaje")
    }
}

function Write-ControlLocalE2EHttpResult {
    param($Resultado)
    # Solo enumerar la coleccion EXTERIOR devuelta por JSON. Un DTO con
    # arrays internos o un item que sea array conserva su propia estructura.
    if($Resultado -is [object[]]) {
        foreach($item in $Resultado) { Write-Output -NoEnumerate $item }
    } else { Write-Output -NoEnumerate $Resultado }
}

function Get-SentenciaCuentaVerificadaE2E {
    param([string]$Usuario,[string]$Correo,[string]$Contrasena,[string]$Nombre)
    $null = Assert-ControlLocalE2EContext
    if(-not $Usuario -or $Usuario.Length -gt 60 -or $Correo -notlike '*@example.invalid' -or -not $Nombre -or -not $Contrasena) {
        throw 'La cuenta inicial de TEST requiere identidad ficticia y correo example.invalid; no migrar usuarios ni credenciales.'
    }
    $sal = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($sal) } finally { $rng.Dispose() }
    $pbkdf2 = New-Object Security.Cryptography.Rfc2898DeriveBytes($Contrasena,$sal,100000,[Security.Cryptography.HashAlgorithmName]::SHA256)
    try { $hash='pbkdf2$100000$'+[Convert]::ToBase64String($sal)+'$'+[Convert]::ToBase64String($pbkdf2.GetBytes(32)) }
    finally { $pbkdf2.Dispose() }
    $usuarioSql=$Usuario.Replace("'","''");$correoSql=$Correo.Replace("'","''");$nombreSql=$Nombre.Replace("'","''")
    # Fragmento privado consumido por las dos formas del mismo montaje TEST.
    # Nunca imprimirlo: contiene un hash ficticio generado para esta corrida.
    return "insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,nombre_profesional_declarado,contrasena_hash) values('$usuarioSql','$correoSql',clock_timestamp(),'$nombreSql','$hash') returning id_cuenta_acceso,nombre_usuario,correo_propio"
}

function NuevaCuentaVerificadaE2E {
    param([string]$Usuario,[string]$Correo,[string]$Contrasena,[string]$Nombre)
    $sentencia=Get-SentenciaCuentaVerificadaE2E $Usuario $Correo $Contrasena $Nombre
    $cuenta=Invoke-ControlLocalE2ESql @"
with nueva as ($sentencia)
select json_build_object('cuenta',id_cuenta_acceso,'usuario',nombre_usuario,'correo',correo_propio)
from nueva;
"@ | ConvertFrom-Json
    if($null -eq $cuenta -or $cuenta.cuenta -le 0){throw 'No se creo la cuenta ficticia de TEST.'}
    Write-Host ('E2E_CUENTA '+($cuenta|ConvertTo-Json -Compress))
    return $cuenta
}

function Initialize-ControlLocalE2EActors {
    param([string] $Etiqueta = 'principal')
    $ctx = Assert-ControlLocalE2EContext
    if ($Etiqueta -notmatch '^[a-z0-9]{1,10}$') { throw 'Etiqueta de fixture E2E invalida.' }
    if (-not $script:bancosActoresE2E) { $script:bancosActoresE2E = @{} }
    if ($script:bancosActoresE2E.ContainsKey($Etiqueta)) { return $script:bancosActoresE2E[$Etiqueta] }
    # Equivalente tecnico a OrigenesDePrueba. Empresa y personas NUEVAS de
    # TEST; no se copian identidades, credenciales, factores ni SID del seed.
    # codigo de Organizacion: maximo30. El RunId sigue en cuentas y evidencia.
    $codigo = 'E2E-' + [guid]::NewGuid().ToString('N').Substring(0,20)
    $especificaciones = @(
        @{ alias = 'admin'; rol = 'BROKER'; administrar = $true; clave = 'Admin2026' },
        @{ alias = 'rsalas'; rol = 'BROKER'; administrar = $false; clave = 'Broker2026' },
        @{ alias = 'psoto'; rol = 'BROKER'; administrar = $false; clave = 'Broker2026' },
        @{ alias = 'vmora'; rol = 'AGENTE'; administrar = $false; clave = 'Agente2026' },
        @{ alias = 'ltorres'; rol = 'AGENTE'; administrar = $false; clave = 'Agente2026' }
    )
    $sqlFixture = "begin;`ninsert into organizacion(codigo,nombre,modalidad,estado) values('$codigo','Empresa ficticia TEST E2E','EMPRESARIAL','I');`n"
    foreach ($actorFixture in $especificaciones) {
        $usuarioFixture = 'e2e-' + $actorFixture.alias + '-' + $ctx.RunId + '-' + $Etiqueta
        $correoFixture = $usuarioFixture + '@example.invalid'
        $sentenciaCuenta=Get-SentenciaCuentaVerificadaE2E $usuarioFixture $correoFixture $actorFixture.clave ('Profesional TEST E2E '+$actorFixture.alias)
        $administrar = if ($actorFixture.administrar) { 'true' } else { 'false' }
        $detalle = if ($actorFixture.rol -eq 'AGENTE') {
            "insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) select organizacion_id,id_persona_rol,'E2E-A-'||id_persona_rol,(clock_timestamp() at time zone 'America/Lima')::date,'D' from rol returning id_persona_rol"
        } else {
            "insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) select organizacion_id,id_persona_rol,'E2E-B-'||id_persona_rol,(clock_timestamp() at time zone 'America/Lima')::date from rol returning id_persona_rol"
        }
        $sqlFixture += @"
with org as (select id_organizacion from organizacion where codigo='$codigo'),
cuenta as ($sentenciaCuenta),
persona as (insert into persona(organizacion_id,nombres_o_razon_social) select id_organizacion,'Profesional TEST E2E $($actorFixture.alias)' from org returning id_persona,organizacion_id),
rol as (insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
 select organizacion_id,id_persona,'$($actorFixture.rol)',(clock_timestamp() at time zone 'America/Lima')::date from persona returning id_persona_rol,organizacion_id),
detalle as ($detalle),
gobierno as (insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
 select organizacion_id,id_persona,'ADMIN',(clock_timestamp() at time zone 'America/Lima')::date from persona where $administrar returning id_persona_rol)
insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado)
 select p.organizacion_id,c.id_cuenta_acceso,p.id_persona,'$($actorFixture.rol)',$administrar,'A' from persona p cross join cuenta c;
"@
    }
    $sqlFixture += @"
insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo)
select a.organizacion_id,b.id_persona_rol,a.id_persona_rol,(clock_timestamp() at time zone 'America/Lima')::date,'Supervision TEST E2E explicita'
from persona_rol a join persona pa on pa.id_persona=a.id_persona join persona_rol b on b.organizacion_id=a.organizacion_id and b.tipo_rol='BROKER'
join persona pb on pb.id_persona=b.id_persona join organizacion o on o.id_organizacion=a.organizacion_id
where o.codigo='$codigo' and a.tipo_rol='AGENTE' and
 ((pa.nombres_o_razon_social='Profesional TEST E2E vmora' and pb.nombres_o_razon_social='Profesional TEST E2E rsalas') or
  (pa.nombres_o_razon_social='Profesional TEST E2E ltorres' and pb.nombres_o_razon_social='Profesional TEST E2E psoto'));
update organizacion set estado='A' where codigo='$codigo';
select json_build_object('organizacion',o.id_organizacion,'codigo',o.codigo,'actores',
 (select json_object_agg(x.alias,json_build_object('correo',c.correo_propio,'cuenta',c.id_cuenta_acceso,'persona',u.id_persona,'rol',u.rol))
 from (values('admin'),('rsalas'),('psoto'),('vmora'),('ltorres')) x(alias)
 join cuenta_acceso c on c.nombre_usuario='e2e-'||x.alias||'-$($ctx.RunId)-$Etiqueta'
 join usuario_organizacion u on u.id_usuario=c.id_cuenta_acceso and u.organizacion_id=o.id_organizacion))
from organizacion o where o.codigo='$codigo';
commit;
"@
    $actoresCreado = (Invoke-ControlLocalE2ESql $sqlFixture) | ConvertFrom-Json
    $script:bancosActoresE2E[$Etiqueta] = $actoresCreado
    # Evidencia allowlist: identidades de TEST, sin password/hash/MFA/SID/token.
    Write-Host ('E2E_IDENTIDAD ' + ($actoresCreado | ConvertTo-Json -Depth 6 -Compress))
    return $actoresCreado
}

function Connect-ControlLocalE2E {
    param(
        [Parameter(Mandatory = $true)][string] $BaseUrl,
        [Parameter(Mandatory = $true)][string] $Correo,
        [Parameter(Mandatory = $true)][string] $Contrasena,
        [long] $Organizacion,
        [string] $Rol,
        [switch] $EnrolarSiempre
    )
    $null = Assert-ControlLocalE2EContext
    if ($Correo -notmatch '^[^@\s]+@[^@\s]+$' -or $Organizacion -le 0 -or -not $Rol) {
        throw 'El fixture requiere correo propio, organizacion y atribucion explicitos; no acepta alias ni primer contexto.'
    }
    if (-not $script:factoresE2E) { $script:factoresE2E = @{}; $script:pasosE2E = @{} }
    $flujo = @{ etapa='INICIO'; accesos=0; privados=(New-Object 'Collections.Generic.List[string]') }
    $flujo.privados.Add($Contrasena)
    $cuerpo = @{ correo = $Correo; contrasena = $Contrasena } | ConvertTo-Json
    try {
        $entrar = {
            $flujo.accesos = $flujo.accesos + 1
            $flujo.etapa = 'LOGIN_' + $flujo.accesos
            $login = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/mfa/desafio" -TimeoutSec 30 -ContentType 'application/json' -Body $cuerpo
            if ($login.token) { $flujo.privados.Add($login.token) }
            if ($login.desafio) {
                $flujo.privados.Add($login.desafio)
                if (-not $script:factoresE2E.ContainsKey($Correo)) { throw 'La cuenta tiene MFA sin material del fixture; no se evita el segundo factor.' }
                $flujo.privados.Add($script:factoresE2E[$Correo])
                $flujo.etapa = 'ESPERA_TOTP_' + $flujo.accesos
                while ($script:pasosE2E.ContainsKey($Correo) -and (Get-PasoTotp) -le $script:pasosE2E[$Correo]) { Start-Sleep -Milliseconds 250 }
                $paso = Get-PasoTotp
                $codigo = Get-CodigoTotp $script:factoresE2E[$Correo] $paso
                $flujo.privados.Add($codigo)
                $flujo.etapa = 'VERIFICAR_MFA_' + $flujo.accesos
                $login = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/mfa/verificar" -TimeoutSec 30 -ContentType 'application/json' `
                    -Body (@{ desafio = $login.desafio; codigo = $codigo } | ConvertTo-Json)
                if ($login.token) { $flujo.privados.Add($login.token) }
                $script:pasosE2E[$Correo] = $paso
            }
            $flujo.etapa = 'CONTEXTOS_' + $flujo.accesos
            $respuestaContextos = Invoke-RestMethod -Method GET -Uri "$BaseUrl/sesion/contextos" -TimeoutSec 30 -Headers @{ Authorization = "Bearer $($login.token)" }
            # PS5.1 puede emitir el arrayJSON como un solo objeto. Enumerarlo
            # antes de filtrar evita escoger un wrapper con dos atribuciones.
            $opciones = @($respuestaContextos | ForEach-Object { $_ })
            $elegibles = @($opciones | Where-Object { $_.idOrganizacion -eq $Organizacion -and $_.rol -eq $Rol })
            if ($elegibles.Count -ne 1) { throw 'No existe una atribucion contextual unica para el actor declarado de TEST.' }
            $referenciaElegida = $elegibles[0].referencia
            if ($referenciaElegida -isnot [string] -or [string]::IsNullOrWhiteSpace($referenciaElegida)) {
                throw 'Core no ofrecio una referencia contextual escalar; no convertir ni escoger la primera.'
            }
            $flujo.privados.Add($referenciaElegida)
            $flujo.etapa = 'SELECCION_' + $flujo.accesos
            $seleccion = Invoke-RestMethod -Method POST -Uri "$BaseUrl/sesion/contexto" -TimeoutSec 30 -ContentType 'application/json' `
                -Headers @{ Authorization = "Bearer $($login.token)" } -Body (@{ referencia = $referenciaElegida } | ConvertTo-Json)
            if ($seleccion.token) { $flujo.privados.Add($seleccion.token) }
            return $seleccion
        }
        $sesion = & $entrar
        $flujo.etapa = 'ESTADO_MFA'
        $estado = Invoke-RestMethod -Method GET -Uri "$BaseUrl/cuenta/mfa" -TimeoutSec 30 -Headers @{ Authorization = "Bearer $($sesion.token)" }
        if ($estado.debeEnrolar -or ($EnrolarSiempre -and -not $estado.activo)) {
            $flujo.etapa = 'ENROLAR_MFA'
            $enrolamiento = Invoke-RestMethod -Method POST -Uri "$BaseUrl/cuenta/mfa" -TimeoutSec 30 -ContentType 'application/json' -Body '{}' `
                -Headers @{ Authorization = "Bearer $($sesion.token)" }
            $flujo.privados.Add($enrolamiento.secreto)
            $paso = Get-PasoTotp
            $codigo = Get-CodigoTotp $enrolamiento.secreto $paso
            $flujo.privados.Add($codigo)
            $flujo.etapa = 'CONFIRMAR_MFA'
            Invoke-RestMethod -Method POST -Uri "$BaseUrl/cuenta/mfa/confirmar" -TimeoutSec 30 -ContentType 'application/json' `
                -Body (@{ codigo = $codigo } | ConvertTo-Json) -Headers @{ Authorization = "Bearer $($sesion.token)" } | Out-Null
            $script:factoresE2E[$Correo] = $enrolamiento.secreto
            $script:pasosE2E[$Correo] = $paso
            $sesion = & $entrar
        }
        return $sesion
    } catch {
        $fallo = $_
        $codigoHttp = 0
        if ($fallo.Exception.Response) { $codigoHttp = [int]$fallo.Exception.Response.StatusCode }
        $errorCore = $null
        $textoError=LeerErrorHttpDeFixture $fallo
        if ($textoError) { try { $errorCore = $textoError | ConvertFrom-Json } catch { $errorCore = $null } }
        $mensaje = $null
        foreach ($campo in @('mensaje','message','error')) {
            if ($errorCore -and $errorCore.$campo -is [string] -and -not [string]::IsNullOrWhiteSpace($errorCore.$campo)) { $mensaje=$errorCore.$campo; break }
        }
        if (-not $mensaje) { $mensaje = if($codigoHttp -eq 0){$fallo.Exception.Message}else{'Core rechazo la peticion sin mensaje JSON legible.'} }
        foreach($privado in $flujo.privados) { if($privado){$mensaje=$mensaje.Replace($privado,'[PROTEGIDO]')} }
        $mensaje = [regex]::Replace($mensaje,'eyJ[A-Za-z0-9_.-]+','[TOKEN_PROTEGIDO]')
        $mensaje = [regex]::Replace($mensaje,'(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b','[REFERENCIA_PROTEGIDA]')
        $mensaje = [regex]::Replace($mensaje,'(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43,}(?![A-Za-z0-9_-])','[REFERENCIA_PROTEGIDA]')
        if($mensaje.Length -gt 700){$mensaje=$mensaje.Substring(0,700)}
        $codigoCore = if($errorCore.codigo){[string]$errorCore.codigo}elseif($errorCore.codigoError){[string]$errorCore.codigoError}else{$null}
        if($codigoCore -and $codigoCore -notmatch '^[A-Z0-9_/-]{1,80}$'){$codigoCore='CODIGO_NO_PUBLICABLE'}
        Write-Host ('E2E_IDENTIDAD_ERROR '+(@{etapa=$flujo.etapa;rol=$Rol;http=$codigoHttp;codigoCore=$codigoCore;mensajeCore=$mensaje}|ConvertTo-Json -Compress))
        throw [InvalidOperationException]::new("Fallo de identidad E2E en $($flujo.etapa) (HTTP $codigoHttp): $mensaje")
    }
}
# El alta de un inmueble con su encargo, comun a todas las suites. Vive aparte
# porque el Corte 0A retiro `POST /locales` y diez guiones lo llamaban: con la
# funcion en un solo fichero, la siguiente puerta que se retire se cambia una
# vez y no diez. Llama a la funcion `Api` de cada guion -PowerShell resuelve el
# nombre al invocar, no al cargar-, asi que no importa que se defina despues.
. "$PSScriptRoot/lib-alta-inmueble.ps1"
. "$PSScriptRoot/lib-interacciones-c4.ps1"
. "$PSScriptRoot/lib-formalizacion-c5.ps1"
