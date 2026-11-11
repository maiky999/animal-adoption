package com.wsb.animaladoption.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the multipart limits on a real embedded Tomcat (MockMvc does not parse multipart requests like Tomcat does).
 * <p>
 * In a multipart/form-data request every form field is a separate "part" - not only the files.
 * The listing form has about 20 fields (title, animal information, checkboxes with their hidden "_field" markers...),
 * while Tomcat's default limit is 10 parts. Exceeding it throws FileCountLimitExceededException already
 * in the CSRF filter, so the user sees an error page instead of the form.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
class MultipartLimitsTest {

    /** Roughly the listing form with all checkboxes ticked and a few photos. */
    private static final int FORM_PARTS = 25;

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("A listing form with ~25 fields must not be rejected by Tomcat's multipart part limit")
    void shouldAcceptListingFormWithManyFields() throws Exception {
        String boundary = "----LapkaTestBoundary";
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < FORM_PARTS; i++) {
            body.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"field").append(i).append("\"\r\n\r\n")
                    .append("value").append("\r\n");
        }
        body.append("--").append(boundary).append("--\r\n");

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ads/new"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // The request has no CSRF token, so 403 is the correct answer - it means Tomcat parsed all the parts
        // and the request reached the CSRF check. Before the fix the limit broke the request first (HTTP 500).
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
