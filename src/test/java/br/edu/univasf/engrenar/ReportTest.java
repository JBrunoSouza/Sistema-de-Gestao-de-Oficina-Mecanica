package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportTest {
    WorkshopService service;Database db;
    @BeforeEach void setup(){
        Session.login(new AppUser(99,"Teste","teste","",Role.GERENTE));
        db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",true);service=new WorkshopService(db);
        db.transaction(c->{c.createStatement().executeUpdate("UPDATE service_order SET entry_date=DATE '2026-09-23'");return null;});
    }
    @AfterEach void logout(){Session.logout();}
    @Test void datesAreInclusiveAndTotalsOnlyIncludeClosedOrders(){
        var day=LocalDate.of(2026,9,23);var report=service.report(new ReportFilter(day,day,null,""));
        assertEquals(4,report.rows().size());assertEquals(1,report.closedCount());assertEquals(new BigDecimal("120.00"),report.receivedTotal());
        assertTrue(service.report(new ReportFilter(day.plusDays(1),null,null,"")).rows().isEmpty());
        assertTrue(service.report(new ReportFilter(null,day.minusDays(1),null,"")).rows().isEmpty());
    }
    @Test void statusCustomerAndPlateFiltersCompose(){
        var report=service.report(new ReportFilter(null,null,OrderStatus.CLOSED,"ana"));
        assertEquals(1,report.rows().size());assertEquals("ABC1D23",report.rows().get(0).plate());
        assertEquals(2,service.report(new ReportFilter(null,null,null,"abc1d23")).rows().size());
        assertTrue(service.report(new ReportFilter(null,null,OrderStatus.REJECTED,"ana")).rows().isEmpty());
        assertTrue(service.report(new ReportFilter(null,null,null,"%_!")).rows().isEmpty());
    }
    @Test void invalidPeriodAndUnauthorizedReportAreRejected(){
        assertThrows(ValidationException.class,()->service.report(new ReportFilter(LocalDate.of(2026,9,24),LocalDate.of(2026,9,23),null,"")));
        for(Role role:List.of(Role.MECANICO,Role.ATENDENTE)) {
            Session.login(new AppUser(99,"Teste","teste","",role));
            assertThrows(ValidationException.class,()->service.report(new ReportFilter(null,null,null,"")));
        }
    }
    @Test void pdfContainsTheExactFilteredSnapshotAndEmptyState(@TempDir Path dir)throws Exception{
        var report=service.report(new ReportFilter(null,null,OrderStatus.CLOSED,"ana"));
        Path file=dir.resolve("report.pdf");service.exportReport(report,file);
        try(var doc=Loader.loadPDF(file.toFile())){
            String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("Ana Lima"));assertTrue(text.contains("120,00"));assertFalse(text.contains("Bruno Santos"));assertEquals(1,doc.getNumberOfPages());
        }
        service.exportReport(service.report(new ReportFilter(null,null,null,"not-found")),file);
        try(var doc=Loader.loadPDF(file.toFile())){assertTrue(new PDFTextStripper().getText(doc).contains("Nenhuma ordem encontrada"));}
        Session.logout();assertThrows(ValidationException.class,()->service.exportReport(report,file));
    }
    @Test void multipagePdfRepeatsHeadersAndPreservesLongNames()throws Exception{
        List<OrderReport.Row> rows=new ArrayList<>();
        for(int i=1;i<=75;i++)rows.add(new OrderReport.Row(i,"José da Conceição "+"Sobrenome ".repeat(12)+i,"ABC1D23",LocalDate.of(2026,9,23),OrderStatus.CLOSED,new BigDecimal("120.50")));
        var report=new OrderReport(new ReportFilter(null,null,null,""),LocalDateTime.of(2026,9,23,12,0),rows);
        Path file=Path.of("target/qa/relatorio-paginado.pdf");Files.createDirectories(file.getParent());service.exportReport(report,file);
        try(var doc=Loader.loadPDF(file.toFile())){
            assertTrue(doc.getNumberOfPages()>1);String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("OS-00075"));assertTrue(text.contains("9.037,50"));
            PDFRenderer renderer=new PDFRenderer(doc);
            for(int i=0;i<doc.getNumberOfPages();i++){
                PDFTextStripper page=new PDFTextStripper();page.setStartPage(i+1);page.setEndPage(i+1);assertTrue(page.getText(doc).contains("Valor decidido"));
                if(i==0||i==doc.getNumberOfPages()-1)javax.imageio.ImageIO.write(renderer.renderImageWithDPI(i,110),"png",Path.of("target/qa/relatorio-pagina-"+(i+1)+".png").toFile());
            }
        }
    }
    @Test void migrationIsIdempotentAndPreservesExistingCredentialsAndData(){
        db.transaction(c->{c.createStatement().executeUpdate("UPDATE app_user SET password_hash='custom',role='ATENDENTE' WHERE username='admin'");return null;});
        // Reuse Database.open URL without exposing any application credentials.
        String url=db.transaction(c->c.getMetaData().getURL());new Database(url,true);new Database(url,true);
        assertEquals(3,service.customers().size());assertEquals(4,service.orders().size());
        db.transaction(c->{try(var r=c.createStatement().executeQuery("SELECT password_hash,role FROM app_user WHERE username='admin'")){assertTrue(r.next());assertEquals("custom",r.getString(1));assertEquals("ATENDENTE",r.getString(2));}return null;});
    }
}
