package com.controllocal.persistence.repositorio;

import com.controllocal.domain.organizacion.Organizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {

    /**
     * Marca que esta organizacion ya cruzo a MFA de gobierno (V37.2).
     *
     * <p>Se enciende <b>en la misma transaccion</b> que activa el primer factor
     * de un {@code TENANT_ADMIN} y <b>nunca se apaga</b>: una organizacion no
     * des-cruza ese umbral.
     *
     * <p>Es lo que hace seguro en el arranque al trigger del invariante
     * operativo: mientras sea falso rige la regla de V34 (basta con que exista
     * gobierno), asi que el sistema no queda tapiado entre el despliegue y el
     * primer enrolamiento — el momento en el que, por definicion, no hay
     * ningun administrador operativo todavia.
     *
     * <p>Condicional a proposito: si ya estaba encendida, no escribe.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            update Organizacion o set o.mfaGobiernoExigido = true
             where o.id = :idOrganizacion and o.mfaGobiernoExigido = false
            """)
    int exigirMfaDeGobierno(@org.springframework.data.repository.query.Param("idOrganizacion")
                            long idOrganizacion);

    Optional<Organizacion> findByCodigo(String codigo);
}
