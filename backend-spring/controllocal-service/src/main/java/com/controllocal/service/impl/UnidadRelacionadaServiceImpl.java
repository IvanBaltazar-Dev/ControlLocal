package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.UnidadRelacionada;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.UnidadRelacionadaRepository;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.UnidadRelacionadaService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.ConflictoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Un solo contrato de relación física; RF014 añade origen explícito sin reinterpretar A/I/C. */
@Service
public class UnidadRelacionadaServiceImpl implements UnidadRelacionadaService {

    private final UnidadRelacionadaRepository relaciones;
    private final PropiedadRepository propiedades;
    private final AutoridadDePropiedad autoridad;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final EventoDominioRepository eventos;
    private final EntityManager entidades;

    private static final String COMANDO = "REGISTRAR_ORIGEN_FISICO_UNIDAD";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public UnidadRelacionadaServiceImpl(UnidadRelacionadaRepository relaciones,
            PropiedadRepository propiedades, AutoridadDePropiedad autoridad, ContextosService contextos,
            CuentaAccesoRepository cuentas, UsuarioOrganizacionRepository miembros,
            ComandoIdempotenteRepository registros, ComandosIdempotentes comandos,
            Documentos documentos, EventoDominioRepository eventos, EntityManager entidades) {
        this.relaciones = relaciones;
        this.propiedades = propiedades;
        this.autoridad = autoridad;
        this.contextos=contextos; this.cuentas=cuentas; this.miembros=miembros;
        this.registros=registros; this.comandos=comandos; this.documentos=documentos;
        this.eventos=eventos; this.entidades=entidades;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaUnidadRelacionada> deLaPropiedad(long idPropiedad, Actor actor) {
        Alcances.exigirOperador(actor);
        autoridad.buscarParaLectura(actor, idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
        List<UnidadRelacionada> filas = relaciones.deLaPropiedad(actor.idOrganizacion(), idPropiedad);
        LinkedHashSet<Long> idsDestino = new LinkedHashSet<>();
        filas.forEach(fila -> idsDestino.add(idPropiedad == fila.getIdPropiedadPrincipal()
                ? fila.getIdPropiedadRelacionada() : fila.getIdPropiedadPrincipal()));
        if (idsDestino.isEmpty()) return List.of();
        Map<Long, Propiedad> porId = new LinkedHashMap<>();
        propiedades.buscarVariasParaLectura(actor.idOrganizacion(), idsDestino,
                        autoridad.alcanceDeLectura(actor).paramRolesArray())
                .forEach(propiedad -> porId.put(propiedad.getId(), propiedad));
        return filas.stream()
                .map(fila -> fichaDe(idPropiedad, fila, porId))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(readOnly=true)
    public List<FichaOrigen> consultarOrigen(long idPropiedad, Instant corte, Actor actor) {
        Alcances.exigirOperador(actor);
        exigirLectura(idPropiedad,actor);
        LocalDate fecha=corte==null?null:corte.atZone(LIMA).toLocalDate();
        var filas=relaciones.origenesDeLaPropiedad(actor.idOrganizacion(),idPropiedad).stream()
                .filter(r->corte==null || (!r.getRegistradoEn().isAfter(corte)
                        &&!r.getVigenteDesde().isAfter(fecha)
                        &&(r.getVigenteHasta()==null||!r.getVigenteHasta().isBefore(fecha))))
                .toList();
        if(filas.isEmpty())return List.of();
        var destinos=new LinkedHashSet<Long>();
        filas.forEach(r->destinos.add(r.getIdPropiedadPrincipal()==idPropiedad
                ?r.getIdPropiedadRelacionada():r.getIdPropiedadPrincipal()));
        var permitidos=new LinkedHashSet<Long>();
        propiedades.buscarVariasParaLectura(actor.idOrganizacion(),destinos,
                autoridad.alcanceDeLectura(actor).paramRolesArray()).forEach(p->permitidos.add(p.getId()));
        return filas.stream().filter(r->permitidos.contains(r.getIdPropiedadPrincipal()==idPropiedad
                ?r.getIdPropiedadRelacionada():r.getIdPropiedadPrincipal())).map(UnidadRelacionadaServiceImpl::fichaOrigen).toList();
    }

    @Override
    @Transactional(readOnly=true)
    public ContextoOrigen capacidadSobreOrigen(long matriz,long unidad,Actor actor) {
        exigirAgente(actor); referencias(matriz,unidad);
        var m=exigirLectura(matriz,actor);var u=exigirLectura(unidad,actor);
        autorizarEscritura(m,u,actor);
        return new ContextoOrigen(matriz,unidad,m.getVersionFisica(),u.getVersionFisica());
    }

    @Override
    @Transactional(readOnly=true)
    public java.util.Optional<ResultadoOrigen> consultarResultadoOrigen(String clave,Actor actor) {
        exigirAgente(actor);
        return comandos.consultarResultado(actor,clave,COMANDO).map(c->{
            var original=documentos.comoObjeto(c.getResultado(),ResultadoOrigen.class);
            exigirLectura(original.idMatriz(),actor);exigirLectura(original.idUnidad(),actor);
            return original;
        });
    }

    @Override
    @Transactional
    public ResultadoOrigen registrarOrigen(DatosOrigen datos,Actor actor,ContextoSesion sesion,
            String claveIdempotencia,Procedencia procedente) {
        exigirAgente(actor);
        DatosOrigen d=datosValidos(datos);
        String clave=Idempotencia.exigirClave(claveIdempotencia);
        Procedencia procedencia=Procedencia.oPantalla(procedente);
        iniciar(actor,sesion);
        String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("datos",d,"procedencia",procedencia)));
        String identidad=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        var previo=comandos.buscar(actor,clave,COMANDO,huella);
        if(previo.isPresent()) {
            var original=documentos.comoObjeto(previo.get().getResultado(),ResultadoOrigen.class);
            exigirLectura(original.idMatriz(),actor); exigirLectura(original.idUnidad(),actor);
            return original;
        }
        relaciones.bloquearGrafoOrigen(actor.idOrganizacion());
        var bloqueadas=new LinkedHashMap<Long,Propiedad>();
        for(long id:new TreeSet<>(List.of(d.idMatriz(),d.idUnidad()))) {
            var p=propiedades.bloquearParaEscritura(actor.idOrganizacion(),id)
                    .orElseThrow(()->new NoEncontradoException("Propiedad"));
            entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);
            bloqueadas.put(id,p);
        }
        var matriz=bloqueadas.get(d.idMatriz());var unidad=bloqueadas.get(d.idUnidad());
        exigirLectura(d.idMatriz(),actor); exigirLectura(d.idUnidad(),actor);
        autorizarEscritura(matriz,unidad,actor);
        if(matriz.getVersionFisica()!=d.versionMatrizEsperada() || unidad.getVersionFisica()!=d.versionUnidadEsperada())
            throw new ConflictoException("Cambió una versión física; revisa ambos extremos antes de registrar el origen.");
        if(relaciones.existeDuplicadoOrigen(actor.idOrganizacion(),d.idMatriz(),d.idUnidad(),d.vigenteDesde(),d.vigenteHasta()))
            throw new ConflictoException("El mismo origen ya está declarado durante esa vigencia.");
        if(relaciones.existeCaminoOrigen(actor.idOrganizacion(),d.idUnidad(),d.idMatriz()))
            throw new ReglaNegocioException("El origen produciría un ciclo entre unidades físicas.");
        incrementar(matriz,d.versionMatrizEsperada(),actor); incrementar(unidad,d.versionUnidadEsperada(),actor);
        Instant ahora=Instant.now().truncatedTo(ChronoUnit.MICROS);
        var relacion=UnidadRelacionada.crearOrigen(actor.idOrganizacion(),d.idMatriz(),d.idUnidad(),
                d.vigenteDesde(),d.vigenteHasta(),d.situacionRegistral(),d.fuente(),
                documentos.objetoCanonico(Map.of("procedencia",procedencia)),
                d.versionMatrizEsperada(),d.versionUnidadEsperada(),actor.idRolOperativo(),ahora);
        relaciones.saveAndFlush(relacion);
        var carga=new LinkedHashMap<String,Object>();
        carga.put("datos",d);carga.put("versionMatriz",matriz.getVersionFisica());carga.put("versionUnidad",unidad.getVersionFisica());
        carga.put("relacion", "ORIGEN_MATRIZ_UNIDAD");carga.put("registradoEn",ahora);
        var evento=eventos.saveAndFlush(procedencia.sellar(EventoDominio.de(actor.idOrganizacion(),
                "ORIGEN_FISICO_REGISTRADO","UNIDAD_RELACIONADA",relacion.getId(),actor.idRolOperativo(),procedencia.canal()))
                .con(documentos.objetoCanonico(carga)));
        var resultado=new ResultadoOrigen(relacion.getId(),d.idMatriz(),d.idUnidad(),"ORIGEN_MATRIZ_UNIDAD",
                d.vigenteDesde(),d.vigenteHasta(),d.situacionRegistral(),estado(d.situacionRegistral()),d.fuente(),
                matriz.getVersionFisica(),unidad.getVersionFisica(),ahora,evento.getId());
        comandos.registrar(actor,clave,COMANDO,huella,"UNIDAD_RELACIONADA",relacion.getId(),procedencia,serializar(resultado));
        return resultado;
    }

