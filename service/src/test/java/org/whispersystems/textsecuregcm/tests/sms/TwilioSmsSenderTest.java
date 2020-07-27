package org.whispersystems.textsecuregcm.tests.sms;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.textsecuregcm.configuration.TwilioConfiguration;
import org.whispersystems.textsecuregcm.configuration.TwilioCountrySenderIdConfiguration;
import org.whispersystems.textsecuregcm.configuration.TwilioSenderIdConfiguration;
import org.whispersystems.textsecuregcm.sms.TwilioSmsSender;

import javax.annotation.Nonnull;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class TwilioSmsSenderTest {

  private static final String       ACCOUNT_ID            = "test_account_id";
  private static final String       ACCOUNT_TOKEN         = "test_account_token";
  private static final List<String> NUMBERS               = List.of("+14151111111", "+14152222222");
  private static final String       MESSAGING_SERVICES_ID = "test_messaging_services_id";
  private static final String       LOCAL_DOMAIN          = "test.com";

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort().dynamicHttpsPort());

  @Nonnull
  private TwilioConfiguration createTwilioConfiguration() {
    TwilioConfiguration configuration = new TwilioConfiguration();
    configuration.setAccountId(ACCOUNT_ID);
    configuration.setAccountToken(ACCOUNT_TOKEN);
    configuration.setNumbers(NUMBERS);
    configuration.setMessagingServicesId(MESSAGING_SERVICES_ID);
    configuration.setLocalDomain(LOCAL_DOMAIN);
    configuration.setIosVerificationText("Verify on iOS: %1$s\n\nsomelink://verify/%1$s");
    configuration.setAndroidNgVerificationText("<#> Verify on AndroidNg: %1$s\n\ncharacters");
    configuration.setAndroid202001VerificationText("Verify on Android202001: %1$s\n\nsomelink://verify/%1$s\n\ncharacters");
    configuration.setGenericVerificationText("Verify on whatever: %1$s");
    return configuration;
  }

  @Test
  public void testSendSms() {
    wireMockRule.stubFor(post(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
                             .withBasicAuth(ACCOUNT_ID, ACCOUNT_TOKEN)
                             .willReturn(aResponse()
                                             .withHeader("Content-Type", "application/json")
                                             .withBody("{\"price\": -0.00750, \"status\": \"sent\"}")));


    TwilioConfiguration configuration = createTwilioConfiguration();
    TwilioSmsSender sender  = new TwilioSmsSender("http://localhost:" + wireMockRule.port(), configuration);
    boolean         success = sender.deliverSmsVerification("+14153333333", Optional.of("android-ng"), "123-456").join();

    assertThat(success).isTrue();

    verify(1, postRequestedFor(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(equalTo("MessagingServiceSid=test_messaging_services_id&To=%2B14153333333&Body=%3C%23%3E+Verify+on+AndroidNg%3A+123-456%0A%0Acharacters")));
  }

  @Test
  public void testSendSmsAndroid202001() {
    wireMockRule.stubFor(post(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
                                 .withBasicAuth(ACCOUNT_ID, ACCOUNT_TOKEN)
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("{\"price\": -0.00750, \"status\": \"sent\"}")));

    TwilioConfiguration configuration = createTwilioConfiguration();
    TwilioSmsSender sender = new TwilioSmsSender("http://localhost:" + wireMockRule.port(), configuration);
    boolean success = sender.deliverSmsVerification("+14153333333", Optional.of("android-2020-01"), "123-456").join();

    assertThat(success).isTrue();

    verify(1, postRequestedFor(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
            .withRequestBody(equalTo("MessagingServiceSid=test_messaging_services_id&To=%2B14153333333&Body=Verify+on+Android202001%3A+123-456%0A%0Asomelink%3A%2F%2Fverify%2F123-456%0A%0Acharacters")));
  }

  @Test
  public void testSendVox() {
    wireMockRule.stubFor(post(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Calls.json"))
                             .withBasicAuth(ACCOUNT_ID, ACCOUNT_TOKEN)
                             .willReturn(aResponse()
                                             .withHeader("Content-Type", "application/json")
                                             .withBody("{\"price\": -0.00750, \"status\": \"completed\"}")));


    TwilioConfiguration configuration = createTwilioConfiguration();

    TwilioSmsSender sender  = new TwilioSmsSender("http://localhost:" + wireMockRule.port(), configuration);
    boolean         success = sender.deliverVoxVerification("+14153333333", "123-456", Optional.of("en_US")).join();

    assertThat(success).isTrue();

    verify(1, postRequestedFor(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Calls.json"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(matching("To=%2B14153333333&From=%2B1415(1111111|2222222)&Url=https%3A%2F%2Ftest.com%2Fv1%2Fvoice%2Fdescription%2F123-456%3Fl%3Den_US")));
  }

  @Test
  public void testSendSmsFiveHundred() {
    wireMockRule.stubFor(post(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
                             .withBasicAuth(ACCOUNT_ID, ACCOUNT_TOKEN)
                             .willReturn(aResponse()
                                             .withStatus(500)
                                             .withHeader("Content-Type", "application/json")
                                             .withBody("{\"message\": \"Server error!\"}")));


    TwilioConfiguration configuration = createTwilioConfiguration();

    TwilioSmsSender sender  = new TwilioSmsSender("http://localhost:" + wireMockRule.port(), configuration);
    boolean         success = sender.deliverSmsVerification("+14153333333", Optional.of("android-ng"), "123-456").join();

    assertThat(success).isFalse();

    verify(3, postRequestedFor(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(equalTo("MessagingServiceSid=test_messaging_services_id&To=%2B14153333333&Body=%3C%23%3E+Verify+on+AndroidNg%3A+123-456%0A%0Acharacters")));
  }

  @Test
  public void testSendVoxFiveHundred() {
    wireMockRule.stubFor(post(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Calls.json"))
                             .withBasicAuth(ACCOUNT_ID, ACCOUNT_TOKEN)
                             .willReturn(aResponse()
                                             .withStatus(500)
                                             .withHeader("Content-Type", "application/json")
                                             .withBody("{\"message\": \"Server error!\"}")));

    TwilioConfiguration configuration = createTwilioConfiguration();

    TwilioSmsSender sender  = new TwilioSmsSender("http://localhost:" + wireMockRule.port(), configuration);
    boolean         success = sender.deliverVoxVerification("+14153333333", "123-456", Optional.of("en_US")).join();

    assertThat(success).isFalse();

    verify(3, postRequestedFor(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Calls.json"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(matching("To=%2B14153333333&From=%2B1415(1111111|2222222)&Url=https%3A%2F%2Ftest.com%2Fv1%2Fvoice%2Fdescription%2F123-456%3Fl%3Den_US")));

  }

  @Test
  public void testSendSmsNetworkFailure() {
    TwilioConfiguration configuration = createTwilioConfiguration();

    TwilioSmsSender sender  = new TwilioSmsSender("http://localhost:" + 39873, configuration);
    boolean         success = sender.deliverSmsVerification("+14153333333", Optional.of("android-ng"), "123-456").join();

    assertThat(success).isFalse();
  }

  private void runSenderIdTest(String destination,
                               String expectedSenderId,
                               Supplier<TwilioSenderIdConfiguration> senderIdConfigurationSupplier) {
    wireMockRule.stubFor(post(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
                                 .withBasicAuth(ACCOUNT_ID, ACCOUNT_TOKEN)
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("{\"price\": -0.00750, \"status\": \"sent\"}")));

    TwilioConfiguration configuration = createTwilioConfiguration();
    configuration.setSenderId(senderIdConfigurationSupplier.get());

    TwilioSmsSender sender  = new TwilioSmsSender("http://localhost:" + wireMockRule.port(), configuration);
    boolean         success = sender.deliverSmsVerification(destination, Optional.of("android-ng"), "987-654").join();

    assertThat(success).isTrue();

    final String requestBodyToParam = "To=" + URLEncoder.encode(destination, StandardCharsets.UTF_8);
    final String requestBodySuffix  = "&Body=%3C%23%3E+Verify+on+AndroidNg%3A+987-654%0A%0Acharacters";
    final String expectedRequestBody;
    if (expectedSenderId != null) {
      expectedRequestBody = requestBodyToParam + "&From=" + expectedSenderId + requestBodySuffix;
    } else {
      expectedRequestBody = "MessagingServiceSid=" + MESSAGING_SERVICES_ID + "&" + requestBodyToParam + requestBodySuffix;
    }
    verify(1, postRequestedFor(urlEqualTo("/2010-04-01/Accounts/" + ACCOUNT_ID + "/Messages.json"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
            .withRequestBody(equalTo(expectedRequestBody)));
  }

  @Test
  public void testSendAlphaIdByCountryCode() {
    runSenderIdTest("+85278675309", "SIGNAL", () -> {
      TwilioSenderIdConfiguration        senderIdConfiguration              = new TwilioSenderIdConfiguration();
      TwilioCountrySenderIdConfiguration twilioCountrySenderIdConfiguration = new TwilioCountrySenderIdConfiguration();
      twilioCountrySenderIdConfiguration.setCountryCode("852");
      twilioCountrySenderIdConfiguration.setSenderId("SIGNAL");
      senderIdConfiguration.setCountrySpecificSenderIds(List.of(twilioCountrySenderIdConfiguration));
      return senderIdConfiguration;
    });
  }

  @Test
  public void testDefaultSenderId() {
    runSenderIdTest("+14098675309", "SIGNALFOO", () -> {
      TwilioSenderIdConfiguration senderIdConfiguration = new TwilioSenderIdConfiguration();
      senderIdConfiguration.setDefaultSenderId("SIGNALFOO");
      return senderIdConfiguration;
    });
  }

  @Test
  public void testDefaultSenderIdWithDisabledCountry() {
    final Supplier<TwilioSenderIdConfiguration> senderIdConfigurationSupplier = () -> {
      TwilioSenderIdConfiguration senderIdConfiguration = new TwilioSenderIdConfiguration();
      senderIdConfiguration.setDefaultSenderId("SIGNALBAR");
      senderIdConfiguration.setCountryCodesWithoutSenderId(Set.of("1"));
      return senderIdConfiguration;
    };
    runSenderIdTest("+14098675309", null, senderIdConfigurationSupplier);
    runSenderIdTest("+447911123456", "SIGNALBAR", senderIdConfigurationSupplier);
  }

  @Test
  public void testDefaultSenderIdWithOverriddenCountry() {
    final Supplier<TwilioSenderIdConfiguration> senderIdConfigurationSupplier = () -> {
      TwilioSenderIdConfiguration        senderIdConfiguration              = new TwilioSenderIdConfiguration();
      TwilioCountrySenderIdConfiguration twilioCountrySenderIdConfiguration = new TwilioCountrySenderIdConfiguration();
      twilioCountrySenderIdConfiguration.setCountryCode("1");
      twilioCountrySenderIdConfiguration.setSenderId("OFCOURSEISTILLLOVEYOU");
      senderIdConfiguration.setDefaultSenderId("JUSTREADTHEINSTRUCTIONS");
      senderIdConfiguration.setCountrySpecificSenderIds(List.of(twilioCountrySenderIdConfiguration));
      return senderIdConfiguration;
    };
    runSenderIdTest("+15128675309", "OFCOURSEISTILLLOVEYOU", senderIdConfigurationSupplier);
    runSenderIdTest("+6433456789", "JUSTREADTHEINSTRUCTIONS", senderIdConfigurationSupplier);
  }

  @Test
  public void testSenderIdWithAllFieldsPopulated() {
    final Supplier<TwilioSenderIdConfiguration> senderIdConfigurationSupplier = () -> {
      TwilioSenderIdConfiguration        senderIdConfiguration               = new TwilioSenderIdConfiguration();
      TwilioCountrySenderIdConfiguration twilioCountrySenderIdConfiguration1 = new TwilioCountrySenderIdConfiguration();
      TwilioCountrySenderIdConfiguration twilioCountrySenderIdConfiguration2 = new TwilioCountrySenderIdConfiguration();
      twilioCountrySenderIdConfiguration1.setCountryCode("1");
      twilioCountrySenderIdConfiguration1.setSenderId("KNOWYOUREOUTTHERE");
      twilioCountrySenderIdConfiguration2.setCountryCode("61");
      twilioCountrySenderIdConfiguration2.setSenderId("SOMEWHERE");
      senderIdConfiguration.setDefaultSenderId("SOMEHOW");
      senderIdConfiguration.setCountrySpecificSenderIds(List.of(twilioCountrySenderIdConfiguration1, twilioCountrySenderIdConfiguration2));
      senderIdConfiguration.setCountryCodesWithoutSenderId(Set.of("7"));
      return senderIdConfiguration;
    };
    runSenderIdTest("+15128675309", "KNOWYOUREOUTTHERE", senderIdConfigurationSupplier);
    runSenderIdTest("+610998765432", "SOMEWHERE", senderIdConfigurationSupplier);
    runSenderIdTest("+74991234567", null, senderIdConfigurationSupplier);
    runSenderIdTest("+85278675309", "SOMEHOW", senderIdConfigurationSupplier);
  }
}
