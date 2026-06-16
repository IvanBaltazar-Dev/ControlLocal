namespace ControlLocal.Web.Models.Oportunidades;

public class OportunidadFormRequest
{
    public long ClienteId { get; set; }

    public long CaptacionId { get; set; }

    public string? Observaciones { get; set; }
}
