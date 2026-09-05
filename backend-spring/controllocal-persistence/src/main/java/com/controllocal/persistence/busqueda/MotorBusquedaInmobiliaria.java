package com.controllocal.persistence.busqueda;

import com.controllocal.persistence.repositorio.PlanDeConsulta;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>La UNICA busqueda sobre la cartera de inmuebles.</b>
 *
 * <h2>Por que existe, con nombre y fecha</h2>
 * Hasta el 2026-09-02 habia dos. {@code GET /locales} usaba la reescritura por
 * <b>conjunto de candidatos</b> que cerro RC-003; {@code GET /propiedades} -el
 * listado universal, el que el producto usa de verdad- llevaba una consulta
 * paralela: un unico {@code OR} de cuatro {@code like} que cruza
 * {@code propiedad}, {@code persona_rol} y {@code persona}. Es exactamente el
 * predicado que RC-003 documento como <b>Seq Scan</b>, porque PostgreSQL no
 * puede combinar los indices trigrama de tres tablas dentro de un OR, y el
 * conteo del sobre paginado paga ese barrido entero <b>en cada peticion</b>.
 *
 * <p>Las dos leian LA MISMA TABLA. No eran dos problemas: era el mismo problema
 * resuelto una vez bien y otra vez a mano. Un p95 verde en {@code /locales} no
 * decia nada del universal, y nadie estaba midiendo el universal.
 *
 * <h2>La estrategia, en una frase</h2>
 * Resolver <b>que ids</b> entran -en la base, con el tenant y todos los filtros
 * dentro, antes del LIMIT- y despues cargar la proyeccion de cada recurso solo
 * para los ids de esa pagina. El total sale del <b>mismo conjunto</b> que la
 * pagina, asi que la lista y el contador no pueden discrepar.
 *
 * <pre>
 *                     BUSQUEDA INMOBILIARIA
 *                             |
 *                    conjunto de candidatos
 *                             |
 *                  +----------+----------+
 *                  |                     |
 *              /locales             /propiedades
 *           campos de LOCAL       campos universales
 * </pre>
 *
 * <h2>Que es comun y que es configuracion</h2>
 * Comun: el tenant en todas las ramas, la normalizacion del texto, el estado
 * legado, <b>las tres ramas de texto -codigo/direccion/distrito, rubro y
 * propietario-</b>, el conjunto de candidatos, el conteo, la paginacion en SQL y
 * el orden por clave primaria. Configuracion de cada recurso
 * ({@link CriterioBusquedaInmobiliaria}): si hay filtros de inmueble -tipo,
 * distrito, operaciones vivas- y el sentido del orden.
 *
 * <h2>Por que el SQL se compone y no es un @Query fijo</h2>
 * Porque las diferencias entre los dos recursos son <b>estructurales</b>, no
 * valores: un {@code join} que sobra, dos {@code EXISTS} que solo pide uno, un
 * {@code order by} al reves. Metidas en un unico {@code @Query} con parametros
 * -{@code and (:tipo is null or ...)}, {@code order by case when :desc ...}-
 * quedarian dentro del plan igualmente y le quitarian al planificador
 * justamente lo que RC-003 le dio. Aqui cada recurso recibe el SQL minimo que
 * su criterio necesita, y sigue habiendo <b>una sola</b> definicion de cada
 * fragmento.
 *
 * <p>Nada de lo que se concatena viene del llamante: el orden sale de
 * {@link OrdenDelListado}, el estado de un {@code switch} cerrado sobre el
 * vocabulario legado, y todo lo demas -texto, tipo, distrito- viaja como
 * parametro con nombre.
 */
@Repository
public class MotorBusquedaInmobiliaria {

    // ------------------------------------------------------------------
    // Fragmentos. Cada uno esta escrito UNA vez y lo comparten los dos
    // recursos: si la normalizacion del texto cambia, cambia para los dos, y
    // no hay forma de que uno se entere y el otro no.
    // ------------------------------------------------------------------

    /**
     * La normalizacion del texto, identica en todas las ramas <b>por
     * construccion</b>: es la misma expresion concatenada en todas, no dos
     * expresiones que casualmente se parecen.
     */
    private static final String PATRON_TEXTO =
            " like lower(concat('%', cast(:texto as varchar), '%'))";

