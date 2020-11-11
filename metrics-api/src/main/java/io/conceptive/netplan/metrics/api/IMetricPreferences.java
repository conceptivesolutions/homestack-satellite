package io.conceptive.netplan.metrics.api;

import org.jetbrains.annotations.NotNull;

/**
 * Contains preferences for a single metric
 *
 * @author w.glanzer, 02.11.2020
 */
public interface IMetricPreferences
{

  /**
   * Returns the value for the given key in this preferences
   *
   * @param pKey     Key
   * @param pDefault Default-Value, if the value is null
   * @return the value
   */
  @NotNull
  String getValue(@NotNull String pKey, @NotNull String pDefault);

}
