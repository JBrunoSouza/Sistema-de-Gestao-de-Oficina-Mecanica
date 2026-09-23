package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.WorkshopService;
import br.edu.univasf.engrenar.view.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaFxAuditTest {
    static void waitFx(java.util.function.BooleanSupplier condition)throws Exception {
        for(int i=0;i<200;i++) {boolean[] ready={false};fx(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(50);}
        fail("JavaFX report did not finish within 10 seconds");
    }
    static final KeyCodeCombination VEHICLES=new KeyCodeCombination(KeyCode.V,KeyCombination.CONTROL_DOWN);
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready=new CountDownLatch(1);
        Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});
        assertTrue(ready.await(15,TimeUnit.SECONDS));
    }
    @AfterAll static void stopFx(){Platform.exit();}
    static void fx(Runnable task) throws Exception {
        FutureTask<Void> future=new FutureTask<>(task,null);Platform.runLater(future);
        try {future.get(15,TimeUnit.SECONDS);} catch(ExecutionException e) {
            if(e.getCause() instanceof AssertionError a) throw a;
            throw e;
        }
    }
    static WorkshopService service() {return new WorkshopService(new Database("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",true));}
    static List<Node> nodes(Node root){
        List<Node> result=new ArrayList<>();result.add(root);
        if(root instanceof Parent p) for(Node child:p.getChildrenUnmodifiable())result.addAll(nodes(child));
        return result;
    }
    @ParameterizedTest @EnumSource(Role.class)
    void eachRoleCanOpenDashboardAndItsMenus(Role role) throws Exception {
        fx(()->{
            var service=service();Session.login(new AppUser(999,"Audit","audit","",role));
            Stage stage=new Stage();MainWindow root=new MainWindow(stage,service,"");
            stage.setScene(new Scene(root));
            List<Button> menus=nodes(root).stream().filter(n->n instanceof Button && n.getId()!=null && n.getId().startsWith("nav-")).map(n->(Button)n).toList();
            assertEquals(role==Role.GERENTE?6:role==Role.ATENDENTE?4:2,menus.size());
            for(Button b:menus) b.fire();
            assertFalse(nodes(root).stream().filter(n->n instanceof Label).map(n->((Label)n).getText()).anyMatch(t->t.contains("Não foi possível")));
            stage.close();Session.logout();
        });
    }
    @Test void seededAdminCanLoginViaActualLoginForm() throws Exception {
        fx(()->{
            Session.logout();boolean[] entered={false};LoginForm form=new LoginForm(service(),()->entered[0]=true);
            for(Node n:nodes(form))if(n instanceof TextField t)t.setText(t instanceof PasswordField?"123456":"admin");
            nodes(form).stream().filter(n->n instanceof Button b && b.getText().equals("Entrar")).map(n->(Button)n).findFirst().orElseThrow().fire();
            assertTrue(entered[0]);assertEquals(Role.GERENTE,Session.getUser().role());Session.logout();
        });
    }
    @Test void mechanicMustNotOpenRestrictedVehiclesThroughShortcut() throws Exception {
        fx(()->{
            Session.login(new AppUser(999,"Audit","audit","",Role.MECANICO));
            Stage stage=new Stage();MainWindow root=new MainWindow(stage,service(),"");Scene scene=new Scene(root);stage.setScene(scene);root.setupKeyShortcuts(scene);
            assertNull(root.lookup("#nav-Veículos"));
            Runnable shortcut=scene.getAccelerators().get(VEHICLES);if(shortcut!=null)shortcut.run();
            boolean opened=nodes(root).stream().anyMatch(n->n instanceof Label l && l.getStyleClass().contains("page-title") && l.getText().equals("Veículos"));
            stage.close();Session.logout();assertFalse(opened,"Ctrl+V abriu a pagina que o menu oculta do mecanico");
        });
    }
    @Test void logoutMustRemoveAuthenticatedShortcuts() throws Exception {
        fx(()->{
            Session.login(new AppUser(999,"Audit","audit","",Role.GERENTE));
            Stage stage=new Stage();MainWindow root=new MainWindow(stage,service(),"");Scene scene=new Scene(root);stage.setScene(scene);root.setupKeyShortcuts(scene);
            nodes(root).stream().filter(n->n instanceof Button b && b.getText().equals("Sair")).map(n->(Button)n).findFirst().orElseThrow().fire();
            assertInstanceOf(LoginForm.class,scene.getRoot());assertNull(Session.getUser());
            stage.close();assertFalse(scene.getAccelerators().containsKey(VEHICLES),"Callback da sessao anterior permaneceu na tela de login");
        });
    }
    @Test void reportScreenAppliesFiltersAndBlocksInvalidOrUnappliedExport()throws Exception {
        Stage[] stage={null};ReportsPane[] pane={null};
        try {
            fx(()->{
                Session.login(new AppUser(999,"Audit","audit","",Role.GERENTE));
                stage[0]=new Stage();pane[0]=new ReportsPane(stage[0],service());Scene scene=new Scene(pane[0],1100,700);
                scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());stage[0].setScene(scene);stage[0].show();
            });
            waitFx(()->!((Button)pane[0].lookup("#report-export")).isDisabled());
            fx(()->{
                assertEquals(4,((TableView<?>)pane[0].lookup("#report-table")).getItems().size());
                ((ComboBox<?>)pane[0].lookup("#report-status")).getSelectionModel().select(4);
                assertTrue(((Button)pane[0].lookup("#report-export")).isDisabled());
                ((Button)pane[0].lookup("#report-apply")).fire();
            });
            waitFx(()->!((Button)pane[0].lookup("#report-export")).isDisabled());
            fx(()->{
                assertEquals(1,((TableView<?>)pane[0].lookup("#report-table")).getItems().size());
                assertTrue(((Label)pane[0].lookup("#report-summary")).getText().contains("120,00"));
                var image=pane[0].snapshot(null,null);var pixels=image.getPixelReader();
                var buffered=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<buffered.getHeight();y++)for(int x=0;x<buffered.getWidth();x++)buffered.setRGB(x,y,pixels.getArgb(x,y));
                try{java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/qa"));javax.imageio.ImageIO.write(buffered,"png",new java.io.File("target/qa/tela-relatorios.png"));}catch(Exception e){throw new RuntimeException(e);}
                ((TextField)pane[0].lookup("#report-from")).setText("31/02/2026");((Button)pane[0].lookup("#report-apply")).fire();
                assertTrue(((Button)pane[0].lookup("#report-export")).isDisabled());
            });
        }finally{fx(()->{if(stage[0]!=null)stage[0].close();Session.logout();});}
    }
}
