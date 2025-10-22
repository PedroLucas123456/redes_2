/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Sistema_de_Operacoes_financeira;
/**
 @author User
 */
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Servidor {
    private static Map<String, Double> contas = new HashMap<>(); // Armazena contas: {numero_conta: saldo}

    public static void main(String[] args) {
        int porta = 12345; // Porta padrão
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            System.out.println("Servidor escutando na porta " + porta);
            while (true) {
                Socket socket = serverSocket.accept(); // Aceita conexões
                new Thread(new ClienteHandler(socket)).start(); // Cria uma nova thread para cada cliente
            }
        } catch (IOException e) {
            System.out.println("Erro ao iniciar o servidor: " + e.getMessage());
        }
    }

    private static class ClienteHandler implements Runnable {
        private Socket socket;

        public ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                String comando;
                while ((comando = in.readLine()) != null) {
                    String resposta = processarComando(comando);
                    out.println(resposta); // Envia a resposta ao cliente
                }
            } catch (IOException e) {
                System.out.println("Erro na comunicação com o cliente: " + e.getMessage());
            } finally {
                try {
                    socket.close(); // Fecha a conexão
                } catch (IOException e) {
                    System.out.println("Erro ao fechar a conexão: " + e.getMessage());
                }
            }
        }

        private String processarComando(String comando) {
            String[] partes = comando.trim().toUpperCase().split(" ");
            if (partes.length == 0) {
                return "[FALHA] Comando inválido.";
            }

            switch (partes[0]) {
                case "CRIAR":
                    return criarConta(partes);
                case "SALDO":
                    return verSaldo(partes);
                case "DEPOSITAR":
                    return depositar(partes);
                case "SACAR":
                    return sacar(partes);
                default:
                    return "[FALHA] Comando inválido.";
            }
        }

        private String criarConta(String[] partes) {
            if (partes.length != 2) {
                return "[FALHA] Uso: CRIAR <numero_conta>";
            }
            String numeroConta = partes[1];
            if (contas.containsKey(numeroConta)) {
                return "[FALHA] Conta já existe.";
            }
            contas.put(numeroConta, 0.0); // Inicializa com saldo zero
            return "[SUCESSO] Conta " + numeroConta + " criada com saldo zero.";
        }

        private String verSaldo(String[] partes) {
            if (partes.length != 2) {
                return "[FALHA] Uso: SALDO <numero_conta>";
            }
            String numeroConta = partes[1];
            if (!contas.containsKey(numeroConta)) {
                return "[FALHA] Conta não existe.";
            }
            double saldo = contas.get(numeroConta);
            return "[SUCESSO] Saldo da conta " + numeroConta + ": " + saldo;
        }

        private String depositar(String[] partes) {
            if (partes.length != 3) {
                return "[FALHA] Uso: DEPOSITAR <numero_conta> <valor>";
            }
            String numeroConta = partes[1];
            try {
                double valor = Double.parseDouble(partes[2]);
                if (!contas.containsKey(numeroConta)) {
                    return "[FALHA] Conta não existe.";
                }
                if (valor < 0) {
                    return "[FALHA] Valor deve ser positivo.";
                }
                contas.put(numeroConta, contas.get(numeroConta) + valor);
                return "[SUCESSO] Depósito de " + valor + " na conta " + numeroConta + ". Novo saldo: " + contas.get(numeroConta);
            } catch (NumberFormatException e) {
                return "[FALHA] Valor inválido.";
            }
        }

        private String sacar(String[] partes) {
            if (partes.length != 3) {
                return "[FALHA] Uso: SACAR <numero_conta> <valor>";
            }
            String numeroConta = partes[1];
            try {
                double valor = Double.parseDouble(partes[2]);
                if (!contas.containsKey(numeroConta)) {
                    return "[FALHA] Conta não existe.";
                }
                if (valor < 0) {
                    return "[FALHA] Valor deve ser positivo.";
                }
                if (contas.get(numeroConta) < valor) {
                    return "[FALHA] Saldo insuficiente.";
                }
                contas.put(numeroConta, contas.get(numeroConta) - valor);
                return "[SUCESSO] Saque de " + valor + " da conta " + numeroConta + ". Novo saldo: " + contas.get(numeroConta);
            } catch (NumberFormatException e) {
                return "[FALHA] Valor inválido.";
            }
        }
    }
}
