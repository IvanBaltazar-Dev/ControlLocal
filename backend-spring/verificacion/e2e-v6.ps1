# =====================================================================
# E2E del flujo F2 sobre el nucleo multi-tenant (V6).
#
# Recorre oferta + prospeccion + captacion contra el API v2 y comprueba,
# fila por fila, que todo lo que se crea nace con el tenant de legado.
# Cubre los criterios #1, #2, #5, #6 y #9 del gate de
# docs/ai/plan-migracion-v6-tenancy.md.
#
# Uso: powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite v6
# Uso:        pwsh backend-spring/verificacion/e2e-v6.ps1
# =====================================================================
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$ok = 0; $fail = 0

function Check($nombre, $condicion, $detalle) {
    if ($condicion) { $script:ok++; Write-Host "  OK   $nombre" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FALLA $nombre -> $detalle" -ForegroundColor Red }
}

function Api($metodo, $ruta, $token, $cuerpo) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $parametros = @{ Method = $metodo; Uri = "$base$ruta"; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $cuerpo) {
        $parametros['Body'] = ($cuerpo | ConvertTo-Json -Depth 6)
        $parametros['ContentType'] = 'application/json'
    }
    Invoke-RestMethod @parametros
}

function Sql($consulta) {
    (docker exec $e2e.PostgresContainer psql -U controllocal -d $e2e.Database -t -A -c $consulta) -join "`n"
}

Write-Host "`n== 1. Login (contrato de token CONGELADO) ==" -ForegroundColor Cyan
$agente = Api POST '/auth/login' $null @{ usuario = 'vmora'; contrasena = 'Agente2026' }
Check 'login agente devuelve token' ($agente.token.Length -gt 40) $agente.token
Check 'rol AGENTE' ($agente.rol -eq 'AGENTE') $agente.rol
Check 'el token sigue teniendo 3 partes HS256' ($agente.token.Split('.').Count -eq 3) 'formato'
$claims = $agente.token.Split('.')[1]
$claims = $claims.PadRight([int](([math]::Ceiling($claims.Length / 4.0)) * 4), '=').Replace('-', '+').Replace('_', '/')
$payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($claims)) | ConvertFrom-Json
# El tenant lo resuelve el backend por request (D-20): si algun dia aparece
# en el token, es que el contrato dejo de estar congelado.
Check 'el token NO lleva la organizacion (gate #5)' ($null -eq $payload.idOrganizacion) 'el token debe seguir congelado'

$broker = Api POST '/auth/login' $null @{ usuario = 'rsalas'; contrasena = 'Broker2026' }
Check 'login broker' ($broker.rol -eq 'BROKER') $broker.rol
$admin = Connect-ControlLocalE2E $base 'admin@controllocal.test' 'Admin2026'
Check 'login admin' ($admin.rol -eq 'ADMIN') $admin.rol
$hoy = (Get-Date).ToString('yyyy-MM-dd')
$finEncargo = (Get-Date).AddDays(90).ToString('yyyy-MM-dd')

Write-Host "`n== 2. Lectura de la cartera (scope por tenant) ==" -ForegroundColor Cyan
$locales = Api GET '/locales?pagina=1&tamano=10' $agente.token
$totalPrevio = $locales.totalRecords
Check 'GET /locales responde el total del tenant' ($totalPrevio -ge 2) "total=$totalPrevio"
$localesAdmin = Api GET '/locales?pagina=1&tamano=10' $admin.token
Check 'el ADMIN ve el mismo universo (su organizacion)' ($localesAdmin.totalRecords -eq $totalPrevio) "$($localesAdmin.totalRecords) vs $totalPrevio"

Write-Host "`n== 3. Alta de local (estampa el tenant + prospeccion inicial) ==" -ForegroundColor Cyan
$sufijo = Get-Random -Minimum 1000 -Maximum 9999
$nuevoLocal = Api POST '/locales' $agente.token @{
    codigoLocal = "LOC-V6$sufijo"; direccion = 'Av. Multi-tenant 456'; distrito = 'Miraflores'
    metraje = 150.5; precioReferencial = 9200; monedaReferencial = 'PEN'; rubroPermitido = 'Cafeteria'
    idPropietario = 43; estadoPublicacion = 'P'
}
$idLocal = $nuevoLocal.id
Check 'POST /locales crea con defaults v1' ($nuevoLocal.estado -eq 'D' -and $nuevoLocal.tipoInmueble -eq 'L' -and $nuevoLocal.uso -eq 'C') "$($nuevoLocal.estado)/$($nuevoLocal.tipoInmueble)/$($nuevoLocal.uso)"
Check 'la propiedad nace en BROX_LEGACY' ((Sql "select organizacion_id from propiedad where id_propiedad=$idLocal") -eq '1') 'organizacion_id'
Check 'el detalle_local hereda el tenant' ((Sql "select organizacion_id from detalle_local_comercial where id_propiedad=$idLocal") -eq '1') 'organizacion_id'
Check 'la publicacion sincronizada lleva tenant' ((Sql "select count(*) from publicacion where id_propiedad=$idLocal and organizacion_id=1") -eq '1') 'publicacion'
Check 'el alta abrio la prospeccion inicial con tenant' ((Sql "select count(*) from prospeccion where id_propiedad=$idLocal and organizacion_id=1") -eq '1') 'prospeccion inicial'

