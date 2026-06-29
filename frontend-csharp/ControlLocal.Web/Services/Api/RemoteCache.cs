namespace ControlLocal.Web.Services.Api;

/// <summary>
/// Caché por-circuito de una lectura remota. Unifica "la mejor decisión de llamada":
/// <list type="bullet">
/// <item>comparte la petición en vuelo entre llamadores concurrentes (dedup: un solo GET HTTP);</item>
/// <item>cachea el resultado con TTL opcional (null = vive hasta invalidar);</item>
/// <item><see cref="Invalidar"/> tras una escritura para forzar la próxima recarga.</item>
/// </list>
/// Pensado para servicios <c>Scoped</c>: un caché por circuito Blazor, sin estado compartido entre usuarios.
/// </summary>
public sealed class CacheRemoto<T>(Func<CancellationToken, Task<T>> cargar, TimeSpan? ttl = null)
{
    private readonly Func<CancellationToken, Task<T>> _cargar = cargar;
    private readonly TimeSpan? _ttl = ttl;
    private readonly object _candado = new();

    private T? _valor;
    private bool _tieneValor;
    private DateTime _generadoUtc = DateTime.MinValue;
    private Task<T>? _enVuelo;

    public Task<T> ObtenerAsync(CancellationToken ct = default)
    {
        lock (_candado)
        {
            if (_tieneValor && !Expirado())
                return Task.FromResult(_valor!);

            // Si ya hay una carga en curso, los llamadores concurrentes la comparten.
            if (_enVuelo is { IsCompleted: false } enCurso)
                return enCurso;

            var tarea = CargarYGuardarAsync(ct);
            _enVuelo = tarea;
            return tarea;
        }
    }

    /// <summary>Descarta el valor cacheado: la próxima lectura vuelve a pedir al backend.</summary>
    public void Invalidar()
    {
        lock (_candado)
        {
            _tieneValor = false;
            _valor = default;
            _enVuelo = null;
        }
    }

    /// <summary>Refleja una escritura ya conocida sin un round-trip extra (p. ej. tras crear/editar).</summary>
    public void Set(T valor)
    {
        lock (_candado)
        {
            _valor = valor;
            _tieneValor = true;
            _generadoUtc = DateTime.UtcNow;
            _enVuelo = null;
        }
    }

    private bool Expirado() => _ttl is { } ttl && DateTime.UtcNow - _generadoUtc > ttl;

    private async Task<T> CargarYGuardarAsync(CancellationToken ct)
    {
        try
        {
            var valor = await _cargar(ct);
            lock (_candado)
            {
                _valor = valor;
                _tieneValor = true;
                _generadoUtc = DateTime.UtcNow;
                _enVuelo = null;
            }
            return valor;
        }
        catch
        {
            // Un fallo no debe envenenar el caché: se limpia la marca en vuelo para permitir reintentos.
            lock (_candado) { _enVuelo = null; }
            throw;
        }
    }
}
