# =====================================================================
# Recuperacion de emergencia del gobierno de un tenant — NIVEL 3.
#
# Diseño: docs/ai/plan-s0-6-mfa-y-break-glass.md §9
# Procedimiento: operacion/custodios-y-recuperacion-de-emergencia.md
#
# ANTES DE EJECUTAR ESTO, LEE EL PROCEDIMIENTO. Los tres criterios de
# activacion (§4.1) tienen que cumplirse a la vez; "es mas rapido" no es uno
# de ellos.
#
# CUATRO COSAS QUE ESTE SCRIPT **NO** HACE, y no por falta de tiempo:
#   * no fija la contrasena de nadie — el titular la conserva o la recupera
#     por su via, y la regla del proyecto no tiene excepciones;
#   * no configura el segundo factor de nadie — lo enrola su titular;
#   * no crea cuentas, personas ni roles — repone gobierno, no puebla;
#   * no abre sesion — no hay token que obtener, ni nada donde entrar.
#
# Se conecta al conector de GESTION LOCAL (127.0.0.1). Si no responde, o la
# recuperacion no esta habilitada, o no estas en el host del backend. Las dos
# cosas son intencionadas: lo que protege esta superficie es la red.
# =====================================================================
[CmdletBinding()]
param(
    [int]    $PuertoGestion = 8091,
    [string] $Host_ = '127.0.0.1',
    [string] $ContextPath = '/controllocal/Api'
)

$ErrorActionPreference = 'Stop'
$base = "http://${Host_}:$PuertoGestion$ContextPath/gestion/recuperacion"

function Pedir($etiqueta) {
    $valor = Read-Host $etiqueta
    if ([string]::IsNullOrWhiteSpace($valor)) { throw "$etiqueta es obligatorio." }
    return $valor.Trim()
}

# Los secretos se piden SIEMPRE asi: por consola y ocultos. Nunca por
# parametro — un secreto en la linea de comandos queda en el historial del
# interprete y en la lista de procesos de la maquina.
function PedirSecreto($etiqueta) {
    $seguro = Read-Host $etiqueta -AsSecureString
    $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero) }
}

function Llamar($metodo, $ruta, $cuerpo, $cabeceras) {
    $parametros = @{ Method = $metodo; Uri = "$base$ruta"; TimeoutSec = 30 }
    if ($cabeceras) { $parametros['Headers'] = $cabeceras }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 5)
        $parametros['ContentType'] = 'application/json'
    }
    try { return Invoke-RestMethod @parametros }
    catch {
        $detalle = '(sin cuerpo)'
        try {
            $lector = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            $detalle = ($lector.ReadToEnd() | ConvertFrom-Json).error
        } catch { }
        throw "La operacion fallo: $detalle"
    }
}

Write-Host ""
Write-Host "== Recuperacion de emergencia del gobierno ==" -ForegroundColor Yellow
Write-Host "Conector local: $base" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Esto NO fija contrasenas, NO configura factores y NO crea cuentas." -ForegroundColor Yellow
Write-Host "Devuelve el acceso; el titular vuelve a enrolar su segundo factor." -ForegroundColor Yellow
Write-Host ""

# --- 1. Quien, sobre quien y por que --------------------------------------
$operador = Pedir 'Identificador del OPERADOR (quien ejecuta; no puede ser custodio)'
$organizacion = [long](Pedir 'id de la organizacion')
$objetivo = [long](Pedir 'id de la PERSONA a la que se devuelve el gobierno')
$motivo = Pedir 'Motivo (que paso, desde cuando, que se intento antes)'

$emision = Llamar POST '' @{
    idOrganizacion   = $organizacion
    idPersonaObjetivo = $objetivo
    operador         = $operador
    motivo           = $motivo
}
$idConcesion = $emision.idConcesion
Write-Host "`nConcesion $idConcesion abierta en PENDIENTE. Todavia no autoriza nada." -ForegroundColor Cyan

# --- 2. Las dos aprobaciones ----------------------------------------------
# Se piden por separado y en momentos distintos A PROPOSITO: la separacion de
# manos la sostiene el procedimiento, no el software.
Write-Host "`n-- Primera aprobacion --" -ForegroundColor Cyan
$custodio1 = Pedir 'Identificador del custodio'
$secreto1 = PedirSecreto 'Secreto del custodio'
Llamar POST "/$idConcesion/aprobaciones" @{ custodio = $custodio1; secreto = $secreto1 } | Out-Null
$secreto1 = $null
Write-Host "Registrada. Falta la segunda." -ForegroundColor DarkGray

Write-Host "`n-- Segunda aprobacion (OTRO custodio) --" -ForegroundColor Cyan
$custodio2 = Pedir 'Identificador del custodio'
$secreto2 = PedirSecreto 'Secreto del custodio'
$activada = Llamar POST "/$idConcesion/aprobaciones" @{ custodio = $custodio2; secreto = $secreto2 }
$secreto2 = $null

if (-not $activada.concesion) { throw 'La concesion no quedo vigente. Revisa las aprobaciones.' }
$concesion = $activada.concesion
Write-Host "`nConcesion VIGENTE durante 30 minutos, con 3 acciones como maximo." -ForegroundColor Green

# --- 3. Las acciones -------------------------------------------------------
# Se aplican SOLO las que hagan falta. Cada una vale una vez.
Write-Host ""
Write-Host "Acciones disponibles (una vez cada una):" -ForegroundColor Cyan
Write-Host "  1) REACTIVAR_CUENTA    2) REVOCAR_MFA    3) REPONER_MEMBRESIA    0) terminar"

$nombres = @{ '1' = 'REACTIVAR_CUENTA'; '2' = 'REVOCAR_MFA'; '3' = 'REPONER_MEMBRESIA' }
while ($true) {
    $opcion = Read-Host "`nAccion a aplicar (0 para terminar)"
    if ($opcion -eq '0') { break }
    if (-not $nombres.ContainsKey($opcion)) { Write-Host 'Opcion no valida.' -ForegroundColor Red; continue }

    try {
        $resultado = Llamar POST "/acciones/$($nombres[$opcion])" $null @{ 'X-Concesion' = $concesion }
        $efecto = if ($resultado.cambioAlgo) { 'aplicada' } else { 'sin efecto (el estado ya lo cumplia)' }
        Write-Host "  $($resultado.tipo): $efecto. Quedan $($resultado.accionesRestantes)." -ForegroundColor Green
        if ($resultado.concesionCerrada) {
            Write-Host "`nLa concesion SE CERRO SOLA: la organizacion ya tiene gobierno operativo." -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "  $_" -ForegroundColor Red
    }
}

$concesion = $null

Write-Host ""
Write-Host "== Cierre, hoy mismo ==" -ForegroundColor Yellow
Write-Host "1. ROTAR los dos secretos de custodio, de uno en uno y probando cada uno."
Write-Host "2. Que el titular ENROLE su segundo factor el mismo."
Write-Host "3. Comprobar que el tenant vuelve a tener DOS administradores operativos."
Write-Host "4. Registrar el ejercicio en el §5 del procedimiento y archivar el motivo."
Write-Host ""
