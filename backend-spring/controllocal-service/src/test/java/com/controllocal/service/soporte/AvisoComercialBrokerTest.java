package com.controllocal.service.soporte;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AvisoComercialBrokerTest {
    @Test void borradoresFotosYReintentosNoSeConviertenEnAvisos(){
        for(String e:List.of("ENCARGO_ABIERTO","CONDICIONES_ENCARGO_EDITADAS","DOCUMENTO_ENCARGO_VINCULADO","FOTO_MEJORADA","REINTENTO_TECNICO"))assertNull(AvisoComercialBroker.encargo(e,9,1));
    }
    @Test void propuestaYPrecioAplicadoConservanHechosDistintos(){
        assertEquals("NB-02",AvisoComercialBroker.encargo("ADENDA_PROPUESTA",9,2).codigo());
        assertEquals("NB-03",AvisoComercialBroker.encargo("PRECIO_ENCARGO_AUTORIZADO",9,3).codigo());
        assertNotEquals(AvisoComercialBroker.encargo("ADENDA_PROPUESTA",9,2),AvisoComercialBroker.encargo("PRECIO_ENCARGO_AUTORIZADO",9,3));
    }
    @Test void marcadorNoAcreditaUnProductorAjeno(){
        assertFalse(AvisoComercialBroker.productorAdmitido("NB-04","FOTO_REGISTRADA","OFERTA"));
        assertFalse(AvisoComercialBroker.productorAdmitido("NB-09","COMISION_COBRO_REGISTRADO","CONTACTO"));
        assertFalse(AvisoComercialBroker.productorAdmitido("NB-12","PROPIEDAD_EDITADA","INMUEBLE"));
        assertFalse(AvisoComercialBroker.productorAdmitido("NB-15","VALORACION_SUPUESTA","ORGANIZACION"));
    }
    @Test void conservarMarcaNoAlteraLaCargaDelHechoOriginal(){
        var original=Map.<String,Object>of("version",7L,"fuente",123L);var m=new AvisoComercialBroker.Marca("NB-09","ENCARGO",9,7,List.of("COBRO"));
        var contenido=AvisoComercialBroker.con(original,m);assertEquals(2,original.size());assertEquals(7L,contenido.get("version"));assertEquals(m,contenido.get("avisoBroker"));
    }
}
