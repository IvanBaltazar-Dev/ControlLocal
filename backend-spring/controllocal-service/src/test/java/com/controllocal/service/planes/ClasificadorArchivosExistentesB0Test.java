package com.controllocal.service.planes;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.PiezaComercial;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.repositorio.PiezaComercialRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClasificadorArchivosExistentesB0Test {
    @Test void reasignarDeAaBUsaLaAutoridadActualSinModificarElAntecedenteDeLaPieza(){
        var a=new Actor(7,8,9,Actor.AGENTE);var b=new Actor(7,10,11,Actor.AGENTE);var broker=new Actor(7,12,13,Actor.BROKER);
        var ref=UUID.randomUUID();var pieza=PiezaComercial.preparar(7,17,23,a.idRolOperativo(),ref,UUID.randomUUID(),1,"{}","{}","a".repeat(64),"PDF_A4",Instant.now());
        var piezas=mock(PiezaComercialRepository.class);var acceso=mock(AccesoEncargo.class);var vigente=mock(Captacion.class);
        when(piezas.findByOrganizacionIdAndReferencia(7,ref)).thenReturn(Optional.of(pieza));when(acceso.leer(eq(17L),any())).thenReturn(vigente);
        // La autoridad canónica cambió a B; A y broker pueden leer, pero no generar por esa lectura.
        doAnswer(i->{if(!b.equals(i.getArgument(1)))throw new AccesoNoAutorizadoException();return null;}).when(acceso).exigirResponsable(eq(vigente),any());
        var clasificador=new ClasificadorArchivosExistentesB0(mock(ContextoPresupuestoB0.class),mock(FotoPropiedadService.class),mock(PerfilService.class),
                mock(DocumentoSolicitudService.class),mock(AccesoArchivoService.class),acceso,piezas,new Documentos(new ObjectMapper().findAndRegisterModules()));
        var sid=new ContextoSesion("b".repeat(64),2_000_000_000,"SID_TEST",1);
        var recurso=clasificador.preparar(ClasificadorArchivosExistentesB0.PIEZA,ref.toString(),"REVISION_PRIVADA",b,sid);
        var clasificacion=clasificador.clasificar(new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),recurso,5,"c".repeat(64),null),b);
        assertEquals(CategoriaAlmacenamiento.PREVIEW,clasificacion.categoria());assertEquals(recurso.huellaOrigen(),clasificacion.huellaFuente());
        assertEquals(a.idRolOperativo(),pieza.getResponsable().longValue());
        assertThrows(AccesoNoAutorizadoException.class,()->clasificador.preparar(ClasificadorArchivosExistentesB0.PIEZA,ref.toString(),"REVISION_PRIVADA",a,sid));
        assertThrows(AccesoNoAutorizadoException.class,()->clasificador.preparar(ClasificadorArchivosExistentesB0.PIEZA,ref.toString(),"REVISION_PRIVADA",broker,sid));
        verify(acceso,times(2)).leer(17,b);verify(acceso,times(2)).exigirResponsable(vigente,b);
    }
}
