package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.inmueble.FotoLocal;

public interface FotoLocalDAO {
    Long crear(FotoLocal foto);
    List<FotoLocal> listarPorLocal(Long idLocal);
    Optional<FotoLocal> buscarPorId(Long idFoto);
    boolean eliminar(Long idFoto);
}
