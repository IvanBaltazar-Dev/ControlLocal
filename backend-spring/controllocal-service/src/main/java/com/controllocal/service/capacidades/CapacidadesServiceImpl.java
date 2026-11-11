package com.controllocal.service.capacidades;

import com.controllocal.service.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.soporte.Alcances;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;
import static com.controllocal.service.capacidades.CapacidadesService.Estado.*;

/** CAT-E40: bandas de la puerta real y contexto leído con las guardas existentes. Sin escrituras. */
@Service
public class CapacidadesServiceImpl implements CapacidadesService {
    // Contrato y definiciones consultables para gobierno/configuración. No
    // describen acceso a cartera, captura de hechos ni actuación comercial.
    private static final Set<String> DEFINICIONES = Set.of(
            "consultar_capacidades", "catalogo_de_captura", "definicion_de_interacciones","definicion_de_metas");
    private final MetaComercialService metas;
    private final RegistroDeCapacidades registro;
    private final com.controllocal.service.encargo.EncargoService ciclo;
    private final UnidadRelacionadaService origenFisico;
    private final AdaptadoresDeCapacidad adaptadores;
    private final PropiedadUniversalService propiedades;
    private final CaptacionService encargos;
    private final ProspeccionService prospecciones;
    private final com.controllocal.service.actividad.ReasignacionDeProspeccion gobiernoProspeccion;
    private final ClienteService clientes;
    private final OportunidadService oportunidades;
    private final SolicitudService solicitudes;
    private final ContratoService contratos;
    private final MotorDeCaptura captura;
    private final CoincidenciaService coincidencias;
    private final InteraccionService interacciones;
    private final OfertaService ofertas;
    private final VisitaService visitas;
    private final com.controllocal.service.actuacion.ActuacionesService actuaciones;
    private final RequerimientoService demandas;
    private final com.controllocal.service.colaboracion.ColaboracionService colaboraciones;
    private final com.controllocal.service.network.NetworkService network;
    private final org.springframework.transaction.support.TransactionTemplate lecturaDemanda;
    private final com.controllocal.service.encargo.AccesoEncargo autoridadEncargos;
    private final com.controllocal.service.documentos.PiezasComercialesService piezas;
    private final com.controllocal.service.materialia.MaterialIaService materialIa;
    private final com.controllocal.service.materialia.ResultadosMaterialIa resultadosMaterial;
    private final com.controllocal.service.planes.DerechosDeInformesC7 derechosInformes;
    private static final Set<String> DECISIONES_C5=Set.of("evaluar_expediente_operacion",
            "confirmar_hito_contractual","rectificar_hito_contractual","registrar_cobro",
            "asignar_reparto_comision","autorizar_borrador_adenda","reprogramar_gestion_cobro","concluir_gestion_cobro");

    public CapacidadesServiceImpl(RegistroDeCapacidades registro, AdaptadoresDeCapacidad adaptadores,
            PropiedadUniversalService propiedades, CaptacionService encargos, ProspeccionService prospecciones,
            ClienteService clientes, OportunidadService oportunidades, SolicitudService solicitudes,
            ContratoService contratos, MotorDeCaptura captura, CoincidenciaService coincidencias,
            InteraccionService interacciones, OfertaService ofertas,
            com.controllocal.service.actuacion.ActuacionesService actuaciones,
            com.controllocal.service.encargo.EncargoService ciclo, UnidadRelacionadaService origenFisico,
            RequerimientoService demandas,com.controllocal.service.colaboracion.ColaboracionService colaboraciones,
            com.controllocal.service.network.NetworkService network,org.springframework.transaction.PlatformTransactionManager transacciones,
            VisitaService visitas,com.controllocal.service.encargo.AccesoEncargo autoridadEncargos,
            com.controllocal.service.documentos.PiezasComercialesService piezas,
            com.controllocal.service.materialia.MaterialIaService materialIa,
            com.controllocal.service.materialia.ResultadosMaterialIa resultadosMaterial,com.controllocal.service.actividad.ReasignacionDeProspeccion gobiernoProspeccion,
            com.controllocal.service.planes.DerechosDeInformesC7 derechosInformes,MetaComercialService metas) {
        this.metas=metas;
        this.derechosInformes=derechosInformes;
        this.gobiernoProspeccion=gobiernoProspeccion;
        this.materialIa=materialIa;
        this.resultadosMaterial=resultadosMaterial;
        this.piezas=piezas;
        this.autoridadEncargos=autoridadEncargos;
        this.visitas=visitas;
        this.demandas=demandas;this.colaboraciones=colaboraciones;this.network=network;
        lecturaDemanda=new org.springframework.transaction.support.TransactionTemplate(transacciones);
        lecturaDemanda.setReadOnly(true);
        lecturaDemanda.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ciclo=ciclo;
        this.origenFisico=origenFisico;
        this.registro = registro; this.adaptadores = adaptadores; this.propiedades = propiedades;
        this.encargos = encargos; this.prospecciones = prospecciones; this.clientes = clientes;
        this.oportunidades = oportunidades; this.solicitudes = solicitudes; this.contratos = contratos;
        this.captura = captura; this.coincidencias = coincidencias; this.interacciones = interacciones;
        this.ofertas = ofertas; this.actuaciones=actuaciones;
    }

