package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.almacen.AlmacenDisco;
import com.controllocal.web.controlador.DocumentosController;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A04/CAT-E32: PostgreSQL real, vínculos versionados y revocación durante I/O. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AccesoArchivosIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired JdbcTemplate jdbc;
    @Autowired AccesoArchivoService acceso;
    @Autowired FotoPropiedadService fotos;
    @Autowired CaptacionService encargos;
    @Autowired LocalComercialService locales;
    @Autowired SolicitudAlquilerRepository solicitudes;
    @Autowired FotoPropiedadRepository fotosRepo;
    @Autowired EntityManagerFactory emf;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired PlatformTransactionManager gestorTx;
    @Autowired ApplicationContext contextoAplicacion;
    @Autowired com.controllocal.service.documentos.SeleccionFotosContextualesService seleccionFotos;
    @TempDir Path disco;
    private final List<Long> documentosCreados = new ArrayList<>();
    private final List<Long> fotosCreadas = new ArrayList<>();
    private Actor agente;
    private long solicitud;
    private org.springframework.security.core.Authentication principalAnteriorGaleria;
    private boolean principalGaleriaInstalado;
    private static long numero(Map<String,Object> fila, String campo) { return ((Number)fila.get(campo)).longValue(); }

    @BeforeEach void expedienteConResponsableReal() {
        var fila = jdbc.queryForMap("""
                SELECT s.id_solicitud,s.organizacion_id,s.id_rol_agente,r.id_persona
                FROM solicitud_alquiler s JOIN persona_rol r ON r.id_persona_rol=s.id_rol_agente
                ORDER BY s.id_solicitud LIMIT 1
                """);
        solicitud = numero(fila,"id_solicitud");
        agente = new Actor(numero(fila,"organizacion_id"),numero(fila,"id_persona"),numero(fila,"id_rol_agente"),Actor.AGENTE);
    }

    @AfterEach void retirarSoloFilasDeEsteMontaje() {
        if(principalGaleriaInstalado){
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(principalAnteriorGaleria);
            principalGaleriaInstalado=false;
        }
        for (long id : documentosCreados) jdbc.update("DELETE FROM documento_solicitud WHERE id_documento=?",id);
        for (long id : fotosCreadas) {
            jdbc.update("DELETE FROM historial_estado WHERE entidad_tipo='FOTO_PROPIEDAD' AND id_entidad=?",id);
            jdbc.update("DELETE FROM foto_propiedad WHERE id_foto=?",id);
        }
    }

    private record Documento(long id, UUID ref, String clave) { }
    private Documento documento(String clave) {
        var f = jdbc.queryForMap("""
                INSERT INTO documento_solicitud(organizacion_id,id_solicitud,id_tipo_documento_requerido,
                        nombre_archivo,ruta_archivo,estado,resultado_revision)
                VALUES (?,?,(SELECT min(id_tipo_documento_requerido) FROM tipo_documento_requerido),
                        'C1-privado.pdf',?,'R','P') RETURNING id_documento,archivo_ref
                """,agente.idOrganizacion(),solicitud,clave);
        long id = numero(f,"id_documento"); documentosCreados.add(id);
        return new Documento(id,(UUID)f.get("archivo_ref"),clave);
    }

    @Test void vinculoPersistidoResuelveExpedienteYConservaClavePrivadaSinAceptarlaComoPermiso() {
        var doc = documento("c1/"+UUID.randomUUID()+".pdf");
        var autorizado = acceso.resolver(doc.ref().toString(),agente);
        assertEquals("DOCUMENTO_SOLICITUD",autorizado.tipo());
        assertEquals(solicitud,autorizado.idRecurso());
        assertEquals("CONSULTA_EXPEDIENTE",autorizado.finalidad());
        assertEquals(doc.clave(),autorizado.claveProveedor());
        assertThrows(ReglaNegocioException.class,()->acceso.resolver(doc.clave(),agente));
        assertThrows(NoEncontradoException.class,()->acceso.resolver(doc.ref().toString(),
                new Actor(agente.idOrganizacion()+100000,agente.idPersona(),agente.idRolOperativo(),Actor.AGENTE)));
        assertEquals(doc.clave(),jdbc.queryForObject("SELECT ruta_archivo FROM documento_solicitud WHERE id_documento=?",String.class,doc.id()));
    }

    @Test void estarEnElMismoTenantNoAbreExpedienteYAdministrarTampoco() {
        var doc = documento("c1/"+UUID.randomUUID()+".pdf");
        assertThrows(AccesoNoAutorizadoException.class,()->acceso.resolver(doc.ref().toString(),
                new Actor(agente.idOrganizacion(),agente.idPersona(),agente.idRolOperativo()+100000,Actor.AGENTE)));
        assertThrows(AccesoNoAutorizadoException.class,()->acceso.resolver(doc.ref().toString(),
                new Actor(agente.idOrganizacion(),agente.idPersona(),agente.idRolOperativo(),Actor.TENANT_ADMIN)));
    }

    @Test void cambiarElBinarioRotaSuReferenciaYNoModificaLaClaveAnteriorEnElAlmacen() {
        var almacen = new AlmacenDisco(disco.toString());
        String claveOriginal = "tenant/"+agente.idOrganizacion()+"/test-c1/"+UUID.randomUUID()+"-original.pdf";
        almacen.guardarEnClave(claveOriginal,new byte[]{1,2},"application/pdf");
        var doc = documento(claveOriginal);
        jdbc.update("UPDATE documento_solicitud SET ruta_archivo=? WHERE id_documento=?","c1/segundo.pdf",doc.id());
        UUID nueva = jdbc.queryForObject("SELECT archivo_ref FROM documento_solicitud WHERE id_documento=?",UUID.class,doc.id());
        assertNotEquals(doc.ref(),nueva);
        assertThrows(NoEncontradoException.class,()->acceso.resolver(doc.ref().toString(),agente));
        assertEquals("c1/segundo.pdf",acceso.resolver(nueva.toString(),agente).claveProveedor());
        assertArrayEquals(new byte[]{1,2},almacen.abrir(claveOriginal).orElseThrow().contenido());
    }

    @Test void perfilPropioSeResuelvePorPersonaLocalYUnaAmbiguedadNoEligeElPrimerPermitido() {
        var anterior = jdbc.queryForMap("SELECT foto_clave,foto_ref FROM persona WHERE organizacion_id=? AND id_persona=?",
                agente.idOrganizacion(),agente.idPersona());
        try {
            jdbc.update("UPDATE persona SET foto_clave=? WHERE organizacion_id=? AND id_persona=?",
                    "c1/perfil-"+UUID.randomUUID()+".png",agente.idOrganizacion(),agente.idPersona());
            UUID ref = jdbc.queryForObject("SELECT foto_ref FROM persona WHERE organizacion_id=? AND id_persona=?",
                    UUID.class,agente.idOrganizacion(),agente.idPersona());
            assertEquals("PERFIL_PROPIO",acceso.resolver(ref.toString(),agente).finalidad());
            assertThrows(NoEncontradoException.class,()->acceso.resolver(ref.toString(),
                    new Actor(agente.idOrganizacion(),agente.idPersona()+100000,agente.idRolOperativo(),Actor.AGENTE)));
            var doc = documento("c1/ambiguo.pdf");
            jdbc.update("UPDATE documento_solicitud SET archivo_ref=? WHERE id_documento=?",ref,doc.id());
            assertThrows(ConflictoException.class,()->acceso.resolver(ref.toString(),agente));
        } finally {
            jdbc.update("UPDATE persona SET foto_clave=?,foto_ref=? WHERE organizacion_id=? AND id_persona=?",
                    anterior.get("foto_clave"),anterior.get("foto_ref"),agente.idOrganizacion(),agente.idPersona());
        }
    }

    @Test void fotoUsaProspeccionAutorizadaSinExigirPermisoDeEdicionFisica() {
        var f = jdbc.queryForMap("""
                SELECT p.id_propiedad,p.organizacion_id,p.id_rol_agente,r.id_persona
                FROM prospeccion p JOIN persona_rol r ON r.id_persona_rol=p.id_rol_agente
                WHERE p.estado NOT IN ('T','D') ORDER BY p.id_prospeccion LIMIT 1
                """);
        var actor = new Actor(numero(f,"organizacion_id"),numero(f,"id_persona"),numero(f,"id_rol_agente"),Actor.AGENTE);
        long propiedad = numero(f,"id_propiedad");
        var foto = jdbc.queryForMap("""
                INSERT INTO foto_propiedad(organizacion_id,id_propiedad,clave,nombre_archivo,orden)
                VALUES (?,?,'c1/foto-privada.png','foto.png',999) RETURNING id_foto,archivo_ref
                """,actor.idOrganizacion(),propiedad);
        fotosCreadas.add(numero(foto,"id_foto"));
        String ref = foto.get("archivo_ref").toString();
        assertEquals("CONSULTA_GALERIA",acceso.resolver(ref,actor).finalidad());
        assertTrue(fotos.listar(propiedad,actor).stream().anyMatch(p->ref.equals(p.clave())));
        var ajeno = new Actor(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo()+100000,Actor.AGENTE);
        assertThrows(NoEncontradoException.class,()->acceso.resolver(ref,ajeno));
        assertThrows(NoEncontradoException.class,()->fotos.listar(propiedad,ajeno));
    }

    @Test void seleccionContextualRevalidaGaleriaRealSinPublicarNiReordenarSusOriginales() {
        var fila=jdbc.queryForMap("""
                SELECT p.id_propiedad,p.organizacion_id,p.id_rol_agente,r.id_persona
                FROM prospeccion p JOIN persona_rol r ON r.id_persona_rol=p.id_rol_agente
                WHERE p.estado NOT IN ('T','D') ORDER BY p.id_prospeccion LIMIT 1
                """);
        var actor=new Actor(numero(fila,"organizacion_id"),numero(fila,"id_persona"),numero(fila,"id_rol_agente"),Actor.AGENTE);
        long propiedad=numero(fila,"id_propiedad");
        var foto=jdbc.queryForMap("""
                INSERT INTO foto_propiedad(organizacion_id,id_propiedad,clave,nombre_archivo,orden)
                VALUES(?,?,?,'seleccion-contextual.png',999) RETURNING id_foto,archivo_ref
                """,actor.idOrganizacion(),propiedad,"c2/seleccion-"+UUID.randomUUID()+".png");
        long idFoto=numero(foto,"id_foto");fotosCreadas.add(idFoto);UUID ref=(UUID)foto.get("archivo_ref");
        var observada=seleccionFotos.observar(propiedad,actor);
        var antes=jdbc.queryForList("select id_foto,archivo_ref,clave,orden,estado from foto_propiedad where id_propiedad=? order by id_foto",propiedad);
        var solicitud=new com.controllocal.service.documentos.SeleccionFotosContextualesService.Solicitud(
                List.of(ref),com.controllocal.service.documentos.SeleccionFotosContextualesService.Finalidad.DIFUSION,observada.versionObservada());
        var seleccion=seleccionFotos.seleccionar(propiedad,solicitud,actor);
        assertEquals(List.of(ref),seleccion.fotosVersionadas());
        assertEquals(antes,jdbc.queryForList("select id_foto,archivo_ref,clave,orden,estado from foto_propiedad where id_propiedad=? order by id_foto",propiedad));
        assertEquals("CONSULTA_GALERIA",acceso.resolver(ref.toString(),actor).finalidad(),"Seleccionar para difusión no concede una autorización de salida");
        assertThrows(NoEncontradoException.class,()->seleccionFotos.seleccionar(propiedad,solicitud,
                new Actor(actor.idOrganizacion()+100000,actor.idPersona(),actor.idRolOperativo(),Actor.AGENTE)));
        assertThrows(AccesoNoAutorizadoException.class,()->seleccionFotos.observar(propiedad,
                new Actor(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),Actor.TENANT_ADMIN)));
        jdbc.update("update foto_propiedad set orden=1000 where id_foto=?",idFoto);
        assertThrows(ConflictoException.class,()->seleccionFotos.seleccionar(propiedad,solicitud,actor));
    }

    @Test void portadasIndividualesDeLocalYEncargoPublicanUuidEnLugarDeClaveProveedor() {
        var fila=jdbc.queryForMap("""
            SELECT c.id_captacion,c.id_propiedad,c.organizacion_id,c.id_rol_agente,r.id_persona
            FROM captacion c JOIN persona_rol r ON r.id_persona_rol=c.id_rol_agente
            WHERE NOT EXISTS(SELECT 1 FROM foto_propiedad f
                WHERE f.organizacion_id=c.organizacion_id AND f.id_propiedad=c.id_propiedad AND f.estado='A')
            ORDER BY c.id_captacion LIMIT 1
            """);
        long inmueble=numero(fila,"id_propiedad");
        var actor=new Actor(numero(fila,"organizacion_id"),numero(fila,"id_persona"),numero(fila,"id_rol_agente"),Actor.AGENTE);
        String clavePortada="tenant/"+actor.idOrganizacion()+"/test-c1/"+UUID.randomUUID()+"-portada.png";
        new AlmacenDisco(disco.toString()).guardarEnClave(clavePortada,new byte[]{(byte)0x89,0x50,0x4e,0x47},"image/png");
        var insertada=jdbc.queryForMap("""
            INSERT INTO foto_propiedad(organizacion_id,id_propiedad,clave,nombre_archivo,orden)
            VALUES(?,?,?,'portada.png',1) RETURNING id_foto,archivo_ref
            """,actor.idOrganizacion(),inmueble,clavePortada);
        fotosCreadas.add(numero(insertada,"id_foto"));String referencia=insertada.get("archivo_ref").toString();
        assertNotEquals(clavePortada,referencia);assertEquals(UUID.fromString(referencia).toString(),referencia);
        assertEquals(referencia,encargos.obtener(numero(fila,"id_captacion"),actor).fotoPortadaClave());
        assertEquals(referencia,locales.buscarPorId(inmueble,actor).orElseThrow().fotoPortadaClave());
        assertEquals(clavePortada,acceso.resolver(referencia,actor).claveProveedor());
        assertThrows(ReglaNegocioException.class,()->acceso.resolver(clavePortada,actor));
    }

    @Test void referenciaColisionadaConPerfilAjenoFallaAntesDeAbrirDocumentoLegible() {
        var otro = jdbc.queryForMap("""
                SELECT id_persona,foto_clave,foto_ref FROM persona
                WHERE organizacion_id=? AND id_persona<>? ORDER BY id_persona LIMIT 1
                """,agente.idOrganizacion(),agente.idPersona());
        long idOtro = numero(otro,"id_persona");
        try {
            jdbc.update("UPDATE persona SET foto_clave=? WHERE organizacion_id=? AND id_persona=?",
                    "c1/ajeno-"+UUID.randomUUID()+".png",agente.idOrganizacion(),idOtro);
            UUID ref = jdbc.queryForObject("SELECT foto_ref FROM persona WHERE id_persona=?",UUID.class,idOtro);
            assertThrows(NoEncontradoException.class,()->acceso.resolver(ref.toString(),agente));
            var doc = documento("c1/colision-ajena.pdf");
            jdbc.update("UPDATE documento_solicitud SET archivo_ref=? WHERE id_documento=?",ref,doc.id());
            var almacen = mock(com.controllocal.web.almacen.AlmacenDocumentos.class);
            var sesiones = mock(RevalidacionDeSesion.class);
            when(sesiones.exigirVigente()).thenReturn(agente);
            var controller = new DocumentosController(almacen,acceso,sesiones);
            assertThrows(ConflictoException.class,()->controller.contenido(ref.toString()));
            verifyNoInteractions(almacen);
        } finally {
            jdbc.update("UPDATE persona SET foto_clave=?,foto_ref=? WHERE organizacion_id=? AND id_persona=?",
                    otro.get("foto_clave"),otro.get("foto_ref"),agente.idOrganizacion(),idOtro);
        }
    }

    @Test void reasignacionCometidaDuranteLecturaRealDeniegaEntregaAunqueOeivConserveLaEntidadAnterior() throws Exception {
        var otros = jdbc.queryForList("""
                SELECT id_persona_rol FROM detalle_agente WHERE organizacion_id=? AND id_persona_rol<>?
                ORDER BY id_persona_rol LIMIT 1
                """,Long.class,agente.idOrganizacion(),agente.idRolOperativo());
        assertFalse(otros.isEmpty(),"El fixture de concurrencia exige otro agente real del mismo tenant");
        var almacen = spy(new AlmacenDisco(disco.toString()));
        String clavePrivada = "tenant/"+agente.idOrganizacion()+"/test-c1/"+UUID.randomUUID()+"-privado.pdf";
        almacen.guardarEnClave(clavePrivada,new byte[]{5,6,7},"application/pdf");
        var doc = documento(clavePrivada);
        var cargado = new CountDownLatch(1); var continuar = new CountDownLatch(1);
        doAnswer(invocacion->{
            var resultado = invocacion.callRealMethod();
            cargado.countDown();
            if (!continuar.await(30,TimeUnit.SECONDS)) throw new IllegalStateException("No llegó el relevo de la prueba");
            return resultado;
        }).when(almacen).abrir(clavePrivada);
        var sesiones = mock(RevalidacionDeSesion.class);
        when(sesiones.exigirVigente()).thenReturn(agente);
        var controller = new DocumentosController(almacen,acceso,sesiones);
        var ejecutor = Executors.newSingleThreadExecutor();
        Future<?> descarga = null;
        try {
            descarga = ejecutor.submit(()->{
                var em = emf.createEntityManager();
                TransactionSynchronizationManager.bindResource(emf,new EntityManagerHolder(em));
                try {
                    var cacheada = solicitudes.buscarFicha(agente.idOrganizacion(),solicitud).orElseThrow();
                    assertEquals(agente.idRolOperativo(),cacheada.getAgente().getId());
                    return controller.contenido(doc.ref().toString());
                } finally {
                    TransactionSynchronizationManager.unbindResource(emf); em.close();
                }
            });
            assertTrue(cargado.await(30,TimeUnit.SECONDS),"La descarga llegó al proveedor tras autorizar");
            jdbc.update("UPDATE solicitud_alquiler SET id_rol_agente=? WHERE id_solicitud=?",otros.getFirst(),solicitud);
            continuar.countDown();
            Future<?> resultado = descarga;
            var error = assertThrows(ExecutionException.class,()->descargaConLimite(resultado));
            assertInstanceOf(AccesoNoAutorizadoException.class,error.getCause());
            assertArrayEquals(new byte[]{5,6,7},new AlmacenDisco(disco.toString()).abrir(clavePrivada).orElseThrow().contenido());
        } finally {
            continuar.countDown();
            if (descarga != null && !descarga.isDone()) descarga.cancel(true);
            ejecutor.shutdownNow(); ejecutor.awaitTermination(10,TimeUnit.SECONDS);
            jdbc.update("UPDATE solicitud_alquiler SET id_rol_agente=? WHERE id_solicitud=?",agente.idRolOperativo(),solicitud);
        }
    }

    private static Object descargaConLimite(Future<?> descarga) throws Exception { return descarga.get(30,TimeUnit.SECONDS); }

    private long inmueblePropioConProspeccion() {
        // Estos cuatro casos necesitan contexto operativo C4 actual, no un actor tomado de un expediente antiguo.
        var origenes=new com.controllocal.integracion.soporte.OrigenesDePrueba(jdbc,gestorTx,
                contextoAplicacion.getBean(com.controllocal.service.captura.MotorDeCaptura.class),
                contextoAplicacion.getBean(com.controllocal.service.ContextosService.class));
        agente=origenes.empresa().agente();
        long id = propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(null,null,null,
                "CASA","VIVIENDA","Foto original C1",
                new PropiedadUniversalService.Ubicacion("Av. C1 "+UUID.randomUUID(),"Miraflores",null,null,null,null,null,null,null),
                List.of(),List.of(new PropiedadUniversalService.ValorAtributo("metraje_total","120"),
                        new PropiedadUniversalService.ValorAtributo("area_terreno","150"),
                        new PropiedadUniversalService.ValorAtributo("metraje_construido","90"),
                        new PropiedadUniversalService.ValorAtributo("dormitorios","3"),
                        new PropiedadUniversalService.ValorAtributo("banos","2")),List.of(),null),agente).idPropiedad();
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(id,"Fotografía aportada en prospección", null),agente);
        return id;
    }

    private FotoPropiedadService.FotoDePropiedad fotoOriginal(long propiedad, AlmacenDisco almacen) {
        String claveFoto = "tenant/"+agente.idOrganizacion()+"/test-c1/"+UUID.randomUUID()+"-original.png";
        almacen.guardarEnClave(claveFoto,new byte[]{(byte)0x89,0x50,0x4e,0x47},"image/png");
        var foto = fotos.agregar(propiedad,claveFoto,"original.png",1,agente);
        fotosCreadas.add(foto.id());
        return foto;
    }

    private int retiradas(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM historial_estado WHERE entidad_tipo='FOTO_PROPIEDAD' AND id_entidad=?",
                Integer.class,id);
    }

    /** Dependencias reales del nuevo adaptador; estos dos casos sólo retiran originales ya existentes. */
    private com.controllocal.web.almacen.GaleriaDeFotos galeriaEnDisco(AlmacenDisco almacen){
        var contextos=contextoAplicacion.getBean(com.controllocal.service.ContextosService.class);
        var sid=com.controllocal.integracion.soporte.SesionesDePrueba.abrir(jdbc,contextos,agente);
        principalAnteriorGaleria=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(sid,null,List.of()));
        principalGaleriaInstalado=true;
        var sesiones=contextoAplicacion.getBean(RevalidacionDeSesion.class);
        assertEquals(agente,sesiones.exigirVigente());
        var salidas=new com.controllocal.web.almacen.SalidaBinariaPersistente(almacen,
                contextoAplicacion.getBean(com.controllocal.service.archivo.SeguimientoSalidaBinaria.class));
        var cuota=new com.controllocal.web.almacen.CuotaArchivosB0(
                contextoAplicacion.getBean(com.controllocal.service.planes.UsoAlmacenamientoB0.class),
                contextoAplicacion.getBean(com.controllocal.service.planes.ConfirmacionAlmacenamientoB0.class),almacen);
        return new com.controllocal.web.almacen.GaleriaDeFotos(fotos,almacen,sesiones,salidas,cuota,
                contextoAplicacion.getBean(com.controllocal.service.planes.ClasificadorArchivosExistentesB0.class),
                contextoAplicacion.getBean(com.controllocal.service.documentos.EvaluacionTecnicaFoto.class),gestorTx);
    }

    @Test void retirarDeGaleriaConservaOriginalReferenciaFechaAutorYTransicionSinEntregarloComoActivo() {
        long propiedad = inmueblePropioConProspeccion();
        var almacen = spy(new AlmacenDisco(disco.toString()));
        var foto = fotoOriginal(propiedad,almacen);
        var antes = jdbc.queryForMap("SELECT * FROM foto_propiedad WHERE id_foto=?",foto.id());
        var galeria = galeriaEnDisco(almacen);
        galeria.eliminar(foto.id(),agente);
        var despues = jdbc.queryForMap("SELECT * FROM foto_propiedad WHERE id_foto=?",foto.id());
        for (String c : List.of("id_propiedad","clave","archivo_ref","fecha_registro","id_persona_registro","id_rol_registro","rol_registro"))
            assertEquals(antes.get(c),despues.get(c),c);
        assertEquals(agente.idPersona(),numero(despues,"id_persona_registro"));
        assertEquals(agente.idRolOperativo(),numero(despues,"id_rol_registro"));
        assertEquals("I",despues.get("estado")); assertNotNull(despues.get("fecha_retiro"));
        assertEquals(1,retiradas(foto.id()));
        assertEquals(agente.idPersona(),jdbc.queryForObject("SELECT id_actor FROM historial_estado WHERE entidad_tipo='FOTO_PROPIEDAD' AND id_entidad=?",
                Long.class,foto.id()));
        assertTrue(fotos.listar(propiedad,agente).isEmpty());
        assertTrue(fotosRepo.portadas(List.of(propiedad)).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE id_propiedad=? AND estado='A'",Integer.class,propiedad));
        assertThrows(NoEncontradoException.class,()->acceso.resolver(foto.clave(),agente));
        assertFalse(fotos.eliminar(foto.id(),agente)); assertEquals(1,retiradas(foto.id()));
        var nueva = fotoOriginal(propiedad,almacen);
        assertNotEquals(foto.clave(),nueva.clave());
        assertEquals(nueva.clave(),fotosRepo.portadas(List.of(propiedad)).getFirst().getClave());
        assertEquals(List.of(nueva.id()),fotos.reordenar(propiedad,List.of(nueva.id()),agente).stream().map(FotoPropiedadService.FotoDePropiedad::id).toList());
        verify(almacen,never()).eliminar(anyString());
        assertArrayEquals(new byte[]{(byte)0x89,0x50,0x4e,0x47},almacen.abrir((String)antes.get("clave")).orElseThrow().contenido());
    }

    @Test void originalNoSeSobrescribeNiReubicaYRetiradaNoSeReactivaPorSql() {
        long propiedad = inmueblePropioConProspeccion();
        var foto = fotoOriginal(propiedad,new AlmacenDisco(disco.toString()));
        for (String asignacion : List.of("clave='otra.png'","archivo_ref=gen_random_uuid()","fecha_registro=now()+interval '1 day'",
                "id_propiedad=(SELECT min(id_propiedad) FROM propiedad WHERE id_propiedad<>"+propiedad+")")) {
            var error = assertThrows(DataAccessException.class,()->jdbc.update("UPDATE foto_propiedad SET "+asignacion+" WHERE id_foto=?",foto.id()));
            assertTrue(error.getMostSpecificCause().getMessage().contains("original de foto es inmutable"));
        }
        assertEquals(foto.clave(),jdbc.queryForObject("SELECT archivo_ref::text FROM foto_propiedad WHERE id_foto=?",String.class,foto.id()));
        assertTrue(fotos.eliminar(foto.id(),agente));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE foto_propiedad SET estado='A',fecha_retiro=null WHERE id_foto=?",foto.id()));
        assertEquals("I",jdbc.queryForObject("SELECT estado FROM foto_propiedad WHERE id_foto=?",String.class,foto.id()));
    }

    @Test void rollbackConservaGaleriaOriginalYNoDejaTransicionDeRetirada() {
        long propiedad = inmueblePropioConProspeccion();
        var almacen = spy(new AlmacenDisco(disco.toString()));
        var foto = fotoOriginal(propiedad,almacen);
        var galeria = galeriaEnDisco(almacen);
        var tx = new TransactionTemplate(gestorTx);
        assertThrows(IllegalStateException.class,()->tx.execute(estado->{
            galeria.eliminar(foto.id(),agente);
            throw new IllegalStateException("Rollback solicitado por la prueba");
        }));
        assertEquals(0,retiradas(foto.id()));
        assertEquals("A",jdbc.queryForObject("SELECT estado FROM foto_propiedad WHERE id_foto=?",String.class,foto.id()));
        assertEquals(foto.clave(),acceso.resolver(foto.clave(),agente).referencia().toString());
        assertEquals(1,fotos.listar(propiedad,agente).size());
        verify(almacen,never()).eliminar(anyString());
    }

    @Test void retiradasConcurrentesGeneranUnaSolaTransicionYConservanElOriginal() throws Exception {
        long propiedad = inmueblePropioConProspeccion();
        var almacen = new AlmacenDisco(disco.toString());
        var foto = fotoOriginal(propiedad,almacen);
        String clave = jdbc.queryForObject("SELECT clave FROM foto_propiedad WHERE id_foto=?",String.class,foto.id());
        var inicio = new CyclicBarrier(2); var ejecutor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> retirar = ()->{inicio.await(30,TimeUnit.SECONDS);return fotos.eliminar(foto.id(),agente);};
            Future<Boolean> a=ejecutor.submit(retirar), b=ejecutor.submit(retirar);
            assertEquals(1,(a.get(30,TimeUnit.SECONDS)?1:0)+(b.get(30,TimeUnit.SECONDS)?1:0));
            assertEquals(1,retiradas(foto.id()));
            assertTrue(almacen.abrir(clave).isPresent());
        } finally { ejecutor.shutdownNow(); }
    }
}
