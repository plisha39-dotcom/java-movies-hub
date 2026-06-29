package ru.practicum.moviehub.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    private void assertJsonContentType(HttpResponse<String> resp) {
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
    }

    private JsonObject parseObject(HttpResponse<String> resp) {
        String body = resp.body().trim();
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private JsonArray parseArray(HttpResponse<String> resp) {
        String body = resp.body().trim();
        return JsonParser.parseString(body).getAsJsonArray();
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest getRequest(String path) {
        return HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build();
    }

    private HttpRequest deleteRequest(String path) {
        return HttpRequest.newBuilder().uri(URI.create(BASE + path)).DELETE().build();
    }

    private HttpRequest postRequest(String path, String json, String contentType) {
        return HttpRequest.newBuilder().uri(URI.create(BASE + path)).timeout(Duration.ofSeconds(2)).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
    }

    private HttpRequest postJsonRequest(String path, String json) {
        return postRequest(path, json, "application/json; charset=UTF-8");
    }

    private void assertMovie(JsonObject object, int expectedId, String expectedTitle, int expectedYear) {
        assertEquals(expectedId, object.get("id").getAsInt(), "Неверный id фильма");
        assertEquals(expectedTitle, object.get("title").getAsString(), "Неверное название фильма");
        assertEquals(expectedYear, object.get("year").getAsInt(), "Неверный год фильма");
    }

    private void assertError(HttpResponse<String> resp, String expectedError) {
        JsonObject object = parseObject(resp);
        assertEquals(expectedError, object.get("error").getAsString(), "Неверный текст ошибки");
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = getRequest("/movies");

        HttpResponse<String> resp = send(req);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertEquals("[]", body, "Должен вернуться пустой массив");
    }

    @Test
    void getMovies_whenStoreHasMovie_returnsMovieArray() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = getRequest("/movies");

        HttpResponse<String> resp = send(req);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        assertJsonContentType(resp);

        JsonArray array = parseArray(resp);

        assertEquals(1, array.size(), "Ожидается 1 фильм в массиве");

        JsonObject movieJson = array.get(0).getAsJsonObject();

        assertMovie(movieJson, 1, "Интерстеллар", 2014);
    }

    @Test
    void postMovies_whenValidRequest_createsMovie() throws Exception {
        String json = "{\"title\":\"Интерстеллар\",\"year\":2014}";
        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertMovie(object, 1, "Интерстеллар", 2014);
    }

    @Test
    void postMovies_whenContentTypeIsNotJson_returns415() throws Exception {
        String json = "{\"title\":\"Интерстеллар\",\"year\":2014}";
        HttpRequest req = postRequest("/movies", json, "text/plain; charset=UTF-8");

        HttpResponse<String> resp = send(req);

        assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415");

        assertJsonContentType(resp);

        assertError(resp, "Неподдерживаемый Content-Type");
    }

    @Test
    void postMovies_whenTitleIsEmpty_returns422() throws Exception {
        String json = "{\"title\":\"\",\"year\":2014}";
        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(422, resp.statusCode(), "POST /movies с пустым title должен вернуть 422");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertError(resp, "Ошибка валидации");
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("название не должно быть пустым", details.get(0).getAsString());
    }

    @Test
    void postMovies_whenTitleIsBlank_returns422() throws Exception {
        String json = "{\"title\":\"  \",\"year\":2014}";
        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(422, resp.statusCode(), "POST /movies с title из пробелов должен вернуть 422");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertError(resp, "Ошибка валидации");
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("название не должно быть пустым", details.get(0).getAsString());
    }

    @Test
    void postMovies_whenTitleIsTooLong_returns422() throws Exception {
        String longTitle = "А".repeat(101);

        String json = "{\"title\":\"%s\",\"year\":2014}".formatted(longTitle);

        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(422, resp.statusCode(), "POST /movies с title > 100 должен вернуть 422");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertError(resp, "Ошибка валидации");
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("название не должно быть длиннее 100 символов", details.get(0).getAsString());
    }

    @Test
    void postMovies_whenYearIsBefore1888_returns422() throws Exception {
        String json = "{\"title\":\"Интерстеллар\",\"year\":1887}";
        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(422, resp.statusCode(), "POST /movies с невалидным year должен вернуть 422");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertError(resp, "Ошибка валидации");
        JsonArray details = object.get("details").getAsJsonArray();

        int minYear = 1888;
        int maxYear = LocalDate.now().getYear() + 1;

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("год должен быть между " + minYear + " и " + maxYear, details.get(0).getAsString());
    }

    @Test
    void postMovies_whenYearIsAfterNextYear_returns422() throws Exception {
        int maxYear = LocalDate.now().getYear() + 1;
        int invalidYear = maxYear + 1;
        int minYear = 1888;

        String json = "{\"title\":\"Интерстеллар\",\"year\":%d}".formatted(invalidYear);

        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(422, resp.statusCode(), "POST /movies с невалидным year должен вернуть 422");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertError(resp, "Ошибка валидации");
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("год должен быть между " + minYear + " и " + maxYear, details.get(0).getAsString());
    }

    @Test
    void postMovies_whenTitleAndYearAreInvalid_returnsAllValidationErrors() throws Exception {
        String json = "{\"title\":\"\",\"year\":1887}";
        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(422, resp.statusCode(), "POST /movies с невалидным year должен вернуть 422");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertError(resp, "Ошибка валидации");
        JsonArray details = object.get("details").getAsJsonArray();

        int minYear = 1888;
        int maxYear = LocalDate.now().getYear() + 1;

        assertEquals(2, details.size(), "Должно быть две ошибки валидации");
        assertEquals("название не должно быть пустым", details.get(0).getAsString());
        assertEquals("год должен быть между " + minYear + " и " + maxYear, details.get(1).getAsString());
    }

    @Test
    void postMovies_whenJsonIsMalformed_returns400() throws Exception {
        String json = "{\"title\":\"Интерстеллар\",\"year\":2014";
        HttpRequest req = postJsonRequest("/movies", json);

        HttpResponse<String> resp = send(req);

        assertEquals(400, resp.statusCode(), "POST /movies с невалидным JSON должен вернуть 400");

        assertJsonContentType(resp);

        assertError(resp, "Некорректный JSON");
    }

    @Test
    void getMovieById_whenMovieExists_returnsMovie() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = getRequest("/movies/1");

        HttpResponse<String> resp = send(req);

        assertEquals(200, resp.statusCode(), "GET /movies/1 должен вернуть 200");

        assertJsonContentType(resp);

        JsonObject object = parseObject(resp);

        assertMovie(object, 1, "Интерстеллар", 2014);
    }

    @Test
    void getMovieById_whenMovieNotFound_returns404() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = getRequest("/movies/999");

        HttpResponse<String> resp = send(req);

        assertEquals(404, resp.statusCode(), "GET /movies/999 должен вернуть 404");

        assertJsonContentType(resp);


        assertError(resp, "Фильм не найден");
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = getRequest("/movies/abc");

        HttpResponse<String> resp = send(req);

        assertEquals(400, resp.statusCode(), "GET /movies/abc должен вернуть 400");

        assertJsonContentType(resp);

        assertError(resp, "Некорректный ID");
    }

    @Test
    void deleteMovieById_whenMovieExists_deletesMovie() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = deleteRequest("/movies/1");

        HttpResponse<String> resp = send(req);

        assertEquals(204, resp.statusCode(), "DELETE /movies/1 должен вернуть 204");

        HttpRequest req1 = getRequest("/movies/1");

        HttpResponse<String> resp1 = send(req1);

        assertEquals(404, resp1.statusCode(), "GET /movies/1 должен вернуть 404");
    }

    @Test
    void deleteMovieById_whenMovieNotFound_returns404() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = deleteRequest("/movies/999");

        HttpResponse<String> resp = send(req);

        assertEquals(404, resp.statusCode(), "DELETE /movies/999 должен вернуть 404");

        assertJsonContentType(resp);

        assertError(resp, "Фильм не найден");
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = deleteRequest("/movies/abc");

        HttpResponse<String> resp = send(req);

        assertEquals(400, resp.statusCode(), "DELETE /movies/abc должен вернуть 400");

        assertJsonContentType(resp);

        assertError(resp, "Некорректный ID");
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredMovies() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        Movie movie1 = new Movie(2, "Начало", 2010);
        Movie movie2 = new Movie(3, "Дюна", 2014);
        store.addMovie(movie);
        store.addMovie(movie1);
        store.addMovie(movie2);

        HttpRequest req = getRequest("/movies?year=2014");

        HttpResponse<String> resp = send(req);

        assertEquals(200, resp.statusCode(), "GET /movies?year=2014 должен вернуть 200");

        assertJsonContentType(resp);

        JsonArray array = parseArray(resp);

        assertEquals(2, array.size(), "Ожидается 2 фильма в массиве");

        JsonObject movieJson = array.get(0).getAsJsonObject();

        assertMovie(movieJson, 1, "Интерстеллар", 2014);

        JsonObject movieJson1 = array.get(1).getAsJsonObject();

        assertMovie(movieJson1, 3, "Дюна", 2014);
    }

    @Test
    void getMoviesByYear_whenNoMoviesForYear_returnsEmptyArray() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        Movie movie1 = new Movie(2, "Начало", 2010);
        Movie movie2 = new Movie(3, "Дюна", 2014);
        store.addMovie(movie);
        store.addMovie(movie1);
        store.addMovie(movie2);

        HttpRequest req = getRequest("/movies?year=2000");

        HttpResponse<String> resp = send(req);

        assertEquals(200, resp.statusCode(), "GET /movies?year=2000 должен вернуть 200");

        assertJsonContentType(resp);

        JsonArray array = parseArray(resp);

        assertEquals(0, array.size(), "Ожидается пустой массив");
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest req = getRequest("/movies?year=abc");

        HttpResponse<String> resp = send(req);

        assertEquals(400, resp.statusCode(), "GET /movies?year=abc должен вернуть 400");

        assertJsonContentType(resp);


        assertError(resp, "Некорректный год");
    }

    @Test
    void unsupportedMethod_whenPutMovies_returns405() throws Exception {
        String json = "{\"title\":\"Интерстеллар\",\"year\":2014}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(405, resp.statusCode(), "Неподдерживаемый метод должен вернуть 405");

        assertJsonContentType(resp);

        assertError(resp, "Метод не поддерживается");
    }

    @Test
    void getMoviesByYear_whenQueryParameterIsWrong_returns400() throws Exception {
        HttpRequest req = getRequest("/movies?abc=2000");

        HttpResponse<String> resp = send(req);

        assertEquals(400, resp.statusCode(), "GET /movies?abc=2000 должен вернуть 400");

        assertJsonContentType(resp);

        assertError(resp, "Некорректный год");
    }
}