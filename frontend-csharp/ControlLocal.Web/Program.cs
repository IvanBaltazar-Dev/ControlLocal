using ControlLocal.Web.Components;
using ControlLocal.Web.Services;
using Microsoft.AspNetCore.DataProtection;

var builder = WebApplication.CreateBuilder(args);

builder.Logging.ClearProviders();
builder.Logging.AddConsole();

// Add services to the container.
builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

var dataProtectionPath = Path.Combine(builder.Environment.ContentRootPath, ".aspnet-data-protection-keys");
Directory.CreateDirectory(dataProtectionPath);
builder.Services.AddDataProtection()
    .PersistKeysToFileSystem(new DirectoryInfo(dataProtectionPath));

builder.Services.AddHttpClient("ControlLocalApi", client =>
{
    var baseUrl = builder.Configuration["Api:BaseUrl"] ?? "http://localhost:8080";
    client.BaseAddress = new Uri(baseUrl);
    client.Timeout = TimeSpan.FromSeconds(1);
});

builder.Services.AddScoped<PropietarioService>();
builder.Services.AddScoped<LocalComercialService>();
builder.Services.AddScoped<CaptacionService>();
builder.Services.AddScoped<OportunidadComercialService>();
builder.Services.AddScoped<RoleState>();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
    // The default HSTS value is 30 days. You may want to change this for production scenarios, see https://aka.ms/aspnetcore-hsts.
    app.UseHsts();
}
app.UseStatusCodePagesWithReExecute("/not-found", createScopeForStatusCodePages: true);
if (!app.Environment.IsDevelopment())
{
    app.UseHttpsRedirection();
}

app.UseAntiforgery();

app.MapStaticAssets();
app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();
