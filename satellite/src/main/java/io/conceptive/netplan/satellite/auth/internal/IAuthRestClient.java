package io.conceptive.netplan.satellite.auth.internal;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * Rest Client for the Authentication Backend
 *
 * @author w.glanzer, 10.11.2020
 */
@RegisterRestClient(configKey = "auth-api")
public interface IAuthRestClient
{

  /**
   * Requests a new JWT token from the backend
   *
   * @param pTokenRequest Request with all information needed
   * @return the result
   */
  @POST
  @Path("/login")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  TokenResult createJWTToken(@NotNull TokenRequest pTokenRequest);

  /**
   * Describes the result from JWT-Creation-Request
   */
  class TokenResult
  {
    /**
     * JWT-Token
     */
    public String token;
  }

  /**
   * Describes the request for JWT-Creation
   */
  class TokenRequest
  {
    public final String loginId;
    public final String password;
    public final String applicationId;

    public TokenRequest(@NotNull String pLoginId, @NotNull String pPassword, @NotNull String pApplicationId)
    {
      loginId = pLoginId;
      password = pPassword;
      applicationId = pApplicationId;
    }
  }

}
