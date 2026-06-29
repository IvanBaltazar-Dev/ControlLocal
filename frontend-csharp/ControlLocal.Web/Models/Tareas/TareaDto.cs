namespace ControlLocal.Web.Models.Tareas;

// DTO de una tarea de la bandeja "Acciones Pendientes" (Etapa 5). Alineado a
// Tarea del backend: tipo, entidad (para resolver navegando), estado y prioridad.
public class TareaDto
{
    public long Id { get; set; }

    // Codigo TipoTarea: RECONTACTO, SEGUIMIENTO, etc.
    public string Tipo { get; set; } = string.Empty;

    // Codigo TipoEntidad: PROSPECCION, SOLICITUD_ALQUILER, CONTRATO_ALQUILER, ...
    public string EntidadTipo { get; set; } = string.Empty;

    public long EntidadId { get; set; }

    // Código de la entidad (para mostrar) y ruta exacta de "Resolver", precalculados por el backend.
    public string EntidadCodigo { get; set; } = string.Empty;

    public string RutaResolver { get; set; } = string.Empty;

    public string Descripcion { get; set; } = string.Empty;

    // Codigo EstadoTarea: PENDIENTE / EN_PROCESO.
    public string Estado { get; set; } = string.Empty;

    // Codigo Prioridad: BAJA / MEDIA / ALTA.
    public string Prioridad { get; set; } = string.Empty;

    public DateTime? FechaProgramada { get; set; }

    // Etapa 9: días que la acción lleva sin atención (anclados al plazo real de la entidad de
    // origen) y fecha de vencimiento cuando la entidad impone un plazo (recontacto, visita, oferta).
    public int? DiasSinAccion { get; set; }

    public DateTime? FechaVencimiento { get; set; }
}
