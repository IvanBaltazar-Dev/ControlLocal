-- V64 - Retirar el residuo de pruebas que quedo en la cartera
--
-- QUE PASO
-- --------
-- El 18 y el 19 de agosto de 2026 las pruebas de integracion corrieron con
-- TEST_DB_URL apuntando a la base de desarrollo. Nada lo impedia: cada prueba
-- leia la variable por su cuenta y le pasaba a Spring lo que hubiera dentro.
-- Medido el 2026-08-19 quedaron en la cartera de la organizacion 1:
--
--   162 propiedades ·  120 captaciones ·  184 hitos de precio
--   471 atributos   ·  120 titularidades ·  42 prospecciones
--   242 eventos de dominio (el outbox ENTERO era residuo)
--
-- EL DANO ERA DE EVIDENCIA, NO DE DATOS
-- -------------------------------------
-- La cabecera del Inicio decia "125 cosas necesitan tu atencion"; 120 eran
-- captaciones de prueba. Y la unica celda con muestra del contraste de renta
-- --Miraflores, 100-199 m2-- pasaba a tener 42 filas a 7000 y 21 a 7500: parece
-- 63 observaciones y son dos valores repetidos por un caso de prueba. Con el
-- residuo dentro, ninguna pantalla de E2 se puede evaluar a ojo, que es el
-- requisito propio de la etapa.
--
-- COMO SE IDENTIFICA, Y POR QUE ES DEMOSTRABLE
-- --------------------------------------------
-- NO por fecha: una fecha no prueba el origen. Por la direccion, que es literal
-- del codigo de prueba y que ninguna propiedad legitima usa.
--
--   'Av. Autoridad %'          118  AutoridadDelDatoIntegrationTest:95
--   'Av. Ida y Vuelta AUT-%'    42  AutoridadDelDatoIntegrationTest:477,606
--   'Av. Doble Fuente 100'       1  ejercicio manual contra la API de dev
--   'Av. Pardo 1234'             1  ejercicio manual contra la API de dev
--                              ---
--                              162
--
-- La atribucion inicial fue a PropiedadUniversalIntegrationTest y estaba
-- equivocada: esa prueba construye sus propios tenants (E2E-UNIVERSAL-A/B, orgs
-- 38 y 39) y no toco la organizacion 1. Quien escribio en la cartera fue
-- AutoridadDelDatoIntegrationTest, que registra por el caso de uso real y por
-- eso recibio codigos PROP-#### indistinguibles de los de produccion. La
-- direccion es lo unico que lo delata, y por eso es el criterio.
--
-- Comprobacion inversa, ejecutada antes de escribir esto: CERO propiedades
-- anteriores al 2026-08-18 llevan ninguna de las cuatro huellas. El criterio
-- selecciona exactamente 162 filas y ni una mas.
--
-- POR QUE SE PUEDE BORRAR
-- -----------------------
-- Del residuo no cuelga nada comercial: 0 contratos, 0 oportunidades,
-- 0 interacciones, 0 reasignaciones, 0 reportes al propietario. La migracion no
-- se fia de esa medicion: la vuelve a comprobar abajo y ABORTA si algo cambio.
--
-- EN UNA BASE LIMPIA ESTA MIGRACION NO HACE NADA. El seed nunca escribio esas
-- direcciones, asi que sobre un `docker compose up` recien creado los seis
-- DELETE afectan cero filas.
--
-- LA CAUSA SE CIERRA APARTE, Y EN EL MISMO CAMBIO
-- -----------------------------------------------
-- BaseDeDatosDePruebas rechaza cualquier base que no sea de pruebas antes de
-- que el contexto arranque, y AislamientoDePruebasTest rompe el build si una
-- prueba la rodea. Borrar el residuo sin cerrar la causa solo prepara la
-- siguiente contaminacion.

-- ---------------------------------------------------------------------------
-- 1. El conjunto, una sola vez
-- ---------------------------------------------------------------------------

CREATE TEMPORARY TABLE residuo_propiedad ON COMMIT DROP AS
SELECT id_propiedad
FROM propiedad
WHERE direccion LIKE 'Av. Autoridad %'
   OR direccion LIKE 'Av. Ida y Vuelta AUT-%'
   OR direccion IN ('Av. Doble Fuente 100', 'Av. Pardo 1234');