    /** Rama 1: lo que vive en la propia fila del inmueble. */
    private static final String RAMA_INMUEBLE = """
            select p.id_propiedad as id
              from propiedad p
             where p.organizacion_id = :idOrganizacion
            """;

    /**
     * Rama 2: el <b>rubro</b>, que es un atributo gobernado y no una columna.
     *
     * <p><b>Es canonica: la piden los DOS recursos</b> (C0-a, 2026-09-02). Desde
     * V71 {@code rubro_permitido} vive en {@code atributo_propiedad} como
     * atributo gobernado de PROPIEDAD, no como un campo del local, asi que
     * buscarlo es buscar la cartera y no una particularidad del recurso
     * heredado.
     *
     * <p>Que el rubro solo <b>aplique</b> a ALMACEN, LOCAL y OFICINA no es razon
     * para excluir la rama del listado universal: las propiedades de los otros
     * cuatro tipos simplemente no producen candidatos por aqui. Una rama que no
     * casa no inventa filas —eso lo comprueba el control negativo de
     * {@code propiedades-listado}—; excluirla, en cambio, hacia que un almacen
     * buscado por su rubro fuera invisible en el unico listado que el producto
     * usa de verdad.
     */
    private static final String RAMA_RUBRO = """
            select a.id_propiedad as id
              from atributo_propiedad a
              join propiedad p on p.id_propiedad = a.id_propiedad
             where a.organizacion_id = :idOrganizacion and p.organizacion_id = :idOrganizacion
            """;

    /** Rama 3: el nombre del representante de la titularidad. */
    private static final String RAMA_PROPIETARIO = """
            select p.id_propiedad as id
              from propiedad p
              left join persona_rol rp on rp.id_persona_rol = p.id_rol_propietario
              left join persona per on per.id_persona = rp.id_persona
             where p.organizacion_id = :idOrganizacion
            """;

    /** El estado legado D/N/I derivado de las dos columnas que lo componen. */
    private static final String ESTADO_DERIVADO = """
            case when p.estado_registro = 'I' then 'I'
                 when p.disponibilidad_comercial = 'D' then 'D'
                 else 'N' end
            """;

    @PersistenceContext
    private EntityManager em;

    private final PlanDeConsulta plan;

    public MotorBusquedaInmobiliaria(PlanDeConsulta plan) {
        this.plan = plan;
    }

    /**
     * <b>Que ids entran y cuantos son en total.</b>
     *
     * <p>Los dos numeros salen del mismo conjunto y en la misma transaccion: la
     * pagina no puede traer una fila que el total no cuente.
     */
    public ConjuntoDeCandidatos resolver(CriterioBusquedaInmobiliaria criterio) {
        // Solo con texto. `SET LOCAL` fuera de transaccion no hace nada, y sin
        // un LIKE parametrizado no hay plan generico del que defenderse; en el
        // listado sin texto replanificar seria coste sin ganancia.
        if (criterio.tieneTexto()) {
            plan.forzarPersonalizado();
        }

        // El conjunto proyecta siempre una columna `id`, de modo que el
        // envoltorio que ordena y recorta es el mismo para los dos recursos: lo
        // unico que cambia entre ellos es el sentido del orden.
        String conjunto = conjunto(criterio);

        Query consultaPagina = em.createNativeQuery(
                "select x.id from (" + conjunto + ") x"
                        + " order by x.id " + criterio.orden().sql()
                        + " limit :limite offset :desplazamiento");
        aplicarParametros(consultaPagina, criterio);
        consultaPagina.setParameter("limite", criterio.tamano());
        consultaPagina.setParameter("desplazamiento", criterio.desplazamiento());

        List<Long> ids = new ArrayList<>();
        for (Object fila : consultaPagina.getResultList()) {
            ids.add(aLong(fila));
        }

        Query consultaTotal = em.createNativeQuery("select count(*) from (" + conjunto + ") x");
        aplicarParametros(consultaTotal, criterio);
        long total = aLong(consultaTotal.getSingleResult());

        return new ConjuntoDeCandidatos(List.copyOf(ids), total);
    }

