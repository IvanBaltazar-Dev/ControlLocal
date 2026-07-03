package com.controllocal.rest.reports;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ReportePropietario;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.PrecioLocal;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;

public final class CaptacionJasperMapper {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat DECIMAL = new DecimalFormat(
            "#,##0.##",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-PE")));

    private CaptacionJasperMapper() {
    }

    public static ContratoExclusividadReporteDto contrato(Captacion captacion) {
        LocalComercial local = captacion.getLocalComercial();
        return new ContratoExclusividadReporteDto(
                texto(captacion.getCodigoCaptacion()),
                propietario(local),
                agente(captacion.getAgenteResponsable()),
                texto(local != null ? local.getDireccion() : null),
                texto(local != null ? local.getDistrito() : null),
                area(local != null ? local.getMetraje() : null),
                porcentaje(captacion.getComisionPactada()),
                vigencia(captacion.getFechaInicioVigencia(), captacion.getFechaFinVigencia()),
                exclusividad(captacion.getExclusividad()),
                fechaGeneracion());
    }

    public static FichaCaptacionReporteDto ficha(Captacion captacion) {
        LocalComercial local = captacion.getLocalComercial();
        return new FichaCaptacionReporteDto(
                texto(captacion.getCodigoCaptacion()),
                texto(local != null ? local.getDireccion() : null),
                texto(local != null ? local.getDistrito() : null),
                propietario(local),
                agente(captacion.getAgenteResponsable()),
                area(local != null ? local.getMetraje() : null),
                texto(local != null ? local.getRubroPermitido() : null, "No registrado"),
                moneda(local != null ? local.getPrecioReferencial() : null),
                porcentaje(captacion.getComisionPactada()),
                vigencia(captacion.getFechaInicioVigencia(), captacion.getFechaFinVigencia()),
                captacion.getUrgencia() == null ? "No registrada" : captacion.getUrgencia() + " / 5",
                exclusividad(captacion.getExclusividad()),
                descripcion(captacion.getEstado()),
                texto(captacion.getObservaciones(), "Sin observaciones"),
                fechaGeneracion());
    }

    public static ReportePropietarioJasperDto reporte(Captacion captacion, ReportePropietario reporte) {
        LocalComercial local = captacion.getLocalComercial();
        int consultas = reporte.getConsultasReportadas() == null ? 0 : reporte.getConsultasReportadas();
        int visitas = reporte.getVisitasReportadas() == null ? 0 : reporte.getVisitasReportadas();
        String conversion = porcentaje(visitas, consultas) + "%";
        return new ReportePropietarioJasperDto(
                texto(captacion.getCodigoCaptacion()),
                texto(local != null ? local.getDireccion() : null),
                texto(local != null ? local.getDistrito() : null),
                propietario(local),
                agente(captacion.getAgenteResponsable()),
                descripcion(captacion.getEstado()),
                periodo(reporte.getPeriodoInicio(), reporte.getPeriodoFin()),
                fecha(reporte.getFechaReporte()),
                descripcion(reporte.getCanalEnvio()),
                area(local != null ? local.getMetraje() : null),
                texto(local != null ? local.getRubroPermitido() : null, "No registrado"),
                moneda(local != null ? local.getPrecioReferencial() : null),
                porcentaje(captacion.getComisionPactada()),
                vigencia(captacion.getFechaInicioVigencia(), captacion.getFechaFinVigencia()),
                exclusividad(captacion.getExclusividad()),
                numero(reporte.getConsultasReportadas()),
                numero(reporte.getVisitasReportadas()),
                conversion,
                lecturaReporte(consultas, visitas, conversion),
                texto(reporte.getObjecionesFrecuentes(), "Sin objeciones registradas"),
                texto(reporte.getAjustesRecomendados(), "Sin ajustes recomendados"),
                fechaGeneracion(),
                ReportCharts.propietario(consultas, visitas));
    }

    public static FichaPropiedadReporteDto fichaPropiedad(
            Captacion captacion,
            Propietario propietarioCompleto,
            List<PrecioLocal> precios,
            int cantidadFotos) {
        LocalComercial local = captacion.getLocalComercial();
        Propietario propietario = propietarioCompleto != null
                ? propietarioCompleto
                : local != null ? local.getPropietario() : null;
        String direccion = texto(local != null ? local.getDireccion() : null);
        String distrito = texto(local != null ? local.getDistrito() : null);
        String rubro = texto(local != null ? local.getRubroPermitido() : null, "Por definir");
        String area = area(local != null ? local.getMetraje() : null);
        return new FichaPropiedadReporteDto(
                texto(captacion.getCodigoCaptacion()),
                direccion,
                distrito,
                descripcion(captacion.getEstado()),
                area,
                rubro,
                local != null && local.getAmbientes() != null ? local.getAmbientes() + " ambientes" : "-",
                local != null && local.getAntiguedadAnios() != null ? local.getAntiguedadAnios() + " años" : "-",
                texto(local != null ? local.getZonaUrbanizacion() : null),
                medida(local != null ? local.getFrente() : null, "m"),
                local != null && local.getNumeroEstacionamientos() != null
                        ? local.getNumeroEstacionamientos().toString()
                        : "-",
                medida(local != null ? local.getCargaElectricaKw() : null, "kW"),
                local != null && local.getAptoLicenciaFuncionamiento() != null
                        ? (local.getAptoLicenciaFuncionamiento() ? "Sí" : "No")
                        : "-",
                texto(local != null ? local.getZonificacion() : null),
                local != null && local.getCuotaMantenimiento() != null
                        ? moneda(local.getCuotaMantenimiento())
                        : "-",
                moneda(local != null ? local.getPrecioReferencial() : null),
                porcentaje(captacion.getComisionPactada()),
                captacion.getUrgencia() == null ? "-" : captacion.getUrgencia() + " / 5",
                exclusividad(captacion.getExclusividad()),
                vigencia(captacion.getFechaInicioVigencia(), captacion.getFechaFinVigencia()),
                diasRestantes(captacion.getFechaFinVigencia()),
                texto(propietario != null ? propietario.getNombresORazonSocial() : null, "No registrado"),
                propietarioTipo(propietario),
                texto(propietario != null ? propietario.getNumeroDocumento() : null),
                texto(propietario != null ? propietario.getTelefono() : null),
                texto(propietario != null ? propietario.getCorreo() : null),
                agente(captacion.getAgenteResponsable()),
                descripcionPropiedad(local, direccion, distrito, area, rubro),
                preciosHistoricos(precios),
                cantidadFotos <= 0 ? "Sin fotos registradas" : cantidadFotos + " fotos registradas",
                fechaGeneracion());
    }

    private static String propietario(LocalComercial local) {
        Propietario propietario = local != null ? local.getPropietario() : null;
        return texto(propietario != null ? propietario.getNombresORazonSocial() : null, "No registrado");
    }

    private static String agente(AgenteInmobiliario agente) {
        return texto(agente != null && agente.getPersona() != null
                ? agente.getPersona().getNombresORazonSocial()
                : null, "No registrado");
    }

    private static String texto(String valor) {
        return texto(valor, "-");
    }

    private static String texto(String valor, String fallback) {
        return valor == null || valor.isBlank() ? fallback : valor.trim();
    }

    private static String descripcion(CodigoEnum valor) {
        return valor == null ? "-" : texto(valor.getDescripcion(), valor.getCodigo());
    }

    private static String propietarioTipo(Propietario propietario) {
        if (propietario == null) {
            return "-";
        }
        String tipoPersona = descripcion(propietario.getTipoPersona());
        String tipoDocumento = descripcion(propietario.getTipoDocumento());
        if ("-".equals(tipoPersona) && "-".equals(tipoDocumento)) {
            return "-";
        }
        if ("-".equals(tipoPersona)) {
            return tipoDocumento;
        }
        if ("-".equals(tipoDocumento)) {
            return tipoPersona;
        }
        return tipoPersona + " - " + tipoDocumento;
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "-" : FECHA.format(fecha);
    }

    private static String periodo(LocalDate inicio, LocalDate fin) {
        if (inicio == null && fin == null) {
            return "Sin período";
        }
        return fecha(inicio) + " - " + fecha(fin);
    }

    private static String vigencia(LocalDate inicio, LocalDate fin) {
        if (inicio == null && fin == null) {
            return "Sin vigencia definida";
        }
        return fecha(inicio) + " - " + fecha(fin);
    }

    private static String exclusividad(Boolean valor) {
        if (valor == null) {
            return "No registrado";
        }
        return valor ? "Encargo exclusivo" : "No exclusivo";
    }

    private static String area(BigDecimal valor) {
        return valor == null ? "-" : DECIMAL.format(valor) + " m²";
    }

    private static String porcentaje(BigDecimal valor) {
        return valor == null ? "-" : DECIMAL.format(valor) + "%";
    }

    private static String moneda(BigDecimal valor) {
        return valor == null ? "-" : "USD " + DECIMAL.format(valor);
    }

    private static String medida(BigDecimal valor, String unidad) {
        return valor == null ? "-" : DECIMAL.format(valor) + " " + unidad;
    }

    private static String numero(Integer valor) {
        return valor == null ? "0" : valor.toString();
    }

    private static int porcentaje(int parte, int total) {
        return total <= 0 ? 0 : Math.min(100, (int) Math.round(parte * 100.0 / total));
    }

    private static String lecturaReporte(int consultas, int visitas, String conversion) {
        if (consultas <= 0 && visitas <= 0) {
            return "No se registraron consultas ni visitas en el periodo. Conviene reforzar publicacion, precio y difusion.";
        }
        if (consultas > 0 && visitas <= 0) {
            return "Hay interes inicial, pero aun no se concreta visita. Revisar disponibilidad, precio o material publicado.";
        }
        return "El periodo genero " + consultas + " consultas y " + visitas
                + " visitas, con conversion a visita de " + conversion + ".";
    }

    private static String diasRestantes(LocalDate fin) {
        if (fin == null) {
            return "-";
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(ZONA_LIMA), fin);
        if (dias == 0) {
            return "Vence hoy";
        }
        if (dias > 0) {
            return "Vence en " + dias + " " + (dias == 1 ? "día" : "días");
        }
        long diasVencida = Math.abs(dias);
        return "Vencida hace " + diasVencida + " " + (diasVencida == 1 ? "día" : "días");
    }

    private static String descripcionPropiedad(
            LocalComercial local,
            String direccion,
            String distrito,
            String area,
            String rubro) {
        if (local != null && local.getDescripcion() != null && !local.getDescripcion().isBlank()) {
            return local.getDescripcion().trim();
        }
        return "Local comercial ubicado en " + direccion + ", " + distrito
                + ". Espacio de " + area + " ideal para el rubro " + rubro
                + ". Información generada desde el expediente comercial de ControlLocal.";
    }

    private static String preciosHistoricos(List<PrecioLocal> precios) {
        if (precios == null || precios.isEmpty()) {
            return "Sin histórico de precios registrado.";
        }
        StringBuilder sb = new StringBuilder();
        int limite = Math.min(precios.size(), 8);
        for (int i = 0; i < limite; i++) {
            PrecioLocal precio = precios.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(fecha(precio.getFecha()))
                    .append(" - ")
                    .append(descripcion(precio.getHito()))
                    .append(" - ")
                    .append(descripcion(precio.getMoneda()))
                    .append(' ')
                    .append(precio.getMonto() == null ? "-" : DECIMAL.format(precio.getMonto()));
        }
        if (precios.size() > limite) {
            sb.append('\n').append("+ ").append(precios.size() - limite).append(" registros adicionales");
        }
        return sb.toString();
    }

    private static String fechaGeneracion() {
        return FECHA_HORA.format(LocalDateTime.now(ZONA_LIMA));
    }
}
