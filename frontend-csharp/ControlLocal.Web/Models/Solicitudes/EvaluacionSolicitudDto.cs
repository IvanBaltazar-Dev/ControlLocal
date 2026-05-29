namespace ControlLocal.Web.Models.Solicitudes;

// DTO de una evaluación de solicitud de alquiler realizada por el broker.
// Alineado al backend Java (EvaluacionSolicitud: tipoEvaluacion, resultado,
// observaciones, responsableEvaluacion, fechaEvaluacion).
public class EvaluacionSolicitudDto
{
    public long Id { get; set; }

    public long SolicitudId { get; set; }

    public string TipoEvaluacion { get; set; } = string.Empty;

    public string Resultado { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    public string ResponsableEvaluacion { get; set; } = string.Empty;

    // Fecha de evaluación lista para mostrar.
    public string FechaEvaluacionTexto { get; set; } = string.Empty;
}
