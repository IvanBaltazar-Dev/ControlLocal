package com.controllocal.service.impl;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.ReporteAvanceRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.TareaRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.TareaService;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.InterpreteDeLaBandeja;
import com.controllocal.service.soporte.LadoDeLaOperacion;
import com.controllocal.service.soporte.NaturalezaDelAsunto;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.PoliticaDeDespacho;
import com.controllocal.service.soporte.FuentesAsuntos;
import com.controllocal.service.soporte.AutoridadAsuntos;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.Recurso;
import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.materialia.ResultadosMaterialIa.*;
import com.controllocal.service.excepcion.ConflictoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * El motor de la bandeja: deriva, reconcilia, enriquece, ordena y corta.
 *
 * <p><b>Que cambia respecto de la v1 y que no.</b> El conjunto de tareas es el
 * mismo, disparador por disparador. Lo que cambia es como se llega a el: la v1
 * cargaba en memoria TODAS las prospecciones, solicitudes, oportunidades,
 * captaciones, visitas y contratos del agente y filtraba en Java; aqui cada
 * disparador pregunta por lo suyo y su condicion baja al WHERE (MEJ-05 /
 * RC-003). El enriquecimiento ya no necesita el mapa-por-id de la v1 porque la
 * consulta trae el codigo y la fecha de plazo consigo.
 *
 * <p>Los disparadores bajan su condición al WHERE. Las coincidencias viven en
 * {@code HallazgoService}, que consume el matching contextual de Core:
 * una coincidencia no es una tarea.
 */
@Service
public class TareaServiceImpl implements TareaService {

    // Los cuatro plazos que gobiernan los disparadores viven en
    // PoliticaComercial, no aqui: hasta E1 este archivo tenia su propia copia
    // del plazo de recontacto y otras tres andaban sueltas por el sistema.
    //
    // El tope de 10 con descarte EN SILENCIO (D-F7-2) se retiro el 2026-08-08 al
    // descongelar el contrato. Era una replica de la v1 y hacia que el agente no
    // pudiera distinguir "tengo 10 tareas" de "tengo 40": la bandeja se veia
    // igual en los dos casos, y las 30 que faltaban no aparecian en ningun
    // sitio ni dejaban rastro. El orden por prioridad ya estaba; lo que faltaba
    // era no mentir sobre el total.

    /**
     * Las unicas {@code entidad_tipo} que se auto-resuelven al reconciliar. Una
     * tarea sobre cualquier otra entidad no se cierra sola nunca (§5.2,
     * trampa 2).
     */
    private static final Set<String> ENTIDADES_AUTO = Set.of(
            Tarea.ENTIDAD_PROSPECCION, "SOLICITUD_ALQUILER", "CONTRATO_ALQUILER",
            "VISITA", "CAPTACION", "REQUERIMIENTO");

    /**
     * Anade a cada asunto su interpretacion: como esta, su expediente y la
     * lectura que lo sintetiza (E2.4).
     *
     * <p>Va aparte del mapeo porque necesita el contexto de TODA la pagina, y
     * ese contexto se carga una vez.
     */
    private List<FichaTarea> interpretadas(Actor actor,List<FichaTarea> asuntos,LocalDate hoy,Map<Long,String> marcas) {
        if (asuntos.isEmpty()) {
            return asuntos;
        }
        List<InterpreteDeLaBandeja.AsuntoADescribir> aDescribir = asuntos.stream()
                .map(t->aDescribir(t,marcas.get(t.id())))
                .toList();
        InterpreteDeLaBandeja.Contexto contexto = interprete.contextoDe(actor,aDescribir);

        List<FichaTarea> conLectura = new java.util.ArrayList<>(asuntos.size());
        for (int i = 0; i < asuntos.size(); i++) {
            FichaTarea t = asuntos.get(i);
            conLectura.add(new FichaTarea(t.id(), t.tipo(), t.entidadTipo(), t.entidadId(),
                    t.entidadCodigo(), t.rutaResolver(), t.descripcion(), t.estado(),
                    t.prioridad(), t.fechaProgramada(), t.diasSinAccion(), t.fechaVencimiento(),
                    t.dependeDeMi(), t.lado(), t.paso(),
                    interprete.de(aDescribir.get(i), contexto, hoy),t.horaActuacion(),t.orden()));
        }
        return List.copyOf(conLectura);
    }

    private static InterpreteDeLaBandeja.AsuntoADescribir aDescribir(FichaTarea t,String marca) {
        return new InterpreteDeLaBandeja.AsuntoADescribir(t.tipo(), t.entidadTipo(),
                t.entidadId(), t.descripcion(), t.diasSinAccion(), t.fechaVencimiento(),
                t.dependeDeMi(),marca);
    }

    /**
     * <b>El orden ya no se decide aqui.</b>
     *
     * <p>Aqui vivia `ALTA antes que MEDIA, y a igual prioridad lo mas rezagado
     * primero`: dos criterios, mientras la pantalla aplicaba los suyos y el
     * diseno declaraba seis. Tres opiniones sobre lo mismo.
     *
     * <p>Ahora la unica es {@link PoliticaDeDespacho}, y este servicio solo le
     * dice los HECHOS de cada asunto.
     */
    private static PoliticaDeDespacho.Asunto comoAsunto(FichaTarea t) {
        return comoAsunto(t,t.orden()==null?null:t.orden().revisarEn());
    }
    private static PoliticaDeDespacho.Asunto comoAsunto(FichaTarea t,LocalDate revisarEn) {
        return new PoliticaDeDespacho.Asunto("TAREA:"+t.id(),t.entidadTipo(),t.entidadId(),
                t.dependeDeMi(),t.fechaVencimiento(),t.horaActuacion(),
                t.fechaProgramada()==null?null:t.fechaProgramada().toInstant(),false,null,false,false,revisarEn);
    }
    private FichaTarea clasificada(FichaTarea t,LocalDate revisarEn) {
        return new FichaTarea(t.id(),t.tipo(),t.entidadTipo(),t.entidadId(),t.entidadCodigo(),t.rutaResolver(),t.descripcion(),
                t.estado(),t.prioridad(),t.fechaProgramada(),t.diasSinAccion(),t.fechaVencimiento(),t.dependeDeMi(),t.lado(),t.paso(),
                t.interpretacion(),t.horaActuacion(),PoliticaDeDespacho.clasificar(comoAsunto(t,revisarEn),contexto.ahora()));
    }

