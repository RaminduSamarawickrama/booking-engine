package com.booking.stripe;

import com.stripe.StripeClient;
import com.stripe.net.HttpClient;
import com.stripe.net.HttpHeaders;
import com.stripe.net.StripeRequest;
import com.stripe.net.StripeResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stands in for Stripe's API in unit tests: records every request the SDK would send and
 * replies with queued JSON. Lets tests assert exact parameters (and the absence of forbidden
 * ones such as payment_method_types) without network access or real keys.
 */
public final class FakeStripeHttp extends HttpClient {

  private final Deque<String> responses = new ArrayDeque<>();
  private final List<StripeRequest> requests = new ArrayList<>();

  public static final String TEST_KEY = "rk_test_unitTestKeyNotReal";

  public StripeClient client() {
    return StripeClient.builder().setApiKey(TEST_KEY).setHttpClient(this).build();
  }

  public FakeStripeHttp reply(String json) {
    responses.add(json);
    return this;
  }

  @Override
  public StripeResponse request(StripeRequest request) {
    requests.add(request);
    String body = responses.isEmpty() ? "{}" : responses.poll();
    return new StripeResponse(200, HttpHeaders.of(Map.of("Request-Id", List.of("req_fake"))), body);
  }

  public List<StripeRequest> requests() {
    return requests;
  }

  public StripeRequest only() {
    if (requests.size() != 1) throw new AssertionError("expected 1 request, got " + requests.size());
    return requests.get(0);
  }

  public static String path(StripeRequest request) {
    return request.url().getPath();
  }

  public static String idempotencyKey(StripeRequest request) {
    return request.headers().firstValue("Idempotency-Key").orElse(null);
  }

  public static String body(StripeRequest request) {
    return request.content() == null ? "" : request.content().stringContent();
  }

  /** Decodes a v1 form-encoded body into key/value pairs, e.g. "payment_intent_data[transfer_group]". */
  public static Map<String, String> form(StripeRequest request) {
    Map<String, String> out = new LinkedHashMap<>();
    String body = body(request);
    if (body.isEmpty()) return out;
    for (String pair : body.split("&")) {
      int eq = pair.indexOf('=');
      String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
      String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      out.put(key, value);
    }
    return out;
  }
}
