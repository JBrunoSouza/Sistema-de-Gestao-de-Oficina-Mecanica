package br.edu.univasf.engrenar.model;

import java.time.LocalDate;

/** Null dates/status mean no restriction. Dates refer to entry_date, inclusively. */
public record ReportFilter(LocalDate from, LocalDate to, OrderStatus status, String search) {
    public ReportFilter { search = search == null ? "" : search.strip(); }
}
