using ControlLocal.Web.Models.Agentes;
using ControlLocal.Web.Models.Asignaciones;
using ControlLocal.Web.Models.Brokers;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Locales;
using ControlLocal.Web.Models.Propietarios;
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
}

public interface IPropietarioService
{
    IReadOnlyList<PropietarioDto> All();
}

public interface IClienteService
{
    IReadOnlyList<ClienteInteresadoDto> All();
}

public interface ILocalService
{
    IReadOnlyList<LocalComercialDto> All();
}

public interface ICaptacionService
{
    IReadOnlyList<CaptacionDto> All();
    IReadOnlyList<BandejaCaptacionDto> Bandeja();
}

public interface ISolicitudService
{
    IReadOnlyList<SolicitudAlquilerDto> All();
}

public interface IVisitaService
{
    IReadOnlyList<VisitaDto> Today();
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
        new() { Iniciales = "VM", Color = "#3A6BB5", Nombre = "Valentina Mora", Email = "vmora@controllocal.pe", NumeroDocumento = "45 893 211", Zona = "Lima Centro", FechaIngresoTexto = "14 Feb 2024", CaptacionesActivas = 14, OportunidadesActivas = 8, EstadoAdministrativo = "Activo" },
        new() { Iniciales = "CV", Color = "#2F7D52", Nombre = "Carolina Vega", Email = "cvega@controllocal.pe", NumeroDocumento = "46 220 411", Zona = "Lima Sur", FechaIngresoTexto = "22 Mar 2024", CaptacionesActivas = 9, OportunidadesActivas = 5, EstadoAdministrativo = "Activo" },
        new() { Iniciales = "AT", Color = "#C0473C", Nombre = "Andrea Torres", Email = "atorres@controllocal.pe", NumeroDocumento = "47 412 008", Zona = "Callao", FechaIngresoTexto = "06 May 2024", CaptacionesActivas = 6, OportunidadesActivas = 3, EstadoAdministrativo = "Activo" },
        new() { Iniciales = "PR", Color = "#C98A1E", Nombre = "Paola Reyes", Email = "preyes@controllocal.pe", NumeroDocumento = "45 002 884", Zona = "Lima Centro", FechaIngresoTexto = "18 Jul 2024", CaptacionesActivas = 7, OportunidadesActivas = 4, EstadoAdministrativo = "Activo" },
        new() { Iniciales = "JM", Color = "#2C77A0", Nombre = "Jorge Marín", Email = "jmarin@controllocal.pe", NumeroDocumento = "44 728 101", Zona = "Lima Moderna", FechaIngresoTexto = "03 Sep 2024", CaptacionesActivas = 5, OportunidadesActivas = 2, EstadoAdministrativo = "Activo" },
        new() { Iniciales = "NS", Color = "#948D81", Nombre = "Natalia Solano", Email = "nsolano@controllocal.pe", NumeroDocumento = "46 998 220", Zona = "Lima Sur", FechaIngresoTexto = "19 Oct 2024", CaptacionesActivas = 0, OportunidadesActivas = 0, EstadoAdministrativo = "Inactivo" },
    };

    public IReadOnlyList<AgenteDto> All() => Data;
}

public class MockPropietarioService : IPropietarioService
{
    private static readonly PropietarioDto[] Data =
    {
        new() { Nombre = "Inmobiliaria Pacífico S.A.C.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 553 102 884", Telefono = "+51 1 432 8800", Correo = "contacto@pacifico.com.pe", CantidadLocales = 6, Estado = "Activo" },
        new() { Nombre = "Carlos Mendoza Rivera", TipoPersona = "Persona natural · DNI", NumeroDocumento = "08 412 991", Telefono = "+51 998 220 411", Correo = "cmendoza@gmail.com", CantidadLocales = 2, Estado = "Activo" },
        new() { Nombre = "Grupo Bermúdez E.I.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 502 998 110", Telefono = "+51 1 222 1108", Correo = "admin@bermudez.pe", CantidadLocales = 4, Estado = "Activo" },
        new() { Nombre = "Ana Lucía Pereyra", TipoPersona = "Persona natural · DNI", NumeroDocumento = "09 778 002", Telefono = "+51 987 412 008", Correo = "alpereyra@hotmail.com", CantidadLocales = 1, Estado = "Activo" },
        new() { Nombre = "Comercial Andina S.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 471 220 008", Telefono = "+51 1 718 4400", Correo = "ventas@andina.com.pe", CantidadLocales = 3, Estado = "Inactivo" },
        new() { Nombre = "Roberto Linares Cruz", TipoPersona = "Persona natural · DNI", NumeroDocumento = "07 823 145", Telefono = "+51 991 552 008", Correo = "rlinares@yahoo.com", CantidadLocales = 1, Estado = "Activo" },
    };

    public IReadOnlyList<PropietarioDto> All() => Data;
}

