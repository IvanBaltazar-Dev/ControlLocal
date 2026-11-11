package com.controllocal.asistencia.interpretacion;

import com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentosComercialesTest {
    @Test void materialConservaLaInstruccionVoluntariaSinInventarVersionesOValidaciones(){
        String texto="""
                preparar material ia {"operacion":"11111111-1111-4111-8111-111111111111","accion":"REDACTAR_FICHA",
                "recurso":{"tipo":"PROPIEDAD","referencia":"7"},"instruccion":"Destaca la luz natural; no supongas otras características."}
                """;
        var d=ArgumentosComerciales.leer(texto,OperacionComercial.PREPARAR_MATERIAL_IA);
        assertEquals("Destaca la luz natural; no supongas otras características.",d.get("instruccion"));
        assertFalse(d.containsKey("versionOrigen"));assertFalse(d.containsKey("proveedor"));assertFalse(d.containsKey("acreditada"));
        assertEquals(d,ArgumentosComerciales.leer(ArgumentosComerciales.serializar(d),OperacionComercial.PREPARAR_MATERIAL_IA));
    }
    @Test void consultaMaterialLeeSoloLaOperacionNombrada(){
        var id="11111111-1111-4111-8111-111111111111";
        assertEquals(Map.of("operacion",id),ArgumentosComerciales.leer("consultar material ia operación "+id,OperacionComercial.CONSULTAR_MATERIAL_IA));
        assertTrue(ArgumentosComerciales.leer("consultar material ia",OperacionComercial.CONSULTAR_MATERIAL_IA).isEmpty());
    }
    @Test
    void declaracionesConservanValorFuenteConfirmacionYMonedaDesconocida() {
        String texto="""
                registrar interaccion {"vinculos":[{"tipo":"PROPIEDAD","referencia":7}],
                  "declaraciones":[{"texto":"Dice120m2","conceptoDeclarado":null,
                    "valorDeclarado":{"numero":120.00,"moneda":null},"unidad":"m2",
                    "fuente":"Informante declarado","confirmacion":{"estado":"POR_CONFIRMAR"}}]}
                """;
        var datos=ArgumentosComerciales.leer(texto,OperacionComercial.REGISTRAR_INTERACCION);
        var copia=ArgumentosComerciales.leer(ArgumentosComerciales.serializar(datos),OperacionComercial.REGISTRAR_INTERACCION);
        Map<?,?> declaracion=(Map<?,?>)((List<?>)copia.get("declaraciones")).getFirst();
        Map<?,?> valor=(Map<?,?>)declaracion.get("valorDeclarado");
        assertEquals(0,new BigDecimal("120").compareTo((BigDecimal)valor.get("numero")));
        assertNull(valor.get("moneda"));
        assertNull(declaracion.get("conceptoDeclarado"));
        assertEquals("m2",declaracion.get("unidad"));
        assertEquals("Informante declarado",declaracion.get("fuente"));
        assertEquals("POR_CONFIRMAR",((Map<?,?>)declaracion.get("confirmacion")).get("estado"));
        assertFalse(datos.containsKey("momento"));
        assertFalse(datos.containsKey("versionEsperada"));
    }

    @Test
    void importeDecimalNoPasaPorDoubleNiHeredaOtraMoneda() {
        var datos=ArgumentosComerciales.leer("""
                presentar oferta {"importe":{"importe":95000.12500000000000000000000001,"moneda":"USD"},
                  "mantenimiento":{"importe":123.45,"moneda":null}}
                """,OperacionComercial.PRESENTAR_OFERTA);
        var importe=(Map<?,?>)datos.get("importe");
        assertEquals(0,new BigDecimal("95000.12500000000000000000000001")
                .compareTo((BigDecimal)importe.get("importe")));
        assertNull(((Map<?,?>)datos.get("mantenimiento")).get("moneda"));
    }

    @Test
    void camposNombradosConservanDecimalesYTextoConCeros() {
        var datos=ArgumentosComerciales.leer("presentar oferta oportunidad 42 importe=95000.125 referencia=00127 evidencia=\"00128\"",
                OperacionComercial.PRESENTAR_OFERTA);
        assertEquals(42L,datos.get("oportunidad"));
        assertEquals(0,new BigDecimal("95000.125").compareTo((BigDecimal)datos.get("importe")));
        assertEquals("00127",datos.get("referencia"));
        assertEquals("00128",datos.get("evidencia"));
    }

    @Test
    void laComaAmbiguaNoRecortaUnImporteSilenciosamente() {
        assertThrows(IllegalArgumentException.class,()->ArgumentosComerciales.leer(
                "presentar oferta importe=95000,50",OperacionComercial.PRESENTAR_OFERTA));
    }

    @Test
    void losDosActosAdicionalesConservanReferenciaSinFabricarVersion() {
        for(var op:List.of(OperacionComercial.VINCULAR_INTERACCION,OperacionComercial.ANOTAR_OBSERVACION_INTERACCION)) {
            assertEquals(Map.of("idInteraccion",17L),ArgumentosComerciales.leer("interaccion 17",op));
            assertFalse(op.preparaPropuesta());
        }
    }
}
