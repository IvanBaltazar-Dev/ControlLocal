namespace ControlLocal.Web.Models.Captaciones;

// DTO de la bandeja de captaciones por revisar (vista del broker supervisor).
public class BandejaCaptacionDto
{
    public long Id { get; set; }

    public string CodigoCaptacion { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string DistritoLocal { get; set; } = string.Empty;

    public int AreaM2 { get; set; }

    public string Rubro { get; set; } = string.Empty;

    public string PropietarioNombre { get; set; } = string.Empty;

    public string NombreAgenteResponsable { get; set; } = string.Empty;

    // Fecha y hora de envío listas para mostrar (ej. "22 May 14:08").
    public string FechaEnvioTexto { get; set; } = string.Empty;

    // Antigüedad lista para mostrar (ej. "hace 2d").
    public string AntiguedadTexto { get; set; } = string.Empty;

    public string ComisionPactadaTexto { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;
}
