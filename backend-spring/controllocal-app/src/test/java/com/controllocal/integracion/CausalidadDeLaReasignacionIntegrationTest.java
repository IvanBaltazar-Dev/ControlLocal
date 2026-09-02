package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ReasignacionCaptacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AgenteService;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.CaptacionService.FichaCaptacion;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Reasignar un ENCARGO parte del agente que alguien miro, y ocurre entero o
 * no ocurre</b> (D-P0-9 y D-P0-10, aplicados a la SEGUNDA autoridad de P0).
 *
 * <h2>Que estaba mal, y por que no lo cubria la gemela de la propiedad</h2>
 * {@code CausalidadDelTraspasoIntegrationTest} cerro la autoridad de la
 * PROPIEDAD. La del ENCARGO —{@code captacion.id_rol_agente}— seguia siendo
 * exactamente lo que aquella dejo de ser:
 * <ul>
 *   <li>un <b>«pon a B»</b> sin estado de partida, asi que dos comandos que
 *       salieran del mismo agente A entraban los dos y el segundo pisaba al
 *       primero;</li>
 *   <li>una columna que se movia <b>por dirty checking</b>: bastaba con que una
 *       edicion del encargo hubiera cargado la fila antes de la reasignacion
 *       para que su <i>flush</i> devolviera el encargo al agente anterior, sin
 *       ninguna fila que lo explicara;</li>
 *   <li>y un destino <b>sin elegibilidad</b>: un agente suspendido, de baja o
 *       fuera de la organizacion podia recibirlo, aunque D-P0-7 ya lo prohibiera
 *       para las propiedades.</li>
 * </ul>
 * Que las dos autoridades tengan la misma forma de defecto no es casualidad: es
 * la misma clase de defecto, y por eso esta clase es la gemela de aquella y no
 * una lista de casos nuevos.
 *
 * <h2>Que se congela, que no</h2>
 * Lo congelado es el <b>comportamiento observable</b>: de un agente concreto
 * puede partir <b>exactamente una</b> reasignacion legitima; la segunda es
 * <b>409</b> y <b>no</b> se reinterpreta sobre el agente nuevo. El mecanismo
 * —precondicion en memoria, compare-and-set nativo y {@code updatable = false}
 * en el mapeo— es una eleccion.
 *
 * <h2>Lo que estas pruebas NO cubren</h2>
 * No repiten las guardas de banda ni de alcance: eso lo mide
 * {@code AutoridadDeEdicionIntegrationTest} y duplicarlo aqui daria dos sitios
 * donde la misma regla puede divergir. Aqui todo actor es legitimo salvo cuando
 * la prueba dice lo contrario, y lo que esta bajo ataque es <b>de que estado se
 * parte</b>, <b>que queda escrito</b> y <b>quien puede recibirlo</b>.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CausalidadDeLaReasignacionIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry registro) {
        BaseDeDatosDePruebas.registrar(registro);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CaptacionService captaciones;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired AgenteService agentes;
    @Autowired Environment entorno;

    /**
     * <b>Los dos escritores del hecho, espiados</b> (D-P0-10).
     *
     * <p>Son espias y no mocks: por defecto hacen lo de siempre —la reasignacion
     * entera funciona— y solo se les cambia el comportamiento en la prueba que
     * necesita romperlos o detenerlos. Un mock permisivo dejaria la suite verde
     * sin escribir nada, que es justo el estado que estas pruebas denuncian.
     *
     * <p>{@code CaptacionRepository} sirve ademas para el ataque de <b>dirty
     * checking</b>: su {@code save} es el ultimo punto de {@code actualizar}
     * anterior al <i>flush</i>, o sea el unico momento en que hay una
     * transaccion que <b>ya leyo</b> el agente y <b>todavia no ha escrito</b>.
     */
    @MockitoSpyBean ReasignacionCaptacionRepository rastro;
    @MockitoSpyBean CaptacionRepository encargos;

    private static final String MOTIVO = "Reparto de cartera del trimestre en el equipo";

    /** El importe que escribe la edicion concurrente, distinto del inicial. */
    private static final BigDecimal IMPORTE_EDITADO = new BigDecimal("4321.00");

    @AfterEach
    void devolverLosEspias() {
        Mockito.reset(rastro, encargos);
    }

    // ==================================================================
    // 1. El comando obsoleto: llego tarde y NO se reinterpreta
    // ==================================================================

    @Test
    @DisplayName("D-P0-9: el comando que sale de A cuando el encargo ya lo lleva B es 409 y no se convierte en «de B a C»")
    void elComandoObsoletoNoSeReinterpreta() {
        Actor a = agenteDelEquipo(0);
        Actor b = agenteDelEquipo(1);
        Actor c = agenteDelEquipo(2);
        Actor quien = broker();
        long idEncargo = encargoDe(a);

        int filas = reasignacionesDe(idEncargo);

        // T1 gana: parte de A, que es lo que hay.
        captaciones.reasignar(idEncargo, b.idRolOperativo(), MOTIVO, a.idRolOperativo(), quien);
        assertEquals(b.idRolOperativo(), agenteDe(idEncargo));
        assertEquals(filas + 1, reasignacionesDe(idEncargo));

        // T2 salio del MISMO A. Llega tarde.
        ConflictoException rechazo = assertThrows(ConflictoException.class,
                () -> captaciones.reasignar(idEncargo, c.idRolOperativo(), MOTIVO,
                        a.idRolOperativo(), quien),
                "dos reasignaciones que parten del mismo agente no pueden entrar las dos: eso es "
                        + "«la ultima escritura gana», y de un estado concreto sale UNA sola "
                        + "transicion legitima");

        assertEquals(b.idRolOperativo(), agenteDe(idEncargo),
                "y el que gano sigue siendo el primero: el segundo no pisa nada");
        assertEquals(filas + 1, reasignacionesDe(idEncargo),
                "el rechazo no deja fila en el historial de reasignaciones");

        // Y el mensaje NO reinterpreta: dice quien lo lleva HOY -- lo unico que
        // permite volver a decidir-- y dice que no se ha convertido en otro.
        assertTrue(rechazo.getMessage().contains(String.valueOf(b.idRolOperativo())),
                "el rechazo tiene que decir quien lo lleva HOY, o el broker no puede hacer otra "
                        + "cosa que reintentar a ciegas. Decia: " + rechazo.getMessage());
        assertTrue(rechazo.getMessage().contains("reinterpreta"),
                "y tiene que decir que NO se ha reinterpretado sobre el estado nuevo: "
                        + rechazo.getMessage());

        // CONTROL POSITIVO: el comando no era invalido, estaba desactualizado.
        // Con el estado que hay de verdad, la misma reasignacion entra.
        captaciones.reasignar(idEncargo, c.idRolOperativo(), MOTIVO, b.idRolOperativo(), quien);
        assertEquals(c.idRolOperativo(), agenteDe(idEncargo));
        assertEquals(filas + 2, reasignacionesDe(idEncargo));
        assertEquals(b.idRolOperativo(), anteriorDeLaUltimaFila(idEncargo),
                "y el historial dice de donde salio de verdad: de B, no de A");
    }

    // ==================================================================
    // 2. El observado equivocado, sin carrera ninguna
    // ==================================================================

    @Test
    @DisplayName("D-P0-9: declarar un agente que no es el actual es 409, y no escribe nada")
    void elObservadoEquivocadoNoEscribeNada() {
        Actor a = agenteDelEquipo(0);
        Actor otro = agenteDelEquipo(1);
        Actor destino = agenteDelEquipo(2);
        Actor quien = broker();
        long idEncargo = encargoDe(a);

        int filas = reasignacionesDe(idEncargo);

        ConflictoException rechazo = assertThrows(ConflictoException.class,
                () -> captaciones.reasignar(idEncargo, destino.idRolOperativo(), MOTIVO,
                        otro.idRolOperativo(), quien),
                "aqui no hay carrera ninguna: el comando declara un estado de partida que no es "
                        + "el que hay. Una reasignacion que no sabe de donde sale no es un hecho");

        assertEquals(a.idRolOperativo(), agenteDe(idEncargo), "y no se ha movido nada");
        assertEquals(filas, reasignacionesDe(idEncargo), "sin fila en el historial");
        assertTrue(rechazo.getMessage().contains(String.valueOf(a.idRolOperativo())),
                "el 409 tiene que nombrar al agente actual para que se pueda volver a decidir "
                        + "sobre el. Decia: " + rechazo.getMessage());

        // CONTROL POSITIVO: con el observado correcto, el mismo comando entra.
        captaciones.reasignar(idEncargo, destino.idRolOperativo(), MOTIVO, a.idRolOperativo(),
                quien);
        assertEquals(destino.idRolOperativo(), agenteDe(idEncargo));
        assertEquals(filas + 1, reasignacionesDe(idEncargo));
    }

    // ==================================================================
    // 3. La carrera de verdad: DOS transacciones vivas a la vez
    // ==================================================================

    /**
     * <b>El ataque que ninguna prueba secuencial puede montar.</b>
     *
     * <p>Las dos transacciones leen A. La primera ejecuta su compare-and-set y
     * se queda <b>detenida dentro de su transaccion</b>, con la fila bloqueada,
     * gracias a que el espia del rastro no devuelve hasta que se le diga. La
     * segunda pasa su precondicion en memoria —porque cargo A, que es lo que
     * habia cuando empezo— y se queda esperando en el UPDATE.
     *
     * <p>Que la segunda esta <b>de verdad</b> bloqueada no se supone: se
     * comprueba contra {@code pg_stat_activity}, y si en diez segundos no
     * aparece ninguna sesion esperando un lock, la prueba <b>falla</b>. Dar por
     * buena una carrera que nunca ocurrio seria un verde que no ha mirado nada.
     */
    @Test
    @DisplayName("D-P0-9: dos transacciones reales que parten del MISMO agente, y solo una gana")
    void dosTransaccionesRealesYSoloUnaGana() throws Exception {
        Actor a = agenteDelEquipo(0);
        Actor b = agenteDelEquipo(1);
        Actor c = agenteDelEquipo(2);
        Actor quien = broker();
        long idEncargo = encargoDe(a);

        int filas = reasignacionesDe(idEncargo);

        CountDownLatch enElCas = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        // El freno. Se pone en el rastro y no en otro sitio porque es la primera
        // escritura DESPUES del compare-and-set: cuando esto se ejecuta, la fila
        // de `captacion` ya esta bloqueada por T1 y su transaccion sigue abierta.
        Mockito.doAnswer(invocacion -> {
            if (primera.compareAndSet(false, true)) {
                enElCas.countDown();
                assertTrue(liberar.await(20, TimeUnit.SECONDS),
                        "nadie solto a T1: la prueba no llego a montar la carrera");
            }
            return comoSiempre(invocacion, rastro);
        }).when(rastro).save(ArgumentMatchers.any());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<FichaCaptacion> t1 = hilos.submit(() -> captaciones.reasignar(idEncargo,
                    b.idRolOperativo(), MOTIVO, a.idRolOperativo(), quien));

            assertTrue(enElCas.await(20, TimeUnit.SECONDS),
                    "T1 no llego a escribir el rastro, asi que no hay CAS ejecutado ni fila "
                            + "bloqueada: sin eso esta prueba no esta midiendo ninguna carrera");

            Future<FichaCaptacion> t2 = hilos.submit(() -> captaciones.reasignar(idEncargo,
                    c.idRolOperativo(), MOTIVO, a.idRolOperativo(), quien));

            esperarAUnaSesionEsperandoUnLock(t2);
            liberar.countDown();

            FichaCaptacion ganador = t1.get(20, TimeUnit.SECONDS);
            assertEquals(b.idRolOperativo(), ganador.idAgente());

            ExecutionException perdedor = assertThrows(ExecutionException.class,
                    () -> t2.get(20, TimeUnit.SECONDS),
                    "las dos partian de A: la segunda no puede entrar tambien");
            assertInstanceOf(ConflictoException.class, perdedor.getCause(),
                    "y el rechazo es un CONFLICTO (409), no un error tecnico ni un 400: la "
                            + "peticion era valida, lo que cambio fue el estado. Fue: "
                            + perdedor.getCause());
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
        }

        assertEquals(b.idRolOperativo(), agenteDe(idEncargo),
                "gana la que ejecuto su compare-and-set primero, y la otra no la pisa");
        assertEquals(filas + 1, reasignacionesDe(idEncargo),
                "UNA fila nueva en el historial, no dos: la reasignacion que perdio no ocurrio");
        assertEquals(a.idRolOperativo(), anteriorDeLaUltimaFila(idEncargo),
                "y la unica fila dice «de A a B», que es lo que de verdad ocurrio");
    }

    // ==================================================================
    // 4. D-P0-10: si falla la traza, no queda el cambio
    // ==================================================================

    @Test
    @DisplayName("D-P0-10: si falla la fila del historial, el agente del encargo tampoco cambia")
    void sinRastroNoHayReasignacion() {
        Actor a = agenteDelEquipo(0);
        Actor b = agenteDelEquipo(1);
        Actor c = agenteDelEquipo(2);
        Actor quien = broker();
        long idEncargo = encargoDe(a);

        // CONTROL POSITIVO: sin romper nada, la misma reasignacion escribe LAS
        // DOS cosas. Sin esto, el rollback de abajo podria estar midiendo una
        // reasignacion que no escribia nada de todas formas.
        int filas = reasignacionesDe(idEncargo);
        captaciones.reasignar(idEncargo, b.idRolOperativo(), MOTIVO, a.idRolOperativo(), quien);
        assertEquals(b.idRolOperativo(), agenteDe(idEncargo));
        assertEquals(filas + 1, reasignacionesDe(idEncargo));

        // Y ahora se rompe la fila del historial, DESPUES del compare-and-set:
        // el agente ya esta cambiado dentro de la transaccion cuando esto
        // estalla, asi que lo que se mide es el ROLLBACK y no una guarda previa.
        int filasAntes = reasignacionesDe(idEncargo);
        Mockito.doThrow(new RuntimeException("fallo inyectado al escribir el historial"))
                .when(rastro).save(ArgumentMatchers.any());

        assertThrows(RuntimeException.class,
                () -> captaciones.reasignar(idEncargo, c.idRolOperativo(), MOTIVO,
                        b.idRolOperativo(), quien));

        assertEquals(b.idRolOperativo(), agenteDe(idEncargo),
                "un agente cambiado sin fila que lo explique es una autoridad que nadie puede "
                        + "explicar: o entran las dos cosas, o no entra ninguna");
        assertEquals(filasAntes, reasignacionesDe(idEncargo));
    }

    // ==================================================================
    // 5. Dirty checking: la puerta que NO es la reasignacion
    // ==================================================================

    /**
     * <b>Una edicion en curso no puede devolver el encargo a quien ya no lo
     * lleva</b> (D-P0-10).
     *
     * <h2>Por que lo hacia</h2>
     * {@code Captacion} no lleva {@code @DynamicUpdate}, asi que el <i>flush</i>
     * de una entidad gestionada escribe la fila <b>entera</b> con los valores
     * que tiene en memoria — incluido {@code id_rol_agente}, con el valor que se
     * leyo al cargar. Si entre la carga y el <i>flush</i> otro comitea una
     * reasignacion A&rarr;B, el {@code UPDATE} de la edicion la pisa y devuelve
     * la columna a A: queda un agente cambiado <b>sin fila que lo explique</b>
     * —lo que D-P0-10 prohibe— y ademas al reves, porque el historial dice «de A
     * a B» sobre un encargo que responde ante A.
     *
     * <h2>Lo que se exige, entero</h2>
     * No basta con que el agente siga siendo B: eso lo cumpliria tambien una
     * edicion que hubiera fallado. Se exige <b>a la vez</b> que el importe nuevo
     * SI este escrito —la edicion era legitima y tenia que entrar—, que el
     * agente siga siendo B, y que no haya aparecido ninguna fila de reasignacion
     * de mas.
     *
     * <h2>Lo que esta prueba dejaba abierto, y ya no (F2.10)</h2>
     * Hasta F2.10 esta prueba terminaba diciendo que la edicion la lanza A
     * cuando <b>todavia</b> lleva el encargo y acaba escribiendo sobre un
     * encargo que ya es de B, y que cerrar esa ventana era «una decision
     * funcional distinta reportada a CONTROL». <b>Esa frase dejo de ser
     * cierta</b>: la ventana se cerro sin decision funcional nueva —la edicion
     * toma la fila del encargo al cargarlo y la autoridad se comprueba bajo el
     * candado—, y lo que le pasa a la edicion que llega tarde lo mide el caso 8.
     * Aqui se mide el orden <b>inverso</b>, que sigue siendo legitimo.
     *
     * <h2>Por que la reasignacion va ahora en OTRO hilo</h2>
     * Se llamaba {@code elFlushDeUnaEdicionNoRevierteLaReasignacion} y lanzaba
     * la reasignacion <b>en el hilo principal</b>. Con el candado ese montaje ya
     * no se puede armar: la edicion tiene la fila tomada, el compare-and-set se
     * queda esperando y el hilo principal —el unico que puede soltar el latch—
     * no vuelve nunca. Se conservan todas las exigencias anteriores y se anade
     * una: que la reasignacion <b>de verdad</b> espero el candado, sondeando
     * {@code pg_stat_activity}. Sin esa sonda, el dia que el candado
     * desapareciera esta prueba seguiria verde por casualidad.
     */
    @Test
    @DisplayName("F2.10: la edicion que tomo la fila escribe, la reasignacion espera su turno y ninguna pisa a la otra")
    void laEdicionQueTomoLaFilaEscribeYLaReasignacionEsperaSuTurno() throws Exception {
        Actor a = agenteDelEquipo(0);
        Actor b = agenteDelEquipo(1);
        Actor quien = broker();
        long idPropiedad = registrar(a);
        long idEncargo = encargoDe(idPropiedad);

        assertEquals(a.idRolOperativo(), agenteDe(idEncargo),
                "el alta tiene que dejar el encargo en manos de quien lo registro: sin eso esta "
                        + "prueba no tiene de donde partir");
        int filasAntes = reasignacionesDe(idEncargo);
        BigDecimal importeAntes = importeDe(idEncargo);
        assertNotNull(importeAntes, "el encargo tiene que nacer con un importe que poder cambiar");

        CountDownLatch cargado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        // El freno va en `save` y ANTES de delegar: es el ultimo punto de
        // `actualizar` en el que la entidad esta gestionada, con su agente (A)
        // en memoria, la FILA TOMADA y todavia sin ningun flush.
        Mockito.doAnswer(invocacion -> {
            if (primera.compareAndSet(false, true)) {
                cargado.countDown();
                assertTrue(liberar.await(20, TimeUnit.SECONDS),
                        "nadie solto a la edicion: la prueba no llego a montar la ventana");
            }
            return comoSiempre(invocacion, encargos);
        }).when(encargos).save(ArgumentMatchers.any());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // E: la edicion legitima de A, parada con el encargo ya cargado.
            Future<?> edicion = hilos.submit(() -> captaciones.actualizar(idEncargo,
                    edicionDeAlquiler(idPropiedad, a, IMPORTE_EDITADO), a));

            assertTrue(cargado.await(20, TimeUnit.SECONDS),
                    "la edicion no llego a guardar el encargo, asi que no hay ninguna "
                            + "transaccion que haya leido A y no haya escrito todavia: sin eso "
                            + "esta prueba no esta midiendo nada");

            // Y mientras esta parada, la reasignacion A->B, que tiene que
            // ESPERAR: su compare-and-set choca con el candado de la edicion.
            Future<FichaCaptacion> reasignacion = hilos.submit(() -> captaciones.reasignar(
                    idEncargo, b.idRolOperativo(), MOTIVO, a.idRolOperativo(), quien));

            esperarAUnaSesionEsperandoUnLock(reasignacion);
            liberar.countDown();

            edicion.get(30, TimeUnit.SECONDS);
            FichaCaptacion hecha = reasignacion.get(30, TimeUnit.SECONDS);
            assertEquals(b.idRolOperativo(), hecha.idAgente(),
                    "la reasignacion desperto y siguio saliendo de A: la edicion escribio el "
                            + "trato, no la autoridad, asi que el estado observado seguia siendo "
                            + "verdad");
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
        }

        assertEquals(0, IMPORTE_EDITADO.compareTo(importeDe(idEncargo)),
                "la edicion era legitima y tenia que entrar: la lanzo A cuando todavia llevaba "
                        + "el encargo. Si el importe no esta escrito, esto no demuestra que el "
                        + "agente se conserve, solo que la edicion no ocurrio");
        assertEquals(b.idRolOperativo(), agenteDe(idEncargo),
                "el encargo volvio a A por el flush de una edicion que no pidio moverlo: un "
                        + "agente cambiado sin fila que lo explique (D-P0-10), y por una puerta "
                        + "que no es la reasignacion. Solo la puerta canonica mueve la autoridad");
        assertEquals(filasAntes + 1, reasignacionesDe(idEncargo),
                "y UNA sola fila: la edicion no puede inventar ni borrar actos de gobierno");
        assertEquals(a.idRolOperativo(), anteriorDeLaUltimaFila(idEncargo),
                "y la fila dice «de A a B», que es lo que de verdad ocurrio");
    }

    // ==================================================================
    // 6. D-P0-7 tambien aqui: quien puede RECIBIR el encargo
    // ==================================================================

    /**
     * <b>El destino tiene que poder recibirlo hoy</b> (D-P0-7 en toda
     * reasignacion de autoridad).
     *
     * <p>Las tres identidades atacadas son las tres que ya existian y no se
     * miraban: cuenta suspendida, agente no operativo y —la que ya estaba
     * cubierta por el alcance— agente del equipo de otro broker. La tercera se
     * mide aqui igual, para que quede en una sola pantalla que <b>alcance</b> y
     * <b>elegibilidad</b> son dos preguntas distintas: la primera la rechaza
     * {@code alcances.alcanza} (medida tambien en
     * {@code CaptacionServiceImplTest.reasignarAUnAgenteFueraDeSuCarteraRespondeElMensajeV1}),
     * la segunda {@code exigirElegible}.
     *
     * <p>Los estados se restituyen por clave primaria en el {@code finally}: la
     * base de pruebas es compartida y una credencial que se quedara en 'I'
     * romperia pruebas que no tienen nada que ver.
     */
    @Test
    @DisplayName("D-P0-7: un destino que no puede recibir el encargo es 403, y no escribe nada")
    void elDestinoNoElegibleNoRecibeElEncargo() {
        Actor a = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor quien = broker();
        long idEncargo = encargoDe(a);
        int filas = reasignacionesDe(idEncargo);

        // (a) CUENTA SUSPENDIDA. El broker lo supervisa -pasa `alcanza`- y aun
        //     asi no puede recibir: son dos invariantes distintas.
        String credencialAntes = estadoAdministrativoDe(destino.idRolOperativo());
        try {
            fijarEstadoAdministrativo(destino.idRolOperativo(), "I");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> captaciones.reasignar(idEncargo, destino.idRolOperativo(), MOTIVO,
                            a.idRolOperativo(), quien),
                    "un agente con la cuenta suspendida no puede recibir un encargo, igual que "
                            + "no puede recibir una propiedad");
            assertEquals(a.idRolOperativo(), agenteDe(idEncargo), "y no se movio nada");
            assertEquals(filas, reasignacionesDe(idEncargo), "ni fila en el historial");

            // Y el GOBIERNO DEL TENANT tampoco puede: la elegibilidad es del
            // destino, no del actor. Un TENANT_ADMIN esta exento de la
            // supervision, nunca de las cinco condiciones de la persona.
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> captaciones.reasignar(idEncargo, destino.idRolOperativo(), MOTIVO,
                            a.idRolOperativo(), tenantAdmin()),
                    "gobernar la organizacion no convierte a un agente suspendido en uno que "
                            + "puede recibir trabajo");
            assertEquals(a.idRolOperativo(), agenteDe(idEncargo));
        } finally {
            fijarEstadoAdministrativo(destino.idRolOperativo(), credencialAntes);
        }

        // CONTROL POSITIVO: restituida la cuenta, el MISMO comando entra. Sin
        // esto, los rechazos de arriba podrian venir de cualquier otra cosa.
        captaciones.reasignar(idEncargo, destino.idRolOperativo(), MOTIVO, a.idRolOperativo(),
                quien);
        assertEquals(destino.idRolOperativo(), agenteDe(idEncargo));
        assertEquals(filas + 1, reasignacionesDe(idEncargo));

        // (b) NO OPERATIVO. Otra de las cinco, contra la autoridad que la decide
        //     (detalle_agente.estado_operativo), y con el encargo ya en manos
        //     del destino anterior.
        String operativoAntes = estadoOperativoDe(a.idRolOperativo());
        try {
            fijarEstadoOperativo(a.idRolOperativo(), "L");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> captaciones.reasignar(idEncargo, a.idRolOperativo(), MOTIVO,
                            destino.idRolOperativo(), quien),
                    "un agente de licencia sigue siendo del equipo y sigue teniendo cuenta: lo "
                            + "que no esta es en condiciones de recibir trabajo nuevo");
            assertEquals(destino.idRolOperativo(), agenteDe(idEncargo));
            assertEquals(filas + 1, reasignacionesDe(idEncargo));
        } finally {
            fijarEstadoOperativo(a.idRolOperativo(), operativoAntes);
        }
    }

    // ==================================================================
    // 7. D-P0-8 para el ENCARGO: desactivar no reasigna
    // ==================================================================

    /**
     * <b>Desactivar a un agente no le quita los encargos que lleva</b>
     * (D-P0-8, dicho sobre la otra autoridad).
     *
     * <p>Es la contrapartida exacta de la prueba de arriba: dejar de poder
     * <b>recibir</b> no es dejar de <b>llevar</b>. Un sistema que reasignara
     * solo al desactivar estaria decidiendo por su cuenta a quien pasan los
     * encargos, sin motivo, sin actor y sin fila que lo explique — que es
     * justamente lo que este corte entero existe para impedir.
     *
     * <p>Y la salida existe: el gobierno del tenant puede sacarlos <b>despues</b>,
     * de forma explicita y trazable. Sin este control positivo, «no se mueven»
     * seria indistinguible de «no se pueden mover».
     */
    @Test
    @DisplayName("D-P0-8: desactivar a un agente no reasigna sus encargos, y el gobierno puede moverlos despues")
    void desactivarNoReasignaLosEncargos() {
        Actor a = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        long idEncargo = encargoDe(a);
        int filas = reasignacionesDe(idEncargo);

        String operativoAntes = estadoOperativoDe(a.idRolOperativo());
        try {
            agentes.actualizar(a.idRolOperativo(), datosDeBaja(), tenantAdmin());

            assertEquals("N", estadoOperativoDe(a.idRolOperativo()),
                    "el montaje tiene que haber desactivado de verdad al agente");
            assertEquals(a.idRolOperativo(), agenteDe(idEncargo),
                    "el encargo sigue siendo suyo: desactivar no es reasignar, y un movimiento "
                            + "de autoridad sin actor ni motivo no es un hecho que nadie pueda "
                            + "explicar despues");
            assertEquals(filas, reasignacionesDe(idEncargo),
                    "y no hay fila en el historial, porque no ocurrio ninguna reasignacion");

            // Y el gobierno del tenant SI puede sacarlos, que es la salida.
            captaciones.reasignar(idEncargo, destino.idRolOperativo(), MOTIVO,
                    a.idRolOperativo(), tenantAdmin());
            assertEquals(destino.idRolOperativo(), agenteDe(idEncargo));
            assertEquals(filas + 1, reasignacionesDe(idEncargo));
        } finally {
            fijarEstadoOperativo(a.idRolOperativo(), operativoAntes);
        }
    }

    // ==================================================================
    // 8. D-P0-13: la elegibilidad no caduca a mitad de una reasignacion
    // ==================================================================

    /**
     * <b>El TOCTOU de la elegibilidad, sobre la autoridad del ENCARGO</b>
     * (D-P0-13).
     *
     * <p>Es la gemela exacta del caso de la propiedad, y esta escrita aparte
     * porque la ventana es la misma pero el objeto es otro: si el candado solo
     * cubriera el traspaso de propiedades, la reasignacion de encargos seguiria
     * pudiendo entregar trabajo a alguien que quedo de baja entre la
     * comprobacion y la escritura.
     *
     * <p>No se exige un desenlace concreto: se exige que <b>haya un orden</b>.
     * Con la fila del destino tomada, la baja <b>espera</b>; que el encargo
     * acabe en un agente que despues queda de licencia es la secuencia causal
     * real —recibio, y luego lo desactivaron—, que es D-P0-8 y no un fallo.
     *
     * <p>El caso <b>inverso</b> —baja comiteada primero— va en la misma prueba:
     * es el que demuestra que el candado ordena la carrera sin perdonar la
     * guarda.
     */
    @Test
    @DisplayName("D-P0-13: desactivar al destino durante una reasignacion espera, y despues no la deshace")
    void laElegibilidadDelDestinoNoCaducaAMitadDeUnaReasignacion() throws Exception {
        Actor a = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor quien = broker();
        long idEncargo = encargoDe(a);
        long b = destino.idRolOperativo();

        int filas = reasignacionesDe(idEncargo);
        String operativoAntes = estadoOperativoDe(b);
        assertEquals("D", operativoAntes,
                "el destino tiene que empezar disponible o esta prueba no parte de una "
                        + "reasignacion legitima");

        CountDownLatch enElRastro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        // El freno va DESPUES de exigirElegible y DESPUES del CAS: cuando esto
        // se ejecuta, T1 tiene la fila de `detalle_agente` del destino tomada y
        // su transaccion abierta. Es el instante exacto de la ventana.
        Mockito.doAnswer(invocacion -> {
            if (primera.compareAndSet(false, true)) {
                enElRastro.countDown();
                assertTrue(liberar.await(20, TimeUnit.SECONDS),
                        "nadie solto a T1: la prueba no llego a montar la ventana");
            }
            return comoSiempre(invocacion, rastro);
        }).when(rastro).save(ArgumentMatchers.any());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<FichaCaptacion> t1 = hilos.submit(() -> captaciones.reasignar(idEncargo, b,
                    MOTIVO, a.idRolOperativo(), quien));

            assertTrue(enElRastro.await(20, TimeUnit.SECONDS),
                    "T1 no llego a escribir el rastro, asi que no hay bloqueo del destino "
                            + "tomado: sin eso esta prueba no esta midiendo ninguna ventana");

            Future<?> baja = hilos.submit(() -> agentes.actualizar(b, deLicencia(), tenantAdmin()));
            esperarAUnaSesionEsperandoUnLock(baja);

            liberar.countDown();
            t1.get(20, TimeUnit.SECONDS);
            baja.get(20, TimeUnit.SECONDS);
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
            fijarEstadoOperativo(b, operativoAntes);
        }

        assertEquals(b, agenteDe(idEncargo),
                "la reasignacion decidio con la fila del destino tomada, asi que su elegibilidad "
                        + "no pudo cambiar entre la comprobacion y la escritura: entra");
        assertEquals(filas + 1, reasignacionesDe(idEncargo),
                "y deja UNA fila: la reasignacion ocurrio entera");

        // Y AL REVES: con la baja ya comiteada, la misma reasignacion se
        // rechaza y no escribe nada.
        long idOtro = encargoDe(a);
        int filasDelOtro = reasignacionesDe(idOtro);
        try {
            agentes.actualizar(b, deLicencia(), tenantAdmin());
            assertEquals("L", estadoOperativoDe(b), "el montaje tiene que dejarlo de licencia");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> captaciones.reasignar(idOtro, b, MOTIVO, a.idRolOperativo(), quien),
                    "con la baja ya comiteada, el destino no puede recibir: el candado ordena la "
                            + "carrera, no perdona la guarda");
            assertEquals(a.idRolOperativo(), agenteDe(idOtro), "y no se movio nada");
            assertEquals(filasDelOtro, reasignacionesDe(idOtro), "sin fila en el historial");
        } finally {
            fijarEstadoOperativo(b, operativoAntes);
        }
        assertEquals(operativoAntes, estadoOperativoDe(b),
                "el estado del agente queda como estaba: la base de pruebas es compartida");
    }

    /** Lo minimo para poner de licencia a un agente por su caso de uso. */
    private AgenteService.DatosAgente deLicencia() {
        return new AgenteService.DatosAgente(null, null, null, null, null, null, null, null, null,
                null, null, "L", null);
    }

    // ==================================================================
    // 8. F2.10: la autoridad de EDICION del encargo tampoco caduca entre
    //    comprobar y escribir
    // ==================================================================

    /**
     * <b>La edicion del agente saliente que llega tarde no aterriza</b> (F2.10).
     *
     * <h2>Que defecto ataca</h2>
     * Es el gemelo exacto del caso 8 del traspaso, dicho sobre P0-4.
     * {@code PUT /captaciones/&#123;id&#125;} cargaba el encargo, preguntaba
     * {@code exigirEdicionDelEncargo} sobre lo que acababa de leer y escribia
     * <b>despues</b>, sin nada que sujetara la fila entre las dos cosas. En esa
     * ventana cabe una reasignacion entera —su compare-and-set toma la fila un
     * instante y la suelta al comitear— y la edicion del agente <b>saliente</b>
     * aterrizaba sobre un encargo que ya es de otro: importe, exclusividad,
     * vigencia y urgencia reescritos por quien ya no lo lleva.
     *
     * <h2>Que NO arreglaba F2.2</h2>
     * {@code updatable = false} sobre {@code id_rol_agente} impide que esa
     * edicion tardia <b>revierta</b> la autoridad —eso lo mide el caso 5—. Lo
     * que nunca impidio es que la edicion <b>se escriba</b>.
     *
     * <h2>Cual de las dos guardas rechaza, y por que se afirma asi</h2>
     * Se exige {@link AccesoNoAutorizadoException}, que es el 403 que el Core ya
     * produce, y <b>no</b> un texto concreto: para un AGENTE, la primera guarda
     * que ve al nuevo dueno es el <b>alcance</b> —{@code cargarConAcceso}, que
     * dice que no antes de llegar a {@code exigirEdicionDelEncargo}—, y ese
     * orden es contrato desde D-S0-17. Afirmar aqui el texto de la segunda seria
     * congelar cual de las dos llega primero, que no es lo que este corte
     * decide. Que la segunda tambien deniega lo miden las pruebas de P0-4.
     *
     * <p>Y se exige la <b>conservacion</b>: el importe tiene que quedar
     * exactamente como estaba. Un 403 despues de haber escrito no protege nada.
     */
    @Test
    @DisplayName("F2.10: la edicion del agente saliente que llega tras la reasignacion espera el candado, recibe 403 y no escribe")
    void laEdicionTardiaDelAgenteSalienteNoAterriza() throws Exception {
        Actor a = agenteDelEquipo(0);
        Actor b = agenteDelEquipo(1);
        Actor quien = broker();
        long idPropiedad = registrar(a);
        long idEncargo = encargoDe(idPropiedad);

        assertEquals(a.idRolOperativo(), agenteDe(idEncargo),
                "el alta tiene que dejar el encargo en manos de quien lo registro: sin eso no "
                        + "hay saliente que pueda llegar tarde");
        BigDecimal importeAntes = importeDe(idEncargo);
        assertNotNull(importeAntes, "el encargo tiene que nacer con un importe que poder cambiar");
        assertNotEquals(0, IMPORTE_EDITADO.compareTo(importeAntes),
                "el importe de partida tiene que ser DISTINTO del que intenta escribir la "
                        + "edicion, o «quedo intacto» no distinguiria nada");
        int filasAntes = reasignacionesDe(idEncargo);

        CountDownLatch enElRastro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        // El freno va en el rastro porque es la primera escritura DESPUES del
        // compare-and-set: cuando esto se ejecuta, la fila de `captacion` ya
        // esta tomada por T y su transaccion sigue abierta.
        Mockito.doAnswer(invocacion -> {
            if (primera.compareAndSet(false, true)) {
                enElRastro.countDown();
                assertTrue(liberar.await(20, TimeUnit.SECONDS),
                        "nadie solto a T: la prueba no llego a montar la ventana");
            }
            return comoSiempre(invocacion, rastro);
        }).when(rastro).save(ArgumentMatchers.any());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<FichaCaptacion> t = hilos.submit(() -> captaciones.reasignar(idEncargo,
                    b.idRolOperativo(), MOTIVO, a.idRolOperativo(), quien));

            assertTrue(enElRastro.await(20, TimeUnit.SECONDS),
                    "T no llego a escribir el rastro, asi que no hay compare-and-set ejecutado "
                            + "ni fila tomada: sin eso esta prueba no esta midiendo ninguna "
                            + "ventana");

            // E: la edicion del SALIENTE, con datos validos, que arranca cuando
            // la reasignacion ya decidio pero todavia no ha comiteado.
            Future<?> edicion = hilos.submit(() -> captaciones.actualizar(idEncargo,
                    edicionDeAlquiler(idPropiedad, a, IMPORTE_EDITADO), a));

            esperarAUnaSesionEsperandoUnLock(edicion);
            liberar.countDown();

            t.get(20, TimeUnit.SECONDS);

            ExecutionException tardia = assertThrows(ExecutionException.class,
                    () -> edicion.get(20, TimeUnit.SECONDS),
                    "la edicion desperto sobre un encargo que ya lleva B: A dejo de poder "
                            + "escribirlo en el mismo instante en que la reasignacion comiteo, y "
                            + "una edicion que aterriza despues es la autoridad comprobada sobre "
                            + "un estado que ya no existe");
            assertInstanceOf(AccesoNoAutorizadoException.class, tardia.getCause(),
                    "y el rechazo es el 403 de siempre, no un error tecnico ni un conflicto: no "
                            + "hay ninguna regla nueva, hay una regla vieja comprobada a tiempo. "
                            + "Fue: " + tardia.getCause());
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
        }

        assertEquals(0, importeAntes.compareTo(importeDe(idEncargo)),
                "la edicion del saliente ATERRIZO: el importe del trato quedo reescrito por "
                        + "quien ya no lleva el encargo, y con el su hito en la serie economica");
        assertEquals(b.idRolOperativo(), agenteDe(idEncargo),
                "y la reasignacion, que gano la carrera, sigue en pie");
        assertEquals(filasAntes + 1, reasignacionesDe(idEncargo),
                "UNA fila de reasignacion: la edicion rechazada no inventa ni borra actos de "
                        + "gobierno");
    }

    // ==================================================================
    // Herramientas de la carrera
    // ==================================================================

    /**
     * <b>Deja que el espia haga lo de siempre.</b>
     *
     * <p>{@code invocacion.callRealMethod()} <b>no</b> sirve aqui: el bean de un
     * repositorio de Spring Data es un proxy JDK, asi que el espia se crea sobre
     * la <b>interfaz</b> y delega en el original a traves de su
     * {@code defaultAnswer}. Llamar al «metodo real» de una interfaz no existe.
     */
    private static Object comoSiempre(InvocationOnMock invocacion, Object espia) throws Throwable {
        Object resultado = Mockito.mockingDetails(espia).getMockCreationSettings()
                .getDefaultAnswer().answer(invocacion);
        assertNotNull(resultado,
                "el espia no delego en el repositorio real: sin eso, la prueba estaria midiendo "
                        + "una reasignacion que no escribe nada");
        return resultado;
    }

    /**
     * <b>Espera a ver una sesion de verdad esperando un lock</b>, o falla.
     *
     * <p>Es la diferencia entre «no hubo carrera» y «no llegue a montarla», y va
     * por una conexion propia <b>fuera del pool</b>: durante la carrera hay dos
     * transacciones ocupando conexiones, y una sonda que compitiera por la
     * tercera podria quedarse esperando justo mientras el bloqueo que viene a
     * medir esta ocurriendo.
     */
    private void esperarAUnaSesionEsperandoUnLock(Future<?> t2) throws Exception {
        try (Connection sonda = conexionDeSonda()) {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < limite) {
                if (bloqueadas(sonda) > 0) {
                    return;
                }
                Thread.sleep(50);
            }
            fail("en 10 segundos ninguna sesion se quedo esperando un lock, asi que la segunda "
                    + "reasignacion NUNCA llego al UPDATE de la fila: lo que fuera que "
                    + "respondiera despues no seria la carrera que esta prueba dice medir. T2 "
                    + (t2.isDone() ? "ya habia terminado: " + resultadoDe(t2) : "seguia en marcha")
                    + ". Sesiones vivas: " + sesiones(sonda));
        }
    }

    private Connection conexionDeSonda() throws SQLException {
        return DriverManager.getConnection(BaseDeDatosDePruebas.url(),
                entorno.getProperty("spring.datasource.username", "controllocal"),
                entorno.getProperty("spring.datasource.password", "controllocal"));
    }

    private int bloqueadas(Connection sonda) throws SQLException {
        try (Statement consulta = sonda.createStatement();
             ResultSet filas = consulta.executeQuery("""
                     select count(*) from pg_stat_activity
                      where datname = current_database() and wait_event_type = 'Lock'
                     """)) {
            filas.next();
            return filas.getInt(1);
        }
    }

    private String resultadoDe(Future<?> futuro) {
        try {
            return String.valueOf(futuro.get(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            return String.valueOf(e.getCause() == null ? e : e.getCause());
        }
    }

    private String sesiones(Connection sonda) throws SQLException {
        StringBuilder texto = new StringBuilder();
        try (Statement consulta = sonda.createStatement();
             ResultSet filas = consulta.executeQuery("""
                     select pid, state, wait_event_type, wait_event, left(query, 90) as consulta
                       from pg_stat_activity
                      where datname = current_database()
                      order by pid
                     """)) {
            while (filas.next()) {
                texto.append("\n  pid=").append(filas.getInt("pid"))
                        .append(" state=").append(filas.getString("state"))
                        .append(" espera=").append(filas.getString("wait_event_type"))
                        .append('/').append(filas.getString("wait_event"))
                        .append(" consulta=")
                        .append(String.valueOf(filas.getString("consulta")).replace('\n', ' '));
            }
        }
        return texto.toString();
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    /** Una propiedad nueva con su encargo de ALQUILER, en manos de quien registra. */
    private long registrar(Actor quien) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Caso de causalidad del encargo",
                new Ubicacion("Av. Encargo " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("3000"), "PEN",
                        null, null, null, null, null, null, null)),
                null), quien).idPropiedad();
    }

    private long encargoDe(Actor quien) {
        return encargoDe(registrar(quien));
    }

    private long encargoDe(long idPropiedad) {
        Long id = jdbc.queryForObject("""
                select id_captacion from captacion
                 where id_propiedad = ? and motivo_operacion = 'A' and estado <> 'C'
                 order by id_captacion desc limit 1
                """, Long.class, idPropiedad);
        assertNotNull(id, "el alta con operacion ALQUILER tiene que dejar su encargo abierto");
        return id;
    }

    /**
     * Un cuerpo <b>valido</b> para {@code PUT /captaciones/{id}} sobre un
     * encargo de ALQUILER: la operacion no se edita, la vigencia es obligatoria
     * y la comision de un alquiler se calcula sobre la renta y en su moneda.
     * Cualquier hueco convertiria el ataque en un rechazo de validacion.
     */
    private CaptacionService.DatosCaptacion edicionDeAlquiler(long idPropiedad, Actor deQuien,
                                                              BigDecimal importe) {
        return new CaptacionService.DatosCaptacion(
                "CAP-CAUSAL-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusMonths(6),
                null, "Edicion concurrente del encargo de alquiler",
                idPropiedad, deQuien.idRolOperativo(), "ALQUILER", 4, Boolean.FALSE,
                "ALQUILER", importe, "PEN",
                "P", "R", new BigDecimal("5"), "PEN", "I", null);
    }

    /**
     * Lo minimo para desactivar operativamente a un agente <b>por su caso de
     * uso</b> y no con un UPDATE a mano: lo que se quiere medir es que la via
     * real de la baja no arrastra los encargos.
     */
    private AgenteService.DatosAgente datosDeBaja() {
        return new AgenteService.DatosAgente(null, null, null, null, null, null, null, null, null,
                null, null, "N", null);
    }

    /**
     * Agentes del MISMO equipo. Hacen falta <b>tres</b> y no dos: los casos de
     * este corte necesitan un origen y <b>dos</b> destinos distintos —A&rarr;B y
     * A&rarr;C partiendo del mismo A—, que es justamente lo que no se puede
     * montar con solo dos.
     */
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
        assertTrue(filas.size() >= 3,
                "hacen falta TRES agentes del mismo equipo para montar A->B y A->C desde el "
                        + "mismo A: encontro " + filas.size());
        return new Actor(((Number) filas.get(indice).get("organizacion_id")).longValue(),
                ((Number) filas.get(indice).get("id_persona")).longValue(),
                ((Number) filas.get(indice).get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    private Long idBrokerConEquipo() {
        Long id = jdbc.queryForObject("""
                select s.id_rol_broker from supervision_agente s
                 where s.fecha_fin is null
                 group by s.id_rol_broker, s.organizacion_id
                having count(*) >= 3
                 order by count(*) desc, s.id_rol_broker limit 1
                """, Long.class);
        assertNotNull(id, "sin un broker que supervise a tres agentes no hay escenario");
        return id;
    }

    private Actor broker() {
        Map<String, Object> fila = jdbc.queryForList("""
                select b.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_broker b join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where b.id_persona_rol = ?
                """, idBrokerConEquipo()).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.BROKER);
    }

    /** El gobierno del tenant, con la misma identidad y otra banda. */
    private Actor tenantAdmin() {
        Actor base = broker();
        return new Actor(base.idOrganizacion(), base.idPersona(), base.idRolOperativo(),
                Actor.TENANT_ADMIN);
    }

    private Long unPropietario(Actor actor) {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
    }

    // ==================================================================
    // Lecturas y montajes directos
    // ==================================================================

    private Long agenteDe(long idCaptacion) {
        return jdbc.queryForObject(
                "select id_rol_agente from captacion where id_captacion = ?",
                Long.class, idCaptacion);
    }

    private BigDecimal importeDe(long idCaptacion) {
        return jdbc.queryForObject("""
                select ce.importe_referencia from captacion c
                  join condicion_economica_captacion ce
                    on ce.id_condicion_economica = c.id_condicion_economica
                 where c.id_captacion = ?
                """, BigDecimal.class, idCaptacion);
    }

    private int reasignacionesDe(long idCaptacion) {
        Integer total = jdbc.queryForObject(
                "select count(*) from reasignacion_captacion where id_captacion = ?",
                Integer.class, idCaptacion);
        assertNotNull(total);
        return total;
    }

    private Long anteriorDeLaUltimaFila(long idCaptacion) {
        List<Long> filas = jdbc.queryForList("""
                select id_rol_agente_anterior from reasignacion_captacion
                 where id_captacion = ?
                 order by id_reasignacion desc limit 1
                """, Long.class, idCaptacion);
        assertFalse(filas.isEmpty(), "no hay ninguna fila de reasignacion que mirar");
        return filas.get(0);
    }

    private String estadoAdministrativoDe(long idRolAgente) {
        return jdbc.queryForObject("""
                select c.estado_administrativo from credencial_usuario c
                  join persona_rol ru on ru.id_persona_rol = c.id_persona_rol
                  join persona_rol ra on ra.id_persona = ru.id_persona
                 where ra.id_persona_rol = ? and ru.vigencia_hasta is null
                """, String.class, idRolAgente);
    }

    private void fijarEstadoAdministrativo(long idRolAgente, String estado) {
        jdbc.update("""
                update credencial_usuario set estado_administrativo = ?
                 where id_persona_rol in (
                     select ru.id_persona_rol from persona_rol ru
                       join persona_rol ra on ra.id_persona = ru.id_persona
                      where ra.id_persona_rol = ? and ru.vigencia_hasta is null)
                """, estado, idRolAgente);
    }

    private String estadoOperativoDe(long idRolAgente) {
        return jdbc.queryForObject(
                "select estado_operativo from detalle_agente where id_persona_rol = ?",
                String.class, idRolAgente);
    }

    private void fijarEstadoOperativo(long idRolAgente, String estado) {
        jdbc.update("update detalle_agente set estado_operativo = ? where id_persona_rol = ?",
                estado, idRolAgente);
    }
}
