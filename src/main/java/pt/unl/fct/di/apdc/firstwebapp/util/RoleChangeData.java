package pt.unl.fct.di.apdc.firstwebapp.util;

public class RoleChangeData {

    public String token;
    public String targetUsername;
    public String role;

    public RoleChangeData() {}

    public RoleChangeData(String token, String targetUsername, String role) {
        this.token = token;
        this.targetUsername = targetUsername;
        this.role = role;
    }
}
