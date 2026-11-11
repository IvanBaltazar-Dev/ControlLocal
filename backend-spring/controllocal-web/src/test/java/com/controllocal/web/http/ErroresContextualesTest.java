package com.controllocal.web.http;

import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErroresContextualesTest {
    private final ManejadorErroresApi manejador=new ManejadorErroresApi();

    @Test void permisoInsuficienteTieneCodigoEstable() {
        var r=manejador.accesoDenegado(new AccesoNoAutorizadoException());
        assertEquals(403,r.getStatusCode().value());
        assertEquals("PERMISO_INSUFICIENTE",r.getBody().codigo());
    }
    @Test void capacidadDeshabilitadaEsDistintaDePermisoYDeErrorTecnico() {
        var r=manejador.capacidadDeshabilitada(new CapacidadDeshabilitadaException());
        assertEquals(409,r.getStatusCode().value());
        assertEquals("CAPACIDAD_DESHABILITADA",r.getBody().codigo());
    }
    @Test void falloTecnicoNoPublicaCausaSqlOSecretos() {
        var r=manejador.errorNoControlado(new IllegalStateException("jdbc secreto /archivo/privado"));
        assertEquals(500,r.getStatusCode().value());
        assertEquals("FALLO_TECNICO",r.getBody().codigo());
        assertFalse(r.getBody().error().contains("secreto"));
        assertFalse(r.getBody().error().contains("jdbc"));
    }
    @Test void noEncontradoYPeticionInvalidaNoSonUnaListaVacia() {
        assertEquals("RECURSO_NO_ENCONTRADO",manejador.recursoNoEncontrado(new NoEncontradoException("Propiedad")).getBody().codigo());
        assertEquals("PETICION_INVALIDA",manejador.reglaNegocio(new ReglaNegocioException("Entrada inválida")).getBody().codigo());
    }
    @Test void unaRutaAusenteNoSeAtribuyeAUnExpedienteInexistente() {
        var r=manejador.recursoInexistente(new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET,"capacidades"));
        assertEquals(404,r.getStatusCode().value());
        assertEquals("RUTA_NO_DISPONIBLE",r.getBody().codigo());
    }
}
