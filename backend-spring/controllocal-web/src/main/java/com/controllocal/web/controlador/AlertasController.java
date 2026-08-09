package com.controllocal.web.controlador;

import com.controllocal.service.AlertaService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AlertaResponse;
import com.controllocal.web.dto.AtenderAlertaResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato CONGELADO del AlertasRest Jakarta: la campana.
 *
 * <p>El alcance vive en el service (AGENTE las suyas, BROKER las de su equipo,
 * ADMIN todas) y aqui no hay gate de rol: cualquier sesion lee su propia
 * campana.
 *
 * <p>Dos rarezas del cable que NO se limpian mientras siga congelado: los dos
 * verbos de {@code atender} —POST y PATCH, identicos, porque distintos clientes
 * usan uno u otro— y el hecho de que <b>el GET escriba</b> (ver abajo).
 */
@RestController
@RequestMapping("alertas")
public class AlertasController {

    private static final Logger LOG = LoggerFactory.getLogger(AlertasController.class);

    /** Cadencia del barrido de recontacto: como mucho una vez cada 5 minutos. */
    private static final long INTERVALO_SYNC_MS = 5 * 60 * 1000L;

    private final AlertaService alertas;

    /**
     * Ultima corrida del barrido. En la v1 es un {@code static volatile}; aqui
     * es un campo del bean, que Spring crea singleton — mismas semanticas, un
     * contador por proceso.
     */
    private volatile long ultimaSyncRecontacto = 0L;

    public AlertasController(AlertaService alertas) {
        this.alertas = alertas;
    }

    /**
     * <b>Este GET escribe.</b> Antes de leer materializa las alertas de
     * recontacto vencido, pero como mucho una vez cada 5 minutos y tragandose
     * cualquier fallo: si el barrido revienta, la campana igual responde. Es un
     * planificador de pobre —la v1 no tiene {@code @Scheduled}— y se replica
     * tal cual (D-F6-2): mover el barrido a un planificador cambiaria CUANDO
     * aparecen las alertas, y eso si se nota desde fuera.
     */
    @GetMapping
    public PageResponse<AlertaResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                               @RequestParam(defaultValue = "20") int tamano) {
        sincronizarRecontactoSiTocaba();
        return pagina(alertas.listar(pagina, tamano, SesionActual.actor()), pagina, tamano);
    }

    @PostMapping("{id}/atender")
    public AtenderAlertaResponse atenderPorPost(@PathVariable long id) {
        return atender(id);
    }

    /** Identico al POST: los dos verbos existen en el cable. */
    @PatchMapping("{id}/atender")
    public AtenderAlertaResponse atenderPorPatch(@PathVariable long id) {
        return atender(id);
    }

    private AtenderAlertaResponse atender(long id) {
        return new AtenderAlertaResponse(alertas.atender(id, SesionActual.actor()));
    }

    private void sincronizarRecontactoSiTocaba() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaSyncRecontacto <= INTERVALO_SYNC_MS) {
            return;
        }
        // Se marca ANTES de correr, igual que la v1: si el barrido falla no se
        // reintenta en la siguiente lectura, se espera al proximo intervalo.
        ultimaSyncRecontacto = ahora;
        try {
            alertas.sincronizarRecontacto(SesionActual.actor());
        } catch (RuntimeException error) {
            LOG.warn("[alertas] el barrido de recontacto fallo; la campana responde igual", error);
        }
    }

    private static PageResponse<AlertaResponse> pagina(
            Pagina<AlertaService.FichaAlerta> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(AlertaResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
