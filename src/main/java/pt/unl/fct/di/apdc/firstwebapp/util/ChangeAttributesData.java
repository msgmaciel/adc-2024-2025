package pt.unl.fct.di.apdc.firstwebapp.util;

import pt.unl.fct.di.apdc.firstwebapp.enums.AccountState;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;

public class ChangeAttributesData extends RegisterData {

    public static final String INVALID_ROLE = "Role must be one of either 'enduser', 'partner', 'backoffice' or 'admin'";
    public static final String INVALID_STATE = "State must be one of either 'active', 'suspended' or 'disabled'";

    public String token;
    public String targetUsername;
    public String state;
    public String role;

    public ChangeAttributesData() {}

    public ChangeAttributesData(String token, String targetUsername,
                                String username, String password, String confirmation,
                                String email, String name, String phone, String privacy,
                                String id, String financialId, String employer, String function,
                                String address, String employerFinancialId,
                                String state, String role) {
        super(username, password, confirmation, email, name, phone, privacy, id, financialId, employer, function, address, employerFinancialId);
        this.token = token;
        this.targetUsername = targetUsername;
        this.state = state;
        this.role = role;
    }

    public boolean isRoleInvalid() {
        return Role.fromDescription(this.role) == null;
    }

    public boolean isStateInvalid() {
        return AccountState.fromDescription(this.state) == null;
    }
}
