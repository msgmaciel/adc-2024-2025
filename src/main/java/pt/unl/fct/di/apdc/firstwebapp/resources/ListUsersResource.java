package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.enums.AccountState;
import pt.unl.fct.di.apdc.firstwebapp.enums.Privacy;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Logger;

@Path("/list-users")
public class ListUsersResource {

    private static final Logger LOG = Logger.getLogger(ListUsersResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    public ListUsersResource() {}

    private String formatTimestamp(Timestamp timestamp) {
        Date date = timestamp.toDate();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return formatter.format(date);
    }

    private String formatUser(Entity currentUser, boolean detailed) {
        StringBuilder sb = new StringBuilder();
        sb.append("User:\n")
                .append(String.format("\tusername: %s\n", currentUser.getString("username")))
                .append(String.format("\tname: %s\n", currentUser.getString("user_name")))
                .append(String.format("\temail: %s\n", currentUser.getString("user_email")));

        if (detailed) {
            sb.append(String.format("\tphone: %s\n", currentUser.getString("user_phone")))
                    .append(String.format("\tprivacy: %s\n", currentUser.getString("user_privacy")))
                    .append(String.format("\trole: %s\n", currentUser.getString("user_role")))
                    .append(String.format("\tstate: %s\n", currentUser.getString("user_state")))
                    .append(String.format("\tcreation time: %s\n", formatTimestamp(currentUser.getTimestamp("user_creation_time"))));

            if (currentUser.contains("user_id"))
                sb.append(String.format("\tid: %s\n", currentUser.getString("user_id")));
            if (currentUser.contains("user_financialId"))
                sb.append(String.format("\tfinancial id: %s\n", currentUser.getString("user_financialId")));
            if (currentUser.contains("user_employer"))
                sb.append(String.format("\temployer: %s\n", currentUser.getString("user_employer")));
            if (currentUser.contains("user_function"))
                sb.append(String.format("\tfunction: %s\n", currentUser.getString("user_function")));
            if (currentUser.contains("user_address"))
                sb.append(String.format("\taddress: %s\n", currentUser.getString("user_address")));
            if (currentUser.contains("user_employerFId"))
                sb.append(String.format("\temployer financial id: %s\n", currentUser.getString("user_employerFId")));
        }
        sb.append("\n");
        return sb.toString();
    }


    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listUsers(String tokenJson) {
        JsonObject jsonObject = JsonParser.parseString(tokenJson).getAsJsonObject();

        Transaction txn = datastore.newTransaction();
        try {

            String token = jsonObject.get("token").getAsString();

            LOG.info(String.format("There was an attempt to consult the user list with token: %s", token));

            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token);
            Entity tokenEntity = txn.get(tokenKey);

            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning(String.format("Failed attempt to consult the user list with token: %s", token));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Token \"" + token + "\" either isn't a valid token or it has expired.")
                        .build();
            }

            String roleDescription = tokenEntity.getString("user_role");
            String username = tokenEntity.getString("username");

            if (Role.PARTNER.getDescription().equals(roleDescription)) {
                txn.rollback();
                LOG.warning(String.format("User \"%s\" attempted a non-permitted user list consultation.", username));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("The user that's associated with the token provided doesn't have the needed permissions to perform this operation.")
                        .build();
            }

            Role role = Objects.requireNonNull(Role.fromDescription(roleDescription));
            String roleKey = "user_role";
            QueryResults<Entity> queryResults;
            StringBuilder output = new StringBuilder();
            switch (role) {
                case ADMIN -> {
                    queryResults = txn.run(Query.newEntityQueryBuilder().setKind("User").build());
                    while (queryResults.hasNext()) {
                        Entity currentUser = queryResults.next();
                        output.append(formatUser(currentUser, true));
                    }
                }
                case BACKOFFICE -> {
                    queryResults = txn.run(Query.newEntityQueryBuilder().setKind("User")
                            .setFilter(StructuredQuery.PropertyFilter.eq(roleKey, Role.ENDUSER.getDescription()))
                            .build());
                    while (queryResults.hasNext()) {
                        Entity currentUser = queryResults.next();
                        output.append(formatUser(currentUser, true));
                    }
                }
                default -> {
                    queryResults = txn.run(Query.newEntityQueryBuilder().setKind("User")
                            .setFilter(StructuredQuery.PropertyFilter.eq(roleKey, roleDescription))
                            .setFilter(StructuredQuery.PropertyFilter.eq("user_privacy", Privacy.PUBLIC.getDescription()))
                            .setFilter(StructuredQuery.PropertyFilter.eq("user_state", AccountState.ACTIVE.getDescription()))
                            .build());

                    while (queryResults.hasNext()) {
                        Entity currentUser = queryResults.next();
                        output.append(formatUser(currentUser, false));
                    }
                }
            }

            txn.commit();
            LOG.info("User \"%s\" made a user list consultation.");
            return Response.ok(output.toString()).build();
        } catch (Exception e) {
            if (txn.isActive())
                txn.rollback();
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.toString()).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

}
