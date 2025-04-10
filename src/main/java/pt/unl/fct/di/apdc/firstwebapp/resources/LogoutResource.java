package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.Transaction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/logout")
public class LogoutResource {

    private static final Logger LOG = Logger.getLogger(LogoutResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogout(String tokenJson) {
        JsonObject jsonObject = JsonParser.parseString(tokenJson).getAsJsonObject();

        String token = jsonObject.get("token").getAsString();

        Transaction txn = datastore.newTransaction();
        try {
            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token);
            Entity tokenEntity = txn.get(tokenKey);

            if (tokenEntity == null) {
                txn.rollback();
                LOG.warning(String.format("Failed attempt to logout with token: %s", token));
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Token \"" + token + "\" either isn't a valid token or it has expired.")
                        .build();
            }

            String username = tokenEntity.getString("username");

            Query<Entity> tokenQuery = Query.newEntityQueryBuilder()
                    .setKind("Token")
                    .setFilter(PropertyFilter.eq("username", username))
                    .build();
            QueryResults<Entity> tokens = txn.run(tokenQuery);

            while (tokens.hasNext()) {
                Entity tEntity = tokens.next();
                txn.delete(tEntity.getKey());
            }

            txn.commit();
            LOG.info("Logout successful for user: " + username);
            return Response.ok("User \"" + username + "\" has been successfully logged out.").build();
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
