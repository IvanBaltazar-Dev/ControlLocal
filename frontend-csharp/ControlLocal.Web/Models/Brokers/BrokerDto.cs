using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Brokers;

// DTO del broker. Base única para los servicios mock actuales y la futura
// integración con el backend Java (Broker / UsuarioInterno).
public class BrokerDto
{
    public long Id { get; set; }

    public string CodigoBroker { get; set; } = string.Empty;

    // Dato visual: iniciales mostradas en el avatar.
    public string Iniciales { get; set; } = string.Empty;

    [Required(ErrorMessage = "El nombre es obligatorio.")]
    public string Nombre { get; set; } = string.Empty;

    public string TipoPersona { get; set; } = "Persona natural";

    [EmailAddress(ErrorMessage = "Ingrese un correo válido.")]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "Seleccione el tipo de documento.")]
    public string TipoDocumento { get; set; } = string.Empty;

    [Required(ErrorMessage = "El número de documento es obligatorio.")]
    public string NumeroDocumento { get; set; } = string.Empty;

    [Phone(ErrorMessage = "Ingrese un teléfono válido.")]
    public string Telefono { get; set; } = string.Empty;

    public string Usuario { get; set; } = string.Empty;

    public string ContrasenaTemporal { get; set; } = string.Empty;

    public string Zona { get; set; } = string.Empty;

    public string TipoBroker { get; set; } = string.Empty;

    public DateOnly? FechaDesignacion { get; set; }

    // Representación de la fecha de designación lista para mostrar en pantalla.
    public string FechaDesignacionTexto { get; set; } = string.Empty;

    public int AgentesACargo { get; set; }

    public int CaptacionesActivas { get; set; }

    public string EstadoAdministrativo { get; set; } = string.Empty;

    public bool EsAdministrador { get; set; }
}
