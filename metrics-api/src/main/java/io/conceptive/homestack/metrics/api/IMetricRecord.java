package io.conceptive.homestack.metrics.api;

import io.conceptive.homestack.model.data.metric.EMetricRecordState;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * Specifies the result for a metrics analyzer
 *
 * @author w.glanzer, 18.09.2020
 */
public interface IMetricRecord
{

  /**
   * @return the current state of this metrics
   */
  @NotNull
  EMetricRecordState getState();

  /**
   * @return additional description to the current state
   */
  @Nullable
  Map<String, String> getResult();

}
