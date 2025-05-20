package com.example.cobolparser;

import net.sf.JRecord.External.ExternalRecord;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CopybookParserTest {

    @Test
    void testParseAndExtractSampleCopybook() throws Exception {
        CopybookParser parser = new CopybookParser();
        String copybookFilePath = "src/test/resources/sample.cpy"; // Path to the sample copybook

        ExternalRecord recordSchema = parser.parseCopybook(copybookFilePath);
        List<Map<String, Object>> actualFieldDetails = parser.extractFieldDetails(recordSchema);

        List<Map<String, Object>> expectedFieldDetails = new ArrayList<>();

        // Define expected structure for sample.cpy
        // Based on sample.cpy:
        // 01 RECORD-LAYOUT.
        //    05 FIELD-A         PIC X(10).
        //    05 FIELD-B         PIC 9(5).
        //    05 FIELD-C         PIC 9(3)V99.
        //    05 FIELD-D         REDEFINES FIELD-C.
        //       10 SUB-FIELD-D1 PIC X(2).
        //       10 SUB-FIELD-D2 PIC 9(3).
        //    05 FIELD-E         PIC X(1) OCCURS 3 TIMES.

        Map<String, Object> fieldA = new HashMap<>();
        fieldA.put("level", "05");
        fieldA.put("fieldName", "FIELD-A");
        fieldA.put("dataType", "X(10)");
        fieldA.put("offset", 0); // 0-based
        fieldA.put("length", 10);
        fieldA.put("jrecordType", "Char"); // Common JRecord type for PIC X
        expectedFieldDetails.add(fieldA);

        Map<String, Object> fieldB = new HashMap<>();
        fieldB.put("level", "05");
        fieldB.put("fieldName", "FIELD-B");
        fieldB.put("dataType", "9(5)");
        fieldB.put("offset", 10);
        fieldB.put("length", 5);
        // JRecord might interpret PIC 9(5) as Zoned Decimal if no COMP/BINARY/PACKED-DECIMAL is specified.
        // Using "Mainframe Zoned Decimal" as a common default. Could also be "Zoned Decimal".
        fieldB.put("jrecordType", "Mainframe Zoned Decimal"); 
        expectedFieldDetails.add(fieldB);

        Map<String, Object> fieldC = new HashMap<>();
        fieldC.put("level", "05");
        fieldC.put("fieldName", "FIELD-C");
        fieldC.put("dataType", "9(3)V99"); // Implied decimal
        fieldC.put("offset", 15);
        fieldC.put("length", 5); // 3 digits for integer, 2 for decimal part
        fieldC.put("jrecordType", "Mainframe Zoned Decimal"); // Common for implied decimal PIC 9
        expectedFieldDetails.add(fieldC);

        Map<String, Object> fieldD = new HashMap<>();
        fieldD.put("level", "05");
        fieldD.put("fieldName", "FIELD-D");
        fieldD.put("dataType", ""); // Redefines usually have empty PIC in JRecord ExternalField
        fieldD.put("redefine", "FIELD-C");
        fieldD.put("offset", 15); // Same as FIELD-C
        fieldD.put("length", 5);   // Same as FIELD-C
        fieldD.put("jrecordType", "Group"); // A field that is redefined is often treated as a group
        expectedFieldDetails.add(fieldD);

        Map<String, Object> subFieldD1 = new HashMap<>();
        subFieldD1.put("level", "10");
        subFieldD1.put("fieldName", "SUB-FIELD-D1");
        subFieldD1.put("dataType", "X(2)");
        subFieldD1.put("offset", 15); // Starts at the beginning of FIELD-D
        subFieldD1.put("length", 2);
        subFieldD1.put("jrecordType", "Char");
        expectedFieldDetails.add(subFieldD1);

        Map<String, Object> subFieldD2 = new HashMap<>();
        subFieldD2.put("level", "10");
        subFieldD2.put("fieldName", "SUB-FIELD-D2");
        subFieldD2.put("dataType", "9(3)");
        subFieldD2.put("offset", 17); // After SUB-FIELD-D1 (15 + 2)
        subFieldD2.put("length", 3);
        subFieldD2.put("jrecordType", "Mainframe Zoned Decimal");
        expectedFieldDetails.add(subFieldD2);

        Map<String, Object> fieldE = new HashMap<>();
        fieldE.put("level", "05");
        fieldE.put("fieldName", "FIELD-E");
        fieldE.put("dataType", "X(1)");
        fieldE.put("offset", 20); // After FIELD-C/FIELD-D (15 + 5)
        fieldE.put("length", 1);   // Length of a single occurrence
        fieldE.put("occurs", 3);
        fieldE.put("jrecordType", "Char"); // Type of the individual item in the array
        expectedFieldDetails.add(fieldE);
        
        // Note: JRecord might create additional fields for arrays (e.g. the array itself as a group).
        // The current extractFieldDetails logic might only list the base element if `getOccurs()` is on the element itself.
        // If the test fails, this is an area to investigate based on actual JRecord output.
        // The prompt implies we list one entry for FIELD-E with an "occurs" attribute.

        assertEquals(expectedFieldDetails, actualFieldDetails, "The extracted field details do not match the expected structure.");
    }
}
