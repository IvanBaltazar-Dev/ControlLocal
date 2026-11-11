package com.controllocal.integracion.soporte;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.PiezasComercialesService;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.almacen.GaleriaDeFotos;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preparacion exclusivamente TEST del contrato HTTP-NATIVO-C6. No crea un
 * endpoint, login demostrado, pago real, perfil IA ni una regla comercial.
 */
public final class PreparacionHttpNativoC6 {
    private static final Path RAIZ = Path.of("D:/init/ControlLocal/work/c6-k33-20261031");
    private static final long SEMILLA = 20261101L;
    private static final int ANCHO = 1600, ALTO = 1200;

    private PreparacionHttpNativoC6() { }

    public static void main(String[] argumentos) {
        try {
            var c = validar(argumentos, System.getenv());
            Resultado resultado;
            var app = new SpringApplication(ControlLocalApplication.class);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.setLogStartupInfo(false);
            app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("fixture-http-nativo-c6", propiedades(c))));
            try (var contexto = app.run("--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=0")) {
                resultado = preparar(contexto, c);
            } finally { SecurityContextHolder.clearContext(); }
            // Root ha preparado previamente la ACL del directorio privado.
            var json = new ObjectMapper();
            Files.write(c.ensayo().resolve("privado/tokens.json"), json.writeValueAsBytes(resultado.privado()),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            byte[] manifiesto = json.writeValueAsBytes(resultado.publico());
            Files.write(c.ensayo().resolve("manifiesto.json"), manifiesto,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("HTTP_NATIVO_C6_PREPARADO " + json.writeValueAsString(Map.of(
                    "corrida",c.corrida(),"candidatoSha256",c.candidato(),"manifiestoSha256",huella(manifiesto),
                    "perfiles",resultado.publico().perfiles().size(),"fotos",resultado.publico().perfiles().stream().mapToInt(p->p.fotos().size()).sum())));
        } catch (Exception | AssertionError fallo) {
            // No publicar argumentos, propiedades, credenciales, SID, JWT o causas.
            System.err.println("No se completo la preparacion sintetica HTTP nativa C6.");
            System.exit(2);
        }
    }

