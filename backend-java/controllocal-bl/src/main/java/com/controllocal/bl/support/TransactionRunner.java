package com.controllocal.bl.support;

import java.sql.Connection;
import java.sql.SQLException;

import com.controllocal.bl.BusinessException;
import com.controllocal.config.TransactionContext;

public final class TransactionRunner {

    private TransactionRunner() {
    }

    public static <T> T write(TransactionalSupplier<T> supplier) {
        return write((TransactionalConnectionSupplier<T>) conn -> supplier.get());
    }

    public static <T> T write(TransactionalConnectionSupplier<T> supplier) {
        RuntimeException failure = null;
        try {
            Connection conn = TransactionContext.getConnection();
            T result = supplier.get(conn);
            TransactionContext.commit();
            return result;
        } catch (RuntimeException e) {
            failure = e;
            rollback(e);
            throw e;
        } catch (Exception e) {
            BusinessException businessError =
                    new BusinessException(mensajeTransaccional(e), e);
            failure = businessError;
            rollback(businessError);
            throw businessError;
        } finally {
            close(failure);
        }
    }

    private static String mensajeTransaccional(Exception error) {
        String detalle = error.getMessage();
        return detalle == null || detalle.isBlank()
                ? "Error al ejecutar la operacion transaccional."
                : "Error al ejecutar la operacion transaccional: " + detalle;
    }

    private static void rollback(Exception originalError) {
        try {
            TransactionContext.rollback();
        } catch (SQLException rollbackError) {
            originalError.addSuppressed(rollbackError);
        }
    }

    private static void close(RuntimeException originalError) {
        try {
            TransactionContext.close();
        } catch (SQLException closeError) {
            if (originalError != null) {
                originalError.addSuppressed(closeError);
                return;
            }
            throw new BusinessException("No se pudo cerrar la conexion transaccional.", closeError);
        }
    }

    public static void write(TransactionalRunnable runnable) {
        write(() -> {
            runnable.run();
            return null;
        });
    }

    public static void write(TransactionalConnectionRunnable runnable) {
        write((TransactionalConnectionSupplier<Void>) conn -> {
            runnable.run(conn);
            return null;
        });
    }

    @FunctionalInterface
    public interface TransactionalSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface TransactionalConnectionSupplier<T> {
        T get(Connection conn) throws Exception;
    }

    @FunctionalInterface
    public interface TransactionalRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface TransactionalConnectionRunnable {
        void run(Connection conn) throws Exception;
    }
}
