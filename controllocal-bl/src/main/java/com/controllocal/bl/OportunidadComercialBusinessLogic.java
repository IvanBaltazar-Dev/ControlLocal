package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.OportunidadComercial;

public interface OportunidadComercialBusinessLogic {

    public Long registrar(OportunidadComercial oportunidad);
    public Optional<OportunidadComercial> buscarPorId(Long idOportunidad);
    public List<OportunidadComercial> listarTodos();
    public boolean actualizar(OportunidadComercial oportunidad);
    public boolean cerrarNoContinua(Long idOportunidad, String motivo);
    public boolean cerrarExitosa(Long idOportunidad);
    public boolean cerrarNoFavorable(Long idOportunidad, String motivo);
    public boolean eliminar(Long idOportunidad);
}
