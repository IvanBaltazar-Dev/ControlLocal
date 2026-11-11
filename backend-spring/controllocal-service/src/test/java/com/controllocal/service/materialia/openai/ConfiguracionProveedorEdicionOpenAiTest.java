package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Cableado local con parámetros/clave TEST; sin inferencia, fondos o credenciales reales. */
class ConfiguracionProveedorEdicionOpenAiTest {
    final ApplicationContextRunner contexto=new ApplicationContextRunner().withUserConfiguration(ConfiguracionProveedorEdicionOpenAi.class)
            .withBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules()).withPropertyValues("OPENAI_API_KEY=");
    @Test void ausenciaDePropiedadesEsOff(){contexto.run(c->{assertThat(c).hasNotFailed().doesNotHaveBean(ProveedorMaterialIa.class).doesNotHaveBean(TransporteHttpOpenAi.class);});}
    @Test void habilitarSoloAdaptadorNoHabilitaGasto(){contexto.withPropertyValues("controllocal.material-ia.openai.edicion.habilitada=true").run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ProveedorMaterialIa.class));}
    @Test void gastoGlobalNoCreaAdaptador(){contexto.withPropertyValues("controllocal.ia.gasto-externo-habilitado=true").run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ProveedorMaterialIa.class));}
    @Test void dosFlagsSinConfiguracionNoFabricanPerfilNiClave(){contexto.withPropertyValues("controllocal.ia.gasto-externo-habilitado=true","controllocal.material-ia.openai.edicion.habilitada=true").run(c->assertThat(c).hasFailed());}
    ApplicationContextRunner habilitado(String configuracion,String clave){return contexto.withPropertyValues(
            "controllocal.ia.gasto-externo-habilitado=true","controllocal.material-ia.openai.edicion.habilitada=true",
            "controllocal.material-ia.openai.edicion.configuracion="+configuracion,"OPENAI_API_KEY="+clave);}
    String configuracionTest()throws Exception{return new ObjectMapper().findAndRegisterModules().writeValueAsString(ProveedorEdicionOpenAiTest.configuracion());}
    @Test void configuracionYClaveServidorExplicitasCreanAdaptadorSinInvocarlo()throws Exception{
        habilitado(configuracionTest(),"CLAVE_TEST_NO_REAL").run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(ProveedorMaterialIa.class).hasSingleBean(TransporteHttpOpenAi.class);
            assertTrue(c.getBean(ProveedorMaterialIa.class).atiende(com.controllocal.service.materialia.MaterialIaService.Accion.MEJORAR_FOTO,"PERFIL_TEST","VERSION_TEST"));
            assertFalse(c.getBean(ProveedorMaterialIa.class).toString().contains("CLAVE_TEST_NO_REAL"));
        });
    }
    @Test void configuracionValidaSinCredencialNoHabilitaProveedor()throws Exception{
        habilitado(configuracionTest(),"").run(c->assertThat(c).hasFailed());
    }
    @Test void configuracionAjenaFallaSinExponerValoresPrivados()throws Exception{
        var nodo=new ObjectMapper().readTree(configuracionTest());((com.fasterxml.jackson.databind.node.ObjectNode)nodo).put("datoDesconocido","VALOR_PRIVADO_TEST");
        habilitado(nodo.toString(),"CLAVE_PRIVADA_TEST").run(c->{
            assertThat(c).hasFailed();var salida=new java.io.StringWriter();c.getStartupFailure().printStackTrace(new java.io.PrintWriter(salida));
            assertFalse(salida.toString().contains("VALOR_PRIVADO_TEST"));assertFalse(salida.toString().contains("CLAVE_PRIVADA_TEST"));
        });
    }
    @Test void configuracionDuplicadaNoSeResuelveSilenciosamente()throws Exception{
        String original=configuracionTest(),duplicada="{\"perfil\":\"VALOR_PRIVADO_TEST\","+original.substring(1);
        habilitado(duplicada,"CLAVE_PRIVADA_TEST").run(c->{assertThat(c).hasFailed();assertFalse(c.getStartupFailure().toString().contains("VALOR_PRIVADO_TEST"));});
    }
    @ParameterizedTest @CsvSource({"1025,1024","1008,640","3856,1712","3840,2176","3072,1008"})
    void sizeDeSalidaFueraDeRestriccionesApiNoSeConfigura(int ancho,int alto){
        assertThrows(IllegalArgumentException.class,()->new ConfiguracionEdicionOpenAi.Parametros("medium","png",null,"opaque",ancho,alto,10000000));
    }
    @ParameterizedTest @CsvSource({"1024,640","1024,1024","3840,2160","2160,3840"})
    void sizeDeSalidaEnRestriccionesApiEsExplicita(int ancho,int alto){
        var p=new ConfiguracionEdicionOpenAi.Parametros("medium","png",null,"opaque",ancho,alto,8294400);assertEquals(ancho+"x"+alto,p.size());
    }
}
