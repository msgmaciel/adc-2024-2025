package pt.unl.fct.di.apdc.firstwebapp.util;

import pt.unl.fct.di.apdc.firstwebapp.enums.Privacy;

public class RegisterData {

	private static final String NEXT_LINE = ";\n";

	public static final String USERNAME_NOT_CONTAIN_AT = "Username cannot contain '@'";
	public static final String INVALID_PASSWORD = "Password must have both a lowercase letter and uppercase letter, a number and punctuation symbol";
	public static final String EQUAL_TO_CONFIRMATION = "Password must be equal to confirmation";
	public static final String INVALID_EMAIL = "Email address must be valid";
	public static final String INVALID_PHONE_NUMBER = "Phone number must something in format: +(country code)(phone number)";
	public static final String INVALID_PRIVACY = "Privacy must be either 'public' or 'private'";


    public String username;
	public String password;
	public String confirmation;
	public String email;
	public String name;
	public String phone;
	public String privacy;

	public String id;
	public String financialId;
	public String employer;
	public String function;
	public String address;
	public String employerFinancialId;

	
	
	public RegisterData() {}
	
	public RegisterData(String username, String password, String confirmation, String email, String name, String phone,
						String privacy, String id, String financialId, String employer, String function, String address,
						String employerFinancialId) {
		this.username = username;
		this.password = password;
		this.confirmation = confirmation;
		this.email = email;
		this.name = name;
		this.phone = phone;
		this.privacy = privacy;

		this.id = id;
		this.financialId = financialId;
		this.employer = employer;
		this.function = function;
		this.address = address;
		this.employerFinancialId = employerFinancialId;
	}
	
	public boolean emptyOrBlankField(String field) {
		return field == null || field.isBlank();
	}

	public boolean isPasswordInvalid() {
		return !password.matches(".*[a-z].*") ||
				!password.matches(".*[A-Z].*") ||
				!password.matches(".*\\d.*") ||
				!password.matches(".*\\p{Punct}.*");
	}

	public boolean doesUsernameHaveAt() {
		return username.contains("@");
	}

	public boolean isEmailInvalid() {
		return !email.contains("@");
	}

	public boolean isPhoneInvalid() {
		return !phone.contains("+");
	}

	public boolean isPrivacyInvalid() {
		return !(privacy.equals(Privacy.PUBLIC.getDescription()) ||
				privacy.equals(Privacy.PRIVATE.getDescription()));
	}

	public String checkRegistrationValidity() {
		StringBuilder validity = new StringBuilder();

		if (emptyOrBlankField(username))
			validity.append("Username cannot be empty;\n");

		if (doesUsernameHaveAt())
			validity.append(USERNAME_NOT_CONTAIN_AT)
					.append(NEXT_LINE);

		if (emptyOrBlankField(password))
			validity.append("Password must not be empty;\n");
		else if (isPasswordInvalid())
			validity.append(INVALID_PASSWORD)
					.append(NEXT_LINE);
		else if (!password.equals(confirmation))
			validity.append(EQUAL_TO_CONFIRMATION)
					.append(NEXT_LINE);

		if (emptyOrBlankField(email) ||
				isEmailInvalid())
			validity.append(INVALID_EMAIL)
					.append(NEXT_LINE);

		if (emptyOrBlankField(name))
			validity.append("Name must not be empty;\n");

		if (emptyOrBlankField(phone) ||
				isPhoneInvalid())
			validity.append(INVALID_PHONE_NUMBER)
					.append(NEXT_LINE);

		if (emptyOrBlankField(privacy) ||
				isPrivacyInvalid())
			validity.append(INVALID_PRIVACY)
					.append(NEXT_LINE);

		if (validity.isEmpty())
			return validity.toString();
		else
			return "Invalid user attributes were input:\n\n" + validity.toString();
	}
}
