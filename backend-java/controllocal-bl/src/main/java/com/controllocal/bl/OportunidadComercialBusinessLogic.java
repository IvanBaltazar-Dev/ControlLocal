package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.OportunidadComercial;

public interface OportunidadComercialBusinessLogic {

    public Long registrar(OportunidadComercial oportunidad);
    public Optional<OportunidadComercial> buscarPorId(Long idOportunidad);
    public List<OportunidadComercial> listarTodos();
    public List<OportunidadComercial> listarPorAgentes(java.util.Collection<Long> idsAgente);
    public List<OportunidadComercial> listarPorCaptaciones(java.util.Collection<Long> idsCaptacion);
    public List<OportunidadComercial> listarPorCliente(Long idCliente);
    public List<OportunidadComercial> listarPorPropietario(Long idPropietario);
    public List<OportunidadComercial> listarPorIds(java.util.Collection<Long> ids);
    public boolean actualizar(OportunidadComercial oportunidad);
    public boolean cerrarNoContinua(Long idOportunidad, String motivo);
    public boolean cerrarExitosa(Long idOportunidad);
    public boolean cerrarNoFavorable(Long idOportunidad, String motivo);
    public boolean eliminar(Long idOportunidad);
}
