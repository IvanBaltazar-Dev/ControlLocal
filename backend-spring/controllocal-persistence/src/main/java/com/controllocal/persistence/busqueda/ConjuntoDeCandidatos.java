package com.controllocal.persistence.busqueda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que devuelve el {@link MotorBusquedaInmobiliaria}: <b>que ids entran en
 * esta pagina y cuantos hay en total</b>.
 *
 * <p>Los dos salen de la misma consulta compuesta y de la misma transaccion, y
 * por eso viajan juntos en un solo valor: separarlos invitaria a contar con un
 * criterio y paginar con otro, que es exactamente el descuadre entre el
 * contador y la lista que el conjunto de candidatos vino a impedir.
 *
 * <p>Aqui no hay filas todavia. Cargar la proyeccion -la de {@code /locales} o
 * la universal- es del recurso, porque es lo unico que de verdad se diferencia
 * entre los dos: el motor decide QUE entra, no COMO se pinta.
 *
 * @param ids   los ids de la pagina, <b>ya ordenados</b> como el recurso pidio.
 *              Quien cargue la proyeccion debe respetar este orden: el
 *              {@code in (...)} de la carga no lo garantiza.
 * @param total el tamano del conjunto completo, no el de la pagina.
 */
public record ConjuntoDeCandidatos(List<Long> ids, long total) {

    public boolean vacio() {
        return ids.isEmpty();
    }

    /**
     * Las filas cargadas, <b>en el orden que decidio el motor</b>.
     *
     * <p>Existe porque un {@code where id in (...)} no promete ningun orden:
     * PostgreSQL devuelve lo que le convenga al plan. Sin esto, el listado
     * universal -que publica {@code id DESC}- recibiria sus propias filas
     * ordenadas al reves en cuanto el planificador cambiara de idea, y seria un
     * defecto intermitente que ninguna prueba con pocas filas reproduce.
     *
     * <p>Ordena por la POSICION del id en esta lista, no por el id: asi sirve
     * para los dos sentidos sin saber cual es, y seguiria sirviendo el dia que
     * el orden no sea por clave primaria.
     *
     * @param filas lo que devolvio el cargador de proyeccion, en cualquier orden
     * @param idDe  como se lee el id de una fila
     */
    public <T> List<T> ordenadas(List<T> filas, java.util.function.Function<T, Long> idDe) {
        Map<Long, Integer> posicion = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            posicion.put(ids.get(i), i);
        }
        List<T> ordenadas = new ArrayList<>(filas);
        // Una fila cuyo id no este en el conjunto no puede existir -se cargo POR
        // esos ids-, pero si apareciera se va al final en vez de reventar: el
        // listado no es sitio para una excepcion por un orden.
        ordenadas.sort(Comparator.comparingInt(
                fila -> posicion.getOrDefault(idDe.apply(fila), Integer.MAX_VALUE)));
        return List.copyOf(ordenadas);
    }
}
