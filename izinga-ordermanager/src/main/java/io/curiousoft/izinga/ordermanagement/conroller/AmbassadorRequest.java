package io.curiousoft.izinga.ordermanagement.conroller;

public class AmbassadorRequest {
    private String name;
    private String phone;
    private String bankAccount;
    private String bankName;
    private String branchCode;

    public AmbassadorRequest() {}

    public AmbassadorRequest(String name, String phone, String bankAccount, String bankName, String branchCode) {
        this.name = name;
        this.phone = phone;
        this.bankAccount = bankAccount;
        this.bankName = bankName;
        this.branchCode = branchCode;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
}
