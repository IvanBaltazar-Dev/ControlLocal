package com.controllocal.persistence.repositorio;
import com.controllocal.domain.seguridad.CuentaAcceso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CuentaAccesoRepository extends JpaRepository<CuentaAcceso,Long> {
 boolean existsByNombreUsuario(String nombreUsuario);
 @Query("select c from CuentaAcceso c where lower(c.correoPropio)=lower(trim(:correo))")
 Optional<CuentaAcceso> buscarPorCorreo(@Param("correo") String correo);
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select c from CuentaAcceso c where c.id=:id")
 Optional<CuentaAcceso> bloquearLecturaContexto(@Param("id") long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CuentaAcceso c where c.id=:id")
 Optional<CuentaAcceso> bloquearCambioAcceso(@Param("id") long id);
 @Query("""
  select new com.controllocal.persistence.repositorio.CuentaAccesoFila(c.id,c.nombreUsuario,c.correoPropio,
    c.correoConfirmadoEn,c.nombreProfesionalDeclarado,c.estado,c.sesionesInvalidasDesde,c.debeCambiarContrasena,c.debeEnrolarMfa)
  from CuentaAcceso c where c.id=:id
  """)
 Optional<CuentaAccesoFila> vista(@Param("id") long id);
 @Query("""
  select new com.controllocal.persistence.repositorio.AccesoAutenticacionFila(c.id,c.correoPropio,c.correoConfirmadoEn,
    c.nombreUsuario,c.contrasenaHash,c.sesionesInvalidasDesde) from CuentaAcceso c where c.id=:id
  """)
 Optional<AccesoAutenticacionFila> accesoActual(@Param("id") long id);
 @Query("select c from CuentaAcceso c,UsuarioOrganizacion u where u.idUsuario=c.id and u.organizacionId=:org and u.idPersona=:persona and u.estado='A'")
 List<CuentaAcceso> cuentasDePersonaLocal(@Param("org") long org,@Param("persona") long persona);
 @Query("select u.idPersona,c from CuentaAcceso c,UsuarioOrganizacion u where u.idUsuario=c.id and u.organizacionId=:org and u.idPersona in :personas")
 List<Object[]> cuentasDePersonasLocales(@Param("org") long org,@Param("personas") Collection<Long> personas);
 @Query("""
  select new com.controllocal.persistence.repositorio.CuentaDeGobiernoFila(p.id,c.id,p.nombresORazonSocial,c.nombreUsuario,
    u.rol,u.administrar,case when u.estado='A' then true else false end,u.id,u.version)
  from CuentaAcceso c,UsuarioOrganizacion u,Persona p
  where u.idUsuario=c.id and u.organizacionId=:org
    and p.id=u.idPersona and p.organizacionId=u.organizacionId
  order by p.nombresORazonSocial
  """)
 List<CuentaDeGobiernoFila> cuentasDeGobierno(@Param("org") long org);
}
