using System.Globalization;
using ControlLocal.Web.Data;
using ControlLocal.Web.Models.Agentes;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Locales;
using ControlLocal.Web.Models.Oportunidades;
using ControlLocal.Web.Models.Propietarios;
using ControlLocal.Web.Models.Shared;
using ControlLocal.Web.Models.Solicitudes;
using ControlLocal.Web.Models.Visitas;
using ControlLocal.Web.Services;

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

internal sealed record ProspeccionApi(
    long Id,
    string CodigoProspeccion,
    long LocalId,
    string LocalCodigo,
    string Direccion,
    string Distrito,
    decimal? AreaM2,
    string Rubro,
    decimal? PrecioReferencial,
    string PropietarioNombre,
    long? IdAgente,
    string? AgenteNombre,
    string Estado,
    string? ResultadoPropuesta,
    DateOnly? FechaContacto,
    DateOnly? FechaReunion,
    DateOnly? FechaPropuesta,
    DateOnly? FechaRecontacto,
    string? Observaciones,
    long? IdCaptacion,
    string? CaptacionCodigo);

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

internal sealed record AgenteApi(
    long Id,
    string CodigoAgente,
    string Nombre,
    string? TipoPersona,
    string? TipoDocumento,
    string? NumeroDocumento,
    string? Telefono,
    string? Correo,
    string? Usuario,
    string? Zona,
    DateOnly? FechaIngreso,
    string? EstadoAdministrativo,
    string? EstadoOperativo);

internal sealed record OportunidadApi(
    long Id,
    string CodigoOportunidad,
    long IdCliente,
    string ClienteNombre,
    long IdCaptacion,
    string CodigoCaptacion,
    string DireccionLocal,
    string DistritoLocal,
    long IdAgente,
    string AgenteNombre,
    string Estado,
    DateTime FechaRegistro,
    string? MotivoCierre,
    string? Observaciones,
    DateTime? FechaCierre);

internal sealed record VisitaApi(
    long Id,
    long IdOportunidad,
    string CodigoOportunidad,
    DateOnly FechaVisita,
    TimeOnly HoraVisita,
    string? Observaciones,
    string Estado,
    string? Resultado,
    long IdCliente,
    string ClienteNombre,
    long IdCaptacion,
    string CodigoCaptacion,
    string DireccionLocal,
    string DistritoLocal,
    long IdAgente,
    string AgenteNombre,
    int? NivelInteres,
    string? ObjecionPrincipal,
    string? OpinionPrecio,
    string? ProximaAccion);

internal sealed record SolicitudApi(
    long Id,
    string CodigoSolicitud,
    DateOnly? FechaRegistro,
    decimal MontoPropuesto,
    string? PlazoTentativo,
    string? Observaciones,
    string Estado,
    DateTime? FechaActualizacionEstado,
    DateOnly? FechaVigenciaOferta,
    long IdOportunidad,
    string? CodigoOportunidad,
    long? IdCliente,
    string? ClienteNombre,
    long? IdCaptacion,
    string? CodigoCaptacion,
    string? DireccionLocal,
    string? DistritoLocal,
    long IdAgente,
    string? AgenteNombre);

internal sealed record EvaluacionApi(
    long Id,
    DateTime? FechaEvaluacion,
    string Resultado,
    string? Observaciones,
    long IdBroker,
    string? BrokerNombre,
    string TipoEvaluacion,
    long IdSolicitud);

internal sealed record AlertaApi(
    long Id,
    string Tipo,
    string Severidad,
    string EntidadTipo,
    long EntidadId,
    long IdAgente,
    string? AgenteNombre,
    string Mensaje,
    string Estado,
    DateTime? FechaGeneracion,
    DateTime? FechaResolucion,
    string? Ruta);

internal sealed record AtenderAlertaApi(bool Atendida);

public class HttpPropietarioService(ApiClient api) : IPropietarioService
{
    private List<PropietarioDto>? _cache;

    public IReadOnlyList<PropietarioDto> All() =>
        _cache ??= Task.Run(() => Cargar()).GetAwaiter().GetResult();

