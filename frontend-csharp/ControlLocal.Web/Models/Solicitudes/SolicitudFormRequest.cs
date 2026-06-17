using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Solicitudes;

public class SolicitudFormRequest
{
    [Required(ErrorMessage = "Debes seleccionar una oportunidad comercial.")]
    public long OportunidadId { get; set; }

    [Range(typeof(decimal), "0.01", "9999999999", ErrorMessage = "El monto debe ser mayor que cero.")]
    public decimal MontoPropuesto { get; set; }

    [Required(ErrorMessage = "El plazo tentativo es obligatorio.")]
    public string PlazoTentativo { get; set; } = string.Empty;

    public string? Observaciones { get; set; }

    public bool EnviarAEvaluacion { get; set; }
}
