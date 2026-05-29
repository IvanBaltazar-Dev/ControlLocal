namespace ControlLocal.Web.Models.Captaciones;

// Registro de historial de una reasignación de captación a otro agente.
// Alineado al backend Java (ReasignacionCaptacion: captacion, agenteAnterior,
// agenteNuevo, brokerResponsable, fechaCambio, motivo).
public class ReasignacionCaptacionDto
{
    public long IdReasignacion { get; set; }

    public long CaptacionId { get; set; }

    public string CodigoCaptacion { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public long AgenteAnteriorId { get; set; }

    public string AgenteAnteriorNombre { get; set; } = string.Empty;

    public long AgenteNuevoId { get; set; }

    public string AgenteNuevoNombre { get; set; } = string.Empty;

    public long BrokerResponsableId { get; set; }

    public string BrokerResponsableNombre { get; set; } = string.Empty;

    public DateTime? FechaCambio { get; set; }

    // Fecha del cambio lista para mostrar (ej. "20 May 2026" o "Hoy · 14:30").
    public string FechaCambioTexto { get; set; } = string.Empty;

    public string Motivo { get; set; } = string.Empty;
}
