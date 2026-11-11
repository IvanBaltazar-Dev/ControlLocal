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
 * <p>La cuenta global no equivale a Persona ni concede por sí sola un contexto.
 * Core resuelve la pertenencia actual y la atribución seleccionada; la sesión durable
 * y su revisión se comprueban antes de operar. TENANT_ADMIN gobierna; BROKER opera
 * dentro de su equipo. Quien acumula ambas atribuciones elige la que ejerce y traza.
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
