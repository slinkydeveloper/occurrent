package org.occurrent.application.service.blocking.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.occurrent.application.converter.CloudEventConverter;
import org.occurrent.application.converter.implementation.GenericCloudEventConverter;
import org.occurrent.application.service.blocking.ApplicationService;
import org.occurrent.application.service.blocking.PolicySideEffect;
import org.occurrent.application.service.blocking.implementation.support.CountNumberOfNamesDefinedPolicy;
import org.occurrent.application.service.blocking.implementation.support.WhenNameDefinedThenCountAverageSizeOfNamePolicy;
import org.occurrent.domain.DomainEvent;
import org.occurrent.domain.DomainEventConverter;
import org.occurrent.domain.Name;
import org.occurrent.domain.NameDefined;
import org.occurrent.eventstore.inmemory.InMemoryEventStore;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.occurrent.application.composition.command.CommandConversion.toStreamCommand;
import static org.occurrent.application.service.blocking.PolicySideEffect.executePolicy;

@DisplayName("generic application service")
public class GenericApplicationServiceTest {

    private ApplicationService<DomainEvent> applicationService;
    private InMemoryEventStore eventStore;

    @BeforeEach
    void initialize_application_service() {
        DomainEventConverter domainEventConverter = new DomainEventConverter(new ObjectMapper());
        CloudEventConverter<DomainEvent> cloudEventConverter = new GenericCloudEventConverter<>(domainEventConverter::convertToDomainEvent, domainEventConverter::convertToCloudEvent);
        eventStore = new InMemoryEventStore();
        applicationService = new GenericApplicationService<>(eventStore, cloudEventConverter);
    }

    @Nested
    @DisplayName("side effects")
    class SideEffectsTest {

        @Test
        void are_executed_after_call_to_execute() {
            // Given
            WhenNameDefinedThenCountAverageSizeOfNamePolicy averageSizePolicy = new WhenNameDefinedThenCountAverageSizeOfNamePolicy();

            // When
            PolicySideEffect<DomainEvent> sideEffect = executePolicy(NameDefined.class, averageSizePolicy::whenNameDefinedThenCountAverageSizeOfName);
            applicationService.execute(UUID.randomUUID(),
                    toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Johan")),
                    sideEffect);

            applicationService.execute(UUID.randomUUID(),
                    toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Tina")),
                    sideEffect);

            applicationService.execute(UUID.randomUUID(),
                    toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Abbe")),
                    sideEffect);

            applicationService.execute(UUID.randomUUID(),
                    toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Agnes")),
                    sideEffect);

            // Then
            assertThat(averageSizePolicy.getAverageSizeOfName()).isEqualTo(4);
        }

        @Test
        void are_composable_using_and_then_execute_another_policy() {
            // Given
            CountNumberOfNamesDefinedPolicy countPolicy = new CountNumberOfNamesDefinedPolicy();
            WhenNameDefinedThenCountAverageSizeOfNamePolicy averageSizePolicy = new WhenNameDefinedThenCountAverageSizeOfNamePolicy();

            PolicySideEffect<DomainEvent> policy = PolicySideEffect.<DomainEvent, NameDefined>executePolicy(NameDefined.class, averageSizePolicy::whenNameDefinedThenCountAverageSizeOfName)
                    .andThenExecuteAnotherPolicy(NameDefined.class, countPolicy::whenNameDefinedThenCountHowManyNamesThatHaveBeenDefined);

            // When
            applicationService.execute(UUID.randomUUID(),
                    toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Johan")),
                    policy);

            applicationService.execute(UUID.randomUUID(),
                    toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Agnes")),
                    policy);

            // Then
            assertAll(
                    () -> assertThat(averageSizePolicy.getAverageSizeOfName()).isEqualTo(5),
                    () -> assertThat(countPolicy.getNumberOfNamesDefined()).isEqualTo(2)
            );
        }

        @Test
        void are_not_executed_when_event_is_not_in_stream() {
            // Given
            WhenNameDefinedThenCountAverageSizeOfNamePolicy averageSizePolicy = new WhenNameDefinedThenCountAverageSizeOfNamePolicy();
            UUID streamId = UUID.randomUUID();

            // When
            applicationService.execute(streamId, toStreamCommand(events -> Name.defineName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Johan")));

            applicationService.execute(streamId,
                    toStreamCommand(events -> Name.changeName(events, UUID.randomUUID().toString(), LocalDateTime.now(), "Tina")),
                    executePolicy(NameDefined.class, averageSizePolicy::whenNameDefinedThenCountAverageSizeOfName));

            // Then
            assertThat(averageSizePolicy.getAverageSizeOfName()).isEqualTo(0);
        }
    }
}