    /**
     * Los contadores por estado legado sobre <b>el mismo conjunto</b> que la
     * lista, en un solo {@code group by} de la base.
     *
     * <p>Existe para que el KPI no se deduzca de las filas descargadas: un
     * contador contado en el cliente solo ve la pagina visible. Se invoca con
     * el criterio <b>sin estado</b> -el resumen cuenta los tres cubos, no
     * filtra por uno- y devuelve solo los estados presentes; completar con cero
     * los que falten es del llamante.
     */
    public Map<String, Long> contarPorEstadoLegado(CriterioBusquedaInmobiliaria criterio) {
        if (criterio.tieneTexto()) {
            plan.forzarPersonalizado();
        }
        String sql = criterio.tieneTexto()
                ? "select " + ESTADO_DERIVADO + " as estado, count(*) as total"
                  + " from (" + ramas(criterio) + ") c"
                  + " join propiedad p on p.id_propiedad = c.id"
                  + " where p.organizacion_id = :idOrganizacion" + filtrosDeInmueble(criterio)
                  + " group by 1"
                : "select " + ESTADO_DERIVADO + " as estado, count(*) as total"
                  + " from propiedad p where p.organizacion_id = :idOrganizacion"
                  + filtroDeEstado(criterio) + filtrosDeInmueble(criterio)
                  + " group by 1";
        Query consulta = em.createNativeQuery(sql);
        aplicarParametros(consulta, criterio);

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (Object fila : consulta.getResultList()) {
            Object[] columnas = (Object[]) fila;
            porEstado.put((String) columnas[0], aLong(columnas[1]));
        }
        return porEstado;
    }

    // ------------------------------------------------------------------
    // Composicion
    // ------------------------------------------------------------------

    /**
     * El conjunto de filas candidatas, ya cerrado: tenant, estado, texto y
     * filtros de inmueble dentro. Lo unico que queda fuera es el orden y el
     * recorte, que son de la pagina y no del conjunto.
     */
    private String conjunto(CriterioBusquedaInmobiliaria criterio) {
        if (!criterio.tieneTexto()) {
            // Sin texto no hay nada que unir: el filtro baja al WHERE y el
            // indice del listado lo sirve directo.
            return "select p.id_propiedad as id from propiedad p"
                    + " where p.organizacion_id = :idOrganizacion"
                    + filtroDeEstado(criterio) + filtrosDeInmueble(criterio);
        }
        if (!criterio.tieneFiltrosDeInmueble()) {
            // La forma que `/locales` lleva midiendo desde RC-003, intacta: sin
            // volver a unir contra `propiedad` cuando no hay nada mas que
            // preguntarle.
            return "select c.id as id from (" + ramas(criterio) + ") c";
        }
        // El universal si tiene mas que preguntar. Las ramas ya cerraron tenant
        // y estado -y cada una uso su indice trigrama-; el join de vuelta es un
        // acceso por clave primaria sobre un conjunto ya reducido, no un
        // barrido.
        return "select c.id as id from (" + ramas(criterio) + ") c"
                + " join propiedad p on p.id_propiedad = c.id"
                + " where p.organizacion_id = :idOrganizacion"
                + filtrosDeInmueble(criterio);
    }

    /**
     * Las ramas del texto, unidas.
     *
     * <p>{@code UNION} y no {@code UNION ALL}: un inmueble puede casar por
     * varias ramas a la vez -el mismo texto en el codigo y en la direccion- y
     * no debe contarse dos veces.
     *
     * <p>El tenant y el estado viajan en TODAS: el conjunto tiene que quedar
     * cerrado antes de unirse, no despues, o el conteo y la pagina discreparian.
     */
    private String ramas(CriterioBusquedaInmobiliaria criterio) {
        String estado = filtroDeEstado(criterio);
        List<String> ramas = new ArrayList<>();
        ramas.add(RAMA_INMUEBLE + estado
                + " and (lower(p.codigo)" + PATRON_TEXTO
                + " or lower(p.direccion)" + PATRON_TEXTO
                + " or lower(p.distrito)" + PATRON_TEXTO + ")");
        // Las tres ramas son canonicas y van SIEMPRE. Hubo una version de este
        // motor en que la del rubro era opcional por recurso; se retiro el
        // mismo dia (C0-a): dejaba a `/locales` como autoridad especial sobre
        // un atributo que es de la propiedad, no suyo.
        ramas.add(RAMA_RUBRO + estado
                + " and a.clave = 'rubro_permitido'"
                + " and lower(a.valor_texto)" + PATRON_TEXTO);
        ramas.add(RAMA_PROPIETARIO + estado
                + " and lower(per.nombres_o_razon_social)" + PATRON_TEXTO);
        return String.join(" union ", ramas);
    }

