package br.edu.univasf.engrenar.model;
import java.math.BigDecimal;
public record OrderItem(long id, long orderId, String kind, String description, int quantity, BigDecimal unitPrice, Long partId, boolean completed) {
    public BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
