package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository;
import com.controllocal.persistence.repositorio.ContratoCompraventaRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.OfertaRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService.DatosOferta;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.ElegibilidadDeResponsable;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WP-A — las reglas de la mesa que <b>no</b> necesitan PostgreSQL.
 *
 * <p>Lo que sí lo necesita —los dos índices parciales, los tres triggers y la
 * cascada del cierre— vive en {@code NegociacionYCierreDeVentaIntegrationTest}
 * y no se duplica aquí: una comprobación previa con mocks que pretenda probar
 * una carrera sólo prueba la comprobación previa.
 *
 * <p><b>La autoridad va de verdad</b>, no mockeada. Escribir una oferta escribe
 * la serie económica del encargo, así que estas pruebas tienen que correr con la
 * regla puesta: con un mock permisivo seguirían verdes justo el día en que
 * alguien la quitara.
 */
class OfertaServiceImplTest {

    private static final long ORG = 1L;
    private static final long PROPIEDAD = 7L;
    private static final long ENCARGO = 55L;
    private static final long MESA = 900L;
    private static final long ROL_AGENTE = 30L;

    private final OfertaRepository ofertas = mock(OfertaRepository.class);
    private final PrecioPropiedadRepository precios = mock(PrecioPropiedadRepository.class);
    private final ContratoCompraventaRepository compraventas =
            mock(ContratoCompraventaRepository.class);
    private final AccesoOportunidad acceso = mock(AccesoOportunidad.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    /** La autoridad real: es lo único que impide negociar el encargo de otro. */
    private final AutoridadDePropiedad autoridad = new AutoridadDePropiedad(
            mock(DetalleAgenteRepository.class),
            mock(AsignacionResponsablePropiedadRepository.class),
            new Alcances(mock(SupervisionAgenteRepository.class)),
            new ElegibilidadDeResponsable(mock(DetalleAgenteRepository.class)),
            mock(PropiedadRepository.class));

    private final OfertaServiceImpl service = new OfertaServiceImpl(
            ofertas, precios, compraventas, acceso, autoridad, new Transiciones(historial));

    /** El agente que lleva ESTE encargo. */
    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, Actor.AGENTE);
    /** Otro agente del mismo tenant: el que no puede negociarlo. */
    private final Actor ajeno = new Actor(ORG, 4L, 44L, Actor.AGENTE);
    /** El gobierno del tenant. Gobernar no es operar. */
    private final Actor gobierno = new Actor(ORG, 5L, 55L, Actor.TENANT_ADMIN);

    private OportunidadComercial mesa;

    @BeforeEach
    void unaMesaAbiertaSobreUnEncargoActivo() {
        mesa = mesaViva();
        lenient().when(acceso.conAcceso(eq(MESA), any())).thenReturn(mesa);
        lenient().when(acceso.alcanza(any(), any())).thenReturn(true);
        lenient().when(ofertas.vivaDe(ORG, MESA)).thenReturn(Optional.empty());
        lenient().when(ofertas.aceptadaDe(ORG, MESA)).thenReturn(Optional.empty());
        lenient().when(compraventas.deLaMesa(ORG, MESA)).thenReturn(Optional.empty());
        lenient().when(ofertas.ultimaRondaDe(ORG, MESA)).thenReturn(null);
        lenient().when(ofertas.save(any())).thenAnswer(i -> conId(i.getArgument(0), 4001L));
        lenient().when(ofertas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(
                anyLong(), any())).thenReturn(Optional.empty());
    }

    // ==================================================================
    // Nada se deduce: lo que falta se rechaza
    // ==================================================================

