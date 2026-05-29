using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Solicitudes;

// DTO de un documento adjunto a una solicitud de alquiler.
// Alineado al backend Java (DocumentoSolicitud: tipoDocumento, nombreArchivo,
// fechaEntrega, resultadoRevision, observaciones, estado).
public class DocumentoSolicitudDto
{
    public long Id { get; set; }

    public long SolicitudId { get; set; }

    [Required(ErrorMessage = "El tipo de documento es obligatorio.")]
    public string TipoDocumento { get; set; } = string.Empty;

    public string NombreArchivo { get; set; } = string.Empty;

    // Fecha de entrega lista para mostrar.
    public string FechaEntregaTexto { get; set; } = string.Empty;

    public string ResultadoRevision { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;
}