$totalNuevo = (Api GET '/locales?pagina=1&tamano=10' $agente.token).totalRecords
Check 'el total del tenant subio en 1' ($totalNuevo -eq $totalPrevio + 1) "$totalNuevo vs $totalPrevio"

Write-Host "`n== 4. Ficha, edicion y precios ==" -ForegroundColor Cyan
$ficha = Api GET "/locales/$idLocal" $agente.token
Check 'GET /locales/{id} devuelve la ficha' ($ficha.codigoLocal -eq "LOC-V6$sufijo") $ficha.codigoLocal
Check 'la ficha trae el propietario resuelto' ($null -ne $ficha.propietarioNombre) 'propietarioNombre'

$editado = Api PUT "/locales/$idLocal" $agente.token @{
    codigoLocal = "LOC-V6$sufijo"; direccion = 'Av. Multi-tenant 456'; distrito = 'Miraflores'
    metraje = 150.5; precioReferencial = 9900; monedaReferencial = 'PEN'; rubroPermitido = 'Cafeteria'
    idPropietario = 43; estadoPublicacion = 'P'
}
Check 'PUT actualiza el precio' ($editado.precioReferencial -eq 9900) $editado.precioReferencial
Check 'el hito de precio U nace con tenant' ((Sql "select count(*) from precio_propiedad where id_propiedad=$idLocal and hito='U' and organizacion_id=1") -eq '1') 'hito U'

$precio = Api POST "/locales/$idLocal/precios" $agente.token @{ hito = 'O'; moneda = 'PEN'; monto = 9500 }
Check 'POST /precios registra el hito' ($precio.hito -eq 'O') $precio.hito
Check 'el precio manual lleva tenant' ((Sql "select organizacion_id from precio_propiedad where id_precio=$($precio.id)") -eq '1') 'organizacion_id'

$publicacion = Api POST "/locales/$idLocal/publicaciones" $agente.token @{ canal = 'URBANIA'; rentaPublicada = 9900; moneda = 'PEN'; estado = 'P' }
Check 'POST /publicaciones crea el anuncio' ($publicacion.estado -eq 'P') $publicacion.estado
Check 'la publicacion nueva lleva tenant' ((Sql "select organizacion_id from publicacion where id_publicacion=$($publicacion.id)") -eq '1') 'organizacion_id'

Write-Host "`n== 5. Prospeccion: maquina de estados + auditoria ==" -ForegroundColor Cyan
$prospecciones = Api GET "/prospecciones?idLocal=$idLocal" $agente.token
$idProspeccion = $prospecciones.items[0].id
Check 'la prospeccion inicial esta en P' ($prospecciones.items[0].estado -eq 'P') $prospecciones.items[0].estado
Check 'el correlativo sigue el formato PRO-####' ($prospecciones.items[0].codigoProspeccion -match '^PRO-\d{4}$') $prospecciones.items[0].codigoProspeccion

$p = Api POST "/prospecciones/$idProspeccion/contactar" $agente.token $null
Check 'contactar -> C' ($p.estado -eq 'C') $p.estado
$p = Api POST "/prospecciones/$idProspeccion/reunion" $agente.token $null
Check 'reunion -> R' ($p.estado -eq 'R') $p.estado
$p = Api POST "/prospecciones/$idProspeccion/propuesta" $agente.token $null
# Cable real de la v1: entregar propuesta deja S, nunca emite el estado E.
Check 'propuesta deja S (la v1 nunca emite E)' ($p.estado -eq 'S') $p.estado
Check 'resultadoPropuesta = P' ($p.resultadoPropuesta -eq 'P') $p.resultadoPropuesta

