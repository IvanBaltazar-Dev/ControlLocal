package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.SesionesComercialesDePrueba;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.ContextosService;
import com.controllocal.service.CuentaService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.CifradoSecretos;
import org.springframework.transaction.PlatformTransactionManager;
import com.controllocal.service.Actor;
import com.controllocal.service.CierreVentaService;
import com.controllocal.service.ClienteService;
import com.controllocal.service.OfertaService;
import com.controllocal.service.OfertaService.*;
import com.controllocal.integracion.soporte.ActividadNegociacionDePrueba;
import com.controllocal.integracion.soporte.EvidenciaC4DePrueba;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.integracion.soporte.FormalizacionesDePrueba;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.OportunidadService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>PostgreSQL real: negociación C4 y resultado por hito C5 confirmado por el broker.</b>
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
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class NegociacionYCierreDeVentaIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;

    @BeforeEach void evidenciarCaso(TestInfo info){EvidenciaC4DePrueba.iniciar(info);}
    @AfterEach void cerrarEvidencia(){EvidenciaC4DePrueba.limpiar();}

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired PropiedadUniversalService propiedades;
    @Autowired EncargoService ciclo;
    @Autowired ContextosService contextos;
    @Autowired CuentaService cuentasPersonales;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired PlatformTransactionManager gestor;
    private SesionesComercialesDePrueba sesionesFixture;
    private synchronized SesionesComercialesDePrueba sesiones() {
        if(sesionesFixture==null)sesionesFixture=new SesionesComercialesDePrueba(jdbc,contextos,cuentasPersonales,factores,cifrado);
        return sesionesFixture;
    }
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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();

        // --- La aprobación real C2 produjo el primer hito U.
        assertEquals(1, hitos(e.encargo(), PrecioPropiedad.HITO_AUTORIZADO),
                "la aprobación del Encargo escribe el importe AUTORIZADO");

        // --- El anuncio: hito P, el importe que el mercado VE.
        //
        // Se escribe por `POST /locales/{id}/precios`, que es un productor real
        // de la serie y pasa por la misma autoridad del encargo, y no creando la
        // publicacion: el gate de publicable exige la ficha completa del inmueble
        // y lo que aqui se prueba es la REFERENCIA, no ese gate. Atarlo a el
        // haria que esta prueba se pusiera roja por un atributo obligatorio nuevo
        // que no tiene nada que ver con negociar.
        precios.registrar(e.propiedad(), new PrecioLocalService.DatosPrecio(
                PrecioPropiedad.HITO_PUBLICADO, "USD", new BigDecimal("390000"),
                LocalDate.now(), "VENTA"), e.agente());
        assertEquals(1, hitos(e.encargo(), PrecioPropiedad.HITO_PUBLICADO),
                "el importe publicado entra en la serie de SU encargo");

        // --- Ronda 1: ofrece el interesado, por debajo del anuncio.
        OfertaService.FichaOferta primera = registrarOfertaRevisada(e, new BigDecimal("350000"), "USD", Oferta.LA_PRESENTA_EL_INTERESADO, "Pago al contado", LocalDate.now(), e.agente());

        assertEquals(1, primera.ronda());
        assertEquals(Oferta.PRESENTADA, primera.estado());
        // La referencia se congeló contra el ANUNCIO, no contra el autorizado:
        // habiendo P, el U no es lo que el interesado vio.
        assertNotNull(primera.referencia(), "con anuncio vivo la referencia no puede ser FALTANTE");
        assertEquals(PrecioPropiedad.HITO_PUBLICADO, primera.referencia().hito());
        assertEquals(0, new BigDecimal("390000").compareTo(primera.referencia().monto()));

        // --- Ronda 2: manifestación del propietario identificado; el operador sigue siendo el agente.
        OfertaService.FichaOferta segunda = bancoC4().contraofertar(e, primera, new BigDecimal("372000"), "P");

        assertEquals(2, segunda.ronda());
        assertEquals(Oferta.LA_PRESENTA_EL_PROPIETARIO, segunda.presentadaPor(),
                "la parte real P se declaró explícitamente; no se confunde con el agente operador");
        assertEquals(Oferta.SUPERADA, estadoDeLaOferta(primera.id()),
                "la anterior sale de la mesa cuando entra la contraoferta");

        // --- El sí.
        OfertaService.FichaOferta aceptada = bancoC4().aceptar(e, segunda, "El comprador acepta la contraoferta.");
        assertEquals(Oferta.ACEPTADA, aceptada.estado());

        // --- Dos hitos O, uno por vuelta, cada uno atado a SU oferta.
        assertEquals(2, hitos(e.encargo(), PrecioPropiedad.HITO_OFERTADO));
        assertEquals(primera.id(), ofertaDelHito(e.encargo(), PrecioPropiedad.HITO_OFERTADO,
                new BigDecimal("350000")));
        assertEquals(segunda.id(), ofertaDelHito(e.encargo(), PrecioPropiedad.HITO_OFERTADO,
                new BigDecimal("372000")));

        // --- El cierre.
        CierreVentaService.FichaCierreVenta cierre = cerrarConHito(e,segunda,null,null);
        assertEquals(e.equipo().broker().idRolOperativo(),cierre.idRolBrokerCierre(),"Decidió el broker real; no se transformó al agente en decisor.");

        assertEquals(0, new BigDecimal("372000").compareTo(cierre.montoPactado()),
                "sin ajuste explícito, lo pactado es lo de la oferta aceptada");
        assertEquals("USD", cierre.moneda());
        assertNull(cierre.montoDeclarado(),
                "lo declarado en la escritura nace FALTANTE: igualarlo al pactado sería inventarlo");
        assertNull(cierre.monedaDeclarada());
        assertEquals(2, cierre.rondaOferta());

        // --- Y las seis consecuencias, comprobadas en la base.
        assertEquals(1, hitos(e.encargo(), PrecioPropiedad.HITO_CIERRE),
                "el cierre escribe el último hito de la serie del encargo");
        assertEquals(segunda.id(), ofertaDelHito(e.encargo(), PrecioPropiedad.HITO_CIERRE,
                new BigDecimal("372000")),
                "el hito C dice de qué VUELTA salió, no sólo de qué encargo");
        assertEquals(Captacion.FINALIZADO, estadoDelEncargo(e.encargo()));
        assertEquals("V", motivoDeCierreDelEncargo(e.encargo()),
                "vendido no es alquilado ('A') ni cierre manual del bróker ('M')");
        assertEquals(OportunidadComercial.FINALIZADA_EXITOSA, estadoDeLaMesa(e.oportunidad()));
        assertEquals(DisponibilidadComercial.VENDIDO.codigo(), disponibilidadDe(e.propiedad()),
                "La escritura confirmada acredita venta sobre sus unidades; no una retirada voluntaria.");
        assertEquals(0, anunciosVivosDelEncargo(e.encargo()),
                "los anuncios del encargo vendido se dan de baja");

        // --- La serie del encargo, entera y en orden: U, P, O, O, C.
        assertEquals(List.of("U", "P", "O", "O", "C"), serieDelEncargo(e.encargo()));
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
        ActividadNegociacionDePrueba.Caso e = bancoC4().mesa("VENTA",IMPORTE_DEL_ENCARGO,"USD",false);

        // Preparación legítima: el primer U nace al aprobar el Encargo; no se borra historia.
        assertEquals(0, hitos(e.encargo(), PrecioPropiedad.HITO_AUTORIZADO));

        OfertaService.FichaOferta oferta = registrarOfertaRevisada(e, new BigDecimal("300000"), "USD", Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now(), e.agente());

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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();

        OfertaService.FichaOferta oferta = registrarOfertaRevisada(e, new BigDecimal("330000"), "USD", Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now(), e.agente());

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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();

        OfertaService.FichaOferta primera = presentar(e, new BigDecimal("350000"));
        bancoC4().aceptar(e, primera, null);

        // Con una aceptada, la mesa ya no admite negociar: el 409 llega antes de
        // que exista una segunda oferta que aceptar, que es lo correcto -- la
        // negociación terminó.
        ConflictoException choque = assertThrows(ConflictoException.class,
                () -> presentar(e, new BigDecimal("360000")));
        assertTrue(choque.getMessage().toLowerCase().contains("aceptada")
                        || choque.getMessage().toLowerCase().contains("cerr"),
                "el mensaje tiene que decir por qué, no sólo que no: " + choque.getMessage());

        assertEquals(1, ofertasEnEstado(e.oportunidad(), Oferta.ACEPTADA));
    }

    /**
     * <b>Una sola oferta VIVA.</b> Mientras hay una sobre la mesa, la siguiente
     * cifra es una contraoferta que la supera. Con dos vivas nadie puede decir a
     * cuál se respondió.
     */
    @Test
    @DisplayName("con una oferta viva, presentar otra responde 409")
    void dosOfertasVivasEnLaMismaMesaSonImposibles() {
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        presentar(e, new BigDecimal("350000"));

        assertThrows(ConflictoException.class, () -> presentar(e, new BigDecimal("355000")));
        assertEquals(1, ofertasEnEstado(e.oportunidad(), Oferta.PRESENTADA));
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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();

        OfertaService.FichaOferta primera = presentar(e, new BigDecimal("340000"));
        bancoC4().rechazar(e, primera, "Muy por debajo.");

        assertEquals(Oferta.RECHAZADA, estadoDeLaOferta(primera.id()));
        assertEquals(OportunidadComercial.ABIERTA, estadoDeLaMesa(e.oportunidad()),
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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        presentar(e, new BigDecimal("340000"));

        assertThrows(ReglaNegocioException.class,
                () -> bancoC4().noContinuidad(e, null, null),
                "sin motivo no se registra una caída: el análisis de por qué se pierden las "
                        + "ventas es justamente para lo que existe");
        assertThrows(ReglaNegocioException.class,
                () -> bancoC4().noContinuidad(e, "Z", null),
                "el vocabulario es cerrado");

        // 'F' -- no consiguió financiamiento -- entró con V90 y NO es "precio":
        // el precio estaba acordado.
        OportunidadService.FichaOportunidad caida = bancoC4().noContinuidad(e, "F", "El banco no aprobó el crédito.");

        assertEquals(OportunidadComercial.NO_CONTINUA, caida.estado());
        assertEquals(MotivoNoContinuidad.rotuloDe("F"), caida.motivoCierre(),
                "el cable guarda la DESCRIPCIÓN, y sale del único sitio donde vive el vocabulario");
        assertEquals("F", razonDeLaCaida(e.oportunidad()));
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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        Actor otro = e.equipo().colega();

        assertThrows(AccesoNoAutorizadoException.class,
                () -> registrarOfertaRevisada(e, new BigDecimal("350000"), "USD", Oferta.LA_PRESENTA_EL_INTERESADO, null, LocalDate.now(), otro));

        assertEquals(0, ofertasDeLaMesa(e.oportunidad()),
                "un 403 no puede dejar media escritura detrás");
    }

    /**
     * <b>El gobierno del tenant no opera.</b> No obtiene lectura ni escritura
     * de la mesa comercial por administrar la organización.
     */
    @Test
    @DisplayName("C1/R19: el TENANT_ADMIN no lee ni negocia la mesa comercial")
    void elGobiernoDelTenantNoNegocia() {
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        presentar(e, new BigDecimal("350000"));
        Actor gobierno = sesiones().gobiernoDe(e.agente().idOrganizacion());
        assertThrows(AccesoNoAutorizadoException.class,()->ofertas.listar(e.oportunidad(),gobierno));
        assertThrows(AccesoNoAutorizadoException.class,()->ofertas.capacidades(e.oportunidad(),gobierno));

        assertThrows(AccesoNoAutorizadoException.class, () -> registrarOfertaRevisada(e, new BigDecimal("360000"), "USD", Oferta.LA_PRESENTA_EL_PROPIETARIO, null, LocalDate.now(), gobierno));
    }

    /**
     * La mesa con no continuidad conserva su negativa mediante la puerta de
     * formalización; no existe otro comando de cierre que eluda ese contexto.
     */
    @Test
    @DisplayName("una mesa caída no se cierra por una confirmación contractual paralela")
    void mesaCaidaNoSeCierraPorUnaConfirmacionParalela() {
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        bancoC4().aceptar(e, oferta, null);

        var listo=prepararCierreConHito(e,oferta,null,null);

        // La mesa se cae después de haber aceptado -- pasa: el banco dice que no.
        bancoC4().noContinuidad(e, "F", "El banco no aprobó.");

        exigirConfirmacionDenegada(listo);
        assertEquals(0,compraventasDelEncargo(e.encargo()),"La mesa caída no se cierra mediante un productor paralelo.");
    }

    // ==================================================================
    // 6. El cierre de venta y sus límites
    // ==================================================================

    /** Un encargo de ALQUILER no se cierra con una compraventa. */
    @Test
    @DisplayName("el cierre de venta rechaza un encargo de alquiler")
    void unAlquilerNoSeCierraConUnaCompraventa() {
        ActividadNegociacionDePrueba.Caso e = unaMesaDeAlquiler();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("2800"), "PEN");
        bancoC4().aceptar(e, oferta, null);

        var c=bancoC5().desde(e);var exp=bancoC5().aprobar(c,bancoC5().presentar(c,true),bancoC5().alquiler(c,new BigDecimal("2800"),"PEN"));
        ReglaNegocioException fallo=assertThrows(ReglaNegocioException.class,()->bancoC5().informar(c,exp,
                com.controllocal.domain.comercial.TipoHitoContractual.ESCRITURA,OrigenesDePrueba.hoy()));
        assertTrue(fallo.getMessage().toLowerCase().contains("tipo de hito"));

        assertEquals(0, compraventasDelEncargo(e.encargo()));
    }

    /**
     * <b>Un alquiler también se negocia</b> (D-N-5). La oferta vive en la mesa
     * igual que en una venta y escribe su hito {@code O} con la operación de SU
     * encargo. El resultado contractual requiere su hito confirmado competente;
     * no se convierte en compraventa.
     */
    @Test
    @DisplayName("una oferta sobre un encargo de alquiler escribe su hito O como ALQUILER")
    void laMesaFuncionaIgualSobreUnEncargoDeAlquiler() {
        ActividadNegociacionDePrueba.Caso e = unaMesaDeAlquiler();

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
        assertEquals(e.encargo(), ((Number) hito.get("id_captacion")).longValue());
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
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        bancoC4().aceptar(e, oferta, null);

        assertThrows(ReglaNegocioException.class, () -> cerrarConHito(e,oferta,new BigDecimal("900000"),null));
        assertEquals(0, compraventasDelEncargo(e.encargo()),
                "una validación que falla no puede dejar el contrato escrito");

        CierreVentaService.FichaCierreVenta cierre = cerrarConHito(e,oferta,new BigDecimal("900000"),"PEN");

        assertEquals(0, new BigDecimal("350000").compareTo(cierre.montoPactado()));
        assertEquals("USD", cierre.moneda());
        assertEquals(0, new BigDecimal("900000").compareTo(cierre.montoDeclarado()));
        assertEquals("PEN", cierre.monedaDeclarada(),
                "lo pactado y lo declarado son dos cifras y pueden ir en dos monedas");
    }

    /** Una oferta viva no se hace pasar por el antecedente aceptado. Una fuente C5 real conserva otra vía. */
    @Test
    @DisplayName("una oferta viva no acredita un acuerdo aceptado ni una fuente final aprobada")
    void noSeCierraConUnaOfertaQueSigueSobreLaMesa() {
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        OfertaService.FichaOferta viva = presentar(e, new BigDecimal("350000"));

        assertThrows(ReglaNegocioException.class, () -> cerrarConHito(e,viva,null,null));
        assertEquals(0, compraventasDelEncargo(e.encargo()));
    }

    /** Una mesa se firma una vez. La segunda es 409, y lo impone la base. */
    @Test
    @DisplayName("un segundo cierre de la misma mesa responde 409")
    void unaMesaSeCierraUnaSolaVez() {
        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        bancoC4().aceptar(e, oferta, null);
        cerrarConHito(e,oferta,null,null);
        exigirConfirmacionDenegada(cierresPreparados.get(e.encargo()));
        assertEquals(1, compraventasDelEncargo(e.encargo()));
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

        ActividadNegociacionDePrueba.Caso e = unaVentaLista();
        OfertaService.FichaOferta oferta = presentar(e, new BigDecimal("350000"));
        bancoC4().aceptar(e, oferta, null);
        cerrarConHito(e,oferta,null,null);

        assertEquals(0, hitos(e.encargo(), null),
                "ningún hito de este encargo puede quedarse sin declararlo");
        assertEquals(legadoAntes, hitosSinEncargoEnTodaLaBase(),
                "una venta completa no puede añadir NI UNA fila huérfana al histórico: "
                        + "las que había siguen siendo las mismas");

        // Y los dos que nacen de una mesa dicen ADEMÁS de qué vuelta salieron.
        Integer sinOferta = jdbc.queryForObject("""
                select count(*) from precio_propiedad
                 where id_captacion = ? and hito in ('O', 'C') and id_oferta is null
                """, Integer.class, e.encargo());
        assertEquals(0, sinOferta, "un hito O o C sin id_oferta no dice de qué negociación salió");

        // Mientras que U y P no llevan ninguna, y eso es la respuesta correcta:
        // no nacen de ninguna mesa.
        Integer conOfertaIndebida = jdbc.queryForObject("""
                select count(*) from precio_propiedad
                 where id_captacion = ? and hito in ('U', 'P') and id_oferta is not null
                """, Integer.class, e.encargo());
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
        ActividadNegociacionDePrueba.Caso uno = unaVentaLista();
        ActividadNegociacionDePrueba.Caso dos = unaVentaLista();

        assertThrows(RuntimeException.class, () -> jdbc.update("""
                insert into oferta (organizacion_id, id_oportunidad, id_captacion, ronda,
                                    presentada_por, monto, moneda, estado, fecha,
                                    id_rol_registra, origen)
                values (?, ?, ?, 1, 'I', 100000, 'USD', 'P', current_date, ?, 'SPA')
                """, uno.agente().idOrganizacion(), uno.oportunidad(), dos.encargo(),
                uno.agente().idRolOperativo()));

        assertEquals(0, ofertasDeLaMesa(uno.oportunidad()));
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    /** Lo que el encargo autoriza. Es también la referencia cuando no hay anuncio. */
    private static final BigDecimal IMPORTE_DEL_ENCARGO = new BigDecimal("400000");

    @Autowired PropietarioService propietarios;
    @Autowired ActuacionesService actuaciones;
    @Autowired Documentos documentos;
    @Autowired MotorDeCaptura captura;
    @Autowired org.springframework.context.ApplicationContext contextoC5;
    private ActividadNegociacionDePrueba fixtureC4;
    private FormalizacionesDePrueba fixtureC5;
    private final java.util.Map<Long,FormalizacionesDePrueba.Caso> casosDeCierre=new java.util.HashMap<>();
    private final java.util.Map<Long,com.controllocal.service.formalizacion.FormalizacionService.Expediente> expedientesDeCierre=new java.util.HashMap<>();
    private final java.util.Map<Long,FormalizacionesDePrueba.HitoRevisado> cierresPreparados=new java.util.HashMap<>();

    private FormalizacionesDePrueba bancoC5(){if(fixtureC5==null)fixtureC5=new FormalizacionesDePrueba(contextoC5);return fixtureC5;}
    private FormalizacionesDePrueba.HitoRevisado prepararCierreConHito(ActividadNegociacionDePrueba.Caso e,OfertaService.FichaOferta oferta,
            BigDecimal declarado,String monedaDeclarada){
        var aceptado=ofertas.consultarAcuerdoAceptado(e.oportunidad(),null,e.agente())
                .orElseThrow(()->new ReglaNegocioException("Una oferta viva no es el acuerdo aceptado que este escenario declara."));
        if(aceptado.oferta()!=oferta.id())throw new ReglaNegocioException("La oferta no corresponde al acuerdo aceptado de esta mesa.");
        var c=casosDeCierre.computeIfAbsent(e.encargo(),id->bancoC5().desde(e));
        var importe=aceptado.propuesta().importe();
        var montoDeclarado=declarado==null?null:new OfertaService.Dinero(declarado,monedaDeclarada,null,null);
        // Elección explícita del escenario: el broker revisará la cifra conocida de la aceptación, sin ajuste.
        var finalDocumentado=bancoC5().venta(c,importe.importe(),importe.moneda(),montoDeclarado);
        var expediente=expedientesDeCierre.get(e.encargo());
        if(expediente==null){expediente=bancoC5().presentar(c,true);expedientesDeCierre.put(e.encargo(),expediente);}
        expediente=bancoC5().aprobar(c,expediente,finalDocumentado);expedientesDeCierre.put(e.encargo(),expediente);
        var hito=bancoC5().informar(c,expediente,com.controllocal.domain.comercial.TipoHitoContractual.ESCRITURA,OrigenesDePrueba.hoy());
        var listo=new FormalizacionesDePrueba.HitoRevisado(c,expediente,hito,finalDocumentado);cierresPreparados.put(e.encargo(),listo);return listo;
    }
    private CierreVentaService.FichaCierreVenta cerrarConHito(ActividadNegociacionDePrueba.Caso e,OfertaService.FichaOferta oferta,
            BigDecimal declarado,String monedaDeclarada){
        var listo=prepararCierreConHito(e,oferta,declarado,monedaDeclarada);var confirmado=bancoC5().confirmar(listo);
        cierresPreparados.put(e.encargo(),new FormalizacionesDePrueba.HitoRevisado(listo.caso(),listo.expediente(),confirmado,listo.acuerdo()));
        return cierres.obtenerDelEncargo(e.encargo(),e.agente());
    }
    private void exigirConfirmacionDenegada(FormalizacionesDePrueba.HitoRevisado r){
        try{
            var p=bancoC5().prepararConfirmacion(r);var actor=r.caso().broker();var sid=bancoC5().sesiones.de(actor);
            var confirmacion=bancoC5().humanas.revisar(p,actor,sid);
            var resultado=actuaciones.confirmar(actor,p.referencia(),sid,confirmacion,Procedencia.deLaPantalla());
            assertEquals("RECHAZADO",resultado.estado(),"La negativa debe ser comercial; un fallo técnico no satisface la prueba.");
        }catch(com.controllocal.service.excepcion.ConflictoException|ReglaNegocioException negativa){
            assertTrue(negativa.getMessage()!=null&&!negativa.getMessage().isBlank());
        }
    }

    private synchronized ActividadNegociacionDePrueba bancoC4() {
        if(fixtureC4==null)fixtureC4=new ActividadNegociacionDePrueba(jdbc,propiedades,ciclo,
                propietarios,clientes,oportunidades,ofertas,actuaciones,documentos,
                new OrigenesDePrueba(jdbc,gestor,captura,contextos),sesiones(),gestor, archivosFirmaTest, almacenFirmaTest);
        return fixtureC4;
    }

    /** El cierre C5 positivo conserva un Encargo habilitado mediante revisión C2 real. */
    private ActividadNegociacionDePrueba.Caso unaVentaLista() {
        return bancoC4().mesa("VENTA",IMPORTE_DEL_ENCARGO,"USD",true);
    }
    private ActividadNegociacionDePrueba.Caso unaMesaDeAlquiler() {
        return bancoC4().mesa("ALQUILER",new BigDecimal("3000"),"PEN",true);
    }
    private OfertaService.FichaOferta presentar(ActividadNegociacionDePrueba.Caso e,BigDecimal monto) {
        return bancoC4().presentar(e,monto);
    }
    private OfertaService.FichaOferta presentar(ActividadNegociacionDePrueba.Caso e,BigDecimal monto,String moneda) {
        return registrarOfertaRevisada(e,monto,moneda,"I",null,OrigenesDePrueba.hoy(),e.agente());
    }
    private OfertaService.FichaOferta registrarOfertaRevisada(ActividadNegociacionDePrueba.Caso e,
            BigDecimal monto,String moneda,String lado,String condiciones,LocalDate fecha,Actor actor) {
        var observado=bancoC4().registro(e,monto,lado,null,null);
        var datos=new DatosRegistroOferta(observado.oportunidad(),observado.versionOportunidad(),observado.versionMesa(),
                observado.proponente(),new Dinero(monto,moneda,observado.importe().base(),observado.importe().periodo()),
                observado.objeto(),condiciones,fecha,"Manifestación explícita TEST C4",null,null);
        return bancoC4().presentar(e,datos,actor);
    }

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
