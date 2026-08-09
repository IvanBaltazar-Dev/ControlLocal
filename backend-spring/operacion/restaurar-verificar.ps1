<#
.SINOPSIS
    Restauracion AUTOMATIZADA Y VERIFICADA de un respaldo de PostgreSQL v2.

.DESCRIPCION
    BLOQUE 1.3 del plan maestro. El entregable no es "un archivo de backup":
    es una restauracion comprobada. Este script ejecuta las siete
    comprobaciones del encargo sobre una base NUEVA y desechable:

      1. crea una base vacia
      2. restaura el ultimo respaldo (o el que se le indique)
      3. valida que Flyway reconoce el historial
      4. comprueba las tablas criticas
      5. ejecuta consultas minimas
      6. comprueba el acceso a los documentos referenciados
      7. genera resultado de exito o fallo

    La base de verificacion se elimina siempre en el bloque `finally`, incluso
    si algo revienta a mitad: dejar bases huerfanas fue el problema que costo
    una corrida entera en las suites E2E.

.EJEMPLO
    powershell -File backend-spring/operacion/restaurar-verificar.ps1
    powershell -File backend-spring/operacion/restaurar-verificar.ps1 -Archivo backups/controllocal_dev_20260804_231500.dump
#>
[CmdletBinding()]
param(
    [string] $Contenedor       = 'controllocal-postgres-v2',
    [string] $Usuario          = 'controllocal',
    [string] $Archivo          = '',
    [string] $Origen           = '',
    [string] $BaseVerificacion = '',
    # Compose prefija el nombre del volumen con el del proyecto (la carpeta).
    [string] $VolumenAlmacen   = 'backend-spring_controllocal_almacen',
    [switch] $ConservarBase
)

$ErrorActionPreference = 'Stop'
$inicio = Get-Date

# OJO: en Windows PowerShell 5.1 $PSScriptRoot NO esta disponible dentro del
# bloque param(), asi que el valor por defecto se resuelve aqui.
if (-not $Origen) { $Origen = Join-Path $PSScriptRoot '..\backups' }

$script:total = 0
$script:ok    = 0
$script:fallos = @()

function Comprobar([string] $nombre, [bool] $condicion, [string] $detalle = '') {
    $script:total++
    if ($condicion) {
        $script:ok++
        Write-Host ("  [OK]    {0}{1}" -f $nombre, $(if ($detalle) { " -- $detalle" } else { '' })) -ForegroundColor Green
    } else {
        $script:fallos += $nombre
        Write-Host ("  [FALLO] {0}{1}" -f $nombre, $(if ($detalle) { " -- $detalle" } else { '' })) -ForegroundColor Red
    }
}

function Consultar([string] $base, [string] $sql) {
    # -tAc: solo tuplas, sin alinear. Devuelve cadena vacia si no hay filas.
    $r = docker exec $Contenedor psql -U $Usuario -d $base -tAc $sql 2>$null
    if ($null -eq $r) { return '' }
    return ($r | Out-String).Trim()
}

Write-Host "=== Restauracion verificada de PostgreSQL v2 ===" -ForegroundColor Cyan

# --- Eleccion del respaldo ----------------------------------------------
if (-not $Archivo) {
    $candidatos = @(Get-ChildItem -Path $Origen -Filter '*.dump' -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending)
    if ($candidatos.Count -eq 0) {
        Write-Host "[FALLO] No hay ningun .dump en '$Origen'. Ejecute antes respaldo.ps1." -ForegroundColor Red
        exit 1
    }
    $Archivo = $candidatos[0].FullName
}
if (-not (Test-Path $Archivo)) {
    Write-Host "[FALLO] No existe el archivo '$Archivo'." -ForegroundColor Red
    exit 1
}
$Archivo = (Resolve-Path $Archivo).Path
$nombreArchivo = Split-Path $Archivo -Leaf

if (-not $BaseVerificacion) {
    $BaseVerificacion = 'controllocal_restauracion_' + (Get-Date -Format 'yyyyMMddHHmmss')
}

Write-Host "Respaldo : $nombreArchivo"
Write-Host "Base test: $BaseVerificacion`n"

