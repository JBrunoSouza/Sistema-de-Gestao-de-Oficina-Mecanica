package br.edu.univasf.engrenar.model;
import java.math.BigDecimal;
import java.util.List;
public record Budget(ServiceOrder order, List<OrderItem> items, BigDecimal services, BigDecimal parts, BigDecimal total) {
    public Budget { items = List.copyOf(items); }
}
