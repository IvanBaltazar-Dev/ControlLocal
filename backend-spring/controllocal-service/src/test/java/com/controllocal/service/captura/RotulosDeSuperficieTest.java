package com.controllocal.service.captura;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class RotulosDeSuperficieTest {
    @ParameterizedTest
    @CsvSource({
            "AREA_TOTAL,Área total", "AREA_CONSTRUIDA,Área construida", "AREA_TERRENO,Área de terreno",
            "AREA_ARRENDABLE,Área arrendable", "AREA_ESTACIONAMIENTO,Área del estacionamiento",
            "AREA_OBJETO_OFRECIDO,Área del objeto ofrecido"})
    void mismoVocabularioConservaExactamenteLaSalidaDocumental(String concepto,String rotulo) {
        assertEquals(rotulo,RotulosDeSuperficie.deConcepto(concepto).orElseThrow());
        assertEquals(rotulo+" (m²)",RotulosDeSuperficie.paraDocumento(concepto));
    }

    @Test void desconocidoNoSeConvierteEnTotalPeroElDocumentoConservaSuContratoAnterior() {
        assertTrue(RotulosDeSuperficie.deConcepto(null).isEmpty());
        assertTrue(RotulosDeSuperficie.deConcepto("AREA_NO_DEFINIDA").isEmpty());
        assertEquals("Superficie declarada, concepto por precisar (m²)",RotulosDeSuperficie.paraDocumento(null));
        assertEquals("Área total (m²)",RotulosDeSuperficie.paraDocumento("AREA_NO_DEFINIDA"));
    }
}
