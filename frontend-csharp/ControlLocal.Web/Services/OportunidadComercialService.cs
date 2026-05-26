using System.Net.Http.Json;
using ControlLocal.Web.Models;

namespace ControlLocal.Web.Services;

public class OportunidadComercialService(IHttpClientFactory httpClientFactory)
{
    private readonly HttpClient _http = httpClientFactory.CreateClient("ControlLocalApi");

    public async Task<List<OportunidadComercialDto>> GetAllAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<List<OportunidadComercialDto>>("/api/oportunidades-comerciales")
                ?? MockData();
        }
        catch
        {
            return MockData();
        }
    }

    public Task<OportunidadComercialDto?> GetByIdAsync(long id) =>
        _http.GetFromJsonAsync<OportunidadComercialDto>($"/api/oportunidades-comerciales/{id}");

    public async Task CreateAsync(OportunidadComercialDto oportunidad) =>
        await _http.PostAsJsonAsync("/api/oportunidades-comerciales", oportunidad);

    public async Task UpdateAsync(long id, OportunidadComercialDto oportunidad) =>
        await _http.PutAsJsonAsync($"/api/oportunidades-comerciales/{id}", oportunidad);

    public async Task DeleteAsync(long id) =>
        await _http.DeleteAsync($"/api/oportunidades-comerciales/{id}");

    // Mock temporal hasta que el backend Java exponga los endpoints REST.
    private static List<OportunidadComercialDto> MockData() =>
    [
        new() { Id = 1, Cliente = "Inversiones Trebol S.A.C.", LocalComercialId = 1, DireccionLocal = "Av. La Marina 245", Estado = "Abierta", FechaRegistro = DateOnly.FromDateTime(DateTime.Today.AddDays(-4)), MontoEstimado = 2800 },
        new() { Id = 2, Cliente = "Boutique Lila", LocalComercialId = 2, DireccionLocal = "Calle Schell 412", Estado = "Solicitud creada", FechaRegistro = DateOnly.FromDateTime(DateTime.Today.AddDays(-8)), MontoEstimado = 1850 },
        new() { Id = 3, Cliente = "Bodegas del Norte", LocalComercialId = 3, DireccionLocal = "Av. Petit Thouars 1875", Estado = "Abierta", FechaRegistro = DateOnly.FromDateTime(DateTime.Today.AddDays(-10)), MontoEstimado = 1600 }
    ];
}
