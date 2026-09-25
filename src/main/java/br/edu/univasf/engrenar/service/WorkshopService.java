package br.edu.univasf.engrenar.service;

import br.edu.univasf.engrenar.dao.*;
import br.edu.univasf.engrenar.model.*;
import java.math.*;
import java.time.*;
import java.time.format.*;
import java.sql.*;
import java.util.*;

/** Cada comando grava uma transacao atomica; valores monetarios nunca usam double. */
public final class WorkshopService {
    private final Database db;
    private final WorkshopDao dao = new WorkshopDao();
    private final Clock clock;
    public WorkshopService(Database db) { this(db, Clock.systemDefaultZone()); }
    public WorkshopService(Database db, Clock clock) { this.db = db; this.clock = clock; }

    public void login(String username, String password) {
        Session.logout();
        if (username == null || username.isBlank() || password == null || password.length()>128)
            throw new ValidationException("auth", "Informe usuário e senha.");
        AppUser authenticated = db.transaction(c -> {
            AppUser user = dao.lockUser(c, username.strip());
            if (user == null || !Passwords.verify(password,user.passwordHash()))
                throw new ValidationException("auth", "Usuário ou senha incorretos.");
            if (!user.passwordHash().startsWith("pbkdf2$")) {
                String secure = Passwords.hash(password);
                dao.execute(c,"UPDATE app_user SET password_hash=? WHERE id=?",secure,user.id());
                user = new AppUser(user.id(),user.name(),user.username(),secure,user.role());
            }
            return user;
        });
        Session.login(authenticated);
    }

