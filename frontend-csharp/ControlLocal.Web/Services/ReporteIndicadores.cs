using ControlLocal.Web.Models.Shared;
using ControlLocal.Web.Services.Api;

namespace ControlLocal.Web.Services;

public sealed record IndicadorReporte(string Icono, string Etiqueta, string Valor, string Delta, string Tono);
public sealed record SerieMensual(string[] Etiquetas, double[] Valores);
public sealed record EtapaReporte(string Color, string Nombre, string Valor);
public sealed record DesempenoFila(string Nombre, string Captaciones, string Cierres, string Conversion, double[] Tendencia);

public sealed record DatosReporte(
    string Ambito,
    IndicadorReporte[] Indicadores,
    SerieMensual CierresPorMes,
    EtapaReporte[] Etapas,
    string[][] Embudo,
    DesempenoFila[] Desempeno);

public static class ReporteIndicadores
{
    private static readonly string[] PaletaEmbudo =
        { "#005BFF", "#00AEEF", "#14B8A6", "#34D399", "#16A34A", "#0D9488" };

    public static DatosReporte Desde(IndicadoresDto ind, bool esAdmin, bool esAgente = false)
    {
        var activasPeriodo = ind.CaptacionesSalud
            .FirstOrDefault(e => e.Nombre.StartsWith("Activas", StringComparison.OrdinalIgnoreCase))?.Valor
            ?? ind.CaptacionesActivas;

        var indicadores = esAdmin
            ? new[]
            {
                new IndicadorReporte("pin", "Captaciones totales", ind.CaptacionesTotales.ToString(), $"{activasPeriodo} activas", "navy"),
                new IndicadorReporte("check", "Operaciones cerradas", ind.Cierres.ToString(), "acumulado", "green"),
                new IndicadorReporte("target", "Tasa de conversion", $"{ind.ConversionPropia}%", $"{ind.CierresCohorte} de {ind.CaptacionesTotales} captaciones cerradas", "blue"),
                new IndicadorReporte("users", "Agentes activos", ind.AgentesActivos.ToString(), $"{ind.BrokersActivos} brokers", "blue"),
            }
            : esAgente
                ? new[]
                {
                    new IndicadorReporte("pin", "Mis captaciones", ind.CaptacionesTotales.ToString(), $"{activasPeriodo} activas", "navy"),
                    new IndicadorReporte("check", "Mis cierres", ind.Cierres.ToString(), "en el periodo", "green"),
                    new IndicadorReporte("target", "Conversion propia", $"{ind.ConversionPropia}%", $"{ind.CierresCohorte} de {ind.CaptacionesTotales} captaciones cerradas", "blue"),
                    new IndicadorReporte("calendar", "Mis visitas", ind.Visitas.ToString(), "en el periodo", "blue"),
                }
                : new[]
                {
                    new IndicadorReporte("pin", "Captaciones del equipo", ind.CaptacionesTotales.ToString(), $"{activasPeriodo} activas", "navy"),
                    new IndicadorReporte("check", "Cierres del equipo", ind.Cierres.ToString(), "acumulado", "green"),
                    new IndicadorReporte("target", "Conversion", $"{ind.ConversionPropia}%", $"{ind.CierresCohorte} de {ind.CaptacionesTotales} captaciones cerradas", "blue"),
                    new IndicadorReporte("calendar", "Visitas realizadas", ind.Visitas.ToString(), "del equipo", "blue"),
                };

        var etapas = ind.CaptacionesSalud
            .Select((e, i) => new EtapaReporte(ColoresEstado.PorIndiceSaludCaptacion(i), e.Nombre, e.Valor.ToString()))
            .ToArray();
        if (etapas.Length == 0)
        {
            etapas = ind.Etapas
                .Select((e, i) => new EtapaReporte(ColoresEstado.PorIndicePipeline(i), e.Nombre, e.Valor.ToString()))
                .ToArray();
        }

        var embudo = ind.Embudo
            .Select((f, i) => new[] { f.Etapa, f.Valor.ToString(), $"{f.Porcentaje}%", PaletaEmbudo[i % PaletaEmbudo.Length] })
            .ToArray();

        var desempeno = ind.Desempeno
            .Select(d => new DesempenoFila(d.Nombre, d.Captaciones.ToString(), d.Cierres.ToString(), $"{d.Conversion}%", Array.Empty<double>()))
            .ToArray();

        var serie = new SerieMensual(
            ind.MesesEtiquetas.ToArray(),
            ind.CierresPorMes.Select(v => (double)v).ToArray());

        return new DatosReporte(
            string.IsNullOrEmpty(ind.Ambito) ? (esAdmin ? "Reportes globales" : "Reportes de equipo") : ind.Ambito,
            indicadores, serie, etapas, embudo, desempeno);
    }
}
