package pt.unl.fct.di.apdc.firstwebapp.initializers;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import pt.unl.fct.di.apdc.firstwebapp.enums.AccountState;
import pt.unl.fct.di.apdc.firstwebapp.enums.Privacy;
import pt.unl.fct.di.apdc.firstwebapp.enums.Role;

import java.util.logging.Logger;

@WebListener
public class RootInitializer implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(RootInitializer.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey("root");
            Entity user = txn.get(userKey);
            if (user != null) {
                txn.rollback();
                LOG.info("\"root\" user already exists, therefore the user wasn't created when the app was deployed");
            } else {
                Entity.Builder userBuilder = Entity.newBuilder(userKey).set("username", "root")
                        .set("user_name", "Miguel da Silva Garanito Maciel")
                        .set("user_pwd", DigestUtils.sha512Hex(".AaBbCc123"))
                        .set("user_email", "ms.maciel@campus.fct.unl.pt")
                        .set("user_phone", "+351968916419")
                        .set("user_privacy", Privacy.PRIVATE.getDescription())
                        .set("user_role", Role.ADMIN.getDescription())
                        .set("user_state", AccountState.ACTIVE.getDescription())
                        .set("user_creation_time", Timestamp.now());

                txn.put(userBuilder.build());
                txn.commit();
                LOG.info("\"root\" user registered");
            }
        }
        catch (DatastoreException e) {
            LOG.info("DatastoreException caught during \"root\" user registration");
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

}
