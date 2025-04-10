package pt.unl.fct.di.apdc.firstwebapp.util;

public class ChangeStateData {

    public String token;
    public String targetUsername;
    public String state;

    public ChangeStateData() {}

    public ChangeStateData(String token, String targetUsername, String state) {
        this.token = token;
        this.targetUsername = targetUsername;
        this.state = state;
    }
}
