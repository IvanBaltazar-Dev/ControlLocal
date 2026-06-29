using ControlLocal.Web.Components;
using ControlLocal.Web.Data;
using ControlLocal.Web.Services;
using ControlLocal.Web.Services.Api;
using Microsoft.AspNetCore.DataProtection;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;

QuestPDF.Settings.License = LicenseType.Community;

// [DIAGNOSTICO TEMPORAL] Captura crashes no manejados a un archivo (%TEMP%/ControlLocal-crash.log).
CrashLog.Breadcrumb("=== Arranque ControlLocal.Web ===");
AppDomain.CurrentDomain.UnhandledException += (_, e) =>
    CrashLog.Breadcrumb($"UnhandledException: {e.ExceptionObject}");
TaskScheduler.UnobservedTaskException += (_, e) =>
{
    CrashLog.Breadcrumb($"UnobservedTaskException: {e.Exception}");
    e.SetObserved();
};

var contentRoot = ResolverRaizContenido();
var builder = WebApplication.CreateBuilder(new WebApplicationOptions
{
    Args = args,
    ContentRootPath = contentRoot,
    WebRootPath = Path.Combine(contentRoot, "wwwroot"),
});

// Evita que una advertencia de arranque intente escribir en el Event Log de
// Windows y derribe la aplicacion cuando el usuario no tiene ese permiso.
builder.Logging.ClearProviders();
builder.Logging.AddConsole();
builder.Logging.AddDebug();

builder.Services.AddDataProtection()
    .PersistKeysToFileSystem(new DirectoryInfo(Path.Combine(Path.GetTempPath(), "ControlLocal-Keys")))
    .SetApplicationName("ControlLocal");

builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

// Estado de UI por circuito (rol activo + navegación).
builder.Services.AddScoped<AppState>();
builder.Services.AddScoped<ExportacionService>();

// Notificaciones in-app: almacen compartido (Singleton) + vista por usuario (Scoped).
// Espeja la entidad Alerta del backend; el AlertasRest real lo sustituira luego.
builder.Services.AddSingleton<NotificacionStore>();
builder.Services.AddScoped<INotificacionService, HttpAlertaService>();
builder.Services.AddHttpClient<ControlLocal.Web.Services.Api.LocalesApiService>();

// El almacenamiento de documentos (S3 o disco, implementación híbrida) vive en el
// backend Java (capa rest). El frontend solo envía/recibe bytes a través del API REST.

// Cliente HTTP del API REST implementado por el backend Java.
builder.Services.Configure<ApiOptions>(builder.Configuration.GetSection(ApiOptions.Seccion));
builder.Services.AddScoped<ApiSession>();
builder.Services.AddHttpClient<ApiClient>()
    // Evita reutilizar conexiones keep-alive que GlassFish ya cerro (lo que colgaba los POST).
    // Recicla el pool rapido; sin dependencias nuevas.
    .ConfigurePrimaryHttpMessageHandler(() => new SocketsHttpHandler
    {
        PooledConnectionLifetime = TimeSpan.FromSeconds(30),
        PooledConnectionIdleTimeout = TimeSpan.FromSeconds(5),
    });
builder.Services.AddScoped<IAuthService, HttpAuthService>();
builder.Services.AddScoped<HttpPerfilService>();
builder.Services.AddScoped<HttpAgenteService>();
builder.Services.AddScoped<IPropietarioService, HttpPropietarioService>();
builder.Services.AddScoped<IClienteService, HttpClienteService>();
builder.Services.AddScoped<IFichaComercialService, HttpFichaComercialService>();
builder.Services.AddScoped<ICoincidenciaCarteraService, HttpCoincidenciaCarteraService>();
builder.Services.AddScoped<IRequerimientoService, HttpRequerimientoService>();
builder.Services.AddScoped<IReportePropietarioService, HttpReportePropietarioService>();
builder.Services.AddScoped<ILocalService, HttpLocalService>();
builder.Services.AddScoped<IPrecioLocalService, HttpPrecioLocalService>();
builder.Services.AddScoped<IPublicacionService, HttpPublicacionService>();
builder.Services.AddScoped<HttpProspeccionService>();
builder.Services.AddScoped<IProspeccionService>(services =>
    services.GetRequiredService<HttpProspeccionService>());
builder.Services.AddScoped<ICaptacionService, HttpCaptacionService>();
builder.Services.AddScoped<HttpOportunidadService>();
builder.Services.AddScoped<IOportunidadService>(services =>
    services.GetRequiredService<HttpOportunidadService>());
builder.Services.AddScoped<IVisitaService, HttpVisitaService>();
builder.Services.AddScoped<HttpFotoLocalService>();
builder.Services.AddScoped<IAgenteService>(services =>
    services.GetRequiredService<HttpAgenteService>());

