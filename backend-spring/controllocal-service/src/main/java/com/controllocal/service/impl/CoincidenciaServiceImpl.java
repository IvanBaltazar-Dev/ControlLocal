package com.controllocal.service.impl;

import com.controllocal.domain.matching.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.*;
import com.controllocal.service.matching.FuenteCarteraMatching.Candidata;
import com.controllocal.service.soporte.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static com.controllocal.service.matching.ContratoMatching.*;

/** CAT-E20/D1–D4: un motor y continuación privada que sólo conserva referencias autorizadas. */
@Service
public class CoincidenciaServiceImpl implements CoincidenciaService {
    public static final String VERSION_REGLA="D1-D4-20261018-C3-R3";
    private final RequerimientoService demandas;
    private final FuenteCarteraMatching cartera;
    private final OfertasNetworkParaMatching network;
    private final RecomendacionesInternasParaMatching internas;
    private final ContextosService contextos;
    private final DisponibilidadContextualService disponibilidad;
    private final RecorridoMatchingRepository recorridos;
    private final ReferenciaRecorridoMatchingRepository referencias;
    private final CursorMatchingRepository cursores;
    private final Documentos documentos;
    private final MotorMatchingDeterminista motor;
    private final AlternativasPrecioMatching alternativas;
    private final Clock reloj;
    private final SecureRandom azar=new SecureRandom();

    @Autowired
    public CoincidenciaServiceImpl(RequerimientoService demandas,FuenteCarteraMatching cartera,
            OfertasNetworkParaMatching network,RecomendacionesInternasParaMatching internas,ContextosService contextos,DisponibilidadContextualService disponibilidad,
            RecorridoMatchingRepository recorridos,ReferenciaRecorridoMatchingRepository referencias,
            CursorMatchingRepository cursores,Documentos documentos,MotorMatchingDeterminista motor,AlternativasPrecioMatching alternativas) {
        this(demandas,cartera,network,internas,contextos,disponibilidad,recorridos,referencias,cursores,documentos,motor,alternativas,Clock.systemUTC());
    }
    CoincidenciaServiceImpl(RequerimientoService demandas,FuenteCarteraMatching cartera,
            OfertasNetworkParaMatching network,RecomendacionesInternasParaMatching internas,ContextosService contextos,DisponibilidadContextualService disponibilidad,
            RecorridoMatchingRepository recorridos,ReferenciaRecorridoMatchingRepository referencias,
            CursorMatchingRepository cursores,Documentos documentos,MotorMatchingDeterminista motor,AlternativasPrecioMatching alternativas,Clock reloj) {
        this.demandas=demandas;this.cartera=cartera;this.network=network;this.internas=internas;this.contextos=contextos;
        this.disponibilidad=disponibilidad;this.recorridos=recorridos;this.referencias=referencias;
        this.cursores=cursores;this.documentos=documentos;this.motor=motor;this.alternativas=alternativas;this.reloj=reloj;
    }
    private record Entrada(long demanda,long versionDemanda,String huellaCriterios,Candidata candidata,Capa capa,
                           Evaluacion evaluacion,String huellaProyeccion,AlternativasPrecioMatching.Alternativa alternativa) { }
    private record Calculo(String huellaOrigen,List<Entrada> entradas,Set<Capa> fallidas) { }
    private record Revalidacion(List<Entrada> entradas,Set<Capa> fallidas,Set<Integer> retiradas) { }
    /** Desenlaces posteriores a la revalidación: conservan sus retiradas técnicas correctas. */
    private static final class SinCoincidenciaEnElRecorrido extends NoEncontradoException {
        private SinCoincidenciaEnElRecorrido(){super("Coincidencia");}
    }
    private static final class RecorridoRequiereActualizacion extends ConflictoException {
        private RecorridoRequiereActualizacion(String motivo){super(motivo);}
    }

