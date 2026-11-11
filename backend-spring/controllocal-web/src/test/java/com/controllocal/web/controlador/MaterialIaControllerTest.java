package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.dto.MaterialIaDtos;
import com.controllocal.web.seguridad.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Delegación y contrato JSON de HTTP; aislamiento/consentimiento/consumo se verifican además en Core/PG. */
class MaterialIaControllerTest {
    final MaterialIaService material=mock(MaterialIaService.class);
    final ResultadosMaterialIa resultados=mock(ResultadosMaterialIa.class);
    final FuentesMaterialIaCore fuentes=mock(FuentesMaterialIaCore.class);
    final EvidenciasTratamientoMaterialIa evidencias=mock(EvidenciasTratamientoMaterialIa.class);
    final ProcedenciaDeCabeceras procedencias=mock(ProcedenciaDeCabeceras.class);
    final MaterialIaController controller=new MaterialIaController(material,resultados,fuentes,evidencias,procedencias);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    final Actor actor=new Actor(1,11,21,Actor.AGENTE);
    ContextoSesion sid;
    @BeforeEach void sesion(){
        var actual=new ContextosService.SesionAccesoDTO("A".repeat(43),1,
                new ContextosService.IdentidadAutenticada(99,"fixture","fixture@example.invalid","Fixture TEST"),
                new ContextosService.Contexto("a".repeat(64),1,11,21,Actor.AGENTE,"EMPRESARIAL","TEST"),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),EstadoDeAcceso.SIN_RESTRICCIONES);
        var principal=new SesionDeRequest(new TokenService("").de(actual),actual);sid=ContextoDeActuacion.de(principal);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,AuthorityUtils.createAuthorityList("ROLE_AGENTE")));
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    private MaterialIaDtos.Solicitud solicitud(String instruccion){return new MaterialIaDtos.Solicitud(UUID.randomUUID(),MaterialIaService.Accion.REDACTAR_FICHA,
            new MaterialIaDtos.Recurso("PROPIEDAD","7"),null,null,instruccion);}

    @Test void headerIdempotenteAusenteEsPeticionInvalidaAntesDeInvocarCore()throws Exception{
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.controllocal.web.http.ManejadorErroresApi()).build();
        var original=UUID.randomUUID();var operacion=UUID.randomUUID();var archivo=UUID.randomUUID();
        var rutas=new LinkedHashMap<String,String>();
        rutas.put("/material-ia/fotografias/"+original+"/seleccion",
                "{\"operacion\":null,\"archivo\":null,\"revisionEsperada\":0}");
        for(var sufijo:List.of("alternativa","descarte","retirada-alternativa"))
            rutas.put("/material-ia/"+operacion+"/archivos/"+archivo+"/"+sufijo,"{\"revisionEsperada\":0}");
        for(var ruta:rutas.entrySet()){
            var respuesta=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ruta.getKey())
                    .contentType("application/json").content(ruta.getValue())).andReturn().getResponse();
            assertEquals(400,respuesta.getStatus(),ruta.getKey());
            var cuerpo=json.readTree(respuesta.getContentAsString());
            assertEquals("PETICION_INVALIDA",cuerpo.path("codigo").asText());
            assertTrue(cuerpo.path("error").asText().contains("Idempotency-Key"));
            assertFalse(respuesta.getContentAsString().contains("java.lang"));
            assertFalse(respuesta.getContentAsString().contains("org.springframework"));
        }
        verifyNoInteractions(material,resultados,fuentes,evidencias,procedencias);
    }

    @Test void preparaLocalConValorYSinConsumirOPedirConsentimiento(){
        var datos=solicitud(null);var preparado=new MaterialIaService.Preparacion(datos.operacion(),datos.accion(),datos.aCore().recurso(),"F1",Map.of("resumen","Datos permitidos"),List.of("IA_PENDIENTE"));
        when(material.preparar(any(),any(),any())).thenAnswer(i->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return preparado;});
        assertSame(preparado,controller.preparar(datos));verify(material).preparar(datos.aCore(),actor,sid);
        verifyNoInteractions(resultados,fuentes,evidencias);verify(material,never()).solicitar(any(),any(),any());
    }
    @Test void solicitudConInstruccionVoluntariaLlegaIntactaAlCoordinador(){
        var datos=solicitud("Mejora exposición y conserva lo representado.");
        when(material.solicitar(any(),any(),any())).thenReturn(new MaterialIaService.Resultado(datos.operacion(),"DISPONIBLE",null,null,List.of()));
        assertEquals("DISPONIBLE",controller.solicitar(datos).estado());verify(material).solicitar(datos.aCore(),actor,sid);
        verifyNoInteractions(resultados,fuentes,evidencias);
    }
    @Test void consultarAnteriorNoGeneraOtraSolicitudNiConsultaLaBanderaDeGasto(){
        var id=UUID.randomUUID();when(material.consultar(id,actor,sid)).thenReturn(new MaterialIaService.Resultado(id,"DISPONIBLE","Texto",null,List.of()));
        assertEquals("Texto",controller.consultar(id).texto());verify(material).consultar(id,actor,sid);
        verify(material,never()).solicitar(any(),any(),any());verify(material,never()).disponibilidad(any());
    }
    @Test void descargaPrivadaLlevaAmbasIdentidadesYSuSesionActual(){
        var op=UUID.randomUUID();var archivo=UUID.randomUUID();
        when(resultados.descargar(op,archivo,actor,sid)).thenReturn(new ResultadosMaterialIa.Contenido(new byte[]{1,2,3},"image/jpeg","variante.jpg"));
        var respuesta=controller.descargar(op,archivo);assertArrayEquals(new byte[]{1,2,3},respuesta.getBody());
        assertEquals("no-store",respuesta.getHeaders().getCacheControl());assertEquals("nosniff",respuesta.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox",respuesta.getHeaders().getFirst("Content-Security-Policy"));
        assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("variante.jpg"));
        verify(resultados).descargar(op,archivo,actor,sid);verifyNoInteractions(material,fuentes,evidencias);
    }
    @Test void denegacionDeDescargaNoSeDevuelveComoArchivoVacio(){
        var op=UUID.randomUUID();var archivo=UUID.randomUUID();doThrow(new AccesoNoAutorizadoException()).when(resultados).descargar(op,archivo,actor,sid);
        assertThrows(AccesoNoAutorizadoException.class,()->controller.descargar(op,archivo));
    }
    @Test void seleccionPorOriginalYVersionesDeleganLectoresAutorizadosDistintos(){
        var original=UUID.randomUUID();var seleccion=new ResultadosMaterialIa.Seleccion(original,0,null,null);
        var version=new ResultadosMaterialIa.Version(UUID.randomUUID(),original,UUID.randomUUID(),4,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH.plusSeconds(604800),false,false,true,"DISPONIBLE");
        when(resultados.seleccionActual(original,actor,sid)).thenReturn(seleccion);when(resultados.versiones(original,actor,sid)).thenReturn(List.of(version));
        assertSame(seleccion,controller.seleccion(original));assertEquals(List.of(version),controller.versiones(original));
        verifyNoInteractions(material,fuentes,evidencias);
    }
    @Test void usarDerivadaOVolverAlOriginalConservaCasSinOtraGeneracion(){
        var original=UUID.randomUUID();var op=UUID.randomUUID();var archivo=UUID.randomUUID();
        controller.seleccionar(original,new MaterialIaDtos.SeleccionFoto(op,archivo,7),"seleccionar-test");
        verify(resultados).seleccionar(original,op,archivo,7,"seleccionar-test",actor,sid);
        controller.seleccionar(original,new MaterialIaDtos.SeleccionFoto(null,null,8),"original-test");
        verify(resultados).seleccionar(original,null,null,8,"original-test",actor,sid);verifyNoInteractions(material,fuentes,evidencias);
    }
    @Test void guardarAlternativaYDescartarTemporalSonIntencionesConRevisionPropia(){
        var op=UUID.randomUUID();var archivo=UUID.randomUUID();
        controller.guardarAlternativa(op,archivo,new MaterialIaDtos.GestionVersion(3),"guardar-test");
        verify(resultados).guardarAlternativa(op,archivo,3,"guardar-test",actor,sid);
        controller.descartarTemporal(op,archivo,new MaterialIaDtos.GestionVersion(4),"descartar-test");
        verify(resultados).descartarTemporal(op,archivo,4,"descartar-test",actor,sid);verifyNoInteractions(material,fuentes,evidencias);
    }
    @Test void unTemporalProtegidoODisputaDeVersionNoSeDisfrazaComoEspacioLibre(){
        var op=UUID.randomUUID();var archivo=UUID.randomUUID();
        doThrow(new ConflictoException("Versión protegida TEST")).when(resultados).descartarTemporal(op,archivo,4,"descartar-test",actor,sid);
        assertThrows(ConflictoException.class,()->controller.descartarTemporal(op,archivo,new MaterialIaDtos.GestionVersion(4),"descartar-test"));
        verifyNoInteractions(material,fuentes,evidencias);
    }
    @Test void retirarAlternativaEsExpresoYDevuelvePendienteSinInventarLiberacion(){
        var op=UUID.randomUUID();var archivo=UUID.randomUUID();var original=UUID.randomUUID();
        var pendiente=new ResultadosMaterialIa.Version(op,original,archivo,9,Instant.EPOCH,null,null,true,false,false,"RETIRADA_PENDIENTE");
        when(resultados.retirarAlternativa(op,archivo,8,"retirar-alternativa",actor,sid)).thenReturn(pendiente);
        assertSame(pendiente,controller.retirarAlternativa(op,archivo,new MaterialIaDtos.GestionVersion(8),"retirar-alternativa"));
        verify(resultados).retirarAlternativa(op,archivo,8,"retirar-alternativa",actor,sid);verifyNoInteractions(material,fuentes,evidencias);
    }
    @Test void presentacionE44ObtieneAlcanceDeLaFuenteServidor(){
        var datos=solicitud("Indicación voluntaria TEST");
        controller.presentarTratamiento(datos);verify(fuentes).presentarTratamiento(datos.aCore(),actor,sid);
        verifyNoInteractions(material,evidencias,resultados);
    }
    @Test void capturaE44NoPermiteEscogerTitularOProveedor(){
        var request=mock(HttpServletRequest.class);when(procedencias.de(request)).thenReturn(Procedencia.deLaPantalla());
        var datos=new MaterialIaDtos.CapturaTratamiento(UUID.randomUUID(),UUID.randomUUID().toString(),0,EvidenciasTratamientoMaterialIa.Decision.RECHAZAR);
        controller.decidirTratamiento(datos,request);verify(evidencias).capturar(datos.aCore(),actor,sid,Procedencia.deLaPantalla());
        verifyNoInteractions(material,fuentes,resultados);
    }
    @ParameterizedTest @ValueSource(strings={"proveedor","perfil","costo","acreditada","actor","organizacion","qc"})
    void dtoComercialNoAceptaDatosInternosNiAcreditaciones(String campo)throws Exception{
        var datos=new LinkedHashMap<String,Object>();datos.put("operacion",UUID.randomUUID().toString());datos.put("accion","REDACTAR_FICHA");
        datos.put("recurso",Map.of("tipo","PROPIEDAD","referencia","7"));datos.put(campo,true);String cuerpo=json.writeValueAsString(datos);
        assertThrows(JsonProcessingException.class,()->json.readValue(cuerpo,MaterialIaDtos.Solicitud.class));
    }
    @Test void recursoTampocoAdmitePermisosDeclarados()throws Exception{
        String datos="{\"operacion\":\"11111111-1111-4111-8111-111111111111\",\"accion\":\"REDACTAR_FICHA\",\"recurso\":{\"tipo\":\"PROPIEDAD\",\"referencia\":\"7\",\"permitido\":true}}";
        assertThrows(JsonProcessingException.class,()->json.readValue(datos,MaterialIaDtos.Solicitud.class));
    }
    @Test void versionHumanaAusenteNoSeConvierteEnCeroPeroCeroExplicitoEsValido()throws Exception{
        assertThrows(JsonProcessingException.class,()->json.readValue("{}",MaterialIaDtos.GestionVersion.class));
        assertThrows(JsonProcessingException.class,()->json.readValue("{\"revisionEsperada\":null}",MaterialIaDtos.GestionVersion.class));
        assertEquals(0,json.readValue("{\"revisionEsperada\":0}",MaterialIaDtos.GestionVersion.class).revisionEsperada());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"revisionEsperada\":0}","{\"operacion\":null,\"revisionEsperada\":0}",
            "{\"archivo\":null,\"revisionEsperada\":0}","{\"operacion\":null,\"archivo\":null}",
            "{\"operacion\":null,\"archivo\":\"11111111-1111-4111-8111-111111111111\",\"revisionEsperada\":0}"})
    void omitirElDestinoNuncaSeConvierteEnDeseleccion(String cuerpo){
        assertThrows(JsonProcessingException.class,()->json.readValue(cuerpo,MaterialIaDtos.SeleccionFoto.class));verifyNoInteractions(resultados);
    }
    @Test void originalRequiereParNuloExplicitoYVersionExplicita()throws Exception{
        var seleccion=json.readValue("{\"operacion\":null,\"archivo\":null,\"revisionEsperada\":0}",MaterialIaDtos.SeleccionFoto.class);
        assertNull(seleccion.operacion());assertNull(seleccion.archivo());assertEquals(0,seleccion.revisionEsperada());
    }
    @ParameterizedTest @ValueSource(strings={"protegida","guardada","venceEn","cuotaLiberada","coste","actor"})
    void laGestionNoAceptaResultadosNiPermisosDeclarados(String campo)throws Exception{
        String cuerpo=json.writeValueAsString(Map.of("revisionEsperada",0,campo,true));
        assertThrows(JsonProcessingException.class,()->json.readValue(cuerpo,MaterialIaDtos.GestionVersion.class));
    }
    @Test void noQuedanRutasDeEleccionPorJobNiEliminacionGenerica(){
        var rutas=Arrays.stream(MaterialIaController.class.getDeclaredMethods()).filter(m->m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class))
                .flatMap(m->Arrays.stream(m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())).toList();
        assertFalse(rutas.contains("/material-ia/{operacion}/archivos/{archivo}/decision"));
        assertFalse(Arrays.stream(MaterialIaController.class.getDeclaredMethods()).anyMatch(m->m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)));
    }
    @Test void dtoInstruccionEsOpcionalYSinTextoPorDefecto()throws Exception{
        String datos="{\"operacion\":\"11111111-1111-4111-8111-111111111111\",\"accion\":\"REDACTAR_FICHA\",\"recurso\":{\"tipo\":\"PROPIEDAD\",\"referencia\":\"7\"}}";
        assertNull(json.readValue(datos,MaterialIaDtos.Solicitud.class).aCore().instruccion());
    }
    @Test void puertaHttpDeclaraAgenteActualYSinTransaccionEnvolvente(){
        assertEquals("hasRole('AGENTE')",MaterialIaController.class.getAnnotation(PreAuthorize.class).value());
        assertNull(MaterialIaController.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        for(var metodo:MaterialIaController.class.getDeclaredMethods())assertNull(metodo.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}
