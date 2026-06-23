using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization; 

namespace ControlLocal.Web.Models.Locales;

public class LocalComercialDto
{
    [JsonPropertyName("idLocal")] //  C# que busca "idLocal" en el JSON de Java
    public long Id { get; set; }

    [JsonPropertyName("codigoLocal")]
    public string CodigoLocal { get; set; } = string.Empty;

    [Required(ErrorMessage = "La dirección es obligatoria.")]
    [JsonPropertyName("direccion")]
    public string Direccion { get; set; } = string.Empty;

    [Required(ErrorMessage = "El distrito es obligatorio.")]
    [JsonPropertyName("distrito")]
    public string Distrito { get; set; } = string.Empty;

    [JsonPropertyName("metraje")] // Conecta "metraje" de Java con "AreaM2" de C#
    public int AreaM2 { get; set; }

    [JsonPropertyName("rubroPermitido")]
    public string Rubro { get; set; } = string.Empty;

    [JsonPropertyName("precioReferencial")]
    public decimal PrecioReferencial { get; set; }

    public string PrecioReferencialTexto { get; set; } = string.Empty;

    [Required(ErrorMessage = "Seleccione el estado.")]
    [JsonPropertyName("estado")]
    public string Estado { get; set; } = string.Empty;

    [JsonPropertyName("idPropietario")]
    public long? PropietarioId { get; set; }

    public string PropietarioNombre { get; set; } = string.Empty;

    [JsonPropertyName("tipoInmueble")]
    public string? TipoInmueble { get; set; }

    [JsonPropertyName("uso")]
    public string? Uso { get; set; }

    [JsonPropertyName("ambientes")]
    public int? Ambientes { get; set; }

    [JsonPropertyName("antiguedadAnios")]
    public int? AntiguedadAnios { get; set; }

    [JsonPropertyName("zonaUrbanizacion")]
    public string? ZonaUrbanizacion { get; set; }

    [JsonPropertyName("geoLat")]
    public decimal? GeoLat { get; set; }

    [JsonPropertyName("geoLong")]
    public decimal? GeoLong { get; set; }

    // Este campo lo añadiste en el backend como string en la respuesta
    [JsonPropertyName("estadoPublicacion")]
    public string? EstadoPublicacion { get; set; }

    [JsonPropertyName("descripcion")]
    public string Descripcion { get; set; } = string.Empty;

    [JsonPropertyName("fechaRegistro")]
    public DateTime? FechaRegistro { get; set; }

    // Ficha tecnica (Diccionario v2). Opcionales: no todos los locales los registran.
    [JsonPropertyName("frente")]
    public decimal? Frente { get; set; }

    [JsonPropertyName("zonificacion")]
    public string? Zonificacion { get; set; }

    [JsonPropertyName("aptoLicenciaFuncionamiento")]
    public bool? AptoLicenciaFuncionamiento { get; set; }

    [JsonPropertyName("cargaElectricaKw")]
    public decimal? CargaElectricaKw { get; set; }

    [JsonPropertyName("numeroEstacionamientos")]
    public int? NumeroEstacionamientos { get; set; }

    [JsonPropertyName("cuotaMantenimiento")]
    public decimal? CuotaMantenimiento { get; set; }

    // FK al catalogo distrito resuelta por el backend desde el nombre (solo Lima por ahora).
    [JsonPropertyName("idDistrito")]
    public long? IdDistrito { get; set; }
}
public class PageResponse<T>
{
    [System.Text.Json.Serialization.JsonPropertyName("items")]
    public System.Collections.Generic.List<T> Items { get; set; } = new();

    [System.Text.Json.Serialization.JsonPropertyName("totalRecords")]
    public long TotalRecords { get; set; }

    [System.Text.Json.Serialization.JsonPropertyName("page")]
    public int Page { get; set; }

    [System.Text.Json.Serialization.JsonPropertyName("pageSize")]
    public int PageSize { get; set; }
}