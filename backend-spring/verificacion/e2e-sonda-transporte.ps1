# =====================================================================
# SONDA DE TRANSPORTE. No es un gate funcional: no comprueba ni una regla de
# negocio. Su unico trabajo es responder a una pregunta que envenena cualquier
# medicion de rendimiento hecha desde esta maquina:
#
#   ¿esta el entorno metiendo pausas periodicas que no vienen del producto?
#
# Existe por lo que paso el 2026-08-03. El gate de busqueda de F3 fallo dos
# corridas seguidas en `VIS casa TODO - profunda` (3.357 y 3.309 ms contra un
# limite de 3.000). No era la consulta: era el **proxy de puertos de Docker
# Desktop**. Tomcat cierra la conexion tras 100 peticiones y el cliente de
# PowerShell mantiene 2 conexiones agrupadas, de modo que el par se renovaba
# cada 200 peticiones; rehacer esa conexion desde Windows costaba ~2 s, y esos
# 2 s caian sobre una llamada cualquiera del gate. Como la secuencia de
# peticiones del gate es determinista, caian SIEMPRE en el mismo escenario, y
# parecia un problema de esa consulta. El analisis completo esta en
# `docs/ai/diagnostico-pico-rc003-gate-f3.md`.
#
# Golpea `/salud` —que no consulta la base ni pagina nada— una vez por segundo
# por los DOS caminos a la vez:
#
#   W. desde Windows contra el puerto publicado (cliente + proxy de Docker Desktop)
#   D. desde dentro de la red de Docker         (sin cliente Windows ni proxy)
#
# Cualquier llamada que se vaya por encima de 500 ms sobre un trabajo de
# milisegundos es una pausa del entorno. La sonda TERMINA EN ERROR si encuentra
# alguna: es la senal de que los percentiles de los gates de rendimiento no son
# fiables hasta averiguar que la produce.
#
# Que se espera ver con el entorno sano (medido tras fijar
# `SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1` en docker-compose.e2e.yml):
#   W: p50 28 ms, peor 201 ms, cero pausas.   D: p50 0 ms, peor 10 ms, cero pausas.
# Y con el entorno enfermo, que es lo que buscaba:
#   W: cuatro pausas de ~2.070 ms, en las llamadas 200, 400, 600 y 800.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite sonda-transporte
# Duracion por defecto 300 s; `CONTROLLOCAL_E2E_SEGUNDOS` la cambia.
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$SEGUNDOS = if ($env:CONTROLLOCAL_E2E_SEGUNDOS) { [int]$env:CONTROLLOCAL_E2E_SEGUNDOS } else { 300 }
$PAUSA = 500

# Los comandos nativos escriben en stderr y PowerShell 5.1 convierte cada linea
# en un ErrorRecord: con $ErrorActionPreference='Stop' eso aborta el guion
# aunque el comando haya ido bien.
function SalidaNativa([scriptblock] $bloque) {
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $bloque 2>&1 | ForEach-Object { $_.ToString() } }
    finally { $ErrorActionPreference = $previo }
}

Write-Host "`n== Sonda de transporte: $SEGUNDOS s de /salud por los dos caminos ==" -ForegroundColor Cyan

