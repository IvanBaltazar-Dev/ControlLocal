package com.controllocal.arquitectura;

import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.PublicacionService.DatosCambio;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.impl.PublicacionServiceImpl;
import com.controllocal.service.soporte.Procedencia;
import com.tngtech.archunit.core.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** RF022 permite crear borrador sinPUB. La exposición y su historia conservan puertas distintas. */
class PuertasDePublicacionTest {
    private static final JavaClasses CLASES=ImportacionesDeProducto.importador()
            .importPackages("com.controllocal");
    private static final Set<String> SUPERFICIE=Set.of("evaluarPublicabilidad","exigirPublicabilidad","exigirPublicabilidadDePieza","listarDeEncargo","obtener","consultarHistoria",
            "crearBorradorAnuncio","editarBorradorAnuncio","validarActuacion","previsualizarActuacion","exigirLecturaActuacion",
            "publicarAnuncio","actualizarAnuncioVisible","pausarAnuncio","reanudarAnuncio","finalizarDifusionAnuncio","consultarResultado");
    private static final Set<String> SENSIBLES=Set.of("publicarAnuncio","actualizarAnuncioVisible","pausarAnuncio","reanudarAnuncio","finalizarDifusionAnuncio");
    @Test void superficieCerradaYContextual(){
        Set<String> nombres=Arrays.stream(PublicacionService.class.getMethods()).map(Method::getName).collect(Collectors.toSet());
        assertEquals(SUPERFICIE,nombres);
        for(Method metodo:PublicacionService.class.getMethods())assertTrue(Arrays.asList(metodo.getParameterTypes()).contains(Actor.class),metodo.toString());
    }
    @Test void noVuelvenCreacionImplicitaNiSelectorGenerico(){
        var nombres=Arrays.stream(PublicacionService.class.getMethods()).map(Method::getName).toList();
        for(String retirado:List.of("crear","sincronizar","crearEnEncargo","actualizar","cambiarEstado","listarPorInmueble","codigoEstadoPublicacion","codigosEstadoPublicacion"))assertFalse(nombres.contains(retirado),retirado);
    }
    @Test void efectosSensiblesExigenTransaccionDelProtocoloHumano() throws Exception {
        for(String nombre:SENSIBLES){var m=PublicacionServiceImpl.class.getMethod(nombre,DatosCambio.class,Actor.class,ContextoSesion.class,String.class,Procedencia.class);
            var t=m.getAnnotation(Transactional.class);assertNotNull(t,nombre);assertEquals(Propagation.MANDATORY,t.propagation(),nombre);}
    }
    @Test void unaSolaConstruccionDeAnunciosPrivados(){
        var constructores=CLASES.stream().flatMap(c->c.getConstructorCallsFromSelf().stream())
                .filter(c->c.getTargetOwner().isEquivalentTo(Publicacion.class)).map(c->c.getOriginOwner().getSimpleName()+"#"+c.getOrigin().getName()).distinct().sorted().toList();
        assertEquals(List.of("PublicacionServiceImpl#borrador"),constructores);
        var borrador=metodo("PublicacionServiceImpl","borrador");assertTrue(llama(borrador,"exigirResponsable"));assertTrue(llama(borrador,"guardar"));
        assertFalse(llama(borrador,"exigirPublicabilidad"),"RF022: guardar incompleto no es publicar");
    }
    @Test void exposicionRevalidaLaMismaPublicabilidad(){
        var ejecutar=metodo("PublicacionServiceImpl","ejecutar");assertTrue(llama(ejecutar,"validarActuacion"));assertTrue(llama(ejecutar,"exigirPublicabilidad"));
        var validar=metodo("PublicacionServiceImpl","validarActuacion");assertTrue(llama(validar,"exigirResponsable"));assertTrue(llama(validar,"exigirPublicabilidad"));
        for(String caso:SENSIBLES)assertTrue(llama(metodo("PublicacionServiceImpl",caso),"ejecutar"),caso);
    }
    @Test void puertaDePiezaRevalidaSuInmuebleConElMismoEvaluadorSinPublicar() throws Exception {
        var declaracion=PublicacionServiceImpl.class.getMethod("exigirPublicabilidadDePieza",long.class,long.class,String.class,Actor.class);
        var transaccion=declaracion.getAnnotation(Transactional.class);assertNotNull(transaccion);assertTrue(transaccion.readOnly());
        var puerta=metodo("PublicacionServiceImpl","exigirPublicabilidadDePieza");
        assertTrue(llama(puerta,"evaluar"));assertTrue(llama(metodo("PublicacionServiceImpl","evaluarPublicabilidad"),"evaluar"));
        assertTrue(puerta.getConstructorCallsFromSelf().stream().anyMatch(c->c.getTargetOwner().getSimpleName().equals("ReglaNegocioException")),
                "La puerta no puede ignorar una insuficiencia de publicación.");
        var evaluar=metodo("PublicacionServiceImpl","evaluar");
        for(String guarda:List.of("exigirOperador","leer","elegir","faltantesDePropiedadParaPublicar",
                "faltantesDeEncargoParaPublicar","consultarVigencia"))assertTrue(llama(evaluar,guarda),guarda);
        var seleccionar=metodo("SeleccionInmueblePieza","seleccionar");
        assertTrue(llama(seleccionar,"consultarComposicion"));assertTrue(llama(seleccionar,"exigirLecturaHistorica"));
        var lectura=metodo("SeleccionInmueblePieza","exigirLecturaHistorica");
        assertTrue(llama(lectura,"puedeLeer"));assertTrue(llama(lectura,"buscarParaLectura"));
        for(var metodo:List.of(puerta,evaluar,seleccionar,lectura))for(var llamada:metodo.getMethodCallsFromSelf()){
            assertFalse(Set.of("setEstado","guardar","save","saveAndFlush","componer","invocar","enviar").contains(llamada.getTarget().getName()),llamada.toString());
            assertFalse(llamada.getTargetOwner().getPackageName().startsWith("com.controllocal.web.almacen"),llamada.toString());
        }
    }
    @Test void puertaDePiezaSoloLaConsumeLaDifusionContextualDePiezas(){
        var llamadores=CLASES.stream().flatMap(c->c.getMethodCallsFromSelf().stream())
                .filter(c->c.getTargetOwner().isEquivalentTo(PublicacionService.class)&&c.getTarget().getName().equals("exigirPublicabilidadDePieza"))
                .map(c->c.getOriginOwner().getSimpleName()+"#"+c.getOrigin().getName()).distinct().sorted().toList();
        assertEquals(List.of("PiezasComercialesServiceImpl#descargar","PiezasComercialesServiceImpl#generar",
                "PiezasComercialesServiceImpl#restaurarArchivo","PiezasComercialesServiceImpl#validarEntrega"),llamadores);
    }
    @Test void cambiosDeEstadoSoloEnCasosInventariados(){
        var escritores=CLASES.stream().flatMap(c->c.getMethodCallsFromSelf().stream())
                .filter(c->c.getTargetOwner().isEquivalentTo(Publicacion.class)&&c.getTarget().getName().equals("setEstado"))
                .map(c->c.getOriginOwner().getSimpleName()+"#"+c.getOrigin().getName()).distinct().sorted().toList();
        assertEquals(List.of("HistoriadorPublicacion#cerrarPorHito","PublicacionServiceImpl#borrador","PublicacionServiceImpl#ejecutar"),escritores);
    }
    @Test void cierrePorHitoSoloProvieneDeLasDosConsecuenciasC5(){
        var llamadores=CLASES.stream().flatMap(c->c.getMethodCallsFromSelf().stream())
                .filter(c->c.getTargetOwner().getSimpleName().equals("HistoriadorPublicacion")&&c.getTarget().getName().equals("cerrarPorHito"))
                .map(c->c.getOriginOwner().getSimpleName()+"#"+c.getOrigin().getName()).distinct().sorted().toList();
        assertEquals(List.of("CierreVentaServiceImpl#retirarDelMercado","ContratoServiceImpl#cerrarComposicionConfirmada"),llamadores);
    }
    @Test void repeticionReautorizaAntesDeDevolverElResultado(){
        var restaurar=metodo("PublicacionServiceImpl","restaurar");assertTrue(llama(restaurar,"leer"));assertTrue(llama(restaurar,"exigirResponsable"));
        assertTrue(llama(metodo("PublicacionServiceImpl","ejecutar"),"bloquearClave"));
        assertTrue(llama(metodo("PublicacionServiceImpl","borrador"),"bloquearClave"));
    }
    @Test void elEscritorDeHistoriaNoAbreUnaPuertaInternaAlternativa(){
        var modificadores=CLASES.stream().flatMap(c->c.getMethodCallsFromSelf().stream())
                .filter(c->c.getTargetOwner().isEquivalentTo(Publicacion.class)&&c.getTarget().getName().startsWith("set"))
                .map(c->c.getOriginOwner().getSimpleName()+"#"+c.getOrigin().getName()).distinct().sorted().toList();
        assertEquals(List.of("HistoriadorPublicacion#cerrarPorHito","PublicacionServiceImpl#aplicar","PublicacionServiceImpl#borrador","PublicacionServiceImpl#ejecutar"),modificadores);
        var historizadores=CLASES.stream().flatMap(c->c.getMethodCallsFromSelf().stream())
                .filter(c->c.getTargetOwner().getSimpleName().equals("HistoriadorPublicacion")&&c.getTarget().getName().equals("guardar"))
                .map(c->c.getOriginOwner().getSimpleName()+"#"+c.getOrigin().getName()).distinct().sorted().toList();
        assertEquals(List.of("HistoriadorPublicacion#cerrarPorHito","PublicacionServiceImpl#borrador","PublicacionServiceImpl#ejecutar"),historizadores);
    }
    private static JavaMethod metodo(String clase,String nombre){return CLASES.stream().filter(c->c.getSimpleName().equals(clase)).findFirst().orElseThrow()
            .getMethods().stream().filter(m->m.getName().equals(nombre)).findFirst().orElseThrow();}
    private static boolean llama(JavaMethod m,String nombre){return m.getMethodCallsFromSelf().stream().anyMatch(c->c.getTarget().getName().equals(nombre));}
}
