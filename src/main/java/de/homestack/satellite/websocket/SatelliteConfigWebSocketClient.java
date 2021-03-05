package de.homestack.satellite.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.homestack.satellite.websocket.api.*;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.CloudEventUtils;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.jackson.PojoCloudEventDataMapper;
import io.conceptive.homestack.model.coders.CloudEventCoder;
import io.conceptive.homestack.model.data.metric.MetricRecordDataModel;
import io.conceptive.homestack.model.satellite.events.*;
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
import java.util.*;

/**
 * Client to communicate with the backend
 *
 * @author w.glanzer, 13.11.2020
 */
@Startup
@ApplicationScoped
@ClientEndpoint(decoders = CloudEventCoder.class, encoders = CloudEventCoder.class)
class SatelliteConfigWebSocketClient implements IMetricRecordPublisher
{
  private static final Logger _LOGGER = Logger.getLogger(SatelliteConfigWebSocketClient.class);

  @ConfigProperty(name = "homestack.satellite.lease.id")
  protected String leaseID;

  @ConfigProperty(name = "homestack.satellite.lease.token")
  protected String leaseToken;

  @ConfigProperty(name = "homestack.cloud.websocket.url")
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
  void onMessage(@NotNull CloudEvent pMessage)
  {
    if (connected != Boolean.TRUE)
    {
      _LOGGER.info("Connection to homestack cloud established and authenticated successfully");
      connected = true;
    }

    // new config received from server
    if (Objects.equals(pMessage.getType(), RenewConfigurationEventData.TYPE))
    {
      PojoCloudEventData<RenewConfigurationEventData> data = CloudEventUtils.mapData(pMessage, PojoCloudEventDataMapper.from(new ObjectMapper(), RenewConfigurationEventData.class));
      if (data != null)
        consumers.forEach(pConsumer -> pConsumer.onSatelliteConfigReceived(data.getValue().config));
      else
        _LOGGER.warn("Invalid configuration event from cloud received");
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
      _LOGGER.error("Failed to connect to homestack cloud '" + url + "'", e);
    }
  }

  @Override
  public void sendMetricRecords(@NotNull Set<MetricRecordDataModel> pRecords)
  {
    if (session != null)
    {
      session.getAsyncRemote().sendObject(CloudEventBuilder.v1()
                                              .withId(UUID.randomUUID().toString())
                                              .withType(MetricRecordsEventData.TYPE)
                                              .withSource(URI.create("/satellite/records"))
                                              .withData(PojoCloudEventData.wrap(MetricRecordsEventData.builder()
                                                                                    .records(pRecords)
                                                                                    .build(),
                                                                                pData -> new ObjectMapper().writeValueAsBytes(pData)))
                                              .build());
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
      session.getAsyncRemote().sendObject(CloudEventBuilder.v1()
                                              .withId(UUID.randomUUID().toString())
                                              .withType(AuthenticateEventData.TYPE)
                                              .withSource(URI.create("/satellite/auth"))
                                              .withData(PojoCloudEventData.wrap(AuthenticateEventData.builder()
                                                                                    .leaseID(leaseID)
                                                                                    .leaseToken(leaseToken)
                                                                                    .version("0.0.0")
                                                                                    .commVersion(1)
                                                                                    .build(),
                                                                                pData -> new ObjectMapper().writeValueAsBytes(pData)))
                                              .build());
    else
      _LOGGER.warn("Tried to authenticate, but connection was not esablished");
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
