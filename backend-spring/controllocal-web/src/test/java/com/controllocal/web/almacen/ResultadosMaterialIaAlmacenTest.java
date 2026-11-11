package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.FuentesMaterialIa.Snapshot;
import com.controllocal.service.materialia.DerivadasMaterialIaService.Lectura;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;

/** Prueba del adaptador I/O con autoridad Core doblada; PG cubre esa autoridad real. */
class ResultadosMaterialIaAlmacenTest {
    final DerivadasMaterialIaService registro=mock(DerivadasMaterialIaService.class);
    final FuentesMaterialIa fuente=mock(FuentesMaterialIa.class);
    final SalidaBinariaPersistente salidas=mock(SalidaBinariaPersistente.class);
    final CuotaArchivosB0 cuota=mock(CuotaArchivosB0.class);
    final AlmacenDocumentos almacen=mock(AlmacenDocumentos.class);
    final UsoAlmacenamientoB0 uso=mock(UsoAlmacenamientoB0.class);
    final PresupuestoIaService presupuesto=mock(PresupuestoIaService.class);
    final Actor actor=new Actor(7,11,13,Actor.AGENTE);
    final ContextoSesion sid=new ContextoSesion("s".repeat(64),Instant.now().plusSeconds(300).getEpochSecond(),"SID-TEST",1);
    final UUID job=UUID.randomUUID(),archivo=UUID.randomUUID(),original=UUID.randomUUID();
    final byte[] bytes={1,2,3};final String namespace="a".repeat(64),clave="CLAVE_PRIVADA_TEST_NO_CABLE";
    ResultadosMaterialIaAlmacen adaptador;Lectura lectura;
    @BeforeEach void preparar(){
        var s=new Solicitud(job,Accion.MEJORAR_FOTO,new Recurso("PROPIEDAD","31"),"TEST",original,null);
        var f=new Snapshot("TEST","b".repeat(64),Map.of(),"TEST",Map.of(),List.of(),null);
        lectura=new Lectura(7,job,"c".repeat(64),s,f,archivo,original,31,"PROCESO_TEST","1",
                new DerivadasMaterialIaService.Binario(clave,namespace,EvaluacionTecnicaFoto.huella(bytes),bytes.length,"image/png"));
        when(registro.autorizar(job,archivo,actor,sid)).thenReturn(lectura);
        when(registro.autorizarArchivo(job,archivo,actor)).thenReturn(lectura);
        when(registro.version(job,actor)).thenReturn(version(true,"DISPONIBLE"));
        when(registro.promovida(job,actor)).thenReturn(true);
        var seleccion=new ResultadosMaterialIa.Seleccion(original,1,job,archivo);
        when(registro.consultarSelecciones(List.of(original),actor)).thenReturn(List.of(seleccion));
        when(registro.bloquearSelecciones(List.of(original),actor)).thenReturn(List.of(seleccion));
        when(fuente.evaluarLecturaArtefacto(any(),any(),eq(actor))).thenReturn(EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMITIDO);
        when(almacen.identidadTecnica()).thenReturn(namespace);when(almacen.abrir(clave)).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"CLAVE_PRIVADA.png","image/png")));
        adaptador=new ResultadosMaterialIaAlmacen(registro,fuente,salidas,cuota,almacen,uso,presupuesto);
    }
    ResultadosMaterialIa.Version version(boolean disponible,String estado){Instant inicio=Instant.now().minusSeconds(60);return new ResultadosMaterialIa.Version(job,original,archivo,2,inicio,inicio,inicio.plusSeconds(604800),false,false,disponible,estado);}
    @Test void limitePrevioAlProveedorEsLaPrevisionServidorDelMismoJob(){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),job);
        var limite=new UsoAlmacenamientoB0.LimiteRecepcion(ref,2048,"VERSION_TEST","FUENTE_TEST",namespace);
        when(uso.limiteRecepcion(ref)).thenReturn(limite);
        assertSame(limite,adaptador.limiteRecepcionParaInvocar(job,actor));
        var orden=inOrder(registro,uso);orden.verify(registro).exigirNamespaceParaInvocar(job,actor,namespace);
        orden.verify(uso).limiteRecepcion(ref);verifyNoInteractions(salidas,cuota,presupuesto,fuente);
    }
    @ParameterizedTest @ValueSource(strings={"AUSENTE","OTRO_JOB","OTRO_NAMESPACE","CAMBIO_NAMESPACE","REVOCADA"})
    void limiteNoConfirmadoNoLlegaAlProveedor(String causa){
        var ref=UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),job);
        var otra=UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),UUID.randomUUID());
        if(!causa.equals("AUSENTE"))when(uso.limiteRecepcion(ref)).thenReturn(new UsoAlmacenamientoB0.LimiteRecepcion(
                causa.equals("OTRO_JOB")?otra:ref,2048,"VERSION_TEST","FUENTE_TEST",causa.equals("OTRO_NAMESPACE")?"b".repeat(64):namespace));
        if(causa.equals("CAMBIO_NAMESPACE"))when(almacen.identidadTecnica()).thenReturn(namespace,"b".repeat(64));
        if(causa.equals("REVOCADA"))doThrow(new AccesoNoAutorizadoException()).when(registro).exigirNamespaceParaInvocar(job,actor,namespace);
        if(causa.equals("REVOCADA")){
            assertThrows(AccesoNoAutorizadoException.class,()->adaptador.limiteRecepcionParaInvocar(job,actor));
            verifyNoInteractions(uso);
        }else assertThrows(ConflictoException.class,()->adaptador.limiteRecepcionParaInvocar(job,actor));
        verifyNoInteractions(salidas,cuota,presupuesto,fuente);
    }
    @Test void recuperacionTecnicaLeeBytesYMetadataDurablesSinSalidaAportadaPorLlamador(){
        var recibida=mock(DerivadasMaterialIaService.Recibido.class);when(recibida.binario()).thenReturn(lectura.binario());
        when(registro.recibido(7,job,"INTENTO_TEST")).thenReturn(recibida);
        var respuesta=new ProveedorMaterialIa.Salida(null,"image/png",bytes,"PROCESO_TEST","1",null);
        when(registro.reconstruir(eq(recibida),aryEq(bytes))).thenReturn(respuesta);
        assertSame(respuesta,adaptador.recuperarRecibido(7,job,"INTENTO_TEST"));
        var orden=inOrder(registro,almacen);orden.verify(registro).recibido(7,job,"INTENTO_TEST");orden.verify(almacen).abrir(clave);
        orden.verify(registro).reconstruir(eq(recibida),aryEq(bytes));orden.verify(registro).recibir(eq(7L),eq(job),eq("INTENTO_TEST"),same(respuesta),any());
        verifyNoInteractions(fuente,presupuesto,salidas,cuota);
    }
    @Test void recuperacionSinObjetoNoInventaContenidoNiInvocaEscritor(){
        var recibida=mock(DerivadasMaterialIaService.Recibido.class);when(recibida.binario()).thenReturn(lectura.binario());when(registro.recibido(7,job,"INTENTO_TEST")).thenReturn(recibida);
        when(almacen.abrir(clave)).thenReturn(Optional.empty());assertThrows(NoEncontradoException.class,()->adaptador.recuperarRecibido(7,job,"INTENTO_TEST"));
        verify(registro,never()).recibir(anyLong(),any(),anyString(),any(),any());verify(registro,never()).reconstruir(any(),any());verifyNoInteractions(salidas,cuota,presupuesto);
    }
    @Test void descargaReautorizaAntesYDespuesYNoExponeIdentidadPrivada()throws Exception{
        var r=adaptador.descargar(job,archivo,actor,sid);assertArrayEquals(bytes,r.bytes());assertEquals("material-"+archivo+".png",r.nombre());
        var orden=inOrder(fuente,almacen,registro);orden.verify(fuente).exigirLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor);
        orden.verify(almacen).abrir(clave);orden.verify(fuente).exigirLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor);
        verify(registro,times(2)).revalidar(lectura,actor,sid);
        byte[] copia=r.bytes();copia[0]=9;assertEquals(1,r.bytes()[0]);
        String publico=new ObjectMapper().writeValueAsString(r);assertFalse(publico.contains(clave));assertFalse(publico.contains(namespace));assertFalse(publico.contains("bytes"));
        verifyNoInteractions(salidas,cuota);
    }
    @ParameterizedTest @ValueSource(strings={"HASH","BYTES","MIME","NAMESPACE","FALTA","SDK","REVOCADA"})
    void bytesOAutorizacionNoConfirmadosNoProducenContenido(String caso){
        switch(caso){
            case "HASH"->when(almacen.abrir(clave)).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(new byte[]{9,2,3},"TEST","image/png")));
            case "BYTES"->when(almacen.abrir(clave)).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(new byte[]{1},"TEST","image/png")));
            case "MIME"->when(almacen.abrir(clave)).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"TEST","image/jpeg")));
            case "NAMESPACE"->when(almacen.identidadTecnica()).thenReturn("c".repeat(64));
            case "FALTA"->when(almacen.abrir(clave)).thenReturn(Optional.empty());
            case "SDK"->when(almacen.abrir(clave)).thenThrow(new IllegalStateException(clave));
            case "REVOCADA"->{var revocada=new AtomicBoolean();when(almacen.abrir(clave)).thenAnswer(i->{revocada.set(true);return Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"TEST","image/png"));});
                doAnswer(i->{if(revocada.get())throw new CapacidadDeshabilitadaException();return null;}).when(fuente).exigirLecturaArtefacto(any(),any(),eq(actor));}
        }
        var fallo=assertThrows(RuntimeException.class,()->adaptador.descargar(job,archivo,actor,sid));assertFalse(fallo.getMessage().contains(clave));verifyNoInteractions(salidas,cuota);
    }
    @Test void descargaDeniegaPadreSqlAntesDeConsultarOLeer(){
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThrows(IllegalStateException.class,()->adaptador.descargar(job,archivo,actor,sid));verifyNoInteractions(registro,fuente,almacen);}
        finally{TransactionSynchronizationManager.clear();}
    }
    @Test void lecturaDePiezaRequierePadreRealYUsaLaMismaAutorizacion(){
        assertThrows(IllegalStateException.class,()->adaptador.leerParaPieza(job,archivo,actor,sid));verifyNoInteractions(registro,fuente,almacen);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            when(almacen.abrir(clave)).thenAnswer(i->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"TEST","image/png"));});
            assertArrayEquals(bytes,adaptador.leerParaPieza(job,archivo,actor,sid).bytes());verify(fuente,times(2)).exigirLecturaArtefacto(any(),any(),eq(actor));
            verify(registro,times(2)).revalidar(lectura,actor,sid);verifyNoInteractions(salidas,cuota);
        }finally{TransactionSynchronizationManager.clear();}
    }
    @Test void lecturaDePiezaNoOmiteDenegacionCore(){
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{when(registro.autorizar(job,archivo,actor,sid)).thenThrow(new AccesoNoAutorizadoException());
            assertThrows(AccesoNoAutorizadoException.class,()->adaptador.leerParaPieza(job,archivo,actor,sid));verifyNoInteractions(almacen,salidas,cuota);
        }finally{TransactionSynchronizationManager.clear();}
    }
    @ParameterizedTest @ValueSource(strings={"FOTO_B","VERSION_FISICA"})
    void derivadoExistenteSePuedeReutilizarEnNuevaPiezaAunqueCambieOtraFuente(String cambio){
        doThrow(new ConflictoException("TEST cambió "+cambio)).when(fuente).revalidar(any(),any(),eq(actor));
        assertThrows(ConflictoException.class,()->fuente.revalidar(lectura.solicitud(),lectura.snapshot(),actor));
        clearInvocations(fuente);
        var seleccion=new ResultadosMaterialIa.Seleccion(original,1,job,archivo);
        when(registro.autorizarArchivo(job,archivo,actor)).thenReturn(lectura);
        when(registro.bloquearSelecciones(List.of(original),actor)).thenReturn(List.of(seleccion));
        when(registro.consultarSelecciones(List.of(original),actor)).thenReturn(List.of(seleccion));
        assertArrayEquals(bytes,adaptador.descargar(job,archivo,actor,sid).bytes());
        clearInvocations(fuente,registro,almacen);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            assertEquals(List.of(seleccion),adaptador.consultarSelecciones(List.of(original),actor));
            assertEquals(List.of(seleccion),adaptador.bloquearSelecciones(List.of(original),actor));
            assertArrayEquals(bytes,adaptador.leerParaPieza(job,archivo,actor,sid).bytes());
            verify(fuente,times(4)).exigirLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor);
            verify(registro).bloquearSelecciones(List.of(original),actor);verify(almacen).abrir(clave);
            verify(registro,times(2)).revalidar(lectura,actor,sid);
            verify(fuente,never()).revalidar(any(),any(),any());verifyNoInteractions(salidas,cuota);
        }
        finally{TransactionSynchronizationManager.clear();}
    }
    @ParameterizedTest @ValueSource(strings={"ORIGINAL","E44","PERMISO"})
    void reusoDeniegaAutorizacionRevocadaAntesDeSeleccionODelContenido(String causa){
        when(registro.autorizarArchivo(job,archivo,actor)).thenReturn(lectura);
        RuntimeException denegacion=switch(causa){
            case "ORIGINAL"->new NoEncontradoException("Fotografía original");
            case "E44"->new CapacidadDeshabilitadaException();
            default->new AccesoNoAutorizadoException();
        };
        doThrow(denegacion).when(fuente).exigirLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            assertSame(denegacion,assertThrows(RuntimeException.class,()->adaptador.consultarSelecciones(List.of(original),actor)));
            assertSame(denegacion,assertThrows(RuntimeException.class,()->adaptador.bloquearSelecciones(List.of(original),actor)));
            assertSame(denegacion,assertThrows(RuntimeException.class,()->adaptador.leerParaPieza(job,archivo,actor,sid)));
            verifyNoInteractions(salidas,cuota);verify(almacen,never()).abrir(anyString());
        }finally{TransactionSynchronizationManager.clear();}
    }
    @ParameterizedTest @ValueSource(strings={"ORIGINAL","E44","PERMISO"})
    void lecturaParaPiezaRevalidaAutorizacionDespuesDeLeerBytes(String causa){
        var revocada=new AtomicBoolean();
        RuntimeException denegacion=switch(causa){
            case "ORIGINAL"->new NoEncontradoException("Fotografía original");
            case "E44"->new CapacidadDeshabilitadaException();
            default->new AccesoNoAutorizadoException();
        };
        doAnswer(i->{if(revocada.get())throw denegacion;return null;}).when(fuente).exigirLecturaArtefacto(any(),any(),eq(actor));
        when(almacen.abrir(clave)).thenAnswer(i->{revocada.set(true);return Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"TEST","image/png"));});
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            assertSame(denegacion,assertThrows(RuntimeException.class,()->adaptador.leerParaPieza(job,archivo,actor,sid)));
            verify(fuente,times(2)).exigirLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor);
            verify(almacen).abrir(clave);verify(registro).revalidar(lectura,actor,sid);verifyNoInteractions(salidas,cuota);
        }finally{TransactionSynchronizationManager.clear();}
    }
    @Test void seleccionRevalidaAutorizacionDespuesDeTomarElLockCore(){
        var revocada=new AtomicBoolean();when(registro.autorizarArchivo(job,archivo,actor)).thenReturn(lectura);
        when(registro.bloquearSelecciones(List.of(original),actor)).thenAnswer(i->{revocada.set(true);return List.of(new ResultadosMaterialIa.Seleccion(original,1,job,archivo));});
        doAnswer(i->{if(revocada.get())throw new AccesoNoAutorizadoException();return null;}).when(fuente).exigirLecturaArtefacto(any(),any(),eq(actor));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            assertThrows(AccesoNoAutorizadoException.class,()->adaptador.bloquearSelecciones(List.of(original),actor));
            verify(registro).bloquearSelecciones(List.of(original),actor);verify(fuente).exigirLecturaArtefacto(any(),any(),eq(actor));
            verifyNoInteractions(salidas,cuota);verify(almacen,never()).abrir(anyString());
        }finally{TransactionSynchronizationManager.clear();}
    }
    @Test void seleccionSinPadreNoConsultaNiBloquea(){
        assertThrows(IllegalStateException.class,()->adaptador.bloquearSelecciones(List.of(original),actor));verifyNoInteractions(registro,fuente,almacen);
    }
    @Test void seleccionDesdeReadOnlyNoSeConvierteEnAutorizacionParaComponer(){
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try{assertThrows(IllegalStateException.class,()->adaptador.bloquearSelecciones(List.of(original),actor));verifyNoInteractions(registro,fuente,almacen);}
        finally{TransactionSynchronizationManager.clear();}
    }
    @Test void metadataRetiradaNoExigeLeerSuBinarioNiRecuperaElJobPrivado(){
        when(registro.version(job,actor)).thenReturn(version(false,"RETIRADA_VERIFICADA"));
        var metadata=new ResultadosMaterialIa.Archivo(job,original,archivo,lectura.binario().huella(),"image/png","PROCESO_TEST","1",Instant.now().minusSeconds(100));when(registro.archivo(lectura)).thenReturn(metadata);
        assertEquals(metadata,adaptador.exigirArchivo(job,archivo,actor));verify(fuente,times(2)).exigirLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor);
        verify(almacen,never()).abrir(anyString());verify(fuente,never()).exigirLecturaHistorica(any(),any(),any());verifyNoInteractions(cuota,salidas,uso);
    }
    @Test void previsionUsaNamespaceRealYMismoJobAntesDelClaim(){
        var recurso=new UsoAlmacenamientoB0.Recurso("CARGA_DERIVADA_MATERIAL_IA","7:"+job,"CONSERVAR_DERIVADA_PRIVADA","d".repeat(64));
        when(registro.previsionar(lectura.solicitud(),lectura.snapshot(),actor,sid,namespace)).thenReturn(new DerivadasMaterialIaService.PlanPrevision(recurso,"PERFIL_TEST","1"));
        var ref=UsoAlmacenamientoB0.referenciaPrevision(7,job);when(uso.previsionar(any(),eq(actor),eq(sid))).thenReturn(new UsoAlmacenamientoB0.PrevisionAdmitida(ref,bytes.length));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            assertEquals(ref,adaptador.previsionar(lectura.solicitud(),lectura.snapshot(),actor,sid));adaptador.exigirPrevisionAntesDeClaim(job,actor,sid);
            var orden=inOrder(fuente,registro,uso);orden.verify(fuente).revalidar(lectura.solicitud(),lectura.snapshot(),actor);
            orden.verify(registro).previsionar(lectura.solicitud(),lectura.snapshot(),actor,sid,namespace);
            orden.verify(uso).previsionar(eq(new UsoAlmacenamientoB0.PrevisionIa(job,recurso,"PERFIL_TEST","1",namespace)),eq(actor),eq(sid));
            orden.verify(registro).exigirReserva(job,actor,namespace);orden.verify(uso).exigirAntesDeClaim(ref,actor,sid);verifyNoInteractions(cuota,salidas);verify(almacen,never()).abrir(anyString());
        }finally{TransactionSynchronizationManager.clear();}
    }
    DerivadasMaterialIaService.PlanRetirada retirada(){return new DerivadasMaterialIaService.PlanRetirada(UUID.randomUUID(),7,job,archivo,3,"DESCARTE_EXPLICITO","INTENCION",lectura.binario());}
    void confirmarAusencia(){
        doAnswer(i->{var p=(DerivadasMaterialIaService.PlanRetirada)i.getArgument(0);
            java.util.function.Consumer<UsoAlmacenamientoB0.AusenciaComprobada> liberar=i.getArgument(1);
            liberar.accept(new UsoAlmacenamientoB0.AusenciaComprobada(p.id(),job,archivo,p.revision(),namespace+":"+clave,bytes.length,lectura.binario().huella(),"MATERIAL-IA-RETIRO:"+p.id()));
            when(registro.version(job,actor)).thenReturn(version(false,"RETIRADA_VERIFICADA"));return null;
        }).when(registro).confirmarAusencia(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"PENDIENTE","RECLAMADA","INCIERTA"})
    void retiradaSoloCierraFinanciacionDeQcAcreditadamenteNuncaReclamada(String fase){
        var plan=retirada();when(registro.descartarTemporal(job,archivo,2,"retirar",actor,sid)).thenReturn(plan);when(almacen.abrir(clave)).thenReturn(Optional.empty());
        var ref=new PresupuestoIaService.ReferenciaInterna(7,job);
        var prueba=new PresupuestoIaService.ComprobacionNoProcesamiento(PresupuestoIaService.claveDeEtapa(ref,ControlesMaterialIa.ETAPA_FINANCIERA),"QC_TEST","INTENTO_TEST","EVENTO_TEST","d".repeat(64),Instant.now());
        var gen=new PresupuestoIaService.ResultadoFacturable(List.of()); // Doble de callback, no medición/cobro real.
        when(presupuesto.acreditarEtapaNoReclamada(ref,ControlesMaterialIa.ETAPA_FINANCIERA,prueba)).thenReturn(fase.equals("PENDIENTE"));
        doAnswer(i->{DerivadasMaterialIaService.CierreControlRetirado cierre=i.getArgument(2);assertEquals(fase.equals("PENDIENTE"),cierre.cerrar(prueba,gen));return null;})
                .when(registro).confirmarAusencia(any(),any(),any());
        adaptador.descartarTemporal(job,archivo,2,"retirar",actor,sid);
        verify(presupuesto).acreditarEtapaNoReclamada(ref,ControlesMaterialIa.ETAPA_FINANCIERA,prueba);
        if(fase.equals("PENDIENTE"))verify(presupuesto).conciliar(ref,gen);else verify(presupuesto,never()).conciliar(any(),any());
    }
    @Test void retiradaExpresaVerificaObjetoYAusenciaSinExigirTransferenciaNiAnunciarLiberacionAntes(){
        var p=retirada();when(registro.descartarTemporal(job,archivo,2,"retirar",actor,sid)).thenReturn(p);var eliminado=new AtomicBoolean();
        when(almacen.abrir(clave)).thenAnswer(i->eliminado.get()?Optional.empty():Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"TEST","image/png")));
        doAnswer(i->{eliminado.set(true);return null;}).when(almacen).eliminar(clave);confirmarAusencia();
        var r=adaptador.descartarTemporal(job,archivo,2,"retirar",actor,sid);assertEquals("RETIRADA_VERIFICADA",r.estadoGestion());assertFalse(r.disponible());
        var orden=inOrder(registro,almacen,cuota);orden.verify(registro).descartarTemporal(job,archivo,2,"retirar",actor,sid);
        orden.verify(almacen).abrir(clave);orden.verify(almacen).eliminar(clave);orden.verify(almacen).abrir(clave);orden.verify(registro).confirmarAusencia(eq(p),any(),any());
        orden.verify(cuota).liberarRetiradaVerificada(eq(UsoAlmacenamientoB0.referenciaPrevision(7,job)),any());verifyNoInteractions(fuente,salidas);
    }
    @ParameterizedTest @ValueSource(strings={"HASH","NAMESPACE","SDK_LECTURA","SDK_DELETE","AUSENCIA_INCIERTA"})
    void retiradaNoConfirmadaConservaCuotaEIntencion(String fallo){
        when(registro.descartarTemporal(job,archivo,2,"retirar",actor,sid)).thenReturn(retirada());
        switch(fallo){
            case "HASH"->when(almacen.abrir(clave)).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(new byte[]{9,2,3},"TEST","image/png")));
            case "NAMESPACE"->when(almacen.identidadTecnica()).thenReturn("b".repeat(64));
            case "SDK_LECTURA"->when(almacen.abrir(clave)).thenThrow(new IllegalStateException(clave));
            case "SDK_DELETE"->doThrow(new IllegalStateException(clave)).when(almacen).eliminar(clave);
            case "AUSENCIA_INCIERTA"->when(almacen.abrir(clave)).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"TEST","image/png"))).thenThrow(new IllegalStateException(clave));
        }
        var e=assertThrows(RuntimeException.class,()->adaptador.descartarTemporal(job,archivo,2,"retirar",actor,sid));assertFalse(e.getMessage().contains(clave));
        verify(registro,never()).confirmarAusencia(any(),any(),any());verifyNoInteractions(cuota,fuente,salidas,uso);
    }
    @Test void deleteAnteriorConRollbackDbSeRecuperaPorAusenciaSinOtraEscritura(){
        var p=retirada();when(registro.descartarTemporal(job,archivo,2,"retirar",actor,sid)).thenReturn(p);when(almacen.abrir(clave)).thenReturn(Optional.empty());confirmarAusencia();
        assertEquals("RETIRADA_VERIFICADA",adaptador.descartarTemporal(job,archivo,2,"retirar",actor,sid).estadoGestion());
        verify(almacen,never()).eliminar(anyString());verify(registro).confirmarAusencia(eq(p),any(),any());verify(cuota).liberarRetiradaVerificada(any(),any());verifyNoInteractions(salidas,fuente);
    }
    ResultadosMaterialIa.GrupoCaducidad prepararGrupo(){
        var v=version(true,"DISPONIBLE");var grupo=new ResultadosMaterialIa.GrupoCaducidad(original,1,"d".repeat(64),v.venceEn().minusSeconds(86400),v.venceEn(),List.of(v));
        when(registro.grupos(eq(actor),any())).thenAnswer(i->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            java.util.function.Predicate<Lectura> filtro=i.getArgument(1);return filtro.test(lectura)?List.of(grupo):List.of();
        });return grupo;
    }
    @ParameterizedTest @ValueSource(strings={"PERMITIDO","PERMISO_INSUFICIENTE","CAPACIDAD_DESHABILITADA"})
    void proyeccionDeAvisoEvaluaDenegacionComoDatoEnMismaTransaccionSinGuardiaProxy(String valor){
        var grupo=prepararGrupo();var estado=EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.valueOf(valor);
        when(fuente.evaluarLecturaArtefacto(lectura.solicitud(),lectura.snapshot(),actor)).thenAnswer(i->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return estado;});
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{
            assertEquals(estado==EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMITIDO?List.of(grupo):List.of(),adaptador.avisosDeCaducidad(actor));
            verify(registro,never()).autorizarArchivo(any(),any(),any());verify(fuente,never()).exigirLecturaArtefacto(any(),any(),any());
            verify(almacen,never()).abrir(anyString());verifyNoInteractions(cuota,salidas,uso);
        }finally{TransactionSynchronizationManager.clear();}
    }
    @ParameterizedTest @ValueSource(strings={"TECNICO","SIN_ESTADO","NAMESPACE"})
    void proyeccionDeAvisoNoOcultaFalloTecnicoComoAusencia(String caso){
        prepararGrupo();
        switch(caso){
            case "TECNICO"->when(fuente.evaluarLecturaArtefacto(any(),any(),eq(actor))).thenThrow(new IllegalStateException("TEST fuente técnica falló"));
            case "SIN_ESTADO"->when(fuente.evaluarLecturaArtefacto(any(),any(),eq(actor))).thenReturn(null);
            case "NAMESPACE"->when(almacen.identidadTecnica()).thenReturn("b".repeat(64));
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThrows(RuntimeException.class,()->adaptador.avisosDeCaducidad(actor));verify(almacen,never()).abrir(anyString());}
        finally{TransactionSynchronizationManager.clear();}
    }
    @Test void evaluacionNoExcepcionalNoSustituyeGuardiaEstrictaDeDescarga(){
        when(fuente.evaluarLecturaArtefacto(any(),any(),eq(actor))).thenReturn(EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMISO_INSUFICIENTE);
        doThrow(new AccesoNoAutorizadoException()).when(fuente).exigirLecturaArtefacto(any(),any(),eq(actor));
        assertThrows(AccesoNoAutorizadoException.class,()->adaptador.descargar(job,archivo,actor,sid));
        verify(fuente,never()).evaluarLecturaArtefacto(any(),any(),any());verify(almacen,never()).abrir(anyString());
    }
}
