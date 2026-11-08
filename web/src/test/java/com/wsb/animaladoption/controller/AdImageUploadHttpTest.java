package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.CategoryRepository;
import com.wsb.animaladoption.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same as a user in the browser: log in, open the "add listing" form, send it with a photo -
 * over real HTTP to a real embedded Tomcat (multipart parsing, CSRF, session), not through MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
class AdImageUploadHttpTest {

    private static final String EMAIL = "upload-http@example.com";
    private static final String PASSWORD = "bezpieczneHaslo123";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> uploadDir.toString());
    }

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AdRepository adRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @AfterEach
    void cleanUp() {
        adRepository.deleteAll(adRepository.findAllByAuthorEmailOrderByCreatedAtDesc(EMAIL));
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Browser-like flow: the photo of a new listing is saved")
    void shouldSavePhotoSentFromTheForm() throws Exception {
        userRepository.save(User.builder()
                .email(EMAIL).passwordHash(passwordEncoder.encode(PASSWORD)).displayName("Autor").mobile("123456789")
                .role(RoleEnum.USER).userType(UserTypeEnum.PRIVATE).isEmailVerified(true).build());
        Category category = categoryRepository.save(new Category(null, "Psy-http"));

        // 1. Log in like the login form does
        String loginToken = csrfFrom(get("/login"));
        HttpResponse<String> login = send(HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + enc(EMAIL) + "&password=" + enc(PASSWORD)
                        + "&_csrf=" + enc(loginToken))));
        assertThat(login.headers().firstValue("Location").orElse("")).doesNotContain("error");

        // 2. Open the form and send it - fields in the same order and with the same names as ads/form.html
        String formToken = csrfFrom(get("/ads/new"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("_csrf", formToken);
        fields.put("title", "Burek szuka domu");
        fields.put("categoryId", category.getId().toString());
        fields.put("location", "Kraków");
        fields.put("age", "");
        fields.put("sex", "");
        fields.put("size", "");
        for (String feature : List.of("vaccinated", "sterilized", "chipped", "goodWithKids", "goodWithAnimals")) {
            fields.put("_" + feature, "on");
        }
        fields.put("description", "Spokojny pies szuka kochającego domu.");

        String boundary = "----LapkaBoundary";
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/ads/new"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipart(boundary, fields, "imageFiles", "burek.jpg", new byte[24 * 1024]))));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/my-ads");

        List<Ad> ads = adRepository.findAllByAuthorEmailOrderByCreatedAtDesc(EMAIL);
        assertThat(ads).hasSize(1);
        // imageUrls is a lazy collection - read it inside a transaction
        int imageCount = transactionTemplate.execute(status ->
                adRepository.findById(ads.get(0).getId()).orElseThrow().getImageUrls().size());
        assertThat(imageCount).isEqualTo(1);
        try (var files = Files.list(uploadDir)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    // --- tiny HTTP helpers ---

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String get(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET()).body();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfFrom(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertThat(matcher.find()).as("CSRF token in the page").isTrue();
        return matcher.group(1);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] multipart(String boundary, Map<String, String> fields,
                                    String fileField, String fileName, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                + fileName + "\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
