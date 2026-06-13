using Microsoft.Extensions.Options;
using Amazon.S3;
using Amazon.S3.Model;
using System.Net;

namespace ControlLocal.Web.Services;

public sealed record ArchivoValidado(bool EsValido, string? Error = null)
{
    public static ArchivoValidado Ok() => new(true);
    public static ArchivoValidado Falla(string error) => new(false, error);
}

// Valida archivos recibidos por las bandejas de importación y carga de documentos:
// extensión y tipo de contenido en lista blanca, tamaño máximo y firma binaria
// (magic bytes) para impedir que un script renombrado pase como documento.
public static class ValidadorArchivos
{
    public const long TamanoMaximo = 5 * 1024 * 1024;

    private static readonly Dictionary<string, string[]> TiposPermitidos = new(StringComparer.OrdinalIgnoreCase)
    {
        [".csv"] = ["text/csv", "application/vnd.ms-excel", "text/plain"],
        [".pdf"] = ["application/pdf"],
        [".png"] = ["image/png"],
        [".jpg"] = ["image/jpeg"],
        [".jpeg"] = ["image/jpeg"],
    };

    private static readonly Dictionary<string, byte[][]> Firmas = new(StringComparer.OrdinalIgnoreCase)
    {
        [".pdf"] = [[0x25, 0x50, 0x44, 0x46]],
        [".png"] = [[0x89, 0x50, 0x4E, 0x47]],
        [".jpg"] = [[0xFF, 0xD8, 0xFF]],
        [".jpeg"] = [[0xFF, 0xD8, 0xFF]],
    };

    public static ArchivoValidado Validar(string nombre, string? tipoContenido, long tamano,
        ReadOnlySpan<byte> primerosBytes, params string[] extensionesPermitidas)
    {
        var extension = Path.GetExtension(nombre);
        if (string.IsNullOrEmpty(extension) || !TiposPermitidos.ContainsKey(extension))
            return ArchivoValidado.Falla($"Tipo de archivo no permitido ({extension}).");

        if (extensionesPermitidas.Length > 0 &&
            !extensionesPermitidas.Contains(extension, StringComparer.OrdinalIgnoreCase))
            return ArchivoValidado.Falla($"Aquí solo se aceptan archivos {string.Join(", ", extensionesPermitidas)}.");

        if (tamano <= 0)
            return ArchivoValidado.Falla("El archivo está vacío.");
        if (tamano > TamanoMaximo)
            return ArchivoValidado.Falla($"El archivo supera el máximo de {TamanoMaximo / 1024 / 1024} MB.");

        if (!string.IsNullOrEmpty(tipoContenido) &&
            !TiposPermitidos[extension].Contains(tipoContenido, StringComparer.OrdinalIgnoreCase))
            return ArchivoValidado.Falla("El tipo de contenido no coincide con la extensión del archivo.");

        if (Firmas.TryGetValue(extension, out var firmas))
        {
            var coincide = false;
            foreach (var firma in firmas)
                if (primerosBytes.Length >= firma.Length && primerosBytes[..firma.Length].SequenceEqual(firma))
                    coincide = true;
            if (!coincide)
                return ArchivoValidado.Falla("El contenido del archivo no corresponde a su extensión.");
        }

        return ArchivoValidado.Ok();
    }

    // Nombre de archivo seguro: sin rutas, sin caracteres de control ni especiales.
    public static string NombreSeguro(string nombre)
    {
        var soloNombre = Path.GetFileName(nombre);
        var extension = Path.GetExtension(soloNombre);
        var basePart = Path.GetFileNameWithoutExtension(soloNombre);
        var limpio = new string(basePart.Select(c => char.IsLetterOrDigit(c) || c is '-' or '_' ? c : '_').ToArray());
        if (string.IsNullOrWhiteSpace(limpio)) limpio = "documento";
        return limpio[..Math.Min(limpio.Length, 80)] + extension.ToLowerInvariant();
    }

    // Limpia texto importado: recorta, elimina caracteres de control y etiquetas HTML.
    public static string TextoSeguro(string? valor, int maxLargo = 200)
    {
        if (string.IsNullOrWhiteSpace(valor)) return "";
        var limpio = new string(valor.Trim().Where(c => !char.IsControl(c)).ToArray());
        limpio = limpio.Replace("<", "").Replace(">", "");
        return limpio[..Math.Min(limpio.Length, maxLargo)];
    }
}

public sealed record DocumentoGuardado(string Clave, string Nombre, long Tamano, DateTime FechaSubida);

// Almacenamiento de documentos del expediente. La implementación por defecto guarda
// en disco local; OpcionesAlmacenDocumentos.Proveedor = "S3" permite apuntar el mismo
// contrato a un bucket de objetos (Amazon S3 o compatible) sin tocar las pantallas.
public interface IDocumentoStorage
{
    Task<DocumentoGuardado> GuardarAsync(string carpeta, string nombreArchivo, Stream contenido, CancellationToken ct = default);
    Task<Stream?> AbrirAsync(string clave, CancellationToken ct = default);
    Task<bool> EliminarAsync(string clave, CancellationToken ct = default);
}

public class OpcionesAlmacenDocumentos
{
    public const string Seccion = "AlmacenDocumentos";

    // "Local" (disco) o "S3" (bucket de objetos compatible con S3).
    public string Proveedor { get; set; } = "Local";
    public string RutaLocal { get; set; } = "storage/documentos";
    public string Bucket { get; set; } = "";
    public string Prefix { get; set; } = "";
    public string Region { get; set; } = "";
    public string Endpoint { get; set; } = "";
}

