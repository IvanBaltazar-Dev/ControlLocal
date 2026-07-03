package com.controllocal.rest.reports;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.rest.dto.Dtos;

public final class IndicadoresJasperMapper {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private IndicadoresJasperMapper() {
    }

    public static ReporteIndicadoresJasperDto desde(
            Dtos.IndicadoresResponse datos,
            boolean esAdmin,
            boolean esAgente,
            String periodoParam) {
        List<Kpi> kpis = kpis(datos, esAdmin, esAgente);
        while (kpis.size() < 4) {
            kpis.add(new Kpi("-", "-", "-"));
        }
        String titulo = esAdmin ? "Reportes globales" : esAgente ? "Mi reporte" : "Reportes de equipo";
        String subtitulo = esAdmin
                ? "Indicadores de toda la corredora y desempeño por broker"
                : esAgente
                        ? "Actividad comercial propia: captaciones, cierres y conversión"
                        : "Indicadores de los agentes a cargo y conversión del equipo";
        return new ReporteIndicadoresJasperDto(
                titulo,
                subtitulo,
                periodoEtiqueta(periodoParam),
                fechaGeneracion(),
                resumen(datos),
                kpis.get(0).etiqueta(), kpis.get(0).valor(), kpis.get(0).delta(),
                kpis.get(1).etiqueta(), kpis.get(1).valor(), kpis.get(1).delta(),
                kpis.get(2).etiqueta(), kpis.get(2).valor(), kpis.get(2).delta(),
                kpis.get(3).etiqueta(), kpis.get(3).valor(), kpis.get(3).delta(),
                operativo(datos.operativo()),
                serie(datos.mesesEtiquetas(), datos.cierresPorMes(), "cierres"),
                serie(datos.mesesEtiquetas(), datos.captacionesPorPeriodo(), "captaciones"),
                serie(datos.mesesEtiquetas(), datos.conversionPorPeriodo(), "% conversion"),
                salud(datos.captacionesSalud()),
                embudo(datos.embudo()),
                desempeno(datos.desempeno(), esAdmin),
                ReportCharts.tendencia(
                        datos.mesesEtiquetas(),
                        datos.captacionesPorPeriodo(),
                        datos.cierresPorMes(),
                        datos.conversionPorPeriodo()),
                ReportCharts.pie("Salud de captaciones", itemsSalud(datos.captacionesSalud())),
                ReportCharts.funnel(itemsEmbudo(datos.embudo())),
                ReportCharts.performance(itemsDesempeno(datos.desempeno(), esAdmin)));
    }

    public static String periodoEtiqueta(String periodoParam) {
        String normal = periodoParam == null ? "" : periodoParam.trim().toLowerCase();
        return switch (normal) {
            case "7", "7d", "semana" -> "7 días";
            case "15", "15d" -> "15 días";
            case "1m", "30", "30d", "mes" -> "1 mes";
            case "3m", "90", "90d" -> "3 meses";
            case "1y", "12m", "365", "365d", "ano", "anio" -> "1 año";
            default -> "6 meses";
        };
    }

    private static List<Kpi> kpis(Dtos.IndicadoresResponse datos, boolean esAdmin, boolean esAgente) {
        int activasPeriodo = datos.captacionesSalud().stream()
                .filter(e -> e.nombre() != null && e.nombre().toLowerCase().startsWith("activas"))
                .findFirst()
                .map(Dtos.IndicadorConteo::valor)
                .orElse(datos.captacionesActivas());
        List<Kpi> resultado = new ArrayList<>();
        if (esAdmin) {
            resultado.add(new Kpi("Captaciones totales", numero(datos.captacionesTotales()), activasPeriodo + " activas"));
            resultado.add(new Kpi("Operaciones cerradas", numero(datos.cierres()), "acumulado"));
            resultado.add(new Kpi("Tasa de conversión", datos.conversionPropia() + "%",
                    datos.cierresCohorte() + " de " + datos.captacionesTotales() + " captaciones cerradas"));
            resultado.add(new Kpi("Agentes activos", numero(datos.agentesActivos()), datos.brokersActivos() + " brokers"));
            return resultado;
        }
        if (esAgente) {
            resultado.add(new Kpi("Mis captaciones", numero(datos.captacionesTotales()), activasPeriodo + " activas"));
            resultado.add(new Kpi("Mis cierres", numero(datos.cierres()), "en el periodo"));
            resultado.add(new Kpi("Conversión propia", datos.conversionPropia() + "%",
                    datos.cierresCohorte() + " de " + datos.captacionesTotales() + " captaciones cerradas"));
            resultado.add(new Kpi("Mis visitas", numero(datos.visitas()), "en el periodo"));
            return resultado;
        }
        resultado.add(new Kpi("Captaciones del equipo", numero(datos.captacionesTotales()), activasPeriodo + " activas"));
        resultado.add(new Kpi("Cierres del equipo", numero(datos.cierres()), "acumulado"));
        resultado.add(new Kpi("Conversión", datos.conversionPropia() + "%",
                datos.cierresCohorte() + " de " + datos.captacionesTotales() + " captaciones cerradas"));
        resultado.add(new Kpi("Visitas realizadas", numero(datos.visitas()), "del equipo"));
        return resultado;
    }

