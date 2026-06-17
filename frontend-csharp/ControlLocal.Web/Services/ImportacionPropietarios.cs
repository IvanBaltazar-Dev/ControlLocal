using System.Text;
using ControlLocal.Web.Models.Propietarios;

namespace ControlLocal.Web.Services;

public sealed record FilaImportacion(int Linea, PropietarioDto? Propietario, string? Error)
{
    public bool EsValida => Propietario is not null;
}

public sealed record ResultadoImportacion(IReadOnlyList<FilaImportacion> Filas)
{
    public IReadOnlyList<PropietarioDto> Validos => Filas.Where(f => f.EsValida).Select(f => f.Propietario!).ToList();
    public int TotalValidas => Filas.Count(f => f.EsValida);
    public int TotalConError => Filas.Count(f => !f.EsValida);
}

// Lee el CSV de la bandeja de importación de propietarios. Formato esperado:
// Nombre, TipoPersona (Natural|Juridica), TipoDocumento (DNI|RUC|CE|Pasaporte),
// NumeroDocumento, Telefono, Correo. Cada celda se sanitiza antes de validar.
public static class ImportacionPropietarios
{
    public const int MaxFilas = 500;

    private static readonly Dictionary<string, string> TiposDocumento = new(StringComparer.OrdinalIgnoreCase)
    {
        ["DNI"] = "DNI",
        ["RUC"] = "RUC",
        ["CE"] = "CE",
        ["CARNET"] = "CE",
        ["PASAPORTE"] = "Pasaporte",
    };

    public static ResultadoImportacion Procesar(Stream contenido, IReadOnlyList<PropietarioDto> existentes)
    {
        var filas = new List<FilaImportacion>();
        var documentosVistos = new HashSet<string>(
            existentes.Select(p => SoloDigitos(p.NumeroDocumento)), StringComparer.Ordinal);

        using var lector = new StreamReader(contenido, Encoding.UTF8, detectEncodingFromByteOrderMarks: true);
        var linea = 0;
        string? texto;
        while ((texto = lector.ReadLine()) is not null)
        {
            linea++;
            if (linea > MaxFilas + 1)
            {
                filas.Add(new FilaImportacion(linea, null, $"Se admiten máximo {MaxFilas} filas por archivo."));
                break;
            }
            if (string.IsNullOrWhiteSpace(texto)) continue;

            var celdas = PartirCsv(texto);
            if (linea == 1 && celdas.Count > 0 &&
                celdas[0].Contains("nombre", StringComparison.OrdinalIgnoreCase))
                continue;

            filas.Add(ValidarFila(linea, celdas, documentosVistos));
        }

        if (filas.Count == 0)
            filas.Add(new FilaImportacion(0, null, "El archivo no contiene filas para importar."));

        return new ResultadoImportacion(filas);
    }

    private static FilaImportacion ValidarFila(int linea, IReadOnlyList<string> celdas, HashSet<string> documentosVistos)
    {
        if (celdas.Count < 4)
            return new FilaImportacion(linea, null, "Faltan columnas (mínimo: nombre, tipo persona, tipo documento, número).");

        var nombre = ValidadorArchivos.TextoSeguro(celdas[0], 150);
        var tipoPersona = ValidadorArchivos.TextoSeguro(celdas[1], 20);
        var tipoDocumento = ValidadorArchivos.TextoSeguro(celdas[2], 20);
        var numeroDocumento = ValidadorArchivos.TextoSeguro(celdas[3], 30);
        var telefono = ValidadorArchivos.TextoSeguro(celdas.Count > 4 ? celdas[4] : "", 20);
        var correo = ValidadorArchivos.TextoSeguro(celdas.Count > 5 ? celdas[5] : "", 150);

        if (nombre.Length < 3)
            return new FilaImportacion(linea, null, "El nombre o razón social es obligatorio (mínimo 3 caracteres).");

        var esJuridica = tipoPersona.StartsWith("J", StringComparison.OrdinalIgnoreCase);
        var esNatural = tipoPersona.StartsWith("N", StringComparison.OrdinalIgnoreCase);
        if (!esJuridica && !esNatural)
            return new FilaImportacion(linea, null, "Tipo de persona inválido (use Natural o Juridica).");

        if (!TiposDocumento.TryGetValue(tipoDocumento, out var docNormalizado))
            return new FilaImportacion(linea, null, "Tipo de documento inválido (use DNI, RUC, CE o Pasaporte).");

        var digitos = SoloDigitos(numeroDocumento);
        if (docNormalizado == "DNI" && digitos.Length != 8)
            return new FilaImportacion(linea, null, "El DNI debe tener 8 dígitos.");
        if (docNormalizado == "RUC" && digitos.Length != 11)
            return new FilaImportacion(linea, null, "El RUC debe tener 11 dígitos.");
        if (digitos.Length is < 6 or > 15)
            return new FilaImportacion(linea, null, "Número de documento inválido.");

        if (!documentosVistos.Add(digitos))
            return new FilaImportacion(linea, null, $"Documento {numeroDocumento} duplicado (ya existe o se repite en el archivo).");

        if (correo.Length > 0 && (!correo.Contains('@') || correo.IndexOf('@') == correo.Length - 1 || !correo[(correo.IndexOf('@') + 1)..].Contains('.')))
            return new FilaImportacion(linea, null, "Correo electrónico inválido.");

        if (telefono.Length > 0 && telefono.Count(char.IsDigit) < 6)
            return new FilaImportacion(linea, null, "Teléfono inválido.");

        var dto = new PropietarioDto
        {
            Nombre = nombre,
            TipoPersona = esJuridica ? $"Persona jurídica · {docNormalizado}" : $"Persona natural · {docNormalizado}",
            NumeroDocumento = numeroDocumento,
            Telefono = telefono,
            Correo = correo,
            CantidadLocales = 0,
            Estado = "Activo",
        };
        return new FilaImportacion(linea, dto, null);
    }

    private static string SoloDigitos(string valor) => new(valor.Where(char.IsDigit).ToArray());

    // Divide una línea CSV respetando comillas dobles.
    private static List<string> PartirCsv(string linea)
    {
        var celdas = new List<string>();
        var actual = new StringBuilder();
        var entreComillas = false;
        for (var i = 0; i < linea.Length; i++)
        {
            var c = linea[i];
            if (entreComillas)
            {
                if (c == '"' && i + 1 < linea.Length && linea[i + 1] == '"') { actual.Append('"'); i++; }
                else if (c == '"') entreComillas = false;
                else actual.Append(c);
            }
            else if (c == '"') entreComillas = true;
            else if (c == ',' || c == ';') { celdas.Add(actual.ToString()); actual.Clear(); }
            else actual.Append(c);
        }
        celdas.Add(actual.ToString());
        return celdas;
    }
}
