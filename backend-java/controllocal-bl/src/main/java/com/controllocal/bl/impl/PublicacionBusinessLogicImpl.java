package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
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

    @Override
    public Optional<Publicacion> buscarPorId(Long idPublicacion) {
        if (idPublicacion == null || idPublicacion <= 0) {
            return Optional.empty();
        }
        return publicaciones.buscarPorId(idPublicacion);
    }

    @Override
    public Publicacion crear(Long idLocal, Publicacion datos) {
        if (idLocal == null || idLocal <= 0) {
            throw new BusinessException("El local de la publicacion es obligatorio.");
        }
        if (datos == null || datos.getCanal() == null) {
            throw new BusinessException("El canal de la publicacion es obligatorio.");
        }
        LocalComercial local = new LocalComercial();
        local.setIdLocal(idLocal);
        datos.setInmueble(local);
        datos.setVersionAnuncio(1);
        if (datos.getMoneda() == null) {
            datos.setMoneda(Moneda.PEN);
        }
        if (datos.getEstado() == null) {
            datos.setEstado(EstadoPublicacion.PUBLICADO);
        }
        if (datos.getTituloAnuncio() == null || datos.getTituloAnuncio().isBlank()) {
            datos.setTituloAnuncio("Publicacion " + idLocal);
        }
        if (datos.getCodigoOrigen() == null || datos.getCodigoOrigen().isBlank()) {
            datos.setCodigoOrigen(datos.getCanal().getCodigo() + "-" + idLocal);
        }
        datos.setFechaPublicacion(LocalDateTime.now());
        datos.setFechaBaja(datos.getEstado() == EstadoPublicacion.CERRADO ? LocalDateTime.now() : null);
        datos.setIdPublicacion(publicaciones.crear(datos));
        return datos;
    }

    @Override
    public Publicacion actualizar(Long idPublicacion, Publicacion datos) {
        Publicacion actual = buscarPorId(idPublicacion)
                .orElseThrow(() -> new BusinessException("Publicacion no encontrada."));
        if (datos != null) {
            if (datos.getCanal() != null) {
                actual.setCanal(datos.getCanal());
            }
            actual.setUrlPublicacion(datos.getUrlPublicacion());
            if (datos.getRentaPublicada() != null) {
                actual.setRentaPublicada(datos.getRentaPublicada());
            }
            if (datos.getMoneda() != null) {
                actual.setMoneda(datos.getMoneda());
            }
            if (datos.getTituloAnuncio() != null && !datos.getTituloAnuncio().isBlank()) {
                actual.setTituloAnuncio(datos.getTituloAnuncio());
            }
            if (datos.getCodigoOrigen() != null && !datos.getCodigoOrigen().isBlank()) {
                actual.setCodigoOrigen(datos.getCodigoOrigen());
            }
        }
        actual.setVersionAnuncio((actual.getVersionAnuncio() == null ? 1 : actual.getVersionAnuncio()) + 1);
        publicaciones.actualizar(actual);
        return actual;
    }

    @Override
    public Publicacion cambiarEstado(Long idPublicacion, EstadoPublicacion estado) {
        if (estado == null) {
            throw new BusinessException("El estado de la publicacion es obligatorio.");
        }
        Publicacion actual = buscarPorId(idPublicacion)
                .orElseThrow(() -> new BusinessException("Publicacion no encontrada."));
        actual.setEstado(estado);
        actual.setFechaBaja(estado == EstadoPublicacion.CERRADO ? LocalDateTime.now() : null);
        if (estado == EstadoPublicacion.PUBLICADO && actual.getFechaPublicacion() == null) {
            actual.setFechaPublicacion(LocalDateTime.now());
        }
        publicaciones.actualizar(actual);
        return actual;
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
