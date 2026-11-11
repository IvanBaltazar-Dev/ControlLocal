package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.seguridad.CredencialPassword;
import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.CredencialPasswordRepository;
import com.controllocal.persistence.repositorio.SesionAccesoRepository;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
import com.controllocal.service.ContrasenaService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito.RECUPERACION;

/** Contraseña y recuperación personales: una cuenta global, ningún operador organizacional. */
@Service
public class ContrasenaServiceImpl implements ContrasenaService {
    private final CuentaAccesoRepository cuentas;
    private final CredencialPasswordRepository historial;
    private final SesionAccesoRepository sesiones;
    private final TokenAccesoRepository tokens;
    private final EnlacesDeCuenta enlaces;
    private final EventosSeguridad auditoria;
    private final EntityManager entidades;
    public ContrasenaServiceImpl(CuentaAccesoRepository cuentas,CredencialPasswordRepository historial,
            SesionAccesoRepository sesiones,TokenAccesoRepository tokens,EnlacesDeCuenta enlaces,
            EventosSeguridad auditoria,EntityManager entidades) {
        this.cuentas=cuentas;this.historial=historial;this.sesiones=sesiones;this.tokens=tokens;
        this.enlaces=enlaces;this.auditoria=auditoria;this.entidades=entidades;
    }
    @Override @Transactional
    public void exigirActual(long idCuenta,char[] actual) { exigirActual(cuentaBloqueada(idCuenta),actual); }
    @Override @Transactional
    public void cambiar(long idCuenta,char[] actual,char[] nueva) {
        var cuenta=cuentaBloqueada(idCuenta);exigirActual(cuenta,actual);
        if(Arrays.equals(actual,nueva)) throw new ReglaNegocioException("La contrasena nueva debe ser distinta de la actual.");
        fijarContrasena(cuenta,nueva);
        auditar("PASSWORD_CAMBIADA",cuenta.getId(),null,null);
    }
    @Override @Transactional
    public void solicitarRecuperacion(String correoPropio,String ip,String agenteUsuario) {
        final String correo;
        try {correo=EnlacesDeCuenta.correo(correoPropio);}
        catch(ReglaNegocioException formatoInvalido) {return;} // Acuse uniforme; no acredita existencia ni emite un enlace.
        var encontrada=cuentas.buscarPorCorreo(correo);
        if(encontrada.isEmpty()) return;
        var cuenta=cuentas.bloquearCambioAcceso(encontrada.get().getId()).orElse(null);
        if(cuenta==null) return;
        entidades.refresh(cuenta,LockModeType.PESSIMISTIC_WRITE);
        if(!cuenta.autenticable()||!correo.equals(cuenta.getCorreoPropio())) return;
        enlaces.emitir(RECUPERACION,cuenta.getId(),cuenta.getCorreoPropio(),cuenta.getNombreProfesionalDeclarado());
        auditar("RECUPERACION_EMITIDA",cuenta.getId(),ip,agenteUsuario);
    }
    @Override @Transactional
    public void canjear(String secreto,char[] nueva,String ip,String agenteUsuario) {
        var token=enlaces.buscar(secreto,RECUPERACION);
        if(token.getIdCuenta()==null) throw EnlacesDeCuenta.invalido();
        var cuenta=cuentaBloqueada(token.getIdCuenta());
        if(!cuenta.getCorreoPropio().equals(token.getCorreoObjetivo())) throw EnlacesDeCuenta.invalido();
        enlaces.consumir(token,RECUPERACION);
        fijarContrasena(cuenta,nueva);
        auditar("RECUPERACION_CANJEADA",cuenta.getId(),ip,agenteUsuario);
        auditar("PASSWORD_RESTABLECIDA",cuenta.getId(),ip,agenteUsuario);
    }
    private CuentaAcceso cuentaBloqueada(long id) {
        var c=cuentas.bloquearCambioAcceso(id).orElseThrow(()->new NoEncontradoException("Cuenta"));
        // El candado por sí solo no refresca una entidad que OEIV ya hubiera gestionado.
        entidades.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(!c.autenticable()) throw new ReglaNegocioException("La cuenta no permite esta operacion.");
        return c;
    }
    private static void exigirActual(CuentaAcceso cuenta,char[] actual) {
        if(actual==null||actual.length==0) throw new ReglaNegocioException("La contrasena actual es obligatoria.");
        if(!PasswordHasher.verificar(actual,cuenta.getContrasenaHash()))
            throw new ReglaNegocioException("La contrasena actual no es correcta.");
    }
    private void fijarContrasena(CuentaAcceso cuenta,char[] nueva) {
        PoliticaContrasenas.exigirValida(nueva,cuenta.getNombreUsuario());
        if(PasswordHasher.verificar(nueva,cuenta.getContrasenaHash())||
                historial.ultimosDe(cuenta.getId(),PageRequest.of(0,PoliticaContrasenas.HISTORIAL)).stream()
                .anyMatch(h->PasswordHasher.verificar(nueva,h.getContrasenaHash())))
            throw new ReglaNegocioException("No puedes reutilizar una de tus ultimas "+PoliticaContrasenas.HISTORIAL+" contrasenas.");
        archivar(cuenta);
        OffsetDateTime ahora=OffsetDateTime.now();
        cuenta.setContrasenaHash(PasswordHasher.hash(nueva));cuenta.setAlgoritmoHash("pbkdf2");
        cuenta.setPasswordActualizadaEn(ahora);cuenta.setDebeCambiarContrasena(false);cuenta.setSesionesInvalidasDesde(ahora);
        cuentas.saveAndFlush(cuenta);
        sesiones.revocarDeCuenta(cuenta.getId());
        tokens.invalidarVivosDe(cuenta.getId(),TokenAcceso.TIPO_RECUPERACION,ahora);
        tokens.invalidarVivosDe(cuenta.getId(),TokenAcceso.TIPO_CAMBIAR_CORREO,ahora);
        tokens.invalidarVivosDe(cuenta.getId(),TokenAcceso.TIPO_DESAFIO_MFA,ahora);
        auditar("SESIONES_INVALIDADAS",cuenta.getId(),null,null);
    }
    private void archivar(CuentaAcceso cuenta) {
        if(cuenta.getContrasenaHash()==null) return;
        var anterior=new CredencialPassword();anterior.setIdCuenta(cuenta.getId());
        anterior.setContrasenaHash(cuenta.getContrasenaHash());anterior.setAlgoritmoHash(cuenta.getAlgoritmoHash());
        anterior.setCreadoEn(OffsetDateTime.now());historial.saveAndFlush(anterior);
        List<Long> conservar=historial.ultimosDe(cuenta.getId(),PageRequest.of(0,PoliticaContrasenas.HISTORIAL))
                .stream().map(CredencialPassword::getId).toList();
        if(!conservar.isEmpty()) historial.podar(cuenta.getId(),conservar);
    }
    private void auditar(String tipo,long cuenta,String ip,String agente) {
        auditoria.registrarEnTransaccion(tipo,"OK",new EventosSeguridad.Contexto(null,null,cuenta,null,ip,agente),Map.of());
    }
}

