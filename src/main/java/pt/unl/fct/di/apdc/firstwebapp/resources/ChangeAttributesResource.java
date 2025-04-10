package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import org.apache.commons.codec.digest.DigestUtils;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeAttributesData;

import java.util.logging.Logger;

@Path("/change-attributes")
public class ChangeAttributesResource {

    private static final String ATTEMPTED_ATTRIBUTE_CHANGE = "Attempted attribute change of user: %s";
    private static final String NON_PERMITTED_ATTEMPT = "User \"%s\" attempted a non-permitted attribute change.";
    private static final String ROLE_INVALID_ATTEMPT = "User \"%s\" attempted to change role with an invalid value: \"%s\".";
    private static final String STATE_INVALID_ATTEMPT = "User \"%s\" attempted to change state with an invalid value: \"%s\".";

    private static final Logger LOG = Logger.getLogger(ChangeAttributesResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    public ChangeAttributesResource() {}

    private void changeRoleInTokens(String username, String role, Transaction txn) {
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Token")
                .setFilter(StructuredQuery.PropertyFilter.eq("username", username))
                .build();
        QueryResults<Entity> tokenResults = txn.run(query);

        while (tokenResults.hasNext()) {
            Entity tokenEntity = tokenResults.next();
            txn.put(Entity.newBuilder(tokenEntity)
                    .set("user_role", role)
                    .build());
        }
    }

    private void changeUsernameInTokens(String oldUsername, String newUsername, Transaction txn) {
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Token")
                .setFilter(StructuredQuery.PropertyFilter.eq("username", oldUsername))
                .build();
        QueryResults<Entity> tokenResults = txn.run(query);

        while (tokenResults.hasNext()) {
            Entity tokenEntity = tokenResults.next();
            txn.put(Entity.newBuilder(tokenEntity)
                    .set("username", newUsername)
                    .build());
        }
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeAttributes(ChangeAttributesData data) {
        Transaction txn = datastore.newTransaction();

        try {
            String token = data.token;
            String targetUsername = data.targetUsername;

            LOG.info(String.format("There was an attempt to change the attributes of user \"%s\" with token: %s", targetUsername, token));

            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token);
            Entity tokenEntity = txn.get(tokenKey);

            if (tokenEntity == null) {
                txn.rollback();
                LOG.info(String.format(ATTEMPTED_ATTRIBUTE_CHANGE, targetUsername));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(String.format("Token \"%s\" either isn't a valid token or it has expired.", token))
                        .build();
            }

            String role = tokenEntity.getString("user_role");
            String username = tokenEntity.getString("username");

            if (Role.PARTNER.getDescription().equals(role)) {
                txn.rollback();
                LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("The user that's associated with the token provided doesn't have the needed permissions to perform this operation.")
                        .build();
            }

            Key targetUserKey = datastore.newKeyFactory().setKind("User").newKey(targetUsername);
            Entity targetUser = txn.get(targetUserKey);

            if (targetUser == null) {
                txn.rollback();
                LOG.warning(String.format(ATTEMPTED_ATTRIBUTE_CHANGE, targetUsername));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(String.format("User \"%s\" doesn't exist.", targetUsername))
                        .build();
            }

            String inputUsername = data.username;
            String inputEmail = data.email;
            String inputName = data.name;
            String inputRole = data.role;
            String inputState = data.state;

            Entity.Builder userBuilder = Entity.newBuilder(targetUser);

            if (Role.ENDUSER.getDescription().equals(role)) {
                if (!username.equals(data.targetUsername)) {
                    txn.rollback();
                    LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Users with \"ENDUSER\" role can only change the attributes of their own user account.")
                            .build();
                }

                if (!data.emptyOrBlankField(inputUsername) ||
                        !data.emptyOrBlankField(inputEmail) ||
                        !data.emptyOrBlankField(inputName)) {
                    LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                    txn.rollback();
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Users with \"ENDUSER\" role can't change their username, email or name attributes.")
                            .build();
                }

            } else if (Role.BACKOFFICE.getDescription().equals(role)) {
                String targetRole = targetUser.getString("user_role");
                if (!(Role.BACKOFFICE.isRoleAbove(targetRole))) {
                    LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                    txn.rollback();
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Users with \"BACKOFFICE\" role can only change the attributes of users that have either the role \"ENDUSER\" or \"PARTNER\".")
                            .build();
                }

                if (!data.emptyOrBlankField(inputUsername) ||
                        !data.emptyOrBlankField(inputEmail)) {
                    LOG.warning(String.format(NON_PERMITTED_ATTEMPT, username));
                    txn.rollback();
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Users with \"BACKOFFICE\" role can't change username or email attributes.")
                            .build();
                }

