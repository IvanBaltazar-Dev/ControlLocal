package com.controllocal.service.actividad;

import com.controllocal.domain.persona.Persona;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** RF003/CAT-E02: referencias contextuales; conocer una Persona no concede acceso a ella. */
@Component
public class ContactosDeInteraccion {
    public record Resuelto(ReferenciaContactoContextual referencia,Persona persona) { }
    private final InteresadosDeDemanda interesados;
    private final PropietarioService propietarios;
    private final PersonaRepository personas;
    private final AutoriaContactoContextualRepository autoria;
    private final OportunidadComercialRepository operadores;
    private final Alcances alcances;
    private final ContextosDeActividad contextos;
    private final Documentos documentos;
    public ContactosDeInteraccion(InteresadosDeDemanda interesados,PropietarioService propietarios,
            PersonaRepository personas,AutoriaContactoContextualRepository autoria,
            OportunidadComercialRepository operadores,Alcances alcances,ContextosDeActividad contextos,Documentos documentos){
        this.interesados=interesados;this.propietarios=propietarios;this.personas=personas;
        this.autoria=autoria;this.operadores=operadores;
        this.alcances=alcances;this.contextos=contextos;this.documentos=documentos;
    }
    public Resuelto resolver(ReferenciaContactoContextual ref,Actor actor,Procedencia proc,boolean persistir){
        exigirForma(ref);
        if(ref.tipo()==TipoContacto.CONTACTO_NUEVO){
            var rf=rf003(ref);interesados.validar(rf,actor);
            if(!persistir)return new Resuelto(ref,null);
            var p=interesados.resolver(rf,actor,proc).persona();
            autoria.registrar(actor.idOrganizacion(),p.getId(),actor.idRolOperativo(),contextos.ahora(),documentos.objetoCanonico(proc));
            return new Resuelto(new ReferenciaContactoContextual(TipoContacto.PERSONA,p.getId(),null,null),p);
        }
        Long id=comprobar(ref,null,actor);
        var persona=personas.findByOrganizacionIdAndId(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Contacto contextual"));
        return new Resuelto(ref,persona);
    }
    /** Devuelve sólo la identidad probada; no hidrata texto ni crea contactos. */
    public Long comprobar(ReferenciaContactoContextual ref,Long personaEsperada,Actor actor){
        exigirForma(ref);Long persona;
        switch(ref.tipo()){
            case CONTACTO_NUEVO -> {var l=interesados.consultarLectura(rf003(ref),personaEsperada,actor);if(!l.autorizada())throw new NoEncontradoException("Contacto original");return null;}
            case PERSONA -> {
                boolean permitida=autoria.origenes(actor.idOrganizacion(),ref.referencia()).stream().anyMatch(o->
                    alcances.alcanza(actor,o.autor())&&operadores.contextoOperativoActual(actor.idOrganizacion(),o.personaAutor(),o.autor(),"AGENTE",contextos.hoy()));
                if(!permitida)throw new NoEncontradoException("Contacto contextual");
                persona=ref.referencia();
            }
            case CLIENTE -> {
                var l=interesados.consultarLectura(rf003(ref),personaEsperada,actor);
                if(!l.autorizada())throw new NoEncontradoException("Contacto original");persona=l.persona();
            }
            case PROPIETARIO -> {
                persona=propietarios.personaLegible(ref.referencia(),actor).orElseThrow(()->new NoEncontradoException("Propietario"));
            }
            case DEMANDA_ORIGEN -> {
                var l=interesados.consultarLectura(rf003(ref),personaEsperada,actor);
                if(!l.autorizada())throw new NoEncontradoException("Contacto original");persona=l.persona();
            }
            default -> throw new ReglaNegocioException("Origen de contacto no admitido.");
        }
        if(persona==null||personaEsperada!=null&&!Objects.equals(persona,personaEsperada))throw new NoEncontradoException("Contacto original");
        return persona;
    }
    private static void exigirForma(ReferenciaContactoContextual ref){
        if(ref==null||ref.tipo()==null)throw new ReglaNegocioException("Identifica el origen contextual del contacto.");
        if(ref.tipo()==TipoContacto.CONTACTO_NUEVO){
            if(ref.referencia()!=null||ref.versionEsperada()!=null||ref.datos()==null)throw new ReglaNegocioException("El contacto nuevo no referencia una persona existente.");
        }else if(ref.referencia()==null||ref.referencia()<=0||ref.datos()!=null
                ||ref.tipo()==TipoContacto.DEMANDA_ORIGEN&&(ref.versionEsperada()==null||ref.versionEsperada()<0))
            throw new ReglaNegocioException("La referencia contextual del contacto está incompleta.");
    }
    private static InteresadosDeDemanda.Referencia rf003(ReferenciaContactoContextual ref){
        return switch(ref.tipo()){
            case CLIENTE -> new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CLIENTE,ref.referencia(),null,null,null);
            case DEMANDA_ORIGEN -> new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.DEMANDA_ORIGEN,null,ref.referencia(),ref.versionEsperada(),null);
            case CONTACTO_NUEVO -> new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CONTACTO_NUEVO,null,null,null,ref.datos());
            default -> throw new ReglaNegocioException("La referencia no pertenece a RF003.");
        };
    }
}
