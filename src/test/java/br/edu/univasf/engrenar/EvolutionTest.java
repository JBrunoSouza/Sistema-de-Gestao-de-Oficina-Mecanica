package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class EvolutionTest {
    Database db;WorkshopService s;String url;
    static final Clock NOW=Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"),ZoneOffset.UTC);
    static final String PASSWORD="Senha longa de teste 2026";
    @BeforeEach void start(){url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";db=new Database(url,true);s=new WorkshopService(db,NOW);s.login("admin","123456");}
    @AfterEach void end(){Session.logout();}
    ServiceOrder open(){return s.orders().stream().filter(o->o.status()==OrderStatus.OPEN).findFirst().orElseThrow();}
    long rejected(){long id=open().id();s.saveDiagnosis(id,"Diagnóstico");s.addService(id,"Revisão","1","50");s.consumePart(id,s.parts().get(0).id(),"2");s.decideBudget(id,false,"Cliente");return id;}
    @Test void cancelReturnsStockExactlyOnceAndReleasesVehicle(){
        Part part=s.parts().get(0);long id=rejected();var order=s.order(id);
        s.cancelRejectedOrder(id,"Cliente desistiu");assertEquals(OrderStatus.CANCELLED,s.order(id).status());
        assertEquals(part.stock(),s.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock());
        assertThrows(ValidationException.class,()->s.cancelRejectedOrder(id,"Novamente"));
        assertNotNull(s.openOrder(order.customerId(),order.vehicleId(),"Outro atendimento","23/09/2026","42000","Atendente"));
        assertTrue(s.orderHistory(id).stream().anyMatch(h->h.action().equals("CANCELAMENTO")));
        assertTrue(s.stockHistory(part.id()).stream().anyMatch(h->h.action().contains("Cancelamento")));
        assertEquals(2,s.items(id).size());
    }
    @Test void revisionPreservesRejectedBudgetHistoryAndRequiresNewApproval(){
        long id=rejected();var before=s.order(id);int balance=s.parts().get(0).stock();
        s.reviseRejectedOrder(id,"Negociar serviços");assertEquals(OrderStatus.OPEN,s.order(id).status());assertNull(s.order(id).budgetTotal());
        assertEquals(balance,s.parts().get(0).stock());assertThrows(ValidationException.class,()->s.completeService(id,s.items(id).get(0).id()));
        s.removeItem(id,s.items(id).get(0).id());s.decideBudget(id,true,"Novo aceite");
        assertNotEquals(before.budgetTotal(),s.order(id).budgetTotal());
        assertTrue(s.orderHistory(id).stream().anyMatch(h->h.action().equals("REVISAO")&&h.details().contains(before.budgetTotal().toString())));
    }
    @Test void unauthorizedAndWrongStateResolutionCannotChangeData(){
        long id=rejected();assertThrows(ValidationException.class,()->s.cancelRejectedOrder(id,""));
        s.login("carlos","123456");assertThrows(ValidationException.class,()->s.cancelRejectedOrder(id,"X"));assertThrows(ValidationException.class,()->s.reviseRejectedOrder(id,"X"));
        s.login("admin","123456");s.cancelRejectedOrder(id,"X");assertThrows(ValidationException.class,()->s.reviseRejectedOrder(id,"X"));
    }
    @Test void simultaneousCancellationOnlyReturnsStockOnce()throws Exception{
        Part part=s.parts().get(0);long id=rejected();var pool=Executors.newFixedThreadPool(2);var latch=new CountDownLatch(1);
        try{Callable<Boolean> cancel=()->{latch.await();try{s.cancelRejectedOrder(id,"Teste concorrente");return true;}catch(ValidationException e){return false;}};
            var a=pool.submit(cancel);var b=pool.submit(cancel);latch.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(part.stock(),s.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock());
        }finally{pool.shutdownNow();}
    }
    @Test void createAndChangePasswordUseSaltedHashesAndRequireCurrentPassword(){
        assertTrue(Session.getUser().passwordHash().startsWith("pbkdf2$"));
        s.createUser("User A","user.a",PASSWORD,Role.ATENDENTE);s.createUser("User B","user.b",PASSWORD,Role.MECANICO);
        s.login("user.a",PASSWORD);String first=Session.getUser().passwordHash();
        assertThrows(ValidationException.class,()->s.changePassword("wrong",PASSWORD+"2"));
        s.changePassword(PASSWORD,PASSWORD+"2");assertFalse(Passwords.verify(PASSWORD,Session.getUser().passwordHash()));
        s.login("user.b",PASSWORD);assertNotEquals(first,Session.getUser().passwordHash());
        assertThrows(ValidationException.class,()->s.users());assertThrows(ValidationException.class,()->s.createUser("C","user.c",PASSWORD,Role.GERENTE));
    }
    @Test void weakDuplicateAndInvalidAccountsAreRejected(){
        assertThrows(ValidationException.class,()->s.createUser("A","short","123",Role.GERENTE));
        assertThrows(ValidationException.class,()->s.createUser("A","admin",PASSWORD,Role.GERENTE));
        assertThrows(ValidationException.class,()->s.createUser("A","bad space",PASSWORD,Role.GERENTE));
        assertThrows(ValidationException.class,()->s.createUser("A","valid",PASSWORD,null));
    }
    @Test void resetRequiresManagerReauthenticationIsHashedAndSingleUse(){
        assertThrows(ValidationException.class,()->s.issueResetCode("ana","wrong"));
        String code=s.issueResetCode("ana","123456");
        db.transaction(c->{try(var r=c.createStatement().executeQuery("SELECT token_hash FROM password_reset")){assertTrue(r.next());assertNotEquals(code,r.getString(1));}return null;});
        Session.logout();s.recoverPassword("ana",code,PASSWORD);assertNull(Session.getUser());
        assertThrows(ValidationException.class,()->s.recoverPassword("ana",code,PASSWORD+"2"));
        assertThrows(ValidationException.class,()->s.login("ana","123456"));s.login("ana",PASSWORD);
        assertEquals(Role.ATENDENTE,Session.getUser().role());
    }
    @Test void resetExpiresAndReissueInvalidatesEarlierCode(){
        String first=s.issueResetCode("ana","123456"),second=s.issueResetCode("ana","123456");
        assertThrows(ValidationException.class,()->s.recoverPassword("ana",first,PASSWORD));
        var later=new WorkshopService(db,Clock.offset(NOW,Duration.ofMinutes(16)));
        assertThrows(ValidationException.class,()->later.recoverPassword("ana",second,PASSWORD));
        s.login("ana","123456");
    }
    @Test void userPasswordChangeInvalidatesRecoveryCode(){
        String code=s.issueResetCode("ana","123456");s.login("ana","123456");s.changePassword("123456",PASSWORD);
        assertThrows(ValidationException.class,()->s.recoverPassword("ana",code,PASSWORD+"2"));
    }
    @Test void diagnosisIsRequiredAndExecutionNotesPersist(){
        long id=open().id();assertThrows(ValidationException.class,()->s.addService(id,"Teste","1","10"));assertThrows(ValidationException.class,()->s.saveDiagnosis(id," "));
        s.saveDiagnosis(id,"Falha confirmada");s.addService(id,"Reparar","1","10");long item=s.items(id).get(0).id();
        assertThrows(ValidationException.class,()->s.completeService(id,item,"Antes do aceite"));s.decideBudget(id,true,"Cliente");
        s.completeService(id,item,"Ajustado e testado");new Database(url,true);
        assertEquals("Ajustado e testado",s.items(id).get(0).observations());assertTrue(s.items(id).get(0).completed());
        assertThrows(ValidationException.class,()->s.completeService(id,item,"x".repeat(2001)));
    }
    @Test void existingPartEntryUpdatesBalancePriceButNotHistoricalOrderPrice(){
        long id=open().id();s.addPart("Nova peça","10","5");Part part=s.parts().stream().filter(p->p.name().equals("Nova peça")).findFirst().orElseThrow();
        s.consumePart(id,part.id(),"2");s.addPart("Nova peça","15","3");
        assertEquals(6,s.parts().stream().filter(p->p.id()==part.id()).findFirst().orElseThrow().stock());
        assertEquals("10.00",s.items(id).get(0).unitPrice().toString());assertEquals(3,s.stockHistory(part.id()).size());
        assertThrows(ValidationException.class,()->s.consumePart(id,part.id(),"7"));assertEquals(3,s.stockHistory(part.id()).size());
    }
}