    @Override @Transactional
    public Coincidencias consultar(Consulta solicitud,Actor actor,ContextoSesion sesion) {
        exigirSesion(actor,sesion);Seleccion s=seleccion(solicitud);
        verificarAcceso(s.origen(),s.idOrigen(),s.versionOrigen(),actor);Instant corte=ahora();
        Calculo calculo=calcular(s,actor,corte,corte);
        var r=new RecorridoMatching();r.setId(UUID.randomUUID());r.setOrganizacionId(actor.idOrganizacion());
        r.setIdPersona(actor.idPersona());r.setIdRol(actor.idRolOperativo());r.setRol(actor.rolEfectivo());
        r.setHuellaContexto(sesion.identidad());r.setTipoOrigen(s.origen().name());r.setIdOrigen(s.idOrigen());
        r.setVersionOrigen(s.versionOrigen());r.setHuellaCriterios(calculo.huellaOrigen());r.setModalidad(s.modalidad().name());
        r.setCapas(csv(s.capas()));r.setCapasNoDisponibles(csv(calculo.fallidas()));r.setCategoria(s.categoria().name());
        r.setVersionRegla(VERSION_REGLA);r.setCorte(corte);r.setCreadoEn(corte);r.setVenceEn(corte.plus(Duration.ofMinutes(15)));r.setTamano(s.tamano());
        r.setContextoExploracion(s.exploracion()==null?null:documentos.objetoCanonico(s.exploracion()));
        if(descubrir(s)&&calculo.fallidas().isEmpty())r.setHuellaEscalones(huellaEscalones(escalones(calculo.entradas())));
        recorridos.saveAndFlush(r);List<ReferenciaRecorridoMatching> filas=new ArrayList<>();int ordinal=0;
        for(var e:calculo.entradas()) {
            var f=new ReferenciaRecorridoMatching();f.setOrganizacionId(actor.idOrganizacion());f.setIdRecorrido(r.getId());
            f.setOrdinal(ordinal++);f.setCapa(e.capa().name());f.setTipoRecurso(e.candidata().tipo());f.setReferenciaRecurso(e.candidata().referenciaInterna());
            f.setVersionRecurso(e.candidata().version());f.setHuellaProyeccion(e.huellaProyeccion());f.setIdDemanda(e.demanda());
            f.setVersionDemanda(e.versionDemanda());f.setHuellaCriterios(e.huellaCriterios());
            f.setReferenciaCandidato(e.evaluacion().referenciaEstable());filas.add(f);
        }
        referencias.saveAllAndFlush(filas);
        return entregar(r,0,actor,sesion,cursor(r,0));
    }
    @Override @Transactional
    public Coincidencias continuar(String cursor,Actor actor,ContextoSesion sesion) {
        exigirSesion(actor,sesion);var c=cargarCursor(cursor,actor);var r=cargarRecorrido(c,actor,sesion);
        return entregar(r,c.getPosicion(),actor,sesion,cursor(r,0));
    }
    @Override @Transactional(noRollbackFor={SinCoincidenciaEnElRecorrido.class,RecorridoRequiereActualizacion.class})
    public Coincidencia explicar(String cursor,String referenciaDemanda,String ref,Actor actor,ContextoSesion sesion) {
        exigirSesion(actor,sesion);
        if(referenciaDemanda==null||referenciaDemanda.isBlank()||ref==null||ref.isBlank())throw new ReglaNegocioException("Referencias de demanda y candidato requeridas.");
        var c=cargarCursor(cursor,actor);var r=cargarRecorrido(c,actor,sesion);
        if(!VERSION_REGLA.equals(r.getVersionRegla()))throw new ConflictoException("Cambió la versión del evaluador; actualice conservando los criterios y selecciones permitidos.");
        if(!ahora().isBefore(r.getVenceEn()))throw new ConflictoException("Continuidad vencida; actualice conservando los criterios permitidos.");
        var vigentes=revalidar(r,actor);
        if(vigentes==null)throw new RecorridoRequiereActualizacion("Cambió el contexto material; actualice la consulta.");
        exigirSesion(actor,sesion);
        List<Entrada> explicables=vigentes.entradas();
        if(descubrir(seleccion(r))) {
            if(!vigentes.retiradas().isEmpty())throw new RecorridoRequiereActualizacion(
                    "Cambió el universo que sustentaba los escalones; actualice conservando la Demanda original.");
            if(!vigentes.fallidas().isEmpty())throw new IllegalStateException("El descubrimiento requiere revalidar todas las capas solicitadas.");
            explicables=escalones(explicables);
            if(!Objects.equals(r.getHuellaEscalones(),huellaEscalones(explicables)))
                throw new RecorridoRequiereActualizacion("Cambiaron los escalones propuestos; actualice conservando la Demanda original.");
        }
        if(!ahora().isBefore(r.getVenceEn()))throw new RecorridoRequiereActualizacion("Continuidad vencida; actualice conservando los criterios permitidos.");
        var encontrada=explicables.stream().filter(e->refDemanda(actor,e.demanda()).equals(referenciaDemanda)
                &&e.evaluacion().referenciaEstable().equals(ref)).findFirst();
        if(encontrada.isPresent())return salida(encontrada.get());
        if(referencias.delRecorrido(actor.idOrganizacion(),r.getId()).stream().anyMatch(f->refDemanda(actor,f.getIdDemanda()).equals(referenciaDemanda)&&Objects.equals(f.getReferenciaCandidato(),ref)
                &&!vigentes.retiradas().contains(f.getOrdinal())&&vigentes.fallidas().contains(Capa.valueOf(f.getCapa()))))
            throw new IllegalStateException("La capa necesaria para explicar esta coincidencia no está disponible.");
        throw new SinCoincidenciaEnElRecorrido();
    }
    @Override @Transactional(readOnly=true)
    public void verificarSeleccionParaApertura(String consulta,String candidato,long encargo,
            Long demanda,Long versionDemanda,Actor actor) {
        Alcances.exigirOperador(actor);
        if(encargo<=0||candidato==null||!candidato.matches("[a-f0-9]{64}")
                ||(demanda==null)!=(versionDemanda==null))throw new ReglaNegocioException("Selección de origen incompleta.");
        // El cursor se resuelve por tenant; no se interpreta su contenido ni se reutiliza como permiso.
        var cursor=cargarCursor(consulta,actor);
        var recorrido=recorridos.findById(cursor.getIdRecorrido()).filter(r->r.getOrganizacionId()==actor.idOrganizacion()
                &&r.getIdPersona()==actor.idPersona()&&r.getIdRol()==actor.idRolOperativo()
                &&Objects.equals(r.getRol(),actor.rolEfectivo())).orElseThrow(()->new NoEncontradoException("Selección"));
        var filas=referencias.delRecorrido(actor.idOrganizacion(),recorrido.getId());
        var elegibles=filas.stream().filter(f->"L0".equals(f.getCapa())&&"ENCARGO".equals(f.getTipoRecurso())
                &&Long.toString(encargo).equals(f.getReferenciaRecurso())&&candidato.equals(f.getReferenciaCandidato())
                &&(demanda==null||f.getIdDemanda()==demanda&&f.getVersionDemanda()==versionDemanda)).toList();
        if(elegibles.isEmpty())throw new NoEncontradoException("Selección");
        var propias=demandas.demandasParaMatching(actor,ahora());
        var actual=cartera.encargo(encargo,actor,ahora());
        boolean vigente=elegibles.stream().anyMatch(f->propias.stream().anyMatch(d->d.idDemanda()==f.getIdDemanda()
                &&relacionPropia(Origen.DEMANDA,actor,actual.responsable(),d.responsableId())));
        if(!vigente)throw new NoEncontradoException("Selección");
        // No evaluar G/H/S/U/W ni extender el recorrido: sólo procedencia y autoridad actual.
    }
    private Coincidencias entregar(RecorridoMatching r,int posicion,Actor actor,ContextoSesion sesion,String consultaRef) {
        var s=seleccion(r);
        if(!VERSION_REGLA.equals(r.getVersionRegla()))
            return respuesta(r,EstadoResultado.CONTEXTO_CAMBIADO,List.of(),null,null,consultaRef,capas(r.getCapasNoDisponibles()));
        if(!ahora().isBefore(r.getVenceEn())) {
            verificarAcceso(s.origen(),s.idOrigen(),null,actor);
            return respuesta(r,EstadoResultado.CONTINUIDAD_VENCIDA,List.of(),null,null,consultaRef,capas(r.getCapasNoDisponibles()));
        }
        var actuales=revalidar(r,actor);
        if(actuales==null)return respuesta(r,EstadoResultado.CONTEXTO_CAMBIADO,List.of(),null,null,consultaRef,capas(r.getCapasNoDisponibles()));
        exigirSesion(actor,sesion);
        if(!ahora().isBefore(r.getVenceEn()))return respuesta(r,EstadoResultado.CONTINUIDAD_VENCIDA,List.of(),null,null,consultaRef,actuales.fallidas());
        List<Entrada> entregables=actuales.entradas();
        Map<String,Integer> ordinalPorPar=new HashMap<>();
        if(descubrir(s)) {
            if(!actuales.retiradas().isEmpty())
                return respuesta(r,EstadoResultado.CONTEXTO_CAMBIADO,List.of(),null,null,consultaRef,actuales.fallidas());
            if(!actuales.fallidas().isEmpty())return respuesta(r,EstadoResultado.DISPONIBLE,List.of(),null,null,consultaRef,actuales.fallidas());
            entregables=escalones(entregables);
            if(!Objects.equals(r.getHuellaEscalones(),huellaEscalones(entregables)))
                return respuesta(r,EstadoResultado.CONTEXTO_CAMBIADO,List.of(),null,null,consultaRef,actuales.fallidas());
            for(int i=0;i<entregables.size();i++){var e=entregables.get(i);ordinalPorPar.put(par(e.demanda(),e.candidata().tipo(),e.candidata().referenciaInterna()),i);}
        } else for(var f:referencias.delRecorrido(actor.idOrganizacion(),r.getId()))
                ordinalPorPar.put(par(f.getIdDemanda(),f.getTipoRecurso(),f.getReferenciaRecurso()),f.getOrdinal());
        if(!ahora().isBefore(r.getVenceEn()))return respuesta(r,EstadoResultado.CONTINUIDAD_VENCIDA,List.of(),null,null,consultaRef,actuales.fallidas());
        var restantes=entregables.stream().filter(e->ordinalPorPar.get(par(e.demanda(),e.candidata().tipo(),e.candidata().referenciaInterna()))>=posicion).toList();
        var pagina=restantes.stream().limit(r.getTamano()).toList();String siguiente=null;
        if(restantes.size()>pagina.size()) {
            var ultima=pagina.getLast();siguiente=cursor(r,ordinalPorPar.get(par(ultima.demanda(),ultima.candidata().tipo(),ultima.candidata().referenciaInterna()))+1);
        }
        LinkedHashMap<Long,List<Coincidencia>> grupos=new LinkedHashMap<>();Map<Long,Long> versiones=new HashMap<>();
        for(var e:pagina){grupos.computeIfAbsent(e.demanda(),k->new ArrayList<>()).add(salida(e));versiones.put(e.demanda(),e.versionDemanda());}
        List<Grupo> salida=new ArrayList<>();grupos.forEach((id,items)->salida.add(new Grupo(refDemanda(actor,id),versiones.get(id),items)));
        return respuesta(r,EstadoResultado.DISPONIBLE,salida,actuales.fallidas().isEmpty()?(long)entregables.size():null,siguiente,consultaRef,actuales.fallidas());
    }
    private Calculo calcular(Seleccion s,Actor actor,Instant corte,Instant ahora) {
        List<Entrada> resultados=new ArrayList<>();Set<Capa> fallidas=EnumSet.noneOf(Capa.class);
        var refsDemandas=s.origen()==Origen.DEMANDA||s.capas().contains(Capa.L0)
                ?demandas.demandasParaMatching(actor,corte):List.<RequerimientoService.ReferenciaDemanda>of();
        List<RequerimientoService.ReferenciaDemanda> aEvaluar;String origen;Candidata fija=null;
        if(s.origen()==Origen.DEMANDA) {
            var d=refsDemandas.stream().filter(v->v.idDemanda()==s.idOrigen()).findFirst().orElseThrow(()->new NoEncontradoException("Demanda"));
            FuenteCarteraMatching.exigirVersion(s.versionOrigen(),d.version());aEvaluar=List.of(d);
            var consulta=demandas.paraMatching(d.idDemanda(),d.version(),s.modalidad(),VERSION_REGLA,corte,actor);
            validarExploracion(s,consulta);
            origen=huellaConsultaActiva(consulta,disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,d.idDemanda(),actor),ahora);
        } else {
            fija=origen(s,actor,null,ahora,true);aEvaluar=refsDemandas;
            origen=huellaDocumento(Map.of("origen",s.origen().name(),"id",s.idOrigen(),"configuracion",fija.oferta().clave().configuracion()));
        }
        List<Candidata> locales=new ArrayList<>();
        if(s.capas().contains(Capa.L0)) {
            try {
                if(fija!=null)locales.add(fija);
                else locales.addAll(cartera.encargos(cartera.referencias(actor),actor,ahora));
            } catch(DataAccessException tecnico){fallidas.add(Capa.L0);}
        }
        for(var d:aEvaluar) {
            var q=demandas.paraMatching(d.idDemanda(),d.version(),s.modalidad(),VERSION_REGLA,corte,actor);
            var disponible=disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,d.idDemanda(),actor);
            String hash=huellaConsultaActiva(q,disponible,ahora);
            for(var o:locales) {
                if(!relacionPropia(s.origen(),actor,o.responsable(),d.responsableId()))continue;
                Candidata candidata=s.origen()!=Origen.DEMANDA&&s.origen()!=Origen.ENCARGO?origen(s,actor,operacion(q),ahora,true):o;
                agregar(resultados,d.idDemanda(),q,hash,candidata,Capa.L0,s.categoria(),disponible,null,ahora,s.exploracion());
            }
            if(s.capas().contains(Capa.L2)) {
                try {
                    for(var publicada:network.candidatas(d.idDemanda(),d.version(),actor,ahora)) {
                        var n=new Candidata("NETWORK",publicada.referencia().ofertaRef(),publicada.referencia().version(),null,publicada.oferta(),publicada.camposPermitidos());
                        agregar(resultados,d.idDemanda(),q,hash,n,Capa.L2,s.categoria(),disponible,publicada.referencia().huellaPublica(),ahora,s.exploracion());
                    }
                } catch(DataAccessException tecnico){fallidas.add(Capa.L2);}
            }
            if(s.origen()==Origen.DEMANDA&&s.capas().contains(Capa.L1)) {
                try {
                    for(var p:internas.ofertas(referenciaOrigen(s),actor,ahora)) {
                        var reducida=interna(p);
                        if(cubiertaPorL0(reducida,locales,s.origen(),actor,d.responsableId()))continue;
                        agregar(resultados,d.idDemanda(),q,hash,reducida,Capa.L1,s.categoria(),disponible,p.referencia().huella(),ahora,s.exploracion());
                    }
                } catch(DataAccessException tecnico){fallidas.add(Capa.L1);}
            }
        }
        if(s.origen()!=Origen.DEMANDA&&s.capas().contains(Capa.L1)) {
            Map<Long,RequerimientoService.ReferenciaDemanda> consultasLocales=new HashMap<>();
            if(s.capas().contains(Capa.L0)&&!fallidas.contains(Capa.L0))for(var d:aEvaluar)
                if(relacionPropia(s.origen(),actor,fija.responsable(),d.responsableId()))consultasLocales.put(d.idDemanda(),d);
            try {
                for(var d:internas.demandas(referenciaOrigen(s),s.modalidad(),VERSION_REGLA,corte,actor)) {
                    var plena=consultasLocales.get(d.id());
                    if(plena!=null) {
                        if(plena.version()!=d.version())throw new ConflictoException("La misma demanda cambió entre sus proyecciones; actualice la consulta.");
                        continue;
                    }
                    var q=d.consulta();var propia=origen(s,actor,operacion(q),ahora,true);
                    propia=conDemanda(propia,d);
                    agregar(resultados,d.id(),q,huellaConsultaActiva(q,d.disponibilidad(),ahora),propia,Capa.L1,s.categoria(),d.disponibilidad(),null,ahora,s.exploracion());
                }
            } catch(DataAccessException tecnico){fallidas.add(Capa.L1);}
        }
        if(fallidas.containsAll(s.capas()))throw new IllegalStateException("Fallaron técnicamente todas las capas solicitadas.");
        resultados.removeIf(e->fallidas.contains(e.capa()));
        return new Calculo(origen,ordenar(resultados,actor),Set.copyOf(fallidas));
    }
    /** null identifica cambio material del origen; cada candidato cambiado sale sólo de su entrega. */
    private Revalidacion revalidar(RecorridoMatching r,Actor actor) {
        Seleccion s=seleccion(r);Instant ahora=ahora();
        var origenActual=referenciaOrigenActual(s,actor);
        Set<Integer> retiradas=new HashSet<>(referencias.retiradas(actor.idOrganizacion(),r.getId()));
        var originales=referencias.delRecorrido(actor.idOrganizacion(),r.getId());
        var filas=originales.stream().filter(f->!retiradas.contains(f.getOrdinal())).toList();
        Set<Capa> fallidas=EnumSet.noneOf(Capa.class);fallidas.addAll(capas(r.getCapasNoDisponibles()));
        Map<Long,RequerimientoService.ReferenciaDemanda> vigentes=new HashMap<>();
        if(s.origen()==Origen.DEMANDA) {
            // La consulta propia es dependencia común de todas las capas; su fallo no permite entregar ninguna.
            for(var d:demandas.demandasParaMatching(actor,ahora))vigentes.put(d.idDemanda(),d);
        } else if(filas.stream().anyMatch(f->"L0".equals(f.getCapa()))) {
            try{for(var d:demandas.demandasParaMatching(actor,ahora))vigentes.put(d.idDemanda(),d);}
            catch(DataAccessException tecnico){fallidas.add(Capa.L0);vigentes.clear();}
        }
        if(s.origen()==Origen.DEMANDA) {
            var d=vigentes.get(s.idOrigen());if(d==null)throw new NoEncontradoException("Demanda");
            var consulta=demandas.paraMatching(d.idDemanda(),d.version(),s.modalidad(),r.getVersionRegla(),r.getCorte(),actor);
            if(!r.getHuellaCriterios().equals(huellaConsultaActiva(consulta,
                    disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,d.idDemanda(),actor),ahora)))return null;
            validarExploracion(s,consulta);
        }
        Map<Long,RecomendacionesInternasParaMatching.DemandaInterna> demandasInternas=new HashMap<>();
        Map<String,RecomendacionesInternasParaMatching.OfertaInterna> ofertasInternas=new HashMap<>();
        if(s.origen()!=Origen.DEMANDA&&s.capas().contains(Capa.L1)) {
            var ids=filas.stream().filter(f->"L1".equals(f.getCapa())).map(ReferenciaRecorridoMatching::getIdDemanda).distinct().toList();
            try{if(!ids.isEmpty())for(var d:internas.revalidarDemandas(origenActual,ids,s.modalidad(),r.getVersionRegla(),r.getCorte(),actor,ahora))demandasInternas.put(d.id(),d);}
            catch(DataAccessException tecnico){fallidas.add(Capa.L1);demandasInternas.clear();}
        } else if(s.origen()==Origen.DEMANDA&&s.capas().contains(Capa.L1)) {
            var refs=filas.stream().filter(f->"INTERNA".equals(f.getTipoRecurso()))
                    .map(f->new RecomendacionesInternasParaMatching.ReferenciaOferta(f.getReferenciaRecurso(),f.getVersionRecurso(),f.getHuellaProyeccion())).toList();
            try{if(!refs.isEmpty())for(var o:internas.revalidarOfertas(origenActual,refs,actor,ahora))ofertasInternas.put(o.referencia().referencia(),o);}
            catch(DataAccessException tecnico){fallidas.add(Capa.L1);ofertasInternas.clear();}
        }
        Map<String,Candidata> encargosActuales=new HashMap<>();
        var idsEncargos=filas.stream().filter(f->"ENCARGO".equals(f.getTipoRecurso()))
                .map(f->Long.valueOf(f.getReferenciaRecurso())).distinct().toList();
        try{if(!idsEncargos.isEmpty())for(var c:cartera.encargos(idsEncargos,actor,ahora))encargosActuales.put(c.referenciaInterna(),c);}
        catch(DataAccessException tecnico){
            // En la inversa L1, el Encargo es el origen común; no una fuente candidata prescindible.
            if(s.origen()==Origen.ENCARGO)throw tecnico;
            fallidas.add(Capa.L0);encargosActuales.clear();
        }
        Map<Long,ContratoMatching.Consulta> consultas=new HashMap<>();Map<Long,ConfirmacionDisponibilidad> disponibles=new HashMap<>();
        Map<String,Candidata> locales=new HashMap<>();Map<String,OfertasNetworkParaMatching.Candidata> publicadas=new HashMap<>();
        try{for(var grupo:filas.stream().filter(f->"NETWORK".equals(f.getTipoRecurso())).collect(Collectors.groupingBy(ReferenciaRecorridoMatching::getIdDemanda)).entrySet()) {
                var d=vigentes.get(grupo.getKey());if(d==null)continue;
                var pedidas=grupo.getValue().stream().map(f->new OfertasNetworkParaMatching.Referencia(f.getReferenciaRecurso(),f.getVersionRecurso(),f.getHuellaProyeccion())).toList();
                for(var p:network.revalidar(d.idDemanda(),d.version(),pedidas,actor,ahora))publicadas.put(d.idDemanda()+"|"+p.referencia().ofertaRef(),p);
            }}catch(DataAccessException tecnico){fallidas.add(Capa.L2);publicadas.clear();}
        List<Entrada> salida=new ArrayList<>();
        for(var f:filas) {
            Capa capa=Capa.valueOf(f.getCapa());
            if(fallidas.contains(capa))continue;
            var propia=vigentes.get(f.getIdDemanda());
            var interna=capa==Capa.L1&&s.origen()!=Origen.DEMANDA?demandasInternas.get(f.getIdDemanda()):null;
            if(interna==null&&propia==null)continue;
            if(capa==Capa.L1&&s.origen()!=Origen.DEMANDA&&interna==null)continue;
            try {
                var q=interna!=null?interna.consulta():consultas.computeIfAbsent(propia.idDemanda(),k->demandas.paraMatching(k,propia.version(),s.modalidad(),r.getVersionRegla(),r.getCorte(),actor));
                var disponible=interna!=null?interna.disponibilidad():disponibles.computeIfAbsent(propia.idDemanda(),k->disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,k,actor));
                String hash=huellaConsultaActiva(q,disponible,ahora);
                if(!f.getHuellaCriterios().equals(hash)) {
                    if(s.origen()==Origen.DEMANDA)return null;
                    referencias.retirar(actor.idOrganizacion(),r.getId(),f.getOrdinal(),ahora);retiradas.add(f.getOrdinal());continue;
                }
                Candidata o;String hashPublica=null;
                if("NETWORK".equals(f.getTipoRecurso())) {
                    var p=publicadas.get(f.getIdDemanda()+"|"+f.getReferenciaRecurso());if(p==null)continue;
                    o=new Candidata("NETWORK",p.referencia().ofertaRef(),p.referencia().version(),null,p.oferta(),p.camposPermitidos());hashPublica=p.referencia().huellaPublica();
                } else if("INTERNA".equals(f.getTipoRecurso())) {
                    var p=ofertasInternas.get(f.getReferenciaRecurso());if(p==null)continue;
                    o=interna(p);hashPublica=p.referencia().huella();
                } else {
                    String key=f.getTipoRecurso()+":"+f.getReferenciaRecurso()+":"+operacion(q);o=locales.get(key);
                    if(o==null) {
                        o="ENCARGO".equals(f.getTipoRecurso())?encargosActuales.get(f.getReferenciaRecurso())
                                :cartera.preliminar(Origen.valueOf(f.getTipoRecurso()),Long.parseLong(f.getReferenciaRecurso()),cartera.versionAutorizada(s.origen(),s.idOrigen(),actor),actor,operacion(q),ahora);
                        if(o==null)continue;
                        locales.put(key,o);
                    }
                    if(capa==Capa.L0&&!relacionPropia(s.origen(),actor,o.responsable(),propia.responsableId()))continue;
                    if(interna!=null)o=conDemanda(o,interna);
                }
                String huella=hashPublica==null?huellaOferta(q,o.oferta()):hashPublica;
                if(!f.getHuellaProyeccion().equals(huella)) {
                    if(s.origen()!=Origen.DEMANDA)return null;
                    referencias.retirar(actor.idOrganizacion(),r.getId(),f.getOrdinal(),ahora);retiradas.add(f.getOrdinal());continue;
                }
                agregar(salida,f.getIdDemanda(),q,hash,o,capa,s.categoria(),disponible,hashPublica,ahora,s.exploracion());
            } catch(AccesoNoAutorizadoException|NoEncontradoException revocado){/* sin pista */}
            catch(DataAccessException tecnico){fallidas.add(capa);salida.removeIf(e->e.capa()==capa);}
        }
        if(fallidas.containsAll(s.capas()))throw new IllegalStateException("Fallaron técnicamente todas las capas solicitadas.");
        // Otra revalidación pudo confirmar una retirada durante nuestras lecturas; no la deshace A→B→A.
        retiradas.addAll(referencias.retiradas(actor.idOrganizacion(),r.getId()));
        Map<String,Integer> ordinales=new HashMap<>();
        for(var f:originales)ordinales.put(par(f.getIdDemanda(),f.getTipoRecurso(),f.getReferenciaRecurso()),f.getOrdinal());
        salida.removeIf(e->retiradas.contains(ordinales.get(par(e.demanda(),e.candidata().tipo(),e.candidata().referenciaInterna()))));
        return new Revalidacion(List.copyOf(salida),Set.copyOf(fallidas),Set.copyOf(retiradas));
    }
    /** D4-09/13: el permiso pleno ya acreditado evita reevaluar una copia reducida del mismo objeto. */
    private static boolean cubiertaPorL0(Candidata reducida,List<Candidata> locales,Origen origen,Actor actor,long responsableDemanda) {
        for(var local:locales) {
            if(!relacionPropia(origen,actor,local.responsable(),responsableDemanda)||!local.oferta().clave().equals(reducida.oferta().clave()))continue;
            if(local.version()!=reducida.version()||!local.oferta().referenciaEstable().equals(reducida.oferta().referenciaEstable()))
                throw new ConflictoException("La misma oferta cambió entre sus proyecciones; actualice la consulta.");
            return true;
        }
        return false;
    }
    private void agregar(List<Entrada> salida,long id,ContratoMatching.Consulta q,String hash,Candidata c,Capa capa,
                         Categoria categoria,ConfirmacionDisponibilidad demanda,String hashPublica,Instant elegibilidadEn,ExploracionPrecio exploracion) {
        var o=c.oferta();var g=MotorMatchingDeterminista.y(o.habilitacion(),motor.disponibilidad(demanda,elegibilidadEn).conclusion());
        var oferta=new Oferta(o.referenciaEstable(),o.clave(),o.version(),o.accesoAutorizado(),g,o.disponibilidad(),o.datos());
        var e=motor.evaluar(q,oferta,elegibilidadEn);
        if(exploracion==null) {
            if(e.categoria()==categoria)salida.add(new Entrada(id,q.version(),hash,c,capa,e,hashPublica==null?huellaOferta(q,o):hashPublica,null));
        } else alternativas.evaluar(q,oferta,e,exploracion.limite(),exploracion.moneda()).ifPresent(a->
                salida.add(new Entrada(id,q.version(),hash,c,capa,e,hashPublica==null?huellaOferta(q,o):hashPublica,a)));
    }
    private void validarExploracion(Seleccion s,ContratoMatching.Consulta consulta) {
        if(s.exploracion()!=null)alternativas.validarExploracion(consulta,s.exploracion().limite(),s.exploracion().moneda());
    }
    /** El orden precio/pasos sólo selecciona propuestas; el listado exploratorio conserva S/U/ref. */
    private List<Entrada> escalones(List<Entrada> entradas) {
        Map<String,Entrada> porOferta=new HashMap<>();
        for(var e:entradas) {
            if(e.alternativa()==null)throw new IllegalStateException("La propuesta no conserva su evaluación de alternativa.");
            porOferta.put(e.evaluacion().referenciaEstable(),e);
        }
        return alternativas.escalones(entradas.stream().map(Entrada::alternativa).toList()).stream()
                .map(a->Objects.requireNonNull(porOferta.get(a.original().referenciaEstable()),"El escalón debe proceder del universo autorizado.")).toList();
    }
    private String huellaEscalones(List<Entrada> pasos) {
        List<Map<String,Object>> referencias=new ArrayList<>();
        for(var e:pasos) {
            var a=e.alternativa();Map<String,Object> datos=new LinkedHashMap<>();
            datos.put("demanda",e.demanda());datos.put("oferta",e.evaluacion().referenciaEstable());
            datos.put("precio",a.precio().stripTrailingZeros());datos.put("maximoOriginal",a.maximoOriginal().stripTrailingZeros());
            datos.put("moneda",a.moneda());datos.put("concepto",a.concepto());datos.put("periodo",a.periodo());
            datos.put("s",e.evaluacion().s());datos.put("u",e.evaluacion().u());datos.put("w",e.evaluacion().w());referencias.add(datos);
        }
        return huellaDocumento(Map.of("escalones",referencias));
    }
    private List<Entrada> ordenar(List<Entrada> entradas,Actor actor) {
        List<Entrada> salida=new ArrayList<>();var grupos=entradas.stream().collect(Collectors.groupingBy(Entrada::demanda));
        for(long id:grupos.keySet().stream().sorted(Comparator.comparing(k->refDemanda(actor,k))).toList()) {
            var porRef=new HashMap<String,Entrada>();
            for(var e:grupos.get(id)) {var anterior=porRef.putIfAbsent(e.evaluacion().referenciaEstable(),e);if(anterior!=null&&!anterior.equals(e))throw new IllegalStateException("Oferta duplicada con proyecciones incompatibles.");}
            for(var e:motor.ordenar(porRef.values().stream().map(Entrada::evaluacion).toList()))salida.add(porRef.get(e.referenciaEstable()));
        }
        return List.copyOf(salida);
    }
    private Candidata origen(Seleccion s,Actor actor,String operacion,Instant ahora,boolean estricta) {
        if(s.origen()==Origen.ENCARGO){var c=cartera.encargo(s.idOrigen(),actor,ahora);if(estricta)FuenteCarteraMatching.exigirVersion(s.versionOrigen(),c.version());return c;}
        return cartera.preliminar(s.origen(),s.idOrigen(),estricta?s.versionOrigen():cartera.versionAutorizada(s.origen(),s.idOrigen(),actor),actor,operacion,ahora);
    }
    private static RecomendacionesInternasParaMatching.OrigenReferencia referenciaOrigen(Seleccion s) {
        return new RecomendacionesInternasParaMatching.OrigenReferencia(s.origen(),s.idOrigen(),s.versionOrigen(),s.tipoVersion());
    }
    private static boolean relacionPropia(Origen origen,Actor actor,Long responsableOferta,long responsableDemanda) {
        // RF070: usar conocimiento físico autorizado no atribuye al agente el Encargo de otro.
        if(origen!=Origen.DEMANDA&&origen!=Origen.ENCARGO&&actor.esAgente())return actor.idRolOperativo()==responsableDemanda;
        return Objects.equals(responsableOferta,responsableDemanda);
    }
    private RecomendacionesInternasParaMatching.OrigenReferencia referenciaOrigenActual(Seleccion s,Actor actor) {
        long version=s.origen()==Origen.DEMANDA?demandas.contextoDisponibilidad(s.idOrigen(),actor,false).version()
                :cartera.versionAutorizada(s.origen(),s.idOrigen(),actor);
        return new RecomendacionesInternasParaMatching.OrigenReferencia(s.origen(),s.idOrigen(),version,s.tipoVersion());
    }
    private static Candidata interna(RecomendacionesInternasParaMatching.OfertaInterna p) {
        var base=new Candidata("INTERNA",p.referencia().referencia(),p.referencia().version(),null,p.oferta(),p.camposV1());
        return conResponsable(base,p.responsable(),"ENCARGO");
    }
    private static Candidata conResponsable(Candidata base,RecomendacionesInternasParaMatching.ResponsableVisible responsable,String recurso) {
        Map<String,Object> datos=new LinkedHashMap<>(base.datosPermitidos());
        datos.put("responsableCoincidente",responsable);datos.put("recursoResponsable",recurso);
        return new Candidata(base.tipo(),base.referenciaInterna(),base.version(),base.responsable(),base.oferta(),datos);
    }
    private static Candidata conDemanda(Candidata base,RecomendacionesInternasParaMatching.DemandaInterna demanda) {
        // La oferta es el origen propio; el recurso coincidente es la demanda física reducida.
        var limpia=new Candidata(base.tipo(),base.referenciaInterna(),base.version(),base.responsable(),base.oferta(),Map.of());
        return conResponsable(limpia,demanda.responsable(),"DEMANDA");
    }
    @Override @Transactional(readOnly=true)
    public void verificarAcceso(Origen origen,long id,Long version,Actor actor) {
        Alcances.exigirOperador(actor);
        long actual=origen==Origen.DEMANDA?demandas.contextoDisponibilidad(id,actor,false).version():cartera.versionAutorizada(origen,id,actor);
        if(version!=null)FuenteCarteraMatching.exigirVersion(version,actual);
    }
    @Override @Transactional(readOnly=true)
    public List<Aptitud> aptitudesPropias(Actor actor) {
        Alcances.exigirOperador(actor);Instant corte=ahora();List<Aptitud> salida=new ArrayList<>();List<Candidata> locales=new ArrayList<>();
        locales.addAll(cartera.encargos(cartera.referencias(actor),actor,corte));
        for(var d:demandas.demandasParaMatching(actor,corte)) {
            var q=demandas.paraMatching(d.idDemanda(),d.version(),Modalidad.COMERCIAL,VERSION_REGLA,corte,actor);
            var disponible=disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,d.idDemanda(),actor);
            for(var o:locales) {
                if(!Objects.equals(o.responsable(),d.responsableId()))continue;List<Entrada> una=new ArrayList<>();
                for(var categoria:List.of(Categoria.COMERCIAL_CONFIRMADA,Categoria.OBLIGATORIAS_PENDIENTES,Categoria.HABILITACION_PENDIENTE))agregar(una,d.idDemanda(),q,huellaConsulta(q),o,Capa.L0,categoria,disponible,null,corte,null);
                if(!una.isEmpty())salida.add(new Aptitud(d.idDemanda(),d.version(),Long.parseLong(o.referenciaInterna()),o.version(),Objects.toString(o.datosPermitidos().get("codigoEncargo"),null),salida(una.getFirst())));
            }
        }
        return List.copyOf(salida);
    }
    private Coincidencia salida(Entrada e) {
        var r=e.evaluacion();var a=e.alternativa();return new Coincidencia(r.referenciaEstable(),r.versionOferta(),e.capa(),r.categoria(),r.g(),r.h(),r.s(),r.u(),r.w(),
                r.criterios().stream().map(c->new Razon(c.referencia(),c.clave(),c.exigencia(),c.peso(),c.resultado().conclusion(),c.resultado().causa())).toList(),e.candidata().datosPermitidos(),r.coberturaCriterios(),
                a==null?null:new AlternativaPrecio(a.precio(),a.maximoOriginal(),a.moneda(),a.concepto(),a.periodo(),a.excesoAbsoluto(),a.excesoPorcentaje()));
    }
    private String huellaConsulta(ContratoMatching.Consulta q){return huellaDocumento(Map.of("modalidad",q.modalidad(),"regla",q.versionRegla(),"criterios",q.criterios(),"coberturaCriterios",q.coberturaCriterios()));}
    private String huellaConsultaActiva(ContratoMatching.Consulta q,ConfirmacionDisponibilidad confirmacion,Instant elegibilidadEn) {
        return huellaDocumento(Map.of("criterios",huellaConsulta(q),"disponibilidad",motor.disponibilidad(confirmacion,elegibilidadEn)));
    }
    private String huellaOferta(ContratoMatching.Consulta q,Oferta o) {
        Set<String> claves=q.criterios().stream().flatMap(c->c.alternativas().stream()).map(Condicion::claveDato).collect(Collectors.toSet());
        return huellaDocumento(Map.of("configuracion",o.clave(),"g",o.habilitacion(),"datos",o.datos().stream().filter(d->claves.contains(d.clave())).toList()));
    }
    private String huellaDocumento(Object o){return Idempotencia.huellaExacta(documentos.objetoCanonico(o));}
    private static String operacion(ContratoMatching.Consulta q){return q.criterios().stream().flatMap(c->c.alternativas().stream()).map(c->c.contexto().operacion()).filter(Objects::nonNull).findFirst().orElse(null);}
    private static TipoVersion tipoVersion(Origen origen){return switch(origen){case DEMANDA->TipoVersion.DEMANDA;case ENCARGO->TipoVersion.ENCARGO;case CAPTURA->TipoVersion.CAPTURA;case PROSPECCION,FISICO->TipoVersion.FISICA;};}
    private static Seleccion seleccion(Consulta q) {
        if(q==null||q.origen()==null||q.idOrigen()==null||q.idOrigen()<=0||q.versionOrigen()==null||q.versionOrigen()<0||q.modalidad()==null||q.categoria()==null||q.capas().isEmpty())throw new ReglaNegocioException("Origen, versión observada, modalidad, capas y categoría son explícitos.");
        if(q.tipoVersion()!=tipoVersion(q.origen()))throw new ReglaNegocioException("El tipo de versión no corresponde al origen.");
        if(q.origen()!=Origen.DEMANDA&&q.capas().contains(Capa.L2))throw new ReglaNegocioException("L2 sólo admite Demanda propia hacia ofertas participantes.");
        if(Set.of(Origen.CAPTURA,Origen.PROSPECCION,Origen.FISICO).contains(q.origen())&&q.modalidad()!=Modalidad.PRELIMINAR)throw new ReglaNegocioException("El registro físico sin Encargo sólo admite evaluación preliminar.");
        var exploracion=normalizarExploracion(q.exploracion());
        if(exploracion!=null&&(q.origen()!=Origen.DEMANDA||q.modalidad()!=Modalidad.COMERCIAL||q.categoria()!=Categoria.INCOMPATIBLE))
            throw new ReglaNegocioException("Las alternativas requieren Demanda propia, modalidad comercial y su categoría original incompatible.");
        int tamano=q.tamano()==null?20:q.tamano();if(tamano<1||tamano>50)throw new ReglaNegocioException("Tamaño de página entre uno y cincuenta.");
        return new Seleccion(q.origen(),q.idOrigen(),q.versionOrigen(),q.tipoVersion(),q.modalidad(),q.capas(),q.categoria(),tamano,exploracion);
    }
    private Seleccion seleccion(RecorridoMatching r){var o=Origen.valueOf(r.getTipoOrigen());return new Seleccion(o,r.getIdOrigen(),r.getVersionOrigen(),tipoVersion(o),Modalidad.valueOf(r.getModalidad()),capas(r.getCapas()),Categoria.valueOf(r.getCategoria()),r.getTamano(),exploracionDe(r));}
    private static ExploracionPrecio normalizarExploracion(ExploracionPrecio e) {
        if(e==null)return null;
        if(e.modo()==null)throw new ReglaNegocioException("El modo de exploración debe ser explícito.");
        if(e.modo()==ModoExploracion.DESCUBRIR) {
            if(e.limite()!=null||e.moneda()!=null)throw new ReglaNegocioException("Descubrir propone escalones reales sin un límite supuesto.");
            return e;
        }
        if(e.limite()==null||e.moneda()==null)throw new ReglaNegocioException("Explorar requiere importe límite y moneda explícitos.");
        return new ExploracionPrecio(e.modo(),e.limite().stripTrailingZeros(),CondicionesEconomicas.moneda(e.moneda(),"del límite de exploración"));
    }
    private ExploracionPrecio exploracionDe(RecorridoMatching r) {
        if(r.getContextoExploracion()==null)return null;
        try{return normalizarExploracion(documentos.comoObjeto(r.getContextoExploracion(),ExploracionPrecio.class));}
        catch(RuntimeException invalido){throw new IllegalStateException("El recorrido no conserva su contexto explícito de exploración.",invalido);}
    }
    private static boolean descubrir(Seleccion s){return s.exploracion()!=null&&s.exploracion().modo()==ModoExploracion.DESCUBRIR;}
    private Coincidencias respuesta(RecorridoMatching r,EstadoResultado estado,List<Grupo> grupos,Long total,String siguiente,String consultaRef,Set<Capa> fallidas) {
        return new Coincidencias(estado,seleccion(r),r.getCorte(),r.getVersionRegla(),consultaRef,grupos,total,fallidas.isEmpty()?Cobertura.COMPLETA:Cobertura.LIMITADA,fallidas,siguiente);
    }
    private void exigirSesion(Actor actor,ContextoSesion sesion) {
        Alcances.exigirOperador(actor);if(sesion==null)throw new AccesoNoAutorizadoException();
        var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
        if(!sesion.equals(ContextoSesion.de(actual))||actual.restricciones().debeCambiarContrasena()||actual.restricciones().debeEnrolarMfa())throw new AccesoNoAutorizadoException();
    }
    private CursorMatching cargarCursor(String ref,Actor actor){if(ref==null||!ref.matches("[A-Za-z0-9_-]{43}"))throw new NoEncontradoException("Continuidad");return cursores.porReferencia(actor.idOrganizacion(),ref).orElseThrow(()->new NoEncontradoException("Continuidad"));}
    private RecorridoMatching cargarRecorrido(CursorMatching c,Actor actor,ContextoSesion sesion){return recorridos.delActor(actor.idOrganizacion(),c.getIdRecorrido(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),sesion.identidad()).orElseThrow(()->new NoEncontradoException("Continuidad"));}
    private String cursor(RecorridoMatching r,int posicion) {
        var existente=cursores.porPosicion(r.getOrganizacionId(),r.getId(),posicion);if(existente.isPresent())return existente.get().getReferencia();
        byte[] bytes=new byte[32];azar.nextBytes(bytes);String ref=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        cursores.insertar(ref,r.getOrganizacionId(),r.getId(),posicion);
        return cursores.porPosicion(r.getOrganizacionId(),r.getId(),posicion).orElseThrow(()->new IllegalStateException("Cursor no persistido.")).getReferencia();
    }
    private static String csv(Set<Capa> capas){return capas.stream().sorted().map(Enum::name).collect(Collectors.joining(","));}
    private static Set<Capa> capas(String csv){return csv.isEmpty()?Set.of():Arrays.stream(csv.split(",")).map(Capa::valueOf).collect(Collectors.toUnmodifiableSet());}
    private static String refDemanda(Actor actor,long id){return Idempotencia.huellaExacta(actor.idOrganizacion()+"|DEMANDA|"+id);}
    private static String par(long demanda,String tipo,String ref){return demanda+"|"+tipo+"|"+ref;}
    private Instant ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS);}
}

