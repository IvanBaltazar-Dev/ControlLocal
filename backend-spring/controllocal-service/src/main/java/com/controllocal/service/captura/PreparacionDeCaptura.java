package com.controllocal.service.captura;

import com.controllocal.service.captura.MotorDeCaptura.MetadatoRespuesta;
import com.controllocal.service.captura.RetornoCaptura.Grupo;
import com.controllocal.service.captura.SuficienciaInmueble.Contexto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Salidas deterministas de los16grupos aprobados; sin PDF, IA, Radar o consultas de mercado. */
@Component
public final class PreparacionDeCaptura {
    public record DefinicionGrupo(String codigo, String resultado, List<String> claves) { }
    private final List<DefinicionGrupo> grupos;
    private final Clock reloj;

    @Autowired
    public PreparacionDeCaptura(ObjectMapper mapper) { this(mapper, Clock.systemUTC()); }

    PreparacionDeCaptura(ObjectMapper mapper, Clock reloj) {
        this.reloj = reloj;
        try (var fuente = new ClassPathResource("captura/grupos-retorno.json").getInputStream()) {
            grupos = List.copyOf(mapper.readValue(fuente, new TypeReference<List<DefinicionGrupo>>() { }));
        } catch (Exception error) {
            throw new IllegalStateException("Definición ejecutable de retornos no disponible.", error);
        }
        long claves = grupos.stream().flatMap(g -> g.claves().stream()).count();
        if (claves != 126 || grupos.stream().flatMap(g -> g.claves().stream()).distinct().count() != claves)
            throw new IllegalStateException("La definición de retornos debe conservar126claves únicas.");
    }

    public List<DefinicionGrupo> definiciones() { return grupos; }

    LocalDate hoy() { return LocalDate.now(reloj.withZone(ZoneId.of("America/Lima"))); }

    public RetornoCaptura preparar(Map<String, Object> conocido, Map<String, MetadatoRespuesta> metadatos,
                                   Contexto contexto, List<String> cambios, OffsetDateTime deteccion,
                                   boolean identificacionPendiente) {
        List<Grupo> resultados = new ArrayList<>();
        for (DefinicionGrupo grupo : grupos) {
            Map<String, Object> datos = new LinkedHashMap<>();
            for (String clave : grupo.claves()) if (conocido.get(clave) != null) datos.put(clave, conocido.get(clave));
            if ("CU01".equals(grupo.codigo())) {
                poner(datos, "tipoPropiedad", conocido.get("tipoPropiedad"));
                if (contexto != null) {
                    poner(datos, "operacionObservada", contexto.operacion());
                    poner(datos, "objetoOfrecido", contexto.objetoOfrecido());
                }
                resultados.add(new Grupo(grupo.codigo(), grupo.resultado(), datos,
                        List.of("PREPARAR_PRIMER_CONTACTO", "CONTINUAR_CAPTURA")));
            } else if (!datos.isEmpty() || "CU02".equals(grupo.codigo()) && conocido.containsKey("direccion")) {
                if ("CU02".equals(grupo.codigo())) {
                    poner(datos, "direccion", conocido.get("direccion"));
                    poner(datos, "distrito", conocido.get("distrito"));
                    Map<String, String> conceptos = new LinkedHashMap<>();
                    grupo.claves().forEach(clave -> {
                        var meta = metadatos.get(clave);
                        if (meta != null && meta.concepto() != null) conceptos.put(clave, meta.concepto());
                    });
                    if (!conceptos.isEmpty()) datos.put("conceptos", conceptos);
                }
                resultados.add(new Grupo(grupo.codigo(), grupo.resultado(), datos,
                        List.of("REVISAR_PREPARACION", "CONTINUAR_CAPTURA")));
            }
        }
        List<String> preguntas = new ArrayList<>();
        sugerir(preguntas, "tipoPropiedad", conocido, metadatos);
        sugerir(preguntas, "distrito", conocido, metadatos);
        if (contexto == null || contexto.operacion() == null || contexto.operacion().isBlank())
            sugerir(preguntas, "contexto.operacion", conocido, metadatos);
        sugerir(preguntas, "direccion", conocido, metadatos);
        sugerir(preguntas, "interiorUnidad", conocido, metadatos,
                "DEPARTAMENTO".equals(conocido.get("tipoPropiedad")) || "OFICINA".equals(conocido.get("tipoPropiedad")));
        sugerir(preguntas, "metraje_total", conocido, metadatos);

        var revision = identificacionPendiente ? revision(deteccion) : null;
        // El corpus de referencias autorizadas no se sustituye por cartera privada,
        // datos ficticios ni por un número calculado sin elegibilidad6.1.
        return new RetornoCaptura("DISPONIBLE", resultados, preguntas.stream().limit(3).toList(), cambios,
                revision, new RetornoCaptura.ReferenciaPrecio("SIN_REFERENCIAS_ELEGIBLES",
                List.of("REFERENCIAS_AUTORIZADAS")));
    }

    private RetornoCaptura.Revision revision(OffsetDateTime deteccion) {
        ZoneId lima = ZoneId.of("America/Lima");
        LocalDate hoy = hoy();
        long dias = deteccion == null ? 0 : Math.max(0,
                ChronoUnit.DAYS.between(deteccion.atZoneSameInstant(lima).toLocalDate(), hoy));
        return new RetornoCaptura.Revision(dias, dias == 0 ? "BAJA" : dias < 3 ? "MEDIA" : "COMPLETAR_O_DESCARTAR",
                List.of("CONTINUAR_CAPTURA", "POSPONER_REVISION_PROPIA", "DESCARTAR_CAPTURA"), null, null);
    }
    private static void sugerir(List<String> preguntas, String clave, Map<String, Object> conocido,
                                Map<String, MetadatoRespuesta> metadatos) {
        sugerir(preguntas, clave, conocido, metadatos, true);
    }
    private static void sugerir(List<String> preguntas, String clave, Map<String, Object> conocido,
                                Map<String, MetadatoRespuesta> metadatos, boolean pertinente) {
        MetadatoRespuesta meta = metadatos.get(clave);
        if (pertinente && !conocido.containsKey(clave) && (meta == null || !meta.noSabe())) preguntas.add(clave);
    }
    private static void poner(Map<String, Object> destino, String clave, Object valor) {
        if (valor != null) destino.put(clave, valor);
    }
}
