using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Propietarios;

// DTO del propietario de locales comerciales.
public class PropietarioDto
{
    public long Id { get; set; }

    [Required(ErrorMessage = "El nombre o razón social es obligatorio.")]
    public string Nombre { get; set; } = string.Empty;

    // Descripción del tipo de persona y documento (ej. "Persona jurídica · RUC").
    public string TipoPersona { get; set; } = string.Empty;

    [Required(ErrorMessage = "El número de documento es obligatorio.")]
    public string NumeroDocumento { get; set; } = string.Empty;

    [Phone(ErrorMessage = "Ingrese un teléfono válido.")]
    public string Telefono { get; set; } = string.Empty;

    [EmailAddress(ErrorMessage = "Ingrese un correo válido.")]
    public string Correo { get; set; } = string.Empty;

    public int CantidadLocales { get; set; }

    public string Estado { get; set; } = string.Empty;
}
