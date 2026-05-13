package com.controllocal.bl.test;

import com.controllocal.bl.customerAcquisitionLifecycle;
import com.controllocal.bl.impl.customerAcquisitionLifecycleImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PruebaCaptacion {
    public static void main(String[] args) {
        try {
            customerAcquisitionLifecycle service = new customerAcquisitionLifecycleImpl();

            // 1. Preparar los datos , la id debe estar en la bd
            Captacion nueva = new Captacion();
            nueva.setCodigoCaptacion("TEST-FAIL-001");
            nueva.setFechaCaptacion(LocalDate.now());
            nueva.setComisionPactada(new BigDecimal("5.5"));

            // Mock de Local
            LocalComercial local = new LocalComercial();
            local.setIdLocal(1L);
            nueva.setLocalComercial(local);

            // Mock de Agente (id_agente que ya tengas en tu tabla agente_inmobiliario)
            AgenteInmobiliario agente = new AgenteInmobiliario();
            agente.setIdAgente(4L);
            nueva.setAgenteResponsable(agente);

            // 2. Ejecutar la prueba
            System.out.println("Intentando registrar captación...");
            Long idGenerado = service.registerAcquisition(nueva);

            System.out.println("¡Éxito! Captación registrada con ID: " + idGenerado);

        } catch (Exception e) {
            System.err.println("Error en la prueba: " + e.getMessage());
            e.printStackTrace();
        }
    }
}