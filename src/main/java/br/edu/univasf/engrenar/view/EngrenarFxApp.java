package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.service.WorkshopService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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

            MainWindow root = new MainWindow(primaryStage, service, "");
            Scene scene = new Scene(root, 1200, 800);

            var cssUrl = getClass().getResource("/styles/theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            root.setupKeyShortcuts(scene);

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

