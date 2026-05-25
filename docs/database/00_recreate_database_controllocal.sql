-- =========================================================
-- Recreacion de base de datos ControlLocal
-- ADVERTENCIA: este script elimina la base controllocal completa.
-- Ejecutalo solo cuando quieras empezar desde cero.
-- =========================================================

DROP DATABASE IF EXISTS controllocal;

CREATE DATABASE controllocal
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE controllocal;
