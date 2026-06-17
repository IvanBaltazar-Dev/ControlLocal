using ControlLocal.Web.Models.Agentes;
using ControlLocal.Web.Models.Asignaciones;
using ControlLocal.Web.Models.Brokers;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Locales;
using ControlLocal.Web.Models.Oportunidades;
using ControlLocal.Web.Models.Propietarios;
using ControlLocal.Web.Models.Shared;
using ControlLocal.Web.Models.Solicitudes;
using ControlLocal.Web.Models.Visitas;
using ControlLocal.Web.Services;

namespace ControlLocal.Web.Data;

// Contratos de servicios de dominio. Las pantallas dependen solo de estas
// interfaces; la implementación activa (mock en memoria o cliente REST de
// Services/Api) se decide por configuración en Program.cs.

public interface IBrokerService
{
    IReadOnlyList<BrokerDto> All();
    BrokerDto? ById(string codigoBroker);
    BrokerDto First();
    BrokerDto Agregar(BrokerDto broker);
    BrokerDto Actualizar(BrokerDto broker);
}

public interface IAgenteService
{
    IReadOnlyList<AgenteDto> All();
    Task<IReadOnlyList<AgenteDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult(All());
    AgenteDto? ById(long id);
    AgenteDto Agregar(AgenteDto agente);
    AgenteDto Actualizar(AgenteDto agente);
    AgenteDto Desactivar(long id);
}

public interface IPropietarioService
{
    IReadOnlyList<PropietarioDto> All();
    PropietarioDto? ById(long id);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<PropietarioDto>> RefrescarAsync(CancellationToken ct = default);
    // Alta individual o desde la bandeja de importación; asigna el id y devuelve el registro.
    PropietarioDto Agregar(PropietarioDto propietario);
    PropietarioDto Actualizar(PropietarioDto propietario);
}

public interface IClienteService
{
    IReadOnlyList<ClienteInteresadoDto> All();
    ClienteInteresadoDto? ById(long id);
    Task<IReadOnlyList<ClienteInteresadoDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult(All());
    ClienteInteresadoDto Agregar(ClienteInteresadoDto cliente);
    ClienteInteresadoDto Actualizar(ClienteInteresadoDto cliente);
}

public interface ILocalService
{
    IReadOnlyList<LocalComercialDto> All();
    LocalComercialDto? ById(long id);
    LocalComercialDto Agregar(LocalComercialDto local);
    LocalComercialDto Actualizar(LocalComercialDto local);
}

// Prospección (pre-captación): el agente persigue al propietario hasta captar el
// local. Espejo, del lado de la oferta, de la oportunidad. Incluye el embudo y el
// seguimiento de recontacto (≤15 días) tras un "por ahora no".
public interface IProspeccionService
{
    IReadOnlyList<ProspeccionDto> All();
    ProspeccionDto? ById(long id);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<ProspeccionDto>> RefrescarAsync(CancellationToken ct = default);
    // En seguimiento cuyo recontacto vence dentro de diasAviso (o ya venció).
    IReadOnlyList<ProspeccionDto> PorRecontactar(int diasAviso);
    ProspeccionDto Contactar(long id);
    ProspeccionDto RegistrarReunion(long id);
    ProspeccionDto EntregarPropuesta(long id);
    ProspeccionDto Posponer(long id, DateOnly fechaRecontacto);
    ProspeccionDto Rechazar(long id, string motivo);
    ProspeccionDto Descartar(long id, string motivo);
    // El propietario acepta: marca Captado y simula la captación creada.
    ProspeccionDto Captar(long id, decimal comisionPactada);
    ProspeccionDto MarcarCaptado(long id, string codigoCaptacion);
}

public interface ICaptacionService
{
    IReadOnlyList<CaptacionDto> All();
    IReadOnlyList<BandejaCaptacionDto> Bandeja();
    CaptacionDto? ByCodigo(string codigo);
    CaptacionDto Agregar(CaptacionDto captacion);
    CaptacionDto Actualizar(CaptacionDto captacion);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<CaptacionDto>> RefrescarAsync(CancellationToken ct = default);
    Task<IReadOnlyList<BandejaCaptacionDto>> RefrescarBandejaAsync(CancellationToken ct = default);
    Task<CaptacionDto?> ObtenerPorCodigoAsync(string codigo, CancellationToken ct = default);
    Task ResolverBandejaAsync(string codigo, string decision, string? observacion, CancellationToken ct = default);
    Task ReasignarBandejaAsync(string codigo, long idNuevoAgente, string motivo, CancellationToken ct = default);
    Task CerrarAsync(long id, string motivo, CancellationToken ct = default);
}

public interface ISolicitudService
{
    IReadOnlyList<SolicitudAlquilerDto> All();
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<SolicitudAlquilerDto>> RefrescarAsync(CancellationToken ct = default);
    SolicitudAlquilerDto? ByCodigo(string codigo);
    SolicitudAlquilerDto Agregar(SolicitudFormRequest request);
    Task<SolicitudAlquilerDto> AgregarAsync(SolicitudFormRequest request, CancellationToken ct = default) =>
        Task.FromResult(Agregar(request));
    SolicitudAlquilerDto ReenviarAEvaluacion(string codigoSolicitud);
    EvaluacionSolicitudDto Evaluar(string codigoSolicitud, EvaluacionSolicitudDto evaluacion);
    Task<EvaluacionSolicitudDto> EvaluarAsync(
        string codigoSolicitud,
        EvaluacionSolicitudDto evaluacion,
        CancellationToken ct = default) =>
        Task.FromResult(Evaluar(codigoSolicitud, evaluacion));
}

public interface IInteraccionService
{
    IReadOnlyList<InteraccionComercialDto> All();
    InteraccionComercialDto? ById(long id);
    InteraccionComercialDto Agregar(InteraccionFormRequest request);
    InteraccionComercialDto Actualizar(long id, string? resultado = null, string? observaciones = null);
}

// Espeja VisitaBusinessLogic del backend Java: listado + transiciones de estado
// de la cita (programar / reprogramar / cancelar / registrar resultado).
// Oportunidad comercial = cliente interesado + captación (puente N:M del modelo).
// Una misma captación puede tener varias oportunidades (varios clientes interesados).
public interface IOportunidadService
{
    IReadOnlyList<OportunidadComercialDto> All();
    OportunidadComercialDto? ById(long id);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<OportunidadComercialDto>> RefrescarAsync(CancellationToken ct = default);
    // Todos los clientes interesados (oportunidades) de una captación.
    IReadOnlyList<OportunidadComercialDto> ByCaptacion(string codigoCaptacion);
    OportunidadComercialDto Crear(OportunidadFormRequest request);
    Task<OportunidadComercialDto> CrearAsync(OportunidadFormRequest request, CancellationToken ct = default) =>
        Task.FromResult(Crear(request));
    OportunidadComercialDto MarcarSolicitudCreada(long id);
    OportunidadComercialDto CerrarNoContinua(long id, string razon, string? observaciones);
    Task<OportunidadComercialDto> CerrarNoContinuaAsync(
        long id,
        string razon,
        string? observaciones,
        CancellationToken ct = default) =>
        Task.FromResult(CerrarNoContinua(id, razon, observaciones));
    Task<OportunidadComercialDto> CerrarExitosaAsync(long id, CancellationToken ct = default);
}

public interface IVisitaService
{
    IReadOnlyList<VisitaDto> All();
    VisitaDto? ById(long id);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<VisitaDto>> RefrescarAsync(CancellationToken ct = default);
    // POST /visitas — programa una nueva visita (estado inicial PROGRAMADA).
    VisitaDto Programar(VisitaFormRequest request);
    // PATCH /visitas/{id}/reprogramar — mueve fecha/hora (estado REPROGRAMADA).
    VisitaDto Reprogramar(long id, string fechaTexto, string horaTexto);
    // PATCH /visitas/{id}/cancelar — cancela con motivo (estado CANCELADA).
    VisitaDto Cancelar(long id, string motivo);
    // PATCH /visitas/{id}/realizar — confirma que la visita ocurrio.
    VisitaDto MarcarRealizada(long id);
    // PATCH /visitas/{id}/no-realizada — confirma que la cita no llego a ocurrir.
    VisitaDto MarcarNoRealizada(long id, string motivo);
    // PATCH /visitas/{id}/resultado — registra el desenlace de una visita REALIZADA.
    // Si el resultado es de no continuidad (N/D), razonNoContinuidad es obligatorio:
    // registra el motivo ligado a la visita y cierra la oportunidad.
    VisitaDto RegistrarResultado(long id, VisitaResultadoRequest request);
    Task<VisitaDto> ProgramarAsync(VisitaFormRequest request, CancellationToken ct = default) =>
        Task.FromResult(Programar(request));
    Task<VisitaDto> ReprogramarAsync(long id, string fechaTexto, string horaTexto, CancellationToken ct = default) =>
        Task.FromResult(Reprogramar(id, fechaTexto, horaTexto));
    Task<VisitaDto> CancelarAsync(long id, string motivo, CancellationToken ct = default) =>
        Task.FromResult(Cancelar(id, motivo));
    Task<VisitaDto> MarcarRealizadaAsync(long id, CancellationToken ct = default) =>
        Task.FromResult(MarcarRealizada(id));
    Task<VisitaDto> MarcarNoRealizadaAsync(long id, string motivo, CancellationToken ct = default) =>
        Task.FromResult(MarcarNoRealizada(id, motivo));
    Task<VisitaDto> RegistrarResultadoAsync(long id, VisitaResultadoRequest request, CancellationToken ct = default) =>
        Task.FromResult(RegistrarResultado(id, request));
}

public interface IAssignmentService
{
    IReadOnlyList<AssignAgentDto> Agents();
    IReadOnlyList<AssignBrokerDto> Brokers();
    // Historial de reasignaciones de agentes entre brokers (relación BrokerAgente).
    IReadOnlyList<BrokerAgenteDto> Historial();
    // Asigna el agente al broker destino, cierra la supervisión anterior y registra el historial.
    BrokerAgenteDto ReasignarAgente(string agenteId, string brokerId, string motivo);
}

// Reasignación de captaciones a otro agente del equipo. Espeja el flujo de
// negocio reasignarCaptacion(...) del backend Java (registra historial).
public interface IReasignacionCaptacionService
{
    // Captaciones que el broker puede reasignar (estado reasignable).
    IReadOnlyList<CaptacionDto> Reasignables();
    // Agentes candidatos a recibir la captación (del equipo del broker).
    IReadOnlyList<AgenteDto> AgentesDestino();
    // Historial de reasignaciones registradas, más reciente primero.
    IReadOnlyList<ReasignacionCaptacionDto> Historial();
    // Registra una reasignación y devuelve el asiento de historial creado.
    ReasignacionCaptacionDto Reasignar(ReasignarCaptacionRequest request);
}

// ----------------------------------------------------------------------------
// In-memory mock implementations
// ----------------------------------------------------------------------------

