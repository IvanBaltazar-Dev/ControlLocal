package com.controllocal.service.impl;

import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.AtributosGobernados;
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

    public PublicacionServiceImpl(PublicacionRepository publicaciones,
                                  PrecioPropiedadRepository precios,
                                  CaptacionRepository encargos,
                                  PropiedadRepository propiedades,
                                  AtributosGobernados gobierno,
                                  AtributosDeEncargo condiciones) {
        this.publicaciones = publicaciones;
        this.precios = precios;
        this.encargos = encargos;
        this.propiedades = propiedades;
        this.gobierno = gobierno;
        this.condiciones = condiciones;
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
        Captacion encargo = encargoDelTenant(idEncargo, actor);
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

    @Override
    @Transactional
    public FichaPublicacion crear(long idPropiedad, DatosPublicacion datos, Actor actor) {
        if (idPropiedad <= 0) {
            throw new ReglaNegocioException("El local de la publicacion es obligatorio.");
        }
        Publicacion p = construir(idPropiedad, datos, actor);
        publicaciones.save(p);
        registrarImportePublicado(p);
        return ficha(p, operacionDe(p));
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
     * <p>No falla cuando no hay encargo: {@code crear(idPropiedad, ...)} existe
     * y produce publicaciones sueltas. Lo que no se hace es dar por buenas sus
     * condiciones comerciales -- no hay ninguna que mirar, y eso no es lo mismo
     * que estar completas.
     */
    private Captacion encargoDe(Publicacion publicacion, Actor actor) {
        Long idEncargo = publicacion.getIdEncargo();
        if (idEncargo == null || idEncargo <= 0) {
            return null;
        }
        return encargos.findById(idEncargo)
                .filter(encargo -> encargo.getOrganizacionId() == actor.idOrganizacion())
                .orElse(null);
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

    @Override
    @Transactional
    public void sincronizar(long idPropiedad, String codigoLocal, BigDecimal precioReferencial,
                            String monedaReferencial, String codigoEstado, Actor actor) {
        if (enBlanco(codigoEstado)) {
            return;
        }
        if (!Publicacion.ESTADOS.contains(codigoEstado)) {
            // Mensaje identico al CodigoEnum.fromCodigo de la v1 (llega como 400).
            throw new IllegalArgumentException("Codigo invalido para EstadoPublicacion: " + codigoEstado);
        }
        String moneda = CondicionesEconomicas.moneda(
                monedaReferencial, "del precio referencial");
        // El formulario heredado edita un LOCAL, asi que el encargo que
        // corresponde es el de alquiler -- que es lo unico que ese formulario
        // sabe registrar. Si no hay exactamente uno, la publicacion se queda
        // sin encargo en vez de atribuirse al que sea.
        Long idEncargo = encargoUnicoDeAlquiler(idPropiedad, actor);
        Publicacion principal = publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(idPropiedad).stream()
                .findFirst()
                .orElse(null);
        if (principal == null && Publicacion.ESTADO_BORRADOR.equals(codigoEstado)) {
            return;
        }
        if (principal == null) {
            principal = new Publicacion();
            principal.setOrganizacionId(actor.idOrganizacion());
            principal.setIdPropiedad(idPropiedad);
            principal.setCanal(Publicacion.CANAL_WEB_PROPIA);
            principal.setVersionAnuncio(1);
            principal.setMoneda(moneda);
            principal.setCodigoOrigen("WEB-" + idPropiedad);
            principal.setFechaPublicacion(OffsetDateTime.now());
        }
        if (principal.getIdEncargo() == null) {
            principal.setIdEncargo(idEncargo);
        }
        principal.setEstado(codigoEstado);
        principal.setImportePublicado(precioReferencial);
        principal.setMoneda(moneda);
        principal.setTituloAnuncio("Publicacion " + codigoLocal);
        principal.setFechaBaja(Publicacion.ESTADO_CERRADO.equals(codigoEstado) ? OffsetDateTime.now() : null);
        publicaciones.save(principal);
        registrarImportePublicado(principal);
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
     * cada edicion de local escribiria uno, porque
     * {@code LocalComercialServiceImpl} llama a {@code sincronizar} en TODA
     * actualizacion, cambie o no el precio.
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

    private Publicacion delTenant(long idPublicacion, Actor actor) {
        return publicaciones.findByOrganizacionIdAndId(actor.idOrganizacion(), idPublicacion)
                .orElseThrow(() -> new ReglaNegocioException("Publicacion no encontrada."));
    }

    /**
     * El unico encargo de ALQUILER de una propiedad, si lo hay.
     *
     * <p>Lo usa el camino heredado: el formulario de local edita un alquiler y
     * nada mas. Con cero o con varios devuelve {@code null} en vez de elegir,
     * porque atribuir el anuncio al encargo equivocado es peor que dejarlo sin
     * atribuir.
     */
    private Long encargoUnicoDeAlquiler(long idPropiedad, Actor actor) {
        List<Captacion> deAlquiler = encargos.encargosDe(actor.idOrganizacion(), idPropiedad).stream()
                .filter(encargo -> encargo.operacion() == OperacionInmobiliaria.ALQUILER)
                .toList();
        return deAlquiler.size() == 1 ? deAlquiler.get(0).getId() : null;
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
