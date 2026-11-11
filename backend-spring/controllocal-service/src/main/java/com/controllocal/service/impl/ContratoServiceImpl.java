package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.RevisionDisponibilidad;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoContrato;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.publicacion.HistoriadorPublicacion;
import com.controllocal.persistence.repositorio.RevisionDisponibilidadRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.ComisionService;
import com.controllocal.service.TareaService;
import com.controllocal.service.ComisionService.FichaComision;
import com.controllocal.service.ContratoService;
import com.controllocal.service.Pagina;
import com.controllocal.service.PreparacionesDeContrato;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.formalizacion.FormalizacionService.AcuerdoFinalDocumentado;
import com.controllocal.service.soporte.AccesoContrato;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.MesaNegociacionRepository;
import com.controllocal.domain.comercial.TipoHitoContractual;
import jakarta.persistence.EntityManager;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.*;
import java.util.function.Supplier;
import java.util.Objects;
import java.util.Set;

/** RF058/059: preparación privada y resultado del hito confirmado, sin producir dinero. */
@Service
public class ContratoServiceImpl implements ContratoService {

    /** Estados de la oportunidad que todavia admiten un contrato (§6). */
    private static final Set<String> OPORTUNIDAD_ABIERTA_AL_CONTRATO = Set.of(
            OportunidadComercial.ABIERTA, OportunidadComercial.SOLICITUD_CREADA);

    private final ContratoAlquilerRepository contratos;
    private final SolicitudAlquilerRepository solicitudes;
    private final OportunidadComercialRepository oportunidades;
    private final CaptacionRepository captaciones;
    private final PropiedadRepository propiedades;
    private final PrecioPropiedadRepository precios;
    private final HistoriadorPublicacion publicaciones;
    private final ComisionService comisiones;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final TareaService tareas;
    private final AlertaService alertas;
    private final SupervisionAgenteRepository supervisiones;
    private final RevisionDisponibilidadRepository revisiones;
    private final AccesoContrato accesoContratos;
    private final PreparacionesDeContrato preparaciones;
    private final ContextosDeActividad contextos;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository recibos;
    private final Documentos documentos;
    private final EntityManager entidades;
    private final AccesoEncargo accesoEncargos;
    private final MesaNegociacionRepository mesas;

