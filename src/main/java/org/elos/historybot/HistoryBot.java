package org.elos.historybot;

import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.elos.historybot.model.User;
import org.elos.historybot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@PropertySource("classpath:application.properties")
public class HistoryBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {


    private final String botToken;

    @Override
    public String getBotToken() {
        return botToken;
    }

    private final UserService userService;

    @Autowired
    public HistoryBot(UserService userService, @Value("${bot.token}") String botToken) {
        this.userService = userService;
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    private final TelegramClient telegramClient;


    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessages(update);
        }
    }

    private void handleMessages(Update update) {
        Message message = update.getMessage();
        String command = message.getText();
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        switch (command) {
            case "/start":
                handleStart(chatId, userId);
                break;
            case "Переглянути теми":
                handleViewTopics(chatId);
                break;
            case "Конспект":
                handleAdditionalCommands(chatId, "Конспект");
                break;
            case "Пам'ятки":
                handleAdditionalCommands(chatId, "Пам_ятки");
                break;
            case "Персоналії":
                handleAdditionalCommands(chatId, "Персоналії");
                break;
            case "Портрети":
                handleAdditionalCommands(chatId, "Портрети");
                break;
            case "Поняття":
                handleAdditionalCommands(chatId, "Поняття");
                break;
            case "Дати":
                handleAdditionalCommands(chatId, "Дати");
                break;
            case "Козацькі угоди":
                sendKozatskiYgodu(chatId);
                break;
        }
        if (command.startsWith("Тема ")) {
            handleTopic(chatId, Integer.parseInt(command.split(" ")[1]));
        }
        if (command.startsWith("Назад")) {
            handleBack(chatId);
        }
    }

    private void handleBack(Long chatId) {
        User user = userService.findByChatId(chatId);
        int position = user.getCurrentPosit();
        switch (position) {
            case 0:
            case 2:
                handleStart(chatId, user.getUserId());
                break;
            case 3:
                handleViewTopics(chatId);
                break;
        }
    }

    private void handleAdditionalCommands(Long chatId, String type) {
        int selectedTopic = userService.findByChatId(chatId).getSelectedTopic();
        List<File> images = extractImagesFromPDF(chatId, selectedTopic, type);
        if (!images.isEmpty()) {
            sendImageAlbum(chatId, images, String.format("Тема " + selectedTopic + ". %s.", type));
            for (File image : images) {
                image.delete();
            }
        }
    }

    private void handleViewTopics(Long chatId) {
        User user = userService.findByChatId(chatId);
        user.setCurrentPosit(2);
        userService.save(user);
        int topicsLength = 32;
        String text = "Теми з історії України.\nОберіть потрібну вам тему на клавіатурі.";
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        for (int i = 1; i <= topicsLength; i += 2) {
            KeyboardRow keyboardRow = new KeyboardRow(
                    KeyboardButton.builder()
                            .text("Тема " + i)
                            .build(),
                    KeyboardButton.builder()
                            .text("Тема " + (i + 1))
                            .build()
            );
            keyboardRows.add(keyboardRow);
        }
        keyboardRows.add(new KeyboardRow(
                KeyboardButton.builder()
                        .text("Назад ◀\uFE0F")
                        .build()
        ));
        ReplyKeyboardMarkup markup = ReplyKeyboardMarkup.builder()
                .keyboard(keyboardRows).build();
        markup.setResizeKeyboard(true);
        sendMsgButton(chatId, text, markup);
    }


    private void handleStart(Long chatId, Long userId) {
        if (!userService.existsByUserId(userId)) {
            userService.createUser(chatId, userId);
        }
        User user = userService.findByUserId(userId);
        user.setCurrentPosit(1);
        userService.save(user);
        ReplyKeyboardMarkup viewTopics = ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(KeyboardButton.builder()
                        .text("Переглянути теми").build())).build();
        viewTopics.setResizeKeyboard(true);
        sendMsgButton(chatId, "Вітаємо в нашому боті про <b>історію України</b>! \uD83C\uDDFA\uD83C\uDDE6" +
                "\nВ нас є теми від появи першої людини (1) на землі, до сучасної України (32)" +
                "\nНатисніть на кнопку нижче, щоб обрати потрібну Вам тему", viewTopics);
    }

    private void handleTopic(Long chatId, int topicNumber) {
        if (userService.existsByUserId(chatId)) {
            User user = userService.findByChatId(chatId);
            user.setCurrentPosit(3);
            user.setSelectedTopic(topicNumber);
            userService.save(user);
            List<KeyboardRow> keyboardRows = new ArrayList<>();
            KeyboardRow keyboardRow = new KeyboardRow(
                    KeyboardButton.builder()
                            .text("Конспект")
                            .build());
            KeyboardRow keyboardRow2 = new KeyboardRow(
                    KeyboardButton.builder()
                            .text("Пам'ятки")
                            .build(),
                    KeyboardButton.builder()
                            .text("Дати")
                            .build());
            KeyboardRow keyboardRow3 = new KeyboardRow(
                    KeyboardButton.builder()
                            .text("Персоналії")
                            .build(),
                    KeyboardButton.builder()
                            .text("Поняття")
                            .build(),
                    KeyboardButton.builder()
                            .text("Портрети")
                            .build());
            KeyboardRow keyboardRow4 = new KeyboardRow(
                    KeyboardButton.builder()
                            .text("Назад ◀\uFE0F")
                            .build()
            );

            keyboardRows.add(keyboardRow);
            keyboardRows.add(keyboardRow2);
            keyboardRows.add(keyboardRow3);
            if (topicNumber > 7 && topicNumber < 12) {
                keyboardRows.add(new KeyboardRow(
                        KeyboardButton.builder()
                                .text("Козацькі угоди")
                                .build()
                ));

            }
            keyboardRows.add(keyboardRow4);

            ReplyKeyboardMarkup markup = ReplyKeyboardMarkup.builder()
                    .keyboard(keyboardRows).build();

            sendMsgButton(chatId, String.format("Тема %d.\nВиберіть потрібні вам опції на клавіатурі." +
                    "\nЗверніть увагу, що не для кожної теми є додаткові відомості (портрети, пам'ятки, персоналії, дати, поняття) ", user.getSelectedTopic()), markup);
        }

    }


    private String extractTextFromPDF(int topicNumber) {
        String filePath = "Полтавцев/Заняття 1/Конспект. Тема " + topicNumber + ".pdf";
        try (InputStream resource = new ClassPathResource(filePath).getInputStream()) {
            try (PDDocument document = PDDocument.load(resource)) {
                PDFTextStripper pdfStripper = new PDFTextStripper();
                return pdfStripper.getText(document);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Помилка при отриманні тексту: " + e.getMessage();
        }
    }

    private void sendPdfToUser(Long chatId, String filePath) {
        ClassPathResource pdfResource = new ClassPathResource(filePath);
        try (InputStream inputStream = pdfResource.getInputStream()) {
            SendDocument sendDocument = new SendDocument(String.valueOf(chatId), new InputFile(inputStream, pdfResource.getFilename()));
            telegramClient.execute(sendDocument);
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
        }
    }

    private List<File> extractImagesFromPDF(Long chatId, int topicNumber, String type) {
        String filePath = getFilePath(topicNumber, type, false, true);
        System.out.println(filePath);
        ClassPathResource resource = new ClassPathResource(filePath);

        // Check if the resource exists. If not, switch to the alternative path.
        try {
            if (!resource.exists() && type == "Персоналії") {
                filePath = getFilePath(topicNumber, type, false, false);
                resource = new ClassPathResource(filePath);
                if (!resource.isFile()) {
                    filePath = getFilePath(topicNumber, type, true, false);
                    resource = new ClassPathResource(filePath);
                    if (!resource.exists() || !resource.getFile().exists()) {
                        filePath = getFilePath(topicNumber, type, true, true);
                        resource = new ClassPathResource(filePath);
                    }
                }
            }
            if (!resource.exists()) {
                filePath = getFilePath(topicNumber, type, true, false);
                resource = new ClassPathResource(filePath);
                System.out.println(filePath);

            }

        } catch (Exception e) {
            sendMsg(chatId, "\uD83D\uDE22 На жаль, нам не вдалося отримати " + type.toLowerCase() + " для теми " + topicNumber);
            throw new RuntimeException(e);
        }

        List<File> imageFiles = new ArrayList<>();

        try (InputStream pdfStream = resource.getInputStream()) {
            try (PDDocument document = PDDocument.load(pdfStream)) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 350); // Render page to image

                    // Compress image to appropriate size
                    BufferedImage compressedImage = Thumbnails.of(image)
                            .size(2048, 2048) // Set max resolution
                            .outputQuality(0.8) // Set quality from 0 to 1
                            .asBufferedImage();

                    // Save the image as a temporary file
                    File imageFile = new File("temp_image_" + page + ".png");
                    ImageIO.write(compressedImage, "png", imageFile);
                    imageFiles.add(imageFile);
                }
            }
            sendPdfToUser(chatId, filePath);
        } catch (IOException e) {
            sendMsg(chatId, "\uD83D\uDE22 На жаль, нам не вдалося отримати " + type.toLowerCase() + " для теми " + topicNumber);
            System.out.println(e.getMessage());
        }

        return imageFiles;
    }

    private void sendKozatskiYgodu(Long chatId) {
        String filePath = "Полтавцев/Заняття 3/Козацькі угоди.pdf";
        sendPdfToUser(chatId, filePath);

        ClassPathResource resource = new ClassPathResource(filePath);

        List<File> imageFiles = new ArrayList<>();

        try (InputStream pdfStream = resource.getInputStream()) {
            try (PDDocument document = PDDocument.load(pdfStream)) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300); // Render page to image

                    // Compress image to appropriate size
                    BufferedImage compressedImage = Thumbnails.of(image)
                            .size(2048, 2048) // Set max resolution
                            .outputQuality(0.7) // Set quality from 0 to 1
                            .asBufferedImage();

                    // Save the image as a temporary file
                    File imageFile = new File("temp_image_" + page + ".png");
                    ImageIO.write(compressedImage, "png", imageFile);
                    imageFiles.add(imageFile);
                }
            }
        } catch (IOException e) {
            sendMsg(chatId, "\uD83D\uDE22 На жаль, нам не вдалося отримати Козацькі угоди");
            System.out.println(e.getMessage());
        }
        sendImageAlbum(chatId, imageFiles, "Козацькі угоди");
    }

    private String getFilePath(int topicNumber, String type, boolean intensive, boolean nopersonalii) {
        int numberOfLesson;

        // Проверяем и формируем путь для типа "Персоналії"
        if (type.equals("Персоналії")) {
            String intens = intensive ? ". Інтенсив" : "";
            String path = String.format("Полтавцев/Заняття %d/Персоналії. Тема %d%s.pdf", getLessonNumber(topicNumber), topicNumber, intens);
            if (nopersonalii) {
                return replaceDiacriticsInPersonal(path);
            }
            return path;
            // Проверяем путь с заменой буквы і на ї
        }

        // Формируем путь для специальных типов (Дати, Пам'ятки, Поняття)
        if ((topicNumber == 1 || topicNumber == 2) && (type.equals("Дати") || type.equals("Пам_ятки") || type.equals("Поняття"))) {
            return String.format("Полтавцев/Заняття 1/%s. Тема 1-2.pdf", type);
        }

        // Определяем номер занятия
        numberOfLesson = getLessonNumber(topicNumber);

        // Формируем стандартный путь
        String intens = intensive ? ". Інтенсив" : "";
        return String.format("Полтавцев/Заняття %d/%s. Тема %d%s.pdf", numberOfLesson, type, topicNumber, intens);
    }

    // Метод для определения номера занятия
    private int getLessonNumber(int topicNumber) {
        if (topicNumber >= 1 && topicNumber <= 4) return 1;
        else if (topicNumber >= 5 && topicNumber <= 7) return 2;
        else if (topicNumber >= 8 && topicNumber <= 11) return 3;
        else if (topicNumber >= 12 && topicNumber <= 14) return 4;
        else if (topicNumber >= 15 && topicNumber <= 17) return 5;
        else if (topicNumber >= 18 && topicNumber <= 20) return 6;
        else if (topicNumber >= 21 && topicNumber <= 22) return 7;
        else if (topicNumber >= 23 && topicNumber <= 25) return 8;
        else if (topicNumber >= 26 && topicNumber <= 28) return 9;
        else if (topicNumber >= 29 && topicNumber <= 32) return 10;
        else return 1;
    }

    // Метод для замены буквы 'і' на 'ї' в слове "Персоналії"
    private String replaceDiacriticsInPersonal(String path) {
        // Заменяем букву 'і' на 'ї' только в случае, если слово "Персоналії" в пути
        if (path.contains("Персоналії")) {
            path = path.replace("Персоналії", "Персоналії");
        }
        return path;
    }

    private void sendImageAlbum(Long chatId, List<File> imageFiles, String caption) {
        List<InputMedia> mediaList = new ArrayList<>();

        for (int i = 0; i < imageFiles.size(); i++) {
            InputMediaPhoto mediaPhoto = new InputMediaPhoto(imageFiles.get(i), imageFiles.get(i).getName());
            if (i == 0) {
                mediaPhoto.setCaption(caption); // Устанавливаем подпись только для первой картинки
            }
            mediaList.add(mediaPhoto);
        }
        if (mediaList.size() < 2) {
            SendPhoto sendPhoto = new SendPhoto(String.valueOf(chatId), new InputFile(imageFiles.get(0)));
            try {
                telegramClient.execute(sendPhoto);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        SendMediaGroup sendMediaGroup = new SendMediaGroup(String.valueOf(chatId), mediaList);
        try {
            telegramClient.execute(sendMediaGroup);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMsg(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);
        sendMessage.setParseMode("HTML");
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMsgButton(Long chatId, String text, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
