package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.DetalleLocalComercial;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.Propiedad;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Logica pura de coincidencia de cartera, portada TAL CUAL desde
 * {@code bl/support/CoincidenciaCartera} de la v1 (contrato F3 §7): evalua un
 * requerimiento de cliente contra una propiedad y entrega un puntaje
 * (0..100 = % de criterios APLICABLES cumplidos) con el detalle de por que
 * coincide y que criterio no cumple.
 *
 * <p>Sin estado ni acceso a datos: la reutilizan las tres rutas de
 * coincidencias (cliente→propiedades, captacion→clientes,
 * prospeccion→clientes) y, mas adelante, la bandeja de tareas
 * PROPONER_OPORTUNIDAD (F6).
 *
 * <p>Los seis criterios y sus frases son parte del cable: el frontend muestra
 * las listas {@code cumple}/{@code noCumple} literales. Un dato faltante
 * —en el requerimiento o en la propiedad— hace que el criterio NO APLIQUE, y
 * por eso no castiga el puntaje.
 */
public final class CoincidenciaCartera {

    private CoincidenciaCartera() {
    }

    public record Evaluacion(int puntaje, List<String> cumple, List<String> noCumple) {
    }

    private enum Resultado { CUMPLE, NO_CUMPLE, NO_APLICA }

    private record Criterio(Resultado res, String detalle) {
    }

    private static final Criterio NA = new Criterio(Resultado.NO_APLICA, "");

    /**
     * Evalua un requerimiento contra una propiedad; null-safe (dato faltante =
     * criterio no aplicable).
     *
     * <p><b>{@code valores} no es opcional aunque el parametro admita vacio.</b>
     * `frente` es un atributo gobernado (D-E4-3) y su columna espejo desaparece
     * en el paso 9. Pasar {@link ValoresDePropiedad#vacio()} no rompe nada
     * visible: simplemente convierte el criterio del frente en NO APLICA, y con
     * ello altera el puntaje sin avisar. Ese es el fallo silencioso que este
     * parametro obliga a resolver en cada llamada.
     */
    public static Evaluacion evaluar(RequerimientoCliente r, Propiedad propiedad,
                                     ValoresDePropiedad valores) {
        if (r == null || propiedad == null) {
            return new Evaluacion(0, List.of(), List.of());
        }
        List<Criterio> criterios = List.of(
                evalDistrito(r, propiedad),
                evalRubro(r, propiedad),
                evalTipo(r, propiedad),
                evalRenta(r, propiedad),
                evalArea(r, propiedad),
                evalFrente(r, valores));
        List<String> cumple = new ArrayList<>();
        List<String> noCumple = new ArrayList<>();
        int aplicables = 0;
        int cumplidos = 0;
        for (Criterio c : criterios) {
            if (c.res() == Resultado.NO_APLICA) {
                continue;
            }
            aplicables++;
            if (c.res() == Resultado.CUMPLE) {
                cumplidos++;
                cumple.add(c.detalle());
            } else {
                noCumple.add(c.detalle());
            }
        }
        int puntaje = aplicables == 0 ? 0 : (int) Math.round(100.0 * cumplidos / aplicables);
        return new Evaluacion(puntaje, cumple, noCumple);
    }

    private static Criterio evalDistrito(RequerimientoCliente r, Propiedad propiedad) {
        List<String> distritos = r.getDistritos() == null ? List.of()
                : r.getDistritos().stream().map(Distrito::getNombre).filter(Objects::nonNull).toList();
        if (distritos.isEmpty()) {
            return NA;
        }
        String localDist = propiedad.getDistrito();
        if (localDist == null || localDist.isBlank()) {
            return new Criterio(Resultado.NO_CUMPLE, "Distrito: el local no tiene distrito registrado");
        }
        boolean ok = distritos.stream().anyMatch(d -> norm(d).equals(norm(localDist)));
        return ok
                ? new Criterio(Resultado.CUMPLE, "Distrito: " + localDist)
                : new Criterio(Resultado.NO_CUMPLE,
                        "Distrito: busca " + String.join(", ", distritos) + ", local en " + localDist);
    }

    /** El rubro coincide por inclusion en CUALQUIER direccion ("Cafeteria" ~ "Cafeteria y panaderia"). */
    private static Criterio evalRubro(RequerimientoCliente r, Propiedad propiedad) {
        String req = r.getRubro();
        if (req == null || req.isBlank()) {
            return NA;
        }
        String loc = rubroPermitido(propiedad);
        if (loc == null || loc.isBlank()) {
            return new Criterio(Resultado.NO_CUMPLE, "Rubro: el local no especifica rubro permitido");
        }
        String a = norm(req);
        String b = norm(loc);
        boolean ok = a.equals(b) || a.contains(b) || b.contains(a);
        return ok
                ? new Criterio(Resultado.CUMPLE, "Rubro: " + req)
                : new Criterio(Resultado.NO_CUMPLE, "Rubro: busca " + req + ", local permite " + loc);
    }

