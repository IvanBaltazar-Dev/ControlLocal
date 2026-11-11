package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comercial.ReasignacionCaptacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.ReasignacionCaptacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.CaptacionService.CandidatoAgente;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.ElegibilidadDeResponsable;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.PoliticaComercial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lecturas y gobierno de responsabilidad; el ciclo versionado pertenece a EncargoService. */
@Service
public class CaptacionServiceImpl implements CaptacionService {

    private final CaptacionRepository captaciones;
    private final ReasignacionCaptacionRepository reasignaciones;
    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final FotoPropiedadRepository fotos;
    private final Alcances alcances;
    private final LectorPorAutoridad lector;
    private final ElegibilidadDeResponsable elegibilidad;
    private final com.controllocal.service.encargo.AccesoEncargo acceso;
    private final com.controllocal.service.encargo.ReglasDeResponsableEncargo reglasResponsable;
    private final com.controllocal.service.soporte.ComandosIdempotentes comandos;
    private final com.controllocal.persistence.repositorio.ComandoIdempotenteRepository registros;
    private final com.controllocal.persistence.repositorio.EventoDominioRepository eventos;
    private final com.controllocal.service.soporte.Documentos json;
    private final jakarta.persistence.EntityManager entidades;
    private final org.springframework.context.ApplicationEventPublisher cambiosComerciales;

