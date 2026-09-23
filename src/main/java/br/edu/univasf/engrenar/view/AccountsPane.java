package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

public final class AccountsPane extends VBox {
    public AccountsPane(Window owner,WorkshopService service){
        super(12);Label title=new Label("Usuários");title.getStyleClass().add("page-title");
        ListView<UserInfo> users=new ListView<>();users.setId("users-list");users.getItems().setAll(service.users());VBox.setVgrow(users,Priority.ALWAYS);
        Button create=new Button("Cadastrar usuário");create.setId("create-user");
        create.setOnAction(e->{FxForm f=new FxForm();TextField name=f.text("name","Nome","");TextField username=f.text("username","Usuário (letras minúsculas, números, ponto ou hífen)","");
            PasswordField password=f.field("password","Senha (12 a 128 caracteres)",new PasswordField());
            ComboBox<Role> role=f.field("role","Perfil",new ComboBox<>());role.getItems().setAll(Role.values());role.setValue(Role.ATENDENTE);
            form(owner,"Cadastrar usuário",f,()->{service.createUser(name.getText(),username.getText(),password.getText(),role.getValue());users.getItems().setAll(service.users());});});
        Button reset=new Button("Emitir código de recuperação");reset.setId("reset-user");
        reset.setOnAction(e->{UserInfo selected=users.getSelectionModel().getSelectedItem();if(selected==null)return;
            FxForm f=new FxForm();f.extra(new Label("Usuário: "+selected.username()+". Código válido por 15 minutos e uma única utilização."));
            PasswordField current=f.field("current","Confirme sua senha de gerente",new PasswordField());
            form(owner,"Recuperar acesso",f,()->{String code=service.issueResetCode(selected.username(),current.getText());
                Alert result=new Alert(Alert.AlertType.INFORMATION);result.initOwner(owner);result.setTitle("Código de recuperação");result.setHeaderText("Entregue o código somente ao titular de "+selected.username());
                TextArea token=new TextArea(code);token.setEditable(false);token.setWrapText(true);token.setPrefRowCount(2);result.getDialogPane().setContent(token);result.showAndWait();});});
        getChildren().addAll(title,new Label("Somente gerentes podem criar contas e emitir códigos. Nenhuma senha existente é exibida."),users,new HBox(10,create,reset));
    }
    public static void changePassword(Window owner,WorkshopService service){
        FxForm f=new FxForm();PasswordField current=f.field("current","Senha atual",new PasswordField());
        PasswordField password=f.field("password","Nova senha (12 a 128 caracteres)",new PasswordField());
        PasswordField confirm=f.field("confirm","Repita a nova senha",new PasswordField());
        form(owner,"Alterar minha senha",f,()->{same(password,confirm);service.changePassword(current.getText(),password.getText());});
    }
    public static void recover(Window owner,WorkshopService service){
        FxForm f=new FxForm();f.extra(new Label("Solicite a um gerente um código de recuperação. Validade: 15 minutos."));
        TextField username=f.text("username","Usuário","");TextField code=f.text("code","Código de recuperação","");
        PasswordField password=f.field("password","Nova senha (12 a 128 caracteres)",new PasswordField());
        PasswordField confirm=f.field("confirm","Repita a nova senha",new PasswordField());
        form(owner,"Recuperar senha",f,()->{same(password,confirm);service.recoverPassword(username.getText(),code.getText(),password.getText());});
    }
    private static void same(PasswordField a,PasswordField b){if(!a.getText().equals(b.getText()))throw new ValidationException("confirm","As senhas não coincidem.");}
    private static void form(Window owner,String title,FxForm form,Runnable save){
        Dialog<Void> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle(title);form.finish();dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            try{form.clearErrors();save.run();}catch(ValidationException ex){form.showError(ex);e.consume();}
            catch(RuntimeException ex){form.showError("Não foi possível concluir. Verifique a conexão com o banco.");e.consume();}});
        dialog.showAndWait();
    }
}
