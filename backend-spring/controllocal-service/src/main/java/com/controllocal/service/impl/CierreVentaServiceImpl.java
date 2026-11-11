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
import com.controllocal.service.publicacion.HistoriadorPublicacion;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.CierreVentaService;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.formalizacion.FormalizacionService.AcuerdoFinalDocumentado;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.persistence.repositorio.MesaNegociacionRepository;
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
    private final HistoriadorPublicacion publicaciones;
    private final SupervisionAgenteRepository supervisiones;
    private final Alcances alcances;
    private final AutoridadDePropiedad autoridad;
    private final Transiciones transiciones;
    private final TareaService tareas;
    private final AlertaService alertas;
    private final AccesoEncargo accesoEncargo;
    private final MesaNegociacionRepository mesas;

    public CierreVentaServiceImpl(CaptacionRepository captaciones,
                                  OportunidadComercialRepository oportunidades,
                                  OfertaRepository ofertas,
                                  ContratoCompraventaRepository compraventas,
                                  PrecioPropiedadRepository precios,
                                  PropiedadRepository propiedades,
                                  HistoriadorPublicacion publicaciones,
                                  SupervisionAgenteRepository supervisiones,
                                  Alcances alcances, AutoridadDePropiedad autoridad,
                                  Transiciones transiciones, TareaService tareas,
                                  AlertaService alertas,AccesoEncargo accesoEncargo,MesaNegociacionRepository mesas) {
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
        this.alertas = alertas;this.accesoEncargo=accesoEncargo;this.mesas=mesas;
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
                contrato.getIdOferta()==null?null:ofertas.buscarFicha(encargo.getOrganizacionId(),contrato.getIdOferta()).orElse(null));
    }

    /** RF059/060: sólo desde el hito confirmado, nunca desde un botón de cierre directo. */
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long confirmarHito(ContextoEconomico contexto,FuenteHitoConfirmado hito,
            FuenteEconomicaFinal fuente,AcuerdoFinalDocumentado revisado,Actor actor) {
        Captacion encargo=accesoEncargo.leer(contexto.encargo(),actor);
        accesoEncargo.exigirDecision(encargo,actor);
        if(contexto.organizacion()!=actor.idOrganizacion()||contexto.oportunidad()==null
                ||hito.tipo()!=com.controllocal.domain.comercial.TipoHitoContractual.ESCRITURA
                ||!Objects.equals(contexto.referencia(),hito.contexto())||fuente==null
                ||fuente.precioVenta()==null||fuente.precioVenta().importe()==null
                ||fuente.precioVenta().importe().signum()<=0)
            throw new ReglaNegocioException("La escritura requiere contexto, fuente y precio final acreditados.");
        var dinero=fuente.precioVenta();
        if(dinero.moneda()==null||!PrecioPropiedad.MONEDAS.contains(dinero.moneda()))
            throw new ReglaNegocioException("El precio final requiere su propia moneda conocida.");
        if(hito.fechaEfectiva()==null)
            throw new ReglaNegocioException("El hito requiere su fecha efectiva acreditada.");
        var mesa=oportunidades.buscarFicha(actor.idOrganizacion(),contexto.oportunidad())
                .orElseThrow(()->new NoEncontradoException("Oportunidad"));
        if(mesa.getCaptacion()==null||!Objects.equals(mesa.getCaptacion().getId(),encargo.getId()))
            throw new NoEncontradoException("Oportunidad del Encargo");
        var anterior=compraventas.deLaMesa(actor.idOrganizacion(),mesa.getId());
        if(hito.rectifica()!=null) {
            var existente=anterior.orElseThrow(()->new NoEncontradoException("Contrato de compraventa"));
            if(!Objects.equals(contexto.contratoVenta(),existente.getId()))
                throw new NoEncontradoException("Contrato del contexto");
            existente.setMontoPactado(dinero.importe());existente.setMoneda(dinero.moneda());
            existente.setFechaFirma(hito.fechaEfectiva());
            declararEscritura(existente,revisado);
            compraventas.saveAndFlush(existente);
            return existente.getId();
        }
        if(anterior.isPresent()||!ADMITEN_CIERRE.contains(mesa.estadoActual()))
            throw new ConflictoException("La operación ya tiene un resultado contractual; no admite otro cierre.");
        Long oferta=null;
        if(fuente.acuerdoC4()!=null) {
            var acuerdo=mesas.acuerdoExacto(actor.idOrganizacion(),fuente.acuerdoC4(),fuente.versionAcuerdoC4())
                    .orElseThrow(()->new NoEncontradoException("Acuerdo histórico exacto"));
            oferta=acuerdo.oferta();
            var propuesta=ofertas.buscarFicha(actor.idOrganizacion(),oferta)
                    .orElseThrow(()->new NoEncontradoException("Oferta del acuerdo"));
            if(!Objects.equals(propuesta.getIdOportunidad(),mesa.getId()))
                throw new NoEncontradoException("Acuerdo de la operación");
        }
        var contrato=new ContratoCompraventa();contrato.setOrganizacionId(actor.idOrganizacion());
        contrato.setIdOportunidad(mesa.getId());contrato.setIdCaptacion(encargo.getId());
        contrato.setIdPropiedad(encargo.getPropiedad().getId());contrato.setIdOferta(oferta);
        contrato.setIdRolCliente(mesa.getCliente()==null?null:mesa.getCliente().getId());
        contrato.setIdRolAgenteCierre(encargo.getAgente().getId());
        contrato.setIdRolBrokerCierre(brokerDe(actor.idOrganizacion(),encargo.getAgente().getId()));
        contrato.setMontoPactado(dinero.importe());contrato.setMoneda(dinero.moneda());
        contrato.setFechaFirma(hito.fechaEfectiva());declararEscritura(contrato,revisado);
        if(revisado!=null)contrato.setObservaciones(texto(revisado.descripcion()));
        compraventas.saveAndFlush(contrato);
        String motivo="Escritura confirmada de la operación "+mesa.getCodigoOportunidad()+".";
        // El total de un paquete no se convierte en el precio individual de sus unidades.
        if(fuente.objeto().tipo()==com.controllocal.service.OfertaService.TipoObjeto.UNIDAD)
            precios.save(PrecioPropiedad.hito(actor.idOrganizacion(),fuente.objeto().propiedad(),
                    OperacionInmobiliaria.VENTA,PrecioPropiedad.HITO_CIERRE,dinero.moneda(),dinero.importe(),hito.fechaEfectiva())
                    .delEncargo(encargo.getId()).deLaOferta(oferta));
        mesa.marcarCierreExitoso();
        transiciones.aplicar(mesa,mesa.getId(),OportunidadComercial.FINALIZADA_EXITOSA,actor,motivo,hito.fechaEfectiva(),hito.referencia().revision());
        oportunidades.save(mesa);
        transiciones.aplicar(encargo,encargo.getId(),Captacion.FINALIZADO,actor,motivo,hito.fechaEfectiva());
        encargo.cerrar(hito.fechaEfectiva(),CIERRE_POR_VENTA,motivo);captaciones.save(encargo);
        retirarDelMercado(encargo,fuente.objeto().unidades(),actor,motivo);
        tareas.resolverPorCierreConfirmado(hito.referencia().revision(),actor);
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.OPORTUNIDAD_CERRADA,Alerta.INFO,
                OportunidadComercial.ENTIDAD_TIPO,mesa.getId(),encargo.getAgente().getId(),motivo,
                com.controllocal.service.soporte.FuentesAsuntos.Origen.de("REVISION_HITO",hito.referencia().revision())),actor);
        return contrato.getId();
    }
    private static void declararEscritura(ContratoCompraventa contrato,AcuerdoFinalDocumentado revisado) {
        if(revisado!=null) {
            var d=revisado.declaradoEnEscritura();
            contrato.declararEnEscritura(d==null?null:d.importe(),d==null?null:d.moneda());
        }
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
     * <b>El inmueble se vendio</b>, dicho con esa palabra desde V91.
     *
     * <p>Hasta este corte aqui se escribia {@code RETIRADO} —«retirado del
     * mercado»— porque era lo mas fuerte que el vocabulario sabia decir con
     * verdad: no habia {@code VENDIDO}, y las otras opciones eran peores
     * ({@code DISPONIBLE} lo desmiente la venta; {@code ALQUILADO} es falso y
     * ademas contamina la estadistica de rentas). Se eligio la afirmacion
     * verdadera aunque incompleta, y se dejo escrita la decision pendiente.
     *
     * <p>{@code V} la cierra. No es un sinonimo mas bonito de {@code T}: son
     * dos hechos con dos futuros distintos —retirar es reversible y vender no
     * lo es—, y la maquina de estados lo dice sin adornos: de {@code RETIRADO}
     * se vuelve a {@code DISPONIBLE} y de {@code VENDIDO} no se sale.
     *
     * <p>El por que sigue escrito donde ya estaba —{@code contrato_compraventa},
     * el hito {@code C} y {@code captacion.motivo_cierre = 'V'}—; lo que cambia
     * es que la columna del inmueble ya no obliga a ir a buscarlo.
     *
     * <p><b>La titularidad no se toca</b>, y no es un olvido: quien figura como
     * propietario despues de una venta es otro hecho, con su fecha y su
     * documento, y esta fuera del alcance de este corte.
     */
    private void retirarDelMercado(Captacion encargo,java.util.List<Long> unidades,Actor actor,String motivo) {
        for(long id:unidades) {
            Propiedad propiedad=propiedades.findById(id).filter(p->p.getOrganizacionId()==actor.idOrganizacion())
                    .orElseThrow(()->new NoEncontradoException("Unidad de la operación"));
            transiciones.aplicarDisponibilidad(propiedad,id,DisponibilidadComercial.VENDIDO,actor,motivo);
            propiedades.save(propiedad);
        }
        publicaciones.cerrarPorHito(encargo,actor);
    }

    private Long brokerDe(Long idOrganizacion, Long idRolAgente) {
        if (idOrganizacion == null || idRolAgente == null) {
            return null;
        }
        return supervisiones.buscarActivaPorAgente(idOrganizacion, idRolAgente)
                .map(SupervisionAgente::getIdRolBroker)
                .orElse(null);
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
