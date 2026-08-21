package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.service.soporte.CoincidenciaCartera.Evaluacion;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blinda el scoring portado de la v1 (contrato F3 §7). Lo que se protege aqui
 * no es solo el numero: las FRASES de cumple/noCumple viajan literales al
 * frontend, y la regla de que un dato faltante NO castiga (NO_APLICA) es lo
 * que evita que un local sin frente registrado hunda su puntaje.
 */
class CoincidenciaCarteraTest {

    @Test
    void seisCriteriosCumplidosDanCien() {
        Evaluacion e = CoincidenciaCartera.evaluar(requerimientoCompleto(), propiedadCompleta(), valoresCompletos());

        assertEquals(100, e.puntaje());
        assertEquals(6, e.cumple().size());
        assertTrue(e.noCumple().isEmpty());
    }

    @Test
    void elPuntajeEsElPorcentajeDeLosCriteriosAPLICABLES() {
        // Renta fuera de rango: 5 de 6 criterios -> 83%.
        RequerimientoCliente r = requerimientoCompleto();
        r.setRentaMax(new BigDecimal("5000"));

        Evaluacion e = CoincidenciaCartera.evaluar(r, propiedadCompleta(), valoresCompletos());

        assertEquals(83, e.puntaje());
        assertEquals(List.of("Renta 8500 fuera de rango (3000 - 5000)"), e.noCumple());
    }

    @Test
    void unDatoFaltanteNoCastiga() {
        // Sin frente pedido ni rangos de area, esos criterios NO APLICAN: el
        // puntaje se calcula solo sobre distrito, rubro y tipo.
        RequerimientoCliente r = requerimientoCompleto();
        r.setFrenteMinimo(null);
        r.setMetrajeMin(null);
        r.setMetrajeMax(null);
        r.setRentaMin(null);
        r.setRentaMax(null);

        Evaluacion e = CoincidenciaCartera.evaluar(r, propiedadCompleta(), valoresCompletos());

        assertEquals(100, e.puntaje());
        assertEquals(3, e.cumple().size());
    }

    @Test
    void elDistritoYElRubroComparanNormalizados() {
        RequerimientoCliente r = requerimientoCompleto();
        r.setRubro("cafeteria");
        Propiedad propiedad = propiedadCompleta();
        propiedad.setDistrito("MIRAFLÓRES");
        r.setDistritos(List.of(distrito("Miraflores")));

        Evaluacion e = CoincidenciaCartera.evaluar(r, propiedad, valoresCompletos());

        // "MIRAFLÓRES" ~ "Miraflores" y "cafeteria" ~ "Cafeteria y panaderia".
        assertEquals(100, e.puntaje());
    }

    @Test
    void elTipoSinEquivalenciaUnoAUnoNoAplica() {
        // DEPOSITO_ALMACEN no mapea a ningun TipoInmueble: el criterio se cae
        // de los aplicables en vez de contar como incumplido.
        RequerimientoCliente r = requerimientoCompleto();
        r.setTipoInmueble("DEPOSITO_ALMACEN");

        Evaluacion e = CoincidenciaCartera.evaluar(r, propiedadCompleta(), valoresCompletos());

        assertEquals(100, e.puntaje());
        assertEquals(5, e.cumple().size());
    }

    @Test
    void elTipoDistintoSiCuentaComoIncumplido() {
        RequerimientoCliente r = requerimientoCompleto();
        r.setTipoInmueble("OFICINA");

        Evaluacion e = CoincidenciaCartera.evaluar(r, propiedadCompleta(), valoresCompletos());

        assertEquals(83, e.puntaje());
        assertEquals(List.of("Tipo: busca OFICINA, local es Local"), e.noCumple());
    }

    @Test
    void sinCriteriosAplicablesElPuntajeEsCero() {
        RequerimientoCliente r = new RequerimientoCliente();
        r.setRubro(null);
        Evaluacion e = CoincidenciaCartera.evaluar(r, new Propiedad(), ValoresDePropiedad.vacio());

        assertEquals(0, e.puntaje());
        assertTrue(e.cumple().isEmpty());
        assertTrue(e.noCumple().isEmpty());
    }

    @Test
    void unRequerimientoOUnaPropiedadNulosNoRompen() {
        assertEquals(0, CoincidenciaCartera.evaluar(null, propiedadCompleta(), valoresCompletos()).puntaje());
        assertEquals(0, CoincidenciaCartera.evaluar(requerimientoCompleto(), null, valoresCompletos()).puntaje());
    }

    // ------------------------------------------------------------------
    // Fixtures: cafeteria en Miraflores que casa con el local demo.
    // ------------------------------------------------------------------

    private static RequerimientoCliente requerimientoCompleto() {
        RequerimientoCliente r = new RequerimientoCliente();
        r.setRubro("Cafeteria");
        r.setTipoInmueble("LOCAL_COMERCIAL");
        r.setRentaMin(new BigDecimal("3000"));
        r.setRentaMax(new BigDecimal("12000"));
        r.setMoneda("PEN");
        r.setMetrajeMin(new BigDecimal("80"));
        r.setMetrajeMax(new BigDecimal("200"));
        r.setFrenteMinimo(new BigDecimal("5"));
        r.setDistritos(List.of(distrito("Miraflores"), distrito("San Isidro")));
        return r;
    }

    private static Propiedad propiedadCompleta() {
        Propiedad propiedad = new Propiedad();
        propiedad.setCodigo("LOC-0001");
        propiedad.setDireccion("Av. Larco 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setTipoInmueble(Propiedad.TIPO_LOCAL);
        propiedad.setMetraje(new BigDecimal("120.00"));
        propiedad.setPrecioReferencial(new BigDecimal("8500.00"));
        // Ya no hay `propiedad.setFrente`: V62 retiro la columna y el campo. La
        // trampa que habia aqui —columna con un valor que no cumple— la hace
        // ahora el compilador, que es mejor guardia que un test.
        return propiedad;
    }

    /**
     * El frente, leido de su AUTORIDAD. Es un atributo gobernado desde D-E4-3:
     * su columna en {@code propiedad} desaparece en el paso 9 y hasta entonces
     * conserva un valor que no cumple, para que este test falle si alguien
     * devuelve el scoring a leerla.
     */
    private static ValoresDePropiedad valoresCompletos() {
        return ValoresDePropiedad.constructor()
                .con(CatalogoAtributo.CLAVE_FRENTE, ValorLogico.deNumero(new BigDecimal("8.00")))
                // El rubro dejo de ser columna en V71 y pasa por la misma
                // puerta que el frente. El puntaje que afirman los casos de
                // abajo NO cambia: es la prueba de que mover el dato de sitio
                // no movio el resultado del matcher.
                .con(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO,
                        ValorLogico.deTexto("Cafeteria y panaderia"))
                .construir();
    }

    private static Distrito distrito(String nombre) {
        Distrito distrito = new Distrito();
        distrito.setNombre(nombre);
        ReflectionTestUtils.setField(distrito, "id", (long) nombre.hashCode());
        return distrito;
    }
}
