package com.controllocal.service.actividad;

import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoInteraccionesTest {
    @Test void publicaElDiccionarioUnicoYLosSelectoresLocalesSinEquivalenciasLegadas(){
        var d=CatalogoInteracciones.definicion();
        assertEquals(3,d.tipos().size());assertEquals(8,d.canales().size());assertEquals(6,d.eventos().size());assertEquals(5,d.resultados().size());
        assertEquals(Set.of("DESTINATARIO","INTERLOCUTOR"),d.referenciasLocales().stream().map(ReferenciaLocal::valor).collect(java.util.stream.Collectors.toSet()));
        assertTrue(d.semantica().contains("comunicación real"));
    }
    @Test void conservaUnaComunicacionRealConDimensionesDesconocidas(){
        assertDoesNotThrow(()->CatalogoInteracciones.validar(null,null,null,Set.of(),null));
        assertDoesNotThrow(()->CatalogoInteracciones.validar(TipoActividad.LLAMADA,null,null,Set.of(EventoComunicacion.LLAMADA_SALIENTE),null));
    }
    @Test void elIntentoDeEnvioFallidoYaDeclaraSuResultadoConocido(){
        assertThrows(ReglaNegocioException.class,()->CatalogoInteracciones.validar(TipoActividad.MENSAJERIA,CanalComunicacion.SMS,null,Set.of(EventoComunicacion.ENVIO_FALLIDO),null));
        assertDoesNotThrow(()->CatalogoInteracciones.validar(TipoActividad.MENSAJERIA,CanalComunicacion.SMS,null,Set.of(EventoComunicacion.ENVIO_FALLIDO),ResultadoInteraccion.COMUNICACION_FALLIDA));
    }
    @Test void unEnvioFallidoNoEsUnMensajeEfectivamenteEnviado(){
        assertThrows(ReglaNegocioException.class,()->CatalogoInteracciones.validar(TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,null,Set.of(EventoComunicacion.ENVIO_FALLIDO,EventoComunicacion.MENSAJE_ENVIADO),ResultadoInteraccion.COMUNICACION_FALLIDA));
    }
    @Test void unIntercambioDeMensajeriaNecesitaAmbosSentidosConocidos(){
        assertThrows(ReglaNegocioException.class,()->CatalogoInteracciones.validar(TipoActividad.MENSAJERIA,CanalComunicacion.CORREO_ELECTRONICO,null,Set.of(EventoComunicacion.MENSAJE_ENVIADO),ResultadoInteraccion.INTERCAMBIO_EFECTIVO));
        assertDoesNotThrow(()->CatalogoInteracciones.validar(TipoActividad.MENSAJERIA,CanalComunicacion.CORREO_ELECTRONICO,null,Set.of(EventoComunicacion.MENSAJE_ENVIADO,EventoComunicacion.MENSAJE_RECIBIDO),ResultadoInteraccion.INTERCAMBIO_EFECTIVO));
    }
    @Test void responderDespuesNoSeFusionaEnUnaLlamadaAnterior(){
        assertThrows(ReglaNegocioException.class,()->CatalogoInteracciones.validar(TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,Set.of(EventoComunicacion.LLAMADA_SALIENTE,EventoComunicacion.LLAMADA_ENTRANTE),ResultadoInteraccion.INTERCAMBIO_EFECTIVO));
    }
    @Test void otroIdentificadoNoSustituyeUnMedioDesconocido(){
        assertThrows(ReglaNegocioException.class,()->CatalogoInteracciones.validar(null,CanalComunicacion.OTRO_IDENTIFICADO,null,Set.of(),null));
        assertDoesNotThrow(()->CatalogoInteracciones.validar(null,CanalComunicacion.OTRO_IDENTIFICADO,"Medio declarado",Set.of(),null));
    }
    @Test void laLlamadaPerdidaYLaSalienteSinRespuestaTienenRotulosDistintos(){
        assertEquals("Llamada perdida",CatalogoInteracciones.rotulo(ResultadoInteraccion.SIN_RESPUESTA,Set.of(EventoComunicacion.LLAMADA_ENTRANTE)));
        assertEquals("No respondieron",CatalogoInteracciones.rotulo(ResultadoInteraccion.SIN_RESPUESTA,Set.of(EventoComunicacion.LLAMADA_SALIENTE)));
        assertNull(CatalogoInteracciones.rotulo(null,Set.of()));
    }
    @Test void publicaFinalidadYRelacionesConocidasSinConvertirLaDeclaracionEnVocabularioCerrado(){
        var d=CatalogoInteracciones.definicion();
        assertEquals(List.of(SeguimientoDeProspeccion.FINALIDAD),d.finalidadesRestriccionConocidas().stream().map(ValorContextual::valor).toList());
        assertEquals(SeguimientoDeProspeccion.relacionesDeclaradasDeRecontacto(),d.relacionesDeclaradasParaRecontacto().stream().map(ValorContextual::valor).toList());
        assertTrue(d.relacionesDeclaradasParaRecontacto().stream().allMatch(o->o.rotulo().contains("declarado")));
        assertEquals(Set.of("GESTION_COMERCIAL","COORDINACION_ACCESO","INFORMACION_CONTACTO","POR_PRECISAR"),d.funcionesContacto().stream().map(ValorContextual::valor).collect(java.util.stream.Collectors.toSet()));
        assertDoesNotThrow(()->CatalogoInteracciones.validar(null,null,null,Set.of(),null));
    }
}
