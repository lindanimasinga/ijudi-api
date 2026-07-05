package io.curiousoft.izinga.ordermanagement.leads;

public class LeadItem {
    private String stockId;
    private String name;
    private int quantity;

    public LeadItem() {}

    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
