# Roles actuales C1: cuenta global, atribuciones contextuales y gobierno separado.
# Fixtures NUEVOS; no backfill de credenciales ni compatibilidad GlassFish.
# Incluye gobierno minimo, membresia aceptada y auditoria comercial por actor.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/e2e-context.ps1"
$e2e = Assert-ControlLocalE2EContext
$base = $e2e.BaseUrl
$fixtureE2E = Initialize-ControlLocalE2EActors
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
    try { Write-ControlLocalE2EHttpResult (Invoke-RestMethod @parametros) } catch { MostrarErrorApiDeFixture $_ $metodo $ruta $token $cuerpo; throw }
}

function CodigoDe($metodo, $ruta, $token, $cuerpo) {
    try {
        Api $metodo $ruta $token $cuerpo | Out-Null
        return 200
    } catch {
        return [int]$_.Exception.Response.StatusCode.value__
    }
}



$hoy=(Get-Date).ToString('yyyy-MM-dd')
$sufijo=[guid]::NewGuid().ToString('N').Substring(0,12)
Write-Host "`n== 1. Cuenta global y pertenencia, sin usuarios heredados ==" -ForegroundColor Cyan
$estructura=Invoke-ControlLocalE2ESql @"
select count(*) filter(where administrar)||'|'||count(*) filter(where rol='BROKER')||'|'||count(*) filter(where rol='AGENTE')||'|'||count(*) filter(where rol in ('ADMIN','TENANT_ADMIN'))
from usuario_organizacion where organizacion_id=$($fixtureE2E.organizacion) and estado='A';
"@
Check 'una atribucion de gobierno separada de tres brokers y dos agentes' ($estructura.Trim() -eq '1|3|2|0') 'estructura inicial'
$dobles=[int](Invoke-ControlLocalE2ESql 'select count(*) from (select id_usuario from usuario_organizacion where estado=''A'' group by id_usuario having count(*)>1) q')
Check 'ninguna cuenta tiene dos organizaciones activas' ($dobles -eq 0) 'pertenencia unica'

Write-Host "`n== 2. Contexto explicito y autoridad del servidor ==" -ForegroundColor Cyan
$admin=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.admin.correo -Contrasena 'Admin2026' -Organizacion $fixtureE2E.organizacion -Rol 'TENANT_ADMIN' -EnrolarSiempre
$broker=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.rsalas.correo -Contrasena 'Broker2026' -Organizacion $fixtureE2E.organizacion -Rol 'BROKER' -EnrolarSiempre
$agente=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.vmora.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
$agenteAjeno=Connect-ControlLocalE2E -BaseUrl $base -Correo $fixtureE2E.actores.ltorres.correo -Contrasena 'Agente2026' -Organizacion $fixtureE2E.organizacion -Rol 'AGENTE'
foreach($actor in @($admin,$broker,$agente)){
 $s=Api GET '/sesion' $actor.token $null
 Check ('Core resuelve la atribucion '+$actor.rol) ($s.rol -eq $actor.rol -and $s.idOrganizacion -eq $fixtureE2E.organizacion -and $s.idCuenta -eq $actor.idCuenta) 'sesion actual'
}
$idGobierno=[long](Invoke-ControlLocalE2ESql "select id_persona_rol from persona_rol where organizacion_id=$($fixtureE2E.organizacion) and id_persona=$($fixtureE2E.actores.admin.persona) and tipo_rol='ADMIN' and vigencia_hasta is null")
Check 'el gobierno usa su propia atribucion, aunque la persona tambien sea broker' ($admin.idDominio -eq $idGobierno) 'dominio contextual'
$jwt=$admin.token.Split('.')
$primerCaracter=if($jwt[2][0] -eq 'A'){'B'}else{'A'}
$firmaAlterada=$primerCaracter+$jwt[2].Substring(1)
$alterado=$jwt[0]+'.'+$jwt[1]+'.'+$firmaAlterada
Check 'alterar la firma del JWT no crea autoridad' ((CodigoDe GET '/cuenta' $alterado $null) -eq 401) 'firma invalida'

Write-Host "`n== 3. Gobierno minimo; sin cartera comercial ==" -ForegroundColor Cyan
foreach($ruta in @('/accesos','/agentes?pagina=1&tamano=20','/brokers?pagina=1&tamano=20','/captaciones/reasignables?pagina=1&tamano=20')){
 Check ('gobierno conserva su proyeccion '+($ruta -split '\?')[0]) ((CodigoDe GET $ruta $admin.token $null) -eq 200) 'proyeccion propia'
}
foreach($ruta in @('/captaciones/pendientes?pagina=1&tamano=1','/captaciones/propiedades-equipo?pagina=1&tamano=1','/captaciones/propiedades-equipo/resumen','/evaluaciones?pagina=1&tamano=1','/interacciones?pagina=1&tamano=1')){
 Check ('gobierno no consulta operacion '+($ruta -split '\?')[0]) ((CodigoDe GET $ruta $admin.token $null) -eq 403) 'operacion denegada'
}
Check 'broker conserva revision comercial' ((CodigoDe GET '/captaciones/pendientes?pagina=1&tamano=1' $broker.token $null) -eq 200) 'broker'
Check 'broker no administra accesos personales' ((CodigoDe GET '/accesos' $broker.token $null) -eq 403) 'gobierno denegado'
Check 'broker no edita perfil profesional ajeno como gobierno' ((CodigoDe PUT "/agentes/$($agente.idDominio)" $broker.token @{nombre='DATO_TEST_PROHIBIDO'}) -eq 403) 'gobierno denegado'
foreach($actor in @($admin,$broker,$agente)){
 Check ('la creacion antigua de credenciales no existe para '+$actor.rol) ((CodigoDe POST '/agentes' $actor.token @{nombre='VIA_RETIRADA_TEST'}) -in @(404,405)) 'via retirada'
}

