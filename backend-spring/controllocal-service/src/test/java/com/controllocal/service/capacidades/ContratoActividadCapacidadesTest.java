package com.controllocal.service.capacidades;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** El contrato que consumen Web/KAIROS debe poder expresar los DTO reales de C4. */
class ContratoActividadCapacidadesTest {
    private final RegistroDeCapacidades registro=new RegistroDeCapacidades("");
    @Test void filtrosDeInformeSonCerradosYLaCohorteMantieneSuCriterioIndependiente(){
        var consulta=(Map<?,?>)((Map<?,?>)registro.de("preparar_informe_comercial").esquema().get("properties")).get("consulta");
        var campos=(Map<?,?>)consulta.get("properties");var filtro=(Map<?,?>)((List<?>)((Map<?,?>)campos.get("filtros")).get("anyOf")).getFirst();
        assertEquals(false,consulta.get("additionalProperties"));assertEquals(false,filtro.get("additionalProperties"));
        var fc=(Map<?,?>)filtro.get("properties");assertEquals(java.util.Set.of("operacion","responsable","tipoActividad","situacion","hechoEconomico"),fc.keySet());
        var situacion=(Map<?,?>)((List<?>)((Map<?,?>)fc.get("situacion")).get("anyOf")).getFirst();
        assertEquals(List.of("recurso","codigo"),situacion.get("required"));assertEquals(false,situacion.get("additionalProperties"));
        var op=(Map<?,?>)((List<?>)((Map<?,?>)campos.get("operacionCohorte")).get("anyOf")).getFirst();assertEquals(List.of("VENTA","ALQUILER"),op.get("enum"));
    }
    @Test void funcionYFinalidadComercialSonCompartidasSinCuotaNiPoderDeFirma(){
        var campos=(Map<?,?>)registro.de("registrar_interaccion").esquema().get("properties");
        var gestion=(Map<?,?>)campos.get("gestionContacto");var datos=(Map<?,?>)gestion.get("properties");
        assertTrue(datos.keySet().containsAll(List.of("finalidades","funcionesInterlocutor","fuenteFunciones","continuarConInterlocutor","respondeAIntento")));
        assertFalse(datos.containsKey("cuota"));assertFalse(datos.containsKey("poder"));assertEquals(false,gestion.get("additionalProperties"));
    }
    @Test void laPresentacionManuscritaPublicaCuatroDatosSinOtraEntradaDeAceptacion(){
        for(String capacidad:List.of("presentar_peticion_encargo","reenviar_peticion_encargo")){
            var campos=(Map<?,?>)registro.de(capacidad).esquema().get("properties");
            var presentacion=(Map<?,?>)campos.get("presentacion");
            var datos=(Map<?,?>)presentacion.get("properties");
            assertFalse(datos.containsKey("evidenciasAceptacion"));
            var firma=(Map<?,?>)((Map<?,?>)datos.get("firmas")).get("items");
            assertEquals(List.of("firmante","fechaFirma","documentoFirmado","descripcion"),firma.get("required"));
            assertEquals(CapacidadesService.Interaccion.HUMANO,registro.de(capacidad).interaccion());
        }
        var decision=registro.de("decidir_captacion");
        assertEquals(CapacidadesService.Interaccion.CONFIRMA,decision.interaccion());
        var revision=(Map<?,?>)((Map<?,?>)decision.esquema().get("properties")).get("revisionFirma");
        var revisados=(Map<?,?>)((Map<?,?>)revision.get("properties")).get("documentoYFirmantesRevisados");
        assertEquals("boolean",revisados.get("type"));assertFalse(revisados.containsKey("default"));
    }
    @Test void cadaCampoObligatorioEstaPublicadoEnElMismoEsquema() {
        for(var d:registro.todas()) {
            var propiedades=(Map<?,?>)d.esquema().get("properties");
            for(Object clave:(List<?>)d.esquema().getOrDefault("required",List.of()))
                assertTrue(propiedades.containsKey(clave),d.idCore()+": "+clave);
        }
    }
    @Test void contraofertaRecibeLaVersionDeOfertaSinCambiarElNombreDelDTO() {
        var esquema=registro.de("registrar_contraoferta").esquema();
        assertTrue(((List<?>)esquema.get("required")).contains("versionOferta"));
        assertFalse(((Map<?,?>)esquema.get("properties")).containsKey("versionOfertaAnterior"));
    }
    @Test void fechaCivilYEvidenciaDeNegociacionSonDeclaracionesSinConversionImplicita() {
        var campos=(Map<?,?>)registro.de("presentar_oferta").esquema().get("properties");
        assertEquals("date",((Map<?,?>)campos.get("fechaHecho")).get("format"));
        assertEquals("string",((Map<?,?>)campos.get("evidencia")).get("type"));
        assertEquals("object",((Map<?,?>)campos.get("importe")).get("type"));
    }
    @Test void planificarSinFechaEsExpresableSinFechaNiVersionInventadas() {
        var d=registro.de("planificar_seguimiento_prospeccion");
        var campos=(Map<?,?>)d.esquema().get("properties");
        assertEquals(List.of("string","null"),((Map<?,?>)campos.get("fecha")).get("type"));
        assertTrue(((List<?>)d.esquema().get("required")).containsAll(List.of("versionEsperada","origen","detalle")));
        assertEquals(CapacidadesService.Interaccion.AVISA,d.interaccion());
        assertEquals(CapacidadesService.Efecto.ESCRITURA,d.efecto());
    }
    @Test void capturaParcialNoExigeResultadosNiCanalesDeRelleno() {
        assertEquals(List.of(),registro.de("registrar_interaccion").esquema().get("required"));
        assertThrows(com.controllocal.service.excepcion.NoEncontradoException.class,()->registro.de("resultados_de_interaccion"));
        assertNotNull(registro.de("definicion_de_interacciones"));
    }
    @Test void finalidadYRelacionSeDescubrenEnCoreSinCerrarUnaDeclaracionAbierta() {
        var campos=(Map<?,?>)registro.de("registrar_interaccion").esquema().get("properties");
        var restriccion=(Map<?,?>)((Map<?,?>)campos.get("restriccion")).get("properties");
        var finalidad=(Map<?,?>)restriccion.get("finalidad");
        assertTrue(((List<?>)finalidad.get("examples")).contains(com.controllocal.service.actividad.SeguimientoDeProspeccion.FINALIDAD));
        assertFalse(finalidad.containsKey("enum"));assertFalse(finalidad.containsKey("default"));
        var destinatario=(Map<?,?>)((Map<?,?>)campos.get("destinatario")).get("properties");
        var relacion=(Map<?,?>)destinatario.get("tipoDeclarado");
        assertTrue(((List<?>)relacion.get("examples")).containsAll(List.of("PROPIETARIO","REPRESENTANTE")));
        assertFalse(relacion.containsKey("enum"));assertFalse(relacion.containsKey("default"));
    }
}
