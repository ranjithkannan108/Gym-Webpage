import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class GymServer {

    // MySQL Config
    static final String DB_URL = "jdbc:mysql://localhost:3306/";
    static final String DB_NAME = "titanfit_db";
    static final String DB_USER = "root";
    static final String DB_PASS = "admin";

    static Connection connection;

    public static void main(String[] args) throws Exception {
        // Initialize DB
        initDatabase();

        // Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);

        // API endpoints
        server.createContext("/api/members", new MembersHandler());
        server.createContext("/api/stats", new StatsHandler());

        // Static file serving
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("\n===========================================");
        System.out.println("  TitanFit Gym Server Started!");
        System.out.println("  Admin Panel: http://localhost:3000/admin.html");
        System.out.println("  Website:     http://localhost:3000/index.html");
        System.out.println("===========================================\n");
    }

    // ===== DATABASE INIT =====
    static void initDatabase() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        // Create database if not exists
        Connection tempConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        Statement stmt = tempConn.createStatement();
        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
        stmt.close();
        tempConn.close();

        // Connect to the database
        connection = DriverManager.getConnection(DB_URL + DB_NAME, DB_USER, DB_PASS);

        // Create table if not exists
        Statement createStmt = connection.createStatement();
        createStmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS members (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +
            "  name VARCHAR(255) NOT NULL," +
            "  package VARCHAR(100) NOT NULL," +
            "  fees DECIMAL(10,2) NOT NULL," +
            "  joining_date DATE NOT NULL," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );
        createStmt.close();

        System.out.println("[OK] Database and table ready!");
    }

    // ===== STATIC FILE HANDLER =====
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            // Determine file path
            File file = new File("." + path);

            // Also check parent directory for assets
            if (!file.exists() && path.startsWith("/asset")) {
                file = new File(".." + path);
            }

            if (!file.exists() || file.isDirectory()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            // Content type
            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            FileInputStream fis = new FileInputStream(file);
            OutputStream os = exchange.getResponseBody();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            fis.close();
            os.close();
        }

        String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".webp")) return "image/webp";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".json")) return "application/json";
            return "application/octet-stream";
        }
    }

    // ===== MEMBERS API HANDLER =====
    static class MembersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            String method = exchange.getRequestMethod();

            // Handle preflight
            if (method.equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            try {
                // Extract ID from path: /api/members/123
                String idStr = null;
                if (path.matches("/api/members/\\d+")) {
                    idStr = path.substring(path.lastIndexOf('/') + 1);
                }

                switch (method) {
                    case "GET":
                        handleGet(exchange, query);
                        break;
                    case "POST":
                        handlePost(exchange);
                        break;
                    case "PUT":
                        handlePut(exchange, idStr);
                        break;
                    case "DELETE":
                        handleDelete(exchange, idStr);
                        break;
                    default:
                        sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        }

        // GET - Read all members with optional filters
        void handleGet(HttpExchange exchange, String query) throws Exception {
            StringBuilder sql = new StringBuilder("SELECT * FROM members WHERE 1=1");
            List<String> params = new ArrayList<>();
            Map<String, String> queryParams = parseQuery(query);

            if (queryParams.containsKey("name")) {
                sql.append(" AND name LIKE ?");
                params.add("%" + queryParams.get("name") + "%");
            }
            if (queryParams.containsKey("package")) {
                sql.append(" AND package = ?");
                params.add(queryParams.get("package"));
            }
            if (queryParams.containsKey("fromDate")) {
                sql.append(" AND joining_date >= ?");
                params.add(queryParams.get("fromDate"));
            }
            if (queryParams.containsKey("toDate")) {
                sql.append(" AND joining_date <= ?");
                params.add(queryParams.get("toDate"));
            }

            sql.append(" ORDER BY joining_date DESC");

            PreparedStatement pstmt = connection.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                pstmt.setString(i + 1, params.get(i));
            }

            ResultSet rs = pstmt.executeQuery();
            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"name\":\"").append(escapeJson(rs.getString("name"))).append("\",")
                    .append("\"package\":\"").append(escapeJson(rs.getString("package"))).append("\",")
                    .append("\"fees\":").append(rs.getDouble("fees")).append(",")
                    .append("\"joining_date\":\"").append(rs.getString("joining_date")).append("\"")
                    .append("}");
                first = false;
            }
            json.append("]");

            rs.close();
            pstmt.close();
            sendResponse(exchange, 200, json.toString());
        }

        // POST - Create new member
        void handlePost(HttpExchange exchange) throws Exception {
            String body = readBody(exchange);
            Map<String, String> data = parseJsonBody(body);

            String name = data.get("name");
            String pkg = data.get("package");
            String fees = data.get("fees");
            String joiningDate = data.get("joiningDate");

            if (name == null || pkg == null || fees == null || joiningDate == null) {
                sendResponse(exchange, 400, "{\"error\":\"All fields are required\"}");
                return;
            }

            PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO members (name, package, fees, joining_date) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            pstmt.setString(1, name);
            pstmt.setString(2, pkg);
            pstmt.setDouble(3, Double.parseDouble(fees));
            pstmt.setString(4, joiningDate);
            pstmt.executeUpdate();

            ResultSet keys = pstmt.getGeneratedKeys();
            int newId = 0;
            if (keys.next()) newId = keys.getInt(1);
            keys.close();
            pstmt.close();

            sendResponse(exchange, 201, "{\"id\":" + newId + ",\"message\":\"Member added successfully\"}");
        }

        // PUT - Update member
        void handlePut(HttpExchange exchange, String idStr) throws Exception {
            if (idStr == null) {
                sendResponse(exchange, 400, "{\"error\":\"Member ID required\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> data = parseJsonBody(body);

            PreparedStatement pstmt = connection.prepareStatement(
                "UPDATE members SET name=?, package=?, fees=?, joining_date=? WHERE id=?"
            );
            pstmt.setString(1, data.get("name"));
            pstmt.setString(2, data.get("package"));
            pstmt.setDouble(3, Double.parseDouble(data.get("fees")));
            pstmt.setString(4, data.get("joiningDate"));
            pstmt.setInt(5, Integer.parseInt(idStr));

            int affected = pstmt.executeUpdate();
            pstmt.close();

            if (affected == 0) {
                sendResponse(exchange, 404, "{\"error\":\"Member not found\"}");
            } else {
                sendResponse(exchange, 200, "{\"message\":\"Member updated successfully\"}");
            }
        }

        // DELETE - Delete member
        void handleDelete(HttpExchange exchange, String idStr) throws Exception {
            if (idStr == null) {
                sendResponse(exchange, 400, "{\"error\":\"Member ID required\"}");
                return;
            }

            PreparedStatement pstmt = connection.prepareStatement("DELETE FROM members WHERE id=?");
            pstmt.setInt(1, Integer.parseInt(idStr));

            int affected = pstmt.executeUpdate();
            pstmt.close();

            if (affected == 0) {
                sendResponse(exchange, 404, "{\"error\":\"Member not found\"}");
            } else {
                sendResponse(exchange, 200, "{\"message\":\"Member deleted successfully\"}");
            }
        }
    }

    // ===== STATS API HANDLER =====
    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (!exchange.getRequestMethod().equals("GET")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                Statement stmt = connection.createStatement();

                // Total members
                ResultSet rs1 = stmt.executeQuery("SELECT COUNT(*) as total FROM members");
                rs1.next();
                int totalMembers = rs1.getInt("total");
                rs1.close();

                // Total fees
                ResultSet rs2 = stmt.executeQuery("SELECT COALESCE(SUM(fees), 0) as totalFees FROM members");
                rs2.next();
                double totalFees = rs2.getDouble("totalFees");
                rs2.close();

                // Package counts
                ResultSet rs3 = stmt.executeQuery("SELECT package, COUNT(*) as count FROM members GROUP BY package");
                StringBuilder packages = new StringBuilder("{");
                boolean first = true;
                while (rs3.next()) {
                    if (!first) packages.append(",");
                    packages.append("\"").append(escapeJson(rs3.getString("package"))).append("\":")
                            .append(rs3.getInt("count"));
                    first = false;
                }
                packages.append("}");
                rs3.close();
                stmt.close();

                String json = "{\"totalMembers\":" + totalMembers +
                              ",\"totalFees\":" + totalFees +
                              ",\"packages\":" + packages.toString() + "}";

                sendResponse(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        }
    }

    // ===== UTILITY METHODS =====

    static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                try {
                    map.put(java.net.URLDecoder.decode(pair[0], "UTF-8"),
                            java.net.URLDecoder.decode(pair[1], "UTF-8"));
                } catch (Exception e) {
                    map.put(pair[0], pair[1]);
                }
            }
        }
        return map;
    }

    // Simple JSON body parser (handles flat key-value objects)
    static Map<String, String> parseJsonBody(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isEmpty()) return map;

        // Remove outer braces
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        // Split by comma, but not inside quotes
        int i = 0;
        while (i < json.length()) {
            // Find key
            int keyStart = json.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String key = json.substring(keyStart + 1, keyEnd);

            // Find colon
            int colon = json.indexOf(':', keyEnd);
            if (colon == -1) break;

            // Find value
            int valStart = colon + 1;
            while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;

            String value;
            if (valStart < json.length() && json.charAt(valStart) == '"') {
                // String value
                int valEnd = json.indexOf('"', valStart + 1);
                value = json.substring(valStart + 1, valEnd);
                i = valEnd + 1;
            } else {
                // Number or other value
                int valEnd = json.indexOf(',', valStart);
                if (valEnd == -1) valEnd = json.length();
                value = json.substring(valStart, valEnd).trim();
                i = valEnd;
            }

            map.put(key, value);

            // Skip comma
            if (i < json.length() && json.charAt(i) == ',') i++;
        }

        return map;
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
