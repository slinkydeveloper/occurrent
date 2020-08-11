package se.haleby.occurrent.changestreamer.api.blocking;

import se.haleby.occurrent.changestreamer.ChangeStreamFilter;
import se.haleby.occurrent.changestreamer.CloudEventWithStreamPosition;
import se.haleby.occurrent.changestreamer.StartAt;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Common interface for blocking change streamers. The purpose of a change streamer is to read events from an event store
 * and react to these events. Typically a change streamer will forward the event to another piece of infrastructure such as
 * a message bus or to create views from the events (such as projections, sagas, snapshots etc).
 */
public interface BlockingChangeStreamer {

    /**
     * Start listening to cloud events persisted to the event store using the supplied start position and <code>filter</code>.
     *
     * @param subscriptionId  The id of the subscription, must be unique!
     * @param action          This action will be invoked for each cloud event that is stored in the EventStore.
     * @param filter          The filter to use to limit which events that are of interest from the EventStore.
     * @param startAtSupplier A supplier that returns the start position to start the subscription from.
     *                        This is a useful alternative to just passing a fixed "StartAt" value if the stream is broken and re-subscribed to.
     *                        In this cases streams should be restarted from the latest position and not the start position as it were when the application
     *                        was started.
     */
    void stream(String subscriptionId, Consumer<CloudEventWithStreamPosition> action, ChangeStreamFilter filter, Supplier<StartAt> startAtSupplier);


    /**
     * Start listening to cloud events persisted to the event store using the supplied start position and <code>filter</code>.
     *
     * @param subscriptionId The id of the subscription, must be unique!
     * @param action         This action will be invoked for each cloud event that is stored in the EventStore.
     * @param filter         The filter to use to limit which events that are of interest from the EventStore.
     * @param startAt        The position to start the subscription from
     */
    default void stream(String subscriptionId, Consumer<CloudEventWithStreamPosition> action, ChangeStreamFilter filter, StartAt startAt) {
        stream(subscriptionId, action, filter, () -> startAt);
    }

    /**
     * Start listening to cloud events persisted to the event store at the supplied start position.
     *
     * @param subscriptionId The id of the subscription, must be unique!
     * @param action         This action will be invoked for each cloud event that is stored in the EventStore.
     * @param startAt        The position to start the subscription from
     */
    default void stream(String subscriptionId, Consumer<CloudEventWithStreamPosition> action, StartAt startAt) {
        stream(subscriptionId, action, null, startAt);
    }

    /**
     * Start listening to cloud events persisted to the event store at this moment in time with the specified <code>filter</code>.
     *
     * @param subscriptionId The id of the subscription, must be unique!
     * @param action         This action will be invoked for each cloud event that is stored in the EventStore.
     * @param filter         The filter to use to limit which events that are of interest from the EventStore.
     */
    default void stream(String subscriptionId, Consumer<CloudEventWithStreamPosition> action, ChangeStreamFilter filter) {
        stream(subscriptionId, action, filter, StartAt.now());
    }

    /**
     * Start listening to cloud events persisted to the event store at this moment in time.
     *
     * @param subscriptionId The id of the subscription, must be unique!
     * @param action         This action will be invoked for each cloud event that is stored in the EventStore.
     */
    default void stream(String subscriptionId, Consumer<CloudEventWithStreamPosition> action) {
        stream(subscriptionId, action, null, StartAt.now());
    }

    /**
     * Cancel the subscription
     */
    void cancelSubscription(String subscriptionId);

    /**
     * Shutdown the change streamer and close all subscriptions (they can be resumed later if you start from a persisted stream position).
     */
    void shutdown();
}