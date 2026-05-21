package com.wsb.animaladoption.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseBackupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // @Scheduled(cron = "0 * * * * *") do testów jak coś, backup co minute
    // backup robiony o 2 w nocy:
    @Scheduled(cron = "0 0 2 * * *")
    public void backupDatabase() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // ściezka ewnątrz kontenera Dockera, połączona z PC
        String fileName = "/var/opt/mssql/backups/animaladoption_" + timestamp + ".bak";

        // komenda SQL Servera do tworzenia backupu
        String sql = "BACKUP DATABASE animaladoption TO DISK = '" + fileName + "' WITH FORMAT";

        try {
            log.info("ROZPOCZĘTO TWORZENIE BACKUPU BAZY DANYCH");
            jdbcTemplate.execute(sql);
            log.info("[BACKUP UDANY] Pomyślnie utworzono backup bazy. Plik znajdziesz w folderze backups: {}", fileName);
        } catch (Exception e) {
            log.error("[BACKUP NIEUDANY] Błąd podczas tworzenia backupu: {}", e.getMessage());
        }
    }
}