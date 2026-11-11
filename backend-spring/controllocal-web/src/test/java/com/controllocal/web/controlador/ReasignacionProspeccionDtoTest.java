package com.controllocal.web.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReasignacionProspeccionDtoTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void ausenciaDeVersionNoSeTransformaEnCero(){assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->json.readValue("{\"responsableObservado\":2,\"responsableNuevo\":3,\"motivo\":\"Gestión TEST\"}",ReasignacionProspeccionController.Cambio.class));}
    @Test void versionNulaNoSeTransformaEnCero(){assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->json.readValue("{\"versionEsperada\":null,\"responsableObservado\":2,\"responsableNuevo\":3,\"motivo\":\"Gestión TEST\"}",ReasignacionProspeccionController.Cambio.class));}
    @Test void ceroConocidoYDeclaradoEsUnaVersionValida()throws Exception{assertEquals(0,json.readValue("{\"versionEsperada\":0,\"responsableObservado\":2,\"responsableNuevo\":3,\"motivo\":\"Gestión TEST\"}",ReasignacionProspeccionController.Cambio.class).versionEsperada());}
}
