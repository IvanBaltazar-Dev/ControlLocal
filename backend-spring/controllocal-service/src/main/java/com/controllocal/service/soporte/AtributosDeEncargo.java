package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.RastroValorGobernado;
import com.controllocal.domain.comercial.AtributoEncargo;
import com.controllocal.domain.comercial.ValorMultipleEncargo;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.persistence.repositorio.AtributoEncargoRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.persistence.repositorio.ValorMultipleEncargoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <b>El enrutador del ENCARGO</b> (Corte 0C, V73). Gemelo de
 * {@link AtributosGobernados}, del que solo se diferencia en el sujeto.
 *
 * <h2>Por que es una clase aparte y no un parametro mas</h2>
 * Parecen la misma maquina con una variable de mas, y no lo son. Cambian las
 * cuatro cosas a la vez:
 *
 * <pre>
 *                     PROPIEDAD                    ENCARGO
 *   aplicabilidad     catalogo_atributo_tipo       catalogo_atributo_operacion
 *   valor             atributo_propiedad           atributo_encargo
 *   identidad         id_propiedad                 id_captacion
 *   autoridad         ATRIBUTO o ESTRUCTURAL       solo ATRIBUTO
 * </pre>
 *
 * <p>Un solo componente con {@code if (esDeEncargo)} dentro tendria esas cuatro
 * bifurcaciones repetidas en cada metodo, y bastaria olvidar una --la de
 * borrar, la de contar faltantes-- para que un dato se escribiera en un sujeto
 * y se leyera del otro. Que es, exactamente, el defecto que D-E4-3 cerro para
 * la autoridad y este corte cierra para el sujeto.
 *
 * <p>Lo que si comparten es la conversion de valores, que no depende del
 * sujeto: vive en {@link ConversionDeValores} y la usan los dos.
 *
 * <h2>Lo que este componente NO sabe</h2>
 * No conoce el agregado {@code Propiedad}. Recibe {@link Comercializacion} --el
 * id del episodio y las dos coordenadas de aplicabilidad-- y nada mas. Componer
 * «lo que le falta a la propiedad» con «lo que le falta al encargo» es trabajo
 * del caso de uso, y se le tiene que ver hacerlo: un componente que reciba las
 * dos cosas y decida por las dos vuelve a mezclar los sujetos un nivel mas
 * abajo, donde ya no se ve.
 */
@Component
public class AtributosDeEncargo {

    private final CatalogoAtributoRepository catalogo;
    private final AtributoEncargoRepository valores;
    private final ValorMultipleEncargoRepository multivalores;
    private final LinajeDelValor linaje;

    public AtributosDeEncargo(CatalogoAtributoRepository catalogo,
                              AtributoEncargoRepository valores,
                              ValorMultipleEncargoRepository multivalores,
                              LinajeDelValor linaje) {
        this.catalogo = catalogo;
        this.valores = valores;
        this.multivalores = multivalores;
        this.linaje = linaje;
    }

    /** Lo que se pregunta para esta comercializacion, en orden de presentacion. */
    public List<CatalogoAtributo> aplicablesA(long idOrganizacion, String tipoPropiedad,
                                              String tipoOperacion) {
        return catalogo.aplicablesAEncargo(idOrganizacion, tipoPropiedad, tipoOperacion);
    }

    public List<CatalogoAtributo> aplicablesA(long idOrganizacion, Comercializacion donde) {
        return aplicablesA(idOrganizacion, donde.tipoPropiedad(), donde.tipoOperacion());
    }

