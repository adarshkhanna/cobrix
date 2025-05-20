package com.example.cobolparser;

import net.sf.JRecord.External.ExternalRecord;
import net.sf.JRecord.External.ExternalField;
import net.sf.JRecord.IO.CobolIoProvider;
import net.sf.JRecord.IO.ICobolIOBuilder;
import net.sf.JRecord.Common.TypeNames; // For JRecord internal type names

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CopybookParser {

    public String readCopybookFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    public ExternalRecord parseCopybook(String copybookFilePath) throws Exception {
        ICobolIOBuilder ioBuilder = CobolIoProvider.getInstance().newIOBuilder(copybookFilePath);
        ExternalRecord recordSchema = ioBuilder.getExternalRecord();
        return recordSchema;
    }

    public List<Map<String, Object>> extractFieldDetails(ExternalRecord record) {
        List<Map<String, Object>> fieldDetailsList = new ArrayList<>();
        if (record == null) {
            return fieldDetailsList;
        }

        for (int i = 0; i < record.getNumberOfRecordFields(); i++) {
            ExternalField field = record.getRecordField(i);
            Map<String, Object> fieldInfo = new HashMap<>();
            
            fieldInfo.put("level", field.getLevelNumber());
            fieldInfo.put("fieldName", field.getFieldName());
            fieldInfo.put("dataType", field.getPicClause());
            fieldInfo.put("offset", field.getPos() - 1); 
            fieldInfo.put("length", field.getLen());
            
            String redefines = field.getRedefinesFieldName();
            if (redefines != null && !redefines.isEmpty()) {
                fieldInfo.put("redefine", redefines);
            }
            
            int occurs = field.getOccurs();
            if (occurs > 0) {
                fieldInfo.put("occurs", occurs);
            }
            
            fieldInfo.put("jrecordType", TypeNames.getTypeName(field.getType()));

            fieldDetailsList.add(fieldInfo);
        }
        return fieldDetailsList;
    }

    public String generateJsonString(List<Map<String, Object>> fieldDetailsList) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(fieldDetailsList);
    }

    public void writeJsonToFile(List<Map<String, Object>> fieldDetailsList, String outputFilePath) throws IOException {
        String jsonString = generateJsonString(fieldDetailsList);
        Files.write(Paths.get(outputFilePath), jsonString.getBytes());
    }

    public static void main(String[] args) {
        CopybookParser parser = new CopybookParser();
        // Path to the sample copybook file created by the worker/build process
        String copybookFilePath = "src/test/resources/sample.cpy"; 
        String outputJsonPath = "sample_output.json";

        try {
            System.out.println("Parsing copybook: " + copybookFilePath);
            ExternalRecord recordSchema = parser.parseCopybook(copybookFilePath);

            if (recordSchema != null) {
                System.out.println("Successfully parsed copybook: " + recordSchema.getRecordName());
                
                System.out.println("Extracting field details...");
                List<Map<String, Object>> fieldDetails = parser.extractFieldDetails(recordSchema);

                System.out.println("Generating JSON string...");
                String jsonOutput = parser.generateJsonString(fieldDetails);

                System.out.println("------------ Generated JSON Start ------------");
                System.out.println(jsonOutput);
                System.out.println("------------- Generated JSON End -------------");

                parser.writeJsonToFile(fieldDetails, outputJsonPath);
                System.out.println("JSON output successfully written to: " + outputJsonPath);
            } else {
                System.out.println("Failed to parse copybook. ExternalRecord is null.");
            }

        } catch (Exception e) {
            System.err.println("An error occurred during copybook processing:");
            e.printStackTrace();
        }
    }
}
