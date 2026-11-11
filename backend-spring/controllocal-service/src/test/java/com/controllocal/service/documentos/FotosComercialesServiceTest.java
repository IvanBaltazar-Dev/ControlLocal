package com.controllocal.service.documentos;

import com.controllocal.domain.inmueble.CopiaComercialFoto;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.Documentos;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reutilización de una copia exacta, sin omitir acceso al original ni su integridad. */
class FotosComercialesServiceTest {
    private final Actor actor=new Actor(1,2,3,Actor.AGENTE);
    private final UUID original=UUID.randomUUID();
    private final byte[] bytes={1,2,3};
    private final OffsetDateTime fecha=OffsetDateTime.parse("2026-11-01T15:00:00Z");
    private final FotoPropiedadService fotos=mock(FotoPropiedadService.class);
    private final ArchivosFoto archivos=mock(ArchivosFoto.class);
    private final EvaluacionTecnicaFoto evaluador=mock(EvaluacionTecnicaFoto.class);
    private final CopiaComercialFotoRepository copias=mock(CopiaComercialFotoRepository.class);
    private final ContextosDeActividad contextos=mock(ContextosDeActividad.class);
    private final ComandoIdempotenteRepository locks=mock(ComandoIdempotenteRepository.class);
    private final Documentos json=mock(Documentos.class);
    private final FotoPropiedadRepository originales=mock(FotoPropiedadRepository.class);
    private final FotosComercialesService servicio=new FotosComercialesService(fotos,archivos,evaluador,
            copias,contextos,locks,json,originales);
    private CopiaComercialFoto previa;

    @BeforeEach void preparar(){
        when(fotos.listar(7,actor)).thenReturn(List.of(new FotoPropiedadService.FotoDePropiedad(11L,original.toString(),"original.png",1)));
        when(originales.fechaRegistroOriginal(1,original)).thenReturn(Optional.of(fecha));
        when(archivos.original(original,actor)).thenReturn(bytes);
        when(copias.originalesConCopia(1,List.of(original),EvaluacionTecnicaFoto.VERSION)).thenReturn(List.of(original));
        previa=CopiaComercialFoto.de(1,7,11,original,EvaluacionTecnicaFoto.huella(bytes),"b".repeat(64),
                "PRIVADA",80,60,"{}",3,fecha.toInstant(),fecha.toInstant());
        when(copias.findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(1,original,
                EvaluacionTecnicaFoto.huella(bytes),EvaluacionTecnicaFoto.VERSION)).thenReturn(Optional.of(previa));
    }

    @Test void reutilizaLaCopiaExactaSinDecodificarNiGuardarDeNuevo(){
        var resultado=servicio.prepararCopias(7,List.of(original),actor).get(original);
        assertEquals(previa.getReferencia(),resultado.referencia());
        assertEquals(original,resultado.original());assertEquals(fecha.toInstant(),resultado.originalRegistradoEn());
        verify(archivos).original(original,actor);
        verifyNoInteractions(evaluador);verify(archivos,never()).guardarCopia(anyLong(),any(),anyString(),any(),any());
        verify(copias,never()).saveAndFlush(any());
        var orden=inOrder(locks,copias);
        orden.verify(locks).bloquearLecturaIdentidad(anyLong());
        orden.verify(copias).findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(1,original,
                EvaluacionTecnicaFoto.huella(bytes),EvaluacionTecnicaFoto.VERSION);
    }

    @Test void bytesDiferentesNoReutilizanNiAsciendenALockEscritor(){
        byte[] nuevos={4,5,6};when(archivos.original(original,actor)).thenReturn(nuevos);
        assertThrows(ConflictoException.class,()->servicio.prepararCopias(7,List.of(original),actor));
        verify(copias).findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(1,original,
                EvaluacionTecnicaFoto.huella(nuevos),EvaluacionTecnicaFoto.VERSION);
        verify(copias,never()).saveAndFlush(any());
        verify(locks).bloquearLecturaIdentidad(anyLong());verify(locks,never()).bloquearIdentidad(anyLong());
        verifyNoInteractions(evaluador);
    }

    @Test void originalInaccesibleNoSeSupleConLaCopia(){
        when(archivos.original(original,actor)).thenThrow(new NoEncontradoException("Fotografía"));
        assertThrows(NoEncontradoException.class,()->servicio.prepararCopias(7,List.of(original),actor));
        verifyNoInteractions(evaluador);verify(copias,never()).saveAndFlush(any());
        verify(copias,never()).findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(anyLong(),any(),anyString(),anyString());
    }

    @Test void revocarLecturaImpideConsultarLaCopiaPersistida(){
        when(fotos.listar(7,actor)).thenThrow(new NoEncontradoException("Propiedad"));
        var error=assertThrows(NoEncontradoException.class,()->servicio.prepararCopias(7,List.of(original),actor));
        assertEquals(new NoEncontradoException("Fotografía").getMessage(),error.getMessage());
        verifyNoInteractions(archivos,copias,evaluador);
    }

    @Test void falloTecnicoDeLaLecturaNoSeConvierteEnFotografiaAusente(){
        var fallo=new IllegalStateException("Lectura técnica fallida");
        when(fotos.listar(7,actor)).thenThrow(fallo);
        assertSame(fallo,assertThrows(IllegalStateException.class,()->servicio.prepararCopias(7,List.of(original),actor)));
        verifyNoInteractions(archivos,copias,evaluador,locks);
    }

