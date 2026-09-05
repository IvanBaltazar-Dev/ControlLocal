package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.persistence.repositorio.ContratoCompraventaRepository;
import com.controllocal.persistence.repositorio.OfertaRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * La mesa de negociacion: quien ofrecio, cuanto, contra que y como acabo
 * (WP-A, D-N-1/D-N-2).
 *
 * <h2>Las tres reglas que sostienen la cadena</h2>
 * <ol>
 *   <li><b>Una sola oferta viva por mesa.</b> Mientras hay una presentada, la
 *       siguiente cifra es una CONTRAOFERTA que la supera, no una segunda
 *       oferta paralela. Con dos vivas nadie puede decir a que se respondio.</li>
 *   <li><b>Una sola aceptada por mesa.</b> Dos aceptadas son dos precios de
 *       cierre para la misma negociacion. Lo corta el indice parcial
 *       {@code uq_oferta_aceptada_por_oportunidad}, no la comprobacion previa:
 *       dos peticiones simultaneas pasan las dos la comprobacion.</li>
 *   <li><b>La referencia se congela</b> al presentar (D-N-1) y nunca se
 *       recalcula: un anuncio que baja de precio despues no puede cambiar
 *       retroactivamente el descuento con el que se negocio.</li>
 * </ol>
 *
 * <h2>Y una que NO esta</h2>
 * Rechazar la ultima oferta <b>no</b> cierra la oportunidad. Es deliberado: que
 * el propietario diga que no a una cifra no significa que el interesado se haya
 * ido, y convertir un rechazo en una caida borraria de la historia las
 * negociaciones que siguieron despues de un no.
 */
@Service
public class OfertaServiceImpl implements OfertaService {

    private final OfertaRepository ofertas;
    private final PrecioPropiedadRepository precios;
    private final ContratoCompraventaRepository compraventas;
    /** Quien alcanza la oportunidad. Es la pregunta de LECTURA. */
    private final AccesoOportunidad acceso;
    /** Quien puede ESCRIBIR el trato: el agente de ESE encargo (P0-4). */
    private final AutoridadDePropiedad autoridad;
    private final Transiciones transiciones;

    public OfertaServiceImpl(OfertaRepository ofertas, PrecioPropiedadRepository precios,
                             ContratoCompraventaRepository compraventas,
                             AccesoOportunidad acceso, AutoridadDePropiedad autoridad,
                             Transiciones transiciones) {
        this.ofertas = ofertas;
        this.precios = precios;
        this.compraventas = compraventas;
        this.acceso = acceso;
        this.autoridad = autoridad;
        this.transiciones = transiciones;
    }

    // ==================================================================
    // Lectura
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<FichaOferta> listar(long idOportunidad, Actor actor) {
        OportunidadComercial mesa = acceso.conAcceso(idOportunidad, actor);
        return ofertas.deLaMesa(mesa.getOrganizacionId(), idOportunidad).stream()
                .map(OfertaServiceImpl::ficha)
                .toList();
    }