    @Override
    // Orquesta DTOs: cada lector conserva su TX. Una denegación esperada no envenena una TX envolvente.
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public CatalogoCapacidades consultar(Consulta entrada, Actor actor) {
        if (actor == null || actor.idOrganizacion() <= 0 || actor.idPersona() <= 0
                || !Set.of(Actor.AGENTE, Actor.BROKER, Actor.TENANT_ADMIN).contains(actor.rolEfectivo())) {
            throw new AccesoNoAutorizadoException();
        }
        Consulta q = normalizar(entrada == null ? Consulta.general() : entrada);
        boolean operador;
        try {
            Alcances.exigirOperador(actor);
            operador = true;
        } catch (AccesoNoAutorizadoException gobierno) {
            operador = false;
        }
        List<RegistroDeCapacidades.Definicion> seleccionadas = q.idCore() == null ? registro.todas()
                : List.of(registro.de(q.idCore()));
        if (q.idCore() == null && q.recurso() != null) {
            seleccionadas = seleccionadas.stream().filter(d -> d.recursos().contains(q.recurso())).toList();
        }
        VistaConsulta vista = new VistaConsulta(q, actor);
        List<DescriptorCapacidad> resultado = new ArrayList<>();
        for (var d : seleccionadas) {
            AdaptadoresDeCapacidad.Disponibilidad puerta = adaptadores.de(d.idCore(), actor);
            Estado estado; String motivo = null;
            Contexto contexto = contexto(actor, q, null, null);
            if (!puerta.reconocido()) {
                estado = DESHABILITADA; motivo = "ADAPTADOR_NO_RECONOCIDO";
            } else if (("declarar_canal_colaboracion".equals(d.idCore())&&!actor.esAgente())
                    || !puerta.permitido() || (!operador && !DEFINICIONES.contains(d.idCore())
                    && !(Actor.TENANT_ADMIN.equals(actor.rolEfectivo()) && Set.of("GOBIERNO_CATALOGO","GOBIERNO_REASIGNACION").contains(d.familia())))) {
                estado = PERMISO_INSUFICIENTE; motivo = "PERMISO_INSUFICIENTE";
            } else if (!puerta.habilitado()) {
                estado = DESHABILITADA; motivo = "CAPACIDAD_DESHABILITADA";
            } else if(Set.of("establecer_meta","decidir_propuesta_meta").contains(d.idCore())&&!vista.puedeDecidirMetas()){
                estado=PERMISO_INSUFICIENTE;motivo="PERMISO_INSUFICIENTE";
            } else if("proponer_meta".equals(d.idCore())&&vista.puedeDecidirMetas()){
                estado=PERMISO_INSUFICIENTE;motivo="PERMISO_INSUFICIENTE";
            } else if(Set.of("establecer_meta","decidir_propuesta_meta").contains(d.idCore())&&q.recurso()==null&&q.referencia()==null){
                estado=q.operacion()==null&&q.etapa()==null&&q.atributo()==null?HABILITADA:DESHABILITADA;
                if(estado==DESHABILITADA)motivo="CONTEXTO_NO_APLICABLE";
            } else if(vista.motivoConfiguracionMaterial(d.idCore())!=null) {
                estado=DESHABILITADA;motivo=vista.motivoConfiguracionMaterial(d.idCore());
            } else if (d.recursos().isEmpty()) {
                estado = q.recurso() == null && q.operacion() == null && q.etapa() == null
                        ? HABILITADA : DESHABILITADA;
                if (estado == DESHABILITADA) motivo = "CONTEXTO_NO_APLICABLE";
                else if (q.atributo() != null && !admiteEntrada(d, q.atributo())) {
                    estado = DESHABILITADA; motivo = "ATRIBUTO_NO_APLICABLE";
                }
            } else if ("registrar_interaccion".equals(d.idCore()) && q.recurso()==null && q.referencia()==null) {
                // C4 permite declarar una comunicación propia sin inventar un recurso relacionado.
                estado=q.operacion()==null&&q.etapa()==null?HABILITADA:DESHABILITADA;
                if(estado==DESHABILITADA)motivo="CONTEXTO_NO_APLICABLE";
                else if(q.atributo()!=null&&!admiteEntrada(d,q.atributo())) {estado=DESHABILITADA;motivo="ATRIBUTO_NO_APLICABLE";}
            } else if (q.recurso() == null || q.referencia() == null) {
                estado = CONTEXTO_REQUERIDO; motivo = "CONTEXTO_REQUERIDO";
            } else if (!d.recursos().contains(q.recurso())) {
                estado = DESHABILITADA; motivo = "CONTEXTO_NO_APLICABLE";
            } else if ("registrar_origen_fisico_unidad".equals(d.idCore()) && "PROPIEDAD".equals(q.recurso()) && q.referenciaRelacionada()==null) {
                estado = CONTEXTO_REQUERIDO; motivo = "CONTEXTO_REQUERIDO";
            } else if (q.referenciaRelacionada()!=null && !"registrar_origen_fisico_unidad".equals(d.idCore())) {
                estado = DESHABILITADA; motivo = "CONTEXTO_NO_APLICABLE";
            } else {
                try {
                    Object ficha = vista.ficha(d);
                    String etapa = etapa(ficha);
                    String operacion = operacion(ficha, q);
                    contexto = contexto(actor, q, operacion, etapa);
                    if (q.etapa() != null && !q.etapa().equals(etapa)) {
                        estado = DESHABILITADA; motivo = "ETAPA_CAMBIADA";
                    } else if (q.operacion() != null && !q.operacion().equals(operacion)) {
                        estado = DESHABILITADA; motivo = "OPERACION_NO_APLICABLE";
                    } else {
                        motivo = vista.motivo(d.idCore(),ficha);
                        estado = motivo == null ? HABILITADA
                                : "PERMISO_INSUFICIENTE".equals(motivo) ? PERMISO_INSUFICIENTE
                                : Set.of("TIPO_DE_PROPIEDAD_REQUERIDO", "OPERACION_REQUERIDA").contains(motivo)
                                  ? CONTEXTO_REQUERIDO : DESHABILITADA;
                    }
                } catch (AccesoNoAutorizadoException denegada) {
                    estado = PERMISO_INSUFICIENTE; motivo = "PERMISO_INSUFICIENTE";
                } catch (CapacidadDeshabilitadaException deshabilitada) {
                    estado = DESHABILITADA; motivo = "CAPACIDAD_DESHABILITADA";
                } catch (ReglaNegocioException condicion) {
                    estado = DESHABILITADA; motivo = "CONDICION_NO_CUMPLIDA";
                } catch (ConflictoException cambiado) {
                    estado = DESHABILITADA; motivo = "CONTEXTO_CAMBIADO";
                }
                // NoEncontrado conserva su 404. Fallos de repositorio/contrato siguen siendo técnicos.
            }
            resultado.add(new DescriptorCapacidad(d.idCore(), d.familia(), d.efecto(), d.interaccion(),
                    d.esquema(), VERSION, estado, motivo, contexto));
        }
        return new CatalogoCapacidades(VERSION, resultado);
    }