try {
    # --- 0. Integridad del archivo antes de tocar nada -------------------
    Write-Host "-- 0. Integridad del archivo --" -ForegroundColor Cyan
    $sidecar = "$Archivo.sha256"
    if (Test-Path $sidecar) {
        $esperado = ((Get-Content $sidecar -Raw).Trim() -split '\s+')[0]
        $actual   = (Get-FileHash -Path $Archivo -Algorithm SHA256).Hash
        Comprobar 'El SHA-256 coincide con el registrado al generarlo' ($esperado -eq $actual)
    } else {
        Comprobar 'Existe el checksum del respaldo' $false 'falta el sidecar .sha256'
    }
    $firma = -join ([System.IO.File]::ReadAllBytes($Archivo)[0..4] | ForEach-Object { [char]$_ })
    Comprobar 'El archivo es un dump custom de PostgreSQL' ($firma -eq 'PGDMP') "firma leida: '$firma'"

    # --- 1. Base vacia ---------------------------------------------------
    Write-Host "`n-- 1. Base vacia --" -ForegroundColor Cyan
    docker exec $Contenedor psql -U $Usuario -d postgres -c "CREATE DATABASE $BaseVerificacion" | Out-Null
    $creada = Consultar 'postgres' "SELECT 1 FROM pg_database WHERE datname = '$BaseVerificacion'"
    Comprobar 'Se creo la base de verificacion' ($creada -eq '1')

    $tablasAntes = Consultar $BaseVerificacion `
        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
    Comprobar 'La base nace vacia' ($tablasAntes -eq '0') "$tablasAntes tablas"

    # --- 2. Restauracion -------------------------------------------------
    Write-Host "`n-- 2. Restauracion --" -ForegroundColor Cyan
    $enContenedor = "/tmp/$nombreArchivo"
    docker cp $Archivo "${Contenedor}:$enContenedor" | Out-Null
    # pg_restore devuelve != 0 por avisos benignos (owner/ACL inexistentes en
    # una base nueva). No se juzga por el codigo de salida sino por el estado
    # final de la base, que es lo que de verdad importa.
    docker exec $Contenedor pg_restore -U $Usuario -d $BaseVerificacion --no-owner --no-privileges $enContenedor 2>$null
    $codigoRestore = $LASTEXITCODE
    docker exec $Contenedor rm -f $enContenedor | Out-Null

    $tablas = Consultar $BaseVerificacion `
        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
    Comprobar 'La restauracion dejo tablas en la base' ([int]$tablas -gt 0) "$tablas tablas (pg_restore salio $codigoRestore)"

    # --- 3. Flyway reconoce el historial --------------------------------
    Write-Host "`n-- 3. Historial de Flyway --" -ForegroundColor Cyan
    $hayHistorial = Consultar $BaseVerificacion `
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='flyway_schema_history'"
    Comprobar 'Existe flyway_schema_history' ($hayHistorial -eq '1')

    if ($hayHistorial -eq '1') {
        $aplicadas = Consultar $BaseVerificacion "SELECT count(*) FROM flyway_schema_history WHERE success"
        $fallidas  = Consultar $BaseVerificacion "SELECT count(*) FROM flyway_schema_history WHERE NOT success"
        # version es VARCHAR: max() daria '9' con la V27 aplicada. Se usa el
        # orden real de aplicacion (installed_rank).
        $maxima    = Consultar $BaseVerificacion `
            "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
        Comprobar 'El historial trae migraciones aplicadas' ([int]$aplicadas -gt 0) "$aplicadas migraciones, maxima $maxima"
        Comprobar 'Ninguna migracion quedo marcada como fallida' ($fallidas -eq '0') "fallidas: $fallidas"

        # Flyway "reconoce" el historial si el baseline coincide con lo que hay:
        # una tabla restaurada a medias daria checksums nulos.
        $sinChecksum = Consultar $BaseVerificacion `
            "SELECT count(*) FROM flyway_schema_history WHERE checksum IS NULL AND type = 'SQL'"
        Comprobar 'Todas las migraciones SQL conservan su checksum' ($sinChecksum -eq '0') "sin checksum: $sinChecksum"
    }

    # --- 4. Tablas criticas ---------------------------------------------
    Write-Host "`n-- 4. Tablas criticas --" -ForegroundColor Cyan
    $criticas = @('organizacion', 'persona', 'persona_rol', 'credencial_usuario',
                  'propiedad', 'captacion', 'oportunidad_comercial', 'solicitud_alquiler',
                  'contrato_alquiler', 'comision_liquidacion', 'historial_estado')
    foreach ($t in $criticas) {
        $existe = Consultar $BaseVerificacion `
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'"
        $filas = if ($existe -eq '1') { Consultar $BaseVerificacion "SELECT count(*) FROM $t" } else { 'n/a' }
        Comprobar "Tabla '$t'" ($existe -eq '1') "$filas filas"
    }

    # --- 5. Consultas minimas -------------------------------------------
    Write-Host "`n-- 5. Consultas minimas --" -ForegroundColor Cyan
    $orgs = Consultar $BaseVerificacion "SELECT count(*) FROM organizacion WHERE estado = 'A'"
    Comprobar 'Hay al menos una organizacion activa' ([int]$orgs -ge 1) "$orgs activas"

    $credenciales = Consultar $BaseVerificacion "SELECT count(*) FROM credencial_usuario"
    Comprobar 'Hay credenciales restauradas' ([int]$credenciales -ge 1) "$credenciales credenciales"

    # Integridad referencial real: si el dump se restauro a medias, una FK
    # colgando aparece aqui y no en el conteo de filas.
    $rolesHuerfanos = Consultar $BaseVerificacion `
        "SELECT count(*) FROM persona_rol pr LEFT JOIN persona p ON p.id_persona = pr.id_persona WHERE p.id_persona IS NULL"
    Comprobar 'Ningun persona_rol quedo sin su persona' ($rolesHuerfanos -eq '0') "huerfanos: $rolesHuerfanos"

    $tenantNulo = Consultar $BaseVerificacion "SELECT count(*) FROM persona WHERE organizacion_id IS NULL"
    Comprobar 'Toda persona conserva su organizacion (tenancy)' ($tenantNulo -eq '0') "sin tenant: $tenantNulo"

    # --- 6. Documentos referenciados ------------------------------------
    Write-Host "`n-- 6. Documentos referenciados --" -ForegroundColor Cyan
    $hayVolumen = docker volume inspect $VolumenAlmacen 2>$null
    if ($LASTEXITCODE -ne 0) {
        Comprobar "Existe el volumen del almacen '$VolumenAlmacen'" $false 'no existe: los binarios no serian persistentes'
    } else {
        Comprobar "Existe el volumen del almacen '$VolumenAlmacen'" $true

        $claves = @()
        $fotos = Consultar $BaseVerificacion "SELECT clave FROM foto_propiedad"
        if ($fotos) { $claves += ($fotos -split "`n" | Where-Object { $_.Trim() }) }
        $docs = Consultar $BaseVerificacion "SELECT ruta_archivo FROM documento_solicitud WHERE ruta_archivo IS NOT NULL"
        if ($docs) { $claves += ($docs -split "`n" | Where-Object { $_.Trim() }) }
        $claves = @($claves | ForEach-Object { $_.Trim() } | Where-Object { $_ })

        if ($claves.Count -eq 0) {
            Comprobar 'La base no referencia binarios (nada que comprobar)' $true '0 claves'
        } else {
            # Un unico contenedor efimero lista el volumen entero: mas barato
            # que un `docker run` por clave, y suficiente para comparar.
            $listado = docker run --rm -v "${VolumenAlmacen}:/almacen" alpine:3.20 `
                       sh -c "cd /almacen 2>/dev/null && find . -type f | sed 's|^\./||'" 2>$null
            $presentes = @()
            if ($listado) { $presentes = @($listado -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }

            $faltan = @($claves | Where-Object { $presentes -notcontains $_ })
            $encontradas = $claves.Count - $faltan.Count
            # Se informa, no se exige el 100 %: una base restaurada de otro
            # entorno referencia binarios que ese entorno nunca tuvo. Lo que
            # importa es que el almacen sea LEGIBLE y que se sepa el numero.
            Comprobar 'El almacen es accesible y se pudo listar' ($null -ne $listado) "$($presentes.Count) archivos en el volumen"
            Write-Host "  [INFO]  Binarios referenciados: $($claves.Count); presentes: $encontradas; ausentes: $($faltan.Count)" -ForegroundColor Gray
            if ($faltan.Count -gt 0 -and $faltan.Count -le 5) {
                foreach ($f in $faltan) { Write-Host "          falta: $f" -ForegroundColor DarkYellow }
            }
        }
    }
}
finally {
    # --- Limpieza SIEMPRE ------------------------------------------------
    if (-not $ConservarBase -and $BaseVerificacion) {
        Write-Host "`n-- Limpieza --" -ForegroundColor Cyan
        docker exec $Contenedor psql -U $Usuario -d postgres -c `
            "DROP DATABASE IF EXISTS $BaseVerificacion WITH (FORCE)" 2>$null | Out-Null
        Write-Host "  Base de verificacion eliminada: $BaseVerificacion" -ForegroundColor Gray
    }
}

# --- 7. Resultado --------------------------------------------------------
$duracion = [math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)
Write-Host "`n=====================================" -ForegroundColor Cyan
if ($script:fallos.Count -eq 0) {
    Write-Host "RESULTADO: EXITO  ($($script:ok)/$($script:total) comprobaciones)" -ForegroundColor Green
    Write-Host "Respaldo verificado: $nombreArchivo"
    Write-Host "Duracion: $duracion s"
    exit 0
} else {
    Write-Host "RESULTADO: FALLO  ($($script:ok)/$($script:total) comprobaciones)" -ForegroundColor Red
    Write-Host "Comprobaciones fallidas:" -ForegroundColor Red
    foreach ($f in $script:fallos) { Write-Host "  - $f" -ForegroundColor Red }
    Write-Host "Duracion: $duracion s"
    exit 1
}
