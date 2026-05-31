/*
 * Bloviate Copyright (c) 2017-2026 Brett
 * Licensed under the Apache License, Version 2.0
 */

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

/**
 * Servlet getting an Access and Refresh Token from the Callback Handler Servlet (if needed)
 * and using those credentials to post to Blogger
 */  

@SuppressWarnings("serial")
public class PostToBloggerServlet extends HttpServlet {
  
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {         
        
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        try {
            //Retrieve the saved OAuth tokens from Datastore
            Entity oauthTokenEntity = datastore.get(KeyFactory.createKey("OAuthTokenEntity", "OA"));
            
            String accessToken = (String) oauthTokenEntity.getProperty("OAuthAccessToken");
            String refreshToken = (String) oauthTokenEntity.getProperty("OAuthRefreshToken");
       
            //Pass the retrieved tokens to the Service class which contains the business logic
            BloviateService.postToBlogger(accessToken, refreshToken, response.getWriter());
            
        } catch (EntityNotFoundException e) {
            // Handle the case where the Cron job fires but the user hasn't authenticated yet
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().append("Error: OAuth tokens not found in Datastore. Please run the setup in the main BloviateServlet first.");
        }
        
    }

}