package com.controllocal.web.dto;

import com.controllocal.service.ReportePropietarioService;

/** Vista previa de los tres valores autoritativos derivados por el servidor. */
public record ReportePropietarioPreviewResponse(int consultas, int visitas, String objeciones) {

    public static ReportePropietarioPreviewResponse desde(
            ReportePropietarioService.ResumenAvance resumen) {
        return new ReportePropietarioPreviewResponse(
                resumen.consultas(), resumen.visitas(), resumen.objeciones());
    }
}
