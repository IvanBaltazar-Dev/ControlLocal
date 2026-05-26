using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models;

public class PropietarioDto
{
    public long Id { get; set; }

    [Required(ErrorMessage = "Seleccione el tipo de documento.")]
    public string TipoDocumento { get; set; } = "DNI";

    [Required(ErrorMessage = "El documento es obligatorio.")]
    [StringLength(11, MinimumLength = 8, ErrorMessage = "El documento debe tener entre 8 y 11 caracteres.")]
    public string NumeroDocumento { get; set; } = string.Empty;

    [Required(ErrorMessage = "El nombre es obligatorio.")]
    public string Nombres { get; set; } = string.Empty;

    public string Apellidos { get; set; } = string.Empty;

    [EmailAddress(ErrorMessage = "Ingrese un correo valido.")]
    public string Correo { get; set; } = string.Empty;

    [Phone(ErrorMessage = "Ingrese un telefono valido.")]
    public string Telefono { get; set; } = string.Empty;

    [Required(ErrorMessage = "Seleccione el estado.")]
    public string Estado { get; set; } = "Activo";

    public string NombreCompleto => string.IsNullOrWhiteSpace(Apellidos)
        ? Nombres
        : $"{Nombres} {Apellidos}";
}
