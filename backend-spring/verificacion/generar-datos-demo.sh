#!/usr/bin/env bash
#
# Generador de datos de demostracion para la base de DESARROLLO.
#
# QUE HACE. Recorre la cadena completa del negocio usando EXCLUSIVAMENTE la API
# real -ningun INSERT directo-, asi que cada fila queda con su auditoria, sus
# transiciones y sus alertas/tareas derivadas. De paso, la corrida ejercita los
# flujos de escritura de punta a punta.
#
#   propietario -> local -> prospeccion -> captacion -> decision del broker
#   -> publicacion -> cliente -> requerimiento -> oportunidad -> visita
#   -> interaccion -> solicitud -> expediente -> conformidad -> reenvio
#   -> evaluacion -> contrato -> comision
#
# PARA QUE. La semilla de las migraciones deja 2 locales y 0 contratos: alcanza
# para ver cada pantalla renderizar, no para ejercitar bandejas, filtros,
# paginacion ni KPI. Esto llena esa parte.
#
# NO ES UNA SUITE E2E. No verifica nada ni tiene marcador de checks: escribe.
# Las suites viven en `e2e-*.ps1` y no dependen de esto.
#
# USO
#   bash backend-spring/verificacion/generar-datos-demo.sh
#
# Requiere el API en http://localhost:8090 y `node` en el PATH (solo para leer
# JSON). Es RE-EJECUTABLE: las personas se reutilizan por numero de documento
# en vez de chocar contra el 409 de duplicado. Lo que ya avanzo de estado no se
# repite, asi que una segunda corrida agrega menos que la primera.
#
# ----------------------------------------------------------------------------
# CUATRO TRAMPAS DEL CABLE QUE ESTE SCRIPT YA SORTEA. Si se toca, conservarlas:
#
# 1. `POST /prospecciones/{id}/captar` DEVUELVE LA PROSPECCION, no la captacion.
#    El id de la captacion viaja en `idCaptacion`. Leer `id` da el de la
#    prospeccion y las llamadas siguientes fallan con "Captacion no encontrado"
#    sobre ids que existen pero son de otra entidad.
#
# 2. `captar` crea la captacion como BORRADOR con `exclusividad` en NULL, y
#    `validarActivacion` la exige. Sin un `PUT /captaciones/{id}` que complete
#    el encargo, la decision del broker responde "La captacion no puede
#    activarse sin condicion economica, moneda, exclusividad, propietario,
#    agente y local.". Mismo orden que sigue `e2e-f4-solicitud.ps1`.
#
# 3. La solicitud nace REGISTRADA (G) y el broker solo evalua desde EN REVISION
#    (E). El puente es `POST /solicitudes/{id}/reenviar`; sin el, G -> A es
#    transicion no permitida.
#
# 4. La comision pide campos concretos: `asignar` exige `montoAgente` y `cobro`
#    exige `estado` (C o A). Mandar solo observaciones responde 400.
# ----------------------------------------------------------------------------
set -uo pipefail

