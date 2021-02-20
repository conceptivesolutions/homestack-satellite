package de.homestack.satellite.config;

import de.homestack.satellite.websocket.api.IConfigConsumer;
import io.conceptive.homestack.model.satellite.SatelliteConfigurationDataModel;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import java.util.Objects;

/**
 * This configuration provider is able to retrieve its config from the backend
 *
 * @author w.glanzer, 10.11.2020
 */
@ApplicationScoped
class DynamicConfigProvider implements IConfigProvider, IConfigConsumer
{

  private final BehaviorSubject<SatelliteConfigurationDataModel> currentlyUsedConfigurationSubject = BehaviorSubject.create();

  @NotNull
  @Override
  public Observable<SatelliteConfigurationDataModel> observe()
  {
    return currentlyUsedConfigurationSubject;
  }

  @Override
  public void onSatelliteConfigReceived(@NotNull SatelliteConfigurationDataModel pConfig)
  {
    if (!Objects.equals(currentlyUsedConfigurationSubject.getValue(), pConfig))
    {
      Logger.getLogger(DynamicConfigProvider.class).info("New configuration received from homestack cloud");
      currentlyUsedConfigurationSubject.onNext(pConfig);
    }
  }

}