    public ContratoServiceImpl(ContratoAlquilerRepository contratos,
                               SolicitudAlquilerRepository solicitudes,
                               OportunidadComercialRepository oportunidades,
                               CaptacionRepository captaciones, PropiedadRepository propiedades,
                               PrecioPropiedadRepository precios, HistoriadorPublicacion publicaciones,
                               ComisionService comisiones, Alcances alcances,
                               Transiciones transiciones, TareaService tareas,
                               AlertaService alertas, SupervisionAgenteRepository supervisiones,
                               RevisionDisponibilidadRepository revisiones,
                               AccesoContrato accesoContratos,PreparacionesDeContrato preparaciones,
                               ContextosDeActividad contextos,ComandosIdempotentes comandos,ComandoIdempotenteRepository recibos,
                               Documentos documentos,EntityManager entidades,AccesoEncargo accesoEncargos,MesaNegociacionRepository mesas) {
        this.accesoContratos=accesoContratos;this.preparaciones=preparaciones;this.contextos=contextos;
        this.comandos=comandos;this.recibos=recibos;this.documentos=documentos;this.entidades=entidades;
        this.accesoEncargos=accesoEncargos;this.mesas=mesas;
        this.revisiones = revisiones;
        this.tareas = tareas;
        this.alertas = alertas;
        this.supervisiones = supervisiones;
        this.contratos = contratos;
        this.solicitudes = solicitudes;
        this.oportunidades = oportunidades;
        this.captaciones = captaciones;
        this.propiedades = propiedades;
        this.precios = precios;
        this.publicaciones = publicaciones;
        this.comisiones = comisiones;
        this.alcances = alcances;
        this.transiciones = transiciones;
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public long confirmarHito(ContextoEconomico ctx,FuenteHitoConfirmado hito,FuenteEconomicaFinal fuente,
                               AcuerdoFinalDocumentado revisado,Actor actor,PreparacionContrato preparacionRevisada) {
        if(ctx==null||hito==null||fuente==null||revisado==null||hito.fechaEfectiva()==null)
            throw new ReglaNegocioException("La confirmación conserva hito, fecha efectiva y fuente económica exacta.");
        if(ctx.organizacion()!=actor.idOrganizacion()||ctx.operacion()!=OperacionInmobiliaria.ALQUILER
                ||hito.tipo()!=TipoHitoContractual.CONTRATO_ALQUILER||!Objects.equals(ctx.referencia(),hito.contexto())
                ||!Objects.equals(ctx.pacto(),hito.pacto())||!Objects.equals(fuente.referencia(),hito.fuenteEconomica()))
            throw new ConflictoException("El hito no pertenece a ese contexto económico de alquiler.");
        if(ctx.oportunidad()==null)throw new ReglaNegocioException("La confirmación de este alquiler necesita su mesa real acreditada.");
        var encargo=accesoEncargos.leer(ctx.encargo(),actor);accesoEncargos.exigirDecision(encargo,actor);
        var oportunidad=oportunidades.buscarFicha(actor.idOrganizacion(),ctx.oportunidad()).orElseThrow(()->new NoEncontradoException("Oportunidad"));
        if(oportunidad.getCaptacion()==null||!Objects.equals(oportunidad.getCaptacion().getId(),ctx.encargo()))
            throw new ConflictoException("La oportunidad no pertenece al Encargo confirmado.");
        if(fuente.rentaMensual()==null||fuente.rentaMensual().importe()==null||fuente.rentaMensual().importe().signum()<=0)
            throw new ReglaNegocioException("Conserva la renta mensual final acreditada para el objeto contratado.");
        String moneda=CondicionesEconomicas.moneda(fuente.rentaMensual().moneda(),"de la renta mensual final");
        if(fuente.objeto()==null||!Objects.equals(fuente.objeto().encargo(),ctx.encargo())
                ||fuente.objeto().unidades()==null||fuente.objeto().unidades().isEmpty())
            throw new ReglaNegocioException("Conserva la composición exacta cubierta por el hito.");
        var terminos=revisado.terminosAlquiler();
        LocalDate inicio=terminos==null?(preparacionRevisada==null?null:preparacionRevisada.inicio()):terminos.inicio();
        LocalDate fin=terminos==null?(preparacionRevisada==null?null:preparacionRevisada.fin()):terminos.fin();
        if(inicio==null||fin==null||!fin.isAfter(inicio))
            throw new ReglaNegocioException("El contrato confirmado necesita sus fechas inicial y final acreditadas; no se inventan desde la firma.");
        SolicitudAlquiler solicitud=null;
        if(ctx.solicitudAlquiler()!=null) {
            solicitud=solicitudes.buscarFicha(actor.idOrganizacion(),ctx.solicitudAlquiler()).orElseThrow(()->new NoEncontradoException("Solicitud"));
            if(solicitud.getOportunidad()==null||!Objects.equals(solicitud.getOportunidad().getId(),oportunidad.getId()))
                throw new ConflictoException("La solicitud no pertenece a la mesa del hito.");
        }
        ContratoAlquiler contrato;ContratoAlquiler anterior=null;
        if(ctx.contratoAlquiler()!=null) {
            var meta=preparaciones.consultar(ctx.contratoAlquiler(),actor);
            SortedSet<Long> ids=new TreeSet<>();ids.add(meta.id());if(meta.contratoAnterior()!=null)ids.add(meta.contratoAnterior());
            Map<Long,ContratoAlquiler> bloqueados=new HashMap<>();
            for(long id:ids) {
                var c=contratos.bloquear(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Contrato"));
                entidades.refresh(c,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);bloqueados.put(id,c);
            }
            contrato=bloqueados.get(meta.id());anterior=meta.contratoAnterior()==null?null:bloqueados.get(meta.contratoAnterior());
            var actual=preparaciones.consultar(meta.id(),actor);
            if(actual.encargo()!=ctx.encargo()||actual.oportunidad()!=ctx.oportunidad()
                    ||!Objects.equals(actual.solicitud(),ctx.solicitudAlquiler()))throw new ConflictoException("Cambió el contexto del contrato preparado.");
            if(hito.rectifica()==null) {
                if(preparacionRevisada==null||preparacionRevisada.id()!=actual.id()
                        ||!Objects.equals(preparacionRevisada.huella(),actual.huella())||!EstadoContrato.EN_PROCESO.codigo().equals(actual.estado()))
                    throw new ConflictoException("La confirmación no cubre la preparación exacta revisada.");
            } else if(EstadoContrato.EN_PROCESO.codigo().equals(actual.estado()))
                throw new ConflictoException("Una rectificación no confirma por primera vez una preparación.");
        } else {
            if(hito.rectifica()!=null)throw new ConflictoException("Falta el contrato original de la rectificación.");
            if(preparacionRevisada!=null)throw new ConflictoException("El contexto no identifica el contrato preparado revisado.");
            if(contratos.existeDeOportunidad(actor.idOrganizacion(),oportunidad.getId()))throw new ConflictoException("La mesa ya conserva un contrato; identifica su contexto original.");
            contrato=new ContratoAlquiler();contrato.setOrganizacionId(actor.idOrganizacion());contrato.setOportunidad(oportunidad);contrato.setSolicitud(solicitud);
        }
        boolean rectificacion=hito.rectifica()!=null;
        if(!rectificacion&&anterior!=null) {
            accesoContratos.exigir(anterior,actor);
            if(anterior.getOportunidad()==null||!Objects.equals(anterior.getOportunidad().getId(),ctx.oportunidad()))
                throw new ConflictoException("El contrato anterior no pertenece a la mesa de esta renovación.");
            if(!EstadoContrato.VIGENTE.codigo().equals(anterior.estadoActual()))throw new ConflictoException("El contrato anterior ya no está vigente.");
            transiciones.aplicar(anterior,anterior.getId(),EstadoContrato.RENOVADO.codigo(),actor,"Renovación contractual confirmada",hito.fechaEfectiva());
            anterior.setFechaEfectivaEstado(hito.fechaEfectiva());contratos.saveAndFlush(anterior);
        }
        contrato.setFechaCierre(hito.fechaEfectiva());contrato.setFechaInicioContrato(inicio);contrato.setFechaFinContrato(fin);
        contrato.setRentaContractual(fuente.rentaMensual().importe());contrato.setMoneda(moneda);
        if(!rectificacion) {
            Long profesional=contrato.getIdRolAgenteCierre()!=null?contrato.getIdRolAgenteCierre():solicitud!=null&&solicitud.getAgente()!=null?solicitud.getAgente().getId():encargo.getAgente().getId();
            contrato.atribuir(profesional,actor.esBroker()?actor.idRolOperativo():null,encargo.getId(),encargo.getPropiedad().getId(),
                    oportunidad.getCliente()==null?null:oportunidad.getCliente().getId());
            if(contrato.getId()==null)transiciones.iniciar(contrato,EstadoContrato.FIRMADO.codigo());
            else transiciones.aplicar(contrato,contrato.getId(),EstadoContrato.FIRMADO.codigo(),actor,"Hito contractual confirmado",hito.fechaEfectiva());
            contrato.setFechaEfectivaEstado(hito.fechaEfectiva());
        }
        contrato=contratos.saveAndFlush(contrato);
        if(!rectificacion)cerrarResultadoConfirmado(encargo,oportunidad,solicitud,anterior!=null,fuente,hito.fechaEfectiva(),hito.referencia().revision(),actor);
        // Formalización guarda este ID y evalúa comisión después; no existe un segundo productor monetario aquí.
        return contrato.getId();
    }

    private void cerrarResultadoConfirmado(Captacion encargo,OportunidadComercial oportunidad,SolicitudAlquiler solicitud,
            boolean renovacion,FuenteEconomicaFinal fuente,LocalDate fecha,long revisionHito,Actor actor) {
        String motivo=renovacion?"Renovación de alquiler confirmada":"Contrato de alquiler confirmado";
        if(!renovacion) {
            if(!OPORTUNIDAD_ABIERTA_AL_CONTRATO.contains(oportunidad.estadoActual()))throw new ReglaNegocioException("La mesa ya tiene un resultado; conserva su episodio legítimo.");
            oportunidad.marcarCierreExitoso();transiciones.aplicar(oportunidad,oportunidad.getId(),OportunidadComercial.FINALIZADA_EXITOSA,actor,motivo,fecha,revisionHito);oportunidades.save(oportunidad);
            if(solicitud!=null&&!SolicitudAlquiler.CERRADA.equals(solicitud.estadoActual())) {
                transiciones.aplicar(solicitud,solicitud.getId(),SolicitudAlquiler.CERRADA,actor,motivo,fecha);solicitudes.save(solicitud);
            }
            if(Captacion.esVivo(encargo.estadoActual())) {
                transiciones.aplicar(encargo,encargo.getId(),Captacion.FINALIZADO,actor,motivo,fecha);encargo.cerrar(fecha,"A",motivo);captaciones.save(encargo);
            }
            tareas.resolverPorCierreConfirmado(revisionHito,actor);
        }
        cerrarComposicionConfirmada(encargo,fuente,fecha,actor,motivo);
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.OPORTUNIDAD_CERRADA,Alerta.INFO,"OPORTUNIDAD",oportunidad.getId(),
                encargo.getAgente().getId(),motivo+" de la oportunidad "+oportunidad.getCodigoOportunidad()+".",
                com.controllocal.service.soporte.FuentesAsuntos.Origen.de("REVISION_HITO",revisionHito)),actor);
    }
    private void cerrarComposicionConfirmada(Captacion encargo,FuenteEconomicaFinal fuente,LocalDate fecha,Actor actor,String motivo) {
        var ids=new TreeSet<>(fuente.objeto().unidades());
        if(ids.size()!=fuente.objeto().unidades().size())throw new ReglaNegocioException("La composición no repite unidades.");
        for(long id:ids) {
            var p=propiedades.bloquearParaEscritura(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Unidad contratada"));
            entidades.refresh(p,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            transiciones.aplicarDisponibilidad(p,id,DisponibilidadComercial.ALQUILADO,actor,motivo);propiedades.save(p);
        }
        if(fuente.objeto().tipo()==com.controllocal.service.OfertaService.TipoObjeto.UNIDAD) {
            if(ids.size()!=1||!Objects.equals(fuente.objeto().propiedad(),ids.first()))throw new ConflictoException("La renta individual no identifica la unidad exacta.");
            var precio=new PrecioPropiedad();precio.setOrganizacionId(actor.idOrganizacion());precio.setIdPropiedad(ids.first());
            precio.setOperacion(OperacionInmobiliaria.ALQUILER);precio.setHito(PrecioPropiedad.HITO_CIERRE);
            precio.setMonto(fuente.rentaMensual().importe());precio.setMoneda(fuente.rentaMensual().moneda());precio.setFecha(fecha);precio.delEncargo(encargo.getId());
            if(fuente.acuerdoC4()!=null) {
                if(fuente.versionAcuerdoC4()==null)throw new IllegalStateException("Falta la versión del acuerdo del cierre.");
                var acuerdo=mesas.acuerdoExacto(actor.idOrganizacion(),fuente.acuerdoC4(),fuente.versionAcuerdoC4())
                        .orElseThrow(()->new IllegalStateException("Falta el acuerdo exacto del cierre."));
                precio.deLaOferta(acuerdo.oferta());
            }
            precios.save(precio);
        }
        // El importe del paquete permanece en contrato/fuente: no se asigna a cada inmueble.
        publicaciones.cerrarPorHito(encargo,actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaContrato> listar(int pagina, int tamano, Actor actor) {
        return listar(new FiltrosContrato(null, null, null, null, pagina, tamano), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaContrato> listar(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            // Un BROKER sin captaciones supervisadas obtiene lista VACIA, no 403.
            return Pagina.vacia();
        }
        String texto = limpiar(filtros.texto());
        if (texto != null) {
            return porTexto(alcance, filtros, texto, actor);
        }
        Page<ContratoAlquiler> page = contratos.buscar(
                alcance.idOrganizacion(), alcance.global(), actor.esAgente(), alcance.paramRoles(),
                limpiar(filtros.distrito()), filtros.idAgente(),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano()),
                        orden(filtros.orden())));
        return paginaDe(page.getContent(), page.getTotalElements(), actor);
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (RC-003, §5). Una rama por
     * tabla, {@code UNION} en la base, y el MISMO conjunto para el conteo, la
     * pagina y los KPI de {@link #resumenCierres}. La proyeccion completa se
     * carga despues, solo para los ids ya resueltos.
     *
     * <p>Las dos variantes de orden comparten las mismas ramas: aqui solo se
     * elige cual de las dos se llama.
     */
    private Pagina<FichaContrato> porTexto(Alcance alcance, FiltrosContrato filtros,
                                            String texto, Actor actor) {
        String roles = alcance.paramRolesArray();
        String distrito = limpiar(filtros.distrito());
        int tamano = tamano(filtros.tamano());
        int pagina = Math.max(1, filtros.pagina());
        long total = contratos.contarPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, texto, distrito, filtros.idAgente());
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        int desplazamiento = (pagina - 1) * tamano;
        List<Long> ids = porCierre(filtros.orden())
                ? contratos.idsPorTextoPorCierre(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), roles, texto, distrito, filtros.idAgente(),
                        tamano, desplazamiento)
                : contratos.idsPorTextoPorId(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), roles, texto, distrito, filtros.idAgente(),
                        tamano, desplazamiento);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        // El `in` no conserva el orden de la lista: se repone con el de los ids,
        // que es el que decidio la base.
        Map<Long, ContratoAlquiler> porId = contratos
                .buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                .collect(java.util.stream.Collectors.toMap(ContratoAlquiler::getId, c -> c,
                        (a, b) -> a, java.util.LinkedHashMap::new));
        List<ContratoAlquiler> ordenados = ids.stream().map(porId::get)
                .filter(Objects::nonNull).toList();
        return paginaDe(ordenados, total, actor);
    }

    /** Una sola lectura de comisiones para toda la pagina, nunca N+1. */
    private Pagina<FichaContrato> paginaDe(List<ContratoAlquiler> contenido, long total,
                                            Actor actor) {
        Map<Long, FichaComision> comisionPorContrato = comisiones.porContratosAlquiler(
                contenido.stream().map(ContratoAlquiler::getId).filter(Objects::nonNull).toList(),
                actor);
        return new Pagina<>(
                contenido.stream()
                        .map(c -> ficha(c, comisionPorContrato.get(c.getId())))
                        .toList(),
                total);
    }

    private static boolean porCierre(String orden) {
        return "cierre".equalsIgnoreCase(orden);
    }

    /**
     * El orden congelado es {@code id desc}; solo la pantalla de cierres pide
     * el otro. Se decide aqui y no en la consulta para no duplicarla.
     */
    private static Sort orden(String orden) {
        return porCierre(orden)
                ? Sort.by(Sort.Direction.DESC, "fechaCierre", "id")
                : Sort.by(Sort.Direction.DESC, "id");
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenCierres resumenCierres(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return resumenVacio();
        }
        String texto=limpiar(filtros.texto());
        var filas=texto==null
                ?contratos.finanzasDeCierres(alcance.idOrganizacion(),alcance.global(),actor.esAgente(),alcance.paramRolesArray(),limpiar(filtros.distrito()),filtros.idAgente())
                :contratos.finanzasDeCierresPorTexto(alcance.idOrganizacion(),alcance.global(),actor.esAgente(),alcance.paramRolesArray(),texto,limpiar(filtros.distrito()),filtros.idAgente());
        var cabeceras=filas.stream().filter(f->"CONTADORES".equals(f.getDimension())).toList();
        if(cabeceras.size()!=1||!Boolean.FALSE.equals(cabeceras.getFirst().getAnomalia()))
            throw new IllegalStateException("La evidencia financiera del conjunto no es íntegra.");
        var c=cabeceras.getFirst();
        return new ResumenCierres(contador(c.getCierres()),contador(c.getConLecturaFinanciera()),contador(c.getSinEvaluacion()),
                importes(filas,"CALCULADO"),importes(filas,"DEVENGADO"),importes(filas,"RECIBIDO"),importes(filas,"SALDO"));
    }

    private static long contador(Long valor) {
        if(valor==null||valor<0)throw new IllegalStateException("El agregado no acredita su población.");
        return valor;
    }
    private static ResumenCierres resumenVacio() {
        return new ResumenCierres(0,0,0,List.of(),List.of(),List.of(),List.of());
    }
    private static List<ImportePorMoneda> importes(List<ContratoAlquilerRepository.FinanzasCierres> filas,String dimension) {
        return filas.stream().filter(f->dimension.equals(f.getDimension())).map(f->{
            long conocidos=contador(f.getDeterminados()),pendientes=contador(f.getPendientes());
            if(f.getMoneda()!=null&&!CondicionesEconomicas.MONEDAS.contains(f.getMoneda()))
                throw new IllegalStateException("La evidencia financiera contiene una moneda ilegible.");
            if((f.getMonto()==null)!=(conocidos==0)||(f.getMonto()!=null&&f.getMoneda()==null))
                throw new IllegalStateException("El agregado no distingue importes conocidos y pendientes.");
            return new ImportePorMoneda(f.getMoneda(),f.getMonto(),conocidos,pendientes);
        }).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<String> distritosDeCierres(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        String texto = limpiar(filtros.texto());
        return texto != null
                ? contratos.distritosDeCierresPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), texto,
                        limpiar(filtros.distrito()), filtros.idAgente())
                : contratos.distritosDeCierres(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(),
                        limpiar(filtros.distrito()), filtros.idAgente());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgenteConCierres> agentesDeCierres(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        String texto = limpiar(filtros.texto());
        return (texto != null
                ? contratos.agentesDeCierresPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), texto,
                        limpiar(filtros.distrito()), filtros.idAgente())
                : contratos.agentesDeCierres(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(),
                        limpiar(filtros.distrito()), filtros.idAgente()))
                .stream()
                .map(a -> new AgenteConCierres(a.getId(), a.getNombre()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FichaContrato obtenerPorOportunidad(long idOportunidad, Actor actor) {
        ContratoAlquiler contrato = contratos
                .buscarPorOportunidad(actor.idOrganizacion(), idOportunidad)
                .orElseThrow(() -> new NoEncontradoException("Contrato"));
        accesoContratos.exigir(contrato, actor);
        return ficha(contrato, comisiones.porContratosAlquiler(List.of(contrato.getId()),actor).get(contrato.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public FichaContrato obtener(long idContrato, Actor actor) {
        ContratoAlquiler contrato = accesoContratos.leer(idContrato, actor);
        return ficha(contrato, comisiones.porContratosAlquiler(List.of(contrato.getId()),actor).get(contrato.getId()));
    }

    // ------------------------------------------------------------------
    // §6 — la cascada del cierre
    // ------------------------------------------------------------------

    @Override @Transactional
    public FichaContrato iniciarEnProceso(DatosContrato datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia) {
        return prepararOperacion("PREPARAR_CONTRATO_ALQUILER",datos,actor,sesion,clave,procedencia,()->{
            var solicitud=solicitudAprobadaPropia(datos,actor);var oportunidad=solicitud.getOportunidad();
            if(!OPORTUNIDAD_ABIERTA_AL_CONTRATO.contains(oportunidad.estadoActual()))throw new ReglaNegocioException("La oportunidad ya está cerrada.");
            if(contratos.existeDeOportunidad(actor.idOrganizacion(),oportunidad.getId()))throw new ConflictoException("La oportunidad ya tiene un contrato preparado o confirmado.");
            var contrato=new ContratoAlquiler();contrato.setOrganizacionId(actor.idOrganizacion());contrato.setSolicitud(solicitud);contrato.setOportunidad(oportunidad);
            contrato.setIncidencias(texto(datos.incidencias()));
            completarSnapshot(contrato,solicitud,EstadoContrato.EN_PROCESO.codigo());atribuir(contrato,solicitud,oportunidad);
            transiciones.iniciar(contrato,EstadoContrato.EN_PROCESO.codigo());return contratos.saveAndFlush(contrato);
        });
    }
    @Override
    @Transactional
    public FichaContrato activar(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.VIGENTE, datos, actor, false);
    }

    @Override
    @Transactional
    public FichaContrato finalizar(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.FINALIZADO, datos, actor, true);
    }

    @Override
    @Transactional
    public FichaContrato rescindir(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.RESCINDIDO, datos, actor, true);
    }

    @Override
    @Transactional
    public FichaContrato anular(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.ANULADO, datos, actor, false);
    }

    @Override @Transactional
    public FichaContrato renovar(long idContrato,DatosRenovacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia) {
        if(datos==null)throw new ReglaNegocioException("Conserva las condiciones declaradas de la renovación.");
        Map<String,Object> entrada=new LinkedHashMap<>();entrada.put("contrato",idContrato);entrada.put("datos",datos);
        return prepararOperacion("PREPARAR_RENOVACION_ALQUILER",entrada,actor,sesion,clave,procedencia,()->{
            accesoContratos.actualAutorizada(idContrato,actor);
            var anterior=contratos.bloquear(actor.idOrganizacion(),idContrato).orElseThrow(()->new NoEncontradoException("Contrato"));
            entidades.refresh(anterior,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            accesoContratos.exigir(anterior,actor);
            if(!EstadoContrato.VIGENTE.codigo().equals(anterior.estadoActual()))throw new ReglaNegocioException("Se prepara la renovación del contrato vigente.");
            if(contratos.existsByOrganizacionIdAndContratoAnteriorId(actor.idOrganizacion(),idContrato))throw new ConflictoException("El contrato ya tiene un sucesor preparado o confirmado.");
            if(datos.fechaInicioContrato()==null||datos.fechaFinContrato()==null||!datos.fechaFinContrato().isAfter(datos.fechaInicioContrato()))
                throw new ReglaNegocioException("Conserva las fechas inicial y final declaradas de la renovación.");
            if(datos.rentaContractual()==null||datos.rentaContractual().signum()<=0)throw new ReglaNegocioException("La renta declarada debe ser mayor que cero.");
            String moneda=CondicionesEconomicas.moneda(datos.moneda(),"de la renovación");
            var nueva=new ContratoAlquiler();nueva.setOrganizacionId(actor.idOrganizacion());nueva.setOportunidad(anterior.getOportunidad());nueva.setSolicitud(anterior.getSolicitud());
            nueva.setContratoAnterior(anterior);nueva.setFechaInicioContrato(datos.fechaInicioContrato());nueva.setFechaFinContrato(datos.fechaFinContrato());
            nueva.setRentaContractual(datos.rentaContractual());nueva.setMoneda(moneda);nueva.setIncidencias(texto(datos.motivo()));
            nueva.atribuir(anterior.getIdRolAgenteCierre(),null,anterior.getIdCaptacion(),anterior.getIdPropiedad(),anterior.getIdRolCliente());
            transiciones.iniciar(nueva,EstadoContrato.EN_PROCESO.codigo());return contratos.saveAndFlush(nueva);
        });
    }
    private FichaContrato prepararOperacion(String tipo,Object entrada,Actor actor,ContextoSesion sesion,String clave,
                                             Procedencia origen,Supplier<ContratoAlquiler> efecto) {
        if(entrada==null||origen==null)throw new ReglaNegocioException("Conserva los datos y procedencia de la preparación.");
        contextos.iniciar(actor,sesion);String key=Idempotencia.exigirClave(clave);
        String identidad=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+key);
        recibos.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        Map<String,Object> dato=new LinkedHashMap<>();dato.put("tipo",tipo);dato.put("actor",actor);dato.put("entrada",entrada);
        String hash=Idempotencia.huellaExacta(documentos.objetoCanonico(dato));
        var previo=comandos.buscar(actor,key,tipo,hash);
        if(previo.isPresent()) {
            var original=documentos.comoObjeto(previo.get().getResultado(),FichaContrato.class);var actual=accesoContratos.leer(original.id(),actor);
            if(!Objects.equals(original.idOportunidad(),actual.getOportunidad().getId())
                    ||!Objects.equals(original.idSolicitud(),actual.getSolicitud()==null?null:actual.getSolicitud().getId())
                    ||!Objects.equals(original.idContratoAnterior(),actual.getContratoAnterior()==null?null:actual.getContratoAnterior().getId()))
                throw new ConflictoException("Los vínculos originales del resultado preparado ya no están autorizados.");
            return original;
        }
        var contrato=efecto.get();entidades.flush();var resultado=ficha(contrato,null);
        comandos.registrar(actor,key,tipo,hash,"CONTRATO_ALQUILER",contrato.getId(),origen,documentos.objetoCanonico(resultado));
        return resultado;
    }
    private static void exigirCambioReal(ContratoAlquiler contrato, EstadoContrato destino) {
        if (destino.codigo().equals(contrato.estadoActual())) {
            throw new ReglaNegocioException("El contrato ya esta en estado "
                    + destino.descripcion().toUpperCase(Locale.ROOT) + ".");
        }
    }

    private FichaContrato transicionarContrato(long idContrato, EstadoContrato destino,
                                                DatosTransicion datos, Actor actor,
                                                boolean revisarInmueble) {
        ContratoAlquiler contrato = accesoContratos.leer(idContrato, actor);
        // Se valida ANTES de tocar nada: ni historial, ni tareas, ni comision.
        // La transicion ilegal la corta `MaquinasEstado` dentro de `aplicar`;
        // esto cubre el unico caso que aquella deja pasar.
        exigirCambioReal(contrato, destino);
        LocalDate fecha = fechaTransicion(datos);
        transiciones.aplicar(contrato, idContrato, destino.codigo(), actor,
                motivo(datos, "Contrato " + destino.descripcion().toLowerCase(Locale.ROOT)), fecha);
        contrato.setFechaEfectivaEstado(fecha);
        contratos.save(contrato);
        if (revisarInmueble) {
            Captacion captacion = captacionDe(contrato);
            if (captacion != null && captacion.getPropiedad() != null) {
                tareas.crearRevisionInmueble(captacion.getPropiedad().getId(),
                        captacion.getAgente() != null ? captacion.getAgente().getId() : null,
                        "Revisar disponibilidad despues de " + destino.descripcion().toLowerCase(Locale.ROOT),
                        idContrato, actor);
            }
        }
        return ficha(contrato, comisiones.porContratosAlquiler(List.of(idContrato),actor).get(idContrato));
    }

    private SolicitudAlquiler solicitudAprobadaPropia(DatosContrato datos, Actor actor) {
        if (datos == null || datos.idSolicitud() == null || datos.idSolicitud() <= 0) {
            throw new ReglaNegocioException("Selecciona la solicitud aprobada que se va a alquilar.");
        }
        SolicitudAlquiler solicitud = solicitudes.buscarFicha(actor.idOrganizacion(), datos.idSolicitud())
                .orElseThrow(() -> new NoEncontradoException("Solicitud"));
        if (solicitud.getAgente() == null || solicitud.getAgente().getId() != actor.idRolOperativo()) {
            throw new AccesoNoAutorizadoException();
        }
        if (!solicitud.estaAprobada()) {
            throw new ReglaNegocioException("Solo se puede registrar el alquiler de una solicitud aprobada.");
        }
        if (solicitud.getOportunidad() == null) {
            throw new ReglaNegocioException("Oportunidad comercial no encontrada.");
        }
        return solicitud;
    }

    private static LocalDate fechaTransicion(DatosTransicion datos) {
        return fechaNoFutura(datos != null ? datos.fechaEfectiva() : null,
                "La fecha efectiva no puede ser futura.");
    }

    private static LocalDate fechaNoFutura(LocalDate fecha, String mensaje) {
        LocalDate efectiva = fecha != null ? fecha : LocalDate.now();
        if (efectiva.isAfter(LocalDate.now())) throw new ReglaNegocioException(mensaje);
        return efectiva;
    }

    private static String motivo(DatosTransicion datos, String porDefecto) {
        String indicado = datos != null ? texto(datos.motivo()) : null;
        return indicado != null ? indicado : porDefecto;
    }

    /**
     * Congela la atribucion del cierre (V27). Antes de esto el agente, la
     * captacion, el inmueble y el cliente se releian de la cadena vigente en
     * cada consulta, asi que una reasignacion posterior reescribia a quien se
     * le atribuia un alquiler cerrado meses atras.
     *
     * <p>El agente es el de la SOLICITUD —el que la respuesta publica— y, en su
     * defecto, el de la oportunidad: misma precedencia que ya usaban la ficha y
     * E4. El broker es el supervisor VIGENTE al cerrar; queda nulo si el agente
     * no tenia ninguno, y no se rellena despues con el supervisor de turno.
     *
     * <p><b>No cambia ninguna regla de alcance.</b> El BROKER sigue alcanzando
     * los contratos por captacion supervisada HOY, como declara la matriz
     * operacion→rol; este campo es trazabilidad del hecho, no un permiso.
     */
    private void atribuir(ContratoAlquiler contrato, SolicitudAlquiler solicitud,
                          OportunidadComercial oportunidad) {
        DetalleAgente agente = solicitud != null && solicitud.getAgente() != null
                ? solicitud.getAgente()
                : oportunidad.getAgente();
        Captacion captacion = oportunidad.getCaptacion();
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleCliente cliente = oportunidad.getCliente();
        contrato.atribuir(
                agente != null ? agente.getId() : null,
                agente != null ? brokerDe(agente.getId(), contrato.getOrganizacionId()) : null,
                captacion != null ? captacion.getId() : null,
                propiedad != null ? propiedad.getId() : null,
                cliente != null ? cliente.getId() : null);
    }

    private Long brokerDe(Long idRolAgente, Long idOrganizacion) {
        if (idRolAgente == null || idOrganizacion == null) {
            return null;
        }
        return supervisiones.buscarActivaPorAgente(idOrganizacion, idRolAgente)
                .map(SupervisionAgente::getIdRolBroker)
                .orElse(null);
    }

    @Override
    @Transactional
    public FichaRevisionDisponibilidad revisarDisponibilidad(long idContrato, String resultado,
                                                             String motivo, Actor actor) {
        String destino = destinoDeRevision(resultado);
        String texto = texto(motivo);
        if (texto == null) {
            throw new ReglaNegocioException("Indica el motivo de la revision.");
        }
        // 1) Contrato con tenant y alcance: un contrato de otra organizacion es
        //    404, no 403 (convencion del cable).
        ContratoAlquiler contrato = accesoContratos.leer(idContrato, actor);

        // 2) Estado: solo un contrato que dejo de ocupar el inmueble se revisa.
        if (!ESTADOS_REVISABLES.contains(contrato.estadoActual())) {
            throw new ReglaNegocioException(
                    "Solo se revisa la disponibilidad de un contrato finalizado, rescindido o anulado.");
        }

        // 6) Idempotencia ANTES de escribir: `uq_revision_contrato` es la
        //    ultima barrera, pero exponer su violacion como 500 no es una
        //    respuesta. Misma decision devuelve la revision original; una
        //    distinta es un conflicto.
        var previa = revisiones.porContrato(actor.idOrganizacion(), idContrato);
        if (previa.isPresent()) {
            RevisionDisponibilidad ya = previa.get();
            if (!ya.getDisponibilidadNueva().equals(destino)) {
                throw new ConflictoException(
                        "Este contrato ya se reviso con un resultado distinto ("
                                + descripcionDestino(ya.getDisponibilidadNueva()) + ").");
            }
            return ficha(ya, true);
        }

        // 3) Propiedad.
        Propiedad propiedad = contrato.getIdPropiedad() == null ? null
                : propiedades.findByOrganizacionIdAndId(actor.idOrganizacion(), contrato.getIdPropiedad())
                        .orElse(null);
        if (propiedad == null) {
            throw new NoEncontradoException("Local comercial");
        }
        // Un contrato ANULADO que nunca formalizo el cierre no dejo el local
        // ocupado: no hay nada que recuperar y no se inventa una revision.
        if (!DisponibilidadComercial.ALQUILADO.codigo().equals(propiedad.getDisponibilidadComercial())) {
            throw new ReglaNegocioException(
                    "El local no esta alquilado: este contrato no lo saco del mercado.");
        }

        // 4) Ocupacion: nadie libera un local que otro contrato sigue ocupando.
        //    Es el caso de la renovacion —anterior en R, sucesor en D/V— y la
        //    razon de que revisar el contrato anterior tenga que fallar.
        long vivos = contratos.contarVivosDePropiedad(
                actor.idOrganizacion(), propiedad.getId(), idContrato);
        if (vivos > 0) {
            throw new ReglaNegocioException(
                    "El local sigue ocupado por otro contrato vigente; no se puede liberar.");
        }

        String anterior = propiedad.getDisponibilidadComercial();

        // 5) La evidencia de la decision, con el contrato origen que
        //    `historial_estado` no puede expresar.
        RevisionDisponibilidad revision = new RevisionDisponibilidad();
        revision.setOrganizacionId(actor.idOrganizacion());
        revision.setIdContratoAlquiler(idContrato);
        revision.setIdPropiedad(propiedad.getId());
        revision.setDisponibilidadAnterior(anterior);
        revision.setDisponibilidadNueva(destino);
        revision.setMotivo(texto);
        revision.setIdActor(actor.idPersona());
        revision.setTipoRolActor(actor.tipoRolOperativo());
        revision.setFechaRevision(LocalDate.now());
        RevisionDisponibilidad guardada = revisiones.save(revision);

        // 6-7) La transicion pasa por Transiciones, asi que queda en
        //      historial_estado con actor, rol, motivo y fecha efectiva.
        //
        //      No pide la autoridad de la propiedad (P0), por la misma razon
        //      que `cerrarLocal`: es el ciclo del CONTRATO, lo ejecuta el
        //      broker que revisa —que nunca es responsable de una propiedad— y
        //      la disponibilidad que escribe es la consecuencia de que el
        //      inmueble dejo de estar ocupado, no una declaracion sobre el.
        //      Exigirla haria imposible recuperar la disponibilidad de ningun
        //      inmueble al terminar su contrato. Registrado en la lista
        //      explicita de `AutoridadDeLaPropiedadTest`.
        transiciones.aplicarDisponibilidad(propiedad, propiedad.getId(),
                DisponibilidadComercial.desde(destino), actor,
                "Revision del contrato " + idContrato + ": " + texto);
        propiedades.save(propiedad);

        // 8) Su tarea, no "alguna del inmueble": el vinculo lo da V43.
        tareas.resolverDeContratoOrigen(idContrato, actor);
        return ficha(guardada, false);
    }

    /** Contratos que dejaron de ocupar el inmueble y por tanto admiten revision. */
    private static final Set<String> ESTADOS_REVISABLES = Set.of(
            EstadoContrato.FINALIZADO.codigo(), EstadoContrato.RESCINDIDO.codigo(),
            EstadoContrato.ANULADO.codigo());

    /**
     * El cliente manda el RESULTADO funcional, no la letra de disponibilidad:
     * traducir es cosa del backend. {@code R} (Reservado) no es un desenlace
     * posible aqui —reservar pertenece a otra causa de negocio—.
     */
    private static String destinoDeRevision(String resultado) {
        String codigo = resultado == null ? "" : resultado.trim().toUpperCase(Locale.ROOT);
        return switch (codigo) {
            case "VOLVER_AL_MERCADO" -> DisponibilidadComercial.DISPONIBLE.codigo();
            case "RETIRAR_DEL_MERCADO" -> DisponibilidadComercial.RETIRADO.codigo();
            default -> throw new ReglaNegocioException(
                    "Resultado de revision invalido: usa VOLVER_AL_MERCADO o RETIRAR_DEL_MERCADO.");
        };
    }

    private static String descripcionDestino(String codigo) {
        return DisponibilidadComercial.DISPONIBLE.codigo().equals(codigo)
                ? "VOLVER_AL_MERCADO" : "RETIRAR_DEL_MERCADO";
    }

    private static FichaRevisionDisponibilidad ficha(RevisionDisponibilidad r, boolean repetida) {
        return new FichaRevisionDisponibilidad(r.getId(), r.getIdContratoAlquiler(),
                r.getIdPropiedad(), r.getDisponibilidadAnterior(), r.getDisponibilidadNueva(),
                descripcionDestino(r.getDisponibilidadNueva()), r.getMotivo(),
                r.getFechaRevision(), repetida);
    }

    // ------------------------------------------------------------------
    // Alcance por rol: BROKER por CAPTACION (§7), no por agente.
    // ------------------------------------------------------------------

    private static Captacion captacionDe(ContratoAlquiler contrato) {
        OportunidadComercial oportunidad = contrato.getOportunidad();
        return oportunidad != null ? oportunidad.getCaptacion() : null;
    }

    // ------------------------------------------------------------------
    // Derivaciones del cable
    // ------------------------------------------------------------------

    /**
     * El cierre solo admite Firmado o Vigente; por defecto Vigente. Los dos
     * mensajes son los del REST v1, que corta antes que su BL —la BL tiene un
     * texto distinto ("El contrato solo puede cerrarse como Firmado o
     * Vigente.") que nunca llega al cable.
     */
    private static Integer plazoMeses(SolicitudAlquiler solicitud) {
        if (solicitud == null) {
            return null;
        }
        if (solicitud.getPlazoContratoMeses() != null) {
            return solicitud.getPlazoContratoMeses();
        }
        return null; // Texto tentativo permanece en Solicitud; no acredita un número de meses.
    }

    private static String texto(String valor) {
        return valor != null && !valor.isBlank() ? valor.trim() : null;
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static FichaContrato ficha(ContratoAlquiler c,FichaComision comision) {
        SolicitudAlquiler s=c.getSolicitud();OportunidadComercial o=c.getOportunidad();
        Captacion cap=o==null?null:o.getCaptacion();Propiedad p=cap==null?null:cap.getPropiedad();
        PersonaRol propietario=p==null?null:p.getRolPropietario();DetalleCliente cliente=o==null?null:o.getCliente();
        DetalleAgente agente=c.getAgenteCierre()!=null?c.getAgenteCierre():s==null?null:s.getAgente();
        // Dos fechas no declaran por sí solas un plazo comercial expresado en meses.
        return new FichaContrato(c.getId(),s==null?null:s.getId(),s==null?null:s.getCodigoSolicitud(),
                o==null?null:o.getId(),o==null?null:o.getCodigoOportunidad(),nombre(cliente==null?null:cliente.getRol()),
                p==null?null:p.getDireccion(),p==null?null:p.getDistrito(),p==null?null:p.estadoLegado(),
                cap==null?null:cap.getCodigoCaptacion(),nombre(agente==null?null:agente.getRol()),c.getRentaContractual(),c.getMoneda(),
                plazoCompatible(c,s),c.getFechaInicioContrato(),c.getFechaFinContrato(),c.getFechaCierre(),c.estadoActual(),c.getIncidencias(),
                c.getIdRolAgenteCierre()!=null?c.getIdRolAgenteCierre():agente==null?null:agente.getId(),propietario==null?null:propietario.getId(),nombre(propietario),
                c.getContratoAnterior()==null?null:c.getContratoAnterior().getId(),comision);
    }
    private static Integer plazoCompatible(ContratoAlquiler c,SolicitudAlquiler s) {
        Integer plazo=plazoMeses(s);
        if(s==null||plazo==null||s.getFechaInicioContrato()==null)return null;
        return Objects.equals(c.getFechaInicioContrato(),s.getFechaInicioContrato())
                &&Objects.equals(c.getFechaFinContrato(),s.getFechaInicioContrato().plusMonths(plazo))?plazo:null;
    }
    private static void completarSnapshot(ContratoAlquiler contrato, SolicitudAlquiler solicitud,
                                           String estado) {
        LocalDate inicio = solicitud.getFechaInicioContrato();
        Integer plazo = plazoMeses(solicitud);
        LocalDate fin = inicio != null && plazo != null ? inicio.plusMonths(plazo) : null;
        if (Set.of(ContratoAlquiler.FIRMADO, ContratoAlquiler.VIGENTE).contains(estado)) {
            if (inicio == null || fin == null || !fin.isAfter(inicio)) {
                throw new ReglaNegocioException(
                        "Un contrato firmado o vigente requiere fecha inicial y fecha final validas.");
            }
        }
        contrato.setFechaInicioContrato(inicio);
        contrato.setFechaFinContrato(fin);
        contrato.setRentaContractual(solicitud.getMontoPropuesto());
        contrato.setMoneda(CondicionesEconomicas.moneda(solicitud.getMoneda(), "del contrato"));
        contrato.setFechaEfectivaEstado(contrato.getFechaCierre());
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
