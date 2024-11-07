package unit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ruitx.server.exceptions.ConnectionException;

import static org.junit.jupiter.api.Assertions.*;

public class ExceptionsUnitTests {

    @Nested
    class ConnectionExceptionTests {
        @Test
        public void testConnectionExceptionWithMessageAndCause() {
            String errorMessage = "Connection failed";
            Throwable cause = new Throwable("Network error");
            ConnectionException exception = new ConnectionException(errorMessage, cause);

            assertEquals(errorMessage, exception.getMessage(), "The exception message should match.");
            assertEquals(cause, exception.getCause(), "The cause of the exception should match.");
        }

        @Test
        public void testConnectionExceptionWithNullCause() {
            String errorMessage = "Connection failed";
            ConnectionException exception = new ConnectionException(errorMessage, null);

            assertEquals(errorMessage, exception.getMessage(), "The exception message should match.");
            assertNull(exception.getCause(), "The cause should be null.");
        }

        @Test
        public void testThrowConnectionException() {
            assertThrows(ConnectionException.class, () -> {
                throw new ConnectionException("Connection failed", new Throwable("Network error"));
            }, "ConnectionException should be thrown when the method is called.");
        }
    }
}

