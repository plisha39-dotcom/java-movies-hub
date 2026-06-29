package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;

    public MoviesServer(MoviesStore store) {
        Gson gson = new Gson();
        MoviesHandler handler = new MoviesHandler(store, gson);
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/movies", handler);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}