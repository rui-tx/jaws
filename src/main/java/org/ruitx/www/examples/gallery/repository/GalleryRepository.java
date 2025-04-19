package org.ruitx.www.examples.gallery.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;

import java.util.List;

public class GalleryRepository {
    private final Mimir db;

    public GalleryRepository() {
        this.db = new Mimir();
    }

    public List<Row> getAllImages() {
        return db.getRows("SELECT * FROM GALLERY;");
    }

    public Row getImageById(String imageId) {
        return db.getRow("SELECT * FROM GALLERY WHERE id = ?", imageId);
    }

    public void saveImage(String imageId, String imageData) {
        db.executeSql("INSERT OR REPLACE INTO GALLERY (id, data) VALUES (?, ?)", imageId, imageData);
    }

    public void deleteImage(String imageId) {
        db.executeSql("DELETE FROM GALLERY WHERE id = ?", imageId);
    }
}
