namespace ControlLocal.Web.Models.Asignaciones;

// DTO de un broker destino candidato en la reasignación de agentes.
public class AssignBrokerDto
{
    public string Id { get; set; } = string.Empty;

    // Dato visual: iniciales mostradas en el avatar.
    public string Iniciales { get; set; } = string.Empty;

    public string Nombre { get; set; } = string.Empty;

    public string Zona { get; set; } = string.Empty;

    public string EstadoAdministrativo { get; set; } = string.Empty;

    // Tipo de broker: "supervisor" o "admin".
    public string TipoBroker { get; set; } = string.Empty;

    public int AgentesACargo { get; set; }

    // Indica si el broker puede ser seleccionado como destino.
    public bool Seleccionable { get; set; }

    // Motivo por el que el broker no es seleccionable (opcional).
    public string? MotivoNoDisponible { get; set; }
    public bool EsAdministrador { get; set; }
}
