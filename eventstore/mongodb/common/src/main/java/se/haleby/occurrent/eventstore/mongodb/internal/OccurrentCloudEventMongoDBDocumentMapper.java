package se.haleby.occurrent.eventstore.mongodb.internal;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import org.bson.Document;
import se.haleby.occurrent.eventstore.mongodb.TimeRepresentation;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.MILLIS;
import static se.haleby.occurrent.cloudevents.OccurrentCloudEventExtension.STREAM_ID;
import static se.haleby.occurrent.eventstore.mongodb.TimeRepresentation.DATE;
import static se.haleby.occurrent.eventstore.mongodb.TimeRepresentation.RFC_3339_STRING;
import static se.haleby.occurrent.eventstore.mongodb.internal.RFC3339.RFC_3339_DATE_TIME_FORMATTER;

public class OccurrentCloudEventMongoDBDocumentMapper {

    public static Document convertToDocument(EventFormat eventFormat, TimeRepresentation timeRepresentation, String streamId, CloudEvent cloudEvent) {
        final CloudEvent cloudEventToUse = timeRepresentation == RFC_3339_STRING ? fixTimestamp(cloudEvent) : cloudEvent;
        byte[] bytes = eventFormat.serialize(cloudEventToUse);
        String serializedAsString = new String(bytes, UTF_8);
        Document cloudEventDocument = Document.parse(serializedAsString);
        cloudEventDocument.put(STREAM_ID, streamId);

        if (timeRepresentation == DATE && cloudEvent.getTime() != null) {
            ZonedDateTime time = cloudEvent.getTime();
            if (!time.truncatedTo(MILLIS).equals(time)) {
                throw new IllegalArgumentException("The " + ZonedDateTime.class.getSimpleName() + " in the CloudEvent time field contains micro-/nanoseconds. " +
                        "This is is not possible to represent when using " + TimeRepresentation.class.getSimpleName() + " " + DATE.name() +
                        ", either change to " + TimeRepresentation.class.getSimpleName() + " " + RFC_3339_STRING.name() +
                        " or remove micro-/nanoseconds using \"zonedDateTime.truncatedTo(ChronoUnit.MILLIS)\".");
            } else if (!time.equals(time.withZoneSameInstant(UTC))) {
                throw new IllegalArgumentException("The " + ZonedDateTime.class.getSimpleName() + " in the CloudEvent time field is not defined using UTC. " +
                        TimeRepresentation.class.getSimpleName() + " " + DATE.name() + " require UTC as timezone to not loose precision. " +
                        "Either change to " + TimeRepresentation.class.getSimpleName() + " " + RFC_3339_STRING.name() +
                        " or convert the " + ZonedDateTime.class.getSimpleName() + " to UTC using e.g. \"zonedDateTime.withZoneSameInstant(ZoneOffset.UTC)\".");
            }

            // Convert date string to a date in order to be able to perform date/time queries on the "time" property name
            Date date = Date.from(time.toInstant());
            cloudEventDocument.put("time", date);
        }

        return cloudEventDocument;
    }

    public static CloudEvent convertToCloudEvent(EventFormat eventFormat, TimeRepresentation timeRepresentation, Document cloudEventDocument) {
        Document document = new Document(cloudEventDocument);
        document.remove("_id");

        if (timeRepresentation == DATE) {
            Object time = document.get("time"); // Be a bit nice and don't enforce Date here if TimeRepresentation has been changed
            if (time instanceof Date) {
                Date timeAsDate = (Date) time;
                ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(timeAsDate.toInstant(), UTC);
                String format = RFC_3339_DATE_TIME_FORMATTER.format(zonedDateTime);
                document.put("time", format);
            }
        }
        String eventJsonString = document.toJson();
        byte[] eventJsonBytes = eventJsonString.getBytes(UTF_8);
        return eventFormat.deserialize(eventJsonBytes);
    }

    // Creates a workaround for issue 200: https://github.com/cloudevents/sdk-java/issues/200
    // Remove when milestone 2 is released
    private static CloudEvent fixTimestamp(CloudEvent cloudEvent) {
        if (cloudEvent.getTime() == null) {
            return cloudEvent;
        }
        return CloudEventBuilder.v1(cloudEvent)
                .withTime(OffsetDateTime.from(cloudEvent.getTime()).toZonedDateTime())
                .build();
    }
}