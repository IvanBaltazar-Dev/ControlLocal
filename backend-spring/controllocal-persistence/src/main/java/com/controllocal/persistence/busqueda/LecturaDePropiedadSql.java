package com.controllocal.persistence.busqueda;

/**
 * Relaciones persistidas que delimitan LECTURA (RF007/CAT-E05), antes de
 * buscar, contar o proyectar. No representa permiso de edición ni convierte
 * al responsable físico histórico en custodio del inmueble.
 *
 * El caso de uso aporta los roles comerciales de su contexto vigente. El alias
 * de propiedad es siempre {@code p}; el tenant se comprueba en cada relación.
 */
public final class LecturaDePropiedadSql {
    private LecturaDePropiedadSql() { }

    public static final String PREDICADO = """
             and (exists (select 1 from prospeccion lp
                           where lp.organizacion_id = p.organizacion_id
                             and lp.id_propiedad = p.id_propiedad
                             and lp.id_rol_agente = any(cast(:rolesLectura as bigint[])))
                  or exists (select 1 from captacion lc
                              where lc.organizacion_id = p.organizacion_id
                                and lc.id_propiedad = p.id_propiedad
                                and lc.id_rol_agente = any(cast(:rolesLectura as bigint[])))
                  or (p.id_rol_incorporo = any(cast(:rolesLectura as bigint[]))
                      and not exists (select 1 from prospeccion lo
                                      where lo.organizacion_id = p.organizacion_id
                                        and lo.id_propiedad = p.id_propiedad)
                      and not exists (select 1 from captacion le
                                      where le.organizacion_id = p.organizacion_id
                                        and le.id_propiedad = p.id_propiedad)))
            """;
}
