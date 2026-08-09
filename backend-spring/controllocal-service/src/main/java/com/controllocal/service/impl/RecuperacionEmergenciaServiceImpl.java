package com.controllocal.service.impl;

import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.seguridad.AccionRecuperacion;
import com.controllocal.domain.seguridad.AprobacionRecuperacion;
import com.controllocal.domain.seguridad.ConcesionRecuperacion;
import com.controllocal.persistence.repositorio.AccionRecuperacionRepository;
import com.controllocal.persistence.repositorio.AprobacionRecuperacionRepository;
import com.controllocal.persistence.repositorio.ConcesionRecuperacionRepository;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.MfaService;
import com.controllocal.service.RecuperacionEmergenciaService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.CierreDeConcesiones;
import com.controllocal.service.soporte.CustodiosConfigurados;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.service.soporte.GobiernoOperativo;
import com.controllocal.service.soporte.UsuariosInternos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RecuperacionEmergenciaServiceImpl implements RecuperacionEmergenciaService {

    /** 256 bits. Es lo que hace suficiente un SHA-256 sin sal ni trabajo lento. */
    private static final int BYTES_SECRETO = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String EMITIDA = "RECUPERACION_EMERGENCIA_EMITIDA";
    private static final String APLICADA = "RECUPERACION_EMERGENCIA_APLICADA";
    private static final String CADUCADA = "RECUPERACION_EMERGENCIA_CADUCADA";
    private static final String APROBACION_FALLIDA = "CUSTODIO_APROBACION_FALLIDA";

    private final ConcesionRecuperacionRepository concesiones;
    private final AprobacionRecuperacionRepository aprobaciones;
    private final AccionRecuperacionRepository acciones;
    private final CredencialUsuarioRepository credenciales;
    private final UsuarioOrganizacionRepository membresias;
    private final PersonaRepository personas;
    private final CustodiosConfigurados custodios;
    private final CierreDeConcesiones cierre;
    private final GobiernoOperativo gobierno;
    private final UsuariosInternos usuarios;
    private final MfaService mfa;
    private final EventosSeguridad auditoria;
    private final boolean habilitada;

    public RecuperacionEmergenciaServiceImpl(
            ConcesionRecuperacionRepository concesiones,
            AprobacionRecuperacionRepository aprobaciones,
            AccionRecuperacionRepository acciones,
            CredencialUsuarioRepository credenciales,
            UsuarioOrganizacionRepository membresias,
            PersonaRepository personas,
            CustodiosConfigurados custodios,
            CierreDeConcesiones cierre,
            GobiernoOperativo gobierno,
            UsuariosInternos usuarios,
            MfaService mfa,
            EventosSeguridad auditoria,
            @Value("${controllocal.recuperacion.habilitada:false}") boolean habilitada) {
        this.concesiones = concesiones;
        this.aprobaciones = aprobaciones;
        this.acciones = acciones;
        this.credenciales = credenciales;
        this.membresias = membresias;
        this.personas = personas;
        this.custodios = custodios;
        this.cierre = cierre;
        this.gobierno = gobierno;
        this.usuarios = usuarios;
        this.mfa = mfa;
        this.auditoria = auditoria;
        this.habilitada = habilitada;
    }

    // --------------------------------------------------------------- emision

    @Override
    @Transactional
    public long emitir(Emision emision) {
        exigirHabilitada();
        String operador = exigirTexto(emision.operador(), "El operador es obligatorio.");
        String motivo = exigirTexto(emision.motivo(), "El motivo es obligatorio.");

        // Quien ejecuta no custodia (D-S0-52). Se comprueba aqui para dar un
        // mensaje, y el CHECK de la tabla lo garantiza pase lo que pase.
        if (operador.equals(custodios.identificadorA()) || operador.equals(custodios.identificadorB())) {
            throw new ReglaNegocioException(
                    "El operador no puede ser uno de los custodios: quien ejecuta no aprueba.");
        }
        // Una emergencia es de UNA persona. Dos concesiones vivas a la vez
        // multiplicarian la superficie sin ninguna necesidad.
        if (concesiones.buscarViva(emision.idOrganizacion()).isPresent()) {
            throw new ReglaNegocioException(
                    "Ya hay una concesion en curso para esta organizacion. Cierrala o espera a que caduque.");
        }
        Persona objetivo = personas.findById(emision.idPersonaObjetivo())
                .filter(p -> p.getOrganizacionId() != null
                        && p.getOrganizacionId() == emision.idOrganizacion())
                .orElseThrow(() -> new NoEncontradoException("Persona"));

        ConcesionRecuperacion concesion = new ConcesionRecuperacion();
        concesion.setOrganizacionId(emision.idOrganizacion());
        concesion.setIdPersonaObjetivo(objetivo.getId());
        concesion.setOperador(operador);
        concesion.setCustodioA(custodios.identificadorA());
        concesion.setCustodioB(custodios.identificadorB());
        // Se fija ya, aunque el secreto no exista todavia: la columna es NOT
        // NULL y una concesion PENDIENTE no debe poder canjearse por nada. Se
        // sustituye por el hash real al completarse la segunda aprobacion.
        concesion.setHashSecreto(hashear(nuevoSecreto()));
        concesion.setMotivo(motivo);
        concesion.setEstado(ConcesionRecuperacion.PENDIENTE);
        concesiones.save(concesion);

        auditoria.registrar(EMITIDA, "OK",
                contexto(emision.idOrganizacion()), motivo, objetivo.getId(),
                Map.of("operador", operador, "estado", ConcesionRecuperacion.PENDIENTE));
        return concesion.getId();
    }

    // ----------------------------------------------------------- aprobacion

    @Override
    @Transactional
    public Optional<String> aprobar(long idConcesion, String identificadorCustodio, char[] secreto) {
        exigirHabilitada();
        ConcesionRecuperacion concesion = concesiones.findById(idConcesion)
                .orElseThrow(() -> new NoEncontradoException("Concesion"));
        if (!concesion.estaPendiente()) {
            throw new ReglaNegocioException("Esa concesion ya no admite aprobaciones.");
        }

        Optional<String> verificado = custodios.verificar(identificadorCustodio, secreto);
        if (verificado.isEmpty()) {
            // Se audita CADA intento fallido: es la señal de que alguien esta
            // probando llaves contra el mecanismo de ultimo recurso.
            auditoria.registrar(APROBACION_FALLIDA, "FALLO",
                    contexto(concesion.getOrganizacionId()), null,
                    concesion.getIdPersonaObjetivo(),
                    Map.of("concesion", String.valueOf(idConcesion)));
            throw new ReglaNegocioException("La aprobacion no es valida.");
        }
        String custodio = verificado.get();
        // El UNIQUE de la tabla lo garantiza; esto solo da el mensaje. Es la
        // pieza que impide que UNA persona cubra las dos partes.
        if (aprobaciones.existsByIdConcesionAndIdentificadorCustodio(idConcesion, custodio)) {
            throw new ReglaNegocioException("Ese custodio ya aprobo esta concesion.");
        }

        List<AprobacionRecuperacion> previas = aprobaciones.findByIdConcesionOrderByOrdenAsc(idConcesion);
        OffsetDateTime ahora = OffsetDateTime.now();

        AprobacionRecuperacion aprobacion = new AprobacionRecuperacion();
        aprobacion.setOrganizacionId(concesion.getOrganizacionId());
        aprobacion.setIdConcesion(idConcesion);
        aprobacion.setIdentificadorCustodio(custodio);
        aprobacion.setAprobadoEn(ahora);
        aprobacion.setOrden((short) (previas.size() + 1));
        aprobaciones.save(aprobacion);

        if (previas.isEmpty()) {
            // Con una sola aprobacion la concesion sigue PENDIENTE y no
            // autoriza absolutamente nada.
            return Optional.empty();
        }

        String secretoConcesion = nuevoSecreto();
        concesion.setHashSecreto(hashear(secretoConcesion));
        concesion.setEstado(ConcesionRecuperacion.VIGENTE);
        concesion.setVigenteDesde(ahora);
        concesion.setExpiraEn(ahora.plusMinutes(ConcesionRecuperacion.MINUTOS_VENTANA));
        concesiones.save(concesion);

        auditoria.registrar(EMITIDA, "OK", contexto(concesion.getOrganizacionId()),
                concesion.getMotivo(), concesion.getIdPersonaObjetivo(),
                Map.of("operador", concesion.getOperador(),
                        "estado", ConcesionRecuperacion.VIGENTE,
                        "aprobaciones", "2"));
        return Optional.of(secretoConcesion);
    }

    @Override
    @Transactional(readOnly = true)
    public Estado consultar(long idConcesion) {
        ConcesionRecuperacion concesion = concesiones.findById(idConcesion)
                .orElseThrow(() -> new NoEncontradoException("Concesion"));
        return new Estado(concesion.getId(), concesion.getEstado(),
                aprobaciones.findByIdConcesionOrderByOrdenAsc(idConcesion).size(),
                concesion.getAccionesConsumidas(), concesion.getMaxAcciones(),
                concesion.getExpiraEn());
    }

    // ------------------------------------------------------------ aplicacion

    @Override
    @Transactional
    public Resultado aplicar(String secretoConcesion, String tipoAccion) {
        exigirHabilitada();
        if (secretoConcesion == null || secretoConcesion.isBlank()) {
            throw new ReglaNegocioException("La concesion es obligatoria.");
        }
        String tipo = exigirTipo(tipoAccion);
        OffsetDateTime ahora = OffsetDateTime.now();

        ConcesionRecuperacion concesion = concesiones.findByHashSecreto(hashear(secretoConcesion))
                .orElseThrow(() -> new ReglaNegocioException("La concesion no es valida o ya caduco."));
        // La caducidad se comprueba AQUI, en cada uso, y no solo en el barrido.
        if (!concesion.utilizableEn(ahora)) {
            throw new ReglaNegocioException("La concesion no es valida o ya caduco.");
        }
        if (acciones.existsByIdConcesionAndTipo(concesion.getId(), tipo)) {
            throw new ReglaNegocioException("Esa accion ya se aplico con esta concesion.");
        }
        // Si el tenant YA tiene gobierno, la emergencia se acabo: se cierra y
        // se rechaza. Comprobarlo solo DESPUES de actuar dejaba una ventana en
        // la que la concesion seguia obrando sobre una organizacion que ya no
        // la necesitaba.
        //
        // El cierre va en TRANSACCION PROPIA porque el rechazo que viene
        // detras es una excepcion, y una excepcion arrastra consigo todo lo
        // escrito en su transaccion: con el cierre dentro, la accion se
        // rechazaba pero la concesion seguia figurando VIGENTE. Lo encontro el
        // simulacro, no el compilador.
        if (gobierno.hayAlgunoOperativo(concesion.getOrganizacionId())) {
            cierre.cerrarPorGobierno(concesion.getId(), ahora);
            throw new ReglaNegocioException(
                    "La organizacion ya tiene un administrador operativo: la concesion se cerro.");
        }

        // CONSUMO ATOMICO: el veredicto es cuantas filas afecto. Va ANTES de
        // aplicar nada, dentro de la misma transaccion: si la accion falla, el
        // rollback devuelve la capacidad; si sale bien, quedo consumida.
        if (concesiones.consumirCapacidad(concesion.getId(), ahora) != 1) {
            throw new ReglaNegocioException("La concesion esta agotada, caducada o cerrada.");
        }

        boolean cambio = ejecutar(concesion, tipo);

        AccionRecuperacion registro = new AccionRecuperacion();
        registro.setOrganizacionId(concesion.getOrganizacionId());
        registro.setIdConcesion(concesion.getId());
        registro.setTipo(tipo);
        registro.setAplicadaEn(ahora);
        registro.setResultado(cambio ? AccionRecuperacion.APLICADA : AccionRecuperacion.SIN_EFECTO);
        acciones.save(registro);

        auditoria.registrar(APLICADA, "OK", contexto(concesion.getOrganizacionId()),
                concesion.getMotivo(), concesion.getIdPersonaObjetivo(),
                Map.of("accion", tipo, "operador", concesion.getOperador(),
                        "resultado", registro.getResultado()));

        boolean cerrada = cerrarSiVolvioElGobierno(concesion, ahora);
        ConcesionRecuperacion actual = concesiones.findById(concesion.getId()).orElseThrow();
        marcarAgotadaSiConsumioSuUltimaAccion(actual, cerrada);
        return new Resultado(tipo, cambio, cerrada,
                (short) (actual.getMaxAcciones() - actual.getAccionesConsumidas()));
    }

    /**
     * <b>AGOTADA tiene productor desde 7.3.3.</b>
     *
     * <p>`A` estaba en el vocabulario desde V38 y nadie la escribia: al gastar
     * la ultima accion la concesion se quedaba VIGENTE, aunque
     * {@code consumirCapacidad} ya no fuera a dejar pasar ni una mas. El estado
     * decia una cosa y la capacidad otra.
     *
     * <p>No hace falta comprobar la capacidad a mano: el UPDATE atomico de
     * {@code consumirCapacidad} es el que decide, y aqui solo se LEE el
     * resultado de aquel. Por eso esto no reintroduce la carrera que aquel
     * cierra.
     *
     * <p>Si el gobierno volvio, CERRADA gana: describe mejor por que la
     * concesion dejo de servir. Y marcarla AGOTADA la saca de
     * {@code uq_concesion_viva_por_organizacion} —que solo cuenta {@code P} y
     * {@code V}—, que es justo lo que se quiere: una concesion sin capacidad no
     * debe bloquear la emision de otra.
     */
    private void marcarAgotadaSiConsumioSuUltimaAccion(ConcesionRecuperacion concesion,
                                                       boolean yaCerrada) {
        if (yaCerrada || !ConcesionRecuperacion.VIGENTE.equals(concesion.getEstado())) {
            return;
        }
        if (concesion.getAccionesConsumidas() < concesion.getMaxAcciones()) {
            return;
        }
        concesion.setEstado(ConcesionRecuperacion.AGOTADA);
        concesiones.save(concesion);
        auditoria.registrar(APLICADA, "OK", contexto(concesion.getOrganizacionId()),
                "Concesion agotada: consumio sus " + concesion.getMaxAcciones() + " acciones.",
                concesion.getIdPersonaObjetivo(),
                Map.of("estado", ConcesionRecuperacion.AGOTADA,
                        "acciones", String.valueOf(concesion.getAccionesConsumidas())));
    }

    /** Las tres unicas cosas, y las tres idempotentes. */
    private boolean ejecutar(ConcesionRecuperacion concesion, String tipo) {
        long organizacion = concesion.getOrganizacionId();
        long persona = concesion.getIdPersonaObjetivo();
        return switch (tipo) {
            case AccionRecuperacion.REACTIVAR_CUENTA -> reactivar(organizacion, persona);
            case AccionRecuperacion.REVOCAR_MFA -> mfa.revocarPorRecuperacion(organizacion, persona);
            case AccionRecuperacion.REPONER_MEMBRESIA -> reponerMembresia(organizacion, persona);
            default -> throw new ReglaNegocioException("Accion desconocida.");
        };
    }

    /**
     * Reactiva la credencial. <b>No toca la contrasena</b>: la regla del
     * proyecto —nadie fija la clave de otro— no tiene excepcion, tampoco en
     * una emergencia.
     */
    private boolean reactivar(long idOrganizacion, long idPersona) {
        CredencialUsuario cuenta = credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .orElseThrow(() -> new NoEncontradoException("Usuario"));
        if (Codigos.ActivoInactivo.ACTIVO.equals(cuenta.getEstadoAdministrativo())) {
            return false;
        }
        cuenta.setEstadoAdministrativo(Codigos.ActivoInactivo.ACTIVO);
        credenciales.save(cuenta);
        return true;
    }

    /**
     * Devuelve la membresia {@code TENANT_ADMIN}. <b>No crea personas</b>: si
     * no hay membresia que elevar, la concesion no la inventa — repone
     * gobierno, no puebla el tenant.
     */
    private boolean reponerMembresia(long idOrganizacion, long idPersona) {
        CredencialUsuario cuenta = credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .orElseThrow(() -> new NoEncontradoException("Usuario"));
        UsuarioOrganizacion membresia = membresias
                .buscarActivaPorCuenta(idOrganizacion, cuenta.getId())
                .orElseThrow(() -> new ReglaNegocioException(
                        "Esa persona no tiene membresia activa en la organizacion. La concesion "
                                + "repone gobierno, no crea cuentas."));
        if (UsuarioOrganizacion.ROL_TENANT_ADMIN.equals(membresia.getRol())) {
            return false;
        }
        Persona persona = personas.findById(idPersona)
                .orElseThrow(() -> new NoEncontradoException("Persona"));
        usuarios.concederGobierno(idOrganizacion, persona, membresia);
        return true;
    }

    /**
     * <b>La concesion se cierra sola</b> en cuanto el tenant vuelve a tener un
     * administrador operativo, aunque haya gastado una sola accion. Dejarla
     * viva «por si acaso» seria mantener abierta una puerta que ya no hace
     * falta.
     */
    private boolean cerrarSiVolvioElGobierno(ConcesionRecuperacion concesion, OffsetDateTime ahora) {
        if (!gobierno.hayAlgunoOperativo(concesion.getOrganizacionId())) {
            return false;
        }
        // Aqui SI se cierra en la transaccion principal: la accion ya salio
        // bien y nadie va a lanzar despues, asi que el cierre viaja con ella.
        // Abrir una transaccion nueva sobre una fila que esta transaccion
        // acaba de modificar seria esperar a un bloqueo que solo suelta quien
        // espera.
        ConcesionRecuperacion actual = concesiones.findById(concesion.getId()).orElseThrow();
        actual.setEstado(ConcesionRecuperacion.CERRADA);
        actual.setCerradaEn(ahora);
        actual.setCierreMotivo(CierreDeConcesiones.GOBIERNO_RESTABLECIDO);
        concesiones.save(actual);
        return true;
    }

    // ------------------------------------------------------------- caducidad

    @Override
    @Transactional
    public int caducarVencidas() {
        OffsetDateTime ahora = OffsetDateTime.now();
        int cerradas = 0;

        for (ConcesionRecuperacion concesion : concesiones.vencidas(ahora)) {
            concesion.setEstado(ConcesionRecuperacion.CADUCADA);
            concesion.setCerradaEn(ahora);
            concesion.setCierreMotivo("VENCIDA");
            concesiones.save(concesion);
            auditoria.registrar(CADUCADA, "OK", contexto(concesion.getOrganizacionId()),
                    concesion.getMotivo(), concesion.getIdPersonaObjetivo(), Map.of());
            cerradas++;
        }

        // Y las que ya no hacen falta: el titular volvio a enrolar y la
        // organizacion tiene gobierno otra vez. Sin este barrido, una concesion
        // se quedaria viva hasta agotar sus 30 minutos aunque la emergencia
        // hubiera terminado hace rato — no podria hacer nada (la comprobacion
        // de `aplicar` la cierra), pero figurar como VIGENTE lo que ya no lo
        // esta es una mentira en el tablero.
        for (ConcesionRecuperacion concesion : concesiones.vivas()) {
            if (cerrarSiVolvioElGobierno(concesion, ahora)) {
                cerradas++;
            }
        }
        return cerradas;
    }

    // -------------------------------------------------------------- interior

    /**
     * Apagada por defecto. Encenderla en {@code prod} sin los dos hashes y sin
     * notificador externo <b>detiene el arranque</b>: una recuperacion de
     * emergencia sin aviso externo puede usarse precisamente cuando nadie esta
     * dentro para ver la campana.
     */
    private void exigirHabilitada() {
        if (!habilitada) {
            throw new ReglaNegocioException(
                    "La recuperacion de emergencia no esta habilitada en esta instalacion.");
        }
        if (!custodios.estanConfigurados()) {
            throw new ReglaNegocioException(
                    "No hay dos custodios configurados: la doble aprobacion seria decorativa.");
        }
    }

    private EventosSeguridad.Contexto contexto(long idOrganizacion) {
        // Sin persona ni rol: no hay sesion. Esa ausencia es informativa —
        // distingue lo que hizo la concesion de lo que hizo alguien dentro.
        return EventosSeguridad.Contexto.anonimo(idOrganizacion, null, null);
    }

    private static String exigirTexto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(mensaje);
        }
        return valor.trim();
    }

    private static String exigirTipo(String tipo) {
        String limpio = tipo == null ? "" : tipo.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (limpio) {
            case AccionRecuperacion.REACTIVAR_CUENTA,
                 AccionRecuperacion.REVOCAR_MFA,
                 AccionRecuperacion.REPONER_MEMBRESIA -> limpio;
            default -> throw new ReglaNegocioException(
                    "Accion desconocida. Solo hay tres: REACTIVAR_CUENTA, REVOCAR_MFA y "
                            + "REPONER_MEMBRESIA.");
        };
    }

    private static String nuevoSecreto() {
        byte[] material = new byte[BYTES_SECRETO];
        RANDOM.nextBytes(material);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(material);
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
