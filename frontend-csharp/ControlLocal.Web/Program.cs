using ControlLocal.Web.Components;
using ControlLocal.Web.Data;
using ControlLocal.Web.Services;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;

// QuestPDF — licencia Community (gratuita para este uso).
QuestPDF.Settings.License = LicenseType.Community;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

// Circuit-scoped UI state (active role + navigation helper).
builder.Services.AddScoped<AppState>();

// Autenticación — mock en memoria hoy; cambiar por HttpAuthService contra el
// backend Java (POST /auth/login) sin tocar la UI.
builder.Services.AddScoped<IAuthService, MockAuthService>();

// Domain services — in-memory mocks today; swap for HttpClient-backed
// implementations when the Java REST API is available, no UI changes needed.
builder.Services.AddScoped<IBrokerService, MockBrokerService>();
builder.Services.AddScoped<IAgenteService, MockAgenteService>();
builder.Services.AddScoped<IPropietarioService, MockPropietarioService>();
builder.Services.AddScoped<IClienteService, MockClienteService>();
builder.Services.AddScoped<ILocalService, MockLocalService>();
builder.Services.AddScoped<IProspeccionService, MockProspeccionService>();
builder.Services.AddScoped<ICaptacionService, MockCaptacionService>();
builder.Services.AddScoped<ISolicitudService, MockSolicitudService>();
builder.Services.AddScoped<IInteraccionService, MockInteraccionService>();
builder.Services.AddScoped<IOportunidadService, MockOportunidadService>();
builder.Services.AddScoped<IVisitaService, MockVisitaService>();
builder.Services.AddScoped<IAssignmentService, MockAssignmentService>();
builder.Services.AddScoped<IReasignacionCaptacionService, MockReasignacionCaptacionService>();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
    // The default HSTS value is 30 days. You may want to change this for production scenarios, see https://aka.ms/aspnetcore-hsts.
    app.UseHsts();
}
app.UseStatusCodePagesWithReExecute("/not-found", createScopeForStatusCodePages: true);
app.UseHttpsRedirection();

app.UseAntiforgery();

// Exportación de la ficha de propiedad a PDF (generado en servidor con QuestPDF).
app.MapGet("/ficha/{codigo}/pdf", (string codigo,
    ICaptacionService capSvc, ILocalService localSvc, IPropietarioService propSvc) =>
{
    var model = FichaBuilder.Build(codigo, capSvc, localSvc, propSvc);
    var bytes = new FichaPropiedadDocument(model).GeneratePdf();
    return Results.File(bytes, "application/pdf", $"Ficha_{model.Codigo}.pdf");
});

app.MapStaticAssets();
app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();
