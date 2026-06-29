namespace ControlLocal.Web.Models.Oportunidades;

public class OportunidadFormRequest
{
    public long ClienteId { get; set; }

    public long CaptacionId { get; set; }

    public string? Observaciones { get; set; }

    // Publicación por la que el cliente llegó a la propiedad (opcional; 0 = sin atribuir).
    public long PublicacionOrigenId { get; set; }
}