public class MockClienteService : IClienteService
{
    private static readonly ClienteInteresadoDto[] Data =
    {
        new() { Nombre = "Inversiones Trébol S.A.C.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 558 110 442", Telefono = "+51 1 445 7800", RubroInteres = "Restaurante / Café", InteresComercial = "Locales 80–150 m² en Lima moderna", CaptacionesVinculadas = 2, Estado = "Activo" },
        new() { Nombre = "Boutique Lila", TipoPersona = "Persona natural · DNI", NumeroDocumento = "45 220 118", Telefono = "+51 998 552 110", RubroInteres = "Moda / Boutique", InteresComercial = "Local en Miraflores < 80 m²", CaptacionesVinculadas = 1, Estado = "Activo" },
        new() { Nombre = "Bodegas del Norte E.I.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 471 882 004", Telefono = "+51 1 552 1180", RubroInteres = "Retail / Almacén", InteresComercial = "Locales > 150 m² con depósito", CaptacionesVinculadas = 3, Estado = "Activo" },
        new() { Nombre = "Café Lima", TipoPersona = "Persona natural · DNI", NumeroDocumento = "44 118 902", Telefono = "+51 987 220 004", RubroInteres = "Café / Postres", InteresComercial = "Esquinas comerciales en Miraflores", CaptacionesVinculadas = 1, Estado = "Activo" },
        new() { Nombre = "Carla Espinoza Núñez", TipoPersona = "Persona natural · DNI", NumeroDocumento = "46 552 008", Telefono = "+51 991 002 552", RubroInteres = "Servicios / Consultorio", InteresComercial = "Oficina-local en San Isidro", CaptacionesVinculadas = 1, Estado = "Activo" },
        new() { Nombre = "Distribuidora El Sol S.R.L.", TipoPersona = "Persona jurídica · RUC", NumeroDocumento = "20 502 110 778", Telefono = "+51 1 718 2200", RubroInteres = "Retail", InteresComercial = "Locales en avenidas principales", CaptacionesVinculadas = 0, Estado = "Inactivo" },
    };

    public IReadOnlyList<ClienteInteresadoDto> All() => Data;
}

public class MockLocalService : ILocalService
{
    private static readonly LocalComercialDto[] Data =
    {
        new() { CodigoLocal = "LC-0218", Estado = "Activa", Direccion = "Av. La Marina 245", Distrito = "San Miguel, Lima", AreaM2 = 120, Rubro = "Restaurante / Café", PrecioReferencialTexto = "2 800", PropietarioNombre = "Inmobiliaria Pacífico" },
        new() { CodigoLocal = "LC-0226", Estado = "Activa", Direccion = "Calle Schell 412, of. 1", Distrito = "Miraflores, Lima", AreaM2 = 68, Rubro = "Moda / Boutique", PrecioReferencialTexto = "1 950", PropietarioNombre = "C. Mendoza" },
        new() { CodigoLocal = "LC-0231", Estado = "Pendiente", Direccion = "Av. Petit Thouars 1875", Distrito = "Jesús María, Lima", AreaM2 = 95, Rubro = "Servicios", PrecioReferencialTexto = "1 600", PropietarioNombre = "Grupo Bermúdez" },
        new() { CodigoLocal = "LC-0234", Estado = "Activa", Direccion = "Jr. Berlín 230", Distrito = "Miraflores, Lima", AreaM2 = 52, Rubro = "Café / Postres", PrecioReferencialTexto = "1 450", PropietarioNombre = "A. Pereyra" },
        new() { CodigoLocal = "LC-0238", Estado = "Observada", Direccion = "Av. Aviación 4012", Distrito = "San Borja, Lima", AreaM2 = 180, Rubro = "Retail", PrecioReferencialTexto = "3 600", PropietarioNombre = "Comercial Andina" },
        new() { CodigoLocal = "LC-0242", Estado = "Activa", Direccion = "Av. Salaverry 2120", Distrito = "Jesús María, Lima", AreaM2 = 88, Rubro = "Servicios médicos", PrecioReferencialTexto = "2 200", PropietarioNombre = "R. Linares" },
    };

    public IReadOnlyList<LocalComercialDto> All() => Data;
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
}

