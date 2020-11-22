package io.conceptive.homestack.satellite.auth;

import org.jetbrains.annotations.NotNull;

/**
 * Provides access to a valid json web token for backend requests
 *
 * @author w.glanzer, 13.11.2020
 */
public interface IJWTProvider
{

  /**
   * Returns a currently available jwt
   *
   * @return token as string - not validated!
   * @throws RuntimeException if it could not be generated
   */
  @NotNull
  String getValidJWT();

}
