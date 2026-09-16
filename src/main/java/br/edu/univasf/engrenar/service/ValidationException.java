package br.edu.univasf.engrenar.service;
import java.util.*;
public final class ValidationException extends RuntimeException {
    private final Map<String,String> fields;
    private final Long existingVehicleId;
    public ValidationException(String field, String message) { this(Map.of(field, message), null); }
    public ValidationException(Map<String,String> fields, Long existingVehicleId) {
        super(String.join("\n", fields.values())); this.fields = Map.copyOf(fields); this.existingVehicleId = existingVehicleId;
    }
    public Map<String,String> fields() { return fields; }
    public Long existingVehicleId() { return existingVehicleId; }
}
