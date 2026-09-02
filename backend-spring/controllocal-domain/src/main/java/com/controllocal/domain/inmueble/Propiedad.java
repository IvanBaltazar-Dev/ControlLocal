package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoRegistroPropiedad;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.persona.PersonaRol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Inmueble de la cartera (generaliza el local_comercial de la v1, MEJ-12/31):
 * aqui viven los atributos comunes a cualquier tipo. Lo especifico de cada uno
 * NO cuelga del agregado: son atributos gobernados por el catalogo, y desde
 * V71 tampoco lo comercial -- rubro, apto para licencia y carga electrica
 * tenian una tabla por tipo y ya no. El propietario es el rol PROPIETARIO de una persona
 * (Party-Role): en el cable congelado idPropietario = persona_rol.id.
 * Los codigos de 1 caracter son el vocabulario heredado del cable; los enums
 * legibles llegan tras el corte del modulo.
 */
@Entity
@Table(name = "propiedad")
public class Propiedad extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "PROPIEDAD";
    public static final String ENTIDAD_DISPONIBILIDAD_TIPO = "DISPONIBILIDAD_PROPIEDAD";

    /** Codigos del adaptador legado; no son columnas del modelo normalizado. */
    public static final String LEGADO_DISPONIBLE = "D";
    public static final String LEGADO_NO_DISPONIBLE = "N";
    public static final String LEGADO_INACTIVO = "I";
    public static final String TIPO_LOCAL = "L";
    public static final String TIPO_OFICINA = "O";

    /** 'C' comercial, 'V' vivienda, 'I' industrial, 'M' mixto. */
    public static final String USO_COMERCIAL = "C";
    public static final Set<String> USOS = Set.of("C", "V", "I", "M");

    // ------------------------------------------------------------------
    // Aqui vivian `TIPOS_INMUEBLE` y `ESTADOS`. Los retiro V71 con la puerta
    // heredada, que era su unico usuario.
    //
    // `TIPOS_INMUEBLE` no se repone, y esta es la razon: declaraba SEIS
    // codigos -- L O D C T X -- cuando la base admite SIETE desde V54, y esa
    // discrepancia era justo lo que impedia editar un ALMACEN ("Valor invalido
    // para tipo de inmueble: A"). El vocabulario vive en un solo sitio,
    // `AtributosGobernados.codigoDelTipo`, que los tiene los siete. Dos listas
    // de tipos es como se llega a que la base acepte uno que el codigo rechaza.
    //
    // `USO_COMERCIAL` si se queda, porque abajo alimenta el defecto del campo
    // `uso`. Ese defecto es material del corte siguiente, no de este: cambiarlo
    // aqui alteraria en silencio el uso de toda propiedad que se cree sin
    // declararlo, y eso es exactamente la clase de reinterpretacion callada que
    // 0A viene a impedir. Queda anotado, no arreglado de paso.
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_propiedad")
    private Long id;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    /** Nombre de distrito tal como se escribio (cable congelado; ver Distrito). */
    @Column(name = "distrito", nullable = false, length = 100)
    private String distrito;

    /** FK resuelta contra el catalogo; NULL si el nombre no esta catalogado. */
    @Column(name = "id_distrito")
    private Long idDistrito;

    @Column(name = "metraje", nullable = false, precision = 10, scale = 2)
    private BigDecimal metraje;

    @Column(name = "precio_referencial", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioReferencial;

    /** Moneda declarada de la renta referencial; no tiene valor por defecto. */
    @Column(name = "moneda_referencial", length = 3)
    private String monedaReferencial;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "estado_registro", nullable = false, length = 1)
    private String estadoRegistro;

    @Column(name = "disponibilidad_comercial", nullable = false, length = 1)
    private String disponibilidadComercial;

    @Column(name = "interior_unidad", length = 40)
    private String interiorUnidad;

    @Column(name = "piso", length = 30)
    private String piso;

    @Column(name = "referencia_interna", length = 120)
    private String referenciaInterna;

    @Column(name = "nombre_edificio_galeria", length = 150)
    private String nombreEdificioGaleria;

    @Column(name = "tipo_inmueble", nullable = false, length = 1)
    private String tipoInmueble = TIPO_LOCAL;

    @Column(name = "uso", nullable = false, length = 1)
    private String uso = USO_COMERCIAL;

    // ------------------------------------------------------------------
    // Aqui vivian ambientes, antiguedad_anios, frente, zonificacion,
    // numero_estacionamientos y cuota_mantenimiento. Su autoridad es
    // `atributo_propiedad` desde D-E4-3, y V62 retiro las columnas.
    //
    // No se reponen "por comodidad de mapeo": tener el campo aqui es lo que
    // permitiria escribirlo, y un valor escrito donde nadie lee es el fallo
    // que esta tanda cerro. Se leen y se escriben por LectorPorAutoridad y
    // AtributosGobernados.
    //
    // `metraje` sigue arriba, y es correcto: es el unico estructural.
    // ------------------------------------------------------------------

    /**
     * <b>La identidad registral del inmueble</b> (V79, Corte 2).
     *
     * <p>Los dos campos son la autoridad de los conceptos
     * {@code PARTIDA_REGISTRAL} y {@code OFICINA_REGISTRAL}, y se escriben
     * <b>solo</b> por sus claves de catalogo a traves de
     * {@code EscritorEstructural}. Nadie los toca por su nombre desde un caso de
     * uso: eso seria la matriz «clave -> campo» otra vez (D-E4-3).
     *
     * <p>Anulables las dos, y es la regla del 3g: un inmueble puede conocerse
     * antes de tener su partida a la vista. NULL significa <b>todavia no se
     * sabe</b>, nunca «no tiene».
     *
     * <p>Estan aqui y no en {@code atributo_propiedad} porque son identidad y no
     * descripcion: no dependen del tipo de inmueble y sobreviven a cualquier
     * encargo. Hasta V79 la partida existia en un unico sitio de toda la base
     * --{@code condicion_compraventa.partida_registral}, colgada de una
     * solicitud de venta-- asi que un inmueble que solo se alquilaba no tenia
     * donde llevarla.
     */
    @Column(name = "partida_registral", length = 40)
    private String partidaRegistral;

    /**
     * La oficina donde esta inscrita esa partida. Su vocabulario vive en el
     * catalogo ({@code catalogo_atributo_opcion}) y lo comprueban la capa de
     * servicio y el trigger {@code tg_vocabulario_estructural}; aqui no hay
     * ninguna lista de oficinas, y no debe haberla.
     */
    @Column(name = "oficina_registral", length = 40)
    private String oficinaRegistral;

    @Column(name = "zona_urbanizacion", length = 150)
    private String zonaUrbanizacion;

    @Column(name = "geo_lat", precision = 10, scale = 7)
    private BigDecimal geoLat;

    @Column(name = "geo_long", precision = 10, scale = 7)
    private BigDecimal geoLong;

    /**
     * Rol PROPIETARIO de la persona duena (la columna tipo_rol_propietario
     * con DEFAULT+CHECK+FK compuesta vive solo en la BD y garantiza que el
     * rol referenciado sea de ese tipo). Se navega desde este lado, LAZY.
     */
    /**
     * <b>Opcional desde V76.</b> Se puede conocer un inmueble sin saber de quien
     * es: obligar a declararlo obligaria a inventarlo. La titularidad de verdad
     * vive en {@code titularidad_propiedad}; esto es su proyeccion para el cable
     * heredado, y solo se escribe cuando hay representante.
     *
     * <p>La obligacion no desaparece: se muda al ENCARGO, que es donde sigue
     * siendo cierta -- una relacion comercial nace de alguien que puede
     * encargarla.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_propietario")
    private PersonaRol rolPropietario;

    /**
     * <b>Como llego BROX a conocer este inmueble</b> (V76). Procedencia, no
     * estado: no cambia porque despues se capte. Ver {@link OrigenIncorporacion}.
     */
    @Column(name = "origen_incorporacion", nullable = false, length = 12)
    private String origenIncorporacion = OrigenIncorporacion.OPERACION.codigo();

    /** Quien la incorporo. NULL en las filas anteriores a V76, que no lo saben. */
    @Column(name = "id_rol_incorporo")
    private Long idRolIncorporo;

    /**
     * <b>Quien responde HOY por esta propiedad</b> (V87, P0-1). Es la unica
     * autoridad de escritura sobre sus hechos: ver no concede editar, y el
     * alcance de tenant tampoco.
     *
     * <p><b>No confundir con {@link #idRolIncorporo}.</b> Aquella es
     * procedencia historica y se escribe una vez; esta es autoridad ACTUAL y
     * cambia por traspaso. Que la primera diga "la incorporo Ana" no da a Ana
     * ningun permiso hoy.
     *
     * <p><b>Es independiente de los ENCARGOS.</b> No se deriva de
     * {@code captacion.id_rol_agente} —una propiedad admite una VENTA y un
     * ALQUILER vivos de agentes distintos, y entonces "el agente de la
     * propiedad" no seria una pregunta con respuesta— y reasignar un encargo no
     * la mueve.
     *
     * <p><b>NULL es FALTANTE</b>, no "de todos": la propiedad se ve y no se
     * edita hasta que un BROKER asigne. Sin defecto y sin relleno.
     *
     * <h2>Por que la columna es de SOLO INSERCION para el ORM</h2>
     * {@code updatable = false} no es una optimizacion: es la mitad estructural
     * de D-P0-10. {@code Propiedad} no lleva {@code @DynamicUpdate}, asi que el
     * flush de una entidad gestionada escribe la fila <b>entera</b> con los
     * valores que tiene en memoria. Sin esta marca, cualquier caso de uso que
     * cargue la propiedad y la guarde despues —{@code PUT /propiedades/&#123;id&#125;}
     * es el mas directo— reescribe {@code id_rol_responsable} con el valor que
     * leyo al cargar. Si entre la carga y el flush otro comitea un traspaso
     * A&rarr;B, la edicion lo pisa y devuelve la autoridad a A <b>sin fila en el
     * expediente</b>: exactamente el «responsable cambiado sin traza» que
     * D-P0-10 prohibe, y por una puerta que no pidio moverlo.
     *
     * <p>Con la marca puesta, la columna tiene <b>dos</b> escritores y ninguno
     * mas: el {@code INSERT} del alta —{@code AutoridadDePropiedad.fijarAlAlta}
     * la pone <b>antes</b> del primer {@code save}, y {@code insertable} sigue
     * siendo {@code true}— y, en {@code UPDATE},
     * {@code PropiedadRepository.cambiarResponsableSi}, que es un {@code UPDATE}
     * JPQL y por tanto <b>no</b> pasa por esta anotacion. Es decir: el
     * compare-and-set del traspaso, que exige el estado observado (D-P0-9).
     *
     * <p>{@link #responsable(Long)} sigue existiendo y sigue escribiendo el
     * campo en memoria: el rastro, el evento y la ficha devuelta leen ese valor
     * dentro de la misma transaccion. Lo que ya no hace es viajar a la base por
     * su cuenta.
     *
     * <p>Lo mide {@code CausalidadDelTraspasoIntegrationTest}, caso
     * <i>«una edicion concurrente no revierte un traspaso»</i>.
     */
    @Column(name = "id_rol_responsable", updatable = false)
    private Long idRolResponsable;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    // ------------------------------------------------------------------
    // Maquina de estados (Transicionable): el estado SOLO muta via el
    // componente Transiciones de la capa service (blindado por ArchUnit).
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return ENTIDAD_TIPO;
    }

    @Override
    public String estadoActual() {
        return estadoRegistro;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estadoRegistro = EstadoRegistroPropiedad.desde(nuevoEstado).codigo();
    }

    @Transient
    public EstadoRegistroPropiedad estadoRegistroTipado() {
        return estadoRegistro == null ? null : EstadoRegistroPropiedad.desde(estadoRegistro);
    }

    @Transient
    public DisponibilidadComercial disponibilidadComercialTipada() {
        return disponibilidadComercial == null
                ? null : DisponibilidadComercial.desde(disponibilidadComercial);
    }

    /**
     * Adaptador de frontera para el contrato D/N/I: el modelo interno nunca
     * vuelve a perder la causa de la no disponibilidad.
     */
    public String estadoLegado() {
        if (estadoRegistroTipado() == EstadoRegistroPropiedad.INACTIVO) return LEGADO_INACTIVO;
        return disponibilidadComercialTipada() == DisponibilidadComercial.DISPONIBLE
                ? LEGADO_DISPONIBLE : LEGADO_NO_DISPONIBLE;
    }

    public void iniciarDisponible() {
        estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
        disponibilidadComercial = DisponibilidadComercial.DISPONIBLE.codigo();
    }

    /**
     * <b>Registrada y activa, pero todavia sin oferta</b> (V75).
     *
     * <p>Es la propiedad que solo se esta prospectando: existe en el registro
     * maestro, se le puede poner ubicacion, titularidad, atributos, duplicados e
     * interacciones, y NO esta ofrecida. La disponibilidad queda en {@code null}
     * a proposito, y no es un quinto estado: {@code DISPONIBILIDAD_PROPIEDAD} es
     * una maquina con transiciones y rotulos, y «todavia no ha entrado en la
     * maquina» es su ausencia, no un valor suyo.
     *
     * <p>Estamparle DISPONIBLE era una <b>deduccion</b>: nada decia lo
     * contrario, asi que se afirmaba. Un inmueble que nadie ha encargado no
     * esta disponible para alquilar; sencillamente no se ofrece.
     */
    public void registrarSinOferta() {
        estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
        disponibilidadComercial = null;
    }

    /**
     * <b>La propiedad entra en el mercado</b> al abrirse su primer encargo.
     *
     * <p>Va aqui y no en el alta porque el hecho que la pone en oferta es el
     * ENCARGO, venga del alta comercial o de una prospeccion captada. Y no pisa
     * una disponibilidad ya declarada: si esta ALQUILADA, RESERVADA o RETIRADA,
     * abrir otro encargo no la vuelve a poner disponible.
     */
    public void entrarEnOferta() {
        if (disponibilidadComercial == null) {
            disponibilidadComercial = DisponibilidadComercial.DISPONIBLE.codigo();
        }
    }

    /** ¿Se esta ofreciendo? {@code false} mientras no tenga ningun encargo. */
    @Transient
    public boolean estaOfrecida() {
        return disponibilidadComercial != null;
    }

    public void aplicarEstadoLegado(String estadoLegado) {
        if (LEGADO_INACTIVO.equals(estadoLegado)) {
            estadoRegistro = EstadoRegistroPropiedad.INACTIVO.codigo();
            disponibilidadComercial = DisponibilidadComercial.RETIRADO.codigo();
        } else if (LEGADO_DISPONIBLE.equals(estadoLegado)) {
            estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
            disponibilidadComercial = DisponibilidadComercial.DISPONIBLE.codigo();
        } else if (LEGADO_NO_DISPONIBLE.equals(estadoLegado)) {
            estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
            disponibilidadComercial = DisponibilidadComercial.RETIRADO.codigo();
        } else {
            throw new IllegalArgumentException("Estado legado de local invalido: " + estadoLegado);
        }
    }

    public void marcarAlquilado() {
        cambiarDisponibilidadA(DisponibilidadComercial.ALQUILADO);
    }

    public void retirarDelMercado() {
        cambiarDisponibilidadA(DisponibilidadComercial.RETIRADO);
    }

    public void reactivarDisponibilidad() {
        if (estadoRegistroTipado() != EstadoRegistroPropiedad.ACTIVO) {
            throw new IllegalStateException("Un local inactivo no puede reactivarse comercialmente.");
        }
        cambiarDisponibilidadA(DisponibilidadComercial.DISPONIBLE);
    }

    /** Escritura tipada de la dimension comercial; nunca recibe codigos libres. */
    public void cambiarDisponibilidadA(DisponibilidadComercial nuevaDisponibilidad) {
        if (nuevaDisponibilidad == null) {
            throw new IllegalArgumentException("La disponibilidad comercial es obligatoria.");
        }
        disponibilidadComercial = nuevaDisponibilidad.codigo();
    }

    /** Como se conocio este inmueble. Ver {@link OrigenIncorporacion}. */
    @Transient
    public OrigenIncorporacion origen() {
        return OrigenIncorporacion.desde(origenIncorporacion);
    }

    /**
     * Declara la procedencia al incorporarla. Se escribe UNA vez, al nacer: una
     * propiedad observada que despues se capta siguio conociendose observando.
     */
    public void incorporadaPor(OrigenIncorporacion origen, Long idRolActor) {
        this.origenIncorporacion = origen.codigo();
        this.idRolIncorporo = idRolActor;
    }

    public String getOrigenIncorporacion() {
        return origenIncorporacion;
    }

    public Long getIdRolIncorporo() {
        return idRolIncorporo;
    }

    /**
     * <b>Fija quien responde por la propiedad.</b>
     *
     * <p>Lo llama <b>solo</b> {@code service.soporte.AutoridadDePropiedad}, y
     * eso lo vigila {@code AutoridadDeLaPropiedadTest}: si el setter fuera de
     * uso libre, cualquier caso de uso podria darse la autoridad a si mismo y
     * la regla se evaporaria sin que nadie tocara la comprobacion.
     */
    public void responsable(Long idRolAgente) {
        this.idRolResponsable = idRolAgente;
    }

    public Long getIdRolResponsable() {
        return idRolResponsable;
    }

    /** ¿Hay alguien que responda por ella? {@code false} = FALTANTE. */
    @Transient
    public boolean tieneResponsable() {
        return idRolResponsable != null;
    }

    public String getEstadoRegistro() { return estadoActual(); }
    public String getDisponibilidadComercial() {
        return disponibilidadComercial;
    }

    // `asignarDetalleLocal` vivia aqui hasta V71. El rubro, el apto para
    // licencia y la carga electrica dejaron de tener una tabla por tipo: son
    // tres claves gobernadas mas, y se escriben por el enrutador de autoridad
    // como las otras. El agregado ya no sabe que existen, que es justo lo que
    // hace universal al modelo.

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getDistrito() {
        return distrito;
    }

    public void setDistrito(String distrito) {
        this.distrito = distrito;
    }

    public Long getIdDistrito() {
        return idDistrito;
    }

    public void setIdDistrito(Long idDistrito) {
        this.idDistrito = idDistrito;
    }

    public BigDecimal getMetraje() {
        return metraje;
    }

    public void setMetraje(BigDecimal metraje) {
        this.metraje = metraje;
    }

    public BigDecimal getPrecioReferencial() {
        return precioReferencial;
    }

    public void setPrecioReferencial(BigDecimal precioReferencial) {
        this.precioReferencial = precioReferencial;
    }

    public String getMonedaReferencial() {
        return monedaReferencial;
    }

    public void setMonedaReferencial(String monedaReferencial) {
        this.monedaReferencial = monedaReferencial;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipoInmueble() {
        return tipoInmueble;
    }

    public void setTipoInmueble(String tipoInmueble) {
        this.tipoInmueble = tipoInmueble;
    }

    public String getUso() {
        return uso;
    }

    public void setUso(String uso) {
        this.uso = uso;
    }

    public String getZonaUrbanizacion() {
        return zonaUrbanizacion;
    }

    public void setZonaUrbanizacion(String zonaUrbanizacion) {
        this.zonaUrbanizacion = zonaUrbanizacion;
    }

    public BigDecimal getGeoLat() {
        return geoLat;
    }

    public void setGeoLat(BigDecimal geoLat) {
        this.geoLat = geoLat;
    }

    public BigDecimal getGeoLong() {
        return geoLong;
    }

    public void setGeoLong(BigDecimal geoLong) {
        this.geoLong = geoLong;
    }

    public String getInteriorUnidad() { return interiorUnidad; }
    public void setInteriorUnidad(String interiorUnidad) { this.interiorUnidad = interiorUnidad; }
    public String getPiso() { return piso; }
    public void setPiso(String piso) { this.piso = piso; }

    public String getPartidaRegistral() { return partidaRegistral; }
    public void setPartidaRegistral(String partidaRegistral) {
        this.partidaRegistral = partidaRegistral;
    }

    public String getOficinaRegistral() { return oficinaRegistral; }
    public void setOficinaRegistral(String oficinaRegistral) {
        this.oficinaRegistral = oficinaRegistral;
    }
    public String getReferenciaInterna() { return referenciaInterna; }
    public void setReferenciaInterna(String referenciaInterna) { this.referenciaInterna = referenciaInterna; }
    public String getNombreEdificioGaleria() { return nombreEdificioGaleria; }
    public void setNombreEdificioGaleria(String nombreEdificioGaleria) { this.nombreEdificioGaleria = nombreEdificioGaleria; }

    public PersonaRol getRolPropietario() {
        return rolPropietario;
    }

    public void setRolPropietario(PersonaRol rolPropietario) {
        this.rolPropietario = rolPropietario;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
