package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembresiasControllerTest {
    private final InvitacionesMembresiaService invitaciones=mock(InvitacionesMembresiaService.class);
    private final MembresiasService membresias=mock(MembresiasService.class);
    private final MembresiasController controller=new MembresiasController(invitaciones,membresias);
    private static final String SID="m".repeat(43), REF="d42bb234-e158-4a8e-83b2-d73ebfbb824e";
    private ContextosService.SesionAccesoDTO sesion;
    private final Actor gobierno=new Actor(72,12,21,Actor.TENANT_ADMIN);
    private void sesion(boolean conGobierno) {
        Instant i=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var c=conGobierno?new ContextosService.Contexto("ctx",72,12,21,Actor.TENANT_ADMIN,"EMPRESARIAL","Org"):null;
        sesion=new ContextosService.SesionAccesoDTO(SID,8,new ContextosService.IdentidadAutenticada(88,"titular","titular@cuentas.example.test",null),c,i,i.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(new TokenService("").de(sesion),sesion),null,List.of()));
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    @Test void aceptarUsaIdentidadGlobalSinInventarPersonaOActorComercial(){
        sesion(false);
        var resultado=new InvitacionesMembresiaService.PertenenciaAceptada(REF,72,900,12,22L,Actor.AGENTE,false);
        when(invitaciones.aceptar(REF,SID,8)).thenReturn(resultado);
        var r=controller.aceptar(REF);assertEquals(resultado,r.getBody());assertEquals("no-store",r.getHeaders().getCacheControl());
        verify(invitaciones).aceptar(REF,SID,8);verifyNoInteractions(membresias);
    }
    @Test void bajaTransportaVersionMotivoYContextoComprobableSinCuentaEnElCuerpo(){
        sesion(true);
        controller.darBaja(900,new MembresiasController.Baja(4L,"Baja formal solicitada"));
        verify(membresias).darBaja(900,4,"Baja formal solicitada",gobierno,ContextoSesion.de(sesion));
    }
    @Test void omitirVersionNoSeTransformaEnUnaVersionCeroValida(){
        sesion(true);controller.darBaja(900,new MembresiasController.Baja(null,"Baja formal solicitada"));
        verify(membresias).darBaja(900,-1,"Baja formal solicitada",gobierno,ContextoSesion.de(sesion));
    }
    @Test void retiroConservaLaSesionYElActorDeGobierno(){
        sesion(true);controller.retirar(REF,new MembresiasController.Retiro("Invitación sustituida"));
        verify(invitaciones).retirar(REF,"Invitación sustituida",gobierno,ContextoSesion.de(sesion));
    }
    @Test void emitirNoRecibeCredencialesNiModificaLaCuentaPersonal(){
        sesion(true);
        var datos=new InvitacionesMembresiaService.DatosInvitacion("otra@cuentas.example.test",null,Actor.BROKER,false,null,null,null,null,Instant.now().plusSeconds(600),"Incorporación");
        controller.emitir(datos,"comando-invitacion-original");
        verify(invitaciones).emitir(datos,gobierno,ContextoSesion.de(sesion),"comando-invitacion-original");verifyNoInteractions(membresias);
    }
}
