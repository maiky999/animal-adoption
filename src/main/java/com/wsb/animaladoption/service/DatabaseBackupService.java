package com.wsb.animaladoption.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("unused")
@Service
public class DatabaseBackupService {

    @Value("${spring.datasource.username:postgres}")
    private String dbUser;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/animal_adoption}")
    private String dbUrl;

    // godzina uruchamiania backupa, ustawiłem 2 w nocy
    @Scheduled(cron = "0 0 2 * * ?")
    public void executeBackup() {
        try {
            // wyciąga nazwe bazy danych z urla
            String dbName = dbUrl.substring(dbUrl.lastIndexOf("/") + 1);

            // formatuje daty do nazwy pliku
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "backup_" + dbName + "_" + timestamp + ".sql"; // wydaje mi sie, ze to tak najbardziej logiczna nazwa

            // tworzy folder na backupy, jakby nie istniały (ofc, że u nas istnieją, odpowiedzialna firma)
            File backupDir = new File("./backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String outputPath = backupDir.getAbsolutePath() + File.separator + backupFileName;

            // cała magia, budowanie polecenia pg_dump
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "pg_dump",
                    "-U", dbUser,
                    "-F", "c", // format kompresowania, podobno custom wydajny i bezpieczniejszy
                    "-b", // dołącza duże obiekty, np. jakby jakieś zdjęcie było w bazie
                    "-v", // większa szczegółowość logów
                    "-f", outputPath,
                    dbName
            );

            // wymaganie ustawionej zmiennej środowiskowej PGPASSWORD
            // albo plik .pgpass, żeby nie pytało o hasło w konsoli
            processBuilder.environment().put("PGPASSWORD", "TWOJE_HASLO_DO_BAZY"); // tu ustaw jakie masz haslo do bazy jak bedziesz testowac

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[BACKUP UDANY] Kopia zapasowa zapisana pomyślnie: " + outputPath);;
            } else {
                System.err.println("[BACKUP NIEUDANY] Coś poszło nie tak. Kod błędu: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[BŁĄD KRYTYCZNY] Wyjątek podczas wykonania backupu: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