    /**
     * El estado legado, escrito como el predicado EXACTO del valor pedido.
     *
     * <p>No va como parametro a proposito. El vocabulario es cerrado y lo valida
     * el llamante antes de llegar aqui, asi que lo unico que puede entrar son
     * estas tres letras; y un predicado concreto le dice al planificador lo que
     * una cadena de {@code or} con un parametro dentro le esconde. El
     * {@code default} no es defensivo por costumbre: es la garantia de que un
     * vocabulario nuevo no se cuela en el SQL por concatenacion.
     */
    private String filtroDeEstado(CriterioBusquedaInmobiliaria criterio) {
        if (criterio.estado() == null) {
            return "";
        }
        return switch (criterio.estado()) {
            case "I" -> " and p.estado_registro = 'I'";
            case "D" -> " and p.estado_registro = 'A' and p.disponibilidad_comercial = 'D'";
            // En SQL `NULL <> 'D'` es UNKNOWN, no TRUE: sin nombrar el NULL, una
            // propiedad que todavia nadie ha encargado salia como 'N' en la fila
            // y no aparecia al filtrar por 'N' (V75).
            case "N" -> " and p.estado_registro = 'A'"
                    + " and (p.disponibilidad_comercial is null or p.disponibilidad_comercial <> 'D')";
            default -> throw new IllegalArgumentException(
                    "Estado de listado fuera del vocabulario legado: \"" + criterio.estado()
                            + "\". Validarlo es de quien construye el criterio.");
        };
    }

    /**
     * Los filtros propios del listado universal. {@code /locales} no los usa y
     * por eso no aparecen en su SQL: el fragmento vacio es la configuracion.
     *
     * <p>La operacion se pregunta con <b>EXISTS</b> y no con una igualdad porque
     * no existe ninguna columna que consultar: "en venta" es <i>tiene un encargo
     * de venta vivo</i>, y "venta y alquiler" es <i>tiene los dos</i>. Vivo es
     * {@code estado in (P,O,A)}, la misma definicion que impone
     * {@code uq_captacion_viva_por_operacion}.
     */
    private String filtrosDeInmueble(CriterioBusquedaInmobiliaria criterio) {
        StringBuilder sql = new StringBuilder();
        if (criterio.tipoPropiedad() != null) {
            sql.append(" and p.tipo_inmueble = :tipo");
        }
        if (criterio.distrito() != null) {
            sql.append(" and lower(p.distrito) = lower(cast(:distrito as varchar))");
        }
        if (criterio.conVenta()) {
            sql.append(existeEncargoVivo("V", "cv"));
        }
        if (criterio.conAlquiler()) {
            sql.append(existeEncargoVivo("A", "ca"));
        }
        return sql.toString();
    }

    private static String existeEncargoVivo(String motivoOperacion, String alias) {
        return " and exists (select 1 from captacion " + alias
                + " where " + alias + ".id_propiedad = p.id_propiedad"
                + " and " + alias + ".organizacion_id = p.organizacion_id"
                + " and " + alias + ".motivo_operacion = '" + motivoOperacion + "'"
                + " and " + alias + ".estado in ('P', 'O', 'A'))";
    }

    /**
     * Se enlaza SOLO lo que el SQL compuesto menciona: Hibernate rechaza un
     * parametro que no aparece en la consulta, y esa es justamente la red que
     * hace falta cuando el SQL se compone.
     */
    private void aplicarParametros(Query consulta, CriterioBusquedaInmobiliaria criterio) {
        consulta.setParameter("idOrganizacion", criterio.idOrganizacion());
        if (criterio.tieneTexto()) {
            consulta.setParameter("texto", criterio.texto());
        }
        if (criterio.tipoPropiedad() != null) {
            consulta.setParameter("tipo", criterio.tipoPropiedad());
        }
        if (criterio.distrito() != null) {
            consulta.setParameter("distrito", criterio.distrito());
        }
    }

    /** PostgreSQL devuelve los conteos como BigInteger o Long segun la consulta. */
    private static long aLong(Object valor) {
        if (valor instanceof BigInteger entero) {
            return entero.longValue();
        }
        return ((Number) valor).longValue();
    }
}
