package com.controllocal.bl.support;

import com.controllocal.bl.BusinessException;
import com.controllocal.config.DBManager;
import com.controllocal.config.DatabaseConfig;

import java.sql.SQLException;

public final class TransactionRunner {

    private TransactionRunner() {
    }

    public static <T> T write(TransactionalSupplier<T> supplier) {
        try {
            DBManager.beginTransaction();
            T result = supplier.get();
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
    public interface TransactionalRunnable {
        void run() throws Exception;
    }
}
