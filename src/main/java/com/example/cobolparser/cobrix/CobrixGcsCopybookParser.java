package com.example.cobolparser.cobrix;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.StorageException;

import java.io.IOException; // Already present, but ensure it's used in main's catch
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List; // Already present, but ensure it's used in main
import java.util.Map;  // Already present, but ensure it's used in main

// Cobrix imports
import za.co.absa.cobrix.cobol.parser.Copybook; // Already present, ensure it's used in main
import za.co.absa.cobrix.cobol.parser.CopybookParser;
import za.co.absa.cobrix.cobol.parser.ast.Statement;
import za.co.absa.cobrix.cobol.parser.ast.Group;
import za.co.absa.cobrix.cobol.parser.ast.Primitive;
import za.co.absa.cobrix.cobol.parser.ast.datatype.Occurs;
import za.co.absa.cobrix.cobol.parser.encoding.Encoding;
import za.co.absa.cobrix.cobol.parser.encoding.codepage.CodePageFactory;
// Consider importing za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException for more specific catch

// Scala converters
import scala.collection.JavaConverters;

// Gson imports
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class CobrixGcsCopybookParser {

    public String readCopybookFromGcs(String gcsPath) throws IOException {
        if (gcsPath == null || !gcsPath.startsWith("gs://")) {
            throw new IllegalArgumentException("Invalid GCS path: Must start with 'gs://'. Path: " + gcsPath);
        }
        String pathWithoutPrefix = gcsPath.substring("gs://".length());
        if (pathWithoutPrefix.isEmpty()) {
            throw new IllegalArgumentException("Invalid GCS path: Path after 'gs://' cannot be empty. Path: " + gcsPath);
        }
        String[] parts = pathWithoutPrefix.split("/", 2);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid GCS path format. Expected gs://bucket-name/object-path. Path: " + gcsPath);
        }
        String bucketName = parts[0];
        String objectName = parts[1];

        Storage storage;
        try {
            storage = StorageOptions.getDefaultInstance().getService();
        } catch (Exception e) {
            throw new IOException("Failed to initialize Google Cloud Storage client: " + e.getMessage(), e);
        }
        
        BlobId blobId = BlobId.of(bucketName, objectName);
        Blob blob = storage.get(blobId);

        if (blob == null || !blob.exists()) {
            throw new IOException("Copybook file not found in GCS at path: " + gcsPath + 
                                  " (Bucket: " + bucketName + ", Object: " + objectName + ")");
        }
        byte[] contentBytes = blob.getContent();
        return new String(contentBytes, StandardCharsets.UTF_8); 
    }

    public Copybook parseCobolWithString(String copybookContents) {
        Encoding utf8Encoding = CodePageFactory.getCodePageByName("UTF-8");
        Copybook parsedCopybook = CopybookParser.parse(copybookContents, utf8Encoding);
        return parsedCopybook;
    }

    public List<Map<String, Object>> extractCobrixFieldDetails(Copybook parsedCopybook) {
        List<Map<String, Object>> allFields = new ArrayList<>();
        if (parsedCopybook == null) {
            return allFields;
        }

        List<Statement> statements = JavaConverters.seqAsJavaList(parsedCopybook.getStatements());
        traverseStatementsRecursive(statements, allFields);
        return allFields;
    }

    private void traverseStatementsRecursive(List<Statement> statements, List<Map<String, Object>> allFields) {
        for (Statement stmt : statements) {
            Map<String, Object> fieldInfo = new HashMap<>();
            fieldInfo.put("level", stmt.getLevel());
            fieldInfo.put("fieldName", stmt.getName());
            if (stmt.getBinaryProperties() != null) {
                 fieldInfo.put("offset", stmt.getBinaryProperties().offset()); 
                 fieldInfo.put("length", stmt.getBinaryProperties().actualSize());
            } else {
                 fieldInfo.put("offset", -1); 
                 fieldInfo.put("length", -1);
            }
            fieldInfo.put("isFiller", stmt.isFiller());

            if (stmt.getRedefines().isDefined()) {
                fieldInfo.put("redefine", stmt.getRedefines().get());
            }

            if (stmt.getOccurs().isDefined()) {
                Occurs occurs = stmt.getOccurs().get();
                fieldInfo.put("occurs", occurs.number()); 
            }
            
            if (stmt instanceof Primitive) {
                Primitive primitive = (Primitive) stmt;
                fieldInfo.put("dataType", primitive.getPic()); 
                fieldInfo.put("fieldCategory", "Primitive");
                allFields.add(fieldInfo); 
            } else if (stmt instanceof Group) {
                Group group = (Group) stmt;
                if (group.getPic() != null && !group.getPic().isEmpty()){
                     fieldInfo.put("dataType", group.getPic()); 
                } else {
                    fieldInfo.put("dataType", ""); 
                }
                fieldInfo.put("fieldCategory", "Group");
                allFields.add(fieldInfo); 
                
                List<Statement> children = JavaConverters.seqAsJavaList(group.getChildren());
                traverseStatementsRecursive(children, allFields);
            } else {
                fieldInfo.put("fieldCategory", "Unknown (" + stmt.getClass().getSimpleName() + ")");
                allFields.add(fieldInfo);
            }
        }
    }

    public String generateJsonString(List<Map<String, Object>> fieldDetailsList) {
        if (fieldDetailsList == null) {
            return "[]"; 
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(fieldDetailsList);
    }

    public void writeJsonToGcs(String jsonString, String gcsOutputPath) throws IOException {
        if (gcsOutputPath == null || !gcsOutputPath.startsWith("gs://")) {
            throw new IllegalArgumentException("Invalid GCS output path: Must start with 'gs://'. Path: " + gcsOutputPath);
        }
        if (jsonString == null) {
            jsonString = ""; 
        }

        String pathWithoutPrefix = gcsOutputPath.substring("gs://".length());
        if (pathWithoutPrefix.isEmpty()) {
            throw new IllegalArgumentException("Invalid GCS output path: Path after 'gs://' cannot be empty. Path: " + gcsOutputPath);
        }

        String[] parts = pathWithoutPrefix.split("/", 2);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid GCS output path format. Expected gs://bucket-name/object-path. Path: " + gcsOutputPath);
        }
        String bucketName = parts[0];
        String objectName = parts[1];

        Storage storage;
        try {
            storage = StorageOptions.getDefaultInstance().getService();
        } catch (Exception e) {
            throw new IOException("Failed to initialize Google Cloud Storage client: " + e.getMessage(), e);
        }
        
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                                    .setContentType("application/json")
                                    .build();
        
        byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        
        try {
            storage.create(blobInfo, jsonBytes);
        } catch (StorageException e) {
            throw new IOException("Failed to write JSON to GCS path: " + gcsOutputPath + ". Error: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        // --- USER ACTION REQUIRED: Update these GCS paths ---
        String copybookGcsPath = "gs://your-bucket-name/path/to/your-copybook.cpy"; // PLEASE UPDATE
        String outputJsonGcsPath = "gs://your-bucket-name/path/to/your-output.json"; // PLEASE UPDATE
        // --- End USER ACTION REQUIRED ---

        // Note: For testing locally without actual GCS, you might need to mock GCS interactions
        // or use a local GCS emulator and configure ADC or client libraries accordingly.
        // The sample_cobrix.cpy file is created in src/test/resources/ for reference.
        // You would typically upload this file to your GCS bucket to use with this main method.

        if (copybookGcsPath.equals("gs://your-bucket-name/path/to/your-copybook.cpy") ||
            outputJsonGcsPath.equals("gs://your-bucket-name/path/to/your-output.json")) {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.out.println("!!! PLEASE UPDATE the placeholder GCS paths in the main method       !!!");
            System.out.println("!!! copybookGcsPath and outputJsonGcsPath before running this example. !!!");
            System.out.println("!!! Example: gs://my-cobol-bucket/copybooks/MYCOPYBOOK.CPY           !!!");
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            return;
        }

        CobrixGcsCopybookParser parser = new CobrixGcsCopybookParser();

        System.out.println("Starting COBOL copybook processing from GCS...");
        System.out.println("Input Copybook GCS Path: " + copybookGcsPath);
        System.out.println("Output JSON GCS Path: " + outputJsonGcsPath);

        try {
            System.out.println("\nStep 1: Reading copybook from GCS path: " + copybookGcsPath);
            String copybookContents = parser.readCopybookFromGcs(copybookGcsPath);
            System.out.println("Successfully read copybook from GCS. Content length: " + (copybookContents == null ? 0 : copybookContents.length()) + " chars.");

            System.out.println("\nStep 2: Parsing COBOL copybook contents with Cobrix...");
            Copybook parsedCopybook = parser.parseCobolWithString(copybookContents);
            System.out.println("Successfully parsed copybook with Cobrix.");

            System.out.println("\nStep 3: Extracting field details from Cobrix schema...");
            List<Map<String, Object>> fieldDetails = parser.extractCobrixFieldDetails(parsedCopybook);
            System.out.println("Successfully extracted " + (fieldDetails == null ? 0 : fieldDetails.size()) + " field entries.");

            System.out.println("\nStep 4: Generating JSON string from extracted details...");
            String jsonOutput = parser.generateJsonString(fieldDetails);
            System.out.println("Successfully generated JSON string. Preview (first 200 chars):");
            System.out.println(jsonOutput == null ? "null" : (jsonOutput.length() > 200 ? jsonOutput.substring(0, 200) + "..." : jsonOutput));

            System.out.println("\nStep 5: Writing JSON output to GCS path: " + outputJsonGcsPath);
            parser.writeJsonToGcs(jsonOutput, outputJsonGcsPath);
            System.out.println("Successfully wrote JSON output to GCS: " + outputJsonGcsPath);

            System.out.println("\nProcess completed successfully!");

        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid argument - " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) { // Catches GCS client init issues, file not found, GCS write issues
            System.err.println("Error during GCS operation or file processing: " + e.getMessage());
            e.printStackTrace();
        } catch (za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException e) { // Specific Cobrix parsing errors
            System.err.println("Error during Cobrix parsing: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) { // Catch other potential runtime exceptions
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
