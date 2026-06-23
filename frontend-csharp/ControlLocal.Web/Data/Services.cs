using ControlLocal.Web.Models.Agentes;
using ControlLocal.Web.Models.Asignaciones;
using ControlLocal.Web.Models.Brokers;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Contratos;
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
    // Variantes async reales (no bloquean el circuito de Blazor Server).
    Task<BrokerDto> AgregarAsync(BrokerDto broker, CancellationToken ct = default) =>
        Task.FromResult(Agregar(broker));
    Task<BrokerDto> ActualizarAsync(BrokerDto broker, CancellationToken ct = default) =>
        Task.FromResult(Actualizar(broker));
}

public interface IAgenteService
{
    IReadOnlyList<AgenteDto> All();
    Task<IReadOnlyList<AgenteDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult(All());
    AgenteDto? ById(long id);
    AgenteDto Agregar(AgenteDto agente);
    AgenteDto Actualizar(AgenteDto agente);
    // Variantes async reales (no bloquean el circuito de Blazor Server).
    Task<AgenteDto> AgregarAsync(AgenteDto agente, CancellationToken ct = default) =>
        Task.FromResult(Agregar(agente));
    Task<AgenteDto> ActualizarAsync(AgenteDto agente, CancellationToken ct = default) =>
        Task.FromResult(Actualizar(agente));
    AgenteDto Desactivar(long id);
}

public interface IPropietarioService
{
    IReadOnlyList<PropietarioDto> All();
    PropietarioDto? ById(long id);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<PropietarioDto>> RefrescarAsync(CancellationToken ct = default);
    // Alta individual; asigna el id y devuelve el registro.
    PropietarioDto Agregar(PropietarioDto propietario);
    PropietarioDto Actualizar(PropietarioDto propietario);
    // Variantes async reales (no bloquean el circuito de Blazor Server).
    Task<PropietarioDto> AgregarAsync(PropietarioDto propietario, CancellationToken ct = default) =>
        Task.FromResult(Agregar(propietario));
    Task<PropietarioDto> ActualizarAsync(PropietarioDto propietario, CancellationToken ct = default) =>
        Task.FromResult(Actualizar(propietario));
}

public interface IClienteService
{
    IReadOnlyList<ClienteInteresadoDto> All();
    ClienteInteresadoDto? ById(long id);
    Task<IReadOnlyList<ClienteInteresadoDto>> RefrescarAsync(CancellationToken ct = default) =>
        Task.FromResult(All());
    ClienteInteresadoDto Agregar(ClienteInteresadoDto cliente);
    ClienteInteresadoDto Actualizar(ClienteInteresadoDto cliente);
    // Variantes async reales (no bloquean el circuito de Blazor Server).
    Task<ClienteInteresadoDto> AgregarAsync(ClienteInteresadoDto cliente, CancellationToken ct = default) =>
        Task.FromResult(Agregar(cliente));
    Task<ClienteInteresadoDto> ActualizarAsync(ClienteInteresadoDto cliente, CancellationToken ct = default) =>
        Task.FromResult(Actualizar(cliente));
}

public interface ILocalService
{
    IReadOnlyList<LocalComercialDto> All();
    LocalComercialDto? ById(long id);
    LocalComercialDto Agregar(LocalComercialDto local);
    LocalComercialDto Actualizar(LocalComercialDto local);
    // Variantes async reales (no bloquean el circuito de Blazor Server).
    Task<LocalComercialDto> AgregarAsync(LocalComercialDto local, CancellationToken ct = default) =>
        Task.FromResult(Agregar(local));
    Task<LocalComercialDto> ActualizarAsync(LocalComercialDto local, CancellationToken ct = default) =>
        Task.FromResult(Actualizar(local));
}

// Historico de precios de un local (tabla precio_local). Solo lectura + alta de hito.
public interface IPrecioLocalService
{
    Task<IReadOnlyList<PrecioLocalDto>> ListarPorLocalAsync(long idLocal, CancellationToken ct = default);
    Task<PrecioLocalDto> RegistrarAsync(long idLocal, string hito, string moneda, decimal monto,
        DateOnly fecha, CancellationToken ct = default);
}

