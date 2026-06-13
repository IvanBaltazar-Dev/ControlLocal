using System.Globalization;
using ControlLocal.Web.Data;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Locales;
using ControlLocal.Web.Models.Propietarios;

namespace ControlLocal.Web.Services.Api;

internal sealed record PropietarioApi(
    long Id,
    string TipoPersona,
    string TipoDocumento,
    string NumeroDocumento,
    string Nombre,
    string Telefono,
    string Correo,
    string Estado,
    bool? ConsentimientoUsoDato,
    DateTime? FechaCreacion);

internal sealed record ClienteApi(
    long Id,
    string TipoPersona,
    string TipoDocumento,
    string NumeroDocumento,
    string Nombre,
    string Telefono,
    string Correo,
    string RubroComercial,
    string Estado,
    bool? ConsentimientoContacto,
    bool? ConsentimientoUsoDato,
    DateTime? FechaCreacion);

internal sealed record LocalApi(
    long Id,
    string CodigoLocal,
    string Direccion,
    string Distrito,
    decimal Metraje,
    decimal PrecioReferencial,
    string RubroPermitido,
    string Descripcion,
    string Estado,
    long IdPropietario,
    string PropietarioNombre,
    string? TipoInmueble,
    string? Uso,
    int? Ambientes,
    int? AntiguedadAnios,
    string? ZonaUrbanizacion,
    decimal? GeoLat,
    decimal? GeoLong,
    string? EstadoPublicacion,
    DateTime? FechaRegistro);

internal sealed record CaptacionApi(
    long Id,
    string CodigoCaptacion,
    DateOnly? FechaCaptacion,
    DateOnly? FechaInicioVigencia,
    DateOnly? FechaFinVigencia,
    decimal ComisionPactada,
    string Observaciones,
    string Estado,
    string MotivoOperacion,
    int? Urgencia,
    bool? Exclusividad,
    string ObservacionRevision,
    DateTime? FechaRevision,
    long IdLocal,
    string DireccionLocal,
    string DistritoLocal,
    decimal? AreaM2,
    string Rubro,
    string PropietarioNombre,
    long IdAgente,
    string AgenteNombre,
    long? IdBrokerRevisor);

public class HttpPropietarioService(ApiClient api) : IPropietarioService
{
    private List<PropietarioDto>? _cache;

    public IReadOnlyList<PropietarioDto> All() =>
        _cache ??= Task.Run(Cargar).GetAwaiter().GetResult();

    public PropietarioDto? ById(long id) =>
        All().FirstOrDefault(item => item.Id == id);

