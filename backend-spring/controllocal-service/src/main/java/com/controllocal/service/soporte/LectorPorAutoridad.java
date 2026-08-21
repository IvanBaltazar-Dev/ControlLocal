package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.AtributoEncargo;
import com.controllocal.domain.comun.FilaDeValorGobernado;
import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.AtributoEncargoRepository;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
import com.controllocal.persistence.repositorio.ValorMultipleAtributoRepository;
import com.controllocal.persistence.repositorio.ValorMultipleEncargoRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>El lector simetrico del escritor</b> (D-E4-3, paso 7).
 *
 * <h2>Por que hace falta una clase y no bastaba con arreglar la ficha</h2>
 * Mover la autoridad de {@code metraje} a su campo canonico lo saco de la lista
 * de atributos de la ficha universal: el dato seguia guardado y dejaba de poder
 * leerse por el API. Se habia movido el escritor y no el lector.
 *
 * <p>Arreglar ese sitio no cierra la clase de fallo, porque el siguiente cambio
 * de autoridad rompe el siguiente lector. La regla que si la cierra es:
 *
 * <blockquote><b>si el escritor enruta por autoridad, el lector tambien.</b>
 * Y lo hace la MISMA capa que conoce {@code destino}, no cada caso de uso por
 * su cuenta.</blockquote>
 *
 * <p>Que un servicio reconstruyera a mano {@code metraje_total} de un sitio y
 * {@code ambientes} de otro seria repartir el conocimiento otra vez, solo que
 * en el lado de la lectura. Aqui se resuelve una vez:
 *
 * <pre>
 *   clave logica  ->  autoridad declarada  ->  valor
 * </pre>
 *
 * <h2>Coste, y donde deja de valer</h2>
 * {@link #deVarias} es <b>dos consultas por pagina</b>, no N+1: una para la
 * pagina de propiedades —que ya la hizo quien llama— y una para sus atributos.
 *
 * <p>Y tiene una frontera que hay que respetar: <b>esto sirve para MOSTRAR</b>.
 * En cuanto una clave gobernada entre en un filtro o en un orden, tiene que ir
 * en SQL <b>antes</b> del {@code LIMIT/OFFSET} (§4 bis de D-E4-3). Hidratar
 * despues de paginar y filtrar en memoria rompe el conteo, las paginas y el
 * orden, y lo hace en silencio. Hoy ninguna de las seis se filtra ni se ordena.
 */
@Component
public class LectorPorAutoridad {

    private final AtributosGobernados gobierno;
    private final AtributoPropiedadRepository valores;
    private final ValorMultipleAtributoRepository multiples;
    private final AtributoEncargoRepository valoresDeEncargo;
    private final ValorMultipleEncargoRepository multiplesDeEncargo;

    public LectorPorAutoridad(AtributosGobernados gobierno, AtributoPropiedadRepository valores,
                              ValorMultipleAtributoRepository multiples,
                              AtributoEncargoRepository valoresDeEncargo,
                              ValorMultipleEncargoRepository multiplesDeEncargo) {
        this.gobierno = gobierno;
        this.valores = valores;
        this.multiples = multiples;
        this.valoresDeEncargo = valoresDeEncargo;
        this.multiplesDeEncargo = multiplesDeEncargo;
    }

    /**
     * Las filas ancla de un lote: las que no llevan escalar porque sus valores
     * viven en la tabla hija. Vale para los dos sujetos, y por eso esta escrito
     * una vez.
     */
    private static List<Long> anclasDe(List<? extends FilaDeValorGobernado> filas) {
        return filas.stream()
                .filter(fila -> fila.valor() == null)
                .map(FilaDeValorGobernado::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Los valores multiples de un lote de filas ancla, en UNA consulta.
     *
     * <p>Hidratar N propiedades cuesta una consulta mas, sea N uno o quinientos.
     * Preguntarlos por fila seria el N+1 que RC-003 retiro del repositorio, y
     * reaparece siempre igual: con una capacidad nueva que se lee "solo para
     * este caso".
     */
    private Map<Long, List<String>> multivaloresDe(List<AtributoPropiedad> filas) {
        List<Long> anclas = anclasDe(filas);
        if (anclas.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> porAncla = new LinkedHashMap<>();
        multiples.deVarios(anclas).forEach(valor -> porAncla
                .computeIfAbsent(valor.getIdAtributoPropiedad(), id -> new java.util.ArrayList<>())
                .add(valor.getValor()));
        return porAncla;
    }

    /** Lo mismo para el ENCARGO. Otra tabla, misma forma, mismo coste. */
    private Map<Long, List<String>> multivaloresDeEncargo(List<AtributoEncargo> filas) {
        List<Long> anclas = anclasDe(filas);
        if (anclas.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> porAncla = new LinkedHashMap<>();
        multiplesDeEncargo.deVarios(anclas).forEach(valor -> porAncla
                .computeIfAbsent(valor.getIdAtributoEncargo(), id -> new java.util.ArrayList<>())
                .add(valor.getValor()));
        return porAncla;
    }

    /**
     * <b>Las condiciones pactadas en UN encargo</b> (V73).
     *
     * <p>Vive aqui, y no en un lector propio, para que siga habiendo <b>un solo
     * sitio</b> que sepa por que columna se lee un valor gobernado. Un segundo
     * lector es como se perdio la moneda de un importe en el Corte 0B: nadie lo
     * escribio para hacer algo distinto, simplemente dejo de enterarse de los
     * cambios del primero.
     *
     * <p>No hay rama estructural: los campos canonicos declarados son conceptos
     * del inmueble. Y no hace falta el catalogo para leer -- la clave ya esta en
     * la fila--, asi que esto es <b>una consulta y una de multivalores</b>.
     */
    public ValoresGobernados deEncargo(long idCaptacion) {
        List<AtributoEncargo> filas =
                valoresDeEncargo.findByIdCaptacionOrderByClaveAsc(idCaptacion);
        return armarDeEncargo(filas, multivaloresDeEncargo(filas));
    }

    /**
     * Lo mismo para varios encargos: un expediente con tres cuesta dos
     * consultas, no seis. Cada uno recibe SUS condiciones y nada mas -- que es
     * la razon de ser de este corte.
     */
    public Map<Long, ValoresGobernados> deEncargos(Collection<Long> idsCaptacion) {
        if (idsCaptacion.isEmpty()) {
            return Map.of();
        }
        List<AtributoEncargo> todas = valoresDeEncargo.deVarios(idsCaptacion);
        Map<Long, List<String>> multivalores = multivaloresDeEncargo(todas);

        Map<Long, List<AtributoEncargo>> porEncargo = new LinkedHashMap<>();
        for (AtributoEncargo fila : todas) {
            porEncargo.computeIfAbsent(fila.getIdCaptacion(), id -> new java.util.ArrayList<>())
                    .add(fila);
        }
        Map<Long, ValoresGobernados> resultado = new LinkedHashMap<>();
        for (Long idCaptacion : idsCaptacion) {
            resultado.put(idCaptacion, armarDeEncargo(
                    porEncargo.getOrDefault(idCaptacion, List.of()), multivalores));
        }
        return resultado;
    }

    private static ValoresGobernados armarDeEncargo(List<AtributoEncargo> filas,
                                                    Map<Long, List<String>> multivalores) {
        ValoresGobernados.Constructor constructor = new ValoresGobernados.Constructor();
        for (AtributoEncargo fila : filas) {
            constructor.con(fila.getClave(), comoValor(fila, multivalores.get(fila.getId())));
        }
        return constructor.construir();
    }

    /**
     * Todo lo que se sabe de una propiedad, por clave logica y por las DOS
     * autoridades. Una consulta de atributos y una de catalogo.
     */
    public ValoresGobernados de(long idOrganizacion, Propiedad propiedad) {
        List<AtributoPropiedad> filas =
                valores.findByIdPropiedadOrderByClaveAsc(propiedad.getId());
        return armar(propiedad, filas, gobierno.definicionesDe(idOrganizacion,
                propiedad.getTipoInmueble()), multivaloresDe(filas));
    }

    /**
     * Lo mismo para una pagina ya resuelta. Las definiciones se piden una vez
     * por TIPO distinto que haya en la pagina —a lo sumo siete—, no una por
     * fila.
     */
    public Map<Long, ValoresGobernados> deVarias(long idOrganizacion,
                                                  Collection<Propiedad> propiedades) {
        if (propiedades.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AtributoPropiedad>> porPropiedad = agrupar(
                propiedades.stream().map(Propiedad::getId).toList());
        Map<Long, List<String>> multivalores = multivaloresDe(
                porPropiedad.values().stream().flatMap(List::stream).toList());

        Map<String, Map<String, CatalogoAtributo>> definicionesPorTipo = new HashMap<>();
        Map<Long, ValoresGobernados> resultado = new LinkedHashMap<>();
        for (Propiedad propiedad : propiedades) {
            Map<String, CatalogoAtributo> definiciones = definicionesPorTipo.computeIfAbsent(
                    propiedad.getTipoInmueble(),
                    tipo -> gobierno.definicionesDe(idOrganizacion, tipo));
            resultado.put(propiedad.getId(), armar(propiedad,
                    porPropiedad.getOrDefault(propiedad.getId(), List.of()), definiciones,
                    multivalores));
        }
        return resultado;
    }

    /**
     * Solo la mitad GOBERNADA, para quien ya trae la estructural resuelta en su
     * propia proyeccion.
     *
     * <p>El nombre dice que esta incompleto a proposito. Lo usa el listado de
     * {@code /locales}: su proyeccion sigue trayendo {@code metraje} en el
     * SELECT porque un listado tiene que poder ordenar y filtrar por lo
     * estructural, y eso solo se hace en SQL antes de paginar. Pedirlo aqui
     * seria traerlo dos veces.
     */
    public Map<Long, ValoresGobernados> gobernadosDeVarias(Collection<Long> idsPropiedad) {
        if (idsPropiedad.isEmpty()) {
            return Map.of();
        }
        Map<Long, ValoresGobernados> porPropiedad = new LinkedHashMap<>();
        Map<Long, List<AtributoPropiedad>> filasPorPropiedad = agrupar(idsPropiedad);
        Map<Long, List<String>> multivalores = multivaloresDe(
                filasPorPropiedad.values().stream().flatMap(List::stream).toList());
        Map<Long, ValoresGobernados> resultado = porPropiedad;
        filasPorPropiedad.forEach((id, filas) -> {
            ValoresGobernados.Constructor constructor = new ValoresGobernados.Constructor();
            filas.forEach(fila -> constructor.con(fila.getClave(),
                    comoValor(fila, multivalores.get(fila.getId()))));
            resultado.put(id, constructor.construir());
        });
        return resultado;
    }

    /**
     * Los valores de una propiedad dentro de un lote ya hidratado, tolerando
     * un id nulo.
     *
     * <p>Existe porque { Map.of()} -- lo que devuelve { #deVarias}
     * cuando no hay nada que hidratar -- <b>lanza NPE al preguntarle por una
     * clave nula</b>, incluso desde { getOrDefault}. Un
     * { getOrDefault(idPropiedad, vacio())} escrito a mano parece seguro y
     * revienta justo en el caso menos probado: cartera sin atributos y una
     * captacion sin propiedad.
     */
    public static ValoresGobernados de(Map<Long, ValoresGobernados> lote, Long idPropiedad) {
        ValoresGobernados valores = idPropiedad == null ? null : lote.get(idPropiedad);
        return valores == null ? ValoresGobernados.vacio() : valores;
    }

    // ------------------------------------------------------------------

    private Map<Long, List<AtributoPropiedad>> agrupar(Collection<Long> ids) {
        Map<Long, List<AtributoPropiedad>> porPropiedad = new LinkedHashMap<>();
        for (AtributoPropiedad fila : valores.deVarias(ids)) {
            porPropiedad.computeIfAbsent(fila.getIdPropiedad(), id -> new java.util.ArrayList<>())
                    .add(fila);
        }
        return porPropiedad;
    }

    /**
     * Junta las dos autoridades en un solo mapa de claves.
     *
     * <p>Las estructurales se anaden DESPUES y solo si su campo canonico tiene
     * valor. No hay conflicto posible: una clave declarada ESTRUCTURAL no deja
     * fila en {@code atributo_propiedad}, y si por lo que fuera quedara una
     * huerfana de antes de la consolidacion, gana la autoridad declarada — que
     * es exactamente lo que significa declararla.
     */
    private static ValoresGobernados armar(Propiedad propiedad, List<AtributoPropiedad> filas,
                                            Map<String, CatalogoAtributo> definiciones,
                                            Map<Long, List<String>> multivalores) {
        ValoresGobernados.Constructor constructor = new ValoresGobernados.Constructor();
        for (AtributoPropiedad fila : filas) {
            CatalogoAtributo definicion = definiciones.get(fila.getClave());
            if (definicion != null && definicion.esEstructural()) {
                continue;
            }
            constructor.con(fila.getClave(),
                    comoValor(fila, multivalores.get(fila.getId())));
        }
        for (CatalogoAtributo definicion : definiciones.values()) {
            if (definicion.esEstructural()) {
                constructor.con(definicion.getClave(),
                        EscritorEstructural.leerValor(propiedad, definicion.getCampoEstructural()));
            }
        }
        return constructor.construir();
    }

    /**
     * La fila, leida por la columna que le toca.
     *
     * <p>{@code multivalor} llega ya resuelto por el lote: una fila ancla no
     * lleva escalar, y preguntar aqui por sus valores seria una consulta por
     * fila -- el N+1 que RC-003 retiro del repositorio.
     */
    private static ValorLogico comoValor(FilaDeValorGobernado fila, List<String> multivalor) {
        if (multivalor != null && !multivalor.isEmpty()) {
            return ValorLogico.deValores(multivalor);
        }
        if (fila.getValorTexto() != null) {
            return ValorLogico.deTexto(fila.getValorTexto());
        }
        if (fila.getValorNumero() != null) {
            // La moneda viaja pegada al monto: un importe sin ella no es dinero.
            return fila.getValorMoneda() == null
                    ? ValorLogico.deNumero(fila.getValorNumero())
                    : ValorLogico.deImporte(fila.getValorNumero(), fila.getValorMoneda());
        }
        if (fila.getValorFecha() != null) {
            return ValorLogico.deFecha(fila.getValorFecha());
        }
        return ValorLogico.deBooleano(fila.getValorBooleano());
    }
}
