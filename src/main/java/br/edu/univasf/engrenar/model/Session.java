package br.edu.univasf.engrenar.model;

public final class Session {
    private static AppUser currentUser;

    private Session() {
        // Construtor privado para evitar que a classe seja instanciada com "new Session()"
    }

    public static void login(AppUser user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static AppUser getUser() {
        return currentUser;
    }
}