    public PropietarioDto Agregar(PropietarioDto propietario)
    {
        var creado = Task.Run(() => api.PostAsync<PropietarioApi>("propietarios", Request(propietario)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio el propietario registrado.");
        var dto = Mapear(creado);
        (_cache ??= []).Add(dto);
        return dto;
    }

    public PropietarioDto Actualizar(PropietarioDto propietario)
    {
        var actualizado = Task.Run(() =>
                api.PutAsync<PropietarioApi>($"propietarios/{propietario.Id}", Request(propietario)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio el propietario actualizado.");
        var dto = Mapear(actualizado);
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == dto.Id);
        if (indice >= 0) _cache[indice] = dto;
        else _cache.Add(dto);
        return dto;
    }

    private async Task<List<PropietarioDto>> Cargar()
    {
        var pagina = await api.GetPaginaAsync<PropietarioApi>("propietarios", 1, 100);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private static PropietarioDto Mapear(PropietarioApi item) => new()
    {
        Id = item.Id,
        Nombre = item.Nombre,
        TipoPersona = Codigos.DescribirPersona(item.TipoPersona, item.TipoDocumento),
        TipoDocumento = item.TipoDocumento,
        NumeroDocumento = item.NumeroDocumento,
        Telefono = item.Telefono,
        Correo = item.Correo,
        Estado = Codigos.EstadoActivo(item.Estado),
        ConsentimientoUsoDato = item.ConsentimientoUsoDato,
        FechaCreacion = item.FechaCreacion,
    };

    private static object Request(PropietarioDto propietario) => new
    {
        tipoPersona = Codigos.TipoPersona(propietario.TipoPersona),
        tipoDocumento = Codigos.TipoDocumento(propietario.TipoDocumento, propietario.NumeroDocumento),
        propietario.NumeroDocumento,
        nombre = propietario.Nombre,
        propietario.Telefono,
        propietario.Correo,
        consentimientoUsoDato = propietario.ConsentimientoUsoDato ?? true,
        estado = Codigos.CodigoEstadoActivo(propietario.Estado),
    };
}

public class HttpClienteService(ApiClient api) : IClienteService
{
    private List<ClienteInteresadoDto>? _cache;

    public IReadOnlyList<ClienteInteresadoDto> All() =>
        _cache ??= Task.Run(Cargar).GetAwaiter().GetResult();

    public ClienteInteresadoDto? ById(long id) =>
        All().FirstOrDefault(item => item.Id == id);

    private async Task<List<ClienteInteresadoDto>> Cargar()
    {
        var pagina = await api.GetPaginaAsync<ClienteApi>("clientes", 1, 100);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    public ClienteInteresadoDto Agregar(ClienteInteresadoDto cliente)
    {
        var creado = Task.Run(() => api.PostAsync<ClienteApi>("clientes", Request(cliente)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio el cliente registrado.");
        var dto = Mapear(creado);
        (_cache ??= []).Add(dto);
        return dto;
    }

    public ClienteInteresadoDto Actualizar(ClienteInteresadoDto cliente)
    {
        var actualizado = Task.Run(() =>
                api.PutAsync<ClienteApi>($"clientes/{cliente.Id}", Request(cliente)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio el cliente actualizado.");
        var dto = Mapear(actualizado);
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == dto.Id);
        if (indice >= 0) _cache[indice] = dto;
        else _cache.Add(dto);
        return dto;
    }

    private static ClienteInteresadoDto Mapear(ClienteApi item) => new()
    {
        Id = item.Id,
        Nombre = item.Nombre,
        TipoPersona = Codigos.DescribirPersona(item.TipoPersona, item.TipoDocumento),
        TipoDocumento = item.TipoDocumento,
        NumeroDocumento = item.NumeroDocumento,
        Telefono = item.Telefono,
        Correo = item.Correo,
        RubroInteres = item.RubroComercial,
        Estado = Codigos.EstadoActivo(item.Estado),
        ConsentimientoContacto = item.ConsentimientoContacto,
        ConsentimientoUsoDato = item.ConsentimientoUsoDato,
        FechaCreacion = item.FechaCreacion,
    };

    private static object Request(ClienteInteresadoDto cliente) => new
    {
        tipoPersona = Codigos.TipoPersona(cliente.TipoPersona),
        tipoDocumento = Codigos.TipoDocumento(cliente.TipoDocumento, cliente.NumeroDocumento),
        cliente.NumeroDocumento,
        nombre = cliente.Nombre,
        cliente.Telefono,
        cliente.Correo,
        rubroComercial = cliente.RubroInteres,
        consentimientoContacto = cliente.ConsentimientoContacto ?? true,
        consentimientoUsoDato = cliente.ConsentimientoUsoDato ?? true,
        estado = Codigos.CodigoEstadoActivo(cliente.Estado),
    };
}

public class HttpLocalService(ApiClient api) : ILocalService
{
    private List<LocalComercialDto>? _cache;

    public IReadOnlyList<LocalComercialDto> All() =>
        _cache ??= Task.Run(Cargar).GetAwaiter().GetResult();

    public LocalComercialDto? ById(long id) =>
        All().FirstOrDefault(item => item.Id == id);

    private async Task<List<LocalComercialDto>> Cargar()
    {
        var pagina = await api.GetPaginaAsync<LocalApi>("locales", 1, 100);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    public LocalComercialDto Agregar(LocalComercialDto local)
    {
        var creado = Task.Run(() => api.PostAsync<LocalApi>("locales", Request(local)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio el local registrado.");
        var dto = Mapear(creado);
        (_cache ??= []).Add(dto);
        return dto;
    }

    public LocalComercialDto Actualizar(LocalComercialDto local)
    {
        var actualizado = Task.Run(() =>
                api.PutAsync<LocalApi>($"locales/{local.Id}", Request(local)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio el local actualizado.");
        var dto = Mapear(actualizado);
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == dto.Id);
        if (indice >= 0) _cache[indice] = dto;
        else _cache.Add(dto);
        return dto;
    }

    private static LocalComercialDto Mapear(LocalApi item) => new()
    {
        Id = item.Id,
        CodigoLocal = item.CodigoLocal,
        Direccion = item.Direccion,
        Distrito = item.Distrito,
        AreaM2 = (int)item.Metraje,
        Rubro = item.RubroPermitido,
        PrecioReferencial = item.PrecioReferencial,
        PrecioReferencialTexto = item.PrecioReferencial.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        Estado = Codigos.EstadoLocal(item.Estado),
        PropietarioId = item.IdPropietario,
        PropietarioNombre = item.PropietarioNombre,
        TipoInmueble = item.TipoInmueble,
        Uso = item.Uso,
        Ambientes = item.Ambientes,
        AntiguedadAnios = item.AntiguedadAnios,
        ZonaUrbanizacion = item.ZonaUrbanizacion,
        GeoLat = item.GeoLat,
        GeoLong = item.GeoLong,
        EstadoPublicacion = item.EstadoPublicacion,
        Descripcion = item.Descripcion,
        FechaRegistro = item.FechaRegistro,
    };

    private static object Request(LocalComercialDto local) => new
    {
        local.CodigoLocal,
        local.Direccion,
        local.Distrito,
        metraje = local.AreaM2,
        precioReferencial = local.PrecioReferencial,
        rubroPermitido = local.Rubro,
        local.Descripcion,
        idPropietario = local.PropietarioId,
        estado = Codigos.CodigoEstadoLocal(local.Estado),
        local.TipoInmueble,
        local.Uso,
        local.Ambientes,
        local.AntiguedadAnios,
        local.ZonaUrbanizacion,
        local.GeoLat,
        local.GeoLong,
        local.EstadoPublicacion,
    };
}

public class HttpCaptacionService(ApiClient api) : ICaptacionService
{
    private List<CaptacionDto>? _captaciones;
    private List<BandejaCaptacionDto>? _bandeja;

    public IReadOnlyList<CaptacionDto> All() =>
        _captaciones ??= Task.Run(CargarCaptaciones).GetAwaiter().GetResult();

    public IReadOnlyList<BandejaCaptacionDto> Bandeja() =>
        _bandeja ??= Task.Run(CargarBandeja).GetAwaiter().GetResult();

    public CaptacionDto? ByCodigo(string codigo) =>
        All().FirstOrDefault(item => item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));

    public CaptacionDto Agregar(CaptacionDto captacion)
    {
        var creada = Task.Run(() => api.PostAsync<CaptacionApi>("captaciones", Request(captacion)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio la captacion registrada.");
        var dto = Mapear(creada);
        ActualizarCache(dto);
        _bandeja = null;
        return dto;
    }

    public CaptacionDto Actualizar(CaptacionDto captacion)
    {
        var actualizada = Task.Run(() =>
                api.PutAsync<CaptacionApi>($"captaciones/{captacion.Id}", Request(captacion)))
            .GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio la captacion actualizada.");
        var dto = Mapear(actualizada);
        ActualizarCache(dto);
        _bandeja = null;
        return dto;
    }

    public async Task<CaptacionDto?> ObtenerPorCodigoAsync(string codigo, CancellationToken ct = default)
    {
        var encontrada = ByCodigo(codigo);
        var bandeja = Bandeja().FirstOrDefault(item =>
            item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));
        var id = encontrada?.Id ?? bandeja?.Id ?? 0;
        if (id <= 0)
            return encontrada ?? Mapear(bandeja);

        var respuesta = await api.GetAsync<CaptacionApi>($"captaciones/{id}", ct);
        var actualizada = respuesta is null ? encontrada : Mapear(respuesta);
        if (actualizada is not null)
            ActualizarCache(actualizada);
        return actualizada;
    }

    public async Task ResolverBandejaAsync(
        string codigo,
        string decision,
        string? observacion,
        CancellationToken ct = default)
    {
        var accion = decision.Trim().ToUpperInvariant();
        if (accion is "OBSERVAR" or "RECHAZAR" && string.IsNullOrWhiteSpace(observacion))
            throw new InvalidOperationException("Debes ingresar un motivo para continuar.");

        var actual = await ObtenerPorCodigoAsync(codigo, ct)
            ?? throw new InvalidOperationException("Captacion no encontrada.");
        var respuesta = await api.PostAsync<CaptacionApi>(
            $"captaciones/{actual.Id}/decision",
            new { accion, observacion },
            ct);
        if (respuesta is not null)
            ActualizarCache(Mapear(respuesta));
        _bandeja?.RemoveAll(item => item.CodigoCaptacion == codigo);
    }

    public async Task ReasignarBandejaAsync(
        string codigo,
        long idNuevoAgente,
        string motivo,
        CancellationToken ct = default)
    {
        if (idNuevoAgente <= 0 || string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Selecciona un agente destino e ingresa el motivo.");
        var actual = await ObtenerPorCodigoAsync(codigo, ct)
            ?? throw new InvalidOperationException("Captacion no encontrada.");
        var respuesta = await api.PostAsync<CaptacionApi>(
            $"captaciones/{actual.Id}/reasignar",
            new { idAgenteNuevo = idNuevoAgente, motivo },
            ct);
        if (respuesta is not null)
            ActualizarCache(Mapear(respuesta));
        _bandeja = null;
    }

    public async Task CerrarAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("El motivo de cierre es obligatorio.");
        var respuesta = await api.PostAsync<CaptacionApi>(
            $"captaciones/{id}/cierre",
            new { motivo = motivo.Trim() },
            ct);
        if (respuesta is not null)
            ActualizarCache(Mapear(respuesta));
        _bandeja = null;
    }

    private async Task<List<CaptacionDto>> CargarCaptaciones()
    {
        var pagina = await api.GetPaginaAsync<CaptacionApi>("captaciones", 1, 100);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private async Task<List<BandejaCaptacionDto>> CargarBandeja()
    {
        var pagina = await api.GetPaginaAsync<CaptacionApi>("captaciones/pendientes", 1, 100);
        return pagina?.Items.Select(MapearBandeja).ToList() ?? [];
    }

    private void ActualizarCache(CaptacionDto actualizada)
    {
        _captaciones ??= [];
        var indice = _captaciones.FindIndex(item => item.CodigoCaptacion == actualizada.CodigoCaptacion);
        if (indice >= 0)
            _captaciones[indice] = actualizada;
        else
            _captaciones.Add(actualizada);
    }

    private static CaptacionDto? Mapear(BandejaCaptacionDto? item) => item is null ? null : new CaptacionDto
    {
        Id = item.Id,
        CodigoCaptacion = item.CodigoCaptacion,
        DireccionLocal = item.DireccionLocal,
        DistritoLocal = item.DistritoLocal,
        AreaM2 = item.AreaM2,
        Rubro = item.Rubro,
        PropietarioNombre = item.PropietarioNombre,
        NombreAgenteResponsable = item.NombreAgenteResponsable,
        ComisionPactadaTexto = item.ComisionPactadaTexto,
        Estado = item.Estado,
    };

    private static CaptacionDto Mapear(CaptacionApi item)
    {
        var hoy = DateOnly.FromDateTime(DateTime.Today);
        var dias = item.FechaFinVigencia?.DayNumber - hoy.DayNumber;
        return new CaptacionDto
        {
            Id = item.Id,
            CodigoCaptacion = item.CodigoCaptacion,
            DireccionLocal = item.DireccionLocal,
            DistritoLocal = item.DistritoLocal,
            AreaM2 = (int)(item.AreaM2 ?? 0),
            Rubro = item.Rubro,
            PropietarioNombre = item.PropietarioNombre,
            LocalId = item.IdLocal,
            AgenteResponsableId = item.IdAgente,
            NombreAgenteResponsable = item.AgenteNombre,
            FechaCaptacion = item.FechaCaptacion,
            FechaInicioVigencia = item.FechaInicioVigencia,
            FechaFinVigencia = item.FechaFinVigencia,
            VigenciaTexto = FormatearVigencia(item.FechaInicioVigencia, item.FechaFinVigencia),
            DiasRestantesTexto = dias is null ? "" : dias >= 0 ? $"venc. en {dias} dias" : $"vencio hace {-dias} dias",
            ComisionPactada = item.ComisionPactada,
            ComisionPactadaTexto = item.ComisionPactada.ToString("0.0", CultureInfo.InvariantCulture),
            Estado = Codigos.EstadoCaptacion(item.Estado),
            Observaciones = item.Observaciones,
            MotivoOperacion = item.MotivoOperacion,
            Urgencia = item.Urgencia,
            Exclusividad = item.Exclusividad,
            ObservacionRevision = item.ObservacionRevision,
            FechaRevision = item.FechaRevision,
            BrokerRevisorId = item.IdBrokerRevisor,
        };
    }

    private static BandejaCaptacionDto MapearBandeja(CaptacionApi item) => new()
    {
        Id = item.Id,
        CodigoCaptacion = item.CodigoCaptacion,
        DireccionLocal = item.DireccionLocal,
        DistritoLocal = item.DistritoLocal,
        AreaM2 = (int)(item.AreaM2 ?? 0),
        Rubro = item.Rubro,
        PropietarioNombre = item.PropietarioNombre,
        NombreAgenteResponsable = item.AgenteNombre,
        FechaEnvioTexto = item.FechaCaptacion?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
        AntiguedadTexto = item.FechaCaptacion is { } fecha
            ? $"hace {Math.Max(0, DateOnly.FromDateTime(DateTime.Today).DayNumber - fecha.DayNumber)}d"
            : "",
        ComisionPactadaTexto = item.ComisionPactada.ToString("0.0", CultureInfo.InvariantCulture),
        Estado = Codigos.EstadoCaptacion(item.Estado),
    };

    private static string FormatearVigencia(DateOnly? inicio, DateOnly? fin)
    {
        if (inicio is null || fin is null)
            return "Sin vigencia definida";
        return $"{inicio:dd MMM yyyy} - {fin:dd MMM yyyy}";
    }

    private static object Request(CaptacionDto captacion) => new
    {
        captacion.CodigoCaptacion,
        captacion.FechaCaptacion,
        captacion.FechaInicioVigencia,
        captacion.FechaFinVigencia,
        captacion.ComisionPactada,
        captacion.Observaciones,
        idLocal = captacion.LocalId,
        motivoOperacion = captacion.MotivoOperacion,
        captacion.Urgencia,
        captacion.Exclusividad,
    };
}

internal static class Codigos
{
    public static string TipoPersona(string valor) =>
        valor.Contains("jur", StringComparison.OrdinalIgnoreCase) ? "J" : "N";

    public static string TipoDocumento(string valor, string numero) =>
        valor.ToUpperInvariant() switch
        {
            "R" or "RUC" => "R",
            "C" or "CE" => "C",
            "P" => "P",
            _ when numero.Replace(" ", "").Length == 11 => "R",
            _ => "D",
        };

    public static string DescribirPersona(string persona, string documento) =>
        $"{(persona == "J" ? "Persona juridica" : "Persona natural")} - {Documento(documento)}";

    public static string EstadoActivo(string codigo) => codigo == "I" ? "Inactivo" : "Activo";

    public static string CodigoEstadoActivo(string estado) =>
        estado.Equals("Inactivo", StringComparison.OrdinalIgnoreCase) ? "I" : "A";

    public static string EstadoLocal(string codigo) => codigo switch
    {
        "N" => "No disponible",
        "I" => "Inactivo",
        _ => "Disponible",
    };

    public static string CodigoEstadoLocal(string estado) => estado switch
    {
        "No disponible" => "N",
        "Inactivo" => "I",
        _ => "D",
    };

    public static string EstadoCaptacion(string codigo) => codigo switch
    {
        "P" => "Pendiente de revision",
        "O" => "Observada",
        "R" => "Rechazada",
        "A" => "Activa",
        "C" => "Cerrada",
        "V" => "Vencida",
        _ => codigo,
    };

    private static string Documento(string codigo) => codigo switch
    {
        "R" => "RUC",
        "C" => "CE",
        "P" => "Pasaporte",
        _ => "DNI",
    };
}
