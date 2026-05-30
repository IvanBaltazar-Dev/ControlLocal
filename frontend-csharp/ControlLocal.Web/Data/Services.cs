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

namespace ControlLocal.Web.Data;

// Service interfaces — the screens depend only on these. The in-memory mock
// implementations below can later be swapped for HttpClient-backed ones that
// call the Java REST API, with no changes to the components. They already work
// with the domain DTOs (Models/*) so the contract is the final one.

public interface IBrokerService
{
    IReadOnlyList<BrokerDto> All();
    BrokerDto? ById(string codigoBroker);
    BrokerDto First();
}

public interface IAgenteService
{
    IReadOnlyList<AgenteDto> All();
    AgenteDto? ById(long id);
}

public interface IPropietarioService
{
    IReadOnlyList<PropietarioDto> All();
    PropietarioDto? ById(long id);
}

public interface IClienteService
{
    IReadOnlyList<ClienteInteresadoDto> All();
    ClienteInteresadoDto? ById(long id);
}

public interface ILocalService
{
    IReadOnlyList<LocalComercialDto> All();
    LocalComercialDto? ById(long id);
}

// Prospección (pre-captación): el agente persigue al propietario hasta captar el
// local. Espejo, del lado de la oferta, de la oportunidad. Incluye el embudo y el
// seguimiento de recontacto (≤15 días) tras un "por ahora no".
public interface IProspeccionService
{
    IReadOnlyList<ProspeccionDto> All();
    ProspeccionDto? ById(long id);
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
}

public interface ICaptacionService
{
    IReadOnlyList<CaptacionDto> All();
    IReadOnlyList<BandejaCaptacionDto> Bandeja();
    CaptacionDto? ByCodigo(string codigo);
}

public interface ISolicitudService
{
    IReadOnlyList<SolicitudAlquilerDto> All();
    SolicitudAlquilerDto? ByCodigo(string codigo);
}

public interface IInteraccionService
{
    IReadOnlyList<InteraccionComercialDto> All();
    InteraccionComercialDto? ById(long id);
}

// Espeja VisitaBusinessLogic del backend Java: listado + transiciones de estado
// de la cita (programar / reprogramar / cancelar / registrar resultado).
// Oportunidad comercial = cliente interesado + captación (puente N:M del modelo).
// Una misma captación puede tener varias oportunidades (varios clientes interesados).
public interface IOportunidadService
{
    IReadOnlyList<OportunidadComercialDto> All();
    OportunidadComercialDto? ById(long id);
    // Todos los clientes interesados (oportunidades) de una captación.
    IReadOnlyList<OportunidadComercialDto> ByCaptacion(string codigoCaptacion);
}

public interface IVisitaService
{
    IReadOnlyList<VisitaDto> All();
    VisitaDto? ById(long id);
    // POST /visitas — programa una nueva visita (estado inicial PROGRAMADA).
    VisitaDto Programar(VisitaFormRequest request);
    // PATCH /visitas/{id}/reprogramar — mueve fecha/hora (estado REPROGRAMADA).
    VisitaDto Reprogramar(long id, string fechaTexto, string horaTexto);
    // PATCH /visitas/{id}/cancelar — cancela con motivo (estado CANCELADA).
    VisitaDto Cancelar(long id, string motivo);
    // PATCH /visitas/{id}/resultado — marca REALIZADA y registra el desenlace.
    // Si el resultado es de no continuidad (N/D), razonNoContinuidad es obligatorio:
    // registra el motivo ligado a la visita y cierra la oportunidad.
    VisitaDto RegistrarResultado(long id, string resultado, string? observaciones, string? razonNoContinuidad);
}

public interface IAssignmentService
{
    IReadOnlyList<AssignAgentDto> Agents();
    IReadOnlyList<AssignBrokerDto> Brokers();
    // Historial de reasignaciones de agentes entre brokers (relación BrokerAgente).
    IReadOnlyList<BrokerAgenteDto> Historial();
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
    private static readonly BrokerDto[] Data =
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
}

