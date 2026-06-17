using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using ControlLocal.Web.Models.Locales;

namespace ControlLocal.Web.Services.Api;

public class LocalesApiService
{
    private readonly HttpClient _httpClient;
    // Ajusta el puerto si tu GlassFish usa uno diferente
    private readonly string _baseUrl = "http://localhost:8080/controllocal/Api/locales";

    public LocalesApiService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    // Método para listar solo los locales del agente (RF-004)
    public async Task<PageResponse<LocalComercialDto>> ObtenerMisLocalesAsync(string tokenJwt, int pagina = 1, int tamano = 10)
    {
        _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", tokenJwt);

        string url = $"{_baseUrl}/mis-locales?pagina={pagina}&tamano={tamano}";
        var response = await _httpClient.GetAsync(url);

        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<PageResponse<LocalComercialDto>>(json) ?? new PageResponse<LocalComercialDto>();
    }

    // Método para actualizar con trazabilidad (RF-004)
    public async Task<bool> ActualizarLocalAsync(long idLocal, LocalComercialDto localEditado, string tokenJwt)
    {
        _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", tokenJwt);

        var jsonContent = JsonSerializer.Serialize(localEditado);
        var content = new StringContent(jsonContent, Encoding.UTF8, "application/json");

        var response = await _httpClient.PutAsync($"{_baseUrl}/{idLocal}", content);

        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync();
            throw new Exception($"Error del servidor: {error}");
        }

        return true;
    }
}