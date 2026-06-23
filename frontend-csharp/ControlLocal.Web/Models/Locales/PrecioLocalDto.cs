namespace ControlLocal.Web.Models.Locales;

// Un hito del historico de precios de un local (tabla precio_local del backend).
public class PrecioLocalDto
{
    public long Id { get; set; }
    public long IdLocal { get; set; }
    public string Hito { get; set; } = "";        // codigo E/R/U/P/O/A/C
    public string HitoTexto { get; set; } = "";    // etiqueta legible
    public string Moneda { get; set; } = "PEN";
    public decimal Monto { get; set; }
    public string MontoTexto { get; set; } = "";
    public DateOnly? Fecha { get; set; }
    public string FechaTexto { get; set; } = "";
}

// Una publicacion del local (tabla publicacion del backend).
public class PublicacionDto
{
    public long Id { get; set; }
    public string Canal { get; set; } = "";
    public string CanalTexto { get; set; } = "";
    public string TituloAnuncio { get; set; } = "";
    public decimal RentaPublicada { get; set; }
    public string RentaTexto { get; set; } = "";
    public string Moneda { get; set; } = "PEN";
    public string Estado { get; set; } = "";       // codigo B/P/S/C
    public string EstadoTexto { get; set; } = "";
    public DateTime? FechaPublicacion { get; set; }
    public string FechaPublicacionTexto { get; set; } = "";
    public DateTime? FechaBaja { get; set; }
    public string? UrlPublicacion { get; set; }
    public string CodigoOrigen { get; set; } = "";
}
