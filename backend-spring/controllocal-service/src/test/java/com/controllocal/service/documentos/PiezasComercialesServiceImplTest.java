package com.controllocal.service.documentos;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.PiezaComercial;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static com.controllocal.service.documentos.PiezasComercialesService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Autoridad/obsolescencia del servicio. El binario controlado NO certifica formato ni almacenamiento real. */
class PiezasComercialesServiceImplTest {
    private static final long ORG=1,ENCARGO=9,PROPIEDAD=7;
    private static final String PRIVADA="REVISION_PRIVADA",DIFUSION="DIFUSION_AUTORIZADA";
    private final Actor actor=new Actor(ORG,10,30,Actor.AGENTE);
    private final ContextoSesion sesion=new ContextoSesion("TEST",Long.MAX_VALUE,"TEST-SID",1);
    private final PiezaComercialRepository piezas=mock(PiezaComercialRepository.class);
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final ContextosDeActividad contextos=mock(ContextosDeActividad.class);
    private final SeleccionFotosContextualesService seleccion=mock(SeleccionFotosContextualesService.class);
    private final FotosComercialesService fotos=mock(FotosComercialesService.class);
    private final PlantillaComercialV08 proyecciones=mock(PlantillaComercialV08.class);
    private final PublicacionService publicaciones=mock(PublicacionService.class);
    private final CompositorPieza compositor=mock(CompositorPieza.class);
    private final ArchivosPieza archivos=mock(ArchivosPieza.class);
    private final ComandoIdempotenteRepository locks=mock(ComandoIdempotenteRepository.class);
    private final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final SeleccionInmueblePieza inmuebles=mock(SeleccionInmueblePieza.class);
    private final com.controllocal.service.materialia.ResultadosMaterialIa derivados=mock(com.controllocal.service.materialia.ResultadosMaterialIa.class);
    private final Captacion c=mock(Captacion.class);
    private final Propiedad propiedad=mock(Propiedad.class);
    private final ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();
    private final Documentos json=new Documentos(mapper);
    private final UUID ref=UUID.randomUUID();
    private final byte[] bytes="BINARIO CONTROLADO DE PRUEBA".getBytes(StandardCharsets.UTF_8);
    private Preparacion preparacion;
    private PiezaComercial p;
    private PiezasComercialesServiceImpl servicio;

