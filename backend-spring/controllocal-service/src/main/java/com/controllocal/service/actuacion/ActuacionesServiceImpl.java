package com.controllocal.service.actuacion;

import com.controllocal.domain.captura.OperacionHerramienta;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.persistence.repositorio.OperacionHerramientaRepository;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.UnidadRelacionadaService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.capacidades.AdaptadoresDeCapacidad;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Autoridad UC-K-06/07. El intérprete no tiene acceso a revisión/confirmación. */
@Service
public class ActuacionesServiceImpl implements ActuacionesService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ActuacionesServiceImpl.class);
    private final OperacionHerramientaRepository operaciones;
    private final ComandoIdempotenteRepository identidades;
    private final BorradorCapturaRepository borradores;
    private final MotorDeCaptura motor;
    private final AdaptadoresDeCapacidad adaptadores;
    private final Documentos documentos;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final ContextosService contextos;
    private final UnidadRelacionadaService origenFisico;
    private final PropiedadUniversalService propiedades;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final Map<String,EfectoDeActuacion> casos;
    private static final String ORIGEN_FISICO="registrar_origen_fisico_unidad";

    public ActuacionesServiceImpl(OperacionHerramientaRepository operaciones,
            ComandoIdempotenteRepository identidades,
            BorradorCapturaRepository borradores, MotorDeCaptura motor,
            AdaptadoresDeCapacidad adaptadores, Documentos documentos, ObjectMapper json,
            PlatformTransactionManager transacciones, ContextosService contextos,
            UnidadRelacionadaService origenFisico, PropiedadUniversalService propiedades,
            CuentaAccesoRepository cuentas, UsuarioOrganizacionRepository miembros,List<EfectoDeActuacion> efectos) {
        this.operaciones=operaciones; this.identidades=identidades; this.borradores=borradores; this.motor=motor;
        this.adaptadores=adaptadores; this.documentos=documentos; this.json=json;
        this.contextos=contextos;
        this.origenFisico=origenFisico;this.propiedades=propiedades;this.cuentas=cuentas;this.miembros=miembros;
        var registrados=new LinkedHashMap<String,EfectoDeActuacion>();
        for(var e:efectos)for(String c:e.capacidades())
            if(Set.of("confirmar_captura",ORIGEN_FISICO).contains(c)||registrados.putIfAbsent(c,e)!=null)
                throw new IllegalStateException("Dos productores para la misma actuación.");
        casos=Map.copyOf(registrados);
        tx=new TransactionTemplate(transacciones);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public Propuesta prepararOrigen(Actor actor,UnidadRelacionadaService.DatosOrigen datos,
            ContextoSesion sesion,Procedencia origen) {
        exigir(actor,sesion,ORIGEN_FISICO);
        return tx.execute(s->{
            exigirContexto(actor,sesion);
            String contenido=instantaneaOrigen(actor,datos);
            var o=new OperacionHerramienta(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),
                    actor.rolEfectivo(),sesion.identidad(),null,UUID.randomUUID().toString(),ORIGEN_FISICO,
                    Idempotencia.huellaExacta(contenido),contenido,
                    documentos.objetoCanonico(mapa(Procedencia.oPantalla(origen))),
                    Instant.ofEpochSecond(sesion.expiraEnEpochSeconds()));
            operaciones.saveAndFlush(o);return propuesta(o);
        });
    }

    @Override public String capacidadDe(String referencia,Actor actor) {
        if(actor==null||(!actor.esAgente()&&!actor.esBroker()))throw new AccesoNoAutorizadoException();
        if(referencia==null||!referencia.matches("[a-f0-9-]{36}"))throw new NoEncontradoException("Actuación no localizada.");
        var selector=operaciones.selectorDeLaActuacion(actor.idOrganizacion(),referencia)
                .orElseThrow(()->new NoEncontradoException("Actuación no localizada."));
        exigirActor(selector.getIdPersona(),selector.getIdPersonaRol(),selector.getRolEfectivo(),actor);
        String capacidad=selector.getCapacidad();
        if(!Set.of("confirmar_captura",ORIGEN_FISICO).contains(capacidad)&&!casos.containsKey(capacidad))
            throw new IllegalStateException("La actuación no conserva una capacidad reconocida.");
        return capacidad;
    }

    @Override public Propuesta prepararFinDemanda(Actor a,com.controllocal.service.RequerimientoService.FinDemanda d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosC3DeActuacion.FIN_DEMANDA,d,a,s,p);
    }
    @Override public Propuesta prepararSolicitudColaboracion(Actor a,com.controllocal.service.colaboracion.ColaboracionService.DatosSolicitud d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosC3DeActuacion.SOLICITAR,d,a,s,p);
    }
    @Override public Propuesta prepararInvitacionColaboracion(Actor a,com.controllocal.service.colaboracion.ColaboracionService.DatosInvitacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosC3DeActuacion.INVITAR,d,a,s,p);}
    @Override public Propuesta prepararDecisionColaboracion(Actor a,com.controllocal.service.colaboracion.ColaboracionService.DatosDecision d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosC3DeActuacion.DECIDIR,d,a,s,p);
    }
    @Override public Propuesta prepararRevocacionColaboracion(Actor a,com.controllocal.service.colaboracion.ColaboracionService.DatosRevocacion d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosC3DeActuacion.REVOCAR,d,a,s,p);
    }
    @Override public Propuesta prepararAperturaOportunidad(Actor a,com.controllocal.service.OportunidadService.DatosOportunidad d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosActividadDeActuacion.ABRIR,d,a,s,p);}
    @Override public Propuesta prepararRectificacionInteraccion(Actor a,com.controllocal.service.InteraccionService.Rectificacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosActividadDeActuacion.RECTIFICAR,d,a,s,p);}
    @Override public Propuesta prepararRectificacionComunicacionReporte(Actor a,com.controllocal.service.ReportePropietarioService.Rectificacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosReporteDeActuacion.RECTIFICAR,d,a,s,p);}
    @Override public Propuesta prepararEstablecimientoMetas(Actor a,com.controllocal.service.MetaComercialService.Establecer d,ContextoSesion s,Procedencia p){return prepararCaso(com.controllocal.service.impl.MetaComercialServiceImpl.FIJAR,d,a,s,p);}
    @Override public Propuesta prepararDecisionMeta(Actor a,com.controllocal.service.MetaComercialService.Decision d,ContextoSesion s,Procedencia p){return prepararCaso(com.controllocal.service.impl.MetaComercialServiceImpl.DECIDIR,d,a,s,p);}
    @Override public Propuesta prepararNoContinuidadOportunidad(Actor a,com.controllocal.service.OportunidadService.NoContinuidad d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosActividadDeActuacion.NO_CONTINUIDAD,d,a,s,p);}
    @Override public Propuesta prepararCancelacionVisita(Actor a,com.controllocal.service.VisitaService.Cancelacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosActividadDeActuacion.CANCELAR,d,a,s,p);}
    @Override public Propuesta prepararRegistroOferta(Actor a,com.controllocal.service.OfertaService.DatosRegistroOferta d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosNegociacionDeActuacion.OFERTA,d,a,s,p);}
    @Override public Propuesta prepararContraoferta(Actor a,com.controllocal.service.OfertaService.DatosContraoferta d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosNegociacionDeActuacion.CONTRAOFERTA,d,a,s,p);}
    @Override public Propuesta prepararAceptacionOferta(Actor a,com.controllocal.service.OfertaService.DatosAceptacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosNegociacionDeActuacion.ACEPTACION,d,a,s,p);}
    @Override public Propuesta prepararRechazoOferta(Actor a,com.controllocal.service.OfertaService.DatosRechazo d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosNegociacionDeActuacion.RECHAZO,d,a,s,p);}
    @Override public Propuesta prepararRetiradaOferta(Actor a,com.controllocal.service.OfertaService.DatosRetirada d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosNegociacionDeActuacion.RETIRADA,d,a,s,p);}
    @Override public Propuesta prepararDecisionEncargo(Actor a,long id,
            com.controllocal.service.encargo.EncargoService.Decision d,ContextoSesion s,Procedencia p){
        return prepararCaso(EfectosDecisionEncargoDeActuacion.CAPACIDAD,
                new EfectosDecisionEncargoDeActuacion.Datos(id,d),a,s,p);
    }

    @Override public Propuesta prepararPresentacionExpediente(Actor a,com.controllocal.service.formalizacion.FormalizacionService.DatosPresentacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosFormalizacionDeActuacion.PRESENTAR,d,a,s,p);}
    @Override public Propuesta prepararEvaluacionExpediente(Actor a,com.controllocal.service.formalizacion.FormalizacionService.DatosRevision d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosFormalizacionDeActuacion.EVALUAR,d,a,s,p);}
    @Override public Propuesta prepararInformacionHito(Actor a,com.controllocal.service.formalizacion.FormalizacionService.DatosInformacionHito d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosFormalizacionDeActuacion.INFORMAR,d,a,s,p);}
    @Override public Propuesta prepararConfirmacionHito(Actor a,com.controllocal.service.formalizacion.FormalizacionService.DatosConfirmacionHito d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosFormalizacionDeActuacion.CONFIRMAR,d,a,s,p);}
    @Override public Propuesta prepararRectificacionHito(Actor a,com.controllocal.service.formalizacion.FormalizacionService.DatosRectificacionHito d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosFormalizacionDeActuacion.RECTIFICAR,d,a,s,p);}
    @Override public Propuesta prepararMovimientoComision(Actor a,com.controllocal.service.ComisionService.DatosCobro d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosComisionDeActuacion.MOVIMIENTO,d,a,s,p);}
    @Override public Propuesta prepararRepartoComision(Actor a,com.controllocal.service.ComisionService.DatosReparto d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosComisionDeActuacion.REPARTO,d,a,s,p);}
    @Override public Propuesta prepararReprogramacionCobro(Actor a,com.controllocal.service.ComisionService.DatosReprogramacion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosComisionDeActuacion.REPROGRAMAR,d,a,s,p);}
    @Override public Propuesta prepararConclusionCobro(Actor a,com.controllocal.service.ComisionService.DatosConclusion d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosComisionDeActuacion.CONCLUIR,d,a,s,p);}
    @Override public Propuesta prepararDecisionBorradorAdenda(Actor a,com.controllocal.service.encargo.EncargoService.ReferenciaAdenda r,
            com.controllocal.service.encargo.EncargoService.DecisionBorradorAdenda d,ContextoSesion s,Procedencia p){return prepararCaso(EfectosAdendaDeActuacion.AUTORIZAR,new EfectosAdendaDeActuacion.Datos(r,d),a,s,p);}

    @Override public Propuesta prepararPublicacion(String capacidad,Actor a,com.controllocal.service.PublicacionService.DatosCambio d,ContextoSesion s,Procedencia p){
        if(capacidad==null||!java.util.Set.of(com.controllocal.service.PublicacionService.PUBLICAR,
                com.controllocal.service.PublicacionService.ACTUALIZAR_VISIBLE,com.controllocal.service.PublicacionService.PAUSAR,
                com.controllocal.service.PublicacionService.REANUDAR,com.controllocal.service.PublicacionService.FINALIZAR).contains(capacidad))
            throw new com.controllocal.service.excepcion.ReglaNegocioException("Actuación de publicación no reconocida.");
        return prepararCaso(capacidad,d,a,s,p);
    }

    @Override public Propuesta prepararEntregaPieza(Actor a,com.controllocal.service.documentos.PiezasComercialesService.Entrega d,ContextoSesion s,Procedencia p){
        return prepararCaso(com.controllocal.service.documentos.PiezasComercialesService.OBTENER,d,a,s,p);
    }

    private Propuesta prepararCaso(String capacidad,Object datos,Actor a,ContextoSesion s,Procedencia p) {
        exigir(a,s,capacidad);
        return tx.execute(estado->{
            bloquearCuenta(a);exigirContexto(a,s);
            String contenido=instantaneaCaso(capacidad,mapa(datos),a);
            var o=new OperacionHerramienta(a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),
                    s.identidad(),null,UUID.randomUUID().toString(),capacidad,Idempotencia.huellaExacta(contenido),
                    contenido,documentos.objetoCanonico(mapa(Procedencia.oPantalla(p))),Instant.ofEpochSecond(s.expiraEnEpochSeconds()));
            operaciones.saveAndFlush(o);return propuesta(o);
        });
    }
    @Override public Propuesta prepararCanalColaboracion(Actor a,com.controllocal.service.colaboracion.CanalesColaboracionService.DatosDeclaracion d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosC3DeActuacion.DECLARAR_CANAL,d,a,s,p);
    }
    @Override public Propuesta prepararSolicitudNetwork(Actor a,com.controllocal.service.network.NetworkService.DatosSolicitud d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosNetworkDeActuacion.SOLICITAR,d,a,s,p);
    }
    @Override public Propuesta prepararDecisionNetwork(Actor a,com.controllocal.service.network.NetworkService.DatosDecision d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosNetworkDeActuacion.DECIDIR,d,a,s,p);
    }
    @Override public Propuesta prepararDesactivacionNetwork(Actor a,com.controllocal.service.network.NetworkService.DatosDesactivacion d,ContextoSesion s,Procedencia p) {
        return prepararCaso(EfectosNetworkDeActuacion.DESACTIVAR,d,a,s,p);
    }
    private String instantaneaCaso(String capacidad,Map<String,Object> datos,Actor a) {
        var e=casos.get(capacidad);if(e==null)throw new IllegalStateException("No se localiza el caso de actuación.");
        var normalizados=mapa(e.validar(capacidad,datos,a));
        return documentos.objetoCanonico(Map.of("contrato",com.controllocal.service.capacidades.CapacidadesService.VERSION,
                "efecto",capacidad,"datos",normalizados,"revision",mapa(e.resumenRevision(capacidad,normalizados,a))));
    }
    private Map<String,Object> datosCaso(String contenido) {
        Object d=documentos.comoMapa(contenido).get("datos");
        if(!(d instanceof Map<?,?>))throw new IllegalStateException("La actuación no conserva sus datos tipados.");
        return mapa(d);
    }

    @Override public Propuesta prepararCaptura(Actor actor,long id,ContextoSesion sesion,Procedencia origen) {
        return prepararCaptura(actor,id,sesion,origen,null,null);
    }

    @Override public Propuesta prepararCaptura(Actor actor,long id,ContextoSesion sesion,Procedencia origen,
            ComandosAutomaticosService.CorrelacionAutomatico correlacion,Map<String,Object> datosEsperados) {
        exigir(actor,sesion,"preparar_actuacion_captura");
        return tx.execute(s -> {
            exigirContexto(actor,sesion);
            String identidadExacta=correlacion==null ? null : Idempotencia.huellaExacta(
                    documentos.objetoCanonico(Map.of("capacidad","preparar_actuacion_captura",
                            "contrato",com.controllocal.service.capacidades.CapacidadesService.VERSION,
                            "actor",mapa(actor),"sesion",mapa(sesion),"correlacion",mapa(correlacion))));
            var identidad=identidadExacta==null ? UUID.randomUUID() : UUID.fromString(
                    identidadExacta.substring(0,8)+"-"+identidadExacta.substring(8,12)+"-"
                            +identidadExacta.substring(12,16)+"-"+identidadExacta.substring(16,20)+"-"
                            +identidadExacta.substring(20,32));
            String ref=identidad.toString();
            if (correlacion!=null) {
                identidades.bloquearIdentidad(identidad.getMostSignificantBits());
                if (operaciones.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),ref).isPresent()) {
                    var previa=cargar(actor,ref);
                    exigirVinculo(previa,sesion);
                    if (previa.getIdBorrador()!=id)
                        throw new ConflictoException("El turno ya preparó otra captura.");
                    motor.capacidadSobre(id,null,null,actor);
                    if (!OperacionHerramienta.PREPARADA.equals(previa.getEstado()))
                        throw new ConflictoException("La actuación ya no está preparada; consulta su resultado.");
                    comprobarDatosEsperados(previa.getContenido(),datosEsperados);
                    return propuesta(previa);
                }
            }
            String contenido=instantanea(actor,id,true);
            comprobarDatosEsperados(contenido,datosEsperados);
            var o=new OperacionHerramienta(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),
                    actor.rolEfectivo(),sesion.identidad(),id,ref,"confirmar_captura",Idempotencia.huellaExacta(contenido),
                    contenido,documentos.objetoCanonico(mapa(Procedencia.oPantalla(origen))),
                    Instant.ofEpochSecond(sesion.expiraEnEpochSeconds()));
            operaciones.saveAndFlush(o);
            return propuesta(o);
        });
    }

    private void comprobarDatosEsperados(String contenido,Map<String,Object> esperados) {
        if (esperados == null) return;
        Map<String,Object> guardado = documentos.comoMapa(contenido);
        var actual = new LinkedHashMap<String,Object>();
        actual.put("datos", guardado.get("datos"));
        actual.put("version", guardado.get("versionCaptura"));
        actual.put("contexto", guardado.get("contexto"));
        actual.put("metadatos", guardado.get("metadatos"));
        if (!documentos.objetoCanonico(esperados).equals(documentos.objetoCanonico(actual)))
            throw new ConflictoException("La captura cambió después del avance; consulta su estado antes de preparar.");
    }

    @Override public Revision revisar(Actor actor,String referencia,ContextoSesion sesion) {
        exigir(actor,sesion,"revisar_actuacion");
        return tx.execute(s -> {
            bloquearCuenta(actor);
            exigirContexto(actor,sesion);
            var o=cargar(actor,referencia); exigirVinculo(o,sesion); exigirVigente(o);
            if (!OperacionHerramienta.PREPARADA.equals(o.getEstado()))
                throw new ConflictoException("La actuación ya fue iniciada; consulta su resultado.");
            comprobarContenido(o.getCapacidad(),o.getContenido(),o.getIdBorrador(),o.getHuella(),actor,true);
            String prueba=UUID.randomUUID()+"-"+UUID.randomUUID();
            o.revisar(Idempotencia.huella(prueba)); operaciones.flush();
            return new Revision(propuesta(o),prueba);
        });
    }

    @Override public Resultado confirmar(Actor actor,String referencia,ContextoSesion sesion,
            Confirmacion confirmacion,Procedencia origenHumano) {
        exigirSesion(actor,sesion);
        if (confirmacion==null || confirmacion.eventoHumano()==null
                || !confirmacion.eventoHumano().matches("[A-Za-z0-9_-]{1,64}"))
            throw new ReglaNegocioException("Se requiere el evento de la revisión humana.");
        // La intención autenticada queda durable ANTES de intentar el efecto. No se reenvía tras incertidumbre.
        boolean iniciar=Boolean.TRUE.equals(tx.execute(s -> {
            bloquearCuenta(actor);
            exigirContexto(actor,sesion);
            var o=cargar(actor,referencia); exigirVinculo(o,sesion); exigirVigente(o);
            exigir(actor,sesion,o.getCapacidad());
            if (!iguales(o.getHuella(),confirmacion.huella())
                    || !iguales(o.getPruebaRevision(),Idempotencia.huella(
                            Objects.toString(confirmacion.pruebaRevision(),""))))
                throw new ConflictoException("La revisión no corresponde a esta actuación.");
            if (!OperacionHerramienta.PREPARADA.equals(o.getEstado())) {
                if (!Objects.equals(o.getEventoHumano(),confirmacion.eventoHumano()))
                    throw new ConflictoException("La actuación ya tiene otro evento de confirmación.");
                if (!Objects.equals(o.getVersionAprobada(),confirmacion.version()))
                    throw new ConflictoException("La versión no corresponde a la revisión aprobada.");
                return false;
            }
            if (o.getVersion()!=confirmacion.version())
                throw new ConflictoException("La revisión cambió; vuelve a revisar el contenido.");
            comprobarContenido(o.getCapacidad(),o.getContenido(),o.getIdBorrador(),o.getHuella(),actor,true);
            o.iniciar(confirmacion.eventoHumano(),documentos.objetoCanonico(Map.of(
                    "eventoHumano",confirmacion.eventoHumano(),"version",confirmacion.version(),
                    "huella",confirmacion.huella(),"procedencia",mapa(Procedencia.oPantalla(origenHumano)))));
            operaciones.flush();
            return true;
        }));
        if (!iniciar) return consultar(actor,referencia,sesion);
        try {
            return tx.execute(s -> {
                bloquearCuenta(actor);
                exigirContexto(actor,sesion);
                var o=cargar(actor,referencia);
                exigir(actor,sesion,o.getCapacidad()); exigirVinculo(o,sesion); exigirVigente(o);
                comprobarContenido(o.getCapacidad(),o.getContenido(),o.getIdBorrador(),o.getHuella(),actor,true);
                Procedencia origen=json.convertValue(documentos.comoMapa(o.getProcedencia()),Procedencia.class);
                Object r=casos.containsKey(o.getCapacidad())
                        ? casos.get(o.getCapacidad()).ejecutar(o.getCapacidad(),datosCaso(o.getContenido()),actor,sesion,clave(o.getReferencia()),origen)
                        : ORIGEN_FISICO.equals(o.getCapacidad())
                        ? origenFisico.registrarOrigen(datosOrigen(o),actor,sesion,clave(o.getReferencia()),origen)
                        : motor.ejecutar(o.getIdBorrador(), ((Number)documentos.comoMapa(o.getContenido()).get("versionCaptura")).intValue(),clave(o.getReferencia()),origen,actor);
                o.exito(documentos.objetoCanonico(mapa(r))); operaciones.flush();
                return resultado(o);
            });
        } catch (AccesoNoAutorizadoException | CapacidadDeshabilitadaException denegada) {
            rechazar(actor,referencia,"AUTORIZACION_CAMBIADA"); throw denegada;
        } catch (ReglaNegocioException | ConflictoException rechazado) {
            rechazar(actor,referencia,rechazado instanceof ConflictoException ? "CONTENIDO_CAMBIADO" : "REGLA_CORE");
            return consultar(actor,referencia,sesion);
        } catch (RuntimeException incertidumbre) {
            // No afirmar rollback: un fallo de confirmación del commit también puede llegar aquí.
            LOG.warn("Actuación {} requiere recuperación: {}",referencia,diagnosticoTecnico(incertidumbre));
            try { return consultar(actor,referencia,sesion); }
            catch (AccesoNoAutorizadoException | CapacidadDeshabilitadaException denegada) { throw denegada; }
            catch (RuntimeException sinLectura) {
                LOG.warn("Actuación {} sin recuperación confirmada: {}",referencia,diagnosticoTecnico(sinLectura));
                return new Resultado(referencia,"EN_VERIFICACION",null,null,"RESULTADO_INCIERTO");
            }
        }
    }

    /** Sólo tipos y ubicación de código; nunca mensajes, contenido, credenciales o datos comerciales. */
    private static String diagnosticoTecnico(Throwable error) {
        var causas=new ArrayList<String>();
        for(Throwable c=error;c!=null&&causas.size()<4;c=c.getCause()) {
            var sitio=Arrays.stream(c.getStackTrace()).filter(s->s.getClassName().startsWith("com.controllocal."))
                    .findFirst().map(StackTraceElement::toString).orElse("infraestructura");
            causas.add(c.getClass().getName()+" @ "+sitio);
        }
        return String.join(" <- ",causas);
    }

    @Override public Resultado consultar(Actor actor,String referencia,ContextoSesion sesion) {
        exigir(actor,sesion,"consultar_actuacion");
        return tx.execute(s -> {
            // Cuenta antes de sesión/recurso: una lectura no anticipa la autoridad ni hace upgrades de lock.
            bloquearCuenta(actor);
            exigirContexto(actor,sesion);
            var o=cargar(actor,referencia);
            if(casos.containsKey(o.getCapacidad())) {
                var caso=casos.get(o.getCapacidad());
                if(Set.of(OperacionHerramienta.INICIADA,OperacionHerramienta.INCIERTA,OperacionHerramienta.EXITO).contains(o.getEstado())) {
                    var conocido=caso.consultarResultado(o.getCapacidad(),clave(o.getReferencia()),actor);
                    if(conocido.isPresent()) {
                        var original=mapa(conocido.get());
                        if(OperacionHerramienta.EXITO.equals(o.getEstado())
                                &&!documentos.objetoCanonico(original).equals(documentos.objetoCanonico(documentos.comoMapa(o.getResultado()))))
                            throw new IllegalStateException("El resultado durable no coincide con el comando original.");
                        if(!OperacionHerramienta.EXITO.equals(o.getEstado()))o.exito(documentos.objetoCanonico(original));
                    } else {
                        if(OperacionHerramienta.EXITO.equals(o.getEstado()))throw new IllegalStateException("No se localiza el comando de un resultado confirmado.");
                        caso.exigirLectura(o.getCapacidad(),datosCaso(o.getContenido()),actor);o.incierta();
                    }
                    operaciones.flush();
                } else caso.exigirLectura(o.getCapacidad(),datosCaso(o.getContenido()),actor);
                return resultado(o);
            }
            // Una nueva sesión del mismo actor puede recuperar resultado, nunca reutilizar la aprobación.
            if (Set.of(OperacionHerramienta.INICIADA,OperacionHerramienta.INCIERTA,
                    OperacionHerramienta.EXITO).contains(o.getEstado())) {
                if(ORIGEN_FISICO.equals(o.getCapacidad())) {
                    var conocido=origenFisico.consultarResultadoOrigen(clave(o.getReferencia()),actor);
                    if(conocido.isPresent()) {
                        var original=mapa(conocido.get());
                        if(OperacionHerramienta.EXITO.equals(o.getEstado())
                                &&!documentos.objetoCanonico(original).equals(documentos.objetoCanonico(documentos.comoMapa(o.getResultado()))))
                            throw new IllegalStateException("El resultado durable no coincide con el comando original.");
                        if(!OperacionHerramienta.EXITO.equals(o.getEstado()))o.exito(documentos.objetoCanonico(original));
                    } else {
                        if(OperacionHerramienta.EXITO.equals(o.getEstado()))throw new IllegalStateException("No se localiza el comando de un resultado confirmado.");
                        o.incierta();
                    }
                    operaciones.flush();return resultado(o);
                }
                var conocido=motor.consultarResultado(o.getIdBorrador(),clave(o.getReferencia()),actor);
                if (conocido.estado()==MotorDeCaptura.EstadoDelResultado.EXITO) {
                    var original=mapa(conocido.resultado());
                    if (OperacionHerramienta.EXITO.equals(o.getEstado())) {
                        var durable=documentos.comoMapa(o.getResultado());
                        // El indicador de transporte no modifica el resultado comercial conservado.
                        original.remove("reintento");durable.remove("reintento");
                        if (!documentos.objetoCanonico(original).equals(documentos.objetoCanonico(durable)))
                            throw new IllegalStateException("El resultado durable no coincide con el comando original.");
                    }
                    if (!OperacionHerramienta.EXITO.equals(o.getEstado()))
                    o.exito(documentos.objetoCanonico(mapa(conocido.resultado())));
                } else {
                    if (OperacionHerramienta.EXITO.equals(o.getEstado()))
                        throw new IllegalStateException("No se localiza el comando de un resultado confirmado.");
                    o.incierta();
                }
                operaciones.flush();
            } else if(ORIGEN_FISICO.equals(o.getCapacidad())) {
                var d=datosOrigen(o);
                propiedades.capacidadSobre(d.idMatriz(),null,actor);propiedades.capacidadSobre(d.idUnidad(),null,actor);
            } else motor.capacidadSobre(o.getIdBorrador(),null,null,actor);
            return resultado(o);
        });
    }

    /** Una proyección escalar: leer el original no requiere conservar mínimos, CAS o permiso de escritura. */
    @Override public CapacidadSobreActuacion capacidadSobre(String referencia,Actor actor) {
        if(actor==null||(!actor.esAgente()&&!actor.esBroker()))throw new AccesoNoAutorizadoException();
        var o=operaciones.contextoDeLaActuacion(actor.idOrganizacion(),referencia)
                .orElseThrow(()->new NoEncontradoException("Actuación no localizada."));
        exigirActor(o.getIdPersona(),o.getIdPersonaRol(),o.getRolEfectivo(),actor);
        exigirIntegridad(o.getCapacidad(),o.getIdBorrador(),o.getHuella(),o.getContenido());
        boolean pendiente=OperacionHerramienta.PREPARADA.equals(o.getEstado());
        boolean terminadoOIncierto=Set.of(OperacionHerramienta.INICIADA,OperacionHerramienta.INCIERTA,OperacionHerramienta.EXITO).contains(o.getEstado());
        if(casos.containsKey(o.getCapacidad())) {
            var caso=casos.get(o.getCapacidad());
            boolean original=terminadoOIncierto&&caso.consultarResultado(o.getCapacidad(),clave(referencia),actor).isPresent();
            if(OperacionHerramienta.EXITO.equals(o.getEstado())&&!original)
                throw new IllegalStateException("No se localiza el comando de un resultado confirmado.");
            if(!original)caso.exigirLectura(o.getCapacidad(),datosCaso(o.getContenido()),actor);
        } else if(ORIGEN_FISICO.equals(o.getCapacidad())) {
            boolean original=false;
            if(terminadoOIncierto) {
                original=origenFisico.consultarResultadoOrigen(clave(referencia),actor).isPresent();
                if(OperacionHerramienta.EXITO.equals(o.getEstado())&&!original)
                    throw new IllegalStateException("No se localiza el comando de un resultado confirmado.");
            }
            if(!original) {
                var d=datosOrigen(o.getContenido());
                propiedades.capacidadSobre(d.idMatriz(),null,actor);propiedades.capacidadSobre(d.idUnidad(),null,actor);
            }
        } else if(terminadoOIncierto) {
            var conocido=motor.consultarResultado(o.getIdBorrador(),clave(referencia),actor);
            if(OperacionHerramienta.EXITO.equals(o.getEstado())&&conocido.estado()!=MotorDeCaptura.EstadoDelResultado.EXITO)
                throw new IllegalStateException("No se localiza el comando de un resultado confirmado.");
        } else motor.capacidadSobre(o.getIdBorrador(),null,null,actor);
        String revision=pendiente?null:"ACTUACION_NO_ADMITIDA",confirmacion=null;
        if(pendiente) {
            try { comprobarContenido(o.getCapacidad(),o.getContenido(),o.getIdBorrador(),o.getHuella(),actor,false); }
            catch(AccesoNoAutorizadoException denegada) { revision="PERMISO_INSUFICIENTE";confirmacion=revision; }
            catch(CapacidadDeshabilitadaException deshabilitada) { revision="CAPACIDAD_DESHABILITADA";confirmacion=revision; }
            catch(ConflictoException cambiado) { revision="CONTEXTO_CAMBIADO";confirmacion=revision; }
            catch(ReglaNegocioException condicion) { revision="CONDICION_NO_CUMPLIDA";confirmacion=revision; }
        }
        return new CapacidadSobreActuacion(o.getCapacidad(),o.getEstado(),revision,confirmacion);
    }

    private String instantanea(Actor actor,long id,boolean bloquear) {
        var b=bloquear?borradores.bloquear(actor.idOrganizacion(),id)
                .orElseThrow(() -> new NoEncontradoException("Captura no localizada.")):null;
        var cap=motor.capacidadSobre(id,null,null,actor);
        if (!cap.editable() || !cap.listoParaEjecutar())
            throw new ReglaNegocioException("La captura todavía no admite esta actuación.");
        var e=motor.consultar(id,actor);
        var contenido=new LinkedHashMap<String,Object>();
        contenido.put("contrato",com.controllocal.service.capacidades.CapacidadesService.VERSION);
        contenido.put("idBorrador",id);
        contenido.put("versionCaptura",b==null?e.version():b.getVersion());contenido.put("codigoCaptura",e.codigo());
        contenido.put("intencion",e.intencion());contenido.put("efecto","REGISTRAR_PROPIEDAD");
        contenido.put("datos",e.conocido());
        contenido.put("contexto",e.contexto());
        contenido.put("metadatos",e.metadatos());
        contenido.put("definicionCore",mapa(motor.definicion(e.intencion(),
                Objects.toString(e.conocido().get("tipoPropiedad"),null),String.join(",",cap.operaciones()),
                e.contexto()==null?null:e.contexto().jurisdiccion(),actor)));
        return documentos.objetoCanonico(contenido);
    }
    private void comprobarContenido(String capacidad,String guardado,Long borrador,String huella,Actor actor,boolean bloquearCaptura) {
        String contenido=casos.containsKey(capacidad)?instantaneaCaso(capacidad,datosCaso(guardado),actor)
                :ORIGEN_FISICO.equals(capacidad)?instantaneaOrigen(actor,datosOrigen(guardado)):instantanea(actor,borrador,bloquearCaptura);
        if (!iguales(huella,Idempotencia.huellaExacta(contenido)))
            throw new ConflictoException("El contenido o la definición cambió; prepara otra revisión.");
    }
    private String instantaneaOrigen(Actor actor,UnidadRelacionadaService.DatosOrigen d) {
        if(d==null||d.idMatriz()==null||d.idUnidad()==null||d.versionMatrizEsperada()==null||d.versionUnidadEsperada()==null)
            throw new ReglaNegocioException("Se requieren ambas propiedades y sus versiones físicas observadas.");
        var actual=origenFisico.capacidadSobreOrigen(d.idMatriz(),d.idUnidad(),actor);
        if(actual.versionMatriz()!=d.versionMatrizEsperada()||actual.versionUnidad()!=d.versionUnidadEsperada())
            throw new ConflictoException("Las propiedades cambiaron; consulta sus versiones antes de preparar otra revisión.");
        return documentos.objetoCanonico(Map.of("contrato",com.controllocal.service.capacidades.CapacidadesService.VERSION,
                "efecto","REGISTRAR_ORIGEN_FISICO_UNIDAD","datosOrigen",mapa(d)));
    }
    private UnidadRelacionadaService.DatosOrigen datosOrigen(OperacionHerramienta o) {
        return datosOrigen(o.getContenido());
    }
    private UnidadRelacionadaService.DatosOrigen datosOrigen(String contenido) {
        return json.convertValue(documentos.comoMapa(contenido).get("datosOrigen"),UnidadRelacionadaService.DatosOrigen.class);
    }
    /** El efecto RF014 reentra la cuenta WRITE; tomarla antes de sesión/operación evita upgrades. */
    private void bloquearCuenta(Actor actor) {
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
    }
    private OperacionHerramienta cargar(Actor actor,String ref) {
        if (ref==null || !ref.matches("[a-f0-9-]{36}")) throw new NoEncontradoException("Actuación no localizada.");
        var o=operaciones.bloquear(actor.idOrganizacion(),ref)
                .orElseThrow(() -> new NoEncontradoException("Actuación no localizada."));
        exigirActor(o,actor);
        exigirIntegridad(o.getCapacidad(),o.getIdBorrador(),o.getHuella(),o.getContenido());
        return o;
    }
    private void exigirIntegridad(String capacidad,Long borrador,String huella,String contenido) {
        if((!Set.of("confirmar_captura",ORIGEN_FISICO).contains(capacidad)&&!casos.containsKey(capacidad))
                || (!"confirmar_captura".equals(capacidad)!=(borrador==null)))
            throw new IllegalStateException("La actuación no conserva un destino compatible con su capacidad.");
        if (!iguales(huella,Idempotencia.huellaExacta(contenido)))
            throw new IllegalStateException("El contenido durable no coincide con su huella.");
    }
    private static void exigirActor(OperacionHerramienta o,Actor actor) {
        exigirActor(o.getIdPersona(),o.getIdPersonaRol(),o.getRolEfectivo(),actor);
    }
    private static void exigirActor(long persona,long rol,String banda,Actor actor) {
        if (persona!=actor.idPersona() || rol!=actor.idRolOperativo()
                || !banda.equals(actor.rolEfectivo())) throw new AccesoNoAutorizadoException();
    }
    private void exigir(Actor a,ContextoSesion sesion,String capacidad) {
        exigirSesion(a,sesion);
        var d=adaptadores.de(capacidad,a);
        if (!d.reconocido() || !d.permitido()) throw new AccesoNoAutorizadoException();
        if (!d.habilitado()) throw new CapacidadDeshabilitadaException();
    }
    private void exigirSesion(Actor a,ContextoSesion sesion) {
        if (a==null || (!a.esAgente()&&!a.esBroker())) throw new AccesoNoAutorizadoException();
        if (sesion==null || sesion.identidad()==null || !sesion.identidad().matches("[a-f0-9]{64}")
                || sesion.expiraEnEpochSeconds()<=Instant.now().getEpochSecond()
                || sesion.referenciaSesion()==null || !sesion.referenciaSesion().matches("[A-Za-z0-9_-]{43}")
                || sesion.revisionContexto()<0)
            throw new AccesoNoAutorizadoException();
    }
    private void exigirContexto(Actor actor,ContextoSesion sesion) {
        try {
            var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(actual).equals(sesion)) throw new AccesoNoAutorizadoException();
        }
        catch (CredencialesInvalidasException e) { throw new AccesoNoAutorizadoException(); }
    }
    private void exigirVinculo(OperacionHerramienta o,ContextoSesion s) {
        if (!iguales(o.getIdentidadSesion(),s.identidad())) throw new AccesoNoAutorizadoException();
    }
    private void exigirVigente(OperacionHerramienta o) {
        if (!o.getCaducaEn().isAfter(Instant.now())) throw new ConflictoException("La revisión caducó.");
    }
    private void rechazar(Actor actor,String ref,String causa) {
        tx.executeWithoutResult(s -> {
            var o=cargar(actor,ref);
            if (Set.of(OperacionHerramienta.INICIADA,OperacionHerramienta.INCIERTA).contains(o.getEstado()))
                o.rechazar(causa);
        });
    }
    private Propuesta propuesta(OperacionHerramienta o) {
        return new Propuesta(o.getReferencia(),o.getCapacidad(),o.getVersion(),o.getHuella(),
                o.getCaducaEn().getEpochSecond(),documentos.comoMapa(o.getContenido()));
    }
    private Resultado resultado(OperacionHerramienta o) {
        String estado=switch(o.getEstado()) {
            case OperacionHerramienta.PREPARADA -> "PREPARADA";
            case OperacionHerramienta.EXITO -> "EXITO";
            case OperacionHerramienta.RECHAZADA -> "RECHAZADO";
            default -> "EN_VERIFICACION";
        };
        return new Resultado(o.getReferencia(),estado,propuesta(o),
                o.getResultado()==null?null:documentos.comoMapa(o.getResultado()),o.getMotivo());
    }
    private static String clave(String referencia) { return "actuacion:"+referencia; }
    private Map<String,Object> mapa(Object valor) { return json.convertValue(valor,new TypeReference<>() { }); }
    private static boolean iguales(String a,String b) {
        return a!=null && b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    }
}
