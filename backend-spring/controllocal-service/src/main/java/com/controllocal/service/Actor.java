package com.controllocal.service;

/**
 * Identidad del actor que ejecuta un caso de uso (Doc 5 §7):
 * <ul>
 *   <li>{@code idOrganizacion} = organizacion.id del tenant en cuyo nombre
 *       actua (D-16/D-20, V6). Es la frontera MAS EXTERNA: se aplica antes
 *       que el rol. Lo resuelve SIEMPRE el backend a partir de la sesion —
 *       el cliente nunca lo envia — y es lo que se estampa en cada fila
 *       privada que el caso de uso crea.</li>
 *   <li>{@code idPersona} = persona.id — identidad UNICA (Party-Role); es el
 *       actor que se graba en historial_estado.</li>
 *   <li>{@code idRolOperativo} = persona_rol.id del rol con el que actua
 *       (BROKER/AGENTE, o el rol ADMIN de gobierno). Equivale al
 *       idAgente/idBroker de la v1 y da el alcance por fila.</li>
 *   <li>{@code rolEfectivo} = banda REAL en el tenant, resuelta en servidor
 *       desde la membresia: {@code AGENTE | BROKER | TENANT_ADMIN}.</li>
 * </ul>
 *
 * <p><b>El rol efectivo no es el rol del token</b> (Bloque 5, D-S0-8). El
 * token sigue diciendo {@code ADMIN} porque su formato esta congelado y solo
 * admite tres valores mientras GlassFish conviva (R1); la banda de verdad la
 * resuelve el backend en cada peticion, igual que ya hacia con el tenant. Dos
 * consecuencias que conviene tener presentes:
 * <ul>
 *   <li>{@code TENANT_ADMIN} <b>no</b> es el {@code ADMIN} de antes. Aquel era
 *       un broker con un booleano y entraba por herencia a 18 operaciones
 *       comerciales; este gobierna la organizacion y no firma hechos del
 *       negocio (D-S0-7, matriz D-S0-17).</li>
 *   <li>Degradar a alguien surte efecto en la siguiente peticion, sin esperar
 *       a que caduque su token.</li>
 * </ul>
 */
public record Actor(long idOrganizacion, long idPersona, long idRolOperativo, String rolEfectivo) {

    /** Gobierna el tenant: cuentas, membresias y organigrama. No opera. */
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    /** Supervisa su equipo y decide los hechos comerciales. */
    public static final String BROKER = "BROKER";
    /** Registra y opera lo suyo. */
    public static final String AGENTE = "AGENTE";

    /**
     * Rol que se graba en {@code historial_estado.tipo_rol_actor}.
     *
     * <p>Antes traducia {@code ADMIN -> BROKER} y por eso la auditoria no
     * distinguia quien habia actuado (H-09). Ya no traduce nada: si una
     * persona gobierna Y opera, lleva dos roles explicitos y el rastro dice
     * cual uso.
     */
    public String tipoRolOperativo() {
        return rolEfectivo;
    }

    public boolean esTenantAdmin() {
        return TENANT_ADMIN.equals(rolEfectivo);
    }

    public boolean esAgente() {
        return AGENTE.equals(rolEfectivo);
    }

    public boolean esBroker() {
        return BROKER.equals(rolEfectivo);
    }
}
