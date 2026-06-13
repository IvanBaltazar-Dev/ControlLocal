using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Visitas;

public class VisitaFormRequest
{
    [Required(ErrorMessage = "Debes seleccionar una oportunidad comercial.")]
    public long OportunidadId { get; set; }

    [Required(ErrorMessage = "Debes indicar la fecha de la visita.")]
    public string FechaTexto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Debes indicar la hora de la visita.")]
    public string HoraTexto { get; set; } = string.Empty;

    public string? Observaciones { get; set; }
}
