package com.controllocal.app;

import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;

import java.math.BigDecimal;
import java.util.Optional;

public class LocalComercialDAOImplIntegrationTest {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);
        PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
        LocalComercialDAO localDAO = new LocalComercialDAOImpl();

        try {
            Propietario propietario = demoPropietario(propietarioDAO, sufijo);
            demoLocalComercial(localDAO, propietario.getIdPropietario(), sufijo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error en demo de LocalComercialDAO.", e);
        }
    }

    private static Propietario demoPropietario(PropietarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD PROPIETARIO SOPORTE -----");

        Propietario propietario = new Propietario();
        propietario.setTipoPersona(TipoPersona.NATURAL);
        propietario.setTipoDocumento("DNI");
        propietario.setNumeroDocumento("7" + sufijo);
        propietario.setNombresORazonSocial("Propietario Demo " + sufijo);
        propietario.setTelefono("900" + sufijo.substring(0, 5));
        propietario.setCorreo("propietario." + sufijo.toLowerCase() + "@controllocal.pe");
        propietario.setEstado(EstadoActivoInactivo.ACTIVO);

        Long id = dao.crear(propietario);
        System.out.println("[CREATE] Propietario creado con ID: " + id);

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[READ]   " + item.getNombresORazonSocial() + " | Estado: " + item.getEstado()
        ));

        propietario.setIdPropietario(id);
        return propietario;
    }

    private static LocalComercial demoLocalComercial(LocalComercialDAO dao, Long idPropietario, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD LOCAL COMERCIAL -----");

        LocalComercial local = new LocalComercial();
        local.setCodigoLocal("LOC" + sufijo);
        local.setDireccion("Av. Local Demo " + sufijo + " 100");
        local.setDistrito("San Borja");
        local.setMetraje(new BigDecimal("88.00"));
        local.setPrecioReferencial(new BigDecimal("5200.00"));
        local.setRubroPermitido("Retail");
        local.setDescripcion("Local demo " + sufijo);
        local.setEstado(EstadoLocalComercial.DISPONIBLE);
        local.setIdPropietario(idPropietario);

        Long id = dao.crear(local);
        System.out.println("[CREATE] Local creado con ID: " + id);

        Optional<LocalComercial> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoLocal() + " | Estado: " + item.getEstado()
                        + " | Propietario: " + item.getIdPropietario()
        ));

        if (encontrado.isPresent()) {
            LocalComercial actualizar = encontrado.get();
            actualizar.setPrecioReferencial(new BigDecimal("5450.00"));
            actualizar.setDescripcion("Local demo actualizado " + sufijo);
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[UPDATE] Precio: " + item.getPrecioReferencial() + " | Descripcion: " + item.getDescripcion()
        ));

        System.out.println("[LIST]   Total locales: " + dao.listarTodos().size());

        dao.eliminar(id);
        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[DELETE] Estado final: " + item.getEstado()
        ));

        local.setIdLocal(id);
        return local;
    }
}
