package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.VisitaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.Vocabulario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actividad.RecibosDeActividad;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.persistence.repositorio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Propagation;
import static com.controllocal.service.negociacion.ContenidoNegociacion.*;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** CAT-E21/RF043–045. Una agenda y una identidad Visita; declarar ocurrencia
 * no crea una reunión ni decide continuidad de la oportunidad. Historial íntegro y CAS.
 */
@Service
public class VisitaServiceImpl implements VisitaService {

    /** Descripciones de EstadoVisita v1: forman parte del mensaje de error. */
    private static final Map<String, String> DESCRIPCION_ESTADO = Map.of(
            "P", "programada", "G", "reprogramada", "C", "cancelada",
            "N", "no realizada", "R", "realizada");

    /** Tope duro del cable para la agenda "proximas". */
    private static final int MAX_PROXIMAS = 8;

    private final VisitaRepository visitas;
    private final AccesoOportunidad acceso;
    private final ContextosDeActividad contextos;
    private final EntityManager entidades;
    private final Documentos documentos;
    private final RecibosDeActividad recibos;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final PlanDeConsulta plan;

    public VisitaServiceImpl(VisitaRepository visitas,
            Alcances alcances,Transiciones transiciones,PlanDeConsulta plan,AccesoOportunidad acceso,
            ContextosDeActividad contextos,EntityManager entidades,Documentos documentos,
            RecibosDeActividad recibos){
        this.visitas=visitas;this.alcances=alcances;this.transiciones=transiciones;
        this.plan=plan;this.acceso=acceso;this.contextos=contextos;this.entidades=entidades;this.documentos=documentos;
        this.recibos=recibos;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaVisita> listar(FiltrosVisita f, Actor actor) {
        contextos.exigirOperador(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        int tamanoValido = tamano(f.tamano());
        String texto = vacioNull(f.query());
        if (texto != null) {
            return porTexto(alcance, actor, f, texto, Math.max(1, f.pagina()), tamanoValido);
        }
        Page<Visita> page = visitas.buscar(alcance.idOrganizacion(), alcance.global(), actor.esAgente(),
                alcance.paramRoles(), f.idOportunidad(), vacioNull(f.estado()), vacioNull(f.distrito()),
                PageRequest.of(Math.max(0, f.pagina() - 1), tamanoValido));
        return new Pagina<>(fichasEnLote(page.getContent(),actor),
                page.getTotalElements());
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (§5 del contrato de
     * listados): una rama por tabla, {@code UNION} en la base y el mismo
     * conjunto para el conteo y la pagina.
     */
    private Pagina<FichaVisita> porTexto(Alcance alcance, Actor actor, FiltrosVisita f,
                                         String texto, int pagina, int tamano) {
        plan.forzarPersonalizado();
        String roles = alcance.paramRolesArray();
        String estado = vacioNull(f.estado());
        String distrito = vacioNull(f.distrito());
        long total = visitas.contarPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, f.idOportunidad(), estado, distrito, texto);
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        List<Long> ids = visitas.idsPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, f.idOportunidad(), estado, distrito, texto,
                tamano, (pagina - 1) * tamano);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        return new Pagina<>(
                visitas.buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                        .map(this::ficha).toList(),
                total);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenVisitas resumen(FiltrosVisita f, Actor actor) {
        contextos.exigirOperador(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenVisitas(0, 0, 0, 0, 0, 0, List.of());
        }
        // Estado y distrito viajan nulos: son los filtros que este resumen acota.
        // Con texto se cuenta sobre el MISMO conjunto de candidatos que pagina
        // la lista; sin texto, sobre el mismo WHERE.
        String texto = vacioNull(f.query());
        if (texto != null) {
            plan.forzarPersonalizado();
        }
        List<ConteoPorEstado> conteos = texto != null
                ? visitas.contarPorEstadoConTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), f.idOportunidad(), null, null, texto)
                : visitas.contarPorEstado(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), f.idOportunidad(), null, null);
        Map<String, Long> porEstado = conteos.stream()
                .collect(Collectors.toMap(ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));
        return new ResumenVisitas(
                porEstado.values().stream().mapToLong(Long::longValue).sum(),
                porEstado.getOrDefault("P", 0L),
                porEstado.getOrDefault("G", 0L),
                porEstado.getOrDefault("R", 0L),
                porEstado.getOrDefault("N", 0L),
                porEstado.getOrDefault("C", 0L),
                visitas.distritosDisponibles(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaVisita> proximas(int tamano, Actor actor) {
        contextos.exigirOperador(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        List<Visita> fuente = visitas.listarProximas(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), alcance.paramRoles(), contextos.hoy(),
                PageRequest.of(0, Math.min(tamano(tamano), MAX_PROXIMAS)));
        return paginaSinTotal(fuente,actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaVisita> mes(int anio, int mes, Actor actor) {
        if (anio < 2000 || anio > 2100 || mes < 1 || mes > 12) {
            throw new ReglaNegocioException("El mes solicitado no es valido.");
        }
        contextos.exigirOperador(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        YearMonth periodo = YearMonth.of(anio, mes);
        List<Visita> fuente = visitas.listarMes(alcance.idOrganizacion(), alcance.global(), actor.esAgente(),
                alcance.paramRoles(), periodo.atDay(1), periodo.atEndOfMonth());
        return paginaSinTotal(fuente,actor);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaVisita obtener(long id, Actor actor) {
        return ficha(cargarConAcceso(id, actor));
    }

    @Override @Transactional(readOnly=true)
    public Pagina<FichaVisita> agenda(ConsultaAgenda q,Actor actor){
        if(q==null||q.desde()==null||q.hasta()==null||q.hasta().isBefore(q.desde()))
            throw new ReglaNegocioException("Indica un intervalo de agenda válido.");
        contextos.exigirOperador(actor);var a=alcances.de(actor);if(a.vacio())return Pagina.vacia();
        if(q.idOportunidad()!=null)acceso.contextoParaNegociacion(q.idOportunidad(),actor);
        var pagina=visitas.agenda(a.idOrganizacion(),a.global(),actor.esAgente(),a.paramRoles(),q.idOportunidad(),
            vacioNull(q.estado()),null,q.desde(),q.hasta(),PageRequest.of(Math.max(0,q.pagina()-1),tamano(q.tamano())));
        return new Pagina<>(fichasEnLote(pagina.getContent(),actor),pagina.getTotalElements());
    }
    @Override @Transactional(readOnly=true)
    public FichaVisita obtenerVersion(long id,long version,Actor actor){
        if(version<0)throw new ReglaNegocioException("Indica la versión histórica.");
        cargarConAcceso(id,actor);
        var f=documentos.comoObjeto(visitas.fichaVersion(actor.idOrganizacion(),id,version)
            .orElseThrow(()->new NoEncontradoException("Versión de visita")),FichaVisita.class);
        if(!Objects.equals(f.id(),id)||f.version()!=version)throw new IllegalStateException("Historia de visita inconsistente.");
        autorizarSnapshot(f,actor);return f;
    }
    @Override @Transactional(readOnly=true)
    public List<Antecedente> historia(long id,Actor actor){
        cargarConAcceso(id,actor);
        return visitas.historia(actor.idOrganizacion(),id).stream().map(v->{
            var f=documentos.comoObjeto(v.getFicha(),FichaVisita.class);autorizarSnapshot(f,actor);
            if(!Objects.equals(f.id(),id)||f.version()!=v.getVersion())throw new IllegalStateException("Historia de visita inconsistente.");
            return new Antecedente(v.getVersion(),v.getRegistradoEn(),v.getOperacion(),v.getMotivo(),f);
        }).toList();
    }
    @Override @Transactional(readOnly=true)
    public List<FichaVisita> porOportunidad(long id,Instant corte,Actor actor){
        acceso.contextoParaNegociacion(id,actor);
        Instant hasta=corte==null?contextos.ahora():corte;
        return visitas.fichasAlCorte(actor.idOrganizacion(),id,hasta).stream().map(j->{
            var f=documentos.comoObjeto(j,FichaVisita.class);if(!Objects.equals(f.idOportunidad(),id))throw new IllegalStateException("Visita de otra oportunidad.");
            autorizarSnapshot(f,actor);return f;
        }).toList();
    }
    private void autorizarSnapshot(FichaVisita f,Actor actor){
        if(f==null||f.idOportunidad()==null)throw new IllegalStateException("La visita no conserva su contexto.");
        var p=acceso.contextoParaNegociacion(f.idOportunidad(),actor);
        if(!Objects.equals(f.idCaptacion(),p.idEncargo()))throw new AccesoNoAutorizadoException();
        // Participantes/lugar son declaraciones de ESTE hecho, no referencias que
        // permitan resolver Persona/Contacto. Se autoriza el snapshot íntegro bajo
        // el contexto histórico, sin sustituirlo por los participantes actuales.
    }
    private void fechaHora(LocalDate fecha,LocalTime hora){
        if(fecha==null||hora==null)throw new ReglaNegocioException("Indica la fecha y hora programadas.");
    }
    private DatosVisita normalizar(DatosVisita d){
        if(d==null)throw new ReglaNegocioException("Aporta la programación de visita.");
        identidad(d.idOportunidad(),"la oportunidad");version(d.versionContexto(),"Oportunidad");fechaHora(d.fechaVisita(),d.horaVisita());
        var participantes=d.participantes().stream().map(p->{
            if(p==null||(texto(p.nombreDeclarado())==null&&texto(p.referenciaDeclarada())==null))
                throw new ReglaNegocioException("Conserva una declaración identificable de cada participante conocido.");
            return new ParticipanteConocido(texto(p.nombreDeclarado()),texto(p.referenciaDeclarada()));
        }).toList();
        return new DatosVisita(d.idOportunidad(),d.versionContexto(),d.fechaVisita(),d.horaVisita(),texto(d.observaciones()),participantes,texto(d.lugarDeclarado()));
    }
    @Override @Transactional
    public DatosVisita validarProgramacion(DatosVisita bruto,Actor actor){
        var d=normalizar(bruto);contextos.exigirAgente(actor);var p=acceso.contextoParaNegociacion(d.idOportunidad(),actor);
        if(p.responsableActual()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        exacta(d.versionContexto(),p.version());
        if(!OportunidadComercial.ABIERTA.equals(p.estado()))throw new ReglaNegocioException("La oportunidad debe estar ABIERTA para programar la visita.");
        return d;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaVisita programar(DatosVisita bruto,Actor actor,ContextoSesion sesion,Procedencia p){
        var d=normalizar(bruto);iniciar(actor,sesion,p);
        var op=acceso.bloquearActividad(d.idOportunidad(),d.versionContexto(),actor);validarProgramacion(d,actor);
        var v=new Visita();v.setOrganizacionId(actor.idOrganizacion());v.setOportunidad(op);v.setAgente(op.getAgente());
        v.setFechaVisita(d.fechaVisita());v.setHoraVisita(d.horaVisita());v.setObservaciones(d.observaciones());
        v.setParticipantesJson(documentos.lista(d.participantes()));v.setLugarDeclarado(d.lugarDeclarado());
        transiciones.iniciar(v,Visita.PROGRAMADA);return guardar(v,"programar_visita",null,p);
    }
    private Reprogramacion normalizar(Reprogramacion d){
        if(d==null)throw new ReglaNegocioException("Aporta la nueva programación.");
        identidad(d.idVisita(),"la visita");version(d.versionEsperada(),"Visita");fechaHora(d.fechaVisita(),d.horaVisita());
        if(texto(d.motivo())==null)throw new ReglaNegocioException("Indica el motivo de reprogramación.");
        return new Reprogramacion(d.idVisita(),d.versionEsperada(),d.fechaVisita(),d.horaVisita(),texto(d.motivo()));
    }
    @Override @Transactional
    public Reprogramacion validarReprogramacion(Reprogramacion bruto,Actor a){
        var d=normalizar(bruto);var v=paraEscritura(d.idVisita(),d.versionEsperada(),a,false);
        modificable(v,"reprogramar");return d;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaVisita reprogramar(Reprogramacion bruto,Actor a,ContextoSesion s,Procedencia p){
        var d=normalizar(bruto);iniciar(a,s,p);var v=paraEscritura(d.idVisita(),d.versionEsperada(),a,true);modificable(v,"reprogramar");
        v.moverA(d.fechaVisita(),d.horaVisita());transiciones.aplicar(v,v.getId(),Visita.REPROGRAMADA,a,d.motivo());
        return guardar(v,"reprogramar_visita",d.motivo(),p);
    }
    private Cancelacion normalizar(Cancelacion d){
        if(d==null)throw new ReglaNegocioException("Aporta la cancelación de visita.");
        identidad(d.idVisita(),"la visita");version(d.versionEsperada(),"Visita");fecha(d.fechaHecho());
        if(texto(d.motivo())==null)throw new ReglaNegocioException("Indica el motivo de cancelación.");
        return new Cancelacion(d.idVisita(),d.versionEsperada(),texto(d.motivo()),d.fechaHecho());
    }
    @Override @Transactional
    public Cancelacion validarCancelacion(Cancelacion bruto,Actor a){
        var d=normalizar(bruto);modificable(paraEscritura(d.idVisita(),d.versionEsperada(),a,false),"cancelar");return d;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaVisita cancelar(Cancelacion bruto,Actor a,ContextoSesion s,String clave,Procedencia p){
        var d=normalizar(bruto);iniciar(a,s,p);String k=Idempotencia.exigirClave(clave);
        var previo=recibos.buscarOriginal(a,k,RecibosDeActividad.CANCELAR,d,p);
        if(previo.isPresent()){var f=documentos.comoObjeto(previo.get(),FichaVisita.class);autorizarSnapshot(f,a);return f;}
        var v=paraEscritura(d.idVisita(),d.versionEsperada(),a,true);modificable(v,"cancelar");
        v.registrarMotivoYLimpiarDesenlace(d.motivo());v.setFechaHecho(d.fechaHecho());
        transiciones.aplicar(v,v.getId(),Visita.CANCELADA,a,d.motivo(),d.fechaHecho());
        var f=guardar(v,"cancelar_visita",d.motivo(),p);String json=documentos.objetoCanonico(f);
        recibos.registrar(a,k,RecibosDeActividad.CANCELAR,d,p,Visita.ENTIDAD_TIPO,v.getId(),f);
        return documentos.comoObjeto(json,FichaVisita.class);
    }
    @Override @Transactional(readOnly=true)
    public Optional<FichaVisita> consultarResultadoCancelacion(String clave,Actor a){
        contextos.exigirOperador(a);return recibos.consultar(a,clave,RecibosDeActividad.CANCELAR).map(r->{
            var f=documentos.comoObjeto(r,FichaVisita.class);autorizarSnapshot(f,a);return f;
        });
    }
    private DesenlaceVisita normalizar(DesenlaceVisita d){
        if(d==null||d.ocurrencia()==null)throw new ReglaNegocioException("Declara si la visita se realizó o no.");
        version(d.versionEsperada(),"Visita");fecha(d.fechaHecho());
        if(texto(d.resultado())==null)throw new ReglaNegocioException("Conserva el resultado contextual observado o declarado.");
        String obj=Vocabulario.opcional(d.objecionPrincipal(),Vocabulario.OBJECIONES_VISITA,"ObjecionVisita");
        String opinion=Vocabulario.opcional(d.opinionPrecio(),Vocabulario.OPINIONES_PRECIO,"OpinionPrecio");
        String proxima=Vocabulario.opcional(d.proximaAccion(),Vocabulario.PROXIMAS_ACCIONES,"ProximaAccionVisita");
        if(d.nivelInteres()!=null&&(d.nivelInteres()<1||d.nivelInteres()>5))throw new ReglaNegocioException("El nivel de interés admite 1 a 5.");
        if(d.ocurrencia()==Ocurrencia.NO_REALIZADA&&(d.nivelInteres()!=null||obj!=null||opinion!=null||proxima!=null))
            throw new ReglaNegocioException("Las opiniones de la visita requieren que se haya realizado; conserva lo conocido en el resultado contextual.");
        return new DesenlaceVisita(d.versionEsperada(),d.ocurrencia(),d.fechaHecho(),texto(d.resultado()),texto(d.observaciones()),
            d.nivelInteres(),obj,opinion,proxima,texto(d.motivoRectificacion()));
    }
    private void comprobarResultado(Visita v,DesenlaceVisita d){
        if(Visita.CANCELADA.equals(v.estadoActual()))throw new ReglaNegocioException("Una visita cancelada no admite declarar ocurrencia ordinaria.");
        boolean terminal=Visita.REALIZADA.equals(v.estadoActual())||Visita.NO_REALIZADA.equals(v.estadoActual());
        String destino=d.ocurrencia()==Ocurrencia.REALIZADA?Visita.REALIZADA:Visita.NO_REALIZADA;
        if((v.tieneDesenlace()||v.getFechaHecho()!=null||(terminal&&!destino.equals(v.estadoActual())))&&d.motivoRectificacion()==null)
            throw new ReglaNegocioException("Indica el motivo de rectificación; la declaración anterior se conserva.");
    }
    @Override @Transactional
    public DesenlaceVisita validarResultado(long id,DesenlaceVisita bruto,Actor a){
        var d=normalizar(bruto);comprobarResultado(paraEscritura(id,d.versionEsperada(),a,false),d);return d;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaVisita registrarResultado(long id,DesenlaceVisita bruto,Actor a,ContextoSesion s,Procedencia p){
        var d=normalizar(bruto);iniciar(a,s,p);var v=paraEscritura(id,d.versionEsperada(),a,true);comprobarResultado(v,d);
        String destino=d.ocurrencia()==Ocurrencia.REALIZADA?Visita.REALIZADA:Visita.NO_REALIZADA;
        transiciones.aplicar(v,v.getId(),destino,a,d.motivoRectificacion()==null?d.resultado():d.motivoRectificacion(),d.fechaHecho());
        v.registrarDesenlace(d.resultado(),d.observaciones(),d.nivelInteres(),d.objecionPrincipal(),d.opinionPrecio(),d.proximaAccion());
        v.setFechaHecho(d.fechaHecho());return guardar(v,"registrar_resultado_visita",d.motivoRectificacion(),p);
    }
    private void iniciar(Actor a,ContextoSesion s,Procedencia p){
        if(p==null)throw new ReglaNegocioException("Conserva la procedencia del hecho.");contextos.iniciar(a,s);
    }
    private Visita paraEscritura(long id,Long version,Actor a,boolean bloquear){
        contextos.exigirAgente(a);identidad(id,"la visita");version(version,"Visita");
        long op=visitas.oportunidadDe(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Visita"));
        var actual=acceso.contextoParaNegociacion(op,a);
        if(actual.responsableActual()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(bloquear)acceso.bloquearActividad(op,actual.version(),a);
        var v=bloquear?visitas.bloquear(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Visita")):cargarConAcceso(id,a);
        if(bloquear)entidades.refresh(v,LockModeType.PESSIMISTIC_WRITE);
        if(v.getAgente()==null||v.getAgente().getId()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
        exacta(version,v.getVersion());
        if(bloquear&&visitas.fichaVersion(a.idOrganizacion(),id,v.getVersion()).isEmpty())
            visitas.historizar(a.idOrganizacion(),id,op,v.getVersion(),contextos.ahora(),"ANTECEDENTE_OBSERVADO",
                "Estado observado antes de la modificación; sin atribuir fecha histórica desconocida.",documentos.objetoCanonico(ficha(v)));
        return v;
    }
    private void modificable(Visita v,String accion){
        if(!v.esModificable())throw new ReglaNegocioException("No se puede "+accion+" una visita "+DESCRIPCION_ESTADO.getOrDefault(v.estadoActual(),"")+".");
    }
    private FichaVisita guardar(Visita v,String op,String motivo,Procedencia p){
        v.registrarCambio(contextos.ahora().atOffset(ZoneOffset.UTC),documentos.objetoCanonico(p));visitas.saveAndFlush(v);
        String ficha=documentos.objetoCanonico(ficha(v));
        visitas.historizar(v.getOrganizacionId(),v.getId(),v.getOportunidad().getId(),v.getVersion(),contextos.ahora(),op,motivo,ficha);
        // El único productor de evento/recibo AUTO es ComandosAutomaticos, dentro de esta TX.
        return documentos.comoObjeto(ficha,FichaVisita.class);
    }
    private static void exacta(long obs,long actual){if(obs!=actual)throw new ConflictoException("Cambió la visita o su contexto; revisa su versión.");}

    // ------------------------------------------------------------------
    // Alcance por rol: BROKER por CAPTACION de la oportunidad (§5).
    // ------------------------------------------------------------------

    private Visita cargarConAcceso(long id, Actor actor) {
        Visita visita = visitas.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Visita"));
        contextos.exigirOperador(actor);
        if (actor.esAgente()?visita.getAgente()==null||visita.getAgente().getId()!=actor.idRolOperativo()
                :!acceso.alcanza(visita.getOportunidad(), actor)) {
            throw new AccesoNoAutorizadoException();
        }
        return visita;
    }

    private Pagina<FichaVisita> paginaSinTotal(List<Visita> fuente,Actor actor) {
        List<FichaVisita> items = fichasEnLote(fuente,actor);
        return new Pagina<>(items, items.size());
    }

    private List<FichaVisita> fichasEnLote(List<Visita> fuente,Actor actor){
        if(fuente.isEmpty())return List.of();
        var cargadas=visitas.buscarFichaPorIds(actor.idOrganizacion(),fuente.stream().map(Visita::getId).toList())
            .stream().collect(Collectors.toMap(Visita::getId,this::ficha));
        return fuente.stream().map(v->{
            var f=cargadas.get(v.getId());if(f==null)throw new NoEncontradoException("Visita");return f;
        }).toList();
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private FichaVisita ficha(Visita v) {
        OportunidadComercial oportunidad = v.getOportunidad();
        DetalleCliente cliente = oportunidad != null ? oportunidad.getCliente() : null;
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleAgente agente = v.getAgente();
        return new FichaVisita(
                v.getId(),
                oportunidad != null ? oportunidad.getId() : null,
                oportunidad != null ? oportunidad.getCodigoOportunidad() : null,
                v.getFechaVisita(), v.getHoraVisita(), v.getObservaciones(), v.estadoActual(),
                v.getResultado(),
                cliente != null ? cliente.getId() : null,
                oportunidad!=null&&oportunidad.getPersonaInteresada()!=null?oportunidad.getPersonaInteresada().getNombresORazonSocial():nombre(cliente != null ? cliente.getRol() : null),
                captacion != null ? captacion.getId() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                agente != null ? agente.getId() : null,
                nombre(agente != null ? agente.getRol() : null),
                v.getNivelInteres(), v.getObjecionPrincipal(), v.getOpinionPrecio(), v.getProximaAccion(),v.getVersion(),
                v.getParticipantesJson()==null?List.of():Arrays.asList(documentos.comoObjeto(v.getParticipantesJson(),ParticipanteConocido[].class)),
                v.getLugarDeclarado(),v.getFechaHecho(),v.getRegistradoEn()==null?null:v.getRegistradoEn().toInstant(),
                v.getProcedenciaJson()==null?null:documentos.comoObjeto(v.getProcedenciaJson(),Procedencia.class));
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
