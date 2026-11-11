package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.UnidadRelacionada;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnidadRelacionadaTest {

    @Test
    void creaRelacionFisicaConTenantYVigencia() {
        UnidadRelacionada relacion = UnidadRelacionada.crear(4L, 10L, 11L, "A",
                LocalDate.of(2026, 9, 1));

        assertEquals(4L, relacion.getOrganizacionId());
        assertEquals(10L, relacion.getIdPropiedadPrincipal());
        assertEquals(11L, relacion.getIdPropiedadRelacionada());
        assertEquals("A", relacion.getTipoRelacion());
        assertEquals(LocalDate.of(2026, 9, 1), relacion.getVigenteDesde());
    }

    @Test
    void noPermiteAutoRelacionNiCodigoFueraDelVocabulario() {
        assertThrows(IllegalArgumentException.class,
                () -> UnidadRelacionada.crear(4L, 10L, 10L, "A", LocalDate.now()));
        assertThrows(IllegalArgumentException.class,
                () -> UnidadRelacionada.crear(4L, 10L, 11L, "X", LocalDate.now()));
    }

    @Test void origenTieneIdentidadesFechaFuenteYRevisionesSinAfirmarIndependencia() {
        var fecha=LocalDate.of(2026,10,28);var registro=Instant.parse("2026-10-28T12:00:00Z");
        var r=UnidadRelacionada.crearOrigen(4L,10L,11L,fecha,fecha,null,"Fuente TEST","{}",2,3,30,registro);
        assertEquals(UnidadRelacionada.ORIGEN_MATRIZ_UNIDAD,r.getTipoRelacion());
        assertEquals(fecha,r.getVigenteDesde());assertEquals(fecha,r.getVigenteHasta());
        org.junit.jupiter.api.Assertions.assertNull(r.getSituacionRegistral());
        assertEquals("Fuente TEST",r.getFuente());assertEquals(2L,r.getVersionMatrizObservada());
        assertEquals(3L,r.getVersionUnidadObservada());assertEquals(30L,r.getIdRolActor());assertEquals(registro,r.getRegistradoEn());
    }

    @Test void elConstructorDeAsociacionesNoEsUnaViaDeOrigenSinProcedencia() {
        assertThrows(IllegalArgumentException.class,
                ()->UnidadRelacionada.crear(4L,10L,11L,"O",LocalDate.of(2026,10,28)));
    }

    @Test void origenRechazaFuenteAusenteIntervaloInvertidoYAutorelacion() {
        var fecha=LocalDate.of(2026,10,28);var registro=Instant.parse("2026-10-28T12:00:00Z");
        assertThrows(IllegalArgumentException.class,()->UnidadRelacionada.crearOrigen(4L,10L,10L,fecha,null,null,"Fuente","{}",0,0,30,registro));
        assertThrows(IllegalArgumentException.class,()->UnidadRelacionada.crearOrigen(4L,10L,11L,fecha,fecha.minusDays(1),null,"Fuente","{}",0,0,30,registro));
        assertThrows(IllegalArgumentException.class,()->UnidadRelacionada.crearOrigen(4L,10L,11L,fecha,null,null," ","{}",0,0,30,registro));
    }
}
