package org.ruitx.jaws.utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Collectors;

public class JawsUtils {

    private JawsUtils() {
    }

    /**
     * Formats a given Unix timestamp into a human-readable date string based on the specified format.
     *
     * @param unixTimestamp the Unix timestamp to format, expressed in seconds since the epoch
     * @param format        the date and time format pattern as defined by {@code DateTimeFormatter}
     * @return a formatted date string based on the given Unix timestamp and format
     */
    public static String formatUnixTimestamp(long unixTimestamp, String format) {
        // Convert to seconds if timestamp is in milliseconds
        long timestampInSeconds = (String.valueOf(unixTimestamp).length() > 10)
                ? unixTimestamp / 1000
                : unixTimestamp;

        Instant instant = Instant.ofEpochSecond(timestampInSeconds);
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return zonedDateTime.format(formatter);
    }

    /**
     * Formats a given Unix timestamp into a human-readable date string using the default format.
     *
     * @param unixTimestamp the Unix timestamp to format, expressed in seconds since the epoch
     * @return a formatted date string based on the given Unix timestamp and the default format "yyyy-MM-dd"
     */
    public static String formatUnixTimestamp(long unixTimestamp) {
        return formatUnixTimestamp(unixTimestamp, "yyyy-MM-dd");
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