    /**
     * <b>Las capacidades, con las MISMAS guardas que los comandos.</b>
     *
     * <p>{@code puedeOfertar} y sus hermanas se preguntan con
     * {@link #autoridadDelEncargo}, que envuelve el metodo que despues deniega,
     * en vez de reescribir «soy el agente» aqui: un segundo criterio "solo para
     * pintar" es como se llega a un boton activo que el backend rechaza cuando
     * la persona ya escribio.
     *
     * <p>{@code puedeRegistrarCaida} lleva ademas la banda AGENTE porque la
     * caida la registra el agente, no el bróker que supervisa; y exige que la
     * oportunidad este ABIERTA, que es lo que
     * {@code OportunidadServiceImpl.noContinuidad} comprueba.
     */
    @Override
    @Transactional(readOnly = true)
    public CapacidadesDeLaMesa capacidades(long idOportunidad, Actor actor) {
        OportunidadComercial mesa = acceso.conAcceso(idOportunidad, actor);
        boolean opera = autoridadDelEncargo(actor, mesa.getCaptacion());
        boolean encargoActivo = mesa.getCaptacion() != null
                && Captacion.ACTIVA.equals(mesa.getCaptacion().estadoActual());
        boolean mesaAbierta = ADMITEN_NEGOCIACION.contains(mesa.estadoActual());
        boolean negociable = opera && encargoActivo && mesaAbierta
                && compraventas.deLaMesa(mesa.getOrganizacionId(), idOportunidad).isEmpty()
                && ofertas.aceptadaDe(mesa.getOrganizacionId(), idOportunidad).isEmpty();

        Optional<Oferta> viva = ofertas.vivaDe(mesa.getOrganizacionId(), idOportunidad);
        boolean hayViva = viva.isPresent();
        Optional<Oferta> aceptada = ofertas.aceptadaDe(mesa.getOrganizacionId(), idOportunidad);

        return new CapacidadesDeLaMesa(
                negociable && !hayViva,
                negociable && hayViva,
                negociable && hayViva,
                negociable && hayViva,
                negociable && hayViva,
                // Cerrar la venta exige una aceptada, encargo de VENTA activo,
                // la MESA todavia abierta y que no se haya cerrado ya. Es el
                // mismo predicado que CierreVentaServiceImpl vuelve a comprobar
                // antes de escribir, `mesaAbierta` incluido: sin el, una mesa
                // que se cayo despues de aceptar una oferta encendia el boton de
                // cerrar la venta y el backend respondia 400 cuando la persona
                // ya habia escrito la fecha de firma.
                opera && encargoActivo && mesaAbierta && aceptada.isPresent()
                        && mesa.getCaptacion() != null
                        && OperacionInmobiliaria.VENTA == mesa.getCaptacion().operacion()
                        && compraventas.deLaMesa(mesa.getOrganizacionId(), idOportunidad).isEmpty(),
                actor.esAgente() && acceso.alcanza(mesa, actor) && mesa.estaAbierta());
    }

    // ==================================================================
    // Comandos
    // ==================================================================

    /**
     * Estados de la oportunidad que todavia admiten negociar. Los mismos que
     * admiten un contrato en {@code ContratoServiceImpl}: una mesa cerrada —por
     * caida o por cierre— ya no recibe cifras.
     */
    private static final java.util.Set<String> ADMITEN_NEGOCIACION = java.util.Set.of(
            OportunidadComercial.ABIERTA, OportunidadComercial.SOLICITUD_CREADA);

    @Override
    @Transactional
    public FichaOferta presentar(long idOportunidad, DatosOferta datos, Procedencia procedencia,
                                 Actor actor) {
        OportunidadComercial mesa = acceso.conAcceso(idOportunidad, actor);
        Captacion encargo = encargoQueOpera(mesa, actor);
        exigirMesaNegociable(mesa, encargo);

        if (ofertas.vivaDe(mesa.getOrganizacionId(), idOportunidad).isPresent()) {
            throw new ConflictoException(
                    "Ya hay una oferta sobre la mesa. Respondela —acepta, rechaza o "
                            + "contraoferta— antes de presentar otra: dos ofertas vivas a la vez "
                            + "no dicen a cual se respondio.");
        }
        String lado = ladoValidado(datos == null ? null : datos.presentadaPor());
        return ficha(escribirOferta(mesa, encargo, datos, lado, procedencia, actor));
    }

    @Override
    @Transactional
    public FichaOferta contraofertar(long idOportunidad, long idOferta, DatosOferta datos,
                                     Procedencia procedencia, Actor actor) {
        OportunidadComercial mesa = acceso.conAcceso(idOportunidad, actor);
        Captacion encargo = encargoQueOpera(mesa, actor);
        exigirMesaNegociable(mesa, encargo);

        Oferta anterior = deLaMesa(mesa, idOferta);
        if (!anterior.estaViva()) {
            throw new ConflictoException(
                    "La oferta de la ronda " + anterior.getRonda() + " ya esta "
                            + Oferta.rotuloDelEstado(anterior.estadoActual()).toLowerCase(Locale.ROOT)
                            + ": solo se contraoferta la que sigue sobre la mesa.");
        }

        // El lado NO se declara: lo contesta el otro. Dejarlo al cliente
        // permitiria una cadena en la que el interesado se contraoferta a si
        // mismo, que no es una negociacion sino dos ofertas suyas seguidas.
        String lado = Oferta.ladoContrario(anterior.getPresentadaPor());

        // La anterior sale de la mesa ANTES de que entre la nueva, y con flush:
        // el indice parcial uq_oferta_viva_por_oportunidad no admite dos vivas,
        // y Hibernate ordena los INSERT antes que los UPDATE, asi que sin este
        // flush explicito la nueva fila chocaria contra la vieja.
        transiciones.aplicar(anterior, anterior.getId(), Oferta.SUPERADA, actor,
                "Superada por la contraoferta del " + Oferta.rotuloDelLado(lado).toLowerCase(Locale.ROOT) + ".");
        ofertas.saveAndFlush(anterior);

        return ficha(escribirOferta(mesa, encargo, datos, lado, procedencia, actor));
    }

