namespace ControlLocal.Web.Services;

public class RoleState
{
    public string CurrentRole { get; private set; } = Roles.Agente;

    public event Action? OnChange;

    public void SetRole(string role)
    {
        CurrentRole = role;
        OnChange?.Invoke();
    }
}

public static class Roles
{
    public const string BrokerAdministrador = "Broker administrador";
    public const string Broker = "Broker";
    public const string Agente = "Agente inmobiliario";
}
