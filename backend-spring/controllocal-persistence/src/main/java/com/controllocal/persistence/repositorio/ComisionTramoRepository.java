package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.ComisionTramo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ComisionTramoRepository extends JpaRepository<ComisionTramo,Long> {
    List<ComisionTramo> findByOrganizacionIdAndComisionAndRevisionPactoOrderByIndicePactoAsc(long org,long comision,long revision);
    Optional<ComisionTramo> findByOrganizacionIdAndComisionAndId(long org,long comision,long id);
    List<ComisionTramo> findByOrganizacionIdAndComisionIn(long org,Collection<Long> ids);
}
