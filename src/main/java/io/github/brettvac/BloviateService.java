/*
 * Copyright (c) 2026 Brett Vachon
 * Licensed under the Apache License, Version 2.0
 * See the LICENSE file in the project root for details.
 */

package io.github.brettvac.bloviate;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import com.google.api.services.blogger.Blogger;
import com.google.api.services.blogger.Blogger.Posts.Insert;
import com.google.api.services.blogger.model.Post;
import com.google.api.services.blogger.BloggerScopes;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class BloviateService {

    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    public static GoogleAuthorizationCodeFlow flow;

    /**
     * Checks if we already have a refresh token stored in Datastore.
     * Returns true if tokens exist, false otherwise.
     */
    public static boolean checkForBloggerAuthorization() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity oauthTokenEntity;

        try {
            oauthTokenEntity = datastore.get(KeyFactory.createKey("OAuthTokenEntity", "OA"));

            String accessToken = oauthTokenEntity.getProperty("OAuthAccessToken").toString();
            String refreshToken = oauthTokenEntity.getProperty("OAuthRefreshToken").toString();

            if (accessToken != null && refreshToken != null) {
                return true;
            }
            return false;

        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    /**
     * Initializes the OAuth flow and redirects the user to Google's consent screen.
     */
    public static boolean authorizeBloviate(String clientId, String clientSecret, HttpServletRequest request, HttpServletResponse response) {
        
        try {
            // Checking if we already have tokens 
            Collection<String> scopes = Arrays.asList(BloggerScopes.BLOGGER);

            flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT,JSON_FACTORY,clientId,clientSecret,scopes)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            String redirectUri = CallbackHandlerServlet.getOAuthCodeCallbackHandlerUrl(request);
            String url = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();

            response.sendRedirect(url);
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Builds a new Google Credential with the Access and Refresh Tokens and
     * posts to Blogger.
     */
    public static void postToBlogger(String OAuthAccessToken, String OAuthRefreshToken, java.io.Writer output) throws IOException {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        String appName = System.getenv("APP_NAME");

        String blogId = "";
        String clientId = "";
        String clientSecret = "";

        try {
            // Retrieve required values from the created Blogger Datastore entity
            Entity blogEntity = datastore.get(KeyFactory.createKey("BloggerIDEntity", "BI"));
            blogId = (String) blogEntity.getProperty("BLOG_ID");
            clientId = (String) blogEntity.getProperty("CLIENT_ID");
            clientSecret = (String) blogEntity.getProperty("CLIENT_SECRET");

        } catch (EntityNotFoundException e) {
            output.append("Error: Blog ID or Credentials not found in Datastore.");
            return;
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setAccessToken(OAuthAccessToken)
                .setRefreshToken(OAuthRefreshToken);

        Blogger blog = new Blogger.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                credential)
                .setApplicationName(appName)
                .build();

        Post content = new Post();
        
        //Retrieve a random line from our content file
        String line = PostToBloggerServlet.getLine();
 
        //Set the title as the first sentence of the line
        content.setTitle(line.substring(0, line.indexOf('.')).trim());
        
        //Rest of the line goes into the contents
        content.setContent(line.substring(line.indexOf('.') + 1).trim());

        Insert postsInsertAction = blog.posts().insert(blogId, content);
        postsInsertAction.setFields("content,published,title");

        Post post = postsInsertAction.execute();

        output.append("Successfully posted to blog: ").append(blogId).append("\n");
        output.append("Published at: ").append(post.getPublished().toString()).append("\n");
        output.append("Content:<br>").append(post.getContent()).append("<br>");
    }
}