public class MockBrokerService : IBrokerService
{
    private static readonly List<BrokerDto> Data = new()
    {
        new() { CodigoBroker = "BRK-001", Iniciales = "RS", Nombre = "Ricardo Salas", Email = "rsalas@controllocal.pe", TipoDocumento = "DNI", NumeroDocumento = "08 412 991", Telefono = "+51 998 110 220", Usuario = "rsalas", Zona = "Lima Centro / Sur", TipoBroker = "Broker supervisor", FechaDesignacionTexto = "11 Ene 2024", AgentesACargo = 7, CaptacionesActivas = 38, EstadoAdministrativo = "Activo", EsAdministrador = false },
        new() { CodigoBroker = "BRK-002", Iniciales = "MQ", Nombre = "Mariana Quintero", Email = "mquintero@controllocal.pe", TipoDocumento = "DNI", NumeroDocumento = "10 552 408", Telefono = "+51 987 220 411", Usuario = "mquintero", Zona = "Lima Norte", TipoBroker = "Broker supervisor", FechaDesignacionTexto = "03 Feb 2024", AgentesACargo = 6, CaptacionesActivas = 31, EstadoAdministrativo = "Activo", EsAdministrador = false },
        new() { CodigoBroker = "BRK-003", Iniciales = "FA", Nombre = "Felipe Andrade", Email = "fandrade@controllocal.pe", TipoDocumento = "DNI", NumeroDocumento = "09 718 220", Telefono = "+51 991 552 100", Usuario = "fandrade", Zona = "Lima Este", TipoBroker = "Broker supervisor", FechaDesignacionTexto = "19 Feb 2024", AgentesACargo = 5, CaptacionesActivas = 27, EstadoAdministrativo = "Activo", EsAdministrador = false },
        new() { CodigoBroker = "BRK-000", Iniciales = "AT", Nombre = "Alejandro Téllez", Email = "atellez@controllocal.pe", TipoDocumento = "DNI", NumeroDocumento = "06 220 884", Telefono = "+51 998 776 002", Usuario = "atellez", Zona = "Global", TipoBroker = "Broker administrador", FechaDesignacionTexto = "01 Oct 2023", AgentesACargo = 0, CaptacionesActivas = 0, EstadoAdministrativo = "Activo", EsAdministrador = true },
        new() { CodigoBroker = "BRK-004", Iniciales = "SR", Nombre = "Sandra Ríos", Email = "srios@controllocal.pe", TipoDocumento = "CE", NumeroDocumento = "00 1123 445", Telefono = "+51 987 002 118", Usuario = "srios", Zona = "Callao / Norte", TipoBroker = "Broker supervisor", FechaDesignacionTexto = "06 May 2024", AgentesACargo = 4, CaptacionesActivas = 22, EstadoAdministrativo = "Activo", EsAdministrador = false },
        new() { CodigoBroker = "BRK-005", Iniciales = "ET", Nombre = "Elena Tafur", Email = "etafur@controllocal.pe", TipoDocumento = "DNI", NumeroDocumento = "08 998 220", Telefono = "+51 991 110 552", Usuario = "etafur", Zona = "Lima Moderna", TipoBroker = "Broker supervisor", FechaDesignacionTexto = "14 Jun 2024", AgentesACargo = 0, CaptacionesActivas = 0, EstadoAdministrativo = "Inactivo", EsAdministrador = false },
    };

    public IReadOnlyList<BrokerDto> All() => Data;
    public BrokerDto? ById(string codigoBroker) => Data.FirstOrDefault(b => b.CodigoBroker == codigoBroker);
    public BrokerDto First() => Data[0];

    public BrokerDto Agregar(BrokerDto broker)
    {
        broker.Id = Data.Count == 0 ? 1 : Math.Max(Data.Max(item => item.Id), Data.Count) + 1;
        broker.CodigoBroker = string.IsNullOrWhiteSpace(broker.CodigoBroker)
            ? $"BRK-{broker.Id:000}"
            : broker.CodigoBroker;
        broker.FechaDesignacion ??= DateOnly.FromDateTime(DateTime.Today);
        broker.FechaDesignacionTexto = broker.FechaDesignacion.Value.ToString("dd MMM yyyy");
        broker.TipoBroker = string.IsNullOrWhiteSpace(broker.TipoBroker) ? "Broker supervisor" : broker.TipoBroker;
        broker.Iniciales = Iniciales(broker.Nombre);
        Data.Add(broker);
        return broker;
    }

    public BrokerDto Actualizar(BrokerDto broker)
    {
        var indice = Data.FindIndex(item => item.CodigoBroker == broker.CodigoBroker);
        if (indice < 0) throw new InvalidOperationException("Broker no encontrado.");
        Data[indice] = broker;
        return broker;
    }

    private static string Iniciales(string nombre) => string.Concat(
        nombre.Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Take(2)
            .Select(parte => char.ToUpperInvariant(parte[0])));
}

public class MockAgenteService : IAgenteService
{
    private static readonly List<AgenteDto> Data = new()
    {
        new() { Id = 1, Iniciales = "VM", Color = "#3A6BB5", Nombre = "Valentina Mora", TipoPersona = "Persona natural", TipoDocumento = "D", Email = "vmora@controllocal.pe", NumeroDocumento = "45 893 211", Telefono = "+51 998 110 311", Zona = "Lima Centro", FechaIngresoTexto = "14 Feb 2024", CaptacionesActivas = 14, OportunidadesActivas = 8, EstadoAdministrativo = "Activo" },
        new() { Id = 2, Iniciales = "CV", Color = "#2F7D52", Nombre = "Carolina Vega", TipoPersona = "Persona natural", TipoDocumento = "D", Email = "cvega@controllocal.pe", NumeroDocumento = "46 220 411", Telefono = "+51 987 220 411", Zona = "Lima Sur", FechaIngresoTexto = "22 Mar 2024", CaptacionesActivas = 9, OportunidadesActivas = 5, EstadoAdministrativo = "Activo" },
        new() { Id = 3, Iniciales = "AT", Color = "#C0473C", Nombre = "Andrea Torres", TipoPersona = "Persona natural", TipoDocumento = "D", Email = "atorres@controllocal.pe", NumeroDocumento = "47 412 008", Telefono = "+51 991 412 008", Zona = "Callao", FechaIngresoTexto = "06 May 2024", CaptacionesActivas = 6, OportunidadesActivas = 3, EstadoAdministrativo = "Activo" },
        new() { Id = 4, Iniciales = "PR", Color = "#00AEEF", Nombre = "Paola Reyes", TipoPersona = "Persona natural", TipoDocumento = "D", Email = "preyes@controllocal.pe", NumeroDocumento = "45 002 884", Telefono = "+51 999 002 884", Zona = "Lima Centro", FechaIngresoTexto = "18 Jul 2024", CaptacionesActivas = 7, OportunidadesActivas = 4, EstadoAdministrativo = "Activo" },
        new() { Id = 5, Iniciales = "JM", Color = "#2C77A0", Nombre = "Jorge Marín", TipoPersona = "Persona natural", TipoDocumento = "D", Email = "jmarin@controllocal.pe", NumeroDocumento = "44 728 101", Telefono = "+51 988 728 101", Zona = "Lima Moderna", FechaIngresoTexto = "03 Sep 2024", CaptacionesActivas = 5, OportunidadesActivas = 2, EstadoAdministrativo = "Activo" },
        new() { Id = 6, Iniciales = "NS", Color = "#948D81", Nombre = "Natalia Solano", TipoPersona = "Persona natural", TipoDocumento = "D", Email = "nsolano@controllocal.pe", NumeroDocumento = "46 998 220", Telefono = "+51 977 998 220", Zona = "Lima Sur", FechaIngresoTexto = "19 Oct 2024", CaptacionesActivas = 0, OportunidadesActivas = 0, EstadoAdministrativo = "Inactivo" },
    };

    public IReadOnlyList<AgenteDto> All() => Data;
    public AgenteDto? ById(long id) => Data.FirstOrDefault(a => a.Id == id);

    public AgenteDto Agregar(AgenteDto agente)
    {
        agente.Id = Data.Count == 0 ? 1 : Data.Max(item => item.Id) + 1;
        agente.CodigoAgente = string.IsNullOrWhiteSpace(agente.CodigoAgente)
            ? $"AGE-{agente.Id:000}"
            : agente.CodigoAgente;
        agente.FechaIngreso ??= DateOnly.FromDateTime(DateTime.Today);
        agente.FechaIngresoTexto = agente.FechaIngreso.Value.ToString("dd MMM yyyy");
        agente.Iniciales = Iniciales(agente.Nombre);
        agente.Color = string.IsNullOrWhiteSpace(agente.Color) ? "#3A6BB5" : agente.Color;
        Data.Add(agente);
        return agente;
    }

    public AgenteDto Actualizar(AgenteDto agente)
    {
        var indice = Data.FindIndex(item => item.Id == agente.Id);
        if (indice < 0) throw new InvalidOperationException("Agente no encontrado.");
        Data[indice] = agente;
        return agente;
    }

    public AgenteDto Desactivar(long id)
    {
        var agente = ById(id) ?? throw new InvalidOperationException("Agente no encontrado.");
        agente.EstadoAdministrativo = "Inactivo";
        agente.EstadoOperativo = "No disponible";
        return agente;
    }

    private static string Iniciales(string nombre) => string.Concat(
        nombre.Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Take(2)
            .Select(parte => char.ToUpperInvariant(parte[0])));
}

public class MockPropietarioService : IPropietarioService
{
    private readonly List<PropietarioDto> _data = new()
    {
        new() { Id = 1, Nombre = "Inmobiliaria Pacífico S.A.C.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 553 102 884", Telefono = "+51 1 432 8800", Correo = "contacto@pacifico.com.pe", CantidadLocales = 6, Estado = "Activo" },
        new() { Id = 2, Nombre = "Carlos Mendoza Rivera", TipoPersona = "Persona natural · DNI", NumeroDocumento = "08 412 991", Telefono = "+51 998 220 411", Correo = "cmendoza@gmail.com", CantidadLocales = 2, Estado = "Activo" },
        new() { Id = 3, Nombre = "Grupo Bermúdez E.I.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 502 998 110", Telefono = "+51 1 222 1108", Correo = "admin@bermudez.pe", CantidadLocales = 4, Estado = "Activo" },
        new() { Id = 4, Nombre = "Ana Lucía Pereyra", TipoPersona = "Persona natural · DNI", NumeroDocumento = "09 778 002", Telefono = "+51 987 412 008", Correo = "alpereyra@hotmail.com", CantidadLocales = 1, Estado = "Activo" },
        new() { Id = 5, Nombre = "Comercial Andina S.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 471 220 008", Telefono = "+51 1 718 4400", Correo = "ventas@andina.com.pe", CantidadLocales = 3, Estado = "Inactivo" },
        new() { Id = 6, Nombre = "Roberto Linares Cruz", TipoPersona = "Persona natural · DNI", NumeroDocumento = "07 823 145", Telefono = "+51 991 552 008", Correo = "rlinares@yahoo.com", CantidadLocales = 1, Estado = "Activo" },
    };

    public IReadOnlyList<PropietarioDto> All() => _data;
    public Task<IReadOnlyList<PropietarioDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<PropietarioDto>>(_data);
    public PropietarioDto? ById(long id) => _data.FirstOrDefault(p => p.Id == id);

