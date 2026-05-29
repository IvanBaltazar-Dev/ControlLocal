namespace ControlLocal.Web.Models.Visitas;

// DTO de una visita a un local comercial.
public class VisitaDto
{
    public long Id { get; set; }

    // Fecha lista para mostrar (ej. "24 May 2026").
    public string FechaTexto { get; set; } = string.Empty;

    // Hora lista para mostrar (ej. "16:00").
    public string HoraTexto { get; set; } = string.Empty;

    public string CodigoCaptacion { get; set; } = string.Empty;

    public string ClienteNombre { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string DistritoLocal { get; set; } = string.Empty;

    public string NombreAgente { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;

    // Resultado de la visita (opcional, solo para visitas ya realizadas).
    public string? ResultadoTexto { get; set; }
}