    @Override
    @Transactional
    public FichaOferta aceptar(long idOportunidad, long idOferta, String motivo, Actor actor) {
        Oferta oferta = paraResponder(idOportunidad, idOferta, actor);
        try {
            transiciones.aplicar(oferta, oferta.getId(), Oferta.ACEPTADA, actor,
                    motivoDe(motivo, "Oferta aceptada."));
            ofertas.saveAndFlush(oferta);
        } catch (DataIntegrityViolationException carrera) {
            // Lo corta uq_oferta_aceptada_por_oportunidad. Es el invariante de
            // dinero de esta tabla: una mesa tiene UN precio de cierre.
            throw new ConflictoException(
                    "Esta negociacion ya tiene una oferta aceptada. Una mesa se cierra con una "
                            + "sola cifra; si hay que cambiarla, el cierre es otro acto.");
        }
        return ficha(oferta);
    }

    @Override
    @Transactional
    public FichaOferta rechazar(long idOportunidad, long idOferta, String motivo, Actor actor) {
        Oferta oferta = paraResponder(idOportunidad, idOferta, actor);
        transiciones.aplicar(oferta, oferta.getId(), Oferta.RECHAZADA, actor,
                motivoDe(motivo, "Oferta rechazada."));
        // Y NADA MAS: rechazar una cifra no cierra la oportunidad. Si el
        // interesado se va, eso se registra aparte y con su motivo.
        return ficha(ofertas.save(oferta));
    }

    @Override
    @Transactional
    public FichaOferta retirar(long idOportunidad, long idOferta, String motivo, Actor actor) {
        Oferta oferta = paraResponder(idOportunidad, idOferta, actor);
        transiciones.aplicar(oferta, oferta.getId(), Oferta.RETIRADA, actor,
                motivoDe(motivo, "Oferta retirada por quien la presento."));
        return ficha(ofertas.save(oferta));
    }

    // ==================================================================
    // Guardas y auxiliares
    // ==================================================================

    /**
     * <b>La guarda de escritura de la mesa.</b> Escribir una oferta es escribir
     * la serie economica del encargo —cada vuelta deja su hito {@code O}—, asi
     * que la autoridad es la del ENCARGO y no la de la propiedad: quien negocio
     * el encargo es quien puede negociarlo, aunque otro responda por el inmueble.
     *
     * <p>No escribe nada: resuelve el encargo y exige. Es la misma forma que
     * {@code PublicacionServiceImpl.exigirEncargoPropio}.
     */
    private Captacion encargoQueOpera(OportunidadComercial mesa, Actor actor) {
        Captacion encargo = mesa.getCaptacion();
        autoridad.exigirEdicionDelEncargo(actor, encargo);
        return encargo;
    }

    /**
     * Se pregunta con el mismo metodo que despues deniega, y por eso se traga la
     * excepcion: aqui la respuesta es un booleano para pintar. <b>No impide
     * nada</b> — esta declarado como lector en {@code AutoridadDeLaPropiedadTest}.
     */
    private boolean autoridadDelEncargo(Actor actor, Captacion encargo) {
        try {
            autoridad.exigirEdicionDelEncargo(actor, encargo);
            return true;
        } catch (AccesoNoAutorizadoException | ReglaNegocioException denegado) {
            return false;
        }
    }

    /** Carga + autoridad + estado, lo que comparten aceptar, rechazar y retirar. */
    private Oferta paraResponder(long idOportunidad, long idOferta, Actor actor) {
        OportunidadComercial mesa = acceso.conAcceso(idOportunidad, actor);
        Captacion encargo = encargoQueOpera(mesa, actor);
        exigirMesaNegociable(mesa, encargo);
        Oferta oferta = deLaMesa(mesa, idOferta);
        if (!oferta.estaViva()) {
            throw new ConflictoException(
                    "La oferta de la ronda " + oferta.getRonda() + " ya esta "
                            + Oferta.rotuloDelEstado(oferta.estadoActual()).toLowerCase(Locale.ROOT)
                            + ": solo se responde a la que sigue sobre la mesa.");
        }
        return oferta;
    }