    public PropietarioDto Agregar(PropietarioDto propietario)
    {
        propietario.Id = _data.Max(p => p.Id) + 1;
        if (string.IsNullOrWhiteSpace(propietario.Estado)) propietario.Estado = "Activo";
        _data.Add(propietario);
        return propietario;
    }

    public PropietarioDto Actualizar(PropietarioDto propietario)
    {
        var indice = _data.FindIndex(item => item.Id == propietario.Id);
        if (indice < 0) throw new InvalidOperationException("Propietario no encontrado.");
        _data[indice] = propietario;
        return propietario;
    }
}

public class MockClienteService : IClienteService
{
    private readonly List<ClienteInteresadoDto> _data = new()
    {
        new() { Id = 1, Nombre = "Inversiones Trébol S.A.C.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 558 110 442", Telefono = "+51 1 445 7800", Correo = "contacto@trebol.pe", RubroInteres = "Restaurante / Café", ConsentimientoContacto = true, ConsentimientoUsoDato = true, Estado = "Activo" },
        new() { Id = 2, Nombre = "Boutique Lila", TipoPersona = "Persona natural · DNI", NumeroDocumento = "45 220 118", Telefono = "+51 998 552 110", Correo = "lila.boutique@gmail.com", RubroInteres = "Moda / Boutique", ConsentimientoContacto = true, ConsentimientoUsoDato = true, Estado = "Activo" },
        new() { Id = 3, Nombre = "Bodegas del Norte E.I.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 471 882 004", Telefono = "+51 1 552 1180", Correo = "operaciones@bodegasnorte.pe", RubroInteres = "Retail / Almacén", ConsentimientoContacto = true, ConsentimientoUsoDato = true, Estado = "Activo" },
        new() { Id = 4, Nombre = "Café Lima", TipoPersona = "Persona natural · DNI", NumeroDocumento = "44 118 902", Telefono = "+51 987 220 004", Correo = "cafelima@gmail.com", RubroInteres = "Café / Postres", ConsentimientoContacto = true, ConsentimientoUsoDato = true, Estado = "Activo" },
        new() { Id = 5, Nombre = "Carla Espinoza Núñez", TipoPersona = "Persona natural · DNI", NumeroDocumento = "46 552 008", Telefono = "+51 991 002 552", Correo = "cespinoza@gmail.com", RubroInteres = "Servicios / Consultorio", ConsentimientoContacto = true, ConsentimientoUsoDato = true, Estado = "Activo" },
        new() { Id = 6, Nombre = "Distribuidora El Sol S.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 502 110 778", Telefono = "+51 1 718 2200", Correo = "ventas@elsol.pe", RubroInteres = "Retail", ConsentimientoContacto = false, ConsentimientoUsoDato = true, Estado = "Inactivo" },
    };

    public IReadOnlyList<ClienteInteresadoDto> All() => _data;
    public ClienteInteresadoDto? ById(long id) => _data.FirstOrDefault(c => c.Id == id);

    public ClienteInteresadoDto Agregar(ClienteInteresadoDto cliente)
    {
        cliente.Id = _data.Count == 0 ? 1 : _data.Max(item => item.Id) + 1;
        _data.Add(cliente);
        return cliente;
    }

    public ClienteInteresadoDto Actualizar(ClienteInteresadoDto cliente)
    {
        var indice = _data.FindIndex(item => item.Id == cliente.Id);
        if (indice < 0) throw new InvalidOperationException("Cliente no encontrado.");
        _data[indice] = cliente;
        return cliente;
    }
}

public class MockLocalService : ILocalService
{
    private readonly List<LocalComercialDto> _data = new()
    {
        // Estado = disponibilidad del inmueble (EstadoLocalComercial: Disponible/No disponible/Inactivo).
        // La etapa de prospección/captación es un eje aparte (ver IProspeccionService).
        new() { Id = 1, CodigoLocal = "LC-0218", Estado = "Disponible", Direccion = "Av. La Marina 245", Distrito = "San Miguel, Lima", AreaM2 = 120, Rubro = "Restaurante / Café", PrecioReferencialTexto = "2 800", PropietarioNombre = "Inmobiliaria Pacífico" },
        new() { Id = 2, CodigoLocal = "LC-0226", Estado = "Disponible", Direccion = "Calle Schell 412, of. 1", Distrito = "Miraflores, Lima", AreaM2 = 68, Rubro = "Moda / Boutique", PrecioReferencialTexto = "1 950", PropietarioNombre = "C. Mendoza" },
        new() { Id = 3, CodigoLocal = "LC-0231", Estado = "Disponible", Direccion = "Av. Petit Thouars 1875", Distrito = "Jesús María, Lima", AreaM2 = 95, Rubro = "Servicios", PrecioReferencialTexto = "1 600", PropietarioNombre = "Grupo Bermúdez" },
        new() { Id = 4, CodigoLocal = "LC-0234", Estado = "No disponible", Direccion = "Jr. Berlín 230", Distrito = "Miraflores, Lima", AreaM2 = 52, Rubro = "Café / Postres", PrecioReferencialTexto = "1 450", PropietarioNombre = "A. Pereyra" },
        new() { Id = 5, CodigoLocal = "LC-0238", Estado = "Inactivo", Direccion = "Av. Aviación 4012", Distrito = "San Borja, Lima", AreaM2 = 180, Rubro = "Retail", PrecioReferencialTexto = "3 600", PropietarioNombre = "Comercial Andina" },
        new() { Id = 6, CodigoLocal = "LC-0242", Estado = "Disponible", Direccion = "Av. Salaverry 2120", Distrito = "Jesús María, Lima", AreaM2 = 88, Rubro = "Servicios médicos", PrecioReferencialTexto = "2 200", PropietarioNombre = "R. Linares" },
    };

    public IReadOnlyList<LocalComercialDto> All() => _data;
    public LocalComercialDto? ById(long id) => _data.FirstOrDefault(l => l.Id == id);

    public LocalComercialDto Agregar(LocalComercialDto local)
    {
        local.Id = _data.Count == 0 ? 1 : _data.Max(item => item.Id) + 1;
        if (string.IsNullOrWhiteSpace(local.CodigoLocal)) local.CodigoLocal = $"LC-{local.Id:0000}";
        _data.Add(local);
        return local;
    }

    public LocalComercialDto Actualizar(LocalComercialDto local)
    {
        var indice = _data.FindIndex(item => item.Id == local.Id);
        if (indice < 0) throw new InvalidOperationException("Local no encontrado.");
        _data[indice] = local;
        return local;
    }
}

public class MockProspeccionService : IProspeccionService
{
    private const string AgenteSesion = "V. Mora";
    private static DateOnly Hoy => DateOnly.FromDateTime(DateTime.Today);

    private readonly List<ProspeccionDto> _data = new()
    {
        new() { Id = 1, CodigoProspeccion = "PRO-0001", LocalId = 1, LocalCodigo = "LC-0218", Direccion = "Av. La Marina 245", Distrito = "San Miguel", AreaM2 = 120, Rubro = "Restaurante / Café", PrecioReferencialTexto = "2 800", PropietarioNombre = "Inmobiliaria Pacífico", NombreAgente = AgenteSesion, Estado = "P" },
        new() { Id = 2, CodigoProspeccion = "PRO-0002", LocalId = 2, LocalCodigo = "LC-0226", Direccion = "Calle Schell 412", Distrito = "Miraflores", AreaM2 = 68, Rubro = "Moda / Boutique", PrecioReferencialTexto = "1 950", PropietarioNombre = "C. Mendoza", NombreAgente = AgenteSesion, Estado = "C", FechaContactoTexto = "26 May 2026" },
        new() { Id = 3, CodigoProspeccion = "PRO-0003", LocalId = 3, LocalCodigo = "LC-0231", Direccion = "Av. Petit Thouars 1875", Distrito = "Jesús María", AreaM2 = 95, Rubro = "Servicios", PrecioReferencialTexto = "1 600", PropietarioNombre = "Grupo Bermúdez", NombreAgente = AgenteSesion, Estado = "R", FechaContactoTexto = "22 May 2026", FechaReunionTexto = "27 May 2026" },
        new() { Id = 4, CodigoProspeccion = "PRO-0004", LocalId = 4, LocalCodigo = "LC-0234", Direccion = "Jr. Berlín 230", Distrito = "Miraflores", AreaM2 = 52, Rubro = "Café / Postres", PrecioReferencialTexto = "1 450", PropietarioNombre = "A. Pereyra", NombreAgente = AgenteSesion, Estado = "E", ResultadoPropuesta = "P", FechaPropuestaTexto = "28 May 2026" },
        // En seguimiento con recontacto VENCIDO (alerta roja).
        new() { Id = 5, CodigoProspeccion = "PRO-0005", LocalId = 5, LocalCodigo = "LC-0238", Direccion = "Av. Aviación 4012", Distrito = "San Borja", AreaM2 = 180, Rubro = "Retail", PrecioReferencialTexto = "3 600", PropietarioNombre = "Comercial Andina", NombreAgente = AgenteSesion, Estado = "S", ResultadoPropuesta = "S", FechaRecontacto = Hoy.AddDays(-3), FechaRecontactoTexto = Hoy.AddDays(-3).ToString("dd MMM yyyy", System.Globalization.CultureInfo.InvariantCulture), Observaciones = "Pidió pensarlo; evalúa otra agencia." },
        // En seguimiento con recontacto PRÓXIMO (alerta ámbar).
        new() { Id = 6, CodigoProspeccion = "PRO-0006", LocalId = 6, LocalCodigo = "LC-0242", Direccion = "Av. Salaverry 2120", Distrito = "Jesús María", AreaM2 = 88, Rubro = "Servicios médicos", PrecioReferencialTexto = "2 200", PropietarioNombre = "R. Linares", NombreAgente = AgenteSesion, Estado = "S", ResultadoPropuesta = "S", FechaRecontacto = Hoy.AddDays(4), FechaRecontactoTexto = Hoy.AddDays(4).ToString("dd MMM yyyy", System.Globalization.CultureInfo.InvariantCulture), Observaciones = "Interesado, decide tras vacaciones." },
        new() { Id = 7, CodigoProspeccion = "PRO-0007", LocalId = 7, LocalCodigo = "LC-0250", Direccion = "Av. Brasil 2890", Distrito = "Magdalena", AreaM2 = 60, Rubro = "Café", PrecioReferencialTexto = "1 700", PropietarioNombre = "Inmobiliaria Pacífico", NombreAgente = AgenteSesion, Estado = "T", ResultadoPropuesta = "A", CaptacionCodigo = "CAP-0251" },
        new() { Id = 8, CodigoProspeccion = "PRO-0008", LocalId = 8, LocalCodigo = "LC-0255", Direccion = "Av. Arequipa 3100", Distrito = "Lince", AreaM2 = 110, Rubro = "Retail", PrecioReferencialTexto = "2 400", PropietarioNombre = "Grupo Bermúdez", NombreAgente = AgenteSesion, Estado = "D", ResultadoPropuesta = "R", Observaciones = "Rechazó la comisión propuesta." },
    };

    public IReadOnlyList<ProspeccionDto> All() => _data;

    public Task<IReadOnlyList<ProspeccionDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<ProspeccionDto>>(_data);

    public ProspeccionDto? ById(long id) => _data.FirstOrDefault(p => p.Id == id);

