package org.ruitx.www.examples.gallery.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;

import java.util.List;

public class GalleryRepository {
    public List<Row> getAllImages() {
        Mimir db = new Mimir();
        return db.getRows("SELECT * FROM GALLERY;");
    }

    public Row getImageById(String imageId) {
        Mimir db = new Mimir();
        return db.getRow("SELECT * FROM GALLERY WHERE id = ?", imageId);
    }
}
