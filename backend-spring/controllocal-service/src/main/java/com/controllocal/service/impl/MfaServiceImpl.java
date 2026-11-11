package com.controllocal.service.impl;
import com.controllocal.domain.seguridad.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.MfaService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
/** Único escritor del factor personal. Orden: Cuenta WRITE, luego factor/código/token. */
@Service
public class MfaServiceImpl implements MfaService {
 private static final SecureRandom RANDOM=new SecureRandom();
 private final FactorAutenticacionRepository factores;
 private final CodigoRespaldoMfaRepository codigos;
 private final CuentaAccesoRepository cuentas;
 private final TokenAccesoRepository tokens;
 private final UsuarioOrganizacionRepository miembros;
 private final OrganizacionRepository organizaciones;
 private final CifradoSecretos cifrado;
 private final GobiernoOperativo gobierno;
 private final BloqueoMfa bloqueo;
 private final EventosSeguridad auditoria;
 public MfaServiceImpl(FactorAutenticacionRepository factores,CodigoRespaldoMfaRepository codigos,
   CuentaAccesoRepository cuentas,TokenAccesoRepository tokens,UsuarioOrganizacionRepository miembros,
   OrganizacionRepository organizaciones,CifradoSecretos cifrado,GobiernoOperativo gobierno,
   BloqueoMfa bloqueo,EventosSeguridad auditoria) {
  this.factores=factores;this.codigos=codigos;this.cuentas=cuentas;this.tokens=tokens;this.miembros=miembros;
  this.organizaciones=organizaciones;this.cifrado=cifrado;this.gobierno=gobierno;this.bloqueo=bloqueo;this.auditoria=auditoria;
 }
 @Override @Transactional(readOnly=true)
 public EstadoFactor estado(long idCuenta) {
  var c=cuenta(idCuenta,false);var activo=factores.buscarActivo(c.getId());
  return new EstadoFactor(activo.isPresent(),c.isDebeEnrolarMfa(),activo.map(f->codigos.contarDisponibles(f.getId())).orElse(0L),
   activo.map(FactorAutenticacion::getActivadoEn).orElse(null));
 }
 @Override @Transactional
 public Enrolamiento iniciar(long idCuenta) {
  var c=cuenta(idCuenta,true);
  if(factores.buscarActivo(idCuenta).isPresent()) throw new ReglaNegocioException("Ya tienes un segundo factor activo. Para cambiarlo, revocalo primero con tu contrasena y un codigo vigente.");
  factores.deleteAll(factores.pendientesDe(idCuenta));byte[] secreto=Totp.secretoNuevo();var material=cifrado.cifrar(secreto);
  var f=new FactorAutenticacion();f.setIdCuenta(idCuenta);f.setTipo(FactorAutenticacion.TIPO_TOTP);
  f.setSecretoCifrado(material.criptograma());f.setNonce(material.nonce());f.setVersionClave(material.version());
  f.setAlgoritmo(Totp.ALGORITMO);f.setDigitos((short)Totp.DIGITOS);f.setPeriodo((short)Totp.PERIODO_SEGUNDOS);
  f.setEstado(FactorAutenticacion.PENDIENTE);factores.save(f);
  return new Enrolamiento(Base32.codificar(secreto),Totp.uri("BROX",c.getCorreoPropio(),secreto));
 }
 @Override @Transactional
 public List<String> confirmar(long idCuenta,String codigo) {
  var c=cuenta(idCuenta,true);OffsetDateTime ahora=OffsetDateTime.now();
  var f=factores.pendientesDe(idCuenta).stream().filter(x->!x.pendienteCaducado(ahora)).findFirst().orElseThrow(ErrorMfaException::enrolamientoInvalido);
  var prueba=Totp.validar(descifrar(f),codigo,Instant.now());if(!prueba.valido())throw ErrorMfaException.codigoInvalido();
  f.setEstado(FactorAutenticacion.ACTIVO);f.setActivadoEn(ahora);f.setUltimoPaso(prueba.paso());f.setUltimoUsoEn(ahora);factores.save(f);
  var visibles=generarCodigos(f.getId());c.setDebeEnrolarMfa(false);c.setSesionesInvalidasDesde(ahora);cuentas.save(c);
  for(long org:miembros.gobiernosActualesDeCuenta(idCuenta,LocalDate.now(ZoneId.of("America/Lima"))))organizaciones.exigirMfaDeGobierno(org);
  exito("MFA_ACTIVADO",idCuenta,null,null);return visibles;
 }
 @Override @Transactional
 public List<String> regenerarCodigos(long idCuenta,char[] contrasena,String codigo) {
  var c=cuenta(idCuenta,true);var f=exigirFactor(c);exigirContrasenaYCodigo(c,f,contrasena,codigo);
  codigos.borrarDe(f.getId());var visibles=generarCodigos(f.getId());exito("MFA_CODIGOS_REGENERADOS",idCuenta,null,null);return visibles;
 }
 @Override @Transactional
 public void revocarPropio(long idCuenta,char[] contrasena,String codigo,String ip,String ua) {
  var c=cuenta(idCuenta,true);var f=exigirFactor(c);exigirContrasenaYCodigo(c,f,contrasena,codigo);
  for(long org:miembros.gobiernosActualesDeCuenta(idCuenta,LocalDate.now(ZoneId.of("America/Lima"))))gobierno.exigirQueQuedeGobierno(org,idCuenta,"revocar tu segundo factor");
  revocar(f,c);exito("MFA_REVOCADO",idCuenta,ip,ua);
 }
 @Override @Transactional
 public void comprobarFactorPropio(long idCuenta,String codigo) {
  var c=cuenta(idCuenta,true);var activo=factores.buscarActivo(idCuenta);if(activo.isEmpty())return;
  int espera=bloqueo.esperaExigida(Long.toString(idCuenta));if(espera>0)throw ErrorMfaException.limiteIntentos(espera);
  try {var r=intentar(activo.get(),c,codigo,OffsetDateTime.now());
   bloqueo.registrar(Long.toString(idCuenta),true,null,null,null);
   exito(r.porCodigoRespaldo()?"MFA_CODIGO_RESPALDO_USADO":"MFA_OK",idCuenta,null,null);
  } catch(ErrorMfaException e) {
   bloqueo.registrar(Long.toString(idCuenta),false,null,null,null);
   auditoria.registrar("MFA_FALLIDO","FALLO",contexto(idCuenta,null,null));throw e;
  }
 }
 @Override @Transactional(readOnly=true)
 public boolean exigeSegundoFactor(long idCuenta) {cuenta(idCuenta,false);return factores.buscarActivo(idCuenta).isPresent();}
 @Override @Transactional
 public Desafio emitirDesafio(long idCuenta) {
  cuenta(idCuenta,true);OffsetDateTime ahora=OffsetDateTime.now();tokens.invalidarVivosDe(idCuenta,TokenAcceso.TIPO_DESAFIO_MFA,ahora);
  byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String ref=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var t=new TokenAcceso();t.setIdCuenta(idCuenta);t.setTipo(TokenAcceso.TIPO_DESAFIO_MFA);t.setHashToken(huella(ref));
  t.setCreadoEn(ahora);t.setExpiraEn(ahora.plusMinutes(5));tokens.save(t);return new Desafio(ref,t.getExpiraEn());
 }
 @Override @Transactional
 public Verificacion verificarDesafio(String desafio,String codigo,String ip,String ua) {
  if(desafio==null||desafio.isBlank())throw ErrorMfaException.desafioInvalido();
  if(codigo==null||codigo.isBlank())throw ErrorMfaException.codigoInvalido();
  var t=tokens.buscarPorHashYTipo(huella(desafio),TokenAcceso.TIPO_DESAFIO_MFA).orElseThrow(ErrorMfaException::desafioInvalido);
  if(t.getIdCuenta()==null)throw ErrorMfaException.desafioInvalido();
  var c=cuenta(t.getIdCuenta(),true);OffsetDateTime ahora=OffsetDateTime.now();exigirVigente(t,ahora);
  var actual=cuentas.accesoActual(c.getId()).orElseThrow(ErrorMfaException::desafioInvalido);
  if(actual.sesionesInvalidasDesde()!=null&&!t.getCreadoEn().isAfter(actual.sesionesInvalidasDesde()))throw ErrorMfaException.desafioInvalido();
  int espera=bloqueo.esperaExigida(Long.toString(c.getId()));if(espera>0)throw ErrorMfaException.limiteIntentos(espera);
  var f=factores.buscarActivo(c.getId()).orElseThrow(ErrorMfaException::desafioInvalido);Verificacion r;
  try {r=intentar(f,c,codigo,ahora);}catch(ErrorMfaException e){
   bloqueo.anotarFalloDeDesafio(t.getId());bloqueo.registrar(Long.toString(c.getId()),false,null,ip,ua);
   auditoria.registrar("MFA_FALLIDO","FALLO",contexto(c.getId(),ip,ua));throw e;
  }
  if(tokens.consumirDesafio(t.getId())!=1){
   if(!t.getExpiraEn().isAfter(OffsetDateTime.now()))throw ErrorMfaException.desafioVencido();
   throw ErrorMfaException.desafioConsumido();
  }
  bloqueo.registrar(Long.toString(c.getId()),true,null,ip,ua);exito("MFA_OK",c.getId(),ip,ua);
  if(r.porCodigoRespaldo())exito("MFA_CODIGO_RESPALDO_USADO",c.getId(),ip,ua);return r;
 }
 @Override @Transactional
 public boolean revocarPorRecuperacion(long idCuenta) {
  var c=cuentas.bloquearCambioAcceso(idCuenta).orElseThrow(()->new NoEncontradoException("Cuenta"));var activo=factores.buscarActivo(idCuenta);
  if(activo.isEmpty()&&c.isDebeEnrolarMfa())return false;
  if(activo.isPresent())revocar(activo.get(),c);
  else {c.setDebeEnrolarMfa(true);c.setSesionesInvalidasDesde(OffsetDateTime.now());cuentas.save(c);}return true;
 }
 private CuentaAcceso cuenta(long id,boolean escritura) {
  var c=(escritura?cuentas.bloquearCambioAcceso(id):cuentas.findById(id)).orElseThrow(()->new NoEncontradoException("Cuenta"));
  if(!c.autenticable())throw new CredencialesInvalidasException();return c;
 }
 private Verificacion intentar(FactorAutenticacion f,CuentaAcceso c,String codigo,OffsetDateTime ahora) {
  var totp=Totp.validar(descifrar(f),codigo,Instant.now());
  if(totp.valido()) {
   if(factores.consumirPaso(f.getId(),totp.paso(),ahora)!=1)throw ErrorMfaException.codigoReutilizado();
   return new Verificacion(c.getId(),false,codigos.contarDisponibles(f.getId()));
  }
  var partes=CodigosRespaldo.partir(codigo);if(!partes.completo())throw ErrorMfaException.codigoInvalido();
  var respaldo=codigos.buscarDisponible(f.getId(),partes.identificador());
  if(respaldo.isEmpty()||!PasswordHasher.verificar(partes.secreto().toCharArray(),respaldo.get().getHashSecreto())
    ||codigos.consumir(respaldo.get().getId(),ahora)!=1)throw ErrorMfaException.codigoInvalido();
  return new Verificacion(c.getId(),true,codigos.contarDisponibles(f.getId()));
 }
 private void exigirContrasenaYCodigo(CuentaAcceso c,FactorAutenticacion f,char[] contrasena,String codigo) {
  if(contrasena==null||contrasena.length==0||!PasswordHasher.verificar(contrasena,c.getContrasenaHash()))
   throw new ReglaNegocioException("La contrasena actual no es correcta.");
  var t=Totp.validar(descifrar(f),codigo,Instant.now());if(!t.valido())throw ErrorMfaException.codigoInvalido();
  if(factores.consumirPaso(f.getId(),t.paso(),OffsetDateTime.now())!=1)throw ErrorMfaException.codigoReutilizado();
 }
 private FactorAutenticacion exigirFactor(CuentaAcceso c){return factores.buscarActivo(c.getId()).orElseThrow(()->new ReglaNegocioException("No tienes un segundo factor activo."));}
 private void revocar(FactorAutenticacion f,CuentaAcceso c){OffsetDateTime ahora=OffsetDateTime.now();f.setEstado(FactorAutenticacion.REVOCADO);f.setRevocadoEn(ahora);
  factores.save(f);codigos.borrarDe(f.getId());c.setDebeEnrolarMfa(true);c.setSesionesInvalidasDesde(ahora);cuentas.save(c);}
 private List<String> generarCodigos(long idFactor){List<String> r=new ArrayList<>();for(var g:CodigosRespaldo.generar()){
  var c=new CodigoRespaldoMfa();c.setIdFactor(idFactor);c.setIdentificador(g.identificador());c.setHashSecreto(g.hash());codigos.save(c);r.add(g.visible());}return r;}
 private byte[] descifrar(FactorAutenticacion f){return cifrado.descifrar(f.getSecretoCifrado(),f.getNonce(),f.getVersionClave());}
 private static void exigirVigente(TokenAcceso t,OffsetDateTime ahora){
  if(t.getUsadoEn()!=null||TokenAcceso.CONSUMIDO.equals(t.getEstado()))throw ErrorMfaException.desafioConsumido();
  if(TokenAcceso.AGOTADO.equals(t.getEstado()))throw ErrorMfaException.desafioAgotado();
  if(t.getInvalidadoEn()!=null||TokenAcceso.REVOCADO.equals(t.getEstado()))throw ErrorMfaException.desafioInvalido();
  if(!t.getExpiraEn().isAfter(ahora))throw ErrorMfaException.desafioVencido();
 }
 private static EventosSeguridad.Contexto contexto(long cuenta,String ip,String ua){return new EventosSeguridad.Contexto(null,null,cuenta,null,ip,ua);}
 private void exito(String tipo,long cuenta,String ip,String ua){auditoria.registrarEnTransaccion(tipo,"OK",contexto(cuenta,ip,ua),Map.of());}
 private static String huella(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
