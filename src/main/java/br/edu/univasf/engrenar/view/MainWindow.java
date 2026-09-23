package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public final class MainWindow extends BorderPane {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final Stage primaryStage;
    private final WorkshopService service;
    private final StackPane content = new StackPane();
    private final Label message = new Label("Pronto para atender. Os dados são salvos automaticamente neste computador.");
    private final Map<String, Button> navButtons = new HashMap<>();
    private String currentPage = "Visão geral";

    public record OrderRow(long id, String number, String customerName, String plate, String entryDate, String responsible, OrderStatus status) {}
    public record VehicleRow(long id, String plate, String brand, String model, int year, long mileage, String customerName) {}
    public record ItemRow(long id, String kind, String description, int quantity, String unitPrice, String subtotal, String execution) {}

    public MainWindow(Stage primaryStage, WorkshopService service, String dataPath) {
        this.primaryStage = primaryStage;
        this.service = service;

        setPrefSize(1200, 800);
        setMinSize(1000, 700);

        // Barra Lateral (Sidebar)
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(220);

        Label brand = new Label("ENGRENAR");
        brand.getStyleClass().add("brand-title");

        Label caption = new Label("Gestão de oficina");
        caption.getStyleClass().add("brand-subtitle");

        // Identificação do usuário atual
        AppUser currentUser = Session.getUser();
        Role role = currentUser.role();
        Label lblUser = new Label("Olá, " + currentUser.name());
        lblUser.setStyle("-fx-text-fill: #f48b32; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");

        sidebar.getChildren().addAll(brand, caption, lblUser, new Region() {{ setMinHeight(15); }});

        // 12. Ocultar Menus com base na Role
        List<String> menuOrder = new ArrayList<>();
        menuOrder.add("Visão geral");

        if (role == Role.GERENTE || role == Role.ATENDENTE) {
            menuOrder.add("Clientes");
            menuOrder.add("Veículos");
        }

        menuOrder.add("Ordens de serviço");

        if (role == Role.GERENTE) {
            menuOrder.add("Estoque");
            menuOrder.add("Relatórios");
            menuOrder.add("Usuários");
        }

        for (String name : menuOrder) {
            Button b = button(name, "nav-" + name, () -> showPage(name));
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            navButtons.put(name, b);
            sidebar.getChildren().add(b);
        }

        Region glue = new Region();
        VBox.setVgrow(glue, Priority.ALWAYS);
        sidebar.getChildren().add(glue);


        // 14. Botão de Logout
        Button btnSair = new Button("Sair");
        btnSair.getStyleClass().add("nav-button");
        btnSair.setMaxWidth(Double.MAX_VALUE);
        btnSair.setStyle("-fx-text-fill: #ff9292;");
        btnSair.setOnAction(e -> {
            primaryStage.getScene().getAccelerators().clear();
            Session.logout();
            LoginForm loginForm = new LoginForm(service, () -> {
                MainWindow novaMain = new MainWindow(primaryStage, service, dataPath);
                primaryStage.getScene().setRoot(novaMain);
                novaMain.setupKeyShortcuts(primaryStage.getScene());
            });
            primaryStage.getScene().setRoot(loginForm);
        });
        sidebar.getChildren().add(button("Alterar minha senha","change-password",()->AccountsPane.changePassword(primaryStage,service)));
        sidebar.getChildren().add(btnSair);
        Label local = new Label("●  Banco local · PostgreSQL");
        local.getStyleClass().add("db-indicator");
        sidebar.getChildren().add(local);

        setLeft(sidebar);

        // Área Central e Barra de Status
        content.setPadding(new Insets(24, 30, 20, 30));

        HBox statusBar = new HBox(message);
        statusBar.getStyleClass().add("status-bar");
        message.getStyleClass().add("status-message");
        if (dataPath != null && !dataPath.isBlank()) {
            message.setTooltip(new Tooltip("Dados: " + dataPath));
        }

        BorderPane mainArea = new BorderPane();
        mainArea.setCenter(content);
        mainArea.setBottom(statusBar);
        setCenter(mainArea);

        showPage(currentPage);
    }

    public void setupKeyShortcuts(Scene scene) {
        scene.getAccelerators().clear();
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), () -> run(() -> showPage("Ordens de serviço")));
        if (canOpen("Veículos")) scene.getAccelerators().put(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN), () -> run(() -> showPage("Veículos")));
    }

    private Button button(String text, String id, Runnable action) {
        Button b = new Button(text);
        b.setId(id);
        b.setOnAction(e -> run(action));
        return b;
    }

    private Button primary(String text, String id, Runnable action) {
        Button b = button(text, id, action);
        b.getStyleClass().add("primary-button");
        return b;
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            notice(e.getMessage(), true);
        } catch (Exception e) {
            e.printStackTrace();
            notice("Não foi possível concluir. Os dados não foram alterados. Verifique o banco local.", true);
        }
    }

    private void notice(String text, boolean error) {
        message.setText(text.replace('\n', ' '));
        message.getStyleClass().removeAll("status-message", "status-message-error");
        message.getStyleClass().add(error ? "status-message-error" : "status-message");
    }

    private String money(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(amount);
    }

    private Node header(String title, String subtitle, Node... actions) {
        BorderPane p = new BorderPane();
        p.setPadding(new Insets(0, 0, 16, 0));

        VBox titles = new VBox(4);
        Label h = new Label(title);
        h.getStyleClass().add("page-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("page-subtitle");
        titles.getChildren().addAll(h, sub);
        p.setLeft(titles);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getChildren().addAll(actions);
        p.setRight(buttons);

        return p;
    }

    private <T> VBox searchableTable(TableView<T> table, ObservableList<T> masterData, BiPredicate<T, String> filterPredicate) {
        TextField search = new TextField();
        search.setPromptText("Pesquisar nesta lista…");
        search.setPrefHeight(36);
        search.setMaxWidth(300);

        FilteredList<T> filteredData = new FilteredList<>(masterData, p -> true);
        search.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(item -> {
                if (newVal == null || newVal.isBlank()) return true;
                String query = newVal.toLowerCase().trim();
                return filterPredicate.test(item, query);
            });
        });

        SortedList<T> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox box = new VBox(12, search, table);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private boolean canOpen(String name) {
        AppUser user = Session.getUser();
        if (user == null) return false;
        return switch (name) {
            case "Clientes", "Veículos" -> user.role() != Role.MECANICO;
            case "Estoque", "Relatórios", "Usuários" -> user.role() == Role.GERENTE;
            default -> true;
        };
    }

    private void showPage(String name) {
        if (!canOpen(name)) throw new ValidationException("auth", "Seu perfil não tem acesso a esta página.");
        currentPage = name;
        navButtons.forEach((k, b) -> {
            b.getStyleClass().remove("nav-button-active");
            if (k.equals(name)) {
                b.getStyleClass().add("nav-button-active");
            }
        });

        content.getChildren().clear();
        BorderPane pagePane = new BorderPane();
        switch (name) {
            case "Clientes" -> customersPage(pagePane);
            case "Veículos" -> vehiclesPage(pagePane);
            case "Ordens de serviço" -> ordersPage(pagePane);
            case "Estoque" -> stockPage(pagePane);
            case "Relatórios" -> reportsPage(pagePane);
            case "Usuários" -> pagePane.setCenter(new AccountsPane(primaryStage,service));
            default -> dashboard(pagePane);
        }
        content.getChildren().add(pagePane);
    }

    private ObservableList<OrderRow> loadOrderRows(List<ServiceOrder> orders) {
        Map<Long, Vehicle> vehicles = new HashMap<>();
        service.vehicles().forEach(v -> vehicles.put(v.id(), v));
        Map<Long, Customer> customers = new HashMap<>();
        service.customers().forEach(c -> customers.put(c.id(), c));

        ObservableList<OrderRow> list = FXCollections.observableArrayList();
        for (ServiceOrder o : orders) {
            Customer c = customers.get(o.customerId());
            Vehicle v = vehicles.get(o.vehicleId());
            list.add(new OrderRow(
                    o.id(),
                    o.number(),
                    c != null ? c.name() : "N/D",
                    v != null ? v.plate() : "N/D",
                    o.entryDate().format(DATE),
                    o.responsible(),
                    o.status()
            ));
        }
        return list;
    }

    private TableView<OrderRow> createOrdersTable() {
        TableView<OrderRow> table = new TableView<>();

        TableColumn<OrderRow, String> colNum = new TableColumn<>("OS");
        colNum.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().number()));
        colNum.setPrefWidth(90);

        TableColumn<OrderRow, String> colCust = new TableColumn<>("Cliente");
        colCust.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().customerName()));
        colCust.setPrefWidth(180);

        TableColumn<OrderRow, String> colPlate = new TableColumn<>("Placa");
        colPlate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plate()));
        colPlate.setPrefWidth(100);

        TableColumn<OrderRow, String> colDate = new TableColumn<>("Entrada");
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().entryDate()));
        colDate.setPrefWidth(100);

        TableColumn<OrderRow, String> colResp = new TableColumn<>("Responsável");
        colResp.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().responsible()));
        colResp.setPrefWidth(140);

        TableColumn<OrderRow, String> colStat = new TableColumn<>("Status");
        colStat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().toString()));
        colStat.setPrefWidth(110);

        table.getColumns().addAll(List.of(colNum, colCust, colPlate, colDate, colResp, colStat));
        return table;
    }

    private void dashboard(BorderPane pagePane) {
        List<ServiceOrder> orders = service.orders();
        pagePane.setTop(header("Sua oficina, em dia", "Atendimentos, veículos e orçamento em um só lugar."));

        HBox cards = new HBox(16);
        for (OrderStatus status : List.of(OrderStatus.OPEN, OrderStatus.APPROVED, OrderStatus.CLOSED)) {
            VBox card = new VBox(6);
            card.getStyleClass().add("metric-card");
            HBox.setHgrow(card, Priority.ALWAYS);

            long count = orders.stream().filter(o -> o.status() == status).count();
            Label countLabel = new Label(String.valueOf(count));
            countLabel.getStyleClass().add("metric-number");

            Label titleLabel = new Label(status.toString());
            titleLabel.getStyleClass().add("metric-title");

            card.getChildren().addAll(countLabel, titleLabel);
            cards.getChildren().add(card);
        }

        TableView<OrderRow> recentTable = createOrdersTable();
        recentTable.setItems(loadOrderRows(orders));
        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        recentTable.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                OrderRow selected = recentTable.getSelectionModel().getSelectedItem();
                if (selected != null) detail(selected.id());
            }
        });

        VBox centerBox = new VBox(16);
        Label sectionTitle = new Label("ATENDIMENTOS RECENTES");
        sectionTitle.getStyleClass().add("field-label");
        VBox.setVgrow(recentTable, Priority.ALWAYS);
        centerBox.getChildren().addAll(cards, sectionTitle, recentTable);
        pagePane.setCenter(centerBox);

        HBox actions = new HBox(12);
        actions.setPadding(new Insets(16, 0, 0, 0));
        actions.getChildren().addAll(
                primary("Abrir ordem de serviço", "new-order", this::orderForm),
                button("Cadastrar veículo", "new-vehicle", () -> vehicleForm(null, v -> showPage("Veículos")))
        );
        pagePane.setBottom(actions);
    }

    private void customersPage(BorderPane pagePane) {
        pagePane.setTop(header("Clientes", "Contatos disponíveis para vincular aos veículos.",
                primary("+ Cadastrar cliente", "new-customer", () -> customerForm(c -> showPage(currentPage)))));

        TableView<Customer> table = new TableView<>();

        TableColumn<Customer, Long> colId = new TableColumn<>("Código");
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colId.setPrefWidth(80);

        TableColumn<Customer, String> colName = new TableColumn<>("Nome");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colName.setPrefWidth(220);

        TableColumn<Customer, String> colPhone = new TableColumn<>("Telefone");
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().phone()));
        colPhone.setPrefWidth(140);

        TableColumn<Customer, String> colEmail = new TableColumn<>("E-mail");
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().email()));
        colEmail.setPrefWidth(220);

        table.getColumns().addAll(List.of(colId, colName, colPhone, colEmail));

        ObservableList<Customer> customers = FXCollections.observableArrayList(service.customers());
        pagePane.setCenter(searchableTable(table, customers, (c, q) ->
                String.valueOf(c.id()).contains(q) ||
                c.name().toLowerCase().contains(q) ||
                c.phone().toLowerCase().contains(q) ||
                c.email().toLowerCase().contains(q)
        ));
    }

    private void vehiclesPage(BorderPane pagePane) {
        pagePane.setTop(header("Veículos", "Cadastro por placa, sempre vinculado a um cliente.",
                primary("+ Cadastrar veículo", "new-vehicle", () -> vehicleForm(null, v -> showPage(currentPage)))));

        Map<Long, String> names = new HashMap<>();
        service.customers().forEach(c -> names.put(c.id(), c.name()));

        ObservableList<VehicleRow> vehicles = FXCollections.observableArrayList();
        for (Vehicle v : service.vehicles()) {
            vehicles.add(new VehicleRow(
                    v.id(), v.plate(), v.brand(), v.model(), v.year(), v.mileage(), names.getOrDefault(v.customerId(), "N/D")
            ));
        }

        TableView<VehicleRow> table = new TableView<>();

        TableColumn<VehicleRow, String> colPlate = new TableColumn<>("Placa");
        colPlate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plate()));
        colPlate.setPrefWidth(100);

        TableColumn<VehicleRow, String> colBrand = new TableColumn<>("Marca");
        colBrand.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().brand()));
        colBrand.setPrefWidth(120);

        TableColumn<VehicleRow, String> colModel = new TableColumn<>("Modelo");
        colModel.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().model()));
        colModel.setPrefWidth(140);

        TableColumn<VehicleRow, Integer> colYear = new TableColumn<>("Ano");
        colYear.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().year()));
        colYear.setPrefWidth(80);

        TableColumn<VehicleRow, Long> colKm = new TableColumn<>("Km");
        colKm.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().mileage()));
        colKm.setPrefWidth(90);

        TableColumn<VehicleRow, String> colCust = new TableColumn<>("Cliente");
        colCust.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().customerName()));
        colCust.setPrefWidth(200);

        table.getColumns().addAll(List.of(colPlate, colBrand, colModel, colYear, colKm, colCust));

        pagePane.setCenter(searchableTable(table, vehicles, (v, q) ->
                v.plate().toLowerCase().contains(q) ||
                v.brand().toLowerCase().contains(q) ||
                v.model().toLowerCase().contains(q) ||
                String.valueOf(v.year()).contains(q) ||
                String.valueOf(v.mileage()).contains(q) ||
                v.customerName().toLowerCase().contains(q)
        ));
    }

    private void ordersPage(BorderPane pagePane) {
        List<ServiceOrder> orders = service.orders();
        TableView<OrderRow> list = createOrdersTable();
        list.setId("orders-table");

        ObservableList<OrderRow> rows = loadOrderRows(orders);

        Runnable open = () -> {
            OrderRow selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) throw new ValidationException("order", "Selecione uma OS na lista.");
            detail(selected.id());
        };

        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                run(open);
            }
        });

        pagePane.setTop(header("Ordens de serviço", "Selecione um atendimento para registrar itens e orçamento.",
                primary("+ Abrir OS", "new-order", this::orderForm)));

        VBox center = searchableTable(list, rows, (o, q) ->
                o.number().toLowerCase().contains(q) ||
                o.customerName().toLowerCase().contains(q) ||
                o.plate().toLowerCase().contains(q) ||
                o.entryDate().toLowerCase().contains(q) ||
                o.responsible().toLowerCase().contains(q) ||
                o.status().toString().toLowerCase().contains(q)
        );
        pagePane.setCenter(center);

        HBox bottom = new HBox(button("Abrir ficha da OS selecionada", "open-order", open));
        bottom.setPadding(new Insets(16, 0, 0, 0));
        pagePane.setBottom(bottom);
    }

    private Stage dialog(String title, FxForm form) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(primaryStage);
        stage.setTitle(title);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");

        Scene scene = new Scene(scroll, 520, 620);
        if (primaryStage.getScene() != null && !primaryStage.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(primaryStage.getScene().getStylesheets());
        }

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.setScene(scene);
        return stage;
    }

    private void showDialog(Stage dialog) {
        dialog.showAndWait();
    }

    private void submit(FxForm form, Runnable action) {
        form.clearErrors();
        try {
            action.run();
        } catch (ValidationException e) {
            form.showError(e);
            if (e.existingVehicleId() != null) {
                vehicleInfo(service.vehicle(e.existingVehicleId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            form.showError("Não foi possível gravar. Verifique o banco local. Nenhuma alteração foi confirmada.");
        }
    }

    private void vehicleInfo(Vehicle v) {
        Customer c = service.customers().stream().filter(x -> x.id() == v.customerId()).findFirst().orElseThrow();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(primaryStage);
        alert.setTitle("Veículo já cadastrado");
        alert.setHeaderText("Veículo já cadastrado na base");
        alert.setContentText("Placa: " + v.plate() + "\nVeículo: " + v.brand() + " " + v.model() +
                "\nAno: " + v.year() + "\nQuilometragem: " + v.mileage() + " km\nCliente: " + c.name());
        if (primaryStage.getScene() != null && !primaryStage.getScene().getStylesheets().isEmpty()) {
            alert.getDialogPane().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
        }
        alert.showAndWait();
    }

    private void customerForm(Consumer<Customer> saved) {
        if (!canOpen("Clientes")) throw new ValidationException("auth", "Seu perfil não pode cadastrar clientes.");
        FxForm f = new FxForm();
        Stage d = dialog("Cadastrar cliente", f);

        TextField name = f.text("name", "Nome *", "");
        TextField phone = f.text("phone", "Telefone *", "");
        TextField email = f.text("email", "E-mail *", "");
        f.finish();

        Button save = primary("Salvar cliente", "save-customer", () -> submit(f, () -> {
            Customer c = service.addCustomer(name.getText(), phone.getText(), email.getText());
            d.close();
            saved.accept(c);
            notice("Cliente cadastrado: " + c.name(), false);
        }));
        f.extra(save);
        showDialog(d);
    }

    private <T> void fill(ComboBox<T> combo, List<T> values, T selected) {
        combo.getItems().setAll(values);
        if (selected != null) combo.setValue(selected);
        else if (!values.isEmpty()) combo.setValue(null);
    }

    private Long customerId(ComboBox<Customer> combo) {
        Customer c = combo.getValue();
        return c == null ? null : c.id();
    }

    private void vehicleForm(Customer selected, Consumer<Vehicle> saved) {
        if (!canOpen("Veículos")) throw new ValidationException("auth", "Seu perfil não pode cadastrar veículos.");
        FxForm f = new FxForm();
        Stage d = dialog("Cadastrar veículo", f);

        TextField plate = f.text("plate", "Placa * (ABC1234 ou ABC1D23)", "");
        TextField brand = f.text("brand", "Marca *", "");
        TextField model = f.text("model", "Modelo *", "");
        TextField km = f.text("mileage", "Quilometragem *", "");
        TextField year = f.text("year", "Ano *", "");

        ComboBox<Customer> customer = f.field("customer", "Cliente *", new ComboBox<>());
        fill(customer, service.customers(), selected);

        f.extra(button("Cliente não localizado? Cadastrar cliente", "missing-customer", () ->
                customerForm(c -> fill(customer, service.customers(), c))
        ));

        f.finish();

        Button save = primary("Salvar veículo", "save-vehicle", () -> submit(f, () -> {
            Vehicle v = service.addVehicle(plate.getText(), brand.getText(), model.getText(), km.getText(), year.getText(), customerId(customer));
            d.close();
            saved.accept(v);
            notice("Veículo " + v.plate() + " cadastrado.", false);
        }));
        f.extra(save);
        showDialog(d);
    }

    private void orderForm() {
        if (!canOpen("Clientes")) throw new ValidationException("auth", "Seu perfil não pode abrir ordens de serviço.");
        FxForm f = new FxForm();
        Stage d = dialog("Abrir ordem de serviço", f);

        ComboBox<Customer> customer = f.field("customer", "Cliente *", new ComboBox<>());
        ComboBox<Vehicle> vehicle = f.field("vehicle", "Veículo * (placa · modelo · km)", new ComboBox<>());
        TextArea complaint = f.area("complaint", "Reclamação do cliente *", "");
        TextField date = f.text("entryDate", "Data de entrada * (dd/mm/aaaa)", service.today().format(DATE));
        TextField km = f.text("mileage", "Quilometragem atual *", "");
        TextField person = f.text("responsible", "Responsável pelo atendimento *", "");

        customer.setOnAction(e -> {
            Long id = customerId(customer);
            fill(vehicle, service.vehicles().stream().filter(v -> id != null && v.customerId() == id).toList(), null);
            km.setText("");
        });

        vehicle.setOnAction(e -> {
            Vehicle v = vehicle.getValue();
            if (v != null) km.setText(String.valueOf(v.mileage()));
        });

        fill(customer, service.customers(), null);

        HBox shortcuts = new HBox(10);
        shortcuts.getChildren().addAll(
                button("Cadastrar cliente", "missing-customer", () -> customerForm(c -> fill(customer, service.customers(), c))),
                button("Cadastrar veículo", "missing-vehicle", () -> vehicleForm(customer.getValue(), v -> {
                    Customer c = service.customers().stream().filter(x -> x.id() == v.customerId()).findFirst().orElseThrow();
                    fill(customer, service.customers(), c);
                    fill(vehicle, service.vehicles().stream().filter(x -> x.customerId() == c.id()).toList(), v);
                }))
        );
        f.extra(shortcuts);

        f.finish();

        Button save = primary("Confirmar abertura", "save-order", () -> submit(f, () -> {
            Vehicle v = vehicle.getValue();
            ServiceOrder order = service.openOrder(customerId(customer), v == null ? null : v.id(), complaint.getText(), date.getText(), km.getText(), person.getText());
            d.close();
            detail(order.id());
            notice(order.number() + " aberta com sucesso.", false);
        }));
        f.extra(save);
        showDialog(d);
    }

    private void detail(long id) {
        ServiceOrder order = service.order(id);
        Vehicle vehicle = service.vehicle(order.vehicleId());

        content.getChildren().clear();
        BorderPane pagePane = new BorderPane();

        pagePane.setTop(header(order.number() + " · " + order.status(),
                vehicle + " | Entrada " + order.entryDate().format(DATE),
                button("Voltar à lista", "back-orders", () -> showPage("Ordens de serviço"))));

        TabPane tabs = new TabPane();
        tabs.setId("order-tabs");

        Tab tabItems = new Tab("Diagnóstico e itens", itemsPanel(order));
        tabItems.setClosable(false);

        Tab tabBudget = new Tab("Orçamento", budgetPanel(order, tabs));
        tabBudget.setClosable(false);

        Tab tabClosing = new Tab("Fechamento", closingPanel(order));
        tabClosing.setClosable(false);

        Tab history=new Tab("Histórico",historyArea(service.orderHistory(id)));history.setClosable(false);
        tabs.getTabs().addAll(tabItems, tabBudget, tabClosing,history);
        if(order.status()==OrderStatus.REJECTED && Session.getUser().role()!=Role.MECANICO){
            HBox resolution=new HBox(12,
                button("Reabrir para revisão","revise-order",()->resolveRejected(id,false)),
                button("Cancelar e devolver peças","cancel-order",()->resolveRejected(id,true)));
            pagePane.setBottom(resolution);
        }
        pagePane.setCenter(tabs);

        content.getChildren().add(pagePane);
    }

    private TableView<ItemRow> itemTable(List<OrderItem> items) {
        TableView<ItemRow> table = new TableView<>();

        TableColumn<ItemRow, String> colKind = new TableColumn<>("Tipo");
        colKind.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().kind()));
        colKind.setPrefWidth(90);

        TableColumn<ItemRow, String> colDesc = new TableColumn<>("Descrição");
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description()));
        colDesc.setPrefWidth(220);

        TableColumn<ItemRow, Integer> colQty = new TableColumn<>("Qtd.");
        colQty.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().quantity()));
        colQty.setPrefWidth(60);

        TableColumn<ItemRow, String> colPrice = new TableColumn<>("Unitário");
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unitPrice()));
        colPrice.setPrefWidth(100);

        TableColumn<ItemRow, String> colSub = new TableColumn<>("Subtotal");
        colSub.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().subtotal()));
        colSub.setPrefWidth(110);

        TableColumn<ItemRow, String> colExec = new TableColumn<>("Execução");
        colExec.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().execution()));
        colExec.setPrefWidth(100);

        table.getColumns().addAll(List.of(colKind, colDesc, colQty, colPrice, colSub, colExec));

        ObservableList<ItemRow> rows = FXCollections.observableArrayList();
        for (OrderItem i : items) {
            rows.add(new ItemRow(
                    i.id(),
                    i.kind().equals("SERVICE") ? "Serviço" : "Peça",
                    i.description(),
                    i.quantity(),
                    money(i.unitPrice()),
                    money(i.subtotal()),
                    i.kind().equals("PART") ? "Vinculada" : (i.completed() ? "Concluído" : "Pendente")
            ));
        }
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private Node itemsPanel(ServiceOrder order) {
        BorderPane panel = new BorderPane();
        panel.setPadding(new Insets(16, 0, 0, 0));

        VBox top = new VBox(8);
        Label complaint = new Label("Relato do cliente: " + order.complaint());
        complaint.getStyleClass().add("field-label");
        complaint.setWrapText(true);

        Label diagLabel = new Label("Diagnóstico técnico:");
        diagLabel.getStyleClass().add("field-label");

        TextArea diagnosis = new TextArea(order.diagnosis());
        diagnosis.setId("diagnosis");
        diagnosis.setPrefRowCount(3);
        diagnosis.setWrapText(true);

        Button saveDiag = button("Salvar diagnóstico", "save-diagnosis", () -> {
            service.saveDiagnosis(order.id(), diagnosis.getText());
            notice("Diagnóstico salvo.", false);
        });
        saveDiag.setDisable(Session.getUser().role() == Role.ATENDENTE || order.status() != OrderStatus.OPEN);
        diagnosis.setEditable(!saveDiag.isDisabled());

        top.getChildren().addAll(complaint, diagLabel, diagnosis, saveDiag);
        panel.setTop(top);

        List<OrderItem> items = service.items(order.id());
        TableView<ItemRow> list = itemTable(items);
        list.setId("items-table");
        VBox.setVgrow(list, Priority.ALWAYS);
        BorderPane.setMargin(list, new Insets(12, 0, 12, 0));
        panel.setCenter(list);

        HBox actions = new HBox(10);
        Button add = primary("+ Serviço", "add-service", () -> serviceForm(order.id()));
        Button part = button("+ Peça", "consume-part", () -> consumeForm(order.id()));
        Button remove = button("Remover item", "remove-item", () -> {
            ItemRow selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) throw new ValidationException("item", "Selecione um item.");
            service.removeItem(order.id(), selected.id());
            detail(order.id());
        });
        Button complete = button("Marcar serviço concluído", "complete-service", () -> {
            ItemRow selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) throw new ValidationException("item", "Selecione um serviço.");
            FxForm form=new FxForm();Stage dialog=dialog("Concluir serviço",form);
            OrderItem item=service.items(order.id()).stream().filter(i->i.id()==selected.id()).findFirst().orElseThrow();
            if(!item.kind().equals("SERVICE"))throw new ValidationException("item","Selecione um serviço.");
            TextArea notes=form.area("observations","Observações da execução (opcional)",item.observations());form.finish();
            form.extra(primary("Concluir e salvar","save-completion",()->submit(form,()->{service.completeService(order.id(),selected.id(),notes.getText());dialog.close();detail(order.id());})));
            showDialog(dialog);
        });

        boolean canExecute = Session.getUser().role() != Role.ATENDENTE;
        add.setDisable(!canExecute || order.status() != OrderStatus.OPEN);
        part.setDisable(!canExecute || order.status() != OrderStatus.OPEN);
        remove.setDisable(!canExecute || order.status() != OrderStatus.OPEN);
        complete.setDisable(!canExecute || order.status() != OrderStatus.APPROVED);

        actions.getChildren().addAll(add, part, remove, complete);
        panel.setBottom(actions);

        return panel;
    }

    private void serviceForm(long id) {
        FxForm f = new FxForm();
        Stage d = dialog("Adicionar serviço", f);

        TextField desc = f.text("description", "Serviço *", "");
        TextField q = f.text("quantity", "Quantidade *", "1");
        TextField p = f.text("price", "Valor unitário (R$) *", "");
        f.finish();

        f.extra(primary("Vincular serviço à OS", "save-service", () -> submit(f, () -> {
            service.addService(id, desc.getText(), q.getText(), p.getText());
            d.close();
            detail(id);
        })));
        showDialog(d);
    }

    private void consumeForm(long id) {
        FxForm f = new FxForm();
        Stage d = dialog("Vincular peça à OS", f);

        ComboBox<Part> part = f.field("part", "Peça em estoque *", new ComboBox<>());
        fill(part, service.parts(), null);

        TextField q = f.text("quantity", "Quantidade utilizada *", "1");
        Label note = new Label("O estoque será reduzido ao confirmar o vínculo.");
        note.getStyleClass().add("brand-subtitle");
        f.extra(note);
        f.finish();

        f.extra(primary("Vincular peça", "save-consumption", () -> submit(f, () -> {
            Part p = part.getValue();
            service.consumePart(id, p == null ? null : p.id(), q.getText());
            d.close();
            detail(id);
        })));
        showDialog(d);
    }

    private Node budgetPanel(ServiceOrder order, TabPane parentTabs) {
        BorderPane panel = new BorderPane();
        panel.setPadding(new Insets(20, 10, 10, 10));

        try {
            Budget budget = service.budget(order.id());
            TableView<ItemRow> budgetItems = itemTable(budget.items());
            budgetItems.setId("budget-items");
            panel.setCenter(budgetItems);

            VBox totals = new VBox(10);
            totals.setPadding(new Insets(16, 0, 0, 0));

            Label subTotals = new Label("Serviços: " + money(budget.services()) + "    |    Peças: " + money(budget.parts()));
            subTotals.getStyleClass().add("field-label");

            Label total = new Label("TOTAL  " + money(budget.total()));
            total.setId("budget-total");
            total.getStyleClass().add("metric-number");

            totals.getChildren().addAll(subTotals, total);

            if (order.decisionAt() != null) {
                Label decisionInfo = new Label("Decisão: " + order.status() + " · " +
                        order.decisionAt().format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")) +
                        " · Responsável: " + order.decisionBy());
                decisionInfo.getStyleClass().add("brand-subtitle");
                totals.getChildren().add(decisionInfo);
            }

            FxForm form = new FxForm();
            TextField responsible = form.text("decisionBy", "Responsável pelo registro da decisão *", "");
            form.finish();

            HBox buttons = new HBox(10);
            for (boolean approved : new boolean[]{true, false}) {
                Button b = button(approved ? "Aprovado" : "Rejeitado", approved ? "approve-budget" : "reject-budget", () -> submit(form, () -> {
                    service.decideBudget(order.id(), approved, responsible.getText());
                    detail(order.id());
                    notice("Decisão registrada com data, responsável e valor total.", false);
                }));
                b.setDisable(order.status() != OrderStatus.OPEN);
                if (approved) b.getStyleClass().add("primary-button");
                buttons.getChildren().add(b);
            }
            form.extra(buttons);
            totals.getChildren().add(form);
            panel.setBottom(totals);

        } catch (ValidationException e) {
            VBox emptyBox = new VBox(16);
            emptyBox.setAlignment(Pos.CENTER);
            Label empty = new Label(e.getMessage());
            empty.getStyleClass().add("page-subtitle");
            empty.setWrapText(true);

            Button goItems = button("Registrar serviços e peças", "go-items", () -> parentTabs.getSelectionModel().select(0));
            goItems.getStyleClass().add("primary-button");

            emptyBox.getChildren().addAll(empty, goItems);
            panel.setCenter(emptyBox);
        }

        return panel;
    }

    private Node closingPanel(ServiceOrder order) {
        FxForm f = new FxForm();
        Label note = new Label("Feche apenas após aprovação, execução dos serviços e recebimento.");
        note.getStyleClass().add("brand-subtitle");
        f.extra(note);

        if (order.status() == OrderStatus.CLOSED) {
            Label closedInfo = new Label("Pagamento: " + order.paymentMethod() + " · Retirada: " + (order.pickupDate() != null ? order.pickupDate().format(DATE) : ""));
            closedInfo.getStyleClass().add("field-label");
            f.extra(closedInfo);
            return f;
        }

        ComboBox<String> payment = f.field("payment", "Forma de pagamento *", new ComboBox<>());
        payment.getItems().addAll("", "Dinheiro", "Cartão", "PIX", "Transferência");
        payment.setValue("");

        TextField pickup = f.text("pickup", "Data de retirada * (dd/mm/aaaa)", service.today().format(DATE));
        CheckBox paid = new CheckBox("Confirmo que o pagamento foi recebido presencialmente");
        f.extra(paid);
        f.finish();

        Button close = primary("Fechar ordem de serviço", "close-order", () -> submit(f, () -> {
            service.closeOrder(order.id(), payment.getValue(), pickup.getText(), paid.isSelected());
            detail(order.id());
            notice("OS fechada. Veículo liberado para novo atendimento.", false);
        }));
        close.setDisable(Session.getUser().role() == Role.MECANICO || order.status() != OrderStatus.APPROVED);
        f.extra(close);
        return f;
    }

    private void stockPage(BorderPane pagePane) {
        pagePane.setTop(header("Estoque de peças", "Saldo local. O consumo é registrado na ficha da OS.",
                primary("+ Cadastrar peça", "new-part", this::partForm)));

        List<Part> parts = service.parts();
        TableView<Part> list = new TableView<>();

        TableColumn<Part, Long> colId = new TableColumn<>("Código");
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colId.setPrefWidth(80);

        TableColumn<Part, String> colName = new TableColumn<>("Peça");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colName.setPrefWidth(220);

        TableColumn<Part, String> colPrice = new TableColumn<>("Preço unitário");
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().unitPrice())));
        colPrice.setPrefWidth(120);

        TableColumn<Part, Integer> colStock = new TableColumn<>("Saldo");
        colStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().stock()));
        colStock.setPrefWidth(90);

        list.getColumns().addAll(List.of(colId, colName, colPrice, colStock));

        ObservableList<Part> rows = FXCollections.observableArrayList(parts);
        pagePane.setCenter(searchableTable(list, rows, (p, q) ->
                String.valueOf(p.id()).contains(q) ||
                p.name().toLowerCase().contains(q) ||
                p.unitPrice().toString().contains(q) ||
                String.valueOf(p.stock()).contains(q)
        ));

        HBox bottom = new HBox(button("Repor selecionada", "replenish", () -> {
            Part selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) throw new ValidationException("part", "Selecione uma peça.");
            FxForm f = new FxForm();
            Stage d = dialog("Repor " + selected.name(), f);
            TextField q = f.text("quantity", "Quantidade de entrada *", "");
            f.finish();
            f.extra(primary("Confirmar entrada", "save-replenish", () -> submit(f, () -> {
                service.replenish(selected.id(), q.getText());
                d.close();
                showPage(currentPage);
            })));
            showDialog(d);
        }));
        bottom.setPadding(new Insets(16, 0, 0, 0));
        bottom.getChildren().add(button("Histórico da peça","stock-history",()->{
            Part selected=list.getSelectionModel().getSelectedItem();if(selected==null)throw new ValidationException("part","Selecione uma peça.");
            Alert history=new Alert(Alert.AlertType.INFORMATION);history.initOwner(primaryStage);history.setTitle("Movimentações - "+selected.name());history.getDialogPane().setContent(historyArea(service.stockHistory(selected.id())));history.showAndWait();
        }));
        pagePane.setBottom(bottom);
    }

    private void partForm() {
        FxForm f = new FxForm();
        Stage d = dialog("Entrada de peças", f);
        f.extra(new Label("Nome já cadastrado: soma a quantidade e atualiza o preço. Itens anteriores de OS mantêm o valor original."));
        TextField name = f.text("name", "Nome *", "");
        TextField price = f.text("price", "Preço unitário (R$) *", "");
        TextField q = f.text("quantity", "Quantidade de entrada *", "");
        f.finish();
        f.extra(primary("Salvar peça", "save-part", () -> submit(f, () -> {
            service.addPart(name.getText(), price.getText(), q.getText());
            d.close();
            showPage(currentPage);
        })));
        showDialog(d);
    }

    private void reportsPage(BorderPane pagePane) {
        pagePane.setCenter(new ReportsPane(primaryStage, service));
    }
    private TextArea historyArea(List<HistoryEntry> entries){
        StringBuilder text=new StringBuilder();
        for(HistoryEntry e:entries)text.append(e.time().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append(" | ").append(e.actor()).append(" | ").append(e.action()).append("\n").append(e.details()).append("\n\n");
        TextArea area=new TextArea(text.isEmpty()?"Nenhum evento registrado. Eventos anteriores à atualização não são reconstruídos.":text.toString());area.setEditable(false);area.setWrapText(true);area.setPrefSize(720,400);return area;
    }
    private void resolveRejected(long id,boolean cancel){
        FxForm f=new FxForm();Stage d=dialog(cancel?"Cancelar OS rejeitada":"Revisar orçamento rejeitado",f);
        f.extra(new Label(cancel?"Devolve as peças ao estoque e libera o veículo. Mantém a OS e o histórico.":"Volta para Aberta e permite editar itens. Mantém o veículo e as peças reservados até nova decisão."));
        TextArea reason=f.area("reason","Motivo obrigatório","");f.finish();
        f.extra(primary(cancel?"Confirmar cancelamento":"Confirmar revisão","confirm-resolution",()->submit(f,()->{
            if(cancel)service.cancelRejectedOrder(id,reason.getText());else service.reviseRejectedOrder(id,reason.getText());d.close();detail(id);
        })));showDialog(d);
    }
}
