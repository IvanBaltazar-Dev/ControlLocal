namespace ControlLocal.Web.Models.Brokers;

// Registro de la relación de supervisión broker–agente y su reasignación.
// Alineado al backend Java (BrokerAgente: broker, agente, fechaAsignacion,
// fechaFin, motivo, estado). El broker administrador autoriza el cambio: al
// reasignar se cierra la supervisión anterior y se abre la nueva.
public class BrokerAgenteDto
{
    public long Id { get; set; }

    public long AgenteId { get; set; }

    public string AgenteNombre { get; set; } = string.Empty;

    public long BrokerAnteriorId { get; set; }

    public string BrokerAnteriorNombre { get; set; } = string.Empty;

    public long BrokerNuevoId { get; set; }

    public string BrokerNuevoNombre { get; set; } = string.Empty;

    public long BrokerAdministradorId { get; set; }

    public string BrokerAdministradorNombre { get; set; } = string.Empty;

    public DateOnly? FechaAsignacion { get; set; }

    // Fecha de asignación lista para mostrar (ej. "22 May 2026").
    public string FechaAsignacionTexto { get; set; } = string.Empty;

    public string Motivo { get; set; } = string.Empty;

    // Estado de la supervisión: "Activa" (vigente) o "Cerrada" (reemplazada).
    public string Estado { get; set; } = "Activa";
}
