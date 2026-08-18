package com.kairos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KAIROS: el asistente conversacional sobre las capacidades publicas de BROX.
 *
 * <p>Arranca, se reinicia y se despliega <b>solo</b>. No comparte proceso, ni
 * base de datos, ni jar con BROX; comparte su contrato.
 */
@SpringBootApplication
public class KairosApplication {

    public static void main(String[] args) {
        SpringApplication.run(KairosApplication.class, args);
    }
}
