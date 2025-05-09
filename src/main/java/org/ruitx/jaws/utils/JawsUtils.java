package org.ruitx.jaws.utils;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.stream.Collectors;

public class JawsUtils {

    private JawsUtils() {
    }

    /**
     * Generates a new random password using the specified parameters.
     * The generated password consists of characters within the specified range
     * and the provided number of characters.
     *
     * @param streamSize         the desired length of the password to be generated
     * @param randomNumberOrigin the inclusive lower bound for the range of ASCII values
     *                           used to generate characters
     * @param randomNumberBound  the exclusive upper bound for the range of ASCII values
     *                           used to generate characters
     * @return an {@code Optional<String>} containing the generated password if successful,
     * or an empty {@code Optional} if the password is empty
     */
    public static Optional<String> newPassword(int streamSize, int randomNumberOrigin, int randomNumberBound) {
        StringBuilder password = new StringBuilder()
                .append(new SecureRandom().ints(streamSize, randomNumberOrigin, randomNumberBound)
                        .mapToObj(i -> String.valueOf((char) i))
                        .collect(Collectors.joining()));
        return password.isEmpty() ? Optional.empty() : Optional.of(password.toString());
    }

    /**
     * Generates a new random password with the specified length.
     * The generated password will consist of characters within the predefined range
     * of ASCII values ranging from 33 (inclusive) to 122 (exclusive).
     *
     * @param streamSize the desired length of the password to be generated
     * @return an {@code Optional<String>} containing the generated password if successful,
     * or an empty {@code Optional} if the password is empty
     */
    public static Optional<String> newPassword(int streamSize) {
        int randomNumberOrigin = 33;
        int randomNumberBound = 122;
        return newPassword(streamSize, randomNumberOrigin, randomNumberBound);
    }

    /**
     * Generates a new random password with the default length of 16 characters.
     * The generated password will consist of characters within the predefined range
     * of ASCII values ranging from 33 (inclusive) to 122 (exclusive).
     *
     * @return an {@code Optional<String>} containing the generated password if successful,
     * or an empty {@code Optional} if the password is empty
     */
    public static Optional<String> newPassword() {
        return newPassword(16);
    }
}
