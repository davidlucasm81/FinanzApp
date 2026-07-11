package com.finanzapp.app.util;

public abstract class Result<T> {
    private Result() {}

    public static final class Success<T> extends Result<T> {
        private final T data;
        public Success(T data) { this.data = data; }
        public T getData() { return data; }
    }

    public static final class Error<T> extends Result<T> {
        private final Exception exception;
        public Error(Exception exception) { this.exception = exception; }
        public Exception getException() { return exception; }
    }

    public static final class Loading<T> extends Result<T> {
        public Loading() {}
    }
}
