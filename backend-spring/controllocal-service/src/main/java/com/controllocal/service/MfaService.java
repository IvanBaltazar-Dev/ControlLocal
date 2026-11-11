package com.controllocal.service;
import java.time.OffsetDateTime;
import java.util.List;
/** Factor personal de Cuenta BROX. IDs sólo derivados por Core, nunca recibidos del cuerpo HTTP. */
public interface MfaService {
 record EstadoFactor(boolean activo,boolean debeEnrolar,long codigosDisponibles,OffsetDateTime activadoEn) {
  public boolean codigosPorAgotarse(){return activo&&codigosDisponibles<=2;}
 }
 record Enrolamiento(String secretoBase32,String uri) { }
 record Desafio(String token,OffsetDateTime expiraEn) { }
 record Verificacion(long idCuenta,boolean porCodigoRespaldo,long codigosRestantes) { }
 EstadoFactor estado(long idCuenta);
 Enrolamiento iniciar(long idCuenta);
 List<String> confirmar(long idCuenta,String codigo);
 List<String> regenerarCodigos(long idCuenta,char[] contrasena,String codigo);
 void revocarPropio(long idCuenta,char[] contrasena,String codigo,String ip,String agenteUsuario);
 void comprobarFactorPropio(long idCuenta,String codigo);
 boolean exigeSegundoFactor(long idCuenta);
 Desafio emitirDesafio(long idCuenta);
 Verificacion verificarDesafio(String desafio,String codigo,String ip,String agenteUsuario);
 /** Exclusivamente concesión interna ya comprobada; sin facultad de TenantAdmin/Broker. */
 boolean revocarPorRecuperacion(long idCuenta);
}
