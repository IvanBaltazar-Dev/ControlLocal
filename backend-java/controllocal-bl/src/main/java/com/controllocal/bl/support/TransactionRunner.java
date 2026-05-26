package com.controllocal.bl.support;

import java.sql.Connection;
import java.sql.SQLException;

import com.controllocal.bl.BusinessException;
import com.controllocal.config.DatabaseConfig;
import com.controllocal.config.DBManager;

public final class TransactionRunner {

    private TransactionRunner() {
    }

    public static <T> T write(TransactionalSupplier<T> supplier) {
        return write((TransactionalConnectionSupplier<T>) conn -> supplier.get());
    }

    public static <T> T write(TransactionalConnectionSupplier<T> supplier) {
        try {
            Connection conn = DBManager.beginTransaction();
            T result = supplier.get(conn);
            DatabaseConfig.commit();
            return result;
        } catch (RuntimeException e) {
            DatabaseConfig.rollback();
            throw e;
        } catch (Exception e) {
            DatabaseConfig.rollback();
            throw new BusinessException("Error al ejecutar la operacion transaccional.", e);
        } finally {
            DatabaseConfig.close();
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

    public static void commit() {
        try {
            DatabaseConfig.commit();
        } catch (SQLException e) {
            throw new BusinessException("No se pudo confirmar la transaccion.", e);
        }
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
