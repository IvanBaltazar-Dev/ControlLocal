package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.inmueble.Sujeto;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.ValorLogico;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Proyección normalizada compartida por Demanda, proveedores autorizados y el motor puro.
 * D1–D4/CAT-E20: no contiene Persona ni obtiene permisos de una coincidencia.
 * Los proveedores filtran recursos/campos antes de construir estas entradas y revalidan
 * antes de entregar resultados. Ningún record es un DTO de petición HTTP.
 */
public final class ContratoMatching {
    private ContratoMatching() { }

    // Selectores técnicos compartidos. Los gobernados usan SIEMPRE sujeto/clave de su definición real.
    public static final String TIPO_PROPIEDAD = selector(Sujeto.PROPIEDAD, "tipo_propiedad");
    public static final String DISTRITO = selector(Sujeto.PROPIEDAD, "distrito");
    public static final String OPERACION = selector(Sujeto.ENCARGO, "operacion");
    public static final String TOTAL_MENSUAL_OBLIGATORIO = selector(Sujeto.ENCARGO, "total_mensual_obligatorio");
    public static final String PRECIO_VENTA = selector(Sujeto.ENCARGO, "precio_venta");
    public static final String ZONIFICACION = selector(Sujeto.PROPIEDAD, "zonificacion");

    public static String selector(Sujeto sujeto, String clave) {
        return Objects.requireNonNull(sujeto).codigo() + ":" + requerida(clave, "Clave del selector");
    }

    public enum Conclusion {
        NO_DETERMINADO((short) 0), CUMPLE((short) 1), INCUMPLE((short) 2);
        private final short codigo;
        Conclusion(short codigo) { this.codigo = codigo; }
        public short codigo() { return codigo; }
        public static Conclusion desdeCodigo(short codigo) {
            return switch (codigo) {
                case 0 -> NO_DETERMINADO;
                case 1 -> CUMPLE;
                case 2 -> INCUMPLE;
                default -> throw new IllegalArgumentException("Código de conclusión no admitido.");
            };
        }
    }

    public enum Exigencia { OBLIGATORIO, PREFERENCIA }
    public enum CoberturaCriterios { COMPLETA, PARCIAL }
    public enum Prioridad {
        IMPORTANTE(3), NORMAL(2), COMPLEMENTARIA(1);
        private final int peso;
        Prioridad(int peso) { this.peso = peso; }
        public int peso() { return peso; }
    }
    public enum Modalidad { COMERCIAL, PRELIMINAR }
    public enum ComparacionTemporal { SOLAPAMIENTO_INICIO, COBERTURA_OCUPACION }
    /** Categorías de evaluación, nunca estados de Encargo. No tienen un orden implícito. */
    public enum Categoria {
        COMERCIAL_CONFIRMADA, OBLIGATORIAS_PENDIENTES, HABILITACION_PENDIENTE,
        NO_HABILITADA, INCOMPATIBLE, POTENCIAL
    }
    /** Causa de indeterminación; un fallo técnico se propaga, no cabe en este vocabulario. */
    public enum Causa {
        DATO_FALTANTE, CONFLICTO, VALOR_INVALIDO, VIGENCIA_NO_ACREDITADA,
        NO_APLICABLE, AUSENCIA_SIN_VALOR_COMPARABLE, CONCEPTO_INCOMPATIBLE, UNIDAD_INCOMPATIBLE,
        MONEDA_NO_COMPARABLE, PERIODO_INCOMPATIBLE, CONFIGURACION_INCOMPATIBLE,
        OPERACION_INCOMPATIBLE, LIMITES_INSUFICIENTES, CRITERIO_NO_INTERPRETABLE,
        SIN_CONFIRMACION_DISPONIBILIDAD, CONFIRMACION_NO_VIGENTE, CONTEXTO_TERRITORIAL_INCOMPATIBLE
    }

    /** Estado C2 reutilizado; fuente/evidencia sólo son las permitidas para esta finalidad. */
    public record MetadatosDato(EstadoDato estado, Causa causa, String referenciaDefinicion,
                                Long versionDefinicion, String evidenciaRef, String fuente,
                                LocalDate observadoEn, Instant vigenteDesde, Instant vigenteHasta) {
        public MetadatosDato {
            Objects.requireNonNull(estado, "Estado del dato explícito.");
            if (versionDefinicion != null && versionDefinicion < 0)
                throw new IllegalArgumentException("Versión de definición inválida.");
            if (vigenteDesde != null && vigenteHasta != null && vigenteHasta.isBefore(vigenteDesde))
                throw new IllegalArgumentException("Intervalo de vigencia inválido.");
        }
    }