    private String motivoContextual(String id, Consulta q, Object ficha, Actor actor) {
        if(ficha instanceof MetaComercialService.CapacidadMeta m&&"decidir_propuesta_meta".equals(id)){
            if(!m.puedeDecidir())return "PERMISO_INSUFICIENTE";
            if(m.reemplazadaPor()!=null||!"E".equals(m.estado()))return "PROPUESTA_NO_PENDIENTE";
        }
        if("obtener_pieza_para_difusion".equals(id)
                &&ficha instanceof com.controllocal.service.documentos.PiezasComercialesService.Estado p
                &&p.desactualizada())return "PIEZA_DESACTUALIZADA";
        if("descargar_archivo_pieza".equals(id)
                &&ficha instanceof com.controllocal.service.documentos.PiezasComercialesService.Estado p
                &&p.archivoPrivado()==null&&p.archivoDifusion()==null)return "ARCHIVO_NO_DISPONIBLE";
        if(ficha instanceof com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion a) {
            if(esEfectoDeActuacion(id)&&!id.equals(a.capacidad()))
                return "EFECTO_DE_ACTUACION_NO_APLICABLE";
            if("revisar_actuacion".equals(id)&&a.motivoRevision()!=null)return a.motivoRevision();
            if(esEfectoDeActuacion(id)&&a.motivoConfirmacion()!=null)return a.motivoConfirmacion();
        }
        if (Set.of("buscar_coincidencias", "explicar_match").contains(id)) {
            coincidencias.verificarAcceso(origenMatching(q.recurso()),numero(q.referencia()),null,actor);
        }
        if ("editar_propiedad".equals(id)) {
            var propiedad = (PropiedadUniversalService.CapacidadSobrePropiedad) ficha;
            if (!propiedad.puedeEditar()) {
                return "PERMISO_INSUFICIENTE";
            }
        }
        if (q.atributo() != null) {
            if (ficha instanceof PropiedadUniversalService.CapacidadSobrePropiedad p) {
                if(!Set.of("editar_propiedad","estado_de_propiedad").contains(id)) {
                    if(!admiteEntrada(registro.de(id),q.atributo()))return "ATRIBUTO_NO_APLICABLE";
                } else if (!p.atributoReconocido()) return "ATRIBUTO_NO_APLICABLE";
                if ("editar_propiedad".equals(id) && !p.atributoEditable()) return "ATRIBUTO_NO_EDITABLE";
            } else if (ficha instanceof MotorDeCaptura.CapacidadSobreCaptura b) {
                if (b.motivoAtributo() != null) return b.motivoAtributo();
            } else if (!admiteEntrada(registro.de(id), q.atributo())) return "ATRIBUTO_NO_APLICABLE";
        }
        if (ficha instanceof MotorDeCaptura.CapacidadSobreCaptura b) {
            if (Set.of("responder_captura","preparar_actuacion_captura","descartar_captura","posponer_revision_captura").contains(id)
                    && !b.editable()) return "CAPTURA_NO_EDITABLE";
            if ("preparar_actuacion_captura".equals(id) && !b.listoParaEjecutar()) {
                return "CAPTURA_INCOMPLETA";
            }
        }
        if("invitar_colaboracion_encargo".equals(id)&&ficha instanceof com.controllocal.service.encargo.EncargoService.Ficha e){
                if(!actor.esAgente()||e.responsable()!=actor.idRolOperativo())return "PERMISO_INSUFICIENTE";
                if(!e.vigencia().habilitado())return "ENCARGO_NO_HABILITADO";
            }
            if(ficha instanceof RequerimientoService.FichaRequerimiento r
                &&Set.of("editar_demanda","retirar_finalizar_demanda","solicitar_colaboracion").contains(id)
                &&!"A".equals(r.estado()))return "DEMANDA_NO_ABIERTA";
        if(ficha instanceof com.controllocal.service.colaboracion.ColaboracionService.MetadatosAcuerdo c) {
            if(Set.of("decidir_colaboracion","revocar_colaboracion").contains(id)&&actor.esAgente()&&!c.parteActual().individual())
                return "PERMISO_INSUFICIENTE";
            if("decidir_colaboracion".equals(id)&&!"P".equals(c.estado()))return "SOLICITUD_NO_PENDIENTE";
            if("revocar_colaboracion".equals(id)&&(c.concesion()==null||c.revocadaEn()!=null))return "CONCESION_NO_REVOCABLE";
        }
        if(ficha instanceof com.controllocal.service.network.NetworkService.Capacidad n) {
            boolean autoridad=switch(id) {
                case "solicitar_participacion_network" -> n.autoridadSolicitar();
                case "decidir_participacion_network" -> n.autoridadDecidir();
                case "desactivar_participacion_network" -> n.autoridadDesactivarDirectamente();
                default -> true;
            };
            if(!autoridad)return "PERMISO_INSUFICIENTE";
            boolean admite=switch(id) {
                case "solicitar_participacion_network" -> n.solicitar();
                case "decidir_participacion_network" -> n.decidir();
                case "desactivar_participacion_network" -> n.desactivarDirectamente();
                default -> n.consultar();
            };
            if(!admite)return "ACTUACION_NO_ADMITIDA";
        }
        if (ficha instanceof com.controllocal.service.encargo.EncargoService.Ficha e) {
            if("PIEZA".equals(registro.de(id).familia())
                    &&(!actor.esAgente()||!Objects.equals(e.responsable(),actor.idRolOperativo())))
                return "PERMISO_INSUFICIENTE";
            if("PUBLICACION".equals(registro.de(id).familia())
                    &&registro.de(id).efecto()!=CapacidadesService.Efecto.LECTURA
                    &&(!actor.esAgente()||!Objects.equals(e.responsable(),actor.idRolOperativo())))
                return "PERMISO_INSUFICIENTE";
            if(Set.of("cargar_archivo_contextual","adjuntar_reutilizar_documento").contains(id)
                    &&(!actor.esAgente()||!java.util.Objects.equals(e.responsable(),actor.idRolOperativo())))
                return "PERMISO_INSUFICIENTE";
            boolean permite = switch(id) {
                case "editar_condiciones_encargo", "declarar_composicion_encargo" -> e.facultades().editar();
                case "regenerar_documento_encargo" -> e.facultades().prepararDocumento();
                case "presentar_peticion_encargo" -> e.facultades().presentar();
                case "solicitar_fin_encargo" -> e.facultades().solicitarFin();
                case "cerrar_encargo" -> e.facultades().finalizar();
                default -> true;
            };
            if (!permite) return "ACTUACION_NO_ADMITIDA";
        }
        if (ficha instanceof com.controllocal.service.encargo.EncargoService.Peticion p) {
            if ("decidir_captacion".equals(id) && !p.facultades().decidir()) return "PETICION_NO_DECIDIBLE";
            if ("reenviar_peticion_encargo".equals(id) && !p.facultades().reenviar()) return "PETICION_NO_REENVIABLE";
        }
        if (ficha instanceof com.controllocal.service.encargo.EncargoService.SolicitudFin f) {
            if ("cancelar_solicitud_fin_encargo".equals(id) && !f.facultades().cancelar()) return "SOLICITUD_NO_CANCELABLE";
            if ("decidir_solicitud_fin_encargo".equals(id) && !f.facultades().decidir()) return "SOLICITUD_NO_DECIDIBLE";
        }
        if ("presentar_oferta".equals(id) && ficha instanceof OportunidadService.FichaOportunidad o) {
            if (!ofertas.capacidades(o.id(), actor).puedeOfertar()) return "MESA_NO_ADMITE_OFERTA";
        }
        return null;
    }

