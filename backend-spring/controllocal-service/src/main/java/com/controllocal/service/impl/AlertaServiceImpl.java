package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.persistence.repositorio.AlertaRepository;
import com.controllocal.persistence.repositorio.AlertaRepository.Cabecera;
import com.controllocal.service.*;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.actividad.SeguimientoDeProspeccion.Necesidad;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.FuentesAsuntos;
import com.controllocal.service.soporte.AutoridadAsuntos;
import com.controllocal.service.soporte.FuentesAsuntos.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.Recurso;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** Campana: R27 usa el mismo seguimiento que Tarea, sin otro reloj ni envío externo. */
@Service
public class AlertaServiceImpl implements AlertaService {
    private final AlertaRepository alertas;
    private final Alcances alcances;
    private final SeguimientoDeProspeccion seguimiento;
    private final FuentesAsuntos fuentes;
    private final FuentesAsuntosRepository origenes;
    private final ContextosDeActividad contexto;
    private final AutoridadAsuntos autoridad;

    public AlertaServiceImpl(AlertaRepository alertas,Alcances alcances,SeguimientoDeProspeccion seguimiento,
                            FuentesAsuntos fuentes,FuentesAsuntosRepository origenes,ContextosDeActividad contexto,AutoridadAsuntos autoridad) {
        this.alertas=alertas;this.alcances=alcances;this.seguimiento=seguimiento;
        this.fuentes=fuentes;this.origenes=origenes;this.contexto=contexto;this.autoridad=autoridad;
    }

    @Override @Transactional
    public Pagina<FichaAlerta> listar(int pagina,int tamano,Actor actor) {
        exigirOperador(actor);contexto.exigirOperador(actor);
        if(actor.esAgente())sincronizarRecontacto(actor);
        sincronizarDecisionesEncargo(actor);
        var alcance=alcances.de(actor);
        if(alcance.vacio())return Pagina.vacia();
        var cabeceras=alertas.cabecerasActivas(alcance.idOrganizacion(),alcance.global(),alcance.paramRoles());
        var permitidas=permitidas(cabeceras,actor);
        if(permitidas.isEmpty())return Pagina.vacia();
        var page=alertas.buscarAutorizadas(actor.idOrganizacion(),permitidas,
                PageRequest.of(Math.max(1,pagina)-1,Math.max(1,Math.min(100,tamano))));
        var recursos=page.getContent().stream().filter(a->a.getFuenteCausaC7()!=null)
                .map(a->new Recurso(a.getEntidadTipo(),a.getEntidadId())).distinct().toList();
        var contextosEntrega=recursos.isEmpty()?Map.<Recurso,FuentesAsuntosRepository.Contexto>of():origenes.contextos(actor.idOrganizacion(),recursos);
        var cabecerasPorId=new HashMap<Long,Cabecera>();cabeceras.forEach(c->cabecerasPorId.put(c.getId(),c));
        var responsables=new HashMap<Long,Long>();
        for(var a:page.getContent())if(a.getFuenteCausaC7()!=null){
            var c=contextosEntrega.get(new Recurso(a.getEntidadTipo(),a.getEntidadId()));
            if(c==null||FuentesAsuntos.responsableDeEntrega(c)==null)throw new ConflictoException("Cambió el destinatario del aviso; actualiza la bandeja.");
            responsables.put(a.getId(),FuentesAsuntos.responsableDeEntrega(c));
        }else{
            var c=cabecerasPorId.get(a.getId());
            if(c==null||c.getIdAgente()==null)throw new ConflictoException("Cambió el destinatario del aviso; actualiza la bandeja.");
            responsables.put(a.getId(),c.getIdAgente());
        }
        var nombres=new HashMap<Long,String>();
        if(!responsables.isEmpty())for(var n:alertas.nombresDeAgentes(actor.idOrganizacion(),Set.copyOf(responsables.values())))nombres.put(n.getId(),n.getNombre());
        var alcanceFinal=alcances.de(actor);
        var finales=alertas.cabecerasActivas(actor.idOrganizacion(),alcanceFinal.global(),alcanceFinal.paramRoles());
        if(!Set.copyOf(permitidas).equals(Set.copyOf(permitidas(finales,actor))))
            throw new ConflictoException("Cambió la causa o el permiso de los avisos; actualiza la bandeja.");
        if(finales.stream().anyMatch(c->c.getFuenteCausaC7()==null&&responsables.containsKey(c.getId())
                &&!Objects.equals(c.getIdAgente(),responsables.get(c.getId()))))
            throw new ConflictoException("Cambió el destinatario del aviso; actualiza la bandeja.");
        if(page.getContent().stream().anyMatch(a->!permitidas.contains(a.getId())))throw new IllegalStateException("La página incorporó un aviso no autorizado.");
        if(!recursos.isEmpty()&&!contextosEntrega.equals(origenes.contextos(actor.idOrganizacion(),recursos)))
            throw new ConflictoException("Cambió el contexto de entrega de los avisos; actualiza la bandeja.");
        contexto.exigirOperador(actor);
        return new Pagina<>(page.getContent().stream().map(a->
                ficha(a,responsables.get(a.getId()),nombres.get(responsables.get(a.getId())))).toList(),page.getTotalElements());
    }

