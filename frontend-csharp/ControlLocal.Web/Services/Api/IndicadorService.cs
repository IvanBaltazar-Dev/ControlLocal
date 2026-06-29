namespace ControlLocal.Web.Services.Api;

// Espejo del IndicadoresResponse del backend Java (GET /indicadores/resumen).
// Con JsonSerializerDefaults.Web el mapeo camelCase <-> PascalCase es automatico.
public sealed record IndicadorConteoDto(string Nombre, int Valor);

public sealed record IndicadorEmbudoDto(string Etapa, int Valor, int Porcentaje);

public sealed record IndicadorDesempenoDto(string Nombre, int Captaciones, int Cierres, int Conversion);

// Indicadores operativos del seguimiento (Etapa 9): vista personal del agente.
public sealed record IndicadorOperativoDto(
    int RecontactosVencidos,
    int RecontactosAlDia,
    int DiasPromedioSinSeguimiento,
    int VisitasPendientes,
    int SolicitudesSinCierre,
    int ConversionProspeccionCaptacion)
{
    public static readonly IndicadorOperativoDto Vacio = new(0, 0, 0, 0, 0, 0);
}

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
    int PropiedadesEquipo,
    IReadOnlyList<string> MesesEtiquetas,
    IReadOnlyList<int> CierresPorMes,
    IReadOnlyList<int> ConversionPorPeriodo,
    IReadOnlyList<int> CaptacionesPorPeriodo,
    IReadOnlyList<IndicadorConteoDto> Etapas,
    IReadOnlyList<IndicadorConteoDto> CaptacionesSalud,
    IReadOnlyList<IndicadorEmbudoDto> Embudo,
    IReadOnlyList<IndicadorDesempenoDto> Desempeno,
    IndicadorOperativoDto? Operativo = null,
    // Conversion propia por cohorte (captaciones del periodo que ya cerraron). El backend ya
    // entrega el % correcto (<=100); el front lo muestra tal cual en vez de dividir contadores.
    int CierresCohorte = 0,
    int ConversionPropia = 0)
{
    // Valor neutro para cuando el backend no responde: ningun panel se rompe.
    public static readonly IndicadoresDto Vacio = new(
        "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0,
        Array.Empty<string>(), Array.Empty<int>(),
        Array.Empty<int>(), Array.Empty<int>(),
        Array.Empty<IndicadorConteoDto>(), Array.Empty<IndicadorConteoDto>(),
        Array.Empty<IndicadorEmbudoDto>(),
        Array.Empty<IndicadorDesempenoDto>(), IndicadorOperativoDto.Vacio);
}

public interface IIndicadorService
{
    // Ultimo resumen cargado en el circuito (null si aun no se ha pedido).
    IndicadoresDto? Cacheado { get; }

    // Trae el resumen del backend y lo cachea por circuito. Best-effort:
    // ante un fallo devuelve el valor vacio en lugar de propagar la excepcion.
    Task<IndicadoresDto> ObtenerAsync(CancellationToken ct = default);

    Task<IndicadoresDto> ObtenerAsync(string periodo, CancellationToken ct = default);
}

public sealed class HttpIndicadorService(ApiClient api) : IIndicadorService
{
    private IndicadoresDto? _cache;
    private string _cachePeriodo = "6m";

    // Peticiones en vuelo por periodo: si el sidebar y el dashboard piden el mismo
    // resumen casi a la vez, comparten una sola llamada en lugar de disparar dos GET.
    // Solo se comparte mientras esta en curso; al terminar, la siguiente refresca.
    private readonly Dictionary<string, Task<IndicadoresDto>> _enVuelo = new();
    private readonly object _candado = new();

    public IndicadoresDto? Cacheado => _cache;

    public Task<IndicadoresDto> ObtenerAsync(CancellationToken ct = default) =>
        ObtenerAsync("6m", ct);

    public Task<IndicadoresDto> ObtenerAsync(string periodo, CancellationToken ct = default)
    {
        var periodoSeguro = string.IsNullOrWhiteSpace(periodo) ? "6m" : periodo.Trim();
        lock (_candado)
        {
            if (_enVuelo.TryGetValue(periodoSeguro, out var enCurso) && !enCurso.IsCompleted)
            {
                return enCurso;
            }
            var tarea = CargarAsync(periodoSeguro);
            _enVuelo[periodoSeguro] = tarea;
            return tarea;
        }
    }

    // No se liga a la cancelacion de un componente: la respuesta la comparten varios
    // y el backend responde rapido. Best-effort: ante un fallo devuelve el valor vacio.
    private async Task<IndicadoresDto> CargarAsync(string periodoSeguro)
    {
        try
        {
            _cache = await api.GetAsync<IndicadoresDto>(
                $"indicadores/resumen?periodo={Uri.EscapeDataString(periodoSeguro)}") ?? IndicadoresDto.Vacio;
            _cachePeriodo = periodoSeguro;
        }
        catch
        {
            // El panel/menu se muestra aunque los indicadores no carguen.
            _cache ??= IndicadoresDto.Vacio;
        }
        finally
        {
            lock (_candado)
            {
                _enVuelo.Remove(periodoSeguro);
            }
        }
        return _cache;
    }
}
