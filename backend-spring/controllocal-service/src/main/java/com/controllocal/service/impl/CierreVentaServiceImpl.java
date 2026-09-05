package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoCompraventa;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoCompraventaRepository;
import com.controllocal.persistence.repositorio.OfertaRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.CierreVentaService;
import com.controllocal.service.TareaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * El cierre de una VENTA (WP-A, D-N-3): la oferta aceptada se convierte en
 * contrato de compraventa y con el se cierra el encargo.
 *
 * <h2>Quien firma, y por que es el AGENTE</h2>
 * La misma banda que {@code POST /contratos}, que es el gemelo de este caso de
 * uso por el lado del alquiler: <b>el broker decide, el agente registra</b>, y
 * un cierre es el registro de un hecho consumado, no una decision de banda. La
 * autoridad concreta es {@code AutoridadDePropiedad.exigirEdicionDelEncargo}:
 * el agente de ESE encargo. Responder por la propiedad no basta —quien negocio
 * es quien cierra— y el gobierno del tenant no lo hereda.
 *
 * <h2>Por que no exige ademas la autoridad de la PROPIEDAD</h2>
 * Escribe {@code disponibilidad_comercial} para sacar el inmueble del mercado,
 * y aun asi no llama a {@code exigirEdicion}. Es el mismo motivo por el que
 * {@code ContratoServiceImpl#cerrarLocal} tampoco lo hace, con una diferencia
 * que conviene decir: alli el ejecutante es el BROKER, que nunca es responsable
 * de una propiedad; aqui es el AGENTE del encargo, que puede serlo o no.
 * Exigirla haria imposible cerrar la venta de un inmueble cuyo responsable es
 * otro agente —o que esta FALTANTE—, y eso no es una autoridad: es un bloqueo.
 * Lo que se escribe no es una declaracion sobre el inmueble sino la
 * <b>consecuencia</b> de un hecho ya decidido.
 */
@Service
public class CierreVentaServiceImpl implements CierreVentaService {

    /** Codigo de {@code captacion.motivo_cierre}: se vendio. */
    static final String CIERRE_POR_VENTA = "V";

    /** Estados de la mesa que todavia admiten cerrarse con exito. */
    private static final java.util.Set<String> ADMITEN_CIERRE = java.util.Set.of(
            OportunidadComercial.ABIERTA, OportunidadComercial.SOLICITUD_CREADA);

    private final CaptacionRepository captaciones;
    private final OportunidadComercialRepository oportunidades;
    private final OfertaRepository ofertas;
    private final ContratoCompraventaRepository compraventas;
    private final PrecioPropiedadRepository precios;
    private final PropiedadRepository propiedades;
    private final PublicacionRepository publicaciones;
    private final SupervisionAgenteRepository supervisiones;
    private final Alcances alcances;
    private final AutoridadDePropiedad autoridad;
    private final Transiciones transiciones;
    private final TareaService tareas;
    private final AlertaService alertas;

    public CierreVentaServiceImpl(CaptacionRepository captaciones,
                                  OportunidadComercialRepository oportunidades,
                                  OfertaRepository ofertas,
                                  ContratoCompraventaRepository compraventas,
                                  PrecioPropiedadRepository precios,
                                  PropiedadRepository propiedades,
                                  PublicacionRepository publicaciones,
                                  SupervisionAgenteRepository supervisiones,
                                  Alcances alcances, AutoridadDePropiedad autoridad,
                                  Transiciones transiciones, TareaService tareas,
                                  AlertaService alertas) {
        this.captaciones = captaciones;
        this.oportunidades = oportunidades;
        this.ofertas = ofertas;
        this.compraventas = compraventas;
        this.precios = precios;
        this.propiedades = propiedades;
        this.publicaciones = publicaciones;
        this.supervisiones = supervisiones;
        this.alcances = alcances;
        this.autoridad = autoridad;
        this.transiciones = transiciones;
        this.tareas = tareas;
        this.alertas = alertas;
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCierreVenta obtenerDelEncargo(long idCaptacion, Actor actor) {
        Captacion encargo = cargarConAcceso(idCaptacion, actor);
        ContratoCompraventa contrato = compraventas
                .delEncargo(encargo.getOrganizacionId(), idCaptacion).stream()
                .findFirst()
                .orElseThrow(() -> new NoEncontradoException("Cierre de venta"));
        return ficha(contrato, encargo,
                oportunidades.buscarFicha(encargo.getOrganizacionId(), contrato.getIdOportunidad())
                        .orElse(null),
                ofertas.buscarFicha(encargo.getOrganizacionId(), contrato.getIdOferta())
                        .orElse(null));
    }

    @Override
    @Transactional
    public FichaCierreVenta cerrar(long idCaptacion, DatosCierreVenta datos, Actor actor) {
        if (datos == null || datos.idOferta() == null || datos.idOferta() <= 0) {
            throw new ReglaNegocioException(
                    "Indica la oferta aceptada con la que se cierra la venta: sin ella no se "
                            + "sabe que cifra se acordo.");
        }
        Captacion encargo = cargarConAcceso(idCaptacion, actor);
        // La autoridad del ENCARGO, y despues de la carga para conservar el 404
        // de otra corredora: un id ajeno no puede empezar a responder 403.
        autoridad.exigirEdicionDelEncargo(actor, encargo);

        if (OperacionInmobiliaria.VENTA != encargo.operacion()) {
            throw new ReglaNegocioException(
                    "El encargo " + encargo.getCodigoCaptacion() + " es de alquiler. El cierre de "
                            + "un alquiler es su contrato de alquiler, no una compraventa.");
        }
        if (!Captacion.ACTIVA.equals(encargo.estadoActual())) {
            throw new ReglaNegocioException("El encargo tiene que estar ACTIVO para cerrarlo.");
        }

        Oferta oferta = ofertas.buscarFicha(encargo.getOrganizacionId(), datos.idOferta())
                .orElseThrow(() -> new NoEncontradoException("Oferta"));
        if (!Objects.equals(oferta.getIdCaptacion(), encargo.getId())) {
            // Para este encargo esa oferta no existe. 404 y no 403: decir que
            // existe en otro encargo ya seria informacion.
            throw new NoEncontradoException("Oferta");
        }
        if (!oferta.estaAceptada()) {
            throw new ReglaNegocioException(
                    "Solo se cierra con una oferta ACEPTADA; la de la ronda " + oferta.getRonda()
                            + " esta " + Oferta.rotuloDelEstado(oferta.estadoActual())
                                    .toLowerCase(Locale.ROOT) + ".");
        }

        OportunidadComercial mesa = oportunidades
                .buscarFicha(encargo.getOrganizacionId(), oferta.getIdOportunidad())
                .orElseThrow(() -> new NoEncontradoException("Oportunidad"));
        if (!ADMITEN_CIERRE.contains(mesa.estadoActual())) {
            throw new ReglaNegocioException(
                    "La oportunidad ya esta cerrada; no admite un cierre de venta.");
        }
        if (compraventas.deLaMesa(encargo.getOrganizacionId(), mesa.getId()).isPresent()) {
            throw new ConflictoException("Esta negociacion ya tiene un cierre de venta registrado.");
        }

        BigDecimal pactado = pactadoDe(datos, oferta);
        String moneda = monedaDe(datos, oferta);
        LocalDate firma = fechaDeFirma(datos.fechaFirma());
        String motivo = "Venta cerrada con la oferta de la ronda " + oferta.getRonda() + ".";

        // 1) El contrato de compraventa.
        ContratoCompraventa contrato = new ContratoCompraventa();
        contrato.setOrganizacionId(encargo.getOrganizacionId());
        contrato.setIdOportunidad(mesa.getId());
        contrato.setIdCaptacion(encargo.getId());
        contrato.setIdPropiedad(encargo.getPropiedad().getId());
        contrato.setIdOferta(oferta.getId());
        DetalleCliente comprador = mesa.getCliente();
        contrato.setIdRolCliente(comprador == null ? null : comprador.getId());
        DetalleAgente agente = encargo.getAgente();
        contrato.setIdRolAgenteCierre(agente == null ? null : agente.getId());
        contrato.setIdRolBrokerCierre(brokerDe(encargo.getOrganizacionId(),
                agente == null ? null : agente.getId()));
        contrato.setMontoPactado(pactado);
        contrato.setMoneda(moneda);
        contrato.declararEnEscritura(datos.montoDeclarado(),
                monedaDeclaradaDe(datos.montoDeclarado(), datos.monedaDeclarada()));
        contrato.setFechaFirma(firma);
        contrato.setObservaciones(texto(datos.observaciones()));
        ContratoCompraventa guardado;
        try {
            guardado = compraventas.saveAndFlush(contrato);
        } catch (DataIntegrityViolationException carrera) {
            // Lo cortan uq_compraventa_oportunidad y uq_compraventa_oferta: una
            // oferta se firma una sola vez.
            throw new ConflictoException("Esta negociacion ya tiene un cierre de venta registrado.");
        }

        // 2) El hito C: el ultimo de la serie de ESTE encargo, atado ademas a la
        //    vuelta que lo produjo. Sin id_oferta, dos negociaciones sobre la
        //    misma propiedad mezclarian sus cierres en una sola linea.
        precios.save(PrecioPropiedad.hito(encargo.getOrganizacionId(),
                        encargo.getPropiedad().getId(), OperacionInmobiliaria.VENTA,
                        PrecioPropiedad.HITO_CIERRE, moneda, pactado, firma)
                .delEncargo(encargo.getId())
                .deLaOferta(oferta.getId()));

        // 3) La mesa se cierra como EXITOSA.
        mesa.marcarCierreExitoso();
        transiciones.aplicar(mesa, mesa.getId(), OportunidadComercial.FINALIZADA_EXITOSA,
                actor, motivo, firma);
        oportunidades.save(mesa);

        // 4) El encargo cumplio su objetivo. El motivo es 'V' —vendido— y no el
        //    'A' del alquiler ni el 'M' del cierre manual del broker: los tres
        //    son cierres y ninguno es el mismo hecho.
        transiciones.aplicar(encargo, encargo.getId(), Captacion.CERRADA, actor, motivo, firma);
        encargo.cerrar(firma, CIERRE_POR_VENTA, motivo);
        captaciones.save(encargo);

        // 5) El inmueble sale del mercado y sus anuncios se cierran.
        retirarDelMercado(encargo, actor, motivo);

        // 6) Las tareas abiertas de la operacion se dan por hechas y se avisa.
        tareas.resolverDeEntidad(OportunidadComercial.ENTIDAD_TIPO, mesa.getId(), actor);
        tareas.resolverDeEntidad(encargo.entidadTipo(), encargo.getId(), actor);
        tareas.resolverDeEntidad("INMUEBLE", encargo.getPropiedad().getId(), actor);
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.OPORTUNIDAD_CERRADA, Alerta.INFO,
                OportunidadComercial.ENTIDAD_TIPO, mesa.getId(),
                agente == null ? null : agente.getId(),
                "Se cerro la venta de la oportunidad " + mesa.getCodigoOportunidad() + "."), actor);

        return ficha(guardado, encargo, mesa, oferta);
    }

    // ==================================================================
    // Guardas y auxiliares
    // ==================================================================

    /**
     * Tenant delante —404 de otra corredora— y alcance sobre el agente del
     * encargo despues. Es el mismo orden y el mismo predicado que
     * {@code CaptacionServiceImpl.cargarConAcceso}: dos formas de alcanzar el
     * mismo encargo se separarian.
     */
    private Captacion cargarConAcceso(long idCaptacion, Actor actor) {
        Captacion encargo = captaciones.buscarFicha(actor.idOrganizacion(), idCaptacion)
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
        if (encargo.getAgente() == null || !alcances.alcanza(actor, encargo.getAgente().getId())) {
            throw new AccesoNoAutorizadoException();
        }
        return encargo;
    }

    /**
     * <b>El inmueble sale del mercado</b>, y con la palabra que el vocabulario
     * de hoy sabe decir.
     *
     * <p>{@code DisponibilidadComercial} tiene {@code DISPONIBLE},
     * {@code RESERVADO}, {@code ALQUILADO} y {@code RETIRADO}. <b>No tiene
     * VENDIDO</b>, y anadirlo cruza dos autoridades que este corte no gobierna
     * —el catalogo de codigos de estado y el de productores—. De las opciones
     * reales:
     * <ul>
     *   <li>dejarlo {@code DISPONIBLE} seria <b>falso</b>: es una afirmacion
     *       sobre el presente que la venta desmiente;</li>
     *   <li>{@code ALQUILADO} seria falso y ademas contaminaria la estadistica
     *       de rentas;</li>
     *   <li>{@code RETIRADO} —«retirado del mercado»— es <b>cierto y mas
     *       debil</b>: no dice que se vendio, y no lo pretende. El por que si
     *       queda escrito, en {@code contrato_compraventa}, en el hito {@code C}
     *       y en {@code captacion.motivo_cierre = 'V'}.
     * </ul>
     * Se elige la afirmacion verdadera aunque sea incompleta, no la completa
     * inventada. Que {@code VENDIDO} entre en el vocabulario es una decision
     * pendiente y esta escrita como tal.
     */
    private void retirarDelMercado(Captacion encargo, Actor actor, String motivo) {
        Propiedad propiedad = encargo.getPropiedad();
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }
        transiciones.aplicarDisponibilidad(propiedad, propiedad.getId(),
                DisponibilidadComercial.RETIRADO, actor, motivo);
        propiedades.save(propiedad);

        for (Publicacion publicacion
                : publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(propiedad.getId())) {
            if (!Objects.equals(publicacion.getIdEncargo(), encargo.getId())) {
                // Los anuncios del OTRO encargo —el de alquiler, si lo hay— no
                // se tocan: vender no cancela un encargo de alquiler vivo, y
                // cerrarlos aqui seria decidir sobre un trato ajeno.
                continue;
            }
            if (!Publicacion.ESTADO_CERRADO.equals(publicacion.getEstado())) {
                publicacion.setEstado(Publicacion.ESTADO_CERRADO);
                publicacion.setFechaBaja(OffsetDateTime.now());
                publicaciones.save(publicacion);
            }
        }
    }

    private Long brokerDe(Long idOrganizacion, Long idRolAgente) {
        if (idOrganizacion == null || idRolAgente == null) {
            return null;
        }
        return supervisiones.buscarActivaPorAgente(idOrganizacion, idRolAgente)
                .map(SupervisionAgente::getIdRolBroker)
                .orElse(null);
    }

    /**
     * El importe pactado. Por defecto el de la oferta aceptada —que es lo que se
     * acordo— y solo se aparta de el si el cierre lo declara: un ajuste de
     * ultima hora es un hecho, deducirlo no.
     */
    private static BigDecimal pactadoDe(DatosCierreVenta datos, Oferta oferta) {
        if (datos.montoPactado() == null) {
            return oferta.getMonto();
        }
        if (datos.montoPactado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException("El importe pactado tiene que ser mayor que cero.");
        }
        return datos.montoPactado();
    }

    private static String monedaDe(DatosCierreVenta datos, Oferta oferta) {
        if (datos.moneda() == null || datos.moneda().isBlank()) {
            return oferta.getMoneda();
        }
        String limpia = datos.moneda().trim().toUpperCase(Locale.ROOT);
        if (!PrecioPropiedad.MONEDAS.contains(limpia)) {
            throw new ReglaNegocioException(
                    "Moneda no valida para el cierre: " + PrecioPropiedad.MONEDAS + ".");
        }
        return limpia;
    }

    /**
     * La moneda de lo declarado en la escritura. <b>Sin defecto</b>: si se
     * declara un importe hay que decir en que moneda, porque en este mercado se
     * pacta en dolares y se escritura en soles con frecuencia, y suponer que
     * coinciden convertiria una escritura de 700.000 soles en 700.000 dolares.
     */
    private static String monedaDeclaradaDe(BigDecimal montoDeclarado, String moneda) {
        if (montoDeclarado == null) {
            return null;
        }
        String limpia = moneda == null ? null : moneda.trim().toUpperCase(Locale.ROOT);
        if (limpia == null || !PrecioPropiedad.MONEDAS.contains(limpia)) {
            throw new ReglaNegocioException(
                    "Declara la moneda del importe de la escritura: " + PrecioPropiedad.MONEDAS
                            + ". No se supone la del importe pactado.");
        }
        return limpia;
    }

    private static LocalDate fechaDeFirma(LocalDate fecha) {
        LocalDate efectiva = fecha == null ? LocalDate.now() : fecha;
        if (efectiva.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha de firma no puede ser futura.");
        }
        return efectiva;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static FichaCierreVenta ficha(ContratoCompraventa c, Captacion encargo,
                                          OportunidadComercial mesa, Oferta oferta) {
        Propiedad propiedad = encargo == null ? null : encargo.getPropiedad();
        DetalleCliente cliente = mesa == null ? null : mesa.getCliente();
        return new FichaCierreVenta(c.getId(), c.getIdCaptacion(),
                encargo == null ? null : encargo.getCodigoCaptacion(),
                c.getIdOportunidad(),
                mesa == null ? null : mesa.getCodigoOportunidad(),
                c.getIdOferta(),
                oferta == null ? null : oferta.getRonda(),
                c.getIdPropiedad(),
                propiedad == null ? null : propiedad.getDireccion(),
                c.getIdRolCliente(),
                nombre(cliente == null ? null : cliente.getRol()),
                c.getIdRolAgenteCierre(), c.getIdRolBrokerCierre(),
                c.getMontoPactado(), c.getMoneda(),
                c.getMontoDeclarado(), c.getMonedaDeclarada(),
                c.getFechaFirma(), c.getObservaciones(),
                Fechas.local(c.getFechaCreacion()));
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null
                ? null : rol.getPersona().getNombresORazonSocial();
    }
}
