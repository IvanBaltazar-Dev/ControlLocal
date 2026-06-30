using System.Globalization;
using ControlLocal.Web.Data;
using ControlLocal.Web.Models.Agentes;
using ControlLocal.Web.Models.Asignaciones;
using ControlLocal.Web.Models.Brokers;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Cartera;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Contratos;
using ControlLocal.Web.Models.Fichas;
using ControlLocal.Web.Models.Locales;
using ControlLocal.Web.Models.Oportunidades;
using ControlLocal.Web.Models.Propietarios;
using ControlLocal.Web.Models.Reportes;
using ControlLocal.Web.Models.Shared;
using ControlLocal.Web.Models.Solicitudes;
using ControlLocal.Web.Models.Tareas;
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
    DateTime? FechaCreacion,
    int CantidadLocales);

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

internal sealed record FichaClienteApi(
    ClienteApi Cliente,
    bool RequerimientoActivo,
    string? CtaRuta,
    Dictionary<string, FichaSectionApi>? Sections);

internal sealed record FichaPropietarioApi(
    PropietarioApi Propietario,
    Dictionary<string, FichaSectionApi>? Sections);

internal sealed record FichaSectionApi(
    string Section,
    long TotalRecords,
    int Page,
    int PageSize,
    IReadOnlyList<FichaRowApi>? Items);

internal sealed record FichaRowApi(
    string? Id,
    string? Codigo,
    string? Proceso,
    string? Titulo,
    string? Subtitulo,
    string? Local,
    string? Distrito,
    string? Cliente,
    long? ClienteId,
    string? Propietario,
    long? PropietarioId,
    string? Agente,
    string? Estado,
    string? Fecha,
    string? Ruta,
    string? Icono,
    string? Tono,
    DateTime? FechaOrden);

internal sealed record CoincidenciaApi(
    string? Tipo,
    long? Id,
    string? Codigo,
    string? Titulo,
    string? Subtitulo,
    string? Distrito,
    string? Renta,
    string? Area,
    string? Frente,
    int Puntaje,
    IReadOnlyList<string>? Cumple,
    IReadOnlyList<string>? NoCumple,
    long? ClienteId,
    long? CaptacionId,
    string? ProponerRuta);

internal sealed record CoincidenciasApi(
    string? Origen,
    int Total,
    int Page,
    int PageSize,
    IReadOnlyList<CoincidenciaApi>? Items);

internal sealed record RequerimientoApi(
    long? Id,
    long? IdCliente,
    string? Rubro,
    string? TipoInmueble,
    decimal? RentaMin,
    decimal? RentaMax,
    string? Moneda,
    decimal? MetrajeMin,
    decimal? MetrajeMax,
    decimal? FrenteMinimo,
    string? Estado,
    string? Observaciones,
    IReadOnlyList<string>? Distritos,
    DateTime? FechaCreacion,
    DateTime? FechaActualizacion);

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
    DateTime? FechaRegistro,
    decimal? Frente,
    string? Zonificacion,
    bool? AptoLicenciaFuncionamiento,
    decimal? CargaElectricaKw,
    int? NumeroEstacionamientos,
    decimal? CuotaMantenimiento,
    long? IdDistrito);

internal sealed record PrecioApi(
    long Id,
    long IdLocal,
    string Hito,
    string Moneda,
    decimal Monto,
    DateOnly? Fecha,
    DateTime? FechaCreacion);

internal sealed record PublicacionApi(
    long Id,
    string Canal,
    string TituloAnuncio,
    decimal RentaPublicada,
    string Moneda,
    string Estado,
    DateTime? FechaPublicacion,
    DateTime? FechaBaja,
    string? UrlPublicacion,
    string CodigoOrigen);

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
    string? CaptacionCodigo,
    string? Disponibilidad);

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
    string? EstadoOperativo,
    int CaptacionesActivas,
    int OperacionesActivas);

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
    DateTime? FechaCierre,
    DateTime? FechaActualizacion,
    long? IdPublicacionOrigen);

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
    string? AgenteNombre,
    int DocumentosEntregados,
    int DocumentosRequeridos,
    int? PlazoMeses,
    DateOnly? FechaInicio,
    string? FormaPago,
    int? MesesGarantia,
    int? MesesAdelanto);

internal sealed record ContratoApi(
    long Id,
    long? IdSolicitud,
    string? CodigoSolicitud,
    long? IdOportunidad,
    string? CodigoOportunidad,
    string? ClienteNombre,
    string? DireccionLocal,
    string? DistritoLocal,
    string? CodigoCaptacion,
    string? AgenteNombre,
    decimal RentaMensual,
    string? Moneda,
    int? PlazoContratoMeses,
    decimal ComisionGenerada,
    DateOnly? FechaInicioContrato,
    DateOnly? FechaFinContrato,
    DateOnly? FechaCierre,
    string? EstadoContrato,
    string? ComisionEstado,
    string? Incidencias,
    long? IdComision,
    long? AgenteId,
    long? PropietarioId,
    string? PropietarioNombre,
    decimal? MontoAgente,
    decimal? MontoEmpresa,
    string? FormaPago,
    DateOnly? FechaCobro);

internal sealed record DocumentoSolicitudApi(
    long Id,
    long? IdSolicitud,
    string? TipoDocumento,
    string? TipoNombre,
    string? NombreArchivo,
    string? RutaArchivo,
    DateTime? FechaEntrega,
    string? Estado,
    string? ResultadoRevision,
    string? Observaciones);

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

internal sealed record InteraccionApi(
    long Id,
    string? Contexto,
    long? IdOportunidad,
    long? IdProspeccion,
    long? IdCaptacion,
    long? IdCliente,
    long? IdPropietario,
    string? CodigoProspeccion,
    DateTime? FechaHora,
    string? CanalContacto,
    string? Resultado,
    string? Observaciones,
    string? TranscripcionNota,
    string? ClienteNombre,
    string? PropietarioNombre,
    string? PersonaTipo,
    string? PersonaNombre,
    string? CodigoCaptacion,
    string? AgenteNombre);

internal sealed record ReasignacionCaptacionApi(
    long IdReasignacion,
    long? IdCaptacion,
    string? CodigoCaptacion,
    string? DireccionLocal,
    long? IdAgenteAnterior,
    string? AgenteAnteriorNombre,
    long? IdAgenteNuevo,
    string? AgenteNuevoNombre,
    long? IdBroker,
    string? BrokerNombre,
    DateTime? FechaCambio,
    string? Motivo);

internal sealed record AsignacionAgenteApi(
    long IdAgente,
    string? Nombre,
    string? NumeroDocumento,
    string? EstadoAdministrativo,
    string? EstadoOperativo,
    string? BrokerActual);

internal sealed record AsignacionBrokerApi(
    long IdBroker,
    string? Nombre,
    string? Zona,
    string? EstadoAdministrativo,
    bool EsAdministrador,
    int AgentesACargo);

internal sealed record BrokerAgenteApi(
    long Id,
    long? IdAgente,
    string? AgenteNombre,
    long? IdBrokerAnterior,
    string? BrokerAnteriorNombre,
    long? IdBrokerNuevo,
    string? BrokerNuevoNombre,
    long? IdBrokerAdministrador,
    string? BrokerAdministradorNombre,
    DateTime? FechaCambio,
    string? Motivo);

internal sealed record BrokerApi(
    long Id,
    string? CodigoBroker,
    string? Nombre,
    string? TipoPersona,
    string? TipoDocumento,
    string? NumeroDocumento,
    string? Telefono,
    string? Correo,
    string? Usuario,
    string? Zona,
    DateOnly? FechaDesignacion,
    string? EstadoAdministrativo,
    bool EsAdministrador,
    int AgentesACargo);

public class HttpPropietarioService(ApiClient api) : IPropietarioService
{
    private readonly CacheRemoto<IReadOnlyList<PropietarioDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<PropietarioDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<PropietarioDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<PropietarioDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        var cacheado = lista.FirstOrDefault(item => item.Id == id);
        if (cacheado is not null) return cacheado;
        try
        {
            var remoto = await api.GetAsync<PropietarioApi>($"propietarios/{id}", ct);
            return remoto is null ? null : Mapear(remoto);
        }
        catch
        {
            return null;
        }
    }

    public async Task<PropietarioDto> AgregarAsync(PropietarioDto propietario, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<PropietarioApi>("propietarios", Request(propietario), ct)
            ?? throw new InvalidOperationException("El API no devolvio el propietario registrado.");
        _cache.Invalidar();
        return Mapear(creado);
    }

    public async Task<PropietarioDto> ActualizarAsync(PropietarioDto propietario, CancellationToken ct = default)
    {
        var actualizado = await api.PutAsync<PropietarioApi>($"propietarios/{propietario.Id}", Request(propietario), ct)
            ?? throw new InvalidOperationException("El API no devolvio el propietario actualizado.");
        _cache.Invalidar();
        return Mapear(actualizado);
    }

    private static async Task<IReadOnlyList<PropietarioDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        // Trae todos los propietarios recorriendo las páginas del backend (por partes de 100),
        // no solo los primeros 100. Mismo patrón que locales/prospecciones.
        var items = await api.GetTodasPaginasAsync<PropietarioApi>("propietarios", ct: ct);
        return items.Select(Mapear).ToList();
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
        CantidadLocales = item.CantidadLocales,
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
    private readonly CacheRemoto<IReadOnlyList<ClienteInteresadoDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<ClienteInteresadoDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<ClienteInteresadoDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<ClienteInteresadoDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        var cacheado = lista.FirstOrDefault(item => item.Id == id);
        if (cacheado is not null) return cacheado;
        try
        {
            var remoto = await api.GetAsync<ClienteApi>($"clientes/{id}", ct);
            return remoto is null ? null : Mapear(remoto);
        }
        catch
        {
            return null;
        }
    }

    public async Task<ClienteInteresadoDto?> ObtenerAsync(long id, CancellationToken ct = default)
    {
        var remoto = await api.GetAsync<ClienteApi>($"clientes/{id}", ct);
        return remoto is null ? null : Mapear(remoto);
    }

    public async Task<ClienteInteresadoDto> AgregarAsync(ClienteInteresadoDto cliente, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<ClienteApi>("clientes", Request(cliente), ct)
            ?? throw new InvalidOperationException("El API no devolvio el cliente registrado.");
        _cache.Invalidar();
        return Mapear(creado);
    }

    public async Task<ClienteInteresadoDto> ActualizarAsync(ClienteInteresadoDto cliente, CancellationToken ct = default)
    {
        var actualizado = await api.PutAsync<ClienteApi>($"clientes/{cliente.Id}", Request(cliente), ct)
            ?? throw new InvalidOperationException("El API no devolvio el cliente actualizado.");
        _cache.Invalidar();
        return Mapear(actualizado);
    }

    private static async Task<IReadOnlyList<ClienteInteresadoDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        // Trae todos los clientes recorriendo las páginas del backend (por partes de 100),
        // no solo los primeros 100. Mismo patrón que locales/prospecciones.
        var items = await api.GetTodasPaginasAsync<ClienteApi>("clientes", ct: ct);
        return items.Select(Mapear).ToList();
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

public class HttpFichaComercialService(ApiClient api) : IFichaComercialService
{
    public async Task<FichaComercialDto?> ClienteAsync(long id, CancellationToken ct = default)
    {
        var ficha = await api.GetAsync<FichaClienteApi>(
            $"clientes/{id}/ficha-comercial?page_size=8",
            ct);
        return ficha is null ? null : Mapear(ficha);
    }

    public async Task<FichaSectionDto> ClienteSectionAsync(
        long id,
        string section,
        int page,
        int pageSize = 8,
        CancellationToken ct = default)
    {
        var seccion = await api.GetAsync<FichaSectionApi>(
            $"clientes/{id}/ficha-comercial/{Uri.EscapeDataString(section)}?page={page}&page_size={pageSize}",
            ct);
        return Mapear(seccion);
    }

    public async Task<FichaComercialDto?> PropietarioAsync(long id, CancellationToken ct = default)
    {
        var ficha = await api.GetAsync<FichaPropietarioApi>(
            $"propietarios/{id}/ficha-comercial?page_size=8",
            ct);
        return ficha is null ? null : Mapear(ficha);
    }

