using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Locales;

// DTO del local comercial.
public class LocalComercialDto
{
    public long Id { get; set; }

    public string CodigoLocal { get; set; } = string.Empty;

    [Required(ErrorMessage = "La dirección es obligatoria.")]
    public string Direccion { get; set; } = string.Empty;

    [Required(ErrorMessage = "El distrito es obligatorio.")]
    public string Distrito { get; set; } = string.Empty;

    public int AreaM2 { get; set; }

    public string Rubro { get; set; } = string.Empty;

    public decimal PrecioReferencial { get; set; }

    // Representación del precio referencial lista para mostrar en pantalla.
    public string PrecioReferencialTexto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Seleccione el estado.")]
    public string Estado { get; set; } = string.Empty;

    public long? PropietarioId { get; set; }

    public string PropietarioNombre { get; set; } = string.Empty;

    public string? TipoInmueble { get; set; }

    public string? Uso { get; set; }

    public int? Ambientes { get; set; }

    public int? AntiguedadAnios { get; set; }

    public string? ZonaUrbanizacion { get; set; }

    public decimal? GeoLat { get; set; }

    public decimal? GeoLong { get; set; }

    public string? EstadoPublicacion { get; set; }

    public string Descripcion { get; set; } = string.Empty;

    public DateTime? FechaRegistro { get; set; }
}
