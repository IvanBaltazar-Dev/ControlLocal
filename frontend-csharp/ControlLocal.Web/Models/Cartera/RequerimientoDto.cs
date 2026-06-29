namespace ControlLocal.Web.Models.Cartera;

// Perfil de busqueda del cliente (Etapa 8). Se gestiona desde la ficha del cliente y, al
// guardarse, queda vinculado al cliente y entra a la recomendacion de cartera.
public sealed class RequerimientoDto
{
    public long Id { get; set; }
    public long ClienteId { get; set; }
    public string Rubro { get; set; } = "";
    public string? TipoInmueble { get; set; }
    public decimal? RentaMin { get; set; }
    public decimal? RentaMax { get; set; }
    public string Moneda { get; set; } = "PEN";
    public decimal? MetrajeMin { get; set; }
    public decimal? MetrajeMax { get; set; }
    public decimal? FrenteMinimo { get; set; }
    public string Estado { get; set; } = "ACTIVO";
    public string? Observaciones { get; set; }
    public List<string> Distritos { get; set; } = new();
    public DateTime? FechaActualizacion { get; set; }
}
