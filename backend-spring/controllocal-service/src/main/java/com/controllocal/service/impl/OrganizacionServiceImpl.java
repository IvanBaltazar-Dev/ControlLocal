package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.persistence.repositorio.OrganizacionRepository;
import com.controllocal.service.OrganizacionService;
import org.springframework.stereotype.Service;

@Service
public class OrganizacionServiceImpl implements OrganizacionService {

    private final OrganizacionRepository organizaciones;

    /**
     * El id del tenant de legado no cambia en toda la vida del proceso, asi
     * que se memoiza: se resuelve una vez y el resto de los requests no
     * vuelven a la BD solo para saber en que organizacion estan.
     */
    private volatile Long idLegado;

    public OrganizacionServiceImpl(OrganizacionRepository organizaciones) {
        this.organizaciones = organizaciones;
    }

    @Override
    public long idOrganizacionActual() {
        Long memoizado = idLegado;
        if (memoizado == null) {
            memoizado = organizaciones.findByCodigo(Organizacion.CODIGO_LEGADO)
                    .map(Organizacion::getId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No existe la organizacion de legado " + Organizacion.CODIGO_LEGADO
                                    + ": falta aplicar la migracion V6 del nucleo multi-tenant."));
            idLegado = memoizado;
        }
        return memoizado;
    }
}
