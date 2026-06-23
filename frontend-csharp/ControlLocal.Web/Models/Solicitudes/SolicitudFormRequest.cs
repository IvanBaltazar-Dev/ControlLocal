using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Solicitudes;

public class SolicitudFormRequest
{
    [Required(ErrorMessage = "Debes seleccionar una oportunidad comercial.")]
    public long OportunidadId { get; set; }

    [Range(typeof(decimal), "0.01", "9999999999", ErrorMessage = "El monto debe ser mayor que cero.")]
    public decimal MontoPropuesto { get; set; }

    // Condiciones del trato: el broker las evalúa y el contrato las hereda al cerrar.
    [Range(1, 600, ErrorMessage = "El plazo en meses debe ser mayor que cero.")]
    public int PlazoMeses { get; set; }

    public DateOnly? FechaInicio { get; set; }

    public string FormaPago { get; set; } = "TRANSFERENCIA";

    public int? MesesGarantia { get; set; }

    public int? MesesAdelanto { get; set; }

    public string? Observaciones { get; set; }

    public bool EnviarAEvaluacion { get; set; }
}
