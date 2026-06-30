using System.Globalization;
using ControlLocal.Web.Data;
using ControlLocal.Web.Models.Locales;

namespace ControlLocal.Web.Services;

public class FichaModel
{
    public string Codigo { get; set; } = "";
    public string Direccion { get; set; } = "";
    public string Distrito { get; set; } = "";
    public string Estado { get; set; } = "";
    public int AreaM2 { get; set; }
    public string Rubro { get; set; } = "";
    public string Ambientes { get; set; } = "";
    public string Antiguedad { get; set; } = "";
    public string Referencia { get; set; } = "";
    public string PrecioTexto { get; set; } = "";
    public string ComisionTexto { get; set; } = "";
    public string VigenciaTexto { get; set; } = "";
    public string DiasRestantesTexto { get; set; } = "";
    public string PropietarioNombre { get; set; } = "";
    public string PropietarioTipo { get; set; } = "";
    public string PropietarioDocumento { get; set; } = "";
    public string PropietarioTelefono { get; set; } = "";
    public string PropietarioCorreo { get; set; } = "";
    public string AgenteNombre { get; set; } = "";
    public string Descripcion { get; set; } = "";
    public string GeneradoTexto { get; set; } = "";
    public string Frente { get; set; } = "-";
    public string Estacionamientos { get; set; } = "-";
    public string CargaElectrica { get; set; } = "-";
    public string AptoLicencia { get; set; } = "-";
    public string Zonificacion { get; set; } = "-";
    public string CuotaMantenimiento { get; set; } = "-";
    public string Urgencia { get; set; } = "-";
    public string Exclusividad { get; set; } = "-";
    public long LocalId { get; set; }
    public List<PrecioLocalDto> Precios { get; set; } = new();
}

public static class FichaBuilder
{
    public static async Task<FichaModel> BuildAsync(
        string? codigo,
        ICaptacionService capSvc,
        ILocalService localSvc,
        IPropietarioService propSvc)
    {
        var cap = (string.IsNullOrEmpty(codigo) ? null : await capSvc.ByCodigoAsync(codigo))
                  ?? (await capSvc.AllAsync()).First();

        var local = (await localSvc.AllAsync()).FirstOrDefault(l => Loose(l.Direccion, cap.DireccionLocal));
        var propietarios = await propSvc.AllAsync();
        var prop = propietarios.FirstOrDefault(p => Loose(p.Nombre, cap.PropietarioNombre));

        var area = cap.AreaM2 > 0 ? cap.AreaM2 : (local?.AreaM2 ?? 0);
        var rubro = local?.Rubro is { Length: > 0 } r ? r : "Por definir";
        var precio = local?.PrecioReferencialTexto is { Length: > 0 } pr ? pr : "-";
        var comision = cap.ComisionPactadaTexto is { Length: > 0 } c && c != "-" ? c : "-";

        return new FichaModel
        {
            Codigo = cap.CodigoCaptacion,
            Direccion = cap.DireccionLocal,
            Distrito = cap.DistritoLocal,
            Estado = cap.Estado,
            AreaM2 = area,
            Rubro = rubro,
            Ambientes = local?.Ambientes is int amb ? $"{amb} ambientes" : "-",
            Antiguedad = local?.AntiguedadAnios is int ant ? $"{ant} anos" : "-",
            Referencia = local?.ZonaUrbanizacion is { Length: > 0 } zona ? zona : "-",
            PrecioTexto = precio,
            ComisionTexto = comision,
            VigenciaTexto = string.IsNullOrEmpty(cap.VigenciaTexto) ? "Por definir" : cap.VigenciaTexto,
            DiasRestantesTexto = cap.DiasRestantesTexto,
            PropietarioNombre = prop?.Nombre ?? cap.PropietarioNombre,
            PropietarioTipo = prop?.TipoPersona ?? "Persona juridica - RUC",
            PropietarioDocumento = prop?.NumeroDocumento ?? "-",
            PropietarioTelefono = prop?.Telefono ?? "-",
            PropietarioCorreo = prop?.Correo ?? "-",
            AgenteNombre = string.IsNullOrEmpty(cap.NombreAgenteResponsable) ? "Sin asignar" : cap.NombreAgenteResponsable,
            Urgencia = cap.Urgencia is int nivel ? $"{nivel} / 5" : "-",
            Exclusividad = cap.Exclusividad == true ? "Encargo exclusivo" : cap.Exclusividad == false ? "No exclusivo" : "-",
            LocalId = local?.Id ?? 0,
            Descripcion = $"Local comercial ubicado en {cap.DireccionLocal}, {cap.DistritoLocal}. " +
                          $"Espacio de {area} m2 ideal para el rubro {rubro.ToLowerInvariant()}. " +
                          "Cuenta con frontis a la calle, instalaciones electricas y sanitarias operativas, " +
                          "y excelente afluencia peatonal en la zona.",
            Frente = local?.Frente is decimal fr ? $"{fr.ToString("0.##", CultureInfo.InvariantCulture)} m" : "-",
            Estacionamientos = local?.NumeroEstacionamientos is int est ? est.ToString() : "-",
            CargaElectrica = local?.CargaElectricaKw is decimal carga
                ? $"{carga.ToString("0.##", CultureInfo.InvariantCulture)} kW" : "-",
            AptoLicencia = local?.AptoLicenciaFuncionamiento is bool apto ? apto ? "Si" : "No" : "-",
            Zonificacion = local?.Zonificacion is { Length: > 0 } zon ? zon : "-",
            CuotaMantenimiento = local?.CuotaMantenimiento is decimal cuota
                ? $"USD {cuota.ToString("0.##", CultureInfo.InvariantCulture)}" : "-",
            GeneradoTexto = $"Generado el {DateTime.Now:dd/MM/yyyy} a las {DateTime.Now:HH:mm}",
        };
    }

    private static bool Loose(string a, string b)
    {
        a = Norm(a);
        b = Norm(b);
        if (a.Length == 0 || b.Length == 0)
            return false;
        return a == b || a.Contains(b) || b.Contains(a);
    }

    private static string Norm(string s) => (s ?? "").Trim().ToLowerInvariant();
}
