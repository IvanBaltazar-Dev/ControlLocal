package com.controllocal.web.controlador;

import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.FotoPropiedadService.FotoDePropiedad;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.almacen.GaleriaDeFotos;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import jakarta.servlet.http.HttpServletRequest;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.AsignarResponsableRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.CandidatoResponsableResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.EdicionRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.FilaPropiedadResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.FotoRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.FotoResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.OrdenFotosRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.PropiedadResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.RegistroRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.RegistroResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.TraspasoResponse;
import com.controllocal.service.ObservacionMercadoService;
import com.controllocal.web.dto.ObservacionMercadoDtos.ObservacionRequest;
import com.controllocal.web.dto.ObservacionMercadoDtos.ObservacionResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <b>La propiedad universal por el cable</b> (D-E4-1).
 *
 * <h2>Por que un recurso nuevo y no mas verbos en {@code /locales}</h2>
 * {@code /locales} es el alta de la v1: un local comercial en alquiler, con un
 * solo propietario y el precio en una columna. Estirarlo para admitir siete
 * tipos, copropiedad y dos operaciones simultaneas habria dejado un recurso con
 * dos comportamientos segun que campos llegaran — y con las 57 pantallas
 * actuales colgando del comportamiento viejo.
 *
 * <p>{@code /propiedades} es el modelo nuevo entero, y el viejo sigue donde
 * estaba hasta que las pantallas migren. Los dos escriben en las mismas tablas.
 *
 * <h2>Las dos cabeceras</h2>
 * <ul>
 *   <li>{@code Idempotency-Key}: del cliente. Con ella, un reintento devuelve
 *       lo que produjo el primer intento en vez de crear una segunda propiedad.
 *       Es lo que hace seguro poner un canal conversacional delante.</li>
 *   <li>{@code X-Origen}: UI, KAIROS, API o SISTEMA. Va al evento de dominio y
 *       es lo que permite responder <i>"quien decidio esto"</i> cuando el
 *       agente conversacional opere (D-K-1).</li>
 * </ul>
 */
@RestController
@RequestMapping("propiedades")
public class PropiedadesUniversalesController {

    private final PropiedadUniversalService propiedades;
    /** Lo que se VIO del mercado. Serie propia: observar no es un hecho comercial (V76). */
    private final ObservacionMercadoService observaciones;
    private final ProcedenciaDeCabeceras procedencias;
    /** La galeria, compartida con {@code /locales/{id}/fotos} (WP-C). */
    private final GaleriaDeFotos galeria;

    public PropiedadesUniversalesController(PropiedadUniversalService propiedades,
                                            ProcedenciaDeCabeceras procedencias,
                                            ObservacionMercadoService observaciones,
                                            GaleriaDeFotos galeria) {
        this.propiedades = propiedades;
        this.procedencias = procedencias;
        this.observaciones = observaciones;
        this.galeria = galeria;
    }

