package com.controllocal.asistencia.interpretacion;

import com.controllocal.asistencia.brox.*;
import com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial;
import com.controllocal.asistencia.conversacion.Accion;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicacionC6InterpretacionTest {
    private final ClienteBrox brox=mock(ClienteBrox.class);
    private final InterpreteDeterminista interprete=new InterpreteDeterminista(brox);
    private final SesionBrox sid=new SesionBrox(1,2,3,"AGENTE","sid",Long.MAX_VALUE);
    private final Traza traza=new Traza("API","KAIROS","TEST","1","c","t","m",null,null);
    @Test void noAdivinaVersionCanalContenidoNiConfirmacion(){
        var lectura=interprete.leer("publicar anuncio 81 del encargo 21",sid,traza);
        assertEquals(Accion.PUBLICAR_ANUNCIO,lectura.accion());
        var datos=ArgumentosComerciales.leer(lectura.datos().get("datosComerciales"),OperacionComercial.PUBLICAR_ANUNCIO);
        assertEquals(Map.of("encargo",21L,"publicacion",81L),datos);
        assertFalse(datos.containsKey("versionEncargo"));assertFalse(datos.containsKey("huellaContexto"));
        verifyNoInteractions(brox);
    }
    @Test void pluralYHistoriaNoSeConfundenConLaLecturaIndividual(){
        assertEquals(Accion.CONSULTAR_PUBLICACIONES,interprete.leer("consultar publicaciones del encargo 21",sid,traza).accion());
        assertEquals(Accion.CONSULTAR_HISTORIA_PUBLICACION,interprete.leer("consultar historia publicación 81 del encargo 21",sid,traza).accion());
        assertEquals(Accion.CONSULTAR_PUBLICACION,interprete.leer("consultar publicación 81 del encargo 21",sid,traza).accion());
        verifyNoInteractions(brox);
    }
    @Test void vocabularioAntiguoNoSeConvierteEnUnaPublicacionConfirmada(){
        assertTrue(com.controllocal.asistencia.herramientas.Herramientas.de("publicar_encargo").isEmpty());
        assertTrue(Accion.de("PUBLICAR_ENCARGO").isEmpty());
        assertTrue(OperacionComercial.PUBLICAR_ANUNCIO.preparaPropuesta());
        assertFalse(OperacionComercial.CREAR_BORRADOR_ANUNCIO.preparaPropuesta());
    }
}
