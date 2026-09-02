package com.controllocal.service.impl;

import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService.DatosPublicacion;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E0.2 — la renta que el mercado VE queda en el historico.
 *
 * <p>Antes, {@code renta_publicada} mutaba en su sitio desde tres productores y
 * el valor anterior se perdia. El hito {@code U} no cubre esto: ese es el precio
 * que el propietario autoriza en privado, y la elasticidad se mide contra el que
 * el mercado vio.
 */
class PublicacionServiceImplTest {

    private final PublicacionRepository publicaciones = mock(PublicacionRepository.class);
    private final PrecioPropiedadRepository precios = mock(PrecioPropiedadRepository.class);

    private final CaptacionRepository encargos = mock(CaptacionRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);

    /**
     * Un catalogo que no encuentra nada faltante: estas pruebas blindan las
     * reglas del ANUNCIO --moneda obligatoria, version incremental, fecha de
     * baja--, no la completitud de la ficha, que tiene la suya contra PostgreSQL.
     */
    private final AtributosGobernados gobierno = mock(AtributosGobernados.class);

    /** Y su gemelo del otro sujeto, por la misma razon. */
    private final com.controllocal.service.soporte.AtributosDeEncargo condiciones =
            mock(com.controllocal.service.soporte.AtributosDeEncargo.class);

    /**
     * <b>La autoridad va de verdad</b> (P0-4). Publicar escribe un hito `P` en
     * la serie economica del encargo, asi que estas pruebas tienen que correr
     * con la regla puesta: con un mock permisivo seguirian verdes justo si
     * alguien la quitara.
     */
    private final com.controllocal.service.soporte.AutoridadDePropiedad autoridad =
            new com.controllocal.service.soporte.AutoridadDePropiedad(
                    mock(com.controllocal.persistence.repositorio.DetalleAgenteRepository.class),
                    mock(com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository.class),
                    new com.controllocal.service.soporte.Alcances(mock(
                            com.controllocal.persistence.repositorio.SupervisionAgenteRepository.class)),
                    // Y la elegibilidad del destino (D-P0-7), tambien de verdad.
                    // Publicar no traspasa nada, asi que aqui no se ejercita.
                    new com.controllocal.service.soporte.ElegibilidadDeResponsable(mock(
                            com.controllocal.persistence.repositorio.DetalleAgenteRepository.class)),
                    // El repositorio del compare-and-set del responsable (D-P0-9),
                    // mockeado: publicar no mueve la autoridad. Que el CAS haga lo
                    // que dice solo se puede probar con dos transacciones reales
                    // (CausalidadDelTraspasoIntegrationTest).
                    mock(com.controllocal.persistence.repositorio.PropiedadRepository.class));

    private final PublicacionServiceImpl service = new PublicacionServiceImpl(
            publicaciones, precios, encargos, propiedades, gobierno, condiciones, autoridad);

    private static final long ORG = 1L;
    private static final long PROPIEDAD = 7L;
    private static final long ENCARGO = 55L;

    private static final long ROL_AGENTE = 30L;

    /** El agente que lleva el encargo de estas pruebas (P0-4). */
    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");

    /** Otro agente del mismo tenant: el que NO puede tocar este encargo. */
    private final Actor ajeno = new Actor(ORG, 4L, 44L, "AGENTE");

