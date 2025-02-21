module com.sosuisha {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.sosuisha to javafx.fxml, javafx.graphics;
}