    /**
     * La oferta tiene que ser de ESTA mesa. Una de otra oportunidad del mismo
     * tenant responde 404 y no 403: para esta mesa no existe.
     */
    private Oferta deLaMesa(OportunidadComercial mesa, long idOferta) {
        Oferta oferta = ofertas.buscarFicha(mesa.getOrganizacionId(), idOferta)
                .orElseThrow(() -> new NoEncontradoException("Oferta"));
        if (!Objects.equals(oferta.getIdOportunidad(), mesa.getId())) {
            throw new NoEncontradoException("Oferta");
        }
        return oferta;
    }

    private void exigirMesaNegociable(OportunidadComercial mesa, Captacion encargo) {
        if (!ADMITEN_NEGOCIACION.contains(mesa.estadoActual())) {
            throw new ReglaNegocioException(
                    "La oportunidad ya esta cerrada; no admite mas ofertas.");
        }
        if (encargo == null || !Captacion.ACTIVA.equals(encargo.estadoActual())) {
            throw new ReglaNegocioException(
                    "El encargo tiene que estar ACTIVO para negociar sobre el.");
        }
        // Con una oferta ACEPTADA la negociacion esta cerrada, aunque todavia no
        // se haya firmado. Sin esto la mesa admitia una cifra nueva junto a la
        // aceptada -- el indice parcial solo impide una SEGUNDA aceptada, no una
        // presentada mas -- y la persona la escribia entera para que `aceptar`
        // respondiera 409 al final. Es ademas lo que `capacidades` ya decia:
        // `negociable` exige que no haya aceptada, y un comando que concede lo
        // que la capacidad niega es el mismo defecto por el otro lado.
        if (ofertas.aceptadaDe(mesa.getOrganizacionId(), mesa.getId()).isPresent()) {
            throw new ConflictoException(
                    "Esta negociacion ya tiene una oferta aceptada: la mesa esta cerrada. "
                            + "Si el acuerdo se cae, registra la caida con su motivo.");
        }
        if (compraventas.deLaMesa(mesa.getOrganizacionId(), mesa.getId()).isPresent()) {
            throw new ConflictoException(
                    "Esta negociacion ya se cerro con un contrato de compraventa.");
        }
    }

    /**
     * Escribe la fila de la oferta y su hito {@code O} en la serie economica del
     * encargo. Las dos cosas en la misma transaccion: una oferta sin hito deja
     * la serie muda, y un hito sin oferta no dice de que vuelta salio.
     */
    private Oferta escribirOferta(OportunidadComercial mesa, Captacion encargo, DatosOferta datos,
                                  String lado, Procedencia procedencia, Actor actor) {
        BigDecimal monto = montoValidado(datos == null ? null : datos.monto());
        String moneda = monedaValidada(datos == null ? null : datos.moneda());
        LocalDate fecha = fechaValidada(datos == null ? null : datos.fecha());

        Oferta oferta = new Oferta();
        oferta.setOrganizacionId(mesa.getOrganizacionId());
        oferta.setIdOportunidad(mesa.getId());
        oferta.setIdCaptacion(encargo.getId());
        oferta.setRonda(siguienteRonda(mesa));
        oferta.setPresentadaPor(lado);
        oferta.setMonto(monto);
        oferta.setMoneda(moneda);
        oferta.setCondiciones(texto(datos == null ? null : datos.condiciones()));
        oferta.setFecha(fecha);
        oferta.setIdRolRegistra(actor.idRolOperativo());
        oferta.setOrigen(procedencia == null
                ? com.controllocal.domain.auditoria.EventoDominio.CANAL_SPA : procedencia.canal());
        congelarReferencia(oferta, encargo);
        transiciones.iniciar(oferta, Oferta.PRESENTADA);
        Oferta guardada = ofertas.save(oferta);

        // El hito 'O' de la serie del encargo, atado ademas a la MESA: dos
        // negociaciones sobre la misma propiedad son dos series distinguibles.
        precios.save(PrecioPropiedad.hito(mesa.getOrganizacionId(), encargo.getPropiedad().getId(),
                        encargo.operacion(), "O", moneda, monto, fecha)
                .delEncargo(encargo.getId())
                .deLaOferta(guardada.getId()));
        return guardada;
    }