    /**
     * Alta universal. Una transaccion: propiedad, ubicacion, titulares,
     * atributos, encargos, condiciones, primeros hitos y evento.
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<RegistroResponse> registrar(
            @RequestBody(required = false) RegistroRequest dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            HttpServletRequest peticion) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la propiedad son obligatorios.");
        }
        RegistroResponse creada = RegistroResponse.desde(
                propiedades.registrar(dto.aDatos(claveIdempotencia, procedencias.de(peticion)), SesionActual.actor()));
        // 200 y no 201 cuando fue un reintento: no se creo nada esta vez, y
        // decirle 201 al cliente le haria contar dos altas donde hubo una.
        return ResponseEntity.status(creada.reintento() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(creada);
    }

    /**
     * <b>Lo que se vio del mercado sobre este inmueble</b> (V76).
     *
     * <p>No es un hecho comercial de BROX: no lo autorizo, no lo publico y no lo
     * negocio. Vive en su propia serie y no toca ni el historico del encargo ni
     * el precio de la propiedad — «lo vi anunciado a 190 000» no es un precio
     * autorizado.
     *
     * <p><b>Append-only.</b> No hay PUT ni DELETE, y su ausencia es la regla: una
     * observacion es un hecho fechado y corregirla borraria la muestra. Si el
     * precio cambio, se observa otra vez.
     */
    @com.controllocal.web.capacidades.CapacidadReconocida("registrar_observacion_mercado")
    @PostMapping("{id}/observaciones")
    @PreAuthorize("hasRole('AGENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservacionResponse observar(@PathVariable long id,
                                        @RequestBody ObservacionRequest dto,
                                        @RequestHeader(value="Idempotency-Key",required=false) String clave,
                                        jakarta.servlet.http.HttpServletRequest peticion) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la observacion son obligatorios.");
        }
        return ObservacionResponse.desde(
                observaciones.registrar(dto.aDatos(id),SesionActual.actor(),clave,procedencias.de(peticion)));
    }

    /** Lo observado de una propiedad, de lo mas reciente a lo mas antiguo. */
    @GetMapping("{id}/observaciones")
    public List<ObservacionResponse> observaciones(@PathVariable long id) {
        return observaciones.listarDe(id, SesionActual.actor()).stream()
                .map(ObservacionResponse::desde)
                .toList();
    }

    // ------------------------------------------------------------------
    // La galeria de fotos (WP-C)
    // ------------------------------------------------------------------

    /**
     * <b>Las fotos de la propiedad, en su orden.</b> La de {@code orden = 1} es
     * la portada.
     *
     * <p>Cada elemento lleva su {@code clave} y el {@code proveedor} que la
     * sirve; <b>la URL la arma el cliente</b> con
     * {@code /documentos/contenido?clave=}. El Core no publica URLs absolutas
     * porque tendria que saber por donde se le llega desde fuera —dominio,
     * prefijo, puerto—, que es configuracion del despliegue y cambia sin que
     * cambie ningun dato.
     *
     * <p>Es la misma galeria que {@code GET /locales/{id}/fotos}: misma tabla,
     * mismo servicio, mismas fotos. Lo que cambia es la forma —aqui viaja
     * {@code orden}— y el tope de escritura.
     */
    @GetMapping("{id}/fotos")
    public List<FotoResponse> fotos(@PathVariable long id) {
        return galeria.listar(id, SesionActual.actor()).stream()
                .map(FotoResponse::desde)
                .toList();
    }

    /**
     * Sube una foto: llega en base64, el binario va al almacen y aqui queda su
     * clave. <b>Maximo 12</b> por propiedad.
     *
     * <p>Se aceptan PNG y JPG hasta 5 MB, comprobando la <b>firma binaria</b> y
     * no solo la extension. Quien puede es quien puede editar la propiedad
     * ({@code AutoridadDePropiedad.exigirEdicion}): una foto es la ficha del
     * inmueble, no un adjunto suelto.
     */
    @PostMapping("{id}/fotos")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<FotoResponse> subirFoto(@PathVariable long id,
                                                  @RequestBody(required = false) FotoRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("La foto es obligatoria.");
        }
        var actor = SesionActual.actor();
        FotoDePropiedad foto = galeria.agregar(id, dto.nombreArchivo(), dto.contenidoBase64(),
                FotoPropiedadService.TOPE_UNIVERSAL, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(FotoResponse.desde(foto));
    }

    /**
     * Quita una foto y su binario. Las siguientes se recolocan: la galeria
     * vuelve a ser 1..N y la portada sigue siendo la foto 1.
     */
    @DeleteMapping("{id}/fotos/{idFoto}")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> eliminarFoto(@PathVariable long id, @PathVariable long idFoto) {
        galeria.eliminar(idFoto, SesionActual.actor());
        return ResponseEntity.noContent().build();
    }

    /**
     * <b>El orden que decide la persona</b>, y con el la portada.
     *
     * <p>Es {@code PUT} y no {@code PATCH} porque reemplaza el orden <b>entero</b>:
     * el cuerpo nombra las fotos de la galeria completa, en su nueva posicion.
     * Un cuerpo parcial obligaria al Core a decidir donde van las que faltan, y
     * cualquiera de las dos respuestas seria una invencion sobre la intencion de
     * quien reordeno.
     */
    @PutMapping("{id}/fotos/orden")
    @PreAuthorize("hasRole('AGENTE')")
    public List<FotoResponse> reordenarFotos(@PathVariable long id,
                                             @RequestBody(required = false) OrdenFotosRequest dto) {
        if (dto == null || dto.ids() == null || dto.ids().isEmpty()) {
            throw new ReglaNegocioException("Hay que decir el orden de las fotos.");
        }
        return galeria.reordenar(id, dto.ids(), SesionActual.actor()).stream()
                .map(FotoResponse::desde)
                .toList();
    }

    /**
     * <b>La cartera por el modelo universal.</b> Va antes de {@code {id}} por el
     * orden del router.
     *
     * <p>La diferencia con {@code GET /locales} no es cosmetica: alli cada fila
     * lleva <b>un</b> precio y ninguna operacion —la proyeccion nacio cuando
     * todo era alquiler—, y aqui cada fila lleva <b>sus encargos</b>, que pueden
     * ser dos. «Venta + alquiler» lo compone el cliente al pintar; no existe
     * como valor.
     *
     * <p>{@code operaciones} filtra por las que la propiedad tiene VIVAS, y con
     * las dos declaradas significa «tiene las dos», no «tiene alguna»: es el
     * filtro que sirve para encontrar exactamente esas.
     */
    @GetMapping
    @com.controllocal.web.capacidades.CapacidadReconocida({"buscar_propiedades"})
    public PageResponse<FilaPropiedadResponse> listar(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer tamano,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String tipoPropiedad,
            @RequestParam(required = false) String distrito,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String operaciones) {
        int paginaSolicitada = ClientesController.paginaSolicitada(page, pagina);
        int tamanoSolicitado = pageSize != null ? pageSize : tamano != null ? tamano : 20;
        var resultado = propiedades.listar(
                new PropiedadUniversalService.FiltrosPropiedad(texto, tipoPropiedad, distrito,
                        estado, operaciones, paginaSolicitada, tamanoSolicitado),
                SesionActual.actor());
        return new PageResponse<>(
                resultado.items().stream().map(FilaPropiedadResponse::desde).toList(),
                resultado.total(), Math.max(1, paginaSolicitada),
                Math.max(1, Math.min(100, tamanoSolicitado)));
    }

    /**
     * Lo que el filtro puede ofrecer, sacado de la cartera real.
     *
     * <p>Los tipos y las operaciones NO estan aqui: son vocabulario del dominio
     * y los publica el motor de captura ({@code GET /captura/apertura}), que es
     * su unico dueno. Repetirlos aqui seria la segunda lista.
     */
    @GetMapping("filtros")
    public PropiedadUniversalService.OpcionesDeFiltro filtros() {
        return propiedades.opcionesDeFiltro(SesionActual.actor());
    }

    /** La propiedad leida por el modelo universal: titulares, atributos y encargos. */
    @GetMapping("{id}")
    @com.controllocal.web.capacidades.CapacidadReconocida({"estado_de_propiedad"})
    public PropiedadResponse consultar(@PathVariable long id) {
        return PropiedadResponse.desde(propiedades.consultar(id, SesionActual.actor()),
                galeria.proveedor());
    }

    /**
     * Edicion parcial. Cambiar el importe de una operacion <b>anade</b> un hito
     * al historico; el anterior no se pierde nunca.
     */
    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    @com.controllocal.web.capacidades.CapacidadReconocida({"editar_propiedad"})
    public PropiedadUniversalService.ResultadoEdicion editar(
            @PathVariable long id,
            @RequestBody(required = false) EdicionRequest dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            HttpServletRequest peticion) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la propiedad son obligatorios.");
        }
        return propiedades.editar(id,dto.aDatos(claveIdempotencia,procedencias.de(peticion)),
                SesionActual.actor());
    }

    // ------------------------------------------------------------------
    // El traspaso del responsable (P0-2)
    // ------------------------------------------------------------------

    /**
     * <b>Quien responde por esta propiedad a partir de ahora.</b>
     *
     * <p>Es la unica forma de mover la autoridad de escritura despues del alta,
     * y la unica de sacar a una propiedad de FALTANTE. Lo decide un BROKER —o
     * el gobierno del tenant—, nunca el agente: si el traspaso fuera del propio
     * agente, la autoridad seria autoservicio.
     *
     * <p>Es {@code POST} y no {@code PUT} a proposito: <b>anade un hecho</b> al
     * expediente de la propiedad. Un {@code PUT} sugeriria que el valor
     * anterior se reemplaza y se olvida, y lo que ocurre es lo contrario — la
     * fila anterior se conserva y el rastro se acumula.
     *
     * <p>No reasigna ningun encargo. El de venta y el de alquiler siguen siendo
     * de quien eran: para eso esta {@code POST /captaciones/{id}/reasignar},
     * que es otra decision y otro hecho.
     *
     * <p><b>El cuerpo declara sobre que responsable se actua</b> (D-P0-9):
     * {@code idResponsableActual} —el que se vio en la ficha— o
     * {@code sinResponsableActual: true} si estaba FALTANTE, <b>exactamente
     * una</b> de las dos. Si al ejecutarse el responsable ya no es ese, la
     * respuesta es <b>409</b> y no se ha escrito nada: el traspaso no se
     * reinterpreta sobre un estado que el actor no vio. La validacion de la
     * declaracion vive en el propio DTO para que sea la misma por cualquier
     * canal.
     */
    @PostMapping("{id}/responsable")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TraspasoResponse asignarResponsable(@PathVariable long id,
                                               @RequestBody(required = false) AsignarResponsableRequest dto) {
        if (dto == null || dto.idAgente() == null) {
            throw new ReglaNegocioException(
                    "Hay que decir a que agente se le asigna: no se deduce de sus captaciones.");
        }
        return TraspasoResponse.desde(propiedades.asignarResponsable(
                id, dto.idAgente(), dto.motivo(), dto.observado(), SesionActual.actor()));
    }

    /**
     * <b>A quien puedo traspasarla</b> — los candidatos ya elegibles para ESTE
     * recurso y ESTE actor (D-P0-7 + D-P0-12).
     *
     * <p><b>Existe para que Angular no decida autoridad.</b> Sin esta
     * superficie, la pantalla del traspaso tendria que pedir la lista de agentes
     * del tenant y depurarla con su propia copia de las cinco condiciones de
     * elegibilidad —que es una lista de permisos viviendo en el cliente— o
     * dejar que el broker eligiera a ciegas y descubriera el rechazo despues.
     * Las dos salidas son la misma: la regla escrita dos veces.
     *
     * <p>Paginado y buscable porque la lista es del <b>tenant</b>, no del
     * formulario: filtrar en el cliente sobre una pagina devuelve resultados
     * incompletos en cuanto haya mas agentes que sitio.
     *
     * <p><b>Y no autoriza nada.</b> El POST de al lado revalida banda, tenant,
     * saliente, destino y elegibilidad: entre pedir esta lista y usarla, un
     * agente puede quedar desactivado. Las dos preguntas comparten el mismo
     * predicado SQL justamente para que no puedan responder cosas distintas.
     */
    @GetMapping("{id}/responsable/candidatos")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public PageResponse<CandidatoResponsableResponse> candidatosAResponsable(
            @PathVariable long id,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer tamano) {
        int paginaSolicitada = ClientesController.paginaSolicitada(page, pagina);
        int tamanoSolicitado = pageSize != null ? pageSize : tamano != null ? tamano : 20;
        var resultado = propiedades.candidatosAResponsable(id, texto, paginaSolicitada,
                tamanoSolicitado, SesionActual.actor());
        return new PageResponse<>(
                resultado.items().stream().map(CandidatoResponsableResponse::desde).toList(),
                resultado.total(), Math.max(1, paginaSolicitada),
                Math.max(1, Math.min(100, tamanoSolicitado)));
    }

    /**
     * <b>El expediente de traspasos: superficie de GOBIERNO</b> (C2), del mas
     * reciente al mas antiguo.
     *
     * <p>Lo leen el BROKER —dentro de su alcance de supervision— y el
     * TENANT_ADMIN dentro de su tenant. <b>El AGENTE no</b>, y eso incluye al
     * responsable vigente de la propiedad: el sabe que responde el y tiene todo
     * lo que necesita para operar, pero no hereda quienes fueron los
     * responsables anteriores, ni los motivos de cada traspaso, ni las
     * observaciones de gobierno sobre agentes que ya no la llevan. El texto
     * libre del motivo es dato interno de gobierno.
     *
     * <p><b>Dos comprobaciones y en este orden</b>, porque no son la misma:
     * <ol>
     *   <li>la banda, aqui — un AGENTE no entra ni con un id de su propia
     *       propiedad;</li>
     *   <li>el alcance sobre <b>este</b> objeto, en el servicio — un BROKER
     *       valido sobre una propiedad que no responde ante ninguno de sus
     *       supervisados tampoco entra. La anotacion no puede saber eso: no
     *       conoce la fila.</li>
     * </ol>
     * Y antes que las dos, la frontera de tenant: un id de otra corredora
     * responde <b>404</b>, no 403.
     *
     * <p>Si el nuevo responsable necesita contexto, la respuesta no es abrirle
     * el expediente: seria una <b>nota de traspaso operativa</b>, distinta del
     * historico de gobierno. No existe todavia.
     */
    @GetMapping("{id}/responsable/historial")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public List<TraspasoResponse> historialDeResponsables(@PathVariable long id) {
        return propiedades.traspasosDe(id, SesionActual.actor()).stream()
                .map(TraspasoResponse::desde)
                .toList();
    }

}
