package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.ComposicionEncargo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ComposicionEncargoRepository extends JpaRepository<ComposicionEncargo,Long> {
    Optional<ComposicionEncargo> findByOrganizacionIdAndId(long org,long id);
    Optional<ComposicionEncargo> findByOrganizacionIdAndIdEncargoAndVersionContenido(long org,long encargo,long version);
}
