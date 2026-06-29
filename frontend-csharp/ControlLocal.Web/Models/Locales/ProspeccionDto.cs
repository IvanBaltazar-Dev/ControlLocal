namespace ControlLocal.Web.Models.Locales;

// DTO de una prospección (pre-captación). Alineado al backend Java (Prospeccion:
// estado [EstadoProspeccion], resultadoPropuesta, fechas de hitos, fechaRecontacto,
// local, agente, captacion). Espejo, del lado de la oferta, de la oportunidad.
public class ProspeccionDto
{
    public long Id { get; set; }

    public string CodigoProspeccion { get; set; } = string.Empty;

    public long LocalId { get; set; }

    public string LocalCodigo { get; set; } = string.Empty;

    public string Direccion { get; set; } = string.Empty;

    public string Distrito { get; set; } = string.Empty;

    public int AreaM2 { get; set; }

    public string Rubro { get; set; } = string.Empty;

    public string PrecioReferencialTexto { get; set; } = string.Empty;

    public string PropietarioNombre { get; set; } = string.Empty;

    // Agente responsable: id para filtrar por agente y nombre para mostrar.
    public long AgenteId { get; set; }

    public string NombreAgente { get; set; } = string.Empty;

    // Código EstadoProspeccion: P/C/R/E/S/T/D.
    public string Estado { get; set; } = "P";

    // Disponibilidad real del local (EstadoLocalComercial: D/N/I). "N" = alquilado/no disponible.
    public string? Disponibilidad { get; set; }

    // Código ResultadoPropuesta: P/A/R/S (null hasta entregar la propuesta).
    public string? ResultadoPropuesta { get; set; }

    public string? FechaContactoTexto { get; set; }
    public string? FechaReunionTexto { get; set; }
    public string? FechaPropuestaTexto { get; set; }

    // Ultima accion de seguimiento; desde el dia 8 sin nueva accion vence el recontacto.
    public DateOnly? FechaRecontacto { get; set; }
    public string? FechaRecontactoTexto { get; set; }

    public string? Observaciones { get; set; }

    // Código de la captación creada al captar (cuando Estado == "T").
    public long CaptacionId { get; set; }

    public string? CaptacionCodigo { get; set; }
}