    static Configuracion validar(String[] a, Map<String,String> env) throws Exception {
        if(a==null||a.length!=3||env==null||a[0]==null||a[0].length()>60
                ||!a[0].matches("[a-z0-9]+(?:-[a-z0-9]+)*")||a[2]==null||!a[2].matches("[0-9a-fA-F]{64}"))throw invalida();
        Path ensayo=Path.of(a[1]);
        Path esperado=RAIZ.resolve("http-nativo-"+a[0]).toAbsolutePath().normalize();
        if(!ensayo.isAbsolute()||!ensayo.normalize().equals(esperado)||!Files.isDirectory(ensayo)
                ||Files.isSymbolicLink(ensayo)||!ensayo.toRealPath().startsWith(RAIZ.toRealPath()))throw invalida();
        ensayo=ensayo.toRealPath();
        Path privado=ensayo.resolve("privado");
        if(!Files.isDirectory(privado)||Files.isSymbolicLink(privado)||!privado.toRealPath().getParent().equals(ensayo)
                ||Files.exists(privado.resolve("tokens.json"))||Files.exists(ensayo.resolve("manifiesto.json")))throw invalida();
        String url=env.get("TEST_DB_URL"),usuario=env.get("TEST_DB_USER"),clave=env.get("TEST_DB_PASSWORD");
        if(url==null||!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):55441/controllocal_ci_c4")
                ||usuario==null||usuario.isBlank()||clave==null||clave.isBlank())throw invalida();
        String directorio=env.get("ALMACEN_DIR");
        if(directorio==null)throw invalida();
        Path almacen=Path.of(directorio);
        if(!almacen.isAbsolute()||!almacen.normalize().equals(ensayo.resolve("almacen"))||Files.isSymbolicLink(almacen)
                ||Files.exists(almacen)&&!almacen.toRealPath().getParent().equals(ensayo))throw invalida();
        String token=env.get("API_TOKEN_SECRET"),mfa=env.get("MFA_CLAVE_CIFRADO"),anterior=env.getOrDefault("MFA_CLAVE_CIFRADO_ANTERIOR","");
        if(token==null||token.length()<32||TokenService.esFallbackDeDesarrollo(token)
                ||!claveMfaValida(mfa)||!anterior.isBlank()&&!claveMfaValida(anterior))throw invalida();
        short version;
        try{version=Short.parseShort(env.getOrDefault("MFA_VERSION_CLAVE","1"));}catch(NumberFormatException fallo){throw invalida();}
        if(version<1)throw invalida();
        return new Configuracion(a[0],ensayo,a[2].toLowerCase(java.util.Locale.ROOT),url,usuario,clave,almacen.normalize(),token,mfa,anterior,version);
    }

    private static boolean claveMfaValida(String valor) {
        if(valor==null||valor.length()<32||CifradoSecretos.esFallbackDeDesarrollo(valor))return false;
        if(valor.startsWith("base64:"))try{Base64.getDecoder().decode(valor.substring(7));}catch(IllegalArgumentException fallo){return false;}
        return true;
    }
    private static Map<String,Object> propiedades(Configuracion c) {
        var p=new LinkedHashMap<String,Object>();
        p.put("spring.profiles.active","test");p.put("spring.main.web-application-type","servlet");
        p.put("server.address","127.0.0.1");p.put("server.port",0);
        p.put("spring.datasource.url",c.url());p.put("spring.datasource.username",c.usuario());p.put("spring.datasource.password",c.clave());
        p.put("spring.datasource.driver-class-name","org.postgresql.Driver");
        p.put("spring.datasource.hikari.jdbc-url",c.url());p.put("spring.datasource.hikari.username",c.usuario());p.put("spring.datasource.hikari.password",c.clave());
        p.put("controllocal.almacen.proveedor","DISCO");p.put("controllocal.almacen.directorio",c.almacen().toString());
        p.put("controllocal.token.secreto",c.token());p.put("controllocal.mfa.clave",c.mfa());
        p.put("controllocal.mfa.clave-anterior",c.anterior());p.put("controllocal.mfa.version-clave",c.versionMfa());
        return p;
    }

    private static Resultado preparar(ConfigurableApplicationContext contexto, Configuracion configuracion) throws Exception {
        var jdbc=contexto.getBean(JdbcTemplate.class);
        if(!"controllocal_ci_c4".equals(jdbc.queryForObject("SELECT current_database()",String.class)))throw invalida();
        var almacen=contexto.getBean(AlmacenDocumentos.class);
        if(!"DISCO".equals(almacen.proveedor()))throw invalida();
        var antes=frontera(jdbc);
        var banco=new FormalizacionesDePrueba(contexto);
        // mesa(true) usa C2 manuscrita y financiacion TEST del banco existente.
        // No se llama nuevoEnEquipo/desde: crear evidencia HITO no sirve a esta prueba.
        // Base declarada sólo para estas rentas sintéticas: MENSUAL acredita período,
        // no base. Core captura esta condición antes de firmar, sin un valor por defecto.
        var condicionesDeAlquiler=List.of(new PropiedadUniversalService.ValorAtributo("modalidad_precio","MENSUAL_TOTAL"));
        var alquiler=banco.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",true,condicionesDeAlquiler);
        var venta=banco.negociacion.mesaEnEquipo(alquiler.equipo(),"VENTA",new BigDecimal("150000"),"USD",true);
        var ajena=banco.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",true,condicionesDeAlquiler);
        var perfiles=new ArrayList<Perfil>();
        perfiles.add(perfil(contexto,banco,alquiler,"alquiler1",1));
        perfiles.add(perfil(contexto,banco,venta,"venta4",4));
        perfiles.add(perfil(contexto,banco,ajena,"aislamiento",0));
        var actores=new LinkedHashMap<String,ActorPublico>();
        var tokens=new LinkedHashMap<String,String>();
        var elegidos=new LinkedHashMap<String,Actor>();
        elegidos.put("responsable",alquiler.agente());elegidos.put("colega",alquiler.equipo().colega());
        elegidos.put("broker",alquiler.equipo().broker());elegidos.put("tenantAdmin",alquiler.equipo().admin());elegidos.put("ajeno",ajena.agente());
        // Completar todo MFA antes de emitir: broker y gobierno pueden compartir
        // cuenta y el enrolamiento legitimo revoca las sesiones anteriores.
        for(Actor actor:elegidos.values())banco.sesiones.de(actor);
        for(var entrada:elegidos.entrySet()) {
            var acceso=acceso(contexto,banco,entrada.getValue());
            var firmador=contexto.getBean(TokenService.class);
            String token=firmador.firmar(firmador.de(acceso.acceso()));
            var comprobado=firmador.validar(token).orElseThrow(PreparacionHttpNativoC6::invalida);
            contexto.getBean(RevalidacionDeSesion.class).comprobar(comprobado);
            actores.put(entrada.getKey(),new ActorPublico(entrada.getValue().idOrganizacion(),entrada.getValue().idPersona(),
                    entrada.getValue().idRolOperativo(),acceso.idCuenta(),acceso.acceso().revision()));
            tokens.put(entrada.getKey(),token);
        }
        for(String token:tokens.values())contexto.getBean(RevalidacionDeSesion.class)
                .comprobar(contexto.getBean(TokenService.class).validar(token).orElseThrow(PreparacionHttpNativoC6::invalida));
        if(!antes.equals(frontera(jdbc)))throw new IllegalStateException("El escenario no puede habilitar pagos, perfiles IA o fondos.");
        var publico=new Manifiesto("HTTP-NATIVO-C6-TEST-1",configuracion.corrida(),configuracion.candidato(),
                almacen.identidadTecnica(),SEMILLA,ANCHO,ALTO,List.of("PDF_A4","PNG_VERTICAL"),actores,perfiles);
        return new Resultado(publico,new AccesosPrivados(configuracion.corrida(),tokens));
    }

    private static Perfil perfil(ConfigurableApplicationContext contexto,FormalizacionesDePrueba banco,
            ActividadNegociacionDePrueba.Caso caso,String etiqueta,int cantidad) throws Exception {
        var galerias=contexto.getBean(GaleriaDeFotos.class);
        var fotos=new ArrayList<Foto>();
        var principal=acceso(contexto,banco,caso.agente());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,
                List.of(new SimpleGrantedAuthority("ROLE_"+caso.agente().rolEfectivo()))));
        try {
            for(int numero=1;numero<=cantidad;numero++) {
                byte[] png=imagen(etiqueta,numero);
                var foto=galerias.agregar(caso.propiedad(),"foto-TEST-"+numero+".png",Base64.getEncoder().encodeToString(png),
                        FotoPropiedadService.TOPE_UNIVERSAL,caso.agente());
                String referencia=java.util.UUID.fromString(foto.clave()).toString();
                fotos.add(new Foto(foto.id(),referencia,foto.orden(),huella(png),png.length,ANCHO,ALTO));
            }
        } finally { SecurityContextHolder.clearContext(); }
        var fisica=contexto.getBean(PropiedadUniversalService.class).consultar(caso.propiedad(),caso.agente());
        var encargo=banco.encargos.consultar(caso.encargo(),caso.agente());
        if(!"APROBADO".equals(encargo.estado())||encargo.vigencia()==null||!encargo.vigencia().habilitado())
            throw new IllegalStateException("El banco no habilito el Encargo TEST con su autoridad real.");
        var seleccion=contexto.getBean(PiezasComercialesService.class).consultarFotosPermitidas(caso.encargo(),null,caso.agente());
        if(seleccion.fotos().size()!=cantidad||!seleccion.fotos().stream().map(f->f.referencia().toString()).toList()
                .equals(fotos.stream().map(Foto::referencia).toList()))throw new IllegalStateException("La galeria TEST no conserva su seleccion Core.");
        String ciclo=contexto.getBean(JdbcTemplate.class).queryForObject("SELECT id_ciclo::text FROM b0_ciclo WHERE organizacion_id=? AND cerrado_en IS NULL AND NOT revocado AND desde<=clock_timestamp() AND hasta>clock_timestamp()",String.class,caso.agente().idOrganizacion());
        return new Perfil(etiqueta,caso.operacion(),caso.agente().idOrganizacion(),caso.propiedad(),fisica.versionFisica(),
                caso.encargo(),encargo.version(),seleccion.versionObservada(),ciclo,List.copyOf(fotos));
    }

    private static SesionDeRequest acceso(ConfigurableApplicationContext contexto,FormalizacionesDePrueba banco,Actor actor) {
        ContextoSesion sid=banco.sesiones.de(actor);
        var dto=new TransactionTemplate(contexto.getBean(PlatformTransactionManager.class)).execute(t->
                contexto.getBean(ContextosService.class).exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),actor));
        if(dto==null||dto.restricciones().debeCambiarContrasena()||dto.restricciones().debeEnrolarMfa())throw invalida();
        var principal=new SesionDeRequest(contexto.getBean(TokenService.class).de(dto),dto);
        contexto.getBean(RevalidacionDeSesion.class).comprobar(principal.token());
        return principal;
    }
    private static List<Long> frontera(JdbcTemplate jdbc) {
        return List.of(jdbc.queryForObject("SELECT count(*) FROM b0_acreditacion_pago",Long.class),
                jdbc.queryForObject("SELECT count(*) FROM b0_perfil_operativo",Long.class),
                jdbc.queryForObject("SELECT count(*) FROM b0_fondo_tecnico",Long.class));
    }
    private static byte[] imagen(String etiqueta,int numero) throws Exception {
        var imagen=new BufferedImage(ANCHO,ALTO,BufferedImage.TYPE_INT_RGB);
        var dibujo=imagen.createGraphics();
        try {
            long valor=SEMILLA+31L*etiqueta.hashCode()+numero;
            dibujo.setColor(new Color((int)(valor&0x7f)+64,(int)((valor>>>8)&0x7f)+64,(int)((valor>>>16)&0x7f)+64));
            dibujo.fillRect(0,0,ANCHO,ALTO);dibujo.setColor(Color.WHITE);dibujo.fillRect(100,100,1400,1000);
            dibujo.setColor(Color.DARK_GRAY);dibujo.setFont(new Font(Font.SANS_SERIF,Font.BOLD,72));
            dibujo.drawString("FOTOGRAFIA SINTETICA TEST",160,360);dibujo.drawString(etiqueta+" / "+numero,160,500);
            dibujo.setColor(new Color(30,100+numero*20,160));dibujo.fillRect(160,650,250+numero*160,220);
        } finally {dibujo.dispose();}
        try(var salida=new ByteArrayOutputStream()) {
            if(!ImageIO.write(imagen,"PNG",salida))throw new IllegalStateException("Falta el escritor PNG local.");
            return salida.toByteArray();
        }
    }
    private static String huella(byte[] contenido) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
    }
    private static IllegalArgumentException invalida(){return new IllegalArgumentException("El ensayo requiere contexto nativo C6 TEST exacto y privado.");}

    record Configuracion(String corrida,Path ensayo,String candidato,String url,String usuario,String clave,Path almacen,
                         String token,String mfa,String anterior,short versionMfa) {
        @Override public String toString(){return "Configuracion privada del ensayo HTTP nativo C6";}
    }
    private record Foto(long id,String referencia,int orden,String sha256,long bytes,int ancho,int alto) { }
    private record Perfil(String etiqueta,String operacion,long organizacion,long propiedad,long versionFisica,
                          long encargo,long versionEncargo,String versionGaleria,String ciclo,List<Foto> fotos) { }
    private record ActorPublico(long organizacion,long persona,long rolOperativo,long cuenta,long revisionContexto) { }
    private record Manifiesto(String contrato,String corrida,String candidatoSha256,String almacenHuella,long semilla,
                              int anchoFoto,int altoFoto,List<String> formatos,Map<String,ActorPublico> actores,List<Perfil> perfiles) { }
    private record AccesosPrivados(String corrida,Map<String,String> tokens) {
        @Override public String toString(){return "Accesos privados del ensayo HTTP nativo C6";}
    }
    private record Resultado(Manifiesto publico,AccesosPrivados privado) { }
}
