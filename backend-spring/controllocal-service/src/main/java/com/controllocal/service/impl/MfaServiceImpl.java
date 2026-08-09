package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.seguridad.CodigoRespaldoMfa;
import com.controllocal.domain.seguridad.FactorAutenticacion;
import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.CodigoRespaldoMfaRepository;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.persistence.repositorio.OrganizacionRepository;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AutenticacionService;
import com.controllocal.service.ContrasenaService;
import com.controllocal.service.MfaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ErrorMfaException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Base32;
import com.controllocal.service.soporte.BloqueoMfa;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.CodigosRespaldo;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.service.soporte.GobiernoOperativo;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.Totp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * <b>Unico punto de escritura de {@code factor_autenticacion}</b>, por la misma
 * razon por la que {@code Transiciones} es el unico que muta estados y
 * {@code EventosSeguridad} el unico que audita: los efectos de activar o
 * revocar un factor son varios y no deben poder ejecutarse a medias.
 */
@Service
public class MfaServiceImpl implements MfaService {

    /** El emisor que ve el usuario en su aplicacion autenticadora. */
    private static final String EMISOR = "ControlLocal";

    private static final int MINUTOS_DESAFIO = 5;
    private static final int MINUTOS_ELEVACION = 5;
    private static final int BYTES_TOKEN = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FactorAutenticacionRepository factores;
    private final CodigoRespaldoMfaRepository codigos;
    private final CredencialUsuarioRepository credenciales;
    private final TokenAccesoRepository tokens;
    private final UsuarioOrganizacionRepository membresias;
    private final OrganizacionRepository organizaciones;
    private final CifradoSecretos cifrado;
    private final GobiernoOperativo gobierno;
    private final BloqueoMfa bloqueoMfa;
    private final AutenticacionService autenticacion;
    private final EventosSeguridad auditoria;

