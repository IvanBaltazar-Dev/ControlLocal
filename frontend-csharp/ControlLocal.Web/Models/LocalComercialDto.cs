using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models;

public class LocalComercialDto
{
    public long Id { get; set; }

    public string Codigo { get; set; } = string.Empty;

    [Required(ErrorMessage = "La direccion es obligatoria.")]
    public string Direccion { get; set; } = string.Empty;

    [Required(ErrorMessage = "El distrito es obligatorio.")]
    public string Distrito { get; set; } = string.Empty;

    [Range(0.01, double.MaxValue, ErrorMessage = "El area debe ser mayor a 0.")]
    public decimal AreaM2 { get; set; }

    [Range(0.01, double.MaxValue, ErrorMessage = "El precio debe ser mayor a 0.")]
    public decimal PrecioAlquiler { get; set; }

    [Required(ErrorMessage = "Seleccione el estado.")]
    public string Estado { get; set; } = "Disponible";

    [Range(1, long.MaxValue, ErrorMessage = "Seleccione un propietario.")]
    public long PropietarioId { get; set; }

    public string NombrePropietario { get; set; } = string.Empty;
}
