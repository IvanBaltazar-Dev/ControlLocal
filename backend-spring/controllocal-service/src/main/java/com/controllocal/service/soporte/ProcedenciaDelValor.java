package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.RastroValorGobernado;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * <b>De donde sale UN valor concreto</b> (4.P, D-4P-1 seccion 2).
 *
 * <h2>Dos ejes, y no se mezclan</h2>
 * <pre>
 *   acto        canal · agente · modelo · conversacion · turno · peticion…
 *               Lo sabe el Core SIEMPRE, y es de la OPERACION.
 *   naturaleza  DECLARADO | OBSERVADO | INFERIDO
 *               A veces solo lo sabe quien captura, y es de CADA VALOR.
 * </pre>
 *
 * <p>Que sean dos ejes tiene consecuencias que hay que poder escribir:
 *
 * <pre>
 *   naturaleza=DECLARADO · canal=WHATSAPP  -> alguien lo declaro y el asistente lo capturo
 *   naturaleza=OBSERVADO · canal=SPA       -> un profesional afirma haberlo visto
 *   naturaleza=INFERIDO  · canal=WHATSAPP · modelo=… · confianza=0.81
 * </pre>
 *
 * <h2>Por que es POR VALOR y no por operacion</h2>
 * Es la razon por la que existe 4.P. Un mismo {@code PUT} puede cambiar
 * {@code tipo_acceso} (visita), {@code zonificacion} (certificado) y
 * {@code vigilancia} (lo dijo el propietario). Una sola respuesta al guardar
 * —«¿como sabes esto?»— estamparia una naturaleza <b>falsa</b> en dos de los
 * tres. Por eso se construye una de estas por cada valor, y por eso el
 * {@code acto} se comparte pero la {@code naturaleza} no.
 *
 * <h2>El Core JAMAS la deduce</h2>
 * Ni del canal, ni del actor, ni del <i>endpoint</i>, ni del tipo de usuario. Si
 * el productor no la declara, <b>queda ausente</b> — y ausente no es una cuarta
 * clase de evidencia: es que no consta como se obtuvo el hecho. Hay un test que
 * escribe el mismo valor por dos canales con dos actores y exige {@code null}
 * en los dos.
 */
public record ProcedenciaDelValor(Procedencia acto, String naturaleza, LocalDate observadoEn,
                                  String evidenciaRef, BigDecimal confianza) {

    /** {@code evidencia_ref} es VARCHAR(300): un puntero, no el documento. */
    private static final int MAX_EVIDENCIA = 300;

    public ProcedenciaDelValor {
        acto = Procedencia.oPantalla(acto);
        naturaleza = naturalezaValidada(naturaleza);
        evidenciaRef = recorte(evidenciaRef);
        confianza = confianzaValidada(confianza);
        exigirInferenciaCompleta(acto, naturaleza, confianza);
    }

    /**
     * Lo que llega de un cliente que no dice nada de la naturaleza — o sea,
     * <b>todos los actuales</b>: el SPA no estrena superficie de captura en 4.P.
     *
     * <p>No es un defecto silencioso: es la ausencia honesta. El valor queda con
     * su procedencia <b>operacional</b> completa —quien lo escribio, cuando, por
     * que canal— y sin afirmar como se conocio, que es justamente lo que no se
     * sabe.
     */
    public static ProcedenciaDelValor delActo(Procedencia acto) {
        return new ProcedenciaDelValor(acto, null, null, null, null);
    }

    /** {@code true} si el productor declaro como se obtuvo el hecho. */
    public boolean declaraNaturaleza() {
        return naturaleza != null;
    }

    // ------------------------------------------------------------------

    /**
     * Tres valores, y ni uno mas.
     *
     * <p>El mensaje nombra los tres <b>y explica por que no hay un cuarto</b>,
     * porque el error que se comete aqui es siempre el mismo: mandar
     * {@code DESCONOCIDO} para «rellenar el campo». No consta <b>es</b> la
     * ausencia, y tiene su propia representacion.
     */
    private static String naturalezaValidada(String naturaleza) {
        if (naturaleza == null || naturaleza.isBlank()) {
            return null;
        }
        String limpia = naturaleza.trim().toUpperCase(Locale.ROOT);
        if (!RastroValorGobernado.NATURALEZAS.contains(limpia)) {
            throw new ReglaNegocioException(
                    "Naturaleza desconocida: \"" + naturaleza + "\". Son DECLARADO, OBSERVADO e "
                            + "INFERIDO. Si no sabes como se obtuvo el dato, no mandes ninguna: "
                            + "la ausencia dice \"no consta\", y eso no es lo mismo que una "
                            + "inferencia ni una observacion.");
        }
        return limpia;
    }

    private static BigDecimal confianzaValidada(BigDecimal confianza) {
        if (confianza == null) {
            return null;
        }
        if (confianza.signum() < 0 || confianza.compareTo(BigDecimal.ONE) > 0) {
            throw new ReglaNegocioException(
                    "La confianza de una inferencia va de 0 a 1, y llego " + confianza.toPlainString()
                            + ".");
        }
        return confianza;
    }

    /**
     * <b>Un INFERIDO no puede existir sin autor.</b>
     *
     * <p>Quien infirio, con que modelo, en que version y con cuanta confianza.
     * Sin las cuatro no es una inferencia: es una afirmacion sin autor, y dentro
     * de dos meses nadie puede revisarla ni retirarla cuando el modelo resulte
     * estar equivocado. Es la unica exigencia dura de este eje, y esta tambien
     * como CHECK en la base ({@code ck_rastro_inferido_completo}): aqui esta el
     * mensaje, alli la garantia.
     *
     * <p>Los tres primeros vienen de {@link Procedencia} y no se piden aparte —
     * duplicarlos habria creado dos verdades sobre el mismo agente—. La
     * confianza es lo unico que no existia en el modelo antes de 4.P.
     */
    private static void exigirInferenciaCompleta(Procedencia acto, String naturaleza,
                                                 BigDecimal confianza) {
        if (!RastroValorGobernado.INFERIDO.equals(naturaleza)) {
            return;
        }
        if (acto.agente() == null || acto.modelo() == null || acto.modeloVersion() == null
                || confianza == null) {
            throw new ReglaNegocioException(
                    "Un valor INFERIDO tiene que decir quien lo infirio, con que modelo, en que "
                            + "version y con cuanta confianza. Falta "
                            + queFalta(acto, confianza) + ". Una inferencia sin autor no se puede "
                            + "revisar ni retirar el dia que el modelo resulte estar equivocado, "
                            + "y en silencio se convierte en un hecho confirmado.");
        }
    }

    private static String queFalta(Procedencia acto, BigDecimal confianza) {
        StringBuilder falta = new StringBuilder();
        if (acto.agente() == null) {
            falta.append("el agente");
        }
        if (acto.modelo() == null) {
            falta.append(falta.isEmpty() ? "" : ", ").append("el modelo");
        }
        if (acto.modeloVersion() == null) {
            falta.append(falta.isEmpty() ? "" : ", ").append("la version del modelo");
        }
        if (confianza == null) {
            falta.append(falta.isEmpty() ? "" : ", ").append("la confianza");
        }
        return falta.toString();
    }

    private static String recorte(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() <= MAX_EVIDENCIA ? limpio : limpio.substring(0, MAX_EVIDENCIA);
    }
}
