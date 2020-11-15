package io.conceptive.netplan.satellite.metrics;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.conceptive.netplan.metrics.api.*;
import io.conceptive.netplan.model.data.*;
import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import io.conceptive.netplan.satellite.config.IConfigProvider;
import io.conceptive.netplan.satellite.websocket.api.IMetricRecordPublisher;
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
  IConfigProvider configProvider;

  @Inject
  IMetricRecordPublisher recordPublisher;

  @Inject
  Instance<IMetricExecutor> metricExecutors;

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
      IMetricExecutor executor = executors.get(metric.type);
      if (executor != null)
      {
        DeviceDataModel device = devices.get(metric.deviceID);
        if (device != null)
        {
          IMetricPreferences preference = new _PreferenceImpl(metric);
          if (executor.canExecute())
            result.add(_toMetricRecord(device, executor, executor.execute(device, preference)));
        }
        else
          _LOGGER.warn("Device with id " + metric.deviceID + " not found");
      }
      else
        _LOGGER.warn("Executor with id " + metric.type + " not found");
    }

    // publish
    if (!result.isEmpty())
      recordPublisher.sendMetricRecords(result);
  }

  /**
   * Converts a metricsResult to the serializable metric result object
   *
   * @param pDevice   Device which the metric belongs to
   * @param pExecutor the executor that issued the given record
   * @param pResult   result to convert
   * @return converted result
   */
  @NotNull
  private MetricRecordDataModel _toMetricRecord(@NotNull DeviceDataModel pDevice, @NotNull IMetricExecutor pExecutor, @NotNull IMetricRecord pResult)
  {
    MetricRecordDataModel metricRecord = new MetricRecordDataModel();
    metricRecord.deviceID = pDevice.id;
    metricRecord.recordTime = new Date();
    metricRecord.type = pExecutor.getType();
    metricRecord.state = MetricRecordDataModel.EState.valueOf(pResult.getState().name());
    metricRecord.result = pResult.getResult();
    return metricRecord;
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
