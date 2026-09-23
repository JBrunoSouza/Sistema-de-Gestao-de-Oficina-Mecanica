package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PostgresIntegrationTest {
    static EmbeddedPostgres postgres;
    Database db;WorkshopService service;String url;
    @BeforeAll static void start()throws Exception {
        postgres=EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses","127.0.0.1").start();
        try(var c=postgres.getPostgresDatabase().getConnection()) {
            c.createStatement().execute("CREATE ROLE engrenar LOGIN PASSWORD 'engrenar'");
            System.out.println("POSTGRES VERSION: "+c.getMetaData().getDatabaseProductVersion());
        }
    }
    @AfterAll static void stop()throws Exception {if(postgres!=null)postgres.close();Session.logout();}
    @BeforeEach void setup()throws Exception {
        String name="audit_"+UUID.randomUUID().toString().replace("-","");
        try(var c=postgres.getPostgresDatabase().getConnection()){c.createStatement().execute("CREATE DATABASE "+name+" OWNER engrenar");}
        url="jdbc:postgresql://127.0.0.1:"+postgres.getPort()+"/"+name;
        db=new Database(url,true);service=new WorkshopService(db);service.login("admin","123456");
    }
    @Test void freshDatabaseLoginBusinessFlowAndFilteredReport(){
        var customer=service.addCustomer("Teste PostgreSQL","123","pg@example.com");
        var vehicle=service.addVehicle("PGT1A23","Fiat","Argo","10","2021",customer.id());
        var order=service.openOrder(customer.id(),vehicle.id(),"Teste","23/09/2026","10","Gerente");
        service.saveDiagnosis(order.id(),"Revisao");service.addService(order.id(),"Revisao","2","25,50");
        service.addPart("Peca PG","10","3");var part=service.parts().stream().filter(p->p.name().equals("Peca PG")).findFirst().orElseThrow();
        service.consumePart(order.id(),part.id(),"1");service.decideBudget(order.id(),true,"Gerente");
        service.completeService(order.id(),service.items(order.id()).stream().filter(i->i.kind().equals("SERVICE")).findFirst().orElseThrow().id());
        service.closeOrder(order.id(),"PIX","23/09/2026",true);
        var report=service.report(new ReportFilter(LocalDate.of(2026,9,23),LocalDate.of(2026,9,23),OrderStatus.CLOSED,"PGT1A23"));
        assertEquals(1,report.rows().size());assertEquals(new BigDecimal("61.00"),report.receivedTotal());
        assertEquals(2,service.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock());
        assertNotNull(service.openOrder(customer.id(),vehicle.id(),"Novo","23/09/2026","10","Gerente"));
    }
    @Test void upgradePreservesOldDataAndReinitializationPreservesCredentials(){
        db.transaction(c->{c.createStatement().execute("DROP TABLE app_user");c.createStatement().execute("UPDATE app_meta SET version=1");return null;});
        new Database(url,true);service.login("admin","123456");assertEquals(3,service.customers().size());assertEquals(4,service.orders().size());
        db.transaction(c->{c.createStatement().executeUpdate("UPDATE app_user SET password_hash='custom' WHERE username='admin'");return null;});
        new Database(url,true);
        assertThrows(ValidationException.class,()->service.login("admin","123456"));
    }
    @Test void rejectedOperationsRollbackAndRoleRestrictionsApply(){
        var open=service.orders().stream().filter(o->o.status()==OrderStatus.OPEN).findFirst().orElseThrow();
        var part=service.parts().get(0);
        assertThrows(ValidationException.class,()->service.consumePart(open.id(),part.id(),"9999"));
        assertEquals(part.stock(),service.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock());
        service.login("carlos","123456");assertThrows(ValidationException.class,()->service.report(new ReportFilter(null,null,null,"")));
        Session.logout();assertThrows(ValidationException.class,()->service.customers());
    }
}
