package pt.unl.fct.di.apdc.firstwebapp.enums;

public enum AwardStatus {

    AWARDED("awarded"),
    NOT_AWARDED("not awarded");

    private final String description;

    AwardStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static AwardStatus fromDescription(String description) {
        for (AwardStatus status : AwardStatus.values()) {
            if (status.getDescription().equals(description)) {
                return status;
            }
        }
        return null;
    }
}
