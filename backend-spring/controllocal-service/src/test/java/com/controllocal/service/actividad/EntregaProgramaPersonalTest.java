package com.controllocal.service.actividad;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.ProgramaPersonalRepository.*;
import com.controllocal.service.*;
import com.controllocal.service.planes.DerechosDeInformesC7;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EntregaProgramaPersonalTest {
    final ProgramaPersonalRepository repo=mock(ProgramaPersonalRepository.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final GeneradorProgramaPersonal generador=mock(GeneradorProgramaPersonal.class);
    final DerechosDeInformesC7 derechos=mock(DerechosDeInformesC7.class);
    final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final ProgramasPersonales servicio=new ProgramasPersonales(repo,contexto,derechos,generador,mock(ComandosIdempotentes.class),mock(ComandoIdempotenteRepository.class),eventos,json,60);
    final Actor a=new Actor(1,2,3,"AGENTE");final UUID ref=UUID.randomUUID(),oc=UUID.randomUUID();final Instant ahora=Instant.parse("2026-11-10T15:00:00Z");
    final ProgramasPersonales.PruebaEntrega prueba=new ProgramasPersonales.PruebaEntrega(List.of(new ProgramasPersonales.Recurso("PROSPECCION",5,1L)),List.of(),List.of());
    Programa programa(){return new Programa(1,1,ref,2,3,0,0,"ACTIVO","{}",ahora,ahora.plusSeconds(600),300,2,1,ahora,ahora,null);}
    Paso paso(){return new Paso(1,1,1,oc,0,ahora,"COMPLETADA",1,UUID.randomUUID(),ahora.plusSeconds(60),json.objetoCanonico(prueba),null,ahora);}
    @BeforeEach void montar(){when(contexto.ahora()).thenReturn(ahora);when(repo.propio(1,3,ref,false)).thenReturn(Optional.of(programa()));when(repo.paso(1,oc)).thenReturn(Optional.of(paso()));}
    @Test void noHidrataElResultadoSiSeRevocaUnaFuenteOriginal(){doThrow(new AccesoNoAutorizadoException()).when(generador).exigirEntrega(prueba,a);assertThrows(AccesoNoAutorizadoException.class,()->servicio.resultado(ref,oc,a));verify(repo,never()).resultadoAutorizado(any(),any());}
    @Test void noHidrataUnaOcurrenciaDeOtroPrograma(){var p=paso();when(repo.paso(1,oc)).thenReturn(Optional.of(new Paso(p.id(),1,99,oc,0,ahora,p.estado(),1,p.reclamo(),p.lease(),p.prueba(),null,ahora)));assertThrows(NoEncontradoException.class,()->servicio.resultado(ref,oc,a));verify(repo,never()).resultadoAutorizado(any(),any());verifyNoInteractions(generador);}
    @Test void noConfirmaLaConfiguracionSiPierdeProAntesDeConservar(){
        var q=new ProgramasPersonales.Configuracion(ProgramasPersonales.Familia.ALERTA_CONDICIONADA,"Revisar TEST",null,null,new ProgramasPersonales.Condicion(ProgramasPersonales.Metrica.OPORTUNIDADES_ABIERTAS,ProgramasPersonales.Comparador.IGUAL,java.math.BigDecimal.ZERO),null,ahora,ahora.plusSeconds(3600),60,2,2,"BANDEJA_PRIVADA");
        when(repo.crear(eq(1L),eq(2L),eq(3L),anyString(),eq(ahora),any(),eq(60L),eq(2),eq(ahora))).thenReturn(programa());
        when(derechos.exigirPro(a,ahora)).thenReturn(null).thenThrow(new CapacidadDeshabilitadaException());
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.configurar(new ProgramasPersonales.DatosConfiguracion(null,null,q),a,null,"pro-durante-configuracion",Procedencia.deLaPantalla()));
        verifyNoInteractions(eventos);
    }
}
