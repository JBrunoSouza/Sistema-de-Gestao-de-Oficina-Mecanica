package br.edu.univasf.engrenar.view;

import br.edu.univasf.engrenar.model.*;
import br.edu.univasf.engrenar.service.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.*;
import java.util.Locale;
import java.util.function.Function;

/** Filters are applied together; export always uses the displayed immutable snapshot. */
public final class ReportsPane extends BorderPane {
    private final WorkshopService service;
    private final Window owner;
    private final TextField from=new TextField(),to=new TextField(),search=new TextField();
    private final ComboBox<String> status=new ComboBox<>();
    private final TableView<OrderReport.Row> table=new TableView<>();
    private final Label summary=new Label(),message=new Label();
    private final Button apply=new Button("Aplicar filtros"),clear=new Button("Limpar"),export=new Button("Exportar PDF");
    private final FlowPane filters=new FlowPane(10,10);
    private OrderReport displayed;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    public ReportsPane(Window owner,WorkshopService service) {
        this.owner=owner;this.service=service;
        Label title=new Label("Relatórios de ordens de serviço");title.getStyleClass().add("page-title");
        Label note=new Label("Período pela data de entrada, incluindo as duas datas. Em branco: sem limite.");note.setWrapText(true);
        from.setId("report-from");to.setId("report-to");search.setId("report-search");status.setId("report-status");
        from.setPromptText("dd/mm/aaaa");to.setPromptText("dd/mm/aaaa");search.setPromptText("Nome ou placa");
        from.setPrefWidth(125);to.setPrefWidth(125);search.setPrefWidth(210);
        status.getItems().add("Todos");for(OrderStatus s:OrderStatus.values())status.getItems().add(s.toString());status.setValue("Todos");
        filters.getChildren().addAll(field("Entrada de",from),field("Até",to),field("Status",status),field("Cliente / placa",search));
        apply.setId("report-apply");clear.setId("report-clear");export.setId("report-export");export.setDisable(true);
        apply.getStyleClass().add("primary-button");export.getStyleClass().add("primary-button");
        HBox actions=new HBox(10,apply,clear,export);
        VBox top=new VBox(12,title,note,filters,actions,message);top.setPadding(new Insets(0,0,16,0));setTop(top);
        column("OS",r->r.number(),100);column("Cliente",r->r.customer(),240);column("Placa",r->r.plate(),100);
        column("Entrada",r->r.entryDate().format(DATE),110);column("Status",r->r.status().toString(),120);
        column("Valor decidido",r->r.agreedTotal()==null?"Não decidido":money.format(r.agreedTotal()),140);
        table.setId("report-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Nenhuma ordem encontrada para estes filtros."));setCenter(table);
        summary.setId("report-summary");summary.setWrapText(true);summary.setPadding(new Insets(14,0,0,0));setBottom(summary);
        from.textProperty().addListener((o,a,b)->dirty());to.textProperty().addListener((o,a,b)->dirty());
        search.textProperty().addListener((o,a,b)->dirty());status.valueProperty().addListener((o,a,b)->dirty());
        apply.setOnAction(e->load());clear.setOnAction(e->{from.clear();to.clear();search.clear();status.setValue("Todos");load();});
        export.setOnAction(e->export());search.setOnAction(e->load());load();
    }
    private VBox field(String label,Control field){return new VBox(4,new Label(label),field);}
    private void column(String name,Function<OrderReport.Row,String> value,int width){
        TableColumn<OrderReport.Row,String> column=new TableColumn<>(name);column.setPrefWidth(width);
        column.setCellValueFactory(c->new SimpleStringProperty(value.apply(c.getValue())));column.setSortable(false);table.getColumns().add(column);
    }
    private void dirty(){export.setDisable(true);message.setText("Filtros alterados. Clique em Aplicar filtros para atualizar e exportar.");}
    private LocalDate date(TextField input,String label){
        if(input.getText().isBlank())return null;
        try{return LocalDate.parse(input.getText().strip(),DATE);}
        catch(DateTimeParseException e){throw new ValidationException("date",label+": informe uma data válida em dd/mm/aaaa.");}
    }
    private void busy(boolean busy){filters.setDisable(busy);apply.setDisable(busy);clear.setDisable(busy);export.setDisable(busy||displayed==null);}
    private void load(){
        final ReportFilter filter;
        try{
            int index=status.getSelectionModel().getSelectedIndex();
            filter=new ReportFilter(date(from,"Data inicial"),date(to,"Data final"),index<=0?null:OrderStatus.values()[index-1],search.getText());
        }catch(ValidationException e){message.setText(e.getMessage());export.setDisable(true);return;}
        busy(true);message.setText("Consultando ordens...");
        Task<OrderReport> task=new Task<>(){@Override protected OrderReport call(){return service.report(filter);}};
        task.setOnSucceeded(e->{displayed=task.getValue();table.getItems().setAll(displayed.rows());
            summary.setText("Ordens: "+displayed.rows().size()+"  |  Fechadas: "+displayed.closedCount()+"  |  Recebido nas OS fechadas: "+money.format(displayed.receivedTotal())+"\nTotais relativos às ordens filtradas pela data de entrada.");
            busy(false);message.setText("Relatório atualizado. O PDF terá os mesmos filtros, linhas e totais.");});
        task.setOnFailed(e->{busy(false);export.setDisable(true);message.setText(task.getException() instanceof ValidationException?task.getException().getMessage():"Não foi possível consultar. Verifique a conexão com o banco e tente novamente.");});
        start(task);
    }
    private void export(){
        if(displayed==null)return;
        FileChooser chooser=new FileChooser();chooser.setTitle("Salvar relatório em PDF");chooser.setInitialFileName("engrenar-relatorio.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF","*.pdf"));
        var selected=chooser.showSaveDialog(owner);if(selected==null)return;
        Path target=selected.toPath();
        if(!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            target=target.resolveSibling(target.getFileName()+".pdf");
            if(java.nio.file.Files.exists(target)) {
                Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"O arquivo "+target.getFileName()+" já existe. Substituir?",ButtonType.YES,ButtonType.NO);
                confirm.initOwner(owner);if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            }
        }
        final Path destination=target;final OrderReport snapshot=displayed;busy(true);message.setText("Gerando PDF...");
        Task<Void> task=new Task<>(){@Override protected Void call()throws Exception{service.exportReport(snapshot,destination);return null;}};
        task.setOnSucceeded(e->{busy(false);message.setText("PDF salvo em "+destination);});
        task.setOnFailed(e->{busy(false);message.setText("Não foi possível salvar o PDF. Verifique a pasta e se o arquivo está aberto.");});start(task);
    }
    private void start(Task<?> task){Thread thread=new Thread(task,"engrenar-report");thread.setDaemon(true);thread.start();}
}
