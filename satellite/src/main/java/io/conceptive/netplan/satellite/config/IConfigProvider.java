package io.conceptive.netplan.satellite.config;

import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author w.glanzer, 10.11.2020
 */
public interface IConfigProvider
{

  /**
   * Returns the current configuration that should be used
   *
   * @return the current model - could change over time
   */
  @NotNull
  SatelliteConfigurationDataModel getConfig();

}
