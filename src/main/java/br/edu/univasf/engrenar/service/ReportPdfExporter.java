package br.edu.univasf.engrenar.service;

import br.edu.univasf.engrenar.model.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.IOException;
import java.nio.file.*;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Paginated A4 landscape report. Writes atomically to preserve an existing file on failure. */
final class ReportPdfExporter {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final PDFont regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private static final float[] X={36,108,366,444,543,690};
    private static final float[] WIDTH={64,250,70,91,139,109};

    void write(OrderReport report,Path target) throws IOException {
        Objects.requireNonNull(report);Objects.requireNonNull(target);
        Path absolute=target.toAbsolutePath();
        Path temp=Files.createTempFile(absolute.getParent(),"engrenar-report-",".pdf");
        try {
            try(PDDocument doc=new PDDocument()) {
                doc.getDocumentInformation().setTitle("Engrenar - Relatório de ordens de serviço");
                int offset=0,page=0;
                do {
                    PDPage sheet=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));doc.addPage(sheet);page++;
                    try(PDPageContentStream out=new PDPageContentStream(doc,sheet)) {
                        text(out,36,551,"ENGRENAR | Relatório de ordens de serviço",bold,18);
                        var f=report.filter();
                        text(out,36,529,"Entrada: "+date(f.from())+" a "+date(f.to())+" | Status: "+(f.status()==null?"Todos":f.status()),regular,10);
                        text(out,36,513,fit("Cliente / placa: "+(f.search().isBlank()?"Todos":f.search()),regular,10,765),regular,10);
                        text(out,36,493,"Ordens: "+report.rows().size()+" | Fechadas: "+report.closedCount()+" | Recebido nas OS fechadas: "+money.format(report.receivedTotal()),bold,11);
                        text(out,36,477,"Totais consideram somente as ordens filtradas pela data de entrada. Valores em reais (R$).",regular,9);
                        String[] headings={"OS","Cliente","Placa","Entrada","Status","Valor decidido"};
                        out.setNonStrokingColor(0.92f,0.94f,0.96f);out.addRect(32,449,778,19);out.fill();out.setNonStrokingColor(0f,0f,0f);
                        for(int i=0;i<headings.length;i++)text(out,X[i],455,headings[i],bold,10);
                        float y=431;
                        if(report.rows().isEmpty())text(out,36,y,"Nenhuma ordem encontrada para os filtros informados.",regular,11);
                        while(offset<report.rows().size()) {
                            var row=report.rows().get(offset);
                            List<String> names=wrap(row.customer(),regular,10,WIDTH[1]);
                            float height=Math.max(24,names.size()*13+9);
                            if(y-height<55)break;
                            String[] values={row.number(),"",row.plate(),row.entryDate().format(DATE),row.status().toString(),row.agreedTotal()==null?"Não decidido":money.format(row.agreedTotal())};
                            for(int i=0;i<values.length;i++)if(i!=1)text(out,X[i],y,fit(values[i],regular,10,WIDTH[i]),regular,10);
                            for(int i=0;i<names.size();i++)text(out,X[1],y-i*13,names.get(i),regular,10);
                            out.setStrokingColor(0.85f,0.85f,0.85f);out.moveTo(32,y-height+10);out.lineTo(810,y-height+10);out.stroke();
                            y-=height;offset++;
                        }
                        text(out,36,29,"Gerado em "+report.generatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),regular,9);
                        text(out,738,29,"Página "+page,regular,9);
                    }
                }while(offset<report.rows().size());
                doc.save(temp.toFile());
            }
            try {Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    private String date(java.time.LocalDate date){return date==null?"Sem limite":date.format(DATE);}
    private String safe(String value) throws IOException {
        StringBuilder result=new StringBuilder();
        for(int cp:value.codePoints().toArray()) {
            String s=new String(Character.toChars(cp));
            if(Character.isISOControl(cp)){result.append(' ');continue;}
            try{regular.encode(s);result.append(s);}catch(IllegalArgumentException e){result.append('?');}
        }
        return result.toString();
    }
    private void text(PDPageContentStream out,float x,float y,String value,PDFont font,float size)throws IOException{
        out.beginText();out.setFont(font,size);out.newLineAtOffset(x,y);out.showText(safe(value));out.endText();
    }
    private String fit(String value,PDFont font,float size,float width)throws IOException{
        String result=safe(value);
        if(font.getStringWidth(result)*size/1000<=width)return result;
        while(!result.isEmpty()&&font.getStringWidth(result+"...")*size/1000>width)result=result.substring(0,result.length()-1);
        return result+"...";
    }
    private List<String> wrap(String value,PDFont font,float size,float width)throws IOException{
        List<String> lines=new ArrayList<>();String line="";
        for(String word:safe(value).strip().split("\\s+")) {
            String combined=line.isEmpty()?word:line+" "+word;
            if(font.getStringWidth(combined)*size/1000<=width){line=combined;continue;}
            if(!line.isEmpty()){lines.add(line);line="";}
            while(font.getStringWidth(word)*size/1000>width){
                int end=1;while(end<word.length()&&font.getStringWidth(word.substring(0,end+1))*size/1000<=width)end++;
                lines.add(word.substring(0,end));word=word.substring(end);
            }
            line=word;
        }
        if(!line.isEmpty())lines.add(line);return lines;
    }
}
