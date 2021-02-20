package io.conceptive.homestack.metrics.api;

import io.conceptive.homestack.model.data.device.DeviceDataModel;
import org.jetbrains.annotations.NotNull;

/**
 * Executor to provide metrics analysis
 *
 * @author w.glanzer, 18.09.2020
 */
public interface IMetricExecutor
{

  /**
   * Returns an unique identifier for this metric.
   * Something like "ping", "tracert", etc.
   * Should be lowercased by convention.
   *
   * @return the identifier
   */
  @NotNull
  String getType();

  /**
   * Determines, if this executor is generally available, or unavailable for any reason
   *
   * @return true, if available
   */
  boolean canExecute();

  /**
   * Executes this metric component for a given device
   *
   * @param pDevice      Device to check
   * @param pPreferences The Preferences for this metric execution
   * @return the result, not null
   */
  @NotNull
  IMetricRecord execute(@NotNull DeviceDataModel pDevice, @NotNull IMetricPreferences pPreferences);

}
