package io.conceptive.netplan.satellite.config.internal;

import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import io.conceptive.netplan.satellite.config.IConfigProvider;
import io.quarkus.runtime.StartupEvent;
import io.reactivex.Observable;
import io.reactivex.subjects.*;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.*;
import javax.inject.Inject;
import java.util.Objects;

/**
 * This configuration provider is able to retrieve its config from the backend
 *
 * @author w.glanzer, 10.11.2020
 */
@ApplicationScoped
public class DynamicConfigProvider implements IConfigProvider
{

  @Inject
  SatelliteConfigWebSocketClient client;

  private final BehaviorSubject<SatelliteConfigurationDataModel> currentlyUsedConfigurationSubject = BehaviorSubject.create();

  @NotNull
  @Override
  public Observable<SatelliteConfigurationDataModel> observe()
  {
    return currentlyUsedConfigurationSubject;
  }

  /**
   * Gets called, if the satellite was started
   */
  @SuppressWarnings("unused")
  void onStart(@Observes StartupEvent pStartupEvent)
  {
    client.onConfigReceived(pConfig -> {
      if(!Objects.equals(currentlyUsedConfigurationSubject.getValue(), pConfig))
      {
        Logger.getLogger(DynamicConfigProvider.class).info("New configuration received from cloud host");
        currentlyUsedConfigurationSubject.onNext(pConfig);
      }
    });
  }

}
