using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Oportunidades;

public class InteraccionFormRequest
{
    public string Contexto { get; set; } = "OPORTUNIDAD";

    public long OportunidadId { get; set; }

    public long ProspeccionId { get; set; }

    public long CaptacionId { get; set; }

    public long ClienteId { get; set; }

    [Required(ErrorMessage = "Debes seleccionar un canal de contacto.")]
    public string CanalContacto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Debes seleccionar un resultado.")]
    public string Resultado { get; set; } = string.Empty;

    public string? Observaciones { get; set; }

    public string? TranscripcionNota { get; set; }
}
