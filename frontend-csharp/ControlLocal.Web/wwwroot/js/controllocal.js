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
        _tabId: null,
        _heartbeat: null,
        _sessionKey: "controlLocal.session",
        _tabsKey: "controlLocal.tabs",
        _tabKey: "controlLocal.tabId",
        _staleMs: 60000,
        init: function () {
            let hadTab = true;
            try {
                this._tabId = sessionStorage.getItem(this._tabKey);
                if (!this._tabId) {
                    hadTab = false;
                    this._tabId = (window.crypto && window.crypto.randomUUID)
                        ? window.crypto.randomUUID()
                        : `${Date.now()}-${Math.random()}`;
                    sessionStorage.setItem(this._tabKey, this._tabId);
                }

                const tabsAntes = this._readTabs();
                const activeAntes = this._activeTabs(tabsAntes).filter(t => t.id !== this._tabId);
                if (!hadTab && activeAntes.length === 0 && localStorage.getItem(this._sessionKey)) {
                    localStorage.removeItem(this._sessionKey);
                }

                this._touch("active");
                if (!this._heartbeat) {
                    this._heartbeat = window.setInterval(() => this._touch("active"), 4000);
                    window.addEventListener("pagehide", () => this._touch("closing"));
                    window.addEventListener("beforeunload", () => this._touch("closing"));
                    window.addEventListener("focus", () => this._touch("active"));
                    document.addEventListener("visibilitychange", () => {
                        this._touch(document.hidden ? "hidden" : "active");
                    });
                }
            } catch {
                // Si el navegador bloquea storage, la app sigue con sesion en memoria del circuito.
            }
        },
        get: function () {
            this.init();
            return localStorage.getItem(this._sessionKey);
        },
        set: function (value) {
            this.init();
            localStorage.setItem(this._sessionKey, value);
            this.notify();
        },
        clear: function () {
            localStorage.removeItem(this._sessionKey);
            localStorage.removeItem(this._tabsKey);
            this.notify();
        },
        notify: function () {
            window.dispatchEvent(new Event("controlLocalSessionChanged"));
        },
        subscribe: function (dotNetRef) {
            this.init();
            const id = (window.crypto && window.crypto.randomUUID)
                ? window.crypto.randomUUID()
                : `${Date.now()}-${Math.random()}`;
            const notifyDotNet = () => dotNetRef.invokeMethodAsync("OnBrowserSessionChanged");
            const storageHandler = (event) => {
                if (event.key === this._sessionKey) notifyDotNet();
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
        },
        _readTabs: function () {
            try {
                const raw = localStorage.getItem(this._tabsKey);
                const tabs = raw ? JSON.parse(raw) : [];
                return Array.isArray(tabs) ? tabs : [];
            } catch {
                return [];
            }
        },
        _writeTabs: function (tabs) {
            localStorage.setItem(this._tabsKey, JSON.stringify(tabs));
        },
        _activeTabs: function (tabs) {
            const now = Date.now();
            return tabs.filter(t => t && t.id && t.state !== "closing" && now - (t.at || 0) <= this._staleMs);
        },
        _touch: function (state) {
            if (!this._tabId) return;
            const now = Date.now();
            const tabs = this._readTabs()
                .filter(t => t && t.id && now - (t.at || 0) <= this._staleMs && t.id !== this._tabId);
            tabs.push({ id: this._tabId, state: state, at: now, href: location.origin });
            this._writeTabs(tabs);
        }
    }
};
