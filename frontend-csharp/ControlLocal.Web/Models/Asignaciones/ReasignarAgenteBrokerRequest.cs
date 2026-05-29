using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Asignaciones;

// Petición para reasignar un agente a otro broker supervisor.
// El broker administrador autoriza la operación.
public class ReasignarAgenteBrokerRequest
{
    [Range(1, long.MaxValue, ErrorMessage = "Seleccione el agente.")]
    public long AgenteId { get; set; }

    [Range(1, long.MaxValue, ErrorMessage = "Seleccione el broker destino.")]
    public long BrokerDestinoId { get; set; }

    [Range(1, long.MaxValue, ErrorMessage = "El broker administrador es obligatorio.")]
    public long BrokerAdministradorId { get; set; }

    [Required(ErrorMessage = "Ingrese el motivo de la reasignación.")]
    public string Motivo { get; set; } = string.Empty;
}
