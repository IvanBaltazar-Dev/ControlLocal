using System.Net.Http.Headers;
using System.Net;
using Microsoft.AspNetCore.Components;
using Microsoft.Extensions.Options;

namespace ControlLocal.Web.Services.Api;

public class ApiOptions
{
    public const string Seccion = "Api";

    public string BaseUrl { get; set; } = "http://localhost:8080/controllocal/Api";
    public int TimeoutSeconds { get; set; } = 15;
}

// Respuesta paginada estándar del API REST (espeja PageResponse del backend).
public sealed record PaginaApi<T>(IReadOnlyList<T> Items, long TotalRecords, int Page, int PageSize);

public sealed record CredencialesLogin(string Usuario, string Contrasena);
public sealed record SesionApi(
    string Token,
    long ExpiraEnSegundos,
    string Rol,
    long IdUsuario,
    long IdDominio,
    string Nombre,
    string Usuario,
    DateTime ExpiraEn);
internal sealed record ErrorApi(string Error);

public class ApiSession
{
    public string? Token { get; set; }
}

// Cliente HTTP tipado hacia el API REST del backend Java. Mantiene el token de
// sesión del circuito y lo adjunta como Bearer en cada llamada.
public class ApiClient
{
    private readonly HttpClient _http;
    private readonly ApiSession _sesion;
    private readonly NavigationManager _navegacion;

    public ApiClient(
        HttpClient http,
        IOptions<ApiOptions> opciones,
        ApiSession sesion,
        NavigationManager navegacion)
    {
        _http = http;
        _sesion = sesion;
        _navegacion = navegacion;
        _http.BaseAddress = new Uri(opciones.Value.BaseUrl.TrimEnd('/') + "/");
        _http.Timeout = TimeSpan.FromSeconds(Math.Clamp(opciones.Value.TimeoutSeconds, 5, 60));
        _http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    }

    public bool TieneSesion => _sesion.Token is not null;

    public async Task<SesionApi?> LoginAsync(CredencialesLogin credenciales, CancellationToken ct = default)
    {
        using var respuesta = await _http.PostAsJsonAsync("auth/login", credenciales, ct);
        if (!respuesta.IsSuccessStatusCode) return null;

        var sesion = await respuesta.Content.ReadFromJsonAsync<SesionApi>(cancellationToken: ct);
        _sesion.Token = sesion?.Token;
        return sesion;
    }

    public void CerrarSesion() => _sesion.Token = null;

    public async Task<T?> GetAsync<T>(string ruta, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Get, ruta);
        using var respuesta = await _http.SendAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await respuesta.Content.ReadFromJsonAsync<T>(cancellationToken: ct);
    }

    public Task<PaginaApi<T>?> GetPaginaAsync<T>(string ruta, int pagina, int tamano, CancellationToken ct = default)
    {
        var separador = ruta.Contains('?') ? '&' : '?';
        return GetAsync<PaginaApi<T>>($"{ruta}{separador}pagina={pagina}&tamano={tamano}", ct);
    }

    public async Task<TRespuesta?> PostAsync<TRespuesta>(string ruta, object cuerpo, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Post, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await _http.SendAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await respuesta.Content.ReadFromJsonAsync<TRespuesta>(cancellationToken: ct);
    }

    public async Task<TRespuesta?> PutAsync<TRespuesta>(string ruta, object cuerpo, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Put, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await _http.SendAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await respuesta.Content.ReadFromJsonAsync<TRespuesta>(cancellationToken: ct);
    }

    public async Task DeleteAsync(string ruta, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Delete, ruta);
        using var respuesta = await _http.SendAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
    }

    public async Task PatchAsync(string ruta, object cuerpo, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Patch, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await _http.SendAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
    }

    public async Task<TRespuesta?> PatchAsync<TRespuesta>(
        string ruta,
        object cuerpo,
        CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Patch, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await _http.SendAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await respuesta.Content.ReadFromJsonAsync<TRespuesta>(cancellationToken: ct);
    }

    private HttpRequestMessage Solicitud(HttpMethod metodo, string ruta)
    {
        var solicitud = new HttpRequestMessage(metodo, ruta.TrimStart('/'));
        if (_sesion.Token is not null)
            solicitud.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _sesion.Token);
        return solicitud;
    }

    private async Task ValidarRespuestaAsync(HttpResponseMessage respuesta, CancellationToken ct)
    {
        if (respuesta.StatusCode == HttpStatusCode.Unauthorized)
        {
            _sesion.Token = null;
            _navegacion.NavigateTo("/login");
            throw new InvalidOperationException("La sesion expiro. Inicia sesion nuevamente.");
        }
        if (respuesta.StatusCode == HttpStatusCode.Forbidden)
        {
            _navegacion.NavigateTo("/acceso-denegado");
            throw new InvalidOperationException("No tienes permisos para realizar esta operacion.");
        }
        if (!respuesta.IsSuccessStatusCode)
        {
            string? mensaje = null;
            try
            {
                mensaje = (await respuesta.Content.ReadFromJsonAsync<ErrorApi>(cancellationToken: ct))?.Error;
            }
            catch
            {
                // La respuesta puede venir vacia o no ser JSON.
            }
            throw new InvalidOperationException(
                string.IsNullOrWhiteSpace(mensaje) ? "No se pudo completar la operacion." : mensaje);
        }
    }
}

// Autenticacion contra el backend Java mediante POST /Api/auth/login.
public class HttpAuthService(ApiClient api) : IAuthService
{
    public async Task<AuthResult> LoginAsync(string usuarioOEmail, string password, CancellationToken ct = default)
    {
        var usuario = (usuarioOEmail ?? "").Trim();
        if (string.IsNullOrWhiteSpace(usuario) || string.IsNullOrWhiteSpace(password))
            return new AuthResult(false, "Ingresa tu usuario y tu contraseña.");

        try
        {
            var sesion = await api.LoginAsync(new CredencialesLogin(usuario, password), ct);
            if (sesion is null)
                return new AuthResult(false, "Usuario o contraseña incorrectos. Verifica tus datos.");

            var rol = sesion.Rol switch
            {
                "ADMIN" => Roles.Admin,
                "BROKER" or "B" => Roles.Broker,
                "AGENTE" or "A" => Roles.Agente,
                _ => sesion.Rol,
            };
            var iniciales = string.Concat(sesion.Nombre
                .Split(' ', StringSplitOptions.RemoveEmptyEntries)
                .Take(2)
                .Select(w => char.ToUpper(w[0])));
            return new AuthResult(true, null, new AuthUser(sesion.Usuario, "", sesion.Nombre, iniciales, rol));
        }
        catch (HttpRequestException)
        {
            return new AuthResult(false, "No se pudo contactar al servidor. Intenta nuevamente.");
        }
        catch (TaskCanceledException)
        {
            return new AuthResult(false, "El servidor tardó demasiado en responder. Intenta nuevamente.");
        }
    }
}
