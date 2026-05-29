using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Captaciones;

// Petición para reasignar una captación a un nuevo agente responsable.
// Espeja el método de negocio reasignarCaptacion(idCaptacion, idAgenteNuevo, idBroker, motivo).
public class ReasignarCaptacionRequest
{
    [Range(1, long.MaxValue, ErrorMessage = "Seleccione una captación.")]
    public long CaptacionId { get; set; }

    [Range(1, long.MaxValue, ErrorMessage = "Seleccione el agente destino.")]
    public long AgenteNuevoId { get; set; }

    [Range(1, long.MaxValue, ErrorMessage = "El broker responsable es obligatorio.")]
    public long BrokerResponsableId { get; set; }

    [Required(ErrorMessage = "Ingrese el motivo de la reasignación.")]
    public string Motivo { get; set; } = string.Empty;
}