    private final TareaRepository tareas;
    private final ContextosDeActividad contexto;
    private final SolicitudAlquilerRepository solicitudes;
    private final VisitaRepository visitas;
    private final CaptacionRepository captaciones;
    private final ContratoAlquilerRepository contratos;
    private final ReporteAvanceRepository reportes;
    private final DetalleAgenteRepository agentes;
    private final InterpreteDeLaBandeja interprete;
    private final SeguimientoDeProspeccion seguimiento;
    private final ResultadosMaterialIa material;
    private final FuentesAsuntos fuentesAsuntos;
    private final AutoridadAsuntos autoridadAsuntos;

    public TareaServiceImpl(TareaRepository tareas, ContextosDeActividad contexto,
                            SolicitudAlquilerRepository solicitudes, VisitaRepository visitas,
                            CaptacionRepository captaciones, ContratoAlquilerRepository contratos,
                            ReporteAvanceRepository reportes,
                            DetalleAgenteRepository agentes,
                            InterpreteDeLaBandeja interprete,SeguimientoDeProspeccion seguimiento,ResultadosMaterialIa material,
                            FuentesAsuntos fuentesAsuntos,AutoridadAsuntos autoridadAsuntos) {
        this.tareas = tareas;
        this.contexto = contexto;
        this.solicitudes = solicitudes;
        this.visitas = visitas;
        this.captaciones = captaciones;
        this.contratos = contratos;
        this.reportes = reportes;
        this.agentes = agentes;
        this.interprete = interprete;
        this.seguimiento=seguimiento;
        this.material=material;
        this.fuentesAsuntos=fuentesAsuntos;this.autoridadAsuntos=autoridadAsuntos;
    }

