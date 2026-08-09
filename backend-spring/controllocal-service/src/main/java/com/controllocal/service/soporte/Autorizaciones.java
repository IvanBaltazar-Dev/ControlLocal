package com.controllocal.service.soporte;

import com.controllocal.domain.consentimiento.AutorizacionTratamientoEvento;
import com.controllocal.domain.consentimiento.AvisoPrivacidadVersion;
import com.controllocal.domain.consentimiento.EvidenciaAutorizacion;
import com.controllocal.persistence.repositorio.AutorizacionTratamientoEventoRepository;
import com.controllocal.persistence.repositorio.AvisoPrivacidadVersionRepository;
import com.controllocal.persistence.repositorio.EvidenciaAutorizacionRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Autorizacion de datos personales (D-27): <b>una sola vez, en el alta</b>.
 * <p>
 * Reutiliza las estructuras que V6 creo y nadie usaba. No hay tabla nueva ni
 * booleano nuevo: el hecho se guarda como <b>evento append-only</b> y "esta
 * autorizado" es una <b>proyeccion</b>, no una columna.
 * <p>
 * Lo que el agente ve son dos cosas —una casilla y un enlace al aviso—; todo
 * lo demas (tenant, actor, fecha, canal, version del aviso, base juridica y
 * finalidad) lo pone esta clase. Si algun dia aparece un campo de fecha, de
 * version o de canal en el formulario, la pantalla esta mal.
 */
@Component
public class Autorizaciones {

    /**
     * La UNICA finalidad activa. Cubre los cinco ambitos de D-27 §3.3:
     * gestion comercial e inmobiliaria, comunicaciones y seguimiento,
     * documentos/contratos/pagos, seguridad/auditoria/cumplimiento legal, y
     * automatizaciones internas necesarias para operar.
     */
    public static final String OPERACION_BROX = "OPERACION_SERVICIO";

    /**
     * Canal tecnico con el que se sella la evidencia. <b>No se pregunta ni se
     * muestra</b>: la columna es NOT NULL y hoy solo hay un camino por el que
     * entra una autorizacion —el formulario de alta—, asi que el dato lo pone
     * el backend y no el agente. Preguntarlo era friccion sin informacion: el
     * usuario elegia entre seis opciones para describir la pantalla en la que
     * ya estaba. El dia que existan otros caminos (WhatsApp, portal del
     * titular) cada uno sellara el suyo, y la estructura ya lo admite.
     */
    public static final String CANAL_FORMULARIO = "FORMULARIO_BROX";

    private final AutorizacionTratamientoEventoRepository eventos;
    private final EvidenciaAutorizacionRepository evidencias;
    private final AvisoPrivacidadVersionRepository avisos;
    private final PersonaRolRepository roles;

    public Autorizaciones(AutorizacionTratamientoEventoRepository eventos,
                          EvidenciaAutorizacionRepository evidencias,
                          AvisoPrivacidadVersionRepository avisos,
                          PersonaRolRepository roles) {
        this.eventos = eventos;
        this.evidencias = evidencias;
        this.avisos = avisos;
        this.roles = roles;
    }

    /**
     * Registra la autorizacion del alta. Se llama <b>dentro de la misma
     * transaccion</b> que crea persona + rol + contacto: si esto lanza, no se
     * persiste ninguna parte del alta (D-27 §6).
     *
     * @param autorizado marca de la casilla, y <b>lo unico que aporta el
     *                   usuario</b>. {@code false} o {@code null} = no hay
     *                   alta, y por tanto tampoco persona.
     */
    public void registrarEnAlta(long idPersona, Boolean autorizado, Actor actor) {
        if (!Boolean.TRUE.equals(autorizado)) {
            // Se rechaza ANTES de escribir nada. No se crea la persona "marcada
            // como que no autorizo": esa fila seria justo el dato que no se
            // puede guardar.
            throw new ReglaNegocioException(
                    "Sin la autorizacion de la persona para el registro y uso de sus datos no se "
                            + "puede completar el alta.");
        }
        AvisoPrivacidadVersion aviso = avisoVigente();

        EvidenciaAutorizacion evidencia = new EvidenciaAutorizacion();
        evidencia.setCanal(CANAL_FORMULARIO);
        evidencia.setFechaHora(OffsetDateTime.now());
        evidencia.setTextoMostrado(aviso.getContenido());
        evidencias.save(evidencia);

        AutorizacionTratamientoEvento evento = new AutorizacionTratamientoEvento();
        evento.setOrganizacionId(actor.idOrganizacion());
        evento.setIdPersona(idPersona);
        evento.setFinalidadCodigo(OPERACION_BROX);
        evento.setEvento(AutorizacionTratamientoEvento.OTORGADO);
        evento.setBaseJuridica(AutorizacionTratamientoEvento.BASE_CONSENTIMIENTO);
        evento.setVersionAviso(aviso.getVersion());
        evento.setOcurridoEn(OffsetDateTime.now());
        evento.setIdEvidencia(evidencia.getId());
        evento.setRegistradaPor(actor.idRolOperativo());
        eventos.save(evento);
    }

