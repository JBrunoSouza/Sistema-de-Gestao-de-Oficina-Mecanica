package br.edu.univasf.engrenar.model;
import java.math.BigDecimal;
public record OrderItem(long id, long orderId, String kind, String description, int quantity, BigDecimal unitPrice, Long partId, boolean completed, String observations) {
    public OrderItem(long id,long orderId,String kind,String description,int quantity,BigDecimal unitPrice,Long partId,boolean completed){this(id,orderId,kind,description,quantity,unitPrice,partId,completed,"");}
    public BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
