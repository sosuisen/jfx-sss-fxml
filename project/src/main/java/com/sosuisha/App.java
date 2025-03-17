package com.sosuisha;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        var loader = new FXMLLoader(getClass().getResource("main.fxml"));
        var scene = new Scene(loader.load(), 640, 480);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        stage.setScene(scene);
        stage.setTitle("MyApp");
        stage.show();
    }

}
