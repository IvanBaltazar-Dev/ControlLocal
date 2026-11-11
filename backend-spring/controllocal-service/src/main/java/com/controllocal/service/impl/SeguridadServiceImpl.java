package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.EventoSeguridad;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.CuentaDeGobiernoFila;
import com.controllocal.persistence.repositorio.EventoSeguridadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.SeguridadService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeguridadServiceImpl implements SeguridadService {

    private final EventoSeguridadRepository eventos;
    private final CuentaAccesoRepository cuentas;

    public SeguridadServiceImpl(EventoSeguridadRepository eventos,
                                CuentaAccesoRepository cuentas) {
        this.eventos = eventos;
        this.cuentas = cuentas;
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
        if (actor == null || !actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        Page<Object[]> page = eventos.avisosDeGobierno(actor.idOrganizacion(), TIPOS_DE_GOBIERNO,
                PageRequest.of(paginaValida - 1, tamanoValido));
        return new Pagina<>(page.getContent().stream().map(SeguridadServiceImpl::aviso).toList(),
                page.getTotalElements());
    }

    /** Una consulta de pertenencias locales; no consulta credenciales, factores o cartera. */
    @Override
    @Transactional(readOnly = true)
    public List<CuentaDeGobierno> cuentas(Actor actor) {
        if (actor == null || !actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
        return cuentas.cuentasDeGobierno(actor.idOrganizacion()).stream()
                .map(SeguridadServiceImpl::cuenta)
                .toList();
    }

    private static CuentaDeGobierno cuenta(CuentaDeGobiernoFila fila) {
        return new CuentaDeGobierno(
                fila.idPersona(), fila.idCuenta(), fila.nombre(), fila.usuario(),
                fila.rolPrincipal(), fila.administrar(), fila.pertenenciaActiva(),fila.idMembresia(),fila.version());
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