    public CaptacionServiceImpl(CaptacionRepository captaciones, ReasignacionCaptacionRepository reasignaciones,
            DetalleAgenteRepository agentes,DetalleBrokerRepository brokers,FotoPropiedadRepository fotos,
            Alcances alcances,LectorPorAutoridad lector,ElegibilidadDeResponsable elegibilidad,
            com.controllocal.service.encargo.AccesoEncargo acceso,
            com.controllocal.service.encargo.ReglasDeResponsableEncargo reglasResponsable,
            com.controllocal.service.soporte.ComandosIdempotentes comandos,
            com.controllocal.persistence.repositorio.ComandoIdempotenteRepository registros,
            com.controllocal.persistence.repositorio.EventoDominioRepository eventos,
            com.controllocal.service.soporte.Documentos json,jakarta.persistence.EntityManager entidades,
            org.springframework.context.ApplicationEventPublisher cambiosComerciales) {
        this.captaciones=captaciones; this.reasignaciones=reasignaciones; this.agentes=agentes;
        this.brokers=brokers; this.fotos=fotos; this.alcances=alcances; this.lector=lector;
        this.elegibilidad=elegibilidad; this.acceso=acceso; this.reglasResponsable=reglasResponsable;
        this.comandos=comandos; this.registros=registros; this.eventos=eventos; this.json=json;
        this.entidades=entidades;
        this.cambiosComerciales=cambiosComerciales;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCaptacion> listar(FiltrosCaptacion filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Long idAgente = filtros.idAgente() != null && filtros.idAgente() > 0
                ? filtros.idAgente()
                : null;
        Page<Captacion> page = captaciones.buscar(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(filtros.estado()), idAgente,
                enBlancoANull(filtros.q()),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano())));
        return paginaConPortada(page);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PropiedadEquipo> carteraDelEquipo(FiltrosEquipo filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Page<com.controllocal.persistence.query.PropiedadDeEquipo> page = captaciones.carteraDelEquipo(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles(),
                enBlancoANull(filtros.texto()), enBlancoANull(filtros.distrito()),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano())));
        return new Pagina<>(page.getContent().stream()
                .map(p -> new PropiedadEquipo(p.getIdPropiedad(), p.getIdCaptacion(),
                        p.getCodigoCaptacion(), p.getEstado(), p.getCodigoLocal(), p.getDireccion(),
                        p.getDistrito(), p.getRubro(), p.getAreaM2(), p.getIdAgente(),
                        p.getAgenteNombre()))
                .toList(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenEquipo resumenCarteraDelEquipo(String texto, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenEquipo(0, 0, 0, 0);
        }
        // El resumen NO filtra por distrito a proposito: es justamente el
        // desglose que el filtro de distrito acota, igual que /locales/resumen.
        var r = captaciones.resumenCarteraDelEquipo(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(texto), null);
        return r == null
                ? new ResumenEquipo(0, 0, 0, 0)
                : new ResumenEquipo(valor(r.getPropiedades()), valor(r.getConCaptacionActiva()),
                        valor(r.getAgentesConCartera()), valor(r.getDistritos()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> distritosDelEquipo(String texto, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        return captaciones.distritosDelEquipo(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(texto), null);
    }

    private static long valor(Long valor) {
        return valor == null ? 0L : valor;
    }

    private static String enBlancoANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCaptacion> pendientes(FiltrosPendientes filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Long idAgente = filtros.idAgente() != null && filtros.idAgente() > 0
                ? filtros.idAgente()
                : null;
        Page<Captacion> page = captaciones.pendientes(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(filtros.estado()), idAgente,
                enBlancoANull(filtros.q()),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano())));
        return paginaConPortada(page);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<EncargoReasignable> reasignables(int pagina, int tamano, String q, Actor actor) {
        exigirBandaQueGobiernaElEncargo(actor);
        Alcance alcance = alcances.paraGobiernoDeReasignacion(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        var page = captaciones.buscarParaReasignacion(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), null, null, vacioNull(q),
                PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return new Pagina<>(page.getContent().stream().map(c -> new EncargoReasignable(
                c.getId(), c.getCodigoCaptacion(), estadoPublico(c.getEstado()), c.getDireccionLocal(),
                c.getIdAgente(), c.getAgenteNombre(),c.getVersion())).toList(), page.getTotalElements());
    }
    @Override @Transactional(readOnly=true)
    public EncargoReasignable consultarReasignable(long id,Actor actor) {
        var c=captaciones.proyeccionDeReasignacion(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Encargo"));
        exigirBandaQueGobiernaElEncargo(actor);
        if(!alcances.alcanzaParaGobiernoDeReasignacion(actor,c.getIdAgente())) throw new AccesoNoAutorizadoException();
        reglasResponsable.exigirCambioOperativo(actor.idOrganizacion(),id,c.getEstado(),c.getTerminosAceptados(),c.getIdDocumentoActual());
        return new EncargoReasignable(c.getId(),c.getCodigoCaptacion(),estadoPublico(c.getEstado()),c.getDireccionLocal(),
                c.getIdAgente(),c.getAgenteNombre(),c.getVersion());
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCaptacion obtener(long id, Actor actor) {
        return fichaIndividual(cargarConAcceso(id, actor), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCaptacion obtenerPorCodigo(String codigo, Actor actor) {
        long id = captaciones
                .idPorCodigo(actor.idOrganizacion(), codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
        // Es la SEGUNDA puerta al mismo recurso, asi que publica lo mismo que la
        // primera: el mismo productor de ficha individual, con las mismas
        // capacidades. Dos formas de leer un encargo que devolvieran formas
        // distintas obligarian al cliente a llevar la regla en una de las dos.
        return fichaIndividual(acceso.leer(id,actor), actor);
    }

    /**
     * <b>La UNICA puerta canonica que mueve el agente de un encargo</b>
     * (D-P0-9 y D-P0-10, aplicados al ENCARGO).
     *
     * <h2>Que estaba mal</h2>
     * Reasignar era «pon a B». Dos comandos que salieran del mismo agente A —uno
     * hacia B y otro hacia C— pasaban exactamente las mismas guardas y el
     * segundo pisaba al primero: <b>la ultima escritura ganaba</b>, y
     * {@code reasignacion_captacion} quedaba afirmando «de A a C» sobre un
     * encargo que en ese momento ya llevaba B. Nadie habia decidido eso. Y
     * ademas la columna se movia por <b>dirty checking</b>: bastaba con que una
     * edicion del encargo hubiera cargado la fila antes del traspaso para que su
     * <i>flush</i> la devolviera al agente anterior, sin fila que lo explicara.
     *
     * <h2>Lo que se congela, y lo que NO cambia</h2>
     * Se congela el <b>comportamiento</b>: de un estado concreto parte
     * <b>exactamente una</b> reasignacion legitima; la segunda es <b>409</b> y
     * <b>no se reinterpreta</b> sobre el estado nuevo. Lo que <b>no</b> cambia
     * es <b>quien puede</b>: la banda y el alcance siguen siendo los de D-S0-17
     * fila 6 —BROKER dentro de su equipo, TENANT_ADMIN entre equipos—, ni un rol
     * mas. El responsable de la PROPIEDAD no gana con esto ninguna autoridad
     * sobre el ENCARGO: son dos autoridades distintas y siguen separadas.
     *
     * <h2>El orden de las guardas, que es contrato</h2>
     * <ol>
     *   <li>{@code cargarConAcceso} — tenant (404) y alcance sobre el agente
     *       <b>actual</b>, que es el saliente;</li>
     *   <li>el motivo, con el minimo de {@code PoliticaComercial};</li>
     *   <li>el destino <b>existe en esta organizacion</b>. El filtro de tenant
     *       es nuevo: {@code findById} no lo lleva, asi que un id de otra
     *       corredora llegaba a la comprobacion de alcance en vez de comportarse
     *       como inexistente;</li>
     *   <li>el alcance sobre el <b>destino</b>;</li>
     *   <li>reasignar al mismo agente es 400;</li>
     *   <li>{@link ElegibilidadDeResponsable#exigirElegible} — D-P0-7 se aplica
     *       en <b>toda</b> reasignacion de autoridad, no solo en la de la
     *       propiedad. Alcanzar a un agente no es que ese agente pueda operar:
     *       un supervisado de baja pasa la cuarta y falla esta. Va la ultima de
     *       las guardas y antes de escribir nada, igual que en el traspaso de
     *       propiedad, para no cambiar el motivo de los rechazos ya medidos;</li>
     *   <li>y solo entonces, <b>desde donde</b>.</li>
     * </ol>
     *
     * <h2>Desde donde: dos comprobaciones, dos preguntas</h2>
     * <pre>
     *   (a) EN MEMORIA -&gt; el comando llego obsoleto o equivocado. Se corta sin
     *       escribir y con el agente de HOY en el mensaje, que es lo unico que
     *       permite volver a decidir.
     *   (b) EN LA BASE -&gt; la carrera. Entre (a) y el UPDATE cabe otra
     *       transaccion; dentro del UPDATE no cabe ninguna.
     * </pre>
     * Las dos responden <b>409</b>, porque para quien reasigna el hecho es el
     * mismo: el estado cambio y hay que volver a mirar. Lo que cambia es cuanta
     * informacion se puede dar — la transaccion que pierde la carrera no puede
     * afirmar quien gano.
     *
     * <p><b>Y el rastro dice de donde salio de verdad</b>: el predecesor de la
     * fila es el <b>observado</b>, que despues de (a) y (b) es exactamente el
     * valor que tenia la columna. No se toma «el que estaba cargado», que es la
     * misma cosa por casualidad y dejaria de serlo el dia que algo cambie.
     */
    @Override
    @Transactional
    public ResultadoReasignacion reasignar(long id,DatosReasignacion datos,Actor actor,
            com.controllocal.service.actuacion.ActuacionesService.ContextoSesion sesion,
            String claveComando,com.controllocal.service.soporte.Procedencia procedencia) {
        if(datos==null || datos.idAgenteNuevo()<=0 || datos.idAgenteObservado()<=0)
            throw new ReglaNegocioException("Debe identificar responsable observado y destino.");
        String clave=com.controllocal.service.soporte.Idempotencia.exigirClave(claveComando);
        if(procedencia==null) throw new IllegalArgumentException("Debe conservar la procedencia.");
        var inicio=acceso.iniciarReasignacion(actor,sesion,id,datos.idAgenteNuevo());
        String identidad=com.controllocal.service.soporte.Idempotencia.huellaExacta(
                "BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        var ids=new java.util.TreeSet<Long>();ids.add(inicio.responsable());ids.add(datos.idAgenteNuevo());
        for(long rol:ids) agentes.bloquearParaGobierno(actor.idOrganizacion(),rol)
                .orElseThrow(AccesoNoAutorizadoException::new);
        Captacion cap=acceso.escribirParaReasignacion(id,actor,inicio);
        String huella=com.controllocal.service.soporte.Idempotencia.huellaExacta(json.objetoCanonico(
                Map.of("actor",actor,"encargo",id,"datos",datos)));
        java.util.Optional<com.controllocal.domain.auditoria.ComandoIdempotente> previo;
        try {previo=comandos.buscar(actor,clave,"REASIGNAR_ENCARGO",huella);}
        catch(ReglaNegocioException distinta) {throw new ConflictoException("La clave identifica otra entrada.");}
        if(previo.isPresent()) return resultadoReasignacion(previo.get().getResultado());
        reglasResponsable.exigirCambioOperativo(cap);
        String motivo=PoliticaComercial.exigirMotivoDeReasignacion(datos.motivo());
        if(cap.getVersion()!=datos.versionEsperada() || cap.getAgente().getId()!=datos.idAgenteObservado())
            throw new ConflictoException("El Encargo cambió; su responsable actual es "+cap.getAgente().getId()
                    +". La reasignación no se reinterpreta sobre ese responsable; vuelve a revisar.");
        if(datos.idAgenteObservado()==datos.idAgenteNuevo())
            throw new ReglaNegocioException("El destino ya es responsable del Encargo.");
        if(!alcances.alcanzaParaGobiernoDeReasignacion(actor,datos.idAgenteNuevo()))
            throw new AccesoNoAutorizadoException();
        elegibilidad.exigirElegible(actor,datos.idAgenteNuevo());
        DetalleAgente anterior=cap.getAgente();
        DetalleAgente nuevo=agentes.findById(datos.idAgenteNuevo())
                .filter(a2->Objects.equals(a2.getOrganizacionId(),actor.idOrganizacion()))
                .orElseThrow(AccesoNoAutorizadoException::new);
        if(captaciones.cambiarAgenteSi(actor.idOrganizacion(),id,datos.idAgenteObservado(),
                datos.idAgenteNuevo(),datos.versionEsperada())!=1)
            throw new ConflictoException("La reasignación ya no corresponde a la versión observada.");
        entidades.refresh(cap);
        ReasignacionCaptacion r=new ReasignacionCaptacion();r.setOrganizacionId(actor.idOrganizacion());
        r.setCaptacion(cap);r.setAgenteAnterior(anterior);r.setAgenteNuevo(nuevo);
        r.setBroker(actor.esBroker()?brokers.findById(actor.idRolOperativo()).orElseThrow(AccesoNoAutorizadoException::new):null);
        r.setIdPersonaActor(actor.idPersona());r.setTipoRolActor(actor.rolEfectivo());r.setMotivo(motivo);
        reasignaciones.saveAndFlush(r);
        var evento=procedencia.sellar(com.controllocal.domain.auditoria.EventoDominio.de(actor.idOrganizacion(),
                "ENCARGO_REASIGNADO","CAPTACION",id,actor.idRolOperativo(),procedencia.canal()))
                .con(json.objeto(com.controllocal.service.soporte.AvisoComercialBroker.con(Map.of("reasignacion",r.getId(),"anterior",anterior.getId(),"nuevo",nuevo.getId()),
                    new com.controllocal.service.soporte.AvisoComercialBroker.Marca("NB-13","ENCARGO",id,cap.getVersion(),List.of("RESPONSABLE")))));
        eventos.save(evento);
        cambiosComerciales.publishEvent(new com.controllocal.service.soporte.CambioDeOfertaComercial(
                com.controllocal.service.soporte.CambioDeOfertaComercial.Recurso.ENCARGO,id,actor,evento));
        var resultado=new ResultadoReasignacion(id,cap.getCodigoCaptacion(),cap.estadoTipado().name(),
                anterior.getId(),nuevo.getId(),r.getId(),cap.getVersion());
        Map<String,Object> snapshot=Map.of("id",id,"codigoCaptacion",resultado.codigoCaptacion(),
                "estado",resultado.estado(),"idAgenteAnterior",anterior.getId(),"idAgente",nuevo.getId(),
                "idReasignacion",r.getId(),"version",cap.getVersion());
        comandos.registrar(actor,clave,"REASIGNAR_ENCARGO",huella,"CAPTACION",id,procedencia,
                json.objetoCanonico(Map.of("version",1,"resultado",snapshot,"huellaResultado",
                        com.controllocal.service.soporte.Idempotencia.huellaExacta(json.objetoCanonico(snapshot)))));
        return resultado;
    }

    private ResultadoReasignacion resultadoReasignacion(String guardado) {
        var contenedor=json.comoMapa(guardado);
        if(!(contenedor.get("resultado") instanceof Map<?,?> resultado))
            throw new IllegalStateException("Falta el resultado durable de reasignación.");
        Map<String,Object> mapa=new java.util.TreeMap<>();resultado.forEach((k,v)->mapa.put((String)k,v));
        if(!Objects.equals(contenedor.get("huellaResultado"),com.controllocal.service.soporte.Idempotencia.huellaExacta(json.objetoCanonico(mapa))))
            throw new IllegalStateException("El resultado de reasignación no conserva su huella.");
        return new ResultadoReasignacion(numero(mapa,"id"),(String)mapa.get("codigoCaptacion"),
                (String)mapa.get("estado"),numero(mapa,"idAgenteAnterior"),numero(mapa,"idAgente"),
                numero(mapa,"idReasignacion"),numero(mapa,"version"));
    }
    private static long numero(Map<String,Object> mapa,String clave) {
        if(!(mapa.get(clave) instanceof Number n)) throw new IllegalStateException("Resultado durable ilegible.");
        return n.longValue();
    }

    /**
     * <b>A quien puedo pasarle este encargo</b> — los candidatos ya elegibles
     * para ESTE encargo y ESTE actor (D-P0-7 + D-P0-12).
     *
     * <p>Sale del <b>mismo</b> componente que ofrece los destinos de una
     * propiedad, con la unica diferencia de a quien excluye: alli el responsable
     * actual, aqui el agente actual del encargo. Las reglas son las mismas
     * —mismo tenant, rol AGENTE vigente, cuenta habilitada, membresia vigente,
     * disponibilidad y, si el actor es BROKER, supervision vigente—, asi que el
     * conjunto es el mismo componente con distinta exclusion. Escribir una
     * segunda version "para encargos" seria la forma habitual de que las dos
     * listas terminen ofreciendo cosas distintas.
     *
     * <p><b>Y la guarda es la misma que apaga el boton</b>: si el actor no puede
     * reasignar este encargo, la respuesta es <b>403</b> y no una lista vacia —
     * «no hay candidatos» y «no te corresponde» son dos respuestas distintas.
     * Un id de otro tenant es <b>404</b>, delante de todo, por
     * {@code cargarConAcceso}.
     *
     * <p><b>No autoriza nada</b>: el POST revalida banda, tenant, destino,
     * elegibilidad y estado observado. Entre pedir esta lista y usarla, una
     * cuenta se puede suspender.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<CandidatoAgente> candidatosAReasignacion(long id, String texto, int pagina,
                                                           int tamano, Actor actor) {
        var cap = consultarReasignable(id, actor);
        Page<DetalleAgente> page = elegibilidad.candidatosExcluyendo(actor, cap.idAgente(),
                texto, PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return new Pagina<>(page.getContent().stream()
                .map(a -> new CandidatoAgente(a.getId(), nombreDe(a), a.getCodigoAgente(),
                        a.getZonaAsignada()))
                .toList(), page.getTotalElements());
    }

    private static String nombreDe(DetalleAgente agente) {
        PersonaRol rol = agente.getRol();
        return rol == null || rol.getPersona() == null
                ? null : rol.getPersona().getNombresORazonSocial();
    }

    /**
     * <b>El rastro de reasignaciones se acota con el alcance del ENCARGO</b>
     * (F3-bis, 2026-09-02 — interpretacion de D-P0-6).
     *
     * <p>Hasta aqui devolvia {@code findByOrganizacionIdOrderByIdDesc}: <b>todo
     * el tenant</b>, sin pasar por {@link Alcances}. Un BROKER leia desde que
     * agente salio cada encargo, hacia cual fue y <b>con que motivo</b> en
     * equipos que no supervisa, y ese motivo es texto libre de gobierno.
     *
     * <p>D-P0-6 decide la lectura de historicos de ENCARGO —BROKER los que estan
     * dentro de su alcance, TENANT_ADMIN todo el tenant—, asi que esto se acota
     * con <b>el mismo alcance que ya usa {@link #listar}</b>, sobre la misma
     * columna ({@code captacion.id_rol_agente}). No hay regla nueva: quien ve el
     * encargo ve su rastro.
     *
     * <p><b>El alcance es el del encargo de HOY</b>, y se declara porque es
     * interpretacion: no es el del agente saliente —un encargo que ya no lleva
     * su equipo seguiria en el rastro del broker para siempre— ni el del broker
     * que firmo la reasignacion —una reasignacion de gobierno no la veria
     * ningun broker—.
     *
     * <p>La banda va <b>delante</b> del alcance y no la sustituye:
     * {@code Alcances.de} le daria a un AGENTE «lo suyo», que aqui es
     * exactamente lo que no le corresponde. El controlador ya lo declara con
     * {@code @PreAuthorize("hasAnyRole('BROKER','TENANT_ADMIN')")}; el Core dice
     * lo mismo para que KAIROS y cualquier consumidor que no pase por HTTP no
     * entren por debajo de la anotacion.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FichaReasignacion> listarReasignaciones(Actor actor) {
        exigirBandaQueGobiernaElEncargo(actor);
        Alcance alcance = alcances.paraGobiernoDeReasignacion(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        return reasignaciones.bitacora(alcance.idOrganizacion(), alcance.global(),
                        alcance.paramRoles()).stream()
                .map(CaptacionServiceImpl::fichaReasignacion)
                .toList();
    }

    // ------------------------------------------------------------------
    // Carga con alcance + soporte.
    // ------------------------------------------------------------------

    private Captacion cargarConAcceso(long id, Actor actor) {
        return acceso.leer(id,actor);
    }

    /**
     * <b>La ficha de UN encargo, con sus capacidades resueltas</b> (D-P0-12).
     *
     * <p>Es el unico productor de una ficha individual, y pasa por aqui tanto la
     * consulta por id como la consulta por codigo: son <b>dos puertas al mismo
     * recurso</b>, y una que publicara `capacidades` y la otra no obligaria al
     * SPA a llevar su propia copia de la regla justo en la puerta que se olvida.
     *
     * <p>Los listados NO pasan por aqui: su pregunta es «que hay», no «que puedo
     * hacer con este», y calcular tres capacidades por fila serian tres
     * comprobaciones de alcance por elemento de la pagina.
     */
    private FichaCaptacion fichaIndividual(Captacion cap, Actor actor) {
        return ficha(cap, portadaDe(cap), gobernadosDe(List.of(cap)), capacidadesDe(actor, cap));
    }

    private String portadaDe(Captacion cap) {
        // Sólo la referencia Core llega al cliente; el proveedor permanece detrás de A04.
        return fotos.deLaPropiedad(cap.getOrganizacionId(), cap.getPropiedad().getId()).stream()
                .findFirst().map(f -> Objects.requireNonNull(f.getArchivoRef(),
                        "La foto carece de referencia de archivo").toString()).orElse(null);
    }

    /**
     * <b>Las tres capacidades, con las MISMAS guardas que los comandos.</b>
     *
     * <p>{@code puedeEditar} se pregunta con el propio
     * {@code exigirEdicionDelEncargo} —el metodo que despues deniega—, en vez de
     * reescribir «soy el agente» aqui: un segundo criterio "solo para pintar" es
     * exactamente como se llega a un boton activo que el backend rechaza cuando
     * la persona ya escribio. Es el mismo patron que {@code autoridadDelEncargo}
     * en la ficha universal.
     *
     * <p>{@code puedeRevisar} y {@code puedeCerrar} exigen la banda BROKER de
     * forma explicita: decidir y cerrar un encargo son operaciones comerciales y
     * el gobierno del tenant <b>no las hereda</b> (D-S0-17, filas 5 y 7). El
     * TENANT_ADMIN alcanza el encargo para leerlo —{@code alcances.alcanza} le
     * dice que si— y aun asi recibe {@code false}, que es lo mismo que le
     * responden los comandos.
     *
     * <p>{@code puedeReasignar} es la excepcion y por eso lo dice aparte: el
     * TENANT_ADMIN <b>si</b> reasigna (D-S0-17 fila 6 — entre equipos es
     * organigrama, no operacion comercial), y sale del <b>mismo</b> predicado
     * que decide si se le ofrecen candidatos.
     */
    private CaptacionService.Capacidades capacidadesDe(Actor actor, Captacion cap) {
        // Las tres mutaciones antiguas se retiraron. Los casos versionados publican sus propias facultades.
        return new CaptacionService.Capacidades(false,false,false,puedeReasignar(actor,cap));
    }

    /**
     * <b>¿Puede este actor mover el encargo a otro agente?</b> (D-S0-17 fila 6).
     *
     * <p>Son las guardas del comando <b>sin el destino</b>, que en la ficha
     * todavia no existe: el BROKER que supervisa hoy al agente que lo lleva, y
     * el TENANT_ADMIN de la organizacion. Un AGENTE nunca —tampoco sobre el
     * suyo: quien lleva un encargo no decide dejar de llevarlo.
     *
     * <p>Existe una sola vez y la usan las <b>dos</b> superficies —la capacidad
     * de la ficha y el 403 de la lista de candidatos— porque son la misma
     * pregunta. El comando revalida ademas el destino, su elegibilidad y el
     * estado observado: esto no autoriza nada, dice si tiene sentido ofrecerlo.
     */
    private boolean puedeReasignar(Actor actor, Captacion cap) {
        if (cap.getAgente() == null) {
            return false;
        }
        return ((actor.esBroker() && alcances.alcanza(actor, cap.getAgente().getId()))
                || actor.esTenantAdmin()) && reglasResponsable.permiteCambioOperativo(cap);
    }

    private Pagina<FichaCaptacion> paginaConPortada(Page<Captacion> page) {
        List<Long> idsPropiedad = page.getContent().stream().map(c -> c.getPropiedad().getId()).distinct().toList();
        var portadas = idsPropiedad.isEmpty() ? java.util.Map.<Long, String>of()
                : fotos.portadas(idsPropiedad).stream()
                    .collect(java.util.stream.Collectors.toMap(f -> f.getIdPropiedad(), f -> f.getClave()));
        Map<Long, ValoresGobernados> gobernados = gobernadosDe(page.getContent());
        // Sin capacidades: un listado responde «que hay», no «que puedo hacer con
        // este». Nulo -> NON_NULL -> no viaja, y el cliente no puede confundirlo
        // con "no puedes nada".
        List<FichaCaptacion> items = page.getContent().stream()
                .map(c -> ficha(c, portadas.get(c.getPropiedad().getId()), gobernados, null))
                .toList();
        return new Pagina<>(items, page.getTotalElements());
    }

    /**
     * Los valores gobernados de las propiedades de estas captaciones, en una
     * sola consulta.
     *
     * <p>El rubro dejo de tener columna propia en V71 y se lee por autoridad
     * como el resto. En lote y no fila a fila: la lista de captaciones es
     * paginada, y una consulta por fila seria el N+1 que RC-003 quito.
     */
    private Map<Long, ValoresGobernados> gobernadosDe(List<Captacion> captacionesDeLaPagina) {
        List<Long> ids = captacionesDeLaPagina.stream()
                .map(Captacion::getPropiedad).filter(Objects::nonNull)
                .map(Propiedad::getId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : lector.gobernadosDeVarias(ids);
    }

    /**
     * <b>Decidir y cerrar un encargo son operaciones COMERCIALES</b> (D-S0-17,
     * filas 5 y 7): las firma el BROKER, y el gobierno del tenant no las hereda.
     *
     * <p>Existe una sola vez para los dos comandos porque es la misma regla, y
     * dicha con las mismas palabras: dos redacciones del mismo rechazo se
     * separan en el primer cambio, igual que dos comprobaciones del mismo
     * permiso.
     */
    /**
     * <b>El rastro de gobierno del encargo no es del AGENTE</b> (F3-bis,
     * interpretacion de D-P0-6).
     *
     * <p>Es la hermana de {@link #exigirBandaComercial} por el otro lado: alli
     * el gobierno no hereda la operacion, aqui la operacion no hereda el
     * gobierno. Existe <b>en el Core</b> y no solo en el
     * {@code @PreAuthorize("hasAnyRole('BROKER','TENANT_ADMIN')")} del
     * controlador porque el alcance por si solo <b>concederia</b>: a un AGENTE,
     * {@code Alcances.de} le devuelve «lo suyo», y sus propias reasignaciones
     * son justamente lo que no le toca leer — quien lleva un encargo sabe que lo
     * lleva, y no hereda por eso el motivo con el que se lo quitaron a otro.
     */
    private static void exigirBandaQueGobiernaElEncargo(Actor actor) {
        if (!actor.esBroker() && !actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException(
                    "El historial de reasignaciones es rastro de gobierno del encargo: lo leen el "
                            + "BROKER que supervisa hoy a quien lo lleva y el gobierno del tenant. "
                            + "Quien opera un encargo sabe que lo lleva, y eso no le concede los "
                            + "motivos con los que se movio la cartera.");
        }
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static FichaCaptacion ficha(Captacion c, String fotoPortadaClave,
                                        Map<Long, ValoresGobernados> gobernados,
                                        CaptacionService.Capacidades capacidades) {
        Propiedad prop = c.getPropiedad();
        ValoresGobernados valores = prop == null ? ValoresGobernados.vacio()
                : LectorPorAutoridad.de(gobernados, prop.getId());
        DetalleAgente agente = c.getAgente();
        DetalleBroker broker = c.getBrokerRevisor();
        CondicionEconomicaCaptacion ce = c.getCondicionEconomica();
        return new FichaCaptacion(
                c.getId(), c.getCodigoCaptacion(), c.getFechaCaptacion(), c.getFechaInicioVigencia(),
                c.getFechaFinVigencia(), c.getComisionPactada(), c.getObservaciones(), c.estadoActual(),
                c.getMotivoOperacion(), c.getUrgencia(), c.getExclusividad(), c.getObservacionRevision(),
                Fechas.local(c.getFechaRevision()),
                prop != null ? prop.getId() : null,
                prop != null ? prop.getDireccion() : null,
                prop != null ? prop.getDistrito() : null,
                prop != null ? prop.getMetraje() : null,
                valores.texto(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO),
                nombrePropietario(prop),
                agente != null ? agente.getId() : null,
                nombrePersona(agente != null ? agente.getRol() : null),
                broker != null ? broker.getId() : null,
                fotoPortadaClave,
                ce != null ? ce.getTipoOperacion() : null,
                ce != null ? ce.getImporteReferencia() : null,
                ce != null ? ce.getMonedaReferencia() : null,
                ce != null ? ce.getTipoComision() : null,
                ce != null ? ce.getBaseCalculo() : null,
                ce != null ? ce.getValorComision() : null,
                ce != null ? ce.getMonedaComision() : null,
                ce != null ? ce.getTratamientoIgv() : null,
                ce != null ? ce.getMotivoSinComision() : null,
                c.getFechaCierre(), c.getMotivoCierre(), c.getDetalleMotivoCierre(),
                capacidades);
    }

    private static FichaReasignacion fichaReasignacion(ReasignacionCaptacion r) {
        Captacion cap = r.getCaptacion();
        Propiedad prop = cap != null ? cap.getPropiedad() : null;
        return new FichaReasignacion(
                r.getId(),
                cap != null ? cap.getId() : null,
                cap != null ? cap.getCodigoCaptacion() : null,
                prop != null ? prop.getDireccion() : null,
                r.getAgenteAnterior() != null ? r.getAgenteAnterior().getId() : null,
                nombrePersona(r.getAgenteAnterior() != null ? r.getAgenteAnterior().getRol() : null),
                r.getAgenteNuevo() != null ? r.getAgenteNuevo().getId() : null,
                nombrePersona(r.getAgenteNuevo() != null ? r.getAgenteNuevo().getRol() : null),
                r.getBroker() != null ? r.getBroker().getId() : null,
                nombrePersona(r.getBroker() != null ? r.getBroker().getRol() : null),
                Fechas.local(r.getFechaCambio()),
                r.getMotivo());
    }

    private static String nombrePropietario(Propiedad prop) {
        if (prop == null || prop.getRolPropietario() == null) {
            return null;
        }
        return nombrePersona(prop.getRolPropietario());
    }

    private static String nombrePersona(PersonaRol rol) {
        if (rol == null || rol.getPersona() == null) {
            return null;
        }
        return rol.getPersona().getNombresORazonSocial();
    }
    private static String estadoPublico(String codigo) {
        return com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion.desde(codigo).name();
    }
}

