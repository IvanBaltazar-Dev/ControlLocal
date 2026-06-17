using System.Text.RegularExpressions;
using ControlLocal.Web.Components;
using ControlLocal.Web.Data;
using ControlLocal.Web.Services;
using ControlLocal.Web.Services.Api;
using Microsoft.AspNetCore.DataProtection;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;

QuestPDF.Settings.License = LicenseType.Community;

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
// Estado de solicitudes compartido en sesion (Singleton) para que las transiciones
// del flujo (reenviar a evaluacion / evaluacion del broker) persistan de verdad.
builder.Services.AddSingleton<SolicitudStore>();

// Almacenamiento de documentos del expediente: disco local por defecto;
// AlmacenDocumentos:Proveedor=S3 apunta el mismo contrato al bucket de objetos.
builder.Services.Configure<OpcionesAlmacenDocumentos>(
    builder.Configuration.GetSection(OpcionesAlmacenDocumentos.Seccion));
builder.Services.AddScoped<AlmacenLocalDocumentos>();
builder.Services.AddScoped<AlmacenS3Documentos>();
builder.Services.AddScoped<IDocumentoStorage>(servicios =>
{
    var configuracion = servicios.GetRequiredService<
        Microsoft.Extensions.Options.IOptions<OpcionesAlmacenDocumentos>>().Value;
    return configuracion.Proveedor.Equals("S3", StringComparison.OrdinalIgnoreCase)
        ? servicios.GetRequiredService<AlmacenS3Documentos>()
        : servicios.GetRequiredService<AlmacenLocalDocumentos>();
});

// Cliente HTTP del API REST implementado por el backend Java.
builder.Services.Configure<ApiOptions>(builder.Configuration.GetSection(ApiOptions.Seccion));
builder.Services.AddScoped<ApiSession>();
builder.Services.AddHttpClient<ApiClient>();
builder.Services.AddScoped<IAuthService, HttpAuthService>();
builder.Services.AddScoped<HttpAgenteService>();
builder.Services.AddScoped<IPropietarioService, HttpPropietarioService>();
builder.Services.AddScoped<IClienteService, HttpClienteService>();
builder.Services.AddScoped<ILocalService, HttpLocalService>();
builder.Services.AddScoped<HttpProspeccionService>();
builder.Services.AddScoped<IProspeccionService>(services =>
    services.GetRequiredService<HttpProspeccionService>());
builder.Services.AddScoped<ICaptacionService, HttpCaptacionService>();
builder.Services.AddScoped<HttpOportunidadService>();
builder.Services.AddScoped<IOportunidadService>(services =>
    services.GetRequiredService<HttpOportunidadService>());
builder.Services.AddScoped<IVisitaService, HttpVisitaService>();
builder.Services.AddScoped<IAgenteService>(services =>
    services.GetRequiredService<HttpAgenteService>());

// Servicios locales de pantallas que aun no tienen un endpoint REST equivalente.
builder.Services.AddScoped<IBrokerService, MockBrokerService>();
builder.Services.AddScoped<ISolicitudService, HttpSolicitudService>();
builder.Services.AddScoped<IInteraccionService, MockInteraccionService>();
builder.Services.AddScoped<IAssignmentService, MockAssignmentService>();
builder.Services.AddScoped<IReasignacionCaptacionService, MockReasignacionCaptacionService>();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
    app.UseHsts();
    app.UseHttpsRedirection();
}
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

// Exportación de la ficha de propiedad a PDF (generado en servidor con QuestPDF).
// El código se valida contra el formato esperado antes de procesar.
app.MapGet("/ficha/{codigo}/pdf", (string codigo,
    ICaptacionService capSvc, ILocalService localSvc, IPropietarioService propSvc) =>
{
    if (!Regex.IsMatch(codigo, "^[A-Za-z0-9-]{1,20}$"))
        return Results.BadRequest("Código de ficha inválido.");

    var model = FichaBuilder.Build(codigo, capSvc, localSvc, propSvc);
    var bytes = new FichaPropiedadDocument(model).GeneratePdf();
    return Results.File(bytes, "application/pdf", $"Ficha_{model.Codigo}.pdf");
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
