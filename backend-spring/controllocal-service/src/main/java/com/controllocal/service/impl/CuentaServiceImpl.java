package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.SesionAccesoRepository;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito.ALTA_CORREO;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito.CAMBIO_CORREO;

/** Casos de cuenta global propios: no reciben un sujeto organizacional elegido por el cliente. */
@Service
@Transactional
public class CuentaServiceImpl implements CuentaService {
    private enum GestionPropia { CONSULTA_CUENTA, CAMBIO_CONTRASENA, ENROLAMIENTO_MFA, ACCESO_COMPLETO }
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final ContrasenaService contrasenas;
    private final MfaService mfa;
    private final EnlacesDeCuenta enlaces;
    private final TokenAccesoRepository tokens;
    private final SesionAccesoRepository sesiones;
    private final NotificadorIdentidad canal;
    private final EventosSeguridad auditoria;
    private final EntityManager entidades;
    public CuentaServiceImpl(ContextosService contextos,CuentaAccesoRepository cuentas,ContrasenaService contrasenas,
            MfaService mfa,EnlacesDeCuenta enlaces,TokenAccesoRepository tokens,SesionAccesoRepository sesiones,
            NotificadorIdentidad canal,EventosSeguridad auditoria,EntityManager entidades) {
        this.contextos=contextos;this.cuentas=cuentas;this.contrasenas=contrasenas;this.mfa=mfa;
        this.enlaces=enlaces;this.tokens=tokens;this.sesiones=sesiones;this.canal=canal;
        this.auditoria=auditoria;this.entidades=entidades;
    }
    @Override public void solicitarAlta(String valor) {
        String correo=EnlacesDeCuenta.correo(valor);
        enlaces.bloquearAlta(correo);
        // Acuse uniforme: ni crea otra cuenta ni publica que el correo ya existe.
        if(cuentas.buscarPorCorreo(correo).isPresent()) return;
        enlaces.emitirAlta(correo);
        auditar("CUENTA_ALTA_SOLICITADA",null,Map.of("entregaAlTitular",canal.entregaAlTitular()));
    }
    @Override public void confirmarAlta(String secreto,String nombreUsuario,String nombreProfesional,char[] contrasena) {
        var prueba=enlaces.buscar(secreto,ALTA_CORREO);
        String correo=EnlacesDeCuenta.correo(prueba.getCorreoObjetivo());
        enlaces.bloquearAlta(correo);
        if(cuentas.buscarPorCorreo(correo).isPresent()) throw EnlacesDeCuenta.invalido();
        String usuario=nombreUsuario(nombreUsuario);
        if(cuentas.existsByNombreUsuario(usuario)) throw new ConflictoException("El nombre de usuario no esta disponible.");
        String nombre=nombreProfesional(nombreProfesional);
        PoliticaContrasenas.exigirValida(contrasena,usuario);
        enlaces.consumir(prueba,ALTA_CORREO);
        var cuenta=new CuentaAcceso();cuenta.setCorreoPropio(correo);cuenta.setCorreoConfirmadoEn(Instant.now());
        cuenta.setNombreUsuario(usuario);cuenta.setNombreProfesionalDeclarado(nombre);
        cuenta.setContrasenaHash(PasswordHasher.hash(contrasena));cuenta.setAlgoritmoHash("pbkdf2");
        cuenta.setPasswordActualizadaEn(OffsetDateTime.now());cuentas.saveAndFlush(cuenta);
        // Cuenta y prueba confirman juntas; no se crea sesión ni pertenencia por verificar correo.
        auditar("CUENTA_ALTA_CONFIRMADA",cuenta.getId(),Map.of());
    }
    @Override public FichaCuenta consultarPropia(String sid,long revision) {
        long id=identidad(sid,revision,GestionPropia.CONSULTA_CUENTA);
        var c=cuentas.vista(id).orElseThrow(()->new NoEncontradoException("Cuenta"));
        return new FichaCuenta(c.idCuenta(),c.nombreUsuario(),c.correoPropio(),c.correoConfirmadoEn(),
                c.nombreProfesionalDeclarado(),c.debeCambiarContrasena(),c.debeEnrolarMfa());
    }
    @Override public void cambiarContrasena(String sid,long revision,char[] actual,char[] nueva) {
        contrasenas.cambiar(identidad(sid,revision,GestionPropia.CAMBIO_CONTRASENA),actual,nueva);
    }
    @Override public void solicitarCambioCorreo(String sid,long revision,String valor,char[] actual,String codigoMfa) {
        String nuevo=EnlacesDeCuenta.correo(valor);long id=identidad(sid,revision,GestionPropia.ACCESO_COMPLETO);
        contrasenas.exigirActual(id,actual);mfa.comprobarFactorPropio(id,codigoMfa);
        var cuenta=cuentaBloqueada(id);
        if(nuevo.equals(cuenta.getCorreoPropio())) throw new ReglaNegocioException("El correo nuevo debe ser diferente.");
        if(cuentas.buscarPorCorreo(nuevo).isPresent()) throw new ConflictoException("El correo nuevo no esta disponible.");
        enlaces.emitir(CAMBIO_CORREO,id,nuevo,cuenta.getNombreProfesionalDeclarado());
        auditar("CORREO_CAMBIO_SOLICITADO",id,Map.of("entregaAlTitular",canal.entregaAlTitular()));
    }
    @Override public void confirmarCambioCorreo(String secreto) {
        var prueba=enlaces.buscar(secreto,CAMBIO_CORREO);
        if(prueba.getIdCuenta()==null) throw EnlacesDeCuenta.invalido();
        var cuenta=cuentaBloqueada(prueba.getIdCuenta());
        if(cuenta.getSesionesInvalidasDesde()!=null&&!prueba.getCreadoEn().isAfter(cuenta.getSesionesInvalidasDesde()))
            throw EnlacesDeCuenta.invalido();
        String nuevo=EnlacesDeCuenta.correo(prueba.getCorreoObjetivo());
        if(cuentas.buscarPorCorreo(nuevo).isPresent()) throw EnlacesDeCuenta.invalido();
        enlaces.consumir(prueba,CAMBIO_CORREO);
        OffsetDateTime ahora=OffsetDateTime.now();cuenta.setCorreoPropio(nuevo);
        cuenta.setCorreoConfirmadoEn(ahora.toInstant());cuenta.setSesionesInvalidasDesde(ahora);
        cuentas.saveAndFlush(cuenta);sesiones.revocarDeCuenta(cuenta.getId());
        tokens.invalidarVivosDe(cuenta.getId(),TokenAcceso.TIPO_RECUPERACION,ahora);
        tokens.invalidarVivosDe(cuenta.getId(),TokenAcceso.TIPO_DESAFIO_MFA,ahora);
        tokens.invalidarVivosDe(cuenta.getId(),TokenAcceso.TIPO_CAMBIAR_CORREO,ahora);
        auditar("CORREO_CAMBIO_CONFIRMADO",cuenta.getId(),Map.of());
        auditar("SESIONES_INVALIDADAS",cuenta.getId(),Map.of());
    }
    @Override public MfaService.EstadoFactor estadoMfa(String sid,long revision) {return mfa.estado(identidad(sid,revision,GestionPropia.ENROLAMIENTO_MFA));}
    @Override public MfaService.Enrolamiento iniciarMfa(String sid,long revision) {return mfa.iniciar(identidad(sid,revision,GestionPropia.ENROLAMIENTO_MFA));}
    @Override public List<String> confirmarMfa(String sid,long revision,String codigo) {return mfa.confirmar(identidad(sid,revision,GestionPropia.ENROLAMIENTO_MFA),codigo);}
    @Override public List<String> regenerarCodigosMfa(String sid,long revision,char[] contrasena,String codigo) {
        return mfa.regenerarCodigos(identidad(sid,revision,GestionPropia.ACCESO_COMPLETO),contrasena,codigo);
    }
    @Override public void revocarMfaPropio(String sid,long revision,char[] contrasena,String codigo,String ip,String agente) {
        mfa.revocarPropio(identidad(sid,revision,GestionPropia.ACCESO_COMPLETO),contrasena,codigo,ip,agente);
    }
    private long identidad(String sid,long revision,GestionPropia gestion) {
        var sesion=contextos.exigirIdentidad(sid,revision);
        var restricciones=sesion.restricciones();
        boolean permitida=switch(gestion) {
            case CONSULTA_CUENTA->true;
            case CAMBIO_CONTRASENA->restricciones.debeCambiarContrasena()||!restricciones.debeEnrolarMfa();
            case ENROLAMIENTO_MFA->!restricciones.debeCambiarContrasena();
            case ACCESO_COMPLETO->!restricciones.debeCambiarContrasena()&&!restricciones.debeEnrolarMfa();
        };
        if(!permitida)throw new AccesoNoAutorizadoException();
        return sesion.identidad().idCuenta();
    }
    private CuentaAcceso cuentaBloqueada(long id) {
        var c=cuentas.bloquearCambioAcceso(id).orElseThrow(()->new NoEncontradoException("Cuenta"));
        entidades.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(!c.autenticable()) throw EnlacesDeCuenta.invalido();
        return c;
    }
    private void auditar(String tipo,Long cuenta,Map<String,?> detalle) {
        auditoria.registrarEnTransaccion(tipo,"OK",new EventosSeguridad.Contexto(null,null,cuenta,null,null,null),detalle);
    }
    private static String nombreUsuario(String valor) {
        if(valor==null||valor.isBlank()) throw new ReglaNegocioException("El nombre de usuario es obligatorio.");
        String nombre=valor.trim();
        if(nombre.length()>60||nombre.chars().anyMatch(Character::isISOControl))
            throw new ReglaNegocioException("El nombre de usuario no es valido.");
        return nombre;
    }
    private static String nombreProfesional(String valor) {
        if(valor==null||valor.isBlank()) return null;
        String nombre=valor.trim();if(nombre.length()>150) throw new ReglaNegocioException("El nombre profesional es demasiado largo.");
        return nombre;
    }
}
