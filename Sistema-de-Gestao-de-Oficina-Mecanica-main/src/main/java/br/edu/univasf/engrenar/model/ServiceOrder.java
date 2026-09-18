package br.edu.univasf.engrenar.model;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
public record ServiceOrder(long id, long customerId, long vehicleId, String complaint, LocalDate entryDate,
        long mileage, String responsible, OrderStatus status, String diagnosis, LocalDateTime decisionAt,
        String decisionBy, BigDecimal budgetTotal, String paymentMethod, LocalDate pickupDate) {
    public String number() { return "OS-" + String.format("%05d", id); }
    @Override public String toString() { return number() + " · " + status; }
}
