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
    private final MoviesStore moviesStore;
    private final Gson gson;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MIN_YEAR = 1888;
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
        if (path.equals("/movies")) {
            String query = ex.getRequestURI().getQuery();
            if (query == null) {
                List<Movie> movies = moviesStore.getAllMovies();
                String json = gson.toJson(movies);
                sendJson(ex, 200, json);
            } else if (query.startsWith("year=")) {
                String yearString = query.substring("year=".length());
                int year;
                try {
                    year = Integer.parseInt(yearString);
                } catch (NumberFormatException e) {
                    sendError(ex, 400, "Некорректный год");
                    return;
                }
                List<Movie> movies = moviesStore.findMoviesByYear(year);
                String json = gson.toJson(movies);
                sendJson(ex, 200, json);
            }
        } else if (path.startsWith("/movies/")) {
            String idString = path.substring("/movies/".length());
            int id;
            try {
                id = Integer.parseInt(idString);
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный ID");
                return;
            }
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
        Headers headers = ex.getRequestHeaders();
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            sendError(ex, 415, "Неподдерживаемый Content-Type");
            return;
        }
        InputStream inputStream = ex.getRequestBody();
        String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject object;
        try {
            object = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }
        String title = object.get("title").getAsString();
        int year = object.get("year").getAsInt();
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
        if (!details.isEmpty()) {
            ErrorResponse response = new ErrorResponse("Ошибка валидации", details);
            String json = gson.toJson(response);
            sendJson(ex, 422, json);
            return;
        }
        Movie movie = moviesStore.createMovie(title, year);
        String json = gson.toJson(movie);
        sendJson(ex, 201, json);
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.startsWith("/movies/")) {
            String idString = path.substring("/movies/".length());
            int id;
            try {
                id = Integer.parseInt(idString);
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный ID");
                return;
            }
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
}
