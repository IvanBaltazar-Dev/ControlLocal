package com.controllocal.service.archivo;

import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository.Intento;
import com.controllocal.service.excepcion.ConflictoException;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unidad de decisiones técnicas. La durabilidad y los locks se prueban con PG real. */
class SeguimientoSalidaBinariaTest {
    final IntentoSalidaBinariaRepository repo=mock(IntentoSalidaBinariaRepository.class);
    final PlatformTransactionManager gestor=mock(PlatformTransactionManager.class);
    final UUID op=UUID.randomUUID(),dueno=UUID.randomUUID();
    final PropietarioBinario propietario=new PropietarioBinario.Organizacion(1);
    final com.controllocal.persistence.archivo.DiarioBinarioTecnico tecnico=mock(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class); SeguimientoSalidaBinaria servicio; final String almacen="c".repeat(64);
    final String hash="a".repeat(64);
    @BeforeEach void iniciar(){when(repo.dueno(1L,null)).thenReturn(dueno);when(tecnico.repositorio()).thenReturn(repo);when(tecnico.gestor()).thenReturn(gestor);servicio=new SeguimientoSalidaBinaria(repo,tecnico);TransactionSynchronizationManager.setActualTransactionActive(true);when(gestor.getTransaction(any())).thenReturn(new SimpleTransactionStatus());}
    @AfterEach void limpiar(){TransactionSynchronizationManager.clear();}
    Intento intento(String estado,boolean incierto){return new Intento(9,1L,op,1,"FUENTE_TEST","tenant/1/salidas/"+op+"/1.pdf","pdf","application/pdf",hash,12,estado,incierto,Instant.now(),null,almacen,dueno,null);}
    SeguimientoSalidaBinaria.IntentoTecnico vista(Intento i){return new SeguimientoSalidaBinaria.IntentoTecnico(i.id(),propietario,i.operacion(),i.numero(),i.fuente(),i.clave(),i.extension(),i.contentType(),i.huella(),i.bytes(),i.estado(),i.escrituraIncierta(),i.ultimaVerificacion(),i.resultadoTecnico(),i.almacenHuella());}
    @Test void registroPrevioConservaFuenteHashYClaveDelServidor(){
        when(repo.ultimo(dueno,op)).thenReturn(Optional.empty());when(repo.insertar(any(UUID.class),any(),anyInt(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString())).thenReturn(intento("PREPARADO",true));
        var p=servicio.preparar(propietario,op,"FUENTE_TEST",almacen,"pdf","application/pdf",hash,12);assertTrue(p.nuevo());
        var orden=inOrder(repo,gestor);orden.verify(repo).bloquear(SeguimientoSalidaBinaria.identidad(propietario,op));orden.verify(gestor).getTransaction(argThat(d->d.getPropagationBehavior()==3));
        verify(repo).insertar(dueno,op,1,"FUENTE_TEST","tenant/1/salidas/"+op+"/1.pdf","pdf","application/pdf",hash,12,almacen);verify(gestor).commit(any());
    }
    @Test void intentoExistenteNoCreaOtroRegistro(){var i=intento("INCIERTO",true);when(repo.ultimo(dueno,op)).thenReturn(Optional.of(i));var p=servicio.preparar(propietario,op,"FUENTE_TEST",almacen,"pdf","application/pdf",hash,12);assertFalse(p.nuevo());assertEquals(vista(i),p.intento());verify(repo,never()).insertar(any(UUID.class),any(),anyInt(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());}
    @Test void noSustituyeContenidoPendiente(){when(repo.ultimo(dueno,op)).thenReturn(Optional.of(intento("INCIERTO",true)));assertThrows(ConflictoException.class,()->servicio.preparar(propietario,op,"FUENTE_TEST",almacen,"pdf","application/pdf","b".repeat(64),12));}
    @Test void retiroVerificadoPermiteOtroIntentoYClaveParaLaMismaAccion(){when(repo.ultimo(dueno,op)).thenReturn(Optional.of(intento("RETIRADO_VERIFICADO",true)));when(repo.insertar(any(UUID.class),any(),anyInt(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString())).thenReturn(intento("PREPARADO",true));assertTrue(servicio.preparar(propietario,op,"FUENTE_TEST",almacen,"png","image/png","b".repeat(64),13).nuevo());verify(repo).insertar(dueno,op,2,"FUENTE_TEST","tenant/1/salidas/"+op+"/2.png","png","image/png","b".repeat(64),13,almacen);}
    @Test void niUnRetiroPermiteCambiarFuenteSilenciosamente(){when(repo.ultimo(dueno,op)).thenReturn(Optional.of(intento("RETIRADO_VERIFICADO",false)));assertThrows(ConflictoException.class,()->servicio.preparar(propietario,op,"OTRA_FUENTE",almacen,"pdf","application/pdf",hash,12));}
    @Test void extensionClienteNoPuedeEscaparDelPrefijo(){assertThrows(IllegalArgumentException.class,()->servicio.preparar(propietario,op,"FUENTE_TEST",almacen,"../pdf","application/pdf",hash,12));verify(repo,never()).insertar(any(UUID.class),any(),anyInt(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());}
    @Test void noIniciaUnaSalidaFueraDeLaTransaccionDelRecurso(){TransactionSynchronizationManager.setActualTransactionActive(false);assertThrows(IllegalStateException.class,()->servicio.bloquear(propietario,op));verifyNoInteractions(repo);}
    @Test void limpiadorNoEsperaNiTocaLaTxActiva(){when(repo.intentarBloqueo(anyLong())).thenReturn(false);SeguimientoSalidaBinaria.Retiro retiro=mock(SeguimientoSalidaBinaria.Retiro.class);assertFalse(servicio.completar(propietario,op,almacen,retiro));verifyNoInteractions(retiro);verify(repo,never()).deOperacion(any(UUID.class),any());}
    @Test void vinculoConfirmadoImpideRetiro(){var i=intento("ESCRITO_VERIFICADO",false);when(repo.intentarBloqueo(anyLong())).thenReturn(true);when(repo.deOperacion(dueno,op)).thenReturn(List.of(i));when(repo.vinculado(dueno,i.clave())).thenReturn(true);SeguimientoSalidaBinaria.Retiro retiro=mock(SeguimientoSalidaBinaria.Retiro.class);assertTrue(servicio.completar(propietario,op,almacen,retiro));verifyNoInteractions(retiro);verify(repo).estado(i,"VINCULADO",false,"VINCULO_CONFIRMADO",true);}
    @Test void custodiaPendienteImpideCleanupSinAfirmarEscrituraOVinculo(){
        var i=intento("PREPARADO",true);when(repo.intentarBloqueo(anyLong())).thenReturn(true);
        when(repo.deOperacion(dueno,op)).thenReturn(List.of(i));when(repo.custodiado(dueno,op,1)).thenReturn(true);
        SeguimientoSalidaBinaria.Retiro retiro=mock(SeguimientoSalidaBinaria.Retiro.class);
        assertTrue(servicio.completar(propietario,op,almacen,retiro));verifyNoInteractions(retiro);
        verify(repo).vinculado(dueno,i.clave());verify(repo).custodiado(dueno,op,1);
        verify(repo,never()).estado(any(),anyString(),anyBoolean(),anyString(),anyBoolean());
    }
    @Test void borrarSinAusenciaVerificadaSiguePendiente(){var i=intento("ESCRITO_VERIFICADO",false);when(repo.intentarBloqueo(anyLong())).thenReturn(true);when(repo.deOperacion(dueno,op)).thenReturn(List.of(i));servicio.completar(propietario,op,almacen,x->false);verify(repo).estado(i,"PENDIENTE_RETIRO",false,"RETIRO_SIN_CONFIRMAR",false);}
    @Test void falloDeRetiroNoSeMarcaLimpio(){var i=intento("INCIERTO",true);when(repo.intentarBloqueo(anyLong())).thenReturn(true);when(repo.deOperacion(dueno,op)).thenReturn(List.of(i));servicio.completar(propietario,op,almacen,x->{throw new IllegalStateException("error proveedor no exportable");});verify(repo).estado(i,"PENDIENTE_RETIRO",true,"RETIRO_SIN_CONFIRMAR",false);}
    @Test void ausenciaConfirmadaConservaFlagDeEscrituraTardiaIncierta(){var i=intento("INCIERTO",true);when(repo.intentarBloqueo(anyLong())).thenReturn(true);when(repo.deOperacion(dueno,op)).thenReturn(List.of(i));servicio.completar(propietario,op,almacen,x->true);verify(repo).estado(i,"RETIRADO_VERIFICADO",true,"AUSENCIA_OBSERVADA",true);}
    @Test void verificarUnVinculoNoLoDegradaAObjetoTemporal(){var i=intento("VINCULADO",false);servicio.verificado(vista(i));verify(repo).estado(i,"VINCULADO",false,"LECTURA_EXACTA",true);}
    @Test void cambiarNamespaceNoReutilizaNiCreaOtroIntento(){when(repo.ultimo(dueno,op)).thenReturn(Optional.of(intento("RETIRADO_VERIFICADO",false)));assertThrows(ConflictoException.class,()->servicio.preparar(propietario,op,"FUENTE_TEST","d".repeat(64),"pdf","application/pdf",hash,12));verify(repo,never()).insertar(any(UUID.class),any(),anyInt(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());}
    @Test void recuperacionNoTocaOtroNamespace(){var i=intento("INCIERTO",true);when(repo.intentarBloqueo(anyLong())).thenReturn(true);when(repo.deOperacion(dueno,op)).thenReturn(List.of(i));SeguimientoSalidaBinaria.Retiro retiro=mock(SeguimientoSalidaBinaria.Retiro.class);assertTrue(servicio.completar(propietario,op,"d".repeat(64),retiro));verifyNoInteractions(retiro);verify(repo,never()).vinculado(any(UUID.class),anyString());verify(repo,never()).estado(any(),anyString(),anyBoolean(),anyString(),anyBoolean());}
    @Test void antecedenteSinNamespaceNoSeAdjudicaAlActual(){var i=intento("INCIERTO",true);var antiguo=new Intento(i.id(),i.organizacion(),i.operacion(),i.numero(),i.fuente(),i.clave(),i.extension(),i.contentType(),i.huella(),i.bytes(),i.estado(),i.escrituraIncierta(),i.ultimaVerificacion(),i.resultadoTecnico(),null,dueno,null);when(repo.ultimo(dueno,op)).thenReturn(Optional.of(antiguo));assertThrows(ConflictoException.class,()->servicio.consultar(propietario,op,"FUENTE_TEST",almacen));}
}
