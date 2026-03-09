/*
 * Copyright (c) 2026 Brett Vachon
 * Licensed under the Apache License, Version 2.0
 * See the LICENSE file in the project root for details.
 */

package io.github.brettvac.bloviate;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class BloviateServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        
        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Bloviate Setup</title></head><body>");
        out.println("<h1>Welcome to Bloviate Setup</h1>");
        
        // Check if we already have OAuth tokens stored
        if (BloviateService.checkForBloggerAuthorization()) {
            
            // User is authorized: Display the On-Demand Post Button
            out.println("<p>Your application is successfully authenticated with Blogger.</p>");
            out.println("<form method='POST' action='" + request.getContextPath() + "/BloviateServlet'>");
            out.println("<input type='hidden' name='action' value='postNow'>");
            out.println("<input type='submit' value='Create Post on Demand'>");
            out.println("</form>");
            
        } else {
            // Show the First-time setup form 
            generateSetupForm(request, out);
        }
        out.println("</body></html>");
    }

    /**
     * Helper function to generate the first-time setup form.
     */
    private void generateSetupForm(HttpServletRequest request, PrintWriter out) {

        out.println("<p>Please provide your Blogger and Google OAuth credentials to get started.</p>");
        
        out.println("<form method='POST' action='" + request.getContextPath() + "/BloviateServlet'>");
        
        out.println("<p><em>Your Blog ID is the unique numeric string found in the URL when viewing your blog in the Blogger dashboard.</em></p>");
        out.println("<label for='blogId'>Blog ID:</label><br>");
        out.println("<input type='text' id='blogId' name='blogId' required pattern='[0-9]+' title='Blog ID must be numeric'><br><br>");
        
        out.println("<p><em>Your OAuth Client ID acts as your application's public identifier. You can generate this in the Google Cloud Console under APIs & Services > Credentials.</em></p>");
        out.println("<label for='clientId'>OAuth Client ID:</label><br>");
        out.println("<input type='text' id='clientId' name='clientId' required><br><br>");
        
        out.println("<p><em>Your OAuth Client Secret is the confidential key paired with your Client ID. Keep this secure and do not share it. Found in the Google Cloud Console.</em></p>");
        out.println("<label for='clientSecret'>OAuth Client Secret:</label><br>");
        out.println("<input type='password' id='clientSecret' name='clientSecret' required><br><br>");
        
        out.println("<input type='submit' value='Start The Authentification Process'>");
        out.println("</form>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    response.setContentType("text/html");
    response.setCharacterEncoding("UTF-8");
    PrintWriter out = response.getWriter();

    // Handle on-demand posting
    if ("postNow".equals(request.getParameter("action"))) {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        try {

            Entity oauthTokenEntity = datastore.get(KeyFactory.createKey("OAuthTokenEntity", "OA"));

            String accessToken = oauthTokenEntity.getProperty("OAuthAccessToken").toString();
            String refreshToken = oauthTokenEntity.getProperty("OAuthRefreshToken").toString();

            out.println("<!DOCTYPE html><html><head><title>Posting to Blogger</title></head><body>");

            BloviateService.postToBlogger(accessToken, refreshToken, out);

            out.println("<p><a href='" + request.getContextPath() + "/BloviateServlet'>Return to dashboard</a></p>");
            out.println("</body></html>");

        } catch (EntityNotFoundException e) {

            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "OAuth tokens not found in Datastore. Please re-authenticate.");
        }

        return;
    }

    // Default: Handle setup form submission
    String blogId = request.getParameter("blogId");
    String clientId = request.getParameter("clientId");
    String clientSecret = request.getParameter("clientSecret");

    // Minimal server validation (still required)
    if (blogId == null || clientId == null || clientSecret == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields.");
        return;
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    Entity blogEntity = new Entity("BloggerIDEntity", "BI");

    blogEntity.setProperty("BLOG_ID", blogId);
    blogEntity.setProperty("CLIENT_ID", clientId);
    blogEntity.setProperty("CLIENT_SECRET", clientSecret);

    datastore.put(blogEntity);

    boolean flowStarted = BloviateService.authorizeBloviate(clientId, clientSecret, request, response);

    if (!flowStarted) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Failed to initialize the OAuth flow.");
    }
}
}