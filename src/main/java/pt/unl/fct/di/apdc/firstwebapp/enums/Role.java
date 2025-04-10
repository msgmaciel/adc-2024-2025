package pt.unl.fct.di.apdc.firstwebapp.enums;

public enum Role {

    ENDUSER("enduser"),
    PARTNER("partner"),
    BACKOFFICE("backoffice"),
    ADMIN("admin");

    private final String description;

    Role (String description) {
        this.description = description;
    }

    public int getHierarchyLevel(String description) {
        return switch (description) {
            case "admin" -> 3;
            case "backoffice" -> 2;
            default -> 1;
        };
    }

    public boolean isRoleAbove(String description) {
        int level1 = getHierarchyLevel(this.getDescription());
        int level2 = getHierarchyLevel(description);
        return level1 > level2;
    }

    public String getDescription() {
        return description;
    }

    public static Role fromDescription(String description) {
        for (Role role : Role.values()) {
            if (role.getDescription().equals(description)) {
                return role;
            }
        }
        return null;
    }
}
