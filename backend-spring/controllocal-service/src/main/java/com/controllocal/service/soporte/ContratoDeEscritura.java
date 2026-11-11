package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;

/**
 * <b>¿Pertenece esta clave al contrato de escritura de AQUI?</b>
 *
 * <h2>La pregunta, dicha una sola vez</h2>
 * No es «¿esta clave se retiro?». Retirarla del catalogo es <b>una</b> de las
 * formas de salir del contrato, y hay otra que produce exactamente el mismo
 * efecto para quien mira la ficha:
 *
 * <pre>
 *   servicios_disponibles  activo = false        -> fuera, en TODOS los tipos
 *   area_terreno           activo = true, pero
 *                          sin fila para T       -> fuera SOLO en un TERRENO,
 *                                                   y sigue dentro en A y C
 * </pre>
 *
 * <p>En los dos casos el valor existe, se conserva y <b>ya no se puede
 * corregir</b>. Una senal que dijera solo «retirada» describiria el primero y
 * <b>mentiria sobre el segundo</b>: {@code area_terreno} no esta retirada --se
 * sigue preguntando en una casa y en un almacen--, asi que llamarla retirada es
 * falso, y llamarla vigente es peor, porque invita a editar lo que la puerta va
 * a rechazar.
 *
 * <p>Por eso lo que se publica es la pregunta generica, y se responde en UN
 * sitio. Los dos sujetos --la propiedad y el encargo-- la hacen igual: dos
 * respuestas a la misma pregunta divergen, igual que dos lectores o dos
 * escritores del mismo dato.
 *
 * <h2>Y la respuesta es la misma que da la puerta de escritura</h2>
 * {@code AtributosGobernados.escribirEnEdicion} rechaza una clave que no
 * aplique <b>aunque el valor enviado sea identico al conservado</b>: la
 * pregunta que se hace es si la clave pertenece al contrato, no si el valor
 * cambia. Esta clase dice por adelantado lo que aquella va a contestar, y esa
 * coincidencia es deliberada -- si divergieran, la ficha prometeria una edicion
 * que el {@code PUT} despues niega.
 *
 * <p>No la sustituye: el cliente recibe la senal para no ofrecer lo imposible,
 * y el Core <b>vuelve a comprobarlo</b> al recibir el comando. La regla no se
 * duplica -- las dos preguntan al mismo catalogo --, pero la puerta no confia
 * en que el cliente se haya portado bien.
 */
public final class ContratoDeEscritura {

    /** El dato forma parte de lo que hoy se pregunta y se corrige. */
    public static final String VIGENTE = "VIGENTE";

    /**
     * El dato existe, se conserva y se lee, y <b>ya no forma parte de la
     * definicion editable actual</b>. No dice por que: eso lo dice el motivo.
     */
    public static final String HISTORICO = "HISTORICO";

    private ContratoDeEscritura() {
    }

    /**
     * @param estadoDato        {@link #VIGENTE} o {@link #HISTORICO}
     * @param editable          si el Core aceptaria hoy un valor para esta
     *                          clave aqui. Va aparte del estado a proposito:
     *                          describen cosas distintas --uno el DATO, otro lo
     *                          que se puede hacer con el-- y el dia que un rol
     *                          solo lea, un dato vigente dejara de ser editable
     *                          sin dejar de ser vigente
     * @param motivoNoEditable  la frase, ya escrita, de por que no se puede
     *                          corregir. {@code null} cuando si se puede. Viaja
     *                          redactada y no como codigo porque componerla es
     *                          traducir, y con dos consumidores --BROX Web y
     *                          KAIROS-- serian dos redacciones que se separan
     *                          (D-A-1 §5)
     */
    public record Vigencia(String estadoDato, boolean editable, String motivoNoEditable) {

        /** Lo normal: la clave esta en el contrato y el valor se corrige. */
        static Vigencia vigente() {
            return new Vigencia(VIGENTE, true, null);
        }

        static Vigencia historico(String motivo) {
            return new Vigencia(HISTORICO, false, motivo);
        }
    }

    /**
     * Para un valor de la PROPIEDAD, en una propiedad de este tipo.
     *
     * @param definicion la del catalogo, o {@code null} si el catalogo no
     *                   conoce la clave
     */
    public static Vigencia dePropiedad(CatalogoAtributo definicion, String tipoPropiedad) {
        return dePropiedad(definicion, tipoPropiedad, null);
    }

