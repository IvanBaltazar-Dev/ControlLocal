package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.OperacionDelEncargo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Reglas heredadas del PrecioLocalBusinessLogicImpl v1 (moneda PEN y fecha
 * de hoy por defecto, monto no negativo), con una mejora documentada: el
 * local debe existir (la v1 dejaba reventar la FK con un 500).
 */
@Service
public class PrecioLocalServiceImpl implements PrecioLocalService {

    private final PrecioPropiedadRepository precios;
    private final PropiedadRepository propiedades;
    private final OperacionDelEncargo operaciones;

    public PrecioLocalServiceImpl(PrecioPropiedadRepository precios, PropiedadRepository propiedades,
                                  OperacionDelEncargo operaciones) {
        this.precios = precios;
        this.propiedades = propiedades;
        this.operaciones = operaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaPrecio> listarPorLocal(long idPropiedad) {
        if (idPropiedad <= 0) {
            throw new ReglaNegocioException("El id de local comercial debe ser mayor que cero.");
        }
        return precios.findByIdPropiedadOrderByFechaAscIdAsc(idPropiedad).stream()
                .map(PrecioLocalServiceImpl::ficha)
                .toList();
    }

    @Override
    @Transactional
    public FichaPrecio registrar(long idPropiedad, DatosPrecio datos, Actor actor) {
        if (idPropiedad <= 0) {
            throw new ReglaNegocioException("El id de local comercial debe ser mayor que cero.");
        }
        if (datos.hito() == null || !PrecioPropiedad.HITOS.contains(datos.hito())) {
            throw new ReglaNegocioException("Valor invalido para hito de precio: " + datos.hito());
        }
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "del precio");
        if (datos.monto() == null || datos.monto().signum() < 0) {
            throw new ReglaNegocioException("El monto del precio no puede ser negativo.");
        }
        if (!propiedades.existsByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)) {
            throw new ReglaNegocioException("El local no existe.");
        }

        // De que operacion es este importe. Declarada si viene; deducida del
        // unico encargo vivo si no; y si no hay forma de saberlo, se rechaza en
        // vez de archivarlo como alquiler (D-E4-1).
        OperacionInmobiliaria operacion =
                operaciones.resolver(actor.idOrganizacion(), idPropiedad, datos.operacion());

        PrecioPropiedad precio = PrecioPropiedad.hito(actor.idOrganizacion(), idPropiedad, operacion,
                datos.hito(), moneda, datos.monto(),
                datos.fecha() != null ? datos.fecha() : LocalDate.now());
        // Atado a su encargo, y SIN encargo no hay hito (V76). Es lo que permite
        // que una venta y un alquiler de la misma propiedad tengan series
        // separadas de verdad -- y, sobre todo, lo que impide que un importe que
        // nadie autorizo entre en la serie economica de la propiedad. La base lo
        // rechazaria igual (`tg_precio_exige_encargo`); se dice aqui para que el
        // mensaje explique la alternativa en vez de llegar como error de
        // integridad.
        Captacion encargo = operaciones.encargoDe(actor.idOrganizacion(), idPropiedad, operacion)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Esta propiedad no tiene un encargo vivo de "
                                + operacion.name().toLowerCase(Locale.ROOT)
                                + ": un hito economico nace del encargo que lo autorizo. Si lo que "
                                + "quieres guardar es lo que se VIO en el mercado, va en las "
                                + "observaciones de la propiedad."));
        precio.delEncargo(encargo.getId());
        precios.save(precio);
        return ficha(precio);
    }

    private static FichaPrecio ficha(PrecioPropiedad p) {
        return new FichaPrecio(p.getId(), p.getIdPropiedad(), p.getHito(), p.getMoneda(),
                p.getMonto(), p.getFecha(), Fechas.local(p.getFechaCreacion()),
                OperacionInmobiliaria.deCodigo(p.getOperacion()).name());
    }
}
