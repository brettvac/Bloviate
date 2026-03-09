/*
 * Copyright (c) 2026 Brett Vachon
 * Licensed under the Apache License, Version 2.0
 * See the LICENSE file in the project root for details.
 */

package io.github.brettvac.bloviate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.Scanner;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;

/**
 * Servlet getting an Access and Refresh Token from the Callback Handler Servlet (if needed)
 * and using those credentials to post to Blogger
 */  

@SuppressWarnings("serial")
public class PostToBloggerServlet extends HttpServlet {
  
    public static final String file = "WEB-INF/StaticFiles/bloviate.txt";  //Static file containing the blog post content

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
    
    /**
     * Helper function to return a random line from a file of unknown file length using a reservoir sampling algorithm
     * @return randline Random line from file
     */
    public static String getLine() throws IOException {
        int numlines = 1;
        String randline = "";
        Random rand = new Random();

        // Using FileInputStream combined with a try-with-resources block to automatically close the stream and scanner to prevent memory leaks.
        try (InputStream stream = new FileInputStream(file);
             Scanner input = new Scanner(stream, "UTF-8")) {
             
            while (input.hasNextLine()) {
                String buf = input.nextLine();

                if (rand.nextInt(numlines) == 0) {
                    randline = buf; // probability of choosing line 1/n
                }

                numlines++;
            }
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("File not found: " + file);
        }

        return randline;
    }
}