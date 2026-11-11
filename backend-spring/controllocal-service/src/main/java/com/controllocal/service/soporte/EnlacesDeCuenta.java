package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.Locale;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito;

/** Única mecánica de los enlaces aprobados: propósito, destino, 30 minutos y consumo CAS. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class EnlacesDeCuenta {
    private static final SecureRandom ALEATORIO=new SecureRandom();
    private static final Duration VIGENCIA=Duration.ofMinutes(30);
    private final TokenAccesoRepository tokens;
    private final NotificadorIdentidad canal;
    private final Clock reloj;
    @Autowired public EnlacesDeCuenta(TokenAccesoRepository tokens,NotificadorIdentidad canal) {
        this(tokens,canal,Clock.systemUTC());
    }
    public EnlacesDeCuenta(TokenAccesoRepository tokens,NotificadorIdentidad canal,Clock reloj) {
        this.tokens=tokens;this.canal=canal;this.reloj=reloj;
    }
    public void emitirAlta(String correo) {
        OffsetDateTime ahora=OffsetDateTime.now(reloj);
        tokens.invalidarAltasVivas(correo,ahora);
        emitir(Proposito.ALTA_CORREO,null,correo,null,ahora);
    }
    public void bloquearAlta(String correo) { tokens.bloquearAltasDeCorreo(correo); }
    public void emitir(Proposito proposito,long cuenta,String correo,String nombre) {
        if(proposito==Proposito.ALTA_CORREO) throw new IllegalArgumentException("El alta no recibe una cuenta preexistente.");
        OffsetDateTime ahora=OffsetDateTime.now(reloj);
        tokens.invalidarVivosDe(cuenta,tipo(proposito),ahora);
        emitir(proposito,cuenta,correo,nombre,ahora);
    }
    private void emitir(Proposito proposito,Long cuenta,String correo,String nombre,OffsetDateTime ahora) {
        byte[] material=new byte[32];ALEATORIO.nextBytes(material);
        String secreto=Base64.getUrlEncoder().withoutPadding().encodeToString(material);
        var token=new TokenAcceso();token.setIdCuenta(cuenta);token.setTipo(tipo(proposito));
        token.setCorreoObjetivo(correo);token.setHashToken(Idempotencia.huellaExacta(secreto));
        token.setCreadoEn(ahora);token.setExpiraEn(ahora.plus(VIGENCIA));tokens.saveAndFlush(token);
        canal.enviar(proposito,new NotificadorIdentidad.Destino(cuenta,nombre,correo),
                new NotificadorIdentidad.TokenEmitido(secreto,token.getExpiraEn()));
    }
    public TokenAcceso buscar(String secreto,Proposito proposito) {
        if(secreto==null||!secreto.matches("[A-Za-z0-9_-]{43}")) throw invalido();
        return tokens.buscarPorHashYTipo(Idempotencia.huellaExacta(secreto),tipo(proposito))
                .filter(t->t.vigenteEn(OffsetDateTime.now(reloj))).orElseThrow(EnlacesDeCuenta::invalido);
    }
    /** El reloj definitivo es el de la base en el UPDATE, incluso tras esperar un candado. */
    public void consumir(TokenAcceso token,Proposito proposito) {
        if(token.getId()==null||tokens.consumirEnlace(token.getId(),tipo(proposito))!=1) throw invalido();
    }
    public static String tipo(Proposito proposito) {
        return switch(proposito) {
            case ALTA_CORREO->TokenAcceso.TIPO_ALTA_CORREO;
            case RECUPERACION->TokenAcceso.TIPO_RECUPERACION;
            case CAMBIO_CORREO->TokenAcceso.TIPO_CAMBIAR_CORREO;
        };
    }
    public static String correo(String valor) {
        if(valor==null) throw new ReglaNegocioException("El correo es obligatorio.");
        String correo=valor.trim().toLowerCase(Locale.ROOT);
        int arroba=correo.indexOf('@');
        if(correo.length()>320||arroba<=0||arroba!=correo.lastIndexOf('@')||arroba==correo.length()-1
                ||correo.chars().anyMatch(Character::isWhitespace)||correo.chars().anyMatch(Character::isISOControl))
            throw new ReglaNegocioException("Ingresa un correo valido.");
        return correo;
    }
    public static ReglaNegocioException invalido() {
        return new ReglaNegocioException("El enlace no es valido o ya fue utilizado.");
    }
}
