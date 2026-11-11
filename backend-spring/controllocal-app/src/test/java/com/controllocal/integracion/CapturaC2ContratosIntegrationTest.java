package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.MotorDeCaptura.Avance;
import com.controllocal.service.captura.MotorDeCaptura.Respuesta;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaInmueble.Aplicabilidad;
import com.controllocal.service.captura.SuficienciaInmueble.Contexto;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** CAT-E08/E11/E39, MAT01/02 y AC047–053: hechos, autoridad y CAS sobre PostgreSQL. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.hikari.maximum-pool-size=10")
class CapturaC2ContratosIntegrationTest {
    private static final Procedencia PANTALLA = Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }
    @Autowired MotorDeCaptura captura;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ObjectMapper json;
    private Actor actor;

    @BeforeEach void contextoTestPropio() {
        actor = new TransactionTemplate(gestor).execute(tx -> {
            long org = organizacion("INDIVIDUAL", "A");
            return profesional(org, Actor.AGENTE, false);
        });
    }

    @Test void departamentoEnSurcoDevuelvePreparacionUtilSinInventarPropiedadNiEncargo() {
        var estado = abrir(actor, respuestas("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco"), null);
        assertFalse(estado.listoParaEjecutar());
        assertNull(estado.idEntidad());
        var contacto = estado.retorno().grupos().stream().filter(g -> "CU01".equals(g.codigo())).findFirst().orElseThrow();
        assertEquals("PREPARACION_CONTACTO", contacto.resultado());
        assertEquals("DEPARTAMENTO", contacto.datos().get("tipoPropiedad"));
        assertEquals("Surco", contacto.datos().get("distrito"));
        assertTrue(contacto.acciones().contains("PREPARAR_PRIMER_CONTACTO"));
        assertEquals("SIN_REFERENCIAS_ELEGIBLES", estado.retorno().referenciaPrecio().estado());
        assertEquals(0, contar("propiedad", actor));
        assertEquals(0, contar("captacion", actor));
        assertThrows(ReglaNegocioException.class,
                () -> captura.ejecutar(estado.idBorrador(), estado.version(), clave(), PANTALLA, actor));
        assertEquals(0, contar("propiedad", actor));
        assertEquals(estado.conocido(), captura.consultar(estado.idBorrador(), actor).conocido());
    }

    /** RF011/AC051: el listado no concede acceso por compartir organización. */
    @Test void enCursoListaSoloCapturasPropiasActivasYConservaLasTerminadasFueraDelListado() {
        var empresa = empresaConDosAgentes();
        Actor titular = empresa.primero(), colega = empresa.segundo(), otroTenant = actor;
        assertEquals(List.of(), captura.enCurso(titular));
        assertEquals(List.of(), captura.enCurso(colega));
        assertEquals(List.of(), captura.enCurso(otroTenant));

        var propia = abrir(titular, respuestas("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco"), null);
        var ajena = abrir(colega, respuestas("tipoPropiedad", "OFICINA", "distrito", "Lince"), null);
        var externa = abrir(otroTenant, respuestas("tipoPropiedad", "TERRENO", "distrito", "Callao"), null);
        var aDescartar = abrir(titular, respuestas("tipoPropiedad", "CASA", "distrito", "Surco"), null);
        var aEjecutar = abrir(titular, respuestas("tipoPropiedad", "TERRENO", "distrito", "Surco",
                "referenciaInterna", "Predio TEST listado propio"), null);
        assertEquals(3, captura.enCurso(titular).size());

        var descartada = captura.descartar(aDescartar.idBorrador(), aDescartar.version(), PANTALLA, titular);
        assertEquals("D", descartada.estado());
        assertTrue(aEjecutar.listoParaEjecutar());
        var alta = captura.ejecutar(aEjecutar.idBorrador(), aEjecutar.version(), clave(), PANTALLA, titular);
        assertTrue(alta.idsEncargos().isEmpty());

        var listado = captura.enCurso(titular);
        assertEquals(List.of(propia.idBorrador()), listado.stream().map(MotorDeCaptura.EstadoCaptura::idBorrador).toList());
        assertEquals("E", listado.getFirst().estado());
        assertEquals(propia.conocido(), listado.getFirst().conocido());
        assertEquals(List.of(ajena.idBorrador()), captura.enCurso(colega).stream().map(MotorDeCaptura.EstadoCaptura::idBorrador).toList());
        assertEquals(List.of(externa.idBorrador()), captura.enCurso(otroTenant).stream().map(MotorDeCaptura.EstadoCaptura::idBorrador).toList());
        assertThrows(NoEncontradoException.class, () -> captura.consultar(ajena.idBorrador(), titular));
        assertThrows(NoEncontradoException.class, () -> captura.consultar(externa.idBorrador(), titular));
        assertEquals("D", captura.consultar(aDescartar.idBorrador(), titular).estado());
        var ejecutada = captura.consultar(aEjecutar.idBorrador(), titular);
        assertEquals("J", ejecutada.estado());
        assertEquals(alta.idPropiedad(), ejecutada.idEntidad());
    }

    @Test void referenciaDeTerrenoSinSuperficieNiZonificacionIdentificaElFisicoSinEncargo() {
        var estado = abrir(actor, respuestas("tipoPropiedad", "TERRENO", "distrito", "Surco",
                "referenciaInterna", "Predio TEST lote15"), null);
        assertTrue(estado.suficiencia().suficiente());
        assertTrue(estado.listoParaEjecutar());
        assertFalse(estado.conocido().containsKey("metraje_total"));
        assertFalse(estado.conocido().containsKey("zonificacion"));
        var alta = captura.ejecutar(estado.idBorrador(), estado.version(), clave(), PANTALLA, actor);
        assertTrue(alta.idsEncargos().isEmpty());
        assertEquals(1, contar("propiedad", actor));
        assertEquals(0, contar("captacion", actor));
        assertNull(jdbc.queryForObject("select metraje from propiedad where id_propiedad=?", java.math.BigDecimal.class, alta.idPropiedad()));
        assertNull(jdbc.queryForObject("select uso from propiedad where id_propiedad=?", String.class, alta.idPropiedad()));
        assertEquals(0, jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='zonificacion'", Integer.class, alta.idPropiedad()));
        assertEquals(0, jdbc.queryForObject("select count(*) from precio_propiedad where id_propiedad=?", Integer.class, alta.idPropiedad()));
        assertTrue(propiedades.consultar(alta.idPropiedad(), actor).encargos().isEmpty());
    }

    @Test void altaDirectaConZonificacionSinCatalogoConservaElPendienteYElRegistroParcial() throws Exception {
        var dato=new PropiedadUniversalService.ValorAtributo("zonificacion","COD-FICTICIO-SIN-CATALOGO",
                null,null,"DECLARADO",null,LocalDate.of(2026,10,28),"DOCUMENTO-FICTICIO-DE-OFERTA");
        var alta=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),PANTALLA,null,
                "TERRENO",null,"Predio de prueba con dato por revisar",
                new PropiedadUniversalService.Ubicacion("Av. Ensayo "+UUID.randomUUID(),"Surco",null,null,null,null,null,null,null),
                null,List.of(dato),List.of(),null),actor);
        assertNotNull(propiedades.consultar(alta.idPropiedad(),actor));
        assertTrue(alta.idsEncargos().isEmpty());
        assertEquals(0,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='zonificacion'",Integer.class,alta.idPropiedad()));
        var metadata=json.readTree(textoPropiedad(alta.idPropiedad(),"metadatos_atributos")).get("zonificacion");
        assertEquals("FALTANTE",metadata.get("estado").asText());
        assertEquals("COD-FICTICIO-SIN-CATALOGO",metadata.get("valorEnRevision").asText());
        assertEquals("JURISDICCION_FALTANTE",metadata.get("incidencia").asText());
        assertEquals("DOCUMENTO-FICTICIO-DE-OFERTA",metadata.get("evidenciaRef").asText());
        assertEquals("2026-10-28",metadata.get("observadoEn").asText());
        assertFalse(metadata.hasNonNull("referenciaCatalogo"));
        assertEquals(0,contar("captacion",actor));
    }

    @ParameterizedTest @ValueSource(strings = {"DEPARTAMENTO", "OFICINA"})
    void unEdificioSinReferenciaDeUnidadNoIdentificaDepartamentoUOficina(String tipo) {
        var estado = abrir(actor, respuestas("tipoPropiedad", tipo, "distrito", "Surco",
                "direccion", "Av. TEST del edificio123", "nombreEdificioGaleria", "Edificio TEST"), null);
        assertFalse(estado.listoParaEjecutar());
        assertTrue(estado.suficiencia().faltantes().stream().anyMatch(f ->
                f.motivo() == SuficienciaInmueble.Motivo.UNIDAD_NO_IDENTIFICADA));
        assertThrows(ReglaNegocioException.class,
                () -> captura.ejecutar(estado.idBorrador(), estado.version(), clave(), PANTALLA, actor));
        assertEquals(0, contar("propiedad", actor));
        assertEquals(0, contar("captacion", actor));
    }

    @Test void materializarCoordenadasDeContextoNoLasBuscaNiEscribeComoAtributosDelCatalogo() throws Exception {
        var entradas = departamentoIdentificado();
        entradas.put("contexto." + SuficienciaInmueble.UNIDAD_DE_EDIFICIO, Respuesta.conocida("APLICA"));
        entradas.put("contexto." + SuficienciaInmueble.MANTENIMIENTO, noAplica("SUSTENTO-TEST-MANTENIMIENTO"));
        var contexto = contexto(Map.of());
        var estado = abrir(actor, entradas, contexto);
        assertEquals(Aplicabilidad.APLICA, estado.contexto().aplicabilidad().get(SuficienciaInmueble.UNIDAD_DE_EDIFICIO));
        assertEquals(Aplicabilidad.NO_APLICA, estado.contexto().aplicabilidad().get(SuficienciaInmueble.MANTENIMIENTO));
        assertEquals(0, jdbc.queryForObject("select count(*) from catalogo_atributo where clave like 'contexto.%' and (organizacion_id is null or organizacion_id=?)", Integer.class, actor.idOrganizacion()));
        var alta = captura.ejecutar(estado.idBorrador(), estado.version(), clave(), PANTALLA, actor);
        assertEquals(0, jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave like 'contexto.%'", Integer.class, alta.idPropiedad()));
        var persistido = json.readTree(jdbc.queryForObject("select contexto_captura from propiedad where id_propiedad=?", String.class, alta.idPropiedad()));
        assertEquals("APLICA", persistido.path("aplicabilidad").path(SuficienciaInmueble.UNIDAD_DE_EDIFICIO).asText());
        assertEquals("NO_APLICA", persistido.path("aplicabilidad").path(SuficienciaInmueble.MANTENIMIENTO).asText());
        var metadata = json.readTree(jdbc.queryForObject("select metadatos_atributos from propiedad where id_propiedad=?", String.class, alta.idPropiedad()));
        assertEquals("SUSTENTO-TEST-MANTENIMIENTO", metadata.path("contexto." + SuficienciaInmueble.MANTENIMIENTO).path("evidenciaRef").asText());
        assertTrue(alta.idsEncargos().isEmpty());
    }

    @Test void cambiarAplicabilidadNoReutilizaElSustentoDeLaDeclaracionAnterior() {
        var entradas = departamentoIdentificado();
        entradas.put("contexto." + SuficienciaInmueble.MANTENIMIENTO, noAplica("SUSTENTO-TEST-ANTERIOR"));
        var inicial = abrir(actor, entradas, contexto(Map.of()));
        var aplica = captura.avanzar(new Avance(null, inicial.idBorrador(), inicial.version(), Map.of(),
                contexto(Map.of(SuficienciaInmueble.MANTENIMIENTO, Aplicabilidad.APLICA))), PANTALLA, actor);
        assertNull(aplica.metadatos().get("contexto." + SuficienciaInmueble.MANTENIMIENTO).evidenciaRef());
        var sinNuevoSustento = captura.avanzar(new Avance(null, inicial.idBorrador(), aplica.version(), Map.of(),
                contexto(Map.of(SuficienciaInmueble.MANTENIMIENTO, Aplicabilidad.NO_APLICA))), PANTALLA, actor);
        assertNull(sinNuevoSustento.metadatos().get("contexto." + SuficienciaInmueble.MANTENIMIENTO).evidenciaRef());
        assertTrue(sinNuevoSustento.suficienciasEncargo().getFirst().faltantes().stream().anyMatch(f ->
                ("contexto." + SuficienciaInmueble.MANTENIMIENTO).equals(f.clave())
                        && f.motivo() == SuficienciaInmueble.Motivo.NO_APLICABLE_SIN_EVIDENCIA));
    }

    @Test void contextoYRespuestaContradictoriosSeRechazanSinAvanceParcial() {
        var inicial = abrir(actor, departamentoIdentificado(), contexto(Map.of()));
        int eventos = eventosCaptura(inicial.idBorrador());
        assertThrows(ReglaNegocioException.class, () -> captura.avanzar(new Avance(null, inicial.idBorrador(), inicial.version(),
                Map.of("contexto." + SuficienciaInmueble.MANTENIMIENTO, noAplica("SUSTENTO-TEST")),
                contexto(Map.of(SuficienciaInmueble.MANTENIMIENTO, Aplicabilidad.APLICA))), PANTALLA, actor));
        var despues = captura.consultar(inicial.idBorrador(), actor);
        assertEquals(inicial.contexto(), despues.contexto());
        assertEquals(inicial.metadatos(), despues.metadatos());
        assertEquals(inicial.version(), despues.version());
        assertEquals(eventos, eventosCaptura(inicial.idBorrador()));
    }

    @Test void edicionOrdinariaConservaElContextoYElSustentoQueNoSeHanCambiado() {
        var entradas = departamentoIdentificado();
        entradas.put("contexto." + SuficienciaInmueble.MANTENIMIENTO, noAplica("SUSTENTO-TEST-CONSERVADO"));
        var inicial = abrir(actor, entradas, contexto(Map.of()));
        var alta = captura.ejecutar(inicial.idBorrador(), inicial.version(), clave(), PANTALLA, actor);
        String contextoAntes = textoPropiedad(alta.idPropiedad(), "contexto_captura");
        String metadataAntes = textoPropiedad(alta.idPropiedad(), "metadatos_atributos");
        long versionFisica=propiedades.consultar(alta.idPropiedad(),actor).versionFisica();
        propiedades.editar(alta.idPropiedad(), new PropiedadUniversalService.ComandoEdicion(clave(), PANTALLA,
                "Comentario TEST de visita", null, null, null, null, null, null, null).conVersion(versionFisica), actor);
        assertEquals(contextoAntes, textoPropiedad(alta.idPropiedad(), "contexto_captura"));
        assertEquals(metadataAntes, textoPropiedad(alta.idPropiedad(), "metadatos_atributos"));
    }

    @Test void replayDeRegistroReautorizaElFisicoDespuesDeCambiarLaRelacionComercialVigente() {
        var empresa = empresaConDosAgentes();
        var inicial = abrir(empresa.primero(), departamentoIdentificado(), null);
        String clave = clave();
        var alta = captura.ejecutar(inicial.idBorrador(), inicial.version(), clave, PANTALLA, empresa.primero());
        assertTrue(captura.ejecutar(inicial.idBorrador(), inicial.version(), clave, PANTALLA, empresa.primero()).reintento());

        // Caso canónico C1: otra agente prospecta expresamente el físico. La
        // procedencia del creador permanece; ya no sustituye la relación lectora.
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(alta.idPropiedad(),
                "Prospección TEST propia del segundo agente", null), empresa.segundo());
        assertThrows(NoEncontradoException.class, () -> propiedades.consultar(alta.idPropiedad(), empresa.primero()));
        assertThrows(NoEncontradoException.class,
                () -> captura.ejecutar(inicial.idBorrador(), inicial.version(), clave, PANTALLA, empresa.primero()));
        assertThrows(NoEncontradoException.class,
                () -> captura.consultarResultado(inicial.idBorrador(), clave, empresa.primero()));
        assertEquals(1, contar("propiedad", empresa.primero()));
        assertEquals(0, contar("captacion", empresa.primero()));
        assertEquals(alta.idPropiedad(), propiedades.consultar(alta.idPropiedad(), empresa.segundo()).id());
    }

    @Test void replayDeEdicionNoEntregaElReciboSiYaNoExisteLecturaFisicaAutorizada() {
        var empresa=empresaConDosAgentes();
        var inicial=abrir(empresa.primero(),departamentoIdentificado(),null);
        var alta=captura.ejecutar(inicial.idBorrador(),inicial.version(),clave(),PANTALLA,empresa.primero());
        long observada=propiedades.consultar(alta.idPropiedad(),empresa.primero()).versionFisica();
        var comando=new PropiedadUniversalService.ComandoEdicion(clave(),PANTALLA,"Corrección TEST permitida",
                null,null,null,null,null,null,null).conVersion(observada);
        var original=propiedades.editar(alta.idPropiedad(),comando,empresa.primero());
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(alta.idPropiedad(),
                "Prospección TEST del segundo agente cambia la relación física vigente", null),empresa.segundo());
        assertThrows(NoEncontradoException.class,()->propiedades.consultar(alta.idPropiedad(),empresa.primero()));
        assertThrows(NoEncontradoException.class,()->propiedades.editar(alta.idPropiedad(),comando,empresa.primero()));
        assertEquals(original.versionFisica(),jdbc.queryForObject("select version_fisica from propiedad where id_propiedad=?",Long.class,alta.idPropiedad()));
    }

    @Test void ochoAltasFisicasConcurrentesDeLaMismaOrganizacionObtienenCodigosPropUnicos() throws Exception {
        List<MotorDeCaptura.EstadoCaptura> borradores = new ArrayList<>();
        for (int i = 0; i < 8; i++) borradores.add(abrir(actor, respuestas("tipoPropiedad", "TERRENO",
                "distrito", "Surco", "referenciaInterna", "Predio TEST lote" + i), null));
        List<Callable<MotorDeCaptura.Ejecucion>> trabajos = borradores.stream().<Callable<MotorDeCaptura.Ejecucion>>map(b ->
                () -> captura.ejecutar(b.idBorrador(), b.version(), "test-c2-" + b.idBorrador(), PANTALLA, actor)).toList();
        var altas = competir(trabajos);
        assertEquals(8, new HashSet<>(altas.stream().map(MotorDeCaptura.Ejecucion::idPropiedad).toList()).size());
        assertEquals(8, new HashSet<>(altas.stream().map(MotorDeCaptura.Ejecucion::codigoPropiedad).toList()).size());
        assertTrue(altas.stream().allMatch(alta -> alta.codigoPropiedad().matches("PROP-[0-9]{4}") && alta.idsEncargos().isEmpty()));
        assertEquals(8, contar("propiedad", actor));
        assertEquals(0, contar("captacion", actor));
        assertEquals(8, jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='PROPIEDAD_REGISTRADA'", Integer.class, actor.idOrganizacion()));
        assertEquals(8, jdbc.queryForObject("select count(*) from borrador_captura where organizacion_id=? and estado='J'", Integer.class, actor.idOrganizacion()));
    }

    private MotorDeCaptura.EstadoCaptura abrir(Actor profesional, Map<String, Respuesta> entradas, Contexto contexto) {
        return captura.avanzar(new Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, entradas, contexto), PANTALLA, profesional);
    }
    private static Map<String, Respuesta> respuestas(String... pares) {
        Map<String, Respuesta> respuestas = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) respuestas.put(pares[i], Respuesta.conocida(pares[i + 1]));
        return respuestas;
    }
    private static Map<String, Respuesta> departamentoIdentificado() {
        return respuestas("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco", "direccion", "Av. TEST123", "interiorUnidad", "501");
    }
    private static Contexto contexto(Map<String, Aplicabilidad> aplicabilidad) {
        return new Contexto("DEPARTAMENTO", "VIVIENDA", "Unidad TEST501", "VENTA", aplicabilidad, Set.of(), "JURISDICCION-TEST");
    }
    private static Respuesta noAplica(String evidencia) {
        return new Respuesta(null, EstadoDato.NO_APLICABLE, evidencia, null, null, null, LocalDate.of(2026, 10, 27), false);
    }
    private int contar(String tabla, Actor profesional) {
        if (!Set.of("propiedad", "captacion").contains(tabla)) throw new IllegalArgumentException("Tabla TEST no admitida.");
        return jdbc.queryForObject("select count(*) from " + tabla + " where organizacion_id=?", Integer.class, profesional.idOrganizacion());
    }
    private int eventosCaptura(long borrador) {
        return jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='BORRADOR_CAPTURA' and entidad_id=?",
                Integer.class, actor.idOrganizacion(), borrador);
    }
    private String textoPropiedad(long propiedad, String columna) {
        if (!Set.of("contexto_captura", "metadatos_atributos").contains(columna)) throw new IllegalArgumentException("Columna TEST no admitida.");
        return jdbc.queryForObject("select " + columna + " from propiedad where id_propiedad=?", String.class, propiedad);
    }
    private static String clave() { return "test-c2-" + UUID.randomUUID(); }

    private long organizacion(String modalidad, String estado) {
        String marca = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,?,?) returning id_organizacion",
                Long.class, "C2-C-" + marca, "TEST contratos de captura", modalidad, estado);
    }
    /** Cuenta/persona/pertenencia TEST real; hash no autenticable, sin clientes ni migración imaginarios. */
    private Actor profesional(long org, String rol, boolean administrar) {
        String marca = UUID.randomUUID().toString().replace("-", "");
        long cuenta = jdbc.queryForObject("""
                insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash)
                values(?,?,clock_timestamp(),'fixture-no-autenticable-c2') returning id_cuenta_acceso
                """, Long.class, "captura-" + marca, "captura-" + marca + "@example.invalid");
        long persona = jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional TEST de captura') returning id_persona", Long.class, org);
        long idRol = rol(org, persona, rol);
        if (Actor.AGENTE.equals(rol)) jdbc.update("""
                insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo)
                values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date,'D')
                """, org, idRol, "AG-C2C-" + idRol);
        else jdbc.update("""
                insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion)
                values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date)
                """, org, idRol, "BR-C2C-" + idRol);
        if (administrar) rol(org, persona, "ADMIN");
        jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,?,'A')",
                org, cuenta, persona, rol, administrar);
        return new Actor(org, persona, idRol, rol);
    }
    private long rol(long org, long persona, String rol) {
        return jdbc.queryForObject("""
                insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
                values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol
                """, Long.class, org, persona, rol);
    }
    private record Empresa(Actor primero, Actor segundo) { }
    private Empresa empresaConDosAgentes() {
        return new TransactionTemplate(gestor).execute(tx -> {
            long org = organizacion("EMPRESARIAL", "I");
            Actor broker = profesional(org, Actor.BROKER, true);
            Actor primero = profesional(org, Actor.AGENTE, false), segundo = profesional(org, Actor.AGENTE, false);
            for (Actor agente : List.of(primero, segundo)) jdbc.update("""
                    insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo)
                    values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date,'Supervisión TEST de contrato')
                    """, org, broker.idRolOperativo(), agente.idRolOperativo());
            jdbc.update("update organizacion set estado='A' where id_organizacion=?", org);
            return new Empresa(primero, segundo);
        });
    }
    private static <T> List<T> competir(List<Callable<T>> trabajos) throws Exception {
        var hilos = Executors.newFixedThreadPool(4);
        CountDownLatch preparados = new CountDownLatch(4), salida = new CountDownLatch(1);
        try {
            var futuros = trabajos.stream().map(trabajo -> hilos.submit(() -> {
                preparados.countDown();
                if (!salida.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrera TEST agotada.");
                return trabajo.call();
            })).toList();
            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            salida.countDown();
            List<T> resultado = new ArrayList<>();
            for (var futuro : futuros) resultado.add(futuro.get(25, TimeUnit.SECONDS));
            return resultado;
        } finally { salida.countDown(); hilos.shutdownNow(); }
    }
}
