namespace ControlLocal.Web.Models.Shared;

// Elemento de la barra de migas de pan. Modelo de apoyo de interfaz reutilizable.
public record BreadcrumbItem(string Label, string? Route = null);
