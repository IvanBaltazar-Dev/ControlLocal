package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La politica comercial de la corredora: el <b>unico</b> sitio donde se decide
 * que significa un numero.
 *
 * <h2>Por que existe</h2>
 *
 * <p>El inventario de E1 ({@code docs/ai/inventario-umbrales-de-dominio.md})
 * encontro el plazo de recontacto <b>cuadruplicado</b> —bandeja, indicadores,
 * campana y dashboard de Angular— coordinado unicamente por un comentario que
 * pedia que los cuatro numeros cuadraran. Cambiar la politica exigia tocar
 * cuatro archivos en dos lenguajes y <b>nada rompia</b> si uno se quedaba
 * atras: la bandeja diria una cosa y el indicador otra, en silencio. Es la
 * misma forma del incidente de V40, donde un CHECK y una funcion PL/pgSQL
 * divergieron sin que nada avisara.
 *
 * <h2>Que decide aqui, y que no</h2>
 *
 * <ul>
 *   <li><b>Si</b>: cuando un dato pasa a significar algo —atrasado, proximo,
 *       suficiente, excesivo— y <b>que se atiende primero</b>.</li>
 *   <li><b>No</b>: cuantas filas caben en una pagina, de que color se pinta un
 *       nivel de atencion, ni como se rotula en pantalla. Eso es presentacion y
 *       vive donde se presenta.</li>
 *   <li><b>Tampoco</b>: la politica de accesos ({@link PoliticaContrasenas},
 *       {@link BloqueoAccesos}, {@link Totp}…), que ya cumple lo mismo en su
 *       propio dominio y no se toca.</li>
 * </ul>
 *
 * <h2>Regla de oro del reparto backend / frontend (R-07)</h2>
 *
 * <p>El backend devuelve el <b>hecho ya interpretado</b>, no el insumo para que
 * otro lo interprete: emite {@code diasSinSeguimiento: 9} <i>y</i>
 * {@code nivelAtencion: ALTO}. Angular decide como se ve un ALTO; nunca cuando
 * algo pasa a serlo. Exponer estos numeros por el API para que el cliente los
 * vuelva a aplicar solo trasladaria la duplicacion de sitio.
 *
 * <h2>Alcance: global hoy, por organizacion despues</h2>
 *
 * <p>Todas las reglas nacen {@link Alcance#GLOBAL}. La estructura para que cada
 * corredora fije el suyo esta prevista —{@link Regla#alcance()} y
 * {@link Regla#version()}— pero <b>no se implementa</b>: con una sola
 * organizacion real seria complejidad sin usuario.
 *
 * <h2>Si cambias un valor</h2>
 *
 * <p>Sube la {@code version} de esa regla y revisa
 * {@code frontend-angular/src/app/core/politica-comercial.ts}, que refleja los
 * dos valores que el formulario necesita conocer por adelantado. Hay un test
 * que lo recuerda ({@code PoliticaComercialTest}).
 */
public final class PoliticaComercial {

    // ------------------------------------------------------------------
    // Las reglas
    // ------------------------------------------------------------------

    /** En que se mide el valor de una regla. */
    public enum Unidad {
        DIAS, MESES, PUNTOS, PORCENTAJE, CARACTERES
    }

    /** Quien fija el valor. Hoy todas son GLOBAL; ver el javadoc de la clase. */
    public enum Alcance {
        /** Igual para toda corredora. */
        GLOBAL,
        /** Reservado: cada corredora fijaria el suyo. Sin implementar. */
        ORGANIZACION
    }

    /**
     * Una regla de negocio con lo que hace falta para poder discutirla sin leer
     * el codigo: nombre estable, que significa, cuanto vale, en que unidad,
     * quien lo fija y desde que version.
     */
    public record Regla(String nombre, String significado, int valor, Unidad unidad,
                        Alcance alcance, int version) {
    }

    /** Con cuanta anticipacion una visita agendada entra en la bandeja del agente. */
    public static final Regla VISITA_PROXIMA = new Regla(
            "visita.dias-de-aviso",
            "Dias de anticipacion con los que una visita agendada aparece en la bandeja.",
            3, Unidad.DIAS, Alcance.GLOBAL, 1);

    /** Cada cuanto se le debe una novedad al propietario de una captacion activa. */
    public static final Regla REPORTE_PROPIETARIO = new Regla(
            "reporte-propietario.dias",
            "Dias entre un informe al propietario y el siguiente mientras el encargo "
                    + "sigue activo.",
            15, Unidad.DIAS, Alcance.GLOBAL, 1);

    /** Cuanto dura un encargo cuando nadie dice lo contrario. El agente puede cambiarlo. */
    public static final Regla ENCARGO = new Regla(
            "encargo.meses-por-defecto",
            "Duracion habitual del encargo del propietario cuando se crea la captacion.",
            6, Unidad.MESES, Alcance.GLOBAL, 1);

    /**
     * Tope de la comision pactada. Son 200 % de una renta mensual —dos rentas—,
     * no 200 % del contrato: por encima de ahi lo mas probable es que alguien
     * haya tecleado un importe en el campo del porcentaje, que es exactamente lo
     * que muestran las fixtures del diagnostico economico.
     */
    public static final Regla COMISION_MAXIMA = new Regla(
            "comision.porcentaje-maximo",
            "Comision maxima admitida, en porcentaje de una renta mensual.",
            200, Unidad.PORCENTAJE, Alcance.GLOBAL, 1);

    /**
     * Longitud minima del motivo al cambiar de responsable. Un "ok" o un "x" no
     * explican nada dentro de seis meses, y este texto es lo unico que queda en
     * el historial para entender por que un inmueble cambio de manos.
     */
    public static final Regla MOTIVO_REASIGNACION = new Regla(
            "reasignacion.caracteres-minimos-del-motivo",
            "Longitud minima del motivo al cambiar el responsable de una captacion o "
                    + "de un agente.",
            10, Unidad.CARACTERES, Alcance.GLOBAL, 1);

    // ------------------------------------------------------------------
    // El ritmo contra la meta (E2.6)
    // ------------------------------------------------------------------
    //
    // Los cinco umbrales de abajo vivian SOLO en la maqueta
    // (docs/ai/prototipos/*.html, bloque POLITICA), duplicados ademas entre sus
    // dos archivos. Es la misma forma del incidente que E1 vino a cerrar, y ya
    // habia divergido: el prototipo decia 10 dias de reporte al propietario
    // donde aqui dicen 15. Suben aqui, que es donde se decide que significa un
    // numero, y el prototipo deja de ser fuente ejecutable.

    /**
     * Muestra minima para que un porcentaje concluya algo.
     *
     * <p>1 visita y 1 solicitud son 100 %, y no significan nada. Por debajo de
     * aqui el porcentaje se muestra con su N y sin estado, que es la regla 9 de
     * D-E2-2.
     */
    public static final Regla MUESTRA_MINIMA = new Regla(
            "muestra.minima-para-concluir",
            "Numero de observaciones por debajo del cual un porcentaje se muestra pero no "
                    + "concluye.",
            5, Unidad.PUNTOS, Alcance.GLOBAL, 1);

    /**
     * Observaciones minimas para publicar un rango de renta propio.
     *
     * <p>Medido el 2026-08-19: la mejor celda zona x metraje de la cartera tiene
     * <b>cuatro</b> observaciones y catorce de las diecisiete tienen una. Con eso
     * el minimo y el maximo son dos puntos sueltos, no un rango. Diez propiedades
     * distintas es lo que separa «esto es nuestra operacion» de «esto son dos
     * casos». Por debajo el contraste no se dibuja y se dice que no hay
     * referencia suficiente; nunca se rellena con el sector.
     */
    public static final Regla RANGO_MUESTRA_MINIMA = new Regla(
            "contraste.propiedades-minimas-para-rango",
            "Propiedades distintas que tiene que haber en una zona y banda de metraje para "
                    + "publicar un rango de renta propio.",
            10, Unidad.PUNTOS, Alcance.GLOBAL, 1);

    /**
     * Dias que puede llevar una renta sin moverse antes de que valga la pena
     * senalarlo.
     *
     * <p>Estaba escrito a mano en {@code InterpreteDeLaBandeja} —un {@code > 45}
     * con un comentario que lo llamaba "el plazo de recontacto de la casa", que
     * son 7 dias y no 45—, y la maqueta llevaba su propia copia con 60. Tres
     * numeros para una regla. Sube aqui con el valor que estaba en produccion:
     * cambiarlo es una decision comercial, no un efecto colateral de
     * centralizarlo.
     */
    public static final Regla RENTA_SIN_AJUSTAR = new Regla(
            "renta.dias-sin-ajustar",
            "Dias que una renta puede permanecer sin cambios antes de senalarla como parada.",
            45, Unidad.DIAS, Alcance.GLOBAL, 1);

    /**
     * Los tres cortes que separan propiedades comparables por tamano.
     *
     * <p>Estan aqui y no dentro de {@link BandaDeMetraje} porque tambien deciden
     * que significa un numero: dicen contra QUIEN se compara una renta. Un corte
     * mal puesto no da un error, da un rango dentro del cual cualquier renta cae
     * bien, y un rango que nunca senala nada no es informacion.
     *
     * <p>Salen de la practica de la casa —el local de calle pequeno, el
     * estandar, el grande y la superficie mayor—, no de un estudio de mercado
     * que BROX no tiene.
     */
    public static final Regla BANDA_METRAJE_PEQUENO = new Regla(
            "contraste.banda-metraje-pequeno",
            "Metros cuadrados hasta los que una propiedad se compara como local pequeno.",
            50, Unidad.PUNTOS, Alcance.GLOBAL, 1);

    public static final Regla BANDA_METRAJE_ESTANDAR = new Regla(
            "contraste.banda-metraje-estandar",
            "Metros cuadrados hasta los que una propiedad se compara como local estandar.",
            100, Unidad.PUNTOS, Alcance.GLOBAL, 1);

    public static final Regla BANDA_METRAJE_GRANDE = new Regla(
            "contraste.banda-metraje-grande",
            "Metros cuadrados hasta los que una propiedad se compara como local grande; "
                    + "por encima es superficie mayor.",
            200, Unidad.PUNTOS, Alcance.GLOBAL, 1);

    /** Catalogo completo. Su unico consumidor hoy es el test que vigila la politica. */
    public static final List<Regla> REGLAS = List.of(
            VISITA_PROXIMA, REPORTE_PROPIETARIO,
            ENCARGO, COMISION_MAXIMA, MOTIVO_REASIGNACION,
            MUESTRA_MINIMA, RANGO_MUESTRA_MINIMA, RENTA_SIN_AJUSTAR,
            BANDA_METRAJE_PEQUENO, BANDA_METRAJE_ESTANDAR, BANDA_METRAJE_GRANDE);

    private PoliticaComercial() {
    }

    // ------------------------------------------------------------------
    // Las reglas, aplicadas
    // ------------------------------------------------------------------

    /** Cuando toca el siguiente informe al propietario contando desde el ultimo. */
    public static LocalDate proximoReporteAlPropietario(LocalDate desde) {
        return desde.plusDays(REPORTE_PROPIETARIO.valor());
    }

    /** Fin del encargo cuando se crea la captacion sin un plazo explicito. */
    public static LocalDate finDelEncargo(LocalDate inicio) {
        return inicio.plusMonths(ENCARGO.valor());
    }

    /** Tope de comision como importe comparable, para las validaciones economicas. */
    public static BigDecimal comisionMaxima() {
        return BigDecimal.valueOf(COMISION_MAXIMA.valor());
    }

    /** Si una muestra da para que un porcentaje concluya algo. */
    public static boolean muestraConcluye(int observaciones) {
        return observaciones >= MUESTRA_MINIMA.valor();
    }

    /**
     * Si una zona y banda de metraje tiene bastantes propiedades distintas para
     * publicar un rango de renta propio. Por debajo se degrada; no se rellena.
     */
    public static boolean rangoPublicable(int propiedadesDistintas) {
        return propiedadesDistintas >= RANGO_MUESTRA_MINIMA.valor();
    }

    /** Si una renta lleva parada lo bastante como para decirlo. */
    public static boolean rentaParada(long diasSinCambios) {
        return diasSinCambios > RENTA_SIN_AJUSTAR.valor();
    }

    /**
     * Valida el motivo de un cambio de responsable y lo devuelve ya recortado.
     *
     * <p>Vivia <b>solo en el formulario de Angular</b>: la API aceptaba un
     * motivo de tres caracteres y bastaba llamarla directamente para saltarse la
     * regla. El formulario puede seguir avisando antes de enviar —es mejor
     * experiencia—, pero quien la hace cumplir es este metodo.
     */
    public static String exigirMotivoDeReasignacion(String motivo) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < MOTIVO_REASIGNACION.valor()) {
            throw new ReglaNegocioException(
                    "Explica el motivo del cambio de responsable con al menos "
                            + MOTIVO_REASIGNACION.valor() + " caracteres: queda en el "
                            + "historial y tiene que entenderse dentro de unos meses.");
        }
        return texto;
    }

    // ------------------------------------------------------------------
    // Que se atiende primero
    // ------------------------------------------------------------------

    /**
     * Cuanto urge algo. Es la interpretacion que el backend entrega hecha; la
     * pantalla elige el color, nunca el nivel.
     */
    public enum NivelAtencion {
        /** Hay alguien esperando y la demora cuesta la operacion. */
        ALTO,
        /** Hay que atenderlo, pero no antes que un ALTO. */
        MEDIO,
        /** Es un dato para orientarse, no un pendiente. */
        INFORMATIVO,
        /** No queda nada pendiente de este concepto. */
        SIN_PENDIENTES
    }

    /**
     * Los conceptos del tablero que el dominio clasifica, con <b>cuanto urgen y
     * en que orden</b>. Antes vivian en ocho ternarios de {@code dashboard.ts}
     * repartidos por rol, y se contradecian: el mismo recontacto vencido iba
     * primero para el administrador y cuarto para el broker.
     *
     * <p>El orden es uno solo y sale de que cuesta mas ignorar: alguien que
     * espera nuestra respuesta, un interesado que se enfria, dinero
     * comprometido, inventario sin aprobar y, al final, lo que solo informa.
     */
    public enum Concepto {

        /** Un interesado entrego su expediente y espera que el broker decida. */
        SOLICITUD_POR_EVALUAR(NivelAtencion.ALTO, 1),

        /** Prospecciones que debieron volver a contactarse y no se contactaron. */
        RECONTACTO_VENCIDO(NivelAtencion.ALTO, 2),

        /** Captaciones esperando la revision del broker para poder ofrecerse. */
        CAPTACION_POR_REVISAR(NivelAtencion.MEDIO, 3),

        /** Aprobadas que todavia no tienen contrato: la comision no esta ganada. */
        SOLICITUD_APROBADA_SIN_CIERRE(NivelAtencion.MEDIO, 4),

        /** Visitas agendadas o vencidas sin resultado. Agenda, no incendio. */
        VISITA_PENDIENTE(NivelAtencion.INFORMATIVO, 6),

        /** Alquileres ya firmados. Se informa; nunca es un pendiente. */
        CIERRE_REGISTRADO(NivelAtencion.INFORMATIVO, 7),

        /** Cuanta gente hay operando. Se informa; nunca es un pendiente. */
        COBERTURA_DE_AGENTES(NivelAtencion.INFORMATIVO, 8);

        private final NivelAtencion nivelCuandoHay;
        private final int prioridad;

        Concepto(NivelAtencion nivelCuandoHay, int prioridad) {
            this.nivelCuandoHay = nivelCuandoHay;
            this.prioridad = prioridad;
        }

        /** Nivel que alcanza este concepto cuando efectivamente hay algo pendiente. */
        public NivelAtencion nivelCuandoHay() {
            return nivelCuandoHay;
        }

        /** 1 es lo que se atiende primero. Un unico orden para los tres roles. */
        public int prioridad() {
            return prioridad;
        }

    }

    /**
     * Clasifica un concepto a partir de su valor.
     *
     * <p>El {@code > 0} no es un umbral configurable —"si hay alguno, hay algo
     * que hacer" es un hecho, no una politica—; lo que si es criterio de negocio
     * es <b>cuanto</b> urge, y eso lo declara el propio {@link Concepto}.
     */
    public static NivelAtencion clasificar(Concepto concepto, int valor) {
        if (concepto.nivelCuandoHay() == NivelAtencion.INFORMATIVO) {
            // Cero visitas agendadas no es "todo al dia": es cero.
            return NivelAtencion.INFORMATIVO;
        }
        return valor > 0 ? concepto.nivelCuandoHay() : NivelAtencion.SIN_PENDIENTES;
    }

    /** Si el nivel obliga a hacer algo. INFORMATIVO no lo hace: solo acompana. */
    public static boolean requiereAtencion(NivelAtencion nivel) {
        return nivel == NivelAtencion.ALTO || nivel == NivelAtencion.MEDIO;
    }
}
