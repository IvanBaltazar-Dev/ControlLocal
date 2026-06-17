using System.ComponentModel.DataAnnotations;

namespace ControlLocal.Web.Models.Agentes;

// DTO del agente inmobiliario. Base única para los servicios mock actuales y
// la futura integración con el backend Java (AgenteInmobiliario / UsuarioInterno).
public class AgenteDto
{
    public long Id { get; set; }

    public string CodigoAgente { get; set; } = string.Empty;

    // Dato visual: iniciales mostradas en el avatar.
    public string Iniciales { get; set; } = string.Empty;

    // Dato visual: color de fondo del avatar.
    public string Color { get; set; } = string.Empty;

    [Required(ErrorMessage = "El nombre es obligatorio.")]
    public string Nombre { get; set; } = string.Empty;

    public string TipoPersona { get; set; } = "Persona natural";

    public string TipoDocumento { get; set; } = "D";

    public string Usuario { get; set; } = string.Empty;

    public string ContrasenaTemporal { get; set; } = string.Empty;

    [EmailAddress(ErrorMessage = "Ingrese un correo válido.")]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "El número de documento es obligatorio.")]
    public string NumeroDocumento { get; set; } = string.Empty;

    public string Telefono { get; set; } = string.Empty;

    public string Zona { get; set; } = string.Empty;

    public DateOnly? FechaIngreso { get; set; }

    // Representación de la fecha de ingreso lista para mostrar en pantalla.
    public string FechaIngresoTexto { get; set; } = string.Empty;

    public int CaptacionesActivas { get; set; }

    public int OportunidadesActivas { get; set; }

    public string EstadoAdministrativo { get; set; } = string.Empty;

    public string EstadoOperativo { get; set; } = string.Empty;

    public long? BrokerId { get; set; }

    public string BrokerNombre { get; set; } = string.Empty;
}
