package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.WorkshopService;
import br.edu.univasf.engrenar.view.MainFrame;
import br.edu.univasf.engrenar.view.Theme;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Teste opt-in: opera os componentes reais do Swing, com H2 em arquivo temporario. */
@EnabledIfSystemProperty(named="engrenar.uiTest",matches="true")
class DesktopFlowTest {
    @TempDir Path dir;
    private MainFrame frame;
    private WorkshopService service;
    @BeforeEach void setup() throws Exception {
        service=new WorkshopService(new Database("jdbc:h2:file:"+dir.resolve("ui").toString().replace('\\','/'),true));
        edt(()->{Theme.install();frame=new MainFrame(service,dir.toString());frame.setVisible(true);return null;});
    }
    @AfterEach void close() throws Exception {edt(()->{for(Window w:Window.getWindows())w.dispose();return null;});}
    private <T> T edt(Callable<T> action) throws Exception {FutureTask<T> task=new FutureTask<>(action);SwingUtilities.invokeLater(task);return task.get(15,TimeUnit.SECONDS);}
    private <T extends Component> T find(Container root,String name,Class<T> type) {
        for(Component c:root.getComponents()){if(name.equals(c.getName())&&type.isInstance(c))return type.cast(c);if(c instanceof Container container){T found=find(container,name,type);if(found!=null)return found;}}return null;
    }
    private void click(Container root,String name) throws Exception {edt(()->{AbstractButton b=find(root,name,AbstractButton.class);assertNotNull(b,name);assertTrue(b.isEnabled(),name);b.doClick();return null;});}
    private void launch(Container root,String name) {SwingUtilities.invokeLater(()->{AbstractButton b=find(root,name,AbstractButton.class);assertNotNull(b,name);b.doClick();});}
    private JDialog dialog(String title) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<until){JDialog d=edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog j&&j.isShowing()&&title.equals(j.getTitle()))return j;return null;});if(d!=null)return d;Thread.sleep(40);}throw new AssertionError("Janela ausente: "+title);
    }
    private void text(Container root,String name,String value) throws Exception {edt(()->{JTextComponent c=find(root,name,JTextComponent.class);assertNotNull(c,name);c.setText(value);return null;});}
    private void select(Container root,String name,int index) throws Exception {edt(()->{find(root,name,JComboBox.class).setSelectedIndex(index);return null;});}
    private void capture(Container root,String name) throws Exception {edt(()->{Path p=Path.of("target","screenshots",name+".png");Files.createDirectories(p.getParent());BufferedImage image=new BufferedImage(root.getWidth(),root.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();root.printAll(g);g.dispose();ImageIO.write(image,"png",p.toFile());return null;});}

    @Test void actualScreensSupportNestedRegistrationOpenOrderAndApproval() throws Exception {
        capture(frame,"01-dashboard");click(frame,"nav-Veículos");launch(frame,"new-vehicle");JDialog v=dialog("Cadastrar veículo");
        launch(v,"missing-customer");JDialog c=dialog("Cadastrar cliente");text(c,"name","Cliente da apresentação");text(c,"phone","87999990000");text(c,"email","demo@example.com");click(c,"save-customer");
        text(v,"plate","JKL2M34");text(v,"brand","Volkswagen");text(v,"model","Polo");text(v,"year","2023");text(v,"mileage","-1");click(v,"save-vehicle");
        assertEquals("error",edt(()->find(v,"mileage",JTextComponent.class).getClientProperty("JComponent.outline")));assertEquals(3,service.vehicles().size());capture(v,"02-validacao-veiculo");
        text(v,"mileage","12000");click(v,"save-vehicle");assertEquals(4,service.vehicles().size());
        click(frame,"nav-Ordens de serviço");launch(frame,"new-order");JDialog o=dialog("Abrir ordem de serviço");
        edt(()->{JComboBox<?> box=find(o,"customer",JComboBox.class);for(int i=0;i<box.getItemCount();i++)if(((Customer)box.getItemAt(i)).name().equals("Cliente da apresentação"))box.setSelectedIndex(i);return null;});select(o,"vehicle",0);
        text(o,"complaint","Revisão e ruído no motor");text(o,"responsible","Matheus");capture(o,"03-abertura-os");click(o,"save-order");
        ServiceOrder saved=service.orders().get(0);assertEquals(OrderStatus.OPEN,saved.status());
        edt(()->{find(frame,"order-tabs",JTabbedPane.class).setSelectedIndex(1);return null;});assertNull(edt(()->find(frame,"approve-budget",AbstractButton.class)));capture(frame,"04-orcamento-sem-itens");click(frame,"go-items");
        launch(frame,"add-service");JDialog item=dialog("Adicionar serviço");text(item,"description","Revisão preventiva");text(item,"price","120,50");text(item,"quantity","2");click(item,"save-service");
        launch(frame,"consume-part");JDialog part=dialog("Vincular peça à OS");select(part,"part",0);text(part,"quantity","1");click(part,"save-consumption");
        edt(()->{find(frame,"order-tabs",JTabbedPane.class).setSelectedIndex(1);return null;});text(frame,"decisionBy","Matheus");capture(frame,"05-orcamento");assertTrue(edt(()->find(frame,"budget-items",JTable.class).getVisibleRect().height)>80,"Tabela deve ficar visível junto aos totais");click(frame,"approve-budget");
        assertEquals(OrderStatus.APPROVED,service.order(saved.id()).status());assertEquals("Matheus",service.order(saved.id()).decisionBy());capture(frame,"06-orcamento-aprovado");
    }
    @Test void duplicateAndExistingOrderAreVisibleAndRejectionIsSaved() throws Exception {
        click(frame,"nav-Veículos");launch(frame,"new-vehicle");JDialog v=dialog("Cadastrar veículo");text(v,"plate","ABC1D23");text(v,"brand","Fiat");text(v,"model","Argo");text(v,"year","2021");text(v,"mileage","42000");select(v,"customer",0);
        launch(v,"save-vehicle");JDialog duplicate=dialog("Veículo já cadastrado");capture(duplicate,"07-placa-duplicada");edt(()->{duplicate.dispose();v.dispose();return null;});
        click(frame,"nav-Ordens de serviço");launch(frame,"new-order");JDialog o=dialog("Abrir ordem de serviço");select(o,"customer",0);select(o,"vehicle",0);text(o,"complaint","Ruído");text(o,"responsible","Ana");click(o,"save-order");assertEquals(4,service.orders().size());capture(o,"08-os-ativa-bloqueada");edt(()->{o.dispose();return null;});
        edt(()->{JTable list=find(frame,"orders-table",JTable.class);for(int r=0;r<list.getRowCount();r++)if("OS-00002".equals(list.getValueAt(r,0)))list.setRowSelectionInterval(r,r);return null;});click(frame,"open-order");
        launch(frame,"add-service");JDialog item=dialog("Adicionar serviço");text(item,"description","Alinhamento");text(item,"price","90");click(item,"save-service");edt(()->{find(frame,"order-tabs",JTabbedPane.class).setSelectedIndex(1);return null;});text(frame,"decisionBy","Thiago");click(frame,"reject-budget");assertEquals(OrderStatus.REJECTED,service.order(2).status());capture(frame,"09-orcamento-rejeitado");
    }
}
