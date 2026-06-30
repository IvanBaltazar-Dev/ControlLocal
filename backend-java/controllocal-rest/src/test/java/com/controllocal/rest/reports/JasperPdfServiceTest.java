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
                        "CAP-001", "Av. España 123", "Jesús María", "Señora Muñoz",
                        "01/06/2026 - 29/06/2026", "29/06/2026", "Correo electrónico",
                        "120 m²", "Restaurante y cafetería", "8", "3",
                        "Precio observado por ubicación y tamaño del local",
                        "Revisar renta referencial y fotografías de fachada",
                        "29/06/2026 23:00")));

        assertPdf(pdf);
        guardarPdf("reporte_propietario.pdf", pdf);
    }

    @Test
    void generaReporteIndicadoresPdf() throws IOException {
        byte[] pdf = service.generarPdf("reporte_indicadores.jasper", Map.of(), List.of(
                new ReporteIndicadoresJasperDto(
                        "Mi reporte", "Actividad comercial propia: captaciones, cierres y conversión",
                        "6 meses", "29/06/2026 23:00",
                        "Mis captaciones", "12", "8 activas",
                        "Mis cierres", "3", "en el periodo",
                        "Conversión propia", "25%", "3 de 12 captaciones cerradas",
                        "Mis visitas", "18", "en el periodo",
                        "Recontactos vencidos: 0\nRecontactos al día: 5\nDías prom. sin seguimiento: 2",
                        "Ene 26: 1 cierres\nFeb 26: 2 cierres",
                        "Ene 26: 4 captaciones\nFeb 26: 8 captaciones",
                        "Activas: 8 (67%)\nPor revisar: 2 (17%)\nTotal captaciones: 12",
                        "Oportunidades activas: 10 (100%)\nCon visita realizada: 6 (60%)",
                        "Agente Muñoz | Captaciones: 12 | Cierres: 3 | Conversión: 25%")));

        assertPdf(pdf);
        guardarPdf("reporte_indicadores.pdf", pdf);
    }

    @Test
    void generaFichaPropiedadPdf() throws IOException {
        byte[] pdf = service.generarPdf("ficha_propiedad.jasper", Map.of(), List.of(
                new FichaPropiedadReporteDto(
                        "CAP-001", "Av. España 123", "Jesús María", "Activa",
                        "120 m²", "Restaurante y cafetería", "3 ambientes", "5 años",
                        "Zona comercial", "8 m", "2", "12 kW", "Sí",
                        "CZ", "USD 200", "USD 5000", "5%", "4 / 5",
                        "Encargo exclusivo", "01/01/2026 - 30/06/2026",
                        "Vence en 1 día", "Señora Muñoz", "Persona jurídica - RUC",
                        "20123456789", "987654321", "demo@email.com", "Agente Peña",
                        "Local comercial con buena iluminación, baño propio y fachada amplia.",
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
