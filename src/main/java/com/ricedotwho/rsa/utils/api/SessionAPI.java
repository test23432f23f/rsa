package com.ricedotwho.rsa.utils.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ricedotwho.rsm.utils.Accessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.session.Session;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class SessionAPI implements Accessor {
   public static String[] getProfileInfo(String token) throws IOException {
      CloseableHttpClient client = HttpClients.createDefault();

      String[] var8;
      try {
         HttpGet request = new HttpGet("https://api.minecraftservices.com/minecraft/profile");
         request.setHeader("Authorization", "Bearer " + token);
         CloseableHttpResponse response = client.execute(request);

         try {
            String jsonString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
            String ign = jsonObject.get("name").getAsString();
            String uuid = jsonObject.get("id").getAsString().replaceAll("-", "");
            var8 = new String[]{ign, uuid};
         } catch (Throwable var11) {
            if (response != null) {
               try {
                  response.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (response != null) {
            response.close();
         }
      } catch (Throwable var12) {
         if (client != null) {
            try {
               client.close();
            } catch (Throwable var9) {
               var12.addSuppressed(var9);
            }
         }

         throw var12;
      }

      if (client != null) {
         client.close();
      }

      return var8;
   }

   public static boolean validateSession(String token) {
      try {
         String[] profileInfo = getProfileInfo(token);
         String ign = profileInfo[0];
         String uuid = profileInfo[1];
         Session user = mc.getSession();
         return ign.equals(user.getUsername()) && uuid.equals(user.getUuidOrNull().toString().replace("-", ""));
      } catch (Exception var5) {
         return false;
      }
   }

   public static boolean checkOnline(String uuid) {
      try {
         CloseableHttpClient client = HttpClients.createDefault();

         boolean var6;
         try {
            HttpGet request = new HttpGet("https://api.slothpixel.me/api/players/" + uuid);
            CloseableHttpResponse response = client.execute(request);

            try {
               String jsonString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
               JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
               var6 = jsonObject.get("online").getAsBoolean();
            } catch (Throwable var9) {
               if (response != null) {
                  try {
                     response.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }
               }

               throw var9;
            }

            if (response != null) {
               response.close();
            }
         } catch (Throwable var10) {
            if (client != null) {
               try {
                  client.close();
               } catch (Throwable var7) {
                  var10.addSuppressed(var7);
               }
            }

            throw var10;
         }

         if (client != null) {
            client.close();
         }

         return var6;
      } catch (Exception var11) {
         var11.printStackTrace();
         return false;
      }
   }

   public static UUID undashedToUUID(String uuid) {
      return UUID.fromString(uuid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
   }

   public static int changeName(String newName, String token) throws IOException {
      CloseableHttpClient client = HttpClients.createDefault();

      int var5;
      try {
         HttpPut request = new HttpPut("https://api.minecraftservices.com/minecraft/profile/name/" + newName);
         request.setHeader("Authorization", "Bearer " + token);
         CloseableHttpResponse response = client.execute(request);

         try {
            var5 = response.getStatusLine().getStatusCode();
         } catch (Throwable var9) {
            if (response != null) {
               try {
                  response.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (response != null) {
            response.close();
         }
      } catch (Throwable var10) {
         if (client != null) {
            try {
               client.close();
            } catch (Throwable var7) {
               var10.addSuppressed(var7);
            }
         }

         throw var10;
      }

      if (client != null) {
         client.close();
      }

      return var5;
   }

   public static int changeSkin(String url, String token) throws IOException {
      CloseableHttpClient client = HttpClients.createDefault();

      int var6;
      try {
         HttpPost request = new HttpPost("https://api.minecraftservices.com/minecraft/profile/skins");
         request.setHeader("Authorization", "Bearer " + token);
         request.setHeader("Content-Type", "application/json");
         String jsonString = "{\n  \"variant\": \"classic\",\n  \"url\": \"%s\"\n}\n".formatted(url);
         request.setEntity(new StringEntity(jsonString));
         CloseableHttpResponse response = client.execute(request);

         try {
            var6 = response.getStatusLine().getStatusCode();
         } catch (Throwable var10) {
            if (response != null) {
               try {
                  response.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (response != null) {
            response.close();
         }
      } catch (Throwable var11) {
         if (client != null) {
            try {
               client.close();
            } catch (Throwable var8) {
               var11.addSuppressed(var8);
            }
         }

         throw var11;
      }

      if (client != null) {
         client.close();
      }

      return var6;
   }
}