    /**
     * <b>Contra que precio se ofrecio</b> (D-N-1): el ultimo hito {@code P}
     * VIGENTE del encargo y, si el encargo no llego a anunciarse, el ultimo
     * {@code U}.
     *
     * <p>Si no hay ninguno de los dos, los cuatro campos se quedan nulos y la
     * ficha lo dice. <b>No</b> se sustituye por el importe del encargo ni por el
     * de la propia oferta: un descuento calculado contra una cifra inventada es
     * peor que no tener descuento.
     */
    private void congelarReferencia(Oferta oferta, Captacion encargo) {
        PrecioPropiedad referencia = ultimoHitoDelEncargo(encargo, PrecioPropiedad.HITO_PUBLICADO)
                .or(() -> ultimoHitoDelEncargo(encargo, PrecioPropiedad.HITO_AUTORIZADO))
                .orElse(null);
        if (referencia == null) {
            return;
        }
        oferta.conReferencia(referencia.getHito(), referencia.getId(),
                referencia.getMonto(), referencia.getMoneda());
    }

    /**
     * El ultimo hito de un tipo <b>DE ESTE ENCARGO</b>. Por encargo y no por
     * propiedad: un inmueble en venta y en alquiler a la vez tiene dos series, y
     * preguntar por la propiedad devolveria la renta como referencia de una
     * oferta de compra.
     */
    private Optional<PrecioPropiedad> ultimoHitoDelEncargo(Captacion encargo, String hito) {
        return precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(encargo.getId(), hito);
    }

    private int siguienteRonda(OportunidadComercial mesa) {
        Integer ultima = ofertas.ultimaRondaDe(mesa.getOrganizacionId(), mesa.getId());
        return ultima == null ? 1 : ultima + 1;
    }

    // ------------------------------------------------------------------
    // Validacion del cuerpo. Nada se deduce: lo que falta se rechaza.
    // ------------------------------------------------------------------

    private static BigDecimal montoValidado(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException("El importe de la oferta tiene que ser mayor que cero.");
        }
        return monto;
    }

    /**
     * <b>Sin moneda por defecto.</b> Un importe sin moneda no se puede comparar
     * con la referencia ni con el cierre, y elegir la del encargo seria suponer
     * que se ofrecio en la misma —que es justo lo que no se sabe.
     */
    private static String monedaValidada(String moneda) {
        String limpia = moneda == null ? null : moneda.trim().toUpperCase(Locale.ROOT);
        if (limpia == null || !PrecioPropiedad.MONEDAS.contains(limpia)) {
            throw new ReglaNegocioException(
                    "Declara la moneda de la oferta: " + PrecioPropiedad.MONEDAS + ".");
        }
        return limpia;
    }

    private static String ladoValidado(String lado) {
        String limpio = lado == null ? null : lado.trim().toUpperCase(Locale.ROOT);
        if (limpio == null || !Oferta.LADOS.contains(limpio)) {
            throw new ReglaNegocioException(
                    "Declara quien presenta la oferta: I el interesado, P el propietario.");
        }
        return limpio;
    }

    /** Una oferta futura no es una oferta: es una intencion, y esa no es evidencia. */
    private static LocalDate fechaValidada(LocalDate fecha) {
        LocalDate efectiva = fecha == null ? LocalDate.now() : fecha;
        if (efectiva.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha de la oferta no puede ser futura.");
        }
        return efectiva;
    }

    private static String motivoDe(String motivo, String pordefecto) {
        String limpio = texto(motivo);
        return limpio == null ? pordefecto : limpio;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static FichaOferta ficha(Oferta o) {
        ReferenciaDeLaOferta referencia = o.tieneReferencia()
                ? new ReferenciaDeLaOferta(o.getHitoReferencia(),
                        PrecioPropiedad.rotuloDelHito(o.getHitoReferencia()),
                        o.getIdPrecioReferencia(), o.getMontoReferencia(), o.getMonedaReferencia())
                // Nulo y no un record con ceros: la referencia FALTANTE es un
                // hecho -el encargo no tenia ni P ni U- y un cero se leeria como
                // «se ofrecio contra nada», que es otra cosa.
                : null;
        return new FichaOferta(o.getId(), o.getIdOportunidad(), o.getIdCaptacion(), o.getRonda(),
                o.getPresentadaPor(), Oferta.rotuloDelLado(o.getPresentadaPor()),
                o.getMonto(), o.getMoneda(),
                o.estadoActual(), Oferta.rotuloDelEstado(o.estadoActual()),
                o.getCondiciones(), o.getFecha(), o.getIdRolRegistra(), o.getOrigen(),
                referencia, Fechas.local(o.getFechaCreacion()));
    }
}
