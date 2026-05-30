using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Oportunidades;

// DTO para capturar los datos del formulario de registro de interacción comercial.
public class InteraccionFormRequest
{
    [Required(ErrorMessage = "Debes seleccionar una captación.")]
    public long CaptacionId { get; set; }

    [Required(ErrorMessage = "Debes seleccionar un canal de contacto.")]
    public string CanalContacto { get; set; } = string.Empty; // L, W, E, P, O

    [Required(ErrorMessage = "Debes seleccionar o registrar un cliente.")]
    public long ClienteId { get; set; }

    // Nombre del cliente (si es nuevo cliente, para registro rápido)
    public string? ClienteNombre { get; set; }

    [Required(ErrorMessage = "Debes seleccionar un resultado.")]
    public string Resultado { get; set; } = string.Empty; // P, I, N, S, D

    public string? Observaciones { get; set; }
}
