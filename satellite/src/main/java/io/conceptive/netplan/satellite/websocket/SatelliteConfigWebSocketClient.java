package io.conceptive.netplan.satellite.websocket;

import io.conceptive.netplan.model.satellite.SatelliteConfigurationDataModel;
import io.conceptive.netplan.model.satellite.events.SatelliteWebSocketEvents;
import io.conceptive.netplan.model.satellite.events.data.AuthenticateEventData;
import io.conceptive.netplan.model.websocket.*;
import io.conceptive.netplan.satellite.auth.IJWTProvider;
import io.conceptive.netplan.satellite.websocket.api.IConfigConsumer;
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

/**
 * Client to communicate with the backend
 *
 * @author w.glanzer, 13.11.2020
 */
@Startup
@ApplicationScoped
@ClientEndpoint(decoders = WebsocketEventCoder.class, encoders = WebsocketEventCoder.class)
class SatelliteConfigWebSocketClient
{
  private static final Logger _LOGGER = Logger.getLogger(SatelliteConfigWebSocketClient.class);

  @ConfigProperty(name = "netplan.satellite.id")
  String satelliteID;

  @ConfigProperty(name = "netplan.rest.backend.url")
  String backendBaseURL;

  @Inject
  IJWTProvider jwtProvider;

  @Inject
  Instance<IConfigConsumer> consumers;

  private Boolean connected = false; // true = connected, false = not connected, null = pending
  private Session session;

  @OnOpen
  void onOpen(@NotNull Session pSession, @Nullable EndpointConfig pConfig)
  {
    _LOGGER.info("Connection to cloud host established, authenticating...");
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
      _LOGGER.info("Connection to cloud host established and authenticated successfully");
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
    _LOGGER.warn("Connection to cloud host closed unexpectedly (" + pReason + ")");
    connected = false;
  }

  @OnError
  void onError(@NotNull Session pSession, @Nullable Throwable pThrowable)
  {
    _LOGGER.error("Unexpected error (" + pThrowable + ") in cloud host communication appeared");
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
      _LOGGER.error("Failed to connect to cloud host '" + backendBaseURL + "/satellites" + "'");
    }
  }

  /**
   * Sends the authentication event, to renew the login lease
   */
  @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void sendAuthenticationEvent()
  {
    if (session != null)
      session.getAsyncRemote().sendObject(SatelliteWebSocketEvents.AUTHENTICATE.payload(new AuthenticateEventData(satelliteID, "1.0.0", jwtProvider.getValidJWT())));
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
      _LOGGER.error("Failed to send keepalive message to cloud host");
    }
  }

}
