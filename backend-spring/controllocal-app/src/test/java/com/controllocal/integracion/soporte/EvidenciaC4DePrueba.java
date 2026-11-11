package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.ComisionService;
import com.controllocal.service.formalizacion.FormalizacionService;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.OfertaService;
import com.controllocal.service.OportunidadService;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.VisitaService;
import com.controllocal.service.actuacion.ActuacionesService.Propuesta;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.encargo.EncargoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.TestInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Evidencia técnica TEST de especificación C4/C5 §10. Sólo el nombre del caso
 * cruza a los hilos hijos; no conserva actores, sesiones ni objetos de negocio.
 * El launcher/JUnit determina el éxito: un retorno de Core no acredita commit.
 */
public final class EvidenciaC4DePrueba {
    private static final InheritableThreadLocal<String> CASO = new InheritableThreadLocal<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    private EvidenciaC4DePrueba() { }

    public static void iniciar(TestInfo info) {
        // No usar displayName: puede interpolar datos de un parámetro del caso.
        CASO.set(info.getTestClass().orElseThrow().getSimpleName() + "."
                + info.getTestMethod().orElseThrow().getName());
        emitir(base("INICIO_CASO", null, null));
    }

    public static void limpiar() {
        try { emitir(base("FIN_CASO", null, null)); }
        finally { CASO.remove(); }
    }

    /** Límite del Callable de un hilo hijo: retira su nombre heredado incluso si falla. */
    public static <T> T enHilo(Callable<T> tarea) throws Exception {
        try {return tarea.call();}
        finally {CASO.remove();}
    }

    public static void actor(Actor actor) { emitir(base("ACTOR", null, actor)); }

    public static void recurso(Actor actor, String tipo, long id, Long version) {
        var fila=base("RECURSO", null, actor);
        identidad(fila,tipo,id,version);emitir(fila);
    }

    public static void accion(String accion, Actor actor, String tipo, Long id, Long version) {
        var fila=base("ESTIMULO", accion, actor);
        identidad(fila,tipo,id,version);emitir(fila);
    }

    public static void propuesta(String accion, Actor actor, Propuesta propuesta) {
        var fila=base("PROPUESTA",accion,actor);
        fila.put("propuestaRef",propuesta.referencia());
        fila.put("versionPropuesta",propuesta.version());emitir(fila);
    }

    public static void estado(String accion, Actor actor, String estadoCore) {
        var fila=base("RESULTADO_CORE",accion,actor);
        fila.put("estadoCore",codigo(estadoCore));emitir(fila);
    }

