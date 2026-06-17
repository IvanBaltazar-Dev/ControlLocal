namespace ControlLocal.Web.Models.Visitas;

public class VisitaResultadoRequest
{
    public string Resultado { get; set; } = string.Empty;
    public string? Observaciones { get; set; }
    public string? RazonNoContinuidad { get; set; }
    public int? NivelInteres { get; set; }
    public string? ObjecionPrincipal { get; set; }
    public string? OpinionPrecio { get; set; }
    public string? ProximaAccion { get; set; }
}
