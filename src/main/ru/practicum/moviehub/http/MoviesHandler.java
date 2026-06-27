package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore moviesStore;
    private final Gson gson;
    private static final int MAX_TITLE_LENGTh = 100;
    private static final int MIN_YEAR = 1888;
    private final int maxYear;


    public MoviesHandler(MoviesStore moviesStore, Gson gson) {
        this.moviesStore = moviesStore;
        this.gson = gson;
        this.maxYear = LocalDate.now().getYear() + 1;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            List<Movie> movies = moviesStore.getAllMovies();
            String json = gson.toJson(movies);
            sendJson(ex, 200, json);
        } else {
            // пока оставим так, позже сделаем сразу вывод ошибки через другой класс
        }
        if ("POST".equalsIgnoreCase(method)) {
            Headers headers = ex.getRequestHeaders();
            String contentType = headers.getFirst("Content-Type");
            if (contentType == null || !contentType.contains("application/json")) {
                ErrorResponse response = new ErrorResponse("Неподдерживаемый Content-Type");
                String json = gson.toJson(response);
                sendJson(ex, 415, json);
                return;
            }
            InputStream inputStream = ex.getRequestBody();
            String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            String title = object.get("title").getAsString();
            int year = object.get("year").getAsInt();
            List<String> details = new ArrayList<>();
            if (title.isBlank()) {
                details.add("название не должно быть пустым");
            }
            if (title.length() > 100) {
                details.add("название не должно быть длиннее " + MAX_TITLE_LENGTh + " символов");
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
    }
}