    private static String operativo(Dtos.IndicadorOperativo op) {
        if (op == null) {
            return "Sin indicadores operativos.";
        }
        return String.join("\n",
                "Recontactos vencidos: " + op.recontactosVencidos(),
                "Recontactos al día: " + op.recontactosAlDia(),
                "Días prom. sin seguimiento: " + op.diasPromedioSinSeguimiento(),
                "Visitas pendientes: " + op.visitasPendientes(),
                "Solicitudes sin cierre: " + op.solicitudesSinCierre(),
                "Conversión prospección -> captación: " + op.conversionProspeccionCaptacion() + "%");
    }

    private static String resumen(Dtos.IndicadoresResponse datos) {
        return "Ambito: " + texto(datos.ambito(), "Sin ambito")
                + " | Captaciones: " + datos.captacionesTotales()
                + " | Cierres: " + datos.cierres()
                + " | Visitas: " + datos.visitas()
                + "\nInteracciones: " + datos.interacciones()
                + " | Oportunidades: " + datos.oportunidadesActivas()
                + " | Solicitudes: " + datos.solicitudesPorEvaluar()
                + "\nPor revisar: " + datos.captacionesPorRevisar()
                + " | Propiedades activas equipo: " + datos.propiedadesEquipo();
    }

    private static String serie(List<String> etiquetas, List<Integer> valores, String sufijo) {
        if (etiquetas == null || etiquetas.isEmpty()) {
            return "Sin datos.";
        }
        List<String> lineas = new ArrayList<>();
        int n = Math.min(etiquetas.size(), valores != null ? valores.size() : 0);
        for (int i = 0; i < n; i++) {
            lineas.add(etiquetas.get(i) + ": " + valores.get(i) + " " + sufijo);
        }
        return String.join("\n", lineas);
    }

    private static String salud(List<Dtos.IndicadorConteo> items) {
        if (items == null || items.isEmpty()) {
            return "Sin etapas registradas.";
        }
        int total = items.stream().mapToInt(Dtos.IndicadorConteo::valor).sum();
        List<String> lineas = new ArrayList<>();
        for (Dtos.IndicadorConteo item : items) {
            int pct = total > 0 ? (int) Math.round(item.valor() * 100.0 / total) : 0;
            lineas.add(item.nombre() + ": " + item.valor() + " (" + pct + "%)");
        }
        lineas.add("Total captaciones: " + total);
        return String.join("\n", lineas);
    }

    private static String embudo(List<Dtos.IndicadorEmbudo> items) {
        if (items == null || items.isEmpty()) {
            return "Sin operaciones en el embudo.";
        }
        return items.stream()
                .map(item -> item.etapa() + ": " + item.valor() + " (" + item.porcentaje() + "%)")
                .reduce((a, b) -> a + "\n" + b)
                .orElse("Sin operaciones en el embudo.");
    }

    private static String desempeno(List<Dtos.IndicadorDesempeno> items, boolean esAdmin) {
        if (items == null || items.isEmpty()) {
            return "Sin actividad registrada.";
        }
        String responsable = esAdmin ? "Broker" : "Agente";
        return items.stream()
                .limit(8)
                .map(item -> responsable + " " + item.nombre()
                        + " | Captaciones: " + item.captaciones()
                        + " | Cierres: " + item.cierres()
                        + " | Conversión: " + item.conversion() + "%")
                .reduce((a, b) -> a + "\n" + b)
                .orElse("Sin actividad registrada.");
    }

    private static List<ReportCharts.Item> itemsSalud(List<Dtos.IndicadorConteo> items) {
        if (items == null) {
            return List.of();
        }
        int[] colores = { ReportCharts.green(), ReportCharts.blue(), ReportCharts.orange(), ReportCharts.red() };
        List<ReportCharts.Item> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Dtos.IndicadorConteo item = items.get(i);
            out.add(ReportCharts.item(item.nombre(), item.valor(), "", colores[i % colores.length]));
        }
        return out;
    }

    private static List<ReportCharts.Item> itemsEmbudo(List<Dtos.IndicadorEmbudo> items) {
        if (items == null) {
            return List.of();
        }
        int[] colores = { ReportCharts.blue(), ReportCharts.cyan(), ReportCharts.purple(), ReportCharts.green() };
        List<ReportCharts.Item> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Dtos.IndicadorEmbudo item = items.get(i);
            out.add(ReportCharts.item(item.etapa(), item.valor(), "(" + item.porcentaje() + "%)", colores[i % colores.length]));
        }
        return out;
    }

    private static List<ReportCharts.Item> itemsDesempeno(List<Dtos.IndicadorDesempeno> items, boolean esAdmin) {
        if (items == null) {
            return List.of();
        }
        String responsable = esAdmin ? "Broker " : "Agente ";
        List<ReportCharts.Item> out = new ArrayList<>();
        for (Dtos.IndicadorDesempeno item : items) {
            out.add(ReportCharts.item(
                    responsable + item.nombre(),
                    item.cierres(),
                    item.cierres() + " cierres | " + item.captaciones() + " caps. | " + item.conversion() + "%",
                    ReportCharts.blue()));
        }
        return out;
    }

    private static String numero(int valor) {
        return Integer.toString(valor);
    }

    private static String texto(String valor, String fallback) {
        return valor == null || valor.isBlank() ? fallback : valor.trim();
    }

    private static String fechaGeneracion() {
        return FECHA_HORA.format(LocalDateTime.now(ZONA_LIMA));
    }

    private record Kpi(String etiqueta, String valor, String delta) {
    }
}
