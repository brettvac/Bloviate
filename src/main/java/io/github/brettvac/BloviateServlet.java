/*
 * Bloviate Copyright (c) 2017-2026 Brett
 * Licensed under the Apache License, Version 2.0
 */

package io.github.brettvac.bloviate;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class BloviateServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
       {
       response.setContentType("text/html; charset=UTF-8");
       PrintWriter out = response.getWriter();
        
       out.println("<!DOCTYPE html>");
       out.println("<html><head><title>Welcome to Bloviate</title></head><body>");
        
       // Check if we already have OAuth tokens stored
       if (BloviateService.checkForBloggerAuthorization()) 
          {
          // User is authorized: Display the On-Demand Post Button
            out.println("<h1>Welcome Back to Bloviate</h1>");
            out.println("<p>Your application is successfully authenticated with Blogger.</p>");
            out.println("<form method='POST' action='" + request.getContextPath() + "/BloviateServlet'>");
            out.println("<input type='hidden' name='action' value='Bloviate'>");
            out.println("<input type='submit' value='Create Post on Demand'>");
            out.println("</form>");
           } 
       else 
            {
            // Show the First-time setup form
            out.println("<h1>Welcome to Bloviate Setup</h1>");
            out.println("<p>Please provide your Blogger and Google OAuth credentials to get started.</p>");
            
            out.println("<form method='POST' action='" + request.getContextPath() + "/BloviateServlet'>");
            
            out.println("<label for='blogId'>Blog ID:</label><br>");
            out.println("<input type='text' id='blogId' name='blogId' required pattern='[0-9]+' title='Blog ID must be numeric'><br><br>");
            
            out.println("<label for='clientId'>OAuth Client ID:</label><br>");
            out.println("<input type='text' id='clientId' name='clientId' required><br><br>");
            
            out.println("<label for='clientSecret'>OAuth Client Secret:</label><br>");
            out.println("<input type='password' id='clientSecret' name='clientSecret' required><br><br>");
            
            out.println("<input type='submit' value='Start The Authentification Process'>");
            out.println("</form>");

            }
        out.println("</body></html>");
       }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
       {

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();

    // Handle on-demand posting
    String action = request.getParameter("action");
    if ("Bloviate".equals(action)) 
      {
      try {

            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            Entity oauthTokenEntity = datastore.get(KeyFactory.createKey("OAuthTokenEntity", "OA"));

            String accessToken = oauthTokenEntity.getProperty("OAuthAccessToken").toString();
            String refreshToken = oauthTokenEntity.getProperty("OAuthRefreshToken").toString();

            out.println("<!DOCTYPE html><html><head><title>Posting to Blogger</title></head><body>");

            Map<String, Object> result = BloviateService.postToBlogger(accessToken, refreshToken);

            out.println("<h1>Blog Post Published Successfully</h1>");

            out.println("<p><strong>Blog ID:</strong> " + result.get("blogId") + "</p>");
            out.println("<p><strong>Published:</strong> " + result.get("published") + "</p>");

            out.println("<hr>");
            out.println("<h2>" + result.get("title") + "</h2>");
            out.println("<div>" + result.get("content") + "</div>");

            out.println("<p><strong>Post URL:</strong> <a href='" + result.get("url") + "'>" + result.get("url") + "</a></p>");

            out.println("<p><a href='" + request.getContextPath() + "/BloviateServlet'>Return to dashboard</a></p>");

            out.println("</body></html>");

      } catch (EntityNotFoundException e) 
         {
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

        try {
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

            Entity blogEntity = new Entity("BloggerIDEntity", "BI");

            blogEntity.setProperty("BLOG_ID", blogId);
            blogEntity.setProperty("CLIENT_ID", clientId);
            blogEntity.setProperty("CLIENT_SECRET", clientSecret);

            datastore.put(blogEntity);

        } catch (Exception e) {
            throw new ServletException("Failed to store Blogger configuration in Datastore", e);
        }

       boolean flowStarted = BloviateService.authorizeBloviate(clientId, clientSecret, request, response);

      if (!flowStarted) 
        {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to initialize the OAuth flow.");
        }
      }
 
}