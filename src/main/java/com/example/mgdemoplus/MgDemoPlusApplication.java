package com.example.mgdemoplus;

import com.example.mgdemoplus.config.LocalDotenvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({
        "com.example.mgdemoplus.common.mapper",
        "com.example.mgdemoplus.history.mapper",
        "com.example.mgdemoplus.lobby.mapper",
        "com.example.mgdemoplus.music.mapper",
        "com.example.mgdemoplus.download.mapper",
        "com.example.mgdemoplus.roomchat.mapper",
        "com.example.mgdemoplus.social.mapper",
        "com.example.mgdemoplus.user.mapper",
        "com.example.mgdemoplus.leaderboard.mapper"
})
@EnableScheduling
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
        LocalDotenvLoader.load();
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
