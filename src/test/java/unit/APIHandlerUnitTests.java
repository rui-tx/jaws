package unit;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.utils.types.Post;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class APIHandlerUnitTests {

    private final ObjectMapper mapper = APIHandler.getMapper();

    @Test
    public void testGenericCallAPI() {
        // Test that the generic callAPI method works with different types
        APIHandler handler = new APIHandler();

        // Create a JavaType for List<TodoJson>
        JavaType todoListType = mapper.getTypeFactory().constructParametricType(List.class, Post.class);

        // This should compile without errors, showing that the method is now generic
        APIResponse<List<Post>> response = handler.callAPI("https://jsonplaceholder.typicode.com/posts", todoListType);

        // We don't actually need to make the API call, just verify it compiles
        System.out.println("[DEBUG_LOG] Generic callAPI method works with List<TodoJson>");
    }

    @Test
    public void testParseDirectJsonArray() throws Exception {
        // Create a test JSON array
        String jsonArray = "[{\"userId\":1,\"id\":1,\"title\":\"Test Title\",\"body\":123}]";

        // Create the JavaType for List<TodoJson>
        JavaType todoListType = mapper.getTypeFactory().constructParametricType(List.class, Post.class);

        // Get access to the private parseResponse method using reflection
        Method parseResponseMethod = APIHandler.class.getDeclaredMethod("parseResponse", String.class, JavaType.class);
        parseResponseMethod.setAccessible(true);

        // Parse the response
        APIResponse<List<Post>> response = (APIResponse<List<Post>>) parseResponseMethod.invoke(new APIHandler(), jsonArray, todoListType);

        // Verify the response
        System.out.println("[DEBUG_LOG] Direct JSON Array Response: " + response);
        assertNotNull(response);
        assertTrue(response.success());
        assertNotNull(response.data());
        assertEquals(1, response.data().size());
        assertEquals("Test Title", response.data().get(0).title());
    }

    @Test
    public void testParseWrappedJsonResponse() throws Exception {
        // Create a test wrapped JSON response
        String wrappedJson = "{\"success\":true,\"code\":\"200 OK\",\"timestamp\":1234567890,\"info\":\"\",\"data\":[{\"userId\":1,\"id\":1,\"title\":\"Test Title\",\"body\":123}]}";

        // Create the JavaType for List<TodoJson>
        JavaType todoListType = mapper.getTypeFactory().constructParametricType(List.class, Post.class);

        // Get access to the private parseResponse method using reflection
        Method parseResponseMethod = APIHandler.class.getDeclaredMethod("parseResponse", String.class, JavaType.class);
        parseResponseMethod.setAccessible(true);

        // Parse the response
        APIResponse<List<Post>> response = (APIResponse<List<Post>>) parseResponseMethod.invoke(new APIHandler(), wrappedJson, todoListType);

        // Verify the response
        System.out.println("[DEBUG_LOG] Wrapped JSON Response: " + response);
        assertNotNull(response);
        assertTrue(response.success());
        assertNotNull(response.data());
        assertEquals(1, response.data().size());
        assertEquals("Test Title", response.data().get(0).title());
    }
}
