package com.controllocal.service.impl;

import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.seguridad.EventoSeguridad;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.CuentaDeGobiernoFila;
import com.controllocal.persistence.repositorio.EventoSeguridadRepository;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.SeguridadService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SeguridadServiceImpl implements SeguridadService {

    private final EventoSeguridadRepository eventos;
    private final CredencialUsuarioRepository credenciales;
    private final FactorAutenticacionRepository factores;
    private final UsuarioOrganizacionRepository membresias;

    public SeguridadServiceImpl(EventoSeguridadRepository eventos,
                                CredencialUsuarioRepository credenciales,
                                FactorAutenticacionRepository factores,
                                UsuarioOrganizacionRepository membresias) {
        this.eventos = eventos;
        this.credenciales = credenciales;
        this.factores = factores;
        this.membresias = membresias;
    }

    /**
     * El gate de rol se repite aqui aunque el controlador ya lo tenga. No es
     * redundancia inutil: la regla es «esto es gobierno» y vive en el caso de
     * uso, asi que un segundo llamador —una tarea, otro controlador— no puede
     * saltarsela por descuido. Es el mismo reparto que en F2.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<AvisoDeGobierno> avisosDeGobierno(int pagina, int tamano, Actor actor) {
        if (!actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        Page<Object[]> page = eventos.avisosDeGobierno(actor.idOrganizacion(), TIPOS_DE_GOBIERNO,
                PageRequest.of(paginaValida - 1, tamanoValido));
        return new Pagina<>(page.getContent().stream().map(SeguridadServiceImpl::aviso).toList(),
                page.getTotalElements());
    }

    /**
     * <b>Tres consultas planas en vez de una ingeniosa.</b> El padron sin
     * agregados ni joins a entidades sin relacion, la banda de gobierno aparte
     * y el recuento de codigos aparte; el cruce se hace aqui. La version que lo
     * resolvia todo en un solo {@code SELECT} devolvia una unica fila para todo
     * el tenant, y sobre decenas de cuentas dos consultas mas no se notan.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CuentaDeGobierno> cuentas(Actor actor) {
        if (!actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
        Map<Long, String> bandaPorCuenta = membresias.bandasActivas(actor.idOrganizacion()).stream()
                .collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (String) fila[1]));
        // Estar en el mapa significa "tiene factor ACTIVO"; el valor es cuantos
        // codigos le quedan sin usar. La ausencia es el dato, no un hueco.
        Map<Long, Long> codigosPorCredencial = factores
                .codigosDisponiblesPorCredencial(actor.idOrganizacion()).stream()
                .collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (Long) fila[1]));

        return credenciales.cuentasDeGobierno(actor.idOrganizacion()).stream()
                .map(fila -> cuenta(fila, bandaPorCuenta, codigosPorCredencial))
                .toList();
    }

    private static CuentaDeGobierno cuenta(CuentaDeGobiernoFila fila,
                                           Map<Long, String> bandaPorCuenta,
                                           Map<Long, Long> codigosPorCredencial) {
        Long codigos = codigosPorCredencial.get(fila.idCredencial());
        return new CuentaDeGobierno(
                fila.idPersona(), fila.idRol(), fila.nombre(), fila.nombreUsuario(),
                bandaPorCuenta.get(fila.idCredencial()),
                Codigos.ActivoInactivo.ACTIVO.equals(fila.estadoAdministrativo()),
                fila.debeCambiarContrasena(), fila.debeEnrolarMfa(),
                codigos != null, codigos == null ? 0 : codigos);
    }

    private static AvisoDeGobierno aviso(Object[] fila) {
        EventoSeguridad evento = (EventoSeguridad) fila[0];
        return new AvisoDeGobierno(
                evento.getId(), evento.getFecha(), evento.getTipo(), evento.getResultado(),
                evento.getIdPersona(), (String) fila[1],
                evento.getIdObjetivo(), (String) fila[2],
                evento.getMotivo(), evento.getIp());
        // `detalle_json` y `agente_usuario` NO salen: el primero es contexto de
        // diagnostico y el segundo huella del navegador. Ninguno hace falta
        // para saber que paso, y todo lo que se publica es superficie.
    }
}
