package org.whispersystems.textsecuregcm.workers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.experiment.PushNotificationExperiment;
import org.whispersystems.textsecuregcm.experiment.PushNotificationExperimentSamples;
import org.whispersystems.textsecuregcm.identity.IdentityType;
import org.whispersystems.textsecuregcm.metrics.MetricsUtil;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

public class StartPushNotificationExperimentCommand<T> extends AbstractSinglePassCrawlAccountsCommand {

  private final PushNotificationExperimentFactory<T> experimentFactory;

  private static final int DEFAULT_MAX_CONCURRENCY = 16;

  @VisibleForTesting
  static final String MAX_CONCURRENCY_ARGUMENT = "max-concurrency";

  private static final Counter INITIAL_SAMPLE_ALREADY_EXISTS_COUNTER =
      Metrics.counter(MetricsUtil.name(StartPushNotificationExperimentCommand.class, "initialSampleAlreadyExists"));

  private static final Logger log = LoggerFactory.getLogger(StartPushNotificationExperimentCommand.class);

  public StartPushNotificationExperimentCommand(final String name,
      final String description,
      final PushNotificationExperimentFactory<T> experimentFactory) {

    super(name, description);
    this.experimentFactory = experimentFactory;
  }

  @Override
  public void configure(final Subparser subparser) {
    super.configure(subparser);

    subparser.addArgument("--max-concurrency")
        .type(Integer.class)
        .dest(MAX_CONCURRENCY_ARGUMENT)
        .setDefault(DEFAULT_MAX_CONCURRENCY)
        .help("Max concurrency for DynamoDB operations");
  }

  @Override
  protected void crawlAccounts(final Flux<Account> accounts) {
    final int maxConcurrency = getNamespace().getInt(MAX_CONCURRENCY_ARGUMENT);

    final PushNotificationExperiment<T> experiment =
        experimentFactory.buildExperiment(getCommandDependencies(), getConfiguration());

    final PushNotificationExperimentSamples pushNotificationExperimentSamples =
        getCommandDependencies().pushNotificationExperimentSamples();

    accounts
        .flatMap(account -> Flux.fromIterable(account.getDevices())
            .map(device -> Tuples.of(account, device)))
            .filterWhen(accountAndDevice -> Mono.fromFuture(() ->
                experiment.isDeviceEligible(accountAndDevice.getT1(), accountAndDevice.getT2())),
                maxConcurrency)
        .flatMap(accountAndDevice -> {
          final UUID accountIdentifier = accountAndDevice.getT1().getIdentifier(IdentityType.ACI);
          final byte deviceId = accountAndDevice.getT2().getId();

          return Mono.fromFuture(() -> {
                try {
                  return pushNotificationExperimentSamples.recordInitialState(
                          accountIdentifier,
                          deviceId,
                          experiment.getExperimentName(),
                          isInExperimentGroup(accountIdentifier, deviceId, experiment.getExperimentName()),
                          experiment.getState(accountAndDevice.getT1(), accountAndDevice.getT2()));
                } catch (final JsonProcessingException e) {
                  throw new UncheckedIOException(e);
                }
              })
              .mapNotNull(stateStored -> {
                if (stateStored) {
                  return accountAndDevice;
                } else {
                  INITIAL_SAMPLE_ALREADY_EXISTS_COUNTER.increment();
                  return null;
                }
              })
              .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                  .onRetryExhaustedThrow(((backoffSpec, retrySignal) -> retrySignal.failure())))
              .onErrorResume(throwable -> {
                log.warn("Failed to record initial sample for {}:{} in experiment {}",
                    accountIdentifier, deviceId, experiment.getExperimentName(), throwable);

                return Mono.empty();
              });
        }, maxConcurrency)
        .flatMap(accountAndDevice -> {
          final Account account = accountAndDevice.getT1();
          final Device device = accountAndDevice.getT2();
          final boolean inExperimentGroup =
              isInExperimentGroup(account.getIdentifier(IdentityType.ACI), device.getId(), experiment.getExperimentName());

          return Mono.fromFuture(() -> inExperimentGroup
              ? experiment.applyExperimentTreatment(account, device)
              : experiment.applyControlTreatment(account, device))
              .onErrorResume(throwable -> {
                log.warn("Failed to apply {} treatment for {}:{} in experiment {}",
                    inExperimentGroup ? "experimental" : " control",
                    account.getIdentifier(IdentityType.ACI),
                    device.getId(),
                    experiment.getExperimentName(),
                    throwable);

                return Mono.empty();
              });
        }, maxConcurrency)
        .then()
        .block();
  }

  private boolean isInExperimentGroup(final UUID accountIdentifier, final byte deviceId, final String experimentName) {
    return ((accountIdentifier.hashCode() ^ Byte.hashCode(deviceId) ^ experimentName.hashCode()) & 0x01) != 0;
  }
}
