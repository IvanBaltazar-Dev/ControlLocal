using System.Net.Http.Json;
using ControlLocal.Web.Models;

namespace ControlLocal.Web.Services;

public class PropietarioService(IHttpClientFactory httpClientFactory)
{
    private readonly HttpClient _http = httpClientFactory.CreateClient("ControlLocalApi");

    public async Task<List<PropietarioDto>> GetAllAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<List<PropietarioDto>>("/api/propietarios")
                ?? MockData();
        }
        catch
        {
            return MockData();
        }
    }

    public Task<PropietarioDto?> GetByIdAsync(long id) =>
        _http.GetFromJsonAsync<PropietarioDto>($"/api/propietarios/{id}");

    public async Task CreateAsync(PropietarioDto propietario) =>
        await _http.PostAsJsonAsync("/api/propietarios", propietario);

    public async Task UpdateAsync(long id, PropietarioDto propietario) =>
        await _http.PutAsJsonAsync($"/api/propietarios/{id}", propietario);

    public async Task DeleteAsync(long id) =>
        await _http.DeleteAsync($"/api/propietarios/{id}");

    // Mock temporal hasta que el backend Java exponga los endpoints REST.
    private static List<PropietarioDto> MockData() =>
    [
        new() { Id = 1, TipoDocumento = "RUC", NumeroDocumento = "20553102884", Nombres = "Inmobiliaria Pacifico S.A.C.", Correo = "contacto@pacifico.pe", Telefono = "014328800", Estado = "Activo" },
        new() { Id = 2, TipoDocumento = "DNI", NumeroDocumento = "08412991", Nombres = "Carlos", Apellidos = "Mendoza Rivera", Correo = "cmendoza@gmail.com", Telefono = "998220411", Estado = "Activo" },
        new() { Id = 3, TipoDocumento = "RUC", NumeroDocumento = "20502998110", Nombres = "Grupo Bermudez E.I.R.L.", Correo = "admin@bermudez.pe", Telefono = "012221108", Estado = "Activo" },
        new() { Id = 4, TipoDocumento = "DNI", NumeroDocumento = "09778002", Nombres = "Ana Lucia", Apellidos = "Pereyra", Correo = "alpereyra@hotmail.com", Telefono = "987412008", Estado = "Inactivo" }
    ];
}