    @org.junit.jupiter.api.BeforeEach
    void elCatalogoNoEncuentraNadaFaltante() {
        org.mockito.Mockito.lenient()
                .when(gobierno.faltantesDePropiedadParaPublicar(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        // El gate de publicacion necesita la propiedad para preguntarle al
        // catalogo por su tipo: sin ella no puede saber que exige.
        com.controllocal.domain.inmueble.Propiedad propiedad =
                new com.controllocal.domain.inmueble.Propiedad();
        propiedad.setTipoInmueble(com.controllocal.domain.inmueble.Propiedad.TIPO_LOCAL);
        org.mockito.Mockito.lenient()
                .when(propiedades.findByOrganizacionIdAndId(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.Optional.of(propiedad));
        // Y el otro sujeto tampoco echa nada en falta. Estan los dos porque el
        // gate pregunta a los dos: si esta prueba solo apagara uno, el dia que
        // alguien retirara la pregunta al encargo seguiria pasando en verde.
        org.mockito.Mockito.lenient()
                .when(condiciones.faltantesDeEncargoParaPublicar(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
    }

    /**
     * El encargo del que cuelgan estas publicaciones.
     *
     * <p>Desde V70 el hito de precio publicado toma de aqui su operacion, en vez
     * de suponer ALQUILER. Sin encargo resuelto <b>no se escribe hito</b>: es
     * mejor no tener el dato que tenerlo colgando de una operacion inventada, y
     * por eso el mock tiene que devolverlo para que estas pruebas vean algo.
     */
    @BeforeEach
    void elEncargoExiste() {
        Captacion encargo = encargoVivo();
        when(encargos.findById(ENCARGO)).thenReturn(Optional.of(encargo));
        // La MISMA fila, cargada con el candado (F2.10): las tres puertas que
        // escriben la serie del encargo -crear en el encargo, editar el anuncio
        // y cambiar su estado- la piden asi, para que la autoridad se compruebe
        // sobre el agente que seguira siendo verdad al escribir el hito.
        when(encargos.bloquearParaEscritura(ORG, ENCARGO)).thenReturn(Optional.of(encargo));
        // Y tambien por propiedad: `operacionDe` resuelve asi el encargo de una
        // publicacion que no lo lleva escrito -- los anuncios anteriores a V70.
        when(encargos.encargosDe(ORG, PROPIEDAD)).thenReturn(List.of(encargo));
    }

    private static Captacion encargoVivo() {
        Propiedad propiedad = new Propiedad();
        // El id lo genera JPA: en una prueba de unidad se inyecta, que es lo que
        // hace el resto de tests de este modulo (ver AgenteServiceImplTest).
        ReflectionTestUtils.setField(propiedad, "id", PROPIEDAD);
        Captacion encargo = new Captacion();
        ReflectionTestUtils.setField(encargo, "id", ENCARGO);
        encargo.setOrganizacionId(ORG);
        encargo.setCodigoCaptacion("CAP-0001");
        encargo.setMotivoOperacion(OperacionInmobiliaria.ALQUILER.codigo());
        encargo.setPropiedad(propiedad);
        // El agente del encargo, que en la base es NOT NULL y aqui faltaba.
        // Desde P0-4 no es decorado: publicar escribe un hito en la serie
        // economica de ESTE encargo, asi que lo hace SU agente. Con el encargo
        // sin agente, la autoridad -que aqui va de verdad, no mockeada- deniega,
        // y es lo correcto: un encargo de nadie no lo publica nadie.
        com.controllocal.domain.persona.DetalleAgente suAgente =
                new com.controllocal.domain.persona.DetalleAgente();
        ReflectionTestUtils.setField(suAgente, "id", ROL_AGENTE);
        encargo.setAgente(suAgente);
        encargo.transicionarA(Captacion.ACTIVA);
        return encargo;
    }

    private static DatosPublicacion datos(BigDecimal renta, String moneda, String estado) {
        return new DatosPublicacion(Publicacion.CANAL_WEB_PROPIA, null, renta, moneda,
                null, null, estado);
    }

    private static Publicacion publicacion(String estado, BigDecimal renta, String moneda) {
        Publicacion p = new Publicacion();
        p.setOrganizacionId(ORG);
        p.setIdPropiedad(PROPIEDAD);
        p.setCanal(Publicacion.CANAL_WEB_PROPIA);
        p.setVersionAnuncio(1);
        p.setImportePublicado(renta);
        p.setIdEncargo(ENCARGO);
        p.setMoneda(moneda);
        p.setEstado(estado);
        p.setFechaPublicacion(OffsetDateTime.now());
        return p;
    }

    private static PrecioPropiedad hitoPublicado(BigDecimal monto, String moneda) {
        PrecioPropiedad hito = new PrecioPropiedad();
        hito.setOrganizacionId(ORG);
        hito.setIdPropiedad(PROPIEDAD);
        hito.setHito(PrecioPropiedad.HITO_PUBLICADO);
        hito.setMonto(monto);
        hito.setMoneda(moneda);
        hito.setFecha(LocalDate.now());
        // Desde V70 la deduplicacion compara tambien el encargo: dos encargos
        // pueden publicar el mismo importe y no son el mismo hecho.
        return hito.delEncargo(ENCARGO);
    }

    private ArgumentCaptor<PrecioPropiedad> hitosGuardados() {
        ArgumentCaptor<PrecioPropiedad> captor = ArgumentCaptor.forClass(PrecioPropiedad.class);
        verify(precios).save(captor.capture());
        return captor;
    }

    @Test
    void publicarUnImporteDejaSuHito() {
        service.crearEnEncargo(ENCARGO, datos(new BigDecimal("5200.00"), "PEN",
                Publicacion.ESTADO_PUBLICADO), agente);

        PrecioPropiedad hito = hitosGuardados().getValue();
        assertEquals(PrecioPropiedad.HITO_PUBLICADO, hito.getHito());
        assertEquals(new BigDecimal("5200.00"), hito.getMonto());
        assertEquals("PEN", hito.getMoneda());
        assertEquals(PROPIEDAD, hito.getIdPropiedad());
        // Atado a SU encargo y con SU operacion. Antes de V70 el hito nacia
        // huerfano y suponiendo ALQUILER: no aparecia en ninguna ficha, porque
        // el historico del encargo y la historia del inmueble filtran por encargo.
        assertEquals(ENCARGO, hito.getIdCaptacion());
        assertEquals(OperacionInmobiliaria.ALQUILER.codigo(), hito.getOperacion());
        // El tenant sale de la publicacion, no del actor: la misma regla que la
        // auditoria de estados.
        assertEquals(ORG, hito.getOrganizacionId());
        assertEquals(LocalDate.now(), hito.getFecha());
    }

    /**
     * <b>No se publica lo que ya no se ofrece.</b> Es una regla de negocio, y por
     * eso vive en el servicio y no en el boton que la pantalla dibuja.
     */
    @Test
    void noSePublicaUnEncargoCerrado() {
        Captacion cerrado = encargoVivo();
        cerrado.transicionarA(Captacion.CERRADA);
        when(encargos.bloquearParaEscritura(ORG, ENCARGO)).thenReturn(Optional.of(cerrado));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.crearEnEncargo(ENCARGO, datos(new BigDecimal("5200.00"), "PEN",
                        Publicacion.ESTADO_PUBLICADO), agente));

        assertTrue(error.getMessage().contains("no esta vigente"), error.getMessage());
        verify(precios, never()).save(any());
    }

    /**
     * <b>Sin encargo resuelto no se escribe hito.</b>
     *
     * <p>Escribirlo suponiendo ALQUILER es lo que hacia antes de V70, y con la
     * venta en el modelo eso mete precios de venta en la serie de alquiler. Es
     * mejor no tener el dato que tenerlo colgando de una operacion inventada.
     *
     * <p><b>Desde P0-4 el anuncio huerfano se rechaza ANTES</b>, y por una razon
     * distinta pero de la misma familia: si no se sabe de que encargo es, no se
     * sabe quien responde por el, y suponerlo es la clase de respuesta que este
     * corte vino a quitar. El efecto observable es el que importaba y no cambia
     * --<b>no se escribe ningun hito</b>-- y ademas ahora se dice por que en vez
     * de guardar en silencio media operacion.
     */
    @Test
    void sinEncargoResueltoNoSeInventaLaOperacion() {
        Publicacion huerfana = publicacion(Publicacion.ESTADO_PUBLICADO,
                new BigDecimal("5200.00"), "PEN");
        huerfana.setIdEncargo(null);
        when(publicaciones.findByOrganizacionIdAndId(ORG, 11L))
                .thenReturn(Optional.of(huerfana));

        com.controllocal.service.excepcion.ReglaNegocioException error = assertThrows(
                com.controllocal.service.excepcion.ReglaNegocioException.class,
                () -> service.actualizar(11L, datos(new BigDecimal("5200.00"), "PEN", null),
                        agente));
        assertTrue(error.getMessage().contains("de que encargo es"),
                "el rechazo tiene que explicar el hueco, no acusar de permisos: "
                        + error.getMessage());

        verify(precios, never()).save(any());
    }

    /**
     * <b>Y el encargo ajeno no se publica</b> (P0-4).
     *
     * <p>Es la puerta que el inventario de las ocho vias no tenia: publicar
     * escribe un hito {@code P} en la serie economica del encargo, asi que hasta
     * V87 cualquier agente del tenant metia una cifra en el historico de otro
     * comprobando unicamente la organizacion.
     */
    @Test
    void publicarElEncargoDeOtroAgenteSeDeniegaYNoDejaHito() {
        assertThrows(com.controllocal.service.excepcion.AccesoNoAutorizadoException.class,
                () -> service.crearEnEncargo(ENCARGO,
                        datos(new BigDecimal("5200.00"), "PEN", Publicacion.ESTADO_PUBLICADO),
                        ajeno));

        verify(precios, never()).save(any());
        verify(publicaciones, never()).save(any());
    }

    /**
     * Un borrador no lo ve nadie. Anotar su renta como "publicada" meteria en la
     * serie precios que nunca existieron para el mercado, y la elasticidad se
     * calcularia contra cifras imaginarias.
     */
    @Test
    void unBorradorNoDejaHitoPorqueElMercadoNoLoVe() {
        service.crearEnEncargo(ENCARGO, datos(new BigDecimal("5200.00"), "PEN",
                Publicacion.ESTADO_BORRADOR), agente);

        verify(precios, never()).save(any());
    }

    /** El instante en que un borrador se publica ES la primera vez que se ve. */
    @Test
    void publicarUnBorradorDejaElHitoEnEseInstante() {
        when(publicaciones.findByOrganizacionIdAndId(ORG, 11L))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_BORRADOR,
                        new BigDecimal("4800.00"), "USD")));

        service.cambiarEstado(11L, Publicacion.ESTADO_PUBLICADO, agente);

        PrecioPropiedad hito = hitosGuardados().getValue();
        assertEquals(new BigDecimal("4800.00"), hito.getMonto());
        assertEquals("USD", hito.getMoneda());
    }

    /**
     * El caso que obliga a deduplicar: {@code actualizar} pasa por
     * {@code registrarImportePublicado} en CADA guardado, cambie o no el precio.
     * Sin este filtro, corregir el titulo del anuncio escribiria un hito de renta.
     */
    @Test
    void guardarSinCambioEconomicoNoDuplicaElHito() {
        when(publicaciones.findByOrganizacionIdAndId(ORG, 11L))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));
        when(precios.findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                PROPIEDAD, PrecioPropiedad.HITO_PUBLICADO))
                // Mismo importe con OTRA escala: 5200 y 5200.00 son el mismo
                // precio. Con equals de BigDecimal esto duplicaria el hito.
                .thenReturn(Optional.of(hitoPublicado(new BigDecimal("5200"), "PEN")));

        service.actualizar(11L, datos(new BigDecimal("5200.00"), "PEN", null), agente);

        verify(precios, never()).save(any());
    }

    @Test
    void guardarConRentaNuevaDejaOtroHito() {
        when(publicaciones.findByOrganizacionIdAndId(ORG, 11L))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));
        when(precios.findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                PROPIEDAD, PrecioPropiedad.HITO_PUBLICADO))
                .thenReturn(Optional.of(hitoPublicado(new BigDecimal("5200.00"), "PEN")));

        service.actualizar(11L, datos(new BigDecimal("4900.00"), "PEN", null), agente);

        assertEquals(new BigDecimal("4900.00"), hitosGuardados().getValue().getMonto());
    }

    /**
     * Mismo numero, otra moneda, es otro precio. En una cartera donde el 60 % de
     * las rentas se cotiza en dolares, tratarlo como "sin cambio" arruinaria la
     * serie.
     */
    @Test
    void cambiarSoloLaMonedaCuentaComoRentaNueva() {
        when(publicaciones.findByOrganizacionIdAndId(ORG, 11L))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("1500.00"), "PEN")));
        when(precios.findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                PROPIEDAD, PrecioPropiedad.HITO_PUBLICADO))
                .thenReturn(Optional.of(hitoPublicado(new BigDecimal("1500.00"), "PEN")));

        service.actualizar(11L, datos(new BigDecimal("1500.00"), "USD", null), agente);

        PrecioPropiedad hito = hitosGuardados().getValue();
        assertEquals("USD", hito.getMoneda());
        assertEquals(new BigDecimal("1500.00"), hito.getMonto());
    }

    /**
     * <b>Devolver un anuncio a borrador no deja hito.</b>
     *
     * <p>Lo protegia {@code sincronizar}, con un local en borrador y sin
     * publicacion previa: no creaba nada y no escribia hito. Esa via se retiro
     * --creaba publicaciones sin pasar por {@code exigirPublicable}-- y la mitad
     * que sigue viva es esta: el hito solo se escribe cuando el anuncio esta
     * PUBLICADO, asi que pasarlo a borrador no anota nada en la serie.
     */
    @Test
    void devolverAborradorNoEscribeHito() {
        when(publicaciones.findByOrganizacionIdAndId(ORG, 11L))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));

        service.cambiarEstado(11L, Publicacion.ESTADO_BORRADOR, agente);

        verify(precios, never()).save(any());
    }

    @Test
    void editarLaRentaDeUnAnuncioPublicadoDejaSuHito() {
        when(publicaciones.findByOrganizacionIdAndId(eq(ORG), eq(11L)))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));

        service.actualizar(11L, datos(new BigDecimal("4700.00"), "PEN", null), agente);

        assertEquals(new BigDecimal("4700.00"), hitosGuardados().getValue().getMonto());
    }
}