BASE=${CONTROLLOCAL_API:-http://localhost:8090/controllocal/Api}
USUARIO_AGENTE=${AGENTE_USUARIO:-vmora}
CLAVE_AGENTE=${AGENTE_CLAVE:-Agente2026}
USUARIO_BROKER=${BROKER_USUARIO:-rsalas}
CLAVE_BROKER=${BROKER_CLAVE:-Broker2026}

log()  { printf '%s\n' "$*"; }
paso() { printf '\n== %s ==\n' "$*"; }

CONT=$(mktemp -d)
trap 'rm -rf "$CONT"' EXIT
: > "$CONT/ok"; : > "$CONT/fallo"; : > "$CONT/detalle"

# Devuelve "codigo<TAB>cuerpo". Nada de variables globales para el codigo HTTP:
# las funciones se invocan dentro de $(...), que es una subshell, y una
# asignacion ahi dentro nunca llegaria al llamador.
api() {
    local metodo=$1 ruta=$2 token=$3 cuerpo=${4:-}
    local resp codigo
    if [ -n "$cuerpo" ]; then
        resp=$(curl -s -m 60 -X "$metodo" "$BASE$ruta" \
            -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
            -d "$cuerpo" -w $'\n%{http_code}')
    else
        resp=$(curl -s -m 60 -X "$metodo" "$BASE$ruta" \
            -H "Authorization: Bearer $token" -w $'\n%{http_code}')
    fi
    codigo=$(printf '%s' "$resp" | tail -n1)
    printf '%s\t%s' "$codigo" "$(printf '%s' "$resp" | sed '$d' | tr -d '\n')"
}

# Lee un campo del cuerpo JSON. Por defecto el id de la entidad creada; el
# quinto argumento permite pedir otro (`idCaptacion`, ver trampa 1).
crear() {
    local etiqueta=$1 metodo=$2 ruta=$3 token=$4 cuerpo=${5:-} campo=${6:-id}
    local resp codigo body
    resp=$(api "$metodo" "$ruta" "$token" "$cuerpo")
    codigo=${resp%%$'\t'*}; body=${resp#*$'\t'}
    if [[ "$codigo" =~ ^2 ]]; then
        echo x >> "$CONT/ok"
        printf '%s' "$body" | CAMPO="$campo" node -e "
            let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
                try{const j=JSON.parse(s);const v=j[process.env.CAMPO];
                    process.stdout.write(v==null?'':String(v))}catch(e){}})" 2>/dev/null
    else
        echo x >> "$CONT/fallo"
        printf '%s -> HTTP %s :: %s\n' "$etiqueta" "$codigo" \
            "$(printf '%s' "$body" | head -c 200)" >> "$CONT/detalle"
    fi
}

# Subida octet-stream: tipo y nombre viajan en el query string.
subir() {
    local sid=$1 token=$2 tipo=$3 nombre=$4 codigo
    codigo=$(printf '%%PDF-1.4 documento de demostracion (%s)' "$tipo" | \
        curl -s -m 60 -X POST \
            "$BASE/solicitudes/$sid/documentos/archivo?tipoDocumento=$tipo&nombreArchivo=$nombre" \
            -H "Authorization: Bearer $token" -H "Content-Type: application/octet-stream" \
            --data-binary @- -o /dev/null -w '%{http_code}')
    if [[ "$codigo" =~ ^2 ]]; then
        echo x >> "$CONT/ok"
    else
        echo x >> "$CONT/fallo"
        printf 'documento %s sol %s -> HTTP %s\n' "$tipo" "$sid" "$codigo" >> "$CONT/detalle"
    fi
}

# Entra por /auth/mfa/desafio, no por /auth/login: es el camino unico del SPA y
# responde 200 sin segundo factor y 202 + desafio con el. Una cuenta con MFA
# activo NO entra por /auth/login (D-S0-22), y ahi el 401 despista.
login() {
    curl -s -m 60 -X POST "$BASE/auth/mfa/desafio" -H "Content-Type: application/json" \
        -d "{\"usuario\":\"$1\",\"contrasena\":\"$2\"}" \
        | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
            try{process.stdout.write(JSON.parse(s).token||'')}catch(e){}})"
}

# Mapa "numeroDocumento -> id" para reutilizar personas ya existentes.
mapa_por_documento() {
    local ruta=$1 token=$2 resp
    resp=$(api GET "$ruta?pagina=1&tamano=200" "$token")
    printf '%s' "${resp#*$'\t'}" | node -e "
        let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
            try{const j=JSON.parse(s);
                const a=j.items||Object.values(j).find(Array.isArray)||[];
                a.forEach(p=>{if(p.numeroDocumento)console.log(p.numeroDocumento+'='+p.id)})
            }catch(e){}})"
}
id_de() { grep -m1 "^$1=" "$2" 2>/dev/null | cut -d= -f2; }

