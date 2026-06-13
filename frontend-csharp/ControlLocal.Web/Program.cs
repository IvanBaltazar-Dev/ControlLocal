using System.Text.RegularExpressions;
using ControlLocal.Web.Components;
using ControlLocal.Web.Data;
using ControlLocal.Web.Services;
using ControlLocal.Web.Services.Api;
using Microsoft.AspNetCore.DataProtection;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;

QuestPDF.Settings.License = LicenseType.Community;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDataProtection()
    .PersistKeysToFileSystem(new DirectoryInfo(Path.Combine(Path.GetTempPath(), "ControlLocal-Keys")))
    .SetApplicationName("ControlLocal");

builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

// Estado de UI por circuito (rol activo + navegación).
builder.Services.AddScoped<AppState>();
builder.Services.AddScoped<ExportacionService>();

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

// Cliente del API REST del backend Java. Con Api:Enabled=true la autenticación
// pasa a HttpAuthService; el resto de servicios se conmuta del mismo modo.
builder.Services.Configure<ApiOptions>(builder.Configuration.GetSection(ApiOptions.Seccion));
builder.Services.AddScoped<ApiSession>();
builder.Services.AddHttpClient<ApiClient>();

var apiHabilitada = builder.Configuration.GetValue<bool>("Api:Enabled");
Console.WriteLine(apiHabilitada
    ? $"ControlLocal iniciado en modo API REST: {builder.Configuration["Api:BaseUrl"]}"
    : "ControlLocal iniciado en modo MOCK LOCAL.");
if (apiHabilitada)
{
    builder.Services.AddScoped<IAuthService, HttpAuthService>();
    builder.Services.AddScoped<IPropietarioService, HttpPropietarioService>();
    builder.Services.AddScoped<IClienteService, HttpClienteService>();
    builder.Services.AddScoped<ILocalService, HttpLocalService>();
    builder.Services.AddScoped<ICaptacionService, HttpCaptacionService>();
}
else
{
    builder.Services.AddScoped<IAuthService, MockAuthService>();
    builder.Services.AddScoped<IPropietarioService, MockPropietarioService>();
    builder.Services.AddScoped<IClienteService, MockClienteService>();
    builder.Services.AddScoped<ILocalService, MockLocalService>();
    builder.Services.AddScoped<ICaptacionService, MockCaptacionService>();
}

// Servicios de dominio (implementación en memoria mientras el API no esté activa).
builder.Services.AddScoped<IBrokerService, MockBrokerService>();
builder.Services.AddScoped<IAgenteService, MockAgenteService>();
builder.Services.AddScoped<IProspeccionService, MockProspeccionService>();
builder.Services.AddScoped<ISolicitudService, MockSolicitudService>();
builder.Services.AddScoped<IInteraccionService, MockInteraccionService>();
builder.Services.AddScoped<IOportunidadService, MockOportunidadService>();
builder.Services.AddScoped<IVisitaService, MockVisitaService>();
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
