package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Logger;

import com.google.cloud.datastore.*;
import org.apache.commons.codec.digest.DigestUtils;

import com.google.cloud.Timestamp;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.enums.AccountState;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.util.RegisterData;

@Path("/register")
public class RegisterResource {

	private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
	private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

	public RegisterResource() {}	// Default constructor, nothing to do

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerUser(RegisterData data) {
		String username = data.username;
		
		LOG.fine("Attempt to register user: " + username);

		String registrationValidity = data.checkRegistrationValidity();

		if (!registrationValidity.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity(registrationValidity).build();
		}

		Transaction txn = datastore.newTransaction();
		try {
			Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
			Entity user = txn.get(userKey);

			if (user != null) {
				txn.rollback();
				return Response.status(Status.CONFLICT).entity("User \"" + username + "\" already exists.").build();
			}

			String email = data.email;

			Query<Entity> emailQuery = Query.newEntityQueryBuilder()
					.setKind("User")
					.setFilter(StructuredQuery.PropertyFilter.eq("user_email", email))
					.build();
			QueryResults<Entity> emailResults = txn.run(emailQuery);

			if (emailResults.hasNext()) {
				txn.rollback();
				return Response.status(Status.CONFLICT)
						.entity("Email already in use.").build();
			}


			Entity.Builder userBuilder = Entity.newBuilder(userKey).set("username", username)
					.set("user_name", data.name)
					.set("user_pwd", DigestUtils.sha512Hex(data.password))
					.set("user_email", email)
					.set("user_phone", data.phone)
					.set("user_privacy", data.privacy)
					.set("user_role", Role.ENDUSER.getDescription())
					.set("user_state", AccountState.DISABLED.getDescription())
					.set("user_creation_time", Timestamp.now());


			if (data.id != null && !data.id.isBlank()) {
				userBuilder.set("user_id", data.id);
			}
			if (data.financialId != null && !data.financialId.isBlank()) {
				userBuilder.set("user_financialId", data.financialId);
			}
			if (data.employer != null && !data.employer.isBlank()) {
				userBuilder.set("user_employer", data.employer);
			}
			if (data.function != null && !data.function.isBlank()) {
				userBuilder.set("user_function", data.function);
			}
			if (data.address != null && !data.address.isBlank()) {
				userBuilder.set("user_address", data.address);
			}
			if (data.employerFinancialId != null && !data.employerFinancialId.isBlank()) {
				userBuilder.set("user_employerFId", data.employerFinancialId);
			}

			txn.put(userBuilder.build());
			txn.commit();
			LOG.info("User registered: " + username);
			return Response.ok("User \"" + username + "\" was registered successfully.").build();
		} catch (Exception e) {
			if (txn.isActive())
				txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.toString()).build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}
}
