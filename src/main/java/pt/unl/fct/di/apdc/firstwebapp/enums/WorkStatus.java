package pt.unl.fct.di.apdc.firstwebapp.enums;

public enum WorkStatus {

    NOT_STARTED("not started"),
    IN_PROGRESS("in progress"),
    COMPLETED("completed");

    private final String description;

    WorkStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static WorkStatus fromDescription(String description) {
        for (WorkStatus status : WorkStatus.values()) {
            if (status.getDescription().equals(description)) {
                return status;
            }
        }
        return null;
    }
}
