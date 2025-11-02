/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Sistema_de_Chat_Multisala;

/**
 *
 * @author User
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Servidor {
    private static final Map<String, List<ClientHandler>> rooms = new ConcurrentHashMap<>();
    private static final Map<ClientHandler, String> clientRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite o endereço IP do servidor: ");
        String ip = scanner.nextLine();
        System.out.print("Digite a porta: ");
        int port = scanner.nextInt();

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(ip, port));
            System.out.println("Servidor iniciado em " + ip + ":" + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método para broadcast de mensagens para uma sala
    public static void broadcast(String message, String room, ClientHandler sender) {
        List<ClientHandler> clientsInRoom = rooms.get(room);
        if (clientsInRoom != null) {
            for (ClientHandler client : clientsInRoom) {
                if (client != sender) {
                    client.sendMessage(message);
                }
            }
        }
    }

    // Método para adicionar cliente a uma sala
    public static void joinRoom(String room, ClientHandler client) {
        rooms.computeIfAbsent(room, k -> new CopyOnWriteArrayList<>()).add(client);
        clientRooms.put(client, room);
        broadcast("MSG [Sistema]: " + client.username + " entrou na sala #" + room, room, null);
    }

    // Método para remover cliente de uma sala
    public static void leaveRoom(ClientHandler client) {
        String room = clientRooms.get(client);
        if (room != null) {
            rooms.get(room).remove(client);
            clientRooms.remove(client);
            broadcast("MSG [Sistema]: " + client.username + " saiu da sala #" + room, room, null);
        }
    }

    // Classe para gerenciar cada cliente
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private String currentRoom;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // Receber nome de usuário
                out.println("Digite seu nome de usuário:");
                username = in.readLine();
                System.out.println("Usuário " + username + " conectado.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("JOIN ")) {
                        String room = message.substring(5);
                        if (currentRoom != null) {
                            leaveRoom(this);
                        }
                        joinRoom(room, this);
                        currentRoom = room;
                        out.println("MSG [Sistema]: Você entrou na sala #" + room);
                    } else if (message.equals("LEAVE")) {
                        leaveRoom(this);
                        currentRoom = null;
                        out.println("MSG [Sistema]: Você saiu da sala.");
                    } else if (message.startsWith("MSG ")) {
                        if (currentRoom != null) {
                            String text = message.substring(4);
                            broadcast("MSG [" + username + "]: " + text, currentRoom, this);
                        } else {
                            out.println("MSG [Sistema]: Você não está em nenhuma sala. Use /join #sala.");
                        }
                    } else if (message.equals("EXIT")) {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                leaveRoom(this);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Cliente " + username + " desconectado.");
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}
