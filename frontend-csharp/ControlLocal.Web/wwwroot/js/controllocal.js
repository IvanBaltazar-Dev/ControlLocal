window.controlLocal = {
    // Descarga en el navegador un archivo generado en el servidor (contenido en base64).
    descargar: function (nombre, tipoContenido, base64) {
        const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0));
        const blob = new Blob([bytes], { type: tipoContenido });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = nombre;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    },

    irA: function (id) {
        const el = document.getElementById(id);
        if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    },

    // Dispara el selector de archivos de un <input type="file"> por id, para que
    // un boton/icono ajeno pueda abrir el explorador nativo.
    clickElemento: function (id) {
        const el = document.getElementById(id);
        if (el) el.click();
    },

    session: {
        _subscriptions: new Map(),
        get: function () {
            return localStorage.getItem("controlLocal.session");
        },
        set: function (value) {
            localStorage.setItem("controlLocal.session", value);
            this.notify();
        },
        clear: function () {
            localStorage.removeItem("controlLocal.session");
            this.notify();
        },
        notify: function () {
            window.dispatchEvent(new Event("controlLocalSessionChanged"));
        },
        subscribe: function (dotNetRef) {
            const id = (window.crypto && window.crypto.randomUUID)
                ? window.crypto.randomUUID()
                : `${Date.now()}-${Math.random()}`;
            const notifyDotNet = () => dotNetRef.invokeMethodAsync("OnBrowserSessionChanged");
            const storageHandler = (event) => {
                if (event.key === "controlLocal.session") notifyDotNet();
            };
            const localHandler = () => notifyDotNet();

            window.addEventListener("storage", storageHandler);
            window.addEventListener("controlLocalSessionChanged", localHandler);
            this._subscriptions.set(id, { storageHandler, localHandler });
            return id;
        },
        unsubscribe: function (id) {
            const subscription = this._subscriptions.get(id);
            if (!subscription) return;

            window.removeEventListener("storage", subscription.storageHandler);
            window.removeEventListener("controlLocalSessionChanged", subscription.localHandler);
            this._subscriptions.delete(id);
        }
    }
};
