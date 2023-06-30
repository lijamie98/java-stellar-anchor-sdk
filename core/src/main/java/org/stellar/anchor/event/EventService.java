package org.stellar.anchor.event;

import java.util.List;
import org.stellar.anchor.api.event.AnchorEvent;
import org.stellar.anchor.api.exception.AnchorException;
import org.stellar.anchor.api.exception.EventPublishException;

/**
 * The EventService is used to publish events to the event queue and to read events from the event
 * queue.
 *
 * <p>Read event example
 *
 * <pre>
 *     EventService eventService = new EventServiceImpl();
 *     eventService.publish(new TransactionEvent(...));
 *     eventService.publish(new ControlEvent(...));
 *     ...
 *     EventService.Session session = eventService.createSession(EventService.EventQueue.TRANSACTION);
 *     EventService.ReadResponse readResponse = session.read();
 *     List&lt;AnchorEvent&gt; events = readResponse.getEvents();
 *     ...
 *     session.ack(readResponse);
 *     ...
 *     session.close();
 *     ...
 *     eventService = null;
 * </pre>
 *
 * <p>Publish event example
 *
 * <pre>
 *     EventService eventService = new EventServiceImpl();
 *     EventService.Session session = eventService.createSession(EventService.EventQueue.TRANSACTION);
 *     session.publish(new TransactionEvent(...));
 *     session.close();
 * </pre>
 */
public interface EventService {
  /**
   * To be deprecated. Use createSession() instead.
   *
   * @param event the event to be published
   * @throws EventPublishException
   */
  void publish(AnchorEvent event) throws EventPublishException;

  /**
   * Creates a session for publishing and reading events.
   *
   * @return a session object.
   */
  Session createSession(EventQueue eventQueue);

  interface Session {
    /**
     * Publishes an event to the event queue. The queue will be determined by the implementation of
     * the Session.
     *
     * @param event the event to publish
     */
    void publish(AnchorEvent event) throws AnchorException;

    /**
     * Reads events from the event queue.
     *
     * @return
     */
    ReadResponse read() throws AnchorException;

    /**
     * Acknowledges that the events returned by the read() method have been processed.
     *
     * @param readResponse
     */
    void ack(ReadResponse readResponse) throws AnchorException;

    /** Closes the session. */
    void close() throws AnchorException;
  }

  interface ReadResponse {
    List<AnchorEvent> getEvents();
  }

  /** List of events queues that are supported by the EventService. */
  enum EventQueue {
    /** The event queue for the transaction events that are to be processed by the anchor. */
    TRANSACTION,

    /** The event queue for events that are used for control-plane purposes. */
    CONTROL
  }
}
