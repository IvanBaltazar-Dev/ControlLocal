package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.ComisionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Frontera Map/DTO: un comando retirado jamás se interpreta como dinero recibido. */
class EfectosComisionDeActuacionTest {
    private final ComisionService core=mock(ComisionService.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Actor actor=new Actor(7,30,41,Actor.BROKER);
    private final EfectosComisionDeActuacion efecto=new EfectosComisionDeActuacion(core,json);

    static Stream<Arguments> selectoresRetirados(){return Stream.of("P","R","C","null")
            .flatMap(tipo->Stream.of(true,false).map(primero->Arguments.of(tipo,primero)));}
    @ParameterizedTest @MethodSource("selectoresRetirados")
    void rechazaSelectorInclusoNuloSinIgnorarloNiConvertirloEnC(String tipo,boolean primero){
        var datos=new LinkedHashMap<String,Object>();
        if(primero)datos.put("tipo","null".equals(tipo)?null:tipo);
        datos.put("comision",7);datos.put("versionEsperada",2);datos.put("monto",5000);
        datos.put("moneda","PEN");datos.put("fecha","2026-10-31");
        if(!primero)datos.put("tipo","null".equals(tipo)?null:tipo);
        RuntimeException error=assertThrows(RuntimeException.class,()->efecto.validar("registrar_cobro",datos,actor));
        assertTrue(causaDeRegla(error));verifyNoInteractions(core);
    }
    @ParameterizedTest @CsvSource({"reprogramar_gestion_cobro,monto","concluir_gestion_cobro,concluida"})
    void gestionNoAdmiteUnSegundoEfectoOculto(String capacidad,String extra){
        var datos=new LinkedHashMap<String,Object>();datos.put("comision",7);datos.put("versionEsperada",2);
        datos.put("motivo","Contacto acordado");if(capacidad.startsWith("reprogramar"))datos.put("proximaRevision","2026-11-04");
        datos.put(extra,null);
        assertTrue(causaDeRegla(assertThrows(RuntimeException.class,()->efecto.validar(capacidad,datos,actor))));
        verifyNoInteractions(core);
    }
    @Test void cobroSinSelectorLlegaAlCasoTipadoSinRegistrarFondos(){
        Map<String,Object> datos=Map.of("comision",7,"versionEsperada",2,"monto",5000,"moneda","PEN","fecha","2026-10-31");
        var entrada=json.convertValue(datos,ComisionService.DatosCobro.class);
        when(core.validarCobro(entrada,actor)).thenReturn(entrada);
        assertEquals(entrada,efecto.validar("registrar_cobro",datos,actor));
        verify(core).validarCobro(entrada,actor);verifyNoMoreInteractions(core);
    }
    @Test void revisionHumanaConservaLaFichaYLosRecibidosAportadosPorCore(){
        Map<String,Object> datos=Map.of("comision",7,"versionEsperada",2,"motivo","Concluir gestión");
        Map<String,Object> resumen=Map.of("gestion","CONCLUIR","recibidos",List.of(Map.of("importe",5000,"moneda","PEN")));
        when(core.previsualizarActuacion("concluir_gestion_cobro",datos,actor)).thenReturn(resumen);
        assertSame(resumen,efecto.resumenRevision("concluir_gestion_cobro",datos,actor));
        verify(core).previsualizarActuacion("concluir_gestion_cobro",datos,actor);verifyNoMoreInteractions(core);
    }
    private static boolean causaDeRegla(Throwable error){
        for(Throwable causa=error;causa!=null;causa=causa.getCause())if(causa instanceof ReglaNegocioException)return true;
        return false;
    }
}