    /**
     * Valores canónicos del contexto. Configuración de consulta null no restringe por ID;
     * cada oferta verifica que sus datos no pertenezcan a otra configuración comercial.
     * Moneda, concepto, unidad y periodicidad faltantes no se inventan.
     */
    public record ContextoValor(String concepto, String unidad, String moneda, String periodo,
                                String operacion, String configuracion, String jurisdiccion,
                                String referenciaCatalogo, String versionFuente) {
        /** Consumidores reales de datos no territoriales; no acredita contexto de zonificación. */
        public ContextoValor(String concepto,String unidad,String moneda,String periodo,String operacion,String configuracion) {
            this(concepto,unidad,moneda,periodo,operacion,configuracion,null,null,null);
        }
    }

    /** OR entre alternativas de UN criterio; no admite una expresión arbitraria de paquetes. */
    public record Condicion(String claveDato, TipoDato tipo, OperadorCriterio operador,
                             ValorLogico valor, ValorLogico hasta, boolean negada,
                             ContextoValor contexto, MetadatosDato metadatos) {
        public Condicion {
            claveDato = requerida(claveDato, "Clave del dato");
            Objects.requireNonNull(tipo, "Tipo explícito.");
            Objects.requireNonNull(operador, "Operador explícito.");
            Objects.requireNonNull(contexto, "Contexto explícito.");
            Objects.requireNonNull(metadatos, "Metadatos explícitos.");
            valor = copiar(valor);
            hasta = copiar(hasta);
        }
    }

    /** La clave identifica el criterio lógico ya normalizado, no la fila/copia de su evidencia. */
    public record Criterio(String referencia, String clave, Exigencia exigencia,
                            Prioridad prioridad, List<Condicion> alternativas) {
        public Criterio {
            referencia = requerida(referencia, "Referencia de criterio");
            clave = requerida(clave, "Identidad lógica del criterio");
            Objects.requireNonNull(exigencia, "Obligatoriedad explícita.");
            if (exigencia == Exigencia.OBLIGATORIO && prioridad != null)
                throw new IllegalArgumentException("Una obligación no es una preferencia ponderada.");
            if (exigencia == Exigencia.PREFERENCIA && prioridad == null) prioridad = Prioridad.NORMAL;
            alternativas = List.copyOf(Objects.requireNonNull(alternativas, "Alternativas explícitas."));
            if (alternativas.isEmpty()) throw new IllegalArgumentException("Grupo de alternativas vacío.");
        }
        public int peso() { return exigencia == Exigencia.PREFERENCIA ? prioridad.peso() : 0; }
    }

    /** UNA demanda/búsqueda/version. El corte proviene del reloj Core, no del navegador. */
    public record Consulta(String referencia, long version, Modalidad modalidad,
                            List<Criterio> criterios, Instant corte, String versionRegla,
                            CoberturaCriterios coberturaCriterios) {
        public Consulta(String referencia,long version,Modalidad modalidad,List<Criterio> criterios,Instant corte,String versionRegla) {
            this(referencia,version,modalidad,criterios,corte,versionRegla,CoberturaCriterios.COMPLETA);
        }
        public Consulta {
            referencia = requerida(referencia, "Referencia de consulta");
            if (version < 0) throw new IllegalArgumentException("Versión de consulta inválida.");
            Objects.requireNonNull(modalidad, "Modalidad explícita.");
            criterios = List.copyOf(Objects.requireNonNull(criterios, "Criterios explícitos."));
            Objects.requireNonNull(corte, "Corte Core explícito.");
            versionRegla = requerida(versionRegla, "Versión de reglas");
            Objects.requireNonNull(coberturaCriterios,"Alcance explícito de la evaluación.");
        }
    }

    /** Intervalo válido declarado distinto de un conflicto; hasta ausente no inventa un máximo. */
    public record Dato(String clave, TipoDato tipo, ValorLogico valor, ValorLogico hasta,
                        boolean intervalo, ContextoValor contexto, MetadatosDato metadatos) {
        public Dato {
            clave = requerida(clave, "Clave del dato");
            Objects.requireNonNull(tipo, "Tipo explícito.");
            Objects.requireNonNull(contexto, "Contexto explícito.");
            Objects.requireNonNull(metadatos, "Metadatos explícitos.");
            if (!intervalo && hasta != null) throw new IllegalArgumentException("Un escalar no tiene segundo límite.");
            valor = copiar(valor);
            hasta = copiar(hasta);
        }
    }

    /** Identidad comercial interna; no se publica el ámbito de otro tenant. */
    public record ClaveCandidato(String ambitoOrigen, String oferta, String configuracion) {
        public ClaveCandidato {
            ambitoOrigen = requerida(ambitoOrigen, "Ámbito de origen");
            oferta = requerida(oferta, "Oferta");
            configuracion = requerida(configuracion, "Configuración");
        }
    }

