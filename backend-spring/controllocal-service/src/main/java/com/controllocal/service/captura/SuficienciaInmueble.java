package com.controllocal.service.captura;

import com.controllocal.service.soporte.AtributosGobernados;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suficiencia MATERIAL de CAT-E11/E39 y catálogo18 (MAT-01/02, 26-10-2026).
 * No autoriza al actor ni acredita partes, pacto, revisión, difusión o pieza.
 * Las puertas de esos casos conservan sus controles además de esta evaluación.
 * No consulta repositorios: el consumidor aporta datos actualmente autorizados.
 */
@Component
public final class SuficienciaInmueble {
    public static final String VERSION_REGLA = "MAT-01-02-CAT18-20261026";
    public static final String UNIDAD_DE_EDIFICIO = "unidadDeEdificio";
    public static final String MANTENIMIENTO = "mantenimiento";
    public static final String POTENCIAL_O_CAMBIO_USO = "potencialOCambioUso";
    public static final String EXPLOTACION_COMERCIAL = "explotacionComercial";
    public static final String EDIFICIO_COMPLETO = "edificioCompleto";
    public static final Set<String> COORDENADAS_APLICABILIDAD = Set.of(UNIDAD_DE_EDIFICIO,
            MANTENIMIENTO,POTENCIAL_O_CAMBIO_USO,EXPLOTACION_COMERCIAL,EDIFICIO_COMPLETO);
    public static final String AREA_TOTAL = "AREA_TOTAL";
    public static final String AREA_CONSTRUIDA = "AREA_CONSTRUIDA";
    public static final String AREA_TERRENO = "AREA_TERRENO";
    public static final String AREA_ARRENDABLE = "AREA_ARRENDABLE";
    public static final String AREA_ESTACIONAMIENTO = "AREA_ESTACIONAMIENTO";
    public static final String AREA_OBJETO_OFRECIDO = "AREA_OBJETO_OFRECIDO";

    public enum Accion { GUARDAR, IDENTIFICAR, CONFIRMAR_ENCARGO, PUBLICAR }
    /** Estados del dato de BROX4.4.5; no estados de vigencia del contrato editable. */
    public enum EstadoDato { CONOCIDO, FALTANTE, AUSENCIA_DECLARADA, NO_APLICABLE }
    public enum Aplicabilidad { APLICA, NO_APLICA, NO_PRECISADA }
    public enum Motivo {
        DATO_FALTANTE, TIPO_NO_RECONOCIDO, CONTEXTO_NO_PRECISADO,
        UNIDAD_NO_IDENTIFICADA, CONCEPTO_AREA_FALTANTE, CONCEPTO_AREA_INCOMPATIBLE,
        AUSENCIA_NO_ADMITIDA, NO_APLICABLE_SIN_EVIDENCIA, NO_APLICABLE_NO_ADMITIDO,
        VALOR_INVALIDO, CATALOGO_ZONIFICACION_NO_ACREDITADO
    }

