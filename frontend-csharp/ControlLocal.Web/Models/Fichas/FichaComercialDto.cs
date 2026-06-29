namespace ControlLocal.Web.Models.Fichas;

public class FichaComercialDto
{
    public FichaPersonaDto Persona { get; set; } = new();

    public bool RequerimientoActivo { get; set; }

    public string CtaRuta { get; set; } = string.Empty;

    public Dictionary<string, FichaSectionDto> Sections { get; set; } = new(StringComparer.OrdinalIgnoreCase);
}

public class FichaPersonaDto
{
    public long Id { get; set; }

    public string Tipo { get; set; } = string.Empty;

    public string Nombre { get; set; } = string.Empty;

    public string TipoPersona { get; set; } = string.Empty;

    public string TipoDocumento { get; set; } = string.Empty;

    public string NumeroDocumento { get; set; } = string.Empty;

    public string Telefono { get; set; } = string.Empty;

    public string Correo { get; set; } = string.Empty;

    public string RubroInteres { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;

    public DateTime? FechaCreacion { get; set; }
}

public class FichaSectionDto
{
    public string Section { get; set; } = string.Empty;

    public long TotalRecords { get; set; }

    public int Page { get; set; } = 1;

    public int PageSize { get; set; } = 8;

    public IReadOnlyList<FichaRowDto> Items { get; set; } = [];
}

public class FichaRowDto
{
    public string Id { get; set; } = string.Empty;

    public string Codigo { get; set; } = string.Empty;

    public string Proceso { get; set; } = string.Empty;

    public string Titulo { get; set; } = string.Empty;

    public string Subtitulo { get; set; } = string.Empty;

    public string Local { get; set; } = string.Empty;

    public string Distrito { get; set; } = string.Empty;

    public string Cliente { get; set; } = string.Empty;

    public long? ClienteId { get; set; }

    public string Propietario { get; set; } = string.Empty;

    public long? PropietarioId { get; set; }

    public string Agente { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;

    public string Fecha { get; set; } = string.Empty;

    public string Ruta { get; set; } = string.Empty;

    public string Icono { get; set; } = "activity";

    public string Tono { get; set; } = "gray";

    public DateTime? FechaOrden { get; set; }
}
