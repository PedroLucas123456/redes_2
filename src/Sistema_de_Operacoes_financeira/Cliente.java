/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Sistema_de_Operacoes_financeira;

/**
 *
 * @author User
 */
import java.io.*;
import java.net.*;

public class Cliente {
    public static void main(String[] args) {
        String host = "localhost"; // Altere para o IP do servidor se necessário
        int porta = 12345; // Porta padrão

        try (Socket socket = new Socket(host, porta);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Conectado ao servidor.");

            String userInput;
            while (true) {
                System.out.print("Digite um comando (CRIAR, SALDO, DEPOSITAR, SACAR) ou /exit: ");
                userInput = stdIn.readLine();
                if (userInput.equalsIgnoreCase("/exit")) {
                    break; // Sai do loop
                }
                out.println(userInput); // Envia o comando
                String resposta = in.readLine(); // Recebe a resposta
                System.out.println(resposta); // Exibe a resposta
            }
        } catch (IOException e) {
            System.out.println("Erro na conexão: " + e.getMessage());
        }
    }
}

