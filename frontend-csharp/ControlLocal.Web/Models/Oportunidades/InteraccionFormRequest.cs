using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Oportunidades;

public class InteraccionFormRequest
{
    [Required(ErrorMessage = "Debes seleccionar una oportunidad comercial.")]
    public long OportunidadId { get; set; }

    [Required(ErrorMessage = "Debes seleccionar un canal de contacto.")]
    public string CanalContacto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Debes seleccionar un resultado.")]
    public string Resultado { get; set; } = string.Empty;

    public string? Observaciones { get; set; }
}
