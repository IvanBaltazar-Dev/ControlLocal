using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Clientes;

// DTO del cliente interesado (prospecto comercial).
public class ClienteInteresadoDto
{
    public long Id { get; set; }

    [Required(ErrorMessage = "El nombre o razón social es obligatorio.")]
    public string Nombre { get; set; } = string.Empty;

    // Descripción del tipo de persona y documento (ej. "Persona natural · DNI").
    public string TipoPersona { get; set; } = string.Empty;

    [Required(ErrorMessage = "El número de documento es obligatorio.")]
    public string NumeroDocumento { get; set; } = string.Empty;

    [Phone(ErrorMessage = "Ingrese un teléfono válido.")]
    public string Telefono { get; set; } = string.Empty;

    public string RubroInteres { get; set; } = string.Empty;

    public string InteresComercial { get; set; } = string.Empty;

    public int CaptacionesVinculadas { get; set; }

    public string Estado { get; set; } = string.Empty;
}
