package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.service.WorkshopService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class EngrenarFxApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            Database db = new Database(
                    "jdbc:postgresql://localhost:5430/engrenar-db",
                    true
            );
            WorkshopService service = new WorkshopService(db);

            // 1. Cria a cena inicialmente vazia (com o tamanho padrão da aplicação)
            Scene scene = new Scene(new Pane(), 1200, 800);

            // 2. Carrega o CSS global para que funcione tanto no Login quanto na MainWindow
            var cssUrl = getClass().getResource("/styles/theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            // 3. Define a ação que ocorrerá após o login com sucesso
            Runnable onLoginSuccess = () -> {
                // A MainWindow só pode ser instanciada AQUI DENTRO, depois que o usuário foi autenticado
                MainWindow mainWindow = new MainWindow(primaryStage, service, "");
                scene.setRoot(mainWindow); // Troca a tela de Login pela tela principal
                mainWindow.setupKeyShortcuts(scene);
            };

            // 4. Instancia o LoginForm e o define como a tela inicial
            LoginForm loginForm = new LoginForm(service, onLoginSuccess);
            scene.setRoot(loginForm);

            primaryStage.setTitle("Engrenar | Gestão de Oficina");
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Falha ao iniciar");
            alert.setHeaderText("Não foi possível abrir o Engrenar.");
            alert.setContentText("Confira se o banco de dados PostgreSQL está ativo (docker compose up -d) e acessível.\n\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}