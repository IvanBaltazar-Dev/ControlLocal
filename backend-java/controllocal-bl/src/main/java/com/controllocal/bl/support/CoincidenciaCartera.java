package com.controllocal.bl.support;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.enums.TipoInmuebleComercial;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.TipoInmueble;

/**
 * Logica pura de coincidencia de cartera (Etapa 8): evalua un requerimiento de cliente
 * contra un local comercial y entrega un puntaje (0..100 = % de criterios aplicables
 * cumplidos) con el detalle de por que coincide y que criterio no se cumple.
 *
 * <p>Sin estado ni acceso a datos. La reutilizan la capa REST (panel de coincidencias en
 * las fichas) y la bandeja del agente (tareas PROPONER_OPORTUNIDAD), para no duplicar las
 * reglas de matching.</p>
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

    /** Evalua un requerimiento contra un local; null-safe (datos faltantes = criterio no aplicable). */
    public static Evaluacion evaluar(RequerimientoCliente r, LocalComercial local) {
        if (r == null || local == null) {
            return new Evaluacion(0, List.of(), List.of());
        }
        List<Criterio> criterios = List.of(
                evalDistrito(r, local),
                evalRubro(r, local),
                evalTipo(r, local),
                evalRenta(r, local),
                evalArea(r, local),
                evalFrente(r, local));
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

    private static Criterio evalDistrito(RequerimientoCliente r, LocalComercial local) {
        List<String> distritos = r.getDistritos() == null ? List.of()
                : r.getDistritos().stream().map(Distrito::getNombre).filter(Objects::nonNull).toList();
        if (distritos.isEmpty()) {
            return NA;
        }
        String localDist = local.getDistrito();
        if (localDist == null || localDist.isBlank()) {
            return new Criterio(Resultado.NO_CUMPLE, "Distrito: el local no tiene distrito registrado");
        }
        boolean ok = distritos.stream().anyMatch(d -> norm(d).equals(norm(localDist)));
        return ok
                ? new Criterio(Resultado.CUMPLE, "Distrito: " + localDist)
                : new Criterio(Resultado.NO_CUMPLE,
                        "Distrito: busca " + String.join(", ", distritos) + ", local en " + localDist);
    }

    private static Criterio evalRubro(RequerimientoCliente r, LocalComercial local) {
        String req = r.getRubro();
        if (req == null || req.isBlank()) {
            return NA;
        }
        String loc = local.getRubroPermitido();
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

    private static Criterio evalTipo(RequerimientoCliente r, LocalComercial local) {
        TipoInmuebleComercial req = r.getTipoInmueble();
        TipoInmueble loc = local.getTipoInmueble();
        if (req == null || loc == null) {
            return NA;
        }
        TipoInmueble esperado = mapTipo(req);
        if (esperado == null) {
            return NA; // sin equivalencia 1:1 (deposito/stand/otro)
        }
        return esperado == loc
                ? new Criterio(Resultado.CUMPLE, "Tipo: " + loc.getDescripcion())
                : new Criterio(Resultado.NO_CUMPLE,
                        "Tipo: busca " + req.getDescripcion() + ", local es " + loc.getDescripcion());
    }

    private static Criterio evalRenta(RequerimientoCliente r, LocalComercial local) {
        BigDecimal min = r.getRentaMin();
        BigDecimal max = r.getRentaMax();
        BigDecimal precio = local.getPrecioReferencial();
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

    private static Criterio evalArea(RequerimientoCliente r, LocalComercial local) {
        BigDecimal min = r.getMetrajeMin();
        BigDecimal max = r.getMetrajeMax();
        BigDecimal m = local.getMetraje();
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

    private static Criterio evalFrente(RequerimientoCliente r, LocalComercial local) {
        BigDecimal fmin = r.getFrenteMinimo();
        BigDecimal f = local.getFrente();
        if (fmin == null || f == null) {
            return NA;
        }
        return f.compareTo(fmin) >= 0
                ? new Criterio(Resultado.CUMPLE, "Frente " + plain(f) + " m (>= " + plain(fmin) + ")")
                : new Criterio(Resultado.NO_CUMPLE, "Frente " + plain(f) + " m, requiere >= " + plain(fmin));
    }

    public static TipoInmueble mapTipo(TipoInmuebleComercial t) {
        return switch (t) {
            case LOCAL_COMERCIAL -> TipoInmueble.LOCAL;
            case OFICINA -> TipoInmueble.OFICINA;
            case TERRENO_COMERCIAL -> TipoInmueble.TERRENO;
            case DEPOSITO_ALMACEN, STAND_MODULO, OTRO -> null;
        };
    }

    private static String norm(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String plain(BigDecimal valor) {
        return valor == null ? "-" : valor.stripTrailingZeros().toPlainString();
    }

    private static String rango(BigDecimal min, BigDecimal max) {
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
