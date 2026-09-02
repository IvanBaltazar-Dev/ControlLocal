package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AgenteService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.ResponsableObservado;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.TraspasoDeResponsable;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.ComandosIdempotentes;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Un traspaso parte del estado que alguien miro, y ocurre entero o no
 * ocurre</b> (D-P0-9 y D-P0-10).
 *
 * <h2>Que estaba mal</h2>
 * Las guardas de P0 decidian <b>quien</b> podia traspasar y <b>a quien</b>.
 * Ninguna decidia <b>desde donde</b>. Dos comandos que salieran del mismo
 * responsable A —uno hacia B y otro hacia C— pasaban exactamente las mismas
 * comprobaciones, y el segundo pisaba al primero: la ultima escritura ganaba, y
 * el expediente quedaba diciendo «de A a C» sobre una propiedad que en ese
 * momento ya llevaba B. Nadie habia decidido eso.
 *
 * <h2>Que se congela, que no</h2>
 * Lo congelado es el <b>comportamiento observable</b>: de un estado concreto
 * puede partir <b>exactamente una</b> transicion legitima; la segunda es
 * <b>409</b> y <b>no</b> se reinterpreta sobre el estado nuevo. El mecanismo
 * —precondicion en memoria mas compare-and-set en SQL— es una eleccion, no un
 * contrato: lo que estas pruebas atacan es el comportamiento.
 *
 * <h2>Por que hacen falta DOS transacciones de verdad</h2>
 * Una prueba secuencial mide el comando <b>obsoleto</b>: el que sale de un
 * estado que ya cambio y se puede detectar leyendo. La <b>carrera</b> es otra
 * cosa —dos transacciones vivas a la vez, las dos habiendo leido A, y el
 * ganador decidido dentro de la base—, y solo se puede provocar teniendo una
 * detenida <b>dentro</b> de su transaccion con la fila ya bloqueada. Por eso
 * aqui hay hilos, latches y un sondeo de {@code pg_stat_activity}: si el
 * bloqueo no llega a existir, la prueba no da por buena la ausencia de carrera,
 * <b>falla</b>.
 *
 * <h2>Y por que se inyectan fallos</h2>
 * «Es atomico porque el metodo lleva {@code @Transactional}» es una lectura del
 * codigo, no una comprobacion. D-P0-10 exige que no quede responsable cambiado
 * sin traza, ni traza de un cambio que no ocurrio, y eso solo se demuestra
 * <b>rompiendo</b> cada escritura por separado y mirando la base despues.
 *
 * <h2>Lo que estas pruebas NO cubren</h2>
 * No repiten las guardas de banda, tenant, alcance ni elegibilidad: eso lo mide
 * {@code AlcanceYGobiernoDeLaAutoridadIntegrationTest}, y duplicarlo aqui daria
 * dos sitios donde la misma regla puede divergir. Aqui todo actor es legitimo y
 * todo destino elegible: lo unico bajo ataque es <b>de que estado se parte</b> y
 * <b>que queda escrito</b>.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CausalidadDelTraspasoIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry registro) {
        BaseDeDatosDePruebas.registrar(registro);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    /**
     * El gobierno del agente, para atacar la <b>otra</b> mitad de la ventana de
     * D-P0-13: no basta con desactivarlo por SQL, porque lo que hay que probar
     * es que la <b>via real</b> —el caso de uso— toma el mismo candado.
     */
    @Autowired AgenteService agentes;
    @Autowired Environment entorno;

    /**
     * <b>Los dos escritores del hecho, espiados</b> (D-P0-10).
     *
     * <p>Son espias y no mocks: por defecto hacen lo de siempre —el traspaso
     * entero funciona— y solo se les cambia el comportamiento en la prueba que
     * necesita romperlos o detenerlos. Un mock permisivo dejaria la suite verde
     * sin escribir nada, que es justo el estado que estas pruebas denuncian.
     */
    @MockitoSpyBean AsignacionResponsablePropiedadRepository rastro;
    @MockitoSpyBean EventoDominioRepository eventos;

    /**
     * <b>El freno de la edicion</b>, y esta pieza y no otra por una razon
     * medida: {@code ComandosIdempotentes#buscar} es lo que
     * {@code PropiedadUniversalServiceImpl#editar} llama <b>despues</b> de
     * cargar la entidad —y de exigir la autoridad— y <b>antes</b> de escribir
     * la primera letra. Es el unico punto del metodo donde la propiedad ya esta
     * gestionada, con su responsable en memoria, y todavia no ha ocurrido
     * ninguna escritura.
     *
     * <p>Un freno puesto en el {@code save} final llegaria tarde: para entonces
     * la transaccion ya habria escrito otras filas y el bloqueo de la carrera
     * seria otro. Uno puesto en el repositorio de la propiedad no serviria,
     * porque {@code editar} lo consulta tambien para <b>leer</b>.
     */
    @MockitoSpyBean ComandosIdempotentes comandos;

    private static final String MOTIVO =
            "Reasignacion por reparto de cartera del trimestre";

    /**
     * La descripcion que escribe la edicion concurrente. Distinta de la del
     * alta a proposito: es lo que permite afirmar que la edicion <b>entro</b>,
     * y no solo que el responsable no se movio.
     */
    private static final String DESCRIPCION_EDITADA =
            "Descripcion reescrita durante un traspaso";

    /**
     * Los espias vuelven a su comportamiento normal entre pruebas. Sin esto,
     * un {@code doThrow} de una prueba de rollback rompería la siguiente en un
     * sitio que no tiene nada que ver.
     */
    @AfterEach
    void devolverLosEspias() {
        Mockito.reset(rastro, eventos, comandos);
    }

    // ==================================================================
    // 1. El comando obsoleto: llego tarde y NO se reinterpreta
    // ==================================================================

    @Test
    @DisplayName("D-P0-9: el comando que sale de A cuando ya responde B es 409 y no se convierte en «de B a C»")
    void elComandoObsoletoNoSeReinterpreta() {
        Actor duena = agenteDelEquipo(0);
        Actor haciaB = agenteDelEquipo(1);
        Actor haciaC = agenteDelEquipo(2);
        Actor quien = broker();
        long idPropiedad = registrar(duena);
        long a = duena.idRolOperativo();

        // T1 gana: parte de A, que es lo que hay.
        propiedades.asignarResponsable(idPropiedad, haciaB.idRolOperativo(), MOTIVO,
                ResponsableObservado.de(a), quien);
        assertEquals(haciaB.idRolOperativo(), responsableDe(idPropiedad));

        int traspasos = traspasosDe(idPropiedad);
        int eventosDeLaPropiedad = eventosDe(idPropiedad);

        // T2 salio del MISMO A. Llega tarde.
        ConflictoException rechazo = assertThrows(ConflictoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, haciaC.idRolOperativo(), MOTIVO,
                        ResponsableObservado.de(a), quien),
                "dos traspasos que parten del mismo responsable no pueden entrar los dos: eso es "
                        + "«la ultima escritura gana», y de un estado concreto sale UNA sola "
                        + "transicion legitima");

        assertEquals(haciaB.idRolOperativo(), responsableDe(idPropiedad),
                "y el que gano sigue siendo el primero: el segundo no pisa nada");
        assertEquals(traspasos, traspasosDe(idPropiedad),
                "el rechazo no deja fila en el expediente");
        assertEquals(eventosDeLaPropiedad, eventosDe(idPropiedad),
                "ni evento: un traspaso que no ocurrio no se anuncia");

        // Y el mensaje NO reinterpreta. Dice donde esta la propiedad hoy -- que
        // es lo unico que permite volver a decidir-- y dice explicitamente que
        // el traspaso no se ha convertido en otro.
        assertTrue(rechazo.getMessage().contains(String.valueOf(haciaB.idRolOperativo())),
                "el rechazo tiene que decir quien responde HOY, o el broker no puede hacer otra "
                        + "cosa que reintentar a ciegas -- que es como se pisan dos traspasos. "
                        + "Decia: " + rechazo.getMessage());
        assertTrue(rechazo.getMessage().contains("reinterpreta"),
                "y tiene que decir que NO se ha reinterpretado sobre el estado nuevo: "
                        + rechazo.getMessage());

        // CONTROL POSITIVO: el comando no era invalido, estaba desactualizado.
        // Con el estado que hay de verdad, el mismo traspaso entra.
        TraspasoDeResponsable segundo = propiedades.asignarResponsable(idPropiedad,
                haciaC.idRolOperativo(), MOTIVO,
                ResponsableObservado.de(haciaB.idRolOperativo()), quien);
        assertEquals(haciaC.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(haciaB.idRolOperativo(), segundo.idResponsableAnterior(),
                "y el expediente dice de donde salio de verdad: de B, no de A");
        assertEquals(traspasos + 1, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad + 1, eventosDe(idPropiedad));
    }

    // ==================================================================
    // 2. El observado equivocado, sin carrera ninguna
    // ==================================================================

    @Test
    @DisplayName("D-P0-9: declarar un responsable que no es el actual es 409, y no escribe nada")
    void elObservadoEquivocadoNoEscribeNada() {
        Actor duena = agenteDelEquipo(0);
        Actor otro = agenteDelEquipo(1);
        Actor destino = agenteDelEquipo(2);
        Actor quien = broker();
        long idPropiedad = registrar(duena);

        int traspasos = traspasosDe(idPropiedad);
        int eventosDeLaPropiedad = eventosDe(idPropiedad);

        ConflictoException rechazo = assertThrows(ConflictoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                        ResponsableObservado.de(otro.idRolOperativo()), quien),
                "aqui no hay carrera ninguna: el comando simplemente declara un estado de partida "
                        + "que no es el que hay. Un traspaso que no sabe de donde sale no es un "
                        + "traspaso");

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "y no se ha movido nada");
        assertEquals(traspasos, traspasosDe(idPropiedad), "sin fila en el expediente");
        assertEquals(eventosDeLaPropiedad, eventosDe(idPropiedad), "y sin evento");

        // El rechazo dice el estado de HOY. Esta comprobacion es la que cae si
        // se quita la precondicion en memoria: el compare-and-set tambien
        // rechazaria -- con razon-- pero no puede decir quien responde ahora,
        // porque la transaccion que pierde la carrera no ha visto al ganador.
        assertTrue(rechazo.getMessage().contains(String.valueOf(duena.idRolOperativo())),
                "el 409 tiene que nombrar al responsable actual para que se pueda volver a "
                        + "decidir sobre el. Decia: " + rechazo.getMessage());

        // CONTROL POSITIVO: con el observado correcto, el mismo comando entra.
        propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                ResponsableObservado.de(duena.idRolOperativo()), quien);
        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(traspasos + 1, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad + 1, eventosDe(idPropiedad));
    }

    // ==================================================================
    // 3. FALTANTE es un estado observado, no una casilla vacia
    // ==================================================================

    @Test
    @DisplayName("D-P0-9: FALTANTE es un estado que se declara, y solo cuadra con FALTANTE")
    void faltanteEsUnEstadoObservadoYNoUnComodin() {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor quien = broker();
        long idPropiedad = registrar(duena);

        // (a) «La vi sin responsable» sobre una propiedad que SI lo tiene.
        //     No es un comodin que valga para cualquier estado: es una
        //     afirmacion, y es falsa.
        int traspasos = traspasosDe(idPropiedad);
        int eventosDeLaPropiedad = eventosDe(idPropiedad);
        assertThrows(ConflictoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                        ResponsableObservado.faltante(), quien),
                "declarar FALTANTE sobre una propiedad con responsable es declarar algo que no "
                        + "es. Si esto entrara, el traspaso se estaria haciendo a ciegas sobre un "
                        + "agente que el actor no vio");
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(traspasos, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad, eventosDe(idPropiedad));

        // (b) Y al reves: «respondia A» sobre una que esta FALTANTE de verdad.
        dejarSinResponsable(idPropiedad);
        assertNull(responsableDe(idPropiedad), "el montaje tiene que dejarla FALTANTE de verdad");
        assertThrows(ConflictoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, destino.idRolOperativo(), MOTIVO,
                        ResponsableObservado.de(duena.idRolOperativo()), quien),
                "el hueco no es «cualquiera»: partir de un responsable que ya no esta es el mismo "
                        + "comando obsoleto por el otro lado");
        assertNull(responsableDe(idPropiedad), "y sigue FALTANTE");
        assertEquals(traspasos, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad, eventosDe(idPropiedad));

        // (c) Y la combinacion que SI cuadra: FALTANTE declarado sobre FALTANTE.
        //     Es la unica forma de sacar una propiedad de FALTANTE, asi que si
        //     esto no entrara, el hueco seria una carcel.
        TraspasoDeResponsable primera = propiedades.asignarResponsable(idPropiedad,
                destino.idRolOperativo(), MOTIVO, ResponsableObservado.faltante(), quien);
        assertEquals(destino.idRolOperativo(), responsableDe(idPropiedad));
        assertNull(primera.idResponsableAnterior(),
                "y la fila lleva el predecesor NULL: no habia. Ese hueco es informacion y no se "
                        + "rellena con el agente del encargo ni con quien la incorporo");
        assertEquals("TRASPASO", primera.origen(),
                "sigue siendo un traspaso aunque no tenga predecesor: el origen no se deduce del "
                        + "hueco");
        assertEquals(traspasos + 1, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad + 1, eventosDe(idPropiedad));
        assertNull(anteriorDeLaUltimaFila(idPropiedad),
                "y en la base tambien: la columna id_rol_responsable_anterior queda NULL");
    }

    // ==================================================================
    // 4. La carrera de verdad: DOS transacciones vivas a la vez
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
     * comprueba contra {@code pg_stat_activity}, y si en cinco segundos no
     * aparece ninguna sesion esperando un lock, la prueba <b>falla</b>. Dar por
     * buena una carrera que nunca ocurrio seria un verde que no ha mirado nada.
     *
     * <p>Al soltar la primera, PostgreSQL re-evalua el WHERE de la segunda sobre
     * la fila <b>ya actualizada</b> —eso es READ COMMITTED— y no encuentra
     * ninguna: 0 filas, 409, y ni una escritura de mas.
     */
    @Test
    @DisplayName("D-P0-9: dos transacciones reales que parten del MISMO responsable, y solo una gana")
    void dosTransaccionesRealesYSoloUnaGana() throws Exception {
        Actor duena = agenteDelEquipo(0);
        Actor haciaB = agenteDelEquipo(1);
        Actor haciaC = agenteDelEquipo(2);
        Actor quien = broker();
        long idPropiedad = registrar(duena);
        long a = duena.idRolOperativo();

        int traspasos = traspasosDe(idPropiedad);
        int eventosDeLaPropiedad = eventosDe(idPropiedad);

        CountDownLatch enElCas = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        // El freno. Se pone en el rastro y no en otro sitio porque es la primera
        // escritura DESPUES del compare-and-set: cuando esto se ejecuta, la fila
        // de `propiedad` ya esta bloqueada por T1 y su transaccion sigue abierta.
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
            Future<TraspasoDeResponsable> t1 = hilos.submit(() -> propiedades.asignarResponsable(
                    idPropiedad, haciaB.idRolOperativo(), MOTIVO, ResponsableObservado.de(a),
                    quien));

            assertTrue(enElCas.await(15, TimeUnit.SECONDS),
                    "T1 no llego a escribir el rastro, asi que no hay CAS ejecutado ni fila "
                            + "bloqueada: sin eso esta prueba no esta midiendo ninguna carrera");

            Future<TraspasoDeResponsable> t2 = hilos.submit(() -> propiedades.asignarResponsable(
                    idPropiedad, haciaC.idRolOperativo(), MOTIVO, ResponsableObservado.de(a),
                    quien));

            esperarAUnaSesionEsperandoUnLock(t2);
            liberar.countDown();

            TraspasoDeResponsable ganador = t1.get(20, TimeUnit.SECONDS);
            assertEquals(haciaB.idRolOperativo(), ganador.idResponsableNuevo());
            assertEquals(a, ganador.idResponsableAnterior());

            ExecutionException perdedor = assertThrows(ExecutionException.class,
                    () -> t2.get(20, TimeUnit.SECONDS),
                    "las dos partian de A: la segunda no puede entrar tambien");
            assertInstanceOf(ConflictoException.class, perdedor.getCause(),
                    "y el rechazo es un CONFLICTO (409), no un error tecnico ni un 400: la "
                            + "peticion era valida, lo que cambio fue el estado. Fue: "
                            + perdedor.getCause());
        } finally {
            // Que un fallo no deje a T1 colgado esperando para siempre.
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
        }

        assertEquals(haciaB.idRolOperativo(), responsableDe(idPropiedad),
                "gana la que ejecuto su compare-and-set primero, y la otra no la pisa");
        assertEquals(traspasos + 1, traspasosDe(idPropiedad),
                "UNA fila nueva en el expediente, no dos: el traspaso que perdio no ocurrio");
        assertEquals(eventosDeLaPropiedad + 1, eventosDe(idPropiedad),
                "y UN evento: el outbox no puede anunciar un traspaso que no paso");
        assertEquals(a, anteriorDeLaUltimaFila(idPropiedad),
                "y la unica fila dice «de A a B», que es lo que de verdad ocurrio");
    }

    // ==================================================================
    // 5. D-P0-10: si falla una parte, no queda ninguna
    // ==================================================================

    @Test
    @DisplayName("D-P0-10: si falla la fila del expediente, el responsable tampoco cambia")
    void sinRastroNoHayTraspaso() {
        Actor duena = agenteDelEquipo(0);
        Actor primero = agenteDelEquipo(1);
        Actor segundo = agenteDelEquipo(2);
        Actor quien = broker();
        long idPropiedad = registrar(duena);

        // CONTROL POSITIVO: sin romper nada, el mismo traspaso escribe LAS TRES
        // cosas. Sin esto, el rollback de abajo podria estar midiendo un
        // traspaso que no escribia nada de todas formas.
        int traspasos = traspasosDe(idPropiedad);
        int eventosDeLaPropiedad = eventosDe(idPropiedad);
        propiedades.asignarResponsable(idPropiedad, primero.idRolOperativo(), MOTIVO,
                ResponsableObservado.de(duena.idRolOperativo()), quien);
        assertEquals(primero.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(traspasos + 1, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad + 1, eventosDe(idPropiedad));

        // Y ahora se rompe la fila del expediente, DESPUES del compare-and-set:
        // el responsable ya esta cambiado dentro de la transaccion cuando esto
        // estalla, asi que lo que se mide es el ROLLBACK y no una guarda previa.
        int traspasosAntes = traspasosDe(idPropiedad);
        int eventosAntes = eventosDe(idPropiedad);
        Mockito.doThrow(new RuntimeException("fallo inyectado al escribir el expediente"))
                .when(rastro).save(ArgumentMatchers.any());

        assertThrows(RuntimeException.class,
                () -> propiedades.asignarResponsable(idPropiedad, segundo.idRolOperativo(), MOTIVO,
                        ResponsableObservado.de(primero.idRolOperativo()), quien));

        assertEquals(primero.idRolOperativo(), responsableDe(idPropiedad),
                "un responsable cambiado sin fila en el expediente es una autoridad que nadie "
                        + "puede explicar: o entran los dos, o no entra ninguno");
        assertEquals(traspasosAntes, traspasosDe(idPropiedad));
        assertEquals(eventosAntes, eventosDe(idPropiedad),
                "y el evento tampoco sobrevive solo");
    }

    @Test
    @DisplayName("D-P0-10: si falla el evento, ni el responsable ni la fila del expediente quedan")
    void sinEventoNoHayTraspaso() {
        Actor duena = agenteDelEquipo(0);
        Actor primero = agenteDelEquipo(1);
        Actor segundo = agenteDelEquipo(2);
        Actor quien = broker();
        long idPropiedad = registrar(duena);

        // CONTROL POSITIVO, otra vez: el traspaso completo funciona.
        int traspasos = traspasosDe(idPropiedad);
        int eventosDeLaPropiedad = eventosDe(idPropiedad);
        propiedades.asignarResponsable(idPropiedad, primero.idRolOperativo(), MOTIVO,
                ResponsableObservado.de(duena.idRolOperativo()), quien);
        assertEquals(primero.idRolOperativo(), responsableDe(idPropiedad));
        assertEquals(traspasos + 1, traspasosDe(idPropiedad));
        assertEquals(eventosDeLaPropiedad + 1, eventosDe(idPropiedad));

        // El evento es lo ULTIMO que se escribe, asi que cuando falla ya hay una
        // columna cambiada y una fila insertada dentro de la transaccion. Es el
        // caso que mas se parece a «casi termino»: si algo iba a sobrevivir a
        // medias, sobreviviria aqui.
        int traspasosAntes = traspasosDe(idPropiedad);
        int eventosAntes = eventosDe(idPropiedad);
        Mockito.doThrow(new RuntimeException("fallo inyectado al anotar el evento"))
                .when(eventos).save(ArgumentMatchers.any());

        assertThrows(RuntimeException.class,
                () -> propiedades.asignarResponsable(idPropiedad, segundo.idRolOperativo(), MOTIVO,
                        ResponsableObservado.de(primero.idRolOperativo()), quien));

        assertEquals(primero.idRolOperativo(), responsableDe(idPropiedad),
                "el responsable vuelve atras: cambiarlo sin anunciarlo dejaria a la auditoria "
                        + "transversal ciega ante un movimiento de autoridad");
        assertEquals(traspasosAntes, traspasosDe(idPropiedad),
                "y la fila del expediente ya insertada se revierte: una traza que afirma un "
                        + "cambio que no ocurrio es peor que no tener traza");
        assertEquals(eventosAntes, eventosDe(idPropiedad));
    }

    // ==================================================================
    // 6. D-P0-10: la puerta que NO es el traspaso
    // ==================================================================

    /**
     * <b>Una edicion en curso no puede devolver la autoridad a quien ya no la
     * tiene</b> (D-P0-10).
     *
     * <h2>Que ataca, y por que no lo cubria ninguna de las de arriba</h2>
     * Las cuatro anteriores atacan el <b>traspaso</b>: dos comandos que compiten,
     * uno obsoleto, una escritura rota. Todas entran por
     * {@code asignarResponsable}, que es la puerta legitima. Esta entra por
     * <b>otra puerta</b> —{@code PUT /propiedades/&#123;id&#125;}— que no pide
     * mover el responsable, no lo menciona en su cuerpo y no deja fila en el
     * expediente. Y aun asi lo movia.
     *
     * <h2>Por que lo movia</h2>
     * {@code Propiedad} <b>no</b> lleva {@code @DynamicUpdate}, asi que el flush
     * de una entidad gestionada escribe la fila <b>entera</b> con los valores
     * que tiene en memoria — incluida {@code id_rol_responsable}, con el valor
     * que se leyo al cargar. Si entre la carga y el flush otro comitea un
     * traspaso A&rarr;B, el {@code UPDATE} de la edicion lo pisa y devuelve la
     * columna a A. Queda un responsable cambiado <b>sin fila que lo explique</b>
     * —exactamente lo que D-P0-10 prohibe— y ademas al reves: el expediente dice
     * «de A a B» sobre una propiedad que responde ante A.
     *
     * <h2>Por que hace falta el hilo</h2>
     * Una prueba secuencial no lo ve: si la edicion carga <b>despues</b> del
     * traspaso, lee B y reescribe B. La ventana existe solo mientras hay una
     * transaccion que <b>ya leyo</b> y <b>todavia no ha escrito</b>, y eso solo
     * se monta parando la edicion dentro de su transaccion. Por eso el latch se
     * cuelga de {@code ComandosIdempotentes#buscar}, que es el ultimo punto de
     * {@code editar} anterior a toda escritura.
     *
     * <h2>Lo que se exige, entero</h2>
     * No basta con que el responsable siga siendo B: eso lo cumpliria tambien
     * una edicion que hubiera fallado. Se exige <b>a la vez</b> que la
     * descripcion nueva SI este escrita —la edicion era legitima y tenia que
     * entrar—, que el responsable siga siendo B, y que no haya aparecido
     * ninguna fila de traspaso de mas.
     *
     * <h2>Por que el traspaso va ahora en OTRO hilo (F2.10)</h2>
     * Esta prueba se llamaba {@code unaEdicionConcurrenteNoRevierteUnTraspaso} y
     * lanzaba el traspaso <b>en el hilo principal</b> mientras la edicion estaba
     * parada. Con el candado de escritura de F2.10 ese montaje ya no se puede
     * armar: la edicion toma la fila de {@code propiedad} al cargarla, asi que
     * el compare-and-set del traspaso se queda esperando y el hilo principal
     * —el unico que puede soltar el latch— no vuelve nunca. No es que la prueba
     * estorbe: es que <b>el hecho que describia ya no existe</b>. Lo que si
     * existe es el mismo par de operaciones en el <b>orden inverso</b> al del
     * caso 8, y eso es lo que se mide aqui, con las mismas exigencias y una mas:
     * que el traspaso <b>de verdad</b> se quedo esperando el candado, sondeando
     * {@code pg_stat_activity}. Sin esa sonda, el dia que el candado
     * desapareciera esta prueba seguiria verde por casualidad — que es
     * exactamente como se pierde una regla.
     */
    @Test
    @DisplayName("F2.10: la edicion que tomo la fila escribe, el traspaso espera su turno y ninguno pisa al otro")
    void laEdicionQueTomoLaFilaEscribeYElTraspasoEsperaSuTurno() throws Exception {
        Actor duena = agenteDelEquipo(0);
        Actor haciaB = agenteDelEquipo(1);
        Actor quien = broker();
        long idPropiedad = registrar(duena);
        long a = duena.idRolOperativo();

        // CONTROL POSITIVO de partida: el alta dejo a A como responsable. Sin
        // esto, "sigue siendo B al final" podria estar midiendo una propiedad
        // que nunca respondio ante nadie.
        assertEquals(a, responsableDe(idPropiedad),
                "el alta tiene que dejar responsable a quien registro (V88): sin eso esta "
                        + "prueba no tiene de donde partir");
        int traspasosAntes = traspasosDe(idPropiedad);

        CountDownLatch cargada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        Mockito.doAnswer(invocacion -> {
            if (primera.compareAndSet(false, true)) {
                cargada.countDown();
                assertTrue(liberar.await(20, TimeUnit.SECONDS),
                        "nadie solto a la edicion: la prueba no llego a montar la ventana");
            }
            return invocacion.callRealMethod();
        }).when(comandos).buscar(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // E: la edicion legitima de la duena, parada con la propiedad ya
            // cargada, su responsable (A) en memoria y la FILA TOMADA.
            Future<?> edicion = hilos.submit(() -> propiedades.editar(idPropiedad,
                    new ComandoEdicion(null, null, DESCRIPCION_EDITADA, null, null, null, null,
                            null),
                    duena));

            assertTrue(cargada.await(20, TimeUnit.SECONDS),
                    "la edicion no llego a cargar la propiedad, asi que no hay ninguna "
                            + "transaccion que haya leido A y no haya escrito todavia: sin eso "
                            + "esta prueba no esta midiendo nada");

            // Y mientras esta parada, el traspaso A->B, que tiene que ESPERAR:
            // su compare-and-set choca con el candado que la edicion ya tiene.
            Future<TraspasoDeResponsable> traspaso = hilos.submit(
                    () -> propiedades.asignarResponsable(idPropiedad, haciaB.idRolOperativo(),
                            MOTIVO, ResponsableObservado.de(a), quien));

            esperarAUnaSesionEsperandoUnLock(traspaso);
            liberar.countDown();

            edicion.get(30, TimeUnit.SECONDS);
            TraspasoDeResponsable hecho = traspaso.get(30, TimeUnit.SECONDS);
            assertEquals(haciaB.idRolOperativo(), hecho.idResponsableNuevo());
            assertEquals(a, hecho.idResponsableAnterior(),
                    "el traspaso desperto y siguio saliendo de A: la edicion escribio la ficha, "
                            + "no la autoridad, asi que el estado observado seguia siendo verdad");
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
        }

        assertEquals(DESCRIPCION_EDITADA, descripcionDe(idPropiedad),
                "la edicion era legitima y tenia que entrar: la duena la lanzo cuando todavia "
                        + "respondia por la propiedad. Si no esta escrita, esto no demuestra que "
                        + "el responsable se conserve, solo que la edicion no ocurrio");
        assertEquals(haciaB.idRolOperativo(), responsableDe(idPropiedad),
                "el responsable volvio a A por el flush de una edicion que no pidio moverlo: un "
                        + "responsable cambiado sin fila en el expediente (D-P0-10), y por una "
                        + "puerta que no es el traspaso. Solo la autoridad mueve la autoridad");
        assertEquals(traspasosAntes + 1, traspasosDe(idPropiedad),
                "y UNA sola fila de traspaso: la edicion no puede inventar ni borrar actos de "
                        + "gobierno");
        assertEquals(a, anteriorDeLaUltimaFila(idPropiedad),
                "la unica fila sigue diciendo «de A a B», que es lo que de verdad ocurrio");
    }

    // ==================================================================
    // 7. D-P0-13: la elegibilidad no puede caducar entre comprobar y escribir
    // ==================================================================

    /**
     * <b>El TOCTOU de la elegibilidad, y su cierre</b> (D-P0-13).
     *
     * <h2>Que defecto ataca</h2>
     * D-P0-7 se <b>comprueba</b> y despues se <b>escribe</b>. Entre las dos
     * cosas cabe otra transaccion que desactive al destino, y la propiedad acaba
     * en manos de alguien que ya no puede recibirla — con todas las guardas
     * verdes, porque cada una dijo la verdad en el instante en que se pregunto.
     *
     * <h2>Lo que se exige, y lo que NO</h2>
     * No se exige un desenlace concreto de la carrera: se exige que <b>haya un
     * orden</b>. Con la fila del destino tomada, la baja <b>espera</b>; el
     * traspaso decide sobre un estado que va a seguir siendo verdad cuando
     * escriba, y la baja entra despues. El resultado —destino responsable y
     * ademas desactivado— es la <b>secuencia causal real</b>: recibio, y luego
     * lo desactivaron. Eso es exactamente D-P0-8, no un fallo.
     *
     * <p>Que la baja esta <b>de verdad</b> esperando no se supone: se comprueba
     * contra {@code pg_stat_activity} por una conexion propia, y si en diez
     * segundos no aparece ninguna sesion esperando un lock, la prueba
     * <b>falla</b> — sin bloqueo, lo que se estaria midiendo es otra cosa.
     *
     * <p>El caso <b>inverso</b> —baja primero, comiteada; traspaso despues— va
     * en la misma prueba y es el que demuestra que el candado no ha convertido
     * la guarda en una formalidad: ahi el traspaso se rechaza y no escribe nada.
     */
    @Test
    @DisplayName("D-P0-13: desactivar al destino durante un traspaso espera, y despues del traspaso no lo deshace")
    void laElegibilidadDelDestinoNoCaducaAMitadDeUnTraspaso() throws Exception {
        Actor duena = agenteDelEquipo(0);
        Actor destino = agenteDelEquipo(1);
        Actor gobierno = tenantAdmin();
        long idPropiedad = registrar(duena);
        long a = duena.idRolOperativo();
        long b = destino.idRolOperativo();

        int traspasosAntes = traspasosDe(idPropiedad);
        String operativoAntes = estadoOperativoDe(b);
        assertEquals("D", operativoAntes,
                "el destino tiene que empezar disponible o esta prueba no parte de un traspaso "
                        + "legitimo");

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
            Future<TraspasoDeResponsable> t1 = hilos.submit(() -> propiedades.asignarResponsable(
                    idPropiedad, b, MOTIVO, ResponsableObservado.de(a), broker()));

            assertTrue(enElRastro.await(20, TimeUnit.SECONDS),
                    "T1 no llego a escribir el rastro, asi que no hay bloqueo del destino "
                            + "tomado: sin eso esta prueba no esta midiendo ninguna ventana");

            Future<?> baja = hilos.submit(() -> agentes.actualizar(b, deLicencia(), gobierno));
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

        assertEquals(b, responsableDe(idPropiedad),
                "el traspaso decidio con la fila del destino tomada, asi que su elegibilidad no "
                        + "pudo cambiar entre la comprobacion y la escritura: entra");
        assertEquals(traspasosAntes + 1, traspasosDe(idPropiedad),
                "y deja UNA fila: el traspaso ocurrio entero");

        // Y AL REVES: con la baja ya comiteada, el mismo traspaso se rechaza y
        // no escribe nada. Sin esto, el verde de arriba podria estar midiendo
        // una guarda que el candado dejo de aplicar.
        long idOtra = registrar(duena);
        int traspasosDeLaOtra = traspasosDe(idOtra);
        try {
            agentes.actualizar(b, deLicencia(), gobierno);
            assertEquals("L", estadoOperativoDe(b), "el montaje tiene que dejarlo de licencia");
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.asignarResponsable(idOtra, b, MOTIVO,
                            ResponsableObservado.de(a), broker()),
                    "con la baja ya comiteada, el destino no puede recibir: el candado ordena la "
                            + "carrera, no perdona la guarda");
            assertEquals(a, responsableDe(idOtra), "y no se movio nada");
            assertEquals(traspasosDeLaOtra, traspasosDe(idOtra), "sin fila en el expediente");
        } finally {
            fijarEstadoOperativo(b, operativoAntes);
        }
        assertEquals(operativoAntes, estadoOperativoDe(b),
                "el estado del agente queda como estaba: la base de pruebas es compartida");
    }

    /** Lo minimo para poner de licencia a un agente por su caso de uso. */
    private com.controllocal.service.AgenteService.DatosAgente deLicencia() {
        return new com.controllocal.service.AgenteService.DatosAgente(null, null, null, null,
                null, null, null, null, null, null, null, "L", null);
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

    /** El gobierno del tenant, con la misma identidad del broker y otra banda. */
    private Actor tenantAdmin() {
        Actor base = broker();
        return new Actor(base.idOrganizacion(), base.idPersona(), base.idRolOperativo(),
                Actor.TENANT_ADMIN);
    }

    // ==================================================================
    // 8. F2.10: la autoridad de EDICION tampoco caduca entre comprobar y
    //    escribir
    // ==================================================================

    /**
     * <b>La edicion del saliente que llega tarde no aterriza</b> (F2.10).
     *
     * <h2>Que defecto ataca</h2>
     * Es el TOCTOU de D-P0-13 dicho sobre la <b>otra</b> autoridad. Alli lo que
     * caducaba entre comprobar y escribir era la <b>elegibilidad del destino</b>;
     * aqui es la <b>autoridad de edicion misma</b>. {@code editar} cargaba la
     * propiedad, preguntaba {@code exigirEdicion} sobre lo que acababa de leer y
     * escribia <b>despues</b>, sin nada que sujetara la fila entre las dos
     * cosas. En esa ventana cabe un traspaso entero —su compare-and-set toma la
     * fila un instante y la suelta al comitear—, y la edicion del agente
     * <b>saliente</b> aterrizaba sobre una propiedad que ya era de otro. Todas
     * las guardas verdes: cada una dijo la verdad en el instante en que se
     * pregunto.
     *
     * <h2>Que NO arreglaba F2.1</h2>
     * {@code updatable = false} sobre {@code id_rol_responsable} impide que esa
     * edicion tardia <b>revierta</b> la autoridad — y eso es lo que mide el caso
     * 6. Lo que nunca impidio es que la edicion <b>se escriba</b>: la
     * descripcion, la ubicacion, los titulares y los atributos entraban igual,
     * firmados por quien ya no responde.
     *
     * <h2>Lo que se exige, y lo que no</h2>
     * No se exige un desenlace inventado: se exige que <b>haya un orden</b>. Con
     * la fila tomada por el compare-and-set del traspaso, la edicion
     * <b>espera</b>; cuando despierta lee al responsable que de verdad hay
     * —B— y recibe el <b>mismo 403</b> que el Core ya produce para cualquier
     * agente que no responde por la propiedad ({@code OTRO_RESPONSABLE}). No hay
     * decision funcional nueva: hay una comprobacion que ahora se hace sobre el
     * estado que seguira siendo verdad al escribir.
     *
     * <p>Que la edicion esta <b>de verdad</b> esperando no se supone: se sondea
     * {@code pg_stat_activity} por una conexion propia, y si en diez segundos no
     * aparece ninguna sesion esperando un lock la prueba <b>falla</b>.
     *
     * <p>Y se exige la <b>conservacion</b>: la descripcion tiene que quedar
     * exactamente como estaba. «Devolvio 403» no basta — un 403 despues de
     * haber escrito seria el peor de los dos mundos.
     */
    @Test
    @DisplayName("F2.10: la edicion del saliente que llega tras el traspaso espera el candado, recibe 403 y no escribe")
    void laEdicionTardiaDelSalienteNoAterriza() throws Exception {
        Actor duena = agenteDelEquipo(0);
        Actor haciaB = agenteDelEquipo(1);
        Actor quien = broker();
        long idPropiedad = registrar(duena);
        long a = duena.idRolOperativo();

        assertEquals(a, responsableDe(idPropiedad),
                "el alta tiene que dejar responsable a quien registro (V88): sin eso no hay "
                        + "saliente que pueda llegar tarde");
        String descripcionAntes = descripcionDe(idPropiedad);
        assertNotEquals(DESCRIPCION_EDITADA, descripcionAntes,
                "la descripcion de partida tiene que ser DISTINTA de la que intenta escribir la "
                        + "edicion, o «quedo intacta» no distinguiria nada");
        int traspasosAntes = traspasosDe(idPropiedad);

        CountDownLatch enElRastro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean primera = new AtomicBoolean(false);

        // El freno va en el rastro porque es la primera escritura DESPUES del
        // compare-and-set: cuando esto se ejecuta, la fila de `propiedad` ya
        // esta tomada por T y su transaccion sigue abierta. Es el instante
        // exacto de la ventana.
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
            Future<TraspasoDeResponsable> t = hilos.submit(() -> propiedades.asignarResponsable(
                    idPropiedad, haciaB.idRolOperativo(), MOTIVO, ResponsableObservado.de(a),
                    quien));

            assertTrue(enElRastro.await(20, TimeUnit.SECONDS),
                    "T no llego a escribir el rastro, asi que no hay compare-and-set ejecutado "
                            + "ni fila tomada: sin eso esta prueba no esta midiendo ninguna "
                            + "ventana");

            // E: la edicion del SALIENTE, que arranca cuando el traspaso ya
            // decidio pero todavia no ha comiteado.
            Future<?> edicion = hilos.submit(() -> propiedades.editar(idPropiedad,
                    new ComandoEdicion(null, null, DESCRIPCION_EDITADA, null, null, null, null,
                            null),
                    duena));

            esperarAUnaSesionEsperandoUnLock(edicion);
            liberar.countDown();

            t.get(20, TimeUnit.SECONDS);

            ExecutionException tardia = assertThrows(ExecutionException.class,
                    () -> edicion.get(20, TimeUnit.SECONDS),
                    "la edicion desperto sobre una propiedad que ya responde ante B: A dejo de "
                            + "poder escribirla en el mismo instante en que el traspaso comiteo, "
                            + "y una edicion que aterriza despues es la autoridad comprobada "
                            + "sobre un estado que ya no existe");
            assertInstanceOf(AccesoNoAutorizadoException.class, tardia.getCause(),
                    "y el rechazo es el 403 de siempre, no un error tecnico ni un conflicto: no "
                            + "hay ninguna regla nueva, hay una regla vieja comprobada a tiempo. "
                            + "Fue: " + tardia.getCause());
            assertTrue(String.valueOf(tardia.getCause().getMessage()).contains("responde otro agente"),
                    "y el motivo es OTRO_RESPONSABLE, el mismo texto que el Core ya da a "
                            + "cualquier agente que no responde por la propiedad -- Web y KAIROS "
                            + "reciben la misma frase. Decia: " + tardia.getCause().getMessage());
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS),
                    "los hilos de la carrera no terminaron");
        }

        assertEquals(descripcionAntes, descripcionDe(idPropiedad),
                "la edicion del saliente ATERRIZO: la ficha del inmueble quedo escrita por quien "
                        + "ya no responde por el. Un 403 que llega despues de haber escrito no "
                        + "protege nada");
        assertEquals(haciaB.idRolOperativo(), responsableDe(idPropiedad),
                "y el traspaso, que gano la carrera, sigue en pie");
        assertEquals(traspasosAntes + 1, traspasosDe(idPropiedad),
                "UNA fila de traspaso: la edicion rechazada no inventa ni borra actos de "
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
     * Lo que si existe es esa answer por defecto, y es exactamente «haz lo que
     * harias sin espia».
     */
    private static Object comoSiempre(InvocationOnMock invocacion, Object espia) throws Throwable {
        Object resultado = Mockito.mockingDetails(espia).getMockCreationSettings()
                .getDefaultAnswer().answer(invocacion);
        assertNotNull(resultado,
                "el espia no delego en el repositorio real: sin eso, la prueba estaria midiendo "
                        + "un traspaso que no escribe nada");
        return resultado;
    }

    /**
     * <b>Espera a ver una sesion de verdad esperando un lock</b>, o falla.
     *
     * <p>Es la diferencia entre «no hubo carrera» y «no llegue a montarla». Sin
     * esta comprobacion, un cambio que hiciera que T2 fallara <b>antes</b> de
     * llegar al UPDATE dejaria la prueba verde: el 409 llegaria igual, por otra
     * razon, y nadie se enteraria de que el candado ya no se estaba probando.
     *
     * <p><b>Va por una conexion propia, fuera del pool de la aplicacion</b>, y
     * eso no es un detalle: durante la carrera hay dos transacciones ocupando
     * conexiones del pool, y una sonda que compitiera por la tercera podria
     * quedarse esperando justo mientras el bloqueo que viene a medir esta
     * ocurriendo. La sonda tiene que poder mirar <b>siempre</b>, incluso —sobre
     * todo— cuando la aplicacion esta atascada.
     *
     * <p>El primer intento no cuenta como respuesta: entre que T2 manda el
     * UPDATE y que PostgreSQL registra la espera pasan unos milisegundos, asi
     * que se sondea hasta el limite antes de concluir que no hay bloqueo.
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
            fail("en 10 segundos ninguna sesion se quedo esperando un lock, asi que el segundo "
                    + "traspaso NUNCA llego al UPDATE de la fila: lo que fuera que respondiera "
                    + "despues no seria la carrera que esta prueba dice medir. T2 "
                    + (t2.isDone() ? "ya habia terminado: " + resultadoDe(t2) : "seguia en marcha")
                    + ". Sesiones vivas: " + sesiones(sonda));
        }
    }

    /**
     * Una conexion directa a la base de pruebas, sin pasar por el pool.
     *
     * <p>La url sale de {@link BaseDeDatosDePruebas}, que es la unica puerta —la
     * misma guarda que impide que una prueba de integracion escriba en la base
     * de desarrollo—, y las credenciales del entorno de Spring ya resuelto.
     */
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

    /** Para que el fallo de arriba diga QUE paso y no solo que no paso lo previsto. */
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
    // Escenario (mismos ayudantes que AlcanceYGobierno..., copiados al minimo)
    // ==================================================================

    private long registrar(Actor quien) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Caso de causalidad",
                new Ubicacion("Av. Causalidad " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("3000"), "PEN",
                        null, null, null, null, null, null, null)),
                null), quien).idPropiedad();
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

    private Long unPropietario(Actor actor) {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
    }

    // ==================================================================
    // Lecturas directas
    // ==================================================================

    private Long responsableDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select id_rol_responsable from propiedad where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    /** Lo que la edicion tenia que dejar escrito, leido de la fila. */
    private String descripcionDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select descripcion from propiedad where id_propiedad = ?",
                String.class, idPropiedad);
    }

    private void dejarSinResponsable(long idPropiedad) {
        jdbc.update("update propiedad set id_rol_responsable = null where id_propiedad = ?",
                idPropiedad);
    }

    /** Filas de TRASPASO de esta propiedad. El ALTA no cuenta: es otro hecho. */
    private int traspasosDe(long idPropiedad) {
        Integer total = jdbc.queryForObject("""
                select count(*) from asignacion_responsable_propiedad
                 where id_propiedad = ? and origen = 'TRASPASO'
                """, Integer.class, idPropiedad);
        assertNotNull(total);
        return total;
    }

    /** Eventos de dominio de esta propiedad, del tipo que sean. */
    private int eventosDe(long idPropiedad) {
        Integer total = jdbc.queryForObject("""
                select count(*) from evento_dominio
                 where entidad_tipo = 'PROPIEDAD' and entidad_id = ?
                """, Integer.class, idPropiedad);
        assertNotNull(total);
        return total;
    }

    /** El predecesor que quedo escrito en la ultima fila de traspaso. */
    private Long anteriorDeLaUltimaFila(long idPropiedad) {
        List<Long> filas = jdbc.queryForList("""
                select id_rol_responsable_anterior from asignacion_responsable_propiedad
                 where id_propiedad = ? and origen = 'TRASPASO'
                 order by id_asignacion desc limit 1
                """, Long.class, idPropiedad);
        assertFalse(filas.isEmpty(), "no hay ninguna fila de traspaso que mirar");
        return filas.get(0);
    }
}