public class MockAgenteService : IAgenteService
{
    private static readonly AgenteDto[] Data =
    {
        new() { Id = 1, Iniciales = "VM", Color = "#3A6BB5", Nombre = "Valentina Mora", Email = "vmora@controllocal.pe", NumeroDocumento = "45 893 211", Zona = "Lima Centro", FechaIngresoTexto = "14 Feb 2024", CaptacionesActivas = 14, OportunidadesActivas = 8, EstadoAdministrativo = "Activo" },
        new() { Id = 2, Iniciales = "CV", Color = "#2F7D52", Nombre = "Carolina Vega", Email = "cvega@controllocal.pe", NumeroDocumento = "46 220 411", Zona = "Lima Sur", FechaIngresoTexto = "22 Mar 2024", CaptacionesActivas = 9, OportunidadesActivas = 5, EstadoAdministrativo = "Activo" },
        new() { Id = 3, Iniciales = "AT", Color = "#C0473C", Nombre = "Andrea Torres", Email = "atorres@controllocal.pe", NumeroDocumento = "47 412 008", Zona = "Callao", FechaIngresoTexto = "06 May 2024", CaptacionesActivas = 6, OportunidadesActivas = 3, EstadoAdministrativo = "Activo" },
        new() { Id = 4, Iniciales = "PR", Color = "#C98A1E", Nombre = "Paola Reyes", Email = "preyes@controllocal.pe", NumeroDocumento = "45 002 884", Zona = "Lima Centro", FechaIngresoTexto = "18 Jul 2024", CaptacionesActivas = 7, OportunidadesActivas = 4, EstadoAdministrativo = "Activo" },
        new() { Id = 5, Iniciales = "JM", Color = "#2C77A0", Nombre = "Jorge Marín", Email = "jmarin@controllocal.pe", NumeroDocumento = "44 728 101", Zona = "Lima Moderna", FechaIngresoTexto = "03 Sep 2024", CaptacionesActivas = 5, OportunidadesActivas = 2, EstadoAdministrativo = "Activo" },
        new() { Id = 6, Iniciales = "NS", Color = "#948D81", Nombre = "Natalia Solano", Email = "nsolano@controllocal.pe", NumeroDocumento = "46 998 220", Zona = "Lima Sur", FechaIngresoTexto = "19 Oct 2024", CaptacionesActivas = 0, OportunidadesActivas = 0, EstadoAdministrativo = "Inactivo" },
    };

    public IReadOnlyList<AgenteDto> All() => Data;
    public AgenteDto? ById(long id) => Data.FirstOrDefault(a => a.Id == id);
}

public class MockPropietarioService : IPropietarioService
{
    private static readonly PropietarioDto[] Data =
    {
        new() { Id = 1, Nombre = "Inmobiliaria Pacífico S.A.C.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 553 102 884", Telefono = "+51 1 432 8800", Correo = "contacto@pacifico.com.pe", CantidadLocales = 6, Estado = "Activo" },
        new() { Id = 2, Nombre = "Carlos Mendoza Rivera", TipoPersona = "Persona natural · DNI", NumeroDocumento = "08 412 991", Telefono = "+51 998 220 411", Correo = "cmendoza@gmail.com", CantidadLocales = 2, Estado = "Activo" },
        new() { Id = 3, Nombre = "Grupo Bermúdez E.I.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 502 998 110", Telefono = "+51 1 222 1108", Correo = "admin@bermudez.pe", CantidadLocales = 4, Estado = "Activo" },
        new() { Id = 4, Nombre = "Ana Lucía Pereyra", TipoPersona = "Persona natural · DNI", NumeroDocumento = "09 778 002", Telefono = "+51 987 412 008", Correo = "alpereyra@hotmail.com", CantidadLocales = 1, Estado = "Activo" },
        new() { Id = 5, Nombre = "Comercial Andina S.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 471 220 008", Telefono = "+51 1 718 4400", Correo = "ventas@andina.com.pe", CantidadLocales = 3, Estado = "Inactivo" },
        new() { Id = 6, Nombre = "Roberto Linares Cruz", TipoPersona = "Persona natural · DNI", NumeroDocumento = "07 823 145", Telefono = "+51 991 552 008", Correo = "rlinares@yahoo.com", CantidadLocales = 1, Estado = "Activo" },
    };

