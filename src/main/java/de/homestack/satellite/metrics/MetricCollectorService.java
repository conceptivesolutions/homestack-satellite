package de.homestack.satellite.metrics;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.homestack.satellite.config.IConfigProvider;
import de.homestack.satellite.metrics.api.*;
import de.homestack.satellite.websocket.api.IMetricRecordPublisher;
import io.conceptive.homestack.model.data.device.DeviceDataModel;
import io.conceptive.homestack.model.data.metric.*;
import io.conceptive.homestack.model.satellite.SatelliteConfigurationDataModel;
import io.quarkus.runtime.*;
import io.reactivex.Observable;
import io.reactivex.*;
import io.reactivex.disposables.*;
import io.reactivex.schedulers.Schedulers;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service, that collects and publishes the metric records
 *
 * @author w.glanzer, 15.11.2020
 */
@ApplicationScoped
public class MetricCollectorService
{
  private static final Logger _LOGGER = Logger.getLogger(MetricCollectorService.class);

  @Inject
  protected IConfigProvider configProvider;

  @Inject
  protected IMetricRecordPublisher recordPublisher;

  @Inject
  protected Instance<IMetricExecutor> metricExecutors;

  private CompositeDisposable disposable;

  @SuppressWarnings("unused")
  void onStart(@Observes StartupEvent pEvent)
  {
    disposable = new CompositeDisposable();
    disposable.add(_initExecutor());
  }

  @SuppressWarnings("unused")
  void onShutdown(@Observes ShutdownEvent pEvent)
  {
    if (disposable != null)
    {
      disposable.dispose();
      disposable = null;
    }
  }

  /**
   * Observes the configuration and triggers the executor,
   * if it should be triggered
   */
  @NotNull
  private Disposable _initExecutor()
  {
    return Observable.combineLatest(configProvider.observe(), Observable.interval(15, TimeUnit.SECONDS), (pConfig, pInterval) -> pConfig)
        // Backpressure
        .toFlowable(BackpressureStrategy.DROP)

        // Observe on different thread pool
        .observeOn(Schedulers.from(Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                                                                     .setNameFormat("tMetricUpdater-%d")
                                                                     .build())))

        // Collect records
        .subscribe(pConfig -> {
          try
          {
            _triggerCollect(pConfig);
          }
          catch (Exception e)
          {
            _LOGGER.warn("Failed to collect records", e);
          }
        });
  }

  /**
   * Triggers a single collect for all currently known metrics
   *
   * @param pConfig config to execute
   */
  private void _triggerCollect(@NotNull SatelliteConfigurationDataModel pConfig)
  {
    if (pConfig.metrics == null || pConfig.devices == null)
      return;

    Map<String, DeviceDataModel> devices = pConfig.devices.stream().collect(Collectors.toMap(pDev -> pDev.id, pDev -> pDev));
    Map<String, IMetricExecutor> executors = metricExecutors.stream().collect(Collectors.toMap(IMetricExecutor::getType, pEx -> pEx));
    Set<MetricRecordDataModel> result = new HashSet<>();

    // check all metrics
    for (MetricDataModel metric : pConfig.metrics)
    {
      if (metric.type != null)
      {
        IMetricExecutor executor = executors.get(metric.type.toLowerCase(Locale.ROOT));
        if (executor != null)
        {
          DeviceDataModel device = devices.get(metric.deviceID);
          if (device != null)
          {
            if (executor.canExecute())
            {
              // execute
              IMetricRecord record = executor.execute(device, new _PreferenceImpl(metric));

              // add result
              result.add(MetricRecordDataModel.builder()
                             .id(UUID.randomUUID().toString())
                             .metricID(metric.id)
                             .recordDate(new Date())
                             .state(EMetricRecordState.valueOf(record.getState().name()))
                             .result(record.getResult())
                             .build());
            }
          }
          else
            _LOGGER.warn("Device with id " + metric.deviceID + " not found");
        }
        else
          _LOGGER.warn("Executor with id " + metric.type + " not found");
      }
    }

    // publish
    if (!result.isEmpty())
      recordPublisher.sendMetricRecords(result);
  }

  /**
   * IMetricPreferences-Impl
   */
  private static class _PreferenceImpl implements IMetricPreferences
  {
    private final MetricDataModel metric;

    public _PreferenceImpl(@NotNull MetricDataModel pMetric)
    {
      metric = pMetric;
    }

    @NotNull
    @Override
    public String getValue(@NotNull String pKey, @NotNull String pDefault)
    {
      if (metric.settings == null)
        return pDefault;
      return metric.settings.getOrDefault(pKey, pDefault);
    }
  }

}
