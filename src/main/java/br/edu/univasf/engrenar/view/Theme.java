package br.edu.univasf.engrenar.view;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
public final class Theme {
    private Theme() {}
    public static void install() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont",new Font("SansSerif",Font.PLAIN,14));
        UIManager.put("Component.arc",12);
        UIManager.put("Button.arc",12);
        UIManager.put("TextComponent.arc",10);
        UIManager.put("Component.minimumHeight",32);
        UIManager.put("Component.focusColor",new Color(244,139,50));
        UIManager.put("Panel.background",new Color(32,37,44));
        UIManager.put("Table.background",new Color(36,42,50));
        UIManager.put("Table.selectionBackground",new Color(83,67,47));
    }
}
