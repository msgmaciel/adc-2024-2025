package pt.unl.fct.di.apdc.firstwebapp.util;

public class ChangePasswordData {

    public String token;
    public String currentPassword;
    public String newPassword;
    public String confirmation;

    public ChangePasswordData() {}

    public ChangePasswordData(String token, String currentPassword, String newPassword, String confirmation) {
        this.token = token;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmation = confirmation;
    }

    public boolean emptyOrBlankField(String field) {
        return field == null || field.isBlank();
    }

    public boolean isPasswordInvalid(String password) {
        return !password.matches(".*[a-z].*") ||
                !password.matches(".*[A-Z].*") ||
                !password.matches(".*\\d.*") ||
                !password.matches(".*\\p{Punct}.*");
    }

    public String validate() {
        StringBuilder validity = new StringBuilder();

        if (emptyOrBlankField(newPassword))
            validity.append("New password must not be empty;\n");
        else if (isPasswordInvalid(newPassword))
            validity.append("New password must have at least one lowercase letter, one uppercase letter, a number and a punctuation symbol;\n");
        if (emptyOrBlankField(confirmation))
            validity.append("Confirmation password must not be empty;\n");
        if (!newPassword.equals(confirmation))
            validity.append("New password and confirmation must match;\n");

        if (validity.isEmpty())
            return validity.toString();
        else
            return "Some fields weren't filled in a valid manner:\n\n" + validity.toString();
    }
}