    public async Task<FichaSectionDto> PropietarioSectionAsync(
        long id,
        string section,
        int page,
        int pageSize = 8,
        CancellationToken ct = default)
    {
        var seccion = await api.GetAsync<FichaSectionApi>(
            $"propietarios/{id}/ficha-comercial/{Uri.EscapeDataString(section)}?page={page}&page_size={pageSize}",
            ct);
        return Mapear(seccion);
    }

    private static FichaComercialDto Mapear(FichaClienteApi api) => new()
    {
        Persona = new FichaPersonaDto
        {
            Id = api.Cliente.Id,
            Tipo = "Cliente interesado",
            Nombre = api.Cliente.Nombre,
            TipoPersona = Codigos.DescribirPersona(api.Cliente.TipoPersona, api.Cliente.TipoDocumento),
            TipoDocumento = api.Cliente.TipoDocumento,
            NumeroDocumento = api.Cliente.NumeroDocumento,
            Telefono = api.Cliente.Telefono,
            Correo = api.Cliente.Correo,
            RubroInteres = api.Cliente.RubroComercial,
            Estado = Codigos.EstadoActivo(api.Cliente.Estado),
            FechaCreacion = api.Cliente.FechaCreacion,
        },
        RequerimientoActivo = api.RequerimientoActivo,
        CtaRuta = api.CtaRuta ?? "",
        Sections = MapearSecciones(api.Sections),
    };

    private static FichaComercialDto Mapear(FichaPropietarioApi api) => new()
    {
        Persona = new FichaPersonaDto
        {
            Id = api.Propietario.Id,
            Tipo = "Propietario",
            Nombre = api.Propietario.Nombre,
            TipoPersona = Codigos.DescribirPersona(api.Propietario.TipoPersona, api.Propietario.TipoDocumento),
            TipoDocumento = api.Propietario.TipoDocumento,
            NumeroDocumento = api.Propietario.NumeroDocumento,
            Telefono = api.Propietario.Telefono,
            Correo = api.Propietario.Correo,
            Estado = Codigos.EstadoActivo(api.Propietario.Estado),
            FechaCreacion = api.Propietario.FechaCreacion,
        },
        Sections = MapearSecciones(api.Sections),
    };

    private static Dictionary<string, FichaSectionDto> MapearSecciones(Dictionary<string, FichaSectionApi>? sections)
    {
        var resultado = new Dictionary<string, FichaSectionDto>(StringComparer.OrdinalIgnoreCase);
        if (sections is null)
            return resultado;

        foreach (var (clave, valor) in sections)
            resultado[clave] = Mapear(valor);
        return resultado;
    }

    private static FichaSectionDto Mapear(FichaSectionApi? section) => section is null
        ? new FichaSectionDto()
        : new FichaSectionDto
        {
            Section = section.Section,
            TotalRecords = section.TotalRecords,
            Page = section.Page,
            PageSize = section.PageSize,
            Items = section.Items?.Select(Mapear).ToList() ?? [],
        };

    private static FichaRowDto Mapear(FichaRowApi item) => new()
    {
        Id = item.Id ?? "",
        Codigo = item.Codigo ?? "",
        Proceso = item.Proceso ?? "",
        Titulo = item.Titulo ?? "",
        Subtitulo = item.Subtitulo ?? "",
        Local = item.Local ?? "",
        Distrito = item.Distrito ?? "",
        Cliente = item.Cliente ?? "",
        ClienteId = item.ClienteId,
        Propietario = item.Propietario ?? "",
        PropietarioId = item.PropietarioId,
        Agente = item.Agente ?? "",
        Estado = item.Estado ?? "",
        Fecha = item.Fecha ?? "",
        Ruta = item.Ruta ?? "",
        Icono = string.IsNullOrWhiteSpace(item.Icono) ? "activity" : item.Icono!,
        Tono = string.IsNullOrWhiteSpace(item.Tono) ? "gray" : item.Tono!,
        FechaOrden = item.FechaOrden,
    };
}

public class HttpCoincidenciaCarteraService(ApiClient api) : ICoincidenciaCarteraService
{
    public async Task<CoincidenciasDto> PropiedadesParaClienteAsync(
        long idCliente, int page = 1, int pageSize = 6, CancellationToken ct = default)
    {
        var resultado = await api.GetAsync<CoincidenciasApi>(
            $"clientes/{idCliente}/coincidencias?page={page}&page_size={pageSize}", ct);
        return Mapear(resultado);
    }

    public async Task<CoincidenciasDto> ClientesParaCaptacionAsync(
        string idOrCodigo, int page = 1, int pageSize = 6, CancellationToken ct = default)
    {
        var resultado = await api.GetAsync<CoincidenciasApi>(
            $"captaciones/{Uri.EscapeDataString(idOrCodigo)}/coincidencias?page={page}&page_size={pageSize}", ct);
        return Mapear(resultado);
    }

    public async Task<CoincidenciasDto> ClientesParaProspeccionAsync(
        long idProspeccion, int page = 1, int pageSize = 6, CancellationToken ct = default)
    {
        var resultado = await api.GetAsync<CoincidenciasApi>(
            $"prospecciones/{idProspeccion}/coincidencias?page={page}&page_size={pageSize}", ct);
        return Mapear(resultado);
    }

    private static CoincidenciasDto Mapear(CoincidenciasApi? api) => api is null
        ? new CoincidenciasDto()
        : new CoincidenciasDto
        {
            Origen = api.Origen ?? "",
            Total = api.Total,
            Page = api.Page <= 0 ? 1 : api.Page,
            PageSize = api.PageSize <= 0 ? 6 : api.PageSize,
            Items = api.Items?.Select(Mapear).ToList() ?? [],
        };

    private static CoincidenciaDto Mapear(CoincidenciaApi item) => new()
    {
        Tipo = item.Tipo ?? "",
        Id = item.Id,
        Codigo = item.Codigo ?? "",
        Titulo = item.Titulo ?? "",
        Subtitulo = item.Subtitulo ?? "",
        Distrito = item.Distrito ?? "",
        Renta = item.Renta ?? "",
        Area = item.Area ?? "",
        Frente = item.Frente ?? "",
        Puntaje = item.Puntaje,
        Cumple = item.Cumple ?? [],
        NoCumple = item.NoCumple ?? [],
        ClienteId = item.ClienteId,
        CaptacionId = item.CaptacionId,
        ProponerRuta = item.ProponerRuta ?? "",
    };
}

internal sealed record ReportePreviewApi(int Consultas, int Visitas, string? Objeciones);

internal sealed record ReportePropietarioApi(
    long Id,
    long? IdCaptacion,
    long? IdAgente,
    DateTime? FechaReporte,
    DateTime? PeriodoInicio,
    DateTime? PeriodoFin,
    int? ConsultasReportadas,
    int? VisitasReportadas,
    string? ObjecionesFrecuentes,
    string? AjustesRecomendados,
    string? CanalEnvio,
    DateTime? FechaCreacion);

// Reporte periódico al propietario (Etapa 8), anclado al expediente de la captación.
public class HttpReportePropietarioService(ApiClient api) : IReportePropietarioService
{
    public async Task<IReadOnlyList<ReportePropietarioDto>> ListarPorCaptacionAsync(long idCaptacion, CancellationToken ct = default)
    {
        var items = await api.GetAsync<List<ReportePropietarioApi>>($"captaciones/{idCaptacion}/reportes-propietario", ct);
        return items?.Select(Map).ToList() ?? [];
    }

    public async Task<ReportePropietarioDto> CrearAsync(long idCaptacion, ReportePropietarioDto reporte, CancellationToken ct = default)
    {
        var resp = await api.PostAsync<ReportePropietarioApi>(
            $"captaciones/{idCaptacion}/reportes-propietario", Cuerpo(reporte), ct);
        return resp is null ? reporte : Map(resp);
    }

    public async Task<ReportePreviewDto> PreviewAsync(long idCaptacion, DateTime? desde, DateTime? hasta, CancellationToken ct = default)
    {
        var filtros = new List<string>();
        if (desde is { } d) filtros.Add($"desde={d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");
        if (hasta is { } h) filtros.Add($"hasta={h.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");
        var ruta = $"captaciones/{idCaptacion}/reportes-propietario/preview";
        if (filtros.Count > 0) ruta += "?" + string.Join("&", filtros);
        var resp = await api.GetAsync<ReportePreviewApi>(ruta, ct);
        return resp is null
            ? ReportePreviewDto.Vacio
            : new ReportePreviewDto(resp.Consultas, resp.Visitas, resp.Objeciones ?? "");
    }

    private static object Cuerpo(ReportePropietarioDto r) => new
    {
        periodoInicio = r.PeriodoInicio?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
        periodoFin = r.PeriodoFin?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
        consultasReportadas = r.ConsultasReportadas,
        visitasReportadas = r.VisitasReportadas,
        objecionesFrecuentes = r.ObjecionesFrecuentes,
        ajustesRecomendados = r.AjustesRecomendados,
        canalEnvio = r.CanalEnvio,
    };

    private static ReportePropietarioDto Map(ReportePropietarioApi a) => new()
    {
        Id = a.Id,
        CaptacionId = a.IdCaptacion ?? 0,
        AgenteId = a.IdAgente ?? 0,
        FechaReporte = a.FechaReporte,
        PeriodoInicio = a.PeriodoInicio,
        PeriodoFin = a.PeriodoFin,
        ConsultasReportadas = a.ConsultasReportadas,
        VisitasReportadas = a.VisitasReportadas,
        ObjecionesFrecuentes = a.ObjecionesFrecuentes,
        AjustesRecomendados = a.AjustesRecomendados,
        CanalEnvio = a.CanalEnvio ?? "E",
        FechaCreacion = a.FechaCreacion,
    };
}

public class HttpRequerimientoService(ApiClient api) : IRequerimientoService
{
    public async Task<IReadOnlyList<RequerimientoDto>> ListarPorClienteAsync(long idCliente, CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<RequerimientoApi>>($"requerimientos/cliente/{idCliente}", ct);
        return lista is null ? [] : lista.Select(Mapear).ToList();
    }

    public async Task<RequerimientoDto> CrearAsync(RequerimientoDto requerimiento, CancellationToken ct = default)
    {
        var resp = await api.PostAsync<RequerimientoApi>("requerimientos", Cuerpo(requerimiento), ct);
        return resp is null ? requerimiento : Mapear(resp);
    }

    public async Task<RequerimientoDto> ActualizarAsync(RequerimientoDto requerimiento, CancellationToken ct = default)
    {
        var resp = await api.PutAsync<RequerimientoApi>($"requerimientos/{requerimiento.Id}", Cuerpo(requerimiento), ct);
        return resp is null ? requerimiento : Mapear(resp);
    }

    public async Task<RequerimientoDto> CambiarEstadoAsync(long id, string estado, CancellationToken ct = default)
    {
        var resp = await api.PostAsync<RequerimientoApi>($"requerimientos/{id}/estado", new { estado }, ct);
        return resp is null ? new RequerimientoDto { Id = id, Estado = estado } : Mapear(resp);
    }

    private static object Cuerpo(RequerimientoDto r) => new
    {
        idCliente = r.ClienteId,
        rubro = r.Rubro,
        tipoInmueble = r.TipoInmueble,
        rentaMin = r.RentaMin,
        rentaMax = r.RentaMax,
        moneda = r.Moneda,
        metrajeMin = r.MetrajeMin,
        metrajeMax = r.MetrajeMax,
        frenteMinimo = r.FrenteMinimo,
        estado = r.Estado,
        observaciones = r.Observaciones,
        distritos = r.Distritos,
    };

    private static RequerimientoDto Mapear(RequerimientoApi a) => new()
    {
        Id = a.Id ?? 0,
        ClienteId = a.IdCliente ?? 0,
        Rubro = a.Rubro ?? "",
        TipoInmueble = a.TipoInmueble,
        RentaMin = a.RentaMin,
        RentaMax = a.RentaMax,
        Moneda = string.IsNullOrWhiteSpace(a.Moneda) ? "PEN" : a.Moneda!,
        MetrajeMin = a.MetrajeMin,
        MetrajeMax = a.MetrajeMax,
        FrenteMinimo = a.FrenteMinimo,
        Estado = string.IsNullOrWhiteSpace(a.Estado) ? "ACTIVO" : a.Estado!,
        Observaciones = a.Observaciones,
        Distritos = a.Distritos?.ToList() ?? [],
        FechaActualizacion = a.FechaActualizacion,
    };
}