    /**
     * <b>Lo que le falta a ESTE encargo para poder anunciarse.</b>
     *
     * <p>Hermana de {@code faltantesDePropiedadParaPublicar} y deliberadamente
     * separada de ella. Publicar necesita las dos respuestas, pero son dos
     * preguntas: una mira un inmueble y la otra un episodio comercial, y el
     * mismo inmueble puede estar listo para alquilarse y no para venderse.
     * Fundirlas en un metodo que reciba propiedad y encargo daria una sola
     * lista sin forma de decir cual de las dos cosas hay que arreglar.
     *
     * <p>Lista vacia significa «por parte del encargo, se puede publicar».
     */
    public List<String> faltantesDeEncargoParaPublicar(long idOrganizacion,
                                                       Comercializacion donde) {
        return valores.clavesQueImpidenPublicar(idOrganizacion, donde.idCaptacion(),
                donde.tipoPropiedad(), donde.tipoOperacion());
    }

    /** Lo que le falta para cerrar el alta del encargo. Solo ALT. */
    public List<String> obligatoriasQueFaltan(long idOrganizacion, Comercializacion donde) {
        return valores.clavesObligatoriasQueFaltan(idOrganizacion, donde.idCaptacion(),
                donde.tipoPropiedad(), donde.tipoOperacion());
    }

    /** Los rotulos de esas claves, para decirlo en palabras y no en claves. */
    public List<String> rotulosDe(long idOrganizacion, Comercializacion donde,
                                  List<String> claves) {
        if (claves.isEmpty()) {
            return List.of();
        }
        Map<String, CatalogoAtributo> porClave = definicionesDe(idOrganizacion, donde);
        return claves.stream()
                .map(clave -> porClave.containsKey(clave) ? porClave.get(clave).getRotulo() : clave)
                .toList();
    }

    /** Las definiciones de esta comercializacion, por clave. Evita el N+1 de la ficha. */
    public Map<String, CatalogoAtributo> definicionesDe(long idOrganizacion,
                                                        Comercializacion donde) {
        Map<String, CatalogoAtributo> porClave = new LinkedHashMap<>();
        for (CatalogoAtributo atributo : aplicablesA(idOrganizacion, donde)) {
            porClave.putIfAbsent(atributo.getClave(), atributo);
        }
        return porClave;
    }

    // ==================================================================
    // Escritura
    // ==================================================================

    /**
     * Comprueba que la clave existe, que es del ENCARGO, que aplica a esta
     * comercializacion y que el valor encaja con su tipo. Devuelve el atributo
     * listo para guardar.
     */
    private AtributoEncargo convertir(long idOrganizacion, Comercializacion donde, String clave,
                                      String valor, String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, donde);
        String limpio = ConversionDeValores.exigirValor(clave, valor);
        long encargo = donde.idCaptacion();