    public List<UserInfo> users() { requireRole(Role.GERENTE); return db.transaction(dao::users); }
    public void createUser(String name,String username,String password,Role role) {
        requireRole(Role.GERENTE);
        String n=required(name,"name","o nome",150),u=required(username,"username","o usuário",50).toLowerCase(Locale.ROOT);
        if(!u.matches("[a-z0-9._-]{3,50}"))throw new ValidationException("username","Use 3 a 50 letras, números, ponto, hífen ou sublinhado.");
        if(role==null)throw new ValidationException("role","Selecione um perfil.");
        Passwords.validate(password);String hash=Passwords.hash(password);
        db.transaction(c->{try{dao.insert(c,"INSERT INTO app_user(name,username,password_hash,role) VALUES(?,?,?,?)",n,u,hash,role.name());}
            catch(SQLException e){if("23505".equals(e.getSQLState()))throw new ValidationException("username","Usuário já cadastrado.");throw e;}return null;});
    }
    public void changePassword(String current,String password) {
        requireAuthenticated();Passwords.validate(password);AppUser session=Session.getUser();
        AppUser changed=db.transaction(c->{AppUser user=dao.lockUser(c,session.username());
            if(user==null||!Passwords.verify(current,user.passwordHash()))throw new ValidationException("current","Senha atual incorreta.");
            if(Passwords.verify(password,user.passwordHash()))throw new ValidationException("password","Escolha uma senha diferente da atual.");
            String hash=Passwords.hash(password);dao.execute(c,"UPDATE app_user SET password_hash=? WHERE id=?",hash,user.id());
            dao.execute(c,"DELETE FROM password_reset WHERE user_id=?",user.id());
            return new AppUser(user.id(),user.name(),user.username(),hash,user.role());});
        Session.login(changed);
    }
    public String issueResetCode(String username,String managerPassword) {
        requireRole(Role.GERENTE);AppUser manager=Session.getUser();
        return db.transaction(c->{AppUser actual=dao.findUserByUsername(c,manager.username());
            if(actual==null||!Passwords.verify(managerPassword,actual.passwordHash()))throw new ValidationException("current","Confirme a senha do gerente.");
            AppUser target=dao.lockUser(c,required(username,"username","o usuário",50));
            if(target==null)throw new ValidationException("username","Usuário não encontrado.");
            String code=Passwords.token();dao.execute(c,"DELETE FROM password_reset WHERE user_id=?",target.id());
            dao.execute(c,"INSERT INTO password_reset(user_id,token_hash,expires_at) VALUES(?,?,?)",target.id(),Passwords.digest(code),LocalDateTime.now(clock).plusMinutes(15));return code;});
    }
    public void recoverPassword(String username,String code,String password) {
        Passwords.validate(password);
        if(username==null||code==null||code.length()>100)throw new ValidationException("auth","Código inválido ou expirado.");
        long id=db.transaction(c->{AppUser user=dao.lockUser(c,username.strip());
            if(user==null)throw new ValidationException("auth","Código inválido ou expirado.");
            try(var p=c.prepareStatement("SELECT token_hash,expires_at FROM password_reset WHERE user_id=?")){
                p.setLong(1,user.id());try(var r=p.executeQuery()){
                    if(!r.next()||!r.getObject(2,LocalDateTime.class).isAfter(LocalDateTime.now(clock))||
                        !java.security.MessageDigest.isEqual(r.getString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8),Passwords.digest(code.strip()).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        throw new ValidationException("auth","Código inválido ou expirado.");
                }
            }
            dao.execute(c,"UPDATE app_user SET password_hash=? WHERE id=?",Passwords.hash(password),user.id());
            dao.execute(c,"DELETE FROM password_reset WHERE user_id=?",user.id());return user.id();});
        if(Session.getUser()!=null&&Session.getUser().id()==id)Session.logout();
    }
    // All profiles need these reads to display customer/vehicle/item data in an OS.
    public List<Customer> customers() { requireAuthenticated(); return db.transaction(dao::customers); }
    public List<Vehicle> vehicles() { requireAuthenticated(); return db.transaction(dao::vehicles); }
    public List<ServiceOrder> orders() { requireAuthenticated(); return db.transaction(dao::orders); }
    public List<Part> parts() { requireAuthenticated(); return db.transaction(dao::parts); }
    public List<OrderItem> items(long id) { requireAuthenticated(); return db.transaction(c -> dao.items(c, id)); }
    public ServiceOrder order(long id) { requireAuthenticated(); return db.transaction(c -> requiredOrder(c, id)); }
    public Vehicle vehicle(long id) { requireAuthenticated(); return db.transaction(c -> dao.vehicle(c, id, false)); }
    private void requireAuthenticated() { requireRole(Role.values()); }
    public LocalDate today() { return LocalDate.now(clock); }
    public OrderReport report(ReportFilter filter) {
        requireRole(Role.GERENTE);
        if (filter == null) throw new ValidationException("filter", "Informe os filtros do relatório.");
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to()))
            throw new ValidationException("from", "A data inicial não pode ser posterior à data final.");
        if (filter.search().length() > 150) throw new ValidationException("search", "Pesquisa: máximo de 150 caracteres.");
        return db.transaction(c -> new OrderReport(filter,LocalDateTime.now(clock),dao.report(c,filter)));
    }

    public void exportReport(OrderReport report, java.nio.file.Path target) throws java.io.IOException {
        requireRole(Role.GERENTE);
        new ReportPdfExporter().write(report,target);
    }
    public BigDecimal receivedTotal() {
        requireRole(Role.GERENTE);
        return orders().stream().filter(o -> o.status() == OrderStatus.CLOSED)
            .map(ServiceOrder::budgetTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String required(String value, String field, String label, int max) {
        if (value == null || value.isBlank()) throw new ValidationException(field, "Preencha " + label + ".");
        String text = value.strip();
        if (text.length() > max) throw new ValidationException(field, label + ": máximo de " + max + " caracteres.");
        return text;
    }
    private long integer(String value, String field, String label, long min, long max) {
        required(value, field, label, 20);
        try { long n = Long.parseLong(value.strip()); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new ValidationException(field, label + ": informe um inteiro entre " + min + " e " + max + "."); }
    }
    private BigDecimal money(String value) {
        required(value, "price", "o valor unitário", 20);
        try {
            String normalized = value.strip();
            if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(',', '.');
            BigDecimal amount = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999999.99")) > 0) throw new NumberFormatException();
            return amount;
        } catch (ArithmeticException | NumberFormatException e) { throw new ValidationException("price", "Informe um valor positivo com até duas casas decimais (ex.: 120,50)."); }
    }

    private void requireRole(Role... allowedRoles) {
        AppUser user = Session.getUser();
        if (user == null) throw new ValidationException("auth", "Usuário não autenticado.");

        boolean authorized = Arrays.asList(allowedRoles).contains(user.role());
        if (!authorized) {
            throw new ValidationException("auth", "Seu perfil (" + user.role() + ") não tem permissão para esta ação.");
        }
    }

    private LocalDate date(String text, String field, String label) {
        required(text, field, label, 10);
        try { return LocalDate.parse(text.strip(), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)); }
        catch (DateTimeParseException e) { throw new ValidationException(field, label + ": use uma data válida no formato dd/MM/aaaa."); }
    }
    private void requireCustomer(Connection c, Long id) throws SQLException {
        if (id == null || !dao.customerExists(c, id)) throw new ValidationException("customer", "Selecione um cliente cadastrado. Use Cadastrar cliente se necessário.");
    }
    public Customer addCustomer(String name, String phone, String email) {
        requireRole(Role.GERENTE, Role.ATENDENTE);
        String n = required(name,"name","o nome",150), p = required(phone,"phone","o telefone",40), e = required(email,"email","o e-mail",150);
        if (!e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new ValidationException("email", "Informe um e-mail válido.");
        return db.transaction(c -> new Customer(dao.insert(c, "INSERT INTO customer(name,phone,email) VALUES(?,?,?)",n,p,e),n,p,e));
    }
    public Vehicle addVehicle(String plate, String brand, String model, String mileage, String year, Long customerId) {
        requireRole(Role.GERENTE, Role.ATENDENTE);
        String p = required(plate,"plate","a placa",12).toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (!p.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}")) throw new ValidationException("plate", "Placa inválida. Use ABC1234 ou ABC1D23.");
        String b = required(brand,"brand","a marca",80), m = required(model,"model","o modelo",100);
        long km = integer(mileage,"mileage","Quilometragem",0,999999999);
        int y = (int)integer(year,"year","Ano",1886,today().getYear()+1);
        return db.transaction(c -> {
            requireCustomer(c, customerId);
            Vehicle existing = dao.byPlate(c,p);
            if (existing != null) throw duplicate(existing);
            try {
                long id = dao.insert(c,"INSERT INTO vehicle(plate,brand,model,mileage,manufacture_year,customer_id) VALUES(?,?,?,?,?,?)",p,b,m,km,y,customerId);
                return new Vehicle(id,p,b,m,km,y,customerId);
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) { existing = dao.byPlate(c,p); if (existing != null) throw duplicate(existing); }
                throw e;
            }
        });
    }
    private ValidationException duplicate(Vehicle v) {
        return new ValidationException(Map.of("plate", "Placa já cadastrada: " + v + ". O veículo existente será exibido."), v.id());
    }
    public ServiceOrder openOrder(Long customerId, Long vehicleId, String complaint, String entryDate, String mileage, String responsible) {
        requireRole(Role.GERENTE, Role.ATENDENTE);
        return db.transaction(c -> {
            requireCustomer(c,customerId);
            Vehicle v = vehicleId == null ? null : dao.vehicle(c,vehicleId,true);
            if (v == null) throw new ValidationException("vehicle","Selecione um veículo cadastrado. Use Cadastrar veículo se necessário.");
            if (v.customerId() != customerId) throw new ValidationException("vehicle","O veículo selecionado não pertence ao cliente.");
            Long active = dao.activeOrder(c,vehicleId);
            if (active != null) throw new ValidationException("vehicle","Este veículo já possui a OS-" + String.format("%05d",active) + " em aberto. Encerre o atendimento antes de abrir outra OS.");
            String text = required(complaint,"complaint","a reclamação do cliente",2000), person = required(responsible,"responsible","o responsável",150);
            LocalDate date = date(entryDate,"entryDate","Data de entrada");
            long km = integer(mileage,"mileage","Quilometragem atual",0,999999999);
            long id = dao.insert(c,"INSERT INTO service_order(customer_id,vehicle_id,complaint,entry_date,mileage,responsible,status) VALUES(?,?,?,?,?,?,'OPEN')",customerId,vehicleId,text,date,km,person);
            dao.execute(c,"INSERT INTO active_order(vehicle_id,order_id) VALUES(?,?)",vehicleId,id);
            dao.execute(c,"UPDATE vehicle SET mileage=? WHERE id=?",km,vehicleId);
            return dao.order(c,id,false);
        });
    }
    private ServiceOrder requiredOrder(Connection c, long id) throws SQLException {
        ServiceOrder order = dao.order(c,id,true);
        if (order == null) throw new ValidationException("order","Selecione uma ordem de serviço existente.");
        return order;
    }
    private ServiceOrder editable(Connection c, long id) throws SQLException {
        ServiceOrder order = requiredOrder(c,id);
        if (order.status() != OrderStatus.OPEN) throw new ValidationException("order","Os itens e o diagnóstico só podem ser alterados enquanto a OS estiver Aberta. Orçamento já decidido ou OS encerrada.");
        return order;
    }
    public void saveDiagnosis(long orderId, String diagnosis) {
        requireRole(Role.MECANICO);
        String text = required(diagnosis,"diagnosis","o diagnóstico técnico",4000);
        db.transaction(c -> { editable(c,orderId); dao.execute(c,"UPDATE service_order SET diagnosis=? WHERE id=?",text,orderId); return null; });
    }
    public void addService(long orderId, String description, String quantity, String unitPrice) {
        requireRole(Role.MECANICO);
        String text = required(description,"description","a descrição do serviço",200);
        int q = (int)integer(quantity,"quantity","Quantidade",1,9999);
        BigDecimal price = money(unitPrice);
        db.transaction(c -> { ServiceOrder order=editable(c,orderId);required(order.diagnosis(),"diagnosis","o diagnóstico técnico antes de incluir serviços",4000); dao.insert(c,"INSERT INTO order_item(order_id,kind,description,quantity,unit_price) VALUES(?,'SERVICE',?,?,?)",orderId,text,q,price); return null; });
    }
    public void addPart(String name, String unitPrice, String quantity) {
        requireRole(Role.GERENTE);
        String text = required(name,"name","o nome da peça",150);
        BigDecimal price = money(unitPrice);
        int q = (int)integer(quantity,"quantity","Quantidade de entrada",1,999999);
        db.transaction(c -> {
            try {
                Part existing=null;
                for(Part p:dao.parts(c))if(p.name().equals(text)){existing=dao.part(c,p.id(),true);break;}
                long id;
                if(existing==null)id=dao.insert(c,"INSERT INTO part(name,unit_price,stock) VALUES(?,?,?)",text,price,q);
                else {
                    if((long)existing.stock()+q>999999999)throw new ValidationException("quantity","Limite de estoque excedido.");
                    id=existing.id();dao.execute(c,"UPDATE part SET stock=stock+?,unit_price=? WHERE id=?",q,price,id);
                }
                movement(c,id,null,q,"Entrada de peças");
            }
            catch (SQLException e) { if ("23505".equals(e.getSQLState())) throw new ValidationException("name","Peça já cadastrada. Use Repor selecionada."); throw e; }
            return null;
        });
    }
    public void replenish(Long id, String quantity) {
        requireRole(Role.GERENTE);
        int q = (int)integer(quantity,"quantity","Quantidade de entrada",1,999999);
        db.transaction(c -> {
            Part part = id == null ? null : dao.part(c,id,true);
            if (part == null) throw new ValidationException("part","Selecione uma peça.");
            if ((long)part.stock()+q > 999999999) throw new ValidationException("quantity","Limite de estoque excedido.");
            dao.execute(c,"UPDATE part SET stock=stock+? WHERE id=?",q,id); movement(c,id,null,q,"Reposição");return null;
        });
    }
    public void consumePart(long orderId, Long partId, String quantity) {
        requireRole(Role.GERENTE);
        int q = (int)integer(quantity,"quantity","Quantidade utilizada",1,9999);
        db.transaction(c -> {
            editable(c,orderId);
            Part part = partId == null ? null : dao.part(c,partId,true);
            if (part == null) throw new ValidationException("part","Selecione uma peça cadastrada.");
            if (part.stock() < q) throw new ValidationException("quantity","Estoque insuficiente. Saldo atual: " + part.stock() + ".");
            dao.execute(c,"UPDATE part SET stock=stock-? WHERE id=?",q,partId);
            dao.insert(c,"INSERT INTO order_item(order_id,kind,description,quantity,unit_price,part_id) VALUES(?,'PART',?,?,?,?)",orderId,part.name(),q,part.unitPrice(),partId);
            movement(c,partId,orderId,-q,"Consumo na OS");
            return null;
        });
    }
    public void removeItem(long orderId, long itemId) {
        requireRole(Role.GERENTE, Role.MECANICO);
        db.transaction(c -> {
            editable(c,orderId);
            OrderItem item = dao.items(c,orderId).stream().filter(i -> i.id()==itemId).findFirst().orElseThrow(() -> new ValidationException("item","Selecione um item desta OS."));
            requireRole(item.kind().equals("PART") ? Role.GERENTE : Role.MECANICO);
            if (item.partId()!=null) returnPart(c,item,orderId,"Item removido");
            dao.execute(c,"DELETE FROM order_item WHERE id=?",itemId); return null;
        });
    }
    private Budget budget(Connection c, ServiceOrder order) throws SQLException {
        List<OrderItem> items = dao.items(c,order.id());
        if (items.isEmpty()) throw new ValidationException("items","Esta OS não possui itens. Registre serviços e/ou peças antes de gerar o orçamento.");
        BigDecimal services = new BigDecimal("0.00"), parts = new BigDecimal("0.00");
        for (OrderItem item : items) { if (item.kind().equals("SERVICE")) services = services.add(item.subtotal()); else parts = parts.add(item.subtotal()); }
        return new Budget(order,items,services,parts,services.add(parts));
    }
    public Budget budget(long id) {
        requireRole(Role.GERENTE, Role.ATENDENTE);
        return db.transaction(c -> budget(c,requiredOrder(c,id)));
    }

    public Budget decideBudget(long id, boolean approved, String responsible) {
        requireRole(Role.GERENTE, Role.ATENDENTE);
        String person = required(responsible,"decisionBy","o responsável pela decisão",150);
        return db.transaction(c -> {
            ServiceOrder order = editable(c,id);
            Budget budget = budget(c,order);
            dao.execute(c,"UPDATE service_order SET status=?,decision_at=?,decision_by=?,budget_total=? WHERE id=?",approved ? "APPROVED" : "REJECTED",LocalDateTime.now(clock),person,budget.total(),id);
            snapshot(c,dao.order(c,id,false),approved?"APROVACAO":"REJEICAO","Decisão registrada por "+person);
            return budget(c,dao.order(c,id,false));
        });
    }
    public void completeService(long id, long itemId) {
        completeService(id,itemId,"");
    }
    public void completeService(long id,long itemId,String observations) {
        requireRole(Role.MECANICO);
        String note=observations==null?"":observations.strip();
        if(note.length()>2000)throw new ValidationException("observations","Observações: máximo de 2000 caracteres.");
        db.transaction(c -> {
            ServiceOrder order=requiredOrder(c,id);
            if (order.status()!=OrderStatus.APPROVED) throw new ValidationException("order","A execução exige orçamento aprovado.");
            required(order.diagnosis(),"diagnosis","o diagnóstico técnico",4000);
            if (dao.execute(c,"UPDATE order_item SET completed=TRUE,observations=? WHERE id=? AND order_id=? AND kind='SERVICE'",note,itemId,id)==0) throw new ValidationException("item","Selecione um serviço desta OS.");
            event(c,id,"SERVICO_CONCLUIDO","Item "+itemId+": "+note);
            return null;
        });
    }
    public void closeOrder(long id, String payment, String pickup) {
        closeOrder(id,payment,pickup,true);
    }
    public void closeOrder(long id, String payment, String pickup, boolean received) {
        requireRole(Role.GERENTE, Role.ATENDENTE);
        if (!received) throw new ValidationException("payment","Confirme o recebimento antes de encerrar.");
        String method = required(payment,"payment","a forma de pagamento",40);
        LocalDate date = date(pickup,"pickup","Data de retirada");
        db.transaction(c -> {
            ServiceOrder order = requiredOrder(c,id);
            if (order.status()!=OrderStatus.APPROVED) throw new ValidationException("order","Somente uma OS com orçamento aprovado pode ser fechada.");
            Budget budget = budget(c,order);
            if (budget.items().stream().anyMatch(i -> i.kind().equals("SERVICE") && !i.completed())) throw new ValidationException("items","Conclua os serviços antes de fechar a OS.");
            if (date.isBefore(order.entryDate())) throw new ValidationException("pickup","A retirada não pode ser anterior à entrada.");
            dao.execute(c,"UPDATE service_order SET status='CLOSED',payment_method=?,pickup_date=? WHERE id=?",method,date,id);
            dao.execute(c,"DELETE FROM active_order WHERE order_id=?",id); return null;
        });
    }

    public List<HistoryEntry> orderHistory(long id){requireAuthenticated();return db.transaction(c->dao.orderHistory(c,id));}
    public List<HistoryEntry> stockHistory(long id){requireRole(Role.GERENTE);return db.transaction(c->dao.stockHistory(c,id));}
    private void event(Connection c,long id,String type,String details)throws SQLException{
        dao.insert(c,"INSERT INTO order_event(order_id,occurred_at,actor,event_type,details) VALUES(?,?,?,?,?)",id,LocalDateTime.now(clock),Session.getUser().username(),type,details);
    }
    private void snapshot(Connection c,ServiceOrder order,String type,String reason)throws SQLException{
        event(c,order.id(),type,reason+" | Estado: "+order.status()+" | Total: "+order.budgetTotal()+" | Decisão: "+order.decisionAt()+" | Responsável: "+order.decisionBy()+" | Diagnóstico: "+order.diagnosis());
        for(OrderItem item:dao.items(c,order.id()))event(c,order.id(),"ITEM_"+type,item.toString());
    }
    private void movement(Connection c,long part,Long order,int quantity,String reason)throws SQLException{
        dao.insert(c,"INSERT INTO stock_movement(part_id,order_id,quantity,balance,occurred_at,actor,reason) VALUES(?,?,?,?,?,?,?)",part,order,quantity,dao.part(c,part,false).stock(),LocalDateTime.now(clock),Session.getUser().username(),reason);
    }
    private void returnPart(Connection c,OrderItem item,long order,String reason)throws SQLException{
        Part part=dao.part(c,item.partId(),true);
        if((long)part.stock()+item.quantity()>999999999)throw new ValidationException("quantity","Devolução excede o limite de estoque. Regularize o saldo antes de cancelar.");
        dao.execute(c,"UPDATE part SET stock=stock+? WHERE id=?",item.quantity(),part.id());movement(c,part.id(),order,item.quantity(),reason);
    }
    public void reviseRejectedOrder(long id,String reason){
        requireRole(Role.GERENTE,Role.ATENDENTE);String text=required(reason,"reason","o motivo da revisão",1000);
        db.transaction(c->{ServiceOrder order=requiredOrder(c,id);
            if(order.status()!=OrderStatus.REJECTED)throw new ValidationException("order","Somente uma OS rejeitada pode voltar para revisão.");
            snapshot(c,order,"REVISAO",text);
            dao.execute(c,"UPDATE service_order SET status='OPEN',decision_at=NULL,decision_by=NULL,budget_total=NULL WHERE id=?",id);
            return null;});
    }
    public void cancelRejectedOrder(long id,String reason){
        requireRole(Role.GERENTE,Role.ATENDENTE);String text=required(reason,"reason","o motivo do cancelamento",1000);
        db.transaction(c->{ServiceOrder order=requiredOrder(c,id);
            if(order.status()!=OrderStatus.REJECTED)throw new ValidationException("order","Somente uma OS rejeitada pode ser cancelada por este fluxo.");
            snapshot(c,order,"CANCELAMENTO",text);
            List<OrderItem> parts=new ArrayList<>(dao.items(c,id).stream().filter(i->i.partId()!=null).toList());parts.sort(Comparator.comparing(OrderItem::partId));
            for(OrderItem item:parts)returnPart(c,item,id,"Cancelamento de OS rejeitada");
            dao.execute(c,"UPDATE service_order SET status='CANCELLED' WHERE id=?",id);
            dao.execute(c,"DELETE FROM active_order WHERE order_id=?",id);return null;});
    }
}
