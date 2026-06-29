package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MoviesStore {
    private final Map<Integer, Movie> movies;
    private int nextId;

    public MoviesStore() {
        movies = new LinkedHashMap<>();
        nextId = 1;
    }

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }

    public void addMovie(Movie movie) {
        int id = movie.getId();
        movies.put(id, movie);
    }

    public Movie createMovie(String title, int year) {
        int id = nextId;
        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        nextId++;
        return movie;
    }

    public Optional<Movie> findMovieById(int id) {
        Movie movie = movies.get(id);
        return Optional.ofNullable(movie);
    }

    public boolean deleteMovie(int id) {
        return movies.remove(id) != null;
    }
}