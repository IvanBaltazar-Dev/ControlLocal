package com.controllocal.web.controlador;

import com.controllocal.service.ReportePropietarioService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.dto.ReportePropietarioPreviewResponse;
import com.controllocal.web.dto.ReportePropietarioRequest;
import com.controllocal.web.dto.ReportePropietarioResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Contrato CONGELADO E2 del ReportesPropietarioRest Jakarta. */
@RestController
@RequestMapping("captaciones/{idCaptacion}/reportes-propietario")
public class ReportesPropietarioController {

    private final ReportePropietarioService reportes;

    public ReportesPropietarioController(ReportePropietarioService reportes) {
        this.reportes = reportes;
    }

    @GetMapping
    public List<ReportePropietarioResponse> listar(@PathVariable long idCaptacion) {
        return reportes.listar(idCaptacion, SesionActual.actor()).stream()
                .map(ReportePropietarioResponse::desde)
                .toList();
    }

    @GetMapping("preview")
    public ReportePropietarioPreviewResponse preview(
            @PathVariable long idCaptacion,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return ReportePropietarioPreviewResponse.desde(
                reportes.preview(idCaptacion, fecha(desde), fecha(hasta),
                        SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ReportePropietarioResponse> registrar(
            @PathVariable long idCaptacion,
            @RequestBody(required = false) ReportePropietarioRequest dto) {
        ReportePropietarioResponse creado = ReportePropietarioResponse.desde(
                reportes.registrar(idCaptacion, dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    private static LocalDate fecha(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (DateTimeParseException error) {
            throw new ReglaNegocioException("Fecha no valida: " + iso);
        }
    }
}
