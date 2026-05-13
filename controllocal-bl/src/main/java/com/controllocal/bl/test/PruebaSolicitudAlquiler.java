package com.controllocal.bl.test;

import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.SolicitudAlquiler;

import com.controllocal.model.persona.ClienteInteresado;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PruebaSolicitudAlquiler {
    public static void main(String[] args) {
        try {

            SolicitudAlquilerBusinessLogic service = new SolicitudAlquilerBusinessLogicImpl();
            // Nueva solicitud
            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setMontoPropuesto( new BigDecimal("8500"));
            solicitud.setFechaSolicitud(LocalDate.now());
            solicitud.setObservaciones( "Cliente interesado en contrato de 3 años.");
            // Mock Cliente
            ClienteInteresado cliente =new ClienteInteresado();
            // ID existente en BD
            cliente.setIdCliente(1L);
            solicitud.setClienteInteresado(cliente);
            // Mock Captación
            Captacion captacion = new Captacion();
            // ID existente en BD
            captacion.setIdCaptacion(1L);
            solicitud.setCaptacion(captacion);
            //  prueba
            System.out.println( "Registrando solicitud...");
            Long id = service.registrarSolicitud(solicitud);
            System.out.println("Solicitud registrada correctamente.");
            System.out.println("ID generado: " + id);
        } catch (Exception ex){
            System.err.println("Error en prueba:");
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
    }
}
