package org.example;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.google.gson.*;
import org.apache.hc.client5.http.fluent.Request;

public class Main extends Application {

    private TextField gameField;
    private TextArea outputArea;

    private Label youtubeLabel;
    private Label steamLabel;
    private Label epicLabel;

    private Hyperlink videoLink;
    private Hyperlink steamLink;
    private Hyperlink epicLink;

    private Properties config = new Properties();

    @Override
    public void start(Stage primaryStage) throws Exception {
        loadConfig();

        gameField = new TextField();
        gameField.setPromptText("Enter Game Name");

        Button analyzeButton = new Button("Analyze Game");
        outputArea = new TextArea();
        outputArea.setEditable(false);

        youtubeLabel = createLinkLabel("YouTube Trailer:");
        steamLabel = createLinkLabel("Steam Link:");
        epicLabel = createLinkLabel("Epic Games Link:");

        videoLink = createHyperlink();
        steamLink = createHyperlink();
        epicLink = createHyperlink();

        Label title = new Label("Game Analyzer");
        title.setStyle("-fx-font-size: 36px; -fx-font-family: 'Comic Sans MS'; -fx-text-fill: #76b900;");

        Button settingsButton = new Button("\u2699");
        settingsButton.setStyle("-fx-font-size: 20px; -fx-background-color: transparent; -fx-text-fill: #76b900;");
        settingsButton.setOnAction(e -> showApiSettingsDialog());

        HBox topBar = new HBox(10, settingsButton, title);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: black;");

        analyzeButton.setOnAction(e -> analyzeGame());

        VBox root = new VBox(10, topBar, gameField, analyzeButton, outputArea,
                youtubeLabel, videoLink,
                steamLabel, steamLink,
                epicLabel, epicLink);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: black; -fx-text-fill: white;");

        gameField.setStyle("-fx-background-color: #333333; -fx-text-fill: white;");
        analyzeButton.setStyle("-fx-background-color: #76b900; -fx-text-fill: black;");
        outputArea.setStyle("-fx-control-inner-background: black; -fx-text-fill: white;");

        Scene scene = new Scene(root, 500, 700);
        primaryStage.setTitle("Game Analyzer");
        primaryStage.setScene(scene);
        primaryStage.show();

        promptForApiKeysIfNeeded();
    }