# Lo mismo para locales, que se identifican por `codigoLocal` y no por
# documento. Sin esto una re-ejecucion reporta un fallo por cada local ya
# existente, y ese ruido esconde los fallos de verdad.
mapa_por_codigo_local() {
    local token=$1 resp
    resp=$(api GET "/locales?page=1&tamano=200" "$token")
    printf '%s' "${resp#*$'\t'}" | node -e "
        let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
            try{const j=JSON.parse(s);
                const a=j.items||Object.values(j).find(Array.isArray)||[];
                a.forEach(l=>{if(l.codigoLocal)console.log(l.codigoLocal+'='+l.id)})
            }catch(e){}})"
}

# ---------------------------------------------------------------- 0. sesiones
paso "Autenticacion"
AGE=$(login "$USUARIO_AGENTE" "$CLAVE_AGENTE")
BRK=$(login "$USUARIO_BROKER" "$CLAVE_BROKER")
if [ -z "$AGE" ] || [ -z "$BRK" ]; then
    log "ERROR: no se pudo autenticar (agente='$USUARIO_AGENTE' broker='$USUARIO_BROKER')."
    log "Comprueba que el API responde en $BASE/salud"
    exit 1
fi
log "agente $USUARIO_AGENTE OK / broker $USUARIO_BROKER OK"

# ----------------------------------------------------------- 1. propietarios
paso "Propietarios"
mapa_por_documento /propietarios "$AGE" > "$CONT/props.map"
PROPS=()
while IFS='|' read -r tp td doc nom tel cor; do
    [ -z "$tp" ] && continue
    id=$(id_de "$doc" "$CONT/props.map")
    if [ -z "$id" ]; then
        id=$(crear "propietario $nom" POST /propietarios "$AGE" \
            "{\"tipoPersona\":\"$tp\",\"tipoDocumento\":\"$td\",\"numeroDocumento\":\"$doc\",\"nombre\":\"$nom\",\"telefono\":\"$tel\",\"correo\":\"$cor\",\"consentimientoUsoDato\":true,\"estado\":\"A\"}")
    fi
    [ -n "$id" ] && PROPS+=("$id")
