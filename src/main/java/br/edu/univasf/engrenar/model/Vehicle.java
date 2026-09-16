package br.edu.univasf.engrenar.model;
public record Vehicle(long id, String plate, String brand, String model, long mileage, int year, long customerId) {
    @Override public String toString() { return plate + " · " + brand + " " + model + " · " + mileage + " km"; }
}
