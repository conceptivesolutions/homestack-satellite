package io.conceptive.netplan.satellite.rest;

import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import io.conceptive.netplan.satellite.auth.AuthorizationHeaderFactory;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * @author w.glanzer, 10.11.2020
 */
@RegisterRestClient(configKey = "backend-api")
@RegisterClientHeaders(AuthorizationHeaderFactory.class)
public interface IBackendRestClient
{

  /**
   * Returns the current configuration that should be used by the client
   *
   * @return the config
   */
  @GET
  @Path("/satellites/config")
  @Produces(MediaType.APPLICATION_JSON)
  SatelliteConfigurationDataModel getConfigForSatellite();

}