    private void iniciar(Actor actor,ContextoSesion sesion) {
        if(sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        try {
            var vigente=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(vigente).equals(sesion))throw new AccesoNoAutorizadoException();
        } catch(CredencialesInvalidasException fallo) { throw new AccesoNoAutorizadoException(); }
    }
    private Propiedad exigirLectura(long id,Actor actor) {
        return autoridad.buscarParaLectura(actor,id).orElseThrow(()->new NoEncontradoException("Propiedad"));
    }
    private void autorizarEscritura(Propiedad matriz,Propiedad unidad,Actor actor) {
        autoridad.exigirEdicion(actor,matriz); autoridad.exigirEdicion(actor,unidad);
    }
    private void incrementar(Propiedad p,long observada,Actor actor) {
        if(propiedades.incrementarVersionFisicaSi(actor.idOrganizacion(),p.getId(),observada)!=1)
            throw new ConflictoException("La revisión física cambió; no se sobrescribe el origen observado.");
        entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);
    }
    private static void exigirAgente(Actor actor) {
        if(actor==null||!actor.esAgente())throw new AccesoNoAutorizadoException();
    }
    private static void referencias(Long matriz,Long unidad) {
        if(matriz==null||unidad==null||matriz<=0||unidad<=0||matriz.equals(unidad))
            throw new ReglaNegocioException("El origen requiere referencias explícitas de dos propiedades distintas.");
    }
    private static DatosOrigen datosValidos(DatosOrigen d) {
        if(d==null)throw new ReglaNegocioException("Debe declarar el origen físico.");
        referencias(d.idMatriz(),d.idUnidad());
        if(d.vigenteDesde()==null||(d.vigenteHasta()!=null&&d.vigenteHasta().isBefore(d.vigenteDesde())))
            throw new ReglaNegocioException("Debe declarar un intervalo válido para el origen.");
        if(d.versionMatrizEsperada()==null||d.versionUnidadEsperada()==null||d.versionMatrizEsperada()<0||d.versionUnidadEsperada()<0)
            throw new ReglaNegocioException("Se requieren las dos versiones físicas observadas.");
        if(d.fuente()==null||d.fuente().isBlank())throw new ReglaNegocioException("Debe conservar la fuente del origen.");
        String situacion=d.situacionRegistral()==null||d.situacionRegistral().isBlank()?null:d.situacionRegistral().trim();
        return new DatosOrigen(d.idMatriz(),d.idUnidad(),d.vigenteDesde(),d.vigenteHasta(),situacion,d.fuente().trim(),
                d.versionMatrizEsperada(),d.versionUnidadEsperada());
    }
    private static EstadoDato estado(String situacion) { return situacion==null?EstadoDato.FALTANTE:EstadoDato.CONOCIDO; }
    private static FichaOrigen fichaOrigen(UnidadRelacionada r) {
        return new FichaOrigen(r.getId(),r.getIdPropiedadPrincipal(),r.getIdPropiedadRelacionada(),"ORIGEN_MATRIZ_UNIDAD",
                r.getVigenteDesde(),r.getVigenteHasta(),r.getSituacionRegistral(),estado(r.getSituacionRegistral()),
                r.getFuente(),r.getVersionMatrizObservada(),r.getVersionUnidadObservada(),r.getRegistradoEn());
    }
    private String serializar(ResultadoOrigen r) {
        var m=new LinkedHashMap<String,Object>();
        m.put("idRelacion",r.idRelacion());m.put("idMatriz",r.idMatriz());m.put("idUnidad",r.idUnidad());m.put("relacion",r.relacion());
        m.put("vigenteDesde",r.vigenteDesde());m.put("vigenteHasta",r.vigenteHasta());m.put("situacionRegistral",r.situacionRegistral());
        m.put("estadoSituacion",r.estadoSituacion());m.put("fuente",r.fuente());m.put("versionMatriz",r.versionMatriz());
        m.put("versionUnidad",r.versionUnidad());m.put("registradoEn",r.registradoEn());m.put("idEvento",r.idEvento());
        return documentos.objetoCanonico(m);
    }

    private static FichaUnidadRelacionada fichaDe(long idPropiedad, UnidadRelacionada fila,
                                                   Map<Long, Propiedad> porId) {
        long idDestino = idPropiedad == fila.getIdPropiedadPrincipal()
                ? fila.getIdPropiedadRelacionada() : fila.getIdPropiedadPrincipal();
        Propiedad destino = porId.get(idDestino);
        if (destino == null) {
            return null;
        }
        return new FichaUnidadRelacionada(fila.getId(), destino.getId(), destino.getCodigo(),
                AtributosGobernados.nombreDelTipo(destino.getTipoInmueble()),
                AtributosGobernados.rotuloDelTipo(destino.getTipoInmueble()),
                fila.getTipoRelacion(), rotuloDeRelacion(fila.getTipoRelacion()),
                destino.getPartidaRegistral(), fila.getVigenteDesde(), fila.getVigenteHasta());
    }

    private static String rotuloDeRelacion(String codigo) {
        return switch (codigo) {
            case UnidadRelacionada.ACCESORIA -> "Accesoria";
            case UnidadRelacionada.INDEPENDIENTE -> "Independiente";
            case UnidadRelacionada.REGLAMENTO_INTERNO -> "Vinculada por reglamento interno";
            case UnidadRelacionada.ORIGEN_MATRIZ_UNIDAD -> "Origen matriz–unidad";
            default -> codigo;
        };
    }
}
