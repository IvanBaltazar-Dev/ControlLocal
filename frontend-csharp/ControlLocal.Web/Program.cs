using ControlLocal.Web.Components;
using ControlLocal.Web.Data;
using ControlLocal.Web.Services;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

// Circuit-scoped UI state (active role + navigation helper).
builder.Services.AddScoped<AppState>();

// Domain services — in-memory mocks today; swap for HttpClient-backed
// implementations when the Java REST API is available, no UI changes needed.
builder.Services.AddScoped<IBrokerService, MockBrokerService>();
builder.Services.AddScoped<IAgenteService, MockAgenteService>();
builder.Services.AddScoped<IPropietarioService, MockPropietarioService>();
builder.Services.AddScoped<IClienteService, MockClienteService>();
builder.Services.AddScoped<ILocalService, MockLocalService>();
builder.Services.AddScoped<ICaptacionService, MockCaptacionService>();
builder.Services.AddScoped<ISolicitudService, MockSolicitudService>();
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

app.MapStaticAssets();
app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();
