package org.ruitx.www.examples.gallery.service;

import org.ruitx.jaws.interfaces.Transactional;
import org.ruitx.jaws.utils.Row;
import org.ruitx.www.examples.gallery.dto.Image;
import org.ruitx.www.examples.gallery.repository.GalleryRepository;
import org.tinylog.Logger;

import java.util.List;

public class GalleryService {
    private final GalleryRepository galleryRepository;

    public GalleryService() {
        this.galleryRepository = new GalleryRepository();
    }

    @Transactional(readOnly = true)
    public List<Image> getAllImages() {
        List<Row> rows = galleryRepository.getAllImages();
        return galleryRepository.getAllImages() != null ?
                rows.stream()
                        .map(row ->
                                new Image(
                                        row.getString("id"),
                                        row.getString("url"),
                                        row.getString("description")))
                        .toList()
                : null;
    }

    @Transactional(readOnly = true)
    public Image getImageById(String imageId) {
        Row row = galleryRepository.getImageById(imageId);
        return row != null ?
                new Image(
                        row.getString("id"),
                        row.getString("url"),
                        row.getString("description"))
                : null;
    }

    @Transactional
    public void saveImage(String imageId, String imageData) {
        try {
            galleryRepository.saveImage(imageId, imageData);
            Logger.info("Image saved successfully: {}", imageId);
        } catch (Exception e) {
            Logger.error("Failed to save image: {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteImage(String imageId) {
        try {
            galleryRepository.deleteImage(imageId);
            Logger.info("Image deleted successfully: {}", imageId);
        } catch (Exception e) {
            Logger.error("Failed to delete image: {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void updateImage(String imageId, String newImageData) {
        try {
            // First check if image exists
            Row existingImage = galleryRepository.getImageById(imageId);
            if (existingImage == null) {
                throw new IllegalArgumentException("Image not found: " + imageId);
            }
            
            // Update the image
            galleryRepository.saveImage(imageId, newImageData);
            Logger.info("Image updated successfully: {}", imageId);
        } catch (Exception e) {
            Logger.error("Failed to update image: {}", e.getMessage());
            throw e;
        }
    }
}