                if (!data.emptyOrBlankField(inputName))
                    userBuilder.set("user_name", inputName);

                if (!data.emptyOrBlankField(inputRole)) {
                    if (data.isRoleInvalid()) {
                        LOG.warning(String.format(ROLE_INVALID_ATTEMPT, username, inputRole));
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(ChangeAttributesData.INVALID_ROLE)
                                .build();
                    }

                    userBuilder.set("user_role", inputRole);
                    changeRoleInTokens(targetUsername, inputRole, txn);
                }
                if (!data.emptyOrBlankField(inputState)) {
                    if (data.isStateInvalid()) {
                        LOG.warning(String.format(STATE_INVALID_ATTEMPT, username, inputState));
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(ChangeAttributesData.INVALID_STATE)
                                .build();
                    }

                    userBuilder.set("user_state", inputState);
                }
            }

            String inputPassword = data.password;
            String inputPhone = data.phone;
            String inputPrivacy = data.privacy;
            String inputAddress = data.address;
            String inputId = data.id;
            String inputFId = data.financialId;
            String inputEmployer = data.employer;
            String inputFunction = data.function;
            String inputEmployerFId = data.employerFinancialId;

            if (!data.emptyOrBlankField(inputPassword)) {

                if (!inputPassword.equals(data.confirmation)) {
                    LOG.warning(String.format("User \"%s\" attempted to change password but confirmation did not match.", username));
                    txn.rollback();
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ChangeAttributesData.EQUAL_TO_CONFIRMATION)
                            .build();
                }

                if (data.isPasswordInvalid()) {
                    LOG.warning(String.format("User \"%s\" attempted to change password with an invalid value.", username));
                    txn.rollback();
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ChangeAttributesData.INVALID_PASSWORD)
                            .build();
                }
                userBuilder.set("user_pwd", DigestUtils.sha512Hex(inputPassword));
            }
            if (!data.emptyOrBlankField(inputPhone)) {
                if (data.isPhoneInvalid()) {
                    LOG.warning(String.format("User \"%s\" attempted to change phone number with an invalid value: \"%s\".", username, inputPhone));
                    txn.rollback();
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ChangeAttributesData.INVALID_PHONE_NUMBER)
                            .build();
                }
                userBuilder.set("user_phone", inputPhone);
            }
            if (!data.emptyOrBlankField(inputPrivacy)) {
                if (data.isPrivacyInvalid()) {
                    LOG.warning(String.format("User \"%s\" attempted to change privacy setting with an invalid value: \"%s\".", username, inputPrivacy));
                    txn.rollback();
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ChangeAttributesData.INVALID_PRIVACY)
                            .build();
                }
                userBuilder.set("user_privacy", inputPrivacy);
            }
            if (!data.emptyOrBlankField(inputAddress)) {
                userBuilder.set("user_address", inputAddress);
            }

            if (!data.emptyOrBlankField(inputId)) {
                userBuilder.set("user_id", inputId);
            }
            if (!data.emptyOrBlankField(inputFId)) {
                userBuilder.set("user_financialId", inputFId);
            }
            if (!data.emptyOrBlankField(inputEmployer)) {
                userBuilder.set("user_employer", inputEmployer);
            }
            if (!data.emptyOrBlankField(inputFunction)) {
                userBuilder.set("user_function", inputFunction);
            }
            if (!data.emptyOrBlankField(inputEmployerFId)) {
                userBuilder.set("user_employerFId", inputEmployerFId);
            }

            if (Role.ADMIN.getDescription().equals(role)) {
                if (!data.emptyOrBlankField(inputEmail)) {
                    if (data.isEmailInvalid()) {
                        LOG.warning(String
                                .format("User \"%s\" attempted to change email of \"%s\" with an invalid value: \"%s\".",
                                        username, targetUsername, inputEmail));
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(ChangeAttributesData.INVALID_EMAIL)
                                .build();
                    }

                    Query<Entity> emailQuery = Query.newEntityQueryBuilder()
                            .setKind("User")
                            .setFilter(StructuredQuery.PropertyFilter.eq("user_email", inputEmail))
                            .build();
                    QueryResults<Entity> emailResults = txn.run(emailQuery);
                    boolean emailExists = false;
                    while (emailResults.hasNext()) {
                        Entity entity = emailResults.next();
                        if (!entity.getKey().getName().equals(targetUsername)) {
                            emailExists = true;
                            break;
                        }
                    }
                    if (emailExists) {
                        LOG.warning(String
                                .format("User \"%s\" attempted to change email of \"%s\" to an email already in use: \"%s\".",
                                        username, targetUsername, inputEmail));
                        txn.rollback();
                        return Response.status(Response.Status.CONFLICT)
                                .entity("Email already in use.")
                                .build();
                    }
                    userBuilder.set("user_email", inputEmail);
                }
                if (!data.emptyOrBlankField(inputName)) {
                    userBuilder.set("user_name", inputName);
                }
                if (!data.emptyOrBlankField(inputRole)) {
                    if (data.isRoleInvalid()) {
                        LOG.warning(String.format(ROLE_INVALID_ATTEMPT, username, inputRole));
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(ChangeAttributesData.INVALID_ROLE)
                                .build();
                    }

                    userBuilder.set("user_role", inputRole);
                }
                if (!data.emptyOrBlankField(inputState)) {
                    if (data.isStateInvalid()) {
                        LOG.warning(String.format(STATE_INVALID_ATTEMPT, username, inputState));
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(ChangeAttributesData.INVALID_STATE)
                                .build();
                    }

                    userBuilder.set("user_state", inputState);
                }

                if (!data.emptyOrBlankField(inputUsername)) {
                    if (data.doesUsernameHaveAt()) {
                        LOG.warning(String
                                .format("User \"%s\" attempted to change username of \"%s\" to an invalid username: \"%s\".",
                                        username, targetUsername, inputUsername));
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(ChangeAttributesData.USERNAME_NOT_CONTAIN_AT)
                                .build();
                    }

                    Key newUserKey = datastore.newKeyFactory().setKind("User").newKey(inputUsername);
                    Entity existingUserWithUsername = txn.get(newUserKey);
                    if (existingUserWithUsername != null) {
                        LOG.warning(String
                                .format("User \"%s\" attempted to change username of \"%s\" to an already existing username: \"%s\".",
                                        username, targetUsername, inputUsername));
                        txn.rollback();
                        return Response.status(Response.Status.CONFLICT)
                                .entity("Username already exists.")
                                .build();
                    }

                    userBuilder.set("username", inputUsername);
                    Entity updatedUser = userBuilder.build();
                    Entity newUser = Entity.newBuilder(newUserKey, updatedUser).build();
                    txn.put(newUser);
                    txn.delete(targetUserKey);


                    changeUsernameInTokens(targetUsername, inputUsername, txn);
                    txn.commit();

                    LOG.info(String.format("The attributes of account \"%s\" were successfully changed by user \"%s\".",
                            targetUsername, username));
                    return Response.ok("Attributes successfully updated.").build();
                }
            }

            txn.put(userBuilder.build());
            txn.commit();
            LOG.info(String.format("The attributes of account \"%s\" were successfully changed by user \"%s\".",
                    targetUsername, username));
            return Response.ok("Attributes successfully updated.").build();
        } catch (Exception e) {
            if (txn.isActive())
                txn.rollback();
            LOG.severe("Error updating attributes: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.toString()).build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }
}
