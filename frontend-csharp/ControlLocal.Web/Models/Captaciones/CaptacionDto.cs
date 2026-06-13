using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Captaciones;

// DTO del expediente de captación. Base única para los servicios mock actuales
// y la futura integración con el backend Java (Captacion).
public class CaptacionDto
{
    public long Id { get; set; }

    [Required(ErrorMessage = "El código de captación es obligatorio.")]
    [StringLength(20, ErrorMessage = "El código no debe superar 20 caracteres.")]
    public string CodigoCaptacion { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string DistritoLocal { get; set; } = string.Empty;

    public int AreaM2 { get; set; }

    public string Rubro { get; set; } = string.Empty;

    public string PropietarioNombre { get; set; } = string.Empty;

    public long LocalId { get; set; }

    public long? AgenteResponsableId { get; set; }

    public string NombreAgenteResponsable { get; set; } = string.Empty;

    public DateOnly? FechaCaptacion { get; set; }

    public DateOnly? FechaInicioVigencia { get; set; }

    public DateOnly? FechaFinVigencia { get; set; }

    // Vigencia lista para mostrar (ej. "01 Abr – 01 Oct 2026" o "Borrador").
    public string VigenciaTexto { get; set; } = string.Empty;

    // Días restantes listos para mostrar (ej. "venc. en 130 días").
    public string DiasRestantesTexto { get; set; } = string.Empty;

    public decimal ComisionPactada { get; set; }

    // Comisión pactada lista para mostrar (ej. "5.0" o "—").
    public string ComisionPactadaTexto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Seleccione el estado.")]
    public string Estado { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    public string MotivoOperacion { get; set; } = string.Empty;

    public int? Urgencia { get; set; }

    public bool? Exclusividad { get; set; }

    public long? BrokerRevisorId { get; set; }

    public string NombreBrokerRevisor { get; set; } = string.Empty;

    public DateTime? FechaRevision { get; set; }

    public string ObservacionRevision { get; set; } = string.Empty;
}
