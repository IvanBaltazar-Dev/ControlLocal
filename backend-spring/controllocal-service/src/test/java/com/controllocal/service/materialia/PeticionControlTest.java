package com.controllocal.service.materialia;

import com.controllocal.service.materialia.ControlesMaterialIa.PeticionControl;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato privado de entrada QC. No ejecuta inferencia ni acredita calidad o permisos. */
class PeticionControlTest {
    private Map<String,Object> datos(){return Map.of("dato","DATO_MINIMIZADO_TEST");}
    private PeticionControl peticion(Accion a,byte[] original){return new PeticionControl(a,datos(),
            "INSTRUCCION_PERMITIDA_TEST",salida(),"CLAVE_PRIVADA_TEST",original);}
    private Salida salida(){return new Salida("TEXTO_PRIVADO_TEST","image/png",new byte[]{91,92,93},"PROCESO_PRIVADO_TEST","VERSION_TEST",null);}
    @Test void copiaDefensivaAlConstruirYAlLeer(){
        byte[] original={11,22,33};var p=peticion(Accion.MEJORAR_FOTO,original);
        original[0]=99;assertArrayEquals(new byte[]{11,22,33},p.originalAutorizado());
        byte[] lectura=p.originalAutorizado();lectura[1]=99;assertArrayEquals(new byte[]{11,22,33},p.originalAutorizado());
        assertNotSame(p.originalAutorizado(),p.originalAutorizado());
    }
    @Test void jsonExcluyeAmbosBinariosYToStringNoRevelaDatosNiEconomia()throws Exception{
        byte[] original={11,22,33};var p=peticion(Accion.MEJORAR_FOTO,original);
        var mapper=JsonMapper.builder().findAndAddModules().build();var arbol=mapper.readTree(mapper.writeValueAsString(p));
        assertFalse(arbol.has("solicitud"));assertFalse(arbol.has("snapshot"));
        assertFalse(arbol.has("originalAutorizado"));assertFalse(arbol.path("salida").has("foto"));
        assertEquals("PeticionControl[privada]",p.toString());
    }
    @Test void redaccionNoRecibeFotoOriginal(){
        assertNull(peticion(Accion.REDACTAR_FICHA,null).originalAutorizado());
        assertThrows(IllegalArgumentException.class,()->peticion(Accion.REDACTAR_FICHA,new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->peticion(Accion.REDACTAR_FICHA,new byte[0]));
    }
    @ParameterizedTest @EnumSource(value=Accion.class,names={"ANALIZAR_FOTO","MEJORAR_FOTO"})
    void controlVisualNecesitaOriginalAutorizadoNoVacio(Accion a){
        assertThrows(IllegalArgumentException.class,()->peticion(a,null));
        assertThrows(IllegalArgumentException.class,()->peticion(a,new byte[0]));
        assertArrayEquals(new byte[]{1},peticion(a,new byte[]{1}).originalAutorizado());
    }
    @Test void entradaNoDependeDeUnRecursoComercialYCopiaElMapa(){
        var datos=new HashMap<String,Object>();datos.put("area",100);
        var p=new PeticionControl(Accion.MEJORAR_FOTO,datos,"MEJORAR_TEST",salida(),"CLAVE_TEST",new byte[]{1});
        datos.put("area",200);assertEquals(100,p.datosPermitidos().get("area"));
        assertThrows(UnsupportedOperationException.class,()->p.datosPermitidos().put("area",300));
    }

}
