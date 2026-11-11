-- La persona que atiende una llamada puede ser un familiar, ocupante,
-- administrador u otro interlocutor. No es por eso titular ni autoridad.
-- El snapshot admite datos parciales y no sustituye la atribucion del propietario.
ALTER TABLE interaccion_comercial
    ADD COLUMN contacto_tipo VARCHAR(50),
    ADD COLUMN contacto_nombre VARCHAR(200),
    ADD COLUMN contacto_telefono VARCHAR(50);

COMMENT ON COLUMN interaccion_comercial.contacto_tipo IS
    'Tipo declarado del interlocutor en este contacto; no es un rol de titularidad.';
COMMENT ON COLUMN interaccion_comercial.contacto_nombre IS
    'Nombre conocido del interlocutor en el momento de la interaccion.';
COMMENT ON COLUMN interaccion_comercial.contacto_telefono IS
    'Telefono conocido del interlocutor en el momento de la interaccion.';
