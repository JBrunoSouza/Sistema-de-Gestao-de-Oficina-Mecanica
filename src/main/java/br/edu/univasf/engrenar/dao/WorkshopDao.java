package br.edu.univasf.engrenar.dao;

import br.edu.univasf.engrenar.model.*;
import java.sql.*;
import java.util.*;

/** JDBC somente: validacoes e transicoes de estado pertencem ao service. */
public final class WorkshopDao {
    public List<OrderReport.Row> report(Connection c, ReportFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT o.id,c.name,v.plate,o.entry_date,o.status,o.budget_total " +
            "FROM service_order o JOIN customer c ON c.id=o.customer_id JOIN vehicle v ON v.id=o.vehicle_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (filter.from() != null) { sql.append(" AND o.entry_date>=?"); args.add(filter.from()); }
        if (filter.to() != null) { sql.append(" AND o.entry_date<=?"); args.add(filter.to()); }
        if (filter.status() != null) { sql.append(" AND o.status=?"); args.add(filter.status().name()); }
        if (!filter.search().isBlank()) {
            sql.append(" AND (LOWER(c.name) LIKE ? ESCAPE '!' OR LOWER(v.plate) LIKE ? ESCAPE '!')");
            String term = "%" + filter.search().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            args.add(term); args.add(term);
        }
        sql.append(" ORDER BY o.entry_date DESC,o.id DESC");
        return query(c, sql.toString(), r -> new OrderReport.Row(r.getLong(1),r.getString(2),r.getString(3),
            r.getObject(4,java.time.LocalDate.class),OrderStatus.valueOf(r.getString(5)),r.getBigDecimal(6)),args.toArray());
    }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet r) throws SQLException; }
    private <T> List<T> query(Connection c, String sql, Mapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p, args);
            try (ResultSet r = p.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (r.next()) result.add(mapper.map(r));
                return result;
            }
        }
    }
    private void bind(PreparedStatement p, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
    }
    public int execute(Connection c, String sql, Object... args) throws SQLException {
        try (var p = c.prepareStatement(sql)) { bind(p, args); return p.executeUpdate(); }
    }
    public long insert(Connection c, String sql, Object... args) throws SQLException {
        try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(p, args); p.executeUpdate();
            try (var r = p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("ID não retornado"); return r.getLong(1); }
        }
    }
    public List<Customer> customers(Connection c) throws SQLException {
        return query(c, "SELECT * FROM customer ORDER BY name", r -> new Customer(r.getLong("id"), r.getString("name"), r.getString("phone"), r.getString("email")));
    }
    public boolean customerExists(Connection c, long id) throws SQLException {
        return !query(c, "SELECT id FROM customer WHERE id=?", r -> r.getLong(1), id).isEmpty();
    }
    private Vehicle vehicle(ResultSet r) throws SQLException {
        return new Vehicle(r.getLong("id"), r.getString("plate"), r.getString("brand"), r.getString("model"), r.getLong("mileage"), r.getInt("manufacture_year"), r.getLong("customer_id"));
    }
    public List<Vehicle> vehicles(Connection c) throws SQLException { return query(c, "SELECT * FROM vehicle ORDER BY plate", this::vehicle); }
    public Vehicle vehicle(Connection c, long id, boolean lock) throws SQLException {
        return query(c, "SELECT * FROM vehicle WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::vehicle, id).stream().findFirst().orElse(null);
    }
    public Vehicle byPlate(Connection c, String plate) throws SQLException {
        return query(c, "SELECT * FROM vehicle WHERE plate=?", this::vehicle, plate).stream().findFirst().orElse(null);
    }
    public Long activeOrder(Connection c, long vehicleId) throws SQLException {
        return query(c, "SELECT order_id FROM active_order WHERE vehicle_id=?", r -> r.getLong(1), vehicleId).stream().findFirst().orElse(null);
    }
    private ServiceOrder order(ResultSet r) throws SQLException {
        return new ServiceOrder(r.getLong("id"), r.getLong("customer_id"), r.getLong("vehicle_id"), r.getString("complaint"),
            r.getObject("entry_date", java.time.LocalDate.class), r.getLong("mileage"), r.getString("responsible"),
            OrderStatus.valueOf(r.getString("status")), r.getString("diagnosis"), r.getObject("decision_at", java.time.LocalDateTime.class),
            r.getString("decision_by"), r.getBigDecimal("budget_total"), r.getString("payment_method"), r.getObject("pickup_date", java.time.LocalDate.class));
    }
    public ServiceOrder order(Connection c, long id, boolean lock) throws SQLException {
        return query(c, "SELECT * FROM service_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::order, id).stream().findFirst().orElse(null);
    }
    public List<ServiceOrder> orders(Connection c) throws SQLException { return query(c, "SELECT * FROM service_order ORDER BY id DESC", this::order); }
    public List<OrderItem> items(Connection c, long orderId) throws SQLException {
        return query(c, "SELECT * FROM order_item WHERE order_id=? ORDER BY id", r -> new OrderItem(r.getLong("id"), r.getLong("order_id"),
            r.getString("kind"), r.getString("description"), r.getInt("quantity"), r.getBigDecimal("unit_price"), (Long)r.getObject("part_id"), r.getBoolean("completed")), orderId);
    }
    private Part part(ResultSet r) throws SQLException { return new Part(r.getLong("id"), r.getString("name"), r.getBigDecimal("unit_price"), r.getInt("stock")); }
    public List<Part> parts(Connection c) throws SQLException { return query(c, "SELECT * FROM part ORDER BY name", this::part); }
    public Part part(Connection c, long id, boolean lock) throws SQLException {
        return query(c, "SELECT * FROM part WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::part, id).stream().findFirst().orElse(null);
    }
    private AppUser appUser(ResultSet r) throws SQLException {
        return new AppUser(
                r.getLong("id"),
                r.getString("name"),
                r.getString("username"),
                r.getString("password_hash"),
                Role.valueOf(r.getString("role"))
        );
    }

    public AppUser findUserByUsername(Connection c, String username) throws SQLException {
        return query(c, "SELECT * FROM app_user WHERE username=?", this::appUser, username)
                .stream().findFirst().orElse(null);
    }
}
