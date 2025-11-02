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
import java.util.*;
import java.util.concurrent.*;

public class DriveServer {
    private static final String USERS_FILE = "users.db"; // cada linha: username:password
    private static final String STORAGE_ROOT = "storage"; // pasta onde cada usuário tem seu diretório
    private ServerSocket serverSocket;
    private final Map<Socket, ClientHandler> handlers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new DriveServer().start();
    }

    public void start() {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.print("Endereco IP para bind (vazio para 0.0.0.0): ");
            String ip = sc.nextLine().trim();
            if (ip.isEmpty()) ip = "0.0.0.0";
            System.out.print("Porta para ouvir: ");
            int port = Integer.parseInt(sc.nextLine().trim());

            InetAddress bindAddr = InetAddress.getByName(ip);
            serverSocket = new ServerSocket(port, 50, bindAddr);
            System.out.println("Servidor ouvindo em " + bindAddr.getHostAddress() + ":" + port);

            // cria estrutura de armazenamento se nao existir
            Files.createDirectories(Paths.get(STORAGE_ROOT));
            ensureUsersFile();

            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket client = serverSocket.accept();
                ClientHandler handler = new ClientHandler(client);
                handlers.put(client, handler);
                pool.execute(handler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureUsersFile() throws IOException {
        Path p = Paths.get(USERS_FILE);
        if (!Files.exists(p)) Files.createFile(p);
    }

    private static synchronized boolean addUser(String username, String password) throws IOException {
        // verifica existencia
        List<String> lines = Files.readAllLines(Paths.get(USERS_FILE));
        for (String l : lines) {
            if (l.split(":", 2)[0].equals(username)) return false;
        }
        String entry = username + ":" + password;
        Files.write(Paths.get(USERS_FILE), Collections.singletonList(entry), StandardOpenOption.APPEND);
        // cria diretorio do usuario
        Files.createDirectories(Paths.get(STORAGE_ROOT, username));
        return true;
    }

    private static synchronized boolean checkLogin(String username, String password) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(USERS_FILE));
        for (String l : lines) {
            String[] parts = l.split(":", 2);
            if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) return true;
        }
        return false;
    }

    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private InputStream rawIn;
        private OutputStream rawOut;
        private String loggedUser = null;

        ClientHandler(Socket s) {
            this.socket = s;
        }

        public void run() {
            try {
                rawIn = socket.getInputStream();
                rawOut = socket.getOutputStream();
                in = new BufferedReader(new InputStreamReader(rawIn, "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(rawOut, "UTF-8"), true);

                out.println("[SUCESSO] Conectado ao servidor Drive.");
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Comando de " + socket.getRemoteSocketAddress() + ": " + line);
                    String[] tokens = line.split(" ", 3);
                    String cmd = tokens[0].toUpperCase();

                    switch (cmd) {
                        case "CREATE":
                            handleCreate(tokens);
                            break;
                        case "LOGIN":
                            handleLogin(tokens);
                            break;
                        case "LIST":
                            handleList();
                            break;
                        case "UPLOAD":
                            handleUpload(tokens);
                            break;
                        case "DOWNLOAD":
                            handleDownload(tokens);
                            break;
                        case "QUIT":
                            out.println("[SUCESSO] Conexão encerrada.");
                            closeConnection();
                            return;
                        default:
                            out.println("[FALHA] Comando desconhecido.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão perdida: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void handleCreate(String[] tokens) throws IOException {
            if (tokens.length < 3) {
                out.println("[FALHA] Uso: CREATE <usuario> <senha>");
                return;
            }
            String user = tokens[1];
            String pass = tokens[2];
            boolean ok = addUser(user, pass);
            if (ok) out.println("[SUCESSO] Usuario criado.");
            else out.println("[FALHA] Usuario já existe.");
        }

        private void handleLogin(String[] tokens) throws IOException {
            if (tokens.length < 3) {
                out.println("[FALHA] Uso: LOGIN <usuario> <senha>");
                return;
            }
            String user = tokens[1];
            String pass = tokens[2];
            boolean ok = checkLogin(user, pass);
            if (ok) {
                loggedUser = user;
                out.println("[SUCESSO] Login realizado.");
            } else {
                out.println("[FALHA] Usuario/senha inválidos.");
            }
        }

        private void handleList() {
            if (!isAuthed()) return;
            try {
                Path userDir = Paths.get(STORAGE_ROOT, loggedUser);
                if (!Files.exists(userDir)) Files.createDirectories(userDir);
                DirectoryStream<Path> ds = Files.newDirectoryStream(userDir);
                out.println("[SUCESSO] Lista de arquivos:");
                for (Path p : ds) {
                    out.println(p.getFileName().toString());
                }
                out.println("END_OF_LIST");
            } catch (IOException e) {
                out.println("[FALHA] Erro ao listar: " + e.getMessage());
            }
        }

        private void handleUpload(String[] tokens) {
            if (!isAuthed()) return;
            if (tokens.length < 3) {
                out.println("[FALHA] Uso: UPLOAD <filename> <filesize>");
                return;
            }
            String filename = tokens[1];
            long filesize;
            try {
                filesize = Long.parseLong(tokens[2]);
            } catch (NumberFormatException e) {
                out.println("[FALHA] Tamanho inválido.");
                return;
            }

            out.println("OK"); // autorização para enviar
            Path userDir = Paths.get(STORAGE_ROOT, loggedUser);
            try {
                Files.createDirectories(userDir);
                Path target = userDir.resolve(filename);
                try (OutputStream fos = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    long remaining = filesize;
                    byte[] buffer = new byte[8192];
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int read = rawIn.read(buffer, 0, toRead);
                        if (read == -1) throw new EOFException("Fim de stream inesperado");
                        fos.write(buffer, 0, read);
                        remaining -= read;
                    }
                }
                out.println("[SUCESSO] Upload concluído.");
            } catch (IOException ex) {
                ex.printStackTrace();
                out.println("[FALHA] Erro ao receber arquivo: " + ex.getMessage());
            }
        }

        private void handleDownload(String[] tokens) {
            if (!isAuthed()) return;
            if (tokens.length < 2) {
                out.println("[FALHA] Uso: DOWNLOAD <filename>");
                return;
            }
            String filename = tokens[1];
            Path userDir = Paths.get(STORAGE_ROOT, loggedUser);
            Path file = userDir.resolve(filename);
            if (!Files.exists(file)) {
                out.println("DOWNLOAD_FAIL Arquivo nao encontrado");
                return;
            }
            try {
                long size = Files.size(file);
                out.println("DOWNLOAD_OK " + size);
                // aguarda possivel "READY" do cliente (poderia ser opcional)
                String clientResp = in.readLine();
                if (clientResp == null || !clientResp.equals("READY")) {
                    out.println("[FALHA] Cliente não pronto.");
                    return;
                }
                try (InputStream fis = Files.newInputStream(file, StandardOpenOption.READ)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        rawOut.write(buffer, 0, read);
                    }
                    rawOut.flush();
                }
                out.println("[SUCESSO] Download concluído.");
            } catch (IOException e) {
                e.printStackTrace();
                out.println("[FALHA] Erro ao enviar arquivo: " + e.getMessage());
            }
        }

        private boolean isAuthed() {
            if (loggedUser == null) {
                out.println("[FALHA] Você precisa fazer LOGIN primeiro.");
                return false;
            }
            return true;
        }

        private void closeConnection() {
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
            handlers.remove(socket);
        }
    }
}

