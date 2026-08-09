package com.controllocal.web.controlador;

import com.controllocal.service.ContratoService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.ComisionAsignarRequest;
import com.controllocal.web.dto.ComisionCobroRequest;
import com.controllocal.web.dto.ComisionMovimientoRequest;
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

/**
 * Contrato CONGELADO del ContratosRest Jakarta: el cierre del trato. El
 * {@code POST} no es un alta mas — dispara la cascada de SIETE efectos (§6) y
 * es lo que cierra la oportunidad como exitosa, por eso
 * {@code POST /oportunidades/{id}/cierre-exitoso} responde 400 para siempre.
 *
 * <p>Dos detalles del cable que se pierden con facilidad:
 * <ul>
 *   <li>el {@code tamano} por defecto de este recurso es <b>100</b>, no 10;</li>
 *   <li>los dos gates de comision son del <b>BROKER supervisor sin ADMIN</b>
 *       (el admin solo lee). El alcance fino —por CAPTACION, no por agente
 *       como en solicitudes— lo impone el service.</li>
 * </ul>
 */
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

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ContratoResponse> registrar(
            @RequestBody(required = false) ContratoRequest dto) {
        ContratoResponse creado = ContratoResponse.desde(
                contratos.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("en-proceso")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ContratoResponse> iniciarEnProceso(
            @RequestBody(required = false) ContratoRequest dto) {
        ContratoResponse creado = ContratoResponse.desde(contratos.iniciarEnProceso(
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("{idContrato}/firmar")
    @PreAuthorize("hasRole('AGENTE')")
    public ContratoResponse firmar(@PathVariable long idContrato,
                                   @RequestBody(required = false) TransicionContratoRequest dto) {
        return ContratoResponse.desde(contratos.firmar(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
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
            @RequestBody(required = false) RenovacionContratoRequest dto) {
        ContratoResponse creado = ContratoResponse.desde(contratos.renovar(idContrato,
                dto == null ? null : dto.aDatos(), SesionActual.actor()));
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

    /** Gate de BROKER, sin ADMIN: el monto de la empresa se calcula solo. */
    @PostMapping("{idContrato}/comision/asignar")
    @PreAuthorize("hasRole('BROKER')")
    public ContratoResponse asignarComision(@PathVariable long idContrato,
                                            @RequestBody(required = false) ComisionAsignarRequest dto) {
        return ContratoResponse.desde(contratos.asignarComision(idContrato,
                dto == null ? null : dto.montoAgente(), SesionActual.actor()));
    }

    /** Gate de BROKER, sin ADMIN: desenlace del cobro (Cobrada o Anulada). */
    @PostMapping("{idContrato}/comision/cobro")
    @PreAuthorize("hasRole('BROKER')")
    public ContratoResponse registrarCobro(@PathVariable long idContrato,
                                           @RequestBody(required = false) ComisionCobroRequest dto) {
        return ContratoResponse.desde(contratos.registrarCobroComision(idContrato,
                dto == null ? null : dto.estado(), dto == null ? null : dto.fechaCobro(),
                dto == null ? null : dto.formaPago(), SesionActual.actor()));
    }

    /**
     * <b>Comando monetario: acepta {@code Idempotency-Key}.</b>
     *
     * <p>La cabecera es OPCIONAL mientras el contrato legado siga congelado
     * —un cliente viejo que no la manda sigue funcionando igual—, pero el SPA
     * nuevo la envia SIEMPRE: genera un UUID al iniciar la operacion y reenvia
     * exactamente esa clave en cada reintento de esa misma operacion.
     *
     * <p>Misma clave + mismo comando devuelve el resultado original sin crear
     * otra fila. Misma clave + comando distinto es un 409: la clave identifica
     * una operacion, no sirve para dos.
     */
    @PostMapping("{idContrato}/comision/movimientos")
    @PreAuthorize("hasRole('BROKER')")
    public ContratoResponse registrarMovimiento(
            @PathVariable long idContrato,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            @RequestBody(required = false) ComisionMovimientoRequest dto) {
        return ContratoResponse.desde(contratos.registrarMovimientoComision(idContrato,
                dto == null ? null : dto.tipo(), dto == null ? null : dto.monto(),
                dto == null ? null : dto.moneda(), dto == null ? null : dto.fecha(),
                dto == null ? null : dto.formaPago(), dto == null ? null : dto.observacion(),
                claveIdempotencia, SesionActual.actor()));
    }

    private static PageResponse<ContratoResponse> pagina(
            Pagina<ContratoService.FichaContrato> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(ContratoResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
