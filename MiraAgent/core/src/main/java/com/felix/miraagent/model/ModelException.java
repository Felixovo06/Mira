package com.felix.miraagent.model;

public class ModelException extends RuntimeException {
    private final String providerName;
    private final int statusCode;

    public ModelException(String message, String providerName, int statusCode) {
        super(message);
        this.providerName = providerName;
        this.statusCode = statusCode;
    }

    public ModelException(String message, String providerName, int statusCode, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.statusCode = statusCode;
    }

    public String getProviderName() { return providerName; }
    public int getStatusCode() { return statusCode; }
}
