package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.util.RemoveUserData;

import java.util.Objects;
import java.util.logging.Logger;

@Path("/remove-user")
public class RemoveUserResource {

    private static final String NON_PERMITTED_ATTEMPT = "User \"%s\" attempted a non-permitted account removal.";
    private static final String NOT_ENOUGH_PERMISSIONS =
            "The user that's associated with the token provided doesn't have the needed permissions to perform this operation.";

    private static final Logger LOG = Logger.getLogger(RemoveUserResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    public RemoveUserResource() {}

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
    public Response removeUser(RemoveUserData data) {
        String targetId = data.targetId;
        String token = data.token;

        LOG.info(String.format("Attempted removal of user with the following email or username: %s", targetId));

        Transaction txn = datastore.newTransaction();

        try {
            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token);
            Entity tokenEntity = txn.get(tokenKey);

            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning(String.format("Removal of user of email or username \"%s\" attempted", targetId));
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

            if (targetId.contains("@")) {
                Query<Entity> emailQuery = Query.newEntityQueryBuilder()
                        .setKind("User")
                        .setFilter(StructuredQuery.PropertyFilter.eq("user_email", targetId))
                        .build();
                QueryResults<Entity> emailResults = datastore.run(emailQuery);

                if (!emailResults.hasNext()) {
                    txn.rollback();
                    LOG.warning("Removal of user with email \"" + targetId + "\" attempted");
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("User with email \"" + targetId + "\" doesn't exist.")
                            .build();
                }

                Entity target = emailResults.next();
                String targetUsername = target.getString("username");
                String targetRole = target.getString("user_role");

                if (!Objects.requireNonNull(Role.fromDescription(userRole))
                        .isRoleAbove(targetRole)) {
                    txn.rollback();
                    LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity(NOT_ENOUGH_PERMISSIONS)
                            .build();
                }

                removeUserTokens(targetUsername, txn);

                txn.delete(target.getKey());
                txn.commit();
                LOG.info("User removed: " + targetUsername);
                return Response.ok("User with email \"" + targetId + "\" was removed successfully.").build();
            } else {
                Key targetKey = datastore.newKeyFactory().setKind("User").newKey(targetId);
                Entity target = txn.get(targetKey);

                if (target == null) {
                    txn.rollback();
                    LOG.warning("Removal of user with username \"" + targetId + "\" attempted.");
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("User with username \"" + targetId + "\" doesn't exist.")
                            .build();
                }

                String targetRole = target.getString("user_role");

                if (!Objects.requireNonNull(Role.fromDescription(userRole))
                        .isRoleAbove(targetRole)) {
                    txn.rollback();
                    LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity(NOT_ENOUGH_PERMISSIONS)
                            .build();
                }

                removeUserTokens(targetId, txn);

                txn.delete(target.getKey());
                txn.commit();
                LOG.info("User removed: " + targetId);
                return Response.ok("User with username \"" + targetId + "\" was removed successfully.").build();
            }

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