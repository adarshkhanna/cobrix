package com.example.cobolparser.advice;

import com.example.cobolparser.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException;

import java.io.FileNotFoundException;
import java.io.IOException;

// For a production application, replace System.err with a proper SLF4J logger
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Example: private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        // log.error("Illegal argument error: {}", e.getMessage(), e); // Example with SLF4J
        System.err.println("GlobalExceptionHandler: Caught IllegalArgumentException - " + e.getMessage());
        ApiResponse errorResponse = new ApiResponse(false, "Invalid input provided: " + e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiResponse> handleFileNotFoundException(FileNotFoundException e) {
        // log.error("File not found error: {}", e.getMessage(), e);
        System.err.println("GlobalExceptionHandler: Caught FileNotFoundException - " + e.getMessage());
        ApiResponse errorResponse = new ApiResponse(false, "Requested file not found: " + e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(CobolParserException.class)
    public ResponseEntity<ApiResponse> handleCobolParserException(CobolParserException e) {
        // log.error("COBOL Parser error: {}", e.getMessage(), e);
        System.err.println("GlobalExceptionHandler: Caught CobolParserException - " + e.getMessage());
        ApiResponse errorResponse = new ApiResponse(false, "Error parsing COBOL copybook: " + e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // This handler for IOException must be after more specific IOExceptions like FileNotFoundException
    // to ensure they are caught by their specific handlers first.
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse> handleIOException(IOException e) {
        // log.error("IO error: {}", e.getMessage(), e);
        System.err.println("GlobalExceptionHandler: Caught IOException - " + e.getMessage());
        // Distinguish between client-caused IO errors and server-side ones if possible.
        // For now, treating generic IO as server error.
        ApiResponse errorResponse = new ApiResponse(false, "An IO error occurred: " + e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception e) {
        // log.error("Unexpected server error: {}", e.getMessage(), e);
        System.err.println("GlobalExceptionHandler: Caught generic Exception - " + e.getMessage());
        e.printStackTrace(); // Good to have for unexpected errors.
        ApiResponse errorResponse = new ApiResponse(false, "An unexpected server error occurred. Please contact support.");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
