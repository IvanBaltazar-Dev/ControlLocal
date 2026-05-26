using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models;

public class CaptacionDto
{
    public long Id { get; set; }

    [Required(ErrorMessage = "El codigo de captacion es obligatorio.")]
    [StringLength(20, ErrorMessage = "El codigo no debe superar 20 caracteres.")]
    public string CodigoCaptacion { get; set; } = string.Empty;

    [Range(1, long.MaxValue, ErrorMessage = "Seleccione un local comercial.")]
    public long LocalComercialId { get; set; }

    public string DireccionLocal { get; set; } = string.Empty;

    [Range(1, long.MaxValue, ErrorMessage = "Seleccione un agente responsable.")]
    public long AgenteResponsableId { get; set; }

    public string NombreAgenteResponsable { get; set; } = string.Empty;

    [Required(ErrorMessage = "La fecha de captacion es obligatoria.")]
    public DateOnly FechaCaptacion { get; set; } = DateOnly.FromDateTime(DateTime.Today);

    public DateOnly? FechaInicioVigencia { get; set; } = DateOnly.FromDateTime(DateTime.Today);

    public DateOnly? FechaFinVigencia { get; set; } = DateOnly.FromDateTime(DateTime.Today.AddMonths(6));

    [Range(typeof(decimal), "0", "99999999", ErrorMessage = "La comision pactada no puede ser negativa.")]
    public decimal ComisionPactada { get; set; }

    [Required(ErrorMessage = "Seleccione el estado.")]
    public string Estado { get; set; } = "Pendiente de revision";

    public string Observaciones { get; set; } = string.Empty;

    public long? BrokerRevisorId { get; set; }

    public string NombreBrokerRevisor { get; set; } = string.Empty;

    public DateTime? FechaRevision { get; set; }

    public string ObservacionRevision { get; set; } = string.Empty;

    public DateTime? FechaCreacion { get; set; }

    public DateTime? FechaActualizacion { get; set; }
}
