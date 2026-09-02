package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Hasta donde llega cada banda, y de donde sale la autoridad</b> (C1, C2 y
 * el requisito transversal D).
 *
 * <h2>Las tres preguntas que responde</h2>
 * <ol>
 *   <li><b>C1 — el alcance del traspaso.</b> La frontera del tenant va SIEMPRE
 *       antes que cualquier permiso de rol. Dentro del tenant, el BROKER asigna
 *       solo a quien supervisa; el TENANT_ADMIN a cualquier agente de su
 *       organizacion, <b>aunque sea del equipo de otro broker</b> — exento de la
 *       restriccion de equipo, nunca de la de tenant.</li>
 *   <li><b>C2 — el expediente es gobierno.</b> Lo leen BROKER (en su alcance) y
 *       TENANT_ADMIN (en su tenant). El AGENTE no, <b>y eso incluye al
 *       responsable vigente</b>.</li>
 *   <li><b>D — la autoridad no se declara desde el cliente.</b> Ningun permiso
 *       sale de un rol, un {@code organizacionId}, un {@code idAgente} o un
 *       {@code idPersonaRol} que venga en la peticion.</li>
 * </ol>
 *
 * <h2>Por que D se prueba aqui y no solo en la capa web</h2>
 * Son <b>dos ataques distintos</b> y conviene no mezclarlos:
 * <pre>
 *   ESCALAMIENTO DE ROL   un AGENTE intenta pasar por BROKER
 *   BOLA / IDOR           rol valido, pero cambia el id del objeto, del tenant,
 *                         del agente o del responsable para alcanzar algo que
 *                         no es suyo
 * </pre>
 * El primero lo corta la construccion del {@link Actor}: {@code rolEfectivo} lo
 * resuelve el servidor desde la membresia ({@code EstadoDeAcceso.bandaEfectiva})
 * y {@code idOrganizacion} sale de la sesion, no del cuerpo ni de la query —
 * {@code FiltroAutenticacionJwt} concede <b>una sola</b> authority
 * ({@code ROLE_<banda>}) y no hay {@code RoleHierarchy}. Un JWT con claims
 * manipulados no llega ni a construir un Actor: falla por <b>firma</b>.
 *
 * <p>Lo que queda por probar —y es lo que se prueba aqui— es el <b>segundo</b>:
 * que con un Actor <b>autentico</b>, el caso de uso no conceda nada por los
 * identificadores que si viajan en la peticion. Por eso estas pruebas fabrican
 * el Actor a mano: es la forma de simular exactamente lo que un atacante
 * <b>podria</b> conseguir si la capa web fallara, y comprobar que el servicio
 * <b>tampoco</b> se lo permite. Una prueba que solo pasara por el controlador
 * mediria la anotacion, no la regla.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AlcanceYGobiernoDeLaAutoridadIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    // Se inyecta para poder preguntarle DIRECTAMENTE por la rama que hoy
    // ningun consumidor alcanza: ver `unAgenteNoAlcanzaElInventarioSinDueno`.
    @Autowired Alcances alcances;

    private static final String MOTIVO =
            "Reasignacion por reparto de cartera del trimestre";

    /**
     * El tenant vecino que estas pruebas <b>construyen</b> para medir la
     * frontera de organizacion. Ver {@link #otroTenant()}: las migraciones
     * crean una sola organizacion, asi que heredarlo de otra suite hacia que la
     * frontera solo se midiera cuando alguien hubiera pasado antes.
     */
    private static final String CODIGO_TENANT_VECINO = "ALCANCE-TENANT-VECINO";

    // ==================================================================
    // C1. El alcance del traspaso
    // ==================================================================

    @Test
    @DisplayName("C1: el BROKER solo asigna a los agentes que supervisa")
    void elBrokerNoAsignaFueraDeSuEquipo() {
        Actor duena = agenteDelEquipo(0);
        Actor ajena = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);

        // El broker que NO supervisa al destino. Su banda es correcta y su
        // tenant tambien: lo unico que le falta es el alcance sobre ESE agente.
        Actor brokerDeOtroEquipo = brokerQueNoSupervisaA(ajena.idRolOperativo());

        AccesoNoAutorizadoException fallo = assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, ajena.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), brokerDeOtroEquipo),
                "un BROKER con banda valida no puede poner a responder a un agente que no "
                        + "supervisa: seria alcanzar el equipo de otro broker");
        assertTrue(fallo.getMessage().toLowerCase().contains("supervis"),
                "y el rechazo tiene que decir que es cuestion de supervision, no de banda: "
                        + fallo.getMessage());

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "y no cambio nada: un traspaso rechazado no deja la propiedad a medias");
    }

    @Test
    @DisplayName("C1: el TENANT_ADMIN asigna entre equipos de brokers distintos")
    void elGobiernoDelTenantCruzaLosEquipos() {
        Actor duena = agenteDelEquipo(0);
        Actor deOtroEquipo = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);

        // El caso del titular: en la misma inmobiliaria, el gobierno puede pasar
        // una propiedad del equipo de un broker al equipo de otro.
        var traspaso = propiedades.asignarResponsable(idPropiedad,
                deOtroEquipo.idRolOperativo(), MOTIVO, observado(idPropiedad), tenantAdmin());

        assertEquals(deOtroEquipo.idRolOperativo(), responsableDe(idPropiedad),
                "el TENANT_ADMIN esta exento de la restriccion de EQUIPO: gobierna la "
                        + "organizacion entera, incluidos los equipos de otros brokers");
        assertEquals("TENANT_ADMIN", traspaso.rolActor(),
                "y el expediente dice con que banda se decidio, no con cual se podria haber "
                        + "decidido");
        assertEquals("TRASPASO", traspaso.origen(),
                "es un traspaso, no un alta: la propiedad ya existia");
    }

    @Test
    @DisplayName("C1: la frontera de tenant va antes que el rol, y para las dos bandas")
    void elTenantVaAntesQueElRol() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        Long agenteDeOtraCorredora = unAgenteDeOtraOrganizacion(duena.idOrganizacion());
        assertNotNull(agenteDeOtraCorredora,
                "este gate necesita un agente de OTRA organizacion; sin el no mide la frontera");

        // Ni el gobierno del tenant la cruza. Es la mitad que el titular marco
        // como innegociable: TENANT_ADMIN esta exento del EQUIPO, nunca del
        // TENANT, y PLATFORM_ADMIN no participa en asignaciones comerciales.
        for (Actor quien : List.of(broker(), tenantAdmin())) {
            assertThrows(ReglaNegocioException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, agenteDeOtraCorredora,
                            MOTIVO, observado(idPropiedad), quien),
                    quien.rolEfectivo() + " no puede poner a responder a un agente de otra "
                            + "corredora: la frontera de organizacion va antes que cualquier "
                            + "permiso de rol");
        }
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "y la propiedad sigue igual");
    }

    /**
     * <b>BOLA por el id de la PROPIEDAD.</b> Rol valido, objeto ajeno.
     *
     * <p>El actor es un broker autentico de su tenant; lo unico que cambia es el
     * id que pide. Tiene que comportarse como <b>inexistente</b> — 404 y no
     * 403—, porque un 403 confirmaria que esa propiedad existe en alguna parte.
     */
    @Test
    @DisplayName("D/BOLA: cambiar el id de la propiedad por uno de otro tenant da INEXISTENTE")
    void unaPropiedadDeOtroTenantNoExisteParaEsteBroker() {
        // Ya no admite «si no hay, no mido»: el tenant vecino lo construye la
        // propia prueba (ver otroTenant()), asi que este caso se ejercita
        // SIEMPRE. Antes dependia de que otra suite hubiera dejado su tenant, y
        // contra una base recien migrada este verde no habia mirado nada.
        long ajena = unaPropiedadDeOtroTenant();
        assertThrows(NoEncontradoException.class,
                () -> propiedades.traspasosDe(ajena, broker()),
                "el expediente de una propiedad de otra corredora responde INEXISTENTE");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.asignarResponsable(ajena, agenteDelEquipo(0).idRolOperativo(),
                        MOTIVO, observado(ajena), broker()),
                "y tampoco se le puede asignar responsable");
    }

    /**
     * <b>BOLA por el tenant.</b> El mismo TENANT_ADMIN, otro
     * {@code organizacionId}.
     *
     * <p>Es el ataque que importa de verdad si algun dia el {@code Actor} se
     * construyera con algo que venga del cliente: aqui se fabrica a mano
     * <b>exactamente eso</b> —un gobierno de tenant con la organizacion
     * cambiada— y el resultado tiene que seguir siendo "no existe". Lo consigue
     * porque la consulta filtra por {@code organizacion_id} <b>en el WHERE</b>,
     * no despues.
     */
    @Test
    @DisplayName("D/BOLA: un TENANT_ADMIN con el tenant cambiado no obtiene el recurso")
    void cambiarElTenantNoAbreElExpediente() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        Long otraOrganizacion = otraOrganizacion(duena.idOrganizacion());
        assertNotNull(otraOrganizacion, "hacen falta dos organizaciones para medir esto");

        Actor gobiernoDeOtroTenant = new Actor(otraOrganizacion,
                tenantAdmin().idPersona(), tenantAdmin().idRolOperativo(), Actor.TENANT_ADMIN);

        assertThrows(NoEncontradoException.class,
                () -> propiedades.traspasosDe(idPropiedad, gobiernoDeOtroTenant),
                "un TENANT_ADMIN nunca consulta expedientes de otro tenant, y no lo consigue "
                        + "cambiando la organizacion: el filtro esta en el WHERE");
    }

    // ==================================================================
    // C2. El expediente es superficie de GOBIERNO
    // ==================================================================

    @Test
    @DisplayName("C2: el AGENTE no lee el expediente, NI SIQUIERA el responsable vigente")
    void elResponsableVigenteNoHeredaElExpediente() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        // Que quede claro que responde ella: no es un problema de identificacion.
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        assertTrue(propiedades.consultar(idPropiedad, duena).responsabilidad().puedeEditar(),
                "responde por ella y la edita: lo que sigue no es falta de autoridad sobre el "
                        + "inmueble, es que el expediente responde otra pregunta");

        AccesoNoAutorizadoException fallo = assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, duena),
                "el responsable vigente sabe QUE responde el —se lo dice su ficha— pero no "
                        + "hereda a los responsables anteriores, ni los motivos de cada "
                        + "traspaso, ni las observaciones de gobierno sobre otros agentes");
        assertTrue(fallo.getMessage().toLowerCase().contains("gobierno"),
                "y el motivo lo dice: es informacion de gobierno, no un permiso que le falte "
                        + "sobre su propia propiedad. Dijo: " + fallo.getMessage());

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, agenteDeOtroEquipo()),
                "y otro agente cualquiera, tampoco");
    }

    @Test
    @DisplayName("C2: el BROKER que supervisa al responsable si lo lee; el que no, no")
    void elExpedienteSigueAlAlcanceDeSupervision() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        var expediente = propiedades.traspasosDe(idPropiedad, broker());
        assertEquals(1, expediente.size(),
                "el broker que supervisa a quien responde lee su expediente, y ahi esta la "
                        + "fila del ALTA (V88)");
        assertEquals("ALTA", expediente.get(0).origen());

        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, brokerAjeno),
                "un BROKER valido, del mismo tenant, pero que no supervisa a quien responde "
                        + "por esta propiedad, no alcanza su expediente");
    }

    @Test
    @DisplayName("C2: el TENANT_ADMIN lee el expediente dentro de su tenant")
    void elGobiernoDelTenantLeeElExpediente() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        assertEquals(1, propiedades.traspasosDe(idPropiedad, tenantAdmin()).size(),
                "el gobierno del tenant alcanza cualquier expediente de SU organizacion");
    }

    /**
     * <b>El inventario sin dueno lo gobierna cualquier broker del tenant</b> (C5).
     *
     * <p>Gobernar lo que no tiene responsable es trabajo de broker: es justo lo
     * que tiene que mirar para decidir a quien asignarlo. La regla "sus
     * supervisados vigentes" existe para no cruzar equipos, y sin responsable no
     * hay a quien supervisar — la regla no tiene sobre que aplicarse y el limite
     * vuelve a ser el tenant.
     *
     * <p>Es el caso que hoy manda: medido el 2026-08-30, las <b>26</b>
     * propiedades de {@code dev} estan FALTANTE.
     */
    @Test
    @DisplayName("C5: una propiedad FALTANTE la alcanza CUALQUIER broker de su tenant")
    void elInventarioSinDuenoLoGobiernaCualquierBrokerDelTenant() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        dejarSinResponsable(idPropiedad);

        assertEquals(1, propiedades.traspasosDe(idPropiedad, broker()).size(),
                "el broker que la tenia en su equipo la sigue viendo cuando queda sin dueno");

        // Y el de OTRO equipo tambien: es lo que cambia con C5. Sin responsable
        // no hay equipo que respetar, y ese broker es tan capaz de asignarla
        // como cualquier otro de la casa.
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        assertEquals(1, propiedades.traspasosDe(idPropiedad, brokerAjeno).size(),
                "un broker que NO supervisaba al responsable saliente tambien alcanza el "
                        + "inventario sin dueno de SU tenant: no hay a quien supervisar, asi "
                        + "que la restriccion de equipo no tiene sobre que aplicarse");

        assertEquals(1, propiedades.traspasosDe(idPropiedad, tenantAdmin()).size(),
                "y el gobierno del tenant, sin cambio");
    }

    /**
     * <b>Y el tenant sigue siendo el limite, tambien para lo FALTANTE</b> (C5).
     *
     * <p>La comprobacion que impide que C5 se lea como "el broker alcanza todo
     * lo que no tiene dueno". La frontera va <b>antes</b> que el rol, y responde
     * <b>inexistente</b>: un 403 confirmaria que esa propiedad existe.
     */
    @Test
    @DisplayName("C5: pero una FALTANTE de OTRO tenant sigue siendo inexistente")
    void loFaltanteDeOtroTenantSigueSiendoInexistente() {
        long ajena = unaPropiedadDeOtroTenant();
        jdbc.update("update propiedad set id_rol_responsable = null where id_propiedad = ?", ajena);

        assertThrows(NoEncontradoException.class,
                () -> propiedades.traspasosDe(ajena, broker()),
                "sin dueno y de otra corredora: la frontera de tenant va primero y responde "
                        + "INEXISTENTE, no 403");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.traspasosDe(ajena, tenantAdmin()),
                "y el gobierno de este tenant tampoco cruza a otro");
    }

    /**
     * <b>C5 no abrio de paso la propiedad CON responsable.</b>
     *
     * <p>La otra mitad, y la que evita que la excepcion se coma la regla: lo que
     * cambia es el caso <b>sin dueno</b>. Con dueno, el broker sigue necesitando
     * supervisarlo.
     */
    @Test
    @DisplayName("C5: con responsable, el broker que no lo supervisa sigue sin entrar")
    void conResponsableElAlcanceDeEquipoSigueIntacto() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, brokerAjeno),
                "esta propiedad SI responde ante alguien, y ese alguien no es de su equipo: "
                        + "C5 no toca este caso");

        // Y en cuanto pierde al responsable, el mismo broker si entra. Las dos
        // mitades de C5 en la misma prueba, sobre la misma propiedad.
        dejarSinResponsable(idPropiedad);
        assertEquals(1, propiedades.traspasosDe(idPropiedad, brokerAjeno).size(),
                "sin dueno, el mismo broker que no entraba ahora si: lo que decide no es "
                        + "quien es el broker, es si hay a quien supervisar");
    }

    /**
     * <b>Y `puedeTraspasar` no es la misma pregunta que el expediente.</b>
     *
     * <p>Se comprueba explicitamente porque las dos superficies se tocan y seria
     * facil que una arrastrara a la otra: ofrecer el boton de traspasar no
     * concede leer los motivos por los que la propiedad cambio de manos, ni al
     * reves.
     */
    @Test
    @DisplayName("C5: ver que puedes traspasar no es poder leer el expediente")
    void traspasarYLeerElExpedienteSiguenSiendoDosPreguntas() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        // El responsable vigente: puede editar, NO puede traspasar y NO lee el
        // expediente. Tres respuestas distintas para la misma persona.
        var suya = propiedades.consultar(idPropiedad, duena).responsabilidad();
        assertTrue(suya.puedeEditar(), "responde por ella");
        assertFalse(suya.puedeTraspasar(), "pero no decide quien responde");
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, duena),
                "ni lee el expediente");

        // Un broker de otro equipo: desde C7 (CONTROL, 2026-09-01) la ficha YA
        // NO le ofrece el traspaso, porque la propiedad responde ante alguien
        // que no supervisa y `puedeTraspasar` lleva el alcance del SALIENTE --
        // el mismo predicado que el POST pregunta en `asignar`.
        //
        // Aqui estaba la asimetria que C6 dejo ANOTADA y sin decidir: el cable
        // ofrecia un boton que el POST rechazaba siempre (el POST exigia
        // `alcanzaIncluidoSinDueno(actor, saliente)` y el booleano no). La
        // inversion de esta linea es la decision C7, tomada de forma consciente
        // sobre la respuesta que C5 dejo medida; el POST no cambio ni una
        // guarda, y la excepcion FALTANTE (C5) queda intacta.
        //
        // OJO con lo que esta prueba sigue sosteniendo: puede traspasar y leer
        // el expediente SON dos autorizaciones distintas, una de ficha y otra de
        // comando. Que coincidan en falso para este broker no las funde -- la
        // separacion sigue viva en las tres respuestas distintas del responsable
        // de arriba (edita, no traspasa, no lee).
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        assertFalse(propiedades.consultar(idPropiedad, brokerAjeno).responsabilidad()
                        .puedeTraspasar(),
                "C7: el booleano del cable lleva el alcance del saliente, no solo la banda");
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, brokerAjeno),
                "y aun asi no lee el expediente: son dos preguntas distintas");
    }

    /**
     * <b>C7: la ficha responde quien puede INICIAR el traspaso de ESTA
     * propiedad.</b>
     *
     * <p>Las respuestas del booleano, medidas contra la misma propiedad: el
     * broker del equipo (C7-C), el gobierno del tenant (C7-E, por autoridad de
     * gobierno y no como super-broker), y en FALTANTE cualquier broker del
     * tenant (C7-B). La que falta -- el broker ajeno ante un saliente que no
     * supervisa (C7-D) -- la mide
     * {@code traspasarYLeerElExpedienteSiguenSiendoDosPreguntas}, que es la
     * respuesta que C6 dejo medida y C7 invirtio de forma consciente.
     *
     * <p>Y la mitad que evita que el booleano se lea como un permiso: decir
     * {@code true} NO autoriza el destino. En FALTANTE la ficha lo ofrece y el
     * POST igual rechaza al agente que el broker no supervisa -- lo mismo que
     * mide C6/7, aqui pegado a la ficha que lo ofrecio. `puedeTraspasar`
     * significa "puedes iniciar desde este estado"; la autorizacion del comando
     * sigue siendo el POST, donde se vuelven a comprobar la banda, el saliente
     * y el destino.
     */
    @Test
    @DisplayName("C7: la ficha dice quien puede iniciar el traspaso, y no autoriza el destino")
    void laFichaDiceQuienPuedeIniciarElTraspaso() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);

        // C7-C: supervisa al responsable vigente -> puede iniciarla.
        assertTrue(propiedades.consultar(idPropiedad, broker()).responsabilidad()
                        .puedeTraspasar(),
                "el broker del equipo puede iniciar el traspaso de una propiedad cuyo "
                        + "responsable si supervisa");

        // C7-E y C7-I: el gobierno del tenant, por AUTORIDAD DE GOBIERNO. Puede
        // gobernar quien responde -- entre equipos, si hace falta -- y NO gana
        // por eso la edicion de los hechos: gobernar no es registrar.
        var delGobierno = propiedades.consultar(idPropiedad, tenantAdmin()).responsabilidad();
        assertTrue(delGobierno.puedeTraspasar(),
                "el TENANT_ADMIN gobierna el cambio de responsable de SU tenant");
        assertFalse(delGobierno.puedeEditar(),
                "y no por eso escribe los hechos de la propiedad ni gana autoridad comercial: "
                        + "no es un super-broker");

        // C7-B: sin dueno no hay saliente a quien supervisar, asi que la ficha
        // lo ofrece a cualquier broker del tenant -- incluido uno que no
        // supervisaba a quien la llevaba (C5 intacta).
        dejarSinResponsable(idPropiedad);
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        assertTrue(propiedades.consultar(idPropiedad, brokerAjeno).responsabilidad()
                        .puedeTraspasar(),
                "FALTANTE: puede iniciarla cualquier broker del tenant");

        // C7-G: ese true NO autoriza el destino. La ficha dijo true y el POST
        // igual rechaza a un agente que ese broker no supervisa.
        Actor destinoAjeno = unAgenteQueNoSupervisa(brokerAjeno, duena.idRolOperativo());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destinoAjeno.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), brokerAjeno),
                "puedeTraspasar=true no sustituye la autorizacion del POST");
        assertNull(responsableDe(idPropiedad), "y la propiedad sigue FALTANTE");
    }

    /**
     * <b>«De nadie» no es «de todos»: un AGENTE no alcanza el inventario sin
     * dueno</b> (C5, la tercera fila de la tabla).
     *
     * <h2>Por que hace falta, y por que en dos niveles</h2>
     * {@code Alcances.alcanzaIncluidoSinDueno} responde {@code !esAgente()}
     * cuando no hay dueno. Esa rama <b>no la alcanza hoy ningun consumidor</b>:
     * los dos —{@code exigirLecturaDelExpediente} y {@code asignar}— cortan al
     * agente por banda <b>antes</b> de preguntar. Consecuencia medida:
     * sustituir ese {@code !esAgente()} por {@code return true} <b>no ponia
     * ninguna prueba en rojo</b>. Una rama defensiva sin red es una rama que el
     * dia que alguien la simplifique "porque no la cubre nada" se lleva por
     * delante la mitad de C5 en silencio.
     *
     * <p>Por eso se mide en los dos niveles, y los dos hacen falta:
     * <ol>
     *   <li>el <b>comportamiento</b>, que es lo que le pasa al usuario: un
     *       agente no lee el expediente de una FALTANTE. Esto lo sostiene la
     *       guarda de banda, no la rama;</li>
     *   <li>la <b>rama</b>, preguntandole a {@code Alcances} directamente. Es
     *       el unico sitio donde {@code return true} se pone rojo.</li>
     * </ol>
     * Con solo el primero, la rama seguiria sin red; con solo el segundo, se
     * estaria fijando una funcion sin decir que protege.
     */
    @Test
    @DisplayName("C5: un AGENTE no alcanza el inventario sin dueno, ni por la puerta de atras")
    void unAgenteNoAlcanzaElInventarioSinDueno() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        dejarSinResponsable(idPropiedad);

        // 1. El comportamiento. Ni quien la registro, ni un agente cualquiera.
        for (Actor quien : List.of(duena, agenteDeOtroEquipo())) {
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.traspasosDe(idPropiedad, quien),
                    "una propiedad sin responsable no pasa a ser de todos: el expediente sigue "
                            + "siendo superficie de gobierno, y perder al dueno no lo abre");
        }
        // Control positivo: el broker SI entra en esta misma propiedad, asi que
        // la negacion de arriba es por banda y no porque nadie alcance nada.
        assertEquals(1, propiedades.traspasosDe(idPropiedad, broker()).size(),
                "y el broker si la alcanza: es inventario sin dueno de su tenant (C5)");

        // 2. La rama, preguntada donde vive. Esto es lo unico que se pone rojo
        //    si `alcanzaIncluidoSinDueno` pasa a devolver `true` sin mirar.
        assertFalse(alcances.alcanzaIncluidoSinDueno(duena, null),
                "sin dueno, un AGENTE no alcanza. Si esto pasa a ser cierto, la excepcion de C5 "
                        + "deja de ser «lo gobierna el broker» y se convierte en «es de todos», "
                        + "que es justo lo que NO se decidio");
        assertTrue(alcances.alcanzaIncluidoSinDueno(broker(), null),
                "el broker si, o la asercion de arriba se cumpliria con una funcion que niega "
                        + "siempre");
        assertTrue(alcances.alcanzaIncluidoSinDueno(tenantAdmin(), null),
                "y el gobierno del tenant tambien");
    }

    // ==================================================================
    // C6. Un traspaso tiene DOS extremos, y los dos se comprueban
    // ==================================================================

    /**
     * <b>El caso permitido, primero.</b>
     *
     * <p>Va delante de las cinco negaciones a proposito: sin el, todas ellas
     * seguirian en verde en un sistema que no dejara traspasar <b>a nadie</b>, y
     * una regla que niega siempre no es la regla que se decidio.
     */
    @Test
    @DisplayName("C6/1: supervisa al SALIENTE y al DESTINO -> traspasa")
    void c6ElBrokerTraspasaDentroDeSuEquipo() {
        Actor saliente = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        long idPropiedad = registrar(saliente);

        propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                observado(idPropiedad), broker());

        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad),
                "los dos extremos estan en su equipo: es un movimiento interno, y ese si lo "
                        + "decide el broker del equipo");
    }

    /**
     * <b>Supervisa el DESTINO pero no el SALIENTE.</b> Es exactamente el hueco
     * que C6 cierra, y el unico de los ocho casos que <b>pasaba</b> antes de
     * este corte: comprobar solo a donde va la propiedad dejaba abierta la
     * puerta de donde sale, asi que un broker podia <b>sacarla del equipo de
     * otro</b> con solo elegir un destino suyo.
     */
    @Test
    @DisplayName("C6/2: supervisa el DESTINO pero NO el SALIENTE -> denegado")
    void c6SupervisarSoloElDestinoNoBasta() {
        Actor salienteAjeno = agenteDeOtroEquipo();
        long idPropiedad = registrar(salienteAjeno);

        Actor brokerPropio = brokerQueNoSupervisaA(salienteAjeno.idRolOperativo());
        Actor destinoSuyo = unSupervisadoDe(brokerPropio);

        AccesoNoAutorizadoException fallo = assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destinoSuyo.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), brokerPropio),
                "sacar una propiedad del equipo de otro broker es un traspaso ENTRE equipos, y "
                        + "eso no lo decide un broker por elegir un destino propio");
        assertTrue(fallo.getMessage().contains("Sacarla de su equipo"),
                "y el rechazo tiene que hablar del SALIENTE, no del destino: los dos mensajes "
                        + "dicen «supervisas», asi que sin esta comprobacion la prueba no "
                        + "distinguiria cual de las dos guardas actuo. Dijo: "
                        + fallo.getMessage());

        assertEquals(salienteAjeno.idRolOperativo(), responsableDe(idPropiedad),
                "y no cambio nada: un traspaso rechazado no deja la propiedad a medias");
    }

    /**
     * <b>Supervisa el SALIENTE pero no el DESTINO.</b> La otra mitad, ya cubierta
     * por C1 y repetida aqui a proposito: las dos negaciones tienen que seguir
     * siendo ciertas <b>a la vez</b>, o cerrar una habria abierto la otra.
     */
    @Test
    @DisplayName("C6/3: supervisa el SALIENTE pero NO el DESTINO -> denegado")
    void c6SupervisarSoloElSalienteNoBasta() {
        Actor saliente = agenteDelEquipo(0);
        Actor destinoAjeno = agenteDeOtroEquipo();
        long idPropiedad = registrar(saliente);

        AccesoNoAutorizadoException fallo = assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destinoAjeno.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), broker()),
                "meter en la cartera de otro equipo sigue siendo alcanzar el equipo de otro");
        assertTrue(fallo.getMessage().contains("ponerlo a responder"),
                "y aqui el rechazo es el del DESTINO: " + fallo.getMessage());

        assertEquals(saliente.idRolOperativo(), responsableDe(idPropiedad));
    }

    /**
     * <b>Ni el uno ni el otro.</b> El caso que no aporta un mecanismo nuevo pero
     * si descarta el fallo mas tonto: que las dos guardas se anularan entre si.
     */
    @Test
    @DisplayName("C6/4: no supervisa a NINGUNO de los dos -> denegado")
    void c6NoSupervisarANingunoDeLosDos() {
        Actor salienteAjeno = agenteDeOtroEquipo();
        long idPropiedad = registrar(salienteAjeno);

        Actor brokerPropio = brokerQueNoSupervisaA(salienteAjeno.idRolOperativo());
        // Destino: otro agente que ESE broker tampoco supervisa. Se resuelve
        // contra la base, no a mano: fijarlo haria que la prueba dejara de medir
        // la regla el dia que cambie el organigrama de la semilla.
        Actor destinoAjeno = unAgenteQueNoSupervisa(brokerPropio, salienteAjeno.idRolOperativo());

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destinoAjeno.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), brokerPropio),
                "sin ninguno de los dos extremos en su equipo no queda nada que lo autorice");

        assertEquals(salienteAjeno.idRolOperativo(), responsableDe(idPropiedad));
    }

    @Test
    @DisplayName("C6/5: el TENANT_ADMIN cruza equipos DENTRO de su tenant -> permitido")
    void c6ElGobiernoDelTenantCruzaEquipos() {
        Actor saliente = agenteDelEquipo(0);
        Actor destinoAjeno = agenteDeOtroEquipo();
        long idPropiedad = registrar(saliente);

        // Control: para el broker del equipo del saliente, este mismo traspaso
        // esta cerrado. Sin esta linea, el verde de abajo podria significar
        // «cualquiera puede» en vez de «el gobierno del tenant puede».
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destinoAjeno.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), broker()));

        propiedades.asignarResponsable(idPropiedad, destinoAjeno.idRolOperativo(),
                MOTIVO, observado(idPropiedad), tenantAdmin());

        assertEquals(destinoAjeno.idRolOperativo(), responsableDe(idPropiedad),
                "un traspaso entre equipos es exactamente lo que el gobierno del tenant existe "
                        + "para decidir; esta exento del EQUIPO, nunca del TENANT");
    }

    /**
     * <b>C6 x C5: el broker saca de FALTANTE hacia su propio equipo.</b>
     *
     * <p>Es la excepcion congelada y su limite en una sola prueba: sin saliente
     * no hay equipo que respetar, asi que gobierna cualquier broker del
     * tenant — y el destino sigue siendo cosa suya.
     */
    @Test
    @DisplayName("C6/6: el BROKER asigna una FALTANTE a un supervisado suyo -> permitido")
    void c6ElBrokerAsignaLaFaltanteDentroDeSuEquipo() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        dejarSinResponsable(idPropiedad);

        // Un broker que NO supervisaba a la saliente. Antes de dejarla sin
        // responsable este mismo traspaso estaria cerrado para el (C6/2).
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        Actor suyo = unSupervisadoDe(brokerAjeno);

        propiedades.asignarResponsable(idPropiedad, suyo.idRolOperativo(), MOTIVO,
                observado(idPropiedad), brokerAjeno);

        assertEquals(suyo.idRolOperativo(), responsableDe(idPropiedad),
                "gobernar el inventario sin dueno es trabajo de broker, y sacarlo de FALTANTE "
                        + "es justo el acto para el que lo mira");
    }

    @Test
    @DisplayName("C6/7: el BROKER NO asigna una FALTANTE fuera de su equipo -> denegado")
    void c6ElBrokerNoAsignaLaFaltanteFueraDeSuEquipo() {
        Actor duena = agenteDelEquipo(0);
        Actor ajena = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);
        dejarSinResponsable(idPropiedad);

        Actor brokerAjeno = brokerQueNoSupervisaA(ajena.idRolOperativo());
        // CONTROL POSITIVO: la alcanza. Sin esto, la negacion de abajo podria
        // deberse a que ese broker nunca entro a esta propiedad.
        assertEquals(1, propiedades.traspasosDe(idPropiedad, brokerAjeno).size(),
                "la alcanza: es inventario sin dueno de SU tenant");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, ajena.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), brokerAjeno),
                "la excepcion FALTANTE abre QUE propiedades gobierna, no A QUIEN puede "
                        + "entregarlas: mirar el inventario sin dueno no es repartirlo");

        assertNull(responsableDe(idPropiedad),
                "y sigue FALTANTE: un traspaso rechazado no la deja a medias");
    }

    /**
     * <b>Otro tenant: nunca, y como recurso inexistente.</b>
     *
     * <p>La frontera va delante de las dos comprobaciones de C6 y <b>no se
     * levanta porque las supervisiones del actor esten en regla</b>. Responde
     * INEXISTENTE y no 403, porque un 403 ya confirmaria que esa propiedad
     * existe en alguna parte.
     */
    @Test
    @DisplayName("C6/8: otro tenant, NUNCA, y como recurso inexistente")
    void c6OtroTenantNuncaYComoInexistente() {
        long ajena = unaPropiedadDeOtroTenant();
        Actor destino = agenteDelEquipo(0);

        for (Actor quien : List.of(broker(), tenantAdmin())) {
            assertThrows(NoEncontradoException.class,
                    () -> propiedades.asignarResponsable(ajena, destino.idRolOperativo(),
                            MOTIVO, observado(ajena), quien),
                    quien.rolEfectivo() + " no traspasa una propiedad de otra corredora, y la "
                            + "respuesta es INEXISTENTE: la frontera de tenant va antes que "
                            + "cualquier alcance de equipo y antes que el gobierno");
        }
    }

    /**
     * <b>Una supervision EXPIRADA cierra el traspaso, y lo cierra por los DOS
     * extremos.</b>
     *
     * <h2>Que protege</h2>
     * El alcance del broker no es «los agentes de su equipo alguna vez» sino
     * «los que supervisa <b>hoy</b>»: {@code SupervisionAgenteRepository
     * .agentesSupervisados} filtra por {@code fechaFin is null}, y de ahi salen
     * las tres respuestas que se miden aqui —el booleano de la ficha (C7), la
     * lectura del expediente (C2) y las dos guardas del POST (C6)—. Esa mitad
     * <b>no la medía nada</b>: ninguna prueba del arbol escribia
     * {@code supervision_agente.fecha_fin}, asi que quitar ese filtro dejaba la
     * suite entera en verde y convertia cada supervision cerrada en un permiso
     * que no caduca. Es la diferencia entre «fue de su equipo» y «es de su
     * equipo», y la unica que un ex-supervisor nota.
     *
     * <h2>Por que se restituye SIEMPRE</h2>
     * El organigrama es escenario <b>compartido</b>: {@code broker()},
     * {@code agenteDelEquipo(i)} y {@code tenantAdmin()} se resuelven
     * consultando las supervisiones <b>abiertas</b>, y {@code idBrokerConEquipo()}
     * exige un broker con dos o mas. Una fila que se quedara cerrada no rompería
     * esta prueba sino las <b>siguientes</b>, con un mensaje que no habla de
     * esto. Por eso el cierre vive en un {@code try} cuyo {@code finally} la
     * reabre, y por eso los tres actores se capturan <b>antes</b>: dentro de la
     * ventana no se llama a ningun ayudante que vuelva a consultar el
     * organigrama.
     */
    @Test
    @DisplayName("C6: una supervision EXPIRADA cierra el traspaso por los dos extremos")
    void c6LaSupervisionExpiradaCierraElTraspaso() {
        Actor b = broker();
        Actor a1 = agenteDelEquipo(1);
        Actor a0 = agenteDelEquipo(0);
        long p1 = registrar(a1);
        long p0 = registrar(a0);

        // CONTROL POSITIVO con la supervision VIGENTE. Sin el, los cuatro «no
        // puede» de abajo podrian deberse a que ese broker nunca pudo.
        assertTrue(propiedades.consultar(p1, b).responsabilidad().puedeTraspasar(),
                "con la supervision vigente la ficha si le ofrece el traspaso (C7)");
        assertEquals(1, propiedades.traspasosDe(p1, b).size(),
                "y si lee su expediente: responde ante un agente que supervisa hoy");

        // La fila viva se identifica ANTES de tocarla, y por su CLAVE PRIMARIA:
        // es la unica referencia que no depende de la fecha. Se captura fuera
        // del `try` a proposito -- si no hubiera fila que cerrar, el `finally`
        // no debe entrar a restituir con un id nulo y tapar el error real.
        Long idSupervision = jdbc.queryForObject("""
                select id_supervision from supervision_agente
                 where id_rol_broker = ? and id_rol_agente = ? and fecha_fin is null
                """, Long.class, b.idRolOperativo(), a1.idRolOperativo());
        assertNotNull(idSupervision,
                "el escenario compartido tiene que traer una supervision VIVA de ese broker "
                        + "sobre ese agente: sin ella no hay nada que expirar y esta prueba no "
                        + "mediria lo que dice medir");

        try {
            int cerradas = jdbc.update("""
                    update supervision_agente set fecha_fin = current_date
                     where id_supervision = ? and fecha_fin is null
                    """, idSupervision);
            assertEquals(1, cerradas,
                    "esta prueba tiene que cerrar EXACTAMENTE una supervision viva: si cerro "
                            + "cero, el escenario no era el que dice medir");

            assertFalse(propiedades.consultar(p1, b).responsabilidad().puedeTraspasar(),
                    "supervision cerrada es supervision que ya no alcanza: la ficha no puede "
                            + "seguir ofreciendo un traspaso que el POST va a negar");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.traspasosDe(p1, b),
                    "ni el expediente de un agente al que ya no supervisa");

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(p1, a0.idRolOperativo(), MOTIVO,
                            observado(p1), b),
                    "el SALIENTE ya no esta supervisado, asi que sacarla de ahi es un traspaso "
                            + "entre equipos aunque el destino siga siendo suyo");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(p0, a1.idRolOperativo(), MOTIVO,
                            observado(p0), b),
                    "y el DESTINO tampoco: la vigencia cierra los DOS extremos, no solo el que "
                            + "se mira primero");

            assertEquals(a1.idRolOperativo(), responsableDe(p1),
                    "un traspaso rechazado no deja la propiedad a medias");
            assertEquals(a0.idRolOperativo(), responsableDe(p0));
        } finally {
            // Restituir SIEMPRE, tambien si una asercion fallo, y por CLAVE
            // PRIMARIA: filtrar por `fecha_fin = current_date` ataba la
            // restitucion al reloj -- una corrida que cruzara la medianoche
            // entre el cierre y este `finally` no restituiria nada, en silencio
            // y con la suite en verde, y dejaria la supervision cerrada para las
            // pruebas SIGUIENTES, que se apoyan en el mismo organigrama.
            // `uq_supervision_activa` es parcial por AGENTE y a este no le queda
            // ninguna otra abierta, asi que reabrir esta no choca con nada.
            jdbc.update("update supervision_agente set fecha_fin = null where id_supervision = ?",
                    idSupervision);
        }

        assertTrue(propiedades.consultar(p1, b).responsabilidad().puedeTraspasar(),
                "restituida la vigencia vuelve a poder, y eso prueba dos cosas: que la "
                        + "restitucion funciono y que lo que decide es la VIGENCIA de la "
                        + "supervision, no la identidad del broker");
    }

    // ------------------------------------------------------------------
    // C6 x C5. La excepcion no se estrecho ni se ensancho
    // ------------------------------------------------------------------

    /**
     * <b>C5 sigue diciendo exactamente lo mismo, en sus cuatro respuestas.</b>
     *
     * <p>C6 toca la misma pregunta —el alcance del broker sobre el
     * responsable— asi que la forma de comprobar que no la desplazo es medir
     * las cuatro respuestas de C5 <b>sobre la misma propiedad</b>: sin dueno
     * entra cualquier broker del tenant, con dueno solo quien lo supervisa, el
     * gobierno del tenant siempre, y otro tenant nunca.
     */
    @Test
    @DisplayName("C6 x C5: la excepcion FALTANTE no se estrecho ni se ensancho")
    void c6NoEstrechoNiEnsanchoC5() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());

        // 1. CON responsable: solo quien lo supervisa. C5 no se ensancho.
        assertEquals(1, propiedades.traspasosDe(idPropiedad, broker()).size());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, brokerAjeno),
                "con responsable manda el EQUIPO: si C6 hubiera ensanchado C5, este broker "
                        + "entraria");

        // 2. SIN responsable: cualquier broker del tenant. C5 no se estrecho.
        dejarSinResponsable(idPropiedad);
        assertEquals(1, propiedades.traspasosDe(idPropiedad, brokerAjeno).size(),
                "sin dueno no hay a quien supervisar, asi que la regla de equipo no tiene "
                        + "sobre que aplicarse. Si C6 hubiera estrechado C5, esto seria 403");

        // 3. El gobierno del tenant, en los dos estados. Sin cambio.
        assertEquals(1, propiedades.traspasosDe(idPropiedad, tenantAdmin()).size());

        // 4. Y el tenant sigue siendo el limite tambien para lo FALTANTE.
        long ajena = unaPropiedadDeOtroTenant();
        jdbc.update("update propiedad set id_rol_responsable = null where id_propiedad = ?", ajena);
        assertThrows(NoEncontradoException.class,
                () -> propiedades.traspasosDe(ajena, broker()),
                "sin dueno y de otra corredora: INEXISTENTE, no 403");
    }

    /**
     * <b>Y la excepcion se cierra sola al asignar.</b>
     *
     * <p>Las otras pruebas miden los dos <b>estados</b>. Esta mide el
     * <b>transito</b> entre ellos, que es donde una excepcion mal cerrada se
     * queda pegada: el broker que entro <i>porque</i> no habia dueno tiene que
     * dejar de entrar en cuanto lo hay, <b>en las dos superficies</b> —leer el
     * expediente y traspasar— y sin que nadie ejecute nada mas.
     */
    @Test
    @DisplayName("C6 x C5: asignada la FALTANTE, la excepcion desaparece y vuelve el EQUIPO")
    void c6LaExcepcionFaltanteSeCierraAlAsignar() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        dejarSinResponsable(idPropiedad);

        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());
        Actor suyo = unSupervisadoDe(brokerAjeno);

        // CONTROL POSITIVO, mientras esta FALTANTE: la lee y la puede traspasar.
        assertEquals(1, propiedades.traspasosDe(idPropiedad, brokerAjeno).size(),
                "entra por C5: no hay a quien supervisar");

        // El broker que SI supervisa a la agente se la asigna a ella.
        propiedades.asignarResponsable(idPropiedad, duena.idRolOperativo(), MOTIVO,
                observado(idPropiedad), broker());
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));

        // Y a partir de aqui, el mismo broker ajeno ya no entra por ninguna de
        // las dos puertas.
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, brokerAjeno),
                "en cuanto hay responsable vuelve a mandar el EQUIPO. Si siguiera entrando, C5 "
                        + "no seria una excepcion por ausencia de dueno: seria alcance de tenant "
                        + "para cualquier broker, que es justo lo que NO se decidio");
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, suyo.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), brokerAjeno),
                "y tampoco la traspasa ya, ni siquiera a uno de los suyos: ahora hay saliente, "
                        + "y no lo supervisa");

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "y la propiedad se quedo donde el broker de su equipo la puso");
    }

    // ==================================================================
    // D. La autoridad no se declara desde el cliente
    // ==================================================================

    /**
     * <b>Escalamiento de rol: un AGENTE que se declara BROKER.</b>
     *
     * <p>La peticion no puede declarar la banda, asi que el ataque solo se puede
     * simular fabricando el {@link Actor} — que es mas de lo que un atacante
     * consigue. Aun asi hay algo que medir, y es lo que se mide: la <b>identidad
     * real</b> del agente (su {@code idPersona} y su {@code idRolOperativo}) con
     * la banda cambiada <b>no</b> le da el alcance de un broker, porque el
     * alcance no sale de la banda: sale de {@code supervision_agente}, que es
     * una tabla del servidor y no un campo de la peticion.
     */
    @Test
    @DisplayName("D/escalamiento: decirse BROKER no concede el alcance de un broker")
    void llamarseBrokerNoDaElAlcanceDeUnBroker() {
        Actor duena = agenteDelEquipo(0);
        Actor ajena = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);

        // El agente, con SU identidad, diciendo que es BROKER.
        Actor fingido = new Actor(duena.idOrganizacion(), duena.idPersona(),
                duena.idRolOperativo(), Actor.BROKER);

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, ajena.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), fingido),
                "un agente no supervisa a nadie: `supervision_agente` no tiene ninguna fila "
                        + "con su rol como broker, asi que cambiar la etiqueta no le da "
                        + "alcance sobre ningun agente");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, fingido),
                "y tampoco le abre el expediente de su propia propiedad: el alcance se "
                        + "consulta contra la tabla de supervision, no contra la banda dicha");
    }

    /**
     * <b>El rol operativo tampoco se puede tomar prestado.</b>
     *
     * <p>Segundo ataque de la familia: mismo agente, pero declarando el
     * {@code idPersonaRol} de un BROKER de verdad. Es lo que ocurriria si algun
     * dia el {@code idRolOperativo} se leyera del cuerpo en vez del token —hoy
     * sale de {@code token().idDominio()}, que va firmado—.
     *
     * <p>Lo interesante es que <b>funcionaria</b>, y por eso la prueba existe:
     * deja escrito, en una comprobacion y no en un comentario, que la unica cosa
     * que impide este ataque es que ese identificador <b>no viaja</b> en la
     * peticion. El dia que alguien lo acepte por parametro, esta prueba es el
     * sitio donde consta lo que se rompe.
     */
    @Test
    @DisplayName("D: el alcance sale del rol operativo del token, y ese no viaja en la peticion")
    void elRolOperativoNoViajaEnLaPeticion() {
        // La comprobacion es estructural: ningun endpoint de este P0 acepta el
        // rol, la organizacion ni el rol operativo por parametro. Se afirma
        // sobre la FIRMA del servicio, que es lo unico que el cliente alcanza --
        // y se lee del PROPIO servicio, no de un literal transcrito: una copia a
        // mano caduca sola, que es la familia de fallos mas cara de este
        // repositorio. Con el literal, anadir `long idOrganizacion` a la firma
        // habria dejado esta prueba verde afirmando que no estaba.
        Map<String, List<Class<?>>> firmas = Map.of(
                "asignarResponsable", List.of(long.class, long.class, String.class,
                        PropiedadUniversalService.ResponsableObservado.class, Actor.class),
                "traspasosDe", List.of(long.class, Actor.class));

        for (Map.Entry<String, List<Class<?>>> esperada : firmas.entrySet()) {
            List<Method> encontrados = Arrays.stream(PropiedadUniversalService.class.getMethods())
                    .filter(metodo -> metodo.getName().equals(esperada.getKey()))
                    .toList();
            // UNA sola firma, y aqui no es manía de limpieza: una sobrecarga de
            // `asignarResponsable` sin el responsable observado seria la misma
            // «ultima escritura gana» entrando por la puerta de atras (D-P0-9).
            assertEquals(1, encontrados.size(),
                    "«" + esperada.getKey() + "» tiene que tener una sola firma. Encontro: "
                            + encontrados);
            Method metodo = encontrados.get(0);
            assertEquals(esperada.getValue(), List.of(metodo.getParameterTypes()),
                    "cambio la firma de " + esperada.getKey() + ": la identidad entra como Actor, "
                            + "que lo construye el servidor, y ni el rol, ni la banda, ni la "
                            + "organizacion viajan como parametro desde el cliente");
            assertEquals(Actor.class, metodo.getParameterTypes()[metodo.getParameterCount() - 1],
                    "y el Actor va el ultimo, como en todo el servicio");
        }

        // Y el unico identificador que SI viaja -- el agente destino -- esta
        // acotado por tenant y por alcance, que es justo lo que prueban las de
        // C1. Aqui se deja constancia de por que ese es el unico que puede
        // viajar: nombra el destino del hecho, no quien lo autoriza.
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, duena.idRolOperativo(),
                        MOTIVO, observado(idPropiedad), duena),
                "y un AGENTE no se traspasa la propiedad a si mismo aunque nombre su propio "
                        + "id: la autoridad seria autoservicio");
    }

    // ==================================================================
    // H6. El motivo del traspaso pesa lo mismo que el de la reasignacion
    // ==================================================================

    @Test
    @DisplayName("H6: el motivo del traspaso exige el mismo minimo que reasignar un encargo")
    void elMotivoDelTraspasoNoAdmiteUnOk() {
        Actor duena = agenteDelEquipo(0);
        Actor otra = agenteDelEquipo(1);
        long idPropiedad = registrar(duena);

        for (String pobre : List.of("ok", "x", "   ", "", "cambio")) {
            assertThrows(ReglaNegocioException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, otra.idRolOperativo(),
                            pobre, observado(idPropiedad), broker()),
                    "\"" + pobre + "\" no explica nada, y esta tabla es append-only: nadie la "
                            + "corrige despues. Es el mismo minimo que exige "
                            + "PoliticaComercial.exigirMotivoDeReasignacion para el encargo, "
                            + "que es el mismo tipo de hecho un nivel mas abajo");
        }

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "y ningun rechazo dejo la propiedad traspasada a medias");

        // Y con un motivo de verdad, entra.
        propiedades.asignarResponsable(idPropiedad, otra.idRolOperativo(), MOTIVO,
                observado(idPropiedad), broker());
        assertEquals(otra.idRolOperativo(), responsableDe(idPropiedad));
    }

    // ==================================================================
    // C8 · D-P0-7 / D-P0-8. Quien puede RECIBIR una propiedad
    // ==================================================================
    //
    // D-P0-7: el DESTINO de un traspaso tiene que cumplir CINCO condiciones, y
    // ninguna es un estado nuevo -- cada una es la autoridad que ya decide ese
    // hecho hoy: `persona_rol` (rol vigente), `credencial_usuario` (cuenta
    // habilitada), `usuario_organizacion` (membresia), `detalle_agente`
    // (disponibilidad) y `supervision_agente` (solo si el actor es BROKER).
    //
    // POR QUE SE MIDE UNA POR UNA. Un unico caso "agente invalido" pasaria
    // igual con cuatro de las cinco comprobaciones borradas. Cada prueba apaga
    // EXACTAMENTE una condicion, exige el 403, la restituye por CLAVE PRIMARIA y
    // vuelve a exigir el traspaso -- ese control positivo es lo que demuestra
    // que el rechazo venia de la condicion apagada y no de otra cosa.
    //
    // Y LA RESTITUCION NO ES COSMETICA. El organigrama es escenario COMPARTIDO:
    // `agenteDelEquipo(i)`, `broker()` y `tenantAdmin()` se resuelven
    // consultandolo. Una cuenta que se quedara en 'I' no romperia esta prueba
    // sino las SIGUIENTES, con un mensaje que no habla de esto. Por eso los
    // actores se capturan ANTES de mutar y el censo de las cuatro tablas se
    // compara al final de cada caso.

    /**
     * <b>Cuenta suspendida: no recibe.</b> Y con la cuenta de vuelta, el
     * <b>mismo</b> traspaso entra — que es lo que prueba que el 403 lo produjo
     * la credencial y no otra de las cinco.
     */
    @Test
    @DisplayName("C8/D-P0-7: un destino con la cuenta suspendida no recibe propiedades")
    void c8LaCuentaSuspendidaNoRecibe() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor b = broker();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        Long idCredencial = credencialDe(destino.idRolOperativo());
        assertNotNull(idCredencial,
                "el destino tiene que traer credencial: sin ella esta prueba no apagaria nada");
        try {
            assertEquals(1, jdbc.update("""
                    update credencial_usuario set estado_administrativo = 'I'
                     where id_persona_rol = ? and estado_administrativo = 'A'
                    """, idCredencial),
                    "tiene que suspender EXACTAMENTE una cuenta activa");

            AccesoNoAutorizadoException fallo = assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(),
                            MOTIVO, observado(idPropiedad), b),
                    "una cuenta suspendida no puede empezar a responder por un inmueble: "
                            + "alcanzar a un agente no es que ese agente pueda operar");
            assertTrue(fallo.getMessage().toLowerCase().contains("no puede recibir"),
                    "y el mensaje no dice CUAL de las cinco fallo -- publicar el estado "
                            + "administrativo de una cuenta ajena a quien preguntaba por un "
                            + "traspaso seria dato que no le corresponde. Dijo: "
                            + fallo.getMessage());
            assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                    "y la propiedad no se movio");
        } finally {
            jdbc.update("update credencial_usuario set estado_administrativo = 'A' "
                    + "where id_persona_rol = ?", idCredencial);
        }

        // CONTROL POSITIVO: con la cuenta restituida, el mismo traspaso entra.
        propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                observado(idPropiedad), b);
        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad),
                "restituida la cuenta, el MISMO traspaso funciona: el rechazo era de la "
                        + "credencial y de nada mas");
        assertEquals(censoAntes, censo(), "y las cuatro tablas quedaron como estaban");
    }

    @Test
    @DisplayName("C8/D-P0-7: un destino no disponible no recibe propiedades")
    void c8ElAgenteNoDisponibleNoRecibe() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor b = broker();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        try {
            // 'L' es LICENCIA. El vocabulario de esta columna es D/L/N y no el de
            // `estado`, aunque las tres lleven una sola letra.
            assertEquals(1, jdbc.update("""
                    update detalle_agente set estado_operativo = 'L'
                     where id_persona_rol = ? and estado_operativo = 'D'
                    """, destino.idRolOperativo()));

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(),
                            MOTIVO, observado(idPropiedad), b),
                    "un agente de licencia no empieza a responder por un inmueble mas");
            assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        } finally {
            jdbc.update("update detalle_agente set estado_operativo = 'D' where id_persona_rol = ?",
                    destino.idRolOperativo());
        }

        propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                observado(idPropiedad), b);
        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad),
                "de vuelta a disponible, el mismo traspaso entra");
        assertEquals(censoAntes, censo());
    }

    @Test
    @DisplayName("C8/D-P0-7: un destino sin membresia vigente en la organizacion no recibe")
    void c8SinMembresiaNoRecibe() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor b = broker();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        Long idMembresia = membresiaDe(destino.idRolOperativo());
        assertNotNull(idMembresia,
                "el destino tiene que traer membresia activa: es una de las cinco condiciones y "
                        + "sin ella esta prueba no apagaria nada");
        try {
            assertEquals(1, jdbc.update("""
                    update usuario_organizacion set estado = 'I'
                     where id_usuario_organizacion = ? and estado = 'A'
                    """, idMembresia));

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(),
                            MOTIVO, observado(idPropiedad), b),
                    "sin membresia vigente no entra al tenant, asi que no puede responder por "
                            + "nada suyo. Es la MISMA fuente que ya lee "
                            + "UsuarioOrganizacionRepository.bandaActivaDePersona, no un estado "
                            + "nuevo");
            assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        } finally {
            jdbc.update("update usuario_organizacion set estado = 'A' "
                    + "where id_usuario_organizacion = ?", idMembresia);
        }

        propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                observado(idPropiedad), b);
        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(censoAntes, censo());
    }

    /**
     * <b>Rol de agente cerrado: la respuesta es 403 y NO «no existe».</b>
     *
     * <p>Y esa eleccion esta medida, no supuesta. {@code asignar} resuelve el
     * destino con {@code agentes.findById}, que va por <b>clave primaria</b> y
     * no mira vigencia — asi que la fila de {@code detalle_agente} sigue ahi y el
     * "ese agente no existe en la organizacion" no llega a dispararse. Lo que
     * corta es la elegibilidad, y por eso responde <b>no puede recibir</b>.
     *
     * <p>Es la respuesta correcta: el agente <b>existe</b> —lleva propiedades,
     * sale en el historial, firmo encargos— y lo que ya no esta vigente es su
     * rol. Decir "no existe" ahi seria mentir sobre alguien que si esta en la
     * casa, y ademas contradiria al expediente, que lo sigue nombrando.
     */
    @Test
    @DisplayName("C8/D-P0-7: un destino con el rol de agente cerrado no recibe (403, no 404)")
    void c8ElRolCerradoNoRecibe() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor b = broker();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        try {
            assertEquals(1, jdbc.update("""
                    update persona_rol set vigencia_hasta = current_date
                     where id_persona_rol = ? and vigencia_hasta is null
                    """, destino.idRolOperativo()));

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(),
                            MOTIVO, observado(idPropiedad), b),
                    "un rol de agente cerrado no recibe propiedades nuevas, y la respuesta es de "
                            + "ELEGIBILIDAD: el agente sigue existiendo y el expediente lo sigue "
                            + "nombrando");
            assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        } finally {
            // Por clave primaria y no por `vigencia_hasta = current_date`: atar la
            // restitucion al reloj deja la fila cerrada si la corrida cruza la
            // medianoche, en silencio y con la suite en verde.
            jdbc.update("update persona_rol set vigencia_hasta = null where id_persona_rol = ?",
                    destino.idRolOperativo());
        }

        propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                observado(idPropiedad), b);
        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(censoAntes, censo());
    }

    /**
     * <b>Supervision vigente y agente operativo son invariantes DISTINTAS.</b>
     *
     * <p>Se prueban por separado porque una implementacion que fundiera las dos
     * —«si lo supervisa, es que puede»— pasaria el caso de arriba y dejaria
     * entrar a un supervisado de baja, que es el error caro: el broker cree que
     * la reparte dentro de su equipo y se la da a alguien que no la va a
     * trabajar.
     *
     * <p>La otra mitad —operativo pero de otro equipo— ya la mide
     * {@code elBrokerNoAsignaFueraDeSuEquipo} (C1), y aqui se cita en vez de
     * repetirse: son la misma pregunta con distinto sujeto.
     */
    @Test
    @DisplayName("C8/D-P0-7: supervisado pero de baja tampoco recibe (dos invariantes, no una)")
    void c8SupervisionYDisponibilidadSonDosCosas() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor b = broker();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        // Que quede establecido que SI lo supervisa: el rechazo de abajo no puede
        // atribuirse al alcance de equipo.
        assertTrue(alcances.alcanza(b, destino.idRolOperativo()),
                "el escenario exige que el broker SI supervise al destino, o esta prueba estaria "
                        + "midiendo C1 otra vez");

        try {
            assertEquals(1, jdbc.update("""
                    update detalle_agente set estado_operativo = 'N'
                     where id_persona_rol = ? and estado_operativo = 'D'
                    """, destino.idRolOperativo()));

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(),
                            MOTIVO, observado(idPropiedad), b),
                    "lo supervisa y aun asi no puede recibirla: supervision y disponibilidad son "
                            + "dos invariantes, y fundirlas dejaria entrar al supervisado de baja");
        } finally {
            jdbc.update("update detalle_agente set estado_operativo = 'D' where id_persona_rol = ?",
                    destino.idRolOperativo());
        }
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(censoAntes, censo());
    }

    /**
     * <b>El gobierno del tenant esta exento del EQUIPO, no de la
     * ELEGIBILIDAD.</b>
     *
     * <p>Es la parte de D-P0-7 que separa las dos exenciones: el TENANT_ADMIN no
     * necesita supervisar a nadie —gobierna su organizacion entera, y eso ya lo
     * mide C1— pero el destino tiene que seguir siendo un agente valido y
     * operativo del mismo tenant. Sin esta prueba, «exento de supervisar» podria
     * implementarse como «exento de comprobar», que es una cosa distinta.
     */
    @Test
    @DisplayName("C8/D-P0-7: el TENANT_ADMIN no supervisa, pero tampoco asigna a un no operativo")
    void c8ElGobiernoDelTenantTampocoAsignaAUnAgenteDeBaja() {
        Actor duena = agenteDelEquipo(0);
        Actor deOtroEquipo = agenteDeOtroEquipo();
        Actor gobierno = tenantAdmin();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        try {
            assertEquals(1, jdbc.update("""
                    update detalle_agente set estado_operativo = 'N'
                     where id_persona_rol = ? and estado_operativo = 'D'
                    """, deOtroEquipo.idRolOperativo()));

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad,
                            deOtroEquipo.idRolOperativo(), MOTIVO, observado(idPropiedad), gobierno),
                    "el gobierno del tenant esta exento de la restriccion de EQUIPO, no de la de "
                            + "ELEGIBILIDAD: el destino sigue teniendo que poder operar");
            assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        } finally {
            jdbc.update("update detalle_agente set estado_operativo = 'D' where id_persona_rol = ?",
                    deOtroEquipo.idRolOperativo());
        }

        // CONTROL POSITIVO: y con el agente de vuelta, cruza equipos como dice C1.
        propiedades.asignarResponsable(idPropiedad, deOtroEquipo.idRolOperativo(), MOTIVO,
                observado(idPropiedad), gobierno);
        assertEquals(deOtroEquipo.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(censoAntes, censo());
    }

    // ------------------------------------------------------------------
    // C8 · D-P0-12: los candidatos que el Core ofrece
    // ------------------------------------------------------------------

    /**
     * <b>La lista es exactamente el conjunto elegible, comparado contra SQL.</b>
     *
     * <p>La consulta de la prueba <b>reescribe</b> el predicado a proposito: es
     * una derivacion independiente, y que las dos coincidan es la asercion. Si
     * la prueba reutilizara el repositorio, comprobaria que una funcion es igual
     * a si misma.
     */
    @Test
    @DisplayName("C8/D-P0-12: los candidatos del BROKER son sus supervisados elegibles, sin el actual")
    void c8LosCandidatosDelBrokerSonSusSupervisadosElegibles() {
        Actor duena = agenteDelEquipo(0);
        Actor b = broker();
        long idPropiedad = registrar(duena);

        List<Long> ofrecidos = idsDeCandidatos(idPropiedad, null, b);
        List<Long> esperados = elegiblesSegunSql(duena.idOrganizacion(), b.idRolOperativo(),
                duena.idRolOperativo());

        assertFalse(esperados.isEmpty(),
                "control positivo: ese broker tiene que supervisar a alguien elegible, o la "
                        + "comparacion de abajo se cumpliria con dos listas vacias");
        assertEquals(esperados, ofrecidos,
                "el Core ofrece EXACTAMENTE los elegibles de este actor para este recurso");
        assertFalse(ofrecidos.contains(duena.idRolOperativo()),
                "y nunca al responsable actual: seria ofrecer un traspaso que el POST rechaza "
                        + "con «ese agente ya responde por esta propiedad»");
    }

    @Test
    @DisplayName("C8/D-P0-12: el TENANT_ADMIN ve los elegibles del tenant, sin el actual")
    void c8LosCandidatosDelGobiernoSonLosDelTenant() {
        Actor duena = agenteDelEquipo(0);
        Actor gobierno = tenantAdmin();
        long idPropiedad = registrar(duena);

        List<Long> ofrecidos = idsDeCandidatos(idPropiedad, null, gobierno);
        List<Long> esperados = elegiblesSegunSql(duena.idOrganizacion(), null,
                duena.idRolOperativo());

        assertEquals(esperados, ofrecidos,
                "el gobierno del tenant no supervisa a nadie, asi que sus candidatos son todos "
                        + "los agentes elegibles de SU organizacion");
        assertTrue(ofrecidos.size() > idsDeCandidatos(idPropiedad, null, broker()).size(),
                "y son MAS que los de un broker: si fueran los mismos, la exencion de equipo no "
                        + "estaria haciendo nada");
        assertFalse(ofrecidos.contains(duena.idRolOperativo()));
    }

    @Test
    @DisplayName("C8/D-P0-12: `texto` acota la lista, y la acota en la base")
    void c8ElTextoFiltraLosCandidatos() {
        Actor duena = agenteDelEquipo(0);
        Actor gobierno = tenantAdmin();
        long idPropiedad = registrar(duena);

        var todos = propiedades.candidatosAResponsable(idPropiedad, null, 1, 100, gobierno);
        assertFalse(todos.items().isEmpty(), "sin candidatos no hay filtro que medir");

        var uno = todos.items().get(0);
        var filtrados = propiedades.candidatosAResponsable(idPropiedad, uno.codigoAgente(), 1, 100,
                gobierno);
        assertEquals(List.of(uno.idAgente()),
                filtrados.items().stream()
                        .map(PropiedadUniversalService.CandidatoResponsable::idAgente).toList(),
                "el codigo de agente identifica a uno solo, asi que el filtro tiene que dejar uno");
        assertEquals(1, filtrados.total(),
                "y el total viene de la BASE, no del tamano de la pagina: con el conteo hecho "
                        + "sobre lo descargado, la paginacion mentiria en cuanto hubiera dos "
                        + "paginas");

        assertTrue(propiedades.candidatosAResponsable(idPropiedad,
                        "no-existe-" + UUID.randomUUID(), 1, 100, gobierno).items().isEmpty(),
                "y un texto que no casa devuelve vacio, no todo");
    }

    /**
     * <b>La lista responde lo mismo que el boton</b>, y por el mismo predicado.
     *
     * <p>Un broker que no supervisa al responsable no puede iniciar el traspaso
     * —{@code puedeTraspasar=false} en la ficha (C7)— asi que tampoco recibe la
     * lista de destinos: seria ofrecerle el paso siguiente de algo que no puede
     * empezar. Y es <b>403</b>, no una lista vacia: «no hay candidatos» y «no te
     * corresponde» son dos respuestas distintas, y devolver la primera por la
     * segunda deja al usuario buscando un agente que no existe.
     */
    @Test
    @DisplayName("C8/D-P0-12: sin poder iniciar el traspaso no hay candidatos, y es 403 no vacio")
    void c8SinPoderIniciarNoHayCandidatos() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        Actor brokerAjeno = brokerQueNoSupervisaA(duena.idRolOperativo());

        assertFalse(propiedades.consultar(idPropiedad, brokerAjeno).responsabilidad()
                        .puedeTraspasar(),
                "el escenario: la ficha ya le dice que no puede iniciarlo (C7)");
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.candidatosAResponsable(idPropiedad, null, 1, 20, brokerAjeno),
                "asi que la lista de destinos responde 403 y no una lista vacia");

        // Y la frontera de tenant, delante de todo: 404 y no 403.
        assertThrows(NoEncontradoException.class,
                () -> propiedades.candidatosAResponsable(unaPropiedadDeOtroTenant(), null, 1, 20,
                        broker()),
                "una propiedad de otra corredora es INEXISTENTE tambien por esta puerta: un 403 "
                        + "confirmaria que existe en alguna parte");
    }

    /**
     * <b>Dejar de ser elegible se nota en las DOS puertas, y en el mismo
     * instante.</b>
     *
     * <p>Es la prueba de que la lista y la revalidacion del POST salen del mismo
     * predicado. Si fueran dos escrituras de la regla, aqui es donde se veria:
     * la lista dejaria de ofrecerlo y el POST seguiria aceptandolo, o al reves.
     *
     * <p>Y la segunda mitad —«el POST despues de que el candidato dejo de ser
     * elegible»— es el caso real: entre pedir la lista y pulsar el boton pasa
     * tiempo, y en ese hueco una cuenta se puede suspender.
     */
    @Test
    @DisplayName("C8/D-P0-12: un agente desactivado sale de la lista y el POST hacia el es 403")
    void c8ElDesactivadoDesapareceDeLaListaYElPostLoRechaza() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor b = broker();
        long idPropiedad = registrar(duena);
        String censoAntes = censo();

        // La lista, ANTES: lo ofrece. Sin esto, la ausencia de abajo podria ser
        // que nunca hubiera estado.
        assertTrue(idsDeCandidatos(idPropiedad, null, b).contains(destino.idRolOperativo()),
                "control positivo: el destino esta en la lista mientras es elegible");

        Long idCredencial = credencialDe(destino.idRolOperativo());
        assertNotNull(idCredencial);
        try {
            assertEquals(1, jdbc.update("""
                    update credencial_usuario set estado_administrativo = 'I'
                     where id_persona_rol = ? and estado_administrativo = 'A'
                    """, idCredencial));

            assertFalse(idsDeCandidatos(idPropiedad, null, b).contains(destino.idRolOperativo()),
                    "desactivado, desaparece de la lista de destinos");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(),
                            MOTIVO, observado(idPropiedad), b),
                    "y el POST hacia el -- que la lista ya no ofrece -- lo rechaza igual: la "
                            + "lista no autoriza nada, y entre pedirla y usarla una cuenta se "
                            + "puede suspender");
            assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        } finally {
            jdbc.update("update credencial_usuario set estado_administrativo = 'A' "
                    + "where id_persona_rol = ?", idCredencial);
        }
        assertEquals(censoAntes, censo());
    }

    // ------------------------------------------------------------------
    // C8 · D-P0-8: desactivar NO reasigna
    // ------------------------------------------------------------------

    /**
     * <b>Desactivar a un agente no mueve ni una propiedad</b> (D-P0-8).
     *
     * <p>Es una decision, no una omision. Reasignar en cascada al desactivar
     * repartiria cartera sin que nadie lo decidiera y sin motivo en el
     * expediente — justo el tipo de escritura que P0 vino a quitar. Lo que
     * ocurre es lo contrario: el agente deja de poder <b>recibir</b>, y lo que
     * ya llevaba se queda donde esta, como <b>situacion que requiere gobierno
     * explicito</b>.
     *
     * <p>Y la situacion tiene salida, que es la otra mitad de la decision: el
     * TENANT_ADMIN la resuelve con un traspaso trazable, y tambien el BROKER que
     * lo supervisa — la supervision sigue abierta porque desactivar una cuenta
     * no cierra el organigrama. La fila del expediente lleva al desactivado como
     * <b>anterior</b>: su rastro no se borra ni se reinterpreta.
     */
    @Test
    @DisplayName("C8/D-P0-8: desactivar no reasigna; el traspaso explicito sigue siendo la salida")
    void c8DesactivarNoReasignaYElTraspasoSigueAbierto() {
        Actor saliente = agenteDelEquipo(0);
        Actor otro = agenteDelEquipo(1);
        Actor b = broker();
        Actor gobierno = tenantAdmin();
        long porElGobierno = registrar(saliente);
        long porElBroker = registrar(saliente);
        String censoAntes = censo();

        Long idCredencial = credencialDe(saliente.idRolOperativo());
        assertNotNull(idCredencial);
        try {
            assertEquals(1, jdbc.update("""
                    update credencial_usuario set estado_administrativo = 'I'
                     where id_persona_rol = ? and estado_administrativo = 'A'
                    """, idCredencial));

            // 1. NO se movio nada.
            assertEquals(saliente.idRolOperativo(), responsableDe(porElGobierno),
                    "desactivar una cuenta no reasigna sus propiedades: seria repartir cartera "
                            + "sin que nadie lo decidiera y sin motivo en el expediente");
            assertEquals(saliente.idRolOperativo(), responsableDe(porElBroker));

            // 2. Y no puede recibir mas.
            assertFalse(idsDeCandidatos(porElGobierno, null, gobierno)
                            .contains(saliente.idRolOperativo()),
                    "ni aparece como destino de nadie");
            long otraPropiedad = registrar(otro);
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(otraPropiedad,
                            saliente.idRolOperativo(), MOTIVO, observado(otraPropiedad), b),
                    "ni puede recibir una nueva");

            // 3. La salida: el traspaso explicito, por las dos bandas que
            //    gobiernan al saliente.
            var delGobierno = propiedades.asignarResponsable(porElGobierno,
                    otro.idRolOperativo(), MOTIVO, observado(porElGobierno), gobierno);
            assertEquals(otro.idRolOperativo(), responsableDe(porElGobierno));
            assertEquals(saliente.idRolOperativo(), delGobierno.idResponsableAnterior(),
                    "y el expediente lleva al desactivado como ANTERIOR: su rastro no se borra "
                            + "ni se reinterpreta");
            assertEquals("TRASPASO", delGobierno.origen());

            assertTrue(alcances.alcanza(b, saliente.idRolOperativo()),
                    "la supervision sigue abierta: desactivar una cuenta no cierra el organigrama");
            propiedades.asignarResponsable(porElBroker, otro.idRolOperativo(), MOTIVO,
                    observado(porElBroker), b);
            assertEquals(otro.idRolOperativo(), responsableDe(porElBroker),
                    "asi que el BROKER que lo supervisa tambien resuelve la situacion");
        } finally {
            jdbc.update("update credencial_usuario set estado_administrativo = 'A' "
                    + "where id_persona_rol = ?", idCredencial);
        }
        assertEquals(censoAntes, censo(),
                "y las cuatro tablas de identidad quedaron exactamente como estaban: este corte "
                        + "no escribe en ninguna de ellas");
    }

    // ------------------------------------------------------------------
    // Soporte de C8
    // ------------------------------------------------------------------

    private List<Long> idsDeCandidatos(long idPropiedad, String texto, Actor quien) {
        return propiedades.candidatosAResponsable(idPropiedad, texto, 1, 100, quien).items()
                .stream()
                .map(PropiedadUniversalService.CandidatoResponsable::idAgente)
                .toList();
    }

    /**
     * <b>Los elegibles, derivados otra vez y a mano.</b>
     *
     * <p>Reescribe las cinco condiciones de D-P0-7 en SQL plano a proposito: es
     * el oraculo independiente contra el que se compara el JPQL del repositorio.
     * Pedirle la lista al mismo repositorio comprobaria que una funcion es igual
     * a si misma.
     *
     * @param idRolBroker {@code null} para el TENANT_ADMIN, que no supervisa
     */
    private List<Long> elegiblesSegunSql(long idOrganizacion, Long idRolBroker, long excluir) {
        return jdbc.queryForList("""
                select a.id_persona_rol
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                  join persona p on p.id_persona = r.id_persona
                 where a.organizacion_id = ?
                   and r.organizacion_id = ?
                   and r.tipo_rol = 'AGENTE'
                   and r.vigencia_hasta is null
                   and a.estado_operativo = 'D'
                   and exists (select 1 from credencial_usuario c
                                 join persona_rol rc on rc.id_persona_rol = c.id_persona_rol
                                where rc.id_persona = r.id_persona
                                  and rc.organizacion_id = r.organizacion_id
                                  and rc.tipo_rol = 'USUARIO_INTERNO'
                                  and rc.vigencia_hasta is null
                                  and c.estado_administrativo = 'A')
                   and exists (select 1 from usuario_organizacion uo
                                 join persona_rol ru on ru.id_persona_rol = uo.id_usuario
                                where ru.id_persona = r.id_persona
                                  and ru.organizacion_id = r.organizacion_id
                                  and ru.tipo_rol = 'USUARIO_INTERNO'
                                  and ru.vigencia_hasta is null
                                  and uo.organizacion_id = r.organizacion_id
                                  and uo.estado = 'A')
                   and (?::bigint is null
                        or exists (select 1 from supervision_agente s
                                    where s.id_rol_broker = ?
                                      and s.id_rol_agente = a.id_persona_rol
                                      and s.fecha_fin is null))
                   and a.id_persona_rol <> ?
                 order by p.nombres_o_razon_social asc, a.id_persona_rol asc
                """, Long.class, idOrganizacion, idOrganizacion, idRolBroker, idRolBroker,
                excluir);
    }

    /** La credencial del USUARIO_INTERNO de la misma persona en la misma organizacion. */
    private Long credencialDe(long idRolAgente) {
        List<Long> ids = jdbc.queryForList("""
                select c.id_persona_rol
                  from credencial_usuario c
                  join persona_rol rc on rc.id_persona_rol = c.id_persona_rol
                  join persona_rol ra on ra.id_persona = rc.id_persona
                                     and ra.organizacion_id = rc.organizacion_id
                 where ra.id_persona_rol = ?
                   and rc.tipo_rol = 'USUARIO_INTERNO'
                   and rc.vigencia_hasta is null
                """, Long.class, idRolAgente);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Su membresia activa en la organizacion, por ese mismo rol interno. */
    private Long membresiaDe(long idRolAgente) {
        List<Long> ids = jdbc.queryForList("""
                select uo.id_usuario_organizacion
                  from usuario_organizacion uo
                  join persona_rol ru on ru.id_persona_rol = uo.id_usuario
                  join persona_rol ra on ra.id_persona = ru.id_persona
                                     and ra.organizacion_id = ru.organizacion_id
                 where ra.id_persona_rol = ?
                   and ru.tipo_rol = 'USUARIO_INTERNO'
                   and ru.vigencia_hasta is null
                   and uo.organizacion_id = ru.organizacion_id
                   and uo.estado = 'A'
                """, Long.class, idRolAgente);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * <b>El censo de las cuatro tablas de identidad, por estado.</b>
     *
     * <p>Se compara al final de cada caso que muta el escenario. Este corte
     * <b>no escribe</b> en ninguna de las cuatro —solo las lee para decidir la
     * elegibilidad—, asi que cualquier diferencia aqui significa que una
     * restitucion no ocurrio, y una restitucion que no ocurre no rompe esta
     * prueba: rompe las SIGUIENTES, con un mensaje que no habla de esto.
     */
    private String censo() {
        return "credencial=" + jdbc.queryForList("""
                select estado_administrativo, count(*) from credencial_usuario
                 group by 1 order by 1
                """).toString()
                + " membresia=" + jdbc.queryForList("""
                select estado, count(*) from usuario_organizacion group by 1 order by 1
                """).toString()
                + " agente=" + jdbc.queryForList("""
                select estado_operativo, count(*) from detalle_agente group by 1 order by 1
                """).toString()
                + " rol=" + jdbc.queryForList("""
                select tipo_rol, count(*) filter (where vigencia_hasta is null) as vigentes,
                       count(*) as total
                  from persona_rol group by 1 order by 1
                """).toString()
                + " supervision=" + jdbc.queryForList("""
                select count(*) filter (where fecha_fin is null) as vigentes, count(*) as total
                  from supervision_agente
                """).toString();
    }

    // ==================================================================
    // H2. Los atributos gobernados tambien responden ante la autoridad
    // ==================================================================

    /**
     * <b>Cuanto se juega en {@code atributo_propiedad}, medido y no
     * transcrito.</b>
     *
     * <p>Hasta este corte el gate de autoridad no vigilaba esa tabla: vigilaba
     * las columnas ESTRUCTURAL del agregado, que son cuatro. Esta prueba existe
     * para que la magnitud del hueco <b>no viva en un comentario</b> — la
     * auditoria de este mismo P0 lo rechazo, entre otras cosas, por cifras
     * escritas a mano que ya estaban caducadas el dia que se escribieron.
     *
     * <p>No fija un numero: fija la <b>relacion</b>, que es lo que sostiene la
     * decision. Si un dia lo gobernado dejara de estar en esa tabla, esto se
     * pone rojo y con las dos cuentas delante.
     */
    @Test
    @DisplayName("H2: la tabla de atributos se lleva la mayoria de lo gobernado")
    void loGobernadoViveSobreTodoEnLaTablaDeAtributos() {
        Integer enTabla = jdbc.queryForObject("""
                select count(*) from catalogo_atributo
                 where sujeto = 'PROPIEDAD' and destino = 'ATRIBUTO'
                """, Integer.class);
        Integer enColumnas = jdbc.queryForObject("""
                select count(*) from catalogo_atributo
                 where sujeto = 'PROPIEDAD' and destino = 'ESTRUCTURAL'
                """, Integer.class);

        assertNotNull(enTabla);
        assertNotNull(enColumnas);
        // Control positivo: si el catalogo se vaciara, las dos cuentas serian 0
        // y la comparacion de abajo pasaria sin haber mirado nada.
        assertTrue(enTabla > 0 && enColumnas > 0,
                "el catalogo de PROPIEDAD tiene que tener claves de los dos destinos; "
                        + "ATRIBUTO=" + enTabla + " ESTRUCTURAL=" + enColumnas);
        assertTrue(enTabla > enColumnas * 5L,
                "la decision de vigilar `atributo_propiedad` se apoya en que ahi vive la "
                        + "inmensa mayoria de lo gobernado. Hoy: ATRIBUTO=" + enTabla
                        + ", ESTRUCTURAL=" + enColumnas + ". Si esto deja de ser cierto, la "
                        + "forma del gate hay que volver a pensarla, no ajustar el numero");
    }

    /**
     * <b>Y responden ante la autoridad</b>: la mitad de ejecucion.
     *
     * <p>Que ninguna via nueva pueda nacer sin guarda es la mitad estructural, y
     * la prueba {@code AutoridadDeLaPropiedadTest}.
     */
    @Test
    @DisplayName("H2: un atributo gobernado de otro no se escribe ni se retira")
    void losAtributosGobernadosRespondenAnteLaAutoridad() {
        Actor duena = agenteDelEquipo(0);
        Actor ajena = agenteDeOtroEquipo();
        long idPropiedad = registrar(duena);

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.editar(idPropiedad,
                        new PropiedadUniversalService.ComandoEdicion(null, null, null, null, null,
                                List.of(new ValorAtributo("dormitorios", "9")), null, null,
                                null),
                        ajena),
                "escribir `dormitorios` en la propiedad de otro es escribir un hecho gobernado "
                        + "del inmueble ajeno");

        assertEquals("3", valorGobernado(idPropiedad, "dormitorios"),
                "y no se escribio nada: el valor sigue siendo el que puso su responsable");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.editar(idPropiedad,
                        new PropiedadUniversalService.ComandoEdicion(null, null, null, null, null,
                                null, null, List.of("dormitorios"), null),
                        ajena),
                "y retirarlo tampoco -- el borrado es fisico, asi que es la escritura mas "
                        + "irreversible de las tres");

        assertEquals("3", valorGobernado(idPropiedad, "dormitorios"),
                "sigue ahi despues del intento de retirada");
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private long registrar(Actor quien) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Caso de alcance",
                new Ubicacion("Av. Alcance " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("3000"), "PEN",
                        null, null, null, null, null, null, null)),
                null), quien).idPropiedad();
    }

    /** Dos agentes del MISMO equipo, para lo que el broker si alcanza. */
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

    /**
     * Un agente del MISMO tenant y de OTRO equipo.
     *
     * <p>Es la pieza que separa «no puede porque es de otra corredora» de «no
     * puede porque no lo supervisa». Sin ella, C1 se probaria solo contra la
     * frontera de tenant, que ya cortaba desde V6, y la parte nueva de la
     * decision quedaria sin medir.
     */
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
        assertTrue(!filas.isEmpty(),
                "hace falta un agente del mismo tenant supervisado por OTRO broker");
        return actorAgente(filas.get(0));
    }

    /**
     * Un agente que <b>ese</b> broker supervisa hoy.
     *
     * <p>Se resuelve contra la base y no con un id escrito a mano: el destino
     * legitimo de un traspaso depende de quien lo firma, y fijarlo aqui haria
     * que la prueba dejara de medir la regla el dia que cambie el organigrama de
     * la semilla.
     */
    private Actor unSupervisadoDe(Actor unBroker) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                  join supervision_agente s on s.id_rol_agente = a.id_persona_rol
                                           and s.fecha_fin is null
                 where s.id_rol_broker = ?
                 order by a.id_persona_rol limit 1
                """, unBroker.idRolOperativo());
        assertTrue(!filas.isEmpty(),
                "ese broker no supervisa a nadie, asi que la prueba no podria medir un destino "
                        + "legitimo suyo");
        return actorAgente(filas.get(0));
    }

    /**
     * Un agente del MISMO tenant que ese broker <b>no</b> supervisa, distinto de
     * uno dado.
     *
     * <p>Es la pieza del caso «no supervisa a ninguno de los dos extremos»: sin
     * ella habria que reutilizar el saliente como destino, y entonces el rechazo
     * podria venir de la regla «ese agente ya responde por esta propiedad» en
     * vez de la de alcance.
     */
    private Actor unAgenteQueNoSupervisa(Actor unBroker, long distintoDe) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 where r.organizacion_id = (select organizacion_id from persona_rol
                                             where id_persona_rol = ?)
                   and a.id_persona_rol <> ?
                   and not exists (select 1 from supervision_agente s
                                    where s.id_rol_broker = ?
                                      and s.id_rol_agente = a.id_persona_rol
                                      and s.fecha_fin is null)
                 order by a.id_persona_rol limit 1
                """, unBroker.idRolOperativo(), distintoDe, unBroker.idRolOperativo());
        assertTrue(!filas.isEmpty(),
                "hace falta un agente del tenant que ese broker NO supervise y que no sea el "
                        + "saliente: sin el, el caso «ninguno de los dos» no se puede montar");
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

    /** Un BROKER real del tenant que NO supervisa al agente dado. */
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
        assertNotNull(id, "hace falta un BROKER del tenant que NO supervise a ese agente: sin "
                + "el, «alcance» y «banda» no se pueden distinguir");
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
    // Lecturas directas
    // ==================================================================

    private Long unPropietario(Actor actor) {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
    }

    private Long unAgenteDeOtraOrganizacion(long idOrganizacion) {
        long vecino = otroTenant();
        assertNotEquals(idOrganizacion, vecino, "el tenant vecino no puede ser el propio");
        return unAgenteDe(vecino);
    }

    private Long unaPropiedadDeOtraOrganizacion(long idOrganizacion) {
        long vecino = otroTenant();
        assertNotEquals(idOrganizacion, vecino, "el tenant vecino no puede ser el propio");
        return unaPropiedadDe(vecino);
    }

    /** La misma, cuando el caso exige que exista y no admite saltarse. */
    private long unaPropiedadDeOtroTenant() {
        Long id = unaPropiedadDeOtraOrganizacion(broker().idOrganizacion());
        assertNotNull(id, "sin una propiedad de OTRO tenant no hay frontera que medir");
        return id;
    }

    // ==================================================================
    // El tenant vecino, construido y no heredado
    // ==================================================================

    /**
     * <b>La segunda organizacion la crea esta prueba, no la hereda.</b>
     *
     * <p>Antes estos tres ayudantes buscaban «cualquier organizacion distinta de
     * la mia», y en la base compartida siempre encontraban una: los tenants que
     * {@code PropiedadUniversalIntegrationTest} y
     * {@code SimulacroRecuperacionIntegrationTest} <b>dejan escritos</b> al
     * terminar. Las migraciones crean <b>una sola</b> organizacion, asi que
     * contra una base recien migrada —la de un clon limpio, o la primera corrida
     * de una instancia nueva— no habia vecino y las dos pruebas de frontera de
     * tenant caian con «hacen falta dos organizaciones para medir esto».
     *
     * <p>Medido el 2026-08-30 sobre una instancia virgen construida por las
     * migraciones reales: {@code organizacion} tenia 1 fila. En la compartida
     * tenia 4, y las otras 3 eran residuo de otras suites. Ademas el orden
     * alfabetico de surefire pone esta clase (A) <b>antes</b> que las que crean
     * esos tenants (P, S), asi que la primera corrida de cualquier base nueva
     * habria sido roja aunque la base fuera correcta.
     *
     * <p>Depender de eso es depender de que otra prueba haya corrido antes y
     * haya dejado su rastro. Aqui se construye lo que hace falta —idempotente,
     * por {@code codigo}, como ya hace {@code PropiedadUniversalIntegrationTest}
     * con los suyos— para que la frontera se mida <b>siempre</b> y no solo
     * cuando alguien paso por delante.
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

    /** Un AGENTE del tenant vecino; se crea la primera vez y se reutiliza. */
    private long unAgenteDe(long idOrganizacion) {
        List<Long> conDetalle = jdbc.queryForList("""
                select d.id_persona_rol from detalle_agente d
                 where d.organizacion_id = ? order by d.id_persona_rol limit 1
                """, Long.class, idOrganizacion);
        if (!conDetalle.isEmpty()) {
            return conDetalle.get(0);
        }
        long idPersona = unaPersonaDe(idOrganizacion);
        // Se busca el rol aparte del detalle: cada sentencia de JdbcTemplate
        // confirma por su cuenta, asi que una corrida que creara el rol y
        // muriera antes del detalle dejaria un rol huerfano, y buscar por rol lo
        // daria por bueno para siempre con el detalle sin existir nunca.
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
        // `estado_operativo` admite D/L/N y no 'A': el vocabulario de esta
        // columna no es el de `estado`, aunque las dos lleven una sola letra.
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

    /**
     * Una PROPIEDAD del tenant vecino.
     *
     * <p>Nace <b>FALTANTE</b> a proposito: se inserta por SQL y no por el caso
     * de uso, asi que no hay actor del alta a quien atribuirla, y un responsable
     * inventado seria justo la procedencia falsa que este P0 vino a quitar. Para
     * lo que la usan las pruebas de frontera da igual: la respuesta tiene que
     * ser INEXISTENTE antes de mirar quien responde.
     */
    private long unaPropiedadDe(long idOrganizacion) {
        List<Long> ids = jdbc.queryForList("""
                select id_propiedad from propiedad where organizacion_id = ?
                 order by id_propiedad limit 1
                """, Long.class, idOrganizacion);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
                insert into propiedad (codigo, direccion, distrito, metraje, tipo_inmueble, uso,
                                       organizacion_id, estado_registro, origen_incorporacion)
                values (?, 'Av. Frontera 100', 'Miraflores', 80.00, 'D', 'C', ?, 'A', 'SEMILLA')
                returning id_propiedad
                """, Long.class, "VECINO-" + idOrganizacion, idOrganizacion);
    }

    private Long responsableDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select id_rol_responsable from propiedad where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    /**
     * <b>Lo que la prueba «vio» justo antes de mandar el comando</b> (D-P0-9).
     *
     * <p>El traspaso declara sobre que responsable actua, y aqui se lee de la
     * base en el mismo instante en que se decide, que es lo que hace una
     * pantalla: cargar la ficha y decidir sobre lo que muestra. Fijarlo a mano
     * haria que estas pruebas midieran el 409 en vez de la regla que estan
     * midiendo; y pasar «lo que hay ahora» automaticamente dentro del servicio
     * seria justo la reinterpretacion que D-P0-9 prohibe -- por eso el
     * observado entra por el comando y no lo deduce el Core.
     */
    private PropiedadUniversalService.ResponsableObservado observado(long idPropiedad) {
        return PropiedadUniversalService.ResponsableObservado.de(responsableDe(idPropiedad));
    }

    private void dejarSinResponsable(long idPropiedad) {
        jdbc.update("update propiedad set id_rol_responsable = null where id_propiedad = ?",
                idPropiedad);
    }

    private Long otraOrganizacion(long idOrganizacion) {
        long vecino = otroTenant();
        assertNotEquals(idOrganizacion, vecino, "el tenant vecino no puede ser el propio");
        return vecino;
    }

    /**
     * El valor gobernado tal como lo guarda la base.
     *
     * <p>Se normaliza el numerico porque {@code valor_numero} es
     * {@code NUMERIC} con escala: un {@code 3} escrito por el alta se lee
     * {@code 3.0000}. Comparar la cadena cruda hacia "3" daba un rojo que no
     * medía la autoridad sino el formato de la columna.
     */
    private String valorGobernado(long idPropiedad, String clave) {
        List<String> valores = jdbc.queryForList("""
                select coalesce(valor_texto, trim(trailing '.' from
                           trim(trailing '0' from valor_numero::text)))
                  from atributo_propiedad
                 where id_propiedad = ? and clave = ?
                """, String.class, idPropiedad, clave);
        assertNotEquals(0, valores.size(), "no hay valor de `" + clave + "` que comparar");
        return valores.get(0);
    }
}
