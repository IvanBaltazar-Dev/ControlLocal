package com.controllocal.service.seguridad;

import com.controllocal.domain.seguridad.VinculoInteraccionSegura;
import com.controllocal.persistence.repositorio.VinculoInteraccionSeguraRepository;
import com.controllocal.service.ContextosService;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * El acceso válido procede de la autoridad de autenticación y se vuelve a
 * validar al entregar el retorno. La correlación no autoriza negocio y nunca
 * convierte una recuperación iniciada en una recuperación terminada.
 */
@Service
@Transactional
public class InicioSeguroServiceImpl implements InicioSeguroService {
    private static final String RECHAZO = "La interacción segura no permite reanudar el acceso.";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final VinculoInteraccionSeguraRepository vinculos;
    private final ContextosService contextos;
    private final Clock reloj;
    private final Supplier<String> referencias;

    @Autowired
    public InicioSeguroServiceImpl(VinculoInteraccionSeguraRepository vinculos,
                                   ContextosService contextos) {
        this(vinculos, contextos, Clock.systemUTC(), InicioSeguroServiceImpl::referenciaNueva);
    }

    InicioSeguroServiceImpl(VinculoInteraccionSeguraRepository vinculos, ContextosService contextos,
                           Clock reloj, Supplier<String> referencias) {
        this.vinculos = vinculos;
        this.contextos = contextos;
        this.reloj = reloj;
        this.referencias = referencias;
    }

    @Override
    public Vinculo iniciar(Proposito proposito, Instant venceEn) {
        Instant ahora = reloj.instant();
        if (proposito != Proposito.ACCESO || venceEn == null || !venceEn.isAfter(ahora)) {
            throw new ReglaNegocioException("El contrato de inicio seguro no es válido.");
        }
        String referencia = referencias.get();
        var vinculo = new VinculoInteraccionSegura();
        vinculo.setHashFlujo(huella(referencia));
        vinculo.setProposito(proposito.name());
        vinculo.setCreadoEn(ahora);
        vinculo.setVenceEn(venceEn);
        vinculos.save(vinculo);
        return new Vinculo(referencia, proposito, venceEn);
    }

    @Override
    public RetornoPreparado completar(String flujoRef, Proposito proposito, SesionVerificada sesion) {
        exigirSesion(sesion);
        Instant ahora = reloj.instant();
        var vinculo = cargarVigente(flujoRef, proposito, ahora);
        // iat y la frontera de creación se comparan en la precisión real del
        // JWT. Un login legítimo del mismo segundo no exige esperar otro;
        // los segundos anteriores siguen sin acreditar este inicio.
        if (vinculo.getCompletadoEn() != null
                || sesion.emitidaEn().isBefore(vinculo.getCreadoEn().truncatedTo(ChronoUnit.SECONDS))) {
            throw rechazo();
        }
        var actor = sesion.actor();
        String retorno = referencias.get();
        vinculo.setOrganizacionId(actor.idOrganizacion());
        vinculo.setIdPersona(actor.idPersona());
        vinculo.setIdRolOperativo(actor.idRolOperativo());
        vinculo.setRolEfectivo(actor.rolEfectivo());
        vinculo.setContextoSesion(sesion.contexto());
        vinculo.setSesionEmitidaEn(sesion.emitidaEn());
        vinculo.setSesionExpiraEn(sesion.expiraEn());
        vinculo.setCompletadoEn(ahora);
        vinculo.setHashRetorno(huella(retorno));
        // Una sesión más corta nunca alarga la vigencia del vínculo original.
        if (sesion.expiraEn().isBefore(vinculo.getVenceEn())) {
            vinculo.setVenceEn(sesion.expiraEn());
        }
        vinculos.save(vinculo);
        return new RetornoPreparado(flujoRef, proposito, retorno, vinculo.getVenceEn());
    }

    @Override
    public RetornoConfirmado consumir(String flujoRef, Proposito proposito, String retornoRef,
                                     SesionVerificada sesion) {
        exigirSesion(sesion);
        Instant ahora = reloj.instant();
        var vinculo = cargarVigente(flujoRef, proposito, ahora);
        var actor = sesion.actor();
        if (vinculo.getCompletadoEn() == null || vinculo.getUsadoEn() != null
                || !Objects.equals(vinculo.getOrganizacionId(), actor.idOrganizacion())
                || !Objects.equals(vinculo.getIdPersona(), actor.idPersona())
                || !Objects.equals(vinculo.getIdRolOperativo(), actor.idRolOperativo())
                || !Objects.equals(vinculo.getRolEfectivo(), actor.rolEfectivo())
                || !Objects.equals(vinculo.getContextoSesion(), sesion.contexto())
                || !Objects.equals(vinculo.getSesionEmitidaEn(), sesion.emitidaEn())
                || !Objects.equals(vinculo.getSesionExpiraEn(), sesion.expiraEn())
                || !mismaHuella(vinculo.getHashRetorno(), retornoRef)) {
            throw rechazo();
        }
        vinculo.setUsadoEn(ahora);
        vinculos.save(vinculo);
        return new RetornoConfirmado(flujoRef, proposito, "CONFIRMADO");
    }

    private VinculoInteraccionSegura cargarVigente(String referencia, Proposito proposito, Instant ahora) {
        if (!referenciaValida(referencia) || proposito != Proposito.ACCESO) {
            throw rechazo();
        }
        var vinculo = vinculos.bloquearPorHuella(huella(referencia)).orElseThrow(InicioSeguroServiceImpl::rechazo);
        if (!proposito.name().equals(vinculo.getProposito()) || !vinculo.getVenceEn().isAfter(ahora)) {
            throw rechazo();
        }
        return vinculo;
    }

    private void exigirSesion(SesionVerificada sesion) {
        Instant ahora = reloj.instant();
        if (sesion == null || sesion.actor() == null || sesion.actor().idOrganizacion() <= 0
                || sesion.actor().idPersona() <= 0 || sesion.actor().idRolOperativo() <= 0
                || sesion.contexto() == null || !sesion.contexto().matches("[a-f0-9]{64}")
                || sesion.emitidaEn() == null || sesion.emitidaEn().isAfter(ahora)
                || sesion.expiraEn() == null || !sesion.expiraEn().isAfter(ahora)) {
            throw new AccesoNoAutorizadoException(RECHAZO);
        }
        try {
            var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),sesion.actor());
            var canonico=com.controllocal.service.actuacion.ActuacionesService.ContextoSesion.de(actual);
            if(!canonico.identidad().equals(sesion.contexto()) || !actual.emitidaEn().equals(sesion.emitidaEn())
                    || !actual.venceEn().equals(sesion.expiraEn())) throw new AccesoNoAutorizadoException(RECHAZO);
        } catch (CredencialesInvalidasException e) { throw new AccesoNoAutorizadoException(RECHAZO); }
    }

    private static String referenciaNueva() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean referenciaValida(String referencia) {
        return referencia != null && referencia.matches("[A-Za-z0-9_-]{43}");
    }

    private static String huella(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo identificar la interacción segura.", e);
        }
    }

    private static boolean mismaHuella(String esperada, String referencia) {
        return esperada != null && referenciaValida(referencia) && MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.US_ASCII), huella(referencia).getBytes(StandardCharsets.US_ASCII));
    }

    private static ConflictoException rechazo() { return new ConflictoException(RECHAZO); }
}
