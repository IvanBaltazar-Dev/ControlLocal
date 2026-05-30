using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Visitas;

// DTO para capturar los datos del formulario de programación de una visita.
// Mapea al POST de creación de visita del futuro API REST.
public class VisitaFormRequest
{
    [Required(ErrorMessage = "Debes seleccionar una captación.")]
    public string CaptacionCodigo { get; set; } = string.Empty;

    [Required(ErrorMessage = "Debes seleccionar un cliente interesado.")]
    public string ClienteNombre { get; set; } = string.Empty;

    [Required(ErrorMessage = "Debes indicar la fecha de la visita.")]
    public string FechaTexto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Debes indicar la hora de la visita.")]
    public string HoraTexto { get; set; } = string.Empty;

    public string? Observaciones { get; set; }
}
