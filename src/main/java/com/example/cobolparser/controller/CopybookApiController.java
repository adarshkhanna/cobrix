package com.example.cobolparser.controller;

import com.example.cobolparser.dto.ApiRequest;
import com.example.cobolparser.dto.ApiResponse;
import com.example.cobolparser.service.CopybookProcessingService; 

import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.http.HttpStatus; 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException; 
import za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException;


/**
 * REST Controller for handling COBOL copybook processing requests.
 * Provides an API endpoint to trigger the parsing of a copybook stored in GCS,
 * with the resulting schema also written to GCS.
 */
@RestController
@RequestMapping("/api/v1")
public class CopybookApiController {

    private final CopybookProcessingService copybookService;

    /**
     * Constructs the controller and injects the required service.
     * @param copybookService The service responsible for the core copybook processing logic.
     */
    @Autowired 
    public CopybookApiController(CopybookProcessingService copybookService) {
        this.copybookService = copybookService;
    }

    /**
     * Processes a COBOL copybook based on a configuration file stored in GCS.
     * The configuration file specifies the GCS path to the copybook and the
     * GCS path where the extracted JSON schema should be written.
     *
     * @param apiRequest The API request payload containing the GCS path to the configuration file.
     *                   See {@link ApiRequest} for details.
     * @return A {@link ResponseEntity} containing an {@link ApiResponse}.
     *         On success, the ApiResponse indicates success and includes the GCS path to the output JSON.
     *         On failure, the ApiResponse indicates failure and includes an error message.
     *         Possible HTTP status codes:
     *         <ul>
     *             <li>200 OK: Processing successful.</li>
     *             <li>400 Bad Request: Invalid input (e.g., missing config path, malformed JSON, parsing error).</li>
     *             <li>404 Not Found: A specified GCS file (config or copybook) was not found.</li>
     *             <li>500 Internal Server Error: An unexpected error occurred during processing.</li>
     *         </ul>
     */
    @PostMapping("/process-copybook")
    public ResponseEntity<ApiResponse> processCopybook(@RequestBody ApiRequest apiRequest) {
        System.out.println("Received request for /process-copybook");

        if (apiRequest == null || apiRequest.getConfig() == null || apiRequest.getConfig().trim().isEmpty()) {
            System.err.println("Error: Request body or 'config' path is missing or empty.");
            ApiResponse errorResponse = new ApiResponse(false, "Request body or 'config' path is missing or empty.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        String gcsConfigPath = apiRequest.getConfig();
        System.out.println("Processing request for GCS config path: " + gcsConfigPath);

        try {
            String outputJsonGcsPath = copybookService.processCopybookFromConfig(gcsConfigPath);
            ApiResponse successResponse = new ApiResponse(true, "Copybook processed successfully. Output available at GCS.", outputJsonGcsPath);
            System.out.println("Successfully processed. Output at: " + outputJsonGcsPath);
            return ResponseEntity.ok(successResponse);
        } catch (IllegalArgumentException e) { 
            System.err.println("Error: Invalid argument - " + e.getMessage());
            ApiResponse errorResponse = new ApiResponse(false, "Invalid input: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (java.io.FileNotFoundException e) { 
            System.err.println("Error: File not found - " + e.getMessage());
            ApiResponse errorResponse = new ApiResponse(false, "File not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (CobolParserException e) { 
             System.err.println("Error: Cobrix parsing failed - " + e.getMessage());
            ApiResponse errorResponse = new ApiResponse(false, "COBOL parsing error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse); 
        }
        catch (IOException e) { 
            System.err.println("Error: IOException during processing - " + e.getMessage());
            ApiResponse errorResponse = new ApiResponse(false, "IO Error during processing: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
        catch (Exception e) { 
            System.err.println("Error: An unexpected error occurred - " + e.getMessage());
            e.printStackTrace(); 
            ApiResponse errorResponse = new ApiResponse(false, "An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
