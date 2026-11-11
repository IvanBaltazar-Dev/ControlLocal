package com.controllocal.domain.seguridad;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
/** Cuenta personal BROX global. Única autoridad de credenciales/canal propio; independiente de Persona y organización. */
@Entity @Table(name="cuenta_acceso")
public class CuentaAcceso {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_cuenta_acceso") private Long id;
 @Column(name="nombre_usuario",nullable=false,unique=true,length=60) private String nombreUsuario;
 @Column(name="correo_propio",nullable=false,unique=true,length=320) private String correoPropio;
 @Column(name="correo_confirmado_en") private Instant correoConfirmadoEn;
 @Column(name="nombre_profesional_declarado",length=150) private String nombreProfesionalDeclarado;
 @Column(name="contrasena_hash",nullable=false,length=255) private String contrasenaHash;
 @Column(name="algoritmo_hash",nullable=false,length=20) private String algoritmoHash="pbkdf2";
 @Column(name="password_actualizada_en") private OffsetDateTime passwordActualizadaEn;
 @Column(name="estado",nullable=false,length=1) private String estado="A";
 @Column(name="debe_cambiar_contrasena",nullable=false) private boolean debeCambiarContrasena;
 @Column(name="debe_enrolar_mfa",nullable=false) private boolean debeEnrolarMfa;
 @Column(name="sesiones_invalidas_desde") private OffsetDateTime sesionesInvalidasDesde;
 public Long getId(){return id;} public void setId(Long v){id=v;}
 public String getNombreUsuario(){return nombreUsuario;} public void setNombreUsuario(String v){nombreUsuario=v;}
 public String getCorreoPropio(){return correoPropio;} public void setCorreoPropio(String v){correoPropio=v;}
 public Instant getCorreoConfirmadoEn(){return correoConfirmadoEn;} public void setCorreoConfirmadoEn(Instant v){correoConfirmadoEn=v;}
 public String getNombreProfesionalDeclarado(){return nombreProfesionalDeclarado;} public void setNombreProfesionalDeclarado(String v){nombreProfesionalDeclarado=v;}
 public String getContrasenaHash(){return contrasenaHash;} public void setContrasenaHash(String v){contrasenaHash=v;}
 public String getAlgoritmoHash(){return algoritmoHash;} public void setAlgoritmoHash(String v){algoritmoHash=v;}
 public OffsetDateTime getPasswordActualizadaEn(){return passwordActualizadaEn;} public void setPasswordActualizadaEn(OffsetDateTime v){passwordActualizadaEn=v;}
 public String getEstado(){return estado;} public void setEstado(String v){if(!"A".equals(v)&&!"I".equals(v))throw new IllegalArgumentException("Estado de cuenta inválido");estado=v;}
 public boolean isDebeCambiarContrasena(){return debeCambiarContrasena;} public void setDebeCambiarContrasena(boolean v){debeCambiarContrasena=v;}
 public boolean isDebeEnrolarMfa(){return debeEnrolarMfa;} public void setDebeEnrolarMfa(boolean v){debeEnrolarMfa=v;}
 public OffsetDateTime getSesionesInvalidasDesde(){return sesionesInvalidasDesde;}
 public void setSesionesInvalidasDesde(OffsetDateTime v){sesionesInvalidasDesde=v==null?null:v.truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
 public boolean autenticable(){return "A".equals(estado)&&correoConfirmadoEn!=null;}
}
