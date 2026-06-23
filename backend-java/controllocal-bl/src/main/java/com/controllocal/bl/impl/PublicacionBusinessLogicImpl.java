package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.controllocal.bl.PublicacionBusinessLogic;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.PublicacionDAO;
import com.controllocal.dao.impl.PublicacionDAOImpl;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.comercial.enums.CanalPublicacion;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;

public class PublicacionBusinessLogicImpl implements PublicacionBusinessLogic {

    private final PublicacionDAO publicaciones;

    public PublicacionBusinessLogicImpl() {
        this(new PublicacionDAOImpl());
    }

    public PublicacionBusinessLogicImpl(PublicacionDAO publicaciones) {
        this.publicaciones = publicaciones;
    }

    @Override
    public List<Publicacion> listarPorInmueble(Long idLocal) {
        if (idLocal == null || idLocal <= 0) {
            return List.of();
        }
        return publicaciones.listarPorInmueble(idLocal);
    }

    @Override
    public String codigoEstadoPublicacion(Long idLocal) {
        if (idLocal == null) {
            return EstadoPublicacion.BORRADOR.getCodigo();
        }
        return publicaciones.listarPorInmueble(idLocal).stream()
                .findFirst()
                .map(Publicacion::getEstado)
                .map(EstadoPublicacion::getCodigo)
                .orElse(EstadoPublicacion.BORRADOR.getCodigo());
    }

    @Override
    public void sincronizar(LocalComercial local, String codigoEstado) {
        if (codigoEstado == null || codigoEstado.isBlank()) {
            return;
        }
        // fromCodigo lanza IllegalArgumentException si es invalido (lo mapea ApiExceptionMapper a 400).
        EstadoPublicacion estado = EstadoPublicacion.fromCodigo(codigoEstado);
        TransactionRunner.write(conn -> {
            List<Publicacion> existentes = publicaciones.listarPorInmueble(local.getIdLocal());
            Publicacion publicacion = existentes.isEmpty() ? null : existentes.get(0);
            if (publicacion == null && estado == EstadoPublicacion.BORRADOR) {
                return;
            }
            if (publicacion == null) {
                publicacion = nuevaPublicacionWeb(local);
            }
            publicacion.setEstado(estado);
            publicacion.setRentaPublicada(local.getPrecioReferencial());
            publicacion.setTituloAnuncio("Publicacion " + local.getCodigoLocal());
            publicacion.setFechaBaja(estado == EstadoPublicacion.CERRADO ? LocalDateTime.now() : null);
            if (publicacion.getIdPublicacion() == null) {
                publicaciones.crear(publicacion);
            } else {
                publicaciones.actualizar(publicacion);
            }
        });
    }

    private static Publicacion nuevaPublicacionWeb(LocalComercial local) {
        Publicacion publicacion = new Publicacion();
        publicacion.setInmueble(local);
        publicacion.setCanal(CanalPublicacion.WEB_PROPIA);
        publicacion.setVersionAnuncio(1);
        publicacion.setTituloAnuncio("Publicacion " + local.getCodigoLocal());
        publicacion.setRentaPublicada(local.getPrecioReferencial());
        publicacion.setMoneda(Moneda.PEN);
        publicacion.setCodigoOrigen("WEB-" + local.getIdLocal());
        publicacion.setFechaPublicacion(LocalDateTime.now());
        return publicacion;
    }
}
