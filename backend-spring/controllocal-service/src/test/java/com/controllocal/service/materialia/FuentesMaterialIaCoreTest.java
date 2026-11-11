package com.controllocal.service.materialia;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.documentos.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Fuente real, no inferencia: minimización, versiones, contexto y evidencia actual. */
class FuentesMaterialIaCoreTest {
    private final Actor actor=new Actor(1,10,20,Actor.AGENTE);
    private final ContextoSesion sesion=mock(ContextoSesion.class);
    private final PlantillaComercialV08 proyector=mock(PlantillaComercialV08.class);
    private final AccesoEncargo encargos=mock(AccesoEncargo.class);
    private final SeleccionInmueblePieza inmuebles=mock(SeleccionInmueblePieza.class);
    private final SeleccionFotosContextualesService galeria=mock(SeleccionFotosContextualesService.class);
    private final ArchivosFoto archivos=mock(ArchivosFoto.class);
    private final EvidenciasTratamientoMaterialIa evidencias=mock(EvidenciasTratamientoMaterialIa.class);
    private final ContextoPresupuestoB0 contextos=mock(ContextoPresupuestoB0.class);
    private final ContextosDeActividad actividad=mock(ContextosDeActividad.class);
    private final PlatformTransactionManager gestor=mock(PlatformTransactionManager.class);
    private final UUID original=UUID.randomUUID();
    private FuentesMaterialIaCore fuentes;