// Publicaciones de un local (tabla publicacion). Solo lectura.
public interface IPublicacionService
{
    Task<IReadOnlyList<PublicacionDto>> ListarPorLocalAsync(long idLocal, CancellationToken ct = default);
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
    // Variantes async reales (no bloquean el circuito de Blazor Server). Las pantallas
    // deben usar estas; las sincronas quedan por compatibilidad.
    Task<CaptacionDto> AgregarAsync(CaptacionDto captacion, CancellationToken ct = default) =>
        Task.FromResult(Agregar(captacion));
    Task<CaptacionDto> ActualizarAsync(CaptacionDto captacion, CancellationToken ct = default) =>
        Task.FromResult(Actualizar(captacion));
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
    // Reenvio asincrono real (no bloquea el circuito de Blazor Server). Las pantallas
    // deben usar esta variante; la sincrona queda solo por compatibilidad.
    Task<SolicitudAlquilerDto> ReenviarAEvaluacionAsync(
        string codigoSolicitud, CancellationToken ct = default) =>
        Task.FromResult(ReenviarAEvaluacion(codigoSolicitud));
    EvaluacionSolicitudDto Evaluar(string codigoSolicitud, EvaluacionSolicitudDto evaluacion);
    Task<EvaluacionSolicitudDto> EvaluarAsync(
        string codigoSolicitud,
        EvaluacionSolicitudDto evaluacion,
        CancellationToken ct = default) =>
        Task.FromResult(Evaluar(codigoSolicitud, evaluacion));
    // Historial de evaluaciones de una solicitud (trazabilidad), accesible al agente dueño y al broker.
    Task<IReadOnlyList<EvaluacionSolicitudDto>> ListarEvaluacionesAsync(long idSolicitud, CancellationToken ct = default);
}

// Contrato de alquiler: el cierre del trato registrado como un "hilo" dentro de la
// solicitud aprobada (espeja /contratos del backend Java). Listar alimenta la
// seccion de propiedades alquiladas; registrar crea el contrato y cierra la operacion.
public interface IContratoService
{
    IReadOnlyList<ContratoAlquilerDto> All();
    Task<IReadOnlyList<ContratoAlquilerDto>> RefrescarAsync(CancellationToken ct = default);
    // Contrato ya registrado para una oportunidad (null si aun no se ha alquilado).
    ContratoAlquilerDto? ByOportunidad(long oportunidadId);
    // Crea el contrato a partir de la solicitud aprobada y cierra el trato.
    Task<ContratoAlquilerDto> RegistrarAsync(ContratoFormRequest request, CancellationToken ct = default);
}

// Documentos de una solicitud (espeja /solicitudes/{id}/documentos del backend Java):
// listar los entregados, subir el archivo real al almacen (S3 o disco, lo gestiona el
// backend) y consultar el estado de conectividad del almacen.
public interface IDocumentoSolicitudService
{
    Task<IReadOnlyList<DocumentoSolicitudDto>> ListarAsync(long idSolicitud, CancellationToken ct = default);

    // Envía los bytes del archivo al backend, que lo guarda en el almacen y registra
    // sus metadatos. Devuelve el documento creado (con su clave en RutaArchivo).
    Task<DocumentoSolicitudDto> SubirAsync(
        long idSolicitud, string tipoDocumento, string nombreArchivo, byte[] contenido,
        CancellationToken ct = default);

    // Revision del broker sobre un documento concreto: "C" valida, "O" observa (con nota).
    Task<DocumentoSolicitudDto> RevisarAsync(
        long idSolicitud, long idDocumento, string resultado, string? observaciones,
        CancellationToken ct = default);

    Task<EstadoAlmacen> EstadoAlmacenAsync(CancellationToken ct = default);
}

public interface IInteraccionService
{
    IReadOnlyList<InteraccionComercialDto> All();
    InteraccionComercialDto? ById(long id);
    InteraccionComercialDto Agregar(InteraccionFormRequest request);
    // Variante async real (no bloquea el circuito de Blazor Server).
    Task<InteraccionComercialDto> AgregarAsync(InteraccionFormRequest request, CancellationToken ct = default) =>
        Task.FromResult(Agregar(request));
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
    // Variantes async reales (no bloquean el circuito de Blazor Server) — normalizado con la
    // reasignación de captaciones.
    Task<IReadOnlyList<AssignAgentDto>> AgentsAsync(CancellationToken ct = default) =>
        Task.FromResult(Agents());
    Task<IReadOnlyList<AssignBrokerDto>> BrokersAsync(CancellationToken ct = default) =>
        Task.FromResult(Brokers());
    Task<IReadOnlyList<BrokerAgenteDto>> HistorialAsync(CancellationToken ct = default) =>
        Task.FromResult(Historial());
    Task<BrokerAgenteDto> ReasignarAgenteAsync(
        string agenteId, string brokerId, string motivo, CancellationToken ct = default) =>
        Task.FromResult(ReasignarAgente(agenteId, brokerId, motivo));
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
