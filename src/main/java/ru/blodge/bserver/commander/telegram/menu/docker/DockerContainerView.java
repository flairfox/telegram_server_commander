package ru.blodge.bserver.commander.telegram.menu.docker;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.blodge.bserver.commander.model.DockerContainer;
import ru.blodge.bserver.commander.services.DockerService;
import ru.blodge.bserver.commander.telegram.CommanderBot;
import ru.blodge.bserver.commander.telegram.menu.MessageView;
import ru.blodge.bserver.commander.utils.builders.EditMessageBuilder;
import ru.blodge.bserver.commander.utils.builders.InlineKeyboardBuilder;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static ru.blodge.bserver.commander.telegram.menu.MenuRouter.DOCKER_CONTAINERS_MENU_SELECTOR;
import static ru.blodge.bserver.commander.telegram.menu.MenuRouter.DOCKER_CONTAINER_MENU_SELECTOR;
import static ru.blodge.bserver.commander.utils.Emoji.BACK_EMOJI;
import static ru.blodge.bserver.commander.utils.Emoji.REFRESH_EMOJI;

public class DockerContainerView implements MessageView {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerView.class);

    private static final String RESTART_CONFIRMATION_TEXT = """
            *Docker-контейнер*
            `%s`
            *будет перезапущен!*
                            
            Вы действительно хотите продолжить?
            """;
    private static final String RESTART_INFO_TEXT = """
            *Docker-контейнер*
            `%s` перезапускается!
            """;

    private static final String STOP_CONFIRMATION_TEXT = """
            *Docker-контейнер*
            `%s`
            *будет остановлен!*
                            
            Вы действительно хотите продолжить?
            """;
    private static final String STOP_INFO_TEXT = """
            *Docker-контейнер*
            `%s` останавливается!
            """;

    private static final String LAUNCH_INFO_TEXT = """
            *Docker-контейнер*
            `%s` запускается!
            """;

    private static final String LOGS_INFO_TEXT = """
            *Docker-контейнер*
            `%s`
                        
            Собираю логи контейнера. По завершении отправлю логи отдельным сообщением...
            """;

    private static final String ALREADY_STOPPED_TEXT = """
            *Docker-контейнер с ID*
            `%s`
            *уже остановлен!*
            """;
    private static final String ALREADY_LAUNCHED_TEXT = """
            *Docker-контейнер с ID*
            `%s`
            *уже запущен!*
            """;


    private static final String RESTART_ACTION = "1";
    private static final String LAUNCH_ACTION = "2";
    private static final String STOP_ACTION = "3";
    private static final String LOGS_ACTION = "4";

    @Override
    public void display(CallbackQuery callbackQuery) {

        String[] callbackDataArr = callbackQuery.getData().split("\\.");
        String containerId = callbackDataArr[1];
        String action = callbackDataArr[2];

        DockerContainer container;
        try {
            container = DockerService.instance().getContainer(containerId);
        } catch (NotFoundException e) {
            displayContainerNotFoundMessage(callbackQuery, containerId);
            return;
        }

        switch (action) {
            // Перезапуск контейнера ======================================================== //
            case RESTART_ACTION + "?" -> displayContainerActionConfirmation(
                    callbackQuery,
                    container,
                    RESTART_ACTION,
                    RESTART_CONFIRMATION_TEXT.formatted(container.names()));
            case RESTART_ACTION + "!" -> {
                displayContainerActionMessage(
                        callbackQuery,
                        container,
                        RESTART_INFO_TEXT.formatted(container.names()));

                try {
                    DockerService.instance().restartContainer(container.id());
                } catch (NotFoundException e) {
                    displayContainerNotFoundMessage(callbackQuery, container.id());
                }
            }
            // ============================================================================== //

            // Остановка контейнера ========================================================= //
            case STOP_ACTION + "?" -> displayContainerActionConfirmation(
                    callbackQuery,
                    container,
                    STOP_ACTION,
                    STOP_CONFIRMATION_TEXT.formatted(container.names()));
            case STOP_ACTION + "!" -> {
                displayContainerActionMessage(
                        callbackQuery,
                        container,
                        STOP_INFO_TEXT.formatted(container.names()));

                try {
                    DockerService.instance().stopContainer(container.id());
                } catch (NotFoundException e) {
                    displayContainerNotFoundMessage(callbackQuery, container.id());
                } catch (NotModifiedException e) {
                    LOGGER.error("Trying to stop container with ID {}, that already stopped", container.id());
                    displayContainerNotModifiedMessage(
                            callbackQuery,
                            container.id(),
                            ALREADY_STOPPED_TEXT.formatted(container.id()));
                }
            }
            // ============================================================================== //

            // Запуск контейнера ============================================================ //
            case LAUNCH_ACTION -> {
                displayContainerActionMessage(
                        callbackQuery,
                        container,
                        LAUNCH_INFO_TEXT.formatted(container.names()));

                try {
                    DockerService.instance().startContainer(container.id());
                } catch (NotFoundException e) {
                    displayContainerNotFoundMessage(callbackQuery, container.id());
                } catch (NotModifiedException e) {
                    LOGGER.error("Trying to start container with ID {}, that already started", container.id());
                    displayContainerNotModifiedMessage(
                            callbackQuery,
                            container.id(),
                            ALREADY_LAUNCHED_TEXT.formatted(container.id()));
                }
            }
            // ============================================================================== //

            // Сбор логов в контейнере ====================================================== //
            case LOGS_ACTION -> displayLogsMenu(
                    callbackQuery,
                    container
            );
            case LOGS_ACTION + "d" -> {
                displayContainerActionMessage(
                        callbackQuery,
                        container,
                        LOGS_INFO_TEXT.formatted(container.names())
                );
                sendLogs(callbackQuery, container, "d");
            }
            case LOGS_ACTION + "w" -> {
                displayContainerActionMessage(
                        callbackQuery,
                        container,
                        LOGS_INFO_TEXT.formatted(container.names())
                );
                sendLogs(callbackQuery, container, "w");
            }
            case LOGS_ACTION + "m" -> {
                displayContainerActionMessage(
                        callbackQuery,
                        container,
                        LOGS_INFO_TEXT.formatted(container.names())
                );
                sendLogs(callbackQuery, container, "m");
            }
            // ============================================================================== //

            // Общая информация о контейнере ================================================ //
            default -> displayContainerInfo(callbackQuery, container);
            // ============================================================================== //
        }

    }

    private void sendLogs(
            CallbackQuery callbackQuery,
            DockerContainer container,
            String periodLiteral) {

        int logsPeriod = switch (periodLiteral) {
            case "d" -> 86400;
            case "w" -> 604800;
            case "m" -> 4629743;
            default -> -1;
        };

        try (LogsResultCallback logsResultCallback = new LogsResultCallback(
                callbackQuery.getMessage().getChatId(),
                periodLiteral,
                container)) {
            DockerService.instance().getLogs(container.id(), logsResultCallback, logsPeriod);
        } catch (IOException e) {
            LOGGER.error("Error while sending logs!");
        } catch (NotFoundException e) {
            displayContainerNotFoundMessage(callbackQuery, container.id());
        }

    }

    private void displayContainerInfo(
            CallbackQuery callbackQuery,
            DockerContainer container) {

        InlineKeyboardBuilder keyboardBuilder = new InlineKeyboardBuilder();
        if (container.status().isRunning()) {
            keyboardBuilder
                    .addButton("Логи", buildContainerCallbackData(container.id(), LOGS_ACTION))
                    .nextRow();
            keyboardBuilder
                    .addButton("Перезапустить", buildContainerCallbackData(container.id(), RESTART_ACTION + "?"))
                    .nextRow();
            keyboardBuilder
                    .addButton("Остановить", buildContainerCallbackData(container.id(), STOP_ACTION + "?"))
                    .nextRow();
        } else {
            keyboardBuilder
                    .addButton("Запустить", buildContainerCallbackData(container.id(), LAUNCH_ACTION))
                    .nextRow();
        }


        keyboardBuilder
                .addButton(REFRESH_EMOJI + " Обновить", buildContainerCallbackData(container.id(), "d"))
                .addButton(BACK_EMOJI + " Назад", DOCKER_CONTAINERS_MENU_SELECTOR);

        InlineKeyboardMarkup keyboardMarkup = keyboardBuilder.build();

        EditMessageText containerInfo = new EditMessageBuilder(callbackQuery)
                .withMessageText("""
                        *Docker-контейнер*
                        `%s`

                        *ID:*\t`%s`
                        *Состояние:*\t%s
                        """.formatted(
                        container.names(),
                        container.id(),
                        container.status().statusEmoji() + " " + container.status().statusDuration()
                ))
                .withReplyMarkup(keyboardMarkup)
                .build();

        send(containerInfo);
    }

    private void displayLogsMenu(
            CallbackQuery callbackQuery,
            DockerContainer container) {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardBuilder()
                .addButton("За сутки", buildContainerCallbackData(container.id(), LOGS_ACTION + "d"))
                .nextRow()
                .addButton("За неделю", buildContainerCallbackData(container.id(), LOGS_ACTION + "w"))
                .nextRow()
                .addButton("За месяц", buildContainerCallbackData(container.id(), LOGS_ACTION + "m"))
                .nextRow()
                .addButton(BACK_EMOJI + " Назад", buildContainerCallbackData(container.id(), "0"))
                .build();

        EditMessageText logsMenu = new EditMessageBuilder(callbackQuery)
                .withMessageText("""
                        *Docker-контейнер*
                        `%s`
                                    
                        За какой период требуется собрать логи?
                        """.formatted(container.names()))
                .withReplyMarkup(keyboardMarkup)
                .build();

        send(logsMenu);
    }

    private void displayContainerActionConfirmation(
            CallbackQuery callbackQuery,
            DockerContainer container,
            String action,
            String text) {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardBuilder()
                .addButton("Да", buildContainerCallbackData(container.id(), action + "!"))
                .addButton("Отмена", buildContainerCallbackData(container.id(), "0"))
                .build();

        EditMessageText restartConfirmation = new EditMessageBuilder(callbackQuery)
                .withMessageText(text)
                .withReplyMarkup(keyboardMarkup)
                .build();

        send(restartConfirmation);
    }

    private void displayContainerActionMessage(
            CallbackQuery callbackQuery,
            DockerContainer container,
            String text) {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardBuilder()
                .addButton("Назад", buildContainerCallbackData(container.id(), "0"))
                .build();

        EditMessageText containerActionMessage = new EditMessageBuilder(callbackQuery)
                .withMessageText(text)
                .withReplyMarkup(keyboardMarkup)
                .build();

        send(containerActionMessage);
    }

    private void displayContainerNotFoundMessage(
            CallbackQuery callbackQuery,
            String containerId) {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardBuilder()
                .addButton(BACK_EMOJI + " К списку контейнеров...", DOCKER_CONTAINERS_MENU_SELECTOR)
                .build();

        EditMessageText containerNotFoundMessage = new EditMessageBuilder(callbackQuery)
                .withMessageText("""
                        *Docker-контейнер с ID*
                        `%s`
                        *не найден!*
                        """.formatted(containerId))
                .withReplyMarkup(keyboardMarkup)
                .build();

        send(containerNotFoundMessage);
    }

    private void displayContainerNotModifiedMessage(
            CallbackQuery callbackQuery,
            String containerId,
            String text) {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardBuilder()
                .addButton(BACK_EMOJI + " Назад", buildContainerCallbackData(containerId, "0"))
                .build();

        EditMessageText containerNotFoundMessage = new EditMessageBuilder(callbackQuery)
                .withMessageText(text)
                .withReplyMarkup(keyboardMarkup)
                .build();

        send(containerNotFoundMessage);
    }

    private void send(EditMessageText message) {
        try {
            CommanderBot.instance().execute(message);
        } catch (TelegramApiException e) {
            LOGGER.error("Error executing docker container menu message", e);
        }
    }

    private String buildContainerCallbackData(String containerId, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append(DOCKER_CONTAINER_MENU_SELECTOR)
                .append(".").append(containerId, 0, 12);
        if (action != null) {
            sb.append(".").append(action);
        }

        return sb.toString();
    }

    private static class LogsResultCallback extends ResultCallbackTemplate<LogsResultCallback, Frame> {

        private final long chatId;

        private final String periodLiteral;
        private final DockerContainer container;
        private Path tempFilePath;
        private BufferedWriter bufferedWriterWriter;

        public LogsResultCallback(
                long chatId,
                String periodLiteral,
                DockerContainer container) {
            this.chatId = chatId;
            this.periodLiteral = periodLiteral;
            this.container = container;
        }

        @Override
        public void onStart(Closeable stream) {
            super.onStart(stream);
            LOGGER.debug("Started to obtain logs from container {} with ID {}", container.names(), container.id());
            tempFilePath = Paths.get(System.getProperty("java.io.tmpdir"), "/", UUID.randomUUID().toString());
            try {
                FileWriter fileWriter = new FileWriter(tempFilePath.toFile());
                bufferedWriterWriter = new BufferedWriter(fileWriter);
            } catch (IOException e) {
                LOGGER.error("There was an error while creating temp file {}", tempFilePath, e);
            }
        }

        @Override
        public void onNext(Frame object) {
            try {
                bufferedWriterWriter.write(object.toString());
                bufferedWriterWriter.newLine();
            } catch (IOException e) {
                LOGGER.error("Error while writing logs to file!", e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            super.onError(throwable);
            LOGGER.error("Error while reading container logs", throwable);

            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setParseMode("markdown");
            errorMessage.setText("""
                    *Docker-контейнер*
                    `%s`
                                           
                    Ошибочка вышла :-(. Логов не будет, электричество кончилось
                    """.formatted(container.names()));

            try {
                bufferedWriterWriter.close();
                CommanderBot.instance().execute(errorMessage);
            } catch (IOException e) {
                LOGGER.error("Error while closing logs stream!", e);
            } catch (TelegramApiException e) {
                LOGGER.error("Error while sending error message!", e);
            }
        }

        @Override
        public void onComplete() {
            super.onComplete();
            LOGGER.debug("Finished to obtain logs from container {} with ID {}", container.names(), container.id());
            try {
                bufferedWriterWriter.close();

                InputFile logsFile = new InputFile();
                logsFile.setMedia(tempFilePath.toFile(), "logs.txt");

                String logsPeriodCaption = switch (periodLiteral) {
                    case "d" -> "сутки";
                    case "w" -> "неделю";
                    case "m" -> "месяц";
                    default -> "ERROR";
                };

                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(chatId);
                sendDocument.setParseMode("markdown");
                sendDocument.setCaption("""
                        *Docker-контейнер*
                        `%s`
                                                
                        А вот и логи за %s!
                        """.formatted(container.names(), logsPeriodCaption));
                sendDocument.setDocument(logsFile);
                CommanderBot.instance().execute(sendDocument);

                Files.delete(tempFilePath);

            } catch (IOException e) {
                LOGGER.error("Error while closing logs stream!", e);
            } catch (TelegramApiException e) {
                LOGGER.error("Error while sending logs!", e);
            }
        }
    }

}
