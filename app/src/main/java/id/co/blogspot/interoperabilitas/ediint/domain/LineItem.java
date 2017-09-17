package id.co.blogspot.interoperabilitas.ediint.domain;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class LineItem {
    private String currencyCode;//Use ISO 4217 three alpha code.
    private String description;
    private String quantity;
    private String totalPrice;
    private String unitPrice;

    public LineItem(String currencyCode, String description, String quantity, String totalPrice, String unitPrice) {
        this.currencyCode = currencyCode;
        this.description = description;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.unitPrice = unitPrice;
    }

    //Use ISO 4217 three alpha code.
    public String getCurrencyCode() {
        return currencyCode;
    }

    //Use ISO 4217 three alpha code.
    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(String totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(String unitPrice) {
        this.unitPrice = unitPrice;
    }
}