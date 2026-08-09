<#
.SINOPSIS
    Copia de seguridad de PostgreSQL v2 con checksum, manifiesto y retencion.

.DESCRIPCION
    BLOQUE 1.2 del plan maestro. Genera un respaldo COMPLETO (esquema + datos +
    historial de Flyway) en formato custom de pg_dump, calcula su SHA-256,
    escribe un manifiesto con los metadatos que hacen falta para confiar en el
    archivo, y purga los respaldos mas viejos que la retencion.

    Dos decisiones que conviene conocer:

    1) El dump se genera DENTRO del contenedor y se trae con `docker cp`.
       Redirigir la salida binaria de `docker exec` con `>` en PowerShell
       CORROMPE el archivo (reencoda y cambia los finales de linea): el dump
       parece correcto, pesa parecido y falla al restaurar. Es la trampa
       clasica de este script.

    2) El destino por defecto esta FUERA del volumen de PostgreSQL. Un respaldo
       guardado dentro del mismo volumen que protege no es un respaldo.

.EJEMPLO
    powershell -File backend-spring/operacion/respaldo.ps1
    powershell -File backend-spring/operacion/respaldo.ps1 -BaseDatos controllocal_dev -RetencionDias 30
#>
[CmdletBinding()]
param(
    [string] $Contenedor    = 'controllocal-postgres-v2',
    [string] $BaseDatos     = 'controllocal_dev',
    [string] $Usuario       = 'controllocal',
    [string] $Destino       = '',
    [int]    $RetencionDias = 14
)

$ErrorActionPreference = 'Stop'
$inicio = Get-Date

# OJO: en Windows PowerShell 5.1 $PSScriptRoot NO esta disponible dentro del
# bloque param(), asi que el valor por defecto se resuelve aqui.
if (-not $Destino) { $Destino = Join-Path $PSScriptRoot '..\backups' }

function Escribir([string] $texto, [string] $color = 'Gray') {
    Write-Host $texto -ForegroundColor $color
}

function Salir-ConError([string] $mensaje) {
    Escribir "[FALLO] $mensaje" 'Red'
    exit 1
}

Escribir "=== Respaldo de PostgreSQL v2 ===" 'Cyan'
Escribir "Contenedor : $Contenedor"
Escribir "Base       : $BaseDatos"

# --- 1. Comprobaciones previas ------------------------------------------
$estado = docker inspect -f '{{.State.Running}}' $Contenedor 2>$null
if ($LASTEXITCODE -ne 0) { Salir-ConError "El contenedor '$Contenedor' no existe." }
if ($estado -ne 'true')  { Salir-ConError "El contenedor '$Contenedor' no esta en ejecucion." }

$existe = docker exec $Contenedor psql -U $Usuario -d postgres -tAc `
    "SELECT 1 FROM pg_database WHERE datname = '$BaseDatos'"
if (-not $existe) { Salir-ConError "La base '$BaseDatos' no existe en el contenedor." }

if (-not (Test-Path $Destino)) { New-Item -ItemType Directory -Path $Destino -Force | Out-Null }
$Destino = (Resolve-Path $Destino).Path
Escribir "Destino    : $Destino"

# --- 2. Metadatos que viajan con el respaldo ----------------------------
# El historial de Flyway va DENTRO del dump, pero tambien en el manifiesto:
# asi se sabe que esquema contiene el archivo sin tener que restaurarlo.
# OJO: flyway_schema_history.version es VARCHAR, asi que max() compara como
# TEXTO y devolveria '9' teniendo aplicada la V27. Se ordena por installed_rank,
# que es el orden real de aplicacion.
$versionFlyway = docker exec $Contenedor psql -U $Usuario -d $BaseDatos -tAc `
    "SELECT coalesce((SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1), 'ninguna')"
$migracionesOk = docker exec $Contenedor psql -U $Usuario -d $BaseDatos -tAc `
    "SELECT count(*) FROM flyway_schema_history WHERE success"
$migracionesKo = docker exec $Contenedor psql -U $Usuario -d $BaseDatos -tAc `
    "SELECT count(*) FROM flyway_schema_history WHERE NOT success"
$versionServidor = docker exec $Contenedor psql -U $Usuario -d $BaseDatos -tAc "SHOW server_version"

if ([int]$migracionesKo -gt 0) {
    Escribir "[AVISO] La base tiene $migracionesKo migracion(es) FALLIDA(S) en el historial." 'Yellow'
}

