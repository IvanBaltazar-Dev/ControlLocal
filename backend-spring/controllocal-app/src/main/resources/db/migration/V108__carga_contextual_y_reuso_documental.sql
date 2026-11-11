-- Contrato técnico RF052/CAT-E32/AC118–120. No ejecutar: Root aplica sólo en DB propia.
create table carga_archivo_contextual (
    id_carga_archivo bigserial primary key,
    organizacion_id bigint not null references organizacion(id_organizacion),
    version bigint not null default 0 check(version>=0),
    archivo_ref uuid not null unique,
    id_rol_autor bigint not null,
    id_encargo_previsto bigint,
    id_documento_previsto bigint,
    finalidad varchar(40) not null check(finalidad in ('ACEPTACION_ENCARGO','TITULARIDAD_ENCARGO','REPRESENTACION_ENCARGO','FIN_ENCARGO')),
    version_destino bigint not null check(version_destino>=0),
    clave_proveedor varchar(500) not null,
    nombre varchar(200) not null,
    content_type varchar(100) not null,
    huella varchar(64) not null check(huella~'^[0-9a-f]{64}$'),
    tamano_bytes bigint not null check(tamano_bytes>0),
    procedencia text not null check(jsonb_typeof(procedencia::jsonb)='object'),
    cargada_en timestamptz not null,
    unique(organizacion_id,id_carga_archivo),
    constraint ck_carga_contexto_existente check(id_encargo_previsto is not null or id_documento_previsto is not null),
    foreign key(organizacion_id,id_rol_autor) references persona_rol(organizacion_id,id_persona_rol),
    foreign key(organizacion_id,id_encargo_previsto) references captacion(organizacion_id,id_captacion),
    foreign key(organizacion_id,id_documento_previsto) references documento_encargo(organizacion_id,id_documento_encargo)
);
create index ix_carga_archivo_actor on carga_archivo_contextual(organizacion_id,id_rol_autor,cargada_en);
alter table archivo_encargo add column origen_referencia uuid,
    add column origen_tipo varchar(40),
    add column origen_huella_vinculo varchar(64),
    add column version_destino bigint;
alter table archivo_encargo add constraint ck_archivo_origen_versionado check(
    (origen_referencia is null and origen_tipo is null and origen_huella_vinculo is null and version_destino is null)
    or (origen_referencia is not null and origen_tipo is not null and origen_tipo in ('CARGA_TEMPORAL','ARCHIVO_ENCARGO','FOTO_PROPIEDAD','FOTO_PERFIL','DOCUMENTO_SOLICITUD')
        and origen_huella_vinculo is not null and origen_huella_vinculo~'^[0-9a-f]{64}$'
        and version_destino is not null and version_destino>=0));
alter table archivo_encargo add constraint ck_archivo_origen_distinto check(origen_referencia is null or origen_referencia<>archivo_ref);
-- FK polimórfica de la versión de origen: comprueba existencia en su tenant, sin reevaluar permisos históricos.
create function validar_origen_archivo_encargo() returns trigger language plpgsql as $$
declare origen_org bigint;
begin
    if new.origen_referencia is null then return new; end if;
    case new.origen_tipo
      when 'CARGA_TEMPORAL' then select organizacion_id into origen_org from carga_archivo_contextual where organizacion_id=new.organizacion_id and archivo_ref=new.origen_referencia for key share;
      when 'ARCHIVO_ENCARGO' then select organizacion_id into origen_org from archivo_encargo where organizacion_id=new.organizacion_id and archivo_ref=new.origen_referencia for key share;
      when 'FOTO_PROPIEDAD' then select organizacion_id into origen_org from foto_propiedad where organizacion_id=new.organizacion_id and archivo_ref=new.origen_referencia for key share;
      when 'FOTO_PERFIL' then select organizacion_id into origen_org from persona where organizacion_id=new.organizacion_id and foto_ref=new.origen_referencia for key share;
      when 'DOCUMENTO_SOLICITUD' then select organizacion_id into origen_org from documento_solicitud where organizacion_id=new.organizacion_id and archivo_ref=new.origen_referencia for key share;
      else raise exception 'Tipo de origen documental no definido' using errcode='23503';
    end case;
    if origen_org is null then raise exception 'La versión de origen documental no pertenece a la organización' using errcode='23503'; end if;
    return new;
end $$;
create constraint trigger fk_archivo_origen_contextual after insert or update of origen_referencia,origen_tipo,organizacion_id on archivo_encargo
    deferrable initially deferred for each row execute function validar_origen_archivo_encargo();

create function conservar_original_archivo_encargo() returns trigger language plpgsql as $$
begin
    if row(new.organizacion_id,new.id_rol_autor,new.registrado_en,new.origen_referencia,new.origen_tipo,new.origen_huella_vinculo,new.version_destino,new.archivo_ref,new.clave_proveedor,new.huella,new.nombre,new.content_type,new.finalidad,new.id_documento_encargo)
        is distinct from row(old.organizacion_id,old.id_rol_autor,old.registrado_en,old.origen_referencia,old.origen_tipo,old.origen_huella_vinculo,old.version_destino,old.archivo_ref,old.clave_proveedor,old.huella,old.nombre,old.content_type,old.finalidad,old.id_documento_encargo)
       or (old.id_captacion is not null and new.id_captacion is distinct from old.id_captacion) then
        raise exception 'El original, la finalidad y la procedencia documental son inmutables' using errcode='23514';
    end if;
    return new;
end $$;
create trigger trg_conservar_original_archivo_encargo before update on archivo_encargo for each row execute function conservar_original_archivo_encargo();
-- La carga conserva original/autor/contexto sin plazo nuevo; no representa conformidad ni vínculo confirmado.
-- Los PDF generados por Core conservan su origen DocumentoEncargo y no fabrican una carga humana.
-- La nueva referencia no copia bytes ni atribuciones; origen_tipo+UUID+huella conservan la procedencia exacta.
