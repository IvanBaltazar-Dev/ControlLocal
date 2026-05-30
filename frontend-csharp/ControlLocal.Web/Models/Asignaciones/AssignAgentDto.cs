namespace ControlLocal.Web.Models.Asignaciones;

// DTO de un agente candidato a reasignación (pantalla "Reasignar agentes").
public class AssignAgentDto
{
    public string Id { get; set; } = string.Empty;

    // Dato visual: iniciales mostradas en el avatar.
    public string Iniciales { get; set; } = string.Empty;

    public string Nombre { get; set; } = string.Empty;

    public string NumeroDocumento { get; set; } = string.Empty;

    public string EstadoAdministrativo { get; set; } = string.Empty;

    public string EstadoOperativo { get; set; } = string.Empty;

    public string BrokerActual { get; set; } = string.Empty;

    // Indica si el agente puede ser seleccionado para reasignación.
    public bool Seleccionable { get; set; }

    // Motivo por el que el agente no es seleccionable (opcional).
    public string? MotivoNoDisponible { get; set; }

    // Flag para excluir al administrador global de listas seleccionables.
    public bool EsAdministrador { get; set; }
}