    /** Caché por proyección necesaria, sólo durante esta petición y actor. */
    private final class VistaConsulta {
        private final Consulta q; private final Actor actor;
        private final Map<String,Object> fichas = new HashMap<>();
        private final Map<String,RuntimeException> fallos = new HashMap<>();
        private final Map<Long,Boolean> decisionesEncargo = new HashMap<>();
        private Boolean composicionHabilitada;
        private Boolean programasPersonales;
        private Boolean autoridadMetas;
        private List<com.controllocal.service.materialia.MaterialIaService.Disponibilidad> disponibilidadMaterial;
        VistaConsulta(Consulta q, Actor actor) { this.q=q; this.actor=actor; }
        boolean puedeDecidirMetas(){if(autoridadMetas==null)autoridadMetas=metas.puedeDecidir(actor);return autoridadMetas;}
        String motivoConfiguracionMaterial(String id){
            if(Set.of("configurar_programa_personal","cambiar_estado_programa_personal","consultar_resultado_programa_personal").contains(id)) {
                if(programasPersonales==null)programasPersonales=derechosInformes.actuales(actor,java.time.Instant.now()).programasPersonales();
                if(!programasPersonales)return "PLAN_PRO_REQUERIDO";
            }
            if(!Set.of("preparar_material_ia","solicitar_material_ia").contains(id))return null;
            if(disponibilidadMaterial==null)disponibilidadMaterial=Arrays.stream(com.controllocal.service.materialia.MaterialIaService.Accion.values())
                    .map(a->Objects.requireNonNull(materialIa.disponibilidad(a),"Falta el estado operativo Core de material IA.")).toList();
            boolean disponible=disponibilidadMaterial.stream().anyMatch(d->"preparar_material_ia".equals(id)?d.preparacionLocal():d.solicitud());
            return disponible?null:"MATERIAL_IA_NO_DISPONIBLE";
        }
        String motivo(String id,Object ficha) {
            if(DECISIONES_C5.contains(id)&&ficha instanceof com.controllocal.service.encargo.EncargoService.Ficha e
                    &&!decisionesEncargo.computeIfAbsent(e.id(),n->autoridadEncargos.puedeDecidir(n,actor)))
                return "PERMISO_INSUFICIENTE";
            String motivo=motivoContextual(id,q,ficha,actor);
            if(motivo!=null)return motivo;
            if(ficha instanceof com.controllocal.service.documentos.PiezasComercialesService.Estado p
                    &&("previsualizar_pieza_comercial".equals(id)&&p.archivoPrivado()==null
                       ||"obtener_pieza_para_difusion".equals(id)&&p.archivoDifusion()==null)){
                if(composicionHabilitada==null)composicionHabilitada=piezas.composicionHabilitada();
                if(!composicionHabilitada)return "COMPOSICION_NO_CONFIGURADA";
            }
            return null;
        }
        Object ficha(RegistroDeCapacidades.Definicion d) {
            String proyeccion = "registrar_interaccion".equals(d.idCore()) ? "CONTEXTO_INTERACCION"
                    : Set.of("descargar_archivo_material_ia","guardar_alternativa_material_ia","descartar_temporal_material_ia","eliminar_alternativa_material_ia").contains(d.idCore()) ? "ARTEFACTO_IA"
                    : Set.of("MATCHING","DISPONIBILIDAD").contains(d.familia()) ? "MATCHING"
                    :"NETWORK".equals(d.familia()) ? "NETWORK"
                    :"invitar_colaboracion_encargo".equals(d.idCore()) ? "CICLO"
                    :"PROPIEDAD".equals(q.recurso())&&"registrar_origen_fisico_unidad".equals(d.idCore()) ? "ORIGEN_FISICO"
                    : "GOBIERNO_REASIGNACION".equals(d.familia()) ? "REASIGNACION"
                    : Set.of("ENCARGO","PUBLICACION","PIEZA","MATERIAL_IA","TRATAMIENTO_IA").contains(d.familia()) ? "CICLO"
                    : q.atributo()!=null&&Set.of("editar_propiedad","estado_de_propiedad").contains(d.idCore()) ? "ATRIBUTO_FISICO" : "BASE";
            // Estos casos ejecutan exactamente el mismo lector, con el mismo actor/recurso/atributo.
            if("ACTUACION".equals(q.recurso())||"PROPIEDAD".equals(q.recurso())&&Set.of("MATCHING","CICLO").contains(proyeccion))proyeccion="BASE";
            if(fallos.containsKey(proyeccion))throw fallos.get(proyeccion);
            if(!fichas.containsKey(proyeccion)) {
                try { fichas.put(proyeccion,cargar(q,actor,proyeccion)); }
                catch(RuntimeException e) { fallos.put(proyeccion,e); throw e; }
            }
            return fichas.get(proyeccion);
        }
    }

