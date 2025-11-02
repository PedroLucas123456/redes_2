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
import java.util.Scanner;

public class Cliente {
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static String currentRoom;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite o endereço IP do servidor: ");
        String serverIp = scanner.nextLine();
        System.out.print("Digite a porta: ");
        int port = scanner.nextInt();

        try {
            socket = new Socket(serverIp, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Receber solicitação de nome e enviar
            String prompt = in.readLine();
            System.out.print(prompt + " ");
            String username = scanner.nextLine();
            out.println(username);

            // Thread para receber mensagens do servidor
            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    System.out.println("Conexão perdida.");
                }
            }).start();

            // Loop para enviar comandos/mensagens
            while (true) {
                String input = scanner.nextLine();
                if (input.startsWith("/join ")) {
                    String room = input.substring(6);
                    out.println("JOIN " + room);
                    currentRoom = room;
                } else if (input.equals("/leave")) {
                    out.println("LEAVE");
                    currentRoom = null;
                } else if (input.equals("/exit") || input.equals("/quit")) {
                    out.println("EXIT");
                    break;
                } else {
                    if (currentRoom != null) {
                        out.println("MSG " + input);
                    } else {
                        System.out.println("Você não está em nenhuma sala. Use /join #sala.");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
