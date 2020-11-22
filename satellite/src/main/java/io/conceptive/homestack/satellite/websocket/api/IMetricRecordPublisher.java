package io.conceptive.homestack.satellite.websocket.api;

import io.conceptive.homestack.model.data.MetricRecordDataModel;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author w.glanzer, 15.11.2020
 */
public interface IMetricRecordPublisher
{

  /**
   * Gets called, if the metric records should be published to cloud
   *
   * @param pRecords records to publish
   */
  void sendMetricRecords(@NotNull Set<MetricRecordDataModel> pRecords);

}
