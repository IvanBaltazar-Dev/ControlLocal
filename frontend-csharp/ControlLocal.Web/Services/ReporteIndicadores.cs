using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;
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

// Única fuente de los indicadores de reportes: la pantalla y el PDF exportado
// leen de aquí, de modo que ambos muestran exactamente lo mismo por período.
public static class ReporteIndicadores
{
    // Colores del embudo: secuencia progresiva (azul → cian → teal → verde) conforme avanza
    // hacia el cierre. Todos distintos para que cada etapa se diferencie en la barra.
    private static readonly string[] PaletaEmbudo =
        { "#005BFF", "#00AEEF", "#14B8A6", "#34D399", "#16A34A", "#0D9488" };

    // Construye los datos del reporte (pantalla + PDF) a partir del resumen REAL del
    // backend (GET /indicadores/resumen). Reemplaza a los antiguos datos de ejemplo.
    public static DatosReporte Desde(IndicadoresDto ind, bool esAdmin, bool esAgente = false)
    {
        var indicadores = esAdmin
            ? new[]
            {
                new IndicadorReporte("pin", "Captaciones totales", ind.CaptacionesTotales.ToString(), $"{ind.CaptacionesActivas} activas", "navy"),
                new IndicadorReporte("check", "Operaciones cerradas", ind.Cierres.ToString(), "acumulado", "green"),
                new IndicadorReporte("target", "Tasa de conversión", $"{Conversion(ind.Cierres, ind.CaptacionesTotales)}%", "cierres / captaciones", "blue"),
                new IndicadorReporte("users", "Agentes activos", ind.AgentesActivos.ToString(), $"{ind.BrokersActivos} brokers", "blue"),
            }
            : esAgente
                ? new[]
                {
                    new IndicadorReporte("pin", "Mis captaciones", ind.CaptacionesTotales.ToString(), $"{ind.CaptacionesActivas} activas", "navy"),
                    new IndicadorReporte("check", "Mis cierres", ind.Cierres.ToString(), "en el periodo", "green"),
                    new IndicadorReporte("target", "Conversión propia", $"{Conversion(ind.Cierres, ind.CaptacionesTotales)}%", "cierres / captaciones", "blue"),
                    new IndicadorReporte("calendar", "Mis visitas", ind.Visitas.ToString(), "en el periodo", "blue"),
                }
            : new[]
            {
                new IndicadorReporte("pin", "Captaciones del equipo", ind.CaptacionesTotales.ToString(), $"{ind.CaptacionesActivas} activas", "navy"),
                new IndicadorReporte("check", "Cierres del equipo", ind.Cierres.ToString(), "acumulado", "green"),
                new IndicadorReporte("target", "Conversión", $"{Conversion(ind.Cierres, ind.CaptacionesTotales)}%", "cierres / captaciones", "blue"),
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

    private static int Conversion(int cierres, int captaciones) =>
        captaciones <= 0 ? 0 : (int)Math.Round(cierres * 100.0 / captaciones);
}

// Reporte de indicadores exportable a PDF (A4) — premium, colorido y CLARO (sin fondos
// oscuros): acentos de marca, KPIs con color por tono y diagramas reales (barras, dona, embudo).
public class ReporteIndicadoresDocument : IDocument
{
    private const string Primary = "#005BFF";
    private const string PrimarySoft = "#EAF1FF";
    private const string Cian = "#00AEEF";
    private const string Verde = "#2F7D52";
    private const string Ambar = "#C77700";
    private const string Rojo = "#D64550";
    private const string Ink = "#243043";   // gris azulado suave (no negro/navy)
    private const string Muted = "#6B7384";
    private const string Line = "#E2E6EC";
    private const string Soft = "#F4F7FB";
    private const string White = "#FFFFFF";

    private readonly DatosReporte _d;
    private readonly string _periodoEtiqueta;

    public ReporteIndicadoresDocument(DatosReporte datos, string periodoEtiqueta)
    {
        _d = datos;
        _periodoEtiqueta = periodoEtiqueta;
    }

    public DocumentMetadata GetMetadata() => new()
    {
        Title = $"{_d.Ambito} · ControlLocal",
        Author = "ControlLocal",
    };

    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.Margin(28);
            page.DefaultTextStyle(t => t.FontSize(10).FontColor(Ink).FontFamily(Fonts.Calibri));

            page.Header().Element(Cabecera);

            page.Content().PaddingVertical(12).Column(col =>
            {
                col.Spacing(16);
                col.Item().Element(Kpis);
                col.Item().Element(c => Bloque(c, "Cierres por mes", Barras));
                col.Item().Row(row =>
                {
                    row.RelativeItem().Element(c => Bloque(c, "Salud de captaciones", Dona));
                    row.ConstantItem(16);
                    row.RelativeItem().Element(c => Bloque(c, "Embudo de conversión", Embudo));
                });
                col.Item().Element(c => Bloque(c, "Desempeño", Desempeno));
            });

            page.Footer().AlignCenter().Text(t =>
            {
                t.Span("ControlLocal · Reporte de indicadores · Página ").FontSize(8).FontColor(Muted);
                t.CurrentPageNumber().FontSize(8).FontColor(Muted);
                t.Span(" de ").FontSize(8).FontColor(Muted);
                t.TotalPages().FontSize(8).FontColor(Muted);
            });
        });
    }

    private void Cabecera(IContainer c) => c.Column(col =>
    {
        col.Item().Background(PrimarySoft).Padding(16).Row(row =>
        {
            row.RelativeItem().Column(b =>
            {
                b.Item().Text("ControlLocal").FontSize(17).Bold().FontColor(Primary);
                b.Item().Text(_d.Ambito).FontSize(12).SemiBold().FontColor(Ink);
            });
            row.ConstantItem(200).Column(b =>
            {
                b.Item().AlignRight().Text(_periodoEtiqueta).FontSize(9.5f).FontColor(Muted);
                b.Item().AlignRight().Text($"Generado el {DateTime.Now:dd/MM/yyyy HH:mm}").FontSize(8).FontColor(Muted);
            });
        });
        col.Item().Height(3).Background(Primary);
    });

    private void Kpis(IContainer c) => c.Row(row =>
    {
        for (var i = 0; i < _d.Indicadores.Length; i++)
        {
            var ind = _d.Indicadores[i];
            if (i > 0) row.ConstantItem(10);
            row.RelativeItem().Border(1).BorderColor(Line).Background(White).Column(col =>
            {
                col.Item().Height(4).Background(TonoColor(ind.Tono));
                col.Item().Padding(11).Column(inner =>
                {
                    inner.Item().Text(ind.Etiqueta).FontSize(8).FontColor(Muted);
                    inner.Item().PaddingTop(3).Text(ind.Valor).FontSize(17).Bold().FontColor(Ink);
                    inner.Item().PaddingTop(2).Text(ind.Delta).FontSize(8).FontColor(Muted);
                });
            });
        }
    });

    // Marco de sección: tarjeta blanca con cabecera de banda clara y acento Primary.
    private void Bloque(IContainer c, string titulo, Action<IContainer> cuerpo) =>
        c.Border(1).BorderColor(Line).Background(White).Column(col =>
        {
            col.Item().Background(Soft).BorderBottom(1).BorderColor(Line)
               .PaddingVertical(8).PaddingHorizontal(12).Row(r =>
               {
                   r.ConstantItem(4).Background(Primary);
                   r.ConstantItem(8);
                   r.RelativeItem().AlignMiddle().Text(titulo).FontSize(11.5f).Bold().FontColor(Ink);
               });
            col.Item().Padding(14).Element(cuerpo);
        });

    // BARRAS: cierres por mes (rectángulos de color, sin dependencias externas).
    private void Barras(IContainer c)
    {
        var etiquetas = _d.CierresPorMes.Etiquetas;
        var valores = _d.CierresPorMes.Valores;
        if (etiquetas.Length == 0)
        {
            c.Text("Sin datos de cierres en el período.").FontSize(9).FontColor(Muted);
            return;
        }
        var max = valores.DefaultIfEmpty(0).Max();
        c.Row(row =>
        {
            for (var i = 0; i < etiquetas.Length; i++)
            {
                var v = i < valores.Length ? valores[i] : 0;
                var ratio = max > 0 ? v / max : 0;
                row.RelativeItem().Column(colb =>
                {
                    colb.Item().AlignCenter().Text(v.ToString("0")).FontSize(8).SemiBold().FontColor(Ink);
                    colb.Item().PaddingTop(3).Height(96).AlignBottom().AlignCenter()
                        .Width(20).Height((float)Math.Max(3, 96 * ratio)).Background(Primary);
                    colb.Item().PaddingTop(4).AlignCenter().Text(etiquetas[i]).FontSize(8).FontColor(Muted);
                });
            }
        });
    }

    // PIPELINE por etapa: barra apilada 100% con color por etapa + leyenda con % real.
    // Sin dependencias externas (QuestPDF 2026 ya no expone SkiaSharp para Canvas).
    private void Dona(IContainer c) => c.Column(col =>
    {
        var total = _d.Etapas.Sum(e => ParseInt(e.Valor));
        if (_d.Etapas.Length == 0 || total <= 0)
        {
            col.Item().Text("Sin etapas registradas.").FontSize(9).FontColor(Muted);
            return;
        }
        // Encabezado: % de captaciones que terminaron alquiladas (etapa Alquilada).
        var alquilada = _d.Etapas.FirstOrDefault(e => e.Nombre.Contains("Alquilada", StringComparison.OrdinalIgnoreCase));
        var pctCierre = (int)Math.Round(100.0 * (alquilada != null ? ParseInt(alquilada.Valor) : 0) / total);
        col.Item().PaddingBottom(10).Row(r =>
        {
            r.ConstantItem(60).AlignMiddle().Text($"{pctCierre}%").FontSize(26).Bold().FontColor(Verde);
            r.ConstantItem(8);
            r.RelativeItem().AlignMiddle().Text("de las captaciones llegó al cierre").FontSize(9.5f).FontColor(Muted);
        });
        col.Item().Height(20).Background(Soft).Row(row =>
        {
            foreach (var e in _d.Etapas)
            {
                var v = ParseInt(e.Valor);
                if (v <= 0) continue;
                row.RelativeItem(v).Background(e.Color);
            }
        });
        col.Item().PaddingTop(12).Column(leg =>
        {
            foreach (var e in _d.Etapas)
            {
                var v = ParseInt(e.Valor);
                var pct = (int)Math.Round(100.0 * v / total);
                leg.Item().PaddingVertical(2).Row(lr =>
                {
                    lr.ConstantItem(10).AlignMiddle().Width(10).Height(10).Background(e.Color);
                    lr.ConstantItem(8);
                    lr.RelativeItem().AlignMiddle().Text(e.Nombre).FontSize(9).FontColor(Ink);
                    lr.ConstantItem(70).AlignRight().AlignMiddle().Text($"{e.Valor} · {pct}%").FontSize(9).Bold().FontColor(Ink);
                });
            }
        });
    });

    // EMBUDO: barras horizontales proporcionales al porcentaje, con color por etapa.
    private void Embudo(IContainer c) => c.Column(col =>
    {
        if (_d.Embudo.Length == 0)
        {
            col.Item().Text("Sin operaciones en el embudo.").FontSize(9).FontColor(Muted);
            return;
        }
        foreach (var fila in _d.Embudo)
        {
            var etapa = fila[0];
            var valor = fila[1];
            var pct = fila[2];
            var color = fila[3];
            var ratio = ParsePct(pct);
            col.Item().PaddingVertical(3).Column(item =>
            {
                item.Item().Row(r =>
                {
                    r.RelativeItem().Text(etapa).FontSize(9).FontColor(Ink);
                    r.ConstantItem(82).AlignRight().Text($"{valor} · {pct}").FontSize(8.5f).FontColor(Muted);
                });
                item.Item().PaddingTop(3).Background(Soft).Height(14).Row(bar =>
                {
                    bar.RelativeItem(Math.Max(0.02f, ratio)).Background(color);
                    bar.RelativeItem(Math.Max(0.0001f, 1f - ratio));
                });
            });
        }
    });

    private void Desempeno(IContainer c)
    {
        if (_d.Desempeno.Length == 0)
        {
            c.Text("Sin actividad registrada todavía.").FontSize(9).FontColor(Muted);
            return;
        }
        c.Table(t =>
        {
            t.ColumnsDefinition(cd =>
            {
                cd.RelativeColumn(3);
                cd.RelativeColumn();
                cd.RelativeColumn();
                cd.RelativeColumn();
            });
            t.Cell().Element(Th).Text("Responsable");
            t.Cell().Element(Th).Text("Captaciones");
            t.Cell().Element(Th).Text("Cierres");
            t.Cell().Element(Th).Text("Conversión");
            foreach (var fila in _d.Desempeno)
            {
                t.Cell().Element(Td).Text(fila.Nombre);
                t.Cell().Element(Td).Text(fila.Captaciones);
                t.Cell().Element(Td).Text(fila.Cierres);
                t.Cell().Element(Td).Text(fila.Conversion);
            }
        });
    }

    private static IContainer Th(IContainer c) =>
        c.Background(PrimarySoft).BorderBottom(1).BorderColor(Line).PaddingVertical(6).PaddingHorizontal(8);

    private static IContainer Td(IContainer c) =>
        c.BorderBottom(1).BorderColor(Line).PaddingVertical(6).PaddingHorizontal(8);

    private static string TonoColor(string tono) => tono switch
    {
        "green" => Verde,
        "navy" => Primary,
        "blue" => Cian,
        "amber" => Ambar,
        "red" => Rojo,
        _ => Primary,
    };

    private static int ParseInt(string s) => int.TryParse(s, out var n) ? n : 0;

    private static float ParsePct(string s)
    {
        var t = (s ?? "").Replace("%", "").Trim();
        return float.TryParse(t, System.Globalization.NumberStyles.Any,
            System.Globalization.CultureInfo.InvariantCulture, out var v)
            ? Math.Clamp(v / 100f, 0f, 1f) : 0f;
    }
}