public class HttpLocalService(ApiClient api, HttpProspeccionService prospecciones) : ILocalService
{
    private readonly CacheRemoto<IReadOnlyList<LocalComercialDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<LocalComercialDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<LocalComercialDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public Task<LocalComercialDto?> ByIdAsync(long id, CancellationToken ct = default) =>
        ObtenerAsync(id, ct);

    public async Task<LocalComercialDto?> ObtenerAsync(long id, CancellationToken ct = default)
    {
        if (id <= 0)
            return null;

        var respuesta = await api.GetAsync<LocalApi>($"locales/{id}", ct);
        if (respuesta is not null)
            return Mapear(respuesta);

        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.Id == id);
    }

    public async Task<LocalComercialDto> AgregarAsync(LocalComercialDto local, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<LocalApi>("locales", Request(local), ct)
            ?? throw new InvalidOperationException("El API no devolvio el local registrado.");
        _cache.Invalidar();
        prospecciones.Invalidar();
        return Mapear(creado);
    }

    public async Task<LocalComercialDto> ActualizarAsync(LocalComercialDto local, CancellationToken ct = default)
    {
        var actualizado = await api.PutAsync<LocalApi>($"locales/{local.Id}", Request(local), ct)
            ?? throw new InvalidOperationException("El API no devolvio el local actualizado.");
        _cache.Invalidar();
        prospecciones.Invalidar();
        return Mapear(actualizado);
    }

    private static async Task<IReadOnlyList<LocalComercialDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var items = await api.GetTodasPaginasAsync<LocalApi>("locales", ct: ct);
        return items.Select(Mapear).ToList();
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
        Frente = item.Frente,
        Zonificacion = item.Zonificacion,
        AptoLicenciaFuncionamiento = item.AptoLicenciaFuncionamiento,
        CargaElectricaKw = item.CargaElectricaKw,
        NumeroEstacionamientos = item.NumeroEstacionamientos,
        CuotaMantenimiento = item.CuotaMantenimiento,
        IdDistrito = item.IdDistrito,
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
        local.Frente,
        local.Zonificacion,
        local.AptoLicenciaFuncionamiento,
        local.CargaElectricaKw,
        local.NumeroEstacionamientos,
        local.CuotaMantenimiento,
    };
}