    private Object cargar(Consulta q, Actor actor, String proyeccion) {
        if("CONTEXTO_INTERACCION".equals(proyeccion)) {
            interacciones.verificarContextoRegistro(q.recurso(),numero(q.referencia()),actor);
            return new ContextoInteraccionPermitido();
        }
        return switch (q.recurso()) {
            case "PROPUESTA_META" -> metas.capacidadSobrePropuesta(numero(q.referencia()),actor);
            case "MATERIAL_IA" -> {
                if("ARTEFACTO_IA".equals(proyeccion)){
                    var version=resultadosMaterial.version(uuid(q.referencia()),actor);
                    if(version==null||!uuid(q.referencia()).equals(version.operacion()))throw new IllegalStateException("La lectura no devolvió el artefacto solicitado.");
                }else materialIa.exigirLectura(uuid(q.referencia()),actor);
                yield new ContextoMaterialIaPermitido();
            }
            case "MATERIAL_IA_FOTO" -> {
                UUID original=uuid(q.referencia());var selecciones=resultadosMaterial.consultarSelecciones(List.of(original),actor);
                if(selecciones==null||selecciones.size()!=1||!original.equals(selecciones.getFirst().original()))
                    throw new IllegalStateException("La lectura no devolvió el original autorizado.");
                yield new ContextoMaterialIaPermitido();
            }
            case "PIEZA" -> piezas.consultarEstado(uuid(q.referencia()),actor);
            case "INTERACCION" -> interacciones.obtener(numero(q.referencia()),actor);
            case "VISITA" -> visitas.obtener(numero(q.referencia()),actor);
            case "ACTUACION" -> actuaciones.capacidadSobre(q.referencia(),actor);
            case "DEMANDA" -> "MATCHING".equals(proyeccion)
                    ? lecturaDemanda.execute(tx->demandas.contextoDisponibilidad(numero(q.referencia()),actor,false))
                    :demandas.buscar(numero(q.referencia()),actor);
            case "SOLICITUD_COLABORACION" -> colaboraciones.consultarMetadatos(q.referencia(),actor);
            case "CONCESION_COLABORACION" -> colaboraciones.consultarMetadatosPorConcesion(q.referencia(),actor);
            case "SOLICITUD_NETWORK" -> network.capacidadSobreSolicitud(q.referencia(),actor);
            case "PROPIEDAD" -> "ORIGEN_FISICO".equals(proyeccion)
                    ? origenFisico.capacidadSobreOrigen(numero(q.referencia()),numero(q.referenciaRelacionada()),actor)
                    : propiedades.capacidadSobre(numero(q.referencia()),
                        "ATRIBUTO_FISICO".equals(proyeccion)?q.atributo():null,actor);
            case "CAPTURA" -> captura.capacidadSobre(numero(q.referencia()), q.operacion(), q.atributo(), actor);
            case "ENCARGO" -> {
                if("NETWORK".equals(proyeccion))yield network.capacidadSobre(numero(q.referencia()),actor);
                if("REASIGNACION".equals(proyeccion)) yield encargos.consultarReasignable(numero(q.referencia()),actor);
                if("CICLO".equals(proyeccion)) yield ciclo.consultar(numero(q.referencia()),actor);
                yield q.referencia().matches("[0-9]+") ? encargos.obtener(numero(q.referencia()),actor)
                        : encargos.obtenerPorCodigo(q.referencia(),actor);
            }
            case "PETICION_ENCARGO" -> ciclo.consultarPeticion(numero(q.referencia()),actor);
            case "SOLICITUD_FIN_ENCARGO" -> ciclo.consultarSolicitudFin(numero(q.referencia()),actor);
            case "DOCUMENTO_ENCARGO" -> ciclo.consultarDocumento(numero(q.referencia()),actor);
            case "PROSPECCION" -> "REASIGNACION".equals(proyeccion)?gobiernoProspeccion.consultar(numero(q.referencia()),actor):prospecciones.obtener(numero(q.referencia()), actor);
            case "CLIENTE" -> clientes.obtener(numero(q.referencia()), actor);
            case "OPORTUNIDAD" -> oportunidades.obtener(numero(q.referencia()), actor);
            case "SOLICITUD" -> solicitudes.obtener(numero(q.referencia()), actor);
            case "CONTRATO" -> contratos.obtener(numero(q.referencia()), actor);
            default -> throw new ReglaNegocioException("Recurso de capacidad no reconocido.");
        };
    }

