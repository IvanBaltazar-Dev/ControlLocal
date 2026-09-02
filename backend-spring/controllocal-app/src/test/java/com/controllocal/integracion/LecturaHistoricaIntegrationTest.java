package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Quien lee la informacion comercial HISTORICA</b> (D-P0-6, N39).
 *
 * <h2>La otra mitad de P0</h2>
 * V87 decidio <b>quien escribe</b> los hechos de una propiedad y de cada
 * encargo. Faltaba la pregunta simetrica —<b>quien lee el pasado</b>— y la
 * respuesta por defecto era «cualquiera del tenant»: la ficha universal servia
 * la serie economica completa de todos los encargos a cualquier agente que
 * supiera el id, y {@code GET /locales/{id}/precios} la servia <b>incluso a
 * otra corredora</b>.
 *
 * <h2>La tabla que se prueba aqui</h2>
 * <pre>
 *   actor                    historia de PROPIEDAD | historico de ENCARGO | expediente    | rastro de reasignaciones
 *   AGENTE responsable           SI                | solo los suyos       | no            | no
 *   AGENTE no responsable        no                | solo los suyos       | no            | no
 *   BROKER                       en su alcance     | de sus supervisados  | si (C2)       | encargos que HOY lleva un supervisado
 *   TENANT_ADMIN                 NO por ser admin  | NO por ser admin     | si (gobierno) | si, todo el tenant (gobierno)
 *   otro tenant                  nunca (404)       | nunca (404)          | nunca (404)   | nunca
 * </pre>
 *
 * <p>La cuarta columna entra el 2026-09-02 (F3-bis) y es <b>aplicacion</b> de la
 * fila del historico de ENCARGO, no una regla nueva: hasta entonces
 * {@code GET /captaciones/reasignaciones} servia el tenant entero a cualquier
 * BROKER. Lo que en ella es interpretacion —el alcance es el del encargo de
 * HOY, no el del agente saliente ni el del broker que firmo— esta declarado en
 * {@code docs/ai/decision-autoridad-de-edicion-de-la-propiedad.md}, D-P0-6.
 *
 * <p>El principio que la sostiene, y por el que el TENANT_ADMIN aparece dos
 * veces en negativo y una en positivo: <b>gobernar no es operar</b>. Poder
 * decidir quien responde por una propiedad —y leer el expediente de traspasos,
 * que es organigrama— no concede los importes, los cierres ni la actividad
 * comercial. Quien gobierne <b>y</b> opere lo obtiene actuando con su banda
 * BROKER: el {@code Actor} llega con una sola banda por peticion.
 *
 * <h2>Se mide el CABLE, no el predicado</h2>
 * Cada caso pasa por {@code propiedades.consultar} y por
 * {@code precios.listarPorLocal}, que es lo que reciben BROX Web y KAIROS. Una
 * prueba del booleano de {@code AutoridadDePropiedad} demostraria que la funcion
 * responde; lo que hay que demostrar es que <b>ningun bloque de la respuesta</b>
 * se cuela por otro camino.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LecturaHistoricaIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired PrecioLocalService precios;
    @Autowired CaptacionService captaciones;

    /** El mismo codigo que usa {@code AlcanceYGobiernoDeLaAutoridadIntegrationTest}. */
    private static final String CODIGO_TENANT_VECINO = "ALCANCE-TENANT-VECINO";

    /** Por encima del minimo de {@code PoliticaComercial.MOTIVO_REASIGNACION}. */
    private static final String MOTIVO_DE_REASIGNACION =
            "Reparto de cartera del trimestre entre equipos";

    // ==================================================================
    // 1. El control positivo: el responsable con su encargo lo ve todo
    // ==================================================================

    /**
     * <b>Va primero, y no es cortesia.</b> Sin el, las nueve negaciones que
     * siguen seguirian verdes en un sistema que no dejara leer <b>nada</b>, y
     * una regla que niega siempre no es la regla que se decidio.
     */
    @Test
    @DisplayName("D-P0-6/control: el responsable con su encargo ve historia, historico y actividad")
    void elResponsableConSuEncargoLoVeTodo() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        FichaPropiedadUniversal ficha = propiedades.consultar(idPropiedad, duena);

        assertNotNull(ficha.historia(),
                "responde por la propiedad y el unico encargo es suyo: la historia del inmueble "
                        + "es exactamente lo que puede leer");
        assertFalse(ficha.historia().linea().isEmpty(),
                "y trae hitos: el alta escribio el primer 'U' de la serie");
        assertEquals(1, ficha.encargos().size());
        assertNotNull(ficha.encargos().get(0).historico(),
                "el historico de SU encargo llega, y llega como lista y no como ausencia");
        assertFalse(ficha.encargos().get(0).historico().isEmpty());
        assertNotNull(ficha.actividad(),
                "y el bloque de actividad existe, aunque todavia no haya hechos que contar");

        assertFalse(precios.listarPorLocal(idPropiedad, duena).isEmpty(),
                "y la serie por su endpoint propio dice lo mismo que la ficha: son dos lecturas "
                        + "del mismo hecho y no pueden discrepar");
    }

    // ==================================================================
    // 2. Un encargo ajeno sobre la MISMA propiedad
    // ==================================================================

    /**
     * <b>El caso que separa las dos filas de la tabla.</b>
     *
     * <p>Dos agentes sobre el mismo inmueble: una responde por la propiedad y
     * lleva el ALQUILER; la otra abre una VENTA y no responde por nada. La
     * segunda tiene que ver <b>su</b> historico y <b>nada mas</b>: ni la
     * historia del inmueble —que no es suya— ni el historico del alquiler ajeno.
     *
     * <p>Y la primera tampoco hereda la venta por ser responsable: la historia
     * que lee se compone <b>solo de los encargos que puede ver</b>. Un bloque
     * agregado que sumara los dos seria la puerta por la que se lee el importe
     * de un encargo ajeno sin pedirlo.
     */
    @Test
    @DisplayName("D-P0-6: otro agente con encargo propio ve SU historico y nada mas")
    void unEncargoPropioNoAbreLaHistoriaDelInmueble() {
        Actor duena = agenteDelEquipo(0);
        Actor otra = agenteDelEquipo(1);
        long idPropiedad = registrar(duena);
        long idVenta = abrirVentaCon(idPropiedad, otra);
        long idAlquiler = unEncargoDe(idPropiedad, "A");

        FichaPropiedadUniversal suya = propiedades.consultar(idPropiedad, otra);

        assertNull(suya.historia(),
                "no responde por la propiedad, asi que la memoria del INMUEBLE no es suya: "
                        + "tener un encargo no concede la historia del inmueble");
        assertNotNull(encargo(suya, idVenta).historico(),
                "pero SU encargo si, entero");
        assertFalse(encargo(suya, idVenta).historico().isEmpty());
        assertNull(encargo(suya, idAlquiler).historico(),
                "y el del alquiler ajeno no: nulo, que con NON_NULL es «no viaja»");
        assertNotNull(encargo(suya, idAlquiler).importe(),
                "aunque el BLOQUE del alquiler si llega: «no puedes ver lo que se pidio antes» "
                        + "no es «este encargo no existe», y hay pantallas que necesitan saber "
                        + "que la propiedad tiene un alquiler vivo");
        assertNotNull(suya.actividad(),
                "y la actividad existe, acotada a su encargo");

        // Y la responsable, por el otro lado: ve su alquiler y NO la venta ajena,
        // ni por el bloque ni por la historia agregada.
        FichaPropiedadUniversal deLaResponsable = propiedades.consultar(idPropiedad, duena);
        assertNotNull(encargo(deLaResponsable, idAlquiler).historico());
        assertNull(encargo(deLaResponsable, idVenta).historico(),
                "responder por la propiedad no concede el historico del encargo de otro (P0-4 "
                        + "leido del lado de la lectura)");
        assertNotNull(deLaResponsable.historia(), "su historia si, con lo que puede ver");
        assertTrue(deLaResponsable.historia().linea().stream()
                        .allMatch(hito -> hito.idEncargo() == idAlquiler),
                "y la historia agregada NO se convierte en la puerta trasera de la venta ajena: "
                        + "cada hito de la linea sigue siendo de un encargo que si puede leer");
        assertTrue(deLaResponsable.historia().porOperacion().stream()
                        .noneMatch(episodio -> "VENTA".equals(episodio.operacion())),
                "tampoco por el recuento por operacion, que es donde el importe ajeno se leeria "
                        + "sin llevar id");

        // Y la serie por su endpoint propio dice lo mismo, hito a hito.
        assertTrue(precios.listarPorLocal(idPropiedad, otra).stream()
                        .allMatch(hito -> "VENTA".equals(hito.operacion())),
                "/locales/{id}/precios responde lo MISMO que la ficha: dos lecturas del mismo "
                        + "hecho que discreparan serian dos autoridades");
        assertTrue(precios.listarPorLocal(idPropiedad, duena).stream()
                        .allMatch(hito -> "ALQUILER".equals(hito.operacion())));
    }

    @Test
    @DisplayName("D-P0-6: un agente sin encargos no lee historia, ni historico, ni actividad")
    void unAgenteSinEncargosNoLeeNada() {
        Actor duena = agenteDelEquipo(0);
        Actor ajena = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);

        FichaPropiedadUniversal ficha = propiedades.consultar(idPropiedad, ajena);

        assertNull(ficha.historia(), "ni responde por ella ni tiene encargos: no hay historia suya");
        assertTrue(ficha.encargos().stream().allMatch(e -> e.historico() == null),
                "ningun historico");
        assertNull(ficha.actividad(), "y ningun hecho comercial");

        // Pero la propiedad SIGUE VISIBLE. Lo que D-P0-6 acota es el pasado
        // comercial, no la existencia del inmueble.
        assertNotNull(ficha.id());
        assertFalse(ficha.atributos().isEmpty(),
                "la cosa fisica no se acota: sigue viendo tipo, ubicacion y atributos");
        assertNotNull(ficha.responsabilidad(),
                "y sigue sabiendo quien responde, que es lo que necesita para no pisarla");

        assertTrue(precios.listarPorLocal(idPropiedad, ajena).isEmpty(),
                "la serie llega VACIA, y vacia significa «de esta serie no te corresponde nada»");
    }

    // ==================================================================
    // 3. El BROKER: su alcance de supervision, ni mas ni menos
    // ==================================================================

    @Test
    @DisplayName("D-P0-6: el BROKER lee lo de sus supervisados, y no lo del equipo de al lado")
    void elBrokerLeeLoDeSuEquipo() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        long idAlquiler = unEncargoDe(idPropiedad, "A");

        FichaPropiedadUniversal delSupervisor = propiedades.consultar(idPropiedad, broker());
        assertNotNull(delSupervisor.historia(),
                "supervisa hoy a quien responde: la historia del inmueble esta en su alcance");
        assertNotNull(encargo(delSupervisor, idAlquiler).historico(),
                "y el historico de un encargo de su equipo tambien");
        assertNotNull(delSupervisor.actividad());
        assertFalse(precios.listarPorLocal(idPropiedad, broker()).isEmpty(),
                "y la serie por su endpoint, igual");

        // Un encargo de un agente de OTRO equipo sobre la MISMA propiedad: ese no.
        Actor deOtroEquipo = agenteDeOtroEquipo();
        long idVenta = abrirVentaCon(idPropiedad, deOtroEquipo);

        FichaPropiedadUniversal conLosDos = propiedades.consultar(idPropiedad, broker());
        assertNotNull(encargo(conLosDos, idAlquiler).historico(), "el suyo sigue");
        assertNull(encargo(conLosDos, idVenta).historico(),
                "el del agente de otro equipo NO: el alcance del broker es «los que supervisa "
                        + "hoy», y no se ensancha porque el encargo cuelgue de una propiedad que "
                        + "si alcanza");
        assertTrue(precios.listarPorLocal(idPropiedad, broker()).stream()
                        .allMatch(hito -> "ALQUILER".equals(hito.operacion())),
                "y la serie tampoco se ensancha por la propiedad");

        // Y el broker del OTRO equipo, al reves: nada de la propiedad, su encargo si.
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        FichaPropiedadUniversal delAjeno = propiedades.consultar(idPropiedad, brokerAjeno);
        assertNull(delAjeno.historia(),
                "no supervisa a quien responde por el inmueble, asi que su memoria no le toca");
        assertNull(encargo(delAjeno, idAlquiler).historico());
    }

    // ==================================================================
    // 4. El TENANT_ADMIN: gobierna, y por eso no opera
    // ==================================================================

    /**
     * <b>La fila que sorprende, y la que sostiene el principio.</b>
     *
     * <p>El gobierno del tenant lee el <b>expediente de traspasos</b> —que es
     * organigrama: quien respondio, desde cuando y por que se movio— y <b>no</b>
     * la informacion comercial. Las dos respuestas en la misma prueba a
     * proposito: si solo se midiera la negacion, un sistema que le negara todo
     * pasaria igual, y no es lo que se decidio.
     */
    @Test
    @DisplayName("D-P0-6: el TENANT_ADMIN no lee lo comercial, pero si el expediente")
    void elGobiernoDelTenantNoHeredaLoComercial() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        Actor gobierno = tenantAdmin();

        FichaPropiedadUniversal ficha = propiedades.consultar(idPropiedad, gobierno);
        assertNull(ficha.historia(),
                "gobernar no es operar: decidir quien responde no concede a cuanto se pidio ni a "
                        + "cuanto se cerro");
        assertTrue(ficha.encargos().stream().allMatch(e -> e.historico() == null),
                "ni el historico de ningun encargo");
        assertNull(ficha.actividad(), "ni la actividad comercial");
        assertTrue(precios.listarPorLocal(idPropiedad, gobierno).isEmpty(),
                "ni la serie de precios por su endpoint propio");

        // Y el control positivo de la misma banda: el EXPEDIENTE si.
        assertEquals(1, propiedades.traspasosDe(idPropiedad, gobierno).size(),
                "el expediente de traspasos SI: es superficie de gobierno (C2), y esta prueba "
                        + "seguiria verde sin esta linea aunque el admin no pudiera leer nada");

        // Y sigue viendo la propiedad y quien responde: lo acotado es el pasado
        // comercial, no el inventario.
        assertNotNull(ficha.responsabilidad().idResponsable());
        assertTrue(ficha.responsabilidad().puedeTraspasar(),
                "y sigue pudiendo iniciar el traspaso: la lectura historica y el gobierno del "
                        + "responsable son dos autorizaciones distintas");
    }

    // ==================================================================
    // 4 bis. El RASTRO de reasignaciones del ENCARGO
    // ==================================================================

    /**
     * <b>El historial de reasignaciones se acota con el alcance del ENCARGO
     * ACTUAL</b> (F3-bis, 2026-09-02 — interpretacion de D-P0-6).
     *
     * <h2>Que estaba pasando</h2>
     * {@code GET /captaciones/reasignaciones} servia
     * {@code findByOrganizacionIdOrderByIdDesc(actor.idOrganizacion())}: <b>todo
     * el tenant</b>, sin pasar por {@code Alcances}. Un BROKER leia quien movio
     * que encargo, desde que agente, hacia cual y <b>con que motivo</b> en
     * equipos que no supervisa — y el motivo es texto libre de gobierno.
     *
     * <h2>La interpretacion, dicha para que se pueda vetar</h2>
     * D-P0-6 decide la lectura de historicos de ENCARGO: <i>BROKER, los que
     * estan dentro de su alcance; TENANT_ADMIN, todo el tenant</i>. El rastro de
     * reasignaciones es informacion de gobierno <b>del encargo</b>, asi que se
     * acota con <b>el mismo alcance que ya usa el listado de captaciones</b>
     * —{@code Alcances.de} sobre {@code captacion.id_rol_agente}—, sin inventar
     * una regla nueva.
     *
     * <p><b>El alcance es el del encargo HOY</b>, no el del agente saliente ni
     * el del broker que firmo la reasignacion. Por eso la segunda fila de esta
     * prueba sale del equipo del broker y <b>deja de verse desde el</b>: si se
     * midiera por el saliente, un encargo que ya no lleva su equipo seguiria
     * apareciendo en su rastro para siempre; y si se midiera por quien firmo,
     * una reasignacion de gobierno no la veria <b>ningun</b> broker.
     */
    @Test
    @DisplayName("D-P0-6: el rastro de reasignaciones se acota con el alcance del ENCARGO de hoy")
    void elRastroDeReasignacionesSeAcotaConElAlcanceDelEncargo() {
        Actor deSuEquipo = agenteDelEquipo(0);
        Actor tambienDeSuEquipo = agenteDelEquipo(1);
        Actor brokerDelEquipo = broker();
        Actor gobierno = tenantAdmin();
        Actor destinoDeOtroEquipo = agenteDeOtroEquipo();
        Actor brokerDelOtroEquipo = brokerQueSupervisaA(destinoDeOtroEquipo.idRolOperativo());

        assertNotEquals(brokerDelEquipo.idRolOperativo(), brokerDelOtroEquipo.idRolOperativo(),
                "sin DOS brokers distintos no hay dos alcances que comparar, y la prueba mediria "
                        + "una sola vez lo mismo");

        // (1) DENTRO del equipo, y la firma su broker: el encargo se queda.
        long idEncargoQueSeQueda = unEncargoDe(registrar(deSuEquipo), "A");
        captaciones.reasignar(idEncargoQueSeQueda, tambienDeSuEquipo.idRolOperativo(),
                MOTIVO_DE_REASIGNACION, deSuEquipo.idRolOperativo(), brokerDelEquipo);

        // (2) ENTRE equipos, y la firma el gobierno del tenant (D-S0-17 fila 6:
        //     reasignar entre equipos es organigrama). El encargo ACABA en el
        //     equipo de al lado, que es lo que decide quien lo ve.
        long idEncargoQueSeVa = unEncargoDe(registrar(deSuEquipo), "A");
        captaciones.reasignar(idEncargoQueSeVa, destinoDeOtroEquipo.idRolOperativo(),
                MOTIVO_DE_REASIGNACION, deSuEquipo.idRolOperativo(), gobierno);

        List<Long> delBroker = encargosDelRastro(brokerDelEquipo);
        assertTrue(delBroker.contains(idEncargoQueSeQueda),
                "control positivo: el encargo que hoy lleva un agente que supervisa SI esta en "
                        + "su rastro, o lo de abajo pasaria en un sistema que no devuelve nada");
        assertFalse(delBroker.contains(idEncargoQueSeVa),
                "y el que hoy lleva un agente de otro equipo NO: el rastro dice desde que agente, "
                        + "hacia cual y con que motivo, y ese motivo es texto de gobierno sobre un "
                        + "equipo que no es el suyo");

        List<Long> delBrokerVecino = encargosDelRastro(brokerDelOtroEquipo);
        assertTrue(delBrokerVecino.contains(idEncargoQueSeVa),
                "y el broker que hoy lo lleva SI lo ve, aunque la reasignacion no la firmara el: "
                        + "el alcance es el del encargo actual, no el de quien la firmo");
        assertFalse(delBrokerVecino.contains(idEncargoQueSeQueda),
                "y no gana el del equipo de al lado: la simetria se mide en los dos sentidos, o "
                        + "'no ve nada' pasaria por 've lo suyo'");

        List<Long> delGobierno = encargosDelRastro(gobierno);
        assertTrue(delGobierno.contains(idEncargoQueSeQueda),
                "el TENANT_ADMIN ve todo el tenant: el expediente de traspasos es gobierno (C2) y "
                        + "el del encargo tambien");
        assertTrue(delGobierno.contains(idEncargoQueSeVa));
        assertTrue(delGobierno.indexOf(idEncargoQueSeVa) < delGobierno.indexOf(idEncargoQueSeQueda),
                "y el ORDEN no cambia: el mas reciente primero, que es lo que ya publicaba el "
                        + "cable. Acotar quien lee no reordena lo que lee");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> captaciones.listarReasignaciones(tambienDeSuEquipo),
                "un AGENTE no llega al rastro, tampoco al de un encargo suyo: el controlador lo "
                        + "declara con @PreAuthorize(\"hasAnyRole('BROKER','TENANT_ADMIN')\") y el "
                        + "Core tiene que decir lo mismo, o KAIROS y cualquier consumidor que no "
                        + "pase por HTTP entran por debajo de la anotacion");
    }

    /** Los encargos del rastro que ese actor recibe, en el orden en que llegan. */
    private List<Long> encargosDelRastro(Actor actor) {
        return captaciones.listarReasignaciones(actor).stream()
                .map(CaptacionService.FichaReasignacion::idCaptacion)
                .toList();
    }

    // ==================================================================
    // 5. La fuga medida: /locales/{id}/precios
    // ==================================================================

    /**
     * <b>ROJO PRIMERO, y medido contra el codigo sin tocar.</b>
     *
     * <p>{@code LocalesController.listarPrecios} llamaba a
     * {@code precios.listarPorLocal(id)} <b>sin actor</b>, y el servicio
     * consultaba {@code precio_propiedad} por {@code id_propiedad} <b>sin
     * tenant</b>. Cualquier usuario autenticado de otra corredora leia la serie
     * economica completa de cualquier propiedad sabiendo su id.
     *
     * <p>La fila 112 de la matriz decia «coleccion hija: se alcanza por el id
     * del padre, que si va filtrado por tenant», y el padre <b>no se cargaba</b>:
     * describia una proteccion que no existia.
     */
    @Test
    @DisplayName("D-P0-6/fuga: /locales/{id}/precios de otro tenant es INEXISTENTE")
    void laSerieDePreciosNoCruzaLaFronteraDeTenant() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        assertFalse(precios.listarPorLocal(idPropiedad, duena).isEmpty(),
                "control positivo: su responsable si lee la serie, o lo de abajo no mide nada");

        Actor deOtraCorredora = agenteDelTenantVecino();
        assertNotEquals(duena.idOrganizacion(), deOtraCorredora.idOrganizacion());

        assertThrows(NoEncontradoException.class,
                () -> precios.listarPorLocal(idPropiedad, deOtraCorredora),
                "la serie economica de una propiedad de otra corredora responde INEXISTENTE, no "
                        + "vacio: vacio afirmaria que esa propiedad existe y no tiene precios");
    }

    /**
     * <b>El hito sin encargo (legado) sigue la regla de la PROPIEDAD.</b>
     *
     * <p>Medidos el 2026-09-01: 8 hitos sin {@code id_captacion} en la base de
     * desarrollo y 102 en la de pruebas. No tienen episodio al que atribuirse,
     * asi que no se les puede aplicar la regla del encargo — y <b>no se les
     * inventa uno</b> para poder clasificarlos. La respuesta se toma de la otra
     * fila de la tabla: los lee quien puede leer la historia de la propiedad.
     */
    @Test
    @DisplayName("D-P0-6: un hito SIN encargo lo lee el responsable, no un agente cualquiera")
    void elHitoLegadoSinEncargoSigueLaReglaDeLaPropiedad() {
        Actor duena = agenteDelEquipo(0);
        Actor ajena = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);

        Long idHito = insertarHitoSinEncargo(idPropiedad, duena.idOrganizacion());
        try {
            assertTrue(precios.listarPorLocal(idPropiedad, duena).stream()
                            .anyMatch(hito -> idHito.equals(hito.id())),
                    "el responsable de la propiedad lee el hito huerfano: sin encargo, la "
                            + "autoridad que queda es la del inmueble");
            assertTrue(precios.listarPorLocal(idPropiedad, broker()).stream()
                            .anyMatch(hito -> idHito.equals(hito.id())),
                    "y el broker que lo alcanza, tambien");
            assertTrue(precios.listarPorLocal(idPropiedad, ajena).stream()
                            .noneMatch(hito -> idHito.equals(hito.id())),
                    "un agente que no responde por ella, no -- que no tenga encargo no lo "
                            + "convierte en publico");
            assertTrue(precios.listarPorLocal(idPropiedad, tenantAdmin()).isEmpty(),
                    "y el gobierno del tenant tampoco: el hito legado es dato comercial igual "
                            + "que los demas");

            // Y NO entra en la historia agregada por su cuenta: sin episodio no
            // se puede decir de que operacion era, y afirmarlo seria inventarlo.
            assertTrue(propiedades.consultar(idPropiedad, duena).historia().linea().stream()
                            .noneMatch(hito -> hito.idEncargo() == null),
                    "la historia del inmueble no adopta hitos sin encargo: cada cifra tiene que "
                            + "poder devolverte al episodio que la produjo");
        } finally {
            // Se borra SIEMPRE y por clave primaria: es una fila fabricada por
            // esta prueba, y dejarla escrita cambiaria el corpus legado que otras
            // suites miden.
            jdbc.update("delete from precio_propiedad where id_precio = ?", idHito);
        }
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private long registrar(Actor quien) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Caso de lectura historica",
                new Ubicacion("Av. Historica " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("3000"), "PEN",
                        null, null, null, null, null, null, null)),
                null), quien).idPropiedad();
    }

    /**
     * <b>Un encargo de VENTA de otro agente sobre la MISMA propiedad, con su
     * serie propia.</b>
     *
     * <p>El hito se escribe aparte y no lo pone {@code captaciones.registrar}:
     * abrir un encargo por esa via no autoriza ningun importe. Sin el, el bloque
     * llegaria con el historico <b>vacio</b> y las aserciones de esta suite no
     * podrian distinguir «no te corresponde» (nulo) de «no hay nada que ver»
     * (lista vacia), que es justo la diferencia que D-P0-6 introduce.
     *
     * <p>Lo escribe el <b>propio</b> agente del encargo, porque desde V87 el
     * hito lo autoriza quien lo negocio ({@code exigirEdicionDelEncargo}).
     */
    private long abrirVentaCon(long idPropiedad, Actor deQuien) {
        long idVenta = captaciones.registrar(nuevoEncargoDeVenta(idPropiedad, deQuien), deQuien)
                .id();
        precios.registrar(idPropiedad,
                new PrecioLocalService.DatosPrecio("U", "USD", new BigDecimal("400000"),
                        LocalDate.now(), "VENTA"),
                deQuien);
        return idVenta;
    }

    /** Un encargo de VENTA sobre una propiedad que YA existe, del agente dado. */
    private CaptacionService.DatosCaptacion nuevoEncargoDeVenta(long idPropiedad, Actor deQuien) {
        return new CaptacionService.DatosCaptacion(
                "CAP-D6-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusMonths(6),
                null, "Encargo de venta sobre una propiedad existente",
                idPropiedad, deQuien.idRolOperativo(), "VENTA", 3, Boolean.FALSE,
                "VENTA", new BigDecimal("400000"), "USD",
                "P", "V", new BigDecimal("3"), "USD", "I", null);
    }

    /**
     * Un hito ECONOMICO sin encargo, como los que dejo el legado.
     *
     * <h2>Por que en DOS sentencias y no en un INSERT</h2>
     * {@code tg_precio_exige_encargo} es un trigger <b>BEFORE INSERT</b> y
     * rechaza cualquier fila con {@code id_captacion} nulo (V76). <b>Y esa es
     * exactamente la razon por la que las filas legadas existen</b>: se
     * escribieron antes de que el trigger existiera, y el trigger no vigila el
     * UPDATE. Reproducir el estado legado pasa por el mismo camino que lo
     * produjo — insertar con encargo y despues soltarlo—, no por saltarse una
     * regla vigente.
     *
     * <p>Se hace asi y no reutilizando una de las filas legadas de la base
     * porque esas cuelgan de propiedades cuyo responsable esta prueba no
     * controla: mediria la suerte del corpus, no la regla.
     */
    private Long insertarHitoSinEncargo(long idPropiedad, long idOrganizacion) {
        long idEncargo = unEncargoDe(idPropiedad, "A");
        Long idHito = jdbc.queryForObject("""
                insert into precio_propiedad (organizacion_id, id_propiedad, id_captacion,
                                              operacion, hito, moneda, monto, fecha)
                values (?, ?, ?, null, 'U', 'PEN', 1234.00, current_date)
                returning id_precio
                """, Long.class, idOrganizacion, idPropiedad, idEncargo);
        assertNotNull(idHito);
        assertEquals(1, jdbc.update(
                "update precio_propiedad set id_captacion = null where id_precio = ?", idHito),
                "el hito legado tiene que quedar SIN encargo, o esta prueba mediria el caso "
                        + "normal con otro nombre");
        return idHito;
    }

    private static EncargoFicha encargo(FichaPropiedadUniversal ficha, long idEncargo) {
        return ficha.encargos().stream()
                .filter(e -> e.idEncargo() == idEncargo)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "la ficha no trae el encargo " + idEncargo + ": el BLOQUE del encargo "
                                + "siempre viaja; lo que D-P0-6 acota es su historico"));
    }

    // ==================================================================
    // Actores, resueltos contra la base
    // ==================================================================

    private Actor agenteDelEquipo(int indice) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                  join supervision_agente s on s.id_rol_agente = a.id_persona_rol
                                           and s.fecha_fin is null
                 where s.id_rol_broker = ?
                 order by a.id_persona_rol
                """, idBrokerConEquipo());
        assertTrue(filas.size() >= 2,
                "hacen falta DOS agentes del mismo equipo: encontro " + filas.size());
        return actorAgente(filas.get(indice));
    }

    /** Un agente del MISMO tenant supervisado por OTRO broker. */
    private Actor agenteDeOtroEquipo() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                  join supervision_agente s on s.id_rol_agente = a.id_persona_rol
                                           and s.fecha_fin is null
                 where s.id_rol_broker <> ?
                   and r.organizacion_id = (select organizacion_id from persona_rol
                                             where id_persona_rol = ?)
                 order by a.id_persona_rol limit 1
                """, idBrokerConEquipo(), idBrokerConEquipo());
        assertFalse(filas.isEmpty(),
                "hace falta un agente del mismo tenant supervisado por OTRO broker");
        return actorAgente(filas.get(0));
    }

    private static Actor actorAgente(Map<String, Object> fila) {
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    private Long idBrokerConEquipo() {
        Long id = jdbc.queryForObject("""
                select s.id_rol_broker from supervision_agente s
                 where s.fecha_fin is null
                 group by s.id_rol_broker, s.organizacion_id
                having count(*) >= 2
                 order by count(*) desc, s.id_rol_broker limit 1
                """, Long.class);
        assertNotNull(id, "sin un broker que supervise a dos agentes no hay escenario");
        return id;
    }

    private Actor broker() {
        return brokerConId(idBrokerConEquipo());
    }

    private Actor brokerQueNoSupervisaA(long idRolAgente) {
        Long id = jdbc.queryForObject("""
                select b.id_persona_rol
                  from detalle_broker b
                  join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where r.organizacion_id = (select organizacion_id from persona_rol
                                             where id_persona_rol = ?)
                   and not exists (select 1 from supervision_agente s
                                    where s.id_rol_broker = b.id_persona_rol
                                      and s.id_rol_agente = ?
                                      and s.fecha_fin is null)
                   and exists (select 1 from supervision_agente s2
                                where s2.id_rol_broker = b.id_persona_rol
                                  and s2.fecha_fin is null)
                 order by b.id_persona_rol limit 1
                """, Long.class, idRolAgente, idRolAgente);
        assertNotNull(id, "hace falta un BROKER del tenant que NO supervise a ese agente");
        return brokerConId(id);
    }

    /**
     * El BROKER que supervisa HOY a ese agente.
     *
     * <p>Se deriva del agente y no se elige aparte a proposito: el escenario
     * necesita <b>el</b> broker del equipo de destino, y tomar «otro broker
     * cualquiera» dejaria la asercion simetrica pasando por casualidad el dia
     * que las supervisiones del corpus cambien.
     */
    private Actor brokerQueSupervisaA(long idRolAgente) {
        Long id = jdbc.queryForObject("""
                select s.id_rol_broker from supervision_agente s
                 where s.id_rol_agente = ? and s.fecha_fin is null
                 order by s.id_rol_broker limit 1
                """, Long.class, idRolAgente);
        assertNotNull(id, "ese agente tiene que estar supervisado hoy por un broker, o no hay "
                + "equipo de destino que medir");
        return brokerConId(id);
    }

    private Actor brokerConId(long idRolBroker) {
        Map<String, Object> fila = jdbc.queryForList("""
                select b.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_broker b join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where b.id_persona_rol = ?
                """, idRolBroker).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.BROKER);
    }

    /** El gobierno del tenant: misma identidad, otra banda (como en V35). */
    private Actor tenantAdmin() {
        Actor base = broker();
        return new Actor(base.idOrganizacion(), base.idPersona(), base.idRolOperativo(),
                Actor.TENANT_ADMIN);
    }

    // ==================================================================
    // Lecturas directas y el tenant vecino
    // ==================================================================

    private Long unPropietario(Actor actor) {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
    }

    /**
     * El encargo vivo de una operacion. {@code codigoOperacion} es el codigo
     * unitario de la columna —{@code V} o {@code A}—, no la palabra: el
     * vocabulario de {@code captacion.motivo_operacion} es de una letra, y pasar
     * "ALQUILER" devuelve cero filas sin decir por que.
     */
    private long unEncargoDe(long idPropiedad, String codigoOperacion) {
        Long id = jdbc.queryForObject("""
                select id_captacion from captacion
                 where id_propiedad = ? and motivo_operacion = ? and estado <> 'C'
                 order by id_captacion desc limit 1
                """, Long.class, idPropiedad, codigoOperacion);
        assertNotNull(id, "no hay encargo vivo de " + codigoOperacion + " en esa propiedad");
        return id;
    }

    private Actor agenteDelTenantVecino() {
        long idOrganizacion = otroTenant();
        long idRol = unAgenteDe(idOrganizacion);
        Long idPersona = jdbc.queryForObject(
                "select id_persona from persona_rol where id_persona_rol = ?", Long.class, idRol);
        assertNotNull(idPersona);
        return new Actor(idOrganizacion, idPersona, idRol, Actor.AGENTE);
    }

    /**
     * El tenant vecino se <b>construye</b>, no se hereda: las migraciones crean
     * una sola organizacion, asi que depender de que otra suite haya dejado la
     * suya hacia que la frontera se midiera solo cuando alguien pasaba antes.
     * Idempotente por codigo, igual que en
     * {@code AlcanceYGobiernoDeLaAutoridadIntegrationTest}.
     */
    private long otroTenant() {
        List<Long> ids = jdbc.queryForList(
                "select id_organizacion from organizacion where codigo = ?",
                Long.class, CODIGO_TENANT_VECINO);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
                insert into organizacion (codigo, nombre, estado) values (?, ?, 'A')
                returning id_organizacion
                """, Long.class, CODIGO_TENANT_VECINO,
                "Tenant vecino de las pruebas de alcance");
    }

    private long unAgenteDe(long idOrganizacion) {
        List<Long> conDetalle = jdbc.queryForList("""
                select d.id_persona_rol from detalle_agente d
                 where d.organizacion_id = ? order by d.id_persona_rol limit 1
                """, Long.class, idOrganizacion);
        if (!conDetalle.isEmpty()) {
            return conDetalle.get(0);
        }
        long idPersona = unaPersonaDe(idOrganizacion);
        List<Long> soloRol = jdbc.queryForList("""
                select id_persona_rol from persona_rol
                 where organizacion_id = ? and id_persona = ? and tipo_rol = 'AGENTE'
                   and vigencia_hasta is null
                """, Long.class, idOrganizacion, idPersona);
        Long idRol = soloRol.isEmpty()
                ? jdbc.queryForObject("""
                        insert into persona_rol (organizacion_id, id_persona, tipo_rol,
                                                 vigencia_desde)
                        values (?, ?, 'AGENTE', current_date)
                        returning id_persona_rol
                        """, Long.class, idOrganizacion, idPersona)
                : soloRol.get(0);
        jdbc.update("""
                insert into detalle_agente (id_persona_rol, organizacion_id, codigo_agente,
                                            fecha_ingreso, estado_operativo)
                values (?, ?, ?, current_date, 'D')
                """, idRol, idOrganizacion, "AG-VECINO-" + idRol);
        return idRol;
    }

    private long unaPersonaDe(long idOrganizacion) {
        String correo = "vecino-" + idOrganizacion + "@alcance.test";
        List<Long> ids = jdbc.queryForList(
                "select id_persona from persona where organizacion_id = ? and correo = ?",
                Long.class, idOrganizacion, correo);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
                insert into persona (organizacion_id, tipo_persona, tipo_documento,
                                     numero_documento, nombres_o_razon_social, correo, estado)
                values (?, 'N', 'D', ?, ?, ?, 'A')
                returning id_persona
                """, Long.class, idOrganizacion, "9" + (70000000L + idOrganizacion),
                "Agente del tenant vecino", correo);
    }
}
