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
    // ACTUALIZADO: Le pasamos el agente para validar propiedad y registrar historial
    public boolean actualizar(LocalComercial local, long idAgente);

    // ACTUALIZADO: Le pasamos el agente para saber quién lo desactivó
    public boolean desactivar(Long idLocal, long idAgente);
    public List<LocalComercial> listarPaginaPorAgente(long idAgente, int limite, int desplazamiento);
}