Write-Host "`n== 4. Ultimo gobierno: puerta Core y rollback diferido reales ==" -ForegroundColor Cyan
$membresia=(Invoke-ControlLocalE2ESql "select id_usuario_organizacion||'|'||version from usuario_organizacion where organizacion_id=$($fixtureE2E.organizacion) and id_usuario=$($fixtureE2E.actores.admin.cuenta) and estado='A'").Trim().Split('|')
$denegada=CodigoDe POST "/membresias/$($membresia[0])/baja" $admin.token @{version=[long]$membresia[1];motivo='Prueba TEST del ultimo gobierno'}
Check 'no se retira el ultimo gobierno por HTTP' ($denegada -in @(400,409)) 'invariante Core'
$estadoAntes=Invoke-ControlLocalE2ESql "select nombre from organizacion where id_organizacion=$($fixtureE2E.organizacion)"
# Control positivo del SQL/columnas/permisos: no aceptar cualquier error como guarda.
Invoke-ControlLocalE2ESql "begin; update organizacion set nombre='CONTROL_TEST_ROLLBACK' where id_organizacion=$($fixtureE2E.organizacion); update usuario_organizacion set administrar=administrar where id_usuario_organizacion=$($membresia[0]); set constraints all immediate; rollback;"|Out-Null
$rechazo=$false
try{Invoke-ControlLocalE2ESql "begin; update organizacion set nombre='MARCA_TEST_NO_DEBE_COMMIT' where id_organizacion=$($fixtureE2E.organizacion); update usuario_organizacion set administrar=false where id_usuario_organizacion=$($membresia[0]); set constraints all immediate; commit;"|Out-Null}catch{$rechazo=$true}
$estadoDespues=Invoke-ControlLocalE2ESql "select nombre from organizacion where id_organizacion=$($fixtureE2E.organizacion)"
$gov=[int](Invoke-ControlLocalE2ESql "select count(*) from usuario_organizacion where id_usuario_organizacion=$($membresia[0]) and estado='A' and administrar")
Check 'el trigger rechaza la perdida del ultimo gobierno' $rechazo 'rollback'
Check 'la marca y la baja se revierten juntas' ($estadoAntes -eq $estadoDespues -and $gov -eq 1) 'sin commit parcial'
Check 'la sesion correcta conserva autoridad tras el rechazo' ((CodigoDe GET '/accesos' $admin.token $null) -eq 200) 'sesion revalidada'

Write-Host "`n== 5. Gobierno invita; el titular acepta sin ceder credenciales ==" -ForegroundColor Cyan
$nueva=NuevaCuentaVerificadaE2E -Usuario ('e2e-roles-'+$sufijo) -Correo ('e2e-roles-'+$sufijo+'@example.invalid') -Contrasena 'Frase propia de prueba 2026' -Nombre 'Profesional ficticio TEST de roles'
$cuentasAntes=[int](Invoke-ControlLocalE2ESql 'select count(*) from cuenta_acceso')
$datos=@{correoCuenta=$nueva.correo;persona=@{nombre='Profesional ficticio TEST de roles';tipoPersona='N';correoLaboral=('laboral-roles-'+$sufijo+'@example.invalid')};rolPrincipal='AGENTE';administrar=$false;idBrokerSupervisor=$broker.idDominio;codigoInterno=('ER-'+$sufijo);zona='TEST';estadoOperativo='D';venceEn=([DateTimeOffset]::UtcNow.AddDays(1).ToString('o'));motivo='Invitacion ficticia TEST de pertenencia'}
Check 'el agente no emite invitaciones' ((CodigoDe POST '/membresias/invitaciones' $agente.token $datos) -eq 403) 'permiso'
Check 'el broker no emite invitaciones' ((CodigoDe POST '/membresias/invitaciones' $broker.token $datos) -eq 403) 'permiso'
$inv=Invoke-ComandoC2DePrueba POST '/membresias/invitaciones' $admin.token $datos ('roles-inv-'+$sufijo)
Check 'la invitacion no devuelve password ni token de identidad' ($inv.referencia -and $null -eq $inv.token -and $null -eq $inv.contrasena) 'pertenencia separada'
$identidad=Api POST '/auth/mfa/desafio' $null @{correo=$nueva.correo;contrasena='Frase propia de prueba 2026'}
Check 'la cuenta sin pertenencia autentica solo como identidad' ($identidad.contextoRequerido -and $identidad.token -and $null -eq $identidad.desafio) 'identidad sin cartera'
$aceptada=Api POST "/membresias/invitaciones/$($inv.referencia)/aceptar" $identidad.token $null
Check 'el titular acepta la pertenencia concreta' ($aceptada.idOrganizacion -eq $fixtureE2E.organizacion -and $aceptada.rolPrincipal -eq 'AGENTE') 'aceptacion propia'
$cuentasDespues=[int](Invoke-ControlLocalE2ESql 'select count(*) from cuenta_acceso')
Check 'gobierno no creo otra cuenta al invitar' ($cuentasAntes -eq $cuentasDespues) 'sin credencial paralela'
$perfil=Api GET "/agentes/$($aceptada.idRolOperativo)/perfil" $admin.token $null
Check 'el perfil profesional corresponde al rol aceptado' ($perfil.id -eq $aceptada.idRolOperativo) 'directorio actual'
$intervalo=Invoke-ControlLocalE2ESql "select (alta_real_en is not null)::text||'|'||(baja_real_en is null)::text from usuario_organizacion where id_usuario_organizacion=$($aceptada.idMembresia)"
Check 'la pertenencia conserva sus fechas reales' ($intervalo.Trim() -eq 'true|true') 'intervalo real'

