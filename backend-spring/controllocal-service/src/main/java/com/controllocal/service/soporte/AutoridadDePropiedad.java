package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.AsignacionResponsablePropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
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
 *   <li>Las vias de escritura sobre la propiedad y su ficha estan repartidas
 *       por <b>varios servicios</b>, y no son las que nadie recuerde: las
 *       cuenta {@code AutoridadDeLaPropiedadTest} en cada build, contra el
 *       bytecode. Aqui no va la cifra a proposito — la del inventario inicial
 *       ya se quedo corta el mismo dia, cuando el gate encontro una via mas
 *       que el inventario no tenia. Una anotacion protege <b>una puerta</b>;
 *       la autoridad tiene que proteger <b>el hecho</b>.</li>
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

    /**
     * Quien alcanza a quien dentro del tenant. La frontera de organizacion va
     * <b>antes</b> que cualquier alcance de equipo, y las dos van antes que el
     * rol: es el orden que fija C1.
     */
    private final Alcances alcances;

    public AutoridadDePropiedad(DetalleAgenteRepository agentes,
                                AsignacionResponsablePropiedadRepository asignaciones,
                                Alcances alcances) {
        this.agentes = agentes;
        this.asignaciones = asignaciones;
        this.alcances = alcances;
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
     * <b>Quien registra una propiedad NUEVA responde por ella</b> — y solo ahi.
     *
     * <p>No es una inferencia: el actor del alta es un hecho conocido, no un
     * valor deducido del caso frecuente. La alternativa —que toda propiedad
     * nazca FALTANTE— dejaria al agente sin poder editar lo que acaba de
     * registrar, y convertiria al broker en un paso obligatorio de cada alta.
     *
     * <h2>El limite, que es la mitad importante</h2>
     * Esto vale <b>unica y exclusivamente cuando nace una fila de
     * {@code propiedad}</b>. Que otro agente vuelva a captar una propiedad
     * existente, le abra un ENCARGO nuevo, la retome o la vuelva a trabajar
     * <b>no</b> lo convierte en responsable; y una propiedad historica FALTANTE
     * <b>sigue FALTANTE</b> hasta que un BROKER asigne. Una propiedad que ya
     * existe solo cambia de manos por {@link #asignar}.
     *
     * <p><b>Detectar o reutilizar una propiedad existente no puede llamar
     * aqui.</b> Y no se deja en un comentario: el indice parcial
     * {@code uq_asignacion_alta_por_propiedad} (V88) hace que una <b>segunda</b>
     * fila de origen {@code ALTA} sobre la misma propiedad no entre en la base,
     * venga del canal que venga y la escriba quien la escriba.
     *
     * <h2>Va en dos tiempos, y por la misma razon que el linaje</h2>
     * La columna se fija <b>antes</b> del primer {@code save} —para que la fila
     * nazca ya con su responsable— y el rastro se escribe <b>despues</b>, con
     * {@link #anotarElAlta}: el rastro se direcciona por el id de la propiedad,
     * que antes del insert todavia no existe. Es exactamente el mismo orden en
     * dos tiempos que ya usa {@code AtributosGobernados} para los
     * estructurales, y por el mismo motivo.
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

    /**
     * <b>El alta, en el expediente</b> (V88).
     *
     * <p>Se llama <b>despues</b> del {@code save} de la propiedad. Sin esta
     * fila, {@code id_rol_responsable} aparecia poblada y el expediente no
     * decia de donde salio — un valor de autoridad sin acto que lo explique es
     * justo lo que este P0 vino a quitar.
     *
     * <p>La fila nace {@code origen = ALTA} y <b>sin predecesor</b>: no hay a
     * quien desplazar, porque la propiedad acaba de existir. Ese hueco es
     * informacion y no se rellena con nada.
     *
     * <p>Si el actor no es agente no se escribe nada, en coherencia con
     * {@link #fijarAlAlta}: no habria responsable que anotar.
     */
    public AsignacionResponsablePropiedad anotarElAlta(Actor actor, Propiedad propiedad) {
        if (!actor.esAgente() || !propiedad.tieneResponsable()) {
            return null;
        }
        AsignacionResponsablePropiedad fila = new AsignacionResponsablePropiedad();
        fila.setOrganizacionId(actor.idOrganizacion());
        fila.setIdPropiedad(propiedad.getId());
        fila.setIdRolResponsableAnterior(null);
        fila.setIdRolResponsableNuevo(propiedad.getIdRolResponsable());
        fila.setIdPersonaActor(actor.idPersona());
        fila.setTipoRolActor(actor.tipoRolOperativo());
        fila.setOrigen(AsignacionResponsablePropiedad.ORIGEN_ALTA);
        // El motivo es obligatorio y aqui no lo escribe una persona: lo dice el
        // acto. Se redacta en el Core y no en cada cliente, para que las altas
        // de BROX Web y las de KAIROS digan lo mismo en el expediente.
        fila.setMotivo("Alta de la propiedad: la registro este agente, que responde por ella "
                + "desde su creacion.");
        return asignaciones.save(fila);
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
     *
     * <h2>Hasta donde llega cada banda (C1)</h2>
     * El orden de las comprobaciones no es casual — <b>la frontera del tenant va
     * siempre antes que cualquier permiso de rol</b>:
     * <pre>
     *   ¿misma organizacion?  NO -&gt; el agente destino no existe, y punto
     *                         SI |
     *   ¿que banda?              v
     *     BROKER        -&gt; solo los agentes que SUPERVISA
     *     TENANT_ADMIN  -&gt; cualquier agente de SU tenant, aunque sea del
     *                      equipo de otro broker. Esta exento de la
     *                      restriccion de EQUIPO, nunca de la de TENANT
     *     AGENTE        -&gt; nunca
     * </pre>
     * Es el mismo {@link Alcances#alcanza} que usa {@code reasignar} para el
     * encargo, y a proposito: dos implementaciones del mismo alcance divergen,
     * y divergen hacia el lado que concede de mas.
     */
    public AsignacionResponsablePropiedad asignar(Actor actor, Propiedad propiedad,
                                                  long idRolAgenteNuevo, String motivo) {
        if (actor.esAgente()) {
            throw new AccesoNoAutorizadoException(
                    "Quien responde por una propiedad lo decide un broker. Un agente no puede "
                            + "traspasarsela ni traspasarla.");
        }
        // El MISMO minimo que exige reasignar un encargo (H6). Antes bastaba
        // con que no viniera vacio: un "ok" de dos caracteres entraba en una
        // tabla append-only que nadie puede corregir despues. `V87` declara
        // esta tabla "el mismo tipo de hecho un nivel mas arriba" que la
        // reasignacion de captacion, asi que no puede pedir menos que ella.
        String texto = PoliticaComercial.exigirMotivoDeReasignacion(motivo);

        // FRONTERA DE TENANT, y va primero. Un agente de otra corredora se
        // comporta como inexistente: el mensaje es el mismo que si el id no
        // existiera en ninguna parte, para no confirmar por la puerta de atras
        // que ese rol existe en otra organizacion.
        DetalleAgente nuevo = agentes.findById(idRolAgenteNuevo)
                .filter(a -> Objects.equals(a.getOrganizacionId(), actor.idOrganizacion()))
                .orElseThrow(() -> new ReglaNegocioException(
                        "Ese agente no existe en la organizacion, asi que no puede responder por "
                                + "ninguna propiedad suya."));

        // Y DESPUES el alcance de equipo. `alcanza` devuelve true para el
        // TENANT_ADMIN —gobierna la organizacion entera, incluidos los equipos
        // de otros brokers— y para el BROKER solo si supervisa a ese agente.
        // La frontera de tenant ya quedo cerrada arriba, asi que este `true`
        // del gobierno nunca sale de su organizacion.
        if (!alcances.alcanza(actor, nuevo.getId())) {
            throw new AccesoNoAutorizadoException(
                    "No supervisas a ese agente, asi que no puedes ponerlo a responder por esta "
                            + "propiedad. Un broker asigna dentro de su equipo; entre equipos lo "
                            + "decide el gobierno de la organizacion.");
        }

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
        // Y declara su origen (V88). No se deduce del predecesor: la PRIMERA
        // asignacion de una propiedad FALTANTE tampoco lo tiene, y es un
        // traspaso -- 12 de las 63 filas medidas estaban en ese caso.
        fila.setOrigen(AsignacionResponsablePropiedad.ORIGEN_TRASPASO);
        fila.setMotivo(texto);
        return asignaciones.save(fila);
    }

    /**
     * <b>El expediente de traspasos es superficie de GOBIERNO, no de operacion</b>
     * (C2).
     *
     * <p>Lo consultan el BROKER que supervisa a quien responde por la propiedad
     * y el TENANT_ADMIN dentro de su tenant. <b>El AGENTE no</b>, y eso incluye
     * al responsable vigente: sabe que responde el —se lo dice
     * {@code responsabilidad} en su ficha— y tiene lo que necesita para operar,
     * pero <b>no hereda</b> los responsables anteriores, los motivos de cada
     * traspaso ni las observaciones de gobierno sobre agentes que ya no la
     * llevan. El texto libre del motivo es dato interno de gobierno.
     *
     * <p><b>Ver quien responde no es ver por que cambio de manos</b>: son dos
     * preguntas distintas y solo la segunda es un expediente.
     *
     * <h2>Una propiedad FALTANTE y el broker (C5)</h2>
     * <b>Cualquier BROKER del tenant la alcanza.</b> Gobernar el inventario sin
     * dueno es trabajo de broker: es justo lo que tiene que mirar para decidir a
     * quien asignarlo. La regla "sus supervisados vigentes" existe para <b>no
     * cruzar equipos</b>, y sin responsable no hay a quien supervisar — asi que
     * esa regla no tiene sobre que aplicarse, y el limite efectivo vuelve a ser
     * el que va siempre delante: <b>el tenant</b>.
     *
     * <p>Lo decide {@link Alcances#alcanzaIncluidoSinDueno}, no una rama aqui.
     * La version anterior de este corte denegaba —heredando el {@code false}
     * que {@link Alcances#alcanza} devuelve ante un dueno nulo <b>antes</b> de
     * mirar la banda—, y con las 26 propiedades de {@code dev} en FALTANTE eso
     * dejaba el expediente practicamente sin lectores. Que no vuelva a pasar en
     * la siguiente superficie depende de que la respuesta salga <b>del sitio que
     * decide alcances</b>, y no de un caso especial en el borde.
     *
     * <p>Si el nuevo responsable necesita contexto, la respuesta <b>no</b> es
     * abrirle el expediente: seria una nota de traspaso operativa, distinta del
     * historico de gobierno, y todavia no existe.
     */
    public void exigirLecturaDelExpediente(Actor actor, Propiedad propiedad) {
        if (actor.esAgente()) {
            throw new AccesoNoAutorizadoException(
                    "El expediente de traspasos es informacion de gobierno. Puedes ver quien "
                            + "responde hoy por esta propiedad y operar lo que sea tuyo, pero los "
                            + "responsables anteriores y los motivos de cada cambio los consulta "
                            + "quien supervisa.");
        }
        // Y el alcance lo decide `Alcances`, que es quien decide alcances. Se
        // pregunta `alcanzaIncluidoSinDueno` y no `alcanza` porque son dos
        // preguntas distintas: aquella niega cuando no hay dueno -y cinco
        // llamadores dependen de eso-, y aqui una propiedad FALTANTE es
        // justamente la que el broker tiene que poder mirar para decidir a
        // quien asignarla (C5).
        //
        // La frontera de tenant ya se comprobo ANTES de llegar aqui: la fila se
        // cargo por (organizacion, id) y un id de otra corredora respondio 404.
        if (!alcances.alcanzaIncluidoSinDueno(actor, propiedad.getIdRolResponsable())) {
            throw new AccesoNoAutorizadoException(
                    "Esta propiedad responde ante un agente que no supervisas, asi que su "
                            + "expediente de traspasos no esta en tu alcance.");
        }
    }

    /** El expediente de traspasos de una propiedad, el mas reciente primero. */
    public List<AsignacionResponsablePropiedad> historial(long idOrganizacion, long idPropiedad) {
        return asignaciones
                .findByOrganizacionIdAndIdPropiedadOrderByFechaAsignacionDescIdDesc(
                        idOrganizacion, idPropiedad);
    }

    /**
     * <b>La autoridad, resuelta y lista para viajar al cliente.</b>
     *
     * <p>Vive aqui —y no en cada servicio que devuelve una ficha— porque hay
     * <b>dos pantallas</b> con acciones de escritura sobre la misma propiedad:
     * la ficha universal ({@code GET /propiedades/{id}}) y la ficha del encargo
     * con su galeria ({@code GET /locales/{id}}). La primera version de este P0
     * resolvio la autoridad dentro de {@code PropiedadUniversalServiceImpl} y
     * dejo a la segunda calculandola en Angular con la regla vieja
     * ({@code rol === 'AGENTE'}), que dejo de ser cierta con V87: el boton se
     * ofrecia a todo agente sobre toda propiedad y respondia 403 siempre.
     *
     * <p>La leccion no es "faltaba una pantalla": es que <b>el inventario se
     * hizo por servicio y las pantallas no se reparten por servicio</b>. Con un
     * solo productor, la tercera pantalla que aparezca no puede nacer con una
     * copia distinta de la regla, porque no hay copia que hacer.
     *
     * <p>Lo produce el <b>mismo metodo</b> que despues deniega la escritura. Si
     * fueran dos, la ficha podria decir "puedes editar" y la escritura contestar
     * 403 — el peor fallo posible de esta pantalla, porque el usuario ya
     * escribio.
     */
    public PropiedadUniversalService.Responsabilidad responsabilidadDe(Actor actor,
                                                                      Propiedad propiedad) {
        String motivo = motivoNoEditable(actor, propiedad);
        return new PropiedadUniversalService.Responsabilidad(
                propiedad == null ? null : propiedad.getIdRolResponsable(),
                nombreDelResponsable(propiedad),
                motivo == null,
                motivo,
                motivo == null ? null : explicacion(motivo),
                // Quien puede decidir QUIEN responde. Es la misma condicion que
                // abre `asignar` -- no ser agente -- y por eso se lee de aqui y
                // no de una segunda tabla: la ficha no puede ofrecer un boton
                // que el POST vaya a rechazar por banda.
                !actor.esAgente());
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
