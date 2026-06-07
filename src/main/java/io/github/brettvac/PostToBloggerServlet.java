package io.github.brettvac.bloviate;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public class PostToBloggerServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(PostToBloggerServlet.class.getName());

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("text/plain; charset=UTF-8");

        try {

            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

            Entity oauthTokenEntity = datastore.get(
                    KeyFactory.createKey("OAuthTokenEntity", "OA")
            );

            Map<String, Object> result = BloviateService.postToBlogger(
                    (String) oauthTokenEntity.getProperty("OAuthAccessToken"),
                    (String) oauthTokenEntity.getProperty("OAuthRefreshToken")
            );

            log.info("Bloviate cron post success | " + "Blog ID=" + result.get("blogId") + " | " + "Title=" + result.get("title") + " | " + "URL=" + result.get("url"));

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("OK");

        } catch (EntityNotFoundException e) {

            log.log(Level.SEVERE,
                    "OAuth tokens not found in Datastore. User must re-authenticate.", e);

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().println("OK");
        }
    }
}
