package com.controllocal.service.soporte;
import com.controllocal.domain.comun.EstadosDominio.*;
import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
/** Pertenencia y datos profesionales locales. Nunca crea ni modifica credenciales personales. */
@Component
public class UsuariosInternos {
 private final PersonaRolRepository roles;
 private final CuentaAccesoRepository cuentas;
 private final UsuarioOrganizacionRepository miembros;
 private final SesionAccesoRepository sesiones;
 private final Clock reloj;
 @org.springframework.beans.factory.annotation.Autowired
 public UsuariosInternos(PersonaRolRepository roles,CuentaAccesoRepository cuentas,
   UsuarioOrganizacionRepository miembros,SesionAccesoRepository sesiones) {
  this(roles,cuentas,miembros,sesiones,Clock.systemUTC());
 }
 UsuariosInternos(PersonaRolRepository roles,CuentaAccesoRepository cuentas,
   UsuarioOrganizacionRepository miembros,SesionAccesoRepository sesiones,Clock reloj) {
  this.roles=roles;this.cuentas=cuentas;this.miembros=miembros;this.sesiones=sesiones;
  this.reloj=reloj;
 }
 public record Vinculacion(PersonaRol rolOperativo,UsuarioOrganizacion pertenencia) { }
 /** Precondición: invitación vigente aceptada por cuenta autenticada/correo confirmado; valores del snapshot, no del cuerpo. */
 @Transactional(propagation=Propagation.MANDATORY)
 public Vinculacion vincularPertenenciaAceptada(long org,long idCuenta,Persona personaLocal,TipoRol principal,
   boolean administrar) {
  var c=cuentas.bloquearCambioAcceso(idCuenta).orElseThrow(()->new NoEncontradoException("Cuenta"));
  if(!c.autenticable()||personaLocal==null||personaLocal.getId()==null||!Objects.equals(personaLocal.getOrganizacionId(),org)
    ||(principal!=TipoRol.AGENTE&&principal!=TipoRol.BROKER&&!(principal==null&&administrar)))throw new AccesoNoAutorizadoException();
  if(miembros.contextosDeCuenta(idCuenta).stream().anyMatch(m->"A".equals(m.estado())))
   throw new ReglaNegocioException("La cuenta ya tiene una pertenencia organizacional activa.");
  PersonaRol operativo=null;
  OffsetDateTime aceptadaEn=OffsetDateTime.ofInstant(reloj.instant(),ZoneOffset.UTC);
  LocalDate vigenteDesde=aceptadaEn.atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate();
  if(principal!=null) {
   // El rol histórico contrario no concede ejercicio: manda el principal de la nueva pertenencia.
   operativo=roles.buscarVigente(personaLocal.getId(),principal).orElseGet(()->{
    var r=Personas.nuevoRol(org,personaLocal,principal);r.setVigenciaDesde(vigenteDesde);return roles.save(r);
   });
  }
  var m=miembros.buscarPorCuenta(org,idCuenta).orElseGet(UsuarioOrganizacion::new);
  if(m.getId()!=null&&!Objects.equals(m.getIdPersona(),personaLocal.getId()))throw new AccesoNoAutorizadoException();
  if(m.getId()!=null)sesiones.revocarDeMembresia(m.getId());
  m.setAltaRealEn(aceptadaEn);m.setBajaRealEn(null);
  m.setOrganizacionId(org);m.setIdUsuario(idCuenta);m.setIdPersona(personaLocal.getId());m.setEstado("A");
  m.setNombreVisible(personaLocal.getNombresORazonSocial());m.setRol(principal==null?null:principal.name());m.setAdministrar(administrar);
  miembros.save(m);if(administrar)concederGobierno(org,personaLocal,m,vigenteDesde);return new Vinculacion(operativo,m);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void concederGobierno(long org,Persona persona,UsuarioOrganizacion miembro,LocalDate vigenteDesde) {
  if(!Objects.equals(persona.getOrganizacionId(),org)||!Objects.equals(miembro.getOrganizacionId(),org)
    ||!Objects.equals(miembro.getIdPersona(),persona.getId())||vigenteDesde==null)throw new AccesoNoAutorizadoException();
  if(miembro.getId()!=null)sesiones.revocarDeMembresia(miembro.getId());
  miembro.setAdministrar(true);miembros.save(miembro);
  if(roles.buscarVigente(persona.getId(),TipoRol.ADMIN).isEmpty()) {
   var r=Personas.nuevoRol(org,persona,TipoRol.ADMIN);r.setVigenciaDesde(vigenteDesde);roles.save(r);
  }
 }
 public Map<Long,CuentaAcceso> cuentasPorPersona(long org,Collection<Long> personas) {
  if(personas.isEmpty())return Map.of();Map<Long,CuentaAcceso> r=new HashMap<>();
  for(Object[] f:cuentas.cuentasDePersonasLocales(org,personas))if(r.put((Long)f[0],(CuentaAcceso)f[1])!=null)
   throw new ReglaNegocioException("La persona tiene mas de una cuenta vinculada.");return r;
 }
 public CuentaAcceso cuenta(long org,long persona) {
  var m=miembros.dePersona(org,persona);if(m.size()!=1)throw new NoEncontradoException("Usuario");
  return cuentas.findById(m.getFirst().getIdUsuario()).orElseThrow(()->new NoEncontradoException("Cuenta"));
 }
 /** Un rol histórico contrario no se presenta como activo por la vigencia de otra función. */
 public Map<Long,String> estadosLocalesDelRol(long org,Collection<Long> personas,String principal) {
  if(!Set.of("AGENTE","BROKER").contains(principal))throw new IllegalArgumentException("Principal no reconocido.");
  if(personas.isEmpty())return Map.of();Map<Long,String> r=new HashMap<>();
  for(Object[] f:miembros.estadosDePersonasDelRol(org,personas,principal))
   if(r.put((Long)f[0],(String)f[1])!=null)throw new ReglaNegocioException("La persona tiene mas de una cuenta vinculada.");
  return r;
 }
 public String estadoLocalDelRol(long org,long persona,String principal){return estadosLocalesDelRol(org,List.of(persona),principal).get(persona);}
 /** Atribución declarada en la pertenencia local; no reemplaza la autorización del contexto. */
 public Map<Long,Boolean> administracionesLocalesPorPersona(long org,Collection<Long> personas) {
  if(personas.isEmpty())return Map.of();Map<Long,Boolean> r=new HashMap<>();
  for(Object[] f:miembros.administracionesDePersonas(org,personas))
   if(r.put((Long)f[0],(Boolean)f[1])!=null)throw new ReglaNegocioException("La persona tiene mas de una cuenta vinculada.");
  return r;
 }
 public boolean administracionLocal(long org,long persona){return Boolean.TRUE.equals(administracionesLocalesPorPersona(org,List.of(persona)).get(persona));}
 public static boolean vacio(String v){return v==null||v.isBlank();}
 public static String tipoPersonaO(String v,String defecto){return codigo(v,defecto,Set.of("N","J"));}
 public static String tipoDocumentoO(String v,String defecto){return codigo(v,defecto,Set.of("D","R","C","P"));}
 public static String estadoAdministrativoO(String v,String defecto){return codigo(v,defecto,Set.of("A","I"));}
 public static String estadoOperativoO(String v,String defecto){return codigo(v,defecto,Set.of("D","L","N"));}
 private static String codigo(String v,String defecto,Set<String> permitidos){String r=vacio(v)?defecto:v.trim();if(!permitidos.contains(r))throw new ReglaNegocioException("Valor invalido: "+r);return r;}
}
