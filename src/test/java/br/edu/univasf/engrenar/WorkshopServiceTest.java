package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopServiceTest {
    static void mechanic(Runnable action) {
        AppUser previous=Session.getUser();
        Session.login(new AppUser(998,"Mecânico de teste","mechanic-test","",Role.MECANICO));
        try { action.run(); } finally { if(previous==null)Session.logout();else Session.login(previous); }
    }

    private WorkshopService service;
    private Database db;
    private Customer customer;
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-09-16T12:30:00Z"),ZoneId.of("America/Sao_Paulo"));
    @BeforeEach void setup() { Session.login(new AppUser(999L,"Auditoria","audit","",Role.GERENTE));
        db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1",false);
        service=new WorkshopService(db,CLOCK);customer=service.addCustomer("Ana","87999999999","ana@example.com");
    }
    private Vehicle vehicle() {return service.addVehicle("ABC1D23","Fiat","Argo","42000","2021",customer.id());}
    private ServiceOrder order() { Vehicle v=vehicle();ServiceOrder o=open(v);mechanic(() -> service.saveDiagnosis(o.id(),"Diagnóstico de teste"));return o; }
    private ServiceOrder open(Vehicle v) {return service.openOrder(customer.id(),v.id(),"Ruído ao frear","16/09/2026","42100","Matheus");}
    private ValidationException invalid(String field,org.junit.jupiter.api.function.Executable action) {ValidationException e=assertThrows(ValidationException.class,action);assertTrue(e.fields().containsKey(field),e.getMessage());return e;}

    @Test void selectionCheckDoesNotReserveAndConfirmationRechecksActiveOrder() {
        Vehicle v=vehicle();
        invalid("customer",()->service.validateOrderSelection(null,v.id()));
        invalid("vehicle",()->service.validateOrderSelection(customer.id(),null));
        assertEquals(v,service.validateOrderSelection(customer.id(),v.id()));
        assertTrue(service.orders().isEmpty());
        ServiceOrder opened=open(v);
        assertTrue(invalid("vehicle",()->service.validateOrderSelection(customer.id(),v.id())).getMessage().contains(opened.number()));
        invalid("vehicle",()->open(v));
        assertEquals(1,service.orders().size());
    }

    @Test void vehiclePersistsAllFieldsAndCustomer() {Vehicle v=vehicle();assertEquals(v,service.vehicle(v.id()));assertEquals(customer.id(),v.customerId());}
    @ParameterizedTest @ValueSource(strings={"abc1d23","ABC-1D23"," ABC1D23 "})
    void duplicatePlateReturnsExistingVehicle(String plate) {Vehicle v=vehicle();ValidationException e=invalid("plate",()->service.addVehicle(plate,"Fiat","Argo","100","2020",customer.id()));assertEquals(v.id(),e.existingVehicleId());assertEquals(1,service.vehicles().size());}
    @Test void absentCustomerPreventsVehicle() {invalid("customer",()->service.addVehicle("ABC1D23","Fiat","Argo","0","2021",null));invalid("customer",()->service.addVehicle("ABC1D23","Fiat","Argo","0","2021",999L));assertTrue(service.vehicles().isEmpty());}
    @ParameterizedTest @ValueSource(strings={"","-1","abc","1.5"})
    void invalidMileagePreventsSave(String km) {invalid("mileage",()->service.addVehicle("ABC1D23","Fiat","Argo",km,"2021",customer.id()));assertTrue(service.vehicles().isEmpty());}
    @Test void blankVehicleFieldsIdentifyField() {
        invalid("plate",()->service.addVehicle("","Fiat","Argo","1","2021",customer.id()));
        invalid("brand",()->service.addVehicle("ABC1D23","","Argo","1","2021",customer.id()));
        invalid("model",()->service.addVehicle("ABC1D23","Fiat","","1","2021",customer.id()));
        invalid("year",()->service.addVehicle("ABC1D23","Fiat","Argo","1","",customer.id()));
        assertTrue(service.vehicles().isEmpty());
    }
    @Test void opensOrderWithGeneratedNumberAndInitialStatus() {ServiceOrder o=order();assertTrue(o.id()>0);assertTrue(o.number().startsWith("OS-"));assertEquals(OrderStatus.OPEN,o.status());assertEquals("Matheus",o.responsible());assertEquals(42100,service.vehicle(o.vehicleId()).mileage());}
    @Test void duplicateOpenOrderShowsOriginalNumber() {ServiceOrder o=order();ValidationException e=invalid("vehicle",()->open(service.vehicle(o.vehicleId())));assertTrue(e.getMessage().contains(o.number()));assertEquals(1,service.orders().size());}
    @Test void orderRequiresExistingMatchingCustomerAndVehicle() {
        Vehicle v=vehicle();Customer other=service.addCustomer("Bia","123","bia@example.com");
        invalid("customer",()->service.openOrder(null,v.id(),"Ruído","16/09/2026","100","Matheus"));
        invalid("vehicle",()->service.openOrder(customer.id(),null,"Ruído","16/09/2026","100","Matheus"));
        invalid("vehicle",()->service.openOrder(customer.id(),999L,"Ruído","16/09/2026","100","Matheus"));
        invalid("vehicle",()->service.openOrder(other.id(),v.id(),"Ruído","16/09/2026","100","Matheus"));
        assertTrue(service.orders().isEmpty());
    }
    @Test void customerValidationPrecedesBlankOrderFields() {
        invalid("customer",()->service.openOrder(null,null,"","","",""));
        invalid("customer",()->service.openOrder(999L,null,"","","",""));
        assertTrue(service.orders().isEmpty());
    }
    @Test void vehicleValidationPrecedesBlankOrderFields() {
        invalid("vehicle",()->service.openOrder(customer.id(),null,"","","",""));
        invalid("vehicle",()->service.openOrder(customer.id(),999L,"","","",""));
        Vehicle v=vehicle();Customer other=service.addCustomer("Bia","123","bia@example.com");
        ValidationException e=invalid("vehicle",()->service.openOrder(other.id(),v.id(),"","","",""));
        assertTrue(e.getMessage().contains("não pertence"));
        assertTrue(service.orders().isEmpty());
    }
    @Test void activeOrderValidationPrecedesBlankOrderFields() {
        Vehicle v=vehicle();ServiceOrder existing=open(v);
        ValidationException e=invalid("vehicle",()->service.openOrder(customer.id(),v.id(),"","","",""));
        assertTrue(e.getMessage().contains(existing.number()));
        assertEquals(1,service.orders().size());
    }
    @Test void requiredOrderFieldsAndInvalidDatesPreventSave() {
        Vehicle v=vehicle();
        invalid("complaint",()->service.openOrder(customer.id(),v.id(),"","16/09/2026","100","Matheus"));
        invalid("entryDate",()->service.openOrder(customer.id(),v.id(),"Ruído","","100","Matheus"));
        invalid("entryDate",()->service.openOrder(customer.id(),v.id(),"Ruído","31/02/2026","100","Matheus"));
        invalid("responsible",()->service.openOrder(customer.id(),v.id(),"Ruído","16/09/2026","100",""));
        invalid("mileage",()->service.openOrder(customer.id(),v.id(),"Ruído","16/09/2026","-1","Matheus"));
        assertTrue(service.orders().isEmpty());assertEquals(42000,service.vehicle(v.id()).mileage());
    }
    @Test void emptyBudgetCannotGenerateApproveOrReject() {
        ServiceOrder o=order();invalid("items",()->service.budget(o.id()));
        invalid("items",()->service.decideBudget(o.id(),true,"Matheus"));invalid("items",()->service.decideBudget(o.id(),false,"Matheus"));
        assertEquals(OrderStatus.OPEN,service.order(o.id()).status());assertNull(service.order(o.id()).decisionAt());
    }
    @Test void calculatesDecimalSubtotalsAndPersistsApproval() {
        ServiceOrder o=order();mechanic(() -> service.addService(o.id(),"Troca de óleo","2","100,10"));service.addPart("Filtro","35,90","5");Part p=service.parts().get(0);service.consumePart(o.id(),p.id(),"3");
        Budget b=service.budget(o.id());assertEquals(new BigDecimal("200.20"),b.services());assertEquals(new BigDecimal("107.70"),b.parts());assertEquals(new BigDecimal("307.90"),b.total());
        service.decideBudget(o.id(),true,"Matheus");ServiceOrder saved=service.order(o.id());
        assertEquals(OrderStatus.APPROVED,saved.status());assertEquals(LocalDateTime.of(2026,9,16,9,30),saved.decisionAt());assertEquals("Matheus",saved.decisionBy());assertEquals(b.total(),saved.budgetTotal());assertEquals(2,service.parts().get(0).stock());
    }
    @Test void rejectionPersistsDateResponsibleAndTotal() {
        ServiceOrder o=order();mechanic(() -> service.addService(o.id(),"Alinhamento","1","90"));service.decideBudget(o.id(),false,"Thiago");ServiceOrder saved=service.order(o.id());
        assertEquals(OrderStatus.REJECTED,saved.status());assertEquals("Thiago",saved.decisionBy());assertNotNull(saved.decisionAt());assertEquals(new BigDecimal("90.00"),saved.budgetTotal());
    }
    @Test void decisionRequiresResponsibleAndDoesNotOverwriteExistingDecision() {ServiceOrder o=order();mechanic(() -> service.addService(o.id(),"Revisão","1","50"));invalid("decisionBy",()->service.decideBudget(o.id(),true,""));service.decideBudget(o.id(),true,"Ana");invalid("order",()->service.decideBudget(o.id(),false,"Bia"));assertEquals("Ana",service.order(o.id()).decisionBy());}
    @Test void insufficientStockIsAtomic() {
        ServiceOrder o=order();service.addPart("Filtro","35,90","2");Part p=service.parts().get(0);invalid("quantity",()->service.consumePart(o.id(),p.id(),"3"));assertEquals(2,service.parts().get(0).stock());assertTrue(service.items(o.id()).isEmpty());
    }
    @Test void removingPartRestoresStock() {ServiceOrder o=order();service.addPart("Filtro","10","3");Part p=service.parts().get(0);service.consumePart(o.id(),p.id(),"2");service.removeItem(o.id(),service.items(o.id()).get(0).id());assertEquals(3,service.parts().get(0).stock());assertTrue(service.items(o.id()).isEmpty());}
    @Test void approvedBudgetCannotChangeItsItemsOrDiagnosis() {ServiceOrder o=order();mechanic(() -> service.addService(o.id(),"Revisão","1","100"));service.decideBudget(o.id(),true,"Ana");invalid("order",()->mechanic(() -> service.addService(o.id(),"Extra","1","10")));invalid("order",()->service.removeItem(o.id(),service.items(o.id()).get(0).id()));invalid("order",()->mechanic(() -> service.saveDiagnosis(o.id(),"Alterado")));assertEquals(new BigDecimal("100.00"),service.budget(o.id()).total());}
    @Test void closeRequiresApprovalCompletedServicesAndPaymentThenReleasesVehicle() {
        ServiceOrder o=order();mechanic(() -> service.addService(o.id(),"Revisão","1","100"));long item=service.items(o.id()).get(0).id();invalid("order",()->mechanic(() -> service.completeService(o.id(),item)));
        invalid("order",()->service.closeOrder(o.id(),"Dinheiro","16/09/2026"));service.decideBudget(o.id(),true,"Ana");invalid("items",()->service.closeOrder(o.id(),"Dinheiro","16/09/2026"));mechanic(() -> service.completeService(o.id(),item));
        invalid("payment",()->service.closeOrder(o.id(),"","16/09/2026"));invalid("pickup",()->service.closeOrder(o.id(),"Dinheiro","15/09/2026"));
        service.closeOrder(o.id(),"Dinheiro","16/09/2026");assertEquals(OrderStatus.CLOSED,service.order(o.id()).status());invalid("order",()->mechanic(() -> service.addService(o.id(),"Extra","1","10")));assertNotEquals(o.id(),open(service.vehicle(o.vehicleId())).id());
    }
    @Test void realFilePersistsAfterDatabaseReinitialization(@TempDir Path dir) {
        String url="jdbc:h2:file:"+dir.resolve("durable").toString().replace('\\','/');WorkshopService first=new WorkshopService(new Database(url,true),CLOCK);
        Customer c=first.addCustomer("Persistente","123","persistente@example.com");Vehicle v=first.addVehicle("XYZ9A12","Ford","Ka","0","2019",c.id());ServiceOrder o=first.openOrder(c.id(),v.id(),"Teste","16/09/2026","0","Matheus");mechanic(() -> first.saveDiagnosis(o.id(),"Diagnóstico persistido"));mechanic(() -> first.addService(o.id(),"Teste real","1","123,45"));first.decideBudget(o.id(),true,"Matheus");
        WorkshopService reopened=new WorkshopService(new Database(url,true),CLOCK);assertEquals(4,reopened.customers().size());assertEquals(4,reopened.vehicles().size());assertEquals(5,reopened.orders().size());assertEquals(OrderStatus.APPROVED,reopened.order(o.id()).status());assertEquals(new BigDecimal("123.45"),reopened.order(o.id()).budgetTotal());
    }
    @Test void seedIncludesEveryDemoStatusAndIsNotDuplicated() {String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";WorkshopService s=new WorkshopService(new Database(url,true));new Database(url,true);assertEquals(3,s.customers().size());assertEquals(3,s.vehicles().size());assertEquals(Set.of(OrderStatus.OPEN,OrderStatus.APPROVED,OrderStatus.REJECTED,OrderStatus.CLOSED),new HashSet<>(s.orders().stream().map(ServiceOrder::status).toList()));for(ServiceOrder o:s.orders())if(o.budgetTotal()!=null)assertEquals(o.budgetTotal(),s.budget(o.id()).total());}
    @Test void databaseForeignKeyRejectsMismatchedOwner() {
        Vehicle v=vehicle();Customer other=service.addCustomer("Outra","123","outra@example.com");
        assertThrows(IllegalStateException.class,()->db.transaction(c->{try(var p=c.prepareStatement("INSERT INTO service_order(customer_id,vehicle_id,complaint,entry_date,mileage,responsible,status) VALUES(?,?,'x',CURRENT_DATE,1,'Ana','OPEN')")){p.setLong(1,other.id());p.setLong(2,v.id());p.executeUpdate();}return null;}));assertTrue(service.orders().isEmpty());
    }
    @Test void concurrentOpenCreatesOnlyOneOrder() throws Exception {
        Vehicle v=vehicle();ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {Callable<Boolean> action=()->{start.await();try{open(v);return true;}catch(ValidationException e){return false;}};Future<Boolean> first=executor.submit(action),second=executor.submit(action);start.countDown();assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));assertEquals(1,service.orders().size());}finally{executor.shutdownNow();}
    }
}

