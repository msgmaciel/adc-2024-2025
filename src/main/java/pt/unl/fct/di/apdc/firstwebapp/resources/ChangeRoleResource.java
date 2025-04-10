package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.util.RoleChangeData;

import java.util.Objects;
import java.util.logging.Logger;

@Path("/change-role")
public class ChangeRoleResource {

    private static final String NON_PERMITTED_ATTEMPT = "User \"%s\" attempted a non-permitted role change.";
    private static final String NOT_ENOUGH_PERMISSIONS =
            "The user that's associated with the token provided doesn't have the needed permissions to perform this operation.";

    private static final Logger LOG = Logger.getLogger(ChangeRoleResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    public ChangeRoleResource() {}

    private void changeRoleInTokens(String username, String role, Transaction txn) {
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Token")
                .setFilter(StructuredQuery.PropertyFilter.eq("username", username))
                .build();
        QueryResults<Entity> tokenResults = txn.run(query);

        while (tokenResults.hasNext()) {
            Entity tokenEntity = tokenResults.next();
            txn.put(Entity.newBuilder(tokenEntity).set("user_role", role).build());
        }
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeRole(RoleChangeData data) {
        String targetUsername = data.targetUsername;
        String role = data.role;
        String token = data.token;

        LOG.info(String.format("Attempted role change of user \"%s\" to: %s", targetUsername, role));

        if (Role.fromDescription(role) == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(String.format("Role \"%s\" doesn't exist.", role)).build();
        }

        Transaction txn = datastore.newTransaction();

        try {
            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token);
            Entity tokenEntity = txn.get(tokenKey);

            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning(String.format("Role change of user \"%s\" attempted", targetUsername));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(String.format("Token \"%s\" either isn't a valid token or it has expired.", token))
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
                LOG.warning(String.format("Role change of user \"%s\" attempted", targetUsername));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(String.format("User \"%s\" doesn't exist.", targetUsername))
                        .build();
            }

            String targetUserRole = targetUser.getString("user_role");

            if (!Objects.requireNonNull(Role.fromDescription(userRole))
                    .isRoleAbove(targetUserRole) || !Objects.requireNonNull(Role.fromDescription(userRole))
                    .isRoleAbove(role)) {
                txn.rollback();
                LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(NOT_ENOUGH_PERMISSIONS)
                        .build();
            }

            targetUser = Entity.newBuilder(targetUser).set("user_role", role).build();

            changeRoleInTokens(targetUsername, role, txn);

            txn.put(targetUser);
            txn.commit();
            LOG.info("User \"" + targetUsername + "\" had their role changed from \"" + targetUserRole
                    + "\" to \"" + role + "\" by user: " + username);
            return Response.ok("User \"" + targetUsername + "\" had their role changed to \"" + role + "\" successfully.").build();
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
