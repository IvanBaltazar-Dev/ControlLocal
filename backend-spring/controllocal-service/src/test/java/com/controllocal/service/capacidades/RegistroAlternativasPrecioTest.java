package com.controllocal.service.capacidades;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RegistroAlternativasPrecioTest {
    @Test void elDescriptorExponeSeleccionExplicitaSinPresupuestoNiLimitePorDefecto() {
        var esquema=new RegistroDeCapacidades("").de("buscar_coincidencias").esquema();
        @SuppressWarnings("unchecked") var propiedades=(Map<String,Object>)esquema.get("properties");
        assertEquals(Map.of("type","string","enum",List.of("DESCUBRIR","EXPLORAR")),propiedades.get("modoExploracion"));
        assertEquals(Map.of("type","number"),propiedades.get("limiteExploracion"));
        assertEquals(Map.of("type","string"),propiedades.get("monedaExploracion"));
        assertFalse(propiedades.containsKey("presupuesto"));assertFalse(esquema.containsKey("required"));
    }
}
