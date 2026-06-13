using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Solicitudes;

// DTO de la solicitud de alquiler.
public class SolicitudAlquilerDto
{
    public long Id { get; set; }

    [Required(ErrorMessage = "El código es obligatorio.")]
    public string CodigoSolicitud { get; set; } = string.Empty;

    public string CodigoOperacion { get; set; } = string.Empty;

    public long OportunidadId { get; set; }

    public string ClienteNombre { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string DistritoLocal { get; set; } = string.Empty;

    public decimal MontoMensual { get; set; }

    // Monto mensual listo para mostrar (ej. "1 850").
    public string MontoMensualTexto { get; set; } = string.Empty;

    public int PlazoMeses { get; set; }

    public string PlazoTentativo { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    // Documentos completados listos para mostrar (ej. "6/6").
    public string DocumentosTexto { get; set; } = string.Empty;

    public int PorcentajeDocumentos { get; set; }

    // Fecha de registro lista para mostrar (ej. "23 May").
    public string FechaRegistroTexto { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;
}
