package com.controllocal.web.controlador;

import com.controllocal.service.ContratoService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.ContratoRequest;
import com.controllocal.web.dto.ContratoResponse;
import com.controllocal.web.dto.RenovacionContratoRequest;
import com.controllocal.web.dto.RevisionDisponibilidadRequest;
import com.controllocal.web.dto.RevisionDisponibilidadResponse;
import com.controllocal.web.dto.ResumenCierresResponse;
import com.controllocal.web.dto.TransicionContratoRequest;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transporte de consulta y preparacion. Firma y cobros usan la actuacion humana canonica de Core. */
@RestController
@RequestMapping("contratos")
public class ContratosController {

    private final ContratoService contratos;

    public ContratosController(ContratoService contratos) {
        this.contratos = contratos;
    }

    /**
     * La v1 corta con 403 si el rol no es ADMIN/BROKER/AGENTE; como el token
     * solo emite esas tres bandas, basta con exigir sesion.
     */
    /**
     * Listado de cierres. Los cuatro filtros son <b>aditivos y opcionales</b>:
     * una llamada sin ellos responde exactamente lo mismo que antes de que
     * existieran —incluido el orden congelado por id descendente—, asi que el
     * cable no se rompe.
     *
     * @param orden {@code cierre} ordena por fecha de cierre descendente, que
     *              es lo que la pantalla de cierres exitosos necesita.
     */
    @GetMapping
    public PageResponse<ContratoResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                 @RequestParam(defaultValue = "100") int tamano,
                                                 @RequestParam(required = false) String texto,
                                                 @RequestParam(required = false) String distrito,
                                                 @RequestParam(required = false) Long idAgente,
                                                 @RequestParam(required = false) String orden) {
        var filtros = new ContratoService.FiltrosContrato(texto, distrito, idAgente, orden,
                pagina, tamano);
        return pagina(contratos.listar(filtros, SesionActual.actor()), pagina, tamano);
    }

    /**
     * KPI de los cierres del alcance. Texto, distrito e id de agente se aplican
     * igual que en la tabla; de otro modo el resumen describiria otro conjunto.
     */
    @GetMapping("resumen")
    public ResumenCierresResponse resumen(@RequestParam(required = false) String texto,
                                          @RequestParam(required = false) String distrito,
                                          @RequestParam(required = false) Long idAgente) {
        var actor = SesionActual.actor();
        var filtros = new ContratoService.FiltrosContrato(texto, distrito, idAgente, null, 1, 1);
        return ResumenCierresResponse.desde(contratos.resumenCierres(filtros, actor),
                contratos.distritosDeCierres(filtros, actor),
                contratos.agentesDeCierres(filtros, actor));
    }

    @GetMapping("oportunidad/{idOportunidad}")
    public ContratoResponse obtenerPorOportunidad(@PathVariable long idOportunidad) {
        return ContratoResponse.desde(
                contratos.obtenerPorOportunidad(idOportunidad, SesionActual.actor()));
    }

    @PostMapping("en-proceso")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ContratoResponse> iniciarEnProceso(
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String clave,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Origen",required=false) String canal,
            @RequestBody(required = false) ContratoRequest dto) {
        ContratoResponse creado = ContratoResponse.desde(contratos.iniciarEnProceso(
                dto == null ? null : dto.aDatos(), SesionActual.actor(),
                com.controllocal.web.seguridad.ContextoDeActuacion.de(SesionActual.sesion()),clave,
                com.controllocal.service.soporte.Procedencia.deCabecera(canal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("{idContrato}/activar")
    @PreAuthorize("hasRole('AGENTE')")
    public ContratoResponse activar(@PathVariable long idContrato,
                                    @RequestBody(required = false) TransicionContratoRequest dto) {
        return ContratoResponse.desde(contratos.activar(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @PostMapping("{idContrato}/finalizar")
    @PreAuthorize("hasRole('AGENTE')")
    public ContratoResponse finalizar(@PathVariable long idContrato,
                                      @RequestBody(required = false) TransicionContratoRequest dto) {
        return ContratoResponse.desde(contratos.finalizar(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    /**
     * <b>BROKER, no AGENTE</b> (Bloque 7): «el broker decide, el agente
     * registra». Rescindir corta un alquiler que ya producia efectos y arrastra
     * consecuencias economicas; no es el registro de un hecho consumado como
     * finalizar por plazo.
     *
     * <p>Y <b>no lo hereda el gobierno</b>: la authority es la banda EFECTIVA,
     * asi que un {@code TENANT_ADMIN} sin rol operativo de broker recibe 403.
     * Administrar el tenant no produce decisiones comerciales (D-S0-17).
     */
    @PostMapping("{idContrato}/rescindir")
    @PreAuthorize("hasRole('BROKER')")
    public ContratoResponse rescindir(@PathVariable long idContrato,
                                      @RequestBody(required = false) TransicionContratoRequest dto) {
        return ContratoResponse.desde(contratos.rescindir(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    /** BROKER por lo mismo que rescindir: anular deja sin efecto un contrato. */
    @PostMapping("{idContrato}/anular")
    @PreAuthorize("hasRole('BROKER')")
    public ContratoResponse anular(@PathVariable long idContrato,
                                   @RequestBody(required = false) TransicionContratoRequest dto) {
        return ContratoResponse.desde(contratos.anular(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @PostMapping("{idContrato}/renovar")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ContratoResponse> renovar(
            @PathVariable long idContrato,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String clave,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Origen",required=false) String canal,
            @RequestBody(required = false) RenovacionContratoRequest dto) {
        ContratoResponse creado = ContratoResponse.desde(contratos.renovar(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor(),
                com.controllocal.web.seguridad.ContextoDeActuacion.de(SesionActual.sesion()),clave,
                com.controllocal.service.soporte.Procedencia.deCabecera(canal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * <b>Recuperacion de disponibilidad tras terminar el contrato (§7.3.2).</b>
     *
     * <p>Gate de <b>BROKER</b>, el mismo que rescindir: devolver un local al
     * mercado o retirarlo es un hecho comercial sobre el inmueble, y el
     * gobierno del cierre contractual ya es del broker. No se amplia a AGENTE
     * por comodidad de pantalla ni lo hereda el TENANT_ADMIN, que gobierna la
     * organizacion y no firma hechos del negocio (D-S0-17).
     */
    @PostMapping("{idContrato}/revision-disponibilidad")
    @PreAuthorize("hasRole('BROKER')")
    public RevisionDisponibilidadResponse revisarDisponibilidad(
            @PathVariable long idContrato,
            @RequestBody(required = false) RevisionDisponibilidadRequest dto) {
        return RevisionDisponibilidadResponse.desde(contratos.revisarDisponibilidad(idContrato,
                dto == null ? null : dto.resultado(), dto == null ? null : dto.motivo(),
                SesionActual.actor()));
    }

    private static PageResponse<ContratoResponse> pagina(
            Pagina<ContratoService.FichaContrato> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(ContratoResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