    public IReadOnlyList<PropietarioDto> All() => Data;
    public PropietarioDto? ById(long id) => Data.FirstOrDefault(p => p.Id == id);
}

public class MockClienteService : IClienteService
{
    private static readonly ClienteInteresadoDto[] Data =
    {
        new() { Id = 1, Nombre = "Inversiones Trébol S.A.C.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 558 110 442", Telefono = "+51 1 445 7800", RubroInteres = "Restaurante / Café", InteresComercial = "Locales 80–150 m² en Lima moderna", CaptacionesVinculadas = 2, Estado = "Activo" },
        new() { Id = 2, Nombre = "Boutique Lila", TipoPersona = "Persona natural · DNI", NumeroDocumento = "45 220 118", Telefono = "+51 998 552 110", RubroInteres = "Moda / Boutique", InteresComercial = "Local en Miraflores < 80 m²", CaptacionesVinculadas = 1, Estado = "Activo" },
        new() { Id = 3, Nombre = "Bodegas del Norte E.I.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 471 882 004", Telefono = "+51 1 552 1180", RubroInteres = "Retail / Almacén", InteresComercial = "Locales > 150 m² con depósito", CaptacionesVinculadas = 3, Estado = "Activo" },
        new() { Id = 4, Nombre = "Café Lima", TipoPersona = "Persona natural · DNI", NumeroDocumento = "44 118 902", Telefono = "+51 987 220 004", RubroInteres = "Café / Postres", InteresComercial = "Esquinas comerciales en Miraflores", CaptacionesVinculadas = 1, Estado = "Activo" },
        new() { Id = 5, Nombre = "Carla Espinoza Núñez", TipoPersona = "Persona natural · DNI", NumeroDocumento = "46 552 008", Telefono = "+51 991 002 552", RubroInteres = "Servicios / Consultorio", InteresComercial = "Oficina-local en San Isidro", CaptacionesVinculadas = 1, Estado = "Activo" },
        new() { Id = 6, Nombre = "Distribuidora El Sol S.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 502 110 778", Telefono = "+51 1 718 2200", RubroInteres = "Retail", InteresComercial = "Locales en avenidas principales", CaptacionesVinculadas = 0, Estado = "Inactivo" },
    };

    public IReadOnlyList<ClienteInteresadoDto> All() => Data;
    public ClienteInteresadoDto? ById(long id) => Data.FirstOrDefault(c => c.Id == id);
}

public class MockLocalService : ILocalService
{
    private static readonly LocalComercialDto[] Data =
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

