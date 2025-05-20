package com.example.cobolparser.service;

import com.example.cobolparser.dto.GcsConfig;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.StorageException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Cobrix imports
import za.co.absa.cobrix.cobol.parser.Copybook;
import za.co.absa.cobrix.cobol.parser.CopybookParser;
import za.co.absa.cobrix.cobol.parser.ast.Statement;
import za.co.absa.cobrix.cobol.parser.ast.Group;
import za.co.absa.cobrix.cobol.parser.ast.Primitive;
import za.co.absa.cobrix.cobol.parser.ast.datatype.Occurs;
import za.co.absa.cobrix.cobol.parser.encoding.Encoding;
import za.co.absa.cobrix.cobol.parser.encoding.codepage.CodePageFactory;
import za.co.absa.cobrix.cobol.parser.exceptions.CobolParserException;


// Scala converters
import scala.collection.JavaConverters;

/**
 * Service class responsible for processing COBOL copybooks.
 * This includes reading files from GCS, parsing copybooks using Cobrix,
 * extracting field details, and writing results back to GCS.
 */
@Service
public class CopybookProcessingService {

    private final Gson gson;

    /**
     * Constructs the service and initializes the Gson parser.
     */
    public CopybookProcessingService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Reads a text file from Google Cloud Storage.
     *
     * @param gcsPath The full GCS path (e.g., "gs://your-bucket-name/path/to/your/file.txt").
     * @return The content of the file as a String.
     * @throws IOException if there's an issue reading from GCS (e.g., file not found, permissions error, GCS client init failure).
     * @throws IllegalArgumentException if the GCS path format is invalid.
     */
    public String readTextFileFromGcs(String gcsPath) throws IOException {
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
            throw new IOException("File not found in GCS at path: " + gcsPath + 
                                  " (Bucket: " + bucketName + ", Object: " + objectName + ")");
        }
        byte[] contentBytes = blob.getContent();
        return new String(contentBytes, StandardCharsets.UTF_8); 
    }

    /**
     * Loads GCS configuration from a JSON file stored in GCS.
     * The configuration specifies paths for the input copybook and output JSON schema.
     *
     * @param gcsConfigPath The GCS path to the configuration JSON file.
     * @return A {@link GcsConfig} object populated from the JSON file.
     * @throws IOException if there's an issue reading the config file from GCS,
     *                     if the JSON is malformed, or if required fields are missing.
     */
    public GcsConfig loadGcsConfig(String gcsConfigPath) throws IOException {
        System.out.println("Loading GCS config from: " + gcsConfigPath);
        String configJsonString = readTextFileFromGcs(gcsConfigPath);
        try {
            GcsConfig config = gson.fromJson(configJsonString, GcsConfig.class);
            if (config == null) { 
                 throw new IOException("Parsed GCS config is null. Check JSON structure and content. Path: " + gcsConfigPath);
            }
            if (config.getCopybook() == null || config.getCopybook().trim().isEmpty()) {
                throw new IOException("Invalid configuration file format: 'copybook' field is required and cannot be empty. Path: " + gcsConfigPath);
            }
            if (config.getOutputJson() == null || config.getOutputJson().trim().isEmpty()) {
                throw new IOException("Invalid configuration file format: 'outputJson' field is required and cannot be empty. Path: " + gcsConfigPath);
            }
            System.out.println("Successfully loaded GCS config. Copybook path: " + config.getCopybook() + ", Output JSON path: " + config.getOutputJson());
            return config;
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse GCS config JSON from path: " + gcsConfigPath + ". Error: " + e.getMessage(), e);
        }
    }

    /**
     * Orchestrates the entire copybook processing workflow.
     * It loads configuration from GCS, reads the copybook from GCS, parses it,
     * extracts field details, generates a JSON string, and writes it back to GCS.
     *
     * @param gcsConfigPath The GCS path to the configuration JSON file.
     * @return The GCS path where the output JSON schema was written.
     * @throws Exception if any step in the process fails (e.g., GCS access, parsing, IO).
     *                   Specific exceptions like {@link IOException}, {@link IllegalArgumentException},
     *                   {@link CobolParserException} might be thrown.
     */
    public String processCopybookFromConfig(String gcsConfigPath) throws Exception {
        System.out.println("Processing copybook based on config from GCS path: " + gcsConfigPath);
        GcsConfig config = loadGcsConfig(gcsConfigPath);

        String copybookPath = config.getCopybook();
        String outputJsonPath = config.getOutputJson();
        
        if (copybookPath == null || copybookPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Copybook path is missing in GCS config.");
        }
        if (outputJsonPath == null || outputJsonPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Output JSON path is missing in GCS config.");
        }
        
        System.out.println("Reading copybook from GCS: " + copybookPath);
        String copybookContents = readTextFileFromGcs(copybookPath); 

        System.out.println("Parsing COBOL copybook with Cobrix...");
        Copybook parsedCopybook = parseCobolWithString(copybookContents); 

        System.out.println("Extracting field details...");
        List<Map<String, Object>> fieldDetails = extractCobrixFieldDetails(parsedCopybook); 

        System.out.println("Generating JSON string...");
        String jsonOutput = generateJsonString(fieldDetails); 

        System.out.println("Writing JSON output to GCS: " + outputJsonPath);
        writeJsonToGcs(jsonOutput, outputJsonPath); 
        
        System.out.println("Successfully processed and wrote JSON to: " + outputJsonPath);
        return outputJsonPath;
    }

    /**
     * Parses a COBOL copybook string using the Cobrix library.
     *
     * @param copybookContents The string content of the COBOL copybook.
     * @return A {@link Copybook} object representing the parsed copybook.
     * @throws CobolParserException if Cobrix fails to parse the string.
     */
    public Copybook parseCobolWithString(String copybookContents) throws CobolParserException {
        Encoding utf8Encoding = CodePageFactory.getCodePageByName("UTF-8");
        Copybook parsedCopybook = CopybookParser.parse(copybookContents, utf8Encoding);
        return parsedCopybook;
    }

    /**
     * Extracts detailed field information from a parsed Cobrix {@link Copybook} object.
     * Traverses the abstract syntax tree (AST) of the copybook.
     *
     * @param parsedCopybook The parsed {@link Copybook} object from Cobrix.
     * @return A list of maps, where each map represents a field and its properties
     *         (e.g., level, name, offset, length, dataType, isFiller, redefine, occurs, fieldCategory).
     *         Returns an empty list if the input copybook is null.
     */
    public List<Map<String, Object>> extractCobrixFieldDetails(Copybook parsedCopybook) {
        List<Map<String, Object>> allFields = new ArrayList<>();
        if (parsedCopybook == null) {
            return allFields;
        }
        List<Statement> statements = JavaConverters.seqAsJavaList(parsedCopybook.getStatements());
        traverseStatementsRecursive(statements, allFields);
        return allFields;
    }

    /**
     * Recursively traverses the statements (AST nodes) of a Cobrix copybook.
     *
     * @param statements The list of {@link Statement} objects to traverse.
     * @param allFields  The list where extracted field information maps are accumulated.
     */
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

    /**
     * Generates a pretty-printed JSON string from a list of field details.
     *
     * @param fieldDetailsList A list of maps, where each map represents a field's properties.
     * @return A pretty-printed JSON string representing the list of field details.
     *         Returns "[]" if the input list is null.
     */
    public String generateJsonString(List<Map<String, Object>> fieldDetailsList) {
        if (fieldDetailsList == null) {
            return "[]"; 
        }
        return gson.toJson(fieldDetailsList);
    }

    /**
     * Writes a JSON string to a specified Google Cloud Storage (GCS) path.
     *
     * @param jsonString The JSON data as a string. If null, an empty string will be written.
     * @param gcsOutputPath The full GCS path for the output file (e.g., "gs://your-bucket/path/output.json").
     * @throws IOException if there's an issue writing to GCS, initializing the GCS client,
     *                     or if the GCS path is invalid.
     * @throws IllegalArgumentException if the GCS output path format is invalid.
     */
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
}
