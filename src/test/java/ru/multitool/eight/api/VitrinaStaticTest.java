package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Проверка отдачи витрины: главная страница, восемь страниц проектов, гифки,
 * ассеты, health и метаданные showcases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class VitrinaStaticTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

    @Test
    @DisplayName("главная страница отдаётся с витриной")
    void indexPage() throws Exception {
        MvcResult root = this.mockMvc.perform(MockMvcRequestBuilders.get("/")).andReturn();
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/index.html")).andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(root.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(html).contains("Восемь проектов").contains("data-projects");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    @DisplayName("страница проекта открывается и содержит гифку")
    void projectPages(int number) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/projects/p" + number + ".html"))
                .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(html).contains("/gifs/p" + number + ".gif");
        assertThat(html).contains("data-call");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    @DisplayName("гифка проекта отдаётся как изображение")
    void gifs(int number) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/gifs/p" + number + ".gif"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/gif");
        assertThat(result.getResponse().getContentAsByteArray()).hasSizeGreaterThan(1000);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/assets/styles.css",
            "/assets/game.css",
            "/assets/showcase.js",
            "/assets/project.js",
            "/assets/game.js",
            "/game.html"
    })
    @DisplayName("ассеты и страница игры доступны")
    void assets(String path) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();

        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        assertThat(result.getResponse().getContentAsByteArray().length).as(path).isPositive();
    }

    @Test
    @DisplayName("actuator сообщает о состоянии UP")
    void health() throws Exception {
        var response = this.restTemplate.getForEntity("/actuator/health", String.class);

        JsonNode body = this.objectMapper.readTree(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.get("status").asText()).isEqualTo("UP");
    }

    static Stream<Arguments> missingPaths() {
        return Stream.of(
                Arguments.of("/no-such-page.html"),
                Arguments.of("/projects/p9.html"),
                Arguments.of("/gifs/p9.gif"),
                Arguments.of("/assets/missing.css")
        );
    }

    @ParameterizedTest(name = "{0} → 404")
    @MethodSource("missingPaths")
    @DisplayName("отсутствующий ресурс даёт 404 в формате ApiError")
    void missingResource(String path) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        JsonNode body = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    @DisplayName("витрина описывает каждый проект согласованно")
    void showcaseEntries(int index) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/showcase/projects"))
                .andReturn();

        JsonNode projects = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode project = projects.get(index);

        assertThat(project.get("number").asInt()).isEqualTo(index + 1);
        assertThat(project.get("key").asText()).isEqualTo("p" + (index + 1));
        assertThat(project.get("page").asText()).isEqualTo("/projects/p" + (index + 1) + ".html");
        assertThat(project.get("gif").asText()).isEqualTo("/gifs/p" + (index + 1) + ".gif");
        assertThat(project.get("accent").asText()).startsWith("#");
        assertThat(project.get("skills").size()).isGreaterThanOrEqualTo(4);
        assertThat(project.get("endpoints").size()).isGreaterThanOrEqualTo(1);
    }
}
