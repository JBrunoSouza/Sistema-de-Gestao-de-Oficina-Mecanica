package br.edu.univasf.engrenar.model;
import java.math.BigDecimal;
public record Part(long id, String name, BigDecimal unitPrice, int stock) {
    @Override public String toString() { return name + " · saldo " + stock; }
}
