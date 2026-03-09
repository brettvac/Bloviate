/*
 * Copyright (c) 2026 Brett Vachon
 * Licensed under the Apache License, Version 2.0
 * See the LICENSE file in the project root for details.
 */

package io.github.brettvac.bloviate;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet handling the OAuth callback from the authentication service. We are
 * retrieving the OAuth code, then exchanging it for a refresh and an access
 * token and saving it.
 */
@SuppressWarnings("serial")

public class CallbackHandlerServlet extends HttpServlet
   {

   /** The name of the OAuth error URL parameter */
   public static final String ERROR_URL_PARAM_NAME = "error";
   /** The name of the OAuth code URL parameter */
   public static final String CODE_URL_PARAM_NAME = "code";
   /** The URL suffix of the OAuth Callback handler servlet */
   public static final String URL_MAPPING = "/oauth2callback";
   /** The URL suffix of the Blogger servlet */
   public static final String REDIRECT_URI = "/BloviateServlet";
    
   public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
      {
      // Check for no errors in the OAuth process
      String[] error = request.getParameterValues(ERROR_URL_PARAM_NAME);
      if (error != null && error.length > 0) 
        {
        response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "OAuth Error: " + error[0]);        return;
        }

      // Check for the presence of the response code
      String[] code = request.getParameterValues(CODE_URL_PARAM_NAME);
      
      if (code == null || code.length == 0) 
          {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing authorization code");
          return;
          }
             
      //Use code to generate a Redirect URI
      if (BloviateService.flow == null) 
         {
         response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OAuth flow uninitialized. Please start from the main page.");
         return;
         }
       
      String redirectUri = getOAuthCodeCallbackHandlerUrl(request);
      GoogleTokenResponse tokenResponse = BloviateService.flow.newTokenRequest(code[0]).setRedirectUri(redirectUri).execute();
          
      //Store the token
      DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();    
      Entity OAuthTokenEntity = new Entity("OAuthTokenEntity","OA"); // "OA" acts as a static key for a single-user app
       
      OAuthTokenEntity.setProperty("OAuthAccessToken", tokenResponse.getAccessToken());
       
      // Google only returns a refresh token on the first authorization prompt
      OAuthTokenEntity.setProperty("OAuthRefreshToken",tokenResponse.getRefreshToken());
       
      datastore.put(OAuthTokenEntity);
       
      // Redirect back to the main servlet
      response.sendRedirect(REDIRECT_URI);
      }

    /**
     * Helper to reconstruct the exact callback URL.
     */
    public static String getOAuthCodeCallbackHandlerUrl(HttpServletRequest request) 
       {
       StringBuilder oauthURL = new StringBuilder();
       oauthURL.append(request.getScheme() + "://");
       oauthURL.append(request.getServerName());
       int port = request.getServerPort();
       
       // Ignore default HTTP (80) and HTTPS (443) ports
        if (port != 80 && port != 443) {
            oauthURL.append(":").append(port);
        }
       
       oauthURL.append(request.getContextPath());
       oauthURL.append(URL_MAPPING);
       oauthURL.append(request.getPathInfo() == null ? "" : request.getPathInfo());
         
       return oauthURL.toString(); 
       }
    
   }
