package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.matching.ContratoMatching.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CAT-E20/AC091–094/099/149: un caso de lectura, sin hechos comerciales implícitos.
 */
public interface CoincidenciaService {

    enum Origen { DEMANDA, ENCARGO, CAPTURA, PROSPECCION, FISICO }
    enum TipoVersion { DEMANDA, ENCARGO, CAPTURA, FISICA }
    enum Capa { L0, L1, L2 }
    enum EstadoResultado { DISPONIBLE, CONTEXTO_CAMBIADO, CONTINUIDAD_VENCIDA }
    enum Cobertura { COMPLETA, LIMITADA }
    enum ModoExploracion { DESCUBRIR, EXPLORAR }
    /** Contexto de lectura separado; no modifica el presupuesto ni la Demanda original. */
    record ExploracionPrecio(ModoExploracion modo,BigDecimal limite,String moneda) { }
    /** Proyección reducida: la Evaluacion interna y su clave de origen no se serializan. */
    record AlternativaPrecio(BigDecimal precio,BigDecimal maximoOriginal,String moneda,String concepto,
                             String periodo,BigDecimal excesoAbsoluto,BigDecimal excesoPorcentaje) { }

    /** La versión es observada; nunca se completa consultando la actual dentro de la actuación. */
    record Consulta(Origen origen, Long idOrigen, Long versionOrigen, TipoVersion tipoVersion, Modalidad modalidad,
                    Set<Capa> capas, Categoria categoria, Integer tamano,ExploracionPrecio exploracion) {
        public Consulta { capas = capas == null ? Set.of() : Set.copyOf(capas); }
    }
    record Seleccion(Origen origen, long idOrigen, long versionOrigen, TipoVersion tipoVersion, Modalidad modalidad,
                      Set<Capa> capas, Categoria categoria, int tamano,ExploracionPrecio exploracion) {
        public Seleccion { capas = Set.copyOf(capas); }
    }
    record Razon(String referenciaCriterio, String clave, Exigencia exigencia, int peso,
                 Conclusion conclusion, Causa causa) { }
    /** Proyección de salida explícita; no serializa la clave/ámbito internos del motor. */
    record Coincidencia(String referencia, long version, Capa capa, Categoria categoria,
                         Conclusion g, Conclusion h, long s, long u, long w,
                         List<Razon> razones, Map<String,Object> datosPermitidos,CoberturaCriterios coberturaCriterios,
                         AlternativaPrecio alternativaPrecio) {
        public Coincidencia {
            razones = List.copyOf(razones);
            datosPermitidos = Collections.unmodifiableMap(new LinkedHashMap<>(datosPermitidos));
        }
    }
    /** Cada grupo es UNA demanda/version; S/U nunca ordenan grupos de búsquedas diferentes. */
    record Grupo(String referenciaDemanda, long versionDemanda, List<Coincidencia> candidatos) {
        public Grupo { candidatos = List.copyOf(candidatos); }
    }
    record Coincidencias(EstadoResultado estado, Seleccion seleccion, Instant corte,
                         String versionRegla, String referenciaConsulta, List<Grupo> grupos, Long total,
                         Cobertura cobertura, Set<Capa> capasNoDisponibles, String continuacion) {
        public Coincidencias {
            grupos = List.copyOf(grupos);
            capasNoDisponibles = Set.copyOf(capasNoDisponibles);
        }
    }
    record Aptitud(long idDemanda, long versionDemanda, long idEncargo, long versionEncargo,
                    String codigoEncargo, Coincidencia evaluacion) { }

    Coincidencias consultar(Consulta consulta, Actor actor, ContextoSesion sesion);
    Coincidencias continuar(String cursor, Actor actor, ContextoSesion sesion);
    /** Explica el par del grupo/demanda y oferta del recorrido; la oferta conserva su referencia estable. */
    Coincidencia explicar(String cursor, String referenciaDemanda, String referenciaCandidato, Actor actor, ContextoSesion sesion);
    /** AC101: comprueba procedencia conservada; no afirma matching actual ni aplica el TTL de navegación. */
    void verificarSeleccionParaApertura(String referenciaConsulta,String referenciaCandidato,
            long idEncargo,Long idDemanda,Long versionDemanda,Actor actor);
    /** Lectura interna actual de Hallazgo; sin porcentaje, umbral, tareas o puntuación entre demandas. */
    List<Aptitud> aptitudesPropias(Actor actor);
    /** Descriptor de origen: no evalúa ni recupera el universo candidato. */
    void verificarAcceso(Origen origen, long idOrigen, Long versionOrigen, Actor actor);
}

