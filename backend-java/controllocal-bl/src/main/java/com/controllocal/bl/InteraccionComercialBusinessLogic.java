package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.InteraccionComercial;

public interface InteraccionComercialBusinessLogic {

    public Long registrar(InteraccionComercial interaccion);
    public Optional<InteraccionComercial> buscarPorId(Long idInteraccion);
    public List<InteraccionComercial> listarTodos();
    public List<InteraccionComercial> listarPorOportunidad(Long idOportunidad);
    public List<InteraccionComercial> listarPorProspeccion(Long idProspeccion);
    public List<InteraccionComercial> listarPorCaptacion(Long idCaptacion);
    public List<InteraccionComercial> listarPorCliente(Long idCliente);
    public List<InteraccionComercial> listarPorAgentes(java.util.Collection<Long> idsAgente);
    public boolean actualizar(InteraccionComercial interaccion);
    public boolean eliminar(Long idInteraccion);
}