    public async Task<IReadOnlyList<PropietarioDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache = await Cargar(ct);
        return _cache;
    }

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

    private async Task<List<PropietarioDto>> Cargar(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<PropietarioApi>("propietarios", 1, 100, ct);
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
        _cache ??= Task.Run(() => Cargar()).GetAwaiter().GetResult();

    public ClienteInteresadoDto? ById(long id) =>
        All().FirstOrDefault(item => item.Id == id);

    public async Task<IReadOnlyList<ClienteInteresadoDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache = await Cargar(ct);
        return _cache;
    }

    private async Task<List<ClienteInteresadoDto>> Cargar(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<ClienteApi>("clientes", 1, 100, ct);
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

public class HttpLocalService(ApiClient api, HttpProspeccionService prospecciones) : ILocalService
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
        prospecciones.Invalidar();
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
        prospecciones.Invalidar();
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

public class HttpProspeccionService(ApiClient api) : IProspeccionService
{
    private List<ProspeccionDto>? _cache;

    public IReadOnlyList<ProspeccionDto> All() =>
        _cache ??= Task.Run(() => Cargar()).GetAwaiter().GetResult();

    public async Task<IReadOnlyList<ProspeccionDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache = await Cargar(ct);
        return _cache;
    }

    public ProspeccionDto? ById(long id) =>
        All().FirstOrDefault(item => item.Id == id);

    public IReadOnlyList<ProspeccionDto> PorRecontactar(int diasAviso)
    {
        var hoy = DateOnly.FromDateTime(DateTime.Today);
        var limite = hoy.AddDays(Math.Max(0, diasAviso));
        return All()
            .Where(item => item.Estado == "S"
                && item.FechaRecontacto is { } fecha
                && fecha <= limite)
            .OrderBy(item => item.FechaRecontacto)
            .ToList();
    }

    public ProspeccionDto Contactar(long id) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/contactar", new { }));

    public ProspeccionDto RegistrarReunion(long id) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/reunion", new { }));

    public ProspeccionDto EntregarPropuesta(long id) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/propuesta", new { }));

    public ProspeccionDto Posponer(long id, DateOnly fechaRecontacto) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>(
            $"prospecciones/{id}/recontactar",
            new { fechaRecontacto }));

    public ProspeccionDto Rechazar(long id, string motivo) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/rechazar", new { motivo }));

    public ProspeccionDto Descartar(long id, string motivo) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/descartar", new { motivo }));

    public ProspeccionDto Captar(long id, decimal comisionPactada) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/captar", new { comisionPactada }));

    public ProspeccionDto MarcarCaptado(long id, string codigoCaptacion) =>
        Actualizar(() => api.PostAsync<ProspeccionApi>(
            $"prospecciones/{id}/marcar-captado",
            new { codigoCaptacion }));

    internal void Invalidar() => _cache = null;

    private async Task<List<ProspeccionDto>> Cargar(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<ProspeccionApi>("prospecciones", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private ProspeccionDto Actualizar(Func<Task<ProspeccionApi?>> operacion)
    {
        // La llamada HTTP se crea DENTRO de Task.Run (hilo del pool, sin
        // SynchronizationContext). Crearla en el contexto del circuito y luego
        // bloquear con GetResult() interbloquea el circuito de Blazor Server.
        var respuesta = Task.Run(operacion).GetAwaiter().GetResult()
            ?? throw new InvalidOperationException("El API no devolvio la prospeccion actualizada.");
        var dto = Mapear(respuesta);
        ActualizarCache(dto);
        return dto;
    }

    private void ActualizarCache(ProspeccionDto actualizada)
    {
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == actualizada.Id);
        if (indice >= 0) _cache[indice] = actualizada;
        else _cache.Insert(0, actualizada);
    }

    private static ProspeccionDto Mapear(ProspeccionApi item) => new()
    {
        Id = item.Id,
        CodigoProspeccion = item.CodigoProspeccion,
        LocalId = item.LocalId,
        LocalCodigo = item.LocalCodigo,
        Direccion = item.Direccion,
        Distrito = item.Distrito,
        AreaM2 = (int)(item.AreaM2 ?? 0),
        Rubro = item.Rubro,
        PrecioReferencialTexto = (item.PrecioReferencial ?? 0)
            .ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        PropietarioNombre = item.PropietarioNombre,
        NombreAgente = item.AgenteNombre ?? "",
        Estado = item.Estado,
        ResultadoPropuesta = item.ResultadoPropuesta,
        FechaContactoTexto = FormatearFecha(item.FechaContacto),
        FechaReunionTexto = FormatearFecha(item.FechaReunion),
        FechaPropuestaTexto = FormatearFecha(item.FechaPropuesta),
        FechaRecontacto = item.FechaRecontacto,
        FechaRecontactoTexto = FormatearFecha(item.FechaRecontacto),
        Observaciones = item.Observaciones,
        CaptacionCodigo = item.CaptacionCodigo,
    };

    private static string? FormatearFecha(DateOnly? fecha) =>
        fecha?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture);
}

public class HttpCaptacionService(ApiClient api) : ICaptacionService
{
    private List<CaptacionDto>? _captaciones;
    private List<BandejaCaptacionDto>? _bandeja;

    public IReadOnlyList<CaptacionDto> All() =>
        _captaciones ??= Task.Run(() => CargarCaptaciones()).GetAwaiter().GetResult();

    public IReadOnlyList<BandejaCaptacionDto> Bandeja() =>
        _bandeja ??= Task.Run(() => CargarBandeja()).GetAwaiter().GetResult();