    @Test void unaIdentidadFisicaInvalidaNoExponeUnErrorDistintoNiLeeLaGaleria(){
        var error=assertThrows(NoEncontradoException.class,()->servicio.evaluar(0,original,actor));
        assertEquals(new NoEncontradoException("Fotografía").getMessage(),error.getMessage());
        verifyNoInteractions(fotos,archivos,copias,evaluador,locks);
    }

    @Test void originalAusenteNoLeeNiTransformaBinarios(){
        assertThrows(NoEncontradoException.class,()->servicio.evaluar(7,null,actor));
        verifyNoInteractions(fotos,archivos,copias,evaluador,locks);
    }

    @Test void revocacionDespuesDeLeerLaCopiaImpideEntregarla(){
        var copia=CopiaComercialFoto.de(1,7,11,original,EvaluacionTecnicaFoto.huella(bytes),
                EvaluacionTecnicaFoto.huella(bytes),"PRIVADA",80,60,"{}",3,fecha.toInstant(),fecha.toInstant());
        when(copias.findByOrganizacionIdAndReferencia(1,copia.getReferencia())).thenReturn(Optional.of(copia));
        when(archivos.leerCopia("PRIVADA",actor)).thenReturn(bytes);
        when(fotos.listar(7,actor))
                .thenReturn(List.of(new FotoPropiedadService.FotoDePropiedad(11L,original.toString(),"original.png",1)))
                .thenThrow(new NoEncontradoException("Propiedad"));
        assertThrows(NoEncontradoException.class,()->servicio.obtener(copia.getReferencia(),actor));
        verify(archivos).leerCopia("PRIVADA",actor);
        verify(fotos,times(2)).listar(7,actor);
        verifyNoInteractions(evaluador);
    }

    @Test void otraOrganizacionNoObtieneLaCopiaPorConocerElOriginal(){
        Actor ajeno=new Actor(9,2,3,Actor.AGENTE);
        assertThrows(NoEncontradoException.class,()->servicio.prepararCopias(7,List.of(original),ajeno));
        verifyNoInteractions(archivos,copias,evaluador);
    }
    @Test void unaFotoAjenaEnElLoteImpideLeerBinariosOMetadataDeCopias(){
        UUID ajena=UUID.randomUUID();
        assertThrows(NoEncontradoException.class,()->servicio.prepararCopias(7,List.of(original,ajena),actor));
        verifyNoInteractions(archivos,copias,evaluador,locks);
    }

    @Test void copiaSinAntecedenteRequiereEvaluacionYUnaSolaEscritura(){
        when(copias.originalesConCopia(1,List.of(original),EvaluacionTecnicaFoto.VERSION)).thenReturn(List.of());
        when(copias.findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(1,original,
                EvaluacionTecnicaFoto.huella(bytes),EvaluacionTecnicaFoto.VERSION)).thenReturn(Optional.empty());
        var evaluacion=new EvaluacionTecnicaFoto.Evaluacion("PNG",80,60,1,"HORIZONTAL",80,60,50,
                EvaluacionTecnicaFoto.huella(bytes),EvaluacionTecnicaFoto.VERSION);
        var procesada=new EvaluacionTecnicaFoto.Copia(evaluacion,"b".repeat(64),"image/png",new byte[]{9});
        when(evaluador.evaluar(bytes)).thenReturn(procesada);
        when(archivos.guardarCopia(7,original,procesada.huella(),procesada.contenido(),actor)).thenReturn("PRIVADA");
        when(contextos.ahora()).thenReturn(fecha.toInstant());
        when(json.objetoCanonico(evaluacion)).thenReturn("{}");
        var resultado=servicio.prepararCopias(7,List.of(original),actor).get(original);
        assertEquals(original,resultado.original());
        verify(evaluador).evaluar(bytes);verify(copias).saveAndFlush(any(CopiaComercialFoto.class));
        verify(locks).bloquearIdentidad(anyLong());verify(locks,never()).bloquearLecturaIdentidad(anyLong());
    }
    @Test void unaCopiaCreadaMientrasEsperabaElFenceExclusivoSeReutiliza(){
        when(copias.originalesConCopia(1,List.of(original),EvaluacionTecnicaFoto.VERSION)).thenReturn(List.of());
        assertEquals(previa.getReferencia(),servicio.prepararCopias(7,List.of(original),actor).get(original).referencia());
        verify(locks).bloquearIdentidad(anyLong());verifyNoInteractions(evaluador);
        verify(copias,never()).saveAndFlush(any());
    }
    @Test void expectativaReutilizablePerdidaNoHaceUpgradeNiOtraEscritura(){
        when(copias.findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(1,original,
                EvaluacionTecnicaFoto.huella(bytes),EvaluacionTecnicaFoto.VERSION)).thenReturn(Optional.empty());
        assertThrows(ConflictoException.class,()->servicio.prepararCopias(7,List.of(original),actor));
        verify(locks).bloquearLecturaIdentidad(anyLong());verify(locks,never()).bloquearIdentidad(anyLong());
        verifyNoInteractions(evaluador);verify(copias,never()).saveAndFlush(any());
    }
}
