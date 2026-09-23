package br.edu.univasf.engrenar.model;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record OrderReport(ReportFilter filter, LocalDateTime generatedAt, List<Row> rows) {
    public OrderReport { rows = List.copyOf(rows); }
    public record Row(long id, String customer, String plate, LocalDate entryDate,
                      OrderStatus status, BigDecimal agreedTotal) {
        public String number() { return "OS-" + String.format("%05d", id); }
    }
    public BigDecimal receivedTotal() {
        return rows.stream().filter(r -> r.status() == OrderStatus.CLOSED)
            .map(Row::agreedTotal).filter(java.util.Objects::nonNull)
            .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }
    public long closedCount() { return rows.stream().filter(r -> r.status() == OrderStatus.CLOSED).count(); }
}
