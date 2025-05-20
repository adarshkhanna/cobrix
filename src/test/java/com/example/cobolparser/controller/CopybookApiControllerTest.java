package com.example.cobolparser.controller;

import com.example.cobolparser.dto.ApiRequest;
import com.example.cobolparser.service.CopybookProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper; // For asJsonString helper
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException;

import java.io.FileNotFoundException;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.is; // For jsonPath assertions

@SpringBootTest
@AutoConfigureMockMvc
public class CopybookApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CopybookProcessingService copybookService;

    @Autowired // Autowire ObjectMapper for JSON conversion
    private ObjectMapper objectMapper; 

    // Helper method to convert objects to JSON string
    private String asJsonString(final Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void processCopybook_success_shouldReturn200Ok() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("gs://valid-config/config.json");
        String expectedOutputGcsPath = "gs://output-bucket/schema.json";

        when(copybookService.processCopybookFromConfig(anyString())).thenReturn(expectedOutputGcsPath);

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.message", is("Copybook processed successfully. Output available at GCS.")))
            .andExpect(jsonPath("$.outputGcsPath", is(expectedOutputGcsPath)));
    }

    @Test
    void processCopybook_serviceThrowsIllegalArgument_shouldReturn400BadRequest() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("gs://config-causes-illegal-arg/config.json");
        String errorMessage = "Test illegal argument";

        when(copybookService.processCopybookFromConfig(anyString())).thenThrow(new IllegalArgumentException(errorMessage));

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("Invalid input: " + errorMessage)));
    }

    @Test
    void processCopybook_serviceThrowsFileNotFound_shouldReturn404NotFound() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("gs://config-file-not-found/config.json");
        String errorMessage = "Test file not found";

        when(copybookService.processCopybookFromConfig(anyString())).thenThrow(new FileNotFoundException(errorMessage));

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("File not found: " + errorMessage)));
    }
    
    @Test
    void processCopybook_serviceThrowsCobolParserException_shouldReturn400BadRequest() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("gs://config-bad-copybook/config.json");
        String errorMessage = "Bad COBOL syntax";

        when(copybookService.processCopybookFromConfig(anyString()))
            .thenThrow(new CobolParserException(errorMessage, "file.cpy", 1, null));

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isBadRequest()) // As per GlobalExceptionHandler
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("Error parsing COBOL copybook: " + errorMessage)));
    }

    @Test
    void processCopybook_serviceThrowsIOException_shouldReturn500InternalServerError() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("gs://config-causes-io-error/config.json");
        String errorMessage = "Test IO error";

        when(copybookService.processCopybookFromConfig(anyString())).thenThrow(new IOException(errorMessage));

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isInternalServerError()) // As per GlobalExceptionHandler
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("An IO error occurred: " + errorMessage)));
    }
    
    @Test
    void processCopybook_serviceThrowsGenericException_shouldReturn500InternalServerError() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("gs://config-causes-generic-error/config.json");
        String errorMessage = "Generic runtime error";

        when(copybookService.processCopybookFromConfig(anyString())).thenThrow(new RuntimeException(errorMessage));

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isInternalServerError()) // As per GlobalExceptionHandler
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("An unexpected server error occurred. Please contact support.")));
    }

    @Test
    void processCopybook_invalidApiRequest_configMissing_shouldReturn400BadRequest() throws Exception {
        ApiRequest request = new ApiRequest(); // config field is null

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("Request body or 'config' path is missing or empty.")));
    }

    @Test
    void processCopybook_invalidApiRequest_configEmpty_shouldReturn400BadRequest() throws Exception {
        ApiRequest request = new ApiRequest();
        request.setConfig("   "); // config field is empty after trim

        mockMvc.perform(post("/api/v1/process-copybook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", is("Request body or 'config' path is missing or empty.")));
    }
}
