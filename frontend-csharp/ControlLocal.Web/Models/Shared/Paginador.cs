namespace ControlLocal.Web.Models.Shared;

public static class Paginador
{
    public const int TamanoPagina = 8;

    // Devuelve la página solicitada; si la página quedó fuera de rango tras un
    // cambio de filtros, se ajusta a la última disponible.
    public static IReadOnlyList<T> Pagina<T>(IReadOnlyList<T> items, ref int pagina, int tamano = TamanoPagina)
    {
        var ultima = Math.Max(1, (int)Math.Ceiling(items.Count / (double)tamano));
        if (pagina < 1) pagina = 1;
        if (pagina > ultima) pagina = ultima;
        return items.Skip((pagina - 1) * tamano).Take(tamano).ToList();
    }
}
