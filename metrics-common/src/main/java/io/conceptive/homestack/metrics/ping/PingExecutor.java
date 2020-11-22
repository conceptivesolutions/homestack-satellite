package io.conceptive.homestack.metrics.ping;

import com.zaxxer.ping.*;
import io.conceptive.homestack.metrics.api.*;
import io.conceptive.homestack.model.data.*;
import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.*;

import javax.enterprise.context.ApplicationScoped;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a "ping" / ICMP request to the given device
 *
 * @author w.glanzer, 18.09.2020
 */
@ApplicationScoped
public class PingExecutor implements IMetricExecutor
{

  private static final int _COUNT = 1;

  @NotNull
  @Override
  public String getType()
  {
    return "ping";
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
    List<_PingResultObject> results = new ArrayList<>();
    Flowable.create(new _PingFlowable(pDevice), BackpressureStrategy.LATEST)
        .limit(_COUNT)
        .blockingForEach(results::add);
    return new _PingResult(results);
  }

  /**
   * Executes a continous ping request - dispose to stop
   */
  private static class _PingFlowable implements FlowableOnSubscribe<_PingResultObject>, PingResponseHandler
  {
    private final IcmpPinger pinger = new IcmpPinger(this);
    private final DeviceDataModel device;
    private final AtomicInteger seqCount = new AtomicInteger(0);
    private FlowableEmitter<_PingResultObject> emitter;

    public _PingFlowable(@NotNull DeviceDataModel pDevice)
    {
      device = pDevice;
    }

    @Override
    public void subscribe(@NotNull FlowableEmitter<_PingResultObject> pEmitter)
    {
      emitter = pEmitter;
      emitter.setDisposable(new Disposable()
      {
        private boolean disposed = false;

        @Override
        public void dispose()
        {
          if (!disposed)
            pinger.stopSelector(); // force
          disposed = true;
        }

        @Override
        public boolean isDisposed()
        {
          return disposed;
        }
      });

      // execute first ping
      _doPing();

      pinger.runSelector();
    }

    @Override
    public void onResponse(@NotNull PingTarget pPingTarget, double pResponseTimeSec, int pByteCount, int pSeq)
    {
      emitter.onNext(new _PingResultObject(pPingTarget, (float) (pResponseTimeSec * 1000), seqCount.getAndIncrement()));
      _doPing();
    }

    @Override
    public void onTimeout(@NotNull PingTarget pPingTarget)
    {
      emitter.onNext(new _PingResultObject(pPingTarget, -1, seqCount.getAndIncrement()));
      _doPing();
    }

    /**
     * Executes a single ping request
     */
    private void _doPing()
    {
      try
      {
        pinger.ping(new PingTarget(InetAddress.getByName(device.address), null, 5_000));
      }
      catch (UnknownHostException e)
      {
        emitter.onError(e);
      }
    }
  }

  /**
   * Simple POJO for ping result
   */
  private static class _PingResultObject
  {
    private final PingTarget target;
    private final float responseTime; // -1 for timeout
    private final int seq;

    public _PingResultObject(@NotNull PingTarget pTarget, float pResponseTime, int pSeq)
    {
      target = pTarget;
      responseTime = pResponseTime;
      seq = pSeq;
    }

    @Override
    public String toString()
    {
      return "_PingResultObject{" +
          "target=" + target.getInetAddress() +
          ", responseTime=" + responseTime +
          ", seq=" + seq +
          '}';
    }
  }

  /**
   * Result-Impl
   */
  private static class _PingResult implements IMetricRecord
  {
    private final MetricRecordDataModel.EState state;
    private final Map<String, String> result;

    public _PingResult(@NotNull List<_PingResultObject> pResults)
    {
      state = _getState(pResults);
      result = Map.of(
          "responseTime", String.format(Locale.ENGLISH, "%.3f", pResults.stream()
              .mapToDouble(pObj -> pObj.responseTime)
              .average()
              .orElse(0))
      );
    }

    @NotNull
    @Override
    public MetricRecordDataModel.EState getState()
    {
      return state;
    }

    @Nullable
    @Override
    public Map<String, String> getResult()
    {
      return result;
    }

    /**
     * Returns the appropriate state
     *
     * @param pResults all ping results
     * @return the state
     */
    @NotNull
    private static MetricRecordDataModel.EState _getState(@NotNull List<_PingResultObject> pResults)
    {
      if (pResults.isEmpty())
        return MetricRecordDataModel.EState.UNKNOWN;
      if (pResults.stream().allMatch(pResult -> pResult.responseTime >= 0))
        return MetricRecordDataModel.EState.SUCCESS;
      else if (pResults.stream().anyMatch(pResult -> pResult.responseTime >= 0))
        return MetricRecordDataModel.EState.WARNING;
      else
        return MetricRecordDataModel.EState.FAILURE;
    }
  }

}
