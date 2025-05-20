package com.example.cobolparser.dto;

/**
 * Represents the API request payload for the copybook processing endpoint.
 * It expects a single field 'config' which is the GCS path to the
 * configuration JSON file.
 */
public class ApiRequest {
    /**
     * The Google Cloud Storage (GCS) path to the configuration JSON file.
     * This file specifies the location of the COBOL copybook and the desired
     * output path for the extracted JSON schema.
     * Example: "gs://your-bucket/path/to/your-config.json"
     */
    private String config;

    /**
     * Gets the GCS path to the configuration JSON file.
     * @return The GCS path string.
     */
    public String getConfig() {
        return config;
    }

    /**
     * Sets the GCS path to the configuration JSON file.
     * @param config The GCS path string.
     */
    public void setConfig(String config) {
        this.config = config;
    }
}
