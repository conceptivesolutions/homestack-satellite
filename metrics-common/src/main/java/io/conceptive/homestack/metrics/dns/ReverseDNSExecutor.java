package io.conceptive.homestack.metrics.dns;

import com.google.common.base.Strings;
import io.conceptive.homestack.metrics.api.*;
import io.conceptive.homestack.model.data.device.DeviceDataModel;
import io.conceptive.homestack.model.data.metric.EMetricRecordState;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import java.net.InetAddress;

/**
 * Executes a reverse DNS query to get the host names for the given device
 *
 * @author w.glanzer, 02.11.2020
 */
@ApplicationScoped
public class ReverseDNSExecutor implements IMetricExecutor
{

  @NotNull
  @Override
  public String getType()
  {
    return "reverse-dns";
  }

  @Override
  public boolean canExecute()
  {
    return SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC;
  }

  @NotNull
  @Override
  public IMetricRecord execute(@NotNull DeviceDataModel pDevice, @NotNull IMetricPreferences pPreferences)
  {
    try
    {
      InetAddress address = InetAddress.getByName(pDevice.address);
      String hostName = address.getHostName();
      if (!Strings.isNullOrEmpty(hostName))
        return new SimpleMetricRecord(EMetricRecordState.SUCCESS)
            .withResult("name", hostName);
    }
    catch (Exception e)
    {
      // nothing
    }

    // not resolvable
    return new SimpleMetricRecord(EMetricRecordState.UNKNOWN);
  }

}