    @Override
    @Transactional
    public List<FichaTarea> bandejaDe(PoliticaDeDespacho.Consulta consulta,Actor actor) {
        contexto.exigirAgente(actor);
        if (!actor.esAgente()) {
            // El gate de rol lo pone el controlador; esto protege el service si
            // alguien lo llama desde otro sitio.
            throw new AccesoNoAutorizadoException();
        }
        long org = actor.idOrganizacion();
        long idAgente = actor.idRolOperativo();
        seguimiento.bloquearParaProyectar(actor);
        var necesidadesR27=necesidadesParaRadar(actor);
        List<Derivada> vigentes = derivar(actor,necesidadesR27);
        Map<UUID,GrupoCaducidad> grupos=gruposMaterial(actor);
        Map<UUID,Derivada> derivadasMaterial=derivarMaterial(actor,grupos);
        vigentes.addAll(derivadasMaterial.values());
        for(var grupo:grupos.values())if(!material.registrarCausaCaducidad(org,grupo))
            throw new ConflictoException("Cambió el estado de las versiones temporales; actualiza la bandeja.");
        Set<String> causasActuales=seguimiento.marcasActuales(actor);
        tareas.reubicarRecontactos(org); // Proyección del responsable maestro; no decisión de reasignación del agente.
        tareas.reubicarCausasSolicitudC7(org);
        Set<String> clavesVigentes = new HashSet<>();
        for (Derivada d : vigentes) {
            clavesVigentes.add(d.clave());
        }

        List<Tarea> existentes = personalesAutorizadas(actor);
        Set<Long> visiblesMaterial=reconciliarMaterial(actor,existentes,grupos,derivadasMaterial);
        // R27 bloquea la misma causa cancelada, nunca otra necesidad material del recurso.
        Set<String> bloqueadas = new HashSet<>();
        Map<String, Derivada> derivadaPorClave = new HashMap<>();
        for (Derivada d : vigentes) {
            derivadaPorClave.putIfAbsent(d.clave(), d);
        }
        for (Tarea t : existentes) {
            if(t.esAvisoMaterialIaC6())continue;
            if(t.getHuellaCausaC7()!=null){bloqueadas.add(t.claveEntidad());continue;}
            if (t.estaAbierta() || Tarea.CANCELADA.equals(t.getEstado())) {
                bloqueadas.add(t.claveEntidad());
            }
        }
        var idsR27=vigentes.stream().filter(d->d.marcaCausa()!=null).map(Derivada::entidadId).distinct().toList();
        List<Tarea> historiaR27=idsR27.isEmpty()?List.of():tareas.recontactosDeProspecciones(org,idsR27);
        for(var t:historiaR27)if(Tarea.CANCELADA.equals(t.getEstado())&&t.getMarcaCausa()!=null)bloqueadas.add(t.claveEntidad());
        for(Tarea t:existentes){
            if(t.esAvisoMaterialIaC6())continue;
            // Una fila anterior sin fuente no se convierte en la ocurrencia C7 ni en un hecho resuelto.
            if("SOLICITUD_ALQUILER".equals(t.getEntidadTipo())&&t.getHuellaCausaC7()==null)continue;
            if(t.getHuellaCausaC7()!=null){
                if("VIGENTE".equals(t.getEstadoCausaC7())&&!clavesVigentes.contains(t.claveEntidad())){
                    boolean nuevaCausa=vigentes.stream().anyMatch(d->Objects.equals(d.entidadTipo(),t.getEntidadTipo())&&Objects.equals(d.entidadId(),t.getEntidadId()));
                    t.setEstadoCausaC7(nuevaCausa?"SUSTITUIDA":"RETIRADA");tareas.save(t);
                }
                continue;
            }
            if((Tarea.PENDIENTE.equals(t.getEstado())||Tarea.RECONTACTO.equals(t.getTipo())&&Tarea.EN_PROCESO.equals(t.getEstado()))&&ENTIDADES_AUTO.contains(t.getEntidadTipo())&&!clavesVigentes.contains(t.claveEntidad())
                    &&!(Tarea.RECONTACTO.equals(t.getTipo())&&t.getMarcaCausa()!=null&&causasActuales.contains(t.getMarcaCausa()))){
                t.completar();tareas.save(t);
            }
        }

        for (Derivada d : vigentes) {
            if(Tarea.ENTIDAD_FOTO_ORIGINAL_C6.equals(d.entidadTipo()))continue;
            if (!bloqueadas.contains(d.clave())) {
                if(d.huellaC7()!=null){
                    Long anterior=tareas.antecedenteC7(org,d.entidadTipo(),d.entidadId()).orElse(null);
                    tareas.crearCausaC7(org,d.tipo(),d.entidadTipo(),d.entidadId(),idAgente,d.descripcion(),d.prioridad(),d.huellaC7(),d.fuenteC7(),anterior);
                }else if(d.marcaCausa()!=null){Long anterior=tareas.antecedenteDeRecontacto(org,d.idSeguimiento()).orElse(null);
                    tareas.crearRecontacto(org,d.entidadId(),idAgente,d.descripcion(),d.prioridad(),d.marcaCausa(),anterior,d.idSeguimiento());
                }else tareas.save(nueva(d, org, idAgente));
            }
        }
        LocalDate hoy = contexto.hoy();
        List<Tarea> filasAbiertas = personalesAutorizadas(actor).stream()
                .filter(Tarea::estaAbierta)
                .filter(t->!"SOLICITUD_ALQUILER".equals(t.getEntidadTipo())||t.getHuellaCausaC7()!=null)
                .filter(t->!t.esAvisoMaterialIaC6()||visiblesMaterial.contains(t.getId()))
                .filter(t->!Tarea.RECONTACTO.equals(t.getTipo())||t.getMarcaCausa()!=null&&derivadaPorClave.containsKey(t.claveEntidad()))
                .toList();
        var marcas=new HashMap<Long,String>();for(var t:filasAbiertas)if(t.getMarcaCausa()!=null){
            marcas.put(t.getId(),t.getMarcaCausa());t.setAgente(agentes.getReferenceById(idAgente));
        }
        List<FichaTarea> abiertas=filasAbiertas.stream()
                .map(t -> ficha(t, derivadaPorClave.get(t.claveEntidad()), hoy))
                .toList();

        // La interpretacion se compone DESPUES y por lote: tres consultas para
        // toda la pagina. Hacerlo dentro del `map` de arriba serian tres por
        // fila, que es el N+1 que vuelve cada vez que se anade una capa.
        abiertas = interpretadas(actor,abiertas,hoy,marcas);
        if(!necesidadesR27.equals(necesidadesParaRadar(actor)))throw new com.controllocal.service.excepcion.ConflictoException("Cambió el seguimiento o su permiso; actualiza la bandeja.");
        if(!huellasGrupos(grupos).equals(huellasGrupos(gruposMaterial(actor))))throw new ConflictoException("Cambió la disponibilidad o el permiso de las versiones temporales; actualiza la bandeja.");
        var fuentesC7=filasAbiertas.stream().filter(t->t.getFuenteCausaC7()!=null).map(t->fuentesAsuntos.leer(t.getFuenteCausaC7())).toList();
        if(fuentesAsuntos.vigentes(fuentesC7,actor).size()!=new HashSet<>(fuentesC7).size())throw new ConflictoException("Cambió la causa o el permiso de los asuntos; actualiza la bandeja.");

        // Catálogo16: bandas y orden explícitos dentro de la consulta autorizada.
        var revisiones=new HashMap<String,LocalDate>();
        for(var n:necesidadesR27)if(n.revision().pospuestaHasta()!=null)revisiones.put(n.marcaCausa(),n.revision().pospuestaHasta());
        return PoliticaDeDespacho.despachar(abiertas.stream().map(t->clasificada(t,revisiones.get(marcas.get(t.id())))).toList(),TareaServiceImpl::comoAsunto,consulta,contexto.ahora());
    }

