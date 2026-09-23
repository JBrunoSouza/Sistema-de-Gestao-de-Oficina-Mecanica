package br.edu.univasf.engrenar.model;

public record AppUser(long id, String name, String username, String passwordHash, Role role) {
}