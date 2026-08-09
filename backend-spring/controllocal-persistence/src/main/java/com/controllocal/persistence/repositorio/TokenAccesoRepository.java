package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.TokenAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * <b>Ninguna operacion de esta tabla se resuelve sin fijar el TIPO</b>
 * (D-S0-23). Es la condicion que hace segura la reutilizacion de
 * {@code token_acceso} para el desafio de MFA y la elevacion: sin ella, un
 * desafio de segundo factor podria canjearse como una recuperacion de
 * contrasena, que es exactamente el fallo que la reutilizacion podria
 * introducir.
 *
 * <p>Por eso aqui <b>no existe</b> un {@code findByHashToken} a secas. Si hace
 * falta uno nuevo, lleva tipo.
 */
public interface TokenAccesoRepository extends JpaRepository<TokenAcceso, Long> {

    /**
     * Busca por HASH <b>y TIPO</b>, que es la unica forma: el token en claro no
     * esta en ningun sitio. No filtra por organizacion a proposito — quien
     * canjea todavia no tiene sesion, asi que no hay tenant que aplicar; el
     * tenant sale de la fila encontrada.
     */
    @Query("""
            select t from TokenAcceso t
             where t.hashToken = :hashToken
               and t.tipo = :tipo
            """)
    Optional<TokenAcceso> buscarPorHashYTipo(@Param("hashToken") String hashToken,
                                             @Param("tipo") String tipo);

    /**
     * Variante para el unico caso en que un canje sirve a <b>dos</b> tipos: el
     * de contrasena acepta {@code RECUPERACION} e {@code INVITACION}, porque el
     * titular define su clave igual en los dos.
     *
     * <p>{@code tipos} es una lista <b>cerrada y explicita</b> del llamador, no
     * un comodin: sigue sin existir una busqueda sin tipo, que es lo que
     * impediria canjear un desafio de MFA como si fuera una recuperacion.
     */
    @Query("""
            select t from TokenAcceso t
             where t.hashToken = :hashToken
               and t.tipo in :tipos
            """)
    Optional<TokenAcceso> buscarPorHashEntreTipos(@Param("hashToken") String hashToken,
                                                  @Param("tipos") java.util.Collection<String> tipos);

    /**
     * Mata los tokens vivos <b>de ese tipo</b> para una credencial. Se llama
     * ANTES de insertar el nuevo, dentro de la misma transaccion: el indice
     * unico parcial {@code uq_token_acceso_activo (id_credencial, tipo)} no
     * admite dos vivos del mismo tipo, y ese es el invariante que hace que un
     * token viejo filtrado deje de servir en cuanto se emite el bueno.
     *
     * <p>Acotarlo por tipo es deliberado: emitir un desafio de MFA <b>no</b>
     * debe matar una recuperacion de contrasena pendiente. Son flujos
     * independientes y acoplarlos crearia una forma rara de sabotaje.
     */
    @Modifying
    @Query("""
            update TokenAcceso t
               set t.invalidadoEn = :ahora, t.estado = com.controllocal.domain.seguridad.TokenAcceso.REVOCADO
             where t.idCredencial = :idCredencial
               and t.tipo = :tipo
               and t.usadoEn is null
               and t.invalidadoEn is null
            """)
    int invalidarVivosDe(@Param("idCredencial") long idCredencial,
                         @Param("tipo") String tipo,
                         @Param("ahora") OffsetDateTime ahora);

    /**
     * Suma un intento fallido y <b>mata el token</b> al llegar al maximo.
     *
     * <p>Es una sola sentencia a proposito: quien la invoca lo hace desde una
     * transaccion propia ({@code REQUIRES_NEW}), porque el intento fallido se
     * registra <b>justo antes de lanzar</b> y con el contador dentro de la
     * transaccion que lanza, el rollback lo borraria — el limite no contaria
     * nada y bastaria insistir sobre el mismo desafio. Es la misma razon por la
     * que {@code EventosSeguridad} y {@code BloqueoAccesos} tienen su propia
     * transaccion.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TokenAcceso t
               set t.intentos = t.intentos + 1,
                   t.estado = case when t.intentos + 1 >= :maximo
                                   then com.controllocal.domain.seguridad.TokenAcceso.AGOTADO
                                   else t.estado end,
                   t.invalidadoEn = case when t.intentos + 1 >= :maximo
                                         then :ahora else t.invalidadoEn end
             where t.id = :id
            """)
    int sumarIntentoFallido(@Param("id") long id,
                            @Param("maximo") short maximo,
                            @Param("ahora") OffsetDateTime ahora);

    /** Retencion: los tokens caducados hace mucho no prueban nada. */
    @Modifying
    @Query("delete from TokenAcceso t where t.creadoEn < :limite")
    int purgarAnterioresA(@Param("limite") OffsetDateTime limite);
}