    @Override @Transactional
    public boolean atender(long id,Actor actor) {
        exigirOperador(actor);contexto.exigirOperador(actor);
        var cabecera=alertas.cabecera(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Alerta"));
        if(!permitidas(List.of(cabecera),actor).contains(id))throw new NoEncontradoException("Alerta");
        if(!Alerta.ACTIVA.equals(cabecera.getEstado()))return false;
        int atendidas=alertas.atenderSiActiva(actor.idOrganizacion(),id,cabecera.getMarcaCausa(),cabecera.getHuellaCausaC7());
        if(!permitidas(List.of(cabecera),actor).contains(id))throw new AccesoNoAutorizadoException();
        contexto.exigirOperador(actor);return atendidas==1;
    }

    @Override @Transactional
    public void emitir(DatosAlerta datos,Actor actor) {
        exigirOperador(actor);contexto.exigirOperador(actor);
        if(datos==null||datos.idRolAgente()==null||datos.idRolAgente()<=0||datos.entidadId()==null||datos.entidadId()<=0
                ||datos.tipo()==null||!Alerta.SEVERIDADES.contains(datos.severidad())||datos.mensaje()==null||datos.mensaje().isBlank()||datos.mensaje().length()>300)
            throw new ReglaNegocioException("El aviso debe conservar recurso, destinatario, hecho y contenido permitido.");
        String selector=switch(datos.tipo()){
            case Alerta.SOLICITUD_REENVIADA -> "ESTADO_SOLICITUD";
            case Alerta.SOLICITUD_DOCUMENTO -> "ENTREGA_DOCUMENTO";
            case Alerta.SOLICITUD_DOCUMENTO_REVISADO -> "REVISION_DOCUMENTO";
            case Alerta.SOLICITUD_EVALUADA -> "EVALUACION_SOLICITUD";
            case Alerta.OPORTUNIDAD_CERRADA -> "REVISION_HITO";
            case Alerta.COMISION_ASIGNADA -> "COMANDO_REPARTO";
            case Alerta.COMISION_COBRADA -> "COMISION_MOVIMIENTO";
            case Alerta.CAPTACION_REVISADA -> "REVISION_ENCARGO";
            default -> throw new ReglaNegocioException("El tipo de aviso no tiene un productor canónico en este contrato.");
        };
        if(datos.origen()==null||!selector.equals(datos.origen().selector()))throw new ReglaNegocioException("El aviso no identifica el hecho de su productor.");
        var causa=fuentes.resolver(new Recurso(datos.entidadTipo(),datos.entidadId()),datos.tipo(),datos.origen(),actor);
        persistir(datos,causa,actor);
    }

    @Override @Transactional
    public int sincronizarRecontacto(Actor actor) {
        exigirOperador(actor);contexto.exigirOperador(actor);
        var actuales=seguimiento.necesidadesActuales(actor);
        var vigentes=seguimiento.marcasActuales(actor);
        Set<Long> prospecciones=actuales.stream().map(n->n.revision().idProspeccion()).collect(Collectors.toSet());
        Map<String,Necesidad> porMarca=actuales.stream().collect(Collectors.toMap(Necesidad::marcaCausa,n->n));
        if(!prospecciones.isEmpty())for(var anterior:alertas.cabecerasRecontacto(actor.idOrganizacion(),prospecciones)) {
            if(!Alerta.ACTIVA.equals(anterior.getEstado())||anterior.getMarcaCausa()==null)continue;
            if(!vigentes.contains(anterior.getMarcaCausa())) {
                // Un contexto maestro legible acredita sustitución; no inferirla desde una revocación.
                alertas.retirarRecontactoSustituido(actor.idOrganizacion(),anterior.getId(),anterior.getMarcaCausa());
            } else {
                var actual=porMarca.get(anterior.getMarcaCausa());
                if(actual!=null&&actual.revision().idProspeccion()==anterior.getEntidadId()
                        &&!Objects.equals(anterior.getIdAgente(),actual.responsableActual()))
                    alertas.actualizarResponsableRecontacto(actor.idOrganizacion(),anterior.getId(),
                            actual.marcaCausa(),actual.responsableActual());
            }
        }
        int creadas=0;
        for(var n:sinRespuesta(seguimiento.necesidadesVencidas(actor)).values())
            creadas+=alertas.crearRecontacto(actor.idOrganizacion(),n.revision().idProspeccion(),
                    n.responsableActual(),n.marcaCausa());
        return creadas;
    }

    private List<Long> permitidas(List<Cabecera> cabeceras,Actor actor){
        var alcance=alcances.de(actor);
        var vencidas=cabeceras.stream().anyMatch(AlertaServiceImpl::esRecontacto)
                ?sinRespuesta(seguimiento.necesidadesVencidas(actor)):Map.<String,Necesidad>of();
        var causas=new LinkedHashMap<Long,Causa>();
        var consultas=cabeceras.stream().filter(c->c.getFuenteCausaC7()!=null)
                .map(c->new AutoridadAsuntos.Consulta(new Recurso(c.getEntidadTipo(),c.getEntidadId()),c.getTipo())).toList();
        var legibles=consultas.isEmpty()?Set.<AutoridadAsuntos.Consulta>of():autoridad.legibles(consultas,actor);
        for(var c:cabeceras)if(c.getFuenteCausaC7()!=null&&legibles.contains(new AutoridadAsuntos.Consulta(new Recurso(c.getEntidadTipo(),c.getEntidadId()),c.getTipo()))){
            var causa=fuentes.leer(c.getFuenteCausaC7());
            if(!causa.recurso().equals(new Recurso(c.getEntidadTipo(),c.getEntidadId()))||!Objects.equals(c.getTipo(),causa.finalidad())
                    ||!Objects.equals(c.getHuellaCausaC7(),fuentes.huella(actor.idOrganizacion(),causa)))
                throw new IllegalStateException("El aviso no conserva su fuente y causa.");
            causas.put(c.getId(),causa);
        }
        var vigentes=fuentes.vigentes(causas.values(),actor);
        return cabeceras.stream().filter(c->esRecontacto(c)
                ?alcance.rolesAgente().contains(c.getIdAgente())&&autorizada(c,vencidas)
                :"VIGENTE".equals(c.getEstadoCausaC7())&&causas.containsKey(c.getId())&&vigentes.contains(causas.get(c.getId())))
                .map(Cabecera::getId).toList();
    }
    private void persistir(DatosAlerta datos,Causa causa,Actor actor){
        if(!Objects.equals(datos.idRolAgente(),causa.responsableAlDetectar()))
            throw new ConflictoException("Cambió el responsable del aviso; conserva el hecho y actualiza su destinatario.");
        String huella=fuentes.huella(actor.idOrganizacion(),causa);
        Long anterior=alertas.antecedenteC7(actor.idOrganizacion(),datos.entidadTipo(),datos.entidadId(),datos.tipo(),huella).orElse(null);
        alertas.crearCausaC7(actor.idOrganizacion(),datos.tipo(),datos.severidad(),datos.entidadTipo(),datos.entidadId(),
                datos.idRolAgente(),datos.mensaje(),huella,fuentes.guardar(causa),anterior);
    }
    private void sincronizarDecisionesEncargo(Actor actor){
        if(!actor.esAgente())return;
        var decisiones=origenes.decisionesActualesEncargo(actor.idOrganizacion(),List.of(actor.idRolOperativo()));
        if(decisiones.isEmpty())return;
        var causas=fuentes.decisiones(decisiones,actor);
        // Sólo ahora se leen los motivos de las revisiones acreditadas para este actor.
        var resultados=origenes.resultadosDecision(actor.idOrganizacion(),causas.keySet());
        var porRevision=new HashMap<Long,FuentesAsuntosRepository.Decision>();decisiones.forEach(d->porRevision.put(d.revision(),d));
        for(var r:resultados){var causa=causas.get(r.revision());var d=porRevision.get(r.revision());
            if(causa==null||d==null)throw new IllegalStateException("La devolución incorporó una revisión no autorizada.");
            boolean observada="OBSERVAR".equals(r.accion());
            String mensaje=observada?"El broker observó la petición del Encargo. "+Objects.toString(r.motivo(),"")
                    :"El broker aprobó la petición del Encargo.";
            if(mensaje.length()>300)mensaje=mensaje.substring(0,299)+"…";
            persistir(new DatosAlerta(Alerta.CAPTACION_REVISADA,observada?Alerta.MEDIA:Alerta.INFO,"CAPTACION",d.encargo(),d.responsable(),mensaje,
                    Origen.de("REVISION_ENCARGO",r.revision())),causa,actor);
        }
        if(fuentes.vigentes(causas.values(),actor).size()!=new HashSet<>(causas.values()).size())
            throw new ConflictoException("Cambió la decisión o el permiso del Encargo; actualiza la bandeja.");
    }

    private static Map<String,Necesidad> sinRespuesta(List<Necesidad> necesidades) {
        return necesidades.stream().filter(n->n.revision().fechaUltimoIntento()!=null)
                .collect(Collectors.toMap(Necesidad::marcaCausa,n->n));
    }
    private static boolean esRecontacto(Cabecera c) {
        return Alerta.SIN_RESPUESTA.equals(c.getTipo())&&Tarea.ENTIDAD_PROSPECCION.equals(c.getEntidadTipo());
    }
    private static boolean autorizada(Cabecera c,Map<String,Necesidad> vencidas) {
        var n=c.getMarcaCausa()==null?null:vencidas.get(c.getMarcaCausa());
        return n!=null&&Objects.equals(c.getEntidadId(),n.revision().idProspeccion())
                &&Objects.equals(c.getIdAgente(),n.responsableActual());
    }
    private static void exigirOperador(Actor actor) {
        if(actor==null||(!actor.esAgente()&&!actor.esBroker()))throw new AccesoNoAutorizadoException();
    }

    private static FichaAlerta ficha(Alerta a,Long responsableActual,String nombreActual) {
        return new FichaAlerta(a.getId(), a.getTipo(), a.getSeveridad(), a.getEntidadTipo(),
                a.getEntidadId(),
                responsableActual,
                nombreActual,
                a.getMensaje(), a.getEstado(), a.getFechaGeneracion(), a.getFechaResolucion(),
                ruta(a.getEntidadTipo(), a.getEntidadId()));
    }

    /**
     * Ruta a la que navega la campana. Calcada del {@code Dtos.ruta(Alerta)} de
     * la v1, incluidos sus huecos: {@code INMUEBLE} y {@code CAPTACION} caen en
     * el {@code default} y viajan con ruta <b>null</b>, asi que esas alertas se
     * muestran sin enlace. Es cable real (D-F6-4), no un olvido que tapar aqui.
     */
    private static String ruta(String entidadTipo, Long id) {
        if (id == null || id <= 0 || entidadTipo == null) {
            return null;
        }
        return switch (entidadTipo) {
            case "SOLICITUD_ALQUILER" -> "solicitud-detail/" + id;
            case "OPORTUNIDAD" -> "oportunidad-detail/" + id;
            case "VISITA" -> "visitas?focus=" + id;
            case "CLIENTE_INTERESADO" -> "cliente-detail/" + id;
            case "PROPIETARIO" -> "owner-detail/" + id;
            case Tarea.ENTIDAD_PROSPECCION -> "prospeccion-detail/" + id;
            case "CONTRATO_ALQUILER" -> "comisiones";
            default -> null;
        };
    }
}