    // Recarga asincrona (sin bloquear el circuito) e invalida la cache: las
    // pantallas la invocan en OnInitializedAsync para ver siempre el estado
    // persistido por el otro rol (agente <-> broker) sin reconectar la sesion.
    public async Task<IReadOnlyList<CaptacionDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _captaciones = await CargarCaptaciones(ct);
        return _captaciones;
    }

    public async Task<IReadOnlyList<BandejaCaptacionDto>> RefrescarBandejaAsync(CancellationToken ct = default)
    {
        _bandeja = await CargarBandeja(ct);
        return _bandeja;
    }

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
        var encontrada = BuscarEnCache(codigo);
        if (encontrada is null)
        {
            _captaciones ??= await CargarCaptaciones(ct);
            encontrada = _captaciones.FirstOrDefault(item =>
                item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));
        }
        if (encontrada is null)
        {
            _bandeja ??= await CargarBandeja(ct);
            encontrada = Mapear(_bandeja.FirstOrDefault(item =>
                item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase)));
        }

        var id = encontrada?.Id ?? 0;
        if (id <= 0)
            return encontrada;

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

        var actual = BuscarEnCache(codigo)
            ?? await ObtenerPorCodigoAsync(codigo, ct)
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
        var actual = BuscarEnCache(codigo)
            ?? await ObtenerPorCodigoAsync(codigo, ct)
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

    private async Task<List<CaptacionDto>> CargarCaptaciones(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<CaptacionApi>("captaciones", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private async Task<List<BandejaCaptacionDto>> CargarBandeja(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<CaptacionApi>("captaciones/pendientes", 1, 100, ct);
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

    private CaptacionDto? BuscarEnCache(string codigo)
    {
        var captacion = _captaciones?.FirstOrDefault(item =>
            item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));
        if (captacion is not null)
            return captacion;

        var bandeja = _bandeja?.FirstOrDefault(item =>
            item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));
        return Mapear(bandeja);
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

public class HttpAgenteService(ApiClient api) : IAgenteService
{
    private List<AgenteDto>? _agentes;

    public IReadOnlyList<AgenteDto> All() =>
        _agentes ??= CargarAgentesSinRomper();

    public async Task<IReadOnlyList<AgenteDto>> RefrescarAsync(CancellationToken ct = default)
    {
        try
        {
            _agentes = await CargarAgentes(ct);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            _agentes ??= [];
        }
        return _agentes;
    }

    public AgenteDto? ById(long id) => All().FirstOrDefault(item => item.Id == id);

    public AgenteDto Agregar(AgenteDto agente)
    {
        _agentes ??= [];
        agente.Id = _agentes.Count == 0 ? 1 : _agentes.Max(item => item.Id) + 1;
        if (string.IsNullOrWhiteSpace(agente.CodigoAgente))
            agente.CodigoAgente = $"AGE-{agente.Id:000}";
        agente.FechaIngreso ??= DateOnly.FromDateTime(DateTime.Today);
        agente.FechaIngresoTexto = agente.FechaIngreso.Value.ToString("dd MMM yyyy", CultureInfo.InvariantCulture);
        agente.Iniciales = Iniciales(agente.Nombre);
        agente.Color = string.IsNullOrWhiteSpace(agente.Color) ? ColorPara(agente.Id) : agente.Color;
        _agentes.Add(agente);
        return agente;
    }

    public AgenteDto Actualizar(AgenteDto agente)
    {
        _agentes ??= CargarAgentesSinRomper();
        var indice = _agentes.FindIndex(item => item.Id == agente.Id);
        if (indice < 0)
            throw new InvalidOperationException("Agente no encontrado.");
        agente.Iniciales = Iniciales(agente.Nombre);
        agente.Color = string.IsNullOrWhiteSpace(agente.Color) ? ColorPara(agente.Id) : agente.Color;
        _agentes[indice] = agente;
        return agente;
    }

    public AgenteDto Desactivar(long id)
    {
        var agente = ById(id) ?? throw new InvalidOperationException("Agente no encontrado.");
        agente.EstadoAdministrativo = "Inactivo";
        agente.EstadoOperativo = "No disponible";
        return agente;
    }

    private List<AgenteDto> CargarAgentesSinRomper()
    {
        try
        {
            return Task.Run(() => CargarAgentes()).GetAwaiter().GetResult();
        }
        catch
        {
            return [];
        }
    }

    private async Task<List<AgenteDto>> CargarAgentes(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<AgenteApi>("agentes", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private static AgenteDto Mapear(AgenteApi item)
    {
        var nombre = item.Nombre ?? "";
        return new AgenteDto
        {
            Id = item.Id,
            CodigoAgente = item.CodigoAgente ?? "",
            Iniciales = Iniciales(nombre),
            Color = ColorPara(item.Id),
            Nombre = nombre,
            TipoPersona = TextoTipoPersona(item.TipoPersona),
            TipoDocumento = item.TipoDocumento ?? "D",
            Usuario = item.Usuario ?? "",
            Email = item.Correo ?? "",
            NumeroDocumento = item.NumeroDocumento ?? "",
            Telefono = item.Telefono ?? "",
            Zona = item.Zona ?? "",
            FechaIngreso = item.FechaIngreso,
            FechaIngresoTexto = item.FechaIngreso?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
            EstadoAdministrativo = TextoActivo(item.EstadoAdministrativo),
            EstadoOperativo = TextoOperativo(item.EstadoOperativo),
        };
    }

    private static string Iniciales(string nombre)
    {
        var iniciales = string.Concat(nombre
            .Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Take(2)
            .Select(parte => char.ToUpperInvariant(parte[0])));
        return string.IsNullOrWhiteSpace(iniciales) ? "--" : iniciales;
    }

    private static string ColorPara(long id) =>
        new[] { "#3A6BB5", "#2F7D52", "#C0473C", "#00AEEF", "#2C77A0", "#948D81" }[Math.Abs((int)id) % 6];

    private static string TextoTipoPersona(string? codigo) => codigo switch
    {
        "J" => "Persona juridica",
        _ => "Persona natural",
    };

    private static string TextoActivo(string? codigo) => codigo switch
    {
        "I" => "Inactivo",
        _ => "Activo",
    };

    private static string TextoOperativo(string? codigo) => codigo switch
    {
        "L" => "Licencia",
        "N" => "No disponible",
        _ => "Disponible",
    };
}

public class HttpOportunidadService(ApiClient api) : IOportunidadService
{
    private List<OportunidadComercialDto>? _cache;

    public IReadOnlyList<OportunidadComercialDto> All() =>
        _cache ??= Task.Run(() => Cargar()).GetAwaiter().GetResult();

    public async Task<IReadOnlyList<OportunidadComercialDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache = await Cargar(ct);
        return _cache;
    }

    public OportunidadComercialDto? ById(long id) =>
        All().FirstOrDefault(item => item.Id == id);

    public IReadOnlyList<OportunidadComercialDto> ByCaptacion(string codigoCaptacion) =>
        All().Where(item => item.CaptacionCodigo == codigoCaptacion).ToList();

    public OportunidadComercialDto Crear(OportunidadFormRequest request)
    {
        return CrearAsync(request).GetAwaiter().GetResult();
    }

    public async Task<OportunidadComercialDto> CrearAsync(
        OportunidadFormRequest request,
        CancellationToken ct = default)
    {
        if (request.ClienteId <= 0)
            throw new InvalidOperationException("Selecciona un cliente interesado.");
        if (request.CaptacionId <= 0)
            throw new InvalidOperationException("Selecciona una captacion activa.");

        var creada = await api.PostAsync<OportunidadApi>(
            "oportunidades",
            new
            {
                idCliente = request.ClienteId,
                idCaptacion = request.CaptacionId,
                observaciones = string.IsNullOrWhiteSpace(request.Observaciones)
                    ? null
                    : request.Observaciones.Trim(),
            },
            ct);
        var dto = creada is null
            ? (await RefrescarAsync(ct)).FirstOrDefault(item =>
                item.ClienteId == request.ClienteId && item.CaptacionId == request.CaptacionId)
            : Mapear(creada);
        if (dto is null)
            throw new InvalidOperationException("La oportunidad se registro, pero no se pudo refrescar el seguimiento.");
        if (creada is not null)
            ActualizarCache(dto);
        return dto;
    }

    public OportunidadComercialDto MarcarSolicitudCreada(long id)
    {
        var oportunidad = ById(id)
            ?? throw new InvalidOperationException("Oportunidad comercial no encontrada.");
        oportunidad.Estado = "Solicitud creada";
        return oportunidad;
    }

    public OportunidadComercialDto CerrarNoContinua(long id, string razon, string? observaciones)
    {
        return CerrarNoContinuaAsync(id, razon, observaciones).GetAwaiter().GetResult();
    }

    public async Task<OportunidadComercialDto> CerrarNoContinuaAsync(
        long id,
        string razon,
        string? observaciones,
        CancellationToken ct = default)
    {
        if (!EnumCatalog.MotivosNoContinuidad.Any(item => item.Code == razon))
            throw new InvalidOperationException("El motivo de no continuidad no es valido.");
        var actualizada = await api.PostAsync<OportunidadApi>(
                $"oportunidades/{id}/no-continuidad",
                new { razon, observaciones },
                ct);
        var dto = actualizada is null
            ? (await RefrescarAsync(ct)).FirstOrDefault(item => item.Id == id)
            : Mapear(actualizada);
        if (dto is null)
            throw new InvalidOperationException("La oportunidad se actualizo, pero no se pudo refrescar su estado.");
        ActualizarCache(dto);
        return dto;
    }

    public async Task<OportunidadComercialDto> CerrarExitosaAsync(long id, CancellationToken ct = default)
    {
        var actualizada = await api.PostAsync<OportunidadApi>(
            $"oportunidades/{id}/cierre-exitoso",
            new { },
            ct);
        var dto = actualizada is null
            ? (await RefrescarAsync(ct)).FirstOrDefault(item => item.Id == id)
            : Mapear(actualizada);
        if (dto is null)
            throw new InvalidOperationException("La oportunidad se cerro, pero no se pudo refrescar su estado.");
        ActualizarCache(dto);
        return dto;
    }

    internal void Invalidar() => _cache = null;

    private async Task<List<OportunidadComercialDto>> Cargar(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<OportunidadApi>("oportunidades", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private void ActualizarCache(OportunidadComercialDto actualizada)
    {
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == actualizada.Id);
        if (indice >= 0) _cache[indice] = actualizada;
        else _cache.Add(actualizada);
    }

    private static OportunidadComercialDto Mapear(OportunidadApi item) => new()
    {
        Id = item.Id,
        ClienteId = item.IdCliente,
        CodigoOportunidad = item.CodigoOportunidad,
        ClienteNombre = item.ClienteNombre,
        CaptacionId = item.IdCaptacion,
        CaptacionCodigo = item.CodigoCaptacion,
        DireccionLocal = item.DireccionLocal,
        NombreAgenteResponsable = item.AgenteNombre,
        Estado = Codigos.EstadoOportunidad(item.Estado),
        FechaRegistroTexto = item.FechaRegistro.ToString("dd MMM yyyy", CultureInfo.InvariantCulture),
        MotivoCierre = item.MotivoCierre ?? "",
        Observaciones = item.Observaciones ?? "",
        FechaCierreTexto = item.FechaCierre?.ToString("dd MMM yyyy HH:mm", CultureInfo.InvariantCulture) ?? "",
    };
}

public class HttpSolicitudService(ApiClient api, HttpOportunidadService oportunidades) : ISolicitudService
{
    private List<SolicitudAlquilerDto>? _cache;

    public IReadOnlyList<SolicitudAlquilerDto> All() =>
        _cache ??= Task.Run(() => Cargar()).GetAwaiter().GetResult();

    public async Task<IReadOnlyList<SolicitudAlquilerDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache = await Cargar(ct);
        return _cache;
    }

    public SolicitudAlquilerDto? ByCodigo(string codigo) =>
        All().FirstOrDefault(item => item.CodigoSolicitud.Equals(codigo, StringComparison.OrdinalIgnoreCase));

    public SolicitudAlquilerDto Agregar(SolicitudFormRequest request)
    {
        return AgregarAsync(request).GetAwaiter().GetResult();
    }

    public async Task<SolicitudAlquilerDto> AgregarAsync(
        SolicitudFormRequest request,
        CancellationToken ct = default)
    {
        var creada = await api.PostAsync<SolicitudApi>("solicitudes", new
            {
                idOportunidad = request.OportunidadId,
                montoPropuesto = request.MontoPropuesto,
                plazoTentativo = request.PlazoTentativo,
                observaciones = request.Observaciones,
            },
            ct);

        if (creada is null)
        {
            var registrada = await BuscarSolicitudRegistradaAsync(request.OportunidadId, ct);
            if (registrada is null)
                throw new InvalidOperationException("La solicitud se registro, pero no se pudo refrescar su estado.");
            if (!request.EnviarAEvaluacion)
                return registrada;

            var reenviada = await api.PostAsync<SolicitudApi>($"solicitudes/{registrada.Id}/reenviar", new { }, ct);
            if (reenviada is null)
            {
                var actualizada = await BuscarSolicitudRegistradaAsync(request.OportunidadId, ct);
                if (actualizada is not null && actualizada.Estado == "En revision")
                    return actualizada;
                throw new InvalidOperationException("La solicitud se envio, pero no se pudo refrescar su estado.");
            }

            var reenviadaDto = Mapear(reenviada);
            ActualizarCache(reenviadaDto);
            oportunidades.Invalidar();
            return reenviadaDto;
        }

        var respuesta = creada;
        if (request.EnviarAEvaluacion)
        {
            respuesta = await api.PostAsync<SolicitudApi>($"solicitudes/{creada.Id}/reenviar", new { }, ct)
                ?? await BuscarSolicitudApiAsync(creada.Id, ct)
                ?? throw new InvalidOperationException("La solicitud se envio, pero no se pudo refrescar su estado.");
        }

        var dto = Mapear(respuesta);
        ActualizarCache(dto);
        oportunidades.Invalidar();
        return dto;
    }

    private async Task<SolicitudAlquilerDto?> BuscarSolicitudRegistradaAsync(long oportunidadId, CancellationToken ct)
    {
        var solicitudes = await RefrescarAsync(ct);
        return solicitudes.FirstOrDefault(item => item.OportunidadId == oportunidadId);
    }

    private async Task<SolicitudApi?> BuscarSolicitudApiAsync(long id, CancellationToken ct)
    {
        var pagina = await api.GetPaginaAsync<SolicitudApi>("solicitudes", 1, 100, ct);
        return pagina?.Items.FirstOrDefault(item => item.Id == id);
    }

    public SolicitudAlquilerDto ReenviarAEvaluacion(string codigoSolicitud)
    {
        var solicitud = ByCodigo(codigoSolicitud)
            ?? throw new InvalidOperationException("Solicitud no encontrada.");
        var respuesta = Task.Run(() => api.PostAsync<SolicitudApi>($"solicitudes/{solicitud.Id}/reenviar", new { }))
            .GetAwaiter().GetResult();
        if (respuesta is null)
        {
            var actualizada = Task.Run(() => BuscarSolicitudRegistradaAsync(solicitud.OportunidadId, default))
                .GetAwaiter().GetResult();
            if (actualizada is not null)
            {
                ActualizarCache(actualizada);
                return actualizada;
            }
            throw new InvalidOperationException("La solicitud se envio, pero no se pudo refrescar su estado.");
        }
        var dto = Mapear(respuesta);
        ActualizarCache(dto);
        return dto;
    }

    public EvaluacionSolicitudDto Evaluar(string codigoSolicitud, EvaluacionSolicitudDto evaluacion)
    {
        return EvaluarAsync(codigoSolicitud, evaluacion).GetAwaiter().GetResult();
    }

    public async Task<EvaluacionSolicitudDto> EvaluarAsync(
        string codigoSolicitud,
        EvaluacionSolicitudDto evaluacion,
        CancellationToken ct = default)
    {
        var solicitud = ByCodigo(codigoSolicitud)
            ?? throw new InvalidOperationException("Solicitud no encontrada.");
        var respuesta = await api.PostAsync<EvaluacionApi>("evaluaciones", new
            {
                idSolicitud = solicitud.Id,
                tipoEvaluacion = evaluacion.TipoEvaluacion,
                resultado = evaluacion.Resultado,
                observaciones = evaluacion.Observaciones,
            },
            ct);

        _cache = null;
        return respuesta is null ? evaluacion : Mapear(respuesta);
    }

    private async Task<List<SolicitudAlquilerDto>> Cargar(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<SolicitudApi>("solicitudes", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private void ActualizarCache(SolicitudAlquilerDto solicitud)
    {
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == solicitud.Id);
        if (indice >= 0) _cache[indice] = solicitud;
        else _cache.Insert(0, solicitud);
    }

    private static SolicitudAlquilerDto Mapear(SolicitudApi item) => new()
    {
        Id = item.Id,
        CodigoSolicitud = item.CodigoSolicitud,
        CodigoOperacion = item.CodigoOportunidad ?? $"OPO-{item.IdOportunidad:0000}",
        OportunidadId = item.IdOportunidad,
        ClienteNombre = item.ClienteNombre ?? "",
        DireccionLocal = item.DireccionLocal ?? "",
        DistritoLocal = item.DistritoLocal ?? "",
        MontoMensual = item.MontoPropuesto,
        MontoMensualTexto = item.MontoPropuesto.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        PlazoMeses = ParseMeses(item.PlazoTentativo),
        PlazoTentativo = item.PlazoTentativo ?? "",
        Observaciones = item.Observaciones ?? "",
        DocumentosTexto = "0/6",
        PorcentajeDocumentos = 0,
        FechaRegistroTexto = item.FechaRegistro?.ToString("dd MMM", CultureInfo.InvariantCulture) ?? "",
        Estado = Codigos.EstadoSolicitud(item.Estado),
    };

    private static EvaluacionSolicitudDto Mapear(EvaluacionApi item) => new()
    {
        Id = item.Id,
        SolicitudId = item.IdSolicitud,
        TipoEvaluacion = item.TipoEvaluacion,
        Resultado = item.Resultado,
        Observaciones = item.Observaciones ?? "",
        ResponsableEvaluacion = item.BrokerNombre ?? "",
        FechaEvaluacionTexto = item.FechaEvaluacion?.ToString("dd MMM yyyy HH:mm", CultureInfo.InvariantCulture) ?? "",
    };

    private static int ParseMeses(string? plazo)
    {
        if (string.IsNullOrWhiteSpace(plazo))
            return 0;
        var digitos = new string(plazo.Where(char.IsDigit).ToArray());
        return int.TryParse(digitos, out var meses) ? meses : 0;
    }
}

public class HttpVisitaService(ApiClient api, HttpOportunidadService oportunidades) : IVisitaService
{
    private List<VisitaDto>? _cache;

    public IReadOnlyList<VisitaDto> All() =>
        _cache ??= Task.Run(() => Cargar()).GetAwaiter().GetResult();

    public async Task<IReadOnlyList<VisitaDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache = await Cargar(ct);
        return _cache;
    }

    public VisitaDto? ById(long id) => All().FirstOrDefault(item => item.Id == id);

    public VisitaDto Programar(VisitaFormRequest request)
    {
        return ProgramarAsync(request).GetAwaiter().GetResult();
    }

    public async Task<VisitaDto> ProgramarAsync(VisitaFormRequest request, CancellationToken ct = default)
    {
        var fecha = ParseFecha(request.FechaTexto);
        var hora = ParseHora(request.HoraTexto);
        var creada = await api.PostAsync<VisitaApi>("visitas", new
            {
                idOportunidad = request.OportunidadId,
                fechaVisita = fecha,
                horaVisita = hora,
                request.Observaciones,
            },
            ct);
        var dto = creada is null
            ? (await RefrescarAsync(ct)).FirstOrDefault(item =>
                item.OportunidadId == request.OportunidadId
                && item.FechaTexto == fecha.ToString("dd MMM yyyy", CultureInfo.InvariantCulture)
                && item.HoraTexto == hora.ToString("HH:mm", CultureInfo.InvariantCulture))
            : Mapear(creada);
        if (dto is null)
            throw new InvalidOperationException("La visita se registro, pero no se pudo refrescar en la agenda.");
        if (creada is not null)
            (_cache ??= []).Insert(0, dto);
        return dto;
    }

    public VisitaDto Reprogramar(long id, string fechaTexto, string horaTexto) =>
        ReprogramarAsync(id, fechaTexto, horaTexto).GetAwaiter().GetResult();

    public Task<VisitaDto> ReprogramarAsync(long id, string fechaTexto, string horaTexto, CancellationToken ct = default) =>
        ActualizarAsync(id, () => api.PatchAsync<VisitaApi>($"visitas/{id}/reprogramar", new
        {
            fechaVisita = ParseFecha(fechaTexto),
            horaVisita = ParseHora(horaTexto),
        }, ct));

    public VisitaDto Cancelar(long id, string motivo)
    {
        return CancelarAsync(id, motivo).GetAwaiter().GetResult();
    }

    public Task<VisitaDto> CancelarAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar el motivo de la cancelacion.");
        return ActualizarAsync(id, () => api.PatchAsync<VisitaApi>(
            $"visitas/{id}/cancelar",
            new { motivo = motivo.Trim() },
            ct));
    }

    public VisitaDto MarcarRealizada(long id) =>
        MarcarRealizadaAsync(id).GetAwaiter().GetResult();

    public Task<VisitaDto> MarcarRealizadaAsync(long id, CancellationToken ct = default) =>
        ActualizarAsync(id, () => api.PatchAsync<VisitaApi>($"visitas/{id}/realizar", new { }, ct));

    public VisitaDto MarcarNoRealizada(long id, string motivo)
    {
        return MarcarNoRealizadaAsync(id, motivo).GetAwaiter().GetResult();
    }

    public Task<VisitaDto> MarcarNoRealizadaAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar por que la visita no se realizo.");
        return ActualizarAsync(id, () => api.PatchAsync<VisitaApi>(
            $"visitas/{id}/no-realizada",
            new { motivo = motivo.Trim() },
            ct));
    }

    public VisitaDto RegistrarResultado(long id, VisitaResultadoRequest request)
    {
        return RegistrarResultadoAsync(id, request).GetAwaiter().GetResult();
    }

    public async Task<VisitaDto> RegistrarResultadoAsync(
        long id,
        VisitaResultadoRequest request,
        CancellationToken ct = default)
    {
        var actualizada = await ActualizarAsync(id, () => api.PatchAsync<VisitaApi>(
            $"visitas/{id}/resultado",
            new
            {
                request.Resultado,
                request.Observaciones,
                request.RazonNoContinuidad,
                request.NivelInteres,
                request.ObjecionPrincipal,
                request.OpinionPrecio,
                request.ProximaAccion,
            },
            ct));
        if (request.Resultado is "N" or "D")
            oportunidades.Invalidar();
        return actualizada;
    }

    private async Task<List<VisitaDto>> Cargar(CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<VisitaApi>("visitas", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private async Task<VisitaDto> ActualizarAsync(long id, Func<Task<VisitaApi?>> operacion)
    {
        var respuesta = await operacion();
        var dto = respuesta is null
            ? (await RefrescarAsync()).FirstOrDefault(item => item.Id == id)
            : Mapear(respuesta);
        if (dto is null)
            throw new InvalidOperationException("La visita se actualizo, pero no se pudo refrescar su estado.");
        _cache ??= [];
        var indice = _cache.FindIndex(item => item.Id == id);
        if (indice >= 0) _cache[indice] = dto;
        else _cache.Add(dto);
        return dto;
    }

    private static DateOnly ParseFecha(string valor)
    {
        if (DateOnly.TryParseExact(valor, "dd MMM yyyy", CultureInfo.InvariantCulture,
                System.Globalization.DateTimeStyles.None, out var fecha)
            || DateOnly.TryParseExact(valor, "yyyy-MM-dd", CultureInfo.InvariantCulture,
                System.Globalization.DateTimeStyles.None, out fecha))
            return fecha;
        throw new InvalidOperationException("La fecha de la visita no es valida.");
    }

    private static TimeOnly ParseHora(string valor)
    {
        if (TimeOnly.TryParseExact(valor, "HH:mm", CultureInfo.InvariantCulture,
                System.Globalization.DateTimeStyles.None, out var hora))
            return hora;
        throw new InvalidOperationException("La hora de la visita no es valida.");
    }

    private static VisitaDto Mapear(VisitaApi item) => new()
    {
        Id = item.Id,
        OportunidadId = item.IdOportunidad,
        FechaTexto = item.FechaVisita.ToString("dd MMM yyyy", CultureInfo.InvariantCulture),
        HoraTexto = item.HoraVisita.ToString("HH:mm", CultureInfo.InvariantCulture),
        CodigoCaptacion = item.CodigoCaptacion,
        ClienteNombre = item.ClienteNombre,
        DireccionLocal = item.DireccionLocal,
        DistritoLocal = item.DistritoLocal,
        NombreAgente = item.AgenteNombre,
        Estado = item.Estado,
        Resultado = item.Resultado,
        Observaciones = item.Observaciones,
        NivelInteres = item.NivelInteres,
        ObjecionPrincipal = item.ObjecionPrincipal,
        OpinionPrecio = item.OpinionPrecio,
        ProximaAccion = item.ProximaAccion,
    };
}

public class HttpAlertaService(ApiClient api, AppState app, NotificacionStore store) : INotificacionService
{
    private static readonly TimeSpan CacheBackendTtl = TimeSpan.FromSeconds(12);
    private List<NotificacionDto>? _cache;
    private DateTime _cacheGeneradaUtc = DateTime.MinValue;

    private string RolActivo => app.CurrentUser?.Role ?? app.Role;

    public IReadOnlyList<NotificacionDto> MisNotificaciones()
    {
        RefrescarBackendSiHaceFalta();
        // El backend se revalida con TTL corto; el bus in-app (store) se lee EN VIVO
        // en cada llamada, asi un Crear() de otro circuito/rol se ve sin recargar.
        return Combinar(_cache ?? [], store.ParaRol(RolActivo));
    }

    public int NoLeidas() => MisNotificaciones().Count(item => !item.Leida);

    private IReadOnlyList<NotificacionDto> Combinar(
        IEnumerable<NotificacionDto> backend,
        IEnumerable<NotificacionDto> locales)
    {
        var vistos = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var resultado = new List<NotificacionDto>();
        foreach (var item in backend.Concat(locales)
            .Where(EsParaRolActivo)
            .OrderByDescending(item => item.Fecha))
        {
            // Deduplica backend vs in-app por entidad (evita la misma alerta dos veces).
            if (!string.IsNullOrEmpty(item.EntidadRef)
                && !vistos.Add($"{item.Tipo}|{item.EntidadRef}|{item.DestinatarioRol}"))
                continue;
            resultado.Add(item);
        }
        return resultado;
    }

    private bool EsParaRolActivo(NotificacionDto item) =>
        item.DestinatarioRol == RolActivo
        || (RolActivo == Roles.Admin && item.DestinatarioRol == Roles.Broker);

    public void MarcarLeida(long id)
    {
        if (id < NotificacionStore.IdLocalMinimo)
        {
            try
            {
                // Solo aplica a alertas persistidas por el backend.
                Task.Run(() => api.PostAsync<AtenderAlertaApi>($"alertas/{id}/atender", new { }))
                    .GetAwaiter().GetResult();
            }
            catch
            {
                // Backend no disponible o alerta ya atendida en servidor.
            }
        }
        store.MarcarLeida(id);   // marca la in-app y dispara el refresco del Topbar
        var notificacion = _cache?.FirstOrDefault(item => item.Id == id);
        if (notificacion is not null)
            notificacion.Leida = true;
    }

    public void MarcarTodasLeidas()
    {
        foreach (var notificacion in MisNotificaciones().Where(item => !item.Leida).ToList())
            MarcarLeida(notificacion.Id);
    }

    public NotificacionDto Crear(NotificacionDto notificacion)
    {
        if (notificacion.Fecha == default)
            notificacion.Fecha = DateTime.Now;
        // Solo al store Singleton: el merge en MisNotificaciones lo hace visible a
        // todos los circuitos y el evento Cambiado refresca la campana en vivo.
        return store.Agregar(notificacion);
    }

    private void RefrescarBackendSiHaceFalta()
    {
        if (_cache is not null && DateTime.UtcNow - _cacheGeneradaUtc < CacheBackendTtl)
            return;

        _cache = Task.Run(Cargar).GetAwaiter().GetResult();
        _cacheGeneradaUtc = DateTime.UtcNow;
    }

    private async Task<List<NotificacionDto>> Cargar()
    {
        try
        {
            var pagina = await api.GetPaginaAsync<AlertaApi>("alertas", 1, 50);
            return pagina?.Items.Select(Mapear).ToList() ?? [];
        }
        catch
        {
            return [];
        }
    }

    private NotificacionDto Mapear(AlertaApi item) => new()
    {
        Id = item.Id,
        Tipo = Tipo(item.Tipo),
        Mensaje = item.Mensaje,
        Severidad = item.Severidad,
        Icono = Icono(item),
        EntidadTipo = item.EntidadTipo,
        EntidadRef = item.EntidadId.ToString(CultureInfo.InvariantCulture),
        Ruta = item.Ruta,
        DestinatarioRol = Destinatario(item.Tipo),
        Fecha = item.FechaGeneracion ?? DateTime.Now,
        Leida = item.Estado != "ACTIVA",
    };

    private string Destinatario(string tipo) => tipo switch
    {
        "SOLICITUD_REENVIADA" => Roles.Broker,
        "SOLICITUD_EVALUADA" => Roles.Agente,
        _ => RolActivo,
    };

    private static string Tipo(string tipo) => tipo switch
    {
        "SOLICITUD_REENVIADA" => "Solicitud por evaluar",
        "SOLICITUD_EVALUADA" => "Solicitud evaluada",
        _ => tipo.Replace('_', ' ').ToLowerInvariant(),
    };

    private static string Icono(AlertaApi item)
    {
        if (item.Severidad == "ALTA")
            return "alert";
        return item.Tipo.StartsWith("SOLICITUD", StringComparison.OrdinalIgnoreCase) ? "fileText" : "bell";
    }
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

    public static string EstadoOportunidad(string codigo) => codigo switch
    {
        "A" => "Abierta",
        "S" => "Solicitud creada",
        "N" => "No continua",
        "F" => "Finalizada exitosa",
        "X" => "Finalizada no favorable",
        _ => codigo,
    };

    public static string EstadoSolicitud(string codigo) => codigo switch
    {
        "G" => "Registrada",
        "E" => "En revision",
        "O" => "Observada",
        "A" => "Aprobada",
        "R" => "Rechazada",
        "D" => "Desistida",
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
