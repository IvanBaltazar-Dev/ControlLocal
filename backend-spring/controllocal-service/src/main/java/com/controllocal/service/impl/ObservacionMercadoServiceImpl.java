package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.ObservacionMercado;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.ObservacionMercadoRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ObservacionMercadoService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.CondicionesEconomicas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Lo que se vio del mercado</b> (V76).
 *
 * <h2>Lo que este servicio NO hace, que es la mitad de su definicion</h2>
 * No escribe {@code precio_propiedad}, no toca {@code propiedad.precio_referencial},
 * no cambia la disponibilidad y no abre nada. <b>Observar no autoriza, no
 * publica y no negocia.</b>
 *
 * <blockquote>BROX nunca convierte una observacion de mercado en un hecho
 * comercial ni inventa una relacion para poder conservar conocimiento.</blockquote>
 *
 * <p>Es una tentacion real y hay que nombrarla: seria comodo que anotar «lo vi a
 * 190 000» dejara el inmueble con un precio que pintar en el listado. Pero ese
 * numero no lo autorizo nadie, y en cuanto se proyectara sobre la propiedad
 * cualquier busqueda por precio maximo lo trataria como si un propietario lo
 * hubiera aceptado.
 */
@Service
public class ObservacionMercadoServiceImpl implements ObservacionMercadoService {

    /** Lo que cabe en una nota util. La columna es VARCHAR(300). */
    private static final int MAX_DETALLE = 300;
    private static final int MAX_FUENTE = 30;

    private final ObservacionMercadoRepository observaciones;
    private final PropiedadRepository propiedades;
    private final PersonaRolRepository roles;

    public ObservacionMercadoServiceImpl(ObservacionMercadoRepository observaciones,
                                         PropiedadRepository propiedades,
                                         PersonaRolRepository roles) {
        this.observaciones = observaciones;
        this.propiedades = propiedades;
        this.roles = roles;
    }

    @Override
    @Transactional
    public FichaObservacion registrar(DatosObservacion datos, Actor actor) {
        if (datos == null || datos.idPropiedad() == null) {
            throw new ReglaNegocioException("La observacion tiene que decir de que inmueble es.");
        }
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), datos.idPropiedad())
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));

        OperacionInmobiliaria operacion = operacionDeclarada(datos.operacion());
        LocalDate fecha = fechaObservada(datos.fechaObservada());
        BigDecimal importe = importeObservado(datos.importe(), operacion);
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "de la observacion");
        String fuente = fuenteDeclarada(datos.fuente());

        ObservacionMercado observacion = new ObservacionMercado(actor.idOrganizacion(),
                propiedad.getId(), fecha, operacion.codigo(), importe, moneda, fuente,
                recorte(datos.detalle()), actor.idRolOperativo());
        observaciones.save(observacion);
        return ficha(observacion, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaObservacion> listarDe(long idPropiedad, Actor actor) {
        propiedades.findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
        List<ObservacionMercado> filas =
                observaciones.findByIdPropiedadOrderByFechaObservadaDescIdDesc(idPropiedad);
        // Los nombres de quien observo, en UNA consulta. Preguntarlos por fila
        // seria el N+1 que RC-003 retiro del repositorio, con un dato nuevo.
        Map<Long, String> nombres = nombresDe(filas);
        return filas.stream().map(fila -> ficha(fila, nombres)).toList();
    }

    // ------------------------------------------------------------------

    private Map<Long, String> nombresDe(List<ObservacionMercado> filas) {
        List<Long> ids = filas.stream().map(ObservacionMercado::getIdRolActor).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> porRol = new LinkedHashMap<>();
        roles.findAllById(ids).forEach(rol -> {
            if (rol.getPersona() != null) {
                porRol.put(rol.getId(), rol.getPersona().getNombresORazonSocial());
            }
        });
        return porRol;
    }

    private static FichaObservacion ficha(ObservacionMercado o, Map<Long, String> nombres) {
        OperacionInmobiliaria operacion = OperacionInmobiliaria.deCodigo(o.getOperacion());
        return new FichaObservacion(o.getId(), o.getFechaObservada(), operacion.name(),
                operacion.name().charAt(0) + operacion.name().substring(1).toLowerCase(Locale.ROOT),
                o.getImporte(), o.getMoneda(),
                // El mismo rotulo que usa el encargo: «precio de venta» o «renta
                // mensual». Un importe observado sin decir cual de los dos es
                // vale lo mismo que no tenerlo.
                operacion.nombreDelImporte(),
                o.getFuente(), o.getDetalle(), o.getIdRolActor(),
                nombres.get(o.getIdRolActor()));
    }

    /**
     * La operacion, dicha. Sin defecto: el mismo importe es un precio de venta o
     * una renta mensual segun cual sea, y suponer una guardaria un comparable
     * falso — que es exactamente lo contrario de para lo que existe este dato.
     */
    private static OperacionInmobiliaria operacionDeclarada(String operacion) {
        if (operacion == null || operacion.isBlank()) {
            throw new ReglaNegocioException(
                    "Declara que se observaba: VENTA o ALQUILER. El mismo importe significa un "
                            + "precio de venta o una renta mensual segun cual sea.");
        }
        try {
            return OperacionInmobiliaria.desde(operacion);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
    }

    /**
     * Cuando se vio. Obligatoria, y nunca futura: un precio sin fecha no se
     * puede comparar con nada, y una fecha por venir no es una observacion sino
     * una expectativa.
     */
    private static LocalDate fechaObservada(LocalDate fecha) {
        if (fecha == null) {
            throw new ReglaNegocioException(
                    "Falta la fecha en que se observo. Un precio sin fecha no se puede comparar "
                            + "con ningun otro: es lo que hace util a la serie.");
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException(
                    "No se observa lo que todavia no ha pasado. Una fecha futura no es una "
                            + "observacion: es una expectativa, y esa no es evidencia.");
        }
        return fecha;
    }

    private static BigDecimal importeObservado(BigDecimal importe, OperacionInmobiliaria operacion) {
        if (importe == null || importe.signum() < 0) {
            throw new ReglaNegocioException(
                    "Falta el " + operacion.nombreDelImporte() + " observado.");
        }
        return importe;
    }

    /**
     * De donde salio. Obligatoria: sin fuente es un rumor, y una serie de
     * rumores no es un comparable.
     *
     * <p>El vocabulario esta <b>abierto a proposito</b>. Las fuentes reales son
     * un hecho del campo —un portal, un cartel, otro corredor, el propio
     * propietario— y nadie las ha inventariado todavia; cerrarlas hoy seria
     * elegir una lista arbitraria u obligar a que la gente meta lo que ve en la
     * casilla que menos miente. Se normaliza a mayusculas para que al menos no
     * se convierta en tres formas de escribir lo mismo.
     */
    private static String fuenteDeclarada(String fuente) {
        if (fuente == null || fuente.isBlank()) {
            throw new ReglaNegocioException(
                    "Falta la fuente de la observacion. Sin decir de donde salio, un precio "
                            + "observado es un rumor: la evidencia va antes que la inferencia.");
        }
        String limpia = fuente.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (limpia.length() > MAX_FUENTE) {
            throw new ReglaNegocioException(
                    "La fuente admite " + MAX_FUENTE + " caracteres: es una etiqueta, no una nota. "
                            + "El detalle va en su campo.");
        }
        return limpia;
    }

    private static String recorte(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return null;
        }
        String limpio = detalle.trim();
        return limpio.length() <= MAX_DETALLE ? limpio : limpio.substring(0, MAX_DETALLE);
    }
}
