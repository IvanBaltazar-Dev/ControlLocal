package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Configuración/clave TEST. Crear beans no ejecuta solicitudes. */
class ConfiguracionProveedorRespuestasOpenAiTest {
    final ApplicationContextRunner contexto=new ApplicationContextRunner().withUserConfiguration(ConfiguracionProveedorRespuestasOpenAi.class)
            .withBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules()).withPropertyValues("OPENAI_API_KEY=");
    @Test void offPorAusencia(){contexto.run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ProveedorMaterialIa.class));}
    @Test void unSoloFlagNoHabilitaGasto(){contexto.withPropertyValues("controllocal.material-ia.openai.respuestas.habilitadas=true")
            .run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ProveedorMaterialIa.class));}
    @Test void gastoGlobalNoCreaRuta(){contexto.withPropertyValues("controllocal.ia.gasto-externo-habilitado=true")
            .run(c->assertThat(c).hasNotFailed().doesNotHaveBean(ProveedorMaterialIa.class));}
    ApplicationContextRunner habilitado(String datos,String clave){return contexto.withPropertyValues(
            "controllocal.ia.gasto-externo-habilitado=true","controllocal.material-ia.openai.respuestas.habilitadas=true",
            "controllocal.material-ia.openai.respuestas.configuracion="+datos,"OPENAI_API_KEY="+clave);}
    String datos()throws Exception{return new ObjectMapper().findAndRegisterModules().writeValueAsString(ProveedorRespuestasOpenAiTest.configuracion());}
    @Test void dosFlagsSinConfiguracionNoInventanLimites(){habilitado("","").run(c->assertThat(c).hasFailed());}
    @Test void faltaClaveNoCreaProveedor()throws Exception{habilitado(datos(),"").run(c->assertThat(c).hasFailed());}
    @Test void configuracionExplicitaCreaDosCapacidadesSinInvocacion()throws Exception{
        habilitado(datos(),"CLAVE_TEST_NO_REAL").run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(ProveedorMaterialIa.class);
            var p=c.getBean(ProveedorMaterialIa.class);
            assertTrue(p.atiende(Accion.REDACTAR_FICHA,"PERFIL_REDACTAR_FICHA","VERSION_TEST"));
            assertTrue(p.atiende(Accion.ANALIZAR_FOTO,"PERFIL_ANALIZAR_FOTO","VERSION_TEST"));
            assertFalse(p.atiende(Accion.MEJORAR_FOTO,"PERFIL_ANALIZAR_FOTO","VERSION_TEST"));
        });
    }
    @Test void coexistenciaEdicionYRespuestasNoConfundeTransportes()throws Exception{
        String edicion=new ObjectMapper().findAndRegisterModules().writeValueAsString(ProveedorEdicionOpenAiTest.configuracion());
        habilitado(datos(),"CLAVE_TEST_NO_REAL").withUserConfiguration(ConfiguracionProveedorEdicionOpenAi.class)
                .withPropertyValues("controllocal.material-ia.openai.edicion.habilitada=true",
                        "controllocal.material-ia.openai.edicion.configuracion="+edicion).run(c->{
                    assertThat(c).hasNotFailed();assertEquals(2,c.getBeansOfType(ProveedorMaterialIa.class).size());
                    assertNotSame(c.getBean("transporteEdicionOpenAi"),c.getBean("transporteRespuestasOpenAi"));
                });
    }
    @Test void errorNoPropagaPayloadOCredencial()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(datos());
        n.put("desconocido","VALOR_PRIVADO_TEST");habilitado(n.toString(),"CLAVE_PRIVADA_TEST").run(c->{
            assertThat(c).hasFailed();var s=new java.io.StringWriter();c.getStartupFailure().printStackTrace(new java.io.PrintWriter(s));
            assertFalse(s.toString().contains("VALOR_PRIVADO_TEST"));assertFalse(s.toString().contains("CLAVE_PRIVADA_TEST"));
        });
    }
}
