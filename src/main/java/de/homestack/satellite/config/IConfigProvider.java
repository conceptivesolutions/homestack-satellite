package de.homestack.satellite.config;

import io.conceptive.homestack.model.satellite.SatelliteConfigurationDataModel;
import io.reactivex.Observable;
import org.jetbrains.annotations.NotNull;

/**
 * @author w.glanzer, 10.11.2020
 */
public interface IConfigProvider
{

  /**
   * Returns an observable that will contain the currently active satellite config
   *
   * @return the observable with the currently active config
   */
  @NotNull
  Observable<SatelliteConfigurationDataModel> observe();

}
