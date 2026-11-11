package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ControlesMaterialIa;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Configuración privada sintética: crear beans no invoca transporte. */
class ConfiguracionControlQcOpenAiTest {
    final ApplicationContextRunner contexto=new ApplicationContextRunner().withUserConfiguration(ConfiguracionControlQcOpenAi.class)
            .withBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules())
            .withBean(EvaluacionTecnicaFoto.class,()->new EvaluacionTecnicaFoto(1_000_000)).withPropertyValues("OPENAI_API_KEY=");
    @Test void ausenciaDeFlagsEsOff(){contexto.run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ControlesMaterialIa.Facturable.class));}
    @Test void soloFlagQcNoActivaGasto(){contexto.withPropertyValues("controllocal.material-ia.openai.qc.habilitado=true")
            .run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ControlesMaterialIa.Facturable.class));}
    @Test void soloFlagGlobalNoCreaJuez(){contexto.withPropertyValues("controllocal.ia.gasto-externo-habilitado=true")
            .run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ControlesMaterialIa.Facturable.class));}
    ApplicationContextRunner habilitado(String cfg,String token){return contexto.withPropertyValues("controllocal.ia.gasto-externo-habilitado=true",
            "controllocal.material-ia.openai.qc.habilitado=true","controllocal.material-ia.openai.qc.configuracion="+cfg,"OPENAI_API_KEY="+token);}
    String datos()throws Exception{return new ObjectMapper().findAndRegisterModules().writeValueAsString(ControlQcOpenAiTest.configuracion());}
    @Test void dosFlagsSinConfiguracionNoInventanRubrica(){habilitado("","").run(c->assertThat(c).hasFailed());}
    @Test void dosFlagsSinCredencialNoCreanJuez()throws Exception{habilitado(datos(),"").run(c->assertThat(c).hasFailed());}
    @Test void configuracionExplicitaCreaJuezSinInvocar()throws Exception{
        habilitado(datos(),"CLAVE_TEST_NO_REAL").run(c->{assertThat(c).hasNotFailed().hasSingleBean(ControlesMaterialIa.Facturable.class);
            assertTrue(c.getBean(ControlesMaterialIa.Facturable.class).atiende(com.controllocal.service.materialia.MaterialIaService.Accion.MEJORAR_FOTO,"PERFIL_MEJORAR_FOTO","VERSION_TEST"));});
    }
    @Test void coexistenciaTextoYQcMantieneFronterasYTiemposExplicitos()throws Exception{
        var texto=new ObjectMapper().findAndRegisterModules().writeValueAsString(ProveedorRespuestasOpenAiTest.configuracion());
        habilitado(datos(),"CLAVE_TEST_NO_REAL").withUserConfiguration(ConfiguracionProveedorRespuestasOpenAi.class)
                .withPropertyValues("controllocal.material-ia.openai.respuestas.habilitadas=true",
                        "controllocal.material-ia.openai.respuestas.configuracion="+texto).run(c->{
                    assertThat(c).hasNotFailed().hasSingleBean(ControlesMaterialIa.Facturable.class);
                    assertEquals(1,c.getBeansOfType(com.controllocal.service.materialia.ProveedorMaterialIa.class).size());
                    assertNotSame(c.getBean("transporteQcOpenAi"),c.getBean("transporteRespuestasOpenAi"));
                });
    }
    @Test void propiedadDesconocidaNoPropagaConfigNiClave()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(datos());n.put("extra","PRIVADO_CONFIG_TEST");
        habilitado(n.toString(),"PRIVADO_CREDENCIAL_TEST").run(c->{assertThat(c).hasFailed();var s=new java.io.StringWriter();
            c.getStartupFailure().printStackTrace(new java.io.PrintWriter(s));assertFalse(s.toString().contains("PRIVADO_CONFIG_TEST"));
            assertFalse(s.toString().contains("PRIVADO_CREDENCIAL_TEST"));});
    }
}