public class AlmacenS3Documentos : IDocumentoStorage
{
    private readonly OpcionesAlmacenDocumentos _opciones;
    private readonly IAmazonS3 _s3;

    public AlmacenS3Documentos(IOptions<OpcionesAlmacenDocumentos> opciones)
    {
        _opciones = opciones.Value;
        if (string.IsNullOrWhiteSpace(_opciones.Bucket))
            throw new InvalidOperationException("AlmacenDocumentos:Bucket es obligatorio para usar S3.");

        var config = new AmazonS3Config();
        if (!string.IsNullOrWhiteSpace(_opciones.Endpoint))
        {
            config.ServiceURL = _opciones.Endpoint;
            config.ForcePathStyle = true;
            if (!string.IsNullOrWhiteSpace(_opciones.Region))
                config.AuthenticationRegion = _opciones.Region;
        }
        else if (!string.IsNullOrWhiteSpace(_opciones.Region))
        {
            config.RegionEndpoint = Amazon.RegionEndpoint.GetBySystemName(_opciones.Region);
        }
        _s3 = new AmazonS3Client(config);
    }

    public async Task<DocumentoGuardado> GuardarAsync(
        string carpeta,
        string nombreArchivo,
        Stream contenido,
        CancellationToken ct = default)
    {
        var carpetaSegura = ValidadorArchivos.NombreSeguro(carpeta + ".d")[..^2];
        var nombreSeguro = ValidadorArchivos.NombreSeguro(nombreArchivo);
        var clave = CrearClave($"{carpetaSegura}/{Guid.NewGuid():N}_{nombreSeguro}");
        var tamano = contenido.CanSeek ? contenido.Length - contenido.Position : 0;

        await _s3.PutObjectAsync(new PutObjectRequest
        {
            BucketName = _opciones.Bucket,
            Key = clave,
            InputStream = contenido,
            AutoCloseStream = false,
        }, ct);

        return new DocumentoGuardado(clave, nombreSeguro, tamano, DateTime.Now);
    }

    public async Task<Stream?> AbrirAsync(string clave, CancellationToken ct = default)
    {
        ValidarClave(clave);
        try
        {
            using var respuesta = await _s3.GetObjectAsync(_opciones.Bucket, clave, ct);
            var memoria = new MemoryStream();
            await respuesta.ResponseStream.CopyToAsync(memoria, ct);
            memoria.Position = 0;
            return memoria;
        }
        catch (AmazonS3Exception error) when (error.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<bool> EliminarAsync(string clave, CancellationToken ct = default)
    {
        ValidarClave(clave);
        var respuesta = await _s3.DeleteObjectAsync(_opciones.Bucket, clave, ct);
        return respuesta.HttpStatusCode is HttpStatusCode.OK or HttpStatusCode.NoContent;
    }

    private string CrearClave(string relativa)
    {
        var prefix = _opciones.Prefix.Trim().Trim('/');
        return string.IsNullOrEmpty(prefix) ? relativa : $"{prefix}/{relativa}";
    }

    private void ValidarClave(string clave)
    {
        if (string.IsNullOrWhiteSpace(clave) || clave.Contains("..", StringComparison.Ordinal)
            || clave.Contains('\\'))
            throw new InvalidOperationException("Clave de documento invalida.");

        var prefix = _opciones.Prefix.Trim().Trim('/');
        if (!string.IsNullOrEmpty(prefix) && !clave.StartsWith(prefix + "/", StringComparison.Ordinal))
            throw new InvalidOperationException("La clave no pertenece al prefijo configurado.");
    }
}

public class AlmacenLocalDocumentos(IOptions<OpcionesAlmacenDocumentos> opciones, IWebHostEnvironment env)
    : IDocumentoStorage
{
    private string Raiz => Path.IsPathRooted(opciones.Value.RutaLocal)
        ? opciones.Value.RutaLocal
        : Path.Combine(env.ContentRootPath, opciones.Value.RutaLocal);

    public async Task<DocumentoGuardado> GuardarAsync(string carpeta, string nombreArchivo, Stream contenido, CancellationToken ct = default)
    {
        var carpetaSegura = ValidadorArchivos.NombreSeguro(carpeta + ".d")[..^2];
        var nombreSeguro = ValidadorArchivos.NombreSeguro(nombreArchivo);
        var clave = $"{carpetaSegura}/{Guid.NewGuid():N}_{nombreSeguro}";

        var destino = RutaFisica(clave);
        Directory.CreateDirectory(Path.GetDirectoryName(destino)!);
        await using var fs = File.Create(destino);
        await contenido.CopyToAsync(fs, ct);
        return new DocumentoGuardado(clave, nombreSeguro, fs.Length, DateTime.Now);
    }

    public Task<Stream?> AbrirAsync(string clave, CancellationToken ct = default)
    {
        var ruta = RutaFisica(clave);
        return Task.FromResult<Stream?>(File.Exists(ruta) ? File.OpenRead(ruta) : null);
    }

    public Task<bool> EliminarAsync(string clave, CancellationToken ct = default)
    {
        var ruta = RutaFisica(clave);
        if (!File.Exists(ruta)) return Task.FromResult(false);
        File.Delete(ruta);
        return Task.FromResult(true);
    }

    // La clave nunca puede escapar de la raíz del almacén (anti path traversal).
    private string RutaFisica(string clave)
    {
        var completa = Path.GetFullPath(Path.Combine(Raiz, clave));
        var raiz = Path.GetFullPath(Raiz);
        if (!completa.StartsWith(raiz, StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException("Clave de documento inválida.");
        return completa;
    }
}
