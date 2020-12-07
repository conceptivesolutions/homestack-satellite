package io.conceptive.homestack.satellite.websocket;

import io.conceptive.homestack.model.data.MetricRecordDataModel;
import io.conceptive.homestack.model.satellite.SatelliteConfigurationDataModel;
import io.conceptive.homestack.model.satellite.events.SatelliteWebSocketEvents;
import io.conceptive.homestack.model.satellite.events.data.*;
import io.conceptive.homestack.model.websocket.*;
import io.conceptive.homestack.satellite.websocket.api.*;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.websocket.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Client to communicate with the backend
 *
 * @author w.glanzer, 13.11.2020
 */
@Startup
@ApplicationScoped
@ClientEndpoint(decoders = WebsocketEventCoder.class, encoders = WebsocketEventCoder.class)
class SatelliteConfigWebSocketClient implements IMetricRecordPublisher
{
  private static final Logger _LOGGER = Logger.getLogger(SatelliteConfigWebSocketClient.class);

  @ConfigProperty(name = "homestack.satellite.lease.id")
  protected String leaseID;

  @ConfigProperty(name = "homestack.satellite.lease.token")
  protected String leaseToken;

  @ConfigProperty(name = "homestack.rest.backend.url")
  protected String backendBaseURL;

  @Inject
  protected Instance<IConfigConsumer> consumers;

  private Boolean connected = false; // true = connected, false = not connected, null = pending
  private Session session;

  @OnOpen
  void onOpen(@NotNull Session pSession, @Nullable EndpointConfig pConfig)
  {
    _LOGGER.info("Connection to homestack cloud established, authenticating...");
    session = pSession;
    connected = null;

    // initialize flow with authentication / authorization
    sendAuthenticationEvent();
  }

  @OnMessage
  void onMessage(@NotNull WebsocketEvent<?> pMessage)
  {
    if (connected != Boolean.TRUE)
    {
      _LOGGER.info("Connection to homestack cloud established and authenticated successfully");
      connected = true;
    }

    // new config received from server
    if (pMessage.equalType(SatelliteWebSocketEvents.CONFIG))
    {
      SatelliteConfigurationDataModel model = SatelliteWebSocketEvents.CONFIG.payloadOf(pMessage);
      consumers.forEach(pConsumer -> pConsumer.onSatelliteConfigReceived(model));
    }
  }

  @OnClose
  void onClose(@NotNull Session pSession, @Nullable CloseReason pReason)
  {
    _LOGGER.warn("Connection to homestack cloud closed unexpectedly (" + pReason + ")");
    connected = false;
  }

  @OnError
  void onError(@NotNull Session pSession, @Nullable Throwable pThrowable)
  {
    _LOGGER.error("Unexpected error in homestack cloud communication appeared", pThrowable);
    connected = false;
  }

  /**
   * Connects this client, if it is not already connected.
   * If the connection is currently not available, it will retry to connect unless it will be available.
   */
  @Scheduled(every = "15s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void connectAndRetry()
  {
    String url = backendBaseURL + "/satellites";

    try
    {
      if (connected == Boolean.FALSE)
        ContainerProvider.getWebSocketContainer().connectToServer(this, new URI(url));
    }
    catch (Exception e)
    {
      _LOGGER.error("Failed to connect to homestack cloud '" + backendBaseURL + "/satellites" + "'", e);
    }
  }

  @Override
  public void sendMetricRecords(@NotNull Set<MetricRecordDataModel> pRecords)
  {
    if (session != null)
    {
      MetricRecordsEventData data = new MetricRecordsEventData();
      data.records = pRecords;
      session.getAsyncRemote().sendObject(SatelliteWebSocketEvents.RECORDS.payload(data));
    }
    else
      _LOGGER.warn("Tried to upload records, but connection was not esablished");
  }

  /**
   * Sends the authentication event, to renew the login lease
   */
  @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void sendAuthenticationEvent()
  {
    if (session != null) // todo version
    {
      AuthenticateEventData data = new AuthenticateEventData();
      data.leaseID = leaseID;
      data.leaseToken = leaseToken;
      session.getAsyncRemote().sendObject(SatelliteWebSocketEvents.AUTHENTICATE.payload(data));
    }
  }

  /**
   * Sends the keepalive ping message
   */
  @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void sendKeepAlive()
  {
    try
    {
      if (session != null)
        session.getAsyncRemote().sendPing(ByteBuffer.allocate(0));
    }
    catch (Exception e)
    {
      _LOGGER.error("Failed to send keepalive message to homestack cloud", e);
    }
  }

}
