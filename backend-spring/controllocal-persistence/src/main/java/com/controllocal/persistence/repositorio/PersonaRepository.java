package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.Persona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    Optional<Persona> findByOrganizacionIdAndId(long idOrganizacion, long id);
}
