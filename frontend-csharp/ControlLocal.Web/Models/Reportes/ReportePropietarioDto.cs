using System.Globalization;

namespace ControlLocal.Web.Models.Reportes;

// Reporte periódico al propietario (Etapa 8): vive en el expediente de la captación y registra
// periodo, consultas, visitas, objeciones y recomendaciones. Alineado a ReportePropietario.
public class ReportePropietarioDto
{
    public long Id { get; set; }

    public long CaptacionId { get; set; }

    public long AgenteId { get; set; }

    public DateTime? FechaReporte { get; set; }

    public DateTime? PeriodoInicio { get; set; }

    public DateTime? PeriodoFin { get; set; }

    public int? ConsultasReportadas { get; set; }

    public int? VisitasReportadas { get; set; }

    public string? ObjecionesFrecuentes { get; set; }

    public string? AjustesRecomendados { get; set; }

    // Código CanalContacto: L/W/E/P/R/T/O.
    public string CanalEnvio { get; set; } = "E";

    public DateTime? FechaCreacion { get; set; }

    public string FechaReporteTexto =>
        FechaReporte?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "-";

    public string PeriodoTexto =>
        PeriodoInicio is null && PeriodoFin is null
            ? "Sin periodo"
            : $"{Fecha(PeriodoInicio)} – {Fecha(PeriodoFin)}";

    private static string Fecha(DateTime? f) =>
        f?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "—";
}

// Valores derivados (no manuales) que el formulario de reporte muestra en solo lectura: se calculan
// en el backend a partir de la actividad real de la propiedad en el periodo.
public sealed record ReportePreviewDto(int Consultas, int Visitas, string Objeciones)
{
    public static readonly ReportePreviewDto Vacio = new(0, 0, "");
}