        return switch (definicion.tipo()) {
            case ENTERO -> AtributoEncargo.deNumero(idOrganizacion, encargo, clave,
                    ConversionDeValores.enRango(definicion,
                            ConversionDeValores.entero(clave, limpio)));
            case DECIMAL -> AtributoEncargo.deNumero(idOrganizacion, encargo, clave,
                    ConversionDeValores.enRango(definicion,
                            ConversionDeValores.decimal(clave, limpio)));
            case IMPORTE -> AtributoEncargo.deImporte(idOrganizacion, encargo, clave,
                    ConversionDeValores.enRango(definicion,
                            ConversionDeValores.decimal(clave, limpio)),
                    ConversionDeValores.exigirMoneda(clave, moneda));
            case BOOLEANO -> AtributoEncargo.deBooleano(idOrganizacion, encargo, clave,
                    ConversionDeValores.booleano(clave, limpio));
            case FECHA -> AtributoEncargo.deFecha(idOrganizacion, encargo, clave,
                    ConversionDeValores.fecha(clave, limpio));
            case TEXTO, LISTA -> AtributoEncargo.deTexto(idOrganizacion, encargo, clave,
                    ConversionDeValores.enLongitud(definicion, limpio));
            case LISTA_MULTIPLE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" admite varios valores: se guarda con la via de "
                            + "multivalor, no con un valor suelto.");
        };
    }

    /** El ancla de un multivalor. Sus valores viven aparte y se SUSTITUYEN. */
    private AtributoEncargo convertirMultivalor(long idOrganizacion, Comercializacion donde,
                                                String clave) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, donde);
        if (!definicion.tipo().esMultivalor()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no admite varios valores.");
        }
        return AtributoEncargo.anclaDeMultivalor(idOrganizacion, donde.idCaptacion(), clave);
    }

    /**
     * Cambia el valor de una condicion que ya existe, respetando su tipo.
     * Reemplazar la fila perderia {@code fecha_creacion}, que es el dato que
     * dice desde cuando se pacto eso.
     */
    private void actualizar(long idOrganizacion, AtributoEncargo existente, String valor,
                            String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, existente.getClave());
        String clave = existente.getClave();
        String limpio = valor == null ? null : valor.trim();
        if (limpio == null || limpio.isEmpty()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego sin valor. Para quitarlo, borralo; "
                            + "no se guarda vacio.");
        }
        switch (definicion.tipo()) {
            case ENTERO -> existente.cambiarANumero(ConversionDeValores.enRango(definicion,
                    ConversionDeValores.entero(clave, limpio)));
            case DECIMAL -> existente.cambiarANumero(ConversionDeValores.enRango(definicion,
                    ConversionDeValores.decimal(clave, limpio)));
            case IMPORTE -> existente.cambiarAImporte(ConversionDeValores.enRango(definicion,
                            ConversionDeValores.decimal(clave, limpio)),
                    ConversionDeValores.exigirMoneda(clave, moneda));
            case BOOLEANO -> existente.cambiarABooleano(
                    ConversionDeValores.booleano(clave, limpio));
            case FECHA -> existente.cambiarAFecha(ConversionDeValores.fecha(clave, limpio));
            case TEXTO, LISTA -> existente.cambiarATexto(
                    ConversionDeValores.enLongitud(definicion, limpio));
            case LISTA_MULTIPLE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" admite varios valores: se edita con la via de "
                            + "multivalor, no con un valor suelto.");
        }
    }

    /**
     * <b>Escribe una condicion del encargo, con su linaje</b> (4.P).
     *
     * <p>No hay rama estructural, y su ausencia esta comprobada en
     * {@link #definicionDe}: hoy los unicos campos canonicos declarados son
     * conceptos de la cosa fisica --METRAJE, PISO--. Una clave del ENCARGO
     * marcada ESTRUCTURAL no tendria escritor, y guardarla como atributo «por
     * si acaso» seria inventarle una autoridad que nadie declaro.
     *
     * <p><b>Simetrico con la propiedad desde el primer commit.</b> La asimetria
     * que se temia ya existia y era peor de lo que parecia: pactar una condicion
     * no emite ni siquiera el evento de operacion que si emite editar una
     * propiedad, asi que hasta 4.P una condicion se escribia <b>sin dejar
     * ningun rastro de ninguna clase</b>. Aqui el encargo deja de ser el sujeto
     * de segunda.
     */
    public void escribir(Actor actor, Comercializacion donde, ValorEntrante entrante) {
        if (entrante.esMultivalor()) {
            escribirMultivalor(actor, donde, entrante);
            return;
        }
        long idOrganizacion = actor.idOrganizacion();
        AtributoEncargo existente = valores
                .findByIdCaptacionAndClave(donde.idCaptacion(), entrante.clave())
                .orElse(null);
        // La foto, ANTES de actualizar: `actualizar` muta la misma entidad.
        ValorLogico hallado = existente == null ? null : ValorLogico.deFila(existente, null);

        AtributoEncargo fila = existente != null ? existente
                : convertir(idOrganizacion, donde, entrante.clave(), entrante.valor(),
                        entrante.moneda());
        if (existente != null) {
            actualizar(idOrganizacion, existente, entrante.valor(), entrante.moneda());
        }
        anotarSegunHabia(actor, entrante, donde.idCaptacion(), hallado,
                ValorLogico.deFila(valores.save(fila), null));
    }

    /**
     * Un multivalor del encargo: su ancla y sus valores, <b>sustituyendo</b>.
     *
     * <p>Mismo gesto que en la propiedad y por la misma razon: sin borrar antes
     * no habria forma de QUITAR una opcion, y quitar es la mitad de lo que
     * significa editar una lista. Y como en la propiedad, el conjunto anterior
     * se lee entero antes de borrarlo y viaja al linaje como conjunto.
     */
    private void escribirMultivalor(Actor actor, Comercializacion donde, ValorEntrante entrante) {
        AtributoEncargo ancla = valores
                .findByIdCaptacionAndClave(donde.idCaptacion(), entrante.clave())
                .orElse(null);
        boolean existia = ancla != null;
        if (!existia) {
            ancla = valores.save(convertirMultivalor(actor.idOrganizacion(), donde,
                    entrante.clave()));
        }
        List<String> antes = existia ? multivalores.valoresDe(ancla.getId()) : List.of();
        ValorLogico hallado = existia ? ValorLogico.deConjunto(antes) : null;

        // La diferencia a la base, el conjunto entero al linaje. Igual que en la
        // propiedad y por la misma razon.
        List<String> despues = entrante.valores();
        List<String> seVan = antes.stream().filter(valor -> !despues.contains(valor)).toList();
        if (!seVan.isEmpty()) {
            multivalores.borrarDe(ancla.getId(), seVan);
        }
        for (String valor : despues) {
            if (!antes.contains(valor)) {
                multivalores.save(new ValorMultipleEncargo(
                        actor.idOrganizacion(), ancla.getId(), valor));
            }
        }
        anotarSegunHabia(actor, entrante, donde.idCaptacion(), hallado,
                ValorLogico.deConjunto(despues));
    }

    /**
     * <b>Retira una condicion de ESTE encargo</b>, y solo de este.
     *
     * <p>Borrar {@code garantia_meses} del alquiler de 2026 no toca el de 2024
     * ni la venta abierta en paralelo, porque el borrado cuelga de
     * {@code id_captacion} igual que la escritura. Es la misma simetria de
     * siempre, aplicada al tercer verbo: <b>leer, escribir y borrar recorren el
     * mismo enrutamiento</b>.
     *
     * <p>Y desde 4.P devuelve —y anota— <b>lo que se llevo</b>. El borrado es
     * fisico: sin leerlo antes, la ultima constancia de que ese encargo tuvo esa
     * condicion se va con la fila.
     *
     * @return {@link ValorRetirado#NO_ES_DEL_CATALOGO} si la clave no esta en el
     *         catalogo, para que el llamante pueda probar otro espacio de
     *         nombres antes de rechazarla
     */
    public ValorRetirado retirar(Actor actor, Comercializacion donde, String clave,
                                 ProcedenciaDelValor procedencia) {
        Optional<CatalogoAtributo> definicion =
                catalogo.porClave(actor.idOrganizacion(), clave);
        if (definicion.isEmpty()) {
            return ValorRetirado.NO_ES_DEL_CATALOGO;
        }
        exigirQueSeaDeEncargo(definicion.get());

        AtributoEncargo existente = valores
                .findByIdCaptacionAndClave(donde.idCaptacion(), clave)
                .orElse(null);
        // La forma la decide el catalogo: un LISTA_MULTIPLE con el conjunto
        // vacio sigue siendo una clave RESPONDIDA, y leerla con `deFila` caeria
        // a la rama escalar y diria que no habia nada. Mismo criterio que al
        // escribir -- el conjunto vacio es un conjunto.
        ValorLogico hallado = existente == null ? null
                : (definicion.get().tipo().esMultivalor()
                        ? ValorLogico.deConjunto(multivalores.valoresDe(existente.getId()))
                        : ValorLogico.deFila(existente, null));

        valores.deleteByIdCaptacionAndClave(donde.idCaptacion(), clave);
        // Y no se anota una retirada que no ocurrio: retirar una condicion que
        // este encargo nunca pacto no es un hecho, y el linaje cuenta lo que
        // paso -- en una tabla que ademas no se puede corregir.
        if (hallado == null) {
            return ValorRetirado.de(null);
        }
        linaje.anotarRetirada(actor, procedencia, RastroValorGobernado.SUJETO_ENCARGO,
                donde.idCaptacion(), clave, hallado);
        return ValorRetirado.de(hallado);
    }

    /** ALTA o EDICION segun lo que hubiera, no segun por que endpoint entro. */
    private void anotarSegunHabia(Actor actor, ValorEntrante entrante, long idCaptacion,
                                  ValorLogico hallado, ValorLogico escrito) {
        if (hallado == null) {
            linaje.anotarAlta(actor, entrante.procedencia(),
                    RastroValorGobernado.SUJETO_ENCARGO, idCaptacion, entrante.clave(), escrito);
        } else {
            linaje.anotarEdicion(actor, entrante.procedencia(),
                    RastroValorGobernado.SUJETO_ENCARGO, idCaptacion, entrante.clave(),
                    hallado, escrito);
        }
    }

    /**
     * La definicion de una clave del ENCARGO. Falla si la clave no existe y
     * falla si existe pero es de la propiedad.
     */
    public CatalogoAtributo definicionDe(long idOrganizacion, String clave) {
        CatalogoAtributo definicion = catalogo.porClave(idOrganizacion, clave)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El atributo \"" + clave + "\" no esta en el catalogo. Una clave existe "
                                + "antes que su valor: si no, dos encargos dicen lo mismo con "
                                + "nombres distintos y dejan de poder compararse."));
        exigirQueSeaDeEncargo(definicion);
        return definicion;
    }

    // ------------------------------------------------------------------

    /**
     * La mitad Java de la regla que el trigger {@code tg_atributo_encargo}
     * garantiza en la base.
     *
     * <p>Aqui y alli por el mismo motivo de siempre: la base es la garantia,
     * esto es el mensaje. Un rechazo de PostgreSQL a mitad de una transaccion
     * no le explica a nadie que {@code metraje_total} es un hecho del inmueble
     * y no una condicion que se pacte encargo a encargo.
     */
    private static void exigirQueSeaDeEncargo(CatalogoAtributo definicion) {
        if (!definicion.esDeEncargo()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" es de la PROPIEDAD y se "
                            + "intento tratar como una condicion del encargo. Un hecho del "
                            + "inmueble no cambia porque cambie quien lo comercializa: se edita "
                            + "en la ficha, una vez, y lo ven todos sus encargos.");
        }
        if (definicion.esEstructural()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" es del ENCARGO y esta "
                            + "declarado ESTRUCTURAL, y no hay campo canonico donde escribirlo. "
                            + "Los campos canonicos declarados son conceptos del inmueble.");
        }
    }

    private static void exigirQueAplique(CatalogoAtributo definicion, Comercializacion donde) {
        if (!definicion.aplicaA(donde.tipoPropiedad(), donde.tipoOperacion())) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" no aplica a "
                            + AtributosGobernados.nombreDelTipo(donde.tipoPropiedad())
                            + " en " + nombreDeLaOperacion(donde.tipoOperacion())
                            + ". Lo que se pregunta de cada comercializacion sale del catalogo, "
                            + "no del formulario.");
        }
    }

    /** El nombre de la operacion, para que el error no diga "operacion A". */
    public static String nombreDeLaOperacion(String tipoOperacion) {
        return switch (tipoOperacion == null ? "" : tipoOperacion) {
            case "A" -> "ALQUILER";
            case "V" -> "VENTA";
            default -> tipoOperacion;
        };
    }
}