$historial = Sql "select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion"
Check 'las 3 transiciones quedaron auditadas' ($historial -eq '3') "filas=$historial"
$historialSinTenant = Sql "select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=$idProspeccion and organizacion_id is null"
Check 'ninguna fila de auditoria quedo sin tenant' ($historialSinTenant -eq '0') "sin tenant=$historialSinTenant"

Write-Host "`n== 6. Captacion: alta desde la prospeccion y revision del broker ==" -ForegroundColor Cyan
$p = Api POST "/prospecciones/$idProspeccion/captar" $agente.token @{ comisionPactada = 100 }
$idCaptacion = $p.idCaptacion
Check 'captar -> T (captado)' ($p.estado -eq 'T') $p.estado
Check 'se creo la captacion' ($null -ne $idCaptacion) 'idCaptacion'
Check 'el correlativo sigue el formato CAP-####' ($p.captacionCodigo -match '^CAP-\d{4}$') $p.captacionCodigo
Check 'la captacion nace con tenant' ((Sql "select organizacion_id from captacion where id_captacion=$idCaptacion") -eq '1') 'organizacion_id'

$pendientes = Api GET '/captaciones/pendientes?pagina=1&tamano=20' $broker.token
Check 'la captacion aparece en la bandeja del broker' (@($pendientes.items | Where-Object { $PSItem.id -eq $idCaptacion }).Count -eq 1) "total=$($pendientes.totalRecords)"

try {
    Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'O'; observacion = '' } | Out-Null
    Check 'observar sin observacion se rechaza (MEJ-03)' $false 'no lanzo error'
} catch {
    Check 'observar sin observacion se rechaza (MEJ-03)' $true 'ok'
}

$decidida = Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'O'; observacion = 'Falta el plano del local' }
Check 'observar -> O' ($decidida.estado -eq 'O') $decidida.estado

$reenviada = Api PUT "/captaciones/$idCaptacion" $agente.token @{
    fechaCaptacion = $hoy; fechaInicioVigencia = $hoy; fechaFinVigencia = $finEncargo
    comisionPactada = 100; observaciones = 'Plano adjunto'; idLocal = $idLocal
    motivoOperacion = 'A'; urgencia = 3; exclusividad = $true
}
Check 'editar una observada la reenvia a P' ($reenviada.estado -eq 'P') $reenviada.estado

$aprobada = Api POST "/captaciones/$idCaptacion/decision" $broker.token @{ accion = 'A'; observacion = 'Conforme' }
Check 'aprobar -> A (activa)' ($aprobada.estado -eq 'A') $aprobada.estado

$auditoriaCaptacion = Sql "select count(*) from historial_estado where entidad_tipo='CAPTACION' and id_entidad=$idCaptacion and organizacion_id=1"
Check 'las transiciones de captacion se auditaron con tenant' ($auditoriaCaptacion -eq '3') "filas=$auditoriaCaptacion"

Write-Host "`n== 7. mis-locales, reasignacion y cierre ==" -ForegroundColor Cyan
$mios = Api GET '/locales/mis-locales?pagina=1&tamano=50' $agente.token
Check 'el local aparece en mis-locales del agente' (@($mios.items | Where-Object { $PSItem.id -eq $idLocal }).Count -eq 1) "total=$($mios.totalRecords)"

# Agente 29 (Javier Ruiz), tambien supervisado por rsalas en el seed.
$reasignada = Api POST "/captaciones/$idCaptacion/reasignar" $broker.token @{ idAgenteNuevo = 29; motivo = 'Balance de cartera' }
Check 'reasignar cambia el responsable' ($reasignada.idAgente -eq 29) $reasignada.idAgente
Check 'reasignar NO transiciona el estado' ($reasignada.estado -eq 'A') $reasignada.estado
Check 'el evento de reasignacion lleva tenant' ((Sql "select count(*) from reasignacion_captacion where id_captacion=$idCaptacion and organizacion_id=1") -eq '1') 'reasignacion'

$cerrada = Api POST "/captaciones/$idCaptacion/cierre" $broker.token @{ motivo = 'Contrato firmado' }
Check 'cerrar -> C' ($cerrada.estado -eq 'C') $cerrada.estado

Write-Host "`n== 8. Ninguna fila del tenant quedo huerfana ==" -ForegroundColor Cyan
$huerfanas = Sql @"
select coalesce(sum(nulos),0) from (
  select (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I where organizacion_id is null', table_name), false, true, '')))[1]::text::int as nulos
  from information_schema.columns where column_name='organizacion_id' and table_schema='public') t
"@
Check 'cero filas con organizacion_id NULL en toda la BD' ($huerfanas -eq '0') "nulos=$huerfanas"

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
