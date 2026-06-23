using ControlLocal.Web.Services;

namespace ControlLocal.Web.Data;

// Notificacion in-app dirigida a un rol (Agente / Broker / Admin). Espeja, del
// lado del frontend, la entidad Alerta del backend Java (tipo, severidad, mensaje,
// entidad asociada y estado leida/no leida). El backend real la entregara via
// AlertasRest; este servicio mantiene la version demostrable en sesion.
public class NotificacionDto
{
    public long Id { get; set; }
    public string Tipo { get; set; } = "";
    public string Mensaje { get; set; } = "";
    public string Severidad { get; set; } = "INFO";  // INFO | MEDIA | ALTA
    public string Icono { get; set; } = "bell";
    public string EntidadTipo { get; set; } = "";
    public string? EntidadRef { get; set; }
    public string? Ruta { get; set; }                 // destino al abrir la notificacion
    public string DestinatarioRol { get; set; } = Roles.Broker;
    public DateTime Fecha { get; set; }
    public bool Leida { get; set; }

    public string FechaTexto => Humanizar(Fecha);

    private static string Humanizar(DateTime fecha)
    {
        var delta = DateTime.Now - fecha;
        if (delta < TimeSpan.FromMinutes(1)) return "hace un momento";
        if (delta < TimeSpan.FromHours(1)) return $"hace {(int)delta.TotalMinutes} min";
        if (delta < TimeSpan.FromHours(24) && fecha.Date == DateTime.Today)
            return $"hoy {fecha:HH:mm}";
        if (fecha.Date == DateTime.Today.AddDays(-1)) return $"ayer {fecha:HH:mm}";
        return fecha.ToString("dd MMM HH:mm");
    }
}

// Almacen compartido (Singleton) de notificaciones: sobrevive a la navegacion y a
// los reinicios de circuito de Blazor Server, de modo que el flujo "reenviar a
// evaluacion -> aviso al broker -> evaluacion -> aviso al agente" es observable.
public class NotificacionStore
{
    public const long IdLocalMinimo = 1_000_001;

    private readonly object _lock = new();
    private readonly List<NotificacionDto> _items = new();
    // Offset alto: los ids in-app no deben colisionar con los ids de Alerta del backend.
    private long _seq = IdLocalMinimo - 1;

    // Se dispara en cada cambio (alta / marcar leida). El Topbar se suscribe para
    // refrescar la campana EN VIVO, incluso cuando el cambio viene de OTRO circuito
    // (otro rol), porque el store es Singleton compartido.
    public event Action? Cambiado;

    // Sin semillas de demo: la campana arranca vacia y solo refleja notificaciones reales
    // generadas por acciones del flujo (reenviar a evaluacion, evaluar, observar documento).

    public NotificacionDto Agregar(NotificacionDto notificacion)
    {
        lock (_lock)
        {
            notificacion.Id = ++_seq;
            if (notificacion.Fecha == default) notificacion.Fecha = DateTime.Now;
            _items.Insert(0, notificacion);
        }
        Cambiado?.Invoke();   // fuera del lock: evita reentrancia con suscriptores
        return notificacion;
    }

    // El admin general supervisa todo, por eso ve tambien las del broker.
    public IReadOnlyList<NotificacionDto> ParaRol(string rol)
    {
        lock (_lock)
            return _items
                .Where(n => n.DestinatarioRol == rol
                    || (rol == Roles.Admin && n.DestinatarioRol == Roles.Broker))
                .OrderByDescending(n => n.Fecha)
                .ToList();
    }

    public bool MarcarLeida(long id)
    {
        bool cambio;
        lock (_lock)
        {
            var notificacion = _items.FirstOrDefault(n => n.Id == id);
            cambio = notificacion is { Leida: false };
            if (cambio) notificacion!.Leida = true;
        }
        if (cambio) Cambiado?.Invoke();
        return cambio;
    }

    public void MarcarTodasLeidas(string rol)
    {
        lock (_lock)
            foreach (var notificacion in _items.Where(n => n.DestinatarioRol == rol
                || (rol == Roles.Admin && n.DestinatarioRol == Roles.Broker)))
                notificacion.Leida = true;
        Cambiado?.Invoke();
    }
}

public interface INotificacionService
{
    IReadOnlyList<NotificacionDto> MisNotificaciones();
    int NoLeidas();
    void MarcarLeida(long id);
    void MarcarTodasLeidas();
    NotificacionDto Crear(NotificacionDto notificacion);
}

// Vista por usuario del almacen compartido: resuelve el rol activo desde AppState.
public class NotificacionService(NotificacionStore store, AppState app) : INotificacionService
{
    private string RolActivo => app.CurrentUser?.Role ?? app.Role;

    public IReadOnlyList<NotificacionDto> MisNotificaciones() => store.ParaRol(RolActivo);
    public int NoLeidas() => store.ParaRol(RolActivo).Count(n => !n.Leida);
    public void MarcarLeida(long id) => store.MarcarLeida(id);
    public void MarcarTodasLeidas() => store.MarcarTodasLeidas(RolActivo);
    public NotificacionDto Crear(NotificacionDto notificacion) => store.Agregar(notificacion);
}
