package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import org.apache.commons.codec.digest.DigestUtils;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.util.ChangePasswordData;

import java.util.logging.Logger;

@Path("/change-password")
public class ChangePasswordResource {

    private static final Logger LOG = Logger.getLogger(ChangePasswordResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    private static final String ATTEMPT_PASSWORD_CHANGE = "Attempted password change using token: %s";
    private static final String INVALID_TOKEN_MSG = "Token \"%s\" is either invalid or has expired.";
    private static final String USER_NOT_FOUND_MSG = "User \"%s\" does not exist.";
    private static final String INCORRECT_CURRENT_PWD = "User \"%s\" provided an incorrect current password.";
    private static final String PASSWORD_UPDATED_MSG = "User \"%s\" changed password successfully.";

    public ChangePasswordResource() {}

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changePassword(ChangePasswordData data) {
        String validationMsg = data.validate();

        if (!validationMsg.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(validationMsg).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            LOG.info(String.format(ATTEMPT_PASSWORD_CHANGE, data.token));

            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(data.token);
            Entity tokenEntity = txn.get(tokenKey);
            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning(String.format(INVALID_TOKEN_MSG, data.token));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(String.format("Token \"%s\" is either invalid or has expired.", data.token))
                        .build();
            }

            String username = tokenEntity.getString("username");

            Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity userEntity = txn.get(userKey);
            if (userEntity == null) {
                txn.rollback();
                LOG.warning(String.format(USER_NOT_FOUND_MSG, username));
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(String.format("User \"%s\" does not exist.", username))
                        .build();
            }

            String storedPasswordHash = userEntity.getString("user_pwd");
            String hashedCurrentPassword = DigestUtils.sha512Hex(data.currentPassword);
            if (!storedPasswordHash.equals(hashedCurrentPassword)) {
                txn.rollback();
                LOG.warning(String.format(INCORRECT_CURRENT_PWD, username));
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Current password is incorrect.")
                        .build();
            }

            String newHashedPassword = DigestUtils.sha512Hex(data.newPassword);
            Entity updatedUser = Entity.newBuilder(userEntity)
                    .set("user_pwd", newHashedPassword)
                    .build();

            txn.put(updatedUser);
            txn.commit();
            LOG.info(String.format(PASSWORD_UPDATED_MSG, username));
            return Response.ok("Password updated successfully.").build();
        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.severe("Error updating password for user: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.toString()).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}