    public MfaServiceImpl(FactorAutenticacionRepository factores,
                          CodigoRespaldoMfaRepository codigos,
                          CredencialUsuarioRepository credenciales,
                          TokenAccesoRepository tokens,
                          UsuarioOrganizacionRepository membresias,
                          OrganizacionRepository organizaciones,
                          CifradoSecretos cifrado,
                          GobiernoOperativo gobierno,
                          BloqueoMfa bloqueoMfa,
                          AutenticacionService autenticacion,
                          EventosSeguridad auditoria) {
        this.factores = factores;
        this.codigos = codigos;
        this.credenciales = credenciales;
        this.tokens = tokens;
        this.membresias = membresias;
        this.organizaciones = organizaciones;
        this.cifrado = cifrado;
        this.gobierno = gobierno;
        this.bloqueoMfa = bloqueoMfa;
        this.autenticacion = autenticacion;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------- lectura

    @Override
    @Transactional(readOnly = true)
    public EstadoFactor estado(Actor actor) {
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        Optional<FactorAutenticacion> activo = factores.buscarActivo(cuenta.getId());
        return new EstadoFactor(
                activo.isPresent(),
                cuenta.isDebeEnrolarMfa(),
                activo.map(f -> codigos.contarDisponibles(f.getId())).orElse(0L),
                activo.map(FactorAutenticacion::getActivadoEn).orElse(null));
    }

    // -------------------------------------------------------- enrolamiento

    @Override
    @Transactional
    public Enrolamiento iniciar(Actor actor) {
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        if (factores.buscarActivo(cuenta.getId()).isPresent()) {
            // Reemplazar exige probar el factor vigente (D-S0-34); por aqui
            // solo se enrola desde cero.
            throw new ReglaNegocioException(
                    "Ya tienes un segundo factor activo. Para cambiarlo, revocalo primero "
                            + "con tu contrasena y un codigo vigente.");
        }
        // Un enrolamiento a medias no se acumula: el anterior pendiente muere.
        factores.deleteAll(factores.pendientesDe(cuenta.getId()));

        byte[] secreto = Totp.secretoNuevo();
        CifradoSecretos.Cifrado guardado = cifrado.cifrar(secreto);

        FactorAutenticacion factor = new FactorAutenticacion();
        factor.setOrganizacionId(actor.idOrganizacion());
        factor.setIdCredencial(cuenta.getId());
        factor.setTipo(FactorAutenticacion.TIPO_TOTP);
        factor.setSecretoCifrado(guardado.criptograma());
        factor.setNonce(guardado.nonce());
        factor.setVersionClave(guardado.version());
        factor.setAlgoritmo(Totp.ALGORITMO);
        factor.setDigitos((short) Totp.DIGITOS);
        factor.setPeriodo((short) Totp.PERIODO_SEGUNDOS);
        factor.setEstado(FactorAutenticacion.PENDIENTE);
        factores.save(factor);

        return new Enrolamiento(Base32.codificar(secreto),
                Totp.uri(EMISOR, cuenta.getNombreUsuario(), secreto));
    }

    @Override
    @Transactional
    public List<String> confirmar(Actor actor, String codigo) {
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        OffsetDateTime ahora = OffsetDateTime.now();

        FactorAutenticacion factor = factores.pendientesDe(cuenta.getId()).stream()
                .filter(f -> !f.pendienteCaducado(ahora))
                .findFirst()
                .orElseThrow(ErrorMfaException::enrolamientoInvalido);

        Totp.Validacion totp = Totp.validar(descifrar(factor), codigo, Instant.now());
        if (!totp.valido()) {
            throw ErrorMfaException.codigoInvalido();
        }

        // --- los cuatro efectos, juntos o ninguno --------------------------
        factor.setEstado(FactorAutenticacion.ACTIVO);
        factor.setActivadoEn(ahora);
        // Y el paso queda SELLADO en el mismo acto (D-S0-31). No es un extra:
        // sin esto, el codigo con el que se confirma el enrolamiento seguia
        // valiendo en /auth/mfa/verificar durante el resto de su ventana —
        // hasta 30 segundos de replay del PRIMER codigo—. `consumirPaso` no
        // sirve aqui porque solo mira factores ACTIVO y en este punto el
        // factor todavia es PENDIENTE; y no hace falta su UPDATE condicional
        // porque la carrera que cubre ya la cierra el indice parcial
        // uq_factor_activo_por_credencial: dos confirmaciones simultaneas no
        // pueden activar dos veces.
        factor.setUltimoPaso(totp.paso());
        factor.setUltimoUsoEn(ahora);
        factores.save(factor);

        List<String> visibles = generarCodigos(factor.getId(), actor.idOrganizacion());

        cuenta.setDebeEnrolarMfa(false);
        cuenta.setSesionesInvalidasDesde(ahora);
        credenciales.save(cuenta);

        // Si es gobierno, la organizacion cruza el umbral: desde aqui el
        // invariante del trigger deja de conformarse con "hay administrador"
        // y pasa a exigir "hay administrador OPERATIVO".
        if (esTenantAdmin(actor.idOrganizacion(), cuenta.getId())) {
            organizaciones.exigirMfaDeGobierno(actor.idOrganizacion());
        }

        auditoria.registrar("MFA_ACTIVADO", "OK", contexto(actor, cuenta));
        return visibles;
    }

    // ------------------------------------------------- codigos y revocacion

    @Override
    @Transactional
    public List<String> regenerarCodigos(Actor actor, char[] contrasena, String codigo) {
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        FactorAutenticacion factor = exigirFactorActivo(cuenta);
        exigirContrasenaYCodigo(cuenta, factor, contrasena, codigo);

        codigos.borrarDe(factor.getId());
        List<String> visibles = generarCodigos(factor.getId(), actor.idOrganizacion());
        // NO invalida sesiones: regenerar codigos no cambia quien eres ni como
        // entras. Echar al usuario aqui seria castigo sin motivo.
        auditoria.registrar("MFA_CODIGOS_REGENERADOS", "OK", contexto(actor, cuenta));
        return visibles;
    }

    @Override
    @Transactional
    public void revocarPropio(Actor actor, char[] contrasena, String codigo,
                              String ip, String agenteUsuario) {
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        FactorAutenticacion factor = exigirFactorActivo(cuenta);
        exigirContrasenaYCodigo(cuenta, factor, contrasena, codigo);

        gobierno.exigirQueQuedeGobierno(actor.idOrganizacion(), cuenta.getId(),
                "revocar tu segundo factor");
        revocar(factor, cuenta, actor.idOrganizacion());
        auditoria.registrar("MFA_REVOCADO", "OK",
                new EventosSeguridad.Contexto(actor.idOrganizacion(), actor.idPersona(),
                        cuenta.getId(), actor.rolEfectivo(), ip, agenteUsuario));
    }

    @Override
    @Transactional
    public void revocarAjeno(Actor actor, long idPersona, String tokenElevacion,
                             String motivo, String ip, String agenteUsuario) {
        // Regla completa por rol: administrar cuentas es gobierno. Un BROKER no
        // revoca el factor de nadie, ni el de sus agentes (D-S0-18).
        if (!actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
        if (actor.idPersona() == idPersona) {
            throw new ReglaNegocioException(
                    "Para revocar tu propio factor usa la opcion de tu perfil.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de la revocacion es obligatorio.");
        }
        consumirElevacion(actor, tokenElevacion, "REVOCAR_MFA_AJENO");

        // Otro tenant responde 404, no 403: un 403 confirmaria que esa persona
        // existe. Mismo criterio que las invitaciones.
        CredencialUsuario objetivo = credenciales
                .buscarPorPersona(actor.idOrganizacion(), idPersona)
                .orElseThrow(() -> new NoEncontradoException("Usuario"));

        gobierno.exigirQueQuedeGobierno(actor.idOrganizacion(), objetivo.getId(),
                "revocar el segundo factor de esa persona");

        factores.buscarActivo(objetivo.getId())
                .ifPresent(factor -> revocar(factor, objetivo, actor.idOrganizacion()));
        // Aunque no tuviera factor, queda obligada a enrolar: es el estado que
        // se busca, no un efecto del borrado.
        objetivo.setDebeEnrolarMfa(true);
        objetivo.setSesionesInvalidasDesde(OffsetDateTime.now());
        credenciales.save(objetivo);

        auditoria.registrar("MFA_REVOCADO", "OK",
                new EventosSeguridad.Contexto(actor.idOrganizacion(), actor.idPersona(),
                        objetivo.getId(), actor.rolEfectivo(), ip, agenteUsuario),
                motivo, idPersona, null);
    }

    /**
     * Nivel 3 (V38). Idempotente: si ya no hay factor y la cuenta ya esta
     * obligada a enrolar, no cambia nada y lo dice.
     */
    @Override
    @Transactional
    public boolean revocarPorRecuperacion(long idOrganizacion, long idPersona) {
        CredencialUsuario cuenta = cuentaDe(idOrganizacion, idPersona);
        Optional<FactorAutenticacion> activo = factores.buscarActivo(cuenta.getId());
        if (activo.isEmpty() && cuenta.isDebeEnrolarMfa()) {
            return false;
        }
        // NO se llama a gobierno.exigirQueQuedeGobierno: esa guarda es la que
        // no se puede satisfacer cuando no queda ningun administrador, y este
        // camino existe precisamente para esa situacion.
        activo.ifPresent(factor -> revocar(factor, cuenta, idOrganizacion));
        if (activo.isEmpty()) {
            // Sin factor que revocar, queda al menos la obligacion de enrolar:
            // es el estado que se busca, no un efecto del borrado.
            cuenta.setDebeEnrolarMfa(true);
            cuenta.setSesionesInvalidasDesde(OffsetDateTime.now());
            credenciales.save(cuenta);
        }
        return true;
    }

    // ------------------------------------------------------------ elevacion

    @Override
    @Transactional
    public ContrasenaService.TokenEntregado emitirElevacion(Actor actor, char[] contrasena,
                                                            String codigo, String accion) {
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        FactorAutenticacion factor = exigirFactorActivo(cuenta);
        try {
            exigirContrasenaYCodigo(cuenta, factor, contrasena, codigo);
        } catch (ReglaNegocioException e) {
            auditoria.registrar("ELEVACION_FALLIDA", "FALLO", contexto(actor, cuenta));
            throw e;
        }

        OffsetDateTime ahora = OffsetDateTime.now();
        tokens.invalidarVivosDe(cuenta.getId(), TokenAcceso.TIPO_ELEVACION, ahora);

        byte[] material = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(material);
        String enClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(material);

        TokenAcceso token = new TokenAcceso();
        token.setOrganizacionId(actor.idOrganizacion());
        token.setIdCredencial(cuenta.getId());
        token.setTipo(TokenAcceso.TIPO_ELEVACION);
        token.setHashToken(hashear(enClaro));
        token.setCreadoEn(ahora);
        token.setExpiraEn(ahora.plusMinutes(MINUTOS_ELEVACION));
        token.setCreadoPor(actor.idPersona());
        // La accion viaja en el motivo: una elevacion sirve para UNA cosa.
        token.setMotivo(accion);
        tokens.save(token);

        auditoria.registrar("ELEVACION_EMITIDA", "OK", contexto(actor, cuenta));
        return new ContrasenaService.TokenEntregado(enClaro, token.getExpiraEn(), true);
    }

    /**
     * Consume la elevacion: tipo exacto, no caducada, de ESTA credencial y de
     * ESTA accion. Un solo uso, sellado en la misma transaccion que la
     * operacion que autoriza — si esta falla, la elevacion sigue viva.
     */
    private void consumirElevacion(Actor actor, String token, String accion) {
        if (token == null || token.isBlank()) {
            throw new ReglaNegocioException(
                    "Esta operacion exige reautenticarse con tu segundo factor.");
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        CredencialUsuario cuenta = cuentaDe(actor.idOrganizacion(), actor.idPersona());
        TokenAcceso fila = tokens
                .buscarPorHashYTipo(hashear(token), TokenAcceso.TIPO_ELEVACION)
                .filter(t -> t.vigenteEn(ahora))
                .filter(t -> t.getIdCredencial().equals(cuenta.getId()))
                .filter(t -> accion.equals(t.getMotivo()))
                .orElseThrow(() -> new ReglaNegocioException(
                        "La reautenticacion no es valida o ya caduco."));
        fila.setUsadoEn(ahora);
        fila.setEstado(TokenAcceso.CONSUMIDO);
        tokens.save(fila);
    }

    // ---------------------------------------------------------------- login

    @Override
    @Transactional(readOnly = true)
    public boolean exigeSegundoFactor(long idOrganizacion, long idPersona) {
        return credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .map(c -> factores.buscarActivo(c.getId()).isPresent())
                .orElse(false);
    }

    @Override
    @Transactional
    public Desafio emitirDesafio(long idOrganizacion, long idPersona) {
        CredencialUsuario cuenta = cuentaDe(idOrganizacion, idPersona);
        OffsetDateTime ahora = OffsetDateTime.now();
        // Solo mata el desafio anterior: una recuperacion de contrasena
        // pendiente es otro flujo y no debe caerse con esto.
        tokens.invalidarVivosDe(cuenta.getId(), TokenAcceso.TIPO_DESAFIO_MFA, ahora);

        byte[] material = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(material);
        String enClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(material);

        TokenAcceso token = new TokenAcceso();
        token.setOrganizacionId(idOrganizacion);
        token.setIdCredencial(cuenta.getId());
        token.setTipo(TokenAcceso.TIPO_DESAFIO_MFA);
        token.setHashToken(hashear(enClaro));
        token.setCreadoEn(ahora);
        token.setExpiraEn(ahora.plusMinutes(MINUTOS_DESAFIO));
        tokens.save(token);

        return new Desafio(enClaro, token.getExpiraEn());
    }

    @Override
    @Transactional
    public Verificacion verificarDesafio(String desafio, String codigo,
                                         String ip, String agenteUsuario) {
        if (desafio == null || desafio.isBlank()) {
            throw ErrorMfaException.desafioInvalido();
        }
        if (codigo == null || codigo.isBlank()) {
            throw ErrorMfaException.codigoInvalido();
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        TokenAcceso fila = tokens
                .buscarPorHashYTipo(hashear(desafio), TokenAcceso.TIPO_DESAFIO_MFA)
                .orElseThrow(ErrorMfaException::desafioInvalido);
        exigirDesafioVigente(fila, ahora);

        CredencialUsuario cuenta = credenciales.findById(fila.getIdCredencial())
                .orElseThrow(ErrorMfaException::desafioInvalido);
        long idPersona = personaDe(cuenta);

        // Control acumulado POR CUENTA: es el que no se reinicia pidiendo
        // desafios nuevos (D-S0-32).
        int espera = bloqueoMfa.esperaExigida(cuenta.getNombreUsuario());
        if (espera > 0) {
            throw ErrorMfaException.limiteIntentos(espera);
        }

        // Perder el factor a mitad del flujo se cuenta como desafio invalido, y
        // no como "no tienes MFA": el segundo lo confirmaria a quien pregunta.
        FactorAutenticacion factor = factores.buscarActivo(cuenta.getId())
                .orElseThrow(ErrorMfaException::desafioInvalido);

        Verificacion resultado;
        try {
            resultado = intentar(factor, cuenta, codigo, ahora);
        } catch (ErrorMfaException error) {
            // El replay TAMBIEN gasta cupo. Si no lo hiciera, distinguirlo del
            // codigo equivocado seria una sonda gratis contra los limites.
            registrarFallo(fila, cuenta, ip, agenteUsuario);
            throw error;
        }

        fila.setUsadoEn(ahora);
        fila.setEstado(TokenAcceso.CONSUMIDO);
        tokens.save(fila);
        bloqueoMfa.registrar(cuenta.getNombreUsuario(), true,
                cuenta.getOrganizacionId(), ip, agenteUsuario);
        auditoria.registrar("MFA_OK", "OK",
                new EventosSeguridad.Contexto(cuenta.getOrganizacionId(), idPersona,
                        cuenta.getId(), null, ip, agenteUsuario));
        if (resultado.porCodigoRespaldo()) {
            auditoria.registrar("MFA_CODIGO_RESPALDO_USADO", "OK",
                    new EventosSeguridad.Contexto(cuenta.getOrganizacionId(), idPersona,
                            cuenta.getId(), null, ip, agenteUsuario));
        }
        return resultado;
    }

    /**
     * Por que un desafio deja de servir. El orden va de lo mas concreto a lo
     * mas generico para que cada caso caiga en SU codigo: consumido y agotado
     * son estados finales, invalidado es un reemplazo y la caducidad es lo
     * ultimo que queda por mirar.
     */
    private static void exigirDesafioVigente(TokenAcceso fila, OffsetDateTime ahora) {
        if (fila.getUsadoEn() != null || TokenAcceso.CONSUMIDO.equals(fila.getEstado())) {
            throw ErrorMfaException.desafioConsumido();
        }
        if (TokenAcceso.AGOTADO.equals(fila.getEstado())) {
            throw ErrorMfaException.desafioAgotado();
        }
        if (fila.getInvalidadoEn() != null || TokenAcceso.REVOCADO.equals(fila.getEstado())) {
            throw ErrorMfaException.desafioInvalido();
        }
        if (!fila.getExpiraEn().isAfter(ahora)) {
            throw ErrorMfaException.desafioVencido();
        }
    }

    /**
     * TOTP primero; si no cuadra, se prueba como codigo de respaldo. Lanza en
     * vez de devolver {@code null} porque los dos fallos posibles <b>no son el
     * mismo</b>: un codigo equivocado se corrige tecleando bien, y uno
     * reutilizado solo se corrige esperando al siguiente.
     */
    private Verificacion intentar(FactorAutenticacion factor, CredencialUsuario cuenta,
                                  String codigo, OffsetDateTime ahora) {
        long idPersona = personaDe(cuenta);
        Totp.Validacion totp = Totp.validar(descifrar(factor), codigo, Instant.now());
        if (totp.valido()) {
            // ATOMICO: el veredicto es cuantas filas afecto. Si es 0, ese paso
            // ya se consumio y el codigo es un replay.
            if (factores.consumirPaso(factor.getId(), totp.paso(), ahora) != 1) {
                throw ErrorMfaException.codigoReutilizado();
            }
            return new Verificacion(idPersona, false, codigos.contarDisponibles(factor.getId()));
        }
        Verificacion conRespaldo = respaldo(factor, idPersona, codigo, ahora);
        if (conRespaldo == null) {
            throw ErrorMfaException.codigoInvalido();
        }
        return conRespaldo;
    }

    private Verificacion respaldo(FactorAutenticacion factor, long idPersona,
                                  String codigo, OffsetDateTime ahora) {
        CodigosRespaldo.Tecleado partes = CodigosRespaldo.partir(codigo);
        if (!partes.completo()) {
            return null;
        }
        Optional<CodigoRespaldoMfa> fila =
                codigos.buscarDisponible(factor.getId(), partes.identificador());
        if (fila.isEmpty()
                || !PasswordHasher.verificar(partes.secreto().toCharArray(), fila.get().getHashSecreto())
                || codigos.consumir(fila.get().getId(), ahora) != 1) {
            return null;
        }
        // Consumir un codigo NO desactiva el factor: solo deja entrar.
        return new Verificacion(idPersona, true, codigos.contarDisponibles(factor.getId()));
    }

    private void registrarFallo(TokenAcceso desafio, CredencialUsuario cuenta,
                                String ip, String agenteUsuario) {
        // Los dos contadores se anotan en transaccion propia: este metodo se
        // llama justo antes de lanzar, y lo que se vaya con el rollback no
        // cuenta nada.
        bloqueoMfa.anotarFalloDeDesafio(desafio.getId());
        bloqueoMfa.registrar(cuenta.getNombreUsuario(), false,
                cuenta.getOrganizacionId(), ip, agenteUsuario);
        auditoria.registrar("MFA_FALLIDO", "FALLO",
                new EventosSeguridad.Contexto(cuenta.getOrganizacionId(), personaDe(cuenta),
                        cuenta.getId(), null, ip, agenteUsuario));
    }

    // ------------------------------------------------------------ interior

    private void revocar(FactorAutenticacion factor, CredencialUsuario cuenta, long idOrganizacion) {
        OffsetDateTime ahora = OffsetDateTime.now();
        factor.setEstado(FactorAutenticacion.REVOCADO);
        factor.setRevocadoEn(ahora);
        factores.save(factor);
        codigos.borrarDe(factor.getId());
        cuenta.setDebeEnrolarMfa(true);
        // Revocar es el momento de mayor riesgo: si lo pidio un atacante, sus
        // sesiones tienen que caer con la revocacion.
        cuenta.setSesionesInvalidasDesde(ahora);
        credenciales.save(cuenta);
    }

    private List<String> generarCodigos(long idFactor, long idOrganizacion) {
        List<String> visibles = new ArrayList<>();
        for (CodigosRespaldo.Generado generado : CodigosRespaldo.generar()) {
            CodigoRespaldoMfa fila = new CodigoRespaldoMfa();
            fila.setOrganizacionId(idOrganizacion);
            fila.setIdFactor(idFactor);
            fila.setIdentificador(generado.identificador());
            fila.setHashSecreto(generado.hash());
            codigos.save(fila);
            visibles.add(generado.visible());
        }
        return visibles;
    }

    private FactorAutenticacion exigirFactorActivo(CredencialUsuario cuenta) {
        return factores.buscarActivo(cuenta.getId())
                .orElseThrow(() -> new ReglaNegocioException(
                        "No tienes un segundo factor activo."));
    }

    /**
     * Reautenticacion reforzada (D-S0-34): contrasena <b>y</b> factor vigente.
     * Una sesion abierta no basta — si bastara, robarla equivaldria a quedarse
     * con la cuenta para siempre.
     */
    private void exigirContrasenaYCodigo(CredencialUsuario cuenta, FactorAutenticacion factor,
                                         char[] contrasena, String codigo) {
        if (contrasena == null || contrasena.length == 0
                || !PasswordHasher.verificar(contrasena, cuenta.getContrasenaHash())) {
            throw new ReglaNegocioException("La contrasena actual no es correcta.");
        }
        Totp.Validacion totp = Totp.validar(descifrar(factor), codigo, Instant.now());
        if (!totp.valido()) {
            throw ErrorMfaException.codigoInvalido();
        }
        if (factores.consumirPaso(factor.getId(), totp.paso(), OffsetDateTime.now()) != 1) {
            throw ErrorMfaException.codigoReutilizado();
        }
    }

    private byte[] descifrar(FactorAutenticacion factor) {
        return cifrado.descifrar(factor.getSecretoCifrado(), factor.getNonce(),
                factor.getVersionClave());
    }

    private CredencialUsuario cuentaDe(long idOrganizacion, long idPersona) {
        return credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .orElseThrow(() -> new NoEncontradoException("Usuario"));
    }

    private static long personaDe(CredencialUsuario cuenta) {
        return cuenta.getRol().getPersona().getId();
    }

    private boolean esTenantAdmin(long idOrganizacion, long idCuenta) {
        return membresias.buscarActivaPorCuenta(idOrganizacion, idCuenta)
                .map(m -> UsuarioOrganizacion.ROL_TENANT_ADMIN.equals(m.getRol()))
                .orElse(false);
    }

    private EventosSeguridad.Contexto contexto(Actor actor, CredencialUsuario cuenta) {
        return new EventosSeguridad.Contexto(actor.idOrganizacion(), actor.idPersona(),
                cuenta.getId(), actor.rolEfectivo(), null, null);
    }

    private static String hashear(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }
}
