package com.example.robotpathplanning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Proqramın giriş nöqtəsi. Spring Boot serverini 8088 portunda qaldırır
 * (bax: application.properties) və hazır olanda brauzeri avtomatik açır (yalnız Windows).
 *
 * Arxitektura:
 *   planning  - yol planlaşdırma alqoritmləri (A*, D* Lite)
 *   sensor    - LiDAR sensoru (robotun xəritəni kəşf etməsi)
 *   fuzzy     - Mamdani fuzzy sistemi (operatorun diqqəti)
 *   model     - xəritə, xana, obyektlər
 *   obstacle  - ssenari zamanı çıxan maneələr (statik və hərəkət edən robot)
 *   scenario  - 4 ssenarinin məntiqi
 *   web       - REST API (simulyasiyanı başlatmaq, fuzzy sorğu) və DTO-lar
 *   websocket - simulyasiya vəziyyətinin brauzerə canlı ötürülməsi
 */
@SpringBootApplication
public class RobotPathPlanningApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotPathPlanningApplication.class, args);
    }

    /** app.open-browser=false olduqda (məs. testlərdə) brauzer açılmır. */
    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        if (!env.getProperty("app.open-browser", Boolean.class, false)) return;
        if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) return;

        // ?v=... hər işə salınmada ünvanı fərqli edir: brauzer köhnə index.html-i keşdən göstərmir
        String url = "http://localhost:" + env.getProperty("local.server.port", "8088")
                + env.getProperty("server.servlet.context-path", "") + "/?v=" + System.currentTimeMillis();
        try {
            new ProcessBuilder("cmd", "/c", "start", url).start();
        } catch (Exception ignored) {
        }
    }
}
