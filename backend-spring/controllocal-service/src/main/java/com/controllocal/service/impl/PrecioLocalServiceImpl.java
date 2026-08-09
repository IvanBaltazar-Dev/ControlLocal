package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Fechas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Reglas heredadas del PrecioLocalBusinessLogicImpl v1 (moneda PEN y fecha
 * de hoy por defecto, monto no negativo), con una mejora documentada: el
 * local debe existir (la v1 dejaba reventar la FK con un 500).
 */
@Service
public class PrecioLocalServiceImpl implements PrecioLocalService {

    private final PrecioPropiedadRepository precios;
    private final PropiedadRepository propiedades;

    public PrecioLocalServiceImpl(PrecioPropiedadRepository precios, PropiedadRepository propiedades) {
        this.precios = precios;
        this.propiedades = propiedades;
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

        PrecioPropiedad precio = new PrecioPropiedad();
        precio.setOrganizacionId(actor.idOrganizacion());
        precio.setIdPropiedad(idPropiedad);
        precio.setHito(datos.hito());
        precio.setMoneda(moneda);
        precio.setMonto(datos.monto());
        precio.setFecha(datos.fecha() != null ? datos.fecha() : LocalDate.now());
        precios.save(precio);
        return ficha(precio);
    }

    private static FichaPrecio ficha(PrecioPropiedad p) {
        return new FichaPrecio(p.getId(), p.getIdPropiedad(), p.getHito(), p.getMoneda(),
                p.getMonto(), p.getFecha(), Fechas.local(p.getFechaCreacion()));
    }
}
