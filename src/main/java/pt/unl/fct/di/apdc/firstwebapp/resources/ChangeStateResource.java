package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeStateData;
import pt.unl.fct.di.apdc.firstwebapp.enums.AccountState;

import java.util.Objects;
import java.util.logging.Logger;

@Path("/change-state")
public class ChangeStateResource {

    private static final String NON_PERMITTED_ATTEMPT = "User \"%s\" attempted a non-permitted account state change.";
    private static final String NOT_ENOUGH_PERMISSIONS =
            "The user that's associated with the token provided doesn't have the needed permissions to perform this operation.";

    private static final Logger LOG = Logger.getLogger(ChangeStateResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    public ChangeStateResource() {}

    private void removeUserTokens(String username, Transaction txn) {
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Token")
                .setFilter(StructuredQuery.PropertyFilter.eq("username", username))
                .build();
        QueryResults<Entity> tokenResults = txn.run(query);

        while (tokenResults.hasNext()) {
            Entity tokenEntity = tokenResults.next();
            txn.delete(tokenEntity.getKey());
        }
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeState(ChangeStateData data) {
        String targetUsername = data.targetUsername;
        String token = data.token;
        String state = data.state;

        LOG.info(String.format("Attempted state change of user \"%s\" to: %s", targetUsername, state));

        if (AccountState.fromDescription(state) == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Type of account state \"" + state + "\" doesn't exist.").build();
        }

        Transaction txn = datastore.newTransaction();

        try {
            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token);
            Entity tokenEntity = txn.get(tokenKey);

            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning(String.format("Account state change of user \"%s\" attempted", targetUsername));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Token \"" + token + "\" either isn't a valid token or it has expired.")
                        .build();
            }

            String userRole = tokenEntity.getString("user_role");
            String username = tokenEntity.getString("username");

            if (!(userRole.equals(Role.ADMIN.getDescription())
                    || userRole.equals(Role.BACKOFFICE.getDescription()))) {
                txn.rollback();
                LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(NOT_ENOUGH_PERMISSIONS)
                        .build();
            }

            Key targetUserKey = datastore.newKeyFactory().setKind("User").newKey(targetUsername);
            Entity targetUser = txn.get(targetUserKey);

            if (targetUser == null) {
                txn.rollback();
                LOG.warning("State change of user \"" + targetUsername + "\" attempted");
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("User \"" + targetUsername + "\" doesn't exist.")
                        .build();
            }

            String targetUserRole = targetUser.getString("user_role");

            if (!Objects.requireNonNull(Role.fromDescription(userRole))
                    .isRoleAbove(targetUserRole) || (userRole.equals(Role.BACKOFFICE.getDescription())
                    && state.equals(AccountState.SUSPENDED.getDescription()))) {
                txn.rollback();
                LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(NOT_ENOUGH_PERMISSIONS)
                        .build();
            }

            if (!AccountState.ACTIVE.getDescription().equals(state))
                removeUserTokens(targetUsername, txn);

            String oldState = targetUser.getString("user_state");

            targetUser = Entity.newBuilder(targetUser).set("user_state", state).build();

            txn.put(targetUser);
            txn.commit();
            LOG.info("User \"" + targetUsername + "\" had their account state changed from \"" + oldState
                    + "\" to \"" + state + "\" by user: " + username);
            return Response.ok("User \"" + targetUsername + "\" had their account state changed to \"" + state +
                    "\" successfully.").build();
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
