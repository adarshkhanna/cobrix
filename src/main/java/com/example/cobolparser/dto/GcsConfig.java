package com.example.cobolparser.dto;

// If using Gson: import com.google.gson.annotations.SerializedName;
// If using Jackson: import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the structure of the GCS configuration JSON file.
 * This file specifies the GCS paths for the input COBOL copybook
 * and the desired GCS location for the output JSON schema.
 */
public class GcsConfig {
    /**
     * The Google Cloud Storage (GCS) path to the COBOL copybook file (e.g., .cpy, .cob).
     * Example: "gs://your-input-bucket/copybooks/my_copybook.cpy"
     */
    private String copybook;
    
    /**
     * The Google Cloud Storage (GCS) path where the extracted JSON schema
     * (field details) should be written.
     * Example: "gs://your-output-bucket/schemas/my_copybook_schema.json"
     * Note: If the underlying JSON key in the config file is "output-json",
     * Gson/Jackson typically handles the mapping from this camelCase field name.
     * For explicit control, annotations like @SerializedName or @JsonProperty can be used.
     */
    private String outputJson; 

    /**
     * Gets the GCS path to the COBOL copybook file.
     * @return The GCS path string for the copybook.
     */
    public String getCopybook() {
        return copybook;
    }

    /**
     * Sets the GCS path to the COBOL copybook file.
     * @param copybook The GCS path string for the copybook.
     */
    public void setCopybook(String copybook) {
        this.copybook = copybook;
    }

    /**
     * Gets the GCS path for the output JSON schema.
     * @return The GCS path string for the output JSON.
     */
    public String getOutputJson() {
        return outputJson;
    }

    /**
     * Sets the GCS path for the output JSON schema.
     * @param outputJson The GCS path string for the output JSON.
     */
    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }
}
