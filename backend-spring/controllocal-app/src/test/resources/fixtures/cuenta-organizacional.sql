-- EXCLUSIVAMENTE TEST. Reconstruye identidades ficticias V3/V6 tras retirar credencial_usuario.
-- El correo .invalid y el hash deliberadamente no autenticable no sirven para login real.
-- No copia contraseñas, canales laborales ni identidad global desde Persona. No reactiva bajas.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM persona_rol a JOIN persona_rol b
        ON b.organizacion_id=a.organizacion_id AND b.id_persona=a.id_persona
       JOIN organizacion o ON o.id_organizacion=a.organizacion_id
       WHERE o.codigo='BROX_LEGACY' AND a.tipo_rol='AGENTE' AND b.tipo_rol='BROKER'
         AND a.vigencia_hasta IS NULL AND b.vigencia_hasta IS NULL) THEN
        RAISE EXCEPTION 'Fixture ambiguo: una persona seed tiene dos roles principales';
    END IF;
    IF EXISTS(SELECT 1 FROM organizacion WHERE codigo='BROX_LEGACY'
       AND modalidad IS NOT NULL AND modalidad<>'EMPRESARIAL') THEN
        RAISE EXCEPTION 'La modalidad del fixture BROX_LEGACY fue cambiada por otra prueba';
    END IF;
END $$;

INSERT INTO persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
 SELECT b.organizacion_id,r.id_persona,'ADMIN',r.vigencia_desde
 FROM detalle_broker b JOIN persona_rol r ON r.id_persona_rol=b.id_persona_rol
 JOIN organizacion o ON o.id_organizacion=b.organizacion_id AND o.codigo='BROX_LEGACY'
 -- Código ficticio explícito V3; no se lee el booleano retirado ni se elige un broker cualquiera.
 WHERE b.codigo_broker='BRK-ADM-001' AND NOT EXISTS(SELECT 1 FROM persona_rol a
   WHERE a.organizacion_id=b.organizacion_id AND a.id_persona=r.id_persona
     AND a.tipo_rol='ADMIN' AND a.vigencia_hasta IS NULL);

INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash)
 SELECT DISTINCT 'fixture-org-'||r.organizacion_id||'-persona-'||r.id_persona,
        'fixture-org-'||r.organizacion_id||'-persona-'||r.id_persona||'@example.invalid',
        clock_timestamp(),'fixture-sin-credencial-autenticable'
 FROM persona_rol r JOIN organizacion o ON o.id_organizacion=r.organizacion_id AND o.codigo='BROX_LEGACY'
 WHERE r.tipo_rol IN ('AGENTE','BROKER','ADMIN') AND r.vigencia_hasta IS NULL
 ON CONFLICT(nombre_usuario) DO NOTHING;

INSERT INTO usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado,nombre_visible)
 SELECT p.organizacion_id,c.id_cuenta_acceso,p.id_persona,r.tipo_rol,
        EXISTS(SELECT 1 FROM persona_rol a WHERE a.organizacion_id=p.organizacion_id
          AND a.id_persona=p.id_persona AND a.tipo_rol='ADMIN' AND a.vigencia_hasta IS NULL),
        'A',p.nombres_o_razon_social
 FROM persona p JOIN organizacion o ON o.id_organizacion=p.organizacion_id AND o.codigo='BROX_LEGACY'
 JOIN cuenta_acceso c ON c.nombre_usuario='fixture-org-'||p.organizacion_id||'-persona-'||p.id_persona
 LEFT JOIN persona_rol r ON r.organizacion_id=p.organizacion_id AND r.id_persona=p.id_persona
   AND r.tipo_rol IN ('BROKER','AGENTE') AND r.vigencia_hasta IS NULL
 WHERE NOT EXISTS(SELECT 1 FROM usuario_organizacion u WHERE u.organizacion_id=p.organizacion_id
   AND u.id_usuario=c.id_cuenta_acceso);

-- Los vínculos de supervisión son los explícitos del seed V3. No se elige otro broker por heurística.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM usuario_organizacion u JOIN organizacion o ON o.id_organizacion=u.organizacion_id
       JOIN persona_rol a ON a.organizacion_id=u.organizacion_id AND a.id_persona=u.id_persona AND a.tipo_rol='AGENTE'
       WHERE o.codigo='BROX_LEGACY' AND u.estado='A' AND u.rol='AGENTE'
         AND NOT EXISTS(SELECT 1 FROM supervision_agente s JOIN persona_rol b ON b.id_persona_rol=s.id_rol_broker
           JOIN usuario_organizacion ub ON ub.organizacion_id=b.organizacion_id AND ub.id_persona=b.id_persona
             AND ub.estado='A' AND ub.rol='BROKER'
           WHERE s.organizacion_id=u.organizacion_id AND s.id_rol_agente=a.id_persona_rol AND s.fecha_fin IS NULL)) THEN
        RAISE EXCEPTION 'El fixture requiere la supervision explicita del seed para cada agente';
    END IF;
END $$;
UPDATE organizacion SET modalidad='EMPRESARIAL' WHERE codigo='BROX_LEGACY' AND modalidad IS NULL;