    private Label createLinkLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-family: 'Comic Sans MS';");
        return label;
    }

    private Hyperlink createHyperlink() {
        Hyperlink link = new Hyperlink();
        link.setStyle("-fx-text-fill: #76b900; -fx-font-size: 14px;");
        return link;
    }

    private void loadConfig() {
        try (FileInputStream in = new FileInputStream("config.properties")) {
            config.load(in);
        } catch (IOException e) {
            System.out.println("No config file found, starting fresh.");
        }
    }

    private void saveConfig() {
        try (FileOutputStream out = new FileOutputStream("config.properties")) {
            config.store(out, "API Keys");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void analyzeGame() {
        String ytKey = config.getProperty("youtube_api", "").trim();
        String rawgKey = config.getProperty("rawg_api", "").trim();
        String gameName = gameField.getText().trim();

        if (ytKey.isEmpty() || rawgKey.isEmpty() || gameName.isEmpty()) {
            showError("Please enter API keys in settings and game name.");
            return;
        }

        outputArea.clear();
        clearLinks();

        outputArea.appendText("Analyzing " + gameName + "...\n");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    String suggestion = null;
                    double rating = -1.0;
                    String video = "";
                    String reviews = "";
                    String steamUrl = "";
                    String epicUrl = "";

                    String query = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
                    String url = "https://api.rawg.io/api/games?key=" + rawgKey + "&search=" + query;
                    String response = Request.get(url).execute().returnContent().asString();
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    JsonArray results = json.getAsJsonArray("results");

                    if (results != null && results.size() > 0) {
                        JsonObject gameObj = results.get(0).getAsJsonObject();

                        if (gameObj.has("rating") && !gameObj.get("rating").isJsonNull()) {
                            rating = gameObj.get("rating").getAsDouble() * 2.0;
                        }

                        video = fetchYoutubeVideo(gameName, ytKey);

                        String steamAppId = extractSteamAppId(gameObj);
                        epicUrl = extractEpicLink(gameObj);

                        if (steamAppId != null) {
                            steamUrl = "https://store.steampowered.com/app/" + steamAppId;
                            reviews = fetchSteamReviews(steamAppId);
                        } else {
                            String rawgGameName = gameObj.has("name") && !gameObj.get("name").isJsonNull()
                                    ? gameObj.get("name").getAsString()
                                    : gameName;
                            steamAppId = searchSteamAppIdByName(rawgGameName);
                            if (steamAppId != null) {
                                steamUrl = "https://store.steampowered.com/app/" + steamAppId;
                                reviews = fetchSteamReviews(steamAppId);
                            } else {
                                reviews = "\nSteam ID not found, cannot fetch reviews.\n";
                            }
                        }
                    } else {
                        suggestion = suggestCorrectName(gameName, rawgKey);
                    }

                    final String finalSuggestion = suggestion;
                    final double finalRating = rating;
                    final String finalVideo = video;
                    final String finalReviews = reviews;
                    final String finalSteamUrl = steamUrl;
                    final String finalEpicUrl = epicUrl;

                    javafx.application.Platform.runLater(() -> {
                        if (finalSuggestion != null) {
                            outputArea.appendText("Game not found. Did you mean: " + finalSuggestion + "?\n");
                        } else {
                            outputArea.appendText("Game: " + gameName + "\nRating: " + finalRating + "\n");
                            outputArea.appendText(finalReviews);

                            setLink(videoLink, finalVideo);
                            setLink(steamLink, finalSteamUrl);
                            setLink(epicLink, finalEpicUrl);
                        }
                    });

                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> outputArea.appendText("Error: " + ex.getMessage() + "\n"));
                }
                return null;
            }
        };

        new Thread(task).start();
    }

    private void setLink(Hyperlink link, String url) {
        if (url != null && url.startsWith("http")) {
            link.setText(url);
            link.setOnAction(e -> openLink(url));
        } else {
            link.setText(url); // For fallback messages like "Epic Games link not found."
            link.setOnAction(null);
        }
    }

    private void clearLinks() {
        videoLink.setText("");
        steamLink.setText("");
        epicLink.setText("");
    }

    private void openLink(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }

    private String fetchYoutubeVideo(String game, String ytKey) throws IOException {
        String query = URLEncoder.encode(game + " trailer", StandardCharsets.UTF_8);
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=" + query + "&key=" + ytKey + "&maxResults=1";

        String response = Request.get(url).execute().returnContent().asString();
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray items = json.getAsJsonArray("items");

        if (items != null && items.size() > 0) {
            JsonObject video = items.get(0).getAsJsonObject();
            if (video.has("id") && !video.get("id").isJsonNull()) {
                JsonObject idObj = video.getAsJsonObject("id");
                if (idObj.has("videoId") && !idObj.get("videoId").isJsonNull()) {
                    return "https://www.youtube.com/watch?v=" + idObj.get("videoId").getAsString();
                }
            }
        }
        return "No video found.";
    }

    private String suggestCorrectName(String game, String rawgKey) throws IOException {
        String query = URLEncoder.encode(game, StandardCharsets.UTF_8);
        String url = "https://api.rawg.io/api/games?key=" + rawgKey + "&search=" + query;

        String response = Request.get(url).execute().returnContent().asString();
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray results = json.getAsJsonArray("results");

        if (results != null && results.size() > 0) {
            JsonObject gameObj = results.get(0).getAsJsonObject();
            if (gameObj.has("name") && !gameObj.get("name").isJsonNull()) {
                return gameObj.get("name").getAsString();
            }
        }
        return null;
    }

    private String extractSteamAppId(JsonObject gameObj) {
        if (gameObj.has("stores") && !gameObj.get("stores").isJsonNull()) {
            JsonArray stores = gameObj.getAsJsonArray("stores");
            for (JsonElement storeElement : stores) {
                JsonObject storeObj = storeElement.getAsJsonObject();
                if (!storeObj.has("store") || storeObj.get("store").isJsonNull()) continue;

                JsonObject storeInfo = storeObj.getAsJsonObject("store");
                String storeName = storeInfo.get("name").getAsString();
                if (storeName.equalsIgnoreCase("Steam")) {
                    if (storeObj.has("url") && !storeObj.get("url").isJsonNull()) {
                        String url = storeObj.get("url").getAsString();
                        String[] parts = url.split("/");
                        for (String part : parts) {
                            if (part.matches("\\d+")) {
                                return part;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractEpicLink(JsonObject gameObj) {
        if (gameObj.has("stores") && !gameObj.get("stores").isJsonNull()) {
            JsonArray stores = gameObj.getAsJsonArray("stores");
            for (JsonElement storeElement : stores) {
                JsonObject storeObj = storeElement.getAsJsonObject();
                if (!storeObj.has("store") || storeObj.get("store").isJsonNull()) continue;

                JsonObject storeInfo = storeObj.getAsJsonObject("store");
                String storeName = storeInfo.get("name").getAsString();
                if (storeName.equalsIgnoreCase("Epic Games")) {
                    if (storeObj.has("url") && !storeObj.get("url").isJsonNull()) {
                        return storeObj.get("url").getAsString();
                    }
                    if (gameObj.has("slug") && !gameObj.get("slug").isJsonNull()) {
                        String slug = gameObj.get("slug").getAsString();
                        return "https://www.epicgames.com/store/en-US/p/" + slug;
                    }
                    return "Epic Games link not available.";
                }
            }
        }
        return "Epic Games link not found.";
    }

    private String searchSteamAppIdByName(String gameName) throws IOException {
        String query = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
        String url = "https://store.steampowered.com/api/storesearch/?term=" + query + "&cc=US&l=en";

        String response = Request.get(url).execute().returnContent().asString();
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        if (json.has("items")) {
            JsonArray items = json.getAsJsonArray("items");
            if (items.size() > 0) {
                JsonObject firstItem = items.get(0).getAsJsonObject();
                return firstItem.get("id").getAsString();
            }
        }
        return null;
    }

    private String fetchSteamReviews(String appId) throws IOException {
        String url = "https://store.steampowered.com/appreviews/" + appId + "?json=1&num_per_page=7&filter=recent&language=all";

        String response = Request.get(url).execute().returnContent().asString();
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray reviews = json.getAsJsonArray("reviews");

        if (reviews == null || reviews.size() == 0) return "\nNo recent Steam reviews found.\n";

        StringBuilder sb = new StringBuilder();
        sb.append("\nLatest 7 Steam Reviews:\n");
        for (JsonElement reviewElement : reviews) {
            JsonObject reviewObj = reviewElement.getAsJsonObject();
            boolean recommended = reviewObj.get("voted_up").getAsBoolean();
            String text = reviewObj.get("review").getAsString().replaceAll("\\s+", " ").trim();
            if (text.length() > 200) {
                text = text.substring(0, 200) + "...";
            }
            sb.append("- ").append(recommended ? "[+]" : "[-]").append(" ").append(text).append("\n");
        }
        return sb.toString();
    }

    private void promptForApiKeysIfNeeded() {
        if (config.getProperty("youtube_api", "").isEmpty() || config.getProperty("rawg_api", "").isEmpty()) {
            showApiSettingsDialog();
        }
    }

    private void showApiSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Edit API Keys");

        dialog.getDialogPane().setStyle("-fx-background-color: black;");

        Label ytLabel = new Label("YouTube API Key:");
        ytLabel.setStyle("-fx-text-fill: #76b900;");
        TextField ytField = new TextField(config.getProperty("youtube_api", ""));
        ytField.setStyle("-fx-background-color: #333333; -fx-text-fill: white;");

        Label rawgLabel = new Label("RAWG API Key:");
        rawgLabel.setStyle("-fx-text-fill: #76b900;");
        TextField rawgField = new TextField(config.getProperty("rawg_api", ""));
        rawgField.setStyle("-fx-background-color: #333333; -fx-text-fill: white;");

        VBox vbox = new VBox(10, ytLabel, ytField, rawgLabel, rawgField);
        vbox.setPadding(new Insets(20));

        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                config.setProperty("youtube_api", ytField.getText().trim());
                config.setProperty("rawg_api", rawgField.getText().trim());
                saveConfig();
            }
            return null;
        });

        dialog.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