    private static Criterio evalTipo(RequerimientoCliente r, Propiedad propiedad) {
        String req = r.getTipoInmueble();
        String loc = propiedad.getTipoInmueble();
        if (req == null || loc == null) {
            return NA;
        }
        String esperado = mapTipo(req);
        if (esperado == null) {
            return NA; // sin equivalencia 1:1 (deposito/stand/otro)
        }
        return esperado.equals(loc)
                ? new Criterio(Resultado.CUMPLE, "Tipo: " + descripcionTipoPropiedad(loc))
                : new Criterio(Resultado.NO_CUMPLE,
                        "Tipo: busca " + descripcionTipoRequerimiento(req)
                                + ", local es " + descripcionTipoPropiedad(loc));
    }

    private static Criterio evalRenta(RequerimientoCliente r, Propiedad propiedad) {
        BigDecimal min = r.getRentaMin();
        BigDecimal max = r.getRentaMax();
        BigDecimal precio = propiedad.getPrecioReferencial();
        if ((min == null && max == null) || precio == null) {
            return NA;
        }
        boolean ok = (min == null || precio.compareTo(min) >= 0)
                && (max == null || precio.compareTo(max) <= 0);
        String rango = rango(min, max);
        return ok
                ? new Criterio(Resultado.CUMPLE, "Renta " + plain(precio) + " dentro de " + rango)
                : new Criterio(Resultado.NO_CUMPLE, "Renta " + plain(precio) + " fuera de rango (" + rango + ")");
    }

    private static Criterio evalArea(RequerimientoCliente r, Propiedad propiedad) {
        BigDecimal min = r.getMetrajeMin();
        BigDecimal max = r.getMetrajeMax();
        BigDecimal m = propiedad.getMetraje();
        if ((min == null && max == null) || m == null) {
            return NA;
        }
        boolean ok = (min == null || m.compareTo(min) >= 0)
                && (max == null || m.compareTo(max) <= 0);
        String rango = rango(min, max) + " m2";
        return ok
                ? new Criterio(Resultado.CUMPLE, "Area " + plain(m) + " m2 dentro de " + rango)
                : new Criterio(Resultado.NO_CUMPLE, "Area " + plain(m) + " m2 fuera de rango (" + rango + ")");
    }

    /** El frente sale de su autoridad, no de la columna espejo (D-E4-3). */
    private static Criterio evalFrente(RequerimientoCliente r, ValoresDePropiedad valores) {
        BigDecimal fmin = r.getFrenteMinimo();
        BigDecimal f = valores.decimal(CatalogoAtributo.CLAVE_FRENTE);
        if (fmin == null || f == null) {
            return NA;
        }
        return f.compareTo(fmin) >= 0
                ? new Criterio(Resultado.CUMPLE, "Frente " + plain(f) + " m (>= " + plain(fmin) + ")")
                : new Criterio(Resultado.NO_CUMPLE, "Frente " + plain(f) + " m, requiere >= " + plain(fmin));
    }

    /**
     * Equivalencia 1:1 entre el tipo que pide el cliente (nombre del enum,
     * §1 del contrato) y el codigo de 1 caracter de la propiedad. Sin
     * equivalencia (deposito, stand, otro) el criterio NO APLICA.
     */
    public static String mapTipo(String tipoRequerimiento) {
        return switch (tipoRequerimiento) {
            case "LOCAL_COMERCIAL" -> Propiedad.TIPO_LOCAL;
            case "OFICINA" -> Propiedad.TIPO_OFICINA;
            case "TERRENO_COMERCIAL" -> "T";
            default -> null;
        };
    }

    private static String rubroPermitido(Propiedad propiedad) {
        DetalleLocalComercial detalle = propiedad.getDetalleLocal();
        return detalle != null ? detalle.getRubroPermitido() : null;
    }

    /** Descripciones del enum TipoInmueble v1 (las frases viajan en el cable). */
    private static String descripcionTipoPropiedad(String codigo) {
        return switch (codigo) {
            case "L" -> "Local";
            case "O" -> "Oficina";
            case "D" -> "Departamento";
            case "C" -> "Casa";
            case "T" -> "Terreno";
            default -> "Otro";
        };
    }

    /** Descripcion del enum TipoInmuebleComercial v1: el nombre con espacios. */
    private static String descripcionTipoRequerimiento(String nombre) {
        return nombre.replace('_', ' ');
    }

    /** Sin acentos y en minusculas: "San Isidro" casa con "san isidro". */
    private static String norm(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public static String plain(BigDecimal valor) {
        return valor == null ? "-" : valor.stripTrailingZeros().toPlainString();
    }

    public static String rango(BigDecimal min, BigDecimal max) {
        if (min != null && max != null) {
            return plain(min) + " - " + plain(max);
        }
        if (min != null) {
            return "desde " + plain(min);
        }
        if (max != null) {
            return "hasta " + plain(max);
        }
        return "sin limite";
    }
}
