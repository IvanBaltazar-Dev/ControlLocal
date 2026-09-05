package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.persona.DetalleAgente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Por que se cayo una oportunidad. La transicion a 'N' ya queda en
 * historial_estado; esto guarda la razon TIPIFICADA, que es lo que permite
 * analizar despues si el problema es precio, ubicacion o el contrato.
 *
 * <p><b>El vocabulario y sus rotulos viven AQUI, y solo aqui</b> (WP-A). Hasta
 * V90 estaban escritos cinco veces en el backend —{@code OportunidadServiceImpl},
 * {@code VisitaServiceImpl}, {@code ReportePropietarioServiceImpl},
 * {@code Descripciones} y {@code Vocabulario}— mas una sexta en el SPA. No
 * divergian por descuido: divergian en cuanto alguien anadiera un codigo, que
 * es exactamente lo que hizo falta para poder registrar por que se cae una
 * VENTA. Una razon nueva en el CHECK y en cuatro de los cinco mapas se lee como
 * {@code null} en el informe al propietario y como la letra cruda en la
 * pantalla.
 */
@Entity
@Table(name = "motivo_no_continuidad")
public class MotivoNoContinuidad extends EntidadDeOrganizacion {

    /**
     * <b>El vocabulario completo, codigo &rarr; rotulo.</b> El orden es el de
     * lectura: primero las siete que nacieron con el alquiler (V7), despues las
     * cuatro que una VENTA necesita y aquellas no sabian decir (V90).
     *
     * <p>Las cuatro nuevas no son sinonimos de ninguna anterior:
     * <ul>
     *   <li>{@code F} el banco no aprobo el credito — no es «precio»: el precio
     *       estaba acordado;</li>
     *   <li>{@code T} el propietario retiro el inmueble — la caida no viene del
     *       lado del interesado;</li>
     *   <li>{@code D} no se resolvio la documentacion (cargas, saneamiento,
     *       partida) — no es «condiciones del contrato»;</li>
     *   <li>{@code S} el interesado desistio — <b>distinto</b> de {@code N}
     *       «no responde»: uno decidio que no, del otro no se sabe nada.</li>
     * </ul>
     */
    private static final Map<String, String> ROTULOS = rotulos(
            "P", "Precio",
            "U", "Ubicacion",
            "C", "Condiciones del contrato",
            "L", "Local no adecuado",
            "N", "Cliente no responde",
            "E", "Encontro otra opcion",
            "F", "No consiguio financiamiento",
            "T", "El propietario retiro el inmueble",
            "D", "No se resolvio la documentacion",
            "S", "El interesado desistio",
            "O", "Otro");

    /** Las razones admitidas. Es el mismo conjunto que el CHECK ck_motivo_razon. */
    public static final Set<String> RAZONES = ROTULOS.keySet();

    /**
     * El rotulo de una razon, o el propio codigo si no esta en el vocabulario.
     * Devolver el codigo y no {@code null} es deliberado: una razon historica
     * que se retirara del catalogo seguiria leyendose como algo, en vez de
     * dejar la casilla vacia en el informe del propietario.
     */
    public static String rotuloDe(String codigo) {
        return ROTULOS.getOrDefault(codigo == null ? "" : codigo, codigo);
    }

    private static Map<String, String> rotulos(String... pares) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(pares[i], pares[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(mapa);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_motivo")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oportunidad", nullable = false)
    private OportunidadComercial oportunidad;

    @Column(name = "razon_principal", nullable = false, length = 1)
    private String razonPrincipal;

    @Column(name = "observaciones")
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    public Long getId() {
        return id;
    }

    public OportunidadComercial getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(OportunidadComercial oportunidad) {
        this.oportunidad = oportunidad;
    }

    public String getRazonPrincipal() {
        return razonPrincipal;
    }

    public void setRazonPrincipal(String razonPrincipal) {
        this.razonPrincipal = razonPrincipal;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
