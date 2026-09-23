package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.service.ValidationException;
import javax.swing.*;
import java.awt.*;
import java.util.*;

/** Formulario apresenta erros por campo; todas as validacoes ficam no service. */
final class Form extends JPanel {
    private final Map<String,JComponent> fields = new LinkedHashMap<>();
    private final JTextArea error = new JTextArea();
    private int row;
    Form() { super(new GridBagLayout()); setBorder(BorderFactory.createEmptyBorder(16,20,16,20)); }
    <T extends JComponent> T field(String key, String label, T component) {
        fields.put(key, component); component.setName(key);
        JLabel text = new JLabel(label); text.setLabelFor(component);
        GridBagConstraints c = new GridBagConstraints(); c.gridx=0; c.gridy=row++; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; c.insets=new Insets(5,0,5,0);
        add(text,c); c.gridy=row++; c.insets=new Insets(0,0,8,0); add(component,c); return component;
    }
    JTextField text(String key,String label,String value) { return field(key,label,new JTextField(value,28)); }
    JTextArea area(String key,String label,String value) {
        JTextArea area = new JTextArea(value,3,35); area.setLineWrap(true); area.setWrapStyleWord(true); area.setName(key);
        field(key,label,new JScrollPane(area)); fields.put(key,area); return area;
    }
    void extra(JComponent component) {
        GridBagConstraints c=new GridBagConstraints(); c.gridx=0; c.gridy=row++; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; c.insets=new Insets(5,0,5,0); add(component,c);
    }
    void finish() { error.setEditable(false); error.setLineWrap(true); error.setWrapStyleWord(true); error.setForeground(new Color(255,146,146)); error.setOpaque(false); error.setRows(3); extra(error); }
    void clearErrors() { error.setText(""); fields.values().forEach(c -> { c.putClientProperty("JComponent.outline",null); c.setToolTipText(null); }); }
    void showError(ValidationException e) {
        error.setText(e.getMessage());
        e.fields().forEach((key,message)->{ JComponent c=fields.get(key); if(c!=null) { c.putClientProperty("JComponent.outline","error"); c.setToolTipText(message); c.requestFocusInWindow(); } });
    }
    void showError(String message) { error.setText(message); }
}