    /**
     * objetoOfrecido identifica concretamente el objeto/unidad, no sólo su tipo.
     * Aplicabilidades y prestaciones son hechos declarados/versionados del caso;
     * su ausencia nunca equivale a NO_APLICA. Operación/uso no se infieren.
     */
    public record Contexto(String tipoPropiedad, String usoOfrecido, String objetoOfrecido,
                           String operacion, Map<String, Aplicabilidad> aplicabilidad,
                           Set<String> prestacionesOfrecidas, String jurisdiccion) {
        public Contexto {
            aplicabilidad = aplicabilidad == null ? Map.of() : Map.copyOf(aplicabilidad);
            prestacionesOfrecidas = prestacionesOfrecidas == null
                    ? Set.of() : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(prestacionesOfrecidas));
        }
        /** No declarar jurisdicción es legítimo para actuaciones que no usan zonificación. */
        public Contexto(String tipoPropiedad, String usoOfrecido, String objetoOfrecido,
                        String operacion, Map<String, Aplicabilidad> aplicabilidad,
                        Set<String> prestacionesOfrecidas) {
            this(tipoPropiedad, usoOfrecido, objetoOfrecido, operacion, aplicabilidad,
                    prestacionesOfrecidas, null);
        }
    }

    /**
     * Concepto no convierte el valor: permite distinguir superficies incompatibles.
     * Para zonificación, referenciaCatalogo identifica jurisdicción+versión revisada;
     * el valor debe haber sido validado por el catálogo gobernado de esa referencia.
     */
    public record Dato(Object valor, EstadoDato estado, String evidenciaRef, String concepto,
                       String referenciaCatalogo) {
        public static Dato conocido(Object valor) {
            return new Dato(valor, EstadoDato.CONOCIDO, null, null, null);
        }
        public static Dato faltante() {
            return new Dato(null, EstadoDato.FALTANTE, null, null, null);
        }
    }

    public record Campo(String clave, List<String> alternativas,
                         boolean admiteAusenciaDeclarada, boolean admiteNoAplicable) {
        public Campo { alternativas = List.copyOf(alternativas); }
    }
    public record Faltante(String clave, Motivo motivo, List<String> alternativas) {
        public Faltante { alternativas = List.copyOf(alternativas); }
    }
    public record Evaluacion(Accion accion, Contexto contexto, String versionRegla,
                             boolean suficiente, List<Campo> aplicables,
                             List<Faltante> faltantes) {
        public Evaluacion {
            aplicables = List.copyOf(aplicables);
            faltantes = List.copyOf(faltantes);
        }
    }

    public Evaluacion evaluar(Accion accion, Contexto contexto, Map<String, Dato> datos) {
        if (accion == null) throw new IllegalArgumentException("La acción es obligatoria.");
        Map<String, Dato> valores = datos == null ? Map.of() : datos;
        Revision revision = new Revision(accion, contexto, valores);
        // Guardar conserva información parcial; validar el formato del dato conocido
        // corresponde al catálogo/escritor, no se convierte en mínimo de guardado.
        if (accion == Accion.GUARDAR) return revision.resultado();

        String tipo = contexto == null ? null
                : AtributosGobernados.codigoDelTipo(contexto.tipoPropiedad()).orElse(null);
        revision.campo("tipoPropiedad", false, false);
        if (tipo == null) revision.falta("tipoPropiedad", contexto == null
                || vacio(contexto.tipoPropiedad()) ? Motivo.DATO_FALTANTE : Motivo.TIPO_NO_RECONOCIDO);
        revision.exigir("distrito", false, false);
        revision.exigirAlternativa("direccion", List.of("referenciaInterna"), false, false);
        if ("D".equals(tipo) || "O".equals(tipo)) {
            if (!revision.conocido("interiorUnidad") && (contexto == null || vacio(contexto.objetoOfrecido()))) {
                revision.campo("interiorUnidad", List.of("objetoOfrecido"), false, false);
                revision.falta("interiorUnidad", Motivo.UNIDAD_NO_IDENTIFICADA,
                        List.of("objetoOfrecido"));
            }
        }
        if (accion == Accion.IDENTIFICAR || tipo == null) return revision.resultado();

        if (contexto == null || vacio(contexto.usoOfrecido())) revision.falta("uso", Motivo.CONTEXTO_NO_PRECISADO);
        if (contexto == null || vacio(contexto.objetoOfrecido())) revision.falta("objetoOfrecido", Motivo.CONTEXTO_NO_PRECISADO);
        if (contexto == null || vacio(contexto.operacion())) revision.falta("operacion", Motivo.CONTEXTO_NO_PRECISADO);
        revision.superficie(tipo);
        switch (tipo) {
            case "D" -> {
                revision.exigir("dormitorios", false, false);
                revision.exigir("banos", false, false);
                revision.unidadEdificio();
                revision.mantenimiento();
            }
            case "C" -> {
                revision.exigir("dormitorios", false, false);
                revision.exigir("banos", false, false);
                revision.exigir("pisos_edificacion", false, false);
                revision.potencial();
            }
            case "T" -> {
                revision.zonificacion();
                revision.exigir("agua_desague", true, false);
                revision.exigir("energia_electrica", true, false);
                revision.exigir("condicion_terreno", true, false);
            }
            case "L" -> {
                revision.exigir("rubro_permitido", false, false);
                revision.exigir("tipo_acceso", false, false);
                revision.unidadEdificio();
                revision.potencial();
            }
            case "O" -> {
                revision.unidadEdificio();
                revision.mantenimiento();
                revision.potencial();
            }
            case "A" -> {
                revision.exigir("condicion_almacenamiento", false, false);
                revision.exigirAlternativa("via_de_acceso", List.of("tipo_via_acceso"), false, false);
                revision.unidadEdificio();
            }
            case "E" -> {
                revision.exigir("tipo_acceso", false, false);
                revision.aplicabilidad(EXPLOTACION_COMERCIAL);
                // No se crea zonificación/licencia obligatoria para cochera individual.
                if (revision.aplica(EXPLOTACION_COMERCIAL)) revision.zonificacion();
            }
            case "X" -> {
                revision.exigir("descripcion", false, false);
                revision.aplicabilidad(EDIFICIO_COMPLETO);
                if (revision.aplica(EDIFICIO_COMPLETO)) {
                    revision.exigir("metraje_construido", false, false);
                    revision.exigir("pisos_edificacion", false, false);
                }
            }
            default -> throw new IllegalStateException("Tipo validado sin tratamiento: " + tipo);
        }
        // Únicamente se exigen prestaciones que constan en el ofrecimiento.
        contexto.prestacionesOfrecidas().stream().sorted()
                .forEach(clave -> revision.exigir(clave, false, false));
        return revision.resultado();
    }

    private static boolean vacio(Object valor) {
        return valor == null || valor instanceof String texto && texto.isBlank()
                || valor instanceof java.util.Collection<?> lista && lista.isEmpty();
    }

    private static final class Revision {
        private final Accion accion;
        private final Contexto contexto;
        private final Map<String, Dato> datos;
        private final Map<String, Campo> campos = new LinkedHashMap<>();
        private final Map<String, Faltante> faltantes = new LinkedHashMap<>();

        private Revision(Accion accion, Contexto contexto, Map<String, Dato> datos) {
            this.accion = accion; this.contexto = contexto; this.datos = datos;
        }
        private Evaluacion resultado() {
            return new Evaluacion(accion, contexto, VERSION_REGLA, faltantes.isEmpty(),
                    new ArrayList<>(campos.values()), new ArrayList<>(faltantes.values()));
        }
        private void campo(String clave, boolean ausencia, boolean noAplicable) {
            campo(clave, List.of(), ausencia, noAplicable);
        }
        private void campo(String clave, List<String> alternativas, boolean ausencia, boolean noAplicable) {
            campos.putIfAbsent(clave, new Campo(clave, alternativas, ausencia, noAplicable));
        }
        private void falta(String clave, Motivo motivo) { falta(clave, motivo, List.of()); }
        private void falta(String clave, Motivo motivo, List<String> alternativas) {
            faltantes.putIfAbsent(clave, new Faltante(clave, motivo, alternativas));
        }
        private boolean conocido(String clave) {
            Dato dato = datos.get(clave);
            return dato != null && dato.estado() == EstadoDato.CONOCIDO && !vacio(dato.valor());
        }
        private Motivo insuficiencia(String clave, boolean ausencia, boolean noAplicable) {
            Dato dato = datos.get(clave);
            if ("zonificacion".equals(clave) && dato != null && dato.estado() == EstadoDato.FALTANTE
                    && dato.valor() != null) return Motivo.CATALOGO_ZONIFICACION_NO_ACREDITADO;
            if (dato == null || dato.estado() == null || dato.estado() == EstadoDato.FALTANTE)
                return Motivo.DATO_FALTANTE;
            return switch (dato.estado()) {
                case CONOCIDO -> vacio(dato.valor()) ? Motivo.DATO_FALTANTE : null;
                case FALTANTE -> Motivo.DATO_FALTANTE;
                case AUSENCIA_DECLARADA -> ausencia ? null : Motivo.AUSENCIA_NO_ADMITIDA;
                case NO_APLICABLE -> !noAplicable ? Motivo.NO_APLICABLE_NO_ADMITIDO
                        : vacio(dato.evidenciaRef()) ? Motivo.NO_APLICABLE_SIN_EVIDENCIA : null;
            };
        }
        private void exigir(String clave, boolean ausencia, boolean noAplicable) {
            exigirAlternativa(clave, List.of(), ausencia, noAplicable);
        }
        private void exigirAlternativa(String clave, List<String> alternativas, boolean ausencia, boolean noAplicable) {
            campo(clave, alternativas, ausencia, noAplicable);
            Motivo motivo = insuficiencia(clave, ausencia, noAplicable);
            if (motivo != null && alternativas.stream()
                    .noneMatch(alternativa -> insuficiencia(alternativa, ausencia, noAplicable) == null)) {
                falta(clave, motivo, alternativas);
            }
        }
        private Aplicabilidad aplicabilidad(String clave) {
            campo("contexto."+clave,false,false);
            Aplicabilidad aplica = contexto.aplicabilidad().getOrDefault(clave, Aplicabilidad.NO_PRECISADA);
            if (aplica == Aplicabilidad.NO_PRECISADA) falta("contexto." + clave, Motivo.CONTEXTO_NO_PRECISADO);
            if (aplica == Aplicabilidad.NO_APLICA) {
                Dato respaldo = datos.get("contexto." + clave);
                if (respaldo == null || vacio(respaldo.evidenciaRef()))
                    falta("contexto." + clave, Motivo.NO_APLICABLE_SIN_EVIDENCIA);
            }
            return aplica;
        }
        private boolean aplica(String clave) { return contexto.aplicabilidad().get(clave) == Aplicabilidad.APLICA; }
        private void unidadEdificio() {
            if (aplicabilidad(UNIDAD_DE_EDIFICIO) == Aplicabilidad.APLICA) {
                exigir("piso", false, true);
                exigir("ascensores", false, true);
            }
        }
        private void mantenimiento() {
            if (aplicabilidad(MANTENIMIENTO) == Aplicabilidad.APLICA) {
                exigir("cuota_mantenimiento", false, false);
                exigir("moneda:cuota_mantenimiento", false, false);
            }
        }
        private void potencial() {
            if (aplicabilidad(POTENCIAL_O_CAMBIO_USO) == Aplicabilidad.APLICA) zonificacion();
        }
        private void zonificacion() {
            exigir("zonificacion", false, true);
            Dato dato = datos.get("zonificacion");
            if (conocido("zonificacion") && (vacio(dato.evidenciaRef()) || vacio(dato.referenciaCatalogo())))
                falta("zonificacion", Motivo.CATALOGO_ZONIFICACION_NO_ACREDITADO);
            if (conocido("zonificacion") && vacio(contexto.jurisdiccion()))
                falta("zonificacion", Motivo.CATALOGO_ZONIFICACION_NO_ACREDITADO);
        }
        private void superficie(String tipo) {
            List<String> alternativas = switch (tipo) {
                case "T", "E" -> List.of();
                case "D" -> List.of("metraje_construido");
                case "C", "X" -> List.of("metraje_construido", "area_terreno");
                default -> List.of("metraje_construido", "metraje_arrendable");
            };
            exigirAlternativa("metraje_total", alternativas, false, false);
            List<String> claves = new ArrayList<>(); claves.add("metraje_total"); claves.addAll(alternativas);
            List<String> presentes = claves.stream().filter(this::conocido).toList();
            if (presentes.isEmpty()) return;
            boolean compatible = false;
            Motivo motivo = Motivo.CONCEPTO_AREA_FALTANTE;
            for (String clave : presentes) {
                Dato dato = datos.get(clave);
                try {
                    if (new BigDecimal(dato.valor().toString()).signum() <= 0) {
                        motivo = Motivo.VALOR_INVALIDO; continue;
                    }
                } catch (NumberFormatException error) { motivo = Motivo.VALOR_INVALIDO; continue; }
                if (vacio(dato.concepto())) continue;
                Set<String> conceptos = switch (clave) {
                    case "metraje_construido" -> Set.of(AREA_CONSTRUIDA);
                    case "area_terreno" -> Set.of(AREA_TERRENO);
                    case "metraje_arrendable" -> Set.of(AREA_ARRENDABLE);
                    default -> switch (tipo) {
                        case "T" -> Set.of(AREA_TERRENO);
                        case "E" -> Set.of(AREA_ESTACIONAMIENTO);
                        case "X" -> Set.of(AREA_OBJETO_OFRECIDO, AREA_TERRENO, AREA_CONSTRUIDA, AREA_TOTAL);
                        default -> Set.of(AREA_TOTAL, AREA_CONSTRUIDA);
                    };
                };
                if (conceptos.contains(dato.concepto())) compatible = true;
                else motivo = Motivo.CONCEPTO_AREA_INCOMPATIBLE;
            }
            if (!compatible) falta("metraje_total", motivo, alternativas);
        }
    }
}
