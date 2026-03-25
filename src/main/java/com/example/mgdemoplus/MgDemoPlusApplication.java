package com.example.mgdemoplus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.mgdemoplus.mapper")
public class MgDemoPlusApplication {

    
    private static final String[] DPGAME_BANNER = {
            "",
            " ██████╗ ██████╗   ██████╗   █████╗ ███╗   ███╗███████╗",
            " ██╔══██╗██╔══██╗ ██╔════╝  ██╔══██╗████╗ ████║██╔════╝",
            " ██║  ██║██████╔╝██║  ███╗ ███████║██╔████╔██║█████╗  ",
            " ██║  ██║██╔═══╝ ██║   ██║ ██╔══██║██║╚██╔╝██║██╔══╝  ",
            " ██████╔╝██║     ╚██████╔╝ ██║  ██║██║ ╚═╝ ██║███████╗",
            " ╚═════╝ ╚═╝      ╚═════╝  ╚═╝  ╚═╝╚═╝     ╚═╝╚══════╝",
            ""
    };

    private static final String[] STARTUP_OK_LINES = {
            "",
            " ╔════════════════════════════════════════════════════╗",
            " ║                                                    ║",
            " ║     ██████╗ ███████╗  █████╗ ██████╗ ██╗   ██╗     ║",
            " ║   ██╔══██╗ ██╔════╝ ██╔══██╗ ██╔══██╗ ╚██╗ ██╔╝    ║",
            " ║     ██████╔╝ █████╗ ███████║ ██║  ██║  ╚████╔╝     ║",
            " ║     ██╔══██╗ ██╔══╝ ██╔══██║ ██║  ██║   ╚██╔╝      ║",
            " ║     ██║  ██║ ███████╗ ██║  ██║ ██████╔╝    ██║     ║",
            " ║     ╚═╝  ╚═╝ ╚══════╝ ╚═╝  ╚═╝ ╚═════╝    ╚═╝      ║",
            " ║                                                    ║",
            " ║     APP READY  -  DPGAME LIVE  -  ENGINES HOT      ║",
            " ║                                                    ║",
            " ╚════════════════════════════════════════════════════╝",
            ""
    };

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MgDemoPlusApplication.class);
        app.setBanner((environment, sourceClass, out) -> {
            for (String line : DPGAME_BANNER) {
                out.println(line);
            }
            String version = environment != null
                    ? environment.getProperty("spring-boot.version", "")
                    : "";
            if (version != null && !version.isEmpty()) {
                out.println(" :: DPGAME :: (Spring Boot " + version + ")");
            } else {
                out.println(" :: DPGAME ::");
            }
            out.println();
        });
        app.run(args);
        for (String line : STARTUP_OK_LINES) {
            System.out.println(line);
        }
    }

}
