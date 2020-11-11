package io.conceptive.netplan.satellite.auth;

import io.conceptive.netplan.satellite.config.DynamicConfigProvider;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.Nullable;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.*;
import java.time.Instant;
import java.util.*;

/**
 * Defines the factory to provide the "Authorization" Header for Rest clients
 *
 * @author w.glanzer, 11.11.2020
 */
@ApplicationScoped
public class AuthorizationHeaderFactory implements ClientHeadersFactory
{

  @Inject
  @RestClient
  IAuthRestClient authRestClient;

  @ConfigProperty(name = "quarkus.oidc.client-id")
  String applicationID;

  @ConfigProperty(name = "USER_MAIL")
  String userMail;

  @ConfigProperty(name = "USER_APIKEY")
  String userAPIKey;

  @ConfigProperty(name = "SATELLITE_ID")
  String satelliteID;

  private String rawToken;
  private Long expirationTime;

  @Override
  public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders, MultivaluedMap<String, String> clientOutgoingHeaders)
  {
    MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();
    result.putSingle("Authorization", "Bearer " + _getValidJWT());
    return result;
  }

  /**
   * @return Retrieves a valid JWT token - NULL if it can not be retrieved
   */
  @Nullable
  private String _getValidJWT()
  {
    try
    {
      if(rawToken == null || expirationTime == null || (expirationTime - 60) >= Instant.now().getEpochSecond())
      {
        rawToken = authRestClient.createJWTToken(new IAuthRestClient.TokenRequest(userMail + "_" + satelliteID, userAPIKey, applicationID)).token;

        // Just hack a expiration time - we do not need to validate the token.
        expirationTime = Long.valueOf(String.valueOf(Json.decodeValue(new String(Base64.getUrlDecoder().decode(rawToken.split("\\.")[1])), Map.class).get("exp")));
      }

      return rawToken;
    }
    catch (Exception e)
    {
      Logger.getLogger(DynamicConfigProvider.class).error("Failed to retrieve token", e);
      return null;
    }
  }

}
