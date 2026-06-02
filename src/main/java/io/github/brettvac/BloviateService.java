/*
 * Bloviate Copyright (c) 2017-2026 Brett
 * Licensed under the Apache License, Version 2.0
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

import java.util.Iterator;
import java.util.Random;
import java.util.stream.Stream;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class BloviateService {
    
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    public static GoogleAuthorizationCodeFlow flow;

     /**
      * Checks whether the stored Blogger OAuth credentials are valid.
      * Returns true only if the refresh token can successfully refresh
      * the access token.
      */
    public static boolean checkForBloggerAuthorization() {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        try {

            // OAuth tokens from the datastore entity
            Entity oauthTokenEntity = datastore.get(KeyFactory.createKey("OAuthTokenEntity", "OA"));

            String accessToken = (String) oauthTokenEntity.getProperty("OAuthAccessToken");
            String refreshToken = (String) oauthTokenEntity.getProperty("OAuthRefreshToken");

            if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()) {
                return false;
            }

            Entity blogEntity = datastore.get(KeyFactory.createKey("BloggerIDEntity", "BI"));

            String clientId = (String) blogEntity.getProperty("CLIENT_ID");
            String clientSecret = (String) blogEntity.getProperty("CLIENT_SECRET");

            if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
                return false;  // Empty values
            }

            GoogleCredential credential =
                    new GoogleCredential.Builder()
                            .setTransport(HTTP_TRANSPORT)
                            .setJsonFactory(JSON_FACTORY)
                            .setClientSecrets(clientId, clientSecret)
                            .build()
                            .setAccessToken(accessToken)
                            .setRefreshToken(refreshToken);

            // Actually test credential validity
            return credential.refreshToken();

        } catch (EntityNotFoundException e) {
            return false;

        } catch (IOException e) {
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
    public static Map<String, Object> postToBlogger(String OAuthAccessToken, String OAuthRefreshToken) throws IOException {

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
             throw new IOException("Missing Blogger configuration (BloggerIDEntity/BI)", e);

        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setAccessToken(OAuthAccessToken)
                .setRefreshToken(OAuthRefreshToken);

        if (!credential.refreshToken()) {
            throw new IOException("Failed to refresh OAuth token.");
        }
        
        Blogger blog = new Blogger.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                credential)
                .setApplicationName(appName)
                .build();

        Post content = createPost();
        
        Insert postsInsertAction = blog.posts().insert(blogId, content);
        postsInsertAction.setFields("id,url,title,content,published");

        Post post = postsInsertAction.execute();

        Map<String, Object> result = new HashMap<>();

        result.put("blogId", blogId);
        result.put("title", post.getTitle());
        result.put("content", post.getContent());
        result.put("url", post.getUrl());
        result.put("published", post.getPublished());

        return result;
    }
    
    /**
     * Helper function to return a random line from a file of unknown file length using a reservoir sampling algorithm
     * @return randline Random line from file
     */
    private static String getLine() throws IOException {
      
      String file = System.getenv("BLOVIATE_WORDS");
      int numLines = 1;
      String randLine = "";
      Random rand = new Random();

      try (Stream<String> lines = Files.lines(Path.of(file))) {
         Iterator<String> it = lines.iterator();

         while (it.hasNext()) {
            String buf = it.next();

            if (rand.nextInt(numLines++) == 0) {
                randLine = buf;
            }
         }

      } catch (NoSuchFileException e) {
          throw new FileNotFoundException("File not found: " + file);
      }

      return randLine;
    }
    
    private static Post createPost() throws IOException {

        String apiKey = System.getenv("HARSH_WORDNIK_API_KEY");
        
        final int limit = 50;
        final int maxRetries = 10;
        int retries = 0;

        while (true) {

            String seedWord = getLine();

            if (seedWord == null || seedWord.trim().isEmpty()) {
                throw new IOException("Unable to retrieve seed word.");
            }

            String urlString =
                    "https://api.wordnik.com/v4/word.json/"
                            + URLEncoder.encode(seedWord, StandardCharsets.UTF_8)
                            + "/examples?limit="
                            + limit
                            + "&api_key="
                            + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(urlString).openConnection();

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");

            int code = connection.getResponseCode();

            if (code == 401 || code == 403) {
                throw new IOException("Invalid Wordnik API key or unauthorized request.");
            }

            if (code != 200) {
               retries++;

               if (retries >= maxRetries) {
                  throw new IOException("Max retries exceeded. Last HTTP code: " + code);
               }

               continue;
            }

            retries = 0; // Reset the counter
            String json;

            try (InputStream is = connection.getInputStream()) {
                json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonObject root =
                    JsonParser.parseString(json).getAsJsonObject();

            JsonArray examples = root.getAsJsonArray("examples");

            if (examples == null || examples.size() == 0) {
                continue;
            }

            String chosenTitle = null;

            LinkedHashSet<String> uniqueSentences = new LinkedHashSet<>();

            for (JsonElement element : examples) {

                JsonObject example = element.getAsJsonObject();

                // First non-empty title wins
                if (chosenTitle == null
                        && example.has("title")
                        && !example.get("title").isJsonNull()) {

                    String title =
                            example.get("title").getAsString().trim();

                    if (!title.isEmpty()) {
                        chosenTitle = title;
                    }
                }

                if (!example.has("text")
                        || example.get("text").isJsonNull()) {
                    continue;
                }

                String text =
                        example.get("text").getAsString().trim();

                if (text.isEmpty()) {
                    continue;
                }

                // Normalize ending
                if (text.endsWith(".")) {
                    uniqueSentences.add(text);
                } else if (text.endsWith("...")) {
                    uniqueSentences.add(text);
                } else {
                    uniqueSentences.add(text + ".");
                }
            }

            // Reject examples with no usable title
            if (chosenTitle == null || chosenTitle.isBlank()) {
                continue;
            }

            if (uniqueSentences.isEmpty()) {
                continue;
            }

            String paragraph =
                    String.join(" ", uniqueSentences);

            Post content = new Post();
            content.setTitle(chosenTitle);
            content.setContent(paragraph);

            return content;
        }
    }
}