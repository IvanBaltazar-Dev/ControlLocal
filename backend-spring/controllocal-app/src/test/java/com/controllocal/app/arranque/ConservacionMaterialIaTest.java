package com.controllocal.app.arranque;

import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.materialia.ResultadosMaterialIa.*;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Sólo adaptador técnico; no certifica caducidad real, retirada/B0 ni ejecución programada en runtime. */
class ConservacionMaterialIaTest {
    private final ResultadosMaterialIa core=mock(ResultadosMaterialIa.class);
    private final AlmacenDocumentos almacen=mock(AlmacenDocumentos.class);
    private final ConservacionMaterialIa barrido=new ConservacionMaterialIa(core,almacen);
    private final String ns="a".repeat(64);
    private final UUID primera=UUID.randomUUID(),segunda=UUID.randomUUID();
    private final CursorConservacion cursor=new CursorConservacion(1,primera);
    private CandidatoConservacion candidata(UUID id){return new CandidatoConservacion(1,id,2);}
    private void almacen(){when(almacen.identidadTecnica()).thenReturn(ns);}

    @Test void flagEspecificoApagadoPorDefectoYCadenciaTecnicaNoSla()throws Exception{
        var condicion=ConservacionMaterialIa.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"controllocal.material-ia.conservacion.habilitada"},condicion.name());assertEquals("true",condicion.havingValue());assertFalse(condicion.matchIfMissing());
        var periodo=ConservacionMaterialIa.class.getMethod("tick").getAnnotation(Scheduled.class);
        assertEquals("${controllocal.material-ia.conservacion.intervalo-ms:60000}",periodo.fixedDelayString());
        assertFalse(ConservacionMaterialIa.class.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
        assertFalse(ConservacionMaterialIa.class.getMethod("conciliar").isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }
    @Test void arranqueYTicUsanMismoRecorridoAcotadoYReinicianAlFin(){
        almacen();when(core.candidatosConservacion(ns,null,128)).thenReturn(new LoteConservacion(List.of(candidata(primera)),cursor,false));
        when(core.candidatosConservacion(ns,cursor,128)).thenReturn(new LoteConservacion(List.of(candidata(segunda)),new CursorConservacion(1,segunda),true));
        barrido.alArrancar();barrido.tick();barrido.tick();
        verify(core,times(2)).candidatosConservacion(ns,null,128);verify(core).candidatosConservacion(ns,cursor,128);
        verify(core,times(2)).conciliarCaducidad(1,primera);verify(core).conciliarCaducidad(1,segunda);
    }
    @Test void unFalloPorItemQuedaTrazableYNoImpideElSiguiente(){
        almacen();when(core.candidatosConservacion(ns,null,128)).thenReturn(new LoteConservacion(List.of(candidata(primera),candidata(segunda)),null,true));
        doThrow(new IllegalStateException("Fallo TEST")).when(core).conciliarCaducidad(1,primera);
        var r=barrido.conciliar();assertEquals(2,r.candidatas());assertEquals(1,r.conciliadas());assertEquals(1,r.fallos());assertFalse(r.omitida());
        verify(core).conciliarCaducidad(1,segunda);barrido.conciliar();verify(core,times(2)).conciliarCaducidad(1,primera);
    }
    @Test void falloDeConsultaConservaCursorParaElReintento(){
        almacen();when(core.candidatosConservacion(ns,null,128)).thenReturn(new LoteConservacion(List.of(candidata(primera)),cursor,false));
        when(core.candidatosConservacion(ns,cursor,128)).thenThrow(new IllegalStateException("Consulta TEST"));
        assertEquals(0,barrido.conciliar().fallos());assertEquals(1,barrido.conciliar().fallos());barrido.conciliar();
        verify(core,times(2)).candidatosConservacion(ns,cursor,128);verify(core,times(1)).conciliarCaducidad(1,primera);
    }
    @Test void noActuaSobreUnLoteDuplicadoNiUnCursorQueNoAvanza(){
        almacen();when(core.candidatosConservacion(ns,null,128)).thenReturn(new LoteConservacion(List.of(candidata(primera),candidata(primera)),null,true));
        assertEquals(1,barrido.conciliar().fallos());verify(core,never()).conciliarCaducidad(anyLong(),any());
        when(core.candidatosConservacion(ns,null,128)).thenReturn(new LoteConservacion(List.of(),null,false));
        assertEquals(1,barrido.conciliar().fallos());verify(core,never()).conciliarCaducidad(anyLong(),any());
    }
    @Test void cambioDeNamespaceNoBorraEnOtroAlmacen(){
        when(almacen.identidadTecnica()).thenReturn(ns,"b".repeat(64));
        when(core.candidatosConservacion(ns,null,128)).thenReturn(new LoteConservacion(List.of(candidata(primera)),null,true));
        assertEquals(1,barrido.conciliar().fallos());verify(core,never()).conciliarCaducidad(anyLong(),any());
    }
    @Test void dosEntradasEnLaMismaInstanciaNoSuperponenElRecorrido()throws Exception{
        almacen();var dentro=new CountDownLatch(1);var salir=new CountDownLatch(1);
        when(core.candidatosConservacion(ns,null,128)).thenAnswer(i->{dentro.countDown();if(!salir.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrera TEST");return new LoteConservacion(List.of(),null,true);});
        try(var pool=Executors.newSingleThreadExecutor()){
            var primera=pool.submit(barrido::conciliar);assertTrue(dentro.await(5,TimeUnit.SECONDS));
            assertTrue(barrido.conciliar().omitida());salir.countDown();assertFalse(primera.get(5,TimeUnit.SECONDS).omitida());
        }finally{salir.countDown();}
        verify(core,times(1)).candidatosConservacion(ns,null,128);
    }
}