    /** Extrae sólo campos declarados abajo; nunca serializa el DTO recibido. */
    public static void resultado(String accion, Actor actor, Object dto) {
        if(CASO.get()==null)return;
        var fila=base("RETORNO_CORE",accion,actor);
        if(dto instanceof InteraccionService.FichaInteraccion f) {
            identidad(fila,"INTERACCION",f.id(),f.version());
            for(var v:f.vinculos()) {
                var vinculo=base("VINCULO_RESULTANTE",accion,actor);
                identidad(vinculo,v.tipo(),v.referencia(),v.versionReferencia());
                vinculo.put("idInteraccion",f.id());emitir(vinculo);
            }
        } else if(dto instanceof VisitaService.FichaVisita f) {
            identidad(fila,"VISITA",f.id(),f.version());fila.put("estadoRecurso",codigo(f.estado()));
        } else if(dto instanceof OportunidadService.FichaOportunidad f) {
            identidad(fila,"OPORTUNIDAD",f.id(),f.version());fila.put("estadoRecurso",codigo(f.estado()));
            fila.put("idEncargo",f.idCaptacion());
        } else if(dto instanceof OfertaService.FichaOferta f) {
            identidad(fila,"OFERTA",f.id(),f.version());fila.put("estadoRecurso",codigo(f.estado()));
            fila.put("idOportunidad",f.idOportunidad());fila.put("versionMesa",f.versionMesa());
        } else if(dto instanceof OfertaService.ResultadoNegociacion f) {
            identidad(fila,"OFERTA",f.oferta(),f.versionOferta());fila.put("estadoRecurso",codigo(f.estado()));
            fila.put("idOportunidad",f.oportunidad());fila.put("versionMesa",f.versionMesa());
            fila.put("idAcuerdo",f.acuerdoAceptado());fila.put("versionAcuerdo",f.versionAcuerdo());
        } else if(dto instanceof PropietarioService.FichaPropietario f) {
            identidad(fila,"PROPIETARIO",f.id(),null);
        } else if(dto instanceof ProspeccionService.FichaProspeccion f) {
            identidad(fila,"PROSPECCION",f.id(),null);fila.put("estadoRecurso",codigo(f.estado()));
        } else if(dto instanceof MotorDeCaptura.EstadoCaptura f) {
            identidad(fila,"BORRADOR_CAPTURA",f.idBorrador(),(long)f.version());
            fila.put("estadoRecurso",codigo(f.estado()));
        } else if(dto instanceof EncargoService.Peticion f) {
            identidad(fila,"PETICION_ENCARGO",f.id(),f.version());
            fila.put("idEncargo",f.encargo());
            fila.put("versionPresentada",f.versionPresentada());
            fila.put("estadoRecurso",codigo(f.estado()));
            if(f.firma()!=null)fila.put("revisionPresentacion",f.firma().revisionPresentacion());
        } else if(dto instanceof FormalizacionService.Expediente f) {
            identidad(fila,"EXPEDIENTE_OPERACION",f.id(),f.version());
            fila.put("idEncargo",f.encargo());fila.put("idOportunidad",f.oportunidad());
        } else if(dto instanceof FormalizacionService.Hito f) {
            identidad(fila,"HITO_CONTRACTUAL",f.id(),f.version());
        } else if(dto instanceof ComisionService.ResultadoCobro f) {
            identidad(fila,"COMISION",f.comision(),f.version());fila.put("idMovimiento",f.movimiento());
        } else if(dto instanceof ComisionService.ResultadoGestion f) {
            identidad(fila,"COMISION",f.comision(),f.version());
        } else if(dto instanceof ComisionService.ResultadoReparto f) {
            identidad(fila,"COMISION",f.comision(),f.version());
        } else if(dto instanceof EncargoService.Adenda f) {
            identidad(fila,"ENCARGO",f.referencia().encargo(),null);fila.put("idPropuesta",f.referencia().propuesta());
        } else if(dto instanceof com.controllocal.service.PublicacionService.FichaPublicacion f) {
            identidad(fila,"PUBLICACION",f.id(),f.version());fila.put("idEncargo",f.idEncargo());
            fila.put("estadoRecurso",codigo(f.estado()));
        } else if(dto instanceof com.controllocal.service.colaboracion.CanalesColaboracionService.Declaracion f) {
            fila.put("entidadTipo","CANAL_COLABORACION");fila.put("referenciaSolicitud",f.solicitud());fila.put("versionRecurso",f.versionSolicitud());
        } else if(dto instanceof com.controllocal.service.MetaComercialService.Resultado f) {
            fila.put("tipoRecurso","METAS");
            fila.put("referencias",f.metas().stream().map(m->{var ref=new LinkedHashMap<String,Object>();
                if(m.idMeta()!=null){ref.put("idRecurso",m.idMeta());ref.put("versionRecurso",m.version());}
                else if(!m.historial().isEmpty()){var h=m.historial().getLast();ref.put("idRevision",h.id());ref.put("versionRevision",h.version());}
                return ref;}).toList());
        } else if(dto instanceof com.controllocal.service.ReportePropietarioService.Comunicacion f) {
            fila.put("tipoRecurso","COMUNICACION_REPORTE");fila.put("referenciaComunicacion",f.referencia());
            fila.put("versionRecurso",f.version());fila.put("referenciaReporte",f.reporte().referencia());fila.put("versionReporte",f.reporte().version());
        } else if(dto instanceof com.controllocal.service.colaboracion.ColaboracionService.Resultado f) {
            fila.put("entidadTipo","COLABORACION");fila.put("referenciaSolicitud",f.solicitud());
            fila.put("versionRecurso",f.versionSolicitud());fila.put("estadoRecurso",codigo(f.estado()));
        } else {
            throw new IllegalArgumentException("Tipo de resultado sin proyección técnica TEST explícita.");
        }
        emitir(fila);
    }

    /** Sólo invocar después de esperar los hilos; PID/txid observados por el caso. */
    public static void transaccion(String fase, long pid, long txid) {
        var fila=base("TRANSACCION",fase,null);
        fila.put("pid",pid);fila.put("txid",txid);emitir(fila);
    }

    private static Map<String,Object> base(String fase,String accion,Actor actor) {
        var fila=new LinkedHashMap<String,Object>();
        fila.put("caso",CASO.get());fila.put("fase",fase);
        if(accion!=null)fila.put("accion",codigo(accion));
        if(actor!=null) {
            fila.put("organizacion",actor.idOrganizacion());fila.put("personaActor",actor.idPersona());
            fila.put("rolActor",actor.idRolOperativo());fila.put("atribucion",codigo(actor.rolEfectivo()));
        }
        return fila;
    }

    private static void identidad(Map<String,Object> fila,String tipo,Long id,Long version) {
        if(tipo!=null)fila.put("tipoRecurso",codigo(tipo));
        if(id!=null)fila.put("idRecurso",id);
        if(version!=null)fila.put("versionRecurso",version);
    }

    private static String codigo(String valor) {
        if(valor!=null&&!valor.matches("[A-Za-z][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("La evidencia TEST sólo admite códigos técnicos.");
        return valor;
    }

    private static void emitir(Map<String,Object> fila) {
        if(CASO.get()==null)return;
        try { System.out.println("C4_EVIDENCIA "+JSON.writeValueAsString(fila)); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("No se pudo emitir evidencia técnica TEST.",ex); }
    }
}
