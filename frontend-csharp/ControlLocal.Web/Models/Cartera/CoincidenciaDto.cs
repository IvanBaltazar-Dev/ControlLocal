namespace ControlLocal.Web.Models.Cartera;

// Una alternativa recomendada por el motor de cartera (Etapa 8). Puede ser una
// propiedad (captacion activa) compatible con un cliente, o un cliente compatible
// con una propiedad/captacion. Incluye el detalle de por que coincide y que no.
public sealed class CoincidenciaDto
{
    public string Tipo { get; init; } = "";            // "PROPIEDAD" | "CLIENTE"
    public long? Id { get; init; }
    public string Codigo { get; init; } = "";
    public string Titulo { get; init; } = "";
    public string Subtitulo { get; init; } = "";
    public string Distrito { get; init; } = "";
    public string Renta { get; init; } = "";
    public string Area { get; init; } = "";
    public string Frente { get; init; } = "";
    public int Puntaje { get; init; }
    public IReadOnlyList<string> Cumple { get; init; } = [];
    public IReadOnlyList<string> NoCumple { get; init; } = [];
    public long? ClienteId { get; init; }
    public long? CaptacionId { get; init; }
    // Ruta a oportunidad-form prellenada. Vacia cuando no es accionable (p.ej. prospeccion sin captacion).
    public string ProponerRuta { get; init; } = "";
}

public sealed class CoincidenciasDto
{
    public string Origen { get; init; } = "";
    public int Total { get; init; }
    public int Page { get; init; } = 1;
    public int PageSize { get; init; } = 6;
    public IReadOnlyList<CoincidenciaDto> Items { get; init; } = [];
}