done <<'EOF'
N|D|41200311|Rosa Elena Iparraguirre|987400311|riparraguirre@demo.pe
N|D|41200312|Manuel Tapia Cordova|987400312|mtapia@demo.pe
J|R|20512300313|Inversiones Santa Cruz SAC|987400313|contacto@santacruz.pe
N|D|41200314|Carmen Delgado Rios|987400314|cdelgado@demo.pe
J|R|20512300315|Corporacion Vento SAC|987400315|admin@vento.pe
N|D|41200316|Julio Bravo Mendieta|987400316|jbravo@demo.pe
N|D|41200317|Silvia Paredes Luna|987400317|sparedes@demo.pe
J|R|20512300318|Grupo Aurora Peru SAC|987400318|ventas@aurora.pe
EOF
log "propietarios disponibles: ${#PROPS[@]}"
if [ ${#PROPS[@]} -eq 0 ]; then
    log "ERROR: sin propietarios no hay nada de que colgar el resto."
    cat "$CONT/detalle"
    exit 1
fi

# ----------------------------------------------------------------- 2. locales
# El alta de local crea ademas su prospeccion inicial.
paso "Locales"
mapa_por_codigo_local "$AGE" > "$CONT/locales.map"
n=0; nuevos=0; existentes=0
while IFS='|' read -r cod dir dist met precio mon rubro tipo amb ant; do
    [ -z "$cod" ] && continue
    if [ -n "$(id_de "$cod" "$CONT/locales.map")" ]; then
        existentes=$((existentes+1)); n=$((n+1)); continue
    fi
    prop=${PROPS[$((n % ${#PROPS[@]}))]}
    crear "local $cod" POST /locales "$AGE" \
        "{\"codigoLocal\":\"$cod\",\"direccion\":\"$dir\",\"distrito\":\"$dist\",\"metraje\":$met,\"precioReferencial\":$precio,\"monedaReferencial\":\"$mon\",\"rubroPermitido\":\"$rubro\",\"descripcion\":\"$rubro en $dist, listo para operar\",\"idPropietario\":$prop,\"estado\":\"D\",\"tipoInmueble\":\"$tipo\",\"uso\":\"C\",\"ambientes\":$amb,\"antiguedadAnios\":$ant,\"zonaUrbanizacion\":\"$dist\",\"estadoPublicacion\":\"B\"}" >/dev/null
    nuevos=$((nuevos+1)); n=$((n+1))
done <<'EOF'
LOC-D010|Av. Benavides 2145|Miraflores|85.0|3800|PEN|Cafeteria|L|2|6
LOC-D011|Jr. de la Union 680|Lima Cercado|140.0|5200|PEN|Tienda por departamento|L|4|22
LOC-D012|Av. Angamos Este 1520|Surquillo|62.5|2600|PEN|Peluqueria|L|2|10
LOC-D013|Av. La Marina 2380|San Miguel|210.0|8900|PEN|Restaurante|L|5|14
LOC-D014|Calle Las Begonias 415|San Isidro|95.0|3200|USD|Oficina administrativa|O|3|7
LOC-D015|Av. Primavera 1180|Santiago de Surco|175.0|7400|PEN|Gimnasio|L|4|9
LOC-D016|Av. Tomas Marsano 3320|Surquillo|58.0|2300|PEN|Farmacia|L|2|16
LOC-D017|Av. Javier Prado Este 4200|La Molina|320.0|14500|PEN|Showroom automotriz|L|6|11
LOC-D018|Jr. Huallaga 320|Lima Cercado|48.0|1900|PEN|Bodega|L|1|30
LOC-D019|Av. Canaval y Moreyra 780|San Isidro|130.0|4600|USD|Oficina corporativa|O|4|8
LOC-D020|Av. Universitaria 5680|Los Olivos|110.0|3400|PEN|Panaderia|L|3|12
LOC-D021|Av. Grau 1450|Barranco|72.0|2900|PEN|Bar restaurante|L|2|25
LOC-D022|Av. Salaverry 2890|Jesus Maria|155.0|5800|PEN|Clinica dental|L|5|13
LOC-D023|Calle Berlin 390|Miraflores|66.0|3100|PEN|Heladeria|L|2|18
LOC-D024|Av. Arequipa 3120|Lince|190.0|6700|PEN|Academia preuniversitaria|L|6|20
LOC-D025|Av. El Polo 670|Santiago de Surco|88.0|4100|USD|Boutique|L|3|5
LOC-D026|Av. Colonial 2450|Callao|260.0|7200|PEN|Deposito comercial|L|3|28
LOC-D027|Av. Pardo 1120|Miraflores|105.0|4800|USD|Agencia bancaria|L|4|9
EOF
log "locales: $nuevos nuevos, $existentes ya existian"

# ---------------------------------------------------------------- 3. clientes
paso "Clientes"
mapa_por_documento /clientes "$AGE" > "$CONT/clientes.map"
CLIENTES=()
while IFS='|' read -r tp td doc nom tel cor rubro; do
    [ -z "$tp" ] && continue
    id=$(id_de "$doc" "$CONT/clientes.map")
    if [ -z "$id" ]; then
        id=$(crear "cliente $nom" POST /clientes "$AGE" \
            "{\"tipoPersona\":\"$tp\",\"tipoDocumento\":\"$td\",\"numeroDocumento\":\"$doc\",\"nombre\":\"$nom\",\"telefono\":\"$tel\",\"correo\":\"$cor\",\"rubroComercial\":\"$rubro\",\"consentimientoContacto\":true,\"consentimientoUsoDato\":true,\"estado\":\"A\"}")
    fi
    [ -n "$id" ] && CLIENTES+=("$id")
done <<'EOF'
N|D|42300401|Andrea Villanueva Soto|986500401|avillanueva@demo.pe|Gastronomia
J|R|20512400402|Franquicias Andinas SAC|986500402|expansion@andinas.pe|Comida rapida
N|D|42300403|Diego Fernandez Rojas|986500403|dfernandez@demo.pe|Retail
J|R|20512400404|Servicios Medicos Peru SAC|986500404|sedes@smperu.pe|Salud
N|D|42300405|Lucia Espinoza Malca|986500405|lespinoza@demo.pe|Belleza
J|R|20512400406|Educa Group SAC|986500406|locales@educagroup.pe|Educacion
N|D|42300407|Ricardo Palomino Vera|986500407|rpalomino@demo.pe|Fitness
J|R|20512400408|Distribuidora Norte SAC|986500408|almacenes@dnorte.pe|Logistica
N|D|42300409|Patricia Zegarra Nunez|986500409|pzegarra@demo.pe|Moda
J|R|20512400410|Cafe Central SAC|986500410|nuevoslocales@cafecentral.pe|Cafeterias
EOF
log "clientes disponibles: ${#CLIENTES[@]}"

# ----------------------------------------------------------- 4. requerimientos
paso "Requerimientos (busquedas declaradas)"
r=0
for cli in "${CLIENTES[@]}"; do
    case $((r % 4)) in
        0) rubro="Gastronomia"; tipo="LOCAL_COMERCIAL"; min=2000; max=6000; mmin=60; mmax=180
           dist='["Miraflores","Surquillo","Barranco"]';;
        1) rubro="Retail"; tipo="LOCAL_COMERCIAL"; min=3000; max=9000; mmin=90; mmax=260
           dist='["Lima Cercado","San Miguel","Los Olivos"]';;
        2) rubro="Servicios"; tipo="OFICINA"; min=2500; max=8000; mmin=70; mmax=200
           dist='["San Isidro","Jesus Maria","Lince"]';;
        *) rubro="Salud"; tipo="LOCAL_COMERCIAL"; min=2800; max=7500; mmin=80; mmax=220
           dist='["Santiago de Surco","La Molina","Miraflores"]';;
    esac
    crear "requerimiento cliente $cli" POST /requerimientos "$AGE" \
        "{\"idCliente\":$cli,\"rubro\":\"$rubro\",\"tipoInmueble\":\"$tipo\",\"rentaMin\":$min,\"rentaMax\":$max,\"moneda\":\"PEN\",\"metrajeMin\":$mmin,\"metrajeMax\":$mmax,\"estado\":\"A\",\"observaciones\":\"Busqueda activa registrada por el agente\",\"distritos\":$dist}" >/dev/null
    r=$((r+1))
done
log "requerimientos: $r"

# ------------------------------------------------- 5. prospecciones -> captar
# Se varia el recorrido a proposito: si todas terminan igual, la bandeja
# muestra una sola columna y no sirve para probar filtros.
paso "Prospecciones: avance y captacion"
RESP=$(api GET "/prospecciones?pagina=1&tamano=200" "$AGE")
mapfile -t PENDIENTES < <(printf '%s' "${RESP#*$'\t'}" | node -e "
    let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
        try{const j=JSON.parse(s);(j.items||[]).filter(p=>p.estado==='P')
            .forEach(p=>console.log(p.id))}catch(e){}})")
log "prospecciones en estado P: ${#PENDIENTES[@]}"

CAPTACIONES=()
k=0
for pid in "${PENDIENTES[@]}"; do
    crear "contactar $pid" POST "/prospecciones/$pid/contactar" "$AGE" \
        '{"observaciones":"Primer contacto telefonico con el propietario"}' >/dev/null
    if [ $((k % 6)) -eq 5 ]; then
        crear "rechazar $pid" POST "/prospecciones/$pid/rechazar" "$AGE" \
            '{"observacion":"El propietario prefiere vender, no alquilar"}' >/dev/null
    else
        crear "reunion $pid" POST "/prospecciones/$pid/reunion" "$AGE" \
            '{"observaciones":"Reunion en el local para revisar condiciones"}' >/dev/null
        crear "propuesta $pid" POST "/prospecciones/$pid/propuesta" "$AGE" \
            '{"observaciones":"Propuesta de encargo entregada"}' >/dev/null
        if [ $((k % 6)) -eq 4 ]; then
            crear "seguimiento $pid" POST "/prospecciones/$pid/seguimiento" "$AGE" \
                '{"observaciones":"Propietario evalua la propuesta"}' >/dev/null
        else
            # TRAMPA 1: el id de la captacion viaja en `idCaptacion`.
            cid=$(crear "captar $pid" POST "/prospecciones/$pid/captar" "$AGE" \
                '{"comisionPactada":100.00}' idCaptacion)
            [ -n "$cid" ] && CAPTACIONES+=("$cid")
        fi
    fi
    k=$((k+1))
done
log "captaciones creadas: ${#CAPTACIONES[@]}"

# ------------------------------------------- 6. completar encargo + decision
# TRAMPA 2: sin este PUT la captacion no puede activarse.
paso "Completar el encargo y decision del broker"
RESP=$(api GET "/captaciones?pagina=1&tamano=200" "$AGE")
mapfile -t PARES < <(printf '%s' "${RESP#*$'\t'}" | node -e "
    let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
        try{const j=JSON.parse(s);(j.items||[]).filter(c=>c.estado==='P')
            .forEach(c=>console.log(c.id+'|'+c.idLocal))}catch(e){}})")

ACTIVAS=(); LOCAL_DE=()
d=0
for par in "${PARES[@]}"; do
    cid=${par%%|*}; loc=${par##*|}
    excl=$([ $((d % 3)) -eq 0 ] && echo true || echo false)
    crear "completar cap $cid" PUT "/captaciones/$cid" "$AGE" \
        "{\"fechaCaptacion\":\"2026-08-01\",\"fechaInicioVigencia\":\"2026-08-01\",\"fechaFinVigencia\":\"2027-02-01\",\"comisionPactada\":100,\"idLocal\":$loc,\"motivoOperacion\":\"A\",\"urgencia\":3,\"exclusividad\":$excl,\"observaciones\":\"Encargo con condiciones acordadas con el propietario\"}" >/dev/null
    case $((d % 9)) in
        7) crear "observar cap $cid" POST "/captaciones/$cid/decision" "$BRK" \
               '{"accion":"O","observacion":"Falta adjuntar la partida registral del inmueble"}' >/dev/null;;
        8) crear "rechazar cap $cid" POST "/captaciones/$cid/decision" "$BRK" \
               '{"accion":"R","observacion":"La comision pactada no cubre el costo de gestion"}' >/dev/null;;
        *) if [ -n "$(crear "aprobar cap $cid" POST "/captaciones/$cid/decision" "$BRK" \
               '{"accion":"A","observacion":"Encargo conforme"}')" ]; then
               ACTIVAS+=("$cid"); LOCAL_DE+=("$loc")
           fi;;
    esac
    d=$((d+1))
