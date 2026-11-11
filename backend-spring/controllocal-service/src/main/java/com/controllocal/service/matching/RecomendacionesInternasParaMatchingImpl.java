package com.controllocal.service.matching;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.CoincidenciaService.Origen;
import com.controllocal.service.CoincidenciaService.TipoVersion;
import com.controllocal.service.captura.MotorDeCaptura.MetadatoRespuesta;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.demanda.ProyeccionComercialDemanda;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.disponibilidad.SelectorConfirmacionVigente;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.network.CamposProyeccionComercial;
import com.controllocal.service.network.PoliticaProyeccionNetworkV1;
import com.controllocal.service.network.ProyeccionV1ParaMatching;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import static com.controllocal.service.matching.ContratoMatching.*;

/** L1 contextual aprobado29/10: no cambia permisos de cartera ni exige participación L2. */
@Service
@Transactional(readOnly=true)
public class RecomendacionesInternasParaMatchingImpl implements RecomendacionesInternasParaMatching {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private static final Set<String> FISICOS=ProyeccionComercialDemanda.SELECTORES_FISICOS.stream()
            .filter(s->s.startsWith("PROPIEDAD:")).map(s->s.substring("PROPIEDAD:".length()))
            .filter(s->!Set.of("tipo_propiedad","distrito").contains(s)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private final ProyeccionOfertaL1Repository ofertas;
    private final ProyeccionDemandaCompartibleRepository proyeccionesDemanda;
    private final RequerimientoClienteRepository contextoActor;
    private final RequerimientoService demandas;
    private final FuenteCarteraMatching propia;
    private final EncargoService encargos;
    private final ConfirmacionDisponibilidadContextualRepository confirmaciones;
    private final SuficienciaRegistrada suficiencia;
    private final GobiernoCatalogoCapturaService catalogos;
    private final Documentos documentos;
    private final MotorMatchingDeterminista motor=new MotorMatchingDeterminista();
    public RecomendacionesInternasParaMatchingImpl(ProyeccionOfertaL1Repository ofertas,ProyeccionDemandaCompartibleRepository proyeccionesDemanda,
            RequerimientoClienteRepository contextoActor,RequerimientoService demandas,FuenteCarteraMatching propia,EncargoService encargos,
            ConfirmacionDisponibilidadContextualRepository confirmaciones,SuficienciaRegistrada suficiencia,
            GobiernoCatalogoCapturaService catalogos,Documentos documentos) {
        this.ofertas=ofertas;this.proyeccionesDemanda=proyeccionesDemanda;this.contextoActor=contextoActor;this.demandas=demandas;
        this.propia=propia;this.encargos=encargos;this.confirmaciones=confirmaciones;this.suficiencia=suficiencia;this.catalogos=catalogos;this.documentos=documentos;
    }
    @Override public List<OfertaInterna> ofertas(OrigenReferencia origen,Actor actor,Instant ahora) {
        admitirOrigen(origen,actor,ahora,true);
        var resultado=proyectarOfertas(ofertas.candidatas(actor.idOrganizacion(),dia(ahora)),actor,ahora);
        admitirOrigen(origen,actor,ahora,true);return resultado;
    }
    @Override public List<OfertaInterna> revalidarOfertas(OrigenReferencia origen,List<ReferenciaOferta> referencias,Actor actor,Instant ahora) {
        admitirOrigen(origen,actor,ahora,true);Objects.requireNonNull(referencias);
        var ids=referencias.stream().map(r->idOferta(r.referencia())).distinct().toList();
        var resultado=proyectarOfertas(ofertas.actuales(actor.idOrganizacion(),ids,dia(ahora)),actor,ahora);
        admitirOrigen(origen,actor,ahora,true);return resultado;
    }
    private List<OfertaInterna> proyectarOfertas(List<ProyeccionOfertaL1Repository.Base> bases,Actor actor,Instant ahora) {
        List<OfertaInterna> salida=new ArrayList<>();
        for(var b:bases) {
            if(actor.esAgente()&&b.responsable()==actor.idRolOperativo())continue;
            try{oferta(b,actor,ahora).ifPresent(salida::add);}
            catch(AccesoNoAutorizadoException|NoEncontradoException revocado){/* no pista de la referencia retirada */}
        }
        return List.copyOf(salida);
    }
    private Optional<OfertaInterna> oferta(ProyeccionOfertaL1Repository.Base inicial,Actor actor,Instant ahora) {
        var b=inicial;
        for(int intento=0;intento<2;intento++) {
            var aptitud=encargos.habilitacionParaRecomendacionInterna(b.id(),dia(ahora),actor);
            if(aptitud.version()!=b.version()||aptitud.versionFisica()!=b.versionFisica()||aptitud.responsable()!=b.responsable()
                    ||!Objects.equals(aptitud.composicion(),b.composicion())) {b=ofertaActual(b.id(),actor,ahora);continue;}
            String op=OperacionInmobiliaria.desde(b.operacion()).name();
            String config=b.composicion()==null?DisponibilidadContextualService.contextoUnidad(b.propiedad()):DisponibilidadContextualService.contextoComposicion(b.composicion());
            var confirmacion=disponibilidad(actor.idOrganizacion(),"ENCARGO",b.id(),op,config);
            var disponible=motor.disponibilidad(confirmacion,ahora);
            if(disponible.conclusion()==Conclusion.INCUMPLE)return Optional.empty();
            var valores=new TreeMap<String,Object>();
            CamposProyeccionComercial.poner(valores,PoliticaProyeccionNetworkV1.CAMPOS,OPERACION,CamposProyeccionComercial.campo(op,null,null,null,null));
            if(b.composicion()==null) {
                if(b.tipo()!=null)CamposProyeccionComercial.poner(valores,PoliticaProyeccionNetworkV1.CAMPOS,TIPO_PROPIEDAD,CamposProyeccionComercial.campo(b.tipo(),null,null,null,null));
                if(b.distrito()!=null)CamposProyeccionComercial.poner(valores,PoliticaProyeccionNetworkV1.CAMPOS,DISTRITO,CamposProyeccionComercial.campo(Long.toString(b.distrito()),null,null,null,null));
                fisicos(valores,b,actor,op);
            }
            // Sólo el pacto del mismo snapshot habilitado acredita el precio/entrega de esta configuración.
            if(aptitud.habilitado()) {
                ofertas.precio(actor.idOrganizacion(),b.id(),b.version()).filter(p->p.importe()!=null&&p.moneda()!=null).ifPresent(p->
                        CamposProyeccionComercial.poner(valores,PoliticaProyeccionNetworkV1.CAMPOS,"ENCARGO:precio_pedido",CamposProyeccionComercial.campo(p.importe(),
                                "VENTA".equals(op)?"PRECIO_VENTA":"PRECIO_ALQUILER",p.unidad(),p.moneda(),p.periodo())));
                for(var atributo:ofertas.booleanos(actor.idOrganizacion(),b.id()))if(atributo.valor()!=null)
                    CamposProyeccionComercial.poner(valores,PoliticaProyeccionNetworkV1.CAMPOS,"ENCARGO:"+atributo.clave(),CamposProyeccionComercial.campo(atributo.valor(),null,null,null,null));
            }
            if(disponible.conclusion()==Conclusion.CUMPLE)
                CamposProyeccionComercial.poner(valores,PoliticaProyeccionNetworkV1.CAMPOS,"ENCARGO:disponibilidad",CamposProyeccionComercial.campo(true,null,null,null,null));
            var actual=ofertaActual(b.id(),actor,ahora);
            if(!actual.equals(b)){b=actual;continue;}
            var g=aptitud.habilitado()?disponible.conclusion():Conclusion.INCUMPLE;
            var responsable=new ResponsableVisible("AGENTE:"+b.responsable(),b.profesional());
            String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("campos",valores,"g",g,"configuracion",config,"responsable",responsable)));
            String ref=Idempotencia.huellaExacta(actor.idOrganizacion()+"|ENCARGO|"+b.id()+"|"+config);
            var oferta=new Oferta(ref,new ClaveCandidato(Long.toString(actor.idOrganizacion()),"ENCARGO:"+b.id(),config),b.version(),true,g,confirmacion,
                    ProyeccionV1ParaMatching.datos(valores,config,ahora));
            return Optional.of(new OfertaInterna(new ReferenciaOferta("encargo:"+b.id(),b.version(),huella),oferta,valores,responsable));
        }
        throw new ConflictoException("La oferta cambió durante la proyección L1; vuelve a comprobarla.");
    }
    private void fisicos(Map<String,Object> salida,ProyeccionOfertaL1Repository.Base base,Actor actor,String op) {
        var filas=ofertas.fisicos(actor.idOrganizacion(),base.propiedad(),FISICOS);
        Map<String,Object> conocidos=new LinkedHashMap<>();Map<String,MetadatoRespuesta> metadatos=new LinkedHashMap<>();
        for(var f:filas) {
            if(f.valor()!=null)conocidos.put(f.clave(),f.valor());
            if(f.tieneMetadato())metadatos.put(f.clave(),new MetadatoRespuesta(f.estado()==null?null:EstadoDato.valueOf(f.estado()),null,f.concepto(),f.referenciaCatalogo(),null,null,false));
        }
        var contexto=new SuficienciaInmueble.Contexto(base.tipo(),null,null,op,Map.of(),Set.of(),base.jurisdiccion());
        var datos=suficiencia.validarDatos(actor.idOrganizacion(),contexto,suficiencia.datos(conocidos,metadatos));
        for(var f:filas) {
            var dato=datos.get(f.clave());if(dato==null||dato.estado()!=EstadoDato.CONOCIDO||dato.valor()==null)continue;
            boolean area=Set.of("metraje_total","metraje_construido","area_terreno").contains(f.clave());
            if(area&&!Set.of("m2","m²").contains(Objects.toString(f.unidad(),"")))continue;
            if("zonificacion".equals(f.clave())) {
                if(!f.documentado())continue;
                var territorio=catalogos.opcionesVigentes(actor.idOrganizacion(),f.clave(),f.jurisdiccion());
                if(territorio.motivo()!=null||!Objects.equals(territorio.referenciaCatalogo(),dato.referenciaCatalogo())
                        ||territorio.jurisdiccion()==null||territorio.versionFuente()==null)continue;
                CamposProyeccionComercial.poner(salida,PoliticaProyeccionNetworkV1.CAMPOS,"PROPIEDAD:"+f.clave(),CamposProyeccionComercial.territorial(dato.valor(),dato.concepto(),f.unidad(),territorio.jurisdiccion(),territorio.versionFuente()));
            } else if(dato.valor() instanceof Number||"piso".equals(f.clave())&&dato.valor() instanceof String)
                CamposProyeccionComercial.poner(salida,PoliticaProyeccionNetworkV1.CAMPOS,"PROPIEDAD:"+f.clave(),CamposProyeccionComercial.campo(dato.valor(),dato.concepto(),f.unidad(),null,null));
        }
    }
    private ProyeccionOfertaL1Repository.Base ofertaActual(long id,Actor actor,Instant ahora) {
        return ofertas.actuales(actor.idOrganizacion(),List.of(id),dia(ahora)).stream().findFirst().orElseThrow(()->new NoEncontradoException("Oferta L1"));
    }
    @Override public List<DemandaInterna> demandas(OrigenReferencia origen,Modalidad modalidad,String regla,Instant corte,Actor actor) {
        admitirOrigen(origen,actor,corte,false);
        var resultado=proyectarDemandas(proyeccionesDemanda.candidatasL1(actor.idOrganizacion(),dia(corte)),modalidad,regla,corte,actor,corte);
        admitirOrigen(origen,actor,corte,false);return resultado;
    }
    @Override public List<DemandaInterna> revalidarDemandas(OrigenReferencia origen,List<Long> ids,Modalidad modalidad,String regla,Instant corte,Actor actor,Instant ahora) {
        admitirOrigen(origen,actor,ahora,false);
        var resultado=proyectarDemandas(proyeccionesDemanda.actualesL1(actor.idOrganizacion(),ids,dia(ahora)),modalidad,regla,corte,actor,ahora);
        admitirOrigen(origen,actor,ahora,false);return resultado;
    }
    private List<DemandaInterna> proyectarDemandas(List<ProyeccionDemandaCompartibleRepository.Base> bases,Modalidad modalidad,String regla,Instant corte,Actor actor,Instant ahora) {
        List<DemandaInterna> salida=new ArrayList<>();
        for(var inicial:bases) {
            if(actor.esAgente()&&inicial.responsable()==actor.idRolOperativo())continue;
            var b=inicial;
            boolean resuelta=false;
            for(int intento=0;intento<2;intento++) {
                var confirmacion=disponibilidad(actor.idOrganizacion(),"DEMANDA",b.id(),OperacionInmobiliaria.desde(b.operacion()).name(),DisponibilidadContextualService.contextoDemanda(b.id()));
                if(motor.disponibilidad(confirmacion,ahora).conclusion()==Conclusion.INCUMPLE){resuelta=true;break;}
                var criterios=new ArrayList<>(ProyeccionComercialDemanda.criteriosBase(OperacionInmobiliaria.desde(b.operacion()).name(),b.tipos(),b.zonas()));
                for(String json:proyeccionesDemanda.criterios(actor.idOrganizacion(),b.id(),b.version(),ProyeccionComercialDemanda.SELECTORES_FISICOS))
                    criterios.add(documentos.comoObjeto(json,Criterio.class));
                var actual=proyeccionesDemanda.actualesL1(actor.idOrganizacion(),List.of(b.id()),dia(ahora)).stream().findFirst().orElse(null);
                if(actual==null){resuelta=true;break;}
                if(!actual.equals(b)){b=actual;continue;}
                salida.add(new DemandaInterna(b.id(),b.version(),new Consulta("demanda:"+b.id(),b.version(),modalidad,criterios,corte,regla,CoberturaCriterios.PARCIAL),
                        new ResponsableVisible("AGENTE:"+b.responsable(),b.profesional()),confirmacion));resuelta=true;break;
            }
            if(!resuelta)throw new ConflictoException("La demanda candidata cambió durante la proyección L1; vuelve a comprobarla.");
        }
        return List.copyOf(salida);
    }
    private ConfirmacionDisponibilidad disponibilidad(long org,String tipo,long id,String operacion,String contexto) {
        var hechos=confirmaciones.hechosParaMatching(org,tipo,id,operacion,DisponibilidadContextualService.ASPECTO,contexto).stream()
                .map(h->new SelectorConfirmacionVigente.Hecho(h.getId(),h.getVersion(),h.getDisponible(),
                        DisponibilidadContextualService.PrecisionHecho.valueOf(h.getPrecision()),h.getBaseFrescura(),h.getFechaSinHora(),h.getRegistradoEn())).toList();
        return SelectorConfirmacionVigente.seleccionar(hechos).paraMatching();
    }
    private void admitirOrigen(OrigenReferencia origen,Actor actor,Instant ahora,boolean buscarOfertas) {
        Objects.requireNonNull(origen);Objects.requireNonNull(ahora);
        if(actor==null||(!actor.esAgente()&&!actor.esBroker())||!contextoActor.contextoOperativoActual(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),dia(ahora)))throw new AccesoNoAutorizadoException();
        if(buscarOfertas!=(origen.origen()==Origen.DEMANDA))throw new ReglaNegocioException("El sentido de matching debe conservar su origen autorizado.");
        var esperado=switch(origen.origen()){case DEMANDA->TipoVersion.DEMANDA;case ENCARGO->TipoVersion.ENCARGO;case CAPTURA->TipoVersion.CAPTURA;case PROSPECCION,FISICO->TipoVersion.FISICA;};
        if(origen.tipoVersion()!=esperado)throw new ReglaNegocioException("El tipo de versión no corresponde al origen.");
        if(buscarOfertas) {
            var c=demandas.contextoDisponibilidad(origen.id(),actor,false);
            if(c.version()!=origen.version())throw new ConflictoException("La demanda de origen cambió.");
            if(!"A".equals(c.estado())||c.vigenteDesde()!=null&&dia(ahora).isBefore(c.vigenteDesde())||c.vigenteHasta()!=null&&dia(ahora).isAfter(c.vigenteHasta()))
                throw new ReglaNegocioException("La demanda de origen no está operativa en este corte.");
        } else FuenteCarteraMatching.exigirVersion(origen.version(),propia.versionAutorizada(origen.origen(),origen.id(),actor));
    }
    private static long idOferta(String ref) {
        if(ref==null||!ref.matches("encargo:[1-9][0-9]*"))throw new ReglaNegocioException("Referencia interna L1 no reconocida.");
        try{return Long.parseLong(ref.substring("encargo:".length()));}catch(NumberFormatException e){throw new ReglaNegocioException("Referencia interna L1 inválida.");}
    }
    private static LocalDate dia(Instant instante){return instante.atZone(LIMA).toLocalDate();}
}
