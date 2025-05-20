package com.example.cobolparser.service;

import com.example.cobolparser.dto.GcsConfig;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class CopybookProcessingServiceTest {

    @Spy
    CopybookProcessingService service = new CopybookProcessingService(); // Assumes default constructor is fine

    @Test
    void loadGcsConfig_validConfig_shouldReturnGcsConfig() throws IOException {
        String validJsonConfig = "{\"copybook\":\"gs://test-bucket/copybook.cpy\",\"outputJson\":\"gs://test-bucket/output.json\"}";
        doReturn(validJsonConfig).when(service).readTextFileFromGcs(anyString());

        GcsConfig config = service.loadGcsConfig("gs://config-bucket/config.json");

        assertNotNull(config);
        assertEquals("gs://test-bucket/copybook.cpy", config.getCopybook());
        assertEquals("gs://test-bucket/output.json", config.getOutputJson());
    }

    @Test
    void loadGcsConfig_invalidJson_shouldThrowIOExceptionWrappingJsonSyntax() throws IOException {
        String invalidJsonConfig = "{\"copybook\":\"gs://test-bucket/copybook.cpy\","; // Malformed JSON
        doReturn(invalidJsonConfig).when(service).readTextFileFromGcs(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.loadGcsConfig("gs://config-bucket/config.json");
        });
        assertTrue(exception.getMessage().contains("Failed to parse GCS config JSON"));
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof JsonSyntaxException);
    }

    @Test
    void loadGcsConfig_missingCopybookField_shouldThrowIOException() throws IOException {
        String jsonConfigMissingField = "{\"outputJson\":\"gs://test-bucket/output.json\"}";
        doReturn(jsonConfigMissingField).when(service).readTextFileFromGcs(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.loadGcsConfig("gs://config-bucket/config.json");
        });
        assertTrue(exception.getMessage().contains("'copybook' field is required"));
    }
    
    @Test
    void loadGcsConfig_emptyCopybookField_shouldThrowIOException() throws IOException {
        String jsonConfigEmptyField = "{\"copybook\":\" \",\"outputJson\":\"gs://test-bucket/output.json\"}";
        doReturn(jsonConfigEmptyField).when(service).readTextFileFromGcs(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.loadGcsConfig("gs://config-bucket/config.json");
        });
        assertTrue(exception.getMessage().contains("'copybook' field is required"));
    }

    @Test
    void loadGcsConfig_missingOutputJsonField_shouldThrowIOException() throws IOException {
        String jsonConfigMissingField = "{\"copybook\":\"gs://test-bucket/copybook.cpy\"}";
        doReturn(jsonConfigMissingField).when(service).readTextFileFromGcs(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.loadGcsConfig("gs://config-bucket/config.json");
        });
        assertTrue(exception.getMessage().contains("'outputJson' field is required"));
    }
    
    @Test
    void loadGcsConfig_emptyOutputJsonField_shouldThrowIOException() throws IOException {
        String jsonConfigEmptyField = "{\"copybook\":\"gs://test-bucket/copybook.cpy\",\"outputJson\":\" \"}";
        doReturn(jsonConfigEmptyField).when(service).readTextFileFromGcs(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.loadGcsConfig("gs://config-bucket/config.json");
        });
        assertTrue(exception.getMessage().contains("'outputJson' field is required"));
    }

    @Test
    void loadGcsConfig_readTextFileFromGcsThrowsIOException_shouldPropagate() throws IOException {
        doThrow(new IOException("GCS read error")).when(service).readTextFileFromGcs(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.loadGcsConfig("gs://config-bucket/config.json");
        });
        assertEquals("GCS read error", exception.getMessage());
    }

    // --- Tests for processCopybookFromConfig ---

    @Test
    void processCopybookFromConfig_successPath_shouldReturnOutputGcsPath() throws Exception {
        GcsConfig mockConfig = new GcsConfig();
        mockConfig.setCopybook("gs://input-bucket/copybook.cpy");
        mockConfig.setOutputJson("gs://output-bucket/schema.json");

        String mockCopybookContent = "01 RECORD.";
        za.co.absa.cobrix.cobol.parser.Copybook mockParsedCopybook = 
            new za.co.absa.cobrix.cobol.parser.Copybook(
                scala.collection.immutable.List$.MODULE$.empty(), // Empty list of statements
                null // Original encoding, not strictly needed for this mock
            ); // Mock a minimal Copybook object
        
        List<Map<String, Object>> mockFieldDetails = new ArrayList<>();
        Map<String, Object> dummyField = new HashMap<>();
        dummyField.put("fieldName", "FIELD-A");
        mockFieldDetails.add(dummyField);

        String mockJsonOutput = "[{\"fieldName\":\"FIELD-A\"}]";

        // Mocking the sequence of calls within processCopybookFromConfig
        doReturn(mockConfig).when(service).loadGcsConfig("gs://config-bucket/config.json");
        doReturn(mockCopybookContent).when(service).readTextFileFromGcs("gs://input-bucket/copybook.cpy");
        doReturn(mockParsedCopybook).when(service).parseCobolWithString(mockCopybookContent);
        doReturn(mockFieldDetails).when(service).extractCobrixFieldDetails(mockParsedCopybook);
        doReturn(mockJsonOutput).when(service).generateJsonString(mockFieldDetails);
        // writeJsonToGcs is void, so no doReturn needed, but verify it's called if desired (later)
        // For now, just ensuring the path is returned.
        // Mockito.doNothing().when(service).writeJsonToGcs(anyString(), anyString()); // If we want to be explicit

        String resultPath = service.processCopybookFromConfig("gs://config-bucket/config.json");

        assertEquals("gs://output-bucket/schema.json", resultPath);
        // Further verifications could include Mockito.verify(service).writeJsonToGcs(mockJsonOutput, "gs://output-bucket/schema.json");
    }

    @Test
    void processCopybookFromConfig_loadConfigFails_shouldThrowException() throws Exception {
        doThrow(new IOException("Failed to load config")).when(service).loadGcsConfig(anyString());

        IOException exception = assertThrows(IOException.class, () -> {
            service.processCopybookFromConfig("gs://config-bucket/invalid_config.json");
        });
        assertEquals("Failed to load config", exception.getMessage());
    }

    @Test
    void processCopybookFromConfig_readCopybookFails_shouldThrowException() throws Exception {
        GcsConfig mockConfig = new GcsConfig();
        mockConfig.setCopybook("gs://input-bucket/nonexistent.cpy");
        mockConfig.setOutputJson("gs://output-bucket/schema.json");
        
        doReturn(mockConfig).when(service).loadGcsConfig(anyString());
        doThrow(new IOException("Failed to read copybook")).when(service).readTextFileFromGcs("gs://input-bucket/nonexistent.cpy");

        IOException exception = assertThrows(IOException.class, () -> {
            service.processCopybookFromConfig("gs://config-bucket/config.json");
        });
        assertEquals("Failed to read copybook", exception.getMessage());
    }
    
    // Add more tests for failures in parseCobolWithString, etc., if needed.
    // Example for parseCobolWithString failure:
    @Test
    void processCopybookFromConfig_parseCobolFails_shouldThrowException() throws Exception {
        GcsConfig mockConfig = new GcsConfig();
        mockConfig.setCopybook("gs://input-bucket/copybook.cpy");
        mockConfig.setOutputJson("gs://output-bucket/schema.json");
        String mockCopybookContent = "01 RECORD.";

        doReturn(mockConfig).when(service).loadGcsConfig(anyString());
        doReturn(mockCopybookContent).when(service).readTextFileFromGcs(anyString());
        doThrow(new za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException("Syntax error", "some_file.cpy", 1, null))
            .when(service).parseCobolWithString(mockCopybookContent);

        za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException exception = 
            assertThrows(za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException.class, () -> {
                service.processCopybookFromConfig("gs://config-bucket/config.json");
            });
        assertEquals("Syntax error", exception.getMessage());
    }
}
