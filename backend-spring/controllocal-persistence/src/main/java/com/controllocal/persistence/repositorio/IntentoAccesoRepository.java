package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.IntentoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Contador del bloqueo por intentos fallidos (D-S0-21). Solo hay una consulta
 * caliente —"cuantos fallos lleva esta clave en la ventana"— y el indice
 * {@code ix_intento_ventana} esta hecho para ella.
 */
public interface IntentoAccesoRepository extends JpaRepository<IntentoAcceso, Long> {

    /**
     * Fallos de una clave dentro de la ventana deslizante.
     *
     * <p>Cuenta <b>solo los fallos</b>: un acierto no consume cupo. Y se corta
     * en el ultimo exito, no en el inicio de la ventana —eso lo hace el
     * llamador con {@code desde}—, que es como "el contador de la cuenta se
     * limpia con un login correcto" sin borrar ninguna fila: el registro sigue
     * siendo append-only y la limpieza es de la <b>lectura</b>.
     */
    @Query("""
            select count(i) from IntentoAcceso i
             where i.claveTipo = :claveTipo
               and i.claveValorHash = :claveValorHash
               and i.exito = false
               and i.ocurridoEn >= :desde
            """)
    long contarFallosDesde(@Param("claveTipo") String claveTipo,
                           @Param("claveValorHash") String claveValorHash,
                           @Param("desde") OffsetDateTime desde);

    /**
     * Instante del ultimo acierto de esa clave dentro de la ventana, si lo
     * hubo. Es lo que permite "limpiar" el contador con un login correcto sin
     * tocar el historial.
     */
    @Query("""
            select max(i.ocurridoEn) from IntentoAcceso i
             where i.claveTipo = :claveTipo
               and i.claveValorHash = :claveValorHash
               and i.exito = true
               and i.ocurridoEn >= :desde
            """)
    OffsetDateTime ultimoExitoDesde(@Param("claveTipo") String claveTipo,
                                    @Param("claveValorHash") String claveValorHash,
                                    @Param("desde") OffsetDateTime desde);

    /**
     * Aciertos dentro de la ventana. Lo usa el contador de MFA (D-S0-32), que
     * <b>no</b> limpia el contador con un acierto sino que descuenta una parte:
     * si un acierto lo borrara entero, quien fuerza bruta lo reiniciaria cada
     * vez que acierta por casualidad.
     */
    @Query("""
            select count(i) from IntentoAcceso i
             where i.claveTipo = :claveTipo
               and i.claveValorHash = :claveValorHash
               and i.exito = true
               and i.ocurridoEn >= :desde
            """)
    long contarExitosDesde(@Param("claveTipo") String claveTipo,
                           @Param("claveValorHash") String claveValorHash,
                           @Param("desde") OffsetDateTime desde);

    /**
     * Purga por retencion. Sin ella la tabla crece sin techo y el barrido de
     * la ventana se degrada; 30 dias es lo que fija el Plan S0 §4.8.
     */
    @Modifying
    @Query("delete from IntentoAcceso i where i.ocurridoEn < :limite")
    int purgarAnterioresA(@Param("limite") OffsetDateTime limite);
}
