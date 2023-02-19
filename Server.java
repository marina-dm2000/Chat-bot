package com.javarush.task.task30.task3008;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной класс сервера
 */
public class Server {
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        ConsoleHelper.writeMessage("Введите порт сервера");
        int port = ConsoleHelper.readInt();
        try (ServerSocket socket = new ServerSocket(port)) {
            ConsoleHelper.writeMessage("Сервер запущен");
            // Ожидаем входящее соединение и запускаем отдельный поток при его принятии
            while (true) {
                Socket socket1 = socket.accept();
                new Handler(socket1).start();
            }
        } catch (IOException e) {
            ConsoleHelper.writeMessage("Произошла ошибка");
        }
    }

    private static class Handler extends Thread {
        private Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            ConsoleHelper.writeMessage("Установлено новое соединение с удаленным адресом " + socket.getRemoteSocketAddress());
            try {
                Connection connection = new Connection(socket);
                String userName = serverHandshake(connection);

                // Сообщаем всем участникам, что присоединился новый участник
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, userName));

                // Сообщаем новому участнику о существующих участниках
                notifyUsers(connection, userName);

                // Обрабатываем сообщения пользователей
                serverMainLoop(connection, userName);

                connectionMap.remove(userName);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, userName));
            } catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Произошла ошибка при обмене данными с удаленным адресом " + socket.getRemoteSocketAddress());
            }

            ConsoleHelper.writeMessage("Соединение с удаленным адресом закрыто");
        }

        /**
         * Этап первый - это этап рукопожатия (знакомства сервера с клиентом)
         *
         * @param connection соединение
         * @return имя нового клиента
         * @throws IOException
         * @throws ClassNotFoundException
         */
        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {
            while (true) {
                connection.send(new Message(MessageType.NAME_REQUEST));
                Message message = connection.receive();
                if (message.getType() != MessageType.USER_NAME) {
                    ConsoleHelper.writeMessage("Получено сообщение от " + socket.getRemoteSocketAddress() + ". Тип сообщения не соответствует протоколу.");
                    continue;
                }

                String userName = message.getData();

                if (userName.isEmpty()) {
                    ConsoleHelper.writeMessage("Попытка подключения к серверу с пустым именем от " + socket.getRemoteSocketAddress());
                    continue;
                }

                if (connectionMap.containsKey(userName)) {
                    ConsoleHelper.writeMessage("Попытка подключения к серверу с уже используемым именем от " + socket.getRemoteSocketAddress());
                    continue;
                }

                connectionMap.put(userName, connection);
                connection.send(new Message(MessageType.NAME_ACCEPTED));
                return userName;
            }
        }

        /**
         * Этап второй - отправка клиенту (новому участнику) информации об остальных клиентах (участниках) чата
         *
         * @param connection соединение с участником, которому будем слать информацию
         * @param userName   имя участника
         * @throws IOException
         */
        private void notifyUsers(Connection connection, String userName) throws IOException {
            for (String user : connectionMap.keySet()) {
                if (userName.equals(user)) {
                    continue;
                }
                connection.send(new Message(MessageType.USER_ADDED, user));
            }
        }

        /**
         * Этап третий - главный цикл обработки сообщений сервером
         *
         * @param connection соединение с участником, которому будем слать информацию
         * @param userName   имя участника
         * @throws IOException
         * @throws ClassNotFoundException
         */
        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();
                if (message.getType() != MessageType.TEXT) {
                    ConsoleHelper.writeMessage("Не удалось отправить сообщение от " + socket.getRemoteSocketAddress());
                    continue;
                }

                sendBroadcastMessage(new Message(MessageType.TEXT, userName + ": " + message.getData()));
            }
        }
    }

    public static void sendBroadcastMessage(Message message) {
        // Рассылаем сообщение по всем соединениям
        for (Connection connection : connectionMap.values()) {
            try {
                connection.send(message);
            } catch (IOException e) {
                ConsoleHelper.writeMessage("Не смогли отправить сообщение " + connection.getRemoteSocketAddress());
            }
        }

    }
}
