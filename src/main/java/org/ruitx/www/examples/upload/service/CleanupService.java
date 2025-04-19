package org.ruitx.www.examples.upload.service;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CleanupService {
    private static final String UPLOAD_DIR = "examples/upload/files";
    private static final long CLEANUP_INTERVAL = 5 * 60 * 1000; // 5 minutes

    public void start() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredFiles();
            }
        }, 0, CLEANUP_INTERVAL);
    }

    private void cleanupExpiredFiles() {
        try {
            Mimir db = new Mimir();
            List<Row> expiredUploads = db.getRows(
                "SELECT * FROM UPLOADS WHERE expiry_time <= CURRENT_TIMESTAMP"
            );

            for (Row upload : expiredUploads) {
                String fileName = upload.getString("file_name");
                String id = upload.getString("id");
                
                // Delete file
                Path filePath = Paths.get(UPLOAD_DIR, fileName);
                Files.deleteIfExists(filePath);
                
                // Delete database record
                db.executeSql("DELETE FROM UPLOADS WHERE id = ?", id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 