builder.Services.AddScoped<ISolicitudService, HttpSolicitudService>();
builder.Services.AddScoped<IDocumentoSolicitudService, HttpDocumentoSolicitudService>();
builder.Services.AddScoped<IContratoService, HttpContratoService>();
builder.Services.AddScoped<ITareaService, HttpTareaService>();

// Todas las pantallas usan ya el backend REST (no quedan mocks en uso).
builder.Services.AddScoped<IIndicadorService, HttpIndicadorService>();
builder.Services.AddScoped<IDashboardService, HttpDashboardService>();
builder.Services.AddScoped<IInteraccionService, HttpInteraccionService>();
builder.Services.AddScoped<IReasignacionCaptacionService, HttpReasignacionCaptacionService>();
builder.Services.AddScoped<IAssignmentService, HttpAssignmentService>();
builder.Services.AddScoped<IBrokerService, HttpBrokerService>();

var app = builder.Build();

// Solo desarrollo local: la página de excepción para desarrolladores ya viene
// activada por defecto. Sin HSTS ni redirección HTTPS (se corre en HTTP local).
app.UseStatusCodePagesWithReExecute("/not-found", createScopeForStatusCodePages: true);

// Cabeceras de seguridad para toda respuesta del servidor.
app.Use(async (context, next) =>
{
    var headers = context.Response.Headers;
    headers["X-Content-Type-Options"] = "nosniff";
    headers["X-Frame-Options"] = "DENY";
    headers["Referrer-Policy"] = "strict-origin-when-cross-origin";
    headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()";
    await next();
});

app.UseAntiforgery();

// La ficha de propiedad se exporta a PDF desde el circuito Blazor (FichaPropiedad.razor),
// donde la sesión (token JWT) y los datos ya están cargados. El antiguo endpoint
// /ficha/{codigo}/pdf abría una request HTTP sin sesión y por eso fallaba al exportar.

// Proxy del contenido real de un documento del expediente. El archivo vive en el
// almacén del backend (S3 o disco); este endpoint lo descarga del API REST (clave
// opaca, sin propagar el token JWT al navegador) y lo reenvía al visor en línea.
// La clave se valida aquí y de nuevo en el backend (anti path-traversal).
app.MapGet("/documento", async (string? clave, HttpContext http, IHttpClientFactory factory,
    Microsoft.Extensions.Options.IOptions<ApiOptions> apiOpciones, CancellationToken ct) =>
{
    if (string.IsNullOrWhiteSpace(clave)
        || clave.Contains("..", StringComparison.Ordinal)
        || clave.Contains('\\'))
        return Results.BadRequest("Clave de documento inválida.");

    byte[] contenido;
    try
    {
        var cliente = factory.CreateClient();
        cliente.BaseAddress = new Uri(apiOpciones.Value.BaseUrl.TrimEnd('/') + "/");
        cliente.Timeout = TimeSpan.FromSeconds(Math.Clamp(apiOpciones.Value.TimeoutSeconds, 5, 60));
        using var respuesta = await cliente.GetAsync(
            $"documentos/contenido?clave={Uri.EscapeDataString(clave)}", ct);
        if (respuesta.StatusCode == System.Net.HttpStatusCode.NotFound)
            return Results.NotFound("El documento no existe o ya no está disponible.");
        if (!respuesta.IsSuccessStatusCode)
            return Results.Problem("No se pudo obtener el documento del backend.");
        contenido = await respuesta.Content.ReadAsByteArrayAsync(ct);
    }
    catch (Exception)
    {
        return Results.Problem("No se pudo obtener el documento del backend.");
    }

    // El middleware global pone X-Frame-Options: DENY (anti-clickjacking de la app),
    // pero el visor incrusta este contenido en un <iframe> del MISMO origen. Se relaja
    // solo para esta respuesta a SAMEORIGIN para que la previsualización funcione.
    http.Response.Headers["X-Frame-Options"] = "SAMEORIGIN";
    http.Response.Headers["Content-Security-Policy"] = "frame-ancestors 'self'";

    var nombre = Path.GetFileName(clave);
    var tipo = TiposContenido.Para(nombre);
    // PDF e imágenes se muestran en línea; el resto se descarga.
    var enLinea = TiposContenido.SePuedeIncrustar(nombre);
    return Results.File(contenido, tipo, fileDownloadName: enLinea ? null : nombre,
        enableRangeProcessing: true);
});

app.MapStaticAssets();
app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();

static string ResolverRaizContenido()
{
    var candidatos = new[]
    {
        Directory.GetCurrentDirectory(),
        AppContext.BaseDirectory,
    };

    foreach (var candidato in candidatos)
    {
        var actual = new DirectoryInfo(candidato);
        while (actual is not null)
        {
            if (Directory.Exists(Path.Combine(actual.FullName, "wwwroot"))
                && File.Exists(Path.Combine(actual.FullName, "ControlLocal.Web.csproj")))
                return actual.FullName;

            actual = actual.Parent;
        }
    }

    return Directory.GetCurrentDirectory();
}