public class HttpPrecioLocalService(ApiClient api) : IPrecioLocalService
{
    public async Task<IReadOnlyList<PrecioLocalDto>> ListarPorLocalAsync(long idLocal, CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<PrecioApi>>($"locales/{idLocal}/precios", ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

    public async Task<PrecioLocalDto> RegistrarAsync(long idLocal, string hito, string moneda, decimal monto,
        DateOnly fecha, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<PrecioApi>(
                $"locales/{idLocal}/precios",
                new { hito, moneda, monto, fecha },
                ct)
            ?? throw new InvalidOperationException("El API no devolvio el precio registrado.");
        return Mapear(creado);
    }

    private static PrecioLocalDto Mapear(PrecioApi item) => new()
    {
        Id = item.Id,
        IdLocal = item.IdLocal,
        Hito = item.Hito,
        HitoTexto = EnumCatalog.HitosPrecio.FirstOrDefault(o => o.Code == item.Hito)?.Label ?? item.Hito,
        Moneda = item.Moneda,
        Monto = item.Monto,
        MontoTexto = item.Monto.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        Fecha = item.Fecha,
        FechaTexto = item.Fecha?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
    };
}

public class HttpPublicacionService(ApiClient api) : IPublicacionService
{
    public async Task<IReadOnlyList<PublicacionDto>> ListarPorLocalAsync(long idLocal, CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<PublicacionApi>>($"locales/{idLocal}/publicaciones", ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

    public async Task<PublicacionDto> CrearAsync(long idLocal, PublicacionDto publicacion, CancellationToken ct = default)
    {
        var resp = await api.PostAsync<PublicacionApi>($"locales/{idLocal}/publicaciones", Cuerpo(publicacion), ct);
        return resp is null ? publicacion : Mapear(resp);
    }

    public async Task<PublicacionDto> ActualizarAsync(long idLocal, PublicacionDto publicacion, CancellationToken ct = default)
    {
        var resp = await api.PutAsync<PublicacionApi>(
            $"locales/{idLocal}/publicaciones/{publicacion.Id}", Cuerpo(publicacion), ct);
        return resp is null ? publicacion : Mapear(resp);
    }

    public async Task<PublicacionDto> CambiarEstadoAsync(long idLocal, long idPublicacion, string estado, CancellationToken ct = default)
    {
        var resp = await api.PostAsync<PublicacionApi>(
            $"locales/{idLocal}/publicaciones/{idPublicacion}/estado", new { estado }, ct);
        return resp is null ? new PublicacionDto { Id = idPublicacion, Estado = estado } : Mapear(resp);
    }

    private static object Cuerpo(PublicacionDto p) => new
    {
        canal = p.Canal,
        urlPublicacion = p.UrlPublicacion,
        rentaPublicada = p.RentaPublicada,
        moneda = p.Moneda,
        tituloAnuncio = p.TituloAnuncio,
        codigoOrigen = p.CodigoOrigen,
        estado = p.Estado,
    };

    private static PublicacionDto Mapear(PublicacionApi item) => new()
    {
        Id = item.Id,
        Canal = item.Canal,
        CanalTexto = EnumCatalog.CanalesPublicacion.FirstOrDefault(o => o.Code == item.Canal)?.Label ?? item.Canal,
        TituloAnuncio = item.TituloAnuncio,
        RentaPublicada = item.RentaPublicada,
        RentaTexto = item.RentaPublicada.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        Moneda = item.Moneda,
        Estado = item.Estado,
        EstadoTexto = EnumCatalog.EstadosPublicacion.FirstOrDefault(o => o.Code == item.Estado)?.Label ?? item.Estado,
        FechaPublicacion = item.FechaPublicacion,
        FechaPublicacionTexto = item.FechaPublicacion?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
        FechaBaja = item.FechaBaja,
        UrlPublicacion = item.UrlPublicacion,
        CodigoOrigen = item.CodigoOrigen,
    };
}

public class HttpProspeccionService(ApiClient api) : IProspeccionService
{
    private readonly CacheRemoto<IReadOnlyList<ProspeccionDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<ProspeccionDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<ProspeccionDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<ProspeccionDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.Id == id);
    }

    public async Task<ProspeccionDto?> ObtenerAsync(long id, CancellationToken ct = default)
    {
        if (id <= 0)
            return null;
        var remoto = await api.GetAsync<ProspeccionApi>($"prospecciones/{id}", ct);
        return remoto is null ? null : Mapear(remoto);
    }

    public async Task<PageResult<ProspeccionDto>> ListarPaginaAsync(
        int pagina, int tamano = 8, string? estado = null, string? distrito = null,
        string? query = null, CancellationToken ct = default, long? idCaptacion = null, long? idLocal = null,
        long? idAgente = null, long? idBrokerSupervisor = null)
    {
        var respuesta = await api.GetPaginaAsync<ProspeccionApi>(
            RutaProspecciones(estado, distrito, query, idCaptacion, idLocal, idAgente, idBrokerSupervisor), pagina, tamano, ct);
        return new PageResult<ProspeccionDto>(
            respuesta?.Items.Select(Mapear).ToList() ?? [],
            respuesta?.TotalRecords ?? 0,
            respuesta?.Page ?? pagina,
            respuesta?.PageSize ?? tamano);
    }

    public async Task<long> ContarAsync(string? estado = null, string? distrito = null, string? query = null, CancellationToken ct = default)
    {
        var respuesta = await api.GetPaginaAsync<ProspeccionApi>(
            RutaProspecciones(estado, distrito, query), 1, 1, ct);
        return respuesta?.TotalRecords ?? 0;
    }

    public async Task<long> ContarRecontactarAsync(int diasAviso = 7, CancellationToken ct = default)
    {
        var respuesta = await api.GetPaginaAsync<ProspeccionApi>(
            $"prospecciones/recontactar?dias={diasAviso}", 1, 1, ct);
        return respuesta?.TotalRecords ?? 0;
    }

    public async Task<PageResult<ProspeccionDto>> ListarRecontactarPaginaAsync(
        int pagina, int tamano = 8, int diasAviso = 7, CancellationToken ct = default)
    {
        var respuesta = await api.GetPaginaAsync<ProspeccionApi>(
            $"prospecciones/recontactar?dias={diasAviso}", pagina, tamano, ct);
        return new PageResult<ProspeccionDto>(
            respuesta?.Items.Select(Mapear).ToList() ?? [],
            respuesta?.TotalRecords ?? 0,
            respuesta?.Page ?? pagina,
            respuesta?.PageSize ?? tamano);
    }

    public Task<ProspeccionDto> ContactarAsync(long id, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/contactar", new { }, ct));

    public Task<ProspeccionDto> RegistrarReunionAsync(long id, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/reunion", new { }, ct));

    public Task<ProspeccionDto> EntregarPropuestaAsync(long id, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/propuesta", new { }, ct));

    public Task<ProspeccionDto> RegistrarSeguimientoAsync(long id, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/seguimiento", new { }, ct));

    public Task<ProspeccionDto> RechazarAsync(long id, string motivo, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/rechazar", new { motivo }, ct));

    public Task<ProspeccionDto> DescartarAsync(long id, string motivo, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/descartar", new { motivo }, ct));

    public Task<ProspeccionDto> CaptarAsync(long id, decimal comisionPactada, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>($"prospecciones/{id}/captar", new { comisionPactada }, ct));

    public Task<ProspeccionDto> MarcarCaptadoAsync(long id, string codigoCaptacion, CancellationToken ct = default) =>
        ActualizarAsync(() => api.PostAsync<ProspeccionApi>(
            $"prospecciones/{id}/marcar-captado",
            new { codigoCaptacion }, ct));

    internal void Invalidar() => _cache.Invalidar();

    private static async Task<IReadOnlyList<ProspeccionDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var items = await api.GetTodasPaginasAsync<ProspeccionApi>("prospecciones", ct: ct);
        return items.Select(Mapear).ToList();
    }

    private static string RutaProspecciones(string? estado, string? distrito, string? query,
        long? idCaptacion = null, long? idLocal = null, long? idAgente = null, long? idBrokerSupervisor = null)
    {
        var filtros = new List<string>();
        if (!string.IsNullOrWhiteSpace(estado)) filtros.Add($"estado={Uri.EscapeDataString(estado)}");
        if (!string.IsNullOrWhiteSpace(distrito)) filtros.Add($"distrito={Uri.EscapeDataString(distrito)}");
        if (!string.IsNullOrWhiteSpace(query)) filtros.Add($"q={Uri.EscapeDataString(query)}");
        if (idCaptacion is > 0) filtros.Add($"idCaptacion={idCaptacion}");
        if (idLocal is > 0) filtros.Add($"idLocal={idLocal}");
        if (idAgente is > 0) filtros.Add($"idAgente={idAgente}");
        if (idBrokerSupervisor is > 0) filtros.Add($"idBrokerSupervisor={idBrokerSupervisor}");
        return filtros.Count == 0 ? "prospecciones" : $"prospecciones?{string.Join("&", filtros)}";
    }

    private async Task<ProspeccionDto> ActualizarAsync(Func<Task<ProspeccionApi?>> operacion)
    {
        var respuesta = await operacion()
            ?? throw new InvalidOperationException("El API no devolvio la prospeccion actualizada.");
        var dto = Mapear(respuesta);
        _cache.Invalidar();
        return dto;
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
        AgenteId = item.IdAgente ?? 0,
        NombreAgente = item.AgenteNombre ?? "",
        Estado = item.Estado,
        ResultadoPropuesta = item.ResultadoPropuesta,
        FechaContactoTexto = FormatearFecha(item.FechaContacto),
        FechaReunionTexto = FormatearFecha(item.FechaReunion),
        FechaPropuestaTexto = FormatearFecha(item.FechaPropuesta),
        FechaRecontacto = item.FechaRecontacto,
        FechaRecontactoTexto = FormatearFecha(item.FechaRecontacto),
        Observaciones = item.Observaciones,
        CaptacionId = item.IdCaptacion ?? 0,
        CaptacionCodigo = item.CaptacionCodigo,
        Disponibilidad = item.Disponibilidad,
    };

    private static string? FormatearFecha(DateOnly? fecha) =>
        fecha?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture);
}

internal sealed record TareaApi(
    long Id,
    string? Tipo,
    string? EntidadTipo,
    long? EntidadId,
    string? EntidadCodigo,
    string? RutaResolver,
    string? Descripcion,
    string? Estado,
    string? Prioridad,
    DateTime? FechaProgramada,
    int? DiasSinAccion,
    DateTime? FechaVencimiento);

// Bandeja "Acciones Pendientes" del agente (Etapa 5): la trae ya reconciliada el backend.
public class HttpTareaService(ApiClient api) : ITareaService
{
    public async Task<IReadOnlyList<TareaDto>> BandejaAsync(CancellationToken ct = default)
    {
        var items = await api.GetAsync<List<TareaApi>>("tareas", ct);
        return items?.Select(Map).ToList() ?? [];
    }

    public async Task<PageResult<TareaDto>> BandejaPaginaAsync(
        int pagina = 1, int tamano = 5, CancellationToken ct = default)
    {
        var page = await api.GetPaginaAsync<TareaApi>("tareas/pendientes", pagina, tamano, ct);
        var items = page?.Items.Select(Map).ToList() ?? [];
        return new PageResult<TareaDto>(items, page?.TotalRecords ?? items.Count, page?.Page ?? pagina, page?.PageSize ?? tamano);
    }

    public Task CancelarAsync(long idTarea, CancellationToken ct = default) =>
        api.PostAsync<object>($"tareas/{idTarea}/cancelar", new { }, ct);

    private static TareaDto Map(TareaApi t) => new()
    {
        Id = t.Id,
        Tipo = t.Tipo ?? "",
        EntidadTipo = t.EntidadTipo ?? "",
        EntidadId = t.EntidadId ?? 0,
        EntidadCodigo = t.EntidadCodigo ?? "",
        RutaResolver = t.RutaResolver ?? "",
        Descripcion = t.Descripcion ?? "",
        Estado = t.Estado ?? "",
        Prioridad = t.Prioridad ?? "",
        FechaProgramada = t.FechaProgramada,
        DiasSinAccion = t.DiasSinAccion,
        FechaVencimiento = t.FechaVencimiento,
    };
}

// Dashboard agregado: una sola llamada trae indicadores + primera pagina de la bandeja.
public sealed record DashboardDto(IndicadoresDto Indicadores, PageResult<TareaDto> Bandeja);

public interface IDashboardService
{
    Task<DashboardDto> CargarAsync(string periodo, CancellationToken ct = default);
}

internal sealed record DashboardApi(IndicadoresDto? Indicadores, PaginaApi<TareaApi>? Bandeja);

public class HttpDashboardService(ApiClient api) : IDashboardService
{
    public async Task<DashboardDto> CargarAsync(string periodo, CancellationToken ct = default)
    {
        var periodoSeguro = string.IsNullOrWhiteSpace(periodo) ? "6m" : periodo.Trim();
        var resp = await api.GetAsync<DashboardApi>(
            $"dashboard?periodo={Uri.EscapeDataString(periodoSeguro)}", ct);
        var indicadores = resp?.Indicadores ?? IndicadoresDto.Vacio;
        var bandeja = resp?.Bandeja;
        var items = bandeja?.Items.Select(MapTarea).ToList() ?? new List<TareaDto>();
        var pagina = new PageResult<TareaDto>(
            items, bandeja?.TotalRecords ?? items.Count, bandeja?.Page ?? 1, bandeja?.PageSize ?? 5);
        return new DashboardDto(indicadores, pagina);
    }

    private static TareaDto MapTarea(TareaApi t) => new()
    {
        Id = t.Id,
        Tipo = t.Tipo ?? "",
        EntidadTipo = t.EntidadTipo ?? "",
        EntidadId = t.EntidadId ?? 0,
        EntidadCodigo = t.EntidadCodigo ?? "",
        RutaResolver = t.RutaResolver ?? "",
        Descripcion = t.Descripcion ?? "",
        Estado = t.Estado ?? "",
        Prioridad = t.Prioridad ?? "",
        FechaProgramada = t.FechaProgramada,
        DiasSinAccion = t.DiasSinAccion,
        FechaVencimiento = t.FechaVencimiento,
    };
}

public class HttpCaptacionService(ApiClient api) : ICaptacionService
{
    private readonly CacheRemoto<IReadOnlyList<CaptacionDto>> _captaciones = new(ct => CargarCaptaciones(api, ct));
    private readonly CacheRemoto<IReadOnlyList<BandejaCaptacionDto>> _bandeja = new(ct => CargarBandeja(api, ct));

    public Task<IReadOnlyList<CaptacionDto>> AllAsync(CancellationToken ct = default) =>
        _captaciones.ObtenerAsync(ct);

    // Recarga asincrona (sin bloquear el circuito) e invalida la cache: las
    // pantallas la invocan en OnInitializedAsync para ver siempre el estado
    // persistido por el otro rol (agente <-> broker) sin reconectar la sesion.
    public async Task<IReadOnlyList<CaptacionDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _captaciones.Invalidar();
        return await _captaciones.ObtenerAsync(ct);
    }

    public async Task<PageResult<CaptacionDto>> ListarReasignablesAsync(
        int pagina = 1, int tamano = 8, string? query = null, CancellationToken ct = default)
    {
        var ruta = "captaciones/reasignables";
        if (!string.IsNullOrWhiteSpace(query))
            ruta += $"?q={Uri.EscapeDataString(query.Trim())}";
        var page = await api.GetPaginaAsync<CaptacionApi>(ruta, pagina, tamano, ct);
        var items = page?.Items.Select(Mapear).ToList() ?? [];
        return new PageResult<CaptacionDto>(items, page?.TotalRecords ?? items.Count, page?.Page ?? pagina, page?.PageSize ?? tamano);
    }

    public async Task<IReadOnlyList<BandejaCaptacionDto>> RefrescarBandejaAsync(CancellationToken ct = default)
    {
        _bandeja.Invalidar();
        return await _bandeja.ObtenerAsync(ct);
    }

    public async Task<CaptacionDto?> ByCodigoAsync(string codigo, CancellationToken ct = default)
    {
        var lista = await _captaciones.ObtenerAsync(ct);
        var encontrada = lista.FirstOrDefault(item =>
            item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));
        return encontrada ?? await ObtenerPorCodigoAsync(codigo, ct);
    }

    // Variantes async reales: el formulario las usa para no bloquear el circuito de
    // Blazor Server (era lo que dejaba "colgado / sin cargar" el botón de guardar/reenviar).
    public async Task<CaptacionDto> AgregarAsync(CaptacionDto captacion, CancellationToken ct = default)
    {
        var creada = await api.PostAsync<CaptacionApi>("captaciones", Request(captacion), ct)
            ?? throw new InvalidOperationException("El API no devolvio la captacion registrada.");
        _captaciones.Invalidar();
        _bandeja.Invalidar();
        return Mapear(creada);
    }

    public async Task<CaptacionDto> ActualizarAsync(CaptacionDto captacion, CancellationToken ct = default)
    {
        var actualizada = await api.PutAsync<CaptacionApi>($"captaciones/{captacion.Id}", Request(captacion), ct)
            ?? throw new InvalidOperationException("El API no devolvio la captacion actualizada.");
        _captaciones.Invalidar();
        _bandeja.Invalidar();
        return Mapear(actualizada);
    }

    public async Task<CaptacionDto?> ObtenerPorCodigoAsync(string codigo, CancellationToken ct = default)
    {
        if (!string.IsNullOrWhiteSpace(codigo))
        {
            var remota = await api.GetAsync<CaptacionApi>($"captaciones/codigo/{Uri.EscapeDataString(codigo)}", ct);
            if (remota is not null)
                return Mapear(remota);
        }

        var captaciones = await _captaciones.ObtenerAsync(ct);
        var encontrada = captaciones.FirstOrDefault(item =>
            item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase));
        if (encontrada is null)
        {
            var bandeja = await _bandeja.ObtenerAsync(ct);
            encontrada = Mapear(bandeja.FirstOrDefault(item =>
                item.CodigoCaptacion.Equals(codigo, StringComparison.OrdinalIgnoreCase)));
        }

        var id = encontrada?.Id ?? 0;
        if (id <= 0)
            return encontrada;

        var respuesta = await api.GetAsync<CaptacionApi>($"captaciones/{id}", ct);
        return respuesta is null ? encontrada : Mapear(respuesta);
    }

    public async Task<CaptacionDto?> ObtenerPorIdAsync(long id, CancellationToken ct = default)
    {
        if (id <= 0)
            return null;
        var respuesta = await api.GetAsync<CaptacionApi>($"captaciones/{id}", ct);
        if (respuesta is null)
            return (await _captaciones.ObtenerAsync(ct)).FirstOrDefault(item => item.Id == id);
        return Mapear(respuesta);
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
        await api.PostAsync<CaptacionApi>(
            $"captaciones/{actual.Id}/decision",
            new { accion, observacion },
            ct);
        _captaciones.Invalidar();
        _bandeja.Invalidar();
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
        await api.PostAsync<CaptacionApi>(
            $"captaciones/{actual.Id}/reasignar",
            new { idAgenteNuevo = idNuevoAgente, motivo },
            ct);
        _captaciones.Invalidar();
        _bandeja.Invalidar();
    }

    public async Task CerrarAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("El motivo de cierre es obligatorio.");
        await api.PostAsync<CaptacionApi>(
            $"captaciones/{id}/cierre",
            new { motivo = motivo.Trim() },
            ct);
        _captaciones.Invalidar();
        _bandeja.Invalidar();
    }

    private static async Task<IReadOnlyList<CaptacionDto>> CargarCaptaciones(ApiClient api, CancellationToken ct)
    {
        var items = await api.GetTodasPaginasAsync<CaptacionApi>("captaciones", ct: ct);
        return items.Select(Mapear).ToList();
    }

    private static async Task<IReadOnlyList<BandejaCaptacionDto>> CargarBandeja(ApiClient api, CancellationToken ct)
    {
        var pagina = await api.GetPaginaAsync<CaptacionApi>("captaciones/pendientes", 1, 100, ct);
        return pagina?.Items.Select(MapearBandeja).ToList() ?? [];
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
    private readonly CacheRemoto<IReadOnlyList<AgenteDto>> _agentes = new(ct => CargarAgentes(api, ct));

    public Task<IReadOnlyList<AgenteDto>> AllAsync(CancellationToken ct = default) =>
        _agentes.ObtenerAsync(ct);

    public async Task<IReadOnlyList<AgenteDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _agentes.Invalidar();
        try
        {
            return await _agentes.ObtenerAsync(ct);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            // Resiliencia: la pantalla de agentes no debe romperse si el backend falla.
            return [];
        }
    }

    public async Task<AgenteDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var lista = await _agentes.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.Id == id);
    }

