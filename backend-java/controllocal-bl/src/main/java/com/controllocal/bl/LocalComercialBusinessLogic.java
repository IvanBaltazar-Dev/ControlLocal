package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.inmueble.LocalComercial;

public interface LocalComercialBusinessLogic {

    public Long registrar(LocalComercial local);
    public Optional<LocalComercial> buscarPorId(Long idLocal);
    public List<LocalComercial> listarTodos();
    public List<LocalComercial> listarPagina(int limite, int desplazamiento);
    public long contar();
    public boolean actualizar(LocalComercial local);
    public boolean desactivar(Long idLocal);
}

