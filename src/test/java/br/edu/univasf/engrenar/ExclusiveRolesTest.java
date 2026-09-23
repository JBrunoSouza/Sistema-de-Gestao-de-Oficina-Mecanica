package br.edu.univasf.engrenar;

import br.edu.univasf.engrenar.dao.Database;
import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExclusiveRolesTest {
    WorkshopService service; long order,part,item;
    @BeforeEach void setup(){
        service=new WorkshopService(new Database("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",true));
        service.login("carlos","123456");
        order=service.orders().stream().filter(o->o.status()==OrderStatus.OPEN).findFirst().orElseThrow().id();
        part=service.parts().get(0).id();
        service.saveDiagnosis(order,"Diagnóstico do mecânico");service.addService(order,"Reparo","1","50");
        item=service.items(order).stream().filter(i->i.kind().equals("SERVICE")).findFirst().orElseThrow().id();
    }
    @AfterEach void logout(){Session.logout();}
    @Test void administratorCannotWriteTechnicalWorkButCanManageParts(){
        service.login("admin","123456");
        assertThrows(ValidationException.class,()->service.saveDiagnosis(order,"Alteração"));
        assertThrows(ValidationException.class,()->service.addService(order,"Outro","1","1"));
        assertThrows(ValidationException.class,()->service.removeItem(order,item));
        service.consumePart(order,part,"1");
        long piece=service.items(order).stream().filter(i->i.kind().equals("PART")).findFirst().orElseThrow().id();
        service.removeItem(order,piece);service.replenish(part,"1");
        service.decideBudget(order,true,"Cliente");
        assertThrows(ValidationException.class,()->service.completeService(order,item));
        service.login("carlos","123456");service.completeService(order,item,"Executado pelo mecânico");
        assertTrue(service.items(order).stream().filter(i->i.id()==item).findFirst().orElseThrow().completed());
    }
    @Test void mechanicCannotManagePartsIncludingRemoval(){
        service.login("admin","123456");service.consumePart(order,part,"1");
        long piece=service.items(order).stream().filter(i->i.kind().equals("PART")).findFirst().orElseThrow().id();
        int stock=service.parts().get(0).stock();
        service.login("carlos","123456");
        assertThrows(ValidationException.class,()->service.addPart("Proibida","10","1"));
        assertThrows(ValidationException.class,()->service.replenish(part,"1"));
        assertThrows(ValidationException.class,()->service.consumePart(order,part,"1"));
        assertThrows(ValidationException.class,()->service.removeItem(order,piece));
        assertEquals(stock,service.parts().get(0).stock());
        assertTrue(service.items(order).stream().anyMatch(i->i.id()==piece));
        service.removeItem(order,item);
        assertFalse(service.items(order).stream().anyMatch(i->i.id()==item));
    }
    @Test void attendantCannotWriteEitherDomain(){
        service.login("ana","123456");
        assertThrows(ValidationException.class,()->service.saveDiagnosis(order,"Alteração"));
        assertThrows(ValidationException.class,()->service.addService(order,"Outro","1","1"));
        assertThrows(ValidationException.class,()->service.completeService(order,item));
        assertThrows(ValidationException.class,()->service.removeItem(order,item));
        assertThrows(ValidationException.class,()->service.addPart("Proibida","10","1"));
        assertThrows(ValidationException.class,()->service.replenish(part,"1"));
        assertThrows(ValidationException.class,()->service.consumePart(order,part,"1"));
    }
}
