package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.AsignacionResponsablePropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * <b>Quien puede escribir los hechos de una propiedad, y quien los de un
 * encargo.</b> El unico sitio donde se decide (P0-1..P0-4).
 *
 * <h2>Por que existe, y por que no vive en el {@code @PreAuthorize}</h2>
 * Hasta V87 no habia autoridad ninguna: {@code PUT /propiedades/{id}} cargaba
 * la fila por {@code (organizacion, id)} y escribia. Cualquier AGENTE del
 * tenant editaba la ficha de cualquier propiedad — y de paso el importe, la
 * exclusividad y la vigencia de un ENCARGO ajeno, con su hito {@code U} en la
 * serie economica de otro.
 *
 * <p>La regla <b>no</b> puede vivir en la anotacion del controlador por dos
 * razones medidas:
 * <ol>
 *   <li>Hay <b>ocho vias de escritura</b> sobre la propiedad y su ficha, en
 *       cuatro servicios distintos. Una anotacion protege una puerta; la
 *       autoridad tiene que proteger el hecho.</li>
 *   <li><b>KAIROS entra por los mismos endpoints</b> con la cabecera
 *       {@code X-Origen} y el mismo token. Lo que se compruebe aqui lo hereda
 *       igual; lo que se comprobara en la capa web tendria que reescribirse
 *       para cada canal nuevo, y el segundo canal es donde se olvida.</li>
 * </ol>
 *
 * <h2>Las TRES autoridades, que pueden ser tres personas distintas</h2>
 * <pre>
 *   PROPIEDAD          -> su responsable actual  -> los hechos fisicos
 *   ENCARGO de VENTA   -> su propio agente       -> ese encargo
 *   ENCARGO de ALQUILER-> su propio agente       -> ese encargo
 * </pre>
 * Ser responsable de la propiedad <b>no</b> concede permiso sobre el encargo de
 * otro, y tener un encargo <b>no</b> concede autoridad sobre la ficha fisica.
 * Son independientes en las dos direcciones: un traspaso de propiedad no
 * reasigna encargos, y reasignar un encargo no mueve al responsable.
 *
 * <h2>FALTANTE</h2>
 * {@code id_rol_responsable} NULL significa <b>no se sabe</b>, no "de todos".
 * La propiedad se ve y no se edita, con motivo explicito, hasta que un BROKER
 * asigne. No se infiere de la captacion viva, ni de la prospeccion, ni de
 * {@code id_rol_incorporo}, y <b>no se tapa con un actor sintetico</b>.
 *
 * <p>Y FALTANTE bloquea <b>solo</b> la escritura de la PROPIEDAD: quien tenga
 * un encargo legitimo lo sigue operando, la propiedad se sigue leyendo, se
 * sigue publicando y se sigue cruzando. Este componente no cierra ninguna otra
 * puerta.
 *
 * <h2>Ver no concede editar</h2>
 * BROKER y TENANT_ADMIN conservan supervision y gobierno —y la lectura de todo
 * su alcance— pero <b>no escriben hechos de la propiedad</b>. No es una
 * omision: es la diferencia entre decidir y registrar. El broker decide sobre
 * la propiedad cambiando <b>quien</b> responde por ella, que es lo que hace
 * {@link #asignar}.
 */
@Component
public class AutoridadDePropiedad {

    /**
     * Motivos por los que no se puede editar, en el vocabulario del Core.
     *
     * <p>Viajan al cable para que el SPA <b>no tenga que deducirlos</b>. Sin
     * esto, la pantalla acabaria con su propia copia de la regla —"si el rol es
     * AGENTE y el id coincide…"— que es exactamente la lista de permisos que el
     * cliente no debe llevar.
     */
    public static final String FALTA_RESPONSABLE = "FALTA_RESPONSABLE";
    public static final String OTRO_RESPONSABLE = "OTRO_RESPONSABLE";
    public static final String NO_OPERA = "NO_OPERA";

    private final DetalleAgenteRepository agentes;
    private final AsignacionResponsablePropiedadRepository asignaciones;

    public AutoridadDePropiedad(DetalleAgenteRepository agentes,
                                AsignacionResponsablePropiedadRepository asignaciones) {
        this.agentes = agentes;
        this.asignaciones = asignaciones;
    }

    // ==================================================================
    // Los hechos de la PROPIEDAD
    // ==================================================================

    /**
     * <b>La comprobacion.</b> Corta antes de escribir nada, y con el motivo
     * dicho: "no puedes" sin decir por que obliga al usuario a adivinar si le
     * falta un permiso o le falta un dato.
     */
    public void exigirEdicion(Actor actor, Propiedad propiedad) {
        String motivo = motivoNoEditable(actor, propiedad);
        if (motivo == null) {
            return;
        }
        throw new AccesoNoAutorizadoException(explicacion(motivo));
    }

    /** ¿Puede este actor escribir hechos de esta propiedad? */
    public boolean puedeEditar(Actor actor, Propiedad propiedad) {
        return motivoNoEditable(actor, propiedad) == null;
    }

    /**
     * El motivo, o {@code null} si si puede. Es el mismo metodo que decide y el
     * que informa: si fueran dos, la ficha podria decir "puedes editar" y la
     * edicion responder 403, que es el peor error posible de esta pantalla.
     */
    public String motivoNoEditable(Actor actor, Propiedad propiedad) {
        if (!actor.esAgente()) {
            // Gobernar y supervisar no es registrar. Un BROKER que quiera que
            // esta propiedad se toque, asigna responsable.
            return NO_OPERA;
        }
        if (propiedad == null || !propiedad.tieneResponsable()) {
            return FALTA_RESPONSABLE;
        }
        return Objects.equals(propiedad.getIdRolResponsable(), actor.idRolOperativo())
                ? null : OTRO_RESPONSABLE;
    }

    /**
     * El motivo en palabras. Lo produce el Core y no el cliente: el mismo texto
     * tiene que llegar a BROX Web y a KAIROS, y dos redacciones del mismo
     * rechazo se separan en el primer cambio.
     */
    public String explicacion(String motivo) {
        return switch (motivo) {
            case FALTA_RESPONSABLE -> "Esta propiedad no tiene agente responsable asignado, "
                    + "asi que todavia no la edita nadie. Un broker tiene que asignarlo antes: "
                    + "no se deduce de la captacion ni de quien la incorporo.";
            case OTRO_RESPONSABLE -> "De esta propiedad responde otro agente. Puedes consultarla, "
                    + "y puedes operar los encargos que sean tuyos, pero sus datos los escribe "
                    + "su responsable.";
            case NO_OPERA -> "Supervisar y gobernar no es registrar: los hechos de la propiedad "
                    + "los escribe el agente que responde por ella. Lo que si puedes decidir es "
                    + "quien responde.";
            default -> throw new IllegalArgumentException("Motivo desconocido: " + motivo);
        };
    }

    // ==================================================================
    // Los hechos de un ENCARGO
    // ==================================================================

    /**
     * <b>El ENCARGO lo edita su propio agente</b> (P0-4).
     *
     * <p>Cubre todo lo que es propio del encargo —importe, exclusividad,
     * vigencia y condiciones comerciales gobernadas— y, con ello, el historico
     * economico: un hito {@code U} o {@code P} nace de un encargo, y por tanto
     * de la autoridad de ese encargo. Por eso esta comprobacion se hace tambien
     * en {@code POST /locales/{id}/precios}, que escribia en la serie de
     * cualquiera con solo comprobar el tenant.
     *
     * <p><b>Ser responsable de la propiedad no basta aqui</b>, y no es un
     * descuido: quien negocio la exclusividad de la venta es quien puede
     * cambiarla, aunque otro responda por el inmueble.
     */
    public void exigirEdicionDelEncargo(Actor actor, Captacion encargo) {
        if (encargo == null) {
            throw new ReglaNegocioException("No se dijo de que encargo se trata.");
        }
        Long idAgente = encargo.getAgente() == null ? null : encargo.getAgente().getId();
        if (!actor.esAgente() || !Objects.equals(idAgente, actor.idRolOperativo())) {
            throw new AccesoNoAutorizadoException(
                    "El encargo " + encargo.getCodigoCaptacion() + " lo lleva otro agente. "
                            + "Su importe, su exclusividad, su vigencia y sus condiciones los "
                            + "cambia quien lo negocio; responder por la propiedad no es "
                            + "responder por el encargo de otro.");
        }
    }

    // ==================================================================
    // El alta
    // ==================================================================

    /**
     * <b>Quien la registra responde por ella</b> — y solo en el alta.
     *
     * <p>No es una inferencia: el actor del alta es un hecho conocido, no un
     * valor deducido del caso frecuente. La alternativa —que toda propiedad
     * nazca FALTANTE— dejaria al agente sin poder editar lo que acaba de
     * registrar, y convertiria al broker en un paso obligatorio de cada alta.
     *
     * <p>Que sea <b>solo</b> en el alta es la otra mitad: volver a registrar
     * una propiedad no captura la autoridad de otra, porque el alta crea una
     * fila nueva y jamas toca una existente. Despues del alta la autoridad solo
     * se mueve por {@link #asignar}.
     */
    public void fijarAlAlta(Actor actor, Propiedad propiedad) {
        if (!actor.esAgente()) {
            // No deberia llegar aqui —el alta es AGENTE— pero si un canal nuevo
            // la abriera a otra banda, la propiedad nace FALTANTE antes que con
            // un responsable que no puede operar.
            return;
        }
        propiedad.responsable(actor.idRolOperativo());
    }

    // ==================================================================
    // El traspaso
    // ==================================================================

    /**
     * <b>Cambia quien responde, y deja dicho quien lo cambio.</b>
     *
     * <p>Lo hace un BROKER (o el gobierno del tenant), nunca el agente que
     * quiere la propiedad ni el que la tiene: si el traspaso fuera del propio
     * agente, la autoridad seria autoservicio y no seria autoridad.
     *
     * <p><b>Lo que este metodo NO hace</b>, y es la mitad de la decision:
     * <ul>
     *   <li>no toca ningun atributo inmobiliario — cambiar de responsable no
     *       cambia el inmueble;</li>
     *   <li>no reasigna ningun ENCARGO — el de venta y el de alquiler siguen
     *       siendo de quien eran, y sus condiciones tambien;</li>
     *   <li>no destruye la historia anterior — el responsable saliente pierde
     *       la autoridad de escritura, no su rastro;</li>
     *   <li>no abre ningun historico: el traspaso concede <b>escritura sobre lo
     *       vigente</b>, y no cambia una sola linea de lo que el nuevo
     *       responsable puede leer.</li>
     * </ul>
     */
    public AsignacionResponsablePropiedad asignar(Actor actor, Propiedad propiedad,
                                                  long idRolAgenteNuevo, String motivo) {
        if (actor.esAgente()) {
            throw new AccesoNoAutorizadoException(
                    "Quien responde por una propiedad lo decide un broker. Un agente no puede "
                            + "traspasarsela ni traspasarla.");
        }
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.isEmpty()) {
            throw new ReglaNegocioException(
                    "El traspaso necesita un motivo: sin el, el expediente dice que la propiedad "
                            + "cambio de manos y no dice por que.");
        }
        DetalleAgente nuevo = agentes.findById(idRolAgenteNuevo)
                .filter(a -> Objects.equals(a.getOrganizacionId(), actor.idOrganizacion()))
                .orElseThrow(() -> new ReglaNegocioException(
                        "Ese agente no existe en la organizacion, asi que no puede responder por "
                                + "ninguna propiedad suya."));

        Long anterior = propiedad.getIdRolResponsable();
        if (Objects.equals(anterior, nuevo.getId())) {
            throw new ReglaNegocioException(
                    "Ese agente ya responde por esta propiedad. Un traspaso que no traspasa no es "
                            + "un hecho, y dejaria una linea \"de A a A\" en su expediente.");
        }

        propiedad.responsable(nuevo.getId());

        AsignacionResponsablePropiedad fila = new AsignacionResponsablePropiedad();
        fila.setOrganizacionId(actor.idOrganizacion());
        fila.setIdPropiedad(propiedad.getId());
        fila.setIdRolResponsableAnterior(anterior);
        fila.setIdRolResponsableNuevo(nuevo.getId());
        fila.setIdPersonaActor(actor.idPersona());
        fila.setTipoRolActor(actor.tipoRolOperativo());
        fila.setMotivo(texto);
        return asignaciones.save(fila);
    }

    /** El expediente de traspasos de una propiedad, el mas reciente primero. */
    public List<AsignacionResponsablePropiedad> historial(long idOrganizacion, long idPropiedad) {
        return asignaciones
                .findByOrganizacionIdAndIdPropiedadOrderByFechaAsignacionDescIdDesc(
                        idOrganizacion, idPropiedad);
    }

    /** Nombre del responsable actual, o {@code null} si esta FALTANTE. */
    public String nombreDelResponsable(Propiedad propiedad) {
        if (propiedad == null || !propiedad.tieneResponsable()) {
            return null;
        }
        return agentes.findById(propiedad.getIdRolResponsable())
                .map(AutoridadDePropiedad::nombreDe)
                .orElse(null);
    }

    private static String nombreDe(DetalleAgente agente) {
        if (agente.getRol() == null || agente.getRol().getPersona() == null) {
            return null;
        }
        return agente.getRol().getPersona().getNombresORazonSocial();
    }
}