    @Override
    @Transactional
    public void cancelar(long id, Actor actor) {
        contexto.exigirAgente(actor);
        var cabecera=tareas.cabeceraPersonal(actor.idOrganizacion(),actor.idRolOperativo(),id)
                .orElseThrow(()->new ReglaNegocioException("Tarea no encontrada."));
        if(cabecera.getFuenteCausa()!=null){
            var causa=causaCabecera(cabecera,actor);
            if(!fuentesAsuntos.vigentes(List.of(causa),actor).contains(causa))throw new AccesoNoAutorizadoException();
        }
        if(tareas.avisoPropioC6(actor.idOrganizacion(),id,actor.idRolOperativo()))seguimiento.bloquearParaProyectar(actor);
        Tarea tarea = tareas.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new ReglaNegocioException("Tarea no encontrada."));
        if(tarea.getFuenteCausaC7()!=null){
            var causa=fuentesAsuntos.leer(tarea.getFuenteCausaC7());
            if(!"VIGENTE".equals(tarea.getEstadoCausaC7())||!fuentesAsuntos.vigentes(List.of(causa),actor).contains(causa))throw new AccesoNoAutorizadoException();
        }
        if(tarea.esAvisoMaterialIaC6()){
            if(tarea.getAgente()==null||!Objects.equals(tarea.getAgente().getId(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
            var grupo=gruposMaterial(actor).get(tarea.getOriginalMaterialIaC6());
            if(grupo==null||!"VIGENTE".equals(tarea.getEstadoCausaMaterialIaC6()))throw new AccesoNoAutorizadoException();
            if(!Objects.equals(tarea.getMarcaCausa(),grupo.huellaCausa()))throw new ConflictoException("El aviso tiene nuevas versiones; revísalo antes de cancelarlo.");
        }
        if(Tarea.RECONTACTO.equals(tarea.getTipo())&&tarea.getMarcaCausa()!=null
                &&seguimiento.necesidadesActuales(actor).stream().noneMatch(n->n.marcaCausa().equals(tarea.getMarcaCausa())))
            throw new AccesoNoAutorizadoException();
        if(Tarea.RECONTACTO.equals(tarea.getTipo())&&tarea.getMarcaCausa()!=null)tarea.setAgente(agentes.getReferenceById(actor.idRolOperativo()));
        if (tarea.getAgente() == null || tarea.getAgente().getId() != actor.idRolOperativo()) {
            throw new ReglaNegocioException("La tarea no pertenece al agente.");
        }
        tarea.cancelar();
        tareas.save(tarea);
        if(tarea.esAvisoMaterialIaC6())tareas.observarAvisoC6(actor.idOrganizacion(),id,"CANCELACION");
    }

    @Override
    @Transactional
    public void resolverPorCierreConfirmado(long revisionHito,Actor actor) {
        var cierre=fuentesAsuntos.cierreConfirmado(revisionHito,actor);
        new java.util.TreeMap<>(cierre.recursos()).forEach((tipo,ids)->tareas.resolverPorCierre(actor.idOrganizacion(),tipo,ids));
        if(!cierre.equals(fuentesAsuntos.cierreConfirmado(revisionHito,actor)))
            throw new ConflictoException("Cambió el hecho o la autoridad del cierre de tareas.");
    }

    @Override
    @Transactional
    public int resolverDeContratoOrigen(long idContrato, Actor actor) {
        int cerradas = 0;
        for (Tarea t : tareas.abiertasDeContratoOrigen(actor.idOrganizacion(), idContrato)) {
            t.completar();
            tareas.save(t);
            cerradas++;
        }
        return cerradas;
    }

    @Override
    @Transactional
    public void crearRevisionInmueble(Long idPropiedad, Long idAgente, String motivo,
                                      Long idContratoOrigen, Actor actor) {
        if (idPropiedad == null || idAgente == null) return;
        // INMUEBLE, no "PROPIEDAD": es el vocabulario que declara
        // `ck_tarea_tipo_entidad`. Con la cadena equivocada esta tarea no se
        // podia insertar, asi que finalizar y rescindir un contrato fallaban
        // SIEMPRE con 409 — y la deduplicacion de arriba buscaba un valor que
        // no puede existir, asi que tampoco deduplicaba.
        // La deduplicacion es POR CONTRATO cuando hay contrato origen: dos
        // contratos sucesivos del mismo local merecen cada uno su revision, y
        // deduplicar por inmueble dejaria al segundo sin tarea que resolver.
        boolean yaExiste = idContratoOrigen != null
                ? !tareas.abiertasDeContratoOrigen(actor.idOrganizacion(), idContratoOrigen).isEmpty()
                : !tareas.abiertasDeEntidad(actor.idOrganizacion(),
                        Tarea.ENTIDAD_INMUEBLE, idPropiedad).isEmpty();
        if (yaExiste) return;
        Derivada derivada = new Derivada(Tarea.REVISION_INMUEBLE, Tarea.ENTIDAD_INMUEBLE, idPropiedad,
                "", Tarea.ALTA,
                "Revisar entrega, estado fisico y decision del propietario antes de reactivar el local."
                        + (motivo == null || motivo.isBlank() ? "" : " " + motivo.trim()),
                null, LocalDate.now(), "locales/" + idPropiedad);
        Tarea tarea = nueva(derivada, actor.idOrganizacion(), idAgente);
        tarea.setIdContratoOrigen(idContratoOrigen);
        tareas.save(tarea);
    }

    // ------------------------------------------------------------------
    // Derivacion: que tareas justifica hoy el estado del flujo (§5.1)
    // ------------------------------------------------------------------

    /**
     * Una tarea que el estado del flujo justifica ahora mismo. Lleva ya
     * resueltos el codigo de la entidad, su plazo y la base de "dias sin
     * accion", porque los trajo la propia consulta del disparador.
     */
    private Map<UUID,GrupoCaducidad> gruposMaterial(Actor actor){
        var grupos=Objects.requireNonNull(material.avisosDeCaducidad(actor),"Falta la proyección Core de temporales.");
        var salida=new LinkedHashMap<UUID,GrupoCaducidad>();
        for(var g:grupos){
            if(g==null||g.original()==null||g.revision()<0||g.huellaCausa()==null||!g.huellaCausa().matches("[0-9a-f]{64}")
                    ||g.avisoEn()==null||g.vencePrimeroEn()==null||g.versiones()==null||g.versiones().isEmpty()
                    ||salida.put(g.original(),g)!=null)throw new IllegalStateException("La causa agrupada de temporales no conserva su contrato.");
        }
        return salida;
    }
    private record HuellaGrupo(String huella,long revision,java.time.Instant aviso,java.time.Instant vence) { }
    private static Map<UUID,HuellaGrupo> huellasGrupos(Map<UUID,GrupoCaducidad> grupos){
        var salida=new HashMap<UUID,HuellaGrupo>();grupos.forEach((id,g)->salida.put(id,new HuellaGrupo(g.huellaCausa(),g.revision(),g.avisoEn(),g.vencePrimeroEn())));return salida;
    }
    private Map<UUID,Derivada> derivarMaterial(Actor actor,Map<UUID,GrupoCaducidad> grupos){
        if(grupos.isEmpty())return Map.of();
        var fotos=Objects.requireNonNull(tareas.fotosParaAvisosC6(actor.idOrganizacion(),grupos.keySet()),"Falta el origen de los avisos.");
        var salida=new LinkedHashMap<UUID,Derivada>();var ids=new HashSet<Long>();
        for(var foto:fotos){
            var g=grupos.get(foto.getOriginal());
            if(g==null||foto.getIdFoto()==null||foto.getIdFoto()<=0||!ids.add(foto.getIdFoto())||salida.containsKey(foto.getOriginal()))
                throw new IllegalStateException("El original de la causa no es unívoco.");
            String fecha=DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm").withZone(ZoneId.of("America/Lima")).format(g.vencePrimeroEn());
            String descripcion=(g.versiones().size()==1?"Revisa el resultado temporal.":"Revisa los "+g.versiones().size()+" resultados temporales.")+" Próximo vencimiento: "+fecha+" (Lima).";
            salida.put(foto.getOriginal(),new Derivada(Tarea.SEGUIMIENTO,Tarea.ENTIDAD_FOTO_ORIGINAL_C6,foto.getIdFoto(),foto.getOriginal().toString(),
                    null,descripcion,g.vencePrimeroEn().atZone(ZoneId.of("America/Lima")).toLocalDate(),null,
                    "material-ia/fotografias/"+foto.getOriginal()+"/versiones"));
        }
        if(!salida.keySet().equals(grupos.keySet()))throw new ConflictoException("Cambió la fotografía original del aviso; actualiza la bandeja.");
        return salida;
    }
    private Set<Long> reconciliarMaterial(Actor actor,List<Tarea> existentes,Map<UUID,GrupoCaducidad> grupos,Map<UUID,Derivada> derivadas){
        var ocupadas=new HashSet<UUID>();var visibles=new HashSet<Long>();var canceladas=new HashMap<UUID,Tarea>();
        var anteriores=new HashMap<UUID,Tarea>();long org=actor.idOrganizacion();
        for(var t:existentes){
            if(!t.esAvisoMaterialIaC6())continue;
            UUID original=t.getOriginalMaterialIaC6();anteriores.merge(original,t,(a,b)->a.getId()>b.getId()?a:b);
            if(!"VIGENTE".equals(t.getEstadoCausaMaterialIaC6()))continue;
            var estado=Objects.requireNonNull(material.estadoCausaCaducidad(org,original,t.getMarcaCausa()),"Falta el estado de la causa conocida.");
            if(estado==EstadoCausaCaducidad.FINALIZADA||estado==EstadoCausaCaducidad.SUSTITUIDA){
                retirarCausaMaterial(actor,t,estado==EstadoCausaCaducidad.FINALIZADA?"FINALIZADA":"SUSTITUIDA");continue;
            }
            if(Tarea.CANCELADA.equals(t.getEstado())){
                canceladas.merge(original,t,(a,b)->a.getId()>b.getId()?a:b);continue;
            }
            if(!t.estaAbierta())continue;
            ocupadas.add(original);var g=grupos.get(original);
            if(estado==EstadoCausaCaducidad.NO_EVALUABLE||g==null)continue;
            var d=derivadas.get(original);
            if(!Objects.equals(t.getMarcaCausa(),g.huellaCausa())||!Objects.equals(t.getRevisionMaterialIaC6(),g.revision())){
                t.setMarcaCausa(g.huellaCausa());t.setRevisionMaterialIaC6(g.revision());t.setDescripcion(d.descripcion());
                tareas.save(t);tareas.observarAvisoC6(org,t.getId(),"ACTUALIZACION");
            }
            visibles.add(t.getId());
        }
        for(var g:grupos.values()){
            if(ocupadas.contains(g.original()))continue;
            var cancelada=canceladas.get(g.original());
            if(cancelada!=null){
                var estado=Objects.requireNonNull(material.estadoCausaCaducidad(org,g.original(),cancelada.getMarcaCausa(),g.huellaCausa()),"Falta la comparación de episodios conocidos.");
                if(estado==EstadoCausaCaducidad.VIGENTE||estado==EstadoCausaCaducidad.NO_EVALUABLE)continue;
                retirarCausaMaterial(actor,cancelada,estado==EstadoCausaCaducidad.SUSTITUIDA?"SUSTITUIDA":"FINALIZADA");
            }
            var anterior=anteriores.get(g.original());var d=derivadas.get(g.original());
            tareas.crearAvisoC6(org,d.entidadId(),actor.idRolOperativo(),d.descripcion(),g.avisoEn(),g.huellaCausa(),anterior==null?null:anterior.getId(),g.original(),g.revision());
            long id=tareas.avisoAbiertoC6(org,actor.idRolOperativo(),g.original()).orElseThrow(()->new ConflictoException("Cambió el aviso; actualiza la bandeja."));
            tareas.observarAvisoC6(org,id,"CREACION");visibles.add(id);
        }
        return visibles;
    }
    private void retirarCausaMaterial(Actor actor,Tarea tarea,String estado){
        tarea.setEstadoCausaMaterialIaC6(estado);tareas.save(tarea);
        tareas.observarAvisoC6(actor.idOrganizacion(),tarea.getId(),"FINALIZADA".equals(estado)?"CAUSA_FINALIZADA":"CAUSA_SUSTITUIDA");
    }

    private record Derivada(String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                            String prioridad, String descripcion, LocalDate fechaVencimiento,
                            LocalDate fechaBase, String rutaExplicita,String marcaCausa,Long idSeguimiento,String huellaC7,String fuenteC7,java.time.LocalTime hora) {
        Derivada(String tipo,String entidadTipo,Long entidadId,String entidadCodigo,String prioridad,String descripcion,LocalDate fechaVencimiento,LocalDate fechaBase,String rutaExplicita,String marcaCausa,Long idSeguimiento,String huellaC7,String fuenteC7){this(tipo,entidadTipo,entidadId,entidadCodigo,prioridad,descripcion,fechaVencimiento,fechaBase,rutaExplicita,marcaCausa,idSeguimiento,huellaC7,fuenteC7,null);}
        Derivada(String tipo,String entidadTipo,Long entidadId,String entidadCodigo,String prioridad,String descripcion,LocalDate fechaVencimiento,
                 LocalDate fechaBase,String rutaExplicita,String marcaCausa,Long idSeguimiento){
            this(tipo,entidadTipo,entidadId,entidadCodigo,prioridad,descripcion,fechaVencimiento,fechaBase,rutaExplicita,marcaCausa,idSeguimiento,null,null);
        }

        /** Para los seis disparadores cuya ruta sale del tipo de entidad. */
        Derivada(String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                 String prioridad, String descripcion, LocalDate fechaVencimiento, LocalDate fechaBase) {
            this(tipo, entidadTipo, entidadId, entidadCodigo, prioridad, descripcion,
                    fechaVencimiento, fechaBase, null,null,null);
        }
        Derivada(String tipo,String entidadTipo,Long entidadId,String entidadCodigo,String prioridad,String descripcion,LocalDate fechaVencimiento,LocalDate fechaBase,String rutaExplicita){this(tipo,entidadTipo,entidadId,entidadCodigo,prioridad,descripcion,fechaVencimiento,fechaBase,rutaExplicita,null,null);}
        String clave(){String base=Tarea.claveEntidad(entidadTipo,entidadId);return huellaC7!=null?base+":"+huellaC7:marcaCausa==null?base:base+":"+marcaCausa;}
    }

    private List<SeguimientoDeProspeccion.Necesidad> necesidadesParaRadar(Actor actor){
        return seguimiento.necesidadesActuales(actor).stream().filter(n->!n.revision().restringido()&&!n.revision().alcancePorPrecisar()&&n.revision().proximaFecha()!=null).toList();
    }
    private List<Derivada> derivar(Actor actor,List<SeguimientoDeProspeccion.Necesidad> necesidades) {
        long org=actor.idOrganizacion(),idAgente=actor.idRolOperativo();
        List<Derivada> out = new ArrayList<>();
        LocalDate hoy = contexto.hoy();

        // 1) R27: fechas reales del destinatario/finalidad; nada se infiere del antiguo botón de seguimiento.
        for(var n:necesidades){
            var r=n.revision();String texto=r.revisarContinuidad()?"Revisa la continuidad: reprograma, conserva sin fecha o descarta con motivo.":"Revisa la próxima actuación con el propietario o representante.";
            out.add(new Derivada(Tarea.RECONTACTO,Tarea.ENTIDAD_PROSPECCION,r.idProspeccion(),"#"+r.idProspeccion(),Tarea.ALTA,texto,
                    r.proximaFecha(),r.proximaFecha(),null,n.marcaCausa(),r.id()));
        }

        var aprobadas=solicitudes.porEstadoDelAgente(org,idAgente,SolicitudAlquiler.APROBADA);
        var observadas=solicitudes.porEstadoDelAgente(org,idAgente,SolicitudAlquiler.OBSERVADA);
        var idsSolicitud=new LinkedHashSet<Long>();aprobadas.forEach(c->idsSolicitud.add(c.getEntidadId()));observadas.forEach(c->idsSolicitud.add(c.getEntidadId()));
        var causasSolicitud=idsSolicitud.isEmpty()?Map.<Long,FuentesAsuntos.Causa>of():fuentesAsuntos.solicitudes(idsSolicitud,actor);
        // 2) Solicitudes aprobadas sin cierre: su transición aceptada distingue la nueva ocurrencia.
        for (CandidatoTarea c : aprobadas) {
            var causa=causaSolicitud(causasSolicitud,c.getEntidadId(),SolicitudAlquiler.APROBADA);
            out.add(new Derivada(Tarea.SEGUIMIENTO, "SOLICITUD_ALQUILER", c.getEntidadId(),
                    nz(c.getEntidadCodigo()), Tarea.ALTA, "Solicitud aprobada pendiente de cierre.",
                    c.getFechaPlazo(), fechaDeMarca(c.getMarca()),null,null,null,fuentesAsuntos.huella(org,causa),fuentesAsuntos.guardar(causa)));
        }

        // 3) Comisiones PENDIENTES con monto del agente ya asignado: listas para cobro.
        for (CandidatoTarea c : contratos.conComisionListaParaCobro(org, idAgente)) {
            out.add(new Derivada(Tarea.SEGUIMIENTO, "CONTRATO_ALQUILER", c.getEntidadId(),
                    nz(c.getEntidadCodigo()), Tarea.MEDIA,
                    "Comision lista para cobro (contrato " + c.getEntidadId() + ").",
                    null, c.getFechaPlazo()));
        }

        // 4) Documentos observados: el broker devolvio la solicitud OBSERVADA.
        for (CandidatoTarea c : observadas) {
            var causa=causaSolicitud(causasSolicitud,c.getEntidadId(),SolicitudAlquiler.OBSERVADA);
            out.add(new Derivada(Tarea.SUBIR_DOCUMENTOS, "SOLICITUD_ALQUILER", c.getEntidadId(),
                    nz(c.getEntidadCodigo()), Tarea.ALTA, "Documentos observados: subsana la solicitud.",
                    c.getFechaPlazo(), fechaDeMarca(c.getMarca()),null,null,null,fuentesAsuntos.huella(org,causa),fuentesAsuntos.guardar(causa)));
        }

        // 5) Visitas que requieren accion: caidas, vencidas sin cerrar o proximas.
        for (CandidatoTarea c : visitas.queExigenAccion(
                org, idAgente)) {
            LocalDate fecha = c.getFechaPlazo();
            boolean noRealizada = Visita.NO_REALIZADA.equals(c.getMarca());
            boolean vencida = fecha != null && fecha.isBefore(hoy);
            String descripcion;
            if (noRealizada) {
                descripcion = "Visita no realizada: reprograma o descarta.";
            } else if (vencida) {
                descripcion = "Visita del " + fecha + " sin cerrar: marca el resultado (¿se realizo?).";
            } else {
                descripcion = "Visita proxima (" + fecha + "): prepara, reprograma o cancela.";
            }
            out.add(new Derivada(Tarea.VISITA, "VISITA", c.getEntidadId(), nz(c.getEntidadCodigo()),
                    noRealizada || vencida ? Tarea.ALTA : Tarea.MEDIA, descripcion, fecha, fecha,null,null,null,null,null,c.getHoraPlazo()));
        }

        // 6) Reporte periodico al propietario de las captaciones ACTIVAS.
        derivarReportes(org, idAgente, hoy, out);

        // HallazgoService publica coincidencias fuera de las tareas del foco.
        // PoliticaDeOrdenUnicaTest y HallazgoFueraDelFocoTest conservan esa frontera.

        return out;
    }

    /** Autoriza cabeceras de Grupo A antes de cargar la descripción o la historia de sus filas. */
    private List<Tarea> personalesAutorizadas(Actor actor){
        var cabeceras=tareas.cabecerasPersonales(actor.idOrganizacion(),actor.idRolOperativo());
        var consultas=new HashMap<Long,AutoridadAsuntos.Consulta>();
        for(var c:cabeceras)if(c.getFuenteCausa()!=null){
            var causa=causaCabecera(c,actor);consultas.put(c.getId(),new AutoridadAsuntos.Consulta(causa.recurso(),causa.finalidad()));
        }
        var legibles=consultas.isEmpty()?Set.<AutoridadAsuntos.Consulta>of():autoridadAsuntos.legibles(consultas.values(),actor);
        var ids=cabeceras.stream().filter(c->c.getFuenteCausa()==null||legibles.contains(consultas.get(c.getId()))).map(TareaRepository.CabeceraC7::getId).toList();
        return ids.isEmpty()?List.of():tareas.personalesAutorizadas(actor.idOrganizacion(),actor.idRolOperativo(),ids);
    }
    private FuentesAsuntos.Causa causaCabecera(TareaRepository.CabeceraC7 c,Actor actor){
        var causa=fuentesAsuntos.leer(c.getFuenteCausa());
        if(!causa.recurso().equals(new Recurso(c.getEntidadTipo(),c.getEntidadId()))
                ||!Objects.equals(c.getHuellaCausa(),fuentesAsuntos.huella(actor.idOrganizacion(),causa)))
            throw new IllegalStateException("La tarea no conserva su identidad de causa.");
        return causa;
    }
    private static FuentesAsuntos.Causa causaSolicitud(Map<Long,FuentesAsuntos.Causa> causas,long id,String estado){
        var causa=causas.get(id);if(causa==null||!Objects.equals(causa.estadoFuente(),estado))throw new ConflictoException("No se pudo acreditar la transición vigente de la solicitud; actualiza la bandeja.");return causa;
    }

    /**
     * Disparador 6. El reloj arranca en el ultimo reporte de la captacion o, si
     * no hay ninguno, en su fecha de captacion; vencido el periodo, la tarea
     * vuelve a pedirse. Registrar un reporte lo reinicia y la tarea se
     * auto-resuelve (CAPTACION esta en ENTIDADES_AUTO).
     */
    private void derivarReportes(long org, long idAgente, LocalDate hoy, List<Derivada> out) {
        List<CandidatoTarea> activas = captaciones.activasDelAgente(org, idAgente);
        if (activas.isEmpty()) {
            return;
        }
        List<Long> ids = activas.stream().map(CandidatoTarea::getEntidadId).toList();
        Map<Long, LocalDate> ultimoReporte = new HashMap<>();
        for (Object[] fila : reportes.ultimasComunicaciones(org, ids)) {
            ultimoReporte.put((Long) fila[0], (LocalDate) fila[1]);
        }
        for (CandidatoTarea c : activas) {
            LocalDate desde = ultimoReporte.get(c.getEntidadId());
            if (desde == null) {
                desde = c.getFechaPlazo() != null ? c.getFechaPlazo() : hoy;
            }
            LocalDate vence = PoliticaComercial.proximoReporteAlPropietario(desde);
            if (vence.isAfter(hoy)) {
                continue;
            }
            String codigo = nz(c.getEntidadCodigo());
            out.add(new Derivada(Tarea.REPORTE_PROPIETARIO, "CAPTACION", c.getEntidadId(), codigo,
                    Tarea.MEDIA,
                    "Reporta avances al propietario de la captacion " + codigo + ".",
                    vence, desde));
        }
    }

    // ------------------------------------------------------------------
    // Persistencia y lectura
    // ------------------------------------------------------------------

    private Tarea nueva(Derivada d, long org, long idAgente) {
        Tarea tarea = new Tarea();
        tarea.setOrganizacionId(org);
        tarea.setTipo(d.tipo());
        tarea.setEntidadTipo(d.entidadTipo());
        tarea.setEntidadId(d.entidadId());
        tarea.setAgente(agentes.getReferenceById(idAgente));
        tarea.setDescripcion(d.descripcion());
        tarea.setFechaProgramada(contexto.ahora().atOffset(java.time.ZoneOffset.UTC));
        tarea.nacer(d.prioridad());
        return tarea;
    }

    /**
     * Enriquecimiento (§5.3). Los cuatro derivados salen de la {@code Derivada}
     * que la justifica hoy; si una tarea abierta ya no tiene disparador —caso
     * de las entidades que no se auto-resuelven— se cae a lo que hay en la
     * fila, que es exactamente lo que hacia el {@code try/catch} best-effort de
     * la v1.
     *
     * <p><b>El detalle que mas facil se porta mal</b>: {@code diasSinAccion} se
     * cuenta desde el plazo REAL de la entidad, no desde que se creo la tarea.
     * Con la fecha de la tarea daria casi siempre 0.
     */
    private static FichaTarea ficha(Tarea t, Derivada d, LocalDate hoy) {
        String codigo = d != null ? d.entidadCodigo() : "";
        // C6 informa conservación: disponer de un aviso no acredita atraso comercial.
        LocalDate base = t.esAvisoMaterialIaC6() ? null : (d != null && d.fechaBase() != null
                ? d.fechaBase()
                : t.getFechaProgramada().toLocalDate());
        String ruta = d != null && d.rutaExplicita() != null
                ? d.rutaExplicita()
                : ruta(t.getEntidadTipo(), t.getEntidadId(), codigo);
        // Los tres derivados de E2.2. Salen del tipo del asunto y del tipo de su
        // entidad: ninguno es columna, y por eso una tarea vieja los responde
        // igual que una recien creada.
        LadoDeLaOperacion.Ubicacion ubicacion = LadoDeLaOperacion.de(t.getEntidadTipo());
        boolean dependeDeMi = NaturalezaDelAsunto.dependeDelAgente(t.getTipo(), t.getEntidadTipo());

        return new FichaTarea(t.getId(), t.getTipo(), t.getEntidadTipo(), t.getEntidadId(),
                codigo, ruta, t.esAvisoMaterialIaC6()&&d!=null?d.descripcion():t.getDescripcion(),
                t.getEstado(), t.getPrioridad(), t.getFechaProgramada(), dias(base, hoy),
                d != null ? d.fechaVencimiento() : null,
                dependeDeMi,
                ubicacion == null ? null : ubicacion.lado().name(),
                ubicacion == null ? null : ubicacion.paso(),
                null,d==null?null:d.hora(),null);
    }

    /**
     * Ruta exacta de "Resolver". Calcada de la v1: las pantallas de detalle
     * enrutan por CODIGO (solicitud, captacion) o por id (prospeccion), las
     * visitas por deep-link y contrato/comision caen en su lista.
     * {@code REQUERIMIENTO} abre la ficha del CLIENTE, no el requerimiento.
     */
    private static String ruta(String entidadTipo, Long id, String codigo) {
        boolean conCodigo = codigo != null && !codigo.isBlank();
        return switch (entidadTipo) {
            case Tarea.ENTIDAD_PROSPECCION -> "prospeccion-detail/" + id;
            case "SOLICITUD_ALQUILER" -> "solicitud-detail/" + (conCodigo ? codigo : id);
            case "CAPTACION" -> conCodigo ? "captacion-detail/" + codigo : "captaciones";
            case "CONTRATO_ALQUILER" -> "comisiones";
            case "VISITA" -> "visitas?focus=" + id;
            case "CLIENTE_INTERESADO" -> "cliente-detail/" + id;
            case "PROPIETARIO" -> "owner-detail/" + id;
            case "OPORTUNIDAD" -> "oportunidad-detail/" + id;
            // REQUERIMIENTO incluido: su Resolver es la ficha del cliente, que
            // se resuelve fuera de aqui; sin cliente, el dashboard.
            default -> "dashboard";
        };
    }

    /** La marca de las solicitudes es su {@code fechaActualizacionEstado} como texto ISO. */
    private static LocalDate fechaDeMarca(String marca) {
        if (marca == null || marca.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(marca.substring(0, 10));
        } catch (RuntimeException ignorada) {
            return null;
        }
    }

    private static int dias(LocalDate base, LocalDate hoy) {
        if (base == null) {
            return 0;
        }
        long dias = ChronoUnit.DAYS.between(base, hoy);
        return dias < 0 ? 0 : (int) dias;
    }

    private static String nz(String texto) {
        return texto == null ? "" : texto;
    }
}
