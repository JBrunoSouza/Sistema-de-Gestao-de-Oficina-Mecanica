package br.edu.univasf.engrenar.model;
public record Customer(long id, String name, String phone, String email) {
    @Override public String toString() { return name + " · #" + id; }
}
