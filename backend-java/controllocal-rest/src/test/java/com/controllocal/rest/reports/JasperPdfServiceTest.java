package com.controllocal.rest.reports;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JasperPdfServiceTest {

    private final JasperPdfService service = new JasperPdfService();

    @Test
    void generaContratoExclusividadPdf() {
        byte[] pdf = service.generarPdf("contrato_exclusividad.jasper", Map.of(), List.of(
                new ContratoExclusividadReporteDto(
                        "CAP-001", "Propietario Demo", "Agente Demo", "Av. Lima 123",
                        "Miraflores", "120 m2", "5%", "01/01/2026 - 30/06/2026",
                        "Encargo exclusivo", "29/06/2026 23:00")));

        assertPdf(pdf);
    }

    @Test
    void generaFichaCaptacionPdf() {
        byte[] pdf = service.generarPdf("ficha_captacion.jasper", Map.of(), List.of(
                new FichaCaptacionReporteDto(
                        "CAP-001", "Av. Lima 123", "Miraflores", "Propietario Demo",
                        "Agente Demo", "120 m2", "Restaurante", "USD 5000", "5%",
                        "01/01/2026 - 30/06/2026", "4 / 5", "Encargo exclusivo",
                        "Activa", "Sin observaciones", "29/06/2026 23:00")));

        assertPdf(pdf);
    }

    @Test
    void generaReportePropietarioPdf() throws IOException {
        byte[] pdf = service.generarPdf("reporte_propietario.jasper", Map.of(), List.of(
                new ReportePropietarioJasperDto(
                        "CAP-001", "Av. Espana 123", "Jesus Maria", "Senora Munoz",
                        "Agente Demo", "Activa", "01/06/2026 - 29/06/2026", "29/06/2026",
                        "Correo electronico", "120 m2", "Restaurante y cafeteria",
                        "USD 5000", "5%", "01/01/2026 - 30/06/2026", "Encargo exclusivo",
                        "8", "3", "38%",
                        "El periodo genero 8 consultas y 3 visitas, con conversion a visita de 38%.",
                        "Precio observado por ubicacion y tamano del local",
                        "Revisar renta referencial y fotografias de fachada",
                        "29/06/2026 23:00", ReportCharts.propietario(8, 3))));

        assertPdf(pdf);
        guardarPdf("reporte_propietario.pdf", pdf);
    }

    @Test
    void generaReporteIndicadoresPdf() throws IOException {
        byte[] pdf = service.generarPdf("reporte_indicadores.jasper", Map.of(), List.of(
                new ReporteIndicadoresJasperDto(
                        "Mi reporte", "Actividad comercial propia: captaciones, cierres y conversion",
                        "6 meses", "29/06/2026 23:00",
                    "Ambito: Mi actividad | Captaciones: 12 | Cierres: 3 | Visitas: 18\n"
                            + "Interacciones: 5 | Oportunidades: 10 | Solicitudes: 2\n"
                            + "Por revisar: 2 | Propiedades activas equipo: 20",
                        "Mis captaciones", "12", "8 activas",
                        "Mis cierres", "3", "en el periodo",
                        "Conversion propia", "25%", "3 de 12 captaciones cerradas",
                        "Mis visitas", "18", "en el periodo",
                    "Recontactos vencidos: 0\nRecontactos al dia: 5\nDias prom. sin seguimiento: 2\n"
                            + "Visitas pendientes: 4\nSolicitudes sin cierre: 2\nConversion prospeccion -> captacion: 30%",
                        "Ene 26: 1 cierres\nFeb 26: 2 cierres",
                        "Ene 26: 4 captaciones\nFeb 26: 8 captaciones",
                        "Ene 26: 25% conversion\nFeb 26: 25% conversion",
                        "Activas: 8 (67%)\nPor revisar: 2 (17%)\nTotal captaciones: 12",
                        "Oportunidades activas: 10 (100%)\nCon visita realizada: 6 (60%)",
                        "Agente Munoz | Captaciones: 12 | Cierres: 3 | Conversion: 25%",
                        ReportCharts.tendencia(List.of("Ene 26", "Feb 26"), List.of(4, 8), List.of(1, 2), List.of(25, 25)),
                        ReportCharts.pie("Salud de captaciones", List.of(
                                ReportCharts.item("Activas", 8, "", ReportCharts.green()),
                                ReportCharts.item("Por revisar", 2, "", ReportCharts.blue()))),
                        ReportCharts.funnel(List.of(
                                ReportCharts.item("Oportunidades", 10, "(100%)", ReportCharts.blue()),
                                ReportCharts.item("Visitas", 6, "(60%)", ReportCharts.green()))),
                        ReportCharts.performance(List.of(
                                ReportCharts.item("Agente Munoz", 3, "3 cierres | 12 caps. | 25%", ReportCharts.blue()))))));

        assertPdf(pdf);
        guardarPdf("reporte_indicadores.pdf", pdf);
    }

    @Test
    void generaFichaPropiedadPdf() throws IOException {
        byte[] pdf = service.generarPdf("ficha_propiedad.jasper", Map.of(), List.of(
                new FichaPropiedadReporteDto(
                        "CAP-001", "Av. Espana 123", "Jesus Maria", "Activa",
                        "120 m2", "Restaurante y cafeteria", "3 ambientes", "5 anos",
                        "Zona comercial", "8 m", "2", "12 kW", "Si",
                        "CZ", "USD 200", "USD 5000", "5%", "4 / 5",
                        "Encargo exclusivo", "01/01/2026 - 30/06/2026",
                        "Vence en 1 dia", "Senora Munoz", "Persona juridica - RUC",
                        "20123456789", "987654321", "demo@email.com", "Agente Pena",
                        "Local comercial con buena iluminacion, bano propio y fachada amplia.",
                        "29/06/2026 - Inicial - USD 5000",
                        "2 fotos registradas", "29/06/2026 23:00")));

        assertPdf(pdf);
        guardarPdf("ficha_propiedad.pdf", pdf);
    }

    private static void assertPdf(byte[] pdf) {
        assertTrue(pdf.length > 4);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
    }

    private static void guardarPdf(String nombre, byte[] pdf) throws IOException {
        Path directorio = Path.of("target", "test-pdfs");
        Files.createDirectories(directorio);
        Files.write(directorio.resolve(nombre), pdf);
    }
}
