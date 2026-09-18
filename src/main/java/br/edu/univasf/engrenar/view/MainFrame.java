package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public final class MainFrame extends JFrame {
    private static final Color ORANGE=new Color(244,139,50), MUTED=new Color(166,174,188);
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final WorkshopService service;
    private final JPanel content=new JPanel(new BorderLayout(0,16));
    private final JLabel message=new JLabel("Pronto para atender. Os dados são salvos automaticamente neste computador.");
    private String page="Visão geral";
    public MainFrame(WorkshopService service,String dataPath) {
        super("Engrenar | Gestão de Oficina"); this.service=service;
        setName("engrenar"); setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); setSize(1200,800); setMinimumSize(new Dimension(1000,700)); setLocationRelativeTo(null);
        JPanel sidebar=new JPanel(); sidebar.setLayout(new BoxLayout(sidebar,BoxLayout.Y_AXIS)); sidebar.setPreferredSize(new Dimension(220,0)); sidebar.setBackground(new Color(24,27,33)); sidebar.setBorder(BorderFactory.createEmptyBorder(28,18,22,18));
        JLabel brand=new JLabel("ENGRENAR"); brand.setFont(brand.getFont().deriveFont(Font.BOLD,25)); brand.setForeground(ORANGE); sidebar.add(brand);
        JLabel caption=new JLabel("Gestão de oficina"); caption.setForeground(MUTED); sidebar.add(caption); sidebar.add(Box.createVerticalStrut(35));
        for(String name:List.of("Visão geral","Clientes","Veículos","Ordens de serviço","Estoque","Relatórios")) {
            JButton b=button(name,"nav-"+name,()->showPage(name)); b.setMaximumSize(new Dimension(200,43)); b.setAlignmentX(LEFT_ALIGNMENT); sidebar.add(b); sidebar.add(Box.createVerticalStrut(10));
        }
        sidebar.add(Box.createVerticalGlue()); JLabel local=new JLabel("●  Banco local · PostgreSQL"); local.setForeground(new Color(125,206,166)); sidebar.add(local);
        content.setBorder(BorderFactory.createEmptyBorder(28,30,20,30));
        JPanel main=new JPanel(new BorderLayout()); main.add(content); message.setBorder(BorderFactory.createEmptyBorder(10,30,14,15)); message.setToolTipText("Dados: "+dataPath); main.add(message,BorderLayout.SOUTH);
        add(sidebar,BorderLayout.WEST); add(main); showPage(page);
        getRootPane().registerKeyboardAction(e->showPage("Ordens de serviço"),KeyStroke.getKeyStroke(KeyEvent.VK_O,InputEvent.CTRL_DOWN_MASK),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->showPage("Veículos"),KeyStroke.getKeyStroke(KeyEvent.VK_V,InputEvent.CTRL_DOWN_MASK),JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    private JButton button(String text,String name,Runnable action) {
        JButton b=new JButton(text); b.setName(name); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.addActionListener(e->run(action)); return b;
    }
    private JButton primary(String text,String name,Runnable action) { JButton b=button(text,name,action); b.setBackground(ORANGE); b.setForeground(new Color(25,25,25)); b.setFont(b.getFont().deriveFont(Font.BOLD)); return b; }
    private void run(Runnable action) { try { action.run(); } catch(ValidationException e) { notice(e.getMessage(),true); } catch(Exception e) { e.printStackTrace(); notice("Não foi possível concluir. Os dados não foram alterados. Verifique o banco local.",true); } }
    private void notice(String text,boolean error) { message.setText(text.replace('\n',' ')); message.setForeground(error?new Color(255,146,146):new Color(125,206,166)); }
    private String money(BigDecimal amount) { return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(amount); }
    private JPanel header(String title,String subtitle,JComponent... actions) {
        JPanel p=new JPanel(new BorderLayout(16,10)), titles=new JPanel(new GridLayout(0,1,0,8));
        JLabel h=new JLabel(title); h.setFont(h.getFont().deriveFont(Font.BOLD,28)); titles.add(h); JLabel sub=new JLabel(subtitle); sub.setForeground(MUTED); titles.add(sub); p.add(titles);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT)); for(JComponent a:actions)buttons.add(a); p.add(buttons,BorderLayout.EAST); return p;
    }
    private JTable table(String[] columns,Object[][] rows) {
        JTable table=new JTable(new DefaultTableModel(rows,columns){ @Override public boolean isCellEditable(int r,int c){return false;} });
        table.setRowHeight(38); table.getTableHeader().setPreferredSize(new Dimension(0,40)); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true); return table;
    }
    private JPanel searchable(JTable table) {
        JPanel p=new JPanel(new BorderLayout(0,12)); JTextField search=new JTextField(); search.putClientProperty("JTextField.placeholderText","Pesquisar nesta lista…"); search.setPreferredSize(new Dimension(200,36));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){ public void insertUpdate(javax.swing.event.DocumentEvent e){filter();} public void removeUpdate(javax.swing.event.DocumentEvent e){filter();} public void changedUpdate(javax.swing.event.DocumentEvent e){filter();} private void filter(){ ((TableRowSorter<?>)table.getRowSorter()).setRowFilter(RowFilter.regexFilter("(?iu)"+java.util.regex.Pattern.quote(search.getText()))); }});
        p.add(search,BorderLayout.NORTH);p.add(new JScrollPane(table));return p;
    }
    private void showPage(String name) {
        page=name;content.removeAll();
        switch(name){ case "Clientes"->customersPage();case "Veículos"->vehiclesPage();case "Ordens de serviço"->ordersPage();case "Estoque"->stockPage();case "Relatórios"->reportsPage();default->dashboard(); }
        content.revalidate(); content.repaint();
    }
    private Object[][] orderRows(List<ServiceOrder> orders) {
        Map<Long,Vehicle> vehicles=new HashMap<>(); service.vehicles().forEach(v->vehicles.put(v.id(),v));
        Map<Long,Customer> customers=new HashMap<>(); service.customers().forEach(c->customers.put(c.id(),c));
        return orders.stream().map(o->new Object[]{o.number(),customers.get(o.customerId()).name(),vehicles.get(o.vehicleId()).plate(),o.entryDate().format(DATE),o.responsible(),o.status()}).toArray(Object[][]::new);
    }
    private void dashboard() {
        List<ServiceOrder> orders=service.orders();
        content.add(header("Sua oficina, em dia", "Atendimentos, veículos e orçamento em um só lugar."),BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(0,24)), cards=new JPanel(new GridLayout(1,3,16,0));
        for(OrderStatus status:List.of(OrderStatus.OPEN,OrderStatus.APPROVED,OrderStatus.CLOSED)) {
            JPanel card=new JPanel(new BorderLayout(0,10)); card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(66,72,84)),BorderFactory.createEmptyBorder(20,20,20,20)));
            JLabel count=new JLabel(""+orders.stream().filter(o->o.status()==status).count());count.setFont(count.getFont().deriveFont(Font.BOLD,34));count.setForeground(ORANGE);card.add(count);card.add(new JLabel(status.toString()),BorderLayout.SOUTH);cards.add(card);
        }
        body.add(cards,BorderLayout.NORTH);JPanel latest=new JPanel(new BorderLayout(0,12));latest.add(new JLabel("ATENDIMENTOS RECENTES"),BorderLayout.NORTH);latest.add(new JScrollPane(table(new String[]{"OS","Cliente","Placa","Entrada","Responsável","Status"},orderRows(orders))));body.add(latest);content.add(body);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.add(primary("Abrir ordem de serviço","new-order",()->orderForm()));actions.add(button("Cadastrar veículo","new-vehicle",()->vehicleForm(null,v->showPage("Veículos"))));content.add(actions,BorderLayout.SOUTH);
    }
    private void customersPage() {
        content.add(header("Clientes","Contatos disponíveis para vincular aos veículos.",primary("+ Cadastrar cliente","new-customer",()->customerForm(this,c->showPage(page)))),BorderLayout.NORTH);
        content.add(searchable(table(new String[]{"Código","Nome","Telefone","E-mail"},service.customers().stream().map(c->new Object[]{c.id(),c.name(),c.phone(),c.email()}).toArray(Object[][]::new))));
    }
    private void vehiclesPage() {
        content.add(header("Veículos","Cadastro por placa, sempre vinculado a um cliente.",primary("+ Cadastrar veículo","new-vehicle",()->vehicleForm(null,v->showPage(page)))),BorderLayout.NORTH);
        Map<Long,String> names=new HashMap<>();service.customers().forEach(c->names.put(c.id(),c.name()));
        content.add(searchable(table(new String[]{"Placa","Marca","Modelo","Ano","Km","Cliente"},service.vehicles().stream().map(v->new Object[]{v.plate(),v.brand(),v.model(),v.year(),v.mileage(),names.get(v.customerId())}).toArray(Object[][]::new))));
    }
    private void ordersPage() {
        List<ServiceOrder> orders=service.orders(); JTable list=table(new String[]{"OS","Cliente","Placa","Entrada","Responsável","Status"},orderRows(orders)); list.setName("orders-table");
        Runnable open=()->{ if(list.getSelectedRow()<0)throw new ValidationException("order","Selecione uma OS na lista."); detail(orders.get(list.convertRowIndexToModel(list.getSelectedRow())).id()); };
        content.add(header("Ordens de serviço","Selecione um atendimento para registrar itens e orçamento.",primary("+ Abrir OS","new-order",this::orderForm)),BorderLayout.NORTH);content.add(searchable(list));
        content.add(button("Abrir ficha da OS selecionada","open-order",open),BorderLayout.SOUTH);
        list.addMouseListener(new MouseAdapter(){ @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)run(open);} });
    }
    private JDialog dialog(Window owner,String title,Form form) {
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL); dialog.setName(title); dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); dialog.setContentPane(form);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);return dialog;
    }
    private void showDialog(JDialog dialog) { dialog.pack(); dialog.setSize(Math.max(500,dialog.getWidth()),Math.min(760,dialog.getHeight()));dialog.setLocationRelativeTo(dialog.getOwner());dialog.setVisible(true); }
    private void submit(Form form,Runnable action) {
        form.clearErrors();try{action.run();}catch(ValidationException e){form.showError(e);if(e.existingVehicleId()!=null)vehicleInfo(service.vehicle(e.existingVehicleId()));}catch(Exception e){e.printStackTrace();form.showError("Não foi possível gravar. Verifique o banco local. Nenhuma alteração foi confirmada.");}
    }
    private void vehicleInfo(Vehicle v) {
        Customer c=service.customers().stream().filter(x->x.id()==v.customerId()).findFirst().orElseThrow();
        JOptionPane.showMessageDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(),"Placa: "+v.plate()+"\nVeículo: "+v.brand()+" "+v.model()+"\nAno: "+v.year()+"\nQuilometragem: "+v.mileage()+" km\nCliente: "+c.name(),"Veículo já cadastrado",JOptionPane.INFORMATION_MESSAGE);
    }
    private void customerForm(Window owner,Consumer<Customer> saved) {
        Form f=new Form();JDialog d=dialog(owner,"Cadastrar cliente",f);JTextField name=f.text("name","Nome *",""),phone=f.text("phone","Telefone *",""),email=f.text("email","E-mail *","");
        f.finish();JButton save=primary("Salvar cliente","save-customer",()->submit(f,()->{Customer c=service.addCustomer(name.getText(),phone.getText(),email.getText());d.dispose();saved.accept(c);notice("Cliente cadastrado: "+c.name(),false);}));f.extra(save);d.getRootPane().setDefaultButton(save);showDialog(d);
    }
    private <T> void fill(JComboBox<T> combo,List<T> values,T selected) { combo.removeAllItems();values.forEach(combo::addItem);combo.setSelectedItem(selected); }
    private Long customerId(JComboBox<Customer> combo) { Customer c=(Customer)combo.getSelectedItem();return c==null?null:c.id(); }
    private void vehicleForm(Customer selected,Consumer<Vehicle> saved) { vehicleForm(this,selected,saved); }
    private void vehicleForm(Window owner,Customer selected,Consumer<Vehicle> saved) {
        Form f=new Form();JDialog d=dialog(owner,"Cadastrar veículo",f);
        JTextField plate=f.text("plate","Placa * (ABC1234 ou ABC1D23)",""),brand=f.text("brand","Marca *",""),model=f.text("model","Modelo *",""),km=f.text("mileage","Quilometragem *",""),year=f.text("year","Ano *","");
        JComboBox<Customer> customer=f.field("customer","Cliente *",new JComboBox<>());fill(customer,service.customers(),selected);
        f.extra(button("Cliente não localizado? Cadastrar cliente","missing-customer",()->customerForm(d,c->fill(customer,service.customers(),c))));
        f.finish();JButton save=primary("Salvar veículo","save-vehicle",()->submit(f,()->{Vehicle v=service.addVehicle(plate.getText(),brand.getText(),model.getText(),km.getText(),year.getText(),customerId(customer));d.dispose();saved.accept(v);notice("Veículo "+v.plate()+" cadastrado.",false);}));f.extra(save);d.getRootPane().setDefaultButton(save);showDialog(d);
    }
    private void orderForm() {
        Form f=new Form();JDialog d=dialog(this,"Abrir ordem de serviço",f);
        JComboBox<Customer> customer=f.field("customer","Cliente *",new JComboBox<>());JComboBox<Vehicle> vehicle=f.field("vehicle","Veículo * (placa · modelo · km)",new JComboBox<>());
        JTextArea complaint=f.area("complaint","Reclamação do cliente *","");JTextField date=f.text("entryDate","Data de entrada * (dd/mm/aaaa)",service.today().format(DATE)),km=f.text("mileage","Quilometragem atual *",""),person=f.text("responsible","Responsável pelo atendimento *","");
        customer.addActionListener(e->{Long id=customerId(customer);fill(vehicle,service.vehicles().stream().filter(v->id!=null&&v.customerId()==id).toList(),null);km.setText("");});
        vehicle.addActionListener(e->{Vehicle v=(Vehicle)vehicle.getSelectedItem();if(v!=null)km.setText(""+v.mileage());});fill(customer,service.customers(),null);
        JPanel shortcuts=new JPanel(new FlowLayout(FlowLayout.LEFT));shortcuts.add(button("Cadastrar cliente","missing-customer",()->customerForm(d,c->fill(customer,service.customers(),c))));
        shortcuts.add(button("Cadastrar veículo","missing-vehicle",()->vehicleForm(d,(Customer)customer.getSelectedItem(),v->{Customer c=service.customers().stream().filter(x->x.id()==v.customerId()).findFirst().orElseThrow();fill(customer,service.customers(),c);fill(vehicle,service.vehicles().stream().filter(x->x.customerId()==c.id()).toList(),v);})));f.extra(shortcuts);
        f.finish();JButton save=primary("Confirmar abertura","save-order",()->submit(f,()->{Vehicle v=(Vehicle)vehicle.getSelectedItem();ServiceOrder order=service.openOrder(customerId(customer),v==null?null:v.id(),complaint.getText(),date.getText(),km.getText(),person.getText());d.dispose();detail(order.id());notice(order.number()+" aberta com sucesso.",false);}));f.extra(save);d.getRootPane().setDefaultButton(save);showDialog(d);
    }
    private void detail(long id) {
        ServiceOrder order=service.order(id);Vehicle vehicle=service.vehicle(order.vehicleId());content.removeAll();
        content.add(header(order.number()+" · "+order.status(),vehicle+" | Entrada "+order.entryDate().format(DATE),button("Voltar à lista","back-orders",()->showPage("Ordens de serviço"))),BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.setName("order-tabs");tabs.addTab("Diagnóstico e itens",itemsPanel(order));tabs.addTab("Orçamento",budgetPanel(order));tabs.addTab("Fechamento",closingPanel(order));content.add(tabs);content.revalidate();content.repaint();
    }
    private JPanel itemsPanel(ServiceOrder order) {
        JPanel panel=new JPanel(new BorderLayout(0,12));panel.setBorder(BorderFactory.createEmptyBorder(16,0,0,0));
        JPanel top=new JPanel(new BorderLayout(0,8));JTextArea complaint=new JTextArea("Relato do cliente: "+order.complaint(),2,40);complaint.setLineWrap(true);complaint.setWrapStyleWord(true);complaint.setEditable(false);top.add(complaint,BorderLayout.NORTH);
        JTextArea diagnosis=new JTextArea(order.diagnosis(),3,40);diagnosis.setName("diagnosis");diagnosis.setLineWrap(true);diagnosis.setWrapStyleWord(true);diagnosis.setBorder(BorderFactory.createTitledBorder("Diagnóstico técnico"));top.add(new JScrollPane(diagnosis));top.add(button("Salvar diagnóstico","save-diagnosis",()->{service.saveDiagnosis(order.id(),diagnosis.getText());notice("Diagnóstico salvo.",false);}),BorderLayout.SOUTH);panel.add(top,BorderLayout.NORTH);
        List<OrderItem> items=service.items(order.id());JTable list=itemTable(items);list.setName("items-table");panel.add(new JScrollPane(list));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=primary("+ Serviço","add-service",()->serviceForm(order.id())),part=button("+ Peça","consume-part",()->consumeForm(order.id())),remove=button("Remover item","remove-item",()->{if(list.getSelectedRow()<0)throw new ValidationException("item","Selecione um item.");service.removeItem(order.id(),items.get(list.convertRowIndexToModel(list.getSelectedRow())).id());detail(order.id());}),complete=button("Marcar serviço concluído","complete-service",()->{if(list.getSelectedRow()<0)throw new ValidationException("item","Selecione um serviço.");service.completeService(order.id(),items.get(list.convertRowIndexToModel(list.getSelectedRow())).id());detail(order.id());});
        add.setEnabled(order.status()==OrderStatus.OPEN);part.setEnabled(order.status()==OrderStatus.OPEN);remove.setEnabled(order.status()==OrderStatus.OPEN);complete.setEnabled(order.status()==OrderStatus.APPROVED);
        actions.add(add);actions.add(part);actions.add(remove);actions.add(complete);panel.add(actions,BorderLayout.SOUTH);return panel;
    }
    private JTable itemTable(List<OrderItem> items) { return table(new String[]{"Tipo","Descrição","Qtd.","Unitário","Subtotal","Execução"},items.stream().map(i->new Object[]{i.kind().equals("SERVICE")?"Serviço":"Peça",i.description(),i.quantity(),money(i.unitPrice()),money(i.subtotal()),i.kind().equals("PART")?"Vinculada":i.completed()?"Concluído":"Pendente"}).toArray(Object[][]::new)); }
    private void serviceForm(long id) {
        Form f=new Form();JDialog d=dialog(this,"Adicionar serviço",f);JTextField desc=f.text("description","Serviço *",""),q=f.text("quantity","Quantidade *","1"),p=f.text("price","Valor unitário (R$) *","");f.finish();f.extra(primary("Vincular serviço à OS","save-service",()->submit(f,()->{service.addService(id,desc.getText(),q.getText(),p.getText());d.dispose();detail(id);})));showDialog(d);
    }
    private void consumeForm(long id) {
        Form f=new Form();JDialog d=dialog(this,"Vincular peça à OS",f);JComboBox<Part> part=f.field("part","Peça em estoque *",new JComboBox<>());fill(part,service.parts(),null);JTextField q=f.text("quantity","Quantidade utilizada *","1");f.extra(new JLabel("O estoque será reduzido ao confirmar o vínculo."));f.finish();f.extra(primary("Vincular peça","save-consumption",()->submit(f,()->{Part p=(Part)part.getSelectedItem();service.consumePart(id,p==null?null:p.id(),q.getText());d.dispose();detail(id);})));showDialog(d);
    }
    private JPanel budgetPanel(ServiceOrder order) {
        JPanel panel=new JPanel(new BorderLayout(0,16));panel.setBorder(BorderFactory.createEmptyBorder(20,10,10,10));
        try {
            Budget budget=service.budget(order.id());JTable budgetItems=itemTable(budget.items());budgetItems.setName("budget-items");panel.add(new JScrollPane(budgetItems));
            JPanel totals=new JPanel();totals.setLayout(new BoxLayout(totals,BoxLayout.Y_AXIS));totals.add(new JLabel("Serviços: "+money(budget.services())+"    |    Peças: "+money(budget.parts())));totals.add(Box.createVerticalStrut(10));JLabel total=new JLabel("TOTAL  "+money(budget.total()));total.setName("budget-total");total.setFont(total.getFont().deriveFont(Font.BOLD,26));total.setForeground(ORANGE);totals.add(total);
            if(order.decisionAt()!=null)totals.add(new JLabel("Decisão: "+order.status()+" · "+order.decisionAt().format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))+" · Responsável: "+order.decisionBy()));
            Form form=new Form();form.setAlignmentX(LEFT_ALIGNMENT);JTextField responsible=form.text("decisionBy","Responsável pelo registro da decisão *","");form.finish();
            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));for(boolean approved:new boolean[]{true,false}){JButton b=button(approved?"Aprovado":"Rejeitado",approved?"approve-budget":"reject-budget",()->submit(form,()->{service.decideBudget(order.id(),approved,responsible.getText());detail(order.id());((JTabbedPane)content.getComponent(1)).setSelectedIndex(1);notice("Decisão registrada com data, responsável e valor total.",false);}));b.setEnabled(order.status()==OrderStatus.OPEN);buttons.add(b);}form.extra(buttons);totals.add(form);panel.add(totals,BorderLayout.SOUTH);
        }catch(ValidationException e){JTextArea empty=new JTextArea(e.getMessage());empty.setEditable(false);empty.setLineWrap(true);empty.setWrapStyleWord(true);empty.setFont(empty.getFont().deriveFont(18f));panel.add(empty);panel.add(button("Registrar serviços e peças","go-items",()->((JTabbedPane)panel.getParent()).setSelectedIndex(0)),BorderLayout.SOUTH);}
        return panel;
    }
    private JPanel closingPanel(ServiceOrder order) {
        Form f=new Form();f.extra(new JLabel("Feche apenas após aprovação, execução dos serviços e recebimento."));
        if(order.status()==OrderStatus.CLOSED) {f.extra(new JLabel("Pagamento: "+order.paymentMethod()+" · Retirada: "+order.pickupDate().format(DATE)));return f;}
        JComboBox<String> payment=f.field("payment","Forma de pagamento *",new JComboBox<>(new String[]{"","Dinheiro","Cartão","PIX","Transferência"}));JTextField pickup=f.text("pickup","Data de retirada * (dd/mm/aaaa)",service.today().format(DATE));JCheckBox paid=new JCheckBox("Confirmo que o pagamento foi recebido presencialmente");f.extra(paid);f.finish();
        JButton close=primary("Fechar ordem de serviço","close-order",()->submit(f,()->{service.closeOrder(order.id(),(String)payment.getSelectedItem(),pickup.getText(),paid.isSelected());detail(order.id());notice("OS fechada. Veículo liberado para novo atendimento.",false);}));close.setEnabled(order.status()==OrderStatus.APPROVED);f.extra(close);return f;
    }
    private void stockPage() {
        content.add(header("Estoque de peças","Saldo local. O consumo é registrado na ficha da OS.",primary("+ Cadastrar peça","new-part",this::partForm)),BorderLayout.NORTH);
        List<Part> parts=service.parts();JTable list=table(new String[]{"Código","Peça","Preço unitário","Saldo"},parts.stream().map(p->new Object[]{p.id(),p.name(),money(p.unitPrice()),p.stock()}).toArray(Object[][]::new));content.add(searchable(list));
        content.add(button("Repor selecionada","replenish",()->{if(list.getSelectedRow()<0)throw new ValidationException("part","Selecione uma peça.");Part part=parts.get(list.convertRowIndexToModel(list.getSelectedRow()));Form f=new Form();JDialog d=dialog(this,"Repor "+part.name(),f);JTextField q=f.text("quantity","Quantidade de entrada *","");f.finish();f.extra(primary("Confirmar entrada","save-replenish",()->submit(f,()->{service.replenish(part.id(),q.getText());d.dispose();showPage(page);})));showDialog(d);}),BorderLayout.SOUTH);
    }
    private void partForm() {
        Form f=new Form();JDialog d=dialog(this,"Cadastrar peça",f);JTextField name=f.text("name","Nome *",""),price=f.text("price","Preço unitário (R$) *",""),q=f.text("quantity","Quantidade de entrada *","");f.finish();f.extra(primary("Salvar peça","save-part",()->submit(f,()->{service.addPart(name.getText(),price.getText(),q.getText());d.dispose();showPage(page);})));showDialog(d);
    }
    private void reportsPage() {
        content.add(header("Relatório local","Resumo da base atual; filtros e exportação ficam para a próxima entrega."),BorderLayout.NORTH);
        List<ServiceOrder> orders=service.orders();content.add(new JScrollPane(table(new String[]{"OS","Cliente","Placa","Entrada","Responsável","Status"},orderRows(orders))));
        content.add(new JLabel("Recebido em ordens fechadas: "+money(service.receivedTotal())+"    |    Ordens registradas: "+orders.size()),BorderLayout.SOUTH);
    }
}
