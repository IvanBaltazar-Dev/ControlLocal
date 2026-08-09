package com.controllocal.persistence.repositorio;

import com.controllocal.domain.consentimiento.AvisoPrivacidadVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Versiones del aviso de privacidad. Es catalogo GLOBAL, no privado de un
 * tenant: el aviso lo publica la plataforma.
 */
public interface AvisoPrivacidadVersionRepository extends JpaRepository<AvisoPrivacidadVersion, Long> {

    /** La unica sin fecha de cierre (indice unico parcial en V28). */
    Optional<AvisoPrivacidadVersion> findFirstByVigenteHastaIsNull();

    /**
     * Ultima version publicada con cambio MATERIAL. Es la frontera de vigencia:
     * una autorizacion otorgada contra una version anterior a esta ya no vale
     * y hay que volver a pedirla (D-27 §3.4).
     * <p>
     * Devuelve vacio cuando nunca hubo un cambio material, que es el caso
     * normal: entonces ninguna autorizacion caduca por este motivo.
     */
    @Query("""
            select v from AvisoPrivacidadVersion v
             where v.cambioMaterial = true
             order by v.vigenteDesde desc, v.id desc
            limit 1
            """)
    Optional<AvisoPrivacidadVersion> ultimoCambioMaterial();
}
