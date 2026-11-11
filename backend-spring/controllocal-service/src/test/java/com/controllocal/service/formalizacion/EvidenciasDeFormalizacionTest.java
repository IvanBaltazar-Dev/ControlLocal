package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.ArchivoEncargo;
import com.controllocal.service.Actor;
import com.controllocal.service.encargo.AccesoArchivoEncargo;
import com.controllocal.service.encargo.EncargoService.ArchivoLectura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaDocumento;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenciasDeFormalizacionTest {
    final Actor actor=new Actor(1,2,3,Actor.BROKER);
    final UUID id=UUID.fromString("36161338-a324-4728-a1d8-f08ede3dfe42");
    final String huella="a".repeat(64);
    final AccesoArchivoEncargo acceso=mock(AccesoArchivoEncargo.class);
    final EvidenciasDeFormalizacion servicio=new EvidenciasDeFormalizacion(acceso);
    ArchivoLectura archivo(long encargo,String finalidad,long version,String hash) {
        return new ArchivoLectura(id,4,encargo,null,3,finalidad,"privado","prueba.pdf","application/pdf",hash,version);
    }
    @Test void conservaVersionConocidaSinInventarCero() {
        when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.HITO,12,huella));
        var ref=servicio.consultar(7,id,ArchivoEncargo.HITO,actor);
        assertEquals(new ReferenciaDocumento(id,12,huella),ref);
        servicio.exigir(7,List.of(ref),ArchivoEncargo.HITO,actor);
        verify(acceso,times(2)).autorizar(id,actor);
    }
    @Test void poseerReferenciaNoPermiteOtroEncargo() {
        when(acceso.autorizar(id,actor)).thenReturn(archivo(8,ArchivoEncargo.HITO,12,huella));
        assertThrows(NoEncontradoException.class,()->servicio.consultar(7,id,ArchivoEncargo.HITO,actor));
    }
    @Test void documentoDeFirmaNoSeConvierteEnEvidenciaDeCobro() {
        when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.ACEPTACION,12,huella));
        assertThrows(NoEncontradoException.class,()->servicio.exigir(7,new ReferenciaDocumento(id,12,huella),ArchivoEncargo.COBRO,actor));
    }
    @Test void detectaCambioDeVersionYDeContenido() {
        var ref=new ReferenciaDocumento(id,12,huella);
        when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.HITO,13,huella));
        assertThrows(ConflictoException.class,()->servicio.exigir(7,ref,ArchivoEncargo.HITO,actor));
        when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.HITO,12,"b".repeat(64)));
        assertThrows(ConflictoException.class,()->servicio.exigir(7,ref,ArchivoEncargo.HITO,actor));
    }
    @Test void permisoRevocadoSeCompruebaOtraVez() {
        when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.HITO,12,huella))
                .thenThrow(new AccesoNoAutorizadoException());
        var ref=servicio.consultar(7,id,ArchivoEncargo.HITO,actor);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigir(7,ref,ArchivoEncargo.HITO,actor));
    }
    @Test void listaNoDuplicaEvidenciaNiAdmiteRefIncompleta() {
        var ref=new ReferenciaDocumento(id,12,huella);
        when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.HITO,12,huella));
        assertThrows(ReglaNegocioException.class,()->servicio.exigir(7,List.of(ref,ref),ArchivoEncargo.HITO,actor));
        assertThrows(ReglaNegocioException.class,()->servicio.exigir(7,new ReferenciaDocumento(id,12,null),ArchivoEncargo.HITO,actor));
        assertThrows(IllegalStateException.class,()->{
            when(acceso.autorizar(id,actor)).thenReturn(archivo(7,ArchivoEncargo.HITO,12,null));
            servicio.consultar(7,id,ArchivoEncargo.HITO,actor);
        });
    }
}
