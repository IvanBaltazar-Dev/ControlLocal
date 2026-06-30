using System.Net.Http.Headers;
using System.Net;
using System.Text.Json;
using Microsoft.AspNetCore.Components;
using Microsoft.Extensions.Options;

namespace ControlLocal.Web.Services.Api;

public class ApiOptions
{
    public const string Seccion = "Api";

    public string BaseUrl { get; set; } = "http://localhost:8080/controllocal/Api";
    // El backend (RDS remoto) puede tardar ~20-30s en la primera consulta tras un periodo
    // inactivo (cold start). 15s era muy ajustado y provocaba TaskCanceledException en
    // listados de interacciones y dashboards. Se sube a 45s para tolerar el calentamiento.
    public int TimeoutSeconds { get; set; } = 45;
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
    private static readonly JsonSerializerOptions JsonOpciones = new(JsonSerializerDefaults.Web);

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
        _http.Timeout = TimeSpan.FromSeconds(Math.Clamp(opciones.Value.TimeoutSeconds, 5, 120));
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
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await LeerJsonAsync<T>(respuesta.Content, ct);
    }

    public Task<PaginaApi<T>?> GetPaginaAsync<T>(string ruta, int pagina, int tamano, CancellationToken ct = default)
    {
        var separador = ruta.Contains('?') ? '&' : '?';
        return GetAsync<PaginaApi<T>>($"{ruta}{separador}pagina={pagina}&tamano={tamano}", ct);
    }

    public async Task<IReadOnlyList<T>> GetTodasPaginasAsync<T>(
        string ruta,
        int tamano = 100,
        CancellationToken ct = default)
    {
        var pagina = 1;
        var resultado = new List<T>();
        var tamanoSeguro = Math.Clamp(tamano, 1, 500);

        while (true)
        {
            var respuesta = await GetPaginaAsync<T>(ruta, pagina, tamanoSeguro, ct);
            if (respuesta?.Items is null || respuesta.Items.Count == 0)
                break;

            resultado.AddRange(respuesta.Items);
            if (resultado.Count >= respuesta.TotalRecords || respuesta.Items.Count < tamanoSeguro)
                break;

            pagina++;
        }

        return resultado;
    }

    // Descarga binaria (sin deserializar JSON): trae los bytes de una foto/documento
    // (p.ej. documentos/contenido?clave=) para incrustarlos en un PDF generado en el circuito.
    public async Task<byte[]?> GetBytesAsync(string ruta, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Get, ruta);
        solicitud.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("*/*"));
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await respuesta.Content.ReadAsByteArrayAsync(ct);
    }

    public async Task<TRespuesta?> PostAsync<TRespuesta>(string ruta, object cuerpo, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Post, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await LeerJsonAsync<TRespuesta>(respuesta.Content, ct);
    }

    // Sube un archivo como octet-stream y devuelve el JSON de respuesta. El backend
    // (capa rest) guarda el contenido en el almacen (S3 o disco) y persiste sus metadatos.
    public async Task<TRespuesta?> PostBytesAsync<TRespuesta>(
        string ruta, byte[] contenido, string contentType, CancellationToken ct = default)
    {
        ControlLocal.Web.Services.CrashLog.Breadcrumb(
            $"PostBytes inicio ruta={ruta} bytes={contenido?.Length ?? -1} ct={contentType} token={(_sesion.Token is null ? "no" : "si")}");
        using var solicitud = Solicitud(HttpMethod.Post, ruta);
        var cuerpo = new ByteArrayContent(contenido);
        cuerpo.Headers.ContentType = new MediaTypeHeaderValue(contentType);
        solicitud.Content = cuerpo;
        // Subida robusta: no esperar "100-continue" y forzar conexion nueva (sin reutilizar
        // una keep-alive que GlassFish pudo cerrar), que es lo que colgaba el POST.
        solicitud.Headers.ExpectContinue = false;
        solicitud.Headers.ConnectionClose = true;
        ControlLocal.Web.Services.CrashLog.Breadcrumb("PostBytes antes de EnviarAsync");
        using var respuesta = await EnviarAsync(solicitud, ct);
        ControlLocal.Web.Services.CrashLog.Breadcrumb($"PostBytes recibio status={(int)respuesta.StatusCode}");
        await ValidarRespuestaAsync(respuesta, ct);
        var resultado = await LeerJsonAsync<TRespuesta>(respuesta.Content, ct);
        ControlLocal.Web.Services.CrashLog.Breadcrumb("PostBytes fin OK");
        return resultado;
    }

    public async Task<TRespuesta?> PutAsync<TRespuesta>(string ruta, object cuerpo, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Put, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await LeerJsonAsync<TRespuesta>(respuesta.Content, ct);
    }

    public async Task DeleteAsync(string ruta, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Delete, ruta);
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
    }

    public async Task PatchAsync(string ruta, object cuerpo, CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Patch, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
    }

    public async Task<TRespuesta?> PatchAsync<TRespuesta>(
        string ruta,
        object cuerpo,
        CancellationToken ct = default)
    {
        using var solicitud = Solicitud(HttpMethod.Patch, ruta);
        solicitud.Content = JsonContent.Create(cuerpo);
        using var respuesta = await EnviarAsync(solicitud, ct);
        await ValidarRespuestaAsync(respuesta, ct);
        return await LeerJsonAsync<TRespuesta>(respuesta.Content, ct);
    }

    private HttpRequestMessage Solicitud(HttpMethod metodo, string ruta)
    {
        var solicitud = new HttpRequestMessage(metodo, ruta.TrimStart('/'));
        if (_sesion.Token is not null)
            solicitud.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _sesion.Token);

        // GlassFish cierra conexiones keep-alive ociosas; el HttpClient de .NET reutiliza esas
        // conexiones medio-cerradas y el POST/PUT/PATCH se quedaba colgado hasta el timeout (el
        // boton "Guardando" no persistia nunca). Igual que PostBytesAsync: no esperar
        // "100-continue" y forzar conexion nueva en cada escritura.
        if (metodo != HttpMethod.Get)
        {
            solicitud.Headers.ExpectContinue = false;
            solicitud.Headers.ConnectionClose = true;
        }
        return solicitud;
    }

    private async Task<HttpResponseMessage> EnviarAsync(HttpRequestMessage solicitud, CancellationToken ct)
    {
        var destino = solicitud.RequestUri?.IsAbsoluteUri == true
            ? solicitud.RequestUri
            : new Uri(_http.BaseAddress!, solicitud.RequestUri ?? new Uri("", UriKind.Relative));

        try
        {
            return await _http.SendAsync(solicitud, ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            ControlLocal.Web.Services.CrashLog.Breadcrumb($"EnviarAsync cancelado por ct: {solicitud.Method} {destino}");
            throw;
        }
        catch (TaskCanceledException ex)
        {
            ControlLocal.Web.Services.CrashLog.Breadcrumb($"EnviarAsync timeout: {solicitud.Method} {destino}");
            throw new InvalidOperationException(
                $"El API REST no respondio a tiempo ({destino}). Verifica que el backend siga levantado.",
                ex);
        }
        catch (HttpRequestException ex)
        {
            ControlLocal.Web.Services.CrashLog.Breadcrumb($"EnviarAsync HttpRequestException: {solicitud.Method} {destino} :: {ex.Message}");
            throw new InvalidOperationException(
                $"No se pudo conectar al API REST ({destino}). Verifica Api:BaseUrl y que GlassFish este sirviendo /controllocal/Api.",
                ex);
        }
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
                mensaje = (await LeerJsonAsync<ErrorApi>(respuesta.Content, ct))?.Error;
            }
            catch
            {
                // La respuesta puede venir vacia o no ser JSON.
            }
            var estado = $"HTTP {(int)respuesta.StatusCode} {respuesta.ReasonPhrase}".Trim();
            var destino = respuesta.RequestMessage?.RequestUri?.ToString();
            var detalle = string.IsNullOrWhiteSpace(mensaje) ? "No se pudo completar la operacion." : mensaje;
            throw new InvalidOperationException(
                string.IsNullOrWhiteSpace(destino) ? $"{estado}. {detalle}" : $"{estado}. {detalle} ({destino})");
        }
    }

    private static async Task<T?> LeerJsonAsync<T>(HttpContent contenido, CancellationToken ct)
    {
        if (contenido.Headers.ContentLength == 0)
            return default;

        var texto = await contenido.ReadAsStringAsync(ct);
        return string.IsNullOrWhiteSpace(texto)
            ? default
            : JsonSerializer.Deserialize<T>(texto, JsonOpciones);
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
            return new AuthResult(true, null, new AuthUser(sesion.Usuario, "", sesion.Nombre, iniciales, rol), sesion.Token);
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
