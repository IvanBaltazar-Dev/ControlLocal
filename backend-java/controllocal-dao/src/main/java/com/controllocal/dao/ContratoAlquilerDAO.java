package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.ContratoAlquiler;

public interface ContratoAlquilerDAO extends CrudDAO<ContratoAlquiler> {
    Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad);

    // Pagina de contratos (que tienen solicitud) acotada por rol en SQL, sin cargar tablas
    // completas: agente -> sus contratos (solicitud.id_agente); broker -> contratos de las
    // captaciones que supervisa (idsCaptacion); admin -> ambos null = sin filtro.
    List<ContratoAlquiler> listarPaginaFiltrado(Long idAgente, Collection<Long> idsCaptacion,
            int limite, int desplazamiento);

    long contarFiltrado(Long idAgente, Collection<Long> idsCaptacion);
}
