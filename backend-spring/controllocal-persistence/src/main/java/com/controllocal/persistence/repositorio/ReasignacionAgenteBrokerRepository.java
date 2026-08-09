package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.ReasignacionAgenteBroker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReasignacionAgenteBrokerRepository
        extends JpaRepository<ReasignacionAgenteBroker, Long> {

    List<ReasignacionAgenteBroker> findByOrganizacionIdOrderByIdDesc(long idOrganizacion);

    List<ReasignacionAgenteBroker> findByOrganizacionIdAndIdRolAgenteOrderByIdDesc(
            long idOrganizacion, long idRolAgente);
}