    private static String etapa(Object ficha) {
        return switch (ficha) {
            case MetaComercialService.CapacidadMeta m -> m.estado();
            case ContextoMaterialIaPermitido p -> null;
            case com.controllocal.service.documentos.PiezasComercialesService.Estado p -> p.estadoArchivo();
            case ContextoInteraccionPermitido p -> null;
            case InteraccionService.FichaInteraccion i -> null;
            case VisitaService.FichaVisita v -> v.estado();
            case com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion a -> a.etapa();
            case PropiedadUniversalService.CapacidadSobrePropiedad p -> p.etapa();
            case UnidadRelacionadaService.ContextoOrigen o -> null;
            case MotorDeCaptura.CapacidadSobreCaptura b -> b.etapa();
            case CaptacionService.FichaCaptacion e -> e.estado();
            case CaptacionService.EncargoReasignable e -> e.estado();
            case com.controllocal.service.encargo.EncargoService.Ficha e -> e.estado();
            case com.controllocal.service.encargo.EncargoService.Peticion e -> e.estado();
            case com.controllocal.service.encargo.EncargoService.SolicitudFin e -> e.estado();
            case com.controllocal.service.encargo.EncargoService.Documento e -> null;
            case RequerimientoService.FichaRequerimiento d -> d.estado();
            case RequerimientoService.ContextoDisponibilidadDemanda d -> d.estado();
            case com.controllocal.service.colaboracion.ColaboracionService.MetadatosAcuerdo c -> c.estado();
            case com.controllocal.service.network.NetworkService.Capacidad n -> n.estadoSolicitud()==null?n.estado():n.estadoSolicitud();
            case ProspeccionService.FichaProspeccion p -> p.estado();
            case com.controllocal.service.actividad.ReasignacionDeProspeccion.Contexto p -> null;
            case ClienteService.FichaCliente c -> c.estado();
            case OportunidadService.FichaOportunidad o -> o.estado();
            case SolicitudService.FichaSolicitud s -> s.estado();
            case ContratoService.FichaContrato c -> c.estadoContrato();
            default -> throw new IllegalStateException("La consulta no devolvió el contrato del recurso.");
        };
    }