    // Agentes supervisados por un broker concreto (GET /brokers/{id}/agentes). Sirve para
    // acotar en la ficha del broker las solicitudes y la actividad de su equipo.
    public async Task<IReadOnlyList<AgenteDto>> AgentesDelBrokerAsync(long idBroker, CancellationToken ct = default)
    {
        if (idBroker <= 0) return Array.Empty<AgenteDto>();
        var lista = await api.GetAsync<List<AgenteApi>>($"brokers/{idBroker}/agentes", ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

    // Alta/edición REST reales (antes eran solo memoria y no persistían). La BL del backend
    // crea persona + usuario_interno + agente y lo asigna al broker supervisor en sesión.
    public async Task<AgenteDto> AgregarAsync(AgenteDto agente, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<AgenteApi>("agentes", Request(agente), ct)
            ?? throw new InvalidOperationException("El API no devolvio el agente registrado.");
        _agentes.Invalidar();
        return Mapear(creado);
    }

    public async Task<AgenteDto> ActualizarAsync(AgenteDto agente, CancellationToken ct = default)
    {
        var actualizado = await api.PutAsync<AgenteApi>($"agentes/{agente.Id}", Request(agente), ct)
            ?? throw new InvalidOperationException("El API no devolvio el agente actualizado.");
        _agentes.Invalidar();
        return Mapear(actualizado);
    }

    private static object Request(AgenteDto a) => new
    {
        nombre = a.Nombre,
        tipoPersona = a.TipoPersona.Contains("jur", StringComparison.OrdinalIgnoreCase) ? "J" : "N",
        tipoDocumento = string.IsNullOrWhiteSpace(a.TipoDocumento) ? "D" : a.TipoDocumento,
        a.NumeroDocumento,
        a.Telefono,
        correo = a.Email,
        a.Usuario,
        contrasena = a.ContrasenaTemporal,
        a.Zona,
        a.CodigoAgente,
        estado = a.EstadoAdministrativo.Equals("Inactivo", StringComparison.OrdinalIgnoreCase) ? "I" : "A",
        estadoOperativo = CodigoOperativo(a.EstadoOperativo),
    };

    private static string CodigoOperativo(string? texto) => texto switch
    {
        "Licencia" => "L",
        "No disponible" => "N",
        _ => "D",
    };

    public async Task<AgenteDto> DesactivarAsync(long id, CancellationToken ct = default)
    {
        var agente = await ByIdAsync(id, ct) ?? throw new InvalidOperationException("Agente no encontrado.");
        agente.EstadoAdministrativo = "Inactivo";
        agente.EstadoOperativo = "No disponible";
        return agente;
    }

    private static async Task<IReadOnlyList<AgenteDto>> CargarAgentes(ApiClient api, CancellationToken ct)
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
            CaptacionesActivas = item.CaptacionesActivas,
            OportunidadesActivas = item.OperacionesActivas,
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
    private readonly CacheRemoto<IReadOnlyList<OportunidadComercialDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<OportunidadComercialDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<OportunidadComercialDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<OportunidadComercialDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.Id == id);
    }

    public async Task<IReadOnlyList<OportunidadComercialDto>> ListarPorCaptacionAsync(long captacionId, CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<OportunidadApi>($"oportunidades?idCaptacion={captacionId}", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    public async Task<IReadOnlyList<OportunidadComercialDto>> ListarPorClienteAsync(long clienteId, CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<OportunidadApi>($"oportunidades?idCliente={clienteId}", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
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
                idPublicacionOrigen = request.PublicacionOrigenId > 0 ? request.PublicacionOrigenId : (long?)null,
            },
            ct);
        var dto = creada is null
            ? (await RefrescarAsync(ct)).FirstOrDefault(item =>
                item.ClienteId == request.ClienteId && item.CaptacionId == request.CaptacionId)
            : Mapear(creada);
        if (dto is null)
            throw new InvalidOperationException("La oportunidad se registro, pero no se pudo refrescar el seguimiento.");
        if (creada is not null)
            _cache.Invalidar();
        return dto;
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
        _cache.Invalidar();
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
        _cache.Invalidar();
        return dto;
    }

    internal void Invalidar() => _cache.Invalidar();

    private static async Task<IReadOnlyList<OportunidadComercialDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var items = await api.GetTodasPaginasAsync<OportunidadApi>("oportunidades", ct: ct);
        return items.Select(Mapear).ToList();
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
        AgenteResponsableId = item.IdAgente,
        NombreAgenteResponsable = item.AgenteNombre,
        Estado = Codigos.EstadoOportunidad(item.Estado),
        FechaRegistroTexto = item.FechaRegistro.ToString("dd MMM yyyy", CultureInfo.InvariantCulture),
        FechaActualizacion = item.FechaActualizacion ?? item.FechaRegistro,
        MotivoCierre = item.MotivoCierre ?? "",
        Observaciones = item.Observaciones ?? "",
        FechaCierreTexto = item.FechaCierre?.ToString("dd MMM yyyy HH:mm", CultureInfo.InvariantCulture) ?? "",
        PublicacionOrigenId = item.IdPublicacionOrigen ?? 0,
    };
}

