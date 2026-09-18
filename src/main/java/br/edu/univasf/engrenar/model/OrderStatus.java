package br.edu.univasf.engrenar.model;
public enum OrderStatus {
    OPEN("Aberta"), APPROVED("Aprovado"), REJECTED("Rejeitado"), CLOSED("Fechada"), CANCELLED("Cancelada");
    private final String label;
    OrderStatus(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
