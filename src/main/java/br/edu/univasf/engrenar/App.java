package br.edu.univasf.engrenar;
import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.service.WorkshopService;
import br.edu.univasf.engrenar.view.MainFrame;
import br.edu.univasf.engrenar.view.Theme;
import javax.swing.*;
import java.nio.file.*;

public final class App {
    public static void main(String[] args) {
        Theme.install();
        try {
            Path folder = Path.of(System.getProperty("engrenar.dataDir", "data")).toAbsolutePath();
            Files.createDirectories(folder);
            Database db = new Database(
                    "jdbc:postgresql://localhost:5432/engrenar-db",
                    true
            );
            SwingUtilities.invokeLater(() -> new MainFrame(new WorkshopService(db), folder.toString()).setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"Não foi possível abrir o Engrenar.\nConfira se outra instância está aberta e se a pasta de dados permite gravação.\n\n" + e.getMessage(),"Falha ao iniciar",JOptionPane.ERROR_MESSAGE);
        }
    }
}