    private record ContextoMaterialIaPermitido() { }

    private record ContextoInteraccionPermitido() { }

    private static String operacion(Object ficha, Consulta q) {
        if(ficha instanceof RequerimientoService.FichaRequerimiento d)return d.operacion();
        if(ficha instanceof RequerimientoService.ContextoDisponibilidadDemanda d)return d.operacion().name();
        if (ficha instanceof com.controllocal.service.encargo.EncargoService.Ficha e) {
            try { return com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(e.operacion()).name(); }
            catch(IllegalArgumentException corrupta) {throw new IllegalStateException("Operación del Encargo no reconocida.",corrupta);}
        }
        // Propiedad, cliente y borrador no adquieren una operación por tener un encargo cercano.
        if (ficha instanceof MotorDeCaptura.CapacidadSobreCaptura b) {
            if (q.operacion() != null && b.operaciones().contains(q.operacion())) return q.operacion();
            return b.operaciones().size() == 1 ? b.operaciones().getFirst() : null;
        }
        if (ficha instanceof CaptacionService.FichaCaptacion e && e.tipoOperacion() != null) {
            try { return com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(e.tipoOperacion()).name(); }
            catch (IllegalArgumentException corrupta) { throw new IllegalStateException("Operación del recurso no reconocida.",corrupta); }
        }
        return null;
    }

