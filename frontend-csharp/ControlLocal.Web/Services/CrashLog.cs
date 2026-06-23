namespace ControlLocal.Web.Services;

// [DIAGNOSTICO TEMPORAL] Escribe migas/crashes de forma sincrona a un archivo en %TEMP%,
// para diagnosticar caidas duras del proceso (p. ej. StackOverflow) que no dejan rastro en
// la consola. Cada escritura hace flush, asi sobrevive al crash. Quitar cuando se resuelva.
public static class CrashLog
{
    private static readonly string Ruta =
        Path.Combine(Path.GetTempPath(), "ControlLocal-crash.log");

    public static void Breadcrumb(string mensaje)
    {
        try { File.AppendAllText(Ruta, $"[{DateTime.Now:O}] {mensaje}{Environment.NewLine}"); }
        catch { /* el propio logger nunca debe fallar */ }
    }
}
