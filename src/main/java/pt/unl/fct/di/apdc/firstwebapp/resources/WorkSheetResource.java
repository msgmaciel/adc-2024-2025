package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pt.unl.fct.di.apdc.firstwebapp.enums.AwardStatus;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;
import pt.unl.fct.di.apdc.firstwebapp.enums.WorkStatus;
import pt.unl.fct.di.apdc.firstwebapp.util.WorkSheetData;
import java.util.logging.Logger;

@Path("/worksheet")
public class WorkSheetResource {
    private static final Logger LOG = Logger.getLogger(WorkSheetResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("Token");
    private static final KeyFactory workSheetKeyFactory = datastore.newKeyFactory().setKind("WorkSheet");

    private static final String KEY_AWARD_DATE = "awardDate";
    private static final String KEY_EXPECTED_START_DATE = "expectedStartDate";
    private static final String KEY_EXPECTED_COMPLETION_DATE = "expectedCompletionDate";
    private static final String KEY_ENTITY_ACCOUNT = "entityAccount";
    private static final String KEY_AWARDING_ENTITY = "awardingEntity";
    private static final String KEY_COMPANY_TAX_ID = "companyTaxId";
    private static final String KEY_WORK_STATUS = "workStatus";
    private static final String KEY_NOTES = "notes";
    private static final String KEY_USER_ROLE = "user_role";

    public WorkSheetResource() {}

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createWorkSheet(WorkSheetData data) {
        String token = data.token;
        String workReference = data.workReference;
        String description = data.description;
        String typeOfWorkTarget = data.typeOfWorkTarget;
        String awardStatusDescription = data.awardStatus;

        LOG.info("Attempting to create work sheet: " + workReference);

        Transaction txn = datastore.newTransaction();
        try {
            Key tokenKey = tokenKeyFactory.newKey(token);
            Entity tokenEntity = txn.get(tokenKey);
            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning("Invalid token provided: " + token);
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Invalid token.")
                        .build();
            }

            String userRoleDescription = tokenEntity.getString(KEY_USER_ROLE);
            Role userRole = Role.fromDescription(userRoleDescription);
            if (userRole == null) {
                txn.rollback();
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Invalid user role.")
                        .build();
            }

            if (data.anyEmptyOrBlank(workReference, description, typeOfWorkTarget, awardStatusDescription)) {
                txn.rollback();
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Missing mandatory work sheet attributes (workReference, description, typeOfWorkTarget, awardStatus).")
                        .build();
            }

            AwardStatus awardStatus = AwardStatus.fromDescription(awardStatusDescription);
            if (awardStatus == null) {
                txn.rollback();
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("awardStatus must be either 'awarded' or 'not awarded'.")
                        .build();
            }

            String awardDate = data.awardDate;
            String expectedStartDate = data.expectedStartDate;
            String expectedCompletionDate = data.expectedCompletionDate;
            String entityAccount = data.entityAccount;
            String awardingEntity = data.awardingEntity;
            String companyTaxId = data.companyTaxId;
            String workStatusDescription = data.workStatus;
            String notes = data.notes;

            if (awardStatus == AwardStatus.AWARDED) {
                if (data.anyEmptyOrBlank(awardDate, expectedStartDate, expectedCompletionDate,
                        entityAccount, awardingEntity, companyTaxId, workStatusDescription)) {
                    txn.rollback();
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Awarded work sheet must have additional attributes: awardDate, expectedStartDate, expectedCompletionDate, entityAccount, awardingEntity, companyTaxId, workStatus.")
                            .build();
                }

                if (!userRole.getDescription().equals(Role.BACKOFFICE.getDescription())) {
                    txn.rollback();
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Only users with 'backoffice' role can register awarded work sheets.")
                            .build();
                }

                WorkStatus workStatus = WorkStatus.fromDescription(workStatusDescription);
                if (workStatus == null) {
                    txn.rollback();
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid workStatus. Allowed values are: 'not started', 'in progress', and 'completed'.")
                            .build();
                }
            }

            Key workSheetKey = workSheetKeyFactory.newKey(workReference);
            Entity existingWS = txn.get(workSheetKey);
            if (existingWS != null) {
                txn.rollback();
                return Response.status(Response.Status.CONFLICT)
                        .entity("Work sheet with reference \"" + workReference + "\" already exists.")
                        .build();
            }

            Entity.Builder wsBuilder = Entity.newBuilder(workSheetKey)
                    .set("workReference", workReference)
                    .set("description", description)
                    .set("typeOfWorkTarget", typeOfWorkTarget)
                    .set("awardStatus", awardStatus.getDescription());

            if (awardStatus == AwardStatus.AWARDED) {
                wsBuilder.set(KEY_AWARD_DATE, awardDate)
                        .set(KEY_EXPECTED_START_DATE, expectedStartDate)
                        .set(KEY_EXPECTED_COMPLETION_DATE, expectedCompletionDate)
                        .set(KEY_ENTITY_ACCOUNT, entityAccount)
                        .set(KEY_AWARDING_ENTITY, awardingEntity)
                        .set(KEY_COMPANY_TAX_ID, companyTaxId)
                        .set(KEY_WORK_STATUS, workStatusDescription);
                if (!data.emptyOrBlankField(notes)) {
                    wsBuilder.set(KEY_NOTES, notes);
                }
            }

            txn.put(wsBuilder.build());
            txn.commit();
            LOG.info("Work sheet created successfully: " + workReference);
            return Response.ok("Work sheet \"" + workReference + "\" was created successfully.")
                    .build();
        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.toString())
                    .build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    @POST
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateWorkSheet(WorkSheetData data) {
        String token = data.token;
        String workReference = data.workReference;

        LOG.info("Attempting to update work sheet: " + workReference);

        Transaction txn = datastore.newTransaction();
        try {
            Key tokenKey = tokenKeyFactory.newKey(token);
            Entity tokenEntity = txn.get(tokenKey);
            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning("Invalid token provided: " + token);
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Invalid token.")
                        .build();
            }

            String userRoleDescription = tokenEntity.getString(KEY_USER_ROLE);
            Role userRole = Role.fromDescription(userRoleDescription);
            if (userRole == null) {
                txn.rollback();
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Invalid user role.")
                        .build();
            }
            String username = tokenEntity.getString("username");

            Key workSheetKey = workSheetKeyFactory.newKey(workReference);
            Entity existingWS = txn.get(workSheetKey);
            if (existingWS == null) {
                txn.rollback();
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Work sheet \"" + workReference + "\" not found.")
                        .build();
            }

            Entity.Builder wsBuilder = Entity.newBuilder(existingWS);

            String awardDate = data.awardDate;
            String expectedStartDate = data.expectedStartDate;
            String expectedCompletionDate = data.expectedCompletionDate;
            String entityAccount = data.entityAccount;
            String awardingEntity = data.awardingEntity;
            String companyTaxId = data.companyTaxId;
            String workStatusDescription = data.workStatus;
            String notes = data.notes;

            boolean updatingAwardDetails =
                    (!data.emptyOrBlankField(awardDate)) ||
                            (!data.emptyOrBlankField(expectedStartDate)) ||
                            (!data.emptyOrBlankField(expectedCompletionDate)) ||
                            (!data.emptyOrBlankField(entityAccount)) ||
                            (!data.emptyOrBlankField(awardingEntity)) ||
                            (!data.emptyOrBlankField(companyTaxId));

            boolean updatingWorkStatus =
                    (!data.emptyOrBlankField(workStatusDescription)) ||
                            (!data.emptyOrBlankField(notes));

            if (updatingAwardDetails) {
                if (!userRole.getDescription().equals(Role.BACKOFFICE.getDescription())) {
                    txn.rollback();
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Only users with 'backoffice' role can update awarding attributes.")
                            .build();
                }
                if (!data.emptyOrBlankField(awardDate)) {
                    wsBuilder.set(KEY_AWARD_DATE, awardDate);
                }
                if (!data.emptyOrBlankField(expectedStartDate)) {
                    wsBuilder.set(KEY_EXPECTED_START_DATE, expectedStartDate);
                }
                if (!data.emptyOrBlankField(expectedCompletionDate)) {
                    wsBuilder.set(KEY_EXPECTED_COMPLETION_DATE, expectedCompletionDate);
                }
                if (!data.emptyOrBlankField(entityAccount)) {
                    wsBuilder.set(KEY_ENTITY_ACCOUNT, entityAccount);
                }
                if (!data.emptyOrBlankField(awardingEntity)) {
                    wsBuilder.set(KEY_AWARDING_ENTITY, awardingEntity);
                }
                if (!data.emptyOrBlankField(companyTaxId)) {
                    wsBuilder.set(KEY_COMPANY_TAX_ID, companyTaxId);
                }
            }

            if (updatingWorkStatus) {
                if (userRole.getDescription().equals(Role.PARTNER.getDescription())) {
                    String wsEntityAccount = existingWS.contains(KEY_ENTITY_ACCOUNT) ? existingWS.getString(KEY_ENTITY_ACCOUNT) : "";
                    if (!wsEntityAccount.equals(username)) {
                        txn.rollback();
                        return Response.status(Response.Status.FORBIDDEN)
                                .entity("Partners can only update the work status of their assigned work sheets.")
                                .build();
                    }
                } else if (!userRole.getDescription().equals(Role.BACKOFFICE.getDescription())) {
                    txn.rollback();
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("User doesn't have permission to update work status.")
                            .build();
                }
                if (!data.emptyOrBlankField(workStatusDescription)) {
                    WorkStatus workStatus = WorkStatus.fromDescription(workStatusDescription);
                    if (workStatus == null) {
                        txn.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity("Invalid workStatus. Allowed values are: 'not started', 'in progress', and 'completed'.")
                                .build();
                    }
                    wsBuilder.set(KEY_WORK_STATUS, workStatus.getDescription());
                }
                if (!data.emptyOrBlankField(notes)) {
                    wsBuilder.set(KEY_NOTES, notes);
                }
            }

            txn.put(wsBuilder.build());
            txn.commit();
            LOG.info("Work sheet updated successfully: " + workReference);
            return Response.ok("Work sheet \"" + workReference + "\" was updated successfully.")
                    .build();
        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.toString())
                    .build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}
