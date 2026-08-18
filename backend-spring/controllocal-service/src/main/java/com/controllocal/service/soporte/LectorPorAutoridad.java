package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
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

    public LectorPorAutoridad(AtributosGobernados gobierno, AtributoPropiedadRepository valores) {
        this.gobierno = gobierno;
        this.valores = valores;
    }

    /**
     * Todo lo que se sabe de una propiedad, por clave logica y por las DOS
     * autoridades. Una consulta de atributos y una de catalogo.
     */
    public ValoresDePropiedad de(long idOrganizacion, Propiedad propiedad) {
        List<AtributoPropiedad> filas =
                valores.findByIdPropiedadOrderByClaveAsc(propiedad.getId());
        return armar(propiedad, filas, gobierno.definicionesDe(idOrganizacion,
                propiedad.getTipoInmueble()));
    }

    /**
     * Lo mismo para una pagina ya resuelta. Las definiciones se piden una vez
     * por TIPO distinto que haya en la pagina —a lo sumo siete—, no una por
     * fila.
     */
    public Map<Long, ValoresDePropiedad> deVarias(long idOrganizacion,
                                                  Collection<Propiedad> propiedades) {
        if (propiedades.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AtributoPropiedad>> porPropiedad = agrupar(
                propiedades.stream().map(Propiedad::getId).toList());

        Map<String, Map<String, CatalogoAtributo>> definicionesPorTipo = new HashMap<>();
        Map<Long, ValoresDePropiedad> resultado = new LinkedHashMap<>();
        for (Propiedad propiedad : propiedades) {
            Map<String, CatalogoAtributo> definiciones = definicionesPorTipo.computeIfAbsent(
                    propiedad.getTipoInmueble(),
                    tipo -> gobierno.definicionesDe(idOrganizacion, tipo));
            resultado.put(propiedad.getId(), armar(propiedad,
                    porPropiedad.getOrDefault(propiedad.getId(), List.of()), definiciones));
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
    public Map<Long, ValoresDePropiedad> gobernadosDeVarias(Collection<Long> idsPropiedad) {
        if (idsPropiedad.isEmpty()) {
            return Map.of();
        }
        Map<Long, ValoresDePropiedad> resultado = new LinkedHashMap<>();
        agrupar(idsPropiedad).forEach((id, filas) -> {
            ValoresDePropiedad.Constructor constructor = new ValoresDePropiedad.Constructor();
            filas.forEach(fila -> constructor.con(fila.getClave(), comoValor(fila)));
            resultado.put(id, constructor.construir());
        });
        return resultado;
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
    private static ValoresDePropiedad armar(Propiedad propiedad, List<AtributoPropiedad> filas,
                                            Map<String, CatalogoAtributo> definiciones) {
        ValoresDePropiedad.Constructor constructor = new ValoresDePropiedad.Constructor();
        for (AtributoPropiedad fila : filas) {
            CatalogoAtributo definicion = definiciones.get(fila.getClave());
            if (definicion != null && definicion.esEstructural()) {
                continue;
            }
            constructor.con(fila.getClave(), comoValor(fila));
        }
        for (CatalogoAtributo definicion : definiciones.values()) {
            if (definicion.esEstructural()) {
                constructor.con(definicion.getClave(),
                        EscritorEstructural.leerValor(propiedad, definicion.getCampoEstructural()));
            }
        }
        return constructor.construir();
    }

    private static ValorLogico comoValor(AtributoPropiedad fila) {
        if (fila.getValorTexto() != null) {
            return ValorLogico.deTexto(fila.getValorTexto());
        }
        if (fila.getValorNumero() != null) {
            return ValorLogico.deNumero(fila.getValorNumero());
        }
        return ValorLogico.deBooleano(fila.getValorBooleano());
    }
}
