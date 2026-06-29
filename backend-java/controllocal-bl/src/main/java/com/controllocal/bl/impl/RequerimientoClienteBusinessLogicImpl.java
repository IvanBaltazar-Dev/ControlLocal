package com.controllocal.bl.impl;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.RequerimientoClienteBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.dao.DistritoDAO;
import com.controllocal.dao.RequerimientoClienteDAO;
import com.controllocal.dao.impl.DistritoDAOImpl;
import com.controllocal.dao.impl.RequerimientoClienteDAOImpl;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.Distrito;

public class RequerimientoClienteBusinessLogicImpl implements RequerimientoClienteBusinessLogic {

    private final RequerimientoClienteDAO requerimientos;
    private final DistritoDAO distritos;

    public RequerimientoClienteBusinessLogicImpl() {
        this(new RequerimientoClienteDAOImpl(), new DistritoDAOImpl());
    }

    public RequerimientoClienteBusinessLogicImpl(RequerimientoClienteDAO requerimientos, DistritoDAO distritos) {
        this.requerimientos = requerimientos;
        this.distritos = distritos;
    }

    @Override
    public Optional<RequerimientoCliente> buscarPorId(Long idRequerimiento) {
        BusinessValidations.id(idRequerimiento, "El id de requerimiento");
        return requerimientos.buscarPorId(idRequerimiento);
    }

    @Override
    public List<RequerimientoCliente> listarTodos() {
        return requerimientos.listarTodos();
    }

    @Override
    public List<RequerimientoCliente> listarPorCliente(Long idCliente) {
        BusinessValidations.id(idCliente, "El id de cliente");
        return requerimientos.listarPorCliente(idCliente);
    }

    @Override
    public Long crear(RequerimientoCliente requerimiento) {
        validar(requerimiento);
        aplicarDefaults(requerimiento);
        resolverDistritos(requerimiento);
        return requerimientos.crear(requerimiento);
    }

    @Override
    public boolean actualizar(RequerimientoCliente requerimiento) {
        BusinessValidations.id(requerimiento != null ? requerimiento.getIdRequerimiento() : null,
                "El id de requerimiento");
        validar(requerimiento);
        aplicarDefaults(requerimiento);
        resolverDistritos(requerimiento);
        return requerimientos.actualizar(requerimiento);
    }

    @Override
    public boolean cambiarEstado(Long idRequerimiento, EstadoRequerimiento estado) {
        BusinessValidations.id(idRequerimiento, "El id de requerimiento");
        if (estado == null) {
            throw new BusinessException("El estado del requerimiento es obligatorio.");
        }
        RequerimientoCliente actual = requerimientos.buscarPorId(idRequerimiento)
                .orElseThrow(() -> new BusinessException("Requerimiento no encontrado."));
        actual.setEstado(estado);
        return requerimientos.actualizar(actual);
    }

    private void validar(RequerimientoCliente r) {
        if (r == null || r.getCliente() == null || r.getCliente().getIdCliente() == null
                || r.getCliente().getIdCliente() <= 0) {
            throw new BusinessException("El cliente del requerimiento es obligatorio.");
        }
        if (r.getRubro() == null || r.getRubro().isBlank()) {
            throw new BusinessException("El rubro del requerimiento es obligatorio.");
        }
        if (r.getRentaMin() != null && r.getRentaMax() != null
                && r.getRentaMax().compareTo(r.getRentaMin()) < 0) {
            throw new BusinessException("La renta maxima no puede ser menor que la renta minima.");
        }
        if (r.getMetrajeMin() != null && r.getMetrajeMax() != null
                && r.getMetrajeMax().compareTo(r.getMetrajeMin()) < 0) {
            throw new BusinessException("El metraje maximo no puede ser menor que el metraje minimo.");
        }
    }

    private static void aplicarDefaults(RequerimientoCliente r) {
        if (r.getEstado() == null) {
            r.setEstado(EstadoRequerimiento.ACTIVO);
        }
        if (r.getMoneda() == null) {
            r.setMoneda(Moneda.PEN);
        }
    }

    // Resuelve los distritos pedidos (por id o por nombre) contra el catalogo activo.
    // Descarta los que no esten catalogados para no romper la FK requerimiento_distrito.
    private void resolverDistritos(RequerimientoCliente r) {
        List<Distrito> pedidos = r.getDistritos();
        if (pedidos == null || pedidos.isEmpty()) {
            r.setDistritos(new ArrayList<>());
            return;
        }
        List<Distrito> catalogo = distritos.listarActivos();
        List<Distrito> resueltos = new ArrayList<>();
        for (Distrito pedido : pedidos) {
            if (pedido == null) {
                continue;
            }
            Distrito match = null;
            if (pedido.getIdDistrito() != null) {
                match = catalogo.stream()
                        .filter(d -> pedido.getIdDistrito().equals(d.getIdDistrito()))
                        .findFirst().orElse(null);
            }
            if (match == null && pedido.getNombre() != null && !pedido.getNombre().isBlank()) {
                String objetivo = normalizar(pedido.getNombre());
                match = catalogo.stream()
                        .filter(d -> normalizar(d.getNombre()).equals(objetivo))
                        .findFirst().orElse(null);
            }
            final Distrito encontrado = match;
            if (encontrado != null
                    && resueltos.stream().noneMatch(d -> d.getIdDistrito().equals(encontrado.getIdDistrito()))) {
                resueltos.add(encontrado);
            }
        }
        r.setDistritos(resueltos);
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase();
    }
}
