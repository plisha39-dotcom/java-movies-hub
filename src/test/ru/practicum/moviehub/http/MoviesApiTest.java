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
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
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

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertEquals("[]", body, "Должен вернуться пустой массив");
    }

    @Test
    void getMovies_whenStoreHasMovie_returnsMovieArray() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        JsonArray array = JsonParser.parseString(body).getAsJsonArray();

        assertEquals(1, array.size(), "Ожидается 1 фильм в массиве");

        JsonObject movieJson = array.get(0).getAsJsonObject();

        assertEquals(1, movieJson.get("id").getAsInt());
        assertEquals("Интерстеллар", movieJson.get("title").getAsString());
        assertEquals(2014, movieJson.get("year").getAsInt());
    }

    @Test
    void postMovies_whenValidRequest_createsMovie() throws Exception {
        String json = """
                {
                  "title": "Интерстеллар",
                  "year": 2014
                }
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals(1, object.get("id").getAsInt());
        assertEquals("Интерстеллар", object.get("title").getAsString());
        assertEquals(2014, object.get("year").getAsInt());
    }

    @Test
    void postMovies_whenContentTypeIsNotJson_returns415() throws Exception {
        String json = """
                {
                  "title": "Интерстеллар",
                  "year": 2014
                }
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Неподдерживаемый Content-Type", object.get("error").getAsString());
    }

    @Test
    void postMovies_whenTitleIsEmpty_returns422() throws Exception {
        String json = """ 
                  {
                  "title": "",
                  "year": 2014
                }
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(422, resp.statusCode(), "POST /movies с пустым title должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Ошибка валидации", object.get("error").getAsString());
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("название не должно быть пустым", details.get(0).getAsString());
    }

    @Test
    void postMovies_whenTitleIsBlank_returns422() throws Exception {
        String json = """ 
                  {
                  "title": "  ",
                  "year": 2014
                }
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(422, resp.statusCode(), "POST /movies с title из пробелов должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Ошибка валидации", object.get("error").getAsString());
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("название не должно быть пустым", details.get(0).getAsString());
    }

    @Test
    void postMovies_whenTitleIsTooLong_returns422() throws Exception {
        String longTitle = "А".repeat(101);

        String json = """
                {
                  "title": "%s",
                  "year": 2014
                }
                """.formatted(longTitle);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(422, resp.statusCode(), "POST /movies с title > 100 должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Ошибка валидации", object.get("error").getAsString());
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("название не должно быть длиннее 100 символов", details.get(0).getAsString());
    }

    @Test
    void postMovies_whenYearIsBefore1888_returns422() throws Exception {
        String json = """ 
                  {
                  "title": "Интерстеллар",
                  "year": 1887
                }
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(422, resp.statusCode(), "POST /movies с невалидным year должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Ошибка валидации", object.get("error").getAsString());
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

        String json = """
                {
                  "title": "Интерстеллар",
                  "year": %d
                }
                """.formatted(invalidYear);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(422, resp.statusCode(), "POST /movies с невалидным year должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Ошибка валидации", object.get("error").getAsString());
        JsonArray details = object.get("details").getAsJsonArray();

        assertEquals(1, details.size(), "Должна быть одна ошибка валидации");
        assertEquals("год должен быть между " + minYear + " и " + maxYear, details.get(0).getAsString());
    }

    @Test
    void postMovies_whenTitleAndYearAreInvalid_returnsAllValidationErrors() throws Exception {
        String json = """ 
                  {
                  "title": "",
                  "year": 1887
                }
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(422, resp.statusCode(), "POST /movies с невалидным year должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Ошибка валидации", object.get("error").getAsString());
        JsonArray details = object.get("details").getAsJsonArray();

        int minYear = 1888;
        int maxYear = LocalDate.now().getYear() + 1;

        assertEquals(2, details.size(), "Должно быть две ошибки валидации");
        assertEquals("название не должно быть пустым", details.get(0).getAsString());
        assertEquals("год должен быть между " + minYear + " и " + maxYear, details.get(1).getAsString());
    }

    @Test
    void postMovies_whenJsonIsMalformed_returns400() throws Exception {
        String json = """
                {
                  "title": "Интерстеллар",
                  "year": 2014
                
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(400, resp.statusCode(), "POST /movies с невалидным JSON должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный JSON", object.get("error").getAsString());
    }

    @Test
    void getMovieById_whenMovieExists_returnsMovie() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "GET /movies/1 должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals(1, object.get("id").getAsInt());
        assertEquals("Интерстеллар", object.get("title").getAsString());
        assertEquals(2014, object.get("year").getAsInt());
    }

    @Test
    void getMovieById_whenMovieNotFound_returns404() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(404, resp.statusCode(), "GET /movies/999 должен вернуть 404");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Фильм не найден", object.get("error").getAsString());
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(400, resp.statusCode(), "GET /movies/abc должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный ID", object.get("error").getAsString());
    }

    @Test
    void deleteMovieById_whenMovieExists_deletesMovie() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(204, resp.statusCode(), "DELETE /movies/1 должен вернуть 204");

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler1 =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp1 = client.send(req1, responseBodyHandler1);

        assertEquals(404, resp1.statusCode(), "GET /movies/1 должен вернуть 404");
    }

    @Test
    void deleteMovieById_whenMovieNotFound_returns404() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(404, resp.statusCode(), "DELETE /movies/999 должен вернуть 404");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Фильм не найден", object.get("error").getAsString());
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);
        String body = resp.body().trim();

        assertEquals(400, resp.statusCode(), "DELETE /movies/abc должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный ID", object.get("error").getAsString());
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredMovies() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        Movie movie1 = new Movie(2, "Начало", 2010);
        Movie movie2 = new Movie(3, "Дюна", 2014);
        store.addMovie(movie);
        store.addMovie(movie1);
        store.addMovie(movie2);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2014"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "GET /movies?year=2014 должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        JsonArray array = JsonParser.parseString(body).getAsJsonArray();

        assertEquals(2, array.size(), "Ожидается 2 фильма в массиве");

        JsonObject movieJson = array.get(0).getAsJsonObject();

        assertEquals(1, movieJson.get("id").getAsInt());
        assertEquals("Интерстеллар", movieJson.get("title").getAsString());
        assertEquals(2014, movieJson.get("year").getAsInt());

        JsonObject movieJson1 = array.get(1).getAsJsonObject();

        assertEquals(3, movieJson1.get("id").getAsInt());
        assertEquals("Дюна", movieJson1.get("title").getAsString());
        assertEquals(2014, movieJson1.get("year").getAsInt());
    }

    @Test
    void getMoviesByYear_whenNoMoviesForYear_returnsEmptyArray() throws Exception {
        Movie movie = new Movie(1, "Интерстеллар", 2014);
        Movie movie1 = new Movie(2, "Начало", 2010);
        Movie movie2 = new Movie(3, "Дюна", 2014);
        store.addMovie(movie);
        store.addMovie(movie1);
        store.addMovie(movie2);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "GET /movies?year=2000 должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        JsonArray array = JsonParser.parseString(body).getAsJsonArray();

        assertEquals(0, array.size(), "Ожидается пустой массив");
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(400, resp.statusCode(), "GET /movies?year=abc должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        JsonObject object = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный год", object.get("error").getAsString());
    }
}