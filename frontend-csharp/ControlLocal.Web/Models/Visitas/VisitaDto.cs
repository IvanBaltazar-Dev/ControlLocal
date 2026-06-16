namespace ControlLocal.Web.Models.Visitas;

// DTO de una visita a un local comercial.
// Alineado al backend Java (Visita: fechaVisita, horaVisita, observaciones,
// estado [EstadoVisita], resultado [ResultadoInteraccion], oportunidad, cliente,
// captacion, agente). Estado y Resultado viajan como código (1 carácter) igual
// que en el resto de DTOs.
public class VisitaDto
{
    public long Id { get; set; }

    public long OportunidadId { get; set; }

    // Fecha lista para mostrar (ej. "24 May 2026").
    public string FechaTexto { get; set; } = string.Empty;

    // Hora lista para mostrar (ej. "16:00").
    public string HoraTexto { get; set; } = string.Empty;

    public string CodigoCaptacion { get; set; } = string.Empty;

    public string ClienteNombre { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string DistritoLocal { get; set; } = string.Empty;

    public string NombreAgente { get; set; } = string.Empty;

    // EstadoVisita: P=Programada, G=Reprogramada, C=Cancelada,
    // N=No realizada, R=Realizada.
    public string Estado { get; set; } = "P";

    // Código ResultadoInteraccion: I/N/S/D/P. Null mientras la visita no se realiza.
    public string? Resultado { get; set; }

    // Notas de la visita / motivo de cancelación.
    public string? Observaciones { get; set; }

    public int? NivelInteres { get; set; }

    public string? ObjecionPrincipal { get; set; }

    public string? OpinionPrecio { get; set; }

    public string? ProximaAccion { get; set; }
}
