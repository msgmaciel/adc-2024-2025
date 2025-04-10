package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.Transaction;
import com.google.gson.Gson;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
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

	@GET
	@Path("/{username}")
	public Response checkUsernameAvailable(@PathParam("username") String username) {
		if (username.trim().equals("user")) {
			return Response.ok().entity(g.toJson(true)).build();
		} else {
			return Response.ok().entity(g.toJson(false)).build();
		}
	}

	private String formatTimestamp(Timestamp timestamp) {
		Date date = timestamp.toDate();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		return formatter.format(date);
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
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


			String hashedPWD = user.getString(USER_PWD);
			if (hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {
				AuthToken token = new AuthToken(username, user.getString("user_role"));

				String tokenId = token.token;
				Timestamp unformattedValidFrom = token.validFrom;
				Timestamp unformattedValidUntil = token.validUntil;

				Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);

				Entity tokenEntity = Entity.newBuilder(tokenKey).set("username", token.username)
						.set("user_role", token.role)
						.set("valid_from", unformattedValidFrom)
						.set("valid_until", unformattedValidUntil)
						.set("token", tokenId).build();

				String formattedValidFrom = formatTimestamp(unformattedValidFrom);
				String formattedValidUntil = formatTimestamp(unformattedValidUntil);

				txn.put(tokenEntity);
				txn.commit();
				LOG.info(LOG_MESSAGE_LOGIN_SUCCESSFUL + username);
				return Response.ok("Login was successful, your token is: " + tokenId + "\n"
				+ "and it's valid from " + formattedValidFrom + " to " + formattedValidUntil + "\n"
				+ "IMPORTANT! Store the given token to be able to provide it in operations that require one.\n").build();
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

	@POST
	@Path("/user")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLatestLogins(LoginData data) {

		Key userKey = userKeyFactory.newKey(data.username);

		Entity user = datastore.get(userKey);
		if (user != null && user.getString(USER_PWD).equals(DigestUtils.sha512Hex(data.password))) {

			// Get the date of yesterday
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, -1);
			Timestamp yesterday = Timestamp.of(cal.getTime());

			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserLog")
					.setFilter(
							CompositeFilter.and(
									PropertyFilter.hasAncestor(
											datastore.newKeyFactory().setKind("User").newKey(data.username)),
									PropertyFilter.ge(USER_LOGIN_TIME, yesterday)))
					.setOrderBy(OrderBy.desc(USER_LOGIN_TIME))
					.setLimit(3)
					.build();
			QueryResults<Entity> logs = datastore.run(query);

			List<Date> loginDates = new ArrayList<Date>();
			logs.forEachRemaining(userlog -> {
				loginDates.add(userlog.getTimestamp(USER_LOGIN_TIME).toDate());
			});

			return Response.ok(g.toJson(loginDates)).build();
		}
		return Response.status(Status.FORBIDDEN).entity(MESSAGE_INVALID_CREDENTIALS)
				.build();
	}

	@POST
	@Path("/user/pagination")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLatestLogins(@QueryParam("next") String nextParam, LoginData data) {

		int next;

		// Checking for valid request parameter values
		try {
			next = Integer.parseInt(nextParam);
			if (next < 0)
				return Response.status(Status.BAD_REQUEST).entity(MESSAGE_NEXT_PARAMETER_INVALID).build();
		} catch (NumberFormatException e) {
			return Response.status(Status.BAD_REQUEST).entity(MESSAGE_NEXT_PARAMETER_INVALID).build();
		}

		Key userKey = userKeyFactory.newKey(data.username);

		Entity user = datastore.get(userKey);
		if (user != null && user.getString(USER_PWD).equals(DigestUtils.sha512Hex(data.password))) {

			// Get the date of yesterday
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, -1);
			Timestamp yesterday = Timestamp.of(cal.getTime());

			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserLog")
					.setFilter(
							CompositeFilter.and(
									PropertyFilter.hasAncestor(
											datastore.newKeyFactory().setKind("User").newKey(data.username)),
									PropertyFilter.ge(USER_LOGIN_TIME, yesterday)))
					.setOrderBy(OrderBy.desc(USER_LOGIN_TIME))
					.setLimit(3)
					.setOffset(next)
					.build();
			QueryResults<Entity> logs = datastore.run(query);

			List<Date> loginDates = new ArrayList<Date>();
			logs.forEachRemaining(userlog -> {
				loginDates.add(userlog.getTimestamp(USER_LOGIN_TIME).toDate());
			});

			return Response.ok(g.toJson(loginDates)).build();
		}
		return Response.status(Status.FORBIDDEN).entity(MESSAGE_INVALID_CREDENTIALS)
				.build();
	}

}