Write-Host "`n== 6. Decidir y reasignar: actor efectivo y limites propios ==" -ForegroundColor Cyan
$propietario=NuevoPropietarioC2DePrueba -Token $agente.token -Nombre 'Propietario ficticio TEST de roles'
$local=NuevoInmuebleConEncargo -LocalIndependienteTest -Token $agente.token -Direccion 'Predio ficticio TEST de roles' -IdPropietario $propietario.id -Metraje 100 -Rubro 'Comercio TEST' -Importe 1000 -Moneda 'PEN' -TipoComision 'E' -BaseCalculo 'R' -ValorComision 1 -TratamientoIgv 'N' -InicioEncargo $hoy -FinEncargo (Get-Date).AddDays(90).ToString('yyyy-MM-dd') -Exclusividad $true
$activa=HabilitarEncargoC2DePrueba -IdEncargo $local.idEncargo -TokenResponsable $agente.token -TokenDecisor $broker.token -MonedaComision 'PEN' -Exclusividad $true -HitoDevengo 'Firma del contrato' -ParteObligada 'Propietario TEST' -Beneficiario 'Agencia TEST' -Exigibilidad 'A la firma del contrato' -FechaFirma $hoy
$auditada=[int](Invoke-ControlLocalE2ESql "select count(*) from revision_encargo where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($local.idEncargo) and actuacion='APROBAR' and id_rol_actor=$($broker.idDominio)")
Check 'la aprobacion traza al broker real, no al gobernador de la cuenta' ($auditada -eq 1) 'revision inmutable'
$reasignada=Invoke-ComandoC2DePrueba POST "/captaciones/$($local.idEncargo)/reasignar" $broker.token @{idAgenteNuevo=$aceptada.idRolOperativo;idAgenteObservado=$activa.responsable;versionEsperada=$activa.version;motivo='Reasignacion interna del equipo TEST'} ('roles-reasig-broker-'+$sufijo)
Check 'broker reasigna dentro de su equipo sin cambiar estado' ($reasignada.idAgente -eq $aceptada.idRolOperativo -and $reasignada.estado -eq 'A') 'ambito broker'
$cruce=Invoke-ComandoC2DePrueba POST "/captaciones/$($local.idEncargo)/reasignar" $admin.token @{idAgenteNuevo=$agenteAjeno.idDominio;idAgenteObservado=$reasignada.idAgente;versionEsperada=$reasignada.version;motivo='Cruce organizacional autorizado TEST'} ('roles-reasig-gov-'+$sufijo)
Check 'gobierno usa su atribucion de cruce, sin aprobar el Encargo' ($cruce.idAgente -eq $agenteAjeno.idDominio -and $cruce.estado -eq 'A') 'gobierno acotado'
$actorReasignacion=Invoke-ControlLocalE2ESql "select string_agg(tipo_rol_actor,'|' order by id_reasignacion) from reasignacion_captacion where organizacion_id=$($fixtureE2E.organizacion) and id_captacion=$($local.idEncargo)"
Check 'cada cambio conserva su atribucion efectiva' ($actorReasignacion.Trim() -eq 'BROKER|TENANT_ADMIN') 'auditoria por actuacion'
Check 'el cruce no concede lectura comercial al gobierno' ((CodigoDe GET "/encargos/$($local.idEncargo)" $admin.token $null) -eq 403) 'gobernar no operar'

Write-Host "`n===== $ok OK / $fail FALLAS =====" -ForegroundColor $(if($fail -eq 0){'Green'}else{'Red'})
if($fail -gt 0){exit 1}
