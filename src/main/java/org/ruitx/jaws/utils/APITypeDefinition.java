package org.ruitx.jaws.utils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.utils.types.ExternalTodo;
import org.ruitx.jaws.utils.types.Image;

import java.util.List;

/**
 * APITypeDefinition is a utility class for defining Java types for API calls.
 */
public class APITypeDefinition {

    private static final ObjectMapper mapper = APIHandler.getMapper();

    /**
     * Type -> List < Todos >
     */
    public static final JavaType EXTERNALTODO_LIST = mapper.getTypeFactory()
            .constructParametricType(List.class, ExternalTodo.class);
    /**
     * Type -> List < Image >
     */
    public static final JavaType IMAGE_LIST = mapper.getTypeFactory()
            .constructParametricType(List.class, Image.class);

    private APITypeDefinition() {
    }

}