public class MockVisitaService : IVisitaService
{
    private static readonly VisitaDto[] Data =
    {
        new() { FechaTexto = "24 May 2026", HoraTexto = "16:00", CodigoCaptacion = "CAP-0218", ClienteNombre = "Inversiones Trébol", DireccionLocal = "Av. La Marina 245", DistritoLocal = "San Miguel", NombreAgente = "V. Mora", Estado = "Programada" },
        new() { FechaTexto = "25 May 2026", HoraTexto = "09:30", CodigoCaptacion = "CAP-0234", ClienteNombre = "Café Lima", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", NombreAgente = "V. Mora", Estado = "Programada" },
        new() { FechaTexto = "26 May 2026", HoraTexto = "10:00", CodigoCaptacion = "CAP-0231", ClienteNombre = "Bodegas del Norte", DireccionLocal = "Av. Petit Thouars 1875", DistritoLocal = "Jesús María", NombreAgente = "D. Romero", Estado = "Programada" },
        new() { FechaTexto = "22 May 2026", HoraTexto = "11:00", CodigoCaptacion = "CAP-0234", ClienteNombre = "Café Lima", DireccionLocal = "Jr. Berlín 230", DistritoLocal = "Miraflores", NombreAgente = "V. Mora", Estado = "Realizada", ResultadoTexto = "Cliente conforme · solicita propuesta" },
        new() { FechaTexto = "20 May 2026", HoraTexto = "15:30", CodigoCaptacion = "CAP-0242", ClienteNombre = "Carla Espinoza", DireccionLocal = "Av. Salaverry 2120", DistritoLocal = "Jesús María", NombreAgente = "C. Vega", Estado = "Cancelada", ResultadoTexto = "Cliente desistió" },
        new() { FechaTexto = "18 May 2026", HoraTexto = "10:30", CodigoCaptacion = "CAP-0238", ClienteNombre = "Plásticos del Sur", DireccionLocal = "Av. Aviación 4012", DistritoLocal = "San Borja", NombreAgente = "M. León", Estado = "Reprogramada", ResultadoTexto = "Cliente solicitó reagendar" },
    };

    public IReadOnlyList<VisitaDto> Today() => Data;
}

public class MockAssignmentService : IAssignmentService
{
    private static readonly AssignAgentDto[] AgentData =
    {
        new() { Id = "a1", Iniciales = "DR", Nombre = "Daniel Romero", NumeroDocumento = "DNI 44 102 776", EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible", BrokerActual = "Mariana Quintero", Seleccionable = true },
        new() { Id = "a2", Iniciales = "ML", Nombre = "Matías León", NumeroDocumento = "DNI 45 778 002", EstadoAdministrativo = "Activo", EstadoOperativo = "Disponible", BrokerActual = "Felipe Andrade", Seleccionable = true },
        new() { Id = "a3", Iniciales = "AT", Nombre = "Andrea Torres", NumeroDocumento = "DNI 47 412 008", EstadoAdministrativo = "Activo", EstadoOperativo = "En licencia", BrokerActual = "Sandra Ríos", Seleccionable = false, MotivoNoDisponible = "En licencia — no recibe asignaciones" },
        new() { Id = "a4", Iniciales = "JS", Nombre = "Joaquín Soto", NumeroDocumento = "DNI 44 991 332", EstadoAdministrativo = "Inactivo", EstadoOperativo = "No disponible", BrokerActual = "—", Seleccionable = false, MotivoNoDisponible = "Agente inactivo" },
    };

    private static readonly AssignBrokerDto[] BrokerData =
    {
        new() { Id = "b1", Iniciales = "RS", Nombre = "Ricardo Salas", Zona = "Lima Centro / Sur", EstadoAdministrativo = "Activo", TipoBroker = "supervisor", AgentesACargo = 7, Seleccionable = true },
        new() { Id = "b2", Iniciales = "FA", Nombre = "Felipe Andrade", Zona = "Lima Este", EstadoAdministrativo = "Activo", TipoBroker = "supervisor", AgentesACargo = 5, Seleccionable = true },
        new() { Id = "b3", Iniciales = "ET", Nombre = "Elena Tafur", Zona = "Lima Moderna", EstadoAdministrativo = "Inactivo", TipoBroker = "supervisor", AgentesACargo = 0, Seleccionable = false, MotivoNoDisponible = "Broker inactivo" },
        new() { Id = "b4", Iniciales = "AT", Nombre = "Alejandro Téllez", Zona = "Global", EstadoAdministrativo = "Activo", TipoBroker = "admin", AgentesACargo = 0, Seleccionable = false, MotivoNoDisponible = "No se puede asignar a un broker administrador" },
    };

    private static readonly BrokerAgenteDto[] HistorialData =
    {
        new() { Id = 1, AgenteNombre = "Daniel Romero", BrokerAnteriorNombre = "M. Quintero", BrokerNuevoNombre = "R. Salas", BrokerAdministradorNombre = "A. Téllez", FechaAsignacionTexto = "22 May 2026", Motivo = "Cese del broker anterior", Estado = "Activa" },
        new() { Id = 2, AgenteNombre = "Matías León", BrokerAnteriorNombre = "S. Ríos", BrokerNuevoNombre = "F. Andrade", BrokerAdministradorNombre = "A. Téllez", FechaAsignacionTexto = "18 May 2026", Motivo = "Redistribución de zona", Estado = "Activa" },
        new() { Id = 3, AgenteNombre = "Carla Núñez", BrokerAnteriorNombre = "R. Salas", BrokerNuevoNombre = "S. Ríos", BrokerAdministradorNombre = "A. Téllez", FechaAsignacionTexto = "09 May 2026", Motivo = "Solicitud escalada del equipo", Estado = "Cerrada" },
        new() { Id = 4, AgenteNombre = "Pedro Vidal", BrokerAnteriorNombre = "F. Andrade", BrokerNuevoNombre = "R. Salas", BrokerAdministradorNombre = "A. Téllez", FechaAsignacionTexto = "02 May 2026", Motivo = "Afinidad de zona", Estado = "Cerrada" },
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
