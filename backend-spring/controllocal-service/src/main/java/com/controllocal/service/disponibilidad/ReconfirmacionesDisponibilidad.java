package com.controllocal.service.disponibilidad;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.ReconfirmacionesDisponibilidadRepository.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.MotorMatchingDeterminista;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.*;

/** D2/E28: solicitud durable; confirmar sigue siendo la única fuente del hecho de disponibilidad. */
@Service
public class ReconfirmacionesDisponibilidad {
    public record Vista(UUID id,Clave clave,Long confirmacionBase,Instant baseFrescura,Instant disponibleEn,
        Instant recordarEn,Instant atencionHasta,Instant terminaFrescuraEn,String estado,LocalDate revisarEn,
        long version,PoliticaDeDespacho.Clasificacion orden,boolean recordatorioDisponible,String mensaje) { }
    public enum Respuesta { POSPONE,DESCONOCE }
    public record Espera(UUID solicitud,long version,Respuesta respuesta,LocalDate revisarEn,String motivo) { }
    private final ReconfirmacionesDisponibilidadRepository repo;
    private final DisponibilidadContextualService disponibilidad;
    private final EncargoService encargos;
    private final RequerimientoService demandas;
    private final ContextosDeActividad contexto;
    private final MotorMatchingDeterminista relojes;
    private final EventoDominioRepository eventos;
    private final Documentos json;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository recibos;
    public ReconfirmacionesDisponibilidad(ReconfirmacionesDisponibilidadRepository repo,DisponibilidadContextualService disponibilidad,
        EncargoService encargos,RequerimientoService demandas,ContextosDeActividad contexto,MotorMatchingDeterminista relojes,
        EventoDominioRepository eventos,Documentos json,ComandosIdempotentes comandos,ComandoIdempotenteRepository recibos){
        this.repo=repo;this.disponibilidad=disponibilidad;this.encargos=encargos;this.demandas=demandas;this.contexto=contexto;
        this.relojes=relojes;this.eventos=eventos;this.json=json;this.comandos=comandos;this.recibos=recibos;
    }
    @Transactional
    public List<Vista> listar(PoliticaDeDespacho.Consulta consulta,Actor actor){
        contexto.exigirAgente(actor);sincronizar(actor);
        var filas=new ArrayList<Solicitud>();
        for(var r:repo.recursos(actor.idOrganizacion(),actor.idRolOperativo())){
            var vigente=disponibilidad.consultar(TipoRecurso.valueOf(r.tipo()),r.id(),actor);
            for(var s:repo.delRecurso(actor.idOrganizacion(),r.tipo(),r.id()))
                if(abierta(s)&&s.responsable()==actor.idRolOperativo()&&s.ambito().equals(ambito(vigente.clave(),actor)))filas.add(s);
        }
        var salida=PoliticaDeDespacho.despachar(filas,this::asunto,consulta,contexto.ahora()).stream().map(this::vista).toList();
        contexto.exigirAgente(actor);return salida;
    }
    /** Ejecutable por barrido o por consulta; no modifica nunca la fecha disponible de una fila previa. */
    @Transactional
    public void sincronizar(Actor actor){
        contexto.exigirAgente(actor);
        for(var r:repo.recursos(actor.idOrganizacion(),actor.idRolOperativo()))sincronizarRecurso(r,actor,false);
        contexto.exigirAgente(actor);
    }
    /** Revalidación del NB14 pendiente: una confirmación concurrente impide una nueva entrega. */
    @Transactional
    public boolean entregable(UUID solicitud,Actor actor){
        contexto.exigirOperador(actor);
        var s=repo.cabecera(actor.idOrganizacion(),solicitud).orElse(null);if(s==null)return false;
        repo.bloquearRecurso(actor.idOrganizacion(),s.ambito().tipo(),s.ambito().recurso());
        s=repo.cabecera(actor.idOrganizacion(),solicitud).orElseThrow();
        var v=disponibilidad.consultar(TipoRecurso.valueOf(s.ambito().tipo()),s.ambito().recurso(),actor);
        if(!"PENDIENTE".equals(s.estado())||contexto.ahora().isBefore(s.vence())||!s.ambito().equals(ambito(v.clave(),actor)))return false;
        if(v.vigente()!=null&&v.vigente().id()!=s.confirmacion())return false;
        if("ENCARGO".equals(s.ambito().tipo())){var f=encargos.consultar(s.ambito().recurso(),actor);return Objects.equals(f.responsable(),s.responsable())&&f.vigencia().habilitado();}
        var d=demandas.contextoDisponibilidad(s.ambito().recurso(),actor,false);
        return d.responsableId()==s.responsable()&&"A".equals(d.estado())&&(d.vigenteDesde()==null||!contexto.hoy().isBefore(d.vigenteDesde()))&&(d.vigenteHasta()==null||!contexto.hoy().isAfter(d.vigenteHasta()));
    }
    /** D5-24: sólo una necesidad explícita de descubrimiento, nunca por faltar un campo al importar. */
    @Transactional
    public Vista solicitarInicial(TipoRecurso tipo,long id,long version,Actor a,ContextoSesion sesion,String clave,Procedencia proc){
        contexto.iniciar(a,sesion);var d=disponibilidad.consultar(tipo,id,a);
        var datos=Map.of("tipo",tipo,"recurso",id,"version",version);String k=lockComando(clave,a);
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(datos));
        var anterior=comandos.buscar(a,k,"SOLICITAR_CONFIRMACION_INICIAL",hash);
        if(anterior.isPresent())return json.comoObjeto(anterior.get().getResultado(),Vista.class);
        if(d.versionRecurso()!=version)throw new ConflictoException("Cambió el recurso de la solicitud.");
        if(d.vigente()!=null)throw new ReglaNegocioException("El recurso ya conserva una confirmación; consulta su vigencia.");
        var s=sincronizarRecurso(new Recurso(tipo.name(),id),a,true);
        if(s==null)throw new ReglaNegocioException("El recurso no tiene una actuación de descubrimiento vigente.");
        var resultado=vista(s);comandos.registrar(a,k,"SOLICITAR_CONFIRMACION_INICIAL",hash,"RECONFIRMACION",id,
            Procedencia.oPantalla(proc),json.objetoCanonico(resultado));return resultado;
    }
    @Transactional
    public Vista registrarEspera(Espera d,Actor a,ContextoSesion sesion,String clave,Procedencia proc){
        if(d==null||d.solicitud()==null||d.version()<0||d.respuesta()==null||d.motivo()==null||d.motivo().isBlank()
            ||d.respuesta()==Respuesta.DESCONOCE&&d.revisarEn()!=null)
            throw new ReglaNegocioException("Conserva solicitud, versión, respuesta y motivo. Desconocer no inventa una fecha.");
        contexto.iniciar(a,sesion);
        var cab=repo.cabecera(a.idOrganizacion(),d.solicitud()).orElseThrow(()->new NoEncontradoException("Solicitud"));
        disponibilidad.consultar(TipoRecurso.valueOf(cab.ambito().tipo()),cab.ambito().recurso(),a);
        String k=lockComando(clave,a),hash=Idempotencia.huellaExacta(json.objetoCanonico(d));
        var previo=comandos.buscar(a,k,"RESPONDER_SOLICITUD_DISPONIBILIDAD",hash);
        if(previo.isPresent())return json.comoObjeto(previo.get().getResultado(),Vista.class);
        var s=sincronizarRecurso(new Recurso(cab.ambito().tipo(),cab.ambito().recurso()),a,false);
        if(s==null||!s.id().equals(d.solicitud())||s.responsable()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(s.version()!=d.version())throw new ConflictoException("Cambió la solicitud de disponibilidad.");
        if(!abierta(s))throw new ConflictoException("La causa ya no está pendiente.");
        if(d.revisarEn()!=null&&d.revisarEn().isBefore(contexto.hoy()))throw new ReglaNegocioException("La revisión de la espera no puede situarse antes de hoy.");
        repo.cambiar(s,d.respuesta()==Respuesta.POSPONE?"POSPUESTA":"DESCONOCE",d.revisarEn(),d.motivo().trim(),a.idRolOperativo(),contexto.ahora(),a.idRolOperativo(),json.objetoCanonico(d));
        var resultado=vista(repo.cabecera(a.idOrganizacion(),s.id()).orElseThrow());
        comandos.registrar(a,k,"RESPONDER_SOLICITUD_DISPONIBILIDAD",hash,"RECONFIRMACION",s.ambito().recurso(),Procedencia.oPantalla(proc),json.objetoCanonico(resultado));return resultado;
    }
    private Solicitud sincronizarRecurso(Recurso r,Actor a,boolean inicial){
        repo.bloquearRecurso(a.idOrganizacion(),r.tipo(),r.id());
        var v=disponibilidad.consultar(TipoRecurso.valueOf(r.tipo()),r.id(),a);
        var ambito=ambito(v.clave(),a);boolean elegible=necesidadActual(r,a);Instant ahora=contexto.ahora();
        var anteriores=repo.delRecurso(a.idOrganizacion(),r.tipo(),r.id());
        Solicitud pendiente=null;
        for(var s:anteriores){
            if(!abierta(s))continue;
            String fin=!s.ambito().equals(ambito)?"SUSTITUIDA":!elegible?"RETIRADA":
                v.vigente()!=null&&v.vigente().id()!=s.confirmacion()?"RESUELTA":null;
            if(fin!=null){repo.cambiar(s,fin,null,s.motivo(),s.responsable(),ahora,null,"{}");continue;}
            if(s.responsable()!=a.idRolOperativo()){
                repo.cambiar(s,s.estado(),s.revisarEn(),s.motivo(),a.idRolOperativo(),ahora,null,
                    json.objetoCanonico(Map.of("hecho","REASIGNACION","anterior",s.responsable(),"actual",a.idRolOperativo())));
                s=repo.cabecera(a.idOrganizacion(),s.id()).orElseThrow();
            }
            if("POSPUESTA".equals(s.estado())&&s.revisarEn()!=null&&!contexto.hoy().isBefore(s.revisarEn())){
                repo.cambiar(s,"PENDIENTE",null,s.motivo(),s.responsable(),ahora,null,"{}");s=repo.cabecera(a.idOrganizacion(),s.id()).orElseThrow();
            }
            pendiente=s;
        }
        if(!elegible)return null;
        if(pendiente==null){
            var hecho=v.vigente();var plazos=relojes.horizontesDisponibilidad(hecho==null?null:hecho.baseFrescura(),null);
            boolean toca=inicial&&hecho==null||hecho!=null&&hecho.disponible()&&!ahora.isBefore(plazos.solicitarConfirmacionEn());
            if(!toca)return null;
            long base=hecho==null?0:hecho.id();
            // Un replay no reabre una solicitud terminada del mismo hecho/contexto.
            var existente=anteriores.stream().filter(s->s.ambito().equals(ambito)&&s.confirmacion()==base).findFirst();
            if(existente.isPresent())return existente.get();
            var tiempos=relojes.horizontesDisponibilidad(hecho==null?null:hecho.baseFrescura(),ahora);
            pendiente=repo.insertar(new Solicitud(UUID.randomUUID(),ambito,base,hecho==null?null:hecho.baseFrescura(),ahora,
                tiempos.recordarAtencionEn(),tiempos.venceAtencionEn(),a.idRolOperativo(),"PENDIENTE",null,null,0,null,null));
            repo.historia(pendiente.id(),0,"DISPONIBLE",null,ahora,"{}");evento(pendiente,"SOLICITUD_DISPONIBILIDAD_DISPONIBLE",false);
        }
        if("PENDIENTE".equals(pendiente.estado())){
            if(!ahora.isBefore(pendiente.recordatorio())&&repo.marcar(pendiente,false,ahora))evento(pendiente,"SOLICITUD_DISPONIBILIDAD_RECORDATORIO",false);
            if(!ahora.isBefore(pendiente.vence())&&repo.marcar(pendiente,true,ahora))evento(pendiente,"SOLICITUD_DISPONIBILIDAD_ESCALADA",true);
        }
        return repo.cabecera(a.idOrganizacion(),pendiente.id()).orElseThrow();
    }
    private boolean necesidadActual(Recurso r,Actor a){
        if("ENCARGO".equals(r.tipo())){var f=encargos.consultar(r.id(),a);return Objects.equals(f.responsable(),a.idRolOperativo())&&f.vigencia().habilitado();}
        var d=demandas.contextoDisponibilidad(r.id(),a,false);return d.responsableId()==a.idRolOperativo()&&"A".equals(d.estado())
            &&(d.vigenteDesde()==null||!contexto.hoy().isBefore(d.vigenteDesde()))&&(d.vigenteHasta()==null||!contexto.hoy().isAfter(d.vigenteHasta()));
    }
    private void evento(Solicitud s,String tipo,boolean broker){
        var carga=new LinkedHashMap<String,Object>();carga.put("solicitud",s.id());carga.put("version",s.version());carga.put("recurso",s.ambito().recurso());carga.put("tipoRecurso",s.ambito().tipo());
        if(broker){carga.put("notificacionBroker","NB-14");carga.put("avisoBroker",new AvisoComercialBroker.Marca("NB-14",s.ambito().tipo(),s.ambito().recurso(),s.version(),List.of("ATENCION_DISPONIBILIDAD")));}
        var evento=EventoDominio.de(s.ambito().organizacion(),tipo,"RECONFIRMACION",s.ambito().recurso(),null,EventoDominio.CANAL_SISTEMA).con(json.objetoCanonico(carga));
        evento.setOcurridoEn(contexto.ahora().atOffset(ZoneOffset.UTC));eventos.saveAndFlush(evento);
    }
    private String lockComando(String clave,Actor a){String k=Idempotencia.exigirClave(clave);String h=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);recibos.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));return k;}
    private Ambito ambito(Clave c,Actor a){return new Ambito(a.idOrganizacion(),c.tipo().name(),c.idRecurso(),c.operacion().name(),c.aspecto(),c.contexto());}
    private static boolean abierta(Solicitud s){return Set.of("PENDIENTE","POSPUESTA","DESCONOCE").contains(s.estado());}
    private PoliticaDeDespacho.Asunto asunto(Solicitud s){
        Instant fecha="PENDIENTE".equals(s.estado())?s.vence():null;
        LocalDate dia=fecha==null?s.revisarEn():fecha.atZone(PoliticaDeDespacho.ZONA).toLocalDate();
        return new PoliticaDeDespacho.Asunto("D2:"+s.id(),s.ambito().tipo(),s.ambito().recurso(),true,dia,fecha==null?null:fecha.atZone(PoliticaDeDespacho.ZONA).toLocalTime(),s.disponible(),false,null,false,false);
    }
    private Vista vista(Solicitud s){
        var a=s.ambito();var fin=relojes.horizontesDisponibilidad(s.base(),s.disponible()).terminaFrescuraEn();
        String mensaje="DESCONOCE".equals(s.estado())?"Disponibilidad pendiente de comprobar":
            "POSPUESTA".equals(s.estado())?"Revisión de disponibilidad pospuesta":
            "DEMANDA".equals(a.tipo())?"Confirma si la búsqueda continúa vigente":
            fin!=null&&!contexto.ahora().isBefore(fin)?"Esta oferta necesita reconfirmación para aparecer en Radar Red":"Confirma si esta oferta continúa disponible";
        return new Vista(s.id(),new Clave(TipoRecurso.valueOf(a.tipo()),a.recurso(),com.controllocal.domain.inmueble.OperacionInmobiliaria.valueOf(a.operacion()),a.aspecto(),a.contexto()),s.confirmacion()==0?null:s.confirmacion(),s.base(),s.disponible(),s.recordatorio(),s.vence(),fin,s.estado(),s.revisarEn(),s.version(),PoliticaDeDespacho.clasificar(asunto(s),contexto.ahora()),s.recordatorioEmitido()!=null&&"PENDIENTE".equals(s.estado()),mensaje);
    }
}