public class HttpSolicitudService(ApiClient api, HttpOportunidadService oportunidades) : ISolicitudService
{
    private readonly CacheRemoto<IReadOnlyList<SolicitudAlquilerDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<SolicitudAlquilerDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<SolicitudAlquilerDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<SolicitudAlquilerDto?> ObtenerAsync(string idOCodigo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(idOCodigo))
            return null;
        var ruta = long.TryParse(idOCodigo.Trim(), out var id) && id > 0
            ? $"solicitudes/{id}"
            : $"solicitudes/codigo/{Uri.EscapeDataString(idOCodigo.Trim())}";
        var item = await api.GetAsync<SolicitudApi>(ruta, ct);
        return item is null ? null : Mapear(item);
    }

    public async Task<PageResult<SolicitudAlquilerDto>> ListarPaginaAsync(
        int pagina = 1, int tamano = 20, CancellationToken ct = default)
    {
        var page = await api.GetPaginaAsync<SolicitudApi>("solicitudes", pagina, tamano, ct);
        var items = page?.Items.Select(Mapear).ToList() ?? [];
        return new PageResult<SolicitudAlquilerDto>(items, page?.TotalRecords ?? items.Count, page?.Page ?? pagina, page?.PageSize ?? tamano);
    }

    public async Task<IReadOnlyList<SolicitudAlquilerDto>> ListarPorCaptacionAsync(long captacionId, CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<SolicitudApi>($"solicitudes?idCaptacion={captacionId}", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    public async Task<IReadOnlyList<SolicitudAlquilerDto>> ListarPorOportunidadAsync(long oportunidadId, CancellationToken ct = default)
    {
        var pagina = await api.GetPaginaAsync<SolicitudApi>($"solicitudes?idOportunidad={oportunidadId}", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    public async Task<SolicitudAlquilerDto?> ByCodigoAsync(string codigo, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.CodigoSolicitud.Equals(codigo, StringComparison.OrdinalIgnoreCase));
    }

    public async Task<SolicitudAlquilerDto> AgregarAsync(
        SolicitudFormRequest request,
        CancellationToken ct = default)
    {
        var creada = await api.PostAsync<SolicitudApi>("solicitudes", new
            {
                idOportunidad = request.OportunidadId,
                montoPropuesto = request.MontoPropuesto,
                plazoMeses = request.PlazoMeses,
                fechaInicio = request.FechaInicio,
                formaPago = request.FormaPago,
                mesesGarantia = request.MesesGarantia,
                mesesAdelanto = request.MesesAdelanto,
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
            _cache.Invalidar();
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
        _cache.Invalidar();
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
        return await api.GetAsync<SolicitudApi>($"solicitudes/{id}", ct);
    }

    public async Task<SolicitudAlquilerDto> ReenviarAEvaluacionAsync(
        string codigoSolicitud, CancellationToken ct = default)
    {
        var solicitud = await ObtenerAsync(codigoSolicitud, ct)
            ?? throw new InvalidOperationException("Solicitud no encontrada.");
        var respuesta = await api.PostAsync<SolicitudApi>(
            $"solicitudes/{solicitud.Id}/reenviar", new { }, ct);
        if (respuesta is null)
        {
            var actualizada = await BuscarSolicitudRegistradaAsync(solicitud.OportunidadId, ct);
            if (actualizada is not null)
            {
                _cache.Invalidar();
                return actualizada;
            }
            throw new InvalidOperationException("La solicitud se envio, pero no se pudo refrescar su estado.");
        }
        var dto = Mapear(respuesta);
        _cache.Invalidar();
        return dto;
    }

    public async Task<EvaluacionSolicitudDto> EvaluarAsync(
        string codigoSolicitud,
        EvaluacionSolicitudDto evaluacion,
        CancellationToken ct = default)
    {
        var solicitud = await ObtenerAsync(codigoSolicitud, ct)
            ?? throw new InvalidOperationException("Solicitud no encontrada.");
        var respuesta = await api.PostAsync<EvaluacionApi>("evaluaciones", new
            {
                idSolicitud = solicitud.Id,
                tipoEvaluacion = evaluacion.TipoEvaluacion,
                resultado = evaluacion.Resultado,
                observaciones = evaluacion.Observaciones,
            },
            ct);

        _cache.Invalidar();
        return respuesta is null ? evaluacion : Mapear(respuesta);
    }

    private static async Task<IReadOnlyList<SolicitudAlquilerDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var items = await api.GetTodasPaginasAsync<SolicitudApi>("solicitudes", ct: ct);
        return items.Select(Mapear).ToList();
    }

    private static SolicitudAlquilerDto Mapear(SolicitudApi item) => new()
    {
        Id = item.Id,
        CodigoSolicitud = item.CodigoSolicitud,
        CodigoOperacion = item.CodigoOportunidad ?? $"OPO-{item.IdOportunidad:0000}",
        OportunidadId = item.IdOportunidad,
        ClienteId = item.IdCliente ?? 0,
        CaptacionId = item.IdCaptacion ?? 0,
        AgenteResponsableId = item.IdAgente,
        AgenteNombre = item.AgenteNombre ?? "",
        ClienteNombre = item.ClienteNombre ?? "",
        DireccionLocal = item.DireccionLocal ?? "",
        DistritoLocal = item.DistritoLocal ?? "",
        MontoMensual = item.MontoPropuesto,
        MontoMensualTexto = item.MontoPropuesto.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        PlazoMeses = item.PlazoMeses ?? ParseMeses(item.PlazoTentativo),
        PlazoTentativo = item.PlazoTentativo ?? "",
        FechaInicioTexto = item.FechaInicio?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
        FormaPago = item.FormaPago switch
        {
            "TRANSFERENCIA" => "Transferencia",
            "DEPOSITO_BANCARIO" => "Depósito bancario",
            "EFECTIVO" => "Efectivo",
            "CHEQUE" => "Cheque",
            "OTRO" => "Otro",
            _ => item.FormaPago ?? "",
        },
        MesesGarantia = item.MesesGarantia,
        MesesAdelanto = item.MesesAdelanto,
        Observaciones = item.Observaciones ?? "",
        DocumentosTexto = $"{item.DocumentosEntregados}/{item.DocumentosRequeridos}",
        PorcentajeDocumentos = item.DocumentosRequeridos > 0
            ? (int)Math.Round(item.DocumentosEntregados * 100.0 / item.DocumentosRequeridos)
            : 0,
        FechaRegistro = item.FechaRegistro,
        FechaRegistroTexto = item.FechaRegistro?.ToString("dd MMM", CultureInfo.InvariantCulture) ?? "",
        Estado = Codigos.EstadoSolicitud(item.Estado),
        FechaActualizacionEstado = item.FechaActualizacionEstado,
    };

    public async Task<IReadOnlyList<EvaluacionSolicitudDto>> ListarEvaluacionesAsync(
        long idSolicitud, CancellationToken ct = default)
    {
        if (idSolicitud <= 0)
            return Array.Empty<EvaluacionSolicitudDto>();
        var lista = await api.GetAsync<List<EvaluacionApi>>($"solicitudes/{idSolicitud}/evaluaciones", ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

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

public class HttpContratoService(ApiClient api) : IContratoService
{
    private readonly CacheRemoto<IReadOnlyList<ContratoAlquilerDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<ContratoAlquilerDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<ContratoAlquilerDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<ContratoAlquilerDto?> ByOportunidadAsync(long oportunidadId, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.OportunidadId == oportunidadId);
    }

    public async Task<ContratoAlquilerDto?> ObtenerPorOportunidadAsync(
        long oportunidadId, CancellationToken ct = default)
    {
        if (oportunidadId <= 0)
            return null;
        try
        {
            var item = await api.GetAsync<ContratoApi>($"contratos/oportunidad/{oportunidadId}", ct);
            return item is null ? null : Mapear(item);
        }
        catch
        {
            return null;
        }
    }

    public async Task<ContratoAlquilerDto> RegistrarAsync(
        ContratoFormRequest request, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<ContratoApi>("contratos", new
            {
                idSolicitud = request.SolicitudId,
                fechaCierre = request.FechaCierre,
                estadoContrato = request.EstadoContrato,
                incidencias = request.Incidencias,
            },
            ct)
            ?? throw new InvalidOperationException("El API no devolvio el contrato registrado.");
        _cache.Invalidar();
        return Mapear(creado);
    }

    public async Task<ContratoAlquilerDto> AsignarComisionAsync(
        long idContrato, decimal montoAgente, CancellationToken ct = default)
    {
        var actualizado = await api.PostAsync<ContratoApi>(
                $"contratos/{idContrato}/comision/asignar", new { montoAgente }, ct)
            ?? throw new InvalidOperationException("El API no devolvio el contrato actualizado.");
        _cache.Invalidar();
        return Mapear(actualizado);
    }

    public async Task<ContratoAlquilerDto> RegistrarCobroAsync(
        long idContrato, string estado, DateOnly? fechaCobro, string? formaPago, CancellationToken ct = default)
    {
        var actualizado = await api.PostAsync<ContratoApi>(
                $"contratos/{idContrato}/comision/cobro",
                new { estado, fechaCobro, formaPago }, ct)
            ?? throw new InvalidOperationException("El API no devolvio el contrato actualizado.");
        _cache.Invalidar();
        return Mapear(actualizado);
    }

    private static async Task<IReadOnlyList<ContratoAlquilerDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var items = await api.GetTodasPaginasAsync<ContratoApi>("contratos", ct: ct);
        return items.Select(Mapear).ToList();
    }

    private static ContratoAlquilerDto Mapear(ContratoApi item) => new()
    {
        Id = item.Id,
        OportunidadId = item.IdOportunidad ?? 0,
        CodigoOportunidad = item.CodigoOportunidad ?? "",
        CodigoSolicitud = item.CodigoSolicitud ?? "",
        ClienteNombre = item.ClienteNombre ?? "",
        DireccionLocal = item.DireccionLocal ?? "",
        DistritoLocal = item.DistritoLocal ?? "",
        CodigoCaptacion = item.CodigoCaptacion ?? "",
        AgenteNombre = item.AgenteNombre ?? "",
        RentaMensual = item.RentaMensual,
        RentaMensualTexto = item.RentaMensual.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        Moneda = item.Moneda ?? "USD",
        PlazoMeses = item.PlazoContratoMeses ?? 0,
        ComisionGenerada = item.ComisionGenerada,
        ComisionGeneradaTexto = item.ComisionGenerada.ToString("N0", CultureInfo.GetCultureInfo("es-PE")),
        FechaInicioTexto = item.FechaInicioContrato?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
        FechaFinTexto = item.FechaFinContrato?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
        FechaCierre = item.FechaCierre,
        FechaCierreTexto = item.FechaCierre?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
        Estado = Codigos.EstadoContrato(item.EstadoContrato),
        ComisionEstado = item.ComisionEstado switch
        {
            "PENDIENTE" => "Pendiente",
            "PARCIAL" => "Parcial",
            "COBRADA" => "Cobrada",
            "ANULADA" => "Anulada",
            _ => item.ComisionEstado ?? "",
        },
        Incidencias = item.Incidencias ?? "",
        IdComision = item.IdComision ?? 0,
        AgenteId = item.AgenteId ?? 0,
        PropietarioId = item.PropietarioId ?? 0,
        PropietarioNombre = item.PropietarioNombre ?? "",
        MontoAgente = item.MontoAgente,
        MontoEmpresa = item.MontoEmpresa,
        MontoAgenteTexto = item.MontoAgente?.ToString("N0", CultureInfo.GetCultureInfo("es-PE")) ?? "",
        MontoEmpresaTexto = item.MontoEmpresa?.ToString("N0", CultureInfo.GetCultureInfo("es-PE")) ?? "",
        FormaPago = item.FormaPago switch
        {
            "TRANSFERENCIA" => "Transferencia",
            "DEPOSITO_BANCARIO" => "Depósito bancario",
            "EFECTIVO" => "Efectivo",
            "CHEQUE" => "Cheque",
            "OTRO" => "Otro",
            _ => item.FormaPago ?? "",
        },
        FechaCobroTexto = item.FechaCobro?.ToString("dd MMM yyyy", CultureInfo.InvariantCulture) ?? "",
    };
}

public class HttpVisitaService(ApiClient api, HttpOportunidadService oportunidades) : IVisitaService
{
    private readonly CacheRemoto<IReadOnlyList<VisitaDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<VisitaDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<VisitaDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<VisitaDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item => item.Id == id);
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
        _cache.Invalidar();
        return dto;
    }

    public Task<VisitaDto> ReprogramarAsync(long id, string fechaTexto, string horaTexto, CancellationToken ct = default) =>
        ActualizarAsync(id, () => api.PatchAsync<VisitaApi>($"visitas/{id}/reprogramar", new
        {
            fechaVisita = ParseFecha(fechaTexto),
            horaVisita = ParseHora(horaTexto),
        }, ct), ct);

    public Task<VisitaDto> CancelarAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar el motivo de la cancelacion.");
        return ActualizarAsync(id, () => api.PatchAsync<VisitaApi>(
            $"visitas/{id}/cancelar",
            new { motivo = motivo.Trim() },
            ct), ct);
    }

    public Task<VisitaDto> MarcarRealizadaAsync(long id, CancellationToken ct = default) =>
        ActualizarAsync(id, () => api.PatchAsync<VisitaApi>($"visitas/{id}/realizar", new { }, ct), ct);

    public Task<VisitaDto> MarcarNoRealizadaAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar por que la visita no se realizo.");
        return ActualizarAsync(id, () => api.PatchAsync<VisitaApi>(
            $"visitas/{id}/no-realizada",
            new { motivo = motivo.Trim() },
            ct), ct);
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
            ct), ct);
        if (request.Resultado is "N" or "D")
            oportunidades.Invalidar();
        return actualizada;
    }

    private static async Task<IReadOnlyList<VisitaDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var pagina = await api.GetPaginaAsync<VisitaApi>("visitas", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private async Task<VisitaDto> ActualizarAsync(long id, Func<Task<VisitaApi?>> operacion, CancellationToken ct = default)
    {
        var respuesta = await operacion();
        var dto = respuesta is null
            ? (await RefrescarAsync(ct)).FirstOrDefault(item => item.Id == id)
            : Mapear(respuesta);
        if (dto is null)
            throw new InvalidOperationException("La visita se actualizo, pero no se pudo refrescar su estado.");
        _cache.Invalidar();
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

public class HttpDocumentoSolicitudService(ApiClient api) : IDocumentoSolicitudService
{
    public async Task<IReadOnlyList<DocumentoSolicitudDto>> ListarAsync(
        long idSolicitud, CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<DocumentoSolicitudApi>>(
            $"solicitudes/{idSolicitud}/documentos", ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

    public async Task<DocumentoSolicitudDto> SubirAsync(
        long idSolicitud, string tipoDocumento, string nombreArchivo, byte[] contenido,
        CancellationToken ct = default)
    {
        // Un POST con cuerpo grande rompe el HttpClient de .NET 10 contra GlassFish (los POST
        // chicos sí funcionan). Como es entorno local en el mismo equipo, el archivo se escribe
        // en una carpeta temporal compartida y al backend solo se le manda el NOMBRE (POST
        // chico); el backend lo lee de ahi, lo guarda en el almacen y borra el temporal.
        var carpeta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "controllocal", "uploads-tmp");
        Directory.CreateDirectory(carpeta);
        var temporal = Guid.NewGuid().ToString("N") + Path.GetExtension(nombreArchivo);
        var rutaTemporal = Path.Combine(carpeta, temporal);
        await File.WriteAllBytesAsync(rutaTemporal, contenido, ct);
        ControlLocal.Web.Services.CrashLog.Breadcrumb($"SubirAsync(handoff) temp={temporal} bytes={contenido.Length}");
        try
        {
            var creado = await api.PostAsync<DocumentoSolicitudApi>(
                $"solicitudes/{idSolicitud}/documentos/local", new
                {
                    tipoDocumento,
                    nombreArchivo,
                    archivoTemporal = temporal,
                }, ct)
                ?? throw new InvalidOperationException("El API no devolvio el documento registrado.");
            ControlLocal.Web.Services.CrashLog.Breadcrumb("SubirAsync(handoff) OK");
            return Mapear(creado);
        }
        finally
        {
            // Red de seguridad: el backend ya lo borra; esto cubre el caso de que el POST falle.
            try { if (File.Exists(rutaTemporal)) File.Delete(rutaTemporal); } catch { /* best-effort */ }
        }
    }

    public async Task<DocumentoSolicitudDto> RevisarAsync(
        long idSolicitud, long idDocumento, string resultado, string? observaciones,
        CancellationToken ct = default)
    {
        var revisado = await api.PatchAsync<DocumentoSolicitudApi>(
            $"solicitudes/{idSolicitud}/documentos/{idDocumento}/revisar",
            new { resultado, observaciones }, ct)
            ?? throw new InvalidOperationException("El API no devolvio el documento revisado.");
        return Mapear(revisado);
    }

    public async Task<IReadOnlyList<DocumentoSolicitudDto>> ConformarTodosAsync(
        long idSolicitud, CancellationToken ct = default)
    {
        var lista = await api.PatchAsync<List<DocumentoSolicitudApi>>(
            $"solicitudes/{idSolicitud}/documentos/conformar", new { }, ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

    public async Task<EstadoAlmacen> EstadoAlmacenAsync(CancellationToken ct = default)
    {
        var estado = await api.GetAsync<EstadoAlmacen>("documentos/salud", ct);
        return estado ?? EstadoAlmacen.Falla("Almacén", "El backend no devolvió el estado del almacén.");
    }

    private static DocumentoSolicitudDto Mapear(DocumentoSolicitudApi item) => new()
    {
        Id = item.Id,
        SolicitudId = item.IdSolicitud ?? 0,
        TipoDocumento = item.TipoDocumento ?? "",
        TipoNombre = item.TipoNombre ?? "",
        NombreArchivo = item.NombreArchivo ?? "",
        RutaArchivo = item.RutaArchivo,
        FechaEntregaTexto = item.FechaEntrega?.ToString("dd MMM yyyy", CultureInfo.GetCultureInfo("es-PE")) ?? "",
        Estado = Codigos.EstadoDocumento(item.Estado),
        ResultadoRevision = Codigos.ResultadoRevisionDocumento(item.ResultadoRevision),
        Observaciones = item.Observaciones ?? "",
    };
}

public class HttpInteraccionService(ApiClient api) : IInteraccionService
{
    private readonly CacheRemoto<IReadOnlyList<InteraccionComercialDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<InteraccionComercialDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<InteraccionComercialDto?> ByIdAsync(long id, CancellationToken ct = default)
    {
        var item = await api.GetAsync<InteraccionApi>($"interacciones/{id}", ct);
        return item is null ? null : Mapear(item);
    }

    public async Task<PageResult<InteraccionComercialDto>> ListarPaginaAsync(
        int pagina,
        int tamano = 8,
        string? grupo = null,
        string? resultado = null,
        string? canal = null,
        string? query = null,
        CancellationToken ct = default)
    {
        var ruta = RutaInteracciones(grupo, resultado, canal, query);
        var respuesta = await api.GetPaginaAsync<InteraccionApi>(ruta, pagina, tamano, ct);
        return new PageResult<InteraccionComercialDto>(
            respuesta?.Items.Select(Mapear).ToList() ?? [],
            respuesta?.TotalRecords ?? 0,
            respuesta?.Page ?? pagina,
            respuesta?.PageSize ?? tamano);
    }

    public async Task<InteraccionComercialDto> AgregarAsync(InteraccionFormRequest request, CancellationToken ct = default)
    {
        var creada = await api.PostAsync<InteraccionApi>("interacciones", Cuerpo(request), ct)
            ?? throw new InvalidOperationException("El API no devolvio la interaccion registrada.");
        _cache.Invalidar();
        return Mapear(creada);
    }

    public Task<IReadOnlyList<InteraccionComercialDto>> ListarPorOportunidadAsync(long oportunidadId, CancellationToken ct = default) =>
        ListarPorContextoAsync("OPORTUNIDAD", "idOportunidad", oportunidadId, ct);

    public Task<IReadOnlyList<InteraccionComercialDto>> ListarPorProspeccionAsync(long prospeccionId, CancellationToken ct = default) =>
        ListarPorContextoAsync("PROSPECCION", "idProspeccion", prospeccionId, ct);

    public Task<IReadOnlyList<InteraccionComercialDto>> ListarPorCaptacionAsync(long captacionId, CancellationToken ct = default) =>
        ListarPorContextoAsync("CAPTACION", "idCaptacion", captacionId, ct);

    public Task<IReadOnlyList<InteraccionComercialDto>> ListarPorClienteAsync(long clienteId, CancellationToken ct = default) =>
        ListarPorContextoAsync("CLIENTE", "idCliente", clienteId, ct);

    // El backend remoto (RDS) tarda en respondes en el primer GET tras un periodo inactivo
    // y a veces dispara TaskCanceledException antes de que termine. Hacemos un reintento
    // unico con un pequeno backoff: si el segundo intento responde, el usuario no ve la
    // pagina con "0 interacciones" sin saber por que.
    private async Task<IReadOnlyList<InteraccionComercialDto>> ListarPorContextoAsync(
        string contexto, string parametroId, long valorId, CancellationToken ct)
    {
        var ruta = $"interacciones?contexto={contexto}&{parametroId}={valorId}";
        try
        {
            var pagina = await api.GetPaginaAsync<InteraccionApi>(ruta, 1, 100, ct);
            return pagina?.Items.Select(Mapear).ToList() ?? [];
        }
        catch (TaskCanceledException) when (!ct.IsCancellationRequested)
        {
            // Timeout del HttpClient (no cancelacion del usuario): un reintento corto
            // suele bastar porque la 2da conexion ya esta caliente.
            await Task.Delay(TimeSpan.FromMilliseconds(400), ct);
            var pagina = await api.GetPaginaAsync<InteraccionApi>(ruta, 1, 100, ct);
            return pagina?.Items.Select(Mapear).ToList() ?? [];
        }
    }

    public async Task<InteraccionComercialDto> ActualizarAsync(long id, string? resultado = null, string? observaciones = null, CancellationToken ct = default)
    {
        var actualizada = await api.PutAsync<InteraccionApi>($"interacciones/{id}", new
            {
                resultado,
                observaciones,
            }, ct)
            ?? throw new InvalidOperationException("El API no devolvio la interaccion actualizada.");
        _cache.Invalidar();
        return Mapear(actualizada);
    }

    private static async Task<IReadOnlyList<InteraccionComercialDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var pagina = await api.GetPaginaAsync<InteraccionApi>("interacciones", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private static string RutaInteracciones(string? grupo, string? resultado, string? canal, string? query)
    {
        var filtros = new List<string>();
        if (!string.IsNullOrWhiteSpace(grupo) && !string.Equals(grupo, "Todas", StringComparison.OrdinalIgnoreCase))
        {
            var grupoApi = grupo.Contains("propietario", StringComparison.OrdinalIgnoreCase) ? "PROPIETARIO" : "CLIENTE";
            filtros.Add($"grupo={Uri.EscapeDataString(grupoApi)}");
        }
        if (!string.IsNullOrWhiteSpace(resultado)) filtros.Add($"resultado={Uri.EscapeDataString(resultado)}");
        if (!string.IsNullOrWhiteSpace(canal)) filtros.Add($"canal={Uri.EscapeDataString(canal)}");
        if (!string.IsNullOrWhiteSpace(query)) filtros.Add($"q={Uri.EscapeDataString(query)}");
        return filtros.Count == 0 ? "interacciones" : $"interacciones?{string.Join("&", filtros)}";
    }

    private static object Cuerpo(InteraccionFormRequest request) => new
    {
        contexto = string.IsNullOrWhiteSpace(request.Contexto) ? "OPORTUNIDAD" : request.Contexto,
        idOportunidad = request.OportunidadId > 0 ? request.OportunidadId : (long?)null,
        idProspeccion = request.ProspeccionId > 0 ? request.ProspeccionId : (long?)null,
        idCaptacion = request.CaptacionId > 0 ? request.CaptacionId : (long?)null,
        idCliente = request.ClienteId > 0 ? request.ClienteId : (long?)null,
        canalContacto = request.CanalContacto,
        resultado = request.Resultado,
        observaciones = request.Observaciones,
        transcripcionNota = request.TranscripcionNota,
    };

    private static InteraccionComercialDto Mapear(InteraccionApi item) => new()
    {
        Id = item.Id,
        Contexto = item.Contexto ?? "OPORTUNIDAD",
        OportunidadId = item.IdOportunidad ?? 0,
        ProspeccionId = item.IdProspeccion ?? 0,
        CaptacionId = item.IdCaptacion ?? 0,
        ClienteId = item.IdCliente ?? 0,
        PropietarioId = item.IdPropietario ?? 0,
        CodigoProspeccion = item.CodigoProspeccion ?? "",
        FechaHora = item.FechaHora,
        FechaHoraTexto = item.FechaHora?.ToString("dd MMM yyyy · HH:mm", CultureInfo.GetCultureInfo("es-PE")) ?? "",
        CanalContacto = item.CanalContacto ?? "",
        Resultado = item.Resultado ?? "",
        Observaciones = item.Observaciones ?? "",
        TranscripcionNota = item.TranscripcionNota ?? "",
        ClienteNombre = item.ClienteNombre ?? "",
        PropietarioNombre = item.PropietarioNombre ?? "",
        PersonaTipo = item.PersonaTipo ?? "",
        PersonaNombre = item.PersonaNombre ?? "",
        CaptacionCodigo = item.CodigoCaptacion ?? "",
        NombreAgenteResponsable = item.AgenteNombre ?? "",
    };
}

public class HttpReasignacionCaptacionService(ApiClient api) : IReasignacionCaptacionService
{
    public async Task<IReadOnlyList<ReasignacionCaptacionDto>> HistorialAsync(CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<ReasignacionCaptacionApi>>("captaciones/reasignaciones", ct);
        return lista?.Select(Mapear).ToList() ?? [];
    }

    private static ReasignacionCaptacionDto Mapear(ReasignacionCaptacionApi item) => new()
    {
        IdReasignacion = item.IdReasignacion,
        CaptacionId = item.IdCaptacion ?? 0,
        CodigoCaptacion = item.CodigoCaptacion ?? "",
        DireccionLocal = item.DireccionLocal ?? "",
        AgenteAnteriorId = item.IdAgenteAnterior ?? 0,
        AgenteAnteriorNombre = item.AgenteAnteriorNombre ?? "",
        AgenteNuevoId = item.IdAgenteNuevo ?? 0,
        AgenteNuevoNombre = item.AgenteNuevoNombre ?? "",
        BrokerResponsableId = item.IdBroker ?? 0,
        BrokerResponsableNombre = item.BrokerNombre ?? "",
        FechaCambio = item.FechaCambio,
        FechaCambioTexto = item.FechaCambio?.ToString("dd MMM yyyy · HH:mm", CultureInfo.GetCultureInfo("es-PE")) ?? "",
        Motivo = item.Motivo ?? "",
    };
}

public class HttpBrokerService(ApiClient api) : IBrokerService
{
    private readonly CacheRemoto<IReadOnlyList<BrokerDto>> _cache = new(ct => CargarAsync(api, ct));

    public Task<IReadOnlyList<BrokerDto>> AllAsync(CancellationToken ct = default) =>
        _cache.ObtenerAsync(ct);

    public async Task<IReadOnlyList<BrokerDto>> RefrescarAsync(CancellationToken ct = default)
    {
        _cache.Invalidar();
        return await _cache.ObtenerAsync(ct);
    }

    public async Task<BrokerDto?> ByCodigoAsync(string codigoBroker, CancellationToken ct = default)
    {
        var lista = await _cache.ObtenerAsync(ct);
        return lista.FirstOrDefault(item =>
            item.CodigoBroker.Equals(codigoBroker, StringComparison.OrdinalIgnoreCase));
    }

    public async Task<BrokerDto> AgregarAsync(BrokerDto broker, CancellationToken ct = default)
    {
        var creado = await api.PostAsync<BrokerApi>("brokers", Request(broker), ct)
            ?? throw new InvalidOperationException("El API no devolvio el broker registrado.");
        _cache.Invalidar();
        return Mapear(creado);
    }

    public async Task<BrokerDto> ActualizarAsync(BrokerDto broker, CancellationToken ct = default)
    {
        var actualizado = await api.PutAsync<BrokerApi>($"brokers/{broker.Id}", Request(broker), ct)
            ?? throw new InvalidOperationException("El API no devolvio el broker actualizado.");
        _cache.Invalidar();
        return Mapear(actualizado);
    }

    private static async Task<IReadOnlyList<BrokerDto>> CargarAsync(ApiClient api, CancellationToken ct)
    {
        var pagina = await api.GetPaginaAsync<BrokerApi>("brokers", 1, 100, ct);
        return pagina?.Items.Select(Mapear).ToList() ?? [];
    }

    private static BrokerDto Mapear(BrokerApi item)
    {
        var nombre = item.Nombre ?? "";
        return new BrokerDto
        {
            Id = item.Id,
            CodigoBroker = item.CodigoBroker ?? "",
            Iniciales = Iniciales(nombre),
            Nombre = nombre,
            TipoPersona = item.TipoPersona == "J" ? "Persona juridica" : "Persona natural",
            Email = item.Correo ?? "",
            TipoDocumento = string.IsNullOrWhiteSpace(item.TipoDocumento) ? "D" : item.TipoDocumento!,
            NumeroDocumento = item.NumeroDocumento ?? "",
            Telefono = item.Telefono ?? "",
            Usuario = item.Usuario ?? "",
            Zona = item.Zona ?? "",
            TipoBroker = item.EsAdministrador ? "Broker administrador" : "Broker supervisor",
            FechaDesignacion = item.FechaDesignacion,
            FechaDesignacionTexto = item.FechaDesignacion?.ToString("dd MMM yyyy", CultureInfo.GetCultureInfo("es-PE")) ?? "",
            AgentesACargo = item.AgentesACargo,
            CaptacionesActivas = 0,
            EstadoAdministrativo = item.EstadoAdministrativo == "I" ? "Inactivo" : "Activo",
            EsAdministrador = item.EsAdministrador,
        };
    }

    private static object Request(BrokerDto broker) => new
    {
        nombre = broker.Nombre,
        tipoPersona = broker.TipoPersona.Contains("jur", StringComparison.OrdinalIgnoreCase) ? "J" : "N",
        tipoDocumento = string.IsNullOrWhiteSpace(broker.TipoDocumento) ? "D" : broker.TipoDocumento,
        broker.NumeroDocumento,
        broker.Telefono,
        correo = broker.Email,
        broker.Usuario,
        contrasena = broker.ContrasenaTemporal,
        broker.Zona,
        broker.CodigoBroker,
        estado = broker.EstadoAdministrativo.Equals("Inactivo", StringComparison.OrdinalIgnoreCase) ? "I" : "A",
        esAdministrador = broker.EsAdministrador,
    };

    private static string Iniciales(string? nombre)
    {
        var iniciales = string.Concat((nombre ?? "")
            .Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Take(2)
            .Select(parte => char.ToUpperInvariant(parte[0])));
        return string.IsNullOrWhiteSpace(iniciales) ? "--" : iniciales;
    }
}

public class HttpAssignmentService(ApiClient api) : IAssignmentService
{
    public async Task<IReadOnlyList<AssignAgentDto>> AgentsAsync(CancellationToken ct = default) =>
        await CargarAgentes(ct);

    public async Task<IReadOnlyList<AssignBrokerDto>> BrokersAsync(CancellationToken ct = default) =>
        await CargarBrokers(ct);

    public async Task<IReadOnlyList<BrokerAgenteDto>> HistorialAsync(CancellationToken ct = default) =>
        await CargarHistorial(ct);

    public async Task<BrokerAgenteDto> ReasignarAgenteAsync(
        string agenteId, string brokerId, string motivo, CancellationToken ct = default)
    {
        if (!long.TryParse(agenteId, out var idAgente) || idAgente <= 0)
            throw new InvalidOperationException("Selecciona el agente a reasignar.");
        if (!long.TryParse(brokerId, out var idBroker) || idBroker <= 0)
            throw new InvalidOperationException("Selecciona el broker destino.");
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Ingresa el motivo de la reasignacion.");

        var respuesta = await api.PostAsync<BrokerAgenteApi>("asignaciones/reasignar", new
            {
                idAgente,
                idBrokerDestino = idBroker,
                motivo = motivo.Trim(),
            }, ct)
            ?? throw new InvalidOperationException("El API no devolvio la reasignacion registrada.");
        return MapearHistorial(respuesta);
    }

    private async Task<List<AssignAgentDto>> CargarAgentes(CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<AsignacionAgenteApi>>("asignaciones/agentes", ct);
        return lista?.Select(MapearAgente).ToList() ?? [];
    }

    private async Task<List<AssignBrokerDto>> CargarBrokers(CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<AsignacionBrokerApi>>("asignaciones/brokers", ct);
        return lista?.Select(MapearBroker).ToList() ?? [];
    }

    private async Task<List<BrokerAgenteDto>> CargarHistorial(CancellationToken ct = default)
    {
        var lista = await api.GetAsync<List<BrokerAgenteApi>>("asignaciones/historial", ct);
        return lista?.Select(MapearHistorial).ToList() ?? [];
    }

    private static AssignAgentDto MapearAgente(AsignacionAgenteApi item)
    {
        var estadoAdmin = TextoActivo(item.EstadoAdministrativo);
        var estadoOper = TextoOperativo(item.EstadoOperativo);
        var seleccionable = estadoAdmin == "Activo" && estadoOper == "Disponible";
        return new AssignAgentDto
        {
            Id = item.IdAgente.ToString(CultureInfo.InvariantCulture),
            Iniciales = Iniciales(item.Nombre),
            Nombre = item.Nombre ?? "",
            NumeroDocumento = item.NumeroDocumento ?? "",
            EstadoAdministrativo = estadoAdmin,
            EstadoOperativo = estadoOper,
            BrokerActual = string.IsNullOrWhiteSpace(item.BrokerActual) ? "—" : item.BrokerActual!,
            Seleccionable = seleccionable,
            MotivoNoDisponible = seleccionable ? null
                : estadoAdmin != "Activo" ? "Agente inactivo"
                : estadoOper == "Licencia" ? "En licencia — no recibe asignaciones"
                : "Agente no disponible",
            EsAdministrador = false,
        };
    }

    private static AssignBrokerDto MapearBroker(AsignacionBrokerApi item)
    {
        var estadoAdmin = TextoActivo(item.EstadoAdministrativo);
        var seleccionable = estadoAdmin == "Activo" && !item.EsAdministrador;
        return new AssignBrokerDto
        {
            Id = item.IdBroker.ToString(CultureInfo.InvariantCulture),
            Iniciales = Iniciales(item.Nombre),
            Nombre = item.Nombre ?? "",
            Zona = item.Zona ?? "",
            EstadoAdministrativo = estadoAdmin,
            TipoBroker = item.EsAdministrador ? "admin" : "supervisor",
            AgentesACargo = item.AgentesACargo,
            Seleccionable = seleccionable,
            MotivoNoDisponible = seleccionable ? null
                : item.EsAdministrador ? "No se puede asignar a un broker administrador"
                : "Broker inactivo",
            EsAdministrador = item.EsAdministrador,
        };
    }

    private static BrokerAgenteDto MapearHistorial(BrokerAgenteApi item) => new()
    {
        Id = item.Id,
        AgenteId = item.IdAgente ?? 0,
        AgenteNombre = item.AgenteNombre ?? "",
        BrokerAnteriorId = item.IdBrokerAnterior ?? 0,
        BrokerAnteriorNombre = string.IsNullOrWhiteSpace(item.BrokerAnteriorNombre) ? "—" : item.BrokerAnteriorNombre!,
        BrokerNuevoId = item.IdBrokerNuevo ?? 0,
        BrokerNuevoNombre = item.BrokerNuevoNombre ?? "",
        BrokerAdministradorId = item.IdBrokerAdministrador ?? 0,
        BrokerAdministradorNombre = item.BrokerAdministradorNombre ?? "",
        FechaAsignacion = item.FechaCambio is { } fecha ? DateOnly.FromDateTime(fecha) : null,
        FechaAsignacionTexto = item.FechaCambio?.ToString("dd MMM yyyy", CultureInfo.GetCultureInfo("es-PE")) ?? "",
        Motivo = item.Motivo ?? "",
        Estado = "Activa",
    };

    private static string TextoActivo(string? codigo) => codigo == "I" ? "Inactivo" : "Activo";

    private static string TextoOperativo(string? codigo) => codigo switch
    {
        "L" => "Licencia",
        "N" => "No disponible",
        _ => "Disponible",
    };

    private static string Iniciales(string? nombre)
    {
        var iniciales = string.Concat((nombre ?? "")
            .Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Take(2)
            .Select(parte => char.ToUpperInvariant(parte[0])));
        return string.IsNullOrWhiteSpace(iniciales) ? "--" : iniciales;
    }
}

public class HttpAlertaService(ApiClient api, AppState app, NotificacionStore store) : INotificacionService
{
    private static readonly TimeSpan CacheBackendTtl = TimeSpan.FromSeconds(60);
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
        || (RolActivo == Roles.Admin && item.DestinatarioRol == Roles.Broker)
        // El administrador (solo lectura) también es notificado cuando el supervisor
        // marca una comisión como cobrada, aunque la alerta esté dirigida al agente.
        || (RolActivo == Roles.Admin && item.Tipo == LabelComisionCobrada);

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
        Ruta = Ruta(item),
        DestinatarioRol = Destinatario(item.Tipo),
        Fecha = item.FechaGeneracion ?? DateTime.Now,
        Leida = item.Estado != "ACTIVA",
    };

    // Lleva al broker directo a evaluar la solicitud, y al agente al detalle del
    // expediente; ambas pantallas resuelven el id numérico de la alerta. Para los
    // demás tipos se respeta la ruta que envíe el backend.
    private static string? Ruta(AlertaApi item)
    {
        var id = item.EntidadId.ToString(CultureInfo.InvariantCulture);
        return item.Tipo switch
        {
            "SOLICITUD_REENVIADA" => $"evaluacion/{id}",
            "SOLICITUD_EVALUADA" => $"solicitud-detail/{id}",
            // El agente modificó un documento mientras el broker evalúa: lo lleva a evaluar.
            "SOLICITUD_DOCUMENTO" => $"evaluacion/{id}",
            // El broker observó un documento: lleva al agente al expediente a subsanarlo.
            "SOLICITUD_DOCUMENTO_REVISADO" => $"documentos/{id}",
            // Las páginas de captación resuelven por código, no por id: el fallback persistido
            // (cuando ya no está la notificación in-app) va a la bandeja/lista, que siempre abre.
            "CAPTACION_CREADA" => "bandeja-captaciones",
            "CAPTACION_REVISADA" => "captaciones",
            "CAPTACION_CERRADA" => "captaciones",
            "OPORTUNIDAD_CERRADA" => $"oportunidad-detail/{id}",
            // Etapa 3: avisos de comision -> el agente abre su lista de comisiones.
            "COMISION_ASIGNADA" => "comisiones",
            "COMISION_COBRADA" => "comisiones",
            _ => item.Ruta,
        };
    }

    private string Destinatario(string tipo) => tipo switch
    {
        "SOLICITUD_REENVIADA" => Roles.Broker,
        "SOLICITUD_EVALUADA" => Roles.Agente,
        "SOLICITUD_DOCUMENTO" => Roles.Broker,
        "SOLICITUD_DOCUMENTO_REVISADO" => Roles.Agente,
        "CAPTACION_CREADA" => Roles.Broker,
        "CAPTACION_REVISADA" => Roles.Agente,
        "CAPTACION_CERRADA" => Roles.Agente,
        "OPORTUNIDAD_CERRADA" => Roles.Broker,
        "COMISION_ASIGNADA" => Roles.Agente,
        "COMISION_COBRADA" => Roles.Agente,
        _ => RolActivo,
    };

    // Etiqueta de la alerta de comisión cobrada; el administrador (solo lectura) también la ve.
    private const string LabelComisionCobrada = "Comisión cobrada";

    // El label debe coincidir EXACTO con el Tipo de la notificación in-app equivalente para
    // que la campana las deduplique (clave Tipo|EntidadRef|Rol) y no se vean dos veces.
    private static string Tipo(string tipo) => tipo switch
    {
        "SOLICITUD_REENVIADA" => "Solicitud por evaluar",
        "SOLICITUD_EVALUADA" => "Solicitud evaluada",
        "SOLICITUD_DOCUMENTO" => "Documentos actualizados",
        "SOLICITUD_DOCUMENTO_REVISADO" => "Documento observado",
        "CAPTACION_CREADA" => "Captación por revisar",
        "CAPTACION_REVISADA" => "Captación revisada",
        "CAPTACION_CERRADA" => "Captación cerrada",
        "OPORTUNIDAD_CERRADA" => "Trato cerrado",
        "COMISION_ASIGNADA" => "Comisión lista para cobro",
        "COMISION_COBRADA" => LabelComisionCobrada,
        _ => tipo.Replace('_', ' ').ToLowerInvariant(),
    };

    private static string Icono(AlertaApi item)
    {
        if (item.Severidad == "ALTA")
            return "alert";
        if (item.Tipo.StartsWith("COMISION", StringComparison.OrdinalIgnoreCase))
            return "handshake";
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
        "C" => "Cerrada",
        _ => codigo,
    };

    public static string EstadoContrato(string? codigo) => codigo switch
    {
        "P" => "En proceso",
        "D" => "Firmado",
        "V" => "Vigente",
        "R" => "Renovado",
        "F" => "Finalizado",
        "S" => "Rescindido",
        "A" => "Anulado",
        _ => codigo ?? "",
    };

    public static string EstadoDocumento(string? codigo) => codigo switch
    {
        "R" => "Registrado",
        "O" => "Observado",
        "V" => "Validado",
        _ => "",
    };

    public static string ResultadoRevisionDocumento(string? codigo) => codigo switch
    {
        "P" => "Pendiente",
        "C" => "Conforme",
        "O" => "Observado",
        _ => "",
    };

    private static string Documento(string codigo) => codigo switch
    {
        "R" => "RUC",
        "C" => "CE",
        "P" => "Pasaporte",
        _ => "DNI",
    };
}

// Perfil del usuario en sesion: foto y telefono persistidos en el backend.
public sealed record PerfilDto(string? Nombre, string? Correo, string? Telefono, string? FotoClave);

public class HttpPerfilService(ApiClient api)
{
    public Task<PerfilDto?> ObtenerAsync(CancellationToken ct = default) =>
        api.GetAsync<PerfilDto>("perfil", ct);

    public Task ActualizarTelefonoAsync(string telefono, CancellationToken ct = default) =>
        api.PatchAsync("perfil", new { telefono }, ct);

    // La imagen viaja en base64 dentro del JSON (el POST binario rompe el handler de .NET
    // contra GlassFish). Devuelve la clave opaca para servirla por "/documento?clave=".
    public async Task<string?> ActualizarFotoAsync(
        string nombreArchivo, byte[] contenido, CancellationToken ct = default)
    {
        var respuesta = await api.PostAsync<FotoRespuesta>("perfil/foto", new
        {
            nombreArchivo,
            contenidoBase64 = Convert.ToBase64String(contenido),
        }, ct);
        return respuesta?.Clave;
    }

    private sealed record FotoRespuesta(string Clave);
}

// Galeria de fotos de un local. La imagen viaja en base64 (el POST binario rompe el handler
// de .NET contra GlassFish). Cada foto se sirve por "/documento?clave=".
public sealed record FotoLocalDto(long IdFoto, string Clave, string Nombre);

public class HttpFotoLocalService(ApiClient api)
{
    public async Task<IReadOnlyList<FotoLocalDto>> ListarAsync(long idLocal, CancellationToken ct = default) =>
        await api.GetAsync<List<FotoLocalDto>>($"locales/{idLocal}/fotos", ct) ?? [];

    public Task<FotoLocalDto?> SubirAsync(
        long idLocal, string nombreArchivo, byte[] contenido, CancellationToken ct = default) =>
        api.PostAsync<FotoLocalDto>($"locales/{idLocal}/fotos", new
        {
            nombreArchivo,
            contenidoBase64 = Convert.ToBase64String(contenido),
        }, ct);

    public Task EliminarAsync(long idLocal, long idFoto, CancellationToken ct = default) =>
        api.DeleteAsync($"locales/{idLocal}/fotos/{idFoto}", ct);

    // Trae los bytes de la foto (por su clave opaca) directo del backend, para incrustarla
    // en el PDF de la ficha (que se genera en el circuito). No pasa por el proxy /documento.
    public Task<byte[]?> DescargarBytesAsync(string clave, CancellationToken ct = default) =>
        api.GetBytesAsync($"documentos/contenido?clave={Uri.EscapeDataString(clave)}", ct);
}
