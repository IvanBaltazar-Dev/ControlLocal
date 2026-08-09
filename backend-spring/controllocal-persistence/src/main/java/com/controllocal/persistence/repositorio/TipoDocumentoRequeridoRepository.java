package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.TipoDocumentoRequerido;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Catalogo GLOBAL (sin tenant): los tipos de documento del alquiler son los
 * mismos para toda corredora y sus ids 1..8 son parte del cable congelado.
 */
public interface TipoDocumentoRequeridoRepository extends JpaRepository<TipoDocumentoRequerido, Long> {
}
