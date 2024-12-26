package org.ruitx.server.utils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.server.controllers.gallery.Image;

import java.util.List;

/**
 * APITypeDefinition is a utility class for defining Java types for API calls.
 */
public class APITypeDefinition {

    private static final ObjectMapper mapper = APIHandler.getMapper();

    /**
     * Type -> Image
     */
    public static final JavaType IMAGE = mapper.getTypeFactory()
            .constructType(Image.class);

    /**
     * Type -> List < Image >
     */
    public static final JavaType IMAGE_LIST = mapper.getTypeFactory()
            .constructParametricType(List.class, Image.class);

}