    /**
     * <b>Sin moneda no hay oferta, y no hay defecto.</b> Elegir la del encargo
     * sería suponer que se ofreció en la misma, que es justo lo que no se sabe:
     * en este mercado se pide en dólares y se ofrece en soles con frecuencia.
     */
    @Test
    @DisplayName("una oferta sin moneda se rechaza; no hereda la del encargo")
    void laMonedaNoTieneDefecto() {
        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), null,
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente));

        assertTrue(fallo.getMessage().toLowerCase().contains("moneda"));
        verify(ofertas, never()).save(any());
        verify(precios, never()).save(any());
    }

    /**
     * <b>Quién presentó no se deduce de la ronda.</b> Una negociación puede
     * empezar por cualquiera de los dos lados, y deducirlo sería inventar quién
     * habló primero — el dato que después explica quién cedió.
     */
    @Test
    @DisplayName("presentar sin declarar el lado se rechaza")
    void elLadoDeLaPrimeraOfertaSeDeclara() {
        assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                        null, null, LocalDate.now()), canal(), agente));
        verify(ofertas, never()).save(any());
    }

    @Test
    @DisplayName("un importe que no es mayor que cero no es una oferta")
    void elImporteTieneQueSerPositivo() {
        assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(BigDecimal.ZERO, "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente));
        assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(null, "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente));
        verify(ofertas, never()).save(any());
    }

    /** Una oferta futura no es una oferta: es una intención, y eso no es evidencia. */
    @Test
    @DisplayName("una oferta con fecha futura se rechaza")
    void laFechaNoPuedeSerFutura() {
        assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                                Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now().plusDays(1)),
                        canal(), agente));
        verify(ofertas, never()).save(any());
    }

    // ==================================================================
    // La referencia congelada (D-N-1)
    // ==================================================================

    /**
     * Con anuncio, la referencia es el <b>publicado</b>: es lo que el interesado
     * vio. El autorizado es lo que el propietario aceptó en privado, y no es lo
     * mismo.
     */
    @Test
    @DisplayName("habiendo hito P, la referencia es el publicado y no el autorizado")
    void laReferenciaPrefiereElPublicado() {
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(
                ENCARGO, PrecioPropiedad.HITO_PUBLICADO))
                .thenReturn(Optional.of(hito(101L, PrecioPropiedad.HITO_PUBLICADO,
                        new BigDecimal("390000"), "USD")));

        Oferta escrita = presentarYCapturar();

        assertEquals(PrecioPropiedad.HITO_PUBLICADO, escrita.getHitoReferencia());
        assertEquals(101L, escrita.getIdPrecioReferencia());
        assertEquals(0, new BigDecimal("390000").compareTo(escrita.getMontoReferencia()));
        assertEquals("USD", escrita.getMonedaReferencia());
        // Y NO se preguntó por el autorizado: habiendo P, el U no se usa.
        verify(precios, never()).findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(
                ENCARGO, PrecioPropiedad.HITO_AUTORIZADO);
    }

    @Test
    @DisplayName("sin hito P, la referencia cae al ultimo AUTORIZADO del encargo")
    void sinPublicadoLaReferenciaEsElAutorizado() {
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(
                ENCARGO, PrecioPropiedad.HITO_AUTORIZADO))
                .thenReturn(Optional.of(hito(77L, PrecioPropiedad.HITO_AUTORIZADO,
                        new BigDecimal("400000"), "USD")));

        Oferta escrita = presentarYCapturar();

        assertEquals(PrecioPropiedad.HITO_AUTORIZADO, escrita.getHitoReferencia());
        assertEquals(0, new BigDecimal("400000").compareTo(escrita.getMontoReferencia()));
    }

    /**
     * <b>Sin P ni U los cuatro campos quedan nulos.</b> No se sustituye por el
     * importe de la propia oferta ni por el del encargo: un descuento calculado
     * contra una cifra inventada se cita después en una negociación.
     */
    @Test
    @DisplayName("sin P ni U la referencia es FALTANTE y no se rellena con la propia oferta")
    void laReferenciaFaltanteSeQuedaFaltante() {
        Oferta escrita = presentarYCapturar();

        assertNull(escrita.getHitoReferencia());
        assertNull(escrita.getIdPrecioReferencia());
        assertNull(escrita.getMontoReferencia());
        assertNull(escrita.getMonedaReferencia());
        assertEquals(0, new BigDecimal("350000").compareTo(escrita.getMonto()),
                "la oferta sí lleva su importe: lo que falta es contra qué compararlo");
    }

    /**
     * <b>Por ENCARGO y no por propiedad.</b> Un inmueble en venta y en alquiler
     * tiene dos series; preguntar por la propiedad devolvería la renta mensual
     * como referencia de una oferta de compra.
     */
    @Test
    @DisplayName("la referencia se busca por encargo, nunca por propiedad")
    void laReferenciaSeBuscaPorEncargo() {
        presentarYCapturar();

        verify(precios).findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(
                ENCARGO, PrecioPropiedad.HITO_PUBLICADO);
        verify(precios, never()).findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                anyLong(), any());
    }

    // ==================================================================
    // El hito de la serie
    // ==================================================================

    /**
     * Cada vuelta deja su hito {@code O} con <b>las dos referencias</b>: el
     * encargo, para poder leer la serie sin pasar por la mesa, y la oferta, para
     * poder separar dos negociaciones sobre el mismo inmueble.
     */
    @Test
    @DisplayName("presentar escribe un hito O con id_captacion e id_oferta")
    void cadaVueltaDejaSuHitoEnLaSerieDelEncargo() {
        service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente);

        ArgumentCaptor<PrecioPropiedad> captor = ArgumentCaptor.forClass(PrecioPropiedad.class);
        verify(precios).save(captor.capture());
        PrecioPropiedad hito = captor.getValue();

        assertEquals(PrecioPropiedad.HITO_OFERTADO, hito.getHito());
        assertEquals(ENCARGO, hito.getIdCaptacion());
        assertEquals(4001L, hito.getIdOferta(), "sin id_oferta el hito no dice de qué vuelta salió");
        assertEquals(OperacionInmobiliaria.VENTA.codigo(), hito.getOperacion(),
                "la operación sale del encargo, no de un defecto");
        assertEquals(0, new BigDecimal("350000").compareTo(hito.getMonto()));
    }

    // ==================================================================
    // La contraoferta
    // ==================================================================

    /**
     * <b>El lado de una contraoferta no se declara: lo dicta la anterior.</b>
     * Dejárselo al cliente permitiría una cadena en la que el interesado se
     * contraoferta a sí mismo, que no es una negociación sino dos ofertas suyas
     * seguidas.
     */
    @Test
    @DisplayName("la contraoferta la firma el lado contrario, aunque el cuerpo diga otra cosa")
    void elLadoDeLaContraofertaSeDeduceYNoSeAcepta() {
        Oferta anterior = viva(3001L, 1, Oferta.LA_PRESENTA_EL_INTERESADO);
        when(ofertas.buscarFicha(ORG, 3001L)).thenReturn(Optional.of(anterior));
        when(ofertas.ultimaRondaDe(ORG, MESA)).thenReturn(1);

        // El cuerpo intenta imponer el MISMO lado. Se ignora.
        service.contraofertar(MESA, 3001L, new DatosOferta(new BigDecimal("372000"), "USD",
                Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente);

        ArgumentCaptor<Oferta> captor = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertas).save(captor.capture());
        assertEquals(Oferta.LA_PRESENTA_EL_PROPIETARIO, captor.getValue().getPresentadaPor());
        assertEquals(2, captor.getValue().getRonda(), "la ronda no se reutiliza");
        assertEquals(Oferta.SUPERADA, anterior.estadoActual());
    }

    /**
     * La anterior sale de la mesa <b>con flush</b> antes de que entre la nueva.
     * Hibernate ordena los {@code INSERT} antes que los {@code UPDATE}, así que
     * sin ese flush la fila nueva chocaría contra el índice parcial que impide
     * dos vivas — y el usuario vería un 409 en una operación legítima.
     */
    @Test
    @DisplayName("la oferta superada se persiste con flush antes de insertar la nueva")
    void laAnteriorSaleDeLaMesaAntesDeQueEntreLaNueva() {
        Oferta anterior = viva(3001L, 1, Oferta.LA_PRESENTA_EL_INTERESADO);
        when(ofertas.buscarFicha(ORG, 3001L)).thenReturn(Optional.of(anterior));

        service.contraofertar(MESA, 3001L, new DatosOferta(new BigDecimal("372000"), "USD",
                null, null, LocalDate.now()), canal(), agente);

        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(ofertas);
        orden.verify(ofertas).saveAndFlush(anterior);
        orden.verify(ofertas).save(any());
    }

    /** Sólo se contraoferta la que sigue sobre la mesa. */
    @Test
    @DisplayName("no se contraoferta una oferta ya resuelta")
    void soloSeRespondeALaQueSigueViva() {
        Oferta rechazada = viva(3001L, 1, Oferta.LA_PRESENTA_EL_INTERESADO);
        rechazada.transicionarA(Oferta.RECHAZADA);
        when(ofertas.buscarFicha(ORG, 3001L)).thenReturn(Optional.of(rechazada));

        assertThrows(ConflictoException.class,
                () -> service.contraofertar(MESA, 3001L, new DatosOferta(new BigDecimal("372000"),
                        "USD", null, null, LocalDate.now()), canal(), agente));
        verify(ofertas, never()).save(any());
    }

    // ==================================================================
    // Lo que rechazar NO hace
    // ==================================================================

    /**
     * <b>Rechazar una cifra no cierra la oportunidad.</b> Que el propietario diga
     * que no a un importe no significa que el interesado se haya ido, y
     * convertir un rechazo en una caída borraría de la historia las
     * negociaciones que siguieron después de un no.
     */
    @Test
    @DisplayName("rechazar toca la oferta y NADA MAS")
    void rechazarNoCierraLaOportunidad() {
        Oferta abierta = viva(3001L, 1, Oferta.LA_PRESENTA_EL_INTERESADO);
        when(ofertas.buscarFicha(ORG, 3001L)).thenReturn(Optional.of(abierta));

        service.rechazar(MESA, 3001L, "Muy por debajo.", agente);

        assertEquals(Oferta.RECHAZADA, abierta.estadoActual());
        assertEquals(OportunidadComercial.ABIERTA, mesa.estadoActual(),
                "la mesa sigue abierta: la caída es un acto aparte, con su motivo");
        verify(precios, never()).save(any());
    }

    /**
     * Retirada y rechazada no son el mismo hecho —en una cede quien ofreció, en
     * la otra quien escuchaba—, y fundirlas borraría de la historia quién cedió.
     */
    @Test
    @DisplayName("retirar deja estado propio, distinto de rechazada")
    void retirarNoEsRechazar() {
        Oferta abierta = viva(3001L, 1, Oferta.LA_PRESENTA_EL_PROPIETARIO);
        when(ofertas.buscarFicha(ORG, 3001L)).thenReturn(Optional.of(abierta));

        service.retirar(MESA, 3001L, null, agente);

        assertEquals(Oferta.RETIRADA, abierta.estadoActual());
    }

    // ==================================================================
    // Autoridad
    // ==================================================================

    /** Escribir en la mesa es operar el ENCARGO: lo hace su agente. */
    @Test
    @DisplayName("otro agente del mismo tenant no escribe en la mesa")
    void otroAgenteNoNegociaElEncargoAjeno() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), ajeno));
        verify(ofertas, never()).save(any());
        verify(precios, never()).save(any());
    }

    /** Gobernar no es operar: el TENANT_ADMIN no firma hechos del negocio. */
    @Test
    @DisplayName("el gobierno del tenant no escribe en la mesa")
    void elGobiernoDelTenantNoNegocia() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), gobierno));
        verify(ofertas, never()).save(any());
    }

    /**
     * <b>Las capacidades salen del mismo predicado que después deniega.</b> Si
     * este test dejara de cuadrar, la pantalla estaría pintando un botón que el
     * backend rechaza — que es el peor error de esta superficie.
     */
    @Test
    @DisplayName("las capacidades de quien no puede escribir son todas falsas")
    void lasCapacidadesUsanLaMismaGuardaQueLosComandos() {
        var suyas = service.capacidades(MESA, agente);
        assertTrue(suyas.puedeOfertar(), "sin oferta viva y con la mesa abierta, ofertar se puede");
        assertTrue(suyas.puedeRegistrarCaida());

        var ajenas = service.capacidades(MESA, ajeno);
        assertTrue(!ajenas.puedeOfertar() && !ajenas.puedeContraofertar()
                && !ajenas.puedeAceptar() && !ajenas.puedeCerrarVenta(),
                "quien no puede escribir no puede nada de la mesa");
    }

    /**
     * Con una oferta viva se responde, no se presenta otra: son excluyentes, y
     * el par de banderas tiene que decirlo.
     */
    @Test
    @DisplayName("con una oferta viva, puedeOfertar es falso y responder es verdadero")
    void ofertarYResponderSonExcluyentes() {
        when(ofertas.vivaDe(ORG, MESA))
                .thenReturn(Optional.of(viva(3001L, 1, Oferta.LA_PRESENTA_EL_INTERESADO)));

        var capacidades = service.capacidades(MESA, agente);

        assertTrue(!capacidades.puedeOfertar());
        assertTrue(capacidades.puedeContraofertar());
        assertTrue(capacidades.puedeAceptar());
        assertTrue(capacidades.puedeRechazar());
        assertTrue(capacidades.puedeRetirar());
    }

    // ==================================================================
    // Estado de la mesa y del encargo
    // ==================================================================

    /** Una oportunidad cerrada no recibe más cifras. */
    @Test
    @DisplayName("una mesa cerrada no admite ofertas")
    void unaMesaCerradaNoAdmiteOfertas() {
        mesa.transicionarA(OportunidadComercial.NO_CONTINUA);

        assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente));
        verify(ofertas, never()).save(any());
    }

    /**
     * <b>Con una oferta ACEPTADA la mesa está cerrada, aunque no se haya
     * firmado.</b>
     *
     * <p>Lo encontró la prueba de integración y era un defecto de verdad: el
     * índice parcial sólo impide una SEGUNDA aceptada, no una presentada más, así
     * que la mesa aceptaba una cifra nueva junto a la aceptada y la persona la
     * escribía entera para que {@code aceptar} respondiera 409 al final. Y
     * {@code capacidades} ya decía lo contrario —{@code negociable} exige que no
     * haya aceptada—: un comando que concede lo que la capacidad niega es el
     * mismo error de dos criterios, por el otro lado.
     */
    @Test
    @DisplayName("con una oferta aceptada, la mesa no admite otra cifra")
    void unaMesaConOfertaAceptadaEstaCerrada() {
        when(ofertas.aceptadaDe(ORG, MESA))
                .thenReturn(Optional.of(aceptada(3001L, 1)));

        ConflictoException choque = assertThrows(ConflictoException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("360000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente));
        assertTrue(choque.getMessage().toLowerCase().contains("aceptada"));

        verify(ofertas, never()).save(any());
        verify(precios, never()).save(any());
        // Y la capacidad dice lo mismo, que es lo que este test blinda.
        assertTrue(!service.capacidades(MESA, agente).puedeOfertar());
    }

    /** Negociar sobre un encargo que ya no está vigente sería negociar lo que no se ofrece. */
    @Test
    @DisplayName("un encargo no ACTIVO no admite ofertas")
    void unEncargoCerradoNoAdmiteOfertas() {
        mesa.getCaptacion().transicionarA(Captacion.CERRADA);

        assertThrows(ReglaNegocioException.class,
                () -> service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                        Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente));
        verify(ofertas, never()).save(any());
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    private static Procedencia canal() {
        return Procedencia.deCabecera("SPA");
    }

    private Oferta presentarYCapturar() {
        service.presentar(MESA, new DatosOferta(new BigDecimal("350000"), "USD",
                Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now()), canal(), agente);
        ArgumentCaptor<Oferta> captor = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertas).save(captor.capture());
        return captor.getValue();
    }

    private OportunidadComercial mesaViva() {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        ReflectionTestUtils.setField(propiedad, "id", PROPIEDAD);

        DetalleAgente elAgente = new DetalleAgente();
        ReflectionTestUtils.setField(elAgente, "id", ROL_AGENTE);

        Captacion encargo = new Captacion();
        encargo.setOrganizacionId(ORG);
        ReflectionTestUtils.setField(encargo, "id", ENCARGO);
        encargo.setCodigoCaptacion("CAP-WPA-1");
        encargo.setPropiedad(propiedad);
        encargo.setAgente(elAgente);
        encargo.setMotivoOperacion("VENTA");
        new Transiciones(historial).iniciar(encargo, Captacion.ACTIVA);

        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setOrganizacionId(ORG);
        ReflectionTestUtils.setField(oportunidad, "id", MESA);
        oportunidad.setCodigoOportunidad("OPO-WPA-1");
        oportunidad.setCaptacion(encargo);
        oportunidad.setAgente(elAgente);
        new Transiciones(historial).iniciar(oportunidad, OportunidadComercial.ABIERTA);
        return oportunidad;
    }

    private Oferta viva(long id, int ronda, String lado) {
        Oferta oferta = new Oferta();
        oferta.setOrganizacionId(ORG);
        ReflectionTestUtils.setField(oferta, "id", id);
        oferta.setIdOportunidad(MESA);
        oferta.setIdCaptacion(ENCARGO);
        oferta.setRonda(ronda);
        oferta.setPresentadaPor(lado);
        oferta.setMonto(new BigDecimal("350000"));
        oferta.setMoneda("USD");
        oferta.setFecha(LocalDate.now());
        oferta.setIdRolRegistra(ROL_AGENTE);
        oferta.setOrigen("SPA");
        new Transiciones(historial).iniciar(oferta, Oferta.PRESENTADA);
        return oferta;
    }

    private Oferta aceptada(long id, int ronda) {
        Oferta oferta = viva(id, ronda, Oferta.LA_PRESENTA_EL_INTERESADO);
        oferta.transicionarA(Oferta.ACEPTADA);
        return oferta;
    }

    private static PrecioPropiedad hito(long id, String cual, BigDecimal monto, String moneda) {
        PrecioPropiedad precio = PrecioPropiedad.hito(ORG, PROPIEDAD, OperacionInmobiliaria.VENTA,
                cual, moneda, monto, LocalDate.now()).delEncargo(ENCARGO);
        ReflectionTestUtils.setField(precio, "id", id);
        return precio;
    }

    private static Oferta conId(Oferta oferta, long id) {
        ReflectionTestUtils.setField(oferta, "id", id);
        return oferta;
    }
}
