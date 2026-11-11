package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.ArchivoEncargo;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.ComisionService.*;
import com.controllocal.service.InteraccionService.ReferenciaContactoContextual;
import com.controllocal.service.InteraccionService.TipoContacto;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.actividad.ContactosDeInteraccion;
import com.controllocal.service.encargo.PactosDeEncargo;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaContextoEconomico;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Referencias privadas RF064: reutiliza lectores de identidad y A04, sin resolver nombres. */
@Component
public class ReferenciasDeMovimientoImpl implements ReferenciasDeMovimiento {
    private final HitosConfirmados hitos;
    private final PactosDeEncargo pactos;
    private final EvidenciasDeFormalizacion evidencias;
    private final PersonaRepository personas;
    private final PersonaRolRepository roles;
    private final PropietarioService propietarios;
    private final ClienteService clientes;
    private final ContactosDeInteraccion contactos;
    public ReferenciasDeMovimientoImpl(HitosConfirmados hitos,PactosDeEncargo pactos,
            EvidenciasDeFormalizacion evidencias,PersonaRepository personas,PersonaRolRepository roles,
            PropietarioService propietarios,ClienteService clientes,ContactosDeInteraccion contactos) {
        this.hitos=hitos;this.pactos=pactos;this.evidencias=evidencias;this.personas=personas;
        this.roles=roles;this.propietarios=propietarios;this.clientes=clientes;this.contactos=contactos;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLectura(ReferenciaContextoEconomico contexto,ReferenciaPacto pacto,
            DatosCobro original,Actor actor) {
        if(contexto==null||pacto==null||original==null)
            throw new ReglaNegocioException("Identifica el contexto y el pacto del movimiento.");
        var c=hitos.consultarContexto(contexto,actor);
        if(c.organizacion()!=actor.idOrganizacion()||c.encargo()!=pacto.encargo())
            throw new NoEncontradoException("Pacto del movimiento");
        var p=pactos.consultarPactoFirmado(pacto,actor);
        exigirSujeto(original.parteObligada(),c.encargo(),p,actor);
        exigirSujeto(original.beneficiario(),c.encargo(),p,actor);
        if(original.destino()==null)
            throw new ReglaNegocioException("Identifica el destino y su titular declarados.");
        exigirSujeto(original.destino().titular(),c.encargo(),p,actor);
        evidencias.exigir(c.encargo(),original.evidencias(),ArchivoEncargo.COBRO,actor);
    }
    private void exigirSujeto(SujetoDeclarado sujeto,long encargo,PactoFirmado pacto,Actor actor) {
        if(sujeto==null||sujeto.referencia()==null||sujeto.etiqueta()==null||sujeto.etiqueta().isBlank())
            throw new ReglaNegocioException("Cada sujeto requiere una referencia y su etiqueta declarada.");
        var r=sujeto.referencia();
        if(r.tipo()==null)throw new ReglaNegocioException("Identifica el tipo de referencia del sujeto.");
        switch(r.tipo()) {
            case "PERSONA" -> {
                exigirId(r);exigirPersona(r.id(),actor);
            }
            case "ORGANIZACION" -> {
                exigirId(r);
                // La referencia del contexto no concede consulta de otros tenants.
                if(r.id()!=actor.idOrganizacion())throw new NoEncontradoException("Organización del movimiento");
            }
            case "CAMPO_PACTO" -> {
                if(r.id()!=null||r.documento()!=null||!Objects.equals(r.pacto(),pacto.referencia()))forma();
                String conocido=switch(Objects.toString(r.localizador(),"")) {
                    case "parteObligada" -> pacto.regla().parteObligada();
                    case "beneficiario" -> pacto.regla().beneficiario();
                    default -> throw new ReglaNegocioException("El campo no identifica una parte declarada del pacto.");
                };
                if(conocido==null||conocido.isBlank())
                    throw new ReglaNegocioException("El pacto no conserva esa declaración de parte.");
            }
            case "CAMPO_DOCUMENTO" -> {
                if(r.id()!=null||r.pacto()!=null||r.documento()==null
                        ||r.localizador()==null||r.localizador().isBlank())forma();
                evidencias.exigir(encargo,r.documento(),null,actor);
            }
            default -> throw new ReglaNegocioException("Tipo de referencia del sujeto no admitido.");
        }
    }
    private void exigirPersona(long persona,Actor actor) {
      try {
        if(!personas.existsByOrganizacionIdAndId(actor.idOrganizacion(),persona))
            throw new NoEncontradoException("Persona del movimiento");
        if(Objects.equals(actor.idPersona(),persona))return;
        for(var tipo:new TipoRol[]{TipoRol.PROPIETARIO,TipoRol.CLIENTE}) {
            var rol=roles.buscarVigente(persona,tipo).filter(r->r.getOrganizacionId()==actor.idOrganizacion());
            if(rol.isPresent()) {
                var autorizada=tipo==TipoRol.PROPIETARIO?propietarios.personaLegible(rol.get().getId(),actor)
                        :clientes.personaLegible(rol.get().getId(),actor);
                if(autorizada.filter(p->p==persona).isPresent())return;
            }
        }
        contactos.comprobar(new ReferenciaContactoContextual(TipoContacto.PERSONA,persona,null,null),persona,actor);
      } catch(NoEncontradoException|AccesoNoAutorizadoException sinLectura) {
        throw new NoEncontradoException("Persona del movimiento");
      }
    }
    private static void exigirId(ReferenciaSujeto r) {
        if(r.id()==null||r.id()<=0||r.pacto()!=null||r.documento()!=null||r.localizador()!=null)forma();
    }
    private static void forma(){throw new ReglaNegocioException("La referencia mezcla identidades o carece de su fuente exacta.");}
}
