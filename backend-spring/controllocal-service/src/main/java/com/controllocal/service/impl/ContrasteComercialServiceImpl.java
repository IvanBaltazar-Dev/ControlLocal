package com.controllocal.service.impl;

import com.controllocal.persistence.query.MediaPropia;
import com.controllocal.persistence.query.RangoDeRenta;
import com.controllocal.persistence.repositorio.ContrasteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContrasteComercialService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.BandaDeMetraje;
import com.controllocal.service.soporte.Contraste;
import com.controllocal.service.soporte.MediasPropias;
import com.controllocal.service.soporte.PoliticaComercial;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Situa un dato contra la operacion de la propia casa, o dice que no puede.
 *
 * <h2>La degradacion es el camino normal, no el error</h2>
 *
 * <p>Con los datos del 2026-08-19 <b>las cuatro comparaciones degradan</b>: cero
 * hitos de renta publicada, cero visitas realizadas, cero interacciones colgadas
 * de una prospeccion y cuatro contratos con cronologia valida frente a los cinco
 * de muestra minima. Eso no es un fallo que haya que rodear: es lo que hay, y
 * decirlo es el producto.
 *
 * <p>El codigo esta escrito para que el camino con dato y el camino sin dato
 * cuesten lo mismo. Cuando la cartera crezca, el contraste aparecera solo.
 */
@Service
public class ContrasteComercialServiceImpl implements ContrasteComercialService {

    private final ContrasteRepository contrastes;
    private final Alcances alcances;

    public ContrasteComercialServiceImpl(ContrasteRepository contrastes, Alcances alcances) {
        this.contrastes = contrastes;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public Contraste rangoDeRenta(long idOrganizacion, String zona, BigDecimal metraje,
                                  BigDecimal renta, String moneda) {
        BandaDeMetraje banda = BandaDeMetraje.de(metraje);
        if (zona == null || zona.isBlank() || banda == null || moneda == null) {
            // Sin zona o sin metraje no se sabe con que grupo compararla, y
            // elegir uno cualquiera la pondria a competir con quien no compite.
            return Contraste.sinGrupoComparable();
        }

        RangoDeRenta rango = contrastes.rangoDeRenta(idOrganizacion, zona,
                banda.desdeODesdeCero(), banda.hastaOInfinito(), moneda);
        int observaciones = rango == null ? 0 : rango.getObservaciones();

        if (!PoliticaComercial.rangoPublicable(observaciones)) {
            return Contraste.sinReferenciaSuficiente(zona, banda.rotulo(), observaciones);
        }
        return Contraste.enRango(rango.getMinimo(), rango.getMaximo(), renta, moneda,
                zona, banda.rotulo(), observaciones);
    }

    @Override
    @Transactional(readOnly = true)
    public MediasPropias mediasDe(Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        if (alcance.vacio()) {
            return new MediasPropias(
                    MediasPropias.Media.sinBase(0, "Todavia no hay visitas con que medirlo"),
                    MediasPropias.Media.sinBase(0, "Todavia no hay contratos con que medirlo"),
                    MediasPropias.Media.sinBase(0, "Todavia no hay contactos con que medirlo"));
        }
        boolean global = alcance.global();
        var roles = alcance.paramRoles();

        MediaPropia visitas = contrastes.propuestasPorVisita(organizacion, global, roles);
        MediaPropia contratos = contrastes.diasHastaContrato(organizacion, global, roles);
        MediaPropia recontactos = contrastes.plazoRealDeRecontacto(organizacion, global, roles);

        return new MediasPropias(
                MediasPropias.proporcion(casos(visitas), base(visitas),
                        "solicitudes", "visitas realizadas"),
                MediasPropias.magnitud(valor(contratos), base(contratos),
                        "dias", "contratos firmados"),
                MediasPropias.magnitud(valor(recontactos), base(recontactos),
                        "dias", "recontactos registrados"));
    }

    private static int base(MediaPropia m) {
        return m == null ? 0 : m.getBase();
    }

    private static int casos(MediaPropia m) {
        return m == null ? 0 : m.getCasos();
    }

    private static BigDecimal valor(MediaPropia m) {
        return m == null ? null : m.getValor();
    }
}
