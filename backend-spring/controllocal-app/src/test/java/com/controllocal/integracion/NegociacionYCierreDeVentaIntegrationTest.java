package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.CierreVentaService;
import com.controllocal.service.ClienteService;
import com.controllocal.service.OfertaService;
import com.controllocal.service.OfertaService.DatosOferta;
import com.controllocal.service.OportunidadService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>WP-A contra PostgreSQL real: la negociación y el cierre de una venta.</b>
 *
 * <p>Lo que aquí se prueba no lo puede probar un test con mocks, y no es una
 * distinción académica:
 *
 * <ul>
 *   <li>los dos invariantes de la mesa —una viva, una aceptada— viven en
 *       <b>índices parciales</b>, no en el servicio;</li>
 *   <li>los triggers {@code tg_oferta_encargo_de_la_mesa},
 *       {@code tg_compraventa_encargo_de_venta} y {@code tg_precio_exige_encargo}
 *       sólo se ejecutan contra Postgres;</li>
 *   <li>la cascada del cierre toca seis tablas en una transacción, y lo que hay
 *       que comprobar es <b>el estado de las seis después</b>.</li>
 * </ul>
 *
 * <p>Cada prueba levanta su propio inmueble, su propio encargo y su propia mesa.
 * Es más lento y es deliberado: reutilizar una fila del seed hace que el
 * resultado dependa del orden de ejecución, y un invariante de dinero que sólo
 * se cumple si otra prueba corrió antes no es un invariante.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NegociacionYCierreDeVentaIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired PropiedadUniversalService propiedades;
    @Autowired CaptacionService captaciones;
    @Autowired OportunidadService oportunidades;
    @Autowired ClienteService clientes;
    @Autowired PrecioLocalService precios;
    @Autowired OfertaService ofertas;
    @Autowired CierreVentaService cierres;
    @Autowired JdbcTemplate jdbc;

    // ==================================================================
    // 1. La venta completa, de punta a punta
    // ==================================================================

    /**
     * <b>U → P → O → contraoferta → aceptación → cierre → C.</b>
     *
     * <p>El recorrido entero, comprobando en cada paso lo que quedó escrito.
     * Está en una sola prueba a propósito: lo que se verifica no es cada paso
     * por separado —eso lo hacen las demás— sino que <b>la cadena se sostiene</b>
     * y que al final la serie económica del encargo se puede leer completa.
     */
    @Test
    @DisplayName("la venta completa: hitos U P O O C, encargo VENDIDO y oportunidad GANADA")
    void laVentaCompletaDejaLaSerieEnteraYCierraLasTresCosas() {
        Escenario e = unaVentaLista();

        // --- El encargo ya nació con su hito U (lo escribe el alta).
        assertEquals(1, hitos(e.idCaptacion(), PrecioPropiedad.HITO_AUTORIZADO),
                "el alta del encargo escribe el importe AUTORIZADO");

        // --- El anuncio: hito P, el importe que el mercado VE.
        //
        // Se escribe por `POST /locales/{id}/precios`, que es un productor real
        // de la serie y pasa por la misma autoridad del encargo, y no creando la
        // publicacion: el gate de publicable exige la ficha completa del inmueble
        // y lo que aqui se prueba es la REFERENCIA, no ese gate. Atarlo a el
        // haria que esta prueba se pusiera roja por un atributo obligatorio nuevo
        // que no tiene nada que ver con negociar.
        precios.registrar(e.idPropiedad(), new PrecioLocalService.DatosPrecio(
                PrecioPropiedad.HITO_PUBLICADO, "USD", new BigDecimal("390000"),
                LocalDate.now(), "VENTA"), e.agente());
        assertEquals(1, hitos(e.idCaptacion(), PrecioPropiedad.HITO_PUBLICADO),
                "el importe publicado entra en la serie de SU encargo");

        // --- Ronda 1: ofrece el interesado, por debajo del anuncio.
        OfertaService.FichaOferta primera = ofertas.presentar(e.idOportunidad(),
                new DatosOferta(new BigDecimal("350000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, "Pago al contado", LocalDate.now()),
                Procedencia.deCabecera("SPA"), e.agente());

        assertEquals(1, primera.ronda());
        assertEquals(Oferta.PRESENTADA, primera.estado());
        // La referencia se congeló contra el ANUNCIO, no contra el autorizado:
        // habiendo P, el U no es lo que el interesado vio.
        assertNotNull(primera.referencia(), "con anuncio vivo la referencia no puede ser FALTANTE");
        assertEquals(PrecioPropiedad.HITO_PUBLICADO, primera.referencia().hito());
        assertEquals(0, new BigDecimal("390000").compareTo(primera.referencia().monto()));

        // --- Ronda 2: contraoferta del propietario. El lado NO se declara.
        OfertaService.FichaOferta segunda = ofertas.contraofertar(e.idOportunidad(), primera.id(),
                new DatosOferta(new BigDecimal("372000"), "USD", null, null, LocalDate.now()),
                Procedencia.deCabecera("SPA"), e.agente());

        assertEquals(2, segunda.ronda());
        assertEquals(Oferta.LA_PRESENTA_EL_PROPIETARIO, segunda.presentadaPor(),
                "responde el otro lado; deducirlo es la regla, no una comodidad");
        assertEquals(Oferta.SUPERADA, estadoDeLaOferta(primera.id()),
                "la anterior sale de la mesa cuando entra la contraoferta");

        // --- El sí.
        OfertaService.FichaOferta aceptada = ofertas.aceptar(e.idOportunidad(), segunda.id(),
                "El comprador acepta la contraoferta.", e.agente());
        assertEquals(Oferta.ACEPTADA, aceptada.estado());

        // --- Dos hitos O, uno por vuelta, cada uno atado a SU oferta.
        assertEquals(2, hitos(e.idCaptacion(), PrecioPropiedad.HITO_OFERTADO));
        assertEquals(primera.id(), ofertaDelHito(e.idCaptacion(), PrecioPropiedad.HITO_OFERTADO,
                new BigDecimal("350000")));
        assertEquals(segunda.id(), ofertaDelHito(e.idCaptacion(), PrecioPropiedad.HITO_OFERTADO,
                new BigDecimal("372000")));

        // --- El cierre.
        CierreVentaService.FichaCierreVenta cierre = cierres.cerrar(e.idCaptacion(),
                new CierreVentaService.DatosCierreVenta(segunda.id(), null, null,
                        null, null, LocalDate.now(), "Firma en notaría."),
                e.agente());

        assertEquals(0, new BigDecimal("372000").compareTo(cierre.montoPactado()),
                "sin ajuste explícito, lo pactado es lo de la oferta aceptada");
        assertEquals("USD", cierre.moneda());
        assertNull(cierre.montoDeclarado(),
                "lo declarado en la escritura nace FALTANTE: igualarlo al pactado sería inventarlo");
        assertNull(cierre.monedaDeclarada());
        assertEquals(2, cierre.rondaOferta());

        // --- Y las seis consecuencias, comprobadas en la base.
        assertEquals(1, hitos(e.idCaptacion(), PrecioPropiedad.HITO_CIERRE),
                "el cierre escribe el último hito de la serie del encargo");
        assertEquals(segunda.id(), ofertaDelHito(e.idCaptacion(), PrecioPropiedad.HITO_CIERRE,
                new BigDecimal("372000")),
                "el hito C dice de qué VUELTA salió, no sólo de qué encargo");
        assertEquals(Captacion.CERRADA, estadoDelEncargo(e.idCaptacion()));
        assertEquals("V", motivoDeCierreDelEncargo(e.idCaptacion()),
                "vendido no es alquilado ('A') ni cierre manual del bróker ('M')");
        assertEquals(OportunidadComercial.FINALIZADA_EXITOSA, estadoDeLaMesa(e.idOportunidad()));
        assertEquals("T", disponibilidadDe(e.idPropiedad()),
                "RETIRADO es lo más fuerte que el vocabulario de hoy sabe decir con verdad");
        assertEquals(0, anunciosVivosDelEncargo(e.idCaptacion()),
                "los anuncios del encargo vendido se dan de baja");

        // --- La serie del encargo, entera y en orden: U, P, O, O, C.
        assertEquals(List.of("U", "P", "O", "O", "C"), serieDelEncargo(e.idCaptacion()));
    }

    // ==================================================================
    // 2. La referencia FALTANTE
    // ==================================================================

    /**
     * <b>Sin P ni U, la referencia es FALTANTE y se dice.</b>
     *
     * <p>No se sustituye por el importe del encargo ni por el de la propia
     * oferta. Un descuento calculado contra una cifra inventada es peor que no
     * tener descuento: el primero se cita en una negociación, el segundo se
     * pregunta.
     */
    @Test
    @DisplayName("sin hito P ni U la referencia queda FALTANTE, y no se rellena con nada")
    void laReferenciaSinPNiUQuedaFaltante() {
        Escenario e = unaVentaLista();

        // Se borran los hitos del encargo: el escenario que este caso necesita
        // es un encargo SIN serie, y el alta siempre escribe el U.
        jdbc.update("delete from precio_propiedad where id_captacion = ?", e.idCaptacion());
        assertEquals(0, hitos(e.idCaptacion(), PrecioPropiedad.HITO_AUTORIZADO));

        OfertaService.FichaOferta oferta = ofertas.presentar(e.idOportunidad(),
                new DatosOferta(new BigDecimal("300000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()),
                Procedencia.deCabecera("SPA"), e.agente());

        assertNull(oferta.referencia(),
                "la referencia ausente ES la información: no hay contra qué comparar");

        Map<String, Object> fila = jdbc.queryForMap("""
                select hito_referencia, id_precio_referencia, monto_referencia, moneda_referencia
                  from oferta where id_oferta = ?
                """, oferta.id());
        assertNull(fila.get("hito_referencia"));
        assertNull(fila.get("id_precio_referencia"));
        assertNull(fila.get("monto_referencia"),
                "el importe de la propia oferta NO puede acabar como su referencia");
        assertNull(fila.get("moneda_referencia"));
    }

    /**
     * Con {@code U} y sin anuncio, la referencia es el <b>autorizado</b>. Es la
     * segunda mitad de D-N-1 y no la misma prueba: que se caiga al `U` cuando
     * no hubo `P` es una regla, no un efecto colateral de la anterior.
     */
    @Test
    @DisplayName("sin anuncio, la referencia es el ultimo importe AUTORIZADO del encargo")
    void sinAnuncioLaReferenciaEsElAutorizado() {
        Escenario e = unaVentaLista();

        OfertaService.FichaOferta oferta = ofertas.presentar(e.idOportunidad(),
                new DatosOferta(new BigDecimal("330000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()),
                Procedencia.deCabecera("SPA"), e.agente());

        assertNotNull(oferta.referencia());
        assertEquals(PrecioPropiedad.HITO_AUTORIZADO, oferta.referencia().hito());
        assertEquals(0, IMPORTE_DEL_ENCARGO.compareTo(oferta.referencia().monto()));
    }

    // ==================================================================
    // 3. Los invariantes de la mesa
    // ==================================================================

    /**
     * <b>Una mesa tiene UN precio de cierre.</b> Es el invariante de dinero de
     * la tabla, y por eso la segunda aceptación no puede depender de una
     * comprobación previa: dos peticiones simultáneas la pasarían las dos.
     *
     * <p>Aquí se prueba por la vía que el usuario puede recorrer —aceptar dos
     * ofertas de la misma mesa— y el resultado es 409.
     */
    @Test
    @DisplayName("aceptar una segunda oferta de la misma mesa responde 409")
    void dosOfertasAceptadasEnLaMismaMesaSonImposibles() {
        Escenario e = unaVentaLista();

        OfertaService.FichaOferta primera = presentar(e, new BigDecimal("350000"));
        ofertas.aceptar(e.idOportunidad(), primera.id(), null, e.agente());

        // Con una aceptada, la mesa ya no admite negociar: el 409 llega antes de
        // que exista una segunda oferta que aceptar, que es lo correcto -- la
        // negociación terminó.
        ConflictoException choque = assertThrows(ConflictoException.class,
                () -> presentar(e, new BigDecimal("360000")));
        assertTrue(choque.getMessage().toLowerCase().contains("aceptada")
                        || choque.getMessage().toLowerCase().contains("cerr"),
                "el mensaje tiene que decir por qué, no sólo que no: " + choque.getMessage());

        assertEquals(1, ofertasEnEstado(e.idOportunidad(), Oferta.ACEPTADA));
    }

    /**
     * <b>Una sola oferta VIVA.</b> Mientras hay una sobre la mesa, la siguiente
     * cifra es una contraoferta que la supera. Con dos vivas nadie puede decir a
     * cuál se respondió.
     */
    @Test
    @DisplayName("con una oferta viva, presentar otra responde 409")
    void dosOfertasVivasEnLaMismaMesaSonImposibles() {
        Escenario e = unaVentaLista();
        presentar(e, new BigDecimal("350000"));

        assertThrows(ConflictoException.class, () -> presentar(e, new BigDecimal("355000")));
        assertEquals(1, ofertasEnEstado(e.idOportunidad(), Oferta.PRESENTADA));
    }

    /**
     * <b>Rechazar una cifra NO cierra la oportunidad.</b> Es la asimetría que
     * este corte existe para conservar: que el propietario diga que no a un
     * importe no significa que el interesado se haya ido, y la negociación
     * puede seguir después de un no.
     */
    @Test
    @DisplayName("rechazar deja la oportunidad ABIERTA y admite una oferta nueva")
    void rechazarNoCierraLaOportunidad() {
        Escenario e = unaVentaLista();

        OfertaService.FichaOferta primera = presentar(e, new BigDecimal("340000"));
        ofertas.rechazar(e.idOportunidad(), primera.id(), "Muy por debajo.", e.agente());

        assertEquals(Oferta.RECHAZADA, estadoDeLaOferta(primera.id()));
        assertEquals(OportunidadComercial.ABIERTA, estadoDeLaMesa(e.idOportunidad()),
                "rechazar una cifra no es que el interesado se haya ido");

        // Y la mesa sigue viva: se puede volver a ofrecer.
        OfertaService.FichaOferta segunda = presentar(e, new BigDecimal("360000"));
        assertEquals(2, segunda.ronda(), "la ronda no se reutiliza: es la vuelta siguiente");
    }

    // ==================================================================
    // 4. La caída, con su motivo
    // ==================================================================

    /**
     * La caída exige un motivo del vocabulario cerrado, y desde V90 ese
     * vocabulario incluye las cuatro razones que una VENTA necesita.
     */
    @Test
    @DisplayName("la caida exige motivo: sin el 400, con uno de venta se registra")
    void laCaidaExigeUnMotivoDelVocabulario() {
        Escenario e = unaVentaLista();
        presentar(e, new BigDecimal("340000"));

        assertThrows(ReglaNegocioException.class,
                () -> oportunidades.noContinuidad(e.idOportunidad(), null, null, e.agente()),
                "sin motivo no se registra una caída: el análisis de por qué se pierden las "
                        + "ventas es justamente para lo que existe");
        assertThrows(ReglaNegocioException.class,
                () -> oportunidades.noContinuidad(e.idOportunidad(), "Z", null, e.agente()),
                "el vocabulario es cerrado");

        // 'F' -- no consiguió financiamiento -- entró con V90 y NO es "precio":
        // el precio estaba acordado.
        OportunidadService.FichaOportunidad caida = oportunidades.noContinuidad(
                e.idOportunidad(), "F", "El banco no aprobó el crédito.", e.agente());

        assertEquals(OportunidadComercial.NO_CONTINUA, caida.estado());
        assertEquals(MotivoNoContinuidad.rotuloDe("F"), caida.motivoCierre(),
                "el cable guarda la DESCRIPCIÓN, y sale del único sitio donde vive el vocabulario");
        assertEquals("F", razonDeLaCaida(e.idOportunidad()));
    }

    /**
     * Las cuatro razones nuevas tienen que pasar por las dos capas que las
     * pueden rechazar: el {@code CHECK} de la tabla y el vocabulario de Java.
     * V90 amplió el primero; si el segundo se hubiera quedado corto, «no
     * consiguió financiamiento» sería un 400 con el dato ya en la base.
     */
    @Test
    @DisplayName("las cuatro razones de V90 las admiten el CHECK y el vocabulario del dominio")
    void elVocabularioDeRazonesYElCheckDicenLoMismo() {
        String definicion = jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                 where conrelid = 'motivo_no_continuidad'::regclass
                   and conname = 'ck_motivo_razon'
                """, String.class);
        assertNotNull(definicion);

        for (String razon : MotivoNoContinuidad.RAZONES) {
            assertTrue(definicion.contains("'" + razon + "'"),
                    "el CHECK no admite la razón " + razon + " que el dominio sí declara");
            assertNotEqualsAlCodigo(razon);
        }
        for (String nueva : List.of("F", "T", "D", "S")) {
            assertTrue(MotivoNoContinuidad.RAZONES.contains(nueva),
                    "V90 añadió " + nueva + " al CHECK y el dominio no la conoce");
        }
    }

    // ==================================================================
    // 5. Autoridad
    // ==================================================================

    /**
     * <b>El encargo lo negocia su agente.</b> Otro agente del mismo tenant
     * <b>alcanza la lectura si la oportunidad es suya</b>, pero no escribe en la
     * mesa: quien negoció el encargo es quien puede negociarlo.
     *
     * <p>Aquí el agente ajeno no alcanza ni la lectura —la oportunidad tampoco es
     * suya—, que es el caso real: 403 antes de llegar a la autoridad del encargo.
     */
    @Test
    @DisplayName("un agente ajeno no negocia la mesa de otro")
    void unAgenteAjenoNoNegociaLaMesaDeOtro() {
        Escenario e = unaVentaLista();
        Actor otro = otroAgenteDelMismoEquipo(e.agente());

        assertThrows(AccesoNoAutorizadoException.class,
                () -> ofertas.presentar(e.idOportunidad(),
                        new DatosOferta(new BigDecimal("350000"), "USD",
                                Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()),
                        Procedencia.deCabecera("SPA"), otro));

        assertEquals(0, ofertasDeLaMesa(e.idOportunidad()),
                "un 403 no puede dejar media escritura detrás");
    }

    /**
     * <b>El gobierno del tenant no opera.</b> Alcanza la mesa para leerla —eso es
     * gobierno— y no puede escribir en ella: gobernar no es negociar.
     */
    @Test
    @DisplayName("el TENANT_ADMIN lee la mesa y no escribe en ella")
    void elGobiernoDelTenantNoNegocia() {
        Escenario e = unaVentaLista();
        presentar(e, new BigDecimal("350000"));
        Actor gobierno = new Actor(e.agente().idOrganizacion(), e.agente().idPersona(),
                e.agente().idRolOperativo(), Actor.TENANT_ADMIN);

        assertEquals(1, ofertas.listar(e.idOportunidad(), gobierno).size(),
                "el gobierno del tenant ve lo que pasa en su organización");

        OfertaService.CapacidadesDeLaMesa capacidades =
                ofertas.capacidades(e.idOportunidad(), gobierno);
        assertFalse(capacidades.puedeAceptar());
        assertFalse(capacidades.puedeContraofertar());
        assertFalse(capacidades.puedeCerrarVenta());
        assertFalse(capacidades.puedeRegistrarCaida());

        assertThrows(AccesoNoAutorizadoException.class, () -> ofertas.presentar(e.idOportunidad(),
                new DatosOferta(new BigDecimal("360000"), "USD",
                        Oferta.LA_PRESENTA_EL_PROPIETARIO, null, LocalDate.now()),
                Procedencia.deCabecera("SPA"), gobierno));
    }

    /**
     * <b>Las capacidades y los comandos no pueden discrepar.</b> Se comprueba en
     * el punto donde más fácil sería que lo hicieran: con la oferta aceptada y
     * la mesa ya caída, {@code puedeCerrarVenta} tiene que ser {@code false}
     * porque {@code cerrar} responde 400.
     */
    @Test
    @DisplayName("puedeCerrarVenta dice que no exactamente cuando cerrar responde 400")
    void lasCapacidadesNoProbanUnBotonQueElBackendRechaza() {
        Escenario e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        ofertas.aceptar(e.idOportunidad(), oferta.id(), null, e.agente());

        assertTrue(ofertas.capacidades(e.idOportunidad(), e.agente()).puedeCerrarVenta(),
                "con la oferta aceptada y todo vivo, cerrar la venta SÍ se puede");

        // La mesa se cae después de haber aceptado -- pasa: el banco dice que no.
        oportunidades.noContinuidad(e.idOportunidad(), "F", "El banco no aprobó.", e.agente());

        assertFalse(ofertas.capacidades(e.idOportunidad(), e.agente()).puedeCerrarVenta(),
                "un botón activo que el backend rechaza es el peor error de esta superficie");
        assertThrows(ReglaNegocioException.class, () -> cierres.cerrar(e.idCaptacion(),
                new CierreVentaService.DatosCierreVenta(oferta.id(), null, null, null, null,
                        LocalDate.now(), null), e.agente()));
    }

    // ==================================================================
    // 6. El cierre de venta y sus límites
    // ==================================================================

    /** Un encargo de ALQUILER no se cierra con una compraventa. */
    @Test
    @DisplayName("el cierre de venta rechaza un encargo de alquiler")
    void unAlquilerNoSeCierraConUnaCompraventa() {
        Escenario e = unaMesaDeAlquiler();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("2800"), "PEN");
        ofertas.aceptar(e.idOportunidad(), oferta.id(), null, e.agente());

        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class,
                () -> cierres.cerrar(e.idCaptacion(), new CierreVentaService.DatosCierreVenta(
                        oferta.id(), null, null, null, null, LocalDate.now(), null), e.agente()));
        assertTrue(fallo.getMessage().toLowerCase().contains("alquiler"));

        assertEquals(0, compraventasDelEncargo(e.idCaptacion()));
    }

    /**
     * <b>Un alquiler también se negocia</b> (D-N-5). La oferta vive en la mesa
     * igual que en una venta y escribe su hito {@code O} con la operación de SU
     * encargo. Lo que no cambia es el cierre: sigue siendo solicitud →
     * evaluación → contrato.
     */
    @Test
    @DisplayName("una oferta sobre un encargo de alquiler escribe su hito O como ALQUILER")
    void laMesaFuncionaIgualSobreUnEncargoDeAlquiler() {
        Escenario e = unaMesaDeAlquiler();

        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("2800"), "PEN");
        assertEquals(1, oferta.ronda());
        assertNotNull(oferta.referencia(), "el encargo de alquiler nació con su hito U");

        Map<String, Object> hito = jdbc.queryForMap("""
                select operacion, hito, id_captacion, id_oferta
                  from precio_propiedad where id_oferta = ?
                """, oferta.id());
        assertEquals("A", hito.get("operacion"),
                "la operación sale del encargo; suponerla es como se mezclan renta y venta");
        assertEquals(PrecioPropiedad.HITO_OFERTADO, hito.get("hito"));
        assertEquals(e.idCaptacion(), ((Number) hito.get("id_captacion")).longValue());
    }

    /**
     * El importe de la escritura es opcional, pero <b>si viene exige su propia
     * moneda</b>: aquí se pacta en dólares y se escritura en soles con
     * frecuencia, y suponer que coinciden convertiría 700.000 soles en 700.000
     * dólares.
     */
    @Test
    @DisplayName("un importe declarado sin su moneda se rechaza; con ella se guarda aparte")
    void loDeclaradoEnLaEscrituraLlevaSuPropiaMoneda() {
        Escenario e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        ofertas.aceptar(e.idOportunidad(), oferta.id(), null, e.agente());

        assertThrows(ReglaNegocioException.class, () -> cierres.cerrar(e.idCaptacion(),
                new CierreVentaService.DatosCierreVenta(oferta.id(), null, null,
                        new BigDecimal("900000"), null, LocalDate.now(), null), e.agente()));
        assertEquals(0, compraventasDelEncargo(e.idCaptacion()),
                "una validación que falla no puede dejar el contrato escrito");

        CierreVentaService.FichaCierreVenta cierre = cierres.cerrar(e.idCaptacion(),
                new CierreVentaService.DatosCierreVenta(oferta.id(), null, null,
                        new BigDecimal("900000"), "PEN", LocalDate.now(), null), e.agente());

        assertEquals(0, new BigDecimal("350000").compareTo(cierre.montoPactado()));
        assertEquals("USD", cierre.moneda());
        assertEquals(0, new BigDecimal("900000").compareTo(cierre.montoDeclarado()));
        assertEquals("PEN", cierre.monedaDeclarada(),
                "lo pactado y lo declarado son dos cifras y pueden ir en dos monedas");
    }

    /** Sólo se cierra con una oferta ACEPTADA; una viva todavía no es un acuerdo. */
    @Test
    @DisplayName("el cierre de venta exige una oferta ACEPTADA")
    void noSeCierraConUnaOfertaQueSigueSobreLaMesa() {
        Escenario e = unaVentaLista();
        OfertaService.FichaOferta viva = presentar(e, new BigDecimal("350000"));

        assertThrows(ReglaNegocioException.class, () -> cierres.cerrar(e.idCaptacion(),
                new CierreVentaService.DatosCierreVenta(viva.id(), null, null, null, null,
                        LocalDate.now(), null), e.agente()));
        assertEquals(0, compraventasDelEncargo(e.idCaptacion()));
    }

    /** Una mesa se firma una vez. La segunda es 409, y lo impone la base. */
    @Test
    @DisplayName("un segundo cierre de la misma mesa responde 409")
    void unaMesaSeCierraUnaSolaVez() {
        Escenario e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        ofertas.aceptar(e.idOportunidad(), oferta.id(), null, e.agente());
        cierres.cerrar(e.idCaptacion(), new CierreVentaService.DatosCierreVenta(
                oferta.id(), null, null, null, null, LocalDate.now(), null), e.agente());

        assertThrows(RuntimeException.class, () -> cierres.cerrar(e.idCaptacion(),
                new CierreVentaService.DatosCierreVenta(oferta.id(), null, null, null, null,
                        LocalDate.now(), null), e.agente()));
        assertEquals(1, compraventasDelEncargo(e.idCaptacion()));
    }

    // ==================================================================
    // 7. La conservación: ningún hito sin encargo
    // ==================================================================

    /**
     * <b>Ninguna fila de la serie económica puede quedarse sin encargo</b>, y eso
     * incluye las que WP-A añade. Un hito colgando sólo de la propiedad hace que
     * dos encargos sucesivos del mismo inmueble mezclen sus cifras en una línea
     * que nadie puede volver a separar.
     *
     * <h2>Lo legado NO se cuenta, y hay que decir por qué</h2>
     * {@code tg_precio_exige_encargo} (V76) es {@code BEFORE INSERT}: corta lo
     * que entra, no reescribe lo que ya estaba. En esta base de pruebas quedan
     * <b>filas anteriores a V76 sin {@code id_captacion}</b>, y son exactamente
     * lo que el modelo manda dejar en paz: no se sabe de qué encargo salieron,
     * inventarlo sería atribuir una cifra a un trato que quizá no la produjo, y
     * borrarlas perdería el dato. Se quedan FALTANTES.
     *
     * <p>Por eso la cuenta se acota a lo que este corte escribe: <b>ningún hito
     * NUEVO puede nacer sin encargo</b>, que es la regla viva. El censo de lo
     * legado se mide aparte y se registra como evidencia, no como fallo.
     */
    @Test
    @DisplayName("ningun hito de precio NUEVO queda sin su encargo, tampoco los de la negociacion")
    void ningunHitoDeLaSerieSeQuedaSinEncargo() {
        int legadoAntes = hitosSinEncargoEnTodaLaBase();

        Escenario e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        ofertas.aceptar(e.idOportunidad(), oferta.id(), null, e.agente());
        cierres.cerrar(e.idCaptacion(), new CierreVentaService.DatosCierreVenta(
                oferta.id(), null, null, null, null, LocalDate.now(), null), e.agente());

        assertEquals(0, hitos(e.idCaptacion(), null),
                "ningún hito de este encargo puede quedarse sin declararlo");
        assertEquals(legadoAntes, hitosSinEncargoEnTodaLaBase(),
                "una venta completa no puede añadir NI UNA fila huérfana al histórico: "
                        + "las que había siguen siendo las mismas");

        // Y los dos que nacen de una mesa dicen ADEMÁS de qué vuelta salieron.
        Integer sinOferta = jdbc.queryForObject("""
                select count(*) from precio_propiedad
                 where id_captacion = ? and hito in ('O', 'C') and id_oferta is null
                """, Integer.class, e.idCaptacion());
        assertEquals(0, sinOferta, "un hito O o C sin id_oferta no dice de qué negociación salió");

        // Mientras que U y P no llevan ninguna, y eso es la respuesta correcta:
        // no nacen de ninguna mesa.
        Integer conOfertaIndebida = jdbc.queryForObject("""
                select count(*) from precio_propiedad
                 where id_captacion = ? and hito in ('U', 'P') and id_oferta is not null
                """, Integer.class, e.idCaptacion());
        assertEquals(0, conOfertaIndebida);
    }

    /**
     * El trigger {@code tg_oferta_encargo_de_la_mesa}: una oferta no puede
     * declarar el encargo de otra propiedad. Sin él, la serie de ese encargo se
     * llenaría de cifras de una negociación ajena.
     */
    @Test
    @DisplayName("una oferta no puede declarar el encargo de otra mesa")
    void laOfertaYSuMesaHablanDelMismoEncargo() {
        Escenario uno = unaVentaLista();
        Escenario dos = unaVentaLista();

        assertThrows(RuntimeException.class, () -> jdbc.update("""
                insert into oferta (organizacion_id, id_oportunidad, id_captacion, ronda,
                                    presentada_por, monto, moneda, estado, fecha,
                                    id_rol_registra, origen)
                values (?, ?, ?, 1, 'I', 100000, 'USD', 'P', current_date, ?, 'SPA')
                """, uno.agente().idOrganizacion(), uno.idOportunidad(), dos.idCaptacion(),
                uno.agente().idRolOperativo()));

        assertEquals(0, ofertasDeLaMesa(uno.idOportunidad()));
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    /** Lo que el encargo autoriza. Es también la referencia cuando no hay anuncio. */
    private static final BigDecimal IMPORTE_DEL_ENCARGO = new BigDecimal("400000");

    private record Escenario(Actor agente, long idPropiedad, long idCaptacion, long idOportunidad) { }

    /** Un encargo de VENTA aprobado y su mesa abierta con un comprador. */
    private Escenario unaVentaLista() {
        return unaMesa("VENTA", IMPORTE_DEL_ENCARGO, "USD");
    }

    /** Lo mismo por el lado del ALQUILER: la mesa es la misma, el cierre no. */
    private Escenario unaMesaDeAlquiler() {
        return unaMesa("ALQUILER", new BigDecimal("3000"), "PEN");
    }

    private Escenario unaMesa(String operacion, BigDecimal importe, String moneda) {
        Actor agente = unAgente();
        long idPropiedad = propiedades.registrar(new ComandoRegistro(null, null, null,
                "LOCAL_COMERCIAL", null, "Caso WP-A",
                new Ubicacion("Av. Negociacion " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(unPropietario(agente), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "120")),
                List.of(new OperacionSolicitada(operacion, importe, moneda,
                        null, null, null, null, null, null, null)),
                null), agente).idPropiedad();

        long idCaptacion = encargoDe(idPropiedad, operacion);
        // El bróker aprueba: sin encargo ACTIVO no se negocia sobre él.
        captaciones.decidir(idCaptacion, "APROBAR", null, brokerDe(agente));

        // El código se declara, y no se deja autogenerar: el automático es
        // `yyMMddHHmmss` y dos escenarios del mismo segundo chocan contra
        // `uq_oportunidad_codigo`. Eso no es un hallazgo de producto -- una
        // persona no da de alta dos oportunidades en el mismo segundo -- pero sí
        // haría esta suite intermitente, y una prueba que a veces pasa no prueba.
        String codigo = "OWPA" + SIGUIENTE_MESA.getAndIncrement();
        long idOportunidad = oportunidades.registrar(new OportunidadService.DatosOportunidad(
                codigo, unCliente(agente), idCaptacion, "Mesa WP-A", null), agente).id();
        return new Escenario(agente, idPropiedad, idCaptacion, idOportunidad);
    }

    /** Un código de oportunidad distinto por escenario, dentro de los 20 caracteres. */
    private static final java.util.concurrent.atomic.AtomicLong SIGUIENTE_MESA =
            new java.util.concurrent.atomic.AtomicLong(Math.floorMod(System.nanoTime(), 1_000_000L));

    private long encargoDe(long idPropiedad, String operacion) {
        Long id = jdbc.queryForObject("""
                select max(id_captacion) from captacion
                 where id_propiedad = ? and motivo_operacion = ?
                """, Long.class, idPropiedad, "VENTA".equals(operacion) ? "V" : "A");
        assertNotNull(id, "el alta con operación tiene que haber abierto su encargo");
        return id;
    }

    private OfertaService.FichaOferta presentar(Escenario e, BigDecimal monto) {
        return presentar(e, monto, "USD");
    }

    private OfertaService.FichaOferta presentar(Escenario e, BigDecimal monto, String moneda) {
        return ofertas.presentar(e.idOportunidad(), new DatosOferta(monto, moneda,
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()),
                Procedencia.deCabecera("SPA"), e.agente());
    }

    // ------------------------------------------------------------------
    // Actores
    // ------------------------------------------------------------------

    private Actor unAgente() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                  join supervision_agente s on s.id_rol_agente = a.id_persona_rol
                                           and s.fecha_fin is null
                 order by a.id_persona_rol limit 1
                """).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    /**
     * Otro agente del MISMO equipo. Del mismo equipo y no de otra corredora: lo
     * que se prueba es la autoridad DENTRO del tenant, que es donde el defecto
     * podría vivir — entre corredoras ya corta V6, y el resultado sería un 404
     * que no dice nada sobre quién puede negociar.
     */
    private Actor otroAgenteDelMismoEquipo(Actor primero) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 where r.organizacion_id = ? and a.id_persona_rol <> ?
                 order by a.id_persona_rol limit 1
                """, primero.idOrganizacion(), primero.idRolOperativo());
        assertFalse(filas.isEmpty(), "sin un segundo agente no hay «otro» que probar");
        Map<String, Object> fila = filas.get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    private Actor brokerDe(Actor agente) {
        Map<String, Object> fila = jdbc.queryForList("""
                select b.id_persona_rol, r.organizacion_id, r.id_persona
                  from supervision_agente s
                  join detalle_broker b on b.id_persona_rol = s.id_rol_broker
                  join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where s.id_rol_agente = ? and s.fecha_fin is null
                 order by b.id_persona_rol
                 limit 1
                """, agente.idRolOperativo()).get(0);
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

    /** Un comprador nuevo por escenario: reutilizar uno mezclaría las mesas. */
    private Long unCliente(Actor actor) {
        // 'D' es el codigo del DNI: el vocabulario de documentos es de UNA letra,
        // como el resto del dominio. Y son ocho digitos exactos.
        String documento = String.format("%08d", SIGUIENTE_DNI.getAndIncrement());
        return clientes.registrar(new ClienteService.DatosCliente("N", "D", documento,
                "Comprador WP-A " + documento, "9" + documento,
                "wpa" + documento + "@prueba.test", "Comercio", Boolean.TRUE, Boolean.TRUE, null),
                actor).id();
    }

    /**
     * Un DNI distinto por comprador, y en un tramo alto para no chocar con el
     * seed. Empieza donde la corrida arranca: dos ejecuciones seguidas no pueden
     * pedir de alta la misma persona.
     */
    private static final java.util.concurrent.atomic.AtomicLong SIGUIENTE_DNI =
            new java.util.concurrent.atomic.AtomicLong(
                    40_000_000L + Math.floorMod(System.nanoTime(), 50_000_000L));

    // ------------------------------------------------------------------
    // Lecturas contra la base
    // ------------------------------------------------------------------

    /** Cuántos hitos de un tipo tiene este encargo; con {@code hito} nulo, los que no lo declaran. */
    private int hitos(long idCaptacion, String hito) {
        Integer n = hito == null
                ? jdbc.queryForObject("""
                        select count(*) from precio_propiedad
                         where id_captacion = ? and hito is null
                        """, Integer.class, idCaptacion)
                : jdbc.queryForObject(
                        "select count(*) from precio_propiedad where id_captacion = ? and hito = ?",
                        Integer.class, idCaptacion, hito);
        return n == null ? 0 : n;
    }

    /**
     * El censo de filas anteriores a V76 que no dicen de qué encargo salieron.
     * Se mide, no se corrige: lo ambiguo permanece FALTANTE.
     */
    private int hitosSinEncargoEnTodaLaBase() {
        Integer n = jdbc.queryForObject(
                "select count(*) from precio_propiedad where id_captacion is null", Integer.class);
        return n == null ? 0 : n;
    }

    private List<String> serieDelEncargo(long idCaptacion) {
        return jdbc.queryForList(
                "select hito from precio_propiedad where id_captacion = ? order by id_precio",
                String.class, idCaptacion);
    }

    private Long ofertaDelHito(long idCaptacion, String hito, BigDecimal monto) {
        return jdbc.queryForObject("""
                select id_oferta from precio_propiedad
                 where id_captacion = ? and hito = ? and monto = ?
                """, Long.class, idCaptacion, hito, monto);
    }

    private String estadoDeLaOferta(long idOferta) {
        return jdbc.queryForObject("select estado from oferta where id_oferta = ?",
                String.class, idOferta);
    }

    private int ofertasEnEstado(long idOportunidad, String estado) {
        Integer n = jdbc.queryForObject(
                "select count(*) from oferta where id_oportunidad = ? and estado = ?",
                Integer.class, idOportunidad, estado);
        return n == null ? 0 : n;
    }

    private int ofertasDeLaMesa(long idOportunidad) {
        Integer n = jdbc.queryForObject("select count(*) from oferta where id_oportunidad = ?",
                Integer.class, idOportunidad);
        return n == null ? 0 : n;
    }

    private int compraventasDelEncargo(long idCaptacion) {
        Integer n = jdbc.queryForObject(
                "select count(*) from contrato_compraventa where id_captacion = ?",
                Integer.class, idCaptacion);
        return n == null ? 0 : n;
    }

    private String estadoDelEncargo(long idCaptacion) {
        return jdbc.queryForObject("select estado from captacion where id_captacion = ?",
                String.class, idCaptacion);
    }

    private String motivoDeCierreDelEncargo(long idCaptacion) {
        return jdbc.queryForObject("select motivo_cierre from captacion where id_captacion = ?",
                String.class, idCaptacion);
    }

    private String estadoDeLaMesa(long idOportunidad) {
        return jdbc.queryForObject(
                "select estado from oportunidad_comercial where id_oportunidad = ?",
                String.class, idOportunidad);
    }

    private String disponibilidadDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select disponibilidad_comercial from propiedad where id_propiedad = ?",
                String.class, idPropiedad);
    }

    private int anunciosVivosDelEncargo(long idCaptacion) {
        Integer n = jdbc.queryForObject("""
                select count(*) from publicacion
                 where id_captacion = ? and estado <> 'C'
                """, Integer.class, idCaptacion);
        return n == null ? 0 : n;
    }

    private String razonDeLaCaida(long idOportunidad) {
        return jdbc.queryForObject("""
                select razon_principal from motivo_no_continuidad
                 where id_oportunidad = ? order by id_motivo desc limit 1
                """, String.class, idOportunidad);
    }

    /**
     * Un rótulo tiene que ser una frase, no la letra otra vez. Devolver el
     * código sería el comportamiento de {@code rotuloDe} ante un código que NO
     * está en el vocabulario, así que verlo aquí significaría que una razón
     * admitida se quedó sin nombre legible.
     */
    private static void assertNotEqualsAlCodigo(String razon) {
        assertFalse(razon.equals(MotivoNoContinuidad.rotuloDe(razon)),
                "la razón " + razon + " no tiene rótulo: saldría como la letra cruda en el informe");
    }
}
