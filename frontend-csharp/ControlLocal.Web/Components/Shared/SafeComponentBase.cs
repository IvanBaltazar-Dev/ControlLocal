using Microsoft.AspNetCore.Components;

namespace ControlLocal.Web.Components.Shared;

public abstract class SafeComponentBase : ComponentBase, IDisposable
{
    private readonly CancellationTokenSource _disposed = new();

    protected CancellationToken DisposedToken => _disposed.Token;

    protected async Task RenderIfAliveAsync()
    {
        if (_disposed.IsCancellationRequested) return;

        try
        {
            await InvokeAsync(StateHasChanged);
        }
        catch (ObjectDisposedException)
        {
        }
        catch (InvalidOperationException) when (_disposed.IsCancellationRequested)
        {
        }
    }

    protected async Task DelayThenRenderAsync(int delayMilliseconds, Action update)
    {
        try
        {
            await Task.Delay(delayMilliseconds, _disposed.Token);
            if (_disposed.IsCancellationRequested) return;

            update();
            await RenderIfAliveAsync();
        }
        catch (OperationCanceledException)
        {
        }
        catch (ObjectDisposedException)
        {
        }
    }

    public virtual void Dispose()
    {
        if (_disposed.IsCancellationRequested) return;

        _disposed.Cancel();
        _disposed.Dispose();
        GC.SuppressFinalize(this);
    }
}
