package unit;

import org.junit.jupiter.api.Test;
import org.ruitx.server.strings.RequestType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestTypeUnitTests {

    @Test
    public void givenValidString_whenFromString_thenCorrectRequestType() {
        // Test each valid string and ensure the correct enum is returned
        assertEquals(RequestType.GET, RequestType.fromString("GET"));
        assertEquals(RequestType.POST, RequestType.fromString("POST"));
        assertEquals(RequestType.PUT, RequestType.fromString("PUT"));
        assertEquals(RequestType.PATCH, RequestType.fromString("PATCH"));
        assertEquals(RequestType.DELETE, RequestType.fromString("DELETE"));
    }

    @Test
    public void givenInvalidString_whenFromString_thenReturnsInvalid() {
        // Test invalid string input
        assertEquals(RequestType.INVALID, RequestType.fromString("INVALID"));
        assertEquals(RequestType.INVALID, RequestType.fromString("INVALID_METHOD"));
        assertEquals(RequestType.INVALID, RequestType.fromString(""));
    }

    @Test
    public void givenValidRequestType_whenToString_thenCorrectString() {
        // Test converting valid RequestType enums back to strings
        assertEquals("GET", RequestType.toString(RequestType.GET));
        assertEquals("POST", RequestType.toString(RequestType.POST));
        assertEquals("PUT", RequestType.toString(RequestType.PUT));
        assertEquals("PATCH", RequestType.toString(RequestType.PATCH));
        assertEquals("DELETE", RequestType.toString(RequestType.DELETE));
    }

    @Test
    public void givenInvalidRequestType_whenToString_thenReturnsInvalid() {
        // Test converting INVALID enum to string (it should return "INVALID")
        assertEquals("INVALID", RequestType.toString(RequestType.INVALID));
    }
}

