package pt.unl.fct.di.apdc.firstwebapp.enums;

public enum AccountState {

    ACTIVE("active"),
    SUSPENDED("suspended"),
    DISABLED("disabled");

    private final String description;

    AccountState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static AccountState fromDescription(String description) {
        for (AccountState state : AccountState.values()) {
            if (state.getDescription().equals(description)) {
                return state;
            }
        }
        return null;
    }
}
