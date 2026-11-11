package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.FactorAutenticacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FactorAutenticacionRepository extends JpaRepository<FactorAutenticacion, Long> {
    @Query("select count(f)>0 from FactorAutenticacion f where f.idCuenta=:cuenta and f.estado=com.controllocal.domain.seguridad.FactorAutenticacion.ACTIVO")
    boolean activoDeCuenta(@Param("cuenta") long cuenta);

    @Query("""
            select f from FactorAutenticacion f
             where f.idCuenta = :idCuenta
               and f.estado = com.controllocal.domain.seguridad.FactorAutenticacion.ACTIVO
            """)
    Optional<FactorAutenticacion> buscarActivo(@Param("idCuenta") long idCuenta);

    @Query("""
            select f from FactorAutenticacion f
             where f.idCuenta = :idCuenta
               and f.estado = com.controllocal.domain.seguridad.FactorAutenticacion.PENDIENTE
             order by f.id desc
            """)
    List<FactorAutenticacion> pendientesDe(@Param("idCuenta") long idCuenta);

    /**
     * <b>Anti-replay ATOMICO (D-S0-31).</b> Este es el corazon de la validacion
     * TOTP y la razon de que no sea una comparacion en memoria.
     *
     * <p>Leer {@code ultimo_paso}, comparar y despues escribir deja una ventana
     * en la que <b>dos peticiones simultaneas aceptan el mismo codigo</b>: las
     * dos leen el valor viejo antes de que ninguna escriba. Un OTP debe
     * aceptarse UNA sola vez durante su vigencia, y eso es una condicion de
     * carrera, no una comparacion.
     *
     * <p>Aqui el veredicto es <b>cuantas filas afecto</b>:
     * <ul>
     *   <li>{@code 1} → el codigo vale y queda consumido en el mismo acto;</li>
     *   <li>{@code 0} → ya se uso o es de un paso anterior. Se rechaza.</li>
     * </ul>
     *
     * <p>Se prefiere a {@code SELECT … FOR UPDATE} porque no mantiene el
     * bloqueo mientras se calcula el HMAC.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FactorAutenticacion f
               set f.ultimoPaso = :paso, f.ultimoUsoEn = :ahora
             where f.id = :idFactor
               and f.estado = com.controllocal.domain.seguridad.FactorAutenticacion.ACTIVO
               and (f.ultimoPaso is null or f.ultimoPaso < :paso)
            """)
    int consumirPaso(@Param("idFactor") long idFactor,
                     @Param("paso") long paso,
                     @Param("ahora") OffsetDateTime ahora);

    /** Limpieza de enrolamientos abandonados: un PENDIENTE viejo no vale nada. */
    @Modifying
    @Query("""
            delete from FactorAutenticacion f
             where f.estado = com.controllocal.domain.seguridad.FactorAutenticacion.PENDIENTE
               and f.creadoEn < :limite
            """)
    int purgarPendientesAnterioresA(@Param("limite") OffsetDateTime limite);
}
