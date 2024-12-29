package unit;

import org.junit.jupiter.api.Test;
import org.ruitx.jaws.strings.ResponseCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ResponseCodeUnitTests {

    @Test
    public void givenValidResponseCode_whenGetCode_thenCorrectCode() {
        // Test for each response code ensuring correct status code is returned
        assertEquals(200, ResponseCode.OK.getCode());
        assertEquals(201, ResponseCode.CREATED.getCode());
        assertEquals(404, ResponseCode.NOT_FOUND.getCode());
        assertEquals(500, ResponseCode.INTERNAL_SERVER_ERROR.getCode());
        assertEquals(403, ResponseCode.FORBIDDEN.getCode());
    }

    @Test
    public void givenValidResponseCode_whenGetMessage_thenCorrectMessage() {
        // Test for each response code ensuring correct message is returned
        assertEquals("OK", ResponseCode.OK.getMessage());
        assertEquals("CREATED", ResponseCode.CREATED.getMessage());
        assertEquals("NOT FOUND", ResponseCode.NOT_FOUND.getMessage());
        assertEquals("INTERNAL SERVER ERROR", ResponseCode.INTERNAL_SERVER_ERROR.getMessage());
        assertEquals("FORBIDDEN", ResponseCode.FORBIDDEN.getMessage());
    }

    @Test
    public void givenResponseCode_whenToString_thenCorrectStringFormat() {
        // Test for each response code ensuring correct toString format (code + message)
        assertEquals("200 OK", ResponseCode.OK.toString());
        assertEquals("201 CREATED", ResponseCode.CREATED.toString());
        assertEquals("404 NOT FOUND", ResponseCode.NOT_FOUND.toString());
        assertEquals("500 INTERNAL SERVER ERROR", ResponseCode.INTERNAL_SERVER_ERROR.toString());
        assertEquals("403 FORBIDDEN", ResponseCode.FORBIDDEN.toString());
    }

    @Test
    public void givenInvalidCode_whenGetCode_thenDoesNotExist() {
        assertNull(ResponseCode.fromCode(600));
    }

    @Test
    public void givenInvalidString_whenFromString_thenReturnsNull() {
        assertNull(ResponseCode.fromString("INVALID"));
    }
}
