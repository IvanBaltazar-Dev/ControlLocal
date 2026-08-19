package com.controllocal.service.soporte;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los <b>cuatro</b> indicadores comerciales, con su nombre visible y el hecho
 * exacto que cuentan. Ni uno mas.
 *
 * <h2>El nombre nombra un hecho, no una categoria</h2>
 *
 * <p>D-E2-2 §1 los llamaba «Prospeccion efectiva», «Captaciones activadas» y
 * «Solicitudes generadas»; D-E2-1 §6.2 y la maqueta, «Propietarios
 * contactados», «Locales captados» y «Solicitudes ingresadas». Dos juegos
 * distintos en los dos documentos que gobiernan, y una comprobacion que exigia
 * los cuatro nombres «letra por letra». El gate no se podia escribir: pedia dos
 * verdades.
 *
 * <p><b>Se resolvio el 2026-08-19 a favor del hecho de negocio.</b> «Prospeccion
 * efectiva» obliga a preguntar que es efectivo; «Propietarios contactados» dice
 * lo que paso. El tablero se lee en segundos o no es un centro de decision.
 *
 * <p>Con un cambio sobre el juego heredado: <b>«Locales captados» pasa a
 * «Propiedades captadas»</b>. BROX dejo de ser un sistema de alquiler de locales
 * el 2026-08-17; llamar «local» a una casa o a un terreno seria arrastrar el
 * nombre viejo a un dominio que ya no lo es.
 *
 * <h2>El codigo es estable; el nombre, no</h2>
 *
 * <p>{@link #codigo()} es lo que viaja por el cable, lo que guarda la meta y lo
 * que persiste. El rotulo puede cambiar el dia que el negocio lo diga sin migrar
 * una fila. Es la misma separacion que el resto del sistema aplica a los estados:
 * codigo unitario dentro, palabra fuera.
 *
 * <h2>Y cada uno cuenta un evento, no algo parecido</h2>
 *
 * <p>{@link #hecho()} dice exactamente que fila suma. Una prospeccion creada
 * <b>no</b> es un propietario contactado: si el contacto no ocurrio, no cuenta.
 * Es la distincion que D-E2-2 §1.1 vino a fijar —31 registros creados no son 31
 * prospectos trabajados— y la unica forma de que el numero signifique algo.
 */
public enum KpiCanonico {

    /**
     * Propietarios a los que de verdad se contacto.
     *
     * <p>La autoridad es {@code prospeccion.fecha_contacto}, no el estado. La
     * escalera de estados (P-C-R-E-S-T) no sirve como fuente porque {@code D}
     * —descartado— se sale de ella y <b>si</b> hubo contacto: los tres
     * descartados de la base tienen su fecha. Contar por estado perderia esos
     * tres y premiaria haber dejado la prospeccion a medias.
     */
    PROPIETARIOS_CONTACTADOS("C", "Propietarios contactados", "propietarios", "propietario",
            "prospeccion con fecha de contacto dentro del mes"),

    /**
     * Propiedades que entraron de verdad a cartera.
     *
     * <p>La autoridad es la <b>transicion</b> {@code P -> A} de
     * {@code historial_estado}, no el estado actual ni {@code fecha_captacion}.
     * El estado actual perderia las que ya cerraron —cuatro de las nueve
     * activadas acabaron en contrato— y {@code fecha_captacion} es cuando se
     * registro, no cuando el broker la aprobo. El hecho es la aprobacion.
     */
    PROPIEDADES_CAPTADAS("P", "Propiedades captadas", "propiedades", "propiedad",
            "transicion de captacion a ACTIVA dentro del mes"),

    /** Solicitudes de alquiler ingresadas, por {@code fecha_registro}. */
    SOLICITUDES_INGRESADAS("S", "Solicitudes ingresadas", "solicitudes", "solicitud",
            "solicitud registrada dentro del mes"),

    /** Contratos firmados, por {@code fecha_cierre}, que es la firma. */
    CONTRATOS_FIRMADOS("F", "Contratos firmados", "contratos", "contrato",
            "contrato con fecha de cierre dentro del mes");

    private final String codigo;
    private final String rotulo;
    private final String unidadPlural;
    private final String unidadSingular;
    private final String hecho;

    KpiCanonico(String codigo, String rotulo, String unidadPlural, String unidadSingular,
                String hecho) {
        this.codigo = codigo;
        this.rotulo = rotulo;
        this.unidadPlural = unidadPlural;
        this.unidadSingular = unidadSingular;
        this.hecho = hecho;
    }

    /** Codigo unitario estable: viaja, se guarda y no cambia con el rotulo. */
    public String codigo() {
        return codigo;
    }

    /** El nombre visible, el mismo en Indicadores y en el pie del Inicio. */
    public String rotulo() {
        return rotulo;
    }

    /** Para redactar «faltan 2 propiedades» sin concatenar una «s». */
    public String unidad(int cantidad) {
        return cantidad == 1 ? unidadSingular : unidadPlural;
    }

    /** Que fila suma exactamente. Se publica para que el numero sea auditable. */
    public String hecho() {
        return hecho;
    }

    /** Los cuatro, en el orden del embudo. El pie y la pantalla usan este orden. */
    public static final List<KpiCanonico> TODOS = List.of(values());

    /** Los cuatro rotulos, que es lo que comprueban los gates de nombre. */
    public static List<String> rotulos() {
        return TODOS.stream().map(KpiCanonico::rotulo).toList();
    }

    /** El KPI de un codigo persistido. */
    public static KpiCanonico porCodigo(String codigo) {
        String buscado = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(k -> k.codigo.equals(buscado))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Codigo de KPI desconocido: '" + codigo + "'. Los cuatro son "
                                + Arrays.stream(values()).map(KpiCanonico::codigo).toList()
                                + ". Los KPI canonicos son cuatro y no se anaden desde una "
                                + "pantalla: si hace falta un quinto, se decide en D-E2-2."));
    }
}
