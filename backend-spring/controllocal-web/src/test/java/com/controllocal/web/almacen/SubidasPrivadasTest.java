package com.controllocal.web.almacen;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.web.controlador.*;
import com.controllocal.web.dto.FotoPerfilRequest;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Adaptadores de subida: cuota/autoridad Core mock explícito; no certifica financiación. */
class SubidasPrivadasTest {
    private static final Actor ACTOR=new Actor(1,2,3,Actor.AGENTE);
    private static final byte[] BYTES=pngReal();
    private static final String PNG=Base64.getEncoder().encodeToString(BYTES);
    final AlmacenDocumentos almacen=mock(AlmacenDocumentos.class);
    final RevalidacionDeSesion sesiones=mock(RevalidacionDeSesion.class);
    final FotoPropiedadService fotos=mock(FotoPropiedadService.class);
    final PerfilService perfil=mock(PerfilService.class);
    final SalidaBinariaPersistente salidas=mock(SalidaBinariaPersistente.class);
    final CuotaArchivosB0 cuota=mock(CuotaArchivosB0.class);
    final ClasificadorArchivosExistentesB0 clasificador=mock(ClasificadorArchivosExistentesB0.class);
    final EvaluacionTecnicaFoto evaluador=mock(EvaluacionTecnicaFoto.class);
    final AtomicInteger commits=new AtomicInteger();
    final AbstractPlatformTransactionManager gestor=new AbstractPlatformTransactionManager(){
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object o,TransactionDefinition d){}
        protected void doCommit(DefaultTransactionStatus s){commits.incrementAndGet();}
        protected void doRollback(DefaultTransactionStatus s){}
    };
    MockedStatic<SesionActual> principal;MockedStatic<ContextoDeActuacion> contexto;
    @BeforeEach void preparar(){
        principal=mockStatic(SesionActual.class);contexto=mockStatic(ContextoDeActuacion.class);
        principal.when(SesionActual::sesion).thenReturn(mock(SesionDeRequest.class));
        contexto.when(()->ContextoDeActuacion.de(any())).thenReturn(new ContextoSesion("a".repeat(64),2_000_000_000,"SID-TEST",1));
        when(clasificador.preparar(anyString(),anyString(),anyString(),eq(ACTOR),any())).thenReturn(new UsoAlmacenamientoB0.Recurso("TEST","1","TEST","a".repeat(64)));
        when(cuota.admision(any())).thenReturn(i->{}); // Sólo prueba del adaptador, B0 real tiene sus suites PG.
        when(salidas.guardar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),anyString(),anyString(),any(),any())).thenReturn(new SalidaBinariaPersistente.Guardado(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(1),UUID.randomUUID(),1,"c".repeat(64),"privada/test.png","b".repeat(64),BYTES.length));
        when(almacen.proveedor()).thenReturn("TEST");
        when(fotos.agregar(eq(5L),anyString(),anyString(),eq(12),eq(ACTOR))).thenReturn(new FotoPropiedadService.FotoDePropiedad(7L,UUID.randomUUID().toString(),"foto.png",1));
        var e=new EvaluacionTecnicaFoto.Evaluacion("PNG",640,480,1,"HORIZONTAL",640,480,0.5,"b".repeat(64),"FOTO-LOCAL-1");
        when(evaluador.evaluar(any())).thenReturn(new EvaluacionTecnicaFoto.Copia(e,"b".repeat(64),"image/png",BYTES));
    }
    @AfterEach void cerrar(){contexto.close();principal.close();}
    GaleriaDeFotos galeria(){return new GaleriaDeFotos(fotos,almacen,sesiones,salidas,cuota,clasificador,evaluador,gestor);}
    PerfilController perfil(){return new PerfilController(perfil,sesiones,salidas,cuota,clasificador);}
    @Test void galeriaNoEscribeBytesConSesionYaRevocada(){
        when(sesiones.exigirVigente()).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->galeria().agregar(5,"foto.png",PNG,12,ACTOR));
        verifyNoInteractions(salidas,fotos,cuota);
    }
    @Test void revocarDuranteSubidaNoRegistraNiBorraACiegas(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->galeria().agregar(5,"foto.png",PNG,12,ACTOR));
        verifyNoInteractions(fotos);verify(cuota,never()).confirmar(any(),any());verify(almacen,never()).eliminar(anyString());
    }
    @Test void rechazoCanonicoConservaErrorYNoConfirmaCuota(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR);var rechazo=new AccesoNoAutorizadoException();
        when(fotos.agregar(eq(5L),anyString(),anyString(),eq(12),eq(ACTOR))).thenThrow(rechazo);
        assertSame(rechazo,assertThrows(AccesoNoAutorizadoException.class,()->galeria().agregar(5,"foto.png",PNG,12,ACTOR)));
        verify(cuota,never()).confirmar(any(),any());verify(almacen,never()).eliminar(anyString());
    }
    @Test void denegacionDePreflightImpideTodaEscritura(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR);var rechazo=new CapacidadDeshabilitadaException();
        when(clasificador.preparar(anyString(),anyString(),anyString(),any(),any())).thenThrow(rechazo);
        assertSame(rechazo,assertThrows(CapacidadDeshabilitadaException.class,()->galeria().agregar(5,"foto.png",PNG,12,ACTOR)));verifyNoInteractions(salidas);
    }
    @Test void recompensaTecnicaRealSeEntregaDespuesDelCommit(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR);
        when(evaluador.evaluar(any())).thenAnswer(i->{assertEquals(1,commits.get());assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return new EvaluacionTecnicaFoto().evaluar(i.getArgument(0));});
        var foto=galeria().agregar(5,"foto.png",PNG,12,ACTOR);
        assertEquals("EVALUADA",foto.utilidad().estado());assertEquals(2,foto.utilidad().evaluacion().anchoOriginal());verify(cuota).confirmar(any(),any());
    }
    @Test void falloDeRecompensaNoRevierteElOriginalConfirmado(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR);when(evaluador.evaluar(any())).thenThrow(new ReglaNegocioException("TEST evaluación indisponible"));
        var foto=galeria().agregar(5,"foto.png",PNG,12,ACTOR);
        assertEquals(1,commits.get());assertEquals("NO_DISPONIBLE",foto.utilidad().estado());assertNotNull(foto.id());verify(cuota).confirmar(any(),any());
    }
    @Test void perfilNoVinculaFotoSiLaSesionCambiaDuranteIo(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->perfil().subirFoto(new FotoPerfilRequest("foto.png",PNG)));
        verifyNoInteractions(perfil);verify(cuota,never()).confirmar(any(),any());verify(almacen,never()).eliminar(anyString());
    }
    @Test void perfilDevuelveReferenciaCoreYNuncaClavePrivada(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR);String ref=UUID.randomUUID().toString();when(perfil.actualizarFoto("privada/test.png",ACTOR)).thenReturn(ref);
        assertEquals(ref,perfil().subirFoto(new FotoPerfilRequest("foto.png",PNG)).clave());verify(cuota).confirmar(any(),any());
    }
    @Test void expedienteNoVinculaConSesionRevocadaDuranteIo(){
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new AccesoNoAutorizadoException());
        var solicitudes=mock(SolicitudService.class);var documentos=mock(DocumentoSolicitudService.class);
        when(solicitudes.obtener(5,ACTOR)).thenReturn(mock(SolicitudService.FichaSolicitud.class));
        var controlador=new SolicitudesController(solicitudes,documentos,mock(EvaluacionService.class),sesiones,salidas,cuota,clasificador);
        assertThrows(AccesoNoAutorizadoException.class,()->controlador.subirDocumento(5,"I","documento.pdf",new byte[]{1,2}));
        verifyNoInteractions(documentos);verify(cuota,never()).confirmar(any(),any());verify(almacen,never()).eliminar(anyString());
    }
    @Test void retirarFotoNuncaBorraElOriginal(){
        when(fotos.eliminar(5,ACTOR)).thenReturn(true);galeria().eliminar(5,ACTOR);verify(fotos).eliminar(5,ACTOR);verifyNoInteractions(almacen);
    }
    private static byte[] pngReal(){try{var b=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",b);return b.toByteArray();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