# --- 3. El dump ----------------------------------------------------------
$marca      = Get-Date -Format 'yyyyMMdd_HHmmss'
$nombre     = "${BaseDatos}_$marca.dump"
$rutaEnCont = "/tmp/$nombre"
$rutaLocal  = Join-Path $Destino $nombre

Escribir "`n-> pg_dump (formato custom, incluye esquema, datos e historial Flyway)..."
docker exec $Contenedor pg_dump -U $Usuario -d $BaseDatos -Fc -f $rutaEnCont
if ($LASTEXITCODE -ne 0) { Salir-ConError "pg_dump devolvio codigo $LASTEXITCODE." }

# `docker cp` mueve bytes sin interpretarlos: es lo que evita la corrupcion.
docker cp "${Contenedor}:$rutaEnCont" $rutaLocal | Out-Null
if ($LASTEXITCODE -ne 0) { Salir-ConError "docker cp devolvio codigo $LASTEXITCODE." }
docker exec $Contenedor rm -f $rutaEnCont | Out-Null

if (-not (Test-Path $rutaLocal)) { Salir-ConError "El archivo no llego al host." }
$tamano = (Get-Item $rutaLocal).Length
if ($tamano -lt 1024) { Salir-ConError "El dump pesa $tamano bytes: es sospechosamente pequeno." }

# El formato custom empieza por la firma 'PGDMP'. Comprobarla aqui detecta al
# instante una corrupcion por reencoding, en vez de descubrirla al restaurar.
$primeros = [System.IO.File]::ReadAllBytes($rutaLocal)[0..4]
$firma = -join ($primeros | ForEach-Object { [char]$_ })
if ($firma -ne 'PGDMP') { Salir-ConError "El archivo no empieza por 'PGDMP' (leido: '$firma'). Dump corrupto." }

# --- 4. Checksum y manifiesto -------------------------------------------
$hash = (Get-FileHash -Path $rutaLocal -Algorithm SHA256).Hash
"$hash  $nombre" | Set-Content -Path "$rutaLocal.sha256" -Encoding ascii

$manifiesto = [ordered]@{
    archivo           = $nombre
    fecha             = (Get-Date).ToString('o')
    baseDatos         = $BaseDatos
    contenedor        = $Contenedor
    versionPostgres   = $versionServidor
    flywayVersionMax  = $versionFlyway
    flywayMigraciones = [int]$migracionesOk
    flywayFallidas    = [int]$migracionesKo
    formato           = 'pg_dump custom (-Fc)'
    tamanoBytes       = $tamano
    sha256            = $hash
    retencionDias     = $RetencionDias
}
$manifiesto | ConvertTo-Json | Set-Content -Path "$rutaLocal.json" -Encoding utf8

Escribir "`n[OK] Respaldo generado" 'Green'
Escribir "  Archivo   : $nombre"
Escribir "  Tamano    : $([math]::Round($tamano/1KB, 1)) KB"
Escribir "  Flyway    : $migracionesOk migraciones, maxima $versionFlyway"
Escribir "  PostgreSQL: $versionServidor"
Escribir "  SHA-256   : $hash"

# --- 5. Retencion --------------------------------------------------------
$limite   = (Get-Date).AddDays(-$RetencionDias)
$caducos  = @(Get-ChildItem -Path $Destino -Filter "${BaseDatos}_*.dump" |
              Where-Object { $_.LastWriteTime -lt $limite })
if ($caducos.Count -gt 0) {
    Escribir "`n-> Retencion: retirando $($caducos.Count) respaldo(s) de mas de $RetencionDias dias"
    foreach ($c in $caducos) {
        Remove-Item $c.FullName -Force
        Remove-Item "$($c.FullName).sha256" -Force -ErrorAction SilentlyContinue
        Remove-Item "$($c.FullName).json"   -Force -ErrorAction SilentlyContinue
        Escribir "   - $($c.Name)"
    }
} else {
    Escribir "`n-> Retencion: nada que retirar (limite $RetencionDias dias)"
}

$vivos = @(Get-ChildItem -Path $Destino -Filter "${BaseDatos}_*.dump")
Escribir "`nRespaldos conservados: $($vivos.Count)" 'Cyan'
Escribir "Duracion: $([math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)) s"
Escribir "`nSIGUIENTE PASO OBLIGATORIO: verificar que se puede restaurar." 'Yellow'
Escribir "  powershell -File backend-spring/operacion/restaurar-verificar.ps1" 'Yellow'
exit 0
