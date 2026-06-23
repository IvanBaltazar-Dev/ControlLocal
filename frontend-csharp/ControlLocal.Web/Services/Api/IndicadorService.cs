namespace ControlLocal.Web.Services.Api;

// Espejo del IndicadoresResponse del backend Java (GET /indicadores/resumen).
// Con JsonSerializerDefaults.Web el mapeo camelCase <-> PascalCase es automatico.
public sealed record IndicadorConteoDto(string Nombre, int Valor);

public sealed record IndicadorEmbudoDto(string Etapa, int Valor, int Porcentaje);

public sealed record IndicadorDesempenoDto(string Nombre, int Captaciones, int Cierres, int Conversion);

public sealed record IndicadoresDto(
    string Ambito,
    int CaptacionesPorRevisar,
    int SolicitudesPorEvaluar,
    int CaptacionesTotales,
    int CaptacionesActivas,
    int CaptacionesPendientes,
    int CaptacionesObservadas,
    int OportunidadesActivas,
    int Interacciones,
    int Visitas,
    int Cierres,
    int AgentesActivos,
    int BrokersActivos,
    IReadOnlyList<string> MesesEtiquetas,
    IReadOnlyList<int> CierresPorMes,
    IReadOnlyList<IndicadorConteoDto> Etapas,
    IReadOnlyList<IndicadorEmbudoDto> Embudo,
    IReadOnlyList<IndicadorDesempenoDto> Desempeno)
{
    // Valor neutro para cuando el backend no responde: ningun panel se rompe.
    public static readonly IndicadoresDto Vacio = new(
        "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        Array.Empty<string>(), Array.Empty<int>(),
        Array.Empty<IndicadorConteoDto>(), Array.Empty<IndicadorEmbudoDto>(),
        Array.Empty<IndicadorDesempenoDto>());
}

public interface IIndicadorService
{
    // Ultimo resumen cargado en el circuito (null si aun no se ha pedido).
    IndicadoresDto? Cacheado { get; }

    // Trae el resumen del backend y lo cachea por circuito. Best-effort:
    // ante un fallo devuelve el valor vacio en lugar de propagar la excepcion.
    Task<IndicadoresDto> ObtenerAsync(CancellationToken ct = default);
}

public sealed class HttpIndicadorService(ApiClient api) : IIndicadorService
{
    private IndicadoresDto? _cache;

    public IndicadoresDto? Cacheado => _cache;

    public async Task<IndicadoresDto> ObtenerAsync(CancellationToken ct = default)
    {
        try
        {
            _cache = await api.GetAsync<IndicadoresDto>("indicadores/resumen", ct) ?? IndicadoresDto.Vacio;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            // El panel/menu se muestra aunque los indicadores no carguen.
            _cache ??= IndicadoresDto.Vacio;
        }
        return _cache;
    }
}
