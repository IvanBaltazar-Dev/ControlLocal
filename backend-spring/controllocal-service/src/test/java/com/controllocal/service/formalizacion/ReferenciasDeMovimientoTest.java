package com.controllocal.service.formalizacion;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.ComisionService.*;
import com.controllocal.service.actividad.ContactosDeInteraccion;
import com.controllocal.service.encargo.PactosDeEncargo;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReferenciasDeMovimientoTest {
    final Actor actor=new Actor(1,2,3,Actor.BROKER);
    final ReferenciaPacto pacto=new ReferenciaPacto(7,11);
    final ReferenciaContextoEconomico contexto=new ReferenciaContextoEconomico(4);
    final HitosConfirmados hitos=mock(HitosConfirmados.class);
    final PactosDeEncargo pactos=mock(PactosDeEncargo.class);
    final EvidenciasDeFormalizacion evidencias=mock(EvidenciasDeFormalizacion.class);
    final PersonaRepository personas=mock(PersonaRepository.class);
    final PersonaRolRepository roles=mock(PersonaRolRepository.class);
    final PropietarioService propietarios=mock(PropietarioService.class);
    final ClienteService clientes=mock(ClienteService.class);
    final ContactosDeInteraccion contactos=mock(ContactosDeInteraccion.class);
    final ReferenciasDeMovimiento servicio=new ReferenciasDeMovimientoImpl(hitos,pactos,evidencias,
            personas,roles,propietarios,clientes,contactos);
    @BeforeEach void fuentes() {
        when(hitos.consultarContexto(contexto,actor)).thenReturn(new ContextoEconomico(contexto,1,7,
                OperacionInmobiliaria.VENTA,9L,null,null,null,pacto,null,List.of()));
        when(pactos.consultarPactoFirmado(pacto,actor)).thenReturn(new PactoFirmado(pacto,3,null,
                new ReglaComision("F","NO_APLICA",new BigDecimal("15000"),"PEN","DECLARADO",null,
                        "Escritura","Titular según documento","Agencia según documento","Según pacto",
                        "Documento",List.of(),null)));
    }
    SujetoDeclarado campo(String campo) {
        return new SujetoDeclarado(new ReferenciaSujeto("CAMPO_PACTO",null,pacto,null,campo),"Declaración comercial");
    }
    DatosCobro cobro(SujetoDeclarado obligado,SujetoDeclarado beneficiario,DestinoPago destino) {
        return new DatosCobro(5L,2L,null,new BigDecimal("5000"),"PEN",LocalDate.of(2026,10,30),
                "Transferencia",obligado,beneficiario,destino,"Operación externa",List.of(),null);
    }
    @Test void campoDocumentadoNoNecesitaCrearPersona() {
        var b=campo("beneficiario");
        servicio.exigirLectura(contexto,pacto,cobro(campo("parteObligada"),b,new DestinoPago("Cuenta declarada",b)),actor);
        verifyNoInteractions(personas,roles,propietarios,clientes,contactos);
        verify(evidencias).exigir(eq(7L),anyList(),eq("COBRO_COMISION"),eq(actor));
    }
    @Test void etiquetaNoSustituyeReferencia() {
        var b=campo("beneficiario");
        var sinOrigen=new SujetoDeclarado(null,"El propietario");
        assertThrows(ReglaNegocioException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(sinOrigen,b,new DestinoPago("Cuenta",b)),actor));
    }
    @Test void noAdmiteOtroPactoComoOrigenDeLaParte() {
        var b=campo("beneficiario");
        var ajeno=new SujetoDeclarado(new ReferenciaSujeto("CAMPO_PACTO",null,new ReferenciaPacto(8,11),null,"parteObligada"),"Declarado");
        assertThrows(ReglaNegocioException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(ajeno,b,new DestinoPago("Cuenta",b)),actor));
    }
    @Test void organizacionDelMovimientoNoAbreOtroTenant() {
        var otro=new SujetoDeclarado(new ReferenciaSujeto("ORGANIZACION",8L,null,null,null),"Otra agencia");
        assertThrows(NoEncontradoException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(campo("parteObligada"),otro,new DestinoPago("Cuenta",otro)),actor));
    }
    @Test void personaExistenteRequiereLecturaContextual() {
        when(personas.existsByOrganizacionIdAndId(1,100)).thenReturn(true);
        when(contactos.comprobar(any(),eq(100L),eq(actor))).thenThrow(new NoEncontradoException("Contacto"));
        var ajeno=new SujetoDeclarado(new ReferenciaSujeto("PERSONA",100L,null,null,null),"Nombre conocido");
        var rechazo=assertThrows(NoEncontradoException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(ajeno,campo("beneficiario"),new DestinoPago("Cuenta",campo("beneficiario"))),actor));
        when(personas.existsByOrganizacionIdAndId(1,100)).thenReturn(false);
        var ausente=assertThrows(NoEncontradoException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(ajeno,campo("beneficiario"),new DestinoPago("Cuenta",campo("beneficiario"))),actor));
        assertEquals(ausente.getMessage(),rechazo.getMessage());
    }
    @Test void cobroRequierePartesYDestinoSinAsumirlosDelActor() {
        assertThrows(ReglaNegocioException.class,()->servicio.exigirLectura(contexto,pacto,cobro(null,null,null),actor));
        assertThrows(ReglaNegocioException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(campo("parteObligada"),campo("beneficiario"),null),actor));
    }
    @Test void reciboNoReutilizaPermisoRevocado() {
        when(hitos.consultarContexto(contexto,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirLectura(contexto,pacto,
                cobro(campo("parteObligada"),campo("beneficiario"),new DestinoPago("Cuenta",campo("beneficiario"))),actor));
        verifyNoInteractions(pactos,evidencias);
    }
}
