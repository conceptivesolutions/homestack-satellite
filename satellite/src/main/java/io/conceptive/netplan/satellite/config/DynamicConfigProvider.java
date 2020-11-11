package io.conceptive.netplan.satellite.config;

import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import io.conceptive.netplan.satellite.rest.IBackendRestClient;
import io.quarkus.runtime.*;
import io.reactivex.Flowable;
import io.reactivex.disposables.*;
import io.reactivex.subjects.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * This configuration provider is able to retrieve its config from the backend
 *
 * @author w.glanzer, 10.11.2020
 */
@ApplicationScoped
public class DynamicConfigProvider implements IConfigProvider
{

  private final Subject<Optional<SatelliteConfigurationDataModel>> currentlyUsedConfiguration = BehaviorSubject.createDefault(Optional.empty());
  private CompositeDisposable disposable;

  @Inject
  @RestClient
  IBackendRestClient backendRestClient;

  @NotNull
  @Override
  public SatelliteConfigurationDataModel getConfig()
  {
    return currentlyUsedConfiguration
        .filter(Optional::isPresent)
        .map(Optional::get)
        .blockingFirst(); // await first config
  }

  /**
   * Gets called, if the satellite was started
   */
  @SuppressWarnings("unused")
  void onStart(@Observes StartupEvent pStartupEvent)
  {
    disposable = new CompositeDisposable();
    disposable.add(_initConfigurationRefresher());
  }

  /**
   * Gets called, if the satellite was stopped
   */
  @SuppressWarnings("unused")
  void onShutdown(@Observes ShutdownEvent pShutdownEvent)
  {
    currentlyUsedConfiguration.onNext(Optional.empty());

    if (disposable != null)
    {
      disposable.dispose();
      disposable = null;
    }
  }

  /**
   * Initializes the configuration refresher
   */
  @NotNull
  private Disposable _initConfigurationRefresher()
  {
    return Flowable.interval(0, 60, TimeUnit.SECONDS)
        .onBackpressureDrop()

        // get config from backend
        .map(pToken -> {
          try
          {
            return Optional.ofNullable(backendRestClient.getConfigForSatellite());
          }
          catch (Exception e)
          {
            Logger.getLogger(DynamicConfigProvider.class).error("Failed to retrieve configuration model from backend", e);
            return Optional.<SatelliteConfigurationDataModel>empty();
          }
        })

        // continue only if config valid
        .filter(Optional::isPresent)
        .map(Optional::get)

        // update subject
        .subscribe(pModel -> currentlyUsedConfiguration.onNext(Optional.of(pModel)));
  }

}