    public IReadOnlyList<ProspeccionDto> PorRecontactar(int diasAviso)
    {
        var limite = Hoy.AddDays(Math.Max(0, diasAviso));
        return _data
            .Where(p => p.Estado == "S" && p.FechaRecontacto is { } f && f <= limite)
            .OrderBy(p => p.FechaRecontacto)
            .ToList();
    }

    public ProspeccionDto Contactar(long id)
    {
        var p = EnProceso(id, "contactar");
        p.Estado = "C";
        p.FechaContactoTexto = FechaHoy();
        return p;
    }

    public ProspeccionDto RegistrarReunion(long id)
    {
        var p = EnProceso(id, "registrar la reunión de");
        p.Estado = "R";
        p.FechaReunionTexto = FechaHoy();
        return p;
    }

    public ProspeccionDto EntregarPropuesta(long id)
    {
        var p = EnProceso(id, "entregar la propuesta de");
        p.Estado = "E";
        p.ResultadoPropuesta = "P";
        p.FechaPropuestaTexto = FechaHoy();
        p.FechaRecontacto = null;
        p.FechaRecontactoTexto = null;
        return p;
    }

    public ProspeccionDto Posponer(long id, DateOnly fechaRecontacto)
    {
        var p = EnProceso(id, "posponer");
        if (fechaRecontacto < Hoy)
            throw new InvalidOperationException("La fecha de recontacto no puede estar en el pasado.");
        if (fechaRecontacto > Hoy.AddDays(15))
            throw new InvalidOperationException("El recontacto no puede superar los 15 días.");
        p.Estado = "S";
        p.ResultadoPropuesta = "S";
        p.FechaRecontacto = fechaRecontacto;
        p.FechaRecontactoTexto = fechaRecontacto.ToString("dd MMM yyyy", System.Globalization.CultureInfo.InvariantCulture);
        return p;
    }

    public ProspeccionDto Rechazar(long id, string motivo)
    {
        var p = EnProceso(id, "rechazar");
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar el motivo del rechazo.");
        p.Estado = "D";
        p.ResultadoPropuesta = "R";
        p.Observaciones = motivo;
        p.FechaRecontacto = null;
        p.FechaRecontactoTexto = null;
        return p;
    }

    public ProspeccionDto Descartar(long id, string motivo)
    {
        var p = EnProceso(id, "descartar");
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar el motivo del descarte.");
        p.Estado = "D";
        p.Observaciones = motivo;
        p.FechaRecontacto = null;
        p.FechaRecontactoTexto = null;
        return p;
    }

    public ProspeccionDto Captar(long id, decimal comisionPactada)
    {
        if (comisionPactada < 0)
            throw new InvalidOperationException("La comisión pactada no puede ser negativa.");
        return MarcarCaptado(id, $"CAP-{1000 + id:0000}");
    }

    public ProspeccionDto MarcarCaptado(long id, string codigoCaptacion)
    {
        var p = EnProceso(id, "captar");
        if (string.IsNullOrWhiteSpace(codigoCaptacion))
            throw new InvalidOperationException("El codigo de captacion es obligatorio.");
        p.Estado = "T";
        p.ResultadoPropuesta = "A";
        p.CaptacionCodigo = codigoCaptacion;
        p.FechaRecontacto = null;
        p.FechaRecontactoTexto = null;
        return p;
    }

    // Solo una prospección en proceso (ni Captada ni Descartada) admite cambios.
    private ProspeccionDto EnProceso(long id, string accion)
    {
        var p = _data.FirstOrDefault(x => x.Id == id)
                ?? throw new InvalidOperationException("Prospección no encontrada.");
        if (p.Estado is "T" or "D")
            throw new InvalidOperationException(
                $"No se puede {accion} una prospección {EnumCatalog.LabelFor(EnumCatalog.EstadosProspeccion, p.Estado).ToLower()}.");
        return p;
    }

    private static string FechaHoy() =>
        DateTime.Today.ToString("dd MMM yyyy", System.Globalization.CultureInfo.InvariantCulture);
}

