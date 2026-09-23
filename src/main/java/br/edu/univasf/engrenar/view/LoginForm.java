package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.service.ValidationException;
import br.edu.univasf.engrenar.service.WorkshopService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class LoginForm extends StackPane {

    public LoginForm(WorkshopService service, Runnable onLoginSuccess) {
        // O fundo escuro baseia-se nas cores do protótipo e no seu CSS
        setStyle("-fx-background-color: #15181d;");

        VBox formContainer = new VBox(20);
        formContainer.setMaxWidth(420);
        formContainer.setAlignment(Pos.CENTER_LEFT);

        // Cabeçalho: Título e a barra laranja
        Label title = new Label("Engrenar | Sistema de gestão\nde oficinas mecânicas");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 28px; -fx-font-weight: bold;");

        Region orangeBar = new Region();
        orangeBar.setMinSize(70, 4);
        orangeBar.setMaxSize(70, 4);
        orangeBar.setStyle("-fx-background-color: #f48b32;");

        VBox header = new VBox(15, title, orangeBar);
        header.setPadding(new Insets(0, 0, 15, 0));

        // Campos de texto com as classes CSS do seu theme.css
        TextField txtUser = new TextField();
        txtUser.setPromptText("Usuário");
        txtUser.setPrefHeight(45);
        txtUser.getStyleClass().add("text-field");

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Senha");
        txtPass.setPrefHeight(45);
        txtPass.getStyleClass().add("text-field");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        // Botão de acesso
        Button btnLogin = new Button("Entrar");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setPrefHeight(45);
        btnLogin.getStyleClass().add("primary-button");

        // Lógica de autenticação e tratamento da ValidationException
        btnLogin.setOnAction(e -> {
            errorLabel.setText("");
            txtUser.getStyleClass().remove("error-field");
            txtPass.getStyleClass().remove("error-field");

            try {
                service.login(txtUser.getText(), txtPass.getText());
                onLoginSuccess.run(); // Alterna o ecrã se a palavra-passe estiver correta
            } catch (ValidationException ex) {
                errorLabel.setText(ex.getMessage());
                txtUser.getStyleClass().add("error-field");
                txtPass.getStyleClass().add("error-field");
            } catch (RuntimeException ex) {
                br.edu.univasf.engrenar.model.Session.logout();
                errorLabel.setText("Não foi possível conectar ao banco. Verifique o PostgreSQL e tente novamente.");
            }
        });

        // Montagem do formulário
        formContainer.getChildren().addAll(header, txtUser, txtPass, btnLogin, errorLabel);
        Button recover=new Button("Esqueci minha senha");recover.setId("recover-password");
        recover.setOnAction(e->AccountsPane.recover(getScene().getWindow(),service));formContainer.getChildren().add(recover);
        btnLogin.setDefaultButton(true);

        // Alinhamento à esquerda com margem, replicando o visual do protótipo
        StackPane.setAlignment(formContainer, Pos.CENTER_LEFT);
        StackPane.setMargin(formContainer, new Insets(0, 0, 0, 100));

        getChildren().add(formContainer);
    }
}
