package com.controllocal.service.soporte;

import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiposDePropiedadTest {

    @Test
    void estacionamientoTieneUnCodigoPersistidoYAliasDeCaptura() {
        assertEquals("E", AtributosGobernados.codigoDelTipo("ESTACIONAMIENTO").orElseThrow());
        assertEquals("E", AtributosGobernados.codigoDelTipo("cochera").orElseThrow());
        assertEquals("ESTACIONAMIENTO", AtributosGobernados.nombreDelTipo("E"));
        assertEquals("Estacionamiento", AtributosGobernados.rotuloDelTipo("E"));
        assertEquals("E", TipoDemandado.codigo("COCHERA").orElseThrow());
        assertEquals("ESTACIONAMIENTO", TipoDemandado.nombreLegado("E"));
        assertTrue(TipoDemandado.CODIGOS.contains("E"));
    }

    @Test
    void elGuionPublicaElMismoVocabularioQueLaAutoridadDeTipos() {
        List<String> publicados = GuionRegistroPropiedad.pregunta(GuionRegistroPropiedad.TIPO_PROPIEDAD)
                .opciones().stream().map(MotorDeCaptura.Opcion::valor).toList();

        assertEquals(AtributosGobernados.nombresDeTipos(), publicados);
        assertTrue(publicados.contains("ESTACIONAMIENTO"));
    }

    @Test
    void elImporteDeOperacionTieneMonedaSinDuplicarElControlPublicado() {
        assertEquals(List.of(GuionRegistroPropiedad.IMPORTE, GuionRegistroPropiedad.EXCLUSIVIDAD),
                GuionRegistroPropiedad.DE_LA_OPERACION);
        assertEquals("IMPORTE", GuionRegistroPropiedad.pregunta("importe:VENTA").tipoDato());
        assertEquals("IMPORTE", GuionRegistroPropiedad.pregunta("importe:VENTA").control());
        assertNull(GuionRegistroPropiedad.atributoDeMoneda("moneda:VENTA"));
        assertEquals("precio_estacionamiento_adicional:ALQUILER",
                GuionRegistroPropiedad.atributoDeMoneda(
                        "moneda:precio_estacionamiento_adicional:ALQUILER"));
        assertNull(GuionRegistroPropiedad.pregunta(
                "moneda:precio_estacionamiento_adicional:ALQUILER"));
        assertFalse(GuionRegistroPropiedad.DE_LA_OPERACION.contains(GuionRegistroPropiedad.MONEDA));
    }
}
