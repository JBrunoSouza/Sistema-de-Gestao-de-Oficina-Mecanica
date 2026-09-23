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

    // 1. Metodo privado para gerar o SHA-256
    private String hash(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro interno de criptografia.", e);
        }
    }

    public void login(String username, String passwordDigitada) {
        Session.logout();
        if (username == null || username.isBlank() || passwordDigitada == null || passwordDigitada.isEmpty())
            throw new ValidationException("auth", "Informe usuário e senha.");
        String login = username.strip();
        String hashDigitado = hash(passwordDigitada);

        db.transaction(c -> {
            try {
                AppUser user = dao.findUserByUsername(c, login);

                if (user == null || !user.passwordHash().equals(hashDigitado)) {
                    throw new ValidationException("auth", "Usuário ou senha incorretos.");
                }
                Session.login(user);
                return null;
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao acessar banco de dados", e);
            }
        });
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
        String text = required(complaint,"complaint","a reclamação do cliente",2000), person = required(responsible,"responsible","o responsável",150);
        LocalDate date = date(entryDate,"entryDate","Data de entrada");
        long km = integer(mileage,"mileage","Quilometragem atual",0,999999999);
        return db.transaction(c -> {
            requireCustomer(c,customerId);
            Vehicle v = vehicleId == null ? null : dao.vehicle(c,vehicleId,true);
            if (v == null) throw new ValidationException("vehicle","Selecione um veículo cadastrado. Use Cadastrar veículo se necessário.");
            if (v.customerId() != customerId) throw new ValidationException("vehicle","O veículo selecionado não pertence ao cliente.");
            Long active = dao.activeOrder(c,vehicleId);
            if (active != null) throw new ValidationException("vehicle","Este veículo já possui a OS-" + String.format("%05d",active) + " em aberto. Encerre o atendimento antes de abrir outra OS.");
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
        requireRole(Role.GERENTE, Role.MECANICO);
        String text = required(diagnosis,"diagnosis","o diagnóstico técnico",4000);
        db.transaction(c -> { editable(c,orderId); dao.execute(c,"UPDATE service_order SET diagnosis=? WHERE id=?",text,orderId); return null; });
    }
    public void addService(long orderId, String description, String quantity, String unitPrice) {
        requireRole(Role.GERENTE, Role.MECANICO);
        String text = required(description,"description","a descrição do serviço",200);
        int q = (int)integer(quantity,"quantity","Quantidade",1,9999);
        BigDecimal price = money(unitPrice);
        db.transaction(c -> { editable(c,orderId); dao.insert(c,"INSERT INTO order_item(order_id,kind,description,quantity,unit_price) VALUES(?,'SERVICE',?,?,?)",orderId,text,q,price); return null; });
    }
    public void addPart(String name, String unitPrice, String quantity) {
        requireRole(Role.GERENTE);
        String text = required(name,"name","o nome da peça",150);
        BigDecimal price = money(unitPrice);
        int q = (int)integer(quantity,"quantity","Quantidade de entrada",1,999999);
        db.transaction(c -> {
            try { dao.insert(c,"INSERT INTO part(name,unit_price,stock) VALUES(?,?,?)",text,price,q); }
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
            dao.execute(c,"UPDATE part SET stock=stock+? WHERE id=?",q,id); return null;
        });
    }
    public void consumePart(long orderId, Long partId, String quantity) {
        requireRole(Role.GERENTE, Role.MECANICO);
        int q = (int)integer(quantity,"quantity","Quantidade utilizada",1,9999);
        db.transaction(c -> {
            editable(c,orderId);
            Part part = partId == null ? null : dao.part(c,partId,true);
            if (part == null) throw new ValidationException("part","Selecione uma peça cadastrada.");
            if (part.stock() < q) throw new ValidationException("quantity","Estoque insuficiente. Saldo atual: " + part.stock() + ".");
            dao.execute(c,"UPDATE part SET stock=stock-? WHERE id=?",q,partId);
            dao.insert(c,"INSERT INTO order_item(order_id,kind,description,quantity,unit_price,part_id) VALUES(?,'PART',?,?,?,?)",orderId,part.name(),q,part.unitPrice(),partId);
            return null;
        });
    }
    public void removeItem(long orderId, long itemId) {
        requireRole(Role.GERENTE, Role.MECANICO);
        db.transaction(c -> {
            editable(c,orderId);
            OrderItem item = dao.items(c,orderId).stream().filter(i -> i.id()==itemId).findFirst().orElseThrow(() -> new ValidationException("item","Selecione um item desta OS."));
            if (item.partId()!=null) dao.execute(c,"UPDATE part SET stock=stock+? WHERE id=?",item.quantity(),item.partId());
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
            return budget(c,dao.order(c,id,false));
        });
    }
    public void completeService(long id, long itemId) {
        requireRole(Role.GERENTE, Role.MECANICO);
        db.transaction(c -> {
            if (requiredOrder(c,id).status()!=OrderStatus.APPROVED) throw new ValidationException("order","A execução exige orçamento aprovado.");
            if (dao.execute(c,"UPDATE order_item SET completed=TRUE WHERE id=? AND order_id=? AND kind='SERVICE'",itemId,id)==0) throw new ValidationException("item","Selecione um serviço desta OS.");
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
}
