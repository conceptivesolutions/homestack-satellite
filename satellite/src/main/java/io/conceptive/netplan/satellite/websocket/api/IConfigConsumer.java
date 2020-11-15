package io.conceptive.netplan.satellite.websocket.api;

import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import org.jetbrains.annotations.NotNull;

/**
 * Consumer that receives the (new) configuration
 * model that was received by the cloud host
 *
 * @author w.glanzer, 15.11.2020
 */
public interface IConfigConsumer
{

  /**
   * Gets called, if a new config was received
   *
   * @param pModel Model of config
   */
  void onSatelliteConfigReceived(@NotNull SatelliteConfigurationDataModel pModel);

}
