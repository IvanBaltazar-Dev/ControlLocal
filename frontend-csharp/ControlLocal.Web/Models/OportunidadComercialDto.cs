namespace ControlLocal.Web.Models;

public class OportunidadComercialDto
{
    public long Id { get; set; }
    public string Cliente { get; set; } = string.Empty;
    public long LocalComercialId { get; set; }
    public string DireccionLocal { get; set; } = string.Empty;
    public string Estado { get; set; } = "Abierta";
    public DateOnly FechaRegistro { get; set; } = DateOnly.FromDateTime(DateTime.Today);
    public decimal MontoEstimado { get; set; }
}
