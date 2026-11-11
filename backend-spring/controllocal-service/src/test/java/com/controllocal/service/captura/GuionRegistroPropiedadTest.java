package com.controllocal.service.captura;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.soporte.AtributosGobernados;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GuionRegistroPropiedadTest {
    @Test void aperturaConservaOchoTiposSinCrearEdificioComoNovenoTipo() {
        var tipos = GuionRegistroPropiedad.pregunta(GuionRegistroPropiedad.TIPO_PROPIEDAD).opciones().stream()
                .map(MotorDeCaptura.Opcion::valor).collect(Collectors.toSet());
        assertEquals(Set.of("DEPARTAMENTO", "CASA", "TERRENO", "LOCAL", "OFICINA", "ALMACEN", "ESTACIONAMIENTO", "OTRO"), tipos);
        assertEquals(Set.copyOf(AtributosGobernados.nombresDeTipos()), tipos);
    }

    @Test void datosDelEncargoNoSonMinimosParaIdentificarElFisico() {
        for (String clave : new String[]{"operaciones", "titulares", "importe:VENTA", "moneda:ALQUILER", "exclusividad:VENTA"})
            assertFalse(GuionRegistroPropiedad.pregunta(clave).obligatoria(), clave);
        assertTrue(GuionRegistroPropiedad.pregunta("tipoPropiedad").obligatoria());
        assertTrue(GuionRegistroPropiedad.pregunta("distrito").obligatoria());
        assertFalse(GuionRegistroPropiedad.pregunta("direccion").obligatoria(), "Se admite referencia suficiente alternativa.");
    }

    @Test void ventaYAlquilerConservanImporteCompuestoYMonedaPropios() {
        var venta = GuionRegistroPropiedad.pregunta("importe:VENTA");
        var alquiler = GuionRegistroPropiedad.pregunta("importe:ALQUILER");
        assertEquals("IMPORTE", venta.control());
        assertEquals("IMPORTE", alquiler.control());
        assertNotEquals(venta.rotulo(), alquiler.rotulo());
        assertEquals(OperacionInmobiliaria.VENTA, GuionRegistroPropiedad.operacionDe(venta.clave()));
        assertEquals(OperacionInmobiliaria.ALQUILER, GuionRegistroPropiedad.operacionDe(alquiler.clave()));
        assertNull(GuionRegistroPropiedad.atributoDeMoneda("moneda:VENTA"));
        assertNull(GuionRegistroPropiedad.atributoDeMoneda("moneda:ALQUILER"));
    }

    @Test void monedaDeUnAtributoYDeUnaCondicionNoSeConfundenConLaMonedaDeOperacion() {
        assertEquals("cuota_mantenimiento", GuionRegistroPropiedad.atributoDeMonedaDePropiedad("moneda:cuota_mantenimiento"));
        assertEquals("precio_estacionamiento_adicional:ALQUILER",
                GuionRegistroPropiedad.atributoDeMonedaDeEncargo("moneda:precio_estacionamiento_adicional:ALQUILER"));
        assertNull(GuionRegistroPropiedad.atributoDeMonedaDePropiedad("moneda:precio_estacionamiento_adicional:ALQUILER"));
        assertNull(GuionRegistroPropiedad.pregunta("moneda:cuota_mantenimiento"));
        assertFalse(GuionRegistroPropiedad.esEstructural("moneda:precio_estacionamiento_adicional:ALQUILER"));
    }

    @Test void unidadYEdificioNoSePreguntanAlTerrenoNiSeDuplicaPiso() {
        assertFalse(GuionRegistroPropiedad.aplicaAlTipo("interiorUnidad", "T"));
        assertFalse(GuionRegistroPropiedad.aplicaAlTipo("nombreEdificioGaleria", "T"));
        assertTrue(GuionRegistroPropiedad.aplicaAlTipo("interiorUnidad", "D"));
        assertFalse(GuionRegistroPropiedad.ESTRUCTURALES.contains("pisoUnidad"));
        assertNull(GuionRegistroPropiedad.pregunta("pisoUnidad"));
    }
}
