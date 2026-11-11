package com.controllocal.web.dto;

import com.controllocal.service.CierreVentaService;
import com.controllocal.service.OfertaService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El cable de la negociacion y del cierre de venta (WP-A).
 *
 * <p>Todos los DTO son {@code record} campo a campo: la entidad no se serializa
 * nunca. Y todo codigo viaja con su rotulo —{@code estado}/{@code estadoRotulo},
 * {@code presentadaPor}/{@code presentadaPorRotulo}— porque el rotulo lo produce
 * el Core: dos consumidores —BROX Web y KAIROS— traduciendo por su cuenta serian
 * dos traducciones que se separan (D-A-1 §6).
 *
 * <p><b>Jackson va {@code NON_NULL}</b>: un campo nulo NO viaja. En Angular
 * llega como {@code undefined}, no como {@code null}, asi que las lecturas
 * opcionales se declaran {@code campo?: T | null} y se comparan con
 * {@code == null}.
 */
public final class OfertaDtos {

    private OfertaDtos() {
    }

    /**
     * Contra que precio se ofrecio, congelado al presentar (D-N-1).
     *
     * <p><b>Que llegue ausente es informacion</b>: significa que el encargo no
     * tenia ni hito publicado ni autorizado cuando se presento la oferta, y la
     * referencia es FALTANTE. No significa cero ni «igual al importe».
     */
    public record ReferenciaResponse(String hito, String hitoRotulo, Long idPrecio,
                                     BigDecimal monto, String moneda) {

        static ReferenciaResponse desde(OfertaService.ReferenciaDeLaOferta r) {
            return r == null ? null : new ReferenciaResponse(r.hito(), r.hitoRotulo(),
                    r.idPrecio(), r.monto(), r.moneda());
        }
    }

    public record OfertaResponse(Long id, Long idOportunidad, Long idCaptacion, Integer ronda,
                                 String presentadaPor, String presentadaPorRotulo,
                                 BigDecimal monto, String moneda,
                                 String estado, String estadoRotulo,
                                 String condiciones, LocalDate fecha,
                                 Long idRolRegistra, String origen,
                                 ReferenciaResponse referencia,
                                 LocalDateTime fechaRegistro,long version,long versionMesa,
                                 OfertaService.ParteReal parteProponente,OfertaService.ObjetoExacto objeto,
                                 OfertaService.Dinero importeDeclarado,Long ofertaAnterior) {

        public static OfertaResponse desde(OfertaService.FichaOferta f) {
            return new OfertaResponse(f.id(), f.idOportunidad(), f.idCaptacion(), f.ronda(),
                    f.presentadaPor(), f.presentadaPorRotulo(), f.monto(), f.moneda(),
                    f.estado(), f.estadoRotulo(), f.condiciones(), f.fecha(),
                    f.idRolRegistra(), f.origen(), ReferenciaResponse.desde(f.referencia()),
                    f.fechaRegistro(),f.version(),f.versionMesa(),f.parteProponente(),f.objeto(),f.importeDeclarado(),f.ofertaAnterior());
        }

        public static List<OfertaResponse> desde(List<OfertaService.FichaOferta> fichas) {
            return fichas == null ? null : fichas.stream().map(OfertaResponse::desde).toList();
        }
    }

    /**
     * Lo que el actor puede hacer sobre esta mesa, decidido en el Core.
     *
     * <p>La pantalla <b>no decide por rol</b>: cada bandera sale del mismo
     * predicado que despues deniega el comando, asi que un boton activo que el
     * backend rechaza no puede ocurrir por discrepancia de criterios.
     */
    public record CapacidadesMesaResponse(boolean puedeOfertar, boolean puedeContraofertar,
                                          boolean puedeAceptar, boolean puedeRechazar,
                                          boolean puedeRetirar,
                                          boolean puedeRegistrarCaida,long versionMesa) {

        public static CapacidadesMesaResponse desde(OfertaService.CapacidadesDeLaMesa c) {
            return c == null ? null : new CapacidadesMesaResponse(c.puedeOfertar(),
                    c.puedeContraofertar(), c.puedeAceptar(), c.puedeRechazar(), c.puedeRetirar(),
                    c.puedeRegistrarCaida(),c.versionMesa());
        }
    }

    public record CierreVentaResponse(Long id, Long idCaptacion, String codigoCaptacion,
                                      Long idOportunidad, String codigoOportunidad, Long idOferta,
                                      Integer rondaOferta, Long idPropiedad,
                                      String direccionPropiedad, Long idRolCliente,
                                      String clienteNombre, Long idRolAgenteCierre,
                                      Long idRolBrokerCierre, BigDecimal montoPactado,
                                      String moneda, BigDecimal montoDeclarado,
                                      String monedaDeclarada, LocalDate fechaFirma,
                                      String observaciones, LocalDateTime fechaRegistro) {

        public static CierreVentaResponse desde(CierreVentaService.FichaCierreVenta f) {
            return new CierreVentaResponse(f.id(), f.idCaptacion(), f.codigoCaptacion(),
                    f.idOportunidad(), f.codigoOportunidad(), f.idOferta(), f.rondaOferta(),
                    f.idPropiedad(), f.direccionPropiedad(), f.idRolCliente(), f.clienteNombre(),
                    f.idRolAgenteCierre(), f.idRolBrokerCierre(), f.montoPactado(), f.moneda(),
                    f.montoDeclarado(), f.monedaDeclarada(), f.fechaFirma(), f.observaciones(),
                    f.fechaRegistro());
        }
    }
}