    /**
     * Igual, con el <b>rotulo</b> de la clave sustituta ya resuelto por quien
     * llama (`N16`). Se pasa resuelto y no se busca aqui porque quien compone
     * la ficha ya tiene el mapa de definiciones delante: buscarlo otra vez
     * seria una consulta por atributo retirado.
     */
    public static Vigencia dePropiedad(CatalogoAtributo definicion, String tipoPropiedad,
                                       String rotuloSustituta) {
        Vigencia desconocida = siNoEstaEnElCatalogo(definicion);
        if (desconocida != null) {
            return desconocida;
        }
        if (!definicion.isActivo()) {
            return Vigencia.historico(retirada(definicion, rotuloSustituta));
        }
        if (!definicion.aplicaA(tipoPropiedad)) {
            return Vigencia.historico("Ya no se pregunta para "
                    + AtributosGobernados.rotuloDelTipo(tipoPropiedad).toLowerCase()
                    + ". El valor se conserva tal como se registro.");
        }
        return Vigencia.vigente();
    }

    /**
     * Para una condicion pactada en un ENCARGO. Misma pregunta, con las dos
     * dimensiones que gobiernan una condicion: el tipo y la operacion.
     */
    public static Vigencia deEncargo(CatalogoAtributo definicion, String tipoPropiedad,
                                     String codigoOperacion) {
        return deEncargo(definicion, tipoPropiedad, codigoOperacion, null);
    }

    /** Igual, con el rotulo de la sustituta ya resuelto (`N16`). */
    public static Vigencia deEncargo(CatalogoAtributo definicion, String tipoPropiedad,
                                     String codigoOperacion, String rotuloSustituta) {
        Vigencia desconocida = siNoEstaEnElCatalogo(definicion);
        if (desconocida != null) {
            return desconocida;
        }
        if (!definicion.isActivo()) {
            return Vigencia.historico(retirada(definicion, rotuloSustituta));
        }
        if (!definicion.aplicaA(tipoPropiedad, codigoOperacion)) {
            return Vigencia.historico("Ya no se pacta en un encargo de "
                    + nombreDeLaOperacion(codigoOperacion) + " sobre "
                    + AtributosGobernados.rotuloDelTipo(tipoPropiedad).toLowerCase()
                    + ". Lo pactado se conserva tal como se acordo.");
        }
        return Vigencia.vigente();
    }

    /**
     * Una clave que el catalogo no conoce tampoco se puede escribir: la puerta
     * la rechaza con «no esta en el catalogo». Asi que es HISTORICA -- existe y
     * no esta en el contrato --, y el motivo lo dice con esas palabras en vez de
     * suponer que se retiro: de una clave que el catalogo no tiene no se sabe si
     * se retiro o si nunca existio, y elegir seria inventar.
     */
    private static Vigencia siNoEstaEnElCatalogo(CatalogoAtributo definicion) {
        return definicion != null ? null : Vigencia.historico(
                "El catalogo no reconoce esta caracteristica, asi que no se puede corregir. "
                        + "El valor se conserva tal como se registro.");
    }

    /**
     * <b>La retirada, dicha entera</b> (`N16`, V91): cuando fue y a favor de
     * que, si consta.
     *
     * <p>Las dos piezas son opcionales por separado y ninguna se inventa:
     * <ul>
     *   <li>sin {@code fecha_retiro} —las claves retiradas antes de V91— la
     *       frase no dice cuando. No se pone la fecha de la migracion ni la de
     *       hoy: seria fechar otra cosa;</li>
     *   <li>sin {@code sustituida_por} no dice sustituta, porque se retiro sin
     *       ella o porque no se declaro. «No hay sustituta» y «no se sabe» se
     *       dicen igual: <b>callando</b>, que es lo unico honesto cuando el
     *       dato no distingue los dos casos.</li>
     * </ul>
     *
     * @param rotuloSustituta el rotulo de la clave sustituta si quien llama
     *                        pudo resolverlo; {@code null} para decirlo con la
     *                        clave tal cual. Nunca se inventa un nombre
     */
    static String retirada(CatalogoAtributo definicion, String rotuloSustituta) {
        StringBuilder frase = new StringBuilder("Ya no se pregunta: «")
                .append(definicion.getRotulo())
                .append("» se retiro del catalogo");
        if (definicion.getFechaRetiro() != null) {
            frase.append(" el ").append(Fechas.enPalabras(definicion.getFechaRetiro()));
        }
        frase.append('.');
        if (definicion.getSustituidaPor() != null && !definicion.getSustituidaPor().isBlank()) {
            frase.append(" La sustituye «")
                    .append(rotuloSustituta != null && !rotuloSustituta.isBlank()
                            ? rotuloSustituta : definicion.getSustituidaPor())
                    .append("».");
        }
        return frase.append(" El valor se conserva tal como se registro.").toString();
    }

    private static String retirada(CatalogoAtributo definicion) {
        return retirada(definicion, null);
    }

    private static String nombreDeLaOperacion(String codigoOperacion) {
        OperacionInmobiliaria operacion = OperacionInmobiliaria.deCodigo(codigoOperacion);
        return operacion == null ? "esta operacion" : operacion.name().toLowerCase();
    }
}
