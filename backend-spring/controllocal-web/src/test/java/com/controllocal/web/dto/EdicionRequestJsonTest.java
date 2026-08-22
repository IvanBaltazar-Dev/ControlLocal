package com.controllocal.web.dto;

import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.web.dto.PropiedadUniversalDtos.EdicionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>La regla de bloques, vista desde el JSON</b>.
 *
 * <p>{@code ConservacionDeLaEdicionIntegrationTest} demuestra la semantica de
 * edicion parcial sobre {@code ComandoEdicion}; este test cubre el tramo que
 * aquel no recorre: <b>el cuerpo que manda el editor</b>, deserializado por
 * Jackson. La promesa de la matriz es «lo que llega {@code null} no se toca», y
 * para que sea verdad hace falta que <i>ausente</i> llegue como {@code null} y
 * que {@code []} llegue como lista vacia — que son dos cosas distintas y el
 * servicio las trata distinto (una lista vacia de titulares cierra la
 * titularidad; {@code null} la conserva).
 */
class EdicionRequestJsonTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("un cuerpo vacio es 'no toques nada': todo llega null")
    void unCuerpoVacioNoTocaNada() throws Exception {
        ComandoEdicion comando = leer("{}").aDatos(null, null);

        assertNull(comando.descripcion());
        assertNull(comando.ubicacion());
        assertNull(comando.titulares());
        assertNull(comando.atributos());
        assertNull(comando.operaciones());
        assertNull(comando.atributosABorrar());
        assertNull(comando.condiciones());
    }

    @Test
    @DisplayName("una lista vacia NO es ausencia: viaja como lista vacia")
    void unaListaVaciaNoEsAusencia() throws Exception {
        ComandoEdicion comando = leer("{\"titulares\":[],\"atributos\":[]}").aDatos(null, null);

        assertNotNull(comando.titulares());
        assertTrue(comando.titulares().isEmpty());
        assertNotNull(comando.atributos());
        assertTrue(comando.atributos().isEmpty());
        assertNull(comando.operaciones(), "lo que no vino sigue sin venir");
    }

    @Test
    @DisplayName("dentro de la ubicacion, lo que no viene tampoco viene")
    void laUbicacionSeFusionaCampoACampo() throws Exception {
        ComandoEdicion comando = leer("{\"ubicacion\":{\"distrito\":\"Surco\"}}").aDatos(null, null);

        assertEquals("Surco", comando.ubicacion().distrito());
        assertNull(comando.ubicacion().direccion());
        assertNull(comando.ubicacion().piso());
    }

    @Test
    @DisplayName("el bloque de una operacion lleva su operacion, importe y moneda, y nada supuesto")
    void elBloqueDeUnaOperacionNoSuponeNada() throws Exception {
        ComandoEdicion comando = leer("""
                {"operaciones":[{"operacion":"VENTA","importe":330000,"moneda":"USD"}]}
                """).aDatos(null, null);

        assertEquals(1, comando.operaciones().size());
        var venta = comando.operaciones().get(0);
        assertEquals("VENTA", venta.operacion());
        assertEquals(0, new BigDecimal("330000").compareTo(venta.importe()));
        assertEquals("USD", venta.moneda());
        assertNull(venta.exclusividad(), "no se mando: no se toca");
        assertNull(venta.inicioEncargo());
        assertNull(venta.finEncargo());
    }

    @Test
    @DisplayName("lo pactado viaja por idEncargo, con la clave sin calificar")
    void loPactadoViajaPorEncargo() throws Exception {
        ComandoEdicion comando = leer("""
                {"condiciones":[{"idEncargo":32,
                                 "atributos":[{"clave":"garantia_meses","valor":"3"}],
                                 "atributosABorrar":["mascotas_aceptadas"]}]}
                """).aDatos(null, null);

        var bloque = comando.condiciones().get(0);
        assertEquals(32L, bloque.idEncargo());
        assertEquals("garantia_meses", bloque.atributos().get(0).clave());
        assertEquals("3", bloque.atributos().get(0).valor());
        assertEquals("mascotas_aceptadas", bloque.atributosABorrar().get(0));
    }

    private EdicionRequest leer(String cuerpo) throws Exception {
        return json.readValue(cuerpo, EdicionRequest.class);
    }
}