done
log "captaciones aprobadas: ${#ACTIVAS[@]}"

# ----------------------------------------------------------- 7. publicaciones
paso "Publicaciones de la cartera activa"
p=0
for loc in "${LOCAL_DE[@]}"; do
    case $((p % 4)) in
        0) canal="URBANIA";; 1) canal="ADONDEVIVIR";;
        2) canal="FACEBOOK";; *) canal="WEB_PROPIA";;
    esac
    crear "publicacion local $loc" POST "/locales/$loc/publicaciones" "$AGE" \
        "{\"canal\":\"$canal\",\"estado\":\"P\",\"rentaPublicada\":$(( 2800 + p * 520 )),\"moneda\":\"PEN\",\"urlPublicacion\":\"https://demo.test/aviso-$loc\"}" >/dev/null
    p=$((p+1))
done
log "publicaciones: $p"

# ----------------------------------------------------------- 8. oportunidades
paso "Oportunidades"
OPORT=()
o=0
for cid in "${ACTIVAS[@]}"; do
    cli=${CLIENTES[$((o % ${#CLIENTES[@]}))]}
    id=$(crear "oportunidad cap $cid" POST /oportunidades "$AGE" \
        "{\"idCliente\":$cli,\"idCaptacion\":$cid,\"observaciones\":\"Interes generado desde la cartera publicada\"}")
    [ -n "$id" ] && OPORT+=("$id")
    o=$((o+1))
done
log "oportunidades creadas: ${#OPORT[@]}"

# -------------------------------------------------- 9. visitas e interacciones
paso "Visitas e interacciones"
v=0
for oid in "${OPORT[@]}"; do
    crear "visita op $oid" POST /visitas "$AGE" \
        "{\"idOportunidad\":$oid,\"fechaVisita\":\"$(printf '2026-08-%02d' $(( (v % 25) + 1 )))\",\"horaVisita\":\"10:30\",\"observaciones\":\"Visita coordinada con el cliente\"}" >/dev/null
    case $((v % 3)) in
        0) res="INTERESADO"; canal="L";;
        1) res="VISITA_AGENDADA"; canal="W";;
        *) res="NEGOCIANDO"; canal="P";;
    esac
    crear "interaccion op $oid" POST /interacciones "$AGE" \
        "{\"contexto\":\"OPORTUNIDAD\",\"idOportunidad\":$oid,\"canalContacto\":\"$canal\",\"resultado\":\"$res\",\"observaciones\":\"Seguimiento comercial del cliente\"}" >/dev/null
    v=$((v+1))
done
for cli in "${CLIENTES[@]:0:6}"; do
    crear "interaccion cli $cli" POST /interacciones "$AGE" \
        "{\"contexto\":\"CLIENTE\",\"idCliente\":$cli,\"canalContacto\":\"E\",\"resultado\":\"BUSQUEDA_LEVANTADA\",\"observaciones\":\"Se levanto la busqueda del cliente\"}" >/dev/null
done
log "visitas: $v"

# ------------------------------------------------------------ 10. solicitudes
paso "Solicitudes de alquiler"
s=0
for oid in "${OPORT[@]}"; do
    crear "solicitud op $oid" POST /solicitudes "$AGE" \
        "{\"idOportunidad\":$oid,\"fechaRegistro\":\"2026-08-05\",\"montoPropuesto\":$(( 2600 + s * 480 )),\"moneda\":\"PEN\",\"plazoTentativo\":\"24 meses\",\"plazoMeses\":24,\"fechaInicio\":\"2026-09-01\",\"formaPago\":\"TRANSFERENCIA\",\"mesesGarantia\":2,\"mesesAdelanto\":1,\"observaciones\":\"Solicitud registrada por el agente\",\"fechaVigenciaOferta\":\"2026-09-15\"}" >/dev/null
    s=$((s+1))
done

# Se releen de la API: las que quedaron en G u O son las reenviables, y esa es
# la lista que manda, no la de ids que devolvio el alta.
RESP=$(api GET "/solicitudes?pagina=1&tamano=200" "$AGE")
mapfile -t SOLIC < <(printf '%s' "${RESP#*$'\t'}" | node -e "
    let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
        try{const j=JSON.parse(s);(j.items||[])
            .filter(x=>x.estado==='G'||x.estado==='O').forEach(x=>console.log(x.id))}catch(e){}})")
log "solicitudes reenviables: ${#SOLIC[@]}"

# ------------------------------------------------- 11. expediente documental
paso "Expediente documental y conformidad"
for sid in "${SOLIC[@]}"; do
    subir "$sid" "$AGE" I dni.pdf
    subir "$sid" "$AGE" R ficha-ruc.pdf
    subir "$sid" "$AGE" V vigencia-poder.pdf
    subir "$sid" "$AGE" E sustento-ingresos.pdf
    subir "$sid" "$AGE" G carta-garantia.pdf
    subir "$sid" "$AGE" D declaracion-jurada.pdf
    crear "conformar sol $sid" PATCH "/solicitudes/$sid/documentos/conformar" "$BRK" \
        '{"observaciones":"Expediente revisado y conforme"}' >/dev/null
done
log "expedientes cargados: ${#SOLIC[@]} (6 tipos cada uno)"

# --------------------------------------------- 12. reenvio + evaluacion
# TRAMPA 3: sin reenviar, el broker no puede evaluar.
paso "Reenvio a revision y evaluacion del broker"
EN_REVISION=()
for sid in "${SOLIC[@]}"; do
    resp=$(api POST "/solicitudes/$sid/reenviar" "$AGE" '{}')
    if [[ "${resp%%$'\t'*}" =~ ^2 ]]; then
        echo x >> "$CONT/ok"; EN_REVISION+=("$sid")
    else
        echo x >> "$CONT/fallo"
        printf 'reenviar sol %s -> HTTP %s :: %s\n' "$sid" "${resp%%$'\t'*}" \
            "$(printf '%s' "${resp#*$'\t'}" | head -c 160)" >> "$CONT/detalle"
    fi
done

APROBADAS=()
e=0
for sid in "${EN_REVISION[@]}"; do
    case $((e % 5)) in
        3) crear "observar sol $sid" POST /evaluaciones "$BRK" \
               "{\"idSolicitud\":$sid,\"tipoEvaluacion\":\"O\",\"resultado\":\"O\",\"observaciones\":\"Falta el sustento economico del ultimo trimestre\"}" >/dev/null;;
        4) crear "rechazar sol $sid" POST /evaluaciones "$BRK" \
               "{\"idSolicitud\":$sid,\"tipoEvaluacion\":\"F\",\"resultado\":\"R\",\"observaciones\":\"Capacidad de pago insuficiente para la renta solicitada\"}" >/dev/null;;
        *) resp=$(api POST /evaluaciones "$BRK" \
               "{\"idSolicitud\":$sid,\"tipoEvaluacion\":\"F\",\"resultado\":\"A\",\"observaciones\":\"Expediente conforme, se autoriza el contrato\"}")
           if [[ "${resp%%$'\t'*}" =~ ^2 ]]; then
               echo x >> "$CONT/ok"; APROBADAS+=("$sid")
           else
               echo x >> "$CONT/fallo"
               printf 'aprobar sol %s -> HTTP %s :: %s\n' "$sid" "${resp%%$'\t'*}" \
                   "$(printf '%s' "${resp#*$'\t'}" | head -c 160)" >> "$CONT/detalle"
           fi;;
    esac
    e=$((e+1))
done
log "solicitudes en revision: ${#EN_REVISION[@]} / aprobadas: ${#APROBADAS[@]}"

# -------------------------------------------------- 13. contratos y comision
# El alta del contrato dispara la cascada de siete efectos: cierra la
# captacion, finaliza la oportunidad y marca el inmueble no disponible.
# TRAMPA 4: asignar exige montoAgente; cobro exige estado.
paso "Contratos, cascada de cierre y comision"
c=0
for sid in "${APROBADAS[@]}"; do
    cid=$(crear "contrato sol $sid" POST /contratos "$AGE" \
        "{\"idSolicitud\":$sid,\"fechaCierre\":\"2026-08-08\",\"estadoContrato\":\"D\",\"incidencias\":\"Cierre sin incidencias\"}")
    if [ -n "$cid" ]; then
        crear "comision asignar $cid" POST "/contratos/$cid/comision/asignar" "$BRK" \
            '{"montoAgente":600,"observaciones":"Comision asignada al agente responsable"}' >/dev/null
        # Un cobro de cada dos: la bandeja de comisiones necesita pendientes
        # y cobradas para que el filtro tenga sentido.
        if [ $((c % 2)) -eq 0 ]; then
            crear "comision cobro $cid" POST "/contratos/$cid/comision/cobro" "$BRK" \
                '{"estado":"C","fechaCobro":"2026-08-09","formaPago":"TRANSFERENCIA"}' >/dev/null
        fi
        c=$((c+1))
    fi
done
log "contratos cerrados: $c"

# -------------------------------------------------------------- 14. resumen
paso "Resumen"
log "operaciones OK:       $(wc -l < "$CONT/ok" | tr -d ' ')"
log "operaciones fallidas: $(wc -l < "$CONT/fallo" | tr -d ' ')"
if [ -s "$CONT/detalle" ]; then
    log ""
    log "Fallos por tipo (ids normalizados a N):"
    sed 's/[0-9]\+/N/g' "$CONT/detalle" | sort | uniq -c | sort -rn | head -20
    log ""
    log "Nota: en una RE-EJECUCION es normal ver duplicados de oportunidad y"
    log "solicitud: el dominio impide abrir dos sobre la misma captacion."
fi
