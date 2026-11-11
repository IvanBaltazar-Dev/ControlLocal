package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Protocolo del adaptador; fuente B0 y vínculo son mocks explícitos, no aceptación PG. */
class CuotaArchivosB0Test {
    final UsoAlmacenamientoB0 uso=mock(UsoAlmacenamientoB0.class);
    final ConfirmacionAlmacenamientoB0 vinculos=mock(ConfirmacionAlmacenamientoB0.class);
    final AlmacenDocumentos almacen=mock(AlmacenDocumentos.class);
    final CuotaArchivosB0 puente=new CuotaArchivosB0(uso,vinculos,almacen);
    final byte[] bytes={1,2,3};final String namespace="c".repeat(64),hash=huella(bytes);
    final UUID op=UUID.randomUUID();final AtomicInteger commits=new AtomicInteger(),rollbacks=new AtomicInteger();
    final CuotaArchivosB0.Contexto c=new CuotaArchivosB0.Contexto(new Recurso("RECURSO_TEST","17","PRUEBA","a".repeat(64)),new Actor(7,8,9,Actor.AGENTE),new ContextoSesion("b".repeat(64),2_000_000_000,"SID_TEST",1));
    final TransactionTemplate tx=new TransactionTemplate(new AbstractPlatformTransactionManager(){
        protected Object doGetTransaction(){return new Object();}protected void doBegin(Object o,TransactionDefinition d){}
        protected void doCommit(DefaultTransactionStatus s){commits.incrementAndGet();}protected void doRollback(DefaultTransactionStatus s){rollbacks.incrementAndGet();}
    });
    @BeforeEach void preparar(){
        when(uso.reservar(any(),any(),any())).thenAnswer(i->new Resultado(((Solicitud)i.getArgument(0)).reserva(),"RESERVADA"));
        when(uso.iniciar(any(),any(),any())).thenReturn(true);when(almacen.identidadTecnica()).thenReturn(namespace);
        when(almacen.abrir("tenant/7/salidas/test.png")).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"foto.png","image/png")));
    }
    @Test void reservaAntesDeIoYConfirmaSoloAlCommitConLaIdentidadExacta(){
        tx.executeWithoutResult(t->{puente.admision(c).comprobar(intencion());verifyNoInteractions(vinculos);
            puente.confirmar(c,guardado());verifyNoInteractions(vinculos);});
        verify(uso).reservar(argThat(s->s.bytes()==3&&s.identidadObjetoConocida().equals(namespace+":tenant/7/salidas/test.png")),eq(c.actor()),eq(c.sesion()));
        verify(vinculos).confirmar(eq(new RefInterna(7,CuotaArchivosB0.reserva(7,op,1))),argThat(o->o.bytes()==3&&o.huellaContenido().equals(hash)),eq("tenant/7/salidas/test.png"));assertEquals(1,commits.get());
    }
    @Test void mismaConfirmacionEnLaTransaccionRegistraUnSoloCallback(){
        tx.executeWithoutResult(t->{puente.admision(c).comprobar(intencion());puente.confirmar(c,guardado());puente.confirmar(c,guardado());});
        verify(vinculos,times(1)).confirmar(any(),any(),anyString());
    }
    @Test void reservarSinGuardadoVerificadoNoConfirmaYRollbackNoConfirma(){
        tx.executeWithoutResult(t->puente.admision(c).comprobar(intencion()));verifyNoInteractions(vinculos);
        tx.executeWithoutResult(t->{puente.confirmar(c,guardado());t.setRollbackOnly();});verifyNoInteractions(vinculos);assertEquals(1,rollbacks.get());
    }
    @Test void denegacionDeCapacidadConservaSuClasificacion(){
        var denegada=new CapacidadDeshabilitadaException();doThrow(denegada).when(uso).reservar(any(),any(),any());
        assertSame(denegada,assertThrows(CapacidadDeshabilitadaException.class,()->tx.executeWithoutResult(t->puente.admision(c).comprobar(intencion()))));
        verify(uso,never()).iniciar(any(),any(),any());verifyNoInteractions(vinculos);
    }
    @Test void recuperacionRevalidaLaReservaSinOtroClaimNiOtraEscritura(){
        for(String estado:List.of("ALMACENANDO","INCIERTA","CONFIRMADA")){
            doReturn(new Resultado(CuotaArchivosB0.reserva(7,op,1),estado)).when(uso).reservar(any(),any(),any());
            tx.executeWithoutResult(t->puente.admision(c).comprobar(intencion()));
        }
        verify(uso,times(3)).reservar(any(),any(),any());verify(uso,never()).iniciar(any(),any(),any());verifyNoInteractions(almacen);
    }
    @Test void namespaceCambiadoImpideConfirmarYRevierte(){
        when(almacen.identidadTecnica()).thenReturn("d".repeat(64));
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.confirmar(c,guardado())));verifyNoInteractions(vinculos);assertEquals(1,rollbacks.get());
    }
    @Test void binarioTruncadoODistintoNoSeConfirma(){
        when(almacen.abrir(anyString())).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(new byte[]{1,2},"foto.png","image/png")));
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.confirmar(c,guardado())));
        when(almacen.abrir(anyString())).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(new byte[]{4,5,6},"foto.png","image/png")));
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.confirmar(c,guardado())));verifyNoInteractions(vinculos);
    }
    @Test void vinculoRechazadoRevierteAntesDeConfirmar(){
        doThrow(new ConflictoException("TEST sin vínculo")).when(vinculos).confirmar(any(),any(),anyString());
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.confirmar(c,guardado())));assertEquals(0,commits.get());assertEquals(1,rollbacks.get());
    }
    @Test void identidadIncluyeNamespaceOrganizacionYNumeroDeIntento(){
        assertNotEquals(CuotaArchivosB0.reserva(7,op,1),CuotaArchivosB0.reserva(8,op,1));assertNotEquals(CuotaArchivosB0.reserva(7,op,1),CuotaArchivosB0.reserva(7,op,2));
        assertThrows(IllegalStateException.class,()->puente.admision(c));
        var ajena=new SalidaBinariaPersistente.Intencion(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(8),op,1,namespace,"tenant/8/salidas/test.png",hash,3);
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.admision(c).comprobar(ajena)));verifyNoInteractions(uso);
    }
    @Test void previsionDelJobSustituyeReservaExactaDelIntento(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,UUID.randomUUID());
        when(uso.admitirPrevisionParaAlmacenamiento(eq(ref),any(),eq(c.actor()),eq(c.sesion()))).thenReturn(new Resultado(ref.reserva(),"ALMACENANDO"));
        tx.executeWithoutResult(t->puente.admision(c,ref).comprobar(intencion()));
        verify(uso).admitirPrevisionParaAlmacenamiento(eq(ref),argThat(s->s.reserva().equals(ref.reserva())&&s.bytes()==3&&s.huellaContenido().equals(hash)&&s.namespaceHash().equals(namespace)),eq(c.actor()),eq(c.sesion()));
        verify(uso,never()).reservar(any(),any(),any());verify(uso,never()).iniciar(any(),any(),any());
    }
    @Test void confirmaLaPrevisionOriginalConElMismoVinculoAntesDelCommit(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,UUID.randomUUID());
        tx.executeWithoutResult(t->{puente.confirmar(c,guardado(),ref);verifyNoInteractions(vinculos);});
        verify(vinculos).confirmar(eq(ref),argThat(o->o.bytes()==3&&o.huellaContenido().equals(hash)),eq("tenant/7/salidas/test.png"));
        verify(uso,never()).reservar(any(),any(),any());
    }
    @Test void previsionAjenaNoPuedeAdmitirNiConfirmarElBinario(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(8,UUID.randomUUID());
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.admision(c,ref)));
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.confirmar(c,guardado(),ref)));
        verifyNoInteractions(uso,vinculos);
    }
    @Test void ausenciaRealPrecedeALiberarLaCuotaYNoRealizaUnDelete(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,UUID.randomUUID());var prueba=ausencia();
        when(almacen.abrir("tenant/7/salidas/test.png")).thenReturn(Optional.empty());
        when(uso.liberarRetiradaVerificada(ref,prueba)).thenReturn(new Resultado(ref.reserva(),"RETIRADO_VERIFICADO"));
        tx.executeWithoutResult(t->assertEquals("RETIRADO_VERIFICADO",puente.liberarRetiradaVerificada(ref,prueba).estado()));
        var orden=inOrder(almacen,uso);orden.verify(almacen).identidadTecnica();orden.verify(almacen).abrir("tenant/7/salidas/test.png");orden.verify(almacen).identidadTecnica();orden.verify(uso).liberarRetiradaVerificada(ref,prueba);
        verify(almacen,never()).eliminar(anyString());
    }
    @Test void binarioTodaviaPresenteNoLiberaNiPorVencimiento(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,UUID.randomUUID());
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.liberarRetiradaVerificada(ref,ausencia())));
        verify(uso,never()).liberarRetiradaVerificada(any(),any());
    }
    @Test void incertidumbreDeLecturaConservaCuotaYNoExponeProveedor(){
        when(almacen.abrir(anyString())).thenThrow(new IllegalStateException("SDK secreto"));
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,UUID.randomUUID());
        var fallo=assertThrows(AlmacenException.class,()->tx.executeWithoutResult(t->puente.liberarRetiradaVerificada(ref,ausencia())));
        assertFalse(fallo.getMessage().contains("secreto"));assertNull(fallo.getCause());verify(uso,never()).liberarRetiradaVerificada(any(),any());
    }
    @Test void retiradaSinTxCoreONamespaceActualNoLibera(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->puente.liberarRetiradaVerificada(ref,ausencia()));
        when(almacen.identidadTecnica()).thenReturn("d".repeat(64));
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->puente.liberarRetiradaVerificada(ref,ausencia())));
        verify(uso,never()).liberarRetiradaVerificada(any(),any());
    }
    private AusenciaComprobada ausencia(){UUID retiro=UUID.randomUUID();return new AusenciaComprobada(retiro,op,UUID.randomUUID(),1,namespace+":tenant/7/salidas/test.png",3,hash,"MATERIAL-IA-RETIRO:"+retiro);}
    private SalidaBinariaPersistente.Intencion intencion(){return new SalidaBinariaPersistente.Intencion(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(7),op,1,namespace,"tenant/7/salidas/test.png",hash,3);}
    private SalidaBinariaPersistente.Guardado guardado(){return new SalidaBinariaPersistente.Guardado(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(7),op,1,namespace,"tenant/7/salidas/test.png",hash,3);}
    private static String huella(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
}
