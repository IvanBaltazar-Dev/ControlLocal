package com.controllocal.app;

public class ControlLocalApplication {

    public static void main(String[] args) {
        System.out.println("ControlLocal iniciado correctamente.");
        System.out.println("Use los servicios de la capa BL para ejecutar flujos de negocio.");

        if (args.length > 0 && "--demo".equalsIgnoreCase(args[0])) {
            DemoDataRunner.run();
        }
    }
}
