package org.ruitx.jaws.types;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.components.Odin;

import java.util.List;

/**
 * The TypeDefinition class provides predefined type definitions for use with the API handling system.
 * These definitions are used to standardize and simplify type declarations for serialization and deserialization
 * of API responses.
 * <p>
 * This class relies on the ObjectMapper instance provided by the APIHandler class to construct and manage type definitions.
 * It is designed to be a utility class, offering static constants for frequently used type specifications.
 * <p>
 * The class is non-instantiable as it is intended solely for defining reusable type constants.
 */
public class TypeDefinition {

    private static final ObjectMapper mapper = Odin.getMapper();

    /**
     * JavaType -> {@code Post}
     */
    public static final JavaType POST = mapper.getTypeFactory()
            .constructType(Post.class);

    /**
     * JavaType -> {@code List<Post>}
     */
    public static final JavaType LIST_POST = mapper.getTypeFactory()
            .constructParametricType(List.class, Post.class);

    private TypeDefinition() {
    }

}
