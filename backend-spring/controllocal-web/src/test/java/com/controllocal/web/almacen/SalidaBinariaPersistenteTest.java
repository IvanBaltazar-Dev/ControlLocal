package com.controllocal.web.almacen;

import com.controllocal.service.archivo.SeguimientoSalidaBinaria.IntentoTecnico;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria;
import com.controllocal.service.archivo.PropietarioBinario;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalidaBinariaPersistenteTest {
    final AlmacenDocumentos almacen=mock(AlmacenDocumentos.class);
    final SeguimientoSalidaBinaria seguimiento=mock(SeguimientoSalidaBinaria.class); final SalidaBinariaPersistente.Admision admision=mock(SalidaBinariaPersistente.Admision.class);
    final SalidaBinariaPersistente salida=new SalidaBinariaPersistente(almacen,seguimiento);
    final UUID op=UUID.randomUUID();final byte[] datos="PDF sintetico TEST".getBytes(StandardCharsets.UTF_8);
    final Map<String,byte[]> objetos=new HashMap<>();
    IntentoTecnico i;
    @BeforeEach void iniciar()throws Exception{
        when(almacen.identidadTecnica()).thenReturn("c".repeat(64));TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
        i=new IntentoTecnico(9,new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,1,"FUENTE_TEST","tenant/1/salidas/"+op+"/1.pdf","pdf","application/pdf",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos)),datos.length,"PREPARADO",true,null,null,"c".repeat(64));
        when(seguimiento.consultar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","c".repeat(64))).thenReturn(Optional.of(i));
        when(seguimiento.preparar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1)),eq(op),eq("FUENTE_TEST"),eq("c".repeat(64)),eq("pdf"),eq("application/pdf"),eq(i.huella()),eq((long)datos.length)))
                .thenReturn(new SeguimientoSalidaBinaria.Preparado(i,true));
        when(almacen.abrir(anyString())).thenAnswer(a->{byte[] b=objetos.get(a.getArgument(0));return b==null?Optional.empty():Optional.of(new AlmacenDocumentos.ArchivoDescargado(b,"salida.pdf","application/pdf"));});
        doAnswer(a->{objetos.put(a.getArgument(0),a.getArgument(1));return null;}).when(almacen).guardarEnClave(anyString(),any(),anyString());
        doAnswer(a->{objetos.remove(a.getArgument(0));return null;}).when(almacen).eliminar(anyString());
    }
    @AfterEach void limpiar(){TransactionSynchronizationManager.clear();}
    SalidaBinariaPersistente.Guardado guardar(){return salida.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","pdf","application/pdf",datos,admision);}
    @Test void recepcionComprometeCustodiaDespuesDelIntentoYAntesDeAdmisionYPut(){
        var custodia=mock(SalidaBinariaPersistente.Custodia.class);
        var g=salida.recibir(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","pdf","application/pdf",datos,admision,custodia);
        var orden=inOrder(seguimiento,custodia,admision,almacen);
        orden.verify(seguimiento).preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","c".repeat(64),"pdf","application/pdf",i.huella(),datos.length);
        orden.verify(custodia).comprometer(argThat(p->p.propietario().equals(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1))&&p.operacion().equals(op)&&p.numeroIntento()==1&&p.huella().equals(i.huella())));
        orden.verify(admision).comprobar(any());orden.verify(almacen).guardarEnClave(i.clave(),datos,"application/pdf");
        orden.verify(seguimiento).verificado(i);assertEquals(i.clave(),g.clave());
    }
    @Test void custodiaNoComprometidaNoPermitePutNiVerificacion(){
        var custodia=mock(SalidaBinariaPersistente.Custodia.class);
        doThrow(new IllegalStateException("TEST custodia no comprometida")).when(custodia).comprometer(any());
        assertThrows(IllegalStateException.class,()->salida.recibir(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","pdf","application/pdf",datos,admision,custodia));
        verifyNoInteractions(admision);verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());verify(seguimiento,never()).verificado(any());
    }
    @Test void putOcurreDespuesDelRegistroYSeVerificaAntesDeEntregar(){var f=guardar();assertEquals(i.clave(),f.clave());assertEquals(i.huella(),f.huella());assertEquals(datos.length,f.bytes());var orden=inOrder(seguimiento,almacen);orden.verify(seguimiento).preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","c".repeat(64),"pdf","application/pdf",i.huella(),datos.length);orden.verify(almacen).abrir(i.clave());orden.verify(almacen).guardarEnClave(i.clave(),datos,"application/pdf");orden.verify(almacen).abrir(i.clave());orden.verify(seguimiento).verificado(i);}
    @Test void intentoExistenteCorrectoSeRecuperaSinPut(){objetos.put(i.clave(),datos);var c=salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",admision);assertEquals(SalidaBinariaPersistente.EstadoConsulta.DISPONIBLE,c.estado());assertEquals(i.clave(),c.guardado().clave());verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());}
    @Test void repetirGuardarConObjetoCorrectoNoSobrescribe(){objetos.put(i.clave(),datos);when(seguimiento.preparar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong())).thenReturn(new SeguimientoSalidaBinaria.Preparado(i,false));guardar();verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());}
    @Test void incertidumbreConAusenciaNoEjecutaOtroPut(){when(seguimiento.preparar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong())).thenReturn(new SeguimientoSalidaBinaria.Preparado(i,false));assertThrows(AlmacenException.class,this::guardar);verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());verify(seguimiento).incierto(i);}
    @Test void contenidoDistintoNoSeSobrescribe(){objetos.put(i.clave(),new byte[]{1});assertThrows(AlmacenException.class,this::guardar);verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());verify(seguimiento).incierto(i);}
    @Test void putSinLecturaConfirmadaNoEsUnArchivoDisponible(){doNothing().when(almacen).guardarEnClave(anyString(),any(),anyString());assertThrows(AlmacenException.class,this::guardar);verify(seguimiento,never()).verificado(any());verify(seguimiento).incierto(i);}
    @Test void falloDelProveedorNoFiltraCausaClavesNiDetalles(){doThrow(new IllegalStateException("SDK secreto ruta y credencial"))
            .when(almacen).guardarEnClave(anyString(),any(),anyString());var e=assertThrows(AlmacenException.class,this::guardar);assertNull(e.getCause());assertFalse(e.getMessage().contains("secreto"));assertFalse(e.getMessage().contains(i.clave()));}
    @Test void callbackSeRegistraUnaVezPorOperacion(){guardar();salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",admision);assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);verify(seguimiento).completar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1)),eq(op),eq("c".repeat(64)),any());}
    @Test void callbacksDeUnMismoComandoConservanDuenosDistintos(){
        when(seguimiento.consultar(any(PropietarioBinario.class),eq(op),eq("FUENTE_TEST"),eq("c".repeat(64))))
                .thenAnswer(llamada->{
                    var dueno=llamada.getArgument(0,PropietarioBinario.class);
                    return Optional.of(new IntentoTecnico(i.id(),dueno,op,1,"FUENTE_TEST",
                            dueno.prefijo()+"/salidas/"+op+"/1.pdf",i.extension(),i.contentType(),
                            i.huella(),i.bytes(),i.estado(),i.escrituraIncierta(),i.ultimaVerificacion(),
                            i.resultadoTecnico(),i.almacenHuella()));
                });
        var a=new PropietarioBinario.Organizacion(1);
        var b=new PropietarioBinario.Organizacion(2);
        var caso=new PropietarioBinario.CasoEvaluacion(op);
        for(var dueno:List.of(a,b,caso,new PropietarioBinario.CasoEvaluacion(op))){
            assertEquals(SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION,
                    salida.recuperar(dueno,op,"FUENTE_TEST",admision).estado());
        }
        var callbacks=TransactionSynchronizationManager.getSynchronizations();
        assertEquals(3,callbacks.size());
        callbacks.forEach(c->c.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        for(var dueno:List.of(a,b,caso))verify(seguimiento).completar(eq(dueno),eq(op),eq("c".repeat(64)),any());
        verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());
    }
    @Test void rollbackTambienSolicitaRecuperacionDurable(){guardar();TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);verify(seguimiento).completar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1)),eq(op),eq("c".repeat(64)),any());}
    @Test void falloPosteriorAlCommitNoFingeRollbackDeLaOperacion(){guardar();doThrow(new IllegalStateException("DB indisponible"))
            .when(seguimiento).completar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),any());assertDoesNotThrow(()->TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED));}
    @Test void eliminarBestEffortNoAcreditaLimpiezaSiSigueElObjeto(){guardar();doNothing().when(almacen).eliminar(i.clave());var captura=org.mockito.ArgumentCaptor.forClass(SeguimientoSalidaBinaria.Retiro.class);TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);verify(seguimiento).completar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1)),eq(op),eq("c".repeat(64)),captura.capture());assertFalse(captura.getValue().retirarYVerificar(i));assertTrue(objetos.containsKey(i.clave()));}
    @Test void retiroVerificadoExigeLeerAusencia(){guardar();var captura=org.mockito.ArgumentCaptor.forClass(SeguimientoSalidaBinaria.Retiro.class);TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);verify(seguimiento).completar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1)),eq(op),eq("c".repeat(64)),captura.capture());assertTrue(captura.getValue().retirarYVerificar(i));assertFalse(objetos.containsKey(i.clave()));}
    @Test void consultaNoIniciadaPermiteComponerSinAfirmarArchivo(){when(seguimiento.consultar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","c".repeat(64))).thenReturn(Optional.empty());var c=salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",admision);assertEquals(SalidaBinariaPersistente.EstadoConsulta.NO_INICIADO,c.estado());assertNull(c.guardado());verify(almacen,never()).abrir(anyString());verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());verify(almacen,never()).eliminar(anyString());}
    @Test void falloDeLecturaEntregaPendienteSinBinarioFicticio(){when(almacen.abrir(anyString())).thenThrow(new AlmacenException("detalle interno",null));var c=salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",admision);assertEquals(SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION,c.estado());assertNull(c.guardado());verify(seguimiento).incierto(i);}
    @Test void callbackNoRetiraSiCambioElNamespace(){guardar();when(almacen.identidadTecnica()).thenReturn("d".repeat(64));TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);verify(seguimiento,never()).completar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),any());assertTrue(objetos.containsKey(i.clave()));}
    @Test void retiroRevalidaIdentidadAntesDeTocarUnObjeto(){guardar();var captura=org.mockito.ArgumentCaptor.forClass(SeguimientoSalidaBinaria.Retiro.class);TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);verify(seguimiento).completar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1)),eq(op),eq("c".repeat(64)),captura.capture());when(almacen.identidadTecnica()).thenReturn("d".repeat(64));assertFalse(captura.getValue().retirarYVerificar(i));verify(almacen,never()).eliminar(anyString());}
    @Test void cambioDeNamespaceDurantePutNoDevuelveArchivoDisponible(){doAnswer(a->{objetos.put(i.clave(),datos);when(almacen.identidadTecnica()).thenReturn("d".repeat(64));return null;}).when(almacen).guardarEnClave(anyString(),any(),anyString());assertThrows(AlmacenException.class,this::guardar);verify(seguimiento).incierto(i);verify(seguimiento,never()).verificado(any());}
    @Test void cambioDeNamespaceDuranteRecuperacionNoDevuelveBytes(){when(almacen.abrir(i.clave())).thenAnswer(a->{when(almacen.identidadTecnica()).thenReturn("d".repeat(64));return Optional.of(new AlmacenDocumentos.ArchivoDescargado(datos,"test.pdf","application/pdf"));});var c=salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",admision);assertEquals(SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION,c.estado());assertNull(c.guardado());verify(seguimiento).incierto(i);verify(seguimiento,never()).verificado(any());}
    @Test void admisionRecibeIntencionExactaAntesDeIo(){var g=guardar();var orden=inOrder(seguimiento,admision,almacen);orden.verify(seguimiento).preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","c".repeat(64),"pdf","application/pdf",i.huella(),datos.length);orden.verify(admision).comprobar(new SalidaBinariaPersistente.Intencion(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,1,"c".repeat(64),i.clave(),i.huella(),datos.length));orden.verify(almacen).abrir(i.clave());assertEquals("c".repeat(64),g.namespaceHash());}
    @Test void denegacionDeAdmisionNoSeConvierteEnFalloDelProveedor(){var denegada=new com.controllocal.service.excepcion.AccesoNoAutorizadoException();doThrow(denegada).when(admision).comprobar(any());assertSame(denegada,assertThrows(com.controllocal.service.excepcion.AccesoNoAutorizadoException.class,this::guardar));verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());verify(almacen,never()).abrir(anyString());verify(seguimiento,never()).incierto(any());assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());}
    @Test void recuperarExactoTampocoOmiteAdmisionNiAbsorbeDenegacion(){objetos.put(i.clave(),datos);var denegada=new com.controllocal.service.excepcion.ReglaNegocioException("Cuota denegada TEST");doThrow(denegada).when(admision).comprobar(any());assertSame(denegada,assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",admision)));verify(almacen,never()).guardarEnClave(anyString(),any(),anyString());verify(seguimiento,never()).incierto(any());verify(seguimiento,never()).verificado(any());}
    @Test void callbackEsObligatorioSinViaQueLoOmita(){assertThrows(IllegalArgumentException.class,()->salida.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST","pdf","application/pdf",datos,null));assertThrows(IllegalArgumentException.class,()->salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),op,"FUENTE_TEST",null));verifyNoInteractions(seguimiento,almacen);}
}
