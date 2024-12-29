package org.ruitx.www.examples.gallery.service;

import org.ruitx.jaws.utils.Row;
import org.ruitx.www.examples.gallery.dto.Image;
import org.ruitx.www.examples.gallery.repository.GalleryRepository;

import java.util.List;

public class GalleryService {

    public List<Image> getAllImages() {
        GalleryRepository galleryRepository = new GalleryRepository();
        List<Row> rows = galleryRepository.getAllImages();
        return rows != null ?
                rows.stream()
                        .map(row ->
                                new Image(
                                        row.getString("id"),
                                        row.getString("url"),
                                        row.getString("description")))
                        .toList()
                : null;
    }

    public Image getImageById(String imageId) {
        GalleryRepository galleryRepository = new GalleryRepository();
        Row row = galleryRepository.getImageById(imageId);
        return row != null ?
                new Image(
                        row.getString("id"),
                        row.getString("url"),
                        row.getString("description"))
                : null;
    }
}