    /**
     * Retira la autorizacion. NO borra el otorgamiento: agrega un evento, que
     * es lo que conserva la trazabilidad de que hubo consentimiento y cuando.
     * <p>
     * Lo que esto NO hace, y es deliberado: no borra contratos, ni movimientos
     * economicos, ni el historial. Esos tratamientos se sostienen en relacion
     * contractual y obligacion legal, no en el consentimiento retirado.
     */
    public void revocar(long idPersona, String motivo, Actor actor) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de la revocacion es obligatorio.");
        }
        if (!estaAutorizada(idPersona, actor.idOrganizacion())) {
            throw new ReglaNegocioException("La persona no tiene una autorizacion vigente que revocar.");
        }
        AutorizacionTratamientoEvento evento = new AutorizacionTratamientoEvento();
        evento.setOrganizacionId(actor.idOrganizacion());
        evento.setIdPersona(idPersona);
        evento.setFinalidadCodigo(OPERACION_BROX);
        evento.setEvento(AutorizacionTratamientoEvento.REVOCADO);
        evento.setBaseJuridica(AutorizacionTratamientoEvento.BASE_CONSENTIMIENTO);
        evento.setVersionAviso(avisoVigente().getVersion());
        evento.setOcurridoEn(OffsetDateTime.now());
        evento.setMotivoRevocacion(motivo.trim());
        evento.setRegistradaPor(actor.idRolOperativo());
        eventos.save(evento);
    }

    /**
     * Proyeccion de vigencia. Una autorizacion vale cuando su ultimo evento la
     * otorga, nadie la revoco despues, y su version del aviso no es anterior al
     * ultimo cambio MATERIAL publicado (D-27 §3.4).
     */
    public boolean estaAutorizada(long idPersona, long idOrganizacion) {
        Optional<AutorizacionTratamientoEvento> ultimo =
                eventos.ultimoEvento(idOrganizacion, idPersona, OPERACION_BROX);
        if (ultimo.isEmpty()) {
            return false;
        }
        AutorizacionTratamientoEvento evento = ultimo.get();
        boolean otorgada = AutorizacionTratamientoEvento.OTORGADO.equals(evento.getEvento())
                || AutorizacionTratamientoEvento.REOTORGADO.equals(evento.getEvento());
        return otorgada && !caducadaPorCambioMaterial(evento.getVersionAviso());
    }

    /** Historial completo para la ficha: quien y cuando. */
    public List<AutorizacionTratamientoEvento> historial(long idPersona, long idOrganizacion) {
        return eventos.findByOrganizacionIdAndIdPersonaOrderByIdDesc(idOrganizacion, idPersona);
    }

    /**
     * Lo que la ficha de cliente y la de propietario muestran de la
     * autorizacion: <b>si esta vigente, cuando se registro, quien la registro</b>
     * y contra que version del aviso.
     * <p>
     * El canal NO sale: es siempre {@link #CANAL_FORMULARIO} y un dato que
     * nunca varia no informa de nada.
     *
     * @param versionAviso   version citada por el evento
     * @param versionVigente version vigente <b>hoy</b>. Van las dos porque el
     *                       numero solo aporta algo cuando <b>difieren</b>: si
     *                       coinciden, mostrarlo es ruido, y quien pinta decide
     *                       con las dos a la vista.
     */
    public record Constancia(String estado, OffsetDateTime registradaEn, String registradaPor,
                             String versionAviso, String versionVigente) {

        /** Ultimo evento OTORGADO/REOTORGADO, sin revocacion ni caducidad. */
        public static final String VIGENTE = "VIGENTE";
        /** El titular la retiro (§5). */
        public static final String REVOCADA = "REVOCADA";
        /** Se otorgo contra un aviso anterior a un cambio MATERIAL (§3.4). */
        public static final String CADUCADA = "CADUCADA";
        /** Persona anterior a D-27: nunca hubo evento que registrar. */
        public static final String SIN_REGISTRO = "SIN_REGISTRO";
        /** Defensivo: hay evento, pero de un tipo que hoy nadie escribe. */
        public static final String NO_VIGENTE = "NO_VIGENTE";
    }

    /**
     * Proyeccion de la autorizacion para la ficha. Nunca lanza: una persona
     * dada de alta <b>antes</b> de D-27 no tiene evento, y eso no es un error
     * sino un hecho que la ficha tiene que poder decir ({@code SIN_REGISTRO}).
     */
    public Constancia constancia(long idPersona, long idOrganizacion) {
        String vigente = avisoVigente().getVersion();
        Optional<AutorizacionTratamientoEvento> ultimo =
                eventos.ultimoEvento(idOrganizacion, idPersona, OPERACION_BROX);
        if (ultimo.isEmpty()) {
            return new Constancia(Constancia.SIN_REGISTRO, null, null, null, vigente);
        }
        AutorizacionTratamientoEvento evento = ultimo.get();
        String nombre = evento.getRegistradaPor() == null ? null
                : roles.nombreDelTitular(evento.getRegistradaPor(), idOrganizacion).orElse(null);
        return new Constancia(estadoDe(evento), evento.getOcurridoEn(), nombre,
                evento.getVersionAviso(), vigente);
    }

    private String estadoDe(AutorizacionTratamientoEvento evento) {
        if (AutorizacionTratamientoEvento.REVOCADO.equals(evento.getEvento())) {
            return Constancia.REVOCADA;
        }
        boolean otorgada = AutorizacionTratamientoEvento.OTORGADO.equals(evento.getEvento())
                || AutorizacionTratamientoEvento.REOTORGADO.equals(evento.getEvento());
        if (!otorgada) {
            return Constancia.NO_VIGENTE;
        }
        return caducadaPorCambioMaterial(evento.getVersionAviso())
                ? Constancia.CADUCADA
                : Constancia.VIGENTE;
    }

    /**
     * Vista del aviso para la capa web. Existe porque la web NO puede ver
     * entidades (regla de capas verificada por ArchUnit): los DTO son la
     * frontera, y {@link #avisoVigente()} devuelve la entidad, que se queda
     * dentro del service.
     */
    public record AvisoVigente(String version, java.time.OffsetDateTime vigenteDesde,
                               boolean cambioMaterial, String contenido) {
    }

    /** Lo que publica la pagina publica de privacidad. */
    public AvisoVigente avisoParaPublicar() {
        AvisoPrivacidadVersion aviso = avisoVigente();
        return new AvisoVigente(aviso.getVersion(), aviso.getVigenteDesde(),
                aviso.isCambioMaterial(), aviso.getContenido());
    }

    /** Version vigente del aviso, la que se cita al registrar. Uso interno del service. */
    public AvisoPrivacidadVersion avisoVigente() {
        return avisos.findFirstByVigenteHastaIsNull().orElseThrow(() -> new IllegalStateException(
                "No hay ninguna version vigente del aviso de privacidad. V28 siembra la 1.0: si "
                        + "falta, la base no esta migrada."));
    }

    /**
     * Una autorizacion otorgada ANTES del ultimo cambio material ya no vale.
     * Se compara por fecha de vigencia de la version citada, no por el texto de
     * la version: '1.10' es posterior a '1.9' y una comparacion de cadenas dice
     * lo contrario.
     */
    private boolean caducadaPorCambioMaterial(String versionCitada) {
        Optional<AvisoPrivacidadVersion> frontera = avisos.ultimoCambioMaterial();
        if (frontera.isEmpty()) {
            return false;   // nunca hubo cambio material: nada caduca
        }
        if (versionCitada == null) {
            return true;    // no se sabe contra que se autorizo: se vuelve a pedir
        }
        AvisoPrivacidadVersion limite = frontera.get();
        if (versionCitada.equals(limite.getVersion())) {
            return false;
        }
        return avisos.findAll().stream()
                .filter(v -> versionCitada.equals(v.getVersion()))
                .findFirst()
                .map(v -> v.getVigenteDesde().isBefore(limite.getVigenteDesde()))
                .orElse(true);  // version desconocida: se vuelve a pedir
    }
}
