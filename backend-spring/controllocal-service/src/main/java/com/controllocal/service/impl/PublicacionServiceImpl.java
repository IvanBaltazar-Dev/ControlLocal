package com.controllocal.service.impl;

import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Comercializacion;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoPublicacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.CondicionesEconomicas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas de publicacion: estado/titulo/codigo conservan sus defaults, pero la
 * moneda es obligatoria y nunca se supone. Version de anuncio
 * incremental y fecha de baja al cerrar. Mensajes identicos (contrato
 * congelado).
 */
@Service
public class PublicacionServiceImpl implements PublicacionService {

    private final PublicacionRepository publicaciones;
    private final PrecioPropiedadRepository precios;
    private final CaptacionRepository encargos;
    private final PropiedadRepository propiedades;
    private final AtributosGobernados gobierno;
    private final AtributosDeEncargo condiciones;

    /** Anunciar un encargo escribe su serie economica: lo hace SU agente (P0-4). */
    private final AutoridadDePropiedad autoridad;

    public PublicacionServiceImpl(PublicacionRepository publicaciones,
                                  PrecioPropiedadRepository precios,
                                  CaptacionRepository encargos,
                                  PropiedadRepository propiedades,
                                  AtributosGobernados gobierno,
                                  AtributosDeEncargo condiciones,
                                  AutoridadDePropiedad autoridad) {
        this.publicaciones = publicaciones;
        this.precios = precios;
        this.encargos = encargos;
        this.propiedades = propiedades;
        this.gobierno = gobierno;
        this.condiciones = condiciones;
        this.autoridad = autoridad;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaPublicacion> listarPorInmueble(long idPropiedad) {
        if (idPropiedad <= 0) {
            return List.of();
        }
        return publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(idPropiedad).stream()
                .map(publicacion -> ficha(publicacion, operacionDe(publicacion)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaPublicacion> listarDeEncargo(long idEncargo, Actor actor) {
        Captacion encargo = encargoDelTenant(idEncargo, actor);
        return publicaciones.deEncargo(actor.idOrganizacion(), idEncargo).stream()
                .map(publicacion -> ficha(publicacion, encargo.operacion()))
                .toList();
    }

    @Override
    @Transactional
    public FichaPublicacion crearEnEncargo(long idEncargo, DatosPublicacion datos, Actor actor) {
        Captacion encargo = encargoDelTenantParaEscribir(idEncargo, actor);
        // Anunciar un encargo es un acto de SU agente (P0-4). Hasta aqui solo se
        // comprobaba el tenant, y no era solo un problema de permisos: publicar
        // escribe un hito `P` en la serie economica del encargo
        // (`registrarImportePublicado`), asi que cualquier agente de la
        // corredora podia meter una cifra en el historico economico de un
        // encargo ajeno. Es la MISMA regla que ya cierra `actualizarEncargo` y
        // `POST /locales/{id}/precios`, y por eso es la misma llamada.
        autoridad.exigirEdicionDelEncargo(actor, encargo);
        // Publicar un encargo cerrado pondria en el mercado algo que ya no se
        // ofrece. Es una regla de negocio: vive aqui y no en el boton.
        if (!Captacion.esVivo(encargo.estadoActual())) {
            throw new ReglaNegocioException(
                    "El encargo " + encargo.getCodigoCaptacion() + " no esta vigente: no se puede "
                            + "publicar lo que ya no se ofrece.");
        }
        exigirPublicable(encargo.getPropiedad(), encargo, actor);
        Publicacion creada = construir(encargo.getPropiedad().getId(), datos, actor);
        creada.setIdEncargo(idEncargo);
        publicaciones.save(creada);
        registrarImportePublicado(creada);
        return ficha(creada, encargo.operacion());
    }

    @Override
    @Transactional(readOnly = true)
    public String codigoEstadoPublicacion(long idPropiedad) {
        if (idPropiedad <= 0) {
            return Publicacion.ESTADO_BORRADOR;
        }
        return publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(idPropiedad).stream()
                .findFirst()
                .map(Publicacion::getEstado)
                .orElse(Publicacion.ESTADO_BORRADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> codigosEstadoPublicacion(Collection<Long> idsPropiedad) {
        List<Long> ids = idsPropiedad == null ? List.of() : idsPropiedad.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> estados = new HashMap<>();
        for (Long id : ids) {
            estados.put(id, Publicacion.ESTADO_BORRADOR);
        }
        publicaciones.estadosPublicacion(ids)
                .forEach(fila -> estados.put(fila.getIdPropiedad(), fila.getEstado()));
        return estados;
    }

    /**
     * <b>El gate de publicacion</b> (Corte 0B, ampliado a los dos sujetos en 0C).
     *
     * <p>Una propiedad puede existir incompleta -- el corredor no sabe todo el
     * inmueble en la primera conversacion y no tiene por que. Pero cuando lo
     * ANUNCIA ya no es una nota suya: es una afirmacion publica, y publicar una
     * ficha a medias es lo que hace incomparable una cartera entera.
     *
     * <h2>Dos preguntas, no una</h2>
     * Publicar necesita que estén completas <b>la cosa y el trato</b>, y son
     * cosas distintas que se arreglan en pantallas distintas:
     *
     * <pre>
     *   ¿le falta algo al INMUEBLE?   -> AtributosGobernados   -> se edita en la ficha
     *   ¿le falta algo a ESTE ENCARGO? -> AtributosDeEncargo    -> se edita en el encargo
     * </pre>
     *
     * <p>El mismo departamento puede estar listo para alquilarse y no para
     * venderse. Por eso se preguntan por separado y el mensaje dice <b>cual de
     * las dos cosas</b> falta: una lista fundida obligaria al corredor a
     * adivinar donde va a arreglarlo.
     *
     * <p><b>La composicion vive aqui, en el caso de uso, y a la vista.</b> Nadie
     * mas abajo recibe la propiedad y el encargo juntos: un componente que
     * decidiera por los dos sujetos volveria a mezclarlos donde ya no se ve, que
     * es como se pierde la simetria que este corte acaba de establecer.
     *
     * <p>Ni este servicio ni ningun controlador interpretan ALT/PUB/OPC: eso lo
     * sabe {@code Exigencia}, y repartirlo seria tener la misma regla con tres
     * duenos.
     *
     * @param encargo el episodio concreto, o {@code null} cuando la publicacion
     *                no cuelga de ninguno -- entonces solo se puede preguntar
     *                por el inmueble, y decir que el trato esta completo seria
     *                inventarse un trato
     */
    private void exigirPublicable(Propiedad propiedad, Captacion encargo, Actor actor) {
        List<String> deLaFicha = gobierno.rotulosDe(actor.idOrganizacion(),
                propiedad.getTipoInmueble(),
                gobierno.faltantesDePropiedadParaPublicar(actor.idOrganizacion(), propiedad));

        List<String> delEncargo = List.of();
        if (encargo != null) {
            Comercializacion donde = Comercializacion.de(encargo, propiedad);
            delEncargo = condiciones.rotulosDe(actor.idOrganizacion(), donde,
                    condiciones.faltantesDeEncargoParaPublicar(actor.idOrganizacion(), donde));
        }

        if (deLaFicha.isEmpty() && delEncargo.isEmpty()) {
            return;
        }
        List<String> partes = new java.util.ArrayList<>();
        if (!deLaFicha.isEmpty()) {
            partes.add("de la ficha del inmueble falta " + String.join(", ", deLaFicha));
        }
        if (!delEncargo.isEmpty()) {
            partes.add("de las condiciones de este encargo falta "
                    + String.join(", ", delEncargo));
        }
        int cuantos = deLaFicha.size() + delEncargo.size();
        throw new ReglaNegocioException(
                "Todavia no se puede publicar: " + String.join("; y ", partes)
                        + ". Se puede registrar sin " + (cuantos == 1 ? "ese dato" : "esos datos")
                        + ", pero no anunciarlo.");
    }

    /**
     * El encargo del que cuelga una publicacion, o {@code null} si no cuelga de
     * ninguno.
     *
     * <p>No falla cuando no hay encargo, y la razon es la columna: {@code
     * publicacion.id_captacion} es ANULABLE -- hay anuncios anteriores a V70 cuya
     * operacion no se sabe -- y {@code cambiarEstado} pregunta por el encargo de
     * CUALQUIER anuncio. Medido el 2026-08-24: 0 de 12 filas la tienen a null,
     * pero el esquema lo permite y basta con eso.
     *
     * <p>Hasta el 2026-08-24 esta linea decia que la tolerancia existia porque
     * {@code crear(idPropiedad, ...)} "produce publicaciones sueltas". Ese metodo
     * SE RETIRO en el microcorte de las puertas, asi que la frase quedo falsa sin
     * que nadie la tocara: la razon verdadera nunca fue esa via.
     *
     * <p>Lo que no se hace es dar por buenas sus condiciones comerciales -- no hay
     * ninguna que mirar, y eso no es lo mismo que estar completas.
     *
     * <p><b>Toma la fila</b> (F2.10). Sus dos llamadores son de escritura
     * —{@code actualizar} y {@code cambiarEstado} del anuncio, que reescriben el
     * hito {@code P} en la serie del encargo— y en los dos es la primera carga
     * de esa fila en la transaccion, que es lo que hace valido el candado: la
     * publicacion se carga antes, pero es otra tabla. Lo que se gana es que la
     * autoridad de {@code exigirEncargoPropio} decida sobre el agente que
     * seguira siendo verdad cuando el hito se escriba.
     */
    private Captacion encargoDe(Publicacion publicacion, Actor actor) {
        Long idEncargo = publicacion.getIdEncargo();
        if (idEncargo == null || idEncargo <= 0) {
            return null;
        }
        return encargos.bloquearParaEscritura(actor.idOrganizacion(), idEncargo).orElse(null);
    }

    /**
     * <b>El anuncio se toca desde el encargo que lo autoriza</b> (P0-4).
     *
     * <p>Se llega a una publicacion por dos coordenadas —su encargo o su propio
     * id— y las dos escriben la misma serie economica. Cerrar solo la primera
     * dejaria la regla puesta en la puerta y abierta la ventana, que es
     * literalmente el comentario que ya vive en {@code cambiarEstado} sobre los
     * dos caminos de exposicion.
     *
     * <p>Una publicacion <b>sin encargo resuelto</b> no se deja tocar: sin
     * saber de quien es el anuncio no hay forma de decir si es tuyo, y suponer
     * que si es la respuesta que este P0 vino a quitar.
     *
     * <p><b>Desde V89 (D-P0-11) esta rama es una guarda defensiva</b>, no un
     * caso vivo: {@code publicacion.id_captacion} es NOT NULL y ninguna fila
     * puede llegar aqui sin encargo. Se conserva a proposito y no se degrada a
     * comentario por dos motivos comprobables: {@link #encargoDe} devuelve
     * {@code null} tambien cuando el encargo existe pero <b>es de otro
     * tenant</b> --- porque lo busca por {@code (organizacion, id)}--- y
     * cuando el id es {@code <= 0}; en ambos casos la respuesta correcta sigue
     * siendo negarse. Una guarda que solo se puede disparar por una via ya
     * cerrada en el esquema es barata; quitarla convierte el fallo silencioso
     * de esas dos vias en un {@code NullPointerException}.
     */
    private void exigirEncargoPropio(Publicacion publicacion, Actor actor) {
        Captacion encargo = encargoDe(publicacion, actor);
        if (encargo == null) {
            throw new ReglaNegocioException(
                    "Este anuncio no dice de que encargo es, asi que no se puede saber quien "
                            + "responde por el. Publica desde el encargo.");
        }
        autoridad.exigirEdicionDelEncargo(actor, encargo);
    }

    private Propiedad propiedadDe(Publicacion publicacion, Actor actor) {
        return propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), publicacion.getIdPropiedad())
                .orElseThrow(() -> new ReglaNegocioException(
                        "La publicacion apunta a una propiedad que no esta en la cartera."));
    }

    /** El anuncio en memoria, sin encargo todavia: lo pone quien lo crea. */
    private static Publicacion construir(long idPropiedad, DatosPublicacion datos, Actor actor) {
        String canal = canalOpcional(datos.canal());
        if (canal == null) {
            throw new ReglaNegocioException("El canal de la publicacion es obligatorio.");
        }
        String estado = codigoOpcional(datos.estado(), Publicacion.ESTADOS, "estado de publicacion");
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "del importe publicado");

        Publicacion p = new Publicacion();
        p.setOrganizacionId(actor.idOrganizacion());
        p.setIdPropiedad(idPropiedad);
        p.setCanal(canal);
        p.setUrlPublicacion(datos.urlPublicacion());
        p.setVersionAnuncio(1);
        p.setImportePublicado(datos.importePublicado());
        p.setMoneda(moneda);
        p.setEstado(estado == null ? Publicacion.ESTADO_PUBLICADO : estado);
        p.setTituloAnuncio(enBlanco(datos.tituloAnuncio()) ? "Publicacion " + idPropiedad : datos.tituloAnuncio());
        p.setCodigoOrigen(enBlanco(datos.codigoOrigen()) ? canal + "-" + idPropiedad : datos.codigoOrigen());
        p.setFechaPublicacion(OffsetDateTime.now());
        p.setFechaBaja(Publicacion.ESTADO_CERRADO.equals(p.getEstado()) ? OffsetDateTime.now() : null);
        return p;
    }

    @Override
    @Transactional
    public FichaPublicacion actualizar(long idPublicacion, DatosPublicacion datos, Actor actor) {
        Publicacion actual = delTenant(idPublicacion, actor);
        // Editar el anuncio cambia el importe publicado, y eso vuelve a escribir
        // un hito `P` en la serie del encargo. Llegar por el id de la
        // publicacion no puede ser una puerta mas barata que llegar por el del
        // encargo (P0-4).
        exigirEncargoPropio(actual, actor);
        if (datos != null) {
            String canal = canalOpcional(datos.canal());
            if (canal != null) {
                actual.setCanal(canal);
            }
            actual.setUrlPublicacion(datos.urlPublicacion());
            if (datos.importePublicado() != null) {
                actual.setImportePublicado(datos.importePublicado());
            }
            actual.setMoneda(CondicionesEconomicas.moneda(
                    datos.moneda(), "del importe publicado"));
            if (!enBlanco(datos.tituloAnuncio())) {
                actual.setTituloAnuncio(datos.tituloAnuncio());
            }
            if (!enBlanco(datos.codigoOrigen())) {
                actual.setCodigoOrigen(datos.codigoOrigen());
            }
        }
        actual.setVersionAnuncio((actual.getVersionAnuncio() == null ? 1 : actual.getVersionAnuncio()) + 1);
        publicaciones.save(actual);
        registrarImportePublicado(actual);
        return ficha(actual, operacionDe(actual));
    }

    @Override
    @Transactional
    public FichaPublicacion cambiarEstado(long idPublicacion, String estado, Actor actor) {
        if (enBlanco(estado)) {
            throw new ReglaNegocioException("El estado de la publicacion es obligatorio.");
        }
        if (!Publicacion.ESTADOS.contains(estado)) {
            throw new ReglaNegocioException("Estado de publicacion no valido: " + estado);
        }
        Publicacion actual = delTenant(idPublicacion, actor);
        // Y la tercera puerta al mismo hito: pasar el anuncio a PUBLICADO
        // tambien escribe la serie del encargo (P0-4).
        exigirEncargoPropio(actual, actor);
        // Los DOS caminos de exposicion externa preguntan lo mismo. Poner una
        // publicacion en PUBLICADO es anunciar igual que crearla, y cerrar solo
        // uno de los dos dejaria la regla puesta en la puerta y abierta la
        // ventana.
        if (Publicacion.ESTADO_PUBLICADO.equals(estado)) {
            exigirPublicable(propiedadDe(actual, actor), encargoDe(actual, actor), actor);
        }
        actual.setEstado(estado);
        actual.setFechaBaja(Publicacion.ESTADO_CERRADO.equals(estado) ? OffsetDateTime.now() : null);
        if (Publicacion.ESTADO_PUBLICADO.equals(estado) && actual.getFechaPublicacion() == null) {
            actual.setFechaPublicacion(OffsetDateTime.now());
        }
        publicaciones.save(actual);
        registrarImportePublicado(actual);
        return ficha(actual, operacionDe(actual));
    }

    /**
     * <b>E0.2 — deja constancia del importe que el mercado VE.</b>
     *
     * <p>Hasta V70 esta escritura tenia dos defectos que solo se veian juntos:
     *
     * <ol>
     *   <li><b>la operacion era una constante.</b> Se escribia siempre
     *       {@code ALQUILER} porque la publicacion no sabia de que encargo era.
     *       El comentario que habia aqui ya lo anunciaba: «la publicacion de una
     *       venta llegara con el encargo de venta y su propio importe, y
     *       entonces esta linea dejara de ser una constante». Es ahora;</li>
     *   <li><b>el hito nacia huerfano.</b> Sin {@code id_captacion}, los hitos
     *       {@code P} no aparecian en ninguna ficha -- ni en el historico del
     *       encargo ni en la historia del inmueble, que filtran por encargo--.
     *       Existian en la base y no los veia nadie.</li>
     * </ol>
     *
     * <p>Con el encargo resuelto, el hito se escribe con SU operacion y atado a
     * SU encargo. Y una publicacion sin encargo (las anteriores a V70 que no se
     * pudieron atribuir) <b>no escribe hito</b>: es mejor no tener el dato que
     * tenerlo colgando de una operacion supuesta.
     *
     * <p><b>Solo se escribe si la publicacion esta PUBLICADA.</b> Un borrador no
     * lo ve nadie, y anotar su importe como "publicado" meteria en la serie
     * precios que nunca existieron para el mercado. Por eso tambien
     * {@code cambiarEstado} pasa por aqui: el instante en que un borrador se
     * publica es la primera vez que ese importe se ve.
     *
     * <p><b>Deduplica</b> contra el ultimo {@code P} del mismo encargo: sin esto
     * cada edicion del anuncio escribiria uno, cambie o no el precio -- y
     * {@code actualizar} pasa por aqui en cada guardado.
     *
     * <p>Este parrafo decia hasta el 2026-08-24 que
     * {@code LocalComercialServiceImpl} llamaba a {@code sincronizar} "en TODA
     * actualizacion". <b>Era falso</b>: esa clase inyecta {@code PublicacionService}
     * y <b>solo le pregunta el estado de publicacion</b>
     * --{@code codigoEstadoPublicacion} y {@code codigosEstadoPublicacion}, tres
     * llamadas--, pero <b>nunca llamo a {@code sincronizar}</b>, que se retiro en
     * el microcorte de las puertas por no tener un solo consumidor de produccion.
     * La deduplicacion sigue haciendo falta igual, ahora por {@code actualizar}.
     */
    private void registrarImportePublicado(Publicacion publicacion) {
        if (!Publicacion.ESTADO_PUBLICADO.equals(publicacion.getEstado())
                || publicacion.getImportePublicado() == null
                || publicacion.getMoneda() == null
                || publicacion.getIdPropiedad() == null) {
            return;
        }
        OperacionInmobiliaria operacion = operacionDe(publicacion);
        if (operacion == null) {
            // Publicacion sin encargo resuelto: no se sabe que operacion
            // publica. Se declara faltante -- no se escribe un hito con una
            // operacion supuesta, que es como se llenan las series de mentiras.
            return;
        }
        boolean sinCambioEconomico = precios
                .findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                        publicacion.getIdPropiedad(), PrecioPropiedad.HITO_PUBLICADO)
                .filter(ultimo -> java.util.Objects.equals(ultimo.getIdCaptacion(),
                        publicacion.getIdEncargo()))
                .filter(ultimo -> ultimo.getMoneda().equals(publicacion.getMoneda()))
                // compareTo y no equals: 4500 y 4500.00 son el mismo precio, y
                // equals de BigDecimal los distingue por escala.
                .filter(ultimo -> ultimo.getMonto().compareTo(publicacion.getImportePublicado()) == 0)
                .isPresent();
        if (sinCambioEconomico) {
            return;
        }
        PrecioPropiedad hito = new PrecioPropiedad();
        hito.setOrganizacionId(publicacion.getOrganizacionId());
        hito.setIdPropiedad(publicacion.getIdPropiedad());
        hito.setOperacion(operacion);
        hito.setHito(PrecioPropiedad.HITO_PUBLICADO);
        hito.setMoneda(publicacion.getMoneda());
        hito.setMonto(publicacion.getImportePublicado());
        hito.setFecha(LocalDate.now());
        precios.save(hito.delEncargo(publicacion.getIdEncargo()));
    }

    /** La operacion que publica un anuncio: la de su encargo, o ninguna. */
    private OperacionInmobiliaria operacionDe(Publicacion publicacion) {
        if (publicacion.getIdEncargo() == null) {
            return null;
        }
        return encargos.findById(publicacion.getIdEncargo())
                .map(Captacion::operacion)
                .orElse(null);
    }

    /** El encargo del tenant, o 404. Un id ajeno no distingue "no existe" de "no puedes". */
    private Captacion encargoDelTenant(long idEncargo, Actor actor) {
        return encargos.findById(idEncargo)
                .filter(encargo -> encargo.getOrganizacionId() == actor.idOrganizacion())
                .orElseThrow(() -> new NoEncontradoException("Encargo"));
    }

    /**
     * <b>El mismo encargo, con la fila tomada</b> (F2.10).
     *
     * <p>Mismo 404 y misma frontera de tenant —ahora resuelta en la consulta—;
     * lo unico que cambia es el candado, para que la autoridad que se comprueba
     * justo despues sea la que seguira siendo verdad cuando el anuncio y su hito
     * se escriban.
     *
     * <p>Es un metodo aparte y no una bandera porque {@link #encargoDelTenant}
     * lo usa tambien {@code listarDeEncargo}, que es {@code readOnly}: una
     * transaccion de solo lectura no puede ejecutar {@code SELECT ... FOR
     * UPDATE}.
     */
    private Captacion encargoDelTenantParaEscribir(long idEncargo, Actor actor) {
        return encargos.bloquearParaEscritura(actor.idOrganizacion(), idEncargo)
                .orElseThrow(() -> new NoEncontradoException("Encargo"));
    }

    private Publicacion delTenant(long idPublicacion, Actor actor) {
        return publicaciones.findByOrganizacionIdAndId(actor.idOrganizacion(), idPublicacion)
                .orElseThrow(() -> new ReglaNegocioException("Publicacion no encontrada."));
    }

    private static String canalOpcional(String canal) {
        if (enBlanco(canal)) {
            return null;
        }
        if (!Publicacion.CANALES.contains(canal)) {
            throw new ReglaNegocioException("Valor invalido para canal de publicacion: " + canal);
        }
        return canal;
    }

    private static String codigoOpcional(String valor, java.util.Set<String> dominio, String campo) {
        if (enBlanco(valor)) {
            return null;
        }
        if (!dominio.contains(valor)) {
            throw new ReglaNegocioException("Valor invalido para " + campo + ": " + valor);
        }
        return valor;
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * El anuncio, listo para leerse.
     *
     * <p>{@code importeRotulo} sale de la OPERACION del encargo: «precio de
     * venta» o «renta mensual». Sin el, la pantalla tendria que decidirlo con un
     * ternario sobre la operacion, que es semantica inmobiliaria en la interfaz
     * (D-A-1 §5). Sin encargo resuelto se declara nulo, no se supone.
     */
    private static FichaPublicacion ficha(Publicacion p, OperacionInmobiliaria operacion) {
        return new FichaPublicacion(p.getId(), p.getIdEncargo(), p.getCanal(), p.getTituloAnuncio(),
                p.getImportePublicado(), p.getMoneda(),
                operacion == null ? null : operacion.nombreDelImporte(),
                p.getEstado(), rotuloDelEstado(p.getEstado()),
                Fechas.local(p.getFechaPublicacion()),
                Fechas.local(p.getFechaBaja()), p.getUrlPublicacion(), p.getCodigoOrigen());
    }

    private static String rotuloDelEstado(String estado) {
        return estado == null ? null : EstadoPublicacion.desde(estado).descripcion();
    }
}
