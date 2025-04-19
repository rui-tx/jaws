package org.ruitx.www.examples.upload.service;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;
import org.tinylog.Logger;
import static org.ruitx.jaws.configs.ApplicationConfig.UPLOAD_DIR;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CleanupService {
    private final Mimir db;

    public CleanupService() {
        this.db = new Mimir();
    }

    public void cleanup() {
        try {
            
            List<Row> expiredUploads = db.getRows(
                "SELECT * FROM UPLOADS WHERE expiry_time <= ?",
                System.currentTimeMillis()
            );
            
            if (expiredUploads.isEmpty()) {
                return;
            }

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
            Logger.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }
} 