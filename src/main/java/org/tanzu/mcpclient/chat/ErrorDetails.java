package org.tanzu.mcpclient.chat;

import java.time.Instant;
import java.util.Map;

public record ErrorDetails(
        String message,
        String errorType,
        String timestamp,
        String stackTrace,
        Map<String, String> context
) {
    
    public static ErrorDetails of(String message, String errorType, String stackTrace, Map<String, String> context) {
        return new ErrorDetails(
                message,
                errorType,
                Instant.now().toString(),
                stackTrace,
                context
        );
    }
    
    public static ErrorDetails fromException(String userMessage, Exception exception, Map<String, String> context) {
        return new ErrorDetails(
                userMessage,
                exception.getClass().getSimpleName(),
                Instant.now().toString(),
                getStackTraceAsString(exception),
                context
        );
    }
    
    private static String getStackTraceAsString(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}