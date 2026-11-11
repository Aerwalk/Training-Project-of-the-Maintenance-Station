package org.example.sto.ui;

import javafx.application.Application;
import org.example.sto.config.AppConfig;
import org.example.sto.config.DbSettings;
import org.example.sto.db.ConnectionManager;

/**
 * Точка входа JavaFX-приложения.
 *
 * <p>Используется и из основного {@code StoApplication}, и напрямую
 * через {@code mvn javafx:run}.
 */
public final class UiLauncher {

    private static AppContext context;

    private UiLauncher() {
    }

    public static void launch(AppContext appContext) {
        context = appContext;
        Application.launch(StoFxApp.class);
    }

    static AppContext context() {
        return context;
    }

    /**
     * Запуск для javafx-maven-plugin: конфигурация и пул создаются здесь.
     */
    public static void main(String[] args) {
        DbSettings settings;
        try {
            settings = AppConfig.load().dbSettings();
        } catch (RuntimeException error) {
            System.err.println("Ошибка конфигурации: " + error.getMessage());
            System.exit(2);
            return;
        }

        try (ConnectionManager connectionManager = new ConnectionManager(settings)) {
            launch(AppContext.create(connectionManager));
        } catch (RuntimeException error) {
            System.err.println("Не удалось запустить интерфейс: " + error.getMessage());
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
