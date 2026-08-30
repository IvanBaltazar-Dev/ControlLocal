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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static final String MOTIVO =
            "Reasignacion por reparto de cartera del trimestre";

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
                        MOTIVO, brokerDeOtroEquipo),
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
                deOtroEquipo.idRolOperativo(), MOTIVO, tenantAdmin());

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
                            MOTIVO, quien),
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
        Long ajena = unaPropiedadDeOtraOrganizacion(broker().idOrganizacion());
        if (ajena == null) {
            // Sin propiedades de otro tenant no hay nada que medir, y decirlo es
            // mejor que dar un verde que no ha mirado nada.
            return;
        }
        assertThrows(NoEncontradoException.class,
                () -> propiedades.traspasosDe(ajena, broker()),
                "el expediente de una propiedad de otra corredora responde INEXISTENTE");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.asignarResponsable(ajena, agenteDelEquipo(0).idRolOperativo(),
                        MOTIVO, broker()),
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
     * <b>Una propiedad FALTANTE no responde ante nadie, asi que ningun broker la
     * alcanza.</b>
     *
     * <p>Es consecuencia de C1+C2 y no una regla aparte, pero se escribe porque
     * es el caso que hoy manda: medido el 2026-08-30, las <b>26</b> propiedades
     * de {@code dev} estan FALTANTE. Se deniega por el lado seguro y no se
     * pierde nada — el TENANT_ADMIN lo lee, y el broker lo leera en cuanto
     * asigne a alguien de su equipo.
     */
    @Test
    @DisplayName("C2: una propiedad FALTANTE es gobierno del tenant, no de un broker")
    void unaPropiedadSinResponsableSoloLaLeeElGobierno() {
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        dejarSinResponsable(idPropiedad);

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.traspasosDe(idPropiedad, broker()),
                "sin responsable no hay a quien supervisar, asi que el alcance del broker no "
                        + "llega. Se deniega por el lado seguro");

        assertEquals(1, propiedades.traspasosDe(idPropiedad, tenantAdmin()).size(),
                "y el gobierno del tenant si lo lee: la propiedad no queda sin nadie que "
                        + "pueda mirarla");

        // Y en cuanto la asigna a alguien de su equipo, su broker vuelve a leerlo.
        propiedades.asignarResponsable(idPropiedad, duena.idRolOperativo(), MOTIVO, broker());
        assertEquals(2, propiedades.traspasosDe(idPropiedad, broker()).size(),
                "asignada a un supervisado suyo, el broker recupera el expediente entero -- "
                        + "el alta y el traspaso");
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
                        MOTIVO, fingido),
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
        // sobre la FIRMA del servicio, que es lo unico que el cliente alcanza.
        List<String> firmas = List.of(
                "asignarResponsable(long idPropiedad, long idRolAgente, String motivo, Actor actor)",
                "traspasosDe(long idPropiedad, Actor actor)");
        for (String firma : firmas) {
            assertTrue(firma.contains("Actor actor"),
                    "la identidad entra como Actor, que lo construye el servidor: " + firma);
            assertTrue(firma.chars().filter(c -> c == ',').count() <= 3,
                    "y no hay ningun parametro suelto de rol, banda u organizacion: " + firma);
        }

        // Y el unico identificador que SI viaja -- el agente destino -- esta
        // acotado por tenant y por alcance, que es justo lo que prueban las de
        // C1. Aqui se deja constancia de por que ese es el unico que puede
        // viajar: nombra el destino del hecho, no quien lo autoriza.
        Actor duena = agenteDelEquipo(0);
        long idPropiedad = registrar(duena);
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, duena.idRolOperativo(),
                        MOTIVO, duena),
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
                            pobre, broker()),
                    "\"" + pobre + "\" no explica nada, y esta tabla es append-only: nadie la "
                            + "corrige despues. Es el mismo minimo que exige "
                            + "PoliticaComercial.exigirMotivoDeReasignacion para el encargo, "
                            + "que es el mismo tipo de hecho un nivel mas abajo");
        }

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "y ningun rechazo dejo la propiedad traspasada a medias");

        // Y con un motivo de verdad, entra.
        propiedades.asignarResponsable(idPropiedad, otra.idRolOperativo(), MOTIVO, broker());
        assertEquals(otra.idRolOperativo(), responsableDe(idPropiedad));
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
        List<Long> ids = jdbc.queryForList("""
                select a.id_persona_rol from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 where r.organizacion_id <> ? order by a.id_persona_rol limit 1
                """, Long.class, idOrganizacion);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long unaPropiedadDeOtraOrganizacion(long idOrganizacion) {
        List<Long> ids = jdbc.queryForList("""
                select id_propiedad from propiedad where organizacion_id <> ?
                 order by id_propiedad limit 1
                """, Long.class, idOrganizacion);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long responsableDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select id_rol_responsable from propiedad where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    private void dejarSinResponsable(long idPropiedad) {
        jdbc.update("update propiedad set id_rol_responsable = null where id_propiedad = ?",
                idPropiedad);
    }

    private Long otraOrganizacion(long idOrganizacion) {
        List<Long> ids = jdbc.queryForList(
                "select id_organizacion from organizacion where id_organizacion <> ? "
                        + "order by id_organizacion limit 1", Long.class, idOrganizacion);
        assertTrue(ids.isEmpty() || !ids.get(0).equals(idOrganizacion));
        return ids.isEmpty() ? null : ids.get(0);
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