    @BeforeEach void iniciar(){
        preparacion=new Preparacion(ENCARGO,0,2,"WEB",List.of(),"GALERIA-1",Formato.PDF_A4,"marfil",null,PROPIEDAD);
        var contenido=new PiezasComercialesServiceImpl.Contenido(preparacion,List.of(),List.of());
        var datos=Map.<String,Object>of("inmueble",Map.of("atributos",Map.of(),"fotos",List.of()),"presentacion",Map.of(),"pieza",Map.of());
        var base=new PlantillaComercialV08.Proyeccion(datos,"a".repeat(64),PROPIEDAD,30);
        var huella=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("fuente",base.huellaOrigen(),"contenido",contenido)));
        p=PiezaComercial.preparar(ORG,ENCARGO,PROPIEDAD,30,ref,ref,1,json.objetoCanonico(contenido),json.objetoCanonico(datos),
                huella,"PDF_A4",Instant.parse("2026-10-31T15:00:00Z"));
        ReflectionTestUtils.setField(p,"id",41L);
        when(acceso.leer(ENCARGO,actor)).thenReturn(c);when(acceso.escribir(eq(ENCARGO),eq(actor),any())).thenReturn(c);
        when(acceso.leerParaActuar(eq(ENCARGO),eq(actor),any())).thenReturn(c);
        when(propiedad.getId()).thenReturn(PROPIEDAD);when(propiedad.getOrganizacionId()).thenReturn(ORG);
        var objeto=new SeleccionInmueblePieza.Seleccion(propiedad,null,true);
        when(inmuebles.exigirLecturaHistorica(ORG,PROPIEDAD,actor)).thenReturn(propiedad);
        when(inmuebles.elegir(c,PROPIEDAD,actor)).thenReturn(objeto);
        when(inmuebles.revisarHistorica(c,PROPIEDAD,actor)).thenReturn(objeto);
        when(piezas.findByOrganizacionIdAndReferencia(ORG,ref)).thenReturn(Optional.of(p));
        when(piezas.bloquear(ORG,ref)).thenReturn(Optional.of(p));
        when(piezas.findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(ORG,ref)).thenReturn(Optional.of(p));
        when(seleccion.observar(PROPIEDAD,actor)).thenReturn(new SeleccionFotosContextualesService.Observacion(
                new SeleccionFotosContextualesService.Contexto("PROPIEDAD",PROPIEDAD),List.of(),"GALERIA-1"));
        when(proyecciones.proyectar(any(),eq(ref),eq(1L),anyString(),eq(actor))).thenReturn(base);
        when(contextos.ahora()).thenReturn(Instant.parse("2026-10-31T15:00:00Z"));
        when(contextos.hoy()).thenReturn(LocalDate.of(2026,10,31));
        when(compositor.configurado()).thenReturn(true);
        when(compositor.componer(anyMap(),anyMap(),eq("PDF_A4"))).thenReturn(new CompositorPieza.Binario(bytes,"application/pdf","pdf"));
        when(archivos.guardar(eq(ref),any(),anyString(),eq(p.getHuellaOrigen()),eq(actor))).thenReturn(
                new ArchivosPieza.Guardado("CLAVE-TECNICA",EvaluacionTecnicaFoto.huella(bytes)));
        servicio=new PiezasComercialesServiceImpl(piezas,acceso,contextos,seleccion,fotos,proyecciones,publicaciones,compositor,
                archivos,locks,comandos,json,eventos,inmuebles,derivados,new EvaluacionTecnicaFoto());
    }
    private UUID archivo(String finalidad){
        UUID id=UUID.randomUUID();p.registrarArchivo(finalidad,id,"CLAVE-TECNICA",EvaluacionTecnicaFoto.huella(bytes),contextos.ahora());
        when(piezas.deArchivo(ORG,id)).thenReturn(Optional.of(p));when(archivos.abrir("CLAVE-TECNICA",actor)).thenReturn(bytes);return id;
    }
    private void fueraDelObjeto(){when(inmuebles.revisarHistorica(c,PROPIEDAD,actor)).thenReturn(new SeleccionInmueblePieza.Seleccion(propiedad,null,false));}

    private AtomicReference<PiezaComercial> capturarPreparacionNueva(){
        var guardada=new AtomicReference<PiezaComercial>();
        var datos=Map.<String,Object>of("inmueble",Map.of("atributos",Map.of(),"fotos",List.of()),"presentacion",Map.of(),"pieza",Map.of());
        when(proyecciones.proyectar(any(),any(UUID.class),anyLong(),anyString(),eq(actor)))
                .thenReturn(new PlantillaComercialV08.Proyeccion(datos,"a".repeat(64),PROPIEDAD,30));
        when(piezas.saveAndFlush(any())).thenAnswer(i->{PiezaComercial nueva=i.getArgument(0);guardada.set(nueva);return nueva;});
        when(piezas.findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(eq(ORG),any(UUID.class)))
                .thenAnswer(i->Optional.ofNullable(guardada.get()));
        return guardada;
    }

    /** La versión anterior persistía este record de dos componentes, no un Map con DTOs anidados. */
    private record ContenidoAnterior(Preparacion datos,List<FotosComercialesService.FotoSalida> copias) { }
    @Test void laLecturaDeContenidoAnteriorSinSeleccionesConservaSuHuella(){
        var anterior=json.objetoCanonico(new ContenidoAnterior(preparacion,List.of()));
        var recuperado=json.comoObjeto(anterior,PiezasComercialesServiceImpl.Contenido.class);
        assertTrue(recuperado.selecciones().isEmpty());
        assertEquals(anterior,json.objetoCanonico(recuperado));
    }

    @Test void laImagenVigenteLaResuelveCoreAunqueElCuerpoNoIndiqueDerivada()throws Exception{
        var d=piezaConDerivada();var nuevas=capturarPreparacionNueva();
        var solicitud=new Preparacion(ENCARGO,0,2,"WEB",List.of(new Foto(d.original(),null,"Vista TEST")),
                "GALERIA-1",Formato.PDF_A4,"marfil",null,PROPIEDAD);
        servicio.preparar(solicitud,actor,sesion,"SELECCION-CORE",Procedencia.deLaPantalla());
        var contenido=json.comoObjeto(nuevas.get().getSolicitud(),PiezasComercialesServiceImpl.Contenido.class);
        assertEquals(d.job(),contenido.datos().fotos().getFirst().derivada().operacion());
        assertEquals(List.of(new ResultadosMaterialIa.Seleccion(d.original(),1,d.job(),d.archivo())),contenido.selecciones());
        var orden=inOrder(derivados,archivos);
        orden.verify(derivados).bloquearSeleccionesParaLectura(List.of(d.original()),actor);
        orden.verify(archivos).fotoDerivada(d.job(),d.archivo(),actor);
        verify(fotos,never()).prepararCopias(anyLong(),any(),any());
    }

    @Test void unCuerpoNoPuedeElegirOtraMejoraSinCambiarLaSeleccionCore()throws Exception{
        var d=piezaConDerivada();
        var solicitud=new Preparacion(ENCARGO,0,2,"WEB",List.of(new Foto(d.original(),null,"Vista TEST",
                new Derivada(UUID.randomUUID(),UUID.randomUUID(),1))),"GALERIA-1",Formato.PDF_A4,"marfil",null,PROPIEDAD);
        assertThrows(ConflictoException.class,()->servicio.preparar(solicitud,actor,sesion,"OTRA-IMAGEN-BODY",Procedencia.deLaPantalla()));
        verify(archivos,never()).fotoDerivada(any(),any(),any());verify(piezas,never()).saveAndFlush(any());
    }

    @Test void laSeleccionOriginalTambienSeBloqueaYNoSeAsumeVigentePorFaltarDerivada(){
        UUID original=UUID.randomUUID();var vigentes=List.of(new ResultadosMaterialIa.Seleccion(original,0,null,null));
        var copia=new FotosComercialesService.FotoSalida(original,original,32,24,"c".repeat(64),
                EvaluacionTecnicaFoto.VERSION,true,Instant.parse("2026-10-20T15:00:00Z"));
        when(derivados.bloquearSeleccionesParaLectura(List.of(original),actor)).thenReturn(vigentes);
        when(derivados.bloquearSelecciones(List.of(original),actor)).thenReturn(vigentes);
        when(derivados.consultarSelecciones(List.of(original),actor)).thenReturn(vigentes);
        when(fotos.prepararCopias(PROPIEDAD,List.of(original),actor)).thenReturn(Map.of(original,copia));when(fotos.consultar(original,actor)).thenReturn(copia);
        var nuevas=capturarPreparacionNueva();var solicitud=new Preparacion(ENCARGO,0,2,"WEB",List.of(new Foto(original,null,"Original")),
                "GALERIA-1",Formato.PDF_A4,"marfil",null,PROPIEDAD);
        servicio.preparar(solicitud,actor,sesion,"ORIGINAL-CORE",Procedencia.deLaPantalla());
        var orden=inOrder(derivados,fotos);orden.verify(derivados).bloquearSeleccionesParaLectura(List.of(original),actor);
        orden.verify(fotos).prepararCopias(PROPIEDAD,List.of(original),actor);
        var nueva=nuevas.get();when(piezas.findByOrganizacionIdAndReferencia(ORG,nueva.getReferencia())).thenReturn(Optional.of(nueva));
        when(piezas.bloquear(ORG,nueva.getReferencia())).thenReturn(Optional.of(nueva));
        when(derivados.bloquearSeleccionesParaLectura(List.of(original),actor)).thenReturn(List.of(
                new ResultadosMaterialIa.Seleccion(original,1,UUID.randomUUID(),UUID.randomUUID())));
        assertThrows(ConflictoException.class,()->servicio.previsualizar(nueva.getReferencia(),1,actor,sesion,"ORIGINAL-CAMBIO",Procedencia.deLaPantalla()));
        verifyNoInteractions(compositor);verify(archivos,never()).guardar(any(),any(),anyString(),anyString(),any());
    }

    @Test void alcanceAjenoSeRechazaAntesDeBloquearCuentasDelEncargo(){
        doThrow(new AccesoNoAutorizadoException()).when(acceso).exigirLectura(ENCARGO,actor);
        var procedencia=Procedencia.deLaPantalla();
        List<org.junit.jupiter.api.function.Executable> peticiones=List.of(
                ()->servicio.preparar(preparacion,actor,sesion,"AJENO-PREPARAR",procedencia),
                ()->servicio.regenerar(new Regeneracion(ref,1,preparacion),actor,sesion,"AJENO-REGENERAR",procedencia),
                ()->servicio.previsualizar(ref,1,actor,sesion,"AJENO-PREVISUALIZAR",procedencia));
        for(var peticion:peticiones)assertThrows(AccesoNoAutorizadoException.class,peticion);
        verify(acceso,times(3)).exigirLectura(ENCARGO,actor);
        verify(acceso,never()).iniciar(any(),any(),any());
        verify(acceso,never()).escribir(anyLong(),any(),any());
        verify(compositor,never()).componer(anyMap(),anyMap(),anyString());
        verify(archivos,never()).guardar(any(),any(),anyString(),anyString(),any());
    }

    private record DerivadaPrueba(UUID job,UUID archivo,UUID original,byte[] imagen) { }
    private DerivadaPrueba piezaConDerivada()throws Exception{
        return piezaConDerivada(EvaluacionTecnicaFoto.VERSION);
    }
    private DerivadaPrueba piezaConDerivada(String versionLocal)throws Exception{
        UUID job=UUID.randomUUID(),archivo=UUID.randomUUID(),original=UUID.randomUUID();
        var imagen=new BufferedImage(32,24,BufferedImage.TYPE_INT_RGB);var salida=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagen,"png",salida));byte[] fuente=salida.toByteArray();
        var copia=new EvaluacionTecnicaFoto().evaluar(fuente);Instant registrada=Instant.parse("2026-10-20T15:00:00Z");
        var metadatos=new com.controllocal.service.materialia.ResultadosMaterialIa.Archivo(job,original,archivo,
                EvaluacionTecnicaFoto.huella(fuente),"image/png","MODELO PRIVADO TEST","VERSION PRIVADA TEST",registrada);
        when(derivados.exigirArchivo(job,archivo,actor)).thenReturn(metadatos);
        var vigentes=List.of(new ResultadosMaterialIa.Seleccion(original,1,job,archivo));
        when(derivados.bloquearSeleccionesParaLectura(List.of(original),actor)).thenReturn(vigentes);
        when(derivados.bloquearSelecciones(List.of(original),actor)).thenReturn(vigentes);
        when(derivados.consultarSelecciones(List.of(original),actor)).thenReturn(vigentes);
        when(archivos.fotoDerivada(job,archivo,actor)).thenReturn(fuente);
        preparacion=new Preparacion(ENCARGO,0,2,"WEB",List.of(new Foto(original,null,"Vista TEST",new Derivada(job,archivo,1))),
                "GALERIA-1",Formato.PDF_A4,"marfil",null,PROPIEDAD);
        String proceso="IA-"+Idempotencia.huellaExacta(metadatos.proceso()+"\u0000"+metadatos.versionProceso()+"\u0000"+metadatos.huella())+"/"+versionLocal;
        var contenido=new PiezasComercialesServiceImpl.Contenido(preparacion,List.of(new FotosComercialesService.FotoSalida(
                archivo,original,copia.evaluacion().anchoCopia(),copia.evaluacion().altoCopia(),copia.huella(),proceso,true,registrada)),vigentes);
        var datos=Map.<String,Object>of("inmueble",Map.of("atributos",Map.of(),"fotos",List.of()),"presentacion",Map.of(),"pieza",Map.of());
        var base=new PlantillaComercialV08.Proyeccion(datos,"a".repeat(64),PROPIEDAD,30);
        String huella=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("fuente",base.huellaOrigen(),"contenido",contenido)));
        p=PiezaComercial.preparar(ORG,ENCARGO,PROPIEDAD,30,ref,ref,1,json.objetoCanonico(contenido),json.objetoCanonico(datos),huella,"PDF_A4",contextos.ahora());
        ReflectionTestUtils.setField(p,"id",41L);
        when(piezas.findByOrganizacionIdAndReferencia(ORG,ref)).thenReturn(Optional.of(p));when(piezas.bloquear(ORG,ref)).thenReturn(Optional.of(p));
        when(piezas.findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(ORG,ref)).thenReturn(Optional.of(p));
        when(proyecciones.proyectar(any(),eq(ref),eq(1L),anyString(),eq(actor))).thenReturn(base);
        when(archivos.guardar(eq(ref),any(),anyString(),eq(huella),eq(actor))).thenReturn(new ArchivosPieza.Guardado("CLAVE-TECNICA",EvaluacionTecnicaFoto.huella(bytes)));
        return new DerivadaPrueba(job,archivo,original,fuente);
    }

    @Test void noUsarImpideUnaNuevaGeneracionSinReprocesarNiSustituirFotos()throws Exception{
        piezaConDerivada();doThrow(new ConflictoException("La selección ya no está vigente.")).when(derivados).exigirMismasSeleccionesParaLectura(anyList(),eq(actor));
        assertThrows(ConflictoException.class,()->servicio.previsualizar(ref,1,actor,sesion,"IA-NO-USAR",Procedencia.deLaPantalla()));
        assertNull(p.archivo(PRIVADA));verify(archivos,never()).fotoDerivada(any(),any(),any());verifyNoInteractions(fotos,compositor);
    }

    @Test void noUsarNoReescribeNiOcultaUnaPiezaYaEmitidaAutorizada()throws Exception{
        var d=piezaConDerivada();UUID emitido=archivo("DIFUSION_AUTORIZADA");
        doThrow(new ConflictoException("Otra elección actual.")).when(derivados).exigirMismasSeleccionesParaLectura(anyList(),eq(actor));
        assertArrayEquals(bytes,servicio.descargar(emitido,actor).contenido());
        assertEquals(emitido,p.archivo("DIFUSION_AUTORIZADA"));
        verify(derivados,never()).exigirMismasSeleccionesParaLectura(anyList(),any());
        verifyNoInteractions(compositor,fotos);
        when(derivados.exigirArchivo(d.job(),d.archivo(),actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.descargar(emitido,actor));
    }

    @Test void actualizarElProcesoLocalNoOcultaElBinarioEmitidoConVersionAnterior()throws Exception{
        piezaConDerivada("FOTO-LOCAL-ANTERIOR");UUID emitido=archivo("DIFUSION_AUTORIZADA");
        assertArrayEquals(bytes,servicio.descargar(emitido,actor).contenido());
        verify(archivos,never()).fotoDerivada(any(),any(),any());verifyNoInteractions(compositor);
    }

    @Test void difundirProtegeLaVersionRealDespuesDeVincularElArchivo()throws Exception{
        var d=piezaConDerivada();
        servicio.obtenerParaDifusion(new Entrega(ref,1,p.getHuellaOrigen()),actor,sesion,"IA-DIF-PROTEGIDA",Procedencia.deLaPantalla());
        assertNotNull(p.archivo(DIFUSION));
        var orden=inOrder(piezas,derivados);
        orden.verify(piezas).saveAndFlush(p);
        orden.verify(derivados).protegerPorPieza(ref,1,List.of(
                new ResultadosMaterialIa.Seleccion(d.original(),1,d.job(),d.archivo())),actor,sesion);
    }

    @Test void unaFotoOriginalHistoricaConservaSusTresCamposCanonicos(){
        UUID original=UUID.randomUUID();
        String anterior=json.objetoCanonico(Map.of("original",original,"ambiente","Sala","rotulo","Principal"));
        Foto recuperada=json.comoObjeto(anterior,Foto.class);
        assertNull(recuperada.derivada());
        assertEquals(anterior,json.objetoCanonico(recuperada),"Un atributo opcional nuevo no cambia la huella de la pieza histórica.");
    }

    @Test void laRevisionHumanaConservaFotosSinRevelarElProcesoTecnico()throws Exception{
        piezaConDerivada();
        var interno=mapper.readTree(p.getSolicitud()).path("copias").get(0);
        assertTrue(interno.hasNonNull("versionProceso"));
        String publicado=json.objetoCanonico(servicio.resumenEntrega(new Entrega(ref,1,p.getHuellaOrigen()),actor));
        var humanas=mapper.readTree(publicado).path("fotos");assertEquals(1,humanas.size());
        for(String campo:List.of("referencia","original","ancho","alto","huella","exifDepurado","originalRegistradoEn"))
            assertEquals(interno.get(campo),humanas.get(0).get(campo),campo);
        assertFalse(publicado.contains("versionProceso"));assertFalse(publicado.contains("MODELO PRIVADO TEST"));
        verify(compositor,never()).componer(anyMap(),anyMap(),anyString());
    }

    @Test void perderPublicabilidadDuranteElAlmacenamientoImpideEmitirYProteger()throws Exception{
        piezaConDerivada();var almacenada=new AtomicBoolean();
        when(archivos.guardar(eq(ref),any(),eq(DIFUSION),eq(p.getHuellaOrigen()),eq(actor))).thenAnswer(i->{
            almacenada.set(true);return new ArchivosPieza.Guardado("CLAVE-TECNICA",EvaluacionTecnicaFoto.huella(bytes));
        });
        doAnswer(i->{if(almacenada.get())throw new ReglaNegocioException("La pieza dejó de ser publicable durante el I/O.");return null;})
                .when(publicaciones).exigirPublicabilidadDePieza(ENCARGO,PROPIEDAD,"WEB",actor);
        assertThrows(ReglaNegocioException.class,()->servicio.obtenerParaDifusion(new Entrega(ref,1,p.getHuellaOrigen()),
                actor,sesion,"IA-DIF-PIERDE-PUB",Procedencia.deLaPantalla()));
        assertTrue(almacenada.get());assertNull(p.archivo(DIFUSION));
        verify(piezas,never()).saveAndFlush(any());
        verify(derivados,never()).protegerPorPieza(any(),anyLong(),anyList(),any(),any());
    }

    @Test void cambiarLaEleccionDuranteLaComposicionNoConfirmaUnArchivoNuevo()throws Exception{
        var d=piezaConDerivada();var cambiada=new AtomicBoolean();
        doAnswer(i->{
            if(cambiada.get())throw new ConflictoException("Cambió la selección.");
            return null;}).when(derivados).exigirMismasSeleccionesParaLectura(anyList(),eq(actor));
        when(compositor.componer(anyMap(),anyMap(),eq("PDF_A4"))).thenAnswer(i->{cambiada.set(true);return new CompositorPieza.Binario(bytes,"application/pdf","pdf");});
        assertThrows(ConflictoException.class,()->servicio.previsualizar(ref,1,actor,sesion,"IA-CAMBIO-DURANTE",Procedencia.deLaPantalla()));
        assertNull(p.archivo(PRIVADA));verify(archivos,never()).guardar(any(),any(),anyString(),anyString(),any());
    }

    @Test void laCopiaComercialUsaElDerivadoSinExponerSuProcesadorNiAlterarlo()throws Exception{
        var d=piezaConDerivada();byte[] original=d.imagen().clone();
        var argumentos=org.mockito.ArgumentCaptor.forClass(Map.class);
        servicio.previsualizar(ref,1,actor,sesion,"IA-COPIA-SALIDA",Procedencia.deLaPantalla());
        verify(derivados,never()).protegerPorPieza(any(),anyLong(),anyList(),any(),any());
        verify(compositor).componer(argumentos.capture(),anyMap(),eq("PDF_A4"));
        var foto=(Map<?,?>)((List<?>)((Map<?,?>)argumentos.getValue().get("inmueble")).get("fotos")).getFirst();
        assertEquals(true,foto.get("exifDepurado"));
        assertEquals("DERIVADA_IA",foto.get("origen"));assertEquals(true,foto.get("mejoraIA"));
        assertEquals(EvaluacionTecnicaFoto.huella(new EvaluacionTecnicaFoto().evaluar(original).contenido()),foto.get("sha256"));
        assertArrayEquals(original,d.imagen());assertFalse(argumentos.getValue().toString().contains("MODELO PRIVADO"));
        assertFalse(p.getSolicitud().contains("VERSION PRIVADA TEST"));verifyNoInteractions(fotos);
    }

    @Test void privadoSinFotosNoSeEntregaTrasRevocarLecturaFisica(){
        UUID id=archivo(PRIVADA);when(inmuebles.exigirLecturaHistorica(ORG,PROPIEDAD,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.descargar(id,actor));
        verify(archivos,never()).abrir(anyString(),any());
    }
    @Test void repeticionDePreviewSinFotosVuelveAAutorizarElSnapshot(){
        UUID id=archivo(PRIVADA);
        var a=new Archivo(id,ref,1,PRIVADA,"Vista previa · ficha.pdf","application/pdf",EvaluacionTecnicaFoto.huella(bytes));
        var recibo=mock(com.controllocal.domain.auditoria.ComandoIdempotente.class);
        when(recibo.getResultado()).thenReturn(json.objetoCanonico(a));
        when(comandos.buscar(eq(actor),eq("PREVIEW-REPETIDA"),eq("previsualizar_pieza_comercial"),anyString())).thenReturn(Optional.of(recibo));
        when(inmuebles.exigirLecturaHistorica(ORG,PROPIEDAD,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.previsualizar(ref,1,actor,sesion,"PREVIEW-REPETIDA",Procedencia.deLaPantalla()));
        verifyNoInteractions(compositor);verify(archivos,never()).abrir(anyString(),any());
    }
    @Test void revocarLecturaDuranteIoImpideLaEntregaAunqueNoHayaFotos(){
        UUID id=archivo(PRIVADA);var revocada=new AtomicBoolean();
        when(inmuebles.exigirLecturaHistorica(ORG,PROPIEDAD,actor)).thenAnswer(i->{if(revocada.get())throw new AccesoNoAutorizadoException();return propiedad;});
        when(archivos.abrir("CLAVE-TECNICA",actor)).thenAnswer(i->{revocada.set(true);return bytes;});
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.descargar(id,actor));
        verify(archivos).abrir("CLAVE-TECNICA",actor);
    }
    @Test void historicoPrivadoConservaLecturaFueraDeLaComposicionActual(){
        UUID id=archivo(PRIVADA);fueraDelObjeto();
        assertArrayEquals(bytes,servicio.descargar(id,actor).contenido());
        verifyNoInteractions(publicaciones,compositor);
    }
    @Test void primeraPreviewHistoricaUsaSnapshotAunqueUnidadYaNoPertenezca(){
        fueraDelObjeto();var a=servicio.previsualizar(ref,1,actor,sesion,"PREVIEW-HISTORICA",Procedencia.deLaPantalla());
        assertNotNull(a.referencia());assertEquals(PRIVADA,a.finalidad());assertTrue(a.nombre().startsWith("Vista previa"));
        verify(proyecciones,never()).proyectar(any(),any(),anyLong(),anyString(),any());
        verifyNoInteractions(publicaciones);
    }
    @Test void historicoFueraDelObjetoNoSeEntregaParaDifusion(){
        UUID id=archivo(DIFUSION);fueraDelObjeto();
        assertThrows(ConflictoException.class,()->servicio.descargar(id,actor));verify(archivos,never()).abrir(anyString(),any());
    }
    @Test void pubSeCompruebaSobreElInmuebleDescrito(){
        doThrow(new ReglaNegocioException("Falta un dato PUB de la unidad")).when(publicaciones).exigirPublicabilidadDePieza(ENCARGO,PROPIEDAD,"WEB",actor);
        assertThrows(ReglaNegocioException.class,()->servicio.validarEntrega(new Entrega(ref,1,p.getHuellaOrigen()),actor));
        verify(publicaciones).exigirPublicabilidadDePieza(ENCARGO,PROPIEDAD,"WEB",actor);
        verify(publicaciones,never()).exigirPublicabilidad(anyLong(),anyString(),any());
    }
    @Test void cambioDePubDuranteIoBloqueaNuevaEntrega(){
        UUID id=archivo(DIFUSION);var cambio=new AtomicBoolean();
        doAnswer(i->{if(cambio.get())throw new ReglaNegocioException("La regla vigente requiere completar la unidad");return null;})
                .when(publicaciones).exigirPublicabilidadDePieza(ENCARGO,PROPIEDAD,"WEB",actor);
        when(archivos.abrir("CLAVE-TECNICA",actor)).thenAnswer(i->{cambio.set(true);return bytes;});
        assertThrows(ReglaNegocioException.class,()->servicio.descargar(id,actor));
        verify(publicaciones,times(2)).exigirPublicabilidadDePieza(ENCARGO,PROPIEDAD,"WEB",actor);
    }
    @Test void unCambioDeOrigenBloqueaDifusionPeroNoBorraElPrivadoHistorico(){
        UUID privado=archivo(PRIVADA),difusion=archivo(DIFUSION);
        when(proyecciones.proyectar(any(),eq(ref),eq(1L),anyString(),eq(actor))).thenReturn(
                new PlantillaComercialV08.Proyeccion(Map.of(),"b".repeat(64),PROPIEDAD,30));
        assertThrows(ConflictoException.class,()->servicio.descargar(difusion,actor));
        assertArrayEquals(bytes,servicio.descargar(privado,actor).contenido());
    }
    @Test void bytesModificadosNoSeEntreganComoArchivoIntegro(){
        UUID id=archivo(PRIVADA);when(archivos.abrir("CLAVE-TECNICA",actor)).thenReturn("MODIFICADO".getBytes(StandardCharsets.UTF_8));
        assertThrows(ConflictoException.class,()->servicio.descargar(id,actor));
    }
    @Test void falloDelCompositorNoRegistraArchivoNiExito(){
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenThrow(new CapacidadDeshabilitadaException());
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.previsualizar(ref,1,actor,sesion,"PREVIEW-NO-DISPONIBLE",Procedencia.deLaPantalla()));
        assertNull(p.archivo(PRIVADA));verify(archivos,never()).guardar(any(),any(),anyString(),anyString(),any());
        verifyNoInteractions(eventos);verify(comandos,never()).registrar(any(),anyString(),anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    @Test void configuracionApagadaNoProduceArchivoVacioNiInvocaElCompositor(){
        when(compositor.configurado()).thenReturn(false);
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.previsualizar(ref,1,actor,sesion,"COMPOSICION-OFF",Procedencia.deLaPantalla()));
        assertNull(p.archivo(PRIVADA));verify(compositor,never()).componer(anyMap(),anyMap(),anyString());
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.validarEntrega(new Entrega(ref,1,p.getHuellaOrigen()),actor));
    }
    @Test void archivoConservadoSigueSiendoRecuperableConCompositorApagado(){
        UUID id=archivo(PRIVADA);when(compositor.configurado()).thenReturn(false);
        var resultado=servicio.previsualizar(ref,1,actor,sesion,"COMPOSICION-OFF-CACHE",Procedencia.deLaPantalla());
        assertEquals(id,resultado.referencia());assertArrayEquals(bytes,servicio.descargar(id,actor).contenido());
        verify(compositor,never()).componer(anyMap(),anyMap(),anyString());
    }
    @Test void recuperacionExactaPrecedeAConfigurarOtroProcesoDeComposicion(){
        when(compositor.configurado()).thenReturn(false);
        when(archivos.recuperar(ref,PRIVADA,p.getHuellaOrigen(),actor)).thenReturn(Optional.of(
                new ArchivosPieza.Guardado("CLAVE-RECUPERADA",EvaluacionTecnicaFoto.huella(bytes))));
        var resultado=servicio.previsualizar(ref,1,actor,sesion,"COMPOSICION-OFF-RECUPERAR",Procedencia.deLaPantalla());
        assertNotNull(resultado.referencia());assertEquals("CLAVE-RECUPERADA",p.clave(PRIVADA));
        verify(compositor,never()).componer(anyMap(),anyMap(),anyString());
    }
}
