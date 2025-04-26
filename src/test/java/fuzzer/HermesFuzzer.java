package fuzzer;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import org.ruitx.jaws.components.Hermes;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class HermesFuzzer {
    private static Path tempTemplatePath;
    private static long testCount = 0;

    public static void fuzzerInitialize() throws IOException {
        // Create a temporary directory for template files
        tempTemplatePath = Files.createTempDirectory("fuzz-templates-");
        Logger.info("Fuzzer initialized with temporary template directory at: " + tempTemplatePath);
    }

    public static void fuzzerTearDown() throws IOException {
        // Clean up temporary files
        Files.walk(tempTemplatePath)
             .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
             .forEach(path -> {
                 try {
                     Files.deleteIfExists(path);
                 } catch (IOException e) {
                     Logger.error("Failed to delete temporary file: " + path);
                 }
             });
        Logger.info("Fuzzer completed. Total tests run: " + testCount);
    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        testCount++;
        
        try {
            // Generate random template content
            String template = data.consumeRemainingAsString();
            
            // Create random parameters
            Map<String, String> queryParams = new HashMap<>();
            Map<String, String> bodyParams = new HashMap<>();
            
            // Add some random parameters
            int paramCount = data.consumeInt(0, 5);
            for (int i = 0; i < paramCount; i++) {
                String key = "param" + i;
                String value = data.consumeString(10);
                if (data.consumeBoolean()) {
                    queryParams.put(key, value);
                } else {
                    bodyParams.put(key, value);
                }
            }
            
            // Test template processing
            String result = Hermes.processTemplate(template, queryParams, bodyParams);
            
            // Basic validation of result
            if (result == null) {
                Logger.error("Template processing returned null for input: " + template);
            }
            
            // Test with file-based template
            Path tempFile = tempTemplatePath.resolve("template" + testCount + ".html");
            Files.writeString(tempFile, template);
            String fileResult = Hermes.processTemplate(tempFile.toFile());
            
            if (fileResult == null) {
                Logger.error("File-based template processing returned null for input: " + template);
            }
            
        } catch (IOException e) {
            // Expected for invalid file operations
            Logger.debug("Expected IO exception during fuzzing: " + e.getMessage());
        } catch (Exception e) {
            // Log unexpected exceptions
            Logger.error("Unexpected error during fuzzing: " + e.getMessage());
            Logger.error("Template that caused error: " + data.consumeRemainingAsString());
            e.printStackTrace();
        }
    }
} 