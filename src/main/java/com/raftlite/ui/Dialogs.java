package com.raftlite.ui;

import javafx.scene.control.Alert;

/** Small helpers for user feedback dialogs. */
final class Dialogs {

    private Dialogs() {
    }

    static void warning(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    static void error(String header, Exception error) {

        String message = error.getMessage();

        if (error.getCause() != null) {
            message += "\n\nCause: " + error.getCause().getMessage();
        }

        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(header);
        alert.showAndWait();
    }
}
