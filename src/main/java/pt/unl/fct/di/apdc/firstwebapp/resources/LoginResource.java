package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;
import com.google.gson.Gson;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.enums.AccountState;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthToken;
import pt.unl.fct.di.apdc.firstwebapp.util.LoginData;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

	private static final String MESSAGE_INVALID_CREDENTIALS = "Incorrect username or password.";
	private static final String MESSAGE_NEXT_PARAMETER_INVALID = "Request parameter 'next' must be greater or equal to 0.";

	private static final String LOG_MESSAGE_LOGIN_ATTEMPT = "Login attempt by user: ";
	private static final String LOG_MESSAGE_LOGIN_SUCCESSFUL = "Login successful by user: ";
	private static final String LOG_MESSAGE_WRONG_PASSWORD = "Wrong password for: ";

	private static final String USER_PWD = "user_pwd";
	private static final String USER_LOGIN_TIME = "user_login_time";

	private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
	private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

	private final Gson g = new Gson();

	public LoginResource() {}

	private String formatTimestamp(Timestamp timestamp) {
		Date date = timestamp.toDate();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		return formatter.format(date);
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_HTML)
	public Response doLogin(LoginData data) {
		String username = data.username;

		LOG.fine(LOG_MESSAGE_LOGIN_ATTEMPT + username);

		Key userKey = userKeyFactory.newKey(username);

		Transaction txn = datastore.newTransaction();
		try {
			Entity user = txn.get(userKey);
			if (user == null) {
				txn.rollback();
				LOG.warning(LOG_MESSAGE_LOGIN_ATTEMPT + username);
				return Response.status(Status.FORBIDDEN)
						.entity(MESSAGE_INVALID_CREDENTIALS)
						.build();
			}

			if (!AccountState.ACTIVE.getDescription().equals(user.getString("user_state"))) {
				txn.rollback();
				LOG.warning(LOG_MESSAGE_LOGIN_ATTEMPT + username);
				return Response.status(Status.FORBIDDEN)
						.entity("Account must be activated by a BACKOFFICE or ADMIN account for the login operation to be available.")
						.build();
			}

			String hashedPWD = user.getString(USER_PWD);
			if (hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {
				AuthToken token = new AuthToken(username, user.getString("user_role"));

				String tokenId = token.token;
				Timestamp unformattedValidFrom = token.validFrom;
				Timestamp unformattedValidUntil = token.validUntil;

				Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);
				String role = token.role;

				Entity tokenEntity = Entity.newBuilder(tokenKey).set("username", token.username)
						.set("user_role", role)
						.set("valid_from", unformattedValidFrom)
						.set("valid_until", unformattedValidUntil)
						.set("token", tokenId).build();

				String formattedValidFrom = formatTimestamp(unformattedValidFrom);
				String formattedValidUntil = formatTimestamp(unformattedValidUntil);

				txn.put(tokenEntity);
				txn.commit();
				LOG.info(LOG_MESSAGE_LOGIN_SUCCESSFUL + username);
				String htmlResponse = "<!DOCTYPE html>\n"
						+ "<html lang=\"en\">\n"
						+ "<head>\n"
						+ "  <meta charset=\"UTF-8\">\n"
						+ "  <title>Welcome Page</title>\n"
						+ "  <style>\n"
						+ "    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f1f1f1; }\n"
						+ "    .container { background-color: #fff; padding: 25px; border-radius: 5px; max-width: 700px; margin: auto; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n"
						+ "    h1 { color: #333; }\n"
						+ "    h2 { color: #666; }\n"
						+ "    ul { list-style-type: disc; margin-left: 20px; }\n"
						+ "    .note { font-size: 0.9em; color: #555; margin-top: 15px; }\n"
						+ "  </style>\n"
						+ "</head>\n"
						+ "<body>\n"
						+ "  <div class=\"container\">\n"
						+ "    <h1>Welcome, " + username + "!</h1>\n"
						+ "    <p>You have successfully logged in as a <strong>" + token.role + "</strong>.</p>\n"
						+ "    <p>your token is <strong>'" + tokenId + "'</strong> and it's valid from <strong>" + formattedValidFrom
						+ "	   </strong> to <strong>" + formattedValidUntil + "</strong></p>\n"
						+ "    <p><strong>IMPORTANT! Store the given token to be able to provide it in operations that require one</strong>.</p>\n"
						+ "    <h2>Available operations for your role</h2>\n"
						+ "    <div id=\"operations\">\n"
						+ "      <!-- Render appropriate operations based on the user's role -->\n"
						+ "      <div class=\"role-" + token.role + "\">\n"
						+ "        <ul>\n";

				if (Role.ENDUSER.getDescription().equals(role)) {
					htmlResponse += "          <li>Change password</li>\n"
							+ "          <li>Modify your account attributes</li>\n"
							+ "          <li>Logout</li>\n";
				} else if (Role.BACKOFFICE.getDescription().equals(role)) {
					htmlResponse += "          <li>Create or modify worksheets</li>\n"
							+ "          <li>Change password</li>\n"
							+ "          <li>Change a user's role (between ENDUSER and PARTNER)</li>\n"
							+ "          <li>Change an account state</li>\n"
							+ "          <li>Remove user accounts (ENDUSER or PARTNER accounts)</li>\n"
							+ "          <li>List users</li>\n"
							+ "          <li>Modify either your account attributes others' (for ENDUSER or PARTNER accounts)</li>\n"
							+ "          <li>Logout</li>\n";
				} else if (Role.ADMIN.getDescription().equals(role)) {
					htmlResponse += "          <li>Change password</li>\n"
							+ "          <li>Change role (for any account)</li>\n"
							+ "          <li>Change account state (for any account)</li>\n"
							+ "          <li>Remove any user account</li>\n"
							+ "          <li>List users</li>\n"
							+ "          <li>Modify account attributes (for any account)</li>\n"
							+ "          <li>Change password</li>\n"
							+ "          <li>Logout</li>\n";
				} else if (Role.PARTNER.getDescription().equals(role)) {
					htmlResponse += "          <li>Create or modify worksheets</li>\n"
							+ "          <li>Change password</li>\n"
							+ "          <li>Logout</li>\n";
				}

				htmlResponse += "        </ul>\n"
						+ "      </div>\n"
						+ "    </div>\n"
						+ "  </div>\n"
						+ "</body>\n"
						+ "</html>";

				return Response.ok(htmlResponse, MediaType.TEXT_HTML).build();
			} else {
				txn.rollback();
				LOG.warning(LOG_MESSAGE_WRONG_PASSWORD + username);
				return Response.status(Status.FORBIDDEN).entity(MESSAGE_INVALID_CREDENTIALS).build();
			}
		} catch (Exception e) {
			if (txn.isActive())
				txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}


}
