package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MIN_YEAR = 1888;
    private static final String MOVIES_PATH = "/movies";
    private static final String MOVIE_ID_PATH_PREFIX = "/movies/";
    private static final String YEAR_QUERY_PREFIX = "year=";
    private final MoviesStore moviesStore;
    private final Gson gson;
    private final int maxYear;

    public MoviesHandler(MoviesStore moviesStore, Gson gson) {
        this.moviesStore = moviesStore;
        this.gson = gson;
        this.maxYear = LocalDate.now().getYear() + 1;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);

        switch (method) {
            case "GET":
                handleGet(ex);
                break;
            case "POST":
                handlePost(ex);
                break;
            case "DELETE":
                handleDelete(ex);
                break;
            default:
                sendError(ex, 405, "Метод не поддерживается");
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals(MOVIES_PATH)) {
            String query = ex.getRequestURI().getQuery();
            if (query == null) {
                List<Movie> movies = moviesStore.getAllMovies();
                String json = gson.toJson(movies);
                sendJson(ex, 200, json);
            } else if (query.startsWith(YEAR_QUERY_PREFIX)) {
                Optional<Integer> optionalYear = parseYear(query);
                if (optionalYear.isEmpty()) {
                    sendError(ex, 400, "Некорректный год");
                    return;
                }
                int year = optionalYear.get();
                List<Movie> movies = moviesStore.findMoviesByYear(year);
                String json = gson.toJson(movies);
                sendJson(ex, 200, json);
            } else {
                sendError(ex, 400, "Некорректный год");
            }
        } else if (path.startsWith(MOVIE_ID_PATH_PREFIX)) {
            Optional<Integer> idOptional = parseId(path);
            if (idOptional.isEmpty()) {
                sendError(ex, 400, "Некорректный ID");
                return;
            }
            int id = idOptional.get();
            Optional<Movie> optionalMovie = moviesStore.findMovieById(id);
            if (optionalMovie.isEmpty()) {
                sendError(ex, 404, "Фильм не найден");
                return;
            }
            Movie movie = optionalMovie.get();
            String json = gson.toJson(movie);
            sendJson(ex, 200, json);
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!path.equals(MOVIES_PATH)) {
            sendError(ex, 404, "Эндпоинт не найден");
            return;
        }
        Headers headers = ex.getRequestHeaders();
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            sendError(ex, 415, "Неподдерживаемый Content-Type");
            return;
        }
        Optional<JsonObject> optionalObject = parseRequestBody(ex);
        if (optionalObject.isEmpty()) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }
        JsonObject object = optionalObject.get();

        List<String> requestDetails = validateMovieRequest(object);
        if (!requestDetails.isEmpty()) {
            sendValidationError(ex, requestDetails);
            return;
        }

        String title = object.get("title").getAsString();
        int year = object.get("year").getAsInt();

        List<String> validationDetails = validateMovie(title, year);
        if (!validationDetails.isEmpty()) {
            sendValidationError(ex, validationDetails);
            return;
        }

        Movie movie = moviesStore.createMovie(title, year);
        String json = gson.toJson(movie);
        sendJson(ex, 201, json);
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.startsWith(MOVIE_ID_PATH_PREFIX)) {
            Optional<Integer> idOptional = parseId(path);
            if (idOptional.isEmpty()) {
                sendError(ex, 400, "Некорректный ID");
                return;
            }
            int id = idOptional.get();
            boolean deleted = moviesStore.deleteMovie(id);
            if (!deleted) {
                sendError(ex, 404, "Фильм не найден");
                return;
            }
            sendNoContent(ex);
        }
    }

    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        ErrorResponse response = new ErrorResponse(message);
        String json = gson.toJson(response);
        sendJson(ex, status, json);
    }

    private Optional<Integer> parseId(String path) {
        String idString = path.substring(MOVIE_ID_PATH_PREFIX.length());
        int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    private Optional<Integer> parseYear(String query) {
        String yearString = query.substring(YEAR_QUERY_PREFIX.length());
        int year;
        try {
            year = Integer.parseInt(yearString);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(year);
    }

    private List<String> validateMovie(String title, int year) {
        List<String> details = new ArrayList<>();
        if (title.isBlank()) {
            details.add("название не должно быть пустым");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            details.add("название не должно быть длиннее " + MAX_TITLE_LENGTH + " символов");
        }
        if (year < MIN_YEAR || year > maxYear) {
            details.add("год должен быть между " + MIN_YEAR + " и " + maxYear);
        }
        return details;
    }

    private Optional<JsonObject> parseRequestBody(HttpExchange ex) throws IOException {
        InputStream inputStream = ex.getRequestBody();
        String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject object;
        try {
            object = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return Optional.empty();
        }
        return Optional.of(object);
    }

    private void sendValidationError(HttpExchange ex, List<String> details) throws IOException {
        ErrorResponse response = new ErrorResponse("Ошибка валидации", details);
        String json = gson.toJson(response);
        sendJson(ex, 422, json);
    }

    private List<String> validateMovieRequest(JsonObject object) {
        List<String> details = new ArrayList<>();
        if (!object.has("title")) {
            details.add("название не должно быть пустым");
        } else if (object.get("title").isJsonNull()) {
            details.add("название не должно быть пустым");
        } else if (!object.get("title").isJsonPrimitive()) {
            details.add("название должно быть строкой");
        } else if (!object.get("title").getAsJsonPrimitive().isString()) {
            details.add("название должно быть строкой");
        }
        if (!object.has("year")) {
            details.add("год не должен быть пустым");
        } else if (object.get("year").isJsonNull()) {
            details.add("год не должен быть пустым");
        } else if (!object.get("year").isJsonPrimitive()) {
            details.add("год должен быть числом");
        } else if (!object.get("year").getAsJsonPrimitive().isNumber()) {
            details.add("год должен быть числом");
        }
        return details;
    }
}
