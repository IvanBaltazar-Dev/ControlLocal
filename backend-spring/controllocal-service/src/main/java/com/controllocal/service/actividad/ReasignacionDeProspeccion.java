package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.domain.auditoria.EventoDominio;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** RF017/CAT-E12/20.9. Gobierno de la misma prospección; no transfiere su Encargo. */
@Component
public class ReasignacionDeProspeccion {
    private static final String COMANDO="REASIGNAR_PROSPECCION";
    public record Datos(long id,long versionEsperada,long responsableObservado,long responsableNuevo,String motivo) { }
    public record Contexto(long id,long version,long responsable) { }
    public record Resultado(long id,long version,long responsableAnterior,long responsable,long evento) { }
    private java.time.Clock reloj=java.time.Clock.system(java.time.ZoneId.of("America/Lima"));
    private final ProspeccionRepository prospecciones;
    private final DetalleAgenteRepository agentes;
    private final Alcances alcances;
    private final ElegibilidadDeResponsable elegibilidad;
    private final ContextosService contextos;
    private final UsuarioOrganizacionRepository miembros;
    private final CuentaAccesoRepository cuentas;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final EventoDominioRepository eventos;
    private final Documentos documentos;
    private final EntityManager entidades;
    public ReasignacionDeProspeccion(ProspeccionRepository p,DetalleAgenteRepository a,Alcances al,ElegibilidadDeResponsable e,
            ContextosService c,UsuarioOrganizacionRepository m,CuentaAccesoRepository cu,ComandoIdempotenteRepository r,
            ComandosIdempotentes co,EventoDominioRepository ev,Documentos d,EntityManager em){
        prospecciones=p;agentes=a;alcances=al;elegibilidad=e;contextos=c;miembros=m;cuentas=cu;registros=r;comandos=co;eventos=ev;documentos=d;entidades=em;
    }
    @Transactional(readOnly=true)
    public Contexto consultar(long id,Actor a){
        var scope=alcances.paraGobiernoDeReasignacion(a);
        var p=prospecciones.contextoGobierno(a.idOrganizacion(),id,scope.global(),scope.paramRoles()).orElseThrow(()->new NoEncontradoException("Prospección"));
        return new Contexto(id,p.getVersion(),p.getResponsable());
    }
    private Datos validar(Datos d,Actor a){
        if(d==null||d.id()<=0||d.versionEsperada()<0||d.responsableObservado()<=0||d.responsableNuevo()<=0)
            throw new ReglaNegocioException("Identifique la prospección, su versión y ambos responsables.");
        var c=consultar(d.id(),a);
        if(c.version()!=d.versionEsperada()||c.responsable()!=d.responsableObservado())throw new ConflictoException("La prospección cambió; revise su responsable y versión actuales.");
        if(d.responsableNuevo()==d.responsableObservado())throw new ReglaNegocioException("El destino ya es responsable de la prospección.");
        if(!alcances.alcanzaParaGobiernoDeReasignacion(a,d.responsableNuevo()))throw new AccesoNoAutorizadoException();
        return new Datos(d.id(),d.versionEsperada(),d.responsableObservado(),d.responsableNuevo(),PoliticaComercial.exigirMotivoDeReasignacion(d.motivo()));
    }
    @Transactional
    public Resultado reasignar(Datos d,Actor a,ActuacionesService.ContextoSesion sesion,String clave,Procedencia procedencia){
        alcances.paraGobiernoDeReasignacion(a);
        if(sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(a.idOrganizacion(),a.idPersona());if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        if(!ActuacionesService.ContextoSesion.de(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),a)).equals(sesion))throw new AccesoNoAutorizadoException();
        String k=Idempotencia.exigirClave(clave),h=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("actor",a,"datos",d,"procedencia",procedencia)));
        String identidad=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        var previo=comandos.buscar(a,k,COMANDO,h);if(previo.isPresent())return restaurar(previo.get().getResultado(),a);
        validar(d,a);
        for(long rol:new TreeSet<>(Set.of(d.responsableObservado(),d.responsableNuevo())))agentes.bloquearParaGobierno(a.idOrganizacion(),rol).orElseThrow(AccesoNoAutorizadoException::new);
        var scope=alcances.paraGobiernoDeReasignacion(a);
        var p=prospecciones.bloquearGobierno(a.idOrganizacion(),d.id(),scope.global(),scope.paramRoles()).orElseThrow(()->new NoEncontradoException("Prospección"));
        entidades.refresh(p);validar(d,a);elegibilidad.exigirElegible(a,d.responsableNuevo());
        p.setAgente(agentes.findById(d.responsableNuevo()).filter(x->Objects.equals(x.getOrganizacionId(),a.idOrganizacion())).orElseThrow(AccesoNoAutorizadoException::new));
        prospecciones.saveAndFlush(p);
        var evento=procedencia.sellar(EventoDominio.de(a.idOrganizacion(),"PROSPECCION_REASIGNADA","PROSPECCION",p.getId(),a.idRolOperativo(),procedencia.canal()))
                .con(documentos.objeto(Map.of("anterior",d.responsableObservado(),"nuevo",d.responsableNuevo(),"motivo",d.motivo(),"version",p.getVersion())));
        evento.setOcurridoEn(java.time.OffsetDateTime.now(reloj));
        eventos.saveAndFlush(evento);
        var resultado=new Resultado(p.getId(),p.getVersion(),d.responsableObservado(),d.responsableNuevo(),evento.getId());
        comandos.registrar(a,k,COMANDO,h,"PROSPECCION",p.getId(),procedencia,documentos.objetoCanonico(resultado));
        return resultado;
    }
    private Resultado restaurar(String guardado,Actor a){var r=documentos.comoObjeto(guardado,Resultado.class);consultar(r.id(),a);return r;}
}
