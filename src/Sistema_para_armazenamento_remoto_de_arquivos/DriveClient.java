/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Sistema_para_armazenamento_remoto_de_arquivos;

/**
 *
 * @author User
 */
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Scanner;

public class DriveClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private InputStream rawIn;
    private OutputStream rawOut;

    public static void main(String[] args) {
        DriveClient client = new DriveClient();
        client.run();
    }

    public void run() {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.print("IP do servidor: ");
            String ip = sc.nextLine().trim();
            System.out.print("Porta do servidor: ");
            int port = Integer.parseInt(sc.nextLine().trim());

            socket = new Socket(ip, port);
            rawIn = socket.getInputStream();
            rawOut = socket.getOutputStream();
            in = new BufferedReader(new InputStreamReader(rawIn, "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(rawOut, "UTF-8"), true);

            // mostrar mensagem inicial
            String servMsg = in.readLine();
            if (servMsg != null) System.out.println(servMsg);

            while (true) {
                System.out.println("\nComandos: CREATE <user> <pass> | LOGIN <user> <pass> | LIST | UPLOAD <localPath> | DOWNLOAD <filename> | QUIT");
                System.out.print("> ");
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(" ", 2);
                String cmd = parts[0].toUpperCase();

                if (cmd.equals("CREATE") || cmd.equals("LOGIN")) {
                    out.println(line);
                    String resp = in.readLine();
                    System.out.println(resp);
                } else if (cmd.equals("LIST")) {
                    out.println("LIST");
                    String resp;
                    while ((resp = in.readLine()) != null) {
                        if (resp.equals("END_OF_LIST")) break;
                        System.out.println(resp);
                    }
                } else if (cmd.equals("UPLOAD")) {
                    if (parts.length < 2) {
                        System.out.println("Uso: UPLOAD <caminho_local>");
                        continue;
                    }
                    Path p = Paths.get(parts[1]);
                    if (!Files.exists(p) || !Files.isRegularFile(p)) {
                        System.out.println("[FALHA] Arquivo local nao existe.");
                        continue;
                    }
                    long size = Files.size(p);
                    String filename = p.getFileName().toString();
                    out.println("UPLOAD " + filename + " " + size);
                    String serverReady = in.readLine();
                    if (!"OK".equals(serverReady)) {
                        System.out.println("[FALHA] Servidor recusou upload: " + serverReady);
                        continue;
                    }
                    // envia bytes
                    try (InputStream fis = Files.newInputStream(p, StandardOpenOption.READ)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            rawOut.write(buffer, 0, read);
                        }
                        rawOut.flush();
                    }
                    String finalResp = in.readLine();
                    System.out.println(finalResp);
                } else if (cmd.equals("DOWNLOAD")) {
                    if (parts.length < 2) {
                        System.out.println("Uso: DOWNLOAD <filename>");
                        continue;
                    }
                    String filename = parts[1].trim();
                    out.println("DOWNLOAD " + filename);
                    String serverResp = in.readLine();
                    if (serverResp == null) {
                        System.out.println("[FALHA] Sem resposta do servidor.");
                        continue;
                    }
                    if (serverResp.startsWith("DOWNLOAD_FAIL")) {
                        System.out.println("[FALHA] " + serverResp.substring("DOWNLOAD_FAIL".length()).trim());
                        continue;
                    }
                    if (serverResp.startsWith("DOWNLOAD_OK")) {
                        String[] tok = serverResp.split(" ");
                        long size = Long.parseLong(tok[1]);
                        // diga que está pronto para receber
                        out.println("READY");
                        // receber bytes
                        Path outFile = Paths.get(filename); // salva no diretorio local atual
                        try (OutputStream fos = Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            long remaining = size;
                            byte[] buffer = new byte[8192];
                            while (remaining > 0) {
                                int toRead = (int)Math.min(buffer.length, remaining);
                                int read = rawIn.read(buffer, 0, toRead);
                                if (read == -1) throw new EOFException("Stream terminou antes do esperado");
                                fos.write(buffer, 0, read);
                                remaining -= read;
                            }
                        }
                        String finalMsg = in.readLine();
                        System.out.println(finalMsg);
                    } else {
                        System.out.println("[FALHA] Resposta inesperada: " + serverResp);
                    }
                } else if (cmd.equals("QUIT")) {
                    out.println("QUIT");
                    String resp = in.readLine();
                    if (resp != null) System.out.println(resp);
                    break;
                } else {
                    System.out.println("Comando desconhecido.");
                }
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }
    }
}