    public IReadOnlyList<LocalComercialDto> All() => Data;
    public LocalComercialDto? ById(long id) => Data.FirstOrDefault(l => l.Id == id);
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
        var p = EnProceso(id, "captar");
        if (comisionPactada < 0)
            throw new InvalidOperationException("La comisión pactada no puede ser negativa.");
        p.Estado = "T";
        p.ResultadoPropuesta = "A";
        p.CaptacionCodigo = $"CAP-{1000 + p.Id:0000}"; // simula la captación creada (pendiente de revisión)
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
    private static readonly CaptacionDto[] Data =
    {
        new() { CodigoCaptacion = "CAP-0218", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", AreaM2 = 120, PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "01 Abr – 01 Oct 2026", DiasRestantesTexto = "venc. en 130 días", ComisionPactadaTexto = "5.0", Estado = "Activa" },
        new() { CodigoCaptacion = "CAP-0226", DireccionLocal = "Calle Schell 412", DistritoLocal = "Miraflores", AreaM2 = 68, PropietarioNombre = "Carlos Mendoza", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "15 May – 15 Nov 2026", DiasRestantesTexto = "venc. en 175 días", ComisionPactadaTexto = "4.5", Estado = "Activa" },
        new() { CodigoCaptacion = "CAP-0233", DireccionLocal = "Av. Caminos del Inca 1820", DistritoLocal = "Surco", AreaM2 = 140, PropietarioNombre = "A. Pereyra", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "Borrador", DiasRestantesTexto = "no enviado", ComisionPactadaTexto = "—", Estado = "Pendiente" },
        new() { CodigoCaptacion = "CAP-0234", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", AreaM2 = 52, PropietarioNombre = "A. Pereyra", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "10 May – 10 Nov 2026", DiasRestantesTexto = "venc. en 170 días", ComisionPactadaTexto = "5.5", Estado = "Activa" },
        new() { CodigoCaptacion = "CAP-0237", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", AreaM2 = 88, PropietarioNombre = "R. Linares", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "En revisión", DiasRestantesTexto = "enviada hace 1d", ComisionPactadaTexto = "5.0", Estado = "Pendiente" },
        new() { CodigoCaptacion = "CAP-0241", DireccionLocal = "Av. Perú 2845", DistritoLocal = "San Martín", AreaM2 = 72, PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "Observada", DiasRestantesTexto = "requiere ajuste", ComisionPactadaTexto = "4.5", Estado = "Observada" },
        new() { CodigoCaptacion = "CAP-0244", DireccionLocal = "Av. Brasil 2890", DistritoLocal = "Magdalena", AreaM2 = 60, PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "23 May – 23 Ago 2026", DiasRestantesTexto = "venc. en 91 días", ComisionPactadaTexto = "5.0", Estado = "Rechazada" },
    };

    private static readonly BandejaCaptacionDto[] BandejaData =
    {
        new() { CodigoCaptacion = "CAP-0231", DireccionLocal = "Av. Petit Thouars 1875", DistritoLocal = "Jesús María", AreaM2 = 95, Rubro = "Servicios", PropietarioNombre = "Grupo Bermúdez", NombreAgenteResponsable = "Carolina Vega", FechaEnvioTexto = "22 May 14:08", AntiguedadTexto = "hace 2d", ComisionPactadaTexto = "5.0", Estado = "Pendiente" },
        new() { CodigoCaptacion = "CAP-0233", DireccionLocal = "Av. Caminos del Inca 1820", DistritoLocal = "Surco", AreaM2 = 140, Rubro = "Restaurante", PropietarioNombre = "A. Pereyra", NombreAgenteResponsable = "Valentina Mora", FechaEnvioTexto = "23 May 11:30", AntiguedadTexto = "hace 1d", ComisionPactadaTexto = "5.0", Estado = "Pendiente" },
        new() { CodigoCaptacion = "CAP-0236", DireccionLocal = "Av. Pardo 2120", DistritoLocal = "Miraflores", AreaM2 = 78, Rubro = "Moda", PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Andrea Torres", FechaEnvioTexto = "23 May 16:42", AntiguedadTexto = "hace 21h", ComisionPactadaTexto = "4.5", Estado = "Pendiente" },
        new() { CodigoCaptacion = "CAP-0238", DireccionLocal = "Av. Aviación 4012", DistritoLocal = "San Borja", AreaM2 = 180, Rubro = "Retail", PropietarioNombre = "Comercial Andina", NombreAgenteResponsable = "Jorge Marín", FechaEnvioTexto = "21 May 10:00", AntiguedadTexto = "hace 3d", ComisionPactadaTexto = "5.0", Estado = "Observada" },
        new() { CodigoCaptacion = "CAP-0240", DireccionLocal = "Av. Tomás Marsano 3400", DistritoLocal = "Surco", AreaM2 = 75, Rubro = "Servicios", PropietarioNombre = "R. Linares", NombreAgenteResponsable = "Carolina Vega", FechaEnvioTexto = "22 May 09:15", AntiguedadTexto = "hace 2d", ComisionPactadaTexto = "4.5", Estado = "Pendiente" },
        new() { CodigoCaptacion = "CAP-0243", DireccionLocal = "Av. Brasil 2890", DistritoLocal = "Magdalena", AreaM2 = 60, Rubro = "Café", PropietarioNombre = "Inmobiliaria Pacífico", NombreAgenteResponsable = "Paola Reyes", FechaEnvioTexto = "23 May 17:50", AntiguedadTexto = "hace 20h", ComisionPactadaTexto = "5.0", Estado = "Observada" },
    };

    public IReadOnlyList<CaptacionDto> All() => Data;
    public IReadOnlyList<BandejaCaptacionDto> Bandeja() => BandejaData;
    public CaptacionDto? ByCodigo(string codigo) => Data.FirstOrDefault(c => c.CodigoCaptacion == codigo);
}

public class MockSolicitudService : ISolicitudService
{
    private static readonly SolicitudAlquilerDto[] Data =
    {
        new() { CodigoSolicitud = "SOL-0425", CodigoOperacion = "OP-1094", ClienteNombre = "Boutique Lila", DireccionLocal = "Calle Schell 412", DistritoLocal = "Miraflores", MontoMensualTexto = "1 850", PlazoMeses = 24, DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "23 May", Estado = "En revisión" },
        new() { CodigoSolicitud = "SOL-0428", CodigoOperacion = "OP-1083", ClienteNombre = "Inversiones Trébol", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", MontoMensualTexto = "2 750", PlazoMeses = 36, DocumentosTexto = "5/6", PorcentajeDocumentos = 83, FechaRegistroTexto = "22 May", Estado = "Observada" },
        new() { CodigoSolicitud = "SOL-0430", CodigoOperacion = "OP-1085", ClienteNombre = "Café Lima", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", MontoMensualTexto = "1 400", PlazoMeses = 24, DocumentosTexto = "4/6", PorcentajeDocumentos = 66, FechaRegistroTexto = "22 May", Estado = "Registrada" },
        new() { CodigoSolicitud = "SOL-0421", CodigoOperacion = "OP-1077", ClienteNombre = "Carla Espinoza", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", MontoMensualTexto = "2 100", PlazoMeses = 24, DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "18 May", Estado = "Aprobada" },
        new() { CodigoSolicitud = "SOL-0415", CodigoOperacion = "OP-1071", ClienteNombre = "Plásticos del Sur", DireccionLocal = "Av. Aviación 4012", DistritoLocal = "San Borja", MontoMensualTexto = "3 500", PlazoMeses = 36, DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "15 May", Estado = "Rechazada" },
        new() { CodigoSolicitud = "SOL-0418", CodigoOperacion = "OP-1068", ClienteNombre = "Restaurantes Bocca", DireccionLocal = "Av. Brasil 2890", DistritoLocal = "Magdalena", MontoMensualTexto = "1 950", PlazoMeses = 24, DocumentosTexto = "6/6", PorcentajeDocumentos = 100, FechaRegistroTexto = "14 May", Estado = "Aprobada" },
    };

    public IReadOnlyList<SolicitudAlquilerDto> All() => Data;
    public SolicitudAlquilerDto? ByCodigo(string codigo) => Data.FirstOrDefault(s => s.CodigoSolicitud == codigo);
}

public class MockInteraccionService : IInteraccionService
{
    private static readonly InteraccionComercialDto[] Data = new[]
    {
        new InteraccionComercialDto { Id = 1, OportunidadId = 1, FechaHoraTexto = "29 May 2026 · 14:30", CanalContacto = "L", ClienteNombre = "Jorge Martinez", CaptacionCodigo = "CAP-0218", Resultado = "I", Observaciones = "Cliente muy interesado en la zona de San Miguel. Seguimiento próxima semana.", NombreAgenteResponsable = "Valentina Mora" },
        new InteraccionComercialDto { Id = 2, OportunidadId = 2, FechaHoraTexto = "28 May 2026 · 11:15", CanalContacto = "W", ClienteNombre = "María Rodríguez", CaptacionCodigo = "CAP-0226", Resultado = "S", Observaciones = "Interesada en locales en Miraflores. Requiere metraje entre 50 y 100 m².", NombreAgenteResponsable = "Valentina Mora" },
        new InteraccionComercialDto { Id = 3, OportunidadId = 3, FechaHoraTexto = "27 May 2026 · 09:45", CanalContacto = "E", ClienteNombre = "Carlos González", CaptacionCodigo = "CAP-0234", Resultado = "N", Observaciones = "No sigue adelante por cambio de planes.", NombreAgenteResponsable = "Carolina Vega" },
        new InteraccionComercialDto { Id = 4, OportunidadId = 4, FechaHoraTexto = "26 May 2026 · 16:20", CanalContacto = "P", ClienteNombre = "Ana López", CaptacionCodigo = "CAP-0237", Resultado = "I", Observaciones = "Visitó el local. Muy conforme con las instalaciones.", NombreAgenteResponsable = "Andrea Torres" },
        new InteraccionComercialDto { Id = 5, OportunidadId = 5, FechaHoraTexto = "25 May 2026 · 13:00", CanalContacto = "L", ClienteNombre = "Pedro Sánchez", CaptacionCodigo = "CAP-0241", Resultado = "P", Observaciones = "Pendiente de respuesta del cliente.", NombreAgenteResponsable = "Paola Reyes" },
    };

    public IReadOnlyList<InteraccionComercialDto> All() => Data;
    public InteraccionComercialDto? ById(long id) => Data.FirstOrDefault(i => i.Id == id);
}

public class MockOportunidadService : IOportunidadService
{
    private static readonly OportunidadComercialDto[] Data =
    {
        // CAP-0218 tiene VARIOS clientes interesados → demuestra el puente N:M.
        new() { Id = 1, CodigoOportunidad = "OP-1083", CaptacionId = 1, CaptacionCodigo = "CAP-0218", DireccionLocal = "Av. La Marina 245", ClienteNombre = "Inversiones Trébol S.A.C.", NombreAgenteResponsable = "V. Mora", Estado = "En seguimiento", FechaRegistroTexto = "20 May 2026" },
        new() { Id = 2, CodigoOportunidad = "OP-1101", CaptacionId = 1, CaptacionCodigo = "CAP-0218", DireccionLocal = "Av. La Marina 245", ClienteNombre = "Café del Puerto S.A.C.", NombreAgenteResponsable = "V. Mora", Estado = "Solicitud creada", FechaRegistroTexto = "22 May 2026" },
        new() { Id = 3, CodigoOportunidad = "OP-1108", CaptacionId = 1, CaptacionCodigo = "CAP-0218", DireccionLocal = "Av. La Marina 245", ClienteNombre = "Boutique Andina E.I.R.L.", NombreAgenteResponsable = "V. Mora", Estado = "Abierta", FechaRegistroTexto = "27 May 2026" },
        new() { Id = 4, CodigoOportunidad = "OP-1094", CaptacionId = 2, CaptacionCodigo = "CAP-0226", DireccionLocal = "Calle Schell 412", ClienteNombre = "Boutique Lila", NombreAgenteResponsable = "V. Mora", Estado = "Solicitud creada", FechaRegistroTexto = "18 May 2026" },
        new() { Id = 5, CodigoOportunidad = "OP-1085", CaptacionId = 4, CaptacionCodigo = "CAP-0234", DireccionLocal = "Jr. Berlín 230", ClienteNombre = "Café Lima", NombreAgenteResponsable = "C. Vega", Estado = "Solicitud creada", FechaRegistroTexto = "16 May 2026" },
        new() { Id = 6, CodigoOportunidad = "OP-1090", CaptacionId = 3, CaptacionCodigo = "CAP-0231", DireccionLocal = "Av. Petit Thouars 1875", ClienteNombre = "Bodegas del Norte", NombreAgenteResponsable = "C. Vega", Estado = "Abierta", FechaRegistroTexto = "16 May 2026" },
    };

    public IReadOnlyList<OportunidadComercialDto> All() => Data;
    public OportunidadComercialDto? ById(long id) => Data.FirstOrDefault(o => o.Id == id);
    public IReadOnlyList<OportunidadComercialDto> ByCaptacion(string codigoCaptacion) =>
        Data.Where(o => o.CaptacionCodigo == codigoCaptacion).ToList();
}

public class MockVisitaService : IVisitaService
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

    public VisitaDto? ById(long id) => _data.FirstOrDefault(v => v.Id == id);

    public VisitaDto Programar(VisitaFormRequest request)
    {
        // Reglas espejo del backend: captación, cliente, fecha y hora obligatorias.
        if (string.IsNullOrWhiteSpace(request.CaptacionCodigo))
            throw new InvalidOperationException("Debes seleccionar una captación.");
        if (string.IsNullOrWhiteSpace(request.ClienteNombre))
            throw new InvalidOperationException("Debes seleccionar un cliente interesado.");
        if (string.IsNullOrWhiteSpace(request.FechaTexto) || string.IsNullOrWhiteSpace(request.HoraTexto))
            throw new InvalidOperationException("La fecha y la hora de la visita son obligatorias.");

        var visita = new VisitaDto
        {
            Id = ++_nextId,
            CodigoCaptacion = request.CaptacionCodigo,
            ClienteNombre = request.ClienteNombre,
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

    public VisitaDto RegistrarResultado(long id, string resultado, string? observaciones, string? razonNoContinuidad)
    {
        var visita = Requerir(id);
        AsegurarModificable(visita, "registrar el resultado de");
        if (string.IsNullOrWhiteSpace(resultado))
            throw new InvalidOperationException("El resultado de la visita es obligatorio.");

        visita.Estado = "R";
        visita.Resultado = resultado;
        if (!string.IsNullOrWhiteSpace(observaciones))
            visita.Observaciones = observaciones;

        // Desenlace de no continuidad: el motivo es obligatorio. Espeja el backend:
        // registra el motivo (ligado a la visita) y cierra la oportunidad.
        if (resultado is "N" or "D")
        {
            if (string.IsNullOrWhiteSpace(razonNoContinuidad))
                throw new InvalidOperationException("Debes indicar el motivo de no continuidad.");
            var motivoLabel = EnumCatalog.LabelFor(EnumCatalog.MotivosNoContinuidad, razonNoContinuidad);
            visita.Observaciones = string.IsNullOrWhiteSpace(observaciones)
                ? $"No continúa · {motivoLabel}"
                : $"{observaciones} · No continúa: {motivoLabel}";
        }
        return visita;
    }

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

    private static readonly BrokerAgenteDto[] HistorialData =
    {
        new() { Id = 1, AgenteNombre = "Daniel Romero", BrokerAnteriorNombre = "M. Quintero", BrokerNuevoNombre = "R. Salas", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "22 May 2026", Motivo = "Cese del broker anterior", Estado = "Activa" },
        new() { Id = 2, AgenteNombre = "Matías León", BrokerAnteriorNombre = "S. Ríos", BrokerNuevoNombre = "F. Andrade", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "18 May 2026", Motivo = "Redistribución de zona", Estado = "Activa" },
        new() { Id = 3, AgenteNombre = "Carla Núñez", BrokerAnteriorNombre = "R. Salas", BrokerNuevoNombre = "S. Ríos", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "09 May 2026", Motivo = "Solicitud escalada del equipo", Estado = "Cerrada" },
        new() { Id = 4, AgenteNombre = "Pedro Vidal", BrokerAnteriorNombre = "F. Andrade", BrokerNuevoNombre = "R. Salas", BrokerAdministradorNombre = "Administración", FechaAsignacionTexto = "02 May 2026", Motivo = "Afinidad de zona", Estado = "Cerrada" },
    };

    public IReadOnlyList<AssignAgentDto> Agents() => AgentData;
    public IReadOnlyList<AssignBrokerDto> Brokers() => BrokerData;
    public IReadOnlyList<BrokerAgenteDto> Historial() => HistorialData;
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
        new() { Id = 2, CodigoCaptacion = "CAP-0226", DireccionLocal = "Calle Schell 412", DistritoLocal = "Miraflores", AgenteResponsableId = 1, NombreAgenteResponsable = "Valentina Mora", VigenciaTexto = "15 May – 15 Nov 2026", DiasRestantesTexto = "venc. en 175 días", Estado = "Activa" },
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
        new() { Id = 4, Iniciales = "PR", Color = "#C98A1E", Nombre = "Paola Reyes", NumeroDocumento = "45 002 884", Zona = "Lima Centro", CaptacionesActivas = 7, EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible" },
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
