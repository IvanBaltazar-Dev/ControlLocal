using System.Net.Http.Json;
using ControlLocal.Web.Models;

namespace ControlLocal.Web.Services;

public class CaptacionService(IHttpClientFactory httpClientFactory)
{
    private readonly HttpClient _http = httpClientFactory.CreateClient("ControlLocalApi");

    public async Task<List<CaptacionDto>> GetAllAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<List<CaptacionDto>>("/api/captaciones")
                ?? MockData();
        }
        catch
        {
            return MockData();
        }
    }

    public Task<CaptacionDto?> GetByIdAsync(long id) =>
        _http.GetFromJsonAsync<CaptacionDto>($"/api/captaciones/{id}");

    public async Task CreateAsync(CaptacionDto captacion) =>
        await _http.PostAsJsonAsync("/api/captaciones", captacion);

    public async Task UpdateAsync(long id, CaptacionDto captacion) =>
        await _http.PutAsJsonAsync($"/api/captaciones/{id}", captacion);

    public async Task DeleteAsync(long id) =>
        await _http.DeleteAsync($"/api/captaciones/{id}");

    // Mock temporal hasta que el backend Java exponga los endpoints REST.
    private static List<CaptacionDto> MockData() =>
    [
        new()
        {
            Id = 1,
            CodigoCaptacion = "CAP-2026-001",
            LocalComercialId = 1,
            DireccionLocal = "Av. La Marina 245",
            AgenteResponsableId = 1,
            NombreAgenteResponsable = "Valentina Mora",
            FechaCaptacion = DateOnly.FromDateTime(DateTime.Today.AddDays(-12)),
            FechaInicioVigencia = DateOnly.FromDateTime(DateTime.Today.AddDays(-10)),
            FechaFinVigencia = DateOnly.FromDateTime(DateTime.Today.AddMonths(6)),
            ComisionPactada = 7.50m,
            Estado = "Activa",
            Observaciones = "Local listo para visitas.",
            BrokerRevisorId = 1,
            NombreBrokerRevisor = "Marco Salazar",
            FechaRevision = DateTime.Today.AddDays(-9).AddHours(10),
            ObservacionRevision = "Documentacion validada.",
            FechaCreacion = DateTime.Today.AddDays(-12).AddHours(9),
            FechaActualizacion = DateTime.Today.AddDays(-9).AddHours(10)
        },
        new()
        {
            Id = 2,
            CodigoCaptacion = "CAP-2026-002",
            LocalComercialId = 2,
            DireccionLocal = "Calle Schell 412",
            AgenteResponsableId = 1,
            NombreAgenteResponsable = "Valentina Mora",
            FechaCaptacion = DateOnly.FromDateTime(DateTime.Today.AddDays(-6)),
            FechaInicioVigencia = DateOnly.FromDateTime(DateTime.Today.AddDays(-5)),
            FechaFinVigencia = DateOnly.FromDateTime(DateTime.Today.AddMonths(4)),
            ComisionPactada = 6.00m,
            Estado = "Pendiente de revision",
            Observaciones = "Pendiente de revision del broker.",
            FechaCreacion = DateTime.Today.AddDays(-6).AddHours(11),
            FechaActualizacion = DateTime.Today.AddDays(-6).AddHours(11)
        },
        new()
        {
            Id = 3,
            CodigoCaptacion = "CAP-2026-003",
            LocalComercialId = 3,
            DireccionLocal = "Av. Petit Thouars 1875",
            AgenteResponsableId = 2,
            NombreAgenteResponsable = "Carolina Vega",
            FechaCaptacion = DateOnly.FromDateTime(DateTime.Today.AddDays(-3)),
            FechaInicioVigencia = DateOnly.FromDateTime(DateTime.Today.AddDays(-2)),
            FechaFinVigencia = DateOnly.FromDateTime(DateTime.Today.AddMonths(5)),
            ComisionPactada = 8.25m,
            Estado = "Observada",
            Observaciones = "Falta validar licencia.",
            BrokerRevisorId = 1,
            NombreBrokerRevisor = "Marco Salazar",
            FechaRevision = DateTime.Today.AddDays(-1).AddHours(16),
            ObservacionRevision = "Adjuntar licencia municipal antes de activar.",
            FechaCreacion = DateTime.Today.AddDays(-3).AddHours(15),
            FechaActualizacion = DateTime.Today.AddDays(-1).AddHours(16)
        }
    ];
}
