package com.omp.menu;

import static com.omp.support.TestFixtures.SHOP_CLOSED;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omp.support.TestFixtures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 HTTP로 메뉴 API를 검증한다: 단건 조회는 경로의 {id}, 생성은 JSON 본문, 목록은 id 내림차순 커서 페이지, PATCH는 보낸 필드만 변경.
 * (@PathVariable · @RequestBody 누락, 필드 없는 MenuResponse 직렬화 실패, 트랜잭션 없는 벌크 UPDATE로 깨져 있던 경로들)
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MenuControllerTest {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MenuRepository menuRepository;
    @Autowired ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clearMenus() {
        // 메뉴를 실제로 지우고 넣으므로 벤치마크 DB(OMP)에 붙지 않았는지 먼저 확인한다
        TestFixtures.assertTestDatabase(jdbc);
        jdbc.update("DELETE FROM menus");
    }

    @Test
    void 경로의_id로_메뉴를_조회한다() throws Exception {
        long id = saveMenu(SHOP_OPEN, "pizza");

        HttpResponse<String> found = send(HttpRequest.newBuilder(uri("/api/v1/menu/" + id)).GET().build());

        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).contains("\"id\":" + id);
    }

    @Test
    void JSON_본문으로_메뉴를_만든다() throws Exception {
        HttpResponse<String> created = send(HttpRequest.newBuilder(uri("/api/v1/menu"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"shopId\": " + SHOP_OPEN + ", \"name\": \"pizza\", \"price\": 15000, \"quantity\": 30, \"description\": \"cheese\"}"))
                .build());

        assertThat(created.statusCode()).isEqualTo(200);
        long id = Long.parseLong(created.body());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT shop_id, name, price, quantity, description FROM menus WHERE menu_id = ?", id);
        assertThat(row)
                .containsEntry("shop_id", SHOP_OPEN)
                .containsEntry("name", "pizza")
                .containsEntry("price", 15000)
                .containsEntry("quantity", 30)
                .containsEntry("description", "cheese");
    }

    @Test
    void 목록은_가게의_메뉴를_id_내림차순_커서_페이지로_준다() throws Exception {
        long first = saveMenu(SHOP_OPEN, "a");
        long second = saveMenu(SHOP_OPEN, "b");
        long third = saveMenu(SHOP_OPEN, "c");
        saveMenu(SHOP_CLOSED, "other shop");

        JsonNode page1 = getJson("/api/v1/menu?shopId=" + SHOP_OPEN + "&pageSize=2");
        assertThat(ids(page1)).containsExactly(third, second);
        assertThat(page1.get("last").asBoolean()).isFalse();
        JsonNode menu = page1.get("content").get(0);
        assertThat(menu.get("name").asText()).isEqualTo("c");
        assertThat(menu.get("price").asInt()).isEqualTo(15000);
        assertThat(menu.get("isSoldOut").asBoolean()).isFalse();
        assertThat(menu.get("description").asText()).isEqualTo("cheese");

        JsonNode page2 = getJson("/api/v1/menu?shopId=" + SHOP_OPEN + "&cursor=" + second + "&pageSize=2");
        assertThat(ids(page2)).containsExactly(first);
        assertThat(page2.get("last").asBoolean()).isTrue();
    }

    @Test
    void PATCH는_보낸_필드만_바꾼다() throws Exception {
        long id = saveMenu(SHOP_OPEN, "pizza");

        HttpResponse<String> patched = send(HttpRequest.newBuilder(uri("/api/v1/menu/" + id))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"price\": 17000, \"isSoldOut\": true}"))
                .build());

        assertThat(patched.statusCode()).isEqualTo(204);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT name, price, quantity, description FROM menus WHERE menu_id = ?", id);
        assertThat(row)
                .containsEntry("name", "pizza")
                .containsEntry("price", 17000)
                .containsEntry("quantity", 30)
                .containsEntry("description", "cheese");
        assertThat(jdbc.queryForObject("SELECT is_sold_out FROM menus WHERE menu_id = ?", Boolean.class, id)).isTrue();
    }

    private long saveMenu(long shopId, String name) {
        return menuRepository.save(new Menu(shopId, 30, name, 15000, "cheese")).getId();
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> res = send(HttpRequest.newBuilder(uri(path)).GET().build());
        assertThat(res.statusCode()).isEqualTo(200);
        return objectMapper.readTree(res.body());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(m -> ids.add(m.get("id").asLong()));
        return ids;
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
