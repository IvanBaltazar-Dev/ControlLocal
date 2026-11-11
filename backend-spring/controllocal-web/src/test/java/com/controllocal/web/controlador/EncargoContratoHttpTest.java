package com.controllocal.web.controlador;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sólo transporte/contrato JSON; autoridad, contexto y persistencia se prueban en PostgreSQL. */
class EncargoContratoHttpTest {
    private final EncargoService core=mock(EncargoService.class);
    private final com.controllocal.service.actuacion.ActuacionesService actuaciones=mock(com.controllocal.service.actuacion.ActuacionesService.class);
    private final com.fasterxml.jackson.databind.ObjectMapper json=JsonMapper.builder().findAndAddModules().build();
    private final org.springframework.test.web.servlet.MockMvc http=MockMvcBuilders.standaloneSetup(new CicloEncargoController(core,actuaciones))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    private static final String ORIGEN="a95a27ee-5c80-44e5-8d35-898a28cf975d";

    @Test void laDecisionSoloPreparaLaRevisionHumanaAunqueElCuerpoAfirmeDocumentoRevisado()throws Exception{
        Actor actor=new Actor(1,10,30,Actor.BROKER);
        var contexto=new ContextosService.Contexto("ctx-http",1,10,30,Actor.BROKER,"EMPRESARIAL","Agencia TEST");
        var acceso=new ContextosService.SesionAccesoDTO("sid-http",1,new ContextosService.IdentidadAutenticada(100,"fixture","fixture@example.test","Fixture"),contexto,
                Instant.parse("2026-10-28T00:00:00Z"),Instant.parse("2026-10-29T00:00:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
        var decision=new EncargoService.Decision("APROBAR",0,0,null,
                new EncargoService.RevisionManuscrita(101,"a".repeat(64),true));
        var propuesta=new com.controllocal.service.actuacion.ActuacionesService.Propuesta(
                "revision-test","decidir_captacion",0,"b".repeat(64),1900000000,java.util.Map.of());
        when(actuaciones.prepararDecisionEncargo(eq(actor),eq(9L),eq(decision),any(),any())).thenReturn(propuesta);
        try(var sesion=mockStatic(SesionActual.class)){
            sesion.when(SesionActual::actor).thenReturn(actor);
            sesion.when(SesionActual::sesion).thenReturn(new SesionDeRequest(new TokenService("").de(acceso),acceso));
            http.perform(post("/encargos/peticiones/9/decision").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(decision)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.referencia").value("revision-test"))
                    .andExpect(jsonPath("$.capacidad").value("decidir_captacion"));
        }
        verify(actuaciones).prepararDecisionEncargo(eq(actor),eq(9L),eq(decision),any(),any());
        verifyNoInteractions(core);
    }

    @Test void vinculoConVersionOmitidaONullNoSeConvierteEnCero()throws Exception{
        String base="{\"referenciaOrigen\":\""+ORIGEN+"\",\"encargo\":9,\"finalidad\":\"TITULARIDAD_ENCARGO\"";
        for(String fin:List.of("}",",\"versionDestino\":null}"))
            http.perform(post("/encargos/archivos").header("Idempotency-Key","prueba-cas").contentType(MediaType.APPLICATION_JSON).content(base+fin)).andExpect(status().isBadRequest());
        verifyNoInteractions(core);
    }
    @Test void ceroExplicitoLlegaComoVersionObservadaYConservaLaReferencia()throws Exception{
        Actor actor=new Actor(1,10,30,Actor.AGENTE);
        var contexto=new ContextosService.Contexto("ctx-http",1,10,30,Actor.AGENTE,"EMPRESARIAL","Agencia TEST");
        var acceso=new ContextosService.SesionAccesoDTO("sid-http",1,new ContextosService.IdentidadAutenticada(100,"fixture","fixture@example.test","Fixture"),contexto,
                Instant.parse("2026-10-28T00:00:00Z"),Instant.parse("2026-10-29T00:00:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
        var recibo=new EncargoService.ArchivoRegistrado(UUID.randomUUID(),9L,null,"TITULARIDAD_ENCARGO","original.pdf","application/pdf","a".repeat(64),7L);
        when(core.adjuntarOReutilizar(any(),eq(actor),any(),eq("prueba-cas"),any())).thenReturn(recibo);
        try(var sesion=mockStatic(SesionActual.class)){
            sesion.when(SesionActual::actor).thenReturn(actor);sesion.when(SesionActual::sesion).thenReturn(new SesionDeRequest(new TokenService("").de(acceso),acceso));
            http.perform(post("/encargos/archivos").header("Idempotency-Key","prueba-cas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"referenciaOrigen\":\""+ORIGEN+"\",\"encargo\":9,\"finalidad\":\"TITULARIDAD_ENCARGO\",\"versionDestino\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.referencia").value(recibo.referencia().toString()))
                    .andExpect(jsonPath("$.version").value(7));
        }
        var datos=org.mockito.ArgumentCaptor.forClass(EncargoService.VinculoArchivo.class);
        verify(core).adjuntarOReutilizar(datos.capture(),eq(actor),any(),eq("prueba-cas"),any());assertEquals(0,datos.getValue().versionDestino());
    }
    @Test void decisionesExigenAmbasVersionesAunqueLaPrimeraSeaCero()throws Exception{
        for(String cuerpo:List.of("{\"actuacion\":\"APROBAR\",\"versionPeticion\":0}","{\"actuacion\":\"APROBAR\",\"versionEncargo\":0}",
                "{\"actuacion\":\"APROBAR\",\"versionPeticion\":null,\"versionEncargo\":0}","{\"actuacion\":\"APROBAR\",\"versionPeticion\":0,\"versionEncargo\":null}"))
            http.perform(post("/encargos/peticiones/1/decision").header("Idempotency-Key","prueba-decision").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        verifyNoInteractions(core);
    }
    @Test void todasLasEntradasCasRechazanAusenciaYNullEnJson()throws Exception{
        record Entrada(Class<?> tipo,String propiedad,String resto){}
        var casos=List.of(new Entrada(EncargoService.CargaArchivo.class,"versionDestino",""),new Entrada(EncargoService.Presentacion.class,"versionEsperada",""),
                new Entrada(EncargoService.Edicion.class,"versionEsperada",""),new Entrada(EncargoService.DatosFin.class,"versionEsperada",""),
                new Entrada(EncargoService.DatosComposicion.class,"versionEsperada",""),new Entrada(CaptacionService.DatosReasignacion.class,"versionEsperada",""),
                new Entrada(CicloEncargoController.Reenvio.class,"versionPeticion",""),new Entrada(CicloEncargoController.Cancelacion.class,"version",""));
        for(var caso:casos){
            assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->json.readValue("{}",caso.tipo()),caso.tipo().getName());
            assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->json.readValue("{\""+caso.propiedad()+"\":null}",caso.tipo()),caso.tipo().getName());
            assertNotNull(json.readValue("{\""+caso.propiedad()+"\":0}",caso.tipo()));
        }
        assertNull(json.readValue("{}",EncargoService.Preparacion.class).versionEncargo(),"Sin Encargo no se fabrica una versión de origen.");
    }
    @Test void composicionMensualConservaImportesFechasFuenteYExhaustividadSinInventarFaltantes()throws Exception{
        var edicion=json.readValue(edicionMensual(),EncargoService.Edicion.class);
        var composicion=edicion.condiciones().composicionMensual();
        assertNull(composicion.completa(),"Enumerar componentes no afirma exhaustividad.");
        assertEquals("Documento privado TEST",composicion.fuente());assertEquals(2,composicion.componentes().size());
        var cota=composicion.componentes().get(1);
        assertEquals(0,cota.importe().compareTo(java.math.BigDecimal.ZERO));
        assertEquals(0,cota.importeHasta().compareTo(new java.math.BigDecimal("150.25")));assertTrue(cota.intervalo());
        assertEquals("PEN",cota.moneda());assertEquals("MENSUAL",cota.periodo());
        assertEquals(com.controllocal.service.matching.ContratoMatching.TratamientoMensual.EXIGIBLE,cota.tratamiento());
        assertEquals(java.time.LocalDate.of(2026,10,30),cota.vigenteDesde());
        assertEquals(java.time.LocalDate.of(2026,12,31),cota.vigenteHasta());
        assertEquals(edicion,json.readValue(json.writeValueAsBytes(edicion),EncargoService.Edicion.class));
        assertNull(json.readValue("{\"versionEsperada\":0,\"condiciones\":{}}",EncargoService.Edicion.class)
                .condiciones().composicionMensual(),"Omitir la declaración no equivale a cero cargos.");
    }
    @Test void edicionHttpTransportaLaMismaDeclaracionMensualAlComandoExistente()throws Exception{
        Actor actor=new Actor(1,10,30,Actor.AGENTE);
        var contexto=new ContextosService.Contexto("ctx-http",1,10,30,Actor.AGENTE,"EMPRESARIAL","Agencia TEST");
        var acceso=new ContextosService.SesionAccesoDTO("sid-http",1,new ContextosService.IdentidadAutenticada(100,"fixture","fixture@example.test","Fixture"),contexto,
                Instant.parse("2026-10-28T00:00:00Z"),Instant.parse("2026-10-29T00:00:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
        try(var sesion=mockStatic(SesionActual.class)){
            sesion.when(SesionActual::actor).thenReturn(actor);sesion.when(SesionActual::sesion).thenReturn(new SesionDeRequest(new TokenService("").de(acceso),acceso));
            http.perform(put("/encargos/9/condiciones").header("Idempotency-Key","composicion-mensual-http")
                    .contentType(MediaType.APPLICATION_JSON).content(edicionMensual())).andExpect(status().isOk());
        }
        var datos=org.mockito.ArgumentCaptor.forClass(EncargoService.Edicion.class);
        verify(core).editar(eq(9L),datos.capture(),eq(actor),any(),eq("composicion-mensual-http"),any());
        assertEquals(json.readValue(edicionMensual(),EncargoService.Edicion.class),datos.getValue());
    }
    private static String edicionMensual(){return """
            {"versionEsperada":0,"condiciones":{"precioPedido":2500,"monedaPrecio":"PEN","periodoPrecio":"MENSUAL",
              "composicionMensual":{"completa":null,"fuente":"Documento privado TEST","componentes":[
                {"referencia":"alquiler","concepto":"PRECIO_ALQUILER","importe":2500,"importeHasta":null,
                 "intervalo":false,"moneda":"PEN","periodo":"MENSUAL","tratamiento":"EXIGIBLE",
                 "incluidoEn":null,"fuente":"Pacto TEST","vigenteDesde":"2026-10-30","vigenteHasta":"2026-12-31"},
                {"referencia":"mantenimiento","concepto":"MANTENIMIENTO","importe":0,"importeHasta":150.25,
                 "intervalo":true,"moneda":"PEN","periodo":"MENSUAL","tratamiento":"EXIGIBLE",
                 "incluidoEn":null,"fuente":null,"vigenteDesde":"2026-10-30","vigenteHasta":"2026-12-31"}]}}}
            """;}
}
