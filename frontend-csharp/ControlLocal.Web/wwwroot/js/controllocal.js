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

    session: {
        get: function () {
            return localStorage.getItem("controlLocal.session");
        },
        set: function (value) {
            localStorage.setItem("controlLocal.session", value);
        },
        clear: function () {
            localStorage.removeItem("controlLocal.session");
        }
    }
};
