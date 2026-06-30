package com.controllocal.rest.reports;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.controllocal.rest.http.ApiException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;

public class JasperPdfService {

    private static final String REPORTS_PATH = "reports/";
    private static final Locale LOCALE_PE = Locale.forLanguageTag("es-PE");

    public <T> byte[] generarPdf(String plantillaJasper, Map<String, Object> parametros, List<T> datos) {
        String recurso = REPORTS_PATH + plantillaJasper;
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(recurso)) {
            if (input == null) {
                throw new ApiException(500, "Plantilla Jasper no encontrada: " + recurso);
            }

            JasperReport reporte = (JasperReport) JRLoader.loadObject(input);
            Map<String, Object> params = parametros == null
                    ? new java.util.HashMap<>()
                    : new java.util.HashMap<>(parametros);
            params.putIfAbsent(JRParameter.REPORT_LOCALE, LOCALE_PE);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(
                    datos == null ? List.of() : datos);
            JasperPrint print = JasperFillManager.fillReport(reporte, params, dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (ApiException e) {
            throw e;
        } catch (JRException e) {
            throw new ApiException(500, "No se pudo generar el PDF Jasper. Detalle: " + e.getMessage());
        } catch (java.io.IOException e) {
            throw new ApiException(500, "No se pudo leer la plantilla Jasper. Detalle: " + e.getMessage());
        }
    }
}