public class MockCaptacionService : ICaptacionService
{
    private static readonly List<CaptacionDto> Data = new()
    {
        new() { Id = 1, LocalId = 1, CodigoCaptacion = "CAP-0218", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", AreaM2 = 120, PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "01 Abr – 01 Oct 2026", DiasRestantesTexto = "venc. en 130 días", ComisionPactadaTexto = "5.0", Estado = "Activa" },
        new() { Id = 2, LocalId = 2, CodigoCaptacion = "CAP-0227", DireccionLocal = "Calle Schell 412", DistritoLocal = "Miraflores", AreaM2 = 68, PropietarioNombre = "Carlos Mendoza", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "15 May – 15 Nov 2026", DiasRestantesTexto = "venc. en 175 días", ComisionPactadaTexto = "4.5", Estado = "Activa" },
        new() { Id = 3, LocalId = 3, CodigoCaptacion = "CAP-0233", DireccionLocal = "Av. Caminos del Inca 1820", DistritoLocal = "Surco", AreaM2 = 140, PropietarioNombre = "A. Pereyra", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "Borrador", DiasRestantesTexto = "no enviado", ComisionPactadaTexto = "—", Estado = "Pendiente de revision" },
        new() { Id = 4, LocalId = 4, CodigoCaptacion = "CAP-0234", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", AreaM2 = 52, PropietarioNombre = "A. Pereyra", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "10 May – 10 Nov 2026", DiasRestantesTexto = "venc. en 170 días", ComisionPactadaTexto = "5.5", Estado = "Activa" },
        new() { Id = 5, LocalId = 5, CodigoCaptacion = "CAP-0237", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", AreaM2 = 88, PropietarioNombre = "R. Linares", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "En revisión", DiasRestantesTexto = "enviada hace 1d", ComisionPactadaTexto = "5.0", Estado = "Pendiente de revision" },
        new() { Id = 6, LocalId = 6, CodigoCaptacion = "CAP-0241", DireccionLocal = "Av. Perú 2845", DistritoLocal = "San Martín", AreaM2 = 72, PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "Observada", DiasRestantesTexto = "requiere ajuste", ComisionPactadaTexto = "4.5", Estado = "Observada" },
        new() { Id = 7, LocalId = 7, CodigoCaptacion = "CAP-0244", DireccionLocal = "Av. Brasil 2890", DistritoLocal = "Magdalena", AreaM2 = 60, PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "23 May – 23 Ago 2026", DiasRestantesTexto = "venc. en 91 días", ComisionPactadaTexto = "5.0", Estado = "Rechazada" },
    };

    private static readonly List<BandejaCaptacionDto> BandejaData = new()
    {
        new() { CodigoCaptacion = "CAP-0231", DireccionLocal = "Av. Petit Thouars 1875", DistritoLocal = "Jesús María", AreaM2 = 95, Rubro = "Servicios", PropietarioNombre = "Grupo Bermúdez", NombreAgenteResponsable = "Carolina Vega", FechaEnvioTexto = "22 May 14:08", AntiguedadTexto = "hace 2d", ComisionPactadaTexto = "5.0", Estado = "Pendiente de revision" },
        new() { CodigoCaptacion = "CAP-0233", DireccionLocal = "Av. Caminos del Inca 1820", DistritoLocal = "Surco", AreaM2 = 140, Rubro = "Restaurante", PropietarioNombre = "A. Pereyra", NombreAgenteResponsable = "Valentina Mora", FechaEnvioTexto = "23 May 11:30", AntiguedadTexto = "hace 1d", ComisionPactadaTexto = "5.0", Estado = "Pendiente de revision" },
        new() { CodigoCaptacion = "CAP-0236", DireccionLocal = "Av. Pardo 2120", DistritoLocal = "Miraflores", AreaM2 = 78, Rubro = "Moda", PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Andrea Torres", FechaEnvioTexto = "23 May 16:42", AntiguedadTexto = "hace 21h", ComisionPactadaTexto = "4.5", Estado = "Pendiente de revision" },
        new() { CodigoCaptacion = "CAP-0238", DireccionLocal = "Av. Aviación 4012", DistritoLocal = "San Borja", AreaM2 = 180, Rubro = "Retail", PropietarioNombre = "Comercial Andina", NombreAgenteResponsable = "Jorge Marín", FechaEnvioTexto = "21 May 10:00", AntiguedadTexto = "hace 3d", ComisionPactadaTexto = "5.0", Estado = "Observada" },
        new() { CodigoCaptacion = "CAP-0240", DireccionLocal = "Av. Tomás Marsano 3400", DistritoLocal = "Surco", AreaM2 = 75, Rubro = "Servicios", PropietarioNombre = "R. Linares", NombreAgenteResponsable = "Carolina Vega", FechaEnvioTexto = "22 May 09:15", AntiguedadTexto = "hace 2d", ComisionPactadaTexto = "4.5", Estado = "Pendiente de revision" },
        new() { CodigoCaptacion = "CAP-0243", DireccionLocal = "Av. Brasil 2890", DistritoLocal = "Magdalena", AreaM2 = 60, Rubro = "Café", PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Paola Reyes", FechaEnvioTexto = "23 May 17:50", AntiguedadTexto = "hace 20h", ComisionPactadaTexto = "5.0", Estado = "Observada" },
    };

    public IReadOnlyList<CaptacionDto> All() => Data;
    public IReadOnlyList<BandejaCaptacionDto> Bandeja() => BandejaData;
    public CaptacionDto? ByCodigo(string codigo) => Data.FirstOrDefault(c => c.CodigoCaptacion == codigo);

    public Task<IReadOnlyList<CaptacionDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<CaptacionDto>>(Data);

    public Task<IReadOnlyList<BandejaCaptacionDto>> RefrescarBandejaAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<BandejaCaptacionDto>>(BandejaData);

    public CaptacionDto Agregar(CaptacionDto captacion)
    {
        captacion.Id = Data.Count == 0 ? 1 : Data.Max(item => item.Id) + 1;
        captacion.CodigoCaptacion = string.IsNullOrWhiteSpace(captacion.CodigoCaptacion)
            ? $"CAP-{captacion.Id:0000}"
            : captacion.CodigoCaptacion;
        captacion.FechaCaptacion ??= DateOnly.FromDateTime(DateTime.Today);
        captacion.Estado = "Pendiente de revision";
        captacion.ComisionPactadaTexto = captacion.ComisionPactada.ToString(
            "0.0", System.Globalization.CultureInfo.InvariantCulture);
        Data.Add(captacion);
        BandejaData.Add(new BandejaCaptacionDto
        {
            Id = captacion.Id,
            CodigoCaptacion = captacion.CodigoCaptacion,
            DireccionLocal = captacion.DireccionLocal,
            DistritoLocal = captacion.DistritoLocal,
            AreaM2 = captacion.AreaM2,
            Rubro = captacion.Rubro,
            PropietarioNombre = captacion.PropietarioNombre,
            NombreAgenteResponsable = captacion.NombreAgenteResponsable,
            FechaEnvioTexto = captacion.FechaCaptacion?.ToString("dd MMM yyyy") ?? "",
            AntiguedadTexto = "hoy",
            ComisionPactadaTexto = captacion.ComisionPactadaTexto,
            Estado = captacion.Estado,
        });
        return captacion;
    }

    public CaptacionDto Actualizar(CaptacionDto captacion)
    {
        var indice = Data.FindIndex(item => item.Id == captacion.Id);
        if (indice < 0) throw new InvalidOperationException("Captacion no encontrada.");
        if (Data[indice].Estado is not ("Pendiente de revision" or "Observada"))
            throw new InvalidOperationException("Solo se puede editar una captacion pendiente u observada.");
        captacion.Estado = "Pendiente de revision";
        captacion.ComisionPactadaTexto = captacion.ComisionPactada.ToString(
            "0.0", System.Globalization.CultureInfo.InvariantCulture);
        Data[indice] = captacion;
        var bandeja = BandejaData.FirstOrDefault(item => item.CodigoCaptacion == captacion.CodigoCaptacion);
        if (bandeja is not null)
        {
            bandeja.ComisionPactadaTexto = captacion.ComisionPactadaTexto;
            bandeja.Estado = captacion.Estado;
        }
        return captacion;
    }

    public Task<CaptacionDto?> ObtenerPorCodigoAsync(string codigo, CancellationToken ct = default)
    {
        var captacion = ByCodigo(codigo);
        if (captacion is null)
        {
            var bandeja = BandejaData.FirstOrDefault(c => c.CodigoCaptacion == codigo);
            if (bandeja is not null)
            {
                captacion = new CaptacionDto
                {
                    Id = bandeja.Id,
                    CodigoCaptacion = bandeja.CodigoCaptacion,
                    DireccionLocal = bandeja.DireccionLocal,
                    DistritoLocal = bandeja.DistritoLocal,
                    AreaM2 = bandeja.AreaM2,
                    Rubro = bandeja.Rubro,
                    PropietarioNombre = bandeja.PropietarioNombre,
                    NombreAgenteResponsable = bandeja.NombreAgenteResponsable,
                    ComisionPactadaTexto = bandeja.ComisionPactadaTexto,
                    Estado = bandeja.Estado,
                    Observaciones = "Expediente enviado para revision del broker.",
                };
                Data.Add(captacion);
            }
        }
        return Task.FromResult(captacion);
    }

    public async Task ResolverBandejaAsync(string codigo, string decision, string? observacion, CancellationToken ct = default)
    {
        var captacion = await ObtenerPorCodigoAsync(codigo, ct)
            ?? throw new InvalidOperationException("Captacion no encontrada.");
        var accion = decision.Trim().ToUpperInvariant();
        if (accion is "OBSERVAR" or "RECHAZAR" && string.IsNullOrWhiteSpace(observacion))
            throw new InvalidOperationException("Debes ingresar un motivo para continuar.");

        captacion.Estado = accion switch
        {
            "APROBAR" => "Activa",
            "OBSERVAR" => "Observada",
            "RECHAZAR" => "Rechazada",
            _ => throw new InvalidOperationException("Decision no valida."),
        };
        captacion.ObservacionRevision = observacion ?? "";
        BandejaData.RemoveAll(item => item.CodigoCaptacion == codigo);
    }

    public async Task ReasignarBandejaAsync(string codigo, long idNuevoAgente, string motivo, CancellationToken ct = default)
    {
        if (idNuevoAgente <= 0 || string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Selecciona un agente destino e ingresa el motivo.");
        var captacion = await ObtenerPorCodigoAsync(codigo, ct)
            ?? throw new InvalidOperationException("Captacion no encontrada.");
        captacion.AgenteResponsableId = idNuevoAgente;
        captacion.NombreAgenteResponsable = $"Agente #{idNuevoAgente}";
        var bandeja = BandejaData.FirstOrDefault(item => item.CodigoCaptacion == codigo);
        if (bandeja is not null)
            bandeja.NombreAgenteResponsable = captacion.NombreAgenteResponsable;
    }

    public Task CerrarAsync(long id, string motivo, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("El motivo de cierre es obligatorio.");
        var captacion = Data.FirstOrDefault(item => item.Id == id)
            ?? throw new InvalidOperationException("Captacion no encontrada.");
        if (captacion.Estado != "Activa")
            throw new InvalidOperationException("Solo se puede cerrar una captacion activa.");
        captacion.Estado = "Cerrada";
        captacion.ObservacionRevision = motivo.Trim();
        return Task.CompletedTask;
    }
}

// Almacen compartido (Singleton) de solicitudes. A diferencia de un servicio
// Scoped, conserva el estado entre navegaciones y reinicios de circuito de Blazor
// Server: esa era la causa de "no se llega a cerrar" (el mock Scoped se re-sembraba
// en cada request y perdia la transicion de estado). El backend REST real lo
// reemplazara por persistencia en MySQL via HttpSolicitudService.
public class SolicitudStore
{
    private readonly object _lock = new();
    private readonly List<SolicitudAlquilerDto> _data = new()
    {
        new() { Id = 1, OportunidadId = 4, CodigoSolicitud = "SOL-0425", CodigoOperacion = "OP-1094", ClienteNombre = "Boutique Lila", DireccionLocal = "Calle Schell 412", DistritoLocal = "Miraflores", MontoMensual = 1850, MontoMensualTexto = "1 850", PlazoMeses = 24, PlazoTentativo = "24 meses", DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "23 May", Estado = "En revision" },
        new() { Id = 2, OportunidadId = 1, CodigoSolicitud = "SOL-0428", CodigoOperacion = "OP-1083", ClienteNombre = "Inversiones Trébol", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", MontoMensual = 2750, MontoMensualTexto = "2 750", PlazoMeses = 36, PlazoTentativo = "36 meses", DocumentosTexto = "5/6", PorcentajeDocumentos = 83, FechaRegistroTexto = "22 May", Estado = "Observada" },
        new() { Id = 3, OportunidadId = 5, CodigoSolicitud = "SOL-0430", CodigoOperacion = "OP-1085", ClienteNombre = "Café Lima", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", MontoMensual = 1400, MontoMensualTexto = "1 400", PlazoMeses = 24, PlazoTentativo = "24 meses", DocumentosTexto = "4/6", PorcentajeDocumentos = 66, FechaRegistroTexto = "22 May", Estado = "Registrada" },
        new() { Id = 4, CodigoSolicitud = "SOL-0421", CodigoOperacion = "OP-1077", ClienteNombre = "Carla Espinoza", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", MontoMensual = 2100, MontoMensualTexto = "2 100", PlazoMeses = 24, PlazoTentativo = "24 meses", DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "18 May", Estado = "Aprobada" },
        new() { Id = 5, CodigoSolicitud = "SOL-0415", CodigoOperacion = "OP-1071", ClienteNombre = "Plásticos del Sur", DireccionLocal = "Av. Aviación 4012", DistritoLocal = "San Borja", MontoMensual = 3500, MontoMensualTexto = "3 500", PlazoMeses = 36, PlazoTentativo = "36 meses", DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "15 May", Estado = "Rechazada" },
        new() { Id = 6, CodigoSolicitud = "SOL-0418", CodigoOperacion = "OP-1068", ClienteNombre = "Restaurantes Bocca", DireccionLocal = "Av. Brasil 2890", DistritoLocal = "Magdalena", MontoMensual = 1950, MontoMensualTexto = "1 950", PlazoMeses = 24, PlazoTentativo = "24 meses", DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "14 May", Estado = "Aprobada" },
    };

    public IReadOnlyList<SolicitudAlquilerDto> All()
    {
        lock (_lock) return _data.ToList();
    }

    public SolicitudAlquilerDto? ByCodigo(string codigo)
    {
        lock (_lock) return _data.FirstOrDefault(s => s.CodigoSolicitud == codigo);
    }

    public void Insertar(SolicitudAlquilerDto solicitud)
    {
        lock (_lock) _data.Insert(0, solicitud);
    }

    public long SiguienteId()
    {
        lock (_lock) return _data.Count == 0 ? 1 : _data.Max(item => item.Id) + 1;
    }
}

// Implementacion de pantallas (demostrable sin backend): orquesta el SolicitudStore
// compartido y, en cada transicion, emite la notificacion al destinatario que
// corresponde (broker al reenviar a evaluacion; agente al cerrar la evaluacion).
public class MockSolicitudService(
    SolicitudStore store,
    IOportunidadService oportunidades,
    INotificacionService notificaciones,
    AppState app) : ISolicitudService
{
    public IReadOnlyList<SolicitudAlquilerDto> All() => store.All();
    public Task<IReadOnlyList<SolicitudAlquilerDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult(store.All());
    public SolicitudAlquilerDto? ByCodigo(string codigo) => store.ByCodigo(codigo);

    public SolicitudAlquilerDto Agregar(SolicitudFormRequest request)
    {
        var oportunidad = oportunidades.ById(request.OportunidadId)
            ?? throw new InvalidOperationException("Oportunidad comercial no encontrada.");
        if (request.MontoPropuesto <= 0)
            throw new InvalidOperationException("El monto propuesto debe ser mayor que cero.");
        if (string.IsNullOrWhiteSpace(request.PlazoTentativo))
            throw new InvalidOperationException("El plazo tentativo es obligatorio.");

        var id = store.SiguienteId();
        var digitosPlazo = new string(request.PlazoTentativo.TakeWhile(char.IsDigit).ToArray());
        var plazoMeses = int.TryParse(digitosPlazo, out var meses) ? meses : 0;
        var solicitud = new SolicitudAlquilerDto
        {
            Id = id,
            CodigoSolicitud = $"SOL-{id:0000}",
            CodigoOperacion = oportunidad.CodigoOportunidad,
            OportunidadId = oportunidad.Id,
            ClienteNombre = oportunidad.ClienteNombre,
            DireccionLocal = oportunidad.DireccionLocal,
            MontoMensual = request.MontoPropuesto,
            MontoMensualTexto = request.MontoPropuesto.ToString("N0"),
            PlazoMeses = plazoMeses,
            PlazoTentativo = request.PlazoTentativo,
            Observaciones = request.Observaciones ?? "",
            FechaRegistroTexto = DateTime.Today.ToString("dd MMM"),
            Estado = request.EnviarAEvaluacion ? "En revision" : "Registrada",
            DocumentosTexto = "0/0",
        };
        store.Insertar(solicitud);
        oportunidades.MarcarSolicitudCreada(oportunidad.Id);
        if (request.EnviarAEvaluacion)
            NotificarReenvio(solicitud);
        return solicitud;
    }

    public SolicitudAlquilerDto ReenviarAEvaluacion(string codigoSolicitud)
    {
        var solicitud = store.ByCodigo(codigoSolicitud)
            ?? throw new InvalidOperationException("Solicitud no encontrada.");
        // Guard de transicion: una solicitud ya cerrada no vuelve a evaluacion.
        if (solicitud.Estado is "Aprobada" or "Rechazada")
            throw new InvalidOperationException("La solicitud ya esta cerrada y no puede reenviarse a evaluacion.");
        solicitud.Estado = "En revision";
        NotificarReenvio(solicitud);
        return solicitud;
    }

    public EvaluacionSolicitudDto Evaluar(string codigoSolicitud, EvaluacionSolicitudDto evaluacion)
    {
        var solicitud = store.ByCodigo(codigoSolicitud)
            ?? throw new InvalidOperationException("Solicitud no encontrada.");
        if (!EnumCatalog.TiposEvaluacionSolicitud.Any(item => item.Code == evaluacion.TipoEvaluacion))
            throw new InvalidOperationException("El tipo de evaluacion no es valido.");
        if (!EnumCatalog.ResultadosEvaluacionSolicitud.Any(item => item.Code == evaluacion.Resultado))
            throw new InvalidOperationException("El resultado de evaluacion no es valido.");
        if (evaluacion.Resultado is "R" or "O" && string.IsNullOrWhiteSpace(evaluacion.Observaciones))
            throw new InvalidOperationException("Las observaciones son obligatorias al observar o rechazar.");

        evaluacion.Id = DateTime.UtcNow.Ticks;
        evaluacion.SolicitudId = solicitud.Id;
        evaluacion.FechaEvaluacionTexto = DateTime.Now.ToString("dd MMM yyyy HH:mm");
        solicitud.Estado = evaluacion.Resultado switch
        {
            "A" => "Aprobada",
            "R" => "Rechazada",
            _ => "Observada",
        };
        NotificarEvaluacion(solicitud, evaluacion);
        return evaluacion;
    }

    // Aviso al broker supervisor de que una solicitud llego (o volvio) a evaluacion.
    private void NotificarReenvio(SolicitudAlquilerDto solicitud)
    {
        var agente = app.CurrentUser?.Nombre ?? "Un agente";
        notificaciones.Crear(new NotificacionDto
        {
            Tipo = "Solicitud por evaluar",
            Mensaje = $"{agente} reenvio la solicitud {solicitud.CodigoSolicitud} ({solicitud.ClienteNombre}) a evaluacion.",
            Severidad = "MEDIA",
            Icono = "arrowRight",
            EntidadTipo = "Solicitud",
            EntidadRef = solicitud.CodigoSolicitud,
            Ruta = $"evaluacion/{solicitud.CodigoSolicitud}",
            DestinatarioRol = Roles.Broker,
        });
    }

    // Aviso al agente con el resultado de la evaluacion del broker.
    private void NotificarEvaluacion(SolicitudAlquilerDto solicitud, EvaluacionSolicitudDto evaluacion)
    {
        var (tipo, icono, severidad) = evaluacion.Resultado switch
        {
            "A" => ("Solicitud aprobada", "check", "INFO"),
            "R" => ("Solicitud rechazada", "alert", "ALTA"),
            _ => ("Solicitud observada", "alert", "MEDIA"),
        };
        var detalle = string.IsNullOrWhiteSpace(evaluacion.Observaciones)
            ? ""
            : $" Observacion: {evaluacion.Observaciones}";
        notificaciones.Crear(new NotificacionDto
        {
            Tipo = tipo,
            Mensaje = $"La solicitud {solicitud.CodigoSolicitud} ({solicitud.ClienteNombre}) fue {solicitud.Estado.ToLowerInvariant()}.{detalle}",
            Severidad = severidad,
            Icono = icono,
            EntidadTipo = "Solicitud",
            EntidadRef = solicitud.CodigoSolicitud,
            Ruta = $"solicitud-detail/{solicitud.CodigoSolicitud}",
            DestinatarioRol = Roles.Agente,
        });
    }
}

public class MockInteraccionService(IOportunidadService oportunidades) : IInteraccionService
{
    private readonly List<InteraccionComercialDto> _data = new()
    {
        new InteraccionComercialDto { Id = 1, OportunidadId = 1, FechaHoraTexto = "29 May 2026 · 14:30", CanalContacto = "L", ClienteNombre = "Jorge Martinez", CaptacionCodigo = "CAP-0218", Resultado = "I", Observaciones = "Cliente muy interesado en la zona de San Miguel. Seguimiento próxima semana.", NombreAgenteResponsable = "Valentina Mora" },
        new InteraccionComercialDto { Id = 2, OportunidadId = 2, FechaHoraTexto = "28 May 2026 · 11:15", CanalContacto = "W", ClienteNombre = "María Rodríguez", CaptacionCodigo = "CAP-0227", Resultado = "S", Observaciones = "Interesada en locales en Miraflores. Requiere metraje entre 50 y 100 m².", NombreAgenteResponsable = "Valentina Mora" },
        new InteraccionComercialDto { Id = 3, OportunidadId = 3, FechaHoraTexto = "27 May 2026 · 09:45", CanalContacto = "E", ClienteNombre = "Carlos González", CaptacionCodigo = "CAP-0234", Resultado = "N", Observaciones = "No sigue adelante por cambio de planes.", NombreAgenteResponsable = "Carolina Vega" },
        new InteraccionComercialDto { Id = 4, OportunidadId = 4, FechaHoraTexto = "26 May 2026 · 16:20", CanalContacto = "P", ClienteNombre = "Ana López", CaptacionCodigo = "CAP-0237", Resultado = "I", Observaciones = "Visitó el local. Muy conforme con las instalaciones.", NombreAgenteResponsable = "Andrea Torres" },
        new InteraccionComercialDto { Id = 5, OportunidadId = 5, FechaHoraTexto = "25 May 2026 · 13:00", CanalContacto = "L", ClienteNombre = "Pedro Sánchez", CaptacionCodigo = "CAP-0241", Resultado = "P", Observaciones = "Pendiente de respuesta del cliente.", NombreAgenteResponsable = "Paola Reyes" },
    };

    public IReadOnlyList<InteraccionComercialDto> All() => _data;
    public InteraccionComercialDto? ById(long id) => _data.FirstOrDefault(i => i.Id == id);

    public InteraccionComercialDto Agregar(InteraccionFormRequest request)
    {
        var oportunidad = oportunidades.ById(request.OportunidadId)
            ?? throw new InvalidOperationException("Oportunidad comercial no encontrada.");
        if (!EnumCatalog.CanalesContacto.Any(item => item.Code == request.CanalContacto))
            throw new InvalidOperationException("El canal de contacto no es valido.");
        if (!EnumCatalog.ResultadosInteraccion.Any(item => item.Code == request.Resultado))
            throw new InvalidOperationException("El resultado no es valido.");

        var interaccion = new InteraccionComercialDto
        {
            Id = _data.Count == 0 ? 1 : _data.Max(item => item.Id) + 1,
            OportunidadId = oportunidad.Id,
            FechaHoraTexto = DateTime.Now.ToString("dd MMM yyyy · HH:mm"),
            CanalContacto = request.CanalContacto,
            Resultado = request.Resultado,
            Observaciones = request.Observaciones ?? "",
            TranscripcionNota = request.TranscripcionNota ?? "",
            ClienteNombre = oportunidad.ClienteNombre,
            CaptacionCodigo = oportunidad.CaptacionCodigo,
            NombreAgenteResponsable = oportunidad.NombreAgenteResponsable,
        };
        _data.Insert(0, interaccion);
        return interaccion;
    }

    public InteraccionComercialDto Actualizar(long id, string? resultado = null, string? observaciones = null)
    {
        var interaccion = ById(id)
            ?? throw new InvalidOperationException("Interaccion comercial no encontrada.");
        if (resultado is not null)
        {
            if (!EnumCatalog.ResultadosInteraccion.Any(item => item.Code == resultado))
                throw new InvalidOperationException("El resultado no es valido.");
            interaccion.Resultado = resultado;
        }
        if (observaciones is not null)
            interaccion.Observaciones = observaciones.Trim();
        return interaccion;
    }
}

public class MockOportunidadService : IOportunidadService
{
    private static readonly OportunidadComercialDto[] Data =
    {
        // CAP-0218 tiene VARIOS clientes interesados → demuestra el puente N:M.
        new() { Id = 1, CodigoOportunidad = "OP-1083", CaptacionId = 1, CaptacionCodigo = "CAP-0218", DireccionLocal = "Av. La Marina 245", ClienteNombre = "Inversiones Trébol S.A.C.", NombreAgenteResponsable = "V. Mora", Estado = "En seguimiento", FechaRegistroTexto = "20 May 2026" },
        new() { Id = 2, CodigoOportunidad = "OP-1101", CaptacionId = 1, CaptacionCodigo = "CAP-0218", DireccionLocal = "Av. La Marina 245", ClienteNombre = "Café del Puerto S.A.C.", NombreAgenteResponsable = "V. Mora", Estado = "Solicitud creada", FechaRegistroTexto = "22 May 2026" },
        new() { Id = 3, CodigoOportunidad = "OP-1108", CaptacionId = 1, CaptacionCodigo = "CAP-0218", DireccionLocal = "Av. La Marina 245", ClienteNombre = "Boutique Andina E.I.R.L.", NombreAgenteResponsable = "V. Mora", Estado = "Abierta", FechaRegistroTexto = "27 May 2026" },
        new() { Id = 4, CodigoOportunidad = "OP-1094", CaptacionId = 2, CaptacionCodigo = "CAP-0227", DireccionLocal = "Calle Schell 412", ClienteNombre = "Boutique Lila", NombreAgenteResponsable = "V. Mora", Estado = "Solicitud creada", FechaRegistroTexto = "18 May 2026" },
        new() { Id = 5, CodigoOportunidad = "OP-1085", CaptacionId = 4, CaptacionCodigo = "CAP-0234", DireccionLocal = "Jr. Berlín 230", ClienteNombre = "Café Lima", NombreAgenteResponsable = "C. Vega", Estado = "Solicitud creada", FechaRegistroTexto = "16 May 2026" },
        new() { Id = 6, CodigoOportunidad = "OP-1090", CaptacionId = 3, CaptacionCodigo = "CAP-0231", DireccionLocal = "Av. Petit Thouars 1875", ClienteNombre = "Bodegas del Norte", NombreAgenteResponsable = "C. Vega", Estado = "Abierta", FechaRegistroTexto = "16 May 2026" },
    };

    public IReadOnlyList<OportunidadComercialDto> All() => Data;
    public Task<IReadOnlyList<OportunidadComercialDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<OportunidadComercialDto>>(Data);
    public OportunidadComercialDto? ById(long id) => Data.FirstOrDefault(o => o.Id == id);
    public IReadOnlyList<OportunidadComercialDto> ByCaptacion(string codigoCaptacion) =>
        Data.Where(o => o.CaptacionCodigo == codigoCaptacion).ToList();

    public OportunidadComercialDto Crear(OportunidadFormRequest request) =>
        throw new InvalidOperationException("El registro de oportunidades requiere el servicio REST.");

    public OportunidadComercialDto MarcarSolicitudCreada(long id)
    {
        var oportunidad = Requerir(id);
        oportunidad.Estado = "Solicitud creada";
        return oportunidad;
    }

    public OportunidadComercialDto CerrarNoContinua(long id, string razon, string? observaciones)
    {
        var oportunidad = Requerir(id);
        if (!EnumCatalog.MotivosNoContinuidad.Any(item => item.Code == razon))
            throw new InvalidOperationException("El motivo de no continuidad no es valido.");
        var motivo = EnumCatalog.LabelFor(EnumCatalog.MotivosNoContinuidad, razon);
        oportunidad.Estado = "No continua";
        oportunidad.MotivoCierre = motivo;
        oportunidad.Observaciones = observaciones?.Trim() ?? "";
        oportunidad.FechaCierreTexto = DateTime.Now.ToString("dd MMM yyyy HH:mm");
        return oportunidad;
    }

    public Task<OportunidadComercialDto> CerrarExitosaAsync(long id, CancellationToken ct = default)
    {
        var oportunidad = Requerir(id);
        oportunidad.Estado = "Finalizada exitosa";
        oportunidad.FechaCierreTexto = DateTime.Now.ToString("dd MMM yyyy HH:mm");
        return Task.FromResult(oportunidad);
    }

    private static OportunidadComercialDto Requerir(long id) =>
        Data.FirstOrDefault(item => item.Id == id)
        ?? throw new InvalidOperationException("Oportunidad comercial no encontrada.");
}

public class MockVisitaService(IOportunidadService oportunidades) : IVisitaService
{
    // Agente autenticado en el prototipo (espeja al usuario en sesión).
    private const string AgenteSesion = "V. Mora";

    private readonly List<VisitaDto> _data = new()
    {
        new() { Id = 1, OportunidadId = 1, FechaTexto = "24 May 2026", HoraTexto = "16:00", CodigoCaptacion = "CAP-0218", ClienteNombre = "Inversiones Trébol", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", NombreAgente = "V. Mora", Estado = "P" },
        new() { Id = 2, OportunidadId = 2, FechaTexto = "25 May 2026", HoraTexto = "09:30", CodigoCaptacion = "CAP-0234", ClienteNombre = "Café Lima", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", NombreAgente = "V. Mora", Estado = "P" },
        new() { Id = 3, OportunidadId = 3, FechaTexto = "26 May 2026", HoraTexto = "10:00", CodigoCaptacion = "CAP-0231", ClienteNombre = "Bodegas del Norte", DireccionLocal = "Av. Petit Thouars 1875", DistritoLocal = "Jesús María", NombreAgente = "D. Romero", Estado = "P" },
        new() { Id = 4, OportunidadId = 4, FechaTexto = "22 May 2026", HoraTexto = "11:00", CodigoCaptacion = "CAP-0234", ClienteNombre = "Café Lima", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", NombreAgente = "V. Mora", Estado = "R", Resultado = "S", Observaciones = "Cliente conforme · solicita propuesta" },
        new() { Id = 5, OportunidadId = 5, FechaTexto = "20 May 2026", HoraTexto = "15:30", CodigoCaptacion = "CAP-0242", ClienteNombre = "Carla Espinoza", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", NombreAgente = "C. Vega", Estado = "C", Observaciones = "Cliente desistió" },
        new() { Id = 6, OportunidadId = 6, FechaTexto = "18 May 2026", HoraTexto = "10:30", CodigoCaptacion = "CAP-0238", ClienteNombre = "Plásticos del Sur", DireccionLocal = "Av. Aviación 4012", DistritoLocal = "San Borja", NombreAgente = "M. León", Estado = "G", Observaciones = "Cliente solicitó reagendar" },
    };

    private long _nextId = 6;

    public IReadOnlyList<VisitaDto> All() => _data;

    public Task<IReadOnlyList<VisitaDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<VisitaDto>>(_data);

    public VisitaDto? ById(long id) => _data.FirstOrDefault(v => v.Id == id);

    public VisitaDto Programar(VisitaFormRequest request)
    {
        var oportunidad = oportunidades.ById(request.OportunidadId)
            ?? throw new InvalidOperationException("Debes seleccionar una oportunidad comercial.");
        if (string.IsNullOrWhiteSpace(request.FechaTexto) || string.IsNullOrWhiteSpace(request.HoraTexto))
            throw new InvalidOperationException("La fecha y la hora de la visita son obligatorias.");

        var visita = new VisitaDto
        {
            Id = ++_nextId,
            OportunidadId = oportunidad.Id,
            CodigoCaptacion = oportunidad.CaptacionCodigo,
            ClienteNombre = oportunidad.ClienteNombre,
            DireccionLocal = oportunidad.DireccionLocal,
            FechaTexto = request.FechaTexto,
            HoraTexto = request.HoraTexto,
            NombreAgente = AgenteSesion,
            Estado = "P",
            Observaciones = request.Observaciones,
        };
        _data.Insert(0, visita);
        return visita;
    }

    public VisitaDto Reprogramar(long id, string fechaTexto, string horaTexto)
    {
        var visita = Requerir(id);
        AsegurarModificable(visita, "reprogramar");
        if (string.IsNullOrWhiteSpace(fechaTexto) || string.IsNullOrWhiteSpace(horaTexto))
            throw new InvalidOperationException("La nueva fecha y hora son obligatorias para reprogramar.");
        visita.FechaTexto = fechaTexto;
        visita.HoraTexto = horaTexto;
        visita.Estado = "G";
        return visita;
    }

    public VisitaDto Cancelar(long id, string motivo)
    {
        var visita = Requerir(id);
        AsegurarModificable(visita, "cancelar");
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar el motivo de la cancelación.");
        visita.Estado = "C";
        visita.Observaciones = motivo;
        return visita;
    }

    public VisitaDto MarcarRealizada(long id)
    {
        var visita = Requerir(id);
        AsegurarModificable(visita, "marcar como realizada");
        visita.Estado = "R";
        visita.Resultado = null;
        return visita;
    }

    public VisitaDto MarcarNoRealizada(long id, string motivo)
    {
        var visita = Requerir(id);
        AsegurarModificable(visita, "marcar como no realizada");
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes indicar por que la visita no se realizo.");
        visita.Estado = "N";
        visita.Observaciones = motivo.Trim();
        visita.Resultado = null;
        visita.NivelInteres = null;
        visita.ObjecionPrincipal = null;
        visita.OpinionPrecio = null;
        visita.ProximaAccion = null;
        return visita;
    }

    public VisitaDto RegistrarResultado(long id, VisitaResultadoRequest request)
    {
        var visita = Requerir(id);
        if (visita.Estado != "R" || !string.IsNullOrWhiteSpace(visita.Resultado))
            throw new InvalidOperationException(
                "Solo una visita realizada y sin resultado admite registrar el desenlace.");
        if (!EnumCatalog.ResultadosInteraccion.Any(item => item.Code == request.Resultado))
            throw new InvalidOperationException("El resultado de la visita es obligatorio.");
        if ((request.Resultado is "N" or "D") && request.NivelInteres is not null)
            throw new InvalidOperationException(
                "No se debe registrar nivel de interes cuando el cliente no continua.");
        if (request.NivelInteres is < 1 or > 5)
            throw new InvalidOperationException("El nivel de interes debe estar entre 1 y 5.");
        if (!CodigoOpcionalValido(EnumCatalog.ObjecionesVisita, request.ObjecionPrincipal)
            || !CodigoOpcionalValido(EnumCatalog.OpinionesPrecio, request.OpinionPrecio)
            || !CodigoOpcionalValido(EnumCatalog.ProximasAccionesVisita, request.ProximaAccion))
            throw new InvalidOperationException("El desenlace cualitativo contiene un valor invalido.");

        visita.Resultado = request.Resultado;
        visita.NivelInteres = (request.Resultado is "N" or "D") ? null : request.NivelInteres;
        visita.ObjecionPrincipal = request.ObjecionPrincipal;
        visita.OpinionPrecio = request.OpinionPrecio;
        visita.ProximaAccion = request.ProximaAccion;
        if (!string.IsNullOrWhiteSpace(request.Observaciones))
            visita.Observaciones = request.Observaciones;

        // Desenlace de no continuidad: el motivo es obligatorio. Espeja el backend:
        // registra el motivo (ligado a la visita) y cierra la oportunidad.
        if (request.Resultado is "N" or "D")
        {
            if (string.IsNullOrWhiteSpace(request.RazonNoContinuidad))
                throw new InvalidOperationException("Debes indicar el motivo de no continuidad.");
            var motivoLabel = EnumCatalog.LabelFor(EnumCatalog.MotivosNoContinuidad, request.RazonNoContinuidad);
            visita.Observaciones = string.IsNullOrWhiteSpace(request.Observaciones)
                ? $"No continúa · {motivoLabel}"
                : $"{request.Observaciones} · No continúa: {motivoLabel}";
            oportunidades.CerrarNoContinua(visita.OportunidadId, request.RazonNoContinuidad, request.Observaciones);
        }
        return visita;
    }

    private static bool CodigoOpcionalValido(IEnumerable<EnumOption> opciones, string? codigo) =>
        string.IsNullOrWhiteSpace(codigo) || opciones.Any(item => item.Code == codigo);

    private VisitaDto Requerir(long id) =>
        _data.FirstOrDefault(v => v.Id == id)
        ?? throw new InvalidOperationException("Visita no encontrada.");

    // Solo una visita PROGRAMADA o REPROGRAMADA admite cambios; REALIZADA/CANCELADA son terminales.
    private static void AsegurarModificable(VisitaDto visita, string accion)
    {
        if (visita.Estado != "P" && visita.Estado != "G")
            throw new InvalidOperationException(
                $"No se puede {accion} una visita {EnumCatalog.LabelFor(EnumCatalog.EstadosVisita, visita.Estado).ToLower()}.");
    }
}

public class MockAssignmentService : IAssignmentService
{
    private static readonly AssignAgentDto[] AgentData =
    {
        new() { Id = "a1", Iniciales = "DR", Nombre = "Daniel Romero", NumeroDocumento = "DNI 44 102 776", EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible", BrokerActual = "Mariana Quintero", Seleccionable = true, EsAdministrador = false },
        new() { Id = "a2", Iniciales = "ML", Nombre = "Matías León", NumeroDocumento = "DNI 45 778 002", EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible", BrokerActual = "Felipe Andrade", Seleccionable = true, EsAdministrador = false },
        new() { Id = "a3", Iniciales = "AT", Nombre = "Andrea Torres", NumeroDocumento = "DNI 47 412 008", EstadoAdministrativo = "Activo", EstadoOperativo = "En licencia", BrokerActual = "Sandra Ríos", Seleccionable = false, MotivoNoDisponible = "En licencia — no recibe asignaciones", EsAdministrador = false },
        new() { Id = "a4", Iniciales = "JS", Nombre = "Joaquín Soto", NumeroDocumento = "DNI 44 991 332", EstadoAdministrativo = "Inactivo", EstadoOperativo = "No disponible", BrokerActual = "—", Seleccionable = false, MotivoNoDisponible = "Agente inactivo", EsAdministrador = false },
        new() { Id = "a5", Iniciales = "AT", Nombre = "Alejandro Téllez", NumeroDocumento = "DNI 06 220 884", EstadoAdministrativo = "Activo", EstadoOperativo = "Global", BrokerActual = "—", Seleccionable = false, MotivoNoDisponible = "Administrador global — sin asignación operativa", EsAdministrador = true },
    };

    private static readonly AssignBrokerDto[] BrokerData =
{
    new() { Id = "b1", Iniciales = "RS", Nombre = "Ricardo Salas", Zona = "Lima Centro / Sur", EstadoAdministrativo = "Activo", TipoBroker = "supervisor", AgentesACargo = 7, Seleccionable = true, EsAdministrador = false },
    new() { Id = "b2", Iniciales = "FA", Nombre = "Felipe Andrade", Zona = "Lima Este", EstadoAdministrativo = "Activo", TipoBroker = "supervisor", AgentesACargo = 5, Seleccionable = true, EsAdministrador = false },
    new() { Id = "b3", Iniciales = "ET", Nombre = "Elena Tafur", Zona = "Lima Moderna", EstadoAdministrativo = "Inactivo", TipoBroker = "supervisor", AgentesACargo = 0, Seleccionable = false, MotivoNoDisponible = "Broker inactivo", EsAdministrador = false },
    new() { Id = "b4", Iniciales = "AT", Nombre = "Alejandro Téllez", Zona = "Global", EstadoAdministrativo = "Activo", TipoBroker = "admin", AgentesACargo = 0, Seleccionable = false, MotivoNoDisponible = "No se puede asignar a un broker administrador", EsAdministrador = true },
};

    private readonly List<BrokerAgenteDto> _historial = new()
    {
        new() { Id = 1, AgenteNombre = "Daniel Romero", BrokerAnteriorNombre = "M. Quintero", BrokerNuevoNombre = "R. Salas", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "22 May 2026", Motivo = "Cese del broker anterior", Estado = "Activa" },
        new() { Id = 2, AgenteNombre = "Matías León", BrokerAnteriorNombre = "S. Ríos", BrokerNuevoNombre = "F. Andrade", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "18 May 2026", Motivo = "Redistribución de zona", Estado = "Activa" },
        new() { Id = 3, AgenteNombre = "Carla Núñez", BrokerAnteriorNombre = "R. Salas", BrokerNuevoNombre = "S. Ríos", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "09 May 2026", Motivo = "Solicitud escalada del equipo", Estado = "Cerrada" },
        new() { Id = 4, AgenteNombre = "Pedro Vidal", BrokerAnteriorNombre = "F. Andrade", BrokerNuevoNombre = "R. Salas", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "02 May 2026", Motivo = "Afinidad de zona", Estado = "Cerrada" },
    };

    public IReadOnlyList<AssignAgentDto> Agents() => AgentData;
    public IReadOnlyList<AssignBrokerDto> Brokers() => BrokerData;
    public IReadOnlyList<BrokerAgenteDto> Historial() => _historial;

    public BrokerAgenteDto ReasignarAgente(string agenteId, string brokerId, string motivo)
    {
        var agente = AgentData.FirstOrDefault(a => a.Id == agenteId)
            ?? throw new InvalidOperationException("Agente no encontrado.");
        var broker = BrokerData.FirstOrDefault(b => b.Id == brokerId)
            ?? throw new InvalidOperationException("Broker destino no encontrado.");

        if (!agente.Seleccionable)
            throw new InvalidOperationException("El agente seleccionado no está disponible para asignación.");
        if (broker.EsAdministrador)
            throw new InvalidOperationException("No puedes asignar agentes a un broker administrador.");
        if (!broker.Seleccionable)
            throw new InvalidOperationException("El broker destino no está activo.");
        if (agente.BrokerActual == broker.Nombre)
            throw new InvalidOperationException("Este agente ya pertenece al broker seleccionado.");
        if (string.IsNullOrWhiteSpace(motivo))
            throw new InvalidOperationException("Debes ingresar un motivo para continuar.");

        // La supervisión anterior del agente queda cerrada desde hoy.
        foreach (var h in _historial.Where(h => h.AgenteNombre == agente.Nombre && h.Estado == "Activa"))
            h.Estado = "Cerrada";

        var registro = new BrokerAgenteDto
        {
            Id = _historial.Max(h => h.Id) + 1,
            AgenteNombre = agente.Nombre,
            BrokerAnteriorNombre = agente.BrokerActual,
            BrokerNuevoNombre = broker.Nombre,
            BrokerAdministradorNombre = "Administración",
            FechaAsignacionTexto = DateTime.Today.ToString("dd MMM yyyy", System.Globalization.CultureInfo.InvariantCulture),
            Motivo = motivo,
            Estado = "Activa",
        };
        _historial.Insert(0, registro);
        agente.BrokerActual = broker.Nombre;
        return registro;
    }
}

public class MockReasignacionCaptacionService : IReasignacionCaptacionService
{
    // Broker responsable autenticado (Ricardo Salas en el prototipo).
    private const long BrokerResponsableId = 1;
    private const string BrokerResponsableNombre = "Ricardo Salas";

    // Estados de captación que NO admiten reasignación.
    private static readonly string[] EstadosNoReasignables = { "Rechazada", "Cerrada", "Vencida" };

    // Captaciones del equipo del broker (con su agente responsable actual).
    private static readonly CaptacionDto[] CaptacionesData =
    {
        new() { Id = 1, CodigoCaptacion = "CAP-0218", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", AgenteResponsableId = 1, NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "01 Abr – 01 Oct 2026", DiasRestantesTexto = "venc. en 130 días", Estado = "Activa" },
        new() { Id = 2, CodigoCaptacion = "CAP-0227", DireccionLocal = "Calle Schell 412", DistritoLocal = "Miraflores", AgenteResponsableId = 1, NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "15 May – 15 Nov 2026", DiasRestantesTexto = "venc. en 175 días", Estado = "Activa" },
        new() { Id = 3, CodigoCaptacion = "CAP-0234", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", AgenteResponsableId = 2, NombreAgenteResponsable = "Carolina Vega", VigenciaTexto = "10 May – 10 Nov 2026", DiasRestantesTexto = "venc. en 170 días", Estado = "Activa" },
        new() { Id = 4, CodigoCaptacion = "CAP-0237", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", AgenteResponsableId = 4, NombreAgenteResponsable = "Paola Reyes", VigenciaTexto = "En revisión", DiasRestantesTexto = "enviada hace 1d", Estado = "Observada" },
        new() { Id = 5, CodigoCaptacion = "CAP-0241", DireccionLocal = "Av. Perú 2845", DistritoLocal = "San Martín", AgenteResponsableId = 1, NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "Observada", DiasRestantesTexto = "requiere ajuste", Estado = "Observada" },
    };

    // Agentes del equipo candidatos a recibir la captación.
    private static readonly AgenteDto[] AgentesData =
    {
        new() { Id = 1, Iniciales = "VM", Color = "#3A6BB5", Nombre = "Valentina Mora", NumeroDocumento = "45 893 211", Zona = "Lima Centro", CaptacionesActivas = 14, EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible" },
        new() { Id = 2, Iniciales = "CV", Color = "#2F7D52", Nombre = "Carolina Vega", NumeroDocumento = "46 220 411", Zona = "Lima Sur", CaptacionesActivas = 9, EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible" },
        new() { Id = 3, Iniciales = "AT", Color = "#C0473C", Nombre = "Andrea Torres", NumeroDocumento = "47 412 008", Zona = "Callao", CaptacionesActivas = 6, EstadoAdministrativo = "Activo", EstadoOperativo = "En licencia" },
        new() { Id = 4, Iniciales = "PR", Color = "#00AEEF", Nombre = "Paola Reyes", NumeroDocumento = "45 002 884", Zona = "Lima Centro", CaptacionesActivas = 7, EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible" },
        new() { Id = 5, Iniciales = "JM", Color = "#2C77A0", Nombre = "Jorge Marín", NumeroDocumento = "44 728 101", Zona = "Lima Moderna", CaptacionesActivas = 5, EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible" },
        new() { Id = 6, Iniciales = "NS", Color = "#948D81", Nombre = "Natalia Solano", NumeroDocumento = "46 998 220", Zona = "Lima Sur", CaptacionesActivas = 0, EstadoAdministrativo = "Inactivo", EstadoOperativo = "No disponible" },
    };

    private readonly List<ReasignacionCaptacionDto> _historial = new()
    {
        new() { IdReasignacion = 3, CodigoCaptacion = "CAP-0205", DireccionLocal = "Av. Angamos 1290", AgenteAnteriorNombre = "Carolina Vega", AgenteNuevoNombre = "Valentina Mora", BrokerResponsableNombre = "Ricardo Salas", FechaCambioTexto = "20 May 2026", Motivo = "Balance de carga del equipo" },
        new() { IdReasignacion = 2, CodigoCaptacion = "CAP-0198", DireccionLocal = "Jr. de la Unión 540", AgenteAnteriorNombre = "Andrea Torres", AgenteNuevoNombre = "Carolina Vega", BrokerResponsableNombre = "Ricardo Salas", FechaCambioTexto = "14 May 2026", Motivo = "Especialización por zona" },
        new() { IdReasignacion = 1, CodigoCaptacion = "CAP-0187", DireccionLocal = "Av. Arequipa 3100", AgenteAnteriorNombre = "Valentina Mora", AgenteNuevoNombre = "Paola Reyes", BrokerResponsableNombre = "Ricardo Salas", FechaCambioTexto = "08 May 2026", Motivo = "Redistribución tras nueva captación" },
    };

    private long _nextId = 4;

    public IReadOnlyList<CaptacionDto> Reasignables() =>
        CaptacionesData.Where(c => !EstadosNoReasignables.Contains(c.Estado)).ToList();

    public IReadOnlyList<AgenteDto> AgentesDestino() => AgentesData;

    public IReadOnlyList<ReasignacionCaptacionDto> Historial() => _historial;

    public ReasignacionCaptacionDto Reasignar(ReasignarCaptacionRequest request)
    {
        var captacion = CaptacionesData.FirstOrDefault(c => c.Id == request.CaptacionId)
            ?? throw new InvalidOperationException("Captación no encontrada.");
        var agenteNuevo = AgentesData.FirstOrDefault(a => a.Id == request.AgenteNuevoId)
            ?? throw new InvalidOperationException("Agente destino no encontrado.");

        // Reglas espejo del backend: agente disponible, distinto al actual y motivo obligatorio.
        if (agenteNuevo.EstadoAdministrativo != "Activo" || agenteNuevo.EstadoOperativo != "Disponible")
            throw new InvalidOperationException("El agente destino no está disponible para asignación.");
        if (agenteNuevo.Id == captacion.AgenteResponsableId)
            throw new InvalidOperationException("La captación ya está asignada a ese agente.");
        if (string.IsNullOrWhiteSpace(request.Motivo))
            throw new InvalidOperationException("Debes ingresar un motivo para continuar.");

        var registro = new ReasignacionCaptacionDto
        {
            IdReasignacion = ++_nextId,
            CaptacionId = captacion.Id,
            CodigoCaptacion = captacion.CodigoCaptacion,
            DireccionLocal = captacion.DireccionLocal,
            AgenteAnteriorId = captacion.AgenteResponsableId ?? 0,
            AgenteAnteriorNombre = captacion.NombreAgenteResponsable,
            AgenteNuevoId = agenteNuevo.Id,
            AgenteNuevoNombre = agenteNuevo.Nombre,
            BrokerResponsableId = BrokerResponsableId,
            BrokerResponsableNombre = BrokerResponsableNombre,
            FechaCambio = DateTime.Now,
            FechaCambioTexto = $"Hoy · {DateTime.Now:HH:mm}",
            Motivo = request.Motivo,
        };

        // Aplica el cambio sobre la captación (agente responsable) y registra el historial.
        captacion.AgenteResponsableId = agenteNuevo.Id;
        captacion.NombreAgenteResponsable = agenteNuevo.Nombre;
        _historial.Insert(0, registro);
        return registro;
    }
}