    @BeforeEach void preparar(){
        when(gestor.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        when(proyector.prepararFisicaPrivada(7,actor)).thenReturn(new PlantillaComercialV08.ProyeccionFisica(
                Map.of("tipo",Map.of("estado","VIGENTE","valor","D"),"atributos",Map.of(
                        "dormitorios",Map.of("estado","VIGENTE","valor",2,"observacion","NOTA PRIVADA",
                                "procedencia",Map.of("registradoEn","2026-11-01","evidenciaRef","ID PRIVADO")))),3));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.PENDIENTE));
        var p=mock(Propiedad.class);when(p.getVersionFisica()).thenReturn(3L);
        when(inmuebles.exigirLecturaHistorica(1,7,actor)).thenReturn(p);
        when(galeria.observar(7,actor)).thenReturn(new SeleccionFotosContextualesService.Observacion(
                new SeleccionFotosContextualesService.Contexto("PROPIEDAD",7),
                List.of(new SeleccionFotosContextualesService.FotoVersionada(original,"foto TEST.png",0)),"galeria-1"));
        when(galeria.originalPermitidoParaLectura(7,original,actor)).thenReturn(true);
        var json=new Documentos(JsonMapper.builder().findAndAddModules().build());
        fuentes=new FuentesMaterialIaCore(proyector,encargos,inmuebles,galeria,archivos,new EvaluacionTecnicaFoto(),
                evidencias,contextos,actividad,json,gestor);
    }

    @Test void fisicaParcialEntregaBorradorPermitidoSinEncargoNiConsentimientoFicticio(){
        var s=solicitud(Accion.REDACTAR_FICHA);var f=fuentes.preparar(s,actor,sesion);
        assertEquals(EstadoEvidencia.PENDIENTE,f.evidencia().estado());
        assertTrue(f.utilidadLocal().containsKey("borradorEstructurado"));
        assertFalse(f.datosPermitidos().toString().contains("NOTA PRIVADA"));
        assertFalse(f.datosPermitidos().toString().contains("ID PRIVADO"));
        assertTrue(f.instruccionPermitida().contains(s.instruccion()));
        assertTrue(f.instruccionPermitida().startsWith("Redactar una ficha inmobiliaria"));
        verifyNoInteractions(encargos,archivos);
        verify(evidencias).resolver(argThat(a->a.categorias().equals(Set.of("CARACTERISTICAS_FISICAS","INSTRUCCION"))),eq(actor),eq(sesion));
    }

    @Test void laNegativaDeContextoOcurreAntesDeLeerDatos(){
        doThrow(new AccesoNoAutorizadoException()).when(contextos).iniciarConsumo(actor,sesion);
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.preparar(solicitud(Accion.REDACTAR_FICHA),actor,sesion));
        verifyNoInteractions(proyector,archivos,evidencias);
    }

    @Test void elUuidDeOtraGaleriaNoPermiteLeerSusBytes(){
        var s=new Solicitud(UUID.randomUUID(),Accion.ANALIZAR_FOTO,new Recurso("PROPIEDAD","7"),null,UUID.randomUUID(),"Analizar esta fotografía.");
        assertThrows(NoEncontradoException.class,()->fuentes.preparar(s,actor,sesion));
        verifyNoInteractions(archivos,evidencias);
    }

    @Test void analizarFotoNoTransportaDatosFisicosNiCondicionesComerciales() throws Exception {
        when(archivos.original(original,actor)).thenReturn(png(0xffaabbcc));
        var s=solicitud(Accion.ANALIZAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        assertTrue(f.datosPermitidos().isEmpty());assertTrue(f.utilidadLocal().containsKey("evaluacionTecnicaLocal"));
        assertArrayEquals(fuentes.fotoParaProcesar(s,f,actor,sesion),fuentes.fotoParaProcesar(s,f,actor,sesion));
        verifyNoInteractions(proyector,encargos);
        verify(evidencias).resolver(argThat(a->a.categorias().equals(Set.of("FOTO_COMERCIAL","INSTRUCCION"))),eq(actor),eq(sesion));
    }

    /** Sólo bytes sintéticos: minimización/correspondencia, no fidelidad o legitimación IA. */
    @ParameterizedTest @EnumSource(value=Accion.class,names={"ANALIZAR_FOTO","MEJORAR_FOTO"})
    void copiaAutorizadaParaQcConservaOriginal1600x1067YRetiraMetadatos(Accion accion)throws Exception{
        byte[] binario=pngConTextoQc(),antes=binario.clone();when(archivos.original(original,actor)).thenReturn(binario);
        var s=solicitud(accion);var f=fuentes.preparar(s,actor,sesion);byte[] copia=fuentes.fotoParaProcesar(s,f,actor,sesion);
        assertArrayEquals(antes,binario);assertTrue(new String(binario,java.nio.charset.StandardCharsets.ISO_8859_1).contains("METADATO-PRIVADO-QC-TEST"));
        assertFalse(new String(copia,java.nio.charset.StandardCharsets.ISO_8859_1).contains("METADATO-PRIVADO-QC-TEST"));
        assertArrayEquals(new EvaluacionTecnicaFoto().evaluar(binario).contenido(),copia);
        var imagen=ImageIO.read(new java.io.ByteArrayInputStream(copia));assertEquals(1600,imagen.getWidth());assertEquals(1067,imagen.getHeight());
        verify(archivos,times(2)).original(original,actor);verifyNoMoreInteractions(archivos);
    }
    @ParameterizedTest @EnumSource(value=Accion.class,names={"ANALIZAR_FOTO","MEJORAR_FOTO"})
    void copiaParaQcRevalidaPermisoAntesDeLeerOriginal(Accion accion)throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xffaabbcc));var s=solicitud(accion);var f=fuentes.preparar(s,actor,sesion);
        clearInvocations(archivos);doThrow(new AccesoNoAutorizadoException()).when(contextos).iniciarConsumo(actor,sesion);
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.fotoParaProcesar(s,f,actor,sesion));verifyNoInteractions(archivos);
    }

    @Test void unaFotoAlteradaNoReutilizaLaPreparacionAnterior() throws Exception {
        when(archivos.original(original,actor)).thenReturn(png(0xffaabbcc));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        when(archivos.original(original,actor)).thenReturn(png(0xff113355));
        assertThrows(ConflictoException.class,()->fuentes.fotoParaProcesar(s,f,actor,sesion));
    }

    @Test void laConsultaRevalidaLaRevocacionActualYNoCreeElSnapshot(){
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.REDACTAR_FICHA);var f=fuentes.preparar(s,actor,sesion);
        doThrow(new AccesoNoAutorizadoException()).when(evidencias).revalidarActual(eq("prueba"),any(),eq(actor));
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.revalidar(s,f,actor));
    }

    @Test void elDestinoRealSeCompruebaYUnaDenegacionConservaSuClasificacion(){
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.REDACTAR_FICHA);var f=fuentes.preparar(s,actor,sesion);
        var destino=new DestinoTratamiento("destino TEST","perfil TEST","1");
        doThrow(new AccesoNoAutorizadoException()).when(evidencias).exigirVigente(eq("prueba"),any(),eq(destino),eq(actor),eq(sesion));
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.exigirTransferencia(s,f,destino,actor,sesion));
        verify(evidencias).exigirVigente(eq("prueba"),any(),eq(destino),eq(actor),eq(sesion));
    }

    @Test void cambiarLaInstruccionNoHeredaLaPreparacionAnterior(){
        var s=solicitud(Accion.REDACTAR_FICHA);var f=fuentes.preparar(s,actor,sesion);
        var otra=new Solicitud(s.operacion(),s.accion(),s.recurso(),s.versionOrigen(),null,"Una instrucción diferente TEST.");
        assertThrows(ConflictoException.class,()->fuentes.revalidar(otra,f,actor));
    }

    @Test void laIndicacionPersonalizadaEsOpcionalYNoSeInventaTextoDelUsuario(){
        var s=new Solicitud(UUID.randomUUID(),Accion.REDACTAR_FICHA,new Recurso("PROPIEDAD","7"),null,null,null);
        var f=fuentes.preparar(s,actor,sesion);
        assertTrue(f.instruccionPermitida().startsWith("Redactar una ficha inmobiliaria"));
        assertFalse(f.instruccionPermitida().contains("Indicaciones personalizadas"));
        assertEquals(EstadoEvidencia.PENDIENTE,f.evidencia().estado());
    }

    @Test void elAvisoHumanoRecibeElAmbitoPreparadoPorCore(){
        var aviso=mock(EvidenciasTratamientoMaterialIa.AvisoParaDecision.class);
        when(evidencias.presentar(any(),eq(actor),eq(sesion))).thenReturn(aviso);
        var s=solicitud(Accion.REDACTAR_FICHA);
        assertSame(aviso,fuentes.presentarTratamiento(s,actor,sesion));
        verify(evidencias).presentar(argThat(a->a.solicitud().equals(s)&&a.huellaFuente().matches("[0-9a-f]{64}")
                &&a.huellaPayload().matches("[0-9a-f]{64}")&&a.categorias().equals(Set.of("CARACTERISTICAS_FISICAS","INSTRUCCION"))),eq(actor),eq(sesion));
        verifyNoInteractions(encargos,archivos);
    }

    @Test void elHistoricoSigueLegibleConOtraVersionFisicaPeroNoPermiteEjecutarLaAnterior(){
        var s=solicitud(Accion.REDACTAR_FICHA);var f=fuentes.preparar(s,actor,sesion);
        when(proyector.prepararFisicaPrivada(7,actor)).thenReturn(new PlantillaComercialV08.ProyeccionFisica(Map.of("tipo","otra versión"),4));
        assertThrows(ConflictoException.class,()->fuentes.revalidar(s,f,actor));
        assertDoesNotThrow(()->fuentes.exigirLecturaHistorica(s,f,actor));
        when(inmuebles.exigirLecturaHistorica(1,7,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.exigirLecturaHistorica(s,f,actor));
    }

    @Test void elArtefactoUsaPermisoActualDelRecursoSinHeredarPreferenciaDeLaAutora()throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        var actual=new Actor(1,30,40,Actor.AGENTE);
        var observada=galeria.observar(7,actor);
        when(inmuebles.exigirLecturaHistorica(1,7,actual)).thenReturn(mock(Propiedad.class));
        when(galeria.observar(7,actual)).thenReturn(observada);
        when(galeria.originalPermitidoParaLectura(7,original,actual)).thenReturn(true);
        doThrow(new AccesoNoAutorizadoException()).when(evidencias).revalidarActual(eq("prueba"),any(),eq(actual));
        assertDoesNotThrow(()->fuentes.exigirLecturaArtefacto(s,f,actual));
        verify(evidencias).revalidarArtefacto(eq("prueba"),any(),eq(actual));
        verify(evidencias,never()).revalidarActual(any(),any(),eq(actual));
        verify(evidencias,never()).exigirVigente(any(),any(),any(),eq(actual),any());
        verify(archivos,never()).original(original,actual);
    }

    @Test void laRevocacionActualDeUsoDelArtefactoSeConserva()throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        doThrow(new AccesoNoAutorizadoException()).when(evidencias).revalidarArtefacto(eq("prueba"),any(),eq(actor));
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.exigirLecturaArtefacto(s,f,actor));
        verify(evidencias).revalidarArtefacto(eq("prueba"),any(),eq(actor));
    }

    @Test void unArtefactoSinOriginalEnGaleriaNoHeredaElPermisoDelSnapshot()throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        when(galeria.observar(7,actor)).thenReturn(new SeleccionFotosContextualesService.Observacion(
                new SeleccionFotosContextualesService.Contexto("PROPIEDAD",7),List.of(),"galeria-2"));
        when(galeria.originalPermitidoParaLectura(7,original,actor)).thenReturn(false);
        assertThrows(NoEncontradoException.class,()->fuentes.exigirLecturaArtefacto(s,f,actor));
        verify(evidencias,never()).revalidarArtefacto(any(),any(),any());
    }

    @ParameterizedTest @EnumSource(EstadoUsoArtefacto.class)
    void laProyeccionConservaElEstadoDeUsoSinConvertirDenegacionEnExcepcion(EstadoUsoArtefacto estado)throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        when(evidencias.evaluarUsoArtefacto(eq("prueba"),any(),eq(actor))).thenReturn(estado);
        assertEquals(estado,fuentes.evaluarLecturaArtefacto(s,f,actor));
        verify(evidencias,never()).revalidarArtefacto(any(),any(),any());
        verify(gestor,never()).rollback(any());
    }

    @Test void laAusenciaActualDelOriginalSeEvaluaSinDarPorResueltaLaCausa()throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        when(galeria.originalPermitidoParaLectura(7,original,actor)).thenReturn(false);
        assertEquals(EstadoUsoArtefacto.PERMISO_INSUFICIENTE,fuentes.evaluarLecturaArtefacto(s,f,actor));
        verify(evidencias,never()).evaluarUsoArtefacto(any(),any(),any());verify(gestor,never()).rollback(any());
    }

    @Test void laRevocacionFisicaSeEvaluaDentroDeLaTransaccionDeLectura()throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        when(inmuebles.exigirLecturaHistorica(1,7,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertEquals(EstadoUsoArtefacto.PERMISO_INSUFICIENTE,fuentes.evaluarLecturaArtefacto(s,f,actor));
        verify(evidencias,never()).evaluarUsoArtefacto(any(),any(),any());verify(gestor,never()).rollback(any());
    }

    @Test void unFalloTecnicoDeUsoNoSeConvierteEnUnaListaVacia()throws Exception{
        when(archivos.original(original,actor)).thenReturn(png(0xff123456));
        when(evidencias.resolver(any(),eq(actor),eq(sesion))).thenReturn(evidencia(EstadoEvidencia.ACREDITADA));
        var s=solicitud(Accion.MEJORAR_FOTO);var f=fuentes.preparar(s,actor,sesion);
        when(evidencias.evaluarUsoArtefacto(eq("prueba"),any(),eq(actor))).thenThrow(new IllegalStateException("Fallo TEST de consulta"));
        assertThrows(IllegalStateException.class,()->fuentes.evaluarLecturaArtefacto(s,f,actor));
        verify(gestor).rollback(any());
    }

    private Solicitud solicitud(Accion accion){return new Solicitud(UUID.randomUUID(),accion,new Recurso("PROPIEDAD","7"),null,
            accion==Accion.REDACTAR_FICHA?null:original,"Preparar asistencia inmobiliaria TEST.");}
    private static EvidenciaTratamiento evidencia(EstadoEvidencia estado){return new EvidenciaTratamiento(estado,"REDACTAR_FICHA",List.of("INSTRUCCION"),
            "condiciones TEST","proveedor TEST","ACEPTAR","prueba","base TEST","1");}
    private static byte[] pngConTextoQc()throws Exception{
        var imagen=new BufferedImage(1600,1067,BufferedImage.TYPE_INT_RGB);imagen.setRGB(10,10,0xffaabbcc);
        var escritor=ImageIO.getImageWritersByFormatName("png").next();var salida=new ByteArrayOutputStream();
        try(var destino=ImageIO.createImageOutputStream(salida)){
            escritor.setOutput(destino);var parametros=escritor.getDefaultWriteParam();
            var metadatos=escritor.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(imagen),parametros);
            var raiz=new javax.imageio.metadata.IIOMetadataNode("javax_imageio_png_1.0");
            var texto=new javax.imageio.metadata.IIOMetadataNode("tEXt");var entrada=new javax.imageio.metadata.IIOMetadataNode("tEXtEntry");
            entrada.setAttribute("keyword","Comment");entrada.setAttribute("value","METADATO-PRIVADO-QC-TEST");texto.appendChild(entrada);raiz.appendChild(texto);
            metadatos.mergeTree("javax_imageio_png_1.0",raiz);escritor.write(null,new javax.imageio.IIOImage(imagen,null,metadatos),parametros);
        }finally{escritor.dispose();}
        return salida.toByteArray();
    }
    private static byte[] png(int color) throws Exception {
        var imagen=new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<20;x++)for(int y=0;y<20;y++)imagen.setRGB(x,y,color);
        var salida=new ByteArrayOutputStream();assertTrue(ImageIO.write(imagen,"png",salida));return salida.toByteArray();
    }
}
