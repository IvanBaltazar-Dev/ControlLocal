package com.controllocal.service.actividad;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecibosDeActividadTest {
    private final Actor actor=new Actor(1,2,3,Actor.AGENTE);
    private final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final RecibosDeActividad recibos=new RecibosDeActividad(comandos,registros,eventos,documentos);
    private final Procedencia fuente=new Procedencia("SPA",null,null,null,null,null,null,"Teléfono privado 987654321",null);

    @Test void outboxNoCopiaContenidoPrivadoYReciboConservaElOriginal() {
        var datos=Map.of("observaciones","Nota privada");
        var resultado=Map.of("id",5L,"version",0L,"telefono","987654321","observaciones","Nota privada");
        recibos.registrar(actor,"c4-privado",RecibosDeActividad.RECTIFICAR,datos,fuente,"INTERACCION",5,resultado);
        var evento=ArgumentCaptor.forClass(EventoDominio.class);
        verify(eventos).saveAndFlush(evento.capture());
        assertNull(evento.getValue().getPeticion());
        assertEquals(java.util.Set.of("version","claveComando","huellaResultado"),documentos.comoMapa(evento.getValue().getCargaUtil()).keySet());
        assertFalse(evento.getValue().getCargaUtil().contains("987654321"));
        verify(comandos).registrar(eq(actor),eq("c4-privado"),eq(RecibosDeActividad.RECTIFICAR),anyString(),eq("INTERACCION"),eq(5L),eq(fuente),eq(documentos.objetoCanonico(resultado)));
    }
    @Test void unResultadoSinVersionNoPublicaUnHechoNiUnReciboAparente() {
        assertThrows(IllegalStateException.class,()->recibos.registrar(actor,"c4-incompleto",RecibosDeActividad.ABRIR,Map.of(),fuente,"OPORTUNIDAD",5,Map.of("id",5)));
        verifyNoInteractions(eventos,comandos);
    }
    @Test void reutilizarClaveConOtroContenidoEsConflictoYNoOtroEfecto() {
        when(comandos.buscar(eq(actor),eq("c4-reintento"),eq(RecibosDeActividad.CANCELAR),anyString()))
                .thenThrow(new ReglaNegocioException("Otro contenido"));
        assertThrows(ConflictoException.class,()->recibos.buscarOriginal(actor,"c4-reintento",RecibosDeActividad.CANCELAR,Map.of("idVisita",7),fuente));
        verify(registros).bloquearIdentidad(anyLong());
        verifyNoInteractions(eventos);
    }
}