    private boolean esEfectoDeActuacion(String id) {
        return registro.de(id).recursos().contains("ACTUACION")&&!Set.of("consultar_actuacion","revisar_actuacion").contains(id);
    }
    private static CoincidenciaService.Origen origenMatching(String recurso) {
        return switch(recurso) {
            case "DEMANDA" -> CoincidenciaService.Origen.DEMANDA;
            case "ENCARGO" -> CoincidenciaService.Origen.ENCARGO;
            case "PROSPECCION" -> CoincidenciaService.Origen.PROSPECCION;
            case "CAPTURA" -> CoincidenciaService.Origen.CAPTURA;
            case "PROPIEDAD" -> CoincidenciaService.Origen.FISICO;
            default -> throw new ReglaNegocioException("Origen de coincidencias no reconocido.");
        };
    }
    private static boolean admiteEntrada(RegistroDeCapacidades.Definicion d, String atributo) {
        return d.esquema().get("properties") instanceof Map<?, ?> propiedades && propiedades.containsKey(atributo);
    }

    private static Contexto contexto(Actor actor, Consulta q, String operacion, String etapa) {
        return new Contexto(actor.idOrganizacion(), actor.idPersona(), actor.rolEfectivo(), q.recurso(),
                q.referencia(), operacion, q.atributo(), etapa, q.referenciaRelacionada());
    }

    private static Consulta normalizar(Consulta q) {
        String recurso = mayusculas(q.recurso());
        if ("CAPTACION".equals(recurso)) recurso = "ENCARGO";
        if (recurso != null && !Set.of("PROPIEDAD", "CAPTURA", "ENCARGO", "PROSPECCION", "CLIENTE", "CONTACTO", "INTERACCION", "VISITA",
                "OPORTUNIDAD", "SOLICITUD", "CONTRATO", "ACTUACION", "PETICION_ENCARGO", "SOLICITUD_FIN_ENCARGO", "DOCUMENTO_ENCARGO",
                "DEMANDA","SOLICITUD_COLABORACION","CONCESION_COLABORACION","SOLICITUD_NETWORK","PROPUESTA_META","PIEZA","MATERIAL_IA","MATERIAL_IA_FOTO").contains(recurso)) {
            throw new ReglaNegocioException("Recurso de capacidad no reconocido.");
        }
        String ref = texto(q.referencia());
        if (ref != null && recurso == null) throw new ReglaNegocioException("La referencia requiere recurso.");
        if(ref!=null&&Set.of("PIEZA","MATERIAL_IA","MATERIAL_IA_FOTO").contains(recurso))uuid(ref);
        else if (ref != null && !Set.of("ENCARGO","ACTUACION","SOLICITUD_COLABORACION","CONCESION_COLABORACION","SOLICITUD_NETWORK").contains(recurso)) numero(ref);
        String operacion = mayusculas(q.operacion());
        if (operacion != null && !Set.of("VENTA", "ALQUILER").contains(operacion)) {
            throw new ReglaNegocioException("Operación no reconocida.");
        }
        String relacionada=texto(q.referenciaRelacionada());
        if(relacionada!=null) {
            if(!"PROPIEDAD".equals(recurso) || ref==null)
                throw new ReglaNegocioException("La referencia relacionada requiere la propiedad matriz.");
            numero(relacionada);
        }
        return new Consulta(texto(q.idCore()), recurso, ref, operacion, texto(q.atributo()), mayusculas(q.etapa()), relacionada);
    }

    private static long numero(String referencia) {
        try {
            long id = Long.parseLong(referencia);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ReglaNegocioException("La referencia debe ser un identificador positivo.");
    }
    private static UUID uuid(String referencia){
        try{UUID id=UUID.fromString(referencia);if(id.toString().equalsIgnoreCase(referencia))return id;}
        catch(IllegalArgumentException|NullPointerException invalida){ }
        throw new ReglaNegocioException("La referencia de pieza debe ser un UUID válido.");
    }
    private static String texto(String valor) {
        if (valor == null || valor.isBlank()) return null;
        if (valor.length() > 128) throw new ReglaNegocioException("El contexto excede la longitud permitida.");
        return valor.trim();
    }
    private static String mayusculas(String valor) {
        String v = texto(valor); return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}
