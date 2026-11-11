package com.controllocal.service.network;

import static com.controllocal.service.network.CamposProyeccionComercial.*;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.captura.SuficienciaInmueble.Dato;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.controllocal.service.captura.SuficienciaInmueble.EstadoDato.CONOCIDO;
import static com.controllocal.service.network.PoliticaProyeccionNetworkV1.*;

/** CAT-E03/E19/E20 y aprobación28/10. Fuente propia; la aprobación comercial no inventa legitimación. */
@Component
public class FuenteNetworkActual implements FuenteDeNetwork,com.controllocal.service.colaboracion.FuenteOfertaConcedida {
    public static final String TRATAMIENTO_PENDIENTE="TRATAMIENTO_NETWORK_PENDIENTE_DE_VALIDACION";
    /** La concesión acredita alcance comercial; no sustituye el tratamiento específico pendiente. */
    @Override @Transactional(readOnly=true)
    public com.controllocal.service.colaboracion.FuenteOfertaConcedida.Verificacion verificarCamposColaboracion(
            com.controllocal.service.colaboracion.ReferenciaParteConcedida r,Actor receptorReal) {
        Alcances.exigirOperador(receptorReal);
        if(r==null||r.lado()!=com.controllocal.domain.colaboracion.LadoColaboracion.ENCARGO
                ||r.concesion()==null||r.idDecision()<=0||r.idRecurso()<=0||r.organizacion()<=0)
            throw new AccesoNoAutorizadoException();
        var actual=contextos.deReferenciasAprobadas(Set.of(r.idRecurso())).stream()
                .filter(c->c.getOrganizacion()==r.organizacion()&&c.getEncargo()==r.idRecurso())
                .findFirst().orElseThrow(()->new NoEncontradoException("Oferta concedida"));
        if(actual.getResponsable()==null)throw new ConflictoException("La oferta ya no tiene responsable actual.");
        // Igual frontera de la fuente Network: no recuperar ni calcular atributos ajenos sin tratamiento validado.
        return new com.controllocal.service.colaboracion.FuenteOfertaConcedida.Verificacion(actual.getOrganizacion(),
                actual.getEncargo(),actual.getResponsable(),actual.getVersion(),null,false,Set.of(TRATAMIENTO_PENDIENTE));
    }
    private final AccesoEncargo acceso;
    private final EncargoService encargos;
    private final Alcances alcances;
    private final OrganizacionRepository organizaciones;
    private final SupervisionAgenteRepository supervisiones;
    private final AutoridadDePropiedad autoridadFisica;
    private final LectorPorAutoridad lector;
    private final AtributosGobernados definiciones;
    private final com.controllocal.service.matching.MotorMatchingDeterminista motor;
    private final SuficienciaRegistrada suficiencia;
    private final GobiernoCatalogoCapturaService catalogosTerritoriales;
    private final DisponibilidadContextualService disponibilidad;
    private final ContextoNetworkRepository contextos;
    private final Documentos documentos;
    private final CaptacionRepository fuentesInvitacion;
    private final ConfirmacionDisponibilidadContextualRepository confirmacionesInvitacion;
    public FuenteNetworkActual(AccesoEncargo acceso,EncargoService encargos,Alcances alcances,
            OrganizacionRepository organizaciones,SupervisionAgenteRepository supervisiones,
            AutoridadDePropiedad autoridadFisica,LectorPorAutoridad lector,SuficienciaRegistrada suficiencia,
            DisponibilidadContextualService disponibilidad,ContextoNetworkRepository contextos,Documentos documentos,
            AtributosGobernados definiciones,com.controllocal.service.matching.MotorMatchingDeterminista motor,
            GobiernoCatalogoCapturaService catalogosTerritoriales,CaptacionRepository fuentesInvitacion,ConfirmacionDisponibilidadContextualRepository confirmacionesInvitacion) {
        this.acceso=acceso;this.encargos=encargos;this.alcances=alcances;this.organizaciones=organizaciones;
        this.supervisiones=supervisiones;this.autoridadFisica=autoridadFisica;this.lector=lector;this.suficiencia=suficiencia;
        this.disponibilidad=disponibilidad;this.contextos=contextos;this.documentos=documentos;
        this.definiciones=definiciones;this.motor=motor;
        this.catalogosTerritoriales=catalogosTerritoriales;
        this.fuentesInvitacion=fuentesInvitacion;this.confirmacionesInvitacion=confirmacionesInvitacion;
    }
    @Override @Transactional(readOnly=true)
    public AutoridadActual consultarAutoridadPropia(long id,Actor a) {
        Alcances.exigirOperador(a);
        var contexto=acceso.contexto(id,a);
        if(!alcances.alcanza(a,contexto.getResponsable()))throw new AccesoNoAutorizadoException();
        var org=organizaciones.findById(a.idOrganizacion()).orElseThrow(AccesoNoAutorizadoException::new);
        boolean individual="INDIVIDUAL".equals(org.getModalidad());
        Long broker=individual?null:supervisiones.buscarActivaPorAgente(a.idOrganizacion(),contexto.getResponsable())
                .map(s->s.getIdRolBroker()).orElse(null);
        Set<String> causas=new TreeSet<>();causas.add(TRATAMIENTO_PENDIENTE);
        if(!org.estaActiva())causas.add("ORGANIZACION_INACTIVA");
        if(!individual&&!"EMPRESARIAL".equals(org.getModalidad()))causas.add("MODALIDAD_NO_ACREDITADA");
        if(!individual&&broker==null)causas.add("SIN_BROKER_COMPETENTE");
        return new AutoridadActual(a.idOrganizacion(),id,contexto.getResponsable(),broker,individual,false,causas);
    }
    @Override @Transactional(readOnly=true)
    public FuentePropia consultarPropia(long id,Set<String> campos,Accion accion,Actor a) {
        exigirCampos(campos);
        if(accion==null)throw new ReglaNegocioException("Se requiere una actuación Network.");
        var autoridad=consultarAutoridadPropia(id,a);
        if(accion==Accion.SOLICITAR&&(!a.esAgente()||a.idRolOperativo()!=autoridad.responsable()))throw new AccesoNoAutorizadoException();
        if(accion==Accion.DECIDIR&&!(a.esBroker()&&Objects.equals(autoridad.brokerActual(),a.idRolOperativo())
                ||a.esAgente()&&autoridad.individual()&&a.idRolOperativo()==autoridad.responsable()))throw new AccesoNoAutorizadoException();
        if(accion==Accion.DESACTIVAR&&(!a.esBroker()||!Objects.equals(autoridad.brokerActual(),a.idRolOperativo())))throw new AccesoNoAutorizadoException();
        var f=encargos.consultar(id,a);
        Propiedad p=autoridadFisica.buscarParaLectura(a,f.idPropiedad()).orElseThrow(()->new NoEncontradoException("Propiedad"));
        var ultima=disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.ENCARGO,id,a);
        var cond=f.condiciones();
        var datos=proyectarOferta(new EncargoService.OfertaAutorizada(a.idOrganizacion(),id,f.idPropiedad(),f.version(),p.getVersionFisica(),f.operacion(),f.composicionActual(),
                cond==null?null:cond.precioPedido(),cond==null?null:cond.monedaPrecio(),cond==null?null:cond.periodoPrecio(),f.vigencia().habilitado()),p,campos,ultima);
        var disponibilidadActual=motor.disponibilidad(ultima,Instant.now());
        Set<String> causas=new TreeSet<>(autoridad.causas());
        if(!f.vigencia().habilitado())causas.add("ENCARGO_NO_HABILITADO");
        if(disponibilidadActual.conclusion()!=com.controllocal.service.matching.ContratoMatching.Conclusion.CUMPLE) {
            var causa=disponibilidadActual.causa();
            causas.add(causa==com.controllocal.service.matching.ContratoMatching.Causa.SIN_CONFIRMACION_DISPONIBILIDAD
                    ||causa==com.controllocal.service.matching.ContratoMatching.Causa.CONFIRMACION_NO_VIGENTE
                    ?FRESCURA:causa==null?"INDISPONIBILIDAD_CONOCIDA":"DISPONIBILIDAD_"+causa.name());
        }
        String contexto=f.composicionActual()==null?"UNIDAD:"+f.idPropiedad():"COMPOSICION:"+f.composicionActual();
        String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("datos",datos,"contexto",contexto)));
        return new FuentePropia(a.idOrganizacion(),id,autoridad.responsable(),autoridad.brokerActual(),autoridad.individual(),
                f.version(),p.getVersionFisica(),VERSION,FINALIDAD,datos,huella,huellaAutoridad(autoridad),false,causas);
    }
    private Map<String,Object> proyectarOferta(EncargoService.OfertaAutorizada f,Propiedad p,Set<String> campos,
            com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad ultima) {
        Map<String,Object> datos=new TreeMap<>();
        poner(datos,campos,"ENCARGO:operacion",campo(f.operacion(),null,null,null,null));
        // Una composición no hereda los atributos de la matriz como si describieran el paquete completo.
        if(f.composicionActual()==null) {
            poner(datos,campos,"PROPIEDAD:tipo_propiedad",campo(p.getTipoInmueble(),null,null,null,null));
            if(p.getIdDistrito()!=null)poner(datos,campos,"PROPIEDAD:distrito",campo(Long.toString(p.getIdDistrito()),null,null,null,null));
            var valores=lector.de(f.organizacion(),p);
            var fisicos=suficiencia.datos(p,valores);
            var catalogo=definiciones.definicionesParaLeer(f.organizacion(),p.getTipoInmueble(),valores.claves());
            for(String clave:List.of("metraje_total","metraje_construido","area_terreno","dormitorios","banos","estacionamientos","piso","ascensores","zonificacion")) {
                String selector="PROPIEDAD:"+clave;if(!campos.contains(selector))continue;
                Dato d=fisicos.get(clave);if(d==null||d.estado()!=CONOCIDO||d.valor()==null)continue;
                boolean area=Set.of("metraje_total","metraje_construido","area_terreno").contains(clave);
                var definicion=catalogo.get(clave);if(definicion==null)continue;
                if(area&&!Set.of("m2","m²").contains(Objects.toString(definicion.getUnidad(),"")))continue;
                if("zonificacion".equals(clave)) {
                    if(d.evidenciaRef()==null||d.referenciaCatalogo()==null)continue;
                    var territorio=catalogosTerritoriales.opcionesVigentes(f.organizacion(),clave,suficiencia.contexto(p,f.operacion(),null).jurisdiccion());
                    if(territorio.motivo()!=null||!Objects.equals(territorio.referenciaCatalogo(),d.referenciaCatalogo()))continue;
                    var campo=campo(d.valor(),d.concepto(),definicion.getUnidad(),null,null);
                    if(territorio.jurisdiccion()!=null)campo.put("jurisdiccion",territorio.jurisdiccion());
                    if(territorio.versionFuente()!=null)campo.put("versionFuente",territorio.versionFuente());
                    poner(datos,campos,selector,campo);continue;
                } else if("piso".equals(clave)) {
                    // CAT-E39: entero/identificador de nivel, TEXTO real; no equivale a notas ni dirección.
                    if(!(d.valor() instanceof String nivel)||nivel.isBlank())continue;
                } else if(!(d.valor() instanceof Number))continue;
                poner(datos,campos,selector,campo(d.valor(),d.concepto(),definicion.getUnidad(),null,null));
            }
        }
        var comerciales=lector.deEncargo(f.encargo());
        boolean porMetro="ALQUILER".equals(f.operacion())&&"POR_M2_AL_MES".equals(comerciales.texto("modalidad_precio"));
        if(f.precioPedido()!=null&&f.monedaPrecio()!=null)
            poner(datos,campos,"ENCARGO:precio_pedido",campo(f.precioPedido(),
                    "VENTA".equals(f.operacion())?"PRECIO_VENTA":"PRECIO_ALQUILER",porMetro?"m2":null,f.monedaPrecio(),f.periodoPrecio()));
        for(String clave:List.of("se_ofrece_amoblado","se_entrega_implementado")) {
            var v=comerciales.valor(clave);
            if(v!=null&&v.booleano()!=null)poner(datos,campos,"ENCARGO:"+clave,campo(v.booleano(),null,null,null,null));
        }
        var disponibilidadActual=motor.disponibilidad(ultima,Instant.now());
        if(disponibilidadActual.conclusion()!=com.controllocal.service.matching.ContratoMatching.Conclusion.NO_DETERMINADO)
            poner(datos,campos,"ENCARGO:disponibilidad",campo(disponibilidadActual.conclusion()==com.controllocal.service.matching.ContratoMatching.Conclusion.CUMPLE,null,null,null,null));
        return Collections.unmodifiableMap(datos);
    }
    @Override @Transactional(readOnly=true)
    public Map<String,Object> camposDeInvitacion(com.controllocal.service.colaboracion.AccesoFuenteDeInvitacion.Permiso permiso) {
        exigirCampos(permiso.campos());var f=encargos.ofertaDeInvitacion(permiso);
        if(!f.habilitado())throw new ReglaNegocioException("El instrumento de la invitación perdió su habilitación.");
        var p=fuentesInvitacion.buscarFicha(permiso.organizacion(),permiso.encargo()).orElseThrow(()->new NoEncontradoException("Encargo de invitación")).getPropiedad();
        com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad confirmacion=null;
        if(permiso.campos().contains("ENCARGO:disponibilidad")){
            String contexto=f.composicionActual()==null?DisponibilidadContextualService.contextoUnidad(f.idPropiedad()):DisponibilidadContextualService.contextoComposicion(f.composicionActual());
            var hechos=confirmacionesInvitacion.hechosParaMatching(permiso.organizacion(),"ENCARGO",permiso.encargo(),
                    com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(f.operacion()).codigo(),DisponibilidadContextualService.ASPECTO,contexto);
            confirmacion=com.controllocal.service.disponibilidad.SelectorConfirmacionVigente.seleccionar(hechos.stream().map(h->new com.controllocal.service.disponibilidad.SelectorConfirmacionVigente.Hecho(
                    h.getId(),h.getVersion(),h.getDisponible(),DisponibilidadContextualService.PrecisionHecho.valueOf(h.getPrecision()),h.getBaseFrescura(),h.getFechaSinHora(),h.getRegistradoEn())).toList()).paraMatching();
        }
        return proyectarOferta(f,p,permiso.campos(),confirmacion);
    }
    @Override @Transactional(readOnly=true)
    public Map<Long,VerificacionActual> verificarActuales(Collection<ReferenciaAutorizada> versiones,Actor a) {
        Alcances.exigirOperador(a);
        return verificarMetadatos(versiones,a);
    }
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Map<Long,VerificacionActual> verificarCambioCore(Collection<ReferenciaAutorizada> versiones,Actor a,
            com.controllocal.domain.auditoria.EventoDominio evento) {
        if(a==null||evento==null||evento.getId()==null||evento.getId()<=0
                ||evento.getOrganizacionId()!=a.idOrganizacion()||!Objects.equals(evento.getIdPersonaRol(),a.idRolOperativo()))
            throw new AccesoNoAutorizadoException();
        if(a.esTenantAdmin()) {
            if(!"ENCARGO_REASIGNADO".equals(evento.getTipo())||!"CAPTACION".equals(evento.getEntidadTipo()))
                throw new AccesoNoAutorizadoException();
        } else Alcances.exigirOperador(a);
        if(versiones==null||versiones.stream().anyMatch(v->v.organizacion()!=a.idOrganizacion()))
            throw new AccesoNoAutorizadoException();
        return verificarMetadatos(versiones,a);
    }
    private Map<Long,VerificacionActual> verificarMetadatos(Collection<ReferenciaAutorizada> versiones,Actor a) {
        if(versiones==null)throw new ReglaNegocioException("Se requieren referencias autorizadas.");
        if(versiones.isEmpty())return Map.of();
        var porId=new LinkedHashMap<Long,ReferenciaAutorizada>();
        for(var r:versiones) {
            exigirCampos(r.campos());
            if(r.ofertaRef()==null||r.ofertaRef().isBlank()||r.referenciaVersion()==null||r.referenciaVersion().isBlank()||r.versionProyeccion()<1)
                throw new ReglaNegocioException("Referencia de versión no válida.");
            var previa=porId.putIfAbsent(r.idEncargo(),r);
            if(previa!=null&&!previa.equals(r))throw new ReglaNegocioException("Dos versiones distintas para la misma referencia.");
        }
        Map<Long,VerificacionActual> resultado=new HashMap<>();
        for(var c:contextos.deReferenciasAprobadas(porId.keySet())) {
            var r=porId.get(c.getEncargo());if(r==null||r.organizacion()!=c.getOrganizacion())continue;
            boolean individual="INDIVIDUAL".equals(c.getModalidad());
            Set<String> causas=new TreeSet<>();causas.add(TRATAMIENTO_PENDIENTE);
            if(!"A".equals(c.getEstadoOrganizacion()))causas.add("ORGANIZACION_INACTIVA");
            if(c.getResponsable()==null)continue;
            var autoridad=new AutoridadActual(c.getOrganizacion(),c.getEncargo(),c.getResponsable(),individual?null:c.getBroker(),individual,false,causas);
            // No existe aún tratamiento específico validado: se detiene ANTES de recuperar atributos ajenos.
            // null no afirma que el dato cambió ni que coincide; elegible=false y su causa gobiernan la salida.
            resultado.put(c.getEncargo(),new VerificacionActual(c.getOrganizacion(),c.getEncargo(),c.getResponsable(),autoridad.brokerActual(),
                    individual,VERSION,FINALIDAD,null,huellaAutoridad(autoridad),false,causas,null));
        }
        return Map.copyOf(resultado);
    }
    private String huellaAutoridad(AutoridadActual a) {
        var d=new TreeMap<String,Object>();d.put("organizacion",a.organizacion());d.put("encargo",a.idEncargo());
        d.put("responsable",a.responsable());d.put("broker",a.brokerActual());d.put("individual",a.individual());
        return Idempotencia.huellaExacta(documentos.objetoCanonico(d));
    }
}
