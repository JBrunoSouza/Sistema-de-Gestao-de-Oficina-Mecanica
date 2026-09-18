package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.service.ValidationException;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Formulario JavaFX que apresenta erros por campo; todas as validacoes ficam no service. */
public final class FxForm extends VBox {
    private final Map<String, Control> fields = new LinkedHashMap<>();
    private final Label error = new Label();

    public FxForm() {
        super(10);
        setPadding(new Insets(16, 20, 16, 20));
        error.getStyleClass().add("form-error-label");
        error.setWrapText(true);
    }

    public <T extends Control> T field(String key, String labelText, T component) {
        fields.put(key, component);
        component.setId(key);
        component.setMaxWidth(Double.MAX_VALUE);

        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");

        VBox group = new VBox(4, label, component);
        getChildren().add(group);
        return component;
    }

    public TextField text(String key, String labelText, String value) {
        TextField tf = new TextField(value);
        return field(key, labelText, tf);
    }

    public TextArea area(String key, String labelText, String value) {
        TextArea area = new TextArea(value);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        return field(key, labelText, area);
    }

    public void extra(Node node) {
        getChildren().add(node);
    }

    public void finish() {
        if (!getChildren().contains(error)) {
            getChildren().add(error);
        }
    }

    public void clearErrors() {
        error.setText("");
        fields.values().forEach(c -> {
            c.getStyleClass().remove("error-field");
            c.setTooltip(null);
        });
    }

    public void showError(ValidationException e) {
        error.setText(e.getMessage());
        boolean focused = false;
        for (Map.Entry<String, String> entry : e.fields().entrySet()) {
            Control c = fields.get(entry.getKey());
            if (c != null) {
                if (!c.getStyleClass().contains("error-field")) {
                    c.getStyleClass().add("error-field");
                }
                c.setTooltip(new Tooltip(entry.getValue()));
                if (!focused) {
                    c.requestFocus();
                    focused = true;
                }
            }
        }
    }

    public void showError(String message) {
        error.setText(message);
    }
}

