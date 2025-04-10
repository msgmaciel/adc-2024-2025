package pt.unl.fct.di.apdc.firstwebapp.enums;

public enum Privacy {

    PUBLIC("public"),
    PRIVATE("private");

    private final String description;

    Privacy (String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
