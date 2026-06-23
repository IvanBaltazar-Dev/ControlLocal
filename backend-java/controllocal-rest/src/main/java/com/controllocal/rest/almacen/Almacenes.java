package com.controllocal.rest.almacen;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Selector del almacen de documentos. La implementacion "hibrida" se resuelve una sola
 * vez segun {@code almacen.proveedor}:
 *
 *   LOCAL  -> siempre disco.
 *   S3     -> S3 si esta configurado; si no, disco.
 *   AUTO   -> S3 si esta configurado y el bucket responde; si no, disco (respaldo).
 *
 * Asi, en desarrollo local, el sistema queda funcional con o sin credenciales S3
 * (las temporales STS caducan): al reiniciar se vuelve a evaluar y cae a disco solo.
 */
public final class Almacenes {

    private static final Logger LOG = Logger.getLogger(Almacenes.class.getName());
    private static volatile AlmacenDocumentos actual;

    private Almacenes() {
    }

    public static AlmacenDocumentos actual() {
        AlmacenDocumentos instancia = actual;
        if (instancia == null) {
            synchronized (Almacenes.class) {
                instancia = actual;
                if (instancia == null) {
                    instancia = resolver();
                    actual = instancia;
                }
            }
        }
        return instancia;
    }

    private static AlmacenDocumentos resolver() {
        String proveedor = AwsConfig.get("almacen.proveedor", "ALMACEN_PROVEEDOR", "AUTO")
                .toUpperCase();
        AlmacenLocal local = AlmacenLocal.desdeConfig();

        if ("LOCAL".equals(proveedor)) {
            LOG.info("Almacen de documentos: disco local (proveedor=LOCAL).");
            return local;
        }

        Optional<AlmacenS3> s3 = AlmacenS3.desdeConfig();
        if (s3.isEmpty()) {
            LOG.info("Almacen de documentos: disco local (S3 sin configurar en aws.properties).");
            return local;
        }

        if ("S3".equals(proveedor)) {
            LOG.info("Almacen de documentos: Amazon S3 (proveedor=S3).");
            return s3.get();
        }

        // AUTO: S3 solo si el bucket responde; si no, respaldo en disco.
        AlmacenDocumentos.EstadoAlmacen estado = s3.get().verificarConexion();
        if (estado.conectado()) {
            LOG.info("Almacen de documentos: Amazon S3 (AUTO, " + estado.detalle() + ").");
            return s3.get();
        }
        LOG.warning("Almacen de documentos: disco local (AUTO, S3 no disponible: "
                + estado.detalle() + ").");
        return local;
    }
}
