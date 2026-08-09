package com.controllocal.persistence.repositorio;

import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Membresia de un usuario en una organizacion. Existia la tabla desde V6 y
 * <b>nadie la leia</b> (H-14): el codigo derivaba la banda de
 * {@code detalle_broker.es_administrador}, que es exactamente la confusion
 * entre rol operativo y gobierno que cierra el Bloque 5 (D-S0-8).
 *
 * <p>El camino de lectura es {@code usuario_organizacion.id_usuario} ->
 * {@code persona_rol} del USUARIO_INTERNO -> {@code persona}. Durante la
 * convivencia {@code id_usuario} apunta al rol interno (1:1 con el login);
 * cuando exista la cuenta global (D-22) cambiara el destino del salto, no
 * estas consultas.
 */
public interface UsuarioOrganizacionRepository extends JpaRepository<UsuarioOrganizacion, Long> {

    /**
     * Banda activa de una persona en el tenant, o vacio si no tiene membresia.
     *
     * <p>Vacio <b>no</b> es un error: significa "sin banda declarada", y el
     * llamador cae al rol operativo. Lo que nunca se deduce de la ausencia es
     * gobierno — un usuario sin membresia no es administrador de nada.
     */
    @Query("""
            select uo.rol
              from UsuarioOrganizacion uo, PersonaRol r
            where uo.organizacionId = :idOrganizacion
              and uo.idUsuario = r.id
              and uo.estado = 'A'
              and r.persona.id = :idPersona
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.USUARIO_INTERNO
            """)
    Optional<String> bandaActivaDePersona(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("idPersona") long idPersona);

    /**
     * Todas las bandas activas del tenant, en pares {@code (idUsuario, rol)}.
     *
     * <p>Existe para el padron de gobierno: pedirlas de una vez y cruzarlas en
     * memoria evita tanto una consulta por cuenta como el {@code left join} a
     * una entidad sin relacion declarada, que es lo que colapsaba el padron a
     * una sola fila.
     */
    @Query("""
            select uo.idUsuario, uo.rol
              from UsuarioOrganizacion uo
             where uo.organizacionId = :idOrganizacion
               and uo.estado = 'A'
            """)
    java.util.List<Object[]> bandasActivas(@Param("idOrganizacion") long idOrganizacion);

    /** Membresia activa por cuenta, para modificarla (alta, baja, cambio de banda). */
    @Query("""
            select uo from UsuarioOrganizacion uo
            where uo.organizacionId = :idOrganizacion
              and uo.idUsuario = :idCuenta
              and uo.estado = 'A'
            """)
    Optional<UsuarioOrganizacion> buscarActivaPorCuenta(@Param("idOrganizacion") long idOrganizacion,
                                                        @Param("idCuenta") long idCuenta);

    /**
     * Cuantos usuarios activos tiene esa banda en el tenant. Es el recuento del
     * invariante "una organizacion nunca sin administrador" (D-S0-9): lo hace la
     * guarda de aplicacion para dar un mensaje, y el trigger deferred de V34 lo
     * repite como garantia.
     */
    @Query("""
            select count(uo) from UsuarioOrganizacion uo
            where uo.organizacionId = :idOrganizacion
              and uo.rol = :rol
              and uo.estado = 'A'
            """)
    long contarActivosConBanda(@Param("idOrganizacion") long idOrganizacion,
                               @Param("rol") String rol);

    /**
     * Administradores <b>OPERATIVOS</b> del tenant, excluyendo una cuenta
     * (D-S0-37).
     *
     * <p>Contar membresias activas <b>no basta</b>: una cuenta suspendida, sin
     * segundo factor o con un cambio obligatorio pendiente figura en el
     * recuento y <b>no puede gobernar</b>. Operativo es:
     *
     * <pre>
     *   membresia TENANT_ADMIN activa
     *     + credencial activa
     *     + factor ACTIVO
     *     + sin debe_cambiar_contrasena
     *     + sin debe_enrolar_mfa
     * </pre>
     *
     * <p>{@code idCredencialExcluida} es la cuenta sobre la que se va a operar:
     * la pregunta no es "¿cuantos hay?" sino "¿cuantos QUEDARIAN?". Pasar 0 (o
     * un id que no existe) cuenta todos.
     *
     * <p>Es el gemelo en la aplicacion del trigger de V37: la guarda da el
     * mensaje, el trigger da la garantia. Ninguna de las dos sobra.
     */
    @Query("""
            select count(uo)
              from UsuarioOrganizacion uo,
                   CredencialUsuario cu,
                   FactorAutenticacion f
             where uo.organizacionId = :idOrganizacion
               and uo.estado = 'A'
               and uo.rol = com.controllocal.domain.organizacion.UsuarioOrganizacion.ROL_TENANT_ADMIN
               and cu.id = uo.idUsuario
               and cu.organizacionId = uo.organizacionId
               and cu.id <> :idCredencialExcluida
               and cu.estadoAdministrativo = 'A'
               and cu.debeCambiarContrasena = false
               and cu.debeEnrolarMfa = false
               and f.idCredencial = cu.id
               and f.estado = com.controllocal.domain.seguridad.FactorAutenticacion.ACTIVO
            """)
    long contarAdministradoresOperativos(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idCredencialExcluida") long idCredencialExcluida);
}
