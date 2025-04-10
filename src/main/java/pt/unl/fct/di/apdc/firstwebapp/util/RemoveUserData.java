package pt.unl.fct.di.apdc.firstwebapp.util;

public class RemoveUserData {

    public String token;
    public String targetId;

    public RemoveUserData() {}

    public RemoveUserData(String token, String targetId) {
        this.token = token;
        this.targetId = targetId;
    }
}
