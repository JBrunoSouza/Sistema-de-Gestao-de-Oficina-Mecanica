package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CurrentMainAuditTest {
    Database db;
    WorkshopService service;
    String url;
    @BeforeEach void setup() {
        Session.logout();
        url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        db=new Database(url,true);
        service=new WorkshopService(db);
    }
    @AfterEach void logout() { Session.logout(); }
    static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    void testUser(String name, Role role) throws Exception {
        String password=hash("audit-only-password");
        db.transaction(c->{try(var p=c.prepareStatement("INSERT INTO app_user(name,username,password_hash,role) VALUES(?,?,?,?)")) {
            p.setString(1,name);p.setString(2,name);p.setString(3,password);p.setString(4,role.name());p.executeUpdate();
        }return null;});
        service.login(name,"audit-only-password");
    }
    @ParameterizedTest @ValueSource(strings={"admin","ana","carlos"})
    void seededPasswordMustBeACompleteSha256(String name) {
        String stored=db.transaction(c->{try(var p=c.prepareStatement("SELECT password_hash FROM app_user WHERE username=?")) {
            p.setString(1,name);try(var r=p.executeQuery()){assertTrue(r.next());return r.getString(1);}
        }});
        assertEquals(64,stored.length(),"SHA-256 hexadecimal deve ter 64 caracteres; nenhum password pode produzir um hash menor neste login");
    }
    @Test void upgradeMustProvisionLoginForExistingDatabase() {
        // Represents the old schema: app_meta exists, app_user does not.
        db.transaction(c->{c.createStatement().execute("DROP TABLE app_user");c.createStatement().execute("UPDATE app_meta SET version=1");return null;});
        new Database(url,true);
        long count=db.transaction(c->{try(var r=c.createStatement().executeQuery("SELECT COUNT(*) FROM app_user")){r.next();return r.getLong(1);}});
        assertTrue(count>0,"Upgrade deixou o banco sem usuarios e sem alternativa de cadastro");
    }
    @Test void correctCredentialsAuthenticateAndWrongPasswordIsRejected() throws Exception {
        testUser("audit-manager",Role.GERENTE);
        assertEquals(Role.GERENTE,Session.getUser().role());
        Session.logout();
        assertThrows(ValidationException.class,()->service.login("audit-manager","incorrect"));
        assertNull(Session.getUser());
    }
    @Test void anonymousReadsMustBeDenied() {
        assertThrows(ValidationException.class,()->service.customers());
    }
    @Test void anonymousWritesAreDenied() {
        assertThrows(ValidationException.class,()->service.addCustomer("X","1","x@example.com"));
    }
    @Test void attendantCanRegisterButCannotManageStockOrDiagnosis() throws Exception {
        testUser("audit-attendant",Role.ATENDENTE);
        assertNotNull(service.addCustomer("X","1","x@example.com"));
        assertThrows(ValidationException.class,()->service.addPart("X","1","1"));
        assertThrows(ValidationException.class,()->service.saveDiagnosis(1,"X"));
        assertThrows(ValidationException.class,()->service.receivedTotal());
    }
    @Test void mechanicCanDiagnoseButCannotRegisterCustomerOrApprove() throws Exception {
        testUser("audit-mechanic",Role.MECANICO);
        long id=service.orders().stream().filter(o->o.status()==OrderStatus.OPEN).findFirst().orElseThrow().id();
        service.saveDiagnosis(id,"Diagnostico de auditoria");
        assertEquals("Diagnostico de auditoria",service.order(id).diagnosis());
        assertThrows(ValidationException.class,()->service.addCustomer("X","1","x@example.com"));
        assertThrows(ValidationException.class,()->service.decideBudget(1,true,"X"));
    }
    @Test void rejectedOrderHasNoRecoveryAndKeepsVehicleAndStockReserved() throws Exception {
        testUser("audit-manager",Role.GERENTE);
        var order=service.orders().stream().filter(o->o.status()==OrderStatus.OPEN).findFirst().orElseThrow();
        var part=service.parts().get(0);
        service.consumePart(order.id(),part.id(),"1");
        int after=service.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock();
        service.decideBudget(order.id(),false,"Auditoria");
        assertThrows(ValidationException.class,()->service.openOrder(order.customerId(),order.vehicleId(),"Nova","23/09/2026","100","Auditoria"));
        assertThrows(ValidationException.class,()->service.removeItem(order.id(),service.items(order.id()).get(0).id()));
        assertThrows(ValidationException.class,()->service.closeOrder(order.id(),"PIX","23/09/2026"));
        assertEquals(after,service.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock());
    }
}