    /** El proveedor acredita que la confirmación corresponde al recurso/operación/aspecto/contexto. */
    public record ConfirmacionDisponibilidad(Instant confirmadaEn, boolean indisponibilidadConocida,
                                             String referenciaConfirmacion, Causa indeterminacion) {
        /** Consumidores existentes sin conflicto: misma información, ninguna autoridad adicional. */
        public ConfirmacionDisponibilidad(Instant confirmadaEn, boolean indisponibilidadConocida,
                                          String referenciaConfirmacion) {
            this(confirmadaEn, indisponibilidadConocida, referenciaConfirmacion, null);
        }
    }

    public record HorizontesDisponibilidad(Instant solicitarConfirmacionEn, Instant terminaFrescuraEn,
                                           Instant recordarAtencionEn, Instant venceAtencionEn) { }

    /** Ventana real de fechas, NO intervalo de incertidumbre de una medición. Extremo null no es infinito. */
    public record VentanaFechas(LocalDate desde, LocalDate hasta, MetadatosDato metadatos) {
        public VentanaFechas { Objects.requireNonNull(metadatos); }
    }

    /**
     * accesoAutorizado no es G: acceso actual sigue siendo obligatorio en PRELIMINAR.
     * G es la habilitación comercial procedente de la puerta canónica de Encargo.
     */
    public record Oferta(String referenciaEstable, ClaveCandidato clave, long version,
                          boolean accesoAutorizado, Conclusion habilitacion,
                          ConfirmacionDisponibilidad disponibilidad, List<Dato> datos) {
        public Oferta {
            referenciaEstable = requerida(referenciaEstable, "Referencia estable");
            Objects.requireNonNull(clave, "Identidad del candidato.");
            if (version < 0) throw new IllegalArgumentException("Versión de oferta inválida.");
            Objects.requireNonNull(habilitacion, "Habilitación explícita.");
            datos = List.copyOf(Objects.requireNonNull(datos, "Datos admitidos explícitos."));
        }
    }

    public record ResultadoCondicion(Conclusion conclusion, Causa causa) {
        public ResultadoCondicion {
            Objects.requireNonNull(conclusion);
            if ((conclusion == Conclusion.NO_DETERMINADO) != (causa != null))
                throw new IllegalArgumentException("Sólo NO_DETERMINADO conserva una causa.");
        }
    }
    public record EvaluacionCriterio(String referencia, String clave, Exigencia exigencia,
                                      int peso, ResultadoCondicion resultado) { }
    /** Resultado INTERNO: el adaptador de salida proyecta lo permitido; no publica candidato/ámbito por defecto. */
    public record Evaluacion(String consulta, long versionConsulta, String versionRegla, Instant corte,
                              String referenciaEstable, ClaveCandidato candidato, long versionOferta,
                              Modalidad modalidad, Conclusion g, Conclusion h, Categoria categoria,
                              List<EvaluacionCriterio> criterios, long s, long u, long w,
                             ResultadoCondicion disponibilidad,CoberturaCriterios coberturaCriterios) {
        public Evaluacion(String consulta,long versionConsulta,String versionRegla,Instant corte,
                String referenciaEstable,ClaveCandidato candidato,long versionOferta,Modalidad modalidad,
                Conclusion g,Conclusion h,Categoria categoria,List<EvaluacionCriterio> criterios,long s,long u,long w,
                ResultadoCondicion disponibilidad) {
            this(consulta,versionConsulta,versionRegla,corte,referenciaEstable,candidato,versionOferta,modalidad,
                    g,h,categoria,criterios,s,u,w,disponibilidad,CoberturaCriterios.COMPLETA);
        }
        public Evaluacion { criterios = List.copyOf(criterios); }
    }

    /** Cada componente ya expresa el alcance mensual declarado, sin cargos iniciales implícitos. */
    public enum TratamientoMensual { EXIGIBLE, INCLUIDO_EN_OTRO, DESCUENTO_FIRME }
    public record ComponenteMensual(String referencia, Dato dato, TratamientoMensual tratamiento) {
        public ComponenteMensual {
            referencia = requerida(referencia, "Referencia del componente");
            Objects.requireNonNull(dato);
            Objects.requireNonNull(tratamiento);
        }
    }

    private static String requerida(String valor, String nombre) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(nombre + " ausente.");
        return valor.trim();
    }

    private static ValorLogico copiar(ValorLogico valor) {
        return valor == null ? null : new ValorLogico(valor.texto(),
                valor.numero() == null ? null : valor.numero().stripTrailingZeros(), valor.booleano(),
                valor.fecha(), valor.moneda(), valor.valores() == null ? null : List.copyOf(valor.valores()));
    }
}
