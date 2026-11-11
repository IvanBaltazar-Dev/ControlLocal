package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.ObservacionMercadoService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

/**
 * <b>La Propiedad como activo de dato</b> (V76).
 *
 * <blockquote>
 * Una Propiedad representa un inmueble <b>conocido</b> por BROX, no
 * necesariamente una oferta <b>gestionada</b> por BROX. Su existencia,
 * procedencia e historia observada son independientes de Prospecciones y
 * Encargos. Los hechos comerciales solo nacen cuando existe la relacion
 * comercial que los autoriza.
 * </blockquote>
 *
 * <blockquote>
 * BROX nunca convierte una observacion de mercado en un hecho comercial ni
 * inventa una relacion para poder conservar conocimiento.
 * </blockquote>
 *
 * <h2>Lo que este gate vigila, y por que cada cosa</h2>
 *
 * <p><b>Cero titularidades es legitimo.</b> Se puede conocer un departamento de
 * 90 m2 con tres dormitorios anunciado a 180 000 USD sin saber todavia quien es
 * su dueno. Obligar a declararlo obligaria a inventarlo, y esa es la regla que
 * el proyecto lleva cortes enteros defendiendo: <b>lo que no se sabe se declara
 * faltante</b>.
 *
 * <p><b>Pero el Encargo si lo exige.</b> Conocer un inmueble no es lo mismo que
 * poder venderlo: una relacion comercial nace de alguien que puede encargarla.
 *
 * <p><b>Y la observacion no es un hecho comercial.</b> «Lo vi anunciado a
 * 190 000» no es un precio autorizado, ni publicado por BROX, ni negociado. Van
 * en series distintas porque son cosas distintas, y mezclarlas falsearia
 * cualquier comparable que se construya despues.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PropiedadComoActivoDeDatoIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired ObservacionMercadoService observaciones;
    @Autowired LocalComercialService locales;
    @Autowired EncargoService ciclo;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ObjectMapper json;
    @SpyBean ComandosIdempotentes comandos;

    // ==================================================================
    // 1. Un inmueble conocido, sin dueno conocido
    // ==================================================================

    @Test
    @DisplayName("una propiedad puede registrarse sin ninguna titularidad conocida")
    void sinTitularConocido() {
        long id = registrarConocida();

        assertEquals(0, contar(
                "select count(*) from titularidad_propiedad where id_propiedad = ?", id),
                "cero titularidades: no se sabe de quien es, y no se inventa");
        assertNull(jdbc.queryForMap(
                        "select id_rol_propietario from propiedad where id_propiedad = ?", id)
                        .get("id_rol_propietario"),
                "y la columna heredada tampoco se rellena con un propietario ficticio");
    }

    /**
     * La titularidad no desaparece: sigue siendo lo normal. Lo que cambia es que
     * deja de ser <b>obligatoria</b> para conocer un inmueble.
     */
    @Test
    @DisplayName("con titular conocido, el alta lo guarda igual que siempre")
    void conTitularConocidoNoCambiaNada() {
        long id = registrarGestionada(List.of());

        assertEquals(1, contar(
                "select count(*) from titularidad_propiedad where id_propiedad = ? "
                        + "and vigente_hasta is null", id));
        assertNotNull(jdbc.queryForMap(
                        "select id_rol_propietario from propiedad where id_propiedad = ?", id)
                        .get("id_rol_propietario"),
                "la proyeccion heredada se sigue escribiendo cuando hay titular");
    }

    // ==================================================================
    // 2. La procedencia: por que BROX conoce este inmueble
    // ==================================================================

    /**
     * <b>Sin procedencia, una propiedad de referencia es un rumor.</b> El
     * producto exige procedencia, vigencia y evidencia <b>antes</b> que
     * inferencia, asi que un inmueble conocido tiene que decir como se conocio.
     */
    @Test
    @DisplayName("toda propiedad declara como llego a conocerse")
    void todaPropiedadDeclaraSuOrigen() {
        assertEquals(0, contar(
                "select count(*) from propiedad where origen_incorporacion is null"),
                "una propiedad sin procedencia no se puede auditar ni comparar");
        assertEquals(1, contar("""
                select count(*) from pg_constraint
                 where conname = 'ck_propiedad_origen_incorporacion'
                """),
                "el vocabulario tiene que estar acotado, no ser texto libre");
    }

    @Test
    @DisplayName("el origen distingue el trabajo operativo de la observacion de mercado")
    void elOrigenDistingueElTrabajoDeLaObservacion() {
        long gestionada = registrarGestionada(List.of(
                new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                        null, null, null, null, null, null, null)));
        long conocida = registrarConocida();

        assertEquals("OPERACION", origenDe(gestionada),
                "la registro un agente en su trabajo");
        assertEquals("OBSERVACION", origenDe(conocida),
                "se conocio mirando el mercado, sin gestionarla");
    }

    @Test
    @DisplayName("y conserva quien la registro, no solo cuando")
    void conservaQuienLaRegistro() {
        long id = registrarConocida();
        Map<String, Object> fila = jdbc.queryForMap("""
                select id_rol_incorporo, fecha_registro from propiedad where id_propiedad = ?
                """, id);
        assertEquals(actor().idRolOperativo(),
                ((Number) fila.get("id_rol_incorporo")).longValue(),
                "quien la incorporo es parte de la evidencia, no del rastro de auditoria");
        assertNotNull(fila.get("fecha_registro"));
    }

    // ==================================================================
    // 3. Conocer no es gestionar: nada comercial nace del conocimiento
    // ==================================================================

    /** CAT-E11/18: abrir un episodio en preparación no acredita habilitación. */
    @Test
    @DisplayName("el Encargo en preparación no afirma disponibilidad ni precio autorizado")
    void sinEncargoHabilitadoLaFichaNoDiceEstarDisponible() {
        long id = registrarGestionada(List.of(
                new OperacionSolicitada("ALQUILER", new BigDecimal("2800"), "PEN",
                        null, null, null, null, null, null, null)));
        var ficha = propiedades.consultar(id, actor());
        assertNull(ficha.disponibilidadRotulo(),
                "preparar el episodio no acredita una oferta habilitada");
        assertNull(ficha.disponibilidadComercial(),
                "y tampoco su codigo: la pantalla no puede pintar lo que no se afirma");
        assertNull(jdbc.queryForObject(
                "select disponibilidad_comercial from propiedad where id_propiedad = ?",
                String.class, id));
        assertEquals(Captacion.EN_PREPARACION, jdbc.queryForObject(
                "select estado from captacion where id_propiedad=?", String.class, id));
        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad=? and hito='U'", id));
    }

    @Test
    @DisplayName("una propiedad de conocimiento se crea, se lee, se edita y se observa sin producir nada comercial")
    void elCicloCompletoNoProduceNadaComercial() {
        long id = registrarConocida();
        var observada = propiedades.consultar(id, actor());

        propiedades.editar(id, new ComandoEdicion(null, null, "Otra descripcion", null, null,
                List.of(new ValorAtributo("metraje_total", "95")), null, null)
                .conVersion(observada.versionFisica()), actor());
        observar(id, "VENTA", new BigDecimal("180000"), "USD", hoyLima().minusDays(10));
        observar(id, "VENTA", new BigDecimal("172000"), "USD", hoyLima());

        var ficha = propiedades.consultar(id, actor());
        assertEquals("Otra descripcion", ficha.descripcion());
        assertTrue(ficha.encargos().isEmpty());

        assertEquals(0, contar("select count(*) from captacion where id_propiedad = ?", id),
                "cero encargos");
        assertEquals(0, contar("select count(*) from prospeccion where id_propiedad = ?", id),
                "cero prospecciones: conocer no es prospectar");
        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad = ?", id),
                "cero hitos: el historico economico es del ENCARGO");
        assertEquals(0, contar("select count(*) from publicacion where id_propiedad = ?", id),
                "cero anuncios");
        assertEquals(2, contar(
                "select count(*) from observacion_mercado where id_propiedad = ?", id),
                "y las dos observaciones, que si son suyas");
    }

    /**
     * <b>El sexto verbo: se puede retirar del registro.</b>
     *
     * <p>El criterio de aceptacion original media cinco —crear, leer, editar,
     * observar y conservar— y los cinco pasan por servicios que solo aplican
     * frontera de tenant. La baja pasaba por {@code exigirPertenencia}, que
     * pregunta por una relacion comercial, asi que <b>el propio autor de la
     * propiedad recibia un 403</b> y no habia otra puerta: el registro quedaba
     * atrapado para siempre. Es una dependencia de Prospeccion y Encargo justo
     * donde V76 declaro que no la hay.
     */
    @Test
    @DisplayName("quien registro una propiedad de conocimiento puede darla de baja")
    void quienLaIncorporoPuedeRetirarla() {
        long id = registrarConocida();

        assertTrue(locales.desactivar(id, actor()),
                "el agente que la incorporo tiene que poder retirar su propio registro");

        assertEquals("I", jdbc.queryForObject(
                "select estado_registro from propiedad where id_propiedad = ?", String.class, id));
    }

    /**
     * <b>Y esa baja no inventa un retiro del mercado.</b>
     *
     * <p>La desactivacion escribia {@code disponibilidad_comercial = 'T'}
     * (RETIRADO) sin mirar: sobre una propiedad que nunca se ofrecio partia de
     * NULL —la maquina de estados se salta la validacion cuando el origen es
     * nulo— y dejaba dicho, en la columna y en el expediente, que se retiro del
     * mercado algo que jamas estuvo en el. Ademas sin vuelta atras, porque NULL
     * no es un codigo del vocabulario.
     */
    @Test
    @DisplayName("retirar del registro algo que nunca se ofrecio no declara un retiro comercial")
    void laBajaDeLoNoOfrecidoNoTocaLaDisponibilidad() {
        long id = registrarConocida();

        locales.desactivar(id, actor());

        assertNull(jdbc.queryForObject(
                "select disponibilidad_comercial from propiedad where id_propiedad = ?",
                String.class, id),
                "nunca estuvo en oferta: no hay nada que retirar del mercado");
        assertEquals(0, contar("""
                select count(*) from historial_estado
                 where entidad_tipo = 'DISPONIBILIDAD_PROPIEDAD' and id_entidad = ?
                """, id),
                "y el expediente no cuenta un hecho comercial que no ocurrio");
    }

    /** Preparar condiciones tampoco convierte la baja física en retirada de una oferta. */
    @Test
    @DisplayName("la baja con Encargo en preparación no inventa un retiro del mercado")
    void laBajaDeEncargoEnPreparacionNoInventaRetiroComercial() {
        long id = registrarGestionada(List.of(
                new OperacionSolicitada("ALQUILER", new BigDecimal("2800"), "PEN",
                        null, null, null, null, null, null, null)));
        assertNull(jdbc.queryForObject(
                "select disponibilidad_comercial from propiedad where id_propiedad = ?",
                String.class, id));

        locales.desactivar(id, actor());

        assertNull(jdbc.queryForObject(
                "select disponibilidad_comercial from propiedad where id_propiedad = ?",
                String.class, id));
        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad=?", id));
    }

    /**
     * <b>Y la historia previa sobrevive.</b> Si mas tarde nace una prospeccion o
     * un encargo, se enlazan a la MISMA propiedad: no la duplican, no la
     * reinterpretan y no borran lo observado antes.
     */
    @Test
    @DisplayName("prospectar y captar despues no destruyen la historia observada")
    void laHistoriaPreviaSobrevive() {
        long id = registrarConocida();
        var observada = propiedades.consultar(id, actor());
        observar(id, "VENTA", new BigDecimal("180000"), "USD", hoyLima().minusMonths(6));
        String retratoAntes = retratoDeObservaciones(id);

        // Y ahora se averigua de quien es. Es la historia real: primero se
        // conoce el inmueble, despues al dueno, y solo entonces se puede
        // encargar. La titularidad se anade sobre la MISMA propiedad.
        propiedades.editar(id, new ComandoEdicion(null, null, null, null,
                List.of(new Titular(unPropietario(), null, Boolean.TRUE)), null, null, null)
                .conVersion(observada.versionFisica()),
                actor());

        long idProspeccion = prospecciones.registrar(
                new ProspeccionService.DatosProspeccion(id, "Ahora si la vamos a captar", null),
                actor()).id();
        prospecciones.captar(idProspeccion, new ProspeccionService.DatosCaptura(
                "VENTA", new BigDecimal("175000"), "USD", new BigDecimal("3"),
                null, null, null, null, null, null), actor());

        assertEquals(1, contar("select count(*) from captacion where id_propiedad = ?", id),
                "el encargo se enlaza a la misma propiedad");
        assertEquals(retratoAntes, retratoDeObservaciones(id),
                "y lo observado antes queda exactamente como estaba");
        assertEquals(0, contar("""
                select count(*) from precio_propiedad where id_propiedad = ? and hito = 'U'
                """, id),
                "el borrador no acredita el precio autorizado que nace de revisión competente");
        assertEquals(Captacion.EN_PREPARACION, jdbc.queryForObject(
                "select estado from captacion where id_propiedad=?", String.class, id));
    }

    // ==================================================================
    // 4. Preparar no habilita el Encargo ni completa una titularidad desconocida
    // ==================================================================

    /** CAT-E11/18: el borrador admite faltantes; la habilitación conserva su guarda. */
    @Test
    @DisplayName("sin titular se prepara el Encargo, sin habilitarlo ni inventar la titularidad")
    void captarSinTitularSoloPreparaEncargo() {
        long id = registrarConocida();
        long idProspeccion = prospecciones.registrar(
                new ProspeccionService.DatosProspeccion(id, "Sin dueno conocido", null), actor()).id();

        var preparada = prospecciones.captar(idProspeccion, new ProspeccionService.DatosCaptura(
                "VENTA", new BigDecimal("175000"), "USD", new BigDecimal("3"),
                null, null, null, null, null, null), actor());
        assertNotNull(preparada.idCaptacion());
        assertFalse(ciclo.consultar(preparada.idCaptacion(), actor()).vigencia().habilitado());
        assertEquals(Captacion.EN_PREPARACION, jdbc.queryForObject(
                "select estado from captacion where id_captacion=?", String.class, preparada.idCaptacion()));
        assertEquals(0, contar("select count(*) from titularidad_propiedad where id_propiedad=?", id));
        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad=?", id));
    }

    @Test
    @DisplayName("el alta explícita con operación y titular faltante abre sólo preparación")
    void elAltaConOperacionesSinTitularNoHabilitaElEncargo() {
        long id = alta(List.of(), List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                null, null, null, null, null, null, null)), "OPERACION");
        assertEquals(0, contar("select count(*) from titularidad_propiedad where id_propiedad=?", id));
        long encargo = jdbc.queryForObject("select id_captacion from captacion where id_propiedad=?", Long.class, id);
        assertFalse(ciclo.consultar(encargo, actor()).vigencia().habilitado());
        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad=?", id));
        assertNull(propiedades.consultar(id, actor()).disponibilidadComercial());
    }

    // ==================================================================
    // 5. La observacion NO es un hecho comercial
    // ==================================================================

    @Test
    @DisplayName("una observacion nunca entra en el historico economico del encargo")
    void laObservacionNoEsUnHecho() {
        long id = registrarConocida();
        observar(id, "VENTA", new BigDecimal("190000"), "USD", hoyLima());

        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad = ?", id),
                "«lo vi anunciado a 190 000» no es un precio autorizado ni publicado");
        assertNull(jdbc.queryForMap(
                        "select precio_referencial from propiedad where id_propiedad = ?", id)
                        .get("precio_referencial"),
                "ni se proyecta sobre la propiedad como si alguien lo hubiera autorizado");
    }

    @Test
    @DisplayName("cada observacion conserva su fuente, su fecha y quien la capturo")
    void cadaObservacionConservaSuEvidencia() {
        long id = registrarConocida();
        observar(id, "VENTA", new BigDecimal("190000"), "USD", hoyLima().minusDays(3));

        Map<String, Object> fila = jdbc.queryForMap("""
                select operacion, importe, moneda, fecha_observada, fuente, id_rol_actor
                  from observacion_mercado where id_propiedad = ?
                """, id);
        assertEquals("V", fila.get("operacion"));
        assertEquals("USD", fila.get("moneda"));
        assertNotNull(fila.get("fecha_observada"), "sin fecha, un precio no significa nada");
        assertNotNull(fila.get("fuente"), "sin fuente, es un rumor");
        assertNotNull(fila.get("id_rol_actor"), "y alguien respondio por ella");
    }

    /**
     * <b>Append-only.</b> Una observacion es un hecho fechado: corregirla
     * borraria la muestra que la hacia util. Lo que se hace es observar otra vez.
     */
    @Test
    @DisplayName("las observaciones no se editan ni se borran: se anaden")
    void lasObservacionesSonAppendOnly() {
        long id = registrarConocida();
        observar(id, "VENTA", new BigDecimal("190000"), "USD", hoyLima().minusMonths(2));
        observar(id, "VENTA", new BigDecimal("178000"), "USD", hoyLima());

        assertEquals(2, contar(
                "select count(*) from observacion_mercado where id_propiedad = ?", id),
                "la de hace dos meses sigue ahi: es la que mide como se movio el precio");

        assertThrows(Exception.class, () -> jdbc.update("""
                update observacion_mercado set importe = 1 where id_propiedad = ?
                """, id), "editar una observacion la deja de ser evidencia");
        assertThrows(Exception.class, () -> jdbc.update("""
                delete from observacion_mercado where id_propiedad = ?
                """, id), "y borrarla borra la muestra");
    }

    @Test
    @DisplayName("una observacion sin fuente o sin fecha se rechaza")
    void sinEvidenciaNoHayObservacion() {
        long id = registrarConocida();
        assertThrows(ReglaNegocioException.class,
                () -> observaciones.registrar(new ObservacionMercadoService.DatosObservacion(
                        id, hoyLima(), "VENTA", new BigDecimal("100000"), "USD", null, null), actor(), null, com.controllocal.service.soporte.Procedencia.deLaPantalla()),
                "sin fuente no se guarda: la evidencia va antes que la inferencia");
        assertThrows(ReglaNegocioException.class,
                () -> observaciones.registrar(new ObservacionMercadoService.DatosObservacion(
                        id, null, "VENTA", new BigDecimal("100000"), "USD", "PORTAL", null), actor(), null, com.controllocal.service.soporte.Procedencia.deLaPantalla()),
                "sin fecha tampoco: un precio sin fecha no se puede comparar");
    }

    @Test
    @DisplayName("una observacion con fecha futura se rechaza: no se observa lo que no ha pasado")
    void noSeObservaElFuturo() {
        long id = registrarConocida();
        assertThrows(ReglaNegocioException.class,
                () -> observar(id, "VENTA", new BigDecimal("100000"), "USD",
                        hoyLima().plusDays(1)));
    }

    // ==================================================================
    // 6. La invariante que ata las dos series
    // ==================================================================

    /**
     * Ninguna fila de {@code precio_propiedad} puede quedarse sin encargo. Es la
     * frontera dicha al reves: si un hecho comercial no tiene relacion comercial
     * que lo autorice, no es un hecho comercial — es una observacion, y va en la
     * otra serie.
     */
    @Test
    @DisplayName("invariante: ningun hito economico NUEVO puede nacer sin su encargo")
    void ningunHitoNuevoSinEncargo() {
        long id = registrarConocida();
        int antecedentes = contar("select count(*) from precio_propiedad where id_captacion is null");
        Exception error = assertThrows(Exception.class, () -> jdbc.update("""
                insert into precio_propiedad
                       (organizacion_id, id_propiedad, hito, operacion, moneda, monto, fecha)
                values (?, ?, 'U', 'V', 'USD', 180000, current_date)
                """, actor().idOrganizacion(), id),
                "un precio autorizado sin encargo que lo autorice no es un hecho comercial");
        assertTrue(error.getMessage().contains("observacion_mercado"),
                "y el error tiene que decir donde va lo que si se vio: " + error.getMessage());

        // Las filas historicas se quedan: borrarlas seria inventar que nunca
        // existieron. Lo que se cierra es la puerta, no el pasado -- y ningun
        // escritor vivo las produce ya.
        assertEquals(antecedentes, contar("""
                select count(*) from precio_propiedad where id_captacion is null
                """));
    }

    @Test
    @DisplayName("la observación sin precio conserva procedencia y no crea efectos comerciales")
    void observacionParcialConservaProcedenciaSinCeroNiHechosComerciales() throws Exception {
        Actor profesional = independienteTest();
        long id = propiedadTest(profesional);
        Map<String, Object> antes = estadoFisicoYComercial(id);
        Procedencia procedencia = procedenciaTest();
        var datos = datosParciales(id);

        var resultado = observaciones.registrar(datos, profesional, "om-" + UUID.randomUUID(), procedencia);

        assertNull(resultado.importe());
        assertNull(resultado.moneda());
        var fila = jdbc.queryForMap("select * from observacion_mercado where id_observacion=?", resultado.id());
        assertNull(fila.get("importe"));
        assertNull(fila.get("moneda"));
        assertEquals(datos.fechaObservada(), ((java.sql.Date) fila.get("fecha_observada")).toLocalDate());
        assertEquals("PORTAL_TEST", fila.get("fuente"));
        assertEquals("V", fila.get("operacion"));
        assertEquals(profesional.idRolOperativo(), ((Number) fila.get("id_rol_actor")).longValue());
        assertEquals(json.readTree(json.writeValueAsString(procedencia)), json.readTree((String) fila.get("procedencia")));
        assertEquals(antes, estadoFisicoYComercial(id));
        sinEfectosComerciales(id);
    }

    @Test
    @DisplayName("la serie exige alcance actual; broker lee y gobierno u otros actores no heredan acceso")
    void observacionesNoSonLecturaGeneralDelTenantNiPermisoDeRegistroDelBroker() {
        EmpresaTest empresa = empresaTest();
        long id = propiedadTest(empresa.agente());
        var datos = datosParciales(id);
        observaciones.registrar(datos, empresa.agente(), null, Procedencia.deLaPantalla());

        assertEquals(1, observaciones.listarDe(id, empresa.broker()).size());
        assertThrows(AccesoNoAutorizadoException.class, () -> observaciones.registrar(datos,
                empresa.broker(), null, Procedencia.deLaPantalla()));
        assertThrows(AccesoNoAutorizadoException.class, () -> observaciones.listarDe(id, empresa.gobierno()));
        assertThrows(NoEncontradoException.class, () -> observaciones.listarDe(id, empresa.otroAgente()));
        assertThrows(NoEncontradoException.class, () -> observaciones.registrar(datos,
                empresa.otroAgente(), null, Procedencia.deLaPantalla()));

        Actor homonimoDeOtroTenant = independienteTest();
        assertEquals(jdbc.queryForObject("select nombres_o_razon_social from persona where id_persona=?",
                        String.class, empresa.agente().idPersona()),
                jdbc.queryForObject("select nombres_o_razon_social from persona where id_persona=?",
                        String.class, homonimoDeOtroTenant.idPersona()));
        assertThrows(NoEncontradoException.class, () -> observaciones.listarDe(id, homonimoDeOtroTenant));
        assertThrows(NoEncontradoException.class, () -> observaciones.registrar(datos,
                homonimoDeOtroTenant, null, Procedencia.deLaPantalla()));
        assertEquals(1, contar("select count(*) from observacion_mercado where id_propiedad=?", id));
        sinEfectosComerciales(id);
    }

    @Test
    @DisplayName("dos intentos concurrentes con la clave original conservan un solo hecho y recibo")
    void observacionConcurrenteConMismaClaveConservaUnEfectoYResultadoOriginal() throws Exception {
        Actor profesional = independienteTest();
        long id = propiedadTest(profesional);
        var datos = datosParciales(id);
        var procedencia = procedenciaTest();
        String clave = "om-" + UUID.randomUUID();
        var hilos = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2), salida = new CountDownLatch(1);
        try {
            var trabajos = java.util.stream.IntStream.range(0, 2).mapToObj(i -> hilos.submit(() -> {
                preparados.countDown();
                if (!salida.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Barrera TEST agotada.");
                return observaciones.registrar(datos, profesional, clave, procedencia);
            })).toList();
            assertTrue(preparados.await(15, TimeUnit.SECONDS));
            salida.countDown();
            var original = trabajos.get(0).get(40, TimeUnit.SECONDS);
            assertEquals(original, trabajos.get(1).get(40, TimeUnit.SECONDS));
            assertEquals(original, observaciones.registrar(datos, profesional, clave, procedencia));
            assertEquals(1, contar("select count(*) from observacion_mercado where id_propiedad=?", id));
            assertEquals(1, contar("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",
                    profesional.idOrganizacion(), clave));
            var recibo = jdbc.queryForMap("select entidad_id,resultado from comando_idempotente where organizacion_id=? and idempotency_key=?",
                    profesional.idOrganizacion(), clave);
            assertEquals(original.id(), ((Number) recibo.get("entidad_id")).longValue());
            assertEquals(original, json.readValue((String) recibo.get("resultado"), ObservacionMercadoService.FichaObservacion.class));
            sinEfectosComerciales(id);
        } finally {
            salida.countDown();
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("fallar la conservación del recibo revierte también la observación y su procedencia")
    void falloDelReciboRevierteHechoYProcedenciaEnLaMismaTransaccion() {
        Actor profesional = independienteTest();
        long id = propiedadTest(profesional);
        String clave = "om-" + UUID.randomUUID();
        var procedencia = procedenciaTest();
        doThrow(new IllegalStateException("Fallo TEST al guardar recibo de observación"))
                .when(comandos).registrar(eq(profesional), eq(clave), eq("REGISTRAR_OBSERVACION_MERCADO"),
                        anyString(), eq("OBSERVACION_MERCADO"), anyLong(), eq(procedencia), anyString());

        assertThrows(IllegalStateException.class,
                () -> observaciones.registrar(datosParciales(id), profesional, clave, procedencia));
        assertEquals(0, contar("select count(*) from observacion_mercado where id_propiedad=?", id));
        assertEquals(0, contar("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",
                profesional.idOrganizacion(), clave));
        sinEfectosComerciales(id);
    }

    // ==================================================================
    // Fixtures TEST propios de AC054, sin relaciones comerciales artificiales
    // ==================================================================

    private static ObservacionMercadoService.DatosObservacion datosParciales(long id) {
        return new ObservacionMercadoService.DatosObservacion(id, hoyLima(),
                "VENTA", null, null, "Portal TEST", "Aviso observado sin precio declarado");
    }

    private static LocalDate hoyLima() {
        return LocalDate.now(ZoneId.of("America/Lima"));
    }

    private static Procedencia procedenciaTest() {
        return Procedencia.deAgente("API", "TEST-KAIROS", null, null, "test-observacion",
                "test-turno", "test-mensaje", "Guardar la referencia observada sin inventar precio")
                .invocando("registrar_observacion_mercado");
    }

    private Map<String, Object> estadoFisicoYComercial(long id) {
        return jdbc.queryForMap("select precio_referencial,moneda_referencial,disponibilidad_comercial,version_fisica from propiedad where id_propiedad=?", id);
    }

    private void sinEfectosComerciales(long id) {
        for (String tabla : List.of("captacion", "prospeccion", "precio_propiedad", "publicacion"))
            assertEquals(0, contar("select count(*) from " + tabla + " where id_propiedad=?", id), tabla);
    }

    private long propiedadTest(Actor profesional) {
        return propiedades.registrar(new ComandoRegistro(null, Procedencia.deLaPantalla(), null,
                "TERRENO", null, "Referencia de mercado TEST",
                new Ubicacion("Av. TEST " + UUID.randomUUID(), "Surco", null, null, null, null, null, null, null),
                List.of(), List.of(), List.of(), null, "OBSERVACION"), profesional).idPropiedad();
    }

    private Actor independienteTest() {
        return new TransactionTemplate(gestor).execute(tx -> profesionalTest(organizacionTest("INDIVIDUAL", "A"), Actor.AGENTE, false));
    }

    private long organizacionTest(String modalidad, String estado) {
        String marca = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,?,?) returning id_organizacion",
                Long.class, "TEST-OM-" + marca, "TEST observaciones de mercado", modalidad, estado);
    }

    private Actor profesionalTest(long org, String rol, boolean administrar) {
        String marca = UUID.randomUUID().toString().replace("-", "");
        long cuenta = jdbc.queryForObject("""
                insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash)
                values(?,?,clock_timestamp(),'fixture-no-autenticable-observacion') returning id_cuenta_acceso
                """, Long.class, "mercado-" + marca, "mercado-" + marca + "@example.invalid");
        long persona = jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional TEST de mercado') returning id_persona",
                Long.class, org);
        long idRol = rolTest(org, persona, rol);
        if (Actor.AGENTE.equals(rol)) jdbc.update("""
                insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo)
                values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date,'D')
                """, org, idRol, "AG-OM-" + idRol);
        else jdbc.update("""
                insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion)
                values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date)
                """, org, idRol, "BR-OM-" + idRol);
        if (administrar) rolTest(org, persona, "ADMIN");
        jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,?,'A')",
                org, cuenta, persona, rol, administrar);
        return new Actor(org, persona, idRol, rol);
    }

    private long rolTest(long org, long persona, String rol) {
        return jdbc.queryForObject("""
                insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
                values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol
                """, Long.class, org, persona, rol);
    }

    private record EmpresaTest(Actor agente, Actor otroAgente, Actor broker, Actor gobierno) { }
    private EmpresaTest empresaTest() {
        return new TransactionTemplate(gestor).execute(tx -> {
            long org = organizacionTest("EMPRESARIAL", "I");
            Actor broker = profesionalTest(org, Actor.BROKER, true);
            Actor agente = profesionalTest(org, Actor.AGENTE, false), otro = profesionalTest(org, Actor.AGENTE, false);
            for (Actor profesional : List.of(agente, otro)) jdbc.update("""
                    insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo)
                    values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date,'Supervisión TEST de observaciones')
                    """, org, broker.idRolOperativo(), profesional.idRolOperativo());
            long idAdmin = jdbc.queryForObject("select id_persona_rol from persona_rol where organizacion_id=? and id_persona=? and tipo_rol='ADMIN'",
                    Long.class, org, broker.idPersona());
            jdbc.update("update organizacion set estado='A' where id_organizacion=?", org);
            return new EmpresaTest(agente, otro, broker,
                    new Actor(org, broker.idPersona(), idAdmin, Actor.TENANT_ADMIN));
        });
    }

    // ==================================================================
    // Fixture precedente, reconciliado con identificación y apertura C2
    // ==================================================================

    /** El inmueble que BROX conoce y no gestiona: sin titular y sin encargo. */
    private long registrarConocida() {
        return alta(List.of(), List.of(), "OBSERVACION");
    }

    /** El inmueble que BROX gestiona: con su titular. */
    private long registrarGestionada(List<OperacionSolicitada> operaciones) {
        return alta(List.of(new Titular(unPropietario(), null, Boolean.TRUE)), operaciones, "OPERACION");
    }

    private Long unPropietario() {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor().idOrganizacion());
    }

    private long alta(List<Titular> titulares, List<OperacionSolicitada> operaciones, String origen) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Conocida por BROX",
                new Ubicacion("Av. Conocimiento "
                        + java.util.UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, "TEST-501", null, null, null),
                titulares,
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                operaciones, null, origen), actor()).idPropiedad();
    }

    private void observar(long idPropiedad, String operacion, BigDecimal importe, String moneda,
                          LocalDate fecha) {
        observaciones.registrar(new ObservacionMercadoService.DatosObservacion(
                idPropiedad, fecha, operacion, importe, moneda, "PORTAL",
                "Aviso publicado en un portal"), actor(), null, com.controllocal.service.soporte.Procedencia.deLaPantalla());
    }

    /** Todas las observaciones, planas: para poder decir «quedaron IGUALES». */
    private String retratoDeObservaciones(long idPropiedad) {
        return jdbc.queryForObject("""
                select coalesce(string_agg(
                           fecha_observada || '|' || operacion || '|' || importe || '|' ||
                           moneda || '|' || fuente, ';' order by id_observacion), '')
                  from observacion_mercado where id_propiedad = ?
                """, String.class, idPropiedad);
    }

    private String origenDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select origen_incorporacion from propiedad where id_propiedad = ?",
                String.class, idPropiedad);
    }

    private int contar(String sql, Object... parametros) {
        Integer n = jdbc.queryForObject(sql, Integer.class, parametros);
        return n == null ? 0 : n;
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), "AGENTE");
    }
}