# El bucle de dentro del contenedor arranca primero y corre en paralelo. Junta
# su stderr (donde `time` escribe) con su stdout DENTRO del contenedor, para no
# depender de como PowerShell trate los dos flujos.
$salidaD = Join-Path $env:TEMP "sonda-transporte-$($e2e.RunId).txt"
$erroresD = Join-Path $env:TEMP "sonda-transporte-$($e2e.RunId).err"
$guion = @"
i=0
while [ `$i -lt $SEGUNDOS ]; do
  i=`$((i+1))
  { printf '%s ' "`$(date -u +%H:%M:%S)"; time wget -q -O /dev/null 'http://api-e2e:8090/controllocal/Api/salud'; } 2>&1
  sleep 1
done
"@
$proceso = Start-Process -FilePath 'docker' -PassThru -NoNewWindow `
    -ArgumentList @('exec', $e2e.PostgresContainer, 'sh', '-c', $guion) `
    -RedirectStandardOutput $salidaD -RedirectStandardError $erroresD

# La PRIMERA llamada de un proceso de PowerShell paga la inicializacion del
# cliente HTTP de .NET —deteccion de proxy del sistema incluida— y puede irse a
# segundos. Es coste de arranque, no una pausa del entorno, asi que se descarta.
Invoke-WebRequest -Uri "$base/salud" -UseBasicParsing -TimeoutSec 60 | Out-Null

$W = @()
$inicio = [datetime]::UtcNow
for ($i = 1; $i -le $SEGUNDOS; $i++) {
    $t0 = [datetime]::UtcNow
    $t = Measure-Command {
        Invoke-WebRequest -Uri "$base/salud" -UseBasicParsing -TimeoutSec 60 | Out-Null
    }
    $ms = [int][math]::Round($t.TotalMilliseconds)
    $W += [pscustomobject]@{ N = $i; Utc = $t0.ToString('HH:mm:ss'); Ms = $ms }
    $resto = 1000 - $ms
    if ($resto -gt 0) { Start-Sleep -Milliseconds $resto }
}
$fin = [datetime]::UtcNow
if (-not $proceso.HasExited) { $proceso.WaitForExit(60000) }

$D = @()
$marca = $null
if (Test-Path -LiteralPath $salidaD) {
    foreach ($linea in (Get-Content -LiteralPath $salidaD)) {
        if ($linea -match '^(\d\d:\d\d:\d\d)\s+real\s+(\d+)m\s+([\d\.]+)s') {
            $D += [pscustomobject]@{ Utc = $matches[1]
                                     Ms = [int]([math]::Round(([int]$matches[2] * 60 + [double]$matches[3]) * 1000)) }
        } elseif ($linea -match '^(\d\d:\d\d:\d\d)\s*$') {
            $marca = $matches[1]
        } elseif ($linea -match '^real\s+(\d+)m\s+([\d\.]+)s' -and $marca) {
            $D += [pscustomobject]@{ Utc = $marca
                                     Ms = [int]([math]::Round(([int]$matches[1] * 60 + [double]$matches[2]) * 1000)) }
        }
    }
}

function Resumen($nombre, $filas) {
    $o = @($filas | Select-Object -ExpandProperty Ms | Sort-Object)
    if ($o.Count -eq 0) { return $null }
    [pscustomobject]@{
        Camino = $nombre; N = $o.Count
        p50 = $o[[int]($o.Count * 0.5)]
        p99 = $o[[math]::Max(0, [int]($o.Count * 0.99) - 1)]
        Peor = $o[-1]
        Pausas = @($o | Where-Object { $_ -ge $PAUSA }).Count
    }
}

Write-Host "`n== Resultado: ventana $($inicio.ToString('HH:mm:ss')) -> $($fin.ToString('HH:mm:ss')) UTC ==" -ForegroundColor Cyan
@((Resumen 'W  Windows -> puerto publicado' $W),
  (Resumen 'D  dentro de Docker'            $D)) |
    Format-Table -AutoSize | Out-String -Width 200 | Write-Host

$pausasW = @($W | Where-Object { $_.Ms -ge $PAUSA })
$pausasD = @($D | Where-Object { $_.Ms -ge $PAUSA })
foreach ($par in @(@{ N = 'W'; P = $pausasW }, @{ N = 'D'; P = $pausasD })) {
    Write-Host "-- pausas de $($par.N) (>= $PAUSA ms) --" -ForegroundColor DarkGray
    if ($par.P.Count) { $par.P | Format-Table -AutoSize | Out-String -Width 200 | Write-Host }
    else { Write-Host "   ninguna" }
}

# Lo que orienta el diagnostico: si la pausa sale en los dos caminos a la vez,
# lo que se detiene es la maquina; si sale solo en W, es el camino
# Windows<->contenedor, que es lo que ocurrio en agosto de 2026.
if ($pausasW.Count -and -not $pausasD.Count) {
    Write-Host "`n  Pausas SOLO en el camino de Windows: sospecha del proxy de puertos." -ForegroundColor Yellow
    Write-Host "  Primero comprobar keep-alive; ver docs/ai/diagnostico-pico-rc003-gate-f3.md." -ForegroundColor Yellow
} elseif ($pausasW.Count -and $pausasD.Count) {
    Write-Host "`n  Pausas en los DOS caminos: se detiene la maquina, no el transporte." -ForegroundColor Yellow
}

# Un periodo regular es la firma de un artefacto del entorno, no de la carga.
if ($pausasW.Count -ge 2) {
    $pasos = for ($i = 1; $i -lt $pausasW.Count; $i++) { $pausasW[$i].N - $pausasW[$i - 1].N }
    Write-Host "  Separacion entre pausas, en llamadas: $($pasos -join ', ')" -ForegroundColor Yellow
}

$total = $pausasW.Count + $pausasD.Count
if ($total -gt 0) {
    Write-Host "`n===== $total pausas del entorno: los percentiles de los gates NO son fiables =====" -ForegroundColor Red
} else {
    Write-Host "`n===== sin pausas del entorno: se puede medir rendimiento =====" -ForegroundColor Green
}
Remove-Item -LiteralPath $salidaD, $erroresD -ErrorAction SilentlyContinue
if ($total -gt 0) { exit 1 }
