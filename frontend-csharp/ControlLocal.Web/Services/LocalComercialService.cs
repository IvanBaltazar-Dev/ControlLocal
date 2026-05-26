using System.Net.Http.Json;
using ControlLocal.Web.Models;

namespace ControlLocal.Web.Services;

public class LocalComercialService(IHttpClientFactory httpClientFactory)
{
    private readonly HttpClient _http = httpClientFactory.CreateClient("ControlLocalApi");

    public async Task<List<LocalComercialDto>> GetAllAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<List<LocalComercialDto>>("/api/locales-comerciales")
                ?? MockData();
        }
        catch
        {
            return MockData();
        }
    }

    public Task<LocalComercialDto?> GetByIdAsync(long id) =>
        _http.GetFromJsonAsync<LocalComercialDto>($"/api/locales-comerciales/{id}");

    public async Task CreateAsync(LocalComercialDto local) =>
        await _http.PostAsJsonAsync("/api/locales-comerciales", local);

    public async Task UpdateAsync(long id, LocalComercialDto local) =>
        await _http.PutAsJsonAsync($"/api/locales-comerciales/{id}", local);

    public async Task DeleteAsync(long id) =>
        await _http.DeleteAsync($"/api/locales-comerciales/{id}");

    // Mock temporal hasta que el backend Java exponga los endpoints REST.
    private static List<LocalComercialDto> MockData() =>
    [
        new() { Id = 1, Codigo = "LC-0218", Direccion = "Av. La Marina 245", Distrito = "San Miguel", AreaM2 = 120, PrecioAlquiler = 2800, Estado = "Disponible", PropietarioId = 1, NombrePropietario = "Inmobiliaria Pacifico S.A.C." },
        new() { Id = 2, Codigo = "LC-0226", Direccion = "Calle Schell 412", Distrito = "Miraflores", AreaM2 = 68, PrecioAlquiler = 1950, Estado = "Disponible", PropietarioId = 2, NombrePropietario = "Carlos Mendoza Rivera" },
        new() { Id = 3, Codigo = "LC-0231", Direccion = "Av. Petit Thouars 1875", Distrito = "Jesus Maria", AreaM2 = 95, PrecioAlquiler = 1600, Estado = "Disponible", PropietarioId = 3, NombrePropietario = "Grupo Bermudez E.I.R.L." }
    ];
}