CREATE TEMPORARY TABLE residuo_captacion ON COMMIT DROP AS
SELECT id_captacion, id_condicion_economica
FROM captacion
WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

-- ---------------------------------------------------------------------------
-- 2. La guarda: si del residuo cuelga trabajo real, no se borra nada
-- ---------------------------------------------------------------------------
--
-- Una migracion de datos que borra sin mirar es peor que el residuo. Si
-- cualquiera de estas cinco cuenta no es cero, la propiedad dejo de ser residuo
-- y hay que decidir a mano.

DO $$
DECLARE
    contratos      bigint;
    oportunidades  bigint;
    interacciones  bigint;
    reasignaciones bigint;
    reportes       bigint;
BEGIN
    SELECT count(*) INTO contratos FROM contrato_alquiler
     WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad)
        OR id_captacion IN (SELECT id_captacion FROM residuo_captacion);

    SELECT count(*) INTO oportunidades FROM oportunidad_comercial
     WHERE id_captacion IN (SELECT id_captacion FROM residuo_captacion);

    SELECT count(*) INTO interacciones FROM interaccion_comercial
     WHERE id_captacion IN (SELECT id_captacion FROM residuo_captacion);

    SELECT count(*) INTO reasignaciones FROM reasignacion_captacion
     WHERE id_captacion IN (SELECT id_captacion FROM residuo_captacion);

    SELECT count(*) INTO reportes FROM reporte_propietario
     WHERE id_captacion IN (SELECT id_captacion FROM residuo_captacion);

    IF contratos + oportunidades + interacciones + reasignaciones + reportes > 0 THEN
        RAISE EXCEPTION
            'V64 aborta: del residuo cuelga trabajo comercial real (contratos=%, '
            'oportunidades=%, interacciones=%, reasignaciones=%, reportes=%). '
            'Deja de ser residuo: decide a mano que se conserva.',
            contratos, oportunidades, interacciones, reasignaciones, reportes;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. El borrado, de las hojas al tronco
-- ---------------------------------------------------------------------------

-- El outbox. Se acota por entidad, no por fecha: un evento de una propiedad
-- real creado el mismo dia no es residuo.
DELETE FROM evento_dominio
 WHERE (entidad_tipo = 'PROPIEDAD' AND entidad_id IN (SELECT id_propiedad FROM residuo_propiedad))
    OR (entidad_tipo = 'CAPTACION' AND entidad_id IN (SELECT id_captacion FROM residuo_captacion));

DELETE FROM prospeccion
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad)
    OR id_captacion IN (SELECT id_captacion FROM residuo_captacion);

DELETE FROM revision_disponibilidad
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

DELETE FROM publicacion
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

DELETE FROM foto_propiedad
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

DELETE FROM precio_propiedad
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad)
    OR id_captacion IN (SELECT id_captacion FROM residuo_captacion);

DELETE FROM atributo_propiedad
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

DELETE FROM titularidad_propiedad
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

DELETE FROM detalle_local_comercial
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

DELETE FROM captacion
 WHERE id_captacion IN (SELECT id_captacion FROM residuo_captacion);

-- Despues de la captacion, porque es ella quien la referencia.
DELETE FROM condicion_economica_captacion
 WHERE id_condicion_economica IN (
     SELECT id_condicion_economica FROM residuo_captacion WHERE id_condicion_economica IS NOT NULL);

DELETE FROM propiedad
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_propiedad);

-- ---------------------------------------------------------------------------
-- 4. Comprobacion de que quedo limpio
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    quedan bigint;
BEGIN
    SELECT count(*) INTO quedan FROM propiedad
     WHERE direccion LIKE 'Av. Autoridad %'
        OR direccion LIKE 'Av. Ida y Vuelta AUT-%'
        OR direccion IN ('Av. Doble Fuente 100', 'Av. Pardo 1234');

    IF quedan > 0 THEN
        RAISE EXCEPTION 'V64 no limpio: quedan % propiedades con huella de prueba.', quedan;
    END IF;
END